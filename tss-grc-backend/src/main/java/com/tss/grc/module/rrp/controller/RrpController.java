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

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER',"
            + "'INTERNAL_AUDITOR','BCM_MANAGER','BOARD_SECRETARY','CFO','CRO')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','BCM_MANAGER','CRO')";
    private static final String APPROVE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','CRO','CFO')";

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
