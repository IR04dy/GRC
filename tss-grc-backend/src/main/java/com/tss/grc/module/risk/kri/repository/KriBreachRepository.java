package com.tss.grc.module.risk.kri.repository;

import com.tss.grc.module.risk.kri.entity.KriBreach;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Org-scoped finders for {@link KriBreach}.
 *
 * <p>Hot path is "open breach for this KRI" — looked up on every
 * measurement insert so the engine can decide whether to open a new
 * breach row or close the existing one.
 */
public interface KriBreachRepository extends JpaRepository<KriBreach, Long> {

    /**
     * The single open breach for a KRI within a tenant. Schema does
     * not enforce uniqueness in SQL (an AMBER→RED transition closes
     * one row and opens another, briefly producing two) so this
     * finder returns the most recent open row.
     */
    Optional<KriBreach> findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
            Long organisationId, Long kriId);

    Optional<KriBreach> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
            Long organisationId, UUID externalId);

    List<KriBreach> findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByOpenedAtDesc(
            Long organisationId, Long kriId);

    List<KriBreach> findByOrganisationIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
            Long organisationId);

    long countByOrganisationIdAndBreachLevelAndClosedAtIsNullAndIsDeletedFalse(
            Long organisationId, String breachLevel);
}
