package com.tss.grc.module.iam.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.dto.UserRequest;
import com.tss.grc.module.iam.dto.UserResponse;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String search, String role, Boolean enabled, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return userRepository.findWithFilters(orgId, search, role, enabled, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return userRepository.findById(id)
            .filter(u -> u.getOrganisationId().equals(orgId))
            .map(this::toResponse)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    @Transactional
    public UserResponse create(UserRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        if (userRepository.existsByUsernameAndOrganisationId(req.username(), orgId)) {
            throw new IllegalArgumentException("Username already exists: " + req.username());
        }
        if (userRepository.existsByEmailAndOrganisationId(req.email(), orgId)) {
            throw new IllegalArgumentException("Email already exists: " + req.email());
        }
        User user = new User();
        user.setOrganisationId(orgId);
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullNameEn(req.fullNameEn());
        user.setFullNameAr(req.fullNameAr());
        user.setJobTitleEn(req.jobTitleEn());
        Boolean active = req.effectiveActive();
        user.setIsActive(active != null ? active : Boolean.TRUE);
        assignRolesByNameOrId(user, req.roles(), req.roleIds());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        User user = userRepository.findById(id)
            .filter(u -> u.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        user.setEmail(req.email());
        user.setFullNameEn(req.fullNameEn());
        user.setFullNameAr(req.fullNameAr());
        user.setJobTitleEn(req.jobTitleEn());
        Boolean active = req.effectiveActive();
        if (active != null) user.setIsActive(active);
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        assignRolesByNameOrId(user, req.roles(), req.roleIds());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse patch(Long id, java.util.Map<String, Object> patch) {
        Long orgId = TenantContext.getCurrentTenant();
        User user = userRepository.findById(id)
            .filter(u -> u.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        if (patch.containsKey("enabled")) {
            Object val = patch.get("enabled");
            boolean enabled = Boolean.TRUE.equals(val) || "true".equals(String.valueOf(val));
            user.setIsActive(enabled);
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        User user = userRepository.findById(id)
            .filter(u -> u.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        user.setIsDeleted(true);
        userRepository.save(user);
    }

    private void assignRolesByNameOrId(User user, Set<String> roleNames, Set<Long> roleIds) {
        // Prefer role names (strings) sent by the UI; fall back to IDs for legacy callers
        if (roleNames != null && !roleNames.isEmpty()) {
            Long orgId = user.getOrganisationId();
            List<Role> allRoles = roleRepository.findByOrganisationIdOrOrganisationIdIsNull(orgId);
            Set<Role> matched = allRoles.stream()
                .filter(r -> roleNames.contains(r.getRoleName()))
                .collect(Collectors.toSet());
            user.setRoles(rejectInactive(matched));
        } else if (roleIds != null && !roleIds.isEmpty()) {
            Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
            user.setRoles(rejectInactive(roles));
        }
    }

    /**
     * F2-62 — refuse to assign a role that confers nothing, rather than silently storing a
     * grant with no effect.
     *
     * <p>Since an inactive role now contributes no authorities, assigning one would leave the
     * admin looking at a saved user whose listed role grants nothing — the same
     * report-disagrees-with-reality shape as the defect this fix closes, just relocated to the
     * user editor. Failing names the offending roles so the caller can activate them first.
     */
    private static Set<Role> rejectInactive(Set<Role> roles) {
        List<String> inactive = roles.stream()
            .filter(r -> !r.grantsAuthority())
            .map(Role::getRoleName)
            .sorted()
            .toList();
        if (!inactive.isEmpty()) {
            throw new IllegalArgumentException(
                "Cannot assign inactive role(s) — they grant no access: " + String.join(", ", inactive));
        }
        return roles;
    }

    private UserResponse toResponse(User u) {
        Set<String> roleNames = u.getRoles().stream()
            .map(Role::getRoleName)
            .collect(Collectors.toSet());
        return new UserResponse(
            u.getId(), u.getUsername(), u.getEmail(),
            u.getFullNameEn(), u.getFullNameAr(), u.getJobTitleEn(),
            u.getIsActive(), u.getIsLocked(),
            roleNames, u.getLastLoginAt(), u.getCreatedAt()
        );
    }
}
