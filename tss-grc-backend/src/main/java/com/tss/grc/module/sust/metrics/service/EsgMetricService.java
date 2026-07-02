package com.tss.grc.module.sust.metrics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.metrics.entity.EsgMetric;
import com.tss.grc.module.sust.metrics.entity.EsgMetricReading;
import com.tss.grc.module.sust.metrics.repository.EsgMetricRepository;
import com.tss.grc.module.sust.metrics.repository.EsgMetricReadingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * ESG metric service. Unified handler for Environmental / Social /
 * Governance pillars.
 *
 * <p>Progress computation is direction-aware (mirrors the SGI pattern):
 * <ul>
 *   <li>If target > baseline → raise-to-target ("renewable %"):
 *       pct = (current - baseline) / (target - baseline) × 100</li>
 *   <li>If target < baseline → lower-to-target ("LTIFR", "water intensity"):
 *       pct = (baseline - current) / (baseline - target) × 100</li>
 * </ul>
 *
 * <p>Status reflects the live progress snapshot:
 * <ul>
 *   <li>AT_TARGET — current value meets/exceeds target in direction</li>
 *   <li>OFF_TRACK — current value worse than baseline</li>
 *   <li>ACTIVE — in between (the common case)</li>
 *   <li>ARCHIVED — retired</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EsgMetricService {

    private static final Set<String> CATEGORIES = Set.of("ENVIRONMENTAL", "SOCIAL", "GOVERNANCE");
    private static final Set<String> STATUSES   = Set.of("ACTIVE", "AT_TARGET", "OFF_TRACK", "ARCHIVED");

    private final EsgMetricRepository repo;
    private final EsgMetricReadingRepository readingRepo;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    public Page<EsgMetric> listByYear(Short year, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearOrderByCategoryAscMetricCodeAsc(org(), year, p);
    }
    public Page<EsgMetric> listByYearAndCategory(Short year, String cat, Pageable p) {
        return repo.findByOrganisationIdAndReportingYearAndCategoryOrderByMetricCodeAsc(org(), year, cat, p);
    }
    public EsgMetric get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("ESG metric " + id));
    }

    @Transactional
    public EsgMetric create(EsgMetric req) {
        req.setId(null);
        req.setOrganisationId(org());
        if (req.getStatus() == null || req.getStatus().isBlank()) req.setStatus("ACTIVE");
        validate(req);
        refreshStatusSnapshot(req);
        EsgMetric saved = repo.save(req);
        audit.log("CREATE", "EsgMetric", saved.getId(), "ESG metric created");
        return saved;
    }

    @Transactional
    public EsgMetric update(Long id, EsgMetric req) {
        EsgMetric cur = get(id);
        if ("ARCHIVED".equals(cur.getStatus()))
            throw new IllegalStateException("Metric is ARCHIVED.");
        if (req.getCategory() != null)              cur.setCategory(req.getCategory());
        if (req.getSubcategory() != null)           cur.setSubcategory(req.getSubcategory());
        if (req.getMetricNameEn() != null)          cur.setMetricNameEn(req.getMetricNameEn());
        if (req.getMetricNameAr() != null)          cur.setMetricNameAr(req.getMetricNameAr());
        if (req.getDescriptionEn() != null)         cur.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null)         cur.setDescriptionAr(req.getDescriptionAr());
        if (req.getUnit() != null)                   cur.setUnit(req.getUnit());
        if (req.getBaselineValue() != null)         cur.setBaselineValue(req.getBaselineValue());
        if (req.getBaselineYear() != null)          cur.setBaselineYear(req.getBaselineYear());
        if (req.getCurrentValue() != null)          cur.setCurrentValue(req.getCurrentValue());
        if (req.getCurrentValueAsOf() != null)      cur.setCurrentValueAsOf(req.getCurrentValueAsOf());
        if (req.getTargetValue() != null)           cur.setTargetValue(req.getTargetValue());
        if (req.getTargetYear() != null)            cur.setTargetYear(req.getTargetYear());
        if (req.getOwnerName() != null)             cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)            cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getResponsibleDepartment() != null) cur.setResponsibleDepartment(req.getResponsibleDepartment());
        if (req.getLinkedSgiCommitmentId() != null) cur.setLinkedSgiCommitmentId(req.getLinkedSgiCommitmentId());
        if (req.getEvidenceRef() != null)           cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getNotes() != null)                  cur.setNotes(req.getNotes());
        validate(cur);
        refreshStatusSnapshot(cur);
        EsgMetric saved = repo.save(cur);
        audit.log("UPDATE", "EsgMetric", saved.getId(), "ESG metric updated");
        return saved;
    }

    /** Record a reading: update the metric's current_value + as_of AND append an
     *  immutable row to the reading history (S-R), so the time-series is retained. */
    @Transactional
    public EsgMetric recordReading(Long id, BigDecimal currentValue, java.time.LocalDate asOf) {
        EsgMetric cur = get(id);
        java.time.LocalDate effectiveAsOf = asOf != null ? asOf : java.time.LocalDate.now();
        cur.setCurrentValue(currentValue);
        cur.setCurrentValueAsOf(effectiveAsOf);
        refreshStatusSnapshot(cur);
        EsgMetric saved = repo.save(cur);
        EsgMetricReading r = new EsgMetricReading();
        r.setOrganisationId(org());
        r.setMetricId(saved.getId());
        r.setReadingValue(currentValue);
        r.setAsOf(effectiveAsOf);
        readingRepo.save(r);
        audit.log("UPDATE", "EsgMetric", saved.getId(), "Recorded ESG metric reading");
        return saved;
    }

    /** Full reading time-series for a metric (newest first). */
    public List<EsgMetricReading> listReadings(Long id) {
        get(id); // org-scope + existence guard
        return readingRepo.findByOrganisationIdAndMetricIdOrderByAsOfDescIdDesc(org(), id);
    }

    @Transactional
    public EsgMetric archive(Long id) {
        EsgMetric cur = get(id);
        cur.setStatus("ARCHIVED");
        EsgMetric saved = repo.save(cur);
        audit.log("ARCHIVE", "EsgMetric", saved.getId(), "ESG metric archived");
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        EsgMetric cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "EsgMetric", id, "ESG metric deleted");
    }

    /**
     * Per-metric computed progress + direction. Returned shape:
     * {@code { progressPct, direction, distanceFromTarget } }.
     */
    public Map<String, Object> progress(EsgMetric m) {
        BigDecimal baseline = nz(m.getBaselineValue());
        BigDecimal current  = nz(m.getCurrentValue());
        BigDecimal target   = nz(m.getTargetValue());

        String direction = target.compareTo(baseline) >= 0 ? "INCREASE" : "DECREASE";
        BigDecimal progressPct;
        if (target.compareTo(baseline) == 0) {
            progressPct = current.compareTo(target) == 0 ? new BigDecimal("100.0") : BigDecimal.ZERO;
        } else if ("INCREASE".equals(direction)) {
            progressPct = pct(current.subtract(baseline), target.subtract(baseline));
        } else {
            progressPct = pct(baseline.subtract(current), baseline.subtract(target));
        }
        BigDecimal distance = "INCREASE".equals(direction)
            ? target.subtract(current)
            : current.subtract(target);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("progressPct", progressPct);
        out.put("direction", direction);
        out.put("distanceFromTarget", distance);
        return out;
    }

    /**
     * Aggregate rollup — totals per category, count off-track / at-target,
     * and a mean progress per category.
     */
    public Map<String, Object> aggregate(Short year) {
        List<EsgMetric> all = repo.findByOrganisationIdAndReportingYear(org(), year);

        Map<String, Integer> countByCategory = new LinkedHashMap<>();
        Map<String, Integer> atTargetByCategory = new LinkedHashMap<>();
        Map<String, Integer> offTrackByCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> progressSumByCategory = new LinkedHashMap<>();
        for (String c : CATEGORIES) {
            countByCategory.put(c, 0);
            atTargetByCategory.put(c, 0);
            offTrackByCategory.put(c, 0);
            progressSumByCategory.put(c, BigDecimal.ZERO);
        }

        for (EsgMetric m : all) {
            countByCategory.merge(m.getCategory(), 1, Integer::sum);
            Map<String, Object> p = progress(m);
            BigDecimal pct = (BigDecimal) p.get("progressPct");
            progressSumByCategory.merge(m.getCategory(), pct, BigDecimal::add);
            if ("AT_TARGET".equals(m.getStatus())) atTargetByCategory.merge(m.getCategory(), 1, Integer::sum);
            if ("OFF_TRACK".equals(m.getStatus())) offTrackByCategory.merge(m.getCategory(), 1, Integer::sum);
        }

        // Mean progress per category for the dashboard's pillar gauges.
        Map<String, BigDecimal> meanProgress = new LinkedHashMap<>();
        for (String c : CATEGORIES) {
            int n = countByCategory.get(c);
            meanProgress.put(c, n == 0 ? BigDecimal.ZERO
                : progressSumByCategory.get(c).divide(new BigDecimal(n), 1, RoundingMode.HALF_UP));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("total", all.size());
        out.put("byCategory", countByCategory);
        out.put("atTargetByCategory", atTargetByCategory);
        out.put("offTrackByCategory", offTrackByCategory);
        out.put("meanProgressByCategory", meanProgress);
        return out;
    }

    /**
     * Snapshot status on save based on the live progress %. Pure
     * computed convenience — does not transition manually-set statuses
     * if the user explicitly set ARCHIVED.
     */
    private void refreshStatusSnapshot(EsgMetric m) {
        if ("ARCHIVED".equals(m.getStatus())) return;
        if (m.getBaselineValue() == null || m.getCurrentValue() == null || m.getTargetValue() == null) {
            m.setStatus("ACTIVE");
            return;
        }
        Map<String, Object> p = progress(m);
        BigDecimal pct = (BigDecimal) p.get("progressPct");
        BigDecimal current  = m.getCurrentValue();
        BigDecimal baseline = m.getBaselineValue();
        BigDecimal target   = m.getTargetValue();
        boolean increase = target.compareTo(baseline) >= 0;

        // OFF_TRACK — current is worse than baseline (regression).
        boolean offTrack = increase
            ? current.compareTo(baseline) < 0
            : current.compareTo(baseline) > 0;
        if (offTrack) { m.setStatus("OFF_TRACK"); return; }

        if (pct.compareTo(new BigDecimal("100.0")) >= 0) {
            m.setStatus("AT_TARGET");
        } else {
            m.setStatus("ACTIVE");
        }
    }

    private void validate(EsgMetric m) {
        if (m.getMetricNameEn() == null || m.getMetricNameEn().isBlank())
            throw new IllegalArgumentException("metricNameEn required");
        if (m.getCategory() == null || !CATEGORIES.contains(m.getCategory()))
            throw new IllegalArgumentException("category must be one of " + CATEGORIES);
        if (m.getReportingYear() == null)
            throw new IllegalArgumentException("reportingYear required");
        if (m.getUnit() == null || m.getUnit().isBlank())
            throw new IllegalArgumentException("unit required");
        if (!STATUSES.contains(m.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal pct(BigDecimal num, BigDecimal den) {
        if (den.signum() == 0) return BigDecimal.ZERO;
        BigDecimal raw = num.multiply(new BigDecimal("100"))
            .divide(den, 1, RoundingMode.HALF_UP);
        if (raw.signum() < 0) return BigDecimal.ZERO;
        BigDecimal cap = new BigDecimal("150.0");
        return raw.compareTo(cap) > 0 ? cap : raw;
    }
}
