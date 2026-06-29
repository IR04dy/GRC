# -*- coding: utf-8 -*-
"""Seed a Saudi GOVERNMENT-AUTHORITY compliance layer into the compliance / regchange
/ nca_ecc schemas for the default tenant (organisation_id = 1).

The tenant is a Saudi general authority (هيئة عامة) led by a President (رئيس الهيئة).
The DB already carries a large BANK-themed compliance dataset (SAMA CAR/LCR/ICAAP,
Treasury, Financial Crimes...). Rather than rewrite that, this script ADDS a
government-native regulatory layer mapped to the authorities the entity actually
answers to:

    NCA   - Essential Cybersecurity Controls ECC-2:2024 (الهيئة الوطنية للأمن السيبراني)
    NDMO / SDAIA - national data management & AI (مكتب إدارة البيانات الوطنية / سدايا)
    PDPL  - Personal Data Protection Law (نظام حماية البيانات الشخصية)
    Nazaha - integrity & anti-corruption (هيئة الرقابة ومكافحة الفساد - نزاهة)
    DGA   - Digital Government Authority (هيئة الحكومة الرقمية)
    NCEMA - National Centre for crisis & continuity (المركز الوطني لإدارة الكوارث والأزمات)

Tables seeded (INSERT / idempotent-UPSERT only - never DELETE / ALTER / TRUNCATE):
    compliance.obligation, compliance.obligation_attestation_campaign,
    compliance.obligation_attestation, compliance.regulatory_changes,
    regchange.changes, compliance.audit_engagements,
    compliance.compliance_assessments, compliance.policies,
    compliance.policy_framework_mappings, compliance.controls,
    compliance.findings, compliance.compliance_findings, compliance.evidence_items

Skipped (reported, not touched): compliance.assessments (legacy 4-row CHECK-bound
set), compliance.frameworks (global reference catalogue), nca_ecc.filings
(FY2023..FY2026 trail already complete).

Idempotency: every INSERT is guarded by a natural key (code / external_id /
(parent,label)). A second run inserts 0.

    Usage:  python seed_gov_compliance.py
"""
from __future__ import annotations

import json
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
# iam.users org1
U_ADMIN, U_RMGR, U_COMPLIANCE, U_AUDITOR, U_RANALYST, U_VIEWER = 1, 2, 3, 4, 5, 6

_ins: dict[str, int] = {}


def bump(table: str, n: int = 1) -> None:
    _ins[table] = _ins.get(table, 0) + n


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    cur.execute("SELECT code, id FROM compliance.frameworks")
    FW = {code: fid for code, fid in cur.fetchall()}
    cur.execute("SELECT framework_code, id FROM platform.control_frameworks")
    PFW = {code: fid for code, fid in cur.fetchall()}

    def exists(sql: str, *params) -> bool:
        cur.execute(sql, *params)
        return cur.fetchone() is not None

    # 1. compliance.controls  (deepen) - key (organisation_id, code)
    print("\n# compliance.controls")
    CONTROLS = [
        ("NCA-ECC-1-3", "Cybersecurity Roles & Responsibilities",
         "أدوار ومسؤوليات الأمن السيبراني", "NCA_ECC", "Cybersecurity Governance",
         "IMPLEMENTED", 4, "CISO Office",
         "RACI for cybersecurity roles approved by the Cybersecurity Supervisory Committee (ECC 1-3)."),
        ("NCA-ECC-1-4", "Cybersecurity Risk Management",
         "إدارة مخاطر الأمن السيبراني", "NCA_ECC", "Cybersecurity Governance",
         "PARTIAL", 3, "CISO Office",
         "Cyber risk methodology and register integrated with enterprise risk (ECC 1-4)."),
        ("NCA-ECC-2-1-GOV", "Asset Management & Inventory",
         "إدارة الأصول وحصرها", "NCA_ECC", "Cybersecurity Defence",
         "PARTIAL", 2, "Information Technology",
         "Authoritative asset inventory covering information and technology assets (ECC 2-1)."),
        ("NCA-ECC-2-10", "Network Security Management",
         "إدارة أمن الشبكات", "NCA_ECC", "Cybersecurity Defence",
         "IMPLEMENTED", 3, "Information Technology",
         "Network segmentation, perimeter and internal protection controls (ECC 2-10)."),
        ("NDMO-DM-1", "Data Ownership & Stewardship",
         "ملكية البيانات والإشراف عليها", "NCA_ECC", "Data Governance",
         "PARTIAL", 3, "Data Governance Office",
         "Data domains assigned to data owners and stewards per NDMO Data Management standards."),
        ("NDMO-DM-2", "Metadata & Data Catalogue",
         "البيانات الوصفية وفهرس البيانات", "NCA_ECC", "Data Governance",
         "PLANNED", 2, "Data Governance Office",
         "Enterprise metadata repository and business glossary per NDMO standards."),
        ("NDMO-DC-1", "National Data Classification",
         "تصنيف البيانات الوطنية", "PDPL", "Data Governance",
         "PARTIAL", 2, "Data Governance Office",
         "Classification of data assets per NDMO & SDAIA national classification policy."),
        ("PDPL-ART-20", "Personal Data Breach Notification",
         "الإبلاغ عن انتهاك البيانات الشخصية", "PDPL", "Privacy",
         "IMPLEMENTED", 3, "Privacy Office",
         "72-hour breach notification to SDAIA and affected data subjects per PDPL Art.20."),
        ("PDPL-ROPA", "Record of Processing Activities (ROPA)",
         "سجل أنشطة معالجة البيانات", "PDPL", "Privacy",
         "PARTIAL", 3, "Privacy Office",
         "Maintained record of processing activities covering all personal-data processing (PDPL)."),
        ("NAZAHA-INT-1", "Conflict of Interest & Integrity Disclosure",
         "إقرار تعارض المصالح والنزاهة", "NAZAHA",
         "Integrity & Anti-Corruption", "PARTIAL", 2, "Compliance Department",
         "Annual conflict-of-interest declarations and integrity controls aligned with Nazaha."),
    ]
    for code, ne, na, fwc, dom, st, mat, owner, desc in CONTROLS:
        if exists("SELECT 1 FROM compliance.controls WHERE organisation_id=? AND code=?", ORG, code):
            print(f"  = {code}")
            continue
        fwid = FW.get(fwc)
        cur.execute(
            """INSERT INTO compliance.controls
                 (organisation_id, code, name_en, name_ar, framework, domain,
                  maturity_level, status, owner, description, framework_id,
                  created_by, created_at, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),0)""",
            ORG, code, ne, na, fwc, dom, mat, st, owner, desc, fwid, U_ADMIN)
        bump("compliance.controls")
        print(f"  + {code}  {ne}")

    def control_id(code):
        cur.execute("SELECT id FROM compliance.controls WHERE organisation_id=? AND code=?", ORG, code)
        r = cur.fetchone()
        return r[0] if r else None

    # 2. compliance.obligation  (deepen) - key obligation_code
    print("\n# compliance.obligation")
    OBLIG = [
        ("OBL-NCA-2026-101", "NCA", "ATTESTATION", "ANNUAL", "CRITICAL",
         "2026-09-30", "NCA-ECC-1-3", "Compliance Department", "إدارة الالتزام",
         "NCA ECC-2:2024 annual self-assessment & governance compliance",
         "التقييم الذاتي السنوي لضوابط الأمن السيبراني ECC-2:2024 والالتزام بالحوكمة",
         "NCA ECC-2:2024 Control 1-1",
         "Annual self-assessment against NCA Essential Cybersecurity Controls ECC-2:2024 and board-level governance attestation.",
         "تقييم ذاتي سنوي وفق الضوابط الأساسية للأمن السيبراني ECC-2:2024 وإقرار الحوكمة على مستوى المجلس."),
        ("OBL-NCA-2026-102", "NCA", "NOTIFICATION", "ON_EVENT", "CRITICAL",
         "2026-12-31", "NCA-ECC-2-10", "CISO Office / Compliance", "مكتب الأمن السيبراني / الالتزام",
         "NCA (Haseen) cyber incident notification within 72 hours",
         "الإبلاغ عن الحوادث السيبرانية لمنصة حصين خلال 72 ساعة",
         "NCA Incident Reporting / Haseen",
         "Mandatory notification of material cybersecurity incidents to NCA via the Haseen platform within 72 hours.",
         "الإبلاغ الإلزامي عن الحوادث السيبرانية الجوهرية للهيئة الوطنية للأمن السيبراني عبر منصة حصين خلال 72 ساعة."),
        ("OBL-NDMO-2026-101", "SDAIA", "REVIEW", "ANNUAL", "HIGH",
         "2026-10-31", "NDMO-DM-1", "Data Governance Office", "مكتب حوكمة البيانات",
         "NDMO National Data Management & Personal Data Protection Standards compliance",
         "الالتزام بمعايير إدارة البيانات الوطنية وحماية البيانات الشخصية",
         "NDMO Data Management Standards",
         "Periodic compliance review against the NDMO National Data Management and Personal Data Protection Standards.",
         "مراجعة دورية للالتزام بمعايير إدارة البيانات الوطنية وحماية البيانات الشخصية الصادرة عن مكتب إدارة البيانات الوطنية."),
        ("OBL-SDAIA-2026-101", "SDAIA", "REVIEW", "SEMI_ANNUAL", "HIGH",
         "2026-08-31", "NDMO-DC-1", "Data Governance Office", "مكتب حوكمة البيانات",
         "National data classification review (SDAIA / NDMO)",
         "مراجعة تصنيف البيانات الوطنية (سدايا / مكتب إدارة البيانات)",
         "NDMO/SDAIA Data Classification Policy",
         "Semi-annual review and recertification of data-asset classification per the national data classification policy.",
         "مراجعة وإعادة اعتماد تصنيف أصول البيانات نصف سنوياً وفق سياسة تصنيف البيانات الوطنية."),
        ("OBL-PDPL-2026-101", "SDAIA", "DOCUMENTATION", "ANNUAL", "HIGH",
         "2026-11-30", "PDPL-ROPA", "Privacy Office", "مكتب حماية البيانات",
         "PDPL Record of Processing Activities (ROPA) annual refresh",
         "التحديث السنوي لسجل أنشطة معالجة البيانات الشخصية",
         "PDPL & Implementing Regulation",
         "Annual review and refresh of the Record of Processing Activities covering all personal-data processing.",
         "مراجعة وتحديث سجل أنشطة معالجة البيانات الشخصية سنوياً بما يشمل جميع عمليات المعالجة."),
        ("OBL-PDPL-2026-102", "SDAIA", "REPORTING", "ON_EVENT", "CRITICAL",
         "2026-12-31", "PDPL-ART-20", "Privacy Office", "مكتب حماية البيانات",
         "PDPL personal-data breach notification to SDAIA within 72 hours",
         "الإبلاغ عن انتهاك البيانات الشخصية لسدايا خلال 72 ساعة",
         "PDPL Art.20",
         "Notification of personal-data breaches to SDAIA and affected data subjects within 72 hours per PDPL Art.20.",
         "الإبلاغ عن انتهاكات البيانات الشخصية لسدايا وأصحاب البيانات المتأثرين خلال 72 ساعة وفق المادة 20."),
        ("OBL-PDPL-2026-103", "SDAIA", "REVIEW", "QUARTERLY", "MEDIUM",
         "2026-09-30", "PDPL-ROPA", "Privacy Office", "مكتب حماية البيانات",
         "Data-subject rights (DSR) request handling SLA",
         "الالتزام بمستوى خدمة معالجة طلبات أصحاب البيانات",
         "PDPL - data subject rights",
         "Quarterly monitoring of data-subject access/erasure request handling within the regulated SLA.",
         "متابعة ربع سنوية لمعالجة طلبات الوصول والتصحيح والحذف ضمن مستوى الخدمة النظامي."),
        ("OBL-NAZAHA-2026-101", "INTERNAL", "ATTESTATION", "ANNUAL", "HIGH",
         "2026-12-15", "NAZAHA-INT-1", "Compliance Department", "إدارة الالتزام",
         "Nazaha annual integrity & conflict-of-interest disclosure",
         "إقرار النزاهة وتعارض المصالح السنوي (نزاهة)",
         "Nazaha integrity programme",
         "Annual integrity and conflict-of-interest declarations for executives and decision-makers aligned with Nazaha.",
         "إقرارات النزاهة وتعارض المصالح السنوية للتنفيذيين وأصحاب القرار بما يتوافق مع متطلبات نزاهة."),
        ("OBL-NAZAHA-2026-102", "INTERNAL", "REPORTING", "ANNUAL", "MEDIUM",
         "2027-01-31", "NAZAHA-INT-1", "Compliance Department", "إدارة الالتزام",
         "Nazaha annual anti-corruption & whistleblowing report",
         "التقرير السنوي لمكافحة الفساد والإبلاغ عن المخالفات (نزاهة)",
         "Nazaha anti-corruption framework",
         "Annual report on anti-corruption controls and whistleblowing cases to the Integrity & Anti-Fraud Committee.",
         "تقرير سنوي عن ضوابط مكافحة الفساد وبلاغات المخالفات يرفع للجنة النزاهة ومكافحة الاحتيال."),
        ("OBL-DGA-2026-101", "DGA", "FILING", "ANNUAL", "MEDIUM",
         "2026-10-15", None, "Digital Transformation", "إدارة التحول الرقمي",
         "DGA digital-government maturity (Qiyas) annual submission",
         "تقديم مؤشر قياس النضج الرقمي للحكومة الرقمية سنوياً",
         "DGA Qiyas / Yefi maturity index",
         "Annual submission to the Digital Government Authority maturity assessment (Qiyas) and improvement plan.",
         "التقديم السنوي لمؤشر قياس النضج الرقمي لهيئة الحكومة الرقمية وخطة التحسين."),
        ("OBL-DGA-2026-102", "DGA", "REPORTING", "QUARTERLY", "LOW",
         "2026-09-30", None, "Digital Transformation", "إدارة التحول الرقمي",
         "DGA open-data publishing & dataset refresh",
         "نشر البيانات المفتوحة وتحديث مجموعات البيانات (الحكومة الرقمية)",
         "DGA Open Data Policy",
         "Quarterly publication and refresh of open datasets on the national open-data portal per DGA policy.",
         "نشر وتحديث مجموعات البيانات المفتوحة ربع سنوياً على البوابة الوطنية للبيانات المفتوحة."),
        ("OBL-NCEMA-2026-101", "NCEMA", "ATTESTATION", "ANNUAL", "HIGH",
         "2026-11-15", None, "Business Continuity", "إدارة استمرارية الأعمال",
         "NCEMA business-continuity readiness annual attestation",
         "الإقرار السنوي لجاهزية استمرارية الأعمال (المركز الوطني)",
         "NCEMA 7000 / ISO 22301",
         "Annual attestation of business-continuity and crisis-readiness aligned with NCEMA requirements.",
         "إقرار سنوي بجاهزية استمرارية الأعمال وإدارة الأزمات بما يتوافق مع متطلبات المركز الوطني لإدارة الكوارث والأزمات."),
        ("OBL-DGA-2026-103", "DGA", "REGISTRATION", "ANNUAL", "LOW",
         "2026-12-01", None, "Digital Transformation", "إدارة التحول الرقمي",
         "Nafath / digital-identity integration assurance",
         "ضمان التكامل مع نفاذ والهوية الرقمية",
         "DGA digital-identity directive",
         "Annual assurance that beneficiary services are integrated with the Nafath national digital-identity platform.",
         "تأكيد سنوي بتكامل خدمات المستفيدين مع منصة نفاذ الوطنية للهوية الرقمية."),
        ("OBL-NCA-2026-103", "NCA", "REVIEW", "ANNUAL", "MEDIUM",
         "2026-09-30", "NCA-ECC-1-4", "CISO Office / Compliance", "مكتب الأمن السيبراني / الالتزام",
         "NCA ECC cybersecurity risk-management review",
         "مراجعة إدارة مخاطر الأمن السيبراني وفق الضوابط الأساسية",
         "NCA ECC-2:2024 Control 1-4",
         "Annual review of the cybersecurity risk-management methodology and treatment plans per ECC 1-4.",
         "مراجعة سنوية لمنهجية إدارة مخاطر الأمن السيبراني وخطط المعالجة وفق الضابط 1-4."),
    ]
    for (code, reg, otype, freq, sev, due, cctrl, bu_en, bu_ar,
         te, ta, ref, de, da) in OBLIG:
        if exists("SELECT 1 FROM compliance.obligation WHERE organisation_id=? AND obligation_code=?", ORG, code):
            print(f"  = {code}")
            continue
        rcid = control_id(cctrl) if cctrl else None
        cur.execute(
            """INSERT INTO compliance.obligation
                 (organisation_id, obligation_code, title_en, title_ar,
                  description_en, description_ar, regulator, regulation_reference,
                  obligation_type, frequency, severity_if_missed, next_due_date,
                  owner_user_id, backup_owner_user_id, business_unit_en, business_unit_ar,
                  related_control_id, lifecycle_stage, created_by_user_id,
                  created_at, created_by, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,SYSUTCDATETIME(),?,0)""",
            ORG, code, te, ta, de, da, reg, ref, otype, freq, sev, due,
            U_COMPLIANCE, U_AUDITOR, bu_en, bu_ar, rcid, U_ADMIN, U_ADMIN)
        bump("compliance.obligation")
        print(f"  + {code}  {te}")

    def oblig_id(code):
        cur.execute("SELECT id FROM compliance.obligation WHERE organisation_id=? AND obligation_code=?", ORG, code)
        r = cur.fetchone()
        return r[0] if r else None

    # 3. compliance.obligation_attestation_campaign  (fill) - key campaign_code
    print("\n# compliance.obligation_attestation_campaign")
    CAMP = [
        ("CAMP-2026-Q1", "Q1-2026 Quarterly Obligation Attestation",
         "حملة إقرار الالتزامات للربع الأول 2026", 2026, "Q1",
         "2026-01-05T08:00:00", "2026-03-31T16:00:00", "2026-03-31T16:00:00", "CLOSED",
         "First-quarter attestation cycle for active regulatory obligations, run by the Compliance Department.",
         "دورة الإقرار للربع الأول للالتزامات التنظيمية النشطة، تديرها إدارة الالتزام.",
         4, 0, 3, 1, 0, 0),
        ("CAMP-2026-Q2", "Q2-2026 Quarterly Obligation Attestation",
         "حملة إقرار الالتزامات للربع الثاني 2026", 2026, "Q2",
         "2026-04-05T08:00:00", "2026-06-30T16:00:00", None, "IN_PROGRESS",
         "Second-quarter attestation cycle for active regulatory obligations.",
         "دورة الإقرار للربع الثاني للالتزامات التنظيمية النشطة.",
         4, 2, 1, 0, 1, 0),
        ("CAMP-2026-ANNUAL-PDPL-NDMO", "2026 Annual PDPL & NDMO Obligations Attestation",
         "حملة الإقرار السنوية لالتزامات حماية البيانات وإدارة البيانات 2026", 2026, None,
         "2026-06-01T08:00:00", "2026-12-15T16:00:00", None, "OPEN",
         "Annual attestation cycle covering PDPL and NDMO data-protection and data-management obligations.",
         "دورة الإقرار السنوية لالتزامات حماية البيانات الشخصية وإدارة البيانات الوطنية.",
         3, 1, 0, 1, 0, 0),
        ("CAMP-2026-Q3-ECC", "Q3-2026 NCA ECC Governance Attestation",
         "حملة إقرار حوكمة الضوابط الأساسية للأمن السيبراني للربع الثالث 2026", 2026, "Q3",
         "2026-07-01T08:00:00", "2026-09-30T16:00:00", None, "OPEN",
         "Quarterly attestation focused on NCA ECC-2:2024 governance controls.",
         "دورة إقرار ربع سنوية تركز على ضوابط الحوكمة في ECC-2:2024.",
         2, 0, 0, 0, 0, 0),
    ]
    for (code, te, ta, yr, q, opened, closes, closed, status,
         de, da, inv, inprog, att, attexc, overdue, na) in CAMP:
        if exists("SELECT 1 FROM compliance.obligation_attestation_campaign WHERE organisation_id=? AND campaign_code=?", ORG, code):
            print(f"  = {code}")
            continue
        cur.execute(
            """INSERT INTO compliance.obligation_attestation_campaign
                 (organisation_id, campaign_code, title_en, title_ar,
                  description_en, description_ar, cycle_year, cycle_quarter,
                  opened_at, closes_at, closed_at, reminder_cadence_days, status,
                  total_invited, total_in_progress, total_attested,
                  total_attested_with_excs, total_overdue, total_na,
                  created_by_user_id, created_at, created_by, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,7,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,0)""",
            ORG, code, te, ta, de, da, yr, q, opened, closes, closed, status,
            inv, inprog, att, attexc, overdue, na, U_COMPLIANCE, U_ADMIN)
        bump("compliance.obligation_attestation_campaign")
        print(f"  + {code}  {te}")

    def camp_id(code):
        cur.execute("SELECT id FROM compliance.obligation_attestation_campaign WHERE organisation_id=? AND campaign_code=?", ORG, code)
        r = cur.fetchone()
        return r[0] if r else None

    # 4. compliance.obligation_attestation  (fill) - key (obligation_id, period_label)
    print("\n# compliance.obligation_attestation")

    def jrefs(*items):
        return json.dumps(list(items), ensure_ascii=False)

    ATT = [
        ("OBL-NCA-2026-101", "CAMP-2026-Q1", "Q1-2026", "2026-01-01", "2026-03-31", "2026-03-31",
         "ATTESTED", "2026-03-20T10:00:00",
         "NCA ECC governance reviewed; roles and committee oversight confirmed for Q1.",
         "تمت مراجعة حوكمة الضوابط الأساسية وتأكيد الأدوار وإشراف اللجنة للربع الأول.",
         jrefs("EV-ECC-CHARTER", "COM-CYBER-minutes-Q1"),
         None, None, "ACCEPTED", "2026-03-25T09:00:00",
         "Attestation accepted; governance evidence sufficient.",
         "تم قبول الإقرار؛ أدلة الحوكمة كافية."),
        ("OBL-PDPL-2026-101", "CAMP-2026-Q1", "Q1-2026", "2026-01-01", "2026-03-31", "2026-03-31",
         "ATTESTED_WITH_EXCEPTIONS", "2026-03-22T11:30:00",
         "ROPA reviewed; majority of processing activities documented.",
         "تمت مراجعة سجل المعالجة وتوثيق غالبية أنشطة المعالجة.",
         jrefs("EV-ROPA-2026"),
         "ROPA for two new beneficiary services not yet fully documented.",
         "لم يُستكمل توثيق سجل المعالجة لخدمتين جديدتين للمستفيدين.",
         "ACCEPTED", "2026-03-28T13:00:00",
         "Accepted with a remediation action to complete the ROPA gaps.",
         "قُبل مع إجراء تصحيحي لاستكمال الثغرات في سجل المعالجة."),
        ("OBL-DGA-2026-102", "CAMP-2026-Q1", "Q1-2026", "2026-01-01", "2026-03-31", "2026-03-31",
         "ATTESTED", "2026-03-18T09:15:00",
         "Open datasets refreshed and published on the national portal for Q1.",
         "تم تحديث ونشر مجموعات البيانات المفتوحة على البوابة الوطنية للربع الأول.",
         jrefs("EV-OPENDATA-Q1"),
         None, None, "ACCEPTED", "2026-03-26T10:00:00",
         "Open-data publishing confirmed.",
         "تم تأكيد نشر البيانات المفتوحة."),
        ("OBL-NAZAHA-2026-101", "CAMP-2026-Q1", "Q1-2026", "2026-01-01", "2026-03-31", "2026-03-31",
         "ATTESTED", "2026-03-15T08:45:00",
         "Conflict-of-interest declarations collected from executives for the cycle.",
         "تم جمع إقرارات تعارض المصالح من التنفيذيين للدورة.",
         jrefs("EV-COI-REGISTER"),
         None, None, "ACCEPTED", "2026-03-27T12:00:00",
         "Integrity declarations complete.",
         "اكتملت إقرارات النزاهة."),
        ("OBL-NCA-2026-101", "CAMP-2026-Q2", "Q2-2026", "2026-04-01", "2026-06-30", "2026-06-30",
         "ATTESTED", "2026-06-20T10:00:00",
         "ECC governance reconfirmed for Q2; no material change.",
         "أُعيد تأكيد حوكمة الضوابط الأساسية للربع الثاني دون تغيير جوهري.",
         jrefs("COM-CYBER-minutes-Q2"),
         None, None, "ACCEPTED", "2026-06-25T09:00:00",
         "Accepted.", "تم القبول."),
        ("OBL-PDPL-2026-103", "CAMP-2026-Q2", "Q2-2026", "2026-04-01", "2026-06-30", "2026-06-30",
         "IN_PROGRESS", None,
         "DSR SLA monitoring under way for the quarter.",
         "متابعة مستوى خدمة طلبات أصحاب البيانات جارية للربع.",
         None, None, None, None, None, None, None),
        ("OBL-NDMO-2026-101", "CAMP-2026-Q2", "Q2-2026", "2026-04-01", "2026-06-30", "2026-06-30",
         "OVERDUE", None,
         None, None, None, None, None, None, None, None, None),
        ("OBL-PDPL-2026-101", "CAMP-2026-ANNUAL-PDPL-NDMO", "2026", "2026-01-01", "2026-12-31", "2026-11-30",
         "IN_PROGRESS", None,
         "Annual ROPA refresh in progress across the Privacy Office.",
         "التحديث السنوي لسجل المعالجة قيد التنفيذ في مكتب حماية البيانات.",
         None, None, None, None, None, None, None),
        ("OBL-NDMO-2026-101", "CAMP-2026-ANNUAL-PDPL-NDMO", "2026", "2026-01-01", "2026-12-31", "2026-10-31",
         "DUE", None,
         None, None, None, None, None, None, None, None, None),
        ("OBL-NCA-2026-101", "CAMP-2026-Q3-ECC", "Q3-2026", "2026-07-01", "2026-09-30", "2026-09-30",
         "DUE", None,
         None, None, None, None, None, None, None, None, None),
        ("OBL-NCA-2026-103", "CAMP-2026-Q3-ECC", "Q3-2026", "2026-07-01", "2026-09-30", "2026-09-30",
         "DUE", None,
         None, None, None, None, None, None, None, None, None),
        ("OBL-NCEMA-2026-101", "CAMP-2026-ANNUAL-PDPL-NDMO", "2026", "2026-01-01", "2026-12-31", "2026-11-15",
         "ATTESTED_WITH_EXCEPTIONS", "2026-06-10T09:00:00",
         "Continuity readiness attested; recovery exercise for one critical service pending.",
         "تم الإقرار بجاهزية الاستمرارية مع تبقي تمرين تعافٍ لخدمة حرجة واحدة.",
         jrefs("EV-BCMS-BIA"),
         "Annual recovery exercise for the licensing service not yet performed.",
         "لم يُنفَّذ بعد تمرين التعافي السنوي لخدمة التراخيص.",
         "REQUEST_CHANGES", "2026-06-18T11:00:00",
         "Schedule the outstanding recovery exercise before year-end.",
         "جدولة تمرين التعافي المتبقي قبل نهاية العام."),
    ]
    for (oc, cc, plabel, pstart, pend, due, st, attat, ae, aa, ev,
         exe, exa, rdec, revat, re, ra) in ATT:
        oid = oblig_id(oc)
        if oid is None:
            print(f"  ! skip attestation (obligation missing): {oc}")
            continue
        if exists("SELECT 1 FROM compliance.obligation_attestation WHERE organisation_id=? AND obligation_id=? AND period_label=?", ORG, oid, plabel):
            print(f"  = {oc} / {plabel}")
            continue
        cid = camp_id(cc)
        attested_by = U_COMPLIANCE if attat else None
        reviewer = U_AUDITOR if rdec else None
        cur.execute(
            """INSERT INTO compliance.obligation_attestation
                 (organisation_id, obligation_id, campaign_id, period_label,
                  period_start, period_end, due_date, attestation_status,
                  attested_by_user_id, attested_at, attestation_text_en, attestation_text_ar,
                  evidence_refs, exceptions_noted_en, exceptions_noted_ar,
                  reviewer_user_id, reviewed_at, reviewer_decision,
                  reviewer_comment_en, reviewer_comment_ar,
                  created_by_user_id, created_at, created_by, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,0)""",
            ORG, oid, cid, plabel, pstart, pend, due, st,
            attested_by, attat, ae, aa, ev, exe, exa,
            reviewer, revat, rdec, re, ra, U_COMPLIANCE, U_ADMIN)
        bump("compliance.obligation_attestation")
        print(f"  + {oc} / {plabel}  [{st}]")

    # 5. compliance.regulatory_changes  (deepen) - key title_en
    print("\n# compliance.regulatory_changes")
    REGCH = [
        ("SDAIA", "NDMO National Data Management Standards - new version",
         "النسخة الجديدة من معايير إدارة البيانات الوطنية", "NDMO DMS v2",
         "Updated national data management and personal-data protection standards issued by NDMO/SDAIA.",
         "تحديث معايير إدارة البيانات الوطنية وحماية البيانات الشخصية الصادرة عن مكتب إدارة البيانات/سدايا.",
         "2026-02-10", "2026-08-01", "APPLICABLE", "HIGH", "IMPACT_ASSESSED",
         "Requires refresh of data classification, ownership and metadata controls.",
         U_COMPLIANCE),
        ("SDAIA", "SDAIA Generative-AI & AI Ethics guidelines - update",
         "تحديث ضوابط الذكاء الاصطناعي التوليدي وأخلاقيات الذكاء الاصطناعي (سدايا)", "SDAIA AI Ethics v2",
         "Updated SDAIA guidelines on responsible and generative AI, including risk-tiering of AI use-cases.",
         "تحديث ضوابط سدايا للذكاء الاصطناعي المسؤول والتوليدي بما يشمل تصنيف مخاطر حالات الاستخدام.",
         "2026-03-01", "2026-09-01", "APPLICABLE", "MEDIUM", "APPLICABILITY_ASSESSED",
         "AI use-cases must be registered and risk-tiered; ethics review required.",
         U_COMPLIANCE),
        ("SDAIA", "PDPL Implementing Regulation - amendment",
         "تعديل اللائحة التنفيذية لنظام حماية البيانات الشخصية", "PDPL IR Amendment 2026",
         "Amendment to the PDPL Implementing Regulation affecting breach notification and DSR timelines.",
         "تعديل على اللائحة التنفيذية لنظام حماية البيانات يؤثر على الإبلاغ عن الانتهاكات وطلبات أصحاب البيانات.",
         "2026-01-20", "2026-07-01", "APPLICABLE", "HIGH", "CONTROLS_UPDATED",
         "Breach-notification workflow and DSR SLA updated to match the amendment.",
         U_COMPLIANCE),
        ("NCA", "NCA ECC-2:2024 transition circular",
         "تعميم الانتقال إلى الضوابط الأساسية ECC-2:2024", "NCA ECC-2 Transition",
         "Circular setting the transition timeline from ECC-1:2018 to ECC-2:2024 for government entities.",
         "تعميم يحدد الجدول الزمني للانتقال من ECC-1:2018 إلى ECC-2:2024 للجهات الحكومية.",
         "2025-12-15", "2026-01-01", "APPLICABLE", "HIGH", "CONTROLS_UPDATED",
         "Control library and self-assessment migrated to ECC-2:2024.",
         U_COMPLIANCE),
        ("DGA", "DGA digital-services & open-data policy - new",
         "السياسة الجديدة للخدمات الرقمية والبيانات المفتوحة (الحكومة الرقمية)", "DGA DSP 2026",
         "New Digital Government Authority policy on digital services standards and open-data publishing.",
         "سياسة جديدة لهيئة الحكومة الرقمية بشأن معايير الخدمات الرقمية ونشر البيانات المفتوحة.",
         "2026-02-25", "2026-06-01", "APPLICABLE", "MEDIUM", "APPLICABILITY_ASSESSED",
         "Beneficiary services and open-data cadence must align with the new standards.",
         U_RANALYST),
        ("OTHER", "Nazaha anti-corruption & disclosure regulation - update",
         "تحديث لائحة مكافحة الفساد والإفصاح (نزاهة)", "Nazaha Reg 2026",
         "Updated Nazaha regulation strengthening conflict-of-interest disclosure and whistleblower protection.",
         "لائحة محدثة من نزاهة تعزز الإفصاح عن تعارض المصالح وحماية المبلغين.",
         "2026-03-10", "2026-09-01", "APPLICABLE", "MEDIUM", "NEW",
         None, U_COMPLIANCE),
        ("NCEMA", "NCEMA national crisis-readiness directive",
         "توجيه المركز الوطني لجاهزية الأزمات", "NCEMA Directive 2026",
         "Directive requiring annual crisis-readiness returns and continuity exercises for government entities.",
         "توجيه يتطلب تقارير سنوية لجاهزية الأزمات وتمارين استمرارية للجهات الحكومية.",
         "2026-02-05", "2026-07-01", "PARTIAL", "MEDIUM", "APPLICABILITY_ASSESSED",
         "Adds an annual readiness return on top of the existing BCMS exercises.",
         U_RANALYST),
        ("DGA", "Government e-procurement & Etimad integration update",
         "تحديث المنافسات والمشتريات الحكومية والتكامل مع اعتماد", "Etimad Update 2026",
         "Update to government procurement rules requiring tighter integration with the Etimad platform.",
         "تحديث أنظمة المشتريات الحكومية يتطلب تكاملاً أوثق مع منصة اعتماد.",
         "2026-01-30", "2026-05-01", "PARTIAL", "LOW", "CLOSED",
         "Procurement workflows aligned with Etimad; no further action.",
         U_COMPLIANCE),
    ]
    for (auth, te, ta, cit, se, sa, pub, eff, appl, imp, st, isum, asg) in REGCH:
        if exists("SELECT 1 FROM compliance.regulatory_changes WHERE organisation_id=? AND title_en=?", ORG, te):
            print(f"  = {te}")
            continue
        closed_at = "2026-05-15T00:00:00" if st == "CLOSED" else None
        cur.execute(
            """INSERT INTO compliance.regulatory_changes
                 (organisation_id, source_authority, title_en, title_ar, citation,
                  summary_en, summary_ar, published_date, effective_date,
                  applicability, impact_level, status, impact_summary,
                  assigned_to_id, closed_at, created_at, created_by, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,0)""",
            ORG, auth, te, ta, cit, se, sa, pub, eff, appl, imp, st, isum,
            asg, closed_at, U_ADMIN)
        bump("compliance.regulatory_changes")
        print(f"  + {te}")

    # 6. regchange.changes  (deepen) - key external_id
    print("\n# regchange.changes")
    RCH = [
        ("RC-NDMO-DC-2026", "SDAIA", "NDMO Data Classification v2",
         "NDMO data-classification standard refresh",
         "تحديث معيار تصنيف البيانات (مكتب إدارة البيانات)",
         "Refreshed national data-classification standard with revised classification levels.",
         "تحديث معيار تصنيف البيانات الوطنية بمستويات تصنيف معدّلة.",
         "https://sdaia.gov.sa/ndmo", "2026-02-12", "2026-08-01",
         "IMPACT_ASSESSED", "APPLICABLE", 4, "Data Governance Office"),
        ("RC-DGA-NAFATH-2026", "DGA", "DGA Nafath Directive 2026",
         "DGA digital-identity / Nafath integration directive",
         "توجيه التكامل مع نفاذ والهوية الرقمية (الحكومة الرقمية)",
         "Directive mandating Nafath integration for beneficiary-facing digital services.",
         "توجيه يلزم بالتكامل مع نفاذ للخدمات الرقمية الموجهة للمستفيدين.",
         "https://dga.gov.sa", "2026-01-25", "2026-06-01",
         "PLANNING", "APPLICABLE", 3, "Digital Transformation"),
        ("RC-NAZAHA-FW-2026", "OTHER", "Nazaha Integrity Framework 2026",
         "Nazaha integrity-framework update",
         "تحديث إطار النزاهة (نزاهة)",
         "Updated national integrity framework with enhanced conflict-of-interest controls.",
         "تحديث الإطار الوطني للنزاهة بضوابط معززة لتعارض المصالح.",
         "https://nazaha.gov.sa", "2026-03-08", "2026-09-01",
         "NEW", "APPLICABLE", 3, "Compliance Department"),
        ("RC-NCEMA-EX-2026", "NCEMA", "NCEMA Exercise Mandate 2026",
         "NCEMA business-continuity exercise mandate",
         "إلزام تمارين استمرارية الأعمال (المركز الوطني)",
         "Mandate requiring annual continuity exercises with NCEMA-reported results.",
         "إلزام بتنفيذ تمارين استمرارية سنوية ترفع نتائجها للمركز الوطني.",
         "https://ncema.gov.sa", "2026-02-03", "2026-07-01",
         "PLANNING", "APPLICABLE", 3, "Business Continuity"),
        ("RC-SDAIA-AIPOL-2026", "SDAIA", "SDAIA National AI Policy 2026",
         "SDAIA national AI policy",
         "السياسة الوطنية للذكاء الاصطناعي (سدايا)",
         "National AI policy update covering responsible AI adoption and governance.",
         "تحديث السياسة الوطنية للذكاء الاصطناعي يشمل التبني المسؤول والحوكمة.",
         "https://sdaia.gov.sa/ai", "2026-03-15", "2026-10-01",
         "NEW", "APPLICABLE", 2, "Data Governance Office"),
        ("RC-PDPL-XBORDER-2026", "SDAIA", "PDPL Cross-Border Rule 2026",
         "PDPL cross-border data-transfer rule",
         "قاعدة نقل البيانات عبر الحدود (نظام حماية البيانات)",
         "Updated rule on cross-border personal-data transfers and adequacy decisions.",
         "قاعدة محدثة لنقل البيانات الشخصية عبر الحدود وقرارات الكفاية.",
         "https://sdaia.gov.sa/pdpl", "2026-01-18", "2026-07-01",
         "IMPACT_ASSESSED", "APPLICABLE", 4, "Privacy Office"),
    ]
    for (ext, reg, cit, te, ta, se, sa, url, pub, eff, st, appl, score, owner) in RCH:
        if exists("SELECT 1 FROM regchange.changes WHERE organisation_id=? AND external_id=?", ORG, ext):
            print(f"  = {ext}")
            continue
        cur.execute(
            """INSERT INTO regchange.changes
                 (external_id, organisation_id, regulator, citation, title_en, title_ar,
                  summary_en, summary_ar, source_url, published_at, effective_at,
                  status, applicability, impact_score, implementation_owner,
                  created_at, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),0)""",
            ext, ORG, reg, cit, te, ta, se, sa, url, pub, eff, st, appl, score, owner)
        bump("regchange.changes")
        print(f"  + {ext}  {te}")

    # 7. compliance.audit_engagements  (deepen) - key engagement_code
    print("\n# compliance.audit_engagements")
    AUD = [
        ("AUD-2026-001", "NCA ECC-2:2024 Regulatory Compliance Assessment",
         "تقييم الالتزام التنظيمي بالضوابط الأساسية ECC-2:2024", "REGULATORY",
         "NCA_ECC", "NCA-authorised cybersecurity assessor", "Lead Assessor - Cyber GRC",
         "FIELDWORK", "2026-04-01", "2026-04-15", "2026-05-01", "2026-06-15", None, None,
         "Assessment of the Authority's compliance with NCA Essential Cybersecurity Controls ECC-2:2024 across all five domains."),
        ("AUD-2026-002", "NDMO / SDAIA National Data Management Maturity Assessment",
         "تقييم نضج إدارة البيانات الوطنية (مكتب إدارة البيانات / سدايا)", "REGULATORY",
         "NDMO", "NDMO-accredited data assessor", "Lead Assessor - Data Governance",
         "REPORTING", "2026-02-01", "2026-02-20", "2026-03-01", "2026-04-10", "2026-05-20", None,
         "Maturity assessment against the NDMO National Data Management standards and data classification."),
        ("AUD-2026-003", "PDPL Compliance Review - Data Protection Office",
         "مراجعة الالتزام بنظام حماية البيانات الشخصية - مكتب حماية البيانات", "INTERNAL",
         "PDPL", "Internal Audit", "Head of Internal Audit",
         "MGMT_RESPONSE", "2026-03-01", "2026-03-10", "2026-03-15", "2026-04-30", "2026-06-01", None,
         "Internal review of PDPL compliance: ROPA, DSR handling, breach notification and cross-border transfers."),
        ("AUD-2026-004", "Nazaha Integrity & Anti-Corruption Programme Review",
         "مراجعة برنامج النزاهة ومكافحة الفساد (نزاهة)", "INTERNAL",
         "NAZAHA", "Internal Audit", "Head of Internal Audit",
         "FIELDWORK", "2026-05-01", "2026-05-12", "2026-05-15", None, None, None,
         "Review of integrity controls, conflict-of-interest disclosures and whistleblowing aligned with Nazaha."),
        ("AUD-2026-005", "General Auditing Bureau (GCA) External Engagement",
         "ارتباط ديوان المراجعة العامة الخارجي", "EXTERNAL",
         "COSO_ERM", "General Auditing Bureau (GCA)", "GCA Audit Team",
         "OPENING_MEETING", "2026-06-01", "2026-06-20", None, None, None, None,
         "External audit by the General Auditing Bureau covering financial and compliance controls."),
    ]
    for (code, te, ta, typ, fw, firm, lead, st, el, om, fs, fe, ri, cl, scope) in AUD:
        if exists("SELECT 1 FROM compliance.audit_engagements WHERE organisation_id=? AND engagement_code=?", ORG, code):
            print(f"  = {code}")
            continue
        cur.execute(
            """INSERT INTO compliance.audit_engagements
                 (organisation_id, engagement_code, title_en, title_ar, audit_type,
                  framework, auditor_firm, lead_auditor, engagement_letter_date,
                  opening_meeting_date, fieldwork_start_date, fieldwork_end_date,
                  report_issued_date, closure_date, scope_summary, status,
                  findings_count, created_at, created_by, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,SYSUTCDATETIME(),?,0)""",
            ORG, code, te, ta, typ, fw, firm, lead, el, om, fs, fe, ri, cl, scope, st, U_ADMIN)
        bump("compliance.audit_engagements")
        print(f"  + {code}  {te}")

    # 8. compliance.compliance_assessments (deepen) - key (org, name); fw->platform.control_frameworks
    print("\n# compliance.compliance_assessments")
    CA = [
        ("2026 - NCA ECC-2:2024 control self-assessment", "NCA_ECC", "INTERNAL",
         "IN_PROGRESS", "2026-04-01", "2026-06-30", None, 114, 78, 18),
        ("2026 - Saudi PDPL compliance self-assessment", "SAUDI_PDPL", "INTERNAL",
         "COMPLETED", "2026-02-01", "2026-03-31", 82.5, 40, 33, 7),
        ("2026 - ISO/IEC 27701 PIMS readiness self-assessment", "ISO_27701", "INTERNAL",
         "UNDER_REVIEW", "2026-03-01", "2026-04-30", 74.0, 49, 36, 13),
        ("2026 - ISO 22301 BCMS readiness self-assessment", "ISO_22301", "INTERNAL",
         "IN_PROGRESS", "2026-04-15", "2026-07-15", None, 32, 20, 6),
    ]
    for (name, pfwc, typ, st, sd, ed, score, total, comp, noncomp) in CA:
        if exists("SELECT 1 FROM compliance.compliance_assessments WHERE organisation_id=? AND assessment_name_en=?", ORG, name):
            print(f"  = {name}")
            continue
        fwid = PFW.get(pfwc)
        cur.execute(
            """INSERT INTO compliance.compliance_assessments
                 (organisation_id, framework_id, assessment_name_en, assessment_type,
                  assessor_id, start_date, end_date, status, overall_score,
                  total_controls, compliant_count, non_compliant_count,
                  created_at, updated_at, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),SYSUTCDATETIME(),0)""",
            ORG, fwid, name, typ, U_COMPLIANCE, sd, ed, st, score, total, comp, noncomp)
        bump("compliance.compliance_assessments")
        print(f"  + {name}")

    def ca_id(name):
        cur.execute("SELECT id FROM compliance.compliance_assessments WHERE organisation_id=? AND assessment_name_en=?", ORG, name)
        r = cur.fetchone()
        return r[0] if r else None

    # 9. compliance.compliance_findings  (fill) - key (assessment_id, title_en)
    print("\n# compliance.compliance_findings")
    CF = [
        ("2026 - NCA ECC-2:2024 control self-assessment", "NON_CONFORMITY", "HIGH",
         "Asset inventory incomplete (ECC 2-1)",
         "The authoritative asset inventory does not yet cover all information and technology assets; gaps in OT assets.",
         "OPEN", "2026-09-30"),
        ("2026 - NCA ECC-2:2024 control self-assessment", "OBSERVATION", "MEDIUM",
         "Cybersecurity roles RACI partially documented (ECC 1-3)",
         "Roles and responsibilities are defined but the RACI matrix is not fully ratified for all units.",
         "IN_PROGRESS", "2026-08-31"),
        ("2026 - Saudi PDPL compliance self-assessment", "NON_CONFORMITY", "HIGH",
         "ROPA gaps for new beneficiary services",
         "Record of Processing Activities is missing entries for two recently launched beneficiary services.",
         "OPEN", "2026-09-15"),
        ("2026 - Saudi PDPL compliance self-assessment", "OBSERVATION", "LOW",
         "DSR SLA tracking not automated",
         "Data-subject-request handling is tracked manually; recommend workflow automation to ensure SLA adherence.",
         "OPEN", "2026-10-31"),
        ("2026 - ISO/IEC 27701 PIMS readiness self-assessment", "NON_CONFORMITY", "MEDIUM",
         "PIMS scope statement not finalised",
         "The privacy information management system scope statement is in draft and not yet approved.",
         "IN_PROGRESS", "2026-07-31"),
        ("2026 - ISO 22301 BCMS readiness self-assessment", "OBSERVATION", "MEDIUM",
         "Business impact analysis outdated",
         "The BIA for critical beneficiary services has not been refreshed within the last review cycle.",
         "OPEN", "2026-08-15"),
    ]
    for (aname, ftype, sev, te, de, st, due) in CF:
        aid = ca_id(aname)
        if aid is None:
            print(f"  ! skip finding (assessment missing): {aname}")
            continue
        if exists("SELECT 1 FROM compliance.compliance_findings WHERE assessment_id=? AND title_en=?", aid, te):
            print(f"  = {te}")
            continue
        cur.execute(
            """INSERT INTO compliance.compliance_findings
                 (assessment_id, finding_type, severity, title_en, description_en,
                  status, owner_id, due_date, created_at, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,SYSUTCDATETIME(),0)""",
            aid, ftype, sev, te, de, st, U_COMPLIANCE, due)
        bump("compliance.compliance_findings")
        print(f"  + {te}")

    # 10. compliance.findings  (deepen) - key (org, title). CHECK severity/status.
    print("\n# compliance.findings")
    FIND = [
        ("NCA ECC - OT network segmentation evidence missing", "HIGH", "IN_PROGRESS",
         "IMPLEMENTING", "Information Technology", "2026-09-30",
         "Implement and evidence OT/IT network segmentation; capture diagrams and firewall rule export.",
         "NCA_ECC", "ECC 2-10"),
        ("NCA ECC - cybersecurity roles RACI gap", "MEDIUM", "OPEN",
         "IDENTIFIED", "CISO Office", "2026-08-31",
         "Ratify the cybersecurity RACI matrix across all organisational units.",
         "NCA_ECC", "ECC 1-3"),
        ("NCA ECC - asset inventory incomplete", "HIGH", "IN_PROGRESS",
         "OWNER_TESTED", "Information Technology", "2026-09-30",
         "Complete authoritative asset inventory including OT assets; owner test passed, awaiting independent test.",
         "NCA_ECC", "ECC 2-1"),
        ("NDMO - national data classification incomplete", "MEDIUM", "IN_PROGRESS",
         "IMPLEMENTING", "Data Governance Office", "2026-10-31",
         "Classify all data assets per the national data classification policy.",
         "NCA_ECC", "NDMO DM"),
        ("NDMO - data-quality KPIs absent", "MEDIUM", "OPEN",
         "IDENTIFIED", "Data Governance Office", "2026-11-30",
         "Define and baseline data-quality KPIs for priority data domains.",
         "NCA_ECC", "NDMO DQ"),
        ("PDPL - ROPA not fully populated", "HIGH", "REMEDIATED",
         "AUDIT_SIGNED_OFF", "Privacy Office", "2026-07-31",
         "Populate ROPA for all processing activities; remediation completed and signed off by Internal Audit.",
         "PDPL", "PDPL ROPA"),
        ("PDPL - data-subject request SLA breaches", "MEDIUM", "IN_PROGRESS",
         "INDEPENDENT_TESTED", "Privacy Office", "2026-09-30",
         "Reduce DSR handling time within SLA; independent test in progress.",
         "PDPL", "PDPL DSR"),
        ("Nazaha - conflict-of-interest declarations outstanding", "MEDIUM", "OPEN",
         "IDENTIFIED", "Compliance Department", "2026-12-15",
         "Collect outstanding conflict-of-interest declarations from all decision-makers.",
         "NAZAHA", "Integrity"),
    ]
    for (title, sev, st, capa_stage, owner, due, capa, fwc, clause) in FIND:
        if exists("SELECT 1 FROM compliance.findings WHERE organisation_id=? AND title=?", ORG, title):
            print(f"  = {title}")
            continue
        fwid = FW.get(fwc)
        owner_test_at = "2026-06-01T00:00:00" if capa_stage in ("OWNER_TESTED", "INDEPENDENT_TESTED", "AUDIT_SIGNED_OFF") else None
        owner_test_result = "PASS" if owner_test_at else None
        indep_at = "2026-06-15T00:00:00" if capa_stage in ("INDEPENDENT_TESTED", "AUDIT_SIGNED_OFF") else None
        indep_by = U_AUDITOR if indep_at else None
        indep_result = "PASS" if indep_at else None
        signoff_at = "2026-06-20T00:00:00" if capa_stage == "AUDIT_SIGNED_OFF" else None
        signoff_by = U_AUDITOR if signoff_at else None
        cur.execute(
            """INSERT INTO compliance.findings
                 (organisation_id, title, severity, owner, capa, due_date, status,
                  capa_stage, framework_id, clause_ref,
                  owner_test_at, owner_test_result,
                  independent_test_at, independent_test_by_id, independent_test_result,
                  audit_signoff_at, audit_signoff_by_id,
                  created_by, created_at, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),0)""",
            ORG, title, sev, owner, capa, due, st, capa_stage, fwid, clause,
            owner_test_at, owner_test_result,
            indep_at, indep_by, indep_result,
            signoff_at, signoff_by, U_ADMIN)
        bump("compliance.findings")
        print(f"  + {title}  [{sev}/{st}/{capa_stage}]")

    # 11. compliance.policies  (deepen) - key code
    print("\n# compliance.policies")
    POL = [
        ("POL-DG-001", "Data Governance & Data Management Policy",
         "سياسة حوكمة وإدارة البيانات", "DATA_GOVERNANCE", "PUBLISHED", "1.0",
         "2026-03-01", "2027-03-01",
         "Establishes data ownership, stewardship, quality and lifecycle management aligned with NDMO standards.",
         "تحدد ملكية البيانات والإشراف عليها وجودتها وإدارة دورة حياتها بما يتوافق مع معايير مكتب إدارة البيانات الوطنية."),
        ("POL-DC-001", "Data Classification & Handling Policy",
         "سياسة تصنيف ومعالجة البيانات", "DATA_GOVERNANCE", "EXEC_APPROVAL", "0.9",
         None, "2027-04-01",
         "Defines data classification levels and handling requirements per SDAIA/NDMO national classification.",
         "تحدد مستويات تصنيف البيانات ومتطلبات التعامل معها وفق التصنيف الوطني (سدايا/مكتب إدارة البيانات)."),
        ("POL-AI-ETHICS-001", "AI Ethics & Responsible-AI Policy",
         "سياسة أخلاقيات الذكاء الاصطناعي والذكاء الاصطناعي المسؤول", "INFORMATION_SECURITY",
         "LEGAL_REVIEW", "0.5", None, "2027-06-01",
         "Sets principles for fair, transparent and accountable AI use aligned with SDAIA AI ethics principles.",
         "تضع مبادئ الاستخدام العادل والشفاف والمسؤول للذكاء الاصطناعي بما يتوافق مع مبادئ سدايا."),
        ("POL-NAZAHA-001", "Anti-Corruption, Integrity & Conflict-of-Interest Policy",
         "سياسة مكافحة الفساد والنزاهة وتعارض المصالح", "RISK_MANAGEMENT", "PUBLISHED", "1.0",
         "2026-02-15", "2027-02-15",
         "Defines integrity expectations, conflict-of-interest disclosure and whistleblowing aligned with Nazaha.",
         "تحدد متطلبات النزاهة وإقرار تعارض المصالح والإبلاغ عن المخالفات بما يتوافق مع نزاهة."),
        ("POL-OPENDATA-001", "Open Data & Digital Government Policy",
         "سياسة البيانات المفتوحة والحكومة الرقمية", "DATA_GOVERNANCE", "DRAFT", "0.3",
         None, "2027-05-01",
         "Governs open-data publishing, digital-services standards and Nafath integration per DGA policy.",
         "تحوكم نشر البيانات المفتوحة ومعايير الخدمات الرقمية والتكامل مع نفاذ وفق سياسة هيئة الحكومة الرقمية."),
    ]
    for (code, te, ta, cat, st, ver, eff, nrev, ce, ca) in POL:
        if exists("SELECT 1 FROM compliance.policies WHERE organisation_id=? AND code=?", ORG, code):
            print(f"  = {code}")
            continue
        published_at = "2026-03-01T00:00:00" if st == "PUBLISHED" else None
        approver = U_AUDITOR if st in ("PUBLISHED", "EXEC_APPROVAL") else None
        approved_at = "2026-02-25T00:00:00" if st == "PUBLISHED" else None
        cur.execute(
            """INSERT INTO compliance.policies
                 (organisation_id, code, title_en, title_ar, version, category,
                  content_en, content_ar, status, author_id, approver_id,
                  approved_at, published_at, effective_from, next_review_date,
                  created_at, created_by, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,0)""",
            ORG, code, te, ta, ver, cat, ce, ca, st, U_COMPLIANCE, approver,
            approved_at, published_at, eff, nrev, U_ADMIN)
        bump("compliance.policies")
        print(f"  + {code}  {te}")

    def policy_id(code):
        cur.execute("SELECT id FROM compliance.policies WHERE organisation_id=? AND code=?", ORG, code)
        r = cur.fetchone()
        return r[0] if r else None

    # 12. compliance.policy_framework_mappings (deepen); fw->compliance.frameworks
    #     key (policy_id, framework_id, clause_ref)
    print("\n# compliance.policy_framework_mappings")
    PFM = [
        ("POL-DG-001", "NCA_ECC", "ECC 1-1", "PARTIAL",
         "Data governance supports cybersecurity governance objectives."),
        ("POL-DG-001", "PDPL", "Art. 5", "FULL",
         "Data management lifecycle aligns with PDPL data-processing principles."),
        ("POL-DC-001", "PDPL", "Art. 19", "PARTIAL",
         "Classification supports data-protection by design."),
        ("POL-AI-ETHICS-001", "ISO_27701", "7.2", "PARTIAL",
         "AI ethics references privacy-by-design controls."),
        ("POL-NAZAHA-001", "NAZAHA", "Integrity", "FULL",
         "Directly implements the Nazaha integrity framework."),
        ("POL-OPENDATA-001", "NCA_ECC", "ECC 2-1", "PARTIAL",
         "Open-data publishing constrained by asset/data classification controls."),
    ]
    for (pcode, fwc, clause, sat, notes) in PFM:
        pid = policy_id(pcode)
        fwid = FW.get(fwc)
        if pid is None or fwid is None:
            print(f"  ! skip mapping (policy/framework missing): {pcode}/{fwc}")
            continue
        if exists("SELECT 1 FROM compliance.policy_framework_mappings WHERE policy_id=? AND framework_id=? AND clause_ref=?", pid, fwid, clause):
            print(f"  = {pcode} -> {fwc} {clause}")
            continue
        cur.execute(
            """INSERT INTO compliance.policy_framework_mappings
                 (policy_id, framework_id, clause_ref, satisfaction, notes,
                  created_at, created_by, is_deleted)
               VALUES (?,?,?,?,?,SYSUTCDATETIME(),?,0)""",
            pid, fwid, clause, sat, notes, U_ADMIN)
        bump("compliance.policy_framework_mappings")
        print(f"  + {pcode} -> {fwc} {clause}")

    # 13. compliance.evidence_items  (deepen) - key (org, title_en)
    print("\n# compliance.evidence_items")
    EV = [
        ("NCA ECC governance charter & board-approval minutes", "DOCUMENT",
         "2026-03-20", "2027-03-20", 1, "NCA-ECC-1-3",
         "2026 - NCA ECC-2:2024 control self-assessment", None),
        ("NDMO data-classification register export", "REPORT",
         "2026-03-10", "2026-09-10", 1, "NDMO-DC-1", None, None),
        ("PDPL Record of Processing Activities (ROPA) document", "DOCUMENT",
         "2026-03-22", "2027-03-22", 0, "PDPL-ROPA",
         "2026 - Saudi PDPL compliance self-assessment", "ROPA gaps for new beneficiary services"),
        ("Data-subject request (DSR) handling log extract", "LOG",
         "2026-06-01", "2026-12-01", 1, "PDPL-ROPA", None, None),
        ("ISO 27701 PIMS scope statement", "DOCUMENT",
         "2026-04-01", "2027-04-01", 0, None,
         "2026 - ISO/IEC 27701 PIMS readiness self-assessment", "PIMS scope statement not finalised"),
        ("ISO 22301 business impact analysis (BIA) report", "REPORT",
         "2026-04-20", "2027-04-20", 0, None,
         "2026 - ISO 22301 BCMS readiness self-assessment", "Business impact analysis outdated"),
        ("Nazaha conflict-of-interest declaration register", "DOCUMENT",
         "2026-03-15", "2027-03-15", 1, "NAZAHA-INT-1", None, None),
        ("NCA ECC-2:2024 self-assessment workbook", "REPORT",
         "2026-06-15", "2027-06-15", 0, "NCA-ECC-1-4",
         "2026 - NCA ECC-2:2024 control self-assessment", None),
    ]

    def cf_id(aname, title):
        if not aname or not title:
            return None
        aid = ca_id(aname)
        if aid is None:
            return None
        cur.execute("SELECT id FROM compliance.compliance_findings WHERE assessment_id=? AND title_en=?", aid, title)
        r = cur.fetchone()
        return r[0] if r else None

    for (te, etype, coll, exp, suff, cctrl, ca_name, cf_title) in EV:
        if exists("SELECT 1 FROM compliance.evidence_items WHERE organisation_id=? AND title_en=?", ORG, te):
            print(f"  = {te}")
            continue
        cid = control_id(cctrl) if cctrl else None
        aid = ca_id(ca_name) if ca_name else None
        fid = cf_id(ca_name, cf_title)
        cur.execute(
            """INSERT INTO compliance.evidence_items
                 (organisation_id, control_id, assessment_id, finding_id,
                  evidence_type, title_en, collection_date, expiry_date,
                  uploaded_by, is_sufficient, created_at, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),0)""",
            ORG, cid, aid, fid, etype, te, coll, exp, U_COMPLIANCE, suff)
        bump("compliance.evidence_items")
        print(f"  + {te}")

    # Verification + summary
    print("\n" + "=" * 70)
    print("INSERTED THIS RUN:")
    total = 0
    for t in sorted(_ins):
        print(f"  {t:<44} +{_ins[t]}")
        total += _ins[t]
    print(f"  {'TOTAL':<44} +{total}")

    print("\nSKIPPED (reported, untouched): compliance.assessments, "
          "compliance.frameworks, nca_ecc.filings")

    print("\nVERIFICATION (org 1 counts):")
    VTABLES = [
        "compliance.controls", "compliance.obligation",
        "compliance.obligation_attestation_campaign", "compliance.obligation_attestation",
        "compliance.regulatory_changes", "regchange.changes",
        "compliance.audit_engagements", "compliance.compliance_assessments",
        "compliance.findings", "compliance.policies", "compliance.evidence_items",
    ]
    for t in VTABLES:
        cur.execute(f"SELECT COUNT(*) FROM {t} WHERE organisation_id=1")
        print(f"  {t:<44} {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM compliance.policy_framework_mappings m JOIN compliance.policies p ON p.id=m.policy_id WHERE p.organisation_id=1")
    print(f"  {'compliance.policy_framework_mappings (org1)':<44} {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM compliance.compliance_findings f JOIN compliance.compliance_assessments a ON a.id=f.assessment_id WHERE a.organisation_id=1")
    print(f"  {'compliance.compliance_findings (org1)':<44} {cur.fetchone()[0]}")

    conn.close()
    print("\nDone.")


if __name__ == "__main__":
    main()
