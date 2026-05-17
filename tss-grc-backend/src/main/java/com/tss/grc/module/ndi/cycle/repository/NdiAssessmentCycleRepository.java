package com.tss.grc.module.ndi.cycle.repository;

import com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiAssessmentCycleRepository extends JpaRepository<NdiAssessmentCycle, Long> {

    Page<NdiAssessmentCycle> findByOrganisationIdOrderByReportingYearDescIdDesc(Long org, Pageable p);

    Page<NdiAssessmentCycle> findByOrganisationIdAndStatusOrderByReportingYearDescIdDesc(
        Long org, String status, Pageable p);

    Optional<NdiAssessmentCycle> findByIdAndOrganisationId(Long id, Long org);

    /** All cycles for the year — service uses this to enforce "one active per year". */
    List<NdiAssessmentCycle> findByOrganisationIdAndReportingYear(Long org, Short year);
}
