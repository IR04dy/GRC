package com.tss.grc.module.integrations.tenable.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.tenable.client.StubTenableClient;
import com.tss.grc.module.integrations.tenable.client.TenableClient;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.AcceptRiskRequest;
import com.tss.grc.module.integrations.tenable.dto.RecastRequest;
import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.dto.StatusDto;
import com.tss.grc.module.integrations.tenable.dto.StubInvocation;
import com.tss.grc.module.integrations.tenable.entity.TenableFindingLog;
import com.tss.grc.module.integrations.tenable.entity.TenableRecastLog;
import com.tss.grc.module.integrations.tenable.repository.TenableFindingLogRepository;
import com.tss.grc.module.integrations.tenable.repository.TenableRecastLogRepository;
import com.tss.grc.module.integrations.tenable.service.TenableInboundService;
import com.tss.grc.module.integrations.tenable.service.TenableOutboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
 * Admin and VM/risk REST endpoints for the Tenable.io / Tenable.sc bridge.
 *
 * <p>Role-split (read the docs comment on each endpoint for the rationale):
 * <ul>
 *   <li>{@code GET  /status} — broad read access (SOC analysts watch this).</li>
 *   <li>{@code POST /poll} — manual poll trigger (PLATFORM_ADMIN / SOC_AUTOMATION).</li>
 *   <li>{@code POST /recast/{pluginId}} — severity recast
 *       (CISO / RISK_MANAGER / PLATFORM_ADMIN).</li>
 *   <li>{@code POST /accept-risk/{pluginId}} — risk acceptance
 *       (CISO / RISK_MANAGER / PLATFORM_ADMIN / BOARD_MEMBER — board has
 *       acceptance authority under SAMA CSF for residual-risk decisions).</li>
 *   <li>{@code POST /remove-recast/{pluginId}} — restore Tenable default
 *       (CISO / PLATFORM_ADMIN only — narrowed to security leadership).</li>
 *   <li>{@code GET  /stub/recorded-calls} — STUB-only diagnostic
 *       (PLATFORM_ADMIN / CISO).</li>
 * </ul>
 *
 * <p>All endpoints use {@code hasAnyRole(...)} not {@code hasAnyAuthority(...)} —
 * Spring prepends {@code ROLE_} which matches how {@code GrcUserDetails}
 * exposes user roles.
 */
@RestController
@RequestMapping("/api/v1/integrations/tenable")
@Tag(name = "Tenable VM Integration")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class TenableController {

    /** Tenable plugin ids are numeric. Constraining the path variable keeps it
     *  out of the outbound API URL path as a traversal / delimiter vector
     *  (SEC-INT-004). */
    private static final String PLUGIN_ID_PATTERN = "\\d{1,12}";

    private final TenableClient client;
    private final TenableProperties props;
    private final TenableInboundService inbound;
    private final TenableOutboundService outbound;
    private final TenableFindingLogRepository findingLogRepo;
    private final TenableRecastLogRepository recastLogRepo;
    /** Optional — only present in STUB mode. */
    private final ObjectProvider<StubTenableClient> stubProvider;

    public TenableController(
        TenableClient client,
        TenableProperties props,
        TenableInboundService inbound,
        TenableOutboundService outbound,
        TenableFindingLogRepository findingLogRepo,
        TenableRecastLogRepository recastLogRepo,
        ObjectProvider<StubTenableClient> stubProvider
    ) {
        this.client = client;
        this.props = props;
        this.inbound = inbound;
        this.outbound = outbound;
        this.findingLogRepo = findingLogRepo;
        this.recastLogRepo = recastLogRepo;
        this.stubProvider = stubProvider;
    }

    // ── /status — kill-switches + last-poll / last-action timestamps ──

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO','RISK_MANAGER','SOC_ANALYST','SOC_TIER_2','SOC_AUTOMATION','INCIDENT_COMMANDER','BOARD_MEMBER')")
    @Operation(summary = "Tenable adapter status — kill-switch, mode, platform, last-poll + last-action timestamps")
    public StatusDto status() {
        boolean healthy = client.isHealthy();
        Long orgId = TenantContext.getCurrentTenant();
        TenableFindingLog lastPoll = orgId == null
            ? null
            : findingLogRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(orgId);
        TenableRecastLog lastAction = orgId == null
            ? null
            : recastLogRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(orgId);

        return new StatusDto(
            props.isEnabled(),
            props.isPollingEnabled(),
            props.getMode(),
            props.getPlatform(),
            props.getBaseUrl(),
            healthy,
            lastPoll == null ? null : lastPoll.getCreatedAt(),
            lastAction == null ? null : lastAction.getCreatedAt()
        );
    }

    // ── /config — bound configuration with secrets masked ─────────────

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
    @Operation(summary = "Bound Tenable configuration — secrets masked")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.isEnabled());
        body.put("mode", props.getMode());
        body.put("platform", props.getPlatform());
        body.put("baseUrl", props.getBaseUrl());
        body.put("accessKey", mask(props.getAccessKey()));
        body.put("secretKey", mask(props.getSecretKey()));
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
    // F2-05 slice 4 — same as CrowdStrikeController#manualPoll: SOC_AUTOMATION does not exist, so
    // pulling Tenable findings was administrator-only. SCANNER_INGEST, the seeded machine role for
    // exactly this feed, holds CYBER:WRITE.
    @PreAuthorize("hasPermission('CYBER','WRITE')")
    @Operation(summary = "Manual poll of the Tenable findings endpoint — returns the number of new findings ingested")
    public Map<String, Object> manualPoll() {
        int ingested = inbound.pollFindings();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ingested", ingested);
        body.put("at", java.time.LocalDateTime.now());
        return body;
    }

    // ── /recast/{pluginId} — severity override ────────────────────────

    @PostMapping("/recast/{pluginId}")
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN')")
    @Operation(summary = "Recast (override) the severity Tenable assigns to a finding — body must include a justification of at least 30 characters")
    public RecastResponse recast(
        @PathVariable("pluginId") @Pattern(regexp = PLUGIN_ID_PATTERN, message = "pluginId must be numeric") String pluginId,
        @Valid @RequestBody RecastRequest req
    ) {
        return outbound.recastSeverity(pluginId, req.assetUuid(), req.newSeverity(), req.justification());
    }

    // ── /accept-risk/{pluginId} — risk acceptance ─────────────────────

    @PostMapping("/accept-risk/{pluginId}")
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN','BOARD_MEMBER')")
    @Operation(summary = "Mark a Tenable finding as risk-accepted — body must include a justification of at least 30 characters")
    public RecastResponse acceptRisk(
        @PathVariable("pluginId") @Pattern(regexp = PLUGIN_ID_PATTERN, message = "pluginId must be numeric") String pluginId,
        @Valid @RequestBody AcceptRiskRequest req
    ) {
        return outbound.acceptRisk(pluginId, req.assetUuid(), req.justification(), req.expiresAt());
    }

    // ── /remove-recast/{pluginId} — restore Tenable default ───────────

    @PostMapping("/remove-recast/{pluginId}")
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN')")
    @Operation(summary = "Remove an existing Tenable recast/acceptance rule, restoring Tenable's original severity")
    public RecastResponse removeRecast(
        @PathVariable("pluginId") @Pattern(regexp = PLUGIN_ID_PATTERN, message = "pluginId must be numeric") String pluginId,
        @Valid @RequestBody(required = false) RemoveRecastBody body
    ) {
        String assetUuid = body == null ? null : body.assetUuid();
        return outbound.removeRecast(pluginId, assetUuid);
    }

    // ── /stub/recorded-calls — STUB-only diagnostic ───────────────────

    @GetMapping("/stub/recorded-calls")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
    @Operation(summary = "STUB mode only — list calls the stub client has seen")
    public ResponseEntity<List<StubInvocation>> recordedCalls() {
        StubTenableClient stub = stubProvider.getIfAvailable();
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

    /** Optional body for /remove-recast — carries just the optional asset uuid.
     *  Constrained to a UUID so it cannot smuggle path-traversal / delimiter
     *  characters into the outbound Tenable API URL (SEC-INT-004). */
    public record RemoveRecastBody(
        @Size(max = 80)
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                 message = "assetUuid must be a valid UUID")
        String assetUuid
    ) {}
}
