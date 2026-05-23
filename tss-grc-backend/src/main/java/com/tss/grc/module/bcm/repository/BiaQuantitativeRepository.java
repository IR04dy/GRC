package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.BiaQuantitative;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for quantitative BIA headers (Slice 9-C).
 *
 * <p>Every finder takes {@code organisationId} explicitly — no global
 * Hibernate filter is wired for tenant scope (per CLAUDE.md the
 * platform does this per-query). Soft-deleted rows are already hidden
 * by the entity's {@code @Where(is_deleted = 0)} clause.
 */
public interface BiaQuantitativeRepository extends JpaRepository<BiaQuantitative, Long> {

    List<BiaQuantitative> findByOrganisationIdAndIsDeletedFalseOrderByAssessmentDateDesc(Long organisationId);

    Optional<BiaQuantitative> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    List<BiaQuantitative> findByOrganisationIdAndStatusInAndIsDeletedFalse(Long organisationId, List<String> statuses);

    List<BiaQuantitative> findByOrganisationIdAndProcessIdAndIsDeletedFalse(Long organisationId, Long processId);

    boolean existsByOrganisationIdAndAssessmentCodeAndIsDeletedFalse(Long organisationId, String assessmentCode);
}
