package com.tss.grc.module.integrations.tenable.repository;

import com.tss.grc.module.integrations.tenable.entity.TenableFindingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenableFindingLogRepository
        extends JpaRepository<TenableFindingLog, Long> {

    /** Fast-path dedupe — returns existing row if the same Tenable
     *  (asset_uuid, plugin_id) was already ingested for this tenant. The
     *  DB-side unique index is the safety net behind this query. */
    Optional<TenableFindingLog> findByOrganisationIdAndTenableAssetUuidAndTenablePluginId(
        Long organisationId, String tenableAssetUuid, String tenablePluginId);

    /** Latest finding ingested for this tenant — drives the /status
     *  endpoint's {@code lastSuccessfulPollAt}. */
    TenableFindingLog findFirstByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    /** Browse history for the integrations dashboard. */
    Page<TenableFindingLog> findByOrganisationIdOrderByCreatedAtDesc(
        Long organisationId, Pageable pageable);

    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
}
