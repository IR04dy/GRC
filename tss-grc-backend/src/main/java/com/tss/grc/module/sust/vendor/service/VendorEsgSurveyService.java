package com.tss.grc.module.sust.vendor.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.vendor.dto.VendorEsgResponseRequest;
import com.tss.grc.module.sust.vendor.entity.VendorEsgSurvey;
import com.tss.grc.module.sust.vendor.repository.VendorEsgSurveyRepository;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
import com.tss.grc.security.GrcUserDetails;
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
 * Vendor ESG surveillance service (S-O).
 *
 * <p>Owns the {@code DRAFT → SENT → RESPONDED → ACCEPTED | REJECTED} lifecycle
 * plus the {@code ESCALATED} non-response terminal, all bound to the real
 * {@code tprm.vendors} register. Key rules:
 * <ul>
 *   <li><b>Real link</b> — {@code create} rejects a {@code tprmVendorId} that
 *       does not exist in this tenant (400, no dangling ids) and snapshots the
 *       vendor's bilingual name.</li>
 *   <li><b>Computed score</b> — {@code esgScore} is always derived from the
 *       reported data in {@link #computeEsgScore}, never bound from a request.</li>
 *   <li><b>Non-response escalation</b> — {@link #sweepOverdue} moves past-due
 *       SENT surveys to ESCALATED and opens a VENDOR_ESG auto-CAPA; a REJECTED
 *       response likewise opens one. Both dedup through the S-D loop.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VendorEsgSurveyService {

    /** Default response window (days) applied when a survey is sent. */
    static final int DEFAULT_RESPONSE_WINDOW_DAYS = 30;

    private static final Set<String> STATUSES =
        Set.of("DRAFT", "SENT", "RESPONDED", "ACCEPTED", "REJECTED", "ESCALATED");

    private final VendorEsgSurveyRepository repo;
    private final VendorRepository          tprmVendorRepo;
    private final CorrectiveActionService   capaService;
    private final AuditLogService           audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<VendorEsgSurvey> list(Pageable p) {
        return repo.findByOrganisationIdOrderByIdDesc(org(), p);
    }
    public Page<VendorEsgSurvey> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByIdDesc(org(), status, p);
    }
    public Page<VendorEsgSurvey> listByYear(Short year, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearOrderByIdDesc(org(), year, p);
    }
    public VendorEsgSurvey get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Vendor ESG survey " + id));
    }

    // ── Writes ────────────────────────────────────────────────────────

    /**
     * Create a survey against a real vendor. Validates the vendor exists in this
     * tenant (→ 400 on a dangling id), snapshots its bilingual name, generates a
     * per-year external id, and forces the initial lifecycle state — the
     * workflow stamps, reported data, computed score, evidence FK and CAPA link
     * are all server-owned (mass-assignment closed).
     */
    @Transactional
    public VendorEsgSurvey create(VendorEsgSurvey req) {
        Long orgId = org();
        if (req.getTprmVendorId() == null)
            throw new IllegalArgumentException("tprmVendorId required");
        if (req.getReportingYear() == null)
            throw new IllegalArgumentException("reportingYear required");

        // Real, same-tenant vendor link — no dangling ids.
        Vendor vendor = tprmVendorRepo.findByIdAndOrganisationId(req.getTprmVendorId(), orgId)
            .orElseThrow(() -> new IllegalArgumentException(
                "tprmVendorId " + req.getTprmVendorId() + " does not exist in this organisation"));

        // One live survey per (tenant, vendor, year) — reject a duplicate with a
        // clear 400 instead of letting the filtered unique index throw a 500.
        repo.findByOrganisationIdAndTprmVendorIdAndReportingYear(orgId, req.getTprmVendorId(), req.getReportingYear())
            .ifPresent(existing -> { throw new IllegalArgumentException(
                "A vendor ESG survey already exists for vendor " + req.getTprmVendorId()
                + " in reporting year " + req.getReportingYear()); });

        req.setId(null);
        req.setOrganisationId(orgId);
        req.setVendorNameEn(vendor.getVendorName());
        req.setVendorNameAr(vendor.getVendorNameAr());
        req.setExternalId(nextExternalId(orgId, req.getReportingYear()));
        // Force initial state + scrub server-owned columns.
        req.setStatus("DRAFT");
        req.setSentAt(null);
        req.setRespondedAt(null);
        req.setReviewedAt(null);
        req.setReportedScope12(null);
        req.setRenewablePct(null);
        req.setSaudizationPct(null);
        req.setHasNetZeroTarget(null);
        req.setIso14001(null);
        req.setEsgScore(null);
        req.setEvidenceItemId(null);
        req.setLinkedCapaId(null);
        req.setRejectReason(null);
        req.setDueDate(null);
        req.setCreatedBy(null);
        req.setUpdatedBy(null);
        VendorEsgSurvey saved = repo.save(req);
        audit.log("CREATE", "VendorEsgSurvey", saved.getId(),
            "Vendor ESG survey created for vendor " + vendor.getVendorName() + " (" + saved.getReportingYear() + ")");
        return saved;
    }

    /**
     * Issue the ESG-data request to the vendor: DRAFT → SENT. Stamps the send
     * time and a response deadline ({@value #DEFAULT_RESPONSE_WINDOW_DAYS} days)
     * that the escalation sweep watches.
     */
    @Transactional
    public VendorEsgSurvey send(Long id) {
        VendorEsgSurvey s = get(id);
        requireTransition(s.getStatus(), "SENT", Set.of("DRAFT"));
        s.setStatus("SENT");
        s.setSentAt(LocalDateTime.now());
        if (s.getDueDate() == null)
            s.setDueDate(LocalDate.now().plusDays(DEFAULT_RESPONSE_WINDOW_DAYS));
        VendorEsgSurvey saved = repo.save(s);
        audit.log("TRANSITION", "VendorEsgSurvey", saved.getId(),
            "Survey sent — response due " + saved.getDueDate());
        return saved;
    }

    /**
     * Record the vendor's ESG data: SENT|ESCALATED → RESPONDED (a late response
     * after escalation is still accepted). Recomputes {@code esgScore} from the
     * reported fields — the score is never client-supplied.
     */
    @Transactional
    public VendorEsgSurvey recordResponse(Long id, VendorEsgResponseRequest r) {
        VendorEsgSurvey s = get(id);
        requireTransition(s.getStatus(), "RESPONDED", Set.of("SENT", "ESCALATED"));
        s.setReportedScope12(r.reportedScope12());
        s.setRenewablePct(r.renewablePct());
        s.setSaudizationPct(r.saudizationPct());
        s.setHasNetZeroTarget(r.hasNetZeroTarget());
        s.setIso14001(r.iso14001());
        s.setStatus("RESPONDED");
        s.setRespondedAt(LocalDateTime.now());
        s.setEsgScore(computeEsgScore(s));
        VendorEsgSurvey saved = repo.save(s);
        audit.log("TRANSITION", "VendorEsgSurvey", saved.getId(),
            "Vendor responded — computed ESG score " + saved.getEsgScore());
        return saved;
    }

    /** Accept a response: RESPONDED → ACCEPTED. Reviewer is the authenticated principal. */
    @Transactional
    public VendorEsgSurvey accept(Long id, GrcUserDetails actor) {
        VendorEsgSurvey s = get(id);
        requireTransition(s.getStatus(), "ACCEPTED", Set.of("RESPONDED"));
        s.setStatus("ACCEPTED");
        s.setReviewedAt(LocalDateTime.now());
        if (actor != null) s.setReviewerName(actor.getUsername());
        VendorEsgSurvey saved = repo.save(s);
        audit.log("APPROVE", "VendorEsgSurvey", saved.getId(), "Vendor ESG response accepted");
        return saved;
    }

    /**
     * Reject a response: RESPONDED → REJECTED. Records the reason and opens a
     * VENDOR_ESG corrective action so the gap is tracked to closure.
     */
    @Transactional
    public VendorEsgSurvey reject(Long id, GrcUserDetails actor, String reason) {
        VendorEsgSurvey s = get(id);
        requireTransition(s.getStatus(), "REJECTED", Set.of("RESPONDED"));
        s.setStatus("REJECTED");
        s.setReviewedAt(LocalDateTime.now());
        s.setRejectReason(reason);
        if (actor != null) s.setReviewerName(actor.getUsername());
        CorrectiveAction capa = openVendorCapa(s,
            "Vendor ESG response rejected: " + s.getVendorNameEn(),
            "رُفض رد استبيان الاستدامة للمورد: " + safeAr(s),
            "The ESG data submitted by " + s.getVendorNameEn()
                + " was rejected" + (reason != null && !reason.isBlank() ? " (" + reason + ")" : "")
                + ". Follow up for a compliant resubmission.");
        s.setLinkedCapaId(capa.getId());
        VendorEsgSurvey saved = repo.save(s);
        audit.log("REJECT", "VendorEsgSurvey", saved.getId(),
            "Vendor ESG response rejected — CAPA " + capa.getId() + " opened");
        return saved;
    }

    /**
     * Non-response escalation sweep for the current tenant. Every SENT survey
     * whose deadline has passed moves to ESCALATED and opens (or reuses) a
     * VENDOR_ESG corrective action. Idempotent — the S-D auto-CAPA dedup means a
     * re-run never doubles CAPAs, and an already-ESCALATED survey (status ≠ SENT)
     * is not re-swept.
     *
     * @return the surveys escalated on this pass.
     */
    @Transactional
    public List<VendorEsgSurvey> sweepOverdue() {
        Long orgId = org();
        if (orgId == null) return List.of();
        List<VendorEsgSurvey> overdue =
            repo.findByOrganisationIdAndStatusAndDueDateBefore(orgId, "SENT", LocalDate.now());
        List<VendorEsgSurvey> escalated = new ArrayList<>();
        for (VendorEsgSurvey s : overdue) {
            s.setStatus("ESCALATED");
            CorrectiveAction capa = openVendorCapa(s,
                "Vendor ESG survey non-response: " + s.getVendorNameEn(),
                "عدم استجابة المورد لاستبيان الاستدامة: " + safeAr(s),
                "Vendor " + s.getVendorNameEn() + " did not submit ESG data by the "
                    + s.getDueDate() + " deadline. Escalate the outstanding request.");
            s.setLinkedCapaId(capa.getId());
            repo.save(s);
            audit.log("TRANSITION", "VendorEsgSurvey", s.getId(),
                "Escalated (non-response) — CAPA " + capa.getId() + " opened");
            escalated.add(s);
        }
        return escalated;
    }

    @Transactional
    public void softDelete(Long id) {
        VendorEsgSurvey s = get(id);
        s.setIsDeleted(true);
        repo.save(s);
        audit.log("DELETE", "VendorEsgSurvey", id, "Vendor ESG survey deleted");
    }

    // ── Aggregation ─────────────────────────────────────────────────
    /** KPI rollup for the surveillance dashboard tab. */
    public Map<String, Object> aggregate() {
        List<VendorEsgSurvey> all = repo.findByOrganisationId(org());
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : List.of("DRAFT", "SENT", "RESPONDED", "ACCEPTED", "REJECTED", "ESCALATED"))
            byStatus.put(s, 0);
        BigDecimal totalSpend = BigDecimal.ZERO, respondedSpend = BigDecimal.ZERO, reportedScope12 = BigDecimal.ZERO;
        BigDecimal scoreSum = BigDecimal.ZERO;
        int scored = 0;
        for (VendorEsgSurvey s : all) {
            byStatus.merge(s.getStatus(), 1, Integer::sum);
            if (s.getSpendSarK() != null) {
                totalSpend = totalSpend.add(s.getSpendSarK());
                if (isResponded(s.getStatus())) respondedSpend = respondedSpend.add(s.getSpendSarK());
            }
            if (s.getReportedScope12() != null) reportedScope12 = reportedScope12.add(s.getReportedScope12());
            if (s.getEsgScore() != null) { scoreSum = scoreSum.add(s.getEsgScore()); scored++; }
        }
        int respondedCount = byStatus.get("RESPONDED") + byStatus.get("ACCEPTED") + byStatus.get("REJECTED");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("byStatus", byStatus);
        out.put("responded", respondedCount);
        out.put("accepted", byStatus.get("ACCEPTED"));
        out.put("escalated", byStatus.get("ESCALATED"));
        out.put("outstanding", byStatus.get("SENT"));
        out.put("totalSpendSarK", totalSpend);
        // Scope-3 supplier-engagement spend coverage (responded ÷ total spend).
        out.put("spendCoveragePct", totalSpend.signum() == 0 ? BigDecimal.ZERO
            : respondedSpend.multiply(BigDecimal.valueOf(100)).divide(totalSpend, 1, RoundingMode.HALF_UP));
        out.put("reportedScope12", reportedScope12);
        out.put("avgEsgScore", scored == 0 ? BigDecimal.ZERO
            : scoreSum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Derive a 0..100 ESG score from the reported data (weights: renewable 30,
     * Saudization 20, net-zero target 25, ISO 14001 15, Scope-1+2 disclosed 10).
     * A vendor that reports nothing scores 0 — the score reflects real disclosure.
     */
    static BigDecimal computeEsgScore(VendorEsgSurvey s) {
        double score = 0;
        if (s.getRenewablePct() != null)   score += clampPct(s.getRenewablePct()) * 0.30;
        if (s.getSaudizationPct() != null) score += clampPct(s.getSaudizationPct()) * 0.20;
        if (Boolean.TRUE.equals(s.getHasNetZeroTarget())) score += 25;
        if (Boolean.TRUE.equals(s.getIso14001()))         score += 15;
        if (s.getReportedScope12() != null)               score += 10;
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private static int clampPct(int v) { return Math.max(0, Math.min(100, v)); }

    private static boolean isResponded(String status) {
        return "RESPONDED".equals(status) || "ACCEPTED".equals(status) || "REJECTED".equals(status);
    }

    private static String safeAr(VendorEsgSurvey s) {
        return s.getVendorNameAr() != null ? s.getVendorNameAr() : s.getVendorNameEn();
    }

    private CorrectiveAction openVendorCapa(VendorEsgSurvey s, String titleEn, String titleAr, String descEn) {
        return capaService.autoOpen("VENDOR_ESG", "VES-" + s.getId(), titleEn, titleAr, descEn, "HIGH");
    }

    private static void requireTransition(String from, String to, Set<String> allowedFrom) {
        if (!STATUSES.contains(to)) throw new IllegalArgumentException("Unknown status: " + to);
        if (!allowedFrom.contains(from))
            throw new IllegalTransitionException(from, to, allowedFrom);
    }

    private String nextExternalId(Long orgId, Short year) {
        String prefix = "VES-" + year + "-";
        int next = Optional.ofNullable(repo.findMaxExternalIdSeq(orgId, prefix)).orElse(0) + 1;
        return prefix + String.format("%04d", next);
    }
}
