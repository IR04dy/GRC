package com.tss.grc.module.sust.sgi.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Saudi Green Initiative commitment service.
 *
 * <p>Status machine: {@code ACTIVE → ACHIEVED} or {@code ACTIVE → RETIRED}.
 *
 * <p>Rules:
 * <ul>
 *   <li><b>Rule A</b> — transitioning to {@code ACHIEVED} requires
 *       current_value to meet/exceed target (within the direction of the
 *       commitment — handles both "raise to" and "lower to" targets).</li>
 *   <li><b>Rule B</b> — transitioning to {@code RETIRED} requires
 *       {@code retiredReason} to be set.</li>
 * </ul>
 *
 * <p>Progress is computed on read, not stored:
 * <pre>
 *   if (target > baseline):  pct = (current - baseline) / (target - baseline)
 *   if (target < baseline):  pct = (baseline - current) / (baseline - target)
 * </pre>
 * Tier (ON_TRACK / AT_RISK / DELAYED) compares actual vs expected at
 * today's point in the timeline (linear assumption between
 * {@code baselineYear} and {@code targetYear}).
 */
@Service
@RequiredArgsConstructor
public class SgiCommitmentService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "ACHIEVED", "RETIRED");
    private static final Set<String> SECTORS = Set.of(
        "ENERGY", "INDUSTRY", "TRANSPORT", "WASTE", "FORESTRY", "WATER", "LAND", "OTHER");

    private final SgiCommitmentRepository repo;
    private final OrganisationSettingsService settingsService;
    private final AuditLogService audit;
    private final CorrectiveActionService capaService;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<SgiCommitment> list(Pageable p) {
        return repo.findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(org(), p);
    }
    public Page<SgiCommitment> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByTargetYearAscCommitmentCodeAsc(org(), status, p);
    }
    public Page<SgiCommitment> listBySector(String sector, Pageable p) {
        return repo.findByOrganisationIdAndSectorOrderByTargetYearAscCommitmentCodeAsc(org(), sector, p);
    }
    public SgiCommitment get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("SGI commitment " + id));
    }
    public SgiCommitment getByCode(String code) {
        return repo.findByOrganisationIdAndCommitmentCode(org(), code)
            .orElseThrow(() -> new EntityNotFoundException("SGI commitment code " + code));
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public SgiCommitment create(SgiCommitment req) {
        req.setId(null);
        req.setOrganisationId(org());
        // Mass-assignment guard (SEC-ESG-003): force the initial lifecycle
        // state (achieve()/retire() are the only paths out of ACTIVE) and
        // scrub the achievement / retirement / audit attribution columns so
        // a commitment cannot be born ACHIEVED with a forged signer.
        req.setStatus("ACTIVE");
        req.setAchievedAt(null);
        req.setAchievedBy(null);
        req.setRetiredAt(null);
        req.setRetiredBy(null);
        req.setRetiredReason(null);
        // SGI-A — attribution comes from the authenticated principal, never the
        // request body (audit-Critical actor-from-principal pattern). created_by
        // seeds the Rule-C SoD comparison (last-editor ≠ achiever).
        req.setCreatedBy(CurrentActor.username());
        req.setUpdatedBy(null);
        validate(req);
        SgiCommitment saved = repo.save(req);
        audit.log("CREATE", "SgiCommitment", saved.getId(), "Created SGI commitment: " + saved.getCommitmentCode());
        maybeAutoCapaBehindPlan(saved);
        return saved;
    }

    @Transactional
    public SgiCommitment update(Long id, SgiCommitment req) {
        SgiCommitment cur = get(id);
        // Achieved/retired commitments are read-only — the lifecycle is
        // terminal and the audit trail should remain intact.
        if ("ACHIEVED".equals(cur.getStatus()) || "RETIRED".equals(cur.getStatus())) {
            throw new IllegalStateException(
                "Commitment is " + cur.getStatus() + " and cannot be modified. Soft-delete to remove from the register.");
        }
        if (req.getSector() != null)             cur.setSector(req.getSector());
        if (req.getTitleEn() != null)            cur.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null)            cur.setTitleAr(req.getTitleAr());
        if (req.getDescriptionEn() != null)      cur.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null)      cur.setDescriptionAr(req.getDescriptionAr());
        if (req.getBaselineValue() != null)      cur.setBaselineValue(req.getBaselineValue());
        if (req.getBaselineYear() != null)       cur.setBaselineYear(req.getBaselineYear());
        if (req.getCurrentValue() != null)       cur.setCurrentValue(req.getCurrentValue());
        if (req.getCurrentValueAsOf() != null)   cur.setCurrentValueAsOf(req.getCurrentValueAsOf());
        if (req.getTargetValue() != null)        cur.setTargetValue(req.getTargetValue());
        if (req.getTargetYear() != null)         cur.setTargetYear(req.getTargetYear());
        if (req.getUnit() != null)               cur.setUnit(req.getUnit());
        if (req.getOwnerName() != null)          cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)         cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getNextMilestoneDate() != null)  cur.setNextMilestoneDate(req.getNextMilestoneDate());
        if (req.getNextMilestoneEn() != null)    cur.setNextMilestoneEn(req.getNextMilestoneEn());
        if (req.getNextMilestoneAr() != null)    cur.setNextMilestoneAr(req.getNextMilestoneAr());
        if (req.getNotesEn() != null)            cur.setNotesEn(req.getNotesEn());
        if (req.getNotesAr() != null)            cur.setNotesAr(req.getNotesAr());
        if (req.getEvidenceRef() != null)        cur.setEvidenceRef(req.getEvidenceRef());
        // SGI-A — stamp the last editor from the principal so Rule C can block a
        // subsequent self-achievement by whoever last touched the numbers.
        cur.setUpdatedBy(CurrentActor.username());
        validate(cur);
        SgiCommitment saved = repo.save(cur);
        audit.log("UPDATE", "SgiCommitment", saved.getId(), "Updated SGI commitment");
        maybeAutoCapaBehindPlan(saved);
        return saved;
    }

    /**
     * Mark a commitment as achieved — a disclosure-grade governance action
     * (APPROVE-guarded at the controller). Achieving an externally-reportable
     * pledge is gated on four rules:
     * <ul>
     *   <li><b>Rule A</b> — the current value must have met the target within
     *       the commitment's direction (raise-to vs lower-to).</li>
     *   <li><b>Rule C (SGI-A, SoD)</b> — the achiever must differ from the last
     *       person who wrote the commitment ({@code updated_by}, falling back to
     *       {@code created_by}); a 409 otherwise. PLATFORM_ADMIN bypasses per
     *       platform convention (mirrors the SoD-scan exclusion).</li>
     *   <li><b>Rule D (SGI-A, evidence gate — mirrors GHG Rule 2)</b> — achieving
     *       requires attached proof: an {@code evidence_item_id} FK or a non-blank
     *       {@code evidence_ref}.</li>
     * </ul>
     * The achiever is stamped from the authenticated principal, never a
     * request-body name (audit-Critical actor-from-principal pattern).
     */
    @Transactional
    public SgiCommitment achieve(Long id) {
        SgiCommitment cur = get(id);
        if (!"ACTIVE".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "ACHIEVED", Set.of());
        }
        if (cur.getCurrentValue() == null || cur.getTargetValue() == null) {
            throw new IllegalArgumentException("Cannot achieve a commitment without current_value and target_value.");
        }
        // Rule A — target must be met within direction.
        int dir = cur.getTargetValue().compareTo(nz(cur.getBaselineValue()));
        boolean met = (dir >= 0)
            ? cur.getCurrentValue().compareTo(cur.getTargetValue()) >= 0   // raise-to
            : cur.getCurrentValue().compareTo(cur.getTargetValue()) <= 0;  // lower-to
        if (!met) {
            throw new IllegalArgumentException(
                "Current value has not met the target yet (Rule A — direction-aware comparison).");
        }
        String achiever = CurrentActor.username();
        // Rule C — segregation of duties: whoever last wrote the numbers cannot
        // also declare the pledge met. IllegalStateException maps to 409.
        String lastEditor = cur.getUpdatedBy() != null ? cur.getUpdatedBy() : cur.getCreatedBy();
        if (!CurrentActor.isPlatformAdmin() && lastEditor != null && lastEditor.equals(achiever)) {
            throw new IllegalStateException(
                "Self-approval blocked: the achiever must differ from the last editor (SoD).");
        }
        // Rule D — no public "target met" claim without attached evidence.
        if (cur.getEvidenceItemId() == null
                && (cur.getEvidenceRef() == null || cur.getEvidenceRef().isBlank())) {
            throw new IllegalArgumentException(
                "Achieving a commitment requires evidence (Rule D): attach an evidence item (evidenceItemId) or set evidence_ref.");
        }
        cur.setStatus("ACHIEVED");
        cur.setAchievedAt(LocalDateTime.now());
        cur.setAchievedBy(achiever);
        SgiCommitment saved = repo.save(cur);
        audit.log("TRANSITION", "SgiCommitment", saved.getId(), "SGI commitment ACHIEVED by " + achiever);
        return saved;
    }

    /**
     * Retire a commitment (APPROVE-guarded). Rule B enforces that a reason is
     * recorded so future reporting cycles can explain the change; the retirer is
     * stamped from the authenticated principal, not a request-body name.
     */
    @Transactional
    public SgiCommitment retire(Long id, String reason) {
        SgiCommitment cur = get(id);
        if (!"ACTIVE".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "RETIRED", Set.of());
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Retiring a commitment requires a reason (Rule B).");
        }
        String retirer = CurrentActor.username();
        cur.setStatus("RETIRED");
        cur.setRetiredAt(LocalDateTime.now());
        cur.setRetiredBy(retirer);
        cur.setRetiredReason(reason);
        SgiCommitment saved = repo.save(cur);
        audit.log("TRANSITION", "SgiCommitment", saved.getId(), "SGI commitment RETIRED by " + retirer);
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        SgiCommitment cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "SgiCommitment", id, "Soft-deleted SGI commitment");
    }

    // ── Progress computation ─────────────────────────────────────────

    /**
     * Computed progress + tier for one commitment. Returned shape:
     * {@code { progressPct, expectedPct, tier, direction } }.
     */
    public Map<String, Object> progress(SgiCommitment c) {
        // S-B — the AT_RISK progress factor is tenant-configurable via
        // OrganisationSettings (default 0.70 lives in the settings schema).
        return progress(c, settingsService.getOrCreate().getSgiAtRiskProgressFactor());
    }

    /**
     * {@code atRiskFactor} is injected by the caller so a batch rollup
     * ({@link #aggregate()}) reads settings once rather than once per row.
     */
    private Map<String, Object> progress(SgiCommitment c, BigDecimal atRiskFactor) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal baseline = nz(c.getBaselineValue());
        BigDecimal current  = nz(c.getCurrentValue());
        BigDecimal target   = nz(c.getTargetValue());

        // Direction: raise-to-target if target > baseline; lower-to-target otherwise.
        String direction = target.compareTo(baseline) >= 0 ? "INCREASE" : "DECREASE";

        BigDecimal progressPct;
        if (target.compareTo(baseline) == 0) {
            // Trivial case — baseline already at target.
            progressPct = current.compareTo(target) == 0 ? new BigDecimal("100.0") : BigDecimal.ZERO;
        } else if ("INCREASE".equals(direction)) {
            // (current - baseline) / (target - baseline)
            BigDecimal num = current.subtract(baseline);
            BigDecimal den = target.subtract(baseline);
            progressPct = pct(num, den);
        } else {
            // (baseline - current) / (baseline - target)
            BigDecimal num = baseline.subtract(current);
            BigDecimal den = baseline.subtract(target);
            progressPct = pct(num, den);
        }

        // Expected progress at today's date on a linear baseline→target
        // timeline. If baseline_year missing we assume the year before
        // first reporting cycle (2025) which gives a workable floor.
        Short by = c.getBaselineYear() != null ? c.getBaselineYear() : 2025;
        Short ty = c.getTargetYear();
        int currentYear = LocalDate.now().getYear();
        BigDecimal expectedPct;
        if (ty <= by) {
            expectedPct = new BigDecimal("100.0");
        } else if (currentYear >= ty) {
            expectedPct = new BigDecimal("100.0");
        } else if (currentYear <= by) {
            expectedPct = BigDecimal.ZERO;
        } else {
            expectedPct = new BigDecimal((currentYear - by) * 100.0 / (ty - by))
                .setScale(1, RoundingMode.HALF_UP);
        }

        // Tier — ON_TRACK / AT_RISK / DELAYED.
        String tier;
        if ("ACHIEVED".equals(c.getStatus())) {
            tier = "ACHIEVED";
        } else if ("RETIRED".equals(c.getStatus())) {
            tier = "RETIRED";
        } else {
            BigDecimal warningFloor = expectedPct.multiply(atRiskFactor);
            if (progressPct.compareTo(expectedPct) >= 0)      tier = "ON_TRACK";
            else if (progressPct.compareTo(warningFloor) >= 0) tier = "AT_RISK";
            else                                                tier = "DELAYED";
        }

        out.put("progressPct", progressPct);
        out.put("expectedPct", expectedPct);
        out.put("tier", tier);
        out.put("direction", direction);
        return out;
    }

    /** Whole-org rollup — counts per tier + per sector + per status. */
    public Map<String, Object> aggregate() {
        // S-B — read the configurable AT_RISK factor once for the whole rollup.
        BigDecimal atRiskFactor = settingsService.getOrCreate().getSgiAtRiskProgressFactor();
        List<SgiCommitment> all = repo.findByOrganisationIdAndStatus(org(), "ACTIVE");
        Map<String, Integer> byTier = new LinkedHashMap<>();
        Map<String, Integer> bySector = new LinkedHashMap<>();
        for (String t : List.of("ON_TRACK", "AT_RISK", "DELAYED")) byTier.put(t, 0);
        for (SgiCommitment c : all) {
            Map<String, Object> p = progress(c, atRiskFactor);
            String tier = (String) p.get("tier");
            byTier.merge(tier, 1, Integer::sum);
            bySector.merge(c.getSector(), 1, Integer::sum);
        }
        long achieved = repo.findByOrganisationIdAndStatus(org(), "ACHIEVED").size();
        long retired  = repo.findByOrganisationIdAndStatus(org(), "RETIRED").size();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeTotal", all.size());
        out.put("achievedTotal", achieved);
        out.put("retiredTotal", retired);
        out.put("byTier", byTier);
        out.put("bySector", bySector);
        return out;
    }

    // ── S-D — auto-CAPA when behind plan ─────────────────────────────

    /**
     * A commitment tracking DELAYED against its linear baseline→target plan is
     * a genuine slippage; idempotently open a corrective action so it is driven
     * to closure (mirrors the compliance F8 {@code AR-EVD-GAP} rule). Deduped by
     * ({@code SGI_COMMITMENT}, id) in {@link CorrectiveActionService#autoOpen}.
     * Only DELAYED triggers — ON_TRACK is fine and AT_RISK is a warning, not an
     * actionable miss. Evaluated on create / update (a write that refreshes
     * {@code current_value}); ACHIEVED / RETIRED commitments never reach here.
     */
    private void maybeAutoCapaBehindPlan(SgiCommitment c) {
        if (c.getId() == null || !"ACTIVE".equals(c.getStatus())) return;
        if (!"DELAYED".equals(progress(c).get("tier"))) return;
        capaService.autoOpen(
            "SGI_COMMITMENT", String.valueOf(c.getId()),
            "SGI commitment behind plan: " + c.getCommitmentCode(),
            "التزام المبادرة الخضراء متأخر عن الخطة: " + c.getCommitmentCode(),
            "Auto-opened by S-D: SGI commitment " + c.getCommitmentCode()
                + " (#" + c.getId() + ") is tracking DELAYED against its linear plan.",
            "HIGH");
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private void validate(SgiCommitment c) {
        if (c.getCommitmentCode() == null || c.getCommitmentCode().isBlank())
            throw new IllegalArgumentException("commitmentCode required");
        if (c.getTitleEn() == null || c.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn required");
        if (c.getSector() == null || !SECTORS.contains(c.getSector()))
            throw new IllegalArgumentException("sector must be one of " + SECTORS);
        if (!STATUSES.contains(c.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
        if (c.getTargetValue() == null)
            throw new IllegalArgumentException("targetValue required");
        if (c.getTargetYear() == null || c.getTargetYear() < 2020 || c.getTargetYear() > 2100)
            throw new IllegalArgumentException("targetYear must be 2020..2100");
        if (c.getUnit() == null || c.getUnit().isBlank())
            throw new IllegalArgumentException("unit required");
        if (c.getBaselineYear() != null && c.getTargetYear() != null
            && c.getTargetYear() < c.getBaselineYear())
            throw new IllegalArgumentException("targetYear must be >= baselineYear");
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** num/den * 100 — guards against divide-by-zero and caps at 150 so a
     *  runaway over-achievement doesn't break dashboard rendering. */
    private static BigDecimal pct(BigDecimal num, BigDecimal den) {
        if (den.signum() == 0) return BigDecimal.ZERO;
        BigDecimal raw = num.multiply(new BigDecimal("100"))
            .divide(den, 1, RoundingMode.HALF_UP);
        if (raw.signum() < 0) return BigDecimal.ZERO;
        BigDecimal cap = new BigDecimal("150.0");
        return raw.compareTo(cap) > 0 ? cap : raw;
    }
}
