package com.tss.grc.module.openbanking.repository;

import com.tss.grc.module.openbanking.entity.ObConsent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ObConsentRepository extends JpaRepository<ObConsent, Long> {

    Page<ObConsent> findByOrganisationIdOrderByGrantedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<ObConsent> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ObConsent> findByConsentReferenceAndOrganisationId(
            String consentReference, Long organisationId);

    List<ObConsent> findByTppIdAndOrganisationIdOrderByGrantedAtDesc(
            Long tppId, Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndTppIdAndStatus(
            Long organisationId, Long tppId, String status);

    /** Consents revoked since a cutoff — replaces a paged in-memory scan in the
     *  dashboard "revoked last 24h" tile (QUAL-TI-008). */
    long countByOrganisationIdAndStatusAndRevokedAtAfter(
            Long organisationId, String status, LocalDateTime since);

    /** Consent counts grouped by type for a tenant in one query — replaces a
     *  paged in-memory group-by in the dashboard (QUAL-TI-008). Returns
     *  {@code [consentType, count]} rows (a null type groups under a null key,
     *  matching the prior {@code merge(null, ...)} behaviour). */
    @Query("""
        SELECT c.consentType, COUNT(c) FROM ObConsent c
        WHERE c.organisationId = :orgId
        GROUP BY c.consentType
        """)
    List<Object[]> countByConsentTypeGrouped(@Param("orgId") Long orgId);
}
