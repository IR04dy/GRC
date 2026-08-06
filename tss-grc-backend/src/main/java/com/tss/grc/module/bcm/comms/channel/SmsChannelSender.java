package com.tss.grc.module.bcm.comms.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Slice 9-E — SMS channel.
 *
 * <p>STUB implementation that logs the dispatch and records a success
 * result so the cascade row + F8 trail are complete end-to-end. The real
 * STC / Mobily / Zain SMS gateway is a Sprint-12 integration; that work
 * replaces the body of {@link #send} without changing the SPI.
 *
 * <p>The 160-char short variant on the template ({@code sms_body_en} /
 * {@code sms_body_ar}) is what the dispatch service passes here — the
 * service strips the email-style long body before invoking SMS.
 */
@Component
public class SmsChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelSender.class);

    @Override public String channelType() { return "SMS"; }

    /** Healthy — the stub works. {@link #deliveryMode()} is where it admits what it is. */
    @Override public boolean isHealthy() { return true; }

    @Override public DeliveryMode deliveryMode() { return DeliveryMode.SIMULATED; }

    @Override
    public SendResult send(List<String> recipients, String subject, String body, String correlationId) {
        int n = recipients == null ? 0 : recipients.size();
        log.info("[sms-stub] would dispatch corr={} to {} recipients (body={} chars)",
            correlationId, n, body == null ? 0 : body.length());
        return SendResult.simulated(n,
            "stub-sms: " + n + " recipients logged; integrate STC/Mobily HTTP gateway in Sprint 12");
    }
}
