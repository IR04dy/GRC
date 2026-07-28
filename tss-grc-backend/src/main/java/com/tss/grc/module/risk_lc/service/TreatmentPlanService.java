package com.tss.grc.module.risk_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk_lc.dto.TreatmentPlanDto;
import com.tss.grc.module.risk_lc.entity.TreatmentPlan;
import com.tss.grc.module.risk_lc.repository.TreatmentPlanRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.common.security.CurrentActor;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Treatment plan service — guards the state machine + computes derived fields.
 *
 * Allowed transitions (mirrors the UI client logic so they stay in sync):
 *   DRAFT       → SUBMITTED
 *   SUBMITTED   → APPROVED | REJECTED
 *   APPROVED    → IN_PROGRESS
 *   IN_PROGRESS → COMPLETED
 *   REJECTED    → DRAFT
 *
 * <p>Wired into F7 (audit) + F8 (automation). Treatment plans are the
 * commitment side of the risk register — the moment a plan is APPROVED
 * the residual-risk number on the parent risk becomes a board-level
 * commitment. Events:
 *
 *   TREATMENT_DRAFTED   — new plan opened
 *   TREATMENT_SUBMITTED — DRAFT → SUBMITTED
 *   TREATMENT_APPROVED  — SUBMITTED → APPROVED (board commitment)
 *   TREATMENT_REJECTED  — SUBMITTED → REJECTED (rework path)
 *   TREATMENT_STARTED   — APPROVED → IN_PROGRESS
 *   TREATMENT_COMPLETED — IN_PROGRESS → COMPLETED (residual realised)
 *   TREATMENT_REOPENED  — REJECTED → DRAFT (rework loop)
 */
@Service
@RequiredArgsConstructor
public class TreatmentPlanService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "DRAFT",       Set.of("SUBMITTED"),
        "SUBMITTED",   Set.of("APPROVED", "REJECTED"),
        "APPROVED",    Set.of("IN_PROGRESS"),
        "IN_PROGRESS", Set.of("COMPLETED"),
        "REJECTED",    Set.of("DRAFT")
    );

    private final TreatmentPlanRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    /** Optional — null in unit tests, auto-wired by Spring at runtime. */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    // ── F8 payload + emit ─────────────────────────────────────────────────────

    private Map<String, Object> payloadFor(TreatmentPlan p, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("treatmentPlanId", nullSafeStr(p.getId()));
        m.put("externalId",      nullSafeStr(p.getExternalId()));
        m.put("riskId",          nullSafeStr(p.getRiskId()));
        m.put("strategy",        nullSafeStr(p.getStrategy()));
        m.put("status",          nullSafeStr(p.getStatus()));
        m.put("previousStatus",  nullSafeStr(previousStatus));
        m.put("costSarK",        nullSafeStr(p.getCostSarK()));
        m.put("approvedBy",      nullSafeStr(p.getApprovedBy()));
        return m;
    }

    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }
    private static String nullSafeMsg(Object o) { return o == null ? "<not provided>" : o.toString(); }

    private void emit(String trigger, TreatmentPlan p, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.requireOrganisationId(), trigger, "TreatmentPlan", p.getId(),
            "treatment:" + nullSafeStr(p.getExternalId()), payloadFor(p, previousStatus)));
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    public List<TreatmentPlanDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId).stream()
            .map(TreatmentPlanDto::from)
            .collect(Collectors.toList());
    }

    public List<TreatmentPlanDto> listForRisk(Long riskId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(orgId, riskId).stream()
            .map(TreatmentPlanDto::from)
            .collect(Collectors.toList());
    }

    public TreatmentPlanDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(TreatmentPlanDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Treatment plan not found: " + externalId));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public TreatmentPlanDto create(TreatmentPlanDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        TreatmentPlan p = new TreatmentPlan();
        p.setExternalId(req.getId() != null ? req.getId() : generateExternalId(orgId));
        p.setOrganisationId(orgId);
        p.setRiskId(req.getRiskId());
        p.setStrategy(req.getStrategy());
        p.setRationaleEn(req.getRationaleEn());
        p.setRationaleAr(req.getRationaleAr());
        p.setCostSarK(req.getCostSarK());
        p.setCostBenefitEn(req.getCostBenefitEn());
        p.setCostBenefitAr(req.getCostBenefitAr());
        p.setStatus("DRAFT");
        p = repo.save(p);
        audit.log("CREATE", "TreatmentPlan", p.getId(),
            "Treatment plan drafted: " + nullSafeMsg(p.getExternalId())
                + " on risk " + nullSafeMsg(p.getRiskId())
                + " (strategy: " + nullSafeMsg(p.getStrategy()) + ")");
        emit("TREATMENT_DRAFTED", p, null);
        return TreatmentPlanDto.from(p);
    }

    @Transactional
    public TreatmentPlanDto transition(String externalId, TreatmentPlanDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        TreatmentPlan p = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Treatment plan not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(p.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), req.getStatus(), allowed);
        }
        String previousStatus = p.getStatus();
        p.setStatus(req.getStatus());
        if ("SUBMITTED".equals(req.getStatus()))   p.setSubmittedAt(LocalDateTime.now());
        if ("APPROVED".equals(req.getStatus())) {
            // F2-22b(vi): the approver is the authenticated principal, not the request's.
            // This line was worse than forgeable — it assigned UNCONDITIONALLY, so when the SPA
            // omitted the field (it always does: riskLifecycle.transitionPlan passes `by` as
            // undefined from every caller) it wrote NULL over the approver. Measured before the
            // fix: the single APPROVED treatment plan in the dev database had no approver at all.
            p.setApprovedBy(actorOrFail(p.getExternalId()));
            p.setApprovedAt(LocalDateTime.now());
        }
        if ("REJECTED".equals(req.getStatus())) {
            p.setRejectedReason(req.getRejectedReason());
        }
        p = repo.save(p);
        audit.log("TRANSITION", "TreatmentPlan", p.getId(),
            "Treatment plan " + nullSafeMsg(p.getExternalId())
                + " status " + previousStatus + " → " + req.getStatus()
                // F2-22b(vi): the saved entity's approver (the authenticated principal), not
                // the request's — which was usually null, so the audit row said nothing.
                + ("APPROVED".equals(req.getStatus()) && p.getApprovedBy() != null
                    ? " by " + p.getApprovedBy() : "")
                + ("REJECTED".equals(req.getStatus()) && req.getRejectedReason() != null
                    ? ": " + req.getRejectedReason() : ""));
        // Per-state trigger so downstream rules can target specific lifecycle
        // moments (board commitment on APPROVED is the leverage point).
        String trigger = switch (req.getStatus()) {
            case "SUBMITTED"   -> "TREATMENT_SUBMITTED";
            case "APPROVED"    -> "TREATMENT_APPROVED";
            case "REJECTED"    -> "TREATMENT_REJECTED";
            case "IN_PROGRESS" -> "TREATMENT_STARTED";
            case "COMPLETED"   -> "TREATMENT_COMPLETED";
            case "DRAFT"       -> "TREATMENT_REOPENED";
            default            -> "TREATMENT_TRANSITIONED";
        };
        emit(trigger, p, previousStatus);
        TreatmentPlanDto out = TreatmentPlanDto.from(p);
        // Sprint 4 §2.7 — push state-machine transition to subscribed clients
        // (open detail pages, kanban watchers). No-op when broadcaster missing.
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "treatment_plan", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        TreatmentPlan p = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Treatment plan not found: " + externalId));
        p.setIsDeleted((short) 1);
        repo.save(p);
        audit.log("DELETE", "TreatmentPlan", p.getId(),
            "Treatment plan soft-deleted: " + nullSafeMsg(p.getExternalId()));
    }

    /** TP-2026-001 generator.
     *
     * <p>Collision-safe: derives the next sequence from the MAX existing suffix
     * for the org+year across ALL rows (incl. soft-deleted), not a count of live
     * rows. A count-based generator ({@code count(live)+1}) collides after any
     * soft-delete, because the {@code uk_tp_external} unique index still counts
     * the deleted row's external_id — which was surfacing as a 500 on create. */
    private String generateExternalId(Long orgId) {
        int year = LocalDateTime.now().getYear();
        Integer maxSeq = repo.findMaxExternalIdSeq(orgId, "TP-" + year + "-%");
        long next = (maxSeq == null ? 0 : maxSeq) + 1;
        return String.format("TP-%d-%03d", year, next);
    }

    /**
     * F2-22b(vi) — who approved a risk treatment plan, from the security context.
     *
     * <p>Refuses rather than writing null, which is what the previous unconditional
     * {@code setApprovedBy(req.getApprovedBy())} did on every SPA-driven approval.
     */
    private String actorOrFail(String externalId) {
        String actor = CurrentActor.username();
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException(
                "No authenticated principal to record as the approver of treatment plan "
                + externalId + ". Accepting a risk treatment without naming who accepted it"
                + " leaves the residual-risk decision unattributed.");
        }
        return actor;
    }
}
