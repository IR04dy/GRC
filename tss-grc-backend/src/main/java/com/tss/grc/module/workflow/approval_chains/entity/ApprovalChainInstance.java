package com.tss.grc.module.workflow.approval_chains.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — A live in-flight approval ladder.
 *
 * <p>Materialised at start time from an {@link ApprovalChainTemplate}.
 * Carries the originator plus a pointer to the gated subject — either
 * a {@code WorkflowSubmission} ID or an arbitrary
 * {@code (sourceEntityType, sourceEntityId)} pair. The service
 * enforces that at least one source identifier is set on start.
 *
 * <h3>State machine</h3>
 * <pre>
 *   PENDING     → IN_PROGRESS | WITHDRAWN | EXPIRED
 *   IN_PROGRESS → APPROVED | REJECTED | WITHDRAWN | EXPIRED
 *   APPROVED   (terminal)
 *   REJECTED   (terminal)
 *   WITHDRAWN  (terminal)
 *   EXPIRED    (terminal)
 * </pre>
 */
@Entity
@Table(schema = "workflow", name = "approval_chain_instance")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ApprovalChainInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "source_workflow_submission_id")
    private Long sourceWorkflowSubmissionId;

    @Column(name = "source_entity_type", length = 40)
    private String sourceEntityType;

    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    /** PENDING | IN_PROGRESS | APPROVED | REJECTED | WITHDRAWN | EXPIRED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "current_step_order", nullable = false)
    private Integer currentStepOrder = 1;

    @Column(name = "originator_user_id", nullable = false)
    private Long originatorUserId;

    @Column(name = "title_en", length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
