package com.tss.grc.module.bcm.comms.channel;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice 9-E — EMAIL channel.
 *
 * <p>Wires {@link JavaMailSender} from {@code spring-boot-starter-mail}.
 * Falls back to log-and-succeed semantics when {@code spring.mail.host}
 * is not configured — same dev-friendly behaviour as
 * {@code com.tss.grc.module.bcm.notify.EmailNotificationChannel} but with
 * an aggregate SendResult instead of a per-recipient list.
 *
 * <p>To wire a real SMTP relay, add to {@code application-*.yml}:
 * <pre>
 * spring:
 *   mail:
 *     host: smtp.example.sa
 *     port: 587
 *     username: ...
 *     password: ${SMTP_PASSWORD}
 * grc:
 *   bcm:
 *     comms:
 *       email:
 *         from: crisis-comms@grc.example.sa
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class EmailChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelSender.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${grc.bcm.comms.email.from:crisis-comms@grc.local}")
    private String fromAddress;

    @Override
    public String channelType() { return "EMAIL"; }

    @Override
    public boolean isHealthy() {
        // Healthy when JavaMailSender bean is present. We do not round-trip
        // SMTP here — that is reserved for an admin "test send" endpoint.
        return mailSender != null;
    }

    @Override
    public SendResult send(List<String> recipients, String subject, String body, String correlationId) {
        if (recipients == null || recipients.isEmpty()) {
            return SendResult.ok(0, "no recipients");
        }

        // No mail bean → dev-mode log-and-succeed so the cascade row is still
        // recorded against the crisis event (audit trail integrity wins over
        // realism in environments where SMTP is not configured).
        if (mailSender == null) {
            log.info("[email-stub] would dispatch corr={} to {} recipients (subject='{}')",
                correlationId, recipients.size(), subject);
            return SendResult.ok(recipients.size(),
                "stub: JavaMailSender not configured; logged " + recipients.size() + " recipients");
        }

        int ok = 0, fail = 0;
        List<String> errors = new ArrayList<>();
        for (String to : recipients) {
            if (to == null || to.isBlank() || !to.contains("@")) {
                fail++;
                errors.add("invalid:" + to);
                continue;
            }
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(to.trim());
                msg.setSubject(subject == null ? "(no subject)" : subject);
                msg.setText((body == null ? "" : body)
                    + "\n\n---\nCrisis-Comms correlation: " + correlationId);
                mailSender.send(msg);
                ok++;
            } catch (Exception e) {
                log.warn("[email] failed to {}: {}", to, e.getMessage());
                fail++;
                errors.add(to + ":" + truncate(e.getMessage(), 80));
            }
        }
        String payload = "email: " + ok + " ok, " + fail + " failed"
            + (errors.isEmpty() ? "" : "; first errors: " + String.join(" | ",
                errors.subList(0, Math.min(3, errors.size()))));
        return fail == 0
            ? SendResult.ok(ok, payload)
            : SendResult.partial(ok, fail, payload);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
