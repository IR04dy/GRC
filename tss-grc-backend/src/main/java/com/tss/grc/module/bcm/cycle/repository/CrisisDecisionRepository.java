package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.CrisisDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrisisDecisionRepository extends JpaRepository<CrisisDecision, Long> {

    List<CrisisDecision> findByOrganisationIdAndCrisisEventIdOrderByDecidedAtAsc(
        Long organisationId, Long crisisEventId);

    long countByOrganisationIdAndCrisisEventId(Long organisationId, Long crisisEventId);
}
