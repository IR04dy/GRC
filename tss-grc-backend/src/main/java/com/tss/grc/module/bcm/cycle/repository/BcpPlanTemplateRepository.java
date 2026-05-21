package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcpPlanTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcpPlanTemplateRepository extends JpaRepository<BcpPlanTemplate, Long> {
    List<BcpPlanTemplate> findByPlanTypeOrderByTemplateNameEnAsc(String planType);
    List<BcpPlanTemplate> findByScenarioCategoryOrderByTemplateNameEnAsc(String category);
    Optional<BcpPlanTemplate> findByTemplateCode(String code);
}
