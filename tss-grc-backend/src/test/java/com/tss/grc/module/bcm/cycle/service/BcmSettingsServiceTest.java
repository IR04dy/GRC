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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BcmSettingsService lock — audit Tier-1 #6.
 *
 * <ul>
 *   <li>getOrInit auto-creates default row on first read</li>
 *   <li>update rejects warning &gt; critical thresholds</li>
 *   <li>update rejects critical &gt; default exercise frequency</li>
 *   <li>update rejects unknown regulatoryImportStatus</li>
 *   <li>update rejects out-of-range risk matrix dimensions</li>
 *   <li>reset clears optional fields and restores defaults</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmSettingsServiceTest {

    private static final Long ORG_ID = 51L;

    @Mock private BcmSettingsRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private BcmSettingsService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(BcmSettings.class))).thenAnswer(inv -> {
            BcmSettings s = inv.getArgument(0);
            if (s.getId() == null) s.setId(1L);
            return s;
        });
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test void getOrInit_autoCreatesDefaultsOnFirstRead() {
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());

        BcmSettings s = service.getOrInit();
        assertThat(s.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(s.getRtoWarningHours()).isEqualTo(12);
        assertThat(s.getExerciseFrequencyMonthsDefault()).isEqualTo(12);
        assertThat(s.getRegulatoryImportStatus()).isEqualTo("PLACEHOLDER");
    }

    @Test void update_rejectsWarningGreaterThanCritical() {
        BcmSettings existing = new BcmSettings(); existing.setOrganisationId(ORG_ID);
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(existing));

        BcmSettings patch = new BcmSettings();
        patch.setRtoWarningHours(48); patch.setRtoCriticalHours(24);
        assertThatThrownBy(() -> service.update(patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rtoWarning");
    }

    @Test void update_rejectsCriticalGreaterThanDefaultExerciseFreq() {
        BcmSettings existing = new BcmSettings(); existing.setOrganisationId(ORG_ID);
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(existing));

        BcmSettings patch = new BcmSettings();
        patch.setExerciseFrequencyMonthsDefault(6);
        patch.setExerciseFrequencyMonthsCritical(12);
        assertThatThrownBy(() -> service.update(patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exerciseFrequencyMonthsCritical");
    }

    @Test void update_rejectsUnknownImportStatus() {
        BcmSettings existing = new BcmSettings(); existing.setOrganisationId(ORG_ID);
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(existing));

        BcmSettings patch = new BcmSettings();
        patch.setRegulatoryImportStatus("BANANAS");
        assertThatThrownBy(() -> service.update(patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("regulatoryImportStatus");
    }

    @Test void update_rejectsRiskMatrixOutOfRange() {
        BcmSettings existing = new BcmSettings(); existing.setOrganisationId(ORG_ID);
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(existing));

        BcmSettings patch = new BcmSettings();
        patch.setRiskMatrixLikelihoodMax(20);
        assertThatThrownBy(() -> service.update(patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Risk matrix");
    }

    @Test void reset_restoresDefaultsAndClearsBlobs() {
        BcmSettings existing = new BcmSettings(); existing.setOrganisationId(ORG_ID);
        existing.setRtoWarningHours(99);
        existing.setApprovalMatrixJson("{\"foo\":\"bar\"}");
        existing.setRegulatoryImportStatus("COMPLETE");
        when(repo.findByOrganisationId(ORG_ID)).thenReturn(Optional.of(existing));

        BcmSettings out = service.reset();
        assertThat(out.getRtoWarningHours()).isEqualTo(12);
        assertThat(out.getApprovalMatrixJson()).isNull();
        assertThat(out.getRegulatoryImportStatus()).isEqualTo("PLACEHOLDER");
    }
}
