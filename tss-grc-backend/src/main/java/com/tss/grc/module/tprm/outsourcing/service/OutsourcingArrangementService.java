package com.tss.grc.module.tprm.outsourcing.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementTransitionRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementUpdateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DashboardResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MaterialityAssessment;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingExitPlanRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingMonitoringEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 13 — Outsourcing arrangement register service.
 *
 * <p>Owns the lifecycle of an outsourcing arrangement (SAMA Rules
 * Governing Outsourcing 2019/2023 §3 register; §4.2 material-outsourcing
 * SAMA non-objection gate). The companion services
 * ({@link OutsourcingDueDiligenceService},
 * {@link OutsourcingMonitoringService},
 * {@link OutsourcingExitPlanService}) handle the per-arrangement
 * sub-records.
 *
 * <h3>Lifecycle state machine</h3>
 * <pre>
 *   PLANNED          → DUE_DILIGENCE | EXITED
 *   DUE_DILIGENCE    → AWAITING_SAMA | APPROVED | EXITED
 *   AWAITING_SAMA    → APPROVED | EXITED
 *   APPROVED         → ACTIVE | EXITED
 *   ACTIVE           → MONITORING_ALERT | UNDER_REVIEW | EXIT_PLANNING | EXITED
 *   MONITORING_ALERT → ACTIVE | UNDER_REVIEW | EXIT_PLANNING | EXITED
 *   UNDER_REVIEW     → ACTIVE | EXIT_PLANNING | EXITED
 *   EXIT_PLANNING    → EXIT_IN_PROGRESS | EXITED
 *   EXIT_IN_PROGRESS → EXITED
 *   EXITED           (terminal)
 * </pre>
 *
 * <h3>Materiality auto-detection</h3>
 * {@link #materialityAssessment(Long)} flags an arrangement as material
 * when ANY of the following hold:
 * <ul>
 *   <li>{@code critical_function != "NA"} — the arrangement underpins a
 *       SAMA-watched critical function (payments, core banking,
 *       AML/CFT, customer data, etc.)</li>
 *   <li>{@code contract_value_sar_annual > 5,000,000} — concentration
 *       on a single spend item</li>
 *   <li>{@code personal_data_processed = true} AND it's plausibly
 *       large-scale (estimated by call sites; this service
 *       conservatively flags any personal-data arrangement)</li>
 * </ul>
 * The result is advisory — the human can override (e.g. mark
 * material-on-paper as non-material with documented rationale). When
 * the auto-suggestion disagrees with the stored value, an
 * {@code OUTSOURCING_MATERIAL_CHANGE} F8 event is emitted so the
 * Compliance Officer is alerted.
 */
@Service
@RequiredArgsConstructor
public class OutsourcingArrangementService {

    private static final Logger log = LoggerFactory.getLogger(OutsourcingArrangementService.class);

    /** Materiality threshold (SAR annual). 5M is SAMA-aligned for the
     *  "material concentration" trigger we use. */
    public static final BigDecimal MATERIAL_VALUE_THRESHOLD = new BigDecimal("5000000.00");

    /** Allowed forward transitions per current lifecycle stage. */
    static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.ofEntries(
        Map.entry("PLANNED",          Set.of("DUE_DILIGENCE", "EXITED")),
        Map.entry("DUE_DILIGENCE",    Set.of("AWAITING_SAMA", "APPROVED", "EXITED")),
        Map.entry("AWAITING_SAMA",    Set.of("APPROVED", "EXITED")),
        Map.entry("APPROVED",         Set.of("ACTIVE", "EXITED")),
        Map.entry("ACTIVE",           Set.of("MONITORING_ALERT", "UNDER_REVIEW", "EXIT_PLANNING", "EXITED")),
        Map.entry("MONITORING_ALERT", Set.of("ACTIVE", "UNDER_REVIEW", "EXIT_PLANNING", "EXITED")),
        Map.entry("UNDER_REVIEW",     Set.of("ACTIVE", "EXIT_PLANNING", "EXITED")),
        Map.entry("EXIT_PLANNING",    Set.of("EXIT_IN_PROGRESS", "EXITED")),
        Map.entry("EXIT_IN_PROGRESS", Set.of("EXITED")),
        Map.entry("EXITED",           Set.of())
    );

    public static final Set<String> LIFECYCLE_STATES = ALLOWED_TRANSITIONS.keySet();

    private final OutsourcingArrangementRepository repo;
    private final OutsourcingMonitoringEventRepository monitoringRepo;
    private final OutsourcingExitPlanRepository exitPlanRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ArrangementResponse> list() {
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org())
            .stream().map(OutsourcingArrangementService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ArrangementResponse> listByLifecycleStage(String stage) {
        return repo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(org(), stage)
            .stream().map(OutsourcingArrangementService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ArrangementResponse> listByMateriality(boolean isMaterial) {
        return repo.findByOrganisationIdAndIsMaterialAndIsDeletedFalseOrderByCreatedAtDesc(org(), isMaterial)
            .stream().map(OutsourcingArrangementService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ArrangementResponse> listByCriticalFunction(String criticalFunction) {
        return repo.findByOrganisationIdAndCriticalFunctionAndIsDeletedFalseOrderByCreatedAtDesc(org(), criticalFunction)
            .stream().map(OutsourcingArrangementService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ArrangementResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ArrangementResponse> listAwaitingSamaNonObjection() {
        return repo.findRequiringSamaNonObjection(org())
            .stream().map(OutsourcingArrangementService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ArrangementResponse> listOverdueForReview() {
        return repo.findMaterialOverdueForReview(org(), LocalDate.now())
            .stream().map(OutsourcingArrangementService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Long orgId = org();

        long total      = repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId).size();
        long active     = repo.countByOrganisationIdAndLifecycleStageAndIsDeletedFalse(orgId, "ACTIVE");
        long activeMat  = repo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(orgId, "ACTIVE")
                              .stream().filter(o -> Boolean.TRUE.equals(o.getIsMaterial())).count();
        long nonMat     = repo.countByOrganisationIdAndIsMaterialAndIsDeletedFalse(orgId, false);
        long awaiting   = repo.findRequiringSamaNonObjection(orgId).size();
        long overdueRev = repo.findMaterialOverdueForReview(orgId, LocalDate.now()).size();

        LocalDate today = LocalDate.now();
        long overdueExit = exitPlanRepo.findOverdueTest(orgId, today.minusDays(365), today.minusDays(730)).size();

        long openMajor = monitoringRepo.countByOrganisationIdAndSeverityInAndResolvedAndIsDeletedFalse(
            orgId, List.of("MAJOR", "CRITICAL"), false);

        // active arrangement count is implicit; if you need raw active total
        // pull it from the totals card on the UI.
        if (active < activeMat) {
            log.debug("[outsourcing] dashboard sanity: active count {} < active material {}; check seed", active, activeMat);
        }

        return new DashboardResponse(total, activeMat, nonMat, awaiting, overdueRev, overdueExit, openMajor);
    }

    /**
     * Auto-detect materiality for an arrangement. Returns the
     * suggestion plus the reasons used to derive it; the caller (UI or
     * service) decides whether to flip the {@code isMaterial} flag.
     */
    @Transactional(readOnly = true)
    public MaterialityAssessment materialityAssessment(Long arrangementId) {
        OutsourcingArrangement o = loadOrThrow(arrangementId);
        List<String> reasons = new ArrayList<>();

        boolean criticalFunctionHit = o.getCriticalFunction() != null
            && !"NA".equals(o.getCriticalFunction());
        if (criticalFunctionHit) {
            reasons.add("critical_function=" + o.getCriticalFunction());
        }

        boolean contractValueHit = o.getContractValueSarAnnual() != null
            && o.getContractValueSarAnnual().compareTo(MATERIAL_VALUE_THRESHOLD) > 0;
        if (contractValueHit) {
            reasons.add("contract_value_sar_annual>" + MATERIAL_VALUE_THRESHOLD.toPlainString());
        }

        boolean personalDataHit = Boolean.TRUE.equals(o.getPersonalDataProcessed());
        if (personalDataHit) {
            reasons.add("personal_data_processed=true");
        }

        boolean suggested = criticalFunctionHit || contractValueHit || personalDataHit;
        return new MaterialityAssessment(
            o.getId(),
            suggested,
            Boolean.TRUE.equals(o.getIsMaterial()),
            reasons.toArray(new String[0])
        );
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public ArrangementResponse create(ArrangementCreateRequest req) {
        Long orgId = org();

        OutsourcingArrangement o = new OutsourcingArrangement();
        o.setOrganisationId(orgId);
        o.setArrangementCode(req.arrangementCode() != null && !req.arrangementCode().isBlank()
            ? req.arrangementCode() : nextArrangementCode());
        if (repo.existsByOrganisationIdAndArrangementCodeAndIsDeletedFalse(orgId, o.getArrangementCode())) {
            throw new IllegalArgumentException("Arrangement code already exists: " + o.getArrangementCode());
        }

        o.setTitleEn(req.titleEn());
        o.setTitleAr(req.titleAr());
        o.setDescriptionEn(req.descriptionEn());
        o.setDescriptionAr(req.descriptionAr());
        o.setServiceType(req.serviceType());
        o.setPrimaryVendorId(req.primaryVendorId());
        o.setCoVendorIds(req.coVendorIds());

        // Materiality: explicit value wins; otherwise auto-detect from the
        // arrangement's own facts (we duplicate the logic here because the
        // entity isn't saved yet so we can't reuse materialityAssessment).
        o.setIsMaterial(req.isMaterial() != null
            ? req.isMaterial()
            : isAutoMaterial(req.criticalFunction(), req.contractValueSarAnnual(), req.personalDataProcessed()));
        o.setMaterialityBasisEn(req.materialityBasisEn());
        o.setMaterialityBasisAr(req.materialityBasisAr());

        o.setSamaNonObjectionRequired(req.samaNonObjectionRequired() != null
            ? req.samaNonObjectionRequired()
            : Boolean.TRUE.equals(o.getIsMaterial())); // material → SAMA non-objection required by default
        o.setSamaNonObjectionObtained(req.samaNonObjectionObtained() != null ? req.samaNonObjectionObtained() : false);
        o.setSamaNonObjectionRef(req.samaNonObjectionRef());
        o.setSamaNonObjectionDate(req.samaNonObjectionDate());

        o.setContractStartDate(req.contractStartDate());
        o.setContractEndDate(req.contractEndDate());
        o.setContractValueSarAnnual(req.contractValueSarAnnual());
        o.setContractRef(req.contractRef());
        o.setContractDocumentUrl(req.contractDocumentUrl());

        o.setCriticalFunction(req.criticalFunction() != null ? req.criticalFunction() : "NA");
        o.setCountryOfProcessing(req.countryOfProcessing());
        o.setRegionOfProcessing(req.regionOfProcessing());
        o.setDataResidencyKdc(req.dataResidencyKdc() != null ? req.dataResidencyKdc() : true);

        o.setPersonalDataProcessed(req.personalDataProcessed() != null ? req.personalDataProcessed() : false);
        o.setPdplDpaSigned(req.pdplDpaSigned() != null ? req.pdplDpaSigned() : false);
        o.setPdplDpaDate(req.pdplDpaDate());
        o.setCrossBorderSafeguardType(req.crossBorderSafeguardType());

        o.setOwnerUserId(req.ownerUserId());
        o.setSponsorUserId(req.sponsorUserId());
        o.setNextReviewDueDate(req.nextReviewDueDate());

        o.setLifecycleStage("PLANNED");

        OutsourcingArrangement saved = repo.save(o);
        audit.log("CREATE", "OutsourcingArrangement", saved.getId(),
            "Outsourcing arrangement registered: " + saved.getArrangementCode() + " (" + saved.getTitleEn() + ")");
        emit("OUTSOURCING_REGISTERED", saved, null);

        // If material and SAMA non-objection isn't obtained, fire the
        // notification-required event so Compliance picks it up.
        if (Boolean.TRUE.equals(saved.getIsMaterial())
            && Boolean.TRUE.equals(saved.getSamaNonObjectionRequired())
            && !Boolean.TRUE.equals(saved.getSamaNonObjectionObtained())) {
            emit("OUTSOURCING_SAMA_NOTIFICATION_REQUIRED", saved, null);
        }

        return toResponse(saved);
    }

    @Transactional
    public ArrangementResponse update(Long id, ArrangementUpdateRequest req) {
        OutsourcingArrangement o = loadOrThrow(id);
        boolean materialBefore = Boolean.TRUE.equals(o.getIsMaterial());

        if (req.titleEn() != null)                  o.setTitleEn(req.titleEn());
        if (req.titleAr() != null)                  o.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null)            o.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null)            o.setDescriptionAr(req.descriptionAr());
        if (req.serviceType() != null)              o.setServiceType(req.serviceType());
        if (req.primaryVendorId() != null)          o.setPrimaryVendorId(req.primaryVendorId());
        if (req.coVendorIds() != null)              o.setCoVendorIds(req.coVendorIds());
        if (req.isMaterial() != null)               o.setIsMaterial(req.isMaterial());
        if (req.materialityBasisEn() != null)       o.setMaterialityBasisEn(req.materialityBasisEn());
        if (req.materialityBasisAr() != null)       o.setMaterialityBasisAr(req.materialityBasisAr());
        if (req.samaNonObjectionRequired() != null) o.setSamaNonObjectionRequired(req.samaNonObjectionRequired());
        if (req.samaNonObjectionObtained() != null) o.setSamaNonObjectionObtained(req.samaNonObjectionObtained());
        if (req.samaNonObjectionRef() != null)      o.setSamaNonObjectionRef(req.samaNonObjectionRef());
        if (req.samaNonObjectionDate() != null)     o.setSamaNonObjectionDate(req.samaNonObjectionDate());
        if (req.contractStartDate() != null)        o.setContractStartDate(req.contractStartDate());
        if (req.contractEndDate() != null)          o.setContractEndDate(req.contractEndDate());
        if (req.contractValueSarAnnual() != null)   o.setContractValueSarAnnual(req.contractValueSarAnnual());
        if (req.contractRef() != null)              o.setContractRef(req.contractRef());
        if (req.contractDocumentUrl() != null)      o.setContractDocumentUrl(req.contractDocumentUrl());
        if (req.criticalFunction() != null)         o.setCriticalFunction(req.criticalFunction());
        if (req.countryOfProcessing() != null)      o.setCountryOfProcessing(req.countryOfProcessing());
        if (req.regionOfProcessing() != null)       o.setRegionOfProcessing(req.regionOfProcessing());
        if (req.dataResidencyKdc() != null)         o.setDataResidencyKdc(req.dataResidencyKdc());
        if (req.personalDataProcessed() != null)    o.setPersonalDataProcessed(req.personalDataProcessed());
        if (req.pdplDpaSigned() != null)            o.setPdplDpaSigned(req.pdplDpaSigned());
        if (req.pdplDpaDate() != null)              o.setPdplDpaDate(req.pdplDpaDate());
        if (req.crossBorderSafeguardType() != null) o.setCrossBorderSafeguardType(req.crossBorderSafeguardType());
        if (req.ownerUserId() != null)              o.setOwnerUserId(req.ownerUserId());
        if (req.sponsorUserId() != null)            o.setSponsorUserId(req.sponsorUserId());
        if (req.nextReviewDueDate() != null)        o.setNextReviewDueDate(req.nextReviewDueDate());

        o.setUpdatedAt(LocalDateTime.now());
        OutsourcingArrangement saved = repo.save(o);
        audit.log("UPDATE", "OutsourcingArrangement", saved.getId(),
            "Outsourcing arrangement updated: " + saved.getArrangementCode());

        // Materiality flip = MUST trigger downstream review.
        if (materialBefore != Boolean.TRUE.equals(saved.getIsMaterial())) {
            emit("OUTSOURCING_MATERIAL_CHANGE", saved, null);
        }

        return toResponse(saved);
    }

    @Transactional
    public ArrangementResponse transition(Long id, ArrangementTransitionRequest req) {
        OutsourcingArrangement o = loadOrThrow(id);
        String from = o.getLifecycleStage();
        guardTransition(from, req.toStage());

        // SAMA non-objection gate: cannot go to APPROVED if material +
        // SAMA non-objection required but not obtained.
        if ("APPROVED".equals(req.toStage())
            && Boolean.TRUE.equals(o.getIsMaterial())
            && Boolean.TRUE.equals(o.getSamaNonObjectionRequired())
            && !Boolean.TRUE.equals(o.getSamaNonObjectionObtained())) {
            throw new IllegalStateException(
                "Cannot APPROVE material arrangement without SAMA non-objection. "
                    + "Set sama_non_objection_obtained = true + supply reference first.");
        }

        o.setLifecycleStage(req.toStage());
        o.setUpdatedAt(LocalDateTime.now());
        OutsourcingArrangement saved = repo.save(o);

        audit.log("TRANSITION", "OutsourcingArrangement", saved.getId(),
            "Arrangement " + saved.getArrangementCode() + " " + from + " → " + req.toStage()
                + (req.reason() != null && !req.reason().isBlank() ? " — " + req.reason() : ""));
        emit("OUTSOURCING_LIFECYCLE_TRANSITION", saved, from);
        return toResponse(saved);
    }

    /**
     * Record that a periodic review of the arrangement has been
     * performed. Bumps {@code next_review_due_date} forward 12 months.
     */
    @Transactional
    public ArrangementResponse recordReview(Long id, Long byUserId) {
        OutsourcingArrangement o = loadOrThrow(id);
        LocalDate today = LocalDate.now();
        o.setNextReviewDueDate(today.plusMonths(12));
        o.setUpdatedAt(LocalDateTime.now());
        OutsourcingArrangement saved = repo.save(o);
        audit.log("REVIEW", "OutsourcingArrangement", saved.getId(),
            "Periodic review recorded for " + saved.getArrangementCode()
                + (byUserId != null ? " by user " + byUserId : "")
                + " — next review " + saved.getNextReviewDueDate());
        emit("OUTSOURCING_REVIEWED", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public void softDelete(Long id) {
        OutsourcingArrangement o = loadOrThrow(id);
        o.setIsDeleted(true);
        o.setDeletedAt(LocalDateTime.now());
        repo.save(o);
        audit.log("DELETE", "OutsourcingArrangement", id,
            "Outsourcing arrangement soft-deleted: " + o.getArrangementCode());
    }

    // ─── Guards ─────────────────────────────────────────────────────

    static void guardTransition(String from, String to) {
        if (!LIFECYCLE_STATES.contains(to)) {
            throw new IllegalArgumentException("Unknown lifecycle stage: " + to);
        }
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(Objects.requireNonNullElse(from, ""), Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    static boolean isAutoMaterial(String criticalFunction, BigDecimal contractValue, Boolean personalDataProcessed) {
        boolean cf = criticalFunction != null && !"NA".equals(criticalFunction);
        boolean cv = contractValue != null && contractValue.compareTo(MATERIAL_VALUE_THRESHOLD) > 0;
        boolean pd = Boolean.TRUE.equals(personalDataProcessed);
        return cf || cv || pd;
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private OutsourcingArrangement loadOrThrow(Long id) {
        return repo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Outsourcing arrangement not found: " + id));
    }

    private static String nextArrangementCode() {
        int year = LocalDate.now().getYear();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "OUT-" + year + "-" + suffix;
    }

    void emit(String trigger, OutsourcingArrangement o, String previousStage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("arrangementId", String.valueOf(o.getId()));
        payload.put("arrangementCode", o.getArrangementCode());
        payload.put("serviceType", o.getServiceType());
        payload.put("isMaterial", String.valueOf(Boolean.TRUE.equals(o.getIsMaterial())));
        payload.put("lifecycleStage", o.getLifecycleStage());
        payload.put("previousStage", previousStage == null ? "" : previousStage);
        payload.put("criticalFunction", o.getCriticalFunction());
        payload.put("countryOfProcessing", o.getCountryOfProcessing());
        payload.put("personalDataProcessed", String.valueOf(Boolean.TRUE.equals(o.getPersonalDataProcessed())));
        payload.put("samaNonObjectionRequired", String.valueOf(Boolean.TRUE.equals(o.getSamaNonObjectionRequired())));
        payload.put("samaNonObjectionObtained", String.valueOf(Boolean.TRUE.equals(o.getSamaNonObjectionObtained())));
        publisher.publishEvent(new DomainEvent(
            o.getOrganisationId(), trigger, "OutsourcingArrangement", o.getId(),
            "outsourcing:" + o.getId(), payload));
    }

    static ArrangementResponse toResponse(OutsourcingArrangement o) {
        return new ArrangementResponse(
            o.getId(), o.getExternalId(), o.getOrganisationId(),
            o.getArrangementCode(), o.getTitleEn(), o.getTitleAr(),
            o.getDescriptionEn(), o.getDescriptionAr(),
            o.getServiceType(), o.getPrimaryVendorId(), o.getCoVendorIds(),
            o.getIsMaterial(), o.getMaterialityBasisEn(), o.getMaterialityBasisAr(),
            o.getSamaNonObjectionRequired(), o.getSamaNonObjectionObtained(),
            o.getSamaNonObjectionRef(), o.getSamaNonObjectionDate(),
            o.getContractStartDate(), o.getContractEndDate(),
            o.getContractValueSarAnnual(), o.getContractRef(), o.getContractDocumentUrl(),
            o.getCriticalFunction(), o.getCountryOfProcessing(), o.getRegionOfProcessing(),
            o.getDataResidencyKdc(), o.getPersonalDataProcessed(),
            o.getPdplDpaSigned(), o.getPdplDpaDate(), o.getCrossBorderSafeguardType(),
            o.getLifecycleStage(), o.getOwnerUserId(), o.getSponsorUserId(),
            o.getNextReviewDueDate(),
            o.getCreatedAt(), o.getUpdatedAt()
        );
    }
}
