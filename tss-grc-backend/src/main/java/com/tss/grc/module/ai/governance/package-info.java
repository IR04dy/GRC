/**
 * AI/ML Model Registry — Sprint 11 strategic deliverable.
 *
 * <p>Tri-track regulatory driver: every model serving a regulated
 * business decision must be inventoried, validated and monitored for
 * drift.
 *
 * <ul>
 *   <li><b>SDAIA AI Ethics Principles</b> (October 2023) — Principle 4
 *       (Transparency &amp; Explainability) requires a central register
 *       of every AI/ML system in production.</li>
 *   <li><b>SAMA AI Governance Principles</b> (December 2024 update) —
 *       inventory + validation gates + post-market drift monitoring
 *       for any model touching a regulated banking decision.</li>
 *   <li><b>EU AI Act Article 16</b> — high-risk AI systems (credit
 *       scoring is the headline example) require risk-management
 *       documentation plus post-market monitoring. Applies to the
 *       bank's European subsidiaries.</li>
 * </ul>
 *
 * <p>Three JPA entities cover the model:
 * <ul>
 *   <li>{@link com.tss.grc.module.ai.governance.entity.MlModel} —
 *       inventory header, lifecycle stage, compliance flags.</li>
 *   <li>{@link com.tss.grc.module.ai.governance.entity.MlModelAudit} —
 *       fairness/bias/explainability/robustness/privacy/security/performance
 *       audit results with metrics_json and optional CAPA link.</li>
 *   <li>{@link com.tss.grc.module.ai.governance.entity.MlModelDriftObservation}
 *       — post-market monitoring observations (data drift, concept
 *       drift, performance degradation, fairness regression) with
 *       acknowledgement workflow.</li>
 * </ul>
 *
 * <p>Lifecycle state machine:
 * <pre>
 *   PROPOSED → DEVELOPMENT → VALIDATION → APPROVED_FOR_PROD
 *                                          ↓
 *                          MONITORING ←─ IN_PRODUCTION
 *                                ↓               ↑
 *                            REVISION ──────────┘
 *                                ↓
 *                         DECOMMISSIONED
 * </pre>
 *
 * <p>Gate at APPROVED_FOR_PROD when {@code risk_tier ∈ {CRITICAL, HIGH}}:
 * <ul>
 *   <li>{@code validator_user_id} and {@code validation_date} are set</li>
 *   <li>{@code has_fairness_audit = true}</li>
 *   <li>{@code has_pdpl_dpia = true}</li>
 *   <li>{@code has_explainability_doc = true}</li>
 * </ul>
 *
 * <p>F8 events emitted: {@code MODEL_REGISTERED},
 * {@code MODEL_LIFECYCLE_TRANSITION}, {@code MODEL_DECOMMISSIONED},
 * {@code MODEL_AUDIT_RECORDED}, {@code MODEL_DRIFT_OBSERVED}.
 *
 * <p>When a {@code CRITICAL} drift observation is recorded the service
 * auto-creates an intake Issue (kind {@code RISK_ISSUE}) and flips the
 * parent model's lifecycle stage to {@code REVISION}.
 */
package com.tss.grc.module.ai.governance;
