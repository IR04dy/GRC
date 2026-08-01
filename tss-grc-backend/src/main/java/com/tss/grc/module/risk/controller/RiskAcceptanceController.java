package com.tss.grc.module.risk.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.risk.dto.RiskAcceptanceDtos.ApproveDto;
import com.tss.grc.module.risk.dto.RiskAcceptanceDtos.CreateDto;
import com.tss.grc.module.risk.dto.RiskAcceptanceDtos.Response;
import com.tss.grc.module.risk.dto.RiskAcceptanceDtos.RevokeDto;
import com.tss.grc.module.risk.service.RiskAcceptanceService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Risk Acceptance REST surface — ERM remediation epic E-A.
 *
 * <pre>
 *   GET  /api/v1/risk/risks/{riskId}/acceptances          list a risk's acceptances
 *   POST /api/v1/risk/risks/{riskId}/acceptances          request (SUBMITTED)
 *   POST /api/v1/risk/acceptances/{id}/approve            APPROVE (RISK_APPROVE) → risk ACCEPTED
 *   POST /api/v1/risk/acceptances/{id}/reject             reject a submission
 *   POST /api/v1/risk/acceptances/{id}/revoke             revoke → reopen risk
 *   POST /api/v1/risk/acceptances/expire-sweep            flip lapsed acceptances → reopen risks
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@RequireModule("RISK")
@Tag(name = "Risk Management — Acceptance")
@SecurityRequirement(name = "bearerAuth")
public class RiskAcceptanceController {

    private final RiskAcceptanceService service;

    @GetMapping("/risks/{riskId}/acceptances")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    public List<Response> listForRisk(@PathVariable Long riskId) {
        return service.listForRisk(riskId).stream().map(Response::from).toList();
    }

    @PostMapping("/risks/{riskId}/acceptances")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a time-boxed risk acceptance (SUBMITTED; awaits RISK_APPROVE sign-off)")
    public Response request(@PathVariable Long riskId, @Valid @RequestBody CreateDto dto,
                            @AuthenticationPrincipal GrcUserDetails me) {
        return Response.from(service.request(riskId, dto, me != null ? me.getUserId() : null));
    }

    @PostMapping("/acceptances/{id}/approve")
    @PreAuthorize("hasPermission('RISK', 'APPROVE')")
    @Operation(summary = "Approve an acceptance — sets the parent risk to ACCEPTED (Board sign-off required if above appetite)")
    public Response approve(@PathVariable Long id, @RequestBody(required = false) ApproveDto dto,
                            @AuthenticationPrincipal GrcUserDetails me) {
        return Response.from(service.approve(id, dto, me != null ? me.getUsername() : null));
    }

    @PostMapping("/acceptances/{id}/reject")
    @PreAuthorize("hasPermission('RISK', 'APPROVE')")
    public Response reject(@PathVariable Long id, @Valid @RequestBody RevokeDto dto,
                           @AuthenticationPrincipal GrcUserDetails me) {
        return Response.from(service.reject(id, dto.reason(), me != null ? me.getUsername() : null));
    }

    @PostMapping("/acceptances/{id}/revoke")
    @PreAuthorize("hasPermission('RISK', 'APPROVE')")
    public Response revoke(@PathVariable Long id, @Valid @RequestBody RevokeDto dto,
                           @AuthenticationPrincipal GrcUserDetails me) {
        return Response.from(service.revoke(id, dto.reason(), me != null ? me.getUsername() : null));
    }

    @PostMapping("/acceptances/expire-sweep")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    @Operation(summary = "Flip APPROVED acceptances past expiry to EXPIRED and reopen their risks")
    public Map<String, Object> expireSweep() {
        return Map.of("expired", service.expireSweep());
    }
}
