package com.tss.grc.module.fraud.controller;

import com.tss.grc.module.fraud.dto.FraudDtos.CaseResponse;
import com.tss.grc.module.fraud.dto.FraudDtos.CaseUpdateResponse;
import com.tss.grc.module.fraud.dto.FraudDtos.CloseCaseRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.CreateCaseRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.DashboardResponse;
import com.tss.grc.module.fraud.dto.FraudDtos.InvestigationUpdateRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.OutcomeRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.TriageRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.TypologyResponse;
import com.tss.grc.module.fraud.service.FraudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for the Sprint 14 Fraud Risk Management module.
 *
 * <p>All endpoints are tenant-scoped via the JWT-derived {@code TenantContext}.
 *
 * <h3>Role guards</h3>
 * <ul>
 *   <li>{@link #READ_GUARD} — case + dashboard reads: PLATFORM_ADMIN,
 *       FRAUD_OFFICER, FRAUD_INVESTIGATOR, COMPLIANCE_OFFICER,
 *       INTERNAL_AUDITOR.</li>
 *   <li>{@link #WRITE_GUARD} — case mutations: PLATFORM_ADMIN,
 *       FRAUD_OFFICER, FRAUD_INVESTIGATOR.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Risk Management (Sprint 14)")
@SecurityRequirement(name = "bearerAuth")
public class FraudController {

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','FRAUD_OFFICER','FRAUD_INVESTIGATOR',"
            + "'COMPLIANCE_OFFICER','INTERNAL_AUDITOR','RISK_MANAGER')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','FRAUD_OFFICER','FRAUD_INVESTIGATOR')";

    private final FraudService service;

    // ─── Typologies ─────────────────────────────────────────────────

    @GetMapping("/typologies")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List active fraud typologies visible to the tenant.")
    public List<TypologyResponse> listTypologies() {
        return service.listTypologies();
    }

    // ─── Case lifecycle ─────────────────────────────────────────────

    @PostMapping("/cases")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Open a fraud case (status=DETECTED).")
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCase(req));
    }

    @GetMapping("/cases")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Paginated list of fraud cases for the current tenant.")
    public Page<CaseResponse> listCases(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        return service.listCases(page, Math.min(size, 200));
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get a single fraud case by id.")
    public CaseResponse getCase(@PathVariable Long id) {
        return service.getCase(id);
    }

    @GetMapping("/cases/{id}/timeline")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List investigation timeline updates for a case (newest first).")
    public List<CaseUpdateResponse> listUpdates(@PathVariable Long id) {
        return service.listUpdates(id);
    }

    @PostMapping("/cases/{id}/triage")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record triage decision; moves DETECTED→INVESTIGATING or DETECTED→FALSE_POSITIVE.")
    public CaseResponse triage(@PathVariable Long id, @Valid @RequestBody TriageRequest req) {
        return service.triage(id, req);
    }

    @PostMapping("/cases/{id}/updates")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Append an investigation update (evidence, finding, STR filed, etc.).")
    public ResponseEntity<CaseUpdateResponse> recordUpdate(@PathVariable Long id,
                                                             @Valid @RequestBody InvestigationUpdateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordInvestigationUpdate(id, req));
    }

    @PostMapping("/cases/{id}/outcome")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Set investigation outcome — CONFIRMED, CLEARED, or FALSE_POSITIVE.")
    public CaseResponse setOutcome(@PathVariable Long id, @Valid @RequestBody OutcomeRequest req) {
        return service.setOutcome(id, req);
    }

    @PostMapping("/cases/{id}/close")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Close the case (terminal — requires outcome already set).")
    public CaseResponse close(@PathVariable Long id, @Valid @RequestBody CloseCaseRequest req) {
        return service.closeCase(id, req);
    }

    // ─── Dashboard ──────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Aggregated counts + YTD confirmed loss + recent cases.")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
