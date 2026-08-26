package com.tss.grc.module.tprm.bcp_attestation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the Slice 10-D Vendor BCP attestation campaign endpoints.
 *
 * <p>Grouped in a single file the same way other slices group their
 * request/response shapes — keeps the controller surface compact and
 * discoverable.
 */
public final class VendorBcpAttestationDtos {
    private VendorBcpAttestationDtos() {}

    // ─── Campaign create / update ───────────────────────────────────
    public record CampaignCreateRequest(
        @Size(max = 64) String campaignCode,
        @NotBlank @Size(max = 255) String titleEn,
        @NotBlank @Size(max = 255) String titleAr,
        @Size(max = 8000) String descriptionEn,
        @Size(max = 8000) String descriptionAr,
        @NotNull @Positive Integer cycleYear,
        @Pattern(regexp = "Q1|Q2|Q3|Q4|") String cycleQuarter,
        LocalDateTime closesAt,
        @Positive Integer reminderCadenceDays,
        @Positive Integer escalationThresholdDays,
        @NotBlank @Pattern(regexp = "ALL|CRITICAL_ONLY|HIGH_AND_ABOVE") String mandatoryThreshold,
        @Size(max = 500) String regulatorReference
    ) {}

    /**
     * F2-185(b): the caps are DELETED, not enabled. {@code vendor_bcp_campaign.closing_summary_en/
     * _ar} are {@code NVARCHAR(MAX)} (V177 lines 94-95), so an 8,000-character limit that had never
     * run would be a new restriction on a campaign's closing narrative rather than a repair. The
     * identical shape sits on {@code ObligationDtos.CampaignCloseRequest}.
     */
    public record CampaignCloseRequest(
        String closingSummaryEn,
        String closingSummaryAr
    ) {}

    // ─── Campaign response ──────────────────────────────────────────
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
        LocalDateTime openedAt,
        LocalDateTime closesAt,
        LocalDateTime closedAt,
        Integer reminderCadenceDays,
        Integer escalationThresholdDays,
        String status,
        String mandatoryThreshold,
        String regulatorReference,
        Integer totalInvited,
        Integer totalSubmitted,
        Integer totalAccepted,
        Integer totalRejected,
        Integer totalOverdue,
        Integer totalNa,
        String closingSummaryEn,
        String closingSummaryAr,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    /** Stats roll-up returned by the detail endpoint. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampaignStatsResponse(
        Long campaignId,
        Integer invited,
        Integer submitted,
        Integer accepted,
        Integer rejected,
        Integer overdue,
        Integer na,
        Integer responseRatePercent,
        Integer daysUntilClose
    ) {}

    // ─── Attestation responses ──────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttestationResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long campaignId,
        Long vendorId,
        String vendorName,
        String vendorNameAr,
        // F2-383: submissionToken removed — it is the vendor's portal credential (authenticates the
        // permitAll POST /by-token/{token}/submit), and this DTO is also returned to broad TPRM:READ
        // staff, so a reader could harvest tokens and submit/forge a vendor's BCP attestation. The
        // vendor receives the token out-of-band in its invitation URL, not echoed in this response.
        LocalDateTime invitedAt,
        LocalDateTime firstReminderAt,
        LocalDateTime secondReminderAt,
        LocalDateTime escalatedAt,
        LocalDateTime overdueAt,
        LocalDateTime submittedAt,
        String submittedByContactEmail,
        String submittedByContactName,
        Integer rtoHoursAttested,
        Integer mtpdHoursAttested,
        Integer contractualRtoHours,
        Integer contractualMtpdHours,
        Boolean rtoBreach,
        Boolean mtpdBreach,
        Boolean hasTestedBcp,
        LocalDate lastBcpTestDate,
        LocalDate lastDrTestDate,
        String subprocessorDependencies,
        String evidenceFilePath,
        String evidenceFileName,
        String status,
        String reviewerCommentEn,
        String reviewerCommentAr,
        Long reviewedByUserId,
        String reviewedByName,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Vendor-side submission ─────────────────────────────────────
    public record AttestationSubmitRequest(
        @NotNull @Positive Integer rtoHoursAttested,
        @NotNull @Positive Integer mtpdHoursAttested,
        @NotNull Boolean hasTestedBcp,
        LocalDate lastBcpTestDate,
        LocalDate lastDrTestDate,
        @Size(max = 8000) String subprocessorDependencies,
        @Size(max = 500) String evidenceFilePath,
        @Size(max = 255) String evidenceFileName,
        @Email @Size(max = 255) String submittedByContactEmail,
        @Size(max = 255) String submittedByContactName
    ) {}

    // ─── Reviewer outcome ───────────────────────────────────────────
    /**
     * F2-185(b): these three caps ARE enabled — {@code @Valid} added at all three binding sites in
     * {@code VendorBcpAttestationController} (accept / reject / out-of-scope). Every number matches
     * its column exactly: {@code reviewer_comment_en} 1000, {@code reviewer_comment_ar} 1000,
     * {@code reviewed_by_name} 255 on {@code tprm.vendor_bcp_attestation}. That equality is what
     * makes switching them on safe — SQL Server already refuses more, so the only behaviour change
     * is a 400 naming the field instead of a truncation 500, and no request that works today can
     * start failing. {@code RequestSizeCapMirrorTest} holds the equality so the number cannot drift
     * away from the column later.
     */
    /**
     * F2-224 — {@code reviewedByName} and {@code reviewedByUserId} are gone: the reviewer is the
     * authenticated principal, resolved in
     * {@code VendorBcpAttestationService#transitionAttestation}. Its {@code @Size(max = 255)}
     * landing in {@code RequestSizeCapMirrorTest} was deleted with it, and that test's
     * anti-vacuity floor lowered by one with the reason recorded (F2-213: a population that
     * falls after a fix is not automatically rot, but it is always worth asking which).
     */
    public record AttestationReviewRequest(
        @Size(max = 1000) String reviewerCommentEn,
        @Size(max = 1000) String reviewerCommentAr
    ) {}

    public record EscalateRequest(
        @Size(max = 1000) String reason,
        Long escalatedToUserId
    ) {}

    /** Wrapper used by bulk endpoints. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BulkResult(
        Integer affected,
        List<Long> affectedIds,
        String message
    ) {}
}
