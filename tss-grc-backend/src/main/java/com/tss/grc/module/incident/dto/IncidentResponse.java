package com.tss.grc.module.incident.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IncidentResponse(
    Long id,
    String title,
    String titleAr,
    String severity,
    String category,
    String status,
    String assignee,
    LocalDate discoveryDate,
    LocalDate resolutionDate,
    String description,
    String timelineJson,
    String lessonsLearned,
    String lessonsLearnedAr,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
