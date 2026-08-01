package com.tss.grc.module.integrations.splunk.controller;

import com.tss.grc.module.integrations.splunk.client.SplunkClient;
import com.tss.grc.module.integrations.splunk.client.StubSplunkClient;
import com.tss.grc.module.integrations.splunk.config.SplunkProperties;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventRequest;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventResponse;
import com.tss.grc.module.integrations.splunk.dto.StubInvocation;
import com.tss.grc.module.integrations.splunk.entity.SplunkHecOutboundLog;
import com.tss.grc.module.integrations.splunk.repository.SplunkHecOutboundLogRepository;
import com.tss.grc.module.integrations.splunk.service.SplunkOutboundService;
import com.tss.grc.common.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin and SOC REST endpoints for the Splunk SIEM bridge. Endpoints split by
 * permission — {@code INTEGRATIONS:READ} to observe, {@code INTEGRATIONS:WRITE}
 * to transmit (ADR-017 D-8, V343):
 *
 * <ul>
 *   <li>{@code GET  /status} — {@code INTEGRATIONS:READ} — kill-switch, mode, last-push timestamp.</li>
 *   <li>{@code GET  /config} — {@code INTEGRATIONS:READ} — bound configuration (HEC token masked).</li>
 *   <li>{@code POST /test-push} — {@code INTEGRATIONS:WRITE} — smoke-test an arbitrary event.</li>
 *   <li>{@code POST /security-event/{id}} — {@code INTEGRATIONS:WRITE} — push a known event.</li>
 *   <li>{@code GET  /stub/recorded-calls} — {@code INTEGRATIONS:WRITE} — STUB-only call log; it
 *       returns frozen request payloads, which is why it sits at the WRITE tier and not READ.</li>
 * </ul>
 *
 * <p>This list previously read "PLATFORM_ADMIN only" and "SOC_ANALYST / SOC_AUTOMATION". Two of those
 * names do not exist in {@code iam.roles}, so the documentation described a population that could
 * never be assembled — the route table was as wrong as the guard it described.
 */
@RestController
@RequestMapping("/api/v1/integrations/splunk")
@Tag(name = "Splunk Integration")
@SecurityRequirement(name = "bearerAuth")
public class SplunkController {

    private final SplunkClient client;
    private final SplunkProperties props;
    private final SplunkOutboundService outbound;
    private final SplunkHecOutboundLogRepository logRepo;
    /** Optional — only present in STUB mode. */
    private final ObjectProvider<StubSplunkClient> stubProvider;

    public SplunkController(
        SplunkClient client,
        SplunkProperties props,
        SplunkOutboundService outbound,
        SplunkHecOutboundLogRepository logRepo,
        ObjectProvider<StubSplunkClient> stubProvider
    ) {
        this.client = client;
        this.props = props;
        this.outbound = outbound;
        this.logRepo = logRepo;
        this.stubProvider = stubProvider;
    }

    @GetMapping("/status")
    // A17-D8: was hasAnyRole('PLATFORM_ADMIN','CISO','SOC_ANALYST','SOC_AUTOMATION'). Two of those
    // four names do not exist in iam.roles, and hasAnyRole gets no PLATFORM_ADMIN bypass, so this was
    // reachable by exactly PLATFORM_ADMIN and CISO. INTEGRATIONS:READ = ORG_ADMIN, GRC_ADMINISTRATOR,
    // CISO, CYBER_MANAGER — see V343 for the per-grantee need-to-know. ADR-017 D-8.
    @PreAuthorize("hasPermission('INTEGRATIONS','READ')")
    @Operation(summary = "Splunk integration health + last-successful-push timestamp")
    public Map<String, Object> status() {
        boolean healthy = client.isHealthy();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.isEnabled());
        body.put("mode", props.getMode());
        body.put("healthy", healthy);
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId != null) {
            SplunkHecOutboundLog lastOk =
                logRepo.findFirstByOrganisationIdAndHttpStatusOrderByCreatedAtDesc(orgId, 200);
            body.put("lastSuccessfulPushAt", lastOk == null ? null : lastOk.getCreatedAt());
        } else {
            body.put("lastSuccessfulPushAt", null);
        }
        return body;
    }

    @GetMapping("/config")
    @PreAuthorize("hasPermission('INTEGRATIONS','READ')")   // A17-D8 — was hasAnyRole('PLATFORM_ADMIN','CISO')
    @Operation(summary = "Bound Splunk integration configuration — HEC token masked")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.isEnabled());
        body.put("mode", props.getMode());
        body.put("hecUrl", props.getHecUrl());
        body.put("hecToken", mask(props.getHecToken()));
        body.put("defaultIndex", props.getDefaultIndex());
        body.put("defaultSourcetype", props.getDefaultSourcetype());
        body.put("verifySsl", props.isVerifySsl());
        body.put("timeoutMs", props.getTimeoutMs());
        body.put("webhookSharedSecretConfigured",
            props.getWebhookSharedSecret() != null && !props.getWebhookSharedSecret().isBlank());

        Map<String, Object> retry = new LinkedHashMap<>();
        retry.put("maxAttempts", props.getRetry().getMaxAttempts());
        retry.put("initialDelayMs", props.getRetry().getInitialDelayMs());
        retry.put("backoffMultiplier", props.getRetry().getBackoffMultiplier());
        body.put("retry", retry);
        return body;
    }

    @PostMapping("/test-push")
    // A17-D8: was hasRole('PLATFORM_ADMIN') — administrator-only, so the integration smoke test could
    // not be run by the persona that owns the integration. INTEGRATIONS:WRITE = ORG_ADMIN, CISO. This
    // transmits an arbitrary event to a third party, which is why it is the WRITE tier and not READ.
    @PreAuthorize("hasPermission('INTEGRATIONS','WRITE')")
    @Operation(summary = "Manual smoke-test: push an arbitrary event through the outbound pipeline")
    public SplunkHecEventResponse testPush(@Valid @RequestBody SplunkHecEventRequest req) {
        return outbound.push(req);
    }

    @PostMapping("/security-event/{id}")
    // A17-D8: egress, not ingest. The four INGEST endpoints stay on CYBER:WRITE (F2-05 slice 4) because
    // the *_INGEST machine roles hold it; this one sends platform data OUT to a third party, which is the
    // data-transfer surface PDPL art. 29 / NCA ECC-2 name. Was two ghosts + PLATFORM_ADMIN + CISO.
    @PreAuthorize("hasPermission('INTEGRATIONS','WRITE')")
    @Operation(summary = "Manually push a known SOC security event into Splunk")
    public SplunkHecEventResponse pushSecurityEvent(@PathVariable Long id) {
        return outbound.pushSecurityEvent(id);
    }

    @GetMapping("/stub/recorded-calls")
    // A17-D8 — WRITE tier on a GET, deliberately. StubInvocation carries the frozen inbound PAYLOAD, so
    // this is the only READ-shaped endpoint in the module that can return what was actually transmitted.
    // It must not widen to the two assurance personas INTEGRATIONS:READ adds. Returns [] outside STUB
    // mode (the bean is absent), so the exposure is dev/pilot-only — but a guard is not a mode.
    @PreAuthorize("hasPermission('INTEGRATIONS','WRITE')")
    @Operation(summary = "STUB mode only — list calls the stub client has seen")
    public ResponseEntity<List<StubInvocation>> recordedCalls() {
        StubSplunkClient stub = stubProvider.getIfAvailable();
        if (stub == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(stub.getRecordedCalls());
    }

    // ── helpers ───────────────────────────────────────────────────────

    private static String mask(String s) {
        if (s == null || s.isBlank()) return "";
        if (s.length() <= 4) return "****";
        return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
    }
}
