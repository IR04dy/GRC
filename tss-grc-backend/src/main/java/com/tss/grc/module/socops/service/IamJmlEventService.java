package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.IamJmlEvent;
import com.tss.grc.module.socops.repository.IamJmlEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IamJmlEventService {

    private final IamJmlEventRepository      repo;
    private final AuditLogService            audit;
    private final ApplicationEventPublisher  publisher;

    @Transactional(readOnly = true)
    public Page<IamJmlEvent> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByOccurredAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public IamJmlEvent get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("IamJmlEvent " + id));
    }

    @Transactional(readOnly = true)
    public List<IamJmlEvent> privilegedReviewsDueWithinDays(int days) {
        LocalDateTime now = LocalDateTime.now();
        return repo.findPrivilegedReviewsDueBetween(
            TenantContext.getCurrentTenant(), now, now.plusDays(days));
    }

    @Transactional
    public IamJmlEvent save(IamJmlEvent in) {
        // SEC-CYB-002 — never trust the client-supplied id on a write. On
        // create we force id=null so a forged id can't overwrite an existing
        // (possibly cross-tenant) row via Hibernate merge. On update we load
        // the row scoped to the caller's tenant and copy only mutable fields
        // onto the managed entity — a detached client-built entity is never
        // merged.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        IamJmlEvent target;
        // Snapshot prior state so transitions (outcome COMPLETED, SoD flag
        // first-trip) only fire once.
        String  priorOutcome = null;
        Boolean priorSod     = null;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            IamJmlEvent existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("IamJmlEvent " + in.getId()));
            priorOutcome = existing.getOutcome();
            priorSod     = existing.getSodViolationDetected();
            copyMutable(in, existing);
            target = existing;
        }
        IamJmlEvent saved = repo.save(target);
        audit.log(isNew ? "JML_CREATE" : "JML_UPDATE",
            "IamJmlEvent", saved.getId(),
            saved.getEventType() + " · " + saved.getSubjectUsername()
                + (Boolean.TRUE.equals(saved.getPrivileged()) ? " · PRIVILEGED" : ""));

        Map<String, Object> payload = Map.of(
            "eventType",      saved.getEventType()       == null ? "" : saved.getEventType(),
            "subject",        saved.getSubjectUsername() == null ? "" : saved.getSubjectUsername(),
            "privileged",     Boolean.TRUE.equals(saved.getPrivileged()),
            "outcome",        saved.getOutcome()         == null ? "" : saved.getOutcome());

        // F8 — AR-132. SoD violations need to land in the IAM lead inbox the
        // first time we flag them. Suppress on follow-up saves of the same row.
        boolean nowSod = Boolean.TRUE.equals(saved.getSodViolationDetected());
        boolean wasSod = Boolean.TRUE.equals(priorSod);
        if (nowSod && (isNew || !wasSod)) {
            publisher.publishEvent(DomainEvent.of(
                saved.getOrganisationId(), "IAM_SOD_VIOLATION",
                "IamJmlEvent", saved.getId(), payload));
        }
        // F8 — AR-144. PRIVILEGED grant/revoke landing as COMPLETED is the
        // moment the "who-had-what" audit trail is canonical — write it to
        // the SAMA / NCA evidence package.
        boolean nowDone = "COMPLETED".equalsIgnoreCase(saved.getOutcome());
        boolean wasDone = "COMPLETED".equalsIgnoreCase(priorOutcome);
        if (nowDone && Boolean.TRUE.equals(saved.getPrivileged()) && (isNew || !wasDone)) {
            publisher.publishEvent(DomainEvent.of(
                saved.getOrganisationId(), "IAM_JML_PRIVILEGED_COMPLETE",
                "IamJmlEvent", saved.getId(), payload));
        }
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(IamJmlEvent in, IamJmlEvent target) {
        target.setEventCode(in.getEventCode());
        target.setEventType(in.getEventType());
        target.setOccurredAt(in.getOccurredAt());
        target.setSubjectUserId(in.getSubjectUserId());
        target.setSubjectUsername(in.getSubjectUsername());
        target.setSubjectEmail(in.getSubjectEmail());
        target.setSubjectDepartment(in.getSubjectDepartment());
        target.setSubjectJobTitle(in.getSubjectJobTitle());
        target.setSourceSystem(in.getSourceSystem());
        target.setTargetSystem(in.getTargetSystem());
        target.setRoleOrEntitlement(in.getRoleOrEntitlement());
        target.setPrivileged(in.getPrivileged());
        target.setJustification(in.getJustification());
        target.setApproverUsername(in.getApproverUsername());
        target.setApproverRole(in.getApproverRole());
        target.setOutcome(in.getOutcome());
        target.setSodViolationDetected(in.getSodViolationDetected());
        target.setSodViolationDetail(in.getSodViolationDetail());
        target.setRequestedAt(in.getRequestedAt());
        target.setApprovedAt(in.getApprovedAt());
        target.setCompletedAt(in.getCompletedAt());
        target.setRevokedAt(in.getRevokedAt());
        target.setReviewDueAt(in.getReviewDueAt());
        target.setRelatedTicketId(in.getRelatedTicketId());
        target.setNotes(in.getNotes());
    }

    @Transactional
    public void delete(Long id) {
        IamJmlEvent e = get(id);
        e.softDelete(null);
        repo.save(e);
        audit.log("JML_DELETE", "IamJmlEvent", id, "Soft-deleted");
    }
}
