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
