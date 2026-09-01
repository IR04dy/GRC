package com.tss.grc.module.ndi.cycle.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle;
import com.tss.grc.module.ndi.cycle.repository.NdiAssessmentCycleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * NDI Assessment Cycle service (slice 1).
 *
 * <p>Owns the 13-state cycle lifecycle:
 * <pre>
 *   DRAFT → PLANNING → DATA_COLLECTION → ASSESSMENT_IN_PROGRESS
 *         → EVIDENCE_REVIEW → GAP_REMEDIATION
 *         → READY_FOR_DATA_OFFICE_REVIEW → UNDER_DATA_OFFICE_REVIEW
 *         → READY_FOR_EXECUTIVE_APPROVAL → APPROVED → SUBMITTED → CLOSED
 *   any → ARCHIVED (terminal)
 * </pre>
 *
 * <p>Rules enforced here (the rest of the 20 spec rules land on the
 * child-entity services in slices 3-12):
 * <ul>
 *   <li>One active (non-CLOSED, non-ARCHIVED) cycle per (org, year).
 *       Filtered-index in V66 couldn't enforce NOT IN, so it's a
 *       service-level check on {@link #create(NdiAssessmentCycle)}.</li>
 *   <li>{@code APPROVED} cycles cannot be transitioned back below
 *       APPROVED — once Data Office signs off, the work is locked.</li>
 *   <li>CLOSED is read-only; ARCHIVED is fully terminal.</li>
 * </ul>
 *
 * <p>Critical-gap-blocks-approval (Rule 10) lives in slice 8 — this
 * service exposes the {@code criticalGapCount} field that slice 8 will
 * keep up to date.
 */
@Service
@RequiredArgsConstructor
public class NdiAssessmentCycleService {

    /** Ordered for forward-only progression. */
    private static final List<String> FORWARD_FLOW = List.of(
        "DRAFT", "PLANNING", "DATA_COLLECTION", "ASSESSMENT_IN_PROGRESS",
        "EVIDENCE_REVIEW", "GAP_REMEDIATION",
        "READY_FOR_DATA_OFFICE_REVIEW", "UNDER_DATA_OFFICE_REVIEW",
        "READY_FOR_EXECUTIVE_APPROVAL", "APPROVED", "SUBMITTED", "CLOSED");

    private static final Set<String> ALL_STATUSES;
    static {
        Set<String> s = new HashSet<>(FORWARD_FLOW);
        s.add("ARCHIVED");
        ALL_STATUSES = Set.copyOf(s);
    }

    /** Statuses that bypass forward-only flow — can be set from any state. */
    private static final Set<String> JUMP_STATUSES = Set.of("ARCHIVED");

    /** Statuses that mean the cycle has effectively ended. The active-per-year
     *  uniqueness check ignores rows in these states. */
    private static final Set<String> END_STATUSES = Set.of("CLOSED", "ARCHIVED");

    private final NdiAssessmentCycleRepository repo;
    private final NdiAuditPublisher audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    /**
     * The state a cycle is created in — <b>derived</b> from the head of {@link #FORWARD_FLOW}, not
     * typed again. F2-196's rule: a second literal is a second thing to keep in step, and the whole
     * point of this constant is that the state the server files a cycle in and the state its
     * machine starts from cannot disagree. Reordering FORWARD_FLOW moves this with it.
     */
    private static final String INITIAL_STATUS = FORWARD_FLOW.get(0);

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<NdiAssessmentCycle> list(Pageable p) {
        return repo.findByOrganisationIdOrderByReportingYearDescIdDesc(org(), p);
    }
    public Page<NdiAssessmentCycle> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByReportingYearDescIdDesc(org(), status, p);
    }
    public NdiAssessmentCycle get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("NDI cycle " + id));
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public NdiAssessmentCycle create(NdiAssessmentCycle req) {
        req.setId(null);
        req.setOrganisationId(org());
        // F2-218: this was a DEFAULT, not a guard — `if (status == null) status = "DRAFT"` leaves
        // the caller's value in place whenever they send one, so a cycle could be created straight
        // into SUBMITTED or CLOSED, skipping transition()'s forward-flow check and the timestamps
        // it stamps. A default and a guard are indistinguishable to any scan that only asks whether
        // the setter is present, which is why this survived the earlier sweeps.
        req.setStatus(ServerOwnedState.requireInitial(
            req.getStatus(), INITIAL_STATUS, "an assessment cycle", "POST /{id}/transition"));
        // transition() stamps these three per target state; a cycle being created has reached none
        // of those states, so a supplied value can only be a claim about something that did not
        // happen. Cleared rather than refused: unlike `status` they carry no vocabulary, so there
        // is nothing for a 400 to name back to the caller.
        req.setApprovedAt(null);
        req.setSubmittedAt(null);
        req.setClosedAt(null);
        validate(req);
        guardOneActivePerYear(req.getReportingYear(), null);
        NdiAssessmentCycle saved = repo.save(req);
        audit.publish("CYCLE", saved.getId(), saved.getCycleCode(), saved.getId(),
            "CREATE", "Created cycle " + saved.getCycleNameEn() + " (" + saved.getReportingYear() + ")");
        return saved;
    }

    @Transactional
    public NdiAssessmentCycle update(Long id, NdiAssessmentCycle req) {
        NdiAssessmentCycle cur = get(id);
        if (END_STATUSES.contains(cur.getStatus())) {
            throw new IllegalStateException(
                "Cycle is " + cur.getStatus() + " and cannot be modified. Re-open via a follow-up cycle.");
        }
        if (req.getCycleNameEn() != null)            cur.setCycleNameEn(req.getCycleNameEn());
        if (req.getCycleNameAr() != null)            cur.setCycleNameAr(req.getCycleNameAr());
        if (req.getReportingYear() != null)          cur.setReportingYear(req.getReportingYear());
        if (req.getPeriodStart() != null)            cur.setPeriodStart(req.getPeriodStart());
        if (req.getPeriodEnd() != null)              cur.setPeriodEnd(req.getPeriodEnd());
        if (req.getScope() != null)                  cur.setScope(req.getScope());
        if (req.getEntityNameEn() != null)           cur.setEntityNameEn(req.getEntityNameEn());
        if (req.getEntityNameAr() != null)           cur.setEntityNameAr(req.getEntityNameAr());
        if (req.getDataOfficeOwnerName() != null)    cur.setDataOfficeOwnerName(req.getDataOfficeOwnerName());
        if (req.getDataOfficeOwnerEmail() != null)   cur.setDataOfficeOwnerEmail(req.getDataOfficeOwnerEmail());
        if (req.getAssessmentManagerName() != null)  cur.setAssessmentManagerName(req.getAssessmentManagerName());
        if (req.getAssessmentManagerEmail() != null) cur.setAssessmentManagerEmail(req.getAssessmentManagerEmail());
        if (req.getNotes() != null)                  cur.setNotes(req.getNotes());
        validate(cur);
        return repo.save(cur);
    }

    /**
     * Forward-only state transition. {@code ARCHIVED} is the one exception
     * — it can be applied from any non-archived state.
     */
    @Transactional
    public NdiAssessmentCycle transition(Long id, String to) {
        NdiAssessmentCycle cur = get(id);
        if (!ALL_STATUSES.contains(to))
            throw new IllegalArgumentException("Unknown NDI cycle status: " + to);

        String from = cur.getStatus();
        if ("ARCHIVED".equals(from))
            throw new IllegalTransitionException(from, to, Set.of());

        boolean ok = JUMP_STATUSES.contains(to)
            || isForwardOneStep(from, to);
        if (!ok) {
            // Compute the allowed next list for a clear error message.
            int idx = FORWARD_FLOW.indexOf(from);
            Set<String> allowed = new LinkedHashSet<>();
            if (idx >= 0 && idx + 1 < FORWARD_FLOW.size()) allowed.add(FORWARD_FLOW.get(idx + 1));
            allowed.add("ARCHIVED");
            throw new IllegalTransitionException(from, to, allowed);
        }

        cur.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        switch (to) {
            case "APPROVED"  -> cur.setApprovedAt(now);
            case "SUBMITTED" -> cur.setSubmittedAt(now);
            case "CLOSED"    -> cur.setClosedAt(now);
            default -> { /* no extra stamping */ }
        }
        NdiAssessmentCycle saved = repo.save(cur);
        audit.publishTransition("CYCLE", saved.getId(), saved.getCycleCode(), saved.getId(),
            from, to, "Cycle transitioned " + from + " → " + to);
        return saved;
    }

    /**
     * Clone a previous cycle as a new DRAFT. Copies the org-level
     * configuration (name/scope/entity/owners) and clears the scores +
     * dates + status. Per the spec, the clone resets evidence and score
     * — child rows (assessments / evidence) are NOT copied; those slices
     * will provide their own clone behaviour where it makes sense.
     */
    @Transactional
    public NdiAssessmentCycle clone(Long sourceId, Short newYear) {
        NdiAssessmentCycle src = get(sourceId);
        if (newYear == null || newYear < 2020 || newYear > 2100)
            throw new IllegalArgumentException("newYear must be 2020..2100");
        guardOneActivePerYear(newYear, null);

        NdiAssessmentCycle copy = new NdiAssessmentCycle();
        copy.setOrganisationId(org());
        copy.setCycleNameEn(src.getCycleNameEn() == null ? null
            : src.getCycleNameEn().replaceAll("\\d{4}$", String.valueOf(newYear)));
        copy.setCycleNameAr(src.getCycleNameAr() == null ? null
            : src.getCycleNameAr().replaceAll("\\d{4}$", String.valueOf(newYear)));
        copy.setCycleCode(String.format(Locale.ROOT, "NDI-%d-Q1", newYear));
        copy.setReportingYear(newYear);
        copy.setScope(src.getScope());
        copy.setEntityNameEn(src.getEntityNameEn());
        copy.setEntityNameAr(src.getEntityNameAr());
        copy.setDataOfficeOwnerName(src.getDataOfficeOwnerName());
        copy.setDataOfficeOwnerEmail(src.getDataOfficeOwnerEmail());
        copy.setAssessmentManagerName(src.getAssessmentManagerName());
        copy.setAssessmentManagerEmail(src.getAssessmentManagerEmail());
        copy.setStatus("DRAFT");
        copy.setNotes(String.format("Cloned from cycle %d (FY%d). Scores + evidence reset for the new year.",
            src.getId(), src.getReportingYear()));
        NdiAssessmentCycle saved = repo.save(copy);
        audit.publish("CYCLE", saved.getId(), saved.getCycleCode(), saved.getId(),
            "CREATE", "Cloned from cycle " + sourceId + " (FY" + src.getReportingYear() + ")");
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        NdiAssessmentCycle cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.publish("CYCLE", cur.getId(), cur.getCycleCode(), cur.getId(),
            "DELETE", "WARN", "Cycle soft-deleted");
    }

    // ── Aggregation ─────────────────────────────────────────────────

    /** Counts per status — drives the NDI dashboard "cycle progress" widget. */
    public Map<String, Object> aggregate() {
        var all = repo.findByOrganisationIdOrderByReportingYearDescIdDesc(
            org(), Pageable.unpaged()).getContent();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : FORWARD_FLOW) byStatus.put(s, 0);
        byStatus.put("ARCHIVED", 0);

        for (var c : all) byStatus.merge(c.getStatus(), 1, Integer::sum);

        long activeCount = all.stream()
            .filter(c -> !END_STATUSES.contains(c.getStatus()))
            .count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("active", activeCount);
        out.put("byStatus", byStatus);
        return out;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static boolean isForwardOneStep(String from, String to) {
        int i = FORWARD_FLOW.indexOf(from);
        int j = FORWARD_FLOW.indexOf(to);
        return i >= 0 && j == i + 1;
    }

    private void guardOneActivePerYear(Short year, Long excludeId) {
        if (year == null) return;
        var same = repo.findByOrganisationIdAndReportingYear(org(), year);
        boolean alreadyActive = same.stream()
            .filter(c -> excludeId == null || !excludeId.equals(c.getId()))
            .anyMatch(c -> !END_STATUSES.contains(c.getStatus()));
        if (alreadyActive) {
            throw new IllegalArgumentException(
                "An active NDI cycle already exists for year " + year + ". Close or archive it first.");
        }
    }

    private void validate(NdiAssessmentCycle c) {
        if (c.getCycleNameEn() == null || c.getCycleNameEn().isBlank())
            throw new IllegalArgumentException("cycleNameEn required");
        if (c.getReportingYear() == null)
            throw new IllegalArgumentException("reportingYear required");
        if (c.getReportingYear() < 2020 || c.getReportingYear() > 2100)
            throw new IllegalArgumentException("reportingYear must be 2020..2100");
        if (!ALL_STATUSES.contains(c.getStatus()))
            throw new IllegalArgumentException("status must be one of " + ALL_STATUSES);
    }
}
