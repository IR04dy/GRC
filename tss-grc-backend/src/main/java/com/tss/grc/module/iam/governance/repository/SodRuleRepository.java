package com.tss.grc.module.iam.governance.repository;

import com.tss.grc.module.iam.governance.entity.SodRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** RBAC Access-Governance P2 — SoD rule finders (global pack = organisation_id NULL). */
public interface SodRuleRepository extends JpaRepository<SodRule, Long> {

    @Query("SELECT r FROM SodRule r WHERE (r.organisationId IS NULL OR r.organisationId = :org) ORDER BY r.ruleCode")
    List<SodRule> findAllForOrg(@Param("org") Long organisationId);

    @Query("SELECT r FROM SodRule r WHERE (r.organisationId IS NULL OR r.organisationId = :org) AND r.isActive = true ORDER BY r.ruleCode")
    List<SodRule> findActiveForOrg(@Param("org") Long organisationId);
}
