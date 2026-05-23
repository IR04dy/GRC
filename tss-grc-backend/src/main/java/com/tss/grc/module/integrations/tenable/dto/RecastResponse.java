package com.tss.grc.module.integrations.tenable.dto;

/**
 * Outbound response — what we tell our caller after a recast / accept-risk /
 * remove-recast request hit Tenable (or the stub).
 *
 * <p>Tenable.io responds to {@code POST /workbenches/assets/{uuid}/vulnerabilities/{plugin_id}/recast}
 * with a 200 + JSON envelope. Tenable.sc uses {@code POST /rest/acceptRiskRule}.
 * Both shapes are flattened to this record for audit log persistence.
 *
 * @param actionType     RECAST_SEVERITY / ACCEPT_RISK / REMOVE_RECAST
 * @param targetPluginId Tenable plugin id the action was issued against
 * @param targetAssetUuid asset uuid scoped to (null = global)
 * @param accepted       true when Tenable acknowledged the action (HTTP 2xx)
 * @param httpStatus     the underlying HTTP response code
 * @param attemptCount   1 on first-try success; higher when retries kicked in
 * @param tenableRuleId  Tenable's "rule_id" (the recast/acceptance ruleset id) if returned
 */
public record RecastResponse(
    String actionType,
    String targetPluginId,
    String targetAssetUuid,
    boolean accepted,
    int httpStatus,
    int attemptCount,
    String tenableRuleId
) {
    public static final String DISABLED_STATUS = "DISABLED";

    public static RecastResponse disabled(String actionType, String pluginId, String assetUuid) {
        return new RecastResponse(actionType, pluginId, assetUuid, false, 0, 0, DISABLED_STATUS);
    }

    public boolean isDisabled() {
        return DISABLED_STATUS.equals(tenableRuleId);
    }
}
