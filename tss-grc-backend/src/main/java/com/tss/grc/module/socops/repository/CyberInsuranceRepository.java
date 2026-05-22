package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.CyberInsurance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CyberInsuranceRepository extends JpaRepository<CyberInsurance, Long> {
    Optional<CyberInsurance> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<CyberInsurance> findByOrganisationIdAndPolicyNumber(Long organisationId, String policyNumber);
    List<CyberInsurance> findByOrganisationIdOrderByEffectiveFromDesc(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);

    /** Policies up for renewal within the window — drives the F8 renewal
     *  questionnaire reminder. */
    @Query("""
        SELECT p FROM CyberInsurance p
        WHERE p.organisationId = :organisationId
          AND p.renewalDueAt IS NOT NULL
          AND p.renewalDueAt BETWEEN :from AND :to
          AND p.status = 'ACTIVE'
        ORDER BY p.renewalDueAt ASC
    """)
    List<CyberInsurance> findRenewalsDueBetween(Long organisationId, LocalDate from, LocalDate to);
}
