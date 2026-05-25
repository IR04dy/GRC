package com.tss.grc.module.reginspect.repository;

import com.tss.grc.module.reginspect.entity.RegInspection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegInspectionRepository extends JpaRepository<RegInspection, Long> {

    Page<RegInspection> findByOrganisationIdOrderByCreatedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<RegInspection> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<RegInspection> findByExternalReferenceAndOrganisationId(
            String externalReference, Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatusIn(
            Long organisationId, java.util.Collection<String> statuses);
}
