package com.tss.grc.module.sust.finance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.module.sust.finance.repository.CbamLineRepository;
import com.tss.grc.module.sust.finance.repository.EnvPermitRepository;
import com.tss.grc.module.sust.finance.repository.EnvPermitViolationRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetActualRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetMilestoneRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetRepository;
import com.tss.grc.module.sust.finance.repository.IfrsRequirementRepository;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * GF-6 — the CBAM derive-from-GHG math (plan §5.7): factor =
 * {@code Σ VERIFIED/APPROVED S1+S2 of the year × share% / production tonnes}.
 * S3 and non-attested records must never leak into an EU-facing figure, and
 * the audit note must spell out every input.
 */
@ExtendWith(MockitoExtension.class)
class CbamDerivationTest {

    private static final Long ORG = 1L;

    @Mock private EsgTargetRepository targets;
    @Mock private EsgTargetActualRepository actuals;
    @Mock private EsgTargetMilestoneRepository milestones;
    @Mock private IfrsRequirementRepository ifrs;
    @Mock private EnvPermitRepository permits;
    @Mock private EnvPermitViolationRepository violations;
    @Mock private CbamLineRepository cbam;
    @Mock private GhgEmissionRecordRepository ghgRecords;
    @Mock private CorrectiveActionRepository capas;
    @Mock private AuditLogService audit;
    @InjectMocks private EsgHorizonService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static GhgEmissionRecord rec(int scope, String status, String tco2e) {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setOrganisationId(ORG);
        r.setReportingYear((short) 2025);
        r.setScope((short) scope);
        r.setStatus(status);
        r.setEmissionValueTco2e(new BigDecimal(tco2e));
        return r;
    }

    @Test
    void derive_sumsOnlyAttestedScope1And2_andEchoesInputsInTheNote() {
        when(ghgRecords.findByOrganisationIdAndReportingYear(ORG, (short) 2025)).thenReturn(List.of(
            rec(1, "VERIFIED", "600"),
            rec(2, "APPROVED", "400"),
            rec(3, "VERIFIED", "999"),    // S3 — excluded
            rec(1, "DRAFT",    "50")));   // not attested — excluded

        var d = service.cbamDerive(2025, new BigDecimal("30"), new BigDecimal("500"));

        // (600 + 400) × 30 % / 500 t = 0.6 tCO2e/t
        assertThat(d.factorTco2ePerTonne()).isEqualByComparingTo("0.6");
        assertThat(d.totalS1S2Tco2e()).isEqualByComparingTo("1000");
        assertThat(d.recordsCounted()).isEqualTo(2);
        assertThat(d.dataBasisNote())
            .contains("2025").contains("1000").contains("30").contains("500")
            .contains("VERIFIED/APPROVED").contains("2 records");
    }

    @Test
    void derive_rejectsOutOfRangeInputs() {
        assertThatThrownBy(() -> service.cbamDerive(2025, BigDecimal.ZERO, BigDecimal.TEN))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("sharePct");
        assertThatThrownBy(() -> service.cbamDerive(2025, new BigDecimal("101"), BigDecimal.TEN))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("sharePct");
        assertThatThrownBy(() -> service.cbamDerive(2025, BigDecimal.TEN, BigDecimal.ZERO))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("productionTonnes");
    }

    @Test
    void deriveCbamFactor_pureMath_sixDecimalPlaces() {
        // 1234.5 × 12.5 % / 3 t = 51.437500
        assertThat(EsgHorizonService.deriveCbamFactor(
                new BigDecimal("1234.5"), new BigDecimal("12.5"), new BigDecimal("3")))
            .isEqualByComparingTo("51.437500");
    }
}
