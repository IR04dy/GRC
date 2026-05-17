package com.tss.grc.module.ndi.submission.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * NDI Submission Package — slice 10.
 *
 * <p>Immutable snapshot the entity sends to SDAIA at cycle close.
 * Freezes the overall NDI score, every artefact count, and a JSON
 * snapshot of the maturity / compliance / opex / domains / evidence /
 * gaps / plan state at the moment of submission.
 *
 * <p>Status machine: DRAFT → READY → SUBMITTED → ACKNOWLEDGED |
 * REJECTED → ARCHIVED. REJECTED can be returned to DRAFT for re-work.
 *
 * <p>Rule 15 (pre-flight) gates DRAFT → READY. Rule 16 (immutability)
 * locks snapshot fields once status moves past READY.
 */
@Entity
@Table(schema = "ndi", name = "submission_package")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiSubmissionPackage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "cycle_id", nullable = false)         private Long cycleId;
    @Column(name = "package_code", length = 40)          private String packageCode;
    @Column(nullable = false)                            private Integer version;

    @Column(name = "snapshot_json",     columnDefinition = "NVARCHAR(MAX)") private String snapshotJson;
    @Column(name = "manifest_json",     columnDefinition = "NVARCHAR(MAX)") private String manifestJson;
    @Column(name = "manifest_checksum", length = 128)                       private String manifestChecksum;

    @Column(name = "overall_ndi_pct",    precision = 5, scale = 2) private BigDecimal overallNdiPct;
    @Column(name = "maturity_score_pct", precision = 5, scale = 2) private BigDecimal maturityScorePct;
    @Column(name = "compliance_pct",     precision = 5, scale = 2) private BigDecimal compliancePct;
    @Column(name = "opex_score_pct",     precision = 5, scale = 2) private BigDecimal opexScorePct;

    @Column(name = "total_evidence",    nullable = false) private Integer totalEvidence;
    @Column(name = "total_gaps",        nullable = false) private Integer totalGaps;
    @Column(name = "critical_gaps",     nullable = false) private Integer criticalGaps;
    @Column(name = "total_actions",     nullable = false) private Integer totalActions;
    @Column(name = "completed_actions", nullable = false) private Integer completedActions;

    @Column(nullable = false, length = 20) private String status;

    @Column(name = "preparer_name",  length = 255) private String preparerName;
    @Column(name = "preparer_email", length = 255) private String preparerEmail;
    @Column(name = "reviewer_name",  length = 255) private String reviewerName;
    @Column(name = "approver_name",  length = 255) private String approverName;
    @Column(name = "submitter_name", length = 255) private String submitterName;

    @Column(name = "prepared_at")     private LocalDateTime preparedAt;
    @Column(name = "reviewed_at")     private LocalDateTime reviewedAt;
    @Column(name = "approved_at")     private LocalDateTime approvedAt;
    @Column(name = "submitted_at")    private LocalDateTime submittedAt;
    @Column(name = "acknowledged_at") private LocalDateTime acknowledgedAt;

    @Column(name = "sdaia_reference",  length = 100)                       private String sdaiaReference;
    @Column(name = "attestation_en",   columnDefinition = "NVARCHAR(MAX)") private String attestationEn;
    @Column(name = "attestation_ar",   columnDefinition = "NVARCHAR(MAX)") private String attestationAr;
    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)") private String rejectionReason;

    @Column(name = "is_placeholder", nullable = false) private Boolean isPlaceholder;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null)        createdAt = LocalDateTime.now();
        if (version == null)          version = 1;
        if (status == null)           status = "DRAFT";
        if (totalEvidence == null)    totalEvidence = 0;
        if (totalGaps == null)        totalGaps = 0;
        if (criticalGaps == null)     criticalGaps = 0;
        if (totalActions == null)     totalActions = 0;
        if (completedActions == null) completedActions = 0;
        if (isPlaceholder == null)    isPlaceholder = true;
        if (isDeleted == null)        isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
