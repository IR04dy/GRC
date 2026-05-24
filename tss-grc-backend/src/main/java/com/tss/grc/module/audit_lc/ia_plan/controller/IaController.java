package com.tss.grc.module.audit_lc.ia_plan.controller;

import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementCreateFromUniverseRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementPhaseTransitionRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneRecordRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanDashboardResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanTransitionRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseBulkImportRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseBulkImportResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.service.IaAnnualPlanService;
import com.tss.grc.module.audit_lc.ia_plan.service.IaAuditUniverseService;
import com.tss.grc.module.audit_lc.ia_plan.service.IaEngagementService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sprint 13 — REST surface for IA annual plan + universe + engagements.
 *
 * <pre>
 *   Plans:
 *     GET    /api/v1/ia/plan
 *     GET    /api/v1/ia/plan/{id}
 *     POST   /api/v1/ia/plan
 *     PATCH  /api/v1/ia/plan/{id}
 *     POST   /api/v1/ia/plan/{id}/transition
 *     DELETE /api/v1/ia/plan/{id}
 *     GET    /api/v1/ia/plan/{id}/dashboard
 *
 *   Universe:
 *     GET    /api/v1/ia/universe
 *     GET    /api/v1/ia/universe/master
 *     GET    /api/v1/ia/universe/by-plan/{planId}
 *     GET    /api/v1/ia/universe/{id}
 *     POST   /api/v1/ia/universe
 *     PATCH  /api/v1/ia/universe/{id}
 *     POST   /api/v1/ia/universe/bulk-import
 *     POST   /api/v1/ia/universe/{id}/recompute-residual
 *     DELETE /api/v1/ia/universe/{id}
 *
 *   Engagements:
 *     GET    /api/v1/ia/engagements
 *     GET    /api/v1/ia/engagements/by-plan/{planId}
 *     GET    /api/v1/ia/engagements/{id}
 *     POST   /api/v1/ia/engagements
 *     POST   /api/v1/ia/engagements/from-universe
 *     PATCH  /api/v1/ia/engagements/{id}
 *     POST   /api/v1/ia/engagements/{id}/transition
 *     DELETE /api/v1/ia/engagements/{id}
 *     POST   /api/v1/ia/engagements/{id}/milestones
 *     POST   /api/v1/ia/engagements/{id}/milestones/record
 * </pre>
 *
 * <p>Read roles are broad (AC member, board member, risk, compliance,
 * ext-auditor, internal auditor, CISO, PLATFORM_ADMIN). Write roles
 * are tighter: PLATFORM_ADMIN, CHIEF_AUDIT_EXECUTIVE, AUDIT_DIRECTOR
 * on the plan; INTERNAL_AUDITOR_LEAD + INTERNAL_AUDITOR on universe /
 * engagements.
 */
@RestController
@RequestMapping("/api/v1/ia")
@RequiredArgsConstructor
@Tag(name = "Internal Audit — Annual Plan + Engagements (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class IaController {

    private final IaAnnualPlanService planService;
    private final IaAuditUniverseService universeService;
    private final IaEngagementService engagementService;

    // ── Plans ────────────────────────────────────────────────────────

    @GetMapping("/plan")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public List<PlanResponse> listPlans() {
        return planService.list();
    }

    @GetMapping("/plan/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public PlanResponse getPlan(@PathVariable Long id) {
        return planService.get(id);
    }

    @PostMapping("/plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR')")
    public PlanResponse createPlan(@RequestBody @Valid PlanCreateRequest req) {
        return planService.create(req);
    }

    @PatchMapping("/plan/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR')")
    public PlanResponse updatePlan(@PathVariable Long id, @RequestBody @Valid PlanUpdateRequest req) {
        return planService.update(id, req);
    }

    @PostMapping("/plan/{id}/transition")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE')")
    public PlanResponse transitionPlan(@PathVariable Long id, @RequestBody @Valid PlanTransitionRequest req) {
        return planService.transition(id, req);
    }

    @DeleteMapping("/plan/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE')")
    public void deletePlan(@PathVariable Long id) {
        planService.softDelete(id);
    }

    @GetMapping("/plan/{id}/dashboard")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public PlanDashboardResponse planDashboard(@PathVariable Long id) {
        return planService.dashboard(id);
    }

    // ── Universe ─────────────────────────────────────────────────────

    @GetMapping("/universe")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public List<UniverseResponse> listAllUniverse() {
        return universeService.listAll();
    }

    @GetMapping("/universe/master")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public List<UniverseResponse> listMasterUniverse() {
        return universeService.listMaster();
    }

    @GetMapping("/universe/by-plan/{planId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public List<UniverseResponse> listUniverseForPlan(@PathVariable Long planId) {
        return universeService.listForPlan(planId);
    }

    @GetMapping("/universe/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public UniverseResponse getUniverse(@PathVariable Long id) {
        return universeService.get(id);
    }

    @PostMapping("/universe")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD')")
    public UniverseResponse createUniverse(@RequestBody @Valid UniverseCreateRequest req) {
        return universeService.create(req);
    }

    @PatchMapping("/universe/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD')")
    public UniverseResponse updateUniverse(@PathVariable Long id, @RequestBody @Valid UniverseUpdateRequest req) {
        return universeService.update(id, req);
    }

    @PostMapping("/universe/bulk-import")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD')")
    public UniverseBulkImportResponse bulkImport(@RequestBody @Valid UniverseBulkImportRequest req) {
        return universeService.bulkImport(req);
    }

    @PostMapping("/universe/{id}/recompute-residual")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD')")
    public UniverseResponse recomputeResidual(@PathVariable Long id) {
        return universeService.recomputeResidual(id);
    }

    @DeleteMapping("/universe/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD')")
    public void deleteUniverse(@PathVariable Long id) {
        universeService.softDelete(id);
    }

    // ── Engagements ──────────────────────────────────────────────────

    @GetMapping("/engagements")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public List<EngagementResponse> listAllEngagements() {
        return engagementService.listAll();
    }

    @GetMapping("/engagements/by-plan/{planId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public List<EngagementResponse> listEngagementsForPlan(@PathVariable Long planId) {
        return engagementService.listForPlan(planId);
    }

    @GetMapping("/engagements/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR',"
        + "'INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','BOARD_MEMBER',"
        + "'RISK_MANAGER','COMPLIANCE_OFFICER','EXTERNAL_AUDITOR','AUDITOR','CISO')")
    public EngagementResponse getEngagement(@PathVariable Long id) {
        return engagementService.get(id);
    }

    @PostMapping("/engagements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR')")
    public EngagementResponse createEngagement(@RequestBody @Valid EngagementCreateRequest req) {
        return engagementService.create(req);
    }

    @PostMapping("/engagements/from-universe")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR')")
    public EngagementResponse createFromUniverse(@RequestBody @Valid EngagementCreateFromUniverseRequest req) {
        return engagementService.createFromUniverse(req);
    }

    @PatchMapping("/engagements/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR')")
    public EngagementResponse updateEngagement(@PathVariable Long id, @RequestBody @Valid EngagementUpdateRequest req) {
        return engagementService.update(id, req);
    }

    @PostMapping("/engagements/{id}/transition")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD')")
    public EngagementResponse transitionEngagement(@PathVariable Long id,
                                                    @RequestBody @Valid EngagementPhaseTransitionRequest req) {
        return engagementService.transitionPhase(id, req);
    }

    @DeleteMapping("/engagements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD')")
    public void deleteEngagement(@PathVariable Long id) {
        engagementService.softDelete(id);
    }

    @PostMapping("/engagements/{id}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR')")
    public MilestoneResponse createMilestone(@PathVariable Long id, @RequestBody @Valid MilestoneCreateRequest req) {
        return engagementService.createMilestone(id, req);
    }

    @PostMapping("/engagements/{id}/milestones/record")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CHIEF_AUDIT_EXECUTIVE','AUDIT_DIRECTOR','INTERNAL_AUDITOR_LEAD','INTERNAL_AUDITOR')")
    public MilestoneResponse recordMilestone(@PathVariable Long id, @RequestBody @Valid MilestoneRecordRequest req) {
        return engagementService.recordMilestone(id, req);
    }
}
