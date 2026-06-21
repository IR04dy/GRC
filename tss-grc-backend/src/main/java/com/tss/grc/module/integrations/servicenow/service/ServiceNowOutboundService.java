package com.tss.grc.module.integrations.servicenow.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.servicenow.client.ServiceNowClient;
import com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentRequest;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentResponse;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Top-level entry point for pushing GRC events into ServiceNow.
 *
 * <p>Sources supported:
 * <ul>
 *   <li>Cyber incidents (CYBER_INCIDENT)</li>
 *   <li>Audit findings (AUDIT_FINDING)</li>
 *   <li>CAPA tasks (CAPA_TASK)</li>
 *   <li>Compliance gaps (COMPLIANCE_GAP)</li>
 * </ul>
 *
 * <p>The flexible {@link #pushGeneric(String, Map)} method accepts a free-form
 * map so we can hook into modules whose DTOs haven't stabilised yet without
 * forcing a tight cross-module compile dependency.
 */
@Service
public class ServiceNowOutboundService {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowOutboundService.class);
    private static final String INTEGRATION_KEY = "SERVICENOW";

    private final ServiceNowClient client;
    private final ServiceNowProperties props;
    private final IntegrationExternalLinkRepository linkRepo;

    public ServiceNowOutboundService(
        ServiceNowClient client,
        ServiceNowProperties props,
        IntegrationExternalLinkRepository linkRepo
    ) {
        this.client = client;
        this.props = props;
        this.linkRepo = linkRepo;
    }

    // ── public façade ──────────────────────────────────────────────────
    //
    // PERF-IO-001: these push entry points are deliberately NOT @Transactional.
    // They invoke a blocking remote HTTP client (client.createIncident); holding
    // an open transaction across that round-trip would pin a pooled Hikari
    // connection for the entire remote latency + retry backoff. The only DB write
    // is the single external_link upsert in recordExternalLink(), which runs in
    // Spring Data's own short repository transaction — atomicity across the HTTP
    // call is neither needed nor desirable here. Do not re-add @Transactional.

    public ServiceNowIncidentResponse pushCyberIncident(Map<String, Object> grcFields) {
        return pushGeneric("CYBER_INCIDENT", grcFields);
    }

    public ServiceNowIncidentResponse pushFinding(Map<String, Object> grcFields) {
        return pushGeneric("AUDIT_FINDING", grcFields);
    }

    public ServiceNowIncidentResponse pushCapaTask(Map<String, Object> grcFields) {
        return pushGeneric("CAPA_TASK", grcFields);
    }

    public ServiceNowIncidentResponse pushComplianceGap(Map<String, Object> grcFields) {
        return pushGeneric("COMPLIANCE_GAP", grcFields);
    }

    /**
     * Push directly with a fully-built request DTO — used by the admin
     * {@code POST /test-push} endpoint and by callers that want full control.
     */
    public ServiceNowIncidentResponse push(ServiceNowIncidentRequest request) {
        if (!props.isEnabled()) {
            log.info("[ServiceNow] disabled — push() is a no-op for {}#{}",
                request.grcSourceModule(), request.grcSourceId());
            return ServiceNowIncidentResponse.disabled();
        }

        // Always enforce severity-derived impact/urgency on top of any caller customFields.
        Map<String, String> custom = new LinkedHashMap<>(request.customFields());
        int[] iu = mapSeverityToImpactUrgency(request.severity());
        custom.put("impact", String.valueOf(iu[0]));
        custom.put("urgency", String.valueOf(iu[1]));

        ServiceNowIncidentRequest enriched = new ServiceNowIncidentRequest(
            request.grcSourceModule(),
            request.grcSourceId(),
            request.shortDescription(),
            request.description(),
            request.severity(),
            request.category() != null ? request.category() : props.getDefaults().getCategory(),
            request.assignmentGroup(),
            request.callerEmail(),
            custom
        );

        ServiceNowIncidentResponse resp = client.createIncident(enriched);
        recordExternalLink(enriched, resp);
        log.info("[ServiceNow] pushed {}#{} -> {} (sysId={})",
            enriched.grcSourceModule(), enriched.grcSourceId(), resp.snowNumber(), resp.snowSysId());
        return resp;
    }

    /**
     * Generic push — maps a free-form GRC field map into a {@link ServiceNowIncidentRequest}.
     * Recognised keys (all optional except {@code id} or {@code externalId}):
     * <ul>
     *   <li>{@code id} or {@code externalId} — GRC record identifier</li>
     *   <li>{@code title} or {@code shortDescription}</li>
     *   <li>{@code description}</li>
     *   <li>{@code severity} — CRITICAL/HIGH/MEDIUM/LOW</li>
     *   <li>{@code category}</li>
     *   <li>{@code assignmentGroup}</li>
     *   <li>{@code callerEmail}</li>
     * </ul>
     */
    public ServiceNowIncidentResponse pushGeneric(String sourceModule, Map<String, Object> grcFields) {
        if (!props.isEnabled()) {
            log.info("[ServiceNow] disabled — pushGeneric({}) is a no-op", sourceModule);
            return ServiceNowIncidentResponse.disabled();
        }
        String grcId = firstNonBlank(
            str(grcFields.get("externalId")),
            str(grcFields.get("id")),
            "unknown");
        String shortDesc = firstNonBlank(
            str(grcFields.get("shortDescription")),
            str(grcFields.get("title")),
            sourceModule + " #" + grcId);
        String severity = firstNonBlank(str(grcFields.get("severity")), "MEDIUM").toUpperCase(Locale.ROOT);
        ServiceNowIncidentRequest req = new ServiceNowIncidentRequest(
            sourceModule,
            grcId,
            truncate(shortDesc, 160),
            str(grcFields.get("description")),
            severity,
            str(grcFields.get("category")),
            str(grcFields.get("assignmentGroup")),
            str(grcFields.get("callerEmail")),
            Map.of()
        );
        return push(req);
    }

    // ── severity → impact/urgency ──────────────────────────────────────

    /**
     * Map GRC severity to ServiceNow impact (1-3) and urgency (1-3).
     * ServiceNow priority is computed by SNow itself from impact × urgency.
     * Mapping rules (per spec):
     *   CRITICAL → impact=1, urgency=1
     *   HIGH     → impact=2, urgency=2
     *   MEDIUM   → impact=3, urgency=2
     *   LOW      → impact=3, urgency=3
     */
    public static int[] mapSeverityToImpactUrgency(String severity) {
        if (severity == null) return new int[]{3, 3};
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> new int[]{1, 1};
            case "HIGH"     -> new int[]{2, 2};
            case "MEDIUM"   -> new int[]{3, 2};
            case "LOW"      -> new int[]{3, 3};
            default         -> new int[]{3, 3};
        };
    }

    // ── helpers ────────────────────────────────────────────────────────

    private void recordExternalLink(ServiceNowIncidentRequest req, ServiceNowIncidentResponse resp) {
        if (resp == null || resp.isDisabled()) return;
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            log.debug("[ServiceNow] no tenant in context; skipping external_link row "
                + "(call originated outside an HTTP request)");
            return;
        }
        Optional<IntegrationExternalLink> existing = linkRepo
            .findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
                orgId, INTEGRATION_KEY, req.grcSourceModule(), req.grcSourceId());

        IntegrationExternalLink link = existing.orElseGet(IntegrationExternalLink::new);
        if (existing.isEmpty()) {
            link.setOrganisationId(orgId);
            link.setIntegration(INTEGRATION_KEY);
            link.setGrcModule(req.grcSourceModule());
            link.setGrcRecordId(req.grcSourceId());
        }
        link.setExternalId(resp.snowSysId());
        link.setExternalNumber(resp.snowNumber());
        link.setExternalUrl(resp.url());
        link.setLastPushedAt(LocalDateTime.now());
        link.setLastKnownState(resp.state());
        link.setUpdatedAt(LocalDateTime.now());
        linkRepo.save(link);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // Test seam — exposed for unit testing.
    Map<String, Object> debugSeverityMap(String severity) {
        int[] iu = mapSeverityToImpactUrgency(severity);
        Map<String, Object> m = new HashMap<>();
        m.put("impact", iu[0]);
        m.put("urgency", iu[1]);
        return m;
    }
}
