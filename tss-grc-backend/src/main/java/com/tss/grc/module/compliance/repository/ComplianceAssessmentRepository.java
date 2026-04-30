package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.ComplianceAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplianceAssessmentRepository extends JpaRepository<ComplianceAssessment, Long> {

    Page<ComplianceAssessment> findByOrganisationId(Long organisationId, Pageable pageable);

    @Query("SELECT AVG(a.score) FROM ComplianceAssessment a WHERE a.organisationId = :orgId AND a.score IS NOT NULL")
    Double avgScoreByOrg(@Param("orgId") Long orgId);

    @Query("SELECT a.framework, AVG(a.score) FROM ComplianceAssessment a WHERE a.organisationId = :orgId AND a.score IS NOT NULL GROUP BY a.framework")
    List<Object[]> avgScoreByFramework(@Param("orgId") Long orgId);
}
