package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    List<RiskAssessment> findByRiskIdOrderByAssessmentDateDesc(Long riskId);
}
