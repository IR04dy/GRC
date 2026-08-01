package com.tss.grc.module.risk_lc.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.risk_lc.dto.TreatmentPlanDto;
import com.tss.grc.module.risk_lc.service.TreatmentPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST endpoints for risk-lifecycle treatment plans.
 *
 *   GET    /api/risk-lc/treatment-plans                 — list all for current org
 *   GET    /api/risk-lc/treatment-plans?riskId=42       — list for one risk
 *   GET    /api/risk-lc/treatment-plans/{id}            — fetch one (id = external id)
 *   POST   /api/risk-lc/treatment-plans                 — create (status starts DRAFT)
 *   POST   /api/risk-lc/treatment-plans/{id}/transition — state machine transition
 *   DELETE /api/risk-lc/treatment-plans/{id}            — soft delete
 *
 * Auth: {@code @PreAuthorize} maps to {@code RISK:READ} / {@code RISK:WRITE}
 * via {@code ModulePermissionEvaluator} (hooked up in SecurityConfig).
 */
@RestController
@RequestMapping("/api/risk-lc/treatment-plans")
@RequiredArgsConstructor
@RequireModule("RISK")
public class TreatmentPlanController {

    private final TreatmentPlanService service;

    @GetMapping
    @PreAuthorize("hasPermission('RISK','READ')")
    public List<TreatmentPlanDto> list(@RequestParam(required = false) Long riskId) {
        return riskId != null ? service.listForRisk(riskId) : service.listForOrg();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('RISK','READ')")
    public TreatmentPlanDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ResponseEntity<TreatmentPlanDto> create(@RequestBody @Valid TreatmentPlanDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public TreatmentPlanDto transition(@PathVariable String id,
                                       @RequestBody @Valid TreatmentPlanDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('RISK','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
