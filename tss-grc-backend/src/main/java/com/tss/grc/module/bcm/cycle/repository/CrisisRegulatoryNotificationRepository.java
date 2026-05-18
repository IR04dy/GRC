package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.CrisisRegulatoryNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrisisRegulatoryNotificationRepository extends JpaRepository<CrisisRegulatoryNotification, Long> {

    List<CrisisRegulatoryNotification> findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(
        Long organisationId, Long crisisEventId);

    Optional<CrisisRegulatoryNotification> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByOrganisationIdAndCrisisEventId(Long organisationId, Long crisisEventId);
}
