package com.tss.grc.module.integrations.splunk.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Record of a single call made against {@code StubSplunkClient}. Used
 * by tests and by the dev-only admin endpoint to inspect what the stub saw.
 *
 * @param at             when the call happened
 * @param method         one of PUSH / HEALTH
 * @param splunkEventId  if known at call time, the stub-generated event id
 * @param payload        the inbound DTO payload — frozen at call time
 */
public record StubInvocation(
    Instant at,
    String method,
    String splunkEventId,
    Map<String, Object> payload
) {}
