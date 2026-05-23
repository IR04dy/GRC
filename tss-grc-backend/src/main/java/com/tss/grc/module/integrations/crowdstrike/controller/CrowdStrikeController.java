package com.tss.grc.module.integrations.crowdstrike.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.crowdstrike.client.CrowdStrikeClient;
import com.tss.grc.module.integrations.crowdstrike.client.StubCrowdStrikeClient;
import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentRequest;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentResponse;
import com.tss.grc.module.integrations.crowdstrike.dto.StatusDto;
import com.tss.grc.module.integrations.crowdstrike.dto.StubInvocation;
import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeActionLog;
import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeDetectionLog;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeActionLogRepository;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeDetectionLogRepository;
import com.tss.grc.module.integrations.crowdstrike.service.CrowdStrikeInboundService;
import com.tss.grc.module.integrations.crowdstrike.service.CrowdStrikeOutboundService;
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
 * Admin and SOC REST endpoints for the CrowdStrike Falcon EDR bridge.
 *
 * <p>Role-split (read the docs comment on each endpoint for the rationale):
 * <ul>
 *   <li>{@code GET  /status} — broad read access (SOC analysts watch this).</li>
 *   <li>{@code POST /poll} — manual poll trigger (PLATFORM_ADMIN / SOC_AUTOMATION).</li>
 *   <li>{@code POST /contain/{hostId}} — containment (SOC_TIER_2 / INCIDENT_COMMANDER / CISO / PLATFORM_ADMIN).</li>
 *   <li>{@code POST /lift/{hostId}} — lift containment (CISO / PLATFORM_ADMIN only — the
 *       restore-from-isolation decision sits with security leadership).</li>
 *   <li>{@code GET  /stub/recorded-calls} — STUB-only diagnostic — PLATFORM_ADMIN / CISO.</li>
 * </ul>
 *
 * <p>All endpoints use {@code hasAnyRole(...)} not {@code hasAnyAuthority(...)} —
 * Spring prepends {@code ROLE_} which matches how {@code GrcUserDetails}
 * exposes user roles.
 */
@RestController
@RequestMapping("/api/v1/integrations/crowdstrike")
@Tag(name = "CrowdStrike Falcon Integration")
@SecurityRequirement(name = "bearerAuth")
public class CrowdStrikeController {

    private final CrowdStrikeClient client;
    private final CrowdStrikeProperties props;
    private final CrowdStrikeInboundService inbound;
    private final CrowdStrikeOutboundService outbound;
    private final CrowdStrikeDetectionLogRepository detectionLogRepo;
    private final CrowdStrikeActionLogRepository actionLogRepo;
    /** Optional — only present in STUB mode. */
    private final ObjectProvider<StubCrowdStrikeClient> stubProvider;

    public CrowdStrikeController(
        CrowdStrikeClient client,
        CrowdStrikeProperties props,
        CrowdStrikeInboundService inbound,
        CrowdStrikeOutboundService outbound,
        CrowdStrikeDetectionLogRepository detectionLogRepo,
        CrowdStrikeActionLogRepository actionLogRepo,
        ObjectProvider<StubCrowdStrikeClient> stubProvider
    ) {
        this.client = client;
        this.props = props;
        this.inbound = inbound;
        this.outbound = outbound;
        this.detectionLogRepo = detectionLogRepo;
        this.actionLogRepo = actionLogRepo;
        this.stubProvider = stubProvider;
    }

    // ── /status — kill-switches + last-poll / last-action timestamps ──

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO','SOC_ANALYST','SOC_TIER_2','SOC_AUTOMATION','INCIDENT_COMMANDER')")
    @Operation(summary = "Falcon adapter status — kill-switch, mode, last-poll + last-action timestamps")
    public StatusDto status() {
        boolean healthy = client.isHealthy();
        Long orgId = TenantContext.getCurrentTenant();
        CrowdStrikeDetectionLog lastPoll = orgId == null
            ? null
            : detectionLogRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(orgId);
        CrowdStrikeActionLog lastAction = orgId == null
            ? null
            : actionLogRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(orgId);

        return new StatusDto(
            props.isEnabled(),
            props.isPollingEnabled(),
            props.getMode(),
            props.getBaseUrl(),
            healthy,
            lastPoll == null ? null : lastPoll.getCreatedAt(),
            lastAction == null ? null : lastAction.getCreatedAt()
        );
    }

    // ── /config — bound configuration with secrets masked ─────────────

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
    @Operation(summary = "Bound CrowdStrike configuration — secrets masked")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.isEnabled());
        body.put("mode", props.getMode());
        body.put("baseUrl", props.getBaseUrl());
        body.put("clientId", mask(props.getClientId()));
        body.put("clientSecret", mask(props.getClientSecret()));
        body.put("pollingIntervalSeconds", props.getPollingIntervalSeconds());
        body.put("pollingEnabled", props.isPollingEnabled());
        body.put("timeoutMs", props.getTimeoutMs());

        Map<String, Object> retry = new LinkedHashMap<>();
        retry.put("maxAttempts", props.getRetry().getMaxAttempts());
        retry.put("initialDelayMs", props.getRetry().getInitialDelayMs());
        retry.put("backoffMultiplier", props.getRetry().getBackoffMultiplier());
        body.put("retry", retry);
        return body;
    }

    // ── /poll — manual poll trigger ───────────────────────────────────

    @PostMapping("/poll")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SOC_AUTOMATION')")
    @Operation(summary = "Manual poll of the Falcon /detects endpoint — returns the number of new detections ingested")
    public Map<String, Object> manualPoll() {
        int ingested = inbound.pollDetections();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ingested", ingested);
        body.put("at", java.time.LocalDateTime.now());
        return body;
    }

    // ── /contain/{hostId} — host isolation ────────────────────────────

    @PostMapping("/contain/{hostId}")
    @PreAuthorize("hasAnyRole('SOC_TIER_2','INCIDENT_COMMANDER','CISO','PLATFORM_ADMIN')")
    @Operation(summary = "Issue a Falcon host-containment action (isolate from network)")
    public ContainmentResponse contain(
        @PathVariable("hostId") String hostId,
        @Valid @RequestBody ContainmentRequest req
    ) {
        return outbound.containHost(hostId, req.reason());
    }

    // ── /lift/{hostId} — restore network connectivity ─────────────────

    @PostMapping("/lift/{hostId}")
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN')")
    @Operation(summary = "Lift Falcon containment — restore network connectivity to an isolated host")
    public ContainmentResponse lift(@PathVariable("hostId") String hostId) {
        return outbound.liftContainment(hostId);
    }

    // ── /stub/recorded-calls — STUB-only diagnostic ───────────────────

    @GetMapping("/stub/recorded-calls")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
    @Operation(summary = "STUB mode only — list calls the stub client has seen")
    public ResponseEntity<List<StubInvocation>> recordedCalls() {
        StubCrowdStrikeClient stub = stubProvider.getIfAvailable();
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
