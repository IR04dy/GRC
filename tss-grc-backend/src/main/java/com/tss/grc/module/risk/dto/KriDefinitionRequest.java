package com.tss.grc.module.risk.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for creating / updating a KRI definition (SEC-RISK-002).
 *
 * <p>Allow-list of client-writable fields. The persistence entity carries
 * server-controlled columns ({@code id}, {@code externalId},
 * {@code organisationId}, {@code createdBy}, {@code updatedBy},
 * {@code createdAt}, {@code isDeleted}) that are NOT exposed here, so a client
 * cannot mass-assign them — in particular {@code organisationId} is stamped
 * from {@link com.tss.grc.common.tenant.TenantContext} server-side.
 *
 * <p>All fields are optional on update (null leaves the column unchanged),
 * matching the pre-existing partial-update semantics. {@code nameEn} is
 * required at the persistence layer; the service enforces it on create.
 */
public record KriDefinitionRequest(
    Long riskId,
    Long categoryId,
    @Size(max = 50) String code,
    @Size(max = 255) String nameEn,
    @Size(max = 255) String nameAr,
    String descriptionEn,
    String descriptionAr,
    @Size(max = 40) String unit,
    @Size(max = 20) String direction,
    BigDecimal greenMax,
    BigDecimal amberMax,
    BigDecimal targetValue,
    @Size(max = 40) String frequency,
    @Size(max = 255) String owner,
    @Size(max = 500) String dataSource,
    @Size(max = 100) String framework,
    Boolean isActive
) {}
