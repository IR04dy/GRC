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
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
 *       and individually fetchable; mutated by the Board Secretary and the
 *       platform administrator.</li>
 * </ul>
 *
 * <p>⚠ That last line used to read "mutated only by the Chair / Secretary / Platform Admin trio",
 * which was wrong in both directions: two of the three names did not exist, so it was the platform
 * administrator alone. F2-52 corrected the names; the Chair still cannot, and F2-151 records why.
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
@RequireModule("AUDIT_LC")
@Tag(name = "Audit Committee — Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class AuditCommitteeDashboardController {

    /**
     * Broad read — every assurance role consumes the live dashboard.
     *
     * <p>F2-52 — was the same list plus {@code AUDIT_COMMITTEE_MEMBER},
     * {@code AUDIT_COMMITTEE_CHAIR} and {@code BOARD_MEMBER}. None of the three is a row in
     * {@code iam.roles} in either database, so they narrowed nothing here: six real roles already
     * read the dashboard. Removed because a guard should read as tightly as it behaves.
     * {@code COMPANY_SECRETARY} became {@code BOARD_SECRETARY} — see {@link #WRITE_GUARD}.
     */
    private static final String READ_GUARD =
            "hasAnyRole('CISO','RISK_MANAGER','COMPLIANCE_OFFICER','INTERNAL_AUDITOR',"
                    + "'EXTERNAL_AUDITOR','BOARD_SECRETARY','PLATFORM_ADMIN')";

    /**
     * Generate / record / finalize.
     *
     * <h3>F2-52 — this said "restricted to the Chair + Secretary + Platform Admin" and admitted the
     * platform administrator alone</h3>
     * It was {@code hasAnyRole('AUDIT_COMMITTEE_CHAIR','COMPANY_SECRETARY','PLATFORM_ADMIN')}.
     * <b>Two of those three names exist in no database</b>, and a role guard never reaches
     * {@code ModulePermissionEvaluator}, so it never reaches the PLATFORM_ADMIN bypass either: the
     * committee's own snapshot — the artefact SAMA CG §6 and CMA CG Article 54 require it to report
     * from — could be generated, resolved and finalised by nobody but the platform administrator.
     *
     * <p>🔑 <b>{@code COMPANY_SECRETARY} was a near-miss.</b> The platform ships
     * {@code BOARD_SECRETARY} ("Board Secretary"), which is that person, and the guard did not name
     * it. Corrected, which restores one of the two intended personas.
     *
     * <p>⚠ <b>The Chair cannot be restored, and that is a finding rather than an omission.</b> No
     * role expresses committee membership, and the roster that could — F2-26's
     * {@code committee_member_terms.member_user_id} — is <b>NULL on all five audit-committee seats</b>,
     * so "is this user the Chair" is unanswerable from data. Adding an entity-bound leg that can never
     * be true would be a control that cannot fire (F2-112's shape). Filed as F2-151 instead.
     */
    private static final String WRITE_GUARD =
            "hasAnyRole('BOARD_SECRETARY','PLATFORM_ADMIN')";

    /**
     * Attestation.
     *
     * <p>F2-52 — this said "open to every voting Committee member + the Chair + external auditor" and
     * was open to <b>neither a member nor the Chair</b>: those two names are ghosts, leaving the two
     * auditors, the secretary-that-wasn't, and the platform administrator. An audit-committee
     * attestation that only the auditors can sign inverts who is assuring whom.
     *
     * <p>⚠ And the capacity is still self-declared — {@code AttestRequest.attestedRole} is written
     * to the row verbatim, so an admitted {@code INTERNAL_AUDITOR} can record an attestation as
     * {@code CHAIR}. The signer's <em>identity</em> is stamped from the principal and hashed; the
     * <em>role they signed in</em> is not checked against anything. F2-151 covers both halves,
     * because validating it needs the same roster linkage the Chair leg needs.
     */
    private static final String ATTEST_GUARD =
            "hasAnyRole('EXTERNAL_AUDITOR','INTERNAL_AUDITOR','BOARD_SECRETARY','PLATFORM_ADMIN')";

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
