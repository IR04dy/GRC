package com.tss.grc.module.audit_lc.ia_plan.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementCreateFromUniverseRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementPhaseTransitionRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneRecordRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneResponse;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAnnualPlan;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAuditUniverseEntry;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaEngagement;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaEngagementMilestone;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAnnualPlanRepository;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAuditUniverseEntryRepository;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaEngagementMilestoneRepository;
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
 * Sprint 13 — Engagement lifecycle service.
 *
 * <p>Phase machine:
 * {@code PLANNED → PLANNING → FIELDWORK → REPORTING → REPORT_ISSUED →
 * FOLLOW_UP → CLOSED}. {@code CANCELLED} is a terminal off-ramp from
 * any non-CLOSED state.
 *
 * <p>Phase gates:
 * <ul>
 *   <li>{@code REPORTING} requires {@code actualStartDate} to be set
 *       (you can't write a report without doing fieldwork first).</li>
 *   <li>{@code CLOSED} requires the engagement to have passed
 *       {@code REPORT_ISSUED} (cannot close before issuing the report).</li>
 *   <li>{@code FOLLOW_UP} is only reachable from {@code REPORT_ISSUED}
 *       and {@code CLOSED} is only reachable from {@code FOLLOW_UP}.</li>
 * </ul>
 *
 * <p>F8 events emitted:
 * {@code IA_ENGAGEMENT_CREATED}, {@code IA_ENGAGEMENT_PLANNING_STARTED},
 * {@code IA_ENGAGEMENT_FIELDWORK_STARTED}, {@code IA_ENGAGEMENT_REPORTING_STARTED},
 * {@code IA_ENGAGEMENT_REPORT_ISSUED}, {@code IA_ENGAGEMENT_FOLLOW_UP_STARTED},
 * {@code IA_ENGAGEMENT_CLOSED}, {@code IA_ENGAGEMENT_CANCELLED},
 * {@code IA_MILESTONE_REACHED}.
 */
@Service
@RequiredArgsConstructor
public class IaEngagementService {

    private static final Logger log = LoggerFactory.getLogger(IaEngagementService.class);

    /** Forward-only transitions; CANCELLED reachable from any non-CLOSED state. */
    static final Map<String, Set<String>> ALLOWED_PHASE_TRANSITIONS = Map.of(
        "PLANNED",       Set.of("PLANNING", "CANCELLED"),
        "PLANNING",      Set.of("FIELDWORK", "CANCELLED"),
        "FIELDWORK",     Set.of("REPORTING", "CANCELLED"),
        "REPORTING",     Set.of("REPORT_ISSUED", "CANCELLED"),
        "REPORT_ISSUED", Set.of("FOLLOW_UP", "CANCELLED"),
        "FOLLOW_UP",     Set.of("CLOSED", "CANCELLED"),
        "CLOSED",        Set.of(),
        "CANCELLED",     Set.of()
    );

    private final IaEngagementRepository engagementRepo;
    private final IaAuditUniverseEntryRepository universeRepo;
    private final IaAnnualPlanRepository planRepo;
    private final IaEngagementMilestoneRepository milestoneRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EngagementResponse> listAll() {
        Long orgId = org();
        return engagementRepo.findByOrganisationIdAndIsDeletedFalseOrderByEngagementCodeAsc(orgId)
            .stream().map(e -> toResponse(e, orgId)).toList();
    }

    @Transactional(readOnly = true)
    public List<EngagementResponse> listForPlan(Long planId) {
        Long orgId = org();
        return engagementRepo.findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEngagementCodeAsc(orgId, planId)
            .stream().map(e -> toResponse(e, orgId)).toList();
    }

    @Transactional(readOnly = true)
    public EngagementResponse get(Long id) {
        Long orgId = org();
        return toResponse(loadOrThrow(id), orgId);
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public EngagementResponse create(EngagementCreateRequest req) {
        Long orgId = org();
        // Plan must exist + be writable (not COMPLETED/ARCHIVED).
        IaAnnualPlan p = planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.planId())
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + req.planId()));
        if ("COMPLETED".equals(p.getStatus()) || "ARCHIVED".equals(p.getStatus())) {
            throw new IllegalArgumentException(
                "Cannot add engagements to plan in status " + p.getStatus());
        }
        String code = req.engagementCode() != null && !req.engagementCode().isBlank()
            ? req.engagementCode().trim()
            : nextEngagementCode(orgId, p.getCycleYear());
        if (engagementRepo.existsByOrganisationIdAndEngagementCodeAndIsDeletedFalse(orgId, code)) {
            throw new IllegalArgumentException("Engagement code already exists: " + code);
        }

        IaEngagement e = new IaEngagement();
        e.setOrganisationId(orgId);
        e.setPlanId(req.planId());
        e.setUniverseEntryId(req.universeEntryId());
        e.setEngagementCode(code);
        e.setTitleEn(req.titleEn());
        e.setTitleAr(req.titleAr());
        e.setAuditType(req.auditType());
        e.setScopeSummaryEn(req.scopeSummaryEn());
        e.setScopeSummaryAr(req.scopeSummaryAr());
        e.setLeadAuditorUserId(req.leadAuditorUserId());
        e.setTeamMemberIds(req.teamMemberIds());
        e.setSponsorUserId(req.sponsorUserId());
        e.setPlannedStartDate(req.plannedStartDate());
        e.setPlannedEndDate(req.plannedEndDate());
        e.setPhase("PLANNED");
        IaEngagement saved = engagementRepo.save(e);

        audit.log("CREATE", "IaEngagement", saved.getId(),
            "Engagement created: " + saved.getEngagementCode() + " (" + saved.getAuditType() + ")");
        emit(saved, "IA_ENGAGEMENT_CREATED", null);
        return toResponse(saved, orgId);
    }

    /**
     * Materialise a new engagement from an existing universe entry,
     * copying scope/business-unit text so the auditor doesn't re-type it.
     */
    @Transactional
    public EngagementResponse createFromUniverse(EngagementCreateFromUniverseRequest req) {
        Long orgId = org();
        IaAuditUniverseEntry u = universeRepo
            .findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.universeEntryId())
            .orElseThrow(() -> new EntityNotFoundException("Universe entry not found: " + req.universeEntryId()));

        // Synthesise a scope summary from the universe entry's process / BU columns
        String scopeEn = "Auditable process: " + nz(u.getProcessNameEn()) + " — Business unit: " + nz(u.getBusinessUnitEn());
        String scopeAr = "العملية القابلة للمراجعة: " + nz(u.getProcessNameAr()) + " — وحدة الأعمال: " + nz(u.getBusinessUnitAr());

        EngagementCreateRequest delegate = new EngagementCreateRequest(
            req.planId(),
            req.universeEntryId(),
            req.engagementCode(),
            u.getTitleEn(),
            u.getTitleAr(),
            req.auditType() != null ? req.auditType() : "OPERATIONAL",
            scopeEn,
            scopeAr,
            req.leadAuditorUserId(),
            null,
            req.sponsorUserId(),
            req.plannedStartDate(),
            req.plannedEndDate()
        );
        return create(delegate);
    }

    @Transactional
    public EngagementResponse update(Long id, EngagementUpdateRequest req) {
        IaEngagement e = loadOrThrow(id);
        if ("CLOSED".equals(e.getPhase()) || "CANCELLED".equals(e.getPhase())) {
            throw new IllegalArgumentException(
                "Engagement in phase " + e.getPhase() + " cannot be edited");
        }
        if (req.titleEn() != null) e.setTitleEn(req.titleEn());
        if (req.titleAr() != null) e.setTitleAr(req.titleAr());
        if (req.auditType() != null) e.setAuditType(req.auditType());
        if (req.scopeSummaryEn() != null) e.setScopeSummaryEn(req.scopeSummaryEn());
        if (req.scopeSummaryAr() != null) e.setScopeSummaryAr(req.scopeSummaryAr());
        if (req.leadAuditorUserId() != null) e.setLeadAuditorUserId(req.leadAuditorUserId());
        if (req.teamMemberIds() != null) e.setTeamMemberIds(req.teamMemberIds());
        if (req.sponsorUserId() != null) e.setSponsorUserId(req.sponsorUserId());
        if (req.plannedStartDate() != null) e.setPlannedStartDate(req.plannedStartDate());
        if (req.plannedEndDate() != null) e.setPlannedEndDate(req.plannedEndDate());
        if (req.planningMemoUrl() != null) {
            e.setPlanningMemoUrl(req.planningMemoUrl());
            // First time a memo URL is set, stamp signed_off_at — auditor
            // can override later via PATCH if they need to re-issue.
            if (e.getPlanningMemoSignedOffAt() == null) {
                e.setPlanningMemoSignedOffAt(LocalDateTime.now());
            }
        }
        if (req.opinion() != null) e.setOpinion(req.opinion());
        if (req.findingCount() != null) e.setFindingCount(req.findingCount());
        if (req.criticalFindingCount() != null) e.setCriticalFindingCount(req.criticalFindingCount());
        e.setUpdatedAt(LocalDateTime.now());
        IaEngagement saved = engagementRepo.save(e);

        audit.log("UPDATE", "IaEngagement", saved.getId(),
            "Engagement updated: " + saved.getEngagementCode());
        return toResponse(saved, org());
    }

    @Transactional
    public EngagementResponse transitionPhase(Long id, EngagementPhaseTransitionRequest req) {
        IaEngagement e = loadOrThrow(id);
        String from = e.getPhase();
        String to = req.newPhase();

        Set<String> allowed = ALLOWED_PHASE_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }

        // Gate validations on specific transitions
        if ("REPORTING".equals(to) && e.getActualStartDate() == null) {
            throw new IllegalArgumentException(
                "Cannot move to REPORTING without an actual fieldwork start date — set actual_start_date or move through FIELDWORK first");
        }
        if ("REPORT_ISSUED".equals(to)) {
            if (e.getActualEndDate() == null) {
                e.setActualEndDate(LocalDate.now());
            }
        }

        // Phase-specific side effects on the engagement row
        switch (to) {
            case "PLANNING" -> {
                // No-op — auditor is still in the planning-memo phase
            }
            case "FIELDWORK" -> {
                if (e.getActualStartDate() == null) {
                    e.setActualStartDate(LocalDate.now());
                }
            }
            case "CANCELLED" -> {
                if (e.getActualEndDate() == null) {
                    e.setActualEndDate(LocalDate.now());
                }
            }
            default -> { /* nothing else */ }
        }

        e.setPhase(to);
        e.setUpdatedAt(LocalDateTime.now());
        IaEngagement saved = engagementRepo.save(e);

        audit.log("TRANSITION", "IaEngagement", saved.getId(),
            "Engagement " + saved.getEngagementCode() + " phase " + from + " → " + to
                + (req.actorUserId() != null ? " by user=" + req.actorUserId() : ""));

        String trigger = switch (to) {
            case "PLANNING"      -> "IA_ENGAGEMENT_PLANNING_STARTED";
            case "FIELDWORK"     -> "IA_ENGAGEMENT_FIELDWORK_STARTED";
            case "REPORTING"     -> "IA_ENGAGEMENT_REPORTING_STARTED";
            case "REPORT_ISSUED" -> "IA_ENGAGEMENT_REPORT_ISSUED";
            case "FOLLOW_UP"     -> "IA_ENGAGEMENT_FOLLOW_UP_STARTED";
            case "CLOSED"        -> "IA_ENGAGEMENT_CLOSED";
            case "CANCELLED"     -> "IA_ENGAGEMENT_CANCELLED";
            default              -> "IA_ENGAGEMENT_PHASE_CHANGED";
        };
        emit(saved, trigger, Map.of("fromPhase", from, "toPhase", to));
        return toResponse(saved, org());
    }

    /**
     * Add a milestone definition (planned date only — actuals via
     * {@link #recordMilestone}).
     */
    @Transactional
    public MilestoneResponse createMilestone(Long engagementId, MilestoneCreateRequest req) {
        Long orgId = org();
        IaEngagement e = loadOrThrow(engagementId);
        IaEngagementMilestone m = new IaEngagementMilestone();
        m.setOrganisationId(orgId);
        m.setEngagementId(e.getId());
        m.setMilestoneType(req.milestoneType());
        m.setPlannedDate(req.plannedDate());
        m.setNotesEn(req.notesEn());
        m.setNotesAr(req.notesAr());
        IaEngagementMilestone saved = milestoneRepo.save(m);
        audit.log("CREATE", "IaEngagementMilestone", saved.getId(),
            "Milestone planned: " + saved.getMilestoneType() + " for engagement " + e.getEngagementCode());
        return milestoneToResponse(saved);
    }

    /**
     * Mark a milestone as reached. Idempotent on (engagement, milestoneType):
     * if a row already exists we update the actual_date in place; otherwise
     * we create a new row. Fires {@code IA_MILESTONE_REACHED} on every call
     * that records a new actual_date.
     */
    @Transactional
    public MilestoneResponse recordMilestone(Long engagementId, MilestoneRecordRequest req) {
        Long orgId = org();
        IaEngagement e = loadOrThrow(engagementId);

        IaEngagementMilestone m = milestoneRepo
            .findByOrganisationIdAndEngagementIdAndMilestoneTypeAndIsDeletedFalse(orgId, e.getId(), req.milestoneType())
            .orElseGet(() -> {
                IaEngagementMilestone fresh = new IaEngagementMilestone();
                fresh.setOrganisationId(orgId);
                fresh.setEngagementId(e.getId());
                fresh.setMilestoneType(req.milestoneType());
                return fresh;
            });

        boolean firstRecord = m.getActualDate() == null;
        m.setActualDate(req.actualDate());
        m.setCompletedByUserId(req.completedByUserId());
        if (req.notesEn() != null) m.setNotesEn(req.notesEn());
        if (req.notesAr() != null) m.setNotesAr(req.notesAr());
        m.setUpdatedAt(LocalDateTime.now());
        IaEngagementMilestone saved = milestoneRepo.save(m);

        audit.log("RECORD", "IaEngagementMilestone", saved.getId(),
            "Milestone " + saved.getMilestoneType() + " recorded for engagement "
                + e.getEngagementCode() + " on " + req.actualDate()
                + (req.completedByUserId() != null ? " by user=" + req.completedByUserId() : ""));

        if (firstRecord) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("engagementId", String.valueOf(e.getId()));
            payload.put("engagementCode", e.getEngagementCode());
            payload.put("milestoneType", saved.getMilestoneType());
            payload.put("actualDate", String.valueOf(saved.getActualDate()));
            publisher.publishEvent(new DomainEvent(
                orgId, "IA_MILESTONE_REACHED", "IaEngagementMilestone", saved.getId(),
                "ia-milestone:" + saved.getId(), payload));
        }
        return milestoneToResponse(saved);
    }

    @Transactional
    public void softDelete(Long id) {
        IaEngagement e = loadOrThrow(id);
        e.setIsDeleted(true);
        e.setDeletedAt(LocalDateTime.now());
        engagementRepo.save(e);
        audit.log("DELETE", "IaEngagement", e.getId(),
            "Engagement soft-deleted: " + e.getEngagementCode());
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private IaEngagement loadOrThrow(Long id) {
        return engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("IA engagement not found: " + id));
    }

    /**
     * Generate the next ENG-{cycleYear}-NNN code for a given org. Naive
     * implementation — counts existing engagements in this cycle and
     * increments. Service guards uniqueness with the {@code existsBy*}
     * check above, so a race is rejected cleanly.
     */
    String nextEngagementCode(Long orgId, Integer cycleYear) {
        // Count engagements for any plan in this cycle (good enough — code
        // is unique per org so a numeric sequence is fine).
        long n = engagementRepo.findByOrganisationIdAndIsDeletedFalseOrderByEngagementCodeAsc(orgId).size();
        return String.format("ENG-%d-%03d", cycleYear, n + 1);
    }

    EngagementResponse toResponse(IaEngagement e, Long orgId) {
        List<MilestoneResponse> milestones = milestoneRepo
            .findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(orgId, e.getId())
            .stream().map(IaEngagementService::milestoneToResponse).toList();
        return new EngagementResponse(
            e.getId(), e.getExternalId(), e.getOrganisationId(),
            e.getPlanId(), e.getUniverseEntryId(),
            e.getEngagementCode(), e.getTitleEn(), e.getTitleAr(),
            e.getAuditType(), e.getScopeSummaryEn(), e.getScopeSummaryAr(),
            e.getLeadAuditorUserId(), e.getTeamMemberIds(), e.getSponsorUserId(),
            e.getPlannedStartDate(), e.getPlannedEndDate(),
            e.getActualStartDate(), e.getActualEndDate(),
            e.getPhase(), e.getPlanningMemoUrl(), e.getPlanningMemoSignedOffAt(),
            e.getOpinion(), e.getFindingCount(), e.getCriticalFindingCount(),
            milestones,
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    static MilestoneResponse milestoneToResponse(IaEngagementMilestone m) {
        return new MilestoneResponse(
            m.getId(), m.getEngagementId(), m.getMilestoneType(),
            m.getPlannedDate(), m.getActualDate(), m.getCompletedByUserId(),
            m.getNotesEn(), m.getNotesAr(), m.getCreatedAt()
        );
    }

    void emit(IaEngagement e, String trigger, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("engagementId", String.valueOf(e.getId()));
        payload.put("engagementCode", e.getEngagementCode());
        payload.put("planId", String.valueOf(e.getPlanId()));
        payload.put("phase", e.getPhase());
        payload.put("auditType", e.getAuditType());
        payload.put("leadAuditorUserId", String.valueOf(e.getLeadAuditorUserId()));
        if (extra != null) payload.putAll(extra);
        publisher.publishEvent(new DomainEvent(
            e.getOrganisationId(), trigger, "IaEngagement", e.getId(),
            "ia-engagement:" + e.getId(), payload));
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
