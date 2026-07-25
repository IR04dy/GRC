package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.QuorumView;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeMeetingAttendee;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
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
 * W1-1 / finding F-2, part 3: deterministic quorum and the minutes-approval gate in
 * {@link CommitteeService} — the two controls that make a committee decision binding.
 *
 * <p>Why this matters beyond the committee module: the change/policy referral gate
 * requires {@code MINUTES_APPROVED}, and minutes cannot be approved unless the meeting
 * was quorate. So quorum arithmetic is, transitively, the precondition for authorising
 * a change or publishing a policy — and it had no test at all.
 *
 * <p>Also pins the Track D e-signature on approved minutes, including that the signed
 * statement carries a SHA-256 of the minutes content (tamper-evidence: the signature is
 * bound to what was actually approved) and that the optional signature bean being
 * absent must not break minutes approval.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeQuorumMinutesTest {

    private static final Long ORG = 1L;
    private static final Long MEETING_ID = 55L;
    private static final Long COMMITTEE_ID = 7L;

    @Mock CommitteeRepository                 repo;
    @Mock CommitteeMeetingRepository          meetingRepo;
    @Mock CommitteeMeetingAttendeeRepository  attendeeRepo;
    @Mock SignatureService                    signatureService;

    @InjectMocks CommitteeService service;

    private CommitteeMeeting meeting;
    private final List<CommitteeMeetingAttendee> sheet = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        // signatureService is @Autowired(required = false) FIELD injection; Mockito uses
        // the @RequiredArgsConstructor for the three final repositories and then skips
        // property injection, so wire it explicitly (see the absent-bean test below).
        ReflectionTestUtils.setField(service, "signatureService", signatureService);

        meeting = new CommitteeMeeting();
        meeting.setId(MEETING_ID);
        meeting.setOrganisationId(ORG);
        meeting.setCommitteeId(COMMITTEE_ID);
        meeting.setMeetingCode("MTG-2026-CAB-A1B2");
        meeting.setStatus("COMPLETED");
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(meeting));
        when(attendeeRepo.findByCommitteeMeetingId(MEETING_ID)).thenReturn(sheet);
        withQuorumPct(50);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        sheet.clear();
    }

    private void withQuorumPct(Integer pct) {
        Committee c = new Committee();
        c.setId(COMMITTEE_ID);
        c.setOrganisationId(ORG);
        c.setQuorumPct(pct);
        when(repo.findById(COMMITTEE_ID)).thenReturn(Optional.of(c));
    }

    /** Add attendees: n voting members of whom {@code present} attended. */
    private void attendance(int voting, int present, int nonVotingPresent) {
        for (int i = 0; i < voting; i++) sheet.add(row("voter" + i, true, i < present));
        for (int i = 0; i < nonVotingPresent; i++) sheet.add(row("observer" + i, false, true));
    }

    private CommitteeMeetingAttendee row(String name, boolean voting, boolean present) {
        CommitteeMeetingAttendee a = new CommitteeMeetingAttendee();
        a.setOrganisationId(ORG);
        a.setCommitteeMeetingId(MEETING_ID);
        a.setMemberName(name);
        a.setIsVotingMember(voting);
        a.setPresent(present);
        a.setCoiDeclared(false);
        return a;
    }

    // ── quorum arithmetic ───────────────────────────────────────────────────

    @Test
    void half_of_an_even_board_meets_a_fifty_percent_quorum() {
        attendance(4, 2, 0);
        QuorumView q = service.quorum(MEETING_ID);
        assertThat(q.totalVoting()).isEqualTo(4);
        assertThat(q.presentVoting()).isEqualTo(2);
        assertThat(q.requiredVoting()).isEqualTo(2);
        assertThat(q.quorumMet()).isTrue();
    }

    @Test
    void one_short_of_the_requirement_is_not_quorate() {
        attendance(4, 1, 0);
        assertThat(service.quorum(MEETING_ID).quorumMet()).isFalse();
    }

    @Test
    void an_odd_board_rounds_the_requirement_up() {
        // 3 voting members at 50% => ceil(1.5) = 2 required, so 1 is not enough.
        attendance(3, 1, 0);
        assertThat(service.quorum(MEETING_ID).requiredVoting()).isEqualTo(2);
        assertThat(service.quorum(MEETING_ID).quorumMet()).isFalse();
        sheet.clear();
        attendance(3, 2, 0);
        assertThat(service.quorum(MEETING_ID).quorumMet()).isTrue();
    }

    @Test
    void non_voting_attendees_never_count_toward_quorum() {
        // A room full of observers cannot make a meeting quorate.
        attendance(4, 1, 10);
        QuorumView q = service.quorum(MEETING_ID);
        assertThat(q.totalVoting()).isEqualTo(4);
        assertThat(q.presentVoting()).isEqualTo(1);
        assertThat(q.quorumMet()).isFalse();
    }

    @Test
    void a_sheet_with_no_voting_members_is_never_quorate() {
        attendance(0, 0, 5);
        QuorumView q = service.quorum(MEETING_ID);
        assertThat(q.totalVoting()).isZero();
        assertThat(q.quorumMet()).isFalse();
    }

    @Test
    void a_committee_without_a_configured_percentage_defaults_to_fifty() {
        withQuorumPct(null);
        attendance(4, 2, 0);
        QuorumView q = service.quorum(MEETING_ID);
        assertThat(q.quorumPct()).isEqualTo(50);
        assertThat(q.quorumMet()).isTrue();
    }

    @Test
    void a_hundred_percent_quorum_requires_every_voting_member() {
        withQuorumPct(100);
        attendance(4, 3, 0);
        assertThat(service.quorum(MEETING_ID).quorumMet()).isFalse();
        sheet.clear();
        attendance(4, 4, 0);
        assertThat(service.quorum(MEETING_ID).quorumMet()).isTrue();
    }

    @Test
    void a_tiny_percentage_still_requires_at_least_one_member_present() {
        // max(1, ceil(...)) — a 1% quorum must not evaluate to "nobody needed".
        withQuorumPct(1);
        attendance(4, 0, 0);
        QuorumView q = service.quorum(MEETING_ID);
        assertThat(q.requiredVoting()).isEqualTo(1);
        assertThat(q.quorumMet()).isFalse();
    }

    @Test
    void the_quorum_view_reports_the_attendance_sheet_it_judged() {
        attendance(2, 1, 1);
        QuorumView q = service.quorum(MEETING_ID);
        assertThat(q.attendees()).hasSize(3);
        assertThat(q.meetingId()).isEqualTo(MEETING_ID);
    }

    @Test
    void a_meeting_from_another_organisation_is_not_found() {
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.quorum(MEETING_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ── the minutes gate: quorum is a precondition for binding decisions ────

    @Test
    void minutes_cannot_be_approved_when_quorum_was_not_met() {
        meeting.setQuorumMet((short) 0);
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "Chair", 9L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not reach quorum");
        assertThat(meeting.getStatus()).isEqualTo("COMPLETED");   // unchanged
        verify(signatureService, never()).sign(anyString(), anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void minutes_cannot_be_approved_when_quorum_was_never_recorded() {
        meeting.setQuorumMet(null);
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "Chair", 9L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not reach quorum");
    }

    @Test
    void minutes_cannot_be_approved_before_the_meeting_is_completed() {
        meeting.setStatus("IN_PROGRESS");
        meeting.setQuorumMet((short) 1);
        assertThatThrownBy(() -> service.approveMinutes(MEETING_ID, "Chair", 9L))
            .isInstanceOf(RuntimeException.class);   // IllegalTransitionException
        assertThat(meeting.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void a_quorate_completed_meeting_can_have_its_minutes_approved() {
        meeting.setQuorumMet((short) 1);
        CommitteeMeeting out = service.approveMinutes(MEETING_ID, "Chair Fahad", 9L);
        assertThat(out.getStatus()).isEqualTo("MINUTES_APPROVED");
        assertThat(out.getMinutesApprovedAt()).isNotNull();
        assertThat(out.getMinutesApprovedBy()).isEqualTo(9L);
        assertThat(out.getApproverName()).isEqualTo("Chair Fahad");
    }

    // ── Track D: e-signature bound to the minutes content ───────────────────

    @Test
    void approving_minutes_records_an_approve_signature_carrying_the_minutes_hash() {
        meeting.setQuorumMet((short) 1);
        meeting.setMinutesEn("Resolved: approve change CHG-42.");
        service.approveMinutes(MEETING_ID, "Chair", 9L);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(signatureService).sign(eq("committee_meeting"), eq(MEETING_ID), eq("APPROVE"),
            statement.capture(), eq(9L));
        assertThat(statement.getValue())
            .contains("MTG-2026-CAB-A1B2")
            .contains("minutes sha256=")
            .matches(".*sha256=[0-9a-f]{64}.*");
    }

    @Test
    void the_signed_hash_changes_when_the_minutes_content_changes() {
        meeting.setQuorumMet((short) 1);
        meeting.setMinutesEn("First draft.");
        service.approveMinutes(MEETING_ID, "Chair", 9L);
        ArgumentCaptor<String> first = ArgumentCaptor.forClass(String.class);
        verify(signatureService).sign(anyString(), anyLong(), anyString(), first.capture(), anyLong());

        // a different meeting object with different minutes must hash differently
        meeting.setStatus("COMPLETED");
        meeting.setMinutesEn("Amended after review.");
        service.approveMinutes(MEETING_ID, "Chair", 9L);
        ArgumentCaptor<String> both = ArgumentCaptor.forClass(String.class);
        verify(signatureService, org.mockito.Mockito.times(2))
            .sign(anyString(), anyLong(), anyString(), both.capture(), anyLong());

        assertThat(both.getAllValues().get(0)).isNotEqualTo(both.getAllValues().get(1));
    }

    @Test
    void no_signature_is_recorded_when_the_approver_is_unidentified() {
        meeting.setQuorumMet((short) 1);
        service.approveMinutes(MEETING_ID, "Chair", null);
        verify(signatureService, never()).sign(anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void minutes_approval_still_succeeds_when_the_signature_bean_is_absent() {
        ReflectionTestUtils.setField(service, "signatureService", null);
        meeting.setQuorumMet((short) 1);
        assertThatCode(() -> service.approveMinutes(MEETING_ID, "Chair", 9L))
            .doesNotThrowAnyException();
        assertThat(meeting.getStatus()).isEqualTo("MINUTES_APPROVED");
    }
}
