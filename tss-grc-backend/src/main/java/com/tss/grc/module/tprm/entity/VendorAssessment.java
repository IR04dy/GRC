package com.tss.grc.module.tprm.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "tprm", name = "vendor_assessments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class VendorAssessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    // PERF-PG-002: denormalised tenant anchor (backfilled from the parent vendor by
    // V230) so /assessments can be scoped + paged directly, without first scanning
    // every vendor and building an unbounded findByVendorIdIn(...) IN-list.
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "assessor", length = 255)
    private String assessor;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "risk_rating", nullable = false, length = 20)
    private String riskRating = "MEDIUM";

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PLANNED";

    @Column(name = "findings", columnDefinition = "NVARCHAR(MAX)")
    private String findings;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_by")
    private Long deletedBy;

    // ─── V81: questionnaire engine fields ──────────────────────────
    // F2-22b(iv): all five are owned by QuestionnaireService and by nothing else — it sets
    // them on rows it loaded itself (assign-template, upsert-response, complete, review),
    // never from a @RequestBody. They are READ_ONLY for binding and unchanged on the wire,
    // because the assessment register and the SAMA outsourcing pack display every one of
    // them. Before this, POST /api/v1/tprm/assessments saved the request body wholesale, so
    // a caller could mint an assessment claiming a REVIEWED 40/40 questionnaire scoring 5%
    // with zero rows in tprm.questionnaire_responses — live-proven, then closed here.

    /** NOT_STARTED → IN_PROGRESS → COMPLETED → REVIEWED. Advanced only by the engine. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "workflow_status", nullable = false, length = 20)
    private String workflowStatus = "NOT_STARTED";

    /** Set by assign-template; it is also the marker that the engine owns this row's score. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "template_id")
    private Long templateId;

    /** Number of questions answered (service-set on every response save). */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "answered_count", nullable = false)
    private Integer answeredCount = 0;

    /** Total mandatory questions on the template (service-set when template assigned). */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions = 0;

    /** Final 0..100 score from the rubric — replaces free-form risk_score for V81-driven flows. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "computed_score_pct", precision = 5, scale = 2)
    private BigDecimal computedScorePct;
}
