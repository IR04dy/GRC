package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CyberIncidentRepository extends JpaRepository<CyberIncident, Long> {

    Page<CyberIncident> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberIncident> findAllByOrganisationId(Long orgId);

    Page<CyberIncident> findByOrganisationIdAndWorkflowStatus(Long orgId, String workflowStatus, Pageable pageable);
}
