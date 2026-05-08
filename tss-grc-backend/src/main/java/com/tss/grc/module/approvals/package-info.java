/**
 * Approvals module — Sprint 4 §3.2 #1.
 *
 * Universal approval-request facility. Any module that needs human sign-off
 * can create an entry here and route by priority + SLA. Backed by V22
 * ({@code approvals.requests} table).
 *
 * Primary state machine: PENDING → APPROVED | REJECTED | DELEGATED | EXPIRED.
 * DELEGATED loops back to PENDING with a new assignee.
 *
 * REST: {@code /api/approvals/requests}.
 */
package com.tss.grc.module.approvals;
