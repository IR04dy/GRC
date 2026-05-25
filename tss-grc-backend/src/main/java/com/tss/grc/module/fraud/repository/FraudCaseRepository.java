package com.tss.grc.module.fraud.repository;

import com.tss.grc.module.fraud.entity.FraudCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FraudCaseRepository extends JpaRepository<FraudCase, Long> {

    Page<FraudCase> findByOrganisationIdOrderByDetectedAtDesc(Long organisationId, Pageable pageable);

    Optional<FraudCase> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<FraudCase> findByExternalReferenceAndOrganisationId(String externalReference, Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    @Query("""
        SELECT COUNT(c) FROM FraudCase c
        WHERE c.organisationId = :orgId
          AND c.status IN ('DETECTED','TRIAGE','INVESTIGATING')
        """)
    long countOpenByOrg(@Param("orgId") Long organisationId);

    /** For dashboard — losses on confirmed/closed cases YTD. */
    @Query("""
        SELECT COALESCE(SUM(c.confirmedLossSar), 0) FROM FraudCase c
        WHERE c.organisationId = :orgId
          AND c.status IN ('CONFIRMED','CLOSED')
          AND c.outcomeSetAt >= :sinceUtc
        """)
    java.math.BigDecimal sumConfirmedLossSince(@Param("orgId") Long orgId,
                                                @Param("sinceUtc") java.time.LocalDateTime sinceUtc);

    /** Used by externalReference generator. */
    @Query("""
        SELECT COUNT(c) FROM FraudCase c
        WHERE c.organisationId = :orgId
          AND c.detectedAt >= :monthStart
          AND c.detectedAt <  :monthEnd
        """)
    long countCasesInMonth(@Param("orgId") Long orgId,
                          @Param("monthStart") java.time.LocalDateTime monthStart,
                          @Param("monthEnd") java.time.LocalDateTime monthEnd);

    List<FraudCase> findTop10ByOrganisationIdOrderByDetectedAtDesc(Long organisationId);
}
