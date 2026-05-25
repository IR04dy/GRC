package com.tss.grc.module.ai.lifecycle.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Periodic recertification (annual for CRITICAL/HIGH risk-tier models). */
@Entity
@Table(schema = "ai_governance", name = "recertification")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Recertification extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "model_id", nullable = false)        private Long modelId;
    @Column(name = "reference", nullable = false, length = 64) private String reference;

    @Column(name = "cycle_label", length = 32) private String cycleLabel;

    /** DUE/IN_REVIEW/RECERTIFIED/RECERTIFIED_WITH_CONDITIONS/REVOKED. */
    @Column(name = "status", nullable = false, length = 32) private String status = "DUE";

    @Column(name = "due_date")        private LocalDate dueDate;
    @Column(name = "started_at")      private LocalDateTime startedAt;
    @Column(name = "decided_at")      private LocalDateTime decidedAt;
    @Column(name = "next_due_date")   private LocalDate nextDueDate;

    @Column(name = "decision_rationale_en", columnDefinition = "NVARCHAR(MAX)") private String decisionRationaleEn;
    @Column(name = "decision_rationale_ar", columnDefinition = "NVARCHAR(MAX)") private String decisionRationaleAr;

    @Column(name = "conditions_en", columnDefinition = "NVARCHAR(MAX)") private String conditionsEn;
    @Column(name = "conditions_ar", columnDefinition = "NVARCHAR(MAX)") private String conditionsAr;

    @Column(name = "approver_user_id") private Long approverUserId;
    @Column(name = "linked_validation_id") private Long linkedValidationId;
}
