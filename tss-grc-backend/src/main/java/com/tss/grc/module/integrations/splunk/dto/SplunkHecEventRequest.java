package com.tss.grc.module.integrations.splunk.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Collections;
import java.util.Map;

/**
 * Outbound DTO sent to Splunk's HTTP Event Collector (HEC) endpoint.
 *
 * <p>Field mapping (GRC → Splunk HEC envelope):
 * <ul>
 *   <li>{@code sourceModule} → {@code event.grc_source_module} (custom)</li>
 *   <li>{@code sourceId}     → {@code event.grc_source_id} (custom)</li>
 *   <li>{@code host}         → top-level {@code host}</li>
 *   <li>{@code source}       → top-level {@code source}</li>
 *   <li>{@code sourcetype}   → top-level {@code sourcetype}</li>
 *   <li>{@code index}        → top-level {@code index}</li>
 *   <li>{@code eventTimeEpochSec} → top-level {@code time} (Splunk expects epoch seconds)</li>
 *   <li>{@code event}        → top-level {@code event} (JSON object)</li>
 * </ul>
 *
 * <p>Field naming here intentionally mirrors {@code ServiceNowIncidentRequest}
 * so the two adapters read identically.
 *
 * @param sourceModule       one of SECURITY_EVENT / FINDING / RISK / CAPA_TASK / COMPLIANCE_GAP
 * @param sourceId           GRC record id (string — matches integration_external_link.grc_record_id)
 * @param host               host attribute (e.g. originating asset hostname)
 * @param source             logical Splunk source string (e.g. "tss-grc-platform")
 * @param sourcetype         Splunk sourcetype — defaults to grc:event:json
 * @param index              Splunk index — defaults to tss_grc
 * @param eventTimeEpochSec  event time as epoch seconds; null means "now"
 * @param event              free-form event payload (mapped to HEC's nested {@code event} field)
 */
public record SplunkHecEventRequest(
    @NotBlank String sourceModule,
    @NotBlank String sourceId,
    String host,
    String source,
    String sourcetype,
    String index,
    Long eventTimeEpochSec,
    Map<String, Object> event
) {
    public SplunkHecEventRequest {
        if (event == null) {
            event = Collections.emptyMap();
        } else {
            event = Map.copyOf(event);
        }
    }
}
