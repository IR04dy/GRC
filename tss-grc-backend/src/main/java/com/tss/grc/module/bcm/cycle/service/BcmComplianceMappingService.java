package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.bcm.cycle.entity.BcmComplianceMapping;
import com.tss.grc.module.bcm.cycle.repository.BcmComplianceMappingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * BCM compliance mapping service — audit Tier-1 #5.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Create a mapping row (or PLACEHOLDER seed already provides one).</li>
 *   <li>Attach evidence — link to a record in the evidence repository.</li>
 *   <li>Assess compliance — set status to COMPLIANT / PARTIAL / NON_COMPLIANT /
 *       NOT_APPLICABLE with a reviewer name and notes.</li>
 *   <li>If PARTIAL or NON_COMPLIANT, the caller can attach a Gap ID
 *       (created via {@code BcmRemediationService.createGap}); the mapping
 *       row carries the soft FK for the coverage report.</li>
 * </ol>
 *
 * <h3>Coverage stats</h3>
 * <p>{@link #coverageByFramework} returns one row per framework with counts
 * across the 5 compliance statuses, ready for the dashboard widget.
 */
@Service
@RequiredArgsConstructor
public class BcmComplianceMappingService {

    public static final Set<String> FRAMEWORKS = Set.of(
        "ISO_22301", "SAMA_OPRES", "DGA", "NCEMA", "INTERNAL", "CUSTOM");
    public static final Set<String> STATUSES = Set.of(
        "NOT_ASSESSED", "COMPLIANT", "PARTIAL", "NON_COMPLIANT", "NOT_APPLICABLE");
    /** F2-234 — the state a mapping is CREATED in; every other STATUS is assessCompliance()'s. */
    private static final String INITIAL_COMPLIANCE_STATUS = "NOT_ASSESSED";
    public static final Set<String> WORKFLOWS = Set.of(
        "GOVERNANCE", "BIA", "BCP", "DRP", "EXERCISE", "CRISIS",
        "CALL_TREE", "STRATEGY", "TRAINING", "EVIDENCE", "OTHER");

    private final BcmComplianceMappingRepository repo;
    private final AuditLogService audit;

    // ─── CRUD ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BcmComplianceMapping> list(String framework) {
        Long org = TenantContext.getCurrentTenant();
        return framework == null
            ? repo.findByOrganisationIdOrderBySourceFrameworkAscRequirementCodeAsc(org)
            : repo.findByOrganisationIdAndSourceFrameworkOrderByRequirementCodeAsc(org, framework);
    }

    @Transactional(readOnly = true)
    public BcmComplianceMapping get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("Compliance mapping not found: " + id));
    }

    @Transactional
    public BcmComplianceMapping create(BcmComplianceMapping body) {
        validate(body, /*creating*/ true);
        body.setId(null);
        body.setOrganisationId(TenantContext.getCurrentTenant());
        // F2-234 — a DEFAULT, not a guard, and validate() only tested MEMBERSHIP of STATUSES, so a
        // mapping could be created COMPLIANT: an ISO 22301 / SAMA OPRES / NCEMA requirement
        // recorded as met without assessCompliance() ever running — no assessor, no clock, and no
        // "Compliance assessed" audit row. A mapping is BORN unassessed; assessing it is an act.
        // ⚠ Safe to refuse rather than coerce (F2-182): the CHECK already rejects anything outside
        // STATUSES, and POST /api/v1/bcm/compliance has NO frontend client at all — bcmCompliance.ts
        // declares list/get/assess/attach-evidence/attach-gap and no create — so no request that
        // succeeds today starts failing. All 412 rows in dev and prod are NOT_ASSESSED.
        body.setComplianceStatus(ServerOwnedState.requireInitial(
            body.getComplianceStatus(), INITIAL_COMPLIANCE_STATUS, "a BCM compliance mapping",
            "POST /api/v1/bcm/compliance/{id}/assess"));
        // assessCompliance()'s stamps. A mapping created carrying an assessment date and an
        // assessor's name is a record of an assessment that was never made.
        body.setAssessedAt(null);
        body.setAssessedBy(null);
        if (body.getIsPlaceholder() == null)    body.setIsPlaceholder(true);

        // Uniqueness check (framework + requirement_code per tenant)
        repo.findByOrganisationIdAndSourceFrameworkAndRequirementCode(
                body.getOrganisationId(), body.getSourceFramework(), body.getRequirementCode())
            .ifPresent(existing -> {
                throw new IllegalArgumentException(
                    "A mapping already exists for " + body.getSourceFramework() + " " + body.getRequirementCode());
            });

        BcmComplianceMapping saved = repo.save(body);
        audit.log("CREATE", "BcmComplianceMapping", saved.getId(),
            "Compliance mapping created: " + saved.getSourceFramework() + " " + saved.getRequirementCode());
        return saved;
    }

    @Transactional
    public BcmComplianceMapping update(Long id, BcmComplianceMapping patch) {
        BcmComplianceMapping existing = get(id);
        if (patch.getRequirementTitleEn()    != null) existing.setRequirementTitleEn(patch.getRequirementTitleEn());
        if (patch.getRequirementTitleAr()    != null) existing.setRequirementTitleAr(patch.getRequirementTitleAr());
        if (patch.getRequirementDescription() != null) existing.setRequirementDescription(patch.getRequirementDescription());
        if (patch.getDomain()                != null) existing.setDomain(patch.getDomain());
        if (patch.getIsPlaceholder()         != null) existing.setIsPlaceholder(patch.getIsPlaceholder());
        if (patch.getRelatedWorkflow()       != null) {
            if (!WORKFLOWS.contains(patch.getRelatedWorkflow()))
                throw new IllegalArgumentException("relatedWorkflow must be one of " + WORKFLOWS);
            existing.setRelatedWorkflow(patch.getRelatedWorkflow());
        }
        if (patch.getRelatedRecordType() != null) existing.setRelatedRecordType(patch.getRelatedRecordType());
        if (patch.getRelatedRecordId()   != null) existing.setRelatedRecordId(patch.getRelatedRecordId());
        if (patch.getRelatedControlId()  != null) existing.setRelatedControlId(patch.getRelatedControlId());
        if (patch.getOwnerId()           != null) existing.setOwnerId(patch.getOwnerId());
        if (patch.getNotes()             != null) existing.setNotes(patch.getNotes());
        existing.setUpdatedAt(LocalDateTime.now());
        BcmComplianceMapping saved = repo.save(existing);
        audit.log("UPDATE", "BcmComplianceMapping", saved.getId(),
            "Compliance mapping updated: " + saved.getRequirementCode());
        return saved;
    }

    /**
     * Attach an evidence record to a mapping. Used by the "Attach evidence"
     * button on the compliance page; takes the evidenceId only so the caller
     * doesn't have to round-trip the whole row.
     */
    @Transactional
    public BcmComplianceMapping attachEvidence(Long id, Long evidenceId) {
        if (evidenceId == null) throw new IllegalArgumentException("evidenceId is required");
        BcmComplianceMapping m = get(id);
        m.setEvidenceId(evidenceId);
        m.setUpdatedAt(LocalDateTime.now());
        BcmComplianceMapping saved = repo.save(m);
        audit.log("CUSTOM", "BcmComplianceMapping", saved.getId(),
            "Evidence #" + evidenceId + " attached to " + saved.getRequirementCode());
        return saved;
    }

    /**
     * Set the compliance status with a reviewer name + notes. PARTIAL /
     * NON_COMPLIANT status may carry an attached gap (set separately via
     * {@link #attachGap}).
     */
    /**
     * F2-234 — <b>the split attribution.</b> This method stamps {@code assessedAt} from the
     * server's own clock and used to copy {@code assessedBy} out of the request body, so one
     * attribution had two owners: the server said WHEN, the caller said WHO. That is F2-223's
     * defect on the ISO 22301 / SAMA OPRES / NCEMA compliance register, and the UI's half was
     * {@code window.prompt('Reviewer name')} with {@code ?? ''} on Cancel — so the assessor of a
     * regulator-facing BCM requirement could be anybody, or the empty string.
     *
     * <p>🔑 <b>It survived F2-223's sweep because the actor reached the setter as a BARE SCALAR
     * PARAMETER.</b> {@code SplitAttributionContractTest}'s {@code REQUEST_VALUE} needs a named
     * receiver ({@code req.}/{@code body.}/…), and {@code m.setAssessedBy(assessedBy)} names none —
     * the wire-ness lives at the CALL SITE, where {@code BcmComplianceMappingController} passes
     * {@code body.assessedBy()}. F2-222 established exactly that ("whether a scalar parameter is
     * caller data is a fact about its call sites") and taught it to the STAMPING side only. One
     * question, asked of a parameter, answered on one side of the wire.
     */
    @Transactional
    public BcmComplianceMapping assessCompliance(Long id, String status, String notes) {
        if (status == null || !STATUSES.contains(status))
            throw new IllegalArgumentException("complianceStatus must be one of " + STATUSES);
        String assessor = CurrentActor.username();
        if (assessor == null || assessor.isBlank())
            throw new IllegalStateException(
                "A compliance assessment with no identified assessor is not an assessment.");
        BcmComplianceMapping m = get(id);
        m.setComplianceStatus(status);
        m.setAssessedBy(assessor);
        if (notes != null) m.setNotes(notes);
        m.setAssessedAt(LocalDateTime.now());
        m.setUpdatedAt(m.getAssessedAt());
        BcmComplianceMapping saved = repo.save(m);
        // F2-223's rule: the trail reads off the SAVED ROW, never the caller's claim — a trail that
        // quotes the claim is what makes a forged attribution invisible to whoever audits it.
        audit.log("CUSTOM", "BcmComplianceMapping", saved.getId(),
            "Compliance assessed: " + saved.getRequirementCode() + " → "
            + saved.getComplianceStatus() + " by " + saved.getAssessedBy());
        return saved;
    }

    @Transactional
    public BcmComplianceMapping attachGap(Long id, Long gapId) {
        if (gapId == null) throw new IllegalArgumentException("gapId is required");
        BcmComplianceMapping m = get(id);
        m.setGapId(gapId);
        m.setUpdatedAt(LocalDateTime.now());
        BcmComplianceMapping saved = repo.save(m);
        audit.log("CUSTOM", "BcmComplianceMapping", saved.getId(),
            "Gap #" + gapId + " attached to " + saved.getRequirementCode());
        return saved;
    }

    // ─── Coverage stats ───────────────────────────────────────────

    /**
     * One row per framework with counts across the 5 compliance statuses.
     * Returns a map {@code framework → {status → count}}.
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Long>> coverageByFramework() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (String fw : FRAMEWORKS) {
            Map<String, Long> row = new LinkedHashMap<>();
            for (String s : STATUSES) {
                row.put(s.toLowerCase(),
                    repo.countByOrganisationIdAndSourceFrameworkAndComplianceStatus(org, fw, s));
            }
            // Skip empty frameworks to keep the dashboard tidy
            long total = row.values().stream().mapToLong(Long::longValue).sum();
            if (total > 0) out.put(fw, row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> overallStats() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Long> out = new LinkedHashMap<>();
        for (String s : STATUSES) {
            out.put(s.toLowerCase(), repo.countByOrganisationIdAndComplianceStatus(org, s));
        }
        return out;
    }

    // ─── Validation ──────────────────────────────────────────────

    private void validate(BcmComplianceMapping m, boolean creating) {
        if (creating) {
            if (m.getSourceFramework() == null || !FRAMEWORKS.contains(m.getSourceFramework()))
                throw new IllegalArgumentException("sourceFramework must be one of " + FRAMEWORKS);
            if (m.getRequirementCode() == null || m.getRequirementCode().isBlank())
                throw new IllegalArgumentException("requirementCode is required");
            if (m.getRequirementTitleEn() == null || m.getRequirementTitleEn().isBlank())
                throw new IllegalArgumentException("requirementTitleEn is required");
        }
        if (m.getRelatedWorkflow() != null && !WORKFLOWS.contains(m.getRelatedWorkflow()))
            throw new IllegalArgumentException("relatedWorkflow must be one of " + WORKFLOWS);
        if (m.getComplianceStatus() != null && !STATUSES.contains(m.getComplianceStatus()))
            throw new IllegalArgumentException("complianceStatus must be one of " + STATUSES);
    }
}
