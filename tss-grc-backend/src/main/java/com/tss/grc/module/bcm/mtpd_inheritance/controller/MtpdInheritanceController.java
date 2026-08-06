package com.tss.grc.module.bcm.mtpd_inheritance.controller;

import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.AssessmentDetailResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.AssessmentResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.CapaCreateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.CapaCreateResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.ComputeRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.FindingResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.PublishRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.service.MtpdInheritanceService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Slice 10-E — REST surface for vendor MTPD inheritance assessments.
 *
 * <pre>
 *   POST   /api/v1/bcm/mtpd-inheritance/assessments              — compute
 *   GET    /api/v1/bcm/mtpd-inheritance/assessments              — list
 *   GET    /api/v1/bcm/mtpd-inheritance/assessments/{id}         — detail (header + findings)
 *   POST   /api/v1/bcm/mtpd-inheritance/assessments/{id}/publish — publish for audit
 *   POST   /api/v1/bcm/mtpd-inheritance/findings/{id}/capa       — create CAPA remediation
 *   GET    /api/v1/bcm/mtpd-inheritance/processes/{processId}/history — per-process trend
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/mtpd-inheritance")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Vendor MTPD Inheritance (Slice 10-E)")
@SecurityRequirement(name = "bearerAuth")
public class MtpdInheritanceController {

    private final MtpdInheritanceService service;

    // ─── Assessments ────────────────────────────────────────────────

    @PostMapping("/assessments")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','RISK_MANAGER','CISO','PLATFORM_ADMIN')")
    public AssessmentResponse compute(@RequestBody @Valid ComputeRequest req) {
        return service.computeAssessment(req);
    }

    @GetMapping("/assessments")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','RISK_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN')")
    public List<AssessmentResponse> list() {
        return service.listAssessments();
    }

    @GetMapping("/assessments/{id}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','RISK_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN')")
    public AssessmentDetailResponse detail(@PathVariable Long id) {
        return service.getAssessmentDetail(id);
    }

    @PostMapping("/assessments/{id}/publish")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN')")
    public AssessmentResponse publish(@PathVariable Long id,
                                       @RequestBody(required = false) @Valid PublishRequest req) {
        return service.publishAssessment(id, req);
    }

    // ─── Findings ──────────────────────────────────────────────────

    @PostMapping("/findings/{id}/capa")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','RISK_MANAGER','PLATFORM_ADMIN')")
    public CapaCreateResponse createCapa(@PathVariable Long id,
                                          @RequestBody(required = false) @Valid CapaCreateRequest req) {
        return service.createCapaForFinding(id, req);
    }

    @GetMapping("/processes/{processId}/history")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','RISK_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN')")
    public List<FindingResponse> processHistory(@PathVariable Long processId) {
        return service.getProcessBreachHistory(processId);
    }
}
