package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.EthicsCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EthicsCaseRepository extends JpaRepository<EthicsCase, Long> {
    Page<EthicsCase> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    @Query("""
        SELECT c FROM EthicsCase c
        WHERE c.organisationId = :orgId
          AND (:status   IS NULL OR c.status   = :status)
          AND (:category IS NULL OR c.category = :category)
          AND (:severity IS NULL OR c.severity = :severity)
        ORDER BY c.createdAt DESC
        """)
    Page<EthicsCase> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("status") String status,
        @Param("category") String category,
        @Param("severity") String severity,
        Pageable pageable
    );

    long countByOrganisationId(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndCategory(Long organisationId, String category);
}
