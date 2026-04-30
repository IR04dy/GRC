/**
 * Cybersecurity module.
 *
 * <p>Covers asset inventory, vulnerability tracking, security incident management,
 * and cyber threat intelligence aligned with NCA ECC 2.0 controls.</p>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code CyberAsset} — IT/OT asset inventory with CIA classification</li>
 *   <li>{@code Vulnerability} — CVE-linked vulnerability records per asset</li>
 *   <li>{@code CyberIncident} — incident lifecycle (Detection → Containment → Recovery → Closure)</li>
 *   <li>{@code CyberAssessment} — periodic security assessments against control frameworks</li>
 *   <li>{@code ThreatIntelligence} — threat actor / IOC repository</li>
 * </ul>
 *
 * <h3>Cross-module integration</h3>
 * <ul>
 *   <li>Risk module ← incident severity triggers risk creation</li>
 *   <li>Compliance module ← assessment findings feed compliance gap register</li>
 *   <li>Workflow module ← incident response workflow orchestration</li>
 * </ul>
 */
package com.tss.grc.module.cyber;
