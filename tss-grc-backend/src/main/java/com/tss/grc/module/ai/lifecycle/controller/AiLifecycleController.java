package com.tss.grc.module.ai.lifecycle.controller;

import com.tss.grc.module.ai.lifecycle.dto.AiLifecycleDtos.*;
import com.tss.grc.module.ai.lifecycle.service.AiLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST surface for Sprint 31 AI/ML Model Lifecycle. */
@RestController
@RequestMapping("/api/v1/ai-lifecycle")
@RequiredArgsConstructor
public class AiLifecycleController {

    private static final String READ = "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','AUDITOR','CISO','DPO','CIO','CRO')";
    private static final String WRITE = "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','CIO','DPO')";
    private static final String APPROVE = "hasAnyRole('PLATFORM_ADMIN','CRO','CIO','DPO')";

    private final AiLifecycleService svc;

    // ──────── Validation ────────

    @GetMapping("/validations") @PreAuthorize(READ)
    public List<ValidationResponse> listValidations(@RequestParam(required = false) Long modelId) {
        return svc.listValidations(modelId);
    }

    @PostMapping("/validations") @PreAuthorize(WRITE)
    public ResponseEntity<ValidationResponse> create(@Valid @RequestBody CreateValidationRequest req) {
        return ResponseEntity.ok(svc.createValidation(req));
    }

    @PostMapping("/validations/{id}/transition") @PreAuthorize(APPROVE)
    public ValidationResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionValidationRequest req) {
        return svc.transitionValidation(id, req);
    }

    // ──────── Retraining ────────

    @GetMapping("/retrainings") @PreAuthorize(READ)
    public List<RetrainingResponse> listRetrainings(@RequestParam(required = false) Long modelId) {
        return svc.listRetrainings(modelId);
    }

    @PostMapping("/retrainings") @PreAuthorize(WRITE)
    public ResponseEntity<RetrainingResponse> createRetraining(@Valid @RequestBody CreateRetrainingRequest req) {
        return ResponseEntity.ok(svc.createRetraining(req));
    }

    @PostMapping("/retrainings/{id}/transition") @PreAuthorize(APPROVE)
    public RetrainingResponse transitionRetraining(@PathVariable Long id, @Valid @RequestBody TransitionRetrainingRequest req) {
        return svc.transitionRetraining(id, req);
    }

    // ──────── Recertification ────────

    @GetMapping("/recertifications") @PreAuthorize(READ)
    public List<RecertificationResponse> listRecerts(@RequestParam(required = false) Long modelId) {
        return svc.listRecertifications(modelId);
    }

    @PostMapping("/recertifications") @PreAuthorize(WRITE)
    public ResponseEntity<RecertificationResponse> createRec(@Valid @RequestBody CreateRecertificationRequest req) {
        return ResponseEntity.ok(svc.createRecertification(req));
    }

    @PostMapping("/recertifications/{id}/transition") @PreAuthorize(APPROVE)
    public RecertificationResponse transitionRec(@PathVariable Long id, @Valid @RequestBody TransitionRecertificationRequest req) {
        return svc.transitionRecertification(id, req);
    }

    // ──────── Timeline + dashboard ────────

    @GetMapping("/models/{id}/timeline") @PreAuthorize(READ)
    public List<EventResponse> timeline(@PathVariable Long id) { return svc.timeline(id); }

    @GetMapping("/dashboard") @PreAuthorize(READ)
    public DashboardResponse dashboard() { return svc.dashboard(); }
}
