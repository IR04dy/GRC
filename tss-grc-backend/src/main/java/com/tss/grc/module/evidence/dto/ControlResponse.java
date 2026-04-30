package com.tss.grc.module.evidence.dto;

import java.util.List;
import java.util.UUID;

public record ControlResponse(
    Long id,
    UUID externalId,
    Long frameworkId,
    String controlCode,
    String nameEn,
    String nameAr,
    String descriptionEn,
    Boolean isActive,
    List<SubControlResponse> subControls
) {}
