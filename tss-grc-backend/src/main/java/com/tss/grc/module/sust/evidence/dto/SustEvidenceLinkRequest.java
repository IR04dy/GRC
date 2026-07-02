package com.tss.grc.module.sust.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Link a real evidence item (from the generic evidence engine) to a
 * sustainability domain entity — S-C evidence bridge.
 */
public record SustEvidenceLinkRequest(
    @NotBlank String entityType,
    @NotNull Long entityId,
    @NotNull Long evidenceItemId,
    String notes
) {}
