package com.tss.grc.module.ndi.maturity.controller;

import com.tss.grc.module.ndi.maturity.entity.NdiMaturityAssessment;
import com.tss.grc.module.ndi.maturity.service.NdiMaturityAssessmentService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * NDI Maturity Assessment REST endpoints —
 * {@code /api/ndi/maturity/*}.
 *
 * <pre>
 *   GET    /?cycleId=&status=&domain=     list items in a cycle
 *   GET    /aggregate?cycleId=            per-cycle rollup
 *   GET    /:id                           fetch one
 *   POST   /start?cycleId=                bulk-create rows from MATURITY framework items
 *   PUT    /:id                           update (level/response/evidence/etc.)
 *   POST   /:id/submit                    → SUBMITTED (Rule 2 enforced)
 *   POST   /:id/mark-under-review         SUBMITTED → UNDER_REVIEW
 *   POST   /:id/return                    body { reason } → RETURNED
 *   POST   /:id/approve                   body { approverName } → APPROVED
 *   POST   /:id/reopen                    APPROVED → IN_PROGRESS
 *   DELETE /:id                           soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/maturity")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "NDI — Maturity Assessment")
public class NdiMaturityAssessmentController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiMaturityAssessmentService service;

    @GetMapping
    @PreAuthorize(READ)
    public Page<NdiMaturityAssessment> list(
            @RequestParam Long cycleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
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
    public NdiMaturityAssessment get(@PathVariable Long id) { return service.get(id); }

    /** Bulk-create rows for a cycle from MATURITY framework items. Idempotent. */
    @PostMapping("/start")
    @PreAuthorize(WRITE)
    public Map<String, Object> startForCycle(@RequestParam Long cycleId) {
        return service.startForCycle(cycleId);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiMaturityAssessment update(@PathVariable Long id, @RequestBody NdiMaturityAssessment req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(WRITE)
    public NdiMaturityAssessment submit(@PathVariable Long id) { return service.submit(id); }

    /** Body: {@code { "reviewerName": "..." }} (optional). */
    @PostMapping("/{id}/mark-under-review")
    @PreAuthorize(WRITE)
    public NdiMaturityAssessment markUnderReview(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.markUnderReview(id, body == null ? null : body.get("reviewerName"));
    }

    /** Body: {@code { "reason": "..." }} required. */
    @PostMapping("/{id}/return")
    @PreAuthorize(WRITE)
    public NdiMaturityAssessment returnForChanges(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.returnForChanges(id, body == null ? null : body.get("reason"));
    }

    /** Body: {@code { "approverName": "..." }} (optional). */
    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public NdiMaturityAssessment approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("approverName"));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(APPROVE)
    public NdiMaturityAssessment reopen(@PathVariable Long id) { return service.reopen(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
