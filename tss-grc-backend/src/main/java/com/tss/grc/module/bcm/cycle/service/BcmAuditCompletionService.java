package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

/**
 * Combined service for the audit-completion bundle:
 *
 * <ul>
 *   <li><b>Tier-3 #18</b> — Management Review workflow (ISO 22301 §9.3)</li>
 *   <li><b>Tier-3 #21</b> — SIEM/SOAR inbound webhook handler</li>
 *   <li><b>Tier-4 #24</b> — SAMA OPRES summary (impact-tolerance posture)</li>
 *   <li><b>Tier-4 #29</b> — PDPL Article 23 breach-notification drafter</li>
 * </ul>
 *
 * <p>Kept in one service because each item is a thin orchestrator (≤ 3
 * methods) over an existing entity. A dedicated service per item would
 * scatter the audit-trail wiring across four near-empty classes.
 */
@Service
@RequiredArgsConstructor
public class BcmAuditCompletionService {

    private static final Set<String> MR_STATUSES =
        Set.of("DRAFT", "IN_PROGRESS", "APPROVED", "ARCHIVED");

    private final BcmManagementReviewRepository      mgmtReviews;
    private final SiemInboundEventRepository         siemEvents;
    private final CrisisEventRepository              crisisEvents;
    private final BcmBiaAssessmentRepository         bias;
    private final AuditLogService                    audit;
    private final ApplicationEventPublisher          eventPublisher;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ─── Tier-3 #18: Management reviews ──────────────────────────

    @Transactional(readOnly = true)
    public List<BcmManagementReview> listManagementReviews() {
        return mgmtReviews.findByOrganisationIdOrderByReviewDateDesc(org());
    }

    @Transactional(readOnly = true)
    public BcmManagementReview getManagementReview(Long id) {
        return mgmtReviews.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Management review not found: " + id));
    }

    @Transactional
    public BcmManagementReview createManagementReview(BcmManagementReview body) {
        if (body.getReviewDate() == null)
            throw new IllegalArgumentException("reviewDate is required");
        if (body.getChairpersonName() == null || body.getChairpersonName().isBlank())
            throw new IllegalArgumentException("chairpersonName is required");
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getReviewCode() == null || body.getReviewCode().isBlank()) {
            body.setReviewCode("MR-" + Year.now().getValue() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        }
        if (body.getStatus() == null) body.setStatus("DRAFT");
        if (!MR_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid status: " + body.getStatus());

        BcmManagementReview saved = mgmtReviews.save(body);
        audit.log("CREATE", "BcmManagementReview", saved.getId(),
            "Management review created: " + saved.getReviewCode());
        return saved;
    }

    @Transactional
    public BcmManagementReview transitionManagementReview(Long id, String to, String actor) {
        BcmManagementReview r = getManagementReview(id);
        if (!MR_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = r.getStatus();
        r.setStatus(to);
        r.setUpdatedAt(LocalDateTime.now());
        if ("APPROVED".equals(to)) {
            r.setApprovedAt(LocalDateTime.now());
            r.setApprovedBy(actor);
        }
        BcmManagementReview saved = mgmtReviews.save(r);
        audit.log("TRANSITION", "BcmManagementReview", saved.getId(),
            "Management review " + saved.getReviewCode() + ": " + from + " → " + to);
        return saved;
    }

    // ─── Tier-3 #21: SIEM/SOAR webhook handler ─────────────────

    public record SiemInboundPayload(
        String sourceSystem,
        String sourceAlertId,
        Integer severity,
        String title,
        String summary,
        Map<String, Object> rawPayload
    ) {}

    /**
     * Ingest one external SIEM/SOAR alert. Always persists the raw row for
     * audit, regardless of escalation decision.
     *
     * <p>Auto-escalation rule (v1, deliberately simple — a future sprint can
     * move it into {@code BcmSettings}): severity ≥ 3 escalates to a fresh
     * CrisisEvent in DETECTED state with {@code triggerSource=CYBER}.
     * severity 1-2 lands as IGNORED — auditable but no crisis.
     */
    @Transactional
    public SiemInboundEvent ingestSiemEvent(SiemInboundPayload payload) {
        Long o = org();
        SiemInboundEvent row = new SiemInboundEvent();
        row.setOrganisationId(o);
        row.setSourceSystem(payload.sourceSystem() == null ? "CUSTOM" : payload.sourceSystem());
        row.setSourceAlertId(payload.sourceAlertId());
        int sev = payload.severity() == null ? 1 : payload.severity();
        if (sev < 1) sev = 1; if (sev > 5) sev = 5;
        row.setSeverity(sev);
        row.setTitle(payload.title() == null || payload.title().isBlank()
            ? "(untitled SIEM alert)" : payload.title());
        row.setSummary(payload.summary());
        // Serialise the payload map to JSON for the audit trail
        try {
            row.setRawPayloadJson(payload.rawPayload() == null ? "{}"
                : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload.rawPayload()));
        } catch (Exception e) {
            row.setRawPayloadJson("{\"_serialisation_error\":\"" + e.getMessage() + "\"}");
        }

        if (sev >= 3) {
            // Escalate: create a fresh CrisisEvent in DETECTED state
            CrisisEvent ce = new CrisisEvent();
            ce.setOrganisationId(o);
            ce.setEventCode("CRIS-SIEM-" + System.currentTimeMillis());
            ce.setTitleEn("SIEM auto-escalation: " + row.getTitle());
            ce.setDescription(row.getSummary());
            ce.setSeverity(sev);
            ce.setTriggerSource("CYBER");
            ce.setStatus("DETECTED");
            ce.setDetectionTime(LocalDateTime.now());
            ce.setCyberRelated(true);
            // F2-238 — this producer saves straight to the repository rather than through
            // CrisisEventService.create, so it does not get that method's defaults. Both columns
            // are NOT NULL and used to be filled by field initialisers on the entity; those had to
            // go because they made update()'s null-guards decorative. Stated rather than inherited:
            // a SIEM auto-escalation genuinely does not know either fact at detection time.
            ce.setPersonalDataAffected(false);
            ce.setSupplierInvolved(false);
            CrisisEvent persisted = crisisEvents.save(ce);

            row.setCrisisEventId(persisted.getId());
            row.setProcessingStatus("ESCALATED");
            row.setProcessedAt(LocalDateTime.now());

            eventPublisher.publishEvent(new DomainEvent(
                o, "BCM_SIEM_ESCALATED", "SiemInboundEvent", null, null,
                Map.of("siemSource", row.getSourceSystem(),
                       "severity", sev,
                       "crisisEventId", persisted.getId(),
                       "crisisEventCode", persisted.getEventCode())));
        } else {
            row.setProcessingStatus("IGNORED");
            row.setProcessedAt(LocalDateTime.now());
        }

        SiemInboundEvent saved = siemEvents.save(row);
        audit.log("INGEST", "SiemInboundEvent", saved.getId(),
            row.getSourceSystem() + " sev=" + sev + " → " + saved.getProcessingStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SiemInboundEvent> listSiemEvents(String status) {
        if (status != null && !status.isBlank())
            return siemEvents.findByOrganisationIdAndProcessingStatusOrderByReceivedAtDesc(org(), status.toUpperCase());
        return siemEvents.findByOrganisationIdOrderByReceivedAtDesc(org());
    }

    // ─── Tier-4 #24: SAMA OPRES summary ──────────────────────────

    /**
     * Aggregate OPRES posture across BIAs flagged as critical operations.
     * For each tier (SOVEREIGN, NATIONAL, SECTORAL, NORMAL) returns the
     * count of BIAs and the smallest impact-tolerance window (which is the
     * binding ceiling per SAMA Article 9).
     */
    @Transactional(readOnly = true)
    public OpresSummary opresSummary() {
        Long o = org();
        List<BcmBiaAssessment> all = bias.findByOrganisationIdOrderByIdDesc(
                o, PageRequest.of(0, 10_000)).getContent();

        long critOps  = all.stream().filter(b -> Boolean.TRUE.equals(b.getOpresCriticalOperationFlag())).count();

        // Per-tier: { tier -> {count, minTolerance, maxTolerance} }
        Map<String, TierStats> byTier = new LinkedHashMap<>();
        for (String t : List.of("SOVEREIGN", "NATIONAL", "SECTORAL", "NORMAL")) {
            byTier.put(t, new TierStats(0, null, null));
        }
        for (BcmBiaAssessment b : all) {
            String tier = b.getNationalCriticalTier() == null ? "NORMAL" : b.getNationalCriticalTier();
            TierStats s = byTier.computeIfAbsent(tier, k -> new TierStats(0, null, null));
            Integer tol = b.getImpactToleranceHours();
            int newMin = s.minToleranceHours == null
                ? (tol == null ? Integer.MAX_VALUE : tol)
                : (tol == null ? s.minToleranceHours : Math.min(s.minToleranceHours, tol));
            int newMax = s.maxToleranceHours == null
                ? (tol == null ? 0 : tol)
                : (tol == null ? s.maxToleranceHours : Math.max(s.maxToleranceHours, tol));
            byTier.put(tier, new TierStats(
                s.count + 1,
                newMin == Integer.MAX_VALUE ? null : newMin,
                newMax == 0 ? null : newMax));
        }

        // Posture: any BIA with rto_hours > impact_tolerance_hours is a breach
        long breaches = all.stream()
            .filter(b -> b.getImpactToleranceHours() != null && b.getRtoHours() != null)
            .filter(b -> b.getRtoHours() > b.getImpactToleranceHours())
            .count();

        return new OpresSummary(all.size(), critOps, breaches, byTier);
    }

    public record TierStats(long count, Integer minToleranceHours, Integer maxToleranceHours) {}
    public record OpresSummary(
        long totalBias,
        long criticalOperationCount,
        long rtoExceedsToleranceCount,
        Map<String, TierStats> tierBreakdown
    ) {}

    // ─── Tier-4 #29: PDPL Article 23 notification draft ─────────

    /**
     * Stamp a crisis event with a PDPL Art. 23 notification draft. Caller
     * passes the regulator reference number once filed. If
     * {@code personalDataAffected} is false on the event, the call refuses
     * — PDPL only applies when there's a personal-data breach.
     */
    @Transactional
    public CrisisEvent draftPdplNotification(Long crisisEventId, String reference) {
        Long o = org();
        CrisisEvent e = crisisEvents.findByIdAndOrganisationId(crisisEventId, o)
            .orElseThrow(() -> new EntityNotFoundException("Crisis event not found: " + crisisEventId));
        if (!Boolean.TRUE.equals(e.getPersonalDataAffected())) {
            throw new IllegalArgumentException(
                "PDPL Art. 23 only applies when personalDataAffected=true on the crisis event.");
        }
        e.setPdplNotificationDraftedAt(LocalDateTime.now());
        if (reference != null && !reference.isBlank()) {
            e.setPdplNotificationReference(reference.trim());
        }
        CrisisEvent saved = crisisEvents.save(e);
        audit.log("PDPL_DRAFTED", "CrisisEvent", saved.getId(),
            "PDPL Art. 23 notification drafted for " + saved.getEventCode()
                + (reference == null ? "" : " (ref " + reference + ")"));
        eventPublisher.publishEvent(new DomainEvent(
            o, "BCM_PDPL_NOTIFICATION_DRAFTED", "CrisisEvent", saved.getId(), null,
            Map.of("eventCode", saved.getEventCode())));
        return saved;
    }
}
