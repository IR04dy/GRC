package com.tss.grc.module.iam.governance.repository;

import com.tss.grc.module.iam.governance.entity.SodViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * RBAC Access-Governance P2 — SoD violations plus the NATIVE detection queries the
 * detective scan runs against the live grant landscape (iam.user_roles ×
 * role_permissions × permissions). Every detection query EXCLUDES holders of
 * PLATFORM_ADMIN — the super-admin bypasses the permission matrix in code by design
 * and is governed by dedicated controls (break-glass, P3), not the SoD engine.
 */
public interface SodViolationRepository extends JpaRepository<SodViolation, Long> {

    List<SodViolation> findByOrganisationIdAndIsDeletedFalseOrderByDetectedAtDesc(Long organisationId);

    List<SodViolation> findByOrganisationIdAndStatusAndIsDeletedFalseOrderByDetectedAtDesc(
        Long organisationId, String status);

    Optional<SodViolation> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    /** All non-deleted violations of (rule, user) — the scan filters "still active" in Java. */
    List<SodViolation> findByOrganisationIdAndRuleIdAndUserIdAndIsDeletedFalse(
        Long organisationId, Long ruleId, Long userId);

    long countByOrganisationIdAndStatusAndIsDeletedFalse(Long organisationId, String status);

    // ── detection: PAIR rules ────────────────────────────────────────────────

    /** Users of the org holding BOTH sides of a conflicting grant pair. */
    @Query(value = """
        SELECT u.id, u.username
        FROM iam.users u
        WHERE u.organisation_id = :org AND u.is_deleted = 0
          AND NOT EXISTS (SELECT 1 FROM iam.user_roles xr JOIN iam.roles xrole ON xrole.id = xr.role_id
                          WHERE xr.user_id = u.id AND xrole.role_name = 'PLATFORM_ADMIN')
          AND EXISTS (SELECT 1 FROM iam.user_roles ur
                      JOIN iam.role_permissions rp ON rp.role_id = ur.role_id
                      JOIN iam.permissions p ON p.id = rp.permission_id
                      WHERE ur.user_id = u.id AND p.module = :ma AND p.action = :aa)
          AND EXISTS (SELECT 1 FROM iam.user_roles ur
                      JOIN iam.role_permissions rp ON rp.role_id = ur.role_id
                      JOIN iam.permissions p ON p.id = rp.permission_id
                      WHERE ur.user_id = u.id AND p.module = :mb AND p.action = :ab)
        """, nativeQuery = true)
    List<Object[]> pairConflictUsers(@Param("org") Long org,
                                     @Param("ma") String moduleA, @Param("aa") String actionA,
                                     @Param("mb") String moduleB, @Param("ab") String actionB);

    // ── detection: STRUCTURAL rules ──────────────────────────────────────────

    /**
     * READONLY_PERSONA_WRITE — users holding an assurance/regulator role (name LIKE
     * {@code pattern}) who also hold any WRITE/APPROVE/DELETE grant outside the audit
     * domain (AUDIT / AUDIT_LOG are the personas' own working modules).
     */
    @Query(value = """
        SELECT DISTINCT u.id, u.username
        FROM iam.users u
        JOIN iam.user_roles ur0 ON ur0.user_id = u.id
        JOIN iam.roles r0 ON r0.id = ur0.role_id
        WHERE u.organisation_id = :org AND u.is_deleted = 0
          AND r0.role_name LIKE :pattern
          AND NOT EXISTS (SELECT 1 FROM iam.user_roles xr JOIN iam.roles xrole ON xrole.id = xr.role_id
                          WHERE xr.user_id = u.id AND xrole.role_name = 'PLATFORM_ADMIN')
          AND EXISTS (SELECT 1 FROM iam.user_roles ur
                      JOIN iam.role_permissions rp ON rp.role_id = ur.role_id
                      JOIN iam.permissions p ON p.id = rp.permission_id
                      WHERE ur.user_id = u.id
                        AND p.action IN ('WRITE','APPROVE','DELETE')
                        AND p.module NOT IN ('AUDIT','AUDIT_LOG'))
        """, nativeQuery = true)
    List<Object[]> readonlyPersonaWriters(@Param("org") Long org, @Param("pattern") String rolePattern);

    /**
     * SERVICE_MULTI_MODULE — users holding a service-account role (name LIKE
     * {@code pattern}) whose effective grants span two or more modules.
     */
    @Query(value = """
        SELECT DISTINCT u.id, u.username
        FROM iam.users u
        JOIN iam.user_roles ur0 ON ur0.user_id = u.id
        JOIN iam.roles r0 ON r0.id = ur0.role_id
        WHERE u.organisation_id = :org AND u.is_deleted = 0
          AND r0.role_name LIKE :pattern
          AND NOT EXISTS (SELECT 1 FROM iam.user_roles xr JOIN iam.roles xrole ON xrole.id = xr.role_id
                          WHERE xr.user_id = u.id AND xrole.role_name = 'PLATFORM_ADMIN')
          AND (SELECT COUNT(DISTINCT p.module) FROM iam.user_roles ur
               JOIN iam.role_permissions rp ON rp.role_id = ur.role_id
               JOIN iam.permissions p ON p.id = rp.permission_id
               WHERE ur.user_id = u.id) >= 2
        """, nativeQuery = true)
    List<Object[]> serviceMultiModuleUsers(@Param("org") Long org, @Param("pattern") String rolePattern);

    // ── preventive-check helpers ─────────────────────────────────────────────

    /** Does the user currently hold a grant (module, action) through any role? */
    @Query(value = """
        SELECT COUNT(1) FROM iam.user_roles ur
        JOIN iam.role_permissions rp ON rp.role_id = ur.role_id
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE ur.user_id = :userId AND p.module = :module AND p.action = :action
        """, nativeQuery = true)
    int countUserGrant(@Param("userId") Long userId, @Param("module") String module,
                       @Param("action") String action);

    /** The (module, action) pairs a role delivers. */
    @Query(value = """
        SELECT p.module, p.action FROM iam.role_permissions rp
        JOIN iam.permissions p ON p.id = rp.permission_id
        WHERE rp.role_id = :roleId
        """, nativeQuery = true)
    List<Object[]> roleGrantPairs(@Param("roleId") Long roleId);

    /**
     * Does the user hold the named role as an ACTIVE grant to a LIVE (not soft-deleted) role?
     * (GRC_ADMINISTRATOR / PLATFORM_ADMIN override + exception-closure checks via
     * {@code SodService#isGovernanceAdmin}.)
     *
     * <p>F2-384 — the {@code AND r.is_deleted = 0} aligns this authz gate with the platform's authz
     * SOURCE OF TRUTH: {@code RoleRepository#findRoleNamesByUserId}, the native query
     * {@code CustomUserDetailsService} uses to build a user's JWT authorities, filters retired roles,
     * so a soft-deleted role confers no authority anywhere. Without the filter this gate treated a
     * retired admin role's surviving grant as still conferring governance-admin power — the drifted
     * lone outlier among the role readers ({@code privilegedRoleIds} / {@code countActiveHoldersByRole}
     * filter too). Latent today (GRC_ADMINISTRATOR / PLATFORM_ADMIN are system roles that
     * {@code AccessGovernanceService#deleteRole} refuses to retire, so no such row is reachable via the
     * API), and the alignment is provably non-breaking: the filter can only exclude a role the JWT
     * authz path already excludes, so no live governance admin loses status. The detective-scan queries
     * above deliberately keep their unfiltered {@code iam.roles} joins — whether a dangling grant to a
     * retired role is a live SoD signal ("the live grant landscape") is a governance-policy call, filed
     * for the IAM owner rather than changed here.
     */
    @Query(value = """
        SELECT COUNT(1) FROM iam.user_roles ur
        JOIN iam.roles r ON r.id = ur.role_id
        WHERE ur.user_id = :userId AND r.role_name = :roleName AND r.is_deleted = 0
        """, nativeQuery = true)
    int countUserRole(@Param("userId") Long userId, @Param("roleName") String roleName);

    /** Org ids with live users — the nightly detective scan iterates these. */
    @Query(value = "SELECT DISTINCT organisation_id FROM iam.users WHERE is_deleted = 0", nativeQuery = true)
    List<Long> distinctOrganisationIds();
}
