package com.tss.grc.module.scm.resilience.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Supplier-specific resilience exercise (gap-analysis: SCM resilience testing).
 *
 * <p>Status machine: {@code SCHEDULED → IN_PROGRESS → COMPLETED → APPROVED | CANCELLED}.
 * See V54 migration for column commentary.
 */
@Entity
@Table(schema = "scm", name = "supplier_resilience_tests")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SupplierResilienceTest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id",     length = 64)     private String externalId;
    @Column(name = "test_code",       length = 40)     private String testCode;

    @Column(name = "vendor_id")                                   private Long vendorId;
    @Column(name = "vendor_name", nullable = false, length = 255) private String vendorName;
    @Column(name = "title_en",    nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar",    length = 255)                   private String titleAr;

    @Column(name = "test_type",      nullable = false, length = 30) private String testType;
    @Column(name = "scenario_scope", nullable = false, length = 30) private String scenarioScope = "DESKTOP";

    @Column(name = "scenario_description_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String scenarioDescriptionEn;
    @Column(name = "scenario_description_ar", columnDefinition = "NVARCHAR(MAX)") private String scenarioDescriptionAr;
    @Column(name = "success_criteria_en",     columnDefinition = "NVARCHAR(MAX)") private String successCriteriaEn;
    @Column(name = "participants",            columnDefinition = "NVARCHAR(MAX)") private String participants;

    @Column(name = "linked_alternate_id") private Long linkedAlternateId;
    @Column(name = "linked_exit_plan_id") private Long linkedExitPlanId;
    @Column(name = "linked_bcm_plan_id")  private Long linkedBcmPlanId;

    @Column(name = "scheduled_for")    private LocalDateTime scheduledFor;
    @Column(name = "executed_at")      private LocalDateTime executedAt;
    @Column(name = "duration_minutes") private Integer duration;

    @Column(length = 25) private String outcome;
    @Column(name = "rto_target_minutes") private Integer rtoTargetMinutes;
    @Column(name = "rto_actual_minutes") private Integer rtoActualMinutes;
    @Column(name = "observations_en", columnDefinition = "NVARCHAR(MAX)") private String observationsEn;
    @Column(name = "observations_ar", columnDefinition = "NVARCHAR(MAX)") private String observationsAr;
    @Column(name = "action_items_en", columnDefinition = "NVARCHAR(MAX)") private String actionItemsEn;
    @Column(name = "capa_refs",       columnDefinition = "NVARCHAR(MAX)") private String capaRefs;
    @Column(name = "evidence_refs",   columnDefinition = "NVARCHAR(MAX)") private String evidenceRefs;

    @Column(name = "next_test_due") private LocalDate nextTestDue;
    @Column(length = 255)           private String facilitator;
    @Column(name = "approver_name", length = 255) private String approverName;
    @Column(name = "approved_at")   private LocalDateTime approvedAt;

    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(name = "cancellation_reason", columnDefinition = "NVARCHAR(MAX)")
    private String cancellationReason;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active",  nullable = false) private Short isActive  = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
