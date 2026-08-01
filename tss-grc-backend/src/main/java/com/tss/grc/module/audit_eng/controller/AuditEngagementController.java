package com.tss.grc.module.audit_eng.controller;

import com.tss.grc.module.audit_eng.dto.AuditEngagementDto;
import com.tss.grc.module.audit_eng.service.AuditEngagementService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// Explicit bean name avoids collision with the legacy
// com.tss.grc.module.compliance.controller.AuditEngagementController.
@RestController("auditEngAuditEngagementController")
@RequestMapping("/api/audit-eng/engagements")
@RequiredArgsConstructor
@RequireModule("AUDIT_LC")
public class AuditEngagementController {

    private final AuditEngagementService service;

    @GetMapping
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<AuditEngagementDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return activeOnly ? service.listActive() : service.listForOrg();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public AuditEngagementDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public ResponseEntity<AuditEngagementDto> create(@RequestBody @Valid AuditEngagementDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public AuditEngagementDto transition(@PathVariable String id,
                                         @RequestBody @Valid AuditEngagementDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('AUDIT','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
