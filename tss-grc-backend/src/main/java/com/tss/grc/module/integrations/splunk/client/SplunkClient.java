package com.tss.grc.module.integrations.splunk.client;

import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventRequest;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventResponse;

/**
 * Abstraction over Splunk's HTTP Event Collector (HEC) for outbound events.
 * Two implementations:
 *
 * <ul>
 *   <li>{@link StubSplunkClient} — in-memory simulator, default in dev/demo</li>
 *   <li>{@link LiveSplunkClient} — real HTTP against an HEC endpoint</li>
 * </ul>
 *
 * <p>Selected by the {@code grc.integrations.splunk.mode} property.
 *
 * <p>Mirrors the {@code ServiceNowClient} shape so a developer who has
 * worked on one adapter recognises the other.
 */
public interface SplunkClient {

    /** Push a single event into Splunk via HEC. */
    SplunkHecEventResponse pushEvent(SplunkHecEventRequest req);

    /**
     * True when the underlying integration is reachable and authenticated.
     * The stub always returns true. The live client hits HEC's
     * {@code /services/collector/health} endpoint.
     */
    boolean isHealthy();
}
