package com.tss.grc.module.regreporting.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Sprint 16 PR #1 — Mandatory Regulatory Reporting Calendar DTOs. */
public final class RegReportingDtos {
    private RegReportingDtos() {}

    // ─── Obligation ────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateObligationRequest {
        @NotBlank @Size(max = 64)  private String code;
        @NotBlank @Size(max = 16)  private String regulator;       // SAMA | NCA | CMA | SDAIA | NCEMA | ZATCA | OTHER
        @NotBlank @Size(max = 16)  private String frequency;       // DAILY | WEEKLY | MONTHLY | QUARTERLY | SEMIANNUAL | ANNUAL | AD_HOC
        @NotBlank @Size(max = 256) private String titleEn;
        @NotBlank @Size(max = 256) private String titleAr;
        @Size(max = 4000)          private String descriptionEn;
        @Size(max = 4000)          private String descriptionAr;
        @NotNull @Min(0)           private Integer dueOffsetBusinessDays;
        @Size(max = 256)           private String statutoryBasis;
        @Size(max = 64)            private String ownerRole;
        @Size(max = 64)            private String reviewerRole;
        @Size(max = 256)           private String filingChannel;
        @Size(max = 64)            private String reminderLadder;   // e.g. "7,3,1"
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateObligationRequest {
        @Size(max = 256) private String titleEn;
        @Size(max = 256) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        @Min(0) private Integer dueOffsetBusinessDays;
        @Size(max = 256) private String statutoryBasis;
        @Size(max = 64) private String ownerRole;
        @Size(max = 64) private String reviewerRole;
        @Size(max = 256) private String filingChannel;
        @Size(max = 64) private String reminderLadder;
        private Boolean active;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ObligationResponse {
        private Long id;
        private String code;
        private String regulator;
        private String frequency;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private Integer dueOffsetBusinessDays;
        private String statutoryBasis;
        private String ownerRole;
        private String reviewerRole;
        private String filingChannel;
        private String reminderLadder;
        private Boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        /** Most recent filing's status (if any) — for catalog hover preview. */
        private String latestFilingStatus;
        private LocalDate latestFilingDueDate;
    }

    // ─── Filing ─────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GenerateFilingRequest {
        /** Period end — if null, computed from frequency and previous cycle. */
        private LocalDate periodEnd;
        /** Override due date — if null, computed from period end + offset. */
        private LocalDate dueDate;
        private Long preparerUserId;
        private Long reviewerUserId;
        private Long approverUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateFilingRequest {
        private LocalDate internalTargetDate;
        private Long preparerUserId;
        private Long reviewerUserId;
        private Long approverUserId;
        @Size(max = 4000) private String preparationNotesEn;
        @Size(max = 4000) private String preparationNotesAr;
        @Size(max = 512)  private String filingArtifactReference;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionFilingRequest {
        @NotBlank @Size(max = 24) private String toStatus;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubmitFilingRequest {
        @NotBlank @Size(max = 256) private String regulatorAckReference;
        @Size(max = 512) private String filingArtifactReference;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RejectFilingRequest {
        @NotBlank @Size(max = 4000) private String rejectionReasonEn;
        @NotBlank @Size(max = 4000) private String rejectionReasonAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExemptFilingRequest {
        @NotBlank @Size(max = 4000) private String exemptionReasonEn;
        @NotBlank @Size(max = 4000) private String exemptionReasonAr;
        @NotBlank @Size(max = 256) private String exemptionReference;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FilingResponse {
        private Long id;
        private Long obligationId;
        private String obligationCode;
        private String obligationTitleEn;
        private String obligationTitleAr;
        private String regulator;
        private String frequency;
        private String reference;
        private String status;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private LocalDate dueDate;
        private LocalDate internalTargetDate;
        private Long preparerUserId;
        private Long reviewerUserId;
        private Long approverUserId;
        private LocalDateTime submittedAt;
        private String regulatorAckReference;
        private LocalDateTime acknowledgedAt;
        private LocalDateTime closedAt;
        private String filingArtifactReference;
        private String preparationNotesEn;
        private String preparationNotesAr;
        private String rejectionReasonEn;
        private String rejectionReasonAr;
        private String exemptionReasonEn;
        private String exemptionReasonAr;
        private String exemptionReference;
        /** Derived: status not terminal AND dueDate &lt; today. */
        private Boolean overdue;
        /** Calendar-day distance to dueDate (negative = overdue). */
        private Long daysUntilDue;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FilingEventResponse {
        private Long id;
        private Long filingId;
        private LocalDateTime occurredAt;
        private String eventType;
        private Long actorUserId;
        private String actorName;
        private String statusAfter;
        private String noteEn;
        private String noteAr;
    }

    // ─── Dashboard + calendar ──────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DashboardResponse {
        private long activeObligationCount;
        private long inFlightFilingCount;
        private long dueWithin7Count;
        private long overdueCount;
        private long submittedThisQuarterCount;
        /** breakdown of in-flight filings by regulator. */
        private Map<String, Long> inFlightByRegulator;
        /** breakdown of in-flight filings by status. */
        private Map<String, Long> inFlightByStatus;
        private List<FilingResponse> upcoming;       // next 14 days
        private List<FilingResponse> overdueRecent;  // up to 10 most-overdue
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CalendarResponse {
        private LocalDate from;
        private LocalDate to;
        private List<FilingResponse> filings;
    }
}
