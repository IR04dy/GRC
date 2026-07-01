package com.tss.grc.module.risk.kri.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.risk.kri.entity.KriBreach;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request / response DTOs for the KRI engine REST surface — Sprint 13.
 */
public final class KriDtos {

    private KriDtos() {}

    // ─── Definition ───────────────────────────────────────────────

    public record KriDefinitionRequest(
            String kriCode,
            @NotBlank @Size(max = 255) String titleEn,
            @NotBlank @Size(max = 255) String titleAr,
            String descriptionEn,
            String descriptionAr,
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(max = 40) String measurementUnit,
            @Size(max = 20) String valueDirection,
            BigDecimal targetValue,
            BigDecimal greenThreshold,
            BigDecimal amberThreshold,
            BigDecimal redThreshold,
            @Size(max = 20) String frequency,
            @Size(max = 20) String sourceQueryType,
            String sourceQuery,
            Long ownerUserId,
            Long linkedRiskId,
            Long linkedFrameworkId,
            String clauseRef,
            Boolean isActive
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KriDefinitionResponse(
            Long id,
            UUID externalId,
            String kriCode,
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            String category,
            String measurementUnit,
            String valueDirection,
            BigDecimal targetValue,
            BigDecimal greenThreshold,
            BigDecimal amberThreshold,
            BigDecimal redThreshold,
            String frequency,
            String sourceQueryType,
            String sourceQuery,
            Long ownerUserId,
            Long linkedRiskId,
            Long linkedFrameworkId,
            String clauseRef,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            // Latest measurement convenience fields (null when no
            // measurement exists yet).
            BigDecimal latestValue,
            String latestStatus,
            LocalDateTime latestMeasuredAt
    ) {
        public static KriDefinitionResponse from(KriDefinition d, KriMeasurement latest) {
            return new KriDefinitionResponse(
                    d.getId(), d.getExternalId(), d.getKriCode(),
                    d.getTitleEn(), d.getTitleAr(),
                    d.getDescriptionEn(), d.getDescriptionAr(),
                    d.getCategory(), d.getMeasurementUnit(), d.getValueDirection(),
                    d.getTargetValue(), d.getGreenThreshold(), d.getAmberThreshold(),
                    d.getRedThreshold(),
                    d.getFrequency(), d.getSourceQueryType(), d.getSourceQuery(),
                    d.getOwnerUserId(), d.getLinkedRiskId(),
                    d.getLinkedFrameworkId(), d.getClauseRef(),
                    d.getIsActive(),
                    d.getCreatedAt(), d.getUpdatedAt(),
                    latest == null ? null : latest.getValue(),
                    latest == null ? null : latest.getStatus(),
                    latest == null ? null : latest.getMeasuredAt()
            );
        }

        public static KriDefinitionResponse from(KriDefinition d) {
            return from(d, null);
        }
    }

    // ─── Measurement ──────────────────────────────────────────────

    public record KriMeasurementRequest(
            @NotNull BigDecimal value,
            LocalDateTime measuredAt,
            String source,
            String notesEn,
            String notesAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KriMeasurementResponse(
            Long id,
            UUID externalId,
            Long kriId,
            String kriCode,
            LocalDateTime measuredAt,
            BigDecimal value,
            String status,
            String source,
            Long measuredByUserId,
            String notesEn,
            String notesAr
    ) {
        public static KriMeasurementResponse from(KriMeasurement m, KriDefinition d) {
            return new KriMeasurementResponse(
                    m.getId(), m.getExternalId(),
                    m.getKriId(),
                    d == null ? null : d.getKriCode(),
                    m.getMeasuredAt(), m.getValue(),
                    m.getStatus(), m.getSource(),
                    m.getMeasuredByUserId(),
                    m.getNotesEn(), m.getNotesAr()
            );
        }
    }

    // ─── Breach ───────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KriBreachResponse(
            Long id,
            UUID externalId,
            Long kriId,
            String kriCode,
            String titleEn,
            String titleAr,
            Long measurementId,
            String breachLevel,
            LocalDateTime openedAt,
            LocalDateTime closedAt,
            Long acknowledgedByUserId,
            LocalDateTime acknowledgedAt,
            Long capaTaskId
    ) {
        public static KriBreachResponse from(KriBreach b, KriDefinition d) {
            return new KriBreachResponse(
                    b.getId(), b.getExternalId(),
                    b.getKriId(),
                    d == null ? null : d.getKriCode(),
                    d == null ? null : d.getTitleEn(),
                    d == null ? null : d.getTitleAr(),
                    b.getMeasurementId(), b.getBreachLevel(),
                    b.getOpenedAt(), b.getClosedAt(),
                    b.getAcknowledgedByUserId(), b.getAcknowledgedAt(),
                    b.getCapaTaskId()
            );
        }
    }

    // ─── Dashboard ────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KriDashboardCell(
            UUID externalId,
            String kriCode,
            String titleEn,
            String titleAr,
            String category,
            String status,
            BigDecimal latestValue,
            String measurementUnit,
            LocalDateTime latestMeasuredAt,
            Boolean hasOpenBreach
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KriDashboardSnapshot(
            long totalKris,
            long activeKris,
            long greenCount,
            long amberCount,
            long redCount,
            long naCount,
            long openBreachAmber,
            long openBreachRed,
            List<KriDashboardCell> cells,
            List<KriBreachResponse> openBreaches
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KriHistoryPoint(
            LocalDateTime measuredAt,
            BigDecimal value,
            String status
    ) {
        public static KriHistoryPoint from(KriMeasurement m) {
            return new KriHistoryPoint(m.getMeasuredAt(), m.getValue(), m.getStatus());
        }
    }
}
