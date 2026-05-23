package com.tss.grc.module.platform.push.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.DispatchPayload;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.RegisterRequest;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.Response;
import com.tss.grc.module.platform.push.entity.WebPushSubscription;
import com.tss.grc.module.platform.push.repository.WebPushSubscriptionRepository;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Slice 9-F — Web Push subscription registry.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>{@link #register(RegisterRequest)} — upsert the
 *       {@code (user_id, endpoint)} pair for the current user. Idempotent:
 *       re-registering the same endpoint just updates the {@code authKey}
 *       and {@code deviceLabel} and re-enables the row.</li>
 *   <li>{@link #unregister(Long)} — soft-delete a subscription owned by
 *       the current user.</li>
 *   <li>{@link #listForCurrentUser()} — power the "active devices" UI.</li>
 *   <li>{@link #dispatchToUserIds(List, DispatchPayload)} — v1 stub:
 *       resolves matching live subscriptions, writes an audit row, and
 *       emits an F8 {@code WEB_PUSH_DISPATCHED} event so downstream rules
 *       can chain on it. Real VAPID + ECDH body-signing lands in Sprint 12.</li>
 * </ol>
 *
 * <h3>F7 + F8 parity</h3>
 * <p>Every mutation writes an audit row via {@link AuditLogService} and
 * publishes a {@link DomainEvent} so the F8 engine can react (e.g. a
 * "first device registered" rule that emails the user a confirmation).
 */
@Service
@RequiredArgsConstructor
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    public static final String EVENT_REGISTERED   = "WEB_PUSH_REGISTERED";
    public static final String EVENT_UNREGISTERED = "WEB_PUSH_UNREGISTERED";
    public static final String EVENT_DISPATCHED   = "WEB_PUSH_DISPATCHED";

    private final WebPushSubscriptionRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Public API ─────────────────────────────────────────────────

    @Transactional
    public Response register(RegisterRequest req) {
        validate(req);
        Long orgId  = orgId();
        Long userId = currentUserId();

        // Upsert: if the same (user_id, endpoint) already exists, refresh
        // the auth key + label + re-enable. Browsers regenerate the auth
        // secret occasionally (e.g. after a session rotation), so this
        // shouldn't be a "conflict" — it's the expected re-register flow.
        Optional<WebPushSubscription> existing = repo
            .findByOrganisationIdAndUserIdAndEndpoint(orgId, userId, req.endpoint());

        WebPushSubscription sub = existing.orElseGet(WebPushSubscription::new);
        boolean isNew = sub.getId() == null;

        sub.setOrganisationId(orgId);
        sub.setUserId(userId);
        sub.setEndpoint(req.endpoint());
        sub.setP256dhKey(req.p256dh());
        sub.setAuthKey(req.auth());
        if (req.deviceLabel() != null && !req.deviceLabel().isBlank()) {
            sub.setDeviceLabel(req.deviceLabel());
        }
        sub.setEnabled(Boolean.TRUE);
        sub.setUpdatedAt(LocalDateTime.now());
        sub.setUpdatedBy(userId);
        if (isNew) {
            sub.setCreatedBy(userId);
        }

        WebPushSubscription saved = repo.save(sub);
        audit.log(isNew ? "CREATE" : "UPDATE", "WebPushSubscription", saved.getId(),
            (isNew ? "Web Push subscription registered" : "Web Push subscription refreshed")
                + " — user=" + userId + " device=" + nullSafeLabel(saved.getDeviceLabel()));
        emit(EVENT_REGISTERED, saved, Map.of(
            "userId",      userId,
            "deviceLabel", nullSafeLabel(saved.getDeviceLabel()),
            "isNew",       isNew
        ));
        return toResponse(saved);
    }

    @Transactional
    public void unregister(Long id) {
        Long orgId  = orgId();
        Long userId = currentUserId();
        WebPushSubscription sub = repo.findByIdAndOrganisationId(id, orgId)
            .orElseThrow(() -> new EntityNotFoundException("Web Push subscription not found: " + id));

        // Tenant isolation on top of the soft-delete @Where — we also gate
        // on user ownership so users can only unregister their own devices.
        // PLATFORM_ADMIN could be allowed via a separate admin endpoint
        // later; for now keep the surface tight.
        if (!Objects.equals(sub.getUserId(), userId)) {
            throw new EntityNotFoundException("Web Push subscription not found: " + id);
        }

        sub.setIsDeleted(Boolean.TRUE);
        sub.setEnabled(Boolean.FALSE);
        sub.setDeletedAt(LocalDateTime.now());
        sub.setDeletedBy(userId);
        sub.setUpdatedAt(LocalDateTime.now());
        sub.setUpdatedBy(userId);
        repo.save(sub);

        audit.log("ARCHIVE", "WebPushSubscription", sub.getId(),
            "Web Push subscription unregistered — user=" + userId
                + " device=" + nullSafeLabel(sub.getDeviceLabel()));
        emit(EVENT_UNREGISTERED, sub, Map.of(
            "userId",      userId,
            "deviceLabel", nullSafeLabel(sub.getDeviceLabel())
        ));
    }

    @Transactional(readOnly = true)
    public List<Response> listForCurrentUser() {
        return repo.findByOrganisationIdAndUserIdOrderByCreatedAtDesc(orgId(), currentUserId())
            .stream().map(WebPushService::toResponse).toList();
    }

    /**
     * v1 stub for the actual Web Push dispatch.
     *
     * <p>Resolves enabled subscriptions for the supplied user IDs, writes
     * one audit row per (user × device) tuple, and emits one F8 event
     * carrying the count so a downstream rule can react (e.g. fall back to
     * SMS if push reach is 0). The real VAPID-signed POST to the browser
     * push service is wired in Sprint 12 — by then this method's contract
     * is stable so callers don't need to change.
     */
    @Transactional
    public int dispatchToUserIds(List<Long> userIds, DispatchPayload payload) {
        if (userIds == null || userIds.isEmpty()) return 0;
        if (payload == null) throw new IllegalArgumentException("payload is required");

        Long orgId = orgId();
        List<WebPushSubscription> targets = repo
            .findByOrganisationIdAndUserIdInAndEnabled(orgId, userIds, Boolean.TRUE);

        LocalDateTime now = LocalDateTime.now();
        for (WebPushSubscription sub : targets) {
            sub.setLastUsedAt(now);
            repo.save(sub);
            audit.log("DISPATCH", "WebPushSubscription", sub.getId(),
                "Web Push dispatched (stub) — user=" + sub.getUserId()
                    + " code=" + nullSafe(payload.code())
                    + " severity=" + nullSafe(payload.severity()));
        }
        // One summary F8 event so a "low push reach" rule can fire
        // exactly once per dispatch instead of N times.
        emit(EVENT_DISPATCHED, null, Map.of(
            "code",           nullSafe(payload.code()),
            "severity",       nullSafe(payload.severity()),
            "targetUserCount", userIds.size(),
            "deviceCount",     targets.size()
        ));
        return targets.size();
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private static void validate(RegisterRequest req) {
        if (req == null) throw new IllegalArgumentException("request body is required");
        if (req.endpoint() == null || req.endpoint().isBlank())
            throw new IllegalArgumentException("endpoint is required");
        if (req.p256dh() == null || req.p256dh().isBlank())
            throw new IllegalArgumentException("p256dh is required");
        if (req.auth() == null || req.auth().isBlank())
            throw new IllegalArgumentException("auth is required");
    }

    private void emit(String trigger, WebPushSubscription sub, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>(extra == null ? Map.of() : extra);
        if (sub != null) {
            payload.putIfAbsent("subscriptionId", String.valueOf(sub.getId()));
        }
        Long entityId = sub == null ? null : sub.getId();
        publisher.publishEvent(DomainEvent.of(orgId(), trigger, "WebPushSubscription", entityId, payload));
    }

    private static Response toResponse(WebPushSubscription s) {
        return new Response(
            s.getId(),
            s.getExternalId(),
            s.getUserId(),
            s.getDeviceLabel(),
            s.getEnabled(),
            s.getLastUsedAt(),
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static String nullSafeLabel(String s) { return s == null || s.isBlank() ? "(unlabeled)" : s; }

    private static Long orgId() {
        return TenantContext.requireOrganisationId();
    }

    /**
     * Resolve the authenticated user's id from the security context. The
     * {@code JwtAuthenticationFilter} populates this on every authenticated
     * request, so reaching this code without a {@link GrcUserDetails} means
     * the controller's {@code @PreAuthorize} guard misfired — bail loudly.
     */
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Not authenticated — cannot register a push subscription");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof GrcUserDetails u) {
            return u.getUserId();
        }
        log.warn("WebPushService: unexpected principal type {} — refusing", principal.getClass());
        throw new IllegalStateException("Authenticated principal is not a GrcUserDetails — cannot resolve user id");
    }

    /** Test seam — used only by unit tests that bypass the security context. */
    static Long resolveUserIdForTests(GrcUserDetails details) {
        return details.getUserId();
    }
}
