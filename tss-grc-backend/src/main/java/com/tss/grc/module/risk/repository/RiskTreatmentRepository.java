package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskTreatment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskTreatmentRepository extends JpaRepository<RiskTreatment, Long> {
    List<RiskTreatment> findByRiskIdOrderByCreatedAtDesc(Long riskId);
}
