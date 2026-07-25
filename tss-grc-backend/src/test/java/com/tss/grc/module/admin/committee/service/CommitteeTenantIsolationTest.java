package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionVoteRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W1-1 / finding F-2, part 5: tenant isolation in the committee engine.
 *
 * <p>Per ADR-003 org filtering is a <em>developer responsibility on every query</em> —
 * there is no global row-level filter — so the thing worth testing is not "does a mock
 * return nothing" but <b>does the service actually thread the current tenant into the
 * repository call</b>, and does it do so <b>before</b> touching unscoped child data.
 *
 * <p>The committee services use an org-check-first pattern: the parent is fetched with
 * {@code findByIdAndOrganisationId(...)} and the children are then fetched by parent id
 * only ({@code findByCommitteeMeetingId}, {@code findByResolutionId} — no org column in
 * the call). That is sound <em>only while the parent check runs first</em>, so these
 * tests assert the ordering explicitly: a foreign-tenant parent must abort before any
 * child query is issued. If someone later reorders those lines, a cross-tenant read of
 * attendance or votes becomes possible and these tests fail.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeTenantIsolationTest {

    private static final Long OURS = 1L;
    private static final Long THEIRS = 2L;
    private static final Long MEETING_ID = 55L;
    private static final Long RES_ID = 99L;

    // ── CommitteeService ────────────────────────────────────────────────────
    @Mock CommitteeRepository                 repo;
    @Mock CommitteeMeetingRepository          meetingRepo;
    @Mock CommitteeMeetingAttendeeRepository  attendeeRepo;
    @InjectMocks CommitteeService committeeService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void the_meeting_lookup_is_scoped_to_the_tenant_in_context() {
        TenantContext.setCurrentTenant(OURS);
        CommitteeMeeting m = new CommitteeMeeting();
        m.setId(MEETING_ID);
        m.setOrganisationId(OURS);
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, OURS)).thenReturn(Optional.of(m));

        committeeService.getMeeting(MEETING_ID);

        // the tenant must come from the context, not a constant
        verify(meetingRepo).findByIdAndOrganisationId(MEETING_ID, OURS);
        verify(meetingRepo, never()).findByIdAndOrganisationId(MEETING_ID, THEIRS);
    }

    @Test
    void switching_tenant_changes_the_organisation_the_query_is_scoped_to() {
        TenantContext.setCurrentTenant(THEIRS);
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, THEIRS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> committeeService.getMeeting(MEETING_ID))
            .isInstanceOf(EntityNotFoundException.class);
        verify(meetingRepo).findByIdAndOrganisationId(MEETING_ID, THEIRS);
    }

    @Test
    void another_tenants_meeting_is_not_found_rather_than_returned() {
        TenantContext.setCurrentTenant(OURS);
        // the row exists, but belongs to THEIRS — the scoped finder yields nothing
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, OURS)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> committeeService.getMeeting(MEETING_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void quorum_aborts_on_a_foreign_meeting_BEFORE_reading_the_attendance_sheet() {
        // findByCommitteeMeetingId carries no organisation filter, so the org check on
        // the meeting must happen first or attendance leaks across tenants.
        TenantContext.setCurrentTenant(OURS);
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, OURS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> committeeService.quorum(MEETING_ID))
            .isInstanceOf(EntityNotFoundException.class);
        verify(attendeeRepo, never()).findByCommitteeMeetingId(anyLong());
    }

    @Test
    void approving_minutes_on_a_foreign_meeting_is_refused_before_any_mutation() {
        TenantContext.setCurrentTenant(OURS);
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, OURS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> committeeService.approveMinutes(MEETING_ID, "Chair", 9L))
            .isInstanceOf(EntityNotFoundException.class);
        verify(meetingRepo, never()).save(any());
    }

    // ── CommitteeResolutionService ──────────────────────────────────────────

    @Mock CommitteeResolutionRepository      resRepo;
    @Mock AuditLogService                    audit;
    @Mock ApplicationEventPublisher          publisher;
    @Mock CommitteeResolutionVoteRepository  voteRepo;
    @Mock CommitteeMeetingAttendeeRepository attendeeRepo2;
    @InjectMocks CommitteeResolutionService resolutionService;

    @Test
    void the_resolution_lookup_is_scoped_to_the_tenant_in_context() {
        TenantContext.setCurrentTenant(OURS);
        CommitteeResolution r = new CommitteeResolution();
        r.setId(RES_ID);
        r.setOrganisationId(OURS);
        when(resRepo.findByIdAndOrganisationId(RES_ID, OURS)).thenReturn(Optional.of(r));

        resolutionService.get(RES_ID);
        verify(resRepo).findByIdAndOrganisationId(RES_ID, OURS);
    }

    @Test
    void voting_on_another_tenants_resolution_is_refused_before_any_vote_is_written() {
        TenantContext.setCurrentTenant(OURS);
        when(resRepo.findByIdAndOrganisationId(RES_ID, OURS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolutionService.recordVote(RES_ID, "Ali", "FOR", "MEMBER", null))
            .isInstanceOf(EntityNotFoundException.class);
        verify(voteRepo, never()).save(any());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void listing_votes_aborts_on_a_foreign_resolution_BEFORE_reading_the_vote_rows() {
        // findByResolutionId is not org-scoped; votes() calls get() first as the guard.
        TenantContext.setCurrentTenant(OURS);
        when(resRepo.findByIdAndOrganisationId(RES_ID, OURS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolutionService.votes(RES_ID))
            .isInstanceOf(EntityNotFoundException.class);
        verify(voteRepo, never()).findByResolutionId(anyLong());
    }

    // ── CommitteeReferralService ────────────────────────────────────────────

    @Mock CommitteeReferralRepository referralRepo;
    @InjectMocks CommitteeReferralService referralService;

    @Test
    void referral_lookups_carry_the_tenant_from_context() {
        TenantContext.setCurrentTenant(OURS);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(OURS, "CHANGE", 500L))
            .thenReturn(Optional.empty());

        referralService.activeReferral("CHANGE", 500L);
        verify(referralRepo).findByOrganisationIdAndSourceModuleAndSourceEntityId(OURS, "CHANGE", 500L);
        verify(referralRepo, never())
            .findByOrganisationIdAndSourceModuleAndSourceEntityId(eq(THEIRS), anyString(), anyLong());
    }

    @Test
    void the_referral_gate_refuses_when_the_item_has_no_referral_in_THIS_tenant() {
        // A referral belonging to another org must not satisfy this org's gate.
        TenantContext.setCurrentTenant(OURS);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(OURS, "CHANGE", 500L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> referralService.assertDecisionApproves("CHANGE", 500L, 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No committee referral");
    }
}
