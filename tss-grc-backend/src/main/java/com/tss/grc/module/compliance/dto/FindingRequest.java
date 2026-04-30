package com.tss.grc.module.compliance.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record FindingRequest(
    @NotBlank @Size(max = 255) String title,
    Long                       assessmentId,
    @NotBlank @Size(max = 20)  String severity,
    @Size(max = 255)           String owner,
    String                     capa,
    LocalDate                  dueDate,
    @Size(max = 50)            String status
) {}
