package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle;
import com.tss.grc.module.bcm.cycle.entity.BcmGovernanceRecord;
import com.tss.grc.module.bcm.cycle.service.BcmCycleService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for BCM cycles + governance — Sprint BCM-1.
 *
 * <pre>
 *   GET    /api/v1/bcm/cycles[?status=…]
 *   GET    /api/v1/bcm/cycles/:id
 *   POST   /api/v1/bcm/cycles
 *   PUT    /api/v1/bcm/cycles/:id
 *   POST   /api/v1/bcm/cycles/:id/transition  { "to": "IN_PROGRESS" }
 *
 *   GET    /api/v1/bcm/governance[?approvalStatus=…]
 *   GET    /api/v1/bcm/governance/:id
 *   POST   /api/v1/bcm/governance
 *   PUT    /api/v1/bcm/governance/:id
 *   POST   /api/v1/bcm/governance/:id/transition { "to": "APPROVED" }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Cycles + Governance (BCM-1)")
public class BcmCycleController {

    private final BcmCycleService service;

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    public record TransitionBody(String to) {}

    // ─── Cycles ────────────────────────────────────────────────────

    @GetMapping("/cycles")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BcmAssessmentCycle> listCycles(@RequestParam(required = false) String status) {
        return service.listCycles(status);
    }

    @GetMapping("/cycles/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcmAssessmentCycle getCycle(@PathVariable Long id) { return service.getCycle(id); }

    @PostMapping("/cycles")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<BcmAssessmentCycle> createCycle(@RequestBody BcmAssessmentCycle body) {
        return ResponseEntity.ok(service.createCycle(body));
    }

    @PutMapping("/cycles/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmAssessmentCycle updateCycle(@PathVariable Long id, @RequestBody BcmAssessmentCycle body) {
        return service.updateCycle(id, body);
    }

    @PostMapping("/cycles/{id}/transition")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmAssessmentCycle transitionCycle(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transitionCycle(id, body.to(), actor());
    }

    // ─── Governance ────────────────────────────────────────────────

    @GetMapping("/governance")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BcmGovernanceRecord> listGovernance(@RequestParam(required = false) String approvalStatus) {
        return service.listGovernance(approvalStatus);
    }

    @GetMapping("/governance/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcmGovernanceRecord getGovernance(@PathVariable Long id) { return service.getGovernance(id); }

    @PostMapping("/governance")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<BcmGovernanceRecord> createGovernance(@RequestBody BcmGovernanceRecord body) {
        return ResponseEntity.ok(service.createGovernance(body));
    }

    @PutMapping("/governance/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmGovernanceRecord updateGovernance(@PathVariable Long id, @RequestBody BcmGovernanceRecord body) {
        return service.updateGovernance(id, body);
    }

    @PostMapping("/governance/{id}/transition")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmGovernanceRecord transitionGovernance(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transitionGovernance(id, body.to(), actor());
    }
}
