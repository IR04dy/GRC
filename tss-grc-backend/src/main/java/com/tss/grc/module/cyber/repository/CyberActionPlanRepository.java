package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberActionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CyberActionPlanRepository extends JpaRepository<CyberActionPlan, Long> {

    Page<CyberActionPlan> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberActionPlan> findAllByOrganisationId(Long orgId);

    Page<CyberActionPlan> findByOrganisationIdAndWorkflowStatus(Long orgId, String workflowStatus, Pageable pageable);
}
