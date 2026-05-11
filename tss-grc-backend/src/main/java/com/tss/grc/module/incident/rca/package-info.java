/**
 * Root Cause Analysis sub-module (Saudi GRC prompt §3.8 + §7.6 #6).
 *
 * <p>Structured RCA bound to a parent record (incident, data breach,
 * BCM invocation, supplier incident). Replaces the bare
 * {@code lessonsLearned} text field with a methodology-aware capture
 * (5-Whys / fishbone / Pareto / free-form) plus participants,
 * recommendations, and linked CAPAs.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT     ──submit()──▶ IN_REVIEW
 *   IN_REVIEW ──approve()─▶ APPROVED  (terminal)
 *             ──reject()──▶ REJECTED  (terminal; reason mandatory)
 * </pre>
 *
 * <p>REST: {@code /api/incident/rca}.
 */
package com.tss.grc.module.incident.rca;
