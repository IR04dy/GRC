/**
 * Recovery & Resolution Plan (RRP) — Sprint 17 PR #1.
 *
 * <p>Every SAMA D-SIB (Domestic Systemically Important Bank) — and now
 * every Tier-2 / Tier-3 bank under the SAMA Recovery Planning Rules
 * (2022) — must lodge an annual Recovery & Resolution Plan with SAMA.
 * The Plan answers two distinct questions:
 *
 * <ul>
 *   <li><b>Recovery Plan</b> — what the bank itself does to restore
 *       viability under severe but plausible stress: capital actions
 *       (rights issue, AT1/T2 issuance, dividend suspension), asset
 *       sales (loan-portfolio disposal, subsidiary divestiture, RE
 *       holdings), liability management (deposit campaigns, repo
 *       facilities, central-bank backstops), business-line cuts
 *       (branch closures, headcount reduction, product withdrawal).
 *   <li><b>Resolution Plan</b> — what SAMA does if the bank fails:
 *       bridge bank, sale-of-business, asset separation, statutory
 *       bail-in waterfall ordering.
 * </ul>
 *
 * <p>The plan rests on four operational catalogs that have to be kept
 * current year-round, not just at submission time:
 *
 * <ol>
 *   <li><b>Critical Functions</b> — the bank's economic functions
 *       that MUST keep running through resolution (payments, deposit
 *       access, SADAD / IPS settlement, trade-finance L/C continuity).
 *   <li><b>Recovery Indicators</b> — the live early-warning metric
 *       set with RED / AMBER / GREEN bands. CAR &lt; 11%, LCR &lt; 110%,
 *       NSFR &lt; 105%, NPL &gt; 5%, profitability turning negative,
 *       cost-of-funding spike, deposit-runoff trigger.
 *   <li><b>Recovery Options</b> — the menu of management actions
 *       with capacity (SAR), time-to-execute (days), and impact
 *       on each indicator. SAMA wants the menu pre-rehearsed so
 *       there's no scramble under stress.
 *   <li><b>Stress Scenarios</b> — three minimum: idiosyncratic
 *       (bank-specific loss), market-wide (regional GCC shock), and
 *       combined. Each scenario rehearses which options fire in
 *       what order.
 * </ol>
 *
 * <p><b>Cycle lifecycle</b>:
 * <pre>
 *   DRAFT              — cycle opened, basic metadata only
 *   IN_PREPARATION     — catalogs filled, scenarios rehearsed
 *   BOARD_REVIEW       — full plan through Board Risk Committee
 *   APPROVED           — Board sign-off captured
 *   SUBMITTED          — lodged with SAMA, ack reference recorded
 *   ACKNOWLEDGED       — SAMA acknowledgment received
 *   CLOSED             — cycle archived
 * </pre>
 *
 * <p>An additional non-cycle trigger fires live throughout the year:
 * {@code RRP_INDICATOR_THRESHOLD_BREACHED} — when a recovery indicator
 * crosses its AMBER or RED band. This is the operational tie-back to
 * KRI breach + Op-Res tolerance breach.
 *
 * <p><b>Saudi-native bilingual</b>:
 * <ul>
 *   <li><i>خطة الاستعادة والحلّ</i> — Recovery &amp; Resolution Plan
 *       (operator-natural; not literal الخطة الاستعادية والحلّية)</li>
 *   <li><i>الوظائف الحيوية</i> — Critical Functions</li>
 *   <li><i>مؤشّرات الاستعادة</i> — Recovery Indicators</li>
 *   <li><i>خيارات الاستعادة</i> — Recovery Options</li>
 *   <li><i>سيناريوهات الإجهاد</i> — Stress Scenarios</li>
 *   <li><i>طاقة الخيار</i> — Option capacity (SAR amount)</li>
 *   <li><i>زمن التنفيذ</i> — Time-to-execute</li>
 *   <li><i>تجاوز نطاق الإنذار</i> — Threshold breach</li>
 * </ul>
 *
 * <p>Emits F7 audit + F8 trigger codes:
 * <ul>
 *   <li>{@code RRP_CYCLE_OPENED}</li>
 *   <li>{@code RRP_CYCLE_TRANSITIONED}</li>
 *   <li>{@code RRP_CYCLE_SUBMITTED}</li>
 *   <li>{@code RRP_CYCLE_CLOSED}</li>
 *   <li>{@code RRP_INDICATOR_THRESHOLD_BREACHED} — live, year-round</li>
 *   <li>{@code RRP_OPTION_CAPACITY_CHANGED} — option re-assessed</li>
 *   <li>{@code RRP_CRITICAL_FUNCTION_ADDED}</li>
 * </ul>
 */
package com.tss.grc.module.rrp;
