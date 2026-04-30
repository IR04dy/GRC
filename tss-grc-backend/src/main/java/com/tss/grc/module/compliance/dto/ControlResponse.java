package com.tss.grc.module.compliance.dto;

import java.time.LocalDateTime;

public record ControlResponse(
    Long id,
    String code,
    String nameEn,
    String nameAr,
    String framework,
    String domain,
    Integer maturityLevel,
    String status,
    String owner,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
