package com.tss.grc.module.tprm.outsourcing.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
import com.tss.grc.security.GrcUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequireModule("TPRM")
@Tag(name = "TPRM — Outsourcing Arrangement Register (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class OutsourcingController {

    private final OutsourcingArrangementService arrangements;
    private final OutsourcingDueDiligenceService dueDiligence;
    private final OutsourcingMonitoringService monitoring;
    private final OutsourcingExitPlanService exitPlans;

    // ─── Arrangements ───────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasPermission('TPRM','READ')")
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
    @PreAuthorize("hasPermission('TPRM','READ')")
    public ArrangementResponse get(@PathVariable Long id) {
        return arrangements.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ArrangementResponse create(@RequestBody @Valid ArrangementCreateRequest req) {
        return arrangements.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ArrangementResponse update(@PathVariable Long id,
                                       @RequestBody @Valid ArrangementUpdateRequest req) {
        return arrangements.update(id, req);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ArrangementResponse transition(@PathVariable Long id,
                                            @RequestBody @Valid ArrangementTransitionRequest req) {
        return arrangements.transition(id, req);
    }

    @PostMapping("/{id}/record-review")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ArrangementResponse recordReview(@PathVariable Long id) {
        Long userId = currentUserIdOrNull();
        return arrangements.recordReview(id, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('TPRM','DELETE')")
    public void delete(@PathVariable Long id) {
        arrangements.softDelete(id);
    }

    @GetMapping("/{id}/materiality-assessment")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public MaterialityAssessment materialityAssessment(@PathVariable Long id) {
        return arrangements.materialityAssessment(id);
    }

    @GetMapping("/admin/sama-pending")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<ArrangementResponse> samaPending() {
        return arrangements.listAwaitingSamaNonObjection();
    }

    @GetMapping("/admin/overdue-reviews")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<ArrangementResponse> overdueReviews() {
        return arrangements.listOverdueForReview();
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public DashboardResponse dashboard() {
        return arrangements.dashboard();
    }

    // ─── Due Diligence ──────────────────────────────────────────────

    @GetMapping("/{id}/due-diligence")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<DueDiligenceResponse> dueDiligenceFor(@PathVariable("id") Long arrangementId) {
        return dueDiligence.listForArrangement(arrangementId);
    }

    @PostMapping("/due-diligence")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public DueDiligenceResponse recordDueDiligence(@RequestBody @Valid DueDiligenceCreateRequest req) {
        return dueDiligence.record(req);
    }

    // ─── Monitoring ─────────────────────────────────────────────────

    @GetMapping("/{id}/monitoring-events")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<MonitoringEventResponse> eventsFor(@PathVariable("id") Long arrangementId) {
        return monitoring.listForArrangement(arrangementId);
    }

    @GetMapping("/monitoring-events/open")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<MonitoringEventResponse> openEvents() {
        return monitoring.listOpen();
    }

    @PostMapping("/monitoring-events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public MonitoringEventResponse recordEvent(@RequestBody @Valid MonitoringEventCreateRequest req) {
        return monitoring.record(req);
    }

    @PostMapping("/monitoring-events/{eventId}/resolve")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public MonitoringEventResponse resolveEvent(@PathVariable Long eventId,
                                                  @RequestBody(required = false) MonitoringEventResolveRequest req) {
        return monitoring.resolve(eventId, req);
    }

    // ─── Exit Plan ──────────────────────────────────────────────────

    @GetMapping("/{id}/exit-plan")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public ExitPlanResponse exitPlanFor(@PathVariable("id") Long arrangementId) {
        return exitPlans.getForArrangement(arrangementId);
    }

    @GetMapping("/exit-plans/overdue-tests")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<ExitPlanResponse> overdueExitTests() {
        return exitPlans.findOverdueTest();
    }

    @PostMapping("/exit-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ExitPlanResponse createExitPlan(@RequestBody @Valid ExitPlanCreateRequest req) {
        return exitPlans.create(req);
    }

    @PatchMapping("/exit-plans/{planId}")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ExitPlanResponse updateExitPlan(@PathVariable Long planId,
                                             @RequestBody @Valid ExitPlanUpdateRequest req) {
        return exitPlans.update(planId, req);
    }

    @PostMapping("/exit-plans/{planId}/record-test")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ExitPlanResponse recordExitTest(@PathVariable Long planId,
                                             @RequestParam(value = "testedOn", required = false) String testedOn) {
        LocalDate when = testedOn != null && !testedOn.isBlank() ? LocalDate.parse(testedOn) : null;
        return exitPlans.recordTest(planId, when);
    }

    // ─── Internal helpers ───────────────────────────────────────────

    /**
     * QUAL-TI-005: resolve the acting user id from the security principal (mirrors
     * ObService/CptyService) instead of always returning null. Returns null only
     * when there is no authenticated GrcUserDetails principal.
     */
    private static Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof GrcUserDetails u)) {
            return null;
        }
        return u.getUserId();
    }
}
