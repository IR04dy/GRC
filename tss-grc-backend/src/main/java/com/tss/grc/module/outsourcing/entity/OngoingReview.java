package com.tss.grc.module.outsourcing.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Periodic SAMA §6 oversight review for a single outsourcing arrangement. */
@Entity
@Table(schema = "outsourcing", name = "ongoing_review")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OngoingReview extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "arrangement_id", nullable = false)
    private Long arrangementId;

    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    @Column(name = "review_period_label", length = 32)
    private String reviewPeriodLabel;

    /** SCHEDULED/IN_PROGRESS/COMPLETED/WITH_FINDINGS/ESCALATED. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "SCHEDULED";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** GREEN/AMBER/RED summary outcome. */
    @Column(name = "outcome_rag", length = 8)
    private String outcomeRag;

    @Column(name = "findings_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String findingsSummaryEn;

    @Column(name = "findings_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String findingsSummaryAr;

    @Column(name = "linked_capa_id")
    private Long linkedCapaId;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;
}
