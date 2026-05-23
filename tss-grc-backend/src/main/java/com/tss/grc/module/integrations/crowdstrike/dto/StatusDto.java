package com.tss.grc.module.integrations.crowdstrike.dto;

import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;

import java.time.LocalDateTime;

/**
 * Read-only snapshot returned by {@code GET /status}.
 *
 * <p>Surfaces the two kill-switches separately (master {@code enabled} +
 * the polling-loop {@code pollingEnabled}) so an operator can tell at a
 * glance whether the adapter would respond to manual calls and/or to
 * the scheduled job.
 *
 * @param enabled              master kill-switch (manual + scheduled paths)
 * @param pollingEnabled       independent polling-loop switch
 * @param mode                 STUB or LIVE
 * @param baseUrl              configured Falcon API base URL
 * @param healthy              OAuth token + last poll succeeded recently
 * @param lastSuccessfulPollAt latest detection-log row that mapped to a
 *                             security_event, or null when no poll has
 *                             succeeded for this tenant yet
 * @param lastActionAt         latest CONTAIN/LIFT/HIDE row (any tenant action)
 */
public record StatusDto(
    boolean enabled,
    boolean pollingEnabled,
    CrowdStrikeProperties.Mode mode,
    String baseUrl,
    boolean healthy,
    LocalDateTime lastSuccessfulPollAt,
    LocalDateTime lastActionAt
) {}
