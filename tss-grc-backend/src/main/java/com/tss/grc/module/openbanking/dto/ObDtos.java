package com.tss.grc.module.openbanking.dto;

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

/** Sprint 19 PR #1 — Open Banking Compliance DTOs. */
public final class ObDtos {
    private ObDtos() {}

    // ─── TPP ───────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateTppRequest {
        @NotBlank @Size(max = 64) private String reference;
        @NotBlank @Size(max = 256) private String legalNameEn;
        @NotBlank @Size(max = 256) private String legalNameAr;
        @Size(max = 64) private String samaLicenceRef;
        @Size(max = 128) private String fapiClientId;
        @NotBlank @Size(max = 32) private String roles;
        @Size(max = 2) private String country;
        @Size(max = 128) private String certFingerprint;
        private LocalDate certExpiresAt;
        private Integer rateLimitPerSec;
        private Long accountManagerUserId;
        private Long complianceOfficerUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateTppRequest {
        @Size(max = 256) private String legalNameEn;
        @Size(max = 256) private String legalNameAr;
        @Size(max = 64) private String samaLicenceRef;
        @Size(max = 128) private String fapiClientId;
        @Size(max = 32) private String roles;
        @Size(max = 2) private String country;
        @Size(max = 128) private String certFingerprint;
        private LocalDate certExpiresAt;
        private Integer rateLimitPerSec;
        private Long accountManagerUserId;
        private Long complianceOfficerUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionTppRequest {
        @NotBlank @Size(max = 16) private String toStatus;
        @Size(max = 4000) private String reasonEn;
        @Size(max = 4000) private String reasonAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TppResponse {
        private Long id;
        private String reference;
        private String legalNameEn;
        private String legalNameAr;
        private String samaLicenceRef;
        private String fapiClientId;
        private String roles;
        private String country;
        private String status;
        private LocalDate onboardedAt;
        private LocalDateTime suspendedAt;
        private LocalDateTime revokedAt;
        private String suspensionReasonEn;
        private String suspensionReasonAr;
        private String certFingerprint;
        private LocalDate certExpiresAt;
        private Integer rateLimitPerSec;
        private Long accountManagerUserId;
        private Long complianceOfficerUserId;
        private Integer activeConsentCount;
        private Integer openIncidentCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ─── Consent ───────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GrantConsentRequest {
        @NotNull private Long tppId;
        @NotBlank @Size(max = 96) private String consentReference;
        @NotBlank @Size(max = 128) private String customerHash;
        @Size(max = 256) private String customerLabelEn;
        @Size(max = 256) private String customerLabelAr;
        @NotBlank @Size(max = 32) private String consentType;
        @NotBlank @Size(max = 512) private String scopes;
        private LocalDate expiresAt;
        private BigDecimal amountCapSar;
        private Integer frequencyWindowDays;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RevokeConsentRequest {
        @NotBlank @Size(max = 16) private String revokedBy;       // CUSTOMER | BANK | TPP
        @Size(max = 4000) private String reasonEn;
        @Size(max = 4000) private String reasonAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConsentResponse {
        private Long id;
        private Long tppId;
        private String consentReference;
        private String customerHash;
        private String customerLabelEn;
        private String customerLabelAr;
        private String consentType;
        private String scopes;
        private String status;
        private LocalDateTime grantedAt;
        private LocalDate expiresAt;
        private LocalDateTime revokedAt;
        private String revokedBy;
        private String revocationReasonEn;
        private String revocationReasonAr;
        private BigDecimal amountCapSar;
        private Integer frequencyWindowDays;
        private Long callCount;
        private LocalDateTime lastUsedAt;
    }

    // ─── API call audit ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecordApiCallRequest {
        @NotNull private Long tppId;
        @Size(max = 96) private String consentReference;
        @NotBlank @Size(max = 8) private String httpMethod;
        @NotBlank @Size(max = 512) private String endpoint;
        @NotNull private Integer statusCode;
        private Integer latencyMs;
        @Size(max = 512) private String scopesUsed;
        @Size(max = 128) private String customerHash;
        @Size(max = 64) private String sourceIpHash;
        @Size(max = 64) private String interactionId;
        private Integer responseBytes;
        @Size(max = 512) private String errorReason;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ApiCallResponse {
        private Long id;
        private Long tppId;
        private String consentReference;
        private LocalDateTime occurredAt;
        private String httpMethod;
        private String endpoint;
        private Integer statusCode;
        private Integer latencyMs;
        private String scopesUsed;
        private String customerHash;
        private String sourceIpHash;
        private String interactionId;
        private Integer responseBytes;
        private String errorReason;
    }

    // ─── Incident ──────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RaiseIncidentRequest {
        private Long tppId;
        @NotBlank @Size(max = 32) private String incidentType;
        @NotBlank @Size(max = 12) private String severity;
        @NotBlank @Size(max = 256) private String titleEn;
        @NotBlank @Size(max = 256) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        private Boolean samaReportable;
        private Long affectedCustomerCount;
        private Long assignedToUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionIncidentRequest {
        @NotBlank @Size(max = 16) private String toStatus;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class NotifySamaRequest {
        @NotBlank @Size(max = 128) private String samaNotificationRef;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class IncidentResponse {
        private Long id;
        private String reference;
        private Long tppId;
        private String incidentType;
        private String severity;
        private String status;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private LocalDateTime detectedAt;
        private LocalDateTime containedAt;
        private LocalDateTime resolvedAt;
        private LocalDateTime closedAt;
        private Boolean samaReportable;
        private String samaNotificationRef;
        private LocalDateTime samaNotifiedAt;
        private Long affectedCustomerCount;
        private Long assignedToUserId;
    }

    // ─── Event ─────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EventResponse {
        private Long id;
        private Long tppId;
        private Long consentId;
        private Long incidentId;
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
        private long activeTpps;
        private long suspendedTpps;
        private long pendingKybTpps;
        private long activeConsents;
        private long revokedConsents24h;
        private long openIncidents;
        private long criticalOpenIncidents;
        private long samaReportableOpen;
        private long apiCalls24h;
        private long apiErrors24h;
        private long certExpiring30d;
        private Map<String, Long> tppsByStatus;
        private Map<String, Long> consentsByType;
        private List<TppResponse> recentTpps;
        private List<IncidentResponse> openIncidentList;
    }
}
