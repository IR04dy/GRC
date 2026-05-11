package com.tss.grc.module.privacy.bridge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.privacy.bridge.entity.CyberBreachBridgeLog;

import java.time.LocalDateTime;

public final class CyberBreachBridgeLogDtos {
    private CyberBreachBridgeLogDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            Long id,
            String eventCode,
            Long cyberIncidentId, String cyberIncidentNumber,
            Long dataBreachId, String dataBreachExternalId,
            String ruleName,
            String triggerReason,
            String outcome, String outcomeDetail,
            LocalDateTime triggeredAt,
            Long triggeredBy, String triggeredByUsername
    ) {
        public static Response from(CyberBreachBridgeLog l) {
            return new Response(
                    l.getId(), l.getEventCode(),
                    l.getCyberIncidentId(), l.getCyberIncidentNumber(),
                    l.getDataBreachId(), l.getDataBreachExternalId(),
                    l.getRuleName(), l.getTriggerReason(),
                    l.getOutcome(), l.getOutcomeDetail(),
                    l.getTriggeredAt(),
                    l.getTriggeredBy(), l.getTriggeredByUsername());
        }
    }

    public record ManualTriggerRequest(
            Long cyberIncidentId,
            Integer dataSubjectsAffectedEst,
            String dataCategoriesAffected,
            String piiAssessment
    ) {}
}
