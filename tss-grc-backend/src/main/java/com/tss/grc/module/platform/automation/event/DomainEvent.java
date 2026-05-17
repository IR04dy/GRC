package com.tss.grc.module.platform.automation.event;

import java.util.Map;

/**
 * Domain event broadcast by any module — the input side of the
 * Automation Rules engine (F8).
 *
 * <p>Modules publish via Spring's {@code ApplicationEventPublisher}.
 * AutomationRuleEngine listens, finds enabled rules whose
 * {@code triggerEvent} matches, evaluates each rule's condition
 * against {@link #payload()}, and fires the matching ones.
 *
 * <p>Typical trigger names (string constants chosen to match the
 * V96 seed):
 * <ul>
 *   <li>{@code INCIDENT_CREATED}, {@code INCIDENT_CLASSIFIED}</li>
 *   <li>{@code EVIDENCE_EXPIRED}, {@code EVIDENCE_REJECTED}</li>
 *   <li>{@code RISK_APPETITE_BREACHED}, {@code RISK_ACCEPTANCE_EXPIRED}</li>
 *   <li>{@code BIA_UPDATED}, {@code VULN_SLA_BREACH}</li>
 *   <li>{@code POLICY_PUBLISHED}, {@code DSR_SLA_NEAR}, …</li>
 * </ul>
 *
 * @param organisationId tenant the event belongs to
 * @param triggerEvent   uppercase canonical event name
 * @param entityType     short class name of the subject (Risk, Vendor…)
 * @param entityId       PK of the subject
 * @param correlationId  optional — ties this event to other audit rows
 * @param payload        key/value snapshot the rule condition checks against
 */
public record DomainEvent(
    Long organisationId,
    String triggerEvent,
    String entityType,
    Long entityId,
    String correlationId,
    Map<String, Object> payload
) {
    public DomainEvent {
        if (triggerEvent == null || triggerEvent.isBlank())
            throw new IllegalArgumentException("triggerEvent is required");
    }

    /** Convenience builder for the common case. */
    public static DomainEvent of(Long org, String trigger, String entityType, Long entityId) {
        return new DomainEvent(org, trigger, entityType, entityId, null, Map.of());
    }

    public static DomainEvent of(Long org, String trigger, String entityType, Long entityId,
                                 Map<String, Object> payload) {
        return new DomainEvent(org, trigger, entityType, entityId, null, payload == null ? Map.of() : payload);
    }
}
