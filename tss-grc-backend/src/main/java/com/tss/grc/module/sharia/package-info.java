/**
 * Sharia Governance &amp; Audit Workflow — Sprint 20 PR #1.
 *
 * <p>Every Saudi licensed bank (whether full Islamic — Al Rajhi, Alinma,
 * Bilad — or a window operation — SNB / Riyad / ANB / SAB) runs a
 * <b>Sharia Supervisory Board (SSB)</b> + an <b>Internal Sharia Audit
 * function</b> as required by:
 *
 * <ul>
 *   <li><b>SAMA's Implementing Regulation on Sharia Governance for Banks
 *       Operating in Saudi Arabia</b> (Aug 2020) — §3 SSB composition;
 *       §5 Internal Sharia Audit; §6 Sharia Compliance Department; §7
 *       Annual Sharia Compliance Report.</li>
 *   <li><b>AAOIFI Governance Standards GSIFI 1-5</b> — Sharia
 *       Supervisory Board appointment, composition, report, audit, and
 *       compliance review.</li>
 *   <li><b>IFSB-10 Guiding Principles on Sharia Governance Systems</b>
 *       — 5 fitness-for-purpose principles for the SSB.</li>
 * </ul>
 *
 * <p>This module brings the operational Sharia surface into the
 * platform — SSB scholar register, SSB resolutions / fatwas with
 * product or transaction linkage, Internal Sharia Audit findings with
 * remediation tracking, Sharia non-compliance events with the
 * mandatory <b>income-purification</b> ledger (Sharia non-compliance
 * income, SNCI, must be donated to charity and reported separately
 * in financial statements), and a product Sharia approval pipeline
 * that gates every new Islamic-banking product launch.
 *
 * <p><b>Entities</b>:
 * <ul>
 *   <li>{@code ShariaScholar} — SSB member register: name, role
 *       (Chairman, Member, Secretariat), AAOIFI/IFSB credentials,
 *       term start/end, conflict-of-interest declarations, fee
 *       structure</li>
 *   <li>{@code ShariaResolution} — formal SSB opinion ("fatwa") on
 *       a product, transaction, or operational matter; reference
 *       (RES-2026-014), title EN+AR, body EN+AR, status (DRAFT →
 *       SSB_REVIEW → APPROVED / REJECTED → ACTIVE → SUPERSEDED),
 *       approval date, signing scholars list, supersedes link</li>
 *   <li>{@code ShariaAuditFinding} — finding from an Internal Sharia
 *       Audit cycle: severity (HIGH / MEDIUM / LOW), root cause,
 *       remediation plan, status (OPEN → REMEDIATION → VERIFIED →
 *       CLOSED), linked resolution if breach</li>
 *   <li>{@code ShariaNonComplianceEvent} — when a transaction or
 *       income stream is found to be Sharia non-compliant: the SNCI
 *       amount must be calculated, segregated, and purified
 *       (donated to charity). Status (DETECTED → INVESTIGATION →
 *       INCOME_PURIFIED → CLOSED), purification destination,
 *       financial impact</li>
 *   <li>{@code ShariaProductApproval} — new product (Murabaha,
 *       Ijara, Tawarruq, Sukuk, etc.) going through the SSB pipeline;
 *       state (SUBMITTED → SSB_REVIEW → APPROVED_PROVISIONAL → APPROVED_FINAL
 *       / REJECTED → LAUNCHED → DECOMMISSIONED), linked resolution</li>
 *   <li>{@code ShariaEvent} — chronological timeline per resolution,
 *       audit finding, non-compliance event, or product approval</li>
 * </ul>
 *
 * <p><b>SSB Resolution lifecycle</b>:
 * <pre>
 *   DRAFT → SSB_REVIEW → APPROVED → ACTIVE → SUPERSEDED
 *                     → REJECTED (terminal)
 * </pre>
 *
 * <p><b>Audit Finding lifecycle</b>:
 * <pre>
 *   OPEN → REMEDIATION → VERIFIED → CLOSED
 * </pre>
 *
 * <p><b>Non-Compliance Event lifecycle</b>:
 * <pre>
 *   DETECTED → INVESTIGATION → INCOME_PURIFIED → CLOSED
 * </pre>
 *
 * <p><b>Product Approval lifecycle</b>:
 * <pre>
 *   SUBMITTED → SSB_REVIEW → APPROVED_PROVISIONAL → APPROVED_FINAL → LAUNCHED → DECOMMISSIONED
 *                          → REJECTED (terminal)
 * </pre>
 *
 * <p><b>SAMA Sharia-governance hot-spots</b> this module covers:
 * <ul>
 *   <li>SSB independence: COI declarations refreshed annually</li>
 *   <li>SSB attendance: minimum 4 meetings/year per SAMA §3.6</li>
 *   <li>Internal Sharia Audit: every Islamic product line audited
 *       at least annually</li>
 *   <li>HIGH-severity audit findings: 30-day Board escalation SLA</li>
 *   <li>SNCI purification: must be settled within 90 days of detection
 *       per SAMA §6.4 + AAOIFI FAS 1</li>
 *   <li>Product launch: zero Islamic-banking product live without
 *       APPROVED_FINAL SSB resolution</li>
 *   <li>Annual Sharia Compliance Report due to Board + SAMA by
 *       end of Q1 each year</li>
 * </ul>
 *
 * <p><b>Saudi-native bilingual</b>:
 * <ul>
 *   <li><i>الحوكمة الشرعية</i> — Sharia governance</li>
 *   <li><i>الهيئة الشرعية</i> — Sharia Supervisory Board (SSB)</li>
 *   <li><i>قرار الهيئة الشرعية / فتوى</i> — SSB resolution / fatwa</li>
 *   <li><i>المراجعة الشرعية الداخلية</i> — Internal Sharia Audit</li>
 *   <li><i>المخالفة الشرعية</i> — Sharia non-compliance event</li>
 *   <li><i>الدخل غير الشرعي</i> — Sharia non-compliance income (SNCI)</li>
 *   <li><i>تطهير الدخل</i> — Income purification</li>
 *   <li><i>اعتماد منتج إسلامي</i> — Islamic product approval</li>
 *   <li><i>المرابحة / الإجارة / التورق / الصكوك / المضاربة / المشاركة</i>
 *       — Murabaha / Ijara / Tawarruq / Sukuk / Mudaraba / Musharaka</li>
 * </ul>
 *
 * <p>Emits F7 audit + F8 trigger codes:
 * <ul>
 *   <li>{@code SHARIA_RESOLUTION_SUBMITTED} / {@code SHARIA_RESOLUTION_APPROVED} /
 *       {@code SHARIA_RESOLUTION_REJECTED} / {@code SHARIA_RESOLUTION_SUPERSEDED}</li>
 *   <li>{@code SHARIA_AUDIT_FINDING_RAISED} (severity-tagged) /
 *       {@code SHARIA_AUDIT_FINDING_TRANSITIONED}</li>
 *   <li>{@code SHARIA_NC_EVENT_DETECTED} (with SNCI amount) /
 *       {@code SHARIA_NC_EVENT_TRANSITIONED} (with {@code from} / {@code to} in the payload) /
 *       {@code SHARIA_NC_EVENT_PURIFIED}</li>
 *   <li>{@code SHARIA_PRODUCT_SUBMITTED} / {@code SHARIA_PRODUCT_APPROVED} /
 *       {@code SHARIA_PRODUCT_LAUNCHED} / {@code SHARIA_PRODUCT_DECOMMISSIONED}</li>
 *   <li>{@code SHARIA_SCHOLAR_COI_DECLARED} / {@code SHARIA_SCHOLAR_REGISTERED} /
 *       {@code SHARIA_SCHOLAR_UPDATED}</li>
 * </ul>
 *
 * <p><b>F2-459 -- three tokens were removed from that list because nothing emits them.</b> It
 * previously named {@code SHARIA_SCHOLAR_TERM_EXPIRING}, {@code SHARIA_BOARD_MEETING_SHORTFALL}
 * and {@code SHARIA_NC_EVENT_CLOSED}. Measured against every string literal in
 * {@code src/main/java}, this module emits <b>21</b> {@code SHARIA_*} tokens and none of those
 * is one of them: the term-expiry ladder AR-260 triggers on the first and has fired 0 times;
 * the second is the SAMA cadence-breach escalation AR-260's own description promises and no
 * code path publishes; and the third does not exist because closure rides
 * {@code SHARIA_NC_EVENT_TRANSITIONED} with {@code to} in the payload, so a rule wanting "NC
 * event closed" has to match the transition and read the payload.
 *
 * <p>⚙ That third one was found by the gate, not by reading: the leg below re-derives the
 * emitted set from real code and refuted the count in this very paragraph on its first run
 * (two, then three). A mechanical check over a documented list is worth more than a careful
 * reading of it, which is the whole argument for the leg existing.
 *
 * <p>🔑 A list headed "Emits F7 audit + F8 trigger codes" is the artefact a developer reads
 * before wiring a rule, so a token documented here reads as available. It is also why the
 * absence was invisible to F2-436's census: that finding's shortlist required a trigger to be
 * "present in NO Java file", and a {@code package-info.java} is a Java file made entirely of
 * comments -- so every token a module DOCUMENTS as emitted counted as evidence that it IS
 * (F2-193's epitaph-as-corpse, in a detector rather than in a fix). Re-run with comments
 * stripped, 13 further triggers are present in Java only inside a comment and 7 of them
 * survive that finding's full criteria, so its readable shortlist is 15 rather than 8.
 */
package com.tss.grc.module.sharia;
