package com.tss.grc.module.platform.regulatory.controller;

import com.tss.grc.module.platform.regulatory.service.CompliancePostureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only views over the spine — feeds F5 per-domain Regulatory
 * Alignment tabs and F6 Compliance Posture Hub.
 *
 * <pre>
 *   GET /api/v1/regulatory/citations/:id                    citation + parent doc
 *   GET /api/v1/regulatory/authority-documents/:id/coverage per-doc coverage
 *   GET /api/v1/regulatory/posture?family=…                 heatmap (rows = docs)
 *   GET /api/v1/regulatory/domain-alignment?domain=…&family=… per-domain rollup
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/regulatory")
@RequiredArgsConstructor
@Tag(name = "Regulatory — Posture (F5+F6)")
public class CompliancePostureController {

    private final CompliancePostureService service;

    @GetMapping("/citations/{id}")
    @PreAuthorize("isAuthenticated()")
    public CompliancePostureService.CitationView getCitation(@PathVariable Long id) {
        return service.getCitationView(id);
    }

    @GetMapping("/authority-documents/{id}/coverage")
    @PreAuthorize("isAuthenticated()")
    public CompliancePostureService.DocumentCoverage coverage(@PathVariable Long id) {
        return service.coverageFor(id);
    }

    @GetMapping("/posture")
    @PreAuthorize("isAuthenticated()")
    public CompliancePostureService.PostureHeatmap posture(@RequestParam(required = false) String family) {
        return service.heatmap(family);
    }

    @GetMapping("/domain-alignment")
    @PreAuthorize("isAuthenticated()")
    public CompliancePostureService.DomainAlignment domainAlignment(
            @RequestParam String domain,
            @RequestParam(required = false) List<String> family) {
        return service.domainAlignment(domain, family);
    }
}
