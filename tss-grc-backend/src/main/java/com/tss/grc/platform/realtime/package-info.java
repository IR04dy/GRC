/**
 * Real-time push layer — Sprint 4 §2.7.
 *
 * STOMP over WebSocket (with SockJS fallback). Endpoint: {@code /ws}.
 * Multi-tenant safe by construction — see {@link com.tss.grc.platform.realtime.WebSocketConfig}
 * topic-naming convention and {@link com.tss.grc.platform.realtime.RealtimeBroadcaster}
 * helper.
 *
 * <p>Frontend client lives in {@code tss-grc-ui-v2/src/lib/realtime.ts}.</p>
 *
 * <p><strong>Status:</strong> SCAFFOLD only. Topic publishers (NotificationOutbox,
 * lifecycle services, SLA pulse scheduler) are not yet wired. Authentication
 * interceptor on the STOMP CONNECT frame (validate JWT, extract org claim,
 * attach to user principal) is the next implementation step.</p>
 */
package com.tss.grc.platform.realtime;
