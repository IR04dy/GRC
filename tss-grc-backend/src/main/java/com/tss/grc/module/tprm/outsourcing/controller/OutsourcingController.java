package com.tss.grc.module.tprm.outsourcing.controller;

import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementTransitionRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementUpdateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DashboardResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DueDiligenceCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DueDiligenceResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ExitPlanCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ExitPlanResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ExitPlanUpdateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MaterialityAssessment;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventResolveRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventResponse;
import com.tss.grc.module.tprm.outsourcing.service.OutsourcingArrangementService;
import com.tss.grc.module.tprm.outsourcing.service.OutsourcingDueDiligenceService;
import com.tss.grc.module.tprm.outsourcing.service.OutsourcingExitPlanService;
import com.tss.grc.module.tprm.outsourcing.service.OutsourcingMonitoringService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 13 — REST surface for the outsourcing arrangement register.
 *
 * <pre>
 *   Arrangements:
 *     GET    /api/v1/tprm/outsourcing
 *     GET    /api/v1/tprm/outsourcing?stage=ACTIVE
 *     GET    /api/v1/tprm/outsourcing?material=true
 *     GET    /api/v1/tprm/outsourcing?criticalFunction=PAYMENTS
 *     GET    /api/v1/tprm/outsourcing/{id}
 *     POST   /api/v1/tprm/outsourcing
 *     PATCH  /api/v1/tprm/outsourcing/{id}
 *     POST   /api/v1/tprm/outsourcing/{id}/transition
 *     POST   /api/v1/tprm/outsourcing/{id}/record-review
 *     DELETE /api/v1/tprm/outsourcing/{id}
 *     GET    /api/v1/tprm/outsourcing/{id}/materiality-assessment
 *     GET    /api/v1/tprm/outsourcing/admin/sama-pending
 *     GET    /api/v1/tprm/outsourcing/admin/overdue-reviews
 *     GET    /api/v1/tprm/outsourcing/dashboard
 *
 *   Due Diligence:
 *     GET    /api/v1/tprm/outsourcing/{id}/due-diligence
 *     POST   /api/v1/tprm/outsourcing/due-diligence
 *
 *   Monitoring:
 *     GET    /api/v1/tprm/outsourcing/{id}/monitoring-events
 *     GET    /api/v1/tprm/outsourcing/monitoring-events/open
 *     POST   /api/v1/tprm/outsourcing/monitoring-events
 *     POST   /api/v1/tprm/outsourcing/monitoring-events/{eventId}/resolve
 *
 *   Exit Plan:
 *     GET    /api/v1/tprm/outsourcing/{id}/exit-plan
 *     GET    /api/v1/tprm/outsourcing/exit-plans/overdue-tests
 *     POST   /api/v1/tprm/outsourcing/exit-plans
 *     PATCH  /api/v1/tprm/outsourcing/exit-plans/{planId}
 *     POST   /api/v1/tprm/outsourcing/exit-plans/{planId}/record-test
 * </pre>
 *
 * <p>Reads are broad (every audit-readiness reader). Writes are scoped
 * to the operational roles. Lifecycle transitions add COMPLIANCE_OFFICER
 * because materiality flips + SAMA non-objection gates are
 * Compliance-owned actions.
 */
@RestController
@RequestMapping("/api/v1/tprm/outsourcing")
@RequiredArgsConstructor
@Tag(name = "TPRM — Outsourcing Arrangement Register (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class OutsourcingController {

    private final OutsourcingArrangementService arrangements;
    private final OutsourcingDueDiligenceService dueDiligence;
    private final OutsourcingMonitoringService monitoring;
    private final OutsourcingExitPlanService exitPlans;

    // ─── Arrangements ───────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ArrangementResponse> list(
            @RequestParam(value = "stage", required = false) String stage,
            @RequestParam(value = "material", required = false) Boolean material,
            @RequestParam(value = "criticalFunction", required = false) String criticalFunction) {
        if (stage != null) {
            return arrangements.listByLifecycleStage(stage);
        }
        if (material != null) {
            return arrangements.listByMateriality(material);
        }
        if (criticalFunction != null) {
            return arrangements.listByCriticalFunction(criticalFunction);
        }
        return arrangements.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public ArrangementResponse get(@PathVariable Long id) {
        return arrangements.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','PLATFORM_ADMIN')")
    public ArrangementResponse create(@RequestBody @Valid ArrangementCreateRequest req) {
        return arrangements.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','PLATFORM_ADMIN')")
    public ArrangementResponse update(@PathVariable Long id,
                                       @RequestBody @Valid ArrangementUpdateRequest req) {
        return arrangements.update(id, req);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PLATFORM_ADMIN')")
    public ArrangementResponse transition(@PathVariable Long id,
                                            @RequestBody @Valid ArrangementTransitionRequest req) {
        return arrangements.transition(id, req);
    }

    @PostMapping("/{id}/record-review")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PLATFORM_ADMIN')")
    public ArrangementResponse recordReview(@PathVariable Long id) {
        Long userId = currentUserIdOrNull();
        return arrangements.recordReview(id, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TPRM_LEAD','PLATFORM_ADMIN')")
    public void delete(@PathVariable Long id) {
        arrangements.softDelete(id);
    }

    @GetMapping("/{id}/materiality-assessment")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public MaterialityAssessment materialityAssessment(@PathVariable Long id) {
        return arrangements.materialityAssessment(id);
    }

    @GetMapping("/admin/sama-pending")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN')")
    public List<ArrangementResponse> samaPending() {
        return arrangements.listAwaitingSamaNonObjection();
    }

    @GetMapping("/admin/overdue-reviews")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN')")
    public List<ArrangementResponse> overdueReviews() {
        return arrangements.listOverdueForReview();
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public DashboardResponse dashboard() {
        return arrangements.dashboard();
    }

    // ─── Due Diligence ──────────────────────────────────────────────

    @GetMapping("/{id}/due-diligence")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<DueDiligenceResponse> dueDiligenceFor(@PathVariable("id") Long arrangementId) {
        return dueDiligence.listForArrangement(arrangementId);
    }

    @PostMapping("/due-diligence")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPRM_LEAD','RISK_MANAGER','PLATFORM_ADMIN')")
    public DueDiligenceResponse recordDueDiligence(@RequestBody @Valid DueDiligenceCreateRequest req) {
        return dueDiligence.record(req);
    }

    // ─── Monitoring ─────────────────────────────────────────────────

    @GetMapping("/{id}/monitoring-events")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<MonitoringEventResponse> eventsFor(@PathVariable("id") Long arrangementId) {
        return monitoring.listForArrangement(arrangementId);
    }

    @GetMapping("/monitoring-events/open")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<MonitoringEventResponse> openEvents() {
        return monitoring.listOpen();
    }

    @PostMapping("/monitoring-events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPRM_LEAD','OPS_MANAGER','INCIDENT_COMMANDER','PLATFORM_ADMIN')")
    public MonitoringEventResponse recordEvent(@RequestBody @Valid MonitoringEventCreateRequest req) {
        return monitoring.record(req);
    }

    @PostMapping("/monitoring-events/{eventId}/resolve")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','OPS_MANAGER','INCIDENT_COMMANDER','PLATFORM_ADMIN')")
    public MonitoringEventResponse resolveEvent(@PathVariable Long eventId,
                                                  @RequestBody(required = false) MonitoringEventResolveRequest req) {
        return monitoring.resolve(eventId, req);
    }

    // ─── Exit Plan ──────────────────────────────────────────────────

    @GetMapping("/{id}/exit-plan")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','PROCUREMENT','CISO','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public ExitPlanResponse exitPlanFor(@PathVariable("id") Long arrangementId) {
        return exitPlans.getForArrangement(arrangementId);
    }

    @GetMapping("/exit-plans/overdue-tests")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','COMPLIANCE_OFFICER','RISK_MANAGER','AUDITOR','BOARD_MEMBER','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN','BCM_MANAGER')")
    public List<ExitPlanResponse> overdueExitTests() {
        return exitPlans.findOverdueTest();
    }

    @PostMapping("/exit-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPRM_LEAD','RISK_MANAGER','BCM_MANAGER','PLATFORM_ADMIN')")
    public ExitPlanResponse createExitPlan(@RequestBody @Valid ExitPlanCreateRequest req) {
        return exitPlans.create(req);
    }

    @PatchMapping("/exit-plans/{planId}")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','RISK_MANAGER','BCM_MANAGER','PLATFORM_ADMIN')")
    public ExitPlanResponse updateExitPlan(@PathVariable Long planId,
                                             @RequestBody @Valid ExitPlanUpdateRequest req) {
        return exitPlans.update(planId, req);
    }

    @PostMapping("/exit-plans/{planId}/record-test")
    @PreAuthorize("hasAnyRole('TPRM_LEAD','RISK_MANAGER','BCM_MANAGER','PLATFORM_ADMIN')")
    public ExitPlanResponse recordExitTest(@PathVariable Long planId,
                                             @RequestParam(value = "testedOn", required = false) String testedOn) {
        LocalDate when = testedOn != null && !testedOn.isBlank() ? LocalDate.parse(testedOn) : null;
        return exitPlans.recordTest(planId, when);
    }

    // ─── Internal helpers ───────────────────────────────────────────

    private static Long currentUserIdOrNull() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            // Many auth principals expose getId() — we don't depend on that
            // here; the audit log records the username separately.
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }
}
