/**
 * Cyber Lifecycle module — backend wiring.
 *
 * Backs {@code lib/cyberLifecycle.ts}. Schema: {@code cyber_lc.*}.
 *
 * Primary state-machine entity: {@link com.tss.grc.module.cyber_lc.entity.Vulnerability}
 * (NEW → TRIAGED → OWNER_ASSIGNED → IN_REMEDIATION → AWAITING_VERIFICATION → CLOSED,
 *  with parallel paths to RISK_ACCEPTED and FALSE_POSITIVE).
 *
 * Aligned with NCA ECC-1:2018 / ECC-2:2024, SAMA Cyber Security Framework,
 * and CITC for telecoms. SLA defaults from NCA ECC severity matrix:
 * 7 days (Critical), 30 (High), 60 (Medium), 90 (Low).
 *
 * Tables modeled in V19 migration:
 *   assets             — lightweight asset inventory (CMDB stub)
 *   threat_intel       — feed entries (NCA Hasen / vendor / SAMA-CERT)
 *   vulnerabilities    — primary state-machine entity
 *   cyber_incidents    — incident registry (detailed IR in module/incident)
 */
package com.tss.grc.module.cyber_lc;
