package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.AnnualClimateReport;
import com.tss.grc.module.sust.climate.entity.FinancedEmissionsRecord;
import com.tss.grc.module.sust.climate.entity.FinancedEmissionsTarget;
import com.tss.grc.module.sust.climate.entity.TcfdClimateScenario;
import com.tss.grc.module.sust.climate.entity.TcfdSectorImpact;
import com.tss.grc.module.sust.climate.repository.FinancedEmissionsRecordRepository;
import com.tss.grc.module.sust.climate.repository.FinancedEmissionsTargetRepository;
import com.tss.grc.module.sust.climate.repository.TcfdClimateScenarioRepository;
import com.tss.grc.module.sust.climate.repository.TcfdSectorImpactRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * TcfdReportService — Sprint 11 (V186) annual disclosure-pack tests.
 *
 * Verifies that the assembled annual report aggregates scenarios +
 * sector impacts + financed-emissions roll-up + target progress
 * correctly, and that missing-data paths degrade gracefully without
 * blowing up.
 */
@ExtendWith(MockitoExtension.class)
class ClimateReportServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private TcfdClimateScenarioRepository scenarioRepo;
    @Mock private TcfdSectorImpactRepository sectorRepo;
    @Mock private FinancedEmissionsRecordRepository recordRepo;
    @Mock private FinancedEmissionsTargetRepository targetRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TcfdScenarioService scenarioService;
    private FinancedEmissionsService emissionsService;
    private TcfdReportService reportService;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // Build the two collaborator services with real instances so the
        // report service exercises the same blending math the production
        // path uses. Only the repositories are mocked.
        scenarioService = new TcfdScenarioService(scenarioRepo, sectorRepo, audit, eventPublisher);
        emissionsService = new FinancedEmissionsService(recordRepo, targetRepo, audit, eventPublisher);
        reportService = new TcfdReportService(scenarioRepo, sectorRepo, recordRepo,
                scenarioService, emissionsService);
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static TcfdClimateScenario scn(Long id, String type, BigDecimal loss, BigDecimal weight) {
        TcfdClimateScenario s = new TcfdClimateScenario();
        s.setId(id); s.setExternalId(UUID.randomUUID());
        s.setOrganisationId(ORG_ID);
        s.setScenarioCode("TCFD-" + type);
        s.setTitleEn(type);
        s.setScenarioType(type);
        s.setReferenceFramework("NGFS");
        s.setTimeHorizonYears(30);
        s.setGlobalWarmingCelsius(new BigDecimal("1.50"));
        s.setExpectedLossSarMillions(loss);
        s.setProbabilityWeight(weight);
        s.setLifecycleStage("BOARD_APPROVED");
        return s;
    }

    private static TcfdSectorImpact sec(Long id, Long scenarioId, String code) {
        TcfdSectorImpact s = new TcfdSectorImpact();
        s.setId(id); s.setExternalId(UUID.randomUUID());
        s.setOrganisationId(ORG_ID);
        s.setScenarioId(scenarioId);
        s.setSectorCode(code);
        s.setSectorNameEn(code);
        s.setExposureSarMillions(new BigDecimal("1000"));
        s.setTransitionRiskScore(new BigDecimal("50.00"));
        s.setPhysicalRiskScore(new BigDecimal("40.00"));
        s.setExpectedPdUpliftBps(50);
        return s;
    }

    private static FinancedEmissionsRecord rec(Long id, String assetClass) {
        FinancedEmissionsRecord r = new FinancedEmissionsRecord();
        r.setId(id); r.setExternalId(UUID.randomUUID());
        r.setOrganisationId(ORG_ID);
        r.setReportingYear(2026);
        r.setReportingPeriod("ANNUAL");
        r.setAssetClass(assetClass);
        r.setAssetClassLabelEn(assetClass);
        r.setOutstandingAmountSarMillions(new BigDecimal("1000"));
        r.setAttributionFactor(new BigDecimal("0.500000"));
        r.setDataQualityScore(2);
        r.setEmissionsScope1Tco2e(new BigDecimal("100"));
        r.setEmissionsScope2Tco2e(new BigDecimal("50"));
        r.setEmissionsScope3Tco2e(new BigDecimal("200"));
        r.setTotalFinancedEmissionsTco2e(new BigDecimal("175.00"));
        r.setLifecycleStage("PUBLISHED");
        return r;
    }

    private static FinancedEmissionsTarget target() {
        FinancedEmissionsTarget t = new FinancedEmissionsTarget();
        t.setId(99L); t.setExternalId(UUID.randomUUID());
        t.setOrganisationId(ORG_ID);
        t.setTargetCode("FE-TARGET-NETZERO-2050");
        t.setTitleEn("Net-zero by 2050");
        t.setBaselineYear(2026);
        t.setTargetYear(2050);
        t.setBaselineEmissionsTco2e(new BigDecimal("1000"));
        t.setTargetEmissionsTco2e(BigDecimal.ZERO);
        t.setReductionPercent(new BigDecimal("100.00"));
        t.setSbtiAligned(Boolean.TRUE);
        t.setAlignmentFramework("SBTI_1_5C");
        return t;
    }

    // ─── Tests ────────────────────────────────────────────────────

    @Test
    void generateAnnualReportData_aggregatesScenariosSectorImpactsAndEmissions() {
        TcfdClimateScenario orderly = scn(1L, "ORDERLY",
                new BigDecimal("1850.00"), new BigDecimal("0.500"));
        TcfdClimateScenario disorderly = scn(2L, "DISORDERLY",
                new BigDecimal("4250.00"), new BigDecimal("0.500"));
        FinancedEmissionsRecord r1 = rec(10L, "BUSINESS_LOANS");
        FinancedEmissionsRecord r2 = rec(11L, "MORTGAGES");

        when(scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
                ORG_ID, "BOARD_APPROVED"))
                .thenReturn(List.of(orderly, disorderly));
        when(sectorRepo.findByOrganisationIdAndScenarioIdAndIsDeletedFalseOrderBySectorCodeAsc(
                ORG_ID, 1L))
                .thenReturn(List.of(sec(100L, 1L, "ENERGY"), sec(101L, 1L, "MATERIALS")));
        when(sectorRepo.findByOrganisationIdAndScenarioIdAndIsDeletedFalseOrderBySectorCodeAsc(
                ORG_ID, 2L))
                .thenReturn(List.of(sec(102L, 2L, "ENERGY")));
        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(
                ORG_ID, 2026))
                .thenReturn(List.of(r1, r2));
        when(targetRepo.findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(ORG_ID))
                .thenReturn(List.of(target()));
        when(recordRepo.findByOrganisationIdAndIsDeletedFalseOrderByReportingYearDescAssetClassAsc(ORG_ID))
                .thenReturn(List.of(r1, r2));

        AnnualClimateReport out = reportService.generateAnnualReportData(2026);

        assertThat(out.reportingYear()).isEqualTo(2026);
        assertThat(out.scenarios()).hasSize(2);
        assertThat(out.sectorImpacts()).hasSize(3);   // 2 + 1
        // Weighted loss: 1850*0.5 + 4250*0.5 = 3050
        assertThat(out.weightedExpectedLoss().weightedExpectedLossSarMillions())
                .isEqualByComparingTo(new BigDecimal("3050.00"));
        assertThat(out.emissionsRecords()).hasSize(2);
        // Total emissions for the year: 175 + 175 = 350
        assertThat(out.financedEmissionsTotal().totalFinancedEmissionsTco2e())
                .isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(out.targetProgress()).hasSize(1);
        assertThat(out.generatedAtIso()).isNotBlank();
    }

    @Test
    void generateAnnualReportData_handlesMissingDataGracefully() {
        // No scenarios, no records, no targets. Should not throw — every
        // section degrades to empty/zero.
        when(scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
                eq(ORG_ID), eq("BOARD_APPROVED")))
                .thenReturn(List.of());
        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(
                eq(ORG_ID), eq(2026)))
                .thenReturn(List.of());
        when(targetRepo.findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(ORG_ID))
                .thenReturn(List.of());

        AnnualClimateReport out = reportService.generateAnnualReportData(2026);

        assertThat(out.scenarios()).isEmpty();
        assertThat(out.sectorImpacts()).isEmpty();
        assertThat(out.emissionsRecords()).isEmpty();
        assertThat(out.targetProgress()).isEmpty();
        assertThat(out.weightedExpectedLoss().approvedScenarioCount()).isZero();
        assertThat(out.financedEmissionsTotal().totalFinancedEmissionsTco2e())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void generateAnnualReportData_excludesDraftEmissionsFromRecords() {
        FinancedEmissionsRecord published = rec(10L, "BUSINESS_LOANS");
        FinancedEmissionsRecord draft = rec(11L, "MORTGAGES");
        draft.setLifecycleStage("DRAFT");
        when(scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
                eq(ORG_ID), eq("BOARD_APPROVED")))
                .thenReturn(List.of());
        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(
                eq(ORG_ID), eq(2026)))
                .thenReturn(List.of(published, draft));
        when(targetRepo.findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(ORG_ID))
                .thenReturn(List.of());

        AnnualClimateReport out = reportService.generateAnnualReportData(2026);

        assertThat(out.emissionsRecords()).hasSize(1);
        assertThat(out.emissionsRecords().get(0).assetClass()).isEqualTo("BUSINESS_LOANS");
    }

    @Test
    void generateAnnualReportData_carriesSectorImpactCountAcrossScenarios() {
        TcfdClimateScenario orderly = scn(1L, "ORDERLY",
                new BigDecimal("100"), new BigDecimal("1.000"));
        when(scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
                eq(ORG_ID), eq("BOARD_APPROVED")))
                .thenReturn(List.of(orderly));
        when(sectorRepo.findByOrganisationIdAndScenarioIdAndIsDeletedFalseOrderBySectorCodeAsc(
                ORG_ID, 1L))
                .thenReturn(List.of(sec(100L, 1L, "ENERGY"),
                                    sec(101L, 1L, "MATERIALS"),
                                    sec(102L, 1L, "INDUSTRIALS")));
        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(
                eq(ORG_ID), eq(2026)))
                .thenReturn(List.of());
        when(targetRepo.findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(ORG_ID))
                .thenReturn(List.of());

        AnnualClimateReport out = reportService.generateAnnualReportData(2026);

        assertThat(out.scenarios()).hasSize(1);
        assertThat(out.sectorImpacts()).hasSize(3);
    }

    @Test
    void generateAnnualReportData_yearIsCarriedThrough() {
        when(scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
                eq(ORG_ID), eq("BOARD_APPROVED")))
                .thenReturn(List.of());
        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(
                eq(ORG_ID), eq(2030)))
                .thenReturn(List.of());
        when(targetRepo.findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(ORG_ID))
                .thenReturn(List.of());

        AnnualClimateReport out = reportService.generateAnnualReportData(2030);
        assertThat(out.reportingYear()).isEqualTo(2030);
        assertThat(out.financedEmissionsTotal().reportingYear()).isEqualTo(2030);
    }
}
