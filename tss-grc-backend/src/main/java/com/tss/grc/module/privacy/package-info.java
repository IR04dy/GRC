/**
 * Privacy &amp; Personal Data Protection module.
 *
 * <p>Supports Saudi PDPL and ISO 27701 compliance: personal data inventory,
 * Record of Processing Activities (RoPA), Privacy Impact Assessments (PIA/DPIA),
 * data subject rights management, and breach notification.</p>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code DataCategory} — classification of personal data types</li>
 *   <li>{@code ProcessingActivity} — RoPA records per Article 12 PDPL</li>
 *   <li>{@code PrivacyAssessment} — PIA/DPIA assessment records</li>
 *   <li>{@code DataBreach} — breach notification lifecycle with PDPC reporting</li>
 *   <li>{@code DataSubjectRequest} — DSR fulfillment tracking (access, deletion, correction)</li>
 *   <li>{@code ConsentRecord} — consent collection and withdrawal audit</li>
 * </ul>
 *
 * <h3>Cross-module integration</h3>
 * <ul>
 *   <li>Risk module ← breaches and PIA findings escalated as risks</li>
 *   <li>Compliance module ← PDPL / ISO 27701 control evidence</li>
 *   <li>Workflow module ← DSR fulfilment and breach notification workflows</li>
 * </ul>
 */
package com.tss.grc.module.privacy;
