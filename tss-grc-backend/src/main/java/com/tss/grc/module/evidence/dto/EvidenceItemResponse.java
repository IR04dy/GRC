package com.tss.grc.module.evidence.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvidenceItemResponse(
    Long id,
    UUID externalId,
    String evidenceCode,
    Long organisationId,
    Long subControlId,
    String subControlCode,
    String subControlNameEn,
    String controlCode,
    String frameworkCode,
    String titleEn,
    String titleAr,
    String descriptionEn,
    String evidenceType,
    String sourceModule,
    Long ownerId,
    String ownerName,
    String version,
    LocalDate validFrom,
    LocalDate validTo,
    boolean isExpired,
    String status,
    String fileName,
    Long fileSize,
    String mimeType,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
