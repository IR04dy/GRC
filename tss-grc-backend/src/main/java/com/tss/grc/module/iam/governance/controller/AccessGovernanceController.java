package com.tss.grc.module.iam.governance.controller;

import com.tss.grc.module.iam.governance.dto.GovernanceDtos.CreateAccessRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.EffectiveAccessResponse;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.MatrixUpdateRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RejectAccessRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RevokeAssignmentRequest;
import com.tss.grc.module.iam.governance.entity.AccessRequest;
import com.tss.grc.module.iam.governance.service.AccessRequestService;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleMatrixResponse;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleSummary;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleUpsertRequest;
import com.tss.grc.module.iam.governance.service.AccessGovernanceService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RBAC Access-Governance P0 (plan §6.1/§8) — role catalogue CRUD, the role × module ×
 * action permission-matrix editor, and the per-user effective-access view.
 * Reads = IAM READ · mutations = IAM WRITE · retire = IAM DELETE (all catalogued in V280).
 */
@RestController
@RequestMapping("/api/v1/iam/governance")
@RequiredArgsConstructor
@RequireModule("ACCESS_GOV")
@Tag(name = "IAM — Access governance (roles / matrix / effective access)")
public class AccessGovernanceController {

    private static final String READ_GUARD    = "hasPermission('IAM','READ')";
    private static final String WRITE_GUARD   = "hasPermission('IAM','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('IAM','APPROVE')";
    private static final String DELETE_GUARD  = "hasPermission('IAM','DELETE')";

    private final AccessGovernanceService service;
    private final AccessRequestService requestService;

    // ── roles ────────────────────────────────────────────────────────────

    @GetMapping("/roles")
    @PreAuthorize(READ_GUARD)
    public List<RoleSummary> listRoles() { return service.listRoles(); }

    @PostMapping("/roles")
    @PreAuthorize(WRITE_GUARD)
    public RoleSummary createRole(@Valid @RequestBody RoleUpsertRequest r) { return service.createRole(r); }

    @PutMapping("/roles/{id}")
    @PreAuthorize(WRITE_GUARD)
    public RoleSummary updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpsertRequest r) {
        return service.updateRole(id, r);
    }

    /**
     * Copies a role — usually a shared global template — into the calling tenant under a new
     * name (F2-56). This is the escape hatch the write guard points at: shared roles are
     * platform-scope and a tenant may not edit them, so it clones one instead. Guarded by
     * {@code IAM:WRITE} like every other role mutation, because the result is a new
     * tenant-owned role.
     */
    @PostMapping("/roles/{id}/clone")
    @PreAuthorize(WRITE_GUARD)
    public RoleSummary cloneRole(@PathVariable Long id, @Valid @RequestBody RoleUpsertRequest r) {
        return service.cloneRole(id, r);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize(DELETE_GUARD)
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        service.deleteRole(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    // ── permission matrix ────────────────────────────────────────────────

    @GetMapping("/roles/{id}/matrix")
    @PreAuthorize(READ_GUARD)
    public RoleMatrixResponse getMatrix(@PathVariable Long id) { return service.getMatrix(id); }

    @PutMapping("/roles/{id}/matrix")
    @PreAuthorize(WRITE_GUARD)
    public RoleMatrixResponse putMatrix(@PathVariable Long id, @Valid @RequestBody MatrixUpdateRequest r) {
        return service.putMatrix(id, r);
    }

    // ── effective access ─────────────────────────────────────────────────

    @GetMapping("/users/{id}/effective-access")
    @PreAuthorize(READ_GUARD)
    public EffectiveAccessResponse effectiveAccess(@PathVariable Long id) {
        return service.effectiveAccess(id);
    }

    // ── access requests — maker-checker (P1, §6.2) ──────────────────────

    @GetMapping("/requests")
    @PreAuthorize(READ_GUARD)
    public List<AccessRequest> listRequests(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status) {
        return requestService.list(status);
    }

    @PostMapping("/requests")
    @PreAuthorize(WRITE_GUARD)
    public AccessRequest createRequest(@Valid @RequestBody CreateAccessRequest r) {
        return requestService.create(r);
    }

    @PostMapping("/requests/{id}/approve")
    @PreAuthorize(APPROVE_GUARD)
    public AccessRequest approveRequest(@PathVariable Long id,
            @Valid @RequestBody(required = false)
            com.tss.grc.module.iam.governance.dto.GovernanceDtos.ApproveAccessRequest r) {
        return requestService.approve(id,
            r != null && Boolean.TRUE.equals(r.override()),
            r == null ? null : r.overrideJustification());
    }

    @PostMapping("/requests/{id}/reject")
    @PreAuthorize(APPROVE_GUARD)
    public AccessRequest rejectRequest(@PathVariable Long id, @Valid @RequestBody RejectAccessRequest r) {
        return requestService.reject(id, r.reason());
    }

    // ── assignments: revoke + sweeps (P1, §6.3) ─────────────────────────

    @PostMapping("/assignments/revoke")
    @PreAuthorize(DELETE_GUARD)
    public java.util.Map<String, Integer> revoke(@Valid @RequestBody RevokeAssignmentRequest r) {
        return java.util.Map.of("removed", requestService.revoke(r.userId(), r.roleId()));
    }

    @PostMapping("/assignments/expiry-sweep")
    @PreAuthorize(WRITE_GUARD)
    public java.util.Map<String, Integer> expirySweep() {
        return java.util.Map.of("removed", requestService.manualExpirySweep());
    }

    @PostMapping("/assignments/jml-sweep")
    @PreAuthorize(WRITE_GUARD)
    public java.util.Map<String, Integer> jmlSweep() {
        return java.util.Map.of("processedLeavers", requestService.jmlSweep());
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
}
