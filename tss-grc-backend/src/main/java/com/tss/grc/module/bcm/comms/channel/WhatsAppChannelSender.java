package com.tss.grc.module.bcm.comms.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Slice 9-E — WhatsApp channel.
 *
 * <p>STUB. WhatsApp Business Cloud API integration (Meta-hosted, Saudi
 * compliant via Cequens/Unifonic) lands in Sprint 12. Until then this
 * sender records a successful dispatch so the cascade row is recorded
 * and the F8 event fires.
 */
@Component
public class WhatsAppChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelSender.class);

    @Override public String channelType() { return "WHATSAPP"; }
    @Override public boolean isHealthy() { return true; }

    @Override public DeliveryMode deliveryMode() { return DeliveryMode.SIMULATED; }

    @Override
    public SendResult send(List<String> recipients, String subject, String body, String correlationId) {
        int n = recipients == null ? 0 : recipients.size();
        log.info("[whatsapp-stub] would dispatch corr={} to {} recipients", correlationId, n);
        return SendResult.simulated(n,
            "stub-whatsapp: " + n + " recipients logged; integrate Meta Cloud API in Sprint 12");
    }
}
