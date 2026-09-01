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

    /**
     * F2-420 -- the UNCAPPED counterpart, for figures published over the whole register.
     *
     * <p>{@code SocOpsDashboardService} used the paged overload above with
     * {@code PageRequest.of(0, 200)} and then derived three published figures from that
     * window. The cap's order (notified_at DESC) is orthogonal to what two of them ask --
     * how many written responses fall due in the next fortnight, and which five are due
     * soonest -- so an examination notified long ago with an imminent deadline was dropped
     * from both (F2-406 / F2-415 / F2-197: a capped list is fine to RENDER and wrong to
     * AGGREGATE). One list now feeds every derivation, so the mismatch is unrepresentable.
     *
     * <p>The paged overload stays: {@code RegulatorExaminationService.list} is a genuine
     * paged register read. Both respect the entity's {@code @Where(is_deleted = 0)}, so the
     * two agree on soft-delete scoping.
     */
    List<RegulatorExamination> findByOrganisationIdOrderByNotifiedAtDesc(Long organisationId);

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
