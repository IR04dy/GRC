package com.tss.grc.module.ndi.compliance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * NDI Compliance Assessment — slice 4.
 *
 * <p>One row per (cycle, framework_item) for COMPLIANCE framework
 * items. Has TWO status fields:
 * <ul>
 *   <li>{@code complianceStatus} — the substantive answer:
 *       COMPLIANT / PARTIAL / NON_COMPLIANT / NOT_APPLICABLE /
 *       EVIDENCE_REQUIRED / UNDER_REVIEW</li>
 *   <li>{@code lifecycleStatus} — the workflow position:
 *       DRAFT → IN_PROGRESS → SUBMITTED → UNDER_REVIEW → APPROVED,
 *       with RETURNED looping back to IN_PROGRESS</li>
 * </ul>
 *
 * <p>Service rules:
 * <ul>
 *   <li><b>Rule 3</b> — COMPLIANT/PARTIAL with no evidence auto-flips
 *       to EVIDENCE_REQUIRED on save</li>
 *   <li><b>Rule 4</b> — PARTIAL or NON_COMPLIANT sets {@code needsGap=true}
 *       so the gap workflow (slice 8) can surface it</li>
 *   <li><b>Rule 5</b> — NOT_APPLICABLE requires {@code naJustification}</li>
 * </ul>
 */
@Entity
@Table(schema = "ndi", name = "compliance_assessment")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiComplianceAssessment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "cycle_id", nullable = false)          private Long cycleId;
    @Column(name = "framework_item_id", nullable = false) private Long frameworkItemId;
    @Column(name = "requirement_code", nullable = false, length = 40) private String requirementCode;

    @Column(nullable = false, length = 30) private String component;
    @Column(nullable = false, length = 80) private String domain;
    @Column(length = 80)                   private String subdomain;

    @Column(name = "compliance_status", nullable = false, length = 30)
    private String complianceStatus = "UNDER_REVIEW";
    @Column(name = "compliance_score", precision = 5, scale = 2) private BigDecimal complianceScore;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "response_text_en", columnDefinition = "NVARCHAR(MAX)") private String responseTextEn;
    @Column(name = "response_text_ar", columnDefinition = "NVARCHAR(MAX)") private String responseTextAr;
    @Column(name = "evidence_ref",     length = 255) private String evidenceRef;
    @Column(name = "evidence_required", nullable = false) private Boolean evidenceRequired = false;
    @Column(name = "evidence_status",  length = 30) private String evidenceStatus;

    @Column(name = "needs_gap", nullable = false) private Boolean needsGap = false;
    @Column(name = "gap_id")                  private Long gapId;
    @Column(name = "corrective_action_id")    private Long correctiveActionId;
    @Column(name = "risk_id")                 private Long riskId;

    @Column(name = "na_justification", columnDefinition = "NVARCHAR(MAX)") private String naJustification;

    @Column(name = "control_owner_name",   length = 255) private String controlOwnerName;
    @Column(name = "control_owner_email",  length = 255) private String controlOwnerEmail;
    @Column(name = "evidence_owner_name",  length = 255) private String evidenceOwnerName;
    @Column(name = "evidence_owner_email", length = 255) private String evidenceOwnerEmail;
    @Column(name = "reviewer_name",        length = 255) private String reviewerName;
    @Column(name = "reviewer_email",       length = 255) private String reviewerEmail;
    @Column(name = "approver_name",        length = 255) private String approverName;

    @Column(name = "lifecycle_status", nullable = false, length = 20) private String lifecycleStatus = "DRAFT";
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @Column(name = "reviewed_at")  private LocalDateTime reviewedAt;
    @Column(name = "approved_at")  private LocalDateTime approvedAt;
    @Column(name = "returned_at")  private LocalDateTime returnedAt;
    @Column(name = "return_reason", columnDefinition = "NVARCHAR(MAX)") private String returnReason;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
