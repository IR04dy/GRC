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
    /**
     * ⚠⚠ F2-245 — {@code riskCode} and {@code riskTitleEn} are GONE from this record,
     * and their absence is the fix.
     *
     * <p>A soft FK that also carries the parent's identity is making <b>two</b> claims:
     * the id says <i>which row</i>, and the code and title say <i>what it is called</i>.
     * Only the parent can answer the second. This record used to accept all three, so
     * a caller could name risk 4 and label it anything — and measured in dev and prod,
     * <b>11 of 11</b> live scenarios did exactly that: every stored label was coherent
     * and none of them was the title of the risk the id names.
     *
     * <p>The server now derives both from the risk it resolves, so a disagreement is
     * unrepresentable rather than merely absent (F2-213). A field the bound type does
     * not declare can never arrive — the strongest closure there is (F2-233).
     */
    public record CreateRequest(
            Long riskId,

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
