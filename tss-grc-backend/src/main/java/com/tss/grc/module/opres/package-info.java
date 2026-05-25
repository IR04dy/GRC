/**
 * Operational Resilience Self-Assessment — Sprint 14.
 *
 * <p>Closes the SAMA Operational Resilience Framework 2024 gap. The
 * platform now lets each Saudi-licensed entity assess its
 * operational-resilience maturity across SAMA's 5 pillars on a
 * 5-level maturity ladder, set per-pillar improvement targets, and
 * track progress between annual cycles.
 *
 * <p><b>SAMA Op-Res 2024 — 5 pillars:</b>
 * <ol>
 *   <li><b>Governance &amp; Strategy</b> ({@code GOVERNANCE}) — board oversight,
 *       Op-Res strategy, accountable executive, three-lines model.</li>
 *   <li><b>Important Business Services</b> ({@code IBS_MAPPING}) — identification
 *       of services that, if disrupted, would impose intolerable
 *       harm on customers / market integrity / safety / soundness.</li>
 *   <li><b>Impact Tolerances</b> ({@code TOLERANCES}) — per-IBS maximum
 *       tolerable disruption (in hours / days / data-loss MB) set
 *       and approved by the board.</li>
 *   <li><b>Mapping &amp; Scenario Testing</b> ({@code MAPPING_TESTING}) —
 *       end-to-end mapping of each IBS to people / processes /
 *       tech / facilities / third parties; severe-but-plausible
 *       scenario tests proving the bank can stay within tolerance.</li>
 *   <li><b>Lessons Learned &amp; Continuous Improvement</b> ({@code LESSONS}) —
 *       post-incident + post-exercise lessons feed remediation,
 *       capability uplift, and the next annual cycle.</li>
 * </ol>
 *
 * <p><b>Maturity ladder (1-5):</b>
 * <ol>
 *   <li>Initial — ad-hoc, undocumented, reactive</li>
 *   <li>Developing — documented but inconsistent execution</li>
 *   <li>Defined — standardised process across the organisation</li>
 *   <li>Managed — metrics-driven, board-reported quarterly</li>
 *   <li>Optimising — continuous improvement, industry-leading</li>
 * </ol>
 *
 * <p>An assessment ({@code OpResAssessment}) is the unit of work — one
 * per fiscal year, with status {@code DRAFT → IN_PROGRESS → APPROVED →
 * CLOSED}. The 25 cells (5 pillars × current + target maturity) sit
 * inside {@link com.tss.grc.module.opres.entity.OpResAssessmentCell}.
 *
 * <p><b>Cross-module wiring</b> — F7 audit row on every mutation, F8
 * event on assessment lifecycle transitions so the rules engine can
 * fan out (notify CRO + Audit Committee Chair on submission, auto-
 * create a CAPA when a pillar's target &lt; current, etc.).
 *
 * @since Sprint 14 PR #8
 */
package com.tss.grc.module.opres;
