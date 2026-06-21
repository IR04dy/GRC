package com.tss.grc.module.risk.dto;

import java.time.LocalDate;

/**
 * Request body for updating a risk treatment plan (SEC-RISK-002).
 *
 * <p>Allow-list of the only fields an update may touch. All fields are
 * optional — a null value leaves the existing column unchanged (matching the
 * pre-existing partial-update semantics). Server-controlled fields
 * ({@code status}, {@code completedAt}, {@code riskId}, {@code id},
 * {@code externalId}) and the immutable {@code treatmentType} are intentionally
 * not exposed, so they cannot be mass-assigned through update().
 */
public record RiskTreatmentUpdateRequest(
    String descriptionEn,
    String descriptionAr,
    Long ownerId,
    LocalDate dueDate
) {}
