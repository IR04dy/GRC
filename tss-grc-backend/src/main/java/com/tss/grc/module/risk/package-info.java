/**
 * Enterprise Risk Management (ERM) module.
 *
 * <p>Owns the full risk lifecycle: identification → assessment → treatment → monitoring.
 * Serves as the <strong>unified risk hub</strong> for all other modules — cyber incidents,
 * BCM BIA findings, privacy breaches, and compliance gaps can all be escalated as risks
 * via {@code risk.risks.source_module}.</p>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code RiskCategory} — configurable taxonomy per organisation</li>
 *   <li>{@code Risk} — core risk record with inherent/residual 5×5 matrix scoring</li>
 *   <li>{@code RiskAssessment} — versioned assessment snapshots (temporal table-backed)</li>
 *   <li>{@code RiskTreatment} — treatment plans (Mitigate / Transfer / Accept / Avoid)</li>
 *   <li>{@code RiskIndicator} (KRI) — threshold-based automated alerting</li>
 *   <li>{@code RiskAppetiteStatement} — board-approved appetite per category</li>
 * </ul>
 *
 * <h3>Cross-module integration</h3>
 * <ul>
 *   <li>Cyber module → raises linked risk on incident classification</li>
 *   <li>BCM module → raises linked risk from BIA critical process findings</li>
 *   <li>Privacy module → raises linked risk from data breach impact assessment</li>
 *   <li>Compliance module → raises linked risk from audit findings</li>
 *   <li>Workflow module → all risk state changes routed through workflow engine</li>
 * </ul>
 */
package com.tss.grc.module.risk;
