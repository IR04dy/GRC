/**
 * CAPA module — Sprint 4 §3.2 #2.
 *
 * Universal Corrective and Preventive Actions tracker. Linked to originating
 * issue/finding/incident via (origin_type, origin_id). Backed by V23
 * ({@code capa.tasks}).
 *
 * State machine: OPEN → IN_PROGRESS → AWAITING_VERIFICATION → CLOSED,
 * with CANCELLED reachable from any active state.
 */
package com.tss.grc.module.capa;
