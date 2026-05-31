package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.DsrRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository("doDsrRequestRepository")
public interface DsrRepository extends JpaRepository<DsrRequest, Long> {
    Optional<DsrRequest> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<DsrRequest> findByOrganisationIdAndId(Long organisationId, Long id);
    List<DsrRequest> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    @Query("SELECT d FROM DoDsrRequest d WHERE d.organisationId = :o AND d.status NOT IN ('FULFILLED','REJECTED','CLOSED','PARTIAL') AND d.slaDeadline < :now")
    List<DsrRequest> findOverdue(Long o, LocalDateTime now);
}
