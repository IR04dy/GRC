package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmBiaAssessment;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpPlanReviewAttestation;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.bcm.cycle.service.PlanReviewService;
import com.tss.grc.module.bcm.cycle.service.PlanReviewService.SweepResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * REST surface for BCP/DRP plan review attestation — audit Tier-2 #10.
 *
 * <pre>
 *   GET    /api/v1/bcm/plans/review/queue?withinDays=30
 *   GET    /api/v1/bcm/plans/{id}/attestations          history for one plan
 *   POST   /api/v1/bcm/plans/{id}/attest                owner attestation
 *   POST   /api/v1/bcm/plans/review/sweep               admin / scheduler sweep
 * </pre>
 *
 * <p>"Critical-tier" cadence (6-month default vs 12-month default) is
 * resolved here by looking up the source BIA's {@code overallCriticality};
 * SOVEREIGN or CRITICAL → critical cadence.
 */
@RestController
@RequestMapping("/api/v1/bcm/plans")
@RequiredArgsConstructor
@Tag(name = "BCM — Plan review attestation (Tier-2 #10)")
public class PlanReviewController {

    private static final Set<String> CRITICAL_TIERS = Set.of("CRITICAL", "SOVEREIGN");

    private final PlanReviewService service;
    private final BcpPlanRepository plans;
    private final BcmBiaAssessmentRepository bias;

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    public record AttestBody(String outcome, String notes) {}

    @GetMapping("/review/queue")
    @PreAuthorize("isAuthenticated()")
    public List<BcpPlan> queue(@RequestParam(defaultValue = "30") int withinDays) {
        return service.reviewQueue(withinDays);
    }

    @GetMapping("/{id}/attestations")
    @PreAuthorize("isAuthenticated()")
    public List<BcpPlanReviewAttestation> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PostMapping("/{id}/attest")
    @PreAuthorize("isAuthenticated()")
    public BcpPlanReviewAttestation attest(@PathVariable Long id,
                                           @RequestBody(required = false) AttestBody body) {
        boolean isCritical = isCriticalTier(id);
        return service.attest(id,
            body == null ? null : body.outcome(),
            body == null ? null : body.notes(),
            actor(),
            isCritical);
    }

    @PostMapping("/review/sweep")
    @PreAuthorize("isAuthenticated()")
    public SweepResult sweep() { return service.sweep(); }

    /**
     * Whether a plan is "critical-tier" for cadence purposes — resolved by
     * looking up its source BIA's overall criticality. Falls back to false
     * (default 12-month cadence) when the plan wasn't BIA-generated or the
     * BIA was deleted.
     */
    private boolean isCriticalTier(Long planId) {
        Long org = TenantContext.getCurrentTenant();
        BcpPlan p = plans.findByIdAndOrganisationId(planId, org).orElse(null);
        if (p == null || p.getSourceBiaId() == null) return false;
        BcmBiaAssessment b = bias.findByIdAndOrganisationId(p.getSourceBiaId(), org).orElse(null);
        return b != null && CRITICAL_TIERS.contains(b.getOverallCriticality());
    }
}
