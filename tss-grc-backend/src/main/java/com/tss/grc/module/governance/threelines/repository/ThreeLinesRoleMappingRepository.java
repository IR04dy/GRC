package com.tss.grc.module.governance.threelines.repository;

import com.tss.grc.module.governance.threelines.entity.ThreeLinesRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the role → LoD band mapping.
 *
 * <p>Queries here are deliberately "org-aware fallback" — the service
 * always asks for the tenant's row first, and only falls back to the
 * system-wide (organisationId = NULL) default when there is no tenant
 * override. That makes the resolution rule explicit and auditable.
 */
public interface ThreeLinesRoleMappingRepository extends JpaRepository<ThreeLinesRoleMapping, Long> {

    /** Per-tenant override (NULL-safe for the WHERE-organisationId clause). */
    Optional<ThreeLinesRoleMapping> findByOrganisationIdAndRoleName(Long organisationId, String roleName);

    /** System-wide default. */
    Optional<ThreeLinesRoleMapping> findByOrganisationIdIsNullAndRoleName(String roleName);

    List<ThreeLinesRoleMapping> findByOrganisationIdAndLodBand(Long organisationId, String lodBand);

    List<ThreeLinesRoleMapping> findByOrganisationIdIsNullAndLodBand(String lodBand);

    /** All effective mappings for the tenant — per-tenant rows union'd with
     *  defaults the tenant has not overridden. */
    @Query("""
        SELECT m FROM ThreeLinesRoleMapping m
         WHERE m.organisationId = :orgId
            OR (m.organisationId IS NULL
                 AND NOT EXISTS (SELECT 1 FROM ThreeLinesRoleMapping x
                                  WHERE x.organisationId = :orgId
                                    AND x.roleName = m.roleName))
        """)
    List<ThreeLinesRoleMapping> findEffectiveForOrg(@Param("orgId") Long orgId);
}
