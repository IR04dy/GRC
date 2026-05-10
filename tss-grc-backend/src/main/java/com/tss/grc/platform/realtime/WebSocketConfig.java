package com.tss.grc.platform.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration — Sprint 4 §2.7.
 *
 * <p>Endpoint: {@code /ws} (with SockJS fallback for browsers behind
 * fussy proxies). Topic prefix: {@code /topic}, app prefix: {@code /app}.</p>
 *
 * <h3>Topic naming convention (mandatory, multi-tenant)</h3>
 *
 * Every server-side broadcast MUST namespace by organisation:
 *
 * <pre>
 *   /topic/org.{orgId}.notifications              — tenant-wide notifications
 *   /topic/org.{orgId}.entity.{type}.{id}         — record-level changes
 *   /user/queue/notifications                     — direct-to-user
 * </pre>
 *
 * <p>Cross-tenant subscription is rejected by {@link WebSocketJwtInterceptor}
 * which validates the JWT in the CONNECT frame and checks the tenant claim
 * on every SUBSCRIBE.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtInterceptor jwtInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker — fine for single-node deployments.
        // For multi-node, swap to ActiveMQ/RabbitMQ here without touching topic paths.
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // origin validation happens at the JWT interceptor
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // The JWT interceptor enforces tenant-isolated subscriptions.
        registration.interceptors(jwtInterceptor);
    }
}
