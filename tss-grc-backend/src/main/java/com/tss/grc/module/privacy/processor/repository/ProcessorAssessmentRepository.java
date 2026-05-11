package com.tss.grc.module.privacy.processor.repository;

import com.tss.grc.module.privacy.processor.entity.ProcessorAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessorAssessmentRepository extends JpaRepository<ProcessorAssessment, Long> {
    Page<ProcessorAssessment> findByOrganisationIdOrderByCreatedAtDesc(Long org, Pageable p);
    Page<ProcessorAssessment> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long org, String status, Pageable p);
    Optional<ProcessorAssessment> findByIdAndOrganisationId(Long id, Long org);
}
