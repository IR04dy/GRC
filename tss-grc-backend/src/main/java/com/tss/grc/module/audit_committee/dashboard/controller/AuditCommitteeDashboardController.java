package com.tss.grc.module.audit_committee.dashboard.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AttestRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AttestationResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.FinalizeRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.GenerateSnapshotRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.LiveDashboardResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.RecordResolutionRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.SnapshotResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.SnapshotSummary;
import com.tss.grc.module.audit_committee.dashboard.service.AuditCommitteeDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for the Audit Committee Dashboard — Sprint 11.
 *
 * <p>Two read paths:
 * <ul>
 *   <li><b>Live</b> — recomputed on every call by aggregating across
 *       module services. Broad read guard so every assurance-side role
 *       can see the same view the Committee will see.</li>
 *   <li><b>Snapshot</b> — frozen JSON tied to a meeting moment. Listed
 *       and individually fetchable; mutated only by the
 *       Chair / Secretary / Platform Admin trio.</li>
 * </ul>
 *
 * <p>SAMA Corporate Governance Regulations §6 and CMA Listed-Companies
 * CG Code Article 54 frame this surface — Audit Committee
 * responsibilities require regular reporting on risk, internal
 * control, compliance and findings. The Committee Chair is
 * customarily an independent non-executive director.
 */
@RestController
@RequestMapping("/api/v1/audit-committee/dashboard")
@RequiredArgsConstructor
@Tag(name = "Audit Committee — Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class AuditCommitteeDashboardController {

    /** Broad read — every assurance role consumes the live dashboard. */
    private static final String READ_GUARD =
            "hasAnyRole('AUDIT_COMMITTEE_MEMBER','AUDIT_COMMITTEE_CHAIR','BOARD_MEMBER',"
                    + "'CISO','RISK_MANAGER','COMPLIANCE_OFFICER','INTERNAL_AUDITOR',"
                    + "'EXTERNAL_AUDITOR','COMPANY_SECRETARY','PLATFORM_ADMIN')";

    /** Generate / record / finalize is restricted to the Chair + Secretary + Platform Admin. */
    private static final String WRITE_GUARD =
            "hasAnyRole('AUDIT_COMMITTEE_CHAIR','COMPANY_SECRETARY','PLATFORM_ADMIN')";

    /** Attestation is open to every voting Committee member + the Chair + external auditor. */
    private static final String ATTEST_GUARD =
            "hasAnyRole('AUDIT_COMMITTEE_MEMBER','AUDIT_COMMITTEE_CHAIR',"
                    + "'EXTERNAL_AUDITOR','COMPANY_SECRETARY','INTERNAL_AUDITOR','PLATFORM_ADMIN')";

    private final AuditCommitteeDashboardService service;

    // ─── Live dashboard ───────────────────────────────────────────

    @GetMapping("/live")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Compute and return the live Audit Committee dashboard for the current tenant.")
    public LiveDashboardResponse live() {
        Long orgId = TenantContext.requireOrganisationId();
        return service.computeLiveDashboard(orgId);
    }

    // ─── Snapshots ────────────────────────────────────────────────

    @PostMapping("/snapshots")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Freeze the live dashboard as a meeting snapshot.")
    public ResponseEntity<SnapshotResponse> generate(@Valid @RequestBody GenerateSnapshotRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generateSnapshot(req));
    }

    @GetMapping("/snapshots")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List past Audit Committee dashboard snapshots, newest first.")
    public List<SnapshotSummary> list() {
        return service.listSnapshots();
    }

    @GetMapping("/snapshots/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Fetch one frozen Audit Committee dashboard snapshot.")
    public SnapshotResponse get(@PathVariable UUID externalId) {
        return service.getSnapshot(externalId);
    }

    @PostMapping("/snapshots/{externalId}/attest")
    @PreAuthorize(ATTEST_GUARD)
    @Operation(summary = "Record a member's signed attestation against a snapshot.")
    public ResponseEntity<AttestationResponse> attest(@PathVariable UUID externalId,
                                                       @Valid @RequestBody AttestRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.attestSnapshot(externalId, req));
    }

    @PostMapping("/snapshots/{externalId}/resolutions")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Append a captured resolution to a snapshot.")
    public SnapshotResponse recordResolution(@PathVariable UUID externalId,
                                              @Valid @RequestBody RecordResolutionRequest req) {
        return service.recordResolution(externalId, req);
    }

    @PostMapping("/snapshots/{externalId}/finalize")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Finalize the snapshot — capture executive summary and lock further edits.")
    public SnapshotResponse finalizeSnapshot(@PathVariable UUID externalId,
                                              @Valid @RequestBody FinalizeRequest req) {
        return service.finalizeSnapshot(externalId, req);
    }
}
