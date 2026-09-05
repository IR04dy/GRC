package com.tss.grc.module.aml.sanctions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — A single individual / entity / vessel / aircraft entry on
 * a sanctions list.
 *
 * <p>{@code organisationId} mirrors the parent list's tenancy (NULL =
 * platform-global). {@code aliases_json} and {@code id_documents_json}
 * are stored as JSON for flexibility; the fuzzy matcher parses them on
 * demand.
 */
@Entity
@Table(schema = "aml", name = "sanctions_list_entry")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SanctionsListEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL = inherits the parent list's platform-global tenancy. */
    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "list_id", nullable = false)
    private Long listId;

    @Column(name = "entry_external_id", nullable = false, length = 64)
    private String entryExternalId;

    /** INDIVIDUAL | ENTITY | VESSEL | AIRCRAFT */
    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "primary_name_en", nullable = false, length = 500)
    private String primaryNameEn;

    @Column(name = "primary_name_ar", length = 500)
    private String primaryNameAr;

    @Column(name = "aliases_json", columnDefinition = "NVARCHAR(MAX)")
    private String aliasesJson;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "id_documents_json", columnDefinition = "NVARCHAR(MAX)")
    private String idDocumentsJson;

    @Column(name = "listing_date")
    private LocalDate listingDate;

    @Column(name = "listed_by", length = 255)
    private String listedBy;

    @Column(name = "listing_reason_en", length = 1000)
    private String listingReasonEn;

    @Column(name = "listing_reason_ar", length = 1000)
    private String listingReasonAr;

    /** STRONG_MATCH_REQUIRED | REVIEW_REQUIRED */
    @Column(name = "severity", nullable = false, length = 30)
    private String severity = "REVIEW_REQUIRED";

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
