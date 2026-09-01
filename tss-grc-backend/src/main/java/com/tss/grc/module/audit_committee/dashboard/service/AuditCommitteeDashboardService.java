package com.tss.grc.module.audit_committee.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AiSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AttestRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AttestationResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.CapaSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.ClimateSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.ControlSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.FinalizeRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.FindingSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.GenerateSnapshotRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.IncidentSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.LiveDashboardResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.ObligationSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.RecordResolutionRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.RiskSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.RiskTopRow;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.SnapshotResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.SnapshotSummary;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.ThreeLinesSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.VendorBcpSection;
import com.tss.grc.module.audit_committee.dashboard.entity.DashboardAttestation;
import com.tss.grc.module.audit_committee.dashboard.entity.DashboardSnapshot;
import com.tss.grc.module.audit_committee.dashboard.repository.DashboardAttestationRepository;
import com.tss.grc.module.audit_committee.dashboard.repository.DashboardSnapshotRepository;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.repository.MlModelDriftObservationRepository;
import com.tss.grc.module.ai.governance.repository.MlModelRepository;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.ccm.repository.ControlTestDefinitionRepository;
import com.tss.grc.module.ccm.repository.ControlTestRunRepository;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.obligation.entity.Obligation;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import com.tss.grc.module.compliance.obligation.repository.ObligationRepository;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesOverviewDto;
import com.tss.grc.module.governance.threelines.service.ThreeLinesService;
import com.tss.grc.module.incident.entity.Incident;
import com.tss.grc.module.incident.repository.IncidentRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpCampaign;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpAttestationRepository;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpCampaignRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Committee Dashboard service — Sprint 11.
 *
 * <p>Aggregates the live state of every GRC stream and freezes a
 * meeting-time snapshot for the Audit Committee. Does NOT own a new
 * register — it's a curation slice that calls the existing module
 * services / repositories.
 *
 * <p>F7 audit_log + F8 domain-event parity on every mutation. F8
 * events carry the {@code AC_} prefix:
 * <ul>
 *   <li>{@code AC_DASHBOARD_SNAPSHOT_GENERATED}</li>
 *   <li>{@code AC_DASHBOARD_ATTESTED}</li>
 *   <li>{@code AC_RESOLUTION_RECORDED}</li>
 *   <li>{@code AC_DASHBOARD_FINALIZED}</li>
 * </ul>
 *
 * <p>Tamper-evident signature_hash: SHA-256 hex of
 * {@code attestation_text || user_id || ISO-8601(timestamp)}. Any
 * auditor with the row's plaintext fields can reproduce the digest
 * and confirm the row was not rewritten post-meeting.
 *
 * <p>Graceful missing-data handling: every section catches Hibernate
 * / runtime errors from the called-into module repos and returns a
 * zero-filled section rather than blowing up the whole dashboard.
 * Sprint 11 is the first time some of these modules sit side-by-side
 * in production; not every tenant will have every register
 * populated.
 */
@Slf4j
@Service
public class AuditCommitteeDashboardService {

    /** Risks at residual level CRITICAL or HIGH are treated as "above appetite". */
    static final List<String> ABOVE_APPETITE_LEVELS = List.of("CRITICAL", "HIGH");
    static final List<String> RISK_CLOSED_STATUSES = List.of("CLOSED", "CANCELLED");
    // F2-370: FINDING here reads compliance.findings (AuditFinding), which has NO declared state
    // machine, so its closed-set is hand-typed (and whether RESOLVED is terminal is an owner/data
    // question — filed). RISK likewise has no Map<state,edges> machine to derive from. The CAPA
    // closed-set, by contrast, was a hand-typed copy of capa.tasks's own machine and now reads the
    // ONE derived CapaTaskService.TERMINAL (F2-196/F2-336), so a new terminal CAPA state is
    // reflected here and in ThreeLinesService at once.
    static final List<String> FINDING_CLOSED_STATUSES = List.of("CLOSED", "RESOLVED");
    static final List<String> INCIDENT_OPEN_STATUSES = List.of("OPEN", "INVESTIGATING");

    private final DashboardSnapshotRepository snapshotRepo;
    private final DashboardAttestationRepository attestationRepo;
    private final RiskRepository riskRepo;
    private final ControlRepository controlRepo;
    private final AuditFindingRepository findingRepo;
    private final CapaTaskRepository capaRepo;
    private final IncidentRepository incidentRepo;
    private final MlModelRepository mlModelRepo;
    private final MlModelDriftObservationRepository mlDriftRepo;
    private final ClimateRiskOpportunityRepository climateRepo;
    private final GhgEmissionRecordRepository ghgRepo;
    private final ObligationRepository obligationRepo;
    private final ObligationAttestationRepository obligationAttRepo;
    private final VendorBcpCampaignRepository bcpCampaignRepo;
    private final VendorBcpAttestationRepository bcpAttRepo;
    private final ControlTestDefinitionRepository ccmDefRepo;
    private final ControlTestRunRepository ccmRunRepo;
    private final ThreeLinesService threeLinesService;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    private final ObjectMapper jsonMapper;

    public AuditCommitteeDashboardService(
            DashboardSnapshotRepository snapshotRepo,
            DashboardAttestationRepository attestationRepo,
            RiskRepository riskRepo,
            ControlRepository controlRepo,
            AuditFindingRepository findingRepo,
            CapaTaskRepository capaRepo,
            IncidentRepository incidentRepo,
            MlModelRepository mlModelRepo,
            MlModelDriftObservationRepository mlDriftRepo,
            ClimateRiskOpportunityRepository climateRepo,
            GhgEmissionRecordRepository ghgRepo,
            ObligationRepository obligationRepo,
            ObligationAttestationRepository obligationAttRepo,
            VendorBcpCampaignRepository bcpCampaignRepo,
            VendorBcpAttestationRepository bcpAttRepo,
            ControlTestDefinitionRepository ccmDefRepo,
            ControlTestRunRepository ccmRunRepo,
            ThreeLinesService threeLinesService,
            AuditLogService audit,
            ApplicationEventPublisher eventPublisher) {
        this.snapshotRepo = snapshotRepo;
        this.attestationRepo = attestationRepo;
        this.riskRepo = riskRepo;
        this.controlRepo = controlRepo;
        this.findingRepo = findingRepo;
        this.capaRepo = capaRepo;
        this.incidentRepo = incidentRepo;
        this.mlModelRepo = mlModelRepo;
        this.mlDriftRepo = mlDriftRepo;
        this.climateRepo = climateRepo;
        this.ghgRepo = ghgRepo;
        this.obligationRepo = obligationRepo;
        this.obligationAttRepo = obligationAttRepo;
        this.bcpCampaignRepo = bcpCampaignRepo;
        this.bcpAttRepo = bcpAttRepo;
        this.ccmDefRepo = ccmDefRepo;
        this.ccmRunRepo = ccmRunRepo;
        this.threeLinesService = threeLinesService;
        this.audit = audit;
        this.eventPublisher = eventPublisher;
        this.jsonMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    // ─── Live dashboard ───────────────────────────────────────────

    /**
     * Compute the live Audit Committee dashboard for the given tenant.
     *
     * <p>Each section is computed in its own try/catch so missing data
     * in one module does not blank out the whole view.
     */
    @Transactional(readOnly = true)
    public LiveDashboardResponse computeLiveDashboard(Long orgId) {
        return new LiveDashboardResponse(
                LocalDateTime.now(),
                safeRiskSection(orgId),
                safeControlSection(orgId),
                safeFindingSection(orgId),
                safeCapaSection(orgId),
                safeAiSection(orgId),
                safeClimateSection(orgId),
                safeObligationSection(orgId),
                safeIncidentSection(orgId),
                safeVendorBcpSection(orgId),
                safeThreeLinesSection(orgId)
        );
    }

    private RiskSection safeRiskSection(Long orgId) {
        try { return riskSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("Risk section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new RiskSection(0, 0, 0, 0, 0, 0, List.of());
        }
    }

    private RiskSection riskSection(Long orgId) {
        var page = riskRepo.findByOrganisationId(orgId, Pageable.ofSize(1000));
        List<Risk> all = page.getContent().stream()
                .filter(r -> !RISK_CLOSED_STATUSES.contains(r.getStatus()))
                .toList();
        long critical = all.stream().filter(r -> "CRITICAL".equals(r.getRiskLevel())).count();
        long high = all.stream().filter(r -> "HIGH".equals(r.getRiskLevel())).count();
        long medium = all.stream().filter(r -> "MEDIUM".equals(r.getRiskLevel())).count();
        long low = all.stream().filter(r -> "LOW".equals(r.getRiskLevel())).count();
        long aboveAppetite = critical + high;

        // Top 5 — order by residualScore DESC nulls last.
        List<RiskTopRow> top = all.stream()
                .filter(r -> ABOVE_APPETITE_LEVELS.contains(r.getRiskLevel()))
                .sorted(Comparator.comparing(
                        (Risk r) -> r.getResidualScore() == null ? Integer.MIN_VALUE : r.getResidualScore())
                        .reversed())
                .limit(5)
                .map(r -> new RiskTopRow(
                        r.getId(),
                        r.getExternalId() == null ? null : r.getExternalId().toString(),
                        r.getTitleEn(), r.getTitleAr(),
                        r.getRiskLevel(), r.getResidualScore(), r.getStatus()))
                .toList();

        return new RiskSection(all.size(), critical, high, medium, low, aboveAppetite, top);
    }

    private ControlSection safeControlSection(Long orgId) {
        try { return controlSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("Control section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new ControlSection(0, 0, 0, 0.0, 0, 0, 0.0);
        }
    }

    private ControlSection controlSection(Long orgId) {
        long total = safeCount(() -> controlRepo.countByOrganisationId(orgId));
        long effective = safeCount(() -> controlRepo.countByOrganisationIdAndStatus(orgId, "IMPLEMENTED"))
                + safeCount(() -> controlRepo.countByOrganisationIdAndStatus(orgId, "EFFECTIVE"));
        long failing = safeCount(() -> controlRepo.countByOrganisationIdAndStatus(orgId, "FAILING"))
                + safeCount(() -> controlRepo.countByOrganisationIdAndStatus(orgId, "INEFFECTIVE"));
        double effectivenessPct = total == 0 ? 0.0 : (effective * 100.0) / (double) total;

        // CCM rollup — direct repo (avoid TenantContext dependency in the CCM service).
        long ccmTotal = ccmDefRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId).size();
        long ccmEnabled = ccmDefRepo.findByOrganisationIdAndIsEnabledTrueAndIsDeletedFalse(orgId).size();
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long passing = safeCount(() -> ccmRunRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(orgId, "PASS", since));
        long failingCcm = safeCount(() -> ccmRunRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(orgId, "FAIL", since));
        long errorCcm = safeCount(() -> ccmRunRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(orgId, "ERROR", since));
        long timeoutCcm = safeCount(() -> ccmRunRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(orgId, "TIMEOUT", since));
        long total24 = passing + failingCcm + errorCcm + timeoutCcm;
        double passRate = total24 == 0 ? 0.0 : (double) passing / (double) total24;

        return new ControlSection(total, effective, failing, effectivenessPct,
                ccmTotal, ccmEnabled, passRate);
    }

    private FindingSection safeFindingSection(Long orgId) {
        try { return findingSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("Finding section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new FindingSection(0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0);
        }
    }

    private FindingSection findingSection(Long orgId) {
        var page = findingRepo.findByOrganisationId(orgId, Pageable.ofSize(2000));
        List<AuditFinding> all = page.getContent();
        List<AuditFinding> open = all.stream()
                .filter(f -> !FINDING_CLOSED_STATUSES.contains(safe(f.getStatus())))
                .toList();
        long critical = open.stream().filter(f -> "CRITICAL".equalsIgnoreCase(f.getSeverity())).count();
        long high = open.stream().filter(f -> "HIGH".equalsIgnoreCase(f.getSeverity())).count();
        long medium = open.stream().filter(f -> "MEDIUM".equalsIgnoreCase(f.getSeverity())).count();
        long low = open.stream().filter(f -> "LOW".equalsIgnoreCase(f.getSeverity())).count();

        LocalDate today = LocalDate.now();
        long ageBucket0to30 = open.stream()
                .filter(f -> ageDays(f.getCreatedAt() == null ? null : f.getCreatedAt().toLocalDate(), today) <= 30)
                .count();
        long ageBucket30to90 = open.stream()
                .filter(f -> {
                    long d = ageDays(f.getCreatedAt() == null ? null : f.getCreatedAt().toLocalDate(), today);
                    return d > 30 && d <= 90;
                })
                .count();
        long ageBucketOver90 = open.stream()
                .filter(f -> ageDays(f.getCreatedAt() == null ? null : f.getCreatedAt().toLocalDate(), today) > 90)
                .count();

        LocalDateTime quarterStart = LocalDateTime.now().minusMonths(3);
        long closedLastQuarter = all.stream()
                .filter(f -> FINDING_CLOSED_STATUSES.contains(safe(f.getStatus())))
                .filter(f -> f.getUpdatedAt() != null && f.getUpdatedAt().isAfter(quarterStart))
                .count();
        long allLastQuarter = all.stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(quarterStart))
                .count();
        double closureRate = allLastQuarter == 0 ? 0.0
                : (closedLastQuarter * 100.0) / (double) (allLastQuarter + closedLastQuarter);

        return new FindingSection(
                open.size(), critical, high, medium, low,
                ageBucket0to30, ageBucket30to90, ageBucketOver90,
                closedLastQuarter, closureRate);
    }

    private CapaSection safeCapaSection(Long orgId) {
        try { return capaSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("CAPA section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new CapaSection(0, 0, 0, 0.0);
        }
    }

    private CapaSection capaSection(Long orgId) {
        List<CapaTask> all = capaRepo.findByOrganisationIdOrderByCreatedAtDesc(orgId);
        List<CapaTask> open = all.stream()
                .filter(c -> !CapaTaskService.TERMINAL.contains(safe(c.getStatus())))
                .toList();
        LocalDate today = LocalDate.now();
        long overdue = open.stream()
                .filter(c -> c.getTargetDate() != null && c.getTargetDate().isBefore(today))
                .count();
        LocalDate quarterStart = today.minusMonths(3);
        long closedThisQuarter = all.stream()
                .filter(c -> "CLOSED".equals(safe(c.getStatus())))
                .filter(c -> c.getClosedAt() != null && c.getClosedAt().toLocalDate().isAfter(quarterStart))
                .count();
        long onTrack = open.size() - overdue;
        double onTrackPct = open.isEmpty() ? 0.0 : (onTrack * 100.0) / (double) open.size();

        return new CapaSection(open.size(), overdue, closedThisQuarter, onTrackPct);
    }

    private AiSection safeAiSection(Long orgId) {
        try { return aiSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("AI section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new AiSection(0, 0, 0, 0, 0, 0, 0);
        }
    }

    private AiSection aiSection(Long orgId) {
        List<MlModel> all = mlModelRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId);
        long critical = all.stream().filter(m -> "CRITICAL".equals(m.getRiskTier())).count();
        long high = all.stream().filter(m -> "HIGH".equals(m.getRiskTier())).count();
        long medium = all.stream().filter(m -> "MEDIUM".equals(m.getRiskTier())).count();
        long low = all.stream().filter(m -> "LOW".equals(m.getRiskTier())).count();
        long missingFairness = mlModelRepo.findHighRiskModelsWithoutFairnessAudit(orgId).size();
        long unackDrift = mlDriftRepo
                .findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(orgId).size();

        return new AiSection(all.size(), critical, high, medium, low, missingFairness, unackDrift);
    }

    private ClimateSection safeClimateSection(Long orgId) {
        try { return climateSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("Climate section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new ClimateSection(0, 0, 0.0, 0.0, 0.0, null);
        }
    }

    private ClimateSection climateSection(Long orgId) {
        List<ClimateRiskOpportunity> all = climateRepo.findByOrganisationId(orgId);
        long approved = all.stream()
                .filter(c -> "TREATED".equals(c.getStatus()) || "ASSESSED".equals(c.getStatus()))
                .count();
        // Probability-weighted expected loss = sum( (impact/5) * (likelihood/5) * financialImpactSar )
        // Likelihood and impact are 1..5 in this schema, so dividing by 5 gives a 0..1 proxy.
        double pwExpLoss = all.stream()
                .filter(c -> "RISK".equals(c.getKind()))
                .filter(c -> c.getFinancialImpactSar() != null)
                .mapToDouble(c -> {
                    Short lhObj = c.getLikelihood();
                    Short imObj = c.getImpact();
                    int lh = lhObj == null ? 0 : lhObj.intValue();
                    int im = imObj == null ? 0 : imObj.intValue();
                    BigDecimal fi = c.getFinancialImpactSar();
                    return (lh / 5.0) * (im / 5.0) * fi.doubleValue();
                })
                .sum();

        // GHG emissions YoY across all scopes.
        short thisYear = (short) LocalDate.now().getYear();
        short lastYear = (short) (thisYear - 1);
        double thisYearTotal = ghgRepo.findByOrganisationIdAndReportingYear(orgId, thisYear).stream()
                .mapToDouble(r -> r.getEmissionValueTco2e() == null ? 0.0 : r.getEmissionValueTco2e().doubleValue())
                .sum();
        double lastYearTotal = ghgRepo.findByOrganisationIdAndReportingYear(orgId, lastYear).stream()
                .mapToDouble(r -> r.getEmissionValueTco2e() == null ? 0.0 : r.getEmissionValueTco2e().doubleValue())
                .sum();
        Double yoy = lastYearTotal == 0.0 ? null
                : ((thisYearTotal - lastYearTotal) * 100.0) / lastYearTotal;

        return new ClimateSection(all.size(), approved, pwExpLoss,
                thisYearTotal, lastYearTotal, yoy);
    }

    private ObligationSection safeObligationSection(Long orgId) {
        try { return obligationSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("Obligation section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new ObligationSection(0, 0, 0.0, Map.of());
        }
    }

    private ObligationSection obligationSection(Long orgId) {
        List<Obligation> all = obligationRepo.findByOrganisationIdAndIsDeletedFalseOrderByNextDueDateAsc(orgId);
        List<Obligation> active = all.stream()
                .filter(o -> "ACTIVE".equals(o.getLifecycleStage()))
                .toList();
        LocalDate today = LocalDate.now();
        long overdue = active.stream()
                .filter(o -> o.getNextDueDate() != null && o.getNextDueDate().isBefore(today))
                .count();
        Map<String, Long> regBreakdown = new LinkedHashMap<>();
        for (Obligation o : active) {
            regBreakdown.merge(safe(o.getRegulator()), 1L, Long::sum);
        }
        long attested = safeCount(() ->
                obligationAttRepo.countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(orgId, "ATTESTED"))
                + safeCount(() ->
                obligationAttRepo.countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(orgId, "ATTESTED_WITH_EXCEPTIONS"));
        long inProgress = safeCount(() ->
                obligationAttRepo.countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(orgId, "IN_PROGRESS"));
        long dueCount = safeCount(() ->
                obligationAttRepo.countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(orgId, "DUE"));
        long overdueCnt = safeCount(() ->
                obligationAttRepo.countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(orgId, "OVERDUE"));
        long total = attested + inProgress + dueCount + overdueCnt;
        double attestedPct = total == 0 ? 0.0 : (attested * 100.0) / (double) total;

        return new ObligationSection(active.size(), overdue, attestedPct, regBreakdown);
    }

    private IncidentSection safeIncidentSection(Long orgId) {
        try { return incidentSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("Incident section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new IncidentSection(0, 0, 0, 0, 0, null, 0);
        }
    }

    private IncidentSection incidentSection(Long orgId) {
        var page = incidentRepo.findByOrganisationId(orgId, Pageable.ofSize(2000));
        LocalDateTime quarterStart = LocalDateTime.now().minusMonths(3);
        List<Incident> lastQuarter = page.getContent().stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().isAfter(quarterStart))
                .toList();
        long critical = lastQuarter.stream().filter(i -> "CRITICAL".equalsIgnoreCase(i.getSeverity())).count();
        long high = lastQuarter.stream().filter(i -> "HIGH".equalsIgnoreCase(i.getSeverity())).count();
        long medium = lastQuarter.stream().filter(i -> "MEDIUM".equalsIgnoreCase(i.getSeverity())).count();
        long low = lastQuarter.stream().filter(i -> "LOW".equalsIgnoreCase(i.getSeverity())).count();

        List<Long> mttrHours = new ArrayList<>();
        for (Incident i : lastQuarter) {
            if (i.getDiscoveryDate() != null && i.getResolutionDate() != null) {
                long h = ChronoUnit.HOURS.between(
                        i.getDiscoveryDate().atStartOfDay(),
                        i.getResolutionDate().atStartOfDay());
                if (h >= 0) mttrHours.add(h);
            }
        }
        Double mttrAvg = mttrHours.isEmpty() ? null
                : mttrHours.stream().mapToLong(Long::longValue).average().orElse(0.0);

        // Any of the 5 Saudi-regulator notification columns set = "notifiable"
        long notifiable = lastQuarter.stream()
                .filter(i -> i.getSamaNotifiedAt() != null
                        || i.getNcaHeyNotifiedAt() != null
                        || i.getDataOfficeNotifiedAt() != null
                        || i.getCitcNotifiedAt() != null
                        || i.getHrsdNotifiedAt() != null)
                .count();

        return new IncidentSection(
                lastQuarter.size(), critical, high, medium, low,
                mttrAvg, notifiable);
    }

    private VendorBcpSection safeVendorBcpSection(Long orgId) {
        try { return vendorBcpSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("Vendor BCP section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new VendorBcpSection(null, 0, 0, 0.0, 0);
        }
    }

    private VendorBcpSection vendorBcpSection(Long orgId) {
        List<VendorBcpCampaign> campaigns = bcpCampaignRepo
                .findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId);
        if (campaigns.isEmpty()) {
            return new VendorBcpSection(null, 0, 0, 0.0, 0);
        }
        VendorBcpCampaign latest = campaigns.get(0);
        List<VendorBcpAttestation> rows = bcpAttRepo
                .findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByCreatedAtAsc(orgId, latest.getId());
        long invited = rows.size();
        long submitted = rows.stream()
                .filter(a -> "SUBMITTED".equals(a.getStatus())
                        || "ACCEPTED".equals(a.getStatus())
                        || "REJECTED".equals(a.getStatus()))
                .count();
        double rate = invited == 0 ? 0.0 : (submitted * 100.0) / (double) invited;
        long belowRto = rows.stream()
                .filter(a -> Boolean.TRUE.equals(a.getRtoBreach()))
                .count();
        return new VendorBcpSection(latest.getCampaignCode(), invited, submitted, rate, belowRto);
    }

    private ThreeLinesSection safeThreeLinesSection(Long orgId) {
        try { return threeLinesSection(orgId); }
        catch (RuntimeException ex) {
            log.warn("Three Lines section failed for org {} — returning zeros: {}", orgId, ex.getMessage());
            return new ThreeLinesSection(0, 0, 0, 0);
        }
    }

    private ThreeLinesSection threeLinesSection(Long orgId) {
        // ThreeLinesService reads from TenantContext directly; ensure tenant is set
        // for the call (caller usually set it but be defensive).
        Long previous = TenantContext.getCurrentTenant();
        boolean weSet = false;
        if (previous == null) {
            TenantContext.setCurrentTenant(orgId);
            weSet = true;
        }
        try {
            ThreeLinesOverviewDto overview = threeLinesService.overview();
            long l1 = 0, l2 = 0, l3 = 0, board = 0;
            for (ThreeLinesOverviewDto.BandCounts band : overview.bands()) {
                long count = band.risksOpen() + band.findingsOpen() + band.capaOpen()
                        + band.policiesPendingApproval() + band.dsrCasesOpen();
                switch (band.lodBand()) {
                    case "LINE_1" -> l1 = count;
                    case "LINE_2" -> l2 = count;
                    case "LINE_3" -> l3 = count;
                    case "BOARD" -> board = band.approvalsPending();
                    default -> {
                    }
                }
            }
            return new ThreeLinesSection(l1, l2, l3, board);
        } finally {
            if (weSet) TenantContext.clear();
        }
    }

    // ─── Mutations ────────────────────────────────────────────────

    /**
     * Freeze the current live dashboard as a snapshot tied to a
     * specific meeting date / type. Emits F7 audit + F8
     * {@code AC_DASHBOARD_SNAPSHOT_GENERATED}.
     */
    @Transactional
    public SnapshotResponse generateSnapshot(GenerateSnapshotRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        Long userId = currentUserId();

        LiveDashboardResponse live = computeLiveDashboard(orgId);
        String json = serialiseDashboard(live);

        String code = (req.snapshotCode() == null || req.snapshotCode().isBlank())
                ? generateSnapshotCode(orgId, req.meetingDate(), req.meetingType())
                : req.snapshotCode();

        if (snapshotRepo.existsByOrganisationIdAndSnapshotCodeAndIsDeletedFalse(orgId, code)) {
            // append a short uniqueness suffix rather than throw — the caller
            // is operating against live data and "two snapshots in a day" is
            // a legitimate workflow (morning + post-meeting refinement).
            code = code + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }

        DashboardSnapshot s = new DashboardSnapshot();
        s.setOrganisationId(orgId);
        s.setSnapshotCode(code);
        s.setMeetingDate(req.meetingDate());
        s.setMeetingType(req.meetingType());
        s.setGeneratedAt(LocalDateTime.now());
        s.setGeneratedByUserId(userId);
        s.setKpisJson(json);
        s.setIsFinalized(Boolean.FALSE);
        s = snapshotRepo.save(s);

        audit.log("AC_DASHBOARD_SNAPSHOT_GENERATED", "DashboardSnapshot", s.getId(),
                "Audit Committee snapshot generated: " + s.getSnapshotCode()
                        + " · meeting=" + s.getMeetingDate() + " · type=" + s.getMeetingType());
        eventPublisher.publishEvent(DomainEvent.of(
                orgId, "AC_DASHBOARD_SNAPSHOT_GENERATED",
                "DashboardSnapshot", s.getId(),
                Map.of(
                        "snapshotCode", s.getSnapshotCode(),
                        "meetingDate", String.valueOf(s.getMeetingDate()),
                        "meetingType", s.getMeetingType())));

        return SnapshotResponse.from(s, List.of());
    }

    /**
     * Record a Committee member's signed attestation. Computes the
     * SHA-256 signature_hash deterministically.
     */
    @Transactional
    public AttestationResponse attestSnapshot(UUID snapshotExternalId, AttestRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        Long userId = currentUserId();
        DashboardSnapshot s = mustGetSnapshot(orgId, snapshotExternalId);
        if (Boolean.TRUE.equals(s.getIsFinalized())) {
            throw new IllegalStateException("Snapshot " + s.getSnapshotCode()
                    + " is finalized — no further attestations allowed.");
        }

        DashboardAttestation a = new DashboardAttestation();
        a.setOrganisationId(orgId);
        a.setSnapshotId(s.getId());
        a.setAttestedByUserId(userId);
        a.setAttestedRole(req.attestedRole());
        a.setAttestationTextEn(req.attestationTextEn());
        a.setAttestationTextAr(req.attestationTextAr());
        LocalDateTime now = LocalDateTime.now();
        a.setAttestedAt(now);
        // Hash composes: text-en (or empty) + "|" + text-ar (or empty) + "|" + userId + "|" + ISO timestamp.
        String composed = nullToEmpty(req.attestationTextEn())
                + "|" + nullToEmpty(req.attestationTextAr())
                + "|" + userId
                + "|" + now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        a.setSignatureHash(sha256Hex(composed));
        a = attestationRepo.save(a);

        audit.log("AC_DASHBOARD_ATTESTED", "DashboardSnapshot", s.getId(),
                "Audit Committee attestation: " + s.getSnapshotCode()
                        + " · role=" + req.attestedRole()
                        + " · user=" + userId
                        + " · hash=" + truncate(a.getSignatureHash(), 12));
        eventPublisher.publishEvent(DomainEvent.of(
                orgId, "AC_DASHBOARD_ATTESTED",
                "DashboardAttestation", a.getId(),
                Map.of(
                        "snapshotCode", s.getSnapshotCode(),
                        "attestedRole", req.attestedRole(),
                        "signatureHash", nullToEmpty(a.getSignatureHash()))));

        return AttestationResponse.from(a);
    }

    /**
     * Append a captured resolution to the snapshot's
     * {@code committee_resolutions} JSON array.
     */
    @Transactional
    public SnapshotResponse recordResolution(UUID snapshotExternalId, RecordResolutionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        Long userId = currentUserId();
        DashboardSnapshot s = mustGetSnapshot(orgId, snapshotExternalId);
        if (Boolean.TRUE.equals(s.getIsFinalized())) {
            throw new IllegalStateException("Snapshot " + s.getSnapshotCode()
                    + " is finalized — no further resolutions allowed.");
        }

        ArrayNode arr;
        try {
            if (s.getCommitteeResolutions() == null || s.getCommitteeResolutions().isBlank()) {
                arr = jsonMapper.createArrayNode();
            } else {
                arr = (ArrayNode) jsonMapper.readTree(s.getCommitteeResolutions());
            }
        } catch (Exception parseEx) {
            log.warn("Could not parse existing resolutions for {} — starting fresh: {}",
                    s.getSnapshotCode(), parseEx.getMessage());
            arr = jsonMapper.createArrayNode();
        }
        ObjectNode obj = jsonMapper.createObjectNode();
        obj.put("id", UUID.randomUUID().toString());
        obj.put("recordedAt", LocalDateTime.now().toString());
        obj.put("recordedByUserId", userId);
        obj.put("textEn", nullToEmpty(req.textEn()));
        obj.put("textAr", nullToEmpty(req.textAr()));
        arr.add(obj);
        try {
            s.setCommitteeResolutions(jsonMapper.writeValueAsString(arr));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise resolutions", ex);
        }
        s = snapshotRepo.save(s);

        audit.log("AC_RESOLUTION_RECORDED", "DashboardSnapshot", s.getId(),
                "Audit Committee resolution recorded against " + s.getSnapshotCode()
                        + " · resolution_count=" + arr.size());
        eventPublisher.publishEvent(DomainEvent.of(
                orgId, "AC_RESOLUTION_RECORDED",
                "DashboardSnapshot", s.getId(),
                Map.of(
                        "snapshotCode", s.getSnapshotCode(),
                        "resolutionCount", arr.size())));

        return SnapshotResponse.from(s, listAttestations(orgId, s.getId()));
    }

    /**
     * Finalize — close the snapshot for further edits and capture the
     * Chair's executive summary.
     */
    @Transactional
    public SnapshotResponse finalizeSnapshot(UUID snapshotExternalId, FinalizeRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        Long userId = currentUserId();
        DashboardSnapshot s = mustGetSnapshot(orgId, snapshotExternalId);
        if (Boolean.TRUE.equals(s.getIsFinalized())) {
            // Idempotent — return the already-finalized state.
            return SnapshotResponse.from(s, listAttestations(orgId, s.getId()));
        }

        s.setExecutiveSummaryEn(req.executiveSummaryEn());
        s.setExecutiveSummaryAr(req.executiveSummaryAr());
        s.setIsFinalized(Boolean.TRUE);
        s.setFinalizedAt(LocalDateTime.now());
        s.setFinalizedByUserId(userId);
        s = snapshotRepo.save(s);

        audit.log("AC_DASHBOARD_FINALIZED", "DashboardSnapshot", s.getId(),
                "Audit Committee snapshot finalized: " + s.getSnapshotCode()
                        + " · by user=" + userId);
        eventPublisher.publishEvent(DomainEvent.of(
                orgId, "AC_DASHBOARD_FINALIZED",
                "DashboardSnapshot", s.getId(),
                Map.of(
                        "snapshotCode", s.getSnapshotCode(),
                        "finalizedByUserId", userId)));

        return SnapshotResponse.from(s, listAttestations(orgId, s.getId()));
    }

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SnapshotSummary> listSnapshots() {
        Long orgId = TenantContext.requireOrganisationId();
        List<DashboardSnapshot> all =
                snapshotRepo.findByOrganisationIdAndIsDeletedFalseOrderByMeetingDateDesc(orgId);
        List<SnapshotSummary> out = new ArrayList<>(all.size());
        for (DashboardSnapshot s : all) {
            long count = attestationRepo.countByOrganisationIdAndSnapshotIdAndIsDeletedFalse(orgId, s.getId());
            out.add(SnapshotSummary.from(s, count));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SnapshotResponse getSnapshot(UUID externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        DashboardSnapshot s = mustGetSnapshot(orgId, externalId);
        return SnapshotResponse.from(s, listAttestations(orgId, s.getId()));
    }

    private List<DashboardAttestation> listAttestations(Long orgId, Long snapshotId) {
        return attestationRepo.findByOrganisationIdAndSnapshotIdAndIsDeletedFalseOrderByAttestedAtAsc(
                orgId, snapshotId);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    DashboardSnapshot mustGetSnapshot(Long orgId, UUID externalId) {
        return snapshotRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("DashboardSnapshot not found: " + externalId));
    }

    private String generateSnapshotCode(Long orgId, LocalDate meetingDate, String meetingType) {
        int year = meetingDate.getYear();
        int month = meetingDate.getMonthValue();
        String quarter = "Q" + (((month - 1) / 3) + 1);
        String typeSuffix = switch (meetingType) {
            case "QUARTERLY" -> "MEETING";
            case "ANNUAL" -> "ANNUAL";
            case "AD_HOC" -> "ADHOC";
            case "OFFCYCLE" -> "OFFCYCLE";
            default -> "MEETING";
        };
        return String.format(Locale.ROOT, "AC-DASH-%d-%s-%s", year, quarter, typeSuffix);
    }

    /**
     * Visible for testing. SHA-256 hex of the supplied composed string.
     */
    static String sha256Hex(String composed) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((composed == null ? "" : composed).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    String serialiseDashboard(LiveDashboardResponse live) {
        try {
            return jsonMapper.writeValueAsString(live);
        } catch (JsonProcessingException e) {
            log.error("Could not serialise dashboard JSON — falling back to empty payload", e);
            return "{}";
        }
    }

    private long ageDays(LocalDate from, LocalDate to) {
        if (from == null || to == null) return 0L;
        return ChronoUnit.DAYS.between(from, to);
    }

    private static String safe(String v) { return v == null ? "" : v; }
    private static String nullToEmpty(String v) { return v == null ? "" : v; }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static long safeCount(java.util.function.LongSupplier sup) {
        try { return sup.getAsLong(); } catch (RuntimeException ex) { return 0L; }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return 0L;
        Object principal = auth.getPrincipal();
        if (principal instanceof com.tss.grc.security.GrcUserDetails ud) {
            return ud.getUserId() == null ? 0L : ud.getUserId();
        }
        return 0L;
    }

    // ─── Test seam — used only by unit tests to inject a fixed user id ───
    Long currentUserIdForTest() { return currentUserId(); }

    /** Suppress unused-warning for the field — referenced inline above. */
    @SuppressWarnings("unused")
    private void unused() {
        Collections.emptyList();
        new HashMap<String, Object>();
    }
}
