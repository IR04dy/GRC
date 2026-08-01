package com.tss.grc.module.integrations.servicenow.controller;

import com.tss.grc.module.integrations.servicenow.client.ServiceNowClient;
import com.tss.grc.module.integrations.servicenow.client.StubServiceNowClient;
import com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentRequest;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentResponse;
import com.tss.grc.module.integrations.servicenow.dto.StubInvocation;
import com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin REST endpoints for the ServiceNow ITSM integration, authorised on the
 * {@code INTEGRATIONS} module (ADR-017 D-8, V343) rather than on a role list.
 *
 * <ul>
 *   <li>{@code GET  /health} — {@code INTEGRATIONS:READ} — live or stub reachability check</li>
 *   <li>{@code GET  /config} — {@code INTEGRATIONS:READ} — bound configuration (secrets masked)</li>
 *   <li>{@code POST /test-push} — {@code INTEGRATIONS:WRITE} — manual smoke-push of an incident</li>
 *   <li>{@code GET  /stub/recorded-calls} — {@code INTEGRATIONS:WRITE} — stub-mode-only call log;
 *       it returns frozen request payloads, so it sits at the WRITE tier, not READ</li>
 * </ul>
 *
 * <p>This class previously said "only PLATFORM_ADMIN or CISO roles can poke at it" — which was
 * accurate, and was the defect: integration administration was reachable by two personas, one of
 * which bypasses authorisation in code.
 */
@RestController
@RequestMapping("/api/v1/integrations/servicenow")
@Tag(name = "ServiceNow Integration")
@SecurityRequirement(name = "bearerAuth")
public class ServiceNowController {

    private final ServiceNowClient client;
    private final ServiceNowProperties props;
    private final ServiceNowOutboundService outbound;
    /** Optional — only present in STUB mode. */
    private final ObjectProvider<StubServiceNowClient> stubProvider;

    public ServiceNowController(
        ServiceNowClient client,
        ServiceNowProperties props,
        ServiceNowOutboundService outbound,
        ObjectProvider<StubServiceNowClient> stubProvider
    ) {
        this.client = client;
        this.props = props;
        this.outbound = outbound;
        this.stubProvider = stubProvider;
    }

    @GetMapping("/health")
    @PreAuthorize("hasPermission('INTEGRATIONS','READ')")   // A17-D8 — was hasAnyRole('PLATFORM_ADMIN','CISO')
    @Operation(summary = "Reachability + auth check against ServiceNow")
    public Map<String, Object> health() {
        boolean healthy = client.isHealthy();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.isEnabled());
        body.put("mode", props.getMode());
        body.put("healthy", healthy);
        return body;
    }

    @GetMapping("/config")
    @PreAuthorize("hasPermission('INTEGRATIONS','READ')")   // A17-D8 — see V343; secrets are masked here
    @Operation(summary = "Bound ServiceNow integration configuration — secrets masked")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.isEnabled());
        body.put("mode", props.getMode());
        body.put("baseUrl", props.getBaseUrl());
        body.put("timeoutSeconds", props.getTimeoutSeconds());
        body.put("maxRetries", props.getMaxRetries());

        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("type", props.getAuth().getType());
        auth.put("clientId", mask(props.getAuth().getClientId()));
        auth.put("clientSecret", mask(props.getAuth().getClientSecret()));
        auth.put("tokenUrl", props.getAuth().getTokenUrl());
        auth.put("username", mask(props.getAuth().getUsername()));
        auth.put("password", mask(props.getAuth().getPassword()));
        body.put("auth", auth);

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("impact", props.getDefaults().getImpact());
        defaults.put("urgency", props.getDefaults().getUrgency());
        defaults.put("category", props.getDefaults().getCategory());
        body.put("defaults", defaults);
        return body;
    }

    @PostMapping("/test-push")
    @PreAuthorize("hasPermission('INTEGRATIONS','WRITE')")  // A17-D8 — transmits to a third party
    @Operation(summary = "Manual smoke-test: push an arbitrary incident through the outbound pipeline")
    public ServiceNowIncidentResponse testPush(@Valid @RequestBody ServiceNowIncidentRequest req) {
        return outbound.push(req);
    }

    @GetMapping("/stub/recorded-calls")
    @PreAuthorize("hasPermission('INTEGRATIONS','WRITE')")  // A17-D8 — returns frozen payloads; WRITE tier
    @Operation(summary = "STUB mode only — list calls the stub client has seen")
    public ResponseEntity<List<StubInvocation>> recordedCalls() {
        StubServiceNowClient stub = stubProvider.getIfAvailable();
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
