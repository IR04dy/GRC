package com.tss.grc.module.aml.sanctions.repository;

import com.tss.grc.module.aml.sanctions.entity.SanctionsListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link SanctionsListEntry}.
 *
 * <p>Entries inherit their parent list's tenancy. Most reads filter by
 * {@code listId} and rely on the list-level org check to enforce
 * isolation.
 */
public interface SanctionsListEntryRepository extends JpaRepository<SanctionsListEntry, Long> {

    List<SanctionsListEntry> findByListIdAndIsDeletedFalseOrderByPrimaryNameEnAsc(Long listId);

    long countByListIdAndIsDeletedFalse(Long listId);

    Optional<SanctionsListEntry> findByListIdAndEntryExternalIdAndIsDeletedFalse(
        Long listId, String entryExternalId);

    boolean existsByListIdAndEntryExternalIdAndIsDeletedFalse(Long listId, String entryExternalId);

    /**
     * All entries across all lists visible to the org. Used by the
     * fuzzy matcher — kept small because lists are usually platform-
     * global, so the visible set is shared across tenants.
     */
    @Query("SELECT e FROM SanctionsListEntry e " +
           "JOIN com.tss.grc.module.aml.sanctions.entity.SanctionsList l ON e.listId = l.id " +
           "WHERE e.isDeleted = false AND l.isDeleted = false AND l.isActive = true " +
           "AND (l.organisationId IS NULL OR l.organisationId = :orgId)")
    List<SanctionsListEntry> findAllActiveVisibleTo(@Param("orgId") Long organisationId);

    /**
     * Active entries from a specific subset of list codes. The matcher
     * loads only the lists named in the {@code listsToScreen} parameter
     * to keep the in-memory candidate set bounded.
     */
    @Query("SELECT e FROM SanctionsListEntry e " +
           "JOIN com.tss.grc.module.aml.sanctions.entity.SanctionsList l ON e.listId = l.id " +
           "WHERE e.isDeleted = false AND l.isDeleted = false AND l.isActive = true " +
           "AND (l.organisationId IS NULL OR l.organisationId = :orgId) " +
           "AND l.listCode IN :listCodes")
    List<SanctionsListEntry> findActiveByListCodesVisibleTo(
        @Param("listCodes") List<String> listCodes, @Param("orgId") Long organisationId);
}
