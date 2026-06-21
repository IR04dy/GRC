package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.SbomComponent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SbomComponentRepository extends JpaRepository<SbomComponent, Long> {

    Optional<SbomComponent> findByIdAndOrganisationId(Long id, Long organisationId);

    Page<SbomComponent> findByOrganisationIdOrderByApplicationNameAscComponentNameAsc(
        Long organisationId, Pageable pageable);

    long countByOrganisationIdAndHasKnownCves(Long organisationId, Boolean hasKnownCves);

    long countByOrganisationId(Long organisationId);
}
