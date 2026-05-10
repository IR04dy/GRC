package com.tss.grc.module.workflow.repository;

import com.tss.grc.module.workflow.entity.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {
    List<WorkflowDefinition> findByOrganisationIdAndIsActiveTrue(Long organisationId);
    Optional<WorkflowDefinition> findByOrganisationIdAndCodeAndVersion(Long organisationId, String code, String version);
    List<WorkflowDefinition> findByOrganisationIdAndTargetModule(Long organisationId, String targetModule);
}
