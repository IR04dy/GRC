package com.tss.grc.module.evidence.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "evidence", name = "evidence_items")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EvidenceItem extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "sub_control_id")
    private Long subControlId;

    /** The EvidenceRequest this item fulfils, if it was collected against one.
     *  Closes the collect→fulfil loop: on APPROVED, the request's
     *  fulfilled_by_id / fulfilled_at get stamped. */
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "evidence_type", nullable = false, length = 50)
    private String evidenceType = "DOCUMENT";

    @Column(name = "source_module", length = 50)
    private String sourceModule;

    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 200)
    private String mimeType;

    // ── Chain-of-custody (assessment §5.9 #3) ──
    /** MANUAL | AGENT | API_PULL | SIEM | SCANNER | NAFATH_SIGNED */
    @Column(name = "collection_method", length = 50)
    private String collectionMethod;

    @Column(name = "collector_name", length = 255)
    private String collectorName;

    @Column(name = "collector_id")
    private Long collectorId;

    @Column(name = "witness_id")
    private Long witnessId;

    @Column(name = "tool_version", length = 100)
    private String toolVersion;

    /** SHA-256 hex of the evidence payload at collection time. */
    @Column(name = "evidence_hash", length = 128)
    private String evidenceHash;

    /** Hash of the previous version of this evidence — forms a per-evidence WORM chain. */
    @Column(name = "previous_hash", length = 128)
    private String previousHash;

    /** Optional external timestamp authority anchor (e.g. RFC 3161 token) — for forensic-grade WORM. */
    @Column(name = "merkle_anchor", length = 255)
    private String merkleAnchor;

    @Column(name = "collected_at")
    private java.time.LocalDateTime collectedAt;

    /** Periodic re-review trigger date (for evidence freshness — §5.9 #5). */
    @Column(name = "review_due_date")
    private java.time.LocalDate reviewDueDate;

    /** If this evidence was produced by a connector — the connector code. */
    @Column(name = "connector_code", length = 50)
    private String connectorCode;

    @Column(name = "current_version_no", nullable = false)
    private Integer currentVersionNo = 1;

    // ─── Chain-of-custody rollup (V47 / gap-analysis item C) ──────────
    /** VALID | BROKEN | UNVERIFIED. */
    @Column(name = "chain_status", length = 15)
    private String chainStatus;

    @Column(name = "chain_last_verified_at")
    private java.time.LocalDateTime chainLastVerifiedAt;

    @Column(name = "chain_last_verified_by")
    private Long chainLastVerifiedBy;

    @Column(name = "chain_version_count")
    private Integer chainVersionCount;

    @Column(name = "chain_first_break_version")
    private Integer chainFirstBreakVersion;
}
