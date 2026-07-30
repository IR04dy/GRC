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
 * <h3>Authorisation — {@code AUDIT:READ / WRITE / DELETE} (F2-05, 2026-07-29)</h3>
 *
 * <p><b>This class used to authorise by role name, and the names were wrong.</b> The guards recited
 * twelve roles on reads and up to five on writes, of which <b>CHIEF_AUDIT_EXECUTIVE,
 * AUDIT_DIRECTOR, INTERNAL_AUDITOR_LEAD, AUDIT_COMMITTEE_MEMBER and BOARD_MEMBER do not exist in
 * {@code iam.roles}</b> — 5 of the 12. A role guard is not evaluated by
 * {@code ModulePermissionEvaluator}, so it has no PLATFORM_ADMIN bypass either: the population that
 * got through was exactly the real roles still named. On <b>11 of these 26 endpoints that was
 * PLATFORM_ADMIN alone</b>, including creating the annual plan, building the audit universe,
 * transitioning a plan or an engagement, bulk import and residual recompute.
 *
 * <p>Live before the change: {@code auditor} (AUDITOR + INTERNAL_AUDITOR) could read the plan and
 * open an engagement, but {@code POST /api/v1/ia/plan} → <b>403</b>. The head of internal audit
 * could not plan the audit year, and the role that would have let them could not be created.
 *
 * <p>The fix is not a new authorisation model — it is <em>this module's existing one</em>.
 * {@code AUDIT:READ/WRITE/DELETE} is catalogued and already granted, and the two sibling
 * controllers over the same domain ({@code AuditLifecycleController} on {@code /api/audit-lc}, and
 * {@code audit_eng}'s {@code AuditEngagementController}) have always used it — including
 * {@code AUDIT:WRITE} on their own annual-plan transition, which is why plan transitions here are
 * WRITE and not APPROVE. {@code AUDIT:APPROVE} is deliberately not invented: it is not in the
 * catalogue, and guarding on an uncatalogued permission is the F2-23 trap.
 *
 * <p>Population change, stated rather than implied. Reads gain BOARD_SECRETARY,
 * EXECUTIVE_REVIEWER, GRC_ADMINISTRATOR, ORG_ADMIN and VIEWER (all already hold {@code AUDIT:READ},
 * and all could already read the sibling register at {@code /api/audit-lc/annual-plans}); they lose
 * nobody, because <b>V326</b> grants {@code AUDIT:READ} to the three real roles the old guard named
 * that the permission lacked — CISO, RISK_MANAGER, COMPLIANCE_OFFICER. Writes and deletes become
 * GRC_ADMINISTRATOR, INTERNAL_AUDITOR and ORG_ADMIN: INTERNAL_AUDITOR extends from 6 of 13 writes
 * to all of them, which is what the ghost names were reaching for and what {@code AUDIT:WRITE}
 * already means everywhere else in this module.
 *
 * @see com.tss.grc.security.RoleGuardConversionRatchetTest the census that found this, and the
 *      ratchet that stops it recurring
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
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<PlanResponse> listPlans() {
        return planService.list();
    }

    @GetMapping("/plan/{id}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public PlanResponse getPlan(@PathVariable Long id) {
        return planService.get(id);
    }

    @PostMapping("/plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public PlanResponse createPlan(@RequestBody @Valid PlanCreateRequest req) {
        return planService.create(req);
    }

    @PatchMapping("/plan/{id}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public PlanResponse updatePlan(@PathVariable Long id, @RequestBody @Valid PlanUpdateRequest req) {
        return planService.update(id, req);
    }

    @PostMapping("/plan/{id}/transition")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public PlanResponse transitionPlan(@PathVariable Long id, @RequestBody @Valid PlanTransitionRequest req) {
        return planService.transition(id, req);
    }

    @DeleteMapping("/plan/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('AUDIT','DELETE')")
    public void deletePlan(@PathVariable Long id) {
        planService.softDelete(id);
    }

    @GetMapping("/plan/{id}/dashboard")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public PlanDashboardResponse planDashboard(@PathVariable Long id) {
        return planService.dashboard(id);
    }

    // ── Universe ─────────────────────────────────────────────────────

    @GetMapping("/universe")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<UniverseResponse> listAllUniverse() {
        return universeService.listAll();
    }

    @GetMapping("/universe/master")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<UniverseResponse> listMasterUniverse() {
        return universeService.listMaster();
    }

    @GetMapping("/universe/by-plan/{planId}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<UniverseResponse> listUniverseForPlan(@PathVariable Long planId) {
        return universeService.listForPlan(planId);
    }

    @GetMapping("/universe/{id}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public UniverseResponse getUniverse(@PathVariable Long id) {
        return universeService.get(id);
    }

    @PostMapping("/universe")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public UniverseResponse createUniverse(@RequestBody @Valid UniverseCreateRequest req) {
        return universeService.create(req);
    }

    @PatchMapping("/universe/{id}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public UniverseResponse updateUniverse(@PathVariable Long id, @RequestBody @Valid UniverseUpdateRequest req) {
        return universeService.update(id, req);
    }

    @PostMapping("/universe/bulk-import")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public UniverseBulkImportResponse bulkImport(@RequestBody @Valid UniverseBulkImportRequest req) {
        return universeService.bulkImport(req);
    }

    @PostMapping("/universe/{id}/recompute-residual")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public UniverseResponse recomputeResidual(@PathVariable Long id) {
        return universeService.recomputeResidual(id);
    }

    @DeleteMapping("/universe/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('AUDIT','DELETE')")
    public void deleteUniverse(@PathVariable Long id) {
        universeService.softDelete(id);
    }

    // ── Engagements ──────────────────────────────────────────────────

    @GetMapping("/engagements")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<EngagementResponse> listAllEngagements() {
        return engagementService.listAll();
    }

    @GetMapping("/engagements/by-plan/{planId}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public List<EngagementResponse> listEngagementsForPlan(@PathVariable Long planId) {
        return engagementService.listForPlan(planId);
    }

    @GetMapping("/engagements/{id}")
    @PreAuthorize("hasPermission('AUDIT','READ')")
    public EngagementResponse getEngagement(@PathVariable Long id) {
        return engagementService.get(id);
    }

    @PostMapping("/engagements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public EngagementResponse createEngagement(@RequestBody @Valid EngagementCreateRequest req) {
        return engagementService.create(req);
    }

    @PostMapping("/engagements/from-universe")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public EngagementResponse createFromUniverse(@RequestBody @Valid EngagementCreateFromUniverseRequest req) {
        return engagementService.createFromUniverse(req);
    }

    @PatchMapping("/engagements/{id}")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public EngagementResponse updateEngagement(@PathVariable Long id, @RequestBody @Valid EngagementUpdateRequest req) {
        return engagementService.update(id, req);
    }

    @PostMapping("/engagements/{id}/transition")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public EngagementResponse transitionEngagement(@PathVariable Long id,
                                                    @RequestBody @Valid EngagementPhaseTransitionRequest req) {
        return engagementService.transitionPhase(id, req);
    }

    @DeleteMapping("/engagements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('AUDIT','DELETE')")
    public void deleteEngagement(@PathVariable Long id) {
        engagementService.softDelete(id);
    }

    @PostMapping("/engagements/{id}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public MilestoneResponse createMilestone(@PathVariable Long id, @RequestBody @Valid MilestoneCreateRequest req) {
        return engagementService.createMilestone(id, req);
    }

    @PostMapping("/engagements/{id}/milestones/record")
    @PreAuthorize("hasPermission('AUDIT','WRITE')")
    public MilestoneResponse recordMilestone(@PathVariable Long id, @RequestBody @Valid MilestoneRecordRequest req) {
        return engagementService.recordMilestone(id, req);
    }
}
