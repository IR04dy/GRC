package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-40 / V322 — a resolution's tally must say where it came from, and a recorded one must
 * survive contact with the voting engine.
 *
 * <p><b>What was measured.</b> {@code admin.committee_resolutions} held 20 rows,
 * {@code committee_resolution_vote} held zero, and all 20 carried a scalar tally with outcome
 * APPROVED. Carried out of F2-28 as the resolution-level twin of the quorum defect.
 *
 * <p><b>Why the obvious fix was wrong.</b> "The tally is caller-authored, so refuse it" would have
 * broken the platform's own minute-taking form — {@code CommitteesPage} posts
 * voteFor/voteAgainst/voteAbstain/outcome from the minutes, which is how a board that votes in the
 * room records its decision. The defect was never that a human can record a tally; it was that
 * nothing distinguished a recorded tally from a computed one, so {@code recomputeTally()} would
 * overwrite the former with the latter. Same trap as F2-22a, where the prescribed
 * {@code @JsonIgnore} would have stripped {@code id} from every response.
 *
 * <p>The four tests below are the invariant in full: an asserted tally is labelled and attributed,
 * an empty one is open to voting, voting stamps COMPUTED, and a vote on an asserted resolution is
 * refused rather than silently converted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResolutionTallyProvenanceTest {

    private static final Long ORG = 1L;
    private static final Long RES_ID = 99L;
    private static final Long COMMITTEE_ID = 7L;
    private static final Long MEETING_ID = 55L;

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

    private static final CommitteeResolutionService.VoteActor SECRETARY =
        new CommitteeResolutionService.VoteActor(42L, "board_secretary", true);

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        ReflectionTestUtils.setField(service, "referralRepo", referralRepo);
        when(repo.save(any(CommitteeResolution.class))).thenAnswer(i -> i.getArgument(0));
        when(voteRepo.save(any(CommitteeResolutionVote.class))).thenAnswer(i -> {
            stored.removeIf(v -> v.getMemberName().equals(i.getArgument(0, CommitteeResolutionVote.class)
                                                           .getMemberName()));
            stored.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(voteRepo.findByResolutionId(RES_ID)).thenReturn(stored);
        when(voteRepo.findByResolutionIdAndMemberName(eq(RES_ID), anyString()))
            .thenAnswer(i -> stored.stream()
                .filter(v -> v.getMemberName().equals(i.getArgument(1))).findFirst());
        when(attendeeRepo.findByCommitteeMeetingId(anyLong())).thenReturn(List.of());
        when(referralRepo.findByResolutionId(anyLong())).thenReturn(Optional.empty());
        CommitteeMemberTerm ali = new CommitteeMemberTerm();
        ali.setId(1L); ali.setOrganisationId(ORG); ali.setCommitteeId(COMMITTEE_ID);
        ali.setMemberName("Ali"); ali.setMemberUserId(100L); ali.setRole("Member");
        ali.setIsVotingMember((short) 1);
        when(memberTermRepo.findActiveByCommittee(ORG, COMMITTEE_ID)).thenReturn(List.of(ali));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        stored.clear();
    }

    private static CommitteeResolution request(Integer forC, Integer against, Integer abstain) {
        CommitteeResolution in = new CommitteeResolution();
        in.setCommitteeId(COMMITTEE_ID);
        in.setMeetingId(MEETING_ID);
        in.setResolutionCode("RES-TEST-001");
        in.setSeq(1);
        in.setTitleEn("Approve the treasury policy");
        in.setTitleAr("اعتماد سياسة الخزينة");
        in.setVoteFor(forC);
        in.setVoteAgainst(against);
        in.setVoteAbstain(abstain);
        in.setOutcome("APPROVED");
        return in;
    }

    @Test
    void aTallyPostedFromTheMinutesIsKeptAndLabelledAsserted() {
        CommitteeResolution saved = service.create(request(5, 0, 0), SECRETARY);

        assertThat(saved.getVoteFor())
            .as("the recorded decision must survive — ignoring it was the wrong fix")
            .isEqualTo(5);
        assertThat(saved.getOutcome()).isEqualTo("APPROVED");
        assertThat(saved.getTallySource()).isEqualTo("ASSERTED");
        assertThat(saved.getTallyRecordedByUserId()).isEqualTo(42L);
        assertThat(saved.getTallyRecordedByName()).isEqualTo("board_secretary");
        assertThat(saved.getTallyStampedAt()).isNotNull();

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("RESOLUTION_CREATE"), eq("CommitteeResolution"), any(), detail.capture());
        assertThat(detail.getValue())
            .as("the audit line must say the tally was asserted and by whom, or the label is "
                + "only visible to someone who queries the table")
            .contains("tally=ASSERTED").contains("board_secretary");
    }

    /**
     * Provenance is server-assigned. A caller who could post {@code tallyRecordedByName} would be
     * back at DF-3, where the vote named its own voter — so the entity marks the four
     * {@code tally*} fields READ_ONLY and the service overwrites them regardless.
     */
    @Test
    void callerSuppliedProvenanceIsDiscarded() {
        CommitteeResolution in = request(3, 1, 0);
        in.setTallySource("COMPUTED");             // the interesting lie: claim it was derived
        in.setTallyRecordedByUserId(9999L);
        in.setTallyRecordedByName("the_chairman");

        CommitteeResolution saved = service.create(in, SECRETARY);

        assertThat(saved.getTallySource()).isEqualTo("ASSERTED");
        assertThat(saved.getTallyRecordedByUserId()).isEqualTo(42L);
        assertThat(saved.getTallyRecordedByName()).isEqualTo("board_secretary");
    }

    @Test
    void aResolutionCreatedWithNoTallyIsComputedAndOpenToVoting() {
        CommitteeResolution created = service.create(request(0, 0, 0), SECRETARY);
        assertThat(created.getTallySource()).isEqualTo("COMPUTED");
        assertThat(created.getTallyRecordedByUserId())
            .as("nothing was asserted, so there is nobody to attribute it to")
            .isNull();

        created.setId(RES_ID);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(created));

        CommitteeResolution afterVote = service.recordVote(RES_ID, null, "FOR", "Member", null,
            CommitteeResolutionService.VoteActor.self(100L, "Ali"));

        assertThat(afterVote.getVoteFor()).isEqualTo(1);
        assertThat(afterVote.getOutcome()).isEqualTo("APPROVED");
        assertThat(afterVote.getTallySource()).isEqualTo("COMPUTED");
        assertThat(afterVote.getTallyRecordedByName())
            .as("a computed tally has no asserter; the vote rows carry the attribution")
            .isNull();
    }

    /**
     * The defect this slice exists to close. {@code recomputeTally} rewrites the tally from the
     * vote rows, so one vote on a 5/0/0 APPROVED board resolution would have produced 1/0/0 — and
     * a RECUSED or ABSTAIN vote, 0/0/0 DEFERRED. All 20 pre-V322 rows sit behind this.
     */
    @Test
    void aVoteOnAnAssertedResolutionIsRefusedRatherThanOverwritingIt() {
        CommitteeResolution asserted = service.create(request(5, 0, 0), SECRETARY);
        asserted.setId(RES_ID);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(asserted));

        assertThatThrownBy(() -> service.recordVote(RES_ID, null, "FOR", "Member", null,
                CommitteeResolutionService.VoteActor.self(100L, "Ali")))
            .isInstanceOf(IllegalStateException.class)          // -> 409, not a silent rewrite
            .hasMessageContaining("RES-TEST-001")
            .hasMessageContaining("5/0/0")
            .hasMessageContaining("board_secretary")
            .hasMessageContaining("create the resolution with no tally");

        assertThat(asserted.getVoteFor()).as("the recorded tally is untouched").isEqualTo(5);
        assertThat(asserted.getOutcome()).isEqualTo("APPROVED");
        assertThat(stored).as("and no vote row was written before the refusal").isEmpty();
    }

    /**
     * The 20 legacy rows carry no attribution — V322 labels them {@code ASSERTED_LEGACY} rather
     * than inventing a recorder. The refusal must still name what it is protecting.
     */
    @Test
    void aVoteOnALegacyAssertedResolutionIsRefusedWithoutClaimingAnAsserter() {
        CommitteeResolution legacy = request(4, 1, 0);
        legacy.setId(RES_ID);
        legacy.setOrganisationId(ORG);
        legacy.setTallySource("ASSERTED_LEGACY");
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.recordVote(RES_ID, null, "FOR", "Member", null,
                CommitteeResolutionService.VoteActor.self(100L, "Ali")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("recorded before vote provenance existed")
            .hasMessageContaining("4/1/0");
    }

    /**
     * An unlabelled row with an empty tally has nothing to lose, so it stays votable. The guard
     * keys on what would actually be overwritten rather than on the label alone — otherwise every
     * entity constructed outside {@code create()} would be permanently unvotable, which is exactly
     * how the first version of this guard broke 22 existing provenance tests.
     */
    @Test
    void anUnlabelledResolutionWithNoTallyIsStillVotable() {
        CommitteeResolution unlabelled = request(0, 0, 0);
        unlabelled.setId(RES_ID);
        unlabelled.setOrganisationId(ORG);
        unlabelled.setTallySource(null);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(unlabelled));

        CommitteeResolution after = service.recordVote(RES_ID, null, "AGAINST", "Member", null,
            CommitteeResolutionService.VoteActor.self(100L, "Ali"));

        assertThat(after.getVoteAgainst()).isEqualTo(1);
        assertThat(after.getOutcome()).isEqualTo("REJECTED");
        assertThat(after.getTallySource()).isEqualTo("COMPUTED");
    }
}
