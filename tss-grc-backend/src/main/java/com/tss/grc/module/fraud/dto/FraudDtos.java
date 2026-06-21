package com.tss.grc.module.fraud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request/response DTOs for the Sprint 14 Fraud Risk Management module.
 *
 * <p>Grouped in a single file to keep imports concise — the COI
 * module follows the same convention.
 */
public final class FraudDtos {
    private FraudDtos() {}

    // ─── Typology DTOs ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TypologyResponse {
        private Long id;
        private String code;
        private String nameEn;
        private String nameAr;
        private String descriptionEn;
        private String descriptionAr;
        private String samaCategoryCode;
        private String perpetratorOrigin;
        private String primaryChannel;
        private String typicalSeverity;
        private Boolean requiresStrFiling;
        private Boolean isActive;
    }

    // ─── Case lifecycle DTOs ──────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateCaseRequest {
        @NotNull(message = "{validation.fraud.typology.required}")
        private Long typologyId;

        @NotBlank @Size(max = 256)
        private String subjectEn;

        @NotBlank @Size(max = 256)
        private String subjectAr;

        private String narrativeEn;
        private String narrativeAr;

        @NotBlank @Pattern(regexp = "DETECTION_SYSTEM|CUSTOMER_REPORT|INTERNAL_REPORT|AUDIT_FINDING|EXTERNAL_PARTY|OTHER")
        private String detectionSource;

        @Size(max = 128)
        private String detectionReference;

        private LocalDate incidentDate;

        @PositiveOrZero
        private BigDecimal estimatedLossSar;

        @PositiveOrZero
        private Integer affectedCustomerCount;

        /** Optional — investigator picked at create time. */
        private Long assignedInvestigatorUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TriageRequest {
        @NotBlank @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW")
        private String severity;

        /** {@code true} → move to INVESTIGATING; {@code false} → FALSE_POSITIVE. */
        @NotNull
        private Boolean proceedToInvestigation;

        @Size(max = 4000)
        private String triageNotesEn;

        @Size(max = 4000)
        private String triageNotesAr;

        private Long assignedInvestigatorUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InvestigationUpdateRequest {
        @NotBlank @Pattern(regexp = "EVIDENCE_RECORDED|FINDING_RECORDED|STR_FILED|LAW_ENFORCEMENT_NOTIFIED|RECOVERY_RECORDED")
        private String updateType;

        @Size(max = 4000)
        private String noteEn;

        @Size(max = 4000)
        private String noteAr;

        @Size(max = 512)
        private String evidenceReference;

        /** When {@code RECOVERY_RECORDED}: the SAR amount recovered. */
        @PositiveOrZero
        private BigDecimal recoveredAmountSar;

        /** When {@code STR_FILED}: the SAFIU/GIB reference. */
        @Size(max = 100)
        private String strReference;

        /** When {@code LAW_ENFORCEMENT_NOTIFIED}: the CID/MHRSD reference. */
        @Size(max = 100)
        private String lawEnforcementReference;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OutcomeRequest {
        @NotBlank @Pattern(regexp = "CONFIRMED|CLEARED|FALSE_POSITIVE")
        private String outcome;

        @PositiveOrZero
        private BigDecimal confirmedLossSar;

        @Size(max = 4000)
        private String findingsEn;

        @Size(max = 4000)
        private String findingsAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CloseCaseRequest {
        @NotBlank @Size(max = 4000)
        private String closureNotesEn;

        @NotBlank @Size(max = 4000)
        private String closureNotesAr;
    }

    // ─── Response shapes ──────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CaseResponse {
        private Long id;
        private String externalReference;
        private Long typologyId;
        private String typologyCodeSnapshot;
        private String typologyNameEn;
        private String typologyNameAr;
        private String subjectEn;
        private String subjectAr;
        private String narrativeEn;
        private String narrativeAr;
        private String status;
        private String severity;
        private String detectionSource;
        private String detectionReference;
        private LocalDate incidentDate;
        private LocalDateTime detectedAt;
        private LocalDateTime reportedAt;
        private BigDecimal estimatedLossSar;
        private BigDecimal confirmedLossSar;
        private BigDecimal recoveredAmountSar;
        private String lossCurrency;
        private Integer affectedCustomerCount;
        private Long assignedInvestigatorUserId;
        private LocalDateTime triagedAt;
        private Long triagedByUserId;
        private LocalDateTime investigationStartedAt;
        private LocalDateTime outcomeSetAt;
        private Long outcomeSetByUserId;
        private LocalDateTime closedAt;
        private Long closedByUserId;
        private String closureNotesEn;
        private String closureNotesAr;
        private Boolean strFiled;
        private String strReference;
        private LocalDateTime strFiledAt;
        private Boolean lawEnforcementEngaged;
        private String lawEnforcementReference;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CaseUpdateResponse {
        private Long id;
        private Long caseId;
        private String updateType;
        private LocalDateTime occurredAt;
        private Long actorUserId;
        private String actorName;
        private String noteEn;
        private String noteAr;
        private String statusAfter;
        private String evidenceReference;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DashboardResponse {
        private long openCases;
        private long detectedCount;
        private long investigatingCount;
        private long confirmedCount;
        private long closedCount;
        private BigDecimal confirmedLossYtdSar;
        private List<CaseResponse> recentCases;
    }
}
