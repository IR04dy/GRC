package com.tss.grc.module.cpty.dto;

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

/** Sprint 18 PR #1 — Counterparty Credit Rating Registry DTOs. */
public final class CptyDtos {
    private CptyDtos() {}

    // ─── Counterparty ──────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateCounterpartyRequest {
        @Size(max = 32) private String reference;          // auto-gen if blank
        @Size(max = 20) private String lei;
        @NotBlank @Size(max = 256) private String legalNameEn;
        @NotBlank @Size(max = 256) private String legalNameAr;
        @Size(max = 128) private String shortName;
        @NotBlank @Size(max = 32) private String sector;
        @Size(max = 2) private String country;
        @Size(max = 128) private String industry;
        @Size(max = 64) private String parentGroupReference;
        private Boolean greFlag;
        private Boolean sovereignFlag;
        private Boolean fiFlag;
        private Long relationshipManagerUserId;
        private Long creditOfficerUserId;
        private LocalDate onboardedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateCounterpartyRequest {
        @Size(max = 256) private String legalNameEn;
        @Size(max = 256) private String legalNameAr;
        @Size(max = 128) private String shortName;
        @Size(max = 32) private String sector;
        @Size(max = 2) private String country;
        @Size(max = 128) private String industry;
        @Size(max = 64) private String parentGroupReference;
        private Boolean greFlag;
        private Boolean sovereignFlag;
        private Boolean fiFlag;
        @Size(max = 16) private String status;
        private Long relationshipManagerUserId;
        private Long creditOfficerUserId;
        private LocalDate kycRefreshedAt;
        private BigDecimal totalExposureSarM;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CounterpartyResponse {
        private Long id;
        private String reference;
        private String lei;
        private String legalNameEn;
        private String legalNameAr;
        private String shortName;
        private String sector;
        private String country;
        private String industry;
        private String parentGroupReference;
        private Boolean greFlag;
        private Boolean sovereignFlag;
        private Boolean fiFlag;
        private String status;
        private Long relationshipManagerUserId;
        private Long creditOfficerUserId;
        private LocalDate kycRefreshedAt;
        private LocalDate onboardedAt;
        private BigDecimal totalExposureSarM;
        private String currentInternalGrade;
        private String currentExternalRating;
        private String currentExternalAgency;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ─── Credit Rating ─────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AssignRatingRequest {
        @NotBlank @Size(max = 16) private String ratingType;   // INTERNAL | EXTERNAL
        @NotNull private LocalDate effectiveDate;
        @NotBlank @Size(max = 16) private String grade;
        @Size(max = 64) private String scale;
        @Size(max = 32) private String agency;
        private BigDecimal pd;
        private BigDecimal lgd;
        private BigDecimal eadSarM;
        @Size(max = 16) private String outlook;
        @Size(max = 4000) private String rationaleEn;
        @Size(max = 4000) private String rationaleAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RatingResponse {
        private Long id;
        private Long counterpartyId;
        private String ratingType;
        private LocalDate effectiveDate;
        private String grade;
        private String scale;
        private String agency;
        private BigDecimal pd;
        private BigDecimal lgd;
        private BigDecimal eadSarM;
        private String outlook;
        private String rationaleEn;
        private String rationaleAr;
        private Long assignedByUserId;
        private Long approvedByUserId;
    }

    // ─── Rating Action ─────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRatingActionRequest {
        @NotBlank @Size(max = 24) private String actionType;
        @NotNull private LocalDate actionDate;
        @Size(max = 16) private String gradeBefore;
        @Size(max = 16) private String gradeAfter;
        @Size(max = 16) private String outlookBefore;
        @Size(max = 16) private String outlookAfter;
        @Size(max = 24) private String triggerSource;
        @Size(max = 4000) private String rationaleEn;
        @Size(max = 4000) private String rationaleAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RatingActionResponse {
        private Long id;
        private Long counterpartyId;
        private String actionType;
        private LocalDate actionDate;
        private String gradeBefore;
        private String gradeAfter;
        private String outlookBefore;
        private String outlookAfter;
        private String triggerSource;
        private String rationaleEn;
        private String rationaleAr;
        private Long actorUserId;
    }

    // ─── Large Exposure Limit ──────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpsertLimitRequest {
        @NotBlank @Size(max = 16) private String scope;       // COUNTERPARTY | GROUP
        private Long counterpartyId;
        @Size(max = 64) private String groupReference;
        @NotBlank @Size(max = 256) private String displayNameEn;
        @NotBlank @Size(max = 256) private String displayNameAr;
        @NotNull private BigDecimal limitSarM;
        @NotNull private BigDecimal utilisedSarM;
        // F2-223 — approvedAt is the server's: this route is CPTY:APPROVE, so the call IS the
        // approval. The client was minting it in the browser, which let a SAMA large-exposure
        // approval be backdated and silently re-dated every time the limit was edited.
        // nextReviewAt stays the caller's — it is a plan, not a record of something that happened.
        private LocalDate nextReviewAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecordUtilisationRequest {
        @NotNull private BigDecimal utilisedSarM;
        @NotNull private LocalDate refreshedAt;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LimitResponse {
        private Long id;
        private String scope;
        private Long counterpartyId;
        private String groupReference;
        private String displayNameEn;
        private String displayNameAr;
        private BigDecimal limitSarM;
        private BigDecimal utilisedSarM;
        private BigDecimal utilisationPct;
        private String band;
        private LocalDate approvedAt;
        private LocalDate nextReviewAt;
        private Long approvedByUserId;
        private LocalDate utilisationRefreshedAt;
    }

    // ─── Event ─────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EventResponse {
        private Long id;
        private Long counterpartyId;
        private LocalDateTime occurredAt;
        private String eventType;
        private Long actorUserId;
        private String actorName;
        private Long linkedRatingId;
        private Long linkedActionId;
        private Long linkedLimitId;
        private String bandBefore;
        private String bandAfter;
        private String noteEn;
        private String noteAr;
    }

    // ─── Dashboard ─────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DashboardResponse {
        private long totalCounterparties;
        private long activeCount;
        private long watchlistCount;
        private long defaultCount;
        private long limitsBreach;
        private long limitsRed;
        private long limitsAmber;
        private BigDecimal totalExposureSarM;
        private Map<String, Long> bySector;
        private Map<String, Long> byInternalGrade;
        private List<CounterpartyResponse> recentCounterparties;
        private List<LimitResponse> topUtilisedLimits;
    }
}
