/**
 * Regulatory Change tracker — Sprint 4 §3.2 #3.
 *
 * Tracks Saudi-regulator updates from initial publication through impact
 * assessment to implementation. Backed by V24 ({@code regchange.changes}).
 *
 * State machine:
 *   NEW → ASSESSED → IMPACT_ANALYSED → IN_IMPLEMENTATION → IMPLEMENTED → CLOSED,
 *   with DEPRIORITISED reachable from any non-terminal state.
 */
package com.tss.grc.module.regchange;
