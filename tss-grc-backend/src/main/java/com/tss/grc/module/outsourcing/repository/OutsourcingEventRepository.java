package com.tss.grc.module.outsourcing.repository;

import com.tss.grc.module.outsourcing.entity.OutsourcingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutsourcingEventRepository extends JpaRepository<OutsourcingEvent, Long> {

    List<OutsourcingEvent> findByOrganisationIdAndArrangementIdOrderByOccurredAtDesc(
            Long organisationId, Long arrangementId);

    List<OutsourcingEvent> findByOrganisationIdAndCloudServiceIdOrderByOccurredAtDesc(
            Long organisationId, Long cloudServiceId);
}
