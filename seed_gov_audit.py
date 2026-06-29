# -*- coding: utf-8 -*-
"""Seed a Saudi GENERAL AUTHORITY (هيئة عامة) internal-audit layer into the audit
domain for the default tenant (organisation_id = 1).

Domain owner: Internal Audit Office (platform.departments code INTERNAL_AUD, id 7).
Committee tie: Audit Committee (admin.committees code COM-AUDIT, id 3). Neither tie
is a hard FK in these tables, so it is carried in text fields (owner_role,
audit_committee_chair, ac_approved_by) and in the Audit-Committee dashboard
snapshots / attestations.

The existing org-1 audit data is SAUDI-BANK themed (AML/KYC, PAM, SADAD, SAMA).
This script does NOT touch it. It ADDS a parallel GOVERNMENT-AUTHORITY audit cycle
(FY2027) anchored to NCA ECC, NDMO, PDPL, NCEMA, DGA, Etimad and Nazaha.

Two parallel engagement systems coexist in the schema and BOTH are populated:
  (A) ia_plan set (FK-linked, IDENTITY ids): ia_annual_plan <- ia_audit_universe_entry,
      ia_engagement (plan_id NOT NULL + universe_entry_id), ia_engagement_milestone.
  (B) legacy set (string engagement_code links, no DB FK): audit_eng.engagements,
      audit_lc.findings, capa_tasks, working_papers, annual_plans.
NEW findings/working-papers/capa reference NEW audit_eng.engagements rows this script
inserts, closing the existing orphan gap.

SKIPPED: audit.audit_logs — the platform's append-only, hash-chained tamper-evident
security trail. Seeding it would break the chain external auditors verify; it is
written only by the runtime audit interceptor. Excluded by design.

Idempotent: every INSERT is guarded by a natural key. A second run inserts 0.
INSERT/UPSERT only — no DELETE/TRUNCATE/DROP/ALTER. organisation_id = 1 only.

    Usage:  python seed_gov_audit.py
"""
from __future__ import annotations

import hashlib
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
U_ADMIN = 1       # Head of Internal Audit / Audit Director / approver / sponsor
U_AUDITOR = 4     # lead auditor
U_ANALYST = 5     # audit team member

OWNER_IA = "Internal Audit Office / مكتب المراجعة الداخلية"
AC_CHAIR = "Audit Committee Chair (COM-AUDIT) / رئيس لجنة المراجعة"

counts: dict[str, int] = {}


def bump(table: str, n: int = 1) -> None:
    counts[table] = counts.get(table, 0) + n


def x(*codes):
    return json.dumps(list(codes), ensure_ascii=False)


# ─────────────────────────────────────────────────────────────────────────────
# A. ia_annual_plan
# ─────────────────────────────────────────────────────────────────────────────
def seed_ia_annual_plan(cur) -> int:
    method_en = ("Risk-based annual internal audit plan for the Authority, built on "
                 "the audit universe and prioritised by inherent and residual risk, "
                 "aligned with the IIA International Standards and the General Court of "
                 "Audit (GCA) expectations, and approved by the Audit Committee.")
    method_ar = ("الخطة السنوية للمراجعة الداخلية للهيئة على أساس المخاطر، مبنية على "
                 "نطاق المراجعة الشامل ومرتبة وفق المخاطر الكامنة والمتبقية، بما يتوافق "
                 "مع معايير المراجعة الداخلية الدولية وتوقعات الديوان العام للمحاسبة، "
                 "ومعتمدة من لجنة المراجعة.")
    cur.execute("SELECT id FROM audit_lc.ia_annual_plan WHERE organisation_id=? AND plan_code='IAP-2027'", ORG)
    row = cur.fetchone()
    if row is None:
        cur.execute(
            """INSERT INTO audit_lc.ia_annual_plan
                 (organisation_id, plan_code, cycle_year, title_en, title_ar,
                  prepared_by_user_id, status, methodology_summary_en, methodology_summary_ar,
                  audit_universe_count, total_engagements_planned,
                  approved_by_user_id, approved_at, created_by_user_id, created_by, updated_by)
               VALUES (?, 'IAP-2027', 2027, ?, ?, ?, 'APPROVED', ?, ?, 16, 7,
                       ?, '2026-12-15T09:00:00', ?, ?, ?)""",
            ORG,
            "2027 Internal Audit Annual Plan",
            "الخطة السنوية للمراجعة الداخلية 2027",
            U_ADMIN, method_en, method_ar, U_ADMIN, U_ADMIN, U_ADMIN, U_ADMIN,
        )
        bump("audit_lc.ia_annual_plan")
        print("  + audit_lc.ia_annual_plan IAP-2027")
    else:
        print("  = audit_lc.ia_annual_plan IAP-2027 (exists)")
    cur.execute(
        """UPDATE audit_lc.ia_annual_plan
              SET methodology_summary_en = COALESCE(methodology_summary_en, ?),
                  methodology_summary_ar = COALESCE(methodology_summary_ar, ?)
            WHERE organisation_id=? AND plan_code='IAP-2026'
              AND (methodology_summary_en IS NULL OR methodology_summary_ar IS NULL)""",
        method_en, method_ar, ORG)
    cur.execute("SELECT id FROM audit_lc.ia_annual_plan WHERE organisation_id=? AND plan_code='IAP-2027'", ORG)
    return cur.fetchone()[0]


# ─────────────────────────────────────────────────────────────────────────────
# B. ia_audit_universe_entry  (16 government processes)
# ─────────────────────────────────────────────────────────────────────────────
UNIVERSE = [
    ("AU-GOV-LICENSING-001", "Licensing & Permits Issuance", "إصدار التراخيص والتصاريح",
     "Licence application intake, review, approval and issuance", "استقبال ودراسة واعتماد وإصدار طلبات الترخيص",
     "Licensing Department", "إدارة الترخيص", 21.0, 0.45, 2027, "Q1", "PARTIAL", True),
    ("AU-GOV-INSPECTION-002", "Field Inspection & Enforcement", "التفتيش والإنفاذ الميداني",
     "Risk-based inspection scheduling, findings and enforcement actions", "جدولة التفتيش على أساس المخاطر والنتائج وإجراءات الإنفاذ",
     "Inspection Department", "إدارة التفتيش", 20.0, 0.50, 2027, "Q3", None, True),
    ("AU-GOV-BENEFICIARY-003", "Beneficiary Services & Grievances", "خدمات المستفيدين والشكاوى",
     "Beneficiary service requests, SLAs and grievance handling", "طلبات خدمات المستفيدين واتفاقيات مستوى الخدمة ومعالجة الشكاوى",
     "Beneficiary Services Department", "إدارة خدمات المستفيدين", 18.0, 0.55, 2027, "Q4", None, True),
    ("AU-GOV-GRANTS-004", "Grants & Subsidies Disbursement", "صرف المنح والإعانات",
     "Eligibility, approval and disbursement of grants and subsidies", "الأهلية واعتماد وصرف المنح والإعانات",
     "Finance Department", "الإدارة المالية", 22.0, 0.40, 2027, "Q2", None, True),
    ("AU-GOV-PROCUREMENT-005", "Procurement via Etimad & Contract Management", "المشتريات عبر اعتماد وإدارة العقود",
     "Tendering through Etimad, award and contract administration", "المنافسات عبر منصة اعتماد والترسية وإدارة العقود",
     "Procurement Department", "إدارة المشتريات", 21.5, 0.42, 2027, "Q1", "PARTIAL", True),
    ("AU-GOV-PAYROLL-006", "Payroll & HR Entitlements", "الرواتب والمستحقات الوظيفية",
     "Payroll processing, allowances and end-of-service entitlements", "معالجة الرواتب والبدلات ومستحقات نهاية الخدمة",
     "Human Resources Department", "إدارة الموارد البشرية", 18.5, 0.55, 2027, "Q2", None, True),
    ("AU-GOV-BUDGET-007", "Budget Execution & Financial Reporting", "تنفيذ الميزانية والتقارير المالية",
     "Budget allocation, commitment control and financial reporting", "تخصيص الميزانية وضبط الارتباطات والتقارير المالية",
     "Finance Department", "الإدارة المالية", 19.0, 0.55, 2027, "Q3", None, False),
    ("AU-GOV-DATAGOV-008", "Data Governance & NDMO Compliance", "حوكمة البيانات والالتزام بمكتب إدارة البيانات",
     "Data classification, ownership, quality and NDMO standards", "تصنيف وملكية وجودة البيانات ومعايير مكتب إدارة البيانات الوطنية",
     "Data Management Office", "مكتب إدارة البيانات", 20.5, 0.45, 2027, "Q1", None, True),
    ("AU-GOV-PRIVACY-009", "Personal Data Protection (PDPL)", "حماية البيانات الشخصية",
     "ROPA, data-subject rights and breach handling under PDPL", "سجل أنشطة المعالجة وحقوق أصحاب البيانات والاستجابة للانتهاكات وفق النظام",
     "Privacy Office", "مكتب حماية البيانات", 20.0, 0.48, 2027, "Q2", None, True),
    ("AU-GOV-CYBER-010", "Cybersecurity — NCA ECC", "الأمن السيبراني - الضوابط الأساسية",
     "Implementation of NCA Essential Cybersecurity Controls", "تطبيق الضوابط الأساسية للأمن السيبراني للهيئة الوطنية",
     "Cybersecurity GRC", "حوكمة الأمن السيبراني", 22.5, 0.40, 2027, "Q2", None, True),
    ("AU-GOV-DIGITAL-011", "Digital Services & DGA Compliance", "الخدمات الرقمية والالتزام بهيئة الحكومة الرقمية",
     "E-services delivery, digital maturity and DGA standards", "تقديم الخدمات الإلكترونية والنضج الرقمي ومعايير هيئة الحكومة الرقمية",
     "Digital Transformation Department", "إدارة التحول الرقمي", 17.5, 0.55, 2027, "Q3", None, False),
    ("AU-GOV-BCM-012", "Business Continuity (NCEMA)", "استمرارية الأعمال",
     "BIA, continuity plans and recovery exercises per NCEMA", "تحليل أثر الأعمال وخطط الاستمرارية والتمارين وفق المركز الوطني",
     "Business Continuity Department", "إدارة استمرارية الأعمال", 18.0, 0.50, 2027, "Q4", None, False),
    ("AU-GOV-RECORDS-013", "Records & Correspondence Management", "إدارة الوثائق والمراسلات",
     "Records lifecycle, retention and official correspondence", "دورة حياة الوثائق وجداول الإتلاف والمراسلات الرسمية",
     "General Services Department", "إدارة الخدمات العامة", 14.0, 0.60, 2027, "Q4", None, False),
    ("AU-GOV-INTEGRITY-014", "Integrity & Anti-Corruption (Nazaha)", "النزاهة ومكافحة الفساد",
     "Conflict-of-interest, integrity and whistleblowing per Nazaha", "تعارض المصالح والنزاهة والإبلاغ عن المخالفات وفق نزاهة",
     "Governance & Policy Department", "إدارة الحوكمة والسياسات", 19.5, 0.50, 2027, "Q3", None, False),
    ("AU-GOV-ASSETS-015", "Asset & Facilities Management", "إدارة الأصول والمرافق",
     "Fixed-asset register, custody and facilities management", "سجل الأصول الثابتة والعهدة وإدارة المرافق",
     "General Services Department", "إدارة الخدمات العامة", 13.5, 0.62, 2027, "Q4", None, False),
    ("AU-GOV-ITGC-016", "IT General Controls", "الضوابط العامة لتقنية المعلومات",
     "Access, change and operations controls over core systems", "ضوابط الوصول والتغيير والتشغيل على الأنظمة الأساسية",
     "Information Technology Department", "إدارة تقنية المعلومات", 19.0, 0.50, 2027, "Q1", None, True),
]


def seed_universe(cur, plan_id):
    ids = {}
    for (code, ten, tar, pen, par, ben, bar, inh, ce, yr, q, rating, snap) in UNIVERSE:
        residual = round(inh * (1 - ce), 2)
        cur.execute("SELECT id FROM audit_lc.ia_audit_universe_entry WHERE organisation_id=? AND entry_code=?", ORG, code)
        row = cur.fetchone()
        if row is None:
            cur.execute(
                """INSERT INTO audit_lc.ia_audit_universe_entry
                     (organisation_id, plan_id, entry_code, title_en, title_ar,
                      process_name_en, process_name_ar, business_unit_en, business_unit_ar,
                      inherent_risk_score, control_environment_score, residual_risk_score,
                      last_audit_rating, planned_audit_year, planned_quarter,
                      created_by_user_id, created_by, updated_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, (plan_id if snap else None), code, ten, tar, pen, par, ben, bar,
                inh, ce, residual, rating, yr, q, U_ADMIN, U_ADMIN, U_ADMIN)
            bump("audit_lc.ia_audit_universe_entry")
            ids[code] = cur.execute("SELECT @@IDENTITY").fetchval()
            print(f"  + universe {code}")
        else:
            ids[code] = row[0]
            print(f"  = universe {code} (exists)")
    return ids


# ─────────────────────────────────────────────────────────────────────────────
# C. ia_engagement (7) + D. milestones
# ─────────────────────────────────────────────────────────────────────────────
ENGAGEMENTS = [
    ("ENG-2027-G01", "AU-GOV-LICENSING-001",
     "Licensing & Permits Issuance Audit", "مراجعة إصدار التراخيص والتصاريح",
     "OPERATIONAL", "CLOSED", "PARTIAL",
     "End-to-end review of licence intake, review, approval, issuance and fee collection controls.",
     "مراجعة شاملة لضوابط استقبال ودراسة واعتماد وإصدار التراخيص وتحصيل الرسوم.",
     "2027-01-12", "2027-02-27", "2027-01-12", "2027-02-26", 3, 0),
    ("ENG-2027-G02", "AU-GOV-PROCUREMENT-005",
     "Etimad Procurement & Contract Management Audit", "مراجعة المشتريات عبر اعتماد وإدارة العقود",
     "COMPLIANCE", "FOLLOW_UP", "PARTIAL",
     "Compliance of tendering, award and contract administration through the Etimad platform.",
     "التزام المنافسات والترسية وإدارة العقود عبر منصة اعتماد.",
     "2027-01-15", "2027-03-15", "2027-01-15", "2027-03-12", 3, 0),
    ("ENG-2027-G03", "AU-GOV-DATAGOV-008",
     "NDMO Data Governance Maturity Audit", "مراجعة نضج حوكمة البيانات",
     "COMPLIANCE", "REPORTING", None,
     "Assessment of data classification, ownership, quality and NDMO standards alignment.",
     "تقييم تصنيف وملكية وجودة البيانات ومواءمة معايير مكتب إدارة البيانات الوطنية.",
     "2027-02-02", "2027-04-10", "2027-02-02", None, 3, 1),
    ("ENG-2027-G04", "AU-GOV-CYBER-010",
     "NCA ECC Cybersecurity Controls Audit", "مراجعة ضوابط الأمن السيبراني الأساسية",
     "IT_GENERAL", "FIELDWORK", None,
     "Design and operating effectiveness of NCA Essential Cybersecurity Controls.",
     "فاعلية تصميم وتشغيل الضوابط الأساسية للأمن السيبراني للهيئة الوطنية.",
     "2027-03-01", "2027-05-15", "2027-03-01", None, 2, 1),
    ("ENG-2027-G05", "AU-GOV-PRIVACY-009",
     "PDPL Personal Data Protection Audit", "مراجعة حماية البيانات الشخصية",
     "COMPLIANCE", "PLANNING", None,
     "Compliance with PDPL: ROPA, lawful basis, data-subject rights and breach handling.",
     "الالتزام بنظام حماية البيانات الشخصية: سجل المعالجة والأساس النظامي وحقوق أصحاب البيانات والانتهاكات.",
     "2027-04-05", "2027-06-15", None, None, 0, 0),
    ("ENG-2027-G06", "AU-GOV-BENEFICIARY-003",
     "Beneficiary Services & Grievances Audit", "مراجعة خدمات المستفيدين والشكاوى",
     "OPERATIONAL", "PLANNED", None,
     "Service-request handling, SLA adherence and grievance resolution effectiveness.",
     "معالجة طلبات الخدمة والالتزام باتفاقيات مستوى الخدمة وفاعلية حل الشكاوى.",
     "2027-09-01", "2027-10-31", None, None, 0, 0),
    ("ENG-2027-G07", "AU-GOV-PAYROLL-006",
     "Payroll & HR Entitlements Audit", "مراجعة الرواتب والمستحقات الوظيفية",
     "FINANCIAL", "PLANNED", None,
     "Accuracy and authorisation of payroll, allowances and end-of-service entitlements.",
     "دقة واعتماد الرواتب والبدلات ومستحقات نهاية الخدمة.",
     "2027-05-04", "2027-06-30", None, None, 0, 0),
]


def seed_engagements(cur, plan_id, uni):
    ids = {}
    for (code, ucode, ten, tar, atype, phase, opinion, sen, sar,
         ps, pe, ast, ae, fc, cc) in ENGAGEMENTS:
        cur.execute("SELECT id FROM audit_lc.ia_engagement WHERE organisation_id=? AND engagement_code=?", ORG, code)
        row = cur.fetchone()
        if row is None:
            cur.execute(
                """INSERT INTO audit_lc.ia_engagement
                     (organisation_id, plan_id, universe_entry_id, engagement_code,
                      title_en, title_ar, audit_type, scope_summary_en, scope_summary_ar,
                      lead_auditor_user_id, team_member_ids, sponsor_user_id,
                      planned_start_date, planned_end_date, actual_start_date, actual_end_date,
                      phase, opinion, finding_count, critical_finding_count,
                      created_by_user_id, created_by, updated_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, plan_id, uni.get(ucode), code, ten, tar, atype, sen, sar,
                U_AUDITOR, "5", U_ADMIN, ps, pe, ast, ae, phase, opinion, fc, cc,
                U_ADMIN, U_ADMIN, U_ADMIN)
            bump("audit_lc.ia_engagement")
            ids[code] = cur.execute("SELECT @@IDENTITY").fetchval()
            print(f"  + ia_engagement {code} [{phase}]")
        else:
            ids[code] = row[0]
            print(f"  = ia_engagement {code} (exists)")
    return ids


def milestone_plan(code):
    full = {
        "KICKOFF": ("اجتماع الافتتاح", "Kick-off meeting held with auditee management."),
        "FIELDWORK_START": ("بدء العمل الميداني", "Fieldwork commenced; sampling and testing started."),
        "DRAFT_REPORT": ("مسودة التقرير", "Draft report issued for management review."),
        "MANAGEMENT_RESPONSE": ("رد الإدارة", "Management responses received and incorporated."),
        "FINAL_REPORT": ("التقرير النهائي", "Final report issued to the Audit Committee."),
        "FOLLOW_UP_DUE": ("تاريخ المتابعة", "Follow-up on remediation actions due."),
    }
    plans = {
        "ENG-2027-G01": [
            ("KICKOFF","2027-01-12","2027-01-12"),("FIELDWORK_START","2027-01-19","2027-01-19"),
            ("DRAFT_REPORT","2027-02-12","2027-02-13"),("MANAGEMENT_RESPONSE","2027-02-20","2027-02-21"),
            ("FINAL_REPORT","2027-02-27","2027-02-26"),("FOLLOW_UP_DUE","2027-05-31",None)],
        "ENG-2027-G02": [
            ("KICKOFF","2027-01-15","2027-01-15"),("FIELDWORK_START","2027-01-22","2027-01-22"),
            ("DRAFT_REPORT","2027-02-26","2027-02-27"),("MANAGEMENT_RESPONSE","2027-03-05","2027-03-06"),
            ("FINAL_REPORT","2027-03-15","2027-03-12"),("FOLLOW_UP_DUE","2027-06-30",None)],
        "ENG-2027-G03": [
            ("KICKOFF","2027-02-02","2027-02-02"),("FIELDWORK_START","2027-02-09","2027-02-09"),
            ("DRAFT_REPORT","2027-04-03","2027-04-04"),("MANAGEMENT_RESPONSE","2027-04-10",None)],
        "ENG-2027-G04": [
            ("KICKOFF","2027-03-01","2027-03-01"),("FIELDWORK_START","2027-03-08","2027-03-08"),
            ("DRAFT_REPORT","2027-05-08",None)],
        "ENG-2027-G05": [
            ("KICKOFF","2027-04-05",None),("FIELDWORK_START","2027-04-12",None)],
        "ENG-2027-G06": [("KICKOFF","2027-09-01",None)],
        "ENG-2027-G07": [("KICKOFF","2027-05-04",None)],
    }
    return [(mt, p, a, full[mt][1], full[mt][0]) for (mt, p, a) in plans[code]]


def seed_milestones(cur, eng):
    for code, eid in eng.items():
        for (mt, p, a, nen, nar) in milestone_plan(code):
            cur.execute(
                "SELECT id FROM audit_lc.ia_engagement_milestone "
                "WHERE organisation_id=? AND engagement_id=? AND milestone_type=?",
                ORG, eid, mt)
            if cur.fetchone():
                continue
            cur.execute(
                """INSERT INTO audit_lc.ia_engagement_milestone
                     (organisation_id, engagement_id, milestone_type, planned_date,
                      actual_date, completed_by_user_id, notes_en, notes_ar,
                      created_by_user_id, created_by, updated_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, eid, mt, p, a, (U_AUDITOR if a else None), nen, nar,
                U_ADMIN, U_ADMIN, U_ADMIN)
            bump("audit_lc.ia_engagement_milestone")
    print(f"  milestones ensured for {len(eng)} engagements")


# ─────────────────────────────────────────────────────────────────────────────
# I. audit_lc.annual_plans (legacy, 1)
# ─────────────────────────────────────────────────────────────────────────────
def seed_legacy_plan(cur):
    ext = "EXT-IAP-LEGACY-2027"
    notes_en = ("Risk-based FY2027 internal audit plan for the Authority, approved by "
                "the Audit Committee (COM-AUDIT) and aligned with IIA Standards.")
    notes_ar = ("خطة المراجعة الداخلية للعام 2027 على أساس المخاطر، معتمدة من لجنة المراجعة "
                "ومتوافقة مع معايير المراجعة الداخلية الدولية.")
    cur.execute("SELECT id FROM audit_lc.annual_plans WHERE organisation_id=? AND external_id=?", ORG, ext)
    if cur.fetchone():
        print(f"  = audit_lc.annual_plans {ext} (exists)")
        return ext
    cur.execute(
        """INSERT INTO audit_lc.annual_plans
             (external_id, organisation_id, year, status, ac_approved_by, ac_approved_at,
              q1_codes, q2_codes, q3_codes, q4_codes, budget_hours, ac_notes_en, ac_notes_ar)
           VALUES (?, ?, 2027, 'EXECUTING', ?, '2026-12-15T09:00:00', ?, ?, ?, ?, ?, ?, ?)""",
        ext, ORG, "Audit Committee (COM-AUDIT) / لجنة المراجعة",
        "ENG-GOV-2027-01,ENG-GOV-2027-04",
        "ENG-GOV-2027-05",
        "ENG-GOV-2027-03",
        "ENG-GOV-2027-02",
        1780, notes_en, notes_ar)
    bump("audit_lc.annual_plans")
    print(f"  + audit_lc.annual_plans {ext}")
    return ext


# ─────────────────────────────────────────────────────────────────────────────
# E. audit_eng.engagements (legacy mirror, 5)
# ─────────────────────────────────────────────────────────────────────────────
LEG_ENG = [
    ("ENG-GOV-2027-01", "Licensing & Permits Issuance Audit", "مراجعة إصدار التراخيص والتصاريح",
     "INTERNAL", "CLOSED", "IIA", None,
     "Assess the effectiveness of controls over licence issuance and fee collection.",
     "تقييم فاعلية الضوابط على إصدار التراخيص وتحصيل الرسوم.",
     "Licensing intake, review, approval, issuance, fees.", "استقبال ودراسة واعتماد وإصدار التراخيص والرسوم.",
     320, 305, 3, "2027-01-12", "2027-01-19", "2027-02-11", "2027-02-12", "2027-02-26", "2027-03-01", "2027-03-01"),
    ("ENG-GOV-2027-02", "Etimad Procurement & Contract Management Audit", "مراجعة المشتريات عبر اعتماد وإدارة العقود",
     "INTERNAL", "FOLLOW_UP", "NCA ECC,IIA", None,
     "Verify compliance of procurement and contracts via the Etimad platform.",
     "التحقق من التزام المشتريات والعقود عبر منصة اعتماد.",
     "Tendering, award, contract administration on Etimad.", "المنافسات والترسية وإدارة العقود على اعتماد.",
     360, 342, 3, "2027-01-15", "2027-01-22", "2027-02-25", "2027-02-26", "2027-03-12", "2027-03-15", None),
    ("ENG-GOV-2027-03", "NDMO Data Governance Maturity Audit", "مراجعة نضج حوكمة البيانات",
     "CO_SOURCED", "REPORTING", "NDMO DMM", "PwC Middle East",
     "Evaluate data governance maturity against NDMO data management standards.",
     "تقييم نضج حوكمة البيانات وفق معايير مكتب إدارة البيانات الوطنية.",
     "Classification, ownership, quality, metadata, sharing.", "التصنيف والملكية والجودة والبيانات الوصفية والمشاركة.",
     420, 290, 3, "2027-02-02", "2027-02-09", None, "2027-04-04", None, None, None),
    ("ENG-GOV-2027-04", "NCA ECC Cybersecurity Controls Audit", "مراجعة ضوابط الأمن السيبراني الأساسية",
     "INTERNAL", "FIELDWORK", "NCA ECC", None,
     "Assess implementation of NCA Essential Cybersecurity Controls.",
     "تقييم تطبيق الضوابط الأساسية للأمن السيبراني للهيئة الوطنية.",
     "Governance, asset, identity, network, logging domains.", "الحوكمة والأصول والهوية والشبكة والتسجيل.",
     400, 180, 2, "2027-03-01", "2027-03-08", None, None, None, None, None),
    ("ENG-GOV-2027-05", "PDPL Personal Data Protection Audit", "مراجعة حماية البيانات الشخصية",
     "INTERNAL", "PLANNING", "PDPL", None,
     "Assess compliance with the Personal Data Protection Law.",
     "تقييم الالتزام بنظام حماية البيانات الشخصية.",
     "ROPA, lawful basis, DSR, breach, cross-border.", "سجل المعالجة والأساس النظامي وحقوق أصحاب البيانات والانتهاكات والنقل.",
     280, 0, 0, "2027-04-05", None, None, None, None, None, None),
]


def seed_legacy_engagements(cur, legacy_plan_ext):
    for (code, ten, tar, atype, status, fw, firm, oen, oar, sen, sar, budget, actual,
         fc, pstart, fwstart, fwend, repstart, repiss, fustart, closed) in LEG_ENG:
        cur.execute("SELECT id FROM audit_eng.engagements WHERE organisation_id=? AND engagement_code=?", ORG, code)
        if cur.fetchone():
            print(f"  = audit_eng.engagements {code} (exists)")
            continue
        cur.execute(
            """INSERT INTO audit_eng.engagements
                 (external_id, organisation_id, engagement_code, title_en, title_ar,
                  objective_en, objective_ar, scope_en, scope_ar, audit_type, framework,
                  auditor_firm, lead_auditor, audit_team, audit_committee_chair, status,
                  planning_started_at, fieldwork_started_at, fieldwork_ended_at,
                  reporting_started_at, report_issued_at, follow_up_started_at, closed_at,
                  budget_hours, actual_hours, findings_count, annual_plan_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            "EXT-" + code, ORG, code, ten, tar, oen, oar, sen, sar, atype, fw,
            firm, OWNER_IA, "auditor (lead), ranalyst (team)", AC_CHAIR, status,
            pstart, fwstart, fwend, repstart, repiss, fustart, closed,
            budget, actual, fc, legacy_plan_ext)
        bump("audit_eng.engagements")
        print(f"  + audit_eng.engagements {code} [{status}]")


# ─────────────────────────────────────────────────────────────────────────────
# F. audit_lc.findings (legacy, 14)
# ─────────────────────────────────────────────────────────────────────────────
FINDINGS = [
    ("FIN-2027-001", "ENG-GOV-2027-03", "FIN-001",
     "Data classification not applied to ~40% of datasets", "عدم تطبيق تصنيف البيانات على نحو 40% من مجموعات البيانات",
     "HIGH", "IN_REMEDIATION", x("NDMO-DCM-2.1", "NDMO-DG-1.3"), "Data Management Office / مكتب إدارة البيانات",
     "Approximately 40% of registered datasets have no NDMO classification label assigned.",
     "نحو 40% من مجموعات البيانات المسجلة دون تصنيف وفق معايير مكتب إدارة البيانات.",
     "NDMO Data Classification standard requires all datasets to be classified.",
     "يتطلب معيار تصنيف البيانات تصنيف جميع مجموعات البيانات.",
     "Data owners not formally assigned for several domains; classification not embedded in onboarding.",
     "عدم تعيين ملاك البيانات لعدة مجالات وعدم دمج التصنيف في إجراءات الإدخال.",
     "Assign data owners and complete classification for all datasets within 90 days.",
     "تعيين ملاك البيانات واستكمال التصنيف لجميع مجموعات البيانات خلال 90 يوماً.",
     "Agreed; Data Management Office will complete classification by Q3 2027.",
     "موافقة؛ سيستكمل مكتب إدارة البيانات التصنيف بحلول الربع الثالث 2027."),
    ("FIN-2027-002", "ENG-GOV-2027-05", "FIN-002",
     "ROPA incomplete under PDPL Article 31", "سجل أنشطة المعالجة غير مكتمل وفق المادة 31",
     "HIGH", "OPEN", x("PDPL-Art31", "PDPL-Art5"), "Privacy Office / مكتب حماية البيانات",
     "The Record of Processing Activities does not cover all processing operations.",
     "سجل أنشطة المعالجة لا يغطي جميع عمليات المعالجة.",
     "PDPL Article 31 requires a complete record of processing activities.",
     "تتطلب المادة 31 من النظام سجلاً كاملاً لأنشطة المعالجة.",
     "No periodic review process for the ROPA; new systems not registered.",
     "عدم وجود مراجعة دورية للسجل وعدم تسجيل الأنظمة الجديدة.",
     "Complete and institutionalise periodic ROPA review owned by the DPO.",
     "استكمال السجل ومأسسة المراجعة الدورية تحت مسؤولية مسؤول حماية البيانات.",
     None, None),
    ("FIN-2027-003", "ENG-GOV-2027-04", "FIN-003",
     "Network segmentation gap (NCA ECC 2-3-1)", "ثغرة في عزل الشبكة وفق الضابط 2-3-1",
     "HIGH", "MGMT_RESPONSE", x("ECC-2-3-1", "ECC-2-3"), "Cybersecurity GRC / حوكمة الأمن السيبراني",
     "Management and production network zones are not segmented per ECC 2-3-1.",
     "عدم عزل نطاقات الشبكة الإدارية والإنتاجية وفق الضابط 2-3-1.",
     "NCA ECC control 2-3-1 requires network segmentation between zones.",
     "يتطلب الضابط 2-3-1 عزل الشبكة بين النطاقات.",
     "Legacy flat network design; segmentation project not yet funded.",
     "تصميم شبكة مسطح قديم ومشروع العزل غير ممول بعد.",
     "Implement network segmentation and micro-segmentation for critical zones.",
     "تطبيق عزل الشبكة والعزل الدقيق للنطاقات الحرجة.",
     "Accepted; segmentation to be delivered in the FY2027 infrastructure programme.",
     "مقبول؛ سيتم تنفيذ العزل ضمن برنامج البنية التحتية 2027."),
    ("FIN-2027-004", "ENG-GOV-2027-04", "FIN-004",
     "Privileged access recertification overdue", "تأخر إعادة اعتماد الصلاحيات المميزة",
     "CRITICAL", "IN_REMEDIATION", x("ECC-2-2-3", "ECC-2-2"), "Cybersecurity GRC / حوكمة الأمن السيبراني",
     "Privileged accounts have not undergone the required quarterly recertification.",
     "لم تخضع الحسابات المميزة لإعادة الاعتماد الفصلية المطلوبة.",
     "ECC 2-2-3 requires periodic review of privileged access rights.",
     "يتطلب الضابط 2-2-3 المراجعة الدورية لحقوق الوصول المميزة.",
     "Recertification process is manual and was not performed in two consecutive quarters.",
     "إجراء إعادة الاعتماد يدوي ولم يُنفذ لربعين متتاليين.",
     "Perform immediate recertification and automate quarterly reviews.",
     "تنفيذ إعادة الاعتماد فوراً وأتمتة المراجعات الفصلية.",
     "Agreed; immediate recertification completed, automation in progress.",
     "موافقة؛ تم تنفيذ إعادة الاعتماد الفورية والأتمتة قيد التنفيذ."),
    ("FIN-2027-005", "ENG-GOV-2027-02", "FIN-005",
     "Single-source justifications missing approvals", "مبررات الشراء المباشر دون اعتمادات",
     "MEDIUM", "MGMT_RESPONSE", x("Etimad-SS", "GTPL-Art"), "Procurement Department / إدارة المشتريات",
     "Several single-source procurements lack documented justification approvals.",
     "عدد من عمليات الشراء المباشر دون اعتماد موثق للمبررات.",
     "Government Tenders & Procurement Law requires approved single-source justification.",
     "يتطلب نظام المنافسات والمشتريات الحكومية اعتماد مبرر الشراء المباشر.",
     "Approval workflow not consistently enforced on the Etimad records.",
     "عدم تطبيق مسار الاعتماد بشكل متسق على سجلات اعتماد.",
     "Enforce mandatory justification approval before single-source award.",
     "إلزام اعتماد المبرر قبل الترسية المباشرة.",
     "Accepted; control to be enforced in the procurement workflow.",
     "مقبول؛ سيتم تطبيق الضابط في مسار المشتريات."),
    ("FIN-2027-006", "ENG-GOV-2027-01", "FIN-006",
     "Licence fee waivers lack secondary review", "إعفاءات رسوم التراخيص دون مراجعة ثانية",
     "MEDIUM", "CLOSED", x("ITGC-SOD"), "Licensing Department / إدارة الترخيص",
     "Fee waivers can be granted by a single officer without secondary review.",
     "يمكن منح إعفاءات الرسوم من موظف واحد دون مراجعة ثانية.",
     "Segregation of duties requires independent review of fee waivers.",
     "يتطلب الفصل بين المهام مراجعة مستقلة لإعفاءات الرسوم.",
     "System role configuration allows waiver and approval by the same user.",
     "إعدادات الصلاحيات تتيح الإعفاء والاعتماد لنفس المستخدم.",
     "Configure a maker-checker control for all fee waivers.",
     "تطبيق ضابط المُدخل والمراجع لجميع إعفاءات الرسوم.",
     "Implemented; maker-checker enforced and verified.",
     "تم التطبيق؛ تم تفعيل المُدخل والمراجع والتحقق منه."),
    ("FIN-2027-007", "ENG-GOV-2027-01", "FIN-007",
     "Licence issuance SLA breaches not monitored", "عدم رصد تجاوزات مدة إصدار التراخيص",
     "LOW", "CLOSED", x("DGA-SLA"), "Licensing Department / إدارة الترخيص",
     "SLA breaches in licence issuance are not tracked or reported.",
     "تجاوزات مدة إصدار التراخيص غير متتبعة أو مبلغ عنها.",
     "DGA service standards require SLA monitoring for e-services.",
     "تتطلب معايير هيئة الحكومة الرقمية رصد مستوى الخدمة للخدمات الإلكترونية.",
     "No dashboard configured for issuance timeliness.",
     "عدم وجود لوحة لمتابعة زمن الإصدار.",
     "Configure SLA monitoring and monthly reporting for licence issuance.",
     "تهيئة رصد مستوى الخدمة وتقرير شهري لإصدار التراخيص.",
     "Implemented; SLA dashboard live.",
     "تم التطبيق؛ لوحة مستوى الخدمة تعمل."),
    ("FIN-2027-008", "ENG-GOV-2027-02", "FIN-008",
     "Contract performance bonds not tracked to expiry", "عدم تتبع الضمانات حتى انتهائها",
     "MEDIUM", "OPEN", x("GTPL-Bond"), "Procurement Department / إدارة المشتريات",
     "Performance bond expiry dates are not actively tracked for active contracts.",
     "تواريخ انتهاء ضمانات الأداء غير متتبعة للعقود السارية.",
     "Contracts require valid performance bonds throughout their term.",
     "تتطلب العقود ضمانات أداء سارية طوال مدتها.",
     "Manual tracking spreadsheet incomplete and outdated.",
     "ملف التتبع اليدوي غير مكتمل وقديم.",
     "Implement automated bond-expiry tracking and alerts.",
     "تطبيق تتبع آلي لانتهاء الضمانات وتنبيهات.",
     None, None),
    ("FIN-2027-009", "ENG-GOV-2027-03", "FIN-009",
     "Data-sharing agreements absent for inter-agency exchange", "غياب اتفاقيات مشاركة البيانات بين الجهات",
     "HIGH", "OPEN", x("NDMO-DS-3.2", "PDPL-Art15"), "Data Management Office / مكتب إدارة البيانات",
     "Inter-agency data exchanges occur without formal data-sharing agreements.",
     "تبادل البيانات بين الجهات يتم دون اتفاقيات رسمية لمشاركة البيانات.",
     "NDMO data-sharing standard requires formal agreements for exchanges.",
     "يتطلب معيار مشاركة البيانات اتفاقيات رسمية للتبادل.",
     "No standard template or governance gate for data-sharing.",
     "عدم وجود نموذج موحد أو بوابة حوكمة لمشاركة البيانات.",
     "Adopt a standard data-sharing agreement template and approval gate.",
     "اعتماد نموذج موحد لاتفاقية مشاركة البيانات وبوابة اعتماد.",
     None, None),
    ("FIN-2027-010", "ENG-GOV-2027-04", "FIN-010",
     "Security event log retention below ECC requirement", "مدة حفظ سجلات الأحداث الأمنية أقل من المطلوب",
     "MEDIUM", "IN_REMEDIATION", x("ECC-2-12-1"), "Cybersecurity GRC / حوكمة الأمن السيبراني",
     "Security event logs are retained for 9 months, below the 18-month requirement.",
     "يتم حفظ سجلات الأحداث الأمنية لمدة 9 أشهر أقل من 18 شهراً المطلوبة.",
     "ECC 2-12 requires retention of security event logs for at least 18 months.",
     "يتطلب الضابط 2-12 حفظ سجلات الأحداث الأمنية 18 شهراً على الأقل.",
     "SIEM storage sized for 9 months only.",
     "سعة نظام إدارة الأحداث مهيأة لـ 9 أشهر فقط.",
     "Expand log storage to meet the 18-month retention requirement.",
     "توسعة سعة حفظ السجلات لتلبية متطلب 18 شهراً.",
     "Agreed; storage expansion procured.",
     "موافقة؛ تم شراء توسعة السعة."),
    ("FIN-2027-011", "ENG-GOV-2027-02", "FIN-011",
     "Conflict-of-interest declarations missing (Nazaha)", "نقص إقرارات تعارض المصالح",
     "HIGH", "MGMT_RESPONSE", x("Nazaha-COI"), "Governance & Policy Department / إدارة الحوكمة والسياسات",
     "Several procurement committee members have no conflict-of-interest declaration on file.",
     "عدد من أعضاء لجان المشتريات دون إقرار تعارض مصالح موثق.",
     "Nazaha integrity requirements mandate annual COI declarations.",
     "تتطلب متطلبات النزاهة إقرارات سنوية لتعارض المصالح.",
     "Declaration process not enforced for committee appointments.",
     "عدم إلزام الإقرار عند تعيين أعضاء اللجان.",
     "Mandate COI declarations as a gate for committee participation.",
     "إلزام إقرار تعارض المصالح كشرط للمشاركة في اللجان.",
     "Accepted; declarations to be collected before next cycle.",
     "مقبول؛ سيتم جمع الإقرارات قبل الدورة القادمة."),
    ("FIN-2027-012", "ENG-GOV-2027-01", "FIN-012",
     "Records retention schedule not enforced", "عدم تطبيق جدول حفظ الوثائق",
     "LOW", "OPEN", x("NDMO-Ret", "Records-Ret"), "General Services Department / إدارة الخدمات العامة",
     "Licensing records are retained beyond the approved retention schedule.",
     "يتم حفظ وثائق التراخيص بعد المدة المعتمدة في جدول الحفظ.",
     "Records retention schedule requires disposal after the approved period.",
     "يتطلب جدول الحفظ الإتلاف بعد المدة المعتمدة.",
     "No automated disposal workflow in the records system.",
     "عدم وجود مسار آلي للإتلاف في نظام الوثائق.",
     "Implement scheduled records disposal aligned to the retention schedule.",
     "تطبيق إتلاف مجدول للوثائق وفق جدول الحفظ.",
     None, None),
    ("FIN-2027-013", "ENG-GOV-2027-04", "FIN-013",
     "MFA not enforced on all administrative accounts", "عدم إلزام التحقق متعدد العوامل لجميع الحسابات الإدارية",
     "HIGH", "IN_REMEDIATION", x("ECC-2-2-1"), "Cybersecurity GRC / حوكمة الأمن السيبراني",
     "A subset of administrative accounts can authenticate without multi-factor authentication.",
     "بعض الحسابات الإدارية يمكنها الدخول دون التحقق متعدد العوامل.",
     "ECC 2-2-1 requires MFA for privileged and remote access.",
     "يتطلب الضابط 2-2-1 التحقق متعدد العوامل للوصول المميز والبعيد.",
     "Conditional-access policy excludes legacy service accounts.",
     "سياسة الوصول المشروط تستثني حسابات الخدمة القديمة.",
     "Enforce MFA for all administrative and service accounts.",
     "إلزام التحقق متعدد العوامل لجميع الحسابات الإدارية وحسابات الخدمة.",
     "Agreed; MFA rollout in progress.",
     "موافقة؛ تعميم التحقق متعدد العوامل قيد التنفيذ."),
    ("FIN-2027-014", "ENG-GOV-2027-03", "FIN-014",
     "Observation on KPI evidence quality", "ملاحظة على جودة أدلة مؤشرات الأداء",
     "OBSERVATION", "OPEN", x("DGA-KPI"), "Strategy & Performance Department / إدارة الاستراتيجية والأداء",
     "Some reported performance KPIs lack supporting evidence for the reported values.",
     "بعض مؤشرات الأداء المبلغ عنها تفتقر إلى أدلة داعمة للقيم المعلنة.",
     "Performance reporting should be supported by verifiable evidence.",
     "ينبغي دعم تقارير الأداء بأدلة قابلة للتحقق.",
     "Evidence collection for KPIs is informal.",
     "جمع أدلة المؤشرات غير رسمي.",
     "Standardise KPI evidence collection and retention.",
     "توحيد جمع أدلة المؤشرات وحفظها.",
     None, None),
]


def seed_findings(cur):
    for (ext, eng, ref, ten, tar, sev, status, cross, owner,
         cen, car, cren, crar, caen, caar, ren, rar, men, mar) in FINDINGS:
        cur.execute("SELECT id FROM audit_lc.findings WHERE organisation_id=? AND external_id=?", ORG, ext)
        if cur.fetchone():
            print(f"  = findings {ext} (exists)")
            continue
        cur.execute(
            """INSERT INTO audit_lc.findings
                 (external_id, organisation_id, engagement_code, ref_number, title_en, title_ar,
                  severity, status, control_crosswalk, condition_en, condition_ar,
                  criteria_en, criteria_ar, cause_en, cause_ar, recommendation_en, recommendation_ar,
                  mgmt_response_en, mgmt_response_ar, mgmt_response_author, mgmt_accepts,
                  target_date, owner_role, capa_ids)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            ext, ORG, eng, ref, ten, tar, sev, status, cross, cen, car,
            cren, crar, caen, caar, ren, rar, men, mar,
            (owner if men else None), (1 if men else None),
            "2027-09-30", owner, None)
        bump("audit_lc.findings")
        print(f"  + findings {ext} [{sev}/{status}]")
