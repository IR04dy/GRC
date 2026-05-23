package com.tss.grc.module.integrations.crowdstrike.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Record of a single call made against {@code StubCrowdStrikeClient}. Used
 * by tests and by the dev-only admin endpoint to inspect what the stub saw.
 *
 * @param at        when the call happened
 * @param method    one of POLL / CONTAIN / LIFT / HIDE / HEALTH
 * @param targetId  the Falcon host id or detection id this call was about
 * @param payload   inbound payload, frozen at call time
 */
public record StubInvocation(
    Instant at,
    String method,
    String targetId,
    Map<String, Object> payload
) {}
