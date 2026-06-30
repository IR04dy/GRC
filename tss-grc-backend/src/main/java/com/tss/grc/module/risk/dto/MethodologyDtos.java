package com.tss.grc.module.risk.dto;

import com.tss.grc.module.risk.entity.RiskMethodologyConfig;
import jakarta.validation.constraints.NotEmpty;

import java.util.Arrays;
import java.util.List;

/** Wire DTOs for configurable methodology + multi-dimensional impact (ERM epic E-F). */
public final class MethodologyDtos {
    private MethodologyDtos() {}

    public record ConfigDto(
        List<String> impactDimensions,
        int likelihoodScaleMax,
        int impactScaleMax
    ) {
        public static ConfigDto from(RiskMethodologyConfig c) {
            String csv = c.getImpactDimensionsCsv() == null ? "" : c.getImpactDimensionsCsv();
            List<String> dims = Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
            return new ConfigDto(dims, c.getLikelihoodScaleMax(), c.getImpactScaleMax());
        }
    }

    /** All fields optional — only the provided ones are applied. */
    public record ConfigUpdateDto(
        List<String> impactDimensions,
        Integer likelihoodScaleMax,
        Integer impactScaleMax
    ) {}

    public record ImpactDimensionDto(String dimension, Integer score) {}

    public record SetImpactDto(@NotEmpty List<ImpactDimensionDto> dimensions) {}

    public record ImpactRatingsResponse(
        List<ImpactDimensionDto> dimensions,
        Integer overallImpact,   // worst (max) dimension — what feeds L×I
        int impactScaleMax
    ) {}
}
