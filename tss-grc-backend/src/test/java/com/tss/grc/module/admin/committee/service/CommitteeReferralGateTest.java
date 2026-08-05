package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeGateMap;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.repository.CommitteeGateMapRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.change_mgmt.service.ChangeSoD;
import jakarta.persistence.EntityNotFoundException;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W1-1 / independent-assessment finding F-2 — the committee referral gate had
 * **no automated test**, despite being the control that makes a change or policy
 * approval depend on a real committee decision (Track D). Everything the platform
 * claims about committee-gated authorisation rests on
 * {@link CommitteeReferralService#assertDecisionApproves}.
 *
 * <p>These tests pin each refusal reason separately, so a future refactor cannot
 * quietly drop one and still look green — the failure modes that matter are
 * "approved without quorate minutes", "approved without enough FOR votes", and
 * "approved when the committee actually rejected/deferred".
 *
 * <p>Also documents a behaviour worth an explicit decision (see
 * {@link #resolution_with_no_meeting_skips_the_minutes_requirement}): a resolution
 * carrying no {@code meetingId} bypasses the MINUTES_APPROVED check entirely.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeReferralGateTest {

    private static final Long ORG      = 1L;
    private static final String MODULE = "CHANGE";
    private static final Long ENTITY   = 500L;
    /** S7b — a referral states what the committee is being asked to decide (V350). */
    private static final CommitteeReferralService.SubjectRef SUBJECT =
        new CommitteeReferralService.SubjectRef("CHG-2026-0042", "Upgrade the gateway", "ترقية البوابة");

    @Mock CommitteeReferralRepository   referralRepo;
    @Mock CommitteeGateMapRepository    gateMapRepo;
    @Mock CommitteeRepository           committeeRepo;
    @Mock CommitteeResolutionRepository resolutionRepo;
    @Mock CommitteeMeetingRepository    meetingRepo;
    /** F2-113 — the roster, for recusalCoverage: a committee with no linked seat cannot be
     *  recused by id however well the referred record captured its people. */
    @Mock CommitteeMemberTermRepository memberTermRepo;

    @InjectMocks CommitteeReferralService service;

    @BeforeEach
    void bindTenant() {
        TenantContext.setCurrentTenant(ORG);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private CommitteeReferral referral(Long resolutionId) {
        CommitteeReferral r = new CommitteeReferral();
        r.setId(10L);
        r.setOrganisationId(ORG);
        r.setSourceModule(MODULE);
        r.setSourceEntityId(ENTITY);
        r.setCommitteeId(7L);
        r.setGate("CAB");
        r.setResolutionId(resolutionId);
        return r;
    }

    private CommitteeResolution resolution(String outcome, Integer voteFor, Long meetingId) {
        CommitteeResolution res = new CommitteeResolution();
        res.setId(99L);
        res.setOrganisationId(ORG);
        res.setCommitteeId(7L);
        res.setMeetingId(meetingId);
        res.setOutcome(outcome);
        res.setVoteFor(voteFor);
        return res;
    }

    private CommitteeMeeting meeting(String status) {
        CommitteeMeeting m = new CommitteeMeeting();
        m.setId(55L);
        m.setOrganisationId(ORG);
        m.setCommitteeId(7L);
        m.setStatus(status);
        return m;
    }

    private void givenReferral(CommitteeReferral ref) {
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
                .thenReturn(Optional.ofNullable(ref));
    }

    // ── the gate: every refusal reason, pinned individually ──────────────────

    @Test
    void refuses_when_there_is_no_referral_at_all() {
        givenReferral(null);
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No committee referral");
    }

    @Test
    void refuses_when_the_motion_has_not_been_linked_yet() {
        givenReferral(referral(null));
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not been raised and linked");
    }

    @Test
    void refuses_when_the_linked_resolution_is_missing() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resolution is missing");
    }

    @Test
    void refuses_when_the_minutes_are_not_approved_yet() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("APPROVED", 3, 55L)));
        when(meetingRepo.findByIdAndOrganisationId(55L, ORG))
                .thenReturn(Optional.of(meeting("IN_PROGRESS")));
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minutes are not approved")
                .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    void refuses_when_the_linked_meeting_is_missing() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("APPROVED", 3, 55L)));
        when(meetingRepo.findByIdAndOrganisationId(55L, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("meeting is missing");
    }

    @Test
    void refuses_when_the_committee_rejected_the_item() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("REJECTED", 0, 55L)));
        when(meetingRepo.findByIdAndOrganisationId(55L, ORG))
                .thenReturn(Optional.of(meeting("MINUTES_APPROVED")));
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not approve")
                .hasMessageContaining("REJECTED");
    }

    @Test
    void refuses_when_the_committee_deferred_the_item() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("DEFERRED", 0, 55L)));
        when(meetingRepo.findByIdAndOrganisationId(55L, ORG))
                .thenReturn(Optional.of(meeting("MINUTES_APPROVED")));
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not approve");
    }

    @Test
    void refuses_when_the_for_votes_are_short_of_the_required_minimum() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("APPROVED", 1, 55L)));
        when(meetingRepo.findByIdAndOrganisationId(55L, ORG))
                .thenReturn(Optional.of(meeting("MINUTES_APPROVED")));
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("short of the required 2")
                .hasMessageContaining("has 1");
    }

    @Test
    void treats_a_null_for_vote_count_as_zero_rather_than_passing() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("APPROVED", null, 55L)));
        when(meetingRepo.findByIdAndOrganisationId(55L, ORG))
                .thenReturn(Optional.of(meeting("MINUTES_APPROVED")));
        assertThatThrownBy(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("short of the required 1");
    }

    // ── the pass: all three conditions satisfied together ───────────────────

    @Test
    void passes_only_when_approved_on_quorate_minutes_with_enough_votes() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("APPROVED", 3, 55L)));
        when(meetingRepo.findByIdAndOrganisationId(55L, ORG))
                .thenReturn(Optional.of(meeting("MINUTES_APPROVED")));
        assertThatCode(() -> service.assertDecisionApproves(MODULE, ENTITY, 3))
                .doesNotThrowAnyException();
    }

    @Test
    void passes_at_the_exact_minimum_vote_boundary() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("APPROVED", 2, 55L)));
        when(meetingRepo.findByIdAndOrganisationId(55L, ORG))
                .thenReturn(Optional.of(meeting("MINUTES_APPROVED")));
        assertThatCode(() -> service.assertDecisionApproves(MODULE, ENTITY, 2))
                .doesNotThrowAnyException();
    }

    /**
     * Documents current behaviour rather than endorsing it: when the deciding
     * resolution carries no {@code meetingId}, the MINUTES_APPROVED requirement is
     * skipped entirely, so an approval can gate a change without any quorate,
     * e-signed minutes behind it. Reachable only by creating a resolution outside a
     * meeting. Flagged for an explicit decision — if the answer is "never allowed",
     * the fix is to refuse a null meetingId here and this test inverts.
     */
    @Test
    void resolution_with_no_meeting_skips_the_minutes_requirement() {
        givenReferral(referral(99L));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("APPROVED", 1, null)));
        assertThatCode(() -> service.assertDecisionApproves(MODULE, ENTITY, 1))
                .doesNotThrowAnyException();
        verify(meetingRepo, never()).findByIdAndOrganisationId(any(), any());
    }

    // ── refer(): gate-map resolution is where finding F-4 bites ─────────────

    @Test
    void refer_fails_with_a_clear_message_when_no_gate_map_is_configured() {
        // F-4: admin.committee_gate_map is EMPTY in the reference environment, so
        // this is the error a real CAB referral hits today. Stage S4 seeds the map.
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
                .thenReturn(Optional.empty());
        when(gateMapRepo.findByOrganisationIdAndGate(ORG, "CAB")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refer(MODULE, ENTITY, "CAB", "APPROVAL", null, "admin", null, SUBJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No committee is configured for gate CAB");
    }

    @Test
    void refer_resolves_the_committee_from_the_gate_map_when_none_is_passed() {
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
                .thenReturn(Optional.empty());
        CommitteeGateMap map = new CommitteeGateMap();
        map.setOrganisationId(ORG);
        map.setGate("CAB");
        map.setCommitteeId(7L);
        when(gateMapRepo.findByOrganisationIdAndGate(ORG, "CAB")).thenReturn(Optional.of(map));
        Committee c = new Committee();
        c.setId(7L);
        c.setOrganisationId(ORG);
        c.setStatus("ACTIVE");
        when(committeeRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(c));
        when(referralRepo.save(any(CommitteeReferral.class))).thenAnswer(i -> i.getArgument(0));

        CommitteeReferral saved = service.refer(MODULE, ENTITY, "CAB", null, null, "admin",
            CommitteeReferralService.Recusal.byName("Requester Name"), SUBJECT);

        assertThat(saved.getCommitteeId()).isEqualTo(7L);
        assertThat(saved.getMode()).isEqualTo("APPROVAL");          // defaulted from null
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRecusalNames()).isEqualTo("Requester Name");
        assertThat(saved.getOrganisationId()).isEqualTo(ORG);
    }

    @Test
    void refer_is_idempotent_and_reuses_the_existing_active_referral() {
        CommitteeReferral existing = referral(null);
        givenReferral(existing);

        CommitteeReferral result = service.refer(MODULE, ENTITY, "CAB", "APPROVAL", 7L, "admin", null, SUBJECT);

        assertThat(result).isSameAs(existing);
        verify(referralRepo, never()).save(any());
    }

    @Test
    void refer_refuses_a_dissolved_committee() {
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
                .thenReturn(Optional.empty());
        Committee c = new Committee();
        c.setId(7L);
        c.setOrganisationId(ORG);
        c.setStatus("DISSOLVED");
        when(committeeRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.refer(MODULE, ENTITY, "CAB", "APPROVAL", 7L, "admin", null, SUBJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is dissolved");
    }

    @Test
    void refer_refuses_a_committee_from_another_organisation() {
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
                .thenReturn(Optional.empty());
        when(committeeRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refer(MODULE, ENTITY, "CAB", "APPROVAL", 7L, "admin", null, SUBJECT))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("not found in this org");
    }

    // ── linkResolution(): binding the deciding motion ────────────────────────

    @Test
    void linkResolution_binds_the_meeting_and_marks_the_referral_scheduled() {
        givenReferral(referral(null));
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG))
                .thenReturn(Optional.of(resolution("DEFERRED", 0, 55L)));
        when(referralRepo.save(any(CommitteeReferral.class))).thenAnswer(i -> i.getArgument(0));

        CommitteeReferral linked = service.linkResolution(MODULE, ENTITY, 99L);

        assertThat(linked.getResolutionId()).isEqualTo(99L);
        assertThat(linked.getMeetingId()).isEqualTo(55L);
        assertThat(linked.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void linkResolution_refuses_a_resolution_from_a_different_committee() {
        givenReferral(referral(null));                       // referral committee = 7
        CommitteeResolution foreign = resolution("APPROVED", 3, 55L);
        foreign.setCommitteeId(8L);                          // different committee
        when(resolutionRepo.findByIdAndOrganisationId(99L, ORG)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.linkResolution(MODULE, ENTITY, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not from the referral's committee");
    }

    @Test
    void linkResolution_refuses_when_there_is_no_referral() {
        givenReferral(null);
        assertThatThrownBy(() -> service.linkResolution(MODULE, ENTITY, 99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("No committee referral");
    }

    // ══════════════════ S7b / CE-4 — the CAB queue, and the orphan guard ══════════════════

    private void givenActiveCommittee(Long id) {
        Committee c = new Committee();
        c.setId(id);
        c.setOrganisationId(ORG);
        c.setStatus("ACTIVE");
        when(committeeRepo.findByIdAndOrganisationId(id, ORG)).thenReturn(Optional.of(c));
    }

    private CommitteeReferral queued(Long id, String code, String status) {
        CommitteeReferral r = new CommitteeReferral();
        r.setId(id);
        r.setOrganisationId(ORG);
        r.setCommitteeId(74L);
        r.setSourceModule(MODULE);
        r.setSourceEntityId(id);
        r.setGate("CAB");
        r.setStatus(status);
        r.setSourceCode(code);
        r.setSubjectEn(code == null ? null : ("Subject for " + code));
        return r;
    }

    @Test
    @DisplayName("S7b — refer() records the subject the agenda needs")
    void referRecordsTheAgendaSubject() {
        givenActiveCommittee(7L);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
            .thenReturn(Optional.empty());
        when(referralRepo.save(any(CommitteeReferral.class))).thenAnswer(i -> i.getArgument(0));

        CommitteeReferral saved = service.refer(MODULE, ENTITY, "CAB", "APPROVAL", 7L, "admin", null, SUBJECT);

        assertThat(saved.getSourceCode()).isEqualTo("CHG-2026-0042");
        assertThat(saved.getSubjectEn()).isEqualTo("Upgrade the gateway");
        assertThat(saved.getSubjectAr()).isEqualTo("ترقية البوابة");
    }

    @Test
    @DisplayName("S7b — SubjectRef.NONE records nothing, and the item stays off the agenda")
    void referWithNoSubjectRecordsNothing() {
        // The omission has to be possible — a caller may genuinely hold no reference — but it is
        // explicit at the call site rather than an overload, so it is a decision on the record.
        givenActiveCommittee(7L);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
            .thenReturn(Optional.empty());
        when(referralRepo.save(any(CommitteeReferral.class))).thenAnswer(i -> i.getArgument(0));

        CommitteeReferral saved = service.refer(MODULE, ENTITY, "CAB", "APPROVAL", 7L, "admin", null,
            CommitteeReferralService.SubjectRef.NONE);

        assertThat(saved.getSourceCode()).isNull();
        assertThat(CommitteeReferralService.SubjectRef.NONE.isPresent()).isFalse();
        assertThat(SUBJECT.isPresent()).isTrue();
    }

    @Test
    @DisplayName("⚠ S7b — a referral with no recorded subject is NOT in the CAB queue")
    void theQueueExcludesSubjectlessReferrals() {
        // THE measurement this guard exists for: dev's only two PENDING referrals (#7 and #8,
        // created by `compliance`) point at change ids 101 and 102, and no change request with
        // either id exists in ANY organisation — orphans left when their changes were deleted, and
        // nothing detects or clears them. A queue keyed on status alone would have put two
        // non-existent items on an agenda while omitting all three real ones. Requiring V350's
        // snapshot excludes them for a stated reason rather than by luck.
        when(referralRepo.findByOrganisationIdAndCommitteeIdAndStatusInOrderByIdAsc(
                eq(ORG), eq(74L), any()))
            .thenReturn(List.of(
                queued(7L,   null,            "PENDING"),      // orphan → change 101
                queued(8L,   null,            "PENDING"),      // orphan → change 102
                queued(161L, "CHG-2025-0014", "PENDING"),      // real
                queued(162L, "CHG-2025-0020", "SCHEDULED")));  // real, already on a meeting

        List<CommitteeReferral> queue = service.awaitingDecision(74L);

        assertThat(queue).extracting(CommitteeReferral::getSourceCode)
            .containsExactly("CHG-2025-0014", "CHG-2025-0020");
        assertThat(queue).hasSize(2);
    }

    @Test
    @DisplayName("S7b — a blank source code counts as absent, not as a code")
    void aBlankCodeIsNotACode() {
        when(referralRepo.findByOrganisationIdAndCommitteeIdAndStatusInOrderByIdAsc(
                eq(ORG), eq(74L), any()))
            .thenReturn(List.of(queued(9L, "   ", "PENDING")));

        assertThat(service.awaitingDecision(74L)).isEmpty();
    }

    @Test
    @DisplayName("S7b — the queue asks only for PENDING and SCHEDULED, never a decided referral")
    void theQueueAsksOnlyForUndecidedStatuses() {
        // DECIDED / WITHDRAWN items are finished with; re-tabling them would be the agenda
        // repeating a decision the committee has already taken.
        when(referralRepo.findByOrganisationIdAndCommitteeIdAndStatusInOrderByIdAsc(
                eq(ORG), eq(74L), any())).thenReturn(List.of());

        service.awaitingDecision(74L);

        org.mockito.ArgumentCaptor<java.util.Collection<String>> statuses =
            org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(referralRepo).findByOrganisationIdAndCommitteeIdAndStatusInOrderByIdAsc(
            eq(ORG), eq(74L), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder("PENDING", "SCHEDULED");
    }

    @Test
    @DisplayName("S7b — a null committee id yields an empty queue rather than an unscoped read")
    void aNullCommitteeIsNotAWildcard() {
        assertThat(service.awaitingDecision(null)).isEmpty();
        verify(referralRepo, never()).findByOrganisationIdAndCommitteeIdAndStatusInOrderByIdAsc(
            any(), any(), any());
    }

    // ── F2-113: what the referral persists, and how far the recusal can reach ───────────────

    @Test
    @DisplayName("F2-113 — both halves of the recusal list are persisted, as two unpaired CSVs")
    void bothHalvesOfTheRecusalListArePersisted() {
        givenActiveCommittee(7L);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
            .thenReturn(Optional.empty());
        when(referralRepo.save(any(CommitteeReferral.class))).thenAnswer(i -> i.getArgument(0));

        CommitteeReferral saved = service.refer(MODULE, ENTITY, "CAB", "APPROVAL", 7L, "admin",
            new CommitteeReferralService.Recusal(List.of(8L, 3L), List.of("CloudOps", "IAM Team")),
            SUBJECT);

        assertThat(saved.getRecusalUserIds())
            .as("the half that can match committee_member_terms.member_user_id — before V358 the "
              + "referral had nowhere to put it and the gate could never fire")
            .isEqualTo("8,3");
        assertThat(saved.getRecusalNames())
            .as("⚠ and the labels are still stored: 29 of dev's 42 active seats carry no user id, so "
              + "for those the name is the only comparable thing there is")
            .isEqualTo("CloudOps,IAM Team");
    }

    @Test
    @DisplayName("F2-113 — an absent or empty recusal stores NULL rather than an empty string")
    void barringNobodyStoresNothing() {
        givenActiveCommittee(7L);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, MODULE, ENTITY))
            .thenReturn(Optional.empty());
        when(referralRepo.save(any(CommitteeReferral.class))).thenAnswer(i -> i.getArgument(0));

        CommitteeReferral saved = service.refer(MODULE, ENTITY, "CAB", "APPROVAL", 7L, "admin",
            null, SUBJECT);

        assertThat(saved.getRecusalUserIds()).isNull();
        assertThat(saved.getRecusalNames()).isNull();
        // Blanks are dropped at construction, so a caller holding two NULL labels — the state of the
        // only two referred dev changes — cannot produce a "," that parses as two empty parties.
        assertThat(new CommitteeReferralService.Recusal(null, java.util.Arrays.asList(null, "  "))
                       .isPresent())
            .isFalse();
        assertThat(CommitteeReferralService.Recusal.byName((String) null).names()).isEmpty();
    }

    /**
     * F2-113 — coverage over the gate the engine enforces, and it must count <b>both</b> causes of
     * blindness. A referral carrying ids is only decidable if its committee has a seat linked to a
     * platform user; counting the referral's half alone would report reach the gate does not have.
     */
    @Test
    @DisplayName("F2-113 — coverage counts a referral checkable only if its committee has a linked seat")
    void coverageNeedsBothTheIdAndALinkedSeat() {
        CommitteeReferral withIds = referralFor(101L, 7L, "8", null);
        CommitteeReferral labelOnly = referralFor(102L, 7L, null, "CloudOps");
        CommitteeReferral idsButBlindCommittee = referralFor(103L, 8L, "8", null);
        CommitteeReferral barsNobody = referralFor(104L, 7L, null, null);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndStatusInOrderByIdAsc(
                eq(ORG), eq(MODULE), any()))
            .thenReturn(List.of(withIds, labelOnly, idsButBlindCommittee, barsNobody));
        when(memberTermRepo.findActiveByCommittee(ORG, 7L)).thenReturn(List.of(seat(8L), seat(null)));
        when(memberTermRepo.findActiveByCommittee(ORG, 8L)).thenReturn(List.of(seat(null)));

        var cov = service.recusalCoverage(MODULE);

        assertThat(cov.gate()).isEqualTo(ChangeSoD.GATE_COMMITTEE_VOTE_NOT_REQUESTER);
        assertThat(cov.population()).isEqualTo(4);
        assertThat(cov.checkable())
            .as("only the referral that carries an id AND sits with a committee that has a linked seat")
            .isEqualTo(1);
        assertThat(cov.labelOnly())
            .as("the label-only referral, plus the one whose ids no seat on its committee can meet — "
              + "both are recorded and both leave the gate UNVERIFIABLE, which is this bucket's "
              + "documented meaning")
            .isEqualTo(2);
        assertThat(cov.absent())
            .as("nothing recorded to compare — dev's state on 4 of 4 live referrals on 2026-08-05")
            .isEqualTo(1);
        assertThat(cov.coveragePct()).isEqualTo(25);
        assertThat(cov.enabledButBlind()).isFalse();
    }

    @Test
    @DisplayName("F2-113 — coverage is measured over the open statuses only, and reuses that definition")
    void coverageMeasuresOnlyWhatTheCommitteeStillHasToDecide() {
        when(referralRepo.findByOrganisationIdAndSourceModuleAndStatusInOrderByIdAsc(
                eq(ORG), eq(MODULE), any())).thenReturn(List.of());

        var cov = service.recusalCoverage(MODULE);

        org.mockito.ArgumentCaptor<java.util.Collection<String>> statuses =
            org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(referralRepo).findByOrganisationIdAndSourceModuleAndStatusInOrderByIdAsc(
            eq(ORG), eq(MODULE), statuses.capture());
        assertThat(statuses.getValue())
            .as("a referral the committee has finished with cannot be re-gated, so counting it would "
              + "report coverage the gate will never be asked for — OPEN_STATUSES reused, not restated")
            .containsExactlyInAnyOrderElementsOf(CommitteeReferralService.OPEN_STATUSES);
        assertThat(cov.population()).isZero();
        assertThat(cov.enabledButBlind())
            .as("an empty population is not a blind gate")
            .isFalse();
        verify(memberTermRepo, never()).findActiveByCommittee(any(), any());
    }

    @Test
    @DisplayName("F2-113 — a committee's roster is read once however many referrals it holds")
    void theRosterIsNotReReadPerReferral() {
        when(referralRepo.findByOrganisationIdAndSourceModuleAndStatusInOrderByIdAsc(
                eq(ORG), eq(MODULE), any()))
            .thenReturn(List.of(referralFor(101L, 7L, "8", null), referralFor(102L, 7L, "3", null),
                                referralFor(103L, 7L, "2", null)));
        when(memberTermRepo.findActiveByCommittee(ORG, 7L)).thenReturn(List.of(seat(8L)));

        assertThat(service.recusalCoverage(MODULE).checkable()).isEqualTo(3);
        verify(memberTermRepo, org.mockito.Mockito.times(1)).findActiveByCommittee(ORG, 7L);
    }

    private CommitteeReferral referralFor(Long entityId, Long committeeId, String ids, String names) {
        CommitteeReferral r = new CommitteeReferral();
        r.setId(entityId);
        r.setOrganisationId(ORG);
        r.setSourceModule(MODULE);
        r.setSourceEntityId(entityId);
        r.setCommitteeId(committeeId);
        r.setGate("CAB");
        r.setStatus("PENDING");
        r.setRecusalUserIds(ids);
        r.setRecusalNames(names);
        return r;
    }

    /** An active seat, linked to {@code userId} or — for 29 of dev's 42 — to nobody. */
    private com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm seat(Long userId) {
        var t = new com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm();
        t.setOrganisationId(ORG);
        t.setMemberName("Seat " + userId);
        t.setMemberUserId(userId);
        t.setRenewalStatus("ACTIVE");
        return t;
    }
}
