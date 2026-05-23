package com.tss.grc.module.bcm.comms.channel;

import java.util.List;

/**
 * Slice 9-E — channel-sender SPI for the multi-channel cascade.
 *
 * <p>Each implementation owns one transport. The dispatch service fans
 * out per channel — picking by {@link #channelType()} from the list of
 * registered beans — so future Sprint-12 integrations (real SMS provider,
 * Microsoft Teams Graph API, Twilio WhatsApp Business) can drop in by
 * adding a new {@code @Component} without touching the dispatch logic.
 *
 * <p>Distinct from {@code com.tss.grc.module.bcm.notify.NotificationChannel}
 * (audit Tier-2) which the legacy {@code CrisisCommsService} uses. The
 * legacy SPI returned a per-recipient list; this v2 SPI returns one
 * aggregate {@link SendResult} per dispatch so the cascade send-log
 * stays compact (one row per channel, not one row per recipient × channel).
 */
public interface ChannelSender {

    /** Stable channel id — must match the {@code channel} column on cascades. */
    String channelType();

    /** True when the channel is wired and ready (mail server reachable etc.). */
    boolean isHealthy();

    /**
     * Best-effort fan-out for one channel. Returns aggregate counts +
     * an optional {@code responsePayload} (truncated provider response)
     * so the audit row can carry forensic detail without exploding.
     */
    SendResult send(List<String> recipients, String subject, String body, String correlationId);

    /**
     * Aggregate per-channel outcome record.
     *
     * @param successCount   recipients accepted by the provider
     * @param failureCount   recipients the provider rejected
     * @param responsePayload truncated provider response / error summary —
     *                       persisted to {@code crisis_comms_cascade.response_payload}
     *                       for forensic replay
     */
    record SendResult(int successCount, int failureCount, String responsePayload) {
        public static SendResult ok(int success, String payload) {
            return new SendResult(success, 0, payload);
        }
        public static SendResult partial(int success, int failure, String payload) {
            return new SendResult(success, failure, payload);
        }
        public static SendResult fail(int attempted, String error) {
            return new SendResult(0, attempted, error);
        }
        public int total() { return successCount + failureCount; }
    }
}
