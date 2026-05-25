package com.tss.grc.module.ai.lifecycle.repository;

import com.tss.grc.module.ai.lifecycle.entity.RetrainingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RetrainingEventRepository extends JpaRepository<RetrainingEvent, Long> {
    List<RetrainingEvent> findByOrganisationIdAndModelIdOrderByCreatedAtDesc(Long org, Long modelId);
    List<RetrainingEvent> findByOrganisationIdOrderByCreatedAtDesc(Long org);
    Optional<RetrainingEvent> findByOrganisationIdAndId(Long org, Long id);
    Optional<RetrainingEvent> findByOrganisationIdAndReference(Long org, String reference);
    long countByOrganisationIdAndStatus(Long org, String status);
}
