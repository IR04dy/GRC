package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmGap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BcmGapRepository extends JpaRepository<BcmGap, Long> {
    List<BcmGap> findByOrganisationIdOrderByIdDesc(Long organisationId);
    List<BcmGap> findByOrganisationIdAndStatusOrderByIdDesc(Long organisationId, String status);
    List<BcmGap> findByOrganisationIdAndSeverityOrderByIdDesc(Long organisationId, String severity);
    List<BcmGap> findByOrganisationIdAndCycleIdOrderByIdDesc(Long organisationId, Long cycleId);
    Optional<BcmGap> findByIdAndOrganisationId(Long id, Long organisationId);
    /** For automation dedup — gaps already linked to a given source record. */
    List<BcmGap> findByOrganisationIdAndRelatedRecordTypeAndRelatedRecordId(
        Long organisationId, String relatedRecordType, Long relatedRecordId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
    /** F2-336 — "open gaps" is the COMPLEMENT of the resolved set, never a single token. */
    long countByOrganisationIdAndStatusNotIn(Long organisationId, Collection<String> excludedStatuses);
    List<BcmGap> findByOrganisationIdAndDueDateBeforeAndStatusNotIn(
        Long organisationId, LocalDate cutoff, List<String> excludedStatuses);
}
