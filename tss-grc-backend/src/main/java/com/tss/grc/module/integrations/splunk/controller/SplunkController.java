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
 * Admin and SOC REST endpoints for the Splunk SIEM bridge. Endpoints split
 * by role:
 *
 * <ul>
 *   <li>{@code GET  /status} — kill-switch, mode, last-successful-push timestamp.</li>
 *   <li>{@code POST /test-push} — PLATFORM_ADMIN only — smoke-test an arbitrary event.</li>
 *   <li>{@code POST /security-event/{id}} — SOC_ANALYST / SOC_AUTOMATION — push a known event.</li>
 *   <li>{@code GET  /config} — PLATFORM_ADMIN / CISO — bound configuration (HEC token masked).</li>
 *   <li>{@code GET  /stub/recorded-calls} — PLATFORM_ADMIN / CISO — STUB-only call log.</li>
 * </ul>
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
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO','SOC_ANALYST','SOC_AUTOMATION')")
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
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
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
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Manual smoke-test: push an arbitrary event through the outbound pipeline")
    public SplunkHecEventResponse testPush(@Valid @RequestBody SplunkHecEventRequest req) {
        return outbound.push(req);
    }

    @PostMapping("/security-event/{id}")
    @PreAuthorize("hasAnyRole('SOC_ANALYST','SOC_AUTOMATION','PLATFORM_ADMIN','CISO')")
    @Operation(summary = "Manually push a known SOC security event into Splunk")
    public SplunkHecEventResponse pushSecurityEvent(@PathVariable Long id) {
        return outbound.pushSecurityEvent(id);
    }

    @GetMapping("/stub/recorded-calls")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
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
