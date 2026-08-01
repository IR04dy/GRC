package com.tss.grc.module.compliance.controller;

import com.tss.grc.module.compliance.dto.*;
import com.tss.grc.module.compliance.service.ComplianceService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@RequireModule("COMPLIANCE")
@Tag(name = "Compliance")
@SecurityRequirement(name = "bearerAuth")
public class ControlController {

    private final ComplianceService complianceService;

    // ── Controls ──────────────────────────────────────────────────────────────

    @GetMapping("/controls")
    @PreAuthorize("hasPermission('COMPLIANCE', 'READ')")
    @Operation(summary = "List controls with optional framework/status/search filters")
    public Page<ControlResponse> listControls(
        @RequestParam(required = false) String framework,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String q,
        @PageableDefault(size = 20, sort = "code") Pageable pageable
    ) {
        return complianceService.listControls(framework, status, q, pageable);
    }

    @GetMapping("/controls/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE', 'READ')")
    public ControlResponse getControl(@PathVariable Long id) {
        return complianceService.getControl(id);
    }

    @PostMapping("/controls")
    @PreAuthorize("hasPermission('COMPLIANCE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new control")
    public ControlResponse createControl(@Valid @RequestBody ControlRequest request) {
        return complianceService.createControl(request);
    }

    @PutMapping("/controls/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE', 'WRITE')")
    public ControlResponse updateControl(@PathVariable Long id, @Valid @RequestBody ControlRequest request) {
        return complianceService.updateControl(id, request);
    }

    @DeleteMapping("/controls/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteControl(@PathVariable Long id) {
        complianceService.deleteControl(id);
    }

    // ── Assessments ───────────────────────────────────────────────────────────

    @GetMapping("/assessments")
    @PreAuthorize("hasPermission('COMPLIANCE', 'READ')")
    public Page<AssessmentResponse> listAssessments(@PageableDefault(size = 20) Pageable pageable) {
        return complianceService.listAssessments(pageable);
    }

    @PostMapping("/assessments")
    @PreAuthorize("hasPermission('COMPLIANCE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentResponse createAssessment(@Valid @RequestBody AssessmentRequest request) {
        return complianceService.createAssessment(request);
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE', 'WRITE')")
    public AssessmentResponse updateAssessment(@PathVariable Long id, @Valid @RequestBody AssessmentRequest request) {
        return complianceService.updateAssessment(id, request);
    }

    // ── Findings ──────────────────────────────────────────────────────────────

    @GetMapping("/findings")
    @PreAuthorize("hasPermission('COMPLIANCE', 'READ')")
    public Page<FindingResponse> listFindings(
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return complianceService.listFindings(severity, status, pageable);
    }

    @PostMapping("/findings")
    @PreAuthorize("hasPermission('COMPLIANCE', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public FindingResponse createFinding(@Valid @RequestBody FindingRequest request) {
        return complianceService.createFinding(request);
    }

    @PutMapping("/findings/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE', 'WRITE')")
    public FindingResponse updateFinding(@PathVariable Long id, @Valid @RequestBody FindingRequest request) {
        return complianceService.updateFinding(id, request);
    }

    @DeleteMapping("/findings/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFinding(@PathVariable Long id) {
        complianceService.deleteFinding(id);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('COMPLIANCE', 'READ')")
    @Operation(summary = "Get compliance scorecard stats")
    public ComplianceStats getStats() {
        return complianceService.getStats();
    }
}
