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
 *      Start a new run. BCM_MANAGER / FACILITATOR / PLATFORM_ADMIN.
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
 *      Dispatch a specific inject. FACILITATOR / BCM_MANAGER / PLATFORM_ADMIN.
 *
 *   POST   /api/v1/bcm/exercise-runs/{id}/injects/{injectId}/responses
 *      Record a participant response. Any authenticated user.
 *
 *   GET    /api/v1/bcm/exercise-runs/{id}/injects/{injectId}/responses
 *      List responses for an inject.
 *
 *   POST   /api/v1/bcm/exercise-runs/{id}/custom-inject
 *      Add an on-the-fly inject. FACILITATOR / BCM_MANAGER / PLATFORM_ADMIN.
 *
 *   POST   /api/v1/bcm/exercise-runs/{id}/transition?action=PAUSE|RESUME|END|ABORT
 *      State machine. FACILITATOR / BCM_MANAGER / PLATFORM_ADMIN.
 * </pre>
 *
 * <p>Same {@code hasAnyRole(…)} pattern as Slice 10-A's
 * {@code ExerciseScenarioLibraryController} — Spring Security normalises
 * authorities with the {@code ROLE_} prefix at user-detail load time.
 */
@RestController
@RequestMapping("/api/v1/bcm/exercise-runs")
@RequiredArgsConstructor
@Tag(name = "BCM — Exercise Runtime (Slice 10-B)")
@SecurityRequirement(name = "bearerAuth")
public class ExerciseRuntimeController {

    private final ExerciseRuntimeService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','PLATFORM_ADMIN')")
    public RunSummary start(@RequestBody @Valid StartRunRequest req) {
        return service.startRun(req);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    @Operation(summary = "List all exercise runs for the org (optional status filter)")
    public List<RunSummary> list(@RequestParam(required = false) String status) {
        return service.listAll(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public RunDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/by-exercise/{exerciseId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public List<RunSummary> listForExercise(@PathVariable Long exerciseId) {
        return service.listForExercise(exerciseId);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public RunTimeline timeline(@PathVariable Long id) {
        return service.getRunTimeline(id);
    }

    @PostMapping("/{id}/injects/{injectId}/dispatch")
    @PreAuthorize("hasAnyRole('FACILITATOR','BCM_MANAGER','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public List<ResponseView> listResponses(@PathVariable Long id, @PathVariable Long injectId) {
        return service.listResponses(id, injectId);
    }

    @PostMapping("/{id}/custom-inject")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FACILITATOR','BCM_MANAGER','PLATFORM_ADMIN')")
    public InjectView customInject(
        @PathVariable Long id,
        @RequestBody @Valid CustomInjectRequest req) {
        return service.addCustomInject(id, req);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('FACILITATOR','BCM_MANAGER','PLATFORM_ADMIN')")
    public RunSummary transition(
        @PathVariable Long id,
        @RequestParam("action") String action,
        @RequestBody(required = false) TransitionRequest req) {
        return service.transition(id, action, req);
    }
}
