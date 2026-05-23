package com.tss.grc.module.integrations.tenable.client;

import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.dto.TenableFindingDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Abstraction over the Tenable API. Two implementations:
 *
 * <ul>
 *   <li>{@link StubTenableClient} — in-memory simulator, default in dev/demo</li>
 *   <li>{@link LiveTenableClient} — real HTTPS against
 *       {@code cloud.tenable.com} (IO) or your Tenable.sc appliance</li>
 * </ul>
 *
 * <p>Selected by the {@code grc.integrations.tenable.mode} property.
 *
 * <p>Method shape mirrors the CrowdStrike adapter where possible — the
 * inbound surface is {@link #pollFindings()} + {@link #isHealthy()}; the
 * outbound surface is the three recast verbs.
 */
public interface TenableClient {

    /**
     * Pull the next batch of findings newer than anything we've seen.
     * The implementation is responsible for tracking the high-water mark
     * across calls (the live client uses Tenable.io's {@code /vulns/export}
     * with a chunked-export cursor, or Tenable.sc's {@code /rest/analysis}
     * with a {@code lastSeen} filter; the stub returns deterministic
     * batches based on its in-memory sequence).
     */
    List<TenableFindingDto> pollFindings();

    /**
     * Recast (override) the severity Tenable assigns to a finding.
     *
     * @param pluginId    Tenable plugin id (numeric in IO, string in SC)
     * @param assetUuid   asset to scope the recast to; null = global
     * @param newSeverity one of INFO/LOW/MEDIUM/HIGH/CRITICAL
     * @param justification analyst justification — recorded in the audit log
     */
    RecastResponse recastSeverity(String pluginId, String assetUuid, String newSeverity, String justification);

    /**
     * Mark a finding as risk-accepted (Tenable hides it from the default
     * VM dashboard but keeps the row in the asset history).
     *
     * @param pluginId      plugin id
     * @param assetUuid     null = accept globally
     * @param justification analyst/risk-owner justification
     * @param expiresAt     when the acceptance window closes (null = open-ended)
     */
    RecastResponse acceptRisk(String pluginId, String assetUuid, String justification, LocalDateTime expiresAt);

    /**
     * Remove an existing recast (restore Tenable's original severity).
     * Mirrors the recastSeverity signature so the audit row carries a
     * REMOVE_RECAST action type.
     */
    RecastResponse removeRecast(String pluginId, String assetUuid);

    /**
     * True when Tenable is reachable and our API key pair is valid. Cheap —
     * the stub always returns true; the live client probes
     * {@code GET /session} (IO) or {@code GET /rest/currentUser} (SC).
     */
    boolean isHealthy();
}
