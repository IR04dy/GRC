/**
 * Change Management (Change Enablement) — ITIL 4 / ISO 20000-1 §8.5.1, NCA ECC 2-10.
 *
 * <p>This file is the module contract. It is read by developers deciding whether a change belongs
 * here, and by anyone asking what the platform actually enforces — so <b>every claim in it is
 * gated</b> by {@code ChangeDocumentationFreshnessTest}. See the note at the bottom on why.
 *
 * <h3>The lifecycle — 16 states, and none of them is a bypass</h3>
 * <pre>
 *   DRAFT ─► SUBMITTED ─► IMPACT_ASSESSED ─► CAB_PENDING ─┬─► CAB_APPROVED ─► SCHEDULED ─┐
 *     │                                                   ├─► CAB_REJECTED ─► DRAFT      │
 *     │                                                   ├─► CAB_DEFERRED ──┐           │
 *     │                                                   └─► MORE_INFO_REQUIRED ─┤      │
 *     │                                                        (both re-table) ──┘       │
 *     └─► EMERGENCY_APPROVED ───────────────────────────────────────────────────────┐    │
 *                                                                                   ▼    ▼
 *                                              IMPLEMENTING ─┬─► IMPLEMENTED ─► POST_REVIEW ─► CLOSED
 *                                                            └─► ROLLED_BACK ─► POST_REVIEW | CLOSED
 *
 *   CANCELLED is reachable from eight states — DRAFT, SUBMITTED, IMPACT_ASSESSED, CAB_DEFERRED,
 *   MORE_INFO_REQUIRED, CAB_APPROVED, SCHEDULED, EMERGENCY_APPROVED — but NOT from CAB_PENDING:
 *   a change already before the committee leaves that state by a recorded decision.
 *   CLOSED and CANCELLED are terminal. Once IMPLEMENTING, the only exits are IMPLEMENTED and
 *   ROLLED_BACK: an executed change cannot be withdrawn, only recorded.
 * </pre>
 *
 * <p><b>{@code EMERGENCY_APPROVED} is not a CAB bypass, and this contract used to say it was.</b>
 * An emergency change skips the <em>pre</em>-authorisation sitting, not the authorisation: it is
 * stamped with a ratification clock on entry ({@code ck_change_ratification_clock} makes
 * {@code PENDING} without a due date unrepresentable), swept daily by
 * {@code EmergencyRatificationSlaScheduler}, and escalated to the CISO with an audit-ledger event
 * when the SLA lapses. Ratification is modelled <b>orthogonally</b> to {@code status}, because a
 * change is genuinely {@code IMPLEMENTED} <em>and</em> genuinely awaiting ratification, and one
 * column would have to discard one of those two facts (ADR-017 D-3).
 *
 * <h3>Who may authorise — and what happens when nobody has said</h3>
 * <p>{@code change_authorisation_matrix} maps <i>type × risk</i> to a <b>gate</b>
 * ({@code AUTO} / {@code CAB} / {@code ECAB}) and a minimum approval count. The gate resolves to a
 * real committee through {@code admin.committee_gate_map}, per tenant. There is deliberately
 * <b>no default</b>: a tenant that has not named its CAB gets a 409 naming the missing
 * configuration, because inventing an authority is worse than refusing to act on one (ADR-017 D-2,
 * and F2-96 — both gates once pointed at committees with zero voting seats while governance-health
 * reported healthy). Operators: {@code docs/change-manager/CAB_OPERATING_RUNBOOK.md}.
 *
 * <p>⚠ <b>Two routes satisfy that gate, and code which reads only one of them is wrong.</b> A change
 * reaches {@code CAB_APPROVED} either on recorded {@code change_approvals} votes or — when it has
 * been referred — on the committee's carried resolution, which is the system of record for that
 * route and writes <b>no</b> vote rows in this module at all. Ask
 * {@link com.tss.grc.module.change_mgmt.spi.CommitteePort#decisionApproves} rather than counting
 * votes; the reconciliation below was drafted counting votes only and would have reported every
 * committee-routed change — the platform's preferred path — as unauthorised.
 *
 * <p>⚠ <b>And it is two, not three — the workflow engine is not one of them.</b> S8h/CE-10 makes
 * change the 15th module to adopt {@code workflow.requests}, and the port that does it
 * ({@link com.tss.grc.module.change_mgmt.spi.WorkflowPort}) <b>refuses</b> the {@code APPROVAL}
 * request type outright. Measured 2026-08-05: {@code WorkflowRequestService} has no callback into any
 * of the 14 existing adopters and emits domain events only for {@code SENT} and {@code ESCALATED}, so
 * an engine request cannot move a change's status even in principle — but an approval-shaped one
 * would sit in the audit trail conferring no authority, and an auditor reconciling it against the
 * register above would find a contradiction with no explanation. The engine routes <em>work</em>;
 * authority stays with the CAB and the committee.
 *
 * <h3>Routing work, and refusing to route it into a void</h3>
 * <p>What G-10 actually names is task handling. {@code change_tasks} carries a due date and nothing
 * else — no assignee id, no SLA, no reminder, no escalation, no timeline — while
 * {@code workflow.requests} carries sixteen such columns. Measured: of 26 open change tasks, <b>23
 * are overdue and 0 have an owner the platform can notify</b> (14 labels match nothing at all, 12
 * name a department with no members, and 0 of 7 users carry a {@code department_id}), so CH-5's
 * {@code CHANGE_TASK_OVERDUE} sweep has been escalating to nobody since it shipped.
 *
 * <p>So {@code WorkflowPort.raise} refuses an addressee that reaches no active user rather than let
 * the engine stamp a recipient row {@code NOTIFIED} having notified no one, and
 * {@code /task-addressability} reports the gap per owner label — distinguishing a name that matches
 * nothing (a data fix) from a real-but-empty target (an appointment), because they need different
 * actions. The reachability rule itself lives in {@code WorkflowRequestService.reachOf}, beside the
 * fan-out it predicts: it was first drafted in {@code change_adapters} with its own copy of each rule
 * and the boundary ratchet rejected it, which was right — a mirror maintained by assertion lies
 * eventually.
 *
 * <h3>Reconciling what was authorised against what went ahead</h3>
 * <p>{@code UnauthorisedChangeRegisterService} (S8f/CE-11) answers "did anything go ahead without
 * authority?" for every change past its authorisation point. Findings are <b>derived on each read</b>
 * from the matrix, the approval trail, the committee route and the status, so a verdict cannot drift
 * from the rows behind it; only the investigator's outcome is stored
 * ({@code change_unauthorised_disposition}, V355) because a human judgement cannot be recomputed.
 *
 * <p>An <b>authority gap</b> (approvals short of the cell) and an <b>unattributed authorisation</b>
 * (approvals present, {@code cab_decision_at} absent — F2-103's legacy) are separate reason codes at
 * separate severities, and only the first is counted as an unauthorised change. A disposition
 * annotates a finding and never erases it; there is no {@code CLOSED} outcome and the KPI counts
 * disposed gaps, so the number cannot be cleared by signing findings off. Two failure classes are
 * declared <b>undetectable</b> in every response rather than omitted: an unticketed production change
 * (no drift feed exists) and implementation inside a freeze window
 * ({@code implementation_started} is NULL on every change, so the predicate would be UNKNOWN and
 * report compliance forever — the defect A17-D3 hit with {@code ratification_due_at}).
 *
 * <h3>Obligations and their proof</h3>
 * <p>A CAB may approve <em>subject to conditions</em> ({@code change_condition}). Imposing and
 * waiving are approval acts ({@code CHANGE:APPROVE}); recording one satisfied is operational work
 * ({@code CHANGE:WRITE}) — but it must carry evidence the evidence subsystem has <b>APPROVED</b>,
 * and {@code SCHEDULED → IMPLEMENTING} is a 409 while an obligation is outstanding. Evidence lives
 * in {@code module/evidence} via {@link com.tss.grc.module.change_mgmt.spi.EvidencePort}, hashed,
 * versioned and independently reviewed. ⚠ {@code change_links} also offers an {@code EVIDENCE}
 * entity type; that is a plain pointer with no hash, no review and no expiry, and using it for
 * evidence would be the deficiency ISO 20000 documented-information asks about, not the remedy.
 *
 * <h3>Peer modules — interfaces out, adapters elsewhere (ADR-012)</h3>
 * <p>This package imports <b>no</b> peer module. It declares outbound ports in {@code spi/}
 * ({@code CommitteePort}, {@code EvidencePort}, {@code CapaPort}, {@code IncidentPort}) and the
 * implementations live in {@code module/change_adapters}, outside both sides.
 * {@code ChangeModuleBoundaryTest} enforces the arrow direction. ⚠ It enforces it over
 * <b>imports</b>, so native SQL against a peer schema crosses the boundary unseen — F2-108, and the
 * CAB aggregate in {@code ChangeGovernanceHealthService} is the known instance.
 *
 * <h3>Generic cross-module linkage</h3>
 * <p>{@code change_mgmt.change_links} lets any module attach a typed pointer to one of its entities
 * (RISK, CONTROL, INCIDENT, VENDOR, BCM_PLAN, AUDIT_FINDING, POLICY, REGULATORY_CHANGE, DPIA, KRI,
 * ASSET, EVIDENCE) with a relationship (AFFECTS, TRIGGERED_BY, MITIGATES, TARGETS, REMEDIATES,
 * DEPENDS_ON, INFORMS).
 *
 * <h3>Entitlement</h3>
 * <p>Homed to the {@code CHANGE} SKU (ADR-018 §3), and so are the adapters. Both controllers carry
 * {@code @RequireModule("CHANGE")} — including {@code ChangeAuthorityController}, which lives here
 * rather than beside the committee rows it writes because a controller must name the SKU its
 * <em>package</em> is sold as ({@code EntitlementCoverageRatchetTest} rejected the other homing).
 * Its <em>permissions</em> are {@code ADMIN}, not {@code CHANGE}: naming which body holds an
 * authority is a charter act, and a {@code CHANGE:APPROVE} holder decides changes, not who decides
 * them. ⚠ One coupling is not fixable by an annotation — constituting a committee is licensed as
 * {@code BOARD}, so a tenant holding {@code CHANGE} alone can reach the screen with nothing to
 * select (ADR-017 D-2).
 *
 * <h3>Why this file is under test</h3>
 * <p>Before CE-16 this contract described a ten-state lifecycle and called the emergency route a
 * fast-path around the committee — five states short, and wrong about the one thing an auditor asks
 * first. A stale contract is worse than no contract: it is the {@code advisory}-javadoc failure of
 * F2-95 in documentation form, where the word in the comment <em>was</em> the finding. So the
 * transition table is the source of truth and the test fails if a state exists in code and not here.
 *
 * <p>The old sentence is quoted in full in that test's javadoc rather than here — a contract should
 * not carry its own retracted claim in a form a skimmer can mistake for the current one.
 */
package com.tss.grc.module.change_mgmt;
