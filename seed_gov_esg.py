# -*- coding: utf-8 -*-
"""Seed a coherent public-authority ESG / sustainability layer into the `sust` schema.

Companion to seed_gov_committees.py. Targets the default tenant (organisation_id = 1)
— the only tenant with working logins, headed by a President (رئيس الهيئة) and a Saudi
GENERAL AUTHORITY (هيئة عامة). The existing org-1 rows in `sust` are framed as a
COMMERCIAL BANK (green Sukuk, PCAF financed-emissions loan book, NGFS scenarios,
Bureau Veritas). Those rows MUST NOT be modified (hard safety rule). This script adds a
DISTINCT, additive FY2026/FY2027 GOVERNMENT layer that hangs off two NEW reporting-year
rows, keyed on natural codes so a second run inserts 0.

What it builds (all organisation_id = 1):

  reporting_year       +2   FY2026 (anchor) + FY2027 (active cycle)
  material_topic       +9   FY2027 public-authority double-materiality topics
  sgi_commitment       +6   the authority's OWN Saudi Green Initiative pledges
  ghg_emission_record  +8   FY2027 operational GHG inventory (Scope 1/2/3)
  esg_metric           +9   FY2027 public-authority ESG KPIs
  tcfd_climate_scenario+3   IPCC-based scenarios (no portfolio loss figures)
  climate_risk_opportunity +7  FY2027 facility/service climate risks & opportunities
  tcfd_item            +11  full FY2027 TCFD 11-recommendation matrix
  cma_disclosure       +11  FY2027 CMA art.91-94 disclosure-readiness matrix
  corrective_action    +8   FY2027 CAPA backlog (one per source_type)
  evidence             +8   FY2027 evidence artefacts
  sustainability_report+3   FY2027 board/regulator deliverables (FK -> FY2027 year)

Linkage: NO committee_id / department_id FK columns exist anywhere in `sust`. Linkage
to COM-ESG (Sustainability Committee) and to platform.departments is DENORMALIZED TEXT
only — 'Sustainability Committee (COM-ESG)' in notes / report_section_reference, and real
Saudi directorate names in owner_name / responsible_department.

Skipped (3 tables) — bank-only PCAF/portfolio constructs with no meaning for a هيئة
(no loan book / investment portfolio): financed_emissions_record, financed_emissions_target,
tcfd_sector_impact. organisation_settings is a per-org singleton already present and can
only be "deepened" by editing the existing row, which the safety rules forbid — also left
untouched.

Idempotent: every INSERT is guarded by an existence check on a natural key. A second run
inserts 0. INSERT-ONLY: no DELETE / TRUNCATE / DROP / ALTER; org 1 only; `sust` only.

    Usage:  python seed_gov_esg.py
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
ADMIN_UID = 1            # iam.users.id for bigint audit columns (admin)
FY = 2027               # the active government cycle year
ANCHOR = 2026           # the back-anchor year

# Common denormalized actors (Saudi public-authority framing).
COMMITTEE_REF = "Sustainability Committee (COM-ESG)"
PRESIDENT = "President of the Authority"
DIR_STRATEGY = "Strategy & Performance"
DIR_GS = "General Services & Facilities"
DIR_DATA = "Data Management Office"
DIR_HR = "Human Resources"
DIR_IT = "Information Technology"
DIR_AUDIT = "Internal Audit Office"
DIR_COMPLIANCE = "Compliance"

# A counter object so every section can report inserts and the grand total is summed.
COUNTS: dict[str, int] = {}
SKIPS: list[tuple[str, int, str]] = []


def section(name: str, inserted: int) -> None:
    COUNTS[name] = inserted
    print(f"  [{name:<26}] +{inserted}")


# ─────────────────────────────────────────────────────────────────────────────
def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    print("Seeding public-authority ESG layer (sust) for organisation_id = 1\n")

    # ── 0. Confirm COM-ESG exists (denormalized linkage target) ──────────────
    cur.execute(
        "SELECT id, name_en FROM admin.committees WHERE organisation_id=? AND code='COM-ESG'",
        ORG,
    )
    esg = cur.fetchone()
    print(f"Linkage anchor: COM-ESG -> {esg[1] if esg else 'NOT FOUND'} "
          f"(denormalized text only; no FK column in sust)\n")

    # ============================================================ reporting_year
    # Natural key: (organisation_id, year). Existing 2025 row untouched.
    ry_rows = [
        dict(year=ANCHOR, status="APPROVED",
             entity_en="General Authority — TSS",
             entity_ar="الهيئة العامة — منصة الحوكمة والمخاطر والامتثال",
             ps="2026-01-01", pe="2026-12-31",
             prepared=DIR_STRATEGY, reviewed=COMMITTEE_REF, approved=PRESIDENT,
             readiness=78, gaps=6,
             notes=("سنة مرجعية لتثبيت بيانات الاستدامة لعام 2026 للهيئة العامة، "
                    "بإشراف لجنة الاستدامة (COM-ESG) ومواءمة رؤية 2030 والمبادرة "
                    "السعودية الخضراء.")),
        dict(year=FY, status="DATA_COLLECTION",
             entity_en="General Authority — TSS",
             entity_ar="الهيئة العامة — منصة الحوكمة والمخاطر والامتثال",
             ps="2027-01-01", pe="2027-12-31",
             prepared=DIR_STRATEGY, reviewed=COMMITTEE_REF, approved=PRESIDENT,
             readiness=62, gaps=11,
             notes=("دورة الإفصاح النشطة لعام 2027 للهيئة العامة. تجمع البيانات عبر "
                    "الإدارات بإشراف لجنة الاستدامة (COM-ESG) ومواءمة رؤية 2030 "
                    "والمبادرة السعودية الخضراء ومعايير المركز الوطني لإدارة الأزمات "
                    "(NCEMA) ومكتب إدارة البيانات الوطنية (NDMO).")),
    ]
    ins = 0
    for r in ry_rows:
        cur.execute("SELECT id FROM sust.reporting_year WHERE organisation_id=? AND year=?",
                    ORG, r["year"])
        if cur.fetchone():
            continue
        cur.execute(
            """INSERT INTO sust.reporting_year
                   (organisation_id, external_id, year, period_start, period_end,
                    reporting_entity_en, reporting_entity_ar, status,
                    prepared_by, reviewed_by, approved_by, approved_at,
                    readiness_score_pct, open_gaps_count, notes,
                    is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                       CASE WHEN ?='APPROVED' THEN SYSUTCDATETIME() ELSE NULL END,
                       ?, ?, ?, 0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-RY-{r['year']}", r["year"], r["ps"], r["pe"],
            r["entity_en"], r["entity_ar"], r["status"],
            r["prepared"], r["reviewed"], r["approved"], r["status"],
            r["readiness"], r["gaps"], r["notes"],
        )
        ins += 1
    section("reporting_year", ins)

    def ry_id(year: int):
        cur.execute("SELECT id FROM sust.reporting_year WHERE organisation_id=? AND year=?",
                    ORG, year)
        row = cur.fetchone()
        return row[0] if row else None

    fy2027_ry = ry_id(FY)

    # ============================================================== material_topic
    # key: (organisation_id, topic_code). category in ENVIRONMENTAL|SOCIAL|GOVERNANCE.
    mt_rows = [
        ("MT-2027-01", "ENVIRONMENTAL", "Facility energy & water efficiency",
         "كفاءة الطاقة والمياه في مباني الهيئة",
         "Energy and water efficiency across HQ and regional offices.",
         "كفاءة استهلاك الطاقة والمياه في المقر الرئيس والمكاتب الإقليمية.",
         4.6, 4.2, "HIGH", "APPROVED", DIR_GS),
        ("MT-2027-02", "ENVIRONMENTAL", "Government fleet decarbonisation",
         "خفض انبعاثات أسطول مركبات الهيئة",
         "Transition of the authority light-vehicle fleet to electric/low-carbon.",
         "تحويل أسطول مركبات الهيئة الخفيفة إلى الكهرباء/منخفضة الكربون.",
         4.1, 3.8, "MEDIUM", "APPROVED", DIR_GS),
        ("MT-2027-03", "ENVIRONMENTAL", "Paper-to-digital & office-waste reduction",
         "التحول الرقمي وخفض النفايات الورقية والمكتبية",
         "Zero-paper digital transformation and office-waste diversion (DGA-aligned).",
         "التحول الرقمي نحو انعدام الورق وتدوير النفايات المكتبية بمواءمة هيئة الحكومة الرقمية.",
         4.3, 3.9, "HIGH", "APPROVED", DIR_DATA),
        ("MT-2027-04", "SOCIAL", "Saudization & national workforce development",
         "السعودة وتنمية الكوادر الوطنية",
         "Tawteen / Nitaqat performance and development of the authority workforce.",
         "نسب التوطين ونطاقات وتنمية كوادر الهيئة الوطنية.",
         4.8, 4.5, "CRITICAL", "APPROVED", DIR_HR),
        ("MT-2027-05", "SOCIAL", "Beneficiary accessibility & e-service inclusion",
         "شمول المستفيدين وإتاحة الخدمات الإلكترونية",
         "Accessibility and inclusion of citizen/beneficiary e-services.",
         "إتاحة الخدمات الإلكترونية للمستفيدين والمواطنين وشمولهم.",
         4.7, 4.4, "CRITICAL", "APPROVED", DIR_STRATEGY),
        ("MT-2027-06", "SOCIAL", "Employee health, safety & wellbeing",
         "صحة وسلامة ورفاهية الموظفين",
         "Occupational health, safety and wellbeing of authority staff.",
         "الصحة والسلامة المهنية ورفاهية موظفي الهيئة.",
         4.0, 3.6, "MEDIUM", "SCORED", DIR_HR),
        ("MT-2027-07", "GOVERNANCE", "Integrity & anti-corruption",
         "النزاهة ومكافحة الفساد",
         "Integrity, anti-corruption controls and whistleblowing (Nazaha).",
         "النزاهة وضوابط مكافحة الفساد والإبلاغ عن المخالفات بمواءمة هيئة نزاهة.",
         4.9, 4.6, "CRITICAL", "APPROVED", DIR_AUDIT),
        ("MT-2027-08", "GOVERNANCE", "Open data & transparency",
         "البيانات المفتوحة والشفافية",
         "Open-data publishing and transparency (NDMO / DGA).",
         "نشر البيانات المفتوحة والشفافية بمواءمة مكتب إدارة البيانات الوطنية وهيئة الحكومة الرقمية.",
         4.4, 4.0, "HIGH", "APPROVED", DIR_DATA),
        ("MT-2027-09", "GOVERNANCE", "Responsible AI in licensing & inspection",
         "الذكاء الاصطناعي المسؤول في الترخيص والتفتيش",
         "Responsible AI in licensing and inspection decisions (SDAIA principles).",
         "الذكاء الاصطناعي المسؤول في قرارات الترخيص والتفتيش بمواءمة مبادئ سدايا.",
         4.2, 3.9, "HIGH", "SCORED", DIR_DATA),
    ]
    ins = 0
    for code, cat, ten, tar, den, dar, stake, biz, tier, status, owner in mt_rows:
        cur.execute("SELECT id FROM sust.material_topic WHERE organisation_id=? AND topic_code=?",
                    ORG, code)
        if cur.fetchone():
            continue
        score = round(stake * biz / 5.0, 2)
        approved_at = "SYSUTCDATETIME()" if status == "APPROVED" else "NULL"
        cur.execute(
            f"""INSERT INTO sust.material_topic
                    (organisation_id, external_id, topic_code, reporting_year, category,
                     topic_en, topic_ar, description_en, description_ar,
                     stakeholder_importance, business_impact, materiality_score,
                     priority_tier, status, owner_name, notes,
                     approved_at, approved_by, is_deleted, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        {approved_at}, ?, 0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-{code}", code, FY, cat, ten, tar, den, dar,
            stake, biz, score, tier, status, owner,
            f"FY{FY} double-materiality topic — oversight by {COMMITTEE_REF}.",
            PRESIDENT if status == "APPROVED" else None,
        )
        ins += 1
    section("material_topic", ins)

    # ============================================================== sgi_commitment
    # key: (organisation_id, commitment_code). sector in allowed enum; target_year 2020..2100.
    sgi_rows = [
        ("SGI-GOV-01", "ENERGY",
         "HQ & regional offices to 50% renewable by 2030",
         "تزويد مباني الهيئة بـ50% طاقة متجددة بحلول 2030",
         "Rooftop PV plus an SREDA-enabled PPA to reach 50% renewable electricity.",
         "ألواح كهروضوئية على الأسطح واتفاقية شراء طاقة عبر سريدا للوصول إلى 50% كهرباء متجددة.",
         12.0, 2024, 21.0, 50.0, 2030, "% renewable"),
        ("SGI-GOV-02", "TRANSPORT",
         "Convert 40% of light-vehicle fleet to EV by 2030",
         "تحويل 40% من الأسطول الخفيف إلى مركبات كهربائية بحلول 2030",
         "Convert 40% of the authority light-vehicle fleet to electric vehicles.",
         "تحويل 40% من أسطول الهيئة الخفيف إلى مركبات كهربائية.",
         3.0, 2024, 11.0, 40.0, 2030, "% EV"),
        ("SGI-GOV-03", "WASTE",
         "70% office-waste diversion & zero-paper by 2030",
         "تحويل 70% من النفايات المكتبية والتحول لانعدام الورق بحلول 2030",
         "Zero-paper digital transformation and 70% diversion of office waste from landfill.",
         "التحول الرقمي نحو انعدام الورق وتحويل 70% من النفايات المكتبية بعيداً عن المرادم.",
         28.0, 2024, 46.0, 70.0, 2030, "% diverted"),
        ("SGI-GOV-04", "WATER",
         "Cut facility water intensity 30% by 2030",
         "خفض كثافة استهلاك المياه في المرافق 30% بحلول 2030",
         "Reduce authority facility water intensity by 30% versus the 2024 baseline.",
         "خفض كثافة استهلاك المياه في مرافق الهيئة بنسبة 30% مقارنة بخط أساس 2024.",
         100.0, 2024, 88.0, 70.0, 2030, "index (2024=100)"),
        ("SGI-GOV-05", "FORESTRY",
         "Campus & community greening contribution",
         "إسهام الهيئة في تشجير المقر والمجتمع",
         "Contribute to the 10-billion-trees target via campus and community greening.",
         "الإسهام في مستهدف زراعة 10 مليارات شجرة عبر تشجير المقر والمجتمع.",
         500.0, 2025, 2400.0, 8000.0, 2030, "trees planted"),
        ("SGI-GOV-06", "LAND",
         "Green-building retrofit of HQ (Mostadam/LEED) by 2028",
         "إعادة تأهيل المقر وفق المباني الخضراء (مستدام/ليد) بحلول 2028",
         "Retrofit HQ to a Mostadam / LEED green-building certification by 2028.",
         "إعادة تأهيل المقر الرئيس للحصول على شهادة مباني خضراء مستدام/ليد بحلول 2028.",
         0.0, 2024, 35.0, 100.0, 2028, "% retrofit complete"),
    ]
    ins = 0
    for (code, sector, ten, tar, den, dar, base, base_yr, cur_v, tgt, tgt_yr, unit) in sgi_rows:
        cur.execute("SELECT id FROM sust.sgi_commitment WHERE organisation_id=? AND commitment_code=?",
                    ORG, code)
        if cur.fetchone():
            continue
        cur.execute(
            """INSERT INTO sust.sgi_commitment
                   (organisation_id, external_id, commitment_code, sector, title_en, title_ar,
                    description_en, description_ar, baseline_value, baseline_year,
                    current_value, current_value_as_of, target_value, target_year, unit,
                    status, owner_name, next_milestone_date, next_milestone_en, next_milestone_ar,
                    notes_en, notes_ar, is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '2026-12-31', ?, ?, ?,
                       'ACTIVE', ?, '2027-12-31', ?, ?, ?, ?, 0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-{code}", code, sector, ten, tar, den, dar, base, base_yr,
            cur_v, tgt, tgt_yr, unit, DIR_GS,
            "Publish the interim 2030-pathway milestone for FY2027.",
            "نشر مؤشر المسار المرحلي نحو 2030 لعام 2027.",
            f"Authority operational SGI pledge — tracked by {COMMITTEE_REF}.",
            f"التزام تشغيلي للهيئة ضمن المبادرة السعودية الخضراء بمتابعة {COMMITTEE_REF}.",
        )
        ins += 1
    section("sgi_commitment", ins)

    # ========================================================= ghg_emission_record
    # key: (organisation_id, record_code). scope 1/2/3; category free nvarchar (reuse vocab).
    ghg_rows = [
        ("GHG-2027-01", 1, "MOBILE_COMBUSTION", "Fleet diesel & petrol (light vehicles)",
         "وقود الأسطول الخفيف (ديزل وبنزين)", DIR_GS, "HQ & regional offices",
         185000.0, "litres", 0.00254, "tCO2e/litre", 469.9, 100, "VERIFIED"),
        ("GHG-2027-02", 1, "STATIONARY_COMBUSTION", "Backup generators (HQ & data hall)",
         "مولدات الطوارئ (المقر وقاعة البيانات)", DIR_GS, "HQ data hall",
         42000.0, "litres", 0.00268, "tCO2e/litre", 112.6, 100, "VERIFIED"),
        ("GHG-2027-03", 1, "FUGITIVE", "HVAC refrigerant fugitive emissions",
         "انبعاثات تسرب مبردات التكييف", DIR_GS, "HQ",
         95.0, "kg R-410A", 2.088, "tCO2e/kg", 198.4, 100, "SUBMITTED"),
        ("GHG-2027-04", 2, "PURCHASED_ELECTRICITY", "Grid electricity — HQ Riyadh",
         "كهرباء الشبكة — المقر الرئيس بالرياض", DIR_GS, "HQ Riyadh",
         6200000.0, "kWh", 0.00057, "tCO2e/kWh", 3534.0, 100, "VERIFIED"),
        ("GHG-2027-05", 2, "PURCHASED_ELECTRICITY", "Grid electricity — regional offices",
         "كهرباء الشبكة — المكاتب الإقليمية", DIR_GS, "Regional offices",
         2100000.0, "kWh", 0.00057, "tCO2e/kWh", 1197.0, 100, "APPROVED"),
        ("GHG-2027-06", 3, "BUSINESS_TRAVEL", "Official domestic & international travel",
         "السفر الرسمي الداخلي والدولي", DIR_STRATEGY, "Authority-wide",
         1850000.0, "passenger-km", 0.00015, "tCO2e/p-km", 277.5, 85, "SUBMITTED"),
        ("GHG-2027-07", 3, "EMPLOYEE_COMMUTING", "Employee commuting (survey-based)",
         "تنقل الموظفين (وفق مسح)", DIR_HR, "Authority-wide",
         4300000.0, "passenger-km", 0.00017, "tCO2e/p-km", 731.0, 70, "DRAFT"),
        ("GHG-2027-08", 3, "PURCHASED_GOODS_SERVICES", "ICT & office goods/services",
         "السلع والخدمات التقنية والمكتبية", DIR_IT, "Authority-wide",
         1.0, "spend-based", 612.0, "tCO2e", 612.0, 60, "DRAFT"),
    ]
    ins = 0
    for (code, scope, cat, sen, sar, owner, fac, act, act_u, ef, ef_u, val, cov, status) in ghg_rows:
        cur.execute("SELECT id FROM sust.ghg_emission_record WHERE organisation_id=? AND record_code=?",
                    ORG, code)
        if cur.fetchone():
            continue
        verified = 1 if status in ("VERIFIED", "APPROVED") else 0
        vprov = "TUV / national verifier" if verified else None
        vdate = "2027-09-30" if verified else None
        cur.execute(
            """INSERT INTO sust.ghg_emission_record
                   (organisation_id, external_id, record_code, reporting_year, scope, category,
                    source_name_en, source_name_ar, business_unit, facility,
                    activity_data, activity_unit, emission_factor, emission_factor_unit,
                    emission_value_tco2e, coverage_percent, verified, verification_provider,
                    verification_date, methodology, owner_name, status, notes,
                    is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                       'GHG Protocol Corporate Standard', ?, ?, ?, 0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-{code}", code, FY, scope, cat, sen, sar, owner, fac,
            act, act_u, ef, ef_u, val, cov, verified, vprov, vdate, owner, status,
            f"FY{FY} authority GHG inventory line — reviewed by {COMMITTEE_REF}.",
        )
        ins += 1
    section("ghg_emission_record", ins)

    # ================================================================== esg_metric
    # key: (organisation_id, metric_code). category E/S/G. optional link to SGI-GOV id.
    def sgi_id(code: str):
        cur.execute("SELECT id FROM sust.sgi_commitment WHERE organisation_id=? AND commitment_code=?",
                    ORG, code)
        row = cur.fetchone()
        return row[0] if row else None

    esg_rows = [
        ("ESG-2027-01", "ENVIRONMENTAL", "Facility energy intensity",
         "كثافة استهلاك الطاقة في المرافق", "kWh/m2",
         210.0, 2024, 178.0, 150.0, 2030, "AT_TARGET", DIR_GS, None),
        ("ESG-2027-02", "ENVIRONMENTAL", "Renewable electricity share",
         "نسبة الكهرباء المتجددة", "%",
         12.0, 2024, 21.0, 50.0, 2030, "ACTIVE", DIR_GS, "SGI-GOV-01"),
        ("ESG-2027-03", "ENVIRONMENTAL", "Facility water intensity",
         "كثافة استهلاك المياه", "index (2024=100)",
         100.0, 2024, 88.0, 70.0, 2030, "ACTIVE", DIR_GS, "SGI-GOV-04"),
        ("ESG-2027-04", "ENVIRONMENTAL", "Office-waste diversion rate",
         "معدل تحويل النفايات المكتبية", "%",
         28.0, 2024, 46.0, 70.0, 2030, "ACTIVE", DIR_DATA, "SGI-GOV-03"),
        ("ESG-2027-05", "ENVIRONMENTAL", "Fleet EV conversion",
         "نسبة تحويل الأسطول إلى الكهرباء", "%",
         3.0, 2024, 11.0, 40.0, 2030, "OFF_TRACK", DIR_GS, "SGI-GOV-02"),
        ("ESG-2027-06", "SOCIAL", "Saudization rate (Nitaqat)",
         "نسبة السعودة (نطاقات)", "%",
         88.0, 2024, 94.0, 97.0, 2028, "AT_TARGET", DIR_HR, None),
        ("ESG-2027-07", "SOCIAL", "Female participation in leadership",
         "مشاركة المرأة في المناصب القيادية", "%",
         18.0, 2024, 27.0, 35.0, 2030, "ACTIVE", DIR_HR, None),
        ("ESG-2027-08", "SOCIAL", "Beneficiary e-service satisfaction",
         "رضا المستفيدين عن الخدمات الإلكترونية", "%",
         79.0, 2024, 86.0, 92.0, 2028, "ACTIVE", DIR_STRATEGY, None),
        ("ESG-2027-09", "GOVERNANCE", "Integrity & anti-corruption training coverage",
         "تغطية تدريب النزاهة ومكافحة الفساد", "%",
         70.0, 2024, 91.0, 100.0, 2027, "AT_TARGET", DIR_AUDIT, None),
    ]
    ins = 0
    for (code, cat, men, mar, unit, base, base_yr, cur_v, tgt, tgt_yr, status, dept, sgi) in esg_rows:
        cur.execute("SELECT id FROM sust.esg_metric WHERE organisation_id=? AND metric_code=?",
                    ORG, code)
        if cur.fetchone():
            continue
        linked = sgi_id(sgi) if sgi else None
        cur.execute(
            """INSERT INTO sust.esg_metric
                   (organisation_id, external_id, metric_code, reporting_year, category,
                    metric_name_en, metric_name_ar, unit, baseline_value, baseline_year,
                    current_value, current_value_as_of, target_value, target_year, status,
                    owner_name, responsible_department, linked_sgi_commitment_id, notes,
                    is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '2026-12-31', ?, ?, ?, ?, ?, ?, ?,
                       0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-{code}", code, FY, cat, men, mar, unit, base, base_yr,
            cur_v, tgt, tgt_yr, status, dept, dept, linked,
            f"FY{FY} public-authority ESG KPI — oversight by {COMMITTEE_REF}.",
        )
        ins += 1
    section("esg_metric", ins)

    # ======================================================= tcfd_climate_scenario
    # key: (organisation_id, scenario_code). bigint audit cols; external_id has default.
    # row_version is rowversion -> never insert. portfolio cols left NULL (no portfolio).
    scn_rows = [
        ("TCFD-2027-ORDERLY", "Orderly transition — national net-zero pathway",
         "تحول منظم — مسار الحياد الكربوني الوطني",
         "ORDERLY", "IEA_NZE_2050", 23, 1.5, 0.45,
         {"grid_decarbonisation": "Saudi power grid decarbonises in line with net-zero 2060",
          "carbon_pricing": "Gradual carbon-pricing on facility energy",
          "physical_stress": "Limited additional heat/flood stress on authority facilities"}),
        ("TCFD-2027-INTERMEDIATE", "Intermediate / disorderly — delayed action",
         "مسار متوسط/غير منظم — تأخر الإجراءات",
         "DISORDERLY", "IPCC_RCP_4_5", 23, 2.4, 0.35,
         {"grid_decarbonisation": "Partial, uneven grid decarbonisation",
          "carbon_pricing": "Abrupt carbon-pricing and tariff increases",
          "physical_stress": "Moderate heat and flash-flood stress on facilities"}),
        ("TCFD-2027-HOTHOUSE", "Hot-house world — high physical risk",
         "عالم الاحترار المرتفع — مخاطر مادية عالية",
         "HOT_HOUSE", "IPCC_RCP_8_5", 23, 4.0, 0.20,
         {"grid_decarbonisation": "Minimal decarbonisation; continued fossil reliance",
          "carbon_pricing": "Weak transition policy",
          "physical_stress": "Severe extreme-heat cooling demand and flood exposure on HQ/data hall"}),
    ]
    ins = 0
    for (code, ten, tar, stype, fw, horizon, warming, weight, assumptions) in scn_rows:
        cur.execute("SELECT id FROM sust.tcfd_climate_scenario WHERE organisation_id=? AND scenario_code=?",
                    ORG, code)
        if cur.fetchone():
            continue
        cur.execute(
            """INSERT INTO sust.tcfd_climate_scenario
                   (organisation_id, scenario_code, title_en, title_ar, description_en, description_ar,
                    scenario_type, reference_framework, time_horizon_years, global_warming_celsius,
                    assumptions_json, probability_weight, lifecycle_stage,
                    approved_by_user_id, approved_at, is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BOARD_APPROVED',
                       ?, SYSUTCDATETIME(), 0, ?, SYSUTCDATETIME(), ?)""",
            ORG, code, ten, tar,
            f"FY{FY} public-authority climate scenario (IPCC/IEA framework); board oversight via {COMMITTEE_REF}.",
            "سيناريو مناخي للهيئة العامة وفق أطر اللجنة الدولية/وكالة الطاقة، بإشراف لجنة الاستدامة (COM-ESG).",
            stype, fw, horizon, warming,
            json.dumps(assumptions, ensure_ascii=False), weight,
            ADMIN_UID, ADMIN_UID, ADMIN_UID,
        )
        ins += 1
    section("tcfd_climate_scenario", ins)

    # =================================================== climate_risk_opportunity
    # key: (organisation_id, item_code). category free nvarchar (reuse vocab).
    cro_rows = [
        ("CRO-2027-01", "RISK", "PHYSICAL", "Extreme-heat cooling load on HQ & data hall",
         "حمل التبريد الناتج عن الحرارة الشديدة على المقر وقاعة البيانات",
         4, 4, "LONG", "ASSESSED", DIR_GS, "Core e-services platform",
         "Rising extreme-heat days increase HVAC load and grid dependency at HQ and the data hall.",
         "ازدياد أيام الحرارة الشديدة يرفع حمل التكييف والاعتماد على الشبكة في المقر وقاعة البيانات."),
        ("CRO-2027-02", "RISK", "PHYSICAL", "Flash-flood exposure of a regional office",
         "تعرّض مكتب إقليمي لمخاطر السيول",
         3, 4, "MEDIUM", "ASSESSED", DIR_GS, "Regional licensing service",
         "A regional office sits in a flash-flood-prone area, risking service disruption.",
         "يقع أحد المكاتب الإقليمية في منطقة معرضة للسيول مما يهدد استمرارية الخدمة."),
        ("CRO-2027-03", "RISK", "REGULATORY", "Tightening continuity & climate-disclosure expectations",
         "تشديد متطلبات الاستمرارية والإفصاح المناخي",
         4, 3, "MEDIUM", "TREATED", DIR_COMPLIANCE, "Authority-wide governance",
         "NCEMA continuity and CMA/SDAIA climate-disclosure expectations for public entities are tightening.",
         "تتجه متطلبات المركز الوطني لإدارة الأزمات والإفصاح المناخي من هيئة السوق وسدايا إلى مزيد من التشديد."),
        ("CRO-2027-04", "RISK", "TRANSITION", "Rising grid tariff & carbon-pricing on facility energy",
         "ارتفاع تعرفة الشبكة وتسعير الكربون على طاقة المرافق",
         3, 3, "LONG", "ASSESSED", DIR_GS, "Facilities operations",
         "Higher electricity tariffs and emerging carbon-pricing raise the authority's facility energy cost.",
         "ارتفاع تعرفة الكهرباء وبدء تسعير الكربون يرفعان كلفة طاقة مرافق الهيئة."),
        ("CRO-2027-05", "OPPORTUNITY", "ENERGY_SOURCE", "Rooftop PV + SREDA PPA across offices",
         "ألواح كهروضوئية واتفاقية طاقة عبر سريدا في المكاتب",
         4, 4, "MEDIUM", "TREATED", DIR_GS, "Facilities operations",
         "Rooftop solar plus an SREDA-enabled PPA cuts emissions and energy cost across offices.",
         "الطاقة الشمسية على الأسطح واتفاقية شراء طاقة عبر سريدا تخفض الانبعاثات وكلفة الطاقة."),
        ("CRO-2027-06", "OPPORTUNITY", "RESILIENCE", "Climate-resilient HQ retrofit (Mostadam)",
         "إعادة تأهيل المقر ليكون مرناً مناخياً (مستدام)",
         3, 4, "LONG", "ASSESSED", DIR_GS, "HQ facilities",
         "A Mostadam green retrofit improves HQ resilience to heat and reduces operating cost.",
         "إعادة التأهيل الأخضر وفق مستدام يحسّن مرونة المقر تجاه الحرارة ويخفض كلفة التشغيل."),
        ("CRO-2027-07", "OPPORTUNITY", "PRODUCTS", "Digital-first services cut fleet & paper footprint",
         "الخدمات الرقمية أولاً تخفض أثر الأسطول والورق",
         4, 3, "SHORT", "TREATED", DIR_DATA, "Citizen e-services",
         "Shifting services digital-first reduces fleet travel and paper consumption.",
         "تحويل الخدمات إلى الرقمية أولاً يخفض تنقلات الأسطول واستهلاك الورق."),
    ]
    ins = 0
    for (code, kind, cat, ten, tar, lik, imp, hor, status, owner, svc, den, dar) in cro_rows:
        cur.execute("SELECT id FROM sust.climate_risk_opportunity WHERE organisation_id=? AND item_code=?",
                    ORG, code)
        if cur.fetchone():
            continue
        rscore = lik * imp
        treatment = ("Mitigation plan owned by the responsible directorate; reviewed by "
                     + COMMITTEE_REF + ".")
        cur.execute(
            """INSERT INTO sust.climate_risk_opportunity
                   (organisation_id, external_id, item_code, reporting_year, kind, category,
                    title_en, title_ar, description_en, description_ar, time_horizon,
                    likelihood, impact, risk_score, related_service, related_tcfd_recommendation,
                    controls_summary, treatment_plan, metrics_summary, owner_name, status,
                    is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                       0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-{code}", code, FY, kind, cat, ten, tar, den, dar, hor,
            lik, imp, rscore, svc, "TCFD Strategy (a)(b)(c)",
            "Linked to FY2027 IPCC scenario analysis.", treatment,
            f"FY{FY} climate {kind.lower()} — oversight by {COMMITTEE_REF}.", owner, status,
        )
        ins += 1
    section("climate_risk_opportunity", ins)

    # ================================================================= tcfd_item
    # key: (organisation_id, reporting_year, pillar, recommendation_code).
    tcfd_rows = [
        ("GOVERNANCE", "a", "Board oversight of climate",
         "إشراف المجلس على المناخ",
         "The board, via the Sustainability Committee (COM-ESG), oversees climate risks and opportunities.",
         "يشرف المجلس عبر لجنة الاستدامة (COM-ESG) على المخاطر والفرص المناخية.",
         "DISCLOSED", DIR_STRATEGY),
        ("GOVERNANCE", "b", "Management's role",
         "دور الإدارة",
         "Strategy & Performance and General Services & Facilities manage climate matters operationally.",
         "تتولى إدارتا الاستراتيجية والأداء والخدمات العامة والمرافق إدارة الشؤون المناخية تشغيلياً.",
         "DISCLOSED", DIR_GS),
        ("STRATEGY", "a", "Climate risks & opportunities by horizon",
         "المخاطر والفرص المناخية حسب الأفق الزمني",
         "Short/medium/long-term climate risks and opportunities to services and facilities are identified.",
         "تم تحديد المخاطر والفرص المناخية قصيرة ومتوسطة وطويلة الأجل على الخدمات والمرافق.",
         "DISCLOSED", DIR_STRATEGY),
        ("STRATEGY", "b", "Impact on services & facilities",
         "الأثر على الخدمات والمرافق",
         "Impact of climate factors on authority services, facilities and cost base is assessed.",
         "تم تقييم أثر العوامل المناخية على خدمات الهيئة ومرافقها وقاعدة التكلفة.",
         "PARTIAL", DIR_GS),
        ("STRATEGY", "c", "Resilience under scenarios",
         "المرونة وفق السيناريوهات",
         "Resilience of the strategy is tested under the FY2027 IPCC/IEA climate scenarios.",
         "تُختبر مرونة الاستراتيجية وفق سيناريوهات 2027 المناخية لوكالة الطاقة واللجنة الدولية.",
         "PARTIAL", DIR_STRATEGY),
        ("RISK_MGMT", "a", "Risk identification process",
         "آلية تحديد المخاطر",
         "Climate risks are identified within the authority's enterprise risk and NCEMA continuity processes.",
         "تُحدَّد المخاطر المناخية ضمن إدارة المخاطر المؤسسية واستمرارية الأعمال وفق المركز الوطني لإدارة الأزمات.",
         "DISCLOSED", DIR_COMPLIANCE),
        ("RISK_MGMT", "b", "Risk management process",
         "آلية إدارة المخاطر",
         "Climate risks are assessed, treated and tracked through the corrective-action workflow.",
         "تُقيَّم المخاطر المناخية وتُعالَج وتُتابَع عبر مسار الإجراءات التصحيحية.",
         "DISCLOSED", DIR_COMPLIANCE),
        ("RISK_MGMT", "c", "Integration into ERM",
         "التكامل مع إدارة المخاطر المؤسسية",
         "Climate risk is integrated into the authority ERM register and NCEMA continuity planning.",
         "تُدمج المخاطر المناخية في سجل المخاطر المؤسسية وخطط الاستمرارية وفق المركز الوطني لإدارة الأزمات.",
         "PARTIAL", DIR_COMPLIANCE),
        ("METRICS_TARGETS", "a", "Metrics used",
         "المقاييس المستخدمة",
         "ESG metrics (energy/water intensity, renewable share, Saudization) track climate performance.",
         "تتبع مؤشرات الحوكمة البيئية والاجتماعية (كثافة الطاقة والمياه، نسبة المتجدد، السعودة) الأداء المناخي.",
         "DISCLOSED", DIR_STRATEGY),
        ("METRICS_TARGETS", "b", "Scope 1/2/3 emissions",
         "انبعاثات النطاق 1/2/3",
         "Scope 1, 2 and 3 emissions are reported from the FY2027 authority GHG inventory.",
         "يُفصح عن انبعاثات النطاقات 1 و2 و3 من جرد الهيئة لعام 2027.",
         "PARTIAL", DIR_GS),
        ("METRICS_TARGETS", "c", "Targets",
         "المستهدفات",
         "Targets are the authority's Saudi Green Initiative commitments (renewable, EV fleet, waste, water).",
         "المستهدفات هي التزامات الهيئة ضمن المبادرة السعودية الخضراء (المتجدد، الأسطول الكهربائي، النفايات، المياه).",
         "DISCLOSED", DIR_GS),
    ]
    ins = 0
    for (pillar, code, ten, tar, ren, rar, status, owner) in tcfd_rows:
        cur.execute(
            "SELECT id FROM sust.tcfd_item WHERE organisation_id=? AND reporting_year=? "
            "AND pillar=? AND recommendation_code=?",
            ORG, FY, pillar, code)
        if cur.fetchone():
            continue
        approval = "APPROVED" if status == "DISCLOSED" else "PENDING"
        cur.execute(
            """INSERT INTO sust.tcfd_item
                   (organisation_id, external_id, reporting_year, pillar, recommendation_code,
                    title_en, title_ar, recommendation_en, recommendation_ar, disclosure_status,
                    evidence_owner, owner_name, reviewer_name, approval_status, approved_by,
                    approved_at, notes, is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                       CASE WHEN ?='APPROVED' THEN SYSUTCDATETIME() ELSE NULL END,
                       ?, 0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-TCFD-{FY}-{pillar}-{code}", FY, pillar, code, ten, tar, ren, rar, status,
            owner, owner, COMMITTEE_REF, approval,
            PRESIDENT if approval == "APPROVED" else None, approval,
            f"FY{FY} TCFD recommendation — oversight by {COMMITTEE_REF}.",
        )
        ins += 1
    section("tcfd_item", ins)

    # ============================================================== cma_disclosure
    # Natural key per the DB unique index uq_cma_org_year_article:
    #   (organisation_id, reporting_year, article, clause). article in 91..94;
    #   each row carries a DISTINCT clause so multiple rows can share an article.
    cma_rows = [
        ("91", "91-a", "Governance & sustainability oversight",
         "الحوكمة والإشراف على الاستدامة",
         "Disclose the governance and board oversight of sustainability via the Sustainability Committee (COM-ESG).",
         "الإفصاح عن حوكمة الاستدامة وإشراف المجلس عبر لجنة الاستدامة (COM-ESG).",
         "DISCLOSED", DIR_STRATEGY),
        ("91", "91-b", "Integrity & anti-corruption framework",
         "إطار النزاهة ومكافحة الفساد",
         "Disclose the integrity and anti-corruption framework aligned with Nazaha.",
         "الإفصاح عن إطار النزاهة ومكافحة الفساد بمواءمة هيئة نزاهة.",
         "DISCLOSED", DIR_AUDIT),
        ("92", "92-a", "Material topics & double materiality",
         "الموضوعات الجوهرية والأهمية النسبية المزدوجة",
         "Disclose the FY2027 material topics and the double-materiality assessment.",
         "الإفصاح عن الموضوعات الجوهرية لعام 2027 وتقييم الأهمية النسبية المزدوجة.",
         "PARTIAL", DIR_STRATEGY),
        ("92", "92-b", "Climate risks & opportunities",
         "المخاطر والفرص المناخية",
         "Disclose climate risks and opportunities under the FY2027 IPCC/IEA scenarios.",
         "الإفصاح عن المخاطر والفرص المناخية وفق سيناريوهات 2027.",
         "PARTIAL", DIR_GS),
        ("93", "93-a", "GHG emissions (Scope 1/2/3)",
         "انبعاثات غازات الدفيئة (النطاق 1/2/3)",
         "Disclose Scope 1/2/3 emissions from the FY2027 authority GHG inventory.",
         "الإفصاح عن انبعاثات النطاقات 1 و2 و3 من جرد الهيئة لعام 2027.",
         "PARTIAL", DIR_GS),
        ("93", "93-b", "Energy, water & waste metrics",
         "مؤشرات الطاقة والمياه والنفايات",
         "Disclose energy intensity, renewable share, water intensity and office-waste diversion.",
         "الإفصاح عن كثافة الطاقة ونسبة المتجدد وكثافة المياه وتحويل النفايات المكتبية.",
         "DISCLOSED", DIR_GS),
        ("93", "93-c", "Open data & transparency publishing",
         "نشر البيانات المفتوحة والشفافية",
         "Disclose open-data publishing on the national portal (NDMO / DGA).",
         "الإفصاح عن نشر البيانات المفتوحة على البوابة الوطنية (مكتب إدارة البيانات/الحكومة الرقمية).",
         "PARTIAL", DIR_DATA),
        ("94", "94-a", "Saudization & national workforce",
         "السعودة والكوادر الوطنية",
         "Disclose Saudization (Nitaqat) and national workforce development metrics.",
         "الإفصاح عن السعودة (نطاقات) ومؤشرات تنمية الكوادر الوطنية.",
         "DISCLOSED", DIR_HR),
        ("94", "94-b", "Diversity & female participation in leadership",
         "التنوع ومشاركة المرأة في القيادة",
         "Disclose diversity and female participation in leadership positions.",
         "الإفصاح عن التنوع ومشاركة المرأة في المناصب القيادية.",
         "PARTIAL", DIR_HR),
        ("94", "94-c", "Employee health, safety & wellbeing",
         "صحة وسلامة ورفاهية الموظفين",
         "Disclose occupational health, safety and wellbeing of authority staff.",
         "الإفصاح عن الصحة والسلامة المهنية ورفاهية موظفي الهيئة.",
         "PARTIAL", DIR_HR),
        ("94", "94-d", "Beneficiary accessibility & e-service inclusion",
         "إتاحة الخدمات الإلكترونية وشمول المستفيدين",
         "Disclose accessibility and inclusion of citizen/beneficiary e-services.",
         "الإفصاح عن إتاحة الخدمات الإلكترونية للمستفيدين وشمولهم.",
         "DISCLOSED", DIR_STRATEGY),
    ]
    ins = 0
    for (article, clause, ten, tar, ren, rar, status, dept) in cma_rows:
        cur.execute(
            "SELECT id FROM sust.cma_disclosure WHERE organisation_id=? AND reporting_year=? "
            "AND article=? AND clause=?",
            ORG, FY, article, clause)
        if cur.fetchone():
            continue
        approval = "APPROVED" if status == "DISCLOSED" else "PENDING"
        cur.execute(
            """INSERT INTO sust.cma_disclosure
                   (organisation_id, external_id, reporting_year, article, clause, title_en, title_ar,
                    requirement_en, requirement_ar, disclosure_status, responsible_department,
                    report_section_reference, reviewer_name, approval_status, approved_by, approved_at,
                    notes, is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                       CASE WHEN ?='APPROVED' THEN SYSUTCDATETIME() ELSE NULL END,
                       ?, 0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-CMA-{FY}-{clause}", FY, article, clause, ten, tar,
            ren, rar, status, dept,
            f"Sustainability Annex FY{FY} — Article {article} ({clause}) section",
            COMMITTEE_REF, approval, PRESIDENT if approval == "APPROVED" else None, approval,
            f"FY{FY} CMA Art.{article} disclosure-readiness — oversight {COMMITTEE_REF}; "
            f"open-data via NDMO/DGA; integrity via Nazaha.",
        )
        ins += 1
    section("cma_disclosure", ins)

    # ============================================================ corrective_action
    # key: (organisation_id, action_code). one per allowed source_type to exercise taxonomy.
    capa_rows = [
        ("CAPA-2027-01", "GHG_RECORD", "GHG-2027-07",
         "Lift Scope 3 commuting & purchased-goods coverage",
         "رفع تغطية انبعاثات النطاق 3 للتنقل والمشتريات",
         "Improve survey response and spend-based mapping to raise Scope 3 coverage above 80%.",
         "تحسين استجابة المسح وربط الإنفاق لرفع تغطية النطاق 3 فوق 80%.",
         "MEDIUM", "IN_PROGRESS", DIR_HR),
        ("CAPA-2027-02", "CLIMATE_RISK", "CRO-2027-01",
         "Install HQ heat & flood mitigation measures",
         "تركيب إجراءات الحد من الحرارة والسيول في المقر",
         "Deploy cooling-efficiency and flood-barrier mitigations at HQ and the data hall.",
         "تنفيذ إجراءات كفاءة التبريد وحواجز السيول في المقر وقاعة البيانات.",
         "HIGH", "OPEN", DIR_GS),
        ("CAPA-2027-03", "CMA_DISCLOSURE", "EXT-CMA-2027-92-a",
         "Close Article 92/93 partial disclosures for FY2027",
         "إغلاق الإفصاحات الجزئية للمادتين 92/93 لعام 2027",
         "Complete evidence and narrative for the partial Article 92 and 93 disclosures.",
         "استكمال الأدلة والسرد للإفصاحات الجزئية للمادتين 92 و93.",
         "HIGH", "IN_PROGRESS", DIR_STRATEGY),
        ("CAPA-2027-04", "TCFD_ITEM", "TCFD-2027-STRATEGY-c",
         "Complete STRATEGY scenario-resilience disclosure",
         "استكمال إفصاح مرونة الاستراتيجية وفق السيناريوهات",
         "Finalise the quantitative scenario-resilience disclosure for the STRATEGY pillar.",
         "إنهاء الإفصاح الكمي عن مرونة الاستراتيجية ضمن ركيزة الاستراتيجية.",
         "MEDIUM", "OPEN", DIR_STRATEGY),
        ("CAPA-2027-05", "SGI_COMMITMENT", "SGI-GOV-02",
         "Publish interim 2030 milestone for EV-fleet & renewable pledges",
         "نشر المؤشر المرحلي 2030 لتعهدات الأسطول الكهربائي والمتجدد",
         "Publish the interim 2030-pathway milestone for the EV-fleet and renewable commitments.",
         "نشر مؤشر المسار المرحلي نحو 2030 لتعهدات الأسطول الكهربائي والطاقة المتجددة.",
         "MEDIUM", "OPEN", DIR_GS),
        ("CAPA-2027-06", "ANNEX_GAP", "SR-2027-01",
         "Remediate a FY2027 sustainability-annex gap",
         "معالجة فجوة في ملحق الاستدامة لعام 2027",
         "Address an identified gap in the FY2027 sustainability annex before publication.",
         "معالجة فجوة محددة في ملحق الاستدامة لعام 2027 قبل النشر.",
         "LOW", "OPEN", DIR_STRATEGY),
        ("CAPA-2027-07", "MANUAL", None,
         "Map authority ESG controls to NCA ECC & NDMO data standards",
         "ربط ضوابط الاستدامة بضوابط الأمن السيبراني ومعايير البيانات الوطنية",
         "Map ESG/sustainability controls to NCA ECC and NDMO national data standards.",
         "ربط ضوابط الاستدامة بالضوابط الأساسية للأمن السيبراني ومعايير مكتب إدارة البيانات الوطنية.",
         "MEDIUM", "IN_PROGRESS", DIR_COMPLIANCE),
        ("CAPA-2027-08", "GHG_RECORD", "GHG-2027-03",
         "Obtain external verification for fugitive refrigerant emissions",
         "الحصول على تحقق خارجي لانبعاثات تسرب المبردات",
         "Engage an external verifier to move the refrigerant fugitive line to VERIFIED.",
         "إشراك جهة تحقق خارجية لرفع بند تسرب المبردات إلى حالة (مُتحقَّق).",
         "LOW", "OPEN", DIR_GS),
    ]
    ins = 0
    for (code, src, sref, ten, tar, den, dar, prio, status, owner) in capa_rows:
        cur.execute("SELECT id FROM sust.corrective_action WHERE organisation_id=? AND action_code=?",
                    ORG, code)
        if cur.fetchone():
            continue
        cur.execute(
            """INSERT INTO sust.corrective_action
                   (organisation_id, external_id, action_code, source_type, source_ref,
                    title_en, title_ar, description_en, description_ar, priority, status,
                    owner_name, assigned_to, target_date, remediation_plan_en, remediation_plan_ar,
                    notes, is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '2027-12-31', ?, ?, ?,
                       0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-{code}", code, src, sref, ten, tar, den, dar, prio, status,
            owner, owner, den, dar,
            f"FY{FY} CAPA — oversight by {COMMITTEE_REF}.",
        )
        ins += 1
    section("corrective_action", ins)

    # ================================================================== evidence
    # key: (organisation_id, evidence_code).
    ev_rows = [
        ("EV-2027-01", "Authority GHG inventory FY2027 — verification statement",
         "بيان التحقق من جرد انبعاثات الهيئة لعام 2027",
         "REPORT", "INTERNAL", "APPROVED", "EXTERNAL_VERIFIED", DIR_GS),
        ("EV-2027-02", "Sustainability Committee (COM-ESG) charter & FY2027 minutes",
         "ميثاق لجنة الاستدامة (COM-ESG) ومحاضر اجتماعات 2027",
         "CHARTER", "INTERNAL", "APPROVED", "VERIFIED", DIR_STRATEGY),
        ("EV-2027-03", "FY2027 TCFD scenario analysis (IPCC/IEA-based)",
         "تحليل سيناريوهات إفصاح المناخ لعام 2027 (وفق اللجنة الدولية/وكالة الطاقة)",
         "REPORT", "INTERNAL", "APPROVED", "VERIFIED", DIR_STRATEGY),
        ("EV-2027-04", "HQ rooftop-PV / SREDA PPA documentation",
         "وثائق الطاقة الشمسية على سطح المقر واتفاقية سريدا",
         "CERTIFICATE", "INTERNAL", "PENDING_REVIEW", "UNVERIFIED", DIR_GS),
        ("EV-2027-05", "HRSD Nitaqat tier confirmation for the authority",
         "تأكيد نطاق نطاقات للهيئة من الموارد البشرية",
         "CERTIFICATE", "RESTRICTED", "APPROVED", "EXTERNAL_VERIFIED", DIR_HR),
        ("EV-2027-06", "FY2027 materiality survey results",
         "نتائج مسح الأهمية النسبية لعام 2027",
         "SPREADSHEET", "INTERNAL", "APPROVED", "VERIFIED", DIR_STRATEGY),
        ("EV-2027-07", "NDMO open-data publication log",
         "سجل نشر البيانات المفتوحة لمكتب إدارة البيانات الوطنية",
         "SPREADSHEET", "PUBLIC", "APPROVED", "VERIFIED", DIR_DATA),
        ("EV-2027-08", "Mostadam green-building assessment",
         "تقييم المباني الخضراء وفق مستدام",
         "REPORT", "INTERNAL", "PENDING_REVIEW", "UNVERIFIED", DIR_GS),
    ]
    ins = 0
    for (code, ten, tar, etype, conf, approval, verif, owner) in ev_rows:
        cur.execute("SELECT id FROM sust.evidence WHERE organisation_id=? AND evidence_code=?",
                    ORG, code)
        if cur.fetchone():
            continue
        vdate = "2027-10-15" if verif in ("VERIFIED", "EXTERNAL_VERIFIED") else None
        vprov = "National / external verifier" if verif == "EXTERNAL_VERIFIED" else None
        cur.execute(
            """INSERT INTO sust.evidence
                   (organisation_id, external_id, evidence_code, title_en, title_ar,
                    module_area, evidence_type, version, reporting_year, valid_from,
                    confidentiality, verification_status, verification_provider, verification_date,
                    approval_status, owner_name, reviewer_name, approved_by, approved_at, notes,
                    is_deleted, created_by, updated_at, updated_by)
               VALUES (?, ?, ?, ?, ?, 'sustainability', ?, 'v1.0', ?, '2027-01-01',
                       ?, ?, ?, ?, ?, ?, ?, ?,
                       CASE WHEN ?='APPROVED' THEN SYSUTCDATETIME() ELSE NULL END,
                       ?, 0, 'admin', SYSUTCDATETIME(), 'admin')""",
            ORG, f"EXT-{code}", code, ten, tar, etype, FY,
            conf, verif, vprov, vdate, approval, owner, COMMITTEE_REF,
            PRESIDENT if approval == "APPROVED" else None, approval,
            f"FY{FY} evidence artefact backing the public-authority ESG layer; {COMMITTEE_REF}.",
        )
        ins += 1
    section("evidence", ins)

    # ======================================================= sustainability_report
    # key: (organisation_id, report_code). FK reporting_year_id -> FY2027 row.
    if fy2027_ry is None:
        SKIPS.append(("sustainability_report", 3, "FY2027 reporting_year id not resolvable"))
        section("sustainability_report", 0)
    else:
        sr_rows = [
            ("SR-2027-01", "ANNUAL_ANNEX", "SUBMITTED",
             "Sustainability Annex FY2027 — General Authority",
             "ملحق الاستدامة لعام 2027 — الهيئة العامة",
             ("# Sustainability Annex FY2027 — General Authority\n\n"
              "Prepared by Strategy & Performance; reviewed by the Sustainability "
              "Committee (COM-ESG); approved by the President of the Authority. "
              "Covers material topics, GHG inventory, climate risks, SGI commitments "
              "and TCFD disclosure, aligned with Vision 2030 and the Saudi Green Initiative.")),
            ("SR-2027-02", "BOARD_ESG", "DRAFT",
             "FY2027 ESG board pack — Sustainability Committee (COM-ESG)",
             "حزمة مجلس الاستدامة لعام 2027 — لجنة الاستدامة (COM-ESG)",
             ("# FY2027 ESG Board Pack — Sustainability Committee (COM-ESG)\n\n"
              "Quarterly oversight pack: materiality, KPI dashboard, SGI progress, "
              "GHG inventory status and the corrective-action backlog.")),
            ("SR-2027-03", "TCFD_REPORT", "DRAFT",
             "FY2027 TCFD climate disclosure (IPCC scenarios)",
             "إفصاح المناخ وفق TCFD لعام 2027 (سيناريوهات اللجنة الدولية)",
             ("# FY2027 TCFD Climate Disclosure\n\n"
              "Governance, strategy, risk management and metrics & targets, tested "
              "under the FY2027 IPCC/IEA climate scenarios for the authority's "
              "facilities and services.")),
        ]
        ins = 0
        for (code, rtype, status, ten, tar, md) in sr_rows:
            cur.execute("SELECT id FROM sust.sustainability_report WHERE organisation_id=? AND report_code=?",
                        ORG, code)
            if cur.fetchone():
                continue
            cur.execute(
                """INSERT INTO sust.sustainability_report
                       (organisation_id, reporting_year_id, external_id, report_code, report_type,
                        title_en, title_ar, version, status, generated_by, reviewed_by, approved_by,
                        content_markdown, is_deleted, created_by, updated_at, updated_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, 'v1.0', ?, ?, ?, ?, ?, 0, 'admin',
                           SYSUTCDATETIME(), 'admin')""",
                ORG, fy2027_ry, f"EXT-{code}", code, rtype, ten, tar, status,
                DIR_STRATEGY, COMMITTEE_REF, PRESIDENT, md,
            )
            ins += 1
        section("sustainability_report", ins)

    # ── Skips (reported, not seeded) ─────────────────────────────────────────
    SKIPS.extend([
        ("financed_emissions_record", 8,
         "PCAF loan-book construct (asset_class/outstanding_amount/attribution) — no meaning for a public authority"),
        ("financed_emissions_target", 2,
         "NZBA/SBTi financed-emissions portfolio targets — bank-only; authority targets live in sgi_commitment"),
        ("tcfd_sector_impact", 18,
         "Per-sector portfolio exposure heat-map (exposure_sar_millions) — no portfolio for a public authority"),
        ("organisation_settings", 1,
         "Per-org singleton already present; deepening requires editing the existing row (forbidden)"),
    ])

    # ── Summary & verification ───────────────────────────────────────────────
    total = sum(COUNTS.values())
    print(f"\nTotal rows inserted this run: {total}")
    print("\nSkipped tables (reported, not seeded):")
    for name, existing, why in SKIPS:
        print(f"  - sust.{name} (org-1 existing={existing}): {why}")

    print("\nVerification — org-1 row counts in each touched table:")
    for t in ["reporting_year", "material_topic", "sgi_commitment", "ghg_emission_record",
              "esg_metric", "tcfd_climate_scenario", "climate_risk_opportunity", "tcfd_item",
              "cma_disclosure", "corrective_action", "evidence", "sustainability_report"]:
        cur.execute(f"SELECT COUNT(*) FROM sust.{t} WHERE organisation_id=? AND is_deleted=0", ORG)
        print(f"  sust.{t:<26} {cur.fetchone()[0]}")

    # FK integrity check: FY2027 reports point at the FY2027 reporting_year
    cur.execute(
        """SELECT COUNT(*) FROM sust.sustainability_report r
           JOIN sust.reporting_year y ON r.reporting_year_id=y.id
           WHERE r.organisation_id=? AND y.year=?""", ORG, FY)
    print(f"\nFK check: FY{FY} reports correctly linked to FY{FY} reporting_year = {cur.fetchone()[0]}")

    conn.close()


if __name__ == "__main__":
    main()
