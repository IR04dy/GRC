package com.tss.grc.module.ndi.audit.service;

import com.tss.grc.module.ndi.audit.entity.NdiAuditEvent;
import com.tss.grc.module.ndi.audit.repository.NdiAuditEventRepository;
import com.tss.grc.common.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralised audit publisher used by every NDI slice's service layer.
 *
 * <p>Other slices autowire this and call {@code publish(...)} at the
 * end of each write path — keeps the audit ledger consistent without
 * duplicating boilerplate per service.
 *
 * <p>Errors here are intentionally swallowed at warn level — an audit
 * write failure must not roll back the parent transaction. Operations
 * proceed; only the audit log loses a row.
 */
@Component
public class NdiAuditPublisher {

    private final NdiAuditEventRepository repo;

    public NdiAuditPublisher(NdiAuditEventRepository repo) { this.repo = repo; }

    /** Convenience for transitions — captures from/to. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishTransition(String entityType, Long entityId, String entityCode,
                                  Long cycleId, String fromState, String toState,
                                  String summary) {
        try {
            NdiAuditEvent e = baseEvent(entityType, entityId, entityCode, cycleId, "TRANSITION", summary);
            e.setFromState(fromState);
            e.setToState(toState);
            repo.save(e);
        } catch (Exception ignored) { /* audit failure never rolls back the parent op */ }
    }

    /** CREATE / UPDATE / DELETE and other simple actions. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String entityType, Long entityId, String entityCode,
                        Long cycleId, String action, String summary) {
        try {
            repo.save(baseEvent(entityType, entityId, entityCode, cycleId, action, summary));
        } catch (Exception ignored) { /* swallow */ }
    }

    /** Action with explicit severity (WARN/ERROR). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String entityType, Long entityId, String entityCode,
                        Long cycleId, String action, String severity, String summary) {
        try {
            NdiAuditEvent e = baseEvent(entityType, entityId, entityCode, cycleId, action, summary);
            if (severity != null) e.setSeverity(severity);
            repo.save(e);
        } catch (Exception ignored) { /* swallow */ }
    }

    private NdiAuditEvent baseEvent(String entityType, Long entityId, String entityCode,
                                    Long cycleId, String action, String summary) {
        NdiAuditEvent e = new NdiAuditEvent();
        e.setOrganisationId(TenantContext.getCurrentTenant());
        e.setCycleId(cycleId);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setEntityCode(entityCode);
        e.setAction(action);
        e.setSeverity("INFO");
        e.setSummary(summary == null || summary.length() <= 500 ? summary : summary.substring(0, 500));
        e.setActorUsername(currentUsername());
        return e;
    }

    private static String currentUsername() {
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            return a == null ? null : a.getName();
        } catch (Exception ignored) { return null; }
    }
}
