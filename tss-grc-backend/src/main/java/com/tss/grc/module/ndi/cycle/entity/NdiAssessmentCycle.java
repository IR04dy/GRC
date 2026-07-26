package com.tss.grc.module.ndi.cycle.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SDAIA National Data Index (NDI / نضيء) Assessment Cycle — slice 1.
 *
 * <p>Foundation entity for the SDAIA-NDI module. Every other NDI entity
 * in slices 2-14 references {@code cycle_id}.
 *
 * <p>Status machine — 13 forward-only states (plus ARCHIVED terminal):
 * <pre>
 *   DRAFT → PLANNING → DATA_COLLECTION → ASSESSMENT_IN_PROGRESS
 *         → EVIDENCE_REVIEW → GAP_REMEDIATION
 *         → READY_FOR_DATA_OFFICE_REVIEW → UNDER_DATA_OFFICE_REVIEW
 *         → READY_FOR_EXECUTIVE_APPROVAL → APPROVED → SUBMITTED → CLOSED
 *   any → ARCHIVED (terminal)
 * </pre>
 *
 * <p>Score columns are denormalised summaries. Recompute from child
 * tables (slices 3-5) after each transition.
 */
@Entity
@Table(schema = "ndi", name = "assessment_cycle")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiAssessmentCycle {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;
    @Column(name = "cycle_code",  length = 40)          private String cycleCode;

    @Column(name = "cycle_name_en", nullable = false, length = 255) private String cycleNameEn;
    @Column(name = "cycle_name_ar", length = 255)                   private String cycleNameAr;

    @Column(name = "reporting_year", nullable = false) private Short reportingYear;
    @Column(name = "period_start") private LocalDate periodStart;
    @Column(name = "period_end")   private LocalDate periodEnd;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String scope;
    @Column(name = "entity_name_en", length = 255) private String entityNameEn;
    @Column(name = "entity_name_ar", length = 255) private String entityNameAr;

    @Column(name = "data_office_owner_name",  length = 255) private String dataOfficeOwnerName;
    @Column(name = "data_office_owner_email", length = 255) private String dataOfficeOwnerEmail;
    @Column(name = "assessment_manager_name",  length = 255) private String assessmentManagerName;
    @Column(name = "assessment_manager_email", length = 255) private String assessmentManagerEmail;

    @Column(nullable = false, length = 40) private String status = "DRAFT";

    @Column(name = "maturity_score",                precision = 5, scale = 2) private BigDecimal maturityScore;
    @Column(name = "compliance_score",              precision = 5, scale = 2) private BigDecimal complianceScore;
    @Column(name = "operational_excellence_score",  precision = 5, scale = 2) private BigDecimal operationalExcellenceScore;
    @Column(name = "overall_score",                 precision = 5, scale = 2) private BigDecimal overallScore;
    @Column(name = "evidence_completion_percent",   precision = 5, scale = 2) private BigDecimal evidenceCompletionPercent;

    @Column(name = "gap_count")          private Integer gapCount;
    @Column(name = "critical_gap_count") private Integer criticalGapCount;

    @Column(name = "submission_readiness_status", length = 20)
    private String submissionReadinessStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_by", length = 255) private String approvedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at")  private LocalDateTime approvedAt;
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @Column(name = "closed_at")    private LocalDateTime closedAt;

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
