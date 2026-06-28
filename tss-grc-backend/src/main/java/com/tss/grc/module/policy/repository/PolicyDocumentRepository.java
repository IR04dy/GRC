package com.tss.grc.module.policy.repository;

import com.tss.grc.module.policy.entity.PolicyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, Long> {

    List<PolicyDocument> findByOrganisationIdOrderByPolicyCodeAscVersionDesc(Long org);

    List<PolicyDocument> findByOrganisationIdAndStatusOrderByPolicyCodeAscVersionDesc(Long org, String status);

    List<PolicyDocument> findByOrganisationIdAndCategoryOrderByPolicyCodeAscVersionDesc(Long org, String category);

    Optional<PolicyDocument> findByIdAndOrganisationId(Long id, Long org);

    Optional<PolicyDocument> findByOrganisationIdAndPolicyCodeAndVersion(Long org, String policyCode, String version);

    /** Latest version per policy_code. */
    @Query("""
        select p from PolicyDocument p
        where p.organisationId = :orgId
          and p.version = (
            select max(p2.version) from PolicyDocument p2
            where p2.organisationId = :orgId and p2.policyCode = p.policyCode
          )
        order by p.policyCode asc
    """)
    List<PolicyDocument> findLatestVersionPerCode(Long orgId);

    /** PUBLISHED rows whose expiry has passed. */
    @Query("""
        select p from PolicyDocument p
        where p.organisationId = :orgId
          and p.status = 'PUBLISHED'
          and p.expiryDate is not null
          and p.expiryDate < :today
    """)
    List<PolicyDocument> findExpiredPublished(Long orgId, LocalDate today);

    /** Distinct tenant ids that own at least one policy — drives the scheduler's per-tenant loop. */
    @Query("select distinct p.organisationId from PolicyDocument p")
    List<Long> findDistinctOrganisationIds();

    /** PUBLISHED policies whose next review falls within [from, to] — drives review-due reminders. */
    @Query("""
        select p from PolicyDocument p
        where p.organisationId = :orgId
          and p.status = 'PUBLISHED'
          and p.nextReviewDate is not null
          and p.nextReviewDate >= :from
          and p.nextReviewDate <= :to
    """)
    List<PolicyDocument> findReviewDue(Long orgId, LocalDate from, LocalDate to);
}
