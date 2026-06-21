package com.tss.grc.module.integrations.tenable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Body of a {@code POST /accept-risk/{pluginId}} call.
 *
 * <p>The plugin id sits in the URL path; this body carries the
 * justification, the (optional) expiry, and the optional asset uuid.
 *
 * <p>Risk acceptance is a board-level decision in most KSA banks — the
 * controller restricts the endpoint to CISO / RISK_MANAGER /
 * PLATFORM_ADMIN / BOARD_MEMBER and the justification is the load-bearing
 * audit field. Minimum length is enforced (30 chars).
 *
 * @param justification free-form analyst/risk-owner justification
 * @param expiresAt     when the acceptance window closes (null = open-ended;
 *                      KSA banks generally require a date)
 * @param assetUuid     optional — null = global acceptance across all
 *                      assets with this plugin, populated = single asset
 */
public record AcceptRiskRequest(
    @NotBlank
    @Size(min = 30, max = 4000,
          message = "justification must be at least 30 characters for the audit trail")
    String justification,

    LocalDateTime expiresAt,

    // Optional — when present must be a Tenable asset UUID. Constrained so it
    // cannot smuggle path-traversal / delimiter characters into the outbound
    // Tenable API URL (SEC-INT-004).
    @Size(max = 80)
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
             message = "assetUuid must be a valid UUID")
    String assetUuid
) {}
