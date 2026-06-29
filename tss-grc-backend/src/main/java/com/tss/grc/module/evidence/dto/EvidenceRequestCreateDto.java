package com.tss.grc.module.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EvidenceRequestCreateDto(
    @NotBlank @Size(max = 300) String titleEn,
    @Size(max = 300) String titleAr,
    String descriptionEn,
    Long subControlId,
    @Size(max = 60) String entityType,
    Long entityId,
    Long assignedToId,
    Long departmentId,
    LocalDate dueDate,
    String priority,
    String notes
) {}
