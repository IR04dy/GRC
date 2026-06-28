package com.tss.grc.module.compliance.repository;

import org.springframework.stereotype.Repository;
import com.tss.grc.module.compliance.entity.Control;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository("complianceControlRepository")
public interface ControlRepository extends JpaRepository<Control, Long> {

    Page<Control> findByOrganisationId(Long organisationId, Pageable pageable);

    @Query("""
        SELECT c FROM ComplianceControl c
        WHERE c.organisationId = :orgId
          AND (:framework IS NULL OR c.framework = :framework)
          AND (:status    IS NULL OR c.status    = :status)
          AND (:q        IS NULL OR LOWER(c.nameEn) LIKE LOWER(CONCAT('%',:q,'%')))
        """)
    Page<Control> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("framework") String framework,
        @Param("status") String status,
        @Param("q") String q,
        Pageable pageable
    );

    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationId(Long organisationId);

    java.util.List<Control> findByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);

    /** C-12 — batch-resolve free-text control codes (e.g. a policy's
     *  {@code relatedControls} "1-1-1, 2-3-1") to real control rows for
     *  read-side FK linking, without altering the stored free-text. */
    java.util.List<Control> findByOrganisationIdAndCodeIn(
        Long organisationId, java.util.Collection<String> codes);

    /** Count of tenant controls tagged to a framework — replaces a
     *  {@code findByOrganisationIdAndFrameworkId(...).size()} fetch in board-pack
     *  framework coverage (QUAL-BCMP-006). */
    long countByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);
}
