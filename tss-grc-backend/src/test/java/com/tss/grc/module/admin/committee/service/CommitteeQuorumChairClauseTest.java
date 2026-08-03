package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.AttendanceMark;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.AttendanceRequest;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.QuorumView;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeMeetingAttendee;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * S6c — quorum may require someone presiding, and the clause is off until a tenant turns it on.
 *
 * <p><b>Why the default matters more than the feature.</b> Measured in both databases before
 * building: only <b>7 of 19</b> committees in dev and <b>5 of 17</b> in production have an active
 * chair seat, and the chair was present at <b>1 of 18</b> / <b>0 of 17</b> meetings recorded quorate.
 * A globally-on requirement would refuse nearly every meeting either database has ever held — the
 * shape S6b-ii established is unacceptable, where a control's first act refuses everything and the
 * user has no action that could succeed. So the first test here is that nothing changed.
 *
 * <p>Sibling of {@link CommitteeQuorumMinutesTest}, which owns the arithmetic; this file owns only
 * the clause.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeQuorumChairClauseTest {

    private static final Long ORG = 1L;
    private static final Long MEETING_ID = 91L;
    private static final Long COMMITTEE_ID = 12L;
    private static final Long SECRETARY_ACTOR = 3L;
    private static final Long OTHER_ACTOR = 9L;

    @Mock CommitteeRepository                repo;
    @Mock CommitteeMeetingRepository         meetingRepo;
    @Mock CommitteeMeetingAttendeeRepository attendeeRepo;
    @Mock CommitteeMemberTermRepository      memberTermRepo;
    @Mock AuditLogService                    audit;
    /** F2-100 — approveMinutes now announces the binding decisions in the minutes it approves. */
    @Mock CommitteeResolutionService         resolutions;

    @InjectMocks CommitteeService service;

    private CommitteeMeeting meeting;
    private final List<CommitteeMeetingAttendee> sheet = new ArrayList<>();
    private final List<CommitteeMemberTerm> roster = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        meeting = new CommitteeMeeting();
        meeting.setId(MEETING_ID);
        meeting.setOrganisationId(ORG);
        meeting.setCommitteeId(COMMITTEE_ID);
        meeting.setMeetingCode("MTG-2026-CAB-CHAIR");
        meeting.setStatus("COMPLETED");
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(meeting));
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID)).thenReturn(sheet);
        when(attendeeRepo.save(any(CommitteeMeetingAttendee.class))).thenAnswer(i -> {
            CommitteeMeetingAttendee a = i.getArgument(0);
            sheet.add(a);
            return a;
        });
        org.mockito.Mockito.doAnswer(i -> { sheet.clear(); return null; })
            .when(attendeeRepo).deleteByCommitteeMeetingId(MEETING_ID);
        when(memberTermRepo.findActiveByCommittee(ORG, COMMITTEE_ID)).thenReturn(roster);
        committee(false);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        sheet.clear();
        roster.clear();
    }

    private void committee(boolean requiresChair) {
        Committee c = new Committee();
        c.setId(COMMITTEE_ID);
        c.setOrganisationId(ORG);
        c.setQuorumPct(50);
        c.setQuorumRequiresChair(requiresChair);
        when(repo.findById(COMMITTEE_ID)).thenReturn(Optional.of(c));
    }

    /** Seat four voters, one of whom holds {@code chairTitle} (null = an ordinary four-member board). */
    private void seatFour(String chairTitle) {
        roster.clear();
        for (int i = 0; i < 4; i++) {
            CommitteeMemberTerm t = new CommitteeMemberTerm();
            t.setId(100L + i);
            t.setOrganisationId(ORG);
            t.setCommitteeId(COMMITTEE_ID);
            t.setMemberName("voter" + i);
            t.setRole(i == 0 && chairTitle != null ? chairTitle : "Member");
            t.setIsVotingMember((short) 1);
            t.setRenewalStatus("ACTIVE");
            t.setAppointedAt(LocalDate.of(2026, 1, 1));
            t.setTermStartsAt(LocalDate.of(2026, 1, 1));
            roster.add(t);
        }
    }

    /** Mark the seats at the given roster indices present; everyone else absent. */
    private QuorumView record(int... presentIdx) {
        List<Integer> present = new ArrayList<>();
        for (int i : presentIdx) present.add(i);
        List<AttendanceMark> marks = new ArrayList<>();
        for (int i = 0; i < roster.size(); i++)
            marks.add(new AttendanceMark(roster.get(i).getId(), null, null, present.contains(i), false, null));
        return service.recordAttendance(MEETING_ID, new AttendanceRequest(marks), SECRETARY_ACTOR);
    }

    // ── 1. the default: nothing changes ─────────────────────────────────────────────────

    @Test
    @DisplayName("with the clause off, a chairless quorate meeting stays quorate")
    void defaultOffChangesNothing() {
        // This is the assertion that protects 18 dev / 17 prod meetings and the 12 committees in each
        // that have no chair seat at all.
        seatFour(null);
        QuorumView v = record(0, 1);            // 2 of 4, 50% → quorate
        assertThat(v.quorumMet()).isTrue();
        assertThat(v.chairRequired()).isFalse();
        assertThat(v.chairPresent()).isFalse();
    }

    @Test
    @DisplayName("a fresh Committee entity has the clause off — the default lives in one place")
    void entityDefaultIsOff() {
        // The migration defaults the column to 0 and the entity to FALSE. If either drifted, a
        // service-layer save would opt a committee in without anyone deciding to.
        assertThat(new Committee().getQuorumRequiresChair()).isFalse();
    }

    // ── 2. the clause, on ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("with the clause on and nobody presiding, a numerically-quorate meeting is NOT quorate")
    void chairAbsentDefeatsHeadcount() {
        committee(true);
        seatFour(null);
        QuorumView v = record(0, 1, 2, 3);      // everyone present — headcount is not the question
        assertThat(v.presentVoting()).isEqualTo(4);
        assertThat(v.requiredVoting()).isEqualTo(2);
        assertThat(v.quorumMet()).as("4 of 4 present, but nobody presiding").isFalse();
        assertThat(v.chairRequired()).isTrue();
        assertThat(v.chairPresent()).isFalse();
    }

    @Test
    @DisplayName("the chair present satisfies the clause")
    void chairPresentSatisfies() {
        committee(true);
        seatFour("Chair");
        QuorumView v = record(0, 1);
        assertThat(v.quorumMet()).isTrue();
        assertThat(v.chairPresent()).isTrue();
    }

    @Test
    @DisplayName("the chair marked ABSENT does not satisfy it — being seated is not being present")
    void chairSeatedButAbsent() {
        committee(true);
        seatFour("Chair");
        QuorumView v = record(1, 2);            // seats 1 and 2; the chair is seat 0
        assertThat(v.presentVoting()).isEqualTo(2);
        assertThat(v.quorumMet()).isFalse();
        assertThat(v.chairPresent()).isFalse();
    }

    @Test
    @DisplayName("a deputy standing in satisfies it — the decision, exercised end to end")
    void deputyPresides() {
        // Charters conventionally read "the Chair or, in their absence, the Vice-Chair". Both
        // databases hold exactly one Vice-Chair seat, so this path is real, not hypothetical.
        committee(true);
        seatFour("Vice-Chair");
        QuorumView v = record(0, 1);
        assertThat(v.quorumMet()).isTrue();
        assertThat(v.chairPresent()).isTrue();
    }

    @Test
    @DisplayName("the secretary does not preside")
    void secretaryDoesNotPreside() {
        committee(true);
        seatFour("Secretary");
        QuorumView v = record(0, 1);
        assertThat(v.quorumMet()).isFalse();
        assertThat(v.chairPresent()).isFalse();
    }

    @Test
    @DisplayName("headcount still binds — a present chair cannot carry an inquorate meeting")
    void theClauseIsAdditionalNotAlternative() {
        // Both conditions, not either. A one-person meeting chaired by the chair is still one person.
        committee(true);
        seatFour("Chair");
        QuorumView v = record(0);               // 1 of 4, required 2
        assertThat(v.chairPresent()).isTrue();
        assertThat(v.quorumMet()).as("chair present but only 1 of the required 2").isFalse();
    }

    // ── 3. the refusal has to name the right remedy ─────────────────────────────────────

    @Test
    @DisplayName("minutes approval refused for chair absence says so, and says how to resolve it")
    void theRefusalNamesTheChairClause() {
        // A17-D2 and F2-96 both turned on this: a refusal naming the wrong remedy sends the operator
        // to fix something that was never broken. "Ensure enough voting members were present" is
        // actively misleading when four of four were.
        committee(true);
        seatFour(null);
        record(0, 1, 2, 3);
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "approver", OTHER_ACTOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Enough voting members were present (4/2)")
            .hasMessageContaining("requires the chair")
            .hasMessageContaining("quorumRequiresChair");
    }

    @Test
    @DisplayName("a genuinely thin meeting still gets the headcount message, not the chair one")
    void theHeadcountRefusalIsUnchanged() {
        committee(true);
        seatFour("Chair");
        record();                                // nobody present
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "approver", OTHER_ACTOR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Record attendance and ensure enough voting members were present")
            .hasMessageNotContaining("requires the chair");
    }

    @Test
    @DisplayName("with the clause satisfied, minutes approve as before")
    void approvalStillWorks() {
        committee(true);
        seatFour("Chair");
        record(0, 1);
        // F2-39: the attendance author cannot approve, so approve as a different actor.
        assertThatCode(() -> service.approveMinutes(MEETING_ID, "approver", OTHER_ACTOR))
            .doesNotThrowAnyException();
    }

    // ── 4. a guest introduced as "Chair" of something else confers nothing ──────────────

    @Test
    @DisplayName("a guest titled Chair does not satisfy the clause")
    void guestsConferNothing() {
        committee(true);
        seatFour(null);
        List<AttendanceMark> marks = new ArrayList<>();
        for (CommitteeMemberTerm t : roster)
            marks.add(new AttendanceMark(t.getId(), null, null, true, false, null));
        // recordAttendance forces isGuest=true and isVotingMember=false for a non-seat mark; the
        // guestRole is free text the caller chose, which is exactly why it must not count.
        marks.add(new AttendanceMark(null, "Visiting Chair — another board", "Chair", true, false, null));
        QuorumView v = service.recordAttendance(MEETING_ID, new AttendanceRequest(marks), SECRETARY_ACTOR);
        assertThat(v.chairPresent()).as("a visitor's title cannot confer quorum on this committee").isFalse();
        assertThat(v.quorumMet()).isFalse();
    }
}
