package com.tss.grc.module.bcm.comms.channel;

import com.tss.grc.module.bcm.comms.channel.ChannelSender.DeliveryMode;
import com.tss.grc.module.bcm.comms.channel.ChannelSender.SendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * F2-147 — a channel that does not send may not report success unqualified.
 *
 * <p>Three of the four crisis-communications senders have no gateway behind
 * them: they log a line and return {@code SendResult.ok(n, "stub-sms: …")}.
 * That trade is deliberate (the cascade row and the F8 chain stay complete in
 * environments with no SMTP or SMS contract), and it is not what this class
 * objects to. What it pins is that the trade must be <em>declared</em>:
 *
 * <ul>
 *   <li>every sender answers {@link ChannelSender#deliveryMode()} — the
 *       compiler collects that, since the method has no default;</li>
 *   <li>a sender that declares {@code SIMULATED} returns
 *       {@code SendResult.simulated(…)}, so the flag reaches the cascade row
 *       and the response rather than living only in a free-text payload;</li>
 *   <li>a sender whose body only logs must not declare {@code LIVE} — checked
 *       against the source, because that is the mistake a Sprint-12 author
 *       makes when they copy a stub and change the channel name.</li>
 * </ul>
 *
 * <p>The source-scan legs strip comments first, and — measured, not assumed —
 * that stripping is <b>not</b> load-bearing today: replacing
 * {@link #stripComments} with the identity function leaves all six tests
 * green. It stays because of the direction it protects. The tells only decide
 * <em>which</em> files get checked, so a future sender whose javadoc says
 * "we would dispatch via Twilio here" while genuinely sending live would be
 * pulled into the check and flagged for declaring a plain {@code LIVE} — a
 * false accusation, not a miss. Keeping the stripper is cheap insurance
 * against that; claiming it currently catches anything would not be true.
 */
class CrisisCommsHonestDeliveryTest {

    private static final Path CHANNEL_DIR = Path.of(
        "src/main/java/com/tss/grc/module/bcm/comms/channel");

    /**
     * The senders as they stand. Named, because "the population is what the
     * population is" is not an assertion — if a fifth channel lands, this list
     * has to be updated deliberately and its delivery mode stated here.
     */
    private static final Map<String, DeliveryMode> EXPECTED_MODES = Map.of(
        "SMS",      DeliveryMode.SIMULATED,
        "TEAMS",    DeliveryMode.SIMULATED,
        "WHATSAPP", DeliveryMode.SIMULATED,
        "EMAIL",    DeliveryMode.SIMULATED   // no JavaMailSender bean under test
    );

    /** Instantiated directly — these are stateless beans with no injected deps. */
    private static List<ChannelSender> senders() {
        EmailChannelSender email = new EmailChannelSender();
        return List.of(email, new SmsChannelSender(),
            new TeamsChannelSender(), new WhatsAppChannelSender());
    }

    // ─── The declared mode ───────────────────────────────────────────

    @Test
    @DisplayName("every sender declares a delivery mode, and none of them is silently LIVE")
    void everySenderDeclaresItsDeliveryMode() {
        for (ChannelSender s : senders()) {
            assertThat(s.deliveryMode())
                .as("%s must declare a delivery mode", s.getClass().getSimpleName())
                .isNotNull();
            assertThat(s.deliveryMode())
                .as("%s declares %s; EXPECTED_MODES says otherwise. If this channel really "
                    + "gained a gateway, update the map and the finding row — do not just "
                    + "flip the sender.", s.channelType(), s.deliveryMode())
                .isEqualTo(EXPECTED_MODES.get(s.channelType()));
        }
        assertThat(senders()).hasSize(EXPECTED_MODES.size());
    }

    // ─── The declared mode must match what send() returns ────────────

    @Test
    @DisplayName("a SIMULATED sender's SendResult carries simulated=true")
    void simulatedSendersFlagTheirResults() {
        List<String> recipients = List.of("a@example.sa", "b@example.sa", "+966500000000");
        for (ChannelSender s : senders()) {
            SendResult r = s.send(recipients, "subject", "body", "corr-1");
            boolean declaredSimulated = s.deliveryMode() == DeliveryMode.SIMULATED;
            assertThat(r.simulated())
                .as("%s declares %s but returned simulated=%s. A sender that says it does not "
                    + "reach a gateway must say so per-call too — the per-call flag is what "
                    + "reaches the cascade row.", s.channelType(), s.deliveryMode(), r.simulated())
                .isEqualTo(declaredSimulated);
        }
    }

    @Test
    @DisplayName("EMAIL flips to LIVE and stops flagging once a JavaMailSender exists")
    void emailIsLiveWhenAMailSenderIsPresent() {
        EmailChannelSender email = new EmailChannelSender();
        ReflectionTestUtils.setField(email, "mailSender", mock(JavaMailSender.class));
        ReflectionTestUtils.setField(email, "fromAddress", "crisis@example.sa");

        assertThat(email.deliveryMode()).isEqualTo(DeliveryMode.LIVE);
        assertThat(email.isHealthy()).isTrue();

        SendResult r = email.send(List.of("a@example.sa"), "s", "b", "corr-2");
        assertThat(r.simulated())
            .as("a real send must not be flagged simulated — the flag has to mean something")
            .isFalse();
        assertThat(r.successCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the simulated factory cannot be mistaken for ok(), and vice versa")
    void theFactoriesDisagreeAboutSimulation() {
        assertThat(SendResult.ok(5, "sent").simulated()).isFalse();
        assertThat(SendResult.partial(4, 1, "one bounced").simulated()).isFalse();
        assertThat(SendResult.fail(5, "gateway down").simulated()).isFalse();
        assertThat(SendResult.simulated(5, "logged only").simulated()).isTrue();
        // A simulated result still carries its counts — the cascade row and the F8
        // chain stay complete. The flag is what stops completeness reading as delivery.
        assertThat(SendResult.simulated(5, "logged only").successCount()).isEqualTo(5);
        assertThat(SendResult.simulated(5, "logged only").failureCount()).isZero();
    }

    // ─── The source, not the prose ───────────────────────────────────

    private static final Pattern LOG_ONLY_TELL = Pattern.compile(
        "\\[(?:sms|teams|whatsapp|email)-stub]|would dispatch|would post|not configured",
        Pattern.CASE_INSENSITIVE);

    /**
     * The body of {@code deliveryMode()}. ⚠ The first version of this test matched
     * {@code deliveryMode(){…DeliveryMode.LIVE} } and flagged {@link EmailChannelSender},
     * whose body is a ternary on the mail bean — the honest shape. The comment beside
     * the regex said a ternary was fine; the regex did not implement the comment. An
     * offence is a body that names LIVE and never names SIMULATED, which is what
     * "unconditionally" actually means in source.
     */
    private static final Pattern DELIVERY_MODE_BODY = Pattern.compile(
        "deliveryMode\\s*\\(\\s*\\)\\s*\\{([^}]*)}");

    @Test
    @DisplayName("no sender whose body only logs may declare LIVE unconditionally")
    void aSenderThatOnlyLogsCannotClaimToBeLive() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0, withTell = 0;

        for (Path p : sourceFiles()) {
            String code = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            scanned++;
            if (!LOG_ONLY_TELL.matcher(code).find()) continue;
            withTell++;
            Matcher body = DELIVERY_MODE_BODY.matcher(code);
            if (!body.find()) {
                // No accessor at all would not compile — the SPI method is abstract —
                // so this can only mean the scan lost the file.
                offenders.add(p.getFileName() + " → deliveryMode() body not found by the scan");
                continue;
            }
            String decl = body.group(1);
            if (decl.contains("DeliveryMode.LIVE") && !decl.contains("SIMULATED")) {
                offenders.add(p.getFileName() + " → declares LIVE with no simulated branch");
            }
        }

        // Anti-vacuity: the scan must have found the files and the tell, or it is
        // asserting nothing. Four senders; three hard stubs plus email's fallback.
        assertThat(scanned)
            .as("no sender sources scanned — the path is wrong, not the code clean")
            .isGreaterThanOrEqualTo(4);
        assertThat(withTell)
            .as("no log-only tell found in any sender after stripping comments. Either the "
                + "stubs are gone (delete this test and celebrate) or the comment stripper "
                + "ate the code.")
            .isGreaterThanOrEqualTo(4);

        assertThat(offenders)
            .as("these senders only log, yet declare DeliveryMode.LIVE unconditionally")
            .isEmpty();
    }

    @Test
    @DisplayName("no sender returns SendResult.ok from a log-only branch")
    void logOnlyBranchesUseTheSimulatedFactory() throws IOException {
        List<String> offenders = new ArrayList<>();
        int checked = 0;

        for (Path p : sourceFiles()) {
            String code = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            if (!LOG_ONLY_TELL.matcher(code).find()) continue;
            checked++;
            // Within a file that logs rather than sends, the payload strings that name
            // themselves a stub must be handed to SendResult.simulated, never ok().
            Matcher m = Pattern.compile(
                "SendResult\\.(ok|simulated)\\s*\\([^;]*?\"stub", Pattern.DOTALL).matcher(code);
            while (m.find()) {
                if ("ok".equals(m.group(1))) {
                    offenders.add(p.getFileName() + " → SendResult.ok(… \"stub…\")");
                }
            }
        }

        assertThat(checked)
            .as("no log-only sender examined — the scan found nothing to check")
            .isGreaterThanOrEqualTo(4);
        assertThat(offenders)
            .as("a payload that calls itself a stub was returned through SendResult.ok, so the "
                + "cascade row will record it as delivered")
            .isEmpty();
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static List<Path> sourceFiles() throws IOException {
        try (Stream<Path> s = Files.list(CHANNEL_DIR)) {
            return s.filter(p -> p.getFileName().toString().endsWith("ChannelSender.java"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Block and line comments out, string literals kept. Every stub in this
     * package explains in prose that it is a stub; a scan that reads prose
     * proves only that the documentation exists.
     */
    static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ")
                  .replaceAll("(?m)^\\s*//.*$", " ")
                  .replaceAll("(?m)(\\S)\\s//[^\"\\n]*$", "$1");
    }
}
