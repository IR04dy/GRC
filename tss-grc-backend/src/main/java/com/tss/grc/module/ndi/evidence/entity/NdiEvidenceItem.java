package com.tss.grc.module.ndi.evidence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NDI Evidence Item — slice 7.
 *
 * <p>Metadata pointer to an evidence artefact (policy / procedure /
 * report / screenshot / attestation / certificate / audit log / dataset).
 * The binary lives in the platform's evidence module once uploaded;
 * this NDI table denormalises a stable pointer + classification +
 * expiry so the submission package can be assembled without round-trips.
 *
 * <p>Status machine: PENDING → ACTIVE → (EXPIRED | SUPERSEDED | REJECTED).
 * The service auto-flips ACTIVE → EXPIRED when expiry_date &lt; today.
 */
@Entity
@Table(schema = "ndi", name = "evidence_item")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiEvidenceItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "cycle_id")                  private Long cycleId;
    @Column(name = "evidence_code", length = 40) private String evidenceCode;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "evidence_type",  nullable = false, length = 30) private String evidenceType;
    @Column(nullable = false, length = 20)                          private String classification;

    @Column(name = "file_ref",        length = 500) private String fileRef;
    @Column(name = "mime_type",       length = 120) private String mimeType;
    @Column(name = "file_size_bytes")               private Long fileSizeBytes;
    @Column(name = "source_system",   length = 255) private String sourceSystem;

    @Column(name = "owner_name",    length = 255) private String ownerName;
    @Column(name = "owner_email",   length = 255) private String ownerEmail;
    @Column(name = "reviewer_name", length = 255) private String reviewerName;

    @Column(name = "effective_date") private LocalDate effectiveDate;
    @Column(name = "expiry_date")    private LocalDate expiryDate;
    @Column(name = "reviewed_at")    private LocalDate reviewedAt;

    @Column(nullable = false, length = 20) private String status;
    @Column(name = "supersedes_id")        private Long supersedesId;
    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)") private String rejectionReason;

    @Column(length = 500)                       private String tags;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null)     createdAt = LocalDateTime.now();
        if (status == null)        status = "PENDING";
        if (classification == null) classification = "INTERNAL";
        if (isDeleted == null)     isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
