package com.tss.grc.module.opres.controller;

import com.tss.grc.module.opres.dto.OpResDtos.AssessmentResponse;
import com.tss.grc.module.opres.dto.OpResDtos.CellResponse;
import com.tss.grc.module.opres.dto.OpResDtos.CellUpsertRequest;
import com.tss.grc.module.opres.dto.OpResDtos.CreateAssessmentRequest;
import com.tss.grc.module.opres.dto.OpResDtos.PillarResponse;
import com.tss.grc.module.opres.dto.OpResDtos.UpdateAssessmentRequest;
import com.tss.grc.module.opres.service.OpResService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for the Sprint 14 PR #8 Operational Resilience
 * self-assessment module. All endpoints are tenant-scoped via
 * the JWT-derived {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/v1/opres")
@RequiredArgsConstructor
@Tag(name = "Operational Resilience Self-Assessment (Sprint 14)")
@SecurityRequirement(name = "bearerAuth")
public class OpResController {

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER',"
            + "'INTERNAL_AUDITOR','BCM_MANAGER','BOARD_SECRETARY')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','BCM_MANAGER')";
    private static final String APPROVE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','BOARD_SECRETARY','RISK_MANAGER')";

    private final OpResService service;

    // ─── Pillars (read-only catalog) ────────────────────────────────

    @GetMapping("/pillars")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List active SAMA Op-Res 2024 pillars visible to the tenant.")
    public List<PillarResponse> listPillars() {
        return service.listPillars();
    }

    // ─── Assessment lifecycle ───────────────────────────────────────

    @PostMapping("/assessments")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Open a new annual or ad-hoc Op-Res assessment.")
    public ResponseEntity<AssessmentResponse> createAssessment(@Valid @RequestBody CreateAssessmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAssessment(req));
    }

    @GetMapping("/assessments")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Paginated list of assessments for the current tenant, newest first.")
    public Page<AssessmentResponse> listAssessments(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "25") int size) {
        return service.listAssessments(page, size);
    }

    @GetMapping("/assessments/{id}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get a single assessment with all 5 pillar cells.")
    public AssessmentResponse getAssessment(@PathVariable Long id) {
        return service.getAssessment(id);
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update assessment metadata (title / due date / sponsor / exec summary).")
    public AssessmentResponse updateAssessment(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateAssessmentRequest req) {
        return service.updateAssessment(id, req);
    }

    @PostMapping("/assessments/{id}/cells")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Upsert a pillar cell (current+target maturity + rationale + evidence). "
            + "Moves DRAFT→IN_PROGRESS on first write.")
    public CellResponse upsertCell(@PathVariable Long id, @Valid @RequestBody CellUpsertRequest req) {
        return service.upsertCell(id, req);
    }

    @PostMapping("/assessments/{id}/submit")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Submit assessment for board / CRO sign-off (IN_PROGRESS → APPROVED).")
    public AssessmentResponse submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/assessments/{id}/approve")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Approve a submitted assessment (records approver + timestamp).")
    public AssessmentResponse approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/assessments/{id}/close")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Close the assessment (terminal). Allowed from APPROVED or DRAFT (abandonment).")
    public AssessmentResponse close(@PathVariable Long id) {
        return service.close(id);
    }
}
