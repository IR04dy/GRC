package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.RegulatorExamination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RegulatorExaminationRepository extends JpaRepository<RegulatorExamination, Long> {

    Optional<RegulatorExamination> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<RegulatorExamination> findByOrganisationIdAndExaminationCode(Long organisationId, String examinationCode);

    Page<RegulatorExamination> findByOrganisationIdOrderByNotifiedAtDesc(Long organisationId, Pageable pageable);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndRegulator(Long organisationId, String regulator);

    /** Exams whose written-response window is open and whose response_due_at
     *  lands in the {@code [from, to]} band. Used by the scheduled sweep to
     *  fire {@code REGULATOR_EXAM_RESPONSE_DUE} ahead of the deadline. */
    @Query("""
        SELECT e FROM RegulatorExamination e
        WHERE e.organisationId = :organisationId
          AND e.responseDueAt IS NOT NULL
          AND e.responseSubmittedAt IS NULL
          AND e.responseDueAt BETWEEN :from AND :to
        ORDER BY e.responseDueAt ASC
    """)
    List<RegulatorExamination> findResponseDueBetween(Long organisationId, LocalDateTime from, LocalDateTime to);

    /** F2-376 — SOC-ops sweep tenant discovery: a tenant that uses this surface but has
     *  never ingested a SecurityEvent must still be swept for its escalations. */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT e.organisationId FROM RegulatorExamination e")
    java.util.List<Long> findDistinctOrganisationIds();
}
