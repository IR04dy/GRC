package com.tss.grc.module.openbanking.repository;

import com.tss.grc.module.openbanking.entity.ObIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObIncidentRepository extends JpaRepository<ObIncident, Long> {

    Page<ObIncident> findByOrganisationIdOrderByDetectedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<ObIncident> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ObIncident> findByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndTppIdAndStatusNotIn(
            Long organisationId, Long tppId, java.util.Collection<String> statuses);

    long countByOrganisationIdAndSeverityAndStatusNotIn(
            Long organisationId, String severity, java.util.Collection<String> statuses);

    /** SAMA-reportable incidents still open (status not in the supplied set) —
     *  replaces a paged in-memory scan in the dashboard (QUAL-TI-008).
     *  {@code samaReportable = true} excludes null/false, matching the prior
     *  {@code Boolean.TRUE.equals(...)} guard. */
    long countByOrganisationIdAndSamaReportableAndStatusNotIn(
            Long organisationId, Boolean samaReportable, java.util.Collection<String> statuses);

    /** F2-337 — the reference minter seeds from the highest existing ordinal for this org+year
     *  (a MAX, never a COUNT: a count re-mints a used ordinal after a gap or beyond the window).
     *  {@code @Where(is_deleted = 0)} scopes this to live rows, matching the filtered unique index
     *  {@code ux_ob_incident_reference}. Reference ordinals are zero-padded, so a lexicographic
     *  DESC is a numeric DESC within the format's domain. */
    Optional<ObIncident> findTopByOrganisationIdAndReferenceStartingWithOrderByReferenceDesc(
            Long organisationId, String referencePrefix);

    /** F2-337 — collision guard for the mint skip-loop; live rows only, so it agrees with the
     *  filtered unique index (a soft-deleted reference is free to be re-used). */
    boolean existsByOrganisationIdAndReference(Long organisationId, String reference);
}
