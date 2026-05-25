package com.tss.grc.module.fraud.repository;

import com.tss.grc.module.fraud.entity.FraudCaseUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudCaseUpdateRepository extends JpaRepository<FraudCaseUpdate, Long> {

    List<FraudCaseUpdate> findByCaseIdAndOrganisationIdOrderByOccurredAtDesc(Long caseId, Long organisationId);

    long countByCaseIdAndOrganisationId(Long caseId, Long organisationId);
}
