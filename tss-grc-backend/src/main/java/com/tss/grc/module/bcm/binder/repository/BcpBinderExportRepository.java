package com.tss.grc.module.bcm.binder.repository;

import com.tss.grc.module.bcm.binder.entity.BcpBinderExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link BcpBinderExport} (Slice 10-F).
 *
 * <p>Every finder takes {@code organisationId} explicitly — the platform
 * does not run a global Hibernate tenant filter (per CLAUDE.md). Soft-
 * deleted rows are already hidden by the entity's
 * {@code @Where(is_deleted = 0)} clause.
 */
public interface BcpBinderExportRepository extends JpaRepository<BcpBinderExport, Long> {

    Optional<BcpBinderExport> findByOrganisationIdAndId(Long organisationId, Long id);

    Optional<BcpBinderExport> findByOrganisationIdAndExternalId(Long organisationId, UUID externalId);

    /** Newest-first list of binder versions for a single plan — drives the UI versions tab. */
    List<BcpBinderExport> findByOrganisationIdAndBcpPlanIdOrderByGeneratedAtDesc(
        Long organisationId, Long bcpPlanId);

    /** Org-wide list of binders by status — admin / triage views. */
    List<BcpBinderExport> findByOrganisationIdAndStatusOrderByGeneratedAtDesc(
        Long organisationId, String status);

    boolean existsByOrganisationIdAndExportCode(Long organisationId, String exportCode);
}
