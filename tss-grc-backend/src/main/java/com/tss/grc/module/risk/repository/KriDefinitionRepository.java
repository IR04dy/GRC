package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.KriDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KriDefinitionRepository extends JpaRepository<KriDefinition, Long> {
    Page<KriDefinition> findByOrganisationIdOrderByCodeAsc(Long organisationId, Pageable pageable);
    long countByOrganisationId(Long organisationId);
    java.util.List<KriDefinition> findByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);
    long countByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);
}
