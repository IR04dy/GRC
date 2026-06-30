package com.tss.grc.module.risk_lc.repository;

import com.tss.grc.module.risk_lc.entity.TreatmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Treatment-plan repository — every finder is org-scoped per the multi-tenant
 * pattern documented in CLAUDE.md.
 *
 * <p>PERF-NQ-007: the three read finders LEFT JOIN FETCH the (now-LAZY) actions
 * collection in a single query, eliminating the per-plan N+1 the EAGER mapping
 * caused on list views. SELECT DISTINCT collapses the duplicate parent rows a
 * one-to-many bag fetch produces, so the Optional finder stays single-valued and
 * the lists don't repeat a plan once per action.
 */
public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, Long> {

    @Query("SELECT DISTINCT p FROM TreatmentPlan p LEFT JOIN FETCH p.actions "
         + "WHERE p.organisationId = :orgId ORDER BY p.createdAt DESC")
    List<TreatmentPlan> findByOrganisationIdOrderByCreatedAtDesc(@Param("orgId") Long organisationId);

    @Query("SELECT DISTINCT p FROM TreatmentPlan p LEFT JOIN FETCH p.actions "
         + "WHERE p.organisationId = :orgId AND p.riskId = :riskId ORDER BY p.createdAt DESC")
    List<TreatmentPlan> findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(
            @Param("orgId") Long organisationId, @Param("riskId") Long riskId);

    @Query("SELECT DISTINCT p FROM TreatmentPlan p LEFT JOIN FETCH p.actions "
         + "WHERE p.organisationId = :orgId AND p.externalId = :externalId")
    Optional<TreatmentPlan> findByOrganisationIdAndExternalId(
            @Param("orgId") Long organisationId, @Param("externalId") String externalId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    /** E-D mandatory-treatment gate: does this risk carry a live, non-rejected
     *  treatment plan? (@Where filters out soft-deleted rows.) */
    boolean existsByOrganisationIdAndRiskIdAndStatusNot(Long organisationId, Long riskId, String status);

    /** PERF-NQ-007: count for the next-external-id generator — replaces loading
     *  every plan (and, when actions was EAGER, every plan's actions) just to size(). */
    long countByOrganisationId(Long organisationId);
}
