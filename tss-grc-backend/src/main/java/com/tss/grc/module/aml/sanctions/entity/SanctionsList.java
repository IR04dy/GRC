package com.tss.grc.module.aml.sanctions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — A single sanctions list (OFAC SDN, UN Consolidated, etc.).
 *
 * <p>{@code organisationId == null} = platform-global list (shared across
 * tenants). An org may also create internal lists scoped to itself.
 */
@Entity
@Table(schema = "aml", name = "sanctions_list")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SanctionsList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL = platform-global list. */
    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "list_code", nullable = false, length = 64)
    private String listCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 255)
    private String titleAr;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** UN | OFAC | EU | SAMA | KSA_NATIONAL | FATF | OTHER */
    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "last_refreshed_at")
    private LocalDateTime lastRefreshedAt;

    /** DAILY | WEEKLY | MONTHLY | ON_DEMAND */
    @Column(name = "refresh_frequency", nullable = false, length = 20)
    private String refreshFrequency = "DAILY";

    @Column(name = "entry_count", nullable = false)
    private Integer entryCount = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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
