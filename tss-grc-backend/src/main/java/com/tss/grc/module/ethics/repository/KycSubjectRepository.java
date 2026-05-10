package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.KycSubject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KycSubjectRepository extends JpaRepository<KycSubject, Long> {
    Page<KycSubject> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    @Query("""
        SELECT s FROM KycSubject s
        WHERE s.organisationId = :orgId
          AND (:type   IS NULL OR s.subjectType = :type)
          AND (:status IS NULL OR s.status      = :status)
          AND (:risk   IS NULL OR s.riskRating  = :risk)
        ORDER BY s.createdAt DESC
        """)
    Page<KycSubject> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("type") String type,
        @Param("status") String status,
        @Param("risk") String risk,
        Pageable pageable
    );

    long countByOrganisationId(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndRiskRating(Long organisationId, String riskRating);
    long countByOrganisationIdAndIsPep(Long organisationId, Boolean isPep);
    long countByOrganisationIdAndIsSanctioned(Long organisationId, Boolean isSanctioned);
}
