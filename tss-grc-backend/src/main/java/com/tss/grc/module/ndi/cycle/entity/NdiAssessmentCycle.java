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

    /**
     * F2-46 — derived from this cycle's {@code ndi.compliance_assessment} rows, never posted.
     *
     * <p>Nothing in the codebase set this column. It was written only by whatever arrived in a
     * request body, because {@code NdiAssessmentCycleService.create()} saves the bound entity
     * ({@code update()} uses a field whitelist and never copied it, so create was the only way in).
     * Meanwhile four consumers read it: the NDI dashboard, the governance-domain rollup, the NDI
     * report, and {@code NdiSubmissionPackageService} — <b>the artefact submitted to the
     * regulator</b>.
     *
     * <p>Measured on the dev tenant before this fix: cycle 2 asserted <b>68.00</b> while its seven
     * child assessments averaged <b>66.43</b>. The figure submitted upward disagreed with the
     * assessment it claimed to summarise, and nothing reconciled the two. (The children are seeded
     * values that never passed through {@code scoreFor} either, so the derivation added here is
     * sound going forward while today's inputs are themselves asserted — the defect fixed is that
     * the roll-up had no derivation at all.)
     *
     * <p>{@code READ_ONLY} because a compliance percentage a caller can type is not a measurement.
     * Still serialised — every consumer above reads it, and hiding it would be the F2-22a trap.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "compliance_score",              precision = 5, scale = 2) private BigDecimal complianceScore;

    /**
     * {@code COMPUTED} — derived from the assessment rows by
     * {@code NdiComplianceAssessmentService.recomputeCycleComplianceScore}. {@code ASSERTED_LEGACY}
     * — a pre-V323 value of unknown origin, kept rather than restated: a migration is the wrong
     * place to change a governance figure that may already have been reported. {@code NULL} — never
     * scored, which stays distinguishable from "computed as zero". Same stance as V315/V316/V319/V322.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "score_source", length = 20) private String scoreSource;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "score_computed_at") private LocalDateTime scoreComputedAt;
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
