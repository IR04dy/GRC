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

    /**
     * §5.4 #6 — narrower variant that pushes the at-risk filter into SQL
     * so we don't pull every cross-border RoPA row into JVM memory only
     * to discard the well-documented ones. Returns rows where:
     *   - adequacy_decision IS NULL (no decision recorded), OR
     *   - adequacy_decision IN ('NONE','PARTIAL') AND
     *     transfer_mechanism IS NULL or blank.
     * The service still builds the human-readable {@code riskNote} field
     * because that depends on which condition matched.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT p FROM ProcessingActivity p
         WHERE p.organisationId = :orgId
           AND p.crossBorderTransfer = true
           AND (
                p.adequacyDecision IS NULL
             OR (p.adequacyDecision IN ('NONE','PARTIAL')
                 AND (p.transferMechanism IS NULL OR LENGTH(TRIM(p.transferMechanism)) = 0))
           )
        ORDER BY p.id
        """)
    List<ProcessingActivity> findAtRiskCrossBorder(
        @org.springframework.data.repository.query.Param("orgId") Long orgId);
}
