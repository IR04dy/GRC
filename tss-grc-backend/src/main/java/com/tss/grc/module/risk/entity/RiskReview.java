package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERM epic E-J — per-event risk review / reassessment log.
 *
 * <p>One row per scheduled (or ad-hoc) review of a risk. Distinct from
 * {@link RiskAttestation} (the lighter periodic owner re-confirmation): a
 * review is a structured re-evaluation that may <em>re-score</em> the risk,
 * so the before/after residual likelihood + impact and the resulting
 * residual-score / risk-level snapshots are captured here for the audit
 * trail — the reassessment stays defensible even if the risk drifts later.
 */
@Entity
@Table(schema = "risk", name = "risk_reviews")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskReview {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt = LocalDateTime.now();

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_name", length = 200)
    private String reviewedByName;

    /** CONFIRMED | RESCORED | ESCALATE | RETIRE_SUGGESTED */
    @Column(nullable = false, length = 30)
    private String decision = "CONFIRMED";

    @Column(name = "residual_likelihood_before")
    private Integer residualLikelihoodBefore;

    @Column(name = "residual_impact_before")
    private Integer residualImpactBefore;

    @Column(name = "residual_likelihood_after")
    private Integer residualLikelihoodAfter;

    @Column(name = "residual_impact_after")
    private Integer residualImpactAfter;

    @Column(name = "residual_score_snapshot")
    private Integer residualScoreSnapshot;

    @Column(name = "risk_level_snapshot", length = 20)
    private String riskLevelSnapshot;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)")
    private String noteEn;

    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)")
    private String noteAr;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
