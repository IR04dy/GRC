package com.tss.grc.module.scm.resilience.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pre-vetted backup supplier we'd fall back to for a critical primary
 * vendor (Saudi GRC §3.3). Each critical vendor may have 1-3 alternates
 * ranked by role.
 */
@Entity
@Table(schema = "scm", name = "alternate_suppliers")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AlternateSupplier {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "primary_vendor_id")
    private Long primaryVendorId;

    @Column(name = "primary_vendor_name", nullable = false, length = 255)
    private String primaryVendorName;

    @Column(name = "alternate_vendor_name", nullable = false, length = 255)
    private String alternateVendorName;

    @Column(name = "alternate_vendor_country", length = 3)
    private String alternateVendorCountry;

    /** BACKUP_PRIMARY | BACKUP_SECONDARY | TERTIARY. */
    @Column(name = "alternate_vendor_role", nullable = false, length = 20)
    private String alternateVendorRole;

    @Column(name = "service_scope_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String serviceScopeEn;

    @Column(name = "service_scope_ar", columnDefinition = "NVARCHAR(MAX)")
    private String serviceScopeAr;

    @Column(name = "estimated_onboarding_days")
    private Integer estimatedOnboardingDays;

    @Column(name = "estimated_cost_sar", precision = 18, scale = 2)
    private BigDecimal estimatedCostSar;

    @Column(name = "last_assessed_at")
    private LocalDateTime lastAssessedAt;

    @Column(name = "last_assessed_by")
    private Long lastAssessedBy;

    /** READY | CONDITIONAL | GAP | NOT_VIABLE. */
    @Column(name = "last_assessed_outcome", length = 20)
    private String lastAssessedOutcome;

    /** NONE | MOU_SIGNED | FRAMEWORK_ACTIVE. */
    @Column(name = "contractual_status", nullable = false, length = 20)
    private String contractualStatus = "NONE";

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String notesEn;

    @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String notesAr;

    /** DRAFT | APPROVED | ARCHIVED. */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approver_name", length = 255)
    private String approverName;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archive_reason", length = 500)
    private String archiveReason;

    // ─── BaseEntity audit fields ─────────────────────────────────────
    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "is_deleted", nullable = false)
    private Short isDeleted = 0;

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
