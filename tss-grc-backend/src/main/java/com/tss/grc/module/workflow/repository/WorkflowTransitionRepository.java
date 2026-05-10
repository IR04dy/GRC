package com.tss.grc.module.workflow.repository;

import com.tss.grc.module.workflow.entity.WorkflowTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, Long> {
    List<WorkflowTransition> findByDefinitionIdOrderBySortOrder(Long definitionId);
    List<WorkflowTransition> findByDefinitionIdAndFromStateIdOrderBySortOrder(Long definitionId, Long fromStateId);
    Optional<WorkflowTransition> findByDefinitionIdAndFromStateIdAndActionCode(Long definitionId, Long fromStateId, String actionCode);
}
