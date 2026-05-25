/**
 * Open Banking Compliance — Sprint 19 PR #1.
 *
 * <p>SAMA's <b>Open Banking Framework</b> (Phase 1 AIS Live: Nov 2022;
 * Phase 2 PIS Live: 2024; Phase 3 VRP &amp; CBPII rolling out 2025-2026)
 * obliges every Saudi licensed bank to expose regulated APIs to third-
 * party providers (TPPs) under FAPI 2.0 security profile. This module
 * brings the operational side into the platform — TPP register with
 * KYB lifecycle, customer consent management with scope &amp; expiry,
 * API audit log, and OB-specific incident handling.
 *
 * <p><b>Entities</b>:
 * <ul>
 *   <li>{@code ObTpp} — Third-Party Provider register: AISP (account
 *       info), PISP (payment init), CBPII (card-based PI issuer)
 *       roles; FAPI client_id; eIDAS / SAMA certificate references;
 *       KYB lifecycle (PENDING_KYB → ACTIVE → SUSPENDED → REVOKED)</li>
 *   <li>{@code ObConsent} — customer consent record: scopes
 *       (accounts:read, balances:read, transactions:read,
 *       payments:initiate, vrp:setup), expiry window, granted/revoked
 *       timestamps, customer reference</li>
 *   <li>{@code ObApiCall} — append-only audit row per regulated API
 *       call: TPP, consent ref, endpoint, HTTP method, status code,
 *       latency, scopes used, customer (hashed), error reason</li>
 *   <li>{@code ObIncident} — OB-specific security or data incident
 *       (consent violation, scope creep, certificate misuse, fraud
 *       signal); separate from the platform's general incident
 *       module because OB has its own SAMA reporting cadence</li>
 *   <li>{@code ObEvent} — chronological timeline per TPP / consent /
 *       incident</li>
 * </ul>
 *
 * <p><b>TPP lifecycle</b>:
 * <pre>
 *   PENDING_KYB → ACTIVE → SUSPENDED → REVOKED
 *                  ↑          ↓
 *                  └─ reinstate
 * </pre>
 *
 * <p><b>Consent lifecycle</b>:
 * <pre>
 *   AUTHORISED → ACTIVE → EXPIRED
 *                       → REVOKED (by customer or by bank)
 * </pre>
 *
 * <p><b>SAMA Open Banking compliance hot-spots</b> this module covers:
 * <ul>
 *   <li>FAPI 2.0 certificate expiry tracking (90/30/7-day reminders)</li>
 *   <li>Consent expiry tracking (default 180 days per SAMA OBF v2)</li>
 *   <li>Bulk consent revocation as fraud signal (≥100 in 1h window)</li>
 *   <li>API abuse detection (rate-limit, scope creep, after-hours pattern)</li>
 *   <li>TPP-on-TPP fraud (one TPP using credentials issued to another)</li>
 *   <li>SAMA quarterly OB return: TPP count + consent count + complaint
 *       count + incident count, automatically rolled up from this module</li>
 * </ul>
 *
 * <p><b>Saudi-native bilingual</b>:
 * <ul>
 *   <li><i>المصرفية المفتوحة</i> — Open Banking</li>
 *   <li><i>مزوّد الخدمة المرخّص</i> — Licensed third-party provider</li>
 *   <li><i>موافقة العميل</i> — Customer consent</li>
 *   <li><i>نطاق الوصول</i> — Access scope</li>
 *   <li><i>سحب الموافقة</i> — Consent revocation</li>
 *   <li><i>سجل استدعاءات الواجهة</i> — API call audit log</li>
 *   <li><i>الحادثة الأمنية</i> — Security incident</li>
 * </ul>
 *
 * <p>Emits F7 audit + F8 trigger codes:
 * <ul>
 *   <li>{@code OB_TPP_ONBOARDED}</li>
 *   <li>{@code OB_TPP_SUSPENDED} / {@code OB_TPP_REVOKED} / {@code OB_TPP_REINSTATED}</li>
 *   <li>{@code OB_CONSENT_GRANTED} / {@code OB_CONSENT_REVOKED} / {@code OB_CONSENT_EXPIRED}</li>
 *   <li>{@code OB_CONSENT_BULK_REVOCATION} — ≥100 revokes in 1h, fraud signal</li>
 *   <li>{@code OB_API_ABUSE} — rate-limit / scope-creep / after-hours pattern</li>
 *   <li>{@code OB_INCIDENT_RAISED} / {@code OB_INCIDENT_RESOLVED}</li>
 *   <li>{@code OB_CERT_EXPIRING} — 90 / 30 / 7-day reminders</li>
 * </ul>
 */
package com.tss.grc.module.openbanking;
