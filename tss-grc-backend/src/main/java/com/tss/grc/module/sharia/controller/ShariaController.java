package com.tss.grc.module.sharia.controller;

import com.tss.grc.module.sharia.dto.ShariaDtos.AuditFindingResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.CreateResolutionRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.CreateScholarRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.DashboardResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.DeclareCoiRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.DetectNcEventRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.EventResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.NcEventResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.ProductResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.PurifyNcEventRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.RaiseAuditFindingRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.ResolutionResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.ScholarResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.SubmitProductRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionAuditFindingRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionNcEventRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionProductRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionResolutionRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.UpdateScholarRequest;
import com.tss.grc.module.sharia.service.ShariaService;
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
 * REST surface for the Sprint 20 Sharia Governance &amp; Audit module.
 */
@RestController
@RequestMapping("/api/v1/sharia")
@RequiredArgsConstructor
@Tag(name = "Sharia Governance & Audit (Sprint 20)")
@SecurityRequirement(name = "bearerAuth")
public class ShariaController {

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER',"
            + "'INTERNAL_AUDITOR','CRO','SHARIA_OFFICER','SHARIA_AUDITOR')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','SHARIA_OFFICER',"
            + "'SHARIA_AUDITOR')";
    private static final String APPROVE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','SHARIA_OFFICER',"
            + "'SHARIA_BOARD_CHAIR','CRO')";

    private final ShariaService service;

    // ─── Scholars ──────────────────────────────────────────────────

    @PostMapping("/scholars")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Register a new SSB scholar.")
    public ResponseEntity<ScholarResponse> createScholar(
            @Valid @RequestBody CreateScholarRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createScholar(req));
    }

    @GetMapping("/scholars")
    @PreAuthorize(READ_GUARD)
    public Page<ScholarResponse> listScholars(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listScholars(page, Math.min(size, 200));
    }

    @GetMapping("/scholars/{id}")
    @PreAuthorize(READ_GUARD)
    public ScholarResponse getScholar(@PathVariable Long id) { return service.getScholar(id); }

    @PutMapping("/scholars/{id}")
    @PreAuthorize(WRITE_GUARD)
    public ScholarResponse updateScholar(@PathVariable Long id,
                                          @Valid @RequestBody UpdateScholarRequest req) {
        return service.updateScholar(id, req);
    }

    @PostMapping("/scholars/{id}/coi")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record the scholar's annual conflict-of-interest declaration.")
    public ScholarResponse declareCoi(@PathVariable Long id,
                                       @Valid @RequestBody DeclareCoiRequest req) {
        return service.declareCoi(id, req);
    }

    // ─── Resolutions ───────────────────────────────────────────────

    @PostMapping("/resolutions")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Draft a new SSB resolution.")
    public ResponseEntity<ResolutionResponse> createResolution(
            @Valid @RequestBody CreateResolutionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createResolution(req));
    }

    @GetMapping("/resolutions")
    @PreAuthorize(READ_GUARD)
    public Page<ResolutionResponse> listResolutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listResolutions(page, Math.min(size, 200));
    }

    @GetMapping("/resolutions/{id}")
    @PreAuthorize(READ_GUARD)
    public ResolutionResponse getResolution(@PathVariable Long id) {
        return service.getResolution(id);
    }

    @PostMapping("/resolutions/{id}/transition")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Move a resolution through the SSB approval pipeline.")
    public ResolutionResponse transitionResolution(@PathVariable Long id,
                                                    @Valid @RequestBody TransitionResolutionRequest req) {
        return service.transitionResolution(id, req);
    }

    @GetMapping("/resolutions/{id}/events")
    @PreAuthorize(READ_GUARD)
    public List<EventResponse> listResolutionEvents(@PathVariable Long id) {
        return service.listResolutionEvents(id);
    }

    // ─── Audit Findings ────────────────────────────────────────────

    @PostMapping("/audit-findings")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Raise a new Internal Sharia Audit finding.")
    public AuditFindingResponse raiseFinding(@Valid @RequestBody RaiseAuditFindingRequest req) {
        return service.raiseFinding(req);
    }

    @GetMapping("/audit-findings")
    @PreAuthorize(READ_GUARD)
    public Page<AuditFindingResponse> listFindings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listFindings(page, Math.min(size, 200));
    }

    @PostMapping("/audit-findings/{id}/transition")
    @PreAuthorize(WRITE_GUARD)
    public AuditFindingResponse transitionFinding(@PathVariable Long id,
                                                    @Valid @RequestBody TransitionAuditFindingRequest req) {
        return service.transitionFinding(id, req);
    }

    // ─── Non-Compliance Events ─────────────────────────────────────

    @PostMapping("/nc-events")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Detect a Sharia non-compliance event (SNCI captured).")
    public NcEventResponse detectNcEvent(@Valid @RequestBody DetectNcEventRequest req) {
        return service.detectNcEvent(req);
    }

    @GetMapping("/nc-events")
    @PreAuthorize(READ_GUARD)
    public Page<NcEventResponse> listNcEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listNcEvents(page, Math.min(size, 200));
    }

    @PostMapping("/nc-events/{id}/transition")
    @PreAuthorize(WRITE_GUARD)
    public NcEventResponse transitionNcEvent(@PathVariable Long id,
                                              @Valid @RequestBody TransitionNcEventRequest req) {
        return service.transitionNcEvent(id, req);
    }

    @PostMapping("/nc-events/{id}/purify")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Record SNCI purification (charity transfer + receipt).")
    public NcEventResponse purifyNcEvent(@PathVariable Long id,
                                          @Valid @RequestBody PurifyNcEventRequest req) {
        return service.purifyNcEvent(id, req);
    }

    // ─── Product Approvals ─────────────────────────────────────────

    @PostMapping("/product-approvals")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Submit a new Islamic-banking product for SSB approval.")
    public ProductResponse submitProduct(@Valid @RequestBody SubmitProductRequest req) {
        return service.submitProduct(req);
    }

    @GetMapping("/product-approvals")
    @PreAuthorize(READ_GUARD)
    public Page<ProductResponse> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listProducts(page, Math.min(size, 200));
    }

    @PostMapping("/product-approvals/{id}/transition")
    @PreAuthorize(APPROVE_GUARD)
    public ProductResponse transitionProduct(@PathVariable Long id,
                                              @Valid @RequestBody TransitionProductRequest req) {
        return service.transitionProduct(id, req);
    }

    // ─── Dashboard ─────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Sharia governance dashboard — SSB + resolutions + audit + SNCI.")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
