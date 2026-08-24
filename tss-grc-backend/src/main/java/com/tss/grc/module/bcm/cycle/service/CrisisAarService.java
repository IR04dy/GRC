package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisAar;
import com.tss.grc.module.bcm.cycle.entity.CrisisDecision;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.entity.CrisisRegulatoryNotification;
import com.tss.grc.module.bcm.cycle.entity.CrisisSituationReport;
import com.tss.grc.module.bcm.cycle.repository.CrisisAarRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisDecisionRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisRegulatoryNotificationRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisSituationReportRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tier-4 audit #23 — NCEMA After-Action Report builder.
 *
 * <p>Generates a structured AAR draft from an existing {@link CrisisEvent}
 * by aggregating its sitreps, decisions, and regulatory notifications.
 * Crisis manager then edits the markdown bodies and walks the AAR through
 * DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED.
 */
@Service
@RequiredArgsConstructor
public class CrisisAarService {

    private final CrisisAarRepository                       aars;
    private final CrisisEventRepository                     crises;
    private final CrisisSituationReportRepository           sitreps;
    private final CrisisDecisionRepository                  decisions;
    private final CrisisRegulatoryNotificationRepository    regNotifications;
    private final AuditLogService                           audit;
    private final ApplicationEventPublisher                 eventPublisher;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long org() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public List<CrisisAar> list() {
        return aars.findByOrganisationIdOrderByCreatedAtDesc(org());
    }

    @Transactional(readOnly = true)
    public List<CrisisAar> listForEvent(Long crisisEventId) {
        return aars.findByOrganisationIdAndCrisisEventIdOrderByCreatedAtDesc(org(), crisisEventId);
    }

    @Transactional(readOnly = true)
    public CrisisAar get(Long id) {
        Long o = org();
        return aars.findById(id)
            .filter(a -> Objects.equals(a.getOrganisationId(), o))
            .orElseThrow(() -> new EntityNotFoundException("AAR " + id + " not found"));
    }

    /**
     * Build a new AAR draft seeded with auto-generated narratives from the
     * parent crisis event's sitreps + decisions + regulatory notifications.
     */
    @Transactional
    public CrisisAar generate(Long crisisEventId) {
        Long o = org();
        CrisisEvent ce = crises.findByIdAndOrganisationId(crisisEventId, o)
            .orElseThrow(() -> new EntityNotFoundException(
                "CrisisEvent " + crisisEventId + " not found for tenant " + o));

        List<CrisisSituationReport> sitRepList =
            sitreps.findByOrganisationIdAndCrisisEventIdOrderByReportedAtAsc(o, crisisEventId);
        List<CrisisDecision> decisionList =
            decisions.findByOrganisationIdAndCrisisEventIdOrderByDecidedAtAsc(o, crisisEventId);
        List<CrisisRegulatoryNotification> regList =
            regNotifications.findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(o, crisisEventId);

        CrisisAar a = new CrisisAar();
        a.setOrganisationId(o);
        a.setCrisisEventId(crisisEventId);
        a.setReportCode(nextReportCode(o));
        a.setTitleEn(cap("After-Action Report — " + safe(ce.getTitleEn()), TITLE_MAX));
        a.setTitleAr(cap("تقرير ما بعد الحدث — " + safe(ce.getTitleAr() == null ? ce.getTitleEn() : ce.getTitleAr()), TITLE_MAX));

        a.setExecutiveSummaryEn(buildExecSummaryEn(ce, sitRepList.size(), decisionList.size(), regList.size()));
        a.setExecutiveSummaryAr(buildExecSummaryAr(ce, sitRepList.size(), decisionList.size(), regList.size()));

        a.setTimelineMdEn(buildTimelineMd(ce, sitRepList));
        a.setTimelineMdAr("راجع الإصدار الإنجليزي للجدول الزمني التفصيلي ثم ترجمه قبل النشر.");

        a.setDecisionsMdEn(buildDecisionsMd(decisionList));
        a.setDecisionsMdAr("راجع قائمة القرارات الإنجليزية ثم ترجمها قبل النشر.");

        a.setRegulatoryNotificationsMd(buildRegulatoryNotificationsMd(regList, ce));

        a.setWhatWorkedMdEn("- (Crisis manager: list what worked — comms cadence, decision speed, escalation paths.)\n");
        a.setWhatFailedMdEn("- (Crisis manager: list what failed — control gaps, missing runbooks, supplier delays.)\n");
        a.setRootCauseMdEn("(Crisis manager: pick PEOPLE / PROCESS / TECHNOLOGY / SUPPLIER / EXTERNAL and document the chain.)");
        a.setRecommendationsMdEn("- (Crisis manager: list recommended corrective actions with owner + due date.)\n");

        CrisisAar saved = aars.save(a);
        audit.log("GENERATE", "CrisisAar", saved.getId(),
            "AAR " + saved.getReportCode() + " generated for crisis event " + crisisEventId);
        // Fan out to F8 — the engine listens for CRISIS_AAR_GENERATED and
        // matches enabled rules against the payload. Seed rules can route
        // AARs from SEV-1/SEV-2 events to the executive sponsor, or auto-
        // notify the BCM champion the moment a draft is created. Same shape
        // as FindingService (PR #50) — correlationId is the human-readable
        // reportCode so downstream rule_runs thread cleanly to the AAR.
        eventPublisher.publishEvent(new DomainEvent(
            o, "CRISIS_AAR_GENERATED",
            "CrisisAar", saved.getId(), saved.getReportCode(),
            payloadFor(saved, ce, null)));
        return saved;
    }

    public record UpdateBody(
        String titleEn, String titleAr,
        String executiveSummaryEn, String executiveSummaryAr,
        String timelineMdEn, String timelineMdAr,
        String decisionsMdEn, String decisionsMdAr,
        String whatWorkedMdEn, String whatWorkedMdAr,
        String whatFailedMdEn, String whatFailedMdAr,
        String rootCauseMdEn, String rootCauseMdAr,
        String recommendationsMdEn, String recommendationsMdAr,
        String regulatoryNotificationsMd,
        String lessonsLearnedIdsJson,
        String correctiveActionIdsJson,
        String gapIdsJson
    ) {}

    @Transactional
    public CrisisAar update(Long id, UpdateBody body) {
        CrisisAar a = get(id);
        if (!"DRAFT".equals(a.getStatus()) && !"UNDER_REVIEW".equals(a.getStatus())) {
            throw new IllegalStateException(
                "AAR is in status " + a.getStatus() + "; editable only in DRAFT or UNDER_REVIEW");
        }
        if (body.titleEn() != null)                  a.setTitleEn(body.titleEn());
        if (body.titleAr() != null)                  a.setTitleAr(body.titleAr());
        if (body.executiveSummaryEn() != null)       a.setExecutiveSummaryEn(body.executiveSummaryEn());
        if (body.executiveSummaryAr() != null)       a.setExecutiveSummaryAr(body.executiveSummaryAr());
        if (body.timelineMdEn() != null)             a.setTimelineMdEn(body.timelineMdEn());
        if (body.timelineMdAr() != null)             a.setTimelineMdAr(body.timelineMdAr());
        if (body.decisionsMdEn() != null)            a.setDecisionsMdEn(body.decisionsMdEn());
        if (body.decisionsMdAr() != null)            a.setDecisionsMdAr(body.decisionsMdAr());
        if (body.whatWorkedMdEn() != null)           a.setWhatWorkedMdEn(body.whatWorkedMdEn());
        if (body.whatWorkedMdAr() != null)           a.setWhatWorkedMdAr(body.whatWorkedMdAr());
        if (body.whatFailedMdEn() != null)           a.setWhatFailedMdEn(body.whatFailedMdEn());
        if (body.whatFailedMdAr() != null)           a.setWhatFailedMdAr(body.whatFailedMdAr());
        if (body.rootCauseMdEn() != null)            a.setRootCauseMdEn(body.rootCauseMdEn());
        if (body.rootCauseMdAr() != null)            a.setRootCauseMdAr(body.rootCauseMdAr());
        if (body.recommendationsMdEn() != null)      a.setRecommendationsMdEn(body.recommendationsMdEn());
        if (body.recommendationsMdAr() != null)      a.setRecommendationsMdAr(body.recommendationsMdAr());
        if (body.regulatoryNotificationsMd() != null) a.setRegulatoryNotificationsMd(body.regulatoryNotificationsMd());
        if (body.lessonsLearnedIdsJson() != null)    a.setLessonsLearnedIdsJson(body.lessonsLearnedIdsJson());
        if (body.correctiveActionIdsJson() != null)  a.setCorrectiveActionIdsJson(body.correctiveActionIdsJson());
        if (body.gapIdsJson() != null)               a.setGapIdsJson(body.gapIdsJson());
        a.setUpdatedAt(LocalDateTime.now());
        CrisisAar saved = aars.save(a);
        audit.log("UPDATE", "CrisisAar", id, "AAR draft updated");
        return saved;
    }

    @Transactional
    public CrisisAar transition(Long id, String target, Long digitalSignatureTxnId) {
        CrisisAar a = get(id);
        String from = a.getStatus();
        String t = target == null ? "" : target.toUpperCase(Locale.ROOT);
        boolean ok;
        switch (t) {
            case "UNDER_REVIEW" -> ok = "DRAFT".equals(from);
            case "APPROVED"     -> { ok = "UNDER_REVIEW".equals(from);
                                     if (ok) { a.setApprovedAt(LocalDateTime.now()); a.setDigitalSignatureTxnId(digitalSignatureTxnId); } }
            case "PUBLISHED"    -> { ok = "APPROVED".equals(from);
                                     if (ok) a.setPublishedAt(LocalDateTime.now()); }
            case "RESCINDED"    -> ok = "PUBLISHED".equals(from) || "APPROVED".equals(from);
            default -> throw new IllegalArgumentException(
                "target must be UNDER_REVIEW, APPROVED, PUBLISHED, or RESCINDED");
        }
        if (!ok) {
            throw new IllegalStateException(
                "Illegal transition: " + from + " → " + t);
        }
        a.setStatus(t);
        a.setUpdatedAt(LocalDateTime.now());
        CrisisAar saved = aars.save(a);
        audit.log("TRANSITION", "CrisisAar", id,
            "AAR " + a.getReportCode() + " " + from + " → " + t);
        // Fan out to F8 — single CRISIS_AAR_TRANSITIONED event matches the
        // FINDING_TRANSITIONED / CAPA_TASK_REVIEWED shape from PRs #46/#48/
        // #50. Rules target a specific step by adding {"status":"APPROVED"}
        // to condition_json rather than us emitting four distinct event
        // names. previousStatus is carried so a rule can fire only on
        // UNDER_REVIEW → APPROVED (the sign-off moment) and skip the
        // APPROVED → RESCINDED rollback path.
        eventPublisher.publishEvent(new DomainEvent(
            org(), "CRISIS_AAR_TRANSITIONED",
            "CrisisAar", id, saved.getReportCode(),
            payloadFor(saved, null, from)));
        return saved;
    }

    // ── narrative builders ───────────────────────────────────────────

    private String buildExecSummaryEn(CrisisEvent ce, int sitReps, int decisions, int regs) {
        return "Crisis event " + safe(ce.getEventCode())
             + " (severity " + ce.getSeverity() + ") was detected at "
             + (ce.getDetectionTime() == null ? "(time not recorded)" : ce.getDetectionTime().format(TS))
             + " and " + ("CLOSED".equals(ce.getStatus()) ? "closed at " + (ce.getClosureTime()==null?"-":ce.getClosureTime().format(TS))
                                                          : "is in status " + ce.getStatus())
             + ". The crisis team logged " + sitReps + " situation report(s), recorded "
             + decisions + " formal decision(s), and filed " + regs + " regulatory notification(s). "
             + "Personal data " + (Boolean.TRUE.equals(ce.getPersonalDataAffected()) ? "WAS" : "was not")
             + " affected; this event " + (Boolean.TRUE.equals(ce.getCyberRelated()) ? "IS" : "is not")
             + " cyber-related.";
    }

    private String buildExecSummaryAr(CrisisEvent ce, int sitReps, int decisions, int regs) {
        return "تم رصد الحدث رقم " + safe(ce.getEventCode())
             + " بدرجة شدة " + ce.getSeverity()
             + ". قام فريق الأزمة بإصدار " + sitReps + " تقرير حالة و"
             + decisions + " قرار رسمي وإرسال " + regs + " إشعار تنظيمي. "
             + "هذه مسودة تلقائية يجب على قائد الأزمة مراجعتها قبل اعتمادها.";
    }

    private String buildTimelineMd(CrisisEvent ce, List<CrisisSituationReport> reports) {
        StringBuilder md = new StringBuilder();
        md.append("| Time (UTC) | Status update | Summary |\n");
        md.append("|---|---|---|\n");
        if (ce.getDetectionTime() != null) {
            md.append("| ").append(ce.getDetectionTime().format(TS))
              .append(" | DETECTED | Event detected (trigger: ")
              .append(safe(ce.getTriggerSource())).append(") |\n");
        }
        if (ce.getActivationTime() != null) {
            md.append("| ").append(ce.getActivationTime().format(TS))
              .append(" | ACTIVATED | Crisis team activated |\n");
        }
        for (CrisisSituationReport r : reports) {
            md.append("| ").append(r.getReportedAt() == null ? "-" : r.getReportedAt().format(TS))
              .append(" | ").append(safe(r.getStatusUpdate()))
              .append(" | ").append(safeSingleLine(r.getSummary()))
              .append(r.getSeverityChange() == null ? "" : " (severity → " + r.getSeverityChange() + ")")
              .append(" |\n");
        }
        if (ce.getClosureTime() != null) {
            md.append("| ").append(ce.getClosureTime().format(TS))
              .append(" | ").append(safe(ce.getStatus())).append(" | Event closed |\n");
        }
        return md.toString();
    }

    private String buildDecisionsMd(List<CrisisDecision> list) {
        if (list.isEmpty()) return "_No formal decisions were recorded during this event._";
        StringBuilder md = new StringBuilder();
        md.append("| Time | Decided by | Decision | Rationale |\n");
        md.append("|---|---|---|---|\n");
        for (CrisisDecision d : list) {
            md.append("| ").append(d.getDecidedAt() == null ? "-" : d.getDecidedAt().format(TS))
              .append(" | ").append(safe(d.getDecidedByName()))
              .append(" | ").append(safeSingleLine(d.getDecisionText()))
              .append(" | ").append(safeSingleLine(d.getRationale()))
              .append(" |\n");
        }
        return md.toString();
    }

    private String buildRegulatoryNotificationsMd(List<CrisisRegulatoryNotification> list, CrisisEvent ce) {
        StringBuilder md = new StringBuilder();
        if (Boolean.TRUE.equals(ce.getPersonalDataAffected()) && ce.getPdpl72hDeadlineAt() != null) {
            md.append("**PDPL Article 20 deadline:** ").append(ce.getPdpl72hDeadlineAt().format(TS))
              .append(Boolean.TRUE.equals(ce.getPdpl72hBreached()) ? "  ⚠️ BREACHED\n\n" : "\n\n");
        }
        if (list.isEmpty()) {
            md.append("_No regulatory notifications were filed during this event._");
            return md.toString();
        }
        md.append("| Regulator | Notified at | Method | Reference | Acknowledged |\n");
        md.append("|---|---|---|---|---|\n");
        for (CrisisRegulatoryNotification n : list) {
            md.append("| ").append(safe(n.getRegulator()))
              .append(" | ").append(n.getNotifiedAt() == null ? "-" : n.getNotifiedAt().format(TS))
              .append(" | ").append(safe(n.getNotificationMethod()))
              .append(" | ").append(safe(n.getReferenceNumber()))
              .append(" | ").append(Boolean.TRUE.equals(n.getAcknowledgementReceived()) ? "Yes" : "No")
              .append(" |\n");
        }
        return md.toString();
    }

    /**
     * AAR-YYYY-NNNNNN, monotonically per-tenant + per-year.
     *
     * <p>Three problems the previous implementation had:
     *   1. {@code aars.findBy...().size()} loaded EVERY AAR into memory just
     *      to count — O(n) memory + O(n) DB on a tenant with thousands of
     *      events. Replaced with a {@code count*} repo method (single COUNT
     *      query, no row hydration).
     *   2. The sequence was global across all years. After year crossover
     *      a new year's first AAR jumped from N+1 instead of resetting to 1.
     *      Now the count is scoped to the {@code AAR-YYYY-} prefix.
     *   3. Two concurrent {@code generate()} calls could compute the same N
     *      and crash one of them at the DB unique constraint (added in V124).
     *      We retry once with N+1 to absorb the obvious race. Higher
     *      contention would need a database SEQUENCE; this is acceptable for
     *      the expected one-AAR-per-major-event cadence.
     */
    private String nextReportCode(Long orgId) {
        int year = LocalDateTime.now().getYear();
        String prefix = String.format("AAR-%d-", year);
        long n = aars.countByOrganisationIdAndReportCodeStartingWith(orgId, prefix) + 1;
        String candidate = String.format("%s%06d", prefix, n);
        // Tiny race-tolerance loop: if the candidate collides (because two
        // concurrent generators picked the same N) bump and try again.
        // Bounded at +5 attempts so we never spin.
        for (int attempt = 0; attempt < 5; attempt++) {
            if (aars.findByOrganisationIdAndReportCode(orgId, candidate).isEmpty()) {
                return candidate;
            }
            n++;
            candidate = String.format("%s%06d", prefix, n);
        }
        return candidate;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /** F2-338 — the AAR title is composed from the crisis event's title
     *  ({@code bcm.crisis_events.title_en} is NVARCHAR(500)) plus a fixed prefix, and stored in
     *  {@code bcm.crisis_aar.title_en/title_ar} (NVARCHAR(300), see {@code @Column(length = 300)}
     *  on {@link CrisisAar}). Cap the composed string to the column length so a long-but-legal
     *  crisis title cannot overflow it and turn AAR generation into a 500 — F2-185b: a length
     *  budget must account for everything the code adds, not just the caller-supplied field. */
    static final int TITLE_MAX = 300;
    private static String cap(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String safeSingleLine(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }

    /**
     * Canonical payload Map for F8 rule-condition matching on CrisisAar
     * events. Engine evaluates equality + contains on these string keys;
     * values are coerced to "" so the matcher never NPEs on a null.
     *
     * <p>{@code severity} is carried from the parent {@link CrisisEvent}
     * when available — for {@code TRANSITIONED} we load it lazily so a
     * rule like "AAR approved on SEV-1 event → notify executive sponsor"
     * can match. {@code previousStatus} only carries on TRANSITIONED.
     */
    private Map<String, Object> payloadFor(CrisisAar a, CrisisEvent ce, String previousStatus) {
        Integer severity = null;
        if (ce != null) {
            severity = ce.getSeverity();
        } else if (a.getCrisisEventId() != null) {
            severity = crises.findByIdAndOrganisationId(a.getCrisisEventId(), a.getOrganisationId())
                .map(CrisisEvent::getSeverity).orElse(null);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportCode",     nullSafe(a.getReportCode()));
        m.put("crisisEventId",  a.getCrisisEventId() == null ? "" : String.valueOf(a.getCrisisEventId()));
        m.put("severity",       severity == null ? "" : String.valueOf(severity));
        m.put("status",         nullSafe(a.getStatus()));
        m.put("titleEn",        nullSafe(a.getTitleEn()));
        if (previousStatus != null) m.put("previousStatus", previousStatus);
        return m;
    }

    private static String nullSafe(String v) { return v == null ? "" : v; }
}
