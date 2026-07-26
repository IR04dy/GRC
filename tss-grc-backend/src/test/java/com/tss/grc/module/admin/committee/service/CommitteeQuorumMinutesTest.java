package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.AttendanceMark;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.AttendanceRequest;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.MeetingMinutesRequest;
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
import com.tss.grc.module.signature.service.SignatureService;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deterministic quorum and the minutes-approval gate — the two controls that make a committee
 * decision binding.
 *
 * <p>Why it matters beyond the committee module: the change/policy referral gate requires
 * {@code MINUTES_APPROVED}, and minutes cannot be approved unless the meeting was quorate. So
 * quorum arithmetic is transitively the precondition for authorising a change or publishing a
 * policy.
 *
 * <p><b>REWRITTEN FOR F2-28 / DF-5 — the previous version of this file asserted the defect.</b>
 * It built a free-text attendance sheet and asserted that {@code totalVoting} came from it, which
 * is exactly the finding: the caller chose both the numerator <em>and the denominator</em> of
 * quorum. Four voting rows, two marked present, quorate — and a committee of seven where three
 * attend and only those three are typed in read as 3/3. Every arithmetic case below is preserved,
 * but the denominator now comes from the committee's <b>active voting roster</b>, which is what
 * quorum means: the minimum proportion of <em>eligible</em> members who must be present.
 *
 * <p>The scale of it, measured: {@code committee_meeting_attendee} held <b>0 rows</b>, so CH-14's
 * deterministic path had never executed and all 17 quorate meetings (15 e-signed) got their flag
 * from {@code quorumMet} on {@code complete}. A test that passed against the sheet is how that
 * survived review — the arithmetic was right and its <em>input</em> was forgeable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeQuorumMinutesTest {

    private static final Long ORG = 1L;
    private static final Long MEETING_ID = 55L;
    private static final Long COMMITTEE_ID = 7L;
    private static final Long SECRETARY = 3L;
    private static final Long CHAIR = 9L;

    @Mock CommitteeRepository                 repo;
    @Mock CommitteeMeetingRepository          meetingRepo;
    @Mock CommitteeMeetingAttendeeRepository  attendeeRepo;
    @Mock CommitteeMemberTermRepository       memberTermRepo;
    @Mock AuditLogService                     audit;
    @Mock SignatureService                    signatureService;

    @InjectMocks CommitteeService service;

    private CommitteeMeeting meeting;
    /** Stands in for the persisted attendance rows. */
    private final List<CommitteeMeetingAttendee> sheet = new ArrayList<>();
    /** Stands in for the committee's active roster. */
    private final List<CommitteeMemberTerm> roster = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        // signatureService is @Autowired(required = false) FIELD injection; Mockito uses the
        // @RequiredArgsConstructor for the final dependencies and then skips property
        // injection, so wire it explicitly (see the absent-bean test below).
        ReflectionTestUtils.setField(service, "signatureService", signatureService);

        meeting = new CommitteeMeeting();
        meeting.setId(MEETING_ID);
        meeting.setOrganisationId(ORG);
        meeting.setCommitteeId(COMMITTEE_ID);
        meeting.setMeetingCode("MTG-2026-CAB-A1B2");
        meeting.setStatus("COMPLETED");
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(meeting));

        // Model the repository: save appends, delete clears. recordAttendance rebuilds the sheet
        // and then reads it back, so the two must agree or the test proves nothing.
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID)).thenReturn(sheet);
        when(attendeeRepo.save(any(CommitteeMeetingAttendee.class))).thenAnswer(i -> {
            CommitteeMeetingAttendee a = i.getArgument(0);
            sheet.add(a);
            return a;
        });
        org.mockito.Mockito.doAnswer(i -> { sheet.clear(); return null; })
            .when(attendeeRepo).deleteByCommitteeMeetingId(MEETING_ID);
        when(memberTermRepo.findActiveByCommittee(ORG, COMMITTEE_ID)).thenReturn(roster);
        withQuorumPct(50);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        sheet.clear();
        roster.clear();
    }

    private void withQuorumPct(Integer pct) {
        Committee c = new Committee();
        c.setId(COMMITTEE_ID);
        c.setOrganisationId(ORG);
        c.setQuorumPct(pct);
        when(repo.findById(COMMITTEE_ID)).thenReturn(Optional.of(c));
    }

    /** Seat the committee: {@code voting} voting seats plus {@code nonVoting} standing invitees. */
    private void rosterOf(int voting, int nonVoting) {
        roster.clear();
        for (int i = 0; i < voting; i++)    roster.add(seat(100L + i, "voter" + i, (short) 1));
        for (int i = 0; i < nonVoting; i++) roster.add(seat(200L + i, "invitee" + i, (short) 0));
    }

    private static CommitteeMemberTerm seat(Long id, String name, Short voting) {
        CommitteeMemberTerm t = new CommitteeMemberTerm();
        t.setId(id);
        t.setOrganisationId(ORG);
        t.setCommitteeId(COMMITTEE_ID);
        t.setMemberName(name);
        t.setRole(voting == 1 ? "Member" : "Standing Invitee");
        t.setIsVotingMember(voting);
        t.setRenewalStatus("ACTIVE");
        t.setAppointedAt(LocalDate.of(2026, 1, 1));
        t.setTermStartsAt(LocalDate.of(2026, 1, 1));
        return t;
    }

    /**
     * Record attendance for the first {@code presentVoting} voting seats, marking every other
     * seated voter present=false. Returns the computed quorum view.
     */
    private QuorumView record(int presentVoting) {
        List<AttendanceMark> marks = new ArrayList<>();
        int seen = 0;
        for (CommitteeMemberTerm t : roster) {
            if (t.getIsVotingMember() == 1) {
                marks.add(new AttendanceMark(t.getId(), null, null, seen++ < presentVoting, false, null));
            }
        }
        return service.recordAttendance(MEETING_ID, new AttendanceRequest(marks), SECRETARY);
    }

    // ── 1. quorum arithmetic, denominator from the ROSTER ───────────────────

    @Test
    void half_of_an_even_board_meets_a_fifty_percent_quorum() {
        rosterOf(4, 0);
        QuorumView q = record(2);
        assertThat(q.totalVoting()).isEqualTo(4);
        assertThat(q.presentVoting()).isEqualTo(2);
        assertThat(q.requiredVoting()).isEqualTo(2);
        assertThat(q.quorumMet()).isTrue();
    }

    @Test
    void one_short_of_the_requirement_is_not_quorate() {
        rosterOf(4, 0);
        assertThat(record(1).quorumMet()).isFalse();
    }

    @Test
    void an_odd_board_rounds_the_requirement_up() {
        rosterOf(3, 0);                     // 3 voting at 50% => ceil(1.5) = 2 required
        assertThat(record(1).requiredVoting()).isEqualTo(2);
        assertThat(record(1).quorumMet()).isFalse();
        assertThat(record(2).quorumMet()).isTrue();
    }

    @Test
    void a_committee_without_a_configured_percentage_defaults_to_fifty() {
        withQuorumPct(null);
        rosterOf(4, 0);
        QuorumView q = record(2);
        assertThat(q.quorumPct()).isEqualTo(50);
        assertThat(q.quorumMet()).isTrue();
    }

    @Test
    void a_hundred_percent_quorum_requires_every_voting_member() {
        withQuorumPct(100);
        rosterOf(4, 0);
        assertThat(record(3).quorumMet()).isFalse();
        assertThat(record(4).quorumMet()).isTrue();
    }

    @Test
    void a_tiny_percentage_still_requires_at_least_one_member_present() {
        withQuorumPct(1);                   // max(1, ceil(..)) — never "nobody needed"
        rosterOf(4, 0);
        QuorumView q = record(0);
        assertThat(q.requiredVoting()).isEqualTo(1);
        assertThat(q.quorumMet()).isFalse();
    }

    /** A committee with no active voting seats cannot transact, whoever is in the room. */
    @Test
    void a_roster_with_no_voting_seats_is_never_quorate() {
        rosterOf(0, 5);
        QuorumView q = record(0);
        assertThat(q.totalVoting()).isZero();
        assertThat(q.quorumMet()).isFalse();
    }

    // ── 2. THE FINDING: the sheet can no longer set the denominator ─────────

    /**
     * <b>The core regression.</b> Under the old model, marking 2 of a 7-member committee present
     * and recording only those two produced totalVoting = 2 → quorate. The denominator is the
     * roster, so the unmarked five are absent and count against it.
     */
    @Test
    void unrecorded_voting_seats_are_absent_and_still_count_in_the_denominator() {
        rosterOf(7, 0);
        QuorumView q = record(2);
        assertThat(q.totalVoting()).isEqualTo(7);        // not 2
        assertThat(q.presentVoting()).isEqualTo(2);
        assertThat(q.requiredVoting()).isEqualTo(4);
        assertThat(q.quorumMet()).isFalse();
    }

    /** …and the count of seats nobody marked is surfaced, so "absent by default" is visible. */
    @Test
    void the_view_reports_how_many_voting_seats_were_never_marked() {
        rosterOf(5, 0);
        List<AttendanceMark> two = List.of(
            new AttendanceMark(100L, null, null, true, false, null),
            new AttendanceMark(101L, null, null, true, false, null));
        QuorumView q = service.recordAttendance(MEETING_ID, new AttendanceRequest(two), SECRETARY);
        assertThat(q.totalVoting()).isEqualTo(5);
        assertThat(q.unrecordedVoting()).isEqualTo(3);
        assertThat(q.quorumMet()).isFalse();             // 2 of 5 present, 3 required
    }

    /** A mark that does not resolve to an active seat of THIS committee is refused. */
    @Test
    void a_mark_for_a_seat_outside_the_roster_is_refused() {
        rosterOf(3, 0);
        AttendanceRequest req = new AttendanceRequest(
            List.of(new AttendanceMark(4242L, null, null, true, false, null)));
        assertThatThrownBy(() -> service.recordAttendance(MEETING_ID, req, SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not on this committee's active roster");
    }

    /** Two marks for one seat would count that member twice toward presence. */
    @Test
    void the_same_seat_cannot_be_marked_twice() {
        rosterOf(3, 0);
        AttendanceRequest req = new AttendanceRequest(List.of(
            new AttendanceMark(100L, null, null, true, false, null),
            new AttendanceMark(100L, null, null, true, false, null)));
        assertThatThrownBy(() -> service.recordAttendance(MEETING_ID, req, SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("appears twice");
    }

    /** Voting eligibility is copied from the seat; the request has no say in it. */
    @Test
    void eligibility_comes_from_the_seat_not_the_request() {
        rosterOf(2, 1);                      // seat 200 is a Standing Invitee
        AttendanceRequest req = new AttendanceRequest(List.of(
            new AttendanceMark(100L, null, null, true, false, null),
            new AttendanceMark(200L, null, null, true, false, null)));
        QuorumView q = service.recordAttendance(MEETING_ID, req, SECRETARY);
        assertThat(q.totalVoting()).isEqualTo(2);
        assertThat(q.presentVoting()).isEqualTo(1);      // the invitee does not count
        assertThat(q.attendees()).anySatisfy(a -> {
            if (a.memberTermId().equals(200L)) assertThat(a.votingMember()).isFalse();
        });
    }

    /** A non-member who attended is recorded — the minutes must be factual — but never counts. */
    @Test
    void a_guest_is_recorded_and_never_counts_toward_quorum() {
        rosterOf(4, 0);
        AttendanceRequest req = new AttendanceRequest(List.of(
            new AttendanceMark(100L, null, null, true, false, null),
            new AttendanceMark(null, "KPMG audit partner", "External auditor", true, false, null)));
        QuorumView q = service.recordAttendance(MEETING_ID, req, SECRETARY);
        assertThat(q.totalVoting()).isEqualTo(4);
        assertThat(q.presentVoting()).isEqualTo(1);
        assertThat(q.attendees()).hasSize(2);
        assertThat(q.attendees()).anySatisfy(a -> {
            if (Boolean.TRUE.equals(a.guest())) {
                assertThat(a.memberName()).isEqualTo("KPMG audit partner");
                assertThat(a.votingMember()).isFalse();
            }
        });
    }

    @Test
    void a_mark_that_is_neither_a_seat_nor_a_named_guest_is_refused() {
        rosterOf(2, 0);
        AttendanceRequest req = new AttendanceRequest(
            List.of(new AttendanceMark(null, "  ", null, true, false, null)));
        assertThatThrownBy(() -> service.recordAttendance(MEETING_ID, req, SECRETARY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("either a roster seat");
    }

    // ── 3. quorum has one origin, and its provenance is recorded ───────────

    @Test
    void recording_attendance_stamps_the_quorum_as_COMPUTED() {
        rosterOf(4, 0);
        record(2);
        assertThat(meeting.getQuorumSource()).isEqualTo("COMPUTED");
        assertThat(meeting.getQuorumDenominator()).isEqualTo(4);
        assertThat(meeting.getQuorumPresent()).isEqualTo(2);
        assertThat(meeting.getQuorumRequired()).isEqualTo(2);
        assertThat(meeting.getQuorumComputedAt()).isNotNull();
        assertThat(meeting.getAttendanceRecordedBy()).isEqualTo(SECRETARY);
    }

    /**
     * The exact channel through which all 17 legacy quorum facts arrived. Refused loudly rather
     * than ignored quietly — a silently-dropped security value teaches the caller nothing.
     */
    @Test
    void completing_a_meeting_cannot_assert_quorum() {
        meeting.setStatus("IN_PROGRESS");
        MeetingMinutesRequest req = new MeetingMinutesRequest("minutes", null, null, (short) 1, null, null);
        assertThatThrownBy(() -> service.completeMeeting(MEETING_ID, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quorumMet cannot be supplied");
        assertThat(meeting.getStatus()).isEqualTo("IN_PROGRESS");
    }

    /** The second, free-text attendance surface — 32 meetings carry one — is closed too. */
    @Test
    void completing_a_meeting_cannot_supply_a_free_text_attendance_blob() {
        meeting.setStatus("IN_PROGRESS");
        MeetingMinutesRequest req = new MeetingMinutesRequest(
            "minutes", null, "[{\"name\":\"whoever\"}]", null, null, null);
        assertThatThrownBy(() -> service.completeMeeting(MEETING_ID, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("attendeesJson is no longer accepted");
    }

    @Test
    void completing_a_meeting_with_only_minutes_still_works() {
        meeting.setStatus("IN_PROGRESS");
        service.completeMeeting(MEETING_ID,
            new MeetingMinutesRequest("Resolved: approve CHG-42.", null, null, null, null, null));
        assertThat(meeting.getStatus()).isEqualTo("COMPLETED");
        assertThat(meeting.getMinutesEn()).isEqualTo("Resolved: approve CHG-42.");
        assertThat(meeting.getQuorumSource()).isNull();   // quorum has one origin, and it is not this
    }

    /**
     * A past quorum must not move when the roster changes. The arithmetic is persisted, so
     * appointing two more members afterwards leaves the recorded denominator alone.
     */
    @Test
    void a_later_roster_change_does_not_alter_a_recorded_quorum() {
        rosterOf(4, 0);
        record(2);
        assertThat(service.quorum(MEETING_ID).quorumMet()).isTrue();

        roster.add(seat(300L, "newcomer1", (short) 1));   // 4 → 6 voting seats
        roster.add(seat(301L, "newcomer2", (short) 1));

        QuorumView after = service.quorum(MEETING_ID);
        assertThat(after.totalVoting()).isEqualTo(4);      // the snapshot, not the live roster
        assertThat(after.requiredVoting()).isEqualTo(2);
        assertThat(after.quorumMet()).isTrue();
    }

    /** Replacing the sheet after the minutes are e-signed would rewrite a signed conclusion. */
    @Test
    void attendance_cannot_be_replaced_once_the_minutes_are_approved() {
        meeting.setStatus("MINUTES_APPROVED");
        rosterOf(4, 0);
        AttendanceRequest req = new AttendanceRequest(
            List.of(new AttendanceMark(100L, null, null, true, false, null)));
        assertThatThrownBy(() -> service.recordAttendance(MEETING_ID, req, SECRETARY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot be replaced");
    }

    /** The replacement is not silent: the ledger records what the sheet was. */
    @Test
    void replacing_the_sheet_is_audited_with_what_it_replaced() {
        rosterOf(4, 0);
        record(4);
        record(1);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit, org.mockito.Mockito.atLeast(2))
            .log(eq("MEETING_ATTENDANCE"), anyString(), anyLong(), detail.capture());
        assertThat(detail.getAllValues().get(1)).contains("replaced:").contains("4 row(s) →");
    }

    @Test
    void a_meeting_from_another_organisation_is_not_found() {
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.quorum(MEETING_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ── 4. the minutes gate ────────────────────────────────────────────────

    @Test
    void minutes_cannot_be_approved_when_quorum_was_not_met() {
        meeting.setQuorumMet((short) 0);
        meeting.setQuorumSource("COMPUTED");
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "Chair", CHAIR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not reach quorum");
        assertThat(meeting.getStatus()).isEqualTo("COMPLETED");
        verify(signatureService, never()).sign(anyString(), anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void minutes_cannot_be_approved_when_quorum_was_never_recorded() {
        meeting.setQuorumMet(null);
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "Chair", CHAIR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not reach quorum");
    }

    /**
     * <b>F2-28.</b> A meeting still awaiting approval cannot pass on an asserted flag. The 15
     * already-approved legacy meetings are untouched — invalidating e-signed governance history
     * to make a new control look clean is the opposite of an audit trail — but going forward,
     * approval requires a derived quorum.
     */
    @Test
    void minutes_cannot_be_approved_on_an_asserted_legacy_quorum() {
        meeting.setQuorumMet((short) 1);
        meeting.setQuorumSource("ASSERTED_LEGACY");
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "Chair", CHAIR))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("asserted rather than computed");
        assertThat(meeting.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void minutes_cannot_be_approved_before_the_meeting_is_completed() {
        meeting.setStatus("IN_PROGRESS");
        meeting.setQuorumMet((short) 1);
        meeting.setQuorumSource("COMPUTED");
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "Chair", CHAIR))
            .isInstanceOf(RuntimeException.class);   // IllegalTransitionException
        assertThat(meeting.getStatus()).isEqualTo("IN_PROGRESS");
    }

    /**
     * <b>F2-39 — four eyes on the last link.</b> Quorum is computed now, but its input is still a
     * human judgement about who was in the room. If the person who authored that sheet also
     * approves the minutes resting on it, the chain F2-28 describes still ends with one actor.
     */
    @Test
    void whoever_recorded_the_attendance_cannot_approve_the_minutes() {
        rosterOf(4, 0);
        record(4);
        meeting.setStatus("COMPLETED");
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "Secretary", SECRETARY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("you cannot also approve its minutes");
        assertThat(meeting.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void a_different_approver_may_sign_off_the_same_sheet() {
        rosterOf(4, 0);
        record(4);
        meeting.setStatus("COMPLETED");
        CommitteeMeeting out = service.approveMinutes(MEETING_ID, "Chair Fahad", CHAIR);
        assertThat(out.getStatus()).isEqualTo("MINUTES_APPROVED");
        assertThat(out.getMinutesApprovedBy()).isEqualTo(CHAIR);
        assertThat(out.getApproverName()).isEqualTo("Chair Fahad");
        assertThat(out.getMinutesApprovedAt()).isNotNull();
    }

    // ── 5. Track D: e-signature bound to the minutes content ───────────────

    @Test
    void approving_minutes_records_an_approve_signature_carrying_the_minutes_hash() {
        rosterOf(4, 0);
        record(4);
        meeting.setStatus("COMPLETED");
        meeting.setMinutesEn("Resolved: approve change CHG-42.");
        service.approveMinutes(MEETING_ID, "Chair", CHAIR);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(signatureService).sign(eq("committee_meeting"), eq(MEETING_ID), eq("APPROVE"),
            statement.capture(), eq(CHAIR));
        assertThat(statement.getValue())
            .contains("MTG-2026-CAB-A1B2")
            .contains("minutes sha256=")
            .matches(".*sha256=[0-9a-f]{64}.*");
    }

    @Test
    void the_signed_hash_changes_when_the_minutes_content_changes() {
        rosterOf(4, 0);
        record(4);
        meeting.setStatus("COMPLETED");
        meeting.setMinutesEn("First draft.");
        service.approveMinutes(MEETING_ID, "Chair", CHAIR);

        meeting.setStatus("COMPLETED");
        meeting.setMinutesEn("Amended after review.");
        service.approveMinutes(MEETING_ID, "Chair", CHAIR);

        ArgumentCaptor<String> both = ArgumentCaptor.forClass(String.class);
        verify(signatureService, org.mockito.Mockito.times(2))
            .sign(anyString(), anyLong(), anyString(), both.capture(), anyLong());
        assertThat(both.getAllValues().get(0)).isNotEqualTo(both.getAllValues().get(1));
    }

    @Test
    void no_signature_is_recorded_when_the_approver_is_unidentified() {
        rosterOf(4, 0);
        record(4);
        meeting.setStatus("COMPLETED");
        service.approveMinutes(MEETING_ID, "Chair", null);
        verify(signatureService, never()).sign(anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void minutes_approval_still_succeeds_when_the_signature_bean_is_absent() {
        ReflectionTestUtils.setField(service, "signatureService", null);
        rosterOf(4, 0);
        record(4);
        meeting.setStatus("COMPLETED");
        assertThatCode(() -> service.approveMinutes(MEETING_ID, "Chair", CHAIR))
            .doesNotThrowAnyException();
        assertThat(meeting.getStatus()).isEqualTo("MINUTES_APPROVED");
    }
}
