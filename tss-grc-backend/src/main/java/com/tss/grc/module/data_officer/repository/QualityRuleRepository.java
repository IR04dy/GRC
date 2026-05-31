package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.QualityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("doQualityRuleRepository")
public interface QualityRuleRepository extends JpaRepository<QualityRule, Long> {
    Optional<QualityRule> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<QualityRule> findByOrganisationIdAndId(Long organisationId, Long id);
    List<QualityRule> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    List<QualityRule> findByOrganisationIdAndCatalogEntryId(Long organisationId, Long catalogEntryId);
}
