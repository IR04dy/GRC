package com.tss.grc.module.workflow.approval_chains.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Sprint 13 — One actor decision on one rung of an
 * {@link ApprovalChainInstance}.
 *
 * <p>Action values:
 * <ul>
 *   <li>{@code APPROVED}         — actor approved the step</li>
 *   <li>{@code REJECTED}         — actor rejected; instance becomes REJECTED</li>
 *   <li>{@code DELEGATED}        — actor handed to another user
 *       (records {@code delegatedToUserId})</li>
 *   <li>{@code REQUESTED_CHANGES} — soft-block; originator must resubmit</li>
 *   <li>{@code RECUSED}          — actor declined for COI; another must act</li>
 * </ul>
 */
@Entity
@Table(schema = "workflow", name = "approval_chain_action")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ApprovalChainAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_id", nullable = false)
    private Long stepId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt = LocalDateTime.now();

    @Column(name = "comment_en", columnDefinition = "NVARCHAR(MAX)")
    private String commentEn;

    @Column(name = "comment_ar", columnDefinition = "NVARCHAR(MAX)")
    private String commentAr;

    @Column(name = "delegated_to_user_id")
    private Long delegatedToUserId;

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
