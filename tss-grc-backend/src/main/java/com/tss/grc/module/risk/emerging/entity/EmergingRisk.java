package com.tss.grc.module.risk.emerging.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Horizon-scanning signal — not yet a registered risk. Once it matures,
 * it gets ESCALATED into {@code risk.risks}. State machine:
 * {@code HORIZON → MONITORING → (ESCALATED | MATURED | DISMISSED)}.
 */
@Entity
@Table(schema = "risk", name = "emerging_risks")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EmergingRisk {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_code", nullable = false, length = 64)
    private String riskCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** REGULATORY | GEOPOLITICAL | TECHNOLOGY | ENVIRONMENTAL | ECONOMIC | SOCIAL | OTHER. */
    @Column(nullable = false, length = 20)
    private String category;

    /** HORIZON_SCAN | INDUSTRY_REPORT | NEWS | REGULATOR | INTERNAL_INTEL | OTHER. */
    @Column(nullable = false, length = 20)
    private String source = "HORIZON_SCAN";

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_citation", length = 500)
    private String sourceCitation;

    /** NEAR_TERM_6M | MID_TERM_12M | LONG_TERM_24M. */
    @Column(nullable = false, length = 20)
    private String horizon;

    /** SLOW | MEDIUM | FAST. */
    @Column(nullable = false, length = 10)
    private String velocity = "MEDIUM";

    /** WEAK | MODERATE | STRONG | CONFIRMED. */
    @Column(name = "signal_strength", nullable = false, length = 15)
    private String signalStrength = "WEAK";

    @Column(name = "estimated_likelihood")
    private Integer estimatedLikelihood;

    @Column(name = "estimated_impact")
    private Integer estimatedImpact;

    @Column(name = "mitigation_thoughts_en", columnDefinition = "NVARCHAR(MAX)")
    private String mitigationThoughtsEn;

    @Column(name = "mitigation_thoughts_ar", columnDefinition = "NVARCHAR(MAX)")
    private String mitigationThoughtsAr;

    @Column(name = "linked_risk_id")
    private Long linkedRiskId;

    @Column(name = "linked_risk_code", length = 64)
    private String linkedRiskCode;

    /** HORIZON | MONITORING | ESCALATED | MATURED | DISMISSED. */
    @Column(nullable = false, length = 20)
    private String status = "HORIZON";

    @Column(name = "first_observed_at", nullable = false)
    private LocalDateTime firstObservedAt = LocalDateTime.now();

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Column(name = "last_reviewed_by")
    private Long lastReviewedBy;

    @Column(name = "last_review_notes", columnDefinition = "NVARCHAR(MAX)")
    private String lastReviewNotes;

    @Column(name = "next_review_due")
    private LocalDate nextReviewDue;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalated_by")
    private Long escalatedBy;

    @Column(name = "matured_at")
    private LocalDateTime maturedAt;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "dismiss_reason", length = 500)
    private String dismissReason;

    @Column(length = 255)
    private String owner;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String tags;

    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "is_deleted", nullable = false)
    private Short isDeleted = 0;

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
