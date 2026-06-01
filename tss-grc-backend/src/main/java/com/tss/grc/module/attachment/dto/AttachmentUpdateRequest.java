package com.tss.grc.module.attachment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Metadata-only edit for an existing attachment (the binary is never touched).
 * Null fields are left unchanged. A blank {@code titleEn} clears the title.
 */
public record AttachmentUpdateRequest(
    String docType,
    String titleEn,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate validTo
) {}
