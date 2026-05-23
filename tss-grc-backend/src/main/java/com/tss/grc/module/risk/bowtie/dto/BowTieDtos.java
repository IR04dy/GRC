package com.tss.grc.module.risk.bowtie.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.risk.bowtie.entity.BowTieAnalysis;
import com.tss.grc.module.risk.bowtie.entity.BowTieConsequence;
import com.tss.grc.module.risk.bowtie.entity.BowTieMitigatingControl;
import com.tss.grc.module.risk.bowtie.entity.BowTiePreventiveControl;
import com.tss.grc.module.risk.bowtie.entity.BowTieThreat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request / response DTOs for the bow-tie REST surface.
 *
 * <p>Children DTOs are reused both as standalone request bodies (when
 * adding one threat / consequence / control via a sub-resource POST)
 * and as nested children of {@link BowTieFullResponse}.
 */
public final class BowTieDtos {

    private BowTieDtos() {}

    // ─── Header request / response ────────────────────────────────

    public record BowTieAnalysisRequest(
            String bowtieCode,
            Long linkedRiskId,
            @NotBlank @Size(max = 500) String topEventTitleEn,
            @Size(max = 500) String topEventTitleAr,
            String topEventDescriptionEn,
            String topEventDescriptionAr,
            @Size(max = 80) String riskCategory,
            LocalDate assessmentDate,
            @Size(max = 255) String assessedByName,
            @Size(max = 60) String methodology,
            LocalDate nextReviewAt,
            // Optional cascade — frontend can POST a fully-shaped bow-tie in one call.
            List<BowTieThreatDto> threats,
            List<BowTieConsequenceDto> consequences,
            List<BowTiePreventiveControlDto> preventiveControls,
            List<BowTieMitigatingControlDto> mitigatingControls
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BowTieAnalysisResponse(
            Long id,
            UUID externalId,
            String bowtieCode,
            Long linkedRiskId,
            String topEventTitleEn,
            String topEventTitleAr,
            String topEventDescriptionEn,
            String topEventDescriptionAr,
            String riskCategory,
            LocalDate assessmentDate,
            String assessedByName,
            String methodology,
            String overallResidualRating,
            BigDecimal overallControlEffectiveness,
            String status,
            LocalDateTime approvedAt,
            String approvedByName,
            LocalDate nextReviewAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static BowTieAnalysisResponse from(BowTieAnalysis b) {
            return new BowTieAnalysisResponse(
                    b.getId(),
                    b.getExternalId(),
                    b.getBowtieCode(),
                    b.getLinkedRiskId(),
                    b.getTopEventTitleEn(),
                    b.getTopEventTitleAr(),
                    b.getTopEventDescriptionEn(),
                    b.getTopEventDescriptionAr(),
                    b.getRiskCategory(),
                    b.getAssessmentDate(),
                    b.getAssessedByName(),
                    b.getMethodology(),
                    b.getOverallResidualRating(),
                    b.getOverallControlEffectiveness(),
                    b.getStatus(),
                    b.getApprovedAt(),
                    b.getApprovedByName(),
                    b.getNextReviewAt(),
                    b.getCreatedAt(),
                    b.getUpdatedAt()
            );
        }
    }

    // ─── Children DTOs ────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BowTieThreatDto(
            Long id,
            UUID externalId,
            Long bowtieId,
            Integer threatOrder,
            @NotBlank @Size(max = 300) String titleEn,
            @Size(max = 300) String titleAr,
            String descriptionEn,
            String descriptionAr,
            String inherentLikelihood,
            String residualLikelihood,
            String source
    ) {
        public static BowTieThreatDto from(BowTieThreat t) {
            return new BowTieThreatDto(
                    t.getId(), t.getExternalId(), t.getBowtieId(),
                    t.getThreatOrder(),
                    t.getTitleEn(), t.getTitleAr(),
                    t.getDescriptionEn(), t.getDescriptionAr(),
                    t.getInherentLikelihood(), t.getResidualLikelihood(),
                    t.getSource()
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BowTieConsequenceDto(
            Long id,
            UUID externalId,
            Long bowtieId,
            Integer consequenceOrder,
            @NotBlank @Size(max = 300) String titleEn,
            @Size(max = 300) String titleAr,
            String descriptionEn,
            String descriptionAr,
            String consequenceType,
            String inherentSeverity,
            String residualSeverity,
            BigDecimal estimatedLossSar
    ) {
        public static BowTieConsequenceDto from(BowTieConsequence c) {
            return new BowTieConsequenceDto(
                    c.getId(), c.getExternalId(), c.getBowtieId(),
                    c.getConsequenceOrder(),
                    c.getTitleEn(), c.getTitleAr(),
                    c.getDescriptionEn(), c.getDescriptionAr(),
                    c.getConsequenceType(),
                    c.getInherentSeverity(), c.getResidualSeverity(),
                    c.getEstimatedLossSar()
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BowTiePreventiveControlDto(
            Long id,
            UUID externalId,
            Long bowtieId,
            Long threatId,
            UUID threatExternalId,
            Integer controlOrder,
            @NotBlank @Size(max = 300) String titleEn,
            @Size(max = 300) String titleAr,
            String descriptionEn,
            String descriptionAr,
            String controlType,
            Long linkedControlId,
            String effectiveness,
            LocalDate lastTestedAt,
            String lastTestResult,
            String responsibleRole,
            String responsibleOwner
    ) {
        public static BowTiePreventiveControlDto from(BowTiePreventiveControl c, UUID threatExternalId) {
            return new BowTiePreventiveControlDto(
                    c.getId(), c.getExternalId(),
                    c.getBowtieId(), c.getThreatId(), threatExternalId,
                    c.getControlOrder(),
                    c.getTitleEn(), c.getTitleAr(),
                    c.getDescriptionEn(), c.getDescriptionAr(),
                    c.getControlType(), c.getLinkedControlId(),
                    c.getEffectiveness(),
                    c.getLastTestedAt(), c.getLastTestResult(),
                    c.getResponsibleRole(), c.getResponsibleOwner()
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BowTieMitigatingControlDto(
            Long id,
            UUID externalId,
            Long bowtieId,
            Long consequenceId,
            UUID consequenceExternalId,
            Integer controlOrder,
            @NotBlank @Size(max = 300) String titleEn,
            @Size(max = 300) String titleAr,
            String descriptionEn,
            String descriptionAr,
            String controlType,
            Long linkedControlId,
            String effectiveness,
            LocalDate lastTestedAt,
            String lastTestResult,
            String responsibleRole,
            String responsibleOwner
    ) {
        public static BowTieMitigatingControlDto from(BowTieMitigatingControl c, UUID consequenceExternalId) {
            return new BowTieMitigatingControlDto(
                    c.getId(), c.getExternalId(),
                    c.getBowtieId(), c.getConsequenceId(), consequenceExternalId,
                    c.getControlOrder(),
                    c.getTitleEn(), c.getTitleAr(),
                    c.getDescriptionEn(), c.getDescriptionAr(),
                    c.getControlType(), c.getLinkedControlId(),
                    c.getEffectiveness(),
                    c.getLastTestedAt(), c.getLastTestResult(),
                    c.getResponsibleRole(), c.getResponsibleOwner()
            );
        }
    }

    // ─── Full detail (header + all children) ──────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BowTieFullResponse(
            BowTieAnalysisResponse header,
            List<BowTieThreatDto> threats,
            List<BowTieConsequenceDto> consequences,
            List<BowTiePreventiveControlDto> preventiveControls,
            List<BowTieMitigatingControlDto> mitigatingControls
    ) {}

    // ─── Approval payload ────────────────────────────────────────
    public record ApproveRequest(@Size(max = 255) String approverName) {}
}
