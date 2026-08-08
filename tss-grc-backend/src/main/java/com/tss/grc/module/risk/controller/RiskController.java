package com.tss.grc.module.risk.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
@RequireModule("RISK")
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

    // F2-164 — `POST /{id}/accept` is GONE, deliberately. It was a bare
    // `RISK:WRITE` status flip to ACCEPTED with no justification, no compensating
    // controls, no expiry and no sign-off, and `RiskService.close` treated that
    // status as an exemption from the mandatory-treatment gate — so any holder of
    // RISK:WRITE could retire a CRITICAL risk in one click, permanently (the
    // expiry sweep iterates acceptance RECORDS, and this route created none).
    // `RiskAcceptance`'s own javadoc had said E-A "replaces the bare
    // RiskService.accept() status flip" since the day it was written; the flip
    // was never removed, so both lived and only one of them asked an approver.
    // The governed route is:
    //   POST /risks/{riskId}/acceptances     RISK:WRITE   — request (SUBMITTED)
    //   POST /acceptances/{id}/approve       RISK:APPROVE — → risk ACCEPTED
    // ⚠ Do not reinstate a convenience transition here. `RiskLifecycleWriterTest`
    // fails the build if any controller regains a direct route to ACCEPTED.

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    public RiskResponse close(@PathVariable Long id) {
        return riskService.close(id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasPermission('RISK', 'APPROVE')")
    @Operation(summary = "Reopen a CLOSED/ARCHIVED risk on recurrence — approval-gated (E-J)")
    public RiskResponse reopen(@PathVariable Long id) {
        return riskService.reopen(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasPermission('RISK', 'APPROVE')")
    @Operation(summary = "Archive a CLOSED risk — retire from the active register; approval-gated (E-J)")
    public RiskResponse archive(@PathVariable Long id) {
        return riskService.archive(id);
    }

    @PostMapping("/{id}/incident-bump")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    @Operation(summary = "Risk-incident retrospective: bump residual likelihood by 1 (capped at 5) when an incident is realised for this risk")
    public RiskResponse incidentBump(@PathVariable Long id) {
        return riskService.bumpLikelihoodFromIncident(id);
    }
}
