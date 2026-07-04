package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberActionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CyberActionPlanRepository extends JpaRepository<CyberActionPlan, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<CyberActionPlan> findByIdAndOrganisationId(Long id, Long orgId);

    Page<CyberActionPlan> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberActionPlan> findAllByOrganisationId(Long orgId);

    Page<CyberActionPlan> findByOrganisationIdAndWorkflowStatus(Long orgId, String workflowStatus, Pageable pageable);

    // CY-E SLA sweep (system-wide; @Where already filters is_deleted=0) ─────────

    /** Overdue + not-yet-escalated open plans — the escalation pass targets these. */
    List<CyberActionPlan> findByWorkflowStatusNotInAndEscalatedAtIsNullAndDueDateBefore(
        Collection<String> statuses, LocalDate date);

    /** Open plans whose due date falls in a window — the pre-due reminder pass. */
    List<CyberActionPlan> findByWorkflowStatusNotInAndDueDateBetween(
        Collection<String> statuses, LocalDate start, LocalDate end);
}
