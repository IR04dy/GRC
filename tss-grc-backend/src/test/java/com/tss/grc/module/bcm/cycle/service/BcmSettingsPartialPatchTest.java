package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmSettings;
import com.tss.grc.module.bcm.cycle.repository.BcmSettingsRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-294 — the THIRD kind of never-firing null guard, where deleting the initialiser would have been
 * the wrong fix.
 *
 * <p>Eleven of the 27 sites are on this entity: the ISO 22301 tolerances every BCM control compares
 * against. {@code BcmSettingsController} exposes a {@code @PatchMapping} that binds the ENTITY, and
 * {@code lib/bcmSettings.ts} sends {@code api.patch('/api/v1/bcm/settings', patch)} — a genuine
 * partial patch object. With the initialisers in place every {@code if (patch.getX() != null)} guard
 * in {@link BcmSettingsService#update} was true on every request, so a patch touching one tolerance
 * reset the other ten to the factory values.
 *
 * <p>⚠ Measured 0 of 1 rows differing in dev and prod, so this was latent — and what made it latent
 * is that no tenant has TUNED their tolerances yet, which is the entire purpose of the settings
 * page. Reachability is a fact about today, not a property of a control (F2-182).
 *
 * <p>🔑 <b>The initialisers were also the register's declared FACTORY DEFAULTS.</b>
 * {@code getOrInit()} relied on the constructor to populate a brand-new row and {@link
 * BcmSettingsService#reset()} read {@code new BcmSettings()} field by field as its source of truth.
 * So the declaration MOVED to {@code BcmSettingsService.defaults()} — one declaration, two readers —
 * rather than being deleted. All three behaviours are asserted here because a structural gate can
 * only see that the initialiser is gone.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmSettingsPartialPatchTest {

    private static final Long ORG_ID = 77L;

    @Mock private BcmSettingsRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private BcmSettingsService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(BcmSettings.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * A tenant that has TUNED every one of the eleven tolerances away from the factory values —
     * which is the state the single live row is not in yet, and the whole reason the reset was
     * latent rather than harmless.
     */
    private BcmSettings tuned() {
        BcmSettings s = new BcmSettings();
        s.setId(3L);
        s.setOrganisationId(ORG_ID);
        s.setRtoWarningHours(2);
        s.setRtoCriticalHours(6);
        s.setRpoWarningHours(1);
        s.setRpoCriticalHours(3);
        s.setExerciseFrequencyMonthsDefault(9);
        s.setExerciseFrequencyMonthsCritical(3);
        s.setEvidenceExpiryWarningDays(21);
        s.setCapaOverdueGraceDays(2);
        s.setRiskMatrixLikelihoodMax(4);
        s.setRiskMatrixImpactMax(4);
        s.setRegulatoryImportStatus("COMPLETE");
        return s;
    }

    @Test
    void aPatchTouchingOneToleranceLeavesTheOtherTenAlone() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(tuned()));

        BcmSettings patch = new BcmSettings();
        patch.setCapaOverdueGraceDays(5);

        BcmSettings saved = service.update(patch);

        assertThat(saved.getCapaOverdueGraceDays()).isEqualTo(5);
        assertThat(saved.getRtoWarningHours()).isEqualTo(2);
        assertThat(saved.getRtoCriticalHours()).isEqualTo(6);
        assertThat(saved.getRpoWarningHours()).isEqualTo(1);
        assertThat(saved.getRpoCriticalHours()).isEqualTo(3);
        assertThat(saved.getExerciseFrequencyMonthsDefault()).isEqualTo(9);
        assertThat(saved.getExerciseFrequencyMonthsCritical()).isEqualTo(3);
        assertThat(saved.getEvidenceExpiryWarningDays()).isEqualTo(21);
        assertThat(saved.getRiskMatrixLikelihoodMax()).isEqualTo(4);
        assertThat(saved.getRiskMatrixImpactMax()).isEqualTo(4);
        assertThat(saved.getRegulatoryImportStatus())
            .as("all eleven, because the reset was all eleven — a patch object carrying one field "
                + "used to arrive carrying every factory value")
            .isEqualTo("COMPLETE");
    }

    @Test
    void aBrandNewTenantRowStillGetsEveryFactoryDefault() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());

        BcmSettings created = service.getOrInit();

        // Every one of these columns is NOT NULL, so this is the load-bearing half of the fix: the
        // database DEFAULT does not rescue it, because the entity maps the column insertable and
        // Hibernate names it in the INSERT and sends the explicit null (F2-240).
        assertThat(created.getRtoWarningHours()).isEqualTo(12);
        assertThat(created.getRtoCriticalHours()).isEqualTo(24);
        assertThat(created.getRpoWarningHours()).isEqualTo(4);
        assertThat(created.getRpoCriticalHours()).isEqualTo(12);
        assertThat(created.getExerciseFrequencyMonthsDefault()).isEqualTo(12);
        assertThat(created.getExerciseFrequencyMonthsCritical()).isEqualTo(6);
        assertThat(created.getEvidenceExpiryWarningDays()).isEqualTo(60);
        assertThat(created.getCapaOverdueGraceDays()).isEqualTo(7);
        assertThat(created.getRiskMatrixLikelihoodMax()).isEqualTo(5);
        assertThat(created.getRiskMatrixImpactMax()).isEqualTo(5);
        assertThat(created.getRegulatoryImportStatus()).isEqualTo("PLACEHOLDER");
        assertThat(created.getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test
    void resetStillRestoresEveryFactoryDefaultFromTheOneDeclaration() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(tuned()));

        BcmSettings after = service.reset();

        assertThat(after.getRtoWarningHours()).isEqualTo(12);
        assertThat(after.getRtoCriticalHours()).isEqualTo(24);
        assertThat(after.getRpoWarningHours()).isEqualTo(4);
        assertThat(after.getRpoCriticalHours()).isEqualTo(12);
        assertThat(after.getExerciseFrequencyMonthsDefault()).isEqualTo(12);
        assertThat(after.getExerciseFrequencyMonthsCritical()).isEqualTo(6);
        assertThat(after.getEvidenceExpiryWarningDays()).isEqualTo(60);
        assertThat(after.getCapaOverdueGraceDays()).isEqualTo(7);
        assertThat(after.getRiskMatrixLikelihoodMax()).isEqualTo(5);
        assertThat(after.getRiskMatrixImpactMax()).isEqualTo(5);
        assertThat(after.getRegulatoryImportStatus()).isEqualTo("PLACEHOLDER");
    }

    @Test
    void theFactoryDeclarationIsOneObjectAndNotASharedSingleton() {
        BcmSettings a = BcmSettingsService.defaults();
        BcmSettings b = BcmSettingsService.defaults();
        a.setRtoWarningHours(999);

        assertThat(b.getRtoWarningHours())
            .as("a static defaults INSTANCE would be a mutable singleton any caller could edit; the "
                + "factory returns a fresh object every call")
            .isEqualTo(12);
    }
}
