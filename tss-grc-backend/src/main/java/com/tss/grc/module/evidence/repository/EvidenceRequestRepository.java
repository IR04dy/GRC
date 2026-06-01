package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EvidenceRequestRepository extends JpaRepository<EvidenceRequest, Long> {

    Page<EvidenceRequest> findByOrganisationId(Long orgId, Pageable pageable);

    /**
     * Open requests past their due date (across all tenants — the SLA scheduler
     * runs system-wide). Excludes terminal states and already-fulfilled rows.
     */
    @Query("SELECT r FROM EvidenceRequest r WHERE r.dueDate IS NOT NULL " +
           "AND r.dueDate < :today AND r.fulfilledAt IS NULL " +
           "AND r.status NOT IN ('FULFILLED','SATISFIED','CLOSED','CANCELLED')")
    List<EvidenceRequest> findOverdue(@Param("today") LocalDate today);

    @Query("SELECT r FROM EvidenceRequest r WHERE r.organisationId = :orgId " +
           "AND (:status IS NULL OR r.status = :status)")
    Page<EvidenceRequest> findByOrgAndStatus(@Param("orgId") Long orgId,
                                              @Param("status") String status,
                                              Pageable pageable);

    long countByOrganisationIdAndStatus(Long orgId, String status);
}
