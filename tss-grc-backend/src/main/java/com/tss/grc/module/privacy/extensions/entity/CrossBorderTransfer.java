package com.tss.grc.module.privacy.extensions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * PDPL Art. 29 cross-border transfer record.
 *
 * <p>State machine: REQUESTED → ASSESSED → APPROVED | REJECTED → EXPIRED.
 */
@Entity
@Table(schema = "privacy", name = "cross_border_transfers")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrossBorderTransfer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "ropa_code", nullable = false, length = 64) private String ropaCode;
    @Column(name = "ropa_title_en", length = 255)             private String ropaTitleEn;

    @Column(name = "source_country", nullable = false, length = 3)    private String sourceCountry = "SAU";
    @Column(name = "destination_country", nullable = false, length = 3) private String destinationCountry;
    @Column(name = "destination_org_name", nullable = false, length = 255) private String destinationOrgName;
    @Column(name = "destination_org_role", length = 50)              private String destinationOrgRole;

    @Column(name = "data_categories", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String dataCategories;
    @Column(name = "data_subjects_count_est") private Long dataSubjectsCountEst;

    /** ADEQUACY | SCC | BCR | EXPLICIT_CONSENT | CONTRACT_NECESSITY | VITAL_INTEREST | PUBLIC_INTEREST. */
    @Column(name = "transfer_mechanism", nullable = false, length = 30) private String transferMechanism;
    @Column(name = "safeguards_en", columnDefinition = "NVARCHAR(MAX)") private String safeguardsEn;
    @Column(name = "safeguards_ar", columnDefinition = "NVARCHAR(MAX)") private String safeguardsAr;
    @Column(name = "justification_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String justificationEn;
    @Column(name = "justification_ar", columnDefinition = "NVARCHAR(MAX)") private String justificationAr;

    @Column(name = "residency_check_at")  private LocalDateTime residencyCheckAt;
    @Column(name = "residency_check_by", length = 255) private String residencyCheckBy;
    /** PASS | PASS_WITH_CONDITIONS | FAIL. */
    @Column(name = "residency_check_outcome", length = 20) private String residencyCheckOutcome;

    /** REQUESTED | ASSESSED | APPROVED | REJECTED | EXPIRED. */
    @Column(nullable = false, length = 20) private String status = "REQUESTED";
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;
    @Column(name = "approved_at") private LocalDateTime approvedAt;
    @Column(name = "approved_by") private Long approvedBy;
    @Column(name = "approver_name", length = 255) private String approverName;
    @Column(name = "effective_from") private LocalDateTime effectiveFrom;
    @Column(name = "expiry_date")    private LocalDateTime expiryDate;

    // ─── Audit ─────────────────────────────────────────────────────────
    @Column(name = "external_id", length = 64) private String externalId;
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active", nullable = false) private Short isActive = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
