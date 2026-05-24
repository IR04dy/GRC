package com.tss.grc.module.workflow.approval_chains.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Sprint 13 — One rung of an {@link ApprovalChainTemplate}.
 *
 * <p>Steps are ordered by {@code stepOrder}. When multiple rows share
 * the same {@code stepOrder} and the step type is {@code PARALLEL},
 * all mandatory rows must complete before the chain advances.
 *
 * <p>Matching is flexible — exactly one of {@code requiredRole},
 * {@code requiredUserId}, or {@code requiredRoleInChain} should be
 * populated. The service validates at template-write time.
 */
@Entity
@Table(schema = "workflow", name = "approval_chain_step")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ApprovalChainStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    /** SERIAL | PARALLEL */
    @Column(name = "step_type", nullable = false, length = 20)
    private String stepType = "SERIAL";

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 500)
    private String titleAr;

    @Column(name = "required_role", length = 40)
    private String requiredRole;

    @Column(name = "required_user_id")
    private Long requiredUserId;

    @Column(name = "required_role_in_chain", length = 40)
    private String requiredRoleInChain;

    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory = true;

    @Column(name = "can_delegate", nullable = false)
    private Boolean canDelegate = true;

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "escalation_role", length = 40)
    private String escalationRole;

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
