/**
 * Sprint 13 — Internal Audit Annual Plan + Engagement Workflow.
 *
 * <p>Backs the V194 migration ({@code audit_lc.ia_annual_plan},
 * {@code audit_lc.ia_audit_universe_entry}, {@code audit_lc.ia_engagement},
 * {@code audit_lc.ia_engagement_milestone}).
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Plan: {@code DRAFT → BOARD_REVIEW → APPROVED → EXECUTING → COMPLETED → ARCHIVED}</li>
 *   <li>Engagement: {@code PLANNED → PLANNING → FIELDWORK → REPORTING → REPORT_ISSUED → FOLLOW_UP → CLOSED}</li>
 * </ul>
 *
 * <h3>Regulatory anchors</h3>
 * <ul>
 *   <li>SAMA Internal Audit Framework (2018) §3.1 — risk-based planning, AC approval.</li>
 *   <li>IIA Standards 2200-2600 — engagement lifecycle.</li>
 *   <li>CMA Listed-Companies CG Code Article 54 — Audit Committee review of IA plan.</li>
 * </ul>
 */
package com.tss.grc.module.audit_lc.ia_plan;
