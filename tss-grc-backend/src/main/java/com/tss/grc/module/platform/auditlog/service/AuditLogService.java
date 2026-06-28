package com.tss.grc.module.platform.auditlog.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Platform audit-log service — Framework Intersection F7.
 *
 * <p>Two paths into the log:
 * <ul>
 *   <li>The full builder: {@link #recordEvent(AuditEventBuilder)} for
 *       callers that want to set every field (workflow_status_before
 *       / _after, correlation id, payload diff, custom severity).</li>
 *   <li>The convenience {@link #log(String, String, Long, String)}
 *       for one-liner audit at the end of a service method.</li>
 * </ul>
 *
 * <p>HTTP context (ip/user_agent/endpoint) and Spring Security user
 * info are pulled automatically. TenantContext gives the
 * organisation_id. Callers never have to remember those fields.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final Set<String> SEVERITIES = Set.of("INFO", "WARN", "ERROR");

    private final AuditLogRepository repo;
    /** Optional: HttpServletRequest is only present inside an HTTP call. */
    private final ObjectProvider<HttpServletRequest> requestProvider;

    /** Convenience: log a simple action. */
    public AuditLogEntry log(String actionType, String entityType, Long entityId, String message) {
        return recordEvent(new AuditEventBuilder()
            .actionType(actionType)
            .entityType(entityType)
            .entityId(entityId)
            .message(message));
    }

    /** Full builder path. */
    @Transactional
    public AuditLogEntry recordEvent(AuditEventBuilder b) {
        AuditLogEntry e = new AuditLogEntry();
        Long org = TenantContext.getCurrentTenant();
        e.setOrganisationId(org != null ? org : 1L);   // fall back to tenant 1 (system events)

        // Required
        if (b.actionType == null || b.actionType.isBlank())
            throw new IllegalArgumentException("audit action_type is required");
        e.setActionType(b.actionType);

        e.setEntityType(b.entityType);
        e.setEntityId(b.entityId);
        e.setEntityExternalId(b.entityExternalId);
        e.setEntityCode(b.entityCode);
        e.setOldValueJson(b.oldValueJson);
        e.setNewValueJson(b.newValueJson);
        e.setWorkflowStatusBefore(b.workflowStatusBefore);
        e.setWorkflowStatusAfter(b.workflowStatusAfter);
        e.setSourceModule(b.sourceModule);
        e.setCorrelationId(b.correlationId);
        e.setParentAuditId(b.parentAuditId);
        e.setDigitalSignatureRef(b.digitalSignatureRef);
        e.setMessage(b.message);

        if (b.severity != null) {
            if (!SEVERITIES.contains(b.severity))
                throw new IllegalArgumentException("severity must be INFO/WARN/ERROR");
            e.setSeverity(b.severity);
        }

        // Auto-fill from HTTP + security if available
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            e.setUserUsername(auth.getName());
            if (!auth.getAuthorities().isEmpty())
                e.setUserRole(auth.getAuthorities().iterator().next().getAuthority());
        }
        HttpServletRequest req = currentRequestOrNull();
        if (req != null) {
            e.setIpAddress(extractIp(req));
            e.setUserAgent(truncate(req.getHeader("User-Agent"), 500));
            e.setSourceEndpoint(req.getMethod() + " " + req.getRequestURI());
        }

        AuditLogEntry saved = repo.save(e);
        log.debug("[audit] org={} {} {}#{} ({})",
            saved.getOrganisationId(), saved.getActionType(),
            saved.getEntityType(), saved.getEntityId(), saved.getSeverity());
        return saved;
    }

    /**
     * The current HTTP request, or {@code null} when none is bound to this thread
     * (scheduled / async execution). {@code ObjectProvider.getIfAvailable()} only
     * swallows a missing bean <em>definition</em> — for the request-scoped
     * {@code HttpServletRequest} it still propagates the "No thread-bound request
     * found" {@link IllegalStateException} when no request scope is active, which
     * previously aborted every audit write from a scheduler thread (C-3 deadline
     * sweeps). Catch it and treat "no request" as "no request metadata to capture".
     */
    private HttpServletRequest currentRequestOrNull() {
        try {
            return requestProvider.getIfAvailable();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ─── Queries ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AuditLogEntry> list(String actionType, String sourceModule, String severity, int page, int size) {
        Long org = TenantContext.getCurrentTenant();
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        if (actionType != null && !actionType.isBlank())
            return repo.findByOrganisationIdAndActionTypeOrderByCreatedAtDesc(org, actionType, pageable);
        if (sourceModule != null && !sourceModule.isBlank())
            return repo.findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(org, sourceModule, pageable);
        if (severity != null && !severity.isBlank())
            return repo.findByOrganisationIdAndSeverityOrderByCreatedAtDesc(org, severity, pageable);
        return repo.findByOrganisationIdOrderByCreatedAtDesc(org, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> historyFor(String entityType, Long entityId) {
        return repo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            TenantContext.getCurrentTenant(), entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> trace(String correlationId) {
        return repo.findByOrganisationIdAndCorrelationIdOrderByCreatedAtAsc(
            TenantContext.getCurrentTenant(), correlationId);
    }

    /** Lightweight stats for the admin dashboard widget. */
    @Transactional(readOnly = true)
    public Map<String, Long> stats24h() {
        Long org = TenantContext.getCurrentTenant();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        return Map.of(
            "events",  repo.countByOrganisationIdAndCreatedAtAfter(org, cutoff),
            "warns",   repo.countByOrganisationIdAndSeverityAndCreatedAtAfter(org, "WARN",  cutoff),
            "errors",  repo.countByOrganisationIdAndSeverityAndCreatedAtAfter(org, "ERROR", cutoff)
        );
    }

    /** Generate a stable correlation id for a multi-step business action. */
    public static String newCorrelationId() { return UUID.randomUUID().toString(); }

    // ─── Helpers ──────────────────────────────────────────────────

    private static String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return truncate((comma < 0 ? xff : xff.substring(0, comma)).trim(), 60);
        }
        return truncate(req.getRemoteAddr(), 60);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ─── Builder (compact API) ────────────────────────────────────

    public static class AuditEventBuilder {
        String actionType, entityType, entityExternalId, entityCode;
        Long entityId, parentAuditId;
        String oldValueJson, newValueJson;
        String workflowStatusBefore, workflowStatusAfter;
        String sourceModule, correlationId, digitalSignatureRef;
        String severity, message;

        public AuditEventBuilder actionType(String v) { this.actionType = v; return this; }
        public AuditEventBuilder entityType(String v) { this.entityType = v; return this; }
        public AuditEventBuilder entityId(Long v)     { this.entityId = v; return this; }
        public AuditEventBuilder entityExternalId(String v) { this.entityExternalId = v; return this; }
        public AuditEventBuilder entityCode(String v) { this.entityCode = v; return this; }
        public AuditEventBuilder oldValueJson(String v) { this.oldValueJson = v; return this; }
        public AuditEventBuilder newValueJson(String v) { this.newValueJson = v; return this; }
        public AuditEventBuilder workflowStatusBefore(String v) { this.workflowStatusBefore = v; return this; }
        public AuditEventBuilder workflowStatusAfter(String v)  { this.workflowStatusAfter = v; return this; }
        public AuditEventBuilder sourceModule(String v) { this.sourceModule = v; return this; }
        public AuditEventBuilder correlationId(String v) { this.correlationId = v; return this; }
        public AuditEventBuilder parentAuditId(Long v) { this.parentAuditId = v; return this; }
        public AuditEventBuilder digitalSignatureRef(String v) { this.digitalSignatureRef = v; return this; }
        public AuditEventBuilder severity(String v) { this.severity = v; return this; }
        public AuditEventBuilder message(String v) { this.message = v; return this; }
    }
}
