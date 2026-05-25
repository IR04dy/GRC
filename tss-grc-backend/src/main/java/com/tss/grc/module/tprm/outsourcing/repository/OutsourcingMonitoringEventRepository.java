package com.tss.grc.module.tprm.outsourcing.repository;

import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingMonitoringEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutsourcingMonitoringEventRepository extends JpaRepository<OutsourcingMonitoringEvent, Long> {

    List<OutsourcingMonitoringEvent> findByOrganisationIdAndArrangementIdAndIsDeletedFalseOrderByEventDateDesc(
        Long organisationId, Long arrangementId);

    List<OutsourcingMonitoringEvent> findByOrganisationIdAndIsDeletedFalseOrderByEventDateDesc(Long organisationId);

    List<OutsourcingMonitoringEvent> findByOrganisationIdAndResolvedAndIsDeletedFalseOrderByEventDateDesc(
        Long organisationId, Boolean resolved);

    Optional<OutsourcingMonitoringEvent> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    long countByOrganisationIdAndArrangementIdAndResolvedAndIsDeletedFalse(
        Long organisationId, Long arrangementId, Boolean resolved);

    long countByOrganisationIdAndSeverityInAndResolvedAndIsDeletedFalse(
        Long organisationId, List<String> severities, Boolean resolved);
}
