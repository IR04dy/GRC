package com.tss.grc.module.iam.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.repository.RoleRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleRepository roleRepository;

    /**
     * F2-158 — the only endpoint in Role Management, and it answered <b>500</b> for every caller.
     *
     * <p>It returned {@code List<Role>}, the JPA entity, whose {@code permissions} collection is lazy.
     * Jackson serialises after the transaction closes, so every call ended in
     * {@code LazyInitializationException} → {@code HttpMessageNotWritableException} → 500. This is
     * landmine 9 in {@code CLAUDE.md} verbatim — <i>"lazy-collection entities 500 outside the session —
     * return DTOs"</i> — and it had never worked, in dev or in production.
     *
     * <p>⚠ <b>How it stayed broken:</b> the frontend carried a hardcoded seven-role dropdown and a mock
     * {@code ADMIN_ROLES} fallback wired into all three failure paths, so the page always rendered
     * something. A workaround that is indistinguishable from success is why nobody filed the 500 — and
     * F2-52 only found it because fixing the dropdown to read the real catalogue made the page depend on
     * this call for the first time. <b>The client-side fallback was load-bearing.</b>
     *
     * <p>The projection is deliberately narrow: exactly the six fields {@code AdminRoleDto} declares in
     * {@code tss-grc-ui-v2/src/lib/api.ts}. Permissions are <em>not</em> included — that is the Access
     * Governance console's job ({@code /api/v1/iam/governance/roles}), and adding them here would
     * re-introduce the collection that caused the failure.
     */
    public record RoleSummary(Long id, String roleName, String displayNameEn, String displayNameAr,
                              Boolean isSystemRole, Boolean isActive) { }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasPermission('IAM', 'READ')")
    public List<RoleSummary> list() {
        return roleRepository
            .findByOrganisationIdOrOrganisationIdIsNull(TenantContext.getCurrentTenant())
            .stream()
            .map(RoleController::toSummary)
            .toList();
    }

    private static RoleSummary toSummary(Role r) {
        return new RoleSummary(r.getId(), r.getRoleName(), r.getDisplayNameEn(),
            r.getDisplayNameAr(), r.getIsSystemRole(), r.getIsActive());
    }
}
