package com.tss.grc.module.ai.governance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One audit run against an {@link MlModel}.
 *
 * <p>Audit type ∈ {FAIRNESS, BIAS, EXPLAINABILITY, ROBUSTNESS, PRIVACY,
 * SECURITY, PERFORMANCE}. Per-(model, audit_type, audit_date) tuple
 * is unique within live rows — one audit run per type per date.
 *
 * <p>{@code metrics_json} is a free-form JSON document indexed by
 * metric name (e.g. {@code demographic_parity_diff},
 * {@code equal_opportunity_diff}, {@code accuracy}, {@code f1_score}).
 */
@Entity
@Table(schema = "ai_governance", name = "ml_model_audit")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class MlModelAudit extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    /** FAIRNESS/BIAS/EXPLAINABILITY/ROBUSTNESS/PRIVACY/SECURITY/PERFORMANCE. */
    @Column(name = "audit_type", nullable = false, length = 40)
    private String auditType;

    @Column(name = "audit_date", nullable = false)
    private LocalDate auditDate;

    @Column(name = "auditor_user_id")
    private Long auditorUserId;

    @Column(name = "auditor_external_party", length = 255)
    private String auditorExternalParty;

    @Column(name = "methodology_en", columnDefinition = "NVARCHAR(MAX)")
    private String methodologyEn;

    @Column(name = "methodology_ar", columnDefinition = "NVARCHAR(MAX)")
    private String methodologyAr;

    @Column(name = "findings_en", columnDefinition = "NVARCHAR(MAX)")
    private String findingsEn;

    @Column(name = "findings_ar", columnDefinition = "NVARCHAR(MAX)")
    private String findingsAr;

    /** PASSED/PASSED_WITH_CONDITIONS/FAILED/REMEDIATED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "metrics_json", columnDefinition = "NVARCHAR(MAX)")
    private String metricsJson;

    @Column(name = "capa_task_id")
    private Long capaTaskId;
}
