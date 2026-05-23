/**
 * Three Lines of Defense (3LoD) — Sprint 11 GRC strategic view.
 *
 * <p>Maps every artefact in the platform (risk / control / finding / CAPA /
 * policy / DSR) to the band of the IIA 2020 Three Lines Model that is
 * accountable for it, and surfaces a role-aligned dashboard.
 *
 * <h2>Bands</h2>
 * <ul>
 *   <li><b>LINE_1 — Operations / risk owners.</b> Saudi-native phrasing:
 *       <i>أصحاب المخاطر التشغيلية</i>. Business unit heads, branch
 *       managers, IT operations, application owners. Own day-to-day
 *       controls, identify and report incidents, execute CAPA.</li>
 *   <li><b>LINE_2 — Risk / Compliance / Privacy / Cyber-GRC oversight.</b>
 *       <i>وظائف الإشراف</i>. Risk management function, Compliance
 *       Officer, DPO (PDPL Art.32), CISO governance.</li>
 *   <li><b>LINE_3 — Internal Audit.</b> <i>المراجعة الداخلية</i>.
 *       Independent assurance reporting to the Audit Committee.</li>
 *   <li><b>BOARD — Audit Committee / Board.</b> Oversight band (not a
 *       "line" per IIA but tracked for the dashboard's escalation column).</li>
 * </ul>
 *
 * <h2>Saudi regulatory anchors</h2>
 * <ul>
 *   <li><b>SAMA Cyber-Security Framework 1.1.1</b> — Governance: clear
 *       allocation of risk and compliance responsibilities. This module is
 *       the auditable evidence the SAMA examiner can trace.</li>
 *   <li><b>NCA ECC-2 1-1</b> — Cybersecurity Governance.</li>
 *   <li><b>IIA Three Lines Model (2020 update)</b>.</li>
 *   <li><b>COSO ERM 2017</b> — Governance &amp; Culture component.</li>
 * </ul>
 *
 * <h2>Resolution rule</h2>
 * <p>When a user holds multiple roles, the service picks the <b>highest
 * band</b> for assurance independence:
 * {@code LINE_3 > LINE_2 > LINE_1}. {@code BOARD} is tracked as a separate
 * oversight band — an Audit Committee Chair who is also a Risk Owner
 * appears in both bands on the dashboard so the conflict is visible.
 */
package com.tss.grc.module.governance.threelines;
