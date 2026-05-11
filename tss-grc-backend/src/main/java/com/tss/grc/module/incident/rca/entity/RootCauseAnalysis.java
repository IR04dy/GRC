package com.tss.grc.module.incident.rca.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Root Cause Analysis — Saudi GRC prompt §3.8 + §7.6 #6.
 *
 * <p>Persists the facilitator's structured RCA against a parent record.
 * Methodology field switches the UI affordance (5-Whys / fishbone /
 * Pareto / free-form). Status lifecycle: DRAFT → IN_REVIEW → APPROVED
 * | REJECTED.
 */
@Entity
@Table(schema = "incident", name = "root_cause_analyses")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RootCauseAnalysis {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "related_entity_type", nullable = false, length = 50)
    private String relatedEntityType;

    @Column(name = "related_entity_id", nullable = false)
    private Long relatedEntityId;

    @Column(name = "related_entity_label", length = 255)
    private String relatedEntityLabel;

    /** FIVE_WHYS | FISHBONE | PARETO | OTHER. */
    @Column(nullable = false, length = 20)
    private String methodology = "FIVE_WHYS";

    @Column(name = "problem_statement_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String problemStatementEn;

    @Column(name = "problem_statement_ar", columnDefinition = "NVARCHAR(MAX)")
    private String problemStatementAr;

    /** JSON: [{question, answer}, ...] when methodology = FIVE_WHYS. */
    @Column(name = "five_whys_json", columnDefinition = "NVARCHAR(MAX)")
    private String fiveWhysJson;

    /** JSON: methodology-specific. See V31 migration comment. */
    @Column(name = "contributing_factors_json", columnDefinition = "NVARCHAR(MAX)")
    private String contributingFactorsJson;

    @Column(name = "root_cause_en", columnDefinition = "NVARCHAR(MAX)")
    private String rootCauseEn;

    @Column(name = "root_cause_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rootCauseAr;

    @Column(name = "recommendations_en", columnDefinition = "NVARCHAR(MAX)")
    private String recommendationsEn;

    @Column(name = "recommendations_ar", columnDefinition = "NVARCHAR(MAX)")
    private String recommendationsAr;

    @Column(length = 255) private String facilitator;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String participants;
    @Column(name = "conducted_at") private LocalDateTime conductedAt;

    /** CSV of CAPA codes (e.g. "CAPA-1024,CAPA-1031"). */
    @Column(name = "capa_refs", columnDefinition = "NVARCHAR(MAX)")
    private String capaRefs;

    /** DRAFT | IN_REVIEW | APPROVED | REJECTED. */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @Column(name = "submitted_by") private Long submittedBy;
    @Column(name = "approved_at") private LocalDateTime approvedAt;
    @Column(name = "approved_by") private Long approvedBy;
    @Column(name = "approver_name", length = 255) private String approverName;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;

    // ─── Audit fields ─────────────────────────────────────────────────
    @Column(name = "external_id", length = 64) private String externalId;
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active", nullable = false) private Short isActive = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
