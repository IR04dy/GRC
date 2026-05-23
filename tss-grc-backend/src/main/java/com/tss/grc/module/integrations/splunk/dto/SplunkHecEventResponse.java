package com.tss.grc.module.integrations.splunk.dto;

/**
 * Outbound response — what we read back from Splunk HEC after pushing an event.
 *
 * <p>The "DISABLED" sentinel ({@code splunkEventId="DISABLED"}) is returned by
 * the outbound service when the integration is disabled via configuration.
 * Callers should treat that as a no-op success.
 *
 * <p>Splunk HEC responses look like:
 * <pre>
 * { "text": "Success", "code": 0, "ackId": 123, "invalid-event-number": null }
 * </pre>
 * We capture {@code ackId} (HEC-side acknowledgement id when indexer ACK is on),
 * the response code, and our deterministic stub-generated event id for the link
 * row in {@code integration_external_link}.
 *
 * @param splunkEventId   deterministic event id (stub) or HEC ackId (live)
 * @param hecCode         HEC numeric code — 0 = success, &gt;0 = HEC-side error
 * @param hecText         HEC response text — "Success" on a good push
 * @param httpStatus      HTTP status of the underlying request (200 / 4xx / 5xx)
 * @param hecIndex        Splunk index the event was routed to
 * @param attemptCount    how many attempts the push needed (1 = first try)
 */
public record SplunkHecEventResponse(
    String splunkEventId,
    int hecCode,
    String hecText,
    int httpStatus,
    String hecIndex,
    int attemptCount
) {
    public static final String DISABLED_EVENT_ID = "DISABLED";

    public static SplunkHecEventResponse disabled() {
        return new SplunkHecEventResponse(DISABLED_EVENT_ID, -1, "DISABLED", 0, null, 0);
    }

    public boolean isDisabled() {
        return DISABLED_EVENT_ID.equals(splunkEventId);
    }

    /** True on a successful HEC POST (HTTP 200 + HEC code 0). */
    public boolean isSuccess() {
        return httpStatus == 200 && hecCode == 0;
    }
}
