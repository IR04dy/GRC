package com.tss.grc.module.workflow.repository;

import com.tss.grc.module.workflow.entity.WorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowStateRepository extends JpaRepository<WorkflowState, Long> {
    List<WorkflowState> findByDefinitionIdOrderBySortOrder(Long definitionId);
    Optional<WorkflowState> findByDefinitionIdAndCode(Long definitionId, String code);
    Optional<WorkflowState> findByDefinitionIdAndIsInitialTrue(Long definitionId);
}
