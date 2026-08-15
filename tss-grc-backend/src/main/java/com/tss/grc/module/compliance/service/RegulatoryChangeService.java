package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.RegulatoryChange;
import com.tss.grc.module.compliance.repository.RegulatoryChangeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Implements the buyer-assessment §5.3 critical recommendation #1:
 * regulatory change management lifecycle workflow.
 */
@Service
@RequiredArgsConstructor
public class RegulatoryChangeService {

    private final RegulatoryChangeRepository repo;

    @Transactional(readOnly = true)
    public Page<RegulatoryChange> list(Pageable pageable) {
        return repo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public RegulatoryChange get(Long id) {
        return repo.findById(id)
            .filter(c -> c.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Regulatory change not found: " + id));
    }

    @Transactional
    public RegulatoryChange create(RegulatoryChange req) {
        // Mass-assignment defense — see PolicyService.create() for the
        // full attack path. Null id forces save() to INSERT, blocking
        // cross-tenant merge() overwrites.
        req.setId(null);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return repo.save(req);
    }

    @Transactional
    public RegulatoryChange update(Long id, RegulatoryChange req) {
        RegulatoryChange e = get(id);
        e.setSourceAuthority(req.getSourceAuthority());
        e.setTitleEn(req.getTitleEn());
        e.setTitleAr(req.getTitleAr());
        e.setCitation(req.getCitation());
        e.setSummaryEn(req.getSummaryEn());
        e.setSummaryAr(req.getSummaryAr());
        e.setPublishedDate(req.getPublishedDate());
        e.setEffectiveDate(req.getEffectiveDate());
        e.setApplicability(req.getApplicability());
        e.setImpactLevel(req.getImpactLevel());
        // F2-213 — `status` and `controlsUpdatedCount` are NOT editable through this
        // endpoint. `RegulatoryChangeRequest` (the only client) declares neither, so both
        // arrived null on every edit: the status was written null and the count coerced to
        // 0 by the guard on the line below, which read as "no controls updated" rather than
        // as missing. Both are populated on 35 of 35 live rows in dev and prod. The status
        // belongs to the assessment workflow and the count is derived from the control
        // links — a payload that cannot express either must not be allowed to reset them.
        e.setImpactSummary(req.getImpactSummary());
        e.setAssignedToId(req.getAssignedToId());
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        RegulatoryChange e = get(id);
        e.softDelete(null);
        repo.save(e);
    }

    // ── Lifecycle workflow actions ────────────────────────────────────────────

    /** NEW → APPLICABILITY_ASSESSED. body must contain `applicability` (APPLICABLE | NOT_APPLICABLE | PARTIAL). */
    @Transactional
    public RegulatoryChange assessApplicability(Long id, Map<String, Object> body) {
        RegulatoryChange e = get(id);
        require(e, "NEW");
        String appl = (String) body.getOrDefault("applicability", "APPLICABLE");
        if (!java.util.Set.of("APPLICABLE", "NOT_APPLICABLE", "PARTIAL").contains(appl)) {
            throw new IllegalArgumentException("applicability must be APPLICABLE | NOT_APPLICABLE | PARTIAL");
        }
        e.setApplicability(appl);
        if ("NOT_APPLICABLE".equals(appl)) {
            e.setStatus("NOT_APPLICABLE");
            e.setClosedAt(LocalDateTime.now());
        } else {
            e.setStatus("APPLICABILITY_ASSESSED");
        }
        return repo.save(e);
    }

    /** APPLICABILITY_ASSESSED → IMPACT_ASSESSED. body may contain `impactLevel` and `impactSummary`. */
    @Transactional
    public RegulatoryChange assessImpact(Long id, Map<String, Object> body) {
        RegulatoryChange e = get(id);
        require(e, "APPLICABILITY_ASSESSED");
        Object lvl = body.get("impactLevel");
        if (lvl instanceof String s) e.setImpactLevel(s);
        Object summ = body.get("impactSummary");
        if (summ instanceof String s) e.setImpactSummary(s);
        e.setStatus("IMPACT_ASSESSED");
        return repo.save(e);
    }

    /** IMPACT_ASSESSED → CONTROLS_UPDATED. body may contain `controlsUpdatedCount`. */
    @Transactional
    public RegulatoryChange markControlsUpdated(Long id, Map<String, Object> body) {
        RegulatoryChange e = get(id);
        require(e, "IMPACT_ASSESSED");
        Object n = body.get("controlsUpdatedCount");
        if (n instanceof Number num) e.setControlsUpdatedCount(num.intValue());
        e.setStatus("CONTROLS_UPDATED");
        return repo.save(e);
    }

    /** CONTROLS_UPDATED → CLOSED. */
    @Transactional
    public RegulatoryChange close(Long id) {
        RegulatoryChange e = get(id);
        require(e, "CONTROLS_UPDATED");
        e.setStatus("CLOSED");
        e.setClosedAt(LocalDateTime.now());
        return repo.save(e);
    }

    private void require(RegulatoryChange e, String... allowed) {
        for (String s : allowed) if (s.equals(e.getStatus())) return;
        throw new IllegalArgumentException(
            "Regulatory change must be in " + java.util.Arrays.toString(allowed) + " (was " + e.getStatus() + ")");
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        return Map.of(
            "newCount",                   repo.countByOrganisationIdAndStatus(org, "NEW"),
            "applicabilityAssessedCount", repo.countByOrganisationIdAndStatus(org, "APPLICABILITY_ASSESSED"),
            "impactAssessedCount",        repo.countByOrganisationIdAndStatus(org, "IMPACT_ASSESSED"),
            "controlsUpdatedCount",       repo.countByOrganisationIdAndStatus(org, "CONTROLS_UPDATED"),
            "closedCount",                repo.countByOrganisationIdAndStatus(org, "CLOSED"),
            "notApplicableCount",         repo.countByOrganisationIdAndStatus(org, "NOT_APPLICABLE")
        );
    }
}
