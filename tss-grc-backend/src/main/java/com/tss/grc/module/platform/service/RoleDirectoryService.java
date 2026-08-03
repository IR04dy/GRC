package com.tss.grc.module.platform.service;

import com.tss.grc.module.iam.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Answers two questions about platform roles, for callers that must not import {@code iam}.
 *
 * <h3>Why it lives in {@code platform}</h3>
 * ADR-012 lets a business module reach {@code common.*}, {@code security.*}, {@code platform.*}
 * and its own {@code spi} — never a peer module, and {@code iam} is a peer. `change_mgmt` needs
 * both of these answers for F2-95, and `ChangeModuleBoundaryTest` would fail an `iam` import.
 * A port + adapter (S5a's pattern) would also work; this is a read-only directory lookup with
 * no module-specific semantics, so it belongs to the platform rather than to Change's SPI.
 *
 * <h3>Why {@link #enforceabilityByRole} and not a boolean "role exists"</h3>
 * A required role can fail to be checkable in two ways, and they are different faults with
 * different remedies — the same split F2-94 needed for notification delivery. This method is
 * built on the <b>same</b> {@code countActiveHoldersByRole} query the notification-reachability
 * report uses, deliberately: one predicate, two consumers. F2-62's defect was two subsystems
 * computing the same fact separately and drifting.
 */
@Service
@RequiredArgsConstructor
public class RoleDirectoryService {

    /** The name is not a role in this tenant. No grant can satisfy it. */
    public static final String NOT_A_ROLE = "NOT_A_ROLE";
    /** A real role that no active, unlocked user in this tenant holds. Nobody could satisfy it. */
    public static final String NO_ACTIVE_HOLDER = "NO_ACTIVE_HOLDER";
    /** A real role with at least one active holder — a requirement naming it is checkable. */
    public static final String ENFORCEABLE = "ENFORCEABLE";

    private final RoleRepository roles;

    /** Upper-cased platform role names held by {@code userId}; empty if null or unknown. */
    @Transactional(readOnly = true)
    public Set<String> rolesOf(Long userId) {
        if (userId == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String name : roles.findRoleNamesByUserId(userId)) {
            if (name != null && !name.isBlank()) out.add(name.trim().toUpperCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * Every role name visible to the tenant mapped to {@link #ENFORCEABLE} or
     * {@link #NO_ACTIVE_HOLDER}. A name absent from the result is {@link #NOT_A_ROLE} — the
     * caller decides, because "absent" is only meaningful against the name it asked about.
     */
    @Transactional(readOnly = true)
    public Map<String, String> enforceabilityByRole(Long organisationId) {
        Map<String, String> out = new LinkedHashMap<>();
        if (organisationId == null) return out;
        for (Object[] row : roles.countActiveHoldersByRole(organisationId)) {
            if (row.length < 2 || row[0] == null) continue;
            int holders = row[1] == null ? 0 : ((Number) row[1]).intValue();
            out.put(String.valueOf(row[0]).toUpperCase(Locale.ROOT),
                holders > 0 ? ENFORCEABLE : NO_ACTIVE_HOLDER);
        }
        return out;
    }

    /** {@link #ENFORCEABLE}, {@link #NO_ACTIVE_HOLDER} or {@link #NOT_A_ROLE} for one name. */
    public String enforceability(Map<String, String> byRole, String roleName) {
        if (roleName == null || roleName.isBlank()) return NOT_A_ROLE;
        return byRole.getOrDefault(roleName.trim().toUpperCase(Locale.ROOT), NOT_A_ROLE);
    }
}
