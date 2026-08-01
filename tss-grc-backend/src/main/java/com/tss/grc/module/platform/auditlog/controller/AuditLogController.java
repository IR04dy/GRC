package com.tss.grc.module.platform.auditlog.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.service.AuditChainService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the platform-wide audit log — F7.
 *
 * <pre>
 *   GET /api/v1/audit-log                 list (filter: actionType, sourceModule, severity)
 *   GET /api/v1/audit-log/stats           24-hour counts
 *   GET /api/v1/audit-log/history         entity history (entityType+entityId required)
 *   GET /api/v1/audit-log/trace/:correlationId  follow a single business action across modules
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/audit-log")
@RequiredArgsConstructor
@Tag(name = "Audit Log (F7)")
public class AuditLogController {

    private final AuditLogService service;
    private final AuditChainService chain;

    @GetMapping
    @PreAuthorize("hasPermission('AUDIT_LOG','READ')")
    public Page<AuditLogEntry> list(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100") int size) {
        return service.list(actionType, sourceModule, severity, page, Math.min(size, 200));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('AUDIT_LOG','READ')")
    public Map<String, Long> stats() { return service.stats24h(); }

    @GetMapping("/history")
    @PreAuthorize("hasPermission('AUDIT_LOG','READ')")
    public List<AuditLogEntry> history(@RequestParam String entityType,
                                       @RequestParam Long entityId) {
        return service.historyFor(entityType, entityId);
    }

    @GetMapping("/trace/{correlationId}")
    @PreAuthorize("hasPermission('AUDIT_LOG','READ')")
    public List<AuditLogEntry> trace(@PathVariable String correlationId) {
        return service.trace(correlationId);
    }

    // ─── F2-31: tamper-evidence ───────────────────────────────────────

    /**
     * Verifies this tenant's audit-ledger hash chain and reports the first break, if any.
     *
     * <p>The response deliberately carries a {@code limitation} string. This control detects
     * modification, mid-chain deletion and insertion; it does NOT detect truncation of the newest
     * rows, and unkeyed it does not resist an actor with database write access. An integrity report
     * that answers "VERIFIED" without saying what was not checked is the kind of evidence that
     * misleads an examiner, so the boundary travels with the verdict.
     */
    @GetMapping("/integrity")
    @PreAuthorize("hasPermission('AUDIT_LOG','READ')")
    public Map<String, Object> integrity() {
        return chain.verifyTenant(TenantContext.getCurrentTenant());
    }

    /**
     * Seals this tenant's unsealed rows now, instead of waiting for the periodic pass.
     *
     * <p>Guarded on {@code AUDIT_LOG:WRITE} — it mutates the three integrity columns. It exists so
     * the control can be EXERCISED rather than only observed: a sealing pass whose only trigger is a
     * timer is a control nobody can demonstrate on request, and DF-1 showed what an unobserved
     * scheduled sweep costs (48 silent failures a day for eight days).
     */
    @PostMapping("/integrity/seal")
    @PreAuthorize("hasPermission('AUDIT_LOG','WRITE')")
    public Map<String, Object> seal() {
        Long org = TenantContext.getCurrentTenant();
        int sealed = chain.sealTenant(org);
        return Map.of("organisationId", org, "sealed", sealed);
    }
}
