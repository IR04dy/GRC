package com.tss.grc.module.governance.pivot.controller;

import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.PivotResponse;
import com.tss.grc.module.governance.pivot.service.RiskControlFindingPivotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the Risk-Control-Finding Pivot view — Sprint 11.
 *
 * <p>Three lenses, one shape:
 * <ul>
 *   <li><b>{@code GET /risk/{id}}</b> — risk-center pivot</li>
 *   <li><b>{@code GET /control/{id}}</b> — control-center pivot</li>
 *   <li><b>{@code GET /finding/{id}}</b> — finding-center pivot</li>
 * </ul>
 *
 * <p>Broad read guard — every assurance-side role consumes this view
 * for engagement scoping and audit-committee preparation. No write
 * surface — the pivot never mutates state.
 *
 * <p>404 semantics: if the focal entity is invisible under the
 * current tenant context, the service throws
 * {@link jakarta.persistence.EntityNotFoundException}, which the
 * global exception handler maps to HTTP 404.
 */
@RestController
@RequestMapping("/api/v1/governance/pivot")
@RequiredArgsConstructor
@Tag(name = "Governance — Risk-Control-Finding Pivot")
@SecurityRequirement(name = "bearerAuth")
public class RiskControlFindingPivotController {

    private static final String READ_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','COMPLIANCE_OFFICER',"
                    + "'INTERNAL_AUDITOR','EXTERNAL_AUDITOR','AUDITOR',"
                    + "'AUDIT_COMMITTEE_MEMBER','AUDIT_COMMITTEE_CHAIR',"
                    + "'BOARD_MEMBER','PLATFORM_ADMIN')";

    private final RiskControlFindingPivotService service;

    @GetMapping("/risk/{riskId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Pivot around a single risk — surrounding controls, findings, CAPA, CCM coverage.")
    public PivotResponse byRisk(@PathVariable Long riskId) {
        return service.buildRiskPivot(riskId);
    }

    @GetMapping("/control/{controlId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Pivot around a single control — surrounding risks, findings, CAPA, CCM coverage.")
    public PivotResponse byControl(@PathVariable Long controlId) {
        return service.buildControlPivot(controlId);
    }

    @GetMapping("/finding/{findingId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Pivot around a single finding — surrounding controls, risks, CAPA, CCM coverage.")
    public PivotResponse byFinding(@PathVariable Long findingId) {
        return service.buildFindingPivot(findingId);
    }
}
