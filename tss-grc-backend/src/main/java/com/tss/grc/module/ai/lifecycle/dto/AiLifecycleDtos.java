package com.tss.grc.module.ai.lifecycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTOs for Sprint 31 AI/ML Model Lifecycle module. */
public final class AiLifecycleDtos {
    private AiLifecycleDtos() {}

    public record CreateValidationRequest(
            @NotNull Long modelId, @NotBlank String reference,
            String validationType, Long validatorUserId, String validatorOrgLabel,
            LocalDate scheduledAt
    ) {}

    public record TransitionValidationRequest(
            @NotBlank String toStatus,
            String metricsJson, String ethicsScoresJson,
            BigDecimal fairnessDisparityPct,
            String findingsEn, String findingsAr,
            String conditionsEn, String conditionsAr,
            Long linkedCapaId
    ) {}

    public record ValidationResponse(
            Long id, Long modelId, String reference, String status,
            String validationType, Long validatorUserId, String validatorOrgLabel,
            LocalDate scheduledAt, LocalDateTime startedAt, LocalDateTime completedAt,
            String metricsJson, String ethicsScoresJson, BigDecimal fairnessDisparityPct,
            String findingsEn, String findingsAr,
            String conditionsEn, String conditionsAr, Long linkedCapaId
    ) {}

    public record CreateRetrainingRequest(
            @NotNull Long modelId, @NotBlank String reference,
            String triggerReason, String rationaleEn, String rationaleAr,
            String fromVersionLabel, String toVersionLabel,
            Long ownerUserId
    ) {}

    public record TransitionRetrainingRequest(
            @NotBlank String toStatus,
            String postDeployMetricsJson,
            String rollbackReasonEn, String rollbackReasonAr
    ) {}

    public record RetrainingResponse(
            Long id, Long modelId, String reference, String status,
            String triggerReason, String rationaleEn, String rationaleAr,
            String fromVersionLabel, String toVersionLabel,
            LocalDateTime approvedAt, LocalDateTime startedAt,
            LocalDate deployedAt, LocalDate rolledBackAt,
            String postDeployMetricsJson,
            String rollbackReasonEn, String rollbackReasonAr,
            Long ownerUserId
    ) {}

    public record CreateRecertificationRequest(
            @NotNull Long modelId, @NotBlank String reference,
            String cycleLabel, LocalDate dueDate
    ) {}

    public record TransitionRecertificationRequest(
            @NotBlank String toStatus,
            String decisionRationaleEn, String decisionRationaleAr,
            String conditionsEn, String conditionsAr,
            LocalDate nextDueDate, Long linkedValidationId
    ) {}

    public record RecertificationResponse(
            Long id, Long modelId, String reference, String cycleLabel, String status,
            LocalDate dueDate, LocalDateTime startedAt, LocalDateTime decidedAt,
            LocalDate nextDueDate,
            String decisionRationaleEn, String decisionRationaleAr,
            String conditionsEn, String conditionsAr,
            Long approverUserId, Long linkedValidationId
    ) {}

    public record EventResponse(
            Long id, Long modelId, Long validationId, Long retrainingId, Long recertificationId,
            LocalDateTime occurredAt, String eventType,
            Long actorUserId, String actorName, String statusAfter,
            String noteEn, String noteAr
    ) {}

    public record DashboardResponse(
            long validationsScheduled,
            long validationsInProgress,
            long validationsFailedOrConditions,
            long retrainingsInProgress,
            long retrainingsDeployedThisQuarter,
            long retrainingsRolledBack,
            long recertificationsOverdue,
            long recertificationsDueNext90d,
            Map<String, Long> validationsByStatus,
            Map<String, Long> retrainingsByStatus,
            Map<String, Long> recertificationsByStatus,
            List<ValidationResponse> recentValidations,
            List<RetrainingResponse> recentRetrainings,
            List<RecertificationResponse> overdueRecertifications
    ) {}
}
