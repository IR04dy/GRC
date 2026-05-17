package com.tss.grc.module.risk.scenario.controller;

import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.ArchiveRequest;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.CreateRequest;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.RejectRequest;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.Response;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.UpdateRequest;
import com.tss.grc.module.risk.scenario.service.RiskScenarioService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for risk scenario analysis.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET    /api/risk/scenarios                    — paginated list (org-scoped)</li>
 *   <li>GET    /api/risk/scenarios?status=DRAFT       — filter by status</li>
 *   <li>GET    /api/risk/scenarios/by-risk/{riskId}   — every scenario for one risk</li>
 *   <li>GET    /api/risk/scenarios/{id}               — single scenario</li>
 *   <li>POST   /api/risk/scenarios                    — open a new scenario</li>
 *   <li>PUT    /api/risk/scenarios/{id}               — update while DRAFT</li>
 *   <li>POST   /api/risk/scenarios/{id}/approve       — sign off</li>
 *   <li>POST   /api/risk/scenarios/{id}/reject        — decline (reason required)</li>
 *   <li>POST   /api/risk/scenarios/{id}/archive       — archive (reason required)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/risk/scenarios")
@RequiredArgsConstructor
@Tag(name = "Risk Scenarios")
@SecurityRequirement(name = "bearerAuth")
public class RiskScenarioController {

    private final RiskScenarioService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List risk scenarios (paginated, org-scoped).")
    public Page<Response> list(@RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, size);
        return (status == null
                ? service.list(pageable)
                : service.listByStatus(status, pageable)).map(Response::from);
    }

    @GetMapping("/by-risk/{riskId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "All scenarios attached to one risk (newest first).")
    public List<Response> byRisk(@PathVariable Long riskId) {
        return service.forRisk(riskId).stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Single scenario by id.")
    public Response get(@PathVariable Long id) {
        return Response.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Open a new scenario (status starts DRAFT).")
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(201).body(Response.from(service.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a DRAFT scenario.")
    public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return Response.from(service.update(id, req));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Approve a DRAFT scenario.")
    public Response approve(@PathVariable Long id, Authentication auth) {
        return Response.from(service.approve(id, usernameOf(auth), userIdOf(auth)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reject a DRAFT scenario.")
    public Response reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return Response.from(service.reject(id, req.reason()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Archive a scenario (supersede with a newer run, etc.).")
    public Response archive(@PathVariable Long id, @Valid @RequestBody ArchiveRequest req,
                            Authentication auth) {
        return Response.from(service.archive(id, req.reason(), userIdOf(auth)));
    }

    // ─── Helpers ──────────────────────────────────────────────────────
    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
    private static String usernameOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUsername();
        return auth != null ? auth.getName() : null;
    }
}
