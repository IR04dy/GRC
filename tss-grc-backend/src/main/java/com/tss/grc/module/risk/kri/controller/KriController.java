package com.tss.grc.module.risk.kri.controller;

import com.tss.grc.module.risk.kri.dto.KriDtos.KriBreachResponse;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDashboardSnapshot;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDefinitionRequest;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDefinitionResponse;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriHistoryPoint;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriMeasurementRequest;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriMeasurementResponse;
import com.tss.grc.module.risk.kri.service.KriBreachService;
import com.tss.grc.module.risk.kri.service.KriDashboardService;
import com.tss.grc.module.risk.kri.service.KriDefinitionService;
import com.tss.grc.module.risk.kri.service.KriMeasurementService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for the Key Risk Indicator engine — Sprint 13.
 *
 * <p>Read-side wide: every officer that contributes to the audit
 * committee pack (RISK_MANAGER, CISO, COMPLIANCE_OFFICER,
 * INTERNAL_AUDITOR, AUDITOR, BCM_MANAGER, DPO, PLATFORM_ADMIN) can
 * read KRI definitions, dashboard, history.
 *
 * <p>Write-side restricted to the four roles that own KRIs in
 * practice — RISK_MANAGER, CISO, COMPLIANCE_OFFICER, PLATFORM_ADMIN.
 * Breach acknowledgement opens to the same group.
 */
@RestController
@RequestMapping("/api/v1/risk/kri")
@RequiredArgsConstructor
@Tag(name = "KRI — Key Risk Indicators")
@SecurityRequirement(name = "bearerAuth")
public class KriController {

    private static final String READ_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','COMPLIANCE_OFFICER','INTERNAL_AUDITOR','AUDITOR','BCM_MANAGER','DPO','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','COMPLIANCE_OFFICER','PLATFORM_ADMIN')";

    private final KriDefinitionService definitionService;
    private final KriMeasurementService measurementService;
    private final KriBreachService breachService;
    private final KriDashboardService dashboardService;

    // ─── Definitions ──────────────────────────────────────────────

    @GetMapping("/definitions")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List KRI definitions for the current tenant.")
    public List<KriDefinitionResponse> listDefinitions(
            @RequestParam(value = "category", required = false) String category) {
        if (category == null || category.isBlank()) return definitionService.list();
        return definitionService.listByCategory(category);
    }

    @GetMapping("/definitions/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one KRI definition by external id.")
    public KriDefinitionResponse getDefinition(@PathVariable UUID externalId) {
        return definitionService.get(externalId);
    }

    @PostMapping("/definitions")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Create a new KRI definition.")
    public ResponseEntity<KriDefinitionResponse> createDefinition(
            @Valid @RequestBody KriDefinitionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(definitionService.create(req));
    }

    @PutMapping("/definitions/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update a KRI definition.")
    public KriDefinitionResponse updateDefinition(@PathVariable UUID externalId,
                                                  @Valid @RequestBody KriDefinitionRequest req) {
        return definitionService.update(externalId, req);
    }

    @PostMapping("/definitions/{externalId}/activate")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Activate a KRI — scheduler picks it up on the next tick.")
    public KriDefinitionResponse activate(@PathVariable UUID externalId) {
        return definitionService.activate(externalId);
    }

    @PostMapping("/definitions/{externalId}/deactivate")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Deactivate a KRI — scheduler skips it on the next tick.")
    public KriDefinitionResponse deactivate(@PathVariable UUID externalId) {
        return definitionService.deactivate(externalId);
    }

    @DeleteMapping("/definitions/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a KRI definition.")
    public void deleteDefinition(@PathVariable UUID externalId,
                                 @AuthenticationPrincipal GrcUserDetails principal) {
        definitionService.delete(externalId, principal == null ? null : principal.getUserId());
    }

    // ─── Measurements ─────────────────────────────────────────────

    @PostMapping("/{externalId}/measurements")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a manual measurement against a KRI.")
    public ResponseEntity<KriMeasurementResponse> record(
            @PathVariable UUID externalId,
            @Valid @RequestBody KriMeasurementRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(measurementService.record(externalId, req));
    }

    @GetMapping("/{externalId}/measurements")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List recent measurements for a KRI (most-recent first).")
    public List<KriMeasurementResponse> listMeasurements(@PathVariable UUID externalId,
                                                        @RequestParam(defaultValue = "90") int limit) {
        return measurementService.recent(externalId, Math.min(limit, 200));
    }

    @GetMapping("/{externalId}/history")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Time-ordered trend points for the detail-page sparkline.")
    public List<KriHistoryPoint> history(@PathVariable UUID externalId,
                                         @RequestParam(defaultValue = "90") int days) {
        return dashboardService.history(externalId, days);
    }

    // ─── Breaches ─────────────────────────────────────────────────

    @GetMapping("/breaches/open")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Currently-open KRI breaches (audit committee surface).")
    public List<KriBreachResponse> openBreaches() {
        return breachService.listOpen();
    }

    @GetMapping("/{externalId}/breaches")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Full breach history for one KRI.")
    public List<KriBreachResponse> breachesForKri(@PathVariable UUID externalId) {
        return breachService.listForKri(externalId);
    }

    @PostMapping("/breaches/{breachExternalId}/acknowledge")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Acknowledge an open KRI breach.")
    public KriBreachResponse acknowledge(@PathVariable UUID breachExternalId,
                                         @AuthenticationPrincipal GrcUserDetails principal) {
        return breachService.acknowledge(breachExternalId,
                principal == null ? null : principal.getUserId());
    }

    @PostMapping("/breaches/{breachExternalId}/close")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Manually close a KRI breach (rare — usually engine-driven).")
    public KriBreachResponse closeBreach(@PathVariable UUID breachExternalId,
                                         @AuthenticationPrincipal GrcUserDetails principal) {
        return breachService.close(breachExternalId,
                principal == null ? null : principal.getUserId());
    }

    // ─── Dashboard ────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "KPI + heatmap snapshot for the KRI dashboard.")
    public KriDashboardSnapshot dashboard() {
        return dashboardService.snapshot();
    }
}
