package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.dto.CommitteeDtos.ResolutionVoteRequest;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.service.CommitteeResolutionService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * <b>D-7 / ADR-014 — committee authorisation separation.</b>
 *
 * <p>Two capabilities used to be one grant. {@code ADMIN:WRITE} gated both
 * "appoint a member term" ({@link CommitteeMemberRegistryController}) and "cast a vote"
 * ({@link CommitteeResolutionController}), so <b>any principal who could vote could appoint
 * themselves to any committee's roster</b> and manufacture a quorum — defeating the recusal
 * and quorum controls sitting next to it.
 *
 * <p>Voting now needs {@code ADMIN:APPROVE}; administering committees still needs
 * {@code ADMIN:WRITE}. This test pins that split so a future tidy-up cannot silently merge
 * them back: it asserts the annotation values by reflection, and asserts the on-behalf
 * capability behaviourally.
 *
 * <p>The on-behalf half also fixes a defect in DF-3's own fix: {@code mayRecordOnBehalf}
 * tested for the authority {@code ADMIN_APPROVE} while {@code ADMIN:APPROVE} <em>was not in
 * the permission catalogue at all</em>, so no tenant could grant it and the path was
 * {@code PLATFORM_ADMIN}-only. <b>A capability the code tests for must be one a tenant can
 * actually hold</b> — the same lesson as F2-23, one layer down. V317 catalogues
 * {@code ADMIN:APPROVE} and the on-behalf check now keys on {@code ADMIN_WRITE}, the
 * secretariat right.
 */
@ExtendWith(MockitoExtension.class)
class CommitteeAuthorisationSeparationTest {

    @Mock CommitteeResolutionService service;
    @InjectMocks CommitteeResolutionController controller;

    // ── 1. the two capabilities are distinct, and stay distinct ──────────────

    @Test
    void casting_a_vote_requires_ADMIN_APPROVE_not_ADMIN_WRITE() {
        assertThat(guardOf(CommitteeResolutionController.class, "recordVote"))
            .isEqualTo("hasPermission('ADMIN','APPROVE')");
    }

    /**
     * The escalation path. Appointing a member term must NOT be reachable with the vote
     * capability, or a voter can add themselves to any roster.
     */
    @Test
    void appointing_a_committee_member_still_requires_ADMIN_WRITE() {
        List<String> memberWriteGuards = writeGuardsOf(CommitteeMemberRegistryController.class);

        assertThat(memberWriteGuards)
            .isNotEmpty()
            .allMatch(g -> g.equals("hasPermission('ADMIN','WRITE')"),
                "every write on the member registry is ADMIN:WRITE, never ADMIN:APPROVE");
        assertThat(memberWriteGuards).doesNotContain("hasPermission('ADMIN','APPROVE')");
    }

    /**
     * Constituting a committee, scheduling meetings, recording attendance — administration.
     *
     * <p>⚠ <b>This test previously asserted that NO write on this controller used
     * {@code ADMIN:APPROVE}, which encoded a defect as the expectation (F2-39).</b> ADR-014's
     * capability table assigns "manage … minutes" (drafting) to {@code ADMIN:WRITE} and
     * <b>"approve minutes"</b> to {@code ADMIN:APPROVE} — but the code guarded
     * {@code approve-minutes} with {@code WRITE}, so the 3 roles that draft the minutes were the
     * only ones who could approve them and the 15 the ADR entitled could not. A blanket
     * "no APPROVE here" assertion is how that survived: it was mechanically true and
     * substantively wrong. The rule is now stated per capability.
     */
    @Test
    void administering_a_committee_requires_ADMIN_WRITE_and_approving_minutes_does_not() {
        List<String> guards = writeGuardsOf(CommitteeController.class);
        assertThat(guards).contains("hasPermission('ADMIN','WRITE')");
        assertThat(guardOf(CommitteeController.class, "createCommittee"))
            .isEqualTo("hasPermission('ADMIN','WRITE')");
        assertThat(guardOf(CommitteeController.class, "recordAttendance"))
            .isEqualTo("hasPermission('ADMIN','WRITE')");
    }

    /**
     * <b>F2-39.</b> Approving minutes is the act that makes a meeting's record binding and
     * e-signs it, and ADR-014 assigns it to the vote capability — the committee-eligible
     * population (15 roles), not the 3-role secretariat that drafted the document. All three
     * secretariat roles also hold {@code ADMIN:APPROVE} (V317), so correcting this widens the
     * population to the one the ADR specified without stranding anybody.
     */
    @Test
    void approving_minutes_requires_ADMIN_APPROVE_per_ADR_014() {
        assertThat(guardOf(CommitteeController.class, "approveMinutes"))
            .isEqualTo("hasPermission('ADMIN','APPROVE')");
    }

    /**
     * The escalation that matters is the reverse direction: the vote capability must not reach
     * committee *administration*. Recording attendance is the input to quorum, so if
     * {@code ADMIN:APPROVE} could write it, a voter could author the denominator of the quorum
     * that legitimises their own vote — F2-28 from the other end.
     */
    @Test
    void the_vote_capability_cannot_record_attendance_or_constitute_a_committee() {
        assertThat(guardOf(CommitteeController.class, "recordAttendance"))
            .isNotEqualTo("hasPermission('ADMIN','APPROVE')");
        assertThat(guardOf(CommitteeController.class, "createCommittee"))
            .isNotEqualTo("hasPermission('ADMIN','APPROVE')");
        assertThat(guardOf(CommitteeController.class, "completeMeeting"))
            .isNotEqualTo("hasPermission('ADMIN','APPROVE')");
    }

    /**
     * <b>F2-26.</b> Linking a seat to a platform user is the act that <em>confers</em> the
     * ability to self-vote, so it belongs to the secretariat right and not to the vote
     * capability. If it were {@code ADMIN:APPROVE}, a voter could link their own account to
     * any seat on any committee and manufacture a quorum — re-opening, through a new
     * endpoint, exactly the escalation V317 decomposed {@code ADMIN:WRITE} to close.
     *
     * <p>{@code writeGuardsOf} above already covers this endpoint as one of the registry's
     * writes; this states the specific reason so the next person to touch it sees it.
     */
    @Test
    void linking_a_seat_to_a_platform_user_requires_ADMIN_WRITE() {
        assertThat(guardOf(CommitteeMemberRegistryController.class, "linkUser"))
            .isEqualTo("hasPermission('ADMIN','WRITE')");
    }

    /**
     * <b>F2-26.</b> The picker is a read, but it is guarded by {@code ADMIN:WRITE} on
     * purpose: measured on the live grant table, {@code BOARD_SECRETARY} holds
     * {@code ADMIN:WRITE} and <em>not</em> {@code IAM:READ}, so sourcing the picker from the
     * IAM user list would 403 for the one persona that maintains committee rosters while
     * still letting them perform the link. Pinned because it reads like a mistake and is
     * not one — and because widening {@code IAM:READ} to the secretariat instead would have
     * disclosed email, roles and lock state to obtain a name.
     */
    @Test
    void the_linkable_user_picker_is_gated_by_the_right_that_performs_the_link() {
        assertThat(guardOf(CommitteeMemberRegistryController.class, "linkableUsers"))
            .isEqualTo("hasPermission('ADMIN','WRITE')");
    }

    /** The linkage report is a report: visible to everyone who can see the committee. */
    @Test
    void the_linkage_report_is_readable_with_ADMIN_READ() {
        assertThat(guardOf(CommitteeMemberRegistryController.class, "linkage"))
            .isEqualTo("hasPermission('ADMIN','READ')");
    }

    // ── 2. on-behalf recording keys on a GRANTABLE capability ────────────────

    /**
     * <b>The capability is isolated deliberately.</b> A real board secretary holds both
     * {@code ADMIN_WRITE} and {@code ADMIN_APPROVE} (V317 grants both), and an earlier version of
     * this test gave them both — which made it pass under the mutation that reverted the check to
     * the ungrantable {@code ADMIN_APPROVE}. A test that supplies every authority cannot say which
     * one is load-bearing. This one holds {@code ADMIN_WRITE} alone;
     * {@link #the_real_secretary_holds_both_and_may_record_on_behalf()} covers the realistic
     * combination.
     */
    @Test
    void a_secretary_holding_ADMIN_WRITE_may_record_on_behalf() {
        assertThat(capturedActor(auth(7L, "secretary", "ADMIN_WRITE"))
            .mayRecordOnBehalf()).isTrue();
    }

    @Test
    void the_real_secretary_holds_both_and_may_record_on_behalf() {
        assertThat(capturedActor(auth(7L, "secretary", "ADMIN_WRITE", "ADMIN_APPROVE", "ADMIN_READ"))
            .mayRecordOnBehalf()).isTrue();
    }

    /**
     * An ordinary committee member holds only the vote capability, so they can vote as
     * themselves and never for anybody else.
     */
    @Test
    void a_member_holding_only_ADMIN_APPROVE_may_not_record_on_behalf() {
        assertThat(capturedActor(auth(8L, "ciso", "ADMIN_APPROVE"))
            .mayRecordOnBehalf()).isFalse();
    }

    @Test
    void platform_admin_may_still_record_on_behalf() {
        assertThat(capturedActor(auth(1L, "admin", "ROLE_PLATFORM_ADMIN"))
            .mayRecordOnBehalf()).isTrue();
    }

    /**
     * Guards the regression directly: {@code ADMIN_APPROVE} alone must not confer on-behalf,
     * because that was the ungrantable string the original check keyed on. If someone
     * "restores" it, this fails.
     */
    @Test
    void the_ungrantable_original_check_is_not_reinstated() {
        assertThat(capturedActor(auth(9L, "member", "ADMIN_APPROVE", "ADMIN_READ"))
            .mayRecordOnBehalf()).isFalse();
    }

    @Test
    void the_actor_carries_the_principals_id_and_username() {
        CommitteeResolutionService.VoteActor actor = capturedActor(auth(42L, "dana", "ADMIN_APPROVE"));
        assertThat(actor.userId()).isEqualTo(42L);
        assertThat(actor.username()).isEqualTo("dana");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Drives the controller and returns the {@link CommitteeResolutionService.VoteActor} it built. */
    private CommitteeResolutionService.VoteActor capturedActor(Authentication auth) {
        when(service.recordVote(anyLong(), any(), anyString(), any(), any(), any()))
            .thenReturn(new CommitteeResolution());

        controller.recordVote(1L, new ResolutionVoteRequest(null, "Member", "FOR", null), auth);

        ArgumentCaptor<CommitteeResolutionService.VoteActor> captor =
            ArgumentCaptor.forClass(CommitteeResolutionService.VoteActor.class);
        org.mockito.Mockito.verify(service)
            .recordVote(anyLong(), any(), anyString(), any(), any(), captor.capture());
        return captor.getValue();
    }

    private static Authentication auth(Long userId, String username, String... authorities) {
        GrcUserDetails principal = new GrcUserDetails(userId, 1L, username, "x", true, true,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
        return new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities());
    }

    /** The single {@code @PreAuthorize} value on a uniquely-named method. */
    private static String guardOf(Class<?> controller, String methodName) {
        List<String> found = Arrays.stream(controller.getDeclaredMethods())
            .filter(m -> m.getName().equals(methodName))
            .map(m -> m.getAnnotation(PreAuthorize.class))
            .filter(java.util.Objects::nonNull)
            .map(PreAuthorize::value)
            .distinct()
            .collect(Collectors.toList());
        assertThat(found).as("@PreAuthorize on %s#%s", controller.getSimpleName(), methodName)
            .hasSize(1);
        return found.get(0);
    }

    /** Guards on every state-changing method of a controller. */
    private static List<String> writeGuardsOf(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
            .filter(CommitteeAuthorisationSeparationTest::isWrite)
            .map(m -> m.getAnnotation(PreAuthorize.class))
            .filter(java.util.Objects::nonNull)
            .map(PreAuthorize::value)
            .distinct()
            .collect(Collectors.toList());
    }

    private static boolean isWrite(Method m) {
        return m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
            || m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
            || m.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)
            || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class);
    }
}
