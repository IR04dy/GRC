package com.tss.grc.module.risk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for recording a risk re-assessment (SEC-RISK-002).
 *
 * <p>Carries ONLY client-writable fields. Server-controlled fields are
 * intentionally absent so a client cannot mass-assign them:
 * <ul>
 *   <li>{@code id} / {@code externalId} — generated server-side</li>
 *   <li>{@code riskId} — taken from the path variable, not the body</li>
 *   <li>{@code assessorId} — derived from the authenticated principal
 *       (attribution / non-repudiation must not be forgeable)</li>
 *   <li>{@code assessmentDate} — stamped server-side (no backdating)</li>
 *   <li>{@code score} — DB-computed column</li>
 *   <li>{@code createdAt} — stamped server-side</li>
 * </ul>
 */
public record RiskAssessmentRequest(
    @NotNull @Min(1) @Max(5) Integer likelihood,
    @NotNull @Min(1) @Max(5) Integer impact,
    String assessmentNotesEn,
    String assessmentNotesAr
) {}
