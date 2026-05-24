package com.tss.grc.module.compliance.obligation.controller;

import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignCloseRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignStatsResponse;
import com.tss.grc.module.compliance.obligation.service.ObligationCampaignService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sprint 11 — REST surface for obligation attestation campaigns.
 *
 * <pre>
 *   GET  /api/v1/compliance/obligation-campaigns
 *   GET  /api/v1/compliance/obligation-campaigns/{id}
 *   GET  /api/v1/compliance/obligation-campaigns/{id}/stats
 *   POST /api/v1/compliance/obligation-campaigns
 *   POST /api/v1/compliance/obligation-campaigns/{id}/open
 *   POST /api/v1/compliance/obligation-campaigns/{id}/send-reminders
 *   POST /api/v1/compliance/obligation-campaigns/{id}/escalate
 *   POST /api/v1/compliance/obligation-campaigns/{id}/close
 *   POST /api/v1/compliance/obligation-campaigns/{id}/cancel
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/compliance/obligation-campaigns")
@RequiredArgsConstructor
@Tag(name = "Compliance — Obligation Attestation Campaigns (Sprint 11)")
@SecurityRequirement(name = "bearerAuth")
public class ObligationCampaignController {

    private final ObligationCampaignService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<CampaignResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public CampaignResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','CISO','PLATFORM_ADMIN','BCM_MANAGER')")
    public CampaignStatsResponse stats(@PathVariable Long id) {
        return service.stats(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public CampaignResponse create(@RequestBody @Valid CampaignCreateRequest req) {
        return service.createCampaign(req);
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public CampaignResponse open(@PathVariable Long id) {
        return service.openCampaign(id);
    }

    @PostMapping("/{id}/send-reminders")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public CampaignResponse sendReminders(@PathVariable Long id) {
        return service.sendReminders(id);
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','AUDITOR','CISO','PLATFORM_ADMIN')")
    public CampaignResponse escalate(@PathVariable Long id) {
        return service.escalateOverdue(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public CampaignResponse close(@PathVariable Long id,
                                   @RequestBody(required = false) CampaignCloseRequest req) {
        return service.closeCampaign(id, req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN')")
    public CampaignResponse cancel(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return service.cancelCampaign(id, reason);
    }
}
