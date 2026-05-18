package com.tss.grc.module.bcm.controller;

import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.service.BcmService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Legacy BCM REST controller — audit T1.1.
 *
 * <p>Surfaces the operational CRUD endpoints used by the {@code /bcm/legacy}
 * UI page. New work should call the cycle controllers under
 * {@code module/bcm/cycle/controller/}:
 * <ul>
 *   <li>{@code /api/v1/bcm/cycles}           — assessment cycles + governance</li>
 *   <li>{@code /api/v1/bcm/bia-assessments}  — BIA wizard</li>
 *   <li>{@code /api/v1/bcm/risks}            — BCM risk register</li>
 *   <li>{@code /api/v1/bcm/strategies}       — continuity strategies</li>
 *   <li>{@code /api/v1/bcm/crisis-events}    — crisis events with SitReps</li>
 *   <li>{@code /api/v1/bcm/{lessons,gaps,capas}}  — remediation</li>
 *   <li>{@code /api/v1/bcm/compliance}       — regulator mapping</li>
 *   <li>{@code /api/v1/bcm/settings}         — tenant configuration</li>
 *   <li>{@code /api/v1/bcm/reports}          — 15-report generator</li>
 * </ul>
 * Cycle controllers carry F7 audit + F8 event publication that this
 * legacy surface lacks. The two coexist by design until a future migration
 * sprint moves {@code BcmPlan} rows into the cycle BCP entity.
 */
@Deprecated(since = "BCM consolidation T1.1", forRemoval = false)
@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@Tag(name = "BCM (legacy)")
@SecurityRequirement(name = "bearerAuth")
public class BcmController {

    private final BcmService bcmService;

    // ── BCM Plans ─────────────────────────────────────────────────────────────

    @GetMapping("/legacy-plans")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Page<BcmPlan> listPlans(@PageableDefault(size = 20) Pageable pageable) {
        return bcmService.listPlans(pageable);
    }

    @GetMapping("/legacy-plans/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcmPlan getPlan(@PathVariable Long id) {
        return bcmService.getPlan(id);
    }

    @PostMapping("/legacy-plans")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public BcmPlan createPlan(@RequestBody BcmPlan req) {
        return bcmService.createPlan(req);
    }

    @PutMapping("/legacy-plans/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmPlan updatePlan(@PathVariable Long id, @RequestBody BcmPlan req) {
        return bcmService.updatePlan(id, req);
    }

    @DeleteMapping("/legacy-plans/{id}")
    @PreAuthorize("hasPermission('BCM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable Long id) {
        bcmService.deletePlan(id);
    }

    // ── Plan workflow actions (ISO 22301 lifecycle) ──────────────────────────
    @PostMapping("/legacy-plans/{id}/submit-for-review")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmPlan submitPlanForReview(@PathVariable Long id) {
        return bcmService.submitForReview(id);
    }

    @PostMapping("/legacy-plans/{id}/approve")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmPlan approvePlan(@PathVariable Long id) {
        return bcmService.approvePlan(id);
    }

    @PostMapping("/legacy-plans/{id}/invoke")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmPlan invokePlan(@PathVariable Long id) {
        return bcmService.invokePlan(id);
    }

    @PostMapping("/legacy-plans/{id}/restore")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmPlan restorePlan(@PathVariable Long id) {
        return bcmService.restorePlan(id);
    }

    // ── Critical Processes ────────────────────────────────────────────────────

    @GetMapping("/processes")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Page<CriticalProcess> listProcesses(@PageableDefault(size = 20) Pageable pageable) {
        return bcmService.listProcesses(pageable);
    }

    @GetMapping("/processes/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public CriticalProcess getProcess(@PathVariable Long id) {
        return bcmService.getProcess(id);
    }

    @PostMapping("/processes")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CriticalProcess createProcess(@RequestBody CriticalProcess req) {
        return bcmService.createProcess(req);
    }

    @PutMapping("/processes/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public CriticalProcess updateProcess(@PathVariable Long id, @RequestBody CriticalProcess req) {
        return bcmService.updateProcess(id, req);
    }

    @DeleteMapping("/processes/{id}")
    @PreAuthorize("hasPermission('BCM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProcess(@PathVariable Long id) {
        bcmService.deleteProcess(id);
    }

    // ── BIA Records ───────────────────────────────────────────────────────────

    @GetMapping("/bia")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BiaRecord> listBia(@RequestParam(required = false) Long processId) {
        return bcmService.listBia(processId);
    }

    @PostMapping("/bia")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public BiaRecord createBia(@RequestBody BiaRecord req) {
        return bcmService.createBia(req);
    }

    @PutMapping("/bia/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BiaRecord updateBia(@PathVariable Long id, @RequestBody BiaRecord req) {
        return bcmService.updateBia(id, req);
    }

    @DeleteMapping("/bia/{id}")
    @PreAuthorize("hasPermission('BCM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBia(@PathVariable Long id) {
        bcmService.deleteBia(id);
    }

    // ── Exercises ────────────────────────────────────────────────────────────

    @GetMapping("/exercises")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Page<BcmExercise> listExercises(@PageableDefault(size = 50) Pageable pageable) {
        return bcmService.listExercises(pageable);
    }

    @GetMapping("/exercises/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcmExercise getExercise(@PathVariable Long id) {
        return bcmService.getExercise(id);
    }

    @PostMapping("/exercises")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public BcmExercise createExercise(@RequestBody BcmExercise req) {
        return bcmService.createExercise(req);
    }

    @PutMapping("/exercises/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmExercise updateExercise(@PathVariable Long id, @RequestBody BcmExercise req) {
        return bcmService.updateExercise(id, req);
    }

    @DeleteMapping("/exercises/{id}")
    @PreAuthorize("hasPermission('BCM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExercise(@PathVariable Long id) {
        bcmService.deleteExercise(id);
    }

    @PostMapping("/exercises/{id}/complete")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmExercise completeExercise(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer score = body.get("score") instanceof Number n ? n.intValue() : null;
        String findings = body.get("findings") instanceof String s ? s : null;
        return bcmService.completeExercise(id, score, findings);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Map<String, Long> getStats() {
        return bcmService.getStats();
    }
}
