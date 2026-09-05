/**
 * AI/ML Model Lifecycle module — Sprint 31.
 *
 * <p>Extends the Sprint 11 {@code module.ai.governance.MlModel} registry
 * with the operational machinery the SDAIA AI Ethics Principles and the
 * incoming SAMA AI Use in Banking guidance expect: independent model
 * validation events, retraining cycles, periodic recertification,
 * and an annual ethics attestation ladder.
 *
 * <h2>Regulatory anchors</h2>
 * <ul>
 *   <li><b>SDAIA AI Ethics Principles</b> (2023) — accountability,
 *       fairness, privacy, reliability, safety, transparency,
 *       human-oversight, and societal-and-environmental wellbeing</li>
 *   <li><b>PDPL Article 4</b> automated decision-making safeguards</li>
 *   <li><b>SAMA Cyber Security Framework</b> §3.4 model-risk management
 *       cross-references for credit-decision and fraud-detection models</li>
 *   <li><b>EU AI Act</b> risk-tier alignment (already on MlModel.riskTier)</li>
 *   <li><b>NCA ECC-2 (2023)</b> §3-4 secure development for ML pipelines</li>
 * </ul>
 *
 * <h2>State machines</h2>
 *
 * Validation Event (independent validation per cycle):
 * <pre>
 *   SCHEDULED → IN_PROGRESS → PASSED|PASSED_WITH_CONDITIONS|FAILED
 *                                       ↓
 *                                    REMEDIATION → PASSED
 * </pre>
 *
 * Retraining Event:
 * <pre>
 *   PROPOSED → APPROVED → IN_PROGRESS → DEPLOYED → ROLLED_BACK
 *                ↓                       ↓
 *             REJECTED                ABANDONED
 * </pre>
 *
 * Recertification (periodic — typically annual for CRITICAL/HIGH-tier):
 * <pre>
 *   DUE → IN_REVIEW → RECERTIFIED|RECERTIFIED_WITH_CONDITIONS|REVOKED
 * </pre>
 *
 * <h2>Drift — what this module does and does not do</h2>
 *
 * <p>{@code MlModelDriftObservation} in {@code module/ai/governance} captures raw drift
 * and {@code MlModelDriftService} publishes {@code MODEL_DRIFT_OBSERVED} <b>on every
 * record</b>. There is no threshold-crossing event, and this module holds no drift code
 * of any kind — its only mention of drift is {@code RetrainingEvent.trigger}, where
 * {@code DRIFT} is one of the reasons a human may record for a retraining.
 *
 * <p><b>F2-467 — this section previously described a drift gate that does not
 * exist</b>, and was wrong three ways in three lines: it said this module "adds a
 * configurable {@code driftThresholdPct} per model" (no such field exists anywhere;
 * {@code ai/governance} has a {@code drift_threshold} column under another name), that
 * observations crossing it "emit {@code MODEL_DRIFT_THRESHOLD_BREACHED}" (that token's
 * only occurrence in the tree was the sentence itself), and that <b>AR-275 reacts to
 * it</b> — AR-275 exists and triggers on {@code MODEL_RETRAINING_PROPOSED}.
 * 🔑 A real rule citation is what made the claim harder to doubt than an
 * invented one would have been.
 */
package com.tss.grc.module.ai.lifecycle;
