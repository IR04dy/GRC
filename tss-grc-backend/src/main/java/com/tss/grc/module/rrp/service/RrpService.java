package com.tss.grc.module.rrp.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateCriticalFunctionRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateIndicatorRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateOptionRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.CriticalFunctionResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.CycleResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.DashboardResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.EventResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.IndicatorResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.OptionResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.RecordIndicatorMeasurementRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.SubmitCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.TransitionCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.UpdateCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.UpdateOptionRequest;
import com.tss.grc.module.rrp.entity.RrpCriticalFunction;
import com.tss.grc.module.rrp.entity.RrpCycle;
import com.tss.grc.module.rrp.entity.RrpEvent;
import com.tss.grc.module.rrp.entity.RrpIndicator;
import com.tss.grc.module.rrp.entity.RrpRecoveryOption;
import com.tss.grc.module.rrp.repository.RrpCriticalFunctionRepository;
import com.tss.grc.module.rrp.repository.RrpCycleRepository;
import com.tss.grc.module.rrp.repository.RrpEventRepository;
import com.tss.grc.module.rrp.repository.RrpIndicatorRepository;
import com.tss.grc.module.rrp.repository.RrpRecoveryOptionRepository;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recovery &amp; Resolution Plan service — Sprint 17 PR #1.
 *
 * <p>Cycle state machine:
 * <pre>
 *   DRAFT → IN_PREPARATION → BOARD_REVIEW → APPROVED →
 *   SUBMITTED → ACKNOWLEDGED → CLOSED
 * </pre>
 *
 * <p>F7+F8 trigger codes:
 * <ul>
 *   <li>RRP_CYCLE_OPENED / TRANSITIONED / SUBMITTED / CLOSED</li>
 *   <li>RRP_INDICATOR_THRESHOLD_BREACHED — live, year-round</li>
 *   <li>RRP_OPTION_CAPACITY_CHANGED</li>
 *   <li>RRP_CRITICAL_FUNCTION_ADDED</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RrpService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "DRAFT",          Set.of("IN_PREPARATION"),
            "IN_PREPARATION", Set.of("BOARD_REVIEW"),
            "BOARD_REVIEW",   Set.of("APPROVED", "IN_PREPARATION"),
            "APPROVED",       Set.of("IN_PREPARATION"),
            "SUBMITTED",      Set.of("ACKNOWLEDGED"),
            "ACKNOWLEDGED",   Set.of("CLOSED"),
            "CLOSED",         Set.of());

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "DRAFT", "IN_PREPARATION", "BOARD_REVIEW", "APPROVED");

    private final RrpCycleRepository cycleRepo;
    private final RrpRecoveryOptionRepository optionRepo;
    private final RrpCriticalFunctionRepository cfRepo;
    private final RrpIndicatorRepository indRepo;
    private final RrpEventRepository eventRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Cycle writes ──────────────────────────────────────────────

    @Transactional
    public CycleResponse createCycle(CreateCycleRequest req) {
        RrpCycle c = new RrpCycle();
        c.setOrganisationId(org());
        c.setReference("RRP-FY" + req.getFiscalYear() + "-"
                + String.format(Locale.ROOT, "%03d", nextOrdinal(req.getFiscalYear())));
        c.setFiscalYear(req.getFiscalYear());
        c.setTitleEn(req.getTitleEn());
        c.setTitleAr(req.getTitleAr());
        c.setStatus("DRAFT");
        c.setSubmissionDueDate(req.getSubmissionDueDate());
        c.setOwnerUserId(req.getOwnerUserId());
        c.setSponsorUserId(req.getSponsorUserId());

        RrpCycle saved = cycleRepo.save(c);
        audit.log("RRP_CYCLE_OPENED", "RrpCycle", saved.getId(),
                "RRP cycle " + saved.getReference() + " opened for FY"
                        + saved.getFiscalYear());
        emit("RRP_CYCLE_OPENED", "RrpCycle", saved.getId(), saved.getReference(),
                Map.of("fiscalYear", String.valueOf(saved.getFiscalYear()),
                        "reference", saved.getReference()));
        timeline(saved.getId(), "STATUS_CHANGED", "DRAFT", null, null,
                "Cycle opened for FY" + saved.getFiscalYear(),
                "افتتاح دورة الخطة للسنة المالية " + saved.getFiscalYear());
        return toCycle(saved);
    }

    @Transactional
    public CycleResponse updateCycle(Long id, UpdateCycleRequest req) {
        RrpCycle c = loadCycle(id);
        if (req.getTitleEn() != null) c.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null) c.setTitleAr(req.getTitleAr());
        if (req.getSubmissionDueDate() != null) c.setSubmissionDueDate(req.getSubmissionDueDate());
        if (req.getOwnerUserId() != null) c.setOwnerUserId(req.getOwnerUserId());
        if (req.getSponsorUserId() != null) c.setSponsorUserId(req.getSponsorUserId());
        if (req.getExecutiveSummaryEn() != null) c.setExecutiveSummaryEn(req.getExecutiveSummaryEn());
        if (req.getExecutiveSummaryAr() != null) c.setExecutiveSummaryAr(req.getExecutiveSummaryAr());
        if (req.getFilingArtifactReference() != null) c.setFilingArtifactReference(req.getFilingArtifactReference());
        if (req.getBoardApprovalDate() != null) c.setBoardApprovalDate(req.getBoardApprovalDate());
        if (req.getBoardResolutionReference() != null) c.setBoardResolutionReference(req.getBoardResolutionReference());
        RrpCycle saved = cycleRepo.save(c);
        audit.log("RRP_CYCLE_UPDATED", "RrpCycle", saved.getId(),
                "Cycle " + saved.getReference() + " metadata updated");
        return toCycle(saved);
    }

    @Transactional
    public CycleResponse transitionCycle(Long id, TransitionCycleRequest req) {
        RrpCycle c = loadCycle(id);
        String from = c.getStatus();
        String to = req.getToStatus();
        if (Set.of("SUBMITTED").contains(to)) {
            throw new IllegalStateException(
                    "Use the dedicated /submit endpoint to lodge with SAMA");
        }
        Set<String> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal transition " + from + " → " + to + " (allowed: " + allowed + ")");
        }
        c.setStatus(to);
        if ("CLOSED".equals(to)) c.setClosedAt(LocalDateTime.now());
        RrpCycle saved = cycleRepo.save(c);

        String trigger = "CLOSED".equals(to) ? "RRP_CYCLE_CLOSED" : "RRP_CYCLE_TRANSITIONED";
        audit.log(trigger, "RrpCycle", saved.getId(),
                "Cycle " + saved.getReference() + " " + from + " → " + to);
        emit(trigger, "RrpCycle", saved.getId(), saved.getReference(),
                Map.of("from", from, "to", to,
                        "reference", saved.getReference(),
                        "fiscalYear", String.valueOf(saved.getFiscalYear())));
        timeline(saved.getId(), "STATUS_CHANGED", to, null, null,
                req.getNoteEn(), req.getNoteAr());
        return toCycle(saved);
    }

    @Transactional
    public CycleResponse submitCycle(Long id, SubmitCycleRequest req) {
        RrpCycle c = loadCycle(id);
        if (!"APPROVED".equals(c.getStatus())) {
            throw new IllegalStateException(
                    "Cycle must be APPROVED to submit; current=" + c.getStatus());
        }
        c.setStatus("SUBMITTED");
        c.setSubmittedAt(LocalDateTime.now());
        c.setRegulatorAckReference(req.getRegulatorAckReference());
        if (req.getFilingArtifactReference() != null)
            c.setFilingArtifactReference(req.getFilingArtifactReference());
        RrpCycle saved = cycleRepo.save(c);

        audit.log("RRP_CYCLE_SUBMITTED", "RrpCycle", saved.getId(),
                "Cycle " + saved.getReference() + " lodged with SAMA, ack "
                        + saved.getRegulatorAckReference());
        emit("RRP_CYCLE_SUBMITTED", "RrpCycle", saved.getId(), saved.getReference(),
                Map.of("reference", saved.getReference(),
                        "fiscalYear", String.valueOf(saved.getFiscalYear()),
                        "regulatorAckReference", saved.getRegulatorAckReference()));
        timeline(saved.getId(), "SUBMITTED", "SUBMITTED", null, null,
                "Lodged with SAMA — ack ref " + saved.getRegulatorAckReference(),
                "إيداع لدى ساما — إقرار الاستلام " + saved.getRegulatorAckReference());
        return toCycle(saved);
    }

    @Transactional
    public CycleResponse acknowledgeCycle(Long id) {
        RrpCycle c = loadCycle(id);
        if (!"SUBMITTED".equals(c.getStatus())) {
            throw new IllegalStateException(
                    "Cycle must be SUBMITTED to acknowledge; current=" + c.getStatus());
        }
        c.setStatus("ACKNOWLEDGED");
        c.setAcknowledgedAt(LocalDateTime.now());
        RrpCycle saved = cycleRepo.save(c);
        audit.log("RRP_CYCLE_TRANSITIONED", "RrpCycle", saved.getId(),
                "Cycle " + saved.getReference() + " SUBMITTED → ACKNOWLEDGED");
        emit("RRP_CYCLE_TRANSITIONED", "RrpCycle", saved.getId(), saved.getReference(),
                Map.of("from", "SUBMITTED", "to", "ACKNOWLEDGED",
                        "reference", saved.getReference()));
        timeline(saved.getId(), "ACK_RECEIVED", "ACKNOWLEDGED", null, null,
                "SAMA acknowledgment received",
                "تم استلام إقرار ساما");
        return toCycle(saved);
    }

    // ─── Recovery options ───────────────────────────────────────────

    @Transactional
    public OptionResponse addOption(Long cycleId, CreateOptionRequest req) {
        RrpCycle c = loadCycle(cycleId);
        RrpRecoveryOption o = new RrpRecoveryOption();
        o.setOrganisationId(org());
        o.setCycleId(c.getId());
        o.setOptionCode(req.getOptionCode());
        o.setCategory(req.getCategory());
        o.setTitleEn(req.getTitleEn());
        o.setTitleAr(req.getTitleAr());
        o.setDescriptionEn(req.getDescriptionEn());
        o.setDescriptionAr(req.getDescriptionAr());
        o.setCapacitySarM(req.getCapacitySarM());
        o.setTimeToExecuteDays(req.getTimeToExecuteDays());
        o.setFeasibility(req.getFeasibility());
        o.setImpactEn(req.getImpactEn());
        o.setImpactAr(req.getImpactAr());
        o.setPreconditionsEn(req.getPreconditionsEn());
        o.setPreconditionsAr(req.getPreconditionsAr());
        o.setLimitationsEn(req.getLimitationsEn());
        o.setLimitationsAr(req.getLimitationsAr());
        o.setAccountableRole(req.getAccountableRole());

        RrpRecoveryOption saved = optionRepo.save(o);
        recountCycle(c);

        audit.log("RRP_OPTION_ADDED", "RrpRecoveryOption", saved.getId(),
                "Option " + saved.getOptionCode() + " (" + saved.getCategory()
                        + ", " + saved.getCapacitySarM() + " SAR M)");
        emit("RRP_OPTION_CAPACITY_CHANGED", "RrpRecoveryOption",
                saved.getId(), saved.getOptionCode(),
                Map.of("cycleId", String.valueOf(c.getId()),
                        "optionCode", saved.getOptionCode(),
                        "category", saved.getCategory(),
                        "capacitySarM", String.valueOf(saved.getCapacitySarM()),
                        "feasibility", saved.getFeasibility()));
        timeline(c.getId(), "OPTION_ADDED", null, saved.getId(), null,
                "Recovery option " + saved.getOptionCode() + " added: " + saved.getTitleEn(),
                "إضافة خيار استعادة " + saved.getOptionCode() + ": " + saved.getTitleAr());
        return toOption(saved);
    }

    @Transactional
    public OptionResponse updateOption(Long optionId, UpdateOptionRequest req) {
        RrpRecoveryOption o = optionRepo.findByIdAndOrganisationId(optionId, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Recovery option " + optionId + " not found"));
        long prevCapacity = o.getCapacitySarM();
        if (req.getCategory() != null) o.setCategory(req.getCategory());
        if (req.getTitleEn() != null) o.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null) o.setTitleAr(req.getTitleAr());
        if (req.getDescriptionEn() != null) o.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null) o.setDescriptionAr(req.getDescriptionAr());
        if (req.getCapacitySarM() != null) o.setCapacitySarM(req.getCapacitySarM());
        if (req.getTimeToExecuteDays() != null) o.setTimeToExecuteDays(req.getTimeToExecuteDays());
        if (req.getFeasibility() != null) o.setFeasibility(req.getFeasibility());
        if (req.getImpactEn() != null) o.setImpactEn(req.getImpactEn());
        if (req.getImpactAr() != null) o.setImpactAr(req.getImpactAr());
        if (req.getPreconditionsEn() != null) o.setPreconditionsEn(req.getPreconditionsEn());
        if (req.getPreconditionsAr() != null) o.setPreconditionsAr(req.getPreconditionsAr());
        if (req.getLimitationsEn() != null) o.setLimitationsEn(req.getLimitationsEn());
        if (req.getLimitationsAr() != null) o.setLimitationsAr(req.getLimitationsAr());
        if (req.getAccountableRole() != null) o.setAccountableRole(req.getAccountableRole());
        RrpRecoveryOption saved = optionRepo.save(o);

        if (saved.getCapacitySarM() != prevCapacity) {
            RrpCycle c = loadCycle(saved.getCycleId());
            recountCycle(c);
            audit.log("RRP_OPTION_CAPACITY_CHANGED", "RrpRecoveryOption",
                    saved.getId(),
                    "Option " + saved.getOptionCode() + " capacity "
                            + prevCapacity + " → " + saved.getCapacitySarM() + " SAR M");
            emit("RRP_OPTION_CAPACITY_CHANGED", "RrpRecoveryOption",
                    saved.getId(), saved.getOptionCode(),
                    Map.of("cycleId", String.valueOf(c.getId()),
                            "optionCode", saved.getOptionCode(),
                            "previousCapacitySarM", String.valueOf(prevCapacity),
                            "capacitySarM", String.valueOf(saved.getCapacitySarM())));
            timeline(c.getId(), "OPTION_REASSESSED", null, saved.getId(), null,
                    "Capacity " + prevCapacity + " → " + saved.getCapacitySarM() + " SAR M",
                    "تعديل الطاقة من " + prevCapacity + " إلى " + saved.getCapacitySarM() + " مليون ريال");
        }
        return toOption(saved);
    }

    // ─── Critical functions ─────────────────────────────────────────

    @Transactional
    public CriticalFunctionResponse addCriticalFunction(Long cycleId,
                                                         CreateCriticalFunctionRequest req) {
        RrpCycle c = loadCycle(cycleId);
        RrpCriticalFunction cf = new RrpCriticalFunction();
        cf.setOrganisationId(org());
        cf.setCycleId(c.getId());
        cf.setFunctionCode(req.getFunctionCode());
        cf.setTitleEn(req.getTitleEn());
        cf.setTitleAr(req.getTitleAr());
        cf.setDescriptionEn(req.getDescriptionEn());
        cf.setDescriptionAr(req.getDescriptionAr());
        cf.setMateriality(req.getMateriality());
        cf.setMaxTolerableDowntimeHours(req.getMaxTolerableDowntimeHours());
        cf.setCustomerCountEstimate(req.getCustomerCountEstimate());
        cf.setDailyVolumeEstimate(req.getDailyVolumeEstimate());
        cf.setCriticalProviderDependenciesEn(req.getCriticalProviderDependenciesEn());
        cf.setCriticalProviderDependenciesAr(req.getCriticalProviderDependenciesAr());
        cf.setContinuityStrategyEn(req.getContinuityStrategyEn());
        cf.setContinuityStrategyAr(req.getContinuityStrategyAr());
        cf.setOwnerRole(req.getOwnerRole());
        RrpCriticalFunction saved = cfRepo.save(cf);
        recountCycle(c);
        audit.log("RRP_CRITICAL_FUNCTION_ADDED", "RrpCriticalFunction",
                saved.getId(),
                "Critical function " + saved.getFunctionCode() + " ("
                        + saved.getMateriality() + ") added");
        emit("RRP_CRITICAL_FUNCTION_ADDED", "RrpCriticalFunction",
                saved.getId(), saved.getFunctionCode(),
                Map.of("cycleId", String.valueOf(c.getId()),
                        "functionCode", saved.getFunctionCode(),
                        "materiality", saved.getMateriality()));
        timeline(c.getId(), "CRITICAL_FUNCTION_ADDED", null, null, saved.getId(),
                "Critical function " + saved.getFunctionCode() + ": " + saved.getTitleEn(),
                "إضافة وظيفة حيوية " + saved.getFunctionCode() + ": " + saved.getTitleAr());
        return toCriticalFunction(saved);
    }

    // ─── Indicators ────────────────────────────────────────────────

    @Transactional
    public IndicatorResponse addIndicator(Long cycleId, CreateIndicatorRequest req) {
        validateThresholdOrder(req.getDirection(), req.getGreenThreshold(), req.getAmberThreshold());
        RrpCycle c = loadCycle(cycleId);
        RrpIndicator i = new RrpIndicator();
        i.setOrganisationId(org());
        i.setCycleId(c.getId());
        i.setIndicatorCode(req.getIndicatorCode());
        i.setCategory(req.getCategory());
        i.setTitleEn(req.getTitleEn());
        i.setTitleAr(req.getTitleAr());
        i.setDescriptionEn(req.getDescriptionEn());
        i.setDescriptionAr(req.getDescriptionAr());
        i.setUnit(req.getUnit());
        i.setDirection(req.getDirection());
        i.setGreenThreshold(req.getGreenThreshold());
        i.setAmberThreshold(req.getAmberThreshold());
        i.setCurrentValue(req.getCurrentValue());
        i.setCurrentBand(computeBand(req.getDirection(),
                req.getGreenThreshold(), req.getAmberThreshold(), req.getCurrentValue()));
        i.setMeasuredAt(req.getMeasuredAt());
        i.setDataSource(req.getDataSource());
        i.setOwnerRole(req.getOwnerRole());
        RrpIndicator saved = indRepo.save(i);
        recountCycle(c);
        audit.log("RRP_INDICATOR_ADDED", "RrpIndicator", saved.getId(),
                "Indicator " + saved.getIndicatorCode() + " (" + saved.getCategory()
                        + ", band " + saved.getCurrentBand() + ")");
        timeline(c.getId(), "INDICATOR_BREACH", null, null, null,
                "Indicator " + saved.getIndicatorCode() + " registered, current band "
                        + saved.getCurrentBand(),
                "تسجيل المؤشر " + saved.getIndicatorCode() + "، النطاق الحالي "
                        + saved.getCurrentBand());
        return toIndicator(saved);
    }

    @Transactional
    public IndicatorResponse recordMeasurement(Long indicatorId,
                                                 RecordIndicatorMeasurementRequest req) {
        RrpIndicator i = indRepo.findByIdAndOrganisationId(indicatorId, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Indicator " + indicatorId + " not found"));
        String prevBand = i.getCurrentBand();
        i.setCurrentValue(req.getValue());
        i.setMeasuredAt(req.getMeasuredAt());
        i.setCurrentBand(computeBand(i.getDirection(),
                i.getGreenThreshold(), i.getAmberThreshold(), req.getValue()));
        RrpIndicator saved = indRepo.save(i);
        RrpCycle c = loadCycle(saved.getCycleId());
        recountCycle(c);

        if (!saved.getCurrentBand().equals(prevBand)) {
            boolean breach = bandWorseThan(saved.getCurrentBand(), prevBand);
            String eventType = breach ? "INDICATOR_BREACH" : "INDICATOR_RECOVERED";
            String trigger = "RRP_INDICATOR_THRESHOLD_BREACHED";
            audit.log(trigger, "RrpIndicator", saved.getId(),
                    "Indicator " + saved.getIndicatorCode() + " " + prevBand
                            + " → " + saved.getCurrentBand());
            emit(trigger, "RrpIndicator", saved.getId(), saved.getIndicatorCode(),
                    Map.of("cycleId", String.valueOf(c.getId()),
                            "indicatorCode", saved.getIndicatorCode(),
                            "category", saved.getCategory(),
                            "bandBefore", prevBand,
                            "bandAfter", saved.getCurrentBand(),
                            "value", String.valueOf(saved.getCurrentValue()),
                            "worsening", String.valueOf(breach)));
            timeline(c.getId(), eventType, null, null, null,
                    "Indicator " + saved.getIndicatorCode() + " " + prevBand
                            + " → " + saved.getCurrentBand()
                            + (req.getNoteEn() == null ? "" : " — " + req.getNoteEn()),
                    "المؤشر " + saved.getIndicatorCode() + " " + prevBand + " ← "
                            + saved.getCurrentBand()
                            + (req.getNoteAr() == null ? "" : " — " + req.getNoteAr()),
                    null, prevBand, saved.getCurrentBand());
        }
        return toIndicator(saved);
    }

    // ─── Reads ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CycleResponse> listCycles(int page, int size) {
        return cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(
                org(), PageRequest.of(page, size))
                .map(this::toCycle);
    }

    @Transactional(readOnly = true)
    public CycleResponse getCycle(Long id) {
        return toCycle(loadCycle(id));
    }

    @Transactional(readOnly = true)
    public List<OptionResponse> listOptions(Long cycleId) {
        loadCycle(cycleId);
        return optionRepo.findByCycleIdAndOrganisationIdOrderByOptionCodeAsc(cycleId, org())
                .stream().map(RrpService::toOption).toList();
    }

    @Transactional(readOnly = true)
    public List<CriticalFunctionResponse> listCriticalFunctions(Long cycleId) {
        loadCycle(cycleId);
        return cfRepo.findByCycleIdAndOrganisationIdOrderByFunctionCodeAsc(cycleId, org())
                .stream().map(RrpService::toCriticalFunction).toList();
    }

    @Transactional(readOnly = true)
    public List<IndicatorResponse> listIndicators(Long cycleId) {
        loadCycle(cycleId);
        return indRepo.findByCycleIdAndOrganisationIdOrderByIndicatorCodeAsc(cycleId, org())
                .stream().map(RrpService::toIndicator).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Long cycleId) {
        loadCycle(cycleId);
        return eventRepo.findByCycleIdAndOrganisationIdOrderByOccurredAtDesc(cycleId, org())
                .stream().map(RrpService::toEvent).toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        long activeCount = 0;
        for (String s : ACTIVE_STATUSES) {
            activeCount += cycleRepo.countByOrganisationIdAndStatus(org(), s);
        }
        long submitted = cycleRepo.countByOrganisationIdAndStatus(org(), "SUBMITTED")
                + cycleRepo.countByOrganisationIdAndStatus(org(), "ACKNOWLEDGED");
        long closed = cycleRepo.countByOrganisationIdAndStatus(org(), "CLOSED");

        // ── F2-421: one UNCAPPED list feeds every derivation below ─────────────────────────────
        //
        // This was `PageRequest.of(0, 50)` -- the 50 most-recent fiscal years -- and four figures
        // came off it while `activeCount`, `submitted`, `closed` and `byStatus` above and below are
        // all-time countBy sums. A windowed figure beside an all-time countBy in one method is this
        // family's signature (F2-406 / F2-415 / F2-420).
        //
        // ⚠ The cap's order is ADVERSE, not merely orthogonal: `fiscal_year DESC` keeps the NEWEST,
        // and an overdue cycle is one whose due date has PASSED, so the rows the window drops are
        // the ones most likely to be overdue. It under-reported -- the flattering direction.
        //
        // ⚠ Reachable without 50 years of history: `rrp_cycle` has no unique constraint on
        // fiscal_year (only ux_rrp_cycle_reference on (organisation_id, reference)) and
        // `nextOrdinal` mints a per-fiscal-year ordinal, so several cycles per year is designed.
        //
        // ⚠ Two derivations below are CORRECT and must keep working, which is why the fix is one
        // uncapped list rather than a ban on capping: `capCurrent` reads element 0 (the newest
        // fiscal year) and `recentCycles` takes `.limit(10)` of the same ordering -- for both of
        // those the cap order IS the question they ask (F2-420's `awarenessRate` shape).
        List<RrpCycle> all = cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(org());

        // ── F2-132: a cycle still awaiting submission, past its own due date ────────────────────
        //
        // submission_due_date was written at creation, updatable, echoed in every cycle response —
        // and compared to nothing anywhere in the module. So a SAMA recovery-plan submission could be
        // months late while this dashboard reported it merely as "active". Measured 2026-08-06: all
        // three tenants hold an IN_PREPARATION FY2026 cycle 128 days past its 2026-03-31 due date.
        //
        // ⚠ ACTIVE_STATUSES is the right population, not "everything not CLOSED": once a cycle is
        // SUBMITTED or ACKNOWLEDGED the filing has happened, so a passed due date is history rather
        // than an outstanding obligation. Counting those would report a permanent, unfixable overdue
        // figure — the trap A17-D3 fell into by measuring a clock with no start.
        //
        // ⚠ Reported, not enforced. Nothing refuses a late submission and no status changes:
        // lateness is a fact about a date, and a stored LATE word could disagree with the date
        // (S7e's reasoning). This counts; the operator decides.
        LocalDate today = LocalDate.now();
        long overdue = all.stream()
                .filter(c -> ACTIVE_STATUSES.contains(c.getStatus()))
                .filter(c -> c.getSubmissionDueDate() != null
                        && c.getSubmissionDueDate().isBefore(today))
                .count();

        long red = 0, amber = 0;
        long capCurrent = 0;
        for (int i = 0; i < all.size(); i++) {
            RrpCycle c = all.get(i);
            red += c.getIndicatorsRed() == null ? 0 : c.getIndicatorsRed();
            amber += c.getIndicatorsAmber() == null ? 0 : c.getIndicatorsAmber();
            if (i == 0) {
                capCurrent = c.getTotalRecoveryCapacitySarM() == null
                        ? 0 : c.getTotalRecoveryCapacitySarM();
            }
        }

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : new String[] {
                "DRAFT", "IN_PREPARATION", "BOARD_REVIEW", "APPROVED",
                "SUBMITTED", "ACKNOWLEDGED", "CLOSED"}) {
            long n = cycleRepo.countByOrganisationIdAndStatus(org(), s);
            if (n > 0) byStatus.put(s, n);
        }

        return DashboardResponse.builder()
                .activeCycleCount(activeCount)
                .submittedCycleCount(submitted)
                .closedCycleCount(closed)
                .overdueCycleCount(overdue)
                .indicatorsRed(red)
                .indicatorsAmber(amber)
                .currentRecoveryCapacitySarM(capCurrent)
                .recentCycles(all.stream().limit(10).map(this::toCycle).toList())
                .byStatus(byStatus)
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private RrpCycle loadCycle(Long id) {
        return cycleRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "RRP cycle " + id + " not found in tenant"));
    }

    private long nextOrdinal(int fy) {
        return cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(
                org(), PageRequest.of(0, 200)).stream()
                .filter(c -> c.getFiscalYear() != null && c.getFiscalYear() == fy)
                .count() + 1L;
    }

    private void recountCycle(RrpCycle c) {
        long opts = optionRepo.countByCycleIdAndOrganisationId(c.getId(), org());
        long cfs  = cfRepo.countByCycleIdAndOrganisationId(c.getId(), org());
        long inds = indRepo.countByCycleIdAndOrganisationId(c.getId(), org());
        long amber = indRepo.countByCycleIdAndOrganisationIdAndCurrentBand(c.getId(), org(), "AMBER");
        long red   = indRepo.countByCycleIdAndOrganisationIdAndCurrentBand(c.getId(), org(), "RED");
        long capacity = optionRepo
                .findByCycleIdAndOrganisationIdOrderByOptionCodeAsc(c.getId(), org()).stream()
                .mapToLong(o -> o.getCapacitySarM() == null ? 0 : o.getCapacitySarM())
                .sum();
        c.setRecoveryOptionCount((int) opts);
        c.setCriticalFunctionCount((int) cfs);
        c.setIndicatorCount((int) inds);
        c.setIndicatorsAmber((int) amber);
        c.setIndicatorsRed((int) red);
        c.setTotalRecoveryCapacitySarM(capacity);
        cycleRepo.save(c);
    }

    /**
     * F2-396 — an RRP indicator is a 3-band RAG (GREEN/AMBER/RED), and {@link #computeBand}
     * only produces correct bands when the green and amber thresholds are strictly ordered
     * per direction: HIGHER_IS_BETTER needs {@code green > amber}, LOWER_IS_BETTER needs
     * {@code green < amber} (see the {@code RrpIndicator} worked examples — CET1 green 13 >
     * amber 11; NPL green 3 < amber 5). An inverted pair silently mis-bands: a breaching SAMA
     * recovery indicator reads GREEN, {@code recountCycle} under-reports, and the
     * {@code RRP_INDICATOR_THRESHOLD_BREACHED} F8 escalation to the CRO/CFO never fires — so the
     * Recovery Committee is never convened. Mirrors the house pattern
     * (VulnerabilitySlaPolicyService.validatePctOrder, BcmSettingsService.validate).
     */
    static void validateThresholdOrder(String direction, BigDecimal green, BigDecimal amber) {
        if (green == null || amber == null) return;   // computeBand returns GREEN — nothing to order
        boolean higherBetter = !"LOWER_IS_BETTER".equals(direction);
        if (higherBetter && green.compareTo(amber) <= 0) {
            throw new IllegalArgumentException(
                    "greenThreshold (" + green + ") must be greater than amberThreshold (" + amber
                            + ") for a HIGHER_IS_BETTER indicator");
        }
        if (!higherBetter && green.compareTo(amber) >= 0) {
            throw new IllegalArgumentException(
                    "greenThreshold (" + green + ") must be less than amberThreshold (" + amber
                            + ") for a LOWER_IS_BETTER indicator");
        }
    }

    static String computeBand(String direction, BigDecimal green,
                                BigDecimal amber, BigDecimal value) {
        if (value == null || green == null || amber == null) return "GREEN";
        boolean higherBetter = !"LOWER_IS_BETTER".equals(direction);
        if (higherBetter) {
            if (value.compareTo(green) >= 0) return "GREEN";
            if (value.compareTo(amber) >= 0) return "AMBER";
            return "RED";
        } else {
            if (value.compareTo(green) <= 0) return "GREEN";
            if (value.compareTo(amber) <= 0) return "AMBER";
            return "RED";
        }
    }

    /** True when {@code after} is a worse RAG band than {@code before}. */
    static boolean bandWorseThan(String after, String before) {
        return rank(after) > rank(before);
    }

    private static int rank(String band) {
        return switch (band == null ? "" : band) {
            case "RED"   -> 2;
            case "AMBER" -> 1;
            default      -> 0;
        };
    }

    private void timeline(Long cycleId, String eventType, String statusAfter,
                           Long linkedOptionId, Long linkedCfId,
                           String noteEn, String noteAr) {
        timeline(cycleId, eventType, statusAfter, linkedOptionId, linkedCfId,
                noteEn, noteAr, null, null, null);
    }

    private void timeline(Long cycleId, String eventType, String statusAfter,
                           Long linkedOptionId, Long linkedCfId,
                           String noteEn, String noteAr,
                           Long linkedIndicatorId, String bandBefore, String bandAfter) {
        RrpEvent e = new RrpEvent();
        e.setOrganisationId(org());
        e.setCycleId(cycleId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setActorName(currentActorName());
        e.setStatusAfter(statusAfter);
        e.setLinkedOptionId(linkedOptionId);
        e.setLinkedCriticalFunctionId(linkedCfId);
        e.setLinkedIndicatorId(linkedIndicatorId);
        e.setBandBefore(bandBefore);
        e.setBandAfter(bandAfter);
        e.setNoteEn(noteEn);
        e.setNoteAr(noteAr);
        eventRepo.save(e);
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    /**
     * F2-257 — the display half of the actor pair, from the same primitive as {@link
     * #currentUserId()}.
     *
     * <p>Deliberately {@code username()} and not {@code requireUsername(...)}: the id half is
     * nullable and comes from the same place, so both halves are populated together or absent
     * together. Refusing here would let a timeline write abort the business operation it exists to
     * record.
     */
    private static String currentActorName() {
        return com.tss.grc.common.security.CurrentActor.username();
    }

    private static Long currentUserId() {
        return com.tss.grc.common.security.CurrentActor.userId();
    }

    private void emit(String trigger, String entityType, Long entityId,
                       String correlationId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, correlationId,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    // ─── DTO mappers ───────────────────────────────────────────────

    private CycleResponse toCycle(RrpCycle c) {
        return CycleResponse.builder()
                .id(c.getId())
                .reference(c.getReference())
                .fiscalYear(c.getFiscalYear())
                .titleEn(c.getTitleEn())
                .titleAr(c.getTitleAr())
                .status(c.getStatus())
                .submissionDueDate(c.getSubmissionDueDate())
                .submittedAt(c.getSubmittedAt())
                .regulatorAckReference(c.getRegulatorAckReference())
                .acknowledgedAt(c.getAcknowledgedAt())
                .closedAt(c.getClosedAt())
                .ownerUserId(c.getOwnerUserId())
                .sponsorUserId(c.getSponsorUserId())
                .boardApprovalDate(c.getBoardApprovalDate())
                .boardResolutionReference(c.getBoardResolutionReference())
                .executiveSummaryEn(c.getExecutiveSummaryEn())
                .executiveSummaryAr(c.getExecutiveSummaryAr())
                .filingArtifactReference(c.getFilingArtifactReference())
                .criticalFunctionCount(c.getCriticalFunctionCount())
                .recoveryOptionCount(c.getRecoveryOptionCount())
                .indicatorCount(c.getIndicatorCount())
                .indicatorsAmber(c.getIndicatorsAmber())
                .indicatorsRed(c.getIndicatorsRed())
                .totalRecoveryCapacitySarM(c.getTotalRecoveryCapacitySarM())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private static OptionResponse toOption(RrpRecoveryOption o) {
        return OptionResponse.builder()
                .id(o.getId()).cycleId(o.getCycleId())
                .optionCode(o.getOptionCode()).category(o.getCategory())
                .titleEn(o.getTitleEn()).titleAr(o.getTitleAr())
                .descriptionEn(o.getDescriptionEn()).descriptionAr(o.getDescriptionAr())
                .capacitySarM(o.getCapacitySarM())
                .timeToExecuteDays(o.getTimeToExecuteDays())
                .feasibility(o.getFeasibility())
                .impactEn(o.getImpactEn()).impactAr(o.getImpactAr())
                .preconditionsEn(o.getPreconditionsEn()).preconditionsAr(o.getPreconditionsAr())
                .limitationsEn(o.getLimitationsEn()).limitationsAr(o.getLimitationsAr())
                .accountableRole(o.getAccountableRole())
                .build();
    }

    private static CriticalFunctionResponse toCriticalFunction(RrpCriticalFunction cf) {
        return CriticalFunctionResponse.builder()
                .id(cf.getId()).cycleId(cf.getCycleId())
                .functionCode(cf.getFunctionCode())
                .titleEn(cf.getTitleEn()).titleAr(cf.getTitleAr())
                .descriptionEn(cf.getDescriptionEn()).descriptionAr(cf.getDescriptionAr())
                .materiality(cf.getMateriality())
                .maxTolerableDowntimeHours(cf.getMaxTolerableDowntimeHours())
                .customerCountEstimate(cf.getCustomerCountEstimate())
                .dailyVolumeEstimate(cf.getDailyVolumeEstimate())
                .criticalProviderDependenciesEn(cf.getCriticalProviderDependenciesEn())
                .criticalProviderDependenciesAr(cf.getCriticalProviderDependenciesAr())
                .continuityStrategyEn(cf.getContinuityStrategyEn())
                .continuityStrategyAr(cf.getContinuityStrategyAr())
                .ownerRole(cf.getOwnerRole())
                .build();
    }

    private static IndicatorResponse toIndicator(RrpIndicator i) {
        return IndicatorResponse.builder()
                .id(i.getId()).cycleId(i.getCycleId())
                .indicatorCode(i.getIndicatorCode()).category(i.getCategory())
                .titleEn(i.getTitleEn()).titleAr(i.getTitleAr())
                .descriptionEn(i.getDescriptionEn()).descriptionAr(i.getDescriptionAr())
                .unit(i.getUnit()).direction(i.getDirection())
                .greenThreshold(i.getGreenThreshold()).amberThreshold(i.getAmberThreshold())
                .currentValue(i.getCurrentValue()).currentBand(i.getCurrentBand())
                .measuredAt(i.getMeasuredAt()).dataSource(i.getDataSource())
                .ownerRole(i.getOwnerRole())
                .build();
    }

    private static EventResponse toEvent(RrpEvent e) {
        return EventResponse.builder()
                .id(e.getId()).cycleId(e.getCycleId())
                .occurredAt(e.getOccurredAt()).eventType(e.getEventType())
                .actorUserId(e.getActorUserId()).actorName(e.getActorName())
                .statusAfter(e.getStatusAfter())
                .linkedOptionId(e.getLinkedOptionId())
                .linkedIndicatorId(e.getLinkedIndicatorId())
                .linkedCriticalFunctionId(e.getLinkedCriticalFunctionId())
                .bandBefore(e.getBandBefore()).bandAfter(e.getBandAfter())
                .noteEn(e.getNoteEn()).noteAr(e.getNoteAr())
                .build();
    }
}
