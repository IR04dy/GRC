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
 *   - Username/email uniqueness checked per-org (not global).
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
        when(userRepository.existsByUsernameAndOrganisationId("alice", ORG_ID)).thenReturn(false);
        when(userRepository.existsByEmailAndOrganisationId("alice@tss.example", ORG_ID)).thenReturn(false);
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

    @Test
    void create_duplicate_username_in_same_org_throws() {
        when(userRepository.existsByUsernameAndOrganisationId("alice", ORG_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_duplicate_email_in_same_org_throws() {
        when(userRepository.existsByUsernameAndOrganisationId("alice", ORG_ID)).thenReturn(false);
        when(userRepository.existsByEmailAndOrganisationId("alice@tss.example", ORG_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(createReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_defaults_active_to_true_when_unspecified() {
        UserRequest req = new UserRequest(
            "alice", "alice@tss.example", "Sup3rSecret!", "Alice Example", null, null,
            null, null,                       // both isActive + enabled null
            Set.of("RISK_MANAGER"), null
        );
        when(userRepository.existsByUsernameAndOrganisationId("alice", ORG_ID)).thenReturn(false);
        when(userRepository.existsByEmailAndOrganisationId("alice@tss.example", ORG_ID)).thenReturn(false);
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
}
