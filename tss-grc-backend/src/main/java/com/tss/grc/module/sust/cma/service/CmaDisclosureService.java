package com.tss.grc.module.sust.cma.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
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
 * CMA Articles 91–94 disclosure service.
 *
 * <p>Disclosure-status set:
 * {@code DISCLOSED | PARTIAL | NOT_DISCLOSED | NOT_APPLICABLE |
 *  UNDER_REVIEW | EVIDENCE_REQUIRED}.
 *
 * <p>Rules:
 * <ul>
 *   <li><b>Rule 5</b> — PARTIAL or NOT_DISCLOSED requires gap description
 *       (English or Arabic — either suffices for the audit trail).</li>
 *   <li><b>Rule 6</b> — DISCLOSED or PARTIAL with empty {@code evidenceRef}
 *       is auto-rewritten to {@code EVIDENCE_REQUIRED} on save. The
 *       service never silently leaves a "disclosed but no evidence"
 *       row in the matrix.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CmaDisclosureService {

    private static final Set<String> STATUSES = Set.of(
        "DISCLOSED", "PARTIAL", "NOT_DISCLOSED", "NOT_APPLICABLE",
        "UNDER_REVIEW", "EVIDENCE_REQUIRED");

    private static final Set<String> ARTICLES = Set.of("91", "92", "93", "94");

    private static final Set<String> APPROVAL_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final CmaDisclosureRepository repo;
    private final AuditLogService audit;
    private final CorrectiveActionService capaService;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<CmaDisclosure> listByYear(Short year, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearOrderByArticleAscClauseAsc(org(), year, p);
    }
    public Page<CmaDisclosure> listByYearAndStatus(Short year, String status, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearAndDisclosureStatusOrderByArticleAscClauseAsc(
            org(), year, status, p);
    }
    public CmaDisclosure get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("CMA disclosure " + id));
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public CmaDisclosure create(CmaDisclosure req) {
        req.setId(null);
        req.setOrganisationId(org());
        if (req.getDisclosureStatus() == null || req.getDisclosureStatus().isBlank())
            req.setDisclosureStatus("UNDER_REVIEW");
        // Mass-assignment guard (SEC-ESG-003): the approval gate is reached
        // only through approve(); a create can never pre-set the approval
        // status or its attribution, nor stamp audit columns. (The working
        // disclosure_status is legitimate data entry and is left as-supplied,
        // subject to Rule 5/6 in applyRules.)
        req.setApprovalStatus(null);
        req.setApprovedBy(null);
        req.setApprovedAt(null);
        req.setLastReviewedAt(null);
        req.setCreatedBy(null);
        req.setUpdatedBy(null);
        validate(req);
        applyRules(req);
        CmaDisclosure saved = repo.save(req);
        audit.log("CREATE", "CmaDisclosure", saved.getId(),
                "CMA disclosure created");
        maybeAutoCapa(saved);
        return saved;
    }

    @Transactional
    public CmaDisclosure update(Long id, CmaDisclosure req) {
        CmaDisclosure cur = get(id);
        assertNotApproved(cur);
        if (req.getTitleEn() != null)              cur.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null)              cur.setTitleAr(req.getTitleAr());
        if (req.getRequirementEn() != null)        cur.setRequirementEn(req.getRequirementEn());
        if (req.getRequirementAr() != null)        cur.setRequirementAr(req.getRequirementAr());
        if (req.getEvidenceRef() != null)          cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getEvidenceOwner() != null)        cur.setEvidenceOwner(req.getEvidenceOwner());
        if (req.getResponsibleDepartment() != null) cur.setResponsibleDepartment(req.getResponsibleDepartment());
        if (req.getGapDescriptionEn() != null)     cur.setGapDescriptionEn(req.getGapDescriptionEn());
        if (req.getGapDescriptionAr() != null)     cur.setGapDescriptionAr(req.getGapDescriptionAr());
        if (req.getReportSectionReference() != null) cur.setReportSectionReference(req.getReportSectionReference());
        if (req.getReviewerName() != null)         cur.setReviewerName(req.getReviewerName());
        if (req.getReviewerEmail() != null)        cur.setReviewerEmail(req.getReviewerEmail());
        if (req.getNotes() != null)                cur.setNotes(req.getNotes());
        if (req.getLinkedCapaId() != null)         cur.setLinkedCapaId(req.getLinkedCapaId());
        if (req.getDisclosureStatus() != null)     cur.setDisclosureStatus(req.getDisclosureStatus());
        validate(cur);
        applyRules(cur);
        CmaDisclosure saved = repo.save(cur);
        audit.log("UPDATE", "CmaDisclosure", saved.getId(),
                "CMA disclosure updated");
        maybeAutoCapa(saved);
        return saved;
    }

    /**
     * Transition the disclosure status. Wraps the common state changes
     * so the matrix UI can fire a single button per outcome.
     */
    @Transactional
    public CmaDisclosure transition(Long id, String to) {
        CmaDisclosure cur = get(id);
        assertNotApproved(cur);
        if (!STATUSES.contains(to))
            throw new IllegalArgumentException("Unknown disclosure_status: " + to);
        cur.setDisclosureStatus(to);
        cur.setLastReviewedAt(LocalDateTime.now());
        validate(cur);
        applyRules(cur);
        CmaDisclosure saved = repo.save(cur);
        audit.log("TRANSITION", "CmaDisclosure", saved.getId(),
                "CMA disclosure -> " + to);
        maybeAutoCapa(saved);
        return saved;
    }

    /**
     * Record a gap description without changing the status. Used by the
     * "Create Gap" button when reviewing partial disclosures.
     */
    @Transactional
    public CmaDisclosure recordGap(Long id, String gapEn, String gapAr) {
        if ((gapEn == null || gapEn.isBlank()) && (gapAr == null || gapAr.isBlank()))
            throw new IllegalArgumentException("Gap description required (en or ar).");
        CmaDisclosure cur = get(id);
        assertNotApproved(cur);
        if (gapEn != null) cur.setGapDescriptionEn(gapEn);
        if (gapAr != null) cur.setGapDescriptionAr(gapAr);
        cur.setLastReviewedAt(LocalDateTime.now());
        return repo.save(cur);
    }

    /**
     * Attach (or replace) the evidence reference. Useful for the
     * "Attach Evidence" button. Triggers Rule 6 re-evaluation — a
     * previously EVIDENCE_REQUIRED row that now carries evidence flips
     * back to UNDER_REVIEW so the reviewer can re-assess.
     */
    @Transactional
    public CmaDisclosure attachEvidence(Long id, String evidenceRef, String owner) {
        if (evidenceRef == null || evidenceRef.isBlank())
            throw new IllegalArgumentException("evidence_ref required.");
        CmaDisclosure cur = get(id);
        assertNotApproved(cur);
        cur.setEvidenceRef(evidenceRef);
        if (owner != null) cur.setEvidenceOwner(owner);
        // If it was EVIDENCE_REQUIRED specifically because of Rule 6,
        // unblock it — reviewer should now re-classify as DISCLOSED or PARTIAL.
        if ("EVIDENCE_REQUIRED".equals(cur.getDisclosureStatus())) {
            cur.setDisclosureStatus("UNDER_REVIEW");
        }
        cur.setLastReviewedAt(LocalDateTime.now());
        return repo.save(cur);
    }

    /**
     * S-K — approve a disclosure. The approver identity is the authenticated
     * principal (never a request-body string) so attribution cannot be forged
     * (SEC-ESG-002), and separation of duties rejects self-approval by the
     * disclosure's owner or reviewer. Approval is terminal: an already-APPROVED
     * disclosure is content-locked (see {@link #assertNotApproved}).
     *
     * @param approver the authenticated caller; required.
     */
    @Transactional
    public CmaDisclosure approve(Long id, GrcUserDetails approver) {
        CmaDisclosure cur = get(id);
        if (approver == null || approver.getUsername() == null || approver.getUsername().isBlank())
            throw new IllegalArgumentException("Approval requires an authenticated approver.");
        if ("APPROVED".equals(cur.getApprovalStatus()))
            throw new IllegalStateException("Disclosure is already approved and locked.");
        if (!"DISCLOSED".equals(cur.getDisclosureStatus())
            && !"PARTIAL".equals(cur.getDisclosureStatus())
            && !"NOT_APPLICABLE".equals(cur.getDisclosureStatus()))
            throw new IllegalStateException(
                "Cannot approve a disclosure in state " + cur.getDisclosureStatus());
        String approvedBy = approver.getUsername();
        // Separation of duties — the approver must not be the disclosure's
        // preparer/owner (evidenceOwner) or its reviewer.
        if (sameActor(approvedBy, cur.getEvidenceOwner()) || sameActor(approvedBy, cur.getReviewerName()))
            throw new IllegalArgumentException(
                "Approver must differ from the disclosure owner/reviewer (separation of duties).");
        cur.setApprovalStatus("APPROVED");
        cur.setApprovedBy(approvedBy);
        cur.setApprovedAt(LocalDateTime.now());
        CmaDisclosure saved = repo.save(cur);
        audit.log("APPROVE", "CmaDisclosure", saved.getId(),
                "CMA disclosure approved by " + approvedBy);
        return saved;
    }

    /** Trim-aware, case-insensitive identity compare used by the SoD check. */
    private static boolean sameActor(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * S-K — an APPROVED disclosure is content-locked (it has been filed): no
     * edits, transitions, evidence swaps, or gap rewrites. Soft-delete only.
     */
    private static void assertNotApproved(CmaDisclosure d) {
        if ("APPROVED".equals(d.getApprovalStatus()))
            throw new IllegalStateException(
                "Disclosure is APPROVED and locked — approved disclosures cannot be modified.");
    }

    @Transactional
    public void softDelete(Long id) {
        CmaDisclosure cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "CmaDisclosure", id,
                "CMA disclosure deleted");
    }

    // ── S-K — official CMA Art. 91–94 disclosure library ─────────────

    /**
     * The official Saudi CMA Articles 91–94 ESG disclosure checklist — the
     * canonical 11 disclosures: {@code {article, clause, titleEn, titleAr,
     * requirementEn, requirementAr}}. Seeded fresh (UNDER_REVIEW) into each new
     * reporting year so a tenant starts from the regulator's library, not a
     * blank matrix.
     */
    private static final String[][] OFFICIAL_LIBRARY = {
        {"91", "(a)", "Board oversight of sustainability + ESG",
         "إشراف المجلس على الاستدامة والحوكمة البيئية والاجتماعية",
         "Disclose the board's role in setting ESG strategy, approving climate-related targets, and reviewing performance against them.",
         "الإفصاح عن دور المجلس في وضع استراتيجية الحوكمة البيئية والاجتماعية، واعتماد الأهداف المتعلّقة بالمناخ، ومراجعة الأداء."},
        {"91", "(b)", "Sustainability committee composition",
         "تشكيل لجنة الاستدامة",
         "Disclose the composition and mandate of the sustainability / ESG committee, with at least one independent director.",
         "الإفصاح عن تشكيل ومهام لجنة الاستدامة / الحوكمة البيئية والاجتماعية، بحضور عضو مستقل واحد على الأقل."},
        {"92", "(a)", "Material ESG topics — assessment",
         "تقييم الموضوعات الجوهرية للحوكمة البيئية والاجتماعية",
         "Conduct and disclose a double-materiality assessment of ESG topics, identifying which are most relevant to the business model.",
         "إجراء والإفصاح عن تقييم الأهمية النسبية المزدوجة لموضوعات الحوكمة البيئية والاجتماعية، وتحديد الأكثر صلة بنموذج الأعمال."},
        {"92", "(b)", "Climate-related risks + opportunities",
         "المخاطر والفرص المتعلّقة بالمناخ",
         "Disclose climate-related risks (transition + physical) and opportunities, mapped to short / medium / long-term horizons per TCFD.",
         "الإفصاح عن المخاطر المتعلّقة بالمناخ (الانتقال والمادية) والفرص، بحسب الآفاق القصيرة والمتوسطة والطويلة وفق TCFD."},
        {"93", "(a)", "GHG emissions — Scope 1 + 2",
         "انبعاثات الغازات الدفيئة — النطاقان 1 و 2",
         "Disclose Scope 1 and Scope 2 emissions in tCO2e, with year-over-year comparison and target trajectory to 2030.",
         "الإفصاح عن انبعاثات النطاقين 1 و 2 بالطن المكافئ ثاني أكسيد الكربون، مع المقارنة السنوية والمسار حتى 2030."},
        {"93", "(b)", "GHG emissions — Scope 3",
         "انبعاثات النطاق 3",
         "Disclose Scope 3 emissions for the most material categories. Where coverage is limited, disclose the % of operations covered.",
         "الإفصاح عن انبعاثات النطاق 3 للفئات الأكثر جوهرية. إن كانت التغطية محدودة، الإفصاح عن النسبة المُشمَلة."},
        {"93", "(c)", "Energy intensity + renewable share",
         "كثافة الطاقة وحصة المتجدّدة",
         "Disclose energy consumption (MWh) and the share of renewable energy in the mix, aligned to the SGI 2030 50% target.",
         "الإفصاح عن استهلاك الطاقة (ميغاواط ساعة) وحصة الطاقة المتجدّدة، بمحاذاة هدف SGI 2030 (50%)."},
        {"93", "(d)", "Water + waste metrics",
         "مؤشرات المياه والنفايات",
         "Disclose water withdrawal, waste generated, and waste-diversion rate (recycle + reuse) where material.",
         "الإفصاح عن سحب المياه والنفايات المُولَّدة ونسبة التحويل (إعادة التدوير + الاستخدام) عند الجوهرية."},
        {"94", "(a)", "Saudization (Nitaqat) compliance",
         "الالتزام بالسعودة (نطاقات)",
         "Disclose Nitaqat tier (Platinum / Green / Yellow), Saudization rate, and improvements vs prior year.",
         "الإفصاح عن فئة نطاقات (بلاتيني / أخضر / أصفر) ونسبة السعودة والتحسّن مقارنة بالعام السابق."},
        {"94", "(b)", "Female workforce participation",
         "مشاركة المرأة في القوى العاملة",
         "Disclose female workforce participation %, leadership representation %, and gender pay-gap analysis.",
         "الإفصاح عن نسبة مشاركة المرأة في العاملين، ونسبة التمثيل القيادي، وتحليل فجوة الأجور بين الجنسين."},
        {"94", "(c)", "Health, safety + LTIFR",
         "الصحة والسلامة وLTIFR",
         "Disclose lost-time injury frequency rate (LTIFR), fatalities, and total recordable incidents.",
         "الإفصاح عن معدّل تكرار الإصابات المُفقِدة للوقت (LTIFR) والوفيات وإجمالي الحوادث."},
    };

    /**
     * S-K — seed the official CMA Art. 91–94 library into {@code year}.
     * Idempotent per (org, year): if any CMA disclosure already exists for the
     * year, do nothing. Invoked on reporting-year open (see
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
            CmaDisclosure d = new CmaDisclosure();
            d.setOrganisationId(org);
            d.setReportingYear(year);
            d.setArticle(r[0]);
            d.setClause(r[1]);
            d.setTitleEn(r[2]);
            d.setTitleAr(r[3]);
            d.setRequirementEn(r[4]);
            d.setRequirementAr(r[5]);
            d.setDisclosureStatus("UNDER_REVIEW");
            repo.save(d);
            n++;
        }
        audit.log("SEED", "CmaDisclosure", null,
            "Seeded official CMA Art.91-94 library (" + n + " disclosures) for year " + year);
        return n;
    }

    // ── Aggregation / Readiness ──────────────────────────────────────

    /**
     * Year-scoped readiness rollup. Returns counts by status, an applicable
     * total (excluding NOT_APPLICABLE), and a readiness percentage:
     * {@code disclosed / applicable * 100}. The dashboard uses this to
     * render the CMA-completion KPI.
     */
    public Map<String, Object> readiness(Short year) {
        List<CmaDisclosure> all = repo.findByOrganisationIdAndReportingYear(org(), year);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : STATUSES) byStatus.put(s, 0);
        int withEvidence = 0, withGap = 0;
        for (CmaDisclosure d : all) {
            byStatus.merge(d.getDisclosureStatus(), 1, Integer::sum);
            if (d.getEvidenceRef() != null && !d.getEvidenceRef().isBlank()) withEvidence++;
            if ((d.getGapDescriptionEn() != null && !d.getGapDescriptionEn().isBlank())
                || (d.getGapDescriptionAr() != null && !d.getGapDescriptionAr().isBlank())) {
                withGap++;
            }
        }
        int applicable = all.size() - byStatus.getOrDefault("NOT_APPLICABLE", 0);
        int disclosed  = byStatus.getOrDefault("DISCLOSED", 0);
        int readinessPct = applicable == 0 ? 0 : (int) Math.round(disclosed * 100.0 / applicable);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("total", all.size());
        out.put("applicable", applicable);
        out.put("byStatus", byStatus);
        out.put("withEvidence", withEvidence);
        out.put("withGap", withGap);
        out.put("readinessPct", readinessPct);
        return out;
    }

    // ── S-D — auto-CAPA on disclosure gap ────────────────────────────

    /**
     * A disclosure that settles in PARTIAL or NOT_DISCLOSED is a genuine
     * disclosure gap; idempotently open a corrective action for it (mirrors the
     * compliance F8 {@code AR-EVD-GAP} rule). Deduped by
     * ({@code CMA_DISCLOSURE}, id) inside {@link CorrectiveActionService#autoOpen},
     * so create / update / transition can all call this without doubling up.
     * The first CAPA is back-linked ({@code linkedCapaId}) for the matrix UI.
     * EVIDENCE_REQUIRED (Rule 6) is intentionally NOT a trigger — it is an
     * evidence-collection state, not a disclosure gap.
     */
    private void maybeAutoCapa(CmaDisclosure d) {
        String s = d.getDisclosureStatus();
        if (!"PARTIAL".equals(s) && !"NOT_DISCLOSED".equals(s)) return;
        String gap = d.getGapDescriptionEn() != null ? d.getGapDescriptionEn()
                   : (d.getGapDescriptionAr() != null ? d.getGapDescriptionAr() : "");
        CorrectiveAction ca = capaService.autoOpen(
            "CMA_DISCLOSURE", String.valueOf(d.getId()),
            "CMA Art. " + d.getArticle() + " disclosure gap (" + s + ")",
            "فجوة إفصاح — المادة " + d.getArticle() + " (هيئة السوق المالية)",
            "Auto-opened by S-D: CMA disclosure #" + d.getId() + " is " + s
                + (gap.isBlank() ? "." : ". Gap: " + gap),
            "HIGH");
        if (ca != null && d.getLinkedCapaId() == null) {
            d.setLinkedCapaId(ca.getId());
            repo.save(d);
        }
    }

    // ── Rule enforcement helpers ─────────────────────────────────────

    /**
     * Apply Rules 5 and 6 to the entity before save.
     *   Rule 5 — PARTIAL or NOT_DISCLOSED requires a gap.
     *   Rule 6 — DISCLOSED or PARTIAL with empty evidence → EVIDENCE_REQUIRED.
     */
    private void applyRules(CmaDisclosure d) {
        String s = d.getDisclosureStatus();
        boolean hasGap = (d.getGapDescriptionEn() != null && !d.getGapDescriptionEn().isBlank())
                      || (d.getGapDescriptionAr() != null && !d.getGapDescriptionAr().isBlank());
        boolean hasEvidence = d.getEvidenceRef() != null && !d.getEvidenceRef().isBlank();

        // Rule 5 — gap required for PARTIAL / NOT_DISCLOSED.
        if (("PARTIAL".equals(s) || "NOT_DISCLOSED".equals(s)) && !hasGap) {
            throw new IllegalArgumentException(
                "Disclosure status " + s + " requires a gap description (Rule 5).");
        }

        // Rule 6 — DISCLOSED/PARTIAL with no evidence → EVIDENCE_REQUIRED.
        if (("DISCLOSED".equals(s) || "PARTIAL".equals(s)) && !hasEvidence) {
            d.setDisclosureStatus("EVIDENCE_REQUIRED");
        }
    }

    // ── Validation ──────────────────────────────────────────────────
    private void validate(CmaDisclosure d) {
        if (d.getTitleEn() == null || d.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn required");
        if (d.getArticle() == null || !ARTICLES.contains(d.getArticle()))
            throw new IllegalArgumentException("article must be one of " + ARTICLES);
        if (d.getReportingYear() == null)
            throw new IllegalArgumentException("reportingYear required");
        if (!STATUSES.contains(d.getDisclosureStatus()))
            throw new IllegalArgumentException("disclosure_status must be one of " + STATUSES);
        if (d.getApprovalStatus() != null && !APPROVAL_STATUSES.contains(d.getApprovalStatus()))
            throw new IllegalArgumentException("approval_status must be one of " + APPROVAL_STATUSES);
    }
}
