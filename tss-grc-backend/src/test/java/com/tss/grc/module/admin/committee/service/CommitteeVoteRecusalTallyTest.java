package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMeetingAttendee;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionVoteRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

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
 * W1-1 / finding F-2, part 2: per-member voting, forced recusal and tally derivation
 * in {@link CommitteeResolutionService} — previously untested, yet this is the
 * arithmetic that decides whether a change or policy is authorised (the referral gate
 * reads {@code outcome} and {@code voteFor} straight off the resolution).
 *
 * <p>Two recusal mechanics are pinned because they are the platform's SoD guarantee:
 * a member who declared a conflict on the meeting attendance, and — Track D — the
 * requester/author of the referred item, may never cast a counted vote. Both force
 * RECUSED rather than rejecting the call, and RECUSED is excluded from the tally.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeVoteRecusalTallyTest {

    private static final Long ORG = 1L;
    private static final Long RES_ID = 99L;
    private static final Long MEETING_ID = 55L;

    @Mock CommitteeResolutionRepository       repo;
    @Mock AuditLogService                     audit;
    @Mock ApplicationEventPublisher           publisher;
    @Mock CommitteeResolutionVoteRepository   voteRepo;
    @Mock CommitteeMeetingAttendeeRepository  attendeeRepo;
    @Mock CommitteeReferralRepository         referralRepo;
    @Mock CommitteeMemberTermRepository       memberTermRepo;   // DF-3
    /** F2-100 — a decision is binding on APPROVED minutes, so publishing needs the meeting.
     *  A real mock rather than a null: publishIfBinding() catches RuntimeException so a
     *  recorded vote is never rolled back by a failed announcement, which means a null
     *  dependency here would be swallowed instead of failing. */
    @Mock CommitteeMeetingRepository          meetingRepo;

    @InjectMocks CommitteeResolutionService service;

    /** Votes "persisted" by the service, so recomputeTally sees what was saved. */
    private final List<CommitteeResolutionVote> stored = new ArrayList<>();

    private static final Long COMMITTEE_ID = 7L;

    /**
     * DF-3: every member named in these tests must now be on the committee's active
     * roster, and each gets a linked platform user id so a vote can be attributed. The
     * ids are the roster index — {@link #asSelf} turns a name back into that member's
     * own actor, so each test below records the vote it always meant to record: the
     * member's own, by that member.
     */
    private static final List<String> ROSTER = List.of(
        "Ali", "Ali Hassan", "Noura", "Sara", "Sara Al-Otaibi", "Omar", "Mansour Bin Ali");

    private CommitteeMemberTerm term(String name, long userId, int voting) {
        CommitteeMemberTerm t = new CommitteeMemberTerm();
        t.setId(userId);
        t.setOrganisationId(ORG);
        t.setCommitteeId(COMMITTEE_ID);
        t.setMemberName(name);
        t.setMemberUserId(userId);
        t.setRole("Member");
        t.setIsVotingMember((short) voting);
        return t;
    }

    /** The actor for "this member is casting their own vote". */
    private CommitteeResolutionService.VoteActor asSelf(String memberName) {
        long id = ROSTER.indexOf(memberName.trim()) + 100L;
        return CommitteeResolutionService.VoteActor.self(id, memberName.trim());
    }

    /**
     * Every pre-DF-3 call site funnels through here. The signature change is the point:
     * a vote can no longer be recorded without saying who recorded it, so the tests have
     * to name an actor — and these tests name the member themselves, which is what they
     * were always implicitly asserting.
     */
    private CommitteeResolution vote(String member, String v, String role, String rationale) {
        return service.recordVote(RES_ID, member, v, role, rationale, asSelf(member));
    }

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        // referralRepo is @Autowired(required = false) FIELD injection. Mockito picks the
        // @RequiredArgsConstructor for the five final deps and then skips property
        // injection entirely, so the field would stay null and the Track D recusal branch
        // would silently no-op — which is very likely why this path had no coverage at
        // all. Wire it explicitly; nullReferralRepo_* below pins the absent-bean path.
        ReflectionTestUtils.setField(service, "referralRepo", referralRepo);
        CommitteeResolution res = new CommitteeResolution();
        res.setId(RES_ID);
        res.setOrganisationId(ORG);
        res.setCommitteeId(COMMITTEE_ID);
        res.setMeetingId(MEETING_ID);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(res));
        when(repo.save(any(CommitteeResolution.class))).thenAnswer(i -> i.getArgument(0));

        when(voteRepo.findByResolutionIdAndMemberName(eq(RES_ID), anyString()))
            .thenAnswer(i -> stored.stream()
                .filter(v -> i.getArgument(1).toString().equalsIgnoreCase(v.getMemberName()))
                .findFirst());
        when(voteRepo.save(any(CommitteeResolutionVote.class))).thenAnswer(i -> {
            CommitteeResolutionVote v = i.getArgument(0);
            stored.removeIf(x -> v.getMemberName().equalsIgnoreCase(x.getMemberName()));
            stored.add(v);
            return v;
        });
        when(voteRepo.findByResolutionId(RES_ID)).thenReturn(stored);
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID)).thenReturn(List.of());
        when(referralRepo.findByResolutionId(RES_ID)).thenReturn(Optional.empty());
        // DF-3: the active roster now gates who may vote at all.
        List<CommitteeMemberTerm> roster = new ArrayList<>();
        for (int i = 0; i < ROSTER.size(); i++) roster.add(term(ROSTER.get(i), 100L + i, 1));
        when(memberTermRepo.findActiveByCommittee(ORG, COMMITTEE_ID)).thenReturn(roster);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        stored.clear();
    }

    private CommitteeMeetingAttendee attendee(String name, boolean coi) {
        CommitteeMeetingAttendee a = new CommitteeMeetingAttendee();
        a.setOrganisationId(ORG);
        a.setCommitteeMeetingId(MEETING_ID);
        a.setMemberName(name);
        a.setCoiDeclared(coi);
        return a;
    }

    private void givenReferralRecusing(String csvNames) {
        givenReferral(null, csvNames);
    }

    /** F2-113 — a referral barring parties by platform user id, as V358 stores them. */
    private void givenReferralRecusingIds(String csvUserIds) {
        givenReferral(csvUserIds, null);
    }

    private void givenReferral(String csvUserIds, String csvNames) {
        CommitteeReferral ref = new CommitteeReferral();
        ref.setId(4242L);
        ref.setOrganisationId(ORG);
        ref.setResolutionId(RES_ID);
        ref.setRecusalUserIds(csvUserIds);
        ref.setRecusalNames(csvNames);
        when(referralRepo.findByResolutionId(RES_ID)).thenReturn(Optional.of(ref));
    }

    /** A seat on the roster that carries NO platform user id — 29 of dev's 42 active seats. */
    private void givenUnlinkedSeat(String name) {
        List<CommitteeMemberTerm> roster = new ArrayList<>();
        for (int i = 0; i < ROSTER.size(); i++) roster.add(term(ROSTER.get(i), 100L + i, 1));
        CommitteeMemberTerm unlinked = term(name, 999L, 1);
        unlinked.setMemberUserId(null);
        roster.add(unlinked);
        when(memberTermRepo.findActiveByCommittee(ORG, COMMITTEE_ID)).thenReturn(roster);
    }

    private String voteOf(String member) {
        return stored.stream().filter(v -> member.equalsIgnoreCase(v.getMemberName()))
            .map(CommitteeResolutionVote::getVote).findFirst().orElse(null);
    }

    // ── input validation ────────────────────────────────────────────────────

    @Test
    void rejects_an_unknown_vote_value() {
        assertThatThrownBy(() -> vote("Ali", "MAYBE", "MEMBER", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vote must be one of");
    }

    /**
     * DF-3 changed this deliberately. A blank {@code memberName} used to be rejected
     * ("memberName is required") because the name <em>was</em> the identity. Now the
     * identity is the authenticated actor, so blank means "my own vote", and it is
     * refused only when the actor holds no seat on this committee — a strictly stronger
     * rule, since the old code accepted any non-blank string from anyone.
     */
    @Test
    void a_blank_member_name_is_refused_when_the_actor_holds_no_seat() {
        assertThatThrownBy(() -> service.recordVote(RES_ID, "   ", "FOR", "MEMBER", null,
                CommitteeResolutionService.VoteActor.self(9999L, "outsider")))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not an active member of this committee");
        verify(voteRepo, never()).save(any());
    }

    @Test
    void a_blank_member_name_records_the_actors_own_vote() {
        CommitteeResolution out = service.recordVote(RES_ID, null, "FOR", "MEMBER", null,
            CommitteeResolutionService.VoteActor.self(100L, "Ali"));
        assertThat(voteOf("Ali")).isEqualTo("FOR");
        assertThat(stored.get(0).getOnBehalf()).isFalse();
        assertThat(stored.get(0).getRecordedByUserId()).isEqualTo(100L);
        assertThat(out.getVoteFor()).isEqualTo(1);
    }

    @Test
    void accepts_a_lowercase_padded_vote_value() {
        vote("  Ali  ", " for ", "MEMBER", null);
        assertThat(voteOf("Ali")).isEqualTo("FOR");
    }

    // ── recusal mechanic 1: conflict of interest on the attendance ───────────

    @Test
    void a_member_who_declared_a_conflict_is_forced_to_recused() {
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID))
            .thenReturn(List.of(attendee("Ali Hassan", true)));
        CommitteeResolution out = vote("Ali Hassan", "FOR", "MEMBER", null);
        assertThat(voteOf("Ali Hassan")).isEqualTo("RECUSED");
        assertThat(out.getVoteFor()).isZero();
    }

    @Test
    void the_conflict_match_is_case_insensitive() {
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID))
            .thenReturn(List.of(attendee("Ali Hassan", true)));
        vote("ALI HASSAN", "AGAINST", "MEMBER", null);
        assertThat(voteOf("ALI HASSAN")).isEqualTo("RECUSED");
    }

    @Test
    void a_member_without_a_declared_conflict_votes_normally() {
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID))
            .thenReturn(List.of(attendee("Ali Hassan", false), attendee("Noura", true)));
        CommitteeResolution out = vote("Ali Hassan", "FOR", "MEMBER", null);
        assertThat(voteOf("Ali Hassan")).isEqualTo("FOR");
        assertThat(out.getVoteFor()).isEqualTo(1);
    }

    // ── recusal mechanic 2: Track D referral requester/author ────────────────

    @Test
    void the_referred_items_requester_is_forced_to_recused() {
        givenReferralRecusing("Sara Al-Otaibi,Mansour Bin Ali");
        vote("Mansour Bin Ali", "FOR", "CHAIR", null);
        assertThat(voteOf("Mansour Bin Ali")).isEqualTo("RECUSED");
    }

    @Test
    void the_referral_recusal_list_tolerates_padding_and_case() {
        givenReferralRecusing(" sara al-otaibi , Mansour Bin Ali ");
        vote("Sara Al-Otaibi", "FOR", "MEMBER", null);
        assertThat(voteOf("Sara Al-Otaibi")).isEqualTo("RECUSED");
    }

    @Test
    void a_member_not_on_the_recusal_list_is_unaffected() {
        givenReferralRecusing("Sara Al-Otaibi");
        CommitteeResolution out = vote("Ali Hassan", "FOR", "MEMBER", null);
        assertThat(voteOf("Ali Hassan")).isEqualTo("FOR");
        assertThat(out.getVoteFor()).isEqualTo(1);
    }

    // ── F2-113: recusal mechanic 2 could never fire, because it compared a label to a person ──

    /**
     * ⚠ <b>THE MUTATION CHECK.</b> Delete the id comparison from {@code recordVote} — revert to
     * {@code sameName(member, nm)} over {@code recusal_names} alone — and this test fails, because
     * there is no name to match: the referral carries only an id, which is what
     * {@code referToCommittee} now produces for a change whose people were captured as identity.
     *
     * <p>It is also the finding itself, in miniature. 'Mansour Bin Ali' is user 106 on the roster; the
     * referral bars user 106 while naming nobody. Before V358 the only thing that crossed the port was
     * a CSV of the change's team labels ('CloudOps', 'IAM Team'), and the seat names are people — so
     * measured in dev, 15 of 15 owner labels and 13 of 13 requester labels resolved to no user and 0
     * of 4 live referrals carried a name at all. The bar had never engaged for any change in either
     * database, and every test in this class was green throughout.
     */
    @Test
    void the_requester_is_recused_by_user_id_when_no_name_is_carried_at_all() {
        givenReferralRecusingIds("106");            // 'Mansour Bin Ali' — roster index 6

        CommitteeResolution out = vote("Mansour Bin Ali", "FOR", "CHAIR", null);

        assertThat(voteOf("Mansour Bin Ali"))
            .as("the change's requester cannot cast its deciding vote — resolved on identity, which "
              + "is the only thing a team label and a person's name have in common")
            .isEqualTo("RECUSED");
        assertThat(out.getVoteFor()).as("a recusal leaves the tally, it does not join it").isZero();
    }

    /**
     * The finding's exact dev shape: the label is a team, the seat is a person, and they are the same
     * user. The name comparison answers DIFFERENT — correctly, and uselessly.
     */
    @Test
    void the_id_recuses_even_when_the_carried_label_could_never_match_the_seat_name() {
        givenReferral("106", "CloudOps,IAM Team");

        vote("Mansour Bin Ali", "FOR", "MEMBER", null);

        assertThat(voteOf("Mansour Bin Ali")).isEqualTo("RECUSED");
    }

    @Test
    void a_member_whose_id_is_not_on_the_recusal_list_votes_normally() {
        givenReferralRecusingIds("106");

        CommitteeResolution out = vote("Ali Hassan", "FOR", "MEMBER", null);   // user 101

        assertThat(voteOf("Ali Hassan")).isEqualTo("FOR");
        assertThat(out.getVoteFor()).isEqualTo(1);
    }

    /**
     * ⚠ THE REGRESSION THE FIX MUST NOT INTRODUCE — the CE-15 lesson, from the far side.
     *
     * <p>29 of dev's 42 active seats carry no {@code member_user_id}, and
     * {@code committee_member_terms.no_platform_user} exists because an external director legitimately
     * never gets one. If the id comparison had been written as "the party has an id, so only an id can
     * answer" — {@code ChangeSoD.compare}'s rule, which is correct for an authenticated principal —
     * every one of those seats would have gone from refused to UNVERIFIABLE the moment referrals
     * started carrying ids. A refactor that stops refusing something is not a refactor.
     */
    @Test
    void an_unlinked_seat_is_still_recused_by_name_even_though_the_referral_carries_ids() {
        givenUnlinkedSeat("External Director B");
        givenReferral("106", "External Director B");

        vote("External Director B", "FOR", "MEMBER", null);

        assertThat(voteOf("External Director B"))
            .as("this seat has no platform user id, so the name is the only comparable thing there "
              + "is — and adding the id half must not have taken it away")
            .isEqualTo("RECUSED");
    }

    /** Both halves are read, and either one refusing is enough. */
    @Test
    void the_name_half_and_the_id_half_are_both_live() {
        givenReferral("106", "Sara Al-Otaibi");

        vote("Sara Al-Otaibi",   "FOR", "MEMBER", null);   // matched by name
        vote("Mansour Bin Ali",  "FOR", "CHAIR",  null);   // matched by id
        CommitteeResolution out = vote("Ali Hassan", "FOR", "MEMBER", null);   // matched by neither

        assertThat(voteOf("Sara Al-Otaibi")).isEqualTo("RECUSED");
        assertThat(voteOf("Mansour Bin Ali")).isEqualTo("RECUSED");
        assertThat(voteOf("Ali Hassan")).isEqualTo("FOR");
        assertThat(out.getVoteFor()).isEqualTo(1);
    }

    /**
     * A malformed CSV must not stop a committee voting. The gate's inability to compare a party is
     * reported by {@code GET /api/v1/changes/sod-coverage}, not thrown at the member trying to vote —
     * S6b-i's rule that weakness belongs in the instrument and not in the verdict.
     */
    @Test
    void an_unparsable_recusal_id_is_skipped_rather_than_breaking_the_vote() {
        givenReferral("not-a-number,106", "Sara Al-Otaibi");

        vote("Ali Hassan",      "FOR", "MEMBER", null);
        vote("Mansour Bin Ali", "FOR", "CHAIR",  null);

        assertThat(voteOf("Ali Hassan")).as("the vote still records").isEqualTo("FOR");
        assertThat(voteOf("Mansour Bin Ali"))
            .as("and the parsable id beside it still bars its party").isEqualTo("RECUSED");
    }

    /**
     * ⚠ Dev's real state on 2026-08-05: <b>4 of 4</b> live referrals carried neither an id nor a name,
     * because {@code referToCommittee} built its CSV from two NULL labels and stored blank. A referral
     * that bars nobody must not accidentally bar somebody — an empty list is UNVERIFIABLE, and
     * UNVERIFIABLE never refuses.
     */
    @Test
    void a_referral_barring_nobody_recuses_nobody() {
        givenReferral(null, null);

        CommitteeResolution out = vote("Mansour Bin Ali", "FOR", "CHAIR", null);

        assertThat(voteOf("Mansour Bin Ali")).isEqualTo("FOR");
        assertThat(out.getVoteFor()).isEqualTo(1);
    }

    @Test
    void an_explicit_recused_vote_short_circuits_the_conflict_lookup() {
        vote("Ali Hassan", "RECUSED", "MEMBER", "stepping out");
        assertThat(voteOf("Ali Hassan")).isEqualTo("RECUSED");
        verify(attendeeRepo, never()).findByCommitteeMeetingId(anyLong());
    }

    // ── tally derivation + outcome mapping ──────────────────────────────────

    @Test
    void recused_votes_are_excluded_from_every_tally_bucket() {
        givenReferralRecusing("Sara Al-Otaibi");
        vote("Ali",   "FOR",     "MEMBER", null);
        vote("Noura", "ABSTAIN", "MEMBER", null);
        CommitteeResolution out = vote("Sara Al-Otaibi", "FOR", "MEMBER", null);

        assertThat(voteOf("Sara Al-Otaibi")).isEqualTo("RECUSED");
        assertThat(out.getVoteFor()).isEqualTo(1);       // Sara's FOR must not count
        assertThat(out.getVoteAgainst()).isZero();
        assertThat(out.getVoteAbstain()).isEqualTo(1);
        assertThat(out.getOutcome()).isEqualTo("APPROVED");
    }

    @Test
    void outcome_is_approved_when_for_outnumbers_against() {
        vote("Ali",   "FOR",     "MEMBER", null);
        vote("Noura", "FOR",     "MEMBER", null);
        CommitteeResolution out = vote("Omar", "AGAINST", "MEMBER", null);
        assertThat(out.getOutcome()).isEqualTo("APPROVED");
        assertThat(out.getVoteFor()).isEqualTo(2);
        assertThat(out.getVoteAgainst()).isEqualTo(1);
    }

    @Test
    void a_tie_fails_the_motion_rather_than_carrying_it() {
        vote("Ali", "FOR", "MEMBER", null);
        CommitteeResolution out = vote("Omar", "AGAINST", "MEMBER", null);
        assertThat(out.getOutcome()).isEqualTo("REJECTED");
    }

    @Test
    void outcome_is_rejected_when_against_outnumbers_for() {
        vote("Ali",   "AGAINST", "MEMBER", null);
        CommitteeResolution out = vote("Omar", "AGAINST", "MEMBER", null);
        assertThat(out.getOutcome()).isEqualTo("REJECTED");
        assertThat(out.getVoteFor()).isZero();
    }

    @Test
    void only_abstentions_and_recusals_leave_the_motion_deferred() {
        givenReferralRecusing("Sara");
        vote("Ali",  "ABSTAIN", "MEMBER", null);
        CommitteeResolution out = vote("Sara", "FOR", "MEMBER", null);
        assertThat(out.getOutcome()).isEqualTo("DEFERRED");
        assertThat(out.getVoteFor()).isZero();
        assertThat(out.getVoteAbstain()).isEqualTo(1);
    }

    @Test
    void a_member_changing_their_vote_updates_the_row_instead_of_double_counting() {
        vote("Ali", "FOR", "MEMBER", null);
        CommitteeResolution out = vote("Ali", "AGAINST", "MEMBER", "changed my mind");
        assertThat(stored).hasSize(1);
        assertThat(out.getVoteFor()).isZero();
        assertThat(out.getVoteAgainst()).isEqualTo(1);
    }

    // ── provenance ──────────────────────────────────────────────────────────

    @Test
    void every_vote_is_audited_with_the_resulting_tally_and_outcome() {
        vote("Ali", "FOR", "CHAIR", null);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("RESOLUTION_VOTE"), eq("CommitteeResolution"), eq(RES_ID), detail.capture());
        assertThat(detail.getValue())
            .contains("Ali voted FOR")
            .contains("1/0/0")
            .contains("APPROVED");
    }

    @Test
    void the_vote_row_carries_the_meeting_org_and_role_for_the_minutes() {
        vote("Ali", "FOR", "CHAIR", "because");
        CommitteeResolutionVote v = stored.get(0);
        assertThat(v.getResolutionId()).isEqualTo(RES_ID);
        assertThat(v.getMeetingId()).isEqualTo(MEETING_ID);
        assertThat(v.getOrganisationId()).isEqualTo(ORG);
        assertThat(v.getMemberRole()).isEqualTo("CHAIR");
        assertThat(v.getRationale()).isEqualTo("because");
    }

    /**
     * Production reality when the optional bean is absent (or in any unit test that
     * constructs the service directly): the referral recusal must no-op rather than NPE.
     */
    @Test
    void recording_a_vote_still_works_when_the_referral_repository_is_absent() {
        ReflectionTestUtils.setField(service, "referralRepo", null);
        CommitteeResolution out = vote("Ali", "FOR", "MEMBER", null);
        assertThat(voteOf("Ali")).isEqualTo("FOR");
        assertThat(out.getOutcome()).isEqualTo("APPROVED");
    }
}
