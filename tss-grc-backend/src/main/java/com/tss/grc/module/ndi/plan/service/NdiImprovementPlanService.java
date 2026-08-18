package com.tss.grc.module.ndi.plan.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import com.tss.grc.module.ndi.gap.repository.NdiCorrectiveActionRepository;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlan;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlanItem;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanItemRepository;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * NDI Improvement Plan service — slice 9.
 *
 * <p>Rules:
 * <ul>
 *   <li>Rule 10 — plan totals are recomputed server-side after every
 *       item add / remove or referenced CAPA status change.</li>
 *   <li>Rule 11 — sequence_order is unique within a plan; reorder()
 *       renumbers in steps of 10.</li>
 *   <li>Rule 12 — target_quarter format Y####-Q[1-4].</li>
 *   <li>Status machine: DRAFT → APPROVED → IN_FLIGHT → COMPLETED |
 *       SUSPENDED → ARCHIVED.</li>
 * </ul>
 */
@Service
@Transactional
public class NdiImprovementPlanService {

    private final NdiImprovementPlanRepository plans;
    private final NdiImprovementPlanItemRepository items;
    private final NdiCorrectiveActionRepository capas;
    private final com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit;

    public NdiImprovementPlanService(NdiImprovementPlanRepository plans,
                                     NdiImprovementPlanItemRepository items,
                                     NdiCorrectiveActionRepository capas,
                                     com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit) {
        this.plans = plans;
        this.items = items;
        this.capas = capas;
        this.audit = audit;
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    /**
     * The state a plan is created in. Read by both this create's guard and {@link #approve}'s
     * precondition, so the state the server files a plan in and the only state it will approve
     * from are one value (F2-218).
     */
    private static final String INITIAL_STATUS = "DRAFT";

    public record PlanRollup(
        Long planId,
        int totalItems,
        int completedItems,
        int progressPct,
        Map<String, Integer> byQuarter,    // count of items per quarter
        Map<String, Integer> byPriority,
        Map<String, Integer> byCapaStatus
    ) {}

    public record EnrichedPlanItem(
        Long id, Long planId, Long capaId, Integer sequenceOrder,
        String priority, String targetQuarter, BigDecimal allocatedBudget,
        String dependencies, String notes,
        // CAPA snapshot
        String actionCode, String actionEn, String actionAr, String actionType,
        String capaStatus, Integer capaProgressPct, String capaTargetDate
    ) {}

    // ─── Plan CRUD ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NdiImprovementPlan> listForCycle(Long cycleId) {
        return plans.findByOrganisationIdAndCycleIdOrderByPeriodStartDesc(org(), cycleId);
    }

    @Transactional(readOnly = true)
    public NdiImprovementPlan get(Long id) {
        return plans.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + id));
    }

    public NdiImprovementPlan create(NdiImprovementPlan body) {
        if (body.getCycleId() == null) throw new IllegalArgumentException("cycleId is required");
        if (body.getNameEn() == null || body.getNameEn().isBlank())
            throw new IllegalArgumentException("nameEn is required");
        if (body.getPeriodStart() == null || body.getPeriodEnd() == null)
            throw new IllegalArgumentException("periodStart and periodEnd are required");
        if (body.getPeriodEnd().isBefore(body.getPeriodStart()))
            throw new IllegalArgumentException("periodEnd must be on or after periodStart");
        body.setId(null);
        body.setOrganisationId(org());
        // F2-218: DRAFT is the only state approve() will act on and the only one this create
        // accepts, read from one constant so the two cannot drift. Probed live as `ranalyst`:
        // POST /api/ndi/plans with status=COMPLETED and approverName="Nobody Who Approved This"
        // answered 200 and stored both — skipping DRAFT -> APPROVED (which stamps the approver from
        // the argument and emits an audit transition) and the whole APPROVED -> IN_FLIGHT ->
        // COMPLETED machine underneath it.
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), INITIAL_STATUS, "an improvement plan", "POST /{id}/approve"));
        body.setApproverName(null);
        body.setApprovedAt(null);
        return plans.save(body);
    }

    public NdiImprovementPlan update(Long id, NdiImprovementPlan body) {
        NdiImprovementPlan p = get(id);
        if (body.getPlanCode()       != null) p.setPlanCode(body.getPlanCode());
        if (body.getNameEn()         != null) p.setNameEn(body.getNameEn());
        if (body.getNameAr()         != null) p.setNameAr(body.getNameAr());
        if (body.getDescriptionEn()  != null) p.setDescriptionEn(body.getDescriptionEn());
        if (body.getDescriptionAr()  != null) p.setDescriptionAr(body.getDescriptionAr());
        if (body.getPeriodStart()    != null) p.setPeriodStart(body.getPeriodStart());
        if (body.getPeriodEnd()      != null) p.setPeriodEnd(body.getPeriodEnd());
        if (body.getOwnerName()      != null) p.setOwnerName(body.getOwnerName());
        if (body.getOwnerEmail()     != null) p.setOwnerEmail(body.getOwnerEmail());
        // F2-218: approverName and status are approve()'s and transition()'s to write. Copying
        // them here let a PUT perform DRAFT -> APPROVED with an approver of the caller's choosing
        // and no audit row — the same bypass as the create above, through the other verb. Editing
        // a plan's name, dates, owner or budget is what this route is for.
        if (body.getBudgetTotal()    != null) p.setBudgetTotal(body.getBudgetTotal());
        if (body.getBudgetCurrency() != null) p.setBudgetCurrency(body.getBudgetCurrency());
        if (body.getNotes()          != null) p.setNotes(body.getNotes());
        return plans.save(p);
    }

    /** F2-236 — the approver is the authenticated principal; the frontend passed the literal
     *  {@code 'admin'}. The trail reads off the saved row, not the caller's claim. */
    public NdiImprovementPlan approve(Long id) {
        NdiImprovementPlan p = get(id);
        if (!INITIAL_STATUS.equals(p.getStatus()))
            throw new IllegalStateException("Only DRAFT plans can be approved (was " + p.getStatus() + ")");
        p.setStatus("APPROVED");
        p.setApproverName(CurrentActor.requireUsername("an improvement-plan approval"));
        p.setApprovedAt(LocalDateTime.now());
        NdiImprovementPlan saved = plans.save(p);
        audit.publishTransition("PLAN", saved.getId(), saved.getPlanCode(), saved.getCycleId(),
            "DRAFT", "APPROVED", "Plan approved by " + saved.getApproverName());
        return saved;
    }

    public NdiImprovementPlan transition(Long id, String to) {
        NdiImprovementPlan p = get(id);
        String from = p.getStatus();
        boolean ok = switch (from) {
            case "APPROVED"   -> "IN_FLIGHT".equals(to);
            case "IN_FLIGHT"  -> "COMPLETED".equals(to) || "SUSPENDED".equals(to);
            case "SUSPENDED"  -> "IN_FLIGHT".equals(to) || "ARCHIVED".equals(to);
            case "COMPLETED"  -> "ARCHIVED".equals(to);
            default           -> false;
        };
        if (!ok) throw new IllegalStateException("Invalid transition " + from + " → " + to);
        p.setStatus(to);
        NdiImprovementPlan saved = plans.save(p);
        audit.publishTransition("PLAN", saved.getId(), saved.getPlanCode(), saved.getCycleId(),
            from, to, "Plan transitioned");
        return saved;
    }

    public void delete(Long id) {
        NdiImprovementPlan p = get(id);
        p.setIsDeleted(true);
        plans.save(p);
        for (NdiImprovementPlanItem it : items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(org(), id)) {
            it.setIsDeleted(true);
            items.save(it);
        }
    }

    // ─── Plan item CRUD ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EnrichedPlanItem> listItems(Long planId) {
        Long o = org();
        get(planId); // existence + tenant check
        List<NdiImprovementPlanItem> rows = items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(o, planId);
        Set<Long> capaIds = new HashSet<>();
        for (NdiImprovementPlanItem r : rows) capaIds.add(r.getCapaId());
        Map<Long, NdiCorrectiveAction> capaById = new HashMap<>();
        for (Long cid : capaIds) {
            capas.findByIdAndOrganisationId(cid, o).ifPresent(c -> capaById.put(c.getId(), c));
        }
        List<EnrichedPlanItem> out = new ArrayList<>(rows.size());
        for (NdiImprovementPlanItem r : rows) {
            NdiCorrectiveAction c = capaById.get(r.getCapaId());
            out.add(new EnrichedPlanItem(
                r.getId(), r.getPlanId(), r.getCapaId(), r.getSequenceOrder(),
                r.getPriority(), r.getTargetQuarter(), r.getAllocatedBudget(),
                r.getDependencies(), r.getNotes(),
                c == null ? null : c.getActionCode(),
                c == null ? null : c.getActionEn(),
                c == null ? null : c.getActionAr(),
                c == null ? null : c.getActionType(),
                c == null ? null : c.getStatus(),
                c == null ? null : c.getProgressPct(),
                c == null || c.getTargetDate() == null ? null : c.getTargetDate().toString()
            ));
        }
        return out;
    }

    public NdiImprovementPlanItem addItem(Long planId, NdiImprovementPlanItem body) {
        Long o = org();
        NdiImprovementPlan plan = get(planId);
        if (body.getCapaId() == null) throw new IllegalArgumentException("capaId is required");
        NdiCorrectiveAction capa = capas.findByIdAndOrganisationId(body.getCapaId(), o)
            .orElseThrow(() -> new EntityNotFoundException("CAPA not found: " + body.getCapaId()));
        if (items.findByOrganisationIdAndPlanIdAndCapaId(o, planId, capa.getId()).isPresent())
            throw new IllegalArgumentException("CAPA " + capa.getId() + " is already on this plan");
        validateQuarter(body.getTargetQuarter());
        body.setId(null);
        body.setOrganisationId(o);
        body.setPlanId(plan.getId());
        if (body.getSequenceOrder() == null) body.setSequenceOrder(nextSequenceOrder(planId));
        NdiImprovementPlanItem saved = items.save(body);
        recomputeRollup(planId);
        return saved;
    }

    public NdiImprovementPlanItem updateItem(Long id, NdiImprovementPlanItem body) {
        NdiImprovementPlanItem it = items.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Plan item not found: " + id));
        if (body.getSequenceOrder()   != null) it.setSequenceOrder(body.getSequenceOrder());
        if (body.getPriority()        != null) it.setPriority(body.getPriority());
        if (body.getTargetQuarter()   != null) { validateQuarter(body.getTargetQuarter()); it.setTargetQuarter(body.getTargetQuarter()); }
        if (body.getAllocatedBudget() != null) it.setAllocatedBudget(body.getAllocatedBudget());
        if (body.getDependencies()    != null) it.setDependencies(body.getDependencies());
        if (body.getNotes()           != null) it.setNotes(body.getNotes());
        NdiImprovementPlanItem saved = items.save(it);
        recomputeRollup(saved.getPlanId());
        return saved;
    }

    public void deleteItem(Long id) {
        NdiImprovementPlanItem it = items.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Plan item not found: " + id));
        it.setIsDeleted(true);
        items.save(it);
        recomputeRollup(it.getPlanId());
    }

    /** Renumber sequence_order in steps of 10 according to caller-supplied id sequence. */
    public List<NdiImprovementPlanItem> reorder(Long planId, List<Long> orderedItemIds) {
        Long o = org();
        get(planId);
        List<NdiImprovementPlanItem> all = items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(o, planId);
        Map<Long, NdiImprovementPlanItem> byId = new HashMap<>();
        for (NdiImprovementPlanItem r : all) byId.put(r.getId(), r);
        int seq = 10;
        for (Long id : orderedItemIds) {
            NdiImprovementPlanItem r = byId.get(id);
            if (r == null) throw new IllegalArgumentException("Plan item " + id + " not in plan " + planId);
            r.setSequenceOrder(seq);
            items.save(r);
            seq += 10;
        }
        return items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(o, planId);
    }

    // ─── Rollup ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlanRollup rollup(Long planId) {
        Long o = org();
        get(planId);
        List<NdiImprovementPlanItem> rows = items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(o, planId);
        Map<String, Integer> byQ = new TreeMap<>();
        Map<String, Integer> byP = new TreeMap<>();
        Map<String, Integer> byS = new TreeMap<>();
        int total = rows.size();
        int completed = 0;
        int sumProgress = 0;
        for (NdiImprovementPlanItem r : rows) {
            byQ.merge(r.getTargetQuarter() == null ? "(none)" : r.getTargetQuarter(), 1, Integer::sum);
            byP.merge(r.getPriority(), 1, Integer::sum);
            NdiCorrectiveAction c = capas.findByIdAndOrganisationId(r.getCapaId(), o).orElse(null);
            if (c != null) {
                byS.merge(c.getStatus(), 1, Integer::sum);
                sumProgress += c.getProgressPct();
                if ("COMPLETED".equals(c.getStatus())) completed++;
            } else {
                byS.merge("UNKNOWN", 1, Integer::sum);
            }
        }
        int avg = total == 0 ? 0 : sumProgress / total;
        return new PlanRollup(planId, total, completed, avg, byQ, byP, byS);
    }

    /** Recompute plan.total_items / completed_items / progress_pct from current CAPA state. */
    private void recomputeRollup(Long planId) {
        Long o = org();
        NdiImprovementPlan p = plans.findByIdAndOrganisationId(planId, o).orElse(null);
        if (p == null) return;
        List<NdiImprovementPlanItem> rows = items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(o, planId);
        int total = rows.size();
        int completed = 0;
        int sum = 0;
        for (NdiImprovementPlanItem r : rows) {
            NdiCorrectiveAction c = capas.findByIdAndOrganisationId(r.getCapaId(), o).orElse(null);
            if (c == null) continue;
            sum += c.getProgressPct();
            if ("COMPLETED".equals(c.getStatus())) completed++;
        }
        p.setTotalItems(total);
        p.setCompletedItems(completed);
        p.setProgressPct(total == 0 ? 0 : sum / total);
        // Auto-complete when all CAPAs done and plan is in flight.
        if (total > 0 && completed == total && "IN_FLIGHT".equals(p.getStatus())) {
            p.setStatus("COMPLETED");
        }
        plans.save(p);
    }

    /** Idempotent — recompute every plan's rollup. Useful after slice 8 CAPA edits. */
    public void recomputeAllForOrg() {
        Long o = org();
        for (NdiImprovementPlan p : plans.findByOrganisationIdOrderByPeriodStartDesc(o)) {
            recomputeRollup(p.getId());
        }
    }

    private void validateQuarter(String q) {
        if (q == null) return;
        if (!q.matches("^Y\\d{4}-Q[1-4]$"))
            throw new IllegalArgumentException("targetQuarter must match Y####-Q[1-4], got '" + q + "'");
    }

    private int nextSequenceOrder(Long planId) {
        List<NdiImprovementPlanItem> rows = items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(org(), planId);
        if (rows.isEmpty()) return 10;
        return rows.get(rows.size() - 1).getSequenceOrder() + 10;
    }
}
