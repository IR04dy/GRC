package com.tss.grc.module.sharia.repository;

import com.tss.grc.module.sharia.entity.ShariaResolution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShariaResolutionRepository extends JpaRepository<ShariaResolution, Long> {

    Page<ShariaResolution> findByOrganisationIdOrderByReferenceDesc(
            Long organisationId, Pageable pageable);

    Optional<ShariaResolution> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ShariaResolution> findByReferenceAndOrganisationId(
            String reference, Long organisationId);

    List<ShariaResolution> findByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatusIn(Long organisationId, java.util.Collection<String> statuses);
}
