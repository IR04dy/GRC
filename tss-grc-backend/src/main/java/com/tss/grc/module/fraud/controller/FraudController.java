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
 * <h3>Authorisation — {@code FRAUD:READ} / {@code FRAUD:WRITE} (F2-05, 2026-07-30)</h3>
 *
 * <p><b>These guards used to name roles, and two of them did not exist.</b> The previous form was
 * two constants — {@code hasAnyRole('PLATFORM_ADMIN','FRAUD_OFFICER','FRAUD_INVESTIGATOR',
 * 'COMPLIANCE_OFFICER','INTERNAL_AUDITOR','RISK_MANAGER')} for reads and the first three of those
 * for writes. <b>{@code FRAUD_OFFICER} and {@code FRAUD_INVESTIGATOR} are absent from
 * {@code iam.roles}</b>, and a role guard is not evaluated by {@code ModulePermissionEvaluator}, so
 * it gets no PLATFORM_ADMIN bypass either — the population that passed was exactly the surviving
 * real names. On the write guard those two ghosts were the <em>only</em> non-admin legs, so the
 * whole case lifecycle (open, triage, update, outcome, close) was <b>PLATFORM_ADMIN-only</b>.
 * Live before the change: {@code compliance} — COMPLIANCE_OFFICER + GRC_ADMINISTRATOR, 86 of 96
 * permissions — got <b>403</b> opening a case.
 *
 * <p>⚠ The old javadoc listed READ_GUARD's roles and <b>omitted RISK_MANAGER, which the constant
 * did include</b>. A guard described in prose beside itself still drifted from itself; that is part
 * of why the census reads the compiled annotation rather than the documentation.
 *
 * <p><b>V328</b> catalogues {@code FRAUD:READ} and {@code FRAUD:WRITE} — this module had no entry in
 * {@code iam.permissions} at all, so unlike the {@code audit_lc} and {@code tprm} conversions there
 * was no existing target to inherit. Reads go to exactly the three real roles the old guard named
 * (COMPLIANCE_OFFICER, INTERNAL_AUDITOR, RISK_MANAGER) plus the two administrators that must be able
 * to read what they may write, so <b>the read population does not widen at all</b> — VIEWER holds no
 * FRAUD permission, unlike AUDIT and TPRM where it already did.
 *
 * <p>Writes go to GRC_ADMINISTRATOR and ORG_ADMIN, derived rather than chosen: they are the only two
 * roles holding ≥80% of the platform's module WRITE actions (measured — ORG_ADMIN 24 of 26,
 * GRC_ADMINISTRATOR 21 of 26; the next is COMPLIANCE_OFFICER at 14).
 *
 * <p><b>Which persona investigates fraud is left open on purpose.</b> The ghost roles are not
 * created and no existing role is anointed — FORENSICS_LEAD, IR_LEAD and SECURITY_ANALYST all exist
 * and all are plausible, which is precisely why picking one here would be a guess dressed as a fix.
 * What changed is that the answer is now a checkbox in the role editor rather than a Java edit and a
 * jar: {@code FRAUD:WRITE} exists and can be granted. Before V328 that sentence was false.
 *
 * @see com.tss.grc.security.RoleGuardConversionRatchetTest the census that found this
 */
@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Risk Management (Sprint 14)")
@SecurityRequirement(name = "bearerAuth")
public class FraudController {

    private static final String READ_GUARD = "hasPermission('FRAUD','READ')";
    private static final String WRITE_GUARD = "hasPermission('FRAUD','WRITE')";

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
