/**
 * NCA ECC Annual Filing module — Sprint 4 §3.2 #6.
 *
 * Saudi-specific differentiator: tracks the annual NCA Essential Cybersecurity
 * Controls self-assessment workflow up to submission to the NCA "Haseen" portal.
 *
 * State machine:
 *   DRAFT → PEER_REVIEW → CISO_APPROVAL → SUBMITTED → ACKNOWLEDGED,
 *   with CANCELLED reachable from any non-terminal state.
 *
 * REST: {@code /api/nca-ecc/filings}.
 */
package com.tss.grc.module.nca_ecc;
