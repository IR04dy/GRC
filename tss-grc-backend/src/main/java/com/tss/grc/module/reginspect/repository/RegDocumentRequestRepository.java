package com.tss.grc.module.reginspect.repository;

import com.tss.grc.module.reginspect.entity.RegDocumentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegDocumentRequestRepository
        extends JpaRepository<RegDocumentRequest, Long> {

    List<RegDocumentRequest> findByInspectionIdAndOrganisationIdOrderByRequestedAtDesc(
            Long inspectionId, Long organisationId);

    Optional<RegDocumentRequest> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByInspectionIdAndOrganisationIdAndStatus(
            Long inspectionId, Long organisationId, String status);

    /**
     * Find document requests still open whose due date is before {@code today}.
     * Used by the F8 scheduler to fire {@code REG_DOCUMENT_REQUEST_OVERDUE}.
     */
    List<RegDocumentRequest> findByOrganisationIdAndStatusInAndDueDateBefore(
            Long organisationId, java.util.Collection<String> statuses, LocalDate today);
}
