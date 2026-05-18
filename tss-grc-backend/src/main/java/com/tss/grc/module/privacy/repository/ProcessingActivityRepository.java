package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.ProcessingActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessingActivityRepository extends JpaRepository<ProcessingActivity, Long> {
    Page<ProcessingActivity> findByOrganisationId(Long organisationId, Pageable pageable);

    /** §5.4 #6: rows flagged as cross-border for the at-risk-transfers report. */
    List<ProcessingActivity> findByOrganisationIdAndCrossBorderTransferTrue(Long organisationId);

    /** §5.4 #7: rows tagged with special-category data. */
    List<ProcessingActivity> findByOrganisationIdAndSpecialCategoryFlagTrue(Long organisationId);
}
