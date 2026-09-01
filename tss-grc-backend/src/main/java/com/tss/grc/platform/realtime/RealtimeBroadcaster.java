package com.tss.grc.platform.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.time.Instant;
import java.util.Map;

/**
 * Tenant-safe broadcast helper — Sprint 4 §2.7.
 *
 * <p>Service classes call this instead of {@link SimpMessagingTemplate} directly,
 * to ensure topic naming always includes the {@code org.{orgId}.} prefix.
 * This is the contract that prevents cross-tenant leaks: there is no public
 * "send to /topic/foo" method; every send is namespaced.</p>
 *
 * <p>Wiring (incremental, follow-up tasks):</p>
 * <ul>
 *   <li>{@code NotificationOutbox.send()} → {@link #sendToUser}</li>
 *   <li>Lifecycle service {@code transition()} → {@link #broadcastEntityUpdate}</li>
 *   <li>SLA scheduler tick → {@link #broadcastOrgEvent}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeBroadcaster {

    private final SimpMessagingTemplate template;

    /**
     * Push a notification to a single user. Uses Spring's user-destination
     * mapping (frontend subscribes to {@code /user/queue/notifications}).
     */
    public void sendToUser(String userId, Object payload) {
        template.convertAndSendToUser(userId, "/queue/notifications",
            envelope("user.notification", payload));
    }

    /**
     * Broadcast a record-level change (e.g., DSR-2026-001 transitioned to DELIVERED).
     *
     * Emits TWO topics:
     *   /topic/org.{orgId}.entity.{type}.{externalId}  — for detail-page subscribers
     *   /topic/org.{orgId}.changed.{type}              — for list-page subscribers
     *
     * Two topics are needed because STOMP's simple broker doesn't support
     * wildcard subscriptions. List pages subscribe to the second topic to know
     * "something of this type changed, refresh"; detail pages subscribe to the
     * first to receive the full payload of just their record.
     */
    public void broadcastEntityUpdate(Long orgId, String entityType, String externalId, Object payload) {
        String detailTopic = String.format(Locale.ROOT, "/topic/org.%d.entity.%s.%s", orgId, entityType, externalId);
        String listTopic   = String.format(Locale.ROOT, "/topic/org.%d.changed.%s",   orgId, entityType);
        template.convertAndSend(detailTopic, envelope("entity.update", payload));
        template.convertAndSend(listTopic,   envelope("entity.changed", Map.of(
            "entityType", entityType, "externalId", externalId
        )));
        log.debug("realtime broadcast → {} + {}", detailTopic, listTopic);
    }

    /**
     * Generic org-wide broadcast — for SLA pulses, dashboard refreshes,
     * cross-record events.
     *
     * Topic shape: /topic/org.{orgId}.{kind}
     */
    public void broadcastOrgEvent(Long orgId, String kind, Object payload) {
        String topic = String.format(Locale.ROOT, "/topic/org.%d.%s", orgId, kind);
        template.convertAndSend(topic, envelope(kind, payload));
    }

    private static Map<String, Object> envelope(String type, Object payload) {
        return Map.of(
            "type", type,
            "ts", Instant.now().toString(),
            "data", payload
        );
    }
}
