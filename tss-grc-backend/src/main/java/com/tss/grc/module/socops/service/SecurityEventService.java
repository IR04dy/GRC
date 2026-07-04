package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.incident.dto.IncidentRequest;
import com.tss.grc.module.incident.dto.IncidentResponse;
import com.tss.grc.module.incident.service.IncidentService;
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
    /** SOC-C: an escalated event is promoted into the canonical cyber.incidents
     *  register (CY-B) — the table the dashboard KPI + PDPL bridge read. */
    private final IncidentService              incidentService;

    /** Incident register severities (CK_incidents_severity). SIEM events also use
     *  INFO, which maps down to LOW. */
    private static final java.util.Set<String> LEGAL_SEVERITY =
        java.util.Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static String legalSeverity(String s) {
        String v = s == null ? "MEDIUM" : s.trim().toUpperCase();
        if (LEGAL_SEVERITY.contains(v)) return v;
        return "INFO".equals(v) ? "LOW" : "MEDIUM";
    }

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

    /**
     * SOC-C: promote a security event to a first-class cyber incident. Creates a
     * row in the canonical {@code cyber.incidents} register (so it lands on the
     * dashboard "open incidents" KPI and is PDPL-linkable via CY-B), links it back
     * through {@code incidentId}, and flips the event to ESCALATED. Idempotent — an
     * event already carrying an {@code incidentId} is returned unchanged. Carries the
     * event's SIEM context (source/host/ip/user/time) into the incident description.
     */
    @Transactional
    public SecurityEvent escalateToIncident(Long id, String note) {
        SecurityEvent e = get(id);
        if (e.getIncidentId() != null) return e;                       // already promoted — idempotent
        if ("FALSE_POSITIVE".equals(e.getStatus())) {
            throw new IllegalStateException("Cannot escalate a false-positive event to an incident");
        }

        String label = (e.getRuleName() != null && !e.getRuleName().isBlank())
                ? e.getRuleName()
                : (e.getCategory() != null && !e.getCategory().isBlank() ? e.getCategory() : "security event");
        String title = "SOC escalation — " + label + " (event #" + id + ")";
        if (title.length() > 255) title = title.substring(0, 255);

        StringBuilder d = new StringBuilder("Escalated from SOC security event #").append(id).append('.');
        if (e.getSourceSystem() != null) d.append(" source=").append(e.getSourceSystem()).append(';');
        if (e.getCategory() != null)     d.append(" category=").append(e.getCategory()).append(';');
        if (e.getSourceHost() != null)   d.append(" host=").append(e.getSourceHost()).append(';');
        if (e.getSourceIp() != null)     d.append(" srcIp=").append(e.getSourceIp()).append(';');
        if (e.getUsername() != null)     d.append(" user=").append(e.getUsername()).append(';');
        if (e.getEventTime() != null)    d.append(" eventTime=").append(e.getEventTime()).append(';');
        if (note != null && !note.isBlank()) d.append("\nAnalyst note: ").append(note);

        IncidentResponse inc = incidentService.create(new IncidentRequest(
                title, null, legalSeverity(e.getSeverity()), "CYBER", "OPEN", null,
                e.getEventTime() != null ? e.getEventTime().toLocalDate() : null, null,
                d.toString(), null, null, null));

        e.setIncidentId(inc.id());
        e.setStatus("ESCALATED");
        if (e.getTriagedAt() == null) e.setTriagedAt(LocalDateTime.now());
        SecurityEvent saved = repo.save(e);

        audit.log("SECURITY_EVENT_ESCALATE", "SecurityEvent", id,
                "Escalated to incident #" + inc.id() + (note == null || note.isBlank() ? "" : " — " + note));
        publisher.publishEvent(DomainEvent.of(
                saved.getOrganisationId(), "SECURITY_EVENT_ESCALATED", "SecurityEvent", id,
                Map.of("incidentId", inc.id(),
                       "severity", saved.getSeverity() == null ? "" : saved.getSeverity())));
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
