/**
 * Sprint 13 — Outsourcing Arrangement Register (SAMA Rules Governing
 * Outsourcing 2019/2023).
 *
 * <p>This module is the structured replacement for the Procurement
 * Excel sheet every Saudi bank keeps to satisfy SAMA Outsourcing §3
 * (register + risk assessment) and §4.2 (material outsourcing needs
 * SAMA non-objection). It coexists with the broader
 * {@link com.tss.grc.module.tprm} vendor registry — a vendor and an
 * outsourcing arrangement are not identical: one outsourcing
 * arrangement may invoke multiple vendors, and one vendor may
 * underpin several arrangements.
 *
 * <h3>Four tables</h3>
 * <ul>
 *   <li>{@code tprm.outsourcing_arrangement} — the canonical
 *       arrangement record (one row per arrangement). Carries the
 *       materiality flag, SAMA non-objection details, contract
 *       financials, PDPL data-flow facts, and the lifecycle stage.</li>
 *   <li>{@code tprm.outsourcing_due_diligence} — multi-row history of
 *       six-axis due-diligence scoring (financial, operational, cyber,
 *       BCM, PDPL, reputation). Composite is the simple average.</li>
 *   <li>{@code tprm.outsourcing_monitoring_event} — ongoing-monitoring
 *       events flagged during BAU (SLA breach, audit report, financial
 *       health flag, regulatory query, change of control...).</li>
 *   <li>{@code tprm.outsourcing_exit_plan} — at-most-one per
 *       arrangement; trigger conditions + ordered exit steps (JSON) +
 *       data-repatriation plan + test cadence.</li>
 * </ul>
 *
 * <h3>F7 + F8 events</h3>
 * The service layer emits the following F8 domain events (also written
 * to the F7 audit log via {@code AuditLogService.log}):
 * <ul>
 *   <li>{@code OUTSOURCING_REGISTERED} — new arrangement entered</li>
 *   <li>{@code OUTSOURCING_LIFECYCLE_TRANSITION} — stage change</li>
 *   <li>{@code OUTSOURCING_MATERIAL_CHANGE} — materiality flag flipped</li>
 *   <li>{@code OUTSOURCING_REVIEWED} — periodic review recorded</li>
 *   <li>{@code OUTSOURCING_SAMA_NOTIFICATION_REQUIRED} — material
 *       arrangement needs SAMA non-objection but hasn't been filed</li>
 *   <li>{@code OUTSOURCING_DD_COMPLETED} — due-diligence assessment
 *       saved</li>
 *   <li>{@code OUTSOURCING_MONITORING_EVENT_RECORDED} — monitoring
 *       event logged</li>
 *   <li>{@code OUTSOURCING_SLA_BREACH} — emitted on SLA_BREACH event
 *       type, feeds the Audit Committee action queue</li>
 *   <li>{@code OUTSOURCING_EXIT_TESTED} — exit-plan rehearsal logged</li>
 * </ul>
 */
package com.tss.grc.module.tprm.outsourcing;
