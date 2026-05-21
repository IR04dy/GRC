package com.tss.grc.module.bcm.notify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Lock the email channel behaviour:
 *
 * <ul>
 *   <li>isHealthy returns false when from address is the default placeholder</li>
 *   <li>isHealthy returns true when a real from address is configured</li>
 *   <li>send rejects malformed email addresses with a per-recipient failure result</li>
 *   <li>send delivers to each recipient via JavaMailSender.send</li>
 *   <li>send catches per-recipient exceptions and returns DeliveryResult.fail</li>
 *   <li>send includes the correlation id in the body footer</li>
 *   <li>channelType() returns "EMAIL"</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationChannelTest {

    @Mock private JavaMailSender mailSender;
    @InjectMocks private EmailNotificationChannel channel;

    @Test void channelType_isEMAIL() {
        assertThat(channel.channelType()).isEqualTo("EMAIL");
    }

    @Test void isHealthy_defaultFrom_isFalse() {
        ReflectionTestUtils.setField(channel, "fromAddress", "bcm-noreply@example.com");
        assertThat(channel.isHealthy()).isFalse();
    }

    @Test void isHealthy_realFrom_isTrue() {
        ReflectionTestUtils.setField(channel, "fromAddress", "ops@grc.example");
        assertThat(channel.isHealthy()).isTrue();
    }

    @Test void send_rejectsMalformedAddress() {
        ReflectionTestUtils.setField(channel, "fromAddress", "ops@grc.example");
        var results = channel.send(List.of("not-an-email"), "subj", "body", "corr-1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).delivered()).isFalse();
        assertThat(results.get(0).error()).contains("invalid");
    }

    @Test void send_deliversToEachRecipient() {
        ReflectionTestUtils.setField(channel, "fromAddress", "ops@grc.example");
        var results = channel.send(List.of("a@x.com", "b@x.com"), "subj", "body", "corr-1");
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.delivered());
        verify(mailSender, org.mockito.Mockito.times(2)).send(any(SimpleMailMessage.class));
    }

    @Test void send_catchesPerRecipientException() {
        ReflectionTestUtils.setField(channel, "fromAddress", "ops@grc.example");
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        var results = channel.send(List.of("a@x.com"), "subj", "body", "corr-1");
        assertThat(results.get(0).delivered()).isFalse();
        assertThat(results.get(0).error()).contains("smtp down");
    }

    @Test void send_decoratesBodyWithCorrelationId() {
        ReflectionTestUtils.setField(channel, "fromAddress", "ops@grc.example");
        org.mockito.ArgumentCaptor<SimpleMailMessage> cap =
            org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        channel.send(List.of("a@x.com"), "subj", "Hello team", "evt-99");
        verify(mailSender).send(cap.capture());
        String text = cap.getValue().getText();
        assertThat(text).contains("Hello team");
        assertThat(text).contains("Correlation: evt-99");
        assertThat(text).contains("automated notification");
    }

    @Test void send_nullJavaMailSender_returnsFailureForEachRecipient() {
        EmailNotificationChannel ch = new EmailNotificationChannel();
        // mailSender intentionally not injected; from-address still default
        var results = ch.send(List.of("a@x.com", "b@x.com"), "subj", "body", "corr-1");
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> !r.delivered() && r.error() != null);
    }
}
