package com.tss.grc.module.ndi.plan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "ndi", name = "improvement_plan_item")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiImprovementPlanItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    @Column(name = "plan_id", nullable = false) private Long planId;
    @Column(name = "capa_id", nullable = false) private Long capaId;

    @Column(name = "sequence_order", nullable = false) private Integer sequenceOrder;
    @Column(nullable = false, length = 20)             private String priority;
    @Column(name = "target_quarter", length = 10)      private String targetQuarter;
    @Column(name = "allocated_budget", precision = 20, scale = 2) private BigDecimal allocatedBudget;
    @Column(length = 500)                              private String dependencies;
    @Column(columnDefinition = "NVARCHAR(MAX)")        private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null)      createdAt = LocalDateTime.now();
        if (sequenceOrder == null)  sequenceOrder = 100;
        if (priority == null)       priority = "MEDIUM";
        if (isDeleted == null)      isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
