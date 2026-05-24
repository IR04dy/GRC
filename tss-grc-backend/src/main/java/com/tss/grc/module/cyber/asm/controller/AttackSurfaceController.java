package com.tss.grc.module.cyber.asm.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.AssetRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.AssetResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.DashboardSnapshot;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingTransitionRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.LifecycleTransitionRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItBlockRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItSanctionRequest;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceAssetRepository;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceFindingRepository;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceShadowItRepository;
import com.tss.grc.module.cyber.asm.service.AttackSurfaceAssetService;
import com.tss.grc.module.cyber.asm.service.AttackSurfaceFindingService;
import com.tss.grc.module.cyber.asm.service.ShadowItService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * REST surface for Attack Surface Management (ASM) — Sprint 11.
 *
 * <p>Read guard is wide (CISO, SOC lead, risk manager, compliance,
 * auditor, platform admin). Write guard is narrow (CISO, SOC lead,
 * platform admin) because the ASM register feeds the audit committee
 * dashboard and the regulator-grade asset inventory.
 */
@RestController
@RequestMapping("/api/v1/asm")
@RequiredArgsConstructor
@Tag(name = "ASM — Attack Surface Management")
@SecurityRequirement(name = "bearerAuth")
public class AttackSurfaceController {

    private static final String READ_GUARD =
            "hasAnyRole('CISO','SOC_LEAD','RISK_MANAGER','COMPLIANCE_OFFICER','AUDITOR','INTERNAL_AUDITOR','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('CISO','SOC_LEAD','PLATFORM_ADMIN')";

    private final AttackSurfaceAssetService assetService;
    private final AttackSurfaceFindingService findingService;
    private final ShadowItService shadowItService;
    private final AttackSurfaceAssetRepository assetRepo;
    private final AttackSurfaceFindingRepository findingRepo;
    private final AttackSurfaceShadowItRepository shadowItRepo;

    // ─── Assets ───────────────────────────────────────────────────

    @GetMapping("/assets")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List all attack-surface assets for the current tenant.")
    public List<AssetResponse> listAssets() {
        return assetService.listAssets();
    }

    @GetMapping("/assets/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one asset by external id.")
    public AssetResponse getAsset(@PathVariable UUID externalId) {
        return assetService.getAsset(externalId);
    }

    @PostMapping("/assets")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Create a new attack-surface asset (defaults to DISCOVERED).")
    public ResponseEntity<AssetResponse> createAsset(@Valid @RequestBody AssetRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createAsset(req));
    }

    @PutMapping("/assets/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update an asset's mutable fields.")
    public AssetResponse updateAsset(@PathVariable UUID externalId, @Valid @RequestBody AssetRequest req) {
        return assetService.updateAsset(externalId, req);
    }

    @PostMapping("/assets/{externalId}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition the asset's lifecycle stage.")
    public AssetResponse transition(@PathVariable UUID externalId,
                                    @Valid @RequestBody LifecycleTransitionRequest req) {
        return assetService.transitionLifecycle(externalId, req);
    }

    // ─── Findings ─────────────────────────────────────────────────

    @GetMapping("/findings")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List every finding for the current tenant.")
    public List<FindingResponse> listFindings() {
        return findingService.listFindings();
    }

    @GetMapping("/assets/{externalId}/findings")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List findings attached to a given asset.")
    public List<FindingResponse> listFindingsForAsset(@PathVariable UUID externalId) {
        return findingService.listFindingsForAsset(externalId);
    }

    @GetMapping("/findings/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one finding by external id.")
    public FindingResponse getFinding(@PathVariable UUID externalId) {
        return findingService.getFinding(externalId);
    }

    @PostMapping("/findings")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a new finding against an asset.")
    public ResponseEntity<FindingResponse> recordFinding(@Valid @RequestBody FindingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(findingService.recordFinding(req));
    }

    @PostMapping("/findings/{externalId}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition the finding status (e.g. FIXED, ACCEPTED_RISK).")
    public FindingResponse transitionFinding(@PathVariable UUID externalId,
                                             @Valid @RequestBody FindingTransitionRequest req) {
        return findingService.transition(externalId, req);
    }

    @PostMapping("/findings/{externalId}/link-capa")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Link the finding to a CAPA task id (idempotent).")
    public FindingResponse linkCapa(@PathVariable UUID externalId,
                                    @RequestParam Long capaTaskId) {
        return findingService.linkCapaTask(externalId, capaTaskId);
    }

    // ─── Shadow IT ────────────────────────────────────────────────

    @GetMapping("/shadow-it")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List shadow-IT discoveries for the current tenant.")
    public List<ShadowItResponse> listShadowIt() {
        return shadowItService.list();
    }

    @GetMapping("/shadow-it/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one shadow-IT discovery.")
    public ShadowItResponse getShadowIt(@PathVariable UUID externalId) {
        return shadowItService.get(externalId);
    }

    @PostMapping("/shadow-it")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a new shadow-IT discovery.")
    public ResponseEntity<ShadowItResponse> recordShadowIt(@Valid @RequestBody ShadowItRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shadowItService.recordDiscovery(req));
    }

    @PostMapping("/shadow-it/{externalId}/sanction")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Sanction the discovery — link to a TPRM vendor record.")
    public ShadowItResponse sanctionShadowIt(@PathVariable UUID externalId,
                                             @Valid @RequestBody ShadowItSanctionRequest req) {
        return shadowItService.sanction(externalId, req);
    }

    @PostMapping("/shadow-it/{externalId}/block")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Block the discovery (terminal).")
    public ShadowItResponse blockShadowIt(@PathVariable UUID externalId,
                                          @RequestBody(required = false) ShadowItBlockRequest req) {
        return shadowItService.block(externalId, req);
    }

    @PostMapping("/shadow-it/{externalId}/not-applicable")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Mark the discovery as not applicable (false positive).")
    public ShadowItResponse markNotApplicable(@PathVariable UUID externalId,
                                              @RequestParam(required = false) String note) {
        return shadowItService.markNotApplicable(externalId, note);
    }

    // ─── Dashboard ────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "KPI snapshot for the ASM register page.")
    public DashboardSnapshot dashboard() {
        Long orgId = TenantContext.getCurrentTenant();
        List<AssetResponse> assets = assetService.listAssets();

        long totalAssets         = assets.size();
        long approvedAssets      = assets.stream().filter(a -> "APPROVED".equals(a.lifecycleStage())).count();
        long shadowItPending     = assets.stream().filter(a -> "SHADOW_IT_PENDING".equals(a.lifecycleStage())).count();
        long publicCritical      = assetRepo.countByOrganisationIdAndExposureAndCriticalityAndIsDeletedFalse(
                orgId, "PUBLIC", "CRITICAL");

        long openFindings        = findingRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "OPEN");
        long investigating       = findingRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "INVESTIGATING");
        // Critical-severity open findings — surface to the dashboard separately.
        List<FindingResponse> recentFindings = findingService.listFindings();
        long criticalOpenFindings = recentFindings.stream()
                .filter(f -> ("OPEN".equals(f.status()) || "INVESTIGATING".equals(f.status())))
                .filter(f -> "CRITICAL".equals(f.severity()))
                .count();

        long pendingShadowIt     = shadowItRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "PENDING_REVIEW")
                + shadowItRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "IN_PROGRESS");

        List<ShadowItResponse> pendingShadowItList = shadowItService.list().stream()
                .filter(s -> "PENDING_REVIEW".equals(s.status()) || "IN_PROGRESS".equals(s.status()))
                .toList();
        long pdplShadowIt = pendingShadowItList.stream().filter(s -> Boolean.TRUE.equals(s.pdplDataProcessed())).count();

        List<AssetResponse> topRisk = assets.stream()
                .filter(a -> a.riskScore() != null)
                .sorted(Comparator.comparing(AssetResponse::riskScore).reversed())
                .limit(10)
                .toList();

        List<FindingResponse> recentTen = recentFindings.stream().limit(15).toList();

        return new DashboardSnapshot(
                totalAssets, approvedAssets, shadowItPending, publicCritical,
                openFindings + investigating, criticalOpenFindings,
                pendingShadowIt, pdplShadowIt,
                topRisk, recentTen, pendingShadowItList);
    }
}
