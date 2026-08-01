package com.tss.grc.module.ndi.audit.controller;

import com.tss.grc.module.ndi.audit.entity.NdiAuditEvent;
import com.tss.grc.module.ndi.audit.service.NdiAuditService;
import com.tss.grc.module.ndi.audit.service.NdiAuditService.AuditAggregate;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ndi/audit")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "NDI — Audit Trail")
public class NdiAuditController {

    private final NdiAuditService service;

    @GetMapping
    @PreAuthorize("hasPermission('NDI','READ')")
    public Page<NdiAuditEvent> search(@RequestParam(required = false) Long cycleId,
                                      @RequestParam(required = false) String entityType,
                                      @RequestParam(required = false) String action,
                                      @RequestParam(required = false) String severity,
                                      @RequestParam(defaultValue = "0")   int page,
                                      @RequestParam(defaultValue = "200") int size) {
        return service.search(cycleId, entityType, action, severity, PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasPermission('NDI','READ')")
    public AuditAggregate aggregate() { return service.aggregate(); }

    /**
     * SEC-NDI-002: the append-only NDI audit ledger is normally written only by
     * {@link com.tss.grc.module.ndi.audit.service.NdiAuditPublisher} from inside
     * each slice's write path. This manual entry point is retained for platform
     * administrators only; the actor identity and timestamp are stamped
     * server-side in {@link NdiAuditService#record} and cannot be supplied by the
     * caller, preventing audit-log forgery / back-dating.
     */
    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<NdiAuditEvent> record(@RequestBody NdiAuditEvent body) {
        return ResponseEntity.ok(service.record(body));
    }
}
