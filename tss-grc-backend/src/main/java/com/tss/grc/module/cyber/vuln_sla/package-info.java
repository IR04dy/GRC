/**
 * Risk-Based Vulnerability SLA engine — Sprint 11 deliverable.
 *
 * <p>Replaces flat-severity SLA buckets (1h CRITICAL / 4h HIGH / 24h
 * MEDIUM / 72h LOW per NCA ECC-2 baseline) with an accelerated clock
 * driven by three signals: CVSS severity × asset exposure × business
 * criticality. The same CRITICAL CVE on a public-facing online-banking
 * portal will get a sub-hour clock; on an internal HR system it stays
 * on the regulator-default 1-hour clock without breaching anything.
 *
 * <p>Regulatory drivers:
 * <ul>
 *   <li><b>NCA ECC-2 control 2-12 (Vulnerability Management)</b> — the
 *       vulnerability-management programme must be "aligned with the
 *       organisation's business and risk context". A flat queue does
 *       not clear that bar; this engine does.</li>
 *   <li><b>SAMA Cyber Security Framework 3.3.8</b> — remediation
 *       timelines must be proportionate to business impact.</li>
 *   <li><b>CISA SSVC (Stakeholder-Specific Vulnerability
 *       Categorization)</b> — emerging industry standard. Exposure,
 *       asset criticality, and exploitation status drive prioritisation
 *       — not raw CVSS.</li>
 * </ul>
 *
 * <p>Compute formula (locked in
 * {@link com.tss.grc.module.cyber.vuln_sla.service.VulnerabilitySlaComputeService}):
 * <pre>
 *   base_hours          = policy.{severity}_sla_hours
 *   exposure_mul        = policy.exposure_{exposure}_mul
 *   criticality_mul     = policy.criticality_{criticality}_mul
 *   raw_hours           = base_hours × exposure_mul × criticality_mul
 *   floor_hours         = 0.25 × base_hours
 *   effective_sla_hours = max(raw_hours, floor_hours)
 *   due_at              = opened_at + effective_sla_hours
 * </pre>
 *
 * <p>The 0.25× floor is intentional: a CRITICAL CVE on a PUBLIC ×
 * CRITICAL asset would land at {@code 1 × 0.5 × 0.5 = 0.25h = 15 min},
 * and we want to keep the very-tight clocks from going sub-minute even
 * if a customer ratchets the multipliers down further. Operators
 * cannot triage a vulnerability faster than the floor; clamping it
 * keeps the SLA achievable.
 *
 * <p>Auto-escalation, run periodically by
 * {@link com.tss.grc.module.cyber.vuln_sla.service.VulnerabilitySlaScheduler}:
 * <ul>
 *   <li>WARNING  — emitted when elapsed/effective ≥ warning_pct (default 0.75)</li>
 *   <li>BREACHED — emitted when elapsed/effective ≥ critical_pct (default 1.00)</li>
 * </ul>
 *
 * <p>F7+F8 contract:
 * <ul>
 *   <li>F8 events: {@code VULN_SLA_COMPUTED},
 *       {@code VULN_SLA_RECOMPUTED}, {@code VULN_SLA_WARNING},
 *       {@code VULN_SLA_BREACHED}, {@code VULN_SLA_REMEDIATED}</li>
 *   <li>F7 audit_log entries for every CREATE / UPDATE / COMPUTE /
 *       RECOMPUTE / ESCALATION / REMEDIATED on both the policy and
 *       finding rows.</li>
 * </ul>
 */
package com.tss.grc.module.cyber.vuln_sla;
