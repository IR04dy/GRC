package com.tss.grc.module.ai.lifecycle.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Independent validation of an AI/ML model. */
@Entity
@Table(schema = "ai_governance", name = "model_validation")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ModelValidation extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "model_id", nullable = false)        private Long modelId;
    @Column(name = "reference", nullable = false, length = 64) private String reference;

    /** SCHEDULED/IN_PROGRESS/PASSED/PASSED_WITH_CONDITIONS/FAILED/REMEDIATION. */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "SCHEDULED";

    /** INDEPENDENT/INTERNAL/EXTERNAL_AUDIT. */
    @Column(name = "validation_type", length = 24) private String validationType;

    @Column(name = "validator_user_id")   private Long validatorUserId;
    @Column(name = "validator_org_label", length = 256) private String validatorOrgLabel;

    @Column(name = "scheduled_at") private LocalDate scheduledAt;
    @Column(name = "started_at")   private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;

    /** Test metric outcomes — JSON like {accuracy:0.94, precision:0.91, ...}. */
    @Column(name = "metrics_json", columnDefinition = "NVARCHAR(MAX)")
    private String metricsJson;

    /** SDAIA principle scores (8 principles), JSON. */
    @Column(name = "ethics_scores_json", columnDefinition = "NVARCHAR(MAX)")
    private String ethicsScoresJson;

    @Column(name = "fairness_disparity_pct", precision = 6, scale = 2)
    private BigDecimal fairnessDisparityPct;

    @Column(name = "findings_en", columnDefinition = "NVARCHAR(MAX)") private String findingsEn;
    @Column(name = "findings_ar", columnDefinition = "NVARCHAR(MAX)") private String findingsAr;

    @Column(name = "conditions_en", columnDefinition = "NVARCHAR(MAX)") private String conditionsEn;
    @Column(name = "conditions_ar", columnDefinition = "NVARCHAR(MAX)") private String conditionsAr;

    @Column(name = "linked_capa_id") private Long linkedCapaId;
}
