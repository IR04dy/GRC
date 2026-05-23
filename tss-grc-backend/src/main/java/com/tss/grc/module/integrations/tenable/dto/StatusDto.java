package com.tss.grc.module.integrations.tenable.dto;

import com.tss.grc.module.integrations.tenable.config.TenableProperties;

import java.time.LocalDateTime;

/**
 * Read-only snapshot returned by {@code GET /status}.
 *
 * <p>Surfaces the two kill-switches separately (master {@code enabled} +
 * the polling-loop {@code pollingEnabled}) so an operator can tell at a
 * glance whether the adapter would respond to manual calls and/or to
 * the scheduled job. Also exposes the platform (IO vs SC) so the SOC
 * dashboard can show the right help text without re-deriving from
 * {@code baseUrl}.
 *
 * @param enabled              master kill-switch (manual + scheduled paths)
 * @param pollingEnabled       independent polling-loop switch
 * @param mode                 STUB or LIVE
 * @param platform             IO (cloud) or SC (on-prem)
 * @param baseUrl              configured Tenable API base URL
 * @param healthy              API key valid + last poll succeeded recently
 * @param lastSuccessfulPollAt latest finding-log row, or null when no poll
 *                             has succeeded for this tenant yet
 * @param lastRecastAt         latest RECAST/ACCEPT/REMOVE row
 */
public record StatusDto(
    boolean enabled,
    boolean pollingEnabled,
    TenableProperties.Mode mode,
    TenableProperties.Platform platform,
    String baseUrl,
    boolean healthy,
    LocalDateTime lastSuccessfulPollAt,
    LocalDateTime lastRecastAt
) {}
