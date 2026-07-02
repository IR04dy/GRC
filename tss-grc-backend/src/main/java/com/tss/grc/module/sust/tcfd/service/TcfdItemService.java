package com.tss.grc.module.sust.tcfd.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import com.tss.grc.security.GrcUserDetails;
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
    private final CorrectiveActionService capaService;

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
        maybeAutoCapa(saved);
        return saved;
    }

    @Transactional
    public TcfdItem update(Long id, TcfdItem req) {
        TcfdItem cur = get(id);
        assertNotApproved(cur);
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
        maybeAutoCapa(saved);
        return saved;
    }

    @Transactional
    public TcfdItem transition(Long id, String to) {
        TcfdItem cur = get(id);
        assertNotApproved(cur);
        if (!STATUSES.contains(to))
            throw new IllegalArgumentException("Unknown disclosure_status: " + to);
        cur.setDisclosureStatus(to);
        cur.setLastReviewedAt(LocalDateTime.now());
        applyRules(cur);
        TcfdItem saved = repo.save(cur);
        audit.log("TRANSITION", "TcfdItem", saved.getId(),
                "TCFD item -> " + to);
        maybeAutoCapa(saved);
        return saved;
    }

    /**
     * S-K — approve a TCFD item. Approver identity is the authenticated
     * principal (never a request-body string) so attribution cannot be forged
     * (SEC-ESG-002); separation of duties rejects self-approval by the item's
     * owner or reviewer. Approval is terminal: an APPROVED item is
     * content-locked (see {@link #assertNotApproved}).
     */
    @Transactional
    public TcfdItem approve(Long id, GrcUserDetails approver) {
        TcfdItem cur = get(id);
        if (approver == null || approver.getUsername() == null || approver.getUsername().isBlank())
            throw new IllegalArgumentException("Approval requires an authenticated approver.");
        if ("APPROVED".equals(cur.getApprovalStatus()))
            throw new IllegalStateException("TCFD item is already approved and locked.");
        if (!"DISCLOSED".equals(cur.getDisclosureStatus())
            && !"PARTIAL".equals(cur.getDisclosureStatus())
            && !"NOT_APPLICABLE".equals(cur.getDisclosureStatus()))
            throw new IllegalStateException(
                "Cannot approve a TCFD item in state " + cur.getDisclosureStatus());
        String approvedBy = approver.getUsername();
        // Separation of duties — approver must not be the item's owner or reviewer.
        if (sameActor(approvedBy, cur.getOwnerName()) || sameActor(approvedBy, cur.getOwnerEmail())
            || sameActor(approvedBy, cur.getReviewerName()))
            throw new IllegalArgumentException(
                "Approver must differ from the item owner/reviewer (separation of duties).");
        cur.setApprovalStatus("APPROVED");
        cur.setApprovedBy(approvedBy);
        cur.setApprovedAt(LocalDateTime.now());
        TcfdItem saved = repo.save(cur);
        audit.log("APPROVE", "TcfdItem", saved.getId(), "TCFD item approved by " + approvedBy);
        return saved;
    }

    /** Trim-aware, case-insensitive identity compare used by the SoD check. */
    private static boolean sameActor(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * S-K — an APPROVED TCFD item is content-locked (filed): no edits,
     * transitions, evidence swaps, gap rewrites, or re-links. Soft-delete only.
     */
    private static void assertNotApproved(TcfdItem t) {
        if ("APPROVED".equals(t.getApprovalStatus()))
            throw new IllegalStateException(
                "TCFD item is APPROVED and locked — approved items cannot be modified.");
    }

    @Transactional
    public TcfdItem recordGap(Long id, String gapEn, String gapAr) {
        if ((gapEn == null || gapEn.isBlank()) && (gapAr == null || gapAr.isBlank()))
            throw new IllegalArgumentException("Gap description required (en or ar).");
        TcfdItem cur = get(id);
        assertNotApproved(cur);
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
        assertNotApproved(cur);
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
        assertNotApproved(cur);
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

    // ── S-K — official TCFD 11-recommendation library ────────────────

    /**
     * The official TCFD 11-recommendation checklist across the four pillars:
     * {@code {pillar, recommendationCode, titleEn, titleAr, recommendationEn,
     * recommendationAr}}. Seeded fresh (UNDER_REVIEW) into each new reporting
     * year so a tenant assesses against the full TCFD framework, not a blank
     * matrix.
     */
    private static final String[][] OFFICIAL_LIBRARY = {
        {"GOVERNANCE", "a", "Board oversight of climate risks",
         "إشراف المجلس على المخاطر المناخية",
         "Describe the board's oversight of climate-related risks and opportunities.",
         "وصف إشراف المجلس على المخاطر والفرص المتعلّقة بالمناخ."},
        {"GOVERNANCE", "b", "Management role in assessing climate risks",
         "دور الإدارة في تقييم المخاطر المناخية",
         "Describe management's role in assessing and managing climate-related risks and opportunities.",
         "وصف دور الإدارة في تقييم وإدارة المخاطر والفرص المتعلّقة بالمناخ."},
        {"STRATEGY", "a", "Climate-related risks + opportunities (short / medium / long term)",
         "المخاطر والفرص المناخية (قصير / متوسط / طويل الأمد)",
         "Describe the climate-related risks and opportunities identified over short, medium, and long-term horizons.",
         "وصف المخاطر والفرص المتعلّقة بالمناخ المحدّدة على المدى القصير والمتوسط والطويل."},
        {"STRATEGY", "b", "Impact on business, strategy, financial planning",
         "الأثر على الأعمال والاستراتيجية والتخطيط المالي",
         "Describe the impact of climate-related risks and opportunities on the business, strategy, and financial planning.",
         "وصف أثر المخاطر والفرص المناخية على الأعمال والاستراتيجية والتخطيط المالي."},
        {"STRATEGY", "c", "Resilience under different climate scenarios (incl. 2°C)",
         "الصمود تحت سيناريوهات مناخية مختلفة (بما في ذلك 2°م)",
         "Describe the resilience of the organisation's strategy, taking into consideration different climate-related scenarios including a 2°C-or-lower scenario.",
         "وصف صمود استراتيجية المنشأة آخذاً في الاعتبار سيناريوهات مناخية مختلفة بما في ذلك سيناريو 2°م أو أقل."},
        {"RISK_MGMT", "a", "Process for identifying climate risks",
         "إجراء تحديد المخاطر المناخية",
         "Describe the organisation's processes for identifying and assessing climate-related risks.",
         "وصف إجراءات المنشأة لتحديد وتقييم المخاطر المتعلّقة بالمناخ."},
        {"RISK_MGMT", "b", "Process for managing climate risks",
         "إجراء إدارة المخاطر المناخية",
         "Describe the organisation's processes for managing climate-related risks.",
         "وصف إجراءات المنشأة لإدارة المخاطر المتعلّقة بالمناخ."},
        {"RISK_MGMT", "c", "Integration into overall risk management",
         "التكامل مع إدارة المخاطر الشاملة",
         "Describe how processes for identifying, assessing, and managing climate-related risks are integrated into the overall risk-management framework.",
         "وصف كيفية دمج إجراءات تحديد وتقييم وإدارة المخاطر المتعلّقة بالمناخ ضمن إطار إدارة المخاطر الشامل."},
        {"METRICS_TARGETS", "a", "Metrics for climate risks + opportunities",
         "مقاييس المخاطر والفرص المناخية",
         "Disclose the metrics used to assess climate-related risks and opportunities in line with the strategy and risk-management process.",
         "الإفصاح عن المقاييس المستخدمة لتقييم المخاطر والفرص المناخية بما يتوافق مع الاستراتيجية وإجراء إدارة المخاطر."},
        {"METRICS_TARGETS", "b", "Scope 1, 2, 3 emissions disclosure",
         "الإفصاح عن انبعاثات النطاقات 1 و 2 و 3",
         "Disclose Scope 1, Scope 2, and (if appropriate) Scope 3 greenhouse-gas (GHG) emissions and the related risks.",
         "الإفصاح عن انبعاثات النطاقين 1 و 2، وإن كان ملائماً النطاق 3، من غازات الدفيئة والمخاطر المرتبطة."},
        {"METRICS_TARGETS", "c", "Targets to manage climate risks + performance",
         "الأهداف لإدارة المخاطر المناخية والأداء",
         "Describe the targets used by the organisation to manage climate-related risks and opportunities and performance against targets.",
         "وصف الأهداف التي تستخدمها المنشأة لإدارة المخاطر والفرص المناخية والأداء مقارنةً بها."},
    };

    /**
     * S-K — seed the official TCFD 11-recommendation library into {@code year}.
     * Idempotent per (org, year): if any TCFD item already exists for the year,
     * do nothing. Invoked on reporting-year open (see
     * {@code SustainabilityReportService.createYear}).
     *
     * @return rows inserted (0 if the year was already seeded).
     */
    @Transactional
    public int seedOfficialLibrary(short year) {
        Long org = org();
        if (!repo.findByOrganisationIdAndReportingYear(org, year).isEmpty()) return 0;
        int n = 0;
        for (String[] r : OFFICIAL_LIBRARY) {
            TcfdItem t = new TcfdItem();
            t.setOrganisationId(org);
            t.setReportingYear(year);
            t.setPillar(r[0]);
            t.setRecommendationCode(r[1]);
            t.setTitleEn(r[2]);
            t.setTitleAr(r[3]);
            t.setRecommendationEn(r[4]);
            t.setRecommendationAr(r[5]);
            t.setDisclosureStatus("UNDER_REVIEW");
            repo.save(t);
            n++;
        }
        audit.log("SEED", "TcfdItem", null,
            "Seeded official TCFD 11-recommendation library for year " + year);
        return n;
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

    // ── S-D — auto-CAPA on disclosure gap ────────────────────────────

    /**
     * A TCFD item that settles in PARTIAL or NOT_DISCLOSED is a disclosure gap;
     * idempotently open a corrective action (mirrors the compliance F8
     * {@code AR-EVD-GAP} rule). Deduped by ({@code TCFD_ITEM}, id) in
     * {@link CorrectiveActionService#autoOpen} so create / update / transition
     * can all call it without doubling up; the first CAPA is back-linked.
     */
    private void maybeAutoCapa(TcfdItem t) {
        String s = t.getDisclosureStatus();
        if (!"PARTIAL".equals(s) && !"NOT_DISCLOSED".equals(s)) return;
        String gap = t.getGapDescriptionEn() != null ? t.getGapDescriptionEn()
                   : (t.getGapDescriptionAr() != null ? t.getGapDescriptionAr() : "");
        CorrectiveAction ca = capaService.autoOpen(
            "TCFD_ITEM", String.valueOf(t.getId()),
            "TCFD " + t.getPillar() + "/" + t.getRecommendationCode() + " disclosure gap (" + s + ")",
            "فجوة إفصاح TCFD — " + t.getPillar(),
            "Auto-opened by S-D: TCFD item #" + t.getId() + " is " + s
                + (gap.isBlank() ? "." : ". Gap: " + gap),
            "HIGH");
        if (ca != null && t.getLinkedCapaId() == null) {
            t.setLinkedCapaId(ca.getId());
            repo.save(t);
        }
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
