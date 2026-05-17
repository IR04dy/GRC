package com.tss.grc.module.ndi.gap.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import com.tss.grc.module.ndi.gap.entity.NdiGap;
import com.tss.grc.module.ndi.gap.repository.NdiCorrectiveActionRepository;
import com.tss.grc.module.ndi.gap.repository.NdiGapRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * NDI Gap + CAPA service — slice 8.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>Rule 7 — gap rollup_progress is the mean of its CAPA progress_pct
 *       values; closing all CAPAs (status=COMPLETED) auto-closes the gap.</li>
 *   <li>Rule 8 — CRITICAL gaps require a CAPA with target_date within 90
 *       days of the gap's open_date.</li>
 *   <li>Rule 9 — progress_pct constrained to 0..100.</li>
 * </ul>
 */
@Service
@Transactional
public class NdiGapService {

    private final NdiGapRepository gaps;
    private final NdiCorrectiveActionRepository capas;

    public NdiGapService(NdiGapRepository gaps, NdiCorrectiveActionRepository capas) {
        this.gaps = gaps;
        this.capas = capas;
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    public record GapAggregate(
        long total,
        Map<String, Long> bySeverity,
        Map<String, Long> byStatus,
        int openCount,
        int inProgressCount,
        int resolvedOrClosedCount,
        int overdueCount,         // CAPAs past target_date and not COMPLETED
        int criticalOpenCount,    // severity=CRITICAL and not (RESOLVED|CLOSED|ACCEPTED_RISK)
        int avgRollupProgress
    ) {}

    // ─── Gap CRUD ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<NdiGap> listGaps(Long cycleId, String status, String severity, Pageable p) {
        Long o = org();
        if (status != null)   return gaps.findByOrganisationIdAndCycleIdAndStatusOrderBySeverityAscIdAsc(o, cycleId, status, p);
        if (severity != null) return gaps.findByOrganisationIdAndCycleIdAndSeverityOrderByIdAsc(o, cycleId, severity, p);
        return gaps.findByOrganisationIdAndCycleIdOrderBySeverityAscIdAsc(o, cycleId, p);
    }

    @Transactional(readOnly = true)
    public NdiGap getGap(Long id) {
        return gaps.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Gap not found: " + id));
    }

    public NdiGap createGap(NdiGap body) {
        if (body.getCycleId() == null) throw new IllegalArgumentException("cycleId is required");
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getSourceType() == null) body.setSourceType("MANUAL");
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getOpenDate() == null) body.setOpenDate(LocalDate.now());
        return gaps.save(body);
    }

    public NdiGap updateGap(Long id, NdiGap body) {
        NdiGap g = getGap(id);
        if (body.getGapCode()       != null) g.setGapCode(body.getGapCode());
        if (body.getDomain()        != null) g.setDomain(body.getDomain());
        if (body.getTitleEn()       != null) g.setTitleEn(body.getTitleEn());
        if (body.getTitleAr()       != null) g.setTitleAr(body.getTitleAr());
        if (body.getDescriptionEn() != null) g.setDescriptionEn(body.getDescriptionEn());
        if (body.getDescriptionAr() != null) g.setDescriptionAr(body.getDescriptionAr());
        if (body.getRootCause()     != null) g.setRootCause(body.getRootCause());
        if (body.getImpactSummary() != null) g.setImpactSummary(body.getImpactSummary());
        if (body.getSeverity()      != null) g.setSeverity(body.getSeverity());
        if (body.getLikelihood()    != null) g.setLikelihood(body.getLikelihood());
        if (body.getOwnerName()     != null) g.setOwnerName(body.getOwnerName());
        if (body.getOwnerEmail()    != null) g.setOwnerEmail(body.getOwnerEmail());
        if (body.getTargetDate()    != null) g.setTargetDate(body.getTargetDate());
        if (body.getActualCloseDate() != null) g.setActualCloseDate(body.getActualCloseDate());
        if (body.getStatus()        != null) g.setStatus(body.getStatus());
        if (body.getRiskId()        != null) g.setRiskId(body.getRiskId());
        if (body.getNotes()         != null) g.setNotes(body.getNotes());
        return gaps.save(g);
    }

    public void deleteGap(Long id) {
        NdiGap g = getGap(id);
        g.setIsDeleted(true);
        gaps.save(g);
        // Cascade soft-delete to its CAPAs.
        for (NdiCorrectiveAction c : capas.findByOrganisationIdAndGapIdOrderByIdAsc(org(), id)) {
            c.setIsDeleted(true);
            capas.save(c);
        }
    }

    // ─── CAPA CRUD ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NdiCorrectiveAction> listCapas(Long gapId) {
        return capas.findByOrganisationIdAndGapIdOrderByIdAsc(org(), gapId);
    }

    @Transactional(readOnly = true)
    public NdiCorrectiveAction getCapa(Long id) {
        return capas.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("CAPA not found: " + id));
    }

    public NdiCorrectiveAction createCapa(Long gapId, NdiCorrectiveAction body) {
        NdiGap parent = getGap(gapId);
        if (body.getActionEn() == null || body.getActionEn().isBlank())
            throw new IllegalArgumentException("actionEn is required");
        body.setId(null);
        body.setGapId(parent.getId());
        body.setOrganisationId(org());
        validateProgress(body);
        NdiCorrectiveAction saved = capas.save(body);
        applyRule8(parent);
        recomputeGapRollup(parent.getId());
        return saved;
    }

    public NdiCorrectiveAction updateCapa(Long id, NdiCorrectiveAction body) {
        NdiCorrectiveAction c = getCapa(id);
        if (body.getActionCode()    != null) c.setActionCode(body.getActionCode());
        if (body.getActionEn()      != null) c.setActionEn(body.getActionEn());
        if (body.getActionAr()      != null) c.setActionAr(body.getActionAr());
        if (body.getActionType()    != null) c.setActionType(body.getActionType());
        if (body.getOwnerName()     != null) c.setOwnerName(body.getOwnerName());
        if (body.getOwnerEmail()    != null) c.setOwnerEmail(body.getOwnerEmail());
        if (body.getTargetDate()    != null) c.setTargetDate(body.getTargetDate());
        if (body.getCompletionDate() != null) c.setCompletionDate(body.getCompletionDate());
        if (body.getProgressPct()   != null) c.setProgressPct(body.getProgressPct());
        if (body.getStatus()        != null) c.setStatus(body.getStatus());
        if (body.getEvidenceId()    != null) c.setEvidenceId(body.getEvidenceId());
        if (body.getBlockerNote()   != null) c.setBlockerNote(body.getBlockerNote());
        if (body.getNotes()         != null) c.setNotes(body.getNotes());
        validateProgress(c);
        // Auto-derive: status=COMPLETED forces progress=100 and stamps completion_date.
        if ("COMPLETED".equals(c.getStatus())) {
            c.setProgressPct(100);
            if (c.getCompletionDate() == null) c.setCompletionDate(LocalDate.now());
        }
        NdiCorrectiveAction saved = capas.save(c);
        recomputeGapRollup(c.getGapId());
        return saved;
    }

    public void deleteCapa(Long id) {
        NdiCorrectiveAction c = getCapa(id);
        c.setIsDeleted(true);
        capas.save(c);
        recomputeGapRollup(c.getGapId());
    }

    // ─── Auto-create gap from a compliance/maturity row ────────────

    public NdiGap upsertFromSource(Long cycleId, String sourceType, Long sourceId,
                                   String domain, String titleEn, String titleAr,
                                   String severity, String ownerName) {
        Long o = org();
        return gaps.findByOrganisationIdAndSourceTypeAndSourceId(o, sourceType, sourceId)
            .map(existing -> {
                if (titleEn != null) existing.setTitleEn(titleEn);
                if (titleAr != null) existing.setTitleAr(titleAr);
                if (severity != null) existing.setSeverity(severity);
                if (ownerName != null) existing.setOwnerName(ownerName);
                return gaps.save(existing);
            })
            .orElseGet(() -> {
                NdiGap g = new NdiGap();
                g.setOrganisationId(o);
                g.setCycleId(cycleId);
                g.setSourceType(sourceType);
                g.setSourceId(sourceId);
                g.setDomain(domain);
                g.setTitleEn(titleEn);
                g.setTitleAr(titleAr);
                g.setSeverity(severity != null ? severity : "MEDIUM");
                g.setOwnerName(ownerName);
                g.setOpenDate(LocalDate.now());
                g.setStatus("OPEN");
                return gaps.save(g);
            });
    }

    // ─── Aggregate ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GapAggregate aggregate(Long cycleId) {
        Long o = org();
        List<NdiGap> all = gaps.findByOrganisationIdAndCycleId(o, cycleId);
        Map<String, Long> bySev = new TreeMap<>();
        Map<String, Long> byStat = new TreeMap<>();
        int open = 0, inProg = 0, doneish = 0, critOpen = 0;
        long progSum = 0;
        for (NdiGap g : all) {
            bySev.merge(g.getSeverity(), 1L, Long::sum);
            byStat.merge(g.getStatus(), 1L, Long::sum);
            progSum += g.getRollupProgress();
            switch (g.getStatus()) {
                case "OPEN"          -> open++;
                case "IN_PROGRESS"   -> inProg++;
                case "RESOLVED",
                     "CLOSED",
                     "ACCEPTED_RISK" -> doneish++;
                default -> {}
            }
            if ("CRITICAL".equals(g.getSeverity())
                && !"RESOLVED".equals(g.getStatus())
                && !"CLOSED".equals(g.getStatus())
                && !"ACCEPTED_RISK".equals(g.getStatus())) critOpen++;
        }

        // Overdue CAPAs (past target_date and not COMPLETED).
        LocalDate today = LocalDate.now();
        int overdue = 0;
        for (NdiCorrectiveAction c : capas.findByOrganisationId(o)) {
            if (c.getTargetDate() != null
                && c.getTargetDate().isBefore(today)
                && !"COMPLETED".equals(c.getStatus())) overdue++;
        }

        int avgProg = all.isEmpty() ? 0 : (int) Math.round((double) progSum / all.size());
        return new GapAggregate(all.size(), bySev, byStat, open, inProg, doneish, overdue, critOpen, avgProg);
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private void validateProgress(NdiCorrectiveAction c) {
        if (c.getProgressPct() == null) c.setProgressPct(0);
        if (c.getProgressPct() < 0 || c.getProgressPct() > 100)
            throw new IllegalArgumentException("progressPct must be 0..100, got " + c.getProgressPct());
    }

    /** Recompute gap.rollup_progress, capa_count, capa_completed_count and auto-close. */
    private void recomputeGapRollup(Long gapId) {
        Long o = org();
        NdiGap g = gaps.findByIdAndOrganisationId(gapId, o).orElse(null);
        if (g == null) return;
        List<NdiCorrectiveAction> children = capas.findByOrganisationIdAndGapIdOrderByIdAsc(o, gapId);
        int n = children.size();
        if (n == 0) {
            g.setCapaCount(0);
            g.setCapaCompletedCount(0);
            g.setRollupProgress(0);
        } else {
            int sum = 0;
            int completed = 0;
            for (NdiCorrectiveAction c : children) {
                sum += c.getProgressPct();
                if ("COMPLETED".equals(c.getStatus())) completed++;
            }
            g.setCapaCount(n);
            g.setCapaCompletedCount(completed);
            g.setRollupProgress(sum / n);

            // Auto-close when all CAPAs completed.
            if (completed == n
                && !"CLOSED".equals(g.getStatus())
                && !"ACCEPTED_RISK".equals(g.getStatus())) {
                g.setStatus("RESOLVED");
                if (g.getActualCloseDate() == null) g.setActualCloseDate(LocalDate.now());
            } else if (sum > 0 && "OPEN".equals(g.getStatus())) {
                g.setStatus("IN_PROGRESS");
            }
        }
        gaps.save(g);
    }

    /** Rule 8 — CRITICAL gaps require a CAPA with target ≤ 90d from open_date. */
    private void applyRule8(NdiGap g) {
        if (!"CRITICAL".equals(g.getSeverity())) return;
        if (g.getOpenDate() == null) return;
        LocalDate limit = g.getOpenDate().plusDays(90);
        boolean compliant = capas.findByOrganisationIdAndGapIdOrderByIdAsc(org(), g.getId())
            .stream()
            .anyMatch(c -> c.getTargetDate() != null && !c.getTargetDate().isAfter(limit));
        if (!compliant) {
            // Soft signal — annotate notes; service does NOT block creation. Audit picks this up.
            String marker = "[Rule 8] CRITICAL gap is missing a CAPA with target date within 90 days of open_date ("
                + ChronoUnit.DAYS.between(g.getOpenDate(), limit) + " days)";
            if (g.getNotes() == null || !g.getNotes().contains("[Rule 8]")) {
                g.setNotes((g.getNotes() == null ? "" : g.getNotes() + "\n") + marker);
                gaps.save(g);
            }
        }
    }
}
