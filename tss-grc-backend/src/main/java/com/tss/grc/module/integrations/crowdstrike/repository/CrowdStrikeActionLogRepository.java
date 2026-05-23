package com.tss.grc.module.integrations.crowdstrike.repository;

import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrowdStrikeActionLogRepository
        extends JpaRepository<CrowdStrikeActionLog, Long> {

    /** Latest action for the {@code /status} endpoint — covers all
     *  action_types so a recent CONTAIN or LIFT both surface there. */
    CrowdStrikeActionLog findFirstByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    /** Browse audit history for a target host. */
    Page<CrowdStrikeActionLog> findByOrganisationIdAndTargetFalconHostIdOrderByCreatedAtDesc(
        Long organisationId, String targetFalconHostId, Pageable pageable);

    Page<CrowdStrikeActionLog> findByOrganisationIdAndActionTypeOrderByCreatedAtDesc(
        Long organisationId, String actionType, Pageable pageable);
}
