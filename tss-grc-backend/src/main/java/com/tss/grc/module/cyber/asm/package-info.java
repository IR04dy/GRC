/**
 * Attack Surface Management (ASM) — Sprint 11 deliverable.
 *
 * <p>Replaces the "stale spreadsheets + reactive Tenable scans" mode
 * for tracking a Saudi bank's external attack surface with a single
 * live register: discovered assets, attached findings, and a
 * shadow-IT pipeline that feeds TPRM after sanction.
 *
 * <p>Regulatory driver:
 * <ul>
 *   <li><b>NCA ECC-2 control 2-3 (Asset Management)</b> — requires
 *       comprehensive inventory of all assets, including external-facing.</li>
 *   <li><b>SAMA Cyber Security Framework 4.2.1</b> — the bank shall
 *       identify and inventory all internet-facing assets.</li>
 *   <li><b>NCA OTCC</b> — operational-technology assets discovered
 *       outside the formal inventory must be flagged and brought
 *       under governance within 30 days. The shadow-IT pipeline is
 *       the SaaS analogue.</li>
 *   <li><b>CISA Cyber Hygiene Services</b> (referenced by SAMA SCRO)
 *       — continuous monitoring of internet-facing assets requires a
 *       register to attest against.</li>
 * </ul>
 *
 * <p>Three entities:
 * <ul>
 *   <li>{@link com.tss.grc.module.cyber.asm.entity.AttackSurfaceAsset}
 *       — long-lived inventory rows with a lifecycle state machine
 *       (DISCOVERED → IN_REVIEW → APPROVED | SHADOW_IT_PENDING →
 *       RETIRED) and a computed risk score.</li>
 *   <li>{@link com.tss.grc.module.cyber.asm.entity.AttackSurfaceFinding}
 *       — issues attached to an asset; opens / closes /
 *       investigates / accepts. Cross-links to CAPA.</li>
 *   <li>{@link com.tss.grc.module.cyber.asm.entity.AttackSurfaceShadowIt}
 *       — pre-asset queue for unsanctioned SaaS / cloud services
 *       discovered via email signups, expense reports, network
 *       traffic, or user declaration. Promotion to SANCTIONED
 *       requires a TPRM vendor link; BLOCKED is terminal; PDPL
 *       flag escalates to the DPO via the F8 event bus.</li>
 * </ul>
 *
 * <p>Risk-score formula (canonical):
 * <pre>
 *   risk_score = severityWeight × exposureMultiplier × criticalityMultiplier
 *     severityWeight  ∈ {CRITICAL=100, HIGH=70, MEDIUM=40, LOW=15, INFO=5}
 *     exposureMul     ∈ {PUBLIC=1.0, PARTNER=0.7, INTERNAL=0.4, RESTRICTED=0.2}
 *     criticalityMul  ∈ {CRITICAL=1.0, HIGH=0.85, MEDIUM=0.6, LOW=0.3}
 * </pre>
 * Severity is the highest open finding (OPEN or INVESTIGATING)
 * attached to the asset; if none open, it defaults to LOW.
 *
 * <p>F7+F8 contract (the platform's automation bridge):
 * <ul>
 *   <li>F8 events emitted: {@code ASM_ASSET_CREATED},
 *       {@code ASM_ASSET_LIFECYCLE_TRANSITION},
 *       {@code ASM_ASSET_RISK_RECOMPUTED},
 *       {@code ASM_FINDING_RECORDED}, {@code ASM_FINDING_CLOSED},
 *       {@code SHADOW_IT_DISCOVERED}, {@code SHADOW_IT_SANCTIONED},
 *       {@code SHADOW_IT_BLOCKED}, {@code SHADOW_IT_PDPL_FLAG}</li>
 *   <li>F7 audit_log entries for every CREATE / UPDATE / TRANSITION /
 *       RISK_RECOMPUTED / FINDING_RECORDED / FINDING_TRANSITION /
 *       SHADOW_IT_DISCOVERED / SHADOW_IT_TRANSITION /
 *       SHADOW_IT_PDPL_ESCALATED.</li>
 * </ul>
 */
package com.tss.grc.module.cyber.asm;
