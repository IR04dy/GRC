package com.tss.grc.module.ndi.submission.controller;

import com.tss.grc.module.ndi.submission.entity.NdiSubmissionPackage;
import com.tss.grc.module.ndi.submission.service.NdiSubmissionPackageService;
import com.tss.grc.module.ndi.submission.service.NdiSubmissionPackageService.Preflight;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * NDI Submission Package REST endpoints — {@code /api/ndi/submission-package/*}.
 *
 * <pre>
 *   GET    /?cycleId=                       list packages for a cycle
 *   GET    /preflight?cycleId=              pre-flight result (Rule 15)
 *   GET    /:id                             fetch one (snapshot + manifest included)
 *   POST   /?cycleId=                       create new DRAFT and populate snapshot
 *   POST   /:id/regenerate                  rebuild snapshot from live state
 *   POST   /:id/finalize                    DRAFT → READY (preflight-gated)
 *   POST   /:id/approve                     stamps approver_name (= principal, != preparer) + approved_at
 *   POST   /:id/submit                      READY → SUBMITTED with attestation (submitter = principal)
 *   POST   /:id/acknowledge                 SUBMITTED → ACKNOWLEDGED with sdaia_reference
 *   POST   /:id/reject                      SUBMITTED → REJECTED with reason
 *   POST   /:id/reopen                      REJECTED → DRAFT (clears stamps)
 *   DELETE /:id                             soft-delete (DRAFT/READY/REJECTED only)
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/submission-package")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "NDI — Submission Package")
public class NdiSubmissionPackageController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    // Regulator-facing transitions (approve/submit/acknowledge/reject) and delete
    // are restricted to approvers.
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiSubmissionPackageService service;

    @GetMapping
    @PreAuthorize(READ)
    public List<NdiSubmissionPackage> list(@RequestParam Long cycleId) {
        return service.listForCycle(cycleId);
    }

    @GetMapping("/preflight")
    @PreAuthorize(READ)
    public Preflight preflight(@RequestParam Long cycleId) {
        return service.preflight(cycleId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiSubmissionPackage get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiSubmissionPackage> create(@RequestParam Long cycleId,
                                                       @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(service.create(cycleId,
            body == null ? null : body.get("preparerName"),
            body == null ? null : body.get("preparerEmail")));
    }

    @PostMapping("/{id}/regenerate")
    @PreAuthorize(WRITE)
    public NdiSubmissionPackage regenerate(@PathVariable Long id) { return service.regenerate(id); }

    @PostMapping("/{id}/finalize")
    @PreAuthorize(WRITE)
    public NdiSubmissionPackage finalize_(@PathVariable Long id) { return service.finalize(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public NdiSubmissionPackage approve(@PathVariable Long id) {
        // SEC-NDI-003: approver resolved server-side from the authenticated
        // principal; SoD (approver != preparer) enforced in the service.
        return service.approve(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(APPROVE)
    public NdiSubmissionPackage submit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        // SEC-NDI-003: submitterName resolved server-side from the principal;
        // only the attestation text comes from the body.
        return service.submit(id, body.get("attestationEn"), body.get("attestationAr"));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize(APPROVE)
    public NdiSubmissionPackage acknowledge(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.acknowledge(id, body.get("sdaiaReference"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(APPROVE)
    public NdiSubmissionPackage reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.reject(id, body.get("reason"));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(WRITE)
    public NdiSubmissionPackage reopen(@PathVariable Long id) { return service.reopen(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
