package com.tss.grc.module.sust.materiality.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
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
 * ESG materiality service. Implements the double-materiality scoring
 * algorithm:
 * <pre>
 *   materiality_score = (stakeholder_importance + business_impact) / 2
 *   priority_tier = bucket(materiality_score):
 *     CRITICAL  ≥ 4.0
 *     HIGH      ≥ 3.0
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

    private final MaterialTopicRepository repo;

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
        if (req.getStatus() == null || req.getStatus().isBlank()) req.setStatus("DRAFT");
        validate(req);
        recomputeScore(req);
        return repo.save(req);
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
        return repo.save(cur);
    }

    /**
     * Score a topic. Transitions DRAFT → SCORED when both axes are
     * provided and recomputes the materiality score + priority tier.
     */
    @Transactional
    public MaterialTopic score(Long id, BigDecimal stakeholder, BigDecimal business) {
        MaterialTopic cur = get(id);
        if (stakeholder == null || business == null)
            throw new IllegalArgumentException("Both stakeholder and business scores required.");
        if (stakeholder.signum() < 0 || stakeholder.compareTo(new BigDecimal("5")) > 0
            || business.signum() < 0   || business.compareTo(new BigDecimal("5")) > 0)
            throw new IllegalArgumentException("Scores must be 0..5.");
        cur.setStakeholderImportance(stakeholder);
        cur.setBusinessImpact(business);
        recomputeScore(cur);
        if ("DRAFT".equals(cur.getStatus())) cur.setStatus("SCORED");
        return repo.save(cur);
    }

    @Transactional
    public MaterialTopic approve(Long id, String approvedBy) {
        MaterialTopic cur = get(id);
        if (!"SCORED".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "APPROVED", Set.of("SCORED"));
        }
        if (approvedBy == null || approvedBy.isBlank())
            throw new IllegalArgumentException("approvedBy required.");
        cur.setStatus("APPROVED");
        cur.setApprovedAt(LocalDateTime.now());
        cur.setApprovedBy(approvedBy);
        return repo.save(cur);
    }

    @Transactional
    public MaterialTopic archive(Long id) {
        MaterialTopic cur = get(id);
        cur.setStatus("ARCHIVED");
        return repo.save(cur);
    }

    @Transactional
    public void softDelete(Long id) {
        MaterialTopic cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
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
        for (MaterialTopic t : all) {
            if (t.getPriorityTier() != null) byTier.merge(t.getPriorityTier(), 1, Integer::sum);
            byCategory.merge(t.getCategory(), 1, Integer::sum);
            if (!"DRAFT".equals(t.getStatus()) && !"ARCHIVED".equals(t.getStatus())) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", t.getId());
                entry.put("topicCode", t.getTopicCode());
                entry.put("topicEn", t.getTopicEn());
                entry.put("topicAr", t.getTopicAr());
                entry.put("category", t.getCategory());
                entry.put("stakeholderImportance", t.getStakeholderImportance());
                entry.put("businessImpact", t.getBusinessImpact());
                entry.put("materialityScore", t.getMaterialityScore());
                entry.put("priorityTier", t.getPriorityTier());
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
        out.put("matrix", matrix);
        return out;
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Compute materiality_score = (stakeholder + business) / 2, then
     * bucket into priority_tier. Called on every save so the entity
     * never stores stale derived values.
     */
    private void recomputeScore(MaterialTopic t) {
        BigDecimal s = nz(t.getStakeholderImportance());
        BigDecimal b = nz(t.getBusinessImpact());
        BigDecimal score = s.add(b).divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP);
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
