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

    // F2-343 — "open" is the COMPLEMENT of the machine's sole terminal state (CLOSED), never a
    // second inclusion list. The old list IN ('DETECTED','TRIAGE','INVESTIGATING') both included
    // a dead token ('TRIAGE' is never persisted — triage() sets INVESTIGATING/FALSE_POSITIVE) and
    // dropped the non-terminal outcome states (CONFIRMED/CLEARED/FALSE_POSITIVE, each still
    // awaiting CLOSE), so a confirmed fraud awaiting recovery/SAR read as NOT open — flatteringly
    // under-counting the dashboard headline (F2-196 / F2-290 / F2-341). The excluded token is
    // pinned against the machine's derived terminal set by FraudOpenCountContractTest.
    @Query("""
        SELECT COUNT(c) FROM FraudCase c
        WHERE c.organisationId = :orgId
          AND c.status <> 'CLOSED'
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
