package com.tss.grc.module.integrations.crowdstrike.service;

import com.tss.grc.common.json.JsonText;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.crowdstrike.client.CrowdStrikeClient;
import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentResponse;
import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeActionLog;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeActionLogRepository;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeDetectionLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.tss.grc.common.exception.DomainAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pushes containment actions into Falcon — host isolate / lift / hide.
 *
 * <p>Each request is recorded in {@code platform.crowdstrike_action_log}
 * regardless of outcome (successful or failed). On success we emit an F7
 * audit_log entry and an F8 domain event so automation rules can react
 * ({@code CROWDSTRIKE_HOST_CONTAINED} / {@code CROWDSTRIKE_CONTAINMENT_LIFTED}).
 *
 * <p>{@code requestedByUserId} is pulled from the {@link SecurityContextHolder}
 * — this is the load-bearing audit field for NCA ECC-2 traceability.
 *
 * <p>Retry semantics live inside {@link LiveCrowdStrikeClient}; this service
 * inspects the resulting {@link ContainmentResponse#attemptCount()} when
 * writing the action_log row.
 */
@Service
public class CrowdStrikeOutboundService {

    private static final Logger log = LoggerFactory.getLogger(CrowdStrikeOutboundService.class);

    private final CrowdStrikeClient client;
    private final CrowdStrikeProperties props;
    private final CrowdStrikeActionLogRepository actionLogRepo;
    private final CrowdStrikeDetectionLogRepository detectionLogRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public CrowdStrikeOutboundService(
        CrowdStrikeClient client,
        CrowdStrikeProperties props,
        CrowdStrikeActionLogRepository actionLogRepo,
        CrowdStrikeDetectionLogRepository detectionLogRepo,
        AuditLogService audit,
        ApplicationEventPublisher publisher,
        ObjectMapper objectMapper
    ) {
        this.client = client;
        this.props = props;
        this.actionLogRepo = actionLogRepo;
        this.detectionLogRepo = detectionLogRepo;
        this.audit = audit;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    // ── public API ──────────────────────────────────────────────────────
    //
    // PERF-IO-001: deliberately NOT @Transactional. runAction() invokes a
    // blocking Falcon HTTP call (call.get()); a surrounding transaction would
    // hold a pooled DB connection across that round-trip + retry backoff. The
    // only writes are the best-effort action_log row (persistActionLog) and the
    // F7 audit row, each of which runs in its own short repository transaction.

    public ContainmentResponse containHost(String falconHostId, String reasonText) {
        return runAction("CONTAIN_HOST", falconHostId, reasonText, () -> client.containHost(falconHostId, reasonText));
    }

    public ContainmentResponse liftContainment(String falconHostId) {
        return runAction("LIFT_CONTAINMENT", falconHostId, null, () -> client.liftContainment(falconHostId));
    }

    public ContainmentResponse hideHost(String falconHostId) {
        return runAction("HIDE_HOST", falconHostId, null, () -> client.hideHost(falconHostId));
    }

    // ── shared action runner ────────────────────────────────────────────

    private ContainmentResponse runAction(
        String actionType,
        String falconHostId,
        String reasonText,
        java.util.function.Supplier<ContainmentResponse> call
    ) {
        if (falconHostId == null || falconHostId.isBlank()) {
            throw new IllegalArgumentException("Falcon host id is required for " + actionType);
        }
        if (!props.isEnabled()) {
            log.info("[CrowdStrike] disabled — {} for {} is a no-op", actionType, falconHostId);
            return ContainmentResponse.disabled(actionType, falconHostId);
        }

        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            throw new IllegalStateException(
                "No tenant in TenantContext — CrowdStrikeOutboundService." + actionType + " requires a tenant scope.");
        }

        // Object-level authz (SEC-INT-003): the upstream Falcon backend can be
        // shared across tenants, so a functional role check is not enough. The
        // target host must appear in *this* tenant's ingested detection log
        // before we dispatch a destructive containment action against it.
        if (detectionLogRepo.findFirstByOrganisationIdAndFalconHostId(orgId, falconHostId).isEmpty()) {
            log.warn("[CrowdStrike] {} rejected — host {} is not in tenant {}'s inventory",
                actionType, falconHostId, orgId);
            throw new DomainAccessDeniedException("Falcon host is not in this tenant's inventory");
        }

        Long userId = currentUserId();

        ContainmentResponse resp = null;
        Exception failure = null;
        try {
            resp = call.get();
        } catch (Exception ex) {
            failure = ex;
            log.warn("[CrowdStrike] {} failed for host {}: {}", actionType, falconHostId, ex.getMessage());
        }

        // Persist the audit row regardless of outcome.
        persistActionLog(orgId, userId, actionType, falconHostId, reasonText, resp, failure);

        if (failure != null) {
            if (failure instanceof RuntimeException re) throw re;
            throw new RuntimeException(failure);
        }

        if (resp != null && resp.accepted()) {
            emitAuditAndDomainEvent(orgId, actionType, falconHostId, reasonText, resp);
        }
        log.info("[CrowdStrike] {} host={} accepted={} http={} attempts={}",
            actionType, falconHostId,
            resp != null && resp.accepted(),
            resp == null ? -1 : resp.httpStatus(),
            resp == null ? -1 : resp.attemptCount());
        return resp;
    }

    // ── audit row plumbing ──────────────────────────────────────────────

    private void persistActionLog(
        Long orgId, Long userId, String actionType, String hostId, String reasonText,
        ContainmentResponse resp, Exception failure
    ) {
        try {
            CrowdStrikeActionLog row = new CrowdStrikeActionLog();
            row.setOrganisationId(orgId);
            row.setActionType(actionType);
            row.setTargetFalconHostId(hostId);
            row.setRequestedByUserId(userId);
            row.setReasonText(reasonText);
            row.setHttpStatus(resp != null ? resp.httpStatus() : null);
            row.setResponsePayload(serialiseResponse(resp));
            row.setAttemptCount(resp != null ? resp.attemptCount() : props.getRetry().getMaxAttempts());
            if (failure != null) {
                row.setErrorMessage(truncate(failure.getClass().getSimpleName() + ": " + failure.getMessage(), 2000));
            }
            actionLogRepo.save(row);
        } catch (Exception ex) {
            // Audit row is best-effort — we don't want a logging failure to bubble up over a
            // successful action or mask the real failure.
            log.warn("[CrowdStrike] failed to persist action_log row: {}", ex.getMessage());
        }
    }

    private String serialiseResponse(ContainmentResponse resp) {
        if (resp == null) return null;
        try {
            return objectMapper.writeValueAsString(resp);
        } catch (JsonProcessingException ex) {
            return "{\"_serialise_error\":\"" + JsonText.escape(ex.getMessage()) + "\"}";
        }
    }

    // ── F7 + F8 emission ────────────────────────────────────────────────

    private void emitAuditAndDomainEvent(
        Long orgId, String actionType, String hostId, String reasonText, ContainmentResponse resp
    ) {
        String triggerName = switch (actionType) {
            case "CONTAIN_HOST"      -> "CROWDSTRIKE_HOST_CONTAINED";
            case "LIFT_CONTAINMENT"  -> "CROWDSTRIKE_CONTAINMENT_LIFTED";
            case "HIDE_HOST"         -> "CROWDSTRIKE_HOST_HIDDEN";
            default                   -> actionType;
        };
        audit.log(triggerName, "FalconHost", null,
            actionType + " host=" + hostId
                + (reasonText == null ? "" : " reason=" + reasonText)
                + " resourceId=" + resp.falconResourceId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("falconHostId", hostId);
        payload.put("actionType", actionType);
        payload.put("reason", reasonText);
        payload.put("falconResourceId", resp.falconResourceId());
        payload.put("attemptCount", resp.attemptCount());
        publisher.publishEvent(DomainEvent.of(
            orgId, triggerName, "FalconHost", null, payload));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Long currentUserId() {
        return com.tss.grc.common.security.CurrentActor.userId();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
