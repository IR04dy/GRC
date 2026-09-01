package com.tss.grc.module.audit_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.tss.grc.module.audit_lc.dto.FindingDto;
import com.tss.grc.module.audit_lc.entity.Finding;
import com.tss.grc.module.audit_lc.repository.FindingRepository;
import com.tss.grc.common.util.ExternalIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finding service — guards the IIA-aligned state machine.
 *
 *   OPEN                  → MGMT_RESPONSE
 *   MGMT_RESPONSE         → IN_REMEDIATION
 *   IN_REMEDIATION        → AWAITING_VERIFICATION
 *   AWAITING_VERIFICATION → CLOSED
 *   (any non-CLOSED)      → REOPENED via separate endpoint (not implemented)
 */
@Service
@RequiredArgsConstructor
public class FindingService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "OPEN",                  Set.of("MGMT_RESPONSE"),
        "MGMT_RESPONSE",         Set.of("IN_REMEDIATION"),
        "IN_REMEDIATION",        Set.of("AWAITING_VERIFICATION"),
        "AWAITING_VERIFICATION", Set.of("CLOSED")
    );

    /**
     * DERIVED terminal set — the states with no outgoing edge (F2-370; never a second hand-typed
     * list, F2-196/F2-336). {@link #ALLOWED} deliberately OMITS terminal states as keys — a
     * terminal is a target with no entry, so {@code getOrDefault} returns empty — and
     * {@code AuditFindingMachineParityTest} pins {@code ALLOWED} at exactly four keys, so this must
     * derive over the state vocabulary (keys ∪ targets) rather than by adding {@code CLOSED} as an
     * empty-value key. Yields {@code [CLOSED]}.
     *
     * <p>{@code public} because {@code governance.threelines.ThreeLinesService} (the 3LoD Line-3
     * band) reads {@code audit_lc.findings} and used to hand-type {@code List.of("CLOSED")} for its
     * "open findings" filter — a copy of this machine that drifts if a terminal state is added. It
     * now reads this ONE derived set (F2-336).
     */
    public static final List<String> TERMINAL = java.util.stream.Stream.concat(
            ALLOWED.keySet().stream(),
            ALLOWED.values().stream().flatMap(Set::stream))
        .distinct()
        .filter(s -> ALLOWED.getOrDefault(s, Set.of()).isEmpty())
        .sorted()
        .toList();

    private final FindingRepository       repo;
    private final AuditLogService         audit;
    private final ApplicationEventPublisher eventPublisher;

    /** Sprint 4 §2.7 — optional realtime broadcaster (null in unit tests). */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<FindingDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByRaisedAtDesc(orgId).stream()
            .map(FindingDto::from).collect(Collectors.toList());
    }

    public List<FindingDto> listForEngagement(String engagementCode) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndEngagementCodeOrderByRaisedAtDesc(orgId, engagementCode).stream()
            .map(FindingDto::from).collect(Collectors.toList());
    }

    public FindingDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(FindingDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + externalId));
    }

    @Transactional
    public FindingDto create(FindingDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        long count = repo.findByOrganisationIdAndEngagementCodeOrderByRaisedAtDesc(orgId, req.getEngagementCode()).size();
        Finding f = new Finding();
        // F2-203. The caller's `id` and `refNumber` are IGNORED — both are minted here.
        //
        // This was `req.getId() != null ? req.getId() : generateExternalId()`, so a client
        // could name the reference of an internal-audit finding. That is F2-172(d)'s defect
        // ("only the server may issue a tracking code"), closed on the intake substrate and
        // then again on DSR cases in F2-202; this is its third instance. The browser-local
        // copy F2-203 retired was minting exactly such a reference —
        // `FIN-<year>-<4 base-36 chars>` — alongside its own `FIN-%03d` sequence counted over
        // a cache that included a seeded finding, so the first real finding raised on an
        // engagement would have been numbered FIN-002.
        //
        // Nothing supplies either field: no test sets them, and the only client that now
        // POSTs here deliberately omits both. Refusing them cannot break a working caller.
        f.setExternalId(generateExternalId());
        f.setOrganisationId(orgId);
        f.setEngagementCode(req.getEngagementCode());
        f.setRefNumber(String.format(Locale.ROOT, "FIN-%03d", count + 1));
        f.setTitleEn(req.getTitleEn()); f.setTitleAr(req.getTitleAr());
        f.setSeverity(req.getSeverity()); f.setStatus("OPEN");
        f.setControlCrosswalk(req.getControlCrosswalk());
        f.setConditionEn(req.getConditionEn()); f.setConditionAr(req.getConditionAr());
        f.setCriteriaEn(req.getCriteriaEn()); f.setCriteriaAr(req.getCriteriaAr());
        f.setCauseEn(req.getCauseEn()); f.setCauseAr(req.getCauseAr());
        f.setRecommendationEn(req.getRecommendationEn()); f.setRecommendationAr(req.getRecommendationAr());
        f.setTargetDate(req.getTargetDate() != null ? req.getTargetDate() : LocalDate.now().plusDays(30));
        f.setOwnerRole(req.getOwnerRole());
        Finding saved = repo.save(f);
        audit.log("CREATE", "Finding", saved.getId(),
            "Finding " + saved.getRefNumber()
            + " raised on engagement " + saved.getEngagementCode()
            + " · severity=" + saved.getSeverity());
        // Fan out to F8 — the engine listens for FINDING_CREATED and
        // matches enabled rules against the payload (engagementCode,
        // severity, ownerRole). Seed rules can route CRITICAL findings
        // to the audit lead, PDPL-anchored findings to the DPO, etc.
        // Same shape as CapaTaskService (PR #46) — no schema or engine
        // change required.
        eventPublisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "FINDING_CREATED",
            "Finding", saved.getId(), saved.getExternalId(),
            payloadFor(saved, null)));
        return FindingDto.from(saved);
    }

    @Transactional
    public FindingDto transition(String externalId, FindingDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        Finding f = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(f.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(f.getStatus(), req.getStatus(), allowed);
        }
        String previousStatus = f.getStatus();
        f.setStatus(req.getStatus());
        if (req.getMgmtResponseEn() != null) f.setMgmtResponseEn(req.getMgmtResponseEn());
        if (req.getMgmtResponseAr() != null) f.setMgmtResponseAr(req.getMgmtResponseAr());
        if (req.getMgmtResponseAuthor() != null) f.setMgmtResponseAuthor(req.getMgmtResponseAuthor());
        Finding saved = repo.save(f);
        audit.log("TRANSITION", "Finding", saved.getId(),
            "Finding " + saved.getRefNumber()
            + " " + previousStatus + " → " + saved.getStatus()
            + (req.getMgmtResponseAuthor() != null
                ? " · response by " + req.getMgmtResponseAuthor() : ""));
        // Fan out to F8 — separate event from CREATED so rules can target
        // status transitions specifically. Payload carries previousStatus
        // so a rule can match "Finding closed on a CRITICAL engagement"
        // (status=CLOSED + severity=CRITICAL + previousStatus=AWAITING_VERIFICATION).
        eventPublisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "FINDING_TRANSITIONED",
            "Finding", saved.getId(), saved.getExternalId(),
            payloadFor(saved, previousStatus)));
        FindingDto out = FindingDto.from(saved);
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "audit_finding", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        Finding f = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + externalId));
        f.setIsDeleted((short) 1);
        repo.save(f);
        audit.log("DELETE", "Finding", f.getId(),
            "Finding " + f.getRefNumber() + " soft-deleted (status=" + f.getStatus() + ")");
    }

    private String generateExternalId() {
        // F2-48: substring(0, 6) kept the HIGH-ORDER hex digits, so this id changed once
        // per 2^20 ms (~17.5 min) and every FIN raised in one window collided on the unique
        // index. Shared generator now, so the tenth copy cannot reintroduce it.
        return ExternalIds.generate("FIN");
    }

    /** Canonical payload Map for F8 rule-condition matching on Finding
     *  events. Engine evaluates equality + contains on these string
     *  keys; values are coerced to "" so the matcher never NPEs on a
     *  null. {@code previousStatus} only carries on TRANSITIONED. */
    private static Map<String, Object> payloadFor(Finding f, String previousStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("externalId",     nullSafe(f.getExternalId()));
        m.put("refNumber",      nullSafe(f.getRefNumber()));
        m.put("engagementCode", nullSafe(f.getEngagementCode()));
        m.put("severity",       nullSafe(f.getSeverity()));
        m.put("status",         nullSafe(f.getStatus()));
        m.put("ownerRole",      nullSafe(f.getOwnerRole()));
        if (previousStatus != null) m.put("previousStatus", previousStatus);
        return m;
    }

    private static String nullSafe(String v) { return v == null ? "" : v; }
}
