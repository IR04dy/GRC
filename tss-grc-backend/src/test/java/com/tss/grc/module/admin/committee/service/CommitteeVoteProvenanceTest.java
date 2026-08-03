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
import com.tss.grc.module.admin.committee.service.CommitteeResolutionService.VoteActor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DF-3 / finding F2-21 — <b>the forged-actor test {@code ADR-006} has required all along</b>
 * ("approvals use immutable authenticated user IDs"; Proof: "forged-actor tests").
 *
 * <p>Until this class existed, {@code recordVote} took {@code memberName} straight from the
 * request body and never compared it to the caller or to the committee roster. One actor
 * could therefore:
 * <ol>
 *   <li>cast a vote in <b>another member's name</b>;</li>
 *   <li>evade COI and Track-D SoD <b>recusal</b>, because both compared the <em>claimed</em>
 *       name — so a spelling variant walked straight past them;</li>
 *   <li>manufacture <b>quorum</b> by submitting several distinct names;</li>
 *   <li><b>overwrite</b> a vote another member had already cast (the row is upserted by
 *       name);</li>
 *   <li>and have the <b>audit ledger</b> record the forged name, so the trail corroborated
 *       the forgery instead of exposing it.</li>
 * </ol>
 * Because committee minutes are SHA-256 e-signed, the signature attested to content whose
 * votes were forgeable.
 *
 * <p><b>Why the existing 38 committee tests never caught it.</b> They pass the member name
 * in as a parameter and assert the logic <em>given</em> that name. They test the decision;
 * they never test the provenance of the identity making it. That is the structural blind
 * spot of unit tests over mocked inputs, and it is the reason this class asserts on
 * <em>who</em> rather than on <em>what</em>.
 *
 * <p>Each test below fails if the corresponding guard in {@code recordVote} is removed —
 * verified by mutation, one guard at a time.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeVoteProvenanceTest {

    private static final Long ORG = 1L;
    private static final Long RES_ID = 99L;
    private static final Long MEETING_ID = 55L;
    private static final Long COMMITTEE_ID = 7L;

    // The roster. Ali and Noura vote; Khalid attends but may not vote.
    private static final long ALI_UID = 101L, NOURA_UID = 102L, KHALID_UID = 103L;
    private static final long SECRETARY_UID = 900L, OUTSIDER_UID = 999L;

    @Mock CommitteeResolutionRepository       repo;
    @Mock AuditLogService                     audit;
    @Mock ApplicationEventPublisher           publisher;
    @Mock CommitteeResolutionVoteRepository   voteRepo;
    @Mock CommitteeMeetingAttendeeRepository  attendeeRepo;
    @Mock CommitteeReferralRepository         referralRepo;
    @Mock CommitteeMemberTermRepository       memberTermRepo;
    /** F2-100 — a decision is binding on APPROVED minutes, so publishing needs the meeting.
     *  A real mock rather than a null: publishIfBinding() catches RuntimeException so a
     *  recorded vote is never rolled back by a failed announcement, which means a null
     *  dependency here would be swallowed instead of failing. */
    @Mock CommitteeMeetingRepository          meetingRepo;

    @InjectMocks CommitteeResolutionService service;

    private final List<CommitteeResolutionVote> stored = new ArrayList<>();

    /** An ordinary member — may cast their own vote and nobody else's. */
    private static VoteActor member(long uid, String name) {
        return VoteActor.self(uid, name);
    }

    /** A committee secretary/chair — may record a vote on a member's behalf. */
    private static VoteActor secretary() {
        return new VoteActor(SECRETARY_UID, "secretary", true);
    }

    private CommitteeMemberTerm term(String name, Long userId, int voting) {
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

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
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
        when(memberTermRepo.findActiveByCommittee(ORG, COMMITTEE_ID)).thenReturn(List.of(
            term("Ali Hassan", ALI_UID, 1),
            term("Noura Al-Qahtani", NOURA_UID, 1),
            term("Khalid Observer", KHALID_UID, 0)));   // standing invitee, non-voting
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        stored.clear();
    }

    private CommitteeResolutionVote row(String member) {
        return stored.stream().filter(v -> member.equalsIgnoreCase(v.getMemberName()))
            .findFirst().orElse(null);
    }

    // ── 1. the core forgery ─────────────────────────────────────────────────

    @Test
    void a_member_cannot_cast_a_vote_in_another_members_name() {
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Noura Al-Qahtani", "FOR", "MEMBER", null, member(ALI_UID, "Ali Hassan")))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("only cast your own vote");
        verify(voteRepo, never()).save(any());
        verify(audit, never()).log(anyString(), anyString(), any(), anyString());
    }

    @Test
    void an_unidentified_caller_cannot_record_a_vote_at_all() {
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Ali Hassan", "FOR", "MEMBER", null, new VoteActor(null, "  ", false)))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("identified user");
        verify(voteRepo, never()).save(any());
    }

    @Test
    void a_name_that_is_not_on_the_active_roster_is_refused() {
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Ghost Member", "FOR", "MEMBER", null, secretary()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an active member of this committee");
        verify(voteRepo, never()).save(any());
    }

    /** Quorum forgery: N invented names must not become N votes. */
    @Test
    void quorum_cannot_be_manufactured_from_invented_names() {
        for (String invented : List.of("Ghost One", "Ghost Two", "Ghost Three")) {
            assertThatThrownBy(() -> service.recordVote(
                    RES_ID, invented, "FOR", "MEMBER", null, secretary()))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(stored).isEmpty();
    }

    @Test
    void an_outsider_with_no_seat_cannot_vote_even_for_a_real_member() {
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Ali Hassan", "FOR", "MEMBER", null, member(OUTSIDER_UID, "outsider")))
            .isInstanceOf(AccessDeniedException.class);
        verify(voteRepo, never()).save(any());
    }

    // ── 2. the legitimate paths ─────────────────────────────────────────────

    @Test
    void a_member_voting_as_themselves_is_recorded_with_their_own_id_and_not_on_behalf() {
        service.recordVote(RES_ID, "Ali Hassan", "FOR", "MEMBER", null, member(ALI_UID, "Ali Hassan"));
        CommitteeResolutionVote v = row("Ali Hassan");
        assertThat(v.getVote()).isEqualTo("FOR");
        assertThat(v.getRecordedByUserId()).isEqualTo(ALI_UID);
        assertThat(v.getRecordedByName()).isEqualTo("Ali Hassan");
        assertThat(v.getOnBehalf()).isFalse();
    }

    /** Identity resolves by linked user id even when the username differs from the
     *  roster name — {@code memberUserId} is the field that cannot be spoofed. */
    @Test
    void identity_resolves_by_linked_user_id_not_by_username_string() {
        service.recordVote(RES_ID, null, "FOR", "MEMBER", null, member(ALI_UID, "ahassan"));
        CommitteeResolutionVote v = row("Ali Hassan");
        assertThat(v).isNotNull();
        assertThat(v.getRecordedByUserId()).isEqualTo(ALI_UID);
        assertThat(v.getOnBehalf()).isFalse();   // it is still his own vote
    }

    @Test
    void a_secretary_may_record_a_vote_on_behalf_and_it_is_stamped_as_such() {
        service.recordVote(RES_ID, "Noura Al-Qahtani", "AGAINST", "MEMBER",
            "raised her hand in the room", secretary());
        CommitteeResolutionVote v = row("Noura Al-Qahtani");
        assertThat(v.getVote()).isEqualTo("AGAINST");
        assertThat(v.getMemberName()).isEqualTo("Noura Al-Qahtani");   // whose vote it is
        assertThat(v.getRecordedByUserId()).isEqualTo(SECRETARY_UID);  // who recorded it
        assertThat(v.getOnBehalf()).isTrue();                          // and that it was on behalf
    }

    @Test
    void the_audit_line_names_the_actor_and_the_on_behalf_relationship() {
        service.recordVote(RES_ID, "Noura Al-Qahtani", "FOR", "MEMBER", null, secretary());
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("RESOLUTION_VOTE"), eq("CommitteeResolution"), eq(RES_ID), detail.capture());
        assertThat(detail.getValue())
            .contains("Noura Al-Qahtani voted FOR")
            .contains("recorded by secretary")
            .contains("ON BEHALF OF Noura Al-Qahtani");
    }

    @Test
    void a_self_recorded_vote_is_audited_without_an_on_behalf_claim() {
        service.recordVote(RES_ID, null, "FOR", "MEMBER", null, member(ALI_UID, "Ali Hassan"));
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("RESOLUTION_VOTE"), eq("CommitteeResolution"), eq(RES_ID), detail.capture());
        assertThat(detail.getValue()).contains("recorded by Ali Hassan").doesNotContain("ON BEHALF OF");
    }

    // ── 3. recusal can no longer be evaded by a name variant ────────────────

    private void givenAliDeclaredAConflict() {
        CommitteeMeetingAttendee a = new CommitteeMeetingAttendee();
        a.setOrganisationId(ORG);
        a.setCommitteeMeetingId(MEETING_ID);
        a.setMemberName("Ali Hassan");
        a.setCoiDeclared(true);
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID)).thenReturn(List.of(a));
    }

    /**
     * The heart of the defect. Recusal used to compare the raw request string, so a
     * differently-cased or padded name was a different person and walked past the COI
     * check. The name is now canonicalised against the roster <em>before</em> recusal is
     * evaluated, so the variant lands on the same member and is forced to RECUSED.
     */
    @Test
    void coi_recusal_cannot_be_evaded_by_a_case_or_padding_variant_of_the_name() {
        givenAliDeclaredAConflict();

        service.recordVote(RES_ID, "  ali hassan  ", "FOR", "MEMBER", null, secretary());

        assertThat(row("Ali Hassan").getVote()).isEqualTo("RECUSED");
        assertThat(stored).hasSize(1);                                   // not a second person
        // The row must carry the ROSTER spelling, not the caller's. Asserting this
        // exactly is what makes the canonicalisation load-bearing: an earlier version of
        // this test only used a case-insensitive lookup, so a mutation that stored the
        // raw request string passed it. Mutation testing caught that; this line fixes it.
        assertThat(stored.get(0).getMemberName()).isEqualTo("Ali Hassan");
    }

    /**
     * The other half of the same defence, and a deliberate design decision worth
     * recording: a name that is mangled beyond a case/padding difference
     * ("ALI&nbsp;&nbsp;&nbsp;hassan", with collapsed-out internal spacing) is
     * <b>rejected outright</b> rather than fuzzily matched.
     *
     * <p>Identity matching stays strict on purpose. Fuzzy-matching names is how you
     * eventually attribute a governance decision to the wrong person; failing closed
     * costs a secretary one corrected keystroke. Either way the recusal-evasion route is
     * shut — the variant is canonicalised (test above) or refused (this test) — and the
     * caller is told exactly why.
     */
    @Test
    void a_name_mangled_beyond_case_and_padding_is_refused_rather_than_fuzzily_matched() {
        givenAliDeclaredAConflict();

        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "ALI   hassan", "FOR", "MEMBER", null, secretary()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an active member of this committee");
        verify(voteRepo, never()).save(any());
    }

    @Test
    void track_d_sod_recusal_cannot_be_evaded_by_a_name_variant() {
        CommitteeReferral ref = new CommitteeReferral();
        ref.setOrganisationId(ORG);
        ref.setResolutionId(RES_ID);
        ref.setRecusalNames("Ali Hassan");
        when(referralRepo.findByResolutionId(RES_ID)).thenReturn(Optional.of(ref));

        service.recordVote(RES_ID, "ali hassan", "FOR", "MEMBER", null, secretary());
        assertThat(row("Ali Hassan").getVote()).isEqualTo("RECUSED");
    }

    // ── 4. vote ownership ───────────────────────────────────────────────────

    /**
     * A member's recorded vote cannot be flipped by another member. This is enforced by
     * the on-behalf gate rather than by a separate overwrite check — mutation testing
     * showed a second check here was redundant (removing it broke nothing) and harmful
     * (see {@link #a_member_may_correct_a_vote_the_secretary_recorded_for_them}).
     */
    @Test
    void a_member_cannot_overwrite_another_members_recorded_vote() {
        service.recordVote(RES_ID, null, "FOR", "MEMBER", null, member(ALI_UID, "Ali Hassan"));
        assertThat(row("Ali Hassan").getVote()).isEqualTo("FOR");

        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Ali Hassan", "AGAINST", "MEMBER", null, member(NOURA_UID, "Noura Al-Qahtani")))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("only cast your own vote");
        assertThat(row("Ali Hassan").getVote()).isEqualTo("FOR");
        assertThat(stored).hasSize(1);
    }

    /**
     * The case a naive overwrite guard breaks: the secretary minutes Ali's show of hands,
     * then Ali corrects it himself. The recorder differs from the actor and Ali holds no
     * on-behalf authority — yet he is the authority on his own vote, so this must succeed
     * and the row must flip back to self-recorded.
     */
    @Test
    void a_member_may_correct_a_vote_the_secretary_recorded_for_them() {
        service.recordVote(RES_ID, "Ali Hassan", "FOR", "MEMBER", "show of hands", secretary());
        assertThat(row("Ali Hassan").getOnBehalf()).isTrue();
        assertThat(row("Ali Hassan").getRecordedByUserId()).isEqualTo(SECRETARY_UID);

        service.recordVote(RES_ID, null, "AGAINST", "MEMBER", "I was misrecorded",
            member(ALI_UID, "Ali Hassan"));

        CommitteeResolutionVote v = row("Ali Hassan");
        assertThat(v.getVote()).isEqualTo("AGAINST");
        assertThat(v.getRecordedByUserId()).isEqualTo(ALI_UID);
        assertThat(v.getOnBehalf()).isFalse();
        assertThat(stored).hasSize(1);
    }

    @Test
    void a_member_may_change_their_own_vote() {
        service.recordVote(RES_ID, null, "FOR", "MEMBER", null, member(ALI_UID, "Ali Hassan"));
        service.recordVote(RES_ID, null, "AGAINST", "MEMBER", "changed my mind",
            member(ALI_UID, "Ali Hassan"));
        assertThat(row("Ali Hassan").getVote()).isEqualTo("AGAINST");
        assertThat(stored).hasSize(1);
    }

    @Test
    void a_secretary_may_correct_a_vote_they_did_not_record() {
        service.recordVote(RES_ID, null, "FOR", "MEMBER", null, member(ALI_UID, "Ali Hassan"));
        service.recordVote(RES_ID, "Ali Hassan", "ABSTAIN", "MEMBER", "minute correction", secretary());
        CommitteeResolutionVote v = row("Ali Hassan");
        assertThat(v.getVote()).isEqualTo("ABSTAIN");
        assertThat(v.getRecordedByUserId()).isEqualTo(SECRETARY_UID);
        assertThat(v.getOnBehalf()).isTrue();
    }

    // ── 5. eligibility ──────────────────────────────────────────────────────

    @Test
    void a_non_voting_member_cannot_cast_a_counted_vote() {
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Khalid Observer", "FOR", "MEMBER", null, secretary()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-voting member");
        verify(voteRepo, never()).save(any());
    }

    @Test
    void a_non_voting_member_cannot_vote_for_themselves_either() {
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, null, "FOR", "MEMBER", null, member(KHALID_UID, "Khalid Observer")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-voting member");
    }

    /** A committee with no roster at all cannot produce a vote — fail closed. */
    @Test
    void an_empty_roster_refuses_every_vote_rather_than_accepting_any_name() {
        when(memberTermRepo.findActiveByCommittee(ORG, COMMITTEE_ID)).thenReturn(List.of());
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Ali Hassan", "FOR", "MEMBER", null, secretary()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, null, "FOR", "MEMBER", null, member(ALI_UID, "Ali Hassan")))
            .isInstanceOf(AccessDeniedException.class);
        verify(voteRepo, never()).save(any());
    }

    // ── 6. ordering — the tenant check must still come first ────────────────

    @Test
    void a_foreign_tenants_resolution_is_refused_before_the_roster_is_ever_read() {
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Ali Hassan", "FOR", "MEMBER", null, secretary()))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verify(memberTermRepo, never()).findActiveByCommittee(any(), any());
        verify(voteRepo, never()).save(any());
    }

    /** An invalid vote value must be rejected before any identity work happens, so a
     *  probe cannot use a bad value to learn whether a name is on the roster. */
    @Test
    void an_invalid_vote_value_is_rejected_before_the_roster_is_read() {
        assertThatThrownBy(() -> service.recordVote(
                RES_ID, "Ali Hassan", "MAYBE", "MEMBER", null, secretary()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vote must be one of");
        verify(memberTermRepo, never()).findActiveByCommittee(any(), any());
    }
}
