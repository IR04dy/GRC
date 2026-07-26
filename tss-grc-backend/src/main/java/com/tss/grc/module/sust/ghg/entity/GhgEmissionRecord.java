package com.tss.grc.module.sust.ghg.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GHG emission inventory record (V56 — slice 2 of the Sustainability
 * module). One row per (reporting-year × scope × source).
 *
 * <p>Status machine: {@code DRAFT → SUBMITTED → VERIFIED → APPROVED |
 * ARCHIVED}. Service-layer rules:
 * <ul>
 *   <li>Rule 2 — verifying ({@code POST /:id/verify}) requires
 *       {@code evidenceRef} to be set.</li>
 * </ul>
 * The Scope-3 partial-disclosure rule (Rule 1) is computed by the
 * aggregate endpoint, not by an entity column.
 */
@Entity
@Table(schema = "sust", name = "ghg_emission_record")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class GhgEmissionRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id",     length = 64)     private String externalId;
    @Column(name = "record_code",     length = 40)     private String recordCode;

    @Column(name = "reporting_year", nullable = false) private Short reportingYear;
    @Column(nullable = false)                          private Short scope;
    @Column(nullable = false, length = 60)             private String category;
    /** V295 — GHG Protocol Scope-3 category 1–15; NULL for S1/S2 (and uncategorised S3). */
    @Column(name = "scope3_category")                  private Short scope3Category;

    @Column(name = "source_name_en", nullable = false, length = 255) private String sourceNameEn;
    @Column(name = "source_name_ar", length = 255)                   private String sourceNameAr;

    @Column(name = "business_unit", length = 120) private String businessUnit;
    @Column(length = 120)                          private String facility;

    @Column(name = "activity_data",        precision = 20, scale = 4) private BigDecimal activityData;
    @Column(name = "activity_unit",        length = 40)              private String activityUnit;
    @Column(name = "emission_factor",      precision = 20, scale = 6) private BigDecimal emissionFactor;
    @Column(name = "emission_factor_unit", length = 40)              private String emissionFactorUnit;
    /** S-J — optional FK to a versioned library factor (sust.emission_factor); when set the calculator pulls value+unit from it. */
    @Column(name = "emission_factor_id") private Long emissionFactorId;

    @Column(name = "emission_value_tco2e", nullable = false, precision = 18, scale = 2)
    private BigDecimal emissionValueTco2e;

    /** S-J — COMPUTED (derived from activity × factor) vs ASSERTED (caller-supplied total). */
    @Column(name = "value_source", nullable = false, length = 20) private String valueSource = "ASSERTED";

    @Column(name = "coverage_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal coveragePercent = new BigDecimal("100.00");

    @Column(nullable = false)                                  private Boolean verified = false;
    @Column(name = "verification_provider", length = 120)      private String verificationProvider;
    @Column(name = "verification_date")                        private LocalDate verificationDate;

    @Column(length = 120)                                       private String methodology;
    @Column(columnDefinition = "NVARCHAR(MAX)")                 private String notes;

    @Column(name = "evidence_ref", length = 255) private String evidenceRef;
    /** S-C — real FK into the generic evidence engine (evidence.evidence_items). */
    @Column(name = "evidence_item_id") private Long evidenceItemId;
    @Column(name = "owner_name",   length = 255) private String ownerName;
    @Column(name = "owner_email",  length = 255) private String ownerEmail;

    @Column(nullable = false, length = 20) private String status = "DRAFT";
    @Column(name = "submitted_at")          private LocalDateTime submittedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at")           private LocalDateTime approvedAt;

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
