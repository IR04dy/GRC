package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BCM compliance mapping — audit Tier-1 #5.
 *
 * <p>One row per (regulatory requirement × BCM workflow item) link with an
 * assessment status + optional evidence + gap pointer. Supports ISO 22301 /
 * SAMA OPRES / DGA / NCEMA / internal / custom frameworks via the
 * {@code sourceFramework} discriminator.
 *
 * <p>Rows marked {@code isPlaceholder=true} until the official control text
 * is imported — matches the platform-wide "PLACEHOLDER until official import"
 * banner pattern.
 */
@Entity
@Table(schema = "bcm", name = "compliance_mappings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmComplianceMapping {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** ISO_22301 | SAMA_OPRES | DGA | NCEMA | INTERNAL | CUSTOM */
    @Column(name = "source_framework", nullable = false, length = 50)
    private String sourceFramework;

    @Column(name = "requirement_code", nullable = false, length = 100)
    private String requirementCode;

    @Column(name = "requirement_title_en", nullable = false, length = 500)
    private String requirementTitleEn;

    @Column(name = "requirement_title_ar", length = 500)
    private String requirementTitleAr;

    @Column(name = "requirement_description", columnDefinition = "NVARCHAR(MAX)")
    private String requirementDescription;

    @Column(length = 100)
    private String domain;

    @Column(name = "is_placeholder", nullable = false)
    private Boolean isPlaceholder = true;

    /** GOVERNANCE | BIA | BCP | DRP | EXERCISE | CRISIS | CALL_TREE | STRATEGY | TRAINING | EVIDENCE | OTHER */
    @Column(name = "related_workflow", length = 50)
    private String relatedWorkflow;

    @Column(name = "related_record_type", length = 100) private String relatedRecordType;
    @Column(name = "related_record_id")                 private Long   relatedRecordId;
    @Column(name = "related_control_id")                private Long   relatedControlId;

    @Column(name = "owner_id")    private Long  ownerId;
    @Column(name = "evidence_id") private Long  evidenceId;

    /** NOT_ASSESSED | COMPLIANT | PARTIAL | NON_COMPLIANT | NOT_APPLICABLE */
    @Column(name = "compliance_status", nullable = false, length = 30)
    private String complianceStatus = "NOT_ASSESSED";

    @Column(name = "assessed_at")               private LocalDateTime assessedAt;
    @Column(name = "assessed_by", length = 255) private String        assessedBy;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "gap_id")
    private Long gapId;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")                    private LocalDateTime updatedAt;
    @Column(name = "created_by")                    private Long createdBy;
    @Column(name = "updated_by")                    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
