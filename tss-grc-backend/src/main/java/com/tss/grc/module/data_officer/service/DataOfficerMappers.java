package com.tss.grc.module.data_officer.service;

import com.tss.grc.module.data_officer.dto.DataOfficerDtos.*;
import com.tss.grc.module.data_officer.entity.Entities.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity → response-DTO mappers for the Data Officer toolkit.
 *
 * <p>Extracted verbatim from the former {@code DataOfficerService} god-class
 * (QUAL-ESG-001). Pure functions with no state, so they live in one place and
 * are reused both by the per-domain services and by the dashboard aggregator.
 */
final class DataOfficerMappers {

    private DataOfficerMappers() {}

    static DsrResponse toDsr(DsrRequest d) {
        Long hoursLeft = null;
        if (d.getSlaDeadline() != null
                && !Set.of("FULFILLED","REJECTED","PARTIAL","CLOSED").contains(d.getStatus())) {
            hoursLeft = Duration.between(LocalDateTime.now(), d.getSlaDeadline()).toHours();
        }
        return new DsrResponse(
            d.getId(), d.getReference(), d.getRequestType(), d.getChannel(),
            d.getRequesterNameEn(), d.getRequesterNameAr(),
            d.getRequesterEmail(), d.getRequesterPhone(),
            d.getRequestSummaryEn(), d.getRequestSummaryAr(),
            d.getStatus(), d.getReceivedAt(), d.getSlaDeadline(),
            d.getIdVerifiedAt(), d.getIdVerificationMethod(),
            d.getFulfilledAt(), d.getRejectionReasonEn(), d.getRejectionReasonAr(),
            d.getRejectionBasis(), d.getAssignedUserId(),
            d.getResponseTextEn(), d.getResponseTextAr(),
            d.getResponseAttachmentEvidenceId(), hoursLeft);
    }

    static ConsentResponse toConsent(ConsentRecord c) {
        return new ConsentResponse(
            c.getId(), c.getReference(), c.getDataSubjectId(),
            c.getPurposeCode(), c.getPurposeLabelEn(), c.getPurposeLabelAr(),
            c.getNoticeVersionId(), c.getChannel(),
            c.getConsentTextEn(), c.getConsentTextAr(),
            c.getGrantedAt(), c.getGrantedEvidence(),
            c.getWithdrawnAt(), c.getWithdrawnReason(),
            c.getValidUntil(), c.getStatus(), c.getLegalBasis(),
            c.getIsSpecialCategory());
    }

    static DpiaResponse toDpia(Dpia d) {
        return new DpiaResponse(
            d.getId(), d.getReference(),
            d.getTitleEn(), d.getTitleAr(),
            d.getProcessingDescriptionEn(), d.getProcessingDescriptionAr(),
            d.getTriggerReason(), d.getIsHighRisk(), d.getStatus(),
            d.getDataOwnerUserId(), d.getDpoUserId(),
            d.getSubmittedAt(),
            d.getDpoOpinionEn(), d.getDpoOpinionAr(), d.getDpoOpinionAt(),
            d.getDecidedAt(),
            d.getDecisionRationaleEn(), d.getDecisionRationaleAr(),
            d.getConditionsEn(), d.getConditionsAr(),
            d.getNextReviewDue(), d.getLinkedProcessingActivityId());
    }

    static BreachResponse toBreach(Breach b) {
        Long hours = null;
        if (b.getSdaiaDeadline() != null && b.getSdaiaNotifiedAt() == null
                && !Set.of("CLOSED","FALSE_POSITIVE").contains(b.getStatus())) {
            hours = Duration.between(LocalDateTime.now(), b.getSdaiaDeadline()).toHours();
        }
        return new BreachResponse(
            b.getId(), b.getReference(),
            b.getTitleEn(), b.getTitleAr(),
            b.getDetectedAt(), b.getDiscoveredAt(), b.getSdaiaDeadline(),
            b.getStatus(), b.getBreachType(), b.getCause(), b.getSeverity(),
            b.getAffectedRecordCount(), b.getAffectedSubjectCount(),
            b.getAffectedCategoriesJson(), b.getIsSpecialCategory(), b.getIsCrossBorder(),
            b.getDescriptionEn(), b.getDescriptionAr(),
            b.getContainmentActionsEn(), b.getContainmentActionsAr(),
            b.getSdaiaNotifiedAt(), b.getSdaiaReference(),
            b.getSubjectsNotifiedAt(),
            b.getSubjectNotificationTemplateEn(), b.getSubjectNotificationTemplateAr(),
            b.getClosedAt(), b.getClosureSummaryEn(), b.getClosureSummaryAr(),
            b.getLinkedIncidentId(), hours);
    }

    static NoticeResponse toNotice(PrivacyNotice n) {
        return new NoticeResponse(
            n.getId(), n.getReference(), n.getVersionLabel(), n.getChannel(),
            n.getTitleEn(), n.getTitleAr(), n.getBodyEn(), n.getBodyAr(),
            n.getStatus(), n.getEffectiveFrom(), n.getPublishedAt(),
            n.getSupersededById(), n.getLegalReviewUserId(), n.getRequiresReConsent());
    }

    static RetentionRuleResponse toRetention(RetentionRule r) {
        return new RetentionRuleResponse(
            r.getId(), r.getReference(), r.getDataCategory(),
            r.getDescriptionEn(), r.getDescriptionAr(), r.getRetentionMonths(),
            r.getLegalBasisEn(), r.getLegalBasisAr(),
            r.getDisposalMethod(), r.getReviewCadenceMonths(),
            r.getNextReviewDue(), r.getOwnerRole(), r.getOwnerUserId(),
            r.getLinkedAssetRef(), r.getStatus());
    }

    static DisposalJobResponse toDisposal(DisposalJob j) {
        return new DisposalJobResponse(
            j.getId(), j.getReference(), j.getRetentionRuleId(),
            j.getScheduledFor(), j.getStartedAt(), j.getCompletedAt(),
            j.getStatus(), j.getRecordsDisposed(), j.getEvidenceHash(), j.getLegalHoldReason());
    }

    static NdrResponse toNdr(NdrSubmission n) {
        return new NdrResponse(
            n.getId(), n.getReference(), n.getCycleYear(),
            n.getTitleEn(), n.getTitleAr(), n.getStatus(),
            n.getDatasetCount(), n.getSubmittedAt(), n.getSubmissionRef(),
            n.getAcceptedAt(), n.getResponseSummaryEn(), n.getResponseSummaryAr());
    }

    static CatalogEntryResponse toCatalogEntry(CatalogEntry e) {
        return new CatalogEntryResponse(
            e.getId(), e.getReference(),
            e.getDatasetNameEn(), e.getDatasetNameAr(),
            e.getDescriptionEn(), e.getDescriptionAr(),
            e.getDomain(), e.getSourceSystem(), e.getStorageLocation(),
            e.getClassification(), e.getContainsPii(), e.getContainsSpecialCategory(),
            e.getOwnerRole(), e.getOwnerUserId(), e.getStewardUserId(), e.getCustodianTeam(),
            e.getRowCountApprox(), e.getRefreshFrequency(), e.getLastProfiledAt());
    }

    static QualityRuleResponse toQualityRule(QualityRule r) {
        return new QualityRuleResponse(
            r.getId(), r.getReference(), r.getCatalogEntryId(),
            r.getRuleNameEn(), r.getRuleNameAr(),
            r.getDimension(), r.getRuleExpression(),
            r.getThresholdPct(), r.getSeverity(), r.getIsActive());
    }

    static QualityRunResponse toQualityRun(QualityRun r) {
        return new QualityRunResponse(
            r.getId(), r.getQualityRuleId(), r.getRunAt(),
            r.getRecordsChecked(), r.getRecordsFailed(),
            r.getPassPct(), r.getBand(), r.getNotesEn(), r.getNotesAr());
    }

    static EthicsResponse toEthics(EthicsReview e) {
        return new EthicsResponse(
            e.getId(), e.getReference(), e.getSubjectType(), e.getSubjectRef(),
            e.getTitleEn(), e.getTitleAr(),
            e.getSummaryEn(), e.getSummaryAr(),
            e.getRequestedAt(), e.getStatus(),
            e.getRiskDimensionsJson(), e.getCommitteeMeetingAt(),
            e.getOpinionEn(), e.getOpinionAr(),
            e.getConditionsEn(), e.getConditionsAr(),
            e.getDecidedAt(), e.getNextReviewDue());
    }
}
