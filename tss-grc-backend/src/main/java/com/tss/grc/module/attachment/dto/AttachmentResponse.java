package com.tss.grc.module.attachment.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttachmentResponse(
    Long id,
    String entityType,
    Long entityId,
    String docType,
    String titleEn,
    String fileName,
    Long fileSize,
    String mimeType,
    String sha256,
    LocalDate validTo,
    String classification,
    Long uploadedById,
    LocalDateTime createdAt,
    Integer version,
    Long versionGroupId,
    String downloadUrl
) {}
