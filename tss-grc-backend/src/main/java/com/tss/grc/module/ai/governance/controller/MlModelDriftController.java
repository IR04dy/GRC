package com.tss.grc.module.ai.governance.controller;

import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationResponse;
import com.tss.grc.module.ai.governance.service.MlModelDriftService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
 * REST surface for post-market drift observations against an AI/ML model.
 *
 * <p>Drift observations are typically recorded by automated monitoring
 * jobs (DATA_DRIFT / CONCEPT_DRIFT / PERFORMANCE_DEGRADATION /
 * FAIRNESS_REGRESSION) and acknowledged by a human owner. CRITICAL
 * severity observations auto-open an Issue and flip the model to
 * REVISION — see {@link MlModelDriftService}.
 */
@RestController
@RequestMapping("/api/v1/ai/models/{externalId}/drift-observations")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "AI/ML Model Drift Observations")
@SecurityRequirement(name = "bearerAuth")
public class MlModelDriftController {

    /**
     * F2-159 — the owner may <b>see</b> the drift observed on their own model, and does not rule on it.
     *
     * <p>⚠ {@code WRITE_GUARD} covers {@code record} and {@code acknowledge}. Both carry the
     * {@code externalId} and could have been bound; {@code acknowledge} is the model owner accepting or
     * dismissing a drift alert against their own model, which is a verdict, not upkeep. Same line as the
     * audit controller and {@code transition}. {@code MODEL_OWNER} is dropped here as a dead leg with no
     * replacement, and whether the owner should be able to acknowledge drift is filed as a policy call.
     */
    private static final String READ_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','DPO','AUDITOR','PLATFORM_ADMIN') "
            + "or hasPermission(#externalId, 'ML_MODEL', 'OWNER')";
    private static final String WRITE_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','PLATFORM_ADMIN')";

    private final MlModelDriftService service;

    @GetMapping
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List drift observations for this model (newest first).")
    public List<DriftObservationResponse> list(@PathVariable String externalId) {
        return service.list(AiModelIds.parse(externalId));
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a drift observation. CRITICAL severity auto-opens an Issue + flips the model to REVISION.")
    public ResponseEntity<DriftObservationResponse> record(@PathVariable String externalId,
                                                           @Valid @RequestBody DriftObservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(AiModelIds.parse(externalId), req));
    }

    @PostMapping("/{obsId}/acknowledge")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Acknowledge a drift observation — stamps acknowledged_at / by.")
    public DriftObservationResponse acknowledge(@PathVariable String externalId,
                                                @PathVariable UUID obsId,
                                                @AuthenticationPrincipal GrcUserDetails me) {
        Long userId = me == null ? null : me.getUserId();
        return service.acknowledge(AiModelIds.parse(externalId), obsId, userId);
    }
}
