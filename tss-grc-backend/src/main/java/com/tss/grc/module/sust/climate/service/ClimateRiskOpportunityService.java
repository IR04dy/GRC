package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import jakarta.persistence.EntityNotFoundException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Climate Risk &amp; Opportunity service — owns the lifecycle, derives risk
 * score from likelihood × impact on every mutation, and gate-keeps the
 * status machine.
 *
 * <p>Workflow Rule 8 (from the spec): when an item's risk score is High
 * (≥15) or Critical (=25), the controller surfaces an ERM-link suggestion
 * to the caller — the actual ERM record creation happens via the
 * {@code POST /api/sustainability/climate-risks/{id}/link-erm} endpoint
 * which the service implements by simply storing the supplied ERM risk id
 * (the cross-module hard link is left to the ERM-side service).
 */
@Service
@RequiredArgsConstructor
public class ClimateRiskOpportunityService {

    private static final Set<String> STATUSES = Set.of("IDENTIFIED","ASSESSED","TREATED","CLOSED","RETIRED");
    private static final Set<String> KINDS    = Set.of("RISK", "OPPORTUNITY");
    private static final Set<String> RISK_CATEGORIES = Set.of(
        "PHYSICAL","TRANSITION","REGULATORY","MARKET","TECHNOLOGY","REPUTATION","SUPPLY_CHAIN");
    private static final Set<String> OPP_CATEGORIES  = Set.of(
        "RESOURCE_EFFICIENCY","ENERGY_SOURCE","PRODUCT_SERVICE","MARKETS","RESILIENCE");

    private final ClimateRiskOpportunityRepository repo;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<ClimateRiskOpportunity> list(Pageable p) {
        return repo.findByOrganisationIdOrderByRiskScoreDescIdDesc(org(), p);
    }
    public Page<ClimateRiskOpportunity> listByKind(String kind, Pageable p) {
        return repo.findByOrganisationIdAndKindOrderByRiskScoreDescIdDesc(org(), kind, p);
    }
    public Page<ClimateRiskOpportunity> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByRiskScoreDescIdDesc(org(), status, p);
    }
    public List<ClimateRiskOpportunity> topByRisk() {
        return repo.findTop5ByOrganisationIdAndStatusNotOrderByRiskScoreDescIdDesc(org(), "RETIRED");
    }
    public ClimateRiskOpportunity get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Climate risk/opportunity " + id));
    }

    /**
     * Whole-org rollup. Returned shape:
     * {@code { total, byKind, byStatus, byBand, treatedRatePct,
     *           needsErmLinkCount } }.
     *
     * <p>{@code treatedRatePct} = (TREATED + CLOSED) / (total - RETIRED)
     * — the share of identified items that have moved past the active
     * pipeline. The dashboard composite score's "climate treated"
     * component reads this directly (slice 14-D replaces a placeholder).
     *
     * <p>{@code byBand} buckets risk_score into CRITICAL (≥20),
     * HIGH (15..19), MEDIUM (9..14), LOW (<9) — matching the
     * frontend's {@code riskScoreBand} client helper.
     */
    public Map<String, Object> aggregate() {
        List<ClimateRiskOpportunity> all = repo.findByOrganisationId(org());

        Map<String, Integer> byKind   = new LinkedHashMap<>();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> byBand   = new LinkedHashMap<>();
        for (String k : KINDS)    byKind.put(k, 0);
        for (String s : STATUSES) byStatus.put(s, 0);
        for (String b : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) byBand.put(b, 0);

        int needsErmLink = 0;
        int treatedOrClosed = 0;
        int retired = 0;
        for (ClimateRiskOpportunity r : all) {
            byKind.merge(r.getKind(), 1, Integer::sum);
            byStatus.merge(r.getStatus(), 1, Integer::sum);

            int score = r.getRiskScore() == null ? 0 : r.getRiskScore();
            String band = score >= 20 ? "CRITICAL"
                       : score >= 15 ? "HIGH"
                       : score >= 9  ? "MEDIUM" : "LOW";
            byBand.merge(band, 1, Integer::sum);

            // Rule 8 trigger — score ≥ 15 on a RISK that hasn't been
            // linked to an ERM record yet.
            if ("RISK".equals(r.getKind()) && score >= 15 && r.getRelatedErmRiskId() == null) {
                needsErmLink++;
            }

            if ("TREATED".equals(r.getStatus()) || "CLOSED".equals(r.getStatus())) treatedOrClosed++;
            if ("RETIRED".equals(r.getStatus())) retired++;
        }

        int activeForRate = Math.max(0, all.size() - retired);
        int treatedRatePct = activeForRate == 0
            ? 0
            : (int) Math.round(treatedOrClosed * 100.0 / activeForRate);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("byKind", byKind);
        out.put("byStatus", byStatus);
        out.put("byBand", byBand);
        out.put("treatedRatePct", treatedRatePct);
        out.put("needsErmLinkCount", needsErmLink);
        return out;
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public ClimateRiskOpportunity create(ClimateRiskOpportunity req) {
        req.setId(null);
        req.setOrganisationId(org());
        validateAxes(req);
        recomputeScore(req);
        if (req.getStatus() == null || req.getStatus().isBlank()) req.setStatus("IDENTIFIED");
        if (req.getIdentifiedAt() == null) req.setIdentifiedAt(LocalDateTime.now());
        return repo.save(req);
    }

    @Transactional
    public ClimateRiskOpportunity update(Long id, ClimateRiskOpportunity req) {
        ClimateRiskOpportunity cur = get(id);
        // Update mutable fields — explicit list rather than blanket copy so we
        // don't accidentally let callers reset audit fields or change tenant.
        if (req.getTitleEn() != null)               cur.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null)               cur.setTitleAr(req.getTitleAr());
        if (req.getDescriptionEn() != null)         cur.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null)         cur.setDescriptionAr(req.getDescriptionAr());
        if (req.getKind() != null)                  cur.setKind(req.getKind());
        if (req.getCategory() != null)              cur.setCategory(req.getCategory());
        if (req.getTimeHorizon() != null)           cur.setTimeHorizon(req.getTimeHorizon());
        if (req.getLikelihood() != null)            cur.setLikelihood(req.getLikelihood());
        if (req.getImpact() != null)                cur.setImpact(req.getImpact());
        if (req.getFinancialImpactSar() != null)    cur.setFinancialImpactSar(req.getFinancialImpactSar());
        if (req.getRelatedService() != null)        cur.setRelatedService(req.getRelatedService());
        if (req.getRelatedSupplier() != null)       cur.setRelatedSupplier(req.getRelatedSupplier());
        if (req.getRelatedAsset() != null)          cur.setRelatedAsset(req.getRelatedAsset());
        if (req.getRelatedTcfdRecommendation() != null) cur.setRelatedTcfdRecommendation(req.getRelatedTcfdRecommendation());
        if (req.getControlsSummary() != null)       cur.setControlsSummary(req.getControlsSummary());
        if (req.getTreatmentPlan() != null)         cur.setTreatmentPlan(req.getTreatmentPlan());
        if (req.getMetricsSummary() != null)        cur.setMetricsSummary(req.getMetricsSummary());
        if (req.getEvidenceRef() != null)           cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getOwnerName() != null)             cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)            cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getReviewDate() != null)            cur.setReviewDate(req.getReviewDate());
        validateAxes(cur);
        recomputeScore(cur);
        return repo.save(cur);
    }

    @Transactional
    public ClimateRiskOpportunity transition(Long id, String to) {
        ClimateRiskOpportunity cur = get(id);
        if (!STATUSES.contains(to)) throw new IllegalArgumentException("Unknown status: " + to);
        // Linear forward + retire-any-time.
        String from = cur.getStatus();
        boolean ok =
            ("IDENTIFIED".equals(from) && "ASSESSED".equals(to)) ||
            ("ASSESSED".equals(from)   && "TREATED".equals(to))  ||
            ("TREATED".equals(from)    && "CLOSED".equals(to))   ||
            "RETIRED".equals(to);
        if (!ok) throw new IllegalTransitionException(from, to, Set.of("ASSESSED","TREATED","CLOSED","RETIRED"));
        cur.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        switch (to) {
            case "ASSESSED" -> cur.setAssessedAt(now);
            case "TREATED"  -> cur.setTreatedAt(now);
            case "CLOSED"   -> cur.setClosedAt(now);
            default -> { /* RETIRED — no timestamp field */ }
        }
        return repo.save(cur);
    }

    /**
     * Workflow Rule 8 — set the ERM-risk link. Service-only validation: we
     * just store the supplied id; the ERM-side service owns the actual
     * cross-module relationship + integrity check.
     */
    @Transactional
    public ClimateRiskOpportunity linkErm(Long id, Map<String, Object> body) {
        ClimateRiskOpportunity cur = get(id);
        Object v = body.get("ermRiskId");
        cur.setRelatedErmRiskId(v == null ? null : Long.valueOf(v.toString()));
        return repo.save(cur);
    }

    @Transactional
    public void softDelete(Long id) {
        ClimateRiskOpportunity cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private void validateAxes(ClimateRiskOpportunity r) {
        if (!KINDS.contains(r.getKind()))
            throw new IllegalArgumentException("kind must be RISK or OPPORTUNITY");
        Set<String> allowed = "OPPORTUNITY".equals(r.getKind()) ? OPP_CATEGORIES : RISK_CATEGORIES;
        if (r.getCategory() == null || !allowed.contains(r.getCategory()))
            throw new IllegalArgumentException("Category " + r.getCategory()
                + " not valid for kind " + r.getKind() + ". Allowed: " + allowed);
        if (r.getLikelihood() == null || r.getLikelihood() < 1 || r.getLikelihood() > 5)
            throw new IllegalArgumentException("likelihood must be 1..5");
        if (r.getImpact() == null || r.getImpact() < 1 || r.getImpact() > 5)
            throw new IllegalArgumentException("impact must be 1..5");
        if (r.getTitleEn() == null || r.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn required");
    }

    private void recomputeScore(ClimateRiskOpportunity r) {
        r.setRiskScore((short) (r.getLikelihood() * r.getImpact()));
    }
}
