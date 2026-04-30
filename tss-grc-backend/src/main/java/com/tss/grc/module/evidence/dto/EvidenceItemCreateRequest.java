package com.tss.grc.module.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EvidenceItemCreateRequest(
    @NotBlank @Size(max = 300) String titleEn,
    @Size(max = 300) String titleAr,
    String descriptionEn,
    Long subControlId,
    @NotBlank String evidenceType,
    String sourceModule,
    Long sourceEntityId,
    Long ownerId,
    String version,
    LocalDate validFrom,
    LocalDate validTo,
    String status,
    String fileName
) {}
