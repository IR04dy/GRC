package com.tss.grc.module.rrp.repository;

import com.tss.grc.module.rrp.entity.RrpIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RrpIndicatorRepository extends JpaRepository<RrpIndicator, Long> {

    List<RrpIndicator> findByCycleIdAndOrganisationIdOrderByIndicatorCodeAsc(
            Long cycleId, Long organisationId);

    Optional<RrpIndicator> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByCycleIdAndOrganisationIdAndCurrentBand(
            Long cycleId, Long organisationId, String currentBand);

    long countByCycleIdAndOrganisationId(Long cycleId, Long organisationId);
}
