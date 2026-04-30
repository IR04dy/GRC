package com.tss.grc.module.evidence.dto;

import java.util.List;
import java.util.UUID;

public record FrameworkResponse(
    Long id,
    UUID externalId,
    String code,
    String nameEn,
    String nameAr,
    String version,
    String descriptionEn,
    String issuer,
    Boolean isActive,
    List<ControlResponse> controls
) {}
