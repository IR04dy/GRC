package com.tss.grc.module.audit_lc.controller;

import com.tss.grc.module.audit_lc.dto.FindingDto;
import com.tss.grc.module.audit_lc.service.FindingService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/audit-lc/findings")
@RequiredArgsConstructor
@RequireModule("AUDIT_LC")
public class FindingController {

    private final FindingService service;

    @GetMapping
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<FindingDto> list(@RequestParam(required = false) String engagementCode) {
        return engagementCode != null ? service.listForEngagement(engagementCode) : service.listForOrg();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public FindingDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public ResponseEntity<FindingDto> create(@RequestBody @Valid FindingDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public FindingDto transition(@PathVariable String id, @RequestBody @Valid FindingDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('AUDIT','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
