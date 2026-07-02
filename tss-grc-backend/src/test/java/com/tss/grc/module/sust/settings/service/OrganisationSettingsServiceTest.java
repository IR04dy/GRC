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

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression test for the partial-update "default clobber" on the
 * Sustainability Settings singleton.
 *
 * <p>{@code PUT /api/sustainability/settings} binds the JPA entity directly.
 * The threshold columns carried field initialisers ({@code = 60}, {@code = 7},
 * …), so a partial PUT that OMITTED a threshold deserialised it to that default
 * (not null), the {@code if (req.getX() != null)} guard fired, and the org's
 * tuned value was silently reset to default. Defaults now live in
 * {@code @PrePersist}, so omitted thresholds stay null and are preserved while
 * a brand-new tenant's row is still seeded on INSERT.
 */
@ExtendWith(MockitoExtension.class)
class OrganisationSettingsServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private OrganisationSettingsRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private OrganisationSettingsService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /** A persisted row whose thresholds the org has tuned away from defaults. */
    private OrganisationSettings tuned() {
        OrganisationSettings s = new OrganisationSettings();
        s.setOrganisationId(ORG_ID);
        s.setEsgReadinessWarningThreshold(80);
        s.setEsgReadinessCriticalThreshold(50);
        s.setEvidenceExpiryWarningDays(45);
        s.setEvidenceSweepCron("0 0 3 * * *");
        s.setScope3PartialCoverageThreshold(new BigDecimal("85.00"));
        s.setCapaOverdueWarningDays(14);
        s.setCapaExecutiveEscalationDays(60);
        s.setSgiAtRiskProgressFactor(new BigDecimal("0.80"));
        return s;
    }

    @Test
    void update_omittingThresholds_preservesTunedValues() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(tuned()));
        when(repo.save(any(OrganisationSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        // Edits only `notes`; every threshold omitted. Post-fix they stay null
        // (no field initialiser) so the null-guards preserve the tuned values.
        OrganisationSettings req = new OrganisationSettings();
        req.setNotes("Adjusted note only.");

        OrganisationSettings saved = service.update(req);

        assertThat(saved.getNotes()).isEqualTo("Adjusted note only.");
        assertThat(saved.getEsgReadinessWarningThreshold()).isEqualTo(80);    // NOT reset to 60
        assertThat(saved.getEsgReadinessCriticalThreshold()).isEqualTo(50);   // NOT reset to 40
        assertThat(saved.getEvidenceExpiryWarningDays()).isEqualTo(45);       // NOT reset to 30
        assertThat(saved.getEvidenceSweepCron()).isEqualTo("0 0 3 * * *");
        assertThat(saved.getScope3PartialCoverageThreshold()).isEqualByComparingTo("85.00");
        assertThat(saved.getCapaOverdueWarningDays()).isEqualTo(14);          // NOT reset to 7
        assertThat(saved.getCapaExecutiveEscalationDays()).isEqualTo(60);     // NOT reset to 30
        assertThat(saved.getSgiAtRiskProgressFactor()).isEqualByComparingTo("0.80");
    }

    @Test
    void update_withExplicitThreshold_stillApplies() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(tuned()));
        when(repo.save(any(OrganisationSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganisationSettings req = new OrganisationSettings();
        req.setCapaOverdueWarningDays(10);

        OrganisationSettings saved = service.update(req);

        assertThat(saved.getCapaOverdueWarningDays()).isEqualTo(10);          // explicit value applied
        assertThat(saved.getEsgReadinessWarningThreshold()).isEqualTo(80);    // others preserved
    }
}
