package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmCommunicationContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcmCommunicationContactRepository extends JpaRepository<BcmCommunicationContact, Long> {

    List<BcmCommunicationContact> findByOrganisationIdOrderByEscalationLevelAscNameAsc(Long organisationId);

    List<BcmCommunicationContact> findByOrganisationIdAndStatusOrderByEscalationLevelAscNameAsc(
        Long organisationId, String status);

    List<BcmCommunicationContact> findByOrganisationIdAndRelatedPlanIdOrderByEscalationLevelAsc(
        Long organisationId, Long planId);

    Optional<BcmCommunicationContact> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByOrganisationIdAndLastTestResult(Long organisationId, String lastTestResult);
}
