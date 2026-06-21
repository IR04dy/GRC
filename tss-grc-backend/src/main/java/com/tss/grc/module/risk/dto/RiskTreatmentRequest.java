package com.tss.grc.module.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for creating / updating a risk treatment plan (SEC-RISK-002).
 *
 * <p>Carries ONLY client-writable fields. Server-controlled fields are
 * intentionally absent so a client cannot mass-assign them:
 * <ul>
 *   <li>{@code id} / {@code externalId} — generated server-side</li>
 *   <li>{@code riskId} — taken from the path variable, not the body</li>
 *   <li>{@code status} — initialised to PLANNED on create and only changed
 *       through the dedicated transition endpoint (no direct write)</li>
 *   <li>{@code completedAt} — stamped by the transition logic, never by the client</li>
 * </ul>
 *
 * <p>On update, {@code treatmentType} is ignored: the type is fixed at creation
 * because changing it would invalidate the treatment's audit history.
 */
public record RiskTreatmentRequest(
    @NotBlank @Size(max = 20) String treatmentType,
    @NotBlank String descriptionEn,
    String descriptionAr,
    Long ownerId,
    LocalDate dueDate
) {}
