package com.tss.grc.module.capa.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.util.ExternalIds;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CAPA service. State machine:
 *   OPEN                  → IN_PROGRESS, CANCELLED
 *   IN_PROGRESS           → AWAITING_VERIFICATION, CANCELLED
 *   AWAITING_VERIFICATION → CLOSED, IN_REMEDIATION (failed verify reopens), CANCELLED
 *   CLOSED, CANCELLED → terminal
 *
 * <p>Note: failed-verify path uses IN_PROGRESS (not a separate IN_REMEDIATION state)
 * to keep the state set tight. Verifier records the rejection in progress_notes.
 *
 * <p>Every mutation writes to {@code platform.audit_log} via {@link AuditLogService}
 * so the reusable {@code EntityActivityTimeline} card (PRs #29-30) lights up on any
 * CAPA detail page automatically — the same forensic story buyers see for
 * workflow submissions, AAR, Risk, and Finding.
 */
@Service
@RequiredArgsConstructor
public class CapaTaskService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "OPEN",                  Set.of("IN_PROGRESS", "CANCELLED"),
        "IN_PROGRESS",           Set.of("AWAITING_VERIFICATION", "CANCELLED"),
        "AWAITING_VERIFICATION", Set.of("CLOSED", "IN_PROGRESS", "CANCELLED")
    );

    private static final List<String> TERMINAL = List.of("CLOSED", "CANCELLED");

    private final CapaTaskRepository       repo;
    private final AuditLogService          audit;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<CapaTaskDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId).stream()
            .map(CapaTaskDto::from).collect(Collectors.toList());
    }

    public List<CapaTaskDto> listForOrigin(String originType, String originId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndOriginTypeAndOriginId(orgId, originType, originId).stream()
            .map(CapaTaskDto::from).collect(Collectors.toList());
    }

    public List<CapaTaskDto> overdue() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndStatusNotInAndTargetDateBefore(orgId, TERMINAL, LocalDate.now()).stream()
            .map(CapaTaskDto::from).collect(Collectors.toList());
    }

    /** Per-tenant CAPA inbox counters used by the sidebar badge.
     *
     *  <p>{@code open} excludes terminal statuses (CLOSED + CANCELLED).
     *  {@code overdue} is the subset of open whose target date is in the
     *  past — exposed alongside open so the sidebar tooltip can render
     *  "12 open · 3 overdue" without a second round-trip.
     *
     *  <p>Two cheap COUNTs, no row payload. Mirrors the aggregate pattern
     *  shipped in {@code WorkflowSubmissionService#countsByWorkflowId}. */
    public CapaCounts counts() {
        Long orgId = TenantContext.requireOrganisationId();
        long open    = repo.countByOrganisationIdAndStatusNotIn(orgId, TERMINAL);
        long overdue = repo.countByOrganisationIdAndStatusNotInAndTargetDateBefore(
                          orgId, TERMINAL, LocalDate.now());
        return new CapaCounts(open, overdue);
    }

    public record CapaCounts(long open, long overdue) {}

    public CapaTaskDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(CapaTaskDto::from)
            .orElseThrow(() -> new EntityNotFoundException("CAPA task not found: " + externalId));
    }

    @Transactional
    public CapaTaskDto create(CapaTaskDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        CapaTask t = new CapaTask();
        t.setExternalId(req.getId() != null ? req.getId() : generateExternalId());
        t.setOrganisationId(orgId);
        t.setOriginType(req.getOriginType());
        t.setOriginId(req.getOriginId());
        t.setTitleEn(req.getTitleEn());
        t.setTitleAr(req.getTitleAr());
        t.setDescriptionEn(req.getDescriptionEn());
        t.setDescriptionAr(req.getDescriptionAr());
        t.setCapaType(req.getCapaType());
        t.setSeverity(req.getSeverity());
        t.setOwner(req.getOwner());
        t.setTargetDate(req.getTargetDate());
        t.setStatus("OPEN");
        CapaTask saved = repo.save(t);
        CapaTaskDto out = CapaTaskDto.from(saved);
        // Forensic trail — every CAPA mutation lands in platform.audit_log
        // so the reusable EntityActivityTimeline card lights up on any CAPA
        // detail page (pattern from PRs #29-30, mirrors FindingService).
        audit.log("CREATE", "CapaTask", saved.getId(),
            "CAPA " + saved.getExternalId() + " opened"
            + (saved.getOriginType() != null
                ? " from " + saved.getOriginType() + " " + saved.getOriginId() : "")
            + " · owner=" + (saved.getOwner() != null ? saved.getOwner() : "—")
            + " · severity=" + (saved.getSeverity() != null ? saved.getSeverity() : "—")
            + " · target=" + (saved.getTargetDate() != null ? saved.getTargetDate() : "—"));
        // Fan out to F8 — engine listens for CAPA_TASK_CREATED and matches
        // enabled rules against the payload (originType, originId,
        // severity, capaType). Seed rules can route a high-severity
        // CAPA on an NCA finding to the CISO, a low-severity privacy
        // CAPA to the DPO, etc. Same shape as WorkflowSubmissionService
        // PR #21 — no schema or engine change required.
        eventPublisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "CAPA_TASK_CREATED",
            "CapaTask", saved.getId(), saved.getExternalId(),
            payloadFor(saved, null)));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "capa", saved.getExternalId(), out);
        return out;
    }

    @Transactional
    public CapaTaskDto transition(String externalId, CapaTaskDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        CapaTask t = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("CAPA task not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(t.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(t.getStatus(), req.getStatus(), allowed);
        }
        String previousStatus = t.getStatus();
        LocalDateTime now = LocalDateTime.now();
        t.setStatus(req.getStatus());

        if ("IN_PROGRESS".equals(req.getStatus()) && t.getStartedAt() == null) {
            t.setStartedAt(now);
        }
        if ("AWAITING_VERIFICATION".equals(req.getStatus())) {
            t.setSubmittedForVerificationAt(now);
        }
        if ("CLOSED".equals(req.getStatus())) {
            t.setVerifiedAt(now);
            // F2-22b(vi): the verifier is the authenticated principal, never the request's.
            // CAPA verification is the evidence that a remediation actually happened, so a
            // caller-supplied name is DF-3's defect. It was also empty in practice — no UI
            // sends this field, so a CLOSED CAPA recorded a verification time and nobody.
            t.setVerifiedBy(verifierOrFail(t.getExternalId()));
            t.setClosedAt(now);
        }
        if ("CANCELLED".equals(req.getStatus())) {
            t.setCancelledAt(now);
            if (req.getCancellationReasonEn() != null) t.setCancellationReasonEn(req.getCancellationReasonEn());
        }
        if (req.getProgressNotesEn() != null) t.setProgressNotesEn(req.getProgressNotesEn());
        if (req.getProgressNotesAr() != null) t.setProgressNotesAr(req.getProgressNotesAr());
        if (req.getEvidenceRefs() != null)    t.setEvidenceRefs(req.getEvidenceRefs());

        CapaTask saved = repo.save(t);
        CapaTaskDto out = CapaTaskDto.from(saved);
        // Status transition lands as its own audit row — actionType is the
        // target state so the timeline card colours it correctly (CLOSED
        // → success badge, CANCELLED → neutral, etc.). The verifier
        // (when CLOSED) is captured in the message for forensic traceability.
        audit.log(saved.getStatus(), "CapaTask", saved.getId(),
            "CAPA " + saved.getExternalId()
            + " " + previousStatus + " → " + saved.getStatus()
            // F2-22b(vi): read the verifier off the SAVED entity, not the request — the audit
            // row must name who actually verified, which is now the authenticated principal.
            + (saved.getVerifiedBy() != null && "CLOSED".equals(saved.getStatus())
                ? " · verified by " + saved.getVerifiedBy() : "")
            + (req.getCancellationReasonEn() != null && "CANCELLED".equals(saved.getStatus())
                ? " · reason: " + req.getCancellationReasonEn() : "")
            + (req.getProgressNotesEn() != null && !req.getProgressNotesEn().isBlank()
                ? " · note: " + req.getProgressNotesEn() : ""));
        // Fan out to F8 — separate event from CREATED so rules can target
        // status transitions specifically. The payload carries
        // previousStatus alongside status so a rule can match "closed
        // an NCA-related CAPA" (status=CLOSED + originType=audit.finding).
        eventPublisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "CAPA_TASK_REVIEWED",
            "CapaTask", saved.getId(), saved.getExternalId(),
            payloadFor(saved, previousStatus)));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "capa", externalId, out);
        return out;
    }

    /** Canonical payload Map for F8 rule-condition matching on CAPA events.
     *  Engine evaluates equality + contains on these string keys; values
     *  are coerced to "" so the matcher never NPEs on a null. */
    private static Map<String, Object> payloadFor(CapaTask t, String previousStatus) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("externalId",  nullSafe(t.getExternalId()));
        m.put("originType",  nullSafe(t.getOriginType()));
        m.put("originId",    nullSafe(t.getOriginId()));
        m.put("status",      nullSafe(t.getStatus()));
        m.put("severity",    nullSafe(t.getSeverity()));
        m.put("capaType",    nullSafe(t.getCapaType()));
        m.put("owner",       nullSafe(t.getOwner()));
        if (previousStatus != null) m.put("previousStatus", previousStatus);
        return m;
    }

    private static String nullSafe(String v) { return v == null ? "" : v; }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        CapaTask t = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("CAPA task not found: " + externalId));
        t.setIsDeleted((short) 1);
        repo.save(t);
        // PLATFORM_ADMIN-only delete (see controller) — still audit it so a
        // reviewer who later finds the CAPA missing can trace the actor.
        audit.log("DELETE", "CapaTask", t.getId(),
            "CAPA " + t.getExternalId() + " soft-deleted (status=" + t.getStatus() + ")");
    }

    /**
     * F2-22b(vi) — who verified a CAPA closure, from the security context.
     *
     * <p>Fails rather than writing an anonymous verification: a CLOSED CAPA whose verifier is
     * unknown is the same as an unverified one, and this transition is reachable only from an
     * authenticated request.
     */
    private String verifierOrFail(String externalId) {
        String actor = CurrentActor.username();
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException(
                "No authenticated principal to record as the verifier of CAPA " + externalId
                + ". A closure with no verifier is not a verification.");
        }
        return actor;
    }

    /**
     * F2-48 — this generator collided with itself, so a tenant could open ONE CAPA every ~17½
     * minutes and every other attempt failed.
     *
     * <p>It was {@code Long.toHexString(currentTimeMillis()).substring(0, 6)}. The epoch in hex is
     * 11 digits, so taking the first six keeps the <b>high-order</b> digits and throws away the low
     * five: the value only changes when bit 20 rolls over, i.e. once per 2²⁰ ms ≈ 17.5 minutes.
     * Every CAPA created inside one window got the identical {@code external_id} and violated
     * {@code uk_capa_external (organisation_id, external_id)}.
     *
     * <p>The symptom did not look like an id problem. The insert fails after {@code repo.save},
     * so callers saw "an unexpected database error" (500), and any caller that had joined its
     * transaction to a {@code @Transactional} auto-CAPA hook got a 503 "Transaction rolled back"
     * on a completely unrelated operation. Measured live: six consecutive scores of a failing
     * processor assessment returned 200, 503, 503, 503, 503, 503 — the first in the window worked.
     *
     * <p>Why it stayed hidden: all 16 seeded rows carry hand-written {@code CAPA-GOV-2026-NNN}
     * ids, so the generator was never exercised twice in a window by anything but a real user, and
     * every auto-CAPA hook swallows its exception and logs a warning. So each module's automation
     * appeared wired while creating nothing.
     *
     * <p>Now: full millisecond precision (nothing truncated) plus 4 random hex digits, so two
     * CAPAs raised in the same millisecond by different threads still differ.
     */
    private String generateExternalId() {
        return ExternalIds.generate("CAPA");
    }
}
