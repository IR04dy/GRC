package com.tss.grc.module.integrations.tenable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of a {@code POST /recast/{pluginId}} call.
 *
 * <p>The plugin id sits in the URL path; this body carries the
 * <em>new severity</em>, the optional asset uuid (null = global recast
 * across all assets with this plugin), and the analyst's justification.
 *
 * <p>Justification is the load-bearing audit field — NCA ECC-2 / SAMA
 * CSF expect "who recasted this finding and why" to be recoverable from
 * the audit log. Minimum length is enforced (30 chars) so analysts can't
 * paper over compliance with "fix later".
 *
 * @param newSeverity   one of INFO / LOW / MEDIUM / HIGH / CRITICAL
 * @param justification free-form analyst justification (min 30 chars)
 * @param assetUuid     optional — null = global recast (every asset with
 *                      this plugin), populated = recast just this finding
 */
public record RecastRequest(
    @NotBlank
    @Pattern(regexp = "INFO|LOW|MEDIUM|HIGH|CRITICAL",
             message = "newSeverity must be one of INFO, LOW, MEDIUM, HIGH, CRITICAL")
    String newSeverity,

    @NotBlank
    @Size(min = 30, max = 4000,
          message = "justification must be at least 30 characters for the audit trail")
    String justification,

    @Size(max = 80)
    String assetUuid
) {}
