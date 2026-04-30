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
    Boolean isActive,
    Long evidenceCount
) {}
