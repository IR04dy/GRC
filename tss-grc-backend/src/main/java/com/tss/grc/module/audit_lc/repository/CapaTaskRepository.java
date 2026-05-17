package com.tss.grc.module.audit_lc.repository;

import com.tss.grc.module.audit_lc.entity.CapaTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Bean name qualified ({@code auditLcCapaTaskRepository}) because {@code module.capa} ships another {@code CapaTaskRepository}. */
@Repository("auditLcCapaTaskRepository")
public interface CapaTaskRepository extends JpaRepository<CapaTask, Long> {

    List<CapaTask> findByOrganisationIdOrderByTargetDateAsc(Long org);

    List<CapaTask> findByOrganisationIdAndFindingIdOrderByTargetDateAsc(Long org, String findingId);

    List<CapaTask> findByOrganisationIdAndStatusOrderByTargetDateAsc(Long org, String status);

    Optional<CapaTask> findByOrganisationIdAndExternalId(Long org, String externalId);

    /** Overdue scan — open / in-progress / awaiting-verification CAPAs past target_date. */
    @Query("""
        select c from AuditLcCapaTask c
        where c.organisationId = :orgId
          and c.status in ('OPEN', 'IN_PROGRESS', 'AWAITING_VERIFICATION')
          and c.targetDate < :today
    """)
    List<CapaTask> findOverdue(Long orgId, LocalDate today);
}
