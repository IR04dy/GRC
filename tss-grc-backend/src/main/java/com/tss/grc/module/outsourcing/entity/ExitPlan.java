package com.tss.grc.module.outsourcing.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** SAMA §7 exit-and-contingency plan. One per arrangement. */
@Entity(name = "OutsourcingCloudExitPlan")
@Table(schema = "outsourcing", name = "exit_plan")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ExitPlan extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "arrangement_id", nullable = false)
    private Long arrangementId;

    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** DRAFT/APPROVED/REHEARSED/READY/EXECUTED/REJECTED. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String summaryEn;

    @Column(name = "summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String summaryAr;

    /** Target migration window in business days from "trigger event" to "fully migrated". */
    @Column(name = "target_migration_days")
    private Integer targetMigrationDays;

    /** Alternative provider on file, if any. */
    @Column(name = "alternative_provider_en", length = 256)
    private String alternativeProviderEn;

    @Column(name = "alternative_provider_ar", length = 256)
    private String alternativeProviderAr;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rehearsed_at")
    private LocalDate rehearsedAt;

    @Column(name = "executed_at")
    private LocalDate executedAt;

    @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonEn;

    @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonAr;
}
