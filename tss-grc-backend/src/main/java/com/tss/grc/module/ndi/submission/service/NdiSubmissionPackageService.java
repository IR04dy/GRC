package com.tss.grc.module.ndi.submission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle;
import com.tss.grc.module.ndi.cycle.repository.NdiAssessmentCycleRepository;
import com.tss.grc.module.ndi.domain.service.NdiGovernanceDomainService;
import com.tss.grc.module.ndi.domain.service.NdiGovernanceDomainService.CycleRollup;
import com.tss.grc.module.ndi.evidence.entity.NdiEvidenceItem;
import com.tss.grc.module.ndi.evidence.repository.NdiEvidenceItemRepository;
import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import com.tss.grc.module.ndi.gap.entity.NdiGap;
import com.tss.grc.module.ndi.gap.repository.NdiCorrectiveActionRepository;
import com.tss.grc.module.ndi.gap.repository.NdiGapRepository;
import com.tss.grc.module.ndi.maturity.entity.NdiMaturityAssessment;
import com.tss.grc.module.ndi.maturity.repository.NdiMaturityAssessmentRepository;
import com.tss.grc.module.ndi.opex.entity.NdiOperationalExcellenceKpi;
import com.tss.grc.module.ndi.opex.repository.NdiOperationalExcellenceKpiRepository;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlan;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanRepository;
import com.tss.grc.module.ndi.submission.entity.NdiSubmissionPackage;
import com.tss.grc.module.ndi.submission.repository.NdiSubmissionPackageRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * NDI Submission Package service — slice 10.
 *
 * <p>Builds the immutable snapshot the entity submits to SDAIA at the
 * close of each cycle. Pulls live state from every NDI slice, freezes
 * it into snapshot_json + manifest_json, and gates the workflow
 * transitions per Rule 15 (pre-flight) and Rule 16 (immutability).
 */
@Service
@Transactional
public class NdiSubmissionPackageService {

    private final NdiSubmissionPackageRepository packages;
    private final NdiAssessmentCycleRepository cycles;
    private final NdiMaturityAssessmentRepository maturity;
    private final NdiComplianceAssessmentRepository compliance;
    private final NdiOperationalExcellenceKpiRepository opex;
    private final NdiEvidenceItemRepository evidence;
    private final NdiGapRepository gaps;
    private final NdiCorrectiveActionRepository capas;
    private final NdiImprovementPlanRepository plans;
    private final NdiGovernanceDomainService domainService;
    private final ObjectMapper json;
    private final NdiAuditPublisher audit;

    public NdiSubmissionPackageService(NdiSubmissionPackageRepository packages,
                                       NdiAssessmentCycleRepository cycles,
                                       NdiMaturityAssessmentRepository maturity,
                                       NdiComplianceAssessmentRepository compliance,
                                       NdiOperationalExcellenceKpiRepository opex,
                                       NdiEvidenceItemRepository evidence,
                                       NdiGapRepository gaps,
                                       NdiCorrectiveActionRepository capas,
                                       NdiImprovementPlanRepository plans,
                                       NdiGovernanceDomainService domainService,
                                       ObjectMapper json,
                                       NdiAuditPublisher audit) {
        this.packages = packages;
        this.cycles = cycles;
        this.maturity = maturity;
        this.compliance = compliance;
        this.opex = opex;
        this.evidence = evidence;
        this.gaps = gaps;
        this.capas = capas;
        this.plans = plans;
        this.domainService = domainService;
        this.json = json;
        this.audit = audit;
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    /**
     * SEC-NDI-003: the acting identity on a regulator-facing transition must
     * come from the authenticated principal, never from a request-body field —
     * otherwise the recorded approver/submitter cannot be trusted. Mirrors the
     * SEC-NDI-002 pattern in {@code NdiAuditService.record()}.
     */
    private static String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    public record Preflight(
        Long cycleId,
        int openCriticalGaps,
        int criticalGapsWithoutCapa,
        int complianceNotApproved,
        int maturityNotApproved,
        int expiredEvidence,
        boolean canFinalize,
        List<String> blockers,
        List<String> warnings
    ) {}

    // ─── CRUD + workflow ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NdiSubmissionPackage> listForCycle(Long cycleId) {
        return packages.findByOrganisationIdAndCycleIdOrderByVersionDesc(org(), cycleId);
    }

    @Transactional(readOnly = true)
    public NdiSubmissionPackage get(Long id) {
        return packages.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Submission package not found: " + id));
    }

    /** Create a new DRAFT package for the given cycle and immediately populate its snapshot. */
    public NdiSubmissionPackage create(Long cycleId, String preparerName, String preparerEmail) {
        Long o = org();
        NdiAssessmentCycle cycle = cycles.findByIdAndOrganisationId(cycleId, o)
            .orElseThrow(() -> new EntityNotFoundException("Cycle not found: " + cycleId));

        int nextVersion = packages.findFirstByOrganisationIdAndCycleIdOrderByVersionDesc(o, cycleId)
            .map(p -> p.getVersion() + 1).orElse(1);

        NdiSubmissionPackage p = new NdiSubmissionPackage();
        p.setOrganisationId(o);
        p.setCycleId(cycleId);
        p.setVersion(nextVersion);
        p.setPackageCode("PKG-" + cycle.getReportingYear() + "-v" + nextVersion);
        p.setStatus("DRAFT");
        p.setPreparerName(preparerName);
        p.setPreparerEmail(preparerEmail);
        p.setPreparedAt(LocalDateTime.now());

        regenerateSnapshotInPlace(p, cycle);
        NdiSubmissionPackage saved = packages.save(p);
        audit.publish("SUBMISSION_PACKAGE", saved.getId(), saved.getPackageCode(),
            saved.getCycleId(), "CREATE", "Created submission package v" + saved.getVersion());
        return saved;
    }

    /** Rebuild snapshot from current NDI state. Only legal while DRAFT or READY. */
    public NdiSubmissionPackage regenerate(Long id) {
        NdiSubmissionPackage p = get(id);
        ensureMutable(p);
        NdiAssessmentCycle cycle = cycles.findByIdAndOrganisationId(p.getCycleId(), org())
            .orElseThrow(() -> new EntityNotFoundException("Cycle not found: " + p.getCycleId()));
        regenerateSnapshotInPlace(p, cycle);
        NdiSubmissionPackage saved = packages.save(p);
        audit.publish("SUBMISSION_PACKAGE", saved.getId(), saved.getPackageCode(),
            saved.getCycleId(), "REGENERATE", "Snapshot regenerated");
        return saved;
    }

    /** DRAFT → READY. Refuses when pre-flight blockers exist (Rule 15). */
    public NdiSubmissionPackage finalize(Long id) {
        NdiSubmissionPackage p = get(id);
        if (!"DRAFT".equals(p.getStatus()))
            throw new IllegalStateException("Only DRAFT packages can be finalized (was " + p.getStatus() + ")");
        Preflight pf = preflight(p.getCycleId());
        if (!pf.canFinalize)
            throw new IllegalStateException("Pre-flight failed: " + String.join("; ", pf.blockers));
        // F2-466 - get(), create() and regenerate() all name this failure; this was the fourth
        // of four lookups in the class and the only bare one: it read `.orElseThrow()` with no
        // argument, so a package whose cycle has since been soft-deleted answered an opaque 500
        // - NoSuchElementException maps to no handler - where its two siblings answer a clean
        // 404 naming the cycle.
        //
        // preflight() above cannot refuse it: its only BLOCKER is criticalNoCapa, everything else
        // is a warning, and every child query returns empty for a soft-deleted cycle - so
        // canFinalize passes VACUOUSLY and this is the line that fails.
        NdiAssessmentCycle cycle = cycles.findByIdAndOrganisationId(p.getCycleId(), org())
            .orElseThrow(() -> new EntityNotFoundException("Cycle not found: " + p.getCycleId()));
        regenerateSnapshotInPlace(p, cycle);
        p.setStatus("READY");
        p.setReviewedAt(LocalDateTime.now());
        NdiSubmissionPackage saved = packages.save(p);
        audit.publishTransition("SUBMISSION_PACKAGE", saved.getId(), saved.getPackageCode(),
            saved.getCycleId(), "DRAFT", "READY", "Finalized submission package");
        return saved;
    }

    public NdiSubmissionPackage approve(Long id) {
        NdiSubmissionPackage p = get(id);
        if (!"READY".equals(p.getStatus()))
            throw new IllegalStateException("Only READY packages can be approved (was " + p.getStatus() + ")");
        // SEC-NDI-003: the approver is the authenticated principal, not a body
        // field; segregation-of-duties requires the approver to differ from the
        // preparer who built the package.
        String approverName = currentUsername();
        if (approverName != null && approverName.equalsIgnoreCase(p.getPreparerName())) {
            throw new IllegalStateException(
                "Segregation of duties: the preparer cannot also approve this submission package.");
        }
        p.setApproverName(approverName);
        p.setApprovedAt(LocalDateTime.now());
        // Approval stays in READY — submission is a separate explicit step.
        NdiSubmissionPackage saved = packages.save(p);
        audit.publish("SUBMISSION_PACKAGE", saved.getId(), saved.getPackageCode(),
            saved.getCycleId(), "APPROVE", "Approved by " + approverName);
        return saved;
    }

    public NdiSubmissionPackage submit(Long id, String attestationEn, String attestationAr) {
        NdiSubmissionPackage p = get(id);
        if (!"READY".equals(p.getStatus()))
            throw new IllegalStateException("Only READY packages can be submitted (was " + p.getStatus() + ")");
        if (p.getApprovedAt() == null)
            throw new IllegalStateException("Package must be approved before submission");
        if (attestationEn == null || attestationEn.isBlank())
            throw new IllegalArgumentException("English attestation text is required for submission");
        // SEC-NDI-003: the submitter is the authenticated principal, not a body field.
        String submitterName = currentUsername();
        p.setStatus("SUBMITTED");
        p.setSubmitterName(submitterName);
        p.setAttestationEn(attestationEn);
        p.setAttestationAr(attestationAr);
        p.setSubmittedAt(LocalDateTime.now());

        // Mark prior versions of this cycle's package as ARCHIVED.
        for (NdiSubmissionPackage older : packages.findByOrganisationIdAndCycleIdOrderByVersionDesc(org(), p.getCycleId())) {
            if (!Objects.equals(older.getId(), p.getId()) && !"ARCHIVED".equals(older.getStatus())) {
                older.setStatus("ARCHIVED");
                packages.save(older);
                audit.publish("SUBMISSION_PACKAGE", older.getId(), older.getPackageCode(),
                    older.getCycleId(), "TRANSITION", "Auto-archived by newer submission v" + p.getVersion());
            }
        }
        NdiSubmissionPackage saved = packages.save(p);
        audit.publishTransition("SUBMISSION_PACKAGE", saved.getId(), saved.getPackageCode(),
            saved.getCycleId(), "READY", "SUBMITTED",
            "Submitted to SDAIA by " + submitterName + " with attestation");
        return saved;
    }

    public NdiSubmissionPackage acknowledge(Long id, String sdaiaReference) {
        NdiSubmissionPackage p = get(id);
        if (!"SUBMITTED".equals(p.getStatus()))
            throw new IllegalStateException("Only SUBMITTED packages can be acknowledged (was " + p.getStatus() + ")");
        p.setStatus("ACKNOWLEDGED");
        p.setSdaiaReference(sdaiaReference);
        p.setAcknowledgedAt(LocalDateTime.now());
        NdiSubmissionPackage saved = packages.save(p);
        audit.publishTransition("SUBMISSION_PACKAGE", saved.getId(), saved.getPackageCode(),
            saved.getCycleId(), "SUBMITTED", "ACKNOWLEDGED",
            "SDAIA acknowledged: " + sdaiaReference);
        return saved;
    }

    public NdiSubmissionPackage reject(Long id, String reason) {
        NdiSubmissionPackage p = get(id);
        if (!"SUBMITTED".equals(p.getStatus()))
            throw new IllegalStateException("Only SUBMITTED packages can be rejected (was " + p.getStatus() + ")");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Rejection reason is required");
        p.setStatus("REJECTED");
        p.setRejectionReason(reason);
        NdiSubmissionPackage saved = packages.save(p);
        audit.publish("SUBMISSION_PACKAGE", saved.getId(), saved.getPackageCode(),
            saved.getCycleId(), "REJECT", "WARN", "SDAIA rejected: " + reason);
        return saved;
    }

    /** REJECTED → DRAFT — clear submitter/approver stamps so the package can be re-worked. */
    public NdiSubmissionPackage reopen(Long id) {
        NdiSubmissionPackage p = get(id);
        if (!"REJECTED".equals(p.getStatus()))
            throw new IllegalStateException("Only REJECTED packages can be reopened (was " + p.getStatus() + ")");
        p.setStatus("DRAFT");
        p.setSubmittedAt(null);
        p.setApprovedAt(null);
        p.setReviewedAt(null);
        NdiSubmissionPackage saved = packages.save(p);
        audit.publishTransition("SUBMISSION_PACKAGE", saved.getId(), saved.getPackageCode(),
            saved.getCycleId(), "REJECTED", "DRAFT", "Reopened for re-work");
        return saved;
    }

    public void delete(Long id) {
        NdiSubmissionPackage p = get(id);
        if ("SUBMITTED".equals(p.getStatus()) || "ACKNOWLEDGED".equals(p.getStatus()))
            throw new IllegalStateException("Submitted/acknowledged packages cannot be deleted");
        p.setIsDeleted(true);
        packages.save(p);
    }

    // ─── Pre-flight ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Preflight preflight(Long cycleId) {
        Long o = org();
        List<NdiGap> allGaps = gaps.findByOrganisationIdAndCycleId(o, cycleId);
        int openCritical = 0;
        int criticalNoCapa = 0;
        for (NdiGap g : allGaps) {
            if ("CRITICAL".equals(g.getSeverity())) {
                if (!"RESOLVED".equals(g.getStatus())
                    && !"CLOSED".equals(g.getStatus())
                    && !"ACCEPTED_RISK".equals(g.getStatus())) openCritical++;
                if (g.getCapaCount() == null || g.getCapaCount() == 0) criticalNoCapa++;
            }
        }

        int complianceNotApproved = 0;
        for (NdiComplianceAssessment c : compliance.findByOrganisationIdAndCycleId(o, cycleId)) {
            if ("NOT_APPLICABLE".equals(c.getComplianceStatus())) continue;
            if (!"APPROVED".equals(c.getLifecycleStatus())) complianceNotApproved++;
        }

        int maturityNotApproved = 0;
        for (NdiMaturityAssessment m : maturity.findByOrganisationIdAndCycleId(o, cycleId)) {
            if (m.getCurrentLevel() == null) continue;
            if (!"APPROVED".equals(m.getStatus())) maturityNotApproved++;
        }

        int expired = 0;
        for (NdiEvidenceItem e : evidence.findByOrganisationIdAndCycleId(o, cycleId)) {
            if ("EXPIRED".equals(e.getStatus())) expired++;
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (criticalNoCapa > 0) blockers.add(criticalNoCapa + " critical gap(s) without any corrective action");
        if (openCritical > 0)   warnings.add(openCritical + " critical gap(s) still open");
        if (complianceNotApproved > 0) warnings.add(complianceNotApproved + " compliance row(s) not yet APPROVED");
        if (maturityNotApproved > 0)   warnings.add(maturityNotApproved + " maturity row(s) with a level set but not APPROVED");
        if (expired > 0)        warnings.add(expired + " expired evidence item(s)");

        boolean ok = blockers.isEmpty();
        return new Preflight(cycleId, openCritical, criticalNoCapa,
            complianceNotApproved, maturityNotApproved, expired,
            ok, blockers, warnings);
    }

    // ─── Snapshot assembly ─────────────────────────────────────────

    private void regenerateSnapshotInPlace(NdiSubmissionPackage p, NdiAssessmentCycle cycle) {
        Long o = org();
        Long cycleId = cycle.getId();

        CycleRollup domainRollup = domainService.rollupForCycle(cycleId);

        List<NdiMaturityAssessment> mat = maturity.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiComplianceAssessment> cmp = compliance.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiOperationalExcellenceKpi> ops = opex.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiEvidenceItem> evs = evidence.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiGap> gs = gaps.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiCorrectiveAction> cs = capas.findByOrganisationId(o).stream()
            .filter(c -> {
                // Only CAPAs whose parent gap is in this cycle.
                return gs.stream().anyMatch(g -> Objects.equals(g.getId(), c.getGapId()));
            })
            .toList();
        List<NdiImprovementPlan> pls = plans.findByOrganisationIdAndCycleIdOrderByPeriodStartDesc(o, cycleId);

        // Snapshot — a structured Map serialised to JSON.
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("schemaVersion", "1.0-placeholder");
        snap.put("generatedAt", LocalDateTime.now().toString());
        snap.put("cycle", Map.of(
            "id", cycle.getId(),
            "code", cycle.getCycleCode() == null ? "" : cycle.getCycleCode(),
            "nameEn", cycle.getCycleNameEn() == null ? "" : cycle.getCycleNameEn(),
            "nameAr", cycle.getCycleNameAr() == null ? "" : cycle.getCycleNameAr(),
            "reportingYear", cycle.getReportingYear(),
            "status", cycle.getStatus()
        ));

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("overallNdiPct", domainRollup.overallNdiPct());

        // Maturity / compliance / opex aggregates over all rows in this cycle.
        BigDecimal matAvg = averageLevel(mat);
        BigDecimal matPct = matAvg == null ? null : matAvg.multiply(BigDecimal.valueOf(20)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cmpPct = averageCompliance(cmp);
        BigDecimal opPct  = averageOpex(ops);
        scores.put("maturityScorePct", matPct);
        scores.put("compliancePct",    cmpPct);
        scores.put("opexScorePct",     opPct);
        snap.put("scores", scores);

        snap.put("domains", domainRollup.domains());
        snap.put("counts", Map.of(
            "maturity",   mat.size(),
            "compliance", cmp.size(),
            "opex",       ops.size(),
            "evidence",   evs.size(),
            "gaps",       gs.size(),
            "capas",      cs.size(),
            "plans",      pls.size()
        ));

        // Manifest — one entry per artefact with a stable key + signature material.
        List<Map<String, Object>> manifest = new ArrayList<>();
        for (NdiMaturityAssessment m : mat)         manifest.add(manifestRow("maturity",   m.getId(),  "FI-" + m.getFrameworkItemId(), m.getStatus()));
        for (NdiComplianceAssessment c : cmp)       manifest.add(manifestRow("compliance", c.getId(),  c.getRequirementCode(),     c.getLifecycleStatus()));
        for (NdiOperationalExcellenceKpi k : ops)   manifest.add(manifestRow("opex",       k.getId(),  k.getKpiCode(),             k.getStatus()));
        for (NdiEvidenceItem e : evs)               manifest.add(manifestRow("evidence",   e.getId(),  e.getEvidenceCode(),        e.getStatus()));
        for (NdiGap g : gs)                          manifest.add(manifestRow("gap",        g.getId(),  g.getGapCode(),             g.getStatus()));
        for (NdiCorrectiveAction c : cs)             manifest.add(manifestRow("capa",       c.getId(),  c.getActionCode(),          c.getStatus()));
        for (NdiImprovementPlan pl : pls)            manifest.add(manifestRow("plan",       pl.getId(), pl.getPlanCode(),           pl.getStatus()));

        try {
            String snapStr = json.writeValueAsString(snap);
            String manStr  = json.writeValueAsString(manifest);
            p.setSnapshotJson(snapStr);
            p.setManifestJson(manStr);
            p.setManifestChecksum(sha256(snapStr + "|" + manStr));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialise snapshot", ex);
        }

        p.setOverallNdiPct(domainRollup.overallNdiPct());
        p.setMaturityScorePct(matPct);
        p.setCompliancePct(cmpPct);
        p.setOpexScorePct(opPct);

        p.setTotalEvidence(evs.size());
        p.setTotalGaps(gs.size());
        p.setCriticalGaps((int) gs.stream().filter(g -> "CRITICAL".equals(g.getSeverity())).count());
        p.setTotalActions(cs.size());
        p.setCompletedActions((int) cs.stream().filter(c -> "COMPLETED".equals(c.getStatus())).count());

        // Inherits placeholder status from the domain catalog — if any domain
        // is still flagged, so is the package.
        boolean anyPlaceholder = domainRollup.domains().stream().anyMatch(d -> d.isPlaceholder());
        p.setIsPlaceholder(anyPlaceholder);
    }

    private Map<String, Object> manifestRow(String type, Long id, String code, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("id", id);
        row.put("code", code == null ? "" : code);
        row.put("status", status == null ? "" : status);
        return row;
    }

    private BigDecimal averageLevel(List<NdiMaturityAssessment> rows) {
        int n = 0; int sum = 0;
        for (NdiMaturityAssessment m : rows) {
            if (m.getCurrentLevel() != null) { sum += m.getCurrentLevel(); n++; }
        }
        if (n == 0) return null;
        return BigDecimal.valueOf((double) sum / n).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageCompliance(List<NdiComplianceAssessment> rows) {
        int n = 0; BigDecimal sum = BigDecimal.ZERO;
        for (NdiComplianceAssessment c : rows) {
            if (c.getComplianceScore() != null) { sum = sum.add(c.getComplianceScore()); n++; }
        }
        if (n == 0) return null;
        return sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageOpex(List<NdiOperationalExcellenceKpi> rows) {
        int n = 0; BigDecimal sum = BigDecimal.ZERO;
        for (NdiOperationalExcellenceKpi k : rows) {
            if (k.getActual() == null || k.getTarget() == null) continue;
            BigDecimal baseline = k.getBaseline() == null ? BigDecimal.ZERO : k.getBaseline();
            BigDecimal denom = k.getTarget().subtract(baseline);
            if (denom.signum() == 0) {
                if (k.getActual().compareTo(k.getTarget()) == 0) { sum = sum.add(BigDecimal.valueOf(100)); n++; }
                continue;
            }
            BigDecimal raw = k.getActual().subtract(baseline)
                .divide(denom, 4, RoundingMode.HALF_UP);
            if (raw.compareTo(BigDecimal.ZERO) < 0) raw = BigDecimal.ZERO;
            if (raw.compareTo(BigDecimal.ONE) > 0)  raw = BigDecimal.ONE;
            sum = sum.add(raw.multiply(BigDecimal.valueOf(100)));
            n++;
        }
        if (n == 0) return null;
        return sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }

    private void ensureMutable(NdiSubmissionPackage p) {
        if (!"DRAFT".equals(p.getStatus()) && !"READY".equals(p.getStatus()))
            throw new IllegalStateException("Snapshot is immutable in status " + p.getStatus()
                + " (Rule 16). Reopen via /reject → /reopen if SDAIA returned the package.");
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new RuntimeException("SHA-256 unavailable", ex);
        }
    }
}
