package com.tss.grc.module.privacy.bridge.repository;

import com.tss.grc.module.privacy.bridge.entity.CyberBreachBridgeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CyberBreachBridgeLogRepository extends JpaRepository<CyberBreachBridgeLog, Long> {
    Page<CyberBreachBridgeLog> findByOrganisationIdOrderByTriggeredAtDesc(Long organisationId, Pageable pageable);
    Page<CyberBreachBridgeLog> findByOrganisationIdAndOutcomeOrderByTriggeredAtDesc(Long organisationId, String outcome, Pageable pageable);
    List<CyberBreachBridgeLog> findByOrganisationIdAndCyberIncidentIdOrderByTriggeredAtDesc(Long organisationId, Long cyberIncidentId);
}
