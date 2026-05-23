package com.tss.grc.module.risk.bowtie.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Header row of a bow-tie analysis — one row per top event.
 *
 * <p>Children (threats, consequences, preventive + mitigating controls)
 * are loaded explicitly through their repositories rather than via JPA
 * cascade to keep transaction boundaries predictable.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT ──submit()──▶ IN_REVIEW ──approve()──▶ APPROVED
 *                                 ──reject──▶ DRAFT     (out of scope here)
 *   APPROVED ──supersede──▶ SUPERSEDED       (out of scope here)
 *   any state ──archive──▶ ARCHIVED
 * </pre>
 */
@Entity
@Table(schema = "risk", name = "bowtie_analysis")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BowTieAnalysis extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "bowtie_code", nullable = false, length = 80)
    private String bowtieCode;

    @Column(name = "linked_risk_id")
    private Long linkedRiskId;

    @Column(name = "top_event_title_en", nullable = false, length = 500)
    private String topEventTitleEn;

    @Column(name = "top_event_title_ar", length = 500)
    private String topEventTitleAr;

    @Column(name = "top_event_description_en", columnDefinition = "NVARCHAR(MAX)")
    private String topEventDescriptionEn;

    @Column(name = "top_event_description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String topEventDescriptionAr;

    /** OPERATIONAL/CYBER/REGULATORY/STRATEGIC/REPUTATIONAL/FINANCIAL/THIRD_PARTY/CLIMATE. */
    @Column(name = "risk_category", length = 80)
    private String riskCategory;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate = LocalDate.now();

    @Column(name = "assessed_by_name", length = 255)
    private String assessedByName;

    @Column(name = "methodology", nullable = false, length = 60)
    private String methodology = "ISO_31010";

    /** CRITICAL/HIGH/MEDIUM/LOW — derived from worst residual L × worst residual S. */
    @Column(name = "overall_residual_rating", length = 20)
    private String overallResidualRating;

    /** 0–100 weighted score across all controls (50% preventive + 50% mitigating). */
    @Column(name = "overall_control_effectiveness", precision = 5, scale = 2)
    private BigDecimal overallControlEffectiveness;

    /** DRAFT/IN_REVIEW/APPROVED/SUPERSEDED/ARCHIVED. */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by_name", length = 255)
    private String approvedByName;

    @Column(name = "next_review_at")
    private LocalDate nextReviewAt;
}
