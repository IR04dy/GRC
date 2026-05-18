package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceConnector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceConnectorRepository extends JpaRepository<EvidenceConnector, Long> {
    List<EvidenceConnector> findByOrganisationIdOrderByCategoryAscNameEnAsc(Long organisationId);
    List<EvidenceConnector> findByOrganisationIdAndStatusOrderByCategoryAscNameEnAsc(Long organisationId, String status);
    Optional<EvidenceConnector> findByOrganisationIdAndCode(Long organisationId, String code);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
