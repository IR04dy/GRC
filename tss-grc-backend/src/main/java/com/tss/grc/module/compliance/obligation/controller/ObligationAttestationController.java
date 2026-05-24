package com.tss.grc.module.compliance.obligation.controller;

import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationRecordRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationReviewRequest;
import com.tss.grc.module.compliance.obligation.service.ObligationAttestationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sprint 11 — REST surface for periodic obligation attestations.
 *
 * <pre>
 *   GET  /api/v1/compliance/obligation-attestations
 *   GET  /api/v1/compliance/obligation-attestations/by-obligation/{obligationId}
 *   GET  /api/v1/compliance/obligation-attestations/by-campaign/{campaignId}
 *   GET  /api/v1/compliance/obligation-attestations/{id}
 *   POST /api/v1/compliance/obligation-attestations/record
 *   POST /api/v1/compliance/obligation-attestations/{id}/review
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/compliance/obligation-attestations")
@RequiredArgsConstructor
@Tag(name = "Compliance — Obligation Attestations (Sprint 11)")
@SecurityRequirement(name = "bearerAuth")
public class ObligationAttestationController {

    private final ObligationAttestationService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<AttestationResponse> list() {
        return service.list();
    }

    @GetMapping("/by-obligation/{obligationId}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<AttestationResponse> byObligation(@PathVariable Long obligationId) {
        return service.listForObligation(obligationId);
    }

    @GetMapping("/by-campaign/{campaignId}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<AttestationResponse> byCampaign(@PathVariable Long campaignId) {
        return service.listForCampaign(campaignId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public AttestationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/record")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public AttestationResponse record(@RequestBody @Valid AttestationRecordRequest req) {
        return service.recordAttestation(req);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','AUDITOR','CISO','PLATFORM_ADMIN')")
    public AttestationResponse review(@PathVariable Long id,
                                       @RequestBody @Valid AttestationReviewRequest req) {
        return service.reviewAttestation(id, req);
    }
}
