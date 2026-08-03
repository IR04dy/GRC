package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeStandingItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * S7b / CE-4 — <b>the agenda is built from the CAB queue.</b>
 *
 * <p><b>What was measured, 2026-08-03, before any of this was written.</b> {@code populateAgenda}
 * existed and built the agenda from the charter template's standing items plus open actions rolled
 * forward. It had no line for the thing a CAB meets to decide: the items referred to it. Dev
 * committee 74 had a referral awaiting decision and its meeting's agenda did not mention it.
 *
 * <p><b>And the naive fix would have been wrong in both directions.</b> The CAB queue has two
 * definitions that disagree:
 *
 * <pre>
 *   by change status  change_requests.status IN ('CAB_PENDING','IMPACT_ASSESSED')   dev 4   prod 3
 *   by referral       committee_referral    .status = 'PENDING'                     dev 2   prod 0
 * </pre>
 *
 * <p>3 of dev's 4 and <b>3 of prod's 3</b> queue items have no referral at all — referral is a
 * separate explicit call ({@code POST /{id}/refer-to-committee}), not a consequence of the
 * transition. And dev's only two {@code PENDING} referrals point at change ids <b>101 and 102</b>,
 * which exist in <em>no</em> organisation: orphans left when their changes were deleted, and
 * nothing detects or clears them. So an agenda keyed on {@code status = 'PENDING'} would have
 * listed two items that do not exist while omitting every real one — F2-96's shape, a confident
 * report wrong in both directions.
 *
 * <p>The control is V350's subject snapshot: only a referral whose {@code sourceCode} was recorded
 * at referral time reaches an agenda. Pre-V350 rows have none, so the orphans are excluded for a
 * <em>stated</em> reason rather than by luck.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CabQueueAgendaTest {

    private static final Long ORG = 1L;
    private static final Long COMMITTEE_ID = 74L;
    private static final Long MEETING_ID = 81L;

    @Mock CommitteeRepository             committees;
    @Mock CommitteeMeetingRepository      meetings;
    @Mock CommitteeStandingItemRepository standingItems;
    @Mock CommitteeActionItemService      actionItems;
    @Mock AuditLogService                 audit;
    @Mock CommitteeReferralService        referrals;

    @InjectMocks CommitteeCalendarService service;

    private CommitteeMeeting meeting;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        // @Autowired(required = false) field injection — Mockito uses the generated constructor for
        // the final dependencies and then skips property injection, the trap recorded on
        // CommitteeResolutionService#memberTermRepo.
        ReflectionTestUtils.setField(service, "referrals", referrals);

        Committee c = new Committee();
        c.setId(COMMITTEE_ID);
        c.setOrganisationId(ORG);
        c.setCharterTemplateCode(null);        // no template — isolates the queue block
        when(committees.findById(COMMITTEE_ID)).thenReturn(Optional.of(c));

        meeting = new CommitteeMeeting();
        meeting.setId(MEETING_ID);
        meeting.setOrganisationId(ORG);
        meeting.setCommitteeId(COMMITTEE_ID);
        meeting.setStatus("SCHEDULED");
        when(meetings.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(meeting));
        when(meetings.save(any(CommitteeMeeting.class))).thenAnswer(i -> i.getArgument(0));
        when(actionItems.openActionsForCommittee(anyLong())).thenReturn(List.of());
        when(referrals.awaitingDecision(COMMITTEE_ID)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static CommitteeReferral referral(String code, String en, String ar, String status) {
        CommitteeReferral r = new CommitteeReferral();
        r.setOrganisationId(ORG);
        r.setCommitteeId(COMMITTEE_ID);
        r.setSourceModule("CHANGE");
        r.setSourceEntityId(161L);
        r.setGate("CAB");
        r.setStatus(status);
        r.setSourceCode(code);
        r.setSubjectEn(en);
        r.setSubjectAr(ar);
        return r;
    }

    @Test
    @DisplayName("CE-4 — a referred item appears on the agenda, named by its reference")
    void aReferredItemReachesTheAgenda() {
        when(referrals.awaitingDecision(COMMITTEE_ID)).thenReturn(List.of(
            referral("CHG-2025-0014", "Upgrade the core banking gateway", "ترقية بوابة الأعمال المصرفية", "PENDING")));

        String agenda = service.populateAgenda(MEETING_ID).getAgendaJson();

        assertThat(agenda).contains("CHG-2025-0014");
        assertThat(agenda).contains("For decision: CHG-2025-0014 — Upgrade the core banking gateway");
        // House rule 7 — the Arabic line is present and is the Arabic subject, not the English one.
        assertThat(agenda).contains("ترقية بوابة الأعمال المصرفية");
    }

    @Test
    @DisplayName("items for decision come BEFORE the standing reports")
    void decisionsOutrankStandingReports() {
        // A charter template with eight standing reports must not push the change awaiting
        // authorisation past the point where the meeting runs out of time.
        when(referrals.awaitingDecision(COMMITTEE_ID)).thenReturn(List.of(
            referral("CHG-A", "Alpha", null, "PENDING"),
            referral("CHG-B", "Beta",  null, "SCHEDULED")));

        String agenda = service.populateAgenda(MEETING_ID).getAgendaJson();

        int alpha = agenda.indexOf("CHG-A");
        int beta  = agenda.indexOf("CHG-B");
        int aob   = agenda.indexOf("AOB");
        assertThat(alpha).isGreaterThan(0);
        assertThat(beta).isGreaterThan(alpha);
        assertThat(aob).as("AOB stays last").isGreaterThan(beta);
        // Items 1-3 are still the fixed preamble, in order.
        assertThat(agenda.indexOf("Welcome")).isLessThan(agenda.indexOf("Approval of previous minutes"));
        assertThat(agenda.indexOf("Approval of previous minutes")).isLessThan(agenda.indexOf("Matters arising"));
        assertThat(agenda.indexOf("Matters arising")).isLessThan(alpha);
    }

    @Test
    @DisplayName("an empty queue leaves the agenda exactly as it was")
    void anEmptyQueueChangesNothing() {
        String agenda = service.populateAgenda(MEETING_ID).getAgendaJson();

        assertThat(agenda).doesNotContain("For decision:");
        // The pre-S7b agenda is still produced in full — this block is additive.
        assertThat(agenda).contains("Welcome").contains("Matters arising").contains("AOB")
            .contains("Date of next meeting");
    }

    @Test
    @DisplayName("the audit line reports how many items for decision were placed")
    void theLedgerRecordsTheQueueSize() {
        when(referrals.awaitingDecision(COMMITTEE_ID)).thenReturn(List.of(
            referral("CHG-A", "Alpha", null, "PENDING")));

        service.populateAgenda(MEETING_ID);

        org.mockito.ArgumentCaptor<String> msg = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(audit).log(
            org.mockito.ArgumentMatchers.eq("CAL_POPULATE_AGENDA"),
            org.mockito.ArgumentMatchers.eq("CommitteeMeeting"),
            org.mockito.ArgumentMatchers.eq(MEETING_ID), msg.capture());
        assertThat(msg.getValue()).contains("items-for-decision=1");
    }

    @Test
    @DisplayName("an Arabic-less subject falls back to the English title, not to a bare id")
    void arabicFallsBackToTheEnglishTitle() {
        // An Arabic reader is better served by a title in the other language than by a reference
        // alone. The referring module may genuinely hold no Arabic title, and refusing the referral
        // over a label would block the governance path to protect the label (S6b-ii's lesson).
        when(referrals.awaitingDecision(COMMITTEE_ID)).thenReturn(List.of(
            referral("CHG-2025-0014", "Upgrade the gateway", null, "PENDING")));

        String agenda = service.populateAgenda(MEETING_ID).getAgendaJson();

        assertThat(agenda).contains("CHG-2025-0014 — Upgrade the gateway");
        // present twice: once in the English line, once as the Arabic fallback
        assertThat(agenda.split("Upgrade the gateway", -1).length - 1).isEqualTo(2);
    }

    // The orphan guard itself lives in CommitteeReferralService#awaitingDecision and is driven
    // against a real repository stub in CommitteeReferralGateTest — testing it here would mean
    // re-implementing the filter next to an assertion about it, which proves nothing.
}
