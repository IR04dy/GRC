package com.tss.grc.module.audit_lc.ia_plan.repository;

import com.tss.grc.module.audit_lc.ia_plan.entity.IaAuditUniverseEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped repository for {@link IaAuditUniverseEntry}. */
public interface IaAuditUniverseEntryRepository extends JpaRepository<IaAuditUniverseEntry, Long> {

    List<IaAuditUniverseEntry> findByOrganisationIdAndIsDeletedFalseOrderByEntryCodeAsc(Long organisationId);

    List<IaAuditUniverseEntry> findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEntryCodeAsc(
        Long organisationId, Long planId);

    /** Master universe — rows with NULL plan_id. */
    List<IaAuditUniverseEntry> findByOrganisationIdAndPlanIdIsNullAndIsDeletedFalseOrderByEntryCodeAsc(
        Long organisationId);

    Optional<IaAuditUniverseEntry> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<IaAuditUniverseEntry> findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(
        Long organisationId, String entryCode, Long planId);

    /** Used by bulk import — duplicate detection inside the master library. */
    Optional<IaAuditUniverseEntry> findByOrganisationIdAndEntryCodeAndPlanIdIsNullAndIsDeletedFalse(
        Long organisationId, String entryCode);

    long countByOrganisationIdAndPlanIdAndIsDeletedFalse(Long organisationId, Long planId);
}
