package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Continuity strategy selected to maintain or recover a critical
 * service/process within its approved RTO/RPO.
 * Lifecycle (approval_status): DRAFT → UNDER_REVIEW → APPROVED | REJECTED → ARCHIVED.
 * Lifecycle (implementation_status): NOT_STARTED → IN_PROGRESS → COMPLETED | ON_HOLD.
 */
@Entity
@Table(schema = "bcm", name = "continuity_strategies")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ContinuityStrategy {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(name = "related_service_id")
    private Long relatedServiceId;

    @Column(name = "related_process_id")
    private Long relatedProcessId;

    @Column(name = "related_bia_id")
    private Long relatedBiaId;

    @Column(name = "rto_requirement_hours")
    private Integer rtoRequirementHours;

    @Column(name = "rpo_requirement_hours")
    private Integer rpoRequirementHours;

    /** See V98 migration for the closed enum of strategy types. */
    @Column(name = "selected_strategy", nullable = false, length = 60)
    private String selectedStrategy;

    @Column(name = "strategy_description_en", columnDefinition = "NVARCHAR(MAX)")
    private String strategyDescriptionEn;

    @Column(name = "strategy_description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String strategyDescriptionAr;

    @Column(name = "cost_estimate", precision = 15, scale = 2)
    private BigDecimal costEstimate;

    /** HIGH | MEDIUM | LOW */
    @Column(length = 20)
    private String feasibility;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(name = "residual_risk", length = 20)
    private String residualRisk;

    @Column(name = "required_resources", columnDefinition = "NVARCHAR(MAX)")
    private String requiredResources;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String dependencies;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "evidence_id")
    private Long evidenceId;

    /** DRAFT | UNDER_REVIEW | APPROVED | REJECTED | ARCHIVED */
    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus = "DRAFT";

    /** NOT_STARTED | IN_PROGRESS | COMPLETED | ON_HOLD */
    @Column(name = "implementation_status", length = 30)
    private String implementationStatus;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
