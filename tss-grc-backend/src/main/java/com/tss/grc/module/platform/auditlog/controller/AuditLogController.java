package com.tss.grc.module.platform.auditlog.controller;

import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
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

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<AuditLogEntry> list(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100") int size) {
        return service.list(actionType, sourceModule, severity, page, size);
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> stats() { return service.stats24h(); }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public List<AuditLogEntry> history(@RequestParam String entityType,
                                       @RequestParam Long entityId) {
        return service.historyFor(entityType, entityId);
    }

    @GetMapping("/trace/{correlationId}")
    @PreAuthorize("isAuthenticated()")
    public List<AuditLogEntry> trace(@PathVariable String correlationId) {
        return service.trace(correlationId);
    }
}
