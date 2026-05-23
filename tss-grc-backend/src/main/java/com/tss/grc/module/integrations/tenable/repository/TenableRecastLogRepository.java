package com.tss.grc.module.integrations.tenable.repository;

import com.tss.grc.module.integrations.tenable.entity.TenableRecastLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenableRecastLogRepository
        extends JpaRepository<TenableRecastLog, Long> {

    /** Latest recast/accept/remove for the {@code /status} endpoint — covers
     *  all action_types so any recent decision surfaces there. */
    TenableRecastLog findFirstByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    /** Browse audit history for a specific plugin. */
    Page<TenableRecastLog> findByOrganisationIdAndTargetPluginIdOrderByCreatedAtDesc(
        Long organisationId, String targetPluginId, Pageable pageable);

    Page<TenableRecastLog> findByOrganisationIdAndActionTypeOrderByCreatedAtDesc(
        Long organisationId, String actionType, Pageable pageable);
}
