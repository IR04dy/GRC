package com.tss.grc.module.integrations.splunk.webhook;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.integrations.splunk.config.SplunkProperties;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Inbound webhook receiver — Splunk's "Webhook" alert action posts here when
 * a saved search fires an alert. We materialise the alert as a
 * {@code socops.security_event} row so the SOC analyst sees it in the
 * existing triage queue.
 *
 * <h3>Auth model</h3>
 *
 * <p>Two layers:
 * <ol>
 *   <li>Role guard — Splunk's webhook step authenticates as a dedicated
 *       service account with one of {@code PLATFORM_ADMIN /
 *       SOC_AUTOMATION / INTEGRATION_SERVICE}. Matches the ServiceNow
 *       webhook pattern.</li>
 *   <li>Optional HMAC — when {@code grc.integrations.splunk.webhook-shared-secret}
 *       is set, the request must carry a hex-encoded HMAC-SHA256 of the raw
 *       body in the {@code X-Splunk-Signature} header. Skipped when no
 *       secret is configured.</li>
 * </ol>
 *
 * <h3>Splunk alert payload shape</h3>
 *
 * <p>Splunk's webhook action posts a JSON envelope like:
 * <pre>
 * {
 *   "result": {
 *     "host": "edge-vpn-01",
 *     "severity": "high",
 *     "signature": "Repeated 401 on edge VPN",
 *     "_time": "2026-05-23T08:14:00.000+03:00"
 *   },
 *   "search_name": "TSS - Edge VPN brute-force",
 *   "result_id": "scheduler__admin__search__..._at_1716440040_3"
 * }
 * </pre>
 * Field name variations (Splunk Enterprise vs Splunk Cloud, ES vs core) are
 * tolerated — we look in {@code result.*} and at the envelope top level.
 *
 * <h3>Deduplication</h3>
 *
 * <p>We compose an idempotency key from {@code (result_id || search_name + _time)}
 * and store it in {@code SecurityEvent.externalId}. A repeat-post for the
 * same key is short-circuited and returns {@code duplicate=true}.
 */
@RestController
@RequestMapping("/api/v1/integrations/splunk/webhook")
@Tag(name = "Splunk Webhook")
@SecurityRequirement(name = "bearerAuth")
public class SplunkWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SplunkWebhookController.class);
    private static final String SOURCE_SYSTEM = "SPLUNK";
    private static final String SIGNATURE_HEADER = "X-Splunk-Signature";

    private final SplunkProperties props;
    private final SecurityEventRepository securityEvents;
    private final IntegrationExternalLinkRepository linkRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    public SplunkWebhookController(
        SplunkProperties props,
        SecurityEventRepository securityEvents,
        IntegrationExternalLinkRepository linkRepo,
        AuditLogService audit,
        ApplicationEventPublisher publisher
    ) {
        this.props = props;
        this.securityEvents = securityEvents;
        this.linkRepo = linkRepo;
        this.audit = audit;
        this.publisher = publisher;
    }

    @PostMapping
    // F2-05 slice 4: was hasAnyRole('PLATFORM_ADMIN','SOC_AUTOMATION','INTEGRATION_SERVICE') — the
    // two service-account names do not exist in iam.roles, so the only grantable option in that list
    // was PLATFORM_ADMIN. CYBER:WRITE is held by all eight seeded *_INGEST machine roles, SIEM_INGEST
    // included, which is the account this webhook was built for.
    @PreAuthorize("hasPermission('CYBER','WRITE')")
    @Operation(summary = "Splunk → GRC: alert webhook posted by a Splunk saved search")
    @Transactional
    public ResponseEntity<Map<String, Object>> alertReceived(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = SIGNATURE_HEADER, required = false) String signatureHeader,
        HttpServletRequest httpRequest
    ) {
        // ── HMAC verification (when configured) ─────────────────────────
        if (props.getWebhookSharedSecret() != null && !props.getWebhookSharedSecret().isBlank()) {
            if (signatureHeader == null || signatureHeader.isBlank()) {
                log.warn("[Splunk-Webhook] missing {} header but a shared secret is configured", SIGNATURE_HEADER);
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("received", false);
                err.put("reason", "missing signature");
                return ResponseEntity.status(401).body(err);
            }
            if (!verifySignature(httpRequest, signatureHeader, props.getWebhookSharedSecret())) {
                log.warn("[Splunk-Webhook] HMAC signature mismatch — rejecting");
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("received", false);
                err.put("reason", "invalid signature");
                return ResponseEntity.status(401).body(err);
            }
        }

        // ── Parse Splunk payload ────────────────────────────────────────
        Map<String, Object> result = asMap(payload.get("result"));
        String searchName = str(payload.get("search_name"));
        String resultId = str(payload.get("result_id"));
        String host = str(result.get("host"));
        String splunkSeverityRaw = str(result.get("severity"));
        String signature = firstNonBlank(
            str(result.get("signature")),
            str(result.get("signature_id")),
            searchName);
        String rawTime = str(result.get("_time"));

        String mappedSeverity = mapSplunkSeverity(splunkSeverityRaw);
        LocalDateTime eventTime = parseSplunkTime(rawTime);
        String externalId = composeExternalId(resultId, searchName, rawTime);

        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            log.warn("[Splunk-Webhook] no tenant in context — JWT filter did not run?");
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("received", false);
            err.put("reason", "missing tenant context");
            return ResponseEntity.status(400).body(err);
        }

        // ── Idempotent upsert into socops.security_event ────────────────
        Optional<SecurityEvent> existing = securityEvents
            .findByOrganisationIdAndSourceSystemAndExternalId(orgId, SOURCE_SYSTEM, externalId);
        boolean duplicate = existing.isPresent();
        SecurityEvent ev = existing.orElseGet(SecurityEvent::new);

        if (!duplicate) {
            ev.setOrganisationId(orgId);
            ev.setExternalId(externalId);
            ev.setSourceSystem(SOURCE_SYSTEM);
            ev.setEventTime(eventTime != null ? eventTime : LocalDateTime.now());
            ev.setIngestedAt(LocalDateTime.now());
            ev.setSeverity(mappedSeverity);
            ev.setCategory("SIEM_ALERT");
            ev.setRuleName(truncate(signature, 300));
            ev.setSourceHost(truncate(host, 255));
            ev.setStatus("NEW");
            ev.setSlaDueAt(computeSlaDue(ev.getEventTime(), mappedSeverity));
            ev.setRawPayload(asString(payload));
            SecurityEvent saved = securityEvents.save(ev);

            // Link the event into integration_external_link so /status can
            // report Splunk-sourced events (matches ServiceNow pattern).
            recordExternalLink(orgId, saved, externalId);

            audit.log("SPLUNK_ALERT_RECEIVED", "SecurityEvent", saved.getId(),
                "Splunk alert '" + (searchName == null ? "—" : searchName) + "' "
                    + "[" + mappedSeverity + "] from " + (host == null ? "—" : host));
            Map<String, Object> evPayload = new LinkedHashMap<>();
            evPayload.put("severity", mappedSeverity);
            evPayload.put("searchName", searchName == null ? "" : searchName);
            evPayload.put("host", host == null ? "" : host);
            evPayload.put("externalId", externalId);
            publisher.publishEvent(DomainEvent.of(
                orgId, "SPLUNK_ALERT_RECEIVED", "SecurityEvent", saved.getId(), evPayload));
        }

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("received", true);
        ack.put("duplicate", duplicate);
        ack.put("securityEventId", ev.getId());
        ack.put("severity", mappedSeverity);
        ack.put("externalId", externalId);
        ack.put("at", LocalDateTime.now().toString());
        return ResponseEntity.ok(ack);
    }

    // ── severity mapping: Splunk → NCA ECC-2 ────────────────────────────

    /**
     * Map a Splunk severity (string or numeric 1..5/6) to GRC severity used
     * in {@code socops.security_event}. Saudi NCA ECC-2 buckets are
     * CRITICAL / HIGH / MEDIUM / LOW. Splunk severities encountered in the
     * wild:
     *
     * <pre>
     *   "informational" / "info" / 1 / "very low"  → LOW
     *   "low"                            / 2       → LOW
     *   "medium"                         / 3       → MEDIUM
     *   "high"                           / 4       → HIGH
     *   "critical" / "severe"            / 5 / 6   → CRITICAL
     * </pre>
     *
     * Unknown / null defaults to MEDIUM — the SOC analyst will rerank during
     * triage; that's preferable to a silent LOW that misses an SLA window.
     */
    public static String mapSplunkSeverity(String raw) {
        if (raw == null || raw.isBlank()) return "MEDIUM";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        // numeric — Splunk uses 1..6 in different contexts; treat >=5 as CRITICAL.
        try {
            int n = Integer.parseInt(s);
            if (n >= 5) return "CRITICAL";
            if (n == 4) return "HIGH";
            if (n == 3) return "MEDIUM";
            return "LOW";
        } catch (NumberFormatException ignore) { /* fall through */ }
        return switch (s) {
            case "critical", "severe", "fatal" -> "CRITICAL";
            case "high", "elevated" -> "HIGH";
            case "medium", "moderate", "warning" -> "MEDIUM";
            case "low", "info", "informational", "very low", "debug", "notice" -> "LOW";
            default -> "MEDIUM";
        };
    }

    /** NCA ECC-2 SLA windows: Critical 1h / High 4h / Medium 24h / Low 72h. */
    private static LocalDateTime computeSlaDue(LocalDateTime eventTime, String severity) {
        if (eventTime == null || severity == null) return null;
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> eventTime.plusHours(1);
            case "HIGH"     -> eventTime.plusHours(4);
            case "MEDIUM"   -> eventTime.plusHours(24);
            default          -> eventTime.plusHours(72);
        };
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void recordExternalLink(Long orgId, SecurityEvent saved, String externalId) {
        try {
            IntegrationExternalLink link = linkRepo
                .findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
                    orgId, "SPLUNK", "SECURITY_EVENT", externalId)
                .orElseGet(IntegrationExternalLink::new);
            link.setOrganisationId(orgId);
            link.setIntegration("SPLUNK");
            link.setGrcModule("SECURITY_EVENT");
            link.setGrcRecordId(externalId);
            link.setExternalId(externalId);
            link.setExternalNumber(externalId);
            link.setLastPulledAt(LocalDateTime.now());
            link.setLastKnownState("ALERTED");
            link.setUpdatedAt(LocalDateTime.now());
            linkRepo.save(link);
        } catch (Exception ex) {
            log.warn("[Splunk-Webhook] failed to write external_link row: {}", ex.getMessage());
        }
    }

    /**
     * Verify HMAC-SHA256 of the raw request body against the
     * {@code X-Splunk-Signature} header. The header value is hex-encoded.
     *
     * <p>The HMAC must be computed over the <em>exact bytes Splunk signed</em>.
     * Those bytes are stashed in the {@code RAW_BODY} request attribute by
     * {@link SplunkRawBodyCachingFilter} <em>before</em> the {@code @RequestBody}
     * binder consumes the stream. If that attribute is missing or empty we
     * <b>fail closed</b> (SEC-INT-005) rather than hashing an empty / partial
     * re-read of an already-drained stream — the old fallback let an attacker
     * present {@code HMAC('')} and have it validate.
     */
    private boolean verifySignature(HttpServletRequest req, String headerHex, String secret) {
        try {
            Object cached = req.getAttribute("RAW_BODY");
            if (!(cached instanceof String raw) || raw.isEmpty()) {
                log.warn("[Splunk-Webhook] raw body unavailable — failing signature check closed");
                return false; // fail closed; never hash an empty/partial body
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return constantTimeEquals(expected, headerHex.trim());
        } catch (Exception ex) {
            log.warn("[Splunk-Webhook] signature verification failed: {}", ex.getMessage());
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    private static String composeExternalId(String resultId, String searchName, String rawTime) {
        if (resultId != null && !resultId.isBlank()) return truncate(resultId, 64);
        String fallback = (searchName == null ? "splunk" : searchName) + "@" + (rawTime == null ? "now" : rawTime);
        return truncate(fallback, 64);
    }

    private static LocalDateTime parseSplunkTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try { return LocalDateTime.parse(raw); } catch (DateTimeParseException ignored) { /* fall through */ }
        }
        // Epoch seconds, common in Splunk
        try {
            long epoch = Long.parseLong(raw);
            return LocalDateTime.ofEpochSecond(epoch, 0, java.time.ZoneOffset.UTC);
        } catch (NumberFormatException ignored) { /* fall through */ }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return new HashMap<>();
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String asString(Map<String, Object> map) {
        if (map == null) return null;
        return map.toString();
    }
}
