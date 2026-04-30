package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.ControlAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ControlAssessmentRepository extends JpaRepository<ControlAssessment, Long> {

    Page<ControlAssessment> findByOrganisationId(Long orgId, Pageable pageable);

    List<ControlAssessment> findAllByOrganisationId(Long orgId);

    Page<ControlAssessment> findByOrganisationIdAndControlId(Long orgId, Long controlId, Pageable pageable);
}
