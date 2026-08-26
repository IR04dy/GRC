package com.tss.grc.module.consumer.complaints.dto;

import com.tss.grc.module.consumer.complaints.entity.Complaint;
import com.tss.grc.module.consumer.complaints.entity.ComplaintAggregateReport;
import com.tss.grc.module.consumer.complaints.entity.ComplaintUpdate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the Customer Complaints module — Sprint 13.
 *
 * <p>The {@link PublicTrackingResponse} type is intentionally NOT a
 * subset of {@link ComplaintResponse}; building it from a separate
 * shape ensures every new PII field added to the full response stays
 * out of the public surface unless the developer makes a deliberate
 * choice. Customer name initials are exposed (first letter only) — no
 * phone, no email, no national ID, no account fragments.
 */
public final class ComplaintDtos {

    private ComplaintDtos() {}

    // ─── Submit (public) ────────────────────────────────────────────

    public record SubmitComplaintRequest(
            // WEB | EMAIL | PHONE | BRANCH | SAMA_REFERRAL | SOCIAL_MEDIA
            @NotBlank String channel,
            // 13 category codes — see Complaint Javadoc
            @NotBlank String category,
            String subcategory,
            // LOW | MEDIUM | HIGH | CRITICAL (default MEDIUM)
            String severity,
            String customerNameEn,
            String customerNameAr,
            // NATIONAL_ID | IQAMA | PASSPORT | CR
            String customerIdType,
            @Size(min = 4, max = 4) String customerIdLast4,
            String customerPhone,
            String customerEmail,
            String customerCity,
            String productCode,
            @Size(min = 4, max = 4) String accountLast4,
            String descriptionEn,
            @NotBlank String descriptionAr
    ) {}

    /**
     * Returned ONCE on submit — anonymous caller captures the token
     * because they cannot ask the bank to recover it for them.
     */
    public record SubmitComplaintResponse(
            String complaintNo,
            UUID submissionToken,
            LocalDateTime ackSlaDueOn,
            LocalDateTime resolutionSlaDueOn,
            String retentionNoticeEn,
            String retentionNoticeAr
    ) {
        public static SubmitComplaintResponse from(Complaint c) {
            return new SubmitComplaintResponse(
                    c.getComplaintNo(),
                    c.getSubmissionToken(),
                    c.getAckSlaDueOn(),
                    c.getResolutionSlaDueOn(),
                    "Please save this tracking token. We cannot recover it for you.",
                    "يرجى حفظ رمز التتبّع — لا يمكننا استرجاعه لك."
            );
        }
    }

    // ─── Internal mutations ──────────────────────────────────────────

    public record AcknowledgeRequest(
            String noteEn,
            String noteAr
    ) {}

    public record TransitionRequest(
            // Target status from the lifecycle state machine
            @NotBlank String toStatus,
            String noteEn,
            String noteAr,
            Boolean customerVisible,
            // Optional resolution fields used on RESOLVED transition
            String resolutionSummaryEn,
            String resolutionSummaryAr,
            BigDecimal compensationAmountSar,
            String rootCauseCode,
            String lessonsLearned
    ) {}

    public record EscalateToSamaRequest(
            @NotBlank String samaReferenceNo,
            String noteEn,
            String noteAr
    ) {}

    public record AssignRequest(
            @NotBlank String assignedTo
    ) {}

    public record GenerateQuarterlyReportRequest(
            @NotBlank String reportPeriod
    ) {}

    // ─── Responses — internal (full record) ─────────────────────────

    // F2-383: submissionToken is deliberately NOT here — it is the anonymous submitter's tracking
    // credential and this DTO is returned to CONSUMER:READ staff. It stays on SubmitComplaintResponse
    // (returned once to the submitter) and off every staff shape; PublicTrackingResponse omits it too.
    public record ComplaintResponse(
            Long id,
            String complaintNo,
            String channel,
            String category,
            String subcategory,
            String severity,
            String status,
            String customerNameEn,
            String customerNameAr,
            String customerIdType,
            String customerIdLast4,
            String customerPhone,
            String customerEmail,
            String customerCity,
            String productCode,
            String accountLast4,
            String descriptionEn,
            String descriptionAr,
            LocalDateTime submittedOn,
            LocalDateTime acknowledgedOn,
            String acknowledgedBy,
            LocalDateTime ackSlaDueOn,
            LocalDateTime resolvedOn,
            String resolutionSummaryEn,
            String resolutionSummaryAr,
            String resolvedBy,
            LocalDateTime resolutionSlaDueOn,
            LocalDateTime escalatedToSamaOn,
            String samaReferenceNo,
            String assignedTo,
            String rootCauseCode,
            BigDecimal compensationAmountSar,
            String lessonsLearned
    ) {
        public static ComplaintResponse from(Complaint c) {
            return new ComplaintResponse(
                    c.getId(),
                    c.getComplaintNo(),
                    c.getChannel(),
                    c.getCategory(),
                    c.getSubcategory(),
                    c.getSeverity(),
                    c.getStatus(),
                    c.getCustomerNameEn(),
                    c.getCustomerNameAr(),
                    c.getCustomerIdType(),
                    c.getCustomerIdLast4(),
                    c.getCustomerPhone(),
                    c.getCustomerEmail(),
                    c.getCustomerCity(),
                    c.getProductCode(),
                    c.getAccountLast4(),
                    c.getDescriptionEn(),
                    c.getDescriptionAr(),
                    c.getSubmittedOn(),
                    c.getAcknowledgedOn(),
                    c.getAcknowledgedBy(),
                    c.getAckSlaDueOn(),
                    c.getResolvedOn(),
                    c.getResolutionSummaryEn(),
                    c.getResolutionSummaryAr(),
                    c.getResolvedBy(),
                    c.getResolutionSlaDueOn(),
                    c.getEscalatedToSamaOn(),
                    c.getSamaReferenceNo(),
                    c.getAssignedTo(),
                    c.getRootCauseCode(),
                    c.getCompensationAmountSar(),
                    c.getLessonsLearned()
            );
        }
    }

    public record ComplaintUpdateResponse(
            Long id,
            Long complaintId,
            String updateType,
            String fromStatus,
            String toStatus,
            String noteEn,
            String noteAr,
            String actor,
            Boolean customerVisible,
            LocalDateTime occurredOn
    ) {
        public static ComplaintUpdateResponse from(ComplaintUpdate u) {
            return new ComplaintUpdateResponse(
                    u.getId(), u.getComplaintId(), u.getUpdateType(),
                    u.getFromStatus(), u.getToStatus(),
                    u.getNoteEn(), u.getNoteAr(),
                    u.getActor(),
                    u.getCustomerVisible(),
                    u.getOccurredOn()
            );
        }
    }

    // ─── Public tracking (PII-stripped) ─────────────────────────────

    /**
     * What an anonymous caller sees on {@code /public/track/{token}}.
     * NO PII fields (phone, email, ID, account fragments). Customer
     * name is reduced to initials only — useful so the customer
     * recognises their own complaint, but useless for cross-referencing
     * other personal data.
     */
    public record PublicTrackingResponse(
            String complaintNo,
            String status,
            String category,
            String severity,
            String customerInitials,
            LocalDateTime submittedOn,
            LocalDateTime acknowledgedOn,
            LocalDateTime resolvedOn,
            LocalDateTime escalatedToSamaOn,
            List<PublicTimelineEntry> timeline
    ) {
        public static PublicTrackingResponse from(Complaint c, List<ComplaintUpdate> visibleUpdates) {
            String initials = initialsOf(c.getCustomerNameEn(), c.getCustomerNameAr());
            return new PublicTrackingResponse(
                    c.getComplaintNo(),
                    c.getStatus(),
                    c.getCategory(),
                    c.getSeverity(),
                    initials,
                    c.getSubmittedOn(),
                    c.getAcknowledgedOn(),
                    c.getResolvedOn(),
                    c.getEscalatedToSamaOn(),
                    visibleUpdates.stream().map(PublicTimelineEntry::from).toList()
            );
        }

        private static String initialsOf(String en, String ar) {
            String src = (en != null && !en.isBlank()) ? en : ar;
            if (src == null || src.isBlank()) return null;
            String[] parts = src.trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(parts.length, 3); i++) {
                if (!parts[i].isEmpty()) {
                    sb.append(parts[i].charAt(0)).append('.');
                }
            }
            return sb.toString();
        }
    }

    public record PublicTimelineEntry(
            String updateType,
            String toStatus,
            String noteEn,
            String noteAr,
            LocalDateTime occurredOn
    ) {
        public static PublicTimelineEntry from(ComplaintUpdate u) {
            return new PublicTimelineEntry(
                    u.getUpdateType(), u.getToStatus(),
                    u.getNoteEn(), u.getNoteAr(),
                    u.getOccurredOn());
        }
    }

    // ─── Quarterly report ────────────────────────────────────────────

    public record QuarterlyReportResponse(
            Long id,
            String reportPeriod,
            Integer totalReceived,
            Integer totalResolved,
            Integer totalEscalatedSama,
            BigDecimal avgResolutionDays,
            BigDecimal ackSlaBreachPct,
            BigDecimal resolutionSlaBreachPct,
            String topCategory,
            Integer topCategoryCount,
            BigDecimal compensationTotalSar,
            LocalDateTime generatedOn,
            String generatedBy
    ) {
        public static QuarterlyReportResponse from(ComplaintAggregateReport r) {
            return new QuarterlyReportResponse(
                    r.getId(),
                    r.getReportPeriod(),
                    r.getTotalReceived(),
                    r.getTotalResolved(),
                    r.getTotalEscalatedSama(),
                    r.getAvgResolutionDays(),
                    r.getAckSlaBreachPct(),
                    r.getResolutionSlaBreachPct(),
                    r.getTopCategory(),
                    r.getTopCategoryCount(),
                    r.getCompensationTotalSar(),
                    r.getGeneratedOn(),
                    r.getGeneratedBy()
            );
        }
    }
}
