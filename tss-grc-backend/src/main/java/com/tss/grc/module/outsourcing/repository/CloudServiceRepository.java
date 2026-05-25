package com.tss.grc.module.outsourcing.repository;

import com.tss.grc.module.outsourcing.entity.CloudService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CloudServiceRepository extends JpaRepository<CloudService, Long> {

    List<CloudService> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    List<CloudService> findByOrganisationIdAndArrangementId(Long organisationId, Long arrangementId);

    Optional<CloudService> findByOrganisationIdAndReference(Long organisationId, String reference);

    Optional<CloudService> findByOrganisationIdAndId(Long organisationId, Long id);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndDataClassification(Long organisationId, String dataClassification);
}
