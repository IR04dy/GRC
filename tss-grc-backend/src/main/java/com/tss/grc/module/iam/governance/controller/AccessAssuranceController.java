package com.tss.grc.module.iam.governance.controller;

import com.tss.grc.module.iam.governance.dto.GovernanceDtos.BreakGlassActivateRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.BreakGlassReviewRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.CloseCampaignRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.CreateCampaignRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.ReviewDecisionRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.SodDispositionRequest;
import com.tss.grc.module.iam.governance.entity.AccessReviewItem;
import com.tss.grc.module.iam.governance.entity.BreakGlassEvent;
import com.tss.grc.module.iam.governance.entity.SodRule;
import com.tss.grc.module.iam.governance.entity.SodViolation;
import com.tss.grc.module.iam.governance.service.AccessAnalyticsService;
import com.tss.grc.module.iam.governance.service.AccessEvidencePackageService;
import com.tss.grc.module.iam.governance.service.BreakGlassService;
import com.tss.grc.module.iam.governance.service.RecertificationService;
import com.tss.grc.module.iam.governance.service.RecertificationService.CampaignView;
import com.tss.grc.module.iam.governance.service.SodService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RBAC Access-Governance P2 (plan §6.4/§6.5/§6.7) — the assurance surface: SoD rules,
 * detective scan + violation dispositions, recertification campaigns, and the
 * one-click access-evidence ZIP. Reads = IAM READ · scan/campaign creation = IAM
 * WRITE · dispositions/decisions/closure = IAM APPROVE.
 */
@RestController
@RequestMapping("/api/v1/iam/governance")
@RequiredArgsConstructor
@RequireModule("ACCESS_GOV")
@Tag(name = "IAM — Access assurance (SoD / recertification / evidence)")
public class AccessAssuranceController {

    private static final String READ_GUARD    = "hasPermission('IAM','READ')";
    private static final String WRITE_GUARD   = "hasPermission('IAM','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('IAM','APPROVE')";

    private final SodService sodService;
    private final RecertificationService recertService;
    private final AccessEvidencePackageService evidenceService;
    private final BreakGlassService breakGlassService;
    private final AccessAnalyticsService analyticsService;

    // ── SoD (§6.4) ───────────────────────────────────────────────────────

    @GetMapping("/sod/rules")
    @PreAuthorize(READ_GUARD)
    public List<SodRule> sodRules() { return sodService.listRules(); }

    @GetMapping("/sod/violations")
    @PreAuthorize(READ_GUARD)
    public List<SodViolation> sodViolations(@RequestParam(required = false) String status) {
        return sodService.listViolations(status);
    }

    @PostMapping("/sod/scan")
    @PreAuthorize(WRITE_GUARD)
    public Map<String, Integer> sodScan() {
        return Map.of("violationsRaised", sodService.scan());
    }

    @PostMapping("/sod/violations/{id}/disposition")
    @PreAuthorize(APPROVE_GUARD)
    public SodViolation disposition(@PathVariable Long id, @Valid @RequestBody SodDispositionRequest r) {
        return sodService.disposition(id, r.status(), r.note(), r.acceptedUntil(), currentUserId());
    }

    // ── recertification campaigns (§6.5) ────────────────────────────────

    @GetMapping("/campaigns")
    @PreAuthorize(READ_GUARD)
    public List<CampaignView> campaigns() { return recertService.list(); }

    @PostMapping("/campaigns")
    @PreAuthorize(WRITE_GUARD)
    public CampaignView createCampaign(@Valid @RequestBody CreateCampaignRequest r) {
        return recertService.create(r);
    }

    @GetMapping("/campaigns/{id}/items")
    @PreAuthorize(READ_GUARD)
    public List<AccessReviewItem> campaignItems(@PathVariable Long id) {
        return recertService.listItems(id);
    }

    @PostMapping("/campaigns/{id}/items/{itemId}/decision")
    @PreAuthorize(APPROVE_GUARD)
    public AccessReviewItem decide(@PathVariable Long id, @PathVariable Long itemId,
                                   @Valid @RequestBody ReviewDecisionRequest r) {
        return recertService.decide(id, itemId, r.decision(), r.note(), currentUserId());
    }

    @PostMapping("/campaigns/{id}/close")
    @PreAuthorize(APPROVE_GUARD)
    public CampaignView closeCampaign(@PathVariable Long id,
                                      @RequestBody(required = false) CloseCampaignRequest r) {
        return recertService.close(id, r == null ? null : r.exceptionNote(), currentUserId());
    }

    // ── break-glass (§6.6, P3) ───────────────────────────────────────────

    @GetMapping("/break-glass")
    @PreAuthorize(READ_GUARD)
    public List<BreakGlassEvent> breakGlassEvents() { return breakGlassService.list(); }

    @PostMapping("/break-glass")
    @PreAuthorize("hasPermission('IAM','BREAK_GLASS')")
    public BreakGlassEvent activateBreakGlass(@Valid @RequestBody BreakGlassActivateRequest r) {
        return breakGlassService.activate(r.incidentRef(), r.reason(), r.validityHours(),
            currentUserId(), currentUsername());
    }

    @PostMapping("/break-glass/{id}/review")
    @PreAuthorize(APPROVE_GUARD)
    public BreakGlassEvent reviewBreakGlass(@PathVariable Long id,
                                            @Valid @RequestBody BreakGlassReviewRequest r) {
        return breakGlassService.review(id, r.note(), currentUserId());
    }

    @PostMapping("/break-glass/expiry-sweep")
    @PreAuthorize(WRITE_GUARD)
    public Map<String, Integer> breakGlassSweep() {
        return Map.of("expired", breakGlassService.expirySweep());
    }

    // ── analytics (§6.7, P3) ─────────────────────────────────────────────

    @GetMapping("/analytics")
    @PreAuthorize(READ_GUARD)
    public AccessAnalyticsService.AccessAnalytics analytics() {
        return analyticsService.analytics();
    }

    // ── access evidence pack (§6.7) — the EXPORT 6th-action pilot gate ───

    // F2-61(c) / ADR-017 D-10c — EXPORT is a containment: the IAM:EXPORT population must be a
    // SUBSET of IAM:READ, because a file is a superset of a screen. Measured EXPORT ⊆ READ in both
    // databases before this landed, so the AND costs no live principal anything; it is here so that
    // a later grant (the way V320 Part 3 inverted BCM) cannot re-open the class.
    /** Guarded by IAM/EXPORT (not READ): the P3 EXPORT rollout ships READ-parity
     *  grants, so behaviour is unchanged until a tenant tightens the matrix. */
    @GetMapping("/evidence-package.zip")
    @PreAuthorize("hasPermission('IAM','EXPORT') and hasPermission('IAM','READ')")
    public ResponseEntity<byte[]> evidencePackage() {
        String generatedBy = currentUsername();
        AccessEvidencePackageService.Package p = evidenceService.generate(generatedBy);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + p.filename() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(p.bytes());
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUsername();
        return null;
    }
}
