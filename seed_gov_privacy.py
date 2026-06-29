# -*- coding: utf-8 -*-
"""Seed Saudi-government-themed PRIVACY-domain demo data for organisation_id = 1.

The default tenant (org 1) is a Saudi General Authority (هيئة عامة) headed by a
President (رئيس الهيئة). The existing privacy/dataclass/consumer seed data is
BANK-themed (T24, SAMA, SIMAH ...). This script ADDS a government PDPL / NDMO
layer on top — it never clobbers existing bank rows. Every INSERT is guarded by a
natural key (code / external_id / reference / unique tuple) so a second run
inserts 0.

Domain  : privacy
Schemas : privacy, privacy_lc, dataclass  (consumer.* intentionally skipped — SAMA
          banking consumer-protection module, not appropriate for a general authority)

Ownership convention (mirrors seed_gov_committees.py): NONE of these tables have a
department_id / committee_id column and there are no FKs to platform.*/admin.*.
Ownership of "PRIVACY dept, COM-PRIVACY committee" is therefore expressed in
CONTENT — *_user_id columns -> iam.users (3=compliance acts as DPO, 5=ranalyst as
assessor, 1=admin), and free-text owner/dpo/reviewer fields carry the Privacy
Office / Personal Data Protection Committee (COM-PRIVACY) strings.

Regulators woven into content: PDPL + Implementing Regulations (SDAIA), NDMO
4-level classification (TOP_SECRET/CONFIDENTIAL/INTERNAL/PUBLIC), NCEMA, DGA
(Government Service Bus), Nazaha.

HARD SAFETY: INSERT / idempotent-UPSERT only. No DELETE/TRUNCATE/DROP/ALTER.
org 1 only. Only the assigned schemas are touched.

    Usage:  python seed_gov_privacy.py
"""
from __future__ import annotations

import sys
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
        except Exception as exc:  # noqa: BLE001
            last = exc
    raise SystemExit(f"Could not connect to SQL Server: {last}")


ORG = 1
# iam.users (org1): 1=admin, 2=rmanager, 3=compliance(DPO), 4=auditor, 5=ranalyst, 6=viewer
DPO = 3        # acts as Data Protection Officer
ASSESSOR = 5   # ranalyst
ADMIN = 1

DPO_NAME = "Data Protection Officer — Privacy Office / مكتب حماية البيانات"
PRIV_OFFICE = "Privacy Office / مكتب الخصوصية"
DMO = "Data Management Office (DATA_MGMT) / مكتب إدارة البيانات"

summary: dict[str, list[int]] = {}


def rec(tbl: str, ins: int = 1) -> None:
    summary.setdefault(tbl, [0, 0])
    summary[tbl][0] += ins


def exists(cur, sql: str, *params) -> bool:
    cur.execute(sql, *params)
    return cur.fetchone() is not None


# ───────────────────────── privacy.processing_activities (ROPA) ─────────────
def seed_processing_activities(cur):
    tbl = "privacy.processing_activities"
    rows = [
        ("SDO-2026-ROPA-101", "Beneficiary & applicant register for licensing and services",
         "سجل المستفيدين ومقدمي الطلبات للتراخيص والخدمات",
         "Maintain identity, contact and eligibility data of beneficiaries and applicants to deliver the Authority's licensing and public services.",
         "PUBLIC_TASK", 120, 0, "IDENTITY,CONTACT,ELIGIBILITY"),
        ("SDO-2026-ROPA-102", "National identity verification via NAFATH/Absher",
         "التحقق من الهوية الوطنية عبر نفاذ وأبشر",
         "Verify the national identity of users for single sign-on and service authorisation through NAFATH/Absher integration.",
         "LEGAL_OBLIGATION", 24, 0, "NATIONAL_ID,IDENTITY"),
        ("SDO-2026-ROPA-103", "Employee HR and payroll (GOSI / Mudad)",
         "الموارد البشرية والرواتب للموظفين (التأمينات/مدد)",
         "Process employee personal, contractual and payroll data including GOSI and Mudad wage-protection reporting.",
         "LEGAL_OBLIGATION", 120, 0, "EMPLOYEE,FINANCIAL,CONTACT"),
        ("SDO-2026-ROPA-104", "Inspection and field-visit case files",
         "ملفات التفتيش والزيارات الميدانية",
         "Record inspection cases, field-visit findings and related personal data of inspected parties and witnesses.",
         "PUBLIC_TASK", 84, 0, "IDENTITY,GEOLOCATION,CASE"),
        ("SDO-2026-ROPA-105", "Grievances and beneficiary correspondence",
         "التظلمات ومراسلات المستفيدين",
         "Handle grievances, enquiries and correspondence submitted by beneficiaries and the public.",
         "PUBLIC_TASK", 60, 0, "IDENTITY,CONTACT,CASE"),
        ("SDO-2026-ROPA-106", "Authority website and digital-service analytics",
         "تحليلات الموقع والخدمات الرقمية للهيئة",
         "Collect website and e-service usage analytics with user consent to improve digital services.",
         "CONSENT", 26, 0, "USAGE,DEVICE,COOKIES"),
        ("SDO-2026-ROPA-107", "Government data exchange via DGA Government Service Bus",
         "تبادل البيانات الحكومي عبر ناقل الخدمات الحكومي",
         "Exchange beneficiary and service data with other government entities through the DGA Government Service Bus for integrated services.",
         "LEGAL_OBLIGATION", 60, 0, "IDENTITY,SERVICE"),
        ("SDO-2026-ROPA-108", "AI and analytics on de-identified service data",
         "تحليلات الذكاء الاصطناعي على بيانات مجهّلة الهوية",
         "Run analytics and AI models over de-identified service data to support policy and service-design decisions.",
         "PUBLIC_TASK", 36, 0, "DEIDENTIFIED,STATISTICAL"),
    ]
    for ref, en, ar, purpose, basis, ret, special, cat in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND data_office_reference=?", ORG, ref):
            continue
        cur.execute(
            f"""INSERT INTO {tbl}
                (external_id, organisation_id, activity_name_en, activity_name_ar, purpose_en,
                 legal_basis, retention_period_months, cross_border_transfer, status,
                 is_deleted, data_office_reference, special_category_flag, data_category)
                VALUES (NEWID(), ?, ?, ?, ?, ?, ?, 0, 'ACTIVE', 0, ?, ?, ?)""",
            ORG, en, ar, purpose, basis, ret, ref, special, cat)
        rec(tbl)


def ropa_id(cur, ref):
    cur.execute("SELECT id FROM privacy.processing_activities WHERE organisation_id=? AND data_office_reference=?", ORG, ref)
    r = cur.fetchone()
    return r[0] if r else None


# ───────────────────────── privacy.data_subject_requests ────────────────────
def seed_dsr(cur):
    tbl = "privacy.data_subject_requests"
    rows = [
        ("ACCESS", "محمد بن عبدالله القحطاني", "m.alqahtani.dsr1@example.sa", 40, "RESOLVED", DPO, True),
        ("RECTIFICATION", "نورة بنت سعد الدوسري", "n.aldosari.dsr2@example.sa", 22, "IN_PROGRESS", ASSESSOR, False),
        ("ERASURE", "خالد بن فهد العتيبي", "k.alotaibi.dsr3@example.sa", 18, "REJECTED", DPO, False),
        ("PORTABILITY", "سارة بنت ناصر الشمري", "s.alshammari.dsr4@example.sa", 35, "RESOLVED", DPO, True),
        ("OBJECT", "عبدالعزيز بن محمد الغامدي", "a.alghamdi.dsr5@example.sa", 12, "ESCALATED", DPO, False),
        ("RESTRICT", "ريم بنت عبدالرحمن الزهراني", "r.alzahrani.dsr6@example.sa", 9, "IN_PROGRESS", ASSESSOR, False),
        ("ACCESS", "فهد بن سلطان الحربي", "f.alharbi.dsr7@example.sa", 28, "RESOLVED", DPO, True),
        ("RECTIFICATION", "العنود بنت تركي المطيري", "a.almutairi.dsr8@example.sa", 5, "NEW", ASSESSOR, False),
        ("ERASURE", "ماجد بن عيد البقمي", "m.albqami.dsr9@example.sa", 31, "RESOLVED", DPO, True),
        ("ACCESS", "هند بنت خالد العنزي", "h.alanazi.dsr10@example.sa", 15, "IN_PROGRESS", ASSESSOR, False),
    ]
    for rtype, name, email, ago, status, assigned, done in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND requester_email=? AND request_type=?", ORG, email, rtype):
            continue
        cur.execute(
            f"""INSERT INTO {tbl}
                (external_id, organisation_id, request_type, requester_name, requester_email,
                 received_at, due_date, status, assigned_to, completed_at, is_deleted)
                VALUES (NEWID(), ?, ?, ?, ?, DATEADD(day, ?, SYSUTCDATETIME()),
                        DATEADD(day, 30, CAST(DATEADD(day, ?, SYSUTCDATETIME()) AS date)),
                        ?, ?, ?, 0)""",
            ORG, rtype, name, email, -ago, -ago, status, assigned,
            (None if not done else _completed_marker()))
        rec(tbl)


def _completed_marker():
    # pyodbc cannot bind a SQL expr; use a recent timestamp string instead.
    import datetime
    return (datetime.datetime.utcnow() - datetime.timedelta(days=2)).strftime("%Y-%m-%d %H:%M:%S")


# ───────────────────────── privacy.dpia ─────────────────────────────────────
def seed_dpia(cur):
    tbl = "privacy.dpia"
    rows = [
        ("NAFATH", "DPIA — NAFATH/Absher identity-verification integration",
         "تقييم أثر — تكامل التحقق من الهوية عبر نفاذ/أبشر", "NEW_PROCESSING", "APPROVED", "LOW",
         "SDO-2026-ROPA-102",
         "Processing is necessary and proportionate for statutory identity verification; residual risk LOW with KSA-resident data and minimisation. Approved by the Personal Data Protection Committee (COM-PRIVACY). / المعالجة ضرورية ومتناسبة للتحقق النظامي من الهوية؛ المخاطر المتبقية منخفضة. اعتُمد من لجنة حماية البيانات الشخصية.",
         60, True),
        ("AI_ELIG", "DPIA — AI eligibility-scoring for beneficiary services",
         "تقييم أثر — التقييم الآلي لاستحقاق المستفيدين بالذكاء الاصطناعي", "AUTOMATED_DECISION", "UNDER_REVIEW", "MEDIUM",
         "SDO-2026-ROPA-108",
         "Automated scoring carries a MEDIUM residual risk; a human-in-the-loop review gate and SDAIA consultation are required before go-live. / ينطوي التقييم الآلي على مخاطر متوسطة؛ يلزم مراجعة بشرية والتنسيق مع سدايا قبل التشغيل.",
         20, False),
        ("DGA_EXCH", "DPIA — Cross-government data exchange via DGA Service Bus",
         "تقييم أثر — تبادل البيانات الحكومي عبر ناقل الخدمات", "LARGE_SCALE", "APPROVED", "LOW",
         "SDO-2026-ROPA-107",
         "Large-scale exchange limited to need-to-know fields over the DGA bus within the Kingdom; residual risk LOW. Approved. / تبادل واسع مقصور على الحقول اللازمة عبر الناقل داخل المملكة؛ المخاطر منخفضة. معتمد.",
         45, True),
        ("INSPECT", "DPIA — Field-inspection mobile data capture (geolocation)",
         "تقييم أثر — التقاط بيانات التفتيش الميداني عبر الجوال (الموقع الجغرافي)", "SPECIAL_CATEGORY", "DRAFT", "MEDIUM",
         "SDO-2026-ROPA-104",
         "Geolocation capture requires geofencing and a tight retention limit; assessment in draft pending mitigations. / يتطلب التقاط الموقع تحديد النطاق وتقليص الاحتفاظ؛ التقييم مسودة بانتظار إجراءات التخفيف.",
         8, False),
    ]
    for key, en, ar, trig, status, residual, ropa_ref, conc, ago, approved in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND title_en=?", ORG, en):
            continue
        pa = ropa_id(cur, ropa_ref)
        approved_at = _completed_marker() if approved else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (external_id, organisation_id, processing_activity_id, title_en, title_ar,
                 trigger_reason, status, overall_residual_risk, dpo_id, assessor_id,
                 assessment_date, next_review_date, approved_at, conclusion, is_deleted, updated_at)
                VALUES (NEWID(), ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        CAST(DATEADD(day, ?, SYSUTCDATETIME()) AS date),
                        DATEADD(year, 1, CAST(DATEADD(day, ?, SYSUTCDATETIME()) AS date)),
                        ?, ?, 0, SYSUTCDATETIME())""",
            ORG, pa, en, ar, trig, status, residual, DPO, ASSESSOR, -ago, -ago, approved_at, conc)
        rec(tbl)


def get_dpia_targets(cur):
    """DPIAs needing question/mitigation/review backfill: any org1 DPIA with 0 questions."""
    cur.execute("SELECT id FROM privacy.dpia WHERE organisation_id=?", ORG)
    all_ids = [r[0] for r in cur.fetchall()]
    targets = []
    for did in all_ids:
        cur.execute("SELECT COUNT(*) FROM privacy.dpia_question WHERE dpia_id=?", did)
        if cur.fetchone()[0] == 0:
            targets.append(did)
    return targets


def seed_dpia_questions(cur, dpia_ids):
    tbl = "privacy.dpia_question"
    Q = [
        (1, "Q1-NECESS", "Is the processing necessary and proportionate to its purpose?",
         "هل المعالجة ضرورية ومتناسبة مع غرضها؟",
         "Yes — limited to the statutory public-task purpose. / نعم — مقصورة على المهمة العامة النظامية.", "LOW"),
        (2, "Q2-BASIS", "What is the lawful basis under PDPL for the processing?",
         "ما الأساس النظامي للمعالجة وفق نظام حماية البيانات الشخصية؟",
         "Public task / legal obligation (PDPL Art.5-6). / المهمة العامة/الالتزام النظامي (المادتان 5-6).", "LOW"),
        (3, "Q3-MINIM", "Has data minimisation been applied?",
         "هل تم تطبيق مبدأ تقليل البيانات؟",
         "Only need-to-know fields are processed. / تُعالَج الحقول اللازمة فقط.", "LOW"),
        (4, "Q4-SPECIAL", "Are special-category data involved and how are they handled?",
         "هل تتضمن المعالجة بيانات من الفئات الخاصة وكيف تُعالَج؟",
         "Where present, additional safeguards and access controls apply. / عند وجودها تُطبَّق ضوابط إضافية.", "MEDIUM"),
        (5, "Q5-RIGHTS", "Are data-subject rights enabled (access, rectification, objection)?",
         "هل حقوق أصحاب البيانات مُفعّلة (الاطلاع والتصحيح والاعتراض)؟",
         "Yes — handled via the DSR workflow. / نعم — عبر مسار طلبات أصحاب البيانات.", "LOW"),
        (6, "Q6-XBORDER", "Is there cross-border transfer or processor exposure?",
         "هل يوجد نقل خارج المملكة أو تعرّض لمعالجين؟",
         "Data is KSA-resident; processors bound by DPAs. / البيانات داخل المملكة والمعالجون مقيّدون باتفاقيات.", "MEDIUM"),
        (7, "Q7-SECURITY", "Are appropriate security measures in place?",
         "هل توجد تدابير أمنية ملائمة؟",
         "Encryption, access control and logging applied. / التشفير والتحكم في الوصول والتسجيل.", "LOW"),
    ]
    for did in dpia_ids:
        for so, code, qen, qar, ans, risk in Q:
            if exists(cur, f"SELECT 1 FROM {tbl} WHERE dpia_id=? AND question_code=?", did, code):
                continue
            cur.execute(
                f"""INSERT INTO {tbl} (dpia_id, question_code, question_en, question_ar, answer, risk_level, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""",
                did, code, qen, qar, ans, risk, so)
            rec(tbl)


def seed_dpia_mitigations(cur, dpia_ids):
    tbl = "privacy.dpia_mitigation"
    M = [
        (1, "De-identify data before AI training and analytics",
         "إزالة الهوية عن البيانات قبل تدريب الذكاء الاصطناعي والتحليلات", "IN_PROGRESS"),
        (2, "Human-in-the-loop review gate for automated decisions",
         "بوابة مراجعة بشرية للقرارات الآلية", "PLANNED"),
        (3, "Data Processing Agreement with KSA-residency clause for all processors",
         "اتفاقية معالجة بيانات تتضمن شرط الإقامة داخل المملكة لجميع المعالجين", "DONE"),
        (4, "Geofencing and 90-day retention limit on inspection geolocation",
         "تحديد النطاق الجغرافي وحد احتفاظ 90 يوماً لبيانات موقع التفتيش", "PLANNED"),
    ]
    for did in dpia_ids:
        count = 2 if did % 2 else 4
        for so, men, mar, status in M[:count]:
            if exists(cur, f"SELECT 1 FROM {tbl} WHERE dpia_id=? AND sort_order=?", did, so):
                continue
            cur.execute(
                f"""INSERT INTO {tbl} (dpia_id, mitigation_en, mitigation_ar, status, owner_id, target_date, sort_order)
                    VALUES (?, ?, ?, ?, ?, DATEADD(day, 60, CAST(SYSUTCDATETIME() AS date)), ?)""",
                did, men, mar, status, DPO, so)
            rec(tbl)


def seed_dpia_reviews(cur, dpia_ids):
    tbl = "privacy.dpia_review"
    rname = "Data Protection Officer — Privacy Office (COM-PRIVACY)"
    for i, did in enumerate(dpia_ids):
        decision = "CHANGES_REQUESTED" if i == 0 else "APPROVED"
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE dpia_id=? AND reviewer_name=?", did, rname):
            continue
        comments = ("Approved by the Personal Data Protection Committee (COM-PRIVACY). / اعتُمد من لجنة حماية البيانات الشخصية."
                    if decision == "APPROVED"
                    else "Changes requested: add mitigations before approval. / مطلوب تعديلات: إضافة إجراءات تخفيف قبل الاعتماد.")
        cur.execute(
            f"""INSERT INTO {tbl} (dpia_id, reviewer_id, reviewer_name, decision, comments, decided_at)
                VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME())""",
            did, DPO, rname, decision, comments)
        rec(tbl)


# ───────────────────────── privacy.data_breaches ────────────────────────────
def seed_breaches(cur):
    tbl = "privacy.data_breaches"
    rows = [
        ("Misdirected email exposing beneficiary list",
         "إفشاء قائمة مستفيدين عبر بريد إلكتروني أُرسل لمستلم خاطئ — Misdirected email exposing a beneficiary list to a wrong recipient.",
         "CONFIDENTIALITY", 320, 1, "CONTAINED", "REPORTED", "SDAIA-BR-2026-011"),
        ("Lost unencrypted USB drive with inspection records",
         "فقدان وسيط تخزين USB غير مشفّر يحتوي سجلات تفتيش — Lost unencrypted USB drive containing inspection records.",
         "CONFIDENTIALITY", 85, 1, "INVESTIGATING", "REPORTED", "SDAIA-BR-2026-014"),
        ("Web-portal misconfiguration exposing application attachments",
         "سوء تهيئة بوابة إلكترونية أدى لكشف مرفقات الطلبات — Portal misconfiguration exposed application attachments.",
         "CONFIDENTIALITY", 1200, 1, "CLOSED", "REPORTED", "SDAIA-BR-2026-007"),
        ("Cloud processor sub-processor security incident",
         "حادث أمني لدى معالج فرعي تابع لمزوّد سحابي — Security incident at a cloud processor's sub-processor.",
         "CONFIDENTIALITY", 40, 1, "INVESTIGATING", "REPORTED", "SDAIA-BR-2026-019"),
        ("Insider unauthorized access to HR records",
         "وصول داخلي غير مصرّح إلى سجلات الموارد البشرية — Insider unauthorized access to HR records.",
         "INTEGRITY", 15, 0, "CONTAINED", "NOT_REPORTED", None),
    ]
    for en, desc, btype, aff, req, status, sdaia, ref in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND title_en=?", ORG, en):
            continue
        notified_at = _completed_marker() if req else None
        submitted_at = _completed_marker() if sdaia == "REPORTED" else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (external_id, organisation_id, title_en, description_en, breach_type,
                 discovered_at, data_subjects_affected_count, pdpc_notification_required,
                 pdpc_notified_at, status, is_deleted, sdaia_notification_status,
                 sdaia_submission_ref, sdaia_submitted_at)
                VALUES (NEWID(), ?, ?, ?, ?, DATEADD(day, -20, SYSUTCDATETIME()), ?, ?,
                        ?, ?, 0, ?, ?, ?)""",
            ORG, en, desc, btype, aff, req, notified_at, status, sdaia, ref, submitted_at)
        rec(tbl)


# ───────────────────────── privacy.cross_border_transfers ───────────────────
def seed_cbt(cur):
    tbl = "privacy.cross_border_transfers"
    rows = [
        ("EXT-CBT-101", "SDO-2026-ROPA-108", "BHR", "GCC Cloud DR Region",
         "DEIDENTIFIED,SERVICE", "ADEQUACY", "APPROVED", "APPROVED",
         "Disaster-recovery replica to a GCC adequacy region; de-identified service data only.",
         "نسخة تعافٍ من الكوارث إلى منطقة خليجية ذات قرار كفاية؛ بيانات خدمة مجهّلة فقط.", None),
        ("EXT-CBT-102", "SDO-2026-ROPA-103", "USA", "SaaS Support (HR/Payroll Vendor)",
         "EMPLOYEE,CONTACT", "SCC", "ASSESSED", "ASSESSED",
         "Time-boxed remote support access by a SaaS vendor under standard contractual clauses with a transfer impact assessment.",
         "وصول دعم عن بُعد محدد المدة من مزوّد برمجي وفق الشروط التعاقدية المعيارية مع تقييم أثر النقل.", None),
        ("EXT-CBT-103", "SDO-2026-ROPA-105", "GBR", "International Conference Organiser",
         "IDENTITY,CONTACT", "EXPLICIT_CONSENT", "APPROVED", "APPROVED",
         "Transfer of attendee data for an international conference with explicit consent.",
         "نقل بيانات المشاركين لمؤتمر دولي بموافقة صريحة.", None),
        ("EXT-CBT-104", "SDO-2026-ROPA-107", "ARE", "Regional Analytics Provider",
         "DEIDENTIFIED", "CONTRACT_NECESSITY", "REQUESTED", "PENDING",
         "Requested transfer pending SDAIA residency check; outcome PENDING.",
         "نقل مطلوب بانتظار فحص الإقامة من سدايا؛ النتيجة قيد المراجعة.", None),
        ("EXT-CBT-105", "SDO-2026-ROPA-101", "IND", "Non-Adequate Offshore Vendor",
         "IDENTITY,CONTACT", "SCC", "REJECTED", "ASSESSED",
         "Proposed transfer to a non-adequate destination without sufficient safeguards.",
         "نقل مقترح إلى وجهة غير كافية دون ضمانات كافية.",
         "Destination is non-adequate and no acceptable safeguard or residency guarantee was provided. / الوجهة غير كافية ولم تُقدَّم ضمانات أو ضمان إقامة مقبول."),
    ]
    for ext, ropa, dest, org_name, cat, mech, status, residency, jen, jar, rej in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND external_id=?", ORG, ext):
            continue
        approved_at = _completed_marker() if status == "APPROVED" else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, ropa_code, ropa_title_en, source_country, destination_country,
                 destination_org_name, destination_org_role, data_categories, data_subjects_count_est,
                 transfer_mechanism, safeguards_en, safeguards_ar, justification_en, justification_ar,
                 residency_check_at, residency_check_by, residency_check_outcome, status,
                 rejection_reason, approved_at, approved_by, approver_name, external_id,
                 is_deleted, is_active, created_by, updated_by, row_version)
                VALUES (?, ?, ?, N'SAU', ?, ?, 'PROCESSOR', ?, ?, ?,
                        N'KSA-residency first; DPA and encryption in transit.',
                        N'الإقامة داخل المملكة أولاً؛ اتفاقية معالجة وتشفير أثناء النقل.',
                        ?, ?, SYSUTCDATETIME(), ?, ?, ?, ?, ?, ?, ?,
                        0, 1, ?, ?, 0)""",
            ORG, ropa, "ROPA " + ropa, dest, org_name, cat, 100, mech,
            jen, jar, DPO_NAME, residency, status, rej, approved_at, DPO,
            "Data Protection Officer (COM-PRIVACY)", ext, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── privacy.data_sharing_agreements ──────────────────
def seed_dsa(cur):
    tbl = "privacy.data_sharing_agreements"
    rows = [
        ("DSA-2026-101", "National data exchange with SDAIA / National Information Center",
         "تبادل بيانات وطني مع سدايا/مركز المعلومات الوطني", "GOVERNMENT",
         "SDAIA — National Information Center / مركز المعلومات الوطني",
         "IDENTITY,SERVICE", "Beneficiaries / المستفيدون",
         "Exchange of reference data with the National Information Center for integrated government services.",
         "تبادل البيانات المرجعية مع مركز المعلومات الوطني للخدمات الحكومية المتكاملة.",
         "LEGAL_OBLIGATION", "ACTIVE", "SDO-2026-ROPA-107"),
        ("DSA-2026-102", "Identity verification with Ministry of Interior (Absher)",
         "التحقق من الهوية مع وزارة الداخلية (أبشر)", "GOVERNMENT",
         "Ministry of Interior — Absher / وزارة الداخلية - أبشر",
         "NATIONAL_ID,IDENTITY", "Beneficiaries & applicants / المستفيدون ومقدمو الطلبات",
         "Identity verification of beneficiaries through Absher.",
         "التحقق من هوية المستفيدين عبر أبشر.",
         "LEGAL_OBLIGATION", "ACTIVE", "SDO-2026-ROPA-102"),
        ("DSA-2026-103", "Beneficiary eligibility with GOSI / HRSD",
         "استحقاق المستفيدين مع التأمينات/الموارد البشرية", "GOVERNMENT",
         "GOSI & HRSD / التأمينات الاجتماعية والموارد البشرية",
         "EMPLOYMENT,ELIGIBILITY", "Beneficiaries / المستفيدون",
         "Eligibility verification using employment and social-insurance data.",
         "التحقق من الاستحقاق باستخدام بيانات التوظيف والتأمينات.",
         "PUBLIC_TASK", "ACTIVE", "SDO-2026-ROPA-101"),
        ("DSA-2026-104", "Tax and compliance data exchange with ZATCA",
         "تبادل بيانات الزكاة والضريبة مع هيئة الزكاة والضريبة والجمارك", "GOVERNMENT",
         "ZATCA / هيئة الزكاة والضريبة والجمارك",
         "IDENTITY,FINANCIAL", "Licensed entities / الجهات المرخّصة",
         "Exchange of relevant tax/compliance data for licensed entities.",
         "تبادل بيانات الزكاة والالتزام للجهات المرخّصة.",
         "LEGAL_OBLIGATION", "DRAFT", "SDO-2026-ROPA-101"),
        ("DSA-2026-105", "Health-linked service exchange with MoH / Sehhaty",
         "تبادل بيانات خدمة صحية مع وزارة الصحة/صحتي", "GOVERNMENT",
         "Ministry of Health — Sehhaty / وزارة الصحة - صحتي",
         "HEALTH,IDENTITY", "Beneficiaries / المستفيدون",
         "Exchange of health-linked attributes for eligible services with safeguards.",
         "تبادل سمات صحية للخدمات المؤهّلة مع الضمانات.",
         "PUBLIC_TASK", "DRAFT", "SDO-2026-ROPA-101"),
        ("DSA-2026-106", "Inter-authority exchange via DGA Government Service Bus",
         "تبادل بين الجهات عبر ناقل الخدمات الحكومي", "GOVERNMENT",
         "Partner Authority via DGA Bus / جهة شريكة عبر الناقل الحكومي",
         "IDENTITY,SERVICE", "Beneficiaries / المستفيدون",
         "Integrated service delivery via the DGA Government Service Bus.",
         "تقديم خدمات متكاملة عبر ناقل الخدمات الحكومي.",
         "LEGAL_OBLIGATION", "EXPIRED", "SDO-2026-ROPA-107"),
    ]
    for code, en, ar, rtype, rname, cat, subj, pen, par, lawful, status, ropa in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND agreement_code=?", ORG, code):
            continue
        import datetime
        now = datetime.date.today()
        eff_from = eff_to = None
        if status in ("ACTIVE", "EXPIRED"):
            eff_from = (now - datetime.timedelta(days=180)).isoformat()
        if status == "EXPIRED":
            eff_to = (now - datetime.timedelta(days=30)).isoformat()
        elif status == "ACTIVE":
            eff_to = (now + datetime.timedelta(days=730)).isoformat()
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, external_id, agreement_code, title_en, title_ar, recipient_type,
                 recipient_name, recipient_country, recipient_legal_role, data_categories, data_subjects,
                 purpose_en, purpose_ar, lawful_basis, cross_border_mechanism, subject_notified,
                 linked_ropa_codes, effective_from, effective_to, status, signer_name,
                 is_deleted, is_active, created_by, updated_by, row_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, N'SAU', 'CONTROLLER', ?, ?, ?, ?, ?, N'NONE', 1,
                        ?, ?, ?, ?, ?, 0, 1, ?, ?, 0)""",
            ORG, "EXT-" + code, code, en, ar, rtype, rname, cat, subj, pen, par, lawful,
            ropa, eff_from, eff_to, status, DPO_NAME, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── privacy.privacy_notices ──────────────────────────
def seed_notices(cur):
    tbl = "privacy.privacy_notices"
    rows = [
        ("PN-2026-101", "Website Privacy Notice", "إشعار خصوصية الموقع الإلكتروني",
         "Explain how the Authority's website processes personal data.",
         "This notice explains what personal data the Authority collects via its website, the lawful basis, retention and your PDPL rights. / يوضّح هذا الإشعار البيانات الشخصية التي تجمعها الهيئة عبر موقعها والأساس النظامي ومدة الاحتفاظ وحقوقك.",
         "PUBLISHED", "SDO-2026-ROPA-106"),
        ("PN-2026-102", "e-Services / Beneficiary Portal Notice", "إشعار خصوصية بوابة الخدمات والمستفيدين",
         "Explain processing within the beneficiary e-services portal.",
         "Describes processing of identity, contact and eligibility data within the e-services portal. / يصف معالجة بيانات الهوية والتواصل والاستحقاق ضمن بوابة الخدمات.",
         "PUBLISHED", "SDO-2026-ROPA-101"),
        ("PN-2026-103", "Job-Applicant / HR Privacy Notice", "إشعار خصوصية المتقدمين للوظائف والموارد البشرية",
         "Explain processing of applicant and employee data.",
         "Covers recruitment and HR processing including GOSI/Mudad. / يغطي معالجة بيانات التوظيف والموارد البشرية بما في ذلك التأمينات ومدد.",
         "PUBLISHED", "SDO-2026-ROPA-103"),
        ("PN-2026-104", "Inspection & Field-Visit Notice", "إشعار التفتيش والزيارات الميدانية",
         "Inform inspected parties about data processed during inspections.",
         "Informs inspected parties of the data captured during field visits and its retention. / يُعلم الأطراف الخاضعة للتفتيش بالبيانات الملتقطة ومدة الاحتفاظ.",
         "PUBLISHED", "SDO-2026-ROPA-104"),
        ("PN-2026-105", "CCTV / Premises Surveillance Notice", "إشعار المراقبة بالكاميرات في المقار",
         "Inform visitors and staff about CCTV processing.",
         "Notifies visitors and staff of CCTV operation, purpose and retention on the premises. / يُعلم الزوار والموظفين بتشغيل كاميرات المراقبة وغرضها ومدة الاحتفاظ.",
         "PUBLISHED", "SDO-2026-ROPA-104"),
        ("PN-2026-106", "Grievance / Contact-Us Notice", "إشعار التظلمات والتواصل",
         "Explain processing of grievance and contact submissions.",
         "Explains how grievance and contact-us submissions are processed and retained. / يوضّح معالجة بلاغات التظلم والتواصل ومدة الاحتفاظ.",
         "DRAFT", "SDO-2026-ROPA-105"),
    ]
    for code, en, ar, pen, body, status, ropa in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND notice_code=?", ORG, code):
            continue
        published_at = _completed_marker() if status == "PUBLISHED" else None
        published_by = DPO if status == "PUBLISHED" else None
        effective_from = _completed_marker() if status == "PUBLISHED" else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, notice_code, version, title_en, title_ar, purpose_en, purpose_ar,
                 dpo_contact, lawful_basis, body_en, body_ar, linked_ropa_codes, status,
                 effective_from, published_at, published_by, external_id,
                 is_deleted, is_active, created_by, updated_by, row_version)
                VALUES (?, ?, 1, ?, ?, ?, ?, N'dpo@authority.gov.sa / مكتب حماية البيانات',
                        N'PUBLIC_TASK', ?, ?, ?, ?, ?, ?, ?, ?,
                        0, 1, ?, ?, 0)""",
            ORG, code, en, ar, pen, pen, body, body, ropa, status,
            effective_from, published_at, published_by, "EXT-" + code, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── privacy.processor_assessments ────────────────────
def seed_processor_assessments(cur):
    tbl = "privacy.processor_assessments"
    rows = [
        ("PRA-2026-101", "In-Kingdom Cloud Hosting Provider / مزوّد استضافة سحابية داخل المملكة",
         "Hosting of beneficiary and service data in a KSA region.", "INITIAL", "SATISFACTORY", "COMPLETED"),
        ("PRA-2026-102", "HR / Payroll SaaS Vendor / مزوّد نظام موارد بشرية ورواتب",
         "Processing of employee HR and payroll data.", "PERIODIC", "CONDITIONAL", "COMPLETED"),
        ("PRA-2026-103", "Contact-Centre / CRM Provider / مزوّد مركز اتصال وإدارة علاقات",
         "Handling of beneficiary enquiries and contact data.", "INITIAL", "SATISFACTORY", "COMPLETED"),
        ("PRA-2026-104", "Document-Management Vendor / مزوّد إدارة وثائق",
         "Storage and processing of case and application documents.", "PERIODIC", "CONDITIONAL", "IN_PROGRESS"),
        ("PRA-2026-105", "Analytics Vendor / مزوّد تحليلات",
         "Analytics over de-identified service data.", "INITIAL", "UNSATISFACTORY", "COMPLETED"),
        ("PRA-2026-106", "Identity-Verification Integrator / مكامل التحقق من الهوية",
         "Integration with NAFATH/Absher identity verification.", "RENEWAL", "SATISFACTORY", "COMPLETED"),
    ]
    for code, proc, scope, atype, outcome, status in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND assessment_code=?", ORG, code):
            continue
        cscore = "MET" if outcome == "SATISFACTORY" else ("PARTIAL" if outcome == "CONDITIONAL" else "NOT_MET")
        risk = 2.0 if outcome == "SATISFACTORY" else (3.5 if outcome == "CONDITIONAL" else 4.5)
        assessed_at = _completed_marker() if status == "COMPLETED" else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, external_id, assessment_code, processor_name, processor_country,
                 processing_scope_en, processing_scope_ar, assessment_type,
                 c1_written_instructions, c2_confidentiality, c3_security_measures, c4_sub_processor_auth,
                 c5_breach_notification, c6_dsr_assistance, c7_deletion_or_return, c8_audit_rights,
                 findings_en, findings_ar, recommendations_en, recommendations_ar, overall_outcome,
                 risk_score, next_assessment_due, assessed_at, assessed_by, assessor_name, status,
                 is_deleted, is_active, created_by, updated_by, row_version)
                VALUES (?, ?, ?, ?, N'SAU', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        N'Assessment of the eight PDPL Art.10 processor clauses; KSA-residency emphasised.',
                        N'تقييم بنود المعالج الثمانية وفق المادة 10؛ مع التأكيد على الإقامة داخل المملكة.',
                        N'Maintain DPA, enforce KSA residency and breach-notification SLAs.',
                        N'الإبقاء على اتفاقية المعالجة وفرض الإقامة داخل المملكة واتفاقيات الإبلاغ عن الانتهاكات.',
                        ?, ?, DATEADD(year, 1, CAST(SYSUTCDATETIME() AS date)), ?, ?, ?, ?,
                        0, 1, ?, ?, 0)""",
            ORG, "EXT-" + code, code, proc, scope, scope, atype,
            cscore, cscore, cscore, cscore, cscore, cscore, cscore, cscore,
            outcome, risk, assessed_at, ASSESSOR, PRIV_OFFICE, status, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── privacy.subprocessor ─────────────────────────────
def seed_subprocessor(cur):
    tbl = "privacy.subprocessor"
    rows = [
        ("SUBP-101", "In-Kingdom Cloud Datacentre", "مركز بيانات سحابي داخل المملكة", "SA",
         "Primary hosting of authority personal data.", "الاستضافة الأساسية للبيانات الشخصية للهيئة.",
         "PUBLIC_INTEREST", "GENERAL", "APPROVED", "HIGH", None),
        ("SUBP-102", "CDN / Edge (KSA POP)", "شبكة توصيل محتوى/حافة (نقطة داخل المملكة)", "SA",
         "Content delivery and edge caching for e-services.", "توصيل المحتوى والتخزين المؤقت للخدمات الإلكترونية.",
         "PUBLIC_INTEREST", "GENERAL", "APPROVED", "MEDIUM", None),
        ("SUBP-103", "Email-Delivery Service", "خدمة إرسال البريد الإلكتروني", "SA",
         "Transactional email delivery to beneficiaries.", "إرسال رسائل البريد المعاملاتية للمستفيدين.",
         "CONTRACT", "SPECIFIC", "APPROVED", "LOW", None),
        ("SUBP-104", "SMS / OTP Gateway", "بوابة الرسائل النصية ورموز التحقق", "SA",
         "SMS and one-time-password delivery for authentication.", "إرسال الرسائل النصية ورموز التحقق للمصادقة.",
         "PUBLIC_INTEREST", "SPECIFIC", "UNDER_DPO_REVIEW", "MEDIUM", None),
        ("SUBP-105", "Backup / Storage Provider", "مزوّد النسخ الاحتياطي والتخزين", "SA",
         "Encrypted backup and storage of authority data.", "النسخ الاحتياطي المشفّر وتخزين بيانات الهيئة.",
         "PUBLIC_INTEREST", "GENERAL", "APPROVED", "HIGH", None),
        ("SUBP-106", "Monitoring / Observability Vendor", "مزوّد المراقبة والرصد التشغيلي", "BH",
         "Infrastructure monitoring and observability.", "مراقبة البنية التحتية والرصد التشغيلي.",
         "LEGITIMATE_INTEREST", "SPECIFIC", "SUSPENDED", "LOW", "SCC_PLUS_TIA"),
    ]
    for code, en, ar, country, pen, par, lawful, auth, lifecycle, vol, cb in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND subprocessor_code=?", ORG, code):
            continue
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, external_id, subprocessor_code, sub_processor_legal_name,
                 sub_processor_legal_name_ar, country_of_processing, processing_purpose_en,
                 processing_purpose_ar, lawful_basis, authorisation_type, cross_border_safeguard_type,
                 data_volume_band, has_dpa_in_place, dpa_signed_date, dpa_expiry_date, lifecycle_stage,
                 dpo_approved_by_user_id, dpo_approved_at, is_deleted, created_by, updated_by)
                VALUES (?, NEWID(), ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, 1, CAST(DATEADD(month, -6, SYSUTCDATETIME()) AS date),
                        CAST(DATEADD(year, 1, SYSUTCDATETIME()) AS date), ?, ?, SYSUTCDATETIME(),
                        0, ?, ?)""",
            ORG, code, en, ar, country, pen, par, lawful, auth, cb,
            vol, lifecycle, DPO, ADMIN, ADMIN)
        rec(tbl)


def subp_id(cur, code):
    cur.execute("SELECT id FROM privacy.subprocessor WHERE organisation_id=? AND subprocessor_code=?", ORG, code)
    r = cur.fetchone()
    return r[0] if r else None


def seed_subprocessor_events(cur):
    tbl = "privacy.subprocessor_event"
    rows = [
        ("SUBP-101", "ADDED", "Onboarded as primary in-Kingdom hosting sub-processor.",
         "أُضيف كمعالج فرعي رئيسي للاستضافة داخل المملكة.", 0),
        ("SUBP-104", "REVIEW_COMPLETED", "DPO review completed for SMS/OTP gateway.",
         "اكتملت مراجعة مكتب حماية البيانات لبوابة الرسائل ورموز التحقق.", 0),
        ("SUBP-106", "SUSPENDED", "Suspended pending cross-border safeguard re-assessment.",
         "عُلِّق بانتظار إعادة تقييم ضمانات النقل خارج المملكة.", 0),
        ("SUBP-106", "CROSS_BORDER_CHANGE", "Cross-border processing location changed; TIA refreshed.",
         "تغيّر موقع المعالجة خارج المملكة؛ تحديث تقييم أثر النقل.", 0),
        ("SUBP-103", "INCIDENT_REPORTED", "Minor email-delivery incident reported and contained.",
         "الإبلاغ عن حادث بسيط في إرسال البريد ومعالجته.", 1),
        ("SUBP-102", "ADDED", "Onboarded as CDN/edge sub-processor (KSA POP).",
         "أُضيف كمعالج فرعي لتوصيل المحتوى داخل المملكة.", 0),
    ]
    for code, etype, den, dar, notified in rows:
        sid = subp_id(cur, code)
        if sid is None:
            continue
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND subprocessor_id=? AND event_type=?", ORG, sid, etype):
            continue
        notif_date = _completed_marker()[:10] if notified else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, external_id, subprocessor_id, event_type, event_date,
                 recorded_by_user_id, description_en, description_ar, data_subjects_notified,
                 data_subjects_notification_date, is_deleted, created_by, updated_by)
                VALUES (?, NEWID(), ?, ?, SYSUTCDATETIME(), ?, ?, ?, ?, ?, 0, ?, ?)""",
            ORG, sid, etype, DPO, den, dar, notified, notif_date, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── privacy.disposal_certificates ────────────────────
def seed_disposal(cur):
    tbl = "privacy.disposal_certificates"
    rows = [
        ("EXT-DC-101", "DATA_ASSET", "DA-GOV-003", "Expired paper inspection files past statutory retention.",
         "ملفات تفتيش ورقية منتهية تجاوزت مدة الاحتفاظ النظامية.", "DESTROY", "COMPLETED", "NDMO-RET-101"),
        ("EXT-DC-102", "DATA_ASSET", "DA-GOV-010", "Beneficiary analytics dataset to be anonymised.",
         "مجموعة بيانات تحليلات المستفيدين لإزالة الهوية.", "ANONYMIZE", "VERIFIED", "NDMO-RET-102"),
        ("EXT-DC-103", "ROPA", "SDO-2026-ROPA-106", "Expired marketing/analytics consent records.",
         "سجلات موافقة تسويق/تحليلات منتهية.", "DELETE", "COMPLETED", "NDMO-RET-103"),
        ("EXT-DC-104", "DATA_ASSET", "DA-GOV-007", "Closed grievance records archived to the national archive.",
         "سجلات تظلمات مغلقة محوّلة للأرشيف الوطني.", "ARCHIVE", "SCHEDULED", "NDMO-RET-104"),
        ("EXT-DC-105", "DATA_ASSET", "DA-GOV-001", "Decommissioned backup media destruction.",
         "إتلاف وسائط نسخ احتياطي مسحوبة من الخدمة.", "DESTROY", "SCHEDULED", "NDMO-RET-105"),
        ("EXT-DC-106", "DATA_ASSET", "DA-GOV-004", "Ex-applicant CVs after retention window.",
         "السير الذاتية لمتقدمين سابقين بعد انتهاء مدة الاحتفاظ.", "DELETE", "COMPLETED", "NDMO-RET-106"),
    ]
    for ext, rtype, rid, sen, sar, method, status, rule in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND external_id=?", ORG, ext):
            continue
        done = status in ("COMPLETED", "VERIFIED")
        executed_at = _completed_marker() if done else None
        verified_at = _completed_marker() if status == "VERIFIED" else None
        exec_by = DPO if done else None
        ver_by = DPO if status == "VERIFIED" else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, related_entity_type, related_entity_id, related_entity_label,
                 retention_rule_ref, scope_description_en, scope_description_ar, disposal_method,
                 status, scheduled_date, executed_at, executed_by, executor_name, verified_at,
                 verified_by, verifier_name, owner_role, owner_name, external_id,
                 is_deleted, is_active, created_by, updated_by, row_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, DATEADD(day, -12, SYSUTCDATETIME()),
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?, 0)""",
            ORG, rtype, rid, rtype + " " + rid, rule, sen, sar, method, status,
            executed_at, exec_by, ("Records Management / " + PRIV_OFFICE) if done else None,
            verified_at, ver_by, PRIV_OFFICE if status == "VERIFIED" else None,
            DMO, PRIV_OFFICE, ext, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── privacy.consent_records ──────────────────────────
def seed_consent(cur):
    tbl = "privacy.consent_records"
    rows = [
        ("DS-2026-0001", "SERVICE_NOTIFICATIONS_SMS", "SMS service notifications", "WEB_FORM", "ACTIVE", "ar"),
        ("DS-2026-0002", "SERVICE_NOTIFICATIONS_EMAIL", "Email service notifications", "WEB_FORM", "ACTIVE", "ar"),
        ("DS-2026-0003", "SATISFACTION_SURVEY", "Satisfaction-survey participation", "MOBILE_APP", "ACTIVE", "ar"),
        ("DS-2026-0004", "NEWSLETTER", "Optional newsletter subscription", "WEB_FORM", "ACTIVE", "ar"),
        ("DS-2026-0005", "BETA_ESERVICE", "Beta e-service participation", "MOBILE_APP", "ACTIVE", "ar"),
        ("DS-2026-0006", "RESEARCH_OPTIN", "Research and statistics opt-in", "WEB_FORM", "ACTIVE", "en"),
        ("DS-2026-0007", "EVENT_REGISTRATION", "Event registration consent", "WEB_FORM", "ACTIVE", "ar"),
        ("DS-2026-0008", "NEWSLETTER", "Optional newsletter subscription", "NAFATH", "WITHDRAWN", "ar"),
        ("DS-2026-0009", "SATISFACTION_SURVEY", "Satisfaction-survey participation", "WEB_FORM", "WITHDRAWN", "ar"),
        ("DS-2026-0010", "SERVICE_NOTIFICATIONS_SMS", "SMS service notifications", "MOBILE_APP", "EXPIRED", "ar"),
    ]
    for ref, code, desc, via, status, lang in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND data_subject_ref=? AND purpose_code=?", ORG, ref, code):
            continue
        withdrawn_at = _completed_marker() if status == "WITHDRAWN" else None
        wreason = "Withdrawn by data subject / سحب الموافقة من صاحب البيانات" if status == "WITHDRAWN" else None
        import datetime
        now = datetime.datetime.utcnow()
        valid_until = ((now - datetime.timedelta(days=1)) if status == "EXPIRED"
                       else (now + datetime.timedelta(days=365))).strftime("%Y-%m-%d %H:%M:%S")
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, external_id, data_subject_ref, purpose_code, purpose_description,
                 legal_basis, granted_at, granted_via, valid_until, withdrawn_at, withdrawn_reason,
                 version, language, status, created_by, is_deleted)
                VALUES (?, NEWID(), ?, ?, ?, 'CONSENT', DATEADD(month, -3, SYSUTCDATETIME()), ?,
                        ?, ?, ?, 'v1', ?, ?, ?, 0)""",
            ORG, ref, code, desc, via, valid_until, withdrawn_at, wreason, lang, status, ADMIN)
        rec(tbl)


# ───────────────────────── privacy.privacy_assessments ──────────────────────
def seed_privacy_assessments(cur):
    tbl = "privacy.privacy_assessments"
    rows = [
        ("PIA — New beneficiary e-service", "تقييم خصوصية — خدمة إلكترونية جديدة للمستفيدين", "PIA", "MEDIUM", "COMPLETED"),
        ("TIA — SaaS support transfer", "تقييم أثر نقل — وصول دعم برمجي", "TIA", "MEDIUM", "COMPLETED"),
        ("LIA — Fraud/eligibility analytics", "تقييم مصلحة مشروعة — تحليلات الاحتيال والاستحقاق", "LIA", "LOW", "DRAFT"),
        ("PIA — CCTV upgrade", "تقييم خصوصية — تحديث كاميرات المراقبة", "PIA", "LOW", "COMPLETED"),
        ("PIA — Mobile inspection app", "تقييم خصوصية — تطبيق التفتيش الميداني", "PIA", "MEDIUM", "DRAFT"),
    ]
    for title, title_ar, atype, risk, status in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND title=?", ORG, title):
            continue
        cur.execute(
            f"""INSERT INTO {tbl}
                (external_id, organisation_id, assessment_type, title, title_ar, risk_level,
                 owner_name, assessment_date, status, is_deleted)
                VALUES (NEWID(), ?, ?, ?, ?, ?, ?, CAST(SYSUTCDATETIME() AS date), ?, 0)""",
            ORG, atype, title, title_ar, risk, "Privacy Office — DPO / مكتب الخصوصية", status)
        rec(tbl)


# ───────────────────────── privacy.privacy_actions ──────────────────────────
def seed_privacy_actions(cur):
    tbl = "privacy.privacy_actions"
    rows = [
        ("Implement human-review gate for AI eligibility scoring",
         "تطبيق بوابة مراجعة بشرية لتقييم الاستحقاق الآلي", "DPIA", "HIGH", "OPEN"),
        ("Close USB-encryption gap after breach",
         "معالجة ثغرة تشفير وسائط USB بعد الانتهاك", "BREACH", "HIGH", "IN_PROGRESS"),
        ("Add KSA-residency clause to vendor DPA",
         "إضافة شرط الإقامة داخل المملكة لاتفاقية المعالجة", "ASSESSMENT", "MEDIUM", "OPEN"),
        ("Publish updated website privacy notice",
         "نشر إشعار خصوصية الموقع المحدّث", "ASSESSMENT", "MEDIUM", "DONE"),
        ("Shorten inspection geolocation retention",
         "تقليص مدة الاحتفاظ بموقع التفتيش", "DPIA", "MEDIUM", "OPEN"),
        ("Remediate portal misconfiguration",
         "معالجة سوء تهيئة البوابة", "BREACH", "HIGH", "DONE"),
        ("Automate DSR intake workflow",
         "أتمتة مسار استقبال طلبات أصحاب البيانات", "DSR", "LOW", "OPEN"),
        ("Schedule SDAIA cross-border transfer notification",
         "جدولة إشعار سدايا بالنقل خارج المملكة", "CBT", "MEDIUM", "OPEN"),
    ]
    for en, ar, src, prio, status in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND title_en=?", ORG, en):
            continue
        completed_at = _completed_marker() if status == "DONE" else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (external_id, organisation_id, title_en, title_ar, description_en, source_type,
                 owner_name, due_date, priority, status, completed_at, is_deleted)
                VALUES (NEWID(), ?, ?, ?, ?, ?, ?, DATEADD(day, 45, CAST(SYSUTCDATETIME() AS date)),
                        ?, ?, ?, 0)""",
            ORG, en, ar, en, src, "Privacy Office / DATA_MGMT", prio, status, completed_at)
        rec(tbl)


# ───────────────────────── dataclass.data_asset ─────────────────────────────
def seed_data_assets(cur):
    tbl = "dataclass.data_asset"
    rows = [
        ("DA-GOV-001", "Beneficiary master register", "السجل الرئيسي للمستفيدين", "BENEFICIARY_PII",
         "CONFIDENTIAL", "NONE", "PUBLIC_TASK", DPO, 10, "SDO-2026-ROPA-101"),
        ("DA-GOV-002", "Licensing & permits register", "سجل التراخيص والتصاريح", "LICENSING",
         "CONFIDENTIAL", "NONE", "LEGAL_OBLIGATION", DPO, 10, None),
        ("DA-GOV-003", "Inspection case files", "ملفات قضايا التفتيش", "INSPECTION",
         "CONFIDENTIAL", "NONE", "PUBLIC_TASK", ASSESSOR, 7, "SDO-2026-ROPA-104"),
        ("DA-GOV-004", "Employee HR master", "السجل الرئيسي للموارد البشرية", "EMPLOYEE",
         "CONFIDENTIAL", "NONE", "LEGAL_OBLIGATION", DPO, 10, None),
        ("DA-GOV-005", "National-ID / NAFATH verification logs", "سجلات التحقق عبر الهوية الوطنية/نفاذ", "IDENTITY",
         "TOP_SECRET", "NONE", "LEGAL_OBLIGATION", DPO, 2, "SDO-2026-ROPA-102"),
        ("DA-GOV-006", "Biometric attendance templates", "قوالب الحضور البيومترية", "EMPLOYEE",
         "TOP_SECRET", "BIOMETRIC", "LEGAL_OBLIGATION", DPO, 3, None),
        ("DA-GOV-007", "Grievances & correspondence", "التظلمات والمراسلات", "GRIEVANCE",
         "CONFIDENTIAL", "NONE", "PUBLIC_TASK", ASSESSOR, 5, None),
        ("DA-GOV-008", "Open-data / published statistics", "البيانات المفتوحة والإحصاءات المنشورة", "OPEN_DATA",
         "PUBLIC", "NONE", "PUBLIC_TASK", ASSESSOR, 0, None),
        ("DA-GOV-009", "Internal policy library", "مكتبة السياسات الداخلية", "GOVERNANCE",
         "INTERNAL", "NONE", "PUBLIC_TASK", DPO, 0, None),
        ("DA-GOV-010", "De-identified analytics warehouse", "مستودع تحليلات مجهّل الهوية", "ANALYTICS",
         "INTERNAL", "NONE", "PUBLIC_TASK", ASSESSOR, 3, "SDO-2026-ROPA-108"),
    ]
    for ref, en, ar, domain, cls, special, lawful, owner, ret, dpia in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND reference=?", ORG, ref):
            continue
        enc = 1 if cls in ("CONFIDENTIAL", "TOP_SECRET") else 0
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, reference, name_en, name_ar, description_en, description_ar,
                 domain, classification, pdpl_special_category, pdpl_lawful_basis, status,
                 owner_role, owner_user_id, host_system_label, retention_years, encryption_at_rest,
                 encryption_in_transit, masking_required, classified_at, approved_at, next_review_due,
                 linked_dpia_ref, created_by, updated_by, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'IN_USE', ?, ?,
                        N'In-Kingdom hosting / استضافة داخل المملكة', ?, 1, 1, ?,
                        DATEADD(month, -4, SYSUTCDATETIME()), DATEADD(month, -3, SYSUTCDATETIME()),
                        DATEADD(year, 1, CAST(SYSUTCDATETIME() AS date)), ?, ?, ?, 0)""",
            ORG, ref, en, ar, en, ar, domain, cls, special, lawful,
            "Data Management Office / " + DMO, owner, ret, enc, dpia, ADMIN, ADMIN)
        rec(tbl)


def asset_id(cur, ref):
    cur.execute("SELECT id FROM dataclass.data_asset WHERE organisation_id=? AND reference=?", ORG, ref)
    r = cur.fetchone()
    return r[0] if r else None


def flow_id(cur, ref):
    cur.execute("SELECT id FROM dataclass.data_flow WHERE organisation_id=? AND reference=?", ORG, ref)
    r = cur.fetchone()
    return r[0] if r else None


def exc_id(cur, ref):
    cur.execute("SELECT id FROM dataclass.data_exception WHERE organisation_id=? AND reference=?", ORG, ref)
    r = cur.fetchone()
    return r[0] if r else None


# ───────────────────────── dataclass.data_flow ──────────────────────────────
def seed_data_flows(cur):
    tbl = "dataclass.data_flow"
    rows = [
        ("DF-GOV-101", "DA-GOV-001", "Beneficiary register", "NAFATH/Absher verification API", "SA",
         "IN_KINGDOM", "API", "ACTIVE", "Identity verification of beneficiaries.", "التحقق من هوية المستفيدين."),
        ("DF-GOV-102", "DA-GOV-002", "Licensing register", "DGA Government Service Bus", "SA",
         "IN_KINGDOM", "API", "ACTIVE", "Share licensing data with partner ministry.", "مشاركة بيانات التراخيص مع وزارة شريكة."),
        ("DF-GOV-103", "DA-GOV-001", "Eligibility engine", "GOSI/HRSD lookup", "SA",
         "IN_KINGDOM", "API", "ACTIVE", "Eligibility verification.", "التحقق من الاستحقاق."),
        ("DF-GOV-104", "DA-GOV-010", "Analytics warehouse", "In-Kingdom cloud analytics", "SA",
         "IN_KINGDOM", "BATCH", "ACTIVE", "Batch analytics over de-identified data.", "تحليلات دفعية على بيانات مجهّلة."),
        ("DF-GOV-105", "DA-GOV-004", "HR master", "Payroll SaaS support", "US",
         "NON_ADEQUATE", "FILE_TRANSFER", "SUSPENDED", "Vendor support access (cross-border).", "وصول دعم المزوّد (عبر الحدود)."),
        ("DF-GOV-106", "DA-GOV-003", "Inspection app", "Inspection case store", "SA",
         "IN_KINGDOM", "API", "ACTIVE", "Field capture to case store.", "نقل الالتقاط الميداني لمخزن القضايا."),
        ("DF-GOV-107", "DA-GOV-008", "Open-data set", "National Open Data Portal", "SA",
         "IN_KINGDOM", "FILE_TRANSFER", "ACTIVE", "Publish open data.", "نشر البيانات المفتوحة."),
        ("DF-GOV-108", "DA-GOV-001", "Primary store", "In-Kingdom DR site", "SA",
         "IN_KINGDOM", "REPLICATION", "ACTIVE", "Disaster-recovery replication.", "تكرار للتعافي من الكوارث."),
    ]
    for ref, aref, src, dest, country, adeq, mode, status, pen, par in rows:
        aid = asset_id(cur, aref)
        if aid is None:
            continue
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND reference=?", ORG, ref):
            continue
        xref = "EXT-CBT-102" if adeq == "NON_ADEQUATE" else None
        import datetime
        now = datetime.date.today()
        activated = (now - datetime.timedelta(days=60)).isoformat() if status == "ACTIVE" else None
        suspended = (now - datetime.timedelta(days=10)).isoformat() if status == "SUSPENDED" else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, data_asset_id, reference, source_system_label, destination_system_label,
                 destination_country, destination_adequacy, transfer_mode, status, purpose_en, purpose_ar,
                 pdpl_art7_transfer_ref, approved_at, activated_at, suspended_at, created_by, updated_by, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        DATEADD(month, -3, SYSUTCDATETIME()), ?, ?, ?, ?, 0)""",
            ORG, aid, ref, src, dest, country, adeq, mode, status, pen, par,
            xref, activated, suspended, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── dataclass.data_exception ─────────────────────────
def seed_data_exceptions(cur):
    tbl = "dataclass.data_exception"
    rows = [
        ("EXC-GOV-101", "DA-GOV-003", "RETENTION_EXTENSION", "ACTIVE",
         "Retain inspection files beyond standard period for ongoing litigation.",
         "الاحتفاظ بملفات التفتيش بعد المدة المعتادة لدعوى قضائية قائمة."),
        ("EXC-GOV-102", "DA-GOV-010", "MASKING_WAIVER", "ACTIVE",
         "Waive masking on the analytics cohort key with compensating controls.",
         "إعفاء من إخفاء مفتاح المجموعة التحليلية مع ضوابط تعويضية."),
        ("EXC-GOV-103", "DA-GOV-004", "CROSS_BORDER_TEMP", "ACTIVE",
         "Time-boxed cross-border SaaS support PoC with DPO and CISO sign-off and SDAIA notification.",
         "إثبات مفهوم محدد المدة لدعم برمجي عبر الحدود باعتماد مكتب حماية البيانات ورئيس الأمن وإشعار سدايا."),
        ("EXC-GOV-104", "DA-GOV-010", "ENCRYPTION_AT_REST_WAIVER", "ACTIVE",
         "Waive at-rest encryption on an ephemeral cache.",
         "إعفاء من التشفير عند التخزين لذاكرة مؤقتة عابرة."),
        ("EXC-GOV-105", "DA-GOV-001", "RETENTION_EXTENSION", "REQUESTED",
         "Requested retention extension pending DPO approval.",
         "طلب تمديد احتفاظ بانتظار موافقة مكتب حماية البيانات."),
    ]
    for ref, aref, etype, status, jen, jar in rows:
        aid = asset_id(cur, aref)
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND reference=?", ORG, ref):
            continue
        active = status == "ACTIVE"
        approved_at = _completed_marker() if active else None
        approver = DPO if active else None
        import datetime
        now = datetime.date.today()
        valid_until = ((now + datetime.timedelta(days=60)) if ref == "EXC-GOV-103"
                       else (now + datetime.timedelta(days=365))).isoformat()
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, data_asset_id, reference, exception_type, status, justification_en,
                 justification_ar, compensating_controls_en, compensating_controls_ar,
                 requested_by_user_id, approver_user_id, approved_at, valid_from, valid_until,
                 created_by, updated_by, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?,
                        N'Access logging, monitoring and time-boxing applied.',
                        N'تطبيق تسجيل الوصول والمراقبة وتحديد المدة.',
                        ?, ?, ?, CAST(SYSUTCDATETIME() AS date), ?,
                        ?, ?, 0)""",
            ORG, aid, ref, etype, status, jen, jar, ASSESSOR, approver, approved_at, valid_until, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── dataclass.dataclass_event (timeline) ─────────────
def seed_dataclass_events(cur):
    tbl = "dataclass.dataclass_event"
    notes = {
        "CLASSIFIED": ("Asset classified per NDMO levels.", "تم تصنيف الأصل وفق مستويات مكتب إدارة البيانات."),
        "APPROVED": ("Classification approved by Data Management Office.", "اعتُمد التصنيف من مكتب إدارة البيانات."),
        "REVIEWED": ("Periodic classification review completed.", "اكتملت المراجعة الدورية للتصنيف."),
        "FLOW_PROPOSED": ("Data flow proposed.", "تم اقتراح تدفق البيانات."),
        "FLOW_ACTIVATED": ("Data flow activated.", "تم تفعيل تدفق البيانات."),
        "FLOW_SUSPENDED": ("Data flow suspended pending re-assessment.", "عُلِّق تدفق البيانات بانتظار إعادة التقييم."),
        "EXCEPTION_REQUESTED": ("Classification exception requested.", "تم طلب استثناء تصنيف."),
        "EXCEPTION_APPROVED": ("Classification exception approved by DPO.", "اعتُمد استثناء التصنيف من مكتب حماية البيانات."),
    }
    events = []  # (kind, ref, event_type, status_after)
    for aref in ("DA-GOV-001", "DA-GOV-003", "DA-GOV-005", "DA-GOV-010"):
        events += [("asset", aref, "CLASSIFIED", "IN_USE"),
                   ("asset", aref, "APPROVED", "IN_USE"),
                   ("asset", aref, "REVIEWED", "IN_USE")]
    events += [("flow", "DF-GOV-102", "FLOW_PROPOSED", "PROPOSED"),
               ("flow", "DF-GOV-102", "FLOW_ACTIVATED", "ACTIVE"),
               ("flow", "DF-GOV-105", "FLOW_PROPOSED", "PROPOSED"),
               ("flow", "DF-GOV-105", "FLOW_SUSPENDED", "SUSPENDED")]
    events += [("exc", "EXC-GOV-101", "EXCEPTION_REQUESTED", "REQUESTED"),
               ("exc", "EXC-GOV-101", "EXCEPTION_APPROVED", "ACTIVE"),
               ("exc", "EXC-GOV-105", "EXCEPTION_REQUESTED", "REQUESTED")]

    import datetime
    base = datetime.datetime.utcnow()
    for seq, (kind, ref, etype, status_after) in enumerate(events):
        aid = fid = eid = None
        if kind == "asset":
            aid = asset_id(cur, ref); idcol, idval = "data_asset_id", aid
        elif kind == "flow":
            fid = flow_id(cur, ref); idcol, idval = "data_flow_id", fid
        else:
            eid = exc_id(cur, ref); idcol, idval = "data_exception_id", eid
        if idval is None:
            continue
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND event_type=? AND {idcol}=?", ORG, etype, idval):
            continue
        occurred = (base - datetime.timedelta(days=120 - seq * 3)).strftime("%Y-%m-%d %H:%M:%S")
        nen, nar = notes[etype]
        actor_label = etype.replace("FLOW_", "").replace("EXCEPTION_", "")
        cur.execute(
            f"""INSERT INTO {tbl}
                (organisation_id, data_asset_id, data_flow_id, data_exception_id, occurred_at,
                 event_type, actor_user_id, actor_name, status_after, note_en, note_ar,
                 created_by, updated_by, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?,
                        N'Privacy Office / Data Management Office', ?, ?, ?, ?, ?, 0)""",
            ORG, aid, fid, eid, occurred, etype, (DPO if seq % 2 else ASSESSOR),
            status_after, nen, nar, ADMIN, ADMIN)
        rec(tbl)


# ───────────────────────── privacy_lc.dsr_cases ─────────────────────────────
def seed_dsr_cases_lc(cur):
    tbl = "privacy_lc.dsr_cases"
    rows = [
        ("DSRLC-2026-101", "ACCESS", "سلمان بن عبدالله الشهري", "4521", "s.alshehri.lc1@example.sa", "DELIVERED", 40),
        ("DSRLC-2026-102", "ERASURE", "منى بنت فهد القرني", "8830", "m.alqarni.lc2@example.sa", "REJECTED", 35),
        ("DSRLC-2026-103", "RECTIFICATION", "تركي بن سعود العمري", "1192", "t.alamri.lc3@example.sa", "COLLECTING", 12),
        ("DSRLC-2026-104", "PORTABILITY", "لمياء بنت محمد الحارثي", "7745", "l.alharthi.lc4@example.sa", "REDACTING", 18),
        ("DSRLC-2026-105", "OBJECTION", "بدر بن ناصر الفيفي", "3098", "b.alfaifi.lc5@example.sa", "VERIFYING_IDENTITY", 6),
        ("DSRLC-2026-106", "ACCESS", "أمل بنت خالد الزهراني", "5567", "a.alzahrani.lc6@example.sa", "EXTENDED", 45),
        ("DSRLC-2026-107", "WITHDRAW_CONSENT", "فيصل بن عبدالعزيز المالكي", "2231", "f.almalki.lc7@example.sa", "INTAKE", 3),
        ("DSRLC-2026-108", "ACCESS", "جواهر بنت سعد الدوسري", "9904", "j.aldosari.lc8@example.sa", "COLLECTING", 15),
    ]
    import datetime
    base = datetime.datetime.utcnow()
    for ext, rtype, name, id4, email, status, ago in rows:
        if exists(cur, f"SELECT 1 FROM {tbl} WHERE organisation_id=? AND external_id=?", ORG, ext):
            continue
        filed_at = (base - datetime.timedelta(days=ago)).strftime("%Y-%m-%d %H:%M:%S")
        sla = (base - datetime.timedelta(days=ago) + datetime.timedelta(days=30)).date().isoformat()
        verified_at = ((base - datetime.timedelta(days=ago - 1)).strftime("%Y-%m-%d %H:%M:%S")
                       if status not in ("INTAKE", "VERIFYING_IDENTITY") else None)
        delivered_at = (base - datetime.timedelta(days=2)).strftime("%Y-%m-%d %H:%M:%S") if status == "DELIVERED" else None
        delivery_method = "SECURE_PORTAL" if status == "DELIVERED" else None
        ext_flag = 1 if status == "EXTENDED" else 0
        ext_reason = "Complex request requiring inter-department collection. / طلب معقّد يتطلب جمعاً بين الإدارات." if status == "EXTENDED" else None
        rej_reason = "Erasure denied: records retained under statutory obligation (PDPL Art.20). / رُفض الحذف: تُحفظ السجلات بموجب التزام نظامي." if status == "REJECTED" else None
        cur.execute(
            f"""INSERT INTO {tbl}
                (external_id, organisation_id, type, subject_name, national_id_last4, contact_email,
                 status, filed_at, identity_verified_at, verification_method, data_summary_en, data_summary_ar,
                 delivery_method, delivered_at, sla_deadline, extended, extension_reason_en,
                 rejection_reason_en, ropa_ids, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, N'NAFATH',
                        N'Subject data located across beneficiary and service registers.',
                        N'حُدّدت بيانات صاحب الطلب عبر سجلات المستفيدين والخدمات.',
                        ?, ?, ?, ?, ?, ?, N'SDO-2026-ROPA-101,SDO-2026-ROPA-105', 0)""",
            ext, ORG, rtype, name, id4, email, status, filed_at, verified_at,
            delivery_method, delivered_at, sla, ext_flag, ext_reason, rej_reason)
        rec(tbl)


# ───────────────────────── main ─────────────────────────────────────────────
def main():
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()
    print("Seeding Saudi-government PRIVACY layer for organisation_id =", ORG, "\n")

    seed_processing_activities(cur)
    seed_dsr(cur)
    seed_dpia(cur)
    dpia_targets = get_dpia_targets(cur)
    seed_dpia_questions(cur, dpia_targets)
    seed_dpia_mitigations(cur, dpia_targets)
    seed_dpia_reviews(cur, dpia_targets)
    seed_breaches(cur)
    seed_cbt(cur)
    seed_dsa(cur)
    seed_notices(cur)
    seed_processor_assessments(cur)
    seed_subprocessor(cur)
    seed_subprocessor_events(cur)
    seed_disposal(cur)
    seed_consent(cur)
    seed_privacy_assessments(cur)
    seed_privacy_actions(cur)
    seed_data_assets(cur)
    seed_data_flows(cur)
    seed_data_exceptions(cur)
    seed_dataclass_events(cur)
    seed_dsr_cases_lc(cur)

    print("\nPer-table summary (inserted):")
    total_ins = 0
    for tbl in sorted(summary):
        ins = summary[tbl][0]
        total_ins += ins
        print(f"  {tbl:<36} +{ins}")
    print(f"\nTOTAL inserted = {total_ins}")

    print("\nVerification — org1 row counts after seeding:")
    for sch, tbl, child in [
        ("privacy", "processing_activities", False), ("privacy", "data_subject_requests", False),
        ("privacy", "dpia", False), ("privacy", "dpia_question", True),
        ("privacy", "dpia_mitigation", True), ("privacy", "dpia_review", True),
        ("privacy", "data_breaches", False), ("privacy", "cross_border_transfers", False),
        ("privacy", "data_sharing_agreements", False), ("privacy", "privacy_notices", False),
        ("privacy", "processor_assessments", False), ("privacy", "subprocessor", False),
        ("privacy", "subprocessor_event", False), ("privacy", "disposal_certificates", False),
        ("privacy", "consent_records", False), ("privacy", "privacy_assessments", False),
        ("privacy", "privacy_actions", False), ("dataclass", "data_asset", False),
        ("dataclass", "data_flow", False), ("dataclass", "data_exception", False),
        ("dataclass", "dataclass_event", False), ("privacy_lc", "dsr_cases", False),
    ]:
        if child:
            cur.execute(f"SELECT COUNT(*) FROM {sch}.{tbl}")
        else:
            cur.execute(f"SELECT COUNT(*) FROM {sch}.{tbl} WHERE organisation_id=1")
        print(f"  {sch + '.' + tbl:<40} {cur.fetchone()[0]}")

    conn.close()
    print("\nDone.")


if __name__ == "__main__":
    main()
