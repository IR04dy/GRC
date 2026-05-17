package com.tss.grc.module.risk.emerging.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.risk.emerging.entity.EmergingRisk;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class EmergingRiskDtos {
    private EmergingRiskDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 255) String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            @NotBlank @Size(max = 20) String category,
            String source,
            String sourceUrl,
            String sourceCitation,
            @NotBlank @Size(max = 20) String horizon,
            String velocity,
            String signalStrength,
            Integer estimatedLikelihood,
            Integer estimatedImpact,
            String mitigationThoughtsEn,
            String mitigationThoughtsAr,
            String owner,
            String tags
    ) {}

    public record UpdateRequest(
            String titleEn, String titleAr,
            String descriptionEn, String descriptionAr,
            String category, String source, String sourceUrl, String sourceCitation,
            String horizon, String velocity, String signalStrength,
            Integer estimatedLikelihood, Integer estimatedImpact,
            String mitigationThoughtsEn, String mitigationThoughtsAr,
            String owner, String tags, LocalDate nextReviewDue
    ) {}

    public record ReviewRequest(@NotBlank String notes, LocalDate nextReviewDue) {}
    public record EscalateRequest(@NotBlank @Size(max = 64) String linkedRiskCode) {}
    public record DismissRequest(@NotBlank @Size(max = 500) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            Long id, String riskCode, String externalId,
            String titleEn, String titleAr,
            String descriptionEn, String descriptionAr,
            String category, String source, String sourceUrl, String sourceCitation,
            String horizon, String velocity, String signalStrength,
            Integer estimatedLikelihood, Integer estimatedImpact,
            String mitigationThoughtsEn, String mitigationThoughtsAr,
            Long linkedRiskId, String linkedRiskCode,
            String status,
            LocalDateTime firstObservedAt, LocalDateTime lastReviewedAt,
            String lastReviewNotes, LocalDate nextReviewDue,
            LocalDateTime escalatedAt, LocalDateTime maturedAt,
            LocalDateTime dismissedAt, String dismissReason,
            String owner, String tags,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Response from(EmergingRisk e) {
            return new Response(
                    e.getId(), e.getRiskCode(), e.getExternalId(),
                    e.getTitleEn(), e.getTitleAr(),
                    e.getDescriptionEn(), e.getDescriptionAr(),
                    e.getCategory(), e.getSource(), e.getSourceUrl(), e.getSourceCitation(),
                    e.getHorizon(), e.getVelocity(), e.getSignalStrength(),
                    e.getEstimatedLikelihood(), e.getEstimatedImpact(),
                    e.getMitigationThoughtsEn(), e.getMitigationThoughtsAr(),
                    e.getLinkedRiskId(), e.getLinkedRiskCode(),
                    e.getStatus(),
                    e.getFirstObservedAt(), e.getLastReviewedAt(),
                    e.getLastReviewNotes(), e.getNextReviewDue(),
                    e.getEscalatedAt(), e.getMaturedAt(),
                    e.getDismissedAt(), e.getDismissReason(),
                    e.getOwner(), e.getTags(),
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }
}
