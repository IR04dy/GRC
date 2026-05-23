package com.tss.grc.module.integrations.servicenow.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Collections;
import java.util.Map;

/**
 * Outbound DTO sent to ServiceNow's Table API for the {@code incident} table.
 *
 * <p>Field mapping (GRC → ServiceNow):
 * <ul>
 *   <li>{@code shortDescription} → {@code short_description}</li>
 *   <li>{@code description}      → {@code description}</li>
 *   <li>{@code severity}         → impact + urgency (see ServiceNowOutboundService)</li>
 *   <li>{@code category}         → {@code category}</li>
 *   <li>{@code assignmentGroup}  → {@code assignment_group}</li>
 *   <li>{@code callerEmail}      → {@code caller_id} (via user lookup on the SNow side)</li>
 *   <li>{@code customFields}     → arbitrary {@code u_*} fields verbatim</li>
 * </ul>
 *
 * @param grcSourceModule one of CYBER_INCIDENT / AUDIT_FINDING / CAPA_TASK / COMPLIANCE_GAP
 * @param grcSourceId     external_id of the originating GRC record
 * @param shortDescription single-line summary — ServiceNow truncates at 160
 * @param description     long-form description
 * @param severity        CRITICAL / HIGH / MEDIUM / LOW
 * @param category        Security / Compliance / BCM
 * @param assignmentGroup target ServiceNow assignment group name
 * @param callerEmail     email of the GRC user reporting the issue
 * @param customFields    optional ServiceNow u_* custom fields
 */
public record ServiceNowIncidentRequest(
    @NotBlank String grcSourceModule,
    @NotBlank String grcSourceId,
    @NotBlank String shortDescription,
    String description,
    @NotBlank String severity,
    String category,
    String assignmentGroup,
    String callerEmail,
    Map<String, String> customFields
) {
    public ServiceNowIncidentRequest {
        if (customFields == null) {
            customFields = Collections.emptyMap();
        } else {
            customFields = Map.copyOf(customFields);
        }
    }
}
