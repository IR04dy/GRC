package com.tss.grc.module.ai.governance.controller;

import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelAuditRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelAuditResponse;
import com.tss.grc.module.ai.governance.service.MlModelAuditService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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

/**
 * REST surface for fairness / bias / explainability / robustness /
 * privacy / security / performance audit results against an AI/ML model.
 *
 * <p>Mounted under the parent model id so the URL reads naturally
 * ({@code /api/v1/ai/models/{id}/audits}). Read-side widely available;
 * write-side restricted to the lifecycle owners.
 */
@RestController
@RequestMapping("/api/v1/ai/models/{externalId}/audits")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "AI/ML Model Audits")
@SecurityRequirement(name = "bearerAuth")
public class MlModelAuditController {

    /**
     * F2-159 — every route here is under {@code /api/v1/ai/models/{externalId}/audits}, so the model's
     * owner can be identified on all of them. The owner is admitted to the <b>reads</b> only.
     *
     * <p>⚠ {@code WRITE_GUARD} guards {@code record}, which files a fairness / bias / AIA audit result
     * against the model. Admitting the owner there would let them author the audit <em>of their own
     * model</em> — V367's defect (the Sharia auditor approving what it audits) transplanted into AI
     * governance. The ability to bind an id is not a reason to bind it: {@code MODEL_OWNER} is dropped
     * from this constant because it never matched anything, and no ownership leg replaces it.
     */
    private static final String READ_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','DPO','AUDITOR','PLATFORM_ADMIN') "
            + "or hasPermission(#externalId, 'ML_MODEL', 'OWNER')";
    private static final String WRITE_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN')";

    private final MlModelAuditService service;

    @GetMapping
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List every audit result against the model (newest first).")
    public List<MlModelAuditResponse> list(@PathVariable String externalId) {
        return service.listAudits(AiModelIds.parse(externalId));
    }

    @GetMapping("/latest/{auditType}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Most-recent audit of the given type — convenience for the detail page.")
    public ResponseEntity<MlModelAuditResponse> latest(@PathVariable String externalId,
                                                       @PathVariable String auditType) {
        return service.getLatestByType(AiModelIds.parse(externalId), auditType)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a new audit result. FAIRNESS/EXPLAINABILITY positive results flip the parent compliance flag.")
    public ResponseEntity<MlModelAuditResponse> record(@PathVariable String externalId,
                                                       @Valid @RequestBody MlModelAuditRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordAudit(AiModelIds.parse(externalId), req));
    }
}
