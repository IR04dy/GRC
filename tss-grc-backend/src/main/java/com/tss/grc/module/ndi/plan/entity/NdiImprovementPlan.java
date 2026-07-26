package com.tss.grc.module.ndi.plan.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NDI Improvement Plan — slice 9. Aggregates corrective actions from
 * slice 8 into a time-bound roadmap with quarterly tracks, priorities
 * and an optional budget. One plan per cycle is the typical case.
 *
 * <p>Status machine: DRAFT → APPROVED → IN_FLIGHT → COMPLETED |
 * SUSPENDED → ARCHIVED.
 *
 * <p>Rollup fields (total_items, completed_items, progress_pct) are
 * recomputed server-side whenever a plan item is added / removed or a
 * referenced CAPA changes state.
 */
@Entity
@Table(schema = "ndi", name = "improvement_plan")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiImprovementPlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "cycle_id", nullable = false) private Long cycleId;
    @Column(name = "plan_code", length = 40)     private String planCode;

    @Column(name = "name_en", nullable = false, length = 255) private String nameEn;
    @Column(name = "name_ar", length = 255)                   private String nameAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end",   nullable = false) private LocalDate periodEnd;

    @Column(name = "owner_name",     length = 255) private String ownerName;
    @Column(name = "owner_email",    length = 255) private String ownerEmail;
    @Column(name = "approver_name",  length = 255) private String approverName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at")                  private LocalDateTime approvedAt;

    @Column(nullable = false, length = 20) private String status;

    @Column(name = "total_items",     nullable = false) private Integer totalItems;
    @Column(name = "completed_items", nullable = false) private Integer completedItems;
    @Column(name = "progress_pct",    nullable = false) private Integer progressPct;

    @Column(name = "budget_total",    precision = 20, scale = 2) private BigDecimal budgetTotal;
    @Column(name = "budget_currency", nullable = false, length = 10) private String budgetCurrency;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null)       createdAt = LocalDateTime.now();
        if (status == null)          status = "DRAFT";
        if (totalItems == null)      totalItems = 0;
        if (completedItems == null)  completedItems = 0;
        if (progressPct == null)     progressPct = 0;
        if (budgetCurrency == null)  budgetCurrency = "SAR";
        if (isDeleted == null)       isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
