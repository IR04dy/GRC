package com.tss.grc.module.integrations.tenable.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Record of a single call made against {@code StubTenableClient}. Used by
 * tests and by the dev-only admin endpoint to inspect what the stub saw.
 *
 * @param at        when the call happened
 * @param method    one of POLL / RECAST / ACCEPT_RISK / REMOVE_RECAST / HEALTH
 * @param targetId  the Tenable plugin id (or asset uuid) this call was about
 * @param payload   inbound payload, frozen at call time
 */
public record StubInvocation(
    Instant at,
    String method,
    String targetId,
    Map<String, Object> payload
) {}
