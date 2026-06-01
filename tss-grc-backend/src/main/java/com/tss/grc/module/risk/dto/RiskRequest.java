package com.tss.grc.module.risk.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RiskRequest(
    @NotBlank @Size(max = 300) String titleEn,
    @Size(max = 300) String titleAr,
    String descriptionEn,
    String descriptionAr,
    Long categoryId,
    Long ownerId,
    @Min(1) @Max(5) Integer inherentLikelihood,
    @Min(1) @Max(5) Integer inherentImpact,
    @Min(1) @Max(5) Integer residualLikelihood,
    @Min(1) @Max(5) Integer residualImpact,
    String status,
    LocalDate reviewDate
) {}
