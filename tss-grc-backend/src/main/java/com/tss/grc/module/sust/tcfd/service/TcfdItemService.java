package com.tss.grc.module.sust.tcfd.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * TCFD alignment service. Mirrors {@code CmaDisclosureService} for
 * Rule 5 (gap required) and Rule 6 (no evidence → EVIDENCE_REQUIRED),
 * plus implements Rule 7 surfacing via the aggregate endpoint.
 *
 * <p>Rule 7 — when a STRATEGY-pillar resilience recommendation
 * (recommendation_code = 'c') is PARTIAL or NOT_DISCLOSED, the
 * {@link #readiness(Short)} response carries
 * {@code strategyResilienceGap=true} + the offending item's id so the
 * dashboard / CapaTab can offer to raise a CA-SUST-* corrective action
 * via {@code source_type='TCFD_ITEM'}.
 */
@Service
@RequiredArgsConstructor
public class TcfdItemService {

    private static final Set<String> STATUSES = Set.of(
        "DISCLOSED", "PARTIAL", "NOT_DISCLOSED", "NOT_APPLICABLE",
        "UNDER_REVIEW", "EVIDENCE_REQUIRED");
    private static final Set<String> PILLARS = Set.of(
        "GOVERNANCE", "STRATEGY", "RISK_MGMT", "METRICS_TARGETS");
    private static final Set<String> APPROVAL_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final TcfdItemRepository repo;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    public Page<TcfdItem> listByYear(Short year, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearOrderByPillarAscRecommendationCodeAsc(org(), year, p);
    }
    public Page<TcfdItem> listByYearAndPillar(Short year, String pillar, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearAndPillarOrderByRecommendationCodeAsc(org(), year, pillar, p);
    }
    public TcfdItem get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("TCFD item " + id));
    }

    @Transactional
    public TcfdItem create(TcfdItem req) {
        req.setId(null);
        req.setOrganisationId(org());
        if (req.getDisclosureStatus() == null || req.getDisclosureStatus().isBlank())
            req.setDisclosureStatus("UNDER_REVIEW");
        validate(req);
        applyRules(req);
        TcfdItem saved = repo.save(req);
        audit.log("CREATE", "TcfdItem", saved.getId(),
                "TCFD item created");
        return saved;
    }

    @Transactional
    public TcfdItem update(Long id, TcfdItem req) {
        TcfdItem cur = get(id);
        if (req.getTitleEn() != null)              cur.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null)              cur.setTitleAr(req.getTitleAr());
        if (req.getRecommendationEn() != null)     cur.setRecommendationEn(req.getRecommendationEn());
        if (req.getRecommendationAr() != null)     cur.setRecommendationAr(req.getRecommendationAr());
        if (req.getEvidenceRef() != null)          cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getEvidenceOwner() != null)        cur.setEvidenceOwner(req.getEvidenceOwner());
        if (req.getGapDescriptionEn() != null)     cur.setGapDescriptionEn(req.getGapDescriptionEn());
        if (req.getGapDescriptionAr() != null)     cur.setGapDescriptionAr(req.getGapDescriptionAr());
        if (req.getRelatedClimateRiskId() != null) cur.setRelatedClimateRiskId(req.getRelatedClimateRiskId());
        if (req.getRelatedMetricId() != null)      cur.setRelatedMetricId(req.getRelatedMetricId());
        if (req.getLinkedCapaId() != null)         cur.setLinkedCapaId(req.getLinkedCapaId());
        if (req.getOwnerName() != null)            cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)           cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getReviewerName() != null)         cur.setReviewerName(req.getReviewerName());
        if (req.getNotes() != null)                cur.setNotes(req.getNotes());
        if (req.getDisclosureStatus() != null)     cur.setDisclosureStatus(req.getDisclosureStatus());
        validate(cur);
        applyRules(cur);
        TcfdItem saved = repo.save(cur);
        audit.log("UPDATE", "TcfdItem", saved.getId(),
                "TCFD item updated");
        return saved;
    }

    @Transactional
    public TcfdItem transition(Long id, String to) {
        TcfdItem cur = get(id);
        if (!STATUSES.contains(to))
            throw new IllegalArgumentException("Unknown disclosure_status: " + to);
        cur.setDisclosureStatus(to);
        cur.setLastReviewedAt(LocalDateTime.now());
        applyRules(cur);
        TcfdItem saved = repo.save(cur);
        audit.log("TRANSITION", "TcfdItem", saved.getId(),
                "TCFD item -> " + to);
        return saved;
    }

    @Transactional
    public TcfdItem recordGap(Long id, String gapEn, String gapAr) {
        if ((gapEn == null || gapEn.isBlank()) && (gapAr == null || gapAr.isBlank()))
            throw new IllegalArgumentException("Gap description required (en or ar).");
        TcfdItem cur = get(id);
        if (gapEn != null) cur.setGapDescriptionEn(gapEn);
        if (gapAr != null) cur.setGapDescriptionAr(gapAr);
        cur.setLastReviewedAt(LocalDateTime.now());
        return repo.save(cur);
    }

    @Transactional
    public TcfdItem attachEvidence(Long id, String evidenceRef, String owner) {
        if (evidenceRef == null || evidenceRef.isBlank())
            throw new IllegalArgumentException("evidence_ref required.");
        TcfdItem cur = get(id);
        cur.setEvidenceRef(evidenceRef);
        if (owner != null) cur.setEvidenceOwner(owner);
        if ("EVIDENCE_REQUIRED".equals(cur.getDisclosureStatus())) {
            cur.setDisclosureStatus("UNDER_REVIEW");
        }
        cur.setLastReviewedAt(LocalDateTime.now());
        return repo.save(cur);
    }

    @Transactional
    public TcfdItem linkClimateRisk(Long id, Long climateRiskId) {
        TcfdItem cur = get(id);
        cur.setRelatedClimateRiskId(climateRiskId);
        return repo.save(cur);
    }

    @Transactional
    public void softDelete(Long id) {
        TcfdItem cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "TcfdItem", id,
                "TCFD item deleted");
    }

    /**
     * Per-year alignment rollup. Returns counts by status and per pillar,
     * the alignment-score % (disclosed / applicable), and the Rule 7 flag.
     */
    public Map<String, Object> readiness(Short year) {
        List<TcfdItem> all = repo.findByOrganisationIdAndReportingYear(org(), year);

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : STATUSES) byStatus.put(s, 0);
        Map<String, Integer> byPillar = new LinkedHashMap<>();
        for (String p : PILLARS) byPillar.put(p, 0);
        Map<String, Integer> disclosedByPillar = new LinkedHashMap<>();
        for (String p : PILLARS) disclosedByPillar.put(p, 0);

        boolean strategyResilienceGap = false;
        Long strategyResilienceItemId = null;
        int withEvidence = 0, withGap = 0;

        for (TcfdItem it : all) {
            byStatus.merge(it.getDisclosureStatus(), 1, Integer::sum);
            byPillar.merge(it.getPillar(), 1, Integer::sum);
            if ("DISCLOSED".equals(it.getDisclosureStatus())) {
                disclosedByPillar.merge(it.getPillar(), 1, Integer::sum);
            }
            // Rule 7 — Strategy resilience recommendation in a non-DISCLOSED state.
            if ("STRATEGY".equals(it.getPillar())
                && "c".equals(it.getRecommendationCode())
                && ("PARTIAL".equals(it.getDisclosureStatus())
                    || "NOT_DISCLOSED".equals(it.getDisclosureStatus())
                    || "EVIDENCE_REQUIRED".equals(it.getDisclosureStatus()))) {
                strategyResilienceGap = true;
                strategyResilienceItemId = it.getId();
            }
            if (it.getEvidenceRef() != null && !it.getEvidenceRef().isBlank()) withEvidence++;
            if ((it.getGapDescriptionEn() != null && !it.getGapDescriptionEn().isBlank())
                || (it.getGapDescriptionAr() != null && !it.getGapDescriptionAr().isBlank())) {
                withGap++;
            }
        }

        int applicable = all.size() - byStatus.getOrDefault("NOT_APPLICABLE", 0);
        int disclosed  = byStatus.getOrDefault("DISCLOSED", 0);
        int alignmentPct = applicable == 0 ? 0 : (int) Math.round(disclosed * 100.0 / applicable);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("total", all.size());
        out.put("applicable", applicable);
        out.put("byStatus", byStatus);
        out.put("byPillar", byPillar);
        out.put("disclosedByPillar", disclosedByPillar);
        out.put("withEvidence", withEvidence);
        out.put("withGap", withGap);
        out.put("alignmentPct", alignmentPct);
        // Rule 7 surface ─────────────────────────────────────────────
        out.put("strategyResilienceGap", strategyResilienceGap);
        out.put("strategyResilienceItemId", strategyResilienceItemId);
        return out;
    }

    private void applyRules(TcfdItem t) {
        String s = t.getDisclosureStatus();
        boolean hasGap = (t.getGapDescriptionEn() != null && !t.getGapDescriptionEn().isBlank())
                      || (t.getGapDescriptionAr() != null && !t.getGapDescriptionAr().isBlank());
        boolean hasEvidence = t.getEvidenceRef() != null && !t.getEvidenceRef().isBlank();

        // Rule 5 — gap required for PARTIAL / NOT_DISCLOSED.
        if (("PARTIAL".equals(s) || "NOT_DISCLOSED".equals(s)) && !hasGap) {
            throw new IllegalArgumentException(
                "Disclosure status " + s + " requires a gap description (Rule 5).");
        }

        // Rule 6 — DISCLOSED/PARTIAL with no evidence → EVIDENCE_REQUIRED.
        if (("DISCLOSED".equals(s) || "PARTIAL".equals(s)) && !hasEvidence) {
            t.setDisclosureStatus("EVIDENCE_REQUIRED");
        }
    }

    private void validate(TcfdItem t) {
        if (t.getTitleEn() == null || t.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn required");
        if (t.getPillar() == null || !PILLARS.contains(t.getPillar()))
            throw new IllegalArgumentException("pillar must be one of " + PILLARS);
        if (t.getRecommendationCode() == null || t.getRecommendationCode().isBlank())
            throw new IllegalArgumentException("recommendationCode required");
        if (t.getReportingYear() == null)
            throw new IllegalArgumentException("reportingYear required");
        if (!STATUSES.contains(t.getDisclosureStatus()))
            throw new IllegalArgumentException("disclosure_status must be one of " + STATUSES);
        if (t.getApprovalStatus() != null && !APPROVAL_STATUSES.contains(t.getApprovalStatus()))
            throw new IllegalArgumentException("approval_status must be one of " + APPROVAL_STATUSES);
    }
}
