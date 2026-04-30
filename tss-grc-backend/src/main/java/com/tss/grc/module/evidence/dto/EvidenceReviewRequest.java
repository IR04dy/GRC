package com.tss.grc.module.evidence.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EvidenceReviewRequest(
    @NotNull Long evidenceId,
    Long reviewerId,
    LocalDate reviewDate,
    String result,
    String notesEn,
    String status
) {}
