package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpSection;
import com.tss.grc.module.bcm.cycle.entity.BcpVersion;
import com.tss.grc.module.bcm.cycle.service.BcpPlanPdfExporter;
import com.tss.grc.module.bcm.cycle.service.BcpPlanService;
import com.tss.grc.module.bcm.cycle.service.BcpPlanService.SectionDef;
import com.tss.grc.module.bcm.cycle.service.BcpPlanService.VersionDiff;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    private final BcpPlanService     service;
    private final BcpPlanPdfExporter pdfExporter;

    @GetMapping
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Page<BcpPlan> list(@RequestParam(required = false) String planType,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "50") int size) {
        return service.list(planType, page, Math.min(size, 200));
    }

    @GetMapping("/section-catalogue/{type}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<SectionDef> sectionCatalogue(@PathVariable String type) {
        return "DRP".equalsIgnoreCase(type) ? BcpPlanService.DRP_SECTIONS : BcpPlanService.BCP_SECTIONS;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Map<String, Long> stats() { return service.stats(); }

    /**
     * Tier-2 #12 — PDF export of an approved/published plan. Returns the
     * bytes as a download with a filename matching the plan code so the
     * browser saves "BCP-2026-000001.pdf" rather than "export".
     */
    // F2-61: EXPORT is composed with READ — see BcmEvidencePackageController for the measurement
    // and ADR-017 D-10c for the rule. Before this, so_officer (CISO, holds BCM:EXPORT via V320
    // Part 3 and no BCM:READ) was refused GET /api/v1/bcm/plans with 403 and served this same
    // plan as 38,043 bytes of PDF with 200.
    @GetMapping("/{id}/export.pdf")
    @PreAuthorize("hasPermission('BCM','EXPORT') and hasPermission('BCM','READ')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id,
                                            @RequestParam(defaultValue = "en") String lang) {
        BcpPlan p = service.get(id);
        byte[] pdf = pdfExporter.export(id, lang);
        String filename = (p.getPlanCode() == null ? "plan-" + id : p.getPlanCode()) + ".pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(pdf);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcpPlan get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<BcpPlan> create(@Valid @RequestBody BcpPlan body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcpPlan update(@PathVariable Long id, @RequestBody BcpPlan body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcpPlan transition(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transition(id, body.to(), currentPrincipalName());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcpPlan reopen(@PathVariable Long id) {
        return service.reopen(id, currentPrincipalName());
    }

    @PostMapping("/generate-from-bia")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<BcpPlan> generateFromBia(@RequestBody GenerateFromBiaBody body) {
        return ResponseEntity.ok(service.generateFromBia(
            body.biaId(), body.planType(), body.titleEn(), body.titleAr()));
    }

    // ─── Sections ─────────────────────────────────────────────

    @GetMapping("/{id}/sections")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BcpSection> sections(@PathVariable Long id) { return service.sectionsFor(id); }

    @PostMapping("/{id}/sections")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public ResponseEntity<BcpSection> addSection(@PathVariable Long id, @RequestBody BcpSection body) {
        return ResponseEntity.ok(service.addSection(id, body));
    }

    @PatchMapping("/sections/{sectionId}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcpSection editSection(@PathVariable Long sectionId, @RequestBody SectionPatchBody body) {
        return service.updateSection(sectionId, body.bodyEn(), body.bodyAr(), currentPrincipalName());
    }

    // ─── Versions ─────────────────────────────────────────────

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BcpVersion> versions(@PathVariable Long id) { return service.versionsFor(id); }

    @GetMapping("/{id}/versions/diff")
    @PreAuthorize("hasPermission('BCM', 'READ')")
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
