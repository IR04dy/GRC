package com.tss.grc.module.sharia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Sprint 20 PR #1 — Sharia Governance DTOs. */
public final class ShariaDtos {
    private ShariaDtos() {}

    // ─── Scholar ───────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateScholarRequest {
        @NotBlank @Size(max = 64) private String reference;
        @NotBlank @Size(max = 256) private String fullNameEn;
        @NotBlank @Size(max = 256) private String fullNameAr;
        @Size(max = 32) private String ssbRole;
        private LocalDate termStart;
        private LocalDate termEnd;
        @Size(max = 4000) private String credentialsEn;
        @Size(max = 4000) private String credentialsAr;
        private BigDecimal annualFeeSar;
        @Size(max = 256) private String email;
        @Size(max = 32) private String phone;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateScholarRequest {
        @Size(max = 256) private String fullNameEn;
        @Size(max = 256) private String fullNameAr;
        @Size(max = 32) private String ssbRole;
        @Size(max = 16) private String status;
        private LocalDate termStart;
        private LocalDate termEnd;
        @Size(max = 4000) private String credentialsEn;
        @Size(max = 4000) private String credentialsAr;
        private BigDecimal annualFeeSar;
        @Size(max = 256) private String email;
        @Size(max = 32) private String phone;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DeclareCoiRequest {
        @Size(max = 4000) private String coiNotesEn;
        @Size(max = 4000) private String coiNotesAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ScholarResponse {
        private Long id;
        private String reference;
        private String fullNameEn;
        private String fullNameAr;
        private String ssbRole;
        private String status;
        private LocalDate termStart;
        private LocalDate termEnd;
        private String credentialsEn;
        private String credentialsAr;
        private LocalDate coiDeclaredAt;
        private String coiNotesEn;
        private String coiNotesAr;
        private BigDecimal annualFeeSar;
        // F2-459: `meetingsAttendedYtd` is deliberately ABSENT. Nothing in the platform writes
        // that column and nothing resets it, so publishing it shipped a frozen seed value as a
        // SAMA Shariah-governance attendance figure. Declaring a field the wire cannot honestly
        // fill is the shape that produced F2-211's em-dash-on-every-row defect.
        private String email;
        private String phone;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ─── Resolution ────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateResolutionRequest {
        @NotBlank @Size(max = 64) private String reference;
        @NotBlank @Size(max = 32) private String subjectArea;
        @NotBlank @Size(max = 512) private String titleEn;
        @NotBlank @Size(max = 512) private String titleAr;
        @Size(max = 8000) private String bodyEn;
        @Size(max = 8000) private String bodyAr;
        @Size(max = 8000) private String rationaleEn;
        @Size(max = 8000) private String rationaleAr;
        @Size(max = 256) private String signingScholars;
        private LocalDate effectiveDate;
        private Long supersedesResolutionId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionResolutionRequest {
        @NotBlank @Size(max = 16) private String toStatus;
        @Size(max = 4000) private String reasonEn;
        @Size(max = 4000) private String reasonAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ResolutionResponse {
        private Long id;
        private String reference;
        private String subjectArea;
        private String titleEn;
        private String titleAr;
        private String bodyEn;
        private String bodyAr;
        private String rationaleEn;
        private String rationaleAr;
        private String status;
        private LocalDateTime submittedAt;
        private LocalDateTime ssbReviewStartedAt;
        private LocalDateTime approvedAt;
        private LocalDateTime rejectedAt;
        private LocalDate effectiveDate;
        private Long supersedesResolutionId;
        private String signingScholars;
        private Long meetingId;
        private String rejectionReasonEn;
        private String rejectionReasonAr;
    }

    // ─── Audit Finding ─────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RaiseAuditFindingRequest {
        @NotBlank @Size(max = 64) private String reference;
        @Size(max = 64) private String auditCycleLabel;
        @NotBlank @Size(max = 12) private String severity;
        @NotBlank @Size(max = 512) private String titleEn;
        @NotBlank @Size(max = 512) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        @Size(max = 4000) private String rootCauseEn;
        @Size(max = 4000) private String rootCauseAr;
        @Size(max = 4000) private String remediationPlanEn;
        @Size(max = 4000) private String remediationPlanAr;
        private Long linkedResolutionId;
        @Size(max = 128) private String productLine;
        private LocalDate remediationDueAt;
        private Long assignedToUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionAuditFindingRequest {
        @NotBlank @Size(max = 16) private String toStatus;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AuditFindingResponse {
        private Long id;
        private String reference;
        private String auditCycleLabel;
        private String severity;
        private String status;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private String rootCauseEn;
        private String rootCauseAr;
        private String remediationPlanEn;
        private String remediationPlanAr;
        private Long linkedResolutionId;
        private String productLine;
        private LocalDateTime raisedAt;
        private LocalDate remediationDueAt;
        private LocalDateTime verifiedAt;
        private LocalDateTime closedAt;
        private Long assignedToUserId;
    }

    // ─── Non-Compliance Event ──────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DetectNcEventRequest {
        @NotBlank @Size(max = 64) private String reference;
        @NotBlank @Size(max = 12) private String severity;
        @NotBlank @Size(max = 512) private String titleEn;
        @NotBlank @Size(max = 512) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        @Size(max = 128) private String productLine;
        private Long linkedResolutionId;
        @Size(max = 32) private String detectionSource;
        @NotNull private BigDecimal snciAmountSar;
        private Long affectedCustomerCount;
        private Long assignedToUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PurifyNcEventRequest {
        @NotBlank @Size(max = 256) private String purificationDestination;
        @NotBlank @Size(max = 128) private String charityReceiptRef;
        @NotNull private LocalDate purifiedAt;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionNcEventRequest {
        @NotBlank @Size(max = 16) private String toStatus;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class NcEventResponse {
        private Long id;
        private String reference;
        private String status;
        private String severity;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private String productLine;
        private Long linkedResolutionId;
        private LocalDateTime detectedAt;
        private String detectionSource;
        private BigDecimal snciAmountSar;
        private String purificationDestination;
        private LocalDate purifiedAt;
        private String charityReceiptRef;
        private LocalDateTime closedAt;
        private Long affectedCustomerCount;
        private Long assignedToUserId;
    }

    // ─── Product Approval ──────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubmitProductRequest {
        @NotBlank @Size(max = 64) private String reference;
        @NotBlank @Size(max = 256) private String productNameEn;
        @NotBlank @Size(max = 256) private String productNameAr;
        @NotBlank @Size(max = 32) private String structure;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        private Long businessOwnerUserId;
        private Long shariaOfficerUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionProductRequest {
        @NotBlank @Size(max = 24) private String toStatus;
        private Long linkedResolutionId;
        @Size(max = 4000) private String reasonEn;
        @Size(max = 4000) private String reasonAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ProductResponse {
        private Long id;
        private String reference;
        private String productNameEn;
        private String productNameAr;
        private String structure;
        private String status;
        private String descriptionEn;
        private String descriptionAr;
        private Long linkedResolutionId;
        private LocalDateTime submittedAt;
        private LocalDateTime ssbReviewStartedAt;
        private LocalDateTime approvedProvisionalAt;
        private LocalDateTime approvedFinalAt;
        private LocalDateTime rejectedAt;
        private LocalDate launchedAt;
        private LocalDate decommissionedAt;
        private String rejectionReasonEn;
        private String rejectionReasonAr;
        private Long businessOwnerUserId;
        private Long shariaOfficerUserId;
    }

    // ─── Event ─────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EventResponse {
        private Long id;
        private Long scholarId;
        private Long resolutionId;
        private Long auditFindingId;
        private Long ncEventId;
        private Long productApprovalId;
        private LocalDateTime occurredAt;
        private String eventType;
        private Long actorUserId;
        private String actorName;
        private String statusAfter;
        private String noteEn;
        private String noteAr;
    }

    // ─── Dashboard ─────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DashboardResponse {
        private long activeScholars;
        private long termsExpiring90d;
        private long activeResolutions;
        private long resolutionsInReview;
        private long openFindingsHigh;
        private long openFindingsTotal;
        private long ncEventsPendingPurification;
        private BigDecimal snciOutstandingSar;
        private long productsInReview;
        private long productsLaunched;
        private Map<String, Long> resolutionsByStatus;
        private Map<String, Long> findingsBySeverity;
        private Map<String, Long> productsByStatus;
        private List<ResolutionResponse> recentResolutions;
        private List<AuditFindingResponse> openHighFindings;
        private List<NcEventResponse> openNcEvents;
    }
}
