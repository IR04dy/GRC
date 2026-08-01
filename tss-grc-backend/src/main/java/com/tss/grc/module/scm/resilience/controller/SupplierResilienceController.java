package com.tss.grc.module.scm.resilience.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.*;
import com.tss.grc.module.scm.resilience.service.AlternateSupplierService;
import com.tss.grc.module.scm.resilience.service.ExitPlanService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for supplier resilience (gap-analysis item 4).
 *
 * <p>Two resources behind one controller:
 * <ul>
 *   <li>/api/scm/resilience/alternates  — alternate suppliers (per primary)</li>
 *   <li>/api/scm/resilience/exit-plans  — disengagement playbooks (per vendor)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/scm/resilience")
@RequiredArgsConstructor
@RequireModule("TPRM")
@Tag(name = "Supplier Resilience")
@SecurityRequirement(name = "bearerAuth")
public class SupplierResilienceController {

    private final AlternateSupplierService altService;
    private final ExitPlanService          planService;

    // ─── Alternate suppliers ──────────────────────────────────────────
    @GetMapping("/alternates")
    @PreAuthorize("hasPermission('SCM','READ')")
    @Operation(summary = "List alternate suppliers (paginated).")
    public Page<AltResponse> listAlts(@RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "100") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200));
        return (status == null
                ? altService.list(pageable)
                : altService.listByStatus(status, pageable)).map(AltResponse::from);
    }

    @GetMapping("/alternates/by-primary/{vendorId}")
    @PreAuthorize("hasPermission('SCM','READ')")
    public List<AltResponse> altsForPrimary(@PathVariable Long vendorId) {
        return altService.byPrimaryVendor(vendorId).stream().map(AltResponse::from).toList();
    }

    @GetMapping("/alternates/{id}")
    @PreAuthorize("hasPermission('SCM','READ')")
    public AltResponse getAlt(@PathVariable Long id) {
        return AltResponse.from(altService.get(id));
    }

    @PostMapping("/alternates")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public ResponseEntity<AltResponse> createAlt(@Valid @RequestBody AltCreateRequest req) {
        return ResponseEntity.status(201).body(AltResponse.from(altService.create(req)));
    }

    @PutMapping("/alternates/{id}")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public AltResponse updateAlt(@PathVariable Long id, @Valid @RequestBody AltUpdateRequest req) {
        return AltResponse.from(altService.update(id, req));
    }

    @PostMapping("/alternates/{id}/approve")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public AltResponse approveAlt(@PathVariable Long id, Authentication auth) {
        return AltResponse.from(altService.approve(id, usernameOf(auth), userIdOf(auth)));
    }

    @PostMapping("/alternates/{id}/archive")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public AltResponse archiveAlt(@PathVariable Long id, @Valid @RequestBody AltArchiveRequest req) {
        return AltResponse.from(altService.archive(id, req.reason()));
    }

    // ─── Exit plans ───────────────────────────────────────────────────
    @GetMapping("/exit-plans")
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<PlanResponse> listPlans(@RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "100") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200));
        return (status == null
                ? planService.list(pageable)
                : planService.listByStatus(status, pageable)).map(PlanResponse::from);
    }

    @GetMapping("/exit-plans/by-vendor/{vendorId}")
    @PreAuthorize("hasPermission('SCM','READ')")
    public List<PlanResponse> plansForVendor(@PathVariable Long vendorId) {
        return planService.byVendor(vendorId).stream().map(PlanResponse::from).toList();
    }

    @GetMapping("/exit-plans/{id}")
    @PreAuthorize("hasPermission('SCM','READ')")
    public PlanResponse getPlan(@PathVariable Long id) {
        return PlanResponse.from(planService.get(id));
    }

    @PostMapping("/exit-plans")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody PlanCreateRequest req) {
        return ResponseEntity.status(201).body(PlanResponse.from(planService.create(req)));
    }

    @PutMapping("/exit-plans/{id}")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public PlanResponse updatePlan(@PathVariable Long id, @Valid @RequestBody PlanUpdateRequest req) {
        return PlanResponse.from(planService.update(id, req));
    }

    @PostMapping("/exit-plans/{id}/approve")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public PlanResponse approvePlan(@PathVariable Long id, Authentication auth) {
        return PlanResponse.from(planService.approve(id, usernameOf(auth), userIdOf(auth)));
    }

    @PostMapping("/exit-plans/{id}/reject")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public PlanResponse rejectPlan(@PathVariable Long id, @Valid @RequestBody PlanRejectRequest req) {
        return PlanResponse.from(planService.reject(id, req.reason()));
    }

    @PostMapping("/exit-plans/{id}/activate")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public PlanResponse activatePlan(@PathVariable Long id, @Valid @RequestBody PlanActivateRequest req,
                                     Authentication auth) {
        return PlanResponse.from(planService.activate(id, req.reason(), userIdOf(auth)));
    }

    @PostMapping("/exit-plans/{id}/archive")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public PlanResponse archivePlan(@PathVariable Long id, @Valid @RequestBody PlanArchiveRequest req) {
        return PlanResponse.from(planService.archive(id, req.reason()));
    }

    @PostMapping("/exit-plans/{id}/record-test")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public PlanResponse recordTest(@PathVariable Long id, @Valid @RequestBody PlanTestRequest req) {
        return PlanResponse.from(planService.recordTest(id, req));
    }

    // ─── helpers ──────────────────────────────────────────────────────
    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
    private static String usernameOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUsername();
        return auth != null ? auth.getName() : null;
    }
}
