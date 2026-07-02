package com.tss.grc.module.sust.ghg.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
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
 * GHG emission inventory service.
 *
 * <p>Enforces the inventory-side workflow rules from the spec:
 * <ul>
 *   <li><b>Rule 1</b> — surfaced via {@link #aggregate(Short)}: if Scope 3
 *       weighted-coverage is below 70%, returns {@code scope3PartialFlag=true}
 *       so the dashboard can render the "Partial Scope-3 disclosure" gap.</li>
 *   <li><b>Rule 2</b> — {@link #verify(Long)} refuses to mark a record
 *       verified unless {@code evidenceRef} is set.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GhgEmissionRecordService {

    private static final Set<String> STATUSES = Set.of("DRAFT","SUBMITTED","VERIFIED","APPROVED","ARCHIVED");

    private final GhgEmissionRecordRepository repo;
    private final OrganisationSettingsService settingsService;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<GhgEmissionRecord> list(Pageable p) {
        return repo.findByOrganisationIdOrderByReportingYearDescScopeAscIdAsc(org(), p);
    }
    public Page<GhgEmissionRecord> listByYear(Short year, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearOrderByScopeAscIdAsc(org(), year, p);
    }
    public Page<GhgEmissionRecord> listByScope(Short scope, Pageable p) {
        return repo.findByOrganisationIdAndScopeOrderByReportingYearDescIdAsc(org(), scope, p);
    }
    public GhgEmissionRecord get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("GHG record " + id));
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public GhgEmissionRecord create(GhgEmissionRecord req) {
        req.setId(null);
        req.setOrganisationId(org());
        // Mass-assignment guard (SEC-ESG-003): force the initial lifecycle
        // state and scrub workflow / verification / attribution / audit
        // columns so a record cannot be born VERIFIED or APPROVED, nor carry
        // a forged verification or audit attribution.
        req.setStatus("DRAFT");
        req.setVerified(false);
        req.setVerificationDate(null);
        req.setSubmittedAt(null);
        req.setApprovedAt(null);
        req.setCreatedBy(null);
        req.setUpdatedBy(null);
        validate(req);
        // Auto-recompute emission value from activity × factor when both
        // are set; otherwise trust the caller-supplied emissionValueTco2e.
        if (req.getActivityData() != null && req.getEmissionFactor() != null) {
            req.setEmissionValueTco2e(deriveTco2e(req.getActivityData(), req.getEmissionFactor(), req.getEmissionFactorUnit()));
        }
        return repo.save(req);
    }

    @Transactional
    public GhgEmissionRecord update(Long id, GhgEmissionRecord req) {
        GhgEmissionRecord cur = get(id);
        if (req.getCategory() != null)           cur.setCategory(req.getCategory());
        if (req.getSourceNameEn() != null)       cur.setSourceNameEn(req.getSourceNameEn());
        if (req.getSourceNameAr() != null)       cur.setSourceNameAr(req.getSourceNameAr());
        if (req.getBusinessUnit() != null)       cur.setBusinessUnit(req.getBusinessUnit());
        if (req.getFacility() != null)           cur.setFacility(req.getFacility());
        if (req.getActivityData() != null)       cur.setActivityData(req.getActivityData());
        if (req.getActivityUnit() != null)       cur.setActivityUnit(req.getActivityUnit());
        if (req.getEmissionFactor() != null)     cur.setEmissionFactor(req.getEmissionFactor());
        if (req.getEmissionFactorUnit() != null) cur.setEmissionFactorUnit(req.getEmissionFactorUnit());
        if (req.getEmissionValueTco2e() != null) cur.setEmissionValueTco2e(req.getEmissionValueTco2e());
        if (req.getCoveragePercent() != null)    cur.setCoveragePercent(req.getCoveragePercent());
        if (req.getMethodology() != null)        cur.setMethodology(req.getMethodology());
        if (req.getNotes() != null)              cur.setNotes(req.getNotes());
        if (req.getEvidenceRef() != null)        cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getOwnerName() != null)          cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)         cur.setOwnerEmail(req.getOwnerEmail());
        // Auto-recompute if activity × factor both present.
        if (cur.getActivityData() != null && cur.getEmissionFactor() != null) {
            cur.setEmissionValueTco2e(deriveTco2e(cur.getActivityData(), cur.getEmissionFactor(), cur.getEmissionFactorUnit()));
        }
        validate(cur);
        return repo.save(cur);
    }

    @Transactional
    public GhgEmissionRecord transition(Long id, String to) {
        GhgEmissionRecord cur = get(id);
        if (!STATUSES.contains(to)) throw new IllegalArgumentException("Unknown status: " + to);
        String from = cur.getStatus();
        boolean ok =
            ("DRAFT".equals(from)     && "SUBMITTED".equals(to)) ||
            ("SUBMITTED".equals(from) && "VERIFIED".equals(to))  ||
            ("VERIFIED".equals(from)  && "APPROVED".equals(to))  ||
            "ARCHIVED".equals(to);
        if (!ok) throw new IllegalTransitionException(from, to, Set.of("SUBMITTED","VERIFIED","APPROVED","ARCHIVED"));
        // Rule 2 — verifying requires evidence_ref.
        if ("VERIFIED".equals(to) && (cur.getEvidenceRef() == null || cur.getEvidenceRef().isBlank())) {
            throw new IllegalArgumentException(
                "GHG verification requires evidence_ref (Rule 2). Attach evidence before verifying.");
        }
        cur.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        switch (to) {
            case "SUBMITTED" -> cur.setSubmittedAt(now);
            case "VERIFIED"  -> { cur.setVerified(true); if (cur.getVerificationDate() == null) cur.setVerificationDate(java.time.LocalDate.now()); }
            case "APPROVED"  -> cur.setApprovedAt(now);
            default -> { /* ARCHIVED */ }
        }
        return repo.save(cur);
    }

    @Transactional
    public void softDelete(Long id) {
        GhgEmissionRecord cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
    }

    // ── Aggregations / Rule 1 ────────────────────────────────────────

    /**
     * Per-year scope aggregate. Returned shape:
     * {@code { year, scope1Total, scope2Total, scope3Total, grandTotal,
     *           scope3Coverage, scope3PartialFlag } }
     *
     * <p>Rule 1 — {@code scope3PartialFlag} is {@code true} when the
     * spend-weighted Scope-3 coverage is below 70%.
     */
    public Map<String, Object> aggregate(Short year) {
        List<GhgEmissionRecord> all = repo.findByOrganisationIdAndReportingYear(org(), year);
        BigDecimal s1 = BigDecimal.ZERO, s2 = BigDecimal.ZERO, s3 = BigDecimal.ZERO;
        BigDecimal s3Weighted = BigDecimal.ZERO, s3Total = BigDecimal.ZERO;
        for (GhgEmissionRecord r : all) {
            BigDecimal v = nz(r.getEmissionValueTco2e());
            switch (r.getScope()) {
                case 1 -> s1 = s1.add(v);
                case 2 -> s2 = s2.add(v);
                case 3 -> {
                    s3 = s3.add(v);
                    s3Total = s3Total.add(v);
                    s3Weighted = s3Weighted.add(v.multiply(nz(r.getCoveragePercent())));
                }
                default -> { /* unreachable per check constraint */ }
            }
        }
        BigDecimal coverage = s3Total.signum() == 0
            ? BigDecimal.ZERO
            : s3Weighted.divide(s3Total, 2, RoundingMode.HALF_UP);
        // LinkedHashMap (not Map.of) to preserve insertion order in the
        // JSON response — easier to read in dashboards + tests.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("scope1Total", s1);
        out.put("scope2Total", s2);
        out.put("scope3Total", s3);
        out.put("grandTotal",  s1.add(s2).add(s3));
        out.put("scope3Coverage", coverage);
        // Rule 1 boundary is tenant-configurable via OrganisationSettings
        // (S-B). Default 70.00 lives in the settings schema, not here.
        BigDecimal s3Threshold = settingsService.getOrCreate().getScope3PartialCoverageThreshold();
        out.put("scope3PartialFlag", coverage.compareTo(s3Threshold) < 0);
        out.put("scope3PartialThreshold", s3Threshold);
        out.put("recordCount", all.size());
        return out;
    }

    /**
     * Year-over-year delta per scope between {@code year} and {@code year-1}.
     * Returned values are percentages, e.g. {@code -8.0} = 8% reduction.
     */
    public Map<String, Object> yoy(Short year) {
        Map<String, Object> cur = aggregate(year);
        Map<String, Object> prev = aggregate((short)(year - 1));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("scope1YoY", pct(cur.get("scope1Total"), prev.get("scope1Total")));
        out.put("scope2YoY", pct(cur.get("scope2Total"), prev.get("scope2Total")));
        out.put("scope3YoY", pct(cur.get("scope3Total"), prev.get("scope3Total")));
        out.put("totalYoY",  pct(cur.get("grandTotal"),  prev.get("grandTotal")));
        return out;
    }

    /** Convenience endpoint for the Verify button on the inventory page. */
    @Transactional
    public GhgEmissionRecord verify(Long id) { return transition(id, "VERIFIED"); }

    // ── Helpers ──────────────────────────────────────────────────────
    private void validate(GhgEmissionRecord r) {
        if (r.getScope() == null || r.getScope() < 1 || r.getScope() > 3)
            throw new IllegalArgumentException("scope must be 1, 2 or 3");
        if (r.getCoveragePercent() != null
            && (r.getCoveragePercent().signum() < 0
                || r.getCoveragePercent().compareTo(new BigDecimal("100")) > 0))
            throw new IllegalArgumentException("coverage_percent must be 0..100");
        if (r.getEmissionValueTco2e() == null || r.getEmissionValueTco2e().signum() < 0)
            throw new IllegalArgumentException("emission_value_tco2e must be >= 0");
        if (r.getSourceNameEn() == null || r.getSourceNameEn().isBlank())
            throw new IllegalArgumentException("sourceNameEn required");
        if (r.getReportingYear() == null) throw new IllegalArgumentException("reportingYear required");
        if (r.getCategory() == null || r.getCategory().isBlank())
            throw new IllegalArgumentException("category required");
    }

    /**
     * Convert (activityData × emissionFactor) into tCO2e using a heuristic
     * on the factor unit. Supports the seed-data units; falls back to
     * "assume the factor is already tCO2e/unit" for anything we don't
     * recognise (callers always have the option to set emissionValueTco2e
     * directly).
     */
    static BigDecimal deriveTco2e(BigDecimal act, BigDecimal factor, String factorUnit) {
        BigDecimal raw = act.multiply(factor);
        if (factorUnit == null) return raw.setScale(2, RoundingMode.HALF_UP);
        String u = factorUnit.toLowerCase();
        // kg → t  divide by 1000
        if (u.startsWith("kg")) return raw.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
        // already t/...
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal pct(Object curO, Object prevO) {
        if (!(curO instanceof BigDecimal cur) || !(prevO instanceof BigDecimal prev)) return BigDecimal.ZERO;
        if (prev.signum() == 0) return BigDecimal.ZERO;
        return cur.subtract(prev)
                  .multiply(new BigDecimal("100"))
                  .divide(prev, 1, RoundingMode.HALF_UP);
    }
}
