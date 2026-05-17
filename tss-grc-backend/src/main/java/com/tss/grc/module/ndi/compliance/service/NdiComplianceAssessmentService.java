package com.tss.grc.module.ndi.compliance.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.framework.entity.NdiFrameworkItem;
import com.tss.grc.module.ndi.framework.repository.NdiFrameworkItemRepository;
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

    private static final Set<String> COMPLIANCE_STATUSES = Set.of(
        "COMPLIANT", "PARTIAL", "NON_COMPLIANT", "NOT_APPLICABLE",
        "EVIDENCE_REQUIRED", "UNDER_REVIEW");
    private static final Set<String> LIFECYCLE_STATUSES = Set.of(
        "DRAFT", "IN_PROGRESS", "SUBMITTED", "UNDER_REVIEW", "RETURNED", "APPROVED");

    private final NdiComplianceAssessmentRepository repo;
    private final NdiFrameworkItemRepository frameworkRepo;
    private final com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit;
    private final com.tss.grc.module.ndi.integration.NdiCrossLinkService crossLink;

    /** Read-through to the ERM risk row this compliance row points to. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public com.tss.grc.module.ndi.integration.NdiCrossLinkService.LinkedRisk getLinkedRisk(Long complianceId) {
        return crossLink.getLinkedRisk(get(complianceId).getRiskId());
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

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
        if (!COMPLIANCE_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Invalid compliance_status: " + newStatus);
        }
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
        return repo.save(cur);
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
        if (req.getReviewerName() != null)     cur.setReviewerName(req.getReviewerName());
        if (req.getReviewerEmail() != null)    cur.setReviewerEmail(req.getReviewerEmail());
        if (req.getNotes() != null)            cur.setNotes(req.getNotes());
        if (req.getComplianceStatus() != null
            && COMPLIANCE_STATUSES.contains(req.getComplianceStatus())) {
            cur.setComplianceStatus(req.getComplianceStatus());
        }
        applyRules(cur);
        if ("DRAFT".equals(cur.getLifecycleStatus())
            && (cur.getResponseTextEn() != null || cur.getEvidenceRef() != null)) {
            cur.setLifecycleStatus("IN_PROGRESS");
        }
        return repo.save(cur);
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
    public NdiComplianceAssessment markUnderReview(Long id, String reviewerName) {
        NdiComplianceAssessment cur = get(id);
        if (!"SUBMITTED".equals(cur.getLifecycleStatus())) {
            throw new IllegalTransitionException(cur.getLifecycleStatus(), "UNDER_REVIEW", Set.of("SUBMITTED"));
        }
        if (reviewerName != null && !reviewerName.isBlank()) cur.setReviewerName(reviewerName);
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
    public NdiComplianceAssessment approve(Long id, String approverName) {
        NdiComplianceAssessment cur = get(id);
        if (!"UNDER_REVIEW".equals(cur.getLifecycleStatus())) {
            throw new IllegalTransitionException(cur.getLifecycleStatus(), "APPROVED", Set.of("UNDER_REVIEW"));
        }
        // Rule 3 sanity check at approval — refuse COMPLIANT/PARTIAL without evidence.
        applyRules(cur);
        if ("EVIDENCE_REQUIRED".equals(cur.getComplianceStatus())) {
            throw new IllegalArgumentException(
                "Cannot approve: item is flagged EVIDENCE_REQUIRED (Rule 3). Attach evidence first.");
        }
        cur.setLifecycleStatus("APPROVED");
        cur.setApprovedAt(LocalDateTime.now());
        if (approverName != null && !approverName.isBlank()) cur.setApproverName(approverName);
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
        for (String s : COMPLIANCE_STATUSES) byCompliance.put(s, 0);
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
}
