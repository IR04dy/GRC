/**
 * Compliance &amp; Audit module.
 *
 * <p>Manages compliance lifecycle against multiple regulatory frameworks
 * (NCA ECC, ISO 27001, ISO 22301, ISO 27701, Saudi PDPL) with gap analysis,
 * remediation tracking, and evidence management.</p>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code ComplianceAssessment} — assessment against a control framework (temporal table-backed)</li>
 *   <li>{@code ComplianceFinding} — individual control gaps / non-conformities</li>
 *   <li>{@code RemediationPlan} — action plan per finding with owner and due date</li>
 *   <li>{@code EvidenceItem} — evidence artefacts linked to controls via attachments</li>
 *   <li>{@code AuditEngagement} — internal / external audit management</li>
 * </ul>
 *
 * <h3>Cross-module integration</h3>
 * <ul>
 *   <li>Risk module ← findings with HIGH/CRITICAL impact escalated as risks</li>
 *   <li>Cyber module ← cyber assessment results feed compliance gap register</li>
 *   <li>BCM module ← exercise outcomes feed ISO 22301 compliance evidence</li>
 *   <li>Privacy module ← PIA results feed PDPL/ISO 27701 compliance evidence</li>
 *   <li>Workflow module ← remediation plans routed through approval workflow</li>
 * </ul>
 */
package com.tss.grc.module.compliance;
