package com.tss.grc.module.integrations.servicenow.dto;

/**
 * Outbound response — what we read back from ServiceNow after creating
 * or updating an incident.
 *
 * <p>The "DISABLED" sentinel (snowSysId="DISABLED") is returned by the
 * outbound service when the integration is disabled via configuration.
 * Callers should treat that as a no-op success.
 *
 * @param snowSysId   ServiceNow opaque sys_id GUID — primary external key
 * @param snowNumber  human-readable INC0012345 — what users see
 * @param state       New / In Progress / On Hold / Resolved / Closed / Canceled
 * @param createdAt   ISO-8601 timestamp the SNow record was created
 * @param url         direct deep-link to the SNow record in the UI
 */
public record ServiceNowIncidentResponse(
    String snowSysId,
    String snowNumber,
    String state,
    String createdAt,
    String url
) {
    public static final String DISABLED_SYS_ID = "DISABLED";

    public static ServiceNowIncidentResponse disabled() {
        return new ServiceNowIncidentResponse(DISABLED_SYS_ID, null, "DISABLED", null, null);
    }

    public boolean isDisabled() {
        return DISABLED_SYS_ID.equals(snowSysId);
    }
}
