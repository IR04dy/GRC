package com.tss.grc.module.compliance.controller;

import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.policy.entity.PolicyDocumentFrameworkMapping;
import com.tss.grc.module.compliance.service.FrameworkAnchorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Framework-anchored views over compliance entities (IA-1).
 *
 * Pattern: the artefact (a Policy) is the primary entity, frameworks are
 * sheets/tabs you can switch between. Each policy can satisfy multiple
 * frameworks via {@code compliance.policy_framework_mappings}.
 *
 * - GET    /api/v1/compliance/frameworks                         → dictionary
 * - GET    /api/v1/compliance/frameworks/{id}/policies           → policies anchored on a framework
 * - GET    /api/v1/compliance/policies/{id}/mappings             → mappings for one policy
 * - POST   /api/v1/compliance/policies/{id}/mappings             → add mapping
 * - PUT    /api/v1/compliance/policies/mappings/{mappingId}      → update mapping
 * - DELETE /api/v1/compliance/policies/mappings/{mappingId}      → remove mapping
 * - GET    /api/v1/compliance/frameworks/coverage                → coverage stats per framework
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance — Framework anchor (Policies)")
@SecurityRequirement(name = "bearerAuth")
public class FrameworkAnchorController {

    private final FrameworkAnchorService service;

    @GetMapping("/frameworks")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<Framework> frameworks() {
        return service.frameworks();
    }

    @GetMapping("/frameworks/{id}/policies")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "All policies that satisfy a given framework, with their clause refs.")
    public List<Map<String, Object>> policiesForFramework(@PathVariable Long id) {
        return service.policiesForFramework(id);
    }

    @GetMapping("/policies/{id}/mappings")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<Map<String, Object>> mappingsForPolicy(@PathVariable Long id) {
        return service.mappingsForPolicy(id);
    }

    @PostMapping("/policies/{id}/mappings")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyDocumentFrameworkMapping addMapping(@PathVariable Long id, @RequestBody PolicyDocumentFrameworkMapping body) {
        return service.addMapping(id, body);
    }

    @PutMapping("/policies/mappings/{mappingId}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyDocumentFrameworkMapping updateMapping(@PathVariable Long mappingId, @RequestBody PolicyDocumentFrameworkMapping body) {
        return service.updateMapping(mappingId, body);
    }

    @DeleteMapping("/policies/mappings/{mappingId}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMapping(@PathVariable Long mappingId) {
        service.deleteMapping(mappingId);
    }

    @GetMapping("/frameworks/coverage")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "Per-framework coverage: policy count + breakdown of satisfaction levels")
    public List<Map<String, Object>> coverage() {
        return service.coverage();
    }

    @GetMapping("/frameworks/{id}/controls")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "All controls anchored on a given framework (IA-3).")
    public List<Map<String, Object>> controlsForFramework(@PathVariable Long id) {
        return service.controlsForFramework(id);
    }

    @GetMapping("/frameworks/{id}/kris")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "All KRIs anchored on a given framework (IA-5).")
    public List<Map<String, Object>> krisForFramework(@PathVariable Long id) {
        return service.krisForFramework(id);
    }

    @GetMapping("/frameworks/{id}/risks")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "All risks anchored on a given framework (IA-5). " +
        "Returns risks where EITHER risk_framework_id OR compliance_framework_id matches.")
    public List<Map<String, Object>> risksForFramework(@PathVariable Long id) {
        return service.risksForFramework(id);
    }

    @GetMapping("/frameworks/{id}/dpias")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    @Operation(summary = "All DPIAs anchored on a given framework (IA-4).")
    public List<Map<String, Object>> dpiasForFramework(@PathVariable Long id) {
        return service.dpiasForFramework(id);
    }

    @GetMapping("/frameworks/{id}/findings")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "All audit findings raised under a given framework (IA-2).")
    public List<Map<String, Object>> findingsForFramework(@PathVariable Long id) {
        return service.findingsForFramework(id);
    }
}
