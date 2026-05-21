package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcpPlanReviewAttestation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BcpPlanReviewAttestationRepository extends JpaRepository<BcpPlanReviewAttestation, Long> {

    List<BcpPlanReviewAttestation> findByOrganisationIdAndPlanIdOrderByAttestedAtDesc(
        Long organisationId, Long planId);

    long countByOrganisationIdAndPlanIdAndAttestationOutcome(
        Long organisationId, Long planId, String outcome);
}
