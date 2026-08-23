package com.tss.grc.module.ndi.gap.service;

import com.tss.grc.common.validation.ServerOwnedState;
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
    private final com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit;
    private final com.tss.grc.module.ndi.integration.NdiCrossLinkService crossLink;

    public NdiGapService(NdiGapRepository gaps, NdiCorrectiveActionRepository capas,
                         com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit,
                         com.tss.grc.module.ndi.integration.NdiCrossLinkService crossLink) {
        this.gaps = gaps;
        this.capas = capas;
        this.audit = audit;
        this.crossLink = crossLink;
    }

    /** Read-through to the ERM risk row this gap points to (Cross-module link). */
    @Transactional(readOnly = true)
    public com.tss.grc.module.ndi.integration.NdiCrossLinkService.LinkedRisk getLinkedRisk(Long gapId) {
        return crossLink.getLinkedRisk(getGap(gapId).getRiskId());
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
        if (status != null)   return gaps.findRegisterBySeverityAndStatus(o, cycleId, status, p);
        if (severity != null) return gaps.findByOrganisationIdAndCycleIdAndSeverityOrderByIdAsc(o, cycleId, severity, p);
        return gaps.findRegisterBySeverity(o, cycleId, p);
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
        // F2-302 — a gap opens OPEN. `NdiGap.onCreate()` defaults the field, and a default is not a
        // guard: probed live, a create naming RESOLVED returned 200 and stored it, so the state the
        // CAPA rollup derives was assertable on a row with no CAPAs at all. Nothing sends a status
        // on create (the register's form has no such field) and `createGapFromSource` sets OPEN
        // outright, so refusing cannot break a request that works today ([[F2-182]]).
        // The route clause is stated rather than defaulted: this register has NO state machine and
        // the PUT records no actor, so the primitive's usual sentence would send an operator to a
        // mechanism that does not exist (F2-302).
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), NdiGap.INITIAL_STATUS, "NDI gap",
            "PUT /api/ndi/gaps/{id}", "status",
            "which keeps the status and the close date consistent with the CAPA rollup"));
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getOpenDate() == null) body.setOpenDate(LocalDate.now());
        // Cross-module integrity: validate risk_id resolves to a real ERM row.
        crossLink.validateRiskExists(body.getRiskId());
        NdiGap saved = gaps.save(body);
        audit.publish("GAP", saved.getId(), saved.getGapCode(), saved.getCycleId(),
            "CREATE", "CRITICAL".equals(saved.getSeverity()) ? "WARN" : "INFO",
            "Opened " + saved.getSeverity() + " gap: " + saved.getTitleEn());
        return saved;
    }


    /**
     * F2-302 — the <b>single writer</b> of the pair {@code (status, actual_close_date)} on a gap.
     *
     * <p>{@code recomputeRollup} has always owned that pair: when every corrective action is
     * complete it sets {@link NdiGap#RESOLVED} <em>and</em> stamps the close date in the same
     * breath. {@code updateGap} wrote the two fields independently, so the register's status column
     * — an inline {@code <select>} on the NDI Gap Register that sends {@code {status}} and nothing
     * else — could produce <b>RESOLVED with no close date</b>, a state the rollup can never reach,
     * rendered directly beside the progress bar showing {@code 0% · 0/1}. Probed live as
     * {@code compliance}: HTTP 200, status RESOLVED, {@code actual_close_date} null.
     *
     * <p>The rule mirrors the rollup rather than adding to it: entering RESOLVED stamps a date if
     * none is set, and returning to a {@link NdiGap#NOT_CLOSED} state clears it, because a gap that
     * is open was not closed. ⚠ That clears a date the caller may have stated in the same body —
     * deliberately, because on this register the close date is <b>derived from the status</b>, which
     * is what the rollup already treats it as. It is not a field the server ignores ([[F2-184]]);
     * the status governs it.
     *
     * <p>⚠ {@code CLOSED} and {@code ACCEPTED_RISK} are left alone in both directions. The rollup
     * refuses to move either, and nothing in this platform declares whether they carry a close date
     * — so stamping one would invent a rule rather than enforce a declared one ([[F2-183]]), and it
     * is filed instead. That asymmetry is the reason this method is not simply "closure stamps a
     * date".
     */
    private static void applyStatusAndCloseDate(NdiGap g, String requested) {
        // Normalise BEFORE either branch tests it: the column is case-insensitively collated, so
        // 'resolved' is accepted by ck_ndi_gap_status and would otherwise match neither RESOLVED
        // nor NOT_CLOSED, skipping the stamp and the clear alike (F2-179, F2-302).
        String next = NdiGap.STATUS.require(requested);
        g.setStatus(next);
        if (NdiGap.RESOLVED.equals(next)) {
            if (g.getActualCloseDate() == null) g.setActualCloseDate(LocalDate.now());
        } else if (NdiGap.NOT_CLOSED.contains(next)) {
            g.setActualCloseDate(null);
        }
    }

    public NdiGap updateGap(Long id, NdiGap body) {
        NdiGap g = getGap(id);
        // F2-307 — settable at birth, fixed thereafter. Nothing mints this code, so a caller
        // names it on create; renaming it afterwards would break anything already citing it.
        ServerOwnedState.requireImmutableReference(
            body.getGapCode(), g.getGapCode(), "gapCode",
            "NDI gap " + (g.getId() == null ? "(new)" : g.getId()),
            "Omit gapCode, or send the value already recorded.");
        if (body.getGapCode() != null && g.getGapCode() == null) {
            g.setGapCode(body.getGapCode());
        }
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
        String prevStatus = g.getStatus();
        // F2-302 — the status and the close date move together. Apply the caller's date FIRST so an
        // explicitly stated one is honoured, then let the status govern.
        if (body.getStatus()        != null) applyStatusAndCloseDate(g, body.getStatus());
        if (body.getRiskId()        != null) { crossLink.validateRiskExists(body.getRiskId()); g.setRiskId(body.getRiskId()); }
        if (body.getNotes()         != null) g.setNotes(body.getNotes());
        NdiGap saved = gaps.save(g);
        // Both sides are canonical here -- the setter above normalised the incoming value and
        // everything stored went through the same primitive -- so `equals` is a real comparison
        // rather than F2-179's trap. Without the normalisation a case-variant echo would read as a
        // CHANGE and publish a TRANSITION audit row for a move that never happened, which is what
        // F2-297 found on CmaDisclosureService and TcfdItemService.
        if (body.getStatus() != null && !g.getStatus().equals(prevStatus)) {
            audit.publishTransition("GAP", saved.getId(), saved.getGapCode(), saved.getCycleId(),
                prevStatus, saved.getStatus(), "Gap status changed");
        } else {
            audit.publish("GAP", saved.getId(), saved.getGapCode(), saved.getCycleId(),
                "UPDATE", "Gap updated");
        }
        return saved;
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
        audit.publish("GAP", g.getId(), g.getGapCode(), g.getCycleId(),
            "DELETE", "WARN", "Gap and " + capas.findByOrganisationIdAndGapIdOrderByIdAsc(org(), id).size() + " CAPAs soft-deleted");
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
        // F2-307 — settable at birth, fixed thereafter. Nothing mints this code, so a caller
        // names it on create; renaming it afterwards would break anything already citing it.
        ServerOwnedState.requireImmutableReference(
            body.getActionCode(), c.getActionCode(), "actionCode",
            "NDI corrective action " + (c.getId() == null ? "(new)" : c.getId()),
            "Omit actionCode, or send the value already recorded.");
        if (body.getActionCode() != null && c.getActionCode() == null) {
            c.setActionCode(body.getActionCode());
        }
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
                applyStatusAndCloseDate(g, NdiGap.RESOLVED);
            } else if (sum > 0 && NdiGap.INITIAL_STATUS.equals(g.getStatus())) {
                applyStatusAndCloseDate(g, "IN_PROGRESS");
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
