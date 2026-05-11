/**
 * Risk scenario analysis — persists Monte-Carlo, stress-test, and what-if
 * scenario runs (Saudi GRC prompt §7.3, §3.1).
 *
 * <p>The {@code RiskLifecyclePage} already runs Monte-Carlo simulations in
 * the browser, but the result was transient. This module persists each
 * run so analysts can compare scenarios over time, attach them to risk-
 * acceptance decisions, and feed them into the board pack.
 *
 * <p>Lifecycle: {@code DRAFT → APPROVED | REJECTED | ARCHIVED}.
 */
package com.tss.grc.module.risk.scenario;
