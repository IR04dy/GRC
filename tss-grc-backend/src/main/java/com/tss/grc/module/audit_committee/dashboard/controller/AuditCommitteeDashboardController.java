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
 *       and individually fetchable; mutated by whoever holds {@code AUDIT:WRITE}.</li>
 * </ul>
 *
 * <p>⚠ That last line used to read "mutated only by the Chair / Secretary / Platform Admin trio",
 * which was wrong in both directions: two of the three names did not exist, so it was the platform
 * administrator alone. F2-52 corrected the names; the Chair still cannot, and F2-151 records why.
 *
 * <p>⚠⚠ <b>It then read "mutated by the Board Secretary and the platform administrator", and that
 * was wrong the same way one notch weaker</b> — {@code BOARD_SECRETARY} exists and is held by
 * nobody, so it remained the platform administrator alone until F2-259 converted the tier to
 * {@code AUDIT:WRITE}. 🔑 A sentence describing a guard's audience is a claim about
 * {@code iam.user_roles}, not about {@code iam.roles}; correcting the names left it as false as it
 * found it, and more convincing. See {@link #WRITE_GUARD}.
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
     *
     * <p>⚠ <b>F2-259 — this tier is deliberately STILL a role guard, and that is a measurement rather
     * than an omission.</b> {@code hasPermission('AUDIT','READ')} is held by 7 live roles in both
     * databases <b>including {@code VIEWER} and {@code AUDITOR}</b>, so converting it would <em>widen
     * a governance dashboard's read to any authenticated reader</em> — which is precisely the defect
     * {@code ROLE_GUARD_CENSUS.md} §B3 records against {@code BoardPackController}, where five ghost
     * roles sat above a comment saying reads were for "a board/governance audience, not any
     * authenticated principal" while {@code REPORTING_READ} admitted {@code viewer}. Four live
     * non-administrator roles already read this dashboard, so there is no lockout here to fix.
     * {@code AuditCommitteeGuardConversionTest} fails the build if somebody "finishes the sweep".
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
     *
     * <h3>F2-259 — the correction above restored nobody, and said it had</h3>
     * {@code BOARD_SECRETARY} <b>exists in {@code iam.roles} and is held by no user</b> — measured in
     * dev <em>and</em> prod, because {@code role_guard_census.py} hardcodes the dev database and every
     * holder figure it publishes is therefore a statement about one of the two (F2-253). So this guard
     * stayed exactly what F2-52's own heading says it was fixing: administrator-only. Probed live —
     * {@code compliance} (GRC_ADMINISTRATOR) and {@code auditor} (INTERNAL_AUDITOR), <b>both holding
     * {@code AUDIT:WRITE}</b>, answered <b>403 on generate, recordResolution and finalize</b> while
     * {@code admin} generated at 201.
     *
     * <p>🔑 <b>Naming a role that exists is not admitting anybody: {@code hasAnyRole} tests for the
     * granted authority {@code ROLE_X}.</b> F2-52 asked whether the name was real; the question the
     * guard puts is whether somebody holds it. Same distinction V373 missed one level up, where
     * seating a role was chosen as the remedy and made 79 sites <em>satisfiable</em> rather than
     * satisfied (F2-258).
     *
     * <p>Converted to the permission every peer controller under this same {@code @RequireModule
     * ("AUDIT_LC")} SKU already uses — {@code AuditLifecycleController}, {@code FindingController},
     * {@code IaController} and {@code AuditEngagementController} all guard with
     * {@code hasPermission('AUDIT', …)}. ⚠ Note the SKU code is <b>not</b> the permission module:
     * {@code AUDIT_LC} and {@code AUDIT_COMMITTEE} have <b>no catalogued permissions at all</b>, and
     * taking the module name from {@code @RequireModule} is what made this look like F2-255's
     * catalogue gap. The catalogued module is {@code AUDIT}.
     *
     * <p>⚠ <b>Audience diff, measured before converting rather than after (V362's landmine), and
     * identical in both databases.</b> Dropped: nothing live. Admitted: {@code GRC_ADMINISTRATOR} and
     * {@code INTERNAL_AUDITOR} — stated rather than left as a side effect. Internal audit is admitted
     * deliberately: {@code AUDIT:WRITE} already lets it author the findings, engagements, annual plans
     * and working papers this snapshot aggregates, and the artefact is internal audit's own reporting
     * pack to its committee. That is the opposite case from V380, where {@code INTERNAL_AUDITOR} was
     * withheld from the audit-log integrity seal because operating it would make internal audit the
     * author of the evidence it examines.
     *
     * <p>⚠ <b>{@code BOARD_SECRETARY} held {@code AUDIT:READ} and {@code AUDIT:EXPORT} and not
     * {@code AUDIT:WRITE}</b> — so the platform already let that role read and take out the audit
     * register, and withheld only the ability to prepare its own committee's snapshot. Converting
     * alone would therefore have retired the persona this guard was written for, which is a policy
     * change disguised as a refactor. <b>V382</b> grants the one missing pair, so the conversion drops
     * nobody. Live effect today: none, since nobody holds the role; what changes is what a tenant
     * administrator gets on assigning it. ⚠ That EXPORT grant was found by V382's own self-check
     * THROWing on a first draft that assumed the pre-existing set was {@code READ} alone.
     */
    private static final String WRITE_GUARD = "hasPermission('AUDIT','WRITE')";

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
     *
     * <p>⚠ <b>F2-259 — also deliberately still a role guard, for the opposite reason to
     * {@link #READ_GUARD}.</b> Converting it to {@code hasPermission('AUDIT','WRITE')} would
     * <b>drop {@code EXTERNAL_AUDITOR}</b>, which holds no {@code AUDIT} grant — and an external
     * auditor is an outside firm's signatory, the one party here whose independence is the point and
     * who may not be a platform principal at all (F2-256's {@code EXTERNAL_AUDIT} rule). It is not a
     * B2b site either: {@code INTERNAL_AUDITOR} is named and held, so a non-administrator can already
     * attest.
     *
     * <p>⚠⚠ <b>And that makes the attestation tier WIDER than the tier producing the artefact it
     * attests to.</b> Probed pre-fix: {@code auditor} signed an attestation at <b>201</b> against a
     * snapshot it was refused permission to <b>generate</b> (403) — so the only reason nobody hit it
     * is that {@code audit_committee.dashboard_snapshot} holds <b>0 rows in dev and prod</b>. The
     * conversion below closes that inversion, since {@code INTERNAL_AUDITOR} holds {@code AUDIT:WRITE}.
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
