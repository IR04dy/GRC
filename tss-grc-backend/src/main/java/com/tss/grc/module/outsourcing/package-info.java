/**
 * Outsourcing &amp; Cloud Governance module — Sprint 30.
 *
 * <p>SAMA Outsourcing Regulation surface for the Saudi bank, layered with
 * the SAMA Cloud Computing Regulatory Framework data-criticality engine.
 * Distinct from the lightweight {@code module.scm} vendor register —
 * this module is regulator-grade, with full materiality classification,
 * cloud-data-classification overlay, ongoing review cadence, and exit-
 * plan lifecycle.
 *
 * <h2>Regulatory anchors</h2>
 * <ul>
 *   <li><b>SAMA Outsourcing Regulation</b> §3 Pre-outsourcing risk
 *       assessment, §4 Material outsourcing notification (within 60
 *       days), §5 Non-objection request for outsourcing of core
 *       activities, §6 Ongoing oversight obligations, §7 Exit and
 *       contingency planning, §8 Cross-border outsourcing</li>
 *   <li><b>SAMA Cloud Computing Regulatory Framework v1.0</b> — Tier-1
 *       (Public) / Tier-2 (Internal) / Tier-3 (Confidential) / Tier-4
 *       (Restricted) data classification gates; in-Kingdom data
 *       residency rules for Tier-3 and Tier-4</li>
 *   <li><b>SAMA Cyber Security Framework</b> §3.3.14 Third-party security</li>
 *   <li><b>NCA ECC-2 (2023)</b> §2-1-2 Outsourcing security; §3-1-2
 *       Cloud security; §4-1-2 Data residency</li>
 *   <li><b>PDPL Article 7</b> cross-border data transfer for vendor
 *       processing arrangements</li>
 * </ul>
 *
 * <h2>State machines</h2>
 *
 * Arrangement (the master outsourcing contract):
 * <pre>
 *   PROPOSED → RISK_ASSESSED → BOARD_APPROVED → SAMA_NOTIFIED → LIVE
 *                 ↓                                              ↓
 *              REJECTED                            UNDER_REVIEW → EXITED
 *                                                                  ↑
 *                                                             (exit plan executed)
 * </pre>
 *
 * Cloud Service (a specific cloud subscription under an arrangement):
 * <pre>
 *   ONBOARDING → CLASSIFIED → APPROVED → IN_USE → DEPRECATED → DECOMMISSIONED
 *                      ↓
 *                  BLOCKED  (data classification fails residency gate)
 * </pre>
 *
 * Ongoing Review (periodic SAMA §6 oversight cycle):
 * <pre>
 *   SCHEDULED → IN_PROGRESS → COMPLETED
 *                    ↓               ↓
 *                ESCALATED      WITH_FINDINGS  (links to CAPA)
 * </pre>
 *
 * Exit Plan (SAMA §7 exit-and-contingency lifecycle):
 * <pre>
 *   DRAFT → APPROVED → REHEARSED → READY
 *               ↓                    ↓
 *           REJECTED              EXECUTED (terminal — arrangement transitions to EXITED)
 * </pre>
 *
 * <h2>Materiality gate (SAMA §4)</h2>
 *
 * <p>The {@code materiality} field is the regulator-recognised flag:
 * MATERIAL / NON_MATERIAL. Material arrangements must transition through
 * {@code SAMA_NOTIFIED} within 60 calendar days of {@code
 * BOARD_APPROVED}; core-activity outsourcing must hold a non-objection
 * letter before {@code LIVE} promotion (see AR-263).
 *
 * <h2>Cloud data-classification gate (SAMA Cloud §5)</h2>
 *
 * <p>For each {@code CloudService}, the {@code dataClassification} (T1
 * Public / T2 Internal / T3 Confidential / T4 Restricted) combined
 * with {@code dataResidency} (IN_KINGDOM / GCC / GLOBAL) forms the
 * approval gate: T3 + T4 must be IN_KINGDOM; T2 may be GCC; T1 may
 * be GLOBAL. The service layer rejects {@code APPROVED} promotion if
 * residency does not satisfy classification (see AR-265).
 *
 * <h2>F7 audit + F8 events</h2>
 *
 * <p>Every state transition emits both an {@code AuditLogService.log()}
 * call and a {@code DomainEvent} via {@code ApplicationEventPublisher}.
 * F8 triggers AR-261..AR-270 react to:
 * {@code OUTSOURCING_ARRANGEMENT_REGISTERED},
 * {@code OUTSOURCING_RISK_ASSESSED},
 * {@code OUTSOURCING_BOARD_APPROVED},
 * {@code OUTSOURCING_SAMA_NOTIFIED},
 * {@code OUTSOURCING_LIVE},
 * {@code OUTSOURCING_BREACH_REPORTED},
 * {@code OUTSOURCING_EXIT_EXECUTED},
 * {@code CLOUD_SERVICE_REGISTERED},
 * {@code CLOUD_SERVICE_RESIDENCY_VIOLATION},
 * {@code OUTSOURCING_REVIEW_OVERDUE}.
 */
package com.tss.grc.module.outsourcing;
