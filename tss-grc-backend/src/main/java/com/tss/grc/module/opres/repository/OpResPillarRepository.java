package com.tss.grc.module.opres.repository;

import com.tss.grc.module.opres.entity.OpResPillar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OpResPillarRepository extends JpaRepository<OpResPillar, Long> {

    /**
     * Return pillars visible to the tenant — shared catalog rows
     * ({@code organisation_id IS NULL}) plus any tenant-specific
     * extensions, ordered by {@code sort_order}.
     */
    @Query("""
        SELECT p FROM OpResPillar p
        WHERE (p.organisationId IS NULL OR p.organisationId = :orgId)
          AND p.isActive = true
        ORDER BY p.sortOrder ASC, p.code ASC
        """)
    List<OpResPillar> findActiveForOrg(@Param("orgId") Long orgId);

    Optional<OpResPillar> findByCodeAndOrganisationIdIsNull(String code);
}
