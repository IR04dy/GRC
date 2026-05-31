package com.tss.grc.module.outsourcing.repository;

import com.tss.grc.module.outsourcing.entity.ExitPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("outsourcingCloudExitPlanRepository")
public interface ExitPlanRepository extends JpaRepository<ExitPlan, Long> {

    List<ExitPlan> findByOrganisationIdAndArrangementIdOrderByCreatedAtDesc(
            Long organisationId, Long arrangementId);

    Optional<ExitPlan> findByOrganisationIdAndId(Long organisationId, Long id);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
