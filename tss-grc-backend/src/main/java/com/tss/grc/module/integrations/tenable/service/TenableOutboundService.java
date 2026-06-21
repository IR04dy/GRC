package com.tss.grc.module.integrations.tenable.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.tenable.client.TenableClient;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.entity.TenableRecastLog;
import com.tss.grc.module.integrations.tenable.repository.TenableFindingLogRepository;
import com.tss.grc.module.integrations.tenable.repository.TenableRecastLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pushes risk-management decisions into Tenable — recast a finding's
 * severity, accept-risk on a finding, or remove an existing recast rule.
 *
 * <p>Each request is recorded in {@code platform.tenable_recast_log}
 * regardless of outcome (successful or failed). On success we emit an F7
 * audit_log entry and an F8 domain event so automation rules can react
 * ({@code TENABLE_RECAST_APPLIED} / {@code TENABLE_RISK_ACCEPTED} /
 * {@code TENABLE_RECAST_REMOVED}).
 *
 * <p>{@code requestedByUserId} is pulled from the {@link SecurityContextHolder}
 * — this is the load-bearing audit field for NCA ECC-2 traceability.
 *
 * <p>{@code justification} is required and must be at least 30 characters
 * (compliance evidence — controllers validate via {@code @Size(min=30)}
 * before reaching this service; the service double-checks defensively).
 */
@Service
public class TenableOutboundService {

    private static final Logger log = LoggerFactory.getLogger(TenableOutboundService.class);

    /** Allowed values for the recast severity field. */
    private static final Set<String> VALID_SEVERITIES =
        Set.of("INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL");

    /** Minimum justification length — compliance audit evidence requirement. */
    private static final int MIN_JUSTIFICATION_CHARS = 30;

    /** Tenable plugin ids are numeric. Defence-in-depth against path injection
     *  (SEC-INT-004) — the controller @Pattern is the first gate; this is the
     *  service-layer backstop. */
    private static final java.util.regex.Pattern PLUGIN_ID_RX =
        java.util.regex.Pattern.compile("\\d{1,12}");

    /** Canonical 8-4-4-4-12 hex UUID for the optional asset scope. */
    private static final java.util.regex.Pattern ASSET_UUID_RX =
        java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final TenableClient client;
    private final TenableProperties props;
    private final TenableRecastLogRepository recastLogRepo;
    private final TenableFindingLogRepository findingLogRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public TenableOutboundService(
        TenableClient client,
        TenableProperties props,
        TenableRecastLogRepository recastLogRepo,
        TenableFindingLogRepository findingLogRepo,
        AuditLogService audit,
        ApplicationEventPublisher publisher,
        ObjectMapper objectMapper
    ) {
        this.client = client;
        this.props = props;
        this.recastLogRepo = recastLogRepo;
        this.findingLogRepo = findingLogRepo;
        this.audit = audit;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    // ── public API ──────────────────────────────────────────────────────

    /**
     * Recast a finding's severity in Tenable.
     *
     * @param pluginId      Tenable plugin id (required)
     * @param assetUuid     null = global recast across all assets with this plugin
     * @param newSeverity   one of INFO/LOW/MEDIUM/HIGH/CRITICAL (required)
     * @param justification audit trail — minimum 30 chars (required)
     */
    @Transactional
    public RecastResponse recastSeverity(String pluginId, String assetUuid, String newSeverity, String justification) {
        validatePluginId(pluginId, "RECAST_SEVERITY");
        validateAssetUuid(assetUuid, "RECAST_SEVERITY");
        validateJustification(justification, "RECAST_SEVERITY");
        if (newSeverity == null || !VALID_SEVERITIES.contains(newSeverity.toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(
                "newSeverity must be one of " + VALID_SEVERITIES);
        }
        String severity = newSeverity.toUpperCase(java.util.Locale.ROOT);
        return runAction(
            "RECAST_SEVERITY", pluginId, assetUuid, severity, justification, null,
            () -> client.recastSeverity(pluginId, assetUuid, severity, justification)
        );
    }

    /**
     * Mark a finding as risk-accepted in Tenable.
     *
     * @param pluginId      Tenable plugin id (required)
     * @param assetUuid     null = global acceptance
     * @param justification audit trail — minimum 30 chars (required)
     * @param expiresAt     null = open-ended; populated = acceptance window
     */
    @Transactional
    public RecastResponse acceptRisk(String pluginId, String assetUuid, String justification, LocalDateTime expiresAt) {
        validatePluginId(pluginId, "ACCEPT_RISK");
        validateAssetUuid(assetUuid, "ACCEPT_RISK");
        validateJustification(justification, "ACCEPT_RISK");
        return runAction(
            "ACCEPT_RISK", pluginId, assetUuid, null, justification, expiresAt,
            () -> client.acceptRisk(pluginId, assetUuid, justification, expiresAt)
        );
    }

    /**
     * Remove an existing recast/acceptance rule, restoring Tenable's
     * original severity. No justification required because the audit row
     * for the original recast already carries one.
     */
    @Transactional
    public RecastResponse removeRecast(String pluginId, String assetUuid) {
        validatePluginId(pluginId, "REMOVE_RECAST");
        validateAssetUuid(assetUuid, "REMOVE_RECAST");
        return runAction(
            "REMOVE_RECAST", pluginId, assetUuid, null, "REMOVE_RECAST: revert to Tenable default severity", null,
            () -> client.removeRecast(pluginId, assetUuid)
        );
    }

    // ── shared action runner ────────────────────────────────────────────

    private RecastResponse runAction(
        String actionType,
        String pluginId,
        String assetUuid,
        String newSeverity,
        String justification,
        LocalDateTime expiresAt,
        java.util.function.Supplier<RecastResponse> call
    ) {
        if (!props.isEnabled()) {
            log.info("[Tenable] disabled — {} for plugin {} is a no-op", actionType, pluginId);
            return RecastResponse.disabled(actionType, pluginId, assetUuid);
        }

        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            throw new IllegalStateException(
                "No tenant in TenantContext — TenableOutboundService." + actionType + " requires a tenant scope.");
        }

        // Object-level authz (SEC-INT-003): the upstream Tenable backend can be
        // shared across tenants, so a functional role check is not enough. The
        // target finding must appear in *this* tenant's ingested finding log
        // before we recast / accept-risk / remove a recast on it. When an
        // assetUuid is supplied we require the exact (asset, plugin) pair;
        // otherwise (global scope) the plugin must at least be in this tenant's
        // inventory.
        verifyTenantOwnsFinding(orgId, pluginId, assetUuid, actionType);

        Long userId = currentUserId();

        RecastResponse resp = null;
        Exception failure = null;
        try {
            resp = call.get();
        } catch (Exception ex) {
            failure = ex;
            log.warn("[Tenable] {} failed for plugin {} asset {}: {}", actionType, pluginId, assetUuid, ex.getMessage());
        }

        // Persist the audit row regardless of outcome.
        persistRecastLog(orgId, userId, actionType, pluginId, assetUuid, newSeverity, justification, expiresAt, resp, failure);

        if (failure != null) {
            if (failure instanceof RuntimeException re) throw re;
            throw new RuntimeException(failure);
        }

        if (resp != null && resp.accepted()) {
            emitAuditAndDomainEvent(orgId, actionType, pluginId, assetUuid, newSeverity, justification, expiresAt, resp);
        }
        log.info("[Tenable] {} plugin={} asset={} accepted={} http={} attempts={}",
            actionType, pluginId, assetUuid,
            resp != null && resp.accepted(),
            resp == null ? -1 : resp.httpStatus(),
            resp == null ? -1 : resp.attemptCount());
        return resp;
    }

    // ── audit row plumbing ──────────────────────────────────────────────

    private void persistRecastLog(
        Long orgId, Long userId, String actionType, String pluginId, String assetUuid,
        String newSeverity, String justification, LocalDateTime expiresAt,
        RecastResponse resp, Exception failure
    ) {
        try {
            TenableRecastLog row = new TenableRecastLog();
            row.setOrganisationId(orgId);
            row.setActionType(actionType);
            row.setTargetPluginId(pluginId);
            row.setTargetAssetUuid(assetUuid);
            row.setNewSeverity(newSeverity);
            row.setJustification(justification);
            row.setExpiresAt(expiresAt);
            row.setRequestedByUserId(userId);
            row.setHttpStatus(resp != null ? resp.httpStatus() : null);
            row.setResponsePayload(serialiseResponse(resp));
            row.setAttemptCount(resp != null ? resp.attemptCount() : props.getRetry().getMaxAttempts());
            if (failure != null) {
                row.setErrorMessage(truncate(failure.getClass().getSimpleName() + ": " + failure.getMessage(), 2000));
            }
            recastLogRepo.save(row);
        } catch (Exception ex) {
            // Audit row is best-effort — we don't want a logging failure to bubble up over a
            // successful action or mask the real failure.
            log.warn("[Tenable] failed to persist recast_log row: {}", ex.getMessage());
        }
    }

    private String serialiseResponse(RecastResponse resp) {
        if (resp == null) return null;
        try {
            return objectMapper.writeValueAsString(resp);
        } catch (JsonProcessingException ex) {
            return "{\"_serialise_error\":\"" + ex.getMessage() + "\"}";
        }
    }

    // ── F7 + F8 emission ────────────────────────────────────────────────

    private void emitAuditAndDomainEvent(
        Long orgId, String actionType, String pluginId, String assetUuid, String newSeverity,
        String justification, LocalDateTime expiresAt, RecastResponse resp
    ) {
        String triggerName = switch (actionType) {
            case "RECAST_SEVERITY"  -> "TENABLE_RECAST_APPLIED";
            case "ACCEPT_RISK"      -> "TENABLE_RISK_ACCEPTED";
            case "REMOVE_RECAST"    -> "TENABLE_RECAST_REMOVED";
            default                  -> actionType;
        };
        audit.log(triggerName, "TenableFinding", null,
            actionType + " plugin=" + pluginId
                + (assetUuid == null ? " scope=global" : " asset=" + assetUuid)
                + (newSeverity == null ? "" : " newSeverity=" + newSeverity)
                + (expiresAt == null ? "" : " expiresAt=" + expiresAt)
                + " ruleId=" + resp.tenableRuleId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenablePluginId", pluginId);
        payload.put("tenableAssetUuid", assetUuid);
        payload.put("actionType", actionType);
        payload.put("newSeverity", newSeverity);
        payload.put("justification", justification);
        payload.put("expiresAt", expiresAt);
        payload.put("tenableRuleId", resp.tenableRuleId());
        payload.put("attemptCount", resp.attemptCount());
        publisher.publishEvent(DomainEvent.of(
            orgId, triggerName, "TenableFinding", null, payload));
    }

    // ── validation helpers ──────────────────────────────────────────────

    private static void validatePluginId(String pluginId, String actionType) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("Tenable plugin id is required for " + actionType);
        }
        if (!PLUGIN_ID_RX.matcher(pluginId).matches()) {
            throw new IllegalArgumentException(
                "Tenable plugin id must be numeric for " + actionType + " (got: " + pluginId + ")");
        }
    }

    /** Optional asset scope — when present must be a UUID (SEC-INT-004). */
    private static void validateAssetUuid(String assetUuid, String actionType) {
        if (assetUuid == null || assetUuid.isBlank()) {
            return; // null/blank = global scope, allowed
        }
        if (!ASSET_UUID_RX.matcher(assetUuid).matches()) {
            throw new IllegalArgumentException(
                "Tenable asset uuid must be a valid UUID for " + actionType);
        }
    }

    private static void validateJustification(String justification, String actionType) {
        if (justification == null || justification.trim().length() < MIN_JUSTIFICATION_CHARS) {
            throw new IllegalArgumentException(
                "Justification must be at least " + MIN_JUSTIFICATION_CHARS
                    + " characters for " + actionType + " (audit evidence requirement)");
        }
    }

    /**
     * Reject recast / accept-risk / remove-recast on a finding this tenant has
     * never ingested (SEC-INT-003). Prevents a SOC/risk user in tenant A from
     * mutating tenant B's findings on a shared upstream Tenable instance.
     */
    private void verifyTenantOwnsFinding(Long orgId, String pluginId, String assetUuid, String actionType) {
        boolean owned = (assetUuid == null || assetUuid.isBlank())
            ? findingLogRepo.findFirstByOrganisationIdAndTenablePluginId(orgId, pluginId).isPresent()
            : findingLogRepo
                .findByOrganisationIdAndTenableAssetUuidAndTenablePluginId(orgId, assetUuid, pluginId)
                .isPresent();
        if (!owned) {
            log.warn("[Tenable] {} rejected — plugin {} asset {} is not in tenant {}'s finding inventory",
                actionType, pluginId, assetUuid, orgId);
            throw new AccessDeniedException("Tenable finding is not in this tenant's inventory");
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof GrcUserDetails grc) return grc.getUserId();
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
