package com.tss.grc.module.evidence.dto;

import java.util.UUID;

public record SubControlResponse(
    Long id,
    UUID externalId,
    Long controlId,
    String subControlCode,
    String nameEn,
    String nameAr,
    String descriptionEn,
    String evidenceGuidance,
    String evidenceGuidanceAr,
    Boolean isActive,
    Long evidenceCount
) {}
