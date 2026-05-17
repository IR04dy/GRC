package com.tss.grc.module.risk.scenario.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.risk.scenario.entity.RiskScenario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request / response DTOs for the {@link RiskScenario} REST surface.
 */
public final class RiskScenarioDtos {

    private RiskScenarioDtos() {}

    // ─── Create request ──────────────────────────────────────────────
    public record CreateRequest(
            Long riskId,
            String riskCode,
            String riskTitleEn,

            @NotBlank @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            String descriptionEn,
            String descriptionAr,

            @NotBlank @Size(max = 20) String scenarioType,
            Integer horizonMonths,
            String currency,

            @NotBlank String inputJson,
            String resultJson,

            BigDecimal expectedLoss,
            BigDecimal p95Loss,
            BigDecimal p99Loss,

            String assumptionsEn,
            String assumptionsAr,
            String methodologyNotesEn,
            String methodologyNotesAr
    ) {}

    // ─── Update request ──────────────────────────────────────────────
    public record UpdateRequest(
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            String inputJson,
            String resultJson,
            BigDecimal expectedLoss,
            BigDecimal p95Loss,
            BigDecimal p99Loss,
            String assumptionsEn,
            String assumptionsAr,
            String methodologyNotesEn,
            String methodologyNotesAr,
            Integer horizonMonths,
            String currency
    ) {}

    // ─── Approve / reject / archive requests ─────────────────────────
    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}

    public record ArchiveRequest(@NotBlank @Size(max = 500) String reason) {}

    // ─── Response ────────────────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            Long id,
            String scenarioCode,
            String externalId,
            Long riskId,
            String riskCode,
            String riskTitleEn,
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            String scenarioType,
            Integer horizonMonths,
            String currency,
            String inputJson,
            String resultJson,
            BigDecimal expectedLoss,
            BigDecimal p95Loss,
            BigDecimal p99Loss,
            String assumptionsEn,
            String assumptionsAr,
            String methodologyNotesEn,
            String methodologyNotesAr,
            String status,
            LocalDateTime approvedAt,
            Long approvedBy,
            String approverName,
            String rejectionReason,
            LocalDateTime archivedAt,
            String archiveReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Response from(RiskScenario s) {
            return new Response(
                    s.getId(),
                    s.getScenarioCode(),
                    s.getExternalId(),
                    s.getRiskId(),
                    s.getRiskCode(),
                    s.getRiskTitleEn(),
                    s.getTitleEn(),
                    s.getTitleAr(),
                    s.getDescriptionEn(),
                    s.getDescriptionAr(),
                    s.getScenarioType(),
                    s.getHorizonMonths(),
                    s.getCurrency(),
                    s.getInputJson(),
                    s.getResultJson(),
                    s.getExpectedLoss(),
                    s.getP95Loss(),
                    s.getP99Loss(),
                    s.getAssumptionsEn(),
                    s.getAssumptionsAr(),
                    s.getMethodologyNotesEn(),
                    s.getMethodologyNotesAr(),
                    s.getStatus(),
                    s.getApprovedAt(),
                    s.getApprovedBy(),
                    s.getApproverName(),
                    s.getRejectionReason(),
                    s.getArchivedAt(),
                    s.getArchiveReason(),
                    s.getCreatedAt(),
                    s.getUpdatedAt()
            );
        }
    }
}
