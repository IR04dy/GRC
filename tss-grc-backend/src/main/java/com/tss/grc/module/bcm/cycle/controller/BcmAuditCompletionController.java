package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcmManagementReview;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.entity.SiemInboundEvent;
import com.tss.grc.module.bcm.cycle.service.BcmAuditCompletionService;
import com.tss.grc.module.bcm.cycle.service.BcmAuditCompletionService.OpresSummary;
import com.tss.grc.module.bcm.cycle.service.BcmAuditCompletionService.SiemInboundPayload;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for the audit-completion bundle:
 *
 * <pre>
 *   Tier-3 #18 — Management reviews
 *     GET    /api/v1/bcm/management-reviews
 *     GET    /api/v1/bcm/management-reviews/{id}
 *     POST   /api/v1/bcm/management-reviews
 *     POST   /api/v1/bcm/management-reviews/{id}/transition  {to:"APPROVED"}
 *
 *   Tier-3 #21 — SIEM/SOAR inbound webhook
 *     GET    /api/v1/bcm/siem-events
 *     POST   /api/v1/bcm/siem-events                          (webhook ingest)
 *
 *   Tier-4 #24 — SAMA OPRES summary
 *     GET    /api/v1/bcm/opres/summary
 *
 *   Tier-4 #29 — PDPL Article 23 draft
 *     POST   /api/v1/bcm/crisis-events/{id}/pdpl-draft        {reference?}
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@Tag(name = "BCM Audit Completion (Tier-3 #18 #21 · Tier-4 #24 #29)")
public class BcmAuditCompletionController {

    private final BcmAuditCompletionService service;

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    public record TransitionBody(String to) {}
    public record PdplDraftBody(String reference) {}

    // ── Management reviews ──────────────────────────────────────

    @GetMapping("/management-reviews")
    @PreAuthorize("isAuthenticated()")
    public List<BcmManagementReview> listReviews() { return service.listManagementReviews(); }

    @GetMapping("/management-reviews/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmManagementReview getReview(@PathVariable Long id) { return service.getManagementReview(id); }

    @PostMapping("/management-reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BcmManagementReview> createReview(@RequestBody BcmManagementReview body) {
        return ResponseEntity.ok(service.createManagementReview(body));
    }

    @PostMapping("/management-reviews/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public BcmManagementReview transitionReview(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transitionManagementReview(id, body.to(), actor());
    }

    // ── SIEM webhook ────────────────────────────────────────────

    @GetMapping("/siem-events")
    @PreAuthorize("isAuthenticated()")
    public List<SiemInboundEvent> listSiem(@RequestParam(required = false) String status) {
        return service.listSiemEvents(status);
    }

    /**
     * Webhook ingest — accepts any authenticated caller for now. A future
     * sprint will swap to a shared-secret header so SIEM systems can post
     * without a user JWT.
     */
    @PostMapping("/siem-events")
    @PreAuthorize("isAuthenticated()")
    public SiemInboundEvent ingestSiem(@RequestBody SiemInboundPayload payload) {
        return service.ingestSiemEvent(payload);
    }

    // ── OPRES summary (Tier-4 #24) ──────────────────────────────

    @GetMapping("/opres/summary")
    @PreAuthorize("isAuthenticated()")
    public OpresSummary opresSummary() { return service.opresSummary(); }

    // ── PDPL Art. 23 draft (Tier-4 #29) ─────────────────────────

    @PostMapping("/crisis-events/{id}/pdpl-draft")
    @PreAuthorize("isAuthenticated()")
    public CrisisEvent draftPdpl(@PathVariable Long id, @RequestBody(required = false) PdplDraftBody body) {
        return service.draftPdplNotification(id, body == null ? null : body.reference());
    }
}
