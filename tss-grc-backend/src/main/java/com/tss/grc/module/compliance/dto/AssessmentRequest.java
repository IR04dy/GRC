package com.tss.grc.module.compliance.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record AssessmentRequest(
    @NotBlank @Size(max = 100) String framework,
    @Size(max = 100)           String period,
    @Size(max = 255)           String auditor,
    @DecimalMin("0") @DecimalMax("100") Double score,
    @Size(max = 50)            String status,
    LocalDate                  assessmentDate,
    String                     notes
) {}
