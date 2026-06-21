package com.tss.grc.module.ccm.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * SSL/TLS certificate-expiry runner — opens a TLS connection to the
 * configured host:port, extracts the leaf certificate, and asserts
 * "expires more than N days from now".
 *
 * <p>The cert lookup is delegated to a strategy seam ({@link CertLookup})
 * so tests can stub the actual TCP open and certificate decoding while
 * still exercising the threshold-comparison + parsing logic.
 */
@Slf4j
@Component
public class SslCertExpiryRunner implements ControlTestRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Strategy seam — production path uses {@link #defaultLookup(String, int, int)}. */
    private final CertLookup lookup;

    public SslCertExpiryRunner() {
        this(SslCertExpiryRunner::defaultLookup);
    }

    /** Test seam — supply a mock {@link CertLookup}. */
    SslCertExpiryRunner(CertLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public String testType() { return "SSL_CERT_EXPIRY"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        try {
            Map<String, Object> cfg = StubRunnerSupport.parseJson(def.getTargetConfigJson());
            Map<String, Object> cond = StubRunnerSupport.parseJson(def.getExpectedConditionJson());
            String host = StubRunnerSupport.strOr(cfg, "host", "");
            int port = StubRunnerSupport.intOr(cfg, "port", 443);
            int minDays = StubRunnerSupport.intOr(cond, "minDaysToExpiry", 30);
            int timeoutMs = Math.max(1_000, StubRunnerSupport.defaultIfNull(def.getTimeoutSeconds(), 30) * 1000);

            if (host.isBlank()) {
                return RunResult.error("target_config_json.host missing", StubRunnerSupport.millisSince(start));
            }

            Instant notAfter = lookup.fetchNotAfter(host, port, timeoutMs);
            long daysToExpiry = Duration.between(Instant.now(), notAfter).toDays();

            Map<String, Object> actual = new HashMap<>();
            actual.put("host", host);
            actual.put("port", port);
            actual.put("notAfter", notAfter.toString());
            actual.put("daysToExpiry", daysToExpiry);
            actual.put("minDaysToExpiry", minDays);
            String actualJson;
            try {
                actualJson = MAPPER.writeValueAsString(actual);
            } catch (Exception jsonEx) {
                actualJson = "{}";
            }

            int duration = StubRunnerSupport.millisSince(start);
            if (daysToExpiry > minDays) {
                return RunResult.pass(actualJson, duration);
            }
            return RunResult.fail(actualJson,
                    "Certificate for " + host + ":" + port + " expires in "
                            + daysToExpiry + " days (floor " + minDays + ")",
                    duration);
        } catch (RuntimeException ex) {
            log.warn("SSL cert-expiry runner failed: {}", ex.getMessage());
            return RunResult.error(ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    StubRunnerSupport.millisSince(start));
        }
    }

    // ─── Production cert lookup ───────────────────────────────────

    /**
     * Open a TLS socket, take the leaf cert, return its {@code notAfter}.
     * Throws on any I/O failure — caller converts to RunResult.error.
     */
    static Instant defaultLookup(String host, int port, int timeoutMs) {
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                socket.startHandshake();
                Certificate[] chain = socket.getSession().getPeerCertificates();
                if (chain == null || chain.length == 0) {
                    throw new IllegalStateException("Empty TLS certificate chain for " + host + ":" + port);
                }
                X509Certificate leaf = (X509Certificate) chain[0];
                return leaf.getNotAfter().toInstant();
            }
        } catch (Exception e) {
            throw new RuntimeException("TLS handshake to " + host + ":" + port + " failed: " + e.getMessage(), e);
        }
    }

    /** Pluggable TLS leaf-cert {@code notAfter} fetcher. */
    @FunctionalInterface
    interface CertLookup {
        Instant fetchNotAfter(String host, int port, int timeoutMs);
    }
}
