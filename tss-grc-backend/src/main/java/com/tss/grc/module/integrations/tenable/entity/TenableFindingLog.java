package com.tss.grc.module.integrations.tenable.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit row for a single Tenable finding observed by the poller. Backs
 * the V176 {@code platform.tenable_finding_log} table.
 *
 * <p>One row per (organisation, asset_uuid, plugin_id) — the unique
 * filtered index on the table enforces dedupe. A re-poll of the same
 * finding is a no-op (the application-side dedupe in
 * {@code TenableInboundService} catches it first; the constraint is the
 * safety net).
 *
 * <p>Extends {@link BaseEntity} for audit columns + ROWVERSION + soft-delete.
 */
@Entity
@Table(name = "tenable_finding_log", schema = "platform")
@SQLRestriction("is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class TenableFindingLog extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "tenable_asset_uuid", nullable = false, length = 80)
    private String tenableAssetUuid;

    @Column(name = "tenable_plugin_id", nullable = false, length = 40)
    private String tenablePluginId;

    /** Mapped NCA-ECC2 bucket (CRITICAL/HIGH/MEDIUM/LOW/INFO). */
    @Column(name = "severity", length = 20)
    private String severity;

    /** CVSS v3 base score (0.0..10.0) — nullable when Tenable hasn't computed one. */
    @Column(name = "cvss_v3_score", precision = 4, scale = 1)
    private BigDecimal cvssV3Score;

    /** Primary CVE id (e.g. CVE-2024-12345). Findings with multiple — first wins. */
    @Column(name = "cve_id", length = 50)
    private String cveId;

    @Column(name = "ingested_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime ingestedAt;

    /** FK to {@code cyber.vulnerabilities.id}. Null when the finding was
     *  dropped by the application-side dedupe before the row was written. */
    @Column(name = "vulnerability_finding_id")
    private Long vulnerabilityFindingId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}
