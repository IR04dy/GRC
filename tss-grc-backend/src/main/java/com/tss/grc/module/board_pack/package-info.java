/**
 * Board Pack module — Sprint 4 §3.2 #5.
 *
 * Periodic (typically quarterly) board reporting packets. Pulls snapshots
 * from risk / audit / incidents / pdpl / ccm modules at draft time and
 * publishes a versioned, signed-off PDF.
 *
 * State machine: DRAFT → IN_REVIEW → APPROVED → PUBLISHED, with CANCELLED.
 *
 * REST: {@code /api/board-pack/packs}.
 */
package com.tss.grc.module.board_pack;
