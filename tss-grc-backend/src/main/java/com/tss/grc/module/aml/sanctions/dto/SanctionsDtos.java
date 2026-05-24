package com.tss.grc.module.aml.sanctions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 13 — Sanctions Screening DTOs (lists, entries, checks, hits, dashboard).
 *
 * <p>Grouped in a single file to keep the controller compact.
 */
public final class SanctionsDtos {
    private SanctionsDtos() {}

    public static final String SOURCE_TYPE_REGEX = "UN|OFAC|EU|SAMA|KSA_NATIONAL|FATF|OTHER";
    public static final String REFRESH_FREQ_REGEX = "DAILY|WEEKLY|MONTHLY|ON_DEMAND";
    public static final String ENTITY_TYPE_REGEX = "INDIVIDUAL|ENTITY|VESSEL|AIRCRAFT";
    public static final String SUBJECT_TYPE_REGEX =
        "CUSTOMER_ONBOARDING|TRANSACTION_COUNTERPARTY|BENEFICIAL_OWNER|EMPLOYEE_KYC|VENDOR_KYC|AD_HOC";
    public static final String CHECK_SOURCE_REGEX =
        "MANUAL|REAL_TIME_API|BATCH_FILE|INTEGRATION_VENDOR";
    public static final String CHECK_RESULT_REGEX = "NO_HIT|POTENTIAL_HIT|CONFIRMED_HIT";
    public static final String HIT_STATUS_REGEX =
        "PENDING_REVIEW|TRUE_POSITIVE|FALSE_POSITIVE|ESCALATED|BLOCK_RECOMMENDED|RELEASE_RECOMMENDED|CLOSED";
    public static final String MATCH_TYPE_REGEX =
        "EXACT_NAME|FUZZY_NAME|ALIAS|DOB_MATCH|DOCUMENT_MATCH|COUNTRY_MATCH|COMPOSITE";
    public static final String SEVERITY_REGEX = "STRONG_MATCH_REQUIRED|REVIEW_REQUIRED";

    // ── List ─────────────────────────────────────────────────────────

    public record ListCreateRequest(
        @NotBlank @Size(max = 64) String listCode,
        @NotBlank @Size(max = 255) String titleEn,
        @NotBlank @Size(max = 255) String titleAr,
        @Size(max = 500) String sourceUrl,
        @NotBlank @Pattern(regexp = SOURCE_TYPE_REGEX) String sourceType,
        @Pattern(regexp = REFRESH_FREQ_REGEX) String refreshFrequency,
        Boolean isActive
    ) {}

    public record ListUpdateRequest(
        @Size(max = 255) String titleEn,
        @Size(max = 255) String titleAr,
        @Size(max = 500) String sourceUrl,
        @Pattern(regexp = REFRESH_FREQ_REGEX) String refreshFrequency,
        Boolean isActive
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ListResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String listCode,
        String titleEn,
        String titleAr,
        String sourceUrl,
        String sourceType,
        LocalDateTime lastRefreshedAt,
        String refreshFrequency,
        Integer entryCount,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ── List entry ───────────────────────────────────────────────────

    public record EntryCreateRequest(
        @NotBlank @Size(max = 64) String entryExternalId,
        @NotBlank @Pattern(regexp = ENTITY_TYPE_REGEX) String entityType,
        @NotBlank @Size(max = 500) String primaryNameEn,
        @Size(max = 500) String primaryNameAr,
        String aliasesJson,
        LocalDate dateOfBirth,
        @Size(max = 100) String country,
        String idDocumentsJson,
        LocalDate listingDate,
        @Size(max = 255) String listedBy,
        @Size(max = 1000) String listingReasonEn,
        @Size(max = 1000) String listingReasonAr,
        @Pattern(regexp = SEVERITY_REGEX) String severity
    ) {}

    public record EntryBulkImportRequest(
        @NotNull @Positive Long listId,
        @NotNull List<EntryCreateRequest> entries
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EntryBulkImportResponse(
        int totalSubmitted,
        int created,
        int skippedExisting
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EntryResponse(
        Long id,
        UUID externalId,
        Long listId,
        String entryExternalId,
        String entityType,
        String primaryNameEn,
        String primaryNameAr,
        String aliasesJson,
        LocalDate dateOfBirth,
        String country,
        String idDocumentsJson,
        LocalDate listingDate,
        String listedBy,
        String listingReasonEn,
        String listingReasonAr,
        String severity,
        LocalDateTime createdAt
    ) {}

    // ── Screening check + hit ────────────────────────────────────────

    public record ScreenRequest(
        @NotBlank @Pattern(regexp = SUBJECT_TYPE_REGEX) String subjectType,
        @Size(max = 255) String subjectReference,
        @NotBlank @Size(max = 500) String subjectNameEn,
        @Size(max = 500) String subjectNameAr,
        LocalDate subjectDob,
        @Size(max = 100) String subjectCountry,
        @Size(max = 255) String subjectIdDocument,
        @Pattern(regexp = CHECK_SOURCE_REGEX) String checkSource,
        /** List codes to screen against; null/empty = every active visible list. */
        List<@Size(max = 64) String> listsToScreen
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HitResponse(
        Long id,
        UUID externalId,
        Long checkId,
        Long listEntryId,
        String listCode,
        String matchedNameEn,
        String matchedNameAr,
        BigDecimal matchScore,
        String matchType,
        String status,
        Long reviewerUserId,
        LocalDateTime reviewedAt,
        String dispositionEn,
        String dispositionAr,
        Boolean slaBreach,
        Long escalatedToUserId,
        LocalDateTime escalatedAt,
        Long capaTaskId,
        LocalDateTime createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String checkCode,
        String subjectType,
        String subjectReference,
        String subjectNameEn,
        String subjectNameAr,
        LocalDate subjectDob,
        String subjectCountry,
        String subjectIdDocument,
        LocalDateTime checkAt,
        String checkSource,
        String listsScreenedCodes,
        String result,
        List<HitResponse> hits,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record HitReviewRequest(
        @NotBlank @Pattern(regexp = HIT_STATUS_REGEX) String newStatus,
        @Positive Long reviewerUserId,
        @Size(max = 2000) String dispositionEn,
        @Size(max = 2000) String dispositionAr,
        Long capaTaskId
    ) {}

    public record HitEscalateRequest(
        @Positive Long escalatedToUserId,
        @Size(max = 2000) String reasonEn,
        @Size(max = 2000) String reasonAr
    ) {}

    // ── Dashboard ────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardResponse(
        long checksLast24h,
        long totalChecks,
        long hitsPendingReview,
        long hitsTruePositive,
        long hitsFalsePositive,
        long hitsEscalated,
        long hitsSlaBreached,
        /** false-positives / (true + false positives) — null when no resolved hits. */
        Double falsePositiveRate,
        LocalDateTime asOf
    ) {}
}
