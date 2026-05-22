package com.tss.grc.module.admin.committee.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeActionItem;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeStandingItem;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeStandingItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Annual calendar + agenda auto-populator. Turns the committees module
 * from "manual create each meeting" into a real operational rhythm.
 *
 * <p>Two main operations:
 * <ol>
 *   <li>{@link #generateAnnualCalendar} — given a committee and a year,
 *       create scheduled meeting rows according to the template's
 *       cadence (monthly / quarterly / bi-weekly / etc.) and pre-load
 *       each agenda with the template's standing items.</li>
 *   <li>{@link #populateAgenda} — given an existing meeting row, refresh
 *       its agenda by combining the template's standing items with the
 *       open actions (matters arising) from the previous meeting of the
 *       same committee. Idempotent — calling it multiple times produces
 *       the same agenda.</li>
 * </ol>
 *
 * <p>The agenda is stored as JSON in {@code CommitteeMeeting.agendaJson}
 * (V40 convention). The structure is an array of
 * {@code {seq, topicEn, topicAr, presenter, minutesAllocated, sourceModule?, isMandatory?}}.
 * The frontend agenda builder lets the secretary edit / re-order / add
 * ad-hoc items on top of this pre-loaded set.
 */
@Service
@RequiredArgsConstructor
public class CommitteeCalendarService {

    private final CommitteeRepository                committees;
    private final CommitteeMeetingRepository         meetings;
    private final CommitteeStandingItemRepository    standingItems;
    private final CommitteeActionItemService         actionItems;
    private final AuditLogService                    audit;
    private final ObjectMapper                       json = new ObjectMapper();

    /**
     * Generate the next 12 months of scheduled meetings for one committee.
     * Idempotent on (committee_code, meeting_code) — re-running with the
     * same year skips meetings that already exist.
     *
     * <p>Default meeting time: Tuesday 10:00 (a Saudi-workweek-friendly
     * slot). The platform's calendar module re-aligns to workdays if
     * the generated date lands on a weekend.
     *
     * @return number of meetings actually created.
     */
    @Transactional
    public int generateAnnualCalendar(Long committeeId, int year) {
        Long org = TenantContext.getCurrentTenant();
        Committee committee = committees.findById(committeeId)
            .filter(c -> c.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Committee " + committeeId));

        String templateCode = committee.getCharterTemplateCode();
        // Standing items come from the template — if the committee wasn't
        // instantiated from a template, the calendar generator still works
        // but with an empty pre-populated agenda.
        List<CommitteeStandingItem> items = templateCode == null
            ? List.of()
            : standingItems.findByTemplateCodeOrderBySeqAsc(templateCode);

        List<LocalDate> dates = scheduleForCadence(committee.getMeetingCadence(), year);
        int created = 0;

        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            String code = String.format("MTG-%d-%s-%03d",
                year, committee.getCode().replace("COM-", "").toUpperCase(), i + 1);

            if (meetings.findByOrganisationIdAndMeetingCode(org, code).isPresent()) {
                continue;   // idempotent — skip if already exists
            }

            CommitteeMeeting m = new CommitteeMeeting();
            m.setOrganisationId(org);
            m.setCommitteeId(committee.getId());
            m.setCommitteeCode(committee.getCode());
            m.setCommitteeNameEn(committee.getNameEn());
            m.setMeetingCode(code);
            m.setTitleEn(committee.getNameEn() + " — " + quarterOf(date) + " " + year);
            m.setTitleAr(committee.getNameAr() == null ? null
                : committee.getNameAr() + " — " + quarterOf(date) + " " + year);
            m.setScheduledAt(LocalDateTime.of(date, LocalTime.of(10, 0)));
            m.setChairName(committee.getChairpersonName());
            m.setSecretaryName(committee.getSecretaryName());
            m.setStatus("SCHEDULED");
            m.setAgendaJson(buildAgendaJson(items));

            meetings.save(m);
            created++;
        }

        audit.log("CAL_GENERATE", "Committee", committeeId,
            "Generated " + created + " scheduled meetings for " + year
                + " (cadence=" + committee.getMeetingCadence() + ")");
        return created;
    }

    /**
     * Re-populate an existing meeting's agenda from the template's
     * standing items + open actions from the previous meeting of this
     * committee. Idempotent.
     */
    @Transactional
    public CommitteeMeeting populateAgenda(Long meetingId) {
        Long org = TenantContext.getCurrentTenant();
        CommitteeMeeting m = meetings.findByIdAndOrganisationId(meetingId, org)
            .orElseThrow(() -> new EntityNotFoundException("Meeting " + meetingId));

        Committee committee = committees.findById(m.getCommitteeId())
            .filter(c -> c.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Committee " + m.getCommitteeId()));

        List<CommitteeStandingItem> items = committee.getCharterTemplateCode() == null
            ? List.of()
            : standingItems.findByTemplateCodeOrderBySeqAsc(committee.getCharterTemplateCode());

        // Open actions from this committee, regardless of which prior meeting —
        // the platform rolls forward every still-open action.
        List<CommitteeActionItem> openActions = actionItems.openActionsForCommittee(m.getCommitteeId());

        m.setAgendaJson(buildAgendaJsonWithMattersArising(items, openActions));
        CommitteeMeeting saved = meetings.save(m);

        audit.log("CAL_POPULATE_AGENDA", "CommitteeMeeting", meetingId,
            "Repopulated agenda · standing=" + items.size()
                + " · open-actions=" + openActions.size());
        return saved;
    }

    // ── Cadence → list of dates ───────────────────────────────────────

    private List<LocalDate> scheduleForCadence(String cadence, int year) {
        if (cadence == null) cadence = "MONTHLY";
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        List<LocalDate> out = new ArrayList<>();

        // We anchor to the 2nd Tuesday of each cadence period — a common
        // Saudi board-pack convention because the prior week leaves time
        // for paper preparation + circulation.
        switch (cadence) {
            case "WEEKLY" -> {
                LocalDate d = jan1.with(TemporalAdjusters.firstInMonth(DayOfWeek.TUESDAY));
                while (d.getYear() == year) { out.add(d); d = d.plusWeeks(1); }
            }
            case "BIWEEKLY" -> {
                LocalDate d = jan1.with(TemporalAdjusters.firstInMonth(DayOfWeek.TUESDAY));
                while (d.getYear() == year) { out.add(d); d = d.plusWeeks(2); }
            }
            case "MONTHLY" -> {
                for (int m = 1; m <= 12; m++) {
                    LocalDate d = LocalDate.of(year, m, 1)
                        .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.TUESDAY));
                    out.add(d);
                }
            }
            case "QUARTERLY" -> {
                for (int m : new int[] { 2, 5, 8, 11 }) {       // mid-Q months
                    LocalDate d = LocalDate.of(year, m, 1)
                        .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.TUESDAY));
                    out.add(d);
                }
            }
            case "SEMI_ANNUAL" -> {
                out.add(LocalDate.of(year, 3, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.TUESDAY)));
                out.add(LocalDate.of(year, 9, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.TUESDAY)));
            }
            case "ANNUAL" -> {
                out.add(LocalDate.of(year, 6, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.TUESDAY)));
            }
            case "AD_HOC" -> { /* no scheduled meetings — committee meets only on demand */ }
            default       -> {
                // Unknown cadence — default to monthly so the committee still gets meetings.
                for (int m = 1; m <= 12; m++) {
                    LocalDate d = LocalDate.of(year, m, 1)
                        .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.TUESDAY));
                    out.add(d);
                }
            }
        }
        return out;
    }

    private static String quarterOf(LocalDate d) {
        int q = ((d.getMonthValue() - 1) / 3) + 1;
        return "Q" + q;
    }

    // ── Agenda JSON builders ──────────────────────────────────────────

    private String buildAgendaJson(List<CommitteeStandingItem> items) {
        return buildAgendaJsonWithMattersArising(items, List.of());
    }

    private String buildAgendaJsonWithMattersArising(
            List<CommitteeStandingItem> standing,
            List<CommitteeActionItem> openActions) {
        List<Map<String, Object>> out = new ArrayList<>();
        int seq = 1;

        // Item 1 — Welcome / apologies / quorum. Always first.
        out.add(rowOf(seq++, "Welcome, apologies, quorum check",
                              "الترحيب، الاعتذارات، فحص النصاب",
                              "Chair", 5, true, null));

        // Item 2 — Approval of previous minutes. Always second.
        out.add(rowOf(seq++, "Approval of previous minutes",
                              "اعتماد المحضر السابق",
                              "Chair", 5, true, null));

        // Item 3 — Matters arising / open actions. Always third — auto-
        // populated with the count of open actions from prior meetings.
        out.add(rowOf(seq++,
            "Matters arising — " + openActions.size() + " open actions",
            "الأمور المنبثقة — " + openActions.size() + " إجراءات مفتوحة",
            "Secretary", 10, true, "ACTIONS"));

        // Items 4+ — standing items in defined sequence.
        for (CommitteeStandingItem it : standing) {
            // Skip "AOB" entries from the template — we add AOB at the end.
            if (it.getTopicEn() != null && it.getTopicEn().equalsIgnoreCase("AOB")) continue;
            out.add(rowOf(seq++,
                it.getTopicEn(), it.getTopicAr(),
                it.getPresenterRole(),
                it.getMinutesAllocated(),
                it.getIsMandatory() != null && it.getIsMandatory() == 1,
                it.getSourceModule()));
        }

        // Item N-1 — AOB
        out.add(rowOf(seq++, "AOB (Any Other Business)", "أمور أخرى",
                      "Chair", 5, true, null));

        // Item N — Date of next meeting
        out.add(rowOf(seq, "Date of next meeting", "تاريخ الاجتماع القادم",
                      "Secretary", 2, true, null));

        try { return json.writeValueAsString(out); }
        catch (JsonProcessingException e) {
            // The map structures we build are JSON-safe; this should
            // never happen, but the contract requires a String return.
            return "[]";
        }
    }

    private static Map<String, Object> rowOf(int seq, String en, String ar,
                                              String presenter, int minutes,
                                              boolean mandatory, String sourceModule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", seq);
        row.put("topicEn", en);
        if (ar != null) row.put("topicAr", ar);
        if (presenter != null) row.put("presenter", presenter);
        row.put("minutesAllocated", minutes);
        row.put("isMandatory", mandatory);
        if (sourceModule != null) row.put("sourceModule", sourceModule);
        return row;
    }
}
