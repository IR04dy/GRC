package com.tss.grc.module.ai.lifecycle.repository;

import com.tss.grc.module.ai.lifecycle.entity.ModelValidation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelValidationRepository extends JpaRepository<ModelValidation, Long> {
    List<ModelValidation> findByOrganisationIdAndModelIdOrderByCreatedAtDesc(Long org, Long modelId);
    List<ModelValidation> findByOrganisationIdOrderByCreatedAtDesc(Long org);
    Optional<ModelValidation> findByOrganisationIdAndId(Long org, Long id);
    Optional<ModelValidation> findByOrganisationIdAndReference(Long org, String reference);
    long countByOrganisationIdAndStatus(Long org, String status);
}
