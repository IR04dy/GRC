package com.tss.grc.module.evidence.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvidenceRequestResponse(
    Long id,
    UUID externalId,
    String requestCode,
    Long subControlId,
    String subControlCode,
    String subControlNameEn,
    String frameworkCode,
    String entityType,
    Long entityId,
    String titleEn,
    String titleAr,
    String descriptionEn,
    Long requestedById,
    String requestedByName,
    Long assignedToId,
    String assignedToName,
    Long departmentId,
    String departmentName,
    LocalDate dueDate,
    String priority,
    String status,
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
