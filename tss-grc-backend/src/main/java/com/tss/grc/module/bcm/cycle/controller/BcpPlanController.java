package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpSection;
import com.tss.grc.module.bcm.cycle.entity.BcpVersion;
import com.tss.grc.module.bcm.cycle.service.BcpPlanService;
import com.tss.grc.module.bcm.cycle.service.BcpPlanService.SectionDef;
import com.tss.grc.module.bcm.cycle.service.BcpPlanService.VersionDiff;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the BCP/DRP plan builder.
 * <pre>
 *   GET    /api/v1/bcm/plans[?planType=BCP|DRP][&page=&size=]
 *   GET    /api/v1/bcm/plans/section-catalogue/{type}
 *   GET    /api/v1/bcm/plans/stats
 *   GET    /api/v1/bcm/plans/{id}
 *   POST   /api/v1/bcm/plans
 *   PATCH  /api/v1/bcm/plans/{id}
 *   POST   /api/v1/bcm/plans/{id}/transition          {to: "APPROVED"}
 *   POST   /api/v1/bcm/plans/{id}/reopen
 *   POST   /api/v1/bcm/plans/generate-from-bia        {biaId, planType, titleEn?, titleAr?}
 *
 *   GET    /api/v1/bcm/plans/{id}/sections
 *   POST   /api/v1/bcm/plans/{id}/sections            (add ad-hoc section)
 *   PATCH  /api/v1/bcm/plans/sections/{sectionId}     {bodyEn?, bodyAr?}
 *
 *   GET    /api/v1/bcm/plans/{id}/versions
 *   GET    /api/v1/bcm/plans/{id}/versions/diff?from=1&to=3
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/plans")
@RequiredArgsConstructor
@Tag(name = "BCM BCP/DRP Plan Builder")
public class BcpPlanController {

    public record TransitionBody(String to) {}
    public record GenerateFromBiaBody(Long biaId, String planType, String titleEn, String titleAr) {}
    public record SectionPatchBody(String bodyEn, String bodyAr) {}

    private final BcpPlanService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<BcpPlan> list(@RequestParam(required = false) String planType,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "50") int size) {
        return service.list(planType, page, size);
    }

    @GetMapping("/section-catalogue/{type}")
    @PreAuthorize("isAuthenticated()")
    public List<SectionDef> sectionCatalogue(@PathVariable String type) {
        return "DRP".equalsIgnoreCase(type) ? BcpPlanService.DRP_SECTIONS : BcpPlanService.BCP_SECTIONS;
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> stats() { return service.stats(); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcpPlan get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BcpPlan> create(@Valid @RequestBody BcpPlan body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcpPlan update(@PathVariable Long id, @RequestBody BcpPlan body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public BcpPlan transition(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transition(id, body.to(), currentPrincipalName());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("isAuthenticated()")
    public BcpPlan reopen(@PathVariable Long id) {
        return service.reopen(id, currentPrincipalName());
    }

    @PostMapping("/generate-from-bia")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BcpPlan> generateFromBia(@RequestBody GenerateFromBiaBody body) {
        return ResponseEntity.ok(service.generateFromBia(
            body.biaId(), body.planType(), body.titleEn(), body.titleAr()));
    }

    // ─── Sections ─────────────────────────────────────────────

    @GetMapping("/{id}/sections")
    @PreAuthorize("isAuthenticated()")
    public List<BcpSection> sections(@PathVariable Long id) { return service.sectionsFor(id); }

    @PostMapping("/{id}/sections")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BcpSection> addSection(@PathVariable Long id, @RequestBody BcpSection body) {
        return ResponseEntity.ok(service.addSection(id, body));
    }

    @PatchMapping("/sections/{sectionId}")
    @PreAuthorize("isAuthenticated()")
    public BcpSection editSection(@PathVariable Long sectionId, @RequestBody SectionPatchBody body) {
        return service.updateSection(sectionId, body.bodyEn(), body.bodyAr(), currentPrincipalName());
    }

    // ─── Versions ─────────────────────────────────────────────

    @GetMapping("/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    public List<BcpVersion> versions(@PathVariable Long id) { return service.versionsFor(id); }

    @GetMapping("/{id}/versions/diff")
    @PreAuthorize("isAuthenticated()")
    public VersionDiff diff(@PathVariable Long id,
                            @RequestParam int from, @RequestParam int to) {
        return service.diff(id, from, to);
    }

    // ─── Helpers ──────────────────────────────────────────────

    private static String currentPrincipalName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return "system";
        return auth.getName();
    }
}
