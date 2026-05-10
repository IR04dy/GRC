package com.tss.grc.platform.realtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sprint 4 §2.7 — single-injection-point helper for non-lifecycle services
 * that want to emit live entity updates without each service repeating the
 * same null-guard + Autowired(required=false) boilerplate.
 *
 * Use:
 *   private final BroadcastHelper bcast;   // Lombok @RequiredArgsConstructor
 *   ...
 *   bcast.entity(orgId, "risk", String.valueOf(risk.getId()), payload);
 *
 * If the broadcaster bean isn't wired (unit tests, profile without WebSocket),
 * the calls are no-ops — safe to call unconditionally from service code.
 */
@Component
public class BroadcastHelper {

    /** Optional — null in unit tests, auto-wired by Spring at runtime. */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    /**
     * Broadcast an entity-level update. Caller passes the entity's external
     * identifier (UUID, code, or stringified Long ID).
     */
    public void entity(Long orgId, String entityType, String externalId, Object payload) {
        if (broadcaster != null && orgId != null && externalId != null) {
            broadcaster.broadcastEntityUpdate(orgId, entityType, externalId, payload);
        }
    }

    /** Convenience overload for services where entity ID is a Long. */
    public void entity(Long orgId, String entityType, Long id, Object payload) {
        if (broadcaster != null && orgId != null && id != null) {
            broadcaster.broadcastEntityUpdate(orgId, entityType, String.valueOf(id), payload);
        }
    }
}
