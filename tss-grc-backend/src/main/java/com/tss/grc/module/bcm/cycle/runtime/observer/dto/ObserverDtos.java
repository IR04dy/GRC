package com.tss.grc.module.bcm.cycle.runtime.observer.dto;

import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.InjectView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Slice 10-C — DTOs for the observer dashboard + observation capture.
 *
 * <p>Request shapes:
 * <ul>
 *   <li>{@link AssignObserverRequest} — POST /exercise-runs/{id}/observers</li>
 *   <li>{@link ObservationRequest}    — POST /exercise-runs/{id}/observations</li>
 *   <li>{@link FlagRequest}           — PUT  /exercise-runs/{id}/observations/{obsId}/flag</li>
 * </ul>
 *
 * <p>Response shapes:
 * <ul>
 *   <li>{@link AssignmentView}    — one row in the observer roster</li>
 *   <li>{@link ObservationView}   — one observation</li>
 *   <li>{@link ObserverDashboard} — full read-only view (run + injects + responses + observations)</li>
 * </ul>
 */
public final class ObserverDtos {

    private ObserverDtos() {}

    // ─── Requests ────────────────────────────────────────────────────────

    /** {@code POST /api/v1/bcm/exercise-runs/{runId}/observers}. */
    public record AssignObserverRequest(
        @NotNull(message = "observerUserId is required") Long observerUserId,
        /**
         * INTERNAL_AUDIT | EXTERNAL_AUDIT | COMPLIANCE_OBSERVER
         *   | SAMA_LIAISON | NCA_LIAISON | BOARD_OBSERVER
         */
        @NotBlank(message = "observerRole is required") String observerRole
    ) {}

    /** {@code POST /api/v1/bcm/exercise-runs/{runId}/observations}. */
    public record ObservationRequest(
        @Size(max = 2000) String observationTextEn,
        @Size(max = 2000) String observationTextAr,
        /**
         * STRENGTH | GAP | RISK | COMMUNICATION | DECISION_QUALITY
         *   | ESCALATION | RECOVERY_TIME | COMPLIANCE | GENERAL (default GENERAL).
         */
        String observationType,
        /** INFO | MINOR | MODERATE | MAJOR | CRITICAL (default INFO). */
        String severity,
        @Size(max = 2000) String recommendationEn,
        @Size(max = 2000) String recommendationAr,
        /** Optional — when set, links the observation to a specific run-inject. */
        Long relatedInjectId,
        /** When true, immediately flag for AAR inclusion. */
        Boolean flagForAar
    ) {}

    /** {@code PUT /api/v1/bcm/exercise-runs/{runId}/observations/{obsId}/flag}. */
    public record FlagRequest(
        @NotNull(message = "flagged is required") Boolean flagged
    ) {}

    // ─── Responses ───────────────────────────────────────────────────────

    public record AssignmentView(
        Long id,
        Long exerciseRunId,
        Long observerUserId,
        String observerRole,
        Long assignedByUserId,
        LocalDateTime assignedAt
    ) {}

    public record ObservationView(
        Long id,
        Long exerciseRunId,
        Long observerUserId,
        Long relatedInjectId,
        String observationTextEn,
        String observationTextAr,
        String observationType,
        String severity,
        String recommendationEn,
        String recommendationAr,
        LocalDateTime recordedAt,
        Integer timeOffsetMinutes,
        Boolean isFlaggedForAar,
        /** Set once promoted to a tracked BCM gap (B-E); null otherwise. */
        Long gapId
    ) {}

    /**
     * The full read-only observer dashboard payload — a single round-trip on
     * page load. Live updates afterwards come over the
     * {@code /topic/org.{orgId}.exercise-run.{runId}} WebSocket (reused from 10-B).
     */
    public record ObserverDashboard(
        RunSummary summary,
        /** All injects on the run, ordered chronologically (planned offset asc). */
        List<InjectView> injects,
        /** All responses, keyed by run-inject id, so the dashboard can render per-inject. */
        Map<Long, List<ResponseView>> responsesByInject,
        /** All observations from all observers, chronological. */
        List<ObservationView> observations,
        /** Observer roster — every assigned observer with role + assignment metadata. */
        List<AssignmentView> observers,
        /**
         * The {@code observerUserId} that requested the dashboard — the frontend
         * uses this to highlight the observer's own observations and to control
         * edit + flag affordances.
         */
        Long currentObserverUserId,
        /** Convenience — true when the requester has an active observer assignment. */
        Boolean isAssignedObserver
    ) {}

    /**
     * End-of-run scoring view: observations grouped by type. Returned by
     * {@code GET /exercise-runs/{id}/observations?groupBy=type}.
     */
    public record ObservationsByType(
        Long exerciseRunId,
        Map<String, List<ObservationView>> byType
    ) {}
}
