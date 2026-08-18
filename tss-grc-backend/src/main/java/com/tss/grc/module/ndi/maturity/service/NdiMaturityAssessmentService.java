package com.tss.grc.module.ndi.maturity.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.framework.entity.NdiFrameworkItem;
import com.tss.grc.module.ndi.framework.repository.NdiFrameworkItemRepository;
import com.tss.grc.module.ndi.maturity.entity.NdiMaturityAssessment;
import com.tss.grc.module.ndi.maturity.repository.NdiMaturityAssessmentRepository;
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
 * NDI Maturity Assessment service (slice 3).
 *
 * <p>State machine:
 * <pre>
 *   DRAFT → IN_PROGRESS → SUBMITTED → UNDER_REVIEW
 *                                   → RETURNED → IN_PROGRESS (loop)
 *                                   → APPROVED   (terminal until reopened)
 * </pre>
 *
 * <p>Three rules enforced:
 * <ul>
 *   <li><b>Rule 1</b> — service auto-flips improvementActionSuggested
 *       when target_level > current_level on save.</li>
 *   <li><b>Rule 2</b> — current_level ≥ 4 requires evidence_ref before
 *       transitioning to SUBMITTED.</li>
 *   <li><b>D.Validation</b> — target_level < current_level requires
 *       justification text.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NdiMaturityAssessmentService {

    /** Forward-only flow + RETURNED loops back to IN_PROGRESS. */
    private static final Set<String> STATUSES = Set.of(
        "DRAFT", "IN_PROGRESS", "SUBMITTED", "UNDER_REVIEW", "RETURNED", "APPROVED");

    private final NdiMaturityAssessmentRepository repo;
    private final NdiFrameworkItemRepository frameworkRepo;
    private final com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<NdiMaturityAssessment> listByCycle(Long cycleId, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdOrderByDomainAscIdAsc(org(), cycleId, p);
    }
    public Page<NdiMaturityAssessment> listByCycleAndStatus(Long cycleId, String status, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdAndStatusOrderByDomainAscIdAsc(org(), cycleId, status, p);
    }
    public Page<NdiMaturityAssessment> listByCycleAndDomain(Long cycleId, String domain, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdAndDomainOrderByIdAsc(org(), cycleId, domain, p);
    }
    public NdiMaturityAssessment get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Maturity assessment " + id));
    }

    // ── Bulk start — generates assessment rows from framework items ──

    /**
     * Generate maturity-assessment rows for a cycle by copying every
     * {@code MATURITY}-component framework item that doesn't already
     * have a row in this cycle. Idempotent — safe to re-run.
     *
     * <p>Returns {@code { created, skipped }} so the UI can show
     * "N rows created (M already existed)".
     */
    @Transactional
    public Map<String, Object> startForCycle(Long cycleId) {
        Long orgId = org();
        List<NdiFrameworkItem> matItems = frameworkRepo.findByOrganisationId(orgId).stream()
            .filter(it -> "MATURITY".equals(it.getComponent()))
            .filter(it -> "ACTIVE".equals(it.getStatus()))
            .toList();

        int created = 0, skipped = 0;
        for (NdiFrameworkItem fi : matItems) {
            var existing = repo.findByOrganisationIdAndCycleIdAndFrameworkItemId(orgId, cycleId, fi.getId());
            if (existing.isPresent()) { skipped++; continue; }
            NdiMaturityAssessment row = new NdiMaturityAssessment();
            row.setOrganisationId(orgId);
            row.setCycleId(cycleId);
            row.setFrameworkItemId(fi.getId());
            row.setComponent(fi.getComponent());
            row.setDomain(fi.getDomain());
            row.setSubdomain(fi.getSubdomain());
            row.setWeight(fi.getWeight() == null ? BigDecimal.ONE : fi.getWeight());
            row.setEvidenceRequired(Boolean.TRUE.equals(fi.getMandatory()));
            row.setStatus("DRAFT");
            repo.save(row);
            created++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", created);
        out.put("skipped", skipped);
        out.put("totalMaturityItems", matItems.size());
        return out;
    }

    // ── Update + transitions ─────────────────────────────────────────

    @Transactional
    public NdiMaturityAssessment update(Long id, NdiMaturityAssessment req) {
        NdiMaturityAssessment cur = get(id);
        if ("APPROVED".equals(cur.getStatus())) {
            throw new IllegalStateException("Approved item is read-only. Reopen first.");
        }
        if (req.getCurrentLevel() != null)        cur.setCurrentLevel(req.getCurrentLevel());
        if (req.getTargetLevel() != null)         cur.setTargetLevel(req.getTargetLevel());
        if (req.getResponseTextEn() != null)      cur.setResponseTextEn(req.getResponseTextEn());
        if (req.getResponseTextAr() != null)      cur.setResponseTextAr(req.getResponseTextAr());
        if (req.getEvidenceRef() != null)         cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getJustification() != null)       cur.setJustification(req.getJustification());
        if (req.getAssessorName() != null)        cur.setAssessorName(req.getAssessorName());
        if (req.getAssessorEmail() != null)       cur.setAssessorEmail(req.getAssessorEmail());
        if (req.getDomainOwnerName() != null)     cur.setDomainOwnerName(req.getDomainOwnerName());
        // F2-236 — `reviewer_name` carries TWO meanings on this column, and only one of them is the
        // caller's. Before the review it is an ASSIGNMENT (measured: 8 of 16 live rows in dev and
        // prod name an office — "Chief Data Officer" — with reviewed_at NULL, so no review has
        // happened); from markUnderReview() onwards it is the ATTRIBUTION of who actually reviewed,
        // stamped from the principal beside reviewedAt.
        //
        // So this route keeps the assignment and freezes the attribution. Without the freeze,
        // closing markUnderReview() would have been pointless: the ordinary edit route, under the
        // same NDI:WRITE, could overwrite the recorded reviewer with anything (F2-222's shape — a
        // control's record defeated through an adjacent route with the same permission). An ECHO of
        // the stored value is still accepted, so a form that round-trips the row keeps working
        // (F2-231).
        if (req.getReviewerName() != null && !req.getReviewerName().equals(cur.getReviewerName())) {
            if (cur.getReviewedAt() != null)
                throw new IllegalArgumentException(
                    "The reviewer of this assessment was recorded when it was marked under review ("
                    + cur.getReviewerName() + ") and is not editable here. Reopen the assessment to "
                    + "reassign it.");
            cur.setReviewerName(req.getReviewerName());
        }
        if (req.getReviewerEmail() != null)       cur.setReviewerEmail(req.getReviewerEmail());
        if (req.getNotes() != null)               cur.setNotes(req.getNotes());

        validate(cur);
        applyRules(cur);
        // Once an assessor touches a DRAFT row, move it to IN_PROGRESS
        // unless an explicit transition is in flight.
        if ("DRAFT".equals(cur.getStatus())
            && (cur.getCurrentLevel() != null || cur.getResponseTextEn() != null)) {
            cur.setStatus("IN_PROGRESS");
        }
        return repo.save(cur);
    }

    @Transactional
    public NdiMaturityAssessment submit(Long id) {
        NdiMaturityAssessment cur = get(id);
        if (!"IN_PROGRESS".equals(cur.getStatus()) && !"DRAFT".equals(cur.getStatus()) && !"RETURNED".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "SUBMITTED", Set.of("IN_PROGRESS", "DRAFT", "RETURNED"));
        }
        if (cur.getCurrentLevel() == null) {
            throw new IllegalArgumentException("Cannot submit without a current_level response.");
        }
        // Rule 2 — levels 4-5 require evidence.
        if (cur.getCurrentLevel() >= 4
            && (cur.getEvidenceRef() == null || cur.getEvidenceRef().isBlank())) {
            throw new IllegalArgumentException(
                "Current level " + cur.getCurrentLevel() + " requires evidence_ref (Rule 2). Attach evidence before submitting.");
        }
        String from = cur.getStatus();
        cur.setStatus("SUBMITTED");
        cur.setSubmittedAt(LocalDateTime.now());
        applyRules(cur);
        NdiMaturityAssessment saved = repo.save(cur);
        audit.publishTransition("MATURITY", saved.getId(), "FI-" + saved.getFrameworkItemId(),
            saved.getCycleId(), from, "SUBMITTED", "Maturity assessment submitted for review");
        return saved;
    }

    @Transactional
    /** F2-236 — the reviewer is the authenticated principal, not the request body. */
    public NdiMaturityAssessment markUnderReview(Long id) {
        NdiMaturityAssessment cur = get(id);
        if (!"SUBMITTED".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "UNDER_REVIEW", Set.of("SUBMITTED"));
        }
        cur.setReviewerName(CurrentActor.requireUsername("a maturity review"));
        cur.setStatus("UNDER_REVIEW");
        cur.setReviewedAt(LocalDateTime.now());
        NdiMaturityAssessment saved = repo.save(cur);
        audit.publishTransition("MATURITY", saved.getId(), "FI-" + saved.getFrameworkItemId(),
            saved.getCycleId(), "SUBMITTED", "UNDER_REVIEW", "Marked under review");
        return saved;
    }

    @Transactional
    public NdiMaturityAssessment returnForChanges(Long id, String reason) {
        NdiMaturityAssessment cur = get(id);
        if (!"UNDER_REVIEW".equals(cur.getStatus()) && !"SUBMITTED".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "RETURNED", Set.of("SUBMITTED", "UNDER_REVIEW"));
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Return-for-changes requires a reason.");
        }
        String from = cur.getStatus();
        cur.setStatus("RETURNED");
        cur.setReturnedAt(LocalDateTime.now());
        cur.setReturnReason(reason);
        NdiMaturityAssessment saved = repo.save(cur);
        audit.publishTransition("MATURITY", saved.getId(), "FI-" + saved.getFrameworkItemId(),
            saved.getCycleId(), from, "RETURNED", "Returned: " + reason);
        return saved;
    }

    @Transactional
    /** F2-236 — the approver is the authenticated principal, not the request body. */
    public NdiMaturityAssessment approve(Long id) {
        NdiMaturityAssessment cur = get(id);
        if (!"UNDER_REVIEW".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "APPROVED", Set.of("UNDER_REVIEW"));
        }
        // Rule 2 sanity-check at approval time too.
        if (cur.getCurrentLevel() != null && cur.getCurrentLevel() >= 4
            && (cur.getEvidenceRef() == null || cur.getEvidenceRef().isBlank())) {
            throw new IllegalArgumentException(
                "Approval blocked: level " + cur.getCurrentLevel() + " requires evidence_ref (Rule 2).");
        }
        cur.setStatus("APPROVED");
        cur.setApprovedAt(LocalDateTime.now());
        cur.setApproverName(CurrentActor.requireUsername("a maturity approval"));
        NdiMaturityAssessment saved = repo.save(cur);
        audit.publishTransition("MATURITY", saved.getId(), "FI-" + saved.getFrameworkItemId(),
            saved.getCycleId(), "UNDER_REVIEW", "APPROVED", "Approved by " + saved.getApproverName());
        return saved;
    }

    @Transactional
    public NdiMaturityAssessment reopen(Long id) {
        NdiMaturityAssessment cur = get(id);
        if (!"APPROVED".equals(cur.getStatus())) {
            throw new IllegalTransitionException(cur.getStatus(), "IN_PROGRESS", Set.of("APPROVED"));
        }
        cur.setStatus("IN_PROGRESS");
        cur.setApprovedAt(null);
        cur.setApproverName(null);
        NdiMaturityAssessment saved = repo.save(cur);
        audit.publishTransition("MATURITY", saved.getId(), "FI-" + saved.getFrameworkItemId(),
            saved.getCycleId(), "APPROVED", "IN_PROGRESS", "Reopened from APPROVED");
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        NdiMaturityAssessment cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.publish("MATURITY", cur.getId(), "FI-" + cur.getFrameworkItemId(),
            cur.getCycleId(), "DELETE", "WARN", "Maturity assessment soft-deleted");
    }

    // ── Aggregate ─────────────────────────────────────────────────────

    /**
     * Per-cycle rollup: maturity score (0..100 normalised), by-level
     * counts, by-domain mean, status counts, Rule-2 violation count
     * (level ≥ 4 with no evidence).
     */
    public Map<String, Object> aggregateForCycle(Long cycleId) {
        List<NdiMaturityAssessment> all = repo.findByOrganisationIdAndCycleId(org(), cycleId);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : STATUSES) byStatus.put(s, 0);
        Map<Integer, Integer> byLevel = new LinkedHashMap<>();
        for (int i = 0; i <= 5; i++) byLevel.put(i, 0);

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int suggested = 0, rule2Violations = 0, unresponded = 0;
        Map<String, BigDecimal[]> domainAccum = new LinkedHashMap<>(); // [sum, weight]

        for (NdiMaturityAssessment a : all) {
            byStatus.merge(a.getStatus(), 1, Integer::sum);
            if (a.getCurrentLevel() == null) { unresponded++; continue; }

            int lvl = a.getCurrentLevel();
            byLevel.merge(lvl, 1, Integer::sum);

            BigDecimal w = a.getWeight() == null ? BigDecimal.ONE : a.getWeight();
            BigDecimal contribution = BigDecimal.valueOf(lvl).multiply(w);
            totalScore  = totalScore.add(contribution);
            totalWeight = totalWeight.add(w);

            domainAccum.computeIfAbsent(a.getDomain(), k -> new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO });
            BigDecimal[] acc = domainAccum.get(a.getDomain());
            acc[0] = acc[0].add(contribution);
            acc[1] = acc[1].add(w);

            if (Boolean.TRUE.equals(a.getImprovementActionSuggested())) suggested++;
            if (lvl >= 4 && (a.getEvidenceRef() == null || a.getEvidenceRef().isBlank())) rule2Violations++;
        }

        // Normalise: score / (totalWeight * 5) * 100 → 0..100.
        BigDecimal maturityPct = totalWeight.signum() == 0
            ? BigDecimal.ZERO
            : totalScore.multiply(BigDecimal.valueOf(100))
                        .divide(totalWeight.multiply(BigDecimal.valueOf(5)), 2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> byDomain = new LinkedHashMap<>();
        domainAccum.forEach((d, acc) -> {
            BigDecimal pct = acc[1].signum() == 0
                ? BigDecimal.ZERO
                : acc[0].multiply(BigDecimal.valueOf(100))
                        .divide(acc[1].multiply(BigDecimal.valueOf(5)), 2, RoundingMode.HALF_UP);
            byDomain.put(d, pct);
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cycleId", cycleId);
        out.put("total", all.size());
        out.put("unresponded", unresponded);
        out.put("maturityScorePct", maturityPct);
        out.put("byStatus", byStatus);
        out.put("byLevel", byLevel);
        out.put("byDomainPct", byDomain);
        out.put("improvementSuggestedCount", suggested);
        out.put("rule2ViolationCount", rule2Violations);
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Rules + score recompute. Called after every successful update. */
    private void applyRules(NdiMaturityAssessment a) {
        // Rule 1 — target > current marks improvement suggestion.
        if (a.getTargetLevel() != null && a.getCurrentLevel() != null) {
            a.setImprovementActionSuggested(a.getTargetLevel() > a.getCurrentLevel());
        } else {
            a.setImprovementActionSuggested(false);
        }
        // Recompute score = current_level × weight.
        if (a.getCurrentLevel() != null) {
            BigDecimal w = a.getWeight() == null ? BigDecimal.ONE : a.getWeight();
            a.setScore(BigDecimal.valueOf(a.getCurrentLevel()).multiply(w));
        }
    }

    private void validate(NdiMaturityAssessment a) {
        if (a.getCycleId() == null)         throw new IllegalArgumentException("cycleId required");
        if (a.getFrameworkItemId() == null) throw new IllegalArgumentException("frameworkItemId required");
        if (a.getCurrentLevel() != null && (a.getCurrentLevel() < 0 || a.getCurrentLevel() > 5))
            throw new IllegalArgumentException("current_level must be 0..5");
        if (a.getTargetLevel() != null && (a.getTargetLevel() < 0 || a.getTargetLevel() > 5))
            throw new IllegalArgumentException("target_level must be 0..5");
        // D.Validation — target < current requires justification.
        if (a.getCurrentLevel() != null && a.getTargetLevel() != null
            && a.getTargetLevel() < a.getCurrentLevel()
            && (a.getJustification() == null || a.getJustification().isBlank())) {
            throw new IllegalArgumentException(
                "target_level (" + a.getTargetLevel() + ") < current_level (" + a.getCurrentLevel()
                + ") requires a justification.");
        }
        if (!STATUSES.contains(a.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
    }
}
