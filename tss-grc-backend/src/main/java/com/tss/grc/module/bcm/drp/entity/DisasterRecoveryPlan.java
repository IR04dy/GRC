package com.tss.grc.module.bcm.drp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "bcm", name = "dr_plans")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DisasterRecoveryPlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    @Column(name = "plan_code", nullable = false, length = 64) private String planCode;
    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "linked_bcp_id")          private Long   linkedBcpId;
    @Column(name = "linked_bcp_code", length = 64) private String linkedBcpCode;
    @Column(name = "critical_system_id")     private Long   criticalSystemId;
    @Column(name = "critical_system_name", length = 255) private String criticalSystemName;
    @Column(length = 255)                    private String owner;

    @Column(name = "rto_hours", nullable = false) private Integer rtoHours = 4;
    @Column(name = "rpo_hours", nullable = false) private Integer rpoHours = 1;
    @Column(name = "mtpd_hours")                  private Integer mtpdHours;

    /** HOT | WARM | COLD | ACTIVE_ACTIVE | CLOUD_DR. */
    @Column(name = "dr_site_type", nullable = false, length = 20) private String drSiteType = "WARM";
    @Column(name = "dr_site_location", length = 255) private String drSiteLocation;
    @Column(name = "dr_site_provider", length = 255) private String drSiteProvider;

    @Column(name = "recovery_strategy_en", columnDefinition = "NVARCHAR(MAX)") private String recoveryStrategyEn;
    @Column(name = "recovery_strategy_ar", columnDefinition = "NVARCHAR(MAX)") private String recoveryStrategyAr;

    @Column(name = "dependencies_json",   columnDefinition = "NVARCHAR(MAX)") private String dependenciesJson;
    @Column(name = "failover_steps_json", columnDefinition = "NVARCHAR(MAX)") private String failoverStepsJson;
    @Column(name = "failback_steps_json", columnDefinition = "NVARCHAR(MAX)") private String failbackStepsJson;

    @Column(name = "data_backup_strategy_en", columnDefinition = "NVARCHAR(MAX)") private String dataBackupStrategyEn;
    @Column(name = "data_backup_strategy_ar", columnDefinition = "NVARCHAR(MAX)") private String dataBackupStrategyAr;

    @Column(name = "last_tested_at")       private LocalDateTime lastTestedAt;
    @Column(name = "last_tested_by")       private Long          lastTestedBy;
    @Column(name = "last_tested_outcome", length = 20) private String lastTestedOutcome;
    @Column(name = "last_test_notes_en", columnDefinition = "NVARCHAR(MAX)") private String lastTestNotesEn;
    @Column(name = "next_test_due")        private LocalDate     nextTestDue;

    /** DRAFT | APPROVED | ACTIVATED | ARCHIVED | REJECTED. */
    @Column(nullable = false, length = 20) private String status = "DRAFT";
    @Column(name = "approved_at")    private LocalDateTime approvedAt;
    @Column(name = "approved_by")    private Long          approvedBy;
    @Column(name = "approver_name", length = 255) private String approverName;
    @Column(name = "activated_at")   private LocalDateTime activatedAt;
    @Column(name = "activated_by")   private Long          activatedBy;
    @Column(name = "activation_reason", length = 500) private String activationReason;
    @Column(name = "archived_at")    private LocalDateTime archivedAt;
    @Column(name = "archive_reason", length = 500) private String archiveReason;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;

    @Column(name = "external_id", length = 64) private String externalId;
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active",  nullable = false) private Short isActive  = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long          deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long          createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long          updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
