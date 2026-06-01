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

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
