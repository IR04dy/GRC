package com.tss.grc.module.rrp.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateCriticalFunctionRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateIndicatorRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateOptionRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.CriticalFunctionResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.CycleResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.DashboardResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.EventResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.IndicatorResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.OptionResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.RecordIndicatorMeasurementRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.SubmitCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.TransitionCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.UpdateCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.UpdateOptionRequest;
import com.tss.grc.module.rrp.service.RrpService;
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
 * REST surface for the Sprint 17 Recovery &amp; Resolution Plan.
 * Tenant-scoped via JWT-derived {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/v1/rrp")
@RequiredArgsConstructor
@RequireModule("SAUDI_FS")
@Tag(name = "Recovery & Resolution Plan (Sprint 17)")
@SecurityRequirement(name = "bearerAuth")
public class RrpController {

    /**
     * F2-260 — all three tiers were pure role guards, and the APPROVE one admitted the platform
     * administrator alone.
     *
     * <h3>The lockout</h3>
     * {@code APPROVE_GUARD} was {@code hasAnyRole('PLATFORM_ADMIN','CRO','CFO')}. {@code CRO} and
     * {@code CFO} are held by <b>no user in dev or prod</b>, and {@code CRO} is additionally
     * <b>absent from prod's {@code iam.roles}</b> because V373 is pending there. A role guard never
     * reaches {@code ModulePermissionEvaluator}, so it never reaches the PLATFORM_ADMIN bypass
     * either: {@code POST /cycles/{id}/submit} — the route that lodges the Recovery &amp; Resolution
     * Plan with SAMA — was reachable by the platform administrator and nobody else.
     *
     * <p>🔑 <b>Why that mattered rather than merely being narrow.</b> {@code /transition} carries the
     * WRITE tier, which {@code RISK_MANAGER} holds, so a risk manager can drive a cycle
     * {@code DRAFT → IN_PREPARATION → BOARD_REVIEW → APPROVED}. {@code RrpService.transition} then
     * explicitly refuses the {@code SUBMITTED} edge (<i>"Use the dedicated /submit endpoint to lodge
     * with SAMA"</i>) and {@code ALLOWED.get("APPROVED")} is {@code {IN_PREPARATION}} — so for every
     * non-administrator <b>APPROVED was a dead end: the only forward edge was the administrator-only
     * submit and the only other move was backwards into preparation.</b> Measured identically in dev
     * and prod: 5 cycles, 46 critical functions, 31 indicators, 32 recovery options, and
     * <b>no cycle has ever reached SUBMITTED</b>.
     *
     * <h3>Why this needed a migration and not a guard edit</h3>
     * {@code RRP} had <b>no catalogued permission of any kind</b> in either database — F2-257 filed
     * that {@code DATACLASS}, {@code OUTSOURCING}, {@code REGINSPECT}, {@code REGREPORTING} and
     * {@code RRP} have no {@code WRITE}/{@code APPROVE} row at all. So unlike F2-253's attrition
     * sites there was no remedy available to a tenant administrator: nothing could be granted because
     * nothing existed to grant. <b>V383</b> catalogues exactly the three tiers these three constants
     * express — read off the guards, never inferred from method shape, which is V362's landmine — and
     * grants them as declared literals.
     *
     * <p>⚠ <b>Audience diff, measured before converting rather than after (V362's landmine) and
     * identical in both databases.</b> READ and WRITE are <b>population-preserving</b>: every role
     * the old lists named is granted, including the four held by nobody, so a tenant administrator
     * assigning one still gets what the shipped guard promised. APPROVE goes from the platform
     * administrator alone to <b>the platform administrator plus {@code GRC_ADMINISTRATOR}</b> — one
     * role, one live holder, on one route, and it follows the platform's own convention rather than a
     * preference: {@code GRC_ADMINISTRATOR} holds {@code APPROVE} on {@code BCM}, {@code RISK} and
     * {@code ADMIN} alike.
     *
     * <p>⚠⚠ <b>{@code RISK_MANAGER} is deliberately NOT granted {@code RRP:APPROVE}, and that is the
     * sharpest decision here.</b> It holds {@code RISK:APPROVE}, so convention argues for it — and
     * the shipped guard pointedly did not name it: WRITE and APPROVE overlapped on {@code CRO} and
     * nothing else. Granting it would let the persona that prepares and board-approves the cycle also
     * lodge it, retiring the one separation this controller does express. V383's check 4 fails the
     * migration if a second role ever holds both. {@code AUDITOR}, {@code VIEWER} and
     * {@code REGULATOR_VIEWER} are likewise withheld from READ although all three hold
     * {@code BCM:READ} and {@code RISK:READ}: a resolution plan is the most sensitive document in a
     * bank's contingency set, and the shipped read guard names none of them.
     */
    private static final String READ_GUARD = "hasPermission('RRP','READ')";
    private static final String WRITE_GUARD = "hasPermission('RRP','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('RRP','APPROVE')";

    private final RrpService service;

    // ─── Cycles ────────────────────────────────────────────────────

    @PostMapping("/cycles")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Open a new RRP cycle for the given fiscal year.")
    public ResponseEntity<CycleResponse> createCycle(@Valid @RequestBody CreateCycleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCycle(req));
    }

    @PutMapping("/cycles/{id}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update RRP cycle metadata.")
    public CycleResponse updateCycle(@PathVariable Long id,
                                      @Valid @RequestBody UpdateCycleRequest req) {
        return service.updateCycle(id, req);
    }

    @GetMapping("/cycles")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Paginated list of RRP cycles, newest fiscal year first.")
    public Page<CycleResponse> listCycles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listCycles(page, Math.min(size, 200));
    }

    @GetMapping("/cycles/{id}")
    @PreAuthorize(READ_GUARD)
    public CycleResponse getCycle(@PathVariable Long id) {
        return service.getCycle(id);
    }

    @PostMapping("/cycles/{id}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Move cycle to the next status (state machine).")
    public CycleResponse transition(@PathVariable Long id,
                                     @Valid @RequestBody TransitionCycleRequest req) {
        return service.transitionCycle(id, req);
    }

    @PostMapping("/cycles/{id}/submit")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Lodge the cycle with SAMA (APPROVED → SUBMITTED).")
    public CycleResponse submit(@PathVariable Long id,
                                 @Valid @RequestBody SubmitCycleRequest req) {
        return service.submitCycle(id, req);
    }

    @PostMapping("/cycles/{id}/acknowledge")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record SAMA acknowledgment (SUBMITTED → ACKNOWLEDGED).")
    public CycleResponse acknowledge(@PathVariable Long id) {
        return service.acknowledgeCycle(id);
    }

    // ─── Recovery options ──────────────────────────────────────────

    @GetMapping("/cycles/{id}/options")
    @PreAuthorize(READ_GUARD)
    public List<OptionResponse> listOptions(@PathVariable Long id) {
        return service.listOptions(id);
    }

    @PostMapping("/cycles/{id}/options")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Add a new recovery option to the cycle's menu.")
    public OptionResponse addOption(@PathVariable Long id,
                                     @Valid @RequestBody CreateOptionRequest req) {
        return service.addOption(id, req);
    }

    @PutMapping("/options/{optionId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update recovery option (capacity reassessment, narrative refresh).")
    public OptionResponse updateOption(@PathVariable Long optionId,
                                        @Valid @RequestBody UpdateOptionRequest req) {
        return service.updateOption(optionId, req);
    }

    // ─── Critical functions ────────────────────────────────────────

    @GetMapping("/cycles/{id}/critical-functions")
    @PreAuthorize(READ_GUARD)
    public List<CriticalFunctionResponse> listCriticalFunctions(@PathVariable Long id) {
        return service.listCriticalFunctions(id);
    }

    @PostMapping("/cycles/{id}/critical-functions")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Add a critical function to the cycle inventory.")
    public CriticalFunctionResponse addCriticalFunction(@PathVariable Long id,
                                                         @Valid @RequestBody CreateCriticalFunctionRequest req) {
        return service.addCriticalFunction(id, req);
    }

    // ─── Indicators ────────────────────────────────────────────────

    @GetMapping("/cycles/{id}/indicators")
    @PreAuthorize(READ_GUARD)
    public List<IndicatorResponse> listIndicators(@PathVariable Long id) {
        return service.listIndicators(id);
    }

    @PostMapping("/cycles/{id}/indicators")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Add a recovery indicator with RAG thresholds.")
    public IndicatorResponse addIndicator(@PathVariable Long id,
                                           @Valid @RequestBody CreateIndicatorRequest req) {
        return service.addIndicator(id, req);
    }

    @PostMapping("/indicators/{indicatorId}/measurements")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a new measurement; recomputes RAG band + fires breach trigger if worse.")
    public IndicatorResponse recordMeasurement(@PathVariable Long indicatorId,
                                                 @Valid @RequestBody RecordIndicatorMeasurementRequest req) {
        return service.recordMeasurement(indicatorId, req);
    }

    // ─── Timeline + dashboard ──────────────────────────────────────

    @GetMapping("/cycles/{id}/events")
    @PreAuthorize(READ_GUARD)
    public List<EventResponse> listEvents(@PathVariable Long id) {
        return service.listEvents(id);
    }

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "RRP dashboard — KPIs + recent cycles + RAG roll-up.")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
