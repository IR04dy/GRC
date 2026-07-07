package com.tss.grc.module.attachment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Generic, typed file attachment that any entity can own.
 *
 * <p>Polymorphic by ({@code entity_type}, {@code entity_id}) so one reusable
 * facility serves every register/form ("attach plan / report / policy / …").
 * Each row carries a {@code doc_type} for purpose and an optional {@code valid_to}
 * for freshness/expiry. Storage mechanics (allow-list, size cap, path-traversal
 * guard, SHA-256) live in {@code AttachmentStorageService}. Maps the pre-existing
 * {@code platform.attachments} table (file_size_bytes / sha256_hash / uploaded_by
 * / uploaded_at) plus the added doc_type / title_en / valid_to columns.
 */
@Entity
@Table(schema = "platform", name = "attachments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Attachment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** PLAN/REPORT/POLICY/CERTIFICATE/EVIDENCE/CONTRACT/MINUTES/QUESTIONNAIRE/SCREENSHOT/OTHER */
    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType = "OTHER";

    @Column(name = "title_en", length = 300)
    private String titleEn;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "mime_type", nullable = false, length = 200)
    private String mimeType;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    /** Optional validity/expiry of the document (freshness / recollection). */
    @Column(name = "valid_to")
    private LocalDate validTo;

    /** Data classification of the artifact (NDMO-aligned): PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED.
     *  Nullable — legacy rows and uploads that don't set one remain null (treated as "unclassified"). */
    @Column(name = "classification", length = 30)
    private String classification;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    // ── Versioning ────────────────────────────────────────────────────────────
    /** 1 for the first upload; incremented each time the document is replaced. */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** Id of the first (v1) attachment in this document's version chain. All
     *  versions of one logical document share this. Null until set post-insert. */
    @Column(name = "version_group_id")
    private Long versionGroupId;

    /** True once a newer version exists — superseded rows are hidden from the
     *  current list but remain available via the version history. */
    @Column(name = "superseded", nullable = false)
    private Boolean superseded = false;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
