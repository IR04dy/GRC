package com.tss.grc.module.openbanking.controller;

import com.tss.grc.module.openbanking.dto.ObDtos.ApiCallResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.ConsentResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.CreateTppRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.DashboardResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.EventResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.GrantConsentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.IncidentResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.NotifySamaRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.RaiseIncidentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.RecordApiCallRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.RevokeConsentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.TppResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.TransitionIncidentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.TransitionTppRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.UpdateTppRequest;
import com.tss.grc.module.openbanking.service.ObService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for the Sprint 19 Open Banking Compliance module.
 * Tenant-scoped via JWT-derived {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/v1/openbanking")
@RequiredArgsConstructor
@Tag(name = "Open Banking Compliance (Sprint 19)")
@SecurityRequirement(name = "bearerAuth")
public class ObController {

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER',"
            + "'INTERNAL_AUDITOR','CISO','OPEN_BANKING_OFFICER')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','OPEN_BANKING_OFFICER')";
    private static final String APPROVE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','CRO')";

    private final ObService service;

    // ─── TPPs ──────────────────────────────────────────────────────

    @PostMapping("/tpps")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Register a new Third-Party Provider (PENDING_KYB).")
    public ResponseEntity<TppResponse> createTpp(@Valid @RequestBody CreateTppRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTpp(req));
    }

    @GetMapping("/tpps")
    @PreAuthorize(READ_GUARD)
    public Page<TppResponse> listTpps(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listTpps(page, size);
    }

    @GetMapping("/tpps/{id}")
    @PreAuthorize(READ_GUARD)
    public TppResponse getTpp(@PathVariable Long id) { return service.getTpp(id); }

    @PutMapping("/tpps/{id}")
    @PreAuthorize(WRITE_GUARD)
    public TppResponse updateTpp(@PathVariable Long id,
                                  @Valid @RequestBody UpdateTppRequest req) {
        return service.updateTpp(id, req);
    }

    @PostMapping("/tpps/{id}/transition")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Move TPP through state machine (KYB approval, suspend, revoke).")
    public TppResponse transitionTpp(@PathVariable Long id,
                                      @Valid @RequestBody TransitionTppRequest req) {
        return service.transitionTpp(id, req);
    }

    @GetMapping("/tpps/{id}/consents")
    @PreAuthorize(READ_GUARD)
    public List<ConsentResponse> listConsentsForTpp(@PathVariable Long id) {
        return service.listConsentsForTpp(id);
    }

    @GetMapping("/tpps/{id}/events")
    @PreAuthorize(READ_GUARD)
    public List<EventResponse> listTppEvents(@PathVariable Long id) {
        return service.listTppEvents(id);
    }

    // ─── Consents ──────────────────────────────────────────────────

    @PostMapping("/consents")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Grant a new customer consent to a TPP.")
    public ConsentResponse grantConsent(@Valid @RequestBody GrantConsentRequest req) {
        return service.grantConsent(req);
    }

    @GetMapping("/consents")
    @PreAuthorize(READ_GUARD)
    public Page<ConsentResponse> listConsents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listConsents(page, size);
    }

    @PostMapping("/consents/{id}/revoke")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Revoke a consent (by CUSTOMER / BANK / TPP).")
    public ConsentResponse revokeConsent(@PathVariable Long id,
                                          @Valid @RequestBody RevokeConsentRequest req) {
        return service.revokeConsent(id, req);
    }

    // ─── API call audit ────────────────────────────────────────────

    @PostMapping("/api-calls")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Append-only: record a regulated API call.")
    public ApiCallResponse recordApiCall(@Valid @RequestBody RecordApiCallRequest req) {
        return service.recordApiCall(req);
    }

    @GetMapping("/api-calls")
    @PreAuthorize(READ_GUARD)
    public Page<ApiCallResponse> listApiCalls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return service.listApiCalls(page, size);
    }

    // ─── Incidents ─────────────────────────────────────────────────

    @PostMapping("/incidents")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Raise a new OB-specific incident.")
    public IncidentResponse raiseIncident(@Valid @RequestBody RaiseIncidentRequest req) {
        return service.raiseIncident(req);
    }

    @GetMapping("/incidents")
    @PreAuthorize(READ_GUARD)
    public Page<IncidentResponse> listIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listIncidents(page, size);
    }

    @GetMapping("/incidents/{id}")
    @PreAuthorize(READ_GUARD)
    public IncidentResponse getIncident(@PathVariable Long id) {
        return service.getIncident(id);
    }

    @PostMapping("/incidents/{id}/transition")
    @PreAuthorize(WRITE_GUARD)
    public IncidentResponse transitionIncident(@PathVariable Long id,
                                                 @Valid @RequestBody TransitionIncidentRequest req) {
        return service.transitionIncident(id, req);
    }

    @PostMapping("/incidents/{id}/notify-sama")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Record SAMA notification + reference (24h window).")
    public IncidentResponse notifySama(@PathVariable Long id,
                                        @Valid @RequestBody NotifySamaRequest req) {
        return service.notifySama(id, req);
    }

    // ─── Dashboard ─────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Open Banking dashboard — TPPs + consents + incidents + API health.")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
