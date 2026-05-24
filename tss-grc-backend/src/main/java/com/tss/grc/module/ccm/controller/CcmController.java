package com.tss.grc.module.ccm.controller;

import com.tss.grc.module.ccm.dto.CcmDtos.DashboardSnapshot;
import com.tss.grc.module.ccm.dto.CcmDtos.TestDefinitionRequest;
import com.tss.grc.module.ccm.dto.CcmDtos.TestDefinitionResponse;
import com.tss.grc.module.ccm.dto.CcmDtos.TestRunResponse;
import com.tss.grc.module.ccm.service.CcmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for Continuous Control Monitoring (CCM) — Sprint 11.
 *
 * <p>Read-side wide (auditors, compliance officers, the audit
 * committee dashboard all consume CCM data). Write-side restricted to
 * the CISO + compliance officer + platform admin trio that owns
 * control-test definitions in practice.
 */
@RestController
@RequestMapping("/api/v1/ccm")
@RequiredArgsConstructor
@Tag(name = "CCM — Continuous Control Monitoring")
@SecurityRequirement(name = "bearerAuth")
public class CcmController {

    private static final String READ_GUARD =
            "hasAnyRole('CISO','RISK_MANAGER','COMPLIANCE_OFFICER','INTERNAL_AUDITOR','AUDITOR','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('CISO','COMPLIANCE_OFFICER','PLATFORM_ADMIN')";
    private static final String RUN_GUARD =
            "hasAnyRole('CISO','COMPLIANCE_OFFICER','PLATFORM_ADMIN')";
    private static final String TOGGLE_GUARD =
            "hasAnyRole('CISO','PLATFORM_ADMIN')";

    private final CcmService service;

    // ─── Test definitions ─────────────────────────────────────────

    @GetMapping("/tests")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List CCM test definitions for the current tenant.")
    public List<TestDefinitionResponse> listTests() {
        return service.listDefinitions();
    }

    @GetMapping("/tests/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one test definition by external id.")
    public TestDefinitionResponse getTest(@PathVariable UUID externalId) {
        return service.getDefinition(externalId);
    }

    @PostMapping("/tests")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Define a new CCM test (starts disabled).")
    public ResponseEntity<TestDefinitionResponse> createTest(@Valid @RequestBody TestDefinitionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDefinition(req));
    }

    @PutMapping("/tests/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update a CCM test definition.")
    public TestDefinitionResponse updateTest(@PathVariable UUID externalId,
                                             @Valid @RequestBody TestDefinitionRequest req) {
        return service.updateDefinition(externalId, req);
    }

    @PostMapping("/tests/{externalId}/enable")
    @PreAuthorize(TOGGLE_GUARD)
    @Operation(summary = "Enable a CCM test — runner can now touch the target system.")
    public TestDefinitionResponse enableTest(@PathVariable UUID externalId) {
        return service.enableTest(externalId);
    }

    @PostMapping("/tests/{externalId}/disable")
    @PreAuthorize(TOGGLE_GUARD)
    @Operation(summary = "Disable a CCM test — scheduler skips it on the next tick.")
    public TestDefinitionResponse disableTest(@PathVariable UUID externalId) {
        return service.disableTest(externalId);
    }

    @PostMapping("/tests/{externalId}/run-now")
    @PreAuthorize(RUN_GUARD)
    @Operation(summary = "Execute one CCM test synchronously and return the recorded run.")
    public TestRunResponse runNow(@PathVariable UUID externalId) {
        return service.runTestNow(externalId);
    }

    // ─── Runs ─────────────────────────────────────────────────────

    @GetMapping("/tests/{externalId}/runs")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get the most-recent N runs for one CCM test.")
    public List<TestRunResponse> getRuns(@PathVariable UUID externalId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return service.getRunsForTest(externalId, limit);
    }

    @GetMapping("/runs/recent")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get the most-recent runs across all CCM tests for the tenant.")
    public List<TestRunResponse> recent(@RequestParam(defaultValue = "50") int limit) {
        return service.listLatestRuns(limit);
    }

    // ─── Dashboard ────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "KPI snapshot for the audit-committee dashboard.")
    public DashboardSnapshot dashboard() {
        return service.dashboard();
    }
}
