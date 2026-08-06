package com.tss.grc.module.bcm.comms.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Slice 9-E — Microsoft Teams channel.
 *
 * <p>STUB. Microsoft Graph API integration for posting to crisis Teams
 * channels lands in Sprint 12 (alongside the ServiceNow ITSM adapter
 * already shipped in PR #177). Records a successful dispatch so the
 * cascade row + F8 chain is complete.
 */
@Component
public class TeamsChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(TeamsChannelSender.class);

    @Override public String channelType() { return "TEAMS"; }
    @Override public boolean isHealthy() { return true; }

    @Override public DeliveryMode deliveryMode() { return DeliveryMode.SIMULATED; }

    @Override
    public SendResult send(List<String> recipients, String subject, String body, String correlationId) {
        int n = recipients == null ? 0 : recipients.size();
        log.info("[teams-stub] would post corr={} to {} channels", correlationId, n);
        return SendResult.simulated(n,
            "stub-teams: " + n + " channels logged; integrate Microsoft Graph API in Sprint 12");
    }
}
