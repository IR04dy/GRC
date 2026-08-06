package com.tss.grc.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

/**
 * F2-80 — the two 403 message policies, and the boundary between them.
 *
 * <p>Before this, one exception type carried both kinds of refusal and therefore one message policy,
 * and the wrong policy was the default: {@code handleAccessDenied} builds its detail from a literal,
 * so <b>23 of 23</b> hand-thrown {@code AccessDeniedException} messages were composed and discarded.
 * The segregation-of-duty gate told a policy owner who <i>holds</i> the permission to go and obtain
 * it.
 *
 * <p>Three things have to stay true for that to remain fixed, and each is one test below:
 * <ol>
 *   <li>Domain refusals go through {@link DomainAccessDeniedException} and their message reaches the
 *       caller.</li>
 *   <li>The <b>base</b> handler stays generic. This is the one most likely to be "simplified" later:
 *       making {@code handleAccessDenied} pass {@code ex.getMessage()} through is a one-line change
 *       that looks like a cleanup and reintroduces the disclosure F2-80 declined.</li>
 *   <li>No delivered message names a person. That was a manual review of 23 messages once; here it
 *       is the rule, so the 24th cannot quietly leak.</li>
 * </ol>
 */
class DomainAccessDeniedContractTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path HANDLER = MAIN.resolve(
            Path.of("com", "tss", "grc", "common", "exception", "GlobalExceptionHandler.java"));

    /** The throw site, matched as a plain literal — no regex to break (F2-69). */
    private static final String BASE_THROW = "new AccessDeniedException(";
    private static final String DOMAIN_THROW = "new DomainAccessDeniedException(";

    /**
     * Accessors that return somebody's identity. A refusal may name the authority required; it may
     * not name the person who holds it, because the reader may be exactly the person who should not
     * learn that.
     */
    private static final Pattern IDENTITY = Pattern.compile(
            "get\\w*(Name|Email|Phone|Owner|Assignee|Member|Recipient|Reporter)\\w*\\s*\\(\\)");

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + root.toAbsolutePath(), e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p.toAbsolutePath(), e);
        }
    }

    /** The whole {@code new X(...)} expression starting at {@code from}, by paren balance. */
    private static String expressionAt(String src, int from) {
        int depth = 0;
        int i = src.indexOf('(', from);
        if (i < 0) return src.substring(from, Math.min(from + 200, src.length()));
        for (int k = i; k < src.length(); k++) {
            char c = src.charAt(k);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return src.substring(from, k + 1);
        }
        return src.substring(from);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the domain handler delivers the message; the base handler still does not")
    void theTwoPoliciesBothHold() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ProblemDetail domain = handler.handleDomainAccessDenied(new DomainAccessDeniedException(
                "Segregation of duty: the policy author/owner cannot approve their own policy"));
        assertThat(domain.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(domain.getDetail())
                .as("a domain refusal must explain itself — that is the whole of F2-80")
                .isEqualTo("Segregation of duty: the policy author/owner cannot approve their own policy");

        ProblemDetail generic = handler.handleAccessDenied(
                new AccessDeniedException("internal guard detail that must not be echoed"));
        assertThat(generic.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(generic.getDetail())
                .as("the base handler must stay generic. Passing ex.getMessage() through here is the "
                    + "one-line 'cleanup' F2-80 rejected: it would echo Spring's own @PreAuthorize "
                    + "text and, at the time, two messages naming a colleague.")
                .isEqualTo("You do not have permission to perform this action.")
                .doesNotContain("internal guard detail");
    }

    @Test
    @DisplayName("no production code throws the base AccessDeniedException — its message would vanish")
    void noProductionCodeThrowsTheBaseType() {
        Assumptions.assumeTrue(Files.isDirectory(MAIN), "src/main/java not reachable");

        List<String> offenders = new ArrayList<>();
        int domainSites = 0;

        for (Path p : javaFiles(MAIN)) {
            String src = read(p);
            domainSites += src.split(Pattern.quote(DOMAIN_THROW), -1).length - 1;
            int at = 0;
            while ((at = src.indexOf(BASE_THROW, at)) >= 0) {
                // DomainAccessDeniedException contains the base name as a substring; skip those.
                boolean isDomain = at >= 6 && src.startsWith("Domain", at + 4);
                if (!isDomain) {
                    offenders.add(MAIN.relativize(p) + " :: " + expressionAt(src, at)
                            .replaceAll("\\s+", " "));
                }
                at += BASE_THROW.length();
            }
        }

        // A collapsed scan must never read as a clean one (F2-69).
        assertThat(domainSites)
                .as("found only %d DomainAccessDeniedException throws; there should be ~23, so this "
                    + "scan is not reading the tree it thinks it is", domainSites)
                .isGreaterThanOrEqualTo(20);

        assertThat(offenders)
                .as("these throw the base type, whose handler builds its detail from a literal — so "
                    + "the message written here is discarded and the caller is told only 'You do not "
                    + "have permission to perform this action.' That is right for Spring's own "
                    + "denials and wrong for a domain rule: it misattributes the cause, telling a "
                    + "user refused by a four-eyes gate to go and obtain access they already hold. "
                    + "Throw DomainAccessDeniedException instead, and review the message for what it "
                    + "discloses first.")
                .isEmpty();
    }

    @Test
    @DisplayName("no delivered refusal names a person")
    void noDeliveredMessageInterpolatesAnIdentity() {
        Assumptions.assumeTrue(Files.isDirectory(MAIN), "src/main/java not reachable");

        List<String> leaks = new ArrayList<>();
        int checked = 0;

        for (Path p : javaFiles(MAIN)) {
            String src = read(p);
            int at = 0;
            while ((at = src.indexOf(DOMAIN_THROW, at)) >= 0) {
                checked++;
                String expr = expressionAt(src, at);
                Matcher m = IDENTITY.matcher(expr);
                if (m.find()) {
                    leaks.add(MAIN.relativize(p) + " :: interpolates " + m.group()
                            + " -> " + expr.replaceAll("\\s+", " "));
                }
                at += DOMAIN_THROW.length();
            }
        }

        assertThat(checked)
                .as("no DomainAccessDeniedException throws found — the scan broke")
                .isGreaterThanOrEqualTo(20);

        assertThat(leaks)
                .as("a DomainAccessDeniedException message is DELIVERED to the caller, and the caller "
                    + "of a refusal is by definition someone the system just decided should not "
                    + "proceed. These name a person to them. Two such messages already existed and "
                    + "were harmless only because the handler discarded them — "
                    + "CommitteeResolutionService interpolated seat.getMemberName() and "
                    + "PlanDistributionService row.getRecipientName(); both now name the authority "
                    + "required instead. Say what is required, not who holds it.")
                .isEmpty();
    }

    @Test
    @DisplayName("the handler wires both policies, and says which is which")
    void theHandlerKeepsBothPoliciesVisible() {
        Assumptions.assumeTrue(Files.isRegularFile(HANDLER), "GlobalExceptionHandler not reachable");
        String src = read(HANDLER);

        assertThat(src)
                .as("the message-bearing handler is gone; every domain refusal has silently reverted "
                    + "to the generic 403")
                .contains("@ExceptionHandler(DomainAccessDeniedException.class)");

        int domainAt = src.indexOf("handleDomainAccessDenied");
        String domainBody = src.substring(domainAt, Math.min(domainAt + 600, src.length()));
        assertThat(domainBody)
                .as("handleDomainAccessDenied must build its detail from the message it was given")
                .contains("ex.getMessage()");
    }
}
