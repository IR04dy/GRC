package com.tss.grc.module.integrations.tenable.dto;

import java.math.BigDecimal;

/**
 * Slim projection of a Tenable finding. Carries the subset of fields the
 * GRC platform needs to materialise a {@code cyber.vulnerabilities} row
 * and an {@code integration_external_link} mapping.
 *
 * <p>Tenable's full finding envelope is much wider (host metadata, scanner
 * config, plugin output, recasted-severity history, exploit availability).
 * The full JSON is captured in {@code TenableFindingLog.responsePayload}
 * via a sibling table; this record is just the structured portion we
 * triage on.
 *
 * <p>Field naming follows Tenable's API verbatim so an analyst tailing a
 * dev log can grep for the same names they see in the Tenable console.
 *
 * @param assetUuid       Tenable asset UUID (RFC 4122) — identifies the
 *                        scanned host. Required for recast/accept-risk.
 * @param pluginId        Tenable plugin id (numeric in IO, string in SC).
 *                        Composite key with assetUuid for finding dedupe.
 * @param pluginName      Human-readable plugin/check name (used as
 *                        title_en when populating cyber.vulnerabilities).
 * @param hostname        Best-effort hostname Tenable resolved for the asset.
 * @param severity        Raw Tenable severity label — CRITICAL / HIGH /
 *                        MEDIUM / LOW / INFO. The inbound service maps to
 *                        NCA-ECC2 buckets (with CVSS v3 fallback).
 * @param cvssV3Score     CVSS v3 base score 0.0..10.0 (nullable when
 *                        Tenable hasn't computed one yet).
 * @param cveId           Primary CVE identifier (e.g. CVE-2024-12345).
 *                        Tenable findings may carry multiple — first wins.
 * @param firstFound      ISO-8601 timestamp Tenable first observed it.
 * @param description     Human-readable description from the plugin body.
 */
public record TenableFindingDto(
    String assetUuid,
    String pluginId,
    String pluginName,
    String hostname,
    String severity,
    BigDecimal cvssV3Score,
    String cveId,
    String firstFound,
    String description
) {}
