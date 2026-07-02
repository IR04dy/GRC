package com.tss.grc.module.sust.climate.controller;

import com.tss.grc.module.sust.climate.dto.ClimateDtos.ProbabilityWeightedExpectedLoss;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdLifecycleTransitionRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdSectorImpactRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdSectorImpactResponse;
import com.tss.grc.module.sust.climate.service.TcfdScenarioService;
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
 * REST surface for the TCFD climate-scenario register — Sprint 11.
 *
 * <p>Read is wide (board, auditors, CFO, CISO, risk all consume the
 * board pack). Write is narrow — the Sustainability Officer and CFO
 * own the figures, with Platform Admin for break-glass.
 *
 * <p>Guarded by {@code hasPermission('SUST', …)} (see migration V248 for the
 * SUST grant matrix + the SUSTAINABILITY_OFFICER role) — one scheme module-wide.
 */
@RestController
@RequestMapping("/api/v1/climate/scenarios")
@RequiredArgsConstructor
@Tag(name = "TCFD Climate Scenarios")
@SecurityRequirement(name = "bearerAuth")
public class TcfdScenarioController {

    private static final String READ_GUARD = "hasPermission('SUST','READ')";
    private static final String WRITE_GUARD = "hasPermission('SUST','WRITE')";

    private final TcfdScenarioService service;

    @GetMapping
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List TCFD climate scenarios for the current tenant.")
    public List<TcfdScenarioResponse> list() {
        return service.list();
    }

    @GetMapping("/approved")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List BOARD_APPROVED scenarios only.")
    public List<TcfdScenarioResponse> approved() {
        return service.listApproved();
    }

    @GetMapping("/weighted-expected-loss")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Probability-weighted expected loss across approved scenarios.")
    public ProbabilityWeightedExpectedLoss weightedExpectedLoss() {
        return service.computeProbabilityWeightedExpectedLoss();
    }

    @GetMapping("/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one scenario by external id.")
    public TcfdScenarioResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @GetMapping("/{externalId}/sector-impacts")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List sector-impact rows for the scenario.")
    public List<TcfdSectorImpactResponse> sectorImpacts(@PathVariable UUID externalId) {
        return service.listSectorImpacts(externalId);
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Create a new TCFD scenario (starts DRAFT).")
    public ResponseEntity<TcfdScenarioResponse> create(@Valid @RequestBody TcfdScenarioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update a DRAFT/IN_REVIEW scenario.")
    public TcfdScenarioResponse update(@PathVariable UUID externalId,
                                       @Valid @RequestBody TcfdScenarioRequest req) {
        return service.update(externalId, req);
    }

    @PostMapping("/{externalId}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition lifecycle stage — DRAFT → IN_REVIEW → BOARD_APPROVED → ARCHIVED.")
    public TcfdScenarioResponse transition(@PathVariable UUID externalId,
                                           @Valid @RequestBody TcfdLifecycleTransitionRequest req,
                                           @AuthenticationPrincipal GrcUserDetails me) {
        Long userId = me == null ? null : me.getUserId();
        return service.transition(externalId, req, userId);
    }

    @PostMapping("/{externalId}/sector-impacts")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Add a sector-impact row to the scenario.")
    public ResponseEntity<TcfdSectorImpactResponse> addSectorImpact(@PathVariable UUID externalId,
                                                                    @Valid @RequestBody TcfdSectorImpactRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addSectorImpact(externalId, req));
    }
}
