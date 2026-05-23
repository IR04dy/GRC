package com.tss.grc.module.compliance.repository;

import org.springframework.stereotype.Repository;
import com.tss.grc.module.compliance.entity.RegulatoryChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

@Repository("complianceRegulatoryChangeRepository")
public interface RegulatoryChangeRepository extends JpaRepository<RegulatoryChange, Long> {
    Page<RegulatoryChange> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);

    /**
     * All not-yet-closed changes with a future or near-past effectiveDate —
     * fuels the regulatory horizon scanner (Slice 8-B).
     */
    @Query("""
        SELECT r FROM ComplianceRegulatoryChange r
        WHERE r.organisationId = :org
          AND r.effectiveDate IS NOT NULL
          AND r.effectiveDate >= :from
          AND r.status NOT IN ('CLOSED', 'NOT_APPLICABLE')
        ORDER BY r.effectiveDate ASC
        """)
    List<RegulatoryChange> findHorizon(Long org, LocalDate from);
}
