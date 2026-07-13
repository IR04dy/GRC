package com.tss.grc.module.sust.sgi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Saudi Green Initiative commitment (V59 — slice 5 of the Sustainability
 * module). Promotes the 7 canonical SGI commitments from a static
 * frontend constant to a backend-driven entity with progress tracking,
 * owner + cadence, and an auditable lifecycle.
 *
 * <p>Status machine: {@code ACTIVE → ACHIEVED} (target met) or
 * {@code ACTIVE → RETIRED} (commitment dropped/replaced; reason required).
 *
 * <p>Progress tier (ON_TRACK / AT_RISK / DELAYED) is computed by the
 * service from baseline + current + target + years — the entity stores
 * only the raw values so reassessments don't lose history.
 */
@Entity
@Table(schema = "sust", name = "sgi_commitment")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SgiCommitment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;
    @Column(name = "commitment_code", nullable = false, length = 40) private String commitmentCode;

    @Column(nullable = false, length = 30) private String sector;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "baseline_value", precision = 20, scale = 4) private BigDecimal baselineValue;
    @Column(name = "baseline_year")                              private Short baselineYear;
    @Column(name = "current_value", precision = 20, scale = 4)   private BigDecimal currentValue;
    @Column(name = "current_value_as_of")                        private LocalDate currentValueAsOf;
    @Column(name = "target_value", nullable = false, precision = 20, scale = 4) private BigDecimal targetValue;
    @Column(name = "target_year", nullable = false)              private Short targetYear;
    @Column(nullable = false, length = 40)                       private String unit;

    @Column(nullable = false, length = 20) private String status = "ACTIVE";

    @Column(name = "owner_name",  length = 255) private String ownerName;
    @Column(name = "owner_email", length = 255) private String ownerEmail;
    @Column(name = "next_milestone_date")        private LocalDate nextMilestoneDate;
    @Column(name = "next_milestone_en", length = 500) private String nextMilestoneEn;
    @Column(name = "next_milestone_ar", length = 500) private String nextMilestoneAr;

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)") private String notesEn;
    @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)") private String notesAr;

    @Column(name = "evidence_ref", length = 255) private String evidenceRef;
    /** S-C — real FK into evidence.evidence_items. */
    @Column(name = "evidence_item_id") private Long evidenceItemId;

    // ── SGI-C (V296) — current_value source lineage ──────────────────────
    /** MANUAL (default) | ESG_METRIC | GHG_TOTAL — provenance of current_value. */
    @Column(name = "value_source", nullable = false, length = 20) private String valueSource = "MANUAL";
    /** FK-by-convention to sust.esg_metric when {@code value_source = ESG_METRIC}. */
    @Column(name = "source_metric_id") private Long sourceMetricId;
    /** GHG scope 1..3 to narrow a {@code GHG_TOTAL} refresh; null = all scopes. */
    @Column(name = "source_scope") private Short sourceScope;

    @Column(name = "achieved_at")                private LocalDateTime achievedAt;
    @Column(name = "achieved_by", length = 255)  private String achievedBy;
    @Column(name = "retired_at")                 private LocalDateTime retiredAt;
    @Column(name = "retired_by", length = 255)   private String retiredBy;
    @Column(name = "retired_reason", columnDefinition = "NVARCHAR(MAX)") private String retiredReason;

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
