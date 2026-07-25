package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMeetingAttendee;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
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

    @InjectMocks CommitteeResolutionService service;

    /** Votes "persisted" by the service, so recomputeTally sees what was saved. */
    private final List<CommitteeResolutionVote> stored = new ArrayList<>();

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
        res.setCommitteeId(7L);
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
        CommitteeReferral ref = new CommitteeReferral();
        ref.setOrganisationId(ORG);
        ref.setResolutionId(RES_ID);
        ref.setRecusalNames(csvNames);
        when(referralRepo.findByResolutionId(RES_ID)).thenReturn(Optional.of(ref));
    }

    private String voteOf(String member) {
        return stored.stream().filter(v -> member.equalsIgnoreCase(v.getMemberName()))
            .map(CommitteeResolutionVote::getVote).findFirst().orElse(null);
    }

    // ── input validation ────────────────────────────────────────────────────

    @Test
    void rejects_an_unknown_vote_value() {
        assertThatThrownBy(() -> service.recordVote(RES_ID, "Ali", "MAYBE", "MEMBER", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vote must be one of");
    }

    @Test
    void rejects_a_blank_member_name() {
        assertThatThrownBy(() -> service.recordVote(RES_ID, "   ", "FOR", "MEMBER", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("memberName is required");
        verify(voteRepo, never()).save(any());
    }

    @Test
    void accepts_a_lowercase_padded_vote_value() {
        service.recordVote(RES_ID, "  Ali  ", " for ", "MEMBER", null);
        assertThat(voteOf("Ali")).isEqualTo("FOR");
    }

    // ── recusal mechanic 1: conflict of interest on the attendance ───────────

    @Test
    void a_member_who_declared_a_conflict_is_forced_to_recused() {
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID))
            .thenReturn(List.of(attendee("Ali Hassan", true)));
        CommitteeResolution out = service.recordVote(RES_ID, "Ali Hassan", "FOR", "MEMBER", null);
        assertThat(voteOf("Ali Hassan")).isEqualTo("RECUSED");
        assertThat(out.getVoteFor()).isZero();
    }

    @Test
    void the_conflict_match_is_case_insensitive() {
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID))
            .thenReturn(List.of(attendee("Ali Hassan", true)));
        service.recordVote(RES_ID, "ALI HASSAN", "AGAINST", "MEMBER", null);
        assertThat(voteOf("ALI HASSAN")).isEqualTo("RECUSED");
    }

    @Test
    void a_member_without_a_declared_conflict_votes_normally() {
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID))
            .thenReturn(List.of(attendee("Ali Hassan", false), attendee("Noura", true)));
        CommitteeResolution out = service.recordVote(RES_ID, "Ali Hassan", "FOR", "MEMBER", null);
        assertThat(voteOf("Ali Hassan")).isEqualTo("FOR");
        assertThat(out.getVoteFor()).isEqualTo(1);
    }

    // ── recusal mechanic 2: Track D referral requester/author ────────────────

    @Test
    void the_referred_items_requester_is_forced_to_recused() {
        givenReferralRecusing("Sara Al-Otaibi,Mansour Bin Ali");
        service.recordVote(RES_ID, "Mansour Bin Ali", "FOR", "CHAIR", null);
        assertThat(voteOf("Mansour Bin Ali")).isEqualTo("RECUSED");
    }

    @Test
    void the_referral_recusal_list_tolerates_padding_and_case() {
        givenReferralRecusing(" sara al-otaibi , Mansour Bin Ali ");
        service.recordVote(RES_ID, "Sara Al-Otaibi", "FOR", "MEMBER", null);
        assertThat(voteOf("Sara Al-Otaibi")).isEqualTo("RECUSED");
    }

    @Test
    void a_member_not_on_the_recusal_list_is_unaffected() {
        givenReferralRecusing("Sara Al-Otaibi");
        CommitteeResolution out = service.recordVote(RES_ID, "Ali Hassan", "FOR", "MEMBER", null);
        assertThat(voteOf("Ali Hassan")).isEqualTo("FOR");
        assertThat(out.getVoteFor()).isEqualTo(1);
    }

    @Test
    void an_explicit_recused_vote_short_circuits_the_conflict_lookup() {
        service.recordVote(RES_ID, "Ali Hassan", "RECUSED", "MEMBER", "stepping out");
        assertThat(voteOf("Ali Hassan")).isEqualTo("RECUSED");
        verify(attendeeRepo, never()).findByCommitteeMeetingId(anyLong());
    }

    // ── tally derivation + outcome mapping ──────────────────────────────────

    @Test
    void recused_votes_are_excluded_from_every_tally_bucket() {
        givenReferralRecusing("Sara Al-Otaibi");
        service.recordVote(RES_ID, "Ali",   "FOR",     "MEMBER", null);
        service.recordVote(RES_ID, "Noura", "ABSTAIN", "MEMBER", null);
        CommitteeResolution out = service.recordVote(RES_ID, "Sara Al-Otaibi", "FOR", "MEMBER", null);

        assertThat(voteOf("Sara Al-Otaibi")).isEqualTo("RECUSED");
        assertThat(out.getVoteFor()).isEqualTo(1);       // Sara's FOR must not count
        assertThat(out.getVoteAgainst()).isZero();
        assertThat(out.getVoteAbstain()).isEqualTo(1);
        assertThat(out.getOutcome()).isEqualTo("APPROVED");
    }

    @Test
    void outcome_is_approved_when_for_outnumbers_against() {
        service.recordVote(RES_ID, "Ali",   "FOR",     "MEMBER", null);
        service.recordVote(RES_ID, "Noura", "FOR",     "MEMBER", null);
        CommitteeResolution out = service.recordVote(RES_ID, "Omar", "AGAINST", "MEMBER", null);
        assertThat(out.getOutcome()).isEqualTo("APPROVED");
        assertThat(out.getVoteFor()).isEqualTo(2);
        assertThat(out.getVoteAgainst()).isEqualTo(1);
    }

    @Test
    void a_tie_fails_the_motion_rather_than_carrying_it() {
        service.recordVote(RES_ID, "Ali", "FOR", "MEMBER", null);
        CommitteeResolution out = service.recordVote(RES_ID, "Omar", "AGAINST", "MEMBER", null);
        assertThat(out.getOutcome()).isEqualTo("REJECTED");
    }

    @Test
    void outcome_is_rejected_when_against_outnumbers_for() {
        service.recordVote(RES_ID, "Ali",   "AGAINST", "MEMBER", null);
        CommitteeResolution out = service.recordVote(RES_ID, "Omar", "AGAINST", "MEMBER", null);
        assertThat(out.getOutcome()).isEqualTo("REJECTED");
        assertThat(out.getVoteFor()).isZero();
    }

    @Test
    void only_abstentions_and_recusals_leave_the_motion_deferred() {
        givenReferralRecusing("Sara");
        service.recordVote(RES_ID, "Ali",  "ABSTAIN", "MEMBER", null);
        CommitteeResolution out = service.recordVote(RES_ID, "Sara", "FOR", "MEMBER", null);
        assertThat(out.getOutcome()).isEqualTo("DEFERRED");
        assertThat(out.getVoteFor()).isZero();
        assertThat(out.getVoteAbstain()).isEqualTo(1);
    }

    @Test
    void a_member_changing_their_vote_updates_the_row_instead_of_double_counting() {
        service.recordVote(RES_ID, "Ali", "FOR", "MEMBER", null);
        CommitteeResolution out = service.recordVote(RES_ID, "Ali", "AGAINST", "MEMBER", "changed my mind");
        assertThat(stored).hasSize(1);
        assertThat(out.getVoteFor()).isZero();
        assertThat(out.getVoteAgainst()).isEqualTo(1);
    }

    // ── provenance ──────────────────────────────────────────────────────────

    @Test
    void every_vote_is_audited_with_the_resulting_tally_and_outcome() {
        service.recordVote(RES_ID, "Ali", "FOR", "CHAIR", null);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("RESOLUTION_VOTE"), eq("CommitteeResolution"), eq(RES_ID), detail.capture());
        assertThat(detail.getValue())
            .contains("Ali voted FOR")
            .contains("1/0/0")
            .contains("APPROVED");
    }

    @Test
    void the_vote_row_carries_the_meeting_org_and_role_for_the_minutes() {
        service.recordVote(RES_ID, "Ali", "FOR", "CHAIR", "because");
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
        CommitteeResolution out = service.recordVote(RES_ID, "Ali", "FOR", "MEMBER", null);
        assertThat(voteOf("Ali")).isEqualTo("FOR");
        assertThat(out.getOutcome()).isEqualTo("APPROVED");
    }
}
