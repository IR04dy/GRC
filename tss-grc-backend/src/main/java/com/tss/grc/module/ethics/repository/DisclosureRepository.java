package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.Disclosure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisclosureRepository extends JpaRepository<Disclosure, Long> {
    Page<Disclosure> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    @Query("""
        SELECT d FROM Disclosure d
        WHERE d.organisationId = :orgId
          AND (:type    IS NULL OR d.disclosureType = :type)
          AND (:status  IS NULL OR d.status         = :status)
        ORDER BY d.createdAt DESC
        """)
    Page<Disclosure> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("type")   String type,
        @Param("status") String status,
        Pageable pageable
    );

    long countByOrganisationId(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndDisclosureType(Long organisationId, String type);
}
