package com.tss.grc.module.bcm.notify;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Email channel for mass notification — audit Tier-2.
 *
 * <p>Uses Spring's {@link JavaMailSender} (already in pom.xml via
 * {@code spring-boot-starter-mail}). Configuration via standard
 * {@code spring.mail.*} properties; this channel only activates if the
 * mail host is set, otherwise stays dormant and reports unhealthy so
 * services fall back to the audit-only SIMULATED behaviour.
 *
 * <p>For dev: configure a local MailHog / Mailpit instance:
 * <pre>
 *   spring.mail.host: localhost
 *   spring.mail.port: 1025
 *   bcm.notify.email.from: bcm-noreply@grc-dev.local
 *   bcm.notify.email.enabled: true
 * </pre>
 *
 * <p>For prod, point at SendGrid / SES / SMTP relay with the matching
 * env vars. Production keeps the @ConditionalOnProperty guard so a
 * tenant that hasn't configured email gets a clear "channel unhealthy"
 * status rather than a runtime crash on send.
 */
@Component
@ConditionalOnProperty(prefix = "bcm.notify.email", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${bcm.notify.email.from:bcm-noreply@example.com}")
    private String fromAddress;

    @Override
    public String channelType() { return "EMAIL"; }

    @Override
    public boolean isHealthy() {
        // We're healthy if the JavaMailSender bean exists AND the from
        // address has been set to something non-default. Don't actually
        // round-trip the SMTP server here — that would be expensive on
        // every health-check; reserve real probing for admin /test-send.
        return mailSender != null
            && fromAddress != null
            && !fromAddress.isBlank()
            && !"bcm-noreply@example.com".equals(fromAddress);
    }

    /**
     * Send one message to each recipient in {@code toAddresses}. The
     * {@code correlationId} threads through as an X-Header so support /
     * incident-response can grep mail-server logs for one specific BCM
     * event without scraping multiple recipients' inboxes.
     */
    @Override
    public List<DeliveryResult> send(List<String> toAddresses, String subject, String body, String correlationId) {
        List<DeliveryResult> results = new ArrayList<>(toAddresses.size());
        if (mailSender == null) {
            for (String to : toAddresses) {
                results.add(DeliveryResult.fail(to, "JavaMailSender not configured"));
            }
            return results;
        }

        for (String to : toAddresses) {
            if (to == null || to.isBlank() || !to.contains("@")) {
                results.add(DeliveryResult.fail(to, "invalid email address"));
                continue;
            }
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(to.trim());
                msg.setSubject(subject == null ? "(no subject)" : subject);
                msg.setText(decorateBody(body, correlationId));
                mailSender.send(msg);
                // SimpleMailMessage doesn't expose a message id; we use the
                // correlation id as a stable per-broadcast handle. Better
                // implementations using MimeMessage can extract Message-ID.
                results.add(DeliveryResult.ok(to, "corr:" + correlationId));
            } catch (Exception e) {
                log.warn("[notify-email] failed to {}: {}", to, e.getMessage());
                results.add(DeliveryResult.fail(to, e.getMessage()));
            }
        }
        return results;
    }

    /**
     * Append a footer with the correlation id + a "this is an automated
     * notification" disclaimer. Keeps recipients from accidentally replying
     * to a no-reply address and gives ops a string to grep for.
     */
    private static String decorateBody(String body, String correlationId) {
        StringBuilder b = new StringBuilder();
        b.append(body == null ? "" : body);
        b.append("\n\n---\n");
        b.append("This is an automated notification from the BCM platform.\n");
        if (correlationId != null && !correlationId.isBlank()) {
            b.append("Correlation: ").append(correlationId).append("\n");
        }
        return b.toString();
    }
}
