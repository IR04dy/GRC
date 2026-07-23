package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.*;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeMeetingAttendee;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
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
        return m;
    }

    @Transactional
    public CommitteeMeeting completeMeeting(Long id, MeetingMinutesRequest req) {
        CommitteeMeeting m = getMeeting(id);
        if (!Set.of("SCHEDULED", "IN_PROGRESS").contains(m.getStatus())) {
            throw new IllegalTransitionException(m.getStatus(), "COMPLETED",
                    Set.of("SCHEDULED", "IN_PROGRESS"));
        }
        m.setStatus("COMPLETED");
        m.setEndedAt(LocalDateTime.now());
        if (req != null) {
            if (req.minutesEn() != null) m.setMinutesEn(req.minutesEn());
            if (req.minutesAr() != null) m.setMinutesAr(req.minutesAr());
            if (req.attendeesJson() != null) m.setAttendeesJson(req.attendeesJson());
            if (req.quorumMet() != null) m.setQuorumMet(req.quorumMet());
            if (req.decisionsJson() != null) m.setDecisionsJson(req.decisionsJson());
            if (req.actionItemsJson() != null) m.setActionItemsJson(req.actionItemsJson());
        }
        // CH-14: when structured attendance exists, compute quorum deterministically
        // (overriding any client-supplied flag); otherwise keep the supplied value.
        recomputeQuorum(m);
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
        return m;
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
        return m;
    }

    // ── CH-14: structured attendance + deterministic quorum ──────────────────

    /** Replace the meeting's attendance sheet, then (re)compute quorum from it. */
    @Transactional
    public QuorumView recordAttendance(Long meetingId, AttendanceRequest req) {
        CommitteeMeeting m = getMeeting(meetingId);
        attendeeRepo.deleteByCommitteeMeetingId(meetingId);
        if (req != null && req.attendees() != null) {
            for (AttendeeRow r : req.attendees()) {
                if (r.memberName() == null || r.memberName().isBlank()) continue;
                CommitteeMeetingAttendee a = new CommitteeMeetingAttendee();
                a.setOrganisationId(m.getOrganisationId());
                a.setCommitteeMeetingId(meetingId);
                a.setMemberName(r.memberName().trim());
                a.setMemberRole(r.memberRole());
                a.setIsVotingMember(r.votingMember() == null || r.votingMember());
                a.setPresent(Boolean.TRUE.equals(r.present()));
                a.setCoiDeclared(Boolean.TRUE.equals(r.coiDeclared()));
                a.setCoiNote(r.coiNote());
                attendeeRepo.save(a);
            }
        }
        recomputeQuorum(m);
        return quorum(meetingId);
    }

    /** Deterministic quorum view for a meeting (present voting members vs the
     *  committee's quorum_pct). */
    @Transactional(readOnly = true)
    public QuorumView quorum(Long meetingId) {
        CommitteeMeeting m = getMeeting(meetingId);
        List<CommitteeMeetingAttendee> rows = attendeeRepo.findByCommitteeMeetingId(meetingId);
        int pct = quorumPctOf(m);
        int totalVoting   = (int) rows.stream().filter(a -> Boolean.TRUE.equals(a.getIsVotingMember())).count();
        int presentVoting = (int) rows.stream()
            .filter(a -> Boolean.TRUE.equals(a.getIsVotingMember()) && Boolean.TRUE.equals(a.getPresent())).count();
        int required = totalVoting == 0 ? 0 : Math.max(1, (int) Math.ceil(totalVoting * pct / 100.0));
        boolean met = totalVoting > 0 && presentVoting >= required;
        List<AttendeeRow> view = rows.stream()
            .map(a -> new AttendeeRow(a.getMemberName(), a.getMemberRole(), a.getIsVotingMember(),
                a.getPresent(), a.getCoiDeclared(), a.getCoiNote())).toList();
        return new QuorumView(meetingId, pct, totalVoting, presentVoting, required, met, view);
    }

    /** Recompute + persist quorum_met from structured attendance, if any rows exist. */
    private void recomputeQuorum(CommitteeMeeting m) {
        List<CommitteeMeetingAttendee> rows = attendeeRepo.findByCommitteeMeetingId(m.getId());
        if (rows.isEmpty()) return;   // no structured attendance → leave the existing flag intact
        int pct = quorumPctOf(m);
        int totalVoting   = (int) rows.stream().filter(a -> Boolean.TRUE.equals(a.getIsVotingMember())).count();
        int presentVoting = (int) rows.stream()
            .filter(a -> Boolean.TRUE.equals(a.getIsVotingMember()) && Boolean.TRUE.equals(a.getPresent())).count();
        int required = totalVoting == 0 ? 0 : Math.max(1, (int) Math.ceil(totalVoting * pct / 100.0));
        m.setQuorumMet((short) (totalVoting > 0 && presentVoting >= required ? 1 : 0));
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
