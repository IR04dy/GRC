package com.tss.grc.module.aml.sanctions.repository;

import com.tss.grc.module.aml.sanctions.entity.SanctionsList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Org-NULL-aware repository for {@link SanctionsList}.
 *
 * <p>Lists with {@code organisation_id IS NULL} are platform-global —
 * every tenant can read them. Lists with a specific {@code organisation_id}
 * are tenant-scoped. The {@code findVisibleTo} variants return both.
 */
public interface SanctionsListRepository extends JpaRepository<SanctionsList, Long> {

    /** Org-scoped + platform-global, ordered by source type then code. */
    @Query("SELECT l FROM SanctionsList l WHERE l.isDeleted = false " +
           "AND (l.organisationId IS NULL OR l.organisationId = :orgId) " +
           "ORDER BY l.sourceType ASC, l.listCode ASC")
    List<SanctionsList> findVisibleTo(@Param("orgId") Long organisationId);

    /** Only platform-global lists (org NULL). */
    @Query("SELECT l FROM SanctionsList l WHERE l.isDeleted = false " +
           "AND l.organisationId IS NULL " +
           "ORDER BY l.sourceType ASC, l.listCode ASC")
    List<SanctionsList> findGlobal();

    /** Only org-scoped lists for the tenant. */
    List<SanctionsList> findByOrganisationIdAndIsDeletedFalseOrderByListCodeAsc(Long organisationId);

    @Query("SELECT l FROM SanctionsList l WHERE l.isDeleted = false " +
           "AND l.id = :id AND (l.organisationId IS NULL OR l.organisationId = :orgId)")
    Optional<SanctionsList> findByIdVisibleTo(@Param("id") Long id, @Param("orgId") Long orgId);

    @Query("SELECT l FROM SanctionsList l WHERE l.isDeleted = false " +
           "AND l.listCode = :listCode " +
           "AND (l.organisationId IS NULL OR l.organisationId = :orgId)")
    Optional<SanctionsList> findByListCodeVisibleTo(@Param("listCode") String listCode,
                                                    @Param("orgId") Long organisationId);

    /** Active visible lists — used by the screening service. */
    @Query("SELECT l FROM SanctionsList l WHERE l.isDeleted = false AND l.isActive = true " +
           "AND (l.organisationId IS NULL OR l.organisationId = :orgId) " +
           "ORDER BY l.sourceType ASC")
    List<SanctionsList> findActiveVisibleTo(@Param("orgId") Long organisationId);
}
