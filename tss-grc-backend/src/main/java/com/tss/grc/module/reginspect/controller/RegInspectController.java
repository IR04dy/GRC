package com.tss.grc.module.reginspect.controller;

import com.tss.grc.module.reginspect.dto.RegInspectDtos.CreateDocumentRequestRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.CreateInspectionRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.DashboardResponse;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.DocumentRequestResponse;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.InspectionResponse;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.InspectionUpdateResponse;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.SubmitDocumentRequestRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.TransitionRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.UpdateInspectionRequest;
import com.tss.grc.module.reginspect.service.RegInspectService;
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
 * REST surface for the Sprint 15 Regulatory Inspection Workspace.
 * Tenant-scoped via JWT-derived {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/v1/reginspect")
@RequiredArgsConstructor
@Tag(name = "Regulatory Inspection Workspace (Sprint 15)")
@SecurityRequirement(name = "bearerAuth")
public class RegInspectController {

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER',"
            + "'INTERNAL_AUDITOR','BCM_MANAGER','BOARD_SECRETARY')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER')";
    private static final String TRANSITION_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER')";

    private final RegInspectService service;

    // ─── Inspection lifecycle ───────────────────────────────────────

    @PostMapping("/inspections")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Open a new regulatory inspection workspace.")
    public ResponseEntity<InspectionResponse> create(
            @Valid @RequestBody CreateInspectionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createInspection(req));
    }

    @GetMapping("/inspections")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Paginated list of inspections for the current tenant.")
    public Page<InspectionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listInspections(page, Math.min(size, 200));
    }

    @GetMapping("/inspections/{id}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get a single inspection.")
    public InspectionResponse get(@PathVariable Long id) {
        return service.getInspection(id);
    }

    @PutMapping("/inspections/{id}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update inspection metadata.")
    public InspectionResponse update(@PathVariable Long id,
                                     @Valid @RequestBody UpdateInspectionRequest req) {
        return service.updateInspection(id, req);
    }

    @PostMapping("/inspections/{id}/transition")
    @PreAuthorize(TRANSITION_GUARD)
    @Operation(summary = "Move inspection to next status in lifecycle.")
    public InspectionResponse transition(@PathVariable Long id,
                                          @Valid @RequestBody TransitionRequest req) {
        return service.transition(id, req);
    }

    // ─── Document requests ──────────────────────────────────────────

    @PostMapping("/inspections/{id}/document-requests")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Raise a new document request on the inspection.")
    public DocumentRequestResponse createDr(@PathVariable Long id,
                                             @Valid @RequestBody CreateDocumentRequestRequest req) {
        return service.createDocumentRequest(id, req);
    }

    @GetMapping("/inspections/{id}/document-requests")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List document requests for the inspection.")
    public List<DocumentRequestResponse> listDr(@PathVariable Long id) {
        return service.listDocumentRequests(id);
    }

    @PostMapping("/document-requests/{drId}/submit")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Submit a document request (records evidence + notes).")
    public DocumentRequestResponse submitDr(@PathVariable Long drId,
                                             @Valid @RequestBody SubmitDocumentRequestRequest req) {
        return service.submitDocumentRequest(drId, req);
    }

    // ─── Timeline + dashboard ──────────────────────────────────────

    @GetMapping("/inspections/{id}/timeline")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Chronological timeline of inspection events.")
    public List<InspectionUpdateResponse> timeline(@PathVariable Long id) {
        return service.listTimeline(id);
    }

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Workspace dashboard — KPIs + recent inspections.")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
