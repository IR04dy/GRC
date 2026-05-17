package com.tss.grc.module.platform.controls.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Control Assessment — replaces the per-module assessment tables
 * over time. One row per (control, assessment cycle), carrying the
 * effectiveness verdict + evidence pointer + finding / CAPA links
 * if the test fails.
 *
 * <p>Workflow: DRAFT → UNDER_REVIEW → APPROVED | REJECTED.
 */
@Entity
@Table(schema = "platform", name = "control_assessments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ControlAssessment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "control_id", nullable = false)
    private Long controlId;

    @Column(name = "assessment_cycle_id")
    private Long assessmentCycleId;

    /** EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED */
    @Column(nullable = false, length = 30)
    private String effectiveness = "NOT_TESTED";

    @Column(name = "assessor_user_id")
    private Long assessorUserId;

    @Column(name = "assessor_name", length = 120)
    private String assessorName;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate = LocalDate.now();

    @Column(name = "evidence_id")
    private Long evidenceId;

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String notesEn;

    @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String notesAr;

    @Column(name = "finding_id")
    private Long findingId;

    @Column(name = "capa_id")
    private Long capaId;

    /** DRAFT | UNDER_REVIEW | APPROVED | REJECTED */
    @Column(name = "workflow_status", nullable = false, length = 30)
    private String workflowStatus = "DRAFT";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
