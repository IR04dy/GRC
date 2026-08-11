package com.tss.grc.module.risk.bowtie.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.ApproveRequest;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieAnalysisRequest;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieAnalysisResponse;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieConsequenceDto;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieFullResponse;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieMitigatingControlDto;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTiePreventiveControlDto;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieThreatDto;
import com.tss.grc.module.risk.bowtie.service.BowTieAnalysisService;
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
 * REST surface for ISO 31010 bow-tie analyses.
 *
 * <p>All endpoints are guarded with {@code hasAnyRole} (not
 * {@code hasAnyAuthority}) — CustomUserDetailsService prefixes roles
 * with {@code ROLE_} so {@code hasAuthority} would silently reject
 * legitimate callers. Pattern from PR #174.
 */
@RestController
@RequestMapping("/api/v1/risk/bowtie")
@RequiredArgsConstructor
@RequireModule("RISK")
@Tag(name = "Risk Bow-Tie Analysis (ISO 31010)")
@SecurityRequirement(name = "bearerAuth")
public class BowTieController {

    private static final String ROLES_GUARD =
            "hasAnyRole('RISK_MANAGER','CISO','PLATFORM_ADMIN','AUDITOR')";

    private final BowTieAnalysisService service;

    @GetMapping
    @PreAuthorize(ROLES_GUARD)
    @Operation(summary = "List bow-tie analyses (newest assessment date first).")
    public List<BowTieAnalysisResponse> list() {
        return service.list();
    }

    @GetMapping("/{externalId}")
    @PreAuthorize(ROLES_GUARD)
    @Operation(summary = "Full bow-tie detail — header + threats + consequences + both control sides.")
    public BowTieFullResponse get(@PathVariable UUID externalId) {
        return service.getFull(externalId);
    }

    @PostMapping
    @PreAuthorize(ROLES_GUARD)
    @Operation(summary = "Create a new bow-tie (status starts DRAFT).")
    public ResponseEntity<BowTieAnalysisResponse> create(@Valid @RequestBody BowTieAnalysisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{externalId}")
    @PreAuthorize(ROLES_GUARD)
    @Operation(summary = "Update header fields of a DRAFT or IN_REVIEW bow-tie.")
    public BowTieAnalysisResponse update(@PathVariable UUID externalId,
                                         @Valid @RequestBody BowTieAnalysisRequest request) {
        return service.update(externalId, request);
    }

    // ─── Threats ──────────────────────────────────────────────────
    @PostMapping("/{externalId}/threats")
    @PreAuthorize(ROLES_GUARD)
    public BowTieThreatDto addThreat(@PathVariable UUID externalId,
                                     @Valid @RequestBody BowTieThreatDto dto) {
        return service.addThreat(externalId, dto);
    }

    @DeleteMapping("/threats/{threatExternalId}")
    @PreAuthorize(ROLES_GUARD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeThreat(@PathVariable UUID threatExternalId) {
        service.removeThreat(threatExternalId);
    }

    // ─── Consequences ─────────────────────────────────────────────
    @PostMapping("/{externalId}/consequences")
    @PreAuthorize(ROLES_GUARD)
    public BowTieConsequenceDto addConsequence(@PathVariable UUID externalId,
                                               @Valid @RequestBody BowTieConsequenceDto dto) {
        return service.addConsequence(externalId, dto);
    }

    @DeleteMapping("/consequences/{consExternalId}")
    @PreAuthorize(ROLES_GUARD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeConsequence(@PathVariable UUID consExternalId) {
        service.removeConsequence(consExternalId);
    }

    // ─── Controls ────────────────────────────────────────────────
    @PostMapping("/threats/{threatExternalId}/preventive-controls")
    @PreAuthorize(ROLES_GUARD)
    public BowTiePreventiveControlDto addPreventive(@PathVariable UUID threatExternalId,
                                                    @Valid @RequestBody BowTiePreventiveControlDto dto) {
        return service.addPreventiveControl(threatExternalId, dto);
    }

    @PostMapping("/consequences/{consExternalId}/mitigating-controls")
    @PreAuthorize(ROLES_GUARD)
    public BowTieMitigatingControlDto addMitigating(@PathVariable UUID consExternalId,
                                                    @Valid @RequestBody BowTieMitigatingControlDto dto) {
        return service.addMitigatingControl(consExternalId, dto);
    }

    // ─── Workflow ────────────────────────────────────────────────
    @PostMapping("/{externalId}/submit")
    @PreAuthorize(ROLES_GUARD)
    @Operation(summary = "Submit a DRAFT bow-tie for review.")
    public BowTieAnalysisResponse submit(@PathVariable UUID externalId) {
        return service.submitForReview(externalId);
    }

    @PostMapping("/{externalId}/approve")
    @PreAuthorize(ROLES_GUARD)
    @Operation(summary = "Approve an IN_REVIEW bow-tie — recomputes effectiveness + residual rating.")
    public BowTieAnalysisResponse approve(@PathVariable UUID externalId,
                                          @Valid @RequestBody(required = false) ApproveRequest request) {
        return service.approve(externalId, request);
    }

    @DeleteMapping("/{externalId}")
    @PreAuthorize(ROLES_GUARD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive (soft-delete) a bow-tie.")
    public void archive(@PathVariable UUID externalId) {
        service.archive(externalId);
    }
}
