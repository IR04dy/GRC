package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.*;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Combined service for {@link Committee} + {@link CommitteeMeeting}.
 *
 * <p>Committee lifecycle:  ACTIVE → INACTIVE → DISSOLVED.
 * <p>Meeting lifecycle:    SCHEDULED → IN_PROGRESS → COMPLETED → MINUTES_APPROVED,
 *                          or SCHEDULED → CANCELLED.
 */
@Service
@RequiredArgsConstructor
public class CommitteeService {

    private static final Logger log = LoggerFactory.getLogger(CommitteeService.class);

    private final CommitteeRepository repo;
    private final CommitteeMeetingRepository meetingRepo;
    private final CommitteeMeetingAttendeeRepository attendeeRepo;   // CH-14
    /** F2-28: the roster is the quorum denominator and the attendance eligibility list. */
    private final CommitteeMemberTermRepository memberTermRepo;
    private final AuditLogService audit;

    /** Track D (S4): reuse the platform e-signature subsystem to record a
     *  non-repudiable sign-off when committee minutes are approved. Optional so
     *  directly-constructed unit tests are unaffected (sign-off then no-ops). */
    @Autowired(required = false)
    private SignatureService signatureService;

    /**
     * Returns {@code true} when the underlying SQL error looks like a
     * missing-table error — usually because the committees schema
     * hasn't been bootstrapped in this DB (dev profile, fresh tenant
     * before {@code setup_committees_db.py} runs). We log once and
     * return an empty page so the UI renders an empty register
     * instead of "unable to load data".
     */
    private static boolean isMissingObjectError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String low = msg.toLowerCase();
                if (low.contains("invalid object name")
                    || low.contains("does not exist")
                    || low.contains("not found")) return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    // ─── Committee reads/writes ───────────────────────────────────────

    // noRollbackFor: when the underlying table is missing, the query
    // throws and Spring would mark the tx rollback-only → commit throws
    // UnexpectedRollbackException → 500. The flag keeps the tx committable
    // so the catch's empty page reaches the client.
    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public Page<Committee> listCommittees(String status, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        try {
            return status == null
                    ? repo.findByOrganisationIdOrderByNameEnAsc(org, pageable)
                    : repo.findByOrganisationIdAndStatusOrderByNameEnAsc(org, status, pageable);
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) {
                log.warn("[committees] admin.committees table missing — run setup_committees_db.py; returning empty page");
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Committee getCommittee(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("Committee " + id));
    }

    @Transactional
    public Committee createCommittee(CommitteeCreateRequest req) {
        Committee c = new Committee();
        c.setOrganisationId(TenantContext.getCurrentTenant());
        c.setCode(req.code());
        c.setExternalId("COM_" + UUID.randomUUID().toString().substring(0, 12));
        c.setNameEn(req.nameEn());
        c.setNameAr(req.nameAr());
        c.setDescriptionEn(req.descriptionEn());
        c.setDescriptionAr(req.descriptionAr());
        c.setCommitteeType(req.committeeType());
        c.setChairpersonName(req.chairpersonName());
        c.setChairpersonRole(req.chairpersonRole());
        c.setSecretaryName(req.secretaryName());
        c.setMembersJson(req.membersJson());
        c.setCharterEn(req.charterEn());
        c.setCharterAr(req.charterAr());
        if (req.meetingCadence() != null) c.setMeetingCadence(req.meetingCadence());
        c.setQuorumPct(req.quorumPct());
        c.setStatus("ACTIVE");
        return repo.save(c);
    }

    @Transactional
    public Committee updateCommittee(Long id, CommitteeUpdateRequest req) {
        Committee c = getCommittee(id);
        if ("DISSOLVED".equals(c.getStatus())) {
            throw new IllegalTransitionException(c.getStatus(), "EDIT", Set.of("ACTIVE", "INACTIVE"));
        }
        if (req.nameEn() != null) c.setNameEn(req.nameEn());
        if (req.nameAr() != null) c.setNameAr(req.nameAr());
        if (req.descriptionEn() != null) c.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null) c.setDescriptionAr(req.descriptionAr());
        if (req.chairpersonName() != null) c.setChairpersonName(req.chairpersonName());
        if (req.chairpersonRole() != null) c.setChairpersonRole(req.chairpersonRole());
        if (req.secretaryName() != null) c.setSecretaryName(req.secretaryName());
        if (req.membersJson() != null) c.setMembersJson(req.membersJson());
        if (req.charterEn() != null) c.setCharterEn(req.charterEn());
        if (req.charterAr() != null) c.setCharterAr(req.charterAr());
        if (req.meetingCadence() != null) c.setMeetingCadence(req.meetingCadence());
        if (req.quorumPct() != null) c.setQuorumPct(req.quorumPct());
        return c;
    }

    @Transactional
    public Committee dissolveCommittee(Long id, CommitteeDissolveRequest req) {
        Committee c = getCommittee(id);
        if ("DISSOLVED".equals(c.getStatus())) {
            throw new IllegalTransitionException(c.getStatus(), "DISSOLVED", Set.of("ACTIVE", "INACTIVE"));
        }
        c.setStatus("DISSOLVED");
        c.setDissolvedAt(LocalDateTime.now());
        c.setDissolvedReason(req.reason());
        return c;
    }

    // ─── Meeting reads/writes ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CommitteeMeeting> listMeetings(String status, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        try {
            return status == null
                    ? meetingRepo.findByOrganisationIdOrderByScheduledAtDesc(org, pageable)
                    : meetingRepo.findByOrganisationIdAndStatusOrderByScheduledAtDesc(org, status, pageable);
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) {
                log.warn("[committees] admin.committee_meetings table missing — run setup_committees_db.py; returning empty page");
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<CommitteeMeeting> meetingsForCommittee(Long committeeId) {
        return meetingRepo.findByOrganisationIdAndCommitteeIdOrderByScheduledAtDesc(
                TenantContext.getCurrentTenant(), committeeId);
    }

    @Transactional(readOnly = true)
    public CommitteeMeeting getMeeting(Long id) {
        return meetingRepo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("CommitteeMeeting " + id));
    }

    @Transactional
    public CommitteeMeeting createMeeting(MeetingCreateRequest req) {
        Committee parent = getCommittee(req.committeeId());
        CommitteeMeeting m = new CommitteeMeeting();
        m.setOrganisationId(TenantContext.getCurrentTenant());
        m.setCommitteeId(parent.getId());
        m.setCommitteeCode(parent.getCode());
        m.setCommitteeNameEn(parent.getNameEn());
        m.setMeetingCode(generateMeetingCode(parent.getCode()));
        m.setExternalId("MTG_" + UUID.randomUUID().toString().substring(0, 12));
        m.setTitleEn(req.titleEn());
        m.setTitleAr(req.titleAr());
        m.setScheduledAt(req.scheduledAt());
        m.setLocationOrLink(req.locationOrLink());
        m.setAgendaJson(req.agendaJson());
        m.setChairName(req.chairName() != null ? req.chairName() : parent.getChairpersonName());
        m.setSecretaryName(req.secretaryName() != null ? req.secretaryName() : parent.getSecretaryName());
        m.setStatus("SCHEDULED");
        return meetingRepo.save(m);
    }

    @Transactional
    public CommitteeMeeting updateMeeting(Long id, MeetingUpdateRequest req) {
        CommitteeMeeting m = getMeeting(id);
        if (Set.of("MINUTES_APPROVED", "CANCELLED").contains(m.getStatus())) {
            throw new IllegalTransitionException(m.getStatus(), "EDIT",
                    Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED"));
        }
        if (req.titleEn() != null) m.setTitleEn(req.titleEn());
        if (req.titleAr() != null) m.setTitleAr(req.titleAr());
        if (req.scheduledAt() != null) m.setScheduledAt(req.scheduledAt());
        if (req.locationOrLink() != null) m.setLocationOrLink(req.locationOrLink());
        if (req.agendaJson() != null) m.setAgendaJson(req.agendaJson());
        if (req.chairName() != null) m.setChairName(req.chairName());
        if (req.secretaryName() != null) m.setSecretaryName(req.secretaryName());
        return m;
    }

    @Transactional
    public CommitteeMeeting startMeeting(Long id) {
        CommitteeMeeting m = getMeeting(id);
        if (!"SCHEDULED".equals(m.getStatus())) {
            throw new IllegalTransitionException(m.getStatus(), "IN_PROGRESS", Set.of("SCHEDULED"));
        }
        m.setStatus("IN_PROGRESS");
        m.setStartedAt(LocalDateTime.now());
        audit.log("MEETING_START", "CommitteeMeeting", m.getId(),
            "Meeting " + label(m) + " opened");
        return m;
    }

    @Transactional
    public CommitteeMeeting completeMeeting(Long id, MeetingMinutesRequest req) {
        CommitteeMeeting m = getMeeting(id);
        if (!Set.of("SCHEDULED", "IN_PROGRESS").contains(m.getStatus())) {
            throw new IllegalTransitionException(m.getStatus(), "COMPLETED",
                    Set.of("SCHEDULED", "IN_PROGRESS"));
        }
        // F2-28 / DF-5: quorum is DERIVED, never asserted. Both of these fields are refused
        // loudly rather than ignored quietly — a security-relevant value that is silently
        // dropped teaches the caller nothing, and every quorum fact in the database before
        // V319 (17 meetings, 15 e-signed) arrived through `quorumMet` on this very call.
        if (req != null && req.quorumMet() != null)
            throw new IllegalArgumentException(
                "quorumMet cannot be supplied. Quorum is computed from the attendance sheet "
              + "against the committee's active voting roster — record attendance at "
              + "POST /meetings/" + id + "/attendance and it will be derived.");
        if (req != null && req.attendeesJson() != null)
            throw new IllegalArgumentException(
                "attendeesJson is no longer accepted. It was a second, free-text attendance "
              + "surface alongside the structured sheet; record attendance at "
              + "POST /meetings/" + id + "/attendance instead.");

        m.setStatus("COMPLETED");
        m.setEndedAt(LocalDateTime.now());
        if (req != null) {
            if (req.minutesEn() != null) m.setMinutesEn(req.minutesEn());
            if (req.minutesAr() != null) m.setMinutesAr(req.minutesAr());
            if (req.decisionsJson() != null) m.setDecisionsJson(req.decisionsJson());
            if (req.actionItemsJson() != null) m.setActionItemsJson(req.actionItemsJson());
        }
        // No quorum recomputation here. Quorum has exactly one origin — recordAttendance —
        // because the old early-return-when-no-rows behaviour on this path is what let an
        // asserted flag become a governance fact.
        audit.log("MEETING_COMPLETE", "CommitteeMeeting", m.getId(),
            "Meeting " + label(m) + " closed and minutes recorded ("
                + (m.getMinutesEn() == null ? 0 : m.getMinutesEn().length()) + " chars EN, "
                + (m.getMinutesAr() == null ? 0 : m.getMinutesAr().length()) + " chars AR)");
        return m;
    }

    @Transactional
    public CommitteeMeeting approveMinutes(Long id, String approverName, Long approverId) {
        CommitteeMeeting m = getMeeting(id);
        if (!"COMPLETED".equals(m.getStatus())) {
            throw new IllegalTransitionException(m.getStatus(), "MINUTES_APPROVED", Set.of("COMPLETED"));
        }
        // CH-14 (governance gate): minutes cannot be approved for a meeting that did
        // not reach quorum — a quorate meeting is a precondition for binding decisions.
        if (m.getQuorumMet() == null || m.getQuorumMet() != 1)
            throw new IllegalStateException(
                "Cannot approve minutes: the meeting did not reach quorum. Record attendance "
                    + "and ensure enough voting members were present.");

        // F2-28 / DF-5: the flag must have been DERIVED, not asserted. Pre-V319 meetings carry
        // ASSERTED_LEGACY and are deliberately left as they are — 15 are already approved and
        // e-signed, and retroactively invalidating approved governance history to make a new
        // control look clean is the opposite of an audit trail. But a meeting still awaiting
        // approval cannot pass on an assertion: record the attendance and it becomes COMPUTED.
        if (!"COMPUTED".equals(m.getQuorumSource()))
            throw new IllegalStateException(
                "Cannot approve minutes: this meeting's quorum was "
              + (m.getQuorumSource() == null ? "never established" : "asserted rather than computed")
              + ". Record the attendance sheet so quorum is derived from the committee's active "
              + "voting roster, then approve.");

        // F2-39: whoever authored the attendance sheet cannot be the one who approves the
        // minutes that rest on it. Quorum is now computed rather than typed, but the *input* is
        // still a human judgement about who was in the room — so the last link in F2-28's chain
        // (attendance → quorum → approved minutes → an approved change) needs a second pair of
        // eyes, the same 409-shaped gate the platform uses for allocation approval and
        // break-glass self-review.
        if (approverId != null && approverId.equals(m.getAttendanceRecordedBy()))
            throw new IllegalStateException(
                "You recorded this meeting's attendance, so you cannot also approve its minutes. "
              + "Quorum rests on that sheet — another authorised approver must sign it off.");
        m.setStatus("MINUTES_APPROVED");
        m.setMinutesApprovedAt(LocalDateTime.now());
        m.setMinutesApprovedBy(approverId);
        m.setApproverName(approverName);
        // Track D (S4): record the approver's e-signature on the approved minutes
        // via the platform signature subsystem — a non-repudiable sign-off whose
        // statement carries a content hash of the minutes (tamper-evidence). The
        // signed entity is queryable at GET /api/v1/signatures/committee_meeting/{id}.
        if (signatureService != null && approverId != null) {
            String statement = "Approved the minutes of committee meeting "
                + (m.getMeetingCode() != null ? m.getMeetingCode() : ("#" + m.getId()))
                + " — minutes sha256=" + minutesDigest(m);
            signatureService.sign("committee_meeting", m.getId(), "APPROVE", statement, approverId);
        }
        // W1-5 / F2-90 — this line did not exist, and it is the one that matters most.
        //
        // Approving the minutes is the act that makes a committee decision binding: four gates
        // guard it (COMPLETED, quorate, quorum COMPUTED not asserted, and F2-39's separation from
        // whoever recorded attendance), it stamps the approver, and Track D asserts CAB_APPROVED on
        // the result. None of it reached the audit ledger. Measured across the whole of
        // platform.audit_log: MEETING_ATTENDANCE was the ONLY meeting action ever written, so an
        // auditor reading the tamper-evident chain (F2-31) could see the attendance sheet being
        // replaced and not the approval resting on it.
        //
        // The e-signature is not a substitute. It is a different subsystem behind a different
        // permission (SIGNATURE:READ vs AUDIT_LOG:READ), it records a signature rather than an
        // event, and it carries no actor role, correlation id or before/after status. "Signed" and
        // "recorded in the ledger" are two claims and the platform was only making one.
        //
        // No unit test caught it: 32 tests in CommitteeQuorumMinutesTest mock AuditLogService and
        // none asserts a call here — the F2-18 blind spot, found on the golden path's last step.
        audit.log("MINUTES_APPROVE", "CommitteeMeeting", m.getId(),
            "Minutes of " + label(m) + " approved by "
                + (approverName == null ? "user#" + approverId : approverName)
                + " · quorum " + m.getQuorumPresent() + "/" + m.getQuorumDenominator()
                + " required " + m.getQuorumRequired()
                + " (" + m.getQuorumSource() + ") · minutes sha256=" + minutesDigest(m));
        return m;
    }

    /** Human-readable meeting label for ledger messages: code when it has one, id otherwise. */
    private static String label(CommitteeMeeting m) {
        return m.getMeetingCode() != null ? m.getMeetingCode() : ("#" + m.getId());
    }

    /** SHA-256 of the meeting's minutes content, binding the sign-off to what was
     *  approved (empty-safe). */
    private static String minutesDigest(CommitteeMeeting m) {
        String content = (m.getMinutesEn() == null ? "" : m.getMinutesEn())
            + "|" + (m.getMinutesAr() == null ? "" : m.getMinutesAr());
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    @Transactional
    public CommitteeMeeting cancelMeeting(Long id, MeetingCancelRequest req) {
        CommitteeMeeting m = getMeeting(id);
        if (Set.of("MINUTES_APPROVED", "CANCELLED").contains(m.getStatus())) {
            throw new IllegalTransitionException(m.getStatus(), "CANCELLED",
                    Set.of("SCHEDULED", "IN_PROGRESS"));
        }
        m.setStatus("CANCELLED");
        m.setCancelReason(req.reason());
        // Cancelling a CAB meeting is as governance-material as holding one: it is how a change
        // waiting on a decision stops getting one. Same F2-90 gap.
        audit.log("MEETING_CANCEL", "CommitteeMeeting", m.getId(),
            "Meeting " + label(m) + " cancelled · reason: " + req.reason());
        return m;
    }

    // ── CH-14 structured attendance · F2-28/DF-5 seat-bound quorum ───────────

    /**
     * Replace the meeting's attendance sheet from roster seats, then compute and persist quorum.
     *
     * <p><b>F2-28 / DF-5 — what was wrong.</b> This method rebuilt the sheet from free-text
     * {@code memberName} plus a <em>client-set</em> {@code votingMember}, and {@code quorum()}
     * then derived both the numerator and the denominator from those same rows. Invent six
     * voting attendees, mark four present, and a 67% committee is quorate. Or skip attendance
     * entirely and pass {@code quorumMet = 1} to {@code complete}, because the old
     * {@code recomputeQuorum} returned early when no rows existed. Then {@code approveMinutes}
     * gates only on that flag, the minutes are SHA-256 e-signed, and Track D asserts
     * {@code CAB_APPROVED} on quorate {@code MINUTES_APPROVED} minutes — a complete chain from
     * typed-in attendance to an approved change.
     *
     * <p>Measured before the fix: {@code committee_meeting_attendee} held <b>0 rows</b>. The
     * deterministic path CH-14 built had never executed on real data, so <em>every</em> quorum
     * fact in the database was caller-supplied.
     *
     * <p><b>Three changes.</b> (1) A mark names a <b>roster seat</b>, and voting eligibility is
     * copied from that seat, never from the body. (2) The denominator is the committee's
     * <b>active voting roster</b>, not the sheet — an absent member still counts, which is what
     * quorum means. (3) The arithmetic is <b>persisted</b>, so a later roster change cannot move
     * a past meeting's quorum.
     *
     * <p>Refused once the minutes are approved: the sheet is an input to a flag that has already
     * been e-signed, and rewriting it afterwards would change the signed conclusion while the
     * signature — which hashes only the minutes text — still verified.
     */
    @Transactional
    public QuorumView recordAttendance(Long meetingId, AttendanceRequest req, Long actorUserId) {
        CommitteeMeeting m = getMeeting(meetingId);
        if (!Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED").contains(m.getStatus()))
            throw new IllegalStateException(
                "Attendance cannot be recorded for a meeting that is " + m.getStatus()
              + ". Once the minutes are approved the attendance sheet is part of an e-signed "
              + "record and cannot be replaced.");

        Long org = m.getOrganisationId();
        // The roster IS the eligibility list. A mark that does not resolve to an active seat of
        // THIS committee is refused rather than silently created as a free-text row.
        Map<Long, CommitteeMemberTerm> roster = m.getCommitteeId() == null
            ? Map.of()
            : memberTermRepo.findActiveByCommittee(org, m.getCommitteeId()).stream()
                .collect(java.util.stream.Collectors.toMap(CommitteeMemberTerm::getId, t -> t,
                    (a, b) -> a, java.util.LinkedHashMap::new));

        List<CommitteeMeetingAttendee> previous = attendeeRepo.findByCommitteeMeetingId(meetingId);
        List<CommitteeMeetingAttendee> incoming = new java.util.ArrayList<>();
        Set<Long> seenSeats = new java.util.HashSet<>();

        for (AttendanceMark mk : req == null || req.marks() == null ? List.<AttendanceMark>of() : req.marks()) {
            CommitteeMeetingAttendee a = new CommitteeMeetingAttendee();
            a.setOrganisationId(org);
            a.setCommitteeMeetingId(meetingId);

            if (mk.memberTermId() != null) {
                CommitteeMemberTerm seat = roster.get(mk.memberTermId());
                if (seat == null)
                    throw new IllegalArgumentException(
                        "Seat " + mk.memberTermId() + " is not on this committee's active roster, "
                      + "so attendance cannot be recorded against it. Attendance is a selection "
                      + "from the roster; a non-member who attended is recorded as a guest.");
                if (!seenSeats.add(seat.getId()))
                    throw new IllegalArgumentException(
                        seat.getMemberName() + " appears twice in this attendance sheet. One mark "
                      + "per seat — a duplicate would count them twice toward quorum.");
                a.setMemberTermId(seat.getId());
                a.setIsGuest(false);
                // Copied from the roster, not the request. This is the line the finding is about.
                a.setMemberName(seat.getMemberName());
                a.setMemberRole(seat.getRole());
                a.setIsVotingMember(seat.getIsVotingMember() != null && seat.getIsVotingMember() == 1);
            } else {
                if (mk.guestName() == null || mk.guestName().isBlank())
                    throw new IllegalArgumentException(
                        "A mark must name either a roster seat (memberTermId) or a guest "
                      + "(guestName). A nameless free-text attendee is what F2-28 removed.");
                a.setMemberTermId(null);
                a.setIsGuest(true);
                a.setMemberName(mk.guestName().trim());
                a.setMemberRole(mk.guestRole());
                a.setIsVotingMember(false);   // a guest never counts, in either direction
            }
            a.setPresent(Boolean.TRUE.equals(mk.present()));
            a.setCoiDeclared(Boolean.TRUE.equals(mk.coiDeclared()));
            a.setCoiNote(mk.coiNote());
            incoming.add(a);
        }

        attendeeRepo.deleteByCommitteeMeetingId(meetingId);
        attendeeRepo.flush();          // the unique (meeting, seat) index must not see both sheets
        for (CommitteeMeetingAttendee a : incoming) attendeeRepo.save(a);

        m.setAttendanceRecordedBy(actorUserId);
        m.setAttendanceRecordedAt(LocalDateTime.now());
        computeQuorum(m, incoming);

        // The register's item 6: replacement must not be silent. The sheet is deleted and
        // rebuilt, so the ledger is the only record of what it replaced.
        audit.log("MEETING_ATTENDANCE", "CommitteeMeeting", meetingId,
            "Attendance sheet replaced · " + previous.size() + " row(s) → " + incoming.size()
                + " · present " + m.getQuorumPresent() + "/" + m.getQuorumDenominator()
                + " (required " + m.getQuorumRequired() + ") · quorum "
                + (m.getQuorumMet() != null && m.getQuorumMet() == 1 ? "MET" : "NOT MET")
                + (previous.isEmpty() ? "" : " · replaced: " + summarise(previous)));

        return quorum(meetingId);
    }

    /**
     * The quorum arithmetic, read back from what was persisted.
     *
     * <p>Deliberately <b>not</b> recomputed on read. If the denominator came from the live
     * roster every time, appointing a member next year would silently change last year's
     * quorum — so {@link #computeQuorum} persists the numbers and this reports them. When a
     * meeting has no computed quorum yet (or carries a pre-V319 {@code ASSERTED_LEGACY} flag),
     * the roster figures are shown for context and {@code quorumSource} says where the flag
     * came from.
     */
    @Transactional(readOnly = true)
    public QuorumView quorum(Long meetingId) {
        CommitteeMeeting m = getMeeting(meetingId);
        List<CommitteeMeetingAttendee> rows = attendeeRepo.findByCommitteeMeetingId(meetingId);
        List<CommitteeMemberTerm> roster = rosterOf(m);
        int pct = quorumPctOf(m);

        int rosterVoting = (int) roster.stream()
            .filter(t -> t.getIsVotingMember() != null && t.getIsVotingMember() == 1).count();
        int totalVoting = m.getQuorumDenominator() != null ? m.getQuorumDenominator() : rosterVoting;
        int presentVoting = m.getQuorumPresent() != null ? m.getQuorumPresent()
            : (int) rows.stream().filter(a -> Boolean.TRUE.equals(a.getIsVotingMember())
                                           && Boolean.TRUE.equals(a.getPresent())).count();
        int required = m.getQuorumRequired() != null ? m.getQuorumRequired() : requiredFor(totalVoting, pct);

        Set<Long> markedSeats = rows.stream().map(CommitteeMeetingAttendee::getMemberTermId)
            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        int unrecorded = (int) roster.stream()
            .filter(t -> t.getIsVotingMember() != null && t.getIsVotingMember() == 1)
            .filter(t -> !markedSeats.contains(t.getId())).count();
        int unlinked = (int) roster.stream()
            .filter(t -> t.getIsVotingMember() != null && t.getIsVotingMember() == 1)
            .filter(t -> t.getMemberUserId() == null).count();

        List<AttendeeRow> view = rows.stream()
            .map(a -> new AttendeeRow(a.getMemberTermId(), a.getMemberName(), a.getMemberRole(),
                a.getIsVotingMember(), a.getPresent(), a.getCoiDeclared(), a.getCoiNote(),
                a.getIsGuest())).toList();

        return new QuorumView(meetingId, pct, totalVoting, presentVoting, required,
            m.getQuorumMet() != null && m.getQuorumMet() == 1,
            m.getQuorumSource(), m.getQuorumComputedAt(), unrecorded, unlinked, view);
    }

    /**
     * Compute and persist quorum from seat-bound attendance against the roster denominator.
     *
     * <p>Called only from {@link #recordAttendance}. The old {@code recomputeQuorum} ran on
     * {@code complete} as well and returned early when no rows existed — which is precisely
     * how a caller-supplied {@code quorumMet} survived to become 17 meetings' quorum fact.
     * Quorum now has exactly one origin: recorded attendance.
     */
    private void computeQuorum(CommitteeMeeting m, List<CommitteeMeetingAttendee> rows) {
        int pct = quorumPctOf(m);
        int totalVoting = (int) rosterOf(m).stream()
            .filter(t -> t.getIsVotingMember() != null && t.getIsVotingMember() == 1).count();
        int presentVoting = (int) rows.stream()
            .filter(a -> Boolean.TRUE.equals(a.getIsVotingMember())
                      && Boolean.TRUE.equals(a.getPresent())).count();
        int required = requiredFor(totalVoting, pct);

        m.setQuorumDenominator(totalVoting);
        m.setQuorumPresent(presentVoting);
        m.setQuorumRequired(required);
        m.setQuorumComputedAt(LocalDateTime.now());
        m.setQuorumSource("COMPUTED");
        // A committee with no active voting seats cannot be quorate. Previously this read as
        // "no rows → keep the flag", which let an empty roster inherit an asserted 1.
        m.setQuorumMet((short) (totalVoting > 0 && presentVoting >= required ? 1 : 0));
    }

    private List<CommitteeMemberTerm> rosterOf(CommitteeMeeting m) {
        return m.getCommitteeId() == null ? List.of()
            : memberTermRepo.findActiveByCommittee(m.getOrganisationId(), m.getCommitteeId());
    }

    private static int requiredFor(int totalVoting, int pct) {
        return totalVoting == 0 ? 0 : Math.max(1, (int) Math.ceil(totalVoting * pct / 100.0));
    }

    /** Compact description of a replaced sheet, for the ledger. */
    private static String summarise(List<CommitteeMeetingAttendee> rows) {
        return rows.stream()
            .map(a -> a.getMemberName() + (Boolean.TRUE.equals(a.getPresent()) ? "=present" : "=absent"))
            .limit(20)
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private int quorumPctOf(CommitteeMeeting m) {
        Integer pct = m.getCommitteeId() == null ? null
            : repo.findById(m.getCommitteeId()).map(Committee::getQuorumPct).orElse(null);
        return pct != null ? pct : 50;   // sensible default when the committee has no configured pct
    }

    private String generateMeetingCode(String committeeCode) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String code = "MTG-" + Year.now().getValue() + "-" + committeeCode + "-" + suffix;
        Long org = TenantContext.getCurrentTenant();
        if (meetingRepo.findByOrganisationIdAndMeetingCode(org, code).isPresent()) {
            suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
            code = "MTG-" + Year.now().getValue() + "-" + committeeCode + "-" + suffix;
        }
        return code;
    }
}
