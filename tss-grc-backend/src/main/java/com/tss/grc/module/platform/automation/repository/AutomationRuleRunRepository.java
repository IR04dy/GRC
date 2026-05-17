package com.tss.grc.module.platform.automation.repository;

import com.tss.grc.module.platform.automation.entity.AutomationRuleRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationRuleRunRepository extends JpaRepository<AutomationRuleRun, Long> {

    Page<AutomationRuleRun> findByOrganisationIdOrderByFiredAtDesc(Long organisationId, Pageable pageable);

    List<AutomationRuleRun> findTop50ByOrganisationIdAndRuleIdOrderByFiredAtDesc(Long organisationId, Long ruleId);

    long countByOrganisationIdAndOutcome(Long organisationId, String outcome);
}
