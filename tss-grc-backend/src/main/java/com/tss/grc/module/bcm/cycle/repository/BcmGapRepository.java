package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmGap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BcmGapRepository extends JpaRepository<BcmGap, Long> {
    List<BcmGap> findByOrganisationIdOrderByIdDesc(Long organisationId);
    List<BcmGap> findByOrganisationIdAndStatusOrderByIdDesc(Long organisationId, String status);
    List<BcmGap> findByOrganisationIdAndSeverityOrderByIdDesc(Long organisationId, String severity);
    List<BcmGap> findByOrganisationIdAndCycleIdOrderByIdDesc(Long organisationId, Long cycleId);
    Optional<BcmGap> findByIdAndOrganisationId(Long id, Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
    List<BcmGap> findByOrganisationIdAndDueDateBeforeAndStatusNotIn(
        Long organisationId, LocalDate cutoff, List<String> excludedStatuses);
}
