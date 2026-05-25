package com.tss.grc.module.rrp.repository;

import com.tss.grc.module.rrp.entity.RrpRecoveryOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RrpRecoveryOptionRepository extends JpaRepository<RrpRecoveryOption, Long> {

    List<RrpRecoveryOption> findByCycleIdAndOrganisationIdOrderByOptionCodeAsc(
            Long cycleId, Long organisationId);

    Optional<RrpRecoveryOption> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByCycleIdAndOrganisationId(Long cycleId, Long organisationId);
}
