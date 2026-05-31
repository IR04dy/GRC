package com.tss.grc.module.outsourcing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.outsourcing.dto.OutsourcingDtos.*;
import com.tss.grc.module.outsourcing.entity.*;
import com.tss.grc.module.outsourcing.repository.*;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Outsourcing &amp; Cloud Governance service — Sprint 30 PR #1.
 *
 * <p>State machines:
 * <pre>
 *   Arrangement: PROPOSED → RISK_ASSESSED → BOARD_APPROVED → SAMA_NOTIFIED
 *                → LIVE → UNDER_REVIEW → EXITED
 *                ↘ REJECTED
 *
 *   CloudService: ONBOARDING → CLASSIFIED → APPROVED → IN_USE → DEPRECATED
 *                  → DECOMMISSIONED
 *                  ↘ BLOCKED
 *
 *   Review:       SCHEDULED → IN_PROGRESS → COMPLETED|WITH_FINDINGS|ESCALATED
 *
 *   ExitPlan:     DRAFT → APPROVED → REHEARSED → READY → EXECUTED
 *                          ↘ REJECTED
 * </pre>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OutsourcingService {

    private static final Map<String, Set<String>> ARRANGEMENT_ALLOWED = Map.ofEntries(
            Map.entry("PROPOSED",       Set.of("RISK_ASSESSED", "REJECTED")),
            Map.entry("RISK_ASSESSED",  Set.of("BOARD_APPROVED", "REJECTED")),
            Map.entry("BOARD_APPROVED", Set.of("SAMA_NOTIFIED", "LIVE")),
            Map.entry("SAMA_NOTIFIED",  Set.of("LIVE")),
            Map.entry("LIVE",           Set.of("UNDER_REVIEW", "SUSPENDED", "EXITED")),
            Map.entry("UNDER_REVIEW",   Set.of("LIVE", "SUSPENDED", "EXITED")),
            Map.entry("SUSPENDED",      Set.of("LIVE", "EXITED")),
            Map.entry("EXITED",         Set.of()),
            Map.entry("REJECTED",       Set.of())
    );

    private static final Map<String, Set<String>> CLOUD_ALLOWED = Map.of(
            "ONBOARDING",     Set.of("CLASSIFIED"),
            "CLASSIFIED",     Set.of("APPROVED", "BLOCKED"),
            "APPROVED",       Set.of("IN_USE"),
            "IN_USE",         Set.of("DEPRECATED", "DECOMMISSIONED"),
            "DEPRECATED",     Set.of("DECOMMISSIONED"),
            "DECOMMISSIONED", Set.of(),
            "BLOCKED",        Set.of("CLASSIFIED")
    );

    private static final Map<String, Set<String>> REVIEW_ALLOWED = Map.of(
            "SCHEDULED",      Set.of("IN_PROGRESS"),
            "IN_PROGRESS",    Set.of("COMPLETED", "WITH_FINDINGS", "ESCALATED"),
            "COMPLETED",      Set.of(),
            "WITH_FINDINGS",  Set.of("COMPLETED"),
            "ESCALATED",      Set.of("WITH_FINDINGS", "COMPLETED")
    );

    private static final Map<String, Set<String>> EXIT_ALLOWED = Map.of(
            "DRAFT",      Set.of("APPROVED", "REJECTED"),
            "APPROVED",   Set.of("REHEARSED"),
            "REHEARSED",  Set.of("READY"),
            "READY",      Set.of("EXECUTED"),
            "EXECUTED",   Set.of(),
            "REJECTED",   Set.of()
    );

    private final OutsourcingArrangementRepository arrangementRepo;
    private final CloudServiceRepository cloudRepo;
    private final OngoingReviewRepository reviewRepo;
    private final ExitPlanRepository exitRepo;
    private final OutsourcingEventRepository eventRepo;
    private final ApplicationEventPublisher publisher;

    // ───────────────────────────────────────── Arrangement CRUD/transition

    public ArrangementResponse createArrangement(CreateArrangementRequest req) {
        Long o = org();
        if (arrangementRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent()) {
            throw new IllegalStateException("Arrangement " + req.reference() + " already exists");
        }
        OutsourcingArrangement a = new OutsourcingArrangement();
        a.setOrganisationId(o);
        a.setReference(req.reference());
        a.setProviderLegalNameEn(req.providerLegalNameEn());
        a.setProviderLegalNameAr(req.providerLegalNameAr());
        a.setProviderCountry(req.providerCountry());
        a.setProviderJurisdiction(req.providerJurisdiction());
        a.setServiceTitleEn(req.serviceTitleEn());
        a.setServiceTitleAr(req.serviceTitleAr());
        a.setServiceDescriptionEn(req.serviceDescriptionEn());
        a.setServiceDescriptionAr(req.serviceDescriptionAr());
        a.setServiceCategory(req.serviceCategory());
        a.setMateriality(req.materiality());
        a.setIsCoreActivity(Boolean.TRUE.equals(req.isCoreActivity()));
        a.setStatus("PROPOSED");
        a.setContractStart(req.contractStart());
        a.setContractEnd(req.contractEnd());
        a.setRenewalNoticeDays(req.renewalNoticeDays());
        a.setAnnualSpendSar(req.annualSpendSar());
        a.setReviewCadenceMonths(req.reviewCadenceMonths());
        a.setRelationshipOwnerUserId(req.relationshipOwnerUserId());
        a.setRiskOwnerUserId(req.riskOwnerUserId());
        a.setLinkedVendorId(req.linkedVendorId());
        arrangementRepo.save(a);
        timeline(a.getId(), null, null, null, "ARRANGEMENT_REGISTERED", "PROPOSED",
                "Outsourcing arrangement registered.",
                "تم تسجيل ترتيب الإسناد.");
        emit("OUTSOURCING_ARRANGEMENT_REGISTERED", "OUTSOURCING_ARRANGEMENT", a.getId(),
                Map.of("reference", a.getReference(), "materiality", a.getMateriality(),
                        "isCoreActivity", a.getIsCoreActivity()));
        return toArrangement(a);
    }

    public ArrangementResponse transitionArrangement(Long id, TransitionRequest req) {
        OutsourcingArrangement a = loadArrangement(id);
        validate(ARRANGEMENT_ALLOWED, a.getStatus(), req.toStatus(), "Arrangement");
        // SAMA §5 gate: core activity requires non-objection ref before LIVE
        if ("LIVE".equals(req.toStatus())
                && Boolean.TRUE.equals(a.getIsCoreActivity())
                && (a.getSamaNonObjectionRef() == null
                    && (req.samaNonObjectionRef() == null || req.samaNonObjectionRef().isBlank()))) {
            throw new IllegalStateException(
                "Core-activity outsourcing requires SAMA non-objection letter before LIVE.");
        }
        LocalDateTime now = LocalDateTime.now();
        String from = a.getStatus();
        a.setStatus(req.toStatus());
        switch (req.toStatus()) {
            case "RISK_ASSESSED" -> {
                a.setRiskAssessedAt(now);
                if (req.riskRating() != null) a.setRiskRating(req.riskRating());
            }
            case "BOARD_APPROVED" -> a.setBoardApprovedAt(now);
            case "SAMA_NOTIFIED" -> {
                a.setSamaNotifiedAt(now);
                if (req.samaNonObjectionRef() != null) a.setSamaNonObjectionRef(req.samaNonObjectionRef());
            }
            case "LIVE" -> {
                a.setLiveAt(LocalDate.now());
                if (req.samaNonObjectionRef() != null) a.setSamaNonObjectionRef(req.samaNonObjectionRef());
                if (a.getNextReviewDue() == null && a.getReviewCadenceMonths() != null) {
                    a.setNextReviewDue(LocalDate.now().plusMonths(a.getReviewCadenceMonths()));
                }
            }
            case "SUSPENDED" -> a.setSuspendedAt(LocalDateTime.now());
            case "EXITED" -> a.setExitedAt(LocalDate.now());
            case "REJECTED" -> {
                a.setRejectedAt(now);
                a.setRejectionReasonEn(req.reasonEn());
                a.setRejectionReasonAr(req.reasonAr());
            }
            default -> { /* no-op */ }
        }
        arrangementRepo.save(a);
        timeline(a.getId(), null, null, null, "ARRANGEMENT_TRANSITIONED_" + req.toStatus(),
                req.toStatus(), req.reasonEn(), req.reasonAr());
        emit("OUTSOURCING_" + req.toStatus(), "OUTSOURCING_ARRANGEMENT", a.getId(),
                Map.of("reference", a.getReference(),
                        "from", from, "to", req.toStatus(),
                        "materiality", a.getMateriality(),
                        "isCoreActivity", a.getIsCoreActivity()));
        return toArrangement(a);
    }

    public List<ArrangementResponse> listArrangements() {
        return arrangementRepo.findByOrganisationIdOrderByCreatedAtDesc(org())
                .stream().map(OutsourcingService::toArrangement).toList();
    }

    public ArrangementResponse getArrangement(Long id) {
        return toArrangement(loadArrangement(id));
    }

    // ─────────────────────────────────────────────── Cloud Service flows

    public CloudServiceResponse createCloudService(CreateCloudServiceRequest req) {
        Long o = org();
        loadArrangement(req.arrangementId());            // tenant validation
        if (cloudRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent()) {
            throw new IllegalStateException("CloudService " + req.reference() + " already exists");
        }
        CloudService c = new CloudService();
        c.setOrganisationId(o);
        c.setArrangementId(req.arrangementId());
        c.setReference(req.reference());
        c.setServiceNameEn(req.serviceNameEn());
        c.setServiceNameAr(req.serviceNameAr());
        c.setDeploymentModel(req.deploymentModel());
        c.setServiceModel(req.serviceModel());
        c.setCspLegalNameEn(req.cspLegalNameEn());
        c.setCspLegalNameAr(req.cspLegalNameAr());
        c.setCspBrand(req.cspBrand());
        c.setDataClassification(req.dataClassification());
        c.setDataResidency(req.dataResidency());
        c.setCspRegionLabel(req.cspRegionLabel());
        c.setApproverRole(req.approverRole());
        c.setSupportsPdplDpiaRef(req.supportsPdplDpiaRef());
        c.setStatus("ONBOARDING");
        cloudRepo.save(c);
        timeline(c.getArrangementId(), c.getId(), null, null,
                "CLOUD_SERVICE_REGISTERED", "ONBOARDING",
                "Cloud service registered.",
                "تم تسجيل الخدمة السحابية.");
        emit("CLOUD_SERVICE_REGISTERED", "CLOUD_SERVICE", c.getId(),
                Map.of("reference", c.getReference(), "cspBrand",
                        c.getCspBrand() == null ? "" : c.getCspBrand()));
        return toCloud(c);
    }

    public CloudServiceResponse classifyCloudService(Long id, ClassifyCloudServiceRequest req) {
        CloudService c = loadCloud(id);
        if (!"ONBOARDING".equals(c.getStatus()) && !"BLOCKED".equals(c.getStatus())) {
            throw new IllegalStateException(
                "Cloud service must be in ONBOARDING or BLOCKED to be (re)classified.");
        }
        c.setDataClassification(req.dataClassification());
        c.setDataResidency(req.dataResidency());
        c.setCspRegionLabel(req.cspRegionLabel());
        c.setStatus("CLASSIFIED");
        c.setClassifiedAt(LocalDateTime.now());
        cloudRepo.save(c);
        timeline(c.getArrangementId(), c.getId(), null, null,
                "CLOUD_SERVICE_CLASSIFIED", "CLASSIFIED",
                "Data classification " + req.dataClassification() + " / residency " + req.dataResidency() + ".",
                "تصنيف البيانات " + req.dataClassification() + " / مقرّ المعالجة " + req.dataResidency() + ".");
        emit("CLOUD_SERVICE_CLASSIFIED", "CLOUD_SERVICE", c.getId(),
                Map.of("classification", req.dataClassification(),
                        "residency", req.dataResidency()));
        return toCloud(c);
    }

    public CloudServiceResponse transitionCloudService(Long id, CloudServiceTransitionRequest req) {
        CloudService c = loadCloud(id);
        validate(CLOUD_ALLOWED, c.getStatus(), req.toStatus(), "CloudService");
        // SAMA Cloud §5 residency gate at APPROVED promotion
        if ("APPROVED".equals(req.toStatus())) {
            String cls = c.getDataClassification();
            String res = c.getDataResidency();
            boolean ok =
                ("T1".equals(cls)) ||
                ("T2".equals(cls) && ("IN_KINGDOM".equals(res) || "GCC".equals(res))) ||
                (("T3".equals(cls) || "T4".equals(cls)) && "IN_KINGDOM".equals(res));
            if (!ok) {
                // Force-block + emit residency violation rather than fail open.
                c.setStatus("BLOCKED");
                c.setBlockReasonEn("Residency " + res + " incompatible with classification " + cls + ".");
                c.setBlockReasonAr("مقرّ المعالجة " + res + " غير متوافق مع التصنيف " + cls + ".");
                cloudRepo.save(c);
                timeline(c.getArrangementId(), c.getId(), null, null,
                        "CLOUD_SERVICE_BLOCKED", "BLOCKED",
                        c.getBlockReasonEn(), c.getBlockReasonAr());
                emit("CLOUD_SERVICE_RESIDENCY_VIOLATION", "CLOUD_SERVICE", c.getId(),
                        Map.of("classification", cls == null ? "" : cls,
                                "residency", res == null ? "" : res));
                return toCloud(c);
            }
        }
        c.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "APPROVED" -> {
                c.setApprovedAt(now);
                if (req.approverRole() != null) c.setApproverRole(req.approverRole());
            }
            case "IN_USE" -> c.setInUseAt(LocalDate.now());
            case "DEPRECATED" -> c.setDeprecatedAt(LocalDate.now());
            case "DECOMMISSIONED" -> c.setDecommissionedAt(LocalDate.now());
            case "BLOCKED" -> {
                c.setBlockReasonEn(req.blockReasonEn());
                c.setBlockReasonAr(req.blockReasonAr());
            }
            default -> { /* no-op */ }
        }
        cloudRepo.save(c);
        timeline(c.getArrangementId(), c.getId(), null, null,
                "CLOUD_SERVICE_" + req.toStatus(), req.toStatus(),
                req.blockReasonEn(), req.blockReasonAr());
        emit("CLOUD_SERVICE_" + req.toStatus(), "CLOUD_SERVICE", c.getId(),
                Map.of("reference", c.getReference(),
                        "classification", c.getDataClassification() == null ? "" : c.getDataClassification(),
                        "residency", c.getDataResidency() == null ? "" : c.getDataResidency()));
        return toCloud(c);
    }

    public List<CloudServiceResponse> listCloudServices(Long arrangementId) {
        if (arrangementId != null) {
            return cloudRepo.findByOrganisationIdAndArrangementId(org(), arrangementId)
                    .stream().map(OutsourcingService::toCloud).toList();
        }
        return cloudRepo.findByOrganisationIdOrderByCreatedAtDesc(org())
                .stream().map(OutsourcingService::toCloud).toList();
    }

    // ────────────────────────────────────────────────── Ongoing reviews

    public ReviewResponse createReview(CreateReviewRequest req) {
        loadArrangement(req.arrangementId());
        OngoingReview r = new OngoingReview();
        r.setOrganisationId(org());
        r.setArrangementId(req.arrangementId());
        r.setReference(req.reference());
        r.setReviewPeriodLabel(req.reviewPeriodLabel());
        r.setDueDate(req.dueDate());
        r.setReviewerUserId(req.reviewerUserId());
        r.setStatus("SCHEDULED");
        reviewRepo.save(r);
        timeline(req.arrangementId(), null, r.getId(), null,
                "REVIEW_SCHEDULED", "SCHEDULED",
                "Ongoing-review scheduled for " + req.dueDate() + ".",
                "تم جدولة المراجعة الدورية لتاريخ " + req.dueDate() + ".");
        return toReview(r);
    }

    public ReviewResponse transitionReview(Long id, TransitionReviewRequest req) {
        OngoingReview r = loadReview(id);
        validate(REVIEW_ALLOWED, r.getStatus(), req.toStatus(), "Review");
        r.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "IN_PROGRESS" -> r.setStartedAt(now);
            case "COMPLETED", "WITH_FINDINGS", "ESCALATED" -> {
                r.setCompletedAt(now);
                if (req.outcomeRag() != null) r.setOutcomeRag(req.outcomeRag());
                if (req.findingsSummaryEn() != null) r.setFindingsSummaryEn(req.findingsSummaryEn());
                if (req.findingsSummaryAr() != null) r.setFindingsSummaryAr(req.findingsSummaryAr());
                if (req.linkedCapaId() != null) r.setLinkedCapaId(req.linkedCapaId());
                // Bump arrangement's nextReviewDue
                OutsourcingArrangement a = arrangementRepo.findById(r.getArrangementId()).orElse(null);
                if (a != null && a.getReviewCadenceMonths() != null) {
                    a.setNextReviewDue(LocalDate.now().plusMonths(a.getReviewCadenceMonths()));
                    arrangementRepo.save(a);
                }
            }
            default -> { /* no-op */ }
        }
        reviewRepo.save(r);
        timeline(r.getArrangementId(), null, r.getId(), null,
                "REVIEW_" + req.toStatus(), req.toStatus(),
                req.findingsSummaryEn(), req.findingsSummaryAr());
        if ("WITH_FINDINGS".equals(req.toStatus()) || "ESCALATED".equals(req.toStatus())) {
            emit("OUTSOURCING_REVIEW_" + req.toStatus(), "OUTSOURCING_REVIEW", r.getId(),
                    Map.of("arrangementId", r.getArrangementId(),
                            "rag", r.getOutcomeRag() == null ? "" : r.getOutcomeRag()));
        }
        return toReview(r);
    }

    public List<ReviewResponse> listReviewsByArrangement(Long arrangementId) {
        return reviewRepo.findByOrganisationIdAndArrangementIdOrderByCreatedAtDesc(org(), arrangementId)
                .stream().map(OutsourcingService::toReview).toList();
    }

    // ──────────────────────────────────────────────────────── Exit plan

    public ExitPlanResponse createExitPlan(CreateExitPlanRequest req) {
        loadArrangement(req.arrangementId());
        ExitPlan p = new ExitPlan();
        p.setOrganisationId(org());
        p.setArrangementId(req.arrangementId());
        p.setReference(req.reference());
        p.setSummaryEn(req.summaryEn());
        p.setSummaryAr(req.summaryAr());
        p.setTargetMigrationDays(req.targetMigrationDays());
        p.setAlternativeProviderEn(req.alternativeProviderEn());
        p.setAlternativeProviderAr(req.alternativeProviderAr());
        p.setStatus("DRAFT");
        exitRepo.save(p);
        timeline(req.arrangementId(), null, null, p.getId(),
                "EXIT_PLAN_DRAFT", "DRAFT",
                "Exit plan drafted.",
                "تم إعداد مسوّدة خطة الخروج.");
        return toExit(p);
    }

    public ExitPlanResponse transitionExitPlan(Long id, ExitPlanTransitionRequest req) {
        ExitPlan p = loadExit(id);
        validate(EXIT_ALLOWED, p.getStatus(), req.toStatus(), "ExitPlan");
        p.setStatus(req.toStatus());
        switch (req.toStatus()) {
            case "APPROVED" -> p.setApprovedAt(LocalDateTime.now());
            case "REHEARSED" -> p.setRehearsedAt(LocalDate.now());
            case "EXECUTED" -> {
                p.setExecutedAt(LocalDate.now());
                // Cascade: parent arrangement → EXITED
                OutsourcingArrangement a = arrangementRepo.findById(p.getArrangementId()).orElse(null);
                if (a != null && !"EXITED".equals(a.getStatus())) {
                    a.setStatus("EXITED");
                    a.setExitedAt(LocalDate.now());
                    arrangementRepo.save(a);
                    emit("OUTSOURCING_EXIT_EXECUTED", "OUTSOURCING_ARRANGEMENT",
                            a.getId(), Map.of("exitPlanId", p.getId()));
                }
            }
            case "REJECTED" -> {
                p.setRejectionReasonEn(req.rejectionReasonEn());
                p.setRejectionReasonAr(req.rejectionReasonAr());
            }
            default -> { /* no-op */ }
        }
        exitRepo.save(p);
        timeline(p.getArrangementId(), null, null, p.getId(),
                "EXIT_PLAN_" + req.toStatus(), req.toStatus(),
                req.rejectionReasonEn(), req.rejectionReasonAr());
        return toExit(p);
    }

    public List<ExitPlanResponse> listExitPlansByArrangement(Long arrangementId) {
        return exitRepo.findByOrganisationIdAndArrangementIdOrderByCreatedAtDesc(org(), arrangementId)
                .stream().map(OutsourcingService::toExit).toList();
    }

    // ───────────────────────────────────────────────────────── Timeline

    public List<EventResponse> arrangementTimeline(Long arrangementId) {
        loadArrangement(arrangementId);
        return eventRepo.findByOrganisationIdAndArrangementIdOrderByOccurredAtDesc(org(), arrangementId)
                .stream().map(OutsourcingService::toEvent).toList();
    }

    public List<EventResponse> cloudServiceTimeline(Long cloudId) {
        loadCloud(cloudId);
        return eventRepo.findByOrganisationIdAndCloudServiceIdOrderByOccurredAtDesc(org(), cloudId)
                .stream().map(OutsourcingService::toEvent).toList();
    }

    // ──────────────────────────────────────────────────────── Dashboard

    public DashboardResponse dashboard() {
        Long o = org();
        List<OutsourcingArrangement> all = arrangementRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        Map<String, Long> arrByStatus = all.stream()
                .collect(Collectors.groupingBy(OutsourcingArrangement::getStatus, Collectors.counting()));

        long arrTotal = all.size();
        long materialActive = arrangementRepo.findActiveMaterial(o).size();
        long live = arrByStatus.getOrDefault("LIVE", 0L);
        long underReview = arrByStatus.getOrDefault("UNDER_REVIEW", 0L);

        List<CloudService> allCloud = cloudRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        long cloudTotal = allCloud.size();
        long cloudT3T4 = allCloud.stream()
                .filter(c -> "T3".equals(c.getDataClassification())
                        || "T4".equals(c.getDataClassification())).count();
        long cloudBlocked = cloudRepo.countByOrganisationIdAndStatus(o, "BLOCKED");

        long reviewsOverdue = arrangementRepo.findReviewsDue(o, LocalDate.now()).size();
        long exitPlansReady = exitRepo.countByOrganisationIdAndStatus(o, "READY");

        Map<String, Long> cloudByClass = allCloud.stream()
                .filter(c -> c.getDataClassification() != null)
                .collect(Collectors.groupingBy(CloudService::getDataClassification, Collectors.counting()));
        Map<String, Long> cloudByResidency = allCloud.stream()
                .filter(c -> c.getDataResidency() != null)
                .collect(Collectors.groupingBy(CloudService::getDataResidency, Collectors.counting()));

        List<ArrangementResponse> recent = all.stream()
                .limit(8).map(OutsourcingService::toArrangement).toList();
        List<ArrangementResponse> overdue = arrangementRepo.findReviewsDue(o, LocalDate.now())
                .stream().limit(10).map(OutsourcingService::toArrangement).toList();

        return new DashboardResponse(
                arrTotal, materialActive, live, underReview,
                cloudTotal, cloudT3T4, cloudBlocked,
                reviewsOverdue, exitPlansReady,
                arrByStatus, cloudByClass, cloudByResidency,
                recent, overdue);
    }

    // ─────────────────────────────────────────────────────── Internals

    private OutsourcingArrangement loadArrangement(Long id) {
        return arrangementRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Arrangement " + id + " not found"));
    }

    private CloudService loadCloud(Long id) {
        return cloudRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("CloudService " + id + " not found"));
    }

    private OngoingReview loadReview(Long id) {
        return reviewRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Review " + id + " not found"));
    }

    private ExitPlan loadExit(Long id) {
        return exitRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("ExitPlan " + id + " not found"));
    }

    private static void validate(Map<String, Set<String>> allowed, String from, String to, String label) {
        Set<String> next = allowed.getOrDefault(from, Set.of());
        if (!next.contains(to)) {
            throw new IllegalStateException(label + " cannot transition " + from + " → " + to);
        }
    }

    private void timeline(Long arrangementId, Long cloudId, Long reviewId, Long exitPlanId,
                          String eventType, String statusAfter,
                          String noteEn, String noteAr) {
        OutsourcingEvent e = new OutsourcingEvent();
        e.setOrganisationId(org());
        e.setArrangementId(arrangementId);
        e.setCloudServiceId(cloudId);
        e.setReviewId(reviewId);
        e.setExitPlanId(exitPlanId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setStatusAfter(statusAfter);
        e.setNoteEn(noteEn);
        e.setNoteAr(noteAr);
        eventRepo.save(e);
    }

    private void emit(String trigger, String entityType, Long entityId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, null,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof GrcUserDetails u)) return null;
        return u.getUserId();
    }

    // ────────────────────────────────────────────────────────── Mappers

    private static ArrangementResponse toArrangement(OutsourcingArrangement a) {
        return new ArrangementResponse(
                a.getId(), a.getOrganisationId(), a.getReference(),
                a.getProviderLegalNameEn(), a.getProviderLegalNameAr(),
                a.getProviderCountry(), a.getProviderJurisdiction(),
                a.getServiceTitleEn(), a.getServiceTitleAr(),
                a.getServiceDescriptionEn(), a.getServiceDescriptionAr(),
                a.getServiceCategory(), a.getMateriality(), a.getIsCoreActivity(),
                a.getStatus(), a.getRiskRating(),
                a.getRiskAssessedAt(), a.getBoardApprovedAt(),
                a.getSamaNotifiedAt(), a.getSamaNonObjectionRef(),
                a.getLiveAt(), a.getExitedAt(), a.getSuspendedAt(), a.getRejectedAt(),
                a.getRejectionReasonEn(), a.getRejectionReasonAr(),
                a.getContractStart(), a.getContractEnd(),
                a.getRenewalNoticeDays(), a.getAnnualSpendSar(),
                a.getNextReviewDue(), a.getReviewCadenceMonths(),
                a.getRelationshipOwnerUserId(), a.getRiskOwnerUserId(), a.getLinkedVendorId());
    }

    private static CloudServiceResponse toCloud(CloudService c) {
        return new CloudServiceResponse(
                c.getId(), c.getOrganisationId(), c.getArrangementId(), c.getReference(),
                c.getServiceNameEn(), c.getServiceNameAr(),
                c.getDeploymentModel(), c.getServiceModel(),
                c.getCspLegalNameEn(), c.getCspLegalNameAr(), c.getCspBrand(),
                c.getDataClassification(), c.getDataResidency(), c.getCspRegionLabel(),
                c.getStatus(),
                c.getClassifiedAt(), c.getApprovedAt(),
                c.getInUseAt(), c.getDeprecatedAt(), c.getDecommissionedAt(),
                c.getBlockReasonEn(), c.getBlockReasonAr(),
                c.getApproverRole(), c.getSupportsPdplDpiaRef());
    }

    private static ReviewResponse toReview(OngoingReview r) {
        return new ReviewResponse(
                r.getId(), r.getOrganisationId(), r.getArrangementId(), r.getReference(),
                r.getReviewPeriodLabel(), r.getStatus(), r.getDueDate(),
                r.getStartedAt(), r.getCompletedAt(),
                r.getOutcomeRag(), r.getFindingsSummaryEn(), r.getFindingsSummaryAr(),
                r.getLinkedCapaId(), r.getReviewerUserId());
    }

    private static ExitPlanResponse toExit(ExitPlan p) {
        return new ExitPlanResponse(
                p.getId(), p.getOrganisationId(), p.getArrangementId(), p.getReference(),
                p.getStatus(), p.getSummaryEn(), p.getSummaryAr(),
                p.getTargetMigrationDays(),
                p.getAlternativeProviderEn(), p.getAlternativeProviderAr(),
                p.getApprovedAt(), p.getRehearsedAt(), p.getExecutedAt(),
                p.getRejectionReasonEn(), p.getRejectionReasonAr());
    }

    private static EventResponse toEvent(OutsourcingEvent e) {
        return new EventResponse(
                e.getId(), e.getArrangementId(), e.getCloudServiceId(),
                e.getReviewId(), e.getExitPlanId(),
                e.getOccurredAt(), e.getEventType(),
                e.getActorName(), e.getStatusAfter(),
                e.getNoteEn(), e.getNoteAr());
    }
}
