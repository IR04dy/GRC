package com.tss.grc.module.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EvidenceLinkRequest(
    @NotNull Long evidenceId,
    @NotBlank String linkedModule,
    String linkedEntityType,
    Long linkedEntityId,
    String notes
) {}
