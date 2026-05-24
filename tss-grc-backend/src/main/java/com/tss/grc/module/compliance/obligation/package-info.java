/**
 * Sprint 11 — Regulatory Obligation register + Periodic Attestation.
 *
 * <p>Every Saudi-regulated bank has hundreds of recurring regulatory
 * obligations (file STR within 24h, submit quarterly CAR returns to
 * SAMA by day 15 of M+1, publish PDPL RoPA annually, attest annual
 * code-of-conduct, quarterly SOX-style access certification, etc.).
 * Today these live in spreadsheets per business unit; this slice
 * ships a single register with owner assignment, due-date tracking,
 * attestation campaigns, and a regulator-grade audit trail.
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@link com.tss.grc.module.compliance.obligation.entity.Obligation}
 *       — long-lived obligation record (regulator, frequency, owner)</li>
 *   <li>{@link com.tss.grc.module.compliance.obligation.entity.ObligationAttestation}
 *       — per-cycle attestation evidence</li>
 *   <li>{@link com.tss.grc.module.compliance.obligation.entity.ObligationAttestationCampaign}
 *       — quarterly / annual cycle launcher</li>
 * </ul>
 *
 * <h3>Regulatory anchors</h3>
 * <ul>
 *   <li>SAMA Compliance Framework (Aug 2019) §3.2 — explicit register
 *       requirement with owner, frequency, severity-if-missed</li>
 *   <li>NCA ECC-2 control 1-1 (Governance) — "documented and assigned
 *       cybersecurity obligations"</li>
 *   <li>SAMA GRO inspections — periodic signed attestations from named
 *       officers</li>
 * </ul>
 *
 * <h3>F8 events emitted</h3>
 * <ul>
 *   <li>{@code OBLIGATION_CREATED}, {@code OBLIGATION_TRANSITIONED}</li>
 *   <li>{@code OBLIGATION_ATTESTED}, {@code OBLIGATION_OVERDUE},
 *       {@code OBLIGATION_ATTESTATION_REVIEWED}</li>
 *   <li>{@code OBLIGATION_CAMPAIGN_CREATED},
 *       {@code OBLIGATION_CAMPAIGN_OPENED},
 *       {@code OBLIGATION_CAMPAIGN_REMINDERS_SENT},
 *       {@code OBLIGATION_CAMPAIGN_ESCALATED},
 *       {@code OBLIGATION_CAMPAIGN_CLOSED},
 *       {@code OBLIGATION_CAMPAIGN_CANCELLED}</li>
 * </ul>
 */
package com.tss.grc.module.compliance.obligation;
