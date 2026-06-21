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
}
