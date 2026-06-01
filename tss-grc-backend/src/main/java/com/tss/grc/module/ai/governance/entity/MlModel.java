package com.tss.grc.module.ai.governance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Inventory row for one AI/ML model — Sprint 11 AI/ML Model Registry.
 *
 * <p>Lifecycle state machine (enforced in {@code MlModelService}):
 * <pre>
 *   PROPOSED → DEVELOPMENT → VALIDATION → APPROVED_FOR_PROD
 *                                          ↓
 *                          MONITORING ←─ IN_PRODUCTION
 *                                ↓               ↑
 *                            REVISION ──────────┘
 *                                ↓
 *                         DECOMMISSIONED
 * </pre>
 *
 * <p>Gate at {@code APPROVED_FOR_PROD} when {@code risk_tier ∈
 * {CRITICAL, HIGH}}: validator + validation date + fairness audit +
 * PDPL DPIA + explainability doc all required.
 */
@Entity
@Table(schema = "ai_governance", name = "ml_model")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class MlModel extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "model_code", nullable = false, length = 64)
    private String modelCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** CLASSIFICATION/REGRESSION/CLUSTERING/NLP/COMPUTER_VISION/RECOMMENDATION/FORECASTING/LLM/GENERATIVE/RULE_BASED/HYBRID. */
    @Column(name = "model_type", nullable = false, length = 40)
    private String modelType;

    /** CREDIT_DECISION/FRAUD_DETECTION/AML_MONITORING/CUSTOMER_CHURN/DOCUMENT_CLASSIFICATION/KYC_OCR/CHATBOT/RECOMMENDER/RISK_SCORING/MARKETING_OPTIMIZATION/OPERATIONS_AUTOMATION. */
    @Column(name = "business_domain", nullable = false, length = 40)
    private String businessDomain;

    /** CRITICAL/HIGH/MEDIUM/LOW (per EU AI Act categorisation). */
    @Column(name = "risk_tier", nullable = false, length = 20)
    private String riskTier;

    /** PROPOSED/DEVELOPMENT/VALIDATION/APPROVED_FOR_PROD/IN_PRODUCTION/MONITORING/REVISION/DECOMMISSIONED. */
    @Column(name = "lifecycle_stage", nullable = false, length = 40)
    private String lifecycleStage = "PROPOSED";

    @Column(name = "version_label", length = 64)
    private String versionLabel;

    /** JSON array of {version, releasedAt, notes} entries — semver chronology. */
    @Column(name = "version_history_json", columnDefinition = "NVARCHAR(MAX)")
    private String versionHistoryJson;

    @Column(name = "model_owner_user_id", nullable = false)
    private Long modelOwnerUserId;

    @Column(name = "business_sponsor_user_id", nullable = false)
    private Long businessSponsorUserId;

    @Column(name = "validator_user_id")
    private Long validatorUserId;

    @Column(name = "validation_date")
    private LocalDate validationDate;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "decommission_date")
    private LocalDate decommissionDate;

    @Column(name = "decommission_reason_en", length = 1000)
    private String decommissionReasonEn;

    @Column(name = "decommission_reason_ar", length = 1000)
    private String decommissionReasonAr;

    // ─── Provenance ──────────────────────────────────────────────

    @Column(name = "training_data_provenance_en", columnDefinition = "NVARCHAR(MAX)")
    private String trainingDataProvenanceEn;

    @Column(name = "training_data_provenance_ar", columnDefinition = "NVARCHAR(MAX)")
    private String trainingDataProvenanceAr;

    @Column(name = "training_data_size_records")
    private Long trainingDataSizeRecords;

    @Column(name = "training_data_period_start")
    private LocalDate trainingDataPeriodStart;

    @Column(name = "training_data_period_end")
    private LocalDate trainingDataPeriodEnd;

    @Column(name = "feature_count")
    private Integer featureCount;

    @Column(name = "external_dataset_used", nullable = false)
    private Boolean externalDatasetUsed = Boolean.FALSE;

    @Column(name = "external_dataset_provenance", columnDefinition = "NVARCHAR(MAX)")
    private String externalDatasetProvenance;

    // ─── Tech ────────────────────────────────────────────────────

    /** TENSORFLOW/PYTORCH/SCIKIT_LEARN/XGBOOST/LIGHTGBM/HUGGINGFACE/AZURE_ML/AWS_SAGEMAKER/OPENAI_API/CUSTOM/HYBRID. */
    @Column(name = "framework", length = 40)
    private String framework;

    /** ON_PREM/AZURE_KSA/AWS_MIDDLE_EAST/GCP/EDGE/MIXED. */
    @Column(name = "hosting", length = 40)
    private String hosting;

    @Column(name = "inference_latency_ms")
    private Integer inferenceLatencyMs;

    @Column(name = "training_compute_hours", precision = 10, scale = 2)
    private BigDecimal trainingComputeHours;

    // ─── Human oversight (SDAIA "Humanity" principle · EU AI Act Art.14) ─
    //
    // Structured, queryable oversight mode — not buried in free text. A
    // HIGH+ tier model cannot be promoted to APPROVED_FOR_PROD with a
    // null or NONE oversight mode (enforced in MlModelService).

    /** HUMAN_IN_THE_LOOP / HUMAN_ON_THE_LOOP / HUMAN_IN_COMMAND / NONE. */
    @Column(name = "human_oversight", length = 30)
    private String humanOversight;

    @Column(name = "human_oversight_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String humanOversightNotesEn;

    @Column(name = "human_oversight_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String humanOversightNotesAr;

    // ─── Data residency & cross-border transfer (PDPL Art.29 · SDAIA) ────
    //
    // Explicit residency + transfer legal basis, rather than inferring
    // from `hosting`. For HIGH+ models with GCC/GLOBAL residency the
    // transfer basis must be set (and not NOT_ASSESSED) before production.

    /** KSA / GCC / GLOBAL. */
    @Column(name = "data_residency", length = 20)
    private String dataResidency;

    /** NONE_KSA_RESIDENT / SDAIA_ADEQUACY / EXPLICIT_CONSENT / BCR_SCC / VITAL_INTEREST / NOT_ASSESSED. */
    @Column(name = "cross_border_transfer_basis", length = 40)
    private String crossBorderTransferBasis;

    // ─── Compliance flags (tri-track regulator surface) ─────────

    @Column(name = "has_pdpl_dpia", nullable = false)
    private Boolean hasPdplDpia = Boolean.FALSE;

    @Column(name = "pdpl_dpia_id")
    private Long pdplDpiaId;

    @Column(name = "has_fairness_audit", nullable = false)
    private Boolean hasFairnessAudit = Boolean.FALSE;

    @Column(name = "last_fairness_audit_date")
    private LocalDate lastFairnessAuditDate;

    @Column(name = "has_explainability_doc", nullable = false)
    private Boolean hasExplainabilityDoc = Boolean.FALSE;

    @Column(name = "is_high_risk_eu_ai_act", nullable = false)
    private Boolean isHighRiskEuAiAct = Boolean.FALSE;

    /** COMPLIANT/PARTIAL/NON_COMPLIANT/NOT_ASSESSED. */
    @Column(name = "sdaia_ai_ethics_compliance", nullable = false, length = 20)
    private String sdaiaAiEthicsCompliance = "NOT_ASSESSED";

    /** COMPLIANT/PARTIAL/NON_COMPLIANT/NOT_ASSESSED. */
    @Column(name = "sama_ai_principles_compliance", nullable = false, length = 20)
    private String samaAiPrinciplesCompliance = "NOT_ASSESSED";
}
