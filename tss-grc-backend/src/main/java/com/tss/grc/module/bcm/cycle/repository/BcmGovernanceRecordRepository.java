package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmGovernanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BcmGovernanceRecordRepository extends JpaRepository<BcmGovernanceRecord, Long> {

    List<BcmGovernanceRecord> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    Optional<BcmGovernanceRecord> findByIdAndOrganisationId(Long id, Long organisationId);

    List<BcmGovernanceRecord> findByOrganisationIdAndApprovalStatusOrderByCreatedAtDesc(
        Long organisationId, String approvalStatus);

    /** Used by the periodic-review scheduler to surface stale governance baselines. */
    List<BcmGovernanceRecord> findByOrganisationIdAndApprovalStatusInAndNextReviewDateBefore(
        Long organisationId, List<String> approvalStatuses, LocalDate today);
}
