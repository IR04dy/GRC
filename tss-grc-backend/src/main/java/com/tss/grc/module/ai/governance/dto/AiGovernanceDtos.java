package com.tss.grc.module.ai.governance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.entity.MlModelAudit;
import com.tss.grc.module.ai.governance.entity.MlModelDriftObservation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request / response DTOs for the AI/ML Model Registry REST surface.
 */
public final class AiGovernanceDtos {

    private AiGovernanceDtos() {}

    // ─── Model header ─────────────────────────────────────────────

    public record MlModelRequest(
            String modelCode,
            @NotBlank @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            String descriptionEn,
            String descriptionAr,
            @NotBlank @Size(max = 40) String modelType,
            @NotBlank @Size(max = 40) String businessDomain,
            @NotBlank @Size(max = 20) String riskTier,
            @Size(max = 64) String versionLabel,
            String versionHistoryJson,
            @NotNull Long modelOwnerUserId,
            @NotNull Long businessSponsorUserId,
            Long validatorUserId,
            LocalDate validationDate,
            LocalDate productionDate,
            // Provenance.
            String trainingDataProvenanceEn,
            String trainingDataProvenanceAr,
            Long trainingDataSizeRecords,
            LocalDate trainingDataPeriodStart,
            LocalDate trainingDataPeriodEnd,
            Integer featureCount,
            Boolean externalDatasetUsed,
            String externalDatasetProvenance,
            // Tech.
            @Size(max = 40) String framework,
            @Size(max = 40) String hosting,
            Integer inferenceLatencyMs,
            BigDecimal trainingComputeHours,
            // Human oversight (SDAIA Humanity · EU AI Act Art.14).
            @Size(max = 30) String humanOversight,
            String humanOversightNotesEn,
            String humanOversightNotesAr,
            // Data residency & cross-border transfer (PDPL Art.29).
            @Size(max = 20) String dataResidency,
            @Size(max = 40) String crossBorderTransferBasis,
            // Compliance flags.
            Boolean hasPdplDpia,
            Long pdplDpiaId,
            Boolean hasFairnessAudit,
            LocalDate lastFairnessAuditDate,
            Boolean hasExplainabilityDoc,
            Boolean isHighRiskEuAiAct,
            @Size(max = 20) String sdaiaAiEthicsCompliance,
            @Size(max = 20) String samaAiPrinciplesCompliance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MlModelResponse(
            Long id,
            UUID externalId,
            String modelCode,
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            String modelType,
            String businessDomain,
            String riskTier,
            String lifecycleStage,
            String versionLabel,
            String versionHistoryJson,
            Long modelOwnerUserId,
            Long businessSponsorUserId,
            Long validatorUserId,
            LocalDate validationDate,
            LocalDate productionDate,
            LocalDate decommissionDate,
            String decommissionReasonEn,
            String decommissionReasonAr,
            String trainingDataProvenanceEn,
            String trainingDataProvenanceAr,
            Long trainingDataSizeRecords,
            LocalDate trainingDataPeriodStart,
            LocalDate trainingDataPeriodEnd,
            Integer featureCount,
            Boolean externalDatasetUsed,
            String externalDatasetProvenance,
            String framework,
            String hosting,
            Integer inferenceLatencyMs,
            BigDecimal trainingComputeHours,
            String humanOversight,
            String humanOversightNotesEn,
            String humanOversightNotesAr,
            String dataResidency,
            String crossBorderTransferBasis,
            Boolean hasPdplDpia,
            Long pdplDpiaId,
            Boolean hasFairnessAudit,
            LocalDate lastFairnessAuditDate,
            Boolean hasExplainabilityDoc,
            Boolean isHighRiskEuAiAct,
            String sdaiaAiEthicsCompliance,
            String samaAiPrinciplesCompliance,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static MlModelResponse from(MlModel m) {
            return new MlModelResponse(
                    m.getId(), m.getExternalId(), m.getModelCode(),
                    m.getTitleEn(), m.getTitleAr(),
                    m.getDescriptionEn(), m.getDescriptionAr(),
                    m.getModelType(), m.getBusinessDomain(),
                    m.getRiskTier(), m.getLifecycleStage(),
                    m.getVersionLabel(), m.getVersionHistoryJson(),
                    m.getModelOwnerUserId(), m.getBusinessSponsorUserId(),
                    m.getValidatorUserId(), m.getValidationDate(),
                    m.getProductionDate(),
                    m.getDecommissionDate(), m.getDecommissionReasonEn(), m.getDecommissionReasonAr(),
                    m.getTrainingDataProvenanceEn(), m.getTrainingDataProvenanceAr(),
                    m.getTrainingDataSizeRecords(),
                    m.getTrainingDataPeriodStart(), m.getTrainingDataPeriodEnd(),
                    m.getFeatureCount(), m.getExternalDatasetUsed(), m.getExternalDatasetProvenance(),
                    m.getFramework(), m.getHosting(),
                    m.getInferenceLatencyMs(), m.getTrainingComputeHours(),
                    m.getHumanOversight(), m.getHumanOversightNotesEn(), m.getHumanOversightNotesAr(),
                    m.getDataResidency(), m.getCrossBorderTransferBasis(),
                    m.getHasPdplDpia(), m.getPdplDpiaId(),
                    m.getHasFairnessAudit(), m.getLastFairnessAuditDate(),
                    m.getHasExplainabilityDoc(), m.getIsHighRiskEuAiAct(),
                    m.getSdaiaAiEthicsCompliance(), m.getSamaAiPrinciplesCompliance(),
                    m.getCreatedAt(), m.getUpdatedAt()
            );
        }
    }

    // ─── Lifecycle / decommission ─────────────────────────────────

    public record LifecycleTransitionRequest(
            @NotBlank @Size(max = 40) String toStage
    ) {}

    public record DecommissionRequest(
            @NotBlank String reasonEn,
            String reasonAr,
            LocalDate decommissionDate
    ) {}

    // ─── Audit ────────────────────────────────────────────────────

    public record MlModelAuditRequest(
            @NotBlank @Size(max = 40) String auditType,
            @NotNull LocalDate auditDate,
            Long auditorUserId,
            @Size(max = 255) String auditorExternalParty,
            String methodologyEn,
            String methodologyAr,
            String findingsEn,
            String findingsAr,
            @NotBlank @Size(max = 20) String status,
            String metricsJson,
            Long capaTaskId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MlModelAuditResponse(
            Long id,
            UUID externalId,
            Long modelId,
            String auditType,
            LocalDate auditDate,
            Long auditorUserId,
            String auditorExternalParty,
            String methodologyEn,
            String methodologyAr,
            String findingsEn,
            String findingsAr,
            String status,
            String metricsJson,
            Long capaTaskId,
            LocalDateTime createdAt
    ) {
        public static MlModelAuditResponse from(MlModelAudit a) {
            return new MlModelAuditResponse(
                    a.getId(), a.getExternalId(), a.getModelId(),
                    a.getAuditType(), a.getAuditDate(),
                    a.getAuditorUserId(), a.getAuditorExternalParty(),
                    a.getMethodologyEn(), a.getMethodologyAr(),
                    a.getFindingsEn(), a.getFindingsAr(),
                    a.getStatus(), a.getMetricsJson(), a.getCapaTaskId(),
                    a.getCreatedAt()
            );
        }
    }

    // ─── Drift ────────────────────────────────────────────────────

    public record DriftObservationRequest(
            LocalDateTime observedAt,
            @NotBlank @Size(max = 40) String observationType,
            @NotBlank @Size(max = 20) String severity,
            @NotBlank @Size(max = 255) String metricName,
            @NotNull BigDecimal metricValue,
            BigDecimal baselineValue,
            BigDecimal driftThreshold,
            String notesEn,
            String notesAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DriftObservationResponse(
            Long id,
            UUID externalId,
            Long modelId,
            LocalDateTime observedAt,
            String observationType,
            String severity,
            String metricName,
            BigDecimal metricValue,
            BigDecimal baselineValue,
            BigDecimal driftThreshold,
            String notesEn,
            String notesAr,
            Boolean isAcknowledged,
            LocalDateTime acknowledgedAt,
            Long acknowledgedByUserId,
            Long triggeredIssueId,
            LocalDateTime createdAt
    ) {
        public static DriftObservationResponse from(MlModelDriftObservation d) {
            return new DriftObservationResponse(
                    d.getId(), d.getExternalId(), d.getModelId(),
                    d.getObservedAt(), d.getObservationType(), d.getSeverity(),
                    d.getMetricName(), d.getMetricValue(),
                    d.getBaselineValue(), d.getDriftThreshold(),
                    d.getNotesEn(), d.getNotesAr(),
                    d.getIsAcknowledged(), d.getAcknowledgedAt(),
                    d.getAcknowledgedByUserId(),
                    d.getTriggeredIssueId(),
                    d.getCreatedAt()
            );
        }
    }
}
