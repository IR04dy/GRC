package com.tss.grc.module.compliance.obligation.controller;

import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationKpis;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationTransitionRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationUpdateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationResponse;
import com.tss.grc.module.compliance.obligation.service.ObligationService;
import com.tss.grc.module.compliance.obligation.service.ObligationAttestationService;
import com.tss.grc.module.attachment.dto.AttachmentResponse;
import com.tss.grc.module.attachment.service.AttachmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 11 — REST surface for the regulatory obligation register.
 *
 * <pre>
 *   GET    /api/v1/compliance/obligations
 *   GET    /api/v1/compliance/obligations/kpis
 *   GET    /api/v1/compliance/obligations/due?days=30
 *   GET    /api/v1/compliance/obligations/by-regulator/{regulator}
 *   GET    /api/v1/compliance/obligations/by-owner/{ownerUserId}
 *   GET    /api/v1/compliance/obligations/{id}
 *   POST   /api/v1/compliance/obligations
 *   PATCH  /api/v1/compliance/obligations/{id}
 *   POST   /api/v1/compliance/obligations/{id}/transition
 *   DELETE /api/v1/compliance/obligations/{id}
 * </pre>
 *
 * <p>Read access is broad (every audit-readiness reader) so the
 * register can be wall-mounted on every dashboard. Writes are gated
 * to the Compliance Officer + Platform Admin.
 */
@RestController
@RequestMapping("/api/v1/compliance/obligations")
@RequiredArgsConstructor
@Tag(name = "Compliance — Obligation Register (Sprint 11)")
@SecurityRequirement(name = "bearerAuth")
public class ObligationController {

    private final ObligationService service;
    private final AttachmentService attachments;
    private final ObligationAttestationService attestationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ObligationResponse> list() {
        return service.list();
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public ObligationKpis kpis() {
        return service.kpis();
    }

    @GetMapping("/due")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ObligationResponse> due(@RequestParam(name = "days", defaultValue = "30") int days) {
        return service.listDueWithinDays(days);
    }

    @GetMapping("/by-regulator/{regulator}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ObligationResponse> byRegulator(@PathVariable String regulator) {
        return service.listByRegulator(regulator);
    }

    @GetMapping("/by-owner/{ownerUserId}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ObligationResponse> byOwner(@PathVariable Long ownerUserId) {
        return service.listByOwner(ownerUserId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public ObligationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public ObligationResponse create(@RequestBody @Valid ObligationCreateRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public ObligationResponse update(@PathVariable Long id,
                                      @RequestBody @Valid ObligationUpdateRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public ObligationResponse transition(@PathVariable Long id,
                                          @RequestBody @Valid ObligationTransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public void delete(@PathVariable Long id) {
        service.softDelete(id);
    }

    /**
     * C-11 — audit evidence-package manifest for an obligation: the obligation,
     * its own attachments, and every attestation cycle with that cycle's uploaded
     * evidence (sha256 + download links). One call an auditor can pull as the
     * basis for a regulatory-obligation evidence pack.
     */
    @GetMapping("/{id}/evidence-package")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public ObligationEvidencePackage evidencePackage(@PathVariable Long id) {
        ObligationResponse o = service.get(id);
        List<AttachmentResponse> oblAtts = attachments.list("Obligation", id);
        List<AttestationEvidence> atts = attestationService.listForObligation(id).stream()
            .map(a -> new AttestationEvidence(a.id(), a.periodLabel(), a.attestationStatus(),
                attachments.list("ObligationAttestation", a.id())))
            .toList();
        return new ObligationEvidencePackage(o, oblAtts, atts, LocalDateTime.now().toString());
    }

    /** Per-attestation evidence within an obligation package (C-11). */
    public record AttestationEvidence(
        Long attestationId, String periodLabel, String status, List<AttachmentResponse> attachments) {}

    /** Assembled audit evidence package for an obligation (C-11). */
    public record ObligationEvidencePackage(
        ObligationResponse obligation,
        List<AttachmentResponse> obligationAttachments,
        List<AttestationEvidence> attestations,
        String generatedAt) {}
}
