package com.tss.grc.module.sust.settings.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import com.tss.grc.module.sust.settings.repository.OrganisationSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-294 — the second settings register in the sweep, and the one whose relocated declaration closed
 * a drift the initialisers had been hiding.
 *
 * <p>{@code carbonOffsetCapPct} and {@code climateScenarioWeightsStrict} were field initialisers, so
 * both guards in {@link OrganisationSettingsService#update} fired on every entity-bound request and
 * a partial body reset them. Measured 0 of 1 rows differing in dev and prod, so latent — the live
 * row still holds the factory values, because no tenant has tuned them.
 *
 * <p>🔑 <b>The drift:</b> {@code resetToDefaults()} writes its defaults as LITERALS and never touched
 * {@code carbonOffsetCapPct} at all, so "reset to defaults" left the carbon-offset cap wherever it
 * happened to be. The entity's initialisers and that literal list were two copies of one fact with
 * nobody maintaining the agreement (F2-177), and the third copy going is what made the gap visible.
 * Both now read {@code defaults()}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganisationSettingsDefaultsTest {

    private static final Long ORG_ID = 91L;

    @Mock private OrganisationSettingsRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private OrganisationSettingsService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(OrganisationSettings.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** A tenant that has tuned both swept fields away from the factory values. */
    private OrganisationSettings tuned() {
        OrganisationSettings s = new OrganisationSettings();
        s.setId(4L);
        s.setOrganisationId(ORG_ID);
        s.setEsgReadinessWarningThreshold(70);
        s.setEsgReadinessCriticalThreshold(45);
        s.setEvidenceExpiryWarningDays(45);
        s.setEvidenceSweepCron("0 0 5 * * *");
        s.setScope3PartialCoverageThreshold(new BigDecimal("80.00"));
        s.setCapaOverdueWarningDays(10);
        s.setCapaExecutiveEscalationDays(45);
        s.setSgiAtRiskProgressFactor(new BigDecimal("0.60"));
        s.setClimateScenarioWeightsStrict(true);
        s.setCarbonOffsetCapPct(new BigDecimal("25.00"));
        return s;
    }

    @Test
    void aPartialBodyLeavesTheTwoTunedFieldsAlone() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(tuned()));

        OrganisationSettings req = new OrganisationSettings();
        req.setEvidenceExpiryWarningDays(60);

        OrganisationSettings saved = service.update(req);

        assertThat(saved.getEvidenceExpiryWarningDays()).isEqualTo(60);
        assertThat(saved.getClimateScenarioWeightsStrict())
            .as("the reset turned a tenant's strict climate-scenario weighting off, silently")
            .isTrue();
        assertThat(saved.getCarbonOffsetCapPct()).isEqualByComparingTo("25.00");
    }

    @Test
    void aBrandNewTenantRowStillGetsBothFactoryDefaults() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());

        OrganisationSettings created = service.getOrCreate();

        // Both columns are NOT NULL, so this is the load-bearing half: `getOrCreate` relied on the
        // constructor to populate a brand-new row.
        assertThat(created.getClimateScenarioWeightsStrict()).isFalse();
        assertThat(created.getCarbonOffsetCapPct()).isEqualByComparingTo("10");
        assertThat(created.getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test
    void resetToDefaultsNowResetsTheCarbonOffsetCapItUsedToSkip() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(tuned()));

        OrganisationSettings after = service.resetToDefaults();

        assertThat(after.getClimateScenarioWeightsStrict()).isFalse();
        assertThat(after.getCarbonOffsetCapPct())
            .as("this is the drift the initialisers were hiding: resetToDefaults wrote nine literals "
                + "and never named this field, so 'reset to defaults' left the cap where it was")
            .isEqualByComparingTo("10");
        // the nine it always did reset, so the closure is additive rather than a rewrite
        assertThat(after.getEsgReadinessWarningThreshold()).isEqualTo(60);
        assertThat(after.getSgiAtRiskProgressFactor()).isEqualByComparingTo("0.70");
    }

    @Test
    void theFactoryDeclarationIsOneObjectAndNotASharedSingleton() {
        OrganisationSettings a = OrganisationSettingsService.defaults();
        OrganisationSettings b = OrganisationSettingsService.defaults();
        a.setCarbonOffsetCapPct(new BigDecimal("99"));

        assertThat(b.getCarbonOffsetCapPct()).isEqualByComparingTo("10");
    }
}
