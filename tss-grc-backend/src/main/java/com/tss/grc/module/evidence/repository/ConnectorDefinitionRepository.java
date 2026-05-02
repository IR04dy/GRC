package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.ConnectorDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConnectorDefinitionRepository extends JpaRepository<ConnectorDefinition, Long> {
    List<ConnectorDefinition> findByOrganisationIdOrderByCode(Long organisationId);
}
