package com.tss.grc.module.scm.clauses.repository;

import com.tss.grc.module.scm.clauses.entity.ContractClause;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractClauseRepository extends JpaRepository<ContractClause, Long> {
    Page<ContractClause> findByOrganisationIdOrderByCategoryAscClauseCodeAsc(Long organisationId, Pageable pageable);
    Page<ContractClause> findByOrganisationIdAndStatusOrderByCategoryAscClauseCodeAsc(Long organisationId, String status, Pageable pageable);
    Page<ContractClause> findByOrganisationIdAndCategoryOrderByClauseCodeAsc(Long organisationId, String category, Pageable pageable);
    Optional<ContractClause> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<ContractClause> findByOrganisationIdAndClauseCode(Long organisationId, String clauseCode);
}
