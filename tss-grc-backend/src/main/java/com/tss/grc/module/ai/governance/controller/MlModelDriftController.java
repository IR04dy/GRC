package com.tss.grc.module.ai.governance.controller;

import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationResponse;
import com.tss.grc.module.ai.governance.service.MlModelDriftService;
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
@Tag(name = "AI/ML Model Drift Observations")
@SecurityRequirement(name = "bearerAuth")
public class MlModelDriftController {

    private static final String READ_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','MODEL_OWNER','DPO','AUDITOR','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('RISK_MANAGER','MODEL_OWNER','CISO','PLATFORM_ADMIN')";

    private final MlModelDriftService service;

    @GetMapping
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List drift observations for this model (newest first).")
    public List<DriftObservationResponse> list(@PathVariable UUID externalId) {
        return service.list(externalId);
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a drift observation. CRITICAL severity auto-opens an Issue + flips the model to REVISION.")
    public ResponseEntity<DriftObservationResponse> record(@PathVariable UUID externalId,
                                                           @Valid @RequestBody DriftObservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(externalId, req));
    }

    @PostMapping("/{obsId}/acknowledge")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Acknowledge a drift observation — stamps acknowledged_at / by.")
    public DriftObservationResponse acknowledge(@PathVariable UUID externalId,
                                                @PathVariable UUID obsId,
                                                @AuthenticationPrincipal GrcUserDetails me) {
        Long userId = me == null ? null : me.getUserId();
        return service.acknowledge(externalId, obsId, userId);
    }
}
