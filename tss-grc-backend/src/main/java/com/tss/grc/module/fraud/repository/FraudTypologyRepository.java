package com.tss.grc.module.fraud.repository;

import com.tss.grc.module.fraud.entity.FraudTypology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FraudTypologyRepository extends JpaRepository<FraudTypology, Long> {

    /**
     * Return typologies visible to the tenant — shared catalog rows
     * ({@code organisation_id IS NULL}) plus any tenant-specific
     * extensions for the supplied org.
     */
    @Query("""
        SELECT t FROM FraudTypology t
        WHERE (t.organisationId IS NULL OR t.organisationId = :orgId)
          AND t.isActive = true
        ORDER BY t.samaCategoryCode ASC, t.code ASC
        """)
    List<FraudTypology> findActiveForOrg(@Param("orgId") Long orgId);

    Optional<FraudTypology> findByCodeAndOrganisationIdIsNull(String code);
}
