package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Permission;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.EffectiveAccessResponse;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.EffectiveGrant;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.GrantPair;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.MatrixCell;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.MatrixUpdateRequest;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleMatrixResponse;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleSummary;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleUpsertRequest;
import com.tss.grc.module.iam.repository.PermissionRepository;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService.AuditEventBuilder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * RBAC Access-Governance P0 (plan §6.1) — runtime management of the role catalogue and
 * the role × module × action permission matrix, plus the per-user effective-access view.
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>Org scoping — a tenant sees every global (null-org) role plus its own, but may
 *       <em>write</em> only its own. <b>Visible is not writable</b>: a global role is shared
 *       by every tenant, so one tenant editing {@code CISO}'s matrix would silently rewrite
 *       what {@code CISO} means for all the others. Changing a shared role is therefore a
 *       platform-scope act, permitted only to {@code PLATFORM_ADMIN} — the same rule
 *       {@link com.tss.grc.module.aml.sanctions.service.SanctionsListService} applies to
 *       platform-global screening lists. A tenant that needs a variant
 *       {@linkplain #cloneRole clones} the role into its own scope.</li>
 *   <li>System roles ({@code is_system_role}) are templates: matrix-tunable but not
 *       renamable or deletable.</li>
 *   <li>Structural matrix rules (§5.2): APPROVE requires READ, DELETE requires WRITE —
 *       violating grants are rejected with a clear message.</li>
 *   <li>Every matrix change is audited with before/after grant lists.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccessGovernanceService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRepository users;
    private final AuditLogService audit;

    // ── roles ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RoleSummary> listRoles() {
        Long org = org();
        Set<Long> privileged = privilegedRoleIds();   // once, not once per role
        return roles.findByOrganisationIdOrOrganisationIdIsNull(org).stream()
            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
            .sorted(Comparator.comparing(Role::getRoleName))
            .map(r -> toSummary(r, privileged.contains(r.getId())))
            .toList();
    }

    @Transactional
    public RoleSummary createRole(RoleUpsertRequest r) {
        Long org = org();
        boolean activeExists = roles.findByOrganisationIdOrOrganisationIdIsNull(org).stream()
            .anyMatch(x -> x.getRoleName().equalsIgnoreCase(r.roleName()));
        if (activeExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists: " + r.roleName());
        }
        // Revive pattern: Role carries @Where(is_deleted=0) so JPA cannot see a retired
        // (soft-deleted) role, yet UQ_role_name(organisation_id, role_name) has no
        // is_deleted filter and would reject the INSERT. Find + revive natively instead.
        Long retiredId = roles.findRetiredRoleId(org, r.roleName()).orElse(null);
        if (retiredId != null) {
            roles.clearRolePermissions(retiredId);   // clean grant slate
            roles.reviveRole(retiredId);
            Role revived = roles.findById(retiredId)
                .orElseThrow(() -> new IllegalStateException("Revived role not visible: " + retiredId));
            revived.setDisplayNameEn(r.displayNameEn());
            revived.setDisplayNameAr(r.displayNameAr());
            // F2-67 — the invariant is "a system role is never inactive", and it has to hold at
            // every writer of the flag, not just the reachable one. This branch is the third:
            // `updateRole` is guarded above, the fresh-role path below cannot break it because it
            // forces `isSystemRole = false`, and this one would honour a caller's `isActive=false`
            // on whatever it revived. Reaching it needs a *retired system role*, which no API path
            // can create (`deleteRole` refuses to retire a template) and which exists in neither
            // database — so this is belt-and-braces for a shape only a direct DB write produces.
            // It is here anyway because "unreachable given today's rows" is the reasoning that left
            // the updateRole hole open in the first place.
            boolean requestedInactive = r.isActive() != null && !r.isActive();
            if (Boolean.TRUE.equals(revived.getIsSystemRole()) && requestedInactive) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "System roles are templates and cannot be deactivated — revive it active, or "
                        + "clone it into this tenant and deactivate the copy instead.");
            }
            revived.setIsActive(!requestedInactive);
            Role saved = roles.save(revived);
            audit.log("CREATE", "Role", saved.getId(), "Role revived: " + saved.getRoleName());
            return toSummary(saved);
        }
        Role e = new Role();
        e.setOrganisationId(org);
        e.setRoleName(r.roleName());
        e.setDisplayNameEn(r.displayNameEn());
        e.setDisplayNameAr(r.displayNameAr());
        e.setIsSystemRole(false);
        e.setIsActive(r.isActive() == null || r.isActive());
        Role saved = roles.save(e);
        audit.log("CREATE", "Role", saved.getId(), "Role created: " + saved.getRoleName());
        return toSummary(saved);
    }

    /**
     * Copies a readable role — typically a shared global template — into the calling tenant
     * under a new name, carrying its grant set across. This is the other half of
     * {@link #mustOwnRole}'s refusal: a tenant that needs {@code CISO} with two extra grants
     * gets its own {@code CISO_*} role rather than mutating the one every other tenant uses.
     *
     * <p>The copy is deliberately <b>not</b> a system role: it is tenant-owned, so it must be
     * renamable and retirable, which {@link #updateRole}/{@link #deleteRole} refuse for
     * templates. Grants are copied by reference to the same {@link Permission} rows — the
     * catalogue is platform-wide, only the role↔permission edges are per-role.
     */
    @Transactional
    public RoleSummary cloneRole(Long sourceId, RoleUpsertRequest r) {
        Role source = mustReadableRole(sourceId);
        Long org = org();
        // The source is always in this list — mustReadableRole only returns roles that are
        // global or tenant-owned, which is exactly what the query selects. So asking for a
        // clone under the source's own name lands here as "already exists", and no separate
        // same-name check is reachable. (One was written and deleted: the live probe returned
        // 409 where it predicted 400, and the unit test covering it had only passed because it
        // mocked an empty visible list — a repository state that cannot occur.)
        boolean taken = roles.findByOrganisationIdOrOrganisationIdIsNull(org).stream()
            .anyMatch(x -> x.getRoleName().equalsIgnoreCase(r.roleName()));
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists: " + r.roleName());
        }
        Role copy = new Role();
        copy.setOrganisationId(org);
        copy.setRoleName(r.roleName());
        copy.setDisplayNameEn(r.displayNameEn());
        copy.setDisplayNameAr(r.displayNameAr());
        copy.setIsSystemRole(false);
        copy.setIsActive(r.isActive() == null || r.isActive());
        copy.getPermissions().addAll(source.getPermissions());
        Role saved = roles.save(copy);

        audit.recordEvent(new AuditEventBuilder()
            .actionType("CREATE")
            .sourceModule("IAM")
            .entityType("Role")
            .entityId(saved.getId())
            .entityCode(saved.getRoleName())
            .newValueJson("{\"clonedFrom\":\"" + source.getRoleName() + "\",\"grants\":\""
                + grantCsv(saved.getPermissions()) + "\"}")
            .message("Role " + saved.getRoleName() + " cloned from "
                + source.getRoleName() + " (" + saved.getPermissions().size() + " grants)"));
        return toSummary(saved);
    }

    @Transactional
    public RoleSummary updateRole(Long id, RoleUpsertRequest r) {
        Role e = mustOwnRole(id);
        if (Boolean.TRUE.equals(e.getIsSystemRole()) && !e.getRoleName().equals(r.roleName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "System roles cannot be renamed");
        }
        // F2-67 — a platform-shipped role cannot be DEACTIVATED either, and until F2-62 that
        // omission cost nothing.
        //
        // `deleteRole` below refuses a system role outright ("templates cannot be deleted") and
        // implements retirement as `setIsActive(false)` + `softDelete`. Since F2-62 made
        // `is_active` enforce, the first half of that pair — reachable from here — has nearly the
        // whole blast radius of the second: every holder of the role loses its ROLE_ marker and
        // every permission on the next request. So deactivation was the **unguarded half of an
        // operation whose other half is explicitly forbidden**, and the lifecycle protection on
        // templates was one method wide.
        //
        // Not currently exploitable, and the reason is worth stating because it is not a defence:
        // every system role is global (V334/F2-56) and `mustOwnRole` rejects global roles before
        // this line, so today the guard is unreachable. That is defence by the *shape of the data*,
        // not by design — production carried 46 tenant-scoped system roles until V334 ran, and it
        // acquired them by being restored from a dev dump, which a future restore can repeat.
        // Deactivating PLATFORM_ADMIN under that shape would have stripped the admin bypass from
        // every holder, with no administrator left to reverse it.
        //
        // Reactivation stays allowed: `isActive = true` can only ever restore access, and refusing
        // it would make a template that somehow became inactive unrecoverable through the API.
        // A tenant that wants a disableable variant clones the role ({@link #cloneRole}) — the copy
        // is deliberately non-system, which is exactly the population F2-62's semantics are for.
        if (Boolean.TRUE.equals(e.getIsSystemRole()) && Boolean.FALSE.equals(r.isActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "System roles are templates and cannot be deactivated — they would grant nothing to "
                    + "every holder. Clone the role into this tenant and deactivate the copy instead.");
        }
        e.setRoleName(r.roleName());
        e.setDisplayNameEn(r.displayNameEn());
        e.setDisplayNameAr(r.displayNameAr());
        if (r.isActive() != null) e.setIsActive(r.isActive());
        Role saved = roles.save(e);
        audit.log("UPDATE", "Role", saved.getId(), "Role updated: " + saved.getRoleName());
        return toSummary(saved);
    }

    @Transactional
    public void deleteRole(Long id, Long actorUserId) {
        Role e = mustOwnRole(id);
        if (Boolean.TRUE.equals(e.getIsSystemRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "System roles are templates and cannot be deleted");
        }
        e.setIsActive(false);
        e.softDelete(actorUserId);
        roles.save(e);
        audit.log("DELETE", "Role", e.getId(), "Role retired: " + e.getRoleName());
    }

    // ── permission matrix ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RoleMatrixResponse getMatrix(Long roleId) {
        Role role = mustReadableRole(roleId);
        Set<String> granted = role.getPermissions().stream()
            .map(Permission::getPermissionName).collect(Collectors.toSet());
        List<MatrixCell> cells = permissions.findAllByOrderByModuleAscActionAsc().stream()
            .map(p -> new MatrixCell(p.getModule(), p.getAction(), p.getPermissionName(),
                granted.contains(p.getPermissionName())))
            .toList();
        return new RoleMatrixResponse(role.getId(), role.getRoleName(),
            Boolean.TRUE.equals(role.getIsSystemRole()), cells);
    }

    @Transactional
    public RoleMatrixResponse putMatrix(Long roleId, MatrixUpdateRequest req) {
        Role role = mustOwnRole(roleId);

        // Resolve requested pairs against the catalogue (unknown pairs are rejected).
        Set<Permission> next = new HashSet<>();
        for (GrantPair g : req.grants()) {
            Permission p = permissions.findByModuleAndAction(g.module().toUpperCase(), g.action().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown permission: " + g.module() + "/" + g.action()));
            next.add(p);
        }
        // Structural rules (§5.2): APPROVE ⇒ READ, DELETE ⇒ WRITE (same module).
        Map<String, Set<String>> byModule = next.stream().collect(Collectors.groupingBy(
            Permission::getModule, Collectors.mapping(Permission::getAction, Collectors.toSet())));
        for (Map.Entry<String, Set<String>> m : byModule.entrySet()) {
            Set<String> acts = m.getValue();
            if (acts.contains("APPROVE") && !acts.contains("READ")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    m.getKey() + ": APPROVE requires READ");
            }
            if (acts.contains("DELETE") && !acts.contains("WRITE")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    m.getKey() + ": DELETE requires WRITE");
            }
        }

        String before = grantCsv(role.getPermissions());
        role.getPermissions().clear();
        role.getPermissions().addAll(next);
        Role saved = roles.save(role);
        String after = grantCsv(saved.getPermissions());

        audit.recordEvent(new AuditEventBuilder()
            .actionType("UPDATE")
            .sourceModule("IAM")
            .entityType("RolePermissionMatrix")
            .entityId(saved.getId())
            .entityCode(saved.getRoleName())
            .oldValueJson("{\"grants\":\"" + before + "\"}")
            .newValueJson("{\"grants\":\"" + after + "\"}")
            .message("Permission matrix updated for role " + saved.getRoleName()
                + " (" + saved.getPermissions().size() + " grants)"));
        return getMatrix(saved.getId());
    }

    // ── effective access ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EffectiveAccessResponse effectiveAccess(Long userId) {
        Long org = org();
        User u = users.findById(userId)
            .filter(x -> org.equals(x.getOrganisationId()))
            .orElseThrow(() -> new EntityNotFoundException("User not found in this organisation: " + userId));

        // F2-62 — this endpoint is named "effective access", so it must report what the auth
        // path would actually mint, not what is merely assigned. An inactive role confers no
        // authorities (see Role#grantsAuthority), so it delivers no grants here and does not
        // confer the PLATFORM_ADMIN bypass either. `roles` still lists every assignment —
        // deactivation does not un-assign, and an admin needs to see the row exists — with
        // `inactiveRoles` naming the subset that grants nothing, so the caller can tell a
        // granting assignment from an inert one instead of inferring it from an empty table.
        List<String> roleNames = u.getRoles().stream().map(Role::getRoleName).sorted().toList();
        List<String> inactiveRoles = u.getRoles().stream()
            .filter(r -> !r.grantsAuthority())
            .map(Role::getRoleName).sorted().toList();
        boolean platformAdmin = u.getRoles().stream()
            .anyMatch(r -> r.grantsAuthority() && "PLATFORM_ADMIN".equals(r.getRoleName()));

        // module/action → set of delivering roles
        Map<String, Set<String>> via = new TreeMap<>();
        for (Role r : u.getRoles()) {
            if (!r.grantsAuthority()) continue;
            for (Permission p : r.getPermissions()) {
                via.computeIfAbsent(p.getModule() + "/" + p.getAction(), k -> new HashSet<>())
                    .add(r.getRoleName());
            }
        }
        List<EffectiveGrant> grants = new ArrayList<>();
        via.forEach((key, rs) -> {
            String[] ma = key.split("/", 2);
            grants.add(new EffectiveGrant(ma[0], ma[1], rs.stream().sorted().toList()));
        });
        return new EffectiveAccessResponse(u.getId(), u.getUsername(), platformAdmin, roleNames,
            inactiveRoles, grants);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Role the caller may write. {@link #mustReadableRole} has already 404'd anything owned
     * by another tenant, so what remains to decide is the shared case: a global (null-org)
     * role belongs to every tenant at once, and rewriting its grants from inside one tenant
     * would change authorisation for all of them. That is a platform-scope act, so only
     * {@code PLATFORM_ADMIN} may perform it; a tenant needing a variant clones instead
     * (F2-56 — and the advice in the message below is only now actually true, since
     * {@link #cloneRole} did not exist when this guard was first written).
     */
    private Role mustOwnRole(Long id) {
        Role r = mustReadableRole(id);
        if (r.getOrganisationId() == null && !CurrentActor.isPlatformAdmin()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Global roles are read-only from a tenant; clone into the tenant to customise");
        }
        return r;
    }

    private Role mustReadableRole(Long id) {
        Long org = org();
        return roles.findById(id)
            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
            .filter(r -> r.getOrganisationId() == null || r.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
    }

    /**
     * F2-280 — the privileged ids this tenant's roles resolve to, from the one shared
     * predicate. One query per call rather than a per-role check: the same mechanism serves the
     * list and the single-role returns, so there is no second way to ask the question (F2-190).
     */
    private Set<Long> privilegedRoleIds() {
        return new HashSet<>(roles.privilegedRoleIds(org()));
    }

    private RoleSummary toSummary(Role r) {
        return toSummary(r, privilegedRoleIds().contains(r.getId()));
    }

    private static RoleSummary toSummary(Role r, boolean privileged) {
        return new RoleSummary(r.getId(), r.getRoleName(), r.getDisplayNameEn(), r.getDisplayNameAr(),
            Boolean.TRUE.equals(r.getIsSystemRole()), Boolean.TRUE.equals(r.getIsActive()),
            r.getOrganisationId(), privileged);
    }

    private static String grantCsv(Set<Permission> perms) {
        return perms.stream().map(Permission::getPermissionName).sorted()
            .collect(Collectors.joining(","));
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }
}
