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

    /**
     * True when the channel is wired and ready (mail server reachable etc.).
     *
     * <p>⚠ <b>Healthy is not the same as live.</b> A simulated sender is
     * perfectly healthy — it does exactly what it was built to do. Ask
     * {@link #deliveryMode()} whether a message actually leaves the building.
     * F2-147: for months every stub answered {@code true} here, with
     * {@code // stub always healthy} beside it, and nothing ever called the
     * method — so the one signal that could have exposed the stubs was both
     * uninformative and unread.
     */
    boolean isHealthy();

    /**
     * Whether this sender hands the message to a real gateway.
     *
     * <p>Deliberately <b>abstract, with no default</b> (F2-147). A default of
     * {@code LIVE} would let the next Sprint-12-style stub inherit an honest
     * answer it has not earned; a default of {@code SIMULATED} would let a
     * real gateway under-report. Declaring it is the price of implementing
     * the SPI, and the compiler collects it.
     */
    DeliveryMode deliveryMode();

    /**
     * Best-effort fan-out for one channel. Returns aggregate counts +
     * an optional {@code responsePayload} (truncated provider response)
     * so the audit row can carry forensic detail without exploding.
     */
    SendResult send(List<String> recipients, String subject, String body, String correlationId);

    /**
     * Does a dispatch on this channel reach a gateway, or only a log line?
     *
     * <p>This is a governance fact, not a diagnostic one. A crisis cascade
     * row is BCM evidence that people were notified; if nothing was sent,
     * the row has to say so, in the response, on the record, and on screen.
     */
    enum DeliveryMode {
        /** A real provider accepted (or rejected) the message. */
        LIVE,
        /** Logged and counted only — no gateway involved. */
        SIMULATED
    }

    /**
     * Aggregate per-channel outcome record.
     *
     * @param successCount   recipients accepted by the provider
     * @param failureCount   recipients the provider rejected
     * @param responsePayload truncated provider response / error summary —
     *                       persisted to {@code crisis_comms_cascade.response_payload}
     *                       for forensic replay
     * @param simulated      true when nothing left the building — the counts
     *                       above are what <em>would</em> have been sent.
     *                       Persisted to {@code crisis_comms_cascade.simulated}
     *                       so the evidence row stays honest long after the
     *                       response has been discarded (F2-147)
     */
    record SendResult(int successCount, int failureCount, String responsePayload, boolean simulated) {
        public static SendResult ok(int success, String payload) {
            return new SendResult(success, 0, payload, false);
        }
        public static SendResult partial(int success, int failure, String payload) {
            return new SendResult(success, failure, payload, false);
        }
        public static SendResult fail(int attempted, String error) {
            return new SendResult(0, attempted, error, false);
        }
        /**
         * Nothing was sent. {@code count} recipients were logged and counted
         * so the cascade row and the F8 chain stay complete — the flag is what
         * stops that completeness from reading as delivery.
         */
        public static SendResult simulated(int count, String payload) {
            return new SendResult(count, 0, payload, true);
        }
        public int total() { return successCount + failureCount; }
    }
}
