package com.tss.grc.module.openbanking.repository;

import com.tss.grc.module.openbanking.entity.ObEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ObEventRepository extends JpaRepository<ObEvent, Long> {

    List<ObEvent> findByTppIdAndOrganisationIdOrderByOccurredAtDesc(
            Long tppId, Long organisationId);

    List<ObEvent> findByConsentIdAndOrganisationIdOrderByOccurredAtDesc(
            Long consentId, Long organisationId);

    List<ObEvent> findByIncidentIdAndOrganisationIdOrderByOccurredAtDesc(
            Long incidentId, Long organisationId);
}
