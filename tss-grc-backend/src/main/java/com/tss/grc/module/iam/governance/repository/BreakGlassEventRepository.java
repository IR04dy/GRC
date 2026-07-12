package com.tss.grc.module.iam.governance.repository;

import com.tss.grc.module.iam.governance.entity.BreakGlassEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RBAC Access-Governance P3 — break-glass ledger plus the analytics natives
 * (plan §6.7). The break-glass assignment itself is a native insert into the bare
 * {@code iam.user_roles} join table with {@code source='BREAK_GLASS'}; the P1 hourly
 * expiry sweep hard-deletes it once {@code valid_to} passes.
 */
public interface BreakGlassEventRepository extends JpaRepository<BreakGlassEvent, Long> {

    List<BreakGlassEvent> findByOrganisationIdAndIsDeletedFalseOrderByActivatedAtDesc(Long organisationId);

    Optional<BreakGlassEvent> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    long countByOrganisationIdAndStatusAndIsDeletedFalse(Long organisationId, String status);

    long countByOrganisationIdAndReviewStatusAndIsDeletedFalse(Long organisationId, String reviewStatus);

    /** Grant the emergency role: time-bound, source BREAK_GLASS, self-assigned by design. */
    @Modifying
    @Query(value = """
        INSERT INTO iam.user_roles (user_id, role_id, assigned_at, assigned_by,
                                    valid_from, valid_to, justification, approved_by, status, source)
        SELECT :userId, :roleId, SYSUTCDATETIME(), :userId,
               SYSUTCDATETIME(), :validTo, :justification, NULL, 'ACTIVE', 'BREAK_GLASS'
        WHERE NOT EXISTS (SELECT 1 FROM iam.user_roles
                          WHERE user_id = :userId AND role_id = :roleId)
        """, nativeQuery = true)
    int insertBreakGlassAssignment(@Param("userId") Long userId, @Param("roleId") Long roleId,
                                   @Param("validTo") LocalDateTime validTo,
                                   @Param("justification") String justification);

    /** Flip past-due ACTIVE events to EXPIRED (the user_roles row dies via the P1 sweep). */
    @Modifying
    @Query(value = """
        UPDATE iam.break_glass_events SET status = 'EXPIRED'
        WHERE status = 'ACTIVE' AND expires_at < SYSUTCDATETIME() AND is_deleted = 0
        """, nativeQuery = true)
    int expireEvents();

    // ── analytics natives (plan §6.7) ────────────────────────────────────────

    /** Distinct users holding at least one privileged role. */
    @Query(value = """
        SELECT COUNT(DISTINCT ur.user_id) FROM iam.user_roles ur
        JOIN iam.roles r ON r.id = ur.role_id AND r.is_privileged = 1 AND r.is_deleted = 0
        JOIN iam.users u ON u.id = ur.user_id AND u.organisation_id = :org AND u.is_deleted = 0
        """, nativeQuery = true)
    long countPrivilegedHolders(@Param("org") Long org);

    /** Assignments held by users with no login in ≥90 days (or never). */
    @Query(value = """
        SELECT COUNT(*) FROM iam.user_roles ur
        JOIN iam.users u ON u.id = ur.user_id AND u.organisation_id = :org AND u.is_deleted = 0
        WHERE u.last_login_at IS NULL OR u.last_login_at < DATEADD(day, -90, SYSUTCDATETIME())
        """, nativeQuery = true)
    long countDormantGrants(@Param("org") Long org);

    /** Time-bound assignments expiring within the next 30 days. */
    @Query(value = """
        SELECT COUNT(*) FROM iam.user_roles ur
        JOIN iam.users u ON u.id = ur.user_id AND u.organisation_id = :org AND u.is_deleted = 0
        WHERE ur.valid_to IS NOT NULL
          AND ur.valid_to BETWEEN SYSUTCDATETIME() AND DATEADD(day, 30, SYSUTCDATETIME())
        """, nativeQuery = true)
    long countExpiringSoon(@Param("org") Long org);

    /** V294 (§10) — PLATFORM_ADMIN bypass visibility: audited actions in the last 30 days
     *  by users holding the bypass role. */
    @Query(value = """
        SELECT COUNT(*) FROM platform.audit_log al
        WHERE al.organisation_id = :org
          AND al.created_at > DATEADD(day, -30, SYSUTCDATETIME())
          AND al.user_id IN (SELECT ur.user_id FROM iam.user_roles ur
                             JOIN iam.roles r ON r.id = ur.role_id
                             WHERE r.role_name = 'PLATFORM_ADMIN')
        """, nativeQuery = true)
    long countPlatformAdminActions30d(@Param("org") Long org);

    /** Top users by distinct effective grants (over-privilege candidates). */
    @Query(value = """
        SELECT TOP 5 u.username, COUNT(DISTINCT rp.permission_id) AS grants
        FROM iam.users u
        JOIN iam.user_roles ur ON ur.user_id = u.id
        JOIN iam.role_permissions rp ON rp.role_id = ur.role_id
        WHERE u.organisation_id = :org AND u.is_deleted = 0
        GROUP BY u.username
        ORDER BY grants DESC
        """, nativeQuery = true)
    List<Object[]> topGrantHolders(@Param("org") Long org);
}
