package com.tss.grc.module.sust.sgi.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.sust.metrics.entity.EsgMetricReading;
import com.tss.grc.module.sust.metrics.repository.EsgMetricReadingRepository;
import com.tss.grc.module.platform.export.TabularExportRequest;
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

    /**
     * F2-182 — {@code value_source} is the lineage switch: {@code MANUAL} means a human owns the
     * number, and {@link #refreshValue}/{@link #refreshSourcedValueQuietly} both gate on
     * {@code "MANUAL".equals(…)}. Create was closed by a {@code @Pattern} on {@code SgiCreateRequest};
     * <b>update was not</b> — {@code PUT /{id}} binds the entity with no {@code @Valid} at all, so
     * {@code valueSource: "manual"} reached the column (which {@code ck_sgi_value_source} accepts,
     * being case-insensitive) and both guards then read it as sourced rather than manual.
     *
     * <p>⚙ Honest note on the harm, because it is smaller than it looks and the reason is reusable:
     * {@code computeSourcedValue} switches on the <em>same</em> string, case-sensitively, and returns
     * empty for {@code 'manual'} — so the quiet sweep no-ops and the endpoint still refuses, just with
     * the wrong message ("no source data available" instead of "has no linked source"). <b>A second
     * case-sensitive comparison downstream accidentally neutralised the first one's defect.</b> That
     * is luck, not a design, and it evaporates the moment anyone adds a {@code default} branch.
     *
     * <p>The vocabulary now lives here rather than on the DTO, so it is the single authority for both
     * paths and {@code ClosedVocabularyPinTest} can check it against the migration — which a
     * {@code @Pattern} regex never was. The DTO's annotation is removed with this change: keeping both
     * would leave create refusing a casing that update accepts.
     */
    static final ClosedVocabulary VALUE_SOURCE = ClosedVocabulary.of(
        "valueSource", "ck_sgi_value_source", "MANUAL", "ESG_METRIC", "GHG_TOTAL");

    /** The lineage value that means "a human maintains this number" — the guard both refreshes read. */
    static final String VALUE_SOURCE_MANUAL = "MANUAL";

    private final SgiCommitmentRepository repo;
    private final OrganisationSettingsService settingsService;
    private final AuditLogService audit;
    private final CorrectiveActionService capaService;
    private final EsgMetricReadingRepository metricReadings;   // SGI-C — ESG_METRIC source
    private final GhgEmissionRecordRepository ghgRecords;      // SGI-C — GHG_TOTAL source
    private final EvidenceItemRepository evidenceItems;        // F2-362 — Rule D usable-evidence check

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
        // F2-182 — normalise here rather than on the DTO, so the entity-bound path is closed too.
        req.setValueSource(VALUE_SOURCE.orDefault(req.getValueSource(), VALUE_SOURCE_MANUAL));
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
        // SGI-C — source lineage (a MANUAL commitment stays fully hand-edited).
        // F2-182 — this was the open writer: PUT binds the entity with no @Valid, so the DTO's
        // @Pattern never ran on this path and 'manual' reached a case-insensitive column.
        if (req.getValueSource() != null)        cur.setValueSource(VALUE_SOURCE.require(req.getValueSource()));
        if (req.getSourceMetricId() != null)     cur.setSourceMetricId(req.getSourceMetricId());
        if (req.getSourceScope() != null)        cur.setSourceScope(req.getSourceScope());
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
        // Rule D — no public "target met" claim without USABLE evidence (F2-362).
        requireRuleDEvidence(cur);
        cur.setStatus("ACHIEVED");
        cur.setAchievedAt(LocalDateTime.now());
        cur.setAchievedBy(achiever);
        SgiCommitment saved = repo.save(cur);
        audit.log("TRANSITION", "SgiCommitment", saved.getId(), "SGI commitment ACHIEVED by " + achiever);
        return saved;
    }

    /**
     * Rule D — a Saudi Green Initiative commitment may not be publicly declared ACHIEVED
     * ("target met") without USABLE evidence.
     *
     * <p>F2-362 (sibling closure of F2-243) — this used to ask {@code getEvidenceItemId() == null},
     * a PRESENCE test. A presence test on a foreign key is not a check on the thing it names, so the
     * gate was discharged by any non-null id {@code FK_sgi_evidence_item} (V250, → {@code
     * evidence.evidence_items}) would accept — including a REJECTED, EXPIRED, DRAFT or PENDING_REVIEW
     * item, i.e. the evidence module's own rejected output. Its GHG twin
     * ({@link GhgEmissionRecordService#requireRule2Evidence}, same module, same V250 FK) was fixed by
     * F2-243 and this one was left (F2-215's two-of-N). F2-243 measured 51 of 78 live evidence items
     * NOT APPROVED (31 EXPIRED / 15 PENDING_REVIEW / 4 DRAFT / 1 REJECTED, dev = prod), so the
     * population that discharged this gate is real.
     *
     * <p>The state is checked HERE, at consumption, not when the item was attached, because an item
     * can be APPROVED on the day it is linked and EXPIRED by the time the pledge is declared met. The
     * resolution is {@code module/evidence}'s own — via the same
     * {@link EvidenceItemRepository#findUsableAsEvidence} predicate GHG uses (F2-282: one predicate,
     * N readers; a caller cannot invent its own idea of what counts as evidence, F2-196).
     *
     * <p>⚠ The legacy free-text {@code evidence_ref} branch is deliberately untouched (F2-182): a
     * request naming an external assurance statement rather than a platform evidence item keeps
     * working; only the {@code evidenceItemId} path is tightened to require a usable item, and a
     * request that discharges a public "target met" claim with a rejected item is not one that
     * "works today".
     */
    private void requireRuleDEvidence(SgiCommitment cur) {
        String ref = cur.getEvidenceRef();
        if (ref != null && !ref.isBlank()) return;              // legacy free-text branch (F2-182)
        Long itemId = cur.getEvidenceItemId();
        if (itemId == null) {
            throw new IllegalArgumentException(
                "Achieving a commitment requires evidence (Rule D): attach an evidence item (evidenceItemId) or set evidence_ref.");
        }
        evidenceItems.findUsableAsEvidence(itemId, org(), LocalDate.now()).orElseThrow(() -> {
            // Name the state, so the caller knows whether to chase a review or a re-collection
            // (F2-180: a diagnostic that names the column is worth more than one that is merely correct).
            String state = evidenceItems.findByIdAndOrganisationId(itemId, org())
                .map(e -> "status=" + e.getStatus()
                          + (e.getValidTo() == null ? "" : ", validTo=" + e.getValidTo()))
                .orElse("not found in this organisation");
            return new IllegalArgumentException(
                "Achieving a commitment requires evidence (Rule D): evidence item " + itemId
                + " cannot support a public 'target met' claim (" + state + "). It must be "
                + EvidenceItem.APPROVED + " and still in date — have it reviewed at POST "
                + "/api/v1/evidence/reviews, or attach a current item instead.");
        });
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

    // ── SGI-C — current_value source lineage ─────────────────────────

    /** value + as-of pulled from a linked source. */
    private record SourcedValue(BigDecimal value, LocalDate asOf) {}

    /**
     * Resolve the latest value for a <em>sourced</em> commitment:
     * <ul>
     *   <li>{@code ESG_METRIC} — the newest reading of {@code source_metric_id}
     *       ({@code reading_value} + its {@code as_of}).</li>
     *   <li>{@code GHG_TOTAL} — the sum of VERIFIED/APPROVED GHG records for the
     *       latest year that has any (optionally narrowed to {@code source_scope});
     *       as-of is that year-end.</li>
     * </ul>
     * Empty for {@code MANUAL} or when the source has no data yet.
     */
    private Optional<SourcedValue> computeSourcedValue(SgiCommitment c) {
        String src = c.getValueSource();
        if ("ESG_METRIC".equals(src) && c.getSourceMetricId() != null) {
            List<EsgMetricReading> readings =
                metricReadings.findByOrganisationIdAndMetricIdOrderByAsOfDescIdDesc(org(), c.getSourceMetricId());
            if (readings.isEmpty()) return Optional.empty();
            EsgMetricReading latest = readings.get(0);
            return Optional.of(new SourcedValue(latest.getReadingValue(), latest.getAsOf()));
        }
        if ("GHG_TOTAL".equals(src)) {
            List<GhgEmissionRecord> recs =
                ghgRecords.findByOrganisationIdAndStatusIn(org(), List.of("VERIFIED", "APPROVED"));
            if (c.getSourceScope() != null) {
                short sc = c.getSourceScope();
                recs = recs.stream().filter(g -> g.getScope() != null && g.getScope() == sc).toList();
            }
            if (recs.isEmpty()) return Optional.empty();
            short maxYear = recs.stream().map(GhgEmissionRecord::getReportingYear)
                .filter(Objects::nonNull).max(Short::compareTo).orElse((short) 0);
            BigDecimal sum = recs.stream()
                .filter(g -> g.getReportingYear() != null && g.getReportingYear() == maxYear)
                .map(GhgEmissionRecord::getEmissionValueTco2e)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return Optional.of(new SourcedValue(sum, LocalDate.of(maxYear, 12, 31)));
        }
        return Optional.empty();
    }

    /**
     * SGI-C — pull {@code current_value} from the linked source on demand
     * (SUST WRITE). A refresh changes the numbers, so it stamps the principal as
     * the last editor (feeding Rule C) and, like any current-value write, opens a
     * behind-plan auto-CAPA if the refreshed figure is DELAYED. MANUAL commitments
     * (or sources with no data yet) are rejected with a clear message.
     */
    @Transactional
    public SgiCommitment refreshValue(Long id) {
        SgiCommitment c = get(id);
        if (!"ACTIVE".equals(c.getStatus())) {
            throw new IllegalStateException(
                "Commitment is " + c.getStatus() + " and cannot be refreshed.");
        }
        // ClosedVocabulary#is, not equals, and deliberately: this reads a row that may have been
        // written before the writer above was closed, which is the one use its javadoc sanctions.
        if (c.getValueSource() == null || VALUE_SOURCE.is(c.getValueSource(), VALUE_SOURCE_MANUAL)) {
            throw new IllegalArgumentException(
                "Commitment " + c.getCommitmentCode() + " has no linked source (value_source=MANUAL).");
        }
        SourcedValue sv = computeSourcedValue(c).orElseThrow(() -> new IllegalArgumentException(
            "No source data available to refresh " + c.getCommitmentCode()
                + " (source " + c.getValueSource() + ")."));
        BigDecimal old = c.getCurrentValue();
        c.setCurrentValue(sv.value());
        c.setCurrentValueAsOf(sv.asOf());
        c.setUpdatedBy(CurrentActor.username());
        SgiCommitment saved = repo.save(c);
        audit.log("UPDATE", "SgiCommitment", saved.getId(),
            "current_value refreshed from " + c.getValueSource() + ": " + old + " -> " + sv.value()
                + " (as of " + sv.asOf() + ")");
        maybeAutoCapaBehindPlan(saved);
        return saved;
    }

    /**
     * SGI-B/SGI-C — used by the surveillance sweep to refresh a sourced value
     * <em>before</em> judging its tier, so an untouched-but-sourced commitment is
     * assessed on live data. Persists value + as-of only; it deliberately does
     * NOT re-stamp {@code updated_by} (no principal off-request, and a background
     * refresh must not overwrite the SoD attribution). No-op for MANUAL or when
     * the value is already current.
     */
    @Transactional
    public void refreshSourcedValueQuietly(SgiCommitment c) {
        // Same reasoning as refreshValue: a legacy row's casing must not turn a hand-maintained
        // commitment into one this background sweep believes it may overwrite.
        if (c.getValueSource() == null || VALUE_SOURCE.is(c.getValueSource(), VALUE_SOURCE_MANUAL)) return;
        computeSourcedValue(c).ifPresent(sv -> {
            boolean changed = sv.value().compareTo(nz(c.getCurrentValue())) != 0
                || !Objects.equals(sv.asOf(), c.getCurrentValueAsOf());
            if (changed) {
                c.setCurrentValue(sv.value());
                c.setCurrentValueAsOf(sv.asOf());
                repo.save(c);
            }
        });
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

    // ── SGI-E — per-register XLSX export (via the shared tabular engine) ──

    /**
     * Build the SGI commitment register as one tabular sheet for the branded
     * XLSX export (XlsxStyler + BrandTheme + ExportNaming + export audit). One
     * row per commitment (all statuses), with the computed tier + progress.
     */
    @Transactional(readOnly = true)
    public TabularExportRequest exportPayload(String lang) {
        boolean ar = "ar".equalsIgnoreCase(lang);
        BigDecimal atRiskFactor = settingsService.getOrCreate().getSgiAtRiskProgressFactor();
        List<List<String>> rows = new ArrayList<>();
        for (SgiCommitment c : repo
                .findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(org(), Pageable.unpaged())
                .getContent()) {
            Map<String, Object> p = progress(c, atRiskFactor);
            rows.add(List.of(
                nn(c.getCommitmentCode()),
                nn(c.getSector()),
                ar && c.getTitleAr() != null ? c.getTitleAr() : nn(c.getTitleEn()),
                nn(c.getUnit()),
                str(c.getBaselineValue()),
                str(c.getCurrentValue()),
                str(c.getTargetValue()),
                c.getTargetYear() == null ? "" : String.valueOf(c.getTargetYear()),
                nn(c.getStatus()),
                String.valueOf(p.get("tier")),
                String.valueOf(p.get("progressPct")),
                nn(c.getOwnerName()),
                nn(c.getValueSource())));
        }
        List<String> headers = ar
            ? List.of("الكود", "القطاع", "العنوان", "الوحدة", "خط الأساس", "الحالي", "الهدف",
                      "سنة الهدف", "الحالة", "التصنيف", "التقدّم %", "المالك", "المصدر")
            : List.of("Code", "Sector", "Title", "Unit", "Baseline", "Current", "Target",
                      "Target year", "Status", "Tier", "Progress %", "Owner", "Source");
        String sheet = ar ? "سجل التزامات المبادرة الخضراء" : "SGI Commitment Register";
        return new TabularExportRequest("SGI", "CommitmentRegister", lang,
            List.of(new TabularExportRequest.Sheet(sheet, headers, rows)));
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

    /** null-safe string for export cells. */
    private static String nn(String s) { return s == null ? "" : s; }
    private static String str(BigDecimal v) { return v == null ? "" : v.toPlainString(); }

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
