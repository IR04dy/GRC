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
}
