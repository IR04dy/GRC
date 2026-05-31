package com.tss.grc.module.data_officer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Sprint 33 — Data Officer Toolkit DTOs. Records mirror entity shape for
 *  readability; create/transition requests carry only the fields the
 *  state machine reads. */
public final class DataOfficerDtos {
    private DataOfficerDtos() {}

    // ─── Generic timeline event ────────────────────────────────────────
    public record EventResponse(
        Long id, String entityType, Long entityId,
        LocalDateTime occurredAt, String eventType,
        Long actorUserId, String statusAfter,
        String noteEn, String noteAr
    ) {}

    // ─── 1. DSR ────────────────────────────────────────────────────────
    public record CreateDsrRequest(
        @NotBlank String reference, @NotBlank String requestType,
        String channel,
        String requesterNameEn, String requesterNameAr,
        String requesterNationalId, String requesterEmail, String requesterPhone,
        String requestSummaryEn, String requestSummaryAr,
        Long assignedUserId
    ) {}
    public record TransitionDsrRequest(
        @NotBlank String toStatus,
        String idVerificationMethod,
        String linkedSystemsJson,
        String responseTextEn, String responseTextAr,
        String rejectionReasonEn, String rejectionReasonAr,
        String rejectionBasis,
        Long responseAttachmentEvidenceId
    ) {}
    public record DsrResponse(
        Long id, String reference, String requestType, String channel,
        String requesterNameEn, String requesterNameAr,
        String requesterEmail, String requesterPhone,
        String requestSummaryEn, String requestSummaryAr,
        String status,
        LocalDateTime receivedAt, LocalDateTime slaDeadline,
        LocalDateTime idVerifiedAt, String idVerificationMethod,
        LocalDateTime fulfilledAt,
        String rejectionReasonEn, String rejectionReasonAr, String rejectionBasis,
        Long assignedUserId,
        String responseTextEn, String responseTextAr,
        Long responseAttachmentEvidenceId,
        Long hoursLeft
    ) {}

    // ─── 2. Consent ────────────────────────────────────────────────────
    public record CreateConsentRequest(
        @NotBlank String reference, String dataSubjectId,
        @NotBlank String purposeCode,
        String purposeLabelEn, String purposeLabelAr,
        Long noticeVersionId, String channel,
        String consentTextEn, String consentTextAr,
        LocalDateTime grantedAt, String grantedEvidence,
        LocalDateTime validUntil, String legalBasis,
        Boolean isSpecialCategory
    ) {}
    public record WithdrawConsentRequest(@NotBlank String reason) {}
    public record ConsentResponse(
        Long id, String reference, String dataSubjectId,
        String purposeCode, String purposeLabelEn, String purposeLabelAr,
        Long noticeVersionId, String channel,
        String consentTextEn, String consentTextAr,
        LocalDateTime grantedAt, String grantedEvidence,
        LocalDateTime withdrawnAt, String withdrawnReason,
        LocalDateTime validUntil, String status, String legalBasis,
        Boolean isSpecialCategory
    ) {}

    // ─── 3. DPIA ───────────────────────────────────────────────────────
    public record CreateDpiaRequest(
        @NotBlank String reference,
        @NotBlank String titleEn, @NotBlank String titleAr,
        String processingDescriptionEn, String processingDescriptionAr,
        @NotBlank String triggerReason, Boolean isHighRisk,
        Long dataOwnerUserId, Long dpoUserId,
        Long linkedProcessingActivityId
    ) {}
    public record TransitionDpiaRequest(
        @NotBlank String toStatus,
        String dpoOpinionEn, String dpoOpinionAr,
        String decisionRationaleEn, String decisionRationaleAr,
        String conditionsEn, String conditionsAr,
        LocalDate nextReviewDue
    ) {}
    public record DpiaResponse(
        Long id, String reference,
        String titleEn, String titleAr,
        String processingDescriptionEn, String processingDescriptionAr,
        String triggerReason, Boolean isHighRisk, String status,
        Long dataOwnerUserId, Long dpoUserId,
        LocalDateTime submittedAt,
        String dpoOpinionEn, String dpoOpinionAr, LocalDateTime dpoOpinionAt,
        LocalDateTime decidedAt,
        String decisionRationaleEn, String decisionRationaleAr,
        String conditionsEn, String conditionsAr,
        LocalDate nextReviewDue, Long linkedProcessingActivityId
    ) {}

    // ─── 4. Breach ─────────────────────────────────────────────────────
    public record CreateBreachRequest(
        @NotBlank String reference,
        @NotBlank String titleEn, @NotBlank String titleAr,
        @NotNull LocalDateTime detectedAt,
        String breachType, String cause, String severity,
        Long affectedRecordCount, Long affectedSubjectCount,
        String affectedCategoriesJson,
        Boolean isSpecialCategory, Boolean isCrossBorder,
        String descriptionEn, String descriptionAr,
        Long linkedIncidentId
    ) {}
    public record TransitionBreachRequest(
        @NotBlank String toStatus,
        String severity,
        String containmentActionsEn, String containmentActionsAr,
        String sdaiaReference,
        String subjectNotificationTemplateEn, String subjectNotificationTemplateAr,
        String closureSummaryEn, String closureSummaryAr
    ) {}
    public record BreachResponse(
        Long id, String reference,
        String titleEn, String titleAr,
        LocalDateTime detectedAt, LocalDateTime discoveredAt, LocalDateTime sdaiaDeadline,
        String status, String breachType, String cause, String severity,
        Long affectedRecordCount, Long affectedSubjectCount,
        String affectedCategoriesJson, Boolean isSpecialCategory, Boolean isCrossBorder,
        String descriptionEn, String descriptionAr,
        String containmentActionsEn, String containmentActionsAr,
        LocalDateTime sdaiaNotifiedAt, String sdaiaReference,
        LocalDateTime subjectsNotifiedAt,
        String subjectNotificationTemplateEn, String subjectNotificationTemplateAr,
        LocalDateTime closedAt, String closureSummaryEn, String closureSummaryAr,
        Long linkedIncidentId,
        Long hoursToSdaiaDeadline
    ) {}

    // ─── 5. Privacy Notice ─────────────────────────────────────────────
    public record CreateNoticeRequest(
        @NotBlank String reference, @NotBlank String versionLabel, @NotBlank String channel,
        @NotBlank String titleEn, @NotBlank String titleAr,
        @NotBlank String bodyEn, @NotBlank String bodyAr,
        LocalDate effectiveFrom, Long legalReviewUserId,
        Boolean requiresReConsent
    ) {}
    public record TransitionNoticeRequest(
        @NotBlank String toStatus,
        Long supersededById
    ) {}
    public record NoticeResponse(
        Long id, String reference, String versionLabel, String channel,
        String titleEn, String titleAr, String bodyEn, String bodyAr,
        String status, LocalDate effectiveFrom, LocalDateTime publishedAt,
        Long supersededById, Long legalReviewUserId, Boolean requiresReConsent
    ) {}

    // ─── 6. Retention ──────────────────────────────────────────────────
    public record CreateRetentionRuleRequest(
        @NotBlank String reference, @NotBlank String dataCategory,
        String descriptionEn, String descriptionAr,
        @NotNull Integer retentionMonths,
        String legalBasisEn, String legalBasisAr,
        String disposalMethod, Integer reviewCadenceMonths,
        String ownerRole, Long ownerUserId, String linkedAssetRef
    ) {}
    public record RetentionRuleResponse(
        Long id, String reference, String dataCategory,
        String descriptionEn, String descriptionAr, Integer retentionMonths,
        String legalBasisEn, String legalBasisAr,
        String disposalMethod, Integer reviewCadenceMonths,
        LocalDate nextReviewDue, String ownerRole, Long ownerUserId,
        String linkedAssetRef, String status
    ) {}
    public record CreateDisposalJobRequest(
        @NotBlank String reference, @NotNull Long retentionRuleId,
        @NotNull LocalDate scheduledFor
    ) {}
    public record TransitionDisposalRequest(
        @NotBlank String toStatus,
        Long recordsDisposed, String evidenceHash, String legalHoldReason
    ) {}
    public record DisposalJobResponse(
        Long id, String reference, Long retentionRuleId,
        LocalDate scheduledFor, LocalDateTime startedAt, LocalDateTime completedAt,
        String status, Long recordsDisposed, String evidenceHash, String legalHoldReason
    ) {}

    // ─── 7. NDR ────────────────────────────────────────────────────────
    public record CreateNdrRequest(
        @NotBlank String reference, @NotNull Integer cycleYear,
        String titleEn, String titleAr
    ) {}
    public record TransitionNdrRequest(
        @NotBlank String toStatus,
        Integer datasetCount,
        String submissionRef,
        String responseSummaryEn, String responseSummaryAr
    ) {}
    public record NdrResponse(
        Long id, String reference, Integer cycleYear,
        String titleEn, String titleAr, String status,
        Integer datasetCount, LocalDateTime submittedAt, String submissionRef,
        LocalDateTime acceptedAt, String responseSummaryEn, String responseSummaryAr
    ) {}

    // ─── 8. Catalog + Lineage ──────────────────────────────────────────
    public record CreateCatalogEntryRequest(
        @NotBlank String reference,
        @NotBlank String datasetNameEn, String datasetNameAr,
        String descriptionEn, String descriptionAr,
        String domain, String sourceSystem, String storageLocation,
        String classification, Boolean containsPii, Boolean containsSpecialCategory,
        String ownerRole, Long ownerUserId, Long stewardUserId, String custodianTeam,
        Long rowCountApprox, String refreshFrequency
    ) {}
    public record CatalogEntryResponse(
        Long id, String reference,
        String datasetNameEn, String datasetNameAr,
        String descriptionEn, String descriptionAr,
        String domain, String sourceSystem, String storageLocation,
        String classification, Boolean containsPii, Boolean containsSpecialCategory,
        String ownerRole, Long ownerUserId, Long stewardUserId, String custodianTeam,
        Long rowCountApprox, String refreshFrequency, LocalDateTime lastProfiledAt
    ) {}
    public record CreateLineageRequest(
        @NotNull Long sourceEntryId, @NotNull Long targetEntryId, String transformLabel
    ) {}
    public record LineageResponse(
        Long id, Long sourceEntryId, Long targetEntryId, String transformLabel
    ) {}

    // ─── 9. Quality ────────────────────────────────────────────────────
    public record CreateQualityRuleRequest(
        @NotBlank String reference, @NotNull Long catalogEntryId,
        @NotBlank String ruleNameEn, String ruleNameAr,
        @NotBlank String dimension, String ruleExpression,
        BigDecimal thresholdPct, String severity
    ) {}
    public record QualityRuleResponse(
        Long id, String reference, Long catalogEntryId,
        String ruleNameEn, String ruleNameAr,
        String dimension, String ruleExpression,
        BigDecimal thresholdPct, String severity, Boolean isActive
    ) {}
    public record RecordQualityRunRequest(
        @NotNull Long qualityRuleId,
        Long recordsChecked, Long recordsFailed,
        BigDecimal passPct, String band,
        String notesEn, String notesAr
    ) {}
    public record QualityRunResponse(
        Long id, Long qualityRuleId, LocalDateTime runAt,
        Long recordsChecked, Long recordsFailed,
        BigDecimal passPct, String band, String notesEn, String notesAr
    ) {}

    // ─── 10. Ethics ────────────────────────────────────────────────────
    public record CreateEthicsRequest(
        @NotBlank String reference, String subjectType, String subjectRef,
        @NotBlank String titleEn, @NotBlank String titleAr,
        String summaryEn, String summaryAr,
        String riskDimensionsJson
    ) {}
    public record TransitionEthicsRequest(
        @NotBlank String toStatus,
        LocalDateTime committeeMeetingAt,
        String opinionEn, String opinionAr,
        String conditionsEn, String conditionsAr,
        LocalDate nextReviewDue
    ) {}
    public record EthicsResponse(
        Long id, String reference, String subjectType, String subjectRef,
        String titleEn, String titleAr, String summaryEn, String summaryAr,
        LocalDateTime requestedAt, String status,
        String riskDimensionsJson, LocalDateTime committeeMeetingAt,
        String opinionEn, String opinionAr, String conditionsEn, String conditionsAr,
        LocalDateTime decidedAt, LocalDate nextReviewDue
    ) {}

    // ─── Unified Dashboard ─────────────────────────────────────────────
    public record DashboardResponse(
        long dsrOpen, long dsrOverdue,
        long consentActive, long consentWithdrawnLast30,
        long dpiaOpen, long dpiaApprovedYtd,
        long breachOpen, long breachOverdueSdaia,
        long noticesPublished, long noticesNeedingReConsent,
        long retentionDue, long disposalScheduled,
        long ndrCurrentYearStatusInt,
        long catalogEntries, long catalogPiiEntries,
        long qualityRules, long qualityRedRunsLast30,
        long ethicsOpen,
        Map<String, Long> dsrByStatus,
        Map<String, Long> breachBySeverity,
        Map<String, Long> dpiaByStatus,
        List<DsrResponse> recentDsr,
        List<BreachResponse> recentBreaches
    ) {}
}
