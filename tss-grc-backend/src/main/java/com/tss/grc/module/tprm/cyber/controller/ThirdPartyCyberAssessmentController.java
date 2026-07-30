package com.tss.grc.module.tprm.cyber.controller;

import com.tss.grc.module.tprm.cyber.entity.ThirdPartyCyberAssessment;
import com.tss.grc.module.tprm.cyber.service.ThirdPartyCyberAssessmentService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tprm/cyber-assessments")
@RequiredArgsConstructor
@Tag(name = "Third-party cyber risk")
public class ThirdPartyCyberAssessmentController {

    private final ThirdPartyCyberAssessmentService service;

    /** Hard cap on client-supplied page size to prevent memory exhaustion. */
    private static final int MAX_PAGE_SIZE = 200;

    @GetMapping
    @PreAuthorize("hasPermission('TPRM','READ')")
    public Page<ThirdPartyCyberAssessment> list(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return status == null ? service.list(p) : service.listByStatus(status, p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public ThirdPartyCyberAssessment get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ResponseEntity<ThirdPartyCyberAssessment> create(@RequestBody ThirdPartyCyberAssessment req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PostMapping("/{id}/posture")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ThirdPartyCyberAssessment recordPosture(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.recordPosture(id, body);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ThirdPartyCyberAssessment submit(@PathVariable Long id) { return service.submit(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('TPRM','APPROVE')")
    public ThirdPartyCyberAssessment approve(@PathVariable Long id,
                                             @AuthenticationPrincipal GrcUserDetails principal) {
        // Approver identity is derived from the authenticated principal, never a client-supplied body field.
        return service.approve(id, principal.getUsername());
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasPermission('TPRM','APPROVE')")
    public ThirdPartyCyberAssessment retire(@PathVariable Long id) { return service.retire(id); }
}
