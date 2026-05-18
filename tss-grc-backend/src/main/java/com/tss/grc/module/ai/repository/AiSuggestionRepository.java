package com.tss.grc.module.ai.repository;

import com.tss.grc.module.ai.entity.AiSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {
    List<AiSuggestion> findByOrganisationIdAndFeatureCodeOrderByCreatedAtDesc(
        Long organisationId, String featureCode);

    List<AiSuggestion> findByOrganisationIdAndRelatedEntityAndRelatedEntityIdOrderByCreatedAtDesc(
        Long organisationId, String relatedEntity, Long relatedEntityId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
