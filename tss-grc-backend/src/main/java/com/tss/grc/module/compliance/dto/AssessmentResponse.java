package com.tss.grc.module.compliance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssessmentResponse(
    Long id,
    String framework,
    String period,
    String auditor,
    Double score,
    String status,
    LocalDate assessmentDate,
    String notes,
    LocalDateTime createdAt
) {}
