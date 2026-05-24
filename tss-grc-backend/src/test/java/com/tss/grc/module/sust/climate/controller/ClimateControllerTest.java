package com.tss.grc.module.sust.climate.controller;

import com.tss.grc.module.sust.climate.dto.ClimateDtos.AnnualClimateReport;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FeLifecycleTransitionRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTotal;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.ProbabilityWeightedExpectedLoss;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdLifecycleTransitionRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioResponse;
import com.tss.grc.module.sust.climate.service.FinancedEmissionsService;
import com.tss.grc.module.sust.climate.service.TcfdReportService;
import com.tss.grc.module.sust.climate.service.TcfdScenarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Light smoke tests across the three climate controllers — verifies
 * URL-to-service dispatch. The {@code @PreAuthorize} guards are
 * enforced by Spring Security at the proxy boundary in integration.
 */
@ExtendWith(MockitoExtension.class)
class ClimateControllerTest {

    private static final UUID SCN_EX = UUID.randomUUID();
    private static final UUID REC_EX = UUID.randomUUID();

    @Mock private TcfdScenarioService scenarioService;
    @Mock private FinancedEmissionsService emissionsService;
    @Mock private TcfdReportService reportService;

    private static TcfdScenarioResponse scnStub() {
        return new TcfdScenarioResponse(
                1L, SCN_EX, "TCFD-2026-ORDERLY",
                "Orderly", "منظَّم",
                null, null,
                "ORDERLY", "NGFS",
                30, new BigDecimal("1.50"),
                null,
                new BigDecimal("142.50"), new BigDecimal("1850.00"),
                new BigDecimal("0.500"),
                "BOARD_APPROVED", 99L, null,
                null, null
        );
    }

    private static FinancedEmissionsResponse recStub() {
        return new FinancedEmissionsResponse(
                10L, REC_EX,
                2026, "ANNUAL", "BUSINESS_LOANS",
                "Business loans", "قروض الأعمال",
                new BigDecimal("58900"), new BigDecimal("0.082"),
                3,
                new BigDecimal("1850000"), new BigDecimal("420000"), new BigDecimal("2350000"),
                new BigDecimal("378840.00"),
                null, null,
                "PUBLISHED",
                null, null
        );
    }

    // ─── TcfdScenarioController ────────────────────────────────────

    @Test
    void scenarioController_list_dispatches() {
        TcfdScenarioController c = new TcfdScenarioController(scenarioService);
        when(scenarioService.list()).thenReturn(List.of(scnStub()));

        List<TcfdScenarioResponse> out = c.list();

        assertThat(out).hasSize(1);
        verify(scenarioService).list();
    }

    @Test
    void scenarioController_create_returns201() {
        TcfdScenarioController c = new TcfdScenarioController(scenarioService);
        when(scenarioService.create(any(TcfdScenarioRequest.class))).thenReturn(scnStub());

        TcfdScenarioRequest req = new TcfdScenarioRequest(
                null, "Orderly", "منظَّم", null, null,
                "ORDERLY", "NGFS", 30, new BigDecimal("1.50"),
                null, null, null, new BigDecimal("0.5"));
        ResponseEntity<TcfdScenarioResponse> resp = c.create(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().scenarioCode()).isEqualTo("TCFD-2026-ORDERLY");
    }

    @Test
    void scenarioController_transition_passesActingUserId() {
        TcfdScenarioController c = new TcfdScenarioController(scenarioService);
        when(scenarioService.transition(any(UUID.class), any(TcfdLifecycleTransitionRequest.class), eq((Long) null)))
                .thenReturn(scnStub());

        TcfdScenarioResponse out = c.transition(SCN_EX,
                new TcfdLifecycleTransitionRequest("BOARD_APPROVED"), null);

        assertThat(out.lifecycleStage()).isEqualTo("BOARD_APPROVED");
    }

    @Test
    void scenarioController_weightedExpectedLoss_dispatches() {
        TcfdScenarioController c = new TcfdScenarioController(scenarioService);
        when(scenarioService.computeProbabilityWeightedExpectedLoss())
                .thenReturn(new ProbabilityWeightedExpectedLoss(
                        new BigDecimal("2880.50"), new BigDecimal("1.000"), 3, null));

        ProbabilityWeightedExpectedLoss out = c.weightedExpectedLoss();
        assertThat(out.approvedScenarioCount()).isEqualTo(3);
        assertThat(out.weightedExpectedLossSarMillions())
                .isEqualByComparingTo(new BigDecimal("2880.50"));
    }

    // ─── FinancedEmissionsController ───────────────────────────────

    @Test
    void emissionsController_create_returns201() {
        FinancedEmissionsController c = new FinancedEmissionsController(emissionsService);
        when(emissionsService.create(any(FinancedEmissionsRequest.class))).thenReturn(recStub());

        FinancedEmissionsRequest req = new FinancedEmissionsRequest(
                2026, "ANNUAL", "BUSINESS_LOANS",
                "Business loans", "قروض الأعمال",
                new BigDecimal("58900"), new BigDecimal("0.082"),
                3,
                new BigDecimal("1850000"), new BigDecimal("420000"), new BigDecimal("2350000"),
                "PCAF", "PCAF AR"
        );
        ResponseEntity<FinancedEmissionsResponse> resp = c.create(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().assetClass()).isEqualTo("BUSINESS_LOANS");
    }

    @Test
    void emissionsController_transition_dispatches() {
        FinancedEmissionsController c = new FinancedEmissionsController(emissionsService);
        when(emissionsService.transition(any(UUID.class), any(FeLifecycleTransitionRequest.class)))
                .thenReturn(recStub());

        FinancedEmissionsResponse out = c.transition(REC_EX,
                new FeLifecycleTransitionRequest("APPROVED"));
        assertThat(out.assetClass()).isEqualTo("BUSINESS_LOANS");
    }

    @Test
    void emissionsController_total_dispatches() {
        FinancedEmissionsController c = new FinancedEmissionsController(emissionsService);
        when(emissionsService.computeTotalForYear(2026, null))
                .thenReturn(new FinancedEmissionsTotal(
                        2026, "ANNUAL",
                        new BigDecimal("378840"),
                        new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("200"),
                        new BigDecimal("58900"), new BigDecimal("3.00"), 1));

        FinancedEmissionsTotal out = c.total(2026, null);
        assertThat(out.reportingYear()).isEqualTo(2026);
        assertThat(out.assetClassCount()).isEqualTo(1);
    }

    // ─── ClimateReportController ───────────────────────────────────

    @Test
    void reportController_annual_dispatches() {
        ClimateReportController c = new ClimateReportController(reportService);
        AnnualClimateReport stub = new AnnualClimateReport(
                2026, List.of(), List.of(),
                new ProbabilityWeightedExpectedLoss(BigDecimal.ZERO, BigDecimal.ZERO, 0, null),
                new FinancedEmissionsTotal(2026, "ANNUAL",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, 0),
                List.of(), List.of(), "2026-05-23T00:00:00Z");
        when(reportService.generateAnnualReportData(2026)).thenReturn(stub);

        AnnualClimateReport out = c.annual(2026);
        assertThat(out.reportingYear()).isEqualTo(2026);
    }
}
