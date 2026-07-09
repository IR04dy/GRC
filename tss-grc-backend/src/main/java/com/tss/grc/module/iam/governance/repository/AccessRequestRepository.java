package com.tss.grc.module.iam.governance.repository;

import com.tss.grc.module.iam.governance.entity.AccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RBAC Access-Governance P1 — access-request finders plus the NATIVE assignment
 * operations on the bare {@code iam.user_roles} join table (no JPA entity maps it;
 * the User↔Role @ManyToMany reads it, these queries write it). Expiry/revocation
 * HARD-DELETE the join row — the @ManyToMany does not honour soft-delete flags on
 * join tables, so removal is the only way the grant actually stops resolving.
 * History lives in {@code iam.access_requests} + the platform audit log.
 */
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    List<AccessRequest> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    List<AccessRequest> findByOrganisationIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, String status);

    Optional<AccessRequest> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    // ── native assignment ops on iam.user_roles ─────────────────────────────

    @Modifying
    @Query(value = """
        INSERT INTO iam.user_roles (user_id, role_id, assigned_at, assigned_by,
                                    valid_from, valid_to, justification, approved_by, status, source)
        SELECT :userId, :roleId, SYSUTCDATETIME(), :assignedBy,
               :validFrom, :validTo, :justification, :approvedBy, 'ACTIVE', 'REQUEST'
        WHERE NOT EXISTS (SELECT 1 FROM iam.user_roles
                          WHERE user_id = :userId AND role_id = :roleId)
        """, nativeQuery = true)
    int insertAssignment(@Param("userId") Long userId, @Param("roleId") Long roleId,
                         @Param("assignedBy") Long assignedBy,
                         @Param("validFrom") LocalDateTime validFrom,
                         @Param("validTo") LocalDateTime validTo,
                         @Param("justification") String justification,
                         @Param("approvedBy") Long approvedBy);

    @Modifying
    @Query(value = "DELETE FROM iam.user_roles WHERE user_id = :userId AND role_id = :roleId", nativeQuery = true)
    int deleteAssignment(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Modifying
    @Query(value = "DELETE FROM iam.user_roles WHERE user_id = :userId", nativeQuery = true)
    int deleteAllAssignments(@Param("userId") Long userId);

    /** Expiry sweep: remove every assignment whose validity window has passed. */
    @Modifying
    @Query(value = "DELETE FROM iam.user_roles WHERE valid_to IS NOT NULL AND valid_to < SYSUTCDATETIME()",
           nativeQuery = true)
    int expireAssignments();

    // ── JML hooks (plan §6.3) — LEAVER events from socops.iam_jml_event ─────

    @Query(value = """
        SELECT j.id, u.id AS user_id, j.subject_username
        FROM socops.iam_jml_event j
        JOIN iam.users u ON u.username = j.subject_username AND u.organisation_id = j.organisation_id
        WHERE j.organisation_id = :org AND j.event_type = 'LEAVER'
          AND j.revoked_at IS NULL AND j.is_deleted = 0
        """, nativeQuery = true)
    List<Object[]> findUnprocessedLeavers(@Param("org") Long org);

    @Modifying
    @Query(value = "UPDATE socops.iam_jml_event SET revoked_at = SYSUTCDATETIME() WHERE id = :id",
           nativeQuery = true)
    int markLeaverProcessed(@Param("id") Long jmlEventId);
}
