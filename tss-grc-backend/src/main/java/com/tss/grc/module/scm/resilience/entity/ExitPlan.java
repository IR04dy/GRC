package com.tss.grc.module.scm.resilience.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Concrete disengagement plan for a vendor (Saudi GRC §3.3). Holds
 * the ordered playbook (steps_json), the last-tested outcome, and the
 * next-test due date.
 *
 * <p>Lifecycle: {@code DRAFT → APPROVED → ACTIVATED | ARCHIVED}, with
 * {@code REJECTED} as a terminal alternative from DRAFT.
 */
@Entity
@Table(schema = "scm", name = "exit_plans")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ExitPlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "vendor_name", nullable = false, length = 255)
    private String vendorName;

    @Column(name = "contract_code", length = 64)
    private String contractCode;

    @Column(name = "plan_code", nullable = false, length = 64)
    private String planCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "trigger_conditions", columnDefinition = "NVARCHAR(MAX)")
    private String triggerConditions;

    /** LOW | MEDIUM | HIGH | CRITICAL. */
    @Column(name = "severity_threshold", length = 20)
    private String severityThreshold;

    @Column(name = "target_disengagement_days", nullable = false)
    private Integer targetDisengagementDays = 90;

    @Column(name = "steps_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String stepsJson;

    @Column(name = "data_migration_plan_en", columnDefinition = "NVARCHAR(MAX)")
    private String dataMigrationPlanEn;

    @Column(name = "data_migration_plan_ar", columnDefinition = "NVARCHAR(MAX)")
    private String dataMigrationPlanAr;

    @Column(name = "knowledge_transfer_plan_en", columnDefinition = "NVARCHAR(MAX)")
    private String knowledgeTransferPlanEn;

    @Column(name = "knowledge_transfer_plan_ar", columnDefinition = "NVARCHAR(MAX)")
    private String knowledgeTransferPlanAr;

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    /** NOT_TESTED | PASSED | GAP_IDENTIFIED | FAILED. */
    @Column(name = "last_tested_outcome", length = 20)
    private String lastTestedOutcome;

    @Column(name = "next_test_due")
    private LocalDate nextTestDue;

    /** DRAFT | APPROVED | ACTIVATED | ARCHIVED | REJECTED. */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approver_name", length = 255)
    private String approverName;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "activated_by")
    private Long activatedBy;

    @Column(name = "activation_reason", length = 500)
    private String activationReason;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archive_reason", length = 500)
    private String archiveReason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

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
