package com.tss.grc.module.ndi.compliance.controller;

import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.service.NdiComplianceAssessmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * NDI Compliance Assessment REST endpoints —
 * {@code /api/ndi/compliance/*}.
 *
 * <pre>
 *   GET    /?cycleId=&status=&domain=     list within a cycle
 *   GET    /aggregate?cycleId=            per-cycle rollup
 *   GET    /:id                           fetch one
 *   POST   /start?cycleId=                bulk-create from COMPLIANCE framework items
 *   PUT    /:id                           update response/evidence/owner/etc.
 *   POST   /:id/set-status                body { status, naJustification? } — Rules 3/4/5
 *   POST   /:id/attach-evidence           body { evidenceRef, evidenceOwner? }
 *   POST   /:id/submit                    DRAFT/IN_PROGRESS/RETURNED → SUBMITTED
 *   POST   /:id/mark-under-review         SUBMITTED → UNDER_REVIEW
 *   POST   /:id/return                    body { reason } → RETURNED
 *   POST   /:id/approve                   body { approverName } → APPROVED
 *   POST   /:id/reopen                    APPROVED → IN_PROGRESS
 *   DELETE /:id                           soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/compliance")
@RequiredArgsConstructor
@Tag(name = "NDI — Compliance Assessment")
public class NdiComplianceAssessmentController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiComplianceAssessmentService service;

    @GetMapping
    @PreAuthorize(READ)
    public Page<NdiComplianceAssessment> list(
            @RequestParam Long cycleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        var p = PageRequest.of(page, size);
        if (status != null) return service.listByCycleAndStatus(cycleId, status, p);
        if (domain != null) return service.listByCycleAndDomain(cycleId, domain, p);
        return service.listByCycle(cycleId, p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize(READ)
    public Map<String, Object> aggregate(@RequestParam Long cycleId) {
        return service.aggregateForCycle(cycleId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiComplianceAssessment get(@PathVariable Long id) { return service.get(id); }

    /** Cross-module: returns the ERM risk row this compliance assessment's risk_id points to, or null. */
    @GetMapping("/{id}/linked-risk")
    @PreAuthorize(READ)
    public com.tss.grc.module.ndi.integration.NdiCrossLinkService.LinkedRisk linkedRisk(@PathVariable Long id) {
        return service.getLinkedRisk(id);
    }

    @PostMapping("/start")
    @PreAuthorize(WRITE)
    public Map<String, Object> startForCycle(@RequestParam Long cycleId) {
        return service.startForCycle(cycleId);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiComplianceAssessment update(@PathVariable Long id, @RequestBody NdiComplianceAssessment req) {
        return service.update(id, req);
    }

    /** Body: {@code { "status": "COMPLIANT", "naJustification": "..." }}. */
    @PostMapping("/{id}/set-status")
    @PreAuthorize(WRITE)
    public NdiComplianceAssessment setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.setStatus(id, body.get("status"), body.get("naJustification"));
    }

    /** Body: {@code { "evidenceRef": "...", "evidenceOwner": "..." }}. */
    @PostMapping("/{id}/attach-evidence")
    @PreAuthorize(WRITE)
    public NdiComplianceAssessment attachEvidence(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.attachEvidence(id, body.get("evidenceRef"), body.get("evidenceOwner"));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(WRITE)
    public NdiComplianceAssessment submit(@PathVariable Long id) { return service.submit(id); }

    @PostMapping("/{id}/mark-under-review")
    @PreAuthorize(WRITE)
    public NdiComplianceAssessment markUnderReview(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.markUnderReview(id, body == null ? null : body.get("reviewerName"));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(WRITE)
    public NdiComplianceAssessment returnForChanges(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.returnForChanges(id, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public NdiComplianceAssessment approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("approverName"));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(APPROVE)
    public NdiComplianceAssessment reopen(@PathVariable Long id) { return service.reopen(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
