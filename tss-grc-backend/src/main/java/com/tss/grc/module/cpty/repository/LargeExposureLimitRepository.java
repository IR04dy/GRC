package com.tss.grc.module.cpty.repository;

import com.tss.grc.module.cpty.entity.LargeExposureLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LargeExposureLimitRepository
        extends JpaRepository<LargeExposureLimit, Long> {

    List<LargeExposureLimit> findByOrganisationIdOrderByUtilisationPctDesc(
            Long organisationId);

    List<LargeExposureLimit> findByCounterpartyIdAndOrganisationId(
            Long counterpartyId, Long organisationId);

    List<LargeExposureLimit> findByOrganisationIdAndBandIn(
            Long organisationId, java.util.Collection<String> bands);

    Optional<LargeExposureLimit> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByOrganisationIdAndBand(Long organisationId, String band);
}
