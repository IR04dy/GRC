package com.tss.grc.module.iam.governance.controller;

import com.tss.grc.module.iam.governance.dto.GovernanceDtos.EffectiveAccessResponse;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.MatrixUpdateRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleMatrixResponse;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleSummary;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleUpsertRequest;
import com.tss.grc.module.iam.governance.service.AccessGovernanceService;
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
@Tag(name = "IAM — Access governance (roles / matrix / effective access)")
public class AccessGovernanceController {

    private static final String READ_GUARD   = "hasPermission('IAM','READ')";
    private static final String WRITE_GUARD  = "hasPermission('IAM','WRITE')";
    private static final String DELETE_GUARD = "hasPermission('IAM','DELETE')";

    private final AccessGovernanceService service;

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

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
}
