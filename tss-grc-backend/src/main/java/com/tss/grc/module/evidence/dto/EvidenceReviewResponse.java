package com.tss.grc.module.evidence.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvidenceReviewResponse(
    Long id,
    UUID externalId,
    Long evidenceId,
    String evidenceTitleEn,
    Long reviewerId,
    String reviewerName,
    LocalDate reviewDate,
    String result,
    String notesEn,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
