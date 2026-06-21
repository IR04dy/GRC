package com.tss.grc.module.integrations.splunk;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.integrations.splunk.config.SplunkProperties;
import com.tss.grc.module.integrations.splunk.webhook.SplunkWebhookController;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the Splunk → GRC webhook:
 *   - Maps Splunk severity strings + numerics → NCA ECC-2 buckets
 *   - Builds a deduplicating externalId
 *   - Creates a SecurityEvent on first call, returns duplicate=true on replay
 *   - Rejects bad HMAC when shared secret is configured
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SplunkWebhookControllerTest {

    @Mock private SecurityEventRepository securityEvents;
    @Mock private IntegrationExternalLinkRepository linkRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private HttpServletRequest httpRequest;

    private SplunkProperties props;
    private SplunkWebhookController controller;

    @BeforeEach
    void setUp() {
        props = new SplunkProperties();
        controller = new SplunkWebhookController(props, securityEvents, linkRepo, audit, publisher);
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Map<String, Object> samplePayload(String severity, String resultId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", "edge-vpn-01");
        result.put("severity", severity);
        result.put("signature", "Edge VPN brute-force");
        result.put("_time", "2026-05-23T08:14:00.000+03:00");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", result);
        body.put("search_name", "TSS - Edge VPN brute-force");
        body.put("result_id", resultId);
        return body;
    }

    // ── severity mapping ────────────────────────────────────────────────

    @Test
    void mapSplunkSeverity_stringForms() {
        assertThat(SplunkWebhookController.mapSplunkSeverity("critical")).isEqualTo("CRITICAL");
        assertThat(SplunkWebhookController.mapSplunkSeverity("Severe")).isEqualTo("CRITICAL");
        assertThat(SplunkWebhookController.mapSplunkSeverity("high")).isEqualTo("HIGH");
        assertThat(SplunkWebhookController.mapSplunkSeverity("MEDIUM")).isEqualTo("MEDIUM");
        assertThat(SplunkWebhookController.mapSplunkSeverity("Low")).isEqualTo("LOW");
        assertThat(SplunkWebhookController.mapSplunkSeverity("informational")).isEqualTo("LOW");
        assertThat(SplunkWebhookController.mapSplunkSeverity(null)).isEqualTo("MEDIUM");
        assertThat(SplunkWebhookController.mapSplunkSeverity("???")).isEqualTo("MEDIUM");
    }

    @Test
    void mapSplunkSeverity_numericForms() {
        assertThat(SplunkWebhookController.mapSplunkSeverity("1")).isEqualTo("LOW");
        assertThat(SplunkWebhookController.mapSplunkSeverity("2")).isEqualTo("LOW");
        assertThat(SplunkWebhookController.mapSplunkSeverity("3")).isEqualTo("MEDIUM");
        assertThat(SplunkWebhookController.mapSplunkSeverity("4")).isEqualTo("HIGH");
        assertThat(SplunkWebhookController.mapSplunkSeverity("5")).isEqualTo("CRITICAL");
        assertThat(SplunkWebhookController.mapSplunkSeverity("6")).isEqualTo("CRITICAL");
    }

    // ── happy path ──────────────────────────────────────────────────────

    @Test
    void alertReceived_validPayload_createsSecurityEvent_andEmitsF7F8() {
        when(securityEvents.findByOrganisationIdAndSourceSystemAndExternalId(eq(1L), eq("SPLUNK"), anyString()))
            .thenReturn(Optional.empty());
        when(securityEvents.save(any(SecurityEvent.class))).thenAnswer(inv -> {
            SecurityEvent ev = inv.getArgument(0);
            ev.setId(101L);
            return ev;
        });
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> resp = controller.alertReceived(
            samplePayload("high", "rid-001"), null, httpRequest
        );

        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = resp.getBody();
        assertThat(body).containsEntry("received", true)
                        .containsEntry("duplicate", false)
                        .containsEntry("severity", "HIGH")
                        .containsEntry("externalId", "rid-001");

        ArgumentCaptor<SecurityEvent> evCap = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEvents).save(evCap.capture());
        SecurityEvent created = evCap.getValue();
        assertThat(created.getSourceSystem()).isEqualTo("SPLUNK");
        assertThat(created.getSeverity()).isEqualTo("HIGH");
        assertThat(created.getExternalId()).isEqualTo("rid-001");
        assertThat(created.getStatus()).isEqualTo("NEW");
        assertThat(created.getRuleName()).contains("Edge VPN");
        // HIGH → 4h SLA
        assertThat(created.getSlaDueAt()).isNotNull();

        verify(audit).log(eq("SPLUNK_ALERT_RECEIVED"), eq("SecurityEvent"), eq(101L), anyString());
        ArgumentCaptor<DomainEvent> domainCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(domainCap.capture());
        assertThat(domainCap.getValue().triggerEvent()).isEqualTo("SPLUNK_ALERT_RECEIVED");
    }

    // ── dedup ───────────────────────────────────────────────────────────

    @Test
    void alertReceived_duplicateExternalId_shortCircuits() {
        SecurityEvent already = new SecurityEvent();
        already.setId(77L);
        when(securityEvents.findByOrganisationIdAndSourceSystemAndExternalId(eq(1L), eq("SPLUNK"), eq("rid-dup")))
            .thenReturn(Optional.of(already));

        ResponseEntity<Map<String, Object>> resp = controller.alertReceived(
            samplePayload("low", "rid-dup"), null, httpRequest
        );

        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("duplicate", true)
                                   .containsEntry("securityEventId", 77L);

        verify(securityEvents, never()).save(any());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── HMAC verification ───────────────────────────────────────────────

    @Test
    void alertReceived_withInvalidHmac_isRejected() {
        props.setWebhookSharedSecret("super-secret");
        // Even with a cached raw body, a wrong signature is rejected.
        when(httpRequest.getAttribute("RAW_BODY")).thenReturn("{\"some\":\"body\"}");
        ResponseEntity<Map<String, Object>> resp = controller.alertReceived(
            samplePayload("high", "rid-007"), "deadbeef", httpRequest
        );
        assertThat(resp.getStatusCodeValue()).isEqualTo(401);
        assertThat(resp.getBody()).containsEntry("received", false);
        verify(securityEvents, never()).save(any());
    }

    @Test
    void alertReceived_withValidHmacOverRawBody_isAccepted() throws Exception {
        props.setWebhookSharedSecret("super-secret");
        String rawBody = "{\"result\":{\"severity\":\"high\"},\"result_id\":\"rid-sig\"}";
        when(httpRequest.getAttribute("RAW_BODY")).thenReturn(rawBody);
        when(securityEvents.findByOrganisationIdAndSourceSystemAndExternalId(eq(1L), eq("SPLUNK"), anyString()))
            .thenReturn(Optional.empty());
        when(securityEvents.save(any(SecurityEvent.class))).thenAnswer(inv -> {
            SecurityEvent ev = inv.getArgument(0);
            ev.setId(303L);
            return ev;
        });
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        String validSig = hmac(rawBody, "super-secret");
        ResponseEntity<Map<String, Object>> resp = controller.alertReceived(
            samplePayload("high", "rid-sig"), validSig, httpRequest
        );
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("received", true);
    }

    @Test
    void alertReceived_whenRawBodyUnavailable_failsClosed_evenWithSignature() throws Exception {
        // SEC-INT-005: no RAW_BODY cached → must reject rather than hash "".
        props.setWebhookSharedSecret("super-secret");
        when(httpRequest.getAttribute("RAW_BODY")).thenReturn(null);
        // An attacker presenting HMAC('') must NOT validate.
        String hmacOfEmpty = hmac("", "super-secret");
        ResponseEntity<Map<String, Object>> resp = controller.alertReceived(
            samplePayload("critical", "rid-forged"), hmacOfEmpty, httpRequest
        );
        assertThat(resp.getStatusCodeValue()).isEqualTo(401);
        assertThat(resp.getBody()).containsEntry("reason", "invalid signature");
        verify(securityEvents, never()).save(any());
    }

    @Test
    void alertReceived_withMissingHmacWhenSecretConfigured_is401() {
        props.setWebhookSharedSecret("super-secret");
        ResponseEntity<Map<String, Object>> resp = controller.alertReceived(
            samplePayload("high", "rid-008"), null, httpRequest
        );
        assertThat(resp.getStatusCodeValue()).isEqualTo(401);
        assertThat(resp.getBody()).containsEntry("reason", "missing signature");
    }

    @Test
    void alertReceived_whenSecretBlank_skipsSignatureCheck() {
        // default props.webhookSharedSecret = ""
        when(securityEvents.findByOrganisationIdAndSourceSystemAndExternalId(eq(1L), eq("SPLUNK"), anyString()))
            .thenReturn(Optional.empty());
        when(securityEvents.save(any(SecurityEvent.class))).thenAnswer(inv -> {
            SecurityEvent ev = inv.getArgument(0);
            ev.setId(202L);
            return ev;
        });
        when(linkRepo.findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
            anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> resp = controller.alertReceived(
            samplePayload("medium", "rid-009"), null, httpRequest
        );
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("severity", "MEDIUM");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Computes hex HMAC-SHA256 for the documented "valid signature" smoke test. */
    @SuppressWarnings("unused")
    static String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
