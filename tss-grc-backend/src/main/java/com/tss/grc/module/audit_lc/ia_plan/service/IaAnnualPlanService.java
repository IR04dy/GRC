package com.tss.grc.module.audit_lc.ia_plan.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanDashboardResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanTransitionRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAnnualPlan;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaEngagement;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAnnualPlanRepository;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAuditUniverseEntryRepository;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaEngagementRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 13 — Internal Audit Annual Plan service.
 *
 * <p>Owns the plan lifecycle:
 * {@code DRAFT → BOARD_REVIEW → APPROVED → EXECUTING → COMPLETED → ARCHIVED}.
 *
 * <p>Transition gates:
 * <ul>
 *   <li>{@code DRAFT → BOARD_REVIEW} requires both methodology summary
 *       fields populated. Cannot send an empty plan to the board.</li>
 *   <li>{@code BOARD_REVIEW → APPROVED} requires {@code approverUserId}.
 *       Records the approver and timestamp on the plan row.</li>
 *   <li>{@code APPROVED → EXECUTING} is automatic-ish; the CAE asserts
 *       readiness.</li>
 *   <li>{@code EXECUTING → COMPLETED} can only be performed once all
 *       non-CANCELLED engagements are CLOSED.</li>
 *   <li>{@code COMPLETED → ARCHIVED} — final shelf state.</li>
 * </ul>
 *
 * <p>F8 events emitted: {@code IA_PLAN_CREATED}, {@code IA_PLAN_SUBMITTED}
 * (DRAFT→BOARD_REVIEW), {@code IA_PLAN_APPROVED}, {@code IA_PLAN_EXECUTING},
 * {@code IA_PLAN_COMPLETED}, {@code IA_PLAN_ARCHIVED}.
 */
@Service
@RequiredArgsConstructor
public class IaAnnualPlanService {

    private static final Logger log = LoggerFactory.getLogger(IaAnnualPlanService.class);

    /** Allowed forward transitions for the plan lifecycle. */
    static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "DRAFT",        Set.of("BOARD_REVIEW", "ARCHIVED"),
        "BOARD_REVIEW", Set.of("DRAFT", "APPROVED", "ARCHIVED"),
        "APPROVED",     Set.of("EXECUTING", "ARCHIVED"),
        "EXECUTING",    Set.of("COMPLETED", "ARCHIVED"),
        "COMPLETED",    Set.of("ARCHIVED"),
        "ARCHIVED",     Set.of()
    );

    private final IaAnnualPlanRepository planRepo;
    private final IaAuditUniverseEntryRepository universeRepo;
    private final IaEngagementRepository engagementRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlanResponse> list() {
        Long orgId = org();
        return planRepo.findByOrganisationIdAndIsDeletedFalseOrderByCycleYearDesc(orgId)
            .stream().map(IaAnnualPlanService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PlanDashboardResponse dashboard(Long planId) {
        Long orgId = org();
        IaAnnualPlan p = loadOrThrow(planId);

        long planned     = engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(orgId, p.getId(), "PLANNED");
        long planning    = engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(orgId, p.getId(), "PLANNING");
        long fieldwork   = engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(orgId, p.getId(), "FIELDWORK");
        long reporting   = engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(orgId, p.getId(), "REPORTING");
        long reportIssued = engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(orgId, p.getId(), "REPORT_ISSUED");
        long followUp    = engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(orgId, p.getId(), "FOLLOW_UP");
        long closed      = engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(orgId, p.getId(), "CLOSED");
        long cancelled   = engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(orgId, p.getId(), "CANCELLED");

        // On-time percentage: of engagements that have a planned_end_date
        // in the past or are CLOSED/REPORT_ISSUED, how many ended on or
        // before their planned date. Returns null if there's nothing to
        // measure yet.
        Double onTime = computeOnTimePercentage(orgId, p.getId());

        return new PlanDashboardResponse(
            p.getId(), p.getPlanCode(), p.getCycleYear(), p.getStatus(),
            p.getAuditUniverseCount(), p.getTotalEngagementsPlanned(),
            planned, planning, fieldwork, reporting,
            reportIssued, followUp, closed, cancelled, onTime
        );
    }

    Double computeOnTimePercentage(Long orgId, Long planId) {
        List<IaEngagement> engs = engagementRepo
            .findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEngagementCodeAsc(orgId, planId);
        int measurable = 0;
        int onTime = 0;
        for (IaEngagement e : engs) {
            if ("CANCELLED".equals(e.getPhase())) continue;
            if (e.getPlannedEndDate() == null) continue;
            // Engagements that finished (CLOSED/REPORT_ISSUED) — judge on actual_end_date
            if ("CLOSED".equals(e.getPhase()) || "REPORT_ISSUED".equals(e.getPhase()) || "FOLLOW_UP".equals(e.getPhase())) {
                measurable++;
                if (e.getActualEndDate() != null && !e.getActualEndDate().isAfter(e.getPlannedEndDate())) {
                    onTime++;
                } else if (e.getActualEndDate() == null && !LocalDate.now().isAfter(e.getPlannedEndDate())) {
                    // Phase moved out of REPORTING but actual_end_date not set yet — give benefit of the doubt
                    onTime++;
                }
            }
        }
        if (measurable == 0) return null;
        return (onTime * 100.0) / measurable;
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public PlanResponse create(PlanCreateRequest req) {
        Long orgId = org();
        String code = req.planCode() != null && !req.planCode().isBlank()
            ? req.planCode().trim()
            : "IAP-" + req.cycleYear();
        if (planRepo.existsByOrganisationIdAndPlanCodeAndIsDeletedFalse(orgId, code)) {
            throw new IllegalArgumentException("Plan code already exists: " + code);
        }

        IaAnnualPlan p = new IaAnnualPlan();
        p.setOrganisationId(orgId);
        p.setPlanCode(code);
        p.setCycleYear(req.cycleYear());
        p.setTitleEn(req.titleEn());
        p.setTitleAr(req.titleAr());
        p.setPreparedByUserId(req.preparedByUserId());
        p.setStatus("DRAFT");
        p.setMethodologySummaryEn(req.methodologySummaryEn());
        p.setMethodologySummaryAr(req.methodologySummaryAr());
        IaAnnualPlan saved = planRepo.save(p);

        audit.log("CREATE", "IaAnnualPlan", saved.getId(),
            "Annual plan created: " + saved.getPlanCode() + " (cycle " + saved.getCycleYear() + ")");
        emit(saved, "IA_PLAN_CREATED", null);
        return toResponse(saved);
    }

    @Transactional
    public PlanResponse update(Long id, PlanUpdateRequest req) {
        IaAnnualPlan p = loadOrThrow(id);
        if ("APPROVED".equals(p.getStatus()) || "EXECUTING".equals(p.getStatus())
            || "COMPLETED".equals(p.getStatus()) || "ARCHIVED".equals(p.getStatus())) {
            throw new IllegalArgumentException(
                "Plan in status " + p.getStatus() + " cannot be edited (only DRAFT/BOARD_REVIEW)");
        }
        if (req.titleEn() != null) p.setTitleEn(req.titleEn());
        if (req.titleAr() != null) p.setTitleAr(req.titleAr());
        if (req.preparedByUserId() != null) p.setPreparedByUserId(req.preparedByUserId());
        if (req.methodologySummaryEn() != null) p.setMethodologySummaryEn(req.methodologySummaryEn());
        if (req.methodologySummaryAr() != null) p.setMethodologySummaryAr(req.methodologySummaryAr());
        p.setUpdatedAt(LocalDateTime.now());
        IaAnnualPlan saved = planRepo.save(p);

        audit.log("UPDATE", "IaAnnualPlan", saved.getId(),
            "Annual plan updated: " + saved.getPlanCode());
        return toResponse(saved);
    }

    @Transactional
    public PlanResponse transition(Long id, PlanTransitionRequest req) {
        Long orgId = org();
        IaAnnualPlan p = loadOrThrow(id);
        String from = p.getStatus();
        String to = req.newStatus();

        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }

        // Gate-specific validations
        if ("BOARD_REVIEW".equals(to)) {
            if (isBlank(p.getMethodologySummaryEn()) || isBlank(p.getMethodologySummaryAr())) {
                throw new IllegalArgumentException(
                    "methodologySummary (both EN+AR) is required before submitting to BOARD_REVIEW");
            }
        }
        if ("APPROVED".equals(to)) {
            if (req.approverUserId() == null) {
                throw new IllegalArgumentException("approverUserId is required to transition to APPROVED");
            }
            p.setApprovedByUserId(req.approverUserId());
            p.setApprovedAt(LocalDateTime.now());
        }
        if ("COMPLETED".equals(to)) {
            // Cannot complete a plan with engagements still in-flight
            List<IaEngagement> engs = engagementRepo
                .findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEngagementCodeAsc(orgId, p.getId());
            long openCount = engs.stream()
                .filter(e -> !"CLOSED".equals(e.getPhase()) && !"CANCELLED".equals(e.getPhase()))
                .count();
            if (openCount > 0) {
                throw new IllegalArgumentException(
                    "Cannot mark plan COMPLETED — " + openCount + " engagement(s) still open");
            }
        }

        p.setStatus(to);
        p.setUpdatedAt(LocalDateTime.now());
        IaAnnualPlan saved = planRepo.save(p);

        audit.log("TRANSITION", "IaAnnualPlan", saved.getId(),
            "Plan " + saved.getPlanCode() + " transitioned " + from + " → " + to
                + (req.approverUserId() != null ? " (approver=" + req.approverUserId() + ")" : ""));

        String trigger = switch (to) {
            case "BOARD_REVIEW" -> "IA_PLAN_SUBMITTED";
            case "APPROVED"     -> "IA_PLAN_APPROVED";
            case "EXECUTING"    -> "IA_PLAN_EXECUTING";
            case "COMPLETED"    -> "IA_PLAN_COMPLETED";
            case "ARCHIVED"     -> "IA_PLAN_ARCHIVED";
            default             -> "IA_PLAN_TRANSITION";
        };
        emit(saved, trigger, Map.of("fromStatus", from, "toStatus", to));
        return toResponse(saved);
    }

    @Transactional
    public void softDelete(Long id) {
        IaAnnualPlan p = loadOrThrow(id);
        p.setIsDeleted(true);
        p.setDeletedAt(LocalDateTime.now());
        planRepo.save(p);
        audit.log("DELETE", "IaAnnualPlan", p.getId(),
            "Annual plan soft-deleted: " + p.getPlanCode());
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private IaAnnualPlan loadOrThrow(Long id) {
        return planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("IA annual plan not found: " + id));
    }

    static PlanResponse toResponse(IaAnnualPlan p) {
        return new PlanResponse(
            p.getId(), p.getExternalId(), p.getOrganisationId(),
            p.getPlanCode(), p.getCycleYear(), p.getTitleEn(), p.getTitleAr(),
            p.getPreparedByUserId(), p.getStatus(),
            p.getMethodologySummaryEn(), p.getMethodologySummaryAr(),
            p.getAuditUniverseCount(), p.getTotalEngagementsPlanned(),
            p.getApprovedByUserId(), p.getApprovedAt(),
            p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    static boolean isBlank(String s) { return s == null || s.isBlank(); }

    void emit(IaAnnualPlan p, String trigger, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", String.valueOf(p.getId()));
        payload.put("planCode", p.getPlanCode());
        payload.put("cycleYear", String.valueOf(p.getCycleYear()));
        payload.put("status", p.getStatus());
        payload.put("preparedByUserId", String.valueOf(p.getPreparedByUserId()));
        if (extra != null) payload.putAll(extra);
        publisher.publishEvent(new DomainEvent(
            p.getOrganisationId(), trigger, "IaAnnualPlan", p.getId(),
            "ia-plan:" + p.getId(), payload));
    }
}
