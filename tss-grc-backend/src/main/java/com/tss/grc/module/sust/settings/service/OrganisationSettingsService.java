package com.tss.grc.module.sust.settings.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import com.tss.grc.module.sust.settings.repository.OrganisationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Per-organisation Sustainability settings service.
 *
 * <p>Lazy-creates a default row for the calling tenant on first read —
 * so a fresh org never sees a 404, and the defaults match the
 * pre-V65 hard-coded constants exactly. Other slice services that
 * need a threshold can call {@link #getOrCreate()} without any
 * "missing settings" branch.
 */
@Service
@RequiredArgsConstructor
public class OrganisationSettingsService {

    private final OrganisationSettingsRepository repo;
    private final AuditLogService audit;

    /**
     * F2-294 — the two defaults {@link OrganisationSettings} used to carry as field
     * initialisers, in one place both readers name.
     *
     * <p>They had to move rather than simply go: {@link #getOrCreate} relied on the
     * constructor to populate a brand-new row, and both columns are NOT NULL. Relocating
     * them also closed a drift the initialisers were hiding — {@link #resetToDefaults}
     * writes its defaults as literals and never touched {@code carbonOffsetCapPct}, so
     * "reset to defaults" left the carbon-offset cap wherever it happened to be.
     */
    static OrganisationSettings defaults() {
        OrganisationSettings d = new OrganisationSettings();
        d.setClimateScenarioWeightsStrict(false);
        d.setCarbonOffsetCapPct(new BigDecimal("10"));
        return d;
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    /**
     * Read the calling org's settings. Lazy-creates the row with all
     * defaults if it doesn't exist yet. Always succeeds.
     */
    @Transactional
    public OrganisationSettings getOrCreate() {
        Long orgId = org();
        return repo.findByOrganisationId(orgId)
            .orElseGet(() -> {
                // F2-294 — the two defaults this row used to inherit from the entity's
                // field initialisers now come from defaults(), the one declaration
                // `resetToDefaults()` also reads.
                OrganisationSettings s = defaults();
                s.setOrganisationId(orgId);
                return repo.save(s);
            });
    }

    @Transactional
    public OrganisationSettings update(OrganisationSettings req) {
        OrganisationSettings cur = getOrCreate();

        if (req.getEsgReadinessWarningThreshold() != null)
            cur.setEsgReadinessWarningThreshold(req.getEsgReadinessWarningThreshold());
        if (req.getEsgReadinessCriticalThreshold() != null)
            cur.setEsgReadinessCriticalThreshold(req.getEsgReadinessCriticalThreshold());
        if (req.getEvidenceExpiryWarningDays() != null)
            cur.setEvidenceExpiryWarningDays(req.getEvidenceExpiryWarningDays());
        if (req.getEvidenceSweepCron() != null)
            cur.setEvidenceSweepCron(req.getEvidenceSweepCron());
        if (req.getScope3PartialCoverageThreshold() != null)
            cur.setScope3PartialCoverageThreshold(req.getScope3PartialCoverageThreshold());
        if (req.getCapaOverdueWarningDays() != null)
            cur.setCapaOverdueWarningDays(req.getCapaOverdueWarningDays());
        if (req.getCapaExecutiveEscalationDays() != null)
            cur.setCapaExecutiveEscalationDays(req.getCapaExecutiveEscalationDays());
        if (req.getSgiAtRiskProgressFactor() != null)
            cur.setSgiAtRiskProgressFactor(req.getSgiAtRiskProgressFactor());
        if (req.getClimateScenarioWeightsStrict() != null)
            cur.setClimateScenarioWeightsStrict(req.getClimateScenarioWeightsStrict());
        if (req.getCarbonOffsetCapPct() != null)   // V295 — decision #4, tenant-configurable
            cur.setCarbonOffsetCapPct(req.getCarbonOffsetCapPct());
        if (req.getNotes() != null) cur.setNotes(req.getNotes());

        validate(cur);
        OrganisationSettings saved = repo.save(cur);
        audit.log("UPDATE", "OrganisationSettings", saved.getId(), "Sustainability settings updated");
        return saved;
    }

    /**
     * Reset all thresholds back to the V65 schema defaults. Useful
     * during dev when tuning has gone sideways.
     */
    @Transactional
    public OrganisationSettings resetToDefaults() {
        OrganisationSettings cur = getOrCreate();
        cur.setEsgReadinessWarningThreshold(60);
        cur.setEsgReadinessCriticalThreshold(40);
        cur.setEvidenceExpiryWarningDays(30);
        cur.setEvidenceSweepCron("0 0 6 * * *");
        cur.setScope3PartialCoverageThreshold(new BigDecimal("70.00"));
        cur.setCapaOverdueWarningDays(7);
        cur.setCapaExecutiveEscalationDays(30);
        cur.setSgiAtRiskProgressFactor(new BigDecimal("0.70"));
        // ⚠⚠ F2-294 — this method wrote nine defaults as literals and never touched
        // `carbonOffsetCapPct`, so "reset to defaults" left the carbon-offset cap exactly
        // where it was. The entity's initialisers and this list were two copies of one
        // fact with nobody maintaining the agreement (F2-177), and the drift only became
        // visible once the initialisers moved. Both fields now come from DEFAULTS.
        OrganisationSettings d = defaults();
        cur.setClimateScenarioWeightsStrict(d.getClimateScenarioWeightsStrict());
        cur.setCarbonOffsetCapPct(d.getCarbonOffsetCapPct());
        OrganisationSettings saved = repo.save(cur);
        audit.log("UPDATE", "OrganisationSettings", saved.getId(), "Sustainability settings reset to defaults");
        return saved;
    }

    // ── Validation — mirrors the SQL CHECK constraints ─────────────
    private void validate(OrganisationSettings s) {
        if (s.getEsgReadinessCriticalThreshold() > s.getEsgReadinessWarningThreshold())
            throw new IllegalArgumentException("Critical threshold must be ≤ warning threshold.");
        if (s.getEsgReadinessWarningThreshold() < 0 || s.getEsgReadinessWarningThreshold() > 100
            || s.getEsgReadinessCriticalThreshold() < 0 || s.getEsgReadinessCriticalThreshold() > 100)
            throw new IllegalArgumentException("Score thresholds must be 0..100.");
        if (s.getScope3PartialCoverageThreshold().compareTo(BigDecimal.ZERO) < 0
            || s.getScope3PartialCoverageThreshold().compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Scope-3 threshold must be 0..100.");
        if (s.getSgiAtRiskProgressFactor().compareTo(BigDecimal.ZERO) < 0
            || s.getSgiAtRiskProgressFactor().compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("SGI at-risk factor must be 0..1.");
        if (s.getCapaExecutiveEscalationDays() < s.getCapaOverdueWarningDays())
            throw new IllegalArgumentException("Executive-escalation days must be ≥ overdue-warning days.");
    }
}
