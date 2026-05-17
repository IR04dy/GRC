package com.tss.grc.module.ndi.framework.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NDI Framework Item — slice 2.
 *
 * <p>Single requirement / question across the three NDI components:
 * {@code COMPLIANCE}, {@code MATURITY}, {@code OPERATIONAL_EXCELLENCE}.
 *
 * <p>Provenance flags are critical here:
 * <ul>
 *   <li>{@code isOfficialImport=true} — landed via the import-csv /
 *       import-json endpoint after SDAIA publishes the official library.</li>
 *   <li>{@code isPlaceholder=true} — interim content. The UI MUST show
 *       a bilingual "Placeholder — requires official SDAIA/NDI control
 *       library import" marker on these rows.</li>
 * </ul>
 * Both flags can be true if an official import was later marked back as
 * placeholder for re-import; service-level helpers keep them consistent.
 *
 * <p>Status machine: {@code DRAFT → ACTIVE → RETIRED} (terminal).
 */
@Entity
@Table(schema = "ndi", name = "framework_item")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiFrameworkItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "framework_version", nullable = false, length = 20) private String frameworkVersion = "v0.1-placeholder";
    @Column(nullable = false, length = 30) private String component;     // COMPLIANCE | MATURITY | OPERATIONAL_EXCELLENCE
    @Column(nullable = false, length = 80) private String domain;
    @Column(length = 80)                   private String subdomain;

    @Column(name = "requirement_code", nullable = false, length = 40) private String requirementCode;
    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;
    @Column(name = "question_en",    columnDefinition = "NVARCHAR(MAX)") private String questionEn;
    @Column(name = "question_ar",    columnDefinition = "NVARCHAR(MAX)") private String questionAr;
    @Column(name = "evidence_guidance_en", columnDefinition = "NVARCHAR(MAX)") private String evidenceGuidanceEn;
    @Column(name = "evidence_guidance_ar", columnDefinition = "NVARCHAR(MAX)") private String evidenceGuidanceAr;

    @Column(name = "scoring_method", length = 40)               private String scoringMethod;  // LEVEL_0_5 | BINARY | PERCENT | RUBRIC
    @Column(nullable = false, precision = 5, scale = 2)         private BigDecimal weight = BigDecimal.ONE;
    @Column(nullable = false, length = 20)                      private String priority = "MEDIUM";
    @Column(nullable = false)                                   private Boolean mandatory = false;
    @Column(name = "owner_role", length = 80)                   private String ownerRole;
    @Column(name = "review_frequency", length = 30)             private String reviewFrequency;

    @Column(name = "related_ndmo_control",     length = 120) private String relatedNdmoControl;
    @Column(name = "related_pdpl_requirement", length = 120) private String relatedPdplRequirement;
    @Column(name = "related_cyber_control",    length = 120) private String relatedCyberControl;
    @Column(name = "related_iso_mapping",      length = 120) private String relatedIsoMapping;

    @Column(name = "is_official_import", nullable = false) private Boolean isOfficialImport = false;
    @Column(name = "is_placeholder",     nullable = false) private Boolean isPlaceholder    = true;

    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "effective_date") private LocalDate effectiveDate;

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
