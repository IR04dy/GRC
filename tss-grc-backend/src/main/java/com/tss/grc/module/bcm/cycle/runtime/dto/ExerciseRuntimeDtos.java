package com.tss.grc.module.bcm.cycle.runtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Slice 10-B — DTOs for the exercise runtime + facilitator console.
 *
 * <p>Request shapes:
 * <ul>
 *   <li>{@link StartRunRequest}        — POST /exercise-runs</li>
 *   <li>{@link CustomInjectRequest}    — POST /exercise-runs/{id}/custom-inject</li>
 *   <li>{@link ResponseRequest}        — POST /exercise-runs/{id}/injects/{injectId}/responses</li>
 *   <li>{@link TransitionRequest}      — POST /exercise-runs/{id}/transition?action=…</li>
 *   <li>{@link EndRunRequest}          — body for END transition (carries summary)</li>
 * </ul>
 *
 * <p>Response shapes:
 * <ul>
 *   <li>{@link RunSummary}             — list-row + start-run echo</li>
 *   <li>{@link RunDetail}              — single-page detail incl. inject queue</li>
 *   <li>{@link InjectView}             — one inject in the run's queue or timeline</li>
 *   <li>{@link ResponseView}           — one participant response</li>
 *   <li>{@link TimelineEntry}          — chronological event in the after-action timeline</li>
 *   <li>{@link RunTimeline}            — the {@code GET /timeline} envelope</li>
 * </ul>
 */
public final class ExerciseRuntimeDtos {

    private ExerciseRuntimeDtos() {}

    // ─── Requests ────────────────────────────────────────────────────────

    /** {@code POST /api/v1/bcm/exercise-runs} — start a new run. */
    public record StartRunRequest(
        @NotNull(message = "exerciseId is required") Long exerciseId,
        Long facilitatorUserId,
        /** IN_PERSON | VIRTUAL_MEET | HYBRID | CHAT_ONLY (default IN_PERSON). */
        String channel,
        Integer participantCount,
        String facilitatorNotesEn,
        String facilitatorNotesAr,
        /** Optional override; otherwise auto-generated. */
        String runCode
    ) {}

    /** {@code POST .../custom-inject} — facilitator throws an on-the-fly inject. */
    public record CustomInjectRequest(
        @NotBlank(message = "injectTextEn is required")
        @Size(max = 2000) String injectTextEn,
        @Size(max = 2000) String injectTextAr,
        @Size(max = 1000) String expectedActionEn,
        @Size(max = 1000) String expectedActionAr,
        /** Defaults to "CUSTOM" if omitted. */
        String injectType,
        /** Defaults to the elapsed minutes since the run started. */
        Integer plannedTimeOffsetMinutes,
        /** When true, dispatch immediately on creation. Defaults to true. */
        Boolean dispatchNow
    ) {}

    /**
     * {@code POST .../injects/{id}/responses}.
     *
     * <p>⚠ F2-226 — there is deliberately NO {@code participantUserId} here. It used to be
     * {@code @NotNull(message = "participantUserId is required")}, so the server demanded an
     * attribution it never verified, while stamping {@code responseAt} from its own clock and
     * COMPUTING the reaction time it certifies. The participant is now the authenticated
     * principal ({@code CurrentActor.userId()}). Do not re-add it: a required field the server
     * discards tells the caller their value matters (F2-184), and one it trusts for the
     * attribution on ISO 22301 exercise evidence is worse again.
     */
    public record ResponseRequest(
        @Size(max = 2000) String responseTextEn,
        @Size(max = 2000) String responseTextAr,
        @Size(max = 1000) String decisionMadeEn,
        @Size(max = 1000) String decisionMadeAr
    ) {}

    /** Query-param {@code action} on {@code POST .../transition?action=…}. */
    public record TransitionRequest(
        /** Optional summary text appended to facilitator_notes_en on END. */
        String summaryEn,
        String summaryAr
    ) {}

    // ─── Responses ───────────────────────────────────────────────────────

    public record RunSummary(
        Long id,
        Long exerciseId,
        String runCode,
        String status,
        String channel,
        Long facilitatorUserId,
        Integer participantCount,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        Integer totalInjects,
        Integer dispatchedInjects,
        Integer pendingInjects
    ) {}

    public record InjectView(
        Long id,
        Long sourceInjectId,
        Integer plannedTimeOffsetMinutes,
        LocalDateTime actualDispatchedAt,
        String injectType,
        String injectTextEn,
        String injectTextAr,
        String expectedActionEn,
        String expectedActionAr,
        String status,
        Integer responseCount
    ) {}

    public record ResponseView(
        Long id,
        Long runInjectId,
        Long participantUserId,
        String responseTextEn,
        String responseTextAr,
        String decisionMadeEn,
        String decisionMadeAr,
        LocalDateTime responseAt,
        Integer responseTimeSeconds,
        BigDecimal qualityScore,
        String facilitatorCommentEn,
        String facilitatorCommentAr
    ) {}

    public record RunDetail(
        RunSummary summary,
        String facilitatorNotesEn,
        String facilitatorNotesAr,
        List<InjectView> injects
    ) {}

    /**
     * One row in the chronological after-action timeline. {@code kind} marks
     * what the entry represents:
     * <ul>
     *   <li>{@code INJECT_DISPATCHED}  — the inject was sent to participants</li>
     *   <li>{@code RESPONSE}           — a participant responded</li>
     *   <li>{@code STATE_CHANGE}       — the run paused / resumed / ended</li>
     *   <li>{@code CUSTOM_INJECT}      — facilitator added an on-the-fly inject</li>
     * </ul>
     */
    public record TimelineEntry(
        LocalDateTime at,
        String kind,
        Long runInjectId,
        Long responseId,
        Integer offsetMinutesPlanned,
        Integer responseTimeSeconds,
        String injectType,
        String label,
        String detailEn,
        String detailAr
    ) {}

    public record RunTimeline(
        Long runId,
        String runCode,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        List<TimelineEntry> entries
    ) {}
}
