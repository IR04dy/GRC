package com.tss.grc.module.sust.materiality.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ESG material topic (V62 — slice 8). Each topic is scored on two
 * axes — stakeholder importance and business impact — both on a 1-5
 * scale. The service computes materiality_score = (s + b) / 2 and
 * buckets into a priority_tier (CRITICAL / HIGH / MEDIUM / LOW) on
 * every save.
 *
 * <p>Status machine: {@code DRAFT → SCORED → APPROVED → ARCHIVED}.
 *
 * <p>Approved topics anchor downstream linkages — CMA Article 92(a),
 * climate-risk topic registration, and the materiality matrix
 * visualization all pull from APPROVED rows.
 */
@Entity
@Table(schema = "sust", name = "material_topic")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class MaterialTopic {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;
    @Column(name = "topic_code", length = 40)           private String topicCode;

    @Column(name = "reporting_year", nullable = false) private Short reportingYear;
    @Column(nullable = false, length = 20)             private String category;

    @Column(name = "topic_en", nullable = false, length = 255) private String topicEn;
    @Column(name = "topic_ar", length = 255)                   private String topicAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "stakeholder_importance", nullable = false, precision = 3, scale = 1)
    private BigDecimal stakeholderImportance = BigDecimal.ZERO;

    @Column(name = "business_impact", nullable = false, precision = 3, scale = 1)
    private BigDecimal businessImpact = BigDecimal.ZERO;

    // S-Q — CSRD/ESRS double-materiality axes (0..5). impact = inside-out
    // (the org's impact on people/environment); financial = outside-in (the
    // topic's effect on the org's financial position). A topic is material if
    // material from EITHER axis; materialityScore = max(impact, financial).
    @Column(name = "impact_materiality", nullable = false, precision = 3, scale = 1)
    private BigDecimal impactMateriality = BigDecimal.ZERO;

    @Column(name = "financial_materiality", nullable = false, precision = 3, scale = 1)
    private BigDecimal financialMateriality = BigDecimal.ZERO;

    @Column(name = "materiality_score", precision = 3, scale = 1)
    private BigDecimal materialityScore;

    @Column(name = "priority_tier", length = 20)
    private String priorityTier;

    @Column(name = "stakeholder_groups", columnDefinition = "NVARCHAR(MAX)")
    private String stakeholderGroups;

    @Column(nullable = false, length = 20) private String status = "DRAFT";

    @Column(name = "linked_cma_disclosure_id") private Long linkedCmaDisclosureId;
    @Column(name = "linked_climate_risk_id")   private Long linkedClimateRiskId;
    @Column(name = "linked_capa_id")           private Long linkedCapaId;

    @Column(name = "evidence_ref", length = 255) private String evidenceRef;
    @Column(name = "owner_name",   length = 255) private String ownerName;
    @Column(name = "owner_email",  length = 255) private String ownerEmail;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "approved_at")              private LocalDateTime approvedAt;
    @Column(name = "approved_by", length = 255) private String approvedBy;

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
