package com.tss.grc.module.policy.repository;

import com.tss.grc.module.policy.entity.PolicyException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyExceptionRepository extends JpaRepository<PolicyException, Long> {

    List<PolicyException> findByOrganisationIdOrderByRequestedAtDesc(Long org);

    List<PolicyException> findByOrganisationIdAndStatusOrderByRequestedAtDesc(Long org, String status);

    List<PolicyException> findByOrganisationIdAndPolicyIdOrderByRequestedAtDesc(Long org, Long policyId);

    Optional<PolicyException> findByIdAndOrganisationId(Long id, Long org);

    /** APPROVED exceptions whose expiry has passed — feed an EXPIRED sweep job. */
    @Query("""
        select e from PolicyException e
        where e.organisationId = :orgId
          and e.status = 'APPROVED'
          and e.expiryDate is not null
          and e.expiryDate < :today
    """)
    List<PolicyException> findExpiredApproved(Long orgId, LocalDate today);
}
