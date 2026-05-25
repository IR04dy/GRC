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
 * <h2>Drift gate</h2>
 *
 * <p>Sprint 11's {@code MlModelDriftObservation} captures raw drift.
 * This module adds a configurable {@code driftThresholdPct} per model.
 * Observations crossing the threshold emit
 * {@code MODEL_DRIFT_THRESHOLD_BREACHED} which AR-275 reacts to.
 */
package com.tss.grc.module.ai.lifecycle;
