package com.tss.grc.module.privacy.extensions.dto;

import com.tss.grc.module.privacy.extensions.entity.CrossBorderTransfer;
import com.tss.grc.module.privacy.extensions.entity.DisposalCertificate;
import com.tss.grc.module.privacy.extensions.entity.PrivacyNotice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Wire types for the three PDPL extension modules. Grouped in one file
 * because they're tightly cohesive (all bound to the same V32 migration
 * + shared {@code privacy} schema + co-owned by the DPO).
 */
public final class PdplExtensionDtos {

    private PdplExtensionDtos() { /* static holder */ }

    // ─── Cross-border transfer ─────────────────────────────────────────

    public record CrossBorderResponse(
            Long id,
            String ropaCode, String ropaTitleEn,
            String sourceCountry, String destinationCountry,
            String destinationOrgName, String destinationOrgRole,
            String dataCategories, Long dataSubjectsCountEst,
            String transferMechanism, String safeguardsEn, String safeguardsAr,
            String justificationEn, String justificationAr,
            LocalDateTime residencyCheckAt, String residencyCheckBy, String residencyCheckOutcome,
            String status, String rejectionReason,
            LocalDateTime approvedAt, Long approvedBy, String approverName,
            LocalDateTime effectiveFrom, LocalDateTime expiryDate,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static CrossBorderResponse from(CrossBorderTransfer t) {
            return new CrossBorderResponse(
                    t.getId(),
                    t.getRopaCode(), t.getRopaTitleEn(),
                    t.getSourceCountry(), t.getDestinationCountry(),
                    t.getDestinationOrgName(), t.getDestinationOrgRole(),
                    t.getDataCategories(), t.getDataSubjectsCountEst(),
                    t.getTransferMechanism(), t.getSafeguardsEn(), t.getSafeguardsAr(),
                    t.getJustificationEn(), t.getJustificationAr(),
                    t.getResidencyCheckAt(), t.getResidencyCheckBy(), t.getResidencyCheckOutcome(),
                    t.getStatus(), t.getRejectionReason(),
                    t.getApprovedAt(), t.getApprovedBy(), t.getApproverName(),
                    t.getEffectiveFrom(), t.getExpiryDate(),
                    t.getCreatedAt(), t.getUpdatedAt()
            );
        }
    }

    public record CrossBorderCreate(
            @NotBlank @Size(max = 64) String ropaCode,
            @Size(max = 255) String ropaTitleEn,
            @NotBlank @Size(max = 3) String destinationCountry,
            @NotBlank @Size(max = 255) String destinationOrgName,
            @Size(max = 50) String destinationOrgRole,
            @NotBlank String dataCategories,
            Long dataSubjectsCountEst,
            @NotBlank @Size(max = 30) String transferMechanism,
            String safeguardsEn, String safeguardsAr,
            @NotBlank String justificationEn,
            String justificationAr,
            LocalDateTime effectiveFrom, LocalDateTime expiryDate
    ) { }

    public record AssessRequest(
            LocalDateTime residencyCheckAt,
            String residencyCheckBy,
            @NotBlank @Size(max = 20) String residencyCheckOutcome
    ) { }

    public record RejectRequest(
            @NotBlank @Size(max = 500) String rejectionReason
    ) { }

    // ─── Disposal certificate ──────────────────────────────────────────

    public record DisposalResponse(
            Long id,
            String relatedEntityType, String relatedEntityId, String relatedEntityLabel,
            String retentionRuleRef,
            String scopeDescriptionEn, String scopeDescriptionAr,
            Long recordsCountEst,
            String disposalMethod, String methodDetailsEn,
            String status,
            LocalDateTime scheduledDate,
            LocalDateTime executedAt, Long executedBy, String executorName,
            LocalDateTime verifiedAt, Long verifiedBy, String verifierName,
            String evidenceRef, Long signatureTxId,
            String ownerRole, String ownerName,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static DisposalResponse from(DisposalCertificate d) {
            return new DisposalResponse(
                    d.getId(),
                    d.getRelatedEntityType(), d.getRelatedEntityId(), d.getRelatedEntityLabel(),
                    d.getRetentionRuleRef(),
                    d.getScopeDescriptionEn(), d.getScopeDescriptionAr(),
                    d.getRecordsCountEst(),
                    d.getDisposalMethod(), d.getMethodDetailsEn(),
                    d.getStatus(),
                    d.getScheduledDate(),
                    d.getExecutedAt(), d.getExecutedBy(), d.getExecutorName(),
                    d.getVerifiedAt(), d.getVerifiedBy(), d.getVerifierName(),
                    d.getEvidenceRef(), d.getSignatureTxId(),
                    d.getOwnerRole(), d.getOwnerName(),
                    d.getCreatedAt(), d.getUpdatedAt()
            );
        }
    }

    public record DisposalCreate(
            @NotBlank @Size(max = 50) String relatedEntityType,
            @NotBlank @Size(max = 120) String relatedEntityId,
            @Size(max = 255) String relatedEntityLabel,
            @Size(max = 120) String retentionRuleRef,
            @NotBlank String scopeDescriptionEn,
            String scopeDescriptionAr,
            Long recordsCountEst,
            @NotBlank @Size(max = 20) String disposalMethod,
            String methodDetailsEn,
            LocalDateTime scheduledDate,
            @Size(max = 100) String ownerRole,
            @Size(max = 255) String ownerName
    ) { }

    public record DisposalCompleteRequest(
            String executorName,
            @Size(max = 120) String evidenceRef
    ) { }

    public record DisposalVerifyRequest(
            String verifierName,
            Long signatureTxId
    ) { }

    // ─── Privacy notice ────────────────────────────────────────────────

    public record NoticeResponse(
            Long id,
            String noticeCode, Integer version,
            String titleEn, String titleAr,
            String purposeEn, String purposeAr,
            String dataCategories, String recipients,
            String retentionPeriodEn, String retentionPeriodAr,
            String dpoContact, String lawfulBasis,
            String bodyEn, String bodyAr,
            String linkedRopaCodes,
            String status,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
            LocalDateTime publishedAt, Long publishedBy,
            LocalDateTime archivedAt, Long archivedBy, String archiveReasonEn,
            Long supersedesId,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static NoticeResponse from(PrivacyNotice n) {
            return new NoticeResponse(
                    n.getId(),
                    n.getNoticeCode(), n.getVersion(),
                    n.getTitleEn(), n.getTitleAr(),
                    n.getPurposeEn(), n.getPurposeAr(),
                    n.getDataCategories(), n.getRecipients(),
                    n.getRetentionPeriodEn(), n.getRetentionPeriodAr(),
                    n.getDpoContact(), n.getLawfulBasis(),
                    n.getBodyEn(), n.getBodyAr(),
                    n.getLinkedRopaCodes(),
                    n.getStatus(),
                    n.getEffectiveFrom(), n.getEffectiveTo(),
                    n.getPublishedAt(), n.getPublishedBy(),
                    n.getArchivedAt(), n.getArchivedBy(), n.getArchiveReasonEn(),
                    n.getSupersedesId(),
                    n.getCreatedAt(), n.getUpdatedAt()
            );
        }
    }

    public record NoticeCreate(
            @NotBlank @Size(max = 64) String noticeCode,
            @NotBlank @Size(max = 255) String titleEn,
            @NotBlank @Size(max = 255) String titleAr,
            @NotBlank String purposeEn,
            String purposeAr,
            String dataCategories, String recipients,
            String retentionPeriodEn, String retentionPeriodAr,
            @Size(max = 255) String dpoContact,
            @Size(max = 50) String lawfulBasis,
            @NotBlank String bodyEn,
            String bodyAr,
            String linkedRopaCodes,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo
    ) { }

    public record NoticeUpdate(
            @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            String purposeEn, String purposeAr,
            String dataCategories, String recipients,
            String retentionPeriodEn, String retentionPeriodAr,
            @Size(max = 255) String dpoContact,
            @Size(max = 50) String lawfulBasis,
            String bodyEn, String bodyAr,
            String linkedRopaCodes,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo
    ) { }

    /**
     * F2-185(b): cap enabled — 500 == {@code privacy.privacy_notices.archive_reason_en}. ⚠ F2-184's
     * filing guessed this column was 500 against a 1000 cap and flagged it as the one case where the
     * annotation would be LOOSER than the column; measured, cap and column agree exactly. A recorded
     * guess is not a measurement, however carefully it is hedged.
     */
    public record NoticeArchiveRequest(@Size(max = 500) String archiveReasonEn) { }
}
