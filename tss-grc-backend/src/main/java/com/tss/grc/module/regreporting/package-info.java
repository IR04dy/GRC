/**
 * Mandatory Regulatory Reporting Calendar — Sprint 16 PR #1.
 *
 * <p>Every Saudi-regulated firm runs a relentless cadence of statutory
 * returns. A mid-sized SAMA-licensed bank typically files 40-60 distinct
 * obligations across daily / weekly / monthly / quarterly / semi-annual /
 * annual cycles — covering capital adequacy, liquidity, AML/CFT, complaints,
 * fraud, cyber events, customer protection, and dozens of niche returns
 * (consumer-finance, FX positions, branch outages, key-personnel changes…).
 * Adding CMA listing returns, NCA cyber returns, ZATCA tax returns, and
 * SDAIA personal-data returns to that mix puts the average compliance team
 * north of 100 recurring obligations.
 *
 * <p>This module turns that into an operational calendar — every obligation
 * gets a catalog row, every cycle becomes a filing record with a 7-step
 * lifecycle, and the F8 engine fires reminder + escalation events as due
 * dates approach.
 *
 * <p><b>Obligation catalog</b> — defines a recurring return:
 * <ul>
 *   <li>regulator (SAMA, CMA, NCA, SDAIA, NCEMA, ZATCA, OTHER)</li>
 *   <li>code (e.g. "SAMA-DLR" for Daily Liquidity Return)</li>
 *   <li>frequency (DAILY | WEEKLY | MONTHLY | QUARTERLY | SEMIANNUAL | ANNUAL | AD_HOC)</li>
 *   <li>dueOffsetBusinessDays — days after period end (e.g. 15 for monthly capital)</li>
 *   <li>owner role + sponsor role (Compliance, CFO, Treasury, CISO, DPO…)</li>
 *   <li>statutory basis (article / circular reference)</li>
 * </ul>
 *
 * <p><b>Filing lifecycle</b> — one cycle of the obligation:
 * <pre>
 *   SCHEDULED         — auto-created from obligation generator
 *   IN_PREPARATION    — data extraction + draft assembly
 *   IN_REVIEW         — first/second-line review
 *   APPROVED          — sign-off by accountable executive
 *   SUBMITTED         — file lodged with regulator, ack reference captured
 *   ACKNOWLEDGED      — regulator acknowledgment received
 *   CLOSED            — cycle complete, archived
 *   REJECTED          — regulator pushed back; corrections needed (→ IN_PREPARATION)
 *   EXEMPTED          — formal waiver granted (with reason + reference)
 *
 *   LATE — derived flag when (today &gt; dueDate AND status not in SUBMITTED/ACKNOWLEDGED/CLOSED/EXEMPTED)
 * </pre>
 *
 * <p><b>Saudi-native bilingual</b>: every regulator-facing field carries
 * EN + AR. Operator-natural Arabic:
 * <ul>
 *   <li><i>التقرير المُلزم</i> — mandatory return (not literal: التقرير الإلزامي)</li>
 *   <li><i>تقويم البلاغات التنظيمية</i> — regulatory filings calendar</li>
 *   <li><i>دورة التقديم</i> — filing cycle</li>
 *   <li><i>رقم إقرار الاستلام</i> — acknowledgment reference</li>
 *   <li><i>متأخر التقديم</i> — late filing</li>
 *   <li><i>إعفاء رسمي</i> — formal exemption</li>
 * </ul>
 *
 * <p>Emits F8 trigger codes:
 * <ul>
 *   <li>{@code REG_REPORT_OBLIGATION_CREATED}</li>
 *   <li>{@code REG_REPORT_FILING_SCHEDULED} — auto-generation for next cycle</li>
 *   <li>{@code REG_REPORT_FILING_STATE_CHANGED}</li>
 *   <li>{@code REG_REPORT_FILING_SUBMITTED}</li>
 *   <li>{@code REG_REPORT_FILING_REJECTED}</li>
 *   <li>{@code REG_REPORT_FILING_CLOSED}</li>
 * </ul>
 *
 * <p><b>F2-467 — two tokens were removed from that list because nothing emits
 * them.</b> It previously named {@code REG_REPORT_FILING_DUE_SOON} "fired by F8
 * scheduler at T-7, T-3, T-1" and {@code REG_REPORT_FILING_OVERDUE} "fired by F8
 * scheduler the day after dueDate". 🔑 Both sentences name the mechanism that
 * fires them, and <b>this module declares no {@code @Scheduled} method at all</b> —
 * which is what made the claim credible rather than checkable.
 *
 * <p>The overdue condition is computed on demand instead, by
 * {@code RegReportingService#countOverdue(today)} for the dashboard. So a SAMA/CMA
 * filing that has passed its due date is <i>visible</i> to whoever opens the screen and
 * <i>escalates to nobody</i>. Writing the sweep is a feature and is filed.
 */
package com.tss.grc.module.regreporting;
