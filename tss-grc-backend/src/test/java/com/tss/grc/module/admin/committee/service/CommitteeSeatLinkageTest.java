package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.LinkUserRequest;
import com.tss.grc.module.admin.committee.dto.LinkableUser;
import com.tss.grc.module.admin.committee.dto.MemberTermRequest;
import com.tss.grc.module.admin.committee.dto.SeatLinkageReport;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberSkillRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <b>F2-26 — a roster seat's platform-user link.</b>
 *
 * <p>The finding was not a broken mechanism. {@code recordVote}'s roster match worked, the
 * grants were right after V317, and {@code member_user_id} had existed since V154 — but it
 * was <b>NULL on all 31 seeded rows</b>, so the match always fell through and a legitimate
 * committee member got <em>"you are not an active member of this committee"</em>. Every vote
 * therefore had to be secretariat-entered and stamped {@code on_behalf = 1}: the
 * secretary-authors-the-facts model F2-21 identified as the root defect, surviving F2-21's
 * fix because the fix's data dependency was never checked. This is the F2-23 lesson a third
 * time — <em>a mechanism was built and nobody verified the data it depends on exists</em> —
 * so these tests pin the three things that stop it recurring:
 *
 * <ol>
 *   <li>the seat has an explicit <b>third state</b>, so "nobody linked this yet" and "this
 *       external auditor will never have a login" stop being the same NULL;</li>
 *   <li>the link is <b>validated</b> — in-tenant, active, and at most one seat per person
 *       per committee, because two seats would count one person twice in the quorum
 *       denominator F2-28 derives from this table;</li>
 *   <li>coverage is <b>reported</b>, so the gap is a number on a page rather than a
 *       discovery made in a meeting.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeSeatLinkageTest {

    private static final Long ORG       = 1L;
    private static final Long OTHER_ORG = 2L;
    private static final Long COMMITTEE = 6L;
    private static final Long TERM      = 57L;
    private static final Long SECRETARY = 3L;
    private static final Long CISO      = 8L;

    @Mock CommitteeMemberTermRepository  terms;
    @Mock CommitteeRepository            committees;
    @Mock CommitteeMemberSkillRepository skills;
    @Mock UserRepository                 users;
    @Mock AuditLogService                audit;
    @Mock ApplicationEventPublisher      publisher;

    @InjectMocks CommitteeMemberRegistryService service;

    private CommitteeMemberTerm seat;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);

        seat = term(TERM, "CCO", "Chair", (short) 1);
        when(terms.findByIdAndOrganisationId(TERM, ORG)).thenReturn(Optional.of(seat));
        when(terms.save(any(CommitteeMemberTerm.class))).thenAnswer(i -> {
            CommitteeMemberTerm t = i.getArgument(0);
            if (t.getId() == null) t.setId(99L);
            return t;
        });
        when(terms.countActiveSeatsForUser(anyLong(), anyLong(), anyLong(), any())).thenReturn(0L);

        Committee c = new Committee();
        c.setId(COMMITTEE);
        c.setOrganisationId(ORG);
        when(committees.findByIdAndOrganisationId(COMMITTEE, ORG)).thenReturn(Optional.of(c));

        when(users.findById(CISO)).thenReturn(Optional.of(user(CISO, "so_officer", "SO Officer", ORG, true)));
        when(users.findById(SECRETARY)).thenReturn(Optional.of(user(SECRETARY, "compliance", "Noura Al-Zahrani", ORG, true)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── 1. the three states ─────────────────────────────────────────────────

    @Test
    void linking_a_seat_records_the_platform_user() {
        CommitteeMemberTerm saved = service.linkUser(TERM, link(CISO), SECRETARY);
        assertThat(saved.getMemberUserId()).isEqualTo(CISO);
        assertThat(saved.getNoPlatformUser()).isFalse();
        assertThat(saved.getNoPlatformUserBasisEn()).isNull();
    }

    /**
     * The whole reason for the flag. An external auditor's seat is not a gap, and a report
     * that counts it as one is a report nobody reads — so the state is declared, not
     * inferred from NULL.
     */
    @Test
    void a_seat_can_be_declared_to_have_no_platform_user() {
        CommitteeMemberTerm saved = service.linkUser(TERM,
            new LinkUserRequest(null, true, "External auditor — attends under CMA Article 54", null),
            SECRETARY);
        assertThat(saved.getMemberUserId()).isNull();
        assertThat(saved.getNoPlatformUser()).isTrue();
        assertThat(saved.getNoPlatformUserBasisEn())
            .isEqualTo("External auditor — attends under CMA Article 54");
    }

    /**
     * An "external" claim with no stated reason is the same unevidenced assertion the flag
     * exists to remove — so it is refused, and the message says which field and why.
     */
    @Test
    void declaring_a_seat_external_without_a_basis_is_refused() {
        assertThatThrownBy(() -> service.linkUser(TERM,
                new LinkUserRequest(null, true, "   ", null), SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("basisEn is required");
        verify(terms, never()).save(any());
    }

    /** Linked AND declared external is contradictory; the DB CHECK refuses it too. */
    @Test
    void a_seat_cannot_be_both_linked_and_declared_external() {
        assertThatThrownBy(() -> service.linkUser(TERM,
                new LinkUserRequest(CISO, true, "External auditor", null), SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot both be linked");
        verify(terms, never()).save(any());
    }

    /** Clearing is a legitimate act — a member leaves, an account is replaced. */
    @Test
    void an_empty_body_clears_the_link_back_to_unlinked() {
        seat.setMemberUserId(CISO);
        CommitteeMemberTerm saved = service.linkUser(TERM,
            new LinkUserRequest(null, null, null, "left the company"), SECRETARY);
        assertThat(saved.getMemberUserId()).isNull();
        assertThat(saved.getNoPlatformUser()).isFalse();
    }

    /** Declaring external must also drop any existing link, not leave both set. */
    @Test
    void declaring_external_clears_a_previous_link() {
        seat.setMemberUserId(CISO);
        CommitteeMemberTerm saved = service.linkUser(TERM,
            new LinkUserRequest(null, true, "Now attends as an external advisor", null), SECRETARY);
        assertThat(saved.getMemberUserId()).isNull();
        assertThat(saved.getNoPlatformUser()).isTrue();
    }

    /** Re-linking must clear a stale external declaration, or the row contradicts itself. */
    @Test
    void linking_clears_a_previous_external_declaration() {
        seat.setNoPlatformUser(true);
        seat.setNoPlatformUserBasisEn("was external");
        CommitteeMemberTerm saved = service.linkUser(TERM, link(CISO), SECRETARY);
        assertThat(saved.getNoPlatformUser()).isFalse();
        assertThat(saved.getNoPlatformUserBasisEn()).isNull();
    }

    // ── 2. validation ───────────────────────────────────────────────────────

    /**
     * The cross-tenant case. One message for "no such user" and "another tenant's user", so
     * a caller cannot enumerate other organisations' user ids by comparing the two.
     */
    @Test
    void a_user_from_another_tenant_cannot_be_linked() {
        when(users.findById(77L)).thenReturn(Optional.of(user(77L, "foreign", "Foreign User", OTHER_ORG, true)));
        assertThatThrownBy(() -> service.linkUser(TERM, link(77L), SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an active user in this organisation");
        verify(terms, never()).save(any());
    }

    @Test
    void a_user_id_that_does_not_exist_is_refused_with_the_same_message() {
        when(users.findById(4242L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.linkUser(TERM, link(4242L), SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an active user in this organisation");
    }

    /**
     * A deactivated account is a departed person; seating one is the fabricated-identity
     * problem F2-26 is about. (A <em>locked</em> account is deliberately still linkable —
     * a lockout is transient auth state, and letting it decide who holds a governance seat
     * would make the coverage report flap.)
     */
    @Test
    void a_deactivated_user_cannot_be_linked() {
        when(users.findById(9L)).thenReturn(Optional.of(user(9L, "gone", "Departed", ORG, false)));
        assertThatThrownBy(() -> service.linkUser(TERM, link(9L), SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an active user in this organisation");
    }

    /**
     * <b>The quorum-integrity guard.</b> Two ACTIVE seats for one person would count them
     * twice in the denominator F2-28 derives from this table and let them cast two counted
     * votes on one resolution — manufacturing a quorum from the roster side, which is the
     * same outcome DF-3 closed from the vote side. 409, because the request is well-formed
     * and the state is the problem.
     */
    @Test
    void one_person_cannot_hold_two_active_seats_on_the_same_committee() {
        when(terms.countActiveSeatsForUser(ORG, COMMITTEE, CISO, TERM)).thenReturn(1L);
        assertThatThrownBy(() -> service.linkUser(TERM, link(CISO), SECRETARY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already holds an active seat");
        verify(terms, never()).save(any());
    }

    /**
     * …but the seat being edited must not match itself. Without {@code excludeTermId},
     * re-sending the same link would 409 and the endpoint would not be idempotent.
     */
    @Test
    void re_linking_the_same_seat_to_the_same_user_is_allowed() {
        seat.setMemberUserId(CISO);
        when(terms.countActiveSeatsForUser(ORG, COMMITTEE, CISO, TERM)).thenReturn(0L);
        assertThat(service.linkUser(TERM, link(CISO), SECRETARY).getMemberUserId()).isEqualTo(CISO);
        verify(terms).countActiveSeatsForUser(ORG, COMMITTEE, CISO, TERM);
    }

    /**
     * A closed term is a historical record, and history is not edited — the same stance
     * V315 takes on pre-fix votes and V316 on pre-fix approvals.
     */
    @Test
    void a_closed_term_cannot_be_relinked() {
        seat.setRenewalStatus("RESIGNED");
        assertThatThrownBy(() -> service.linkUser(TERM, link(CISO), SECRETARY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("historical record");
        verify(terms, never()).save(any());
    }

    // ── 3. the appointment path had NO validation at all ────────────────────

    /**
     * {@code appoint()} copied {@code memberUserId} straight from the body with no check —
     * the one field on that endpoint with no server-side defence, precisely because nothing
     * was populating it. F2-25's probe could not catch this: it tested {@code id} and
     * {@code organisationId}, which <em>were</em> defended.
     */
    @Test
    void appointing_with_a_foreign_user_link_is_refused() {
        when(users.findById(77L)).thenReturn(Optional.of(user(77L, "foreign", "Foreign", OTHER_ORG, true)));
        assertThatThrownBy(() -> service.appoint(appointWith(77L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an active user in this organisation");
        verify(terms, never()).save(any());
    }

    @Test
    void appointing_a_user_who_already_holds_a_seat_is_refused() {
        when(terms.countActiveSeatsForUser(ORG, COMMITTEE, CISO, null)).thenReturn(1L);
        assertThatThrownBy(() -> service.appoint(appointWith(CISO)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already holds an active seat");
    }

    /** An appointment with no link is still the normal case and must stay unvalidated. */
    @Test
    void appointing_without_a_user_link_does_not_touch_the_user_repository() {
        service.appoint(appointWith(null));
        verify(users, never()).findById(any());
    }

    // ── 4. the audit ledger records the transition, and names a self-link ───

    @Test
    void the_ledger_records_the_before_and_after_state() {
        seat.setMemberUserId(4L);
        service.linkUser(TERM, link(CISO), SECRETARY);
        assertThat(auditDetail()).contains("user=4").contains("user=" + CISO).contains("→");
    }

    @Test
    void the_ledger_records_a_transition_out_of_unlinked() {
        service.linkUser(TERM, link(CISO), SECRETARY);
        assertThat(auditDetail()).contains("UNLINKED →");
    }

    /**
     * <b>Self-linking is allowed and marked.</b> It grants no new capability — an
     * {@code ADMIN:WRITE} holder can already record a vote for any member on their behalf —
     * but it changes the <em>provenance stamp</em> from an on-behalf entry to a personal
     * member vote. Blocking it would need a second approver on routine roster maintenance
     * and would strand single-administrator tenants; naming it in the ledger makes the case
     * reviewable instead. The residual (self-link → vote → unlink) is reconstructible from
     * the ledger plus V315's {@code voter_user_id}: deterrence, not prevention.
     */
    @Test
    void a_secretary_linking_their_own_account_is_marked_SELF() {
        service.linkUser(TERM, link(SECRETARY), SECRETARY);
        assertThat(auditDetail()).contains("SELF");
    }

    @Test
    void linking_somebody_else_is_not_marked_SELF() {
        service.linkUser(TERM, link(CISO), SECRETARY);
        assertThat(auditDetail()).doesNotContain("SELF");
    }

    @Test
    void the_secretariat_note_reaches_the_ledger() {
        service.linkUser(TERM, new LinkUserRequest(CISO, null, null, "per RES-2026-Q3-11"), SECRETARY);
        assertThat(auditDetail()).contains("per RES-2026-Q3-11");
    }

    // ── 5. the coverage report ──────────────────────────────────────────────

    @Test
    void the_report_classifies_each_seat_into_one_of_three_states() {
        rosterOf(
            linked(1L,  "CCO",              "Chair",            (short) 1, CISO),
            external(2L,"External Auditor", "Standing Invitee", (short) 0, "No platform account"),
            term(3L,    "DPO",              "Member",           (short) 1));

        SeatLinkageReport r = service.linkage(COMMITTEE);
        assertThat(r.activeSeats()).isEqualTo(3);
        assertThat(r.linked()).isEqualTo(1);
        assertThat(r.declaredExternal()).isEqualTo(1);
        assertThat(r.unlinked()).isEqualTo(1);
        assertThat(r.seats()).extracting(SeatLinkageReport.Seat::state)
            .containsExactly(SeatLinkageReport.State.LINKED,
                             SeatLinkageReport.State.EXTERNAL,
                             SeatLinkageReport.State.UNLINKED);
    }

    /**
     * A declared-external seat leaves the denominator. With 1 linked, 1 external and 1
     * unlinked the honest number is 50% (1 of 2 linkable), not 33% — otherwise a committee
     * that seats an external auditor can never reach 100% and stops being read.
     */
    @Test
    void declared_external_seats_are_excluded_from_the_coverage_denominator() {
        rosterOf(
            linked(1L,  "CCO",              "Chair",            (short) 1, CISO),
            external(2L,"External Auditor", "Standing Invitee", (short) 0, "No platform account"),
            term(3L,    "DPO",              "Member",           (short) 1));
        assertThat(service.linkage(COMMITTEE).coveragePct()).isEqualTo(50);
    }

    /**
     * The number that answers the operational question. Non-voting invitees never cast a
     * counted vote, so a blended percentage can read healthy while every voting chair is
     * unlinked.
     */
    @Test
    void voting_coverage_is_reported_separately_from_overall_coverage() {
        rosterOf(
            linked(1L, "DPO", "Standing Invitee", (short) 0, CISO),   // linked, non-voting
            term(2L,   "CCO", "Chair",            (short) 1),          // unlinked, voting
            term(3L,   "CRO", "Member",           (short) 1));         // unlinked, voting

        SeatLinkageReport r = service.linkage(COMMITTEE);
        assertThat(r.votingSeats()).isEqualTo(2);
        assertThat(r.votingLinked()).isZero();
        assertThat(r.votingUnlinked()).isEqualTo(2);
        assertThat(r.coveragePct()).isEqualTo(33);   // healthy-looking, and nobody can vote
    }

    /**
     * The resolved name is surfaced deliberately: a mis-link — "CRO" pointing at the
     * auditor's account — is only visible if the report says whose account it is.
     */
    @Test
    void the_report_resolves_the_linked_users_name_so_a_mislink_is_visible() {
        rosterOf(linked(1L, "CRO", "Chair", (short) 1, CISO));
        when(users.findAllById(List.of(CISO)))
            .thenReturn(List.of(user(CISO, "so_officer", "SO Officer", ORG, true)));

        SeatLinkageReport.Seat s = service.linkage(COMMITTEE).seats().get(0);
        assertThat(s.username()).isEqualTo("so_officer");
        assertThat(s.fullNameEn()).isEqualTo("SO Officer");
    }

    /** A user row from another tenant must never be resolved into this report. */
    @Test
    void a_cross_tenant_user_row_is_not_resolved_into_the_report() {
        rosterOf(linked(1L, "CRO", "Chair", (short) 1, 77L));
        when(users.findAllById(List.of(77L)))
            .thenReturn(List.of(user(77L, "foreign", "Foreign User", OTHER_ORG, true)));

        SeatLinkageReport.Seat s = service.linkage(COMMITTEE).seats().get(0);
        assertThat(s.memberUserId()).isEqualTo(77L);      // the stored fact is reported
        assertThat(s.username()).isNull();                 // but never resolved cross-tenant
        assertThat(s.fullNameEn()).isNull();
    }

    @Test
    void an_empty_roster_reports_zero_rather_than_dividing_by_zero() {
        rosterOf();
        SeatLinkageReport r = service.linkage(COMMITTEE);
        assertThat(r.activeSeats()).isZero();
        assertThat(r.coveragePct()).isZero();
    }

    /** Only EXTERNAL seats carry a basis; a linked seat must not leak a stale one. */
    @Test
    void only_an_external_seat_reports_a_basis() {
        CommitteeMemberTerm stale = linked(1L, "CCO", "Chair", (short) 1, CISO);
        stale.setNoPlatformUserBasisEn("stale text from before it was linked");
        rosterOf(stale);
        assertThat(service.linkage(COMMITTEE).seats().get(0).basisEn()).isNull();
    }

    // ── 6. the picker ───────────────────────────────────────────────────────

    @Test
    void the_picker_lists_active_tenant_users_with_a_minimal_projection() {
        pickerUsers(user(CISO, "so_officer", "SO Officer", ORG, true),
                    user(SECRETARY, "compliance", "Noura Al-Zahrani", ORG, true));

        List<LinkableUser> out = service.linkableUsers(null, null);
        assertThat(out).extracting(LinkableUser::username)
            .containsExactly("compliance", "so_officer");    // sorted by display name
    }

    @Test
    void the_picker_excludes_deactivated_users() {
        pickerUsers(user(CISO, "so_officer", "SO Officer", ORG, true),
                    user(9L, "gone", "Departed Person", ORG, false));
        assertThat(service.linkableUsers(null, null)).extracting(LinkableUser::username)
            .containsExactly("so_officer");
    }

    @Test
    void the_picker_filters_on_username_name_and_job_title() {
        User u = user(CISO, "so_officer", "SO Officer", ORG, true);
        u.setJobTitleEn("Chief Information Security Officer");
        pickerUsers(u, user(SECRETARY, "compliance", "Noura Al-Zahrani", ORG, true));

        assertThat(service.linkableUsers(null, "chief information")).hasSize(1);
        assertThat(service.linkableUsers(null, "noura")).hasSize(1);
        assertThat(service.linkableUsers(null, "so_off")).hasSize(1);
        assertThat(service.linkableUsers(null, "zzz")).isEmpty();
    }

    /**
     * The UI needs to grey out a choice that would 409 rather than let the secretary
     * discover it after submitting.
     */
    @Test
    void the_picker_reports_how_many_seats_a_user_already_holds_on_the_committee() {
        pickerUsers(user(CISO, "so_officer", "SO Officer", ORG, true));
        when(terms.countActiveSeatsForUser(ORG, COMMITTEE, CISO, null)).thenReturn(1L);
        assertThat(service.linkableUsers(COMMITTEE, null).get(0).heldSeats()).isEqualTo(1);
    }

    // ── 4. F2-178 — the report says which seats actually confer access ──────

    /**
     * A linked seat whose term window has closed still counts on the roster and no longer counts for
     * authorisation, and the report says both.
     *
     * <p><b>F2-178.</b> This report was written when {@code member_user_id} had one consumer —
     * {@code recordVote}'s roster match, which is content with {@code renewal_status = 'ACTIVE'}. F2-177
     * added a second that also requires the term window to be open. Nothing in the platform ever moves
     * an ACTIVE term to EXPIRED (the sweep sends reminders; {@code endTerm} is manual), so a roster
     * drifts into "fully linked, admits nobody" on its own. Reporting only {@code linked} would state,
     * truthfully and uselessly, that the roster is complete.
     */
    @Test
    void a_linked_seat_outside_its_term_window_counts_on_the_roster_and_not_for_authorisation() {
        CommitteeMemberTerm lapsed = linked(1L, "CCO", "Chair", (short) 1, CISO);
        lapsed.setTermEndsAt(LocalDate.now().minusDays(1));
        rosterOf(lapsed, linked(2L, "DPO", "Member", (short) 1, SECRETARY));

        SeatLinkageReport r = service.linkage(COMMITTEE);

        assertThat(r.linked()).isEqualTo(2);
        assertThat(r.coveragePct()).isEqualTo(100);      // the roster reads complete…
        assertThat(r.currentSeats()).isEqualTo(1);
        assertThat(r.currentLinked()).isEqualTo(1);      // …and admits one of the two
        assertThat(r.seats()).extracting(SeatLinkageReport.Seat::currentlySeated)
            .containsExactly(false, true);
        // The lapsed seat is still LINKED: currency and linkage are independent axes, and collapsing
        // them into one enum would make a lapsed-and-unlinked seat report only one of its problems.
        assertThat(r.seats().get(0).state()).isEqualTo(SeatLinkageReport.State.LINKED);
    }

    @Test
    void an_ended_term_stops_conferring_access_immediately_rather_than_at_its_end_date() {
        CommitteeMemberTerm resigned = linked(1L, "CCO", "Chair", (short) 1, CISO);
        resigned.setTermEndsAt(LocalDate.now().plusYears(2));
        resigned.setEndedAt(java.time.LocalDateTime.now().minusDays(3));
        rosterOf(resigned);

        SeatLinkageReport r = service.linkage(COMMITTEE);

        // Ending a term mid-window is a distinct act (resignation, removal) recorded in end_reason, and
        // it revokes the seat now — not when term_ends_at arrives two years later.
        assertThat(r.linked()).isEqualTo(1);
        assertThat(r.currentLinked()).isZero();
        assertThat(r.seats().get(0).currentlySeated()).isFalse();
    }

    @Test
    void an_unlinked_seat_inside_its_window_is_current_but_confers_nothing() {
        rosterOf(term(1L, "DPO", "Member", (short) 1));

        SeatLinkageReport r = service.linkage(COMMITTEE);

        // currentSeats counts seats the guard would consider; currentLinked counts the ones it can
        // actually match a user to. An unlinked seat is in the first and not the second, and conflating
        // them would report an empty roster's worth of access.
        assertThat(r.currentSeats()).isEqualTo(1);
        assertThat(r.currentLinked()).isZero();
        assertThat(r.seats().get(0).currentlySeated()).isTrue();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static LinkUserRequest link(Long userId) {
        return new LinkUserRequest(userId, null, null, null);
    }

    private MemberTermRequest appointWith(Long userId) {
        return new MemberTermRequest(COMMITTEE, "Layla Al-Otaibi", userId, "Member",
            (short) 1, (short) 0, null, LocalDate.of(2026, 3, 1), null,
            LocalDate.of(2026, 3, 1), null, null);
    }

    private static CommitteeMemberTerm term(Long id, String name, String role, Short voting) {
        CommitteeMemberTerm t = new CommitteeMemberTerm();
        t.setId(id);
        t.setOrganisationId(ORG);
        t.setCommitteeId(COMMITTEE);
        t.setMemberName(name);
        t.setRole(role);
        t.setIsVotingMember(voting);
        t.setRenewalStatus("ACTIVE");
        t.setAppointedAt(LocalDate.of(2026, 1, 1));
        t.setTermStartsAt(LocalDate.of(2026, 1, 1));
        return t;
    }

    private static CommitteeMemberTerm linked(Long id, String name, String role, Short voting, Long userId) {
        CommitteeMemberTerm t = term(id, name, role, voting);
        t.setMemberUserId(userId);
        return t;
    }

    private static CommitteeMemberTerm external(Long id, String name, String role, Short voting, String basis) {
        CommitteeMemberTerm t = term(id, name, role, voting);
        t.setNoPlatformUser(true);
        t.setNoPlatformUserBasisEn(basis);
        return t;
    }

    private static User user(Long id, String username, String nameEn, Long org, boolean active) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFullNameEn(nameEn);
        u.setOrganisationId(org);
        u.setIsActive(active);
        return u;
    }

    private void rosterOf(CommitteeMemberTerm... roster) {
        when(terms.findActiveByCommittee(ORG, COMMITTEE)).thenReturn(new ArrayList<>(List.of(roster)));
    }

    private void pickerUsers(User... list) {
        when(users.findByOrganisationId(eq(ORG), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(list)));
    }

    /** The detail string the ledger received for the link transition. */
    private String auditDetail() {
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("MEMBER_LINK_USER"), anyString(), anyLong(), detail.capture());
        return detail.getValue();
    }
}
