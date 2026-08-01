package com.tss.grc.module.tprm.bcp_attestation.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignCloseRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignCreateRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignResponse;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignStatsResponse;
import com.tss.grc.module.tprm.bcp_attestation.service.VendorBcpCampaignService;
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
 * Slice 10-D — REST surface for vendor BCP attestation campaigns.
 *
 * <pre>
 *   GET    /api/v1/tprm/bcp-campaigns                       — list
 *   GET    /api/v1/tprm/bcp-campaigns/{id}                  — detail
 *   GET    /api/v1/tprm/bcp-campaigns/{id}/stats            — response-rate roll-up
 *   POST   /api/v1/tprm/bcp-campaigns                       — create (DRAFT)
 *   POST   /api/v1/tprm/bcp-campaigns/{id}/open             — DRAFT → OPEN (materialise rows)
 *   POST   /api/v1/tprm/bcp-campaigns/{id}/send-reminders?round=1|2
 *   POST   /api/v1/tprm/bcp-campaigns/{id}/escalate
 *   POST   /api/v1/tprm/bcp-campaigns/{id}/close
 *   POST   /api/v1/tprm/bcp-campaigns/{id}/cancel
 * </pre>
 *
 * <p>Role guard uses {@code hasAnyRole} (not {@code hasAnyAuthority}) —
 * matches {@code CustomUserDetailsService}'s {@code ROLE_} prefix
 * convention. Read-side is open to the broad audit-readiness audience
 * (TPRM_LEAD, RISK_MANAGER, AUDITOR, CISO, BCM_MANAGER, PLATFORM_ADMIN);
 * write-side is tight to the TPRM Lead and platform admins.
 */
@RestController
@RequestMapping("/api/v1/tprm/bcp-campaigns")
@RequiredArgsConstructor
@RequireModule("TPRM")
@Tag(name = "TPRM — Vendor BCP Attestation Campaigns (Slice 10-D)")
@SecurityRequirement(name = "bearerAuth")
public class VendorBcpCampaignController {

    private final VendorBcpCampaignService service;

    @GetMapping
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<CampaignResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public CampaignResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public CampaignStatsResponse stats(@PathVariable Long id) {
        return service.stats(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public CampaignResponse create(@RequestBody @Valid CampaignCreateRequest req) {
        return service.createCampaign(req);
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public CampaignResponse open(@PathVariable Long id) {
        return service.openCampaign(id);
    }

    @PostMapping("/{id}/send-reminders")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public CampaignResponse sendReminders(@PathVariable Long id,
                                           @RequestParam("round") int round) {
        return service.sendReminders(id, round);
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public CampaignResponse escalate(@PathVariable Long id) {
        return service.escalateOverdue(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public CampaignResponse close(@PathVariable Long id,
                                   @RequestBody(required = false) CampaignCloseRequest req) {
        return service.closeCampaign(id, req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public CampaignResponse cancel(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return service.cancelCampaign(id, reason);
    }
}
