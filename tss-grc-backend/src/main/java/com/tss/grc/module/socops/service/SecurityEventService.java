package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * CRUD + workflow operations on a {@link SecurityEvent} after it's been
 * ingested. Triage / assign / resolve / mark false-positive are the four
 * verbs the SOC analyst uses in normal operation; each writes to the
 * audit log so the SAMA examiner can reconstruct the timeline.
 */
@Service
@RequiredArgsConstructor
public class SecurityEventService {

    private final SecurityEventRepository      repo;
    private final AuditLogService              audit;
    private final ApplicationEventPublisher    publisher;

    @Transactional(readOnly = true)
    public Page<SecurityEvent> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByEventTimeDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public SecurityEvent get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("SecurityEvent " + id));
    }

    @Transactional
    public SecurityEvent triage(Long id, Long assigneeUserId) {
        SecurityEvent e = get(id);
        e.setStatus("TRIAGED");
        e.setTriagedAt(LocalDateTime.now());
        if (assigneeUserId != null) e.setAssignedToUserId(assigneeUserId);
        SecurityEvent saved = repo.save(e);
        audit.log("SECURITY_EVENT_TRIAGE", "SecurityEvent", id,
            "Triaged" + (assigneeUserId == null ? "" : " — assigned to user " + assigneeUserId));
        publisher.publishEvent(DomainEvent.of(
            saved.getOrganisationId(), "SECURITY_EVENT_TRIAGED",
            "SecurityEvent", id,
            Map.of("assigneeUserId", assigneeUserId == null ? 0 : assigneeUserId,
                   "severity",       saved.getSeverity() == null ? "" : saved.getSeverity())));
        return saved;
    }

    @Transactional
    public SecurityEvent resolve(Long id, String resolution, boolean falsePositive) {
        SecurityEvent e = get(id);
        e.setStatus(falsePositive ? "FALSE_POSITIVE" : "RESOLVED");
        e.setResolvedAt(LocalDateTime.now());
        if (resolution != null && !resolution.isBlank()) {
            String existing = e.getNotes() == null ? "" : e.getNotes() + "\n";
            e.setNotes(existing + "[" + LocalDateTime.now() + "] " + resolution);
        }
        SecurityEvent saved = repo.save(e);
        audit.log("SECURITY_EVENT_" + (falsePositive ? "FALSE_POSITIVE" : "RESOLVE"),
            "SecurityEvent", id, "Closed: " + (resolution == null ? "—" : resolution));
        publisher.publishEvent(DomainEvent.of(
            saved.getOrganisationId(), "SECURITY_EVENT_RESOLVED",
            "SecurityEvent", id,
            Map.of("falsePositive", falsePositive,
                   "severity",      saved.getSeverity() == null ? "" : saved.getSeverity(),
                   "status",        saved.getStatus()   == null ? "" : saved.getStatus())));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        SecurityEvent e = get(id);
        e.softDelete(null);
        repo.save(e);
        audit.log("SECURITY_EVENT_DELETE", "SecurityEvent", id, "Soft-deleted");
    }
}
