package com.tss.grc.module.compliance.obligation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 11 — Request / response DTOs for the obligation register +
 * periodic attestation endpoints.
 *
 * <p>Grouped in a single file (as other slices do) to keep the
 * controller surface compact and discoverable.
 */
public final class ObligationDtos {
    private ObligationDtos() {}

    // ─── Validation regex constants ───────────────────────────────────

    public static final String REGULATOR_REGEX =
        "SAMA|NCA|SDAIA|CMA|ZATCA|MOH|GAZT|MOJ|NCEMA|CITC|MOC|MOI|INTERNAL";
    public static final String TYPE_REGEX =
        "REPORTING|ATTESTATION|DOCUMENTATION|NOTIFICATION|AUDIT|TRAINING|REVIEW|FILING|REGISTRATION";
    public static final String FREQUENCY_REGEX =
        "DAILY|WEEKLY|MONTHLY|QUARTERLY|SEMI_ANNUAL|ANNUAL|BIENNIAL|ON_EVENT|AD_HOC";
    public static final String SEVERITY_REGEX = "CRITICAL|HIGH|MEDIUM|LOW";
    public static final String LIFECYCLE_REGEX = "DRAFT|ACTIVE|SUSPENDED|RETIRED";

    // ─── Obligation create / update ───────────────────────────────────

    public record ObligationCreateRequest(
        @Size(max = 64) String obligationCode,
        @NotBlank @Size(max = 500) String titleEn,
        @NotBlank @Size(max = 500) String titleAr,
        @Size(max = 8000) String descriptionEn,
        @Size(max = 8000) String descriptionAr,
        @NotBlank @Pattern(regexp = REGULATOR_REGEX) String regulator,
        @Size(max = 500) String regulationReference,
        @NotBlank @Pattern(regexp = TYPE_REGEX) String obligationType,
        @NotBlank @Pattern(regexp = FREQUENCY_REGEX) String frequency,
        @Pattern(regexp = SEVERITY_REGEX) String severityIfMissed,
        LocalDate firstDueDate,
        @NotNull LocalDate nextDueDate,
        @PositiveOrZero Integer leadTimeDays,
        @NotNull @Positive Long ownerUserId,
        Long backupOwnerUserId,
        @Size(max = 255) String businessUnitEn,
        @Size(max = 255) String businessUnitAr,
        Long relatedControlId,
        Long relatedRiskId
    ) {}

    public record ObligationUpdateRequest(
        @Size(max = 500) String titleEn,
        @Size(max = 500) String titleAr,
        @Size(max = 8000) String descriptionEn,
        @Size(max = 8000) String descriptionAr,
        @Pattern(regexp = REGULATOR_REGEX) String regulator,
        @Size(max = 500) String regulationReference,
        @Pattern(regexp = TYPE_REGEX) String obligationType,
        @Pattern(regexp = FREQUENCY_REGEX) String frequency,
        @Pattern(regexp = SEVERITY_REGEX) String severityIfMissed,
        LocalDate firstDueDate,
        LocalDate nextDueDate,
        @PositiveOrZero Integer leadTimeDays,
        Long ownerUserId,
        Long backupOwnerUserId,
        @Size(max = 255) String businessUnitEn,
        @Size(max = 255) String businessUnitAr,
        Long relatedControlId,
        Long relatedRiskId
    ) {}

    public record ObligationTransitionRequest(
        @NotBlank @Pattern(regexp = LIFECYCLE_REGEX) String toStage,
        @Size(max = 1000) String reason
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ObligationResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String obligationCode,
        String titleEn,
        String titleAr,
        String descriptionEn,
        String descriptionAr,
        String regulator,
        String regulationReference,
        String obligationType,
        String frequency,
        String severityIfMissed,
        LocalDate firstDueDate,
        LocalDate nextDueDate,
        Integer leadTimeDays,
        Long ownerUserId,
        Long backupOwnerUserId,
        String businessUnitEn,
        String businessUnitAr,
        Long relatedControlId,
        Long relatedRiskId,
        String lifecycleStage,
        LocalDateTime lastAttestedAt,
        Long lastAttestedByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Attestation submit / review ──────────────────────────────────

    public record AttestationRecordRequest(
        @NotNull @Positive Long obligationId,
        Long campaignId,
        @Size(max = 64) String periodLabel,
        LocalDate periodStart,
        LocalDate periodEnd,
        @NotNull LocalDate dueDate,
        @NotBlank @Pattern(regexp = "ATTESTED|ATTESTED_WITH_EXCEPTIONS|NOT_APPLICABLE") String attestationStatus,
        // F2-224 — attestedByUserId is gone. It carried @NotNull @Positive, so the server
        // REQUIRED an attribution it never verified; the attester is now the authenticated
        // principal (ObligationAttestationService#recordAttestation).
        @Size(max = 2000) String attestationTextEn,
        @Size(max = 2000) String attestationTextAr,
        @Size(max = 8000) String evidenceRefs,
        @Size(max = 2000) String exceptionsNotedEn,
        @Size(max = 2000) String exceptionsNotedAr
    ) {}

    public record AttestationReviewRequest(
        @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED|REQUEST_CHANGES") String reviewerDecision,
        // F2-224 — reviewerUserId is gone, for the same reason as attestedByUserId above.
        @Size(max = 1000) String reviewerCommentEn,
        @Size(max = 1000) String reviewerCommentAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttestationResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long obligationId,
        Long campaignId,
        String periodLabel,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate dueDate,
        String attestationStatus,
        Long attestedByUserId,
        LocalDateTime attestedAt,
        String attestationTextEn,
        String attestationTextAr,
        String evidenceRefs,
        String exceptionsNotedEn,
        String exceptionsNotedAr,
        Long reviewerUserId,
        LocalDateTime reviewedAt,
        String reviewerDecision,
        String reviewerCommentEn,
        String reviewerCommentAr,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Campaign create / close ──────────────────────────────────────

    public record CampaignCreateRequest(
        @Size(max = 64) String campaignCode,
        @NotBlank @Size(max = 500) String titleEn,
        @NotBlank @Size(max = 500) String titleAr,
        @Size(max = 8000) String descriptionEn,
        @Size(max = 8000) String descriptionAr,
        @NotNull @Positive Integer cycleYear,
        @Pattern(regexp = "Q1|Q2|Q3|Q4|") String cycleQuarter,
        @Pattern(regexp = REGULATOR_REGEX + "|") String regulatorFilter,
        @Pattern(regexp = FREQUENCY_REGEX + "|") String frequencyFilter,
        @NotNull LocalDateTime closesAt,
        @Positive Integer reminderCadenceDays
    ) {}

    /**
     * F2-185(b): both caps were {@code @Size(max = 8000)} and neither had ever run — the binding
     * parameter carries no {@code @Valid}. They are DELETED rather than switched on, because
     * {@code obligation_attestation_campaign.closing_summary_en/_ar} are {@code NVARCHAR(MAX)}
     * (V184 lines 186-187, and the entity says so too): the schema deliberately leaves a closing
     * summary unbounded, so enabling an 8,000-character limit would impose one nobody chose on the
     * narrative that closes a regulatory attestation campaign. Same call as {@code coiNote} in
     * F2-184 — a dormant number is a decision to make, not a repair to apply.
     */
    public record CampaignCloseRequest(
        String closingSummaryEn,
        String closingSummaryAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampaignResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String campaignCode,
        String titleEn,
        String titleAr,
        String descriptionEn,
        String descriptionAr,
        Integer cycleYear,
        String cycleQuarter,
        String regulatorFilter,
        String frequencyFilter,
        LocalDateTime openedAt,
        LocalDateTime closesAt,
        LocalDateTime closedAt,
        Integer reminderCadenceDays,
        String status,
        Integer totalInvited,
        Integer totalInProgress,
        Integer totalAttested,
        Integer totalAttestedWithExcs,
        Integer totalOverdue,
        Integer totalNa,
        String closingSummaryEn,
        String closingSummaryAr,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampaignStatsResponse(
        Long campaignId,
        Integer invited,
        Integer inProgress,
        Integer attested,
        Integer attestedWithExceptions,
        Integer overdue,
        Integer notApplicable,
        Integer completionPercent,
        Integer daysUntilClose
    ) {}

    // ─── Register KPIs (used by the landing page tiles) ───────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ObligationKpis(
        Long activeCount,
        Long overdueCount,
        Long dueThisMonthCount,
        Long attestedThisCycleCount
    ) {}
}
