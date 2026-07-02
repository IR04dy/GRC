package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.AnnualClimateReport;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.CapaEntry;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTotal;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.ProbabilityWeightedExpectedLoss;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TargetProgress;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdChecklistEntry;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdSectorImpactResponse;
import com.tss.grc.module.sust.climate.entity.TcfdClimateScenario;
import com.tss.grc.module.sust.climate.repository.FinancedEmissionsRecordRepository;
import com.tss.grc.module.sust.climate.repository.TcfdClimateScenarioRepository;
import com.tss.grc.module.sust.climate.repository.TcfdSectorImpactRepository;
import com.tss.grc.module.sust.evidence.service.SustEvidenceLinkService;
import com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService;
import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Assembles the annual IFRS S2 / TCFD disclosure pack — Sprint 11 (V186),
 * completed under S-G.
 *
 * <p>The pack now carries the FULL disclosure (previously it omitted four
 * sections — the review's "IFRS S2 report is incomplete" finding):
 * <ul>
 *   <li>{@code BOARD_APPROVED} TCFD scenarios + per-scenario sector impacts.</li>
 *   <li>Probability-weighted expected-loss blend.</li>
 *   <li>Financed-emissions roll-up + per-asset-class records + target progress.</li>
 *   <li><b>S-G</b> — the TCFD 4-pillar alignment checklist for the year.</li>
 *   <li><b>S-G</b> — the GHG inventory (scope totals + Scope-3 coverage).</li>
 *   <li><b>S-G</b> — the evidence index (linked items + file names + SHA-256).</li>
 *   <li><b>S-G</b> — the open gaps / corrective-action list.</li>
 * </ul>
 *
 * <p>Missing data degrades to an empty collection / zero aggregate rather than
 * throwing. {@code ClimateReportService} persists + locks a snapshot of this.
 */
@Service
@RequiredArgsConstructor
public class TcfdReportService {

    private final TcfdClimateScenarioRepository scenarioRepo;
    private final TcfdSectorImpactRepository sectorRepo;
    private final FinancedEmissionsRecordRepository recordRepo;
    private final TcfdScenarioService scenarioService;
    private final FinancedEmissionsService emissionsService;
    // ── S-G — the four previously-omitted sections ──
    private final TcfdItemRepository tcfdItemRepo;
    private final GhgEmissionRecordService ghgService;
    private final CorrectiveActionRepository capaRepo;
    private final SustEvidenceLinkService sustEvidenceLinkService;

    @Transactional(readOnly = true)
    public AnnualClimateReport generateAnnualReportData(int reportingYear) {
        Long orgId = TenantContext.getCurrentTenant();
        short year = (short) reportingYear;

        // 1. Board-approved scenarios.
        List<TcfdClimateScenario> approvedScenarios = scenarioRepo
                .findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(orgId, "BOARD_APPROVED");
        List<TcfdScenarioResponse> scenarios = approvedScenarios.stream()
                .map(TcfdScenarioResponse::from).toList();

        // 2. Sector impacts of those scenarios.
        List<TcfdSectorImpactResponse> sectorImpacts = approvedScenarios.stream()
                .flatMap(s -> sectorRepo.findByOrganisationIdAndScenarioIdAndIsDeletedFalseOrderBySectorCodeAsc(
                                orgId, s.getId()).stream())
                .map(TcfdSectorImpactResponse::from)
                .toList();

        // 3. Probability-weighted blend.
        ProbabilityWeightedExpectedLoss weighted = scenarioService.computeProbabilityWeightedExpectedLoss();

        // 4. Financed emissions for the year + per-asset-class breakdown.
        FinancedEmissionsTotal total = emissionsService.computeTotalForYear(reportingYear, null);
        List<FinancedEmissionsResponse> emissionsRecords = recordRepo
                .findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(orgId, reportingYear)
                .stream()
                .filter(r -> "APPROVED".equals(r.getLifecycleStage()) || "PUBLISHED".equals(r.getLifecycleStage()))
                .map(FinancedEmissionsResponse::from)
                .toList();

        // 5. Target progress.
        List<TargetProgress> targetProgress = emissionsService.computeReductionAgainstTarget();

        // 6. S-G — TCFD 4-pillar alignment checklist for the year.
        List<TcfdChecklistEntry> tcfdChecklist = tcfdItemRepo
                .findByOrganisationIdAndReportingYear(orgId, year).stream()
                .map(TcfdReportService::toChecklistEntry)
                .toList();

        // 7. S-G — GHG inventory (scope totals + Scope-3 coverage).
        Map<String, Object> ghgInventory = ghgService.aggregate(year);

        // 8. S-G — evidence index (linked items + file names + SHA-256).
        Map<String, Object> evidenceIndex = sustEvidenceLinkService.buildPackage(null);

        // 9. S-G — open / in-progress gaps + corrective actions.
        List<CapaEntry> gapsCapa = capaRepo
                .findByOrganisationIdAndStatusIn(orgId, List.of("OPEN", "IN_PROGRESS")).stream()
                .map(TcfdReportService::toCapaEntry)
                .toList();

        String generatedAt = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new AnnualClimateReport(
                reportingYear, scenarios, sectorImpacts,
                weighted, total, emissionsRecords, targetProgress,
                tcfdChecklist, ghgInventory, evidenceIndex, gapsCapa,
                generatedAt);
    }

    private static TcfdChecklistEntry toChecklistEntry(TcfdItem t) {
        return new TcfdChecklistEntry(
                t.getPillar(), t.getRecommendationCode(), t.getDisclosureStatus(),
                t.getTitleEn(), t.getTitleAr(), t.getGapDescriptionEn());
    }

    private static CapaEntry toCapaEntry(CorrectiveAction c) {
        return new CapaEntry(
                c.getActionCode(), c.getTitleEn(), c.getStatus(), c.getPriority(),
                c.getSourceType(), c.getSourceRef(),
                c.getTargetDate() == null ? null : c.getTargetDate().toString());
    }
}
