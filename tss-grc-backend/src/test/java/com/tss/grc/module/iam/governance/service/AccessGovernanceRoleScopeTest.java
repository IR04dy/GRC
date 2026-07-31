package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Permission;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.GrantPair;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.MatrixUpdateRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleSummary;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleUpsertRequest;
import com.tss.grc.module.iam.repository.PermissionRepository;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-56 — role scoping across tenants.
 *
 * <p>V334 makes the 46 bootstrap-seeded system roles global so tenants 2+ can reach them.
 * That turns them into <em>shared</em> rows, and these tests pin the consequence that makes
 * sharing safe: a tenant may read a shared role but may not rewrite it, because
 * {@code CISO}'s grant matrix is the same row for every tenant. Only platform scope
 * ({@code ROLE_PLATFORM_ADMIN}) may change it; a tenant needing a variant clones.
 *
 * <p>The regression these guard against is not hypothetical in shape — it is the defect
 * {@code SanctionsListService.assertWritable} was written to stop for platform-global
 * screening lists (SEC-FIN-003), reached here through a different table.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessGovernanceRoleScopeTest {

    private static final long TENANT = 1L;

    @Mock RoleRepository roles;
    @Mock PermissionRepository permissions;
    @Mock UserRepository users;
    @Mock AuditLogService audit;

    @InjectMocks AccessGovernanceService service;

    @BeforeEach
    void bindTenant() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static Role role(long id, String name, Long org, boolean system) {
        Role r = new Role();
        r.setId(id);
        r.setRoleName(name);
        r.setDisplayNameEn(name);
        r.setDisplayNameAr(name);
        r.setOrganisationId(org);
        r.setIsSystemRole(system);
        r.setIsActive(true);
        r.setIsDeleted(false);
        return r;
    }

    private static Permission perm(long id, String module, String action) {
        Permission p = new Permission();
        p.setId(id);
        p.setModule(module);
        p.setAction(action);
        p.setPermissionName(module + "_" + action);
        return p;
    }

    /** Authenticates as a principal that is NOT the platform admin. */
    private static void asTenantAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("orgadmin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"),
                        new SimpleGrantedAuthority("IAM_WRITE"))));
    }

    private static void asPlatformAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
    }

    // ── the rule V334 depends on ─────────────────────────────────────────────

    @Test
    void aTenantCannotRewriteASharedRolesMatrixBecauseThatRowBelongsToEveryTenant() {
        Role sharedCiso = role(33L, "CISO", null, true);
        when(roles.findById(33L)).thenReturn(Optional.of(sharedCiso));
        asTenantAdmin();

        assertThatThrownBy(() -> service.putMatrix(33L,
                new MatrixUpdateRequest(List.of(new GrantPair("RISK", "READ")))))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("clone into the tenant");

        // and nothing was written — the refusal must precede persistence, not follow it
        verify(roles, never()).save(any(Role.class));
    }

    @Test
    void platformAdminMayRewriteASharedRoleBecauseThatIsThePlatformScopeItOwns() {
        Role sharedCiso = role(33L, "CISO", null, true);
        sharedCiso.getPermissions().add(perm(1L, "RISK", "READ"));
        when(roles.findById(33L)).thenReturn(Optional.of(sharedCiso));
        when(permissions.findByModuleAndAction("RISK", "READ"))
            .thenReturn(Optional.of(perm(1L, "RISK", "READ")));
        when(permissions.findAllByOrderByModuleAscActionAsc())
            .thenReturn(List.of(perm(1L, "RISK", "READ")));
        when(roles.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
        asPlatformAdmin();

        assertThat(service.putMatrix(33L,
            new MatrixUpdateRequest(List.of(new GrantPair("RISK", "READ")))).roleName())
            .isEqualTo("CISO");
        verify(roles).save(any(Role.class));
    }

    @Test
    void aTenantOwnedRoleStaysEditableByItsOwnTenant() {
        Role own = role(90L, "CISO_VARIANT", TENANT, false);
        when(roles.findById(90L)).thenReturn(Optional.of(own));
        when(permissions.findByModuleAndAction("RISK", "READ"))
            .thenReturn(Optional.of(perm(1L, "RISK", "READ")));
        when(permissions.findAllByOrderByModuleAscActionAsc())
            .thenReturn(List.of(perm(1L, "RISK", "READ")));
        when(roles.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
        asTenantAdmin();

        assertThat(service.putMatrix(90L,
            new MatrixUpdateRequest(List.of(new GrantPair("RISK", "READ")))).roleName())
            .isEqualTo("CISO_VARIANT");
    }

    // ── the escape hatch that makes the refusal actionable ───────────────────

    @Test
    void cloningASharedRoleCopiesItsGrantsIntoATenantOwnedNonSystemRole() {
        Role sharedCiso = role(33L, "CISO", null, true);
        sharedCiso.getPermissions().addAll(Set.of(perm(1L, "RISK", "READ"), perm(2L, "CYBER", "READ")));
        when(roles.findById(33L)).thenReturn(Optional.of(sharedCiso));
        when(roles.findByOrganisationIdOrOrganisationIdIsNull(TENANT))
            .thenReturn(List.of(sharedCiso));
        when(roles.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
        asTenantAdmin();

        RoleSummary clone = service.cloneRole(33L,
            new RoleUpsertRequest("CISO_KSA", "CISO (KSA)", "رئيس أمن المعلومات (السعودية)", null));

        assertThat(clone.roleName()).isEqualTo("CISO_KSA");
        // tenant-owned, so updateRole/deleteRole will accept it where they refuse the template
        assertThat(clone.organisationId()).isEqualTo(TENANT);
        assertThat(clone.systemRole()).isFalse();
        // the source keeps its own grants — a clone copies edges, it does not move them
        assertThat(sharedCiso.getPermissions()).hasSize(2);
    }

    /**
     * Reusing the source's own name is refused as a name conflict, because the source is
     * always among the roles the tenant can see — {@code mustReadableRole} admits only global
     * or tenant-owned roles, which is precisely what the visibility query returns.
     *
     * <p>This test is written against that real repository state deliberately. Its first
     * version stubbed an <em>empty</em> visible list to exercise a dedicated same-name check,
     * and passed — but the live probe returned 409 where it predicted 400, because the empty
     * list cannot occur and the check was therefore unreachable. The check is gone; this pins
     * what actually happens.
     */
    @Test
    void reusingTheSourceNameIsRefusedAsAConflictBecauseTheSourceIsAlwaysVisible() {
        Role sharedCiso = role(33L, "CISO", null, true);
        when(roles.findById(33L)).thenReturn(Optional.of(sharedCiso));
        when(roles.findByOrganisationIdOrOrganisationIdIsNull(TENANT))
            .thenReturn(List.of(sharedCiso));
        asTenantAdmin();

        assertThatThrownBy(() -> service.cloneRole(33L,
                new RoleUpsertRequest("CISO", "CISO copy", "نسخة", null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already exists");
        verify(roles, never()).save(any(Role.class));
    }

    @Test
    void aCloneNameAlreadyInUseIsARejectedConflictNotASecondRow() {
        Role sharedCiso = role(33L, "CISO", null, true);
        Role existing = role(91L, "CISO_KSA", TENANT, false);
        when(roles.findById(33L)).thenReturn(Optional.of(sharedCiso));
        when(roles.findByOrganisationIdOrOrganisationIdIsNull(TENANT))
            .thenReturn(List.of(sharedCiso, existing));
        asTenantAdmin();

        assertThatThrownBy(() -> service.cloneRole(33L,
                new RoleUpsertRequest("CISO_KSA", "CISO (KSA)", "رئيس", null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already exists");
        verify(roles, never()).save(any(Role.class));
    }

    @Test
    void aRoleOwnedByAnotherTenantIsNotEvenReadableSoCloningItIsNotAPathAcrossTenants() {
        Role otherTenants = role(99L, "THEIR_ROLE", 2L, false);
        when(roles.findById(99L)).thenReturn(Optional.of(otherTenants));
        asTenantAdmin();

        // mustReadableRole 404s it — the clone endpoint cannot be used to read a
        // neighbouring tenant's private role definition
        assertThatThrownBy(() -> service.cloneRole(99L,
                new RoleUpsertRequest("COPIED", "Copied", "منسوخ", null)))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ── reading is unaffected: that is the half F2-56 needed to keep ─────────

    @Test
    void everyTenantStillSeesSharedRolesInTheListWhichIsWhatMakesThemAppointable() {
        Role shared = role(33L, "CISO", null, true);
        Role own = role(90L, "CISO_VARIANT", TENANT, false);
        when(roles.findByOrganisationIdOrOrganisationIdIsNull(TENANT))
            .thenReturn(List.of(shared, own));
        asTenantAdmin();

        assertThat(service.listRoles()).extracting(RoleSummary::roleName)
            .containsExactly("CISO", "CISO_VARIANT");
    }
}
