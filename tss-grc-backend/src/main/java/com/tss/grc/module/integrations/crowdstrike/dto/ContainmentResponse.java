package com.tss.grc.module.integrations.crowdstrike.dto;

/**
 * Outbound response — what we tell our caller after a contain / lift
 * request hit Falcon (or the stub).
 *
 * <p>Falcon's {@code /devices/entities/devices-actions/v2} responds with
 * a 202 (Accepted) on a queued action plus a JSON envelope containing
 * the resource id; we capture the ids and the http status for the
 * audit row.
 *
 * @param actionType     CONTAIN_HOST / LIFT_CONTAINMENT / HIDE_HOST.
 * @param targetHostId   Falcon device id the action was issued against.
 * @param accepted       true when Falcon acknowledged the action (HTTP 2xx).
 * @param httpStatus     the underlying HTTP response code.
 * @param attemptCount   1 on first-try success; higher when retries kicked in.
 * @param falconResourceId Falcon's "resources[0].id" if present.
 */
public record ContainmentResponse(
    String actionType,
    String targetHostId,
    boolean accepted,
    int httpStatus,
    int attemptCount,
    String falconResourceId
) {
    public static final String DISABLED_STATUS = "DISABLED";

    public static ContainmentResponse disabled(String actionType, String targetHostId) {
        return new ContainmentResponse(actionType, targetHostId, false, 0, 0, DISABLED_STATUS);
    }

    public boolean isDisabled() {
        return DISABLED_STATUS.equals(falconResourceId);
    }
}
