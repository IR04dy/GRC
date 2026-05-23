/**
 * BCM Exercise Runtime — Slice 10-B.
 *
 * <p>Live-fire phase of an exercise: the facilitator console fires injects on
 * schedule (or on-demand) to participants, captures their responses and
 * decisions, and produces an after-action timeline that feeds the existing
 * AAR module.
 *
 * <p>Builds on top of Slice 10-A (the {@code bcm.exercise_scenario_inject}
 * library + {@code BcmExercise.scenarioId} link). Where Slice 10-A pre-stages
 * an exercise from a library scenario, Slice 10-B is the run-time that
 * actually plays the scenario through to a recorded timeline.
 *
 * <h3>Three new entities</h3>
 * <ul>
 *   <li>{@link com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun} —
 *       the "session" of an exercise; SCHEDULED → IN_PROGRESS → PAUSED →
 *       COMPLETED / ABORTED state machine.</li>
 *   <li>{@link com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunInject} —
 *       per-run instance of an inject; body cloned verbatim from the source
 *       library inject so editing the library does not rewrite history.</li>
 *   <li>{@link com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunResponse} —
 *       a participant's response to a dispatched inject; carries the elapsed
 *       seconds since dispatch.</li>
 * </ul>
 *
 * <h3>WebSocket topic</h3>
 * <p>Every {@code dispatchInject} call publishes a STOMP message to
 * {@code /topic/org.{orgId}.exercise-run.{runId}} via
 * {@link com.tss.grc.platform.realtime.RealtimeBroadcaster}. The Saudi-native
 * facilitator console subscribes to that topic to render the response feed
 * in real time, and the participant view subscribes to receive the inject
 * payload the moment it is dispatched.
 *
 * <h3>F7 + F8 parity</h3>
 * <p>Every state transition, inject dispatch, and response logs an audit row
 * (F7) and publishes a {@link com.tss.grc.module.platform.automation.event.DomainEvent}
 * (F8) — same pattern as Slice 10-A's {@code EXERCISE_INSTANTIATED_FROM_SCENARIO}.
 */
package com.tss.grc.module.bcm.cycle.runtime;
