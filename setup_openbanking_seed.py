"""Seed Saudi-realistic Open Banking data across 3 tenants.

Sprint 19 PR #4. Pairs with PRs #255 (backend), #256 (F8 rules), #257 (frontend).

Idempotent: each (org, reference) guarded by existence check.

Usage:  python setup_openbanking_seed.py
"""
from __future__ import annotations
import sys
from datetime import date, datetime, timedelta
import pyodbc

sys.stdout.reconfigure(encoding="utf-8")
conn = pyodbc.connect(
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;"
)
conn.autocommit = False
cur = conn.cursor()
NOW = datetime.now()
TODAY = date.today()


def tpp_exists(org, ref):
    cur.execute(
        "SELECT id FROM openbanking.ob_tpp "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_tpp(org, ref, name_en, name_ar, sama_licence, fapi_client,
               roles, country="SA", status="ACTIVE",
               cert_fp=None, cert_expires=None, rate_limit=None,
               am_id=None, co_id=None, onboarded=None,
               suspended_at=None, susp_reason_en=None, susp_reason_ar=None):
    existing = tpp_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO openbanking.ob_tpp "
        "(organisation_id, reference, legal_name_en, legal_name_ar, "
        " sama_licence_ref, fapi_client_id, roles, country, status, "
        " onboarded_at, suspended_at, suspension_reason_en, suspension_reason_ar, "
        " cert_fingerprint, cert_expires_at, rate_limit_per_sec, "
        " account_manager_user_id, compliance_officer_user_id) "
        "OUTPUT INSERTED.id "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, name_en, name_ar, sama_licence, fapi_client, roles, country,
        status, onboarded or TODAY - timedelta(days=180),
        suspended_at, susp_reason_en, susp_reason_ar,
        cert_fp, cert_expires, rate_limit, am_id, co_id,
    )
    return int(cur.fetchone()[0])


def consent_exists(org, ref):
    cur.execute(
        "SELECT id FROM openbanking.ob_consent "
        "WHERE organisation_id = ? AND consent_reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_consent(org, tpp_id, ref, customer_hash, customer_label_en,
                   customer_label_ar, consent_type, scopes, status="ACTIVE",
                   granted_at=None, expires_at=None,
                   revoked_at=None, revoked_by=None, rev_reason_en=None,
                   rev_reason_ar=None, amount_cap=None, freq_window=None,
                   call_count=0, last_used_at=None):
    existing = consent_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO openbanking.ob_consent "
        "(organisation_id, tpp_id, consent_reference, customer_hash, "
        " customer_label_en, customer_label_ar, consent_type, scopes, status, "
        " granted_at, expires_at, revoked_at, revoked_by, "
        " revocation_reason_en, revocation_reason_ar, "
        " amount_cap_sar, frequency_window_days, call_count, last_used_at) "
        "OUTPUT INSERTED.id "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, tpp_id, ref, customer_hash, customer_label_en, customer_label_ar,
        consent_type, scopes, status,
        granted_at or NOW - timedelta(days=60),
        expires_at, revoked_at, revoked_by, rev_reason_en, rev_reason_ar,
        amount_cap, freq_window, call_count, last_used_at,
    )
    return int(cur.fetchone()[0])


def incident_exists(org, ref):
    cur.execute(
        "SELECT id FROM openbanking.ob_incident "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_incident(org, ref, tpp_id, inc_type, severity, status,
                    title_en, title_ar, desc_en, desc_ar,
                    detected_at, sama_reportable=False,
                    sama_notification_ref=None, sama_notified_at=None,
                    affected_count=None, assigned_to=None):
    existing = incident_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO openbanking.ob_incident "
        "(organisation_id, reference, tpp_id, incident_type, severity, status, "
        " title_en, title_ar, description_en, description_ar, "
        " detected_at, sama_reportable, sama_notification_ref, sama_notified_at, "
        " affected_customer_count, assigned_to_user_id) "
        "OUTPUT INSERTED.id "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, tpp_id, inc_type, severity, status, title_en, title_ar,
        desc_en, desc_ar, detected_at,
        1 if sama_reportable else 0,
        sama_notification_ref, sama_notified_at,
        affected_count, assigned_to,
    )
    return int(cur.fetchone()[0])


def insert_event(org, tpp_id=None, consent_id=None, incident_id=None,
                 event_type=None, status_after=None,
                 note_en=None, note_ar=None, occurred_at=None,
                 actor_name=None):
    cur.execute(
        "INSERT INTO openbanking.ob_event "
        "(organisation_id, tpp_id, consent_id, incident_id, occurred_at, "
        " event_type, actor_name, status_after, note_en, note_ar) "
        "VALUES (?,?,?,?,?,?,?,?,?,?)",
        org, tpp_id, consent_id, incident_id,
        occurred_at or NOW, event_type, actor_name, status_after,
        note_en, note_ar,
    )


def insert_api_call(org, tpp_id, consent_ref, occurred_at, method, endpoint,
                    status_code, latency_ms, scopes=None, customer_hash=None,
                    source_ip_hash=None, interaction_id=None,
                    response_bytes=None, error_reason=None):
    cur.execute(
        "INSERT INTO openbanking.ob_api_call "
        "(organisation_id, tpp_id, consent_reference, occurred_at, "
        " http_method, endpoint, status_code, latency_ms, scopes_used, "
        " customer_hash, source_ip_hash, interaction_id, response_bytes, "
        " error_reason) "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, tpp_id, consent_ref, occurred_at, method, endpoint,
        status_code, latency_ms, scopes, customer_hash, source_ip_hash,
        interaction_id, response_bytes, error_reason,
    )


def recount_tpp(org, tpp_id):
    cur.execute(
        "UPDATE openbanking.ob_tpp SET "
        "active_consent_count = ("
        "  SELECT COUNT(*) FROM openbanking.ob_consent "
        "  WHERE organisation_id = ? AND tpp_id = ? "
        "    AND status IN (N'ACTIVE', N'AUTHORISED') AND is_deleted = 0), "
        "open_incident_count = ("
        "  SELECT COUNT(*) FROM openbanking.ob_incident "
        "  WHERE organisation_id = ? AND tpp_id = ? "
        "    AND status NOT IN (N'RESOLVED', N'CLOSED') AND is_deleted = 0) "
        "WHERE id = ?",
        org, tpp_id, org, tpp_id, tpp_id,
    )


def seed_tss_grc():
    org = 1
    print(f"\n=== org={org} TSS GRC — Saudi bank acting as ASPSP ===")
    lean = insert_tpp(
        org, "TPP-LEAN-001", "Lean Technologies KSA", "لين تكنولوجيز السعودية",
        sama_licence="SAMA-OB-AISP-2022-001", fapi_client="lean-prod-aspsp1-2024",
        roles="AISP,PISP", rate_limit=80, onboarded=TODAY - timedelta(days=540),
        cert_fp="SHA256:c4f8b29a7e0d5e3f4a1b6c8d2e5f9a3b7c4e8d2f5a9b3c7e0d4f8a2b6c9d3e7",
        cert_expires=TODAY + timedelta(days=120),
    )
    insert_event(org, tpp_id=lean, event_type="TPP_ONBOARDED",
                 status_after="ACTIVE", actor_name="Sara Al-Otaibi",
                 occurred_at=NOW - timedelta(days=540),
                 note_en="Lean onboarded as Phase-1 AISP pilot. KYB signed off by Compliance.",
                 note_ar="تم تسجيل لين كمزوّد AIS في الموجة الأولى. اعتمدت الالتزام الـ KYB.")

    tarabut = insert_tpp(
        org, "TPP-TARABUT-002", "Tarabut Gateway", "تربط جيتواي",
        sama_licence="SAMA-OB-AISP-2023-008", fapi_client="tarabut-ksa-prod",
        roles="AISP", country="GB", rate_limit=50,
        onboarded=TODAY - timedelta(days=420),
        cert_fp="SHA256:7a3e9b5c1d8f2a4e6b9c3d7e0f5a8b2c4e6d9f3a7b1c5e8d2f6a4b9c3e7d1f5",
        cert_expires=TODAY + timedelta(days=22),
    )
    insert_event(org, tpp_id=tarabut, event_type="CERT_EXPIRING",
                 occurred_at=NOW - timedelta(days=2),
                 actor_name="System (cert-monitor)",
                 note_en="Tarabut mTLS cert expires in 22 days. AR-249 T-30 reminder fired.",
                 note_ar="شهادة تربط تنتهي خلال ٢٢ يوماً. تذكير AR-249 (-٣٠ يوم) صدر تلقائياً.")

    drahim = insert_tpp(
        org, "TPP-DRAHIM-003", "Drahim Financial Solutions", "دراهم للحلول المالية",
        sama_licence="SAMA-OB-AISP-2023-014", fapi_client="drahim-prod-001",
        roles="AISP", rate_limit=40, onboarded=TODAY - timedelta(days=360),
        cert_fp="SHA256:9d4f7a2b6e1c5d8f3a7b9c2e6d4f8a1b5c3e7d9f2a4b6c8e1d5f3a7b9c2e6d4",
        cert_expires=TODAY + timedelta(days=280),
    )
    malaa = insert_tpp(
        org, "TPP-MALAA-004", "Malaa Wealth Aggregator", "ملاءة لتجميع البيانات المالية",
        sama_licence="SAMA-OB-AISP-2024-022", fapi_client="malaa-aispksa",
        roles="AISP", rate_limit=40, onboarded=TODAY - timedelta(days=180),
        cert_fp="SHA256:2c5e8f1a4d7b9c3e6d2f5a8b1c4e7d9f2a5b8c1e4d7f0a3b6c9d2e5f8a1b4c7",
        cert_expires=TODAY + timedelta(days=210),
    )

    hala = insert_tpp(
        org, "TPP-HALA-005", "Hala Payments KSA", "هلا للمدفوعات",
        sama_licence="SAMA-OB-PISP-2023-019", fapi_client="hala-pisp-2024",
        roles="PISP,AISP", status="SUSPENDED", rate_limit=60,
        onboarded=TODAY - timedelta(days=300),
        cert_fp="SHA256:5e8b2d6a9c3f7e1d4b8a2c5f9e3d6b0a4c7f1e5d8b2a6c9f3e7d1b4a8c2e5f9",
        cert_expires=TODAY + timedelta(days=380),
        suspended_at=NOW - timedelta(days=14),
        susp_reason_en="Suspended after AR-242 escalation: Hala read /accounts/balances for "
                       "consent CN-HALA-2024-7791 outside the AIS_ACCOUNT_INFO scope. "
                       "Reinstatement gated on third-party audit + scope-enforcement fix.",
        susp_reason_ar="تم الإيقاف بعد تصعيد AR-242: قراءة هلا لـ /accounts/balances ضمن "
                       "موافقة CN-HALA-2024-7791 خارج نطاق AIS_ACCOUNT_INFO. "
                       "إعادة التفعيل مرتبطة بمراجعة طرف ثالث وإصلاح ضبط النطاقات.",
    )
    insert_event(org, tpp_id=hala, event_type="TPP_SUSPENDED",
                 status_after="SUSPENDED", actor_name="Khalid Al-Harbi (CISO)",
                 occurred_at=NOW - timedelta(days=14),
                 note_en="Suspended after scope-creep incident OBI-2026-001.",
                 note_ar="تم الإيقاف إثر حادثة تجاوز النطاق OBI-2026-001.")

    tweeq = insert_tpp(
        org, "TPP-TWEEQ-006", "Tweeq (STC Pay subsidiary)", "توق (شركة تابعة لـ STC Pay)",
        sama_licence="SAMA-OB-AISP-2025-005", fapi_client="tweeq-sandbox",
        roles="AISP", status="PENDING_KYB", rate_limit=40,
        onboarded=TODAY - timedelta(days=12),
        cert_fp="SHA256:8a4c7f2e5b1d9c3f6a8b2e5d7c1f4a9b3e6d8c2f5a7b1e4d9c3f6a8b2e5d7c1",
        cert_expires=TODAY + timedelta(days=730),
    )

    # Consents
    insert_consent(
        org, lean, "CN-LEAN-2024-0001",
        customer_hash="sha256:f8a3c2d1e5b7f9a4c6d8e2b5a7c9f1d3e5b7a9c1d3f5e7b9a1c3",
        customer_label_en="Ahmed Al-Rashid (SNB customer)",
        customer_label_ar="أحمد الراشد (عميل البنك الأهلي)",
        consent_type="AIS_ACCOUNT_INFO", scopes="accounts,balances",
        granted_at=NOW - timedelta(days=90),
        expires_at=TODAY + timedelta(days=275),
        call_count=247, last_used_at=NOW - timedelta(hours=4),
    )
    insert_consent(
        org, lean, "CN-LEAN-2024-0002",
        customer_hash="sha256:b2d5e8a1c4f7d9b3e6a8c1f4d7e9b2a5c8f1d4e7b9a2c5f8d1e4",
        customer_label_en="Fatima Al-Zahrani (Al Rajhi customer)",
        customer_label_ar="فاطمة الزهراني (عميلة الراجحي)",
        consent_type="AIS_TRANSACTIONS", scopes="accounts,transactions",
        granted_at=NOW - timedelta(days=45),
        expires_at=TODAY + timedelta(days=320),
        call_count=1183, last_used_at=NOW - timedelta(hours=1),
    )
    insert_consent(
        org, lean, "CN-LEAN-2024-0003",
        customer_hash="sha256:a9c3e5f7d1b4a8c2e5f9d3b7a1c4e8d2f5b9a3c7e1d5f8b2a6c9d3",
        customer_label_en="Khalifa Holdings (corporate)",
        customer_label_ar="خليفة القابضة (شركات)",
        consent_type="PIS_PAYMENT", scopes="payments:initiate",
        granted_at=NOW - timedelta(days=120),
        expires_at=TODAY + timedelta(days=245), amount_cap=500000,
        call_count=68, last_used_at=NOW - timedelta(days=2),
    )
    insert_consent(
        org, lean, "CN-LEAN-2024-0004",
        customer_hash="sha256:e1f5a9c3d7b2e6a4c8f2d6b1e5a9c3f7d1b4e8a2c6f0d4b8a2c6",
        customer_label_en="Mohammad Al-Qahtani (SME owner)",
        customer_label_ar="محمد القحطاني (صاحب منشأة)",
        consent_type="AIS_BALANCES", scopes="balances", status="REVOKED",
        revoked_by="CUSTOMER",
        granted_at=NOW - timedelta(days=180), revoked_at=NOW - timedelta(days=11),
        rev_reason_en="Customer closed Lean account via mobile app",
        rev_reason_ar="أغلق العميل حساب لين عبر تطبيق الجوال",
        call_count=842, last_used_at=NOW - timedelta(days=11),
    )
    insert_consent(
        org, tarabut, "CN-TARABUT-2024-0011",
        customer_hash="sha256:d4f8b2a6c0e4d8b2f6a0c4e8b2d6f0a4c8e2b6d0f4a8c2e6d0f4",
        customer_label_en="Saudi Manama Investments",
        customer_label_ar="استثمارات سعودية المنامة",
        consent_type="AIS_TRANSACTIONS", scopes="accounts,transactions,balances",
        granted_at=NOW - timedelta(days=70),
        expires_at=TODAY + timedelta(days=295),
        call_count=2914, last_used_at=NOW - timedelta(minutes=18),
    )
    insert_consent(
        org, tarabut, "CN-TARABUT-2024-0012",
        customer_hash="sha256:6b9d3f7a1c5e9d3b7f1a5c9e3d7b1f5a9c3e7d1b5f9a3c7e1d5",
        customer_label_en="Al Faisaliah Group treasury",
        customer_label_ar="خزينة مجموعة الفيصلية",
        consent_type="AIS_ACCOUNT_INFO", scopes="accounts",
        granted_at=NOW - timedelta(days=22),
        expires_at=TODAY + timedelta(days=343),
        call_count=128, last_used_at=NOW - timedelta(hours=12),
    )
    insert_consent(
        org, drahim, "CN-DRAHIM-2024-0021",
        customer_hash="sha256:b8c2d6f0a4e8c2b6d0f4a8c2e6b0d4f8a2c6e0b4d8f2a6c0e4",
        customer_label_en="Aisha Al-Dosari (retail)",
        customer_label_ar="عائشة الدوسري (أفراد)",
        consent_type="AIS_TRANSACTIONS", scopes="accounts,transactions",
        granted_at=NOW - timedelta(days=130),
        expires_at=TODAY + timedelta(days=235),
        call_count=4127, last_used_at=NOW - timedelta(minutes=42),
    )
    insert_consent(
        org, drahim, "CN-DRAHIM-2024-0022",
        customer_hash="sha256:1e5d9b3f7c1a5e9d3b7f1c5a9e3d7b1f5c9a3e7d1b5f9c3a7e",
        customer_label_en="Sultan Al-Otaibi (retail)",
        customer_label_ar="سلطان العتيبي (أفراد)",
        consent_type="AIS_BALANCES", scopes="balances",
        granted_at=NOW - timedelta(days=60),
        expires_at=TODAY + timedelta(days=305),
        call_count=856, last_used_at=NOW - timedelta(hours=3),
    )
    insert_consent(
        org, drahim, "CN-DRAHIM-2024-0023",
        customer_hash="sha256:4a8b2c6d0e4f8b2a6c0d4e8b2f6a0c4e8b2d6f0a4c8e2b6d0",
        customer_label_en="Reem Al-Shahrani (retail)",
        customer_label_ar="ريم الشهراني (أفراد)",
        consent_type="AIS_ACCOUNT_INFO", scopes="accounts", status="EXPIRED",
        granted_at=NOW - timedelta(days=370),
        expires_at=TODAY - timedelta(days=5),
        call_count=2103, last_used_at=NOW - timedelta(days=6),
    )
    insert_consent(
        org, malaa, "CN-MALAA-2024-0031",
        customer_hash="sha256:f0e4d8c2b6a0f4e8d2c6b0a4f8e2d6c0b4a8f2e6d0c4b8a2f6",
        customer_label_en="Al-Rashed Family Office",
        customer_label_ar="مكتب عائلة الراشد",
        consent_type="AIS_ACCOUNT_INFO", scopes="accounts,balances",
        granted_at=NOW - timedelta(days=55),
        expires_at=TODAY + timedelta(days=310),
        call_count=89, last_used_at=NOW - timedelta(hours=8),
    )
    insert_consent(
        org, hala, "CN-HALA-2024-7791",
        customer_hash="sha256:7c1f5a9d3e7b1c5f9a3d7e1b5c9f3a7d1e5b9c3f7a1d5e9b3c",
        customer_label_en="Omar Al-Mutairi (retail — affected by breach)",
        customer_label_ar="عمر المطيري (أفراد — متأثّر بالحادثة)",
        consent_type="AIS_ACCOUNT_INFO", scopes="accounts", status="REVOKED",
        revoked_by="BANK",
        granted_at=NOW - timedelta(days=180), revoked_at=NOW - timedelta(days=14),
        rev_reason_en="Auto-revoked when TPP Hala was suspended (OBI-2026-001 scope-creep).",
        rev_reason_ar="سحب آلي عند إيقاف مزوّد هلا (OBI-2026-001 تجاوز النطاق).",
        call_count=312, last_used_at=NOW - timedelta(days=14),
    )

    # Bulk revocation: 9 fast revokes feeding AR-245 fraud-signal context
    for i in range(12, 21):
        ts = NOW - timedelta(days=14, hours=i)
        insert_consent(
            org, hala, f"CN-HALA-2024-77{i:02d}",
            customer_hash=f"sha256:hala-{i:02d}-aabbccdd",
            customer_label_en=f"Bulk-revoke customer #{i}",
            customer_label_ar=f"عميل سحب جماعي رقم {i}",
            consent_type="AIS_ACCOUNT_INFO", scopes="accounts", status="REVOKED",
            revoked_by="BANK", granted_at=NOW - timedelta(days=120), revoked_at=ts,
            rev_reason_en="Auto-revoked in bulk after Hala suspension.",
            rev_reason_ar="سحب جماعي آلي بعد إيقاف هلا.",
            call_count=50 + i,
        )

    # API calls (12 samples for API-health 24h panel)
    for hours_ago, status, latency in [
        (1, 200, 87), (2, 200, 92), (3, 200, 78), (4, 200, 105),
        (5, 200, 88), (6, 200, 67), (7, 200, 110), (8, 200, 95),
        (4, 401, 23), (12, 200, 89), (16, 200, 91), (20, 500, 1234),
    ]:
        insert_api_call(
            org, lean, "CN-LEAN-2024-0002",
            occurred_at=NOW - timedelta(hours=hours_ago),
            method="GET", endpoint="/open-banking/v3.1/aisp/accounts",
            status_code=status, latency_ms=latency,
            scopes="accounts,transactions",
            customer_hash="sha256:b2d5e8a1c4f7d9b3e6a8c1f4d7e9b2a5c8f1d4e7b9a2c5f8d1e4",
            source_ip_hash="sha256:lean-prod-egress-001",
            interaction_id=f"int-{hours_ago:02d}-2026",
            response_bytes=4200 if status == 200 else None,
            error_reason="Backend timeout — T24 core slow" if status == 500 else
                         "Invalid bearer token — likely expired access" if status == 401 else None,
        )

    # DATA_LEAK incident — feeds AR-247 (24h SAMA) + AR-250 (PDPL Art.25)
    inc_leak = insert_incident(
        org, "OBI-2026-002", lean,
        inc_type="DATA_LEAK", severity="CRITICAL", status="INVESTIGATING",
        title_en="Lean prod API leaked 1,847 customer transaction histories to unauthenticated endpoint",
        title_ar="تسرّب من واجهات لين الإنتاجية لـ ١٬٨٤٧ سجلّاً لعمليات العملاء عبر نقطة وصول غير مصرَّحة",
        desc_en="Investigation: during a deploy of v4.2.0 to Lean's KSA cluster on 2026-05-23 "
                "at 02:14 AST, a cache-key collision exposed customer transaction snapshots via "
                "the /v3.1/aisp/transactions endpoint without bearer-token validation for 19 minutes. "
                "1,847 unique customer transaction histories returned. "
                "SAMA OBF v2 §11.4 24h notification window applies. "
                "PDPL Article 25 SDAIA 72h-window also engaged via AR-250.",
        desc_ar="التحقيق: أثناء نشر الإصدار 4.2.0 إلى عنقود لين في المملكة بتاريخ ٢٠٢٦/٠٥/٢٣ "
                "الساعة ٠٢:١٤ بتوقيت السعودية، أدّى تصادم مفاتيح ذاكرة التخزين المؤقت إلى كشف "
                "لقطات لعمليات العملاء عبر نقطة /v3.1/aisp/transactions دون التحقّق من رمز "
                "Bearer لمدة ١٩ دقيقة. أُعيد ١٬٨٤٧ سجلّاً فريداً لعمليات عملاء. "
                "تنطبق نافذة الإبلاغ ٢٤ ساعة وفق §١١.٤ من إطار ساما، "
                "إضافة إلى نافذة ٧٢ ساعة وفق المادة ٢٥ من PDPL عبر AR-250.",
        detected_at=NOW - timedelta(hours=6), sama_reportable=True,
        affected_count=1847, assigned_to=2,
    )
    insert_event(org, tpp_id=lean, incident_id=inc_leak,
                 event_type="INCIDENT_RAISED",
                 status_after="OPEN", actor_name="System (SIEM detection)",
                 occurred_at=NOW - timedelta(hours=6),
                 note_en="Splunk detection — anomalous unauthenticated 200 OK on /transactions.",
                 note_ar="رصد Splunk — استجابة 200 OK غير مصرّحة على /transactions.")
    insert_event(org, tpp_id=lean, incident_id=inc_leak,
                 event_type="INCIDENT_STATUS_CHANGED",
                 status_after="INVESTIGATING", actor_name="Khalid Al-Harbi (CISO)",
                 occurred_at=NOW - timedelta(hours=5),
                 note_en="CISO triaging. Splunk forensic + 19-min replay capture started.",
                 note_ar="بدأ CISO الفرز. تم إطلاق تحقيق Splunk + إعادة تشغيل النوافذ.")

    inc_scope = insert_incident(
        org, "OBI-2026-001", hala,
        inc_type="SCOPE_CREEP", severity="HIGH", status="CONTAINED",
        title_en="Hala read /accounts/balances outside the AIS_ACCOUNT_INFO scope (consent CN-HALA-2024-7791)",
        title_ar="قراءة هلا لـ /accounts/balances خارج نطاق AIS_ACCOUNT_INFO (موافقة CN-HALA-2024-7791)",
        desc_en="Hala invoked /v3.1/aisp/accounts/{id}/balances 312 times on consent "
                "CN-HALA-2024-7791 whose scope was AIS_ACCOUNT_INFO only. Scope-enforcement "
                "should have returned 403. Investigation traced to Hala's gateway config drift. "
                "TPP suspended; consent + cascade revocations completed.",
        desc_ar="استدعت هلا /v3.1/aisp/accounts/{id}/balances ٣١٢ مرّة عبر موافقة "
                "CN-HALA-2024-7791 المحدّدة بنطاق AIS_ACCOUNT_INFO فقط. كان يجب أن "
                "تستجيب البوّابة بـ 403. تتبّع التحقيق الخلل إلى انحراف في إعدادات بوّابة هلا. "
                "تم إيقاف المزوّد وسحب الموافقة والموافقات المرتبطة آلياً.",
        detected_at=NOW - timedelta(days=15), sama_reportable=False,
        affected_count=1, assigned_to=3,
    )

    for tpp_id in [lean, tarabut, drahim, malaa, hala, tweeq]:
        recount_tpp(org, tpp_id)
    print(f"  ✓ org={org}: 6 TPPs + ~20 consents + 12 API calls + 2 incidents (1 CRITICAL DATA_LEAK + 1 HIGH SCOPE_CREEP)")


def seed_aramco():
    org = 2
    print(f"\n=== org={org} Aramco Trading — corporate-treasury OB consumption ===")
    tarabut = insert_tpp(
        org, "TPP-TARABUT-AT-001",
        "Tarabut Gateway (Aramco corp)", "تربط جيتواي (شركات أرامكو)",
        sama_licence="SAMA-OB-AISP-2023-008", fapi_client="tarabut-aramco-corp",
        roles="AISP", rate_limit=120, onboarded=TODAY - timedelta(days=210),
        cert_fp="SHA256:e2f6a1c4d7b9e3a5c8f2b6d4e7a9c1f3b6d8a2e5c7f1d4b9a3e6c8d2f5a7b1",
        cert_expires=TODAY + timedelta(days=410),
    )
    geidea = insert_tpp(
        org, "TPP-GEIDEA-AT-002",
        "Geidea B2B Payments", "جيديا للمدفوعات بين الشركات",
        sama_licence="SAMA-OB-PISP-2024-031", fapi_client="geidea-aramco-b2b",
        roles="PISP", rate_limit=80, onboarded=TODAY - timedelta(days=145),
        cert_fp="SHA256:a4c8e1f5b9d3a7c2e6b0d4f8a2c5e9b3d7f1a4c8e2b6d0f4a8c2e6d0b4f8a2",
        cert_expires=TODAY + timedelta(days=315),
    )
    insert_consent(
        org, tarabut, "CN-ARTRD-TARABUT-001",
        customer_hash="sha256:aramco-trading-treasury-master",
        customer_label_en="Aramco Trading Co. treasury (master account)",
        customer_label_ar="خزينة أرامكو للتجارة (الحساب الرئيسي)",
        consent_type="AIS_TRANSACTIONS",
        scopes="accounts,transactions,balances",
        granted_at=NOW - timedelta(days=190),
        expires_at=TODAY + timedelta(days=175),
        call_count=8421, last_used_at=NOW - timedelta(minutes=11),
    )
    insert_consent(
        org, geidea, "CN-ARTRD-GEIDEA-001",
        customer_hash="sha256:aramco-trading-vendor-payments",
        customer_label_en="Aramco vendor-payment program",
        customer_label_ar="برنامج دفعات موردي أرامكو",
        consent_type="VRP_SETUP",
        scopes="payments:initiate,payments:recurring",
        granted_at=NOW - timedelta(days=120),
        expires_at=TODAY + timedelta(days=245),
        amount_cap=50000000, freq_window=30,
        call_count=687, last_used_at=NOW - timedelta(hours=6),
    )
    recount_tpp(org, tarabut)
    recount_tpp(org, geidea)
    print(f"  ✓ org={org}: 2 TPPs + 2 corporate-grade consents")


def seed_stc_bank():
    org = 3
    print(f"\n=== org={org} STC Bank — digital-bank embedded fintech ===")
    lean = insert_tpp(
        org, "TPP-LEAN-STC-001",
        "Lean Technologies (STC Bank partner)", "لين تكنولوجيز (شريك بنك STC)",
        sama_licence="SAMA-OB-AISP-2022-001", fapi_client="lean-stcbank-prod",
        roles="AISP,PISP", rate_limit=100, onboarded=TODAY - timedelta(days=180),
        cert_fp="SHA256:9f3a7d1b5c9e3a7d1b5c9f3a7d1b5c9e3a7d1b5c9f3a7d1b5c9e3a7d1b5c9f",
        cert_expires=TODAY + timedelta(days=85),
    )
    surge = insert_tpp(
        org, "TPP-SURGE-002", "Surge Fintech", "سيرج فينتك",
        sama_licence="SAMA-OB-PISP-2024-040", fapi_client="surge-stcbank-prod",
        roles="PISP", rate_limit=60, onboarded=TODAY - timedelta(days=95),
        cert_fp="SHA256:2e5d8b1c4f7a9d3b6e0c4f8a1d5b9e2c5f8a1d4b7e0c3f6a9d2b5e8c1f4a7d",
        cert_expires=TODAY + timedelta(days=180),
    )
    hyperpay = insert_tpp(
        org, "TPP-HYPERPAY-003", "HyperPay Recurring", "هايبر باي للدفعات الدورية",
        sama_licence="SAMA-OB-VRP-2025-007", fapi_client="hyperpay-stcbank-vrp",
        roles="PISP", rate_limit=60, onboarded=TODAY - timedelta(days=42),
        cert_fp="SHA256:c7f1a4d8b2e6c0f3a7d1b5e9c3f6a0d4b8e2c6f0a4d8b2e6c0f3a7d1b5e9c3",
        cert_expires=TODAY + timedelta(days=520),
    )
    insert_consent(
        org, lean, "CN-STC-LEAN-001",
        customer_hash="sha256:stc-customer-aabbcc-1",
        customer_label_en="STC Pay merchant — digital storefront",
        customer_label_ar="تاجر STC Pay — متجر رقمي",
        consent_type="AIS_TRANSACTIONS", scopes="accounts,transactions",
        granted_at=NOW - timedelta(days=85),
        expires_at=TODAY + timedelta(days=280),
        call_count=15234, last_used_at=NOW - timedelta(minutes=2),
    )
    insert_consent(
        org, surge, "CN-STC-SURGE-001",
        customer_hash="sha256:stc-surge-merchant-21",
        customer_label_en="Surge subscription box (SaaS)",
        customer_label_ar="صندوق اشتراك سيرج (SaaS)",
        consent_type="PIS_PAYMENT", scopes="payments:initiate",
        granted_at=NOW - timedelta(days=22),
        expires_at=TODAY + timedelta(days=343),
        amount_cap=200000,
        call_count=412, last_used_at=NOW - timedelta(hours=1),
    )
    insert_consent(
        org, hyperpay, "CN-STC-HYPER-001",
        customer_hash="sha256:stc-hyperpay-monthly-saas-1",
        customer_label_en="Monthly SaaS billing (Saudi PayTV)",
        customer_label_ar="فواتير SaaS شهرية (تلفزيون مدفوع سعودي)",
        consent_type="VRP_SETUP",
        scopes="payments:initiate,payments:recurring",
        granted_at=NOW - timedelta(days=30),
        expires_at=TODAY + timedelta(days=335),
        amount_cap=999, freq_window=30,
        call_count=21, last_used_at=NOW - timedelta(days=2),
    )
    inc = insert_incident(
        org, "OBI-2026-101", surge,
        inc_type="CONSENT_VIOLATION", severity="HIGH", status="OPEN",
        title_en="Surge initiated payment without valid consent token at 11:32 AST",
        title_ar="سيرج بدأت عملية دفع دون رمز موافقة صالح الساعة ١١:٣٢ بتوقيت السعودية",
        desc_en="Surge POST /payments returned 201 Created though the bearer token "
                "presented had been revoked 6 hours prior (consent CN-STC-SURGE-002 — "
                "now closed). Indicates token-validation cache TTL too high. Affected: "
                "SAR 31,400 in attempted payments, 4 customers. Under investigation.",
        desc_ar="استجابت سيرج بـ 201 Created رغم أن رمز Bearer المُقدَّم قد سُحبت موافقته "
                "قبل ٦ ساعات (موافقة CN-STC-SURGE-002 — مغلقة الآن). يشير ذلك إلى أن "
                "ذاكرة التحقّق طويلة الأجل أكثر من اللازم. التأثير: محاولات دفع بـ ٣١٬٤٠٠ "
                "ريال على ٤ عملاء. قيد التحقيق.",
        detected_at=NOW - timedelta(hours=3), sama_reportable=False,
        affected_count=4, assigned_to=2,
    )
    insert_event(org, tpp_id=surge, incident_id=inc,
                 event_type="INCIDENT_RAISED",
                 status_after="OPEN", actor_name="System (consent-cache audit)",
                 occurred_at=NOW - timedelta(hours=3),
                 note_en="Cache TTL mismatch — token still valid in gateway 6h after revoke.",
                 note_ar="عدم تطابق ذاكرة التخزين المؤقت — الرمز ما زال صالحاً بعد ٦ ساعات من السحب.")
    for tpp_id in [lean, surge, hyperpay]:
        recount_tpp(org, tpp_id)
    print(f"  ✓ org={org}: 3 TPPs + 3 consents (incl. VRP) + 1 OPEN consent-violation incident")


def main() -> None:
    print(">>> Seeding Saudi-realistic Open Banking surfaces across 3 tenants")
    try:
        seed_tss_grc()
        seed_aramco()
        seed_stc_bank()
        conn.commit()
        print("\n✓ Sprint 19 PR #4 seed complete — committed")
    except pyodbc.Error as e:
        conn.rollback()
        print(f"\n✗ seed failed, rolled back: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
