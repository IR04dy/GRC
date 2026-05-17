package com.tss.grc.module.scm.fourthparty.repository;

import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FourthPartyExposureRepository extends JpaRepository<FourthPartyExposure, Long> {

    Page<FourthPartyExposure> findByOrganisationIdOrderByConcentrationRiskDescDownstreamCriticalityDesc(
            Long organisationId, Pageable pageable);

    Optional<FourthPartyExposure> findByIdAndOrganisationId(Long id, Long organisationId);

    Page<FourthPartyExposure> findByOrganisationIdAndStatusOrderByCreatedAtDesc(
            Long organisationId, String status, Pageable pageable);

    /**
     * Group rows by fourth-party name and count how many primaries each one
     * sits behind. The service uses this to elevate {@code concentration_risk}
     * to HIGH whenever the same name appears ≥3 times.
     */
    @Query("""
            SELECT f.fourthPartyName, COUNT(f) FROM FourthPartyExposure f
            WHERE f.organisationId = :orgId
            GROUP BY f.fourthPartyName
            HAVING COUNT(f) >= 2
            """)
    List<Object[]> concentrationCounts(Long orgId);
}
