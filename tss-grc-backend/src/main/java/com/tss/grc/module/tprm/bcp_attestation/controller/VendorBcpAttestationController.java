package com.tss.grc.module.tprm.bcp_attestation.controller;

import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationResponse;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationReviewRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationSubmitRequest;
import com.tss.grc.module.tprm.bcp_attestation.service.VendorBcpAttestationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Slice 10-D — REST surface for vendor BCP attestation rows.
 *
 * <pre>
 *   GET    /api/v1/tprm/bcp-attestations?campaignId=N        — list (broad read)
 *   GET    /api/v1/tprm/bcp-attestations?campaignId=N&status=… — filtered list
 *   GET    /api/v1/tprm/bcp-attestations/{id}                — detail
 *   GET    /api/v1/tprm/bcp-attestations/by-token/{token}    — vendor portal lookup (token auth)
 *   POST   /api/v1/tprm/bcp-attestations/{id}/submit         — vendor / TPRM Lead manual submit
 *   POST   /api/v1/tprm/bcp-attestations/by-token/{token}/submit — vendor portal submit (token auth)
 *   POST   /api/v1/tprm/bcp-attestations/{id}/accept         — reviewer accept
 *   POST   /api/v1/tprm/bcp-attestations/{id}/reject         — reviewer reject
 *   POST   /api/v1/tprm/bcp-attestations/{id}/mark-out-of-scope
 * </pre>
 *
 * <p>Vendor-portal authentication for v1 is by opaque attestation token
 * (UUID embedded in the URL). A full vendor portal (separate auth realm,
 * MFA, evidence file upload, audit) is a follow-up — documented in the
 * Slice 10-D PR description.
 */
@RestController
@RequestMapping("/api/v1/tprm/bcp-attestations")
@RequiredArgsConstructor
@Tag(name = "TPRM — Vendor BCP Attestation Rows (Slice 10-D)")
@SecurityRequirement(name = "bearerAuth")
public class VendorBcpAttestationController {

    private final VendorBcpAttestationService service;

    @GetMapping
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<AttestationResponse> list(@RequestParam("campaignId") Long campaignId,
                                            @RequestParam(value = "status", required = false) String status) {
        if (status != null && !status.isBlank()) {
            return service.listForCampaignByStatus(campaignId, status);
        }
        return service.listForCampaign(campaignId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public AttestationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /**
     * Vendor-portal lookup. v1 trusts the token alone (token IS the credential).
     * Full vendor-portal hardening (rate-limit, audit, MFA bind) is a follow-up.
     */
    @GetMapping("/by-token/{token}")
    @PreAuthorize("permitAll()")
    public AttestationResponse getByToken(@PathVariable UUID token) {
        return service.getByToken(token);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public AttestationResponse submit(@PathVariable Long id,
                                       @RequestBody @Valid AttestationSubmitRequest req) {
        return service.submitAttestation(id, req);
    }

    /** Vendor-portal submit — token-authenticated. */
    @PostMapping("/by-token/{token}/submit")
    @PreAuthorize("permitAll()")
    public AttestationResponse submitByToken(@PathVariable UUID token,
                                              @RequestBody @Valid AttestationSubmitRequest req) {
        return service.submitByToken(token, req);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public AttestationResponse accept(@PathVariable Long id,
                                       @RequestBody(required = false) AttestationReviewRequest req) {
        return service.acceptAttestation(id, req);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public AttestationResponse reject(@PathVariable Long id,
                                       @RequestBody(required = false) AttestationReviewRequest req) {
        return service.rejectAttestation(id, req);
    }

    @PostMapping("/{id}/mark-out-of-scope")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public AttestationResponse markOutOfScope(@PathVariable Long id,
                                                @RequestBody(required = false) AttestationReviewRequest req) {
        return service.markOutOfScope(id, req);
    }
}
