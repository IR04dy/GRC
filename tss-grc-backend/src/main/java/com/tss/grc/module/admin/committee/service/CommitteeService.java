package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.CommitteeDtos.*;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return m;
    }

    @Transactional
    public CommitteeMeeting approveMinutes(Long id, String approverName, Long approverId) {
        CommitteeMeeting m = getMeeting(id);
        if (!"COMPLETED".equals(m.getStatus())) {
            throw new IllegalTransitionException(m.getStatus(), "MINUTES_APPROVED", Set.of("COMPLETED"));
        }
        m.setStatus("MINUTES_APPROVED");
        m.setMinutesApprovedAt(LocalDateTime.now());
        m.setMinutesApprovedBy(approverId);
        m.setApproverName(approverName);
        return m;
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
