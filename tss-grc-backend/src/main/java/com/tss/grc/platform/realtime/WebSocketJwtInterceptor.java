package com.tss.grc.platform.realtime;

import com.tss.grc.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * STOMP CONNECT/SUBSCRIBE interceptor — Sprint 4 §2.7 completion.
 *
 * <h3>Two responsibilities</h3>
 *
 * <ol>
 *   <li><strong>CONNECT</strong>: validate the JWT in the {@code Authorization}
 *       header, extract the userId + orgId claims, attach them to the session
 *       so subsequent SUBSCRIBE frames know who's connected.</li>
 *   <li><strong>SUBSCRIBE</strong>: enforce that the topic destination
 *       starts with {@code /topic/org.{orgId}.} matching the connected user's
 *       claim, OR is a {@code /user/queue/...} path Spring routes per-user.
 *       Cross-tenant subscription is rejected.</li>
 * </ol>
 *
 * <p>Failure modes:</p>
 * <ul>
 *   <li>Missing/invalid JWT on CONNECT → reject the frame, ERROR sent to client</li>
 *   <li>Cross-tenant SUBSCRIBE → reject the frame, ERROR sent to client</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketJwtInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider tokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand cmd = accessor.getCommand();
        if (cmd == null) return message;

        switch (cmd) {
            case CONNECT: return handleConnect(message, accessor);
            case SUBSCRIBE: return handleSubscribe(message, accessor);
            default: return message;
        }
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String auth = firstHeader(accessor, "Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT rejected — missing Authorization header");
            throw new IllegalStateException("Missing Authorization header on STOMP CONNECT");
        }
        String token = auth.substring("Bearer ".length()).trim();
        if (!tokenProvider.validateToken(token)) {
            log.warn("STOMP CONNECT rejected — invalid JWT");
            throw new IllegalStateException("Invalid JWT on STOMP CONNECT");
        }
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long orgId = tokenProvider.getOrganisationIdFromToken(token);

        // Attach principal so /user/* destinations work + SUBSCRIBE can read it.
        accessor.setUser(new StompPrincipal(userId, orgId));
        log.debug("STOMP CONNECT accepted for userId={} orgId={}", userId, orgId);
        return message;
    }

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof StompPrincipal sp)) {
            // Should never happen — CONNECT would have set it.
            throw new IllegalStateException("STOMP SUBSCRIBE without authenticated principal");
        }

        String dest = accessor.getDestination();
        if (dest == null) return message;

        // Allow user-scoped queues (/user/* is Spring's per-user destination).
        if (dest.startsWith("/user/")) return message;

        // Topic destinations MUST start with /topic/org.{orgId}.
        String requiredPrefix = "/topic/org." + sp.getOrgId() + ".";
        if (!dest.startsWith(requiredPrefix)) {
            log.warn("STOMP SUBSCRIBE rejected — userId={} orgId={} attempted to subscribe to {}",
                sp.getUserId(), sp.getOrgId(), dest);
            throw new IllegalStateException(
                "Cross-tenant subscription denied. Topic must start with " + requiredPrefix);
        }
        return message;
    }

    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    /** Custom Principal carrying both userId and orgId so SUBSCRIBE checks can verify tenant scope. */
    public static class StompPrincipal extends AbstractAuthenticationToken implements Principal {
        private final Long userId;
        private final Long orgId;

        public StompPrincipal(Long userId, Long orgId) {
            super(java.util.Collections.emptyList());
            this.userId = userId;
            this.orgId = orgId;
            setAuthenticated(true);
        }

        @Override public String getName()    { return String.valueOf(userId); }
        @Override public Object getCredentials() { return null; }
        @Override public Object getPrincipal()   { return userId; }

        public Long getUserId() { return userId; }
        public Long getOrgId()  { return orgId; }
    }
}
