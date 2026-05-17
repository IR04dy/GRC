package com.tss.grc.module.ndi.submission.controller;

import com.tss.grc.module.ndi.submission.entity.NdiSubmissionPackage;
import com.tss.grc.module.ndi.submission.service.NdiSubmissionPackageService;
import com.tss.grc.module.ndi.submission.service.NdiSubmissionPackageService.Preflight;
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
 *   POST   /:id/approve                     stamps approver_name + approved_at
 *   POST   /:id/submit                      READY → SUBMITTED with attestation
 *   POST   /:id/acknowledge                 SUBMITTED → ACKNOWLEDGED with sdaia_reference
 *   POST   /:id/reject                      SUBMITTED → REJECTED with reason
 *   POST   /:id/reopen                      REJECTED → DRAFT (clears stamps)
 *   DELETE /:id                             soft-delete (DRAFT/READY/REJECTED only)
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/submission-package")
@RequiredArgsConstructor
@Tag(name = "NDI — Submission Package")
public class NdiSubmissionPackageController {

    private final NdiSubmissionPackageService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NdiSubmissionPackage> list(@RequestParam Long cycleId) {
        return service.listForCycle(cycleId);
    }

    @GetMapping("/preflight")
    @PreAuthorize("isAuthenticated()")
    public Preflight preflight(@RequestParam Long cycleId) {
        return service.preflight(cycleId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiSubmissionPackage get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiSubmissionPackage> create(@RequestParam Long cycleId,
                                                       @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(service.create(cycleId,
            body == null ? null : body.get("preparerName"),
            body == null ? null : body.get("preparerEmail")));
    }

    @PostMapping("/{id}/regenerate")
    @PreAuthorize("isAuthenticated()")
    public NdiSubmissionPackage regenerate(@PathVariable Long id) { return service.regenerate(id); }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("isAuthenticated()")
    public NdiSubmissionPackage finalize_(@PathVariable Long id) { return service.finalize(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public NdiSubmissionPackage approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("approverName"));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public NdiSubmissionPackage submit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.submit(id, body.get("submitterName"), body.get("attestationEn"), body.get("attestationAr"));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public NdiSubmissionPackage acknowledge(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.acknowledge(id, body.get("sdaiaReference"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public NdiSubmissionPackage reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.reject(id, body.get("reason"));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("isAuthenticated()")
    public NdiSubmissionPackage reopen(@PathVariable Long id) { return service.reopen(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
