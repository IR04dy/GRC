package com.tss.grc.module.sust.tcfd.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * TCFD alignment recommendation (V61 — slice 7).
 *
 * <p>Mirrors the V60 CMA shape — same 6-status set + Rule 5 (gap
 * required for Partial/Not Disclosed) + Rule 6 (no evidence →
 * EVIDENCE_REQUIRED) — plus a {@code relatedClimateRiskId} pointer for
 * TCFD → climate-risk-register linkage.
 *
 * <p>Activates Rule 7: when a STRATEGY-pillar resilience recommendation
 * (recommendation_code = 'c') is PARTIAL or NOT_DISCLOSED, the
 * aggregate endpoint surfaces {@code strategyResilienceGap=true} so
 * the dashboard can offer a climate-risk gap creation.
 */
@Entity
@Table(schema = "sust", name = "tcfd_item")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class TcfdItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "reporting_year", nullable = false) private Short reportingYear;
    @Column(nullable = false, length = 20)             private String pillar;
    @Column(name = "recommendation_code", nullable = false, length = 5) private String recommendationCode;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "recommendation_en", columnDefinition = "NVARCHAR(MAX)") private String recommendationEn;
    @Column(name = "recommendation_ar", columnDefinition = "NVARCHAR(MAX)") private String recommendationAr;

    @Column(name = "disclosure_status", nullable = false, length = 30)
    private String disclosureStatus = "UNDER_REVIEW";

    @Column(name = "evidence_ref",   length = 255) private String evidenceRef;
    /** S-C — real FK into evidence.evidence_items. */
    @Column(name = "evidence_item_id") private Long evidenceItemId;
    @Column(name = "evidence_owner", length = 255) private String evidenceOwner;

    @Column(name = "gap_description_en", columnDefinition = "NVARCHAR(MAX)") private String gapDescriptionEn;
    @Column(name = "gap_description_ar", columnDefinition = "NVARCHAR(MAX)") private String gapDescriptionAr;

    @Column(name = "related_climate_risk_id") private Long relatedClimateRiskId;
    @Column(name = "related_metric_id")       private Long relatedMetricId;
    @Column(name = "linked_capa_id")          private Long linkedCapaId;

    @Column(name = "owner_name",     length = 255) private String ownerName;
    @Column(name = "owner_email",    length = 255) private String ownerEmail;
    @Column(name = "reviewer_name",  length = 255) private String reviewerName;
    @Column(name = "last_reviewed_at")              private LocalDateTime lastReviewedAt;
    @Column(name = "approval_status", length = 30)  private String approvalStatus;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_by",    length = 255)  private String approvedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at")                    private LocalDateTime approvedAt;

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
