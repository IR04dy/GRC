package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.Risk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RiskRepository extends JpaRepository<Risk, Long> {
    Page<Risk> findByOrganisationId(Long organisationId, Pageable pageable);

    @Query("SELECT r FROM Risk r WHERE r.organisationId = :orgId AND (:status IS NULL OR r.status = :status)")
    Page<Risk> findByOrgAndStatus(@Param("orgId") Long orgId, @Param("status") String status, Pageable pageable);

    long countByOrganisationIdAndRiskLevel(Long organisationId, String riskLevel);
    long countByOrganisationIdAndStatus(Long organisationId, String status);

    @Query("SELECT r FROM Risk r WHERE r.organisationId = :orgId ORDER BY r.createdAt DESC")
    List<Risk> findTop5ByOrganisationIdOrderByCreatedAtDesc(@Param("orgId") Long orgId, Pageable pageable);

    @Query("SELECT r.inherentLikelihood, r.inherentImpact, COUNT(r) FROM Risk r WHERE r.organisationId = :orgId AND r.inherentLikelihood IS NOT NULL GROUP BY r.inherentLikelihood, r.inherentImpact")
    List<Object[]> countByHeatmapCell(@Param("orgId") Long orgId);

    @Query("SELECT rc.nameEn, COUNT(r) FROM Risk r JOIN RiskCategory rc ON r.categoryId = rc.id WHERE r.organisationId = :orgId GROUP BY rc.nameEn ORDER BY COUNT(r) DESC")
    List<Object[]> countByCategory(@Param("orgId") Long orgId);
}
