"""Seed Saudi-realistic regulatory inspections + DRs for 3 tenants.

Sprint 15 PR #4. Pairs with PRs #234 (backend), #235 (F8 rules),
#236 (frontend).

Each tenant gets two inspections:
  FY2025 — CLOSED. Full lifecycle with executive summary, all DRs
           SUBMITTED. Shows in the workspace history register.
  FY2026 — Active (FIELDWORK or EXIT_MEETING). Mix of OPEN /
           IN_PROGRESS / SUBMITTED / OVERDUE DRs so the workspace
           detail page has all DR states to render.

Per-tenant content reflects each bank's real regulatory profile:

  org=1  TSS GRC          — SAMA on-site (Cyber + AML + Op-Res)
  org=2  Aramco Trading   — CMA listed-co audit (continuing disclosure)
  org=3  STC Bank         — NCA OTCC (Cyber posture) + SAMA dual track

The bilingual EN+AR DR titles use operator-natural Arabic. SAMA
correspondence terminology throughout — not literal translation.

Idempotent: each (org, external_reference) pair is guarded by an
existence check.

Usage:
  python setup_reginspect_seed.py
"""
from __future__ import annotations

import sys
from datetime import date, datetime, timedelta

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = False
cur = conn.cursor()

NOW = datetime.now()
TODAY = date.today()


def inspection_exists(org_id: int, ext_ref: str) -> int | None:
    cur.execute(
        'SELECT id FROM reginspect.reginspect_inspection '
        'WHERE organisation_id = ? AND external_reference = ? AND is_deleted = 0',
        org_id, ext_ref,
    )
    row = cur.fetchone()
    return int(row[0]) if row else None


def insert_inspection(org_id, ext_ref, regulator, title_en, title_ar,
                       status, notified_at, entry_date, exit_date,
                       closed_at, lead_inspector, scope_en, scope_ar,
                       exec_en, exec_ar, open_dr, overdue_dr, submitted_dr) -> int:
    cur.execute(
        '''INSERT INTO reginspect.reginspect_inspection
        (organisation_id, external_reference, regulator,
         title_en, title_ar, status,
         notified_at, entry_date, exit_date, closed_at,
         lead_inspector_name, scope_en, scope_ar,
         coordinator_user_id, sponsor_user_id,
         executive_summary_en, executive_summary_ar,
         open_dr_count, overdue_dr_count, submitted_dr_count,
         created_at, updated_at, created_by, updated_by, is_deleted)
        OUTPUT INSERTED.id
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                1, 1, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0)''',
        org_id, ext_ref, regulator, title_en, title_ar, status,
        notified_at, entry_date, exit_date, closed_at,
        lead_inspector, scope_en, scope_ar,
        exec_en, exec_ar,
        open_dr, overdue_dr, submitted_dr, NOW, NOW,
    )
    return int(cur.fetchone()[0])


def insert_dr(org_id, inspection_id, ref, title_en, title_ar,
              desc_en, desc_ar, status, requested_at, due_date,
              submitted_at, evidence_ref) -> None:
    cur.execute(
        '''INSERT INTO reginspect.reginspect_document_request
        (organisation_id, inspection_id, reference,
         title_en, title_ar, description_en, description_ar,
         status, requested_at, due_date, submitted_at,
         assigned_to_user_id, evidence_reference,
         created_at, updated_at, created_by, updated_by, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 1, 1, 0)''',
        org_id, inspection_id, ref, title_en, title_ar,
        desc_en, desc_ar, status, requested_at, due_date, submitted_at,
        evidence_ref, NOW, NOW,
    )


def insert_timeline(org_id, inspection_id, occurred_at, update_type,
                     status_after, note_en, note_ar) -> None:
    cur.execute(
        '''INSERT INTO reginspect.reginspect_inspection_update
        (organisation_id, inspection_id, occurred_at, update_type,
         actor_user_id, status_after, note_en, note_ar,
         created_at, updated_at, created_by, updated_by, is_deleted)
        VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, 1, 1, 0)''',
        org_id, inspection_id, occurred_at, update_type,
        status_after, note_en, note_ar, NOW, NOW,
    )


# ─── Tenant 1 — TSS GRC (Saudi Bank, SAMA on-site) ────────────────

def seed_tss(org_id: int) -> tuple[int, int]:
    insp_count = 0
    dr_count = 0

    # FY2025 — CLOSED full-cycle SAMA on-site
    ref = "SAMA-OSI-2025-Q4-001"
    if not inspection_exists(org_id, ref):
        i_id = insert_inspection(
            org_id, ref, "SAMA",
            "FY2025 Q4 SAMA on-site — Cyber + AML + Op-Res posture",
            "تفتيش ساما الميداني للربع الرابع 2025 — الوضع السيبراني ومكافحة غسل الأموال والصمود التشغيلي",
            "CLOSED",
            date(2025, 10, 5), date(2025, 11, 3), date(2025, 12, 15),
            datetime(2026, 1, 20, 14, 30),
            "أ. عبدالله الحربي + فريق ساما (5 مفتّشين)",
            "Cyber posture (SAMA Cyber Framework 2017), AML/CFT transaction monitoring, Op-Res 2024 readiness",
            "الوضع السيبراني (إطار ساما السيبراني 2017)، مراقبة معاملات غسل الأموال، جاهزية الصمود التشغيلي 2024",
            "Inspection closed with 4 findings — 2 cyber (MFA coverage on legacy auth + EDR coverage), 1 AML "
            "(STR drafting timeliness for SADAD push-payment alerts), 1 Op-Res (severe-but-plausible exercise "
            "frequency). All responses submitted; CAPA tracking active.",
            "أُغلق التفتيش بأربع ملاحظات — اثنتان سيبرانيتان (تغطية المصادقة متعدّدة العوامل على الأنظمة "
            "القديمة + تغطية الاكتشاف والاستجابة في النقاط الطرفية)، واحدة لمكافحة غسل الأموال (سرعة "
            "صياغة بلاغات المعاملات المشبوهة لتنبيهات سداد)، وواحدة للصمود التشغيلي (وتيرة تمارين "
            "السيناريوهات الشديدة المحتملة). جميع الردود مُقدَّمة؛ متابعة الإجراءات التصحيحية فعّالة.",
            0, 0, 7,
        )
        insp_count += 1
        # 7 SUBMITTED DRs
        for i, (en, ar, ev) in enumerate([
            ("Q3-2025 SADAD reconciliation logs",
             "سجلات تسوية سداد للربع الثالث 2025",
             "SP/SAMA-Q4-2025/DR-001"),
            ("FY2025 MFA coverage attestation for all banking apps",
             "إقرار تغطية المصادقة متعدّدة العوامل لجميع التطبيقات المصرفية للسنة 2025",
             "SP/SAMA-Q4-2025/DR-002"),
            ("EDR endpoint coverage Q3-2025 audit report",
             "تقرير تدقيق تغطية الاكتشاف والاستجابة في النقاط الطرفية للربع الثالث 2025",
             "SP/SAMA-Q4-2025/DR-003"),
            ("AML transaction-monitoring rule library + tuning history",
             "مكتبة قواعد مراقبة معاملات غسل الأموال وسجل التعديلات",
             "SP/SAMA-Q4-2025/DR-004"),
            ("SAFIU STR submissions Q3-2025 with response times",
             "بلاغات المعاملات المشبوهة المُقدَّمة للوحدة للربع الثالث 2025 مع أزمنة الردّ",
             "SP/SAMA-Q4-2025/DR-005"),
            ("Op-Res FY2024 assessment + management responses",
             "تقييم الصمود التشغيلي للسنة 2024 مع الردود الإدارية",
             "SP/SAMA-Q4-2025/DR-006"),
            ("Severe-but-plausible exercise log FY2024-2025",
             "سجل تمارين السيناريوهات الشديدة المحتملة للفترة 2024-2025",
             "SP/SAMA-Q4-2025/DR-007"),
        ], start=1):
            insert_dr(
                org_id, i_id, f"{ref}-DR-{i:03d}",
                en, ar, en, ar, "SUBMITTED",
                datetime(2025, 11, 5 + i, 10, 0),
                date(2025, 11, 10 + i),
                datetime(2025, 11, 9 + i, 16, 30),
                ev,
            )
            dr_count += 1
        # Timeline (3 status changes + 7 DR ops + closure)
        insert_timeline(org_id, i_id, datetime(2025, 10, 5, 9, 0),
                        "STATUS_CHANGED", "PLANNED",
                        "Inspection workspace opened on SAMA notification",
                        "افتتاح ورشة التفتيش بناءً على إشعار ساما")
        insert_timeline(org_id, i_id, datetime(2025, 11, 3, 9, 30),
                        "STATUS_CHANGED", "ENTRY_MEETING",
                        "Entry meeting held — engagement letter received, scope locked",
                        "عقد اجتماع الافتتاح — استلام خطاب التكليف وإغلاق النطاق")
        insert_timeline(org_id, i_id, datetime(2025, 11, 5, 14, 0),
                        "STATUS_CHANGED", "FIELDWORK",
                        "Fieldwork started — 7 document requests raised over 3 weeks",
                        "بداية العمل الميداني — رفع 7 طلبات وثائق خلال 3 أسابيع")
        insert_timeline(org_id, i_id, datetime(2025, 12, 15, 11, 0),
                        "STATUS_CHANGED", "EXIT_MEETING",
                        "Exit meeting — 4 provisional findings",
                        "اجتماع الإغلاق — 4 ملاحظات مبدئية")
        insert_timeline(org_id, i_id, datetime(2025, 12, 17, 16, 0),
                        "STATUS_CHANGED", "RESPONSE_DRAFTING",
                        "Response drafting started",
                        "بداية صياغة الردود")
        insert_timeline(org_id, i_id, datetime(2026, 1, 20, 14, 30),
                        "CLOSURE", "CLOSED",
                        "Inspection closed — formal letter received, all responses accepted",
                        "إغلاق التفتيش — استلام الخطاب الرسمي وقبول جميع الردود")

    # FY2026 — Active FIELDWORK
    ref = "SAMA-OSI-2026-Q2-001"
    existing = inspection_exists(org_id, ref)
    if existing is None:
        i_id = insert_inspection(
            org_id, ref, "SAMA",
            "FY2026 Q2 SAMA on-site — IFRS-9 ECL + Liquidity",
            "تفتيش ساما الميداني للربع الثاني 2026 — معيار 9 للخسائر الائتمانية المتوقّعة والسيولة",
            "FIELDWORK",
            date(2026, 4, 12), date(2026, 5, 5), date(2026, 6, 30),
            None,
            "أ. مشاري السبيعي + فريق ساما (4 مفتّشين)",
            "IFRS-9 ECL model validation, LCR/NSFR daily monitoring, treasury risk posture",
            "التحقّق من نموذج الخسائر الائتمانية المتوقّعة وفق معيار 9، الرصد اليومي لنسبة تغطية السيولة "
            "وصافي التمويل المستقرّ، وضع مخاطر الخزينة",
            None, None,
            3, 1, 2,
        )
        insp_count += 1
        # 6 DRs in mixed states
        drs = [
            ("SUBMITTED", "Q1-2026 IFRS-9 ECL parameters + back-test results",
             "وسائط ونتائج التحقّق الخلفي لنموذج الخسائر الائتمانية المتوقّعة للربع الأوّل 2026",
             5, 5, "SP/SAMA-Q2-2026/DR-001"),
            ("SUBMITTED", "LCR daily values FY2026-YTD",
             "قيم نسبة تغطية السيولة اليومية منذ بداية 2026",
             7, 5, "SP/SAMA-Q2-2026/DR-002"),
            ("IN_PROGRESS", "Treasury risk appetite framework + recent excursions",
             "إطار شهية مخاطر الخزينة والاستثناءات الأخيرة",
             10, 12, None),
            ("OPEN", "Model risk management — ECL model governance pack",
             "إدارة مخاطر النماذج — حزمة حوكمة نموذج الخسائر الائتمانية",
             3, 15, None),
            ("OPEN", "Recovery & Resolution Plan FY2026 — current draft",
             "خطة التعافي والحلّ للسنة 2026 — المسوّدة الحالية",
             2, 18, None),
            ("OPEN", "Stress test FY2025 results + management actions taken",
             "نتائج اختبار الضغط للسنة 2025 والإجراءات الإدارية المتّخذة",
             1, -5, None),  # -5 = overdue
        ]
        for i, (status, en, ar, days_ago, due_in, ev) in enumerate(drs, start=1):
            req_at = NOW - timedelta(days=days_ago)
            due_date = TODAY + timedelta(days=due_in)
            submitted_at = None
            if status == "SUBMITTED":
                submitted_at = req_at + timedelta(days=days_ago - due_in - 1)
            insert_dr(
                org_id, i_id, f"{ref}-DR-{i:03d}",
                en, ar, en, ar, status,
                req_at, due_date, submitted_at, ev,
            )
            dr_count += 1
        # Timeline
        insert_timeline(org_id, i_id, datetime(2026, 4, 12, 9, 0),
                        "STATUS_CHANGED", "PLANNED",
                        "Workspace opened",
                        "افتتاح الورشة")
        insert_timeline(org_id, i_id, datetime(2026, 5, 5, 9, 30),
                        "STATUS_CHANGED", "ENTRY_MEETING",
                        "Entry meeting held",
                        "عقد اجتماع الافتتاح")
        insert_timeline(org_id, i_id, datetime(2026, 5, 8, 11, 0),
                        "STATUS_CHANGED", "FIELDWORK",
                        "Fieldwork in progress — 6 DRs raised so far",
                        "العمل الميداني جارٍ — تم رفع 6 طلبات حتى الآن")

    return insp_count, dr_count


# ─── Tenant 2 — Aramco Trading (CMA listed-co) ────────────────────

def seed_aramco(org_id: int) -> tuple[int, int]:
    insp_count = 0
    dr_count = 0

    # FY2025 — CLOSED CMA continuing-disclosure audit
    ref = "CMA-OSI-2025-Q3-001"
    if not inspection_exists(org_id, ref):
        i_id = insert_inspection(
            org_id, ref, "CMA",
            "FY2025 CMA listing audit — continuing-disclosure review",
            "تدقيق هيئة السوق المالية 2025 — مراجعة الإفصاحات المستمرّة",
            "CLOSED",
            date(2025, 7, 10), date(2025, 8, 5), date(2025, 9, 20),
            datetime(2025, 11, 5, 10, 0),
            "أ. هند الزهراني + فريق هيئة السوق المالية",
            "Continuing disclosure obligations, related-party transactions, board independence",
            "التزامات الإفصاح المستمرّ، المعاملات مع الأطراف ذات العلاقة، استقلالية المجلس",
            "Closed with 2 findings — late insider-trading window disclosure (FY2024 Q4) + 1 related-party "
            "transaction not pre-approved by Audit Committee. Both responses accepted; CAPA tracking active.",
            "أُغلق بملاحظتين — تأخّر الإفصاح عن نافذة الإفصاح للمطّلعين (الربع الرابع 2024) ومعاملة واحدة "
            "مع طرف ذي علاقة لم تُعتمَد مسبقاً من لجنة المراجعة. كلا الردّين مقبولان؛ متابعة الإجراءات "
            "التصحيحية فعّالة.",
            0, 0, 5,
        )
        insp_count += 1
        for i, (en, ar, ev) in enumerate([
            ("FY2024 + Q1-2025 board-meeting minutes",
             "محاضر اجتماعات المجلس للسنة 2024 والربع الأوّل 2025",
             "SP/CMA-Q3-2025/DR-001"),
            ("Insider trading window disclosure log FY2024",
             "سجل الإفصاح عن نوافذ تداول المطّلعين للسنة 2024",
             "SP/CMA-Q3-2025/DR-002"),
            ("Related-party transactions register FY2024-2025",
             "سجل المعاملات مع الأطراف ذات العلاقة للفترة 2024-2025",
             "SP/CMA-Q3-2025/DR-003"),
            ("Audit Committee charter + independence attestations",
             "ميثاق لجنة المراجعة وإقرارات الاستقلالية",
             "SP/CMA-Q3-2025/DR-004"),
            ("Annual board evaluation report FY2024",
             "تقرير التقييم السنوي للمجلس للسنة 2024",
             "SP/CMA-Q3-2025/DR-005"),
        ], start=1):
            insert_dr(
                org_id, i_id, f"{ref}-DR-{i:03d}",
                en, ar, en, ar, "SUBMITTED",
                datetime(2025, 8, 7 + i, 10, 0),
                date(2025, 8, 12 + i),
                datetime(2025, 8, 11 + i, 17, 0),
                ev,
            )
            dr_count += 1
        insert_timeline(org_id, i_id, datetime(2025, 11, 5, 10, 0),
                        "CLOSURE", "CLOSED",
                        "CMA closure letter received",
                        "استلام خطاب الإغلاق من هيئة السوق المالية")

    # FY2026 — Active EXIT_MEETING
    ref = "CMA-OSI-2026-Q2-001"
    if not inspection_exists(org_id, ref):
        i_id = insert_inspection(
            org_id, ref, "CMA",
            "FY2026 CMA — energy-trading desk supervision review",
            "هيئة السوق المالية 2026 — مراجعة الإشراف على مكاتب تداول الطاقة",
            "EXIT_MEETING",
            date(2026, 3, 15), date(2026, 4, 7), date(2026, 5, 18),
            None,
            "أ. سعد العتيبي + فريق هيئة السوق المالية",
            "Energy-trading desk supervision (LNG + crude), market-abuse controls, e-comms surveillance",
            "الإشراف على مكاتب تداول الطاقة (الغاز المسال والخام)، ضوابط منع التلاعب بالسوق، "
            "مراقبة الاتصالات الإلكترونية",
            None, None,
            0, 0, 4,
        )
        insp_count += 1
        for i, (en, ar, ev) in enumerate([
            ("Energy-trading desk org chart + supervisor coverage",
             "هيكل مكتب تداول الطاقة وتغطية المشرفين",
             "SP/CMA-Q2-2026/DR-001"),
            ("Market-abuse controls library + recent alerts triage",
             "مكتبة ضوابط منع التلاعب بالسوق وفرز التنبيهات الأخيرة",
             "SP/CMA-Q2-2026/DR-002"),
            ("E-comms surveillance Q1-2026 — keyword hits + dispositions",
             "مراقبة الاتصالات الإلكترونية للربع الأوّل 2026 — نتائج الكلمات المفتاحية ومعالجاتها",
             "SP/CMA-Q2-2026/DR-003"),
            ("Trader certification + compliance training records",
             "سجلات اعتماد المتداولين والتدريب على الالتزام",
             "SP/CMA-Q2-2026/DR-004"),
        ], start=1):
            insert_dr(
                org_id, i_id, f"{ref}-DR-{i:03d}",
                en, ar, en, ar, "SUBMITTED",
                datetime(2026, 4, 10 + i, 9, 0),
                date(2026, 4, 15 + i),
                datetime(2026, 4, 14 + i, 16, 0),
                ev,
            )
            dr_count += 1
        insert_timeline(org_id, i_id, datetime(2026, 5, 18, 10, 0),
                        "STATUS_CHANGED", "EXIT_MEETING",
                        "Exit meeting — provisional findings discussed; responses due 48h",
                        "اجتماع الإغلاق — مناقشة الملاحظات المبدئية؛ الردود مستحقّة خلال 48 ساعة")

    return insp_count, dr_count


# ─── Tenant 3 — STC Bank (NCA OTCC + dual SAMA) ───────────────────

def seed_stc(org_id: int) -> tuple[int, int]:
    insp_count = 0
    dr_count = 0

    # FY2025 — CLOSED NCA cyber
    ref = "NCA-OSI-2025-Q4-001"
    if not inspection_exists(org_id, ref):
        i_id = insert_inspection(
            org_id, ref, "NCA",
            "FY2025 Q4 NCA OTCC — operational technology cyber controls",
            "هيئة الأمن السيبراني للربع الرابع 2025 — ضوابط الأمن السيبراني للتقنية التشغيلية",
            "CLOSED",
            date(2025, 9, 20), date(2025, 10, 12), date(2025, 11, 25),
            datetime(2026, 1, 8, 14, 0),
            "أ. خالد البلوي + فريق هيئة الأمن السيبراني",
            "OTCC compliance, SS7/SIGTRAN security, HSM cluster controls, voice-biometric system risk",
            "الامتثال لضوابط الأمن السيبراني للتقنية التشغيلية، أمن SS7/SIGTRAN، ضوابط مجموعة وحدات "
            "الأجهزة الأمنية، مخاطر نظام التحقّق البيومتري الصوتي",
            "Closed with 3 findings — SS7 filtering rules incomplete, HSM key-rotation cadence > policy, "
            "voice-biometric anti-spoofing model lacks adversarial-test evidence. All responses accepted; "
            "CAPA tracking active in cyber dashboard.",
            "أُغلق بثلاث ملاحظات — قواعد تصفية SS7 غير مكتملة، وتيرة تدوير المفاتيح في وحدة الأجهزة "
            "الأمنية تتجاوز السياسة، نموذج مكافحة الانتحال في التحقّق الصوتي يفتقر إلى دليل اختبار "
            "خصومي. جميع الردود مقبولة؛ متابعة الإجراءات التصحيحية فعّالة في لوحة الأمن السيبراني.",
            0, 0, 6,
        )
        insp_count += 1
        for i, (en, ar, ev) in enumerate([
            ("SS7/SIGTRAN filter ruleset + last 3 months alert log",
             "قواعد تصفية SS7/SIGTRAN وسجل تنبيهات آخر 3 أشهر",
             "SP/NCA-Q4-2025/DR-001"),
            ("HSM key-rotation policy + actual rotation dates FY2025",
             "سياسة تدوير المفاتيح في وحدة الأجهزة الأمنية وتواريخ التدوير الفعلية 2025",
             "SP/NCA-Q4-2025/DR-002"),
            ("Voice-biometric anti-spoofing test reports",
             "تقارير اختبار مكافحة الانتحال في التحقّق الصوتي",
             "SP/NCA-Q4-2025/DR-003"),
            ("USSD *900# session monitoring + abnormal-volume alerts",
             "مراقبة جلسات USSD ‎*900# وتنبيهات الحجم الشاذّ",
             "SP/NCA-Q4-2025/DR-004"),
            ("OT/IT network segmentation diagram + last audit findings",
             "مخطّط فصل شبكات التقنية التشغيلية/المعلوماتية وملاحظات آخر تدقيق",
             "SP/NCA-Q4-2025/DR-005"),
            ("Cyber incident log Q3-Q4 2025 + closure evidence",
             "سجل حوادث الأمن السيبراني للربعين الثالث والرابع 2025 وأدلّة الإغلاق",
             "SP/NCA-Q4-2025/DR-006"),
        ], start=1):
            insert_dr(
                org_id, i_id, f"{ref}-DR-{i:03d}",
                en, ar, en, ar, "SUBMITTED",
                datetime(2025, 10, 15 + i, 10, 0),
                date(2025, 10, 20 + i),
                datetime(2025, 10, 19 + i, 16, 30),
                ev,
            )
            dr_count += 1
        insert_timeline(org_id, i_id, datetime(2026, 1, 8, 14, 0),
                        "CLOSURE", "CLOSED",
                        "NCA closure letter received with 3 findings",
                        "استلام خطاب الإغلاق من هيئة الأمن السيبراني بثلاث ملاحظات")

    # FY2026 — Active FIELDWORK SAMA
    ref = "SAMA-OSI-2026-Q1-001"
    if not inspection_exists(org_id, ref):
        i_id = insert_inspection(
            org_id, ref, "SAMA",
            "FY2026 Q1 SAMA on-site — payment-services regulation + telco-bank dual oversight",
            "تفتيش ساما الميداني للربع الأوّل 2026 — تنظيم خدمات المدفوعات والإشراف الثنائي للمصرفية الاتصالاتية",
            "FIELDWORK",
            date(2026, 2, 10), date(2026, 3, 8), date(2026, 4, 30),
            None,
            "أ. نادية القحطاني + فريق ساما (3 مفتّشين)",
            "Payment-services regulation compliance, mobile-money safeguarding, telco-bank dual-oversight "
            "with CITC, customer-protection complaints",
            "الامتثال لتنظيم خدمات المدفوعات، حماية أموال المحفظة الجوّالة، الإشراف الثنائي للمصرفية "
            "الاتصالاتية مع هيئة الاتصالات، شكاوى حماية العملاء",
            None, None,
            2, 1, 3,
        )
        insp_count += 1
        drs = [
            ("SUBMITTED", "Customer-money safeguarding daily reconciliation Q4-2025 + Q1-2026",
             "تسوية يومية لحماية أموال العملاء للربع الرابع 2025 والأوّل 2026",
             3, 5, "SP/SAMA-Q1-2026/DR-001"),
            ("SUBMITTED", "Mobile-money outage register + RCA closures FY2025",
             "سجل انقطاعات المحفظة الجوّالة وإغلاقات تحليل الأسباب الجذرية للسنة 2025",
             5, 5, "SP/SAMA-Q1-2026/DR-002"),
            ("SUBMITTED", "Customer complaints log Q4-2025 + SAMA escalations",
             "سجل شكاوى العملاء للربع الرابع 2025 والتصعيدات إلى ساما",
             7, 5, "SP/SAMA-Q1-2026/DR-003"),
            ("IN_PROGRESS", "Dual-oversight memo with CITC — shared incident-reporting protocol",
             "مذكّرة الإشراف الثنائي مع هيئة الاتصالات — بروتوكول الإبلاغ المشترك عن الحوادث",
             4, 10, None),
            ("OPEN", "Payment-services pricing schedule FY2026 + customer disclosure",
             "جدول تسعير خدمات المدفوعات للسنة 2026 والإفصاح للعملاء",
             2, 12, None),
            ("OPEN", "SIM-swap fraud controls + recent override log",
             "ضوابط احتيال تبديل شريحة الاتصال وسجل التجاوزات الأخيرة",
             1, -3, None),
        ]
        for i, (status, en, ar, days_ago, due_in, ev) in enumerate(drs, start=1):
            req_at = NOW - timedelta(days=days_ago)
            due_date = TODAY + timedelta(days=due_in)
            submitted_at = (req_at + timedelta(days=days_ago - due_in - 1)
                            if status == "SUBMITTED" else None)
            insert_dr(
                org_id, i_id, f"{ref}-DR-{i:03d}",
                en, ar, en, ar, status,
                req_at, due_date, submitted_at, ev,
            )
            dr_count += 1
        insert_timeline(org_id, i_id, datetime(2026, 2, 10, 9, 0),
                        "STATUS_CHANGED", "PLANNED",
                        "Workspace opened on SAMA + CITC joint notification",
                        "افتتاح الورشة بناءً على إشعار مشترك من ساما وهيئة الاتصالات")
        insert_timeline(org_id, i_id, datetime(2026, 3, 8, 9, 30),
                        "STATUS_CHANGED", "ENTRY_MEETING",
                        "Entry meeting",
                        "اجتماع الافتتاح")
        insert_timeline(org_id, i_id, datetime(2026, 3, 10, 11, 0),
                        "STATUS_CHANGED", "FIELDWORK",
                        "Fieldwork in progress",
                        "العمل الميداني جارٍ")

    return insp_count, dr_count


def main():
    print(">>> Seeding Saudi-realistic regulatory inspections + DRs")
    total_i = 0
    total_dr = 0
    try:
        for org_id, label, seeder in [
            (1, "TSS GRC",        seed_tss),
            (2, "Aramco Trading", seed_aramco),
            (3, "STC Bank",       seed_stc),
        ]:
            print(f"-- org={org_id} ({label}) --")
            i, d = seeder(org_id)
            print(f"  ✓ {i} inspection(s), {d} DR(s)")
            total_i += i
            total_dr += d
        conn.commit()
        print(f"✓ done — {total_i} inspections + {total_dr} DRs inserted")
    except Exception as e:
        conn.rollback()
        print(f"! rolled back: {e}")
        raise


if __name__ == '__main__':
    main()
