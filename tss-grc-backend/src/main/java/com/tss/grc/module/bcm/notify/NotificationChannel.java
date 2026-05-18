package com.tss.grc.module.bcm.notify;

import java.util.List;

/**
 * Mass-notification channel SPI — audit Tier-2.
 *
 * <p>Replaces the SIMULATED-only {@code CrisisCommsDistribution.delivery_status}
 * with a real outbound channel. Each implementation owns one transport
 * (email / SMS / Slack / webhook); BCM services fan-out via a list of
 * registered channels, picking the right one(s) per audience.
 *
 * <h3>Implementation contract</h3>
 * <ul>
 *   <li>{@link #channelType()} — stable id matching the {@code channel} column
 *       on {@code crisis_comms_audiences} (e.g. "EMAIL", "SMS").</li>
 *   <li>{@link #send} — best-effort delivery. Returns a per-recipient
 *       {@link DeliveryResult} so the caller knows what succeeded and what
 *       failed without forcing a partial-success exception.</li>
 *   <li>{@link #isHealthy} — used by /actuator/health and the admin settings
 *       page to surface a "channel configured" indicator. Implementations
 *       should return false if their credentials are missing rather than
 *       throwing — the channel just stays dormant until configured.</li>
 * </ul>
 *
 * <p>Failures inside one recipient do not abort the broadcast — the channel
 * collects per-recipient outcomes and the caller decides what to escalate
 * (write a SUPPLIER_INCIDENT audit row, retry, etc.).
 */
public interface NotificationChannel {

    /** Stable channel id — must match the {@code channel} column on audiences. */
    String channelType();

    /** Quick "is this channel actually wired up" probe used by admin UI + health. */
    boolean isHealthy();

    /**
     * Best-effort fan-out. Each recipient address is the channel-specific
     * identifier (email address for EMAIL, E.164 phone for SMS, channel
     * webhook URL for Slack, etc.).
     */
    List<DeliveryResult> send(List<String> toAddresses,
                              String subject,
                              String body,
                              String correlationId);

    /**
     * Per-recipient outcome record. {@code sentAt} is set on success;
     * {@code error} on failure. Both never both populated.
     */
    record DeliveryResult(String recipient, boolean delivered, String providerMessageId, String error) {
        public static DeliveryResult ok(String to, String providerId) {
            return new DeliveryResult(to, true, providerId, null);
        }
        public static DeliveryResult fail(String to, String error) {
            return new DeliveryResult(to, false, null, error);
        }
    }
}
