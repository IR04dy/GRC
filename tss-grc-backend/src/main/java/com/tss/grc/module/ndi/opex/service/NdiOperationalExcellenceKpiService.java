package com.tss.grc.module.ndi.opex.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.opex.entity.NdiOperationalExcellenceKpi;
import com.tss.grc.module.ndi.opex.repository.NdiOperationalExcellenceKpiRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * NDI Operational Excellence KPI service (slice 5).
 *
 * <p>Per-KPI {@code status} (Rule 13) is computed on every save from
 * actual vs target with direction-awareness:
 * <ul>
 *   <li>"Higher is better" — target ≥ baseline. Status thresholds:
 *       ACHIEVED if actual ≥ target, ON_TRACK ≥ 0.90×target,
 *       AT_RISK ≥ 0.70×target, BEHIND_PLAN below.</li>
 *   <li>"Lower is better" — target &lt; baseline (e.g. MTTR). Thresholds
 *       invert: ACHIEVED if actual ≤ target, etc.</li>
 *   <li>{@code actual} null + {@code dataSource} null → DATA_MISSING.</li>
 *   <li>{@code actual} null + {@code dataSource} set → NOT_STARTED.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NdiOperationalExcellenceKpiService {

    private static final Set<String> STATUSES = Set.of(
        "ON_TRACK", "AT_RISK", "BEHIND_PLAN", "ACHIEVED", "DATA_MISSING", "NOT_STARTED");

    private final NdiOperationalExcellenceKpiRepository repo;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<NdiOperationalExcellenceKpi> listByCycle(Long cycleId, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdOrderByCategoryAscIdAsc(org(), cycleId, p);
    }
    public Page<NdiOperationalExcellenceKpi> listByCycleAndStatus(Long cycleId, String status, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdAndStatusOrderByCategoryAscIdAsc(org(), cycleId, status, p);
    }
    public Page<NdiOperationalExcellenceKpi> listByCycleAndCategory(Long cycleId, String category, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdAndCategoryOrderByIdAsc(org(), cycleId, category, p);
    }
    public NdiOperationalExcellenceKpi get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("NDI KPI " + id));
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public NdiOperationalExcellenceKpi create(NdiOperationalExcellenceKpi req) {
        req.setId(null);
        req.setOrganisationId(org());
        validate(req);
        recomputeStatus(req);
        return repo.save(req);
    }

    @Transactional
    public NdiOperationalExcellenceKpi update(Long id, NdiOperationalExcellenceKpi req) {
        NdiOperationalExcellenceKpi cur = get(id);
        if (req.getCategory() != null)         cur.setCategory(req.getCategory());
        if (req.getIndicatorNameEn() != null)  cur.setIndicatorNameEn(req.getIndicatorNameEn());
        if (req.getIndicatorNameAr() != null)  cur.setIndicatorNameAr(req.getIndicatorNameAr());
        if (req.getDefinitionEn() != null)     cur.setDefinitionEn(req.getDefinitionEn());
        if (req.getDefinitionAr() != null)     cur.setDefinitionAr(req.getDefinitionAr());
        if (req.getFormula() != null)          cur.setFormula(req.getFormula());
        if (req.getDataSource() != null)       cur.setDataSource(req.getDataSource());
        if (req.getBaseline() != null)         cur.setBaseline(req.getBaseline());
        if (req.getTarget() != null)           cur.setTarget(req.getTarget());
        if (req.getActual() != null)           cur.setActual(req.getActual());
        if (req.getUnit() != null)             cur.setUnit(req.getUnit());
        if (req.getFrequency() != null)        cur.setFrequency(req.getFrequency());
        if (req.getOwnerName() != null)        cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)       cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getEvidenceRef() != null)      cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getNotes() != null)            cur.setNotes(req.getNotes());
        validate(cur);
        recomputeStatus(cur);
        return repo.save(cur);
    }

    /**
     * Record a new actual reading. Stamps {@code lastActualAt} and
     * recomputes status. Convenience over the generic update endpoint.
     */
    @Transactional
    public NdiOperationalExcellenceKpi updateActual(Long id, BigDecimal actual, LocalDate asOf) {
        NdiOperationalExcellenceKpi cur = get(id);
        cur.setActual(actual);
        cur.setLastActualAt(asOf != null ? asOf : LocalDate.now());
        recomputeStatus(cur);
        return repo.save(cur);
    }

    @Transactional
    public void softDelete(Long id) {
        NdiOperationalExcellenceKpi cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
    }

    // ── Aggregate ────────────────────────────────────────────────────

    /**
     * Per-cycle rollup. Cycle "operational excellence score" is the
     * mean of progressTowardTargetPct across all KPIs (0..100). Also
     * returns status counts + behind-plan + at-risk counts so the
     * dashboard can render the gauge + buckets directly.
     */
    public Map<String, Object> aggregateForCycle(Long cycleId) {
        List<NdiOperationalExcellenceKpi> all = repo.findByOrganisationIdAndCycleId(org(), cycleId);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : STATUSES) byStatus.put(s, 0);
        Map<String, Integer> byCategory = new LinkedHashMap<>();

        BigDecimal totalProgress = BigDecimal.ZERO;
        int scored = 0;

        for (NdiOperationalExcellenceKpi k : all) {
            byStatus.merge(k.getStatus(), 1, Integer::sum);
            byCategory.merge(k.getCategory(), 1, Integer::sum);
            BigDecimal p = progressTowardTargetPct(k);
            if (p != null) {
                totalProgress = totalProgress.add(p);
                scored++;
            }
        }
        BigDecimal opexScorePct = scored == 0
            ? BigDecimal.ZERO
            : totalProgress.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cycleId", cycleId);
        out.put("total", all.size());
        out.put("opexScorePct", opexScorePct);
        out.put("byStatus", byStatus);
        out.put("byCategory", byCategory);
        out.put("behindPlanCount", byStatus.getOrDefault("BEHIND_PLAN", 0));
        out.put("atRiskCount",     byStatus.getOrDefault("AT_RISK", 0));
        out.put("achievedCount",   byStatus.getOrDefault("ACHIEVED", 0));
        return out;
    }

    // ── Rule 13: status computation ──────────────────────────────────

    /**
     * Compute the status from actual vs target, direction-aware. Capped
     * to the V70 CHECK constraint values.
     */
    private void recomputeStatus(NdiOperationalExcellenceKpi k) {
        if (k.getActual() == null) {
            k.setStatus(k.getDataSource() == null || k.getDataSource().isBlank()
                ? "DATA_MISSING" : "NOT_STARTED");
            return;
        }
        BigDecimal target = k.getTarget();
        BigDecimal baseline = k.getBaseline() == null ? BigDecimal.ZERO : k.getBaseline();
        boolean higherIsBetter = target.compareTo(baseline) >= 0;

        // Compute progress fraction: 1.0 means at target, 0 means at baseline.
        BigDecimal progress = progressFraction(k);
        if (progress == null) {  // target == baseline edge case
            k.setStatus(k.getActual().compareTo(target) == 0 ? "ACHIEVED" : "NOT_STARTED");
            return;
        }

        if (higherIsBetter) {
            if (k.getActual().compareTo(target) >= 0) { k.setStatus("ACHIEVED"); return; }
        } else {
            if (k.getActual().compareTo(target) <= 0) { k.setStatus("ACHIEVED"); return; }
        }

        // Banded thresholds — 90% / 70% of progress toward target.
        if (progress.compareTo(new BigDecimal("0.90")) >= 0)      k.setStatus("ON_TRACK");
        else if (progress.compareTo(new BigDecimal("0.70")) >= 0) k.setStatus("AT_RISK");
        else                                                       k.setStatus("BEHIND_PLAN");
    }

    /**
     * Progress fraction toward target (0..1). Used by both status
     * compute and the dashboard score. Direction-aware.
     */
    private static BigDecimal progressFraction(NdiOperationalExcellenceKpi k) {
        if (k.getActual() == null) return null;
        BigDecimal baseline = k.getBaseline() == null ? BigDecimal.ZERO : k.getBaseline();
        BigDecimal target   = k.getTarget();
        BigDecimal denom    = target.subtract(baseline);
        if (denom.signum() == 0) return null;  // edge case — caller handles
        return k.getActual().subtract(baseline)
            .divide(denom, 4, RoundingMode.HALF_UP);
    }

    /** Progress percentage 0..100 — same as fraction but × 100, clamped. */
    private static BigDecimal progressTowardTargetPct(NdiOperationalExcellenceKpi k) {
        BigDecimal f = progressFraction(k);
        if (f == null) {
            if (k.getActual() == null) return null;
            return k.getActual().compareTo(k.getTarget()) == 0 ? new BigDecimal("100") : BigDecimal.ZERO;
        }
        BigDecimal pct = f.multiply(new BigDecimal("100"));
        if (pct.signum() < 0)                                     pct = BigDecimal.ZERO;
        if (pct.compareTo(new BigDecimal("100")) > 0)             pct = new BigDecimal("100");
        return pct.setScale(2, RoundingMode.HALF_UP);
    }

    // ── Validation ───────────────────────────────────────────────────
    private void validate(NdiOperationalExcellenceKpi k) {
        if (k.getCycleId() == null) throw new IllegalArgumentException("cycleId required");
        if (k.getIndicatorNameEn() == null || k.getIndicatorNameEn().isBlank())
            throw new IllegalArgumentException("indicatorNameEn required");
        if (k.getCategory() == null || k.getCategory().isBlank())
            throw new IllegalArgumentException("category required");
        if (k.getTarget() == null) throw new IllegalArgumentException("target required");
        if (!STATUSES.contains(k.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
    }
}
