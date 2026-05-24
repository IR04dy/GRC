/**
 * Sprint 13 — Whistleblower / Ethics Hotline.
 *
 * <p>Saudi banks must operate a confidential whistleblower channel per
 * SAMA Corporate Governance Regulations Article 28 + CMA Listed-
 * Companies CG Code Article 87. NCA ECC-2 control 5-1-3 requires the
 * channel to be anonymous-capable for cybersecurity violations.
 *
 * <p>This package gives the bank an in-platform hotline owned by the
 * Ethics Committee, with three core surfaces:
 *
 * <ul>
 *   <li><strong>Public submission portal</strong> — token IS the
 *       credential. Anonymous-by-default. Reporter IP is NEVER logged
 *       in the audit ledger.</li>
 *   <li><strong>Public status page</strong> — reporter uses their
 *       opaque token to check status and receive replies from the
 *       Ethics Officer.</li>
 *   <li><strong>Internal triage workspace</strong> — Ethics Officer,
 *       Compliance Officer, Internal Auditor, Audit Committee member,
 *       Platform Admin see the full register.</li>
 * </ul>
 *
 * <p>Senior-executive involvement is a hard escalation flag — the
 * F8 trigger {@code WHISTLEBLOWER_SENIOR_EXEC_FLAGGED} fires at
 * submission and triage so the Ethics Committee Chair + Internal
 * Auditor are notified out-of-band, bypassing the normal manager-
 * of-the-accused chain.
 *
 * <p>Retaliation complaints are a sub-flow with a separate state
 * machine — a retaliation claim is itself a protected report and
 * is owned by an independent protector (typically HR Director or
 * external investigator) who must not be in the supervisory chain
 * of the original case's investigator.
 */
package com.tss.grc.module.ethics.whistleblower;
