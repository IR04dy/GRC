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

    /*
     * F2-145(e) slice 4 — these guards authorise by PERMISSION, not by role name.
     *
     * They used to read hasAnyRole(...), which compares authority strings and never reaches
     * ModulePermissionEvaluator. Populations measured per action against iam.role_permissions BEFORE the
     * change, because ADR-010's amendment requires the INTENDED population rather than the current one:
     *
     *   READ     guard PLATFORM_ADMIN, RISK_MANAGER, COMPLIANCE_OFFICER, INTERNAL_AUDITOR, CFO, CRO,
     *                  CREDIT_OFFICER
     *            grant CFO, COMPLIANCE_OFFICER, CREDIT_OFFICER, INTERNAL_AUDITOR, RISK_MANAGER
     *   WRITE    guard PLATFORM_ADMIN, RISK_MANAGER, CREDIT_OFFICER, CRO
     *            grant CREDIT_OFFICER, RISK_MANAGER
     *   APPROVE  guard PLATFORM_ADMIN, CRO, CFO
     *            grant CFO
     *
     * PLATFORM_ADMIN is absent from every grant list on purpose: hasPermission gets the in-code
     * platform-admin bypass. CRO is seated in no row of iam.roles, so its three recitations here were
     * permanently false legs, and converting the guards is what retires them (F2-52's third route).
     *
     * ⚠ V369 IS LOAD-BEARING, AND IT HAD TO CREATE AN ACTION RATHER THAN REVOKE A GRANT. CPTY had no
     * APPROVE permission at all: V362 derived actions from method shape, and this controller's two
     * approval endpoints — assignRating and upsertLimit — are named like neither an approval nor a
     * transition, so the classifier read both as writes and folded CFO into CPTY:WRITE. The tempting
     * conversion was therefore to point APPROVE_GUARD at the CPTY:WRITE that already existed, which
     * would have taken the approval population from {PLATFORM_ADMIN, CFO} to include CREDIT_OFFICER and
     * RISK_MANAGER — letting a credit officer assign the internal credit rating on their own
     * counterparty and set their own large-exposure limit. Under SAMA's large-exposure rules that
     * separation IS the control. V369 catalogues CPTY:APPROVE, grants it to CFO, and revokes CFO's
     * mis-derived WRITE.
     *
     * ⚠ CFO is held by NO active user in dev, so APPROVE is reachable today only through the
     * administrator's bypass. That is unchanged by this commit — APPROVE_GUARD already named only
     * PLATFORM_ADMIN, a ghost, and a role nobody holds — so the conversion makes an existing narrowness
     * visible in a grant table instead of hiding it in a name list. Fixing it is tenant provisioning
     * (assign CFO to a person, or seat CRO), recorded in F2-145.
     *
     * ⚠ THE TIERS ARE NOT UNIFORM BY VERB. recordAction and recordUtilisation are POSTs on WRITE because
     * recording what happened is the analyst's job; assignRating and upsertLimit are POSTs on APPROVE
     * because deciding the rating and the limit is the credit authority's. CptyControllerGuardTest pins
     * all 14 sites, so a tidying sweep has to argue with a failing test.
     */
    private static final String READ_GUARD = "hasPermission('CPTY','READ')";
    private static final String WRITE_GUARD = "hasPermission('CPTY','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('CPTY','APPROVE')";

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
