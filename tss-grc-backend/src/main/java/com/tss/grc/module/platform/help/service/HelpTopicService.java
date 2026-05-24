package com.tss.grc.module.platform.help.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpLookupResponse;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpTopicRequest;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpTopicResponse;
import com.tss.grc.module.platform.help.entity.HelpTopic;
import com.tss.grc.module.platform.help.repository.HelpTopicRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@link HelpTopic} service — Sprint 12 Embedded Help.
 *
 * <p>The hot path is {@link #lookupForRoute(String)} — fired every
 * time a user opens the floating help drawer. It resolves the right
 * topics for the current URL with this precedence:
 *
 * <ol>
 *   <li>org-specific row wins over platform-global (multi-tenant
 *       override)</li>
 *   <li>exact path match wins over wildcard match</li>
 *   <li>longest matching wildcard wins over shorter</li>
 *   <li>display_order ties are broken by topic title</li>
 * </ol>
 *
 * <p>F7+F8 contract:
 * <ul>
 *   <li>F7 audit_log: CREATE, UPDATE, DELETE, VIEW
 *       (track-view writes audit at INFO severity)</li>
 *   <li>F8 events: {@code HELP_TOPIC_CREATED},
 *       {@code HELP_TOPIC_UPDATED}, {@code HELP_TOPIC_DELETED},
 *       {@code HELP_TOPIC_VIEWED}</li>
 * </ul>
 *
 * <p>The view-tracking event is fire-and-forget — its single
 * consumer is the analytics path that surfaces under-documented
 * pages on the admin dashboard. We tolerate a small amount of audit
 * volume for a strong feedback loop on what users open.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelpTopicService {

    private final HelpTopicRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    // ─── Lookup ────────────────────────────────────────────────────

    /**
     * Resolve every topic that matches the given route, ordered by
     * the precedence rules above.
     */
    @Transactional(readOnly = true)
    public HelpLookupResponse lookupForRoute(String routePath) {
        Long orgId = TenantContext.requireOrganisationId();
        String normalised = normaliseRoute(routePath);
        List<HelpTopic> visible = repo.findAllVisible(orgId);

        // Group matches by topic_code so an org-override hides the
        // platform-global row sharing the same code.
        Map<String, HelpTopic> byCode = new LinkedHashMap<>();
        for (HelpTopic h : visible) {
            if (!matches(h.getRoutePattern(), normalised)) continue;
            HelpTopic existing = byCode.get(h.getTopicCode());
            if (existing == null) {
                byCode.put(h.getTopicCode(), h);
                continue;
            }
            // Tenant-scoped row (non-null orgId) wins over global.
            if (existing.getOrganisationId() == null && h.getOrganisationId() != null) {
                byCode.put(h.getTopicCode(), h);
            }
        }

        List<HelpTopicResponse> ordered = byCode.values().stream()
                // org-specific first, then by precedence (exact > wildcard),
                // then by display order, then alphabetically.
                .sorted(Comparator
                        .comparing((HelpTopic h) -> h.getOrganisationId() == null ? 1 : 0)
                        .thenComparing(h -> patternPrecedence(h.getRoutePattern()))
                        .thenComparing(HelpTopic::getDisplayOrder)
                        .thenComparing(HelpTopic::getTitleEn, Comparator.nullsLast(String::compareTo)))
                .map(HelpTopicResponse::from)
                .toList();

        return new HelpLookupResponse(normalised, ordered);
    }

    @Transactional(readOnly = true)
    public List<HelpTopicResponse> searchTopics(String query) {
        if (query == null || query.isBlank()) return List.of();
        Long orgId = TenantContext.requireOrganisationId();
        return repo.search(orgId, query.trim()).stream()
                .map(HelpTopicResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HelpTopicResponse> listAll() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findAllVisible(orgId).stream()
                .sorted(Comparator
                        .comparing(HelpTopic::getModule, Comparator.nullsLast(String::compareTo))
                        .thenComparing(HelpTopic::getRoutePattern, Comparator.nullsLast(String::compareTo))
                        .thenComparing(HelpTopic::getDisplayOrder))
                .map(HelpTopicResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HelpTopicResponse> listForModule(String module) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByModule(orgId, module).stream()
                .map(HelpTopicResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public HelpTopicResponse get(UUID externalId) {
        return HelpTopicResponse.from(mustGet(externalId));
    }

    // ─── Mutations ─────────────────────────────────────────────────

    @Transactional
    public HelpTopicResponse create(HelpTopicRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        if (repo.countByTopicCodeAndIsDeletedFalse(req.topicCode()) > 0) {
            throw new IllegalArgumentException(
                    "Help topic code already exists: " + req.topicCode());
        }
        HelpTopic h = new HelpTopic();
        // Author-created topics are always tenant-scoped — the seed
        // pre-loads the platform-global rows; user-created rows
        // belong to the tenant whose admin made them.
        h.setOrganisationId(orgId);
        applyFields(h, req);
        h = repo.save(h);

        audit.log("CREATE", "HelpTopic", h.getId(),
                "Help topic created: " + h.getTopicCode() + " — " + h.getTitleEn());
        events.publishEvent(new DomainEvent(
                orgId, "HELP_TOPIC_CREATED",
                "HelpTopic", h.getId(), h.getTopicCode(), payload(h)));
        return HelpTopicResponse.from(h);
    }

    @Transactional
    public HelpTopicResponse update(UUID externalId, HelpTopicRequest req) {
        HelpTopic h = mustGet(externalId);
        applyFields(h, req);
        h = repo.save(h);

        audit.log("UPDATE", "HelpTopic", h.getId(),
                "Help topic updated: " + h.getTopicCode());
        events.publishEvent(new DomainEvent(
                h.getOrganisationId() == null ? TenantContext.getCurrentTenant() : h.getOrganisationId(),
                "HELP_TOPIC_UPDATED",
                "HelpTopic", h.getId(), h.getTopicCode(), payload(h)));
        return HelpTopicResponse.from(h);
    }

    @Transactional
    public void delete(UUID externalId) {
        HelpTopic h = mustGet(externalId);
        h.softDelete(null);
        repo.save(h);
        audit.log("DELETE", "HelpTopic", h.getId(),
                "Help topic soft-deleted: " + h.getTopicCode());
        events.publishEvent(new DomainEvent(
                h.getOrganisationId() == null ? TenantContext.getCurrentTenant() : h.getOrganisationId(),
                "HELP_TOPIC_DELETED",
                "HelpTopic", h.getId(), h.getTopicCode(), payload(h)));
    }

    /**
     * Fire-and-forget analytics — captures that a user opened a
     * particular help topic on a particular page. The audit row
     * surfaces under-documented pages on the admin dashboard.
     */
    @Transactional
    public void trackView(UUID externalId, String route) {
        HelpTopic h = mustGet(externalId);
        Long orgId = TenantContext.getCurrentTenant();
        audit.log("VIEW", "HelpTopic", h.getId(),
                "Help viewed: " + h.getTopicCode() + " on " + (route == null ? "?" : route));
        Map<String, Object> p = payload(h);
        p.put("viewRoute", route == null ? "" : route);
        events.publishEvent(new DomainEvent(
                orgId, "HELP_TOPIC_VIEWED",
                "HelpTopic", h.getId(), h.getTopicCode(), p));
    }

    // ─── Internals ─────────────────────────────────────────────────

    private HelpTopic mustGet(UUID externalId) {
        return repo.findByExternalIdAndIsDeletedFalse(externalId)
                .orElseThrow(() -> new EntityNotFoundException("HelpTopic " + externalId));
    }

    private void applyFields(HelpTopic h, HelpTopicRequest r) {
        h.setTopicCode(r.topicCode());
        h.setTitleEn(r.titleEn());
        h.setTitleAr(r.titleAr());
        h.setSummaryEn(r.summaryEn());
        h.setSummaryAr(r.summaryAr());
        h.setBodyEn(r.bodyEn());
        h.setBodyAr(r.bodyAr());
        h.setRoutePattern(r.routePattern());
        h.setModule(r.module());
        h.setRegulatoryAnchor(r.regulatoryAnchor());
        h.setRegulatorLinkUrl(r.regulatorLinkUrl());
        h.setRelatedKbArticleId(r.relatedKbArticleId());
        h.setRelatedWorkflowCodes(r.relatedWorkflowCodes());
        if (r.severity() != null && !r.severity().isBlank()) h.setSeverity(r.severity());
        if (r.displayOrder() != null) h.setDisplayOrder(r.displayOrder());
    }

    private static Map<String, Object> payload(HelpTopic h) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("topicCode",       safe(h.getTopicCode()));
        p.put("externalId",      h.getExternalId() == null ? "" : h.getExternalId().toString());
        p.put("routePattern",    safe(h.getRoutePattern()));
        p.put("module",          safe(h.getModule()));
        p.put("severity",        safe(h.getSeverity()));
        p.put("organisationId",  h.getOrganisationId() == null ? "GLOBAL" : h.getOrganisationId().toString());
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }

    // ─── Route matching ────────────────────────────────────────────

    /**
     * Lowercase + strip trailing slash + drop hash fragment so the
     * lookup is robust against trailing punctuation and browser
     * normalisation differences.
     */
    static String normaliseRoute(String raw) {
        if (raw == null || raw.isBlank()) return "/";
        String r = raw.trim();
        int hash = r.indexOf('#');
        if (hash >= 0) r = r.substring(0, hash);
        int qm = r.indexOf('?');
        if (qm >= 0) r = r.substring(0, qm);
        if (!r.startsWith("/")) r = "/" + r;
        if (r.length() > 1 && r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r.toLowerCase();
    }

    /**
     * Match a route pattern (exact path or wildcard with `*`) against
     * a normalised route. Translates the pattern to a regex:
     *   - `*` → any path segment (no slash)
     *   - any other char is literal
     */
    static boolean matches(String pattern, String route) {
        if (pattern == null) return false;
        String p = pattern.toLowerCase().trim();
        if (p.equals(route)) return true;
        if (!p.contains("*")) return false;
        // Translate to regex: escape regex metachars then replace `*`.
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '*') {
                regex.append("[^/]+");
            } else if ("\\.[]{}()+?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append("$");
        try {
            return Pattern.compile(regex.toString()).matcher(route).matches();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Precedence weight used for sort:
     *   0 = exact (no wildcards)
     *   N = number of wildcards (fewer is more specific → smaller wins)
     */
    static int patternPrecedence(String pattern) {
        if (pattern == null) return 100;
        int wildcards = 0;
        for (int i = 0; i < pattern.length(); i++) if (pattern.charAt(i) == '*') wildcards++;
        return wildcards;
    }
}
