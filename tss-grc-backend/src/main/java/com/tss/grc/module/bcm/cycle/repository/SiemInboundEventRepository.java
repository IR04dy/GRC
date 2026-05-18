package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.SiemInboundEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiemInboundEventRepository extends JpaRepository<SiemInboundEvent, Long> {
    List<SiemInboundEvent> findByOrganisationIdOrderByReceivedAtDesc(Long organisationId);
    List<SiemInboundEvent> findByOrganisationIdAndProcessingStatusOrderByReceivedAtDesc(
        Long organisationId, String status);
    long countByOrganisationIdAndProcessingStatus(Long organisationId, String status);
}
