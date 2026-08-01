package com.tss.grc.module.ai.governance.controller;

import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DecommissionRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.LifecycleTransitionRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelResponse;
import com.tss.grc.module.ai.governance.service.AiRegistryExportService;
import com.tss.grc.module.ai.governance.service.MlModelService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for the AI/ML Model Registry inventory.
 *
 * <p>Read-side widely available (auditors, DPO, model owners need
 * visibility). Write-side restricted to the roles that own model
 * lifecycle decisions (Risk, CISO, Platform Admin, plus the dedicated
 * MODEL_OWNER role for delegated maintenance).
 *
 * <p>All endpoints use {@code hasAnyRole} because the
 * {@code CustomUserDetailsService} prefixes Spring authorities with
 * {@code ROLE_} — {@code hasAnyAuthority} would silently reject every
 * legitimate caller.
 */
@RestController
@RequestMapping("/api/v1/ai/models")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "AI/ML Model Registry")
@SecurityRequirement(name = "bearerAuth")
public class MlModelController {

    private static final String READ_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','MODEL_OWNER','DPO','AUDITOR','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('RISK_MANAGER','MODEL_OWNER','CISO','PLATFORM_ADMIN')";
    /**
     * F2-27 — extracting the model inventory as a file needs the catalogued AI:EXPORT
     * permission, AND-ed in front of the role list rather than replacing it, so this
     * narrows and never widens. Two measured details behind the exact expression:
     * <ul>
     *   <li>{@code MODEL_OWNER} is dropped — it appears in {@code READ_GUARD} but is not a
     *       defined role in {@code iam.roles}, so it never matched anything.</li>
     *   <li>{@code AUDITOR} is kept, and V320 grants it {@code AI:EXPORT} explicitly: it
     *       holds no {@code AI:READ}, so the set-derived grant would have missed it and this
     *       AND would have 403'd a role this endpoint has always allowed.</li>
     * </ul>
     * V320 catalogues AI:EXPORT (it did not exist) and grants it to the 41 AI:READ roles.
     */
    private static final String EXPORT_GUARD =
            "hasPermission('AI','EXPORT') and "
            + "hasAnyRole('RISK_MANAGER','CISO','DPO','AUDITOR','PLATFORM_ADMIN')";

    private final MlModelService service;
    private final AiRegistryExportService exportService;

    @GetMapping
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List AI/ML models for the current tenant.")
    public List<MlModelResponse> list() {
        return service.list();
    }

    @GetMapping("/export")
    @PreAuthorize(EXPORT_GUARD)
    @Operation(summary = "Regulator-ready AI registry pack (XLSX or PDF): posture summary + per-model attributes. "
            + "Optional filters (stage/tier/domain/euHighRisk/gap) export only the matching subset — e.g. gap=AIA for a remediation list.")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "XLSX") String format,
                                         @RequestParam(required = false) String stage,
                                         @RequestParam(required = false) String tier,
                                         @RequestParam(required = false) String domain,
                                         @RequestParam(required = false) String gap,
                                         @RequestParam(required = false) Boolean euHighRisk,
                                         @RequestParam(defaultValue = "en") String lang) {
        boolean pdf = "PDF".equalsIgnoreCase(format);
        byte[] body = exportService.export(format,
                new AiRegistryExportService.ExportFilter(stage, tier, domain, gap, euHighRisk), lang);
        MediaType ct = pdf ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = "ai-model-registry." + (pdf ? "pdf" : "xlsx");
        return ResponseEntity.ok()
                .contentType(ct)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    @GetMapping("/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one model by external id.")
    public MlModelResponse get(@PathVariable String externalId) {
        return service.get(AiModelIds.parse(externalId));
    }

    @GetMapping("/high-risk-without-fairness")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Production-running CRITICAL/HIGH-risk models that lack a fairness audit.")
    public List<MlModelResponse> highRiskWithoutFairness() {
        return service.findHighRiskModelsWithoutFairnessAudit();
    }

    @GetMapping("/high-risk-without-aia")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Production-running CRITICAL/HIGH-risk models that lack a signed-off AI Impact Assessment.")
    public List<MlModelResponse> highRiskWithoutAia() {
        return service.findHighRiskModelsWithoutSignedOffAia();
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Register a new AI/ML model (starts in PROPOSED).")
    public ResponseEntity<MlModelResponse> create(@Valid @RequestBody MlModelRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update metadata on a model that is not DECOMMISSIONED.")
    public MlModelResponse update(@PathVariable String externalId, @Valid @RequestBody MlModelRequest req) {
        return service.update(AiModelIds.parse(externalId), req);
    }

    @PostMapping("/{externalId}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition lifecycle stage — HIGH+ risk → APPROVED_FOR_PROD requires fairness/DPIA/explainability/validator.")
    public MlModelResponse transition(@PathVariable String externalId,
                                      @Valid @RequestBody LifecycleTransitionRequest req) {
        return service.transition(AiModelIds.parse(externalId), req);
    }

    @PostMapping("/{externalId}/decommission")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Decommission the model with a documented reason.")
    public MlModelResponse decommission(@PathVariable String externalId,
                                        @Valid @RequestBody DecommissionRequest req) {
        return service.decommission(AiModelIds.parse(externalId), req);
    }
}
