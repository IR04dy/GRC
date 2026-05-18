package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcmComplianceMapping;
import com.tss.grc.module.bcm.cycle.service.BcmComplianceMappingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for BCM compliance mapping.
 * <pre>
 *   GET    /api/v1/bcm/compliance[?framework=ISO_22301]
 *   GET    /api/v1/bcm/compliance/coverage
 *   GET    /api/v1/bcm/compliance/stats
 *   GET    /api/v1/bcm/compliance/{id}
 *   POST   /api/v1/bcm/compliance
 *   PATCH  /api/v1/bcm/compliance/{id}
 *   POST   /api/v1/bcm/compliance/{id}/attach-evidence  {evidenceId: 42}
 *   POST   /api/v1/bcm/compliance/{id}/assess          {status, assessedBy, notes}
 *   POST   /api/v1/bcm/compliance/{id}/attach-gap      {gapId: 17}
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/compliance")
@RequiredArgsConstructor
@Tag(name = "BCM Compliance Mapping")
public class BcmComplianceMappingController {

    public record AttachEvidenceBody(Long evidenceId) {}
    public record AssessBody(String status, String assessedBy, String notes) {}
    public record AttachGapBody(Long gapId) {}

    private final BcmComplianceMappingService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BcmComplianceMapping> list(@RequestParam(required = false) String framework) {
        return service.list(framework);
    }

    @GetMapping("/coverage")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Map<String, Long>> coverage() { return service.coverageByFramework(); }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> stats() { return service.overallStats(); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmComplianceMapping get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BcmComplianceMapping> create(@Valid @RequestBody BcmComplianceMapping body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmComplianceMapping update(@PathVariable Long id, @RequestBody BcmComplianceMapping body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/attach-evidence")
    @PreAuthorize("isAuthenticated()")
    public BcmComplianceMapping attachEvidence(@PathVariable Long id, @RequestBody AttachEvidenceBody body) {
        return service.attachEvidence(id, body.evidenceId());
    }

    @PostMapping("/{id}/assess")
    @PreAuthorize("isAuthenticated()")
    public BcmComplianceMapping assess(@PathVariable Long id, @RequestBody AssessBody body) {
        return service.assessCompliance(id, body.status(), body.assessedBy(), body.notes());
    }

    @PostMapping("/{id}/attach-gap")
    @PreAuthorize("isAuthenticated()")
    public BcmComplianceMapping attachGap(@PathVariable Long id, @RequestBody AttachGapBody body) {
        return service.attachGap(id, body.gapId());
    }
}
