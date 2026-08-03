package com.tss.grc.module.iam.repository;

import com.tss.grc.module.iam.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByOrganisationIdOrOrganisationIdIsNull(Long organisationId);
    List<Role> findByIsSystemRoleTrue();

    // ── retired-role revive support (RBAC governance P0) ────────────────────
    // Role carries @Where(is_deleted = 0), so JPA can never see a soft-deleted
    // role — yet UQ_role_name(organisation_id, role_name) has no is_deleted
    // filter, so a retired role blocks re-creating the same name. These native
    // queries bypass @Where to find + revive the retired row instead.

    @Query(value = "SELECT TOP 1 id FROM iam.roles WHERE organisation_id = :org "
                 + "AND UPPER(role_name) = UPPER(:name) AND is_deleted = 1", nativeQuery = true)
    Optional<Long> findRetiredRoleId(@Param("org") Long org, @Param("name") String name);

    @Modifying
    @Query(value = "UPDATE iam.roles SET is_deleted = 0, deleted_at = NULL, deleted_by = NULL, "
                 + "is_active = 1 WHERE id = :id", nativeQuery = true)
    void reviveRole(@Param("id") Long id);

    /** A revived role starts with a clean grant slate. */
    @Modifying
    @Query(value = "DELETE FROM iam.role_permissions WHERE role_id = :id", nativeQuery = true)
    void clearRolePermissions(@Param("id") Long id);

    /**
     * F2-94 — every role name visible to the tenant (its own plus the global ones) paired
     * with the number of users who would actually receive a broadcast addressed to it.
     * One query, so a report over ~84 addressed role names does not become ~84 round trips.
     *
     * <p><b>The holder predicate is deliberately identical to
     * {@code UserRepository#findActiveByOrganisationIdAndRoleName}</b> — active, not locked,
     * not soft-deleted, same tenant. F2-62's defect was a report and an enforcement point
     * computing access separately and drifting apart, each half defensible alone. A role
     * absent from this result is not a role at all, which is a different fault from a role
     * present with zero holders, and the two have different remedies.
     *
     * @return rows of {@code [role_name (String), active_holders (Number)]}
     */
    @Query(value = """
        SELECT ro.role_name AS role_name, COUNT(DISTINCT u.id) AS active_holders
          FROM iam.roles ro
          LEFT JOIN iam.user_roles ur ON ur.role_id = ro.id
          LEFT JOIN iam.users u ON u.id = ur.user_id
               AND u.organisation_id = :org
               AND u.is_active = 1
               AND ISNULL(u.is_deleted, 0) = 0
               AND (u.is_locked IS NULL OR u.is_locked = 0)
         WHERE ro.is_deleted = 0
           AND (ro.organisation_id = :org OR ro.organisation_id IS NULL)
         GROUP BY ro.role_name
        """, nativeQuery = true)
    List<Object[]> countActiveHoldersByRole(@Param("org") Long org);
}
