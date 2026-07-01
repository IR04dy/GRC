package com.tss.grc.module.risk.kri.repository;

import com.tss.grc.module.risk.kri.entity.KriDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Org-scoped finders for {@link KriDefinition}.
 *
 * <p>All queries derive from JPA method-name conventions so they
 * automatically pick up the {@code @Where(is_deleted = 0)} filter on
 * the entity.
 */
public interface KriDefinitionRepository extends JpaRepository<KriDefinition, Long> {

    List<KriDefinition> findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(Long organisationId);

    Optional<KriDefinition> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
            Long organisationId, UUID externalId);

    Optional<KriDefinition> findByOrganisationIdAndKriCodeAndIsDeletedFalse(
            Long organisationId, String kriCode);

    long countByOrganisationIdAndKriCode(Long organisationId, String kriCode);

    /**
     * Active KRIs for a tenant — the scheduler's per-tenant evaluation
     * walk filters down to AUTO_SQL / AUTO_CALLBACK / METRIC_API in
     * Java (rather than in the query) so the method-name finder stays
     * legible.
     */
    List<KriDefinition> findByOrganisationIdAndIsActiveTrueAndIsDeletedFalse(Long organisationId);

    /**
     * Tenant-wide enumeration used by the scheduler — walks every
     * active KRI across every tenant on the scheduled tick.
     */
    List<KriDefinition> findByIsActiveTrueAndIsDeletedFalse();

    List<KriDefinition> findByOrganisationIdAndCategoryAndIsDeletedFalseOrderByKriCodeAsc(
            Long organisationId, String category);

    // ── E-E consolidation: compliance ↔ KRI crosswalk (replaces the legacy
    //    engine's framework_id finders used by FrameworkAnchorService). ──
    long countByOrganisationIdAndLinkedFrameworkIdAndIsDeletedFalse(
            Long organisationId, Long linkedFrameworkId);

    List<KriDefinition> findByOrganisationIdAndLinkedFrameworkIdAndIsDeletedFalseOrderByKriCodeAsc(
            Long organisationId, Long linkedFrameworkId);

    /** Board-pack / export totals (replaces legacy countByOrganisationId). */
    long countByOrganisationIdAndIsDeletedFalse(Long organisationId);
}
