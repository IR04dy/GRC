package com.tss.grc.module.workflow.repository;

import com.tss.grc.module.workflow.entity.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
    Optional<WorkflowInstance> findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
        Long organisationId, String targetModule, String targetEntity, Long targetEntityId);
    List<WorkflowInstance> findByOrganisationIdAndDefinitionId(Long organisationId, Long definitionId);
}
