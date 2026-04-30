package com.tss.grc.module.risk.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record RiskResponse(
    Long id,
    UUID externalId,
    String riskCode,
    String titleEn,
    String titleAr,
    String descriptionEn,
    Long categoryId,
    String categoryName,
    String categoryNameEn,
    String categoryNameAr,
    Long ownerId,
    String ownerName,
    String ownerFullNameEn,
    String ownerUsername,
    String status,
    Integer inherentLikelihood,
    Integer inherentImpact,
    Integer inherentScore,
    Integer residualLikelihood,
    Integer residualImpact,
    Integer residualScore,
    String riskLevel,
    LocalDate identifiedDate,
    LocalDate reviewDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
