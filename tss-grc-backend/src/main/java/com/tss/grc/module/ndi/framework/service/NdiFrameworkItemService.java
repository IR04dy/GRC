package com.tss.grc.module.ndi.framework.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.framework.entity.NdiFrameworkItem;
import com.tss.grc.module.ndi.framework.repository.NdiFrameworkItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * NDI Framework Item service (slice 2).
 *
 * <p>Provides the catalog operations the spec lists:
 * <ul>
 *   <li>CRUD on individual items</li>
 *   <li>Bulk JSON import — the only required import surface for now;
 *       CSV import lands as a follow-up</li>
 *   <li>Mark official / mark placeholder flag-flips with provenance
 *       consistency rules ({@code is_official_import + is_placeholder}
 *       are mutually exclusive when both go through the service)</li>
 *   <li>aggregate() — counts per component / per domain / placeholder
 *       status, used by the dashboard widget on slice 12</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NdiFrameworkItemService {

    private static final Set<String> COMPONENTS = Set.of(
        "COMPLIANCE", "MATURITY", "OPERATIONAL_EXCELLENCE");
    private static final Set<String> PRIORITIES = Set.of(
        "CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "RETIRED");

    private final NdiFrameworkItemRepository repo;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<NdiFrameworkItem> list(Pageable p) {
        return repo.findByOrganisationIdOrderByComponentAscDomainAscRequirementCodeAsc(org(), p);
    }
    public Page<NdiFrameworkItem> listByComponent(String component, Pageable p) {
        return repo.findByOrganisationIdAndComponentOrderByDomainAscRequirementCodeAsc(org(), component, p);
    }
    public Page<NdiFrameworkItem> listByDomain(String domain, Pageable p) {
        return repo.findByOrganisationIdAndDomainOrderByComponentAscRequirementCodeAsc(org(), domain, p);
    }
    public NdiFrameworkItem get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Framework item " + id));
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public NdiFrameworkItem create(NdiFrameworkItem req) {
        req.setId(null);
        req.setOrganisationId(org());
        if (req.getStatus() == null || req.getStatus().isBlank()) req.setStatus("ACTIVE");
        if (req.getPriority() == null || req.getPriority().isBlank()) req.setPriority("MEDIUM");
        // F2-240 — these two defaults were the ENTITY's initialisers, and status/priority above
        // were not. Two of four covered here, the other two left to the field declaration: the
        // shape F2-215 measured, where the mechanism a reader finds present makes the remainder
        // look covered. Both columns are NOT NULL, and while `mandatory` is only display state,
        // `weight` is a scoring input, so an omitted default is a 500 on create either way.
        if (req.getMandatory() == null) req.setMandatory(false);
        if (req.getWeight() == null) req.setWeight(BigDecimal.ONE);
        validate(req);
        return repo.save(req);
    }

    @Transactional
    public NdiFrameworkItem update(Long id, NdiFrameworkItem req) {
        NdiFrameworkItem cur = get(id);
        if (req.getComponent() != null)          cur.setComponent(req.getComponent());
        if (req.getDomain() != null)             cur.setDomain(req.getDomain());
        if (req.getSubdomain() != null)          cur.setSubdomain(req.getSubdomain());
        if (req.getRequirementCode() != null)    cur.setRequirementCode(req.getRequirementCode());
        if (req.getTitleEn() != null)            cur.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null)            cur.setTitleAr(req.getTitleAr());
        if (req.getDescriptionEn() != null)      cur.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null)      cur.setDescriptionAr(req.getDescriptionAr());
        if (req.getQuestionEn() != null)         cur.setQuestionEn(req.getQuestionEn());
        if (req.getQuestionAr() != null)         cur.setQuestionAr(req.getQuestionAr());
        if (req.getEvidenceGuidanceEn() != null) cur.setEvidenceGuidanceEn(req.getEvidenceGuidanceEn());
        if (req.getEvidenceGuidanceAr() != null) cur.setEvidenceGuidanceAr(req.getEvidenceGuidanceAr());
        if (req.getScoringMethod() != null)      cur.setScoringMethod(req.getScoringMethod());
        if (req.getWeight() != null)             cur.setWeight(req.getWeight());
        if (req.getPriority() != null)           cur.setPriority(req.getPriority());
        if (req.getMandatory() != null)          cur.setMandatory(req.getMandatory());
        if (req.getOwnerRole() != null)          cur.setOwnerRole(req.getOwnerRole());
        if (req.getReviewFrequency() != null)    cur.setReviewFrequency(req.getReviewFrequency());
        if (req.getRelatedNdmoControl() != null)    cur.setRelatedNdmoControl(req.getRelatedNdmoControl());
        if (req.getRelatedPdplRequirement() != null) cur.setRelatedPdplRequirement(req.getRelatedPdplRequirement());
        if (req.getRelatedCyberControl() != null) cur.setRelatedCyberControl(req.getRelatedCyberControl());
        if (req.getRelatedIsoMapping() != null)  cur.setRelatedIsoMapping(req.getRelatedIsoMapping());
        if (req.getStatus() != null)             cur.setStatus(req.getStatus());
        if (req.getEffectiveDate() != null)      cur.setEffectiveDate(req.getEffectiveDate());
        if (req.getNotes() != null)              cur.setNotes(req.getNotes());
        validate(cur);
        return repo.save(cur);
    }

    @Transactional
    public NdiFrameworkItem markOfficial(Long id) {
        NdiFrameworkItem cur = get(id);
        cur.setIsOfficialImport(true);
        cur.setIsPlaceholder(false);
        return repo.save(cur);
    }

    @Transactional
    public NdiFrameworkItem markPlaceholder(Long id) {
        NdiFrameworkItem cur = get(id);
        cur.setIsOfficialImport(false);
        cur.setIsPlaceholder(true);
        return repo.save(cur);
    }

    @Transactional
    public void softDelete(Long id) {
        NdiFrameworkItem cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
    }

    /**
     * Bulk JSON import. Each map in the input list maps to one
     * framework_item row. Upsert by (frameworkVersion, requirementCode):
     * existing rows are updated, new rows are inserted. Marks every
     * imported row as is_official_import=true / is_placeholder=false
     * (callers who want to import as drafts should flip the flags
     * post-import via markPlaceholder).
     *
     * <p>Returns a per-row outcome summary so the UI can show
     * "X inserted, Y updated, Z failed with reason".
     */
    @Transactional
    public Map<String, Object> importJson(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty())
            throw new IllegalArgumentException("Import payload is empty.");
        int inserted = 0, updated = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            try {
                String version = strOr(r, "frameworkVersion", "v0.1-imported");
                String code    = strOr(r, "requirementCode", null);
                if (code == null) throw new IllegalArgumentException("requirementCode required");
                var existing = repo.findByOrganisationIdAndFrameworkVersionAndRequirementCode(
                    org(), version, code);
                NdiFrameworkItem item = existing.orElseGet(NdiFrameworkItem::new);
                item.setOrganisationId(org());
                item.setFrameworkVersion(version);
                item.setRequirementCode(code);
                item.setComponent(strOr(r, "component", "MATURITY"));
                item.setDomain(strOr(r, "domain", "Data Governance"));
                item.setSubdomain(strOr(r, "subdomain", null));
                item.setTitleEn(strOr(r, "titleEn", code));
                item.setTitleAr(strOr(r, "titleAr", null));
                item.setDescriptionEn(strOr(r, "descriptionEn", null));
                item.setDescriptionAr(strOr(r, "descriptionAr", null));
                item.setQuestionEn(strOr(r, "questionEn", null));
                item.setQuestionAr(strOr(r, "questionAr", null));
                item.setEvidenceGuidanceEn(strOr(r, "evidenceGuidanceEn", null));
                item.setEvidenceGuidanceAr(strOr(r, "evidenceGuidanceAr", null));
                item.setScoringMethod(strOr(r, "scoringMethod", null));
                item.setPriority(strOr(r, "priority", "MEDIUM"));
                item.setMandatory(Boolean.TRUE.equals(r.get("mandatory")));
                item.setOwnerRole(strOr(r, "ownerRole", null));
                item.setReviewFrequency(strOr(r, "reviewFrequency", null));
                item.setRelatedNdmoControl(strOr(r, "relatedNdmoControl", null));
                item.setRelatedPdplRequirement(strOr(r, "relatedPdplRequirement", null));
                item.setRelatedCyberControl(strOr(r, "relatedCyberControl", null));
                item.setRelatedIsoMapping(strOr(r, "relatedIsoMapping", null));
                item.setIsOfficialImport(true);
                item.setIsPlaceholder(false);
                item.setStatus(strOr(r, "status", "ACTIVE"));
                Object w = r.get("weight");
                if (w instanceof Number n) item.setWeight(new java.math.BigDecimal(n.toString()));
                validate(item);
                repo.save(item);
                if (existing.isPresent()) updated++; else inserted++;
            } catch (Exception ex) {
                Map<String, String> err = new LinkedHashMap<>();
                err.put("row", String.valueOf(i));
                err.put("requirementCode", String.valueOf(r.get("requirementCode")));
                err.put("error", ex.getMessage());
                errors.add(err);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inserted", inserted);
        out.put("updated",  updated);
        out.put("failed",   errors.size());
        out.put("errors",   errors);
        return out;
    }

    // ── Aggregation ────────────────────────────────────────────────

    /** Counts per component, per domain, plus placeholder/official totals. */
    public Map<String, Object> aggregate() {
        List<NdiFrameworkItem> all = repo.findByOrganisationId(org());
        Map<String, Integer> byComponent = new LinkedHashMap<>();
        for (String c : COMPONENTS) byComponent.put(c, 0);
        Map<String, Integer> byDomain    = new LinkedHashMap<>();
        Map<String, Integer> byPriority  = new LinkedHashMap<>();
        for (String p : PRIORITIES) byPriority.put(p, 0);

        int placeholder = 0, official = 0, mandatory = 0;
        for (NdiFrameworkItem it : all) {
            byComponent.merge(it.getComponent(), 1, Integer::sum);
            byDomain.merge(it.getDomain(), 1, Integer::sum);
            byPriority.merge(it.getPriority(), 1, Integer::sum);
            if (Boolean.TRUE.equals(it.getIsPlaceholder()))      placeholder++;
            if (Boolean.TRUE.equals(it.getIsOfficialImport()))   official++;
            if (Boolean.TRUE.equals(it.getMandatory()))          mandatory++;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total",            all.size());
        out.put("placeholderCount", placeholder);
        out.put("officialCount",    official);
        out.put("mandatoryCount",   mandatory);
        out.put("byComponent",      byComponent);
        out.put("byDomain",         byDomain);
        out.put("byPriority",       byPriority);
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private void validate(NdiFrameworkItem r) {
        if (r.getRequirementCode() == null || r.getRequirementCode().isBlank())
            throw new IllegalArgumentException("requirementCode required");
        if (r.getTitleEn() == null || r.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn required");
        if (r.getComponent() == null || !COMPONENTS.contains(r.getComponent()))
            throw new IllegalArgumentException("component must be one of " + COMPONENTS);
        if (r.getDomain() == null || r.getDomain().isBlank())
            throw new IllegalArgumentException("domain required");
        if (!PRIORITIES.contains(r.getPriority()))
            throw new IllegalArgumentException("priority must be one of " + PRIORITIES);
        if (!STATUSES.contains(r.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
    }

    private static String strOr(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        if (v == null) return def;
        String s = v.toString().trim();
        return s.isEmpty() ? def : s;
    }
}
