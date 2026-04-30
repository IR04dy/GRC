package com.tss.grc.module.compliance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FindingResponse(
    Long id,
    String title,
    Long assessmentId,
    String severity,
    String owner,
    String capa,
    LocalDate dueDate,
    String status,
    LocalDateTime createdAt
) {}
