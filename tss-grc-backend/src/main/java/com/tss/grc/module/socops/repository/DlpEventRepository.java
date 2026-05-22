package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.DlpEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DlpEventRepository extends JpaRepository<DlpEvent, Long> {
    Optional<DlpEvent> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<DlpEvent> findByOrganisationIdAndSourceSystemAndExternalId(
        Long organisationId, String sourceSystem, String externalId);
    Page<DlpEvent> findByOrganisationIdOrderByEventTimeDesc(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndReviewed(Long organisationId, Boolean reviewed);
    long countByOrganisationIdAndActionTaken(Long organisationId, String actionTaken);
    long countByOrganisationIdAndDataClassification(Long organisationId, String dataClassification);
}
