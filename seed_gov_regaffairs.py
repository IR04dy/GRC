# -*- coding: utf-8 -*-
"""Seed Saudi-government regulatory-affairs depth for the default tenant (org=1).

Domain "regaffairs" — schemas: openbanking, sharia, reginspect, regreporting.

The default tenant (organisation_id = 1) is a Saudi GENERAL AUTHORITY
(هيئة عامة) headed by a President (رئيس الهيئة). The existing seeds
(setup_reginspect_seed.py / setup_regreporting_seed.py) filled these
tables with COMMERCIAL-BANK content (SAMA liquidity/capital/AML, ZATCA
VAT, CMA listed-co audits). None of that reflects what a public authority
actually deals with.

A Saudi public authority is INSPECTED by oversight bodies — DGA (digital
government), NCA (ECC cyber), Nazaha (integrity/anti-corruption), NDMO/SDAIA
(national data), the General Auditing Bureau — and it FILES periodic returns
to those bodies (DGA digital-maturity/Qiyas, NDMO data-management maturity,
NCEMA business-continuity readiness, Nazaha integrity plan, MoF/Etimad budget
execution, Vision-2030 VRO KPI returns).

This script DEEPENS reginspect + regreporting with that coherent government
layer. It SKIPS openbanking + sharia entirely — those are banking-only
constructs (Open Banking TPP/consent/API telemetry; Shariah supervisory
board, product approvals, fatwas) that are wholly inapplicable to a secular
public authority. Rationale logged per-table at the bottom.

Cross-linking note (verified against the live DB): reginspect/regreporting
carry NO FK columns to platform.departments / admin.committees / iam.users —
ownership is expressible only via the bigint *_user_id columns (spread across
iam.users 1-6 here, instead of the existing seeds' hardcoded 1) and via
free-text role-string columns (owner_role / reviewer_role, aligned to the
department codes). No new schema, no FK invention.

Idempotent (mirrors seed_gov_committees.py / the setup_*_seed.py guards):
  reginspect_inspection        → (organisation_id, external_reference)
  reginspect_document_request  → (organisation_id, reference)
  reginspect_inspection_update → (organisation_id, inspection_id, occurred_at, update_type)
  regreport_obligation         → (organisation_id, code)
  regreport_filing             → (organisation_id, reference)
  regreport_event              → (organisation_id, filing_id, occurred_at, event_type)
A second run inserts 0.

SAFETY: INSERT-only; no DELETE/TRUNCATE/DROP/ALTER; organisation_id = 1 only;
only the four assigned schemas are touched. Existing rows are never modified.

    Usage:  python seed_gov_regaffairs.py
"""
from __future__ import annotations

import sys
from datetime import date, datetime, timedelta

import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

_CONN_CANDIDATES = [
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;",
    "DRIVER={ODBC Driver 18 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Encrypt=no;TrustServerCertificate=yes;Unicode_Results=Yes;",
]


def connect() -> pyodbc.Connection:
    last = None
    for cs in _CONN_CANDIDATES:
        try:
            return pyodbc.connect(cs)
        except Exception as exc:  # noqa: BLE001 - try next driver
            last = exc
    raise SystemExit(f"Could not connect to SQL Server: {last}")


ORG = 1
NOW = datetime.now()
TODAY = date.today()


def TS(days_back: int, hour: int = 10, minute: int = 0) -> datetime:
    """Deterministic timestamp anchored to TODAY (NOT wall-clock NOW), so event
    `occurred_at` values are identical across runs and the composite idempotency
    guard (org, filing_id, occurred_at, event_type) holds. Using datetime.now()
    here would make every run emit a new timestamp -> duplicate events."""
    return datetime.combine(TODAY - timedelta(days=days_back),
                            datetime.min.time().replace(hour=hour, minute=minute))

# iam.users (org 1): 1=admin (President office), 2=rmanager, 3=compliance,
# 4=auditor, 5=ranalyst, 6=viewer. We spread ownership rather than hardcode 1.

# running counters
counts: dict[str, int] = {
    "reginspect_inspection": 0,
    "reginspect_document_request": 0,
    "reginspect_inspection_update": 0,
    "regreport_obligation": 0,
    "regreport_filing": 0,
    "regreport_event": 0,
}


# ─────────────────────────────────────────────────────────────────────────
#  reginspect — existence checks + inserts (mirror setup_reginspect_seed.py)
# ─────────────────────────────────────────────────────────────────────────

def insp_id(cur, ext_ref: str) -> int | None:
    cur.execute(
        "SELECT id FROM reginspect.reginspect_inspection "
        "WHERE organisation_id=? AND external_reference=? AND is_deleted=0",
        ORG, ext_ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_inspection(cur, ext_ref, regulator, title_en, title_ar, status,
                      notified_at, entry_date, exit_date, closed_at,
                      lead_inspector, scope_en, scope_ar, exec_en, exec_ar,
                      coordinator_uid, sponsor_uid,
                      open_dr, overdue_dr, submitted_dr) -> int:
    """Insert and return id. Caller guards existence."""
    cur.execute(
        """INSERT INTO reginspect.reginspect_inspection
            (organisation_id, external_reference, regulator,
             title_en, title_ar, status,
             notified_at, entry_date, exit_date, closed_at,
             lead_inspector_name, scope_en, scope_ar,
             coordinator_user_id, sponsor_user_id,
             executive_summary_en, executive_summary_ar,
             open_dr_count, overdue_dr_count, submitted_dr_count,
             created_at, updated_at, created_by, updated_by, is_deleted)
        OUTPUT INSERTED.id
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, 1, 1, 0)""",
        ORG, ext_ref, regulator, title_en, title_ar, status,
        notified_at, entry_date, exit_date, closed_at,
        lead_inspector, scope_en, scope_ar,
        coordinator_uid, sponsor_uid, exec_en, exec_ar,
        open_dr, overdue_dr, submitted_dr, NOW, NOW,
    )
    counts["reginspect_inspection"] += 1
    return int(cur.fetchone()[0])


def insert_dr(cur, inspection_id, ref, title_en, title_ar, desc_en, desc_ar,
              status, requested_at, due_date, submitted_at, assigned_uid,
              evidence_ref) -> None:
    cur.execute(
        "SELECT id FROM reginspect.reginspect_document_request "
        "WHERE organisation_id=? AND reference=? AND is_deleted=0",
        ORG, ref,
    )
    if cur.fetchone():
        return
    cur.execute(
        """INSERT INTO reginspect.reginspect_document_request
            (organisation_id, inspection_id, reference,
             title_en, title_ar, description_en, description_ar,
             status, requested_at, due_date, submitted_at,
             assigned_to_user_id, evidence_reference,
             created_at, updated_at, created_by, updated_by, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0)""",
        ORG, inspection_id, ref, title_en, title_ar, desc_en, desc_ar,
        status, requested_at, due_date, submitted_at, assigned_uid,
        evidence_ref, NOW, NOW,
    )
    counts["reginspect_document_request"] += 1


def insert_timeline(cur, inspection_id, occurred_at, update_type, status_after,
                    note_en, note_ar, actor_uid) -> None:
    # composite idempotency guard — no natural code column
    cur.execute(
        "SELECT id FROM reginspect.reginspect_inspection_update "
        "WHERE organisation_id=? AND inspection_id=? AND occurred_at=? "
        "AND update_type=? AND is_deleted=0",
        ORG, inspection_id, occurred_at, update_type,
    )
    if cur.fetchone():
        return
    cur.execute(
        """INSERT INTO reginspect.reginspect_inspection_update
            (organisation_id, inspection_id, occurred_at, update_type,
             actor_user_id, status_after, note_en, note_ar,
             created_at, updated_at, created_by, updated_by, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0)""",
        ORG, inspection_id, occurred_at, update_type,
        actor_uid, status_after, note_en, note_ar, NOW, NOW,
    )
    counts["reginspect_inspection_update"] += 1


# ─────────────────────────────────────────────────────────────────────────
#  regreporting — existence checks + inserts (mirror setup_regreporting_seed)
# ─────────────────────────────────────────────────────────────────────────

def oblig_id(cur, code: str) -> int | None:
    cur.execute(
        "SELECT id FROM regreporting.regreport_obligation "
        "WHERE organisation_id=? AND code=? AND is_deleted=0",
        ORG, code,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_obligation(cur, code, regulator, frequency, title_en, title_ar,
                      due_offset_bd, owner_role, reviewer_role,
                      statutory_basis, filing_channel, desc_en, desc_ar,
                      reminder_ladder="14,7,3,1") -> int:
    existing = oblig_id(cur, code)
    if existing:
        return existing
    cur.execute(
        """INSERT INTO regreporting.regreport_obligation
            (organisation_id, code, regulator, frequency, title_en, title_ar,
             description_en, description_ar, due_offset_business_days,
             statutory_basis, owner_role, reviewer_role, filing_channel,
             reminder_ladder, active, created_at, updated_at,
             created_by, updated_by, is_deleted)
        OUTPUT INSERTED.id
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 1, 1, 0)""",
        ORG, code, regulator, frequency, title_en, title_ar,
        desc_en, desc_ar, due_offset_bd, statutory_basis, owner_role,
        reviewer_role, filing_channel, reminder_ladder, NOW, NOW,
    )
    counts["regreport_obligation"] += 1
    return int(cur.fetchone()[0])


def filing_id(cur, reference: str) -> int | None:
    cur.execute(
        "SELECT id FROM regreporting.regreport_filing "
        "WHERE organisation_id=? AND reference=? AND is_deleted=0",
        ORG, reference,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_filing(cur, obligation_id, reference, status,
                  period_start, period_end, due_date,
                  preparer_uid, reviewer_uid, approver_uid,
                  submitted_at=None, ack_ref=None, acknowledged_at=None,
                  closed_at=None, artifact_ref=None,
                  prep_en=None, prep_ar=None,
                  internal_target=None) -> int | None:
    existing = filing_id(cur, reference)
    if existing:
        return existing
    cur.execute(
        """INSERT INTO regreporting.regreport_filing
            (organisation_id, obligation_id, reference, status,
             period_start, period_end, due_date, internal_target_date,
             submitted_at, regulator_ack_reference, acknowledged_at,
             closed_at, filing_artifact_reference,
             preparation_notes_en, preparation_notes_ar,
             preparer_user_id, reviewer_user_id, approver_user_id,
             created_at, updated_at, created_by, updated_by, is_deleted)
        OUTPUT INSERTED.id
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, 1, 1, 0)""",
        ORG, obligation_id, reference, status,
        period_start, period_end, due_date, internal_target,
        submitted_at, ack_ref, acknowledged_at, closed_at, artifact_ref,
        prep_en, prep_ar, preparer_uid, reviewer_uid, approver_uid,
        NOW, NOW,
    )
    counts["regreport_filing"] += 1
    return int(cur.fetchone()[0])


def insert_fevent(cur, fid, occurred_at, event_type, status_after,
                  note_en, note_ar, actor_uid) -> None:
    cur.execute(
        "SELECT id FROM regreporting.regreport_event "
        "WHERE organisation_id=? AND filing_id=? AND occurred_at=? "
        "AND event_type=? AND is_deleted=0",
        ORG, fid, occurred_at, event_type,
    )
    if cur.fetchone():
        return
    cur.execute(
        """INSERT INTO regreporting.regreport_event
            (organisation_id, filing_id, occurred_at, event_type,
             actor_user_id, status_after, note_en, note_ar,
             created_at, updated_at, created_by, updated_by, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0)""",
        ORG, fid, occurred_at, event_type, actor_uid,
        status_after, note_en, note_ar, NOW, NOW,
    )
    counts["regreport_event"] += 1


# ═════════════════════════════════════════════════════════════════════════
#  reginspect — three government-oversight inspections
# ═════════════════════════════════════════════════════════════════════════

def seed_reginspect(cur) -> None:
    print("\n>>> reginspect — government oversight inspections (org=1)")

    # ── (1) DGA digital-government compliance & Qiyas — CLOSED full cycle ──
    ref = "DGA-OSI-2025-001"
    if insp_id(cur, ref) is None:
        i = insert_inspection(
            cur, ref, "OTHER",
            "FY2025 Digital Government Authority — digital-government compliance & Qiyas maturity review",
            "تقييم هيئة الحكومة الرقمية 2025 — الالتزام بالحكومة الرقمية ومؤشر النضج (قياس)",
            "CLOSED",
            datetime(2025, 9, 1, 9, 0), date(2025, 9, 22), date(2025, 11, 6),
            datetime(2025, 12, 18, 13, 0),
            "فريق هيئة الحكومة الرقمية (الإشراف على النضج الرقمي)",
            "Digital Government Authority compliance review: e-services maturity, Nafath/Tawakkalna "
            "integration, Government Service Bus (GSB) API onboarding, open-data publishing, and the "
            "Qiyas digital-maturity index.",
            "مراجعة الالتزام لدى هيئة الحكومة الرقمية: نضج الخدمات الإلكترونية، التكامل مع نفاذ وتوكلنا، "
            "الربط مع ناقل الخدمات الحكومي عبر واجهات برمجة التطبيقات، نشر البيانات المفتوحة، ومؤشر النضج "
            "الرقمي (قياس).",
            "Closed with 3 observations — (1) two priority e-services not yet fully integrated with the "
            "Government Service Bus, (2) open-data publishing cadence below target on two datasets, (3) a "
            "small set of services missing Nafath single-sign-on. Qiyas digital-maturity score improved "
            "to 78%. All responses accepted; corrective actions tracked.",
            "أُغلق التقييم بثلاث ملاحظات — (1) خدمتان إلكترونيتان ذات أولوية لم تكتمل ربطهما بناقل الخدمات "
            "الحكومي، (2) وتيرة نشر بياناتٍ مفتوحة دون المستهدف في مجموعتي بيانات، (3) عددٌ محدود من الخدمات "
            "يفتقر إلى الدخول الموحّد عبر نفاذ. ارتفع مؤشر النضج الرقمي (قياس) إلى 78%. قُبلت جميع الردود؛ "
            "وتُتابَع الإجراءات التصحيحية.",
            coordinator_uid=3, sponsor_uid=1,   # compliance coordinates, President office sponsors
            open_dr=0, overdue_dr=0, submitted_dr=5,
        )
        drs = [
            ("DGA e-services catalogue + integration status",
             "كتالوج الخدمات الإلكترونية وحالة التكامل",
             "Full catalogue of the Authority's digital services with channel and GSB integration status.",
             "كتالوج كامل لخدمات الهيئة الرقمية مع حالة القناة والربط بناقل الخدمات الحكومي.",
             "EVID/DGA-2025/DR-001", 3),
            ("Nafath / Tawakkalna single-sign-on integration attestation",
             "إقرار التكامل مع نفاذ وتوكلنا للدخول الموحّد",
             "Attestation of Nafath SSO coverage across customer-facing services.",
             "إقرار تغطية الدخول الموحّد عبر نفاذ في الخدمات الموجَّهة للمستفيدين.",
             "EVID/DGA-2025/DR-002", 5),
            ("Government Service Bus (GSB) onboarding certificate",
             "شهادة الربط مع ناقل الخدمات الحكومي",
             "GSB API onboarding certificate and list of exposed/consumed APIs.",
             "شهادة الربط مع ناقل الخدمات الحكومي وقائمة واجهات البرمجة المنشورة والمستهلكة.",
             "EVID/DGA-2025/DR-003", 5),
            ("Open-data publishing log on the national open-data portal",
             "سجل نشر البيانات المفتوحة على البوابة الوطنية للبيانات المفتوحة",
             "Open-data publishing log with dataset count, update cadence, and licence.",
             "سجل نشر البيانات المفتوحة مع عدد المجموعات ووتيرة التحديث والترخيص.",
             "EVID/DGA-2025/DR-004", 5),
            ("Digital-maturity (Qiyas) self-assessment workbook",
             "ملف التقييم الذاتي للنضج الرقمي (قياس)",
             "Completed Qiyas digital-maturity self-assessment with supporting evidence.",
             "ملف التقييم الذاتي للنضج الرقمي (قياس) مكتملاً مع الأدلة المساندة.",
             "EVID/DGA-2025/DR-005", 3),
        ]
        dga_req0 = datetime(2025, 9, 25, 10, 0)
        for n, (en, ar, den, dar, ev, uid) in enumerate(drs, start=1):
            req_at = dga_req0 + timedelta(days=n)
            insert_dr(cur, i, f"{ref}-DR-{n:03d}", en, ar, den, dar, "SUBMITTED",
                      req_at, (req_at + timedelta(days=7)).date(),
                      req_at + timedelta(days=5), uid, ev)
        for occ, ut, st, en, ar, uid in [
            (datetime(2025, 9, 1, 9, 0), "STATUS_CHANGED", "PLANNED",
             "Workspace opened on DGA notification of the annual digital-government review.",
             "افتتاح ورشة العمل بناءً على إشعار هيئة الحكومة الرقمية بالمراجعة السنوية للحكومة الرقمية.", 3),
            (datetime(2025, 9, 22, 9, 30), "STATUS_CHANGED", "ENTRY_MEETING",
             "Entry meeting held — scope and Qiyas evidence list confirmed.",
             "عقد اجتماع الافتتاح — تأكيد النطاق وقائمة أدلة مؤشر قياس.", 3),
            (datetime(2025, 9, 25, 11, 0), "DOCUMENT_REQUEST", "FIELDWORK",
             "Fieldwork started — 5 document requests issued.",
             "بدء العمل الميداني — إصدار 5 طلبات وثائق.", 3),
            (datetime(2025, 11, 6, 11, 0), "STATUS_CHANGED", "EXIT_MEETING",
             "Exit meeting — 3 provisional observations discussed.",
             "اجتماع الإغلاق — مناقشة 3 ملاحظات مبدئية.", 3),
            (datetime(2025, 11, 10, 16, 0), "STATUS_CHANGED", "RESPONSE_DRAFTING",
             "Response drafting started with the responsible departments.",
             "بدء صياغة الردود مع الإدارات المعنية.", 3),
            (datetime(2025, 12, 18, 13, 0), "CLOSURE", "CLOSED",
             "Inspection closed — Qiyas score 78%; all responses accepted; corrective actions tracked.",
             "أُغلق التقييم — مؤشر قياس 78%؛ قُبلت جميع الردود؛ وتُتابَع الإجراءات التصحيحية.", 1),
        ]:
            insert_timeline(cur, i, occ, ut, st, en, ar, uid)
        print(f"  + {ref}  DGA digital-government compliance (CLOSED)")

    # ── (2) NCA Essential Cybersecurity Controls (ECC-2) audit — FIELDWORK ──
    ref = "NCA-ECC-2026-001"
    if insp_id(cur, ref) is None:
        i = insert_inspection(
            cur, ref, "NCA",
            "FY2026 NCA Essential Cybersecurity Controls (ECC-2) compliance audit",
            "تدقيق الالتزام بالضوابط الأساسية للأمن السيبراني (ECC-2) — الهيئة الوطنية للأمن السيبراني 2026",
            "FIELDWORK",
            datetime(2026, 4, 5, 9, 0), date(2026, 4, 26), None, None,
            "فريق الهيئة الوطنية للأمن السيبراني",
            "NCA Essential Cybersecurity Controls (ECC-2) compliance audit of the Authority's systems — "
            "cybersecurity governance, asset management, identity & access management, data protection, "
            "and cybersecurity incident management.",
            "تدقيق الالتزام بالضوابط الأساسية للأمن السيبراني (ECC-2) لأنظمة الهيئة — حوكمة الأمن السيبراني، "
            "إدارة الأصول، إدارة الهوية والصلاحيات، حماية البيانات، وإدارة حوادث الأمن السيبراني.",
            None, None,
            coordinator_uid=3, sponsor_uid=1,
            open_dr=3, overdue_dr=1, submitted_dr=2,
        )
        # mixed states + one past-due OPEN
        drs = [
            ("SUBMITTED", "ECC governance — cybersecurity strategy & steering-committee charter",
             "حوكمة الضوابط — استراتيجية الأمن السيبراني وميثاق اللجنة الإشرافية",
             "Approved cybersecurity strategy and the cybersecurity steering-committee charter (ECC 1-1, 1-2).",
             "استراتيجية الأمن السيبراني المعتمدة وميثاق اللجنة الإشرافية للأمن السيبراني (الضابط 1-1 و1-2).",
             8, 5, "EVID/NCA-ECC-2026/DR-001", 3),
            ("SUBMITTED", "Asset inventory + classification register",
             "سجل حصر الأصول وتصنيفها",
             "Information-asset inventory with classification and ownership (ECC 1-4, 2-1).",
             "حصر أصول المعلومات مع التصنيف والملكية (الضابط 1-4 و2-1).",
             7, 5, "EVID/NCA-ECC-2026/DR-002", 5),
            ("IN_PROGRESS", "Identity & access management — access-recertification evidence",
             "إدارة الهوية والصلاحيات — أدلة إعادة اعتماد الصلاحيات",
             "Privileged- and standard-access recertification cycle evidence (ECC 2-2).",
             "أدلة دورة إعادة اعتماد الصلاحيات للحسابات المميّزة والعادية (الضابط 2-2).",
             6, 9, None, 5),
            ("OPEN", "Data-classification & protection policy + DLP coverage",
             "سياسة تصنيف البيانات وحمايتها وتغطية منع تسرّب البيانات",
             "Data-classification policy and data-leakage-prevention coverage report (ECC 2-7).",
             "سياسة تصنيف البيانات وتقرير تغطية منع تسرّب البيانات (الضابط 2-7).",
             4, 11, None, 3),
            ("OPEN", "Cybersecurity incident-response runbook + recent drill evidence",
             "دليل الاستجابة لحوادث الأمن السيبراني وأدلة آخر تمرين",
             "Incident-response plan, classification matrix, and last tabletop-drill record (ECC 2-13).",
             "خطة الاستجابة للحوادث ومصفوفة التصنيف وسجل آخر تمرين محاكاة (الضابط 2-13).",
             10, -4, None, 5),  # past-due OPEN
        ]
        for n, (status, en, ar, den, dar, days_ago, due_in, ev, uid) in enumerate(drs, start=1):
            req_at = NOW - timedelta(days=days_ago)
            due_d = TODAY + timedelta(days=due_in)
            sub_at = req_at + timedelta(days=max(days_ago - 2, 1)) if status == "SUBMITTED" else None
            insert_dr(cur, i, f"{ref}-DR-{n:03d}", en, ar, den, dar, status,
                      req_at, due_d, sub_at, uid, ev)
        for occ, ut, st, en, ar, uid in [
            (datetime(2026, 4, 5, 9, 0), "STATUS_CHANGED", "PLANNED",
             "Workspace opened on NCA notification of the ECC-2 compliance audit.",
             "افتتاح ورشة العمل بناءً على إشعار الهيئة الوطنية للأمن السيبراني بتدقيق الالتزام بالضوابط الأساسية.", 3),
            (datetime(2026, 4, 26, 9, 30), "STATUS_CHANGED", "ENTRY_MEETING",
             "Entry meeting held — scope across ECC domains 1-5 confirmed.",
             "عقد اجتماع الافتتاح — تأكيد النطاق عبر مجالات الضوابط الأساسية من 1 إلى 5.", 3),
            (datetime(2026, 4, 29, 11, 0), "DOCUMENT_REQUEST", "FIELDWORK",
             "Fieldwork in progress — 5 evidence requests issued across ECC domains.",
             "العمل الميداني جارٍ — إصدار 5 طلبات أدلة عبر مجالات الضوابط الأساسية.", 3),
        ]:
            insert_timeline(cur, i, occ, ut, st, en, ar, uid)
        print(f"  + {ref}  NCA ECC-2 cyber audit (FIELDWORK)")

    # ── (3) Nazaha integrity & anti-corruption review — ENTRY_MEETING ──
    ref = "NZH-INT-2026-001"
    if insp_id(cur, ref) is None:
        i = insert_inspection(
            cur, ref, "OTHER",
            "FY2026 Oversight & Anti-Corruption Authority (Nazaha) — integrity & anti-corruption review",
            "مراجعة هيئة الرقابة ومكافحة الفساد (نزاهة) 2026 — النزاهة ومكافحة الفساد",
            "ENTRY_MEETING",
            datetime(2026, 5, 18, 9, 0), date(2026, 6, 9), None, None,
            "فريق هيئة الرقابة ومكافحة الفساد (نزاهة)",
            "Nazaha integrity & anti-corruption review: conflict-of-interest declarations, procurement "
            "integrity controls, the whistleblowing channel, and code-of-conduct attestations.",
            "مراجعة النزاهة ومكافحة الفساد من هيئة الرقابة ومكافحة الفساد (نزاهة): إقرارات تعارض المصالح، "
            "ضوابط نزاهة المشتريات، قناة الإبلاغ عن المخالفات، وإقرارات ميثاق السلوك الوظيفي.",
            None, None,
            coordinator_uid=3, sponsor_uid=4,   # compliance coordinates, auditor sponsors
            open_dr=4, overdue_dr=0, submitted_dr=0,
        )
        drs = [
            ("Conflict-of-interest declarations register",
             "سجل إقرارات تعارض المصالح",
             "Register of conflict-of-interest declarations for leadership and procurement staff.",
             "سجل إقرارات تعارض المصالح للقيادات وموظفي المشتريات.",
             "EVID/NZH-2026/DR-001", 3),
            ("Procurement-integrity controls + Etimad compliance evidence",
             "ضوابط نزاهة المشتريات وأدلة الالتزام بمنصة اعتماد",
             "Procurement-integrity controls, segregation of duties, and Etimad-platform compliance.",
             "ضوابط نزاهة المشتريات والفصل بين المهام والالتزام بمنصة اعتماد.",
             "EVID/NZH-2026/DR-002", 3),
            ("Whistleblowing-channel statistics + case-handling SLAs",
             "إحصاءات قناة الإبلاغ عن المخالفات واتفاقيات مستوى معالجة البلاغات",
             "Whistleblowing-channel intake statistics and case-handling service levels.",
             "إحصاءات استقبال البلاغات في قناة الإبلاغ ومستويات خدمة معالجة الحالات.",
             "EVID/NZH-2026/DR-003", 2),
            ("Code-of-conduct attestation completion report",
             "تقرير اكتمال إقرارات ميثاق السلوك الوظيفي",
             "Annual code-of-conduct attestation completion across all employees.",
             "اكتمال الإقرار السنوي بميثاق السلوك الوظيفي لجميع الموظفين.",
             "EVID/NZH-2026/DR-004", 3),
        ]
        for n, (en, ar, den, dar, ev, uid) in enumerate(drs, start=1):
            insert_dr(cur, i, f"{ref}-DR-{n:03d}", en, ar, den, dar, "OPEN",
                      NOW - timedelta(days=3), TODAY + timedelta(days=10 + n),
                      None, uid, ev)
        for occ, ut, st, en, ar, uid in [
            (datetime(2026, 5, 18, 9, 0), "STATUS_CHANGED", "PLANNED",
             "Workspace opened on Nazaha notification of the integrity review.",
             "افتتاح ورشة العمل بناءً على إشعار نزاهة بمراجعة النزاهة.", 3),
            (datetime(2026, 6, 9, 9, 30), "STATUS_CHANGED", "ENTRY_MEETING",
             "Entry meeting held — scope confirmed; 4 evidence requests issued.",
             "عقد اجتماع الافتتاح — تأكيد النطاق وإصدار 4 طلبات أدلة.", 3),
        ]:
            insert_timeline(cur, i, occ, ut, st, en, ar, uid)
        print(f"  + {ref}  Nazaha integrity review (ENTRY_MEETING)")


# ═════════════════════════════════════════════════════════════════════════
#  regreporting — six government periodic obligations + filings + events
# ═════════════════════════════════════════════════════════════════════════

def seed_regreporting(cur) -> None:
    print("\n>>> regreporting — government periodic returns (org=1)")

    fy = TODAY.year - 1            # prior full year
    cur_fy = TODAY.year
    fy_start, fy_end = date(fy, 1, 1), date(fy, 12, 31)

    # ── (1) DGA Digital Government Maturity (Qiyas) — ANNUAL ──
    o = insert_obligation(
        cur, "DGA-DMR", "OTHER", "ANNUAL",
        "DGA Annual Digital Government Maturity Return (Qiyas)",
        "التقرير السنوي لنضج الحكومة الرقمية (قياس) — هيئة الحكومة الرقمية",
        45, "DIGITAL_TRANSFORMATION_OFFICER", "STRATEGY_OFFICER",
        "Digital Government Authority — Digital Government Maturity (Qiyas) framework",
        "منصة قياس — هيئة الحكومة الرقمية",
        "Annual digital-government maturity self-assessment submitted via the Qiyas platform: e-services "
        "maturity, channel adoption, GSB integration, and open-data publishing.",
        "تقييم ذاتي سنوي لنضج الحكومة الرقمية يُقدَّم عبر منصة قياس: نضج الخدمات الإلكترونية، تبنّي القنوات، "
        "الربط بناقل الخدمات الحكومي، ونشر البيانات المفتوحة.",
        reminder_ladder="30,14,7,3,1",
    )
    fid = insert_filing(
        cur, o, f"DGA-DMR-{fy}", "CLOSED", fy_start, fy_end, fy_end + timedelta(days=63),
        preparer_uid=3, reviewer_uid=2, approver_uid=1,
        submitted_at=datetime(fy + 1, 2, 20, 11, 0),
        ack_ref=f"DGA-QIYAS-{fy}-04417",
        acknowledged_at=datetime(fy + 1, 3, 4, 10, 0),
        closed_at=datetime(fy + 1, 3, 18, 9, 0),
        artifact_ref=f"EVID/dga/qiyas/{fy}/maturity-return.pdf",
        prep_en="Qiyas digital-maturity score 78% (up from 71%); two priority e-services flagged for GSB integration.",
        prep_ar="مؤشر النضج الرقمي (قياس) 78% (مقابل 71%)؛ تم رصد خدمتين ذات أولوية للربط بناقل الخدمات الحكومي.",
    )
    if counts["regreport_filing"] and fid:
        insert_fevent(cur, fid, datetime(fy + 1, 2, 1, 9, 0), "STATUS_CHANGED", "IN_PREPARATION",
                      f"Qiyas {fy} return preparation opened.", f"فتح إعداد تقرير قياس لعام {fy}.", 3)
        insert_fevent(cur, fid, datetime(fy + 1, 2, 20, 11, 0), "SUBMITTED", "SUBMITTED",
                      "Submitted via the Qiyas platform.", "تم الإيداع عبر منصة قياس.", 3)
        insert_fevent(cur, fid, datetime(fy + 1, 3, 4, 10, 0), "ACK_RECEIVED", "ACKNOWLEDGED",
                      "DGA acknowledged the maturity return.", "أقرّت هيئة الحكومة الرقمية استلام التقرير.", 1)
    fid2 = insert_filing(
        cur, o, f"DGA-DMR-{cur_fy}", "IN_PREPARATION",
        date(cur_fy, 1, 1), date(cur_fy, 12, 31), date(cur_fy, 12, 31) + timedelta(days=63),
        preparer_uid=3, reviewer_uid=2, approver_uid=1,
        prep_en="Evidence collection in progress across digital deputyship.",
        prep_ar="جمع الأدلة جارٍ على مستوى وكالة التحول الرقمي.",
    )
    if fid2:
        insert_fevent(cur, fid2, TS(10, 9), "STATUS_CHANGED", "IN_PREPARATION",
                      f"Qiyas {cur_fy} cycle opened.", f"فتح دورة قياس لعام {cur_fy}.", 3)

    # ── (2) NDMO national data-management maturity (DMM) — SEMIANNUAL ──
    o = insert_obligation(
        cur, "NDMO-DMM", "OTHER", "SEMIANNUAL",
        "NDMO Data-Management & Governance Maturity Self-Assessment (DMM)",
        "التقييم الذاتي لنضج إدارة وحوكمة البيانات (DMM) — مكتب إدارة البيانات الوطنية",
        30, "DATA_MANAGEMENT_OFFICER", "DIGITAL_TRANSFORMATION_OFFICER",
        "National Data Management Office (NDMO, under SDAIA) — Data Management & Governance maturity (15 domains)",
        "بوابة مكتب إدارة البيانات الوطنية",
        "Semiannual self-assessment against the NDMO data-management maturity model (15 domains incl. data "
        "governance, quality, classification, and personal-data protection).",
        "تقييم ذاتي نصف سنوي وفق نموذج نضج إدارة البيانات لمكتب إدارة البيانات الوطنية (15 مجالاً تشمل حوكمة "
        "البيانات والجودة والتصنيف وحماية البيانات الشخصية).",
        reminder_ladder="21,14,7,1",
    )
    fid = insert_filing(
        cur, o, f"NDMO-DMM-{fy}-H2", "CLOSED",
        date(fy, 7, 1), date(fy, 12, 31), date(fy + 1, 1, 31) + timedelta(days=14),
        preparer_uid=3, reviewer_uid=2, approver_uid=1,
        submitted_at=datetime(fy + 1, 2, 5, 14, 0),
        ack_ref=f"NDMO-DMM-{fy}-H2-0331",
        acknowledged_at=datetime(fy + 1, 2, 12, 10, 0),
        closed_at=datetime(fy + 1, 2, 20, 9, 0),
        prep_en="Overall DMM maturity level 3 (Defined); data-quality domain remains the priority gap.",
        prep_ar="مستوى نضج إدارة البيانات الإجمالي 3 (مُعرَّف)؛ يبقى مجال جودة البيانات الفجوة ذات الأولوية.",
    )
    if fid:
        insert_fevent(cur, fid, datetime(fy + 1, 1, 20, 9, 0), "STATUS_CHANGED", "IN_PREPARATION",
                      f"H2-{fy} DMM self-assessment opened.", f"فتح التقييم الذاتي لإدارة البيانات للنصف الثاني {fy}.", 3)
        insert_fevent(cur, fid, datetime(fy + 1, 2, 5, 14, 0), "SUBMITTED", "SUBMITTED",
                      "Submitted via the NDMO portal.", "تم الإيداع عبر بوابة مكتب إدارة البيانات الوطنية.", 3)
        insert_fevent(cur, fid, datetime(fy + 1, 2, 12, 10, 0), "ACK_RECEIVED", "ACKNOWLEDGED",
                      "NDMO acknowledged the self-assessment.", "أقرّ مكتب إدارة البيانات الوطنية استلام التقييم.", 1)
    fid2 = insert_filing(
        cur, o, f"NDMO-DMM-{cur_fy}-H1", "IN_REVIEW",
        date(cur_fy, 1, 1), date(cur_fy, 6, 30), date(cur_fy, 7, 31) + timedelta(days=14),
        preparer_uid=3, reviewer_uid=2, approver_uid=1,
        prep_en="Draft complete; under review by the data-governance office before sign-off.",
        prep_ar="المسودة مكتملة؛ قيد المراجعة من مكتب حوكمة البيانات قبل الاعتماد.",
    )
    if fid2:
        insert_fevent(cur, fid2, TS(6, 9), "STATUS_CHANGED", "IN_REVIEW",
                      f"H1-{cur_fy} DMM draft moved to review.", f"نقل مسودة النصف الأول {cur_fy} إلى المراجعة.", 2)

    # ── (3) NCEMA business-continuity readiness — ANNUAL ──
    o = insert_obligation(
        cur, "NCEMA-BCR", "NCEMA", "ANNUAL",
        "NCEMA Annual Business-Continuity Readiness Return",
        "التقرير السنوي لجاهزية استمرارية الأعمال — المركز الوطني لإدارة الكوارث والأزمات",
        45, "BCM_MANAGER", "STRATEGY_OFFICER",
        "National Center for Risk & Crisis Management (NCEMA) — business-continuity readiness (NCEMA 7000)",
        "بوابة المركز الوطني لإدارة الكوارث والأزمات",
        "Annual business-continuity readiness return per NCEMA 7000: BIA currency, continuity-plan coverage, "
        "and exercise/recovery-test results.",
        "تقرير سنوي لجاهزية استمرارية الأعمال وفق معيار المركز 7000: حداثة تحليل الأثر، تغطية خطط الاستمرارية، "
        "ونتائج التمارين واختبارات التعافي.",
        reminder_ladder="30,14,7,3,1",
    )
    fid = insert_filing(
        cur, o, f"NCEMA-BCR-{cur_fy}", "SUBMITTED",
        date(cur_fy, 1, 1), date(cur_fy, 12, 31), date(cur_fy, 3, 31) + timedelta(days=63),
        preparer_uid=2, reviewer_uid=3, approver_uid=1,
        submitted_at=TS(12, 11),
        prep_en="BIA refreshed for all critical services; two recovery tests executed; awaiting NCEMA acknowledgement.",
        prep_ar="تحديث تحليل الأثر لجميع الخدمات الحرجة؛ تنفيذ اختباري تعافٍ؛ بانتظار إقرار المركز الوطني.",
    )
    if fid:
        insert_fevent(cur, fid, TS(25, 9), "STATUS_CHANGED", "IN_PREPARATION",
                      f"NCEMA {cur_fy} readiness return opened.", f"فتح تقرير الجاهزية لعام {cur_fy}.", 2)
        insert_fevent(cur, fid, TS(12, 11), "SUBMITTED", "SUBMITTED",
                      "Submitted via the NCEMA portal; awaiting acknowledgement.",
                      "تم الإيداع عبر بوابة المركز الوطني؛ بانتظار الإقرار.", 2)

    # ── (4) Nazaha national integrity-plan progress — ANNUAL ──
    o = insert_obligation(
        cur, "NZH-IPR", "OTHER", "ANNUAL",
        "Nazaha Annual Integrity & Anti-Corruption Plan Progress Return",
        "التقرير السنوي لتقدّم خطة النزاهة ومكافحة الفساد — نزاهة",
        45, "INTEGRITY_OFFICER", "STRATEGY_OFFICER",
        "Oversight & Anti-Corruption Authority (Nazaha) — National Integrity & Anti-Corruption Strategy progress",
        "بوابة هيئة الرقابة ومكافحة الفساد (نزاهة)",
        "Annual progress return on the entity's integrity & anti-corruption plan aligned with the National "
        "Strategy: COI controls, whistleblowing, awareness, and procurement integrity.",
        "تقرير سنوي عن تقدّم خطة النزاهة ومكافحة الفساد للجهة بما يتوافق مع الاستراتيجية الوطنية: ضوابط تعارض "
        "المصالح، الإبلاغ عن المخالفات، التوعية، ونزاهة المشتريات.",
        reminder_ladder="30,14,7,1",
    )
    fid = insert_filing(
        cur, o, f"NZH-IPR-{cur_fy}", "SCHEDULED",
        date(cur_fy, 1, 1), date(cur_fy, 12, 31), date(cur_fy, 12, 31) + timedelta(days=63),
        preparer_uid=3, reviewer_uid=4, approver_uid=1,
    )
    if fid:
        insert_fevent(cur, fid, TS(2, 9), "STATUS_CHANGED", "SCHEDULED",
                      f"Nazaha integrity-plan {cur_fy} return scheduled.",
                      f"جدولة تقرير خطة النزاهة لعام {cur_fy}.", 3)

    # ── (5) MoF / Etimad budget-execution & procurement-plan — QUARTERLY ──
    o = insert_obligation(
        cur, "MOF-BEX", "OTHER", "QUARTERLY",
        "MoF Quarterly Budget-Execution & Procurement-Plan Return (Etimad)",
        "التقرير الربع‑سنوي لتنفيذ الميزانية وخطة المشتريات (اعتماد) — وزارة المالية",
        20, "CFO", "STRATEGY_OFFICER",
        "Ministry of Finance — quarterly budget-execution reporting via the Etimad platform",
        "منصة اعتماد — وزارة المالية",
        "Quarterly budget-execution return (commitments vs. budget, disbursement rate) and procurement-plan "
        "progress lodged via Etimad.",
        "تقرير ربع‑سنوي لتنفيذ الميزانية (الارتباطات مقابل المعتمد ونسبة الصرف) وتقدّم خطة المشتريات يُقدَّم "
        "عبر منصة اعتماد.",
    )
    last_q_end = date(TODAY.year, ((TODAY.month - 1) // 3) * 3 + 1, 1) - timedelta(days=1)
    last_q_start = date(last_q_end.year, ((last_q_end.month - 1) // 3) * 3 + 1, 1)
    last_q_no = ((last_q_end.month - 1) // 3) + 1
    fid = insert_filing(
        cur, o, f"MOF-BEX-{last_q_end.year}-Q{last_q_no}", "APPROVED",
        last_q_start, last_q_end, last_q_end + timedelta(days=28),
        preparer_uid=3, reviewer_uid=2, approver_uid=1,
        prep_en="Disbursement rate 23% of annual budget for the quarter; ready for President-office sign-off on Etimad.",
        prep_ar="نسبة الصرف 23% من الميزانية السنوية خلال الربع؛ جاهز لاعتماد مكتب الرئيس عبر منصة اعتماد.",
    )
    if fid:
        insert_fevent(cur, fid, TS(8, 9), "STATUS_CHANGED", "APPROVED",
                      "Budget-execution return approved internally; pending Etimad lodgement.",
                      "اعتُمد تقرير تنفيذ الميزانية داخلياً؛ بانتظار الإيداع عبر اعتماد.", 1)
    cur_q_start = date(TODAY.year, ((TODAY.month - 1) // 3) * 3 + 1, 1)
    cur_q_no = ((cur_q_start.month - 1) // 3) + 1
    # quarter end = start month + 2, last day
    em = cur_q_start.month + 2
    cur_q_end = (date(cur_q_start.year, em + 1, 1) - timedelta(days=1)) if em < 12 \
        else date(cur_q_start.year, 12, 31)
    fid2 = insert_filing(
        cur, o, f"MOF-BEX-{cur_q_start.year}-Q{cur_q_no}", "IN_PREPARATION",
        cur_q_start, cur_q_end, cur_q_end + timedelta(days=28),
        preparer_uid=3, reviewer_uid=2, approver_uid=1,
        prep_en="Quarter in progress; commitments and disbursements being reconciled.",
        prep_ar="الربع جارٍ؛ تتم تسوية الارتباطات والمصروفات.",
    )
    if fid2:
        insert_fevent(cur, fid2, TS(4, 9), "STATUS_CHANGED", "IN_PREPARATION",
                      f"Q{cur_q_no}-{cur_q_start.year} budget-execution return opened.",
                      f"فتح تقرير تنفيذ الميزانية للربع {cur_q_no} لعام {cur_q_start.year}.", 3)

    # ── (6) Vision-2030 VRO strategic-KPI alignment — QUARTERLY ──
    o = insert_obligation(
        cur, "VRO-KPI", "OTHER", "QUARTERLY",
        "Vision 2030 VRO Quarterly Strategic-KPI Alignment Return",
        "التقرير الربع‑سنوي لمواءمة مؤشرات الأداء الاستراتيجية — مكتب تحقيق الرؤية",
        15, "STRATEGY_OFFICER", "DIGITAL_TRANSFORMATION_OFFICER",
        "Vision Realization Office (VRO) — quarterly strategic-KPI and Vision-2030 alignment reporting",
        "بوابة مكتب تحقيق الرؤية",
        "Quarterly strategic-KPI return reporting the Authority's performance against its Vision-2030-aligned "
        "objectives and initiative milestones.",
        "تقرير ربع‑سنوي لمؤشرات الأداء الاستراتيجية يعرض أداء الهيئة مقابل أهدافها المواءمة لرؤية 2030 "
        "ومحطات المبادرات.",
    )
    fid = insert_filing(
        cur, o, f"VRO-KPI-{last_q_end.year}-Q{last_q_no}", "CLOSED",
        last_q_start, last_q_end, last_q_end + timedelta(days=21),
        preparer_uid=2, reviewer_uid=3, approver_uid=1,
        submitted_at=datetime.combine(last_q_end + timedelta(days=12), datetime.min.time().replace(hour=11)),
        ack_ref=f"VRO-{last_q_end.year}-Q{last_q_no}-1182",
        acknowledged_at=datetime.combine(last_q_end + timedelta(days=15), datetime.min.time().replace(hour=10)),
        closed_at=datetime.combine(last_q_end + timedelta(days=21), datetime.min.time().replace(hour=9)),
        prep_en="11 of 14 strategic KPIs on or above target; 3 initiatives flagged amber with recovery plans.",
        prep_ar="11 من 14 مؤشراً استراتيجياً على المستهدف أو أعلى؛ 3 مبادرات بحالة تحذير مع خطط تعافٍ.",
    )
    if fid:
        insert_fevent(cur, fid, datetime.combine(last_q_end + timedelta(days=5), datetime.min.time().replace(hour=9)),
                      "STATUS_CHANGED", "IN_PREPARATION",
                      f"VRO Q{last_q_no} KPI return preparation opened.",
                      f"فتح إعداد تقرير مؤشرات مكتب تحقيق الرؤية للربع {last_q_no}.", 2)
        insert_fevent(cur, fid, datetime.combine(last_q_end + timedelta(days=12), datetime.min.time().replace(hour=11)),
                      "SUBMITTED", "SUBMITTED",
                      "Submitted to the Vision Realization Office.", "تم الإيداع لدى مكتب تحقيق الرؤية.", 2)
        insert_fevent(cur, fid, datetime.combine(last_q_end + timedelta(days=15), datetime.min.time().replace(hour=10)),
                      "ACK_RECEIVED", "ACKNOWLEDGED",
                      "VRO acknowledged the KPI return.", "أقرّ مكتب تحقيق الرؤية استلام التقرير.", 1)
    fid2 = insert_filing(
        cur, o, f"VRO-KPI-{cur_q_start.year}-Q{cur_q_no}", "SCHEDULED",
        cur_q_start, cur_q_end, cur_q_end + timedelta(days=21),
        preparer_uid=2, reviewer_uid=3, approver_uid=1,
    )
    if fid2:
        insert_fevent(cur, fid2, TS(1, 9), "STATUS_CHANGED", "SCHEDULED",
                      f"VRO Q{cur_q_no}-{cur_q_start.year} KPI return scheduled.",
                      f"جدولة تقرير مؤشرات مكتب تحقيق الرؤية للربع {cur_q_no} لعام {cur_q_start.year}.", 2)


# ═════════════════════════════════════════════════════════════════════════
#  openbanking + sharia — SKIPPED (banking-only). Logged, never touched.
# ═════════════════════════════════════════════════════════════════════════

SKIPPED = [
    ("openbanking.ob_tpp", "Open Banking TPP register (AISP/PISP) — SAMA Open-Banking-Framework "
     "construct for licensed ASPSP banks; a public authority onboards no TPPs."),
    ("openbanking.ob_consent", "AIS/PIS customer consents — meaningful only for a bank holding retail "
     "accounts; inapplicable to a public authority."),
    ("openbanking.ob_api_call", "Open-Banking API-gateway telemetry — no Open Banking API surface at a "
     "non-bank authority."),
    ("openbanking.ob_incident", "Open-Banking-specific incidents — authority runs no Open Banking service; "
     "operational incidents belong in the cyber/incident modules."),
    ("openbanking.ob_event", "Audit log subordinate to the Open Banking entities — nothing to log."),
    ("sharia.sharia_scholar", "Shariah Supervisory Board members — governance organ of an Islamic bank; a "
     "secular government authority has none."),
    ("sharia.sharia_product_approval", "Shariah approvals of Islamic financial products — authority issues "
     "no Islamic financial products."),
    ("sharia.sharia_resolution", "Fatwas / Shariah-board resolutions — no Shariah board exists."),
    ("sharia.sharia_audit_finding", "Shariah-audit findings of an Islamic FI — inapplicable; authority audit "
     "findings live in the audit modules."),
    ("sharia.sharia_nc_event", "Shariah non-compliance events (income purification) — meaningless for a "
     "non-Islamic-finance body."),
    ("sharia.sharia_event", "Audit log subordinate to the Shariah entities — nothing to record."),
]


# ═════════════════════════════════════════════════════════════════════════

def verify(cur) -> None:
    print("\n--- Verification (organisation_id = 1) ---")
    for tbl, label in [
        ("reginspect.reginspect_inspection", "inspections"),
        ("reginspect.reginspect_document_request", "document requests"),
        ("reginspect.reginspect_inspection_update", "inspection updates"),
        ("regreporting.regreport_obligation", "obligations"),
        ("regreporting.regreport_filing", "filings"),
        ("regreporting.regreport_event", "events"),
    ]:
        cur.execute(f"SELECT COUNT(*) FROM {tbl} WHERE organisation_id=? AND is_deleted=0", ORG)
        print(f"   {label:<20} total org-1 rows: {cur.fetchone()[0]}")

    print("\n   Government inspections seeded by this script:")
    cur.execute(
        "SELECT external_reference, regulator, status FROM reginspect.reginspect_inspection "
        "WHERE organisation_id=? AND is_deleted=0 AND external_reference IN "
        "('DGA-OSI-2025-001','NCA-ECC-2026-001','NZH-INT-2026-001') ORDER BY external_reference",
        ORG,
    )
    for r in cur.fetchall():
        print(f"     {r[0]:<20} [{r[1]}] {r[2]}")

    print("\n   Government obligations seeded by this script:")
    cur.execute(
        "SELECT code, regulator, frequency FROM regreporting.regreport_obligation "
        "WHERE organisation_id=? AND is_deleted=0 AND code IN "
        "('DGA-DMR','NDMO-DMM','NCEMA-BCR','NZH-IPR','MOF-BEX','VRO-KPI') ORDER BY code",
        ORG,
    )
    for r in cur.fetchall():
        print(f"     {r[0]:<12} [{r[1]}] {r[2]}")


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    print(">>> seed_gov_regaffairs — Saudi government regulatory-affairs depth (org=1)")
    print("    schemas: reginspect, regreporting  (openbanking, sharia SKIPPED — banking-only)")

    seed_reginspect(cur)
    seed_regreporting(cur)

    print("\n>>> SKIPPED tables (banking-only, logged per safety constraint):")
    for tbl, reason in SKIPPED:
        print(f"   - {tbl}: {reason}")

    total = sum(counts.values())
    print("\n--- Inserted this run ---")
    for k, v in counts.items():
        print(f"   {k:<32} +{v}")
    print(f"   {'TOTAL':<32} +{total}")

    verify(cur)
    conn.close()
    print("\n✓ done")


if __name__ == "__main__":
    main()
