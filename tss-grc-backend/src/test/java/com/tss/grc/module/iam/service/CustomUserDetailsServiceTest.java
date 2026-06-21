package com.tss.grc.module.iam.service;

import com.tss.grc.module.iam.entity.Permission;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PERF-NQ-002 — with roles/permissions now LAZY, the auth path must load the
 * full authorization graph via the @EntityGraph fetch finder
 * (findWithRolesByUsername), not the plain findByUsername.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private CustomUserDetailsService service;

    private static User adminWithRole() {
        Permission perm = new Permission();
        perm.setPermissionName("RISK_READ");
        Role role = new Role();
        role.setRoleName("PLATFORM_ADMIN");
        role.setPermissions(Set.of(perm));
        User u = new User();
        u.setId(1L); u.setOrganisationId(1L); u.setUsername("admin");
        u.setPasswordHash("$2b$10$x"); u.setIsActive(true); u.setIsLocked(false);
        u.setRoles(Set.of(role));
        return u;
    }

    @Test void loadByUsername_usesFetchFinder_andBuildsAuthorities() {
        when(userRepository.findWithRolesByUsername("admin")).thenReturn(Optional.of(adminWithRole()));

        UserDetails details = service.loadUserByUsername("admin");

        Set<String> auths = details.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(auths).contains("ROLE_PLATFORM_ADMIN", "RISK_READ");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        // Must use the graph-fetching finder, never the lean findByUsername.
        verify(userRepository).findWithRolesByUsername("admin");
        verify(userRepository, never()).findByUsername(any());
    }

    @Test void lockedUser_isRejectedUntilWindowExpires_thenAutoUnlocks() {
        User locked = adminWithRole();
        locked.setIsLocked(true);
        locked.setLockedUntil(LocalDateTime.now().plusMinutes(10)); // still within window
        when(userRepository.findWithRolesByUsername("admin")).thenReturn(Optional.of(locked));
        assertThat(service.loadUserByUsername("admin").isAccountNonLocked()).isFalse();

        // Window elapsed -> auto-unlock (SEC-AUTH-001).
        locked.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        assertThat(service.loadUserByUsername("admin").isAccountNonLocked()).isTrue();
    }
}
