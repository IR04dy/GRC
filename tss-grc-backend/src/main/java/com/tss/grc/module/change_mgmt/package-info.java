/**
 * Change Management module — ITIL v4 Change Enablement.
 *
 * Owns the full lifecycle of a change request: DRAFT → SUBMITTED →
 * IMPACT_ASSESSED → CAB_PENDING → CAB_APPROVED/REJECTED → SCHEDULED →
 * IMPLEMENTING → IMPLEMENTED → POST_REVIEW → CLOSED, with an EMERGENCY
 * fast-path that bypasses CAB.
 *
 * Generic cross-module integration is provided by
 * {@code change_mgmt.change_links} — any other module can attach a link
 * pointing at one of its entities (RISK, CONTROL, INCIDENT, VENDOR,
 * BCM_PLAN, AUDIT_FINDING, POLICY, REGULATORY_CHANGE, DPIA, KRI, ASSET,
 * EVIDENCE) with a typed relationship (AFFECTS, TRIGGERED_BY, MITIGATES,
 * TARGETS, REMEDIATES, DEPENDS_ON, INFORMS).
 */
package com.tss.grc.module.change_mgmt;
