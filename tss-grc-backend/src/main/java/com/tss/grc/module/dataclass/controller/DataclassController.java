package com.tss.grc.module.dataclass.controller;

import com.tss.grc.module.dataclass.dto.DataclassDtos.*;
import com.tss.grc.module.dataclass.service.DataclassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST surface for Sprint 32 Data Classification + NDMO. */
@RestController
@RequestMapping("/api/v1/dataclass")
@RequiredArgsConstructor
public class DataclassController {

    private static final String READ = "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','AUDITOR','DPO','CISO','CIO','RISK_MANAGER')";
    private static final String WRITE = "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','DPO','CIO')";
    private static final String APPROVE = "hasAnyRole('PLATFORM_ADMIN','DPO','CISO','CIO')";

    private final DataclassService svc;

    // ──────── Assets ────────

    @GetMapping("/assets") @PreAuthorize(READ)
    public List<AssetResponse> listAssets() { return svc.listAssets(); }

    @GetMapping("/assets/{id}") @PreAuthorize(READ)
    public AssetResponse getAsset(@PathVariable Long id) { return svc.getAsset(id); }

    @PostMapping("/assets") @PreAuthorize(WRITE)
    public ResponseEntity<AssetResponse> createAsset(@Valid @RequestBody CreateAssetRequest req) {
        return ResponseEntity.ok(svc.createAsset(req));
    }

    @PostMapping("/assets/{id}/classify") @PreAuthorize(WRITE)
    public AssetResponse classify(@PathVariable Long id, @Valid @RequestBody ClassifyAssetRequest req) {
        return svc.classifyAsset(id, req);
    }

    @PostMapping("/assets/{id}/transition") @PreAuthorize(APPROVE)
    public AssetResponse transitionAsset(@PathVariable Long id, @Valid @RequestBody TransitionAssetRequest req) {
        return svc.transitionAsset(id, req);
    }

    @GetMapping("/assets/{id}/timeline") @PreAuthorize(READ)
    public List<EventResponse> timeline(@PathVariable Long id) { return svc.timeline(id); }

    // ──────── Flows ────────

    @GetMapping("/flows") @PreAuthorize(READ)
    public List<FlowResponse> listFlows(@RequestParam(required = false) Long assetId) {
        return svc.listFlows(assetId);
    }

    @PostMapping("/flows") @PreAuthorize(WRITE)
    public ResponseEntity<FlowResponse> createFlow(@Valid @RequestBody CreateFlowRequest req) {
        return ResponseEntity.ok(svc.createFlow(req));
    }

    @PostMapping("/flows/{id}/transition") @PreAuthorize(APPROVE)
    public FlowResponse transitionFlow(@PathVariable Long id, @Valid @RequestBody TransitionFlowRequest req) {
        return svc.transitionFlow(id, req);
    }

    // ──────── Exceptions ────────

    @GetMapping("/exceptions") @PreAuthorize(READ)
    public List<ExceptionResponse> listExceptions() { return svc.listExceptions(); }

    @PostMapping("/exceptions") @PreAuthorize(WRITE)
    public ResponseEntity<ExceptionResponse> createException(@Valid @RequestBody CreateExceptionRequest req) {
        return ResponseEntity.ok(svc.createException(req));
    }

    @PostMapping("/exceptions/{id}/transition") @PreAuthorize(APPROVE)
    public ExceptionResponse transitionException(@PathVariable Long id, @Valid @RequestBody TransitionExceptionRequest req) {
        return svc.transitionException(id, req);
    }

    // ──────── Dashboard ────────

    @GetMapping("/dashboard") @PreAuthorize(READ)
    public DashboardResponse dashboard() { return svc.dashboard(); }
}
