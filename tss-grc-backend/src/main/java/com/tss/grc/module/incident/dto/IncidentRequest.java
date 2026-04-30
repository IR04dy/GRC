package com.tss.grc.module.incident.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record IncidentRequest(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 255)           String titleAr,
    @NotBlank @Size(max = 20)  String severity,
    @Size(max = 50)            String category,
    @Size(max = 50)            String status,
    @Size(max = 255)           String assignee,
    LocalDate                  discoveryDate,
    LocalDate                  resolutionDate,
    String                     description,
    String                     timelineJson,
    String                     lessonsLearned,
    String                     lessonsLearnedAr
) {}
