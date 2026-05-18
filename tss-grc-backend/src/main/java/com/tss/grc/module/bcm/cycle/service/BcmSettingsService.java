package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmSettings;
import com.tss.grc.module.bcm.cycle.repository.BcmSettingsRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * BCM tenant settings service — audit Tier-1 #6.
 *
 * <p>Lazy initialiser pattern: {@link #getOrInit()} creates the default row on
 * first read so callers never have to handle a missing record. {@link #update}
 * is a single PATCH that validates threshold consistency
 * ({@code warning ≤ critical} for RTO and RPO, frequencies in months are
 * positive, etc.).
 *
 * <p>Defaults baked in the entity constructor match SAMA Operational Resilience
 * Framework expectations for retail-bank operations. A new tenant onboarding
 * for a different sector typically overrides a handful of values on the
 * settings page.
 */
@Service
@RequiredArgsConstructor
public class BcmSettingsService {

    private static final Set<String> IMPORT_STATUSES = Set.of("PLACEHOLDER", "PARTIAL", "COMPLETE");

    private final BcmSettingsRepository repo;
    private final AuditLogService audit;

    @Transactional
    public BcmSettings getOrInit() {
        Long org = TenantContext.getCurrentTenant();
        return repo.findByOrganisationId(org).orElseGet(() -> {
            BcmSettings fresh = new BcmSettings();
            fresh.setOrganisationId(org);
            // every default is set in the entity constructor
            BcmSettings saved = repo.save(fresh);
            audit.log("CREATE", "BcmSettings", saved.getId(),
                "BCM settings initialised for tenant " + org);
            return saved;
        });
    }

    @Transactional
    public BcmSettings update(BcmSettings patch) {
        BcmSettings existing = getOrInit();
        // Patch scalars
        if (patch.getRtoWarningHours()  != null) existing.setRtoWarningHours(patch.getRtoWarningHours());
        if (patch.getRtoCriticalHours() != null) existing.setRtoCriticalHours(patch.getRtoCriticalHours());
        if (patch.getRpoWarningHours()  != null) existing.setRpoWarningHours(patch.getRpoWarningHours());
        if (patch.getRpoCriticalHours() != null) existing.setRpoCriticalHours(patch.getRpoCriticalHours());
        if (patch.getExerciseFrequencyMonthsDefault()  != null) existing.setExerciseFrequencyMonthsDefault(patch.getExerciseFrequencyMonthsDefault());
        if (patch.getExerciseFrequencyMonthsCritical() != null) existing.setExerciseFrequencyMonthsCritical(patch.getExerciseFrequencyMonthsCritical());
        if (patch.getEvidenceExpiryWarningDays() != null) existing.setEvidenceExpiryWarningDays(patch.getEvidenceExpiryWarningDays());
        if (patch.getCapaOverdueGraceDays()      != null) existing.setCapaOverdueGraceDays(patch.getCapaOverdueGraceDays());
        if (patch.getRiskMatrixLikelihoodMax()   != null) existing.setRiskMatrixLikelihoodMax(patch.getRiskMatrixLikelihoodMax());
        if (patch.getRiskMatrixImpactMax()       != null) existing.setRiskMatrixImpactMax(patch.getRiskMatrixImpactMax());

        // JSON blobs — only validated for non-empty if the caller sent them
        if (patch.getApprovalMatrixJson()    != null) existing.setApprovalMatrixJson(patch.getApprovalMatrixJson());
        if (patch.getNotificationRulesJson() != null) existing.setNotificationRulesJson(patch.getNotificationRulesJson());

        // Framework versions
        if (patch.getIso22301Version()        != null) existing.setIso22301Version(patch.getIso22301Version());
        if (patch.getSamaOpresVersion()       != null) existing.setSamaOpresVersion(patch.getSamaOpresVersion());
        if (patch.getDgaFrameworkVersion()    != null) existing.setDgaFrameworkVersion(patch.getDgaFrameworkVersion());
        if (patch.getNcemaFrameworkVersion()  != null) existing.setNcemaFrameworkVersion(patch.getNcemaFrameworkVersion());
        if (patch.getRegulatoryImportStatus() != null) {
            if (!IMPORT_STATUSES.contains(patch.getRegulatoryImportStatus()))
                throw new IllegalArgumentException("regulatoryImportStatus must be one of " + IMPORT_STATUSES);
            existing.setRegulatoryImportStatus(patch.getRegulatoryImportStatus());
        }
        if (patch.getRegulatoryImportNotes() != null) existing.setRegulatoryImportNotes(patch.getRegulatoryImportNotes());

        validate(existing);
        existing.setUpdatedAt(LocalDateTime.now());
        BcmSettings saved = repo.save(existing);
        audit.log("UPDATE", "BcmSettings", saved.getId(), "BCM settings updated");
        return saved;
    }

    /**
     * Restore the row to constructor defaults. Used by the admin "Reset to defaults"
     * button on the settings page.
     */
    @Transactional
    public BcmSettings reset() {
        BcmSettings existing = getOrInit();
        BcmSettings fresh = new BcmSettings();
        existing.setRtoWarningHours(fresh.getRtoWarningHours());
        existing.setRtoCriticalHours(fresh.getRtoCriticalHours());
        existing.setRpoWarningHours(fresh.getRpoWarningHours());
        existing.setRpoCriticalHours(fresh.getRpoCriticalHours());
        existing.setExerciseFrequencyMonthsDefault(fresh.getExerciseFrequencyMonthsDefault());
        existing.setExerciseFrequencyMonthsCritical(fresh.getExerciseFrequencyMonthsCritical());
        existing.setEvidenceExpiryWarningDays(fresh.getEvidenceExpiryWarningDays());
        existing.setCapaOverdueGraceDays(fresh.getCapaOverdueGraceDays());
        existing.setRiskMatrixLikelihoodMax(fresh.getRiskMatrixLikelihoodMax());
        existing.setRiskMatrixImpactMax(fresh.getRiskMatrixImpactMax());
        existing.setApprovalMatrixJson(null);
        existing.setNotificationRulesJson(null);
        existing.setRegulatoryImportStatus("PLACEHOLDER");
        existing.setUpdatedAt(LocalDateTime.now());
        BcmSettings saved = repo.save(existing);
        audit.log("CUSTOM", "BcmSettings", saved.getId(), "BCM settings reset to defaults");
        return saved;
    }

    private void validate(BcmSettings s) {
        if (s.getRtoWarningHours() < 0 || s.getRtoCriticalHours() < 0
                || s.getRpoWarningHours() < 0 || s.getRpoCriticalHours() < 0)
            throw new IllegalArgumentException("Threshold hours cannot be negative");
        if (s.getRtoWarningHours() > s.getRtoCriticalHours())
            throw new IllegalArgumentException("rtoWarningHours must be ≤ rtoCriticalHours");
        if (s.getRpoWarningHours() > s.getRpoCriticalHours())
            throw new IllegalArgumentException("rpoWarningHours must be ≤ rpoCriticalHours");
        if (s.getExerciseFrequencyMonthsDefault() <= 0 || s.getExerciseFrequencyMonthsCritical() <= 0)
            throw new IllegalArgumentException("Exercise frequencies must be positive");
        if (s.getExerciseFrequencyMonthsCritical() > s.getExerciseFrequencyMonthsDefault())
            throw new IllegalArgumentException(
                "exerciseFrequencyMonthsCritical must be ≤ exerciseFrequencyMonthsDefault");
        if (s.getEvidenceExpiryWarningDays() <= 0)
            throw new IllegalArgumentException("evidenceExpiryWarningDays must be positive");
        if (s.getCapaOverdueGraceDays() < 0)
            throw new IllegalArgumentException("capaOverdueGraceDays cannot be negative");
        if (s.getRiskMatrixLikelihoodMax() < 3 || s.getRiskMatrixImpactMax() < 3)
            throw new IllegalArgumentException("Risk matrix dimensions must be ≥ 3");
        if (s.getRiskMatrixLikelihoodMax() > 10 || s.getRiskMatrixImpactMax() > 10)
            throw new IllegalArgumentException("Risk matrix dimensions must be ≤ 10");
    }
}
