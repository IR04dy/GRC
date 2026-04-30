/**
 * Workflow &amp; Case Management module.
 *
 * <p>Lightweight state-machine engine (no external BPMN dependency) that routes
 * approval and review workflows across all GRC modules. Supports SLA tracking,
 * escalation, delegation, and full audit history per workflow instance.</p>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code WorkflowDefinition} — reusable template (steps, transitions, SLA)</li>
 *   <li>{@code WorkflowInstance} — runtime instance bound to a GRC entity</li>
 *   <li>{@code WorkflowTask} — individual task assigned to a user or role</li>
 *   <li>{@code WorkflowTransition} — allowed state transitions per definition</li>
 *   <li>{@code WorkflowComment} — threaded comments per instance</li>
 * </ul>
 *
 * <h3>States</h3>
 * <pre>DRAFT → SUBMITTED → IN_PROGRESS → APPROVED | REJECTED | ESCALATED | CANCELLED</pre>
 *
 * <h3>SLA enforcement</h3>
 * <p>Spring {@code @Scheduled(fixedDelay = 900_000)} checks overdue tasks every 15 minutes
 * and auto-escalates to the defined escalation user/role.</p>
 */
package com.tss.grc.module.workflow;
