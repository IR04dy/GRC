package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcmBiaAssessment;
import com.tss.grc.module.bcm.cycle.service.BcmBiaAssessmentService;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.service.BcmService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the rich BIA assessment entity (audit Tier-1 #3).
 * <pre>
 *   GET    /api/v1/bcm/bia-assessments[?cycleId=]
 *   GET    /api/v1/bcm/bia-assessments/stats
 *   GET    /api/v1/bcm/bia-assessments/catalog        — time-band + dimension labels
 *   GET    /api/v1/bcm/bia-assessments/{id}
 *   POST   /api/v1/bcm/bia-assessments
 *   PATCH  /api/v1/bcm/bia-assessments/{id}
 *   POST   /api/v1/bcm/bia-assessments/{id}/transition   {to: "APPROVED"}
 *   POST   /api/v1/bcm/bia-assessments/{id}/calculate-criticality
 *   POST   /api/v1/bcm/bia-assessments/{id}/generate-bcp  {planNameEn, planNameAr, planType, version}
 *   POST   /api/v1/bcm/bia-assessments/{id}/generate-drp  {planNameEn, planNameAr, planType, version}
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/bia-assessments")
@RequiredArgsConstructor
@Tag(name = "BCM BIA Assessments")
public class BcmBiaAssessmentController {

    public record TransitionBody(String to) {}

    /**
     * Body for the "Generate BCP/DRP from BIA" buttons. Only the plan name +
     * type need to be specified by the user — the BIA's MTPD / RTO / RPO get
     * copied onto the new plan by the calling layer at materialisation time.
     */
    public record GeneratePlanBody(String planNameEn, String planNameAr, String planType, String version) {}

    /**
     * Catalog of canonical time-band labels + impact dimensions for the
     * frontend wizard. Stable; baked from {@code BcmBiaAssessmentService} constants.
     */
    public record CatalogResponse(List<String> timeBands, List<String> impactDimensions) {}

    private final BcmBiaAssessmentService service;
    private final BcmService bcmService;     // for materialising the generated BCP

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<BcmBiaAssessment> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return service.list(page, size);
    }

    @GetMapping("/by-cycle/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public List<BcmBiaAssessment> byCycle(@PathVariable Long cycleId) {
        return service.byCycle(cycleId);
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> stats() { return service.stats(); }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public CatalogResponse catalog() {
        return new CatalogResponse(BcmBiaAssessmentService.TIME_BANDS,
                                   BcmBiaAssessmentService.IMPACT_DIMENSIONS);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmBiaAssessment get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BcmBiaAssessment> create(@Valid @RequestBody BcmBiaAssessment body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmBiaAssessment update(@PathVariable Long id, @RequestBody BcmBiaAssessment body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public BcmBiaAssessment transition(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transition(id, body.to());
    }

    @PostMapping("/{id}/calculate-criticality")
    @PreAuthorize("isAuthenticated()")
    public BcmBiaAssessment calculateCriticality(@PathVariable Long id) {
        return service.calculateCriticality(id);
    }

    /**
     * Generate a BCP from this BIA: requires the BIA to be APPROVED / ACTIVE.
     * Creates a {@link BcmPlan} (legacy entity, status DRAFT) seeded with the
     * BIA's metadata and stamps the BIA's {@code generatedBcpId}. Returns both.
     */
    @PostMapping("/{id}/generate-bcp")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> generateBcp(@PathVariable Long id, @RequestBody GeneratePlanBody body) {
        BcmBiaAssessment bia = service.get(id);
        if (!"APPROVED".equals(bia.getApprovalStatus()) && !"ACTIVE".equals(bia.getApprovalStatus()))
            throw new IllegalArgumentException(
                "BIA must be APPROVED or ACTIVE to generate a BCP (current=" + bia.getApprovalStatus() + ")");

        BcmPlan req = new BcmPlan();
        req.setPlanNameEn(body.planNameEn() != null && !body.planNameEn().isBlank()
            ? body.planNameEn() : ("BCP from " + bia.getBiaCode()));
        req.setPlanNameAr(body.planNameAr());
        req.setPlanType(body.planType() != null ? body.planType() : "BUSINESS_CONTINUITY");
        req.setVersion(body.version() != null ? body.version() : "1.0");
        req.setStatus("DRAFT");
        BcmPlan created = bcmService.createPlan(req);

        BcmBiaAssessment updated = service.markBcpGenerated(id, created.getId());
        return Map.of("bia", updated, "bcp", created);
    }

    /**
     * Generate a DRP placeholder from this BIA. Same gate as BCP. Today this
     * also creates a {@link BcmPlan} with {@code planType="DISASTER_RECOVERY"}
     * — the dedicated {@code DisasterRecoveryPlan} table is the right home
     * eventually, but it requires the much-richer DRP wizard which is a
     * separate audit-Tier-2 item.
     */
    @PostMapping("/{id}/generate-drp")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> generateDrp(@PathVariable Long id, @RequestBody GeneratePlanBody body) {
        BcmBiaAssessment bia = service.get(id);
        if (!"APPROVED".equals(bia.getApprovalStatus()) && !"ACTIVE".equals(bia.getApprovalStatus()))
            throw new IllegalArgumentException(
                "BIA must be APPROVED or ACTIVE to generate a DRP (current=" + bia.getApprovalStatus() + ")");

        BcmPlan req = new BcmPlan();
        req.setPlanNameEn(body.planNameEn() != null && !body.planNameEn().isBlank()
            ? body.planNameEn() : ("DRP from " + bia.getBiaCode()));
        req.setPlanNameAr(body.planNameAr());
        req.setPlanType("DISASTER_RECOVERY");
        req.setVersion(body.version() != null ? body.version() : "1.0");
        req.setStatus("DRAFT");
        BcmPlan created = bcmService.createPlan(req);

        BcmBiaAssessment updated = service.markDrpGenerated(id, created.getId());
        return Map.of("bia", updated, "drp", created);
    }
}
