package com.tss.grc.module.iam.service;

import com.tss.grc.common.util.LikePatterns;
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
        // F2-449: :search is bound into a LIKE over three columns
        return userRepository.findWithFilters(orgId, LikePatterns.escape(search), role, enabled, pageable)
                             .map(this::toResponse);
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
        return createIn(TenantContext.getCurrentTenant(), req);
    }

    /**
     * Create a user in a <b>named</b> organisation rather than the caller's own.
     *
     * <p>F2-81 needs this: tenant onboarding creates the new tenant's first administrator, and at
     * that moment {@link TenantContext} still holds the <i>host operator's</i> organisation — so
     * {@link #create(UserRequest)} would have filed the new bank's admin under whoever onboarded
     * them. Splitting the explicit case out rather than copying the body keeps the rules that matter
     * in one place: the username/email uniqueness checks, {@code @StrongPassword} hashing, and
     * {@link #assignRolesByNameOrId}'s refusal to grant an inactive role (F2-62). A second
     * implementation of those would drift, which is the shape this programme keeps finding.
     *
     * <p>Note {@code assignRolesByNameOrId} resolves roles from {@code user.getOrganisationId()},
     * not from the thread — so it is already correct for a foreign organisation, and global roles
     * ({@code organisation_id IS NULL}, which is how every role in {@code iam.roles} is stored) are
     * visible to the new tenant without creating any.
     */
    @Transactional
    public UserResponse createIn(Long orgId, UserRequest req) {
        requireUsernameAvailable(req.username());
        requireEmailAvailable(req.email(), null);
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
        requireEmailAvailable(req.email(), user.getId());
        user.setEmail(req.email());
        user.setFullNameEn(req.fullNameEn());
        user.setFullNameAr(req.fullNameAr());
        // F2-213 — `AdminUserRequest` has no jobTitleEn and the admin form has no input for
        // it, so editing a user (to change a role, say) wrote null over their job title.
        // Populated on 2 of 7 live rows in both databases. Preserving takes away nothing an
        // operator has today; offering it means adding the field, which is filed, not
        // invented here. `create` above still takes it — a create has nothing to preserve.
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

    /**
     * Refuse a username the platform's own unique index would refuse — F2-418.
     *
     * <p>The guard this replaced asked whether the username was taken <em>in the caller's
     * organisation, among rows that are not soft-deleted</em>. {@code ix_users_username} is global
     * and unfiltered (V227 documents both as deliberate: login resolves a user by username with no
     * tenant context), so the guard passed for exactly the rows the index rejects and the caller
     * got a bare 409 instead of a 400 naming the field.
     *
     * <p>⚠ Two reachable cases, and the second is the provisioning path. A username held by a
     * <b>soft-deleted</b> user — {@link #delete(Long)} is a soft delete — and a username held in
     * <b>another organisation</b>. {@link com.tss.grc.module.iam.onboarding.service.TenantOnboardingService}
     * creates a new tenant's first administrator inside the onboarding transaction, so a collision
     * there discarded the organisation, its licences and its administrator — while telling the
     * operator the username must be unique within an organisation that had no users at all.
     *
     * <p>On disclosure: the refusal says the username is unavailable, which is observable from the
     * index's own 409 today and is inherent to a globally-unique username space. It says nothing
     * about <em>which</em> organisation holds it or anything about that user, which is the line
     * {@code CommitteeMemberRegistryService#requireUserInTenant} draws for the same reason.
     */
    private void requireUsernameAvailable(String username) {
        if (userRepository.countByUsernameIncludingDeleted(username) > 0) {
            throw new IllegalArgumentException(
                "Username already in use: " + username
                + " — usernames are unique across the whole platform, not per organisation, "
                + "because login resolves a user by username with no tenant context.");
        }
    }

    /**
     * Email counterpart of {@link #requireUsernameAvailable}. {@code excludeUserId} is the row
     * being edited, so a user keeping their own address does not collide with themselves; pass
     * {@code null} on a create.
     */
    private void requireEmailAvailable(String email, Long excludeUserId) {
        long held = (excludeUserId == null)
            ? userRepository.countByEmailIncludingDeleted(email)
            : userRepository.countByEmailIncludingDeletedExcludingId(email, excludeUserId);
        if (held > 0) {
            throw new IllegalArgumentException(
                "Email already in use: " + email
                + " — email addresses are unique across the whole platform, not per organisation.");
        }
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
