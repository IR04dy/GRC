/**
 * Fraud Risk Management — Sprint 14.
 *
 * <p>Saudi bank-grade fraud risk module closing the SAMA Anti-Fraud
 * Framework 2018 gap. Provides:
 *
 * <ul>
 *   <li><b>Typology catalog</b> — codified fraud typologies relevant to
 *       Saudi banks (Business Email Compromise, ATM skimming,
 *       SADAD/IPS push-payment fraud, account takeover, internal
 *       embezzlement, etc.) with Saudi-native bilingual labels.</li>
 *   <li><b>Case investigation</b> — fraud case lifecycle with state
 *       machine: {@code DETECTED → TRIAGE → INVESTIGATING →
 *       CONFIRMED|CLEARED|FALSE_POSITIVE → CLOSED}. Each transition
 *       captures the investigator, evidence references, and outcome
 *       narrative in both EN and AR.</li>
 *   <li><b>Investigation timeline</b> — {@code FraudCaseUpdate} rows
 *       capture every triage decision, finding, status change, and
 *       closure note for audit trail purposes.</li>
 * </ul>
 *
 * <p><b>Cross-module wiring</b> — every case mutation emits an F7
 * audit-log row (via {@code AuditLogService}) and an F8 automation
 * event (via {@code AutomationEventService}) so the platform rules
 * engine can fan out to NOTIFY / ESCALATE / FLAG / CREATE_RECORD
 * actions (e.g. auto-open an incident when a CONFIRMED fraud case
 * affects more than 100 customers).
 *
 * <p><b>Regulatory mapping</b> —
 * <ul>
 *   <li>SAMA Anti-Fraud Framework 2018 — Articles 4-9 (governance,
 *       detection, investigation, reporting, recovery)</li>
 *   <li>SAMA AML/CFT 2018 — Article 25 (suspicious transaction
 *       reporting)</li>
 *   <li>SAMA Cyber Security Framework — Subdomain 3.3.13 (Anti-Fraud)</li>
 *   <li>FATF Recommendation 20 — STR/SAR filing</li>
 * </ul>
 *
 * <p><b>Bilingual data</b> — typology labels, case subjects, and
 * narrative fields all carry {@code *En} / {@code *Ar} columns. The
 * frontend selects the active locale via {@code I18n.current}.
 *
 * @since Sprint 14
 */
package com.tss.grc.module.fraud;
