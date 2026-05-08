package com.tss.grc.module.platform.files.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Where;

/**
 * File metadata — production-grade companion to the frontend
 * `lib/fileUpload.ts` adapter.
 *
 * The actual bytes live in S3-compatible storage at `storage_ref`. This
 * row tracks ownership, residency, virus-scan outcome, retention, and
 * SHA-256 for tamper detection + dedupe.
 *
 * Indexed by (organisation_id, attached_to) for "list files attached to
 * record X" lookups, and by (organisation_id, sha256) for dedupe.
 */
@Entity
@Table(name = "file_metadata",
       indexes = {
           @Index(name = "idx_file_org_attached", columnList = "organisation_id, attached_to"),
           @Index(name = "idx_file_org_sha", columnList = "organisation_id, sha256"),
           @Index(name = "idx_file_uploaded", columnList = "uploaded_at"),
       })
@Where(clause = "is_deleted = 0")
public class FileMetadata extends BaseEntity {

    /** Tenant scope. */
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Logical owner — "evidence/EV-1078", "dsr/DSR-2026-013", … */
    @Column(name = "attached_to", nullable = false, length = 200)
    private String attachedTo;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "mime", nullable = false, length = 100)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** SHA-256 hex (64 chars). */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /** Storage reference returned by the storage backend (S3 key, etc.). */
    @Column(name = "storage_ref", length = 500)
    private String storageRef;

    /** PENDING / CLEAN / SUSPICIOUS / INFECTED / SKIPPED. */
    @Column(name = "scan_status", nullable = false, length = 32)
    private String scanStatus;

    @Column(name = "scan_reason", length = 500)
    private String scanReason;

    /** When the upload completed. */
    @Column(name = "uploaded_at", nullable = false)
    private String uploadedAt;

    @Column(name = "uploaded_by", nullable = false, length = 200)
    private String uploadedBy;

    /** Vendor CR number when the uploader is a third party. */
    @Column(name = "cr_number", length = 32)
    private String crNumber;

    /** KSA / GCC / GLOBAL — drives cross-border review flag. */
    @Column(name = "residency", nullable = false, length = 16)
    private String residency;

    /** Days the file is retained before auto-delete. */
    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;

    public FileMetadata() {}

    public Long getOrganisationId() { return organisationId; }
    public void setOrganisationId(Long organisationId) { this.organisationId = organisationId; }
    public String getAttachedTo() { return attachedTo; }
    public void setAttachedTo(String attachedTo) { this.attachedTo = attachedTo; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }
    public String getScanStatus() { return scanStatus; }
    public void setScanStatus(String scanStatus) { this.scanStatus = scanStatus; }
    public String getScanReason() { return scanReason; }
    public void setScanReason(String scanReason) { this.scanReason = scanReason; }
    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public String getCrNumber() { return crNumber; }
    public void setCrNumber(String crNumber) { this.crNumber = crNumber; }
    public String getResidency() { return residency; }
    public void setResidency(String residency) { this.residency = residency; }
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
}
