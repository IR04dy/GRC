package com.tss.grc.module.iam.service;

import com.tss.grc.module.iam.entity.Permission;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.security.GrcUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return buildUserDetails(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UsernameNotFoundException("User not found by ID: " + userId));
        return buildUserDetails(user);
    }

    private GrcUserDetails buildUserDetails(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName()));
            for (Permission perm : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(perm.getPermissionName()));
            }
        }
        // SEC-AUTH-001: an account is locked only while isLocked AND the lockout
        // window is still open. Once lockedUntil has passed, it auto-unlocks
        // (the next successful login resets the counter via LoginAttemptService).
        boolean currentlyLocked = Boolean.TRUE.equals(user.getIsLocked())
            && (user.getLockedUntil() == null || java.time.LocalDateTime.now().isBefore(user.getLockedUntil()));

        return new GrcUserDetails(
            user.getId(),
            user.getOrganisationId(),
            user.getUsername(),
            user.getPasswordHash(),
            Boolean.TRUE.equals(user.getIsActive()),
            !currentlyLocked,
            authorities
        );
    }
}
