package com.tss.grc.module.sust.climate.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FeLifecycleTransitionRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTargetRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTargetResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTotal;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TargetProgress;
import com.tss.grc.module.sust.climate.service.FinancedEmissionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for Scope-3 Cat-15 (Financed Emissions) records and
 * net-zero / interim targets — Sprint 11.
 */
@RestController
// S-M — canonical /api/v1/sustainability/*; legacy /api/v1/climate/* kept as alias.
@RequestMapping({"/api/v1/sustainability/financed-emissions", "/api/v1/climate/financed-emissions"})
@RequiredArgsConstructor
@RequireModule("ESG")
@Tag(name = "Financed Emissions (PCAF)")
@SecurityRequirement(name = "bearerAuth")
public class FinancedEmissionsController {

    private static final String READ_GUARD = "hasPermission('SUST','READ')";
    private static final String WRITE_GUARD = "hasPermission('SUST','WRITE')";

    private final FinancedEmissionsService service;

    @GetMapping
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List all financed-emissions records for the current tenant.")
    public List<FinancedEmissionsResponse> list(@RequestParam(required = false) Integer year) {
        return year == null ? service.list() : service.listForYear(year);
    }

    @GetMapping("/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one financed-emissions record by external id.")
    public FinancedEmissionsResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @GetMapping("/totals")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Roll-up total for the reporting year/period.")
    public FinancedEmissionsTotal total(@RequestParam Integer year,
                                        @RequestParam(required = false) String period) {
        return service.computeTotalForYear(year, period);
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Create a new financed-emissions record (starts DRAFT).")
    public ResponseEntity<FinancedEmissionsResponse> create(@Valid @RequestBody FinancedEmissionsRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update a non-published record.")
    public FinancedEmissionsResponse update(@PathVariable UUID externalId,
                                            @Valid @RequestBody FinancedEmissionsRequest req) {
        return service.update(externalId, req);
    }

    @PostMapping("/{externalId}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition lifecycle stage — DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED.")
    public FinancedEmissionsResponse transition(@PathVariable UUID externalId,
                                                @Valid @RequestBody FeLifecycleTransitionRequest req) {
        return service.transition(externalId, req);
    }

    // ─── Targets ──────────────────────────────────────────────────

    @GetMapping("/targets")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List reduction targets.")
    public List<FinancedEmissionsTargetResponse> targets() {
        return service.listTargets();
    }

    @PostMapping("/targets")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Create a new reduction target.")
    public ResponseEntity<FinancedEmissionsTargetResponse> createTarget(@Valid @RequestBody FinancedEmissionsTargetRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTarget(req));
    }

    @GetMapping("/target-progress")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Compare each target against the latest available roll-up.")
    public List<TargetProgress> targetProgress() {
        return service.computeReductionAgainstTarget();
    }
}
