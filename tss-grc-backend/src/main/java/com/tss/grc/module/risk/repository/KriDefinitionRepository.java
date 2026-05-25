package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.KriDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Legacy KRI definition repository — assessment §5.1 ERM #5.
 *
 * <p>Named explicitly to avoid a bean-name collision with the Sprint 13 KRI
 * engine repository at {@code com.tss.grc.module.risk.kri.repository.KriDefinitionRepository}
 * (PR #212). Spring Data derives bean names from the simple class name; two
 * repositories named {@code KriDefinitionRepository} in different packages
 * would otherwise collide.
 */
@Repository("legacyKriDefinitionRepository")
public interface KriDefinitionRepository extends JpaRepository<KriDefinition, Long> {
    Page<KriDefinition> findByOrganisationIdOrderByCodeAsc(Long organisationId, Pageable pageable);
    long countByOrganisationId(Long organisationId);
    java.util.List<KriDefinition> findByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);
    long countByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);
}
