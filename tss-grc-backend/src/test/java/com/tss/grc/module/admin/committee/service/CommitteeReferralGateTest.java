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
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
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

        CommitteeReferral saved = service.refer(MODULE, ENTITY, "CAB", null, null, "admin", "Requester Name", SUBJECT);

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
}
