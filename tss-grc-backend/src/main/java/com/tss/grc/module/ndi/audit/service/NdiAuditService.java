package com.tss.grc.module.ndi.audit.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.audit.entity.NdiAuditEvent;
import com.tss.grc.module.ndi.audit.repository.NdiAuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * NDI Audit Service — slice 14.
 *
 * <p>Thin write/read API around the append-only ndi.audit_event table.
 * Other slices can call {@code record()} to publish state changes; the
 * /audit endpoints surface the ledger to the Audit tab.
 */
@Service
@Transactional
public class NdiAuditService {

    private final NdiAuditEventRepository repo;

    public NdiAuditService(NdiAuditEventRepository repo) { this.repo = repo; }

    private Long org() { return TenantContext.getCurrentTenant(); }

    public record AuditAggregate(
        long total,
        Map<String, Long> byEntityType,
        Map<String, Long> byAction,
        Map<String, Long> bySeverity
    ) {}

    @Transactional(readOnly = true)
    public Page<NdiAuditEvent> search(Long cycleId, String entityType, String action, String severity, Pageable p) {
        return repo.search(org(), cycleId, entityType, action, severity, p);
    }

    @Transactional(readOnly = true)
    public AuditAggregate aggregate() {
        Long o = org();
        Page<NdiAuditEvent> page = repo.findByOrganisationIdOrderByOccurredAtDesc(o, Pageable.unpaged());
        Map<String, Long> byEntity = new TreeMap<>();
        Map<String, Long> byAction = new TreeMap<>();
        Map<String, Long> bySev    = new TreeMap<>();
        for (NdiAuditEvent e : page) {
            byEntity.merge(e.getEntityType(), 1L, Long::sum);
            byAction.merge(e.getAction(), 1L, Long::sum);
            bySev.merge(e.getSeverity(), 1L, Long::sum);
        }
        return new AuditAggregate(page.getTotalElements(), byEntity, byAction, bySev);
    }

    public NdiAuditEvent record(NdiAuditEvent body) {
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getAction() == null) throw new IllegalArgumentException("action is required");
        if (body.getEntityType() == null) throw new IllegalArgumentException("entityType is required");
        if (body.getSeverity() == null) body.setSeverity("INFO");

        // SEC-NDI-002: this is an append-only regulatory ledger. The acting
        // identity and the timestamp are authoritative facts that must come from
        // the server, never from the request body — otherwise a caller could
        // back-date entries or attribute actions to another user. Overwrite any
        // attacker-supplied actor/timestamp with values derived from the
        // authenticated principal.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        body.setActorUsername(auth == null ? null : auth.getName());
        body.setActorRole(auth == null ? null : firstRole(auth));
        body.setOccurredAt(LocalDateTime.now());
        return repo.save(body);
    }

    /** First {@code ROLE_*} authority on the principal, without the {@code ROLE_} prefix. */
    private static String firstRole(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a != null && a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .findFirst()
            .orElse(null);
    }
}
