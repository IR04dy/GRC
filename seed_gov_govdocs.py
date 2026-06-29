# -*- coding: utf-8 -*-
"""Seed the government-documents (govdocs) layer for the default tenant (org 1).

Domain "govdocs" spans six schemas / nine tables:

    policy.documents          governance / ECC / HR / procurement / data policies
    policy.exceptions         policy-exception register tied to the new policies
    policy.attestations       all-staff acknowledgement of mandatory policies
    kb.categories             knowledge-base sections for an authority
    kb.articles               how-to / explainer articles filed under them
    capa.tasks                corrective / preventive action register
    board_pack.packs          Vision-2030 / regulator-posture board packs
    approvals.requests        approval requests wired to the records above
    signature.digital_signature_transactions   President / Board / GOV_POL e-sign trail

The target tenant (organisation_id = 1) is a Saudi GENERAL AUTHORITY (هيئة عامة)
headed by a President (رئيس الهيئة). Every table already holds a handful of
bank-flavoured rows; this script DEEPENS each with a coherent government layer
under brand-new natural keys, so an idempotent re-run inserts 0.

Regulators woven in by topic: NCA (ECC), NDMO + SDAIA (national data / AI),
PDPL (personal data), NCEMA (continuity), Nazaha (نزاهة, integrity), DGA
(digital government), ZATCA / Etimad (procurement), Vision 2030.

Cross-links (string convention only — no DB FKs in these schemas):
  - owner / approver = إدارة السياسات والحوكمة (GOV_POL) and the President's office
  - board packs -> COM-BOARD via audience='BOARD'
  - approvals + signatures reference the seeded policy / exception / CAPA /
    board-pack records by their codes (text) or numeric ids.

Idempotency / natural keys (verified from sys.indexes):
  - policy.documents      UNIQUE (organisation_id, policy_code, version)
  - kb.categories         UNIQUE (organisation_id, code)
  - kb.articles           UNIQUE (organisation_id, slug)
  - capa.tasks            UNIQUE (organisation_id, external_id)
  - board_pack.packs      UNIQUE (organisation_id, external_id)
  - approvals.requests    UNIQUE (organisation_id, external_id)
  - policy.exceptions     no UNIQUE -> guard by exception_code
  - policy.attestations   no UNIQUE -> guard by (policy_id, user_username)
  - signature.dst         no UNIQUE -> guard by external_id (always set)

HARD SAFETY: INSERT-only (no DELETE/TRUNCATE/DROP/ALTER). organisation_id = 1
only. All Arabic passed as Unicode parameters.

    Usage:  python seed_gov_govdocs.py     (run twice; second run inserts 0)
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
        except Exception as exc:  # noqa: BLE001 - try next driver
            last = exc
    raise SystemExit(f"Could not connect to SQL Server: {last}")


ORG = 1
GOV_POL_EN = "Governance & Policy Department"
GOV_POL_AR = "إدارة السياسات والحوكمة"
PRESIDENT_EN = "President of the Authority"
PRESIDENT_AR = "رئيس الهيئة"
BOARD_CHAIR_AR = "رئيس مجلس الإدارة"
HASH = "a" * 64  # deterministic 64-char sha256-shaped hex for SIGNED rows

inserted = {}   # table -> count
skipped = []    # (table, reason)


def bump(tbl: str, n: int = 1) -> None:
    inserted[tbl] = inserted.get(tbl, 0) + n


# ─────────────────────────────────────────────────────────────────────────────
# 1. policy.documents  (12 authority-grade governance policies)
# ─────────────────────────────────────────────────────────────────────────────
POLICIES = [
    dict(code="POL-GOV-001", ver="2.0", cat="GOVERNANCE",
         ten="Corporate Governance Manual", tar="دليل الحوكمة المؤسسية",
         sen="The Authority's overarching governance framework: board, committees, delegation and accountability.",
         sar="الإطار العام لحوكمة الهيئة: المجلس واللجان والتفويض والمساءلة.",
         fw="Vision 2030 / DGA", ctrl="DGA-GOV, Vision2030",
         cad="BIENNIAL", eff="2025-01-01", nxt="2027-01-01"),
    dict(code="POL-DELEG-001", ver="1.2", cat="GOVERNANCE",
         ten="Delegation of Authority Policy", tar="سياسة تفويض الصلاحيات",
         sen="Financial and administrative delegation matrix and approval thresholds.",
         sar="مصفوفة التفويض المالي والإداري وحدود اعتماد القرارات.",
         fw="Corporate Governance", ctrl="DGA-GOV",
         cad="BIENNIAL", eff="2025-02-01", nxt="2027-02-01"),
    dict(code="POL-COI-001", ver="1.1", cat="GOVERNANCE",
         ten="Conflict of Interest Policy", tar="سياسة تعارض المصالح",
         sen="Identification, disclosure and management of conflicts of interest, aligned with Nazaha.",
         sar="تحديد حالات تعارض المصالح والإفصاح عنها وإدارتها بما يتوافق مع هيئة نزاهة.",
         fw="Nazaha", ctrl="NAZAHA-COI",
         cad="ANNUAL", eff="2025-01-15", nxt="2026-01-15"),
    dict(code="POL-CODE-001", ver="2.0", cat="GOVERNANCE",
         ten="Code of Conduct & Professional Ethics", tar="مدونة السلوك الوظيفي وأخلاقيات المهنة",
         sen="Mandatory all-staff code of conduct and integrity standards (Nazaha).",
         sar="مدونة السلوك الوظيفي ومعايير النزاهة الملزمة لجميع المنسوبين (نزاهة).",
         fw="Nazaha", ctrl="NAZAHA-CODE",
         cad="ANNUAL", eff="2025-01-01", nxt="2026-01-01"),
    dict(code="POL-WB-001", ver="1.0", cat="GOVERNANCE",
         ten="Whistleblowing & Reporting of Violations Policy", tar="سياسة الإبلاغ عن المخالفات",
         sen="Confidential channels and protections for reporting violations, aligned with Nazaha.",
         sar="قنوات الإبلاغ السرية وحماية المبلّغين عن المخالفات بما يتوافق مع نزاهة.",
         fw="Nazaha", ctrl="NAZAHA-WB",
         cad="BIENNIAL", eff="2025-03-01", nxt="2027-03-01"),
    dict(code="POL-RM-001", ver="1.3", cat="GOVERNANCE",
         ten="Enterprise Risk Management Policy", tar="سياسة إدارة المخاطر المؤسسية",
         sen="Enterprise risk framework, appetite and the corporate risk register.",
         sar="إطار إدارة المخاطر المؤسسية وحدود تقبّل المخاطر وسجل المخاطر المؤسسي.",
         fw="ISO 31000 / COSO", ctrl="ISO31000",
         cad="ANNUAL", eff="2025-01-10", nxt="2026-01-10"),
    dict(code="POL-NCA-ECC-001", ver="2.0", cat="INFORMATION_SECURITY",
         ten="Cybersecurity Policy (NCA ECC)", tar="سياسة الأمن السيبراني (الضوابط الأساسية)",
         sen="Cybersecurity policy mapped to NCA Essential Cybersecurity Controls (ECC-2).",
         sar="سياسة الأمن السيبراني وفق الضوابط الأساسية للأمن السيبراني للهيئة الوطنية (ECC-2).",
         fw="NCA ECC-2", ctrl="ECC-1-3, ECC-2-1, ECC-2-3",
         cad="ANNUAL", eff="2025-01-01", nxt="2026-01-01"),
    dict(code="POL-ACC-001", ver="1.1", cat="INFORMATION_SECURITY",
         ten="Access Control & Identity Management Policy", tar="سياسة التحكم بالوصول وإدارة الهوية",
         sen="Identity lifecycle, least-privilege access and MFA requirements per NCA ECC.",
         sar="دورة حياة الهوية ومبدأ أقل الصلاحيات والتحقق متعدد العوامل وفق الضوابط الأساسية.",
         fw="NCA ECC-2", ctrl="ECC-2-2",
         cad="ANNUAL", eff="2025-02-01", nxt="2026-02-01"),
    dict(code="POL-DC-001", ver="1.2", cat="DATA_GOVERNANCE",
         ten="Data Classification & Handling Policy", tar="سياسة تصنيف البيانات والتعامل معها",
         sen="Data classification levels (Top Secret..Public) and handling per NDMO / SDAIA.",
         sar="مستويات تصنيف البيانات (سري للغاية حتى عام) وضوابط التعامل وفق مكتب إدارة البيانات وسدايا.",
         fw="NDMO / SDAIA", ctrl="NDMO-DC, SDAIA-DC",
         cad="ANNUAL", eff="2025-01-20", nxt="2026-01-20"),
    dict(code="POL-AI-001", ver="1.0", cat="DATA_GOVERNANCE",
         ten="Responsible AI & AI Ethics Policy", tar="سياسة أخلاقيات الذكاء الاصطناعي",
         sen="Responsible use of AI/ML aligned with SDAIA AI ethics principles.",
         sar="الاستخدام المسؤول للذكاء الاصطناعي بما يتوافق مع مبادئ أخلاقيات الذكاء الاصطناعي من سدايا.",
         fw="SDAIA AI Ethics", ctrl="SDAIA-AI",
         cad="ANNUAL", eff="2025-04-01", nxt="2026-04-01"),
    dict(code="POL-HR-001", ver="1.1", cat="HR",
         ten="Human Resources & Recruitment Policy", tar="سياسة الموارد البشرية والتوظيف",
         sen="Recruitment, performance and conduct rules for the Authority's workforce.",
         sar="قواعد التوظيف والأداء والسلوك الوظيفي لمنسوبي الهيئة.",
         fw="HRSD", ctrl="HRSD-HR",
         cad="BIENNIAL", eff="2025-01-05", nxt="2027-01-05"),
    dict(code="POL-PROC-001", ver="1.2", cat="PROCUREMENT",
         ten="Procurement & Contracting Policy", tar="سياسة الشراء والمنافسات والتعاقد",
         sen="Procurement and contracting rules aligned with Etimad and ZATCA requirements.",
         sar="قواعد الشراء والمنافسات والتعاقد بما يتوافق مع منصة اعتماد ومتطلبات هيئة الزكاة والضريبة والجمارك.",
         fw="Etimad / ZATCA", ctrl="ETIMAD-PROC, ZATCA",
         cad="BIENNIAL", eff="2025-02-15", nxt="2027-02-15"),
]


def seed_policies(cur) -> None:
    print("\n# policy.documents")
    for p in POLICIES:
        cur.execute(
            "SELECT id FROM policy.documents WHERE organisation_id=? AND policy_code=? AND version=?",
            ORG, p["code"], p["ver"],
        )
        if cur.fetchone():
            print(f"  = {p['code']} v{p['ver']}")
            continue
        cur.execute(
            """INSERT INTO policy.documents
                 (organisation_id, external_id, policy_code, version, title_en, title_ar,
                  summary_en, summary_ar, category, applicability_scope,
                  owner_name, owner_email, approver_name, approver_email,
                  effective_date, next_review_date, review_cadence, status, published_at,
                  related_framework, related_controls, is_deleted, created_by)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PUBLISHED',SYSUTCDATETIME(),?,?,0,'system')""",
            ORG, "EXT-" + p["code"], p["code"], p["ver"], p["ten"], p["tar"],
            p["sen"], p["sar"], p["cat"], "All employees of the Authority",
            GOV_POL_AR, "gov.policy@authority.gov.sa", PRESIDENT_AR, "president.office@authority.gov.sa",
            p["eff"], p["nxt"], p["cad"], p["fw"], p["ctrl"],
        )
        bump("policy.documents")
        print(f"  + {p['code']} v{p['ver']}  {p['ten']}")


def policy_id(cur, code: str) -> int | None:
    cur.execute(
        "SELECT TOP 1 id FROM policy.documents WHERE organisation_id=? AND policy_code=? ORDER BY id DESC",
        ORG, code,
    )
    r = cur.fetchone()
    return r[0] if r else None


# ─────────────────────────────────────────────────────────────────────────────
# 2. policy.exceptions  (6 exceptions tied to the new policies)
# ─────────────────────────────────────────────────────────────────────────────
EXCEPTIONS = [
    dict(ec="EXC-GOV-2026-001", pol="POL-NCA-ECC-001", risk="HIGH", status="APPROVED",
         ten="Temporary MFA exemption for legacy licensing mainframe",
         tar="إعفاء مؤقت من التحقق متعدد العوامل لنظام التراخيص القديم",
         jen="The legacy licensing mainframe cannot support MFA before its 2026 replacement.",
         jar="نظام التراخيص القديم لا يدعم التحقق متعدد العوامل قبل استبداله في 2026.",
         cen="Access restricted via a hardened jump-host with PAM session recording.",
         car="حصر الوصول عبر خادم وسيط محصّن مع تسجيل الجلسات بنظام إدارة الوصول المتميّز.",
         eff="2026-01-01", exp="2026-12-31"),
    dict(ec="EXC-GOV-2026-002", pol="POL-DC-001", risk="MEDIUM", status="UNDER_REVIEW",
         ten="Retention of inspection records beyond NDMO schedule (legal hold)",
         tar="الاحتفاظ بسجلات التفتيش بعد جدول الإتلاف بسبب الحجز القانوني",
         jen="Active litigation requires retaining inspection records past the NDMO retention schedule.",
         jar="تتطلب دعوى قضائية قائمة الاحتفاظ بسجلات التفتيش بعد جدول الاحتفاظ المعتمد من مكتب إدارة البيانات.",
         cen="Records segregated under legal hold with restricted access and audit logging.",
         car="عزل السجلات تحت الحجز القانوني مع تقييد الوصول وتسجيل العمليات.",
         eff="2026-03-01", exp="2026-09-30"),
    dict(ec="EXC-GOV-2026-003", pol="POL-DC-001", risk="LOW", status="APPROVED",
         ten="Open-data publication deviation for DGA national portal",
         tar="استثناء نشر البيانات المفتوحة على بوابة الحكومة الرقمية",
         jen="Aggregated statistics published to the DGA open-data portal as Public, deviating from default Internal handling.",
         jar="نشر إحصاءات مجمّعة على بوابة البيانات المفتوحة للهيئة كبيانات عامة خلافاً للتصنيف الداخلي الافتراضي.",
         cen="Data anonymised and reviewed by the Data Governance Committee before release.",
         car="إخفاء هوية البيانات ومراجعتها من لجنة حوكمة البيانات قبل النشر.",
         eff="2026-02-01", exp="2027-02-01"),
    dict(ec="EXC-GOV-2026-004", pol="POL-PROC-001", risk="MEDIUM", status="APPROVED",
         ten="Single-source procurement exception (Etimad direct purchase)",
         tar="استثناء الشراء من مصدر وحيد (شراء مباشر عبر اعتماد)",
         jen="Specialised inspection equipment available from a single qualified supplier; direct purchase via Etimad.",
         jar="توفر معدات تفتيش متخصصة من مورّد مؤهّل وحيد؛ شراء مباشر عبر منصة اعتماد.",
         cen="Justification memo and price benchmarking approved by the procurement committee.",
         car="مذكرة تبرير ومقارنة أسعار معتمدة من لجنة المشتريات.",
         eff="2026-04-01", exp="2026-07-01"),
    dict(ec="EXC-GOV-2026-005", pol="POL-ACC-001", risk="LOW", status="REQUESTED",
         ten="Delayed access recertification during organisational restructure",
         tar="تأجيل إعادة اعتماد الصلاحيات أثناء إعادة الهيكلة التنظيمية",
         jen="Quarterly access recertification deferred 60 days while departments are restructured.",
         jar="تأجيل إعادة اعتماد الصلاحيات الربعية ستين يوماً أثناء إعادة هيكلة الإدارات.",
         cen="Privileged accounts reviewed weekly during the deferral window.",
         car="مراجعة الحسابات المتميّزة أسبوعياً خلال فترة التأجيل.",
         eff="2026-05-01", exp="2026-07-01"),
    dict(ec="EXC-GOV-2026-006", pol="POL-AI-001", risk="HIGH", status="EXPIRED",
         ten="Interim use of an AI model pending SDAIA assessment",
         tar="استخدام مؤقت لنموذج ذكاء اصطناعي بانتظار تقييم سدايا",
         jen="A beneficiary-service AI assistant used in pilot before completing the SDAIA AI ethics assessment.",
         jar="استخدام مساعد ذكاء اصطناعي لخدمة المستفيدين في مرحلة تجريبية قبل استكمال تقييم أخلاقيات الذكاء الاصطناعي من سدايا.",
         cen="Pilot limited to non-personal data with human-in-the-loop review.",
         car="حصر التجربة على بيانات غير شخصية مع مراجعة بشرية على القرارات.",
         eff="2025-10-01", exp="2026-01-31"),
]


def seed_exceptions(cur) -> None:
    print("\n# policy.exceptions")
    for e in EXCEPTIONS:
        cur.execute(
            "SELECT id FROM policy.exceptions WHERE organisation_id=? AND exception_code=?",
            ORG, e["ec"],
        )
        if cur.fetchone():
            print(f"  = {e['ec']}")
            continue
        pid = policy_id(cur, e["pol"])
        if pid is None:
            skipped.append((f"policy.exceptions:{e['ec']}", f"policy {e['pol']} not found"))
            print(f"  ! {e['ec']} skipped (policy {e['pol']} missing)")
            continue
        st = e["status"]
        rev = "SYSUTCDATETIME()" if st in ("UNDER_REVIEW", "APPROVED", "REJECTED", "EXPIRED") else "NULL"
        appr = "SYSUTCDATETIME()" if st in ("APPROVED", "EXPIRED") else "NULL"
        cur.execute(
            f"""INSERT INTO policy.exceptions
                 (organisation_id, external_id, policy_id, exception_code, title_en, title_ar,
                  justification_en, justification_ar, compensating_controls_en, compensating_controls_ar,
                  requestor_name, requestor_email, reviewer_name, approver_name,
                  risk_level, reviewed_at, approved_at, effective_date, expiry_date,
                  status, is_deleted, created_by)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,{rev},{appr},?,?,?,0,'system')""",
            ORG, e["ec"], pid, e["ec"], e["ten"], e["tar"],
            e["jen"], e["jar"], e["cen"], e["car"],
            GOV_POL_AR, "gov.policy@authority.gov.sa", GOV_POL_AR, PRESIDENT_AR,
            e["risk"], e["eff"], e["exp"], st,
        )
        bump("policy.exceptions")
        print(f"  + {e['ec']}  [{st}]  {e['ten']}")


# ─────────────────────────────────────────────────────────────────────────────
# 3. policy.attestations  (all-staff campaign for 2 mandatory policies)
# ─────────────────────────────────────────────────────────────────────────────
USERS = [
    dict(u="admin", fn="مدير المنصة", role="PLATFORM_ADMIN", dept="إدارة السياسات والحوكمة"),
    dict(u="rmanager", fn="مدير المخاطر", role="RISK_MANAGER", dept="إدارة المخاطر المؤسسية"),
    dict(u="compliance", fn="مسؤول الالتزام", role="COMPLIANCE_OFFICER", dept="إدارة الالتزام"),
    dict(u="auditor", fn="المراجع الداخلي", role="AUDITOR", dept="إدارة المراجعة الداخلية"),
    dict(u="ranalyst", fn="محلل المخاطر", role="RISK_ANALYST", dept="إدارة المخاطر المؤسسية"),
    dict(u="viewer", fn="مستخدم اطلاع", role="VIEWER", dept="إدارة العمليات"),
]
# (policy_code, status, declined_reason)  status spread across the 12 rows
ATT_PLAN = {
    "POL-CODE-001": [  # Code of Conduct — all attested except viewer pending
        ("admin", "ATTESTED", None), ("rmanager", "ATTESTED", None),
        ("compliance", "ATTESTED", None), ("auditor", "ATTESTED", None),
        ("ranalyst", "ATTESTED", None), ("viewer", "PENDING", None),
    ],
    "POL-NCA-ECC-001": [  # Cybersecurity — mostly attested, 1 pending, 1 declined
        ("admin", "ATTESTED", None), ("rmanager", "ATTESTED", None),
        ("compliance", "ATTESTED", None),
        ("auditor", "DECLINED", "أتحفظ على بند الوصول عن بُعد وأطلب توضيحاً قبل الإقرار"),
        ("ranalyst", "PENDING", None), ("viewer", "ATTESTED", None),
    ],
}


def seed_attestations(cur) -> None:
    print("\n# policy.attestations")
    umap = {u["u"]: u for u in USERS}
    for pol_code, rows in ATT_PLAN.items():
        pid = policy_id(cur, pol_code)
        if pid is None:
            skipped.append((f"policy.attestations:{pol_code}", "policy not found"))
            continue
        ver = next((p["ver"] for p in POLICIES if p["code"] == pol_code), "1.0")
        for uname, status, reason in rows:
            cur.execute(
                "SELECT id FROM policy.attestations WHERE organisation_id=? AND policy_id=? AND user_username=?",
                ORG, pid, uname,
            )
            if cur.fetchone():
                print(f"  = {pol_code}/{uname}")
                continue
            u = umap[uname]
            att = "SYSUTCDATETIME()" if status == "ATTESTED" else "NULL"
            dec = "SYSUTCDATETIME()" if status == "DECLINED" else "NULL"
            ev = "NAFATH-signed acknowledgement" if status == "ATTESTED" else None
            cur.execute(
                f"""INSERT INTO policy.attestations
                     (organisation_id, external_id, policy_id, policy_version, user_username,
                      user_full_name, user_role, user_department, status,
                      attested_at, declined_at, declined_reason, evidence_ref, is_deleted, created_by)
                   VALUES (?,?,?,?,?,?,?,?,?,{att},{dec},?,?,0,'system')""",
                ORG, f"ATT-{pol_code}-{uname}", pid, ver, uname,
                u["fn"], u["role"], u["dept"], status, reason, ev,
            )
            bump("policy.attestations")
            print(f"  + {pol_code}/{uname}  [{status}]")


# ─────────────────────────────────────────────────────────────────────────────
# 4. kb.categories  (6 government-domain sections)
# ─────────────────────────────────────────────────────────────────────────────
KB_CATS = [
    ("governance", "Governance", "الحوكمة", 50),
    ("cybersecurity", "Cybersecurity (NCA ECC)", "الأمن السيبراني", 60),
    ("data-management", "Data Management (NDMO / SDAIA)", "إدارة البيانات", 70),
    ("business-continuity", "Business Continuity (NCEMA)", "استمرارية الأعمال", 80),
    ("procurement", "Procurement & Tenders", "المشتريات والمنافسات", 90),
    ("hr", "Human Resources", "الموارد البشرية", 100),
]


def seed_kb_categories(cur) -> None:
    print("\n# kb.categories")
    for code, en, ar, so in KB_CATS:
        cur.execute("SELECT id FROM kb.categories WHERE organisation_id=? AND code=?", ORG, code)
        if cur.fetchone():
            print(f"  = {code}")
            continue
        cur.execute(
            "INSERT INTO kb.categories (organisation_id, code, name_en, name_ar, sort_order, is_deleted) "
            "VALUES (?,?,?,?,?,0)",
            ORG, code, en, ar, so,
        )
        bump("kb.categories")
        print(f"  + {code}  {en}")


# ─────────────────────────────────────────────────────────────────────────────
# 5. kb.articles  (14 explainer / how-to articles)
# ─────────────────────────────────────────────────────────────────────────────
ARTICLES = [
    dict(slug="gov-committee-structure", cat="governance",
         ten="How the Authority's Governance Committees Work",
         tar="كيف تعمل لجان الحوكمة في الهيئة",
         sen="From the Board of Directors down to specialised committees.",
         sar="من مجلس الإدارة وصولاً إلى اللجان المتخصصة.",
         ben="The Board of Directors oversees strategy; specialised committees (Audit, Risk, Compliance, Cybersecurity) report into it.",
         bar="يشرف مجلس الإدارة على الاستراتيجية، وترفع إليه اللجان المتخصصة (المراجعة، المخاطر، الالتزام، الأمن السيبراني)."),
    dict(slug="gov-delegation-of-authority", cat="governance",
         ten="Delegation of Authority — Quick Guide",
         tar="دليل مختصر لتفويض الصلاحيات",
         sen="Who can approve what, and at which threshold.",
         sar="من يعتمد ماذا، وعند أي حد.",
         ben="The delegation matrix sets financial and administrative approval limits per role.",
         bar="تحدد مصفوفة التفويض حدود الاعتماد المالي والإداري لكل مستوى وظيفي."),
    dict(slug="gov-ecc-overview", cat="cybersecurity",
         ten="NCA ECC Control Domains — Staff Overview",
         tar="نظرة عامة على مجالات الضوابط الأساسية للأمن السيبراني",
         sen="The five ECC main domains explained for everyone.",
         sar="شرح مجالات الضوابط الأساسية الخمسة لجميع المنسوبين.",
         ben="The NCA Essential Cybersecurity Controls span governance, defence, resilience, third-party and ICS domains.",
         bar="تشمل الضوابط الأساسية للأمن السيبراني الحوكمة والتعزيز والصمود والأطراف الخارجية وأنظمة التحكم الصناعي."),
    dict(slug="gov-data-classification", cat="data-management",
         ten="Classifying & Handling Data per NDMO / SDAIA",
         tar="تصنيف البيانات والتعامل معها وفق مكتب إدارة البيانات وسدايا",
         sen="Top Secret, Secret, Confidential, Public — and how to handle each.",
         sar="سري للغاية، سري، مقيّد، عام — وكيفية التعامل مع كل مستوى.",
         ben="Classify every dataset into one of four levels and apply the matching handling and sharing rules.",
         bar="صنّف كل مجموعة بيانات إلى أحد المستويات الأربعة وطبّق ضوابط التعامل والمشاركة المناسبة."),
    dict(slug="gov-pdpl-dsr", cat="data-management",
         ten="Handling Data-Subject Requests under PDPL",
         tar="التعامل مع طلبات أصحاب البيانات وفق نظام حماية البيانات الشخصية",
         sen="Access, correction, deletion — the public-authority workflow.",
         sar="الاطلاع والتصحيح والحذف — مسار العمل في الهيئة العامة.",
         ben="Route data-subject requests to the Privacy Office and respond within the PDPL statutory period.",
         bar="حوّل طلبات أصحاب البيانات إلى إدارة الخصوصية وردّ خلال المدة النظامية المحددة في النظام."),
    dict(slug="gov-bcm-activation", cat="business-continuity",
         ten="Business Continuity Activation Steps (NCEMA)",
         tar="خطوات تفعيل استمرارية الأعمال (المركز الوطني لإدارة الأزمات)",
         sen="What to do when a major disruption hits.",
         sar="ماذا تفعل عند وقوع اضطراب كبير.",
         ben="On disruption, the Crisis Management Committee activates the continuity plan and recovery teams.",
         bar="عند الاضطراب، تفعّل لجنة إدارة الأزمات خطة الاستمرارية وفرق التعافي."),
    dict(slug="gov-coi-disclosure", cat="governance",
         ten="Disclosing a Conflict of Interest (Nazaha)",
         tar="الإفصاح عن تعارض المصالح (نزاهة)",
         sen="When and how to disclose, step by step.",
         sar="متى وكيف تُفصح، خطوة بخطوة.",
         ben="Disclose any actual or potential conflict of interest before participating in a related decision.",
         bar="أفصح عن أي تعارض مصالح فعلي أو محتمل قبل المشاركة في أي قرار ذي علاقة."),
    dict(slug="gov-whistleblowing-channel", cat="governance",
         ten="Whistleblowing Channel — How to Report",
         tar="قناة الإبلاغ عن المخالفات — كيف تُبلّغ",
         sen="Confidential reporting and protections.",
         sar="الإبلاغ السري وحماية المبلّغين.",
         ben="Reports can be submitted confidentially; whistleblowers are protected from retaliation per Nazaha.",
         bar="يمكن تقديم البلاغات بسرية، ويُحمى المبلّغون من أي إجراء انتقامي وفق نزاهة."),
    dict(slug="gov-procurement-etimad", cat="procurement",
         ten="Procurement via Etimad — Quick Start",
         tar="الشراء عبر منصة اعتماد — بداية سريعة",
         sen="Raising and routing a procurement request.",
         sar="إنشاء طلب شراء وتوجيهه.",
         ben="All competitive procurement flows through Etimad; single-source needs a documented exception.",
         bar="تتم جميع المنافسات عبر منصة اعتماد، ويتطلب الشراء من مصدر وحيد استثناءً موثّقاً."),
    dict(slug="gov-onboarding-access", cat="hr",
         ten="Employee Onboarding & Offboarding Access",
         tar="منح وسحب الصلاحيات عند التعيين وانتهاء الخدمة",
         sen="Joiner-mover-leaver access steps.",
         sar="خطوات الصلاحيات عند الالتحاق والنقل والمغادرة.",
         ben="Access is provisioned on joining and revoked on the last working day under least-privilege.",
         bar="تُمنح الصلاحيات عند الالتحاق وتُسحب في آخر يوم عمل وفق مبدأ أقل الصلاحيات."),
    dict(slug="gov-records-retention", cat="data-management",
         ten="Records-Retention Schedule — Explainer",
         tar="جدول الاحتفاظ بالسجلات — شرح",
         sen="How long to keep records and when to dispose.",
         sar="مدة الاحتفاظ بالسجلات وموعد الإتلاف.",
         ben="Retention periods follow the NDMO schedule; legal holds override scheduled disposal.",
         bar="تتبع مدد الاحتفاظ جدول مكتب إدارة البيانات، ويوقف الحجز القانوني الإتلاف المجدول."),
    dict(slug="gov-dga-service-standards", cat="governance",
         ten="DGA Digital-Government Service Standards",
         tar="معايير الخدمات الحكومية الرقمية (هيئة الحكومة الرقمية)",
         sen="What good digital services must meet.",
         sar="ما يجب أن تستوفيه الخدمات الرقمية الجيدة.",
         ben="Digital services must meet DGA standards for accessibility, performance and user experience.",
         bar="يجب أن تستوفي الخدمات الرقمية معايير هيئة الحكومة الرقمية للوصول والأداء وتجربة المستخدم."),
    dict(slug="gov-ai-acceptable-use", cat="data-management",
         ten="Acceptable Use of AI Tools (SDAIA)",
         tar="الاستخدام المقبول لأدوات الذكاء الاصطناعي (سدايا)",
         sen="Do's and don'ts for AI at the Authority.",
         sar="ما يجوز وما لا يجوز عند استخدام الذكاء الاصطناعي في الهيئة.",
         ben="Never enter classified or personal data into external AI tools; follow SDAIA AI ethics principles.",
         bar="لا تُدخل بيانات مصنّفة أو شخصية في أدوات الذكاء الاصطناعي الخارجية، والتزم بمبادئ سدايا."),
    dict(slug="gov-policy-attestation-walkthrough", cat="governance",
         ten="Annual Policy-Attestation Walkthrough",
         tar="شرح الإقرار السنوي بالسياسات",
         sen="How to complete your yearly attestations.",
         sar="كيف تُكمل إقراراتك السنوية بالسياسات.",
         ben="Review each mandatory policy and confirm your acknowledgement, signed via Nafath.",
         bar="راجع كل سياسة إلزامية وأكّد إقرارك بها موقّعاً عبر نفاذ."),
]


def seed_articles(cur) -> None:
    print("\n# kb.articles")
    for a in ARTICLES:
        cur.execute("SELECT id FROM kb.articles WHERE organisation_id=? AND slug=?", ORG, a["slug"])
        if cur.fetchone():
            print(f"  = {a['slug']}")
            continue
        cur.execute(
            """INSERT INTO kb.articles
                 (external_id, organisation_id, slug, category_code, title_en, title_ar,
                  summary_en, summary_ar, body_en_md, body_ar_md, tags,
                  status, visibility, author, published_at, view_count, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,'PUBLISHED','INTERNAL',?,SYSUTCDATETIME(),0,0)""",
            "EXT-KB-" + a["slug"], ORG, a["slug"], a["cat"], a["ten"], a["tar"],
            a["sen"], a["sar"], a["ben"], a["bar"], a["cat"], GOV_POL_AR,
        )
        bump("kb.articles")
        print(f"  + {a['slug']}  {a['ten']}")


# ─────────────────────────────────────────────────────────────────────────────
# 6. capa.tasks  (10 corrective / preventive actions)
# ─────────────────────────────────────────────────────────────────────────────
CAPAS = [
    dict(ex="CAPA-GOV-2026-001", ot="control_test", oid="ECC-2-1-DLP", typ="CORRECTIVE",
         sev="HIGH", status="IN_PROGRESS",
         ten="Implement DLP on the licensing system",
         tar="تطبيق منع تسرّب البيانات على نظام التراخيص",
         den="ECC self-assessment found no DLP on the licensing system handling restricted data.",
         dar="أظهر التقييم الذاتي للضوابط الأساسية غياب منع تسرّب البيانات على نظام التراخيص الذي يعالج بيانات مقيّدة.",
         owner="إدارة الأمن السيبراني", target="2026-09-30",
         pen="DLP policy drafted; agent rollout 60% complete.", par="تم إعداد سياسة منع التسرّب، واكتمل نشر الوكيل بنسبة 60%."),
    dict(ex="CAPA-GOV-2026-002", ot="control_test", oid="ECC-2-2-MFA", typ="CORRECTIVE",
         sev="HIGH", status="OPEN",
         ten="Close MFA gap on privileged accounts",
         tar="معالجة فجوة التحقق متعدد العوامل على الحسابات المتميّزة",
         den="A subset of privileged accounts lacks MFA, breaching NCA ECC-2-2.",
         dar="بعض الحسابات المتميّزة تفتقر إلى التحقق متعدد العوامل بما يخالف الضابط ECC-2-2.",
         owner="مركز العمليات الأمنية", target="2026-08-15", pen=None, par=None),
    dict(ex="CAPA-GOV-2026-003", ot="audit_finding", oid="IA-2026-014", typ="CORRECTIVE",
         sev="MEDIUM", status="AWAITING_VERIFICATION",
         ten="Fix segregation-of-duties gap in procurement",
         tar="معالجة فجوة الفصل بين المهام في المشتريات",
         den="Internal audit found one role could both create and approve purchase orders.",
         dar="وجدت المراجعة الداخلية أن دوراً واحداً يستطيع إنشاء أوامر الشراء واعتمادها معاً.",
         owner="إدارة المشتريات", target="2026-06-30",
         pen="Roles split in Etimad workflow; awaiting audit verification.", par="تم فصل الأدوار في مسار اعتماد؛ بانتظار تحقق المراجعة."),
    dict(ex="CAPA-GOV-2026-004", ot="control_test", oid="NDMO-DQ-07", typ="PREVENTIVE",
         sev="MEDIUM", status="IN_PROGRESS",
         ten="Remediate data-quality gaps in the beneficiary register",
         tar="معالجة فجوات جودة البيانات في سجل المستفيدين",
         den="NDMO data-quality review flagged duplicate and incomplete beneficiary records.",
         dar="رصدت مراجعة جودة البيانات لمكتب إدارة البيانات سجلات مستفيدين مكررة وغير مكتملة.",
         owner="إدارة إدارة البيانات", target="2026-10-31",
         pen="De-duplication rules deployed; completeness checks in progress.", par="تم نشر قواعد إزالة التكرار، وفحوص الاكتمال جارية."),
    dict(ex="CAPA-GOV-2026-005", ot="dpia", oid="DPIA-2026-003", typ="CORRECTIVE",
         sev="HIGH", status="CLOSED",
         ten="Add consent capture to the e-services portal (PDPL DPIA)",
         tar="إضافة التقاط الموافقة في بوابة الخدمات الإلكترونية (تقييم أثر الخصوصية)",
         den="DPIA identified missing explicit consent capture for optional data processing.",
         dar="حدد تقييم أثر الخصوصية غياب التقاط الموافقة الصريحة لمعالجة البيانات الاختيارية.",
         owner="إدارة الخصوصية", target="2026-05-31",
         pen="Consent banner and audit log delivered and verified.", par="تم تسليم شاشة الموافقة وسجل التدقيق والتحقق منهما."),
    dict(ex="CAPA-GOV-2026-006", ot="incident", oid="INC-2026-021", typ="CORRECTIVE",
         sev="CRITICAL", status="CLOSED",
         ten="Root-cause remediation for phishing incident",
         tar="معالجة السبب الجذري لحادثة التصيّد",
         den="A phishing incident exposed weak email filtering; remediation required.",
         dar="كشفت حادثة تصيّد ضعف تصفية البريد الإلكتروني وتطلّبت المعالجة.",
         owner="مركز العمليات الأمنية", target="2026-04-30",
         pen="Advanced email filtering enabled; staff awareness refreshed.", par="تم تفعيل التصفية المتقدمة للبريد وتحديث التوعية."),
    dict(ex="CAPA-GOV-2026-007", ot="control_test", oid="NCEMA-BIA-02", typ="PREVENTIVE",
         sev="MEDIUM", status="IN_PROGRESS",
         ten="Follow-up actions from the NCEMA BIA",
         tar="إجراءات المتابعة من تحليل أثر الأعمال",
         den="Business impact analysis recommended improving RTO for the licensing service.",
         dar="أوصى تحليل أثر الأعمال بتحسين زمن التعافي المستهدف لخدمة التراخيص.",
         owner="إدارة استمرارية الأعمال", target="2026-11-30",
         pen="Warm standby being provisioned for the licensing service.", par="يجري تجهيز موقع احتياطي دافئ لخدمة التراخيص."),
    dict(ex="CAPA-GOV-2026-008", ot="audit_finding", oid="IA-2026-019", typ="CORRECTIVE",
         sev="LOW", status="OPEN",
         ten="Improve the conflict-of-interest disclosure process",
         tar="تحسين عملية الإفصاح عن تعارض المصالح",
         den="Internal audit noted COI disclosures are collected manually and inconsistently.",
         dar="لاحظت المراجعة الداخلية أن إقرارات تعارض المصالح تُجمع يدوياً وبشكل غير متسق.",
         owner="إدارة الالتزام", target="2026-09-15", pen=None, par=None),
    dict(ex="CAPA-GOV-2026-009", ot="inspection_finding", oid="INSP-2026-101", typ="CORRECTIVE",
         sev="MEDIUM", status="IN_PROGRESS",
         ten="Clear the inspection-function case backlog",
         tar="تصفية تراكم حالات وظيفة التفتيش",
         den="The inspection function has a backlog of cases beyond the service SLA.",
         dar="لدى وظيفة التفتيش تراكم في الحالات يتجاوز اتفاقية مستوى الخدمة.",
         owner="إدارة التفتيش", target="2026-12-15",
         pen="Surge resourcing plan approved; backlog reduced 30%.", par="تم اعتماد خطة دعم مؤقتة، وانخفض التراكم بنسبة 30%."),
    dict(ex="CAPA-GOV-2026-010", ot="risk_treatment", oid="RT-2026-008", typ="PREVENTIVE",
         sev="LOW", status="CANCELLED",
         ten="Pilot third-party risk scoring tool",
         tar="تجربة أداة تقييم مخاطر الأطراف الخارجية",
         den="Preventive action to pilot a TPRM scoring tool, cancelled pending budget reallocation.",
         dar="إجراء وقائي لتجربة أداة تقييم مخاطر الأطراف الخارجية، أُلغي بانتظار إعادة توزيع الميزانية.",
         owner="إدارة المخاطر المؤسسية", target="2026-07-31",
         pen=None, par=None, cancel="Deferred to the 2027 plan after budget review.",
         cancel_ar="تأجيل إلى خطة 2027 بعد مراجعة الميزانية."),
]


def seed_capa(cur) -> None:
    print("\n# capa.tasks")
    for c in CAPAS:
        cur.execute("SELECT id FROM capa.tasks WHERE organisation_id=? AND external_id=?", ORG, c["ex"])
        if cur.fetchone():
            print(f"  = {c['ex']}")
            continue
        st = c["status"]
        started = "SYSUTCDATETIME()" if st in ("IN_PROGRESS", "AWAITING_VERIFICATION", "CLOSED") else "NULL"
        subm = "SYSUTCDATETIME()" if st in ("AWAITING_VERIFICATION", "CLOSED") else "NULL"
        ver = "SYSUTCDATETIME()" if st == "CLOSED" else "NULL"
        closed = "SYSUTCDATETIME()" if st == "CLOSED" else "NULL"
        canc = "SYSUTCDATETIME()" if st == "CANCELLED" else "NULL"
        vby = "auditor" if st == "CLOSED" else None
        cur.execute(
            f"""INSERT INTO capa.tasks
                 (external_id, organisation_id, origin_type, origin_id, title_en, title_ar,
                  description_en, description_ar, capa_type, severity, owner, target_date, status,
                  started_at, submitted_for_verification_at, verified_at, verified_by, closed_at,
                  cancelled_at, cancellation_reason_en, progress_notes_en, progress_notes_ar,
                  evidence_refs, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,{started},{subm},{ver},?,{closed},{canc},?,?,?,?,0)""",
            c["ex"], ORG, c["ot"], c["oid"], c["ten"], c["tar"],
            c["den"], c["dar"], c["typ"], c["sev"], c["owner"], c["target"], st,
            vby, c.get("cancel"), c.get("pen"), c.get("par"),
            f"EVID-{c['ex']}",
        )
        bump("capa.tasks")
        print(f"  + {c['ex']}  [{st}]  {c['ten']}")


# ─────────────────────────────────────────────────────────────────────────────
# 7. board_pack.packs  (4 authority / Vision-2030 packs)
# ─────────────────────────────────────────────────────────────────────────────
def jdump(obj):
    return json.dumps(obj, ensure_ascii=False)


PACKS = [
    dict(ex="BP-GOV-2026-Q2-BOARD", aud="BOARD", status="APPROVED",
         ten="Board Pack — Vision 2030 & Regulator Posture (Q2 2026)",
         tar="حقيبة المجلس — رؤية 2030 وموقف الالتزام التنظيمي (الربع الثاني 2026)",
         pl="2026-Q2-GOV", ps="2026-04-01", pe="2026-06-30",
         risk=jdump({"en": "Top enterprise risks within appetite", "ar": "أبرز المخاطر المؤسسية ضمن حدود التقبّل", "top": 5}),
         pdpl=jdump({"en": "PDPL DSR closure 96%", "ar": "إغلاق طلبات أصحاب البيانات 96%"}),
         custom=jdump({"en": "Regulator scorecard: NCA / NDMO / PDPL", "ar": "بطاقة أداء الجهات: الأمن السيبراني / إدارة البيانات / حماية البيانات",
                        "vision2030": {"en": "Strategic-objective progress 82%", "ar": "تقدّم الأهداف الاستراتيجية 82%"}}),
         drafted_by="Board & Committees Secretariat", approved_by=PRESIDENT_AR),
    dict(ex="BP-GOV-2026-Q2-DATAGOV", aud="EXEC", status="APPROVED",
         ten="Data Governance & AI Maturity Pack (Q2 2026)",
         tar="حقيبة نضج حوكمة البيانات والذكاء الاصطناعي (الربع الثاني 2026)",
         pl="2026-Q2-DATA", ps="2026-04-01", pe="2026-06-30",
         risk=jdump({"en": "Data risks tracked", "ar": "متابعة مخاطر البيانات"}),
         pdpl=jdump({"en": "NDMO maturity level 3 of 5", "ar": "مستوى نضج مكتب إدارة البيانات 3 من 5"}),
         custom=jdump({"en": "SDAIA NDI compliance summary", "ar": "ملخص الالتزام بمؤشر البيانات الوطنية من سدايا"}),
         drafted_by="Data Management Office", approved_by="Deputy for Digital Transformation & Data"),
    dict(ex="BP-GOV-2026-Q2-CRISIS", aud="EXEC", status="IN_REVIEW",
         ten="Continuity Readiness Pack — NCEMA (Q2 2026)",
         tar="حقيبة جاهزية استمرارية الأعمال — المركز الوطني (الربع الثاني 2026)",
         pl="2026-Q2-BCM", ps="2026-04-01", pe="2026-06-30",
         risk=jdump({"en": "Continuity risks", "ar": "مخاطر الاستمرارية"}),
         pdpl=None,
         custom=jdump({"en": "Last DR exercise: pass, RTO met", "ar": "آخر تمرين تعافٍ: ناجح وتحقق زمن التعافي",
                        "ncema": {"en": "NCEMA readiness rating: Green", "ar": "تقييم الجاهزية: أخضر"}}),
         drafted_by="Business Continuity", approved_by=None),
    dict(ex="BP-GOV-2026-Q2-INTEGRITY", aud="EXEC", status="DRAFT",
         ten="Annual Integrity & Compliance Pack — Nazaha (2026)",
         tar="حقيبة النزاهة والالتزام السنوية — نزاهة (2026)",
         pl="2026-FY-INTEGRITY", ps="2026-01-01", pe="2026-12-31",
         risk=jdump({"en": "Fraud & integrity risks", "ar": "مخاطر الاحتيال والنزاهة"}),
         pdpl=None,
         custom=jdump({"en": "COI disclosures 100%, 0 substantiated violations", "ar": "إقرارات تعارض المصالح 100%، لا مخالفات مثبتة",
                        "nazaha": {"en": "Whistleblowing channel active", "ar": "قناة الإبلاغ عن المخالفات مفعّلة"}}),
         drafted_by="Compliance", approved_by=None),
]


def seed_packs(cur) -> None:
    print("\n# board_pack.packs")
    for p in PACKS:
        cur.execute("SELECT id FROM board_pack.packs WHERE organisation_id=? AND external_id=?", ORG, p["ex"])
        if cur.fetchone():
            print(f"  = {p['ex']}")
            continue
        st = p["status"]
        subm = "SYSUTCDATETIME()" if st in ("IN_REVIEW", "APPROVED", "PUBLISHED") else "NULL"
        rev = "SYSUTCDATETIME()" if st in ("APPROVED", "PUBLISHED") else "NULL"
        appr = "SYSUTCDATETIME()" if st in ("APPROVED", "PUBLISHED") else "NULL"
        cur.execute(
            f"""INSERT INTO board_pack.packs
                 (external_id, organisation_id, title_en, title_ar, period_label,
                  period_start, period_end, audience, risk_summary_json, pdpl_summary_json,
                  custom_sections_json, status, drafted_by, submitted_for_review_at,
                  reviewed_at, reviewed_by, approved_at, approved_by, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,{subm},{rev},?,{appr},?,0)""",
            p["ex"], ORG, p["ten"], p["tar"], p["pl"], p["ps"], p["pe"], p["aud"],
            p["risk"], p["pdpl"], p["custom"], st, p["drafted_by"],
            (PRESIDENT_AR if st in ("APPROVED", "PUBLISHED") else None), p["approved_by"],
        )
        bump("board_pack.packs")
        print(f"  + {p['ex']}  [{st}]  {p['ten']}")


# ─────────────────────────────────────────────────────────────────────────────
# 8. approvals.requests  (8 requests wired to the records above)
# ─────────────────────────────────────────────────────────────────────────────
APPROVALS = [
    dict(ex="APR-GOV-2026-001", et="POLICY", eid="POL-GOV-001", pr="HIGH", status="APPROVED",
         ten="Publish the Corporate Governance Manual", tar="نشر دليل الحوكمة المؤسسية",
         sen="Approve publication of POL-GOV-001 v2.0.", sar="اعتماد نشر دليل الحوكمة المؤسسية الإصدار 2.0.",
         req="compliance", asg="admin", decided="admin"),
    dict(ex="APR-GOV-2026-002", et="POLICY", eid="POL-NCA-ECC-001", pr="HIGH", status="APPROVED",
         ten="Approve the Cybersecurity Policy (NCA ECC)", tar="اعتماد سياسة الأمن السيبراني (الضوابط الأساسية)",
         sen="Approve POL-NCA-ECC-001 v2.0 mapped to ECC-2.", sar="اعتماد سياسة الأمن السيبراني الإصدار 2.0 وفق الضوابط الأساسية.",
         req="compliance", asg="admin", decided="admin"),
    dict(ex="APR-GOV-2026-003", et="POLICY_EXCEPTION", eid="EXC-GOV-2026-001", pr="HIGH", status="APPROVED",
         ten="Approve the legacy-mainframe MFA exception", tar="اعتماد استثناء التحقق متعدد العوامل للنظام القديم",
         sen="Approve EXC-GOV-2026-001 with compensating controls.", sar="اعتماد الاستثناء مع الضوابط التعويضية.",
         req="rmanager", asg="admin", decided="admin"),
    dict(ex="APR-GOV-2026-004", et="BOARD_PACK", eid="BP-GOV-2026-Q2-BOARD", pr="HIGH", status="APPROVED",
         ten="Distribute the Q2 Board Pack to the Board", tar="توزيع حقيبة المجلس للربع الثاني على المجلس",
         sen="Approve distribution of the Vision-2030 board pack to COM-BOARD.", sar="اعتماد توزيع حقيبة رؤية 2030 على مجلس الإدارة.",
         req="admin", asg="admin", decided="admin"),
    dict(ex="APR-GOV-2026-005", et="CAPA", eid="CAPA-GOV-2026-005", pr="MEDIUM", status="APPROVED",
         ten="Close the PDPL consent-capture CAPA", tar="إغلاق إجراء التقاط الموافقة (الخصوصية)",
         sen="Approve closure of CAPA-GOV-2026-005 after verification.", sar="اعتماد إغلاق الإجراء بعد التحقق.",
         req="compliance", asg="auditor", decided="auditor"),
    dict(ex="APR-GOV-2026-006", et="AI_MODEL", eid="POL-AI-001", pr="HIGH", status="PENDING",
         ten="Approve beneficiary-service AI assistant deployment", tar="اعتماد نشر المساعد الذكي لخدمة المستفيدين",
         sen="SDAIA-gated approval of an AI assistant under the Responsible AI policy.", sar="اعتماد مشروط بسدايا لمساعد ذكي وفق سياسة الذكاء الاصطناعي المسؤول.",
         req="rmanager", asg="admin", decided=None),
    dict(ex="APR-GOV-2026-007", et="DATA_CLASS", eid="EXC-GOV-2026-003", pr="MEDIUM", status="PENDING",
         ten="Approve open-data reclassification for DGA portal", tar="اعتماد إعادة تصنيف البيانات المفتوحة لبوابة الحكومة الرقمية",
         sen="Approve reclassification of aggregated stats to Public for DGA.", sar="اعتماد إعادة تصنيف الإحصاءات المجمّعة كبيانات عامة للهيئة.",
         req="compliance", asg="admin", decided=None),
    dict(ex="APR-GOV-2026-008", et="PROCUREMENT", eid="EXC-GOV-2026-004", pr="MEDIUM", status="DELEGATED",
         ten="Approve single-source procurement (Etimad)", tar="اعتماد الشراء من مصدر وحيد (اعتماد)",
         sen="Single-source purchase approval delegated for review.", sar="تفويض اعتماد الشراء من مصدر وحيد للمراجعة.",
         req="rmanager", asg="compliance", decided=None, deleg="admin",
         deleg_reason="تفويض المراجعة الفنية لمسؤول الالتزام أثناء سفر المعتمد"),
]


def seed_approvals(cur) -> None:
    print("\n# approvals.requests")
    for a in APPROVALS:
        cur.execute("SELECT id FROM approvals.requests WHERE organisation_id=? AND external_id=?", ORG, a["ex"])
        if cur.fetchone():
            print(f"  = {a['ex']}")
            continue
        st = a["status"]
        decided = "SYSUTCDATETIME()" if st in ("APPROVED", "REJECTED") else "NULL"
        orig = a.get("deleg") or a["asg"]  # original assignee (pre-delegation if delegated)
        cur.execute(
            f"""INSERT INTO approvals.requests
                 (external_id, organisation_id, entity_type, entity_id, title_en, title_ar,
                  summary_en, summary_ar, requested_by, assigned_to, original_assigned_to,
                  priority, status, decided_at, decided_by, delegated_from, delegation_reason_en,
                  is_deleted)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,{decided},?,?,?,0)""",
            a["ex"], ORG, a["et"], a["eid"], a["ten"], a["tar"],
            a["sen"], a["sar"], a["req"], a["asg"], orig,
            a["pr"], st, a.get("decided"), a.get("deleg"), a.get("deleg_reason"),
        )
        bump("approvals.requests")
        print(f"  + {a['ex']}  [{st}]  {a['ten']}")


# ─────────────────────────────────────────────────────────────────────────────
# 9. signature.digital_signature_transactions  (8 e-sign records)
# ─────────────────────────────────────────────────────────────────────────────
def pack_id(cur, ex: str) -> int | None:
    cur.execute("SELECT id FROM board_pack.packs WHERE organisation_id=? AND external_id=?", ORG, ex)
    r = cur.fetchone()
    return r[0] if r else None


def capa_id(cur, ex: str) -> int | None:
    cur.execute("SELECT id FROM capa.tasks WHERE organisation_id=? AND external_id=?", ORG, ex)
    r = cur.fetchone()
    return r[0] if r else None


def seed_signatures(cur) -> None:
    print("\n# signature.digital_signature_transactions")
    # resolve related ids lazily inside the plan builder
    plan = [
        dict(ex="DSIG-GOV-2026-001", ret="policy", rid_code=("policy", "POL-GOV-001"),
             label="POL-GOV-001 — Corporate Governance Manual", provider="NAFATH",
             signer=PRESIDENT_AR, role=PRESIDENT_AR, suid=1, iuid=1, status="SIGNED",
             pen="President's approval signature on the Governance Manual",
             par="توقيع رئيس الهيئة على اعتماد دليل الحوكمة المؤسسية"),
        dict(ex="DSIG-GOV-2026-002", ret="board_pack", rid_code=("pack", "BP-GOV-2026-Q2-BOARD"),
             label="BP-GOV-2026-Q2-BOARD — Vision 2030 board pack", provider="NAFATH",
             signer=BOARD_CHAIR_AR, role=BOARD_CHAIR_AR, suid=1, iuid=1, status="SIGNED",
             pen="Board chair acknowledgement of the Q2 board pack",
             par="اعتماد رئيس مجلس الإدارة لحقيبة المجلس للربع الثاني"),
        dict(ex="DSIG-GOV-2026-003", ret="policy", rid_code=("policy", "POL-NCA-ECC-001"),
             label="POL-NCA-ECC-001 — Cybersecurity Policy", provider="INTERNAL",
             signer=GOV_POL_AR, role=GOV_POL_AR, suid=3, iuid=3, status="SIGNED",
             pen="GOV_POL head sign-off on the Cybersecurity Policy",
             par="توقيع إدارة السياسات والحوكمة على سياسة الأمن السيبراني"),
        dict(ex="DSIG-GOV-2026-004", ret="regulatory_evidence_packet", rid_code=None, rid=0,
             label="PDPL evidence packet — Q2 2026", provider="INTERNAL",
             signer="مسؤول حماية البيانات", role="DPO", suid=3, iuid=3, status="REQUESTED",
             pen="DPO confirmation of the PDPL evidence packet",
             par="تأكيد مسؤول حماية البيانات على حزمة أدلة الالتزام بحماية البيانات"),
        dict(ex="DSIG-GOV-2026-005", ret="nca_compliance_attestation", rid_code=None, rid=0,
             label="NCA ECC assessment completeness", provider="INTERNAL",
             signer="رئيس الأمن السيبراني", role="CISO", suid=1, iuid=1, status="REQUESTED",
             pen="CISO attestation of NCA ECC assessment completeness",
             par="إقرار رئيس الأمن السيبراني باكتمال تقييم الضوابط الأساسية"),
        dict(ex="DSIG-GOV-2026-006", ret="bcm_plan", rid_code=None, rid=0,
             label="Annual continuity-plan sign-off (NCEMA)", provider="INTERNAL",
             signer="مدير استمرارية الأعمال", role="BCM_OFFICER", suid=2, iuid=2, status="SIGNED",
             pen="Annual sign-off of the business continuity plan",
             par="الاعتماد السنوي لخطة استمرارية الأعمال"),
        dict(ex="DSIG-GOV-2026-007", ret="policy_exception", rid_code=None, rid=0,
             label="EXC-GOV-2026-004 — single-source procurement", provider="INTERNAL",
             signer=GOV_POL_AR, role="PROCUREMENT_LEAD", suid=3, iuid=1, status="REJECTED",
             pen="Procurement single-source exception signature",
             par="توقيع استثناء الشراء من مصدر وحيد",
             reject="نقص في مبرر السعر؛ يلزم مقارنة أسعار إضافية قبل الاعتماد"),
        dict(ex="DSIG-GOV-2026-008", ret="data_classification", rid_code=None, rid=0,
             label="Open-data reclassification approval", provider="INTERNAL",
             signer=GOV_POL_AR, role="DATA_OWNER", suid=3, iuid=3, status="EXPIRED",
             pen="Data-classification approval (expired)",
             par="اعتماد تصنيف البيانات (منتهي الصلاحية)"),
    ]
    for s in plan:
        cur.execute(
            "SELECT id FROM signature.digital_signature_transactions WHERE organisation_id=? AND external_id=?",
            ORG, s["ex"],
        )
        if cur.fetchone():
            print(f"  = {s['ex']}")
            continue
        # resolve related_entity_id
        if s.get("rid_code"):
            kind, code = s["rid_code"]
            if kind == "policy":
                rid = policy_id(cur, code)
            elif kind == "pack":
                rid = pack_id(cur, code)
            elif kind == "capa":
                rid = capa_id(cur, code)
            else:
                rid = None
            if rid is None:
                rid = 0
        else:
            rid = s.get("rid", 0)
        st = s["status"]
        sh = HASH if st == "SIGNED" else None
        rr = s.get("reject") if st == "REJECTED" else None
        sts = "SYSUTCDATETIME()" if st == "SIGNED" else "NULL"
        cert = f"CERT-{s['ex']}" if st == "SIGNED" else None
        cur.execute(
            f"""INSERT INTO signature.digital_signature_transactions
                 (organisation_id, related_entity_type, related_entity_id, related_entity_label,
                  signer_user_id, signer_name, signer_role, initiator_user_id, initiator_name,
                  signature_provider, purpose, purpose_ar, signed_document_hash, certificate_reference,
                  status, rejection_reason, signature_timestamp, external_id,
                  is_deleted, is_active, created_at, updated_at, row_version)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,{sts},?,0,1,SYSUTCDATETIME(),SYSUTCDATETIME(),0)""",
            ORG, s["ret"], rid, s["label"],
            s["suid"], s["signer"], s["role"], s["iuid"], GOV_POL_AR,
            s["provider"], s["pen"], s["par"], sh, cert,
            st, rr, s["ex"],
        )
        bump("signature.digital_signature_transactions")
        print(f"  + {s['ex']}  [{st}]  {s['label']}")


# ─────────────────────────────────────────────────────────────────────────────
def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    seed_policies(cur)
    seed_exceptions(cur)
    seed_attestations(cur)
    seed_kb_categories(cur)
    seed_articles(cur)
    seed_capa(cur)
    seed_packs(cur)
    seed_approvals(cur)
    seed_signatures(cur)

    total = sum(inserted.values())
    print("\n" + "=" * 70)
    print("INSERTED THIS RUN")
    print("=" * 70)
    for tbl in ["policy.documents", "policy.exceptions", "policy.attestations",
                "kb.categories", "kb.articles", "capa.tasks", "board_pack.packs",
                "approvals.requests", "signature.digital_signature_transactions"]:
        print(f"  {tbl:<45} {inserted.get(tbl, 0)}")
    print(f"  {'TOTAL':<45} {total}")
    if skipped:
        print("\nSKIPPED:")
        for t, r in skipped:
            print(f"  ! {t}: {r}")

    # ── Verification counts (org 1) ──
    print("\n" + "=" * 70)
    print("VERIFICATION — org 1 row counts")
    print("=" * 70)
    checks = [
        ("policy.documents", "policy.documents WHERE organisation_id=1 AND is_deleted=0"),
        ("  of which POL-* (gov)", "policy.documents WHERE organisation_id=1 AND is_deleted=0 AND policy_code LIKE 'POL-%'"),
        ("policy.exceptions", "policy.exceptions WHERE organisation_id=1 AND is_deleted=0"),
        ("  of which EXC-GOV-*", "policy.exceptions WHERE organisation_id=1 AND is_deleted=0 AND exception_code LIKE 'EXC-GOV-%'"),
        ("policy.attestations", "policy.attestations WHERE organisation_id=1 AND is_deleted=0"),
        ("  of which ATT-POL-*", "policy.attestations WHERE organisation_id=1 AND is_deleted=0 AND external_id LIKE 'ATT-POL-%'"),
        ("kb.categories", "kb.categories WHERE organisation_id=1 AND is_deleted=0"),
        ("kb.articles", "kb.articles WHERE organisation_id=1 AND is_deleted=0"),
        ("  of which gov-*", "kb.articles WHERE organisation_id=1 AND is_deleted=0 AND slug LIKE 'gov-%'"),
        ("capa.tasks", "capa.tasks WHERE organisation_id=1 AND is_deleted=0"),
        ("  of which CAPA-GOV-*", "capa.tasks WHERE organisation_id=1 AND is_deleted=0 AND external_id LIKE 'CAPA-GOV-%'"),
        ("board_pack.packs", "board_pack.packs WHERE organisation_id=1 AND is_deleted=0"),
        ("  of which BP-GOV-*", "board_pack.packs WHERE organisation_id=1 AND is_deleted=0 AND external_id LIKE 'BP-GOV-%'"),
        ("approvals.requests", "approvals.requests WHERE organisation_id=1 AND is_deleted=0"),
        ("  of which APR-GOV-*", "approvals.requests WHERE organisation_id=1 AND is_deleted=0 AND external_id LIKE 'APR-GOV-%'"),
        ("signature.dst", "signature.digital_signature_transactions WHERE organisation_id=1 AND is_deleted=0"),
        ("  of which DSIG-GOV-*", "signature.digital_signature_transactions WHERE organisation_id=1 AND is_deleted=0 AND external_id LIKE 'DSIG-GOV-%'"),
    ]
    for label, frm in checks:
        cur.execute(f"SELECT COUNT(*) FROM {frm}")
        print(f"  {label:<32} {cur.fetchone()[0]}")

    conn.close()
    print("\nDone.")


if __name__ == "__main__":
    main()
