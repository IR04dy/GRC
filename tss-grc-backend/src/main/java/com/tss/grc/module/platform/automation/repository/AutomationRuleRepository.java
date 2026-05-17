package com.tss.grc.module.platform.automation.repository;

import com.tss.grc.module.platform.automation.entity.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {

    List<AutomationRule> findByOrganisationIdOrderByPriorityAscRuleCodeAsc(Long organisationId);

    List<AutomationRule> findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
        Long organisationId, String triggerEvent);

    Optional<AutomationRule> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<AutomationRule> findByOrganisationIdAndRuleCode(Long organisationId, String ruleCode);
}
