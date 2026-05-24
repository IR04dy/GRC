/**
 * BCM Exercise Observer Dashboard — Slice 10-C.
 *
 * <p>Read-only live view of a running exercise for auditors, internal audit,
 * CISO, and regulator observers. Builds on top of Slice 10-B's
 * {@link com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun} machinery.
 *
 * <h3>Why</h3>
 * <p>SAMA BCF Annex C requires independent observers to score every full-scale
 * exercise. Today observers either crowd into the facilitator's console
 * (risking accidental writes) or watch over a participant's shoulder. This
 * slice gives them their own read-only dashboard with structured observation
 * capture and AAR feed-back.
 *
 * <h3>Two new entities</h3>
 * <ul>
 *   <li>{@link com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObserverAssignment} —
 *       the access-control gate. A facilitator / BCM_MANAGER / PLATFORM_ADMIN
 *       assigns a user as an observer on a specific run, tagged with the
 *       observer's role (INTERNAL_AUDIT, EXTERNAL_AUDIT, COMPLIANCE_OBSERVER,
 *       SAMA_LIAISON, NCA_LIAISON, BOARD_OBSERVER). Only assigned observers
 *       can record observations on a run.</li>
 *   <li>{@link com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObservation} —
 *       structured observer capture. Type + severity + free-text in the
 *       observer's preferred language, optionally linked to a specific
 *       run-inject. {@code is_flagged_for_aar} drives After-Action Report
 *       inclusion.</li>
 * </ul>
 *
 * <h3>READ-ONLY enforcement</h3>
 * <p>The observer dashboard does NOT expose any dispatch / transition endpoints.
 * The {@link com.tss.grc.module.bcm.cycle.runtime.observer.controller.ExerciseObserverController}
 * exposes only assignment management, observation capture, and read endpoints.
 * Dispatch + transition remain on the Slice 10-B
 * {@code ExerciseRuntimeController} which requires
 * {@code FACILITATOR / BCM_MANAGER / PLATFORM_ADMIN}.
 *
 * <h3>WebSocket topic reuse</h3>
 * <p>Observers subscribe to the same {@code /topic/org.{orgId}.exercise-run.{runId}}
 * topic from 10-B. No new topic is introduced. When the observer records an
 * observation, the service broadcasts an {@code OBSERVATION_ADDED} payload so
 * other observers and the facilitator console receive new observations live.
 *
 * <h3>AAR integration</h3>
 * <p>When a run ends (status=COMPLETED), the existing
 * {@code ExerciseRuntimeService.createAfterActionDraft()} hook is extended to
 * pull observations where {@code is_flagged_for_aar = 1} into the AAR draft's
 * findings section, grouped by type and severity.
 *
 * <h3>F7 + F8 parity</h3>
 * <p>Every observer-assignment, observation-record, and AAR-flag change writes
 * an audit row (F7) and publishes a
 * {@link com.tss.grc.module.platform.automation.event.DomainEvent} (F8):
 * {@code EXERCISE_OBSERVER_ASSIGNED}, {@code EXERCISE_OBSERVATION_RECORDED},
 * {@code EXERCISE_OBSERVATION_FLAGGED}.
 */
package com.tss.grc.module.bcm.cycle.runtime.observer;
