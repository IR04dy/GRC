/**
 * Business Continuity Management (BCM) module.
 *
 * <p>Implements ISO 22301-aligned BCM lifecycle: BIA → strategy → plan development
 * → exercising → maintenance, with RTO/RPO tracking per critical process.</p>
 *
 * <h3>Key entities</h3>
 * <ul>
 *   <li>{@code CriticalProcess} — business process inventory with dependencies</li>
 *   <li>{@code BiaRecord} — Business Impact Analysis per process</li>
 *   <li>{@code BcmPlan} — Business Continuity / Disaster Recovery plans</li>
 *   <li>{@code BcmExercise} — tabletop / functional / full-scale exercise records</li>
 *   <li>{@code RecoveryStrategy} — approved recovery strategies per process</li>
 * </ul>
 *
 * <h3>Cross-module integration</h3>
 * <ul>
 *   <li>Risk module ← critical BIA findings escalated as risks</li>
 *   <li>Cyber module ← cyber incidents trigger BCM activation workflows</li>
 *   <li>Compliance module ← BCM exercise results feed ISO 22301 compliance evidence</li>
 * </ul>
 */
package com.tss.grc.module.bcm;
