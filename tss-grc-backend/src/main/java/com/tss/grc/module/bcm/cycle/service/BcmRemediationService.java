package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmCorrectiveAction;
import com.tss.grc.module.bcm.cycle.entity.BcmGap;
import com.tss.grc.module.bcm.cycle.entity.BcmLessonLearned;
import com.tss.grc.module.bcm.cycle.repository.BcmCorrectiveActionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmGapRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmLessonLearnedRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BCM remediation service — Sprint BCM-3.
 *
 * <p>Owns three closely-related resources: lessons learned, gaps,
 * corrective actions. Closure flows between them (closing a CAPA
 * can close the linked gap; an approved lesson learned can spawn a
 * corrective action).
 *
 * <p>Publishes {@code BCM_CAPA_OVERDUE} when a CAPA passes its
 * due_date so the F8 engine fires the BCM-T4 escalation.
 */
@Service
@RequiredArgsConstructor
public class BcmRemediationService {

    // ── Lesson statuses ──
    private static final Set<String> LESSON_STATUSES = Set.of(
        "DRAFT", "UNDER_REVIEW", "APPROVED", "REJECTED", "CLOSED");
    private static final Set<String> LESSON_SOURCES = Set.of(
        "INCIDENT", "CRISIS", "EXERCISE", "AUDIT", "MANUAL");
    // ── Gap statuses ──
    private static final Set<String> GAP_STATUSES = Set.of(
        "OPEN", "ASSIGNED", "IN_PROGRESS", "UNDER_REVIEW", "CLOSED", "ACCEPTED_RISK", "REOPENED");
    /** F2-220 — the state a gap is CREATED in; every other GAP_STATUS is transitionGap()'s. */
    private static final String INITIAL_GAP_STATUS = "OPEN";
    private static final Set<String> GAP_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> GAP_SOURCES = Set.of(
        "BIA", "BCP", "DRP", "EXERCISE", "CALL_TREE", "AUDIT", "RISK", "MANUAL", "EVIDENCE");
    // ── CAPA statuses ──
    private static final Set<String> CAPA_STATUSES = Set.of(
        "OPEN", "ASSIGNED", "IN_PROGRESS", "EVIDENCE_SUBMITTED", "UNDER_REVIEW",
        "CLOSED", "OVERDUE", "REOPENED");
    /**
     * F2-234 — the state a CAPA is CREATED in; every other CAPA_STATUS is transitionCapa()'s.
     *
     * <p>🔑 <b>F2-220 fixed {@link #createGap} in THIS FILE, with this primitive, under a comment
     * naming this exact defect — and left the identical two lines in {@link #createCapa} eighty
     * lines below.</b> Not a sibling file and not a sibling module: the same file. F2-215's
     * headline at its sharpest — fixing the instance you noticed leaves the remainder looking
     * covered, because the mechanism that covers it is visibly present.
     */
    private static final String INITIAL_CAPA_STATUS = "OPEN";
    private static final Set<String> CAPA_PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    /**
     * F2-336 — the RESOLVED (terminal-disposition) states of each register, so that "open" is the
     * COMPLEMENT of a declared set and never a single inclusion token (F2-196). These are the ONE
     * authoritative definition; readers reference them rather than typing their own list (F2-282: a
     * concept read by several places must be read the same way).
     *
     * <p>{@code CAPA_RESOLVED = {CLOSED}} is the CAPA machine's only no-outgoing-edge state
     * ({@code case "CLOSED" -> false}); every other status — OPEN (the initial state), ASSIGNED,
     * IN_PROGRESS, EVIDENCE_SUBMITTED, UNDER_REVIEW, OVERDUE, REOPENED — is an active workflow step,
     * which is exactly the complement {@link BcmBoardPackService#capaSnapshot} already sums as
     * {@code activeOpen} (its overdue sweep uses {@code done = List.of("CLOSED")}).
     *
     * <p>{@code GAP_RESOLVED = {CLOSED, ACCEPTED_RISK}}: a gap is resolved either by remediation
     * (CLOSED) or by formally accepting the residual risk (ACCEPTED_RISK). {@code ACCEPTED_RISK} is a
     * re-openable disposition ({@code ACCEPTED_RISK -> OPEN}), not an active remediation step, and the
     * platform treats it as resolved everywhere else — {@code AttackSurfaceFindingService.CLOSING_STATUSES}
     * includes it, and the NDI gap sibling counts "critical open" as {@code not (RESOLVED|CLOSED|ACCEPTED_RISK)}.
     */
    public static final Set<String> CAPA_RESOLVED = Set.of("CLOSED");
    public static final Set<String> GAP_RESOLVED  = Set.of("CLOSED", "ACCEPTED_RISK");

    private final BcmLessonLearnedRepository lessons;
    private final BcmGapRepository gaps;
    private final BcmCorrectiveActionRepository capas;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ─── Lessons ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BcmLessonLearned> listLessons(String status) {
        Long o = org();
        return (status != null && !status.isBlank())
            ? lessons.findByOrganisationIdAndStatusOrderByIdDesc(o, status)
            : lessons.findByOrganisationIdOrderByIdDesc(o);
    }

    @Transactional(readOnly = true)
    public BcmLessonLearned getLesson(Long id) {
        return lessons.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Lesson learned not found: " + id));
    }

    @Transactional
    public BcmLessonLearned createLesson(BcmLessonLearned body) {
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getSourceType() == null || !LESSON_SOURCES.contains(body.getSourceType()))
            throw new IllegalArgumentException("sourceType must be one of " + LESSON_SOURCES);
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null) body.setStatus("DRAFT");
        if (!LESSON_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid status: " + body.getStatus());
        BcmLessonLearned saved = lessons.save(body);
        audit("BcmLessonLearned", saved.getId(), saved.getTitleEn(), "CREATE", null, saved.getStatus(),
            "Lesson learned created: " + saved.getTitleEn());
        return saved;
    }

    @Transactional
    public BcmLessonLearned transitionLesson(Long id, String to, String actor) {
        BcmLessonLearned l = getLesson(id);
        if (!LESSON_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = l.getStatus();
        boolean ok = switch (from) {
            case "DRAFT"        -> "UNDER_REVIEW".equals(to);
            case "UNDER_REVIEW" -> "APPROVED".equals(to) || "REJECTED".equals(to);
            case "APPROVED"     -> "CLOSED".equals(to);
            case "REJECTED"     -> "DRAFT".equals(to);
            default             -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        l.setStatus(to);
        if ("APPROVED".equals(to)) {
            l.setApprovedBy(actor);
            l.setApprovedAt(LocalDateTime.now());
        }
        l.setUpdatedAt(LocalDateTime.now());
        BcmLessonLearned saved = lessons.save(l);
        audit("BcmLessonLearned", saved.getId(), saved.getTitleEn(), "TRANSITION", from, to,
            "Lesson " + saved.getId() + " " + from + " → " + to);
        return saved;
    }

    // ─── Gaps ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BcmGap> listGaps(String status, String severity, Long cycleId) {
        Long o = org();
        if (cycleId != null) return gaps.findByOrganisationIdAndCycleIdOrderByIdDesc(o, cycleId);
        if (severity != null && !severity.isBlank())
            return gaps.findByOrganisationIdAndSeverityOrderByIdDesc(o, severity);
        if (status != null && !status.isBlank())
            return gaps.findByOrganisationIdAndStatusOrderByIdDesc(o, status);
        return gaps.findByOrganisationIdOrderByIdDesc(o);
    }

    @Transactional(readOnly = true)
    public BcmGap getGap(Long id) {
        return gaps.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Gap not found: " + id));
    }

    @Transactional
    public BcmGap createGap(BcmGap body) {
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getSource() == null || !GAP_SOURCES.contains(body.getSource()))
            throw new IllegalArgumentException("source must be one of " + GAP_SOURCES);
        if (body.getSeverity() == null) body.setSeverity("MEDIUM");
        if (!GAP_SEVERITIES.contains(body.getSeverity()))
            throw new IllegalArgumentException("Invalid severity: " + body.getSeverity());
        body.setId(null);
        body.setOrganisationId(org());
        // F2-220 — a DEFAULT, not a guard: the membership test below it admitted CLOSED, so a
        // create could close a gap. transitionGap() refuses to CLOSE a HIGH or CRITICAL gap
        // without at least one linked corrective action; this route asked for none.
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), INITIAL_GAP_STATUS, "a BCM gap",
            "POST /api/v1/bcm/gaps/{id}/transition"));
        BcmGap saved = gaps.save(body);
        audit("BcmGap", saved.getId(), saved.getTitleEn(), "CREATE", null, saved.getStatus(),
            "Gap created: " + saved.getTitleEn());
        return saved;
    }

    @Transactional
    public BcmGap transitionGap(Long id, String to, String actor) {
        BcmGap g = getGap(id);
        if (!GAP_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = g.getStatus();
        boolean ok = switch (from) {
            case "OPEN"            -> "ASSIGNED".equals(to) || "ACCEPTED_RISK".equals(to);
            case "ASSIGNED"        -> "IN_PROGRESS".equals(to) || "OPEN".equals(to);
            case "IN_PROGRESS"     -> "UNDER_REVIEW".equals(to) || "ASSIGNED".equals(to);
            case "UNDER_REVIEW"    -> "CLOSED".equals(to) || "IN_PROGRESS".equals(to);
            // CLOSED is terminal via the generic (BCM_WRITE) path — reopening
            // a closed gap is a privileged action, only via reopenGap() below.
            case "CLOSED"          -> false;
            case "REOPENED"        -> "IN_PROGRESS".equals(to);
            case "ACCEPTED_RISK"   -> "OPEN".equals(to);
            default                -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        if ("CLOSED".equals(to) && ("HIGH".equals(g.getSeverity()) || "CRITICAL".equals(g.getSeverity()))) {
            // High/critical gaps must have at least one CAPA linked.
            long capaCount = capas.findByOrganisationIdAndGapIdOrderByIdDesc(org(), g.getId()).size();
            if (capaCount == 0)
                throw new IllegalArgumentException(
                    "High/critical gap cannot be CLOSED without at least one corrective action");
        }
        g.setStatus(to);
        g.setUpdatedAt(LocalDateTime.now());
        BcmGap saved = gaps.save(g);
        audit("BcmGap", saved.getId(), saved.getTitleEn(), "TRANSITION", from, to,
            "Gap " + saved.getId() + " " + from + " → " + to
            + (actor == null || actor.isBlank() ? "" : " by " + actor));
        return saved;
    }

    /**
     * Reopen a CLOSED gap. Gated at the controller by {@code BCM_APPROVE};
     * a justification reason is mandatory and recorded in the audit trail.
     */
    @Transactional
    public BcmGap reopenGap(Long id, String reason, String approver) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A reopen reason is required");
        BcmGap g = getGap(id);
        if (!"CLOSED".equals(g.getStatus()))
            throw new IllegalStateException(
                "Only a CLOSED gap can be reopened (current: " + g.getStatus() + ")");
        String from = g.getStatus();
        g.setStatus("REOPENED");
        g.setUpdatedAt(LocalDateTime.now());
        BcmGap saved = gaps.save(g);
        audit("BcmGap", saved.getId(), saved.getTitleEn(), "REOPEN", from, "REOPENED",
            "Gap " + saved.getId() + " reopened with approval by "
            + (approver == null || approver.isBlank() ? "?" : approver) + " — reason: " + reason);
        return saved;
    }

    // ─── Corrective actions ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BcmCorrectiveAction> listCapas(String status, Long gapId) {
        Long o = org();
        if (gapId != null) return capas.findByOrganisationIdAndGapIdOrderByIdDesc(o, gapId);
        if (status != null && !status.isBlank())
            return capas.findByOrganisationIdAndStatusOrderByIdDesc(o, status);
        return capas.findByOrganisationIdOrderByIdDesc(o);
    }

    @Transactional(readOnly = true)
    public BcmCorrectiveAction getCapa(Long id) {
        return capas.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Corrective action not found: " + id));
    }

    @Transactional
    public BcmCorrectiveAction createCapa(BcmCorrectiveAction body) {
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getPriority() == null) body.setPriority("MEDIUM");
        if (!CAPA_PRIORITIES.contains(body.getPriority()))
            throw new IllegalArgumentException("Invalid priority: " + body.getPriority());
        if (body.getProgressPercent() == null) body.setProgressPercent(0);
        if (body.getProgressPercent() < 0 || body.getProgressPercent() > 100)
            throw new IllegalArgumentException("progressPercent must be 0..100");
        body.setId(null);
        body.setOrganisationId(org());
        // F2-234 — a DEFAULT, not a guard, followed by a MEMBERSHIP test that admitted CLOSED.
        // transitionCapa() refuses to CLOSE without evidence ("Cannot CLOSE without evidence")
        // and walks OPEN → ASSIGNED → IN_PROGRESS → EVIDENCE_SUBMITTED → UNDER_REVIEW → CLOSED;
        // a create naming the state skipped the ladder AND the evidence requirement. It also
        // produced a row nothing could move: updateCapa() refuses to edit a CLOSED CAPA and
        // transitionCapa() has `case "CLOSED" -> false`, so only BCM:APPROVE reopenCapa() could.
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), INITIAL_CAPA_STATUS, "a BCM corrective action",
            "POST /api/v1/bcm/capas/{id}/transition"));
        // closureDate is transitionCapa()'s stamp on CLOSE and reopenCapa() clears it; a CAPA
        // that has never been closed cannot carry one. progressPercent is deliberately NOT
        // cleared — it is the owner's own progress report, validated 0..100 and defaulted to 0,
        // and refusing it would be a gate failing on correct code (F2-214).
        body.setClosureDate(null);
        BcmCorrectiveAction saved = capas.save(body);
        audit("BcmCorrectiveAction", saved.getId(), saved.getTitleEn(), "CREATE", null, saved.getStatus(),
            "CAPA created: " + saved.getTitleEn());
        return saved;
    }

    @Transactional
    public BcmCorrectiveAction updateCapa(Long id, BcmCorrectiveAction body) {
        BcmCorrectiveAction a = getCapa(id);
        if ("CLOSED".equals(a.getStatus()))
            throw new IllegalStateException("Cannot edit a CLOSED corrective action");
        if (body.getTitleEn() != null)      a.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)      a.setTitleAr(body.getTitleAr());
        if (body.getActionPlan() != null)   a.setActionPlan(body.getActionPlan());
        if (body.getOwnerId() != null)      a.setOwnerId(body.getOwnerId());
        if (body.getPriority() != null) {
            if (!CAPA_PRIORITIES.contains(body.getPriority()))
                throw new IllegalArgumentException("Invalid priority");
            a.setPriority(body.getPriority());
        }
        if (body.getDueDate() != null)      a.setDueDate(body.getDueDate());
        if (body.getProgressPercent() != null) {
            if (body.getProgressPercent() < 0 || body.getProgressPercent() > 100)
                throw new IllegalArgumentException("progressPercent must be 0..100");
            a.setProgressPercent(body.getProgressPercent());
        }
        if (body.getEvidenceId() != null)   a.setEvidenceId(body.getEvidenceId());
        if (body.getReviewerId() != null)   a.setReviewerId(body.getReviewerId());
        a.setUpdatedAt(LocalDateTime.now());
        return capas.save(a);
    }

    @Transactional
    public BcmCorrectiveAction transitionCapa(Long id, String to, String actor) {
        BcmCorrectiveAction a = getCapa(id);
        if (!CAPA_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = a.getStatus();
        boolean ok = switch (from) {
            case "OPEN"               -> "ASSIGNED".equals(to);
            case "ASSIGNED"           -> "IN_PROGRESS".equals(to);
            case "IN_PROGRESS"        -> "EVIDENCE_SUBMITTED".equals(to);
            case "EVIDENCE_SUBMITTED" -> "UNDER_REVIEW".equals(to);
            case "UNDER_REVIEW"       -> "CLOSED".equals(to) || "IN_PROGRESS".equals(to);
            // CLOSED is terminal via the generic (BCM_WRITE) path — reopening
            // a closed CAPA is a privileged action, only via reopenCapa() below.
            case "CLOSED"             -> false;
            case "REOPENED"           -> "IN_PROGRESS".equals(to);
            case "OVERDUE"            -> "IN_PROGRESS".equals(to) || "EVIDENCE_SUBMITTED".equals(to);
            default                   -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        if ("CLOSED".equals(to) && a.getEvidenceId() == null)
            throw new IllegalArgumentException("Cannot CLOSE without evidence — attach evidence first");
        a.setStatus(to);
        if ("CLOSED".equals(to)) {
            a.setClosureDate(LocalDate.now());
            a.setProgressPercent(100);
        }
        a.setUpdatedAt(LocalDateTime.now());
        BcmCorrectiveAction saved = capas.save(a);
        audit("BcmCorrectiveAction", saved.getId(), saved.getTitleEn(), "TRANSITION", from, to,
            "CAPA " + saved.getId() + " " + from + " → " + to
            + (actor == null || actor.isBlank() ? "" : " by " + actor));
        return saved;
    }

    /**
     * Reopen a CLOSED corrective action. Gated at the controller by
     * {@code BCM_APPROVE}; a justification reason is mandatory and recorded
     * in the audit trail. Clears the closure date so the CAPA is live again.
     */
    @Transactional
    public BcmCorrectiveAction reopenCapa(Long id, String reason, String approver) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A reopen reason is required");
        BcmCorrectiveAction a = getCapa(id);
        if (!"CLOSED".equals(a.getStatus()))
            throw new IllegalStateException(
                "Only a CLOSED corrective action can be reopened (current: " + a.getStatus() + ")");
        a.setStatus("REOPENED");
        a.setClosureDate(null);
        a.setUpdatedAt(LocalDateTime.now());
        BcmCorrectiveAction saved = capas.save(a);
        audit("BcmCorrectiveAction", saved.getId(), saved.getTitleEn(), "REOPEN", "CLOSED", "REOPENED",
            "CAPA " + saved.getId() + " reopened with approval by "
            + (approver == null || approver.isBlank() ? "?" : approver) + " — reason: " + reason);
        return saved;
    }

    /** Scheduled-job entry point: flip overdue OPEN/IN_PROGRESS CAPAs to OVERDUE and fire BCM-T4. */
    @Transactional
    public int flagOverdueCapas() {
        Long o = org();
        List<BcmCorrectiveAction> overdue = capas.findByOrganisationIdAndDueDateBeforeAndStatusNotIn(
            o, LocalDate.now(), List.of("CLOSED", "REOPENED", "OVERDUE"));
        int flipped = 0;
        for (BcmCorrectiveAction a : overdue) {
            String from = a.getStatus();
            a.setStatus("OVERDUE");
            a.setUpdatedAt(LocalDateTime.now());
            capas.save(a);
            audit("BcmCorrectiveAction", a.getId(), a.getTitleEn(), "TRANSITION", from, "OVERDUE",
                "CAPA " + a.getId() + " flipped to OVERDUE by scheduler");
            events.publishEvent(DomainEvent.of(
                a.getOrganisationId(),
                "BCM_CAPA_OVERDUE",
                "BcmCorrectiveAction",
                a.getId(),
                Map.of(
                    "priority", a.getPriority(),
                    "owner_id", a.getOwnerId() == null ? -1 : a.getOwnerId()
                )));
            flipped++;
        }
        return flipped;
    }

    /** Aggregate counters for the BCM dashboard. */
    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long o = org();
        return Map.of(
            "openGaps",         gaps.countByOrganisationIdAndStatusNotIn(o, GAP_RESOLVED),
            "criticalGaps",     gaps.countByOrganisationIdAndSeverity(o, "CRITICAL"),
            "openCapas",        capas.countByOrganisationIdAndStatusNotIn(o, CAPA_RESOLVED),
            "overdueCapas",     capas.countByOrganisationIdAndStatus(o, "OVERDUE"),
            "closedCapas",      capas.countByOrganisationIdAndStatus(o, "CLOSED")
        );
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private void audit(String entityType, Long id, String code, String action,
                       String before, String after, String message) {
        this.audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType(action)
            .entityType(entityType)
            .entityId(id)
            .entityCode(code)
            .workflowStatusBefore(before)
            .workflowStatusAfter(after)
            .sourceModule("bcm")
            .message(message));
    }

}
