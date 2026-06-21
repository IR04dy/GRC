package com.tss.grc.module.integrations.splunk.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Captures the <em>exact raw bytes</em> of an inbound Splunk webhook request so
 * the controller can verify the {@code X-Splunk-Signature} HMAC over the same
 * bytes Splunk signed — not over an already-consumed / re-serialised payload
 * (SEC-INT-005).
 *
 * <p>The buffered body is exposed two ways:
 * <ul>
 *   <li>as the {@code RAW_BODY} request attribute (a UTF-8 String) that
 *       {@link SplunkWebhookController#verifySignature} reads, and</li>
 *   <li>via a wrapped request whose {@code getInputStream()} / {@code getReader()}
 *       replay the buffered bytes, so the downstream {@code @RequestBody} JSON
 *       binder still parses the same content.</li>
 * </ul>
 *
 * <p>Scoped to the Splunk webhook path only — every other route is passed
 * through untouched so we never buffer large or streaming request bodies
 * elsewhere.
 */
@Component
@Order(Integer.MIN_VALUE + 50) // before Spring Security / the dispatcher reads the body
public class SplunkRawBodyCachingFilter extends OncePerRequestFilter {

    static final String RAW_BODY_ATTR = "RAW_BODY";
    private static final String WEBHOOK_PATH = "/api/v1/integrations/splunk/webhook";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only buffer the Splunk webhook POST; leave every other request alone.
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith(WEBHOOK_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        request.setAttribute(RAW_BODY_ATTR, new String(body, StandardCharsets.UTF_8));
        chain.doFilter(new CachedBodyRequestWrapper(request, body), response);
    }

    /** Request wrapper that replays a pre-read body for the JSON binder. */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream backing = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return backing.read(); }
                @Override public boolean isFinished() { return backing.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { /* no-op */ }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset cs = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), cs));
        }
    }
}
