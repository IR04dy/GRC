package com.tss.grc.module.integrations.servicenow.client;

import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentRequest;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentResponse;

import java.util.Map;

/**
 * Abstraction over the ServiceNow REST Table API for incidents. Two
 * implementations:
 *
 * <ul>
 *   <li>{@link StubServiceNowClient} — in-memory simulator, default in dev/demo</li>
 *   <li>{@link LiveServiceNowClient} — real HTTP against a ServiceNow instance</li>
 * </ul>
 *
 * <p>Selected by the {@code grc.integrations.servicenow.mode} property.
 */
public interface ServiceNowClient {

    /** Create a new ServiceNow incident. */
    ServiceNowIncidentResponse createIncident(ServiceNowIncidentRequest req);

    /** Look up an incident by its human-readable INC number. Returns null if not found. */
    ServiceNowIncidentResponse getIncident(String snowNumber);

    /**
     * Apply a partial update — keys map to ServiceNow column names verbatim.
     * Common keys: {@code state}, {@code work_notes}, {@code assignment_group}.
     */
    ServiceNowIncidentResponse updateIncident(String snowNumber, Map<String, Object> patch);

    /** Move the incident to Closed (state=7) with the provided closure notes. */
    boolean closeIncident(String snowNumber, String closureNotes);

    /**
     * True when the underlying integration is reachable and authenticated.
     * The stub always returns true. The live client hits a cheap endpoint.
     */
    boolean isHealthy();
}
