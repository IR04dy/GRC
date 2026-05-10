package com.tss.grc.module.risk.controller;

import com.tss.grc.module.risk.dto.RiskRequest;
import com.tss.grc.module.risk.dto.RiskResponse;
import com.tss.grc.module.risk.service.RiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/risk/risks")
@RequiredArgsConstructor
@Tag(name = "Risk Management")
@SecurityRequirement(name = "bearerAuth")
public class RiskController {

    private final RiskService riskService;

    @GetMapping
    @PreAuthorize("hasPermission('RISK', 'READ')")
    @Operation(summary = "List risks with optional status filter")
    public Page<RiskResponse> list(
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return riskService.list(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    public RiskResponse get(@PathVariable Long id) {
        return riskService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RiskResponse create(@Valid @RequestBody RiskRequest request) {
        return riskService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    public RiskResponse update(@PathVariable Long id, @Valid @RequestBody RiskRequest request) {
        return riskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        riskService.delete(id);
    }

    // ── Risk lifecycle workflow (ISO 31000 / COSO ERM) ────────────────────────
    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    public RiskResponse submitForReview(@PathVariable Long id) {
        return riskService.submitForReview(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    public RiskResponse approve(@PathVariable Long id) {
        return riskService.approve(id);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    public RiskResponse accept(@PathVariable Long id) {
        return riskService.accept(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    public RiskResponse close(@PathVariable Long id) {
        return riskService.close(id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    public RiskResponse reopen(@PathVariable Long id) {
        return riskService.reopen(id);
    }

    @PostMapping("/{id}/incident-bump")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    @Operation(summary = "Risk-incident retrospective: bump residual likelihood by 1 (capped at 5) when an incident is realised for this risk")
    public RiskResponse incidentBump(@PathVariable Long id) {
        return riskService.bumpLikelihoodFromIncident(id);
    }
}
