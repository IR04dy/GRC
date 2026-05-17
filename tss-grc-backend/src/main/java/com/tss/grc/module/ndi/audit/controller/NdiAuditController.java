package com.tss.grc.module.ndi.audit.controller;

import com.tss.grc.module.ndi.audit.entity.NdiAuditEvent;
import com.tss.grc.module.ndi.audit.service.NdiAuditService;
import com.tss.grc.module.ndi.audit.service.NdiAuditService.AuditAggregate;
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
@Tag(name = "NDI — Audit Trail")
public class NdiAuditController {

    private final NdiAuditService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<NdiAuditEvent> search(@RequestParam(required = false) Long cycleId,
                                      @RequestParam(required = false) String entityType,
                                      @RequestParam(required = false) String action,
                                      @RequestParam(required = false) String severity,
                                      @RequestParam(defaultValue = "0")   int page,
                                      @RequestParam(defaultValue = "200") int size) {
        return service.search(cycleId, entityType, action, severity, PageRequest.of(page, size));
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public AuditAggregate aggregate() { return service.aggregate(); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiAuditEvent> record(@RequestBody NdiAuditEvent body) {
        return ResponseEntity.ok(service.record(body));
    }
}
