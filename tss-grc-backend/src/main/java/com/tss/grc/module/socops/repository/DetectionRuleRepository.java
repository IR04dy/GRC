package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.DetectionRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetectionRuleRepository extends JpaRepository<DetectionRule, Long> {

    Optional<DetectionRule> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<DetectionRule> findByOrganisationIdAndRuleCode(Long organisationId, String ruleCode);

    Page<DetectionRule> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    /** MITRE-technique filter — matches rows whose comma-separated technique list contains the
     *  query (a bare tactic/technique id like {@code T1566} also matches sub-techniques). */
    Page<DetectionRule> findByOrganisationIdAndMitreTechniquesContainingIgnoreCaseOrderByCreatedAtDesc(
        Long organisationId, String technique, Pageable pageable);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndEnabledTrue(Long organisationId);
}
