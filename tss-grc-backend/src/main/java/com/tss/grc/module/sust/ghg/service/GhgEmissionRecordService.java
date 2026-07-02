package com.tss.grc.module.sust.ghg.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.ghg.entity.EmissionFactor;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.EmissionFactorRepository;
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
    private static final String COMPUTED = "COMPUTED";
    private static final String ASSERTED = "ASSERTED";
    private static final int MAX_TRAJECTORY_YEARS = 20;

    private final GhgEmissionRecordRepository repo;
    private final OrganisationSettingsService settingsService;
    private final AuditLogService audit;
    /** S-J — versioned emission-factor library, for records that reference a library factor. */
    private final EmissionFactorRepository factorRepo;

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
        // S-J: resolve any library factor, run the unit registry, and stamp
        // valueSource (COMPUTED vs ASSERTED) — before validate() so the derived
        // emission value is what gets range-checked.
        applyDerivation(req);
        validate(req);
        GhgEmissionRecord saved = repo.save(req);
        audit.log("CREATE", "GhgEmissionRecord", saved.getId(),
            "Created GHG record: " + saved.getSourceNameEn() + " (" + saved.getValueSource() + ")");
        return saved;
    }

    @Transactional
    public GhgEmissionRecord update(Long id, GhgEmissionRecord req) {
        GhgEmissionRecord cur = get(id);
        // S-J edit-lock: an APPROVED record is immutable — its numbers are
        // attested. To change it you must reopen (which forces re-verification).
        if ("APPROVED".equals(cur.getStatus())) {
            throw new IllegalStateException(
                "APPROVED GHG record is locked — reopen it (POST /{id}/reopen) to edit; re-verification is then required.");
        }
        boolean wasVerified = "VERIFIED".equals(cur.getStatus());

        if (req.getCategory() != null)           cur.setCategory(req.getCategory());
        if (req.getSourceNameEn() != null)       cur.setSourceNameEn(req.getSourceNameEn());
        if (req.getSourceNameAr() != null)       cur.setSourceNameAr(req.getSourceNameAr());
        if (req.getBusinessUnit() != null)       cur.setBusinessUnit(req.getBusinessUnit());
        if (req.getFacility() != null)           cur.setFacility(req.getFacility());
        if (req.getActivityData() != null)       cur.setActivityData(req.getActivityData());
        if (req.getActivityUnit() != null)       cur.setActivityUnit(req.getActivityUnit());
        if (req.getEmissionFactor() != null)     cur.setEmissionFactor(req.getEmissionFactor());
        if (req.getEmissionFactorUnit() != null) cur.setEmissionFactorUnit(req.getEmissionFactorUnit());
        if (req.getEmissionFactorId() != null)   cur.setEmissionFactorId(req.getEmissionFactorId());
        if (req.getEmissionValueTco2e() != null) cur.setEmissionValueTco2e(req.getEmissionValueTco2e());
        if (req.getCoveragePercent() != null)    cur.setCoveragePercent(req.getCoveragePercent());
        if (req.getMethodology() != null)        cur.setMethodology(req.getMethodology());
        if (req.getNotes() != null)              cur.setNotes(req.getNotes());
        if (req.getEvidenceRef() != null)        cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getEvidenceItemId() != null)     cur.setEvidenceItemId(req.getEvidenceItemId());
        if (req.getOwnerName() != null)          cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)         cur.setOwnerEmail(req.getOwnerEmail());
        applyDerivation(cur);
        validate(cur);
        // A VERIFIED record whose numbers were just edited can no longer claim
        // verification — drop it back to SUBMITTED and clear the verification.
        if (wasVerified) {
            cur.setStatus("SUBMITTED");
            cur.setVerified(false);
            cur.setVerificationDate(null);
        }
        GhgEmissionRecord saved = repo.save(cur);
        audit.log("UPDATE", "GhgEmissionRecord", saved.getId(),
            "Updated GHG record" + (wasVerified ? " — verification reset, re-verification required" : ""));
        return saved;
    }

    /**
     * S-J — reopen an APPROVED record for editing. Forces re-verification:
     * back to DRAFT, verification + approval cleared. Evidence is retained.
     */
    @Transactional
    public GhgEmissionRecord reopen(Long id) {
        GhgEmissionRecord cur = get(id);
        if (!"APPROVED".equals(cur.getStatus())) {
            throw new IllegalStateException(
                "Only an APPROVED GHG record can be reopened (current status: " + cur.getStatus() + ").");
        }
        cur.setStatus("DRAFT");
        cur.setVerified(false);
        cur.setVerificationDate(null);
        cur.setApprovedAt(null);
        cur.setSubmittedAt(null);
        GhgEmissionRecord saved = repo.save(cur);
        audit.log("TRANSITION", "GhgEmissionRecord", saved.getId(),
            "GHG record reopened APPROVED → DRAFT (re-verification required)");
        return saved;
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
        // Rule 2 — verifying requires evidence: a real evidence-item FK (S-C)
        // OR the legacy free-text evidence_ref.
        if ("VERIFIED".equals(to)
                && cur.getEvidenceItemId() == null
                && (cur.getEvidenceRef() == null || cur.getEvidenceRef().isBlank())) {
            throw new IllegalArgumentException(
                "GHG verification requires evidence (Rule 2): attach an evidence item (evidenceItemId) or set evidence_ref.");
        }
        cur.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        switch (to) {
            case "SUBMITTED" -> cur.setSubmittedAt(now);
            case "VERIFIED"  -> { cur.setVerified(true); if (cur.getVerificationDate() == null) cur.setVerificationDate(java.time.LocalDate.now()); }
            case "APPROVED"  -> cur.setApprovedAt(now);
            default -> { /* ARCHIVED */ }
        }
        GhgEmissionRecord saved = repo.save(cur);
        audit.log("TRANSITION", "GhgEmissionRecord", saved.getId(), "GHG record -> " + to);
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        GhgEmissionRecord cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "GhgEmissionRecord", id, "Soft-deleted GHG record");
    }

    // ── Aggregations / Rule 1 ────────────────────────────────────────

    /**
     * Per-year scope aggregate. Returned shape:
     * {@code { year, scope1Total, scope2Total, scope3Total, grandTotal,
     *           scope3Coverage, scope3PartialFlag } }
     *
     * <p>Rule 1 — {@code scope3PartialFlag} is {@code true} when the
     * emission-weighted Scope-3 coverage is below the configured threshold.
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
        // S-J: coverage is the EMISSION-weighted mean of per-row coverage%
        // (each Scope-3 row weighted by its tCO2e) — not spend-weighted; no
        // spend input exists. Label it so dashboards/auditors read it correctly.
        out.put("scope3CoverageBasis", "emission-weighted");
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
        out.put("priorYear", (short)(year - 1));
        // S-J: distinguish a genuine 0% change from "no prior-year baseline" —
        // the deltas below are only meaningful when the prior year has records.
        int prevCount = prev.get("recordCount") instanceof Integer c ? c : 0;
        out.put("priorYearHasData", prevCount > 0);
        out.put("scope1YoY", pct(cur.get("scope1Total"), prev.get("scope1Total")));
        out.put("scope2YoY", pct(cur.get("scope2Total"), prev.get("scope2Total")));
        out.put("scope3YoY", pct(cur.get("scope3Total"), prev.get("scope3Total")));
        out.put("totalYoY",  pct(cur.get("grandTotal"),  prev.get("grandTotal")));
        return out;
    }

    /**
     * S-J — multi-year emissions trajectory: per-year scope totals + grand total
     * across an inclusive [fromYear, toYear] range (capped at {@value #MAX_TRAJECTORY_YEARS}
     * years). Feeds the inventory's real YoY trend chart.
     */
    public Map<String, Object> trajectory(Short fromYear, Short toYear) {
        if (fromYear == null || toYear == null) throw new IllegalArgumentException("fromYear and toYear are required");
        if (toYear < fromYear) throw new IllegalArgumentException("toYear must be >= fromYear");
        if (toYear - fromYear + 1 > MAX_TRAJECTORY_YEARS)
            throw new IllegalArgumentException("Range too wide — max " + MAX_TRAJECTORY_YEARS + " years");
        List<Map<String, Object>> series = new ArrayList<>();
        for (short y = fromYear; y <= toYear; y++) {
            Map<String, Object> a = aggregate(y);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("year", y);
            point.put("scope1Total", a.get("scope1Total"));
            point.put("scope2Total", a.get("scope2Total"));
            point.put("scope3Total", a.get("scope3Total"));
            point.put("grandTotal",  a.get("grandTotal"));
            point.put("recordCount", a.get("recordCount"));
            series.add(point);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromYear", fromYear);
        out.put("toYear", toYear);
        out.put("series", series);
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
     * S-J — resolve the emission value and its provenance. When the record
     * references a library factor, pull its value+unit (and activity unit if
     * blank); then when activity data is present, compute tCO₂e via the unit
     * registry and stamp the value {@code COMPUTED}; otherwise the caller's
     * total stands as {@code ASSERTED}.
     */
    private void applyDerivation(GhgEmissionRecord r) {
        if (r.getEmissionFactorId() != null) {
            EmissionFactor ef = factorRepo.findByIdAndOrganisationId(r.getEmissionFactorId(), org())
                .orElseThrow(() -> new IllegalArgumentException(
                    "emissionFactorId " + r.getEmissionFactorId() + " not found in this tenant's factor library."));
            r.setEmissionFactor(ef.getFactorValue());
            r.setEmissionFactorUnit(ef.getFactorUnit());
            if (r.getActivityUnit() == null || r.getActivityUnit().isBlank()) r.setActivityUnit(ef.getActivityUnit());
        }
        if (r.getActivityData() != null && r.getEmissionFactor() != null) {
            r.setEmissionValueTco2e(GhgUnitRegistry.toTco2e(r.getActivityData(), r.getEmissionFactor(), r.getEmissionFactorUnit()));
            r.setValueSource(COMPUTED);
        } else {
            r.setValueSource(ASSERTED);
        }
    }

    /** S-J — unit conversion delegated to {@link GhgUnitRegistry} (g/kg/t/kt/Mt aware). */
    static BigDecimal deriveTco2e(BigDecimal act, BigDecimal factor, String factorUnit) {
        return GhgUnitRegistry.toTco2e(act, factor, factorUnit);
    }

    // ── Bulk CSV import (T-06) ────────────────────────────────────────

    /**
     * S-J — bulk CSV import. The header row names the columns; recognised keys:
     * {@code reportingYear, scope, category, sourceNameEn, sourceNameAr,
     * businessUnit, facility, activityData, activityUnit, emissionFactor,
     * emissionFactorUnit, emissionFactorId, emissionValueTco2e, coveragePercent,
     * methodology, evidenceRef, ownerName, ownerEmail}. Each data row is created
     * as a DRAFT through {@link #create} (so all validation + derivation applies).
     * Returns {@code { created, failed, errors:[{line,error}] }} — one bad row
     * does not abort the others.
     */
    @Transactional
    public Map<String, Object> importCsv(String csv) {
        if (csv == null || csv.isBlank()) throw new IllegalArgumentException("CSV body is empty");
        List<String> lines = csv.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        if (lines.size() < 2) throw new IllegalArgumentException("CSV must have a header row and at least one data row");
        List<String> headers = splitCsvLine(lines.get(0)).stream().map(String::trim).toList();

        int created = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = splitCsvLine(lines.get(i));
            Map<String, String> row = new HashMap<>();
            for (int c = 0; c < headers.size() && c < cells.size(); c++) row.put(headers.get(c), cells.get(c).trim());
            try {
                create(rowToRecord(row));
                created++;
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("line", i + 1);
                err.put("error", e.getMessage());
                errors.add(err);
            }
        }
        audit.log("IMPORT", "GhgEmissionRecord", null,
            "GHG CSV import: " + created + " created, " + errors.size() + " failed");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", created);
        out.put("failed", errors.size());
        out.put("errors", errors);
        return out;
    }

    private GhgEmissionRecord rowToRecord(Map<String, String> row) {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setReportingYear(parseShort(row.get("reportingYear"), "reportingYear"));
        r.setScope(parseShort(row.get("scope"), "scope"));
        r.setCategory(row.get("category"));
        r.setSourceNameEn(row.get("sourceNameEn"));
        r.setSourceNameAr(row.get("sourceNameAr"));
        r.setBusinessUnit(row.get("businessUnit"));
        r.setFacility(row.get("facility"));
        r.setActivityData(parseDec(row.get("activityData")));
        r.setActivityUnit(row.get("activityUnit"));
        r.setEmissionFactor(parseDec(row.get("emissionFactor")));
        r.setEmissionFactorUnit(row.get("emissionFactorUnit"));
        String efId = row.get("emissionFactorId");
        if (efId != null && !efId.isBlank()) r.setEmissionFactorId(Long.valueOf(efId.trim()));
        BigDecimal val = parseDec(row.get("emissionValueTco2e"));
        r.setEmissionValueTco2e(val == null ? BigDecimal.ZERO : val);
        BigDecimal cov = parseDec(row.get("coveragePercent"));
        if (cov != null) r.setCoveragePercent(cov);
        r.setMethodology(row.get("methodology"));
        r.setEvidenceRef(row.get("evidenceRef"));
        r.setOwnerName(row.get("ownerName"));
        r.setOwnerEmail(row.get("ownerEmail"));
        return r;
    }

    private static Short parseShort(String v, String field) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(field + " required");
        try { return Short.valueOf(v.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(field + " must be a number, got '" + v + "'"); }
    }
    private static BigDecimal parseDec(String v) {
        if (v == null || v.isBlank()) return null;
        try { return new BigDecimal(v.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("not a number: '" + v + "'"); }
    }

    /** Minimal CSV field splitter with double-quote support (handles commas inside "…"). */
    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                out.add(cur.toString()); cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
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
