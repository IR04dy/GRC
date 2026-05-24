package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.AnnualClimateReport;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTotal;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.ProbabilityWeightedExpectedLoss;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TargetProgress;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdSectorImpactResponse;
import com.tss.grc.module.sust.climate.entity.TcfdClimateScenario;
import com.tss.grc.module.sust.climate.repository.FinancedEmissionsRecordRepository;
import com.tss.grc.module.sust.climate.repository.TcfdClimateScenarioRepository;
import com.tss.grc.module.sust.climate.repository.TcfdSectorImpactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Assembles the annual IFRS S2 / TCFD disclosure pack — Sprint 11
 * (V186).
 *
 * <p>The pack carries:
 * <ul>
 *   <li>Every {@code BOARD_APPROVED} TCFD scenario for the tenant
 *       (orderly / disorderly / hot-house).</li>
 *   <li>Per-scenario sector impacts (transition + physical risk,
 *       expected PD uplift).</li>
 *   <li>Probability-weighted expected-loss blend across approved
 *       scenarios.</li>
 *   <li>Financed-emissions roll-up for the requested year (Scope-1/2/3
 *       attributed × outstanding × attribution factor).</li>
 *   <li>Per asset-class records (data quality, methodology).</li>
 *   <li>Target progress (baseline → target percent, achievement
 *       ratio).</li>
 * </ul>
 *
 * <p>Missing data is handled gracefully — empty scenario lists, missing
 * emissions roll-ups, no targets — every section degrades to an empty
 * collection or zero aggregate rather than throwing. The frontend
 * decides how to render the placeholders.
 */
@Service
@RequiredArgsConstructor
public class TcfdReportService {

    private final TcfdClimateScenarioRepository scenarioRepo;
    private final TcfdSectorImpactRepository sectorRepo;
    private final FinancedEmissionsRecordRepository recordRepo;
    private final TcfdScenarioService scenarioService;
    private final FinancedEmissionsService emissionsService;

    @Transactional(readOnly = true)
    public AnnualClimateReport generateAnnualReportData(int reportingYear) {
        Long orgId = TenantContext.getCurrentTenant();

        // 1. Board-approved scenarios.
        List<TcfdClimateScenario> approvedScenarios = scenarioRepo
                .findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(orgId, "BOARD_APPROVED");
        List<TcfdScenarioResponse> scenarios = approvedScenarios.stream()
                .map(TcfdScenarioResponse::from).toList();

        // 2. All sector impacts that belong to those scenarios.
        List<TcfdSectorImpactResponse> sectorImpacts = approvedScenarios.stream()
                .flatMap(s -> sectorRepo.findByOrganisationIdAndScenarioIdAndIsDeletedFalseOrderBySectorCodeAsc(
                                orgId, s.getId()).stream())
                .map(TcfdSectorImpactResponse::from)
                .toList();

        // 3. Probability-weighted blend.
        ProbabilityWeightedExpectedLoss weighted =
                scenarioService.computeProbabilityWeightedExpectedLoss();

        // 4. Financed emissions for the year + per-asset-class breakdown.
        FinancedEmissionsTotal total = emissionsService.computeTotalForYear(reportingYear, null);
        List<FinancedEmissionsResponse> emissionsRecords = recordRepo
                .findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(orgId, reportingYear)
                .stream()
                .filter(r -> "APPROVED".equals(r.getLifecycleStage())
                        || "PUBLISHED".equals(r.getLifecycleStage()))
                .map(FinancedEmissionsResponse::from)
                .toList();

        // 5. Target progress.
        List<TargetProgress> targetProgress = emissionsService.computeReductionAgainstTarget();

        String generatedAt = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new AnnualClimateReport(
                reportingYear, scenarios, sectorImpacts,
                weighted, total, emissionsRecords, targetProgress,
                generatedAt);
    }
}
