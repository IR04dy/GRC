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
        validate(req);
        recomputeScore(req);
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
        if (req.getCategory() != null)               cur.setCategory(req.getCategory());
        if (req.getTopicEn() != null)                cur.setTopicEn(req.getTopicEn());
        if (req.getTopicAr() != null)                cur.setTopicAr(req.getTopicAr());
        if (req.getDescriptionEn() != null)          cur.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null)          cur.setDescriptionAr(req.getDescriptionAr());
        if (req.getStakeholderImportance() != null)  cur.setStakeholderImportance(req.getStakeholderImportance());
        if (req.getBusinessImpact() != null)         cur.setBusinessImpact(req.getBusinessImpact());
        if (req.getImpactMateriality() != null)      cur.setImpactMateriality(req.getImpactMateriality());
        if (req.getFinancialMateriality() != null)   cur.setFinancialMateriality(req.getFinancialMateriality());
        if (req.getStakeholderGroups() != null)      cur.setStakeholderGroups(req.getStakeholderGroups());
        if (req.getLinkedCmaDisclosureId() != null)  cur.setLinkedCmaDisclosureId(req.getLinkedCmaDisclosureId());
        if (req.getLinkedClimateRiskId() != null)    cur.setLinkedClimateRiskId(req.getLinkedClimateRiskId());
        if (req.getLinkedCapaId() != null)           cur.setLinkedCapaId(req.getLinkedCapaId());
        if (req.getEvidenceRef() != null)            cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getOwnerName() != null)              cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)             cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getNotes() != null)                  cur.setNotes(req.getNotes());
        validate(cur);
        recomputeScore(cur);
        MaterialTopic saved = repo.save(cur);
        audit.log("UPDATE", "MaterialTopic", saved.getId(),
                "Material topic updated");
        return saved;
    }

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
        if (impact.signum() < 0 || impact.compareTo(new BigDecimal("5")) > 0
            || financial.signum() < 0 || financial.compareTo(new BigDecimal("5")) > 0)
            throw new IllegalArgumentException("Scores must be 0..5.");
        cur.setImpactMateriality(impact);
        cur.setFinancialMateriality(financial);
        cur.setStakeholderImportance(impact);   // legacy mirror
        cur.setBusinessImpact(financial);       // legacy mirror
        recomputeScore(cur);
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
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
