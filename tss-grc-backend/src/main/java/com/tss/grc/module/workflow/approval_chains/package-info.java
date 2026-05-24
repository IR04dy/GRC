/**
 * Sprint 13 — Multi-step Workflow Approval Chains.
 *
 * <p>Generic approval-chain engine that replaces the flat
 * "approved/rejected" outcome on every wizard submission with a
 * configurable ladder of approvals. Saudi-regulated banks need a
 * documented chain of approvers for material decisions: a CAPA
 * closure goes Risk Owner → Department Head → DPO → CISO; a
 * cross-border data-transfer goes DPO → Legal → Compliance Officer
 * → Board. This module owns the template, instance, action, and
 * scheduler that drive those ladders.
 *
 * <h3>Regulatory anchors</h3>
 * <ul>
 *   <li><b>SAMA Corporate Governance §5.3</b> — documented multi-step
 *       authorisation for material decisions.</li>
 *   <li><b>NCA ECC-2 control 1-3-2</b> — documented chain of approvals
 *       for cybersecurity exceptions.</li>
 *   <li><b>PDPL Article 32</b> — DPO sign-off chain before any
 *       cross-border data transfer.</li>
 * </ul>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code ApprovalChainTemplate} — slowly-changing ladder blueprint</li>
 *   <li>{@code ApprovalChainStep}     — one rung; SERIAL or PARALLEL</li>
 *   <li>{@code ApprovalChainInstance} — a live in-flight ladder</li>
 *   <li>{@code ApprovalChainAction}   — every actor decision per rung</li>
 * </ul>
 *
 * <h3>Instance state machine</h3>
 * <pre>
 *   PENDING     → IN_PROGRESS | WITHDRAWN | EXPIRED
 *   IN_PROGRESS → APPROVED | REJECTED | WITHDRAWN | EXPIRED
 *   APPROVED   (terminal)
 *   REJECTED   (terminal)
 *   WITHDRAWN  (terminal)
 *   EXPIRED    (terminal)
 * </pre>
 *
 * <h3>F7 + F8</h3>
 * <p>Every mutation writes an audit row AND publishes a canonical
 * {@code DomainEvent}: {@code APPROVAL_CHAIN_STARTED},
 * {@code APPROVAL_CHAIN_STEP_COMPLETED},
 * {@code APPROVAL_CHAIN_COMPLETED}, {@code APPROVAL_CHAIN_REJECTED},
 * {@code APPROVAL_CHAIN_ESCALATED}, {@code APPROVAL_CHAIN_DELEGATED}.
 *
 * @since Sprint 13 (V192)
 */
package com.tss.grc.module.workflow.approval_chains;
