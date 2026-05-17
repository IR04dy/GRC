package com.tss.grc.module.ndi.audit.repository;

import com.tss.grc.module.ndi.audit.entity.NdiAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NdiAuditEventRepository extends JpaRepository<NdiAuditEvent, Long> {

    Page<NdiAuditEvent> findByOrganisationIdOrderByOccurredAtDesc(Long org, Pageable p);

    Page<NdiAuditEvent> findByOrganisationIdAndCycleIdOrderByOccurredAtDesc(Long org, Long cycleId, Pageable p);

    Page<NdiAuditEvent> findByOrganisationIdAndEntityTypeOrderByOccurredAtDesc(Long org, String entityType, Pageable p);

    Page<NdiAuditEvent> findByOrganisationIdAndActionOrderByOccurredAtDesc(Long org, String action, Pageable p);

    @Query("""
        select e from NdiAuditEvent e
        where e.organisationId = :orgId
          and (:cycleId is null or e.cycleId = :cycleId)
          and (:entityType is null or e.entityType = :entityType)
          and (:action is null or e.action = :action)
          and (:severity is null or e.severity = :severity)
        order by e.occurredAt desc
    """)
    Page<NdiAuditEvent> search(Long orgId, Long cycleId, String entityType,
                               String action, String severity, Pageable p);
}
