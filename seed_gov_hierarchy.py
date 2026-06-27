# -*- coding: utf-8 -*-
"""Seed a realistic Saudi government authority org hierarchy into platform.departments.

Models a typical Saudi *General Authority* (هيئة عامة) org chart for the default
tenant (organisation_id = 1 — the only tenant with working logins, so the chart
is immediately visible to the `admin` account and on the GRC Expert page, which
is the one screen that lists departments).

Structure (4 levels, bilingual EN/AR):

    L0  Office of the President                       مكتب رئيس الهيئة
    L1  ├─ oversight/staff: Internal Audit, Cybersecurity, GRC, Legal,
    │   │  Strategy & Performance, Corporate Communication, Board Secretariat
    │   └─ Deputyships (وكالات): Shared Services, Digital Transformation & Data,
    │      Operations & Services
    L2  General departments under each deputyship / GRC
    L3  Sections (أقسام) under a few departments to show depth

The eight pre-existing flat departments (CEO, RISK, CYBER, PRIVACY, BCM,
PROCUREMENT, INTERNAL_AUD, COMPLIANCE) are *folded into* this tree (re-parented)
rather than left as orphan roots — no duplication, no soft-deletes.

Idempotent: every node is upserted by (organisation_id, code). Re-running only
re-points parents / refreshes metadata; it never double-inserts and never wipes
a head_user_id that is already set.

    Usage:  python seed_gov_hierarchy.py
"""
from __future__ import annotations

import sys
import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

# Match the working connection used by seed_real_data.py, with a Driver-18
# fallback for machines that only ship the newer MSODBC.
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


ORG = 1  # default tenant (Office of the President sits here)

# code, parent_code, name_en, name_ar, head_username, cost_centre, description
# NOTE: ordered top-down (parents before children) so parent_id always resolves.
NODES: list[tuple] = [
    # ── L0 — root ────────────────────────────────────────────────────────────
    ("CEO", None, "Office of the President", "مكتب رئيس الهيئة",
     "admin", "CC-1000",
     "Executive leadership of the Authority; sets strategy and chairs the executive committee."),

    # ── L1 — oversight & staff functions (report to the President) ────────────
    ("INTERNAL_AUD", "CEO", "Internal Audit", "المراجعة الداخلية",
     "auditor", "CC-1100",
     "Independent assurance reporting to the Audit Committee; risk-based audit plan."),
    ("CYBER", "CEO", "Cybersecurity", "الأمن السيبراني",
     None, "CC-1200",
     "Implements NCA Essential Cybersecurity Controls (ECC) and operates the SOC."),
    ("GOV_GRC", "CEO", "Governance, Risk & Compliance", "الحوكمة وإدارة المخاطر والالتزام",
     "compliance", "CC-1300",
     "Enterprise governance, risk management and regulatory compliance oversight."),
    ("LEGAL", "CEO", "Legal Affairs", "الشؤون القانونية",
     None, "CC-1400",
     "Legal counsel, contracts, regulations and litigation."),
    ("STRATEGY", "CEO", "Strategy & Performance", "الاستراتيجية وقياس الأداء",
     None, "CC-1500",
     "Strategic planning, KPIs and institutional performance (Vision 2030)."),
    ("COMMS", "CEO", "Corporate Communication", "الاتصال المؤسسي",
     None, "CC-1600",
     "Corporate communication, media relations and the official spokesperson."),
    ("BOARD_SEC", "CEO", "Board & Committees Secretariat", "أمانة المجلس واللجان",
     None, "CC-1700",
     "Secretariat for the board and its committees; resolutions and follow-up."),

    # ── L1 — Deputyships (وكالات) ─────────────────────────────────────────────
    ("VP_SHARED", "CEO", "Deputyship of Shared Services", "وكالة الخدمات المشتركة",
     None, "CC-2000",
     "Corporate support: human resources, finance, IT, procurement and facilities."),
    ("VP_DIGITAL", "CEO", "Deputyship of Digital Transformation & Data",
     "وكالة التحول الرقمي والبيانات",
     None, "CC-3000",
     "Digital government delivery, data management and artificial intelligence."),
    ("VP_OPS", "CEO", "Deputyship of Operations & Services", "وكالة العمليات والخدمات",
     None, "CC-4000",
     "Core sector operations, licensing, inspection and beneficiary services."),

    # ── L2 — under Governance, Risk & Compliance ─────────────────────────────
    ("RISK", "GOV_GRC", "Enterprise Risk Management", "إدارة المخاطر المؤسسية",
     "rmanager", "CC-1310",
     "ERM framework, corporate risk register, KRIs and risk appetite."),
    ("BCM", "GOV_GRC", "Business Continuity", "استمرارية الأعمال",
     None, "CC-1320",
     "Business impact analysis, continuity and disaster recovery (ISO 22301 / NCA)."),
    ("COMPLIANCE", "GOV_GRC", "Compliance", "الالتزام",
     "compliance", "CC-1330",
     "Regulatory obligations, controls, attestations and compliance monitoring."),
    ("GOV_POL", "GOV_GRC", "Governance & Policy", "الحوكمة والسياسات",
     None, "CC-1340",
     "Corporate governance framework, delegations of authority and policy management."),

    # ── L2 — under Shared Services ───────────────────────────────────────────
    ("HR", "VP_SHARED", "Human Resources", "الموارد البشرية",
     None, "CC-2100",
     "Workforce planning, payroll, and Saudization compliance (HRSD)."),
    ("FINANCE", "VP_SHARED", "Finance", "الشؤون المالية",
     None, "CC-2200",
     "Budgeting, accounting, treasury and financial control."),
    ("IT", "VP_SHARED", "Information Technology", "تقنية المعلومات",
     None, "CC-2300",
     "IT infrastructure, enterprise applications and the service desk."),
    ("PROCUREMENT", "VP_SHARED", "Procurement & Supply Chain", "المشتريات وسلسلة الإمداد",
     None, "CC-2400",
     "Procurement, contracts and vendor management (Etimad)."),
    ("GEN_SERVICES", "VP_SHARED", "General Services & Facilities", "الخدمات العامة والمرافق",
     None, "CC-2500",
     "Facilities, fleet, security guarding and administrative services."),

    # ── L2 — under Digital Transformation & Data ─────────────────────────────
    ("DIGITAL", "VP_DIGITAL", "Digital Transformation", "التحول الرقمي",
     None, "CC-3100",
     "Digital government delivery (DGA), automation and e-services."),
    ("DATA_MGMT", "VP_DIGITAL", "Data Management Office", "مكتب إدارة البيانات",
     None, "CC-3200",
     "NDMO data management standards and national data governance (SDAIA)."),
    ("BI_AI", "VP_DIGITAL", "Data & AI", "البيانات والذكاء الاصطناعي",
     None, "CC-3300",
     "Business intelligence, advanced analytics and AI use-cases."),
    ("PRIVACY", "VP_DIGITAL", "Privacy Office", "مكتب الخصوصية",
     None, "CC-3400",
     "PDPL compliance: ROPA, data-subject requests and breach notification."),

    # ── L2 — under Operations & Services ─────────────────────────────────────
    ("OPERATIONS", "VP_OPS", "Core Operations", "العمليات الرئيسية",
     None, "CC-4100",
     "Delivery of the Authority's core sector mandate."),
    ("LICENSING", "VP_OPS", "Licensing & Permits", "التراخيص والتصاريح",
     None, "CC-4200",
     "Issuance, renewal and revocation of sector licenses and permits."),
    ("INSPECTION", "VP_OPS", "Inspection & Enforcement", "الرقابة والتفتيش",
     None, "CC-4300",
     "Field inspection, monitoring and enforcement of regulations."),
    ("BENEFICIARY", "VP_OPS", "Beneficiary Experience", "تجربة المستفيد",
     None, "CC-4400",
     "Beneficiary/customer experience and service excellence (Vision 2030)."),

    # ── L3 — sections (أقسام) ─────────────────────────────────────────────────
    ("SOC", "CYBER", "Security Operations Center", "مركز العمليات الأمنية",
     None, "CC-1210",
     "24/7 monitoring, threat detection and incident response."),
    ("CYBER_GRC", "CYBER", "Cyber Risk & Compliance", "مخاطر والتزام الأمن السيبراني",
     None, "CC-1220",
     "Cyber risk assessment, ECC compliance and security audits."),
    ("HR_OPS", "HR", "HR Operations", "عمليات الموارد البشرية",
     None, "CC-2110",
     "Employee services, payroll processing and personnel records."),
    ("HR_DEV", "HR", "Training & Development", "التدريب والتطوير",
     None, "CC-2120",
     "Capability building, training and talent development."),
    ("DATA_GOV", "DATA_MGMT", "Data Governance", "حوكمة البيانات",
     None, "CC-3210",
     "Data ownership, classification, cataloguing and stewardship."),
    ("DATA_QUALITY", "DATA_MGMT", "Data Quality", "جودة البيانات",
     None, "CC-3220",
     "Data quality measurement, profiling and remediation."),
]


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    def dept_id(code: str | None):
        if not code:
            return None
        cur.execute(
            "SELECT id FROM platform.departments WHERE organisation_id=? AND code=?",
            ORG, code,
        )
        row = cur.fetchone()
        return row[0] if row else None

    def user_id(username: str | None):
        if not username:
            return None
        cur.execute("SELECT id FROM iam.users WHERE username=?", username)
        row = cur.fetchone()
        return row[0] if row else None

    inserted = updated = 0
    for code, parent_code, name_en, name_ar, head, cc, desc in NODES:
        pid = dept_id(parent_code)
        if parent_code and pid is None:
            print(f"  ! parent '{parent_code}' missing for '{code}' — skipped")
            continue
        hid = user_id(head)
        existing = dept_id(code)
        if existing is not None:
            # Re-point parent + refresh metadata. COALESCE keeps an already-set
            # head if this run doesn't supply one (so re-runs never wipe heads).
            cur.execute(
                """UPDATE platform.departments
                      SET parent_id   = ?,
                          name_en     = ?,
                          name_ar     = ?,
                          head_user_id = COALESCE(?, head_user_id),
                          cost_centre = ?,
                          description = ?,
                          status      = 'ACTIVE',
                          is_deleted  = 0,
                          updated_at  = SYSUTCDATETIME(),
                          updated_by  = 1
                    WHERE id = ?""",
                pid, name_en, name_ar, hid, cc, desc, existing,
            )
            updated += 1
            print(f"  ~ {code:<13} {name_en}")
        else:
            cur.execute(
                """INSERT INTO platform.departments
                       (organisation_id, external_id, code, name_en, name_ar,
                        parent_id, head_user_id, cost_centre, description, status,
                        created_at, updated_at, created_by, is_deleted)
                   VALUES (?, NEWID(), ?, ?, ?, ?, ?, ?, ?, 'ACTIVE',
                           SYSUTCDATETIME(), SYSUTCDATETIME(), 1, 0)""",
                ORG, code, name_en, name_ar, pid, hid, cc, desc,
            )
            inserted += 1
            print(f"  + {code:<13} {name_en}")

    print(f"\nDone. inserted={inserted}, updated={updated}, total nodes={len(NODES)}")

    # ── Verification: print the tree as it now stands ────────────────────────
    print("\nResulting hierarchy (organisation_id = 1):\n")
    cur.execute(
        """WITH tree AS (
               SELECT id, parent_id, code, name_en, name_ar, 0 AS lvl,
                      CAST(RIGHT('000'+CAST(id AS varchar(10)),10) AS varchar(900)) AS path
                 FROM platform.departments
                WHERE organisation_id = ? AND is_deleted = 0 AND parent_id IS NULL
               UNION ALL
               SELECT d.id, d.parent_id, d.code, d.name_en, d.name_ar, t.lvl + 1,
                      CAST(t.path + '/' + RIGHT('000'+CAST(d.id AS varchar(10)),10) AS varchar(900))
                 FROM platform.departments d
                 JOIN tree t ON d.parent_id = t.id
                WHERE d.organisation_id = ? AND d.is_deleted = 0
           )
           SELECT lvl, code, name_en, name_ar FROM tree ORDER BY path""",
        ORG, ORG,
    )
    for lvl, code, name_en, name_ar in cur.fetchall():
        indent = "    " * lvl
        print(f"{indent}{name_en}  —  {name_ar}  [{code}]")

    cur.execute(
        "SELECT COUNT(*) FROM platform.departments WHERE organisation_id=? AND is_deleted=0",
        ORG,
    )
    print(f"\nLive department count for org {ORG}: {cur.fetchone()[0]}")
    conn.close()


if __name__ == "__main__":
    main()
