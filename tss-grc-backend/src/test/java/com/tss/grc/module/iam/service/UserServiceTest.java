package com.tss.grc.module.iam.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.dto.UserRequest;
import com.tss.grc.module.iam.dto.UserResponse;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService — Sprint 4 §4.1 test backlog.
 *
 * Locks the IAM tenant-scoping invariants:
 *   - All reads/writes filter by organisationId from TenantContext.
 *   - Cross-tenant lookups surface as EntityNotFoundException, never leak data.
 *   - Username/email uniqueness checked GLOBALLY, including soft-deleted rows — F2-418.
 *     This line used to read "checked per-org (not global)" and was one of the two artefacts
 *     that disagreed about the same index. V227 creates ix_users_username / ix_users_email
 *     with a key of username / email ALONE and no filter, and says why: login resolves a user
 *     by username with no tenant context. The guard followed this comment rather than the
 *     migration, so it answered "available" for a username held in another organisation or by
 *     a soft-deleted user — exactly the rows the index rejects.
 *   - Passwords always hashed via PasswordEncoder; never stored verbatim.
 *   - Role assignment honors role names first (UI), falls back to roleIds (legacy).
 *   - Soft delete sets isDeleted=true and saves; never calls deleteById.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long USER_ID   = 42L;

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static User user(Long id, Long orgId) {
        User u = new User();
        u.setId(id);
        u.setOrganisationId(orgId);
        u.setUsername("alice");
        u.setEmail("alice@tss.example");
        u.setPasswordHash("$2a$10$old-hash");
        u.setFullNameEn("Alice Example");
        u.setFullNameAr("أليس");
        u.setIsActive(Boolean.TRUE);
        u.setIsLocked(Boolean.FALSE);
        u.setRoles(java.util.Collections.emptySet());
        return u;
    }

    private static Role role(Long id, String name, Long orgId) {
        Role r = new Role();
        r.setId(id);
        r.setRoleName(name);
        r.setOrganisationId(orgId);
        return r;
    }

    private static UserRequest createReq() {
        return new UserRequest(
            "alice", "alice@tss.example", "Sup3rSecret!", "Alice Example", "أليس",
            "Engineer", null, true, Set.of("RISK_MANAGER"), null
        );
    }

    // ─── get / list tenant scope ─────────────────────────────────────

    @Test
    void get_unknown_id_throws_EntityNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    void get_other_tenant_throws_EntityNotFound() {
        // Cross-tenant access must surface as 404, never leak existence
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, OTHER_ORG)));

        assertThatThrownBy(() -> service.get(USER_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void get_same_tenant_returns_response() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, ORG_ID)));

        UserResponse resp = service.get(USER_ID);
        assertThat(resp.id()).isEqualTo(USER_ID);
        assertThat(resp.username()).isEqualTo("alice");
    }

    // ─── create ──────────────────────────────────────────────────────

    @Test
    void create_hashes_password_and_stamps_org() {
        when(userRepository.countByUsernameIncludingDeleted("alice")).thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("alice@tss.example")).thenReturn(0L);
        when(passwordEncoder.encode("Sup3rSecret!")).thenReturn("$2a$10$new-hash");
        when(roleRepository.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID))
            .thenReturn(List.of(role(7L, "RISK_MANAGER", ORG_ID)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(USER_ID);
            return saved;
        });

        service.create(createReq());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$new-hash");
        assertThat(saved.getRoles()).extracting(Role::getRoleName).containsExactly("RISK_MANAGER");
    }

    // ─── F2-62: an inactive role grants nothing, so it must not be assignable ──
    //
    // Silently storing the grant would move the original defect rather than fix it: the admin
    // would be shown a saved user whose listed role confers no access. Refusing names the role.

    @Test
    void create_withInactiveRole_isRefused_byName() {
        Role dormant = role(7L, "RISK_MANAGER", ORG_ID);
        dormant.setIsActive(false);
        when(userRepository.countByUsernameIncludingDeleted("alice")).thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("alice@tss.example")).thenReturn(0L);
        when(roleRepository.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID)).thenReturn(List.of(dormant));

        assertThatThrownBy(() -> service.create(createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inactive role")
            .hasMessageContaining("RISK_MANAGER");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void create_withInactiveRole_isRefused_byLegacyIdPath() {
        // The roleIds branch is the legacy caller path and bypasses the name lookup entirely,
        // so it needs its own guard — an unguarded fallback is still an unguarded grant.
        Role dormant = role(7L, "RISK_MANAGER", ORG_ID);
        dormant.setIsActive(false);
        UserRequest byId = new UserRequest(
            "alice", "alice@tss.example", "Sup3rSecret!", "Alice Example", "أليس",
            "Engineer", null, true, null, Set.of(7L));
        when(userRepository.countByUsernameIncludingDeleted("alice")).thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("alice@tss.example")).thenReturn(0L);
        when(roleRepository.findAllById(Set.of(7L))).thenReturn(List.of(dormant));

        assertThatThrownBy(() -> service.create(byId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RISK_MANAGER");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void update_cannotSwapInActiveRoleForAnInactiveOne() {
        Role dormant = role(7L, "RISK_MANAGER", ORG_ID);
        dormant.setIsActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, ORG_ID)));
        when(roleRepository.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID)).thenReturn(List.of(dormant));

        assertThatThrownBy(() -> service.update(USER_ID, createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RISK_MANAGER");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void create_refusesAUsernameHeldAnywhereOnThePlatform() {
        when(userRepository.countByUsernameIncludingDeleted("alice")).thenReturn(1L);

        assertThatThrownBy(() -> service.create(createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username already in use")
            .as("the refusal must say where the uniqueness actually applies — the old message "
              + "left the caller checking their own organisation, which was already clean")
            .hasMessageContaining("across the whole platform");

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_refusesAnEmailHeldAnywhereOnThePlatform() {
        when(userRepository.countByUsernameIncludingDeleted("alice")).thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("alice@tss.example")).thenReturn(1L);

        assertThatThrownBy(() -> service.create(createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_defaults_active_to_true_when_unspecified() {
        UserRequest req = new UserRequest(
            "alice", "alice@tss.example", "Sup3rSecret!", "Alice Example", null, null,
            null, null,                       // both isActive + enabled null
            Set.of("RISK_MANAGER"), null
        );
        when(userRepository.countByUsernameIncludingDeleted("alice")).thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("alice@tss.example")).thenReturn(0L);
        when(passwordEncoder.encode("Sup3rSecret!")).thenReturn("$2a$10$h");
        when(roleRepository.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID))
            .thenReturn(List.of(role(7L, "RISK_MANAGER", ORG_ID)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    // ─── update ──────────────────────────────────────────────────────

    @Test
    void update_other_tenant_throws_EntityNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, OTHER_ORG)));

        assertThatThrownBy(() -> service.update(USER_ID, createReq()))
            .isInstanceOf(EntityNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void update_blank_password_does_not_rehash() {
        // Password field omitted ⇒ keep existing hash
        UserRequest req = new UserRequest(
            "alice", "alice@tss.example", "  ", "Alice Example", null, null,
            true, null, Set.of("RISK_MANAGER"), null
        );
        User existing = user(USER_ID, ORG_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(roleRepository.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID))
            .thenReturn(List.of(role(7L, "RISK_MANAGER", ORG_ID)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(USER_ID, req);

        verify(passwordEncoder, never()).encode(any());
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$old-hash");
    }

    @Test
    void update_with_new_password_rehashes() {
        UserRequest req = new UserRequest(
            "alice", "alice@tss.example", "BrandN3w!", "Alice Example", null, null,
            true, null, Set.of("RISK_MANAGER"), null
        );
        User existing = user(USER_ID, ORG_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("BrandN3w!")).thenReturn("$2a$10$rotated");
        when(roleRepository.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID))
            .thenReturn(List.of(role(7L, "RISK_MANAGER", ORG_ID)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(USER_ID, req);

        verify(passwordEncoder).encode("BrandN3w!");
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$rotated");
    }

    // ─── patch (toggle enabled) ──────────────────────────────────────

    @Test
    void patch_enabled_true_flips_isActive_on() {
        User existing = user(USER_ID, ORG_ID);
        existing.setIsActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.patch(USER_ID, Map.of("enabled", true));

        assertThat(existing.getIsActive()).isTrue();
    }

    @Test
    void patch_enabled_false_flips_isActive_off() {
        User existing = user(USER_ID, ORG_ID);
        existing.setIsActive(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.patch(USER_ID, Map.of("enabled", false));

        assertThat(existing.getIsActive()).isFalse();
    }

    @Test
    void patch_other_tenant_throws_EntityNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, OTHER_ORG)));

        assertThatThrownBy(() -> service.patch(USER_ID, Map.of("enabled", true)))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── soft delete ─────────────────────────────────────────────────

    @Test
    void delete_soft_deletes_via_save_not_deleteById() {
        User existing = user(USER_ID, ORG_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(USER_ID);

        assertThat(existing.getIsDeleted()).isTrue();
        verify(userRepository).save(existing);
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void delete_other_tenant_throws_EntityNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, OTHER_ORG)));

        assertThatThrownBy(() -> service.delete(USER_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── F2-418: the guard asks the question the index enforces ──────────────

    /**
     * The soft-delete axis. {@link UserService#delete(Long)} is a soft delete and
     * {@code ix_users_username} is unfiltered, so the username stays taken — while the guard that
     * stood here was a derived finder and therefore could not see the row at all.
     *
     * <p>The count is stubbed on the NATIVE finder specifically: a mocked repository has no
     * {@code @Where} and no index, so what this can assert is which question the service asks
     * (F2-374's reason for a structural gate alongside).
     */
    @Test
    void create_refusesAUsernameHeldByASoftDeletedUser() {
        when(userRepository.countByUsernameIncludingDeleted("alice")).thenReturn(1L);

        assertThatThrownBy(() -> service.create(createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username already in use");

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).countByEmailIncludingDeleted(any());
    }

    /**
     * The tenant axis, on the provisioning path. {@code createIn} is what
     * {@code TenantOnboardingService} calls to create a new tenant's first administrator, and the
     * organisation it creates them in has no users at all — so the tenant-scoped guard was
     * <em>always</em> satisfied there and the global index was the only thing that refused,
     * rolling the whole onboarding back.
     */
    @Test
    void createIn_refusesAUsernameHeldByAnotherOrganisation() {
        when(userRepository.countByUsernameIncludingDeleted("alice")).thenReturn(1L);

        assertThatThrownBy(() -> service.createIn(OTHER_ORG, createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .as("the count is org-agnostic on purpose: asking about OTHER_ORG's own rows would "
              + "answer 'available' for a username org 1 holds")
            .hasMessageContaining("across the whole platform");

        verify(userRepository, never()).save(any());
    }

    /**
     * The update path had NO uniqueness guard at all, so changing a user's email to one held
     * anywhere was a bare 409. It must now refuse — and the count must EXCLUDE the row being
     * edited, or a user keeping their own address collides with themselves.
     */
    @Test
    void update_refusesAnEmailHeldByAnotherRowAndExcludesItsOwn() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, ORG_ID)));
        when(userRepository.countByEmailIncludingDeletedExcludingId("alice@tss.example", USER_ID))
            .thenReturn(1L);

        assertThatThrownBy(() -> service.update(USER_ID, createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).countByEmailIncludingDeleted(any());
    }

    @Test
    void update_allowsAUserToKeepItsOwnEmail() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, ORG_ID)));
        when(userRepository.countByEmailIncludingDeletedExcludingId("alice@tss.example", USER_ID))
            .thenReturn(0L);
        when(roleRepository.findByOrganisationIdOrOrganisationIdIsNull(ORG_ID))
            .thenReturn(List.of(role(7L, "RISK_MANAGER", ORG_ID)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(USER_ID, createReq());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail())
            .as("excluding its own id is what stops the guard refusing an edit that changes "
              + "nothing about the email")
            .isEqualTo("alice@tss.example");
    }
}
