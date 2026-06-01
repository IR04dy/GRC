package com.tss.grc.module.ai.governance.controller;

import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelAuditRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelAuditResponse;
import com.tss.grc.module.ai.governance.service.MlModelAuditService;
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
@Tag(name = "AI/ML Model Audits")
@SecurityRequirement(name = "bearerAuth")
public class MlModelAuditController {

    private static final String READ_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','MODEL_OWNER','DPO','AUDITOR','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('RISK_MANAGER','MODEL_OWNER','CISO','AUDITOR','PLATFORM_ADMIN')";

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
