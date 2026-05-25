/**
 * Counterparty Credit Rating Registry — Sprint 18 PR #1.
 *
 * <p>Every Saudi bank runs a counterparty register that's the spine of
 * credit-risk management. SAMA's Credit Risk Management Standards
 * require: (a) a unified inventory of every meaningful counterparty
 * (corporate + sovereign + financial-institution + retail-aggregate),
 * (b) an internal rating per counterparty with point-in-time PD/LGD/EAD,
 * (c) external rating capture where available (S&amp;P / Moody's / Fitch
 * + Saudi-local agencies like CARE Ratings Saudi), (d) a watchlist
 * + rating-action history feeding the ECL/IFRS 9 staging process, and
 * (e) large-exposure limits per counterparty + per group with live
 * utilisation tracking against SAMA's 25%-of-Tier-1 single-name cap.
 *
 * <p>Until now this lives in spreadsheets or a vendor system loosely
 * integrated with the core. This module brings it into the platform
 * with operational state machines and F8 event emission so credit risk
 * is wired into the same notification + escalation fabric as the rest
 * of GRC.
 *
 * <p><b>Entities</b>:
 * <ul>
 *   <li>{@code Counterparty} — register row (legal name AR/EN, LEI,
 *       sector, parent group, country, sovereign flag, FI flag,
 *       relationship-manager role, sponsor)</li>
 *   <li>{@code CreditRating} — point-in-time rating record (internal
 *       grade + PD + LGD + EAD; or external agency + scale)</li>
 *   <li>{@code RatingAction} — watchlist / downgrade / upgrade /
 *       default events forming the rating-history timeline</li>
 *   <li>{@code LargeExposureLimit} — counterparty-level or group-level
 *       limit with current utilisation tracked daily</li>
 *   <li>{@code Event} — chronological timeline for the counterparty
 *       record (rating actions, limit changes, relationship-manager
 *       handoffs, KYC refresh)</li>
 * </ul>
 *
 * <p><b>Rating-action lifecycle</b>:
 * <pre>
 *   STABLE → WATCHLIST_NEGATIVE → DOWNGRADE → DEFAULT
 *                              ↘ STABLE         (recovery)
 *   STABLE → WATCHLIST_POSITIVE → UPGRADE
 *                              ↘ STABLE
 * </pre>
 *
 * <p><b>Counterparty status</b>:
 * <pre>
 *   ACTIVE | WATCHLIST | DEFAULT | RESOLVED | DORMANT
 * </pre>
 *
 * <p><b>Limit utilisation bands</b> (RAG-style on dashboard):
 * <pre>
 *   GREEN  — ≤ 70% utilised
 *   AMBER  — 70-90% utilised  → notify Credit Risk + RM
 *   RED    — &gt; 90%             → escalate CRO + freeze new credit
 *   BREACH — &gt; 100%            → SAMA-reportable large-exposure event
 * </pre>
 *
 * <p><b>Saudi-native bilingual</b>:
 * <ul>
 *   <li><i>سجل الأطراف المقابلة</i> — Counterparty Register</li>
 *   <li><i>التصنيف الائتماني</i> — Credit Rating</li>
 *   <li><i>قائمة المراقبة</i> — Watchlist</li>
 *   <li><i>خفض التصنيف</i> — Rating downgrade</li>
 *   <li><i>التعثّر</i> — Default</li>
 *   <li><i>حدّ التعرّض الكبير</i> — Large-exposure limit</li>
 *   <li><i>تجاوز الحدّ</i> — Limit breach</li>
 *   <li><i>احتمال التعثّر / PD</i> — Probability of Default</li>
 *   <li><i>الخسارة عند التعثّر / LGD</i> — Loss Given Default</li>
 *   <li><i>التعرّض عند التعثّر / EAD</i> — Exposure at Default</li>
 * </ul>
 *
 * <p>Emits F7 audit + F8 trigger codes:
 * <ul>
 *   <li>{@code COUNTERPARTY_REGISTERED}</li>
 *   <li>{@code COUNTERPARTY_RATING_DOWNGRADED} — live</li>
 *   <li>{@code COUNTERPARTY_RATING_UPGRADED}</li>
 *   <li>{@code COUNTERPARTY_DEFAULTED}</li>
 *   <li>{@code COUNTERPARTY_WATCHLIST_NEGATIVE}</li>
 *   <li>{@code COUNTERPARTY_WATCHLIST_REMOVED}</li>
 *   <li>{@code LARGE_EXPOSURE_LIMIT_AMBER}</li>
 *   <li>{@code LARGE_EXPOSURE_LIMIT_RED}</li>
 *   <li>{@code LARGE_EXPOSURE_LIMIT_BREACH}</li>
 * </ul>
 */
package com.tss.grc.module.cpty;
