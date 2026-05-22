package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolution lifecycle service. A resolution is created when a motion
 * is carried at a committee meeting — once the meeting closes, the
 * resolution becomes an immutable record.
 *
 * <p>The {@code regulatorNotify} flag drives an F8 rule that pre-fills
 * the regulator-notification queue (CMA disclosure, SAMA material
 * decision, SDAIA personal-data event, etc.).
 *
 * <p>Audit-log + DomainEvent on create. Resolutions don't have a
 * mutable status — the outcome (APPROVED/REJECTED/DEFERRED/WITHDRAWN)
 * is set at creation and is part of the immutable record.
 */
@Service
@RequiredArgsConstructor
public class CommitteeResolutionService {

    private final CommitteeResolutionRepository repo;
    private final AuditLogService               audit;
    private final ApplicationEventPublisher     publisher;

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public Page<CommitteeResolution> list(Pageable pageable) {
        try {
            return repo.findByOrganisationIdOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), pageable);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public CommitteeResolution get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeResolution " + id));
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeResolution> listByMeeting(Long meetingId) {
        try {
            return repo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(
                TenantContext.getCurrentTenant(), meetingId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeResolution> listByCommittee(Long committeeId) {
        try {
            return repo.findByOrganisationIdAndCommitteeIdOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), committeeId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional
    public CommitteeResolution create(CommitteeResolution in) {
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        CommitteeResolution saved = repo.save(in);

        audit.log("RESOLUTION_CREATE", "CommitteeResolution", saved.getId(),
            saved.getResolutionCode() + " · " + saved.getOutcome()
                + " (" + saved.getVoteFor() + "/" + saved.getVoteAgainst() + "/" + saved.getVoteAbstain() + ")");

        // F8 — broadcast resolution outcomes so downstream automation can
        // act (regulator notification, board notification, risk-register
        // update for accepted risks, policy publication for approved
        // policies, etc.). The trigger event encodes the outcome so rule
        // conditions can dispatch.
        publisher.publishEvent(DomainEvent.of(
            org, "COMMITTEE_RESOLUTION_" + saved.getOutcome(),
            "CommitteeResolution", saved.getId(),
            Map.of("resolutionCode",  saved.getResolutionCode(),
                   "committeeId",     saved.getCommitteeId(),
                   "meetingId",       saved.getMeetingId(),
                   "outcome",         saved.getOutcome(),
                   "regulatorNotify", Boolean.valueOf(saved.getRegulatorNotify() != null && saved.getRegulatorNotify() == 1),
                   "confidentiality", saved.getConfidentiality() == null ? "" : saved.getConfidentiality())));

        return saved;
    }
}
