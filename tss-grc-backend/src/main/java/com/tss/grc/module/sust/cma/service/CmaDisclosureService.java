package com.tss.grc.module.sust.cma.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
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
        return saved;
    }

    @Transactional
    public CmaDisclosure update(Long id, CmaDisclosure req) {
        CmaDisclosure cur = get(id);
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
        return saved;
    }

    /**
     * Transition the disclosure status. Wraps the common state changes
     * so the matrix UI can fire a single button per outcome.
     */
    @Transactional
    public CmaDisclosure transition(Long id, String to) {
        CmaDisclosure cur = get(id);
        if (!STATUSES.contains(to))
            throw new IllegalArgumentException("Unknown disclosure_status: " + to);
        cur.setDisclosureStatus(to);
        cur.setLastReviewedAt(LocalDateTime.now());
        validate(cur);
        applyRules(cur);
        CmaDisclosure saved = repo.save(cur);
        audit.log("TRANSITION", "CmaDisclosure", saved.getId(),
                "CMA disclosure -> " + to);
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

    @Transactional
    public CmaDisclosure approve(Long id, String approvedBy) {
        CmaDisclosure cur = get(id);
        if (!"DISCLOSED".equals(cur.getDisclosureStatus())
            && !"PARTIAL".equals(cur.getDisclosureStatus())
            && !"NOT_APPLICABLE".equals(cur.getDisclosureStatus()))
            throw new IllegalStateException(
                "Cannot approve a disclosure in state " + cur.getDisclosureStatus());
        cur.setApprovalStatus("APPROVED");
        cur.setApprovedBy(approvedBy);
        cur.setApprovedAt(LocalDateTime.now());
        CmaDisclosure saved = repo.save(cur);
        audit.log("APPROVE", "CmaDisclosure", saved.getId(),
                "CMA disclosure approved by " + approvedBy);
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        CmaDisclosure cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "CmaDisclosure", id,
                "CMA disclosure deleted");
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
