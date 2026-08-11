package com.tss.grc.module.ndi.compliance.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.framework.entity.NdiFrameworkItem;
import com.tss.grc.module.ndi.framework.repository.NdiFrameworkItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * NDI Compliance Assessment service (slice 4).
 *
 * <p>Two parallel state spaces:
 * <ul>
 *   <li>{@code complianceStatus} — substantive answer (6 values).
 *       Rules 3, 4, 5 enforced here.</li>
 *   <li>{@code lifecycleStatus} — workflow position (DRAFT/IN_PROGRESS/
 *       SUBMITTED/UNDER_REVIEW/RETURNED/APPROVED).</li>
 * </ul>
 *
 * <p>Score model: COMPLIANT=100, PARTIAL=50, NON_COMPLIANT=0,
 * NOT_APPLICABLE/EVIDENCE_REQUIRED/UNDER_REVIEW=null (excluded from
 * denominator). The aggregate endpoint normalises across the cycle.
 */
@Service
@RequiredArgsConstructor
public class NdiComplianceAssessmentService {

    /**
     * F2-182 — was a bare {@code Set.of(...)}; now a vocabulary that names {@code ck_ndi_comp_status},
     * so {@code ClosedVocabularyPinTest} checks the six tokens against the migration.
     *
     * <p><b>⚠ Read this before believing either the old or the new claim about it.</b> F2-182's
     * register row said a lower-case {@code evidence_required} <em>bypasses the Rule 3 approval
     * gate</em>. Measured by reading the code: <b>it does not, and never could through the API.</b>
     * Every writer of this column is closed — {@link #setStatus} refused an unrecognised value with a
     * 400, {@link #update} silently ignored one, and the other three writers pass literals. The
     * finding's "open writer" classification came from a scan that saw
     * {@code cur.setComplianceStatus(req.getComplianceStatus())} and could not see the
     * {@code COMPLIANCE_STATUSES.contains(...) &&} guard in the same {@code if}. 🔑 <b>A static scan
     * cannot answer "is this writer closed" — the guard may be a same-line conjunction, a shared
     * validate() method, or a DTO annotation, and none of the three is visible at the setter.</b>
     *
     * <p>What <em>was</em> true is narrower and still worth closing: the gate's <b>read</b> was
     * case-sensitive, so a row carrying that casing from any other origin — a data fix, a restore, a
     * future importer — would have walked straight through an NCA/NDI approval gate. Reachability was
     * the only thing protecting it, and reachability is not a guarantee. The vocabulary makes the
     * question moot instead of unlikely.
     */
    private static final ClosedVocabulary COMPLIANCE_STATUS = ClosedVocabulary.of(
        "status", "ck_ndi_comp_status",
        "COMPLIANT", "PARTIAL", "NON_COMPLIANT", "NOT_APPLICABLE",
        "EVIDENCE_REQUIRED", "UNDER_REVIEW");

    /** The Rule 3 outcome that blocks approval — named because it is a control, not a label. */
    static final String STATUS_EVIDENCE_REQUIRED = "EVIDENCE_REQUIRED";
    private static final Set<String> LIFECYCLE_STATUSES = Set.of(
        "DRAFT", "IN_PROGRESS", "SUBMITTED", "UNDER_REVIEW", "RETURNED", "APPROVED");

    private final NdiComplianceAssessmentRepository repo;
    /** F2-46 — the cycle roll-up is written here, because this is where a child score changes. */
    private final com.tss.grc.module.ndi.cycle.repository.NdiAssessmentCycleRepository cycleRepo;
    private final NdiFrameworkItemRepository frameworkRepo;
    private final com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit;
    private final com.tss.grc.module.ndi.integration.NdiCrossLinkService crossLink;

    /** Read-through to the ERM risk row this compliance row points to. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public com.tss.grc.module.ndi.integration.NdiCrossLinkService.LinkedRisk getLinkedRisk(Long complianceId) {
        return crossLink.getLinkedRisk(get(complianceId).getRiskId());
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    /**
     * SEC-NDI-003: the acting identity on a privileged transition must come
     * from the authenticated principal, never from a request-body field —
     * otherwise the recorded approver/reviewer cannot be trusted. Mirrors the
     * SEC-NDI-002 pattern in {@code NdiAuditService.record()}.
     */
    private static String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<NdiComplianceAssessment> listByCycle(Long cycleId, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdOrderByDomainAscIdAsc(org(), cycleId, p);
    }
    public Page<NdiComplianceAssessment> listByCycleAndStatus(Long cycleId, String status, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdAndComplianceStatusOrderByDomainAscIdAsc(org(), cycleId, status, p);
    }
    public Page<NdiComplianceAssessment> listByCycleAndDomain(Long cycleId, String domain, Pageable p) {
        return repo.findByOrganisationIdAndCycleIdAndDomainOrderByIdAsc(org(), cycleId, domain, p);
    }
    public NdiComplianceAssessment get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Compliance assessment " + id));
    }

    // ── Bulk start — generate from framework items ──────────────────

    @Transactional
    public Map<String, Object> startForCycle(Long cycleId) {
        Long orgId = org();
        List<NdiFrameworkItem> items = frameworkRepo.findByOrganisationId(orgId).stream()
            .filter(it -> "COMPLIANCE".equals(it.getComponent()))
            .filter(it -> "ACTIVE".equals(it.getStatus()))
            .toList();
        int created = 0, skipped = 0;
        for (NdiFrameworkItem fi : items) {
            var existing = repo.findByOrganisationIdAndCycleIdAndFrameworkItemId(orgId, cycleId, fi.getId());
            if (existing.isPresent()) { skipped++; continue; }
            NdiComplianceAssessment row = new NdiComplianceAssessment();
            row.setOrganisationId(orgId);
            row.setCycleId(cycleId);
            row.setFrameworkItemId(fi.getId());
            row.setRequirementCode(fi.getRequirementCode());
            row.setComponent(fi.getComponent());
            row.setDomain(fi.getDomain());
            row.setSubdomain(fi.getSubdomain());
            row.setWeight(fi.getWeight() == null ? BigDecimal.ONE : fi.getWeight());
            row.setEvidenceRequired(Boolean.TRUE.equals(fi.getMandatory()));
            row.setComplianceStatus("UNDER_REVIEW");
            row.setLifecycleStatus("DRAFT");
            repo.save(row);
            created++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", created);
        out.put("skipped", skipped);
        out.put("totalComplianceItems", items.size());
        return out;
    }

    // ── Status changes (the substantive answer) ──────────────────────

    /**
     * Set the compliance status. Applies Rules 3, 4, 5 before save.
     */
    @Transactional
    public NdiComplianceAssessment setStatus(Long id, String newStatus, String naJustification) {
        NdiComplianceAssessment cur = get(id);
        if ("APPROVED".equals(cur.getLifecycleStatus())) {
            throw new IllegalStateException("Approved item is read-only. Reopen first.");
        }
        // F2-47 — an omitted status used to reach Set.of(...).contains(null), which THROWS, so a
        // request missing the field returned 500 instead of 400. Found while probing F2-46 with the
        // wrong body key: the controller reads body.get("status"), and any other key yields null.
        // F2-182 — was a case-SENSITIVE Set.contains, so this already refused 'evidence_required'
        // (which is why the gate was never bypassable). require() keeps the refusal for a token the
        // column cannot hold and additionally NORMALISES a casing it can, so an integration sending
        // 'compliant' now succeeds instead of receiving a 400 for a value the database accepts.
        newStatus = COMPLIANCE_STATUS.require(newStatus);
        // Rule 5 — NOT_APPLICABLE requires justification.
        if ("NOT_APPLICABLE".equals(newStatus)) {
            String j = naJustification != null && !naJustification.isBlank()
                ? naJustification : cur.getNaJustification();
            if (j == null || j.isBlank()) {
                throw new IllegalArgumentException(
                    "NOT_APPLICABLE requires a justification (Rule 5).");
            }
            cur.setNaJustification(j);
        }
        cur.setComplianceStatus(newStatus);
        applyRules(cur);
        if ("DRAFT".equals(cur.getLifecycleStatus())) cur.setLifecycleStatus("IN_PROGRESS");
        NdiComplianceAssessment saved = repo.save(cur);
        // F2-46 — applyRules() just moved this row's score, so the cycle roll-up is now stale.
        recomputeCycleComplianceScore(saved.getCycleId());
        return saved;
    }

    @Transactional
    public NdiComplianceAssessment update(Long id, NdiComplianceAssessment req) {
        NdiComplianceAssessment cur = get(id);
        if ("APPROVED".equals(cur.getLifecycleStatus())) {
            throw new IllegalStateException("Approved item is read-only. Reopen first.");
        }
        if (req.getResponseTextEn() != null)   cur.setResponseTextEn(req.getResponseTextEn());
        if (req.getResponseTextAr() != null)   cur.setResponseTextAr(req.getResponseTextAr());
        if (req.getEvidenceRef() != null)      cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getEvidenceStatus() != null)   cur.setEvidenceStatus(req.getEvidenceStatus());
        if (req.getNaJustification() != null)  cur.setNaJustification(req.getNaJustification());
        if (req.getControlOwnerName() != null) cur.setControlOwnerName(req.getControlOwnerName());
        if (req.getControlOwnerEmail() != null) cur.setControlOwnerEmail(req.getControlOwnerEmail());
        if (req.getEvidenceOwnerName() != null) cur.setEvidenceOwnerName(req.getEvidenceOwnerName());
        // SEC-NDI-003: reviewerName/Email are attribution fields for the
        // maker/checker SoD control — they are stamped server-side from the
        // authenticated principal at markUnderReview, never bound from the body.
        if (req.getNotes() != null)            cur.setNotes(req.getNotes());
        // F2-182 — this used to SILENTLY IGNORE an unrecognised status: the caller supplied a field,
        // got 200, and the status had not moved. That is a deliberate behaviour change, stated rather
        // than slipped in: an unrecognised token is now a 400 naming it, consistent with setStatus.
        // No request the register screen can send is affected (the status is a fixed dropdown); the
        // caller who benefits is the one whose casing differs, which now succeeds instead of no-oping.
        if (req.getComplianceStatus() != null) {
            cur.setComplianceStatus(COMPLIANCE_STATUS.require(req.getComplianceStatus()));
        }
        applyRules(cur);
        if ("DRAFT".equals(cur.getLifecycleStatus())
            && (cur.getResponseTextEn() != null || cur.getEvidenceRef() != null)) {
            cur.setLifecycleStatus("IN_PROGRESS");
        }
        NdiComplianceAssessment saved = repo.save(cur);
        // F2-46 — this path can change complianceStatus, so it can change the row's score too.
        recomputeCycleComplianceScore(saved.getCycleId());
        return saved;
    }

    /**
     * Attach evidence — convenience endpoint. If the row was sitting in
     * EVIDENCE_REQUIRED, flip back to UNDER_REVIEW so the reviewer can
     * re-classify with the new evidence in hand.
     */
    @Transactional
    public NdiComplianceAssessment attachEvidence(Long id, String evidenceRef, String evidenceOwner) {
        if (evidenceRef == null || evidenceRef.isBlank())
            throw new IllegalArgumentException("evidence_ref required");
        NdiComplianceAssessment cur = get(id);
        if ("APPROVED".equals(cur.getLifecycleStatus())) {
            throw new IllegalStateException("Approved item is read-only.");
        }
        cur.setEvidenceRef(evidenceRef);
        cur.setEvidenceStatus("ATTACHED");
        if (evidenceOwner != null) cur.setEvidenceOwnerName(evidenceOwner);
        if ("EVIDENCE_REQUIRED".equals(cur.getComplianceStatus())) {
            cur.setComplianceStatus("UNDER_REVIEW");
        }
        applyRules(cur);
        return repo.save(cur);
    }

    // ── Lifecycle transitions ────────────────────────────────────────

    @Transactional
    public NdiComplianceAssessment submit(Long id) {
        NdiComplianceAssessment cur = get(id);
        if (!"DRAFT".equals(cur.getLifecycleStatus())
            && !"IN_PROGRESS".equals(cur.getLifecycleStatus())
            && !"RETURNED".equals(cur.getLifecycleStatus())) {
            throw new IllegalTransitionException(cur.getLifecycleStatus(), "SUBMITTED",
                Set.of("DRAFT", "IN_PROGRESS", "RETURNED"));
        }
        String from = cur.getLifecycleStatus();
        applyRules(cur);
        cur.setLifecycleStatus("SUBMITTED");
        cur.setSubmittedAt(LocalDateTime.now());
        NdiComplianceAssessment saved = repo.save(cur);
        audit.publishTransition("COMPLIANCE", saved.getId(), saved.getRequirementCode(),
            saved.getCycleId(), from, "SUBMITTED", "Compliance assessment submitted");
        return saved;
    }

    @Transactional
    public NdiComplianceAssessment markUnderReview(Long id) {
        NdiComplianceAssessment cur = get(id);
        if (!"SUBMITTED".equals(cur.getLifecycleStatus())) {
            throw new IllegalTransitionException(cur.getLifecycleStatus(), "UNDER_REVIEW", Set.of("SUBMITTED"));
        }
        // SEC-NDI-003: reviewer identity is the authenticated principal, not a body field.
        cur.setReviewerName(currentUsername());
        cur.setLifecycleStatus("UNDER_REVIEW");
        cur.setReviewedAt(LocalDateTime.now());
        NdiComplianceAssessment saved = repo.save(cur);
        audit.publishTransition("COMPLIANCE", saved.getId(), saved.getRequirementCode(),
            saved.getCycleId(), "SUBMITTED", "UNDER_REVIEW", "Marked under review");
        return saved;
    }

    @Transactional
    public NdiComplianceAssessment returnForChanges(Long id, String reason) {
        NdiComplianceAssessment cur = get(id);
        if (!"UNDER_REVIEW".equals(cur.getLifecycleStatus())
            && !"SUBMITTED".equals(cur.getLifecycleStatus())) {
            throw new IllegalTransitionException(cur.getLifecycleStatus(), "RETURNED",
                Set.of("SUBMITTED", "UNDER_REVIEW"));
        }
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Return-for-changes requires a reason.");
        String from = cur.getLifecycleStatus();
        cur.setLifecycleStatus("RETURNED");
        cur.setReturnedAt(LocalDateTime.now());
        cur.setReturnReason(reason);
        NdiComplianceAssessment saved = repo.save(cur);
        audit.publishTransition("COMPLIANCE", saved.getId(), saved.getRequirementCode(),
            saved.getCycleId(), from, "RETURNED", "Returned: " + reason);
        return saved;
    }

    @Transactional
    public NdiComplianceAssessment approve(Long id) {
        NdiComplianceAssessment cur = get(id);
        if (!"UNDER_REVIEW".equals(cur.getLifecycleStatus())) {
            throw new IllegalTransitionException(cur.getLifecycleStatus(), "APPROVED", Set.of("UNDER_REVIEW"));
        }
        // Rule 3 sanity check at approval — refuse COMPLIANT/PARTIAL without evidence.
        applyRules(cur);
        // F2-182 — ClosedVocabulary#is, not equals, and this is the one place in this class where the
        // distinction earns its keep: this reads a STORED value whose origin the method cannot know.
        // Every API writer is closed, so no caller can produce 'evidence_required' — but a data fix, a
        // restore or a future importer could, and a case-sensitive equals would then wave it straight
        // through an NCA/NDI approval gate. Reachability was the only thing protecting this, and
        // "nobody can currently reach it" is a fact about today, not a property of the control.
        if (COMPLIANCE_STATUS.is(cur.getComplianceStatus(), STATUS_EVIDENCE_REQUIRED)) {
            throw new IllegalArgumentException(
                "Cannot approve: item is flagged EVIDENCE_REQUIRED (Rule 3). Attach evidence first.");
        }
        // SEC-NDI-003: the approver is the authenticated principal, not a body
        // field; and segregation-of-duties requires the approver to differ from
        // the reviewer who marked the item UNDER_REVIEW.
        String approverName = currentUsername();
        if (approverName != null && approverName.equalsIgnoreCase(cur.getReviewerName())) {
            throw new IllegalStateException(
                "Segregation of duties: the reviewer cannot also approve this assessment.");
        }
        cur.setLifecycleStatus("APPROVED");
        cur.setApprovedAt(LocalDateTime.now());
        cur.setApproverName(approverName);
        NdiComplianceAssessment saved = repo.save(cur);
        audit.publishTransition("COMPLIANCE", saved.getId(), saved.getRequirementCode(),
            saved.getCycleId(), "UNDER_REVIEW", "APPROVED", "Approved by " + approverName);
        return saved;
    }

    @Transactional
    public NdiComplianceAssessment reopen(Long id) {
        NdiComplianceAssessment cur = get(id);
        if (!"APPROVED".equals(cur.getLifecycleStatus())) {
            throw new IllegalTransitionException(cur.getLifecycleStatus(), "IN_PROGRESS", Set.of("APPROVED"));
        }
        cur.setLifecycleStatus("IN_PROGRESS");
        cur.setApprovedAt(null);
        cur.setApproverName(null);
        NdiComplianceAssessment saved = repo.save(cur);
        audit.publishTransition("COMPLIANCE", saved.getId(), saved.getRequirementCode(),
            saved.getCycleId(), "APPROVED", "IN_PROGRESS", "Reopened from APPROVED");
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        NdiComplianceAssessment cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.publish("COMPLIANCE", cur.getId(), cur.getRequirementCode(),
            cur.getCycleId(), "DELETE", "WARN", "Compliance assessment soft-deleted");
    }

    // ── Aggregate ────────────────────────────────────────────────────

    public Map<String, Object> aggregateForCycle(Long cycleId) {
        List<NdiComplianceAssessment> all = repo.findByOrganisationIdAndCycleId(org(), cycleId);

        Map<String, Integer> byCompliance = new LinkedHashMap<>();
        for (String s : COMPLIANCE_STATUS.tokens()) byCompliance.put(s, 0);
        Map<String, Integer> byLifecycle = new LinkedHashMap<>();
        for (String s : LIFECYCLE_STATUSES) byLifecycle.put(s, 0);
        Map<String, BigDecimal[]> domainAccum = new LinkedHashMap<>();  // [score, weight]

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int needsGap = 0, evidenceRequired = 0;

        for (NdiComplianceAssessment a : all) {
            byCompliance.merge(a.getComplianceStatus(), 1, Integer::sum);
            byLifecycle.merge(a.getLifecycleStatus(), 1, Integer::sum);

            BigDecimal score = scoreFor(a.getComplianceStatus());
            BigDecimal w = a.getWeight() == null ? BigDecimal.ONE : a.getWeight();
            if (score != null) {
                BigDecimal contribution = score.multiply(w);
                totalScore  = totalScore.add(contribution);
                totalWeight = totalWeight.add(w);
                domainAccum.computeIfAbsent(a.getDomain(), k -> new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO });
                BigDecimal[] acc = domainAccum.get(a.getDomain());
                acc[0] = acc[0].add(contribution);
                acc[1] = acc[1].add(w);
            }
            if (Boolean.TRUE.equals(a.getNeedsGap()))                   needsGap++;
            if ("EVIDENCE_REQUIRED".equals(a.getComplianceStatus()))    evidenceRequired++;
        }

        BigDecimal compliancePct = totalWeight.signum() == 0
            ? BigDecimal.ZERO
            : totalScore.divide(totalWeight, 2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> byDomainPct = new LinkedHashMap<>();
        domainAccum.forEach((d, acc) -> {
            BigDecimal pct = acc[1].signum() == 0
                ? BigDecimal.ZERO
                : acc[0].divide(acc[1], 2, RoundingMode.HALF_UP);
            byDomainPct.put(d, pct);
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cycleId", cycleId);
        out.put("total", all.size());
        out.put("compliancePct", compliancePct);
        out.put("byCompliance", byCompliance);
        out.put("byLifecycle", byLifecycle);
        out.put("byDomainPct", byDomainPct);
        out.put("needsGapCount", needsGap);
        out.put("evidenceRequiredCount", evidenceRequired);
        return out;
    }

    /** Status → numeric score for the cycle rollup. */
    private static BigDecimal scoreFor(String status) {
        return switch (status) {
            case "COMPLIANT"     -> new BigDecimal("100");
            case "PARTIAL"       -> new BigDecimal("50");
            case "NON_COMPLIANT" -> BigDecimal.ZERO;
            default -> null;  // NA / EVIDENCE_REQUIRED / UNDER_REVIEW excluded
        };
    }

    // ── Rule enforcement ────────────────────────────────────────────

    /**
     * Apply Rules 3 + 4 + 5 against the entity before save.
     *   Rule 3 — COMPLIANT or PARTIAL with no evidence → flip to EVIDENCE_REQUIRED.
     *   Rule 4 — PARTIAL or NON_COMPLIANT → set needsGap = true.
     *   Rule 5 — NOT_APPLICABLE without justification → reject (handled
     *            in setStatus before this runs; here we re-check).
     */
    private void applyRules(NdiComplianceAssessment a) {
        String s = a.getComplianceStatus();
        boolean hasEvidence = a.getEvidenceRef() != null && !a.getEvidenceRef().isBlank();

        // Rule 3
        if (("COMPLIANT".equals(s) || "PARTIAL".equals(s)) && !hasEvidence) {
            a.setComplianceStatus("EVIDENCE_REQUIRED");
            s = "EVIDENCE_REQUIRED";
        }

        // Rule 4
        a.setNeedsGap("PARTIAL".equals(s) || "NON_COMPLIANT".equals(s));

        // Rule 5 — re-guard at save time too.
        if ("NOT_APPLICABLE".equals(s)
            && (a.getNaJustification() == null || a.getNaJustification().isBlank())) {
            throw new IllegalArgumentException(
                "NOT_APPLICABLE requires na_justification (Rule 5).");
        }

        // Recompute score for the row.
        a.setComplianceScore(scoreFor(s));
    }

    // ── F2-46: the cycle's compliance score is derived, not declared ──────────────────

    /**
     * Re-derive {@code ndi.assessment_cycle.compliance_score} from this cycle's assessment rows.
     *
     * <p><b>Why this method did not exist.</b> The per-item score has always been computed
     * ({@link #scoreFor}), but the cycle-level roll-up had <em>no setter anywhere in the
     * codebase</em> — the column was written only by whatever arrived in
     * {@code NdiAssessmentCycleService.create()}'s request body. Four consumers read it, and one of
     * them is {@code NdiSubmissionPackageService}, the package submitted to the regulator. Measured
     * before this fix: cycle 2 asserted 68.00 against a derivable 66.43.
     *
     * <p><b>What counts.</b> The mean of the child rows that carry a score. {@code scoreFor} returns
     * {@code null} for {@code NOT_APPLICABLE}, {@code EVIDENCE_REQUIRED} and {@code UNDER_REVIEW},
     * so those are excluded from both numerator and denominator — an item nobody has assessed yet
     * must not drag the percentage down, and an N/A item is not a failure. That matches how
     * {@code NdiDashboardService} and {@code NdiSubmissionPackageService} already average the column.
     *
     * <p><b>No scored rows leaves the score untouched</b> rather than writing zero: "nothing assessed
     * yet" and "assessed at zero" are different facts, and the submission package distinguishes them
     * by NULL. Consistent with V323 leaving unscored cycles unlabelled.
     *
     * @return the cycle as persisted, or {@code null} if the cycle is absent or has no scored rows
     */
    @Transactional
    public com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle recomputeCycleComplianceScore(
            Long cycleId) {
        if (cycleId == null) {
            return null;
        }
        com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle cycle = cycleRepo.findById(cycleId)
            .filter(c -> c.getOrganisationId().equals(org()))
            .orElse(null);
        if (cycle == null) {
            return null;
        }
        List<NdiComplianceAssessment> rows = repo.findByOrganisationIdAndCycleId(org(), cycleId);
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (NdiComplianceAssessment a : rows) {
            if (a.getComplianceScore() != null) {
                sum = sum.add(a.getComplianceScore());
                n++;
            }
        }
        if (n == 0) {
            return cycle;
        }
        cycle.setComplianceScore(sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
        cycle.setScoreSource("COMPUTED");
        cycle.setScoreComputedAt(LocalDateTime.now());
        return cycleRepo.save(cycle);
    }
}
