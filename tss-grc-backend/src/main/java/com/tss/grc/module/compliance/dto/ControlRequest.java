package com.tss.grc.module.compliance.dto;

import jakarta.validation.constraints.*;

public record ControlRequest(
    @NotBlank @Size(max = 50)  String code,
    @NotBlank @Size(max = 255) String nameEn,
    @Size(max = 255)           String nameAr,
    @NotBlank @Size(max = 100) String framework,
    @Size(max = 100)           String domain,
    @Min(1) @Max(5)            Integer maturityLevel,
    @Size(max = 50)            String status,
    @Size(max = 255)           String owner,
    String description
) {}
