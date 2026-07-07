package com.tss.grc.module.workflow.requests.repository;

import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequest, Long> {

    Optional<WorkflowRequest> findByIdAndOrganisationId(Long id, Long organisationId);

    Page<WorkflowRequest> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    Page<WorkflowRequest> findByOrganisationIdAndStatusOrderByCreatedAtDesc(
        Long organisationId, String status, Pageable pageable);

    /** Adoption — engine requests raised from one module (its live in-flight list). */
    Page<WorkflowRequest> findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(
        Long organisationId, String sourceModule, Pageable pageable);

    Page<WorkflowRequest> findByOrganisationIdAndSourceModuleAndStatusOrderByCreatedAtDesc(
        Long organisationId, String sourceModule, String status, Pageable pageable);

    Page<WorkflowRequest> findByOrganisationIdAndInitiatedByOrderByCreatedAtDesc(
        Long organisationId, Long initiatedBy, Pageable pageable);

    /** Requests anchored to one source aggregate (e.g. a control, a risk, a BIA). */
    List<WorkflowRequest> findByOrganisationIdAndSourceRecordTypeAndSourceRecordIdOrderByCreatedAtDesc(
        Long organisationId, String sourceRecordType, Long sourceRecordId);

    /** WF-2 sweep — open requests past their due date (cross-tenant; scheduler runs system-wide). */
    List<WorkflowRequest> findByStatusInAndDueDateBefore(Collection<String> statuses, LocalDate date);

    /** WF-2 sweep — open requests coming due inside the reminder window. */
    List<WorkflowRequest> findByStatusInAndDueDateBetween(
        Collection<String> statuses, LocalDate from, LocalDate to);
}
