package com.tss.grc.module.sharia.repository;

import com.tss.grc.module.sharia.entity.ShariaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShariaEventRepository extends JpaRepository<ShariaEvent, Long> {

    List<ShariaEvent> findByResolutionIdAndOrganisationIdOrderByOccurredAtDesc(
            Long resolutionId, Long organisationId);

    List<ShariaEvent> findByAuditFindingIdAndOrganisationIdOrderByOccurredAtDesc(
            Long auditFindingId, Long organisationId);

    List<ShariaEvent> findByNcEventIdAndOrganisationIdOrderByOccurredAtDesc(
            Long ncEventId, Long organisationId);

    List<ShariaEvent> findByProductApprovalIdAndOrganisationIdOrderByOccurredAtDesc(
            Long productApprovalId, Long organisationId);
}
