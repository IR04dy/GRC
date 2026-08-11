package com.tss.grc.module.bcm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the Slice 9-C Quantitative BIA endpoints.
 *
 * <p>Grouped in a single file (final, private ctor) the same way
 * {@code DrPlanDtos} groups its create/update/response shapes — keeps
 * the controller surface compact and discoverable.
 */
public final class BiaQuantitativeDtos {
    private BiaQuantitativeDtos() {}

    // ─── Loss-line (used in both directions) ─────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BiaQuantitativeLossLineDto(
        Long id,
        @NotBlank @Pattern(regexp = "24h|72h|1w|2w|1m") String timeBand,
        @NotBlank @Size(max = 40) String lossType,
        @NotNull @PositiveOrZero BigDecimal amountSar,
        String basisNarrativeEn,
        String basisNarrativeAr,
        @Pattern(regexp = "HIGH|MEDIUM|LOW") String confidence,
        Boolean isEstimate
    ) {}

    // ─── Create / Update request ────────────────────────────────────
    public record BiaQuantitativeRequest(
        @NotNull Long processId,
        @Size(max = 80) String assessmentCode,
        @NotNull LocalDate assessmentDate,
        @NotNull Integer cycleYear,
        @Size(max = 3) String currency,
        String methodologyNotesEn,
        String methodologyNotesAr,
        @PositiveOrZero BigDecimal maxTolerableLossSar,
        @Size(max = 255) String preparedByName,
        Long preparedByUserId,
        // F2-184 — without @Valid, none of the loss line's own constraints ran: the
        // @Pattern vocabularies on timeBand/confidence and the @PositiveOrZero on amountSar.
        // bcm.bia_quantitative_loss_line has zero CHECKs, so those patterns were the only
        // thing narrowing time_band and confidence. Safe to enable: all 40 stored rows hold
        // in-vocabulary values and none has a negative amount (min 0.00).
        @Valid List<BiaQuantitativeLossLineDto> lossLines
    ) {}

    // ─── Response ────────────────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BiaQuantitativeResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long processId,
        String assessmentCode,
        LocalDate assessmentDate,
        Integer cycleYear,
        String currency,
        String methodologyNotesEn,
        String methodologyNotesAr,
        BigDecimal totalLoss24hSar,
        BigDecimal totalLoss72hSar,
        BigDecimal totalLoss1wSar,
        BigDecimal totalLoss2wSar,
        BigDecimal totalLoss1mSar,
        BigDecimal maxTolerableLossSar,
        String mtpdBreachAt,
        String status,
        Long preparedByUserId,
        String preparedByName,
        LocalDateTime preparedAt,
        LocalDateTime bcmLeadReviewedAt,
        String bcmLeadReviewedBy,
        LocalDateTime cfoApprovedAt,
        String cfoApproverName,
        String cfoComments,
        LocalDateTime boardApprovedAt,
        String boardMinutesRef,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<BiaQuantitativeLossLineDto> lossLines
    ) {}

    // ─── CFO approve/reject ─────────────────────────────────────────
    public record CfoApprovalRequest(
        @NotBlank @Size(max = 255) String approverName,
        String comments,
        @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
        String rejectionReason
    ) {}

    // ─── Board approve ──────────────────────────────────────────────
    public record BoardApprovalRequest(
        @NotBlank @Size(max = 255) String minutesRef
    ) {}
}
