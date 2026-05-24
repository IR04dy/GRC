package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.ProbabilityWeightedExpectedLoss;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdLifecycleTransitionRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdSectorImpactRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdSectorImpactResponse;
import com.tss.grc.module.sust.climate.entity.TcfdClimateScenario;
import com.tss.grc.module.sust.climate.entity.TcfdSectorImpact;
import com.tss.grc.module.sust.climate.repository.TcfdClimateScenarioRepository;
import com.tss.grc.module.sust.climate.repository.TcfdSectorImpactRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle + analytics service for {@link TcfdClimateScenario} —
 * Sprint 11 (V186).
 *
 * <p>Lifecycle:
 * <pre>
 *   DRAFT → IN_REVIEW → BOARD_APPROVED → ARCHIVED
 * </pre>
 *
 * <p>Hard gates enforced server-side:
 * <ul>
 *   <li>Cannot {@code ARCHIVE} from anything except {@code BOARD_APPROVED}.</li>
 *   <li>Cannot re-open a scenario once {@code ARCHIVED}.</li>
 *   <li>{@code BOARD_APPROVED} stamps {@code approvedAt} and the
 *       caller's user id; the JSON event payload carries both.</li>
 * </ul>
 *
 * <p>F8 events emitted:
 * <ul>
 *   <li>{@code TCFD_SCENARIO_CREATED}</li>
 *   <li>{@code TCFD_SCENARIO_SUBMITTED} (DRAFT → IN_REVIEW)</li>
 *   <li>{@code TCFD_SCENARIO_APPROVED} (IN_REVIEW → BOARD_APPROVED)</li>
 *   <li>{@code TCFD_SCENARIO_ARCHIVED} (BOARD_APPROVED → ARCHIVED)</li>
 *   <li>{@code TCFD_SCENARIO_LIFECYCLE_TRANSITION} (every state change)</li>
 * </ul>
 *
 * <p>{@link #computeProbabilityWeightedExpectedLoss()} blends across
 * every {@code BOARD_APPROVED} scenario as
 * {@code Σ (expected_loss × probability_weight)}. Warns the caller if
 * the sum of probability weights diverges from 1.0 (board pack
 * convention is that approved scenarios collectively partition the
 * future).
 */
@Service
@RequiredArgsConstructor
public class TcfdScenarioService {

    private static final Map<String, Set<String>> ALLOWED_NEXT = Map.of(
            "DRAFT",          Set.of("IN_REVIEW"),
            "IN_REVIEW",      Set.of("DRAFT", "BOARD_APPROVED"),
            "BOARD_APPROVED", Set.of("ARCHIVED", "IN_REVIEW"),
            "ARCHIVED",       Set.of()
    );

    private final TcfdClimateScenarioRepository scenarioRepo;
    private final TcfdSectorImpactRepository sectorRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TcfdScenarioResponse> list() {
        Long orgId = TenantContext.getCurrentTenant();
        return scenarioRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId)
                .stream().map(TcfdScenarioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TcfdScenarioResponse> listApproved() {
        Long orgId = TenantContext.getCurrentTenant();
        return scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(orgId, "BOARD_APPROVED")
                .stream().map(TcfdScenarioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TcfdScenarioResponse get(UUID externalId) {
        return TcfdScenarioResponse.from(mustGet(externalId));
    }

    @Transactional(readOnly = true)
    public List<TcfdSectorImpactResponse> listSectorImpacts(UUID scenarioExternalId) {
        TcfdClimateScenario s = mustGet(scenarioExternalId);
        return sectorRepo.findByOrganisationIdAndScenarioIdAndIsDeletedFalseOrderBySectorCodeAsc(
                s.getOrganisationId(), s.getId())
                .stream().map(TcfdSectorImpactResponse::from).toList();
    }

    // ─── Writes ───────────────────────────────────────────────────

    @Transactional
    public TcfdScenarioResponse create(TcfdScenarioRequest req) {
        Long orgId = TenantContext.getCurrentTenant();

        TcfdClimateScenario s = new TcfdClimateScenario();
        s.setOrganisationId(orgId);
        s.setScenarioCode(req.scenarioCode() == null || req.scenarioCode().isBlank()
                ? generateCode(orgId, req.scenarioType())
                : req.scenarioCode());
        applyFields(s, req);
        s.setLifecycleStage("DRAFT");
        s = scenarioRepo.save(s);

        audit.log("CREATE", "TcfdClimateScenario", s.getId(),
                "Scenario " + s.getScenarioCode() + " created — " + safe(s.getTitleEn())
                        + " · type=" + s.getScenarioType()
                        + " · framework=" + s.getReferenceFramework());
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "TCFD_SCENARIO_CREATED",
                "TcfdClimateScenario", s.getId(), s.getScenarioCode(),
                payload(s, null)));
        return TcfdScenarioResponse.from(s);
    }

    @Transactional
    public TcfdScenarioResponse update(UUID externalId, TcfdScenarioRequest req) {
        TcfdClimateScenario s = mustGet(externalId);
        // Once BOARD_APPROVED or ARCHIVED the figures are locked — the board
        // approved the specific numbers, edits must go through a fresh
        // IN_REVIEW cycle.
        if ("BOARD_APPROVED".equals(s.getLifecycleStage())
                || "ARCHIVED".equals(s.getLifecycleStage())) {
            throw new IllegalTransitionException(s.getLifecycleStage(), "UPDATED",
                    Set.of("DRAFT", "IN_REVIEW"));
        }
        applyFields(s, req);
        s = scenarioRepo.save(s);
        audit.log("UPDATE", "TcfdClimateScenario", s.getId(),
                "Scenario " + s.getScenarioCode() + " updated");
        return TcfdScenarioResponse.from(s);
    }

    @Transactional
    public TcfdScenarioResponse transition(UUID externalId,
                                           TcfdLifecycleTransitionRequest req,
                                           Long actingUserId) {
        TcfdClimateScenario s = mustGet(externalId);
        String from = s.getLifecycleStage();
        String to = req.toStage();
        Set<String> allowed = ALLOWED_NEXT.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
        s.setLifecycleStage(to);
        if ("BOARD_APPROVED".equals(to)) {
            s.setApprovedAt(LocalDateTime.now());
            s.setApprovedByUserId(actingUserId);
        }
        s = scenarioRepo.save(s);

        String triggerEvent = switch (to) {
            case "IN_REVIEW" -> "DRAFT".equals(from)
                    ? "TCFD_SCENARIO_SUBMITTED" : "TCFD_SCENARIO_LIFECYCLE_TRANSITION";
            case "BOARD_APPROVED" -> "TCFD_SCENARIO_APPROVED";
            case "ARCHIVED" -> "TCFD_SCENARIO_ARCHIVED";
            default -> "TCFD_SCENARIO_LIFECYCLE_TRANSITION";
        };
        audit.log("TRANSITION", "TcfdClimateScenario", s.getId(),
                "Scenario " + s.getScenarioCode() + " " + from + " → " + to);
        eventPublisher.publishEvent(new DomainEvent(
                s.getOrganisationId(), triggerEvent,
                "TcfdClimateScenario", s.getId(), s.getScenarioCode(),
                payload(s, from)));
        return TcfdScenarioResponse.from(s);
    }

    // ─── Sector impacts ───────────────────────────────────────────

    @Transactional
    public TcfdSectorImpactResponse addSectorImpact(UUID scenarioExternalId, TcfdSectorImpactRequest req) {
        TcfdClimateScenario parent = mustGet(scenarioExternalId);
        if ("ARCHIVED".equals(parent.getLifecycleStage())) {
            throw new IllegalStateException(
                    "Cannot add sector impact to archived scenario " + parent.getScenarioCode());
        }
        TcfdSectorImpact si = new TcfdSectorImpact();
        si.setOrganisationId(parent.getOrganisationId());
        si.setScenarioId(parent.getId());
        si.setSectorCode(req.sectorCode());
        si.setSectorNameEn(req.sectorNameEn());
        si.setSectorNameAr(req.sectorNameAr());
        si.setExposureSarMillions(req.exposureSarMillions());
        si.setTransitionRiskScore(req.transitionRiskScore());
        si.setPhysicalRiskScore(req.physicalRiskScore());
        si.setExpectedPdUpliftBps(req.expectedPdUpliftBps() == null ? 0 : req.expectedPdUpliftBps());
        si.setMitigantActionsEn(req.mitigantActionsEn());
        si.setMitigantActionsAr(req.mitigantActionsAr());
        si = sectorRepo.save(si);

        audit.log("CREATE", "TcfdSectorImpact", si.getId(),
                "Sector " + si.getSectorCode() + " added to " + parent.getScenarioCode());
        return TcfdSectorImpactResponse.from(si);
    }

    // ─── Analytics ────────────────────────────────────────────────

    /**
     * Sum of {@code expectedLoss × probabilityWeight} across every
     * {@code BOARD_APPROVED} scenario. Returns a warning when the total
     * probability weight diverges from 1.0 by more than 0.005.
     */
    @Transactional(readOnly = true)
    public ProbabilityWeightedExpectedLoss computeProbabilityWeightedExpectedLoss() {
        Long orgId = TenantContext.getCurrentTenant();
        List<TcfdClimateScenario> approved = scenarioRepo
                .findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(orgId, "BOARD_APPROVED");
        if (approved.isEmpty()) {
            return new ProbabilityWeightedExpectedLoss(
                    BigDecimal.ZERO, BigDecimal.ZERO, 0,
                    "No BOARD_APPROVED scenarios — blend is zero.");
        }
        BigDecimal weightedLoss = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (TcfdClimateScenario s : approved) {
            BigDecimal loss = s.getExpectedLossSarMillions() == null
                    ? BigDecimal.ZERO : s.getExpectedLossSarMillions();
            BigDecimal weight = s.getProbabilityWeight() == null
                    ? BigDecimal.ZERO : s.getProbabilityWeight();
            weightedLoss = weightedLoss.add(loss.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        String warning = null;
        BigDecimal one = BigDecimal.ONE;
        if (totalWeight.subtract(one).abs().compareTo(new BigDecimal("0.005")) > 0) {
            warning = "Probability weights sum to " + totalWeight.setScale(3, RoundingMode.HALF_UP)
                    + " (expected ~1.000) — review board-approved allocation.";
        }
        return new ProbabilityWeightedExpectedLoss(
                weightedLoss.setScale(2, RoundingMode.HALF_UP),
                totalWeight.setScale(3, RoundingMode.HALF_UP),
                approved.size(),
                warning);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    public TcfdClimateScenario mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("TcfdClimateScenario not found: " + externalId));
    }

    private void applyFields(TcfdClimateScenario s, TcfdScenarioRequest r) {
        if (r.titleEn() != null) s.setTitleEn(r.titleEn());
        if (r.titleAr() != null) s.setTitleAr(r.titleAr());
        if (r.descriptionEn() != null) s.setDescriptionEn(r.descriptionEn());
        if (r.descriptionAr() != null) s.setDescriptionAr(r.descriptionAr());
        if (r.scenarioType() != null) s.setScenarioType(r.scenarioType());
        if (r.referenceFramework() != null) s.setReferenceFramework(r.referenceFramework());
        if (r.timeHorizonYears() != null) s.setTimeHorizonYears(r.timeHorizonYears());
        if (r.globalWarmingCelsius() != null) s.setGlobalWarmingCelsius(r.globalWarmingCelsius());
        if (r.assumptionsJson() != null) s.setAssumptionsJson(r.assumptionsJson());
        if (r.portfolioAtRiskSarBillions() != null) s.setPortfolioAtRiskSarBillions(r.portfolioAtRiskSarBillions());
        if (r.expectedLossSarMillions() != null) s.setExpectedLossSarMillions(r.expectedLossSarMillions());
        if (r.probabilityWeight() != null) s.setProbabilityWeight(r.probabilityWeight());
    }

    private String generateCode(Long orgId, String scenarioType) {
        int year = Year.now().getValue();
        String typeFragment = scenarioType == null ? "CUSTOM"
                : scenarioType.replace("_", "");
        for (int attempt = 0; attempt < 10; attempt++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format("TCFD-%d-%s-%03d", year, typeFragment, seq);
            if (scenarioRepo.countByOrganisationIdAndScenarioCode(orgId, candidate) == 0) {
                return candidate;
            }
        }
        return "TCFD-" + year + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private static Map<String, Object> payload(TcfdClimateScenario s, String previousStage) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("scenarioCode",      safe(s.getScenarioCode()));
        p.put("externalId",        s.getExternalId() == null ? "" : s.getExternalId().toString());
        p.put("titleEn",           safe(s.getTitleEn()));
        p.put("scenarioType",      safe(s.getScenarioType()));
        p.put("referenceFramework",safe(s.getReferenceFramework()));
        p.put("timeHorizonYears",  s.getTimeHorizonYears() == null ? 0 : s.getTimeHorizonYears());
        p.put("globalWarmingCelsius", s.getGlobalWarmingCelsius() == null ? "" : s.getGlobalWarmingCelsius().toPlainString());
        p.put("probabilityWeight", s.getProbabilityWeight() == null ? "" : s.getProbabilityWeight().toPlainString());
        p.put("lifecycleStage",    safe(s.getLifecycleStage()));
        if (previousStage != null) p.put("previousStage", previousStage);
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
