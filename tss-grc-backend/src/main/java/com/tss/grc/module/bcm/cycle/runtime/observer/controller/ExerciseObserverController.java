package com.tss.grc.module.bcm.cycle.runtime.observer.controller;

import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.AssignObserverRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.AssignmentView;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.FlagRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationView;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationsByType;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObserverDashboard;
import com.tss.grc.module.bcm.cycle.runtime.observer.service.ExerciseObserverService;
import com.tss.grc.module.bcm.cycle.entity.BcmGap;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Slice 10-C — REST surface for the observer dashboard.
 *
 * <pre>
 *   POST   /api/v1/bcm/exercise-runs/{runId}/observers
 *      Assign an observer. FACILITATOR / BCM_MANAGER / PLATFORM_ADMIN.
 *
 *   DELETE /api/v1/bcm/exercise-runs/{runId}/observers/{assignmentId}
 *      Remove an observer (soft-delete). Same auth.
 *
 *   GET    /api/v1/bcm/exercise-runs/{runId}/observers
 *      Roster of currently assigned observers. Broad read.
 *
 *   GET    /api/v1/bcm/exercise-runs/{runId}/observer-dashboard
 *      Full read-only dashboard. Open to assigned observers + auditors +
 *      BCM_MANAGER + CISO + PLATFORM_ADMIN.
 *
 *   POST   /api/v1/bcm/exercise-runs/{runId}/observations
 *      Record an observation. Service gates on active assignment for the
 *      requester — role floor here is broad to permit auditors who might
 *      have varying RBAC roles.
 *
 *   PUT    /api/v1/bcm/exercise-runs/{runId}/observations/{obsId}/flag
 *      Toggle AAR flag (author-only — service enforces).
 *
 *   GET    /api/v1/bcm/exercise-runs/{runId}/observations
 *      List all observations.
 *
 *   GET    /api/v1/bcm/exercise-runs/{runId}/observations/by-type
 *      Observations grouped by type — drives the end-of-run scoring screen.
 * </pre>
 *
 * <h3>READ-ONLY enforcement</h3>
 * <p>This controller does NOT expose any dispatch or transition endpoints.
 * Those remain on the Slice 10-B {@code ExerciseRuntimeController} which
 * requires {@code FACILITATOR / BCM_MANAGER / PLATFORM_ADMIN}. An observer
 * accidentally hitting a write endpoint will receive 403 from the security
 * filter chain — there is no observer-flavoured dispatch path at all.
 */
@RestController
@RequestMapping("/api/v1/bcm/exercise-runs")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Exercise Observer Dashboard (Slice 10-C)")
@SecurityRequirement(name = "bearerAuth")
public class ExerciseObserverController {

    private final ExerciseObserverService service;

    // ─── Observer assignment ────────────────────────────────────────────

    @PostMapping("/{runId}/observers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FACILITATOR','BCM_MANAGER','PLATFORM_ADMIN')")
    public AssignmentView assignObserver(
        @PathVariable Long runId,
        @RequestBody @Valid AssignObserverRequest req) {
        return service.assignObserver(runId, req);
    }

    @DeleteMapping("/{runId}/observers/{assignmentId}")
    @PreAuthorize("hasAnyRole('FACILITATOR','BCM_MANAGER','PLATFORM_ADMIN')")
    public ResponseEntity<Void> removeObserver(
        @PathVariable Long runId,
        @PathVariable Long assignmentId) {
        service.removeObserver(runId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{runId}/observers")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR',"
                + "'INTERNAL_AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public List<AssignmentView> listObservers(@PathVariable Long runId) {
        return service.listObservers(runId);
    }

    // ─── Observer dashboard (read-only mirror) ──────────────────────────

    @GetMapping("/{runId}/observer-dashboard")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR',"
                + "'INTERNAL_AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public ObserverDashboard getObserverDashboard(
        @PathVariable Long runId,
        @RequestParam(value = "observerUserId", required = false) Long observerUserId) {
        // Default to the authenticated user — the frontend may also pass it
        // explicitly when a BCM_MANAGER is viewing as an observer.
        Long uid = observerUserId != null
            ? observerUserId
            : ExerciseObserverService.currentUserId();
        return service.getObserverDashboard(runId, uid);
    }

    // ─── Observation capture (gated on assignment in the service) ───────

    @PostMapping("/{runId}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR',"
                + "'INTERNAL_AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public ObservationView recordObservation(
        @PathVariable Long runId,
        @RequestBody @Valid ObservationRequest req) {
        return service.recordObservation(runId, req);
    }

    @PutMapping("/{runId}/observations/{obsId}/flag")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR',"
                + "'INTERNAL_AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public ObservationView flagObservation(
        @PathVariable Long runId,
        @PathVariable Long obsId,
        @RequestBody @Valid FlagRequest req) {
        return service.flagObservationForAar(runId, obsId, req);
    }

    @PostMapping("/{runId}/observations/{obsId}/promote-to-gap")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR',"
                + "'INTERNAL_AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    @Operation(summary = "Promote an exercise observation to a tracked BCM gap (B-E)")
    public BcmGap promoteObservationToGap(@PathVariable Long runId, @PathVariable Long obsId) {
        return service.promoteObservationToGap(runId, obsId);
    }

    @GetMapping("/{runId}/observations")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR',"
                + "'INTERNAL_AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public List<ObservationView> listObservations(@PathVariable Long runId) {
        return service.listObservations(runId);
    }

    @GetMapping("/{runId}/observations/by-type")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','FACILITATOR','CISO','AUDITOR',"
                + "'INTERNAL_AUDITOR','PLATFORM_ADMIN','RISK_MANAGER')")
    public ObservationsByType observationsByType(@PathVariable Long runId) {
        return service.getObservationsByType(runId);
    }
}
