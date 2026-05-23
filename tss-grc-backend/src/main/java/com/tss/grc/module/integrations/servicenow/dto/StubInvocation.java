package com.tss.grc.module.integrations.servicenow.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Record of a single call made against {@code StubServiceNowClient}. Used
 * by tests and by the dev-only admin endpoint to inspect what the stub saw.
 *
 * @param at         when the call happened
 * @param method     one of CREATE / GET / UPDATE / CLOSE / HEALTH
 * @param snowNumber if known at call time, the INC number targeted
 * @param payload    the inbound DTO/patch payload — frozen at call time
 */
public record StubInvocation(
    Instant at,
    String method,
    String snowNumber,
    Map<String, Object> payload
) {}
