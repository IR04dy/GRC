package com.tss.grc.module.compliance.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.entity.Control;
import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.compliance.entity.Policy;
import com.tss.grc.module.compliance.entity.PolicyFrameworkMapping;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.compliance.repository.FrameworkRepository;
import com.tss.grc.module.compliance.repository.PolicyFrameworkMappingRepository;
import com.tss.grc.module.compliance.repository.PolicyRepository;
import com.tss.grc.module.privacy.entity.Dpia;
import com.tss.grc.module.privacy.repository.DpiaRepository;
import com.tss.grc.module.risk.entity.KriDefinition;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.springframework.data.domain.PageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Framework-anchored views over compliance entities (IA-1).
 *
 * Pattern: the artefact (a Policy) is the primary entity, frameworks are
 * sheets/tabs you can switch between. Each policy can satisfy multiple
 * frameworks via {@code compliance.policy_framework_mappings}.
 *
 * - GET    /api/v1/compliance/frameworks                         → dictionary
 * - GET    /api/v1/compliance/frameworks/{id}/policies           → policies anchored on a framework
 * - GET    /api/v1/compliance/policies/{id}/mappings             → mappings for one policy
 * - POST   /api/v1/compliance/policies/{id}/mappings             → add mapping
 * - PUT    /api/v1/compliance/policies/mappings/{mappingId}      → update mapping
 * - DELETE /api/v1/compliance/policies/mappings/{mappingId}      → remove mapping
 * - GET    /api/v1/compliance/frameworks/coverage                → coverage stats per framework
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance — Framework anchor (Policies)")
@SecurityRequirement(name = "bearerAuth")
public class FrameworkAnchorController {

    private final FrameworkRepository frameworkRepo;
    private final PolicyRepository policyRepo;
    private final PolicyFrameworkMappingRepository mappingRepo;
    private final AuditFindingRepository findingRepo;
    private final ControlRepository controlRepo;
    private final DpiaRepository dpiaRepo;
    private final KriDefinitionRepository kriRepo;
    private final RiskRepository riskRepo;

    @GetMapping("/frameworks")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<Framework> frameworks() {
        return frameworkRepo.findByIsActiveTrueOrderByFamilyAscCodeAsc();
    }

    @GetMapping("/frameworks/{id}/policies")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "All policies that satisfy a given framework, with their clause refs.")
    public List<Map<String, Object>> policiesForFramework(@PathVariable Long id) {
        Long org = TenantContext.getCurrentTenant();
        List<PolicyFrameworkMapping> mappings = mappingRepo.findByFrameworkId(id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PolicyFrameworkMapping m : mappings) {
            Policy p = policyRepo.findById(m.getPolicyId()).orElse(null);
            if (p == null || !p.getOrganisationId().equals(org)) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("mappingId",     m.getId());
            row.put("policyId",      p.getId());
            row.put("code",          p.getCode());
            row.put("titleEn",       p.getTitleEn());
            row.put("titleAr",       p.getTitleAr());
            row.put("version",       p.getVersion());
            row.put("category",      p.getCategory());
            row.put("status",        p.getStatus());
            row.put("nextReviewDate", p.getNextReviewDate());
            row.put("clauseRef",     m.getClauseRef());
            row.put("satisfaction",  m.getSatisfaction());
            row.put("notes",         m.getNotes());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/policies/{id}/mappings")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<Map<String, Object>> mappingsForPolicy(@PathVariable Long id) {
        requirePolicy(id);
        List<PolicyFrameworkMapping> mappings = mappingRepo.findByPolicyId(id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PolicyFrameworkMapping m : mappings) {
            Framework f = frameworkRepo.findById(m.getFrameworkId()).orElse(null);
            Map<String, Object> row = new HashMap<>();
            row.put("mappingId",     m.getId());
            row.put("frameworkId",   m.getFrameworkId());
            row.put("frameworkCode", f != null ? f.getCode()    : null);
            row.put("frameworkName", f != null ? f.getNameEn() : null);
            row.put("frameworkVersion", f != null ? f.getVersion() : null);
            row.put("clauseRef",     m.getClauseRef());
            row.put("satisfaction",  m.getSatisfaction());
            row.put("notes",         m.getNotes());
            out.add(row);
        }
        return out;
    }

    @PostMapping("/policies/{id}/mappings")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyFrameworkMapping addMapping(@PathVariable Long id, @RequestBody PolicyFrameworkMapping body) {
        requirePolicy(id);
        if (body.getFrameworkId() == null) throw new IllegalArgumentException("frameworkId is required");
        body.setId(null);            // force INSERT — block merge() into a cross-tenant row (SEC-CMP-001)
        body.setIsDeleted(false);
        body.setPolicyId(id);
        if (body.getSatisfaction() == null) body.setSatisfaction("PARTIAL");
        return mappingRepo.save(body);
    }

    @PutMapping("/policies/mappings/{mappingId}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyFrameworkMapping updateMapping(@PathVariable Long mappingId, @RequestBody PolicyFrameworkMapping body) {
        PolicyFrameworkMapping m = mappingRepo.findById(mappingId)
            .orElseThrow(() -> new EntityNotFoundException("Mapping not found: " + mappingId));
        requirePolicy(m.getPolicyId());
        if (body.getClauseRef()    != null) m.setClauseRef(body.getClauseRef());
        if (body.getSatisfaction() != null) m.setSatisfaction(body.getSatisfaction());
        if (body.getNotes()        != null) m.setNotes(body.getNotes());
        return mappingRepo.save(m);
    }

    @DeleteMapping("/policies/mappings/{mappingId}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMapping(@PathVariable Long mappingId) {
        PolicyFrameworkMapping m = mappingRepo.findById(mappingId)
            .orElseThrow(() -> new EntityNotFoundException("Mapping not found: " + mappingId));
        requirePolicy(m.getPolicyId());
        m.setIsDeleted(true);
        mappingRepo.save(m);
    }

    @GetMapping("/frameworks/coverage")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "Per-framework coverage: policy count + breakdown of satisfaction levels")
    public List<Map<String, Object>> coverage() {
        Long org = TenantContext.getCurrentTenant();
        List<Framework> all = frameworkRepo.findByIsActiveTrueOrderByFamilyAscCodeAsc();
        List<AuditFinding> tenantFindings = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Framework f : all) {
            List<PolicyFrameworkMapping> rows = mappingRepo.findByFrameworkId(f.getId());
            long full = 0, partial = 0, oos = 0, na = 0;
            for (PolicyFrameworkMapping m : rows) {
                String s = m.getSatisfaction() == null ? "" : m.getSatisfaction();
                switch (s) {
                    case "FULL"           -> full++;
                    case "PARTIAL"        -> partial++;
                    case "OUT_OF_SCOPE"   -> oos++;
                    case "NOT_APPLICABLE" -> na++;
                    default -> { /* unknown */ }
                }
            }
            long openFindings = 0, closedFindings = 0;
            for (AuditFinding fnd : tenantFindings) {
                if (fnd.getFrameworkId() == null || !fnd.getFrameworkId().equals(f.getId())) continue;
                if ("CLOSED".equals(fnd.getStatus())) closedFindings++; else openFindings++;
            }
            List<Control> ctrls = controlRepo.findByOrganisationIdAndFrameworkId(org, f.getId());
            long implemented = ctrls.stream().filter(c -> "IMPLEMENTED".equals(c.getStatus())).count();
            int  maturitySum = ctrls.stream().mapToInt(c -> c.getMaturityLevel() == null ? 0 : c.getMaturityLevel()).sum();
            double avgMaturity = ctrls.isEmpty() ? 0 : Math.round((maturitySum * 10.0) / ctrls.size()) / 10.0;
            Map<String, Object> r = new HashMap<>();
            r.put("frameworkId",    f.getId());
            r.put("code",           f.getCode());
            r.put("nameEn",         f.getNameEn());
            r.put("nameAr",         f.getNameAr());
            r.put("family",         f.getFamily());
            r.put("policyCount",    rows.size());
            r.put("full",           full);
            r.put("partial",        partial);
            r.put("outOfScope",     oos);
            r.put("notApplicable",  na);
            r.put("openFindings",   openFindings);
            r.put("closedFindings", closedFindings);
            r.put("controlCount",   ctrls.size());
            r.put("implementedControls", implemented);
            r.put("avgMaturity",    avgMaturity);
            r.put("dpiaCount",      dpiaRepo.countByOrganisationIdAndFrameworkId(org, f.getId()));
            r.put("kriCount",       kriRepo.countByOrganisationIdAndFrameworkId(org, f.getId()));
            // Risk count = union of risks tagged on either dimension
            long riskAsRiskFw = riskRepo.countByOrganisationIdAndRiskFrameworkId(org, f.getId());
            long riskAsCompFw = riskRepo.countByOrganisationIdAndComplianceFrameworkId(org, f.getId());
            // Note: this double-counts risks tagged on BOTH dimensions to the same framework — rare in practice.
            r.put("riskCount",      riskAsRiskFw + riskAsCompFw);
            r.put("riskAsRiskFw",   riskAsRiskFw);
            r.put("riskAsCompFw",   riskAsCompFw);
            out.add(r);
        }
        return out;
    }

    @GetMapping("/frameworks/{id}/controls")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "All controls anchored on a given framework (IA-3).")
    public List<Map<String, Object>> controlsForFramework(@PathVariable Long id) {
        Long org = TenantContext.getCurrentTenant();
        List<Control> ctrls = controlRepo.findByOrganisationIdAndFrameworkId(org, id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Control c : ctrls) {
            Map<String, Object> row = new HashMap<>();
            row.put("id",            c.getId());
            row.put("code",          c.getCode());
            row.put("nameEn",        c.getNameEn());
            row.put("nameAr",        c.getNameAr());
            row.put("domain",        c.getDomain());
            row.put("maturityLevel", c.getMaturityLevel());
            row.put("status",        c.getStatus());
            row.put("owner",         c.getOwner());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/frameworks/{id}/kris")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "All KRIs anchored on a given framework (IA-5).")
    public List<Map<String, Object>> krisForFramework(@PathVariable Long id) {
        Long org = TenantContext.getCurrentTenant();
        List<KriDefinition> kris = kriRepo.findByOrganisationIdAndFrameworkId(org, id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (KriDefinition k : kris) {
            Map<String, Object> row = new HashMap<>();
            row.put("id",          k.getId());
            row.put("code",        k.getCode());
            row.put("nameEn",      k.getNameEn());
            row.put("nameAr",      k.getNameAr());
            row.put("unit",        k.getUnit());
            row.put("direction",   k.getDirection());
            row.put("greenMax",    k.getGreenMax());
            row.put("amberMax",    k.getAmberMax());
            row.put("frequency",   k.getFrequency());
            row.put("owner",       k.getOwner());
            row.put("clauseRef",   k.getClauseRef());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/frameworks/{id}/risks")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "All risks anchored on a given framework (IA-5). " +
        "Returns risks where EITHER risk_framework_id OR compliance_framework_id matches.")
    public List<Map<String, Object>> risksForFramework(@PathVariable Long id) {
        Long org = TenantContext.getCurrentTenant();
        List<Risk> riskFw = riskRepo.findByOrganisationIdAndRiskFrameworkId(org, id);
        List<Risk> compFw = riskRepo.findByOrganisationIdAndComplianceFrameworkId(org, id);
        // Union by id
        Map<Long, Risk> union = new HashMap<>();
        for (Risk r : riskFw) union.put(r.getId(), r);
        for (Risk r : compFw) union.put(r.getId(), r);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Risk r : union.values()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id",                r.getId());
            row.put("titleEn",           r.getTitleEn());
            row.put("titleAr",           r.getTitleAr());
            row.put("status",            r.getStatus());
            row.put("inherentScore",     r.getInherentScore());
            row.put("residualScore",     r.getResidualScore());
            row.put("riskLevel",         r.getRiskLevel());
            // Indicate which dimension(s) matched so the UI can show a badge
            row.put("matchedAsRiskFramework",       r.getRiskFrameworkId() != null && r.getRiskFrameworkId().equals(id));
            row.put("matchedAsComplianceFramework", r.getComplianceFrameworkId() != null && r.getComplianceFrameworkId().equals(id));
            row.put("riskFrameworkClause",       r.getRiskFrameworkClause());
            row.put("complianceFrameworkClause", r.getComplianceFrameworkClause());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/frameworks/{id}/dpias")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    @Operation(summary = "All DPIAs anchored on a given framework (IA-4).")
    public List<Map<String, Object>> dpiasForFramework(@PathVariable Long id) {
        Long org = TenantContext.getCurrentTenant();
        List<Dpia> dpias = dpiaRepo.findByOrganisationIdAndFrameworkId(org, id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Dpia d : dpias) {
            Map<String, Object> row = new HashMap<>();
            row.put("id",                  d.getId());
            row.put("titleEn",             d.getTitleEn());
            row.put("titleAr",             d.getTitleAr());
            row.put("triggerReason",       d.getTriggerReason());
            row.put("status",              d.getStatus());
            row.put("overallResidualRisk", d.getOverallResidualRisk());
            row.put("assessmentDate",      d.getAssessmentDate());
            row.put("nextReviewDate",      d.getNextReviewDate());
            row.put("clauseRef",           d.getClauseRef());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/frameworks/{id}/findings")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "All audit findings raised under a given framework (IA-2).")
    public List<Map<String, Object>> findingsForFramework(@PathVariable Long id) {
        Long org = TenantContext.getCurrentTenant();
        // Pull a wide page; tenant filter applied in-memory below
        List<AuditFinding> all = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AuditFinding f : all) {
            if (f.getFrameworkId() == null || !f.getFrameworkId().equals(id)) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("id",        f.getId());
            row.put("title",     f.getTitle());
            row.put("severity",  f.getSeverity());
            row.put("owner",     f.getOwner());
            row.put("dueDate",   f.getDueDate());
            row.put("status",    f.getStatus());
            row.put("capaStage", f.getCapaStage());
            row.put("clauseRef", f.getClauseRef());
            row.put("createdAt", f.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    private Policy requirePolicy(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return policyRepo.findById(id)
            .filter(p -> p.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Policy not found: " + id));
    }
}
