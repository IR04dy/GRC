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
 * <p>Authorisation split (read the comment on each endpoint for the rationale):
 * <ul>
 *   <li>{@code GET  /status} — {@code INTEGRATIONS:READ} — kill-switch, mode, platform, last-poll.
 *       Was an eight-name role list of which five were ghosts; {@code RISK_MANAGER} is the one role
 *       this slice's conversion costs anything, and A17-D8 records why (V343).</li>
 *   <li>{@code GET  /config} — {@code INTEGRATIONS:READ} — bound configuration, secrets masked.</li>
 *   <li>{@code POST /poll} — {@code CYBER:WRITE} — ingest; {@code SCANNER_INGEST} holds it
 *       (F2-05 slice 4). Previously PLATFORM_ADMIN / SOC_AUTOMATION, the second a ghost.</li>
 *   <li>{@code POST /recast/{pluginId}} — {@code hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN')},
 *       a role guard ON PURPOSE (A17-D8): no catalogued permission expresses "cyber AND risk", and
 *       {@code INTEGRATIONS:WRITE} would revoke this from {@code RISK_MANAGER}.</li>
 *   <li>{@code POST /accept-risk/{pluginId}} — same guard. ⚠ {@code BOARD_MEMBER} was removed: the
 *       SAMA argument for board residual-risk acceptance is sound, but the role does not exist in
 *       {@code iam.roles} on either database, so no board member ever reached this endpoint. A board
 *       acceptance path needs a real role, not a name in an annotation.</li>
 *   <li>{@code POST /remove-recast/{pluginId}} — {@code hasAnyRole('CISO','PLATFORM_ADMIN')} —
 *       narrower than {@code /recast} on purpose: reverting another analyst's severity decision is a
 *       narrower act than making one.</li>
 *   <li>{@code GET  /stub/recorded-calls} — {@code INTEGRATIONS:WRITE} — STUB-only diagnostic that
 *       returns frozen request payloads, hence the WRITE tier rather than READ.</li>
 * </ul>
 *
 * <p>The three remaining role guards use {@code hasAnyRole(...)} not {@code hasAnyAuthority(...)} —
 * Spring prepends {@code ROLE_}, which matches how {@code GrcUserDetails} exposes user roles. A role
 * guard never reaches {@code ModulePermissionEvaluator} and therefore gets <b>no PLATFORM_ADMIN
 * bypass</b>, which is why all three name it explicitly.
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
    // A17-D8. Eight names, FIVE of them ghosts — and BOARD_MEMBER on an adapter kill-switch endpoint is
    // the tell that this list was written by enumeration rather than by design. INTEGRATIONS:READ.
    //
    // ⚠ This is the ONE access loss in the whole slice, recorded rather than buried: RISK_MANAGER was
    // named here and does not hold INTEGRATIONS:READ. It keeps recast / accept-risk / remove-recast
    // below — its actual function — and it holds no CYBER permission on either database, so it never had
    // a route to the findings themselves. What it loses is the adapter's polling timestamp. Live-probed
    // both before (200) and after (403).
    @PreAuthorize("hasPermission('INTEGRATIONS','READ')")
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
    @PreAuthorize("hasPermission('INTEGRATIONS','READ')")   // A17-D8 — see V343
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
    // A17-D8: deliberately NOT converted, and this endpoint is the measurement that decided it.
    // INTEGRATIONS:WRITE is {ORG_ADMIN, CISO} — converting would REVOKE severity override and risk
    // acceptance from RISK_MANAGER, the one role whose function this is. CYBER:WRITE is rejected by D-8
    // (eight machine feeds). Vulnerability risk-acceptance is a two-population decision — cyber AND
    // risk — with no catalogued permission expressing it, and inventing one is not in this slice.
    // All three names exist in iam.roles; verified by IntegrationsAuthorizationTest.
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
    // A17-D8: BOARD_MEMBER stripped — see TENABLE.md, which justified its presence on SAMA grounds
    // ("the board accepts residual risk above the appetite"). The reasoning was sound and the
    // implementation never worked: BOARD_MEMBER does not exist in iam.roles on either database, so the
    // guard admitted CISO, RISK_MANAGER and PLATFORM_ADMIN and no board member ever reached it. A board
    // risk-acceptance path is a real requirement and it needs a real role, not a name in an annotation.
    @PreAuthorize("hasAnyRole('CISO','RISK_MANAGER','PLATFORM_ADMIN')")
    @Operation(summary = "Mark a Tenable finding as risk-accepted — body must include a justification of at least 30 characters")
    public RecastResponse acceptRisk(
        @PathVariable("pluginId") @Pattern(regexp = PLUGIN_ID_PATTERN, message = "pluginId must be numeric") String pluginId,
        @Valid @RequestBody AcceptRiskRequest req
    ) {
        return outbound.acceptRisk(pluginId, req.assetUuid(), req.justification(), req.expiresAt());
    }

    // ── /remove-recast/{pluginId} — restore Tenable default ───────────

    @PostMapping("/remove-recast/{pluginId}")
    // A17-D8: unchanged, and the asymmetry with #recast is deliberate — reverting another analyst's
    // severity decision is a narrower act than making one. Both names exist in iam.roles.
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
    @PreAuthorize("hasPermission('INTEGRATIONS','WRITE')")  // A17-D8 — returns frozen payloads; WRITE tier
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
