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
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
@RequireModule("SAUDI_FS")
@Tag(name = "Open Banking Compliance (Sprint 19)")
@SecurityRequirement(name = "bearerAuth")
public class ObController {

    /*
     * F2-145(e) slice 2 — these three guards authorise by PERMISSION, not by role name.
     *
     * They used to read hasAnyRole(...), which compares authority strings and so never reaches
     * ModulePermissionEvaluator: the OPENBANKING permissions V362 catalogued decided nothing here. The
     * populations were measured per action against iam.role_permissions BEFORE the change, because
     * ADR-010's amendment requires the INTENDED population and not merely the current one:
     *
     *   READ     guard PLATFORM_ADMIN, COMPLIANCE_OFFICER, RISK_MANAGER, INTERNAL_AUDITOR, CISO,
     *                  OPEN_BANKING_OFFICER
     *            grant CISO, COMPLIANCE_OFFICER, INTERNAL_AUDITOR, OPEN_BANKING_OFFICER, RISK_MANAGER
     *   WRITE    guard PLATFORM_ADMIN, COMPLIANCE_OFFICER, OPEN_BANKING_OFFICER
     *            grant COMPLIANCE_OFFICER, OPEN_BANKING_OFFICER
     *   APPROVE  guard PLATFORM_ADMIN, COMPLIANCE_OFFICER, CRO
     *            grant COMPLIANCE_OFFICER
     *
     * PLATFORM_ADMIN is absent from every grant list on purpose: hasPermission gets the in-code
     * platform-admin bypass, so naming it would be decoration. CRO was seated nowhere — a ghost whose
     * APPROVE leg had always been false — and converting the guard is what finally retires its three
     * recitations here (F2-52's third route: a converted guard deletes every name it recites).
     *
     * ⚠ V368 IS LOAD-BEARING FOR THE APPROVE TIER. The catalogue also granted OPENBANKING:APPROVE to
     * OPEN_BANKING_OFFICER, which no APPROVE guard here has ever named — V362 derived actions from method
     * shape, and this controller's WRITE tier carries two transition-shaped methods. Converting before
     * V368 would have handed the module's operator the authority to admit the TPPs it operates:
     * createTpp, transitionTpp (KYB approve / suspend / revoke) and notifySama. V368 revokes it, which is
     * why the APPROVE population above is COMPLIANCE_OFFICER alone.
     *
     * ⚠ THE TIERS ARE NOT UNIFORM BY VERB, AND THAT IS THE DESIGN. createTpp is a POST on APPROVE
     * because TPP admission is a compliance control gate under SAMA's Open Banking Framework, while
     * transitionIncident is a POST on WRITE because incident handling is the operator's day job. Do not
     * "tidy" them into verb order — ObControllerGuardTest pins all 18 sites and will fail.
     */
    private static final String READ_GUARD = "hasPermission('OPENBANKING','READ')";
    private static final String WRITE_GUARD = "hasPermission('OPENBANKING','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('OPENBANKING','APPROVE')";

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
        return service.listTpps(page, Math.min(size, 200));
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
        return service.listConsents(page, Math.min(size, 200));
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
        return service.listApiCalls(page, Math.min(size, 200));
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
        return service.listIncidents(page, Math.min(size, 200));
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
