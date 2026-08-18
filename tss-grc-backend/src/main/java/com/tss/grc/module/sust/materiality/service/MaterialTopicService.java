package com.tss.grc.module.sust.materiality.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.materiality.entity.MaterialTopic;
import com.tss.grc.module.sust.materiality.repository.MaterialTopicRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ESG materiality service. Implements true CSRD/ESRS double-materiality
 * scoring on two INDEPENDENT axes (S-Q):
 * <pre>
 *   impact_materiality    (inside-out)  — the org's impact on people/environment
 *   financial_materiality (outside-in)  — the topic's effect on the org's finances
 *   materiality_score = MAX(impact, financial)   // material if material from EITHER axis
 *   priority_tier = bucket(materiality_score):
 *     CRITICAL  ≥ 4.0
 *     HIGH      ≥ 3.0   (= MATERIAL_THRESHOLD: a topic is "material" on an axis ≥ this)
 *     MEDIUM    ≥ 2.0
 *     LOW       < 2.0
 * </pre>
 *
 * <p>Recomputes score + tier on every save so the entity never goes
 * stale relative to its scoring inputs.
 *
 * <p>Status machine:
 * <pre>
 *   DRAFT → SCORED → APPROVED → ARCHIVED
 *   any   → ARCHIVED  (terminal)
 * </pre>
 *
 * <p>Transition rules:
 *   • SCORED requires both scoring axes > 0.
 *   • APPROVED requires SCORED first (must have been scored).
 */
@Service
@RequiredArgsConstructor
public class MaterialTopicService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "SCORED", "APPROVED", "ARCHIVED");
    private static final Set<String> CATEGORIES = Set.of("ENVIRONMENTAL", "SOCIAL", "GOVERNANCE");
    /** A topic is material on an axis when that axis scores at/above this (the HIGH-tier floor). */
    private static final BigDecimal MATERIAL_THRESHOLD = new BigDecimal("3.0");

    /**
     * F2-237 — the declared range of both double-materiality axes, as ONE rule with several
     * readers rather than a copy per write path.
     *
     * <p>⚠⚠ {@code score()} refused anything outside 0..5 and {@code create()}/{@code update()}
     * wrote both axes raw, so the range was a property of one route rather than of the column.
     * The database says the same thing about half the columns and not the other half:
     * {@code ck_mt_stake_score} and {@code ck_mt_biz_score} bound the LEGACY mirror pair 0..5 and
     * there was no constraint at all on the canonical {@code impact_materiality} /
     * {@code financial_materiality} — F2-215's two-of-N shape, at the schema. V379 adds the two
     * that were missing, and this pair is what the service checks so the two cannot drift.
     *
     * <p>🔑 The pattern was already in the tree, in the sibling artefact:
     * {@code NdiAssessmentCycleService.validate()} carries its own range rule
     * (<i>"reportingYear must be 2020..2100"</i>) so no write path can miss it, while
     * {@code MaterialTopicService.validate()} — right here — checked the topic name, the category,
     * the reporting year and the status, and not the two axes the whole register exists to record.
     */
    private static final BigDecimal AXIS_MIN = BigDecimal.ZERO;
    private static final BigDecimal AXIS_MAX = new BigDecimal("5");

    private final MaterialTopicRepository repo;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    public Page<MaterialTopic> listByYear(Short year, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearOrderByMaterialityScoreDescIdAsc(org(), year, p);
    }
    public Page<MaterialTopic> listByYearAndCategory(Short year, String cat, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearAndCategoryOrderByMaterialityScoreDescIdAsc(org(), year, cat, p);
    }
    public Page<MaterialTopic> listByYearAndStatus(Short year, String status, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearAndStatusOrderByMaterialityScoreDescIdAsc(org(), year, status, p);
    }
    public MaterialTopic get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Material topic " + id));
    }

    @Transactional
    public MaterialTopic create(MaterialTopic req) {
        req.setId(null);
        req.setOrganisationId(org());
        // Mass-assignment guard (SEC-ESG-003): force the initial lifecycle
        // state (scoring/approval happen only via score()/approve()) and
        // scrub approval attribution + audit columns so a topic cannot be
        // born APPROVED with a forged approver.
        req.setStatus("DRAFT");
        req.setApprovedBy(null);
        req.setApprovedAt(null);
        req.setCreatedBy(null);
        req.setUpdatedBy(null);
        // F2-237 — the axes go through the one writer, so a created topic cannot carry a score
        // outside the declared range or a legacy mirror that disagrees with it. The canonical axis
        // wins where a caller supplies both (MaterialTopicCreateRequest already resolves the other
        // direction: an omitted canonical value falls back to the legacy field).
        applyAxes(req, req.getImpactMateriality(), req.getFinancialMateriality());
        validate(req);
        MaterialTopic saved = repo.save(req);
        audit.log("CREATE", "MaterialTopic", saved.getId(),
                "Material topic created");
        return saved;
    }

    @Transactional
    public MaterialTopic update(Long id, MaterialTopic req) {
        MaterialTopic cur = get(id);
        if ("ARCHIVED".equals(cur.getStatus())) {
            throw new IllegalStateException("Topic is ARCHIVED and cannot be modified.");
        }
        BigDecimal beforeScore = cur.getMaterialityScore();
        String beforeTier = cur.getPriorityTier();
        if (req.getCategory() != null)               cur.setCategory(req.getCategory());
        if (req.getTopicEn() != null)                cur.setTopicEn(req.getTopicEn());
        if (req.getTopicAr() != null)                cur.setTopicAr(req.getTopicAr());
        if (req.getDescriptionEn() != null)          cur.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null)          cur.setDescriptionAr(req.getDescriptionAr());
        if (req.getStakeholderGroups() != null)      cur.setStakeholderGroups(req.getStakeholderGroups());
        if (req.getLinkedCmaDisclosureId() != null)  cur.setLinkedCmaDisclosureId(req.getLinkedCmaDisclosureId());
        if (req.getLinkedClimateRiskId() != null)    cur.setLinkedClimateRiskId(req.getLinkedClimateRiskId());
        if (req.getLinkedCapaId() != null)           cur.setLinkedCapaId(req.getLinkedCapaId());
        if (req.getEvidenceRef() != null)            cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getOwnerName() != null)              cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)             cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getNotes() != null)                  cur.setNotes(req.getNotes());
        // ⚠⚠ F2-237 — THE FOUR SCORE COLUMNS ARE DELIBERATELY NOT WRITTEN HERE, and the reason is
        // that the null-guard which used to write them COULD NEVER FIRE.
        //
        // This route binds the ENTITY, and MaterialTopic declares
        //     private BigDecimal impactMateriality = BigDecimal.ZERO;
        // on all four score columns (they are NOT NULL). So a PUT that omits the key deserialises
        // to ZERO, not to null — `if (req.getImpactMateriality() != null)` was true on every single
        // request, and the line READ as a partial update while BEHAVING as a full replace. Probed
        // live: a PUT carrying only topicEn and category zeroed a CRITICAL topic's axes, and
        // recomputeScore then took materiality_score to 0.0 and priority_tier to LOW — a CMA
        // Article 92(a) double-materiality input, destroyed by an edit to an unrelated field.
        //
        // 🔑 The frontend was carrying the workaround: EsgMaterialityTab's edit handler echoes both
        // axes back with a comment explaining that "an omitted value would zero out the score +
        // tier". A defence in one client is not a defence — the next caller does not have it.
        //
        // The axes are owned by create() and by score(), which is what this register already says
        // out loud ("Scoring is owned by the dedicated Score action"). An edit route that cannot
        // write them cannot zero them, and the legacy mirror cannot be driven apart from the
        // canonical pair either, because applyAxes is then the only writer of all four.
        validate(cur);
        recomputeScore(cur);
        MaterialTopic saved = repo.save(cur);
        // 🔑 F2-188 — recomputeScore runs on every save, so an edit to a topic's owner name can
        // move its materiality score and its priority tier. The old line read "Material topic
        // updated" whatever moved: a duplicated derivation agrees with the original on the row and
        // differs on the audit trail, which is the part nothing asserts. Say what moved.
        audit.log("UPDATE", "MaterialTopic", saved.getId(),
                "Material topic updated" + derivedMove(beforeScore, beforeTier, saved));
        return saved;
    }

    /**
     * The ONE writer of the double-materiality axis pair.
     *
     * <p>Three routes wrote these columns and only {@code score()} enforced the declared range,
     * kept the legacy mirror in step and recomputed the derived values. Everything that writes an
     * axis now comes through here, so the range, the mirror and the derivation are properties of
     * the field rather than of whichever route the caller reached.
     *
     * <p>⚠ The mirror matters because {@code stakeholder_importance} / {@code business_impact} are
     * the same two facts under their pre-S-Q names, read by the legacy ESG surfaces and by the
     * frontend's own {@code impactMateriality ?? stakeholderImportance} fallback. {@code update()}
     * wrote all four independently, so one row could carry an impact of 5 and a stakeholder
     * importance of 1 — F2-196's disjoint-vocabulary shape inside a single row. Measured 2026-08-18:
     * 0 rows disagree in dev or prod, so this was latent.
     */
    private void applyAxes(MaterialTopic t, BigDecimal impact, BigDecimal financial) {
        t.setImpactMateriality(requireAxis(impact, "impactMateriality"));
        t.setFinancialMateriality(requireAxis(financial, "financialMateriality"));
        t.setStakeholderImportance(t.getImpactMateriality());   // legacy mirror — never a 2nd writer
        t.setBusinessImpact(t.getFinancialMateriality());
        recomputeScore(t);
    }

    /**
     * A double-materiality axis is 0..5, and saying so names the field.
     *
     * <p>{@code require}, not {@code coerce} (F2-182's test: what already refuses this?). The
     * frontend gates 0..5 in the create form, the edit form and the score dialog, so no request a
     * human can make through the platform starts failing — which is exactly why the hole was only
     * reachable by a machine caller, and machine callers are where this family of defect lives
     * (F2-179). Clamping instead would silently record a materiality score nobody chose.
     */
    private static BigDecimal requireAxis(BigDecimal v, String field) {
        if (v == null) {
            throw new IllegalArgumentException(field + " is required (0..5)");
        }
        if (v.compareTo(AXIS_MIN) < 0 || v.compareTo(AXIS_MAX) > 0) {
            throw new IllegalArgumentException(
                field + " must be between " + AXIS_MIN.toPlainString() + " and "
                    + AXIS_MAX.toPlainString() + " — got " + v.toPlainString());
        }
        return v;
    }

    /** "" when neither derived value moved, else what moved — for the audit line. */
    private static String derivedMove(BigDecimal beforeScore, String beforeTier, MaterialTopic after) {
        boolean sameScore = beforeScore != null && after.getMaterialityScore() != null
            && beforeScore.compareTo(after.getMaterialityScore()) == 0;
        boolean sameTier = Objects.equals(beforeTier, after.getPriorityTier());
        if (sameScore && sameTier) return "";
        return " · materiality " + str(beforeScore) + " → " + str(after.getMaterialityScore())
            + " (" + beforeTier + " → " + after.getPriorityTier() + ")";
    }

    private static String str(BigDecimal v) { return v == null ? "—" : v.toPlainString(); }

    /**
     * Score a topic on the two double-materiality axes. Transitions
     * DRAFT → SCORED and recomputes materiality_score = max(impact, financial)
     * + priority tier. The legacy stakeholder/business columns are mirrored so
     * any legacy reader stays consistent.
     *
     * @param impact    impact materiality (inside-out), 0..5
     * @param financial financial materiality (outside-in), 0..5
     */
    @Transactional
    public MaterialTopic score(Long id, BigDecimal impact, BigDecimal financial) {
        MaterialTopic cur = get(id);
        if (impact == null || financial == null)
            throw new IllegalArgumentException("Both impact and financial materiality scores required.");
        // F2-237 — the range, the legacy mirror and the recompute were all HERE and nowhere else,
        // which is what made this route's guarantees a property of the route rather than of the
        // column. They now live in applyAxes, which create() and update() also go through.
        applyAxes(cur, impact, financial);
        if ("DRAFT".equals(cur.getStatus())) cur.setStatus("SCORED");
        MaterialTopic saved = repo.save(cur);
        audit.log("SCORE", "MaterialTopic", saved.getId(),
            "Scored impact=" + impact + " financial=" + financial + " → " + saved.getMaterialityScore()
                + " (" + saved.getPriorityTier() + ")");
        return saved;
    }

    @Transactional
    public MaterialTopic approve(Long id) {
        MaterialTopic cur = get(id);
        if (!"SCORED".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "APPROVED", Set.of("SCORED"));
        }
        // F2-236 — the approver is the principal. This method REQUIRED an `approvedBy` it never
        // verified (a 400 when omitted) while stamping approvedAt from its own clock: the third
        // register in this lineage to demand an attribution and then trust it.
        cur.setStatus("APPROVED");
        cur.setApprovedAt(LocalDateTime.now());
        cur.setApprovedBy(CurrentActor.requireUsername("a material-topic approval"));
        MaterialTopic saved = repo.save(cur);
        // The trail reads off the SAVED ROW, never the caller's claim (F2-223: a trail that quotes
        // the claim is what makes a forged attribution invisible to whoever audits it).
        audit.log("APPROVE", "MaterialTopic", saved.getId(),
                "Material topic approved by " + saved.getApprovedBy());
        return saved;
    }

    @Transactional
    public MaterialTopic archive(Long id) {
        MaterialTopic cur = get(id);
        cur.setStatus("ARCHIVED");
        MaterialTopic saved = repo.save(cur);
        audit.log("ARCHIVE", "MaterialTopic", saved.getId(),
                "Material topic archived");
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        MaterialTopic cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "MaterialTopic", id,
                "Material topic deleted");
    }

    /**
     * Generate the materiality matrix data for visualization. Returns:
     * {@code { year, total, byTier, byCategory, criticalCount,
     *           highCount, matrix: [...] } } where each matrix entry is
     * one APPROVED-or-SCORED topic with its two coordinates.
     */
    public Map<String, Object> generateMatrix(Short year) {
        List<MaterialTopic> all = repo.findByOrganisationIdAndReportingYear(org(), year);
        Map<String, Integer> byTier     = new LinkedHashMap<>();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (String tier : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) byTier.put(tier, 0);
        for (String cat  : CATEGORIES) byCategory.put(cat, 0);

        List<Map<String, Object>> matrix = new ArrayList<>();
        int impactMaterialCount = 0, financialMaterialCount = 0, doubleMaterialCount = 0;
        for (MaterialTopic t : all) {
            if (t.getPriorityTier() != null) byTier.merge(t.getPriorityTier(), 1, Integer::sum);
            byCategory.merge(t.getCategory(), 1, Integer::sum);
            if (!"DRAFT".equals(t.getStatus()) && !"ARCHIVED".equals(t.getStatus())) {
                // Double-materiality classification: material from the impact
                // axis, the financial axis, or both (the top-right quadrant).
                boolean fromImpact    = nz(t.getImpactMateriality()).compareTo(MATERIAL_THRESHOLD) >= 0;
                boolean fromFinancial = nz(t.getFinancialMateriality()).compareTo(MATERIAL_THRESHOLD) >= 0;
                if (fromImpact)                  impactMaterialCount++;
                if (fromFinancial)               financialMaterialCount++;
                if (fromImpact && fromFinancial) doubleMaterialCount++;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", t.getId());
                entry.put("topicCode", t.getTopicCode());
                entry.put("topicEn", t.getTopicEn());
                entry.put("topicAr", t.getTopicAr());
                entry.put("category", t.getCategory());
                entry.put("impactMateriality", t.getImpactMateriality());
                entry.put("financialMateriality", t.getFinancialMateriality());
                entry.put("stakeholderImportance", t.getStakeholderImportance());
                entry.put("businessImpact", t.getBusinessImpact());
                entry.put("materialityScore", t.getMaterialityScore());
                entry.put("priorityTier", t.getPriorityTier());
                entry.put("materialFromImpact", fromImpact);
                entry.put("materialFromFinancial", fromFinancial);
                entry.put("doubleMaterial", fromImpact && fromFinancial);
                entry.put("status", t.getStatus());
                matrix.add(entry);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("total", all.size());
        out.put("byTier", byTier);
        out.put("byCategory", byCategory);
        out.put("criticalCount", byTier.getOrDefault("CRITICAL", 0));
        out.put("highCount", byTier.getOrDefault("HIGH", 0));
        // Double-materiality quadrant rollup (over the plotted, non-DRAFT topics).
        out.put("impactMaterialCount", impactMaterialCount);
        out.put("financialMaterialCount", financialMaterialCount);
        out.put("doubleMaterialCount", doubleMaterialCount);
        out.put("materialThreshold", MATERIAL_THRESHOLD);
        out.put("matrix", matrix);
        return out;
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Double materiality — materiality_score = MAX(impact, financial): a topic
     * is as material as its stronger perspective (material if material from
     * EITHER axis), never the average that hid a strong single axis. Bucketed
     * into priority_tier. Called on every save so derived values never go stale.
     */
    private void recomputeScore(MaterialTopic t) {
        BigDecimal impact    = nz(t.getImpactMateriality());
        BigDecimal financial = nz(t.getFinancialMateriality());
        BigDecimal score = impact.max(financial).setScale(1, RoundingMode.HALF_UP);
        t.setMaterialityScore(score);

        if (score.compareTo(new BigDecimal("4.0")) >= 0)       t.setPriorityTier("CRITICAL");
        else if (score.compareTo(new BigDecimal("3.0")) >= 0)  t.setPriorityTier("HIGH");
        else if (score.compareTo(new BigDecimal("2.0")) >= 0)  t.setPriorityTier("MEDIUM");
        else                                                    t.setPriorityTier("LOW");
    }

    private void validate(MaterialTopic t) {
        if (t.getTopicEn() == null || t.getTopicEn().isBlank())
            throw new IllegalArgumentException("topicEn required");
        if (t.getCategory() == null || !CATEGORIES.contains(t.getCategory()))
            throw new IllegalArgumentException("category must be one of " + CATEGORIES);
        if (t.getReportingYear() == null)
            throw new IllegalArgumentException("reportingYear required");
        if (!STATUSES.contains(t.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
        // F2-237 — the range rule as a property of the ROW, so a future write path that forgets
        // applyAxes still cannot store a score outside the axes the register declares. This is the
        // shape NdiAssessmentCycleService.validate() already had for its reporting year, and the
        // reason it is worth having twice: applyAxes names the field for the caller, this catches
        // the writer nobody has written yet.
        requireAxis(t.getImpactMateriality(), "impactMateriality");
        requireAxis(t.getFinancialMateriality(), "financialMateriality");
        requireAxis(t.getStakeholderImportance(), "stakeholderImportance");
        requireAxis(t.getBusinessImpact(), "businessImpact");
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
