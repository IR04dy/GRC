package com.tss.grc.module.bcm.cycle.runtime.controller;

import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.CustomInjectRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.InjectView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunDetail;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunSummary;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunTimeline;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.StartRunRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.TransitionRequest;
import com.tss.grc.module.bcm.cycle.runtime.service.ExerciseRuntimeService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Slice 10-B — REST surface for the exercise runtime + facilitator console.
 *
 * <pre>
 *   POST   /api/v1/bcm/exercise-runs
 *      Start a new run. BCM_MANAGER / PLATFORM_ADMIN (no run exists yet, so nobody facilitates it).
 *
 *   GET    /api/v1/bcm/exercise-runs/{id}
 *      Full detail (header + inject queue).
 *
 *   GET    /api/v1/bcm/exercise-runs/by-exercise/{exerciseId}
 *      List runs for an exercise.
 *
 *   GET    /api/v1/bcm/exercise-runs/{id}/timeline
 *      Chronological after-action timeline.
 *
 *   POST   /api/v1/bcm/exercise-runs/{id}/injects/{injectId}/dispatch
 *      Dispatch a specific inject. BCM_MANAGER / PLATFORM_ADMIN, or THIS RUN's facilitator.
 *
 *   POST   /api/v1/bcm/exercise-runs/{id}/injects/{injectId}/responses
 *      Record a participant response. Any authenticated user.
 *
 *   GET    /api/v1/bcm/exercise-runs/{id}/injects/{injectId}/responses
 *      List responses for an inject.
 *
 *   POST   /api/v1/bcm/exercise-runs/{id}/custom-inject
 *      Add an on-the-fly inject. BCM_MANAGER / PLATFORM_ADMIN, or THIS RUN's facilitator.
 *
 *   POST   /api/v1/bcm/exercise-runs/{id}/transition?action=PAUSE|RESUME|END|ABORT
 *      State machine. BCM_MANAGER / PLATFORM_ADMIN, or THIS RUN's facilitator.
 * </pre>
 *
 * <p><b>F2-161 — this is no longer a pure {@code hasAnyRole(…)} surface.</b> Every route that
 * names a single run now also admits <em>that run's</em> facilitator, via
 * {@code hasPermission(#id, 'EXERCISE_RUN', 'OWNER')} against
 * {@code exercise_run.facilitator_user_id} (populated 4/4).
 *
 * <p>⚠ The guards used to recite {@code FACILITATOR}, which matches no row in {@code iam.roles},
 * alongside {@code BCM_MANAGER}, which has <b>0 users</b> — so dispatching an inject, adding a
 * custom inject and transitioning the run were reachable by the <b>platform administrator and nobody
 * else</b>, while the person actually facilitating could only read. For a business-continuity tool
 * that is the live-exercise path: the screen someone is on while the clock runs.
 *
 * <p>⚠ Three routes stay role-only and <em>cannot</em> be bound: {@code POST /} (the run does not
 * exist yet), {@code GET /} (a collection) and {@code by-exercise/{exerciseId}} (one exercise has
 * many runs, possibly with different facilitators, so the exercise id cannot answer the question).
 * Spring Security still normalises role authorities with the {@code ROLE_} prefix at user-detail
 * load time; the ownership leg goes through {@code ModulePermissionEvaluator} instead.
 */
@RestController
@RequestMapping("/api/v1/bcm/exercise-runs")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Exercise Runtime (Slice 10-B)")
@SecurityRequirement(name = "bearerAuth")
public class ExerciseRuntimeController {

    private final ExerciseRuntimeService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // F2-161: names no single run, so there is no facilitator to be — role-only, with the
    // ghost FACILITATOR removed (it matched no row in iam.roles and admitted nobody).
    // F2-254: ...and BCM_MANAGER is held by NO user either, in dev or prod. The comment above
    // removed FACILITATOR for matching no row in iam.roles and "admitting nobody" - and kept a name
    // with exactly that property, because "does this name exist?" was the only question being asked.
    // KEY: this is the one route on this controller the ownership leg CANNOT rescue. Its three
    // sibling writes are `hasAnyRole(...) or hasPermission(#id,'EXERCISE_RUN','OWNER')`, so the
    // facilitator drives them; #start names no run, so there is no record to own yet. Probed live:
    // `ranalyst` (no BCM grant at all) transitions the run they facilitate and is 403 on #start.
    // A facilitator could drive an exercise run they were not allowed to create.
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public RunSummary start(@RequestBody @Valid StartRunRequest req) {
        return service.startRun(req);
    }

    @GetMapping
    // F2-161: names no single run, so there is no facilitator to be — role-only, with the
    // ghost FACILITATOR removed (it matched no row in iam.roles and admitted nobody).
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    @Operation(summary = "List all exercise runs for the org (optional status filter)")
    public List<RunSummary> list(@RequestParam(required = false) String status) {
        return service.listAll(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN',"
                + "'RISK_MANAGER') or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')")
    public RunDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/by-exercise/{exerciseId}")
    // F2-161: scoped by EXERCISE, not run. One exercise has many runs and they may have
    // different facilitators, so #exerciseId cannot answer "do you facilitate this run".
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public List<RunSummary> listForExercise(@PathVariable Long exerciseId) {
        return service.listForExercise(exerciseId);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN',"
                + "'RISK_MANAGER') or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')")
    public RunTimeline timeline(@PathVariable Long id) {
        return service.getRunTimeline(id);
    }

    @PostMapping("/{id}/injects/{injectId}/dispatch")
    @PreAuthorize("hasPermission('BCM','WRITE') "
                + "or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')")
    public InjectView dispatch(@PathVariable Long id, @PathVariable Long injectId) {
        return service.dispatchInject(id, injectId);
    }

    @PostMapping("/{id}/injects/{injectId}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ResponseView respond(
        @PathVariable Long id,
        @PathVariable Long injectId,
        @RequestBody @Valid ResponseRequest req) {
        return service.recordResponse(id, injectId, req);
    }

    @GetMapping("/{id}/injects/{injectId}/responses")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','AUDITOR','PLATFORM_ADMIN',"
                + "'RISK_MANAGER') or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')")
    public List<ResponseView> listResponses(@PathVariable Long id, @PathVariable Long injectId) {
        return service.listResponses(id, injectId);
    }

    @PostMapping("/{id}/custom-inject")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('BCM','WRITE') "
                + "or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')")
    public InjectView customInject(
        @PathVariable Long id,
        @RequestBody @Valid CustomInjectRequest req) {
        return service.addCustomInject(id, req);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('BCM','WRITE') "
                + "or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')")
    public RunSummary transition(
        @PathVariable Long id,
        @RequestParam("action") String action,
        @RequestBody(required = false) TransitionRequest req) {
        return service.transition(id, action, req);
    }
}
