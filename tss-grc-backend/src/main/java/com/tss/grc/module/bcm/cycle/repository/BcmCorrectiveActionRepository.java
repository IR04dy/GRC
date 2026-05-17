package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmCorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BcmCorrectiveActionRepository extends JpaRepository<BcmCorrectiveAction, Long> {
    List<BcmCorrectiveAction> findByOrganisationIdOrderByIdDesc(Long organisationId);
    List<BcmCorrectiveAction> findByOrganisationIdAndStatusOrderByIdDesc(Long organisationId, String status);
    List<BcmCorrectiveAction> findByOrganisationIdAndGapIdOrderByIdDesc(Long organisationId, Long gapId);
    Optional<BcmCorrectiveAction> findByIdAndOrganisationId(Long id, Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    List<BcmCorrectiveAction> findByOrganisationIdAndDueDateBeforeAndStatusNotIn(
        Long organisationId, LocalDate cutoff, List<String> excludedStatuses);
}
