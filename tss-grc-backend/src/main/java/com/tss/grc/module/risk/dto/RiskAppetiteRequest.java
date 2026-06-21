package com.tss.grc.module.risk.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for creating / updating a risk appetite statement (SEC-RISK-002).
 *
 * <p>Allow-list of client-writable fields. Server-/workflow-controlled fields
 * are intentionally NOT exposed so they cannot be mass-assigned:
 * <ul>
 *   <li>{@code id}, {@code externalId}, {@code organisationId},
 *       {@code createdAt} — server-managed</li>
 *   <li>{@code status} — initialised to DRAFT on create; only changed through
 *       the dedicated approve / retire endpoints (not via create/update)</li>
 *   <li>{@code approvedBy}, {@code approvedAt} — stamped by the approve
 *       transition; a client must not be able to self-attest sign-off</li>
 * </ul>
 *
 * <p>All fields are optional on update (null leaves the column unchanged),
 * matching the pre-existing partial-update semantics; the not-null persistence
 * columns ({@code titleEn}, {@code riskDimension}, {@code appetiteLevel}) are
 * enforced at create time by the service / DB.
 */
public record RiskAppetiteRequest(
    Long categoryId,
    @Size(max = 500) String titleEn,
    @Size(max = 500) String titleAr,
    String statementEn,
    String statementAr,
    @Size(max = 60) String riskDimension,
    @Size(max = 20) String appetiteLevel,
    BigDecimal toleranceLower,
    BigDecimal toleranceUpper,
    @Size(max = 40) String unit,
    BigDecimal currentExposure,
    LocalDate nextReviewDate
) {}
