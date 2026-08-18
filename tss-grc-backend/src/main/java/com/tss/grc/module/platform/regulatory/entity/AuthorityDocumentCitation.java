package com.tss.grc.module.platform.regulatory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Citation — one requirement paragraph inside an authority document.
 *
 * <p>Examples:
 * <ul>
 *   <li>NCA-ECC-V2 · {@code 2-1-1} "Asset Management" sub-control.</li>
 *   <li>ISO-27001 · {@code A.5.7} "Threat intelligence."</li>
 *   <li>PDPL · {@code Art-14} "Cross-border data transfer."</li>
 * </ul>
 *
 * <p>Hierarchical via {@code parent_citation_code} + {@code depth} so
 * the UI can render the regulation's natural tree without a recursive
 * CTE on every read.
 */
@Entity
@Table(schema = "platform", name = "authority_document_citations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AuthorityDocumentCitation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "authority_document_id", nullable = false)
    private Long authorityDocumentId;

    @Column(name = "citation_code", nullable = false, length = 80)
    private String citationCode;

    @Column(name = "parent_citation_code", length = 80)
    private String parentCitationCode;

    @Column(nullable = false)
    private Integer depth = 0;

    // ⚠⚠ F2-239 — the five fields below carry NO initialiser ON PURPOSE, and re-adding one is the
    // defect. `updateCitation` guards each with `if (body.getX() != null)`; an entity-bound PUT
    // deserialises an omitted key to the field's INITIALISER, not to null, so an initialiser makes
    // every one of those guards true on every request and the stored value is overwritten. Probed
    // live: the register's own edit form sends six keys and omits three of these, so editing a
    // requirement paragraph moved it to sequence_order 0 (the top of the regulation's tree), marked
    // it a placeholder, and recorded it EN_ONLY — in the same request that supplied its Arabic text.
    // The defaults live in `createCitation`, which is the only writer of this table.
    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "requirement_text_en", columnDefinition = "NVARCHAR(MAX)")
    private String requirementTextEn;

    @Column(name = "requirement_text_ar", columnDefinition = "NVARCHAR(MAX)")
    private String requirementTextAr;

    @Column(name = "intent_en", columnDefinition = "NVARCHAR(MAX)")
    private String intentEn;

    @Column(name = "intent_ar", columnDefinition = "NVARCHAR(MAX)")
    private String intentAr;

    @Column(name = "testing_guidance_en", columnDefinition = "NVARCHAR(MAX)")
    private String testingGuidanceEn;

    @Column(name = "testing_guidance_ar", columnDefinition = "NVARCHAR(MAX)")
    private String testingGuidanceAr;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false)
    private Boolean mandatory;

    @Column(name = "is_placeholder", nullable = false)
    private Boolean isPlaceholder;

    @Column(name = "translation_status", nullable = false, length = 20)
    private String translationStatus;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
