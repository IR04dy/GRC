package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
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
        if (body.getComplianceStatus() == null) body.setComplianceStatus("NOT_ASSESSED");
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
    @Transactional
    public BcmComplianceMapping assessCompliance(Long id, String status, String assessedBy, String notes) {
        if (status == null || !STATUSES.contains(status))
            throw new IllegalArgumentException("complianceStatus must be one of " + STATUSES);
        BcmComplianceMapping m = get(id);
        m.setComplianceStatus(status);
        m.setAssessedBy(assessedBy);
        if (notes != null) m.setNotes(notes);
        m.setAssessedAt(LocalDateTime.now());
        m.setUpdatedAt(m.getAssessedAt());
        BcmComplianceMapping saved = repo.save(m);
        audit.log("CUSTOM", "BcmComplianceMapping", saved.getId(),
            "Compliance assessed: " + saved.getRequirementCode() + " → " + status);
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
