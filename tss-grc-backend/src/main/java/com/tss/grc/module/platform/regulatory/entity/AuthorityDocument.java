package com.tss.grc.module.platform.regulatory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Authority Document — a regulator's published document (NCA ECC,
 * SAMA BCM FW, PDPL, ISO 27001 …) that the platform tracks.
 *
 * <p>Part of the Framework Intersection plan (slice F1). One row per
 * (org, code, version). The platform never claims regulatory
 * completeness — every seeded row carries {@code is_placeholder = 1}
 * until the official text is imported.
 *
 * <p>See {@code docs/grc/framework-intersection-plan.md} §4 for the
 * full catalog.
 */
@Entity
@Table(schema = "platform", name = "authority_documents")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AuthorityDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 60)
    private String family;

    @Column(nullable = false, length = 60)
    private String jurisdiction;

    @Column(nullable = false, length = 120)
    private String issuer;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String summaryEn;

    @Column(name = "summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String summaryAr;

    /** JSON. {@code { "org_types": ["BANK","INSURER"], "sectors":["FINANCE"] }} */
    @Column(name = "applicability_json", columnDefinition = "NVARCHAR(MAX)")
    private String applicabilityJson;

    @Column(name = "effective_date_g")
    private LocalDate effectiveDateG;

    @Column(name = "effective_date_h", length = 20)
    private String effectiveDateH;

    // ⚠⚠ F2-239 — no initialiser ON PURPOSE on these two, and re-adding one is the defect: an
    // entity-bound PUT deserialises an omitted key to the initialiser, so `update`'s
    // `if (body.getIsPlaceholder() != null)` guard could never be false. The defaults live in
    // `createDocument`. Latent here rather than live — this page's DOCUMENT form does send both,
    // which is why the citation half below it was the reachable one — but two of five fixed leaves
    // the rest looking covered (F2-215), and the register is the boundary, not the field.
    @Column(name = "is_placeholder", nullable = false)
    private Boolean isPlaceholder;

    /** EN_ONLY | AR_AUTO | AR_HUMAN_REVIEWED | AR_OFFICIAL */
    @Column(name = "translation_status", nullable = false, length = 20)
    private String translationStatus;

    /** DRAFT | ACTIVE | DEPRECATED | SUPERSEDED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "superseded_by_id")
    private Long supersededById;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
