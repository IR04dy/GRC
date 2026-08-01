package com.tss.grc.module.cpty.controller;

import com.tss.grc.module.cpty.dto.CptyDtos.AssignRatingRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.CounterpartyResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.CreateCounterpartyRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.CreateRatingActionRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.DashboardResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.EventResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.LimitResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.RatingActionResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.RatingResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.RecordUtilisationRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.UpdateCounterpartyRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.UpsertLimitRequest;
import com.tss.grc.module.cpty.service.CptyService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
 * REST surface for the Sprint 18 Counterparty Credit Rating Registry.
 * Tenant-scoped via JWT-derived {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/v1/cpty")
@RequiredArgsConstructor
@RequireModule("SAUDI_FS")
@Tag(name = "Counterparty Credit Rating Registry (Sprint 18)")
@SecurityRequirement(name = "bearerAuth")
public class CptyController {

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER',"
            + "'INTERNAL_AUDITOR','CFO','CRO','CREDIT_OFFICER')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','CREDIT_OFFICER','CRO')";
    private static final String APPROVE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','CRO','CFO')";

    private final CptyService service;

    // ─── Counterparties ────────────────────────────────────────────

    @PostMapping("/counterparties")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Register a new counterparty.")
    public ResponseEntity<CounterpartyResponse> create(
            @Valid @RequestBody CreateCounterpartyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createCounterparty(req));
    }

    @GetMapping("/counterparties")
    @PreAuthorize(READ_GUARD)
    public Page<CounterpartyResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listCounterparties(page, Math.min(size, 200));
    }

    @GetMapping("/counterparties/{id}")
    @PreAuthorize(READ_GUARD)
    public CounterpartyResponse get(@PathVariable Long id) {
        return service.getCounterparty(id);
    }

    @PutMapping("/counterparties/{id}")
    @PreAuthorize(WRITE_GUARD)
    public CounterpartyResponse update(@PathVariable Long id,
                                        @Valid @RequestBody UpdateCounterpartyRequest req) {
        return service.updateCounterparty(id, req);
    }

    // ─── Ratings ───────────────────────────────────────────────────

    @GetMapping("/counterparties/{id}/ratings")
    @PreAuthorize(READ_GUARD)
    public List<RatingResponse> listRatings(@PathVariable Long id) {
        return service.listRatings(id);
    }

    @PostMapping("/counterparties/{id}/ratings")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Assign a new rating (internal or external).")
    public RatingResponse assignRating(@PathVariable Long id,
                                        @Valid @RequestBody AssignRatingRequest req) {
        return service.assignRating(id, req);
    }

    // ─── Rating actions ────────────────────────────────────────────

    @GetMapping("/counterparties/{id}/actions")
    @PreAuthorize(READ_GUARD)
    public List<RatingActionResponse> listActions(@PathVariable Long id) {
        return service.listActions(id);
    }

    @PostMapping("/counterparties/{id}/actions")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a rating action (watchlist / downgrade / upgrade / default).")
    public RatingActionResponse recordAction(@PathVariable Long id,
                                              @Valid @RequestBody CreateRatingActionRequest req) {
        return service.recordAction(id, req);
    }

    // ─── Large-exposure limits ─────────────────────────────────────

    @GetMapping("/limits")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "All large-exposure limits, sorted by utilisation desc.")
    public List<LimitResponse> listLimits() {
        return service.listLimits();
    }

    @GetMapping("/counterparties/{id}/limits")
    @PreAuthorize(READ_GUARD)
    public List<LimitResponse> listLimitsForCounterparty(@PathVariable Long id) {
        return service.listLimitsForCounterparty(id);
    }

    @PostMapping("/limits")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Upsert a counterparty- or group-scope limit.")
    public LimitResponse upsertLimit(@Valid @RequestBody UpsertLimitRequest req) {
        return service.upsertLimit(req);
    }

    @PostMapping("/limits/{limitId}/utilisation")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record daily utilisation; recomputes band + fires breach events.")
    public LimitResponse recordUtilisation(@PathVariable Long limitId,
                                            @Valid @RequestBody RecordUtilisationRequest req) {
        return service.recordUtilisation(limitId, req);
    }

    // ─── Events + dashboard ────────────────────────────────────────

    @GetMapping("/counterparties/{id}/events")
    @PreAuthorize(READ_GUARD)
    public List<EventResponse> listEvents(@PathVariable Long id) {
        return service.listEvents(id);
    }

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Counterparty register dashboard with sector / grade / band roll-ups.")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
