package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S7a — <b>F2-89</b>: a committee motion must be raisable before it is decided, and <b>F2-100</b>:
 * the decision must be announced when — and only when — it becomes binding.
 *
 * <p><b>What was measured, 2026-08-03, before any of this was written.</b>
 *
 * <pre>
 *   CK_resolution_outcome permitted APPROVED | REJECTED | DEFERRED | WITHDRAWN  — four DECISIONS
 *   admin.committee_resolutions ................ 22 rows, every one APPROVED
 *   AR-152  COMMITTEE_RESOLUTION_DEFERRED ...... enabled, fire_count 38, last 2026-08-03 09:56
 *   resolutions with outcome = 'DEFERRED' ...... 0
 *   AR-147  COMMITTEE_RESOLUTION_APPROVED ...... enabled, last fired 2026-07-26 14:19
 *   resolutions 69 + 70 (voted to APPROVED) .... created 2026-08-03 09:56  ← a week LATER
 * </pre>
 *
 * <p>Thirty-eight fires of the re-table rule and not one deferred resolution: every fire was on a
 * transient placeholder, because {@code create()} published {@code COMMITTEE_RESOLUTION_<outcome>}
 * and the caller had to name a verdict to raise a motion at all. The golden-path IT picked
 * {@code DEFERRED} with the comment "there is no undecided state"; the only UI that creates a
 * resolution defaults the field to {@code APPROVED}, which is wired to a regulator disclosure clock.
 *
 * <p>And the second half is provable from those last two lines: both resolutions decided by counted
 * in-platform votes reached {@code APPROVED} a week <em>after</em> the APPROVED rule last fired, so
 * it never fired for either. The module had exactly one {@code publishEvent}, in {@code create()}.
 * Raising a motion notified the secretary to re-table it; deciding it notified nobody.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeMotionLifecycleTest {

    private static final Long ORG = 1L;
    private static final Long RES_ID = 501L;
    private static final Long COMMITTEE_ID = 7L;
    private static final Long MEETING_ID = 55L;

    @Mock CommitteeResolutionRepository       repo;
    @Mock AuditLogService                     audit;
    @Mock ApplicationEventPublisher           publisher;
    @Mock CommitteeResolutionVoteRepository   voteRepo;
    @Mock CommitteeMeetingAttendeeRepository  attendeeRepo;
    @Mock CommitteeReferralRepository         referralRepo;
    @Mock CommitteeMemberTermRepository       memberTermRepo;
    @Mock CommitteeMeetingRepository          meetingRepo;

    @InjectMocks CommitteeResolutionService service;

    private final List<CommitteeResolutionVote> stored = new ArrayList<>();
    private final CommitteeMeeting meeting = new CommitteeMeeting();

    private static final CommitteeResolutionService.VoteActor SECRETARY =
        new CommitteeResolutionService.VoteActor(42L, "board_secretary", true);

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        ReflectionTestUtils.setField(service, "referralRepo", referralRepo);
        when(repo.save(any(CommitteeResolution.class))).thenAnswer(i -> {
            CommitteeResolution r = i.getArgument(0);
            if (r.getId() == null) r.setId(RES_ID);
            return r;
        });
        when(voteRepo.save(any(CommitteeResolutionVote.class))).thenAnswer(i -> {
            CommitteeResolutionVote v = i.getArgument(0);
            stored.removeIf(x -> x.getMemberName().equals(v.getMemberName()));
            stored.add(v);
            return v;
        });
        when(voteRepo.findByResolutionId(RES_ID)).thenReturn(stored);
        when(voteRepo.findByResolutionIdAndMemberName(eq(RES_ID), anyString()))
            .thenAnswer(i -> stored.stream()
                .filter(v -> v.getMemberName().equals(i.getArgument(1))).findFirst());
        when(attendeeRepo.findByCommitteeMeetingId(anyLong())).thenReturn(List.of());
        when(referralRepo.findByResolutionId(anyLong())).thenReturn(Optional.empty());

        // A three-seat voting roster.
        List<CommitteeMemberTerm> roster = new ArrayList<>();
        for (String name : List.of("Ali", "Noura", "Khalid")) {
            CommitteeMemberTerm t = new CommitteeMemberTerm();
            t.setId((long) (roster.size() + 1)); t.setOrganisationId(ORG);
            t.setCommitteeId(COMMITTEE_ID); t.setMemberName(name); t.setRole("Member");
            t.setIsVotingMember((short) 1);
            roster.add(t);
        }
        when(memberTermRepo.findActiveByCommittee(ORG, COMMITTEE_ID)).thenReturn(roster);

        meeting.setId(MEETING_ID);
        meeting.setOrganisationId(ORG);
        meeting.setStatus("COMPLETED");            // not yet binding
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(meeting));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        stored.clear();
    }

    private CommitteeResolution request() {
        CommitteeResolution in = new CommitteeResolution();
        in.setCommitteeId(COMMITTEE_ID);
        in.setMeetingId(MEETING_ID);
        in.setResolutionCode("RES-S7A-001");
        in.setSeq(1);
        in.setTitleEn("Authorise change CHG-2026-0042");
        in.setTitleAr("اعتماد التغيير CHG-2026-0042");
        in.setBodyEn("The committee resolves to authorise the change.");
        in.setBodyAr("تقرر اللجنة اعتماد التغيير.");
        return in;
    }

    /** Every event name published during this test. */
    private List<String> published() {
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(publisher, atLeast(0)).publishEvent(cap.capture());
        return cap.getAllValues().stream()
            .filter(o -> o instanceof DomainEvent)
            .map(o -> ((DomainEvent) o).triggerEvent())
            .toList();
    }

    // ══════════════════════════ F2-89 — raising a motion ══════════════════════════

    @Test
    @DisplayName("F2-89 — a motion created with no outcome is PROPOSED, not a verdict")
    void aMotionMayBeRaisedUndecided() {
        CommitteeResolution saved = service.create(request(), SECRETARY);

        assertThat(saved.getOutcome()).isEqualTo("PROPOSED");
        assertThat(CommitteeDecisionAuthority.isDecided(saved)).isFalse();
        // The tally is open to voting: F2-40 labels an untallied resolution COMPUTED precisely so
        // recordVote() will accept it.
        assertThat(saved.getTallySource()).isEqualTo("COMPUTED");
        assertThat(saved.getVoteFor()).isZero();
    }

    @Test
    @DisplayName("F2-89 — raising a motion publishes COMMITTEE_MOTION_RAISED and NO decision event")
    void raisingAMotionAnnouncesNoDecision() {
        service.create(request(), SECRETARY);

        assertThat(published()).containsExactly("COMMITTEE_MOTION_RAISED");
        // The measured failure, asserted directly: these are the two enabled rules a placeholder
        // used to trip. AR-146/AR-147 listen to the first, AR-152 to the second.
        assertThat(published())
            .as("raising a motion must not start a regulator disclosure clock (AR-146/AR-147) "
              + "nor ask the secretary to re-table it (AR-152)")
            .doesNotContain("COMMITTEE_RESOLUTION_APPROVED", "COMMITTEE_RESOLUTION_DEFERRED");
    }

    @Test
    @DisplayName("an outcome outside the vocabulary is refused with the raise instruction")
    void anUnknownOutcomeIsRefused() {
        CommitteeResolution in = request();
        in.setOutcome("CARRIED");

        assertThatThrownBy(() -> service.create(in, SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PROPOSED");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("a lower-case outcome is normalised rather than rejected")
    void outcomeIsNormalised() {
        CommitteeResolution in = request();
        in.setOutcome("approved");
        meeting.setStatus("MINUTES_APPROVED");

        assertThat(service.create(in, SECRETARY).getOutcome()).isEqualTo("APPROVED");
    }

    // ══════════════════════ F2-89 — the tally's undecided answer ══════════════════════

    @Test
    @DisplayName("the tally→outcome mapping, including the zero-vote case no caller can reach")
    void theTallyMapping() {
        // A pure function so every case is stated and testable. An earlier draft of this test
        // tried to reach the zero-vote branch through service.votes() — a read-only method — and
        // therefore asserted nothing at all. A test that cannot fail is worse than no test,
        // because it reads as coverage.
        assertThat(CommitteeResolutionService.outcomeFor(0, 0, 0))
            .as("no vote rows: the motion is still only raised, not deferred")
            .isEqualTo("PROPOSED");
        assertThat(CommitteeResolutionService.outcomeFor(3, 2, 1)).isEqualTo("APPROVED");
        assertThat(CommitteeResolutionService.outcomeFor(2, 1, 1))
            .as("a tie among cast votes fails the motion").isEqualTo("REJECTED");
        assertThat(CommitteeResolutionService.outcomeFor(2, 0, 2)).isEqualTo("REJECTED");
        assertThat(CommitteeResolutionService.outcomeFor(3, 0, 0))
            .as("votes recorded but none cast for or against — the committee could not decide")
            .isEqualTo("DEFERRED");
        // Every answer must be in the vocabulary the CHECK constraint permits.
        for (int rows = 0; rows <= 3; rows++)
            for (int f = 0; f <= 3; f++)
                for (int a = 0; a <= 3; a++)
                    assertThat(CommitteeDecisionAuthority.ALL)
                        .as("outcomeFor(%d,%d,%d)", rows, f, a)
                        .contains(CommitteeResolutionService.outcomeFor(rows, f, a));
    }

    @Test
    @DisplayName("abstentions and recusals only DO decide the motion — DEFERRED, a real outcome")
    void onlyAbstentionsIsADeferral() {
        CommitteeResolution r = service.create(request(), SECRETARY);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(r));

        service.recordVote(RES_ID, "Ali", "ABSTAIN", "Member", null, SECRETARY);

        // The committee met and a vote was recorded; nobody supported the motion. That is a
        // deferral, and AR-152's "re-table at the next meeting" is the correct response to it —
        // which is exactly why a motion merely RAISED must not report the same value.
        assertThat(r.getOutcome()).isEqualTo("DEFERRED");
        assertThat(CommitteeDecisionAuthority.isDecided(r)).isTrue();
    }

    @Test
    @DisplayName("a counted majority decides the motion APPROVED")
    void aMajorityApproves() {
        CommitteeResolution r = service.create(request(), SECRETARY);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(r));

        service.recordVote(RES_ID, "Ali",   "FOR",     "Member", null, SECRETARY);
        service.recordVote(RES_ID, "Noura", "FOR",     "Member", null, SECRETARY);
        service.recordVote(RES_ID, "Khalid","AGAINST", "Member", null, SECRETARY);

        assertThat(r.getOutcome()).isEqualTo("APPROVED");
        assertThat(r.getVoteFor()).isEqualTo(2);
        assertThat(r.getVoteAgainst()).isEqualTo(1);
    }

    // ══════════════════════ F2-100 — announcing the decision ══════════════════════

    @Test
    @DisplayName("F2-100 — the deciding vote on APPROVED minutes announces the decision, once")
    void theDecidingVoteAnnouncesOnApprovedMinutes() {
        meeting.setStatus("MINUTES_APPROVED");
        CommitteeResolution r = service.create(request(), SECRETARY);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(r));

        service.recordVote(RES_ID, "Ali",   "FOR", "Member", null, SECRETARY);
        service.recordVote(RES_ID, "Noura", "FOR", "Member", null, SECRETARY);

        assertThat(r.getDecisionPublishedAt()).isNotNull();
        // Announced exactly once despite two votes both landing on APPROVED — the second is
        // suppressed by decision_published_at, not by luck.
        assertThat(published()).filteredOn("COMMITTEE_RESOLUTION_APPROVED"::equals).hasSize(1);
    }

    @Test
    @DisplayName("F2-100 — a decided motion on unapproved minutes is NOT announced yet")
    void nothingIsAnnouncedBeforeTheMinutesAreApproved() {
        // meeting is COMPLETED, not MINUTES_APPROVED
        CommitteeResolution r = service.create(request(), SECRETARY);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(r));

        service.recordVote(RES_ID, "Ali",   "FOR", "Member", null, SECRETARY);
        service.recordVote(RES_ID, "Noura", "FOR", "Member", null, SECRETARY);

        assertThat(r.getOutcome()).isEqualTo("APPROVED");
        assertThat(r.getDecisionPublishedAt())
            .as("a decision is binding on approved minutes — CommitteeDecisionAuthority")
            .isNull();
        assertThat(published()).doesNotContain("COMMITTEE_RESOLUTION_APPROVED");
    }

    @Test
    @DisplayName("F2-100 — approving the minutes announces the decision that was waiting")
    void approvingTheMinutesAnnouncesTheWaitingDecision() {
        CommitteeResolution r = service.create(request(), SECRETARY);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(r));
        service.recordVote(RES_ID, "Ali",   "FOR", "Member", null, SECRETARY);
        service.recordVote(RES_ID, "Noura", "FOR", "Member", null, SECRETARY);
        assertThat(r.getDecisionPublishedAt()).isNull();      // still COMPLETED

        // now the minutes are approved — this is what CommitteeService.approveMinutes calls
        meeting.setStatus("MINUTES_APPROVED");
        when(repo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(ORG, MEETING_ID))
            .thenReturn(List.of(r));

        assertThat(service.publishBindingDecisionsForMeeting(MEETING_ID)).isEqualTo(1);
        assertThat(r.getDecisionPublishedAt()).isNotNull();
        assertThat(published()).contains("COMMITTEE_RESOLUTION_APPROVED");
    }

    @Test
    @DisplayName("F2-100 — approving minutes does not announce a motion nobody voted on")
    void anUndecidedMotionIsNotAnnouncedAtMinutesApproval() {
        CommitteeResolution r = service.create(request(), SECRETARY);   // PROPOSED, no votes
        meeting.setStatus("MINUTES_APPROVED");
        when(repo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(ORG, MEETING_ID))
            .thenReturn(List.of(r));

        assertThat(service.publishBindingDecisionsForMeeting(MEETING_ID)).isZero();
        assertThat(r.getDecisionPublishedAt()).isNull();
        assertThat(published()).doesNotContain("COMMITTEE_RESOLUTION_PROPOSED");
    }

    @Test
    @DisplayName("F2-100 — a second minutes-approval pass re-announces nothing")
    void announcementIsIdempotentAcrossPasses() {
        meeting.setStatus("MINUTES_APPROVED");
        CommitteeResolution r = service.create(request(), SECRETARY);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(r));
        service.recordVote(RES_ID, "Ali", "FOR", "Member", null, SECRETARY);
        when(repo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(ORG, MEETING_ID))
            .thenReturn(List.of(r));

        assertThat(service.publishBindingDecisionsForMeeting(MEETING_ID)).isZero();  // vote did it
        assertThat(service.publishBindingDecisionsForMeeting(MEETING_ID)).isZero();
        assertThat(published()).filteredOn("COMMITTEE_RESOLUTION_APPROVED"::equals).hasSize(1);
    }

    @Test
    @DisplayName("F2-100 — the secretariat's post-approval minute-up announces at create")
    void aResolutionRecordedAfterTheMinutesAreApprovedAnnouncesImmediately() {
        // Measured 19 of 22: the dominant real-world order is approve the minutes, THEN type up
        // what was resolved. Publishing only at minutes approval would have announced nothing for
        // this path — the mirror image of the bug.
        meeting.setStatus("MINUTES_APPROVED");
        CommitteeResolution in = request();
        in.setOutcome("APPROVED");
        in.setVoteFor(5);

        CommitteeResolution saved = service.create(in, SECRETARY);

        assertThat(saved.getTallySource()).isEqualTo("ASSERTED");
        assertThat(saved.getDecisionPublishedAt()).isNotNull();
        assertThat(published()).containsExactly("COMMITTEE_RESOLUTION_APPROVED");
    }

    @Test
    @DisplayName("a caller cannot pre-stamp decision_published_at to silence the announcement")
    void thePublishStampIsServerAssigned() {
        meeting.setStatus("MINUTES_APPROVED");
        CommitteeResolution in = request();
        in.setOutcome("APPROVED");
        in.setVoteFor(3);
        in.setDecisionPublishedAt(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));  // forged

        CommitteeResolution saved = service.create(in, SECRETARY);

        assertThat(saved.getDecisionPublishedAt()).isAfter(java.time.LocalDateTime.of(2020, 1, 1, 0, 0));
        assertThat(published()).containsExactly("COMMITTEE_RESOLUTION_APPROVED");
    }

    @Test
    @DisplayName("an announcement failure does not roll back the recorded vote")
    void theVoteSurvivesAFailedAnnouncement() {
        // The decision is the fact; the notification is a consequence. F2-90's lesson inverted:
        // what an auditor reads is the row and the ledger entry, and neither may be lost because
        // a listener threw.
        meeting.setStatus("MINUTES_APPROVED");
        CommitteeResolution r = service.create(request(), SECRETARY);
        when(repo.findByIdAndOrganisationId(RES_ID, ORG)).thenReturn(Optional.of(r));
        // The <Object> witness matters: ApplicationEventPublisher overloads publishEvent on both
        // Object and ApplicationEvent, and a bare lambda binds to the more specific one — which
        // this code never calls, so the stub would silently never apply and the test would pass
        // without ever exercising a failure.
        doThrow(new IllegalStateException("listener exploded"))
            .when(publisher).publishEvent(ArgumentMatchers.<Object>argThat(o -> o instanceof DomainEvent
                && "COMMITTEE_RESOLUTION_APPROVED".equals(((DomainEvent) o).triggerEvent())));

        service.recordVote(RES_ID, "Ali", "FOR", "Member", null, SECRETARY);

        assertThat(r.getOutcome()).isEqualTo("APPROVED");
        assertThat(r.getVoteFor()).isEqualTo(1);
        assertThat(stored).hasSize(1);
    }
}
