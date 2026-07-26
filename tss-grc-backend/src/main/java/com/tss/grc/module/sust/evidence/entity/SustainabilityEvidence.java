package com.tss.grc.module.sust.evidence.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sustainability-scoped evidence repository (V64 — slice 10).
 *
 * <p>Backs the {@code evidence_ref} text columns scattered across the
 * V55-V63 entities. Each ESG record can either embed a free-text
 * reference (legacy) or point at a row here (new). The
 * {@code module_area} discriminator (GHG / SGI / CMA / TCFD /
 * MATERIALITY / METRIC / CLIMATE_RISK / CORRECTIVE_ACTION / ANNEX /
 * OTHER) lets the repo answer "what evidence supports the GHG
 * inventory" without joining every sustainability table.
 *
 * <p>Approval lifecycle:
 * <pre>
 *   PENDING_REVIEW → APPROVED  (reviewer signed off)
 *   PENDING_REVIEW → REJECTED  (sent back with comments)
 *   APPROVED → SUPERSEDED      (replaced by a newer version)
 *   APPROVED → EXPIRED         (Rule 9 — valid_to passed)
 * </pre>
 */
@Entity
@Table(schema = "sust", name = "evidence")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SustainabilityEvidence {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;
    @Column(name = "evidence_code", length = 64)        private String evidenceCode;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "module_area",   length = 40) private String moduleArea;
    @Column(name = "evidence_type", length = 40) private String evidenceType;

    @Column(name = "file_url",  length = 500) private String fileUrl;
    @Column(name = "file_name", length = 255) private String fileName;
    @Column(length = 20)                       private String version;

    @Column(name = "reporting_year") private Short reportingYear;

    @Column(name = "valid_from")  private LocalDate validFrom;
    @Column(name = "valid_to")    private LocalDate validTo;
    @Column(name = "expiry_date") private LocalDate expiryDate;

    @Column(nullable = false, length = 20)              private String confidentiality = "INTERNAL";
    @Column(name = "verification_status",   length = 30) private String verificationStatus;
    @Column(name = "verification_provider", length = 120) private String verificationProvider;
    @Column(name = "verification_date")                   private LocalDate verificationDate;

    @Column(name = "approval_status",  nullable = false, length = 30) private String approvalStatus = "PENDING_REVIEW";
    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)") private String rejectionReason;

    @Column(name = "digital_signature_status", length = 20) private String digitalSignatureStatus;

    @Column(name = "owner_name",  length = 255) private String ownerName;
    @Column(name = "owner_email", length = 255) private String ownerEmail;
    @Column(name = "reviewer_name", length = 255) private String reviewerName;
    @Column(name = "reviewed_at")                  private LocalDateTime reviewedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_by",  length = 255)   private String approvedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at")                  private LocalDateTime approvedAt;

    @Column(name = "supersedes_id") private Long supersedesId;

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
