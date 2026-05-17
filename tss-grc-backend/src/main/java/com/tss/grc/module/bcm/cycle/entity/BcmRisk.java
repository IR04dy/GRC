package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BCM risk register row — threats to services / processes /
 * facilities / people / technology / suppliers / data. Sits
 * alongside the ERM risk register and links to it via
 * {@code related_erm_risk_id}.
 */
@Entity
@Table(schema = "bcm", name = "bcm_risks")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmRisk {

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

    @Column(name = "related_asset_id")
    private Long relatedAssetId;

    @Column(name = "related_supplier_id")
    private Long relatedSupplierId;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String cause;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String event;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String consequence;

    /** See V98 migration for the closed enum of threat categories. */
    @Column(name = "threat_category", nullable = false, length = 60)
    private String threatCategory;

    @Column(name = "existing_controls", columnDefinition = "NVARCHAR(MAX)")
    private String existingControls;

    @Column
    private Integer likelihood;       // 1..5 (CHECK in DB)

    @Column
    private Integer impact;           // 1..5 (CHECK in DB)

    @Column(name = "inherent_risk_score")
    private Integer inherentRiskScore;

    /** EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED */
    @Column(name = "control_effectiveness", length = 30)
    private String controlEffectiveness;

    @Column(name = "residual_risk_score")
    private Integer residualRiskScore;

    @Column(name = "risk_owner_id")
    private Long riskOwnerId;

    @Column(name = "treatment_plan", columnDefinition = "NVARCHAR(MAX)")
    private String treatmentPlan;

    @Column(name = "related_bia_id")
    private Long relatedBiaId;

    @Column(name = "related_bcp_id")
    private Long relatedBcpId;

    @Column(name = "related_drp_id")
    private Long relatedDrpId;

    @Column(name = "related_erm_risk_id")
    private Long relatedErmRiskId;

    /** DRAFT | ASSESSED | TREATMENT_PLANNED | ACCEPTED | MITIGATED | CLOSED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
