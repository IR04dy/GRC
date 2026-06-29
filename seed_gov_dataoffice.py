# -*- coding: utf-8 -*-
"""Seed a Saudi GOVERNMENT data-office layer into the data_officer.* and ndi.* schemas.

Domain: "dataoffice" (Data Management Office). Tenant: organisation_id = 1 ONLY,
which on this DB is a Saudi General Authority (هيئة عامة) headed by a President.

WHY THIS SCRIPT EXISTS
----------------------
Every existing row in both schemas was seeded for a RETAIL-BANK demo tenant
(T24/SADAD/Murex/Actimize, KYC/AML/CCTV retention, marketing/profiling consent,
an NDI "Demo Entity" cycle owned by a CSO). None of that fits a public authority
running licensing / inspection / beneficiary services.

The hard safety rules forbid DELETE/UPDATE of existing rows, so this script does
NOT touch the bank rows. Instead it ADDS a coherent, parallel GOVERNMENT layer:

  ndi.*           one Authority-owned cycle NDI-2026 + a full assessment fabric
                  (maturity / compliance / OPEX KPIs / gaps / CAPAs / evidence /
                   improvement plan + items / submission package / audit trail)
                  hung off that one cycle, referencing the EXISTING persona-neutral
                  governance_domain (8) and framework_item (20) rows.
  data_officer.*  a government data catalog (beneficiary/licensing/inspection/...)
                  + lineage, quality rules + runs, NCAR-aligned retention + disposal,
                  PDPL privacy notices + citizen consent, public-sector DPIAs,
                  citizen DSRs, SDAIA AI ethics reviews, public-authority breaches,
                  a do_event activity trail, and a 2026 National Data Catalogue
                  submission.

REGULATORS woven in (natural Saudi institutional Arabic, not transliteration):
  NDMO + SDAIA (data management / National Data Index / DCAT-AP-SA metadata),
  PDPL + SDAIA (personal data, 72h breach notification), NCAR / المركز الوطني
  للوثائق والمحفوظات (retention & disposal), Saudi Open Data / data.gov.sa,
  DGA, Vision 2030.

LINKAGE (no DB-level FKs exist in either schema; no department/committee columns):
  * People links are *_user_id -> iam.users (1..6).
  * Ownership by DATA_MGMT (dept id 23; sub GOV/QUALITY/PRIVACY/BI_AI) and
    COM-DATAGOV (committee 14) is expressed LOGICALLY via owner_role / *_name
    free-text ("Director, Data Management Office", "Chair, Data Governance
    Committee", "President of the Authority").
  * ndi created_by/updated_by are nvarchar usernames ('admin'/'system'), NOT ids.

IDEMPOTENT: every INSERT is guarded by an existence check on a natural key
(reference / *_code / setting_key / (cycle_id, requirement_code) / edge pairs / ...).
A second run inserts 0 and prints "+0".

SAFETY: INSERT-only. No DELETE / TRUNCATE / DROP / ALTER. organisation_id = 1 only.
Only the data_officer and ndi schemas are touched.

    Usage:  python seed_gov_dataoffice.py
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
ADMIN = 1          # iam.users.admin  -> bigint *_user_id / created_by columns
RMANAGER = 2
COMPLIANCE_U = 3
AUDITOR = 4
RANALYST = 5
VIEWER = 6
USER = "admin"     # ndi created_by/updated_by are nvarchar usernames
SYS = "system"

# Logical owners (free-text; tie to DATA_MGMT dept 23 + COM-DATAGOV committee 14)
DMO_DIR = "Director, Data Management Office"
DMO_DIR_AR = "مدير عام مكتب إدارة البيانات"
CDO = "Chief Data Officer"
DG_LEAD = "Data Governance Lead"
DQ_LEAD = "Data Quality Lead"
DPO = "Data Protection Officer"
DATAGOV_CHAIR = "Chair, Data Governance Committee"
PRESIDENT = "President of the Authority"

# Running tallies, keyed by table label.
_INS: dict[str, int] = {}
_SKIP: list[str] = []
_BLOCK: list[str] = []


def bump(label: str, n: int = 1) -> None:
    _INS[label] = _INS.get(label, 0) + n


# ─────────────────────────────────────────────────────────────────────────────
# Generic idempotent insert helper.
# ─────────────────────────────────────────────────────────────────────────────
def upsert(cur, label, table, natural_where, natural_params, cols, vals):
    """Insert one row if no existing row matches natural_where; else skip.

    Returns the id of the existing-or-new row (via the natural key lookup).
    """
    cur.execute(
        f"SELECT id FROM {table} WHERE organisation_id=? AND {natural_where}",
        ORG, *natural_params,
    )
    row = cur.fetchone()
    if row:
        return row[0]
    collist = ", ".join(cols)
    qs = ", ".join("?" for _ in cols)
    cur.execute(
        f"INSERT INTO {table} (organisation_id, {collist}) "
        f"OUTPUT INSERTED.id VALUES (?, {qs})",
        ORG, *vals,
    )
    new_id = cur.fetchone()[0]
    bump(label)
    return new_id


# ═════════════════════════════════════════════════════════════════════════════
#  ndi.*  — the Authority's NDI-2026 assessment fabric
# ═════════════════════════════════════════════════════════════════════════════
def seed_ndi(cur):
    # ── 1. assessment_cycle (the anchor everything hangs off) ─────────────────
    cycle_id = upsert(
        cur, "ndi.assessment_cycle", "ndi.assessment_cycle",
        "cycle_code=?", ("NDI-2026",),
        ["external_id", "cycle_code", "cycle_name_en", "cycle_name_ar",
         "reporting_year", "period_start", "period_end", "scope",
         "entity_name_en", "entity_name_ar",
         "data_office_owner_name", "data_office_owner_email",
         "assessment_manager_name", "assessment_manager_email",
         "status", "maturity_score", "compliance_score",
         "operational_excellence_score", "overall_score",
         "evidence_completion_percent", "gap_count", "critical_gap_count",
         "submission_readiness_status", "notes", "created_by", "updated_by"],
        ["EXT-NDI-2026", "NDI-2026",
         "National Data Index Readiness Cycle 2026",
         "دورة جاهزية مؤشر البيانات الوطني 2026",
         2026, "2026-01-01", "2026-12-31",
         "Authority-wide NDI self-assessment against the NDMO data-management "
         "standards and SDAIA National Data Index, covering governance, catalog, "
         "quality, sharing, open data, personal-data protection, security and "
         "classification for the Authority's beneficiary, licensing and inspection "
         "services.",
         "General Authority — National Data Index Submission 2026",
         "الهيئة العامة — تقديم مؤشر البيانات الوطني 2026",
         DMO_DIR, "dmo.director@authority.gov.sa",
         "NDI Assessment Lead", "ndi.lead@authority.gov.sa",
         "ASSESSMENT_IN_PROGRESS", 62.0, 68.0, 58.0, 63.0,
         71.0, 8, 2, "AT_RISK",
         "First Authority-owned NDI cycle aligned to NDMO/SDAIA; benchmarked "
         "against the national NDI maturity target. Owned by the Data Management "
         "Office, governed by the Data Governance Committee (COM-DATAGOV).",
         USER, USER],
    )

    # Map existing framework_item ids/domains so child rows reference real items.
    cur.execute(
        "SELECT id, component, domain, requirement_code, title_en "
        "FROM ndi.framework_item WHERE organisation_id=?", ORG)
    fw = {r[3]: dict(id=r[0], comp=r[1], domain=r[2], title=r[4])
          for r in cur.fetchall()}

    # ── 2. maturity_assessment (one per MATURITY framework_item) ──────────────
    # (req_code, current, target, status, response_en, response_ar, gap_code_link)
    MAT = [
        ("NDI-DG-001", 3, 4, "APPROVED",
         "A data governance framework has been approved by the Data Governance "
         "Committee (COM-DATAGOV); roles for the Data Management Office, data "
         "owners and stewards are defined for the Authority's core registers.",
         "تم اعتماد إطار حوكمة البيانات من لجنة حوكمة البيانات؛ وحُددت أدوار مكتب "
         "إدارة البيانات وملّاك البيانات والمشرفين على السجلات الرئيسية للهيئة.",
         None),
        ("NDI-DC-001", 2, 4, "APPROVED",
         "The data catalog covers the beneficiary and licensing registers, but "
         "inspection and correspondence assets are not yet fully catalogued to the "
         "NDMO DCAT-AP-SA metadata standard.",
         "يغطي فهرس البيانات سجلّي المستفيدين والتراخيص، إلا أن أصول التفتيش "
         "والمراسلات لم تُفهرس بالكامل وفق معيار البيانات الوصفية الوطني.",
         "GAP-2026-001"),
        ("NDI-DQ-001", 3, 4, "APPROVED",
         "Data quality rules are defined for the national-ID and licence-number "
         "fields on critical registers, with periodic quality runs and red/amber "
         "banding reported to the Data Quality function.",
         "عُرّفت قواعد جودة البيانات لحقول الهوية الوطنية ورقم الترخيص على السجلات "
         "الحرجة، مع تشغيل دوري لفحوص الجودة ورفع النتائج لإدارة جودة البيانات.",
         None),
        ("NDI-CL-001", 2, 4, "APPROVED",
         "A data classification policy aligned to NDMO is approved; structured "
         "registers are classified, but classification of unstructured case files "
         "is still manual and not yet automated.",
         "اعتُمدت سياسة تصنيف البيانات وفق مكتب إدارة البيانات الوطنية؛ وصُنّفت "
         "السجلات المهيكلة، بينما لا يزال تصنيف ملفات القضايا غير المهيكلة يدويًا.",
         "GAP-2026-006"),
        ("NDI-DS-001", 3, 4, "APPROVED",
         "Critical datasets are encrypted at rest in the Authority's data centre "
         "and the government cloud, in line with NDMO data-security controls.",
         "تُشفَّر مجموعات البيانات الحرجة أثناء التخزين في مركز بيانات الهيئة "
         "والحوسبة السحابية الحكومية بما يتوافق مع ضوابط أمن البيانات.",
         None),
        ("NDI-DSH-001", 2, 4, "APPROVED",
         "Some inter-authority data-sharing is operational via the national "
         "integration channel, but several data-sharing agreements with partner "
         "authorities are still pending signature.",
         "تعمل بعض عمليات تبادل البيانات بين الجهات عبر قناة التكامل الوطنية، إلا "
         "أن عددًا من اتفاقيات تبادل البيانات مع الجهات الشريكة بانتظار التوقيع.",
         "GAP-2026-007"),
        ("NDI-OD-001", 2, 4, "APPROVED",
         "An initial set of datasets is published to the Saudi Open Data portal "
         "(data.gov.sa), but a publication backlog remains versus the Authority's "
         "open-data plan under the Saudi Open Data Policy.",
         "نُشرت مجموعة أولية من البيانات على البوابة الوطنية للبيانات المفتوحة، "
         "لكن لا يزال هناك تراكم في النشر مقارنة بخطة البيانات المفتوحة للهيئة.",
         "GAP-2026-004"),
        ("NDI-PDPL-001", 3, 4, "APPROVED",
         "Records of processing activities (ROPA) are documented for most citizen "
         "services per PDPL Article 18, but ROPA for newly launched e-services is "
         "incomplete.",
         "وُثّق سجل أنشطة المعالجة لمعظم الخدمات المقدمة للمستفيدين وفق المادة 18 "
         "من نظام حماية البيانات الشخصية، إلا أن سجل الخدمات الإلكترونية الجديدة "
         "غير مكتمل.",
         "GAP-2026-003"),
    ]
    mat_ids = {}
    for code, cur_lvl, tgt, status, ren, ar, gaplink in MAT:
        it = fw.get(code)
        if not it:
            continue
        score = round(cur_lvl / 5.0 * 100.0, 2)
        mid = upsert(
            cur, "ndi.maturity_assessment", "ndi.maturity_assessment",
            "cycle_id=? AND framework_item_id=?", (cycle_id, it["id"]),
            ["external_id", "cycle_id", "framework_item_id", "component",
             "domain", "current_level", "target_level", "score", "weight",
             "response_text_en", "response_text_ar", "evidence_required",
             "assessor_name", "domain_owner_name", "reviewer_name",
             "approver_name", "status", "submitted_at", "approved_at",
             "created_by", "updated_by"],
            [f"EXT-MAT-2026-{code}", cycle_id, it["id"], it["comp"],
             it["domain"], cur_lvl, tgt, score, 5.0, ren, ar, 1,
             DG_LEAD, DMO_DIR, CDO, DATAGOV_CHAIR, status,
             "2026-03-15T09:00:00", "2026-03-28T12:00:00", USER, USER],
        )
        mat_ids[code] = (mid, gaplink)

    # ── 3. compliance_assessment (one per COMPLIANCE framework_item) ──────────
    # (req_code, status, score, response_en, response_ar, needs_gap, gap_code, ev_code)
    COMP = [
        ("NDI-DG-C-001", "COMPLIANT", 100.0,
         "The Authority's data governance policy is approved by the President and "
         "published on the intranet; ratified by COM-DATAGOV.",
         "اعتُمدت سياسة حوكمة البيانات من رئيس الهيئة ونُشرت على البوابة الداخلية، "
         "وأقرّتها لجنة حوكمة البيانات.",
         0, None, "EV-2026-001"),
        ("NDI-DC-C-001", "PARTIAL", 55.0,
         "Metadata for the beneficiary and licensing registers follows the NDMO "
         "DCAT-AP-SA standard, but inspection and archive datasets are not yet "
         "compliant.",
         "تتبع البيانات الوصفية لسجلّي المستفيدين والتراخيص المعيار الوطني، إلا أن "
         "مجموعات بيانات التفتيش والأرشيف غير مطابقة بعد.",
         1, "GAP-2026-001", "EV-2026-004"),
        ("NDI-MDM-C-001", "PARTIAL", 60.0,
         "Master data governance is applied to the beneficiary entity, but a "
         "single golden record for organisations/establishments across registers "
         "is not yet established.",
         "طُبّقت حوكمة البيانات الرئيسية على كيان المستفيد، إلا أنه لم يُنشأ بعد "
         "سجل ذهبي موحّد للمنشآت عبر السجلات.",
         1, "GAP-2026-002", None),
        ("NDI-DLM-C-001", "PARTIAL", 50.0,
         "A retention policy exists, but the disposal schedule for inspection "
         "records is not yet fully aligned to the National Center for Records & "
         "Archives (NCAR) retention schedule.",
         "توجد سياسة احتفاظ، إلا أن جدول الإتلاف لسجلات التفتيش غير متوائم بالكامل "
         "مع جدول الاحتفاظ الصادر عن المركز الوطني للوثائق والمحفوظات.",
         1, "GAP-2026-005", "EV-2026-006"),
        ("NDI-DS-C-001", "COMPLIANT", 95.0,
         "Role-based access control is enforced on all critical datasets, with "
         "periodic access recertification by data owners.",
         "تُطبَّق صلاحيات الوصول المبنية على الأدوار على جميع مجموعات البيانات "
         "الحرجة، مع إعادة اعتماد دورية للصلاحيات من ملّاك البيانات.",
         0, None, None),
        ("NDI-PDPL-C-001", "PARTIAL", 65.0,
         "Privacy notices for the main portal comply with PDPL Article 12, but "
         "notices for newly launched e-services have not yet been refreshed.",
         "تلتزم إشعارات الخصوصية للبوابة الرئيسية بالمادة 12 من نظام حماية البيانات "
         "الشخصية، إلا أن إشعارات الخدمات الإلكترونية الجديدة لم تُحدَّث بعد.",
         1, "GAP-2026-008", "EV-2026-010"),
        ("NDI-DL-C-001", "NON_COMPLIANT", 40.0,
         "Mandatory data governance training has not yet reached the 95% "
         "completion target across the Authority's workforce.",
         "لم يبلغ التدريب الإلزامي على حوكمة البيانات نسبة الإنجاز المستهدفة 95% "
         "على مستوى منسوبي الهيئة.",
         1, None, "EV-2026-008"),
    ]
    comp_ids = {}
    for code, status, score, ren, ar, needs_gap, gaplink, ev in COMP:
        it = fw.get(code)
        if not it:
            continue
        ev_status = "ATTACHED" if ev else None
        cid = upsert(
            cur, "ndi.compliance_assessment", "ndi.compliance_assessment",
            "cycle_id=? AND requirement_code=?", (cycle_id, code),
            ["external_id", "cycle_id", "framework_item_id", "requirement_code",
             "component", "domain", "compliance_status", "compliance_score",
             "weight", "response_text_en", "response_text_ar", "evidence_ref",
             "evidence_required", "evidence_status", "needs_gap",
             "control_owner_name", "evidence_owner_name", "reviewer_name",
             "approver_name", "lifecycle_status", "submitted_at", "approved_at",
             "created_by", "updated_by"],
            [f"EXT-COMP-2026-{code}", cycle_id, it["id"], code, it["comp"],
             it["domain"], status, score, 5.0, ren, ar, ev, 1, ev_status,
             needs_gap, DMO_DIR, DG_LEAD, CDO, DATAGOV_CHAIR, "APPROVED",
             "2026-03-15T09:00:00", "2026-03-28T12:00:00", USER, USER],
        )
        comp_ids[code] = (cid, gaplink)

    # ── 4. operational_excellence_kpi (5, mapped to the 5 OE framework items) ──
    # (kpi_code, oe_req, category, name_en, name_ar, baseline, target, actual,
    #  unit, status, trend, owner)
    KPI = [
        ("KPI-2026-CAT", "NDI-OE-002", "Data Catalog",
         "Critical data assets catalogued", "نسبة أصول البيانات الحرجة المفهرسة",
         72.0, 90.0, 78.0, "%", "BEHIND_PLAN", "IMPROVING", DMO_DIR),
        ("KPI-2026-SHA", "NDI-OE-003", "Data Sharing",
         "Data-sharing requests fulfilled within SLA",
         "نسبة طلبات تبادل البيانات المنجزة ضمن اتفاقية مستوى الخدمة",
         80.0, 95.0, 86.0, "%", "AT_RISK", "IMPROVING", DG_LEAD),
        ("KPI-2026-PIA", "NDI-OE-004", "Personal Data Protection",
         "PIAs completed before service launch",
         "نسبة تقييمات الأثر على الخصوصية المنجزة قبل إطلاق الخدمة",
         60.0, 100.0, 80.0, "%", "BEHIND_PLAN", "IMPROVING", DPO),
        ("KPI-2026-MTTR", "NDI-OE-001", "Data Quality",
         "Mean time to resolve data-quality issues",
         "متوسط زمن معالجة مشكلات جودة البيانات",
         15.0, 7.0, 11.0, "days", "AT_RISK", "IMPROVING", DQ_LEAD),
        ("KPI-2026-LIT", "NDI-OE-005", "Data Literacy",
         "Data literacy programme employee coverage",
         "نسبة تغطية برنامج الثقافة البياناتية لمنسوبي الهيئة",
         45.0, 95.0, 58.0, "%", "BEHIND_PLAN", "IMPROVING", CDO),
    ]
    for kc, oe, cat, nen, nar, base, tgt, act, unit, status, trend, owner in KPI:
        it = fw.get(oe)
        upsert(
            cur, "ndi.operational_excellence_kpi", "ndi.operational_excellence_kpi",
            "kpi_code=?", (kc,),
            ["external_id", "cycle_id", "kpi_code", "category",
             "indicator_name_en", "indicator_name_ar", "baseline", "target",
             "actual", "unit", "frequency", "owner_name", "status", "trend",
             "last_actual_at", "created_by", "updated_by"],
            [f"EXT-{kc}", cycle_id, kc, cat, nen, nar, base, tgt, act, unit,
             "QUARTERLY", owner, status, trend, "2026-03-31", USER, USER],
        )

    # ── 5. gap (8 government gaps), CAPAs, evidence, plan, items, package ──────
    # Build evidence first so CAPAs can reference evidence_id.
    # (ev_code, title_en, title_ar, type, classification, status, owner)
    EV = [
        ("EV-2026-001", "Approved Data Governance Policy",
         "سياسة حوكمة البيانات المعتمدة", "POLICY", "INTERNAL", "ACTIVE", DMO_DIR),
        ("EV-2026-002", "Data Classification Policy (NDMO-aligned)",
         "سياسة تصنيف البيانات (وفق مكتب إدارة البيانات)", "POLICY",
         "INTERNAL", "ACTIVE", DG_LEAD),
        ("EV-2026-003", "Records of Processing Activities (ROPA) export",
         "مخرج سجل أنشطة المعالجة", "REPORT", "CONFIDENTIAL", "ACTIVE", DPO),
        ("EV-2026-004", "Data Catalog Inventory Report",
         "تقرير جرد فهرس البيانات", "REPORT", "INTERNAL", "ACTIVE", DMO_DIR),
        ("EV-2026-005", "DMO Organisation Chart & RACI",
         "الهيكل التنظيمي ومصفوفة المسؤوليات لمكتب إدارة البيانات", "REPORT",
         "INTERNAL", "ACTIVE", DMO_DIR),
        ("EV-2026-006", "Retention Schedule approved per NCAR",
         "جدول الاحتفاظ المعتمد وفق المركز الوطني للوثائق والمحفوظات",
         "POLICY", "INTERNAL", "ACTIVE", DG_LEAD),
        ("EV-2026-007", "Open Data Publication Log (data.gov.sa)",
         "سجل نشر البيانات المفتوحة على البوابة الوطنية", "AUDIT_LOG",
         "PUBLIC", "ACTIVE", DG_LEAD),
        ("EV-2026-008", "Data Governance Training Completion Report",
         "تقرير إنجاز التدريب على حوكمة البيانات", "REPORT", "INTERNAL",
         "ACTIVE", CDO),
        ("EV-2026-009", "Inter-Authority Data-Sharing Agreement",
         "اتفاقية تبادل البيانات بين الجهات الحكومية", "ATTESTATION",
         "CONFIDENTIAL", "ACTIVE", DMO_DIR),
        ("EV-2026-010", "DPIA Approval Memos (citizen e-services)",
         "مذكرات اعتماد تقييم الأثر على حماية البيانات للخدمات الإلكترونية",
         "ATTESTATION", "CONFIDENTIAL", "ACTIVE", DPO),
    ]
    ev_ids = {}
    for ec, ten, tar, typ, cls, status, owner in EV:
        eid = upsert(
            cur, "ndi.evidence_item", "ndi.evidence_item",
            "evidence_code=?", (ec,),
            ["external_id", "cycle_id", "evidence_code", "title_en", "title_ar",
             "evidence_type", "classification", "owner_name", "reviewer_name",
             "effective_date", "expiry_date", "status", "created_by", "updated_by"],
            [f"EXT-{ec}", cycle_id, ec, ten, tar, typ, cls, owner, DMO_DIR,
             "2026-01-15", "2027-01-15", status, USER, USER],
        )
        ev_ids[ec] = eid

    # gaps: (gap_code, source_type, source_req, domain, sev, title_en, title_ar,
    #        rollup, status, owner, desc_en, desc_ar)
    GAP = [
        ("GAP-2026-001", "COMPLIANCE", "NDI-DC-C-001", "Data Catalog and Metadata",
         "HIGH", "Beneficiary/licensing catalog coverage below target",
         "تغطية فهرس سجلّي المستفيدين والتراخيص دون المستهدف", 40, "IN_PROGRESS",
         DMO_DIR,
         "Catalog coverage of critical registers is below the 90% target and "
         "inspection/archive metadata is not yet DCAT-AP-SA compliant.",
         "تغطية فهرس السجلات الحرجة دون المستهدف 90%، وبيانات التفتيش والأرشيف "
         "غير مطابقة للمعيار الوطني للبيانات الوصفية."),
        ("GAP-2026-002", "COMPLIANCE", "NDI-MDM-C-001", "Master and Reference Data",
         "MEDIUM", "No golden record for establishments across registers",
         "غياب السجل الذهبي للمنشآت عبر السجلات", 20, "OPEN", DG_LEAD,
         "Establishment/organisation entities are duplicated across the licensing "
         "and inspection registers with no master golden record.",
         "تتكرر كيانات المنشآت عبر سجلّي التراخيص والتفتيش دون سجل رئيسي ذهبي موحّد."),
        ("GAP-2026-003", "MATURITY", "NDI-PDPL-001", "Personal Data Protection",
         "HIGH", "ROPA incomplete for new citizen e-services",
         "سجل أنشطة المعالجة غير مكتمل للخدمات الإلكترونية الجديدة", 35,
         "IN_PROGRESS", DPO,
         "Records of processing activities are not documented for recently "
         "launched citizen e-services, a PDPL Article 18 obligation.",
         "لم تُوثّق أنشطة المعالجة للخدمات الإلكترونية المطلقة حديثًا، وهو التزام "
         "بموجب المادة 18 من نظام حماية البيانات الشخصية."),
        ("GAP-2026-004", "MATURITY", "NDI-OD-001", "Open Data",
         "MEDIUM", "Open-data publication backlog vs data.gov.sa plan",
         "تراكم نشر البيانات المفتوحة مقابل خطة البوابة الوطنية", 30, "IN_PROGRESS",
         DG_LEAD,
         "Datasets approved for release are not yet published to data.gov.sa, "
         "creating a backlog against the Authority's open-data plan.",
         "لم تُنشر مجموعات البيانات المعتمدة للإصدار على البوابة الوطنية للبيانات "
         "المفتوحة، مما أوجد تراكمًا مقابل خطة البيانات المفتوحة للهيئة."),
        ("GAP-2026-005", "COMPLIANCE", "NDI-DLM-C-001", "Data Lifecycle Management",
         "CRITICAL", "Retention schedule for inspection records not NCAR-aligned",
         "جدول احتفاظ سجلات التفتيش غير متوائم مع المركز الوطني للوثائق",
         15, "OPEN", DMO_DIR,
         "The disposal schedule for inspection records is not aligned to the "
         "National Center for Records & Archives (NCAR) retention schedule, "
         "creating a compliance exposure.",
         "جدول إتلاف سجلات التفتيش غير متوائم مع جدول الاحتفاظ الصادر عن المركز "
         "الوطني للوثائق والمحفوظات، مما يشكّل مخاطرة التزامية."),
        ("GAP-2026-006", "MATURITY", "NDI-CL-001", "Data Classification",
         "MEDIUM", "Classification of unstructured case files not automated",
         "تصنيف ملفات القضايا غير المهيكلة غير مؤتمت", 10, "OPEN", DG_LEAD,
         "Unstructured case files are classified manually; automated "
         "classification tooling aligned to the NDMO classification policy is "
         "not yet deployed.",
         "تُصنّف ملفات القضايا غير المهيكلة يدويًا؛ ولم تُطبَّق بعد أدوات التصنيف "
         "الآلي المتوائمة مع سياسة التصنيف الوطنية."),
        ("GAP-2026-007", "MATURITY", "NDI-DSH-001", "Data Sharing",
         "HIGH", "Inter-authority data-sharing agreements pending",
         "اتفاقيات تبادل البيانات بين الجهات بانتظار التوقيع", 25, "IN_PROGRESS",
         DMO_DIR,
         "Several data-sharing agreements with partner authorities required for "
         "national-integration data exchange are pending signature.",
         "عدد من اتفاقيات تبادل البيانات مع الجهات الشريكة اللازمة للتكامل الوطني "
         "بانتظار التوقيع."),
        ("GAP-2026-008", "COMPLIANCE", "NDI-PDPL-C-001", "Personal Data Protection",
         "LOW", "Privacy notice refresh pending for new e-service",
         "تحديث إشعار الخصوصية بانتظار الإنجاز لخدمة إلكترونية جديدة", 50,
         "IN_PROGRESS", DPO,
         "The PDPL Article 12 privacy notice for a newly launched e-service has "
         "not yet been refreshed to reflect the updated processing.",
         "لم يُحدَّث إشعار الخصوصية وفق المادة 12 لخدمة إلكترونية مطلقة حديثًا "
         "ليعكس المعالجة المُحدَّثة."),
    ]
    gap_ids = {}
    for gc, src_type, src_req, domain, sev, ten, tar, rollup, status, owner, den, dar in GAP:
        # resolve source_id from the matching maturity/compliance row
        src_id = None
        if src_type == "COMPLIANCE" and src_req in comp_ids:
            src_id = comp_ids[src_req][0]
        elif src_type == "MATURITY" and src_req in mat_ids:
            src_id = mat_ids[src_req][0]
        gid = upsert(
            cur, "ndi.gap", "ndi.gap",
            "gap_code=?", (gc,),
            ["external_id", "cycle_id", "gap_code", "source_type", "source_id",
             "domain", "title_en", "title_ar", "description_en", "description_ar",
             "severity", "owner_name", "open_date", "target_date",
             "rollup_progress", "status", "created_by", "updated_by"],
            [f"EXT-{gc}", cycle_id, gc, src_type, src_id, domain, ten, tar,
             den, dar, sev, owner, "2026-02-01", "2026-09-30", rollup, status,
             USER, USER],
        )
        gap_ids[gc] = gid

    # corrective_action (10): (capa_code, gap_code, action_en, action_ar, type,
    #   progress, status, owner, ev_code)
    CAPA = [
        ("CAPA-2026-001", "GAP-2026-001",
         "Publish DCAT-AP-SA-compliant metadata for the top registers in the data catalog",
         "نشر بيانات وصفية مطابقة للمعيار الوطني لأهم السجلات في فهرس البيانات",
         "PROCESS", 40, "IN_PROGRESS", DG_LEAD, "EV-2026-004"),
        ("CAPA-2026-002", "GAP-2026-001",
         "Complete cataloguing of inspection and correspondence datasets",
         "استكمال فهرسة مجموعات بيانات التفتيش والمراسلات",
         "PROCESS", 30, "IN_PROGRESS", DMO_DIR, None),
        ("CAPA-2026-003", "GAP-2026-002",
         "Establish a master golden record for establishments across registers",
         "إنشاء سجل ذهبي رئيسي للمنشآت عبر السجلات",
         "TECH", 10, "NOT_STARTED", DG_LEAD, None),
        ("CAPA-2026-004", "GAP-2026-003",
         "Complete ROPA for licensing, inspection and beneficiary e-services per PDPL Art.18",
         "استكمال سجل أنشطة المعالجة للخدمات الإلكترونية وفق المادة 18",
         "GOVERNANCE", 35, "IN_PROGRESS", DPO, "EV-2026-003"),
        ("CAPA-2026-005", "GAP-2026-004",
         "Stand up an open-data publication pipeline to data.gov.sa and clear the backlog",
         "تأسيس مسار نشر للبيانات المفتوحة إلى البوابة الوطنية ومعالجة التراكم",
         "TECH", 30, "IN_PROGRESS", DG_LEAD, "EV-2026-007"),
        ("CAPA-2026-006", "GAP-2026-005",
         "Align retention and disposal schedules to the NCAR retention schedule",
         "مواءمة جداول الاحتفاظ والإتلاف مع جدول المركز الوطني للوثائق والمحفوظات",
         "POLICY", 20, "IN_PROGRESS", DMO_DIR, "EV-2026-006"),
        ("CAPA-2026-007", "GAP-2026-006",
         "Deploy automated classification tooling for unstructured case files",
         "تطبيق أدوات التصنيف الآلي لملفات القضايا غير المهيكلة",
         "TECH", 5, "NOT_STARTED", DG_LEAD, None),
        ("CAPA-2026-008", "GAP-2026-007",
         "Sign inter-authority data-sharing agreements via the national integration channel",
         "توقيع اتفاقيات تبادل البيانات بين الجهات عبر قناة التكامل الوطنية",
         "GOVERNANCE", 25, "IN_PROGRESS", DMO_DIR, "EV-2026-009"),
        ("CAPA-2026-009", "GAP-2026-008",
         "Refresh PDPL Article 12 privacy notices for the new e-services",
         "تحديث إشعارات الخصوصية وفق المادة 12 للخدمات الإلكترونية الجديدة",
         "CONTROL", 50, "IN_PROGRESS", DPO, "EV-2026-010"),
        ("CAPA-2026-010", "GAP-2026-003",
         "Deliver mandatory data governance training to reach 95% workforce coverage",
         "تنفيذ التدريب الإلزامي على حوكمة البيانات لبلوغ تغطية 95% من المنسوبين",
         "TRAINING", 45, "IN_PROGRESS", CDO, "EV-2026-008"),
    ]
    capa_ids = {}
    for cc, gc, aen, aar, typ, prog, status, owner, ev in CAPA:
        gid = gap_ids.get(gc)
        if gid is None:
            continue
        eid = ev_ids.get(ev) if ev else None
        cid = upsert(
            cur, "ndi.corrective_action", "ndi.corrective_action",
            "action_code=?", (cc,),
            ["external_id", "gap_id", "action_code", "action_en", "action_ar",
             "action_type", "owner_name", "target_date", "progress_pct",
             "status", "evidence_id", "created_by", "updated_by"],
            [f"EXT-{cc}", gid, cc, aen, aar, typ, owner, "2026-09-30", prog,
             status, eid, USER, USER],
        )
        capa_ids[cc] = cid

    # ── improvement_plan (umbrella) ───────────────────────────────────────────
    plan_id = upsert(
        cur, "ndi.improvement_plan", "ndi.improvement_plan",
        "plan_code=?", ("PLAN-2026-NDI",),
        ["external_id", "cycle_id", "plan_code", "name_en", "name_ar",
         "description_en", "description_ar", "period_start", "period_end",
         "owner_name", "approver_name", "status", "total_items",
         "completed_items", "progress_pct", "budget_total", "budget_currency",
         "created_by", "updated_by"],
        ["EXT-PLAN-2026-NDI", cycle_id, "PLAN-2026-NDI",
         "NDI Readiness & Data-Maturity Uplift Plan 2026",
         "خطة الارتقاء بجاهزية مؤشر البيانات الوطني ونضج البيانات 2026",
         "FY2026 improvement plan consolidating the corrective actions arising "
         "from the NDI-2026 assessment, sequenced to lift the Authority's "
         "NDI maturity toward the national benchmark.",
         "خطة تحسين للسنة المالية 2026 تجمع الإجراءات التصحيحية الناتجة عن تقييم "
         "دورة مؤشر البيانات الوطني 2026، مرتبة للارتقاء بنضج بيانات الهيئة نحو "
         "المعيار الوطني.",
         "2026-01-01", "2027-06-30", DMO_DIR, DATAGOV_CHAIR, "IN_FLIGHT",
         8, 0, 28, 4500000.00, "SAR", USER, USER],
    )

    # ── improvement_plan_item (8) sequencing the CAPAs into quarters ──────────
    # (capa_code, seq, priority, quarter, budget, deps)
    PI = [
        ("CAPA-2026-001", 1, "HIGH", "2026-Q1", 600000.00, None),
        ("CAPA-2026-004", 2, "HIGH", "2026-Q1", 700000.00, None),
        ("CAPA-2026-006", 3, "CRITICAL", "2026-Q2", 400000.00, "CAPA-2026-001"),
        ("CAPA-2026-002", 4, "MEDIUM", "2026-Q2", 350000.00, "CAPA-2026-001"),
        ("CAPA-2026-009", 5, "MEDIUM", "2026-Q2", 200000.00, None),
        ("CAPA-2026-005", 6, "HIGH", "2026-Q3", 900000.00, "CAPA-2026-001"),
        ("CAPA-2026-008", 7, "HIGH", "2026-Q3", 500000.00, "CAPA-2026-004"),
        ("CAPA-2026-007", 8, "MEDIUM", "2026-Q4", 850000.00, "CAPA-2026-002"),
    ]
    for cc, seq, prio, q, budget, dep in PI:
        cid = capa_ids.get(cc)
        if cid is None:
            continue
        # natural key = (plan_id, capa_id)
        cur.execute(
            "SELECT id FROM ndi.improvement_plan_item "
            "WHERE organisation_id=? AND plan_id=? AND capa_id=?",
            ORG, plan_id, cid)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO ndi.improvement_plan_item "
            "(organisation_id, plan_id, capa_id, sequence_order, priority, "
            " target_quarter, allocated_budget, dependencies, created_by, updated_by) "
            "VALUES (?,?,?,?,?,?,?,?,?,?)",
            ORG, plan_id, cid, seq, prio, q, budget, dep, USER, USER)
        bump("ndi.improvement_plan_item")

    # ── submission_package (1) ────────────────────────────────────────────────
    attest_en = (
        "The General Authority attests that the National Data Index 2026 "
        "submission reflects its data-management posture against the NDMO "
        "standards and the SDAIA National Data Index, and authorises submission. "
        "Approved by the President of the Authority and the Data Governance "
        "Committee.")
    attest_ar = (
        "تقرّ الهيئة العامة بأن تقديم مؤشر البيانات الوطني لعام 2026 يعكس وضع إدارة "
        "البيانات لديها وفق معايير مكتب إدارة البيانات الوطنية ومؤشر البيانات "
        "الوطني الصادر عن سدايا، وتعتمد التقديم. مُعتمد من رئيس الهيئة ولجنة حوكمة "
        "البيانات.")
    upsert(
        cur, "ndi.submission_package", "ndi.submission_package",
        "package_code=?", ("PKG-2026-v1",),
        ["external_id", "cycle_id", "package_code", "version",
         "overall_ndi_pct", "maturity_score_pct", "compliance_pct",
         "opex_score_pct", "total_evidence", "total_gaps", "critical_gaps",
         "total_actions", "completed_actions", "status", "preparer_name",
         "reviewer_name", "approver_name", "attestation_en", "attestation_ar",
         "is_placeholder", "prepared_at", "created_by", "updated_by"],
        ["EXT-PKG-2026-v1", cycle_id, "PKG-2026-v1", 1,
         63.0, 62.0, 68.0, 58.0, len(EV), len(GAP), 1, len(CAPA), 0,
         "READY", DMO_DIR, DATAGOV_CHAIR, PRESIDENT, attest_en, attest_ar,
         0, "2026-04-05T10:00:00", USER, USER],
    )

    # ── audit_event (12) narrating the cycle lifecycle ────────────────────────
    # (entity_type, action, summary, actor_role, sev, occurred_at)
    AUD = [
        ("CYCLE", "CREATE",
         "NDI-2026 readiness cycle created for the General Authority", DMO_DIR,
         "INFO", "2026-01-05T08:00:00"),
        ("CYCLE", "TRANSITION",
         "Framework selected: NDMO/NDI standard items adopted for NDI-2026",
         DG_LEAD, "INFO", "2026-01-12T09:00:00"),
        ("MATURITY_ASSESSMENT", "SUBMIT",
         "Maturity self-assessment submitted across 8 NDMO domains", DG_LEAD,
         "INFO", "2026-03-15T09:00:00"),
        ("MATURITY_ASSESSMENT", "APPROVE",
         "Maturity assessment approved by the Data Governance Committee",
         DATAGOV_CHAIR, "INFO", "2026-03-28T12:00:00"),
        ("COMPLIANCE_ASSESSMENT", "SUBMIT",
         "Compliance responses submitted for 7 NDI requirements", DMO_DIR,
         "INFO", "2026-03-15T09:30:00"),
        ("COMPLIANCE_ASSESSMENT", "APPROVE",
         "Compliance assessment approved; 3 partial and 1 non-compliant noted",
         DATAGOV_CHAIR, "WARN", "2026-03-28T12:30:00"),
        ("GAP", "CREATE",
         "8 gaps raised from the NDI-2026 assessment (2 high/critical)", DMO_DIR,
         "WARN", "2026-04-01T10:00:00"),
        ("CORRECTIVE_ACTION", "CREATE",
         "10 corrective actions assigned to DMO, Data Governance and Privacy",
         DMO_DIR, "INFO", "2026-04-02T11:00:00"),
        ("EVIDENCE_ITEM", "CREATE",
         "Evidence pack attached (policies, ROPA, catalog, NCAR schedule)",
         DG_LEAD, "INFO", "2026-04-03T09:00:00"),
        ("IMPROVEMENT_PLAN", "APPROVE",
         "FY2026 NDI improvement plan approved (budget SAR 4.5M)", DATAGOV_CHAIR,
         "INFO", "2026-04-04T13:00:00"),
        ("SUBMISSION_PACKAGE", "CREATE",
         "NDI-2026 submission package prepared (PKG-2026-v1)", DMO_DIR, "INFO",
         "2026-04-05T10:00:00"),
        ("SUBMISSION_PACKAGE", "TRANSITION",
         "Submission package marked READY pending Presidential endorsement",
         PRESIDENT, "INFO", "2026-04-06T08:00:00"),
    ]
    for etype, action, summary, role, sev, when in AUD:
        # natural key = (cycle_id, entity_type, action, occurred_at)
        cur.execute(
            "SELECT id FROM ndi.audit_event WHERE organisation_id=? AND cycle_id=? "
            "AND entity_type=? AND action=? AND occurred_at=?",
            ORG, cycle_id, etype, action, when)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO ndi.audit_event "
            "(organisation_id, cycle_id, entity_type, entity_code, action, "
            " severity, actor_username, actor_role, summary, occurred_at) "
            "VALUES (?,?,?,?,?,?,?,?,?,?)",
            ORG, cycle_id, etype, "NDI-2026", action, sev, USER, role,
            summary, when)
        bump("ndi.audit_event")

    return cycle_id


# ═════════════════════════════════════════════════════════════════════════════
#  data_officer.*  — government data catalog, quality, retention, privacy
# ═════════════════════════════════════════════════════════════════════════════
def seed_data_officer(cur):
    # ── catalog_entry (10 government data assets) ─────────────────────────────
    # (ref, name_en, name_ar, domain, classification, pii, special, owner_role,
    #  owner_u, steward_u, source, freq, desc_en, desc_ar)
    CAT = [
        ("CAT-GOV-001", "National Beneficiary Registry", "سجل المستفيدين الوطني",
         "BENEFICIARY", "CONFIDENTIAL", 1, 1, DMO_DIR, ADMIN, RANALYST,
         "Beneficiary Services Platform", "DAILY",
         "Master registry of beneficiaries of the Authority's services, keyed by "
         "national ID / Iqama.",
         "السجل الرئيسي لمستفيدي خدمات الهيئة، مفهرس برقم الهوية الوطنية/الإقامة."),
        ("CAT-GOV-002", "Licensing & Permits Register", "سجل التراخيص والتصاريح",
         "LICENSING", "INTERNAL", 1, 0, DMO_DIR, ADMIN, RANALYST,
         "Licensing System", "DAILY",
         "Register of licences and permits issued by the Authority to "
         "establishments and individuals.",
         "سجل التراخيص والتصاريح الصادرة عن الهيئة للمنشآت والأفراد."),
        ("CAT-GOV-003", "Field Inspection Records", "سجلات التفتيش الميداني",
         "INSPECTION", "CONFIDENTIAL", 1, 0, DG_LEAD, RMANAGER, RANALYST,
         "Inspection Management System", "DAILY",
         "Records of field inspections, findings and corrective notices linked to "
         "licensed establishments.",
         "سجلات الزيارات التفتيشية الميدانية والملاحظات والإشعارات التصحيحية "
         "المرتبطة بالمنشآت المرخّصة."),
        ("CAT-GOV-004", "Complaints & Cases Registry", "سجل الشكاوى والقضايا",
         "COMPLAINTS", "CONFIDENTIAL", 1, 1, DG_LEAD, RMANAGER, RANALYST,
         "Citizen Complaints Platform", "HOURLY",
         "Citizen and beneficiary complaints and case files, with SLA tracking.",
         "شكاوى المستفيدين والمواطنين وملفات القضايا مع متابعة اتفاقية مستوى الخدمة."),
        ("CAT-GOV-005", "Open Data Catalog", "فهرس البيانات المفتوحة",
         "OPEN_DATA", "PUBLIC", 0, 0, DG_LEAD, ADMIN, RANALYST,
         "Open Data Publishing Service", "MONTHLY",
         "Aggregated, anonymised datasets published to the Saudi Open Data portal "
         "(data.gov.sa).",
         "مجموعات بيانات مجمّعة ومجهّلة الهوية منشورة على البوابة الوطنية للبيانات "
         "المفتوحة."),
        ("CAT-GOV-006", "Service Performance & BI Dataset",
         "مجموعة بيانات أداء الخدمات وذكاء الأعمال",
         "ANALYTICS", "INTERNAL", 0, 0, CDO, ADMIN, RANALYST,
         "Business Intelligence Platform", "DAILY",
         "Curated analytical dataset on service volumes, SLA performance and "
         "operational KPIs.",
         "مجموعة بيانات تحليلية لأحجام الخدمات وأداء اتفاقيات مستوى الخدمة "
         "ومؤشرات التشغيل."),
        ("CAT-GOV-007", "GIS / Geospatial Assets", "الأصول الجغرافية المكانية",
         "GEOSPATIAL", "INTERNAL", 0, 0, DG_LEAD, RMANAGER, RANALYST,
         "Enterprise GIS", "MONTHLY",
         "Geospatial layers and addressing data supporting inspection routing and "
         "service coverage analysis.",
         "الطبقات الجغرافية المكانية وبيانات العنونة الداعمة لتوجيه التفتيش وتحليل "
         "تغطية الخدمات."),
        ("CAT-GOV-008", "Document Archive (NCAR)", "الأرشيف الوثائقي",
         "ARCHIVE", "CONFIDENTIAL", 1, 0, DMO_DIR, ADMIN, RANALYST,
         "Electronic Document Management System", "DAILY",
         "Electronic document and records archive governed by the NCAR retention "
         "schedule.",
         "الأرشيف الإلكتروني للوثائق والسجلات وفق جدول الاحتفاظ الصادر عن المركز "
         "الوطني للوثائق والمحفوظات."),
        ("CAT-GOV-009", "HR & Payroll", "الموارد البشرية والرواتب",
         "HR", "CONFIDENTIAL", 1, 1, DMO_DIR, ADMIN, RANALYST,
         "HR Information System", "MONTHLY",
         "Employee master data and payroll records for the Authority's workforce.",
         "البيانات الرئيسية للموظفين وسجلات الرواتب لمنسوبي الهيئة."),
        ("CAT-GOV-010", "Correspondence / CMS", "المراسلات وإدارة المحتوى",
         "CORRESPONDENCE", "INTERNAL", 1, 0, DG_LEAD, ADMIN, RANALYST,
         "Correspondence Management System", "HOURLY",
         "Incoming/outgoing official correspondence and content management "
         "records.",
         "المراسلات الرسمية الواردة والصادرة وسجلات إدارة المحتوى."),
    ]
    cat_ids = {}
    for ref, nen, nar, dom, cls, pii, spc, role, ou, su, src, freq, den, dar in CAT:
        cid = upsert(
            cur, "data_officer.catalog_entry", "data_officer.catalog_entry",
            "reference=?", (ref,),
            ["reference", "dataset_name_en", "dataset_name_ar", "description_en",
             "description_ar", "domain", "source_system", "classification",
             "contains_pii", "contains_special_category", "owner_role",
             "owner_user_id", "steward_user_id", "refresh_frequency",
             "created_by", "updated_by"],
            [ref, nen, nar, den, dar, dom, src, cls, pii, spc, role, ou, su,
             freq, ADMIN, ADMIN],
        )
        cat_ids[ref] = cid

    # ── catalog_lineage (8 edges among the new gov assets) ────────────────────
    # (source_ref, target_ref, label)
    LIN = [
        ("CAT-GOV-001", "CAT-GOV-006", "ETL aggregation into BI dataset"),
        ("CAT-GOV-002", "CAT-GOV-006", "ETL aggregation into BI dataset"),
        ("CAT-GOV-003", "CAT-GOV-006", "ETL aggregation into BI dataset"),
        ("CAT-GOV-002", "CAT-GOV-005", "Anonymisation for open-data publication"),
        ("CAT-GOV-003", "CAT-GOV-005", "Anonymisation for open-data publication"),
        ("CAT-GOV-001", "CAT-GOV-004", "Beneficiary linkage to complaints"),
        ("CAT-GOV-008", "CAT-GOV-010", "Archive feed from correspondence"),
        ("CAT-GOV-007", "CAT-GOV-003", "Geocoding for inspection routing"),
    ]
    for sref, tref, label in LIN:
        sid, tid = cat_ids.get(sref), cat_ids.get(tref)
        if not (sid and tid):
            continue
        cur.execute(
            "SELECT id FROM data_officer.catalog_lineage WHERE organisation_id=? "
            "AND source_entry_id=? AND target_entry_id=?", ORG, sid, tid)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO data_officer.catalog_lineage "
            "(organisation_id, source_entry_id, target_entry_id, transform_label, "
            " created_by, updated_by) VALUES (?,?,?,?,?,?)",
            ORG, sid, tid, label, ADMIN, ADMIN)
        bump("data_officer.catalog_lineage")

    # ── quality_rule (8) on the new gov assets ────────────────────────────────
    # (ref, cat_ref, name_en, name_ar, dimension, threshold, severity)
    QR = [
        ("DQR-GOV-001", "CAT-GOV-001", "National ID completeness on beneficiaries",
         "اكتمال رقم الهوية الوطنية للمستفيدين", "COMPLETENESS", 99.0, "CRITICAL"),
        ("DQR-GOV-002", "CAT-GOV-001", "National ID checksum validity",
         "صحة خوارزمية رقم الهوية الوطنية", "VALIDITY", 99.5, "CRITICAL"),
        ("DQR-GOV-003", "CAT-GOV-002", "Licence number format validity",
         "صحة صيغة رقم الترخيص", "VALIDITY", 98.0, "HIGH"),
        ("DQR-GOV-004", "CAT-GOV-003", "Inspection record references a valid licence",
         "ارتباط سجل التفتيش بترخيص صحيح", "INTEGRITY", 97.0, "HIGH"),
        ("DQR-GOV-005", "CAT-GOV-004", "Complaint SLA timestamp present",
         "وجود ختم زمني لاتفاقية مستوى خدمة الشكوى", "TIMELINESS", 95.0, "MEDIUM"),
        ("DQR-GOV-006", "CAT-GOV-003", "Inspection address normalised / geocoded",
         "توحيد عنوان التفتيش وترميزه الجغرافي", "CONSISTENCY", 90.0, "MEDIUM"),
        ("DQR-GOV-007", "CAT-GOV-001", "Duplicate beneficiary detection",
         "كشف تكرار المستفيدين", "UNIQUENESS", 99.0, "HIGH"),
        ("DQR-GOV-008", "CAT-GOV-005", "Open-data dataset freshness",
         "حداثة مجموعات البيانات المفتوحة", "TIMELINESS", 90.0, "MEDIUM"),
    ]
    qr_ids = {}
    for ref, cref, nen, nar, dim, thr, sev in QR:
        cid = cat_ids.get(cref)
        if cid is None:
            continue
        rid = upsert(
            cur, "data_officer.quality_rule", "data_officer.quality_rule",
            "reference=?", (ref,),
            ["reference", "catalog_entry_id", "rule_name_en", "rule_name_ar",
             "dimension", "threshold_pct", "severity", "is_active",
             "created_by", "updated_by"],
            [ref, cid, nen, nar, dim, thr, sev, 1, ADMIN, ADMIN],
        )
        qr_ids[ref] = rid

    # ── quality_run (12: 1-2 per rule, mix of bands) ──────────────────────────
    # (rule_ref, run_at, checked, failed, pass_pct, band, note_en, note_ar)
    QRUN = [
        ("DQR-GOV-001", "2026-02-10T02:00:00", 1250000, 8200, 99.34, "GREEN",
         "Within threshold on beneficiary registry.",
         "ضمن الحد المقبول على سجل المستفيدين."),
        ("DQR-GOV-001", "2026-03-10T02:00:00", 1262000, 9100, 99.28, "AMBER",
         "Slight increase in missing IDs after intake of legacy records.",
         "ارتفاع طفيف في الهويات المفقودة بعد إدراج سجلات قديمة."),
        ("DQR-GOV-002", "2026-03-10T02:10:00", 1262000, 4300, 99.66, "GREEN",
         "Checksum validity above target.",
         "صحة خوارزمية الهوية أعلى من المستهدف."),
        ("DQR-GOV-003", "2026-03-10T02:20:00", 540000, 14200, 97.37, "AMBER",
         "Format violations from older licence series.",
         "مخالفات صيغة من سلاسل تراخيص قديمة."),
        ("DQR-GOV-004", "2026-03-10T02:30:00", 320000, 12800, 96.00, "RED",
         "Inspection records referencing missing licences — feeds GAP-2026-001.",
         "سجلات تفتيش ترتبط بتراخيص مفقودة — تغذّي الفجوة GAP-2026-001."),
        ("DQR-GOV-004", "2026-04-10T02:30:00", 331000, 10900, 96.71, "AMBER",
         "Improving after data-cleansing sprint.",
         "تحسّن بعد حملة تنظيف البيانات."),
        ("DQR-GOV-005", "2026-03-10T02:40:00", 89000, 5100, 94.27, "RED",
         "Missing SLA timestamps on backlog complaints.",
         "غياب الختم الزمني لاتفاقية مستوى الخدمة في الشكاوى المتراكمة."),
        ("DQR-GOV-006", "2026-03-10T02:50:00", 320000, 41000, 87.19, "RED",
         "Address normalisation gap on field inspections.",
         "فجوة توحيد العناوين في التفتيش الميداني."),
        ("DQR-GOV-007", "2026-03-10T03:00:00", 1262000, 7400, 99.41, "GREEN",
         "Duplicate rate within tolerance.",
         "نسبة التكرار ضمن الحد المقبول."),
        ("DQR-GOV-008", "2026-03-10T03:10:00", 120, 18, 85.00, "RED",
         "Open-data freshness below target — feeds GAP-2026-004.",
         "حداثة البيانات المفتوحة دون المستهدف — تغذّي الفجوة GAP-2026-004."),
        ("DQR-GOV-008", "2026-04-10T03:10:00", 132, 14, 89.39, "AMBER",
         "Backlog reducing as the publication pipeline ramps.",
         "تراجع التراكم مع تشغيل مسار النشر."),
        ("DQR-GOV-003", "2026-04-10T02:20:00", 548000, 11900, 97.83, "AMBER",
         "Format violations decreasing after cleansing.",
         "تراجع مخالفات الصيغة بعد التنظيف."),
    ]
    for rref, rat, chk, fail, pp, band, nen, nar in QRUN:
        rid = qr_ids.get(rref)
        if rid is None:
            continue
        cur.execute(
            "SELECT id FROM data_officer.quality_run WHERE organisation_id=? "
            "AND quality_rule_id=? AND run_at=?", ORG, rid, rat)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO data_officer.quality_run "
            "(organisation_id, quality_rule_id, run_at, records_checked, "
            " records_failed, pass_pct, band, notes_en, notes_ar, created_by, updated_by) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            ORG, rid, rat, chk, fail, pp, band, nen, nar, ADMIN, ADMIN)
        bump("data_officer.quality_run")

    # ── retention_rule (8, NCAR/NDMO/PDPL aligned) ────────────────────────────
    # (ref, category, months, disposal, review_months, owner_role, owner_u,
    #  asset_ref, legal_en, legal_ar, desc_en, desc_ar)
    RET = [
        ("RET-GOV-001", "Beneficiary case files", 120, "ANONYMISE", 12, DMO_DIR,
         ADMIN, "CAT-GOV-001",
         "Retained per NCAR retention schedule and PDPL minimisation; anonymised "
         "after the statutory period.",
         "يُحتفظ بها وفق جدول المركز الوطني للوثائق والمحفوظات ومبدأ تقليل البيانات "
         "في نظام حماية البيانات الشخصية، وتُجهَّل بعد المدة النظامية.",
         "Retention rule for beneficiary case files.",
         "قاعدة احتفاظ لملفات قضايا المستفيدين."),
        ("RET-GOV-002", "Licensing records", 180, "OVERWRITE", 12, DMO_DIR, ADMIN,
         "CAT-GOV-002",
         "Retained per NCAR schedule for regulatory licensing evidence.",
         "يُحتفظ بها وفق جدول المركز الوطني للوثائق والمحفوظات كأدلة ترخيص تنظيمية.",
         "Retention rule for licensing records.",
         "قاعدة احتفاظ لسجلات التراخيص."),
        ("RET-GOV-003", "Inspection reports", 120, "OVERWRITE", 12, DG_LEAD,
         RMANAGER, "CAT-GOV-003",
         "Retained per NCAR schedule; disposal pending alignment (GAP-2026-005).",
         "يُحتفظ بها وفق جدول المركز الوطني؛ الإتلاف بانتظار المواءمة "
         "(الفجوة GAP-2026-005).",
         "Retention rule for field inspection reports.",
         "قاعدة احتفاظ لتقارير التفتيش الميداني."),
        ("RET-GOV-004", "Complaints & cases", 84, "ANONYMISE", 12, DG_LEAD,
         RMANAGER, "CAT-GOV-004",
         "Retained per NCAR schedule and PDPL; anonymised after closure period.",
         "يُحتفظ بها وفق جدول المركز الوطني ونظام حماية البيانات؛ تُجهَّل بعد فترة "
         "الإغلاق.",
         "Retention rule for complaints and case files.",
         "قاعدة احتفاظ للشكاوى وملفات القضايا."),
        ("RET-GOV-005", "Correspondence & archive", 240, "PHYSICAL_DESTRUCTION",
         24, DMO_DIR, ADMIN, "CAT-GOV-008",
         "Retained per NCAR archival schedule for official correspondence.",
         "يُحتفظ بها وفق جدول الأرشفة للمركز الوطني للوثائق والمحفوظات للمراسلات "
         "الرسمية.",
         "Retention rule for correspondence and document archive.",
         "قاعدة احتفاظ للمراسلات والأرشيف الوثائقي."),
        ("RET-GOV-006", "HR / employee records", 300, "ANONYMISE", 24, DMO_DIR,
         ADMIN, "CAT-GOV-009",
         "Retained per HR regulations and NCAR schedule; PDPL minimisation "
         "applied after separation.",
         "يُحتفظ بها وفق أنظمة الموارد البشرية وجدول المركز الوطني؛ ويُطبَّق تقليل "
         "البيانات بعد انتهاء الخدمة.",
         "Retention rule for HR and employee records.",
         "قاعدة احتفاظ لسجلات الموارد البشرية والموظفين."),
        ("RET-GOV-007", "Open-data source snapshots", 36, "OVERWRITE", 12,
         DG_LEAD, ADMIN, "CAT-GOV-005",
         "Source snapshots retained to support reproducibility of open-data "
         "releases.",
         "يُحتفظ بلقطات المصدر لدعم قابلية إعادة إنتاج إصدارات البيانات المفتوحة.",
         "Retention rule for open-data source snapshots.",
         "قاعدة احتفاظ للقطات مصدر البيانات المفتوحة."),
        ("RET-GOV-008", "System & audit logs", 24, "CRYPTO_SHRED", 12, DG_LEAD,
         ADMIN, "CAT-GOV-006",
         "Retained per NDMO data-security and audit requirements; "
         "crypto-shredded after the retention window.",
         "يُحتفظ بها وفق متطلبات أمن البيانات والتدقيق؛ وتُمحى تشفيريًا بعد انتهاء "
         "مدة الاحتفاظ.",
         "Retention rule for system and audit logs.",
         "قاعدة احتفاظ لسجلات النظام والتدقيق."),
    ]
    ret_ids = {}
    for ref, cat, months, disp, rev, role, ou, aref, len_, lar, den, dar in RET:
        rid = upsert(
            cur, "data_officer.retention_rule", "data_officer.retention_rule",
            "reference=?", (ref,),
            ["reference", "data_category", "description_en", "description_ar",
             "retention_months", "legal_basis_en", "legal_basis_ar",
             "disposal_method", "review_cadence_months", "next_review_due",
             "owner_role", "owner_user_id", "linked_asset_ref", "status",
             "created_by", "updated_by"],
            [ref, cat, den, dar, months, len_, lar, disp, rev, "2027-01-31",
             role, ou, aref, "ACTIVE", ADMIN, ADMIN],
        )
        ret_ids[ref] = rid

    # ── disposal_job (4 against gov retention rules) ──────────────────────────
    # (ref, rule_ref, scheduled, status, disposed, hold_reason)
    DISP = [
        ("DISP-GOV-001", "RET-GOV-004", "2026-05-01", "COMPLETED", 12400, None),
        ("DISP-GOV-002", "RET-GOV-003", "2026-06-01", "ON_HOLD", None,
         "Awaiting NCAR retention-schedule alignment (GAP-2026-005)."),
        ("DISP-GOV-003", "RET-GOV-005", "2026-07-01", "SCHEDULED", None, None),
        ("DISP-GOV-004", "RET-GOV-008", "2026-04-30", "COMPLETED", 8600000, None),
    ]
    for ref, rref, sched, status, disposed, hold in DISP:
        rid = ret_ids.get(rref)
        if rid is None:
            continue
        ev_hash = f"sha256:gov-disposal-{ref.lower()}"
        upsert(
            cur, "data_officer.disposal_job", "data_officer.disposal_job",
            "reference=?", (ref,),
            ["retention_rule_id", "reference", "scheduled_for", "status",
             "records_disposed", "evidence_hash", "legal_hold_reason",
             "created_by", "updated_by"],
            [rid, ref, sched, status, disposed,
             (ev_hash if status == "COMPLETED" else None), hold, ADMIN, ADMIN],
        )

    # ── privacy_notice (5, PDPL Art.12, public-authority channels) ────────────
    # (ref, version, channel, title_en, title_ar, status, effective, body_en, body_ar)
    body_en = (
        "This notice explains how the General Authority, as data controller, "
        "processes your personal data to deliver its public services. Processing "
        "is based on the performance of a public task and on your consent where "
        "applicable, in accordance with the Personal Data Protection Law (PDPL). "
        "You have the rights of access, rectification, erasure and objection. "
        "Contact the Data Protection Officer for enquiries; complaints may be "
        "raised with SDAIA.")
    body_ar = (
        "يوضّح هذا الإشعار كيفية معالجة الهيئة العامة، بصفتها جهة تحكّم في البيانات، "
        "لبياناتك الشخصية لتقديم خدماتها العامة. تستند المعالجة إلى أداء مهمة عامة "
        "وإلى موافقتك عند الاقتضاء، وفق نظام حماية البيانات الشخصية. ولك حقوق "
        "الاطلاع والتصحيح والمحو والاعتراض. للاستفسار تواصل مع مسؤول حماية البيانات، "
        "ويمكن رفع الشكاوى إلى الهيئة السعودية للبيانات والذكاء الاصطناعي (سدايا).")
    PN = [
        ("NOT-GOV-001", "v1.0", "WEB", "Main Portal Privacy Notice",
         "إشعار الخصوصية للبوابة الرئيسية", "PUBLISHED", "2026-01-01"),
        ("NOT-GOV-002", "v1.0", "MOBILE", "Mobile App Privacy Notice",
         "إشعار الخصوصية لتطبيق الجوال", "PUBLISHED", "2026-01-01"),
        ("NOT-GOV-003", "v1.0", "E_SERVICE_PORTAL", "E-Service Portal Privacy Notice",
         "إشعار الخصوصية لبوابة الخدمات الإلكترونية", "DRAFT", "2026-06-01"),
        ("NOT-GOV-004", "v1.0", "SERVICE_CENTER", "Service Center Privacy Notice",
         "إشعار الخصوصية لمركز الخدمة", "PUBLISHED", "2026-01-01"),
        ("NOT-GOV-005", "v1.0", "CALL_CENTER", "Call Center Privacy Notice",
         "إشعار الخصوصية لمركز الاتصال", "PUBLISHED", "2026-01-01"),
    ]
    notice_ids = {}
    for ref, ver, ch, ten, tar, status, eff in PN:
        nid = upsert(
            cur, "data_officer.privacy_notice", "data_officer.privacy_notice",
            "reference=?", (ref,),
            ["reference", "version_label", "channel", "title_en", "title_ar",
             "body_en", "body_ar", "status", "effective_from",
             "published_at", "legal_review_user_id", "created_by", "updated_by"],
            [ref, ver, ch, ten, tar, body_en, body_ar, status, eff,
             (eff + "T08:00:00" if status == "PUBLISHED" else None),
             COMPLIANCE_U, ADMIN, ADMIN],
        )
        notice_ids[ref] = nid

    # ── consent_record (10 citizen-facing services) ───────────────────────────
    # (ref, purpose, label_en, label_ar, notice_ref, channel, legal_basis,
    #  status, granted, withdrawn)
    CON = [
        ("CON-GOV-001", "SERVICE_DELIVERY", "Beneficiary service registration",
         "التسجيل في خدمات المستفيدين", "NOT-GOV-001", "WEB", "PUBLIC_TASK",
         "GRANTED", "2026-01-20T10:00:00", None),
        ("CON-GOV-002", "NOTIFICATIONS", "SMS/email application-status updates",
         "تحديثات حالة الطلب عبر الرسائل والبريد", "NOT-GOV-001", "WEB", "CONSENT",
         "GRANTED", "2026-01-21T10:00:00", None),
        ("CON-GOV-003", "NOTIFICATIONS", "SMS/email application-status updates",
         "تحديثات حالة الطلب عبر الرسائل والبريد", "NOT-GOV-002", "MOBILE",
         "CONSENT", "WITHDRAWN", "2026-01-22T10:00:00", "2026-03-01T09:00:00"),
        ("CON-GOV-004", "ELIGIBILITY", "Beneficiary eligibility processing",
         "معالجة استحقاق المستفيد", "NOT-GOV-003", "E_SERVICE_PORTAL",
         "LEGAL_OBLIGATION", "GRANTED", "2026-02-01T10:00:00", None),
        ("CON-GOV-005", "SURVEY", "Service feedback & satisfaction survey",
         "استبيان تقييم الخدمة والرضا", "NOT-GOV-001", "WEB", "CONSENT",
         "GRANTED", "2026-02-05T10:00:00", None),
        ("CON-GOV-006", "INTER_AGENCY_SHARING", "Data sharing with a partner authority",
         "تبادل البيانات مع جهة شريكة", "NOT-GOV-003", "E_SERVICE_PORTAL",
         "CONSENT", "GRANTED", "2026-02-10T10:00:00", None),
        ("CON-GOV-007", "INTER_AGENCY_SHARING", "Data sharing with a partner authority",
         "تبادل البيانات مع جهة شريكة", "NOT-GOV-003", "E_SERVICE_PORTAL",
         "CONSENT", "WITHDRAWN", "2026-02-11T10:00:00", "2026-04-02T09:00:00"),
        ("CON-GOV-008", "SERVICE_DELIVERY", "Licensing application processing",
         "معالجة طلب الترخيص", "NOT-GOV-004", "SERVICE_CENTER", "PUBLIC_TASK",
         "GRANTED", "2026-02-15T10:00:00", None),
        ("CON-GOV-009", "NOTIFICATIONS", "Call-center callback consent",
         "موافقة معاودة الاتصال من مركز الاتصال", "NOT-GOV-005", "CALL_CENTER",
         "CONSENT", "GRANTED", "2026-02-20T10:00:00", None),
        ("CON-GOV-010", "ELIGIBILITY", "Beneficiary eligibility re-assessment",
         "إعادة تقييم استحقاق المستفيد", "NOT-GOV-003", "E_SERVICE_PORTAL",
         "LEGAL_OBLIGATION", "GRANTED", "2026-03-01T10:00:00", None),
    ]
    for ref, pc, len_, lar, nref, ch, lb, status, granted, withdrawn in CON:
        nid = notice_ids.get(nref)
        wreason = "Data subject withdrew consent." if withdrawn else None
        upsert(
            cur, "data_officer.consent_record", "data_officer.consent_record",
            "reference=?", (ref,),
            ["reference", "purpose_code", "purpose_label_en", "purpose_label_ar",
             "notice_version_id", "channel", "granted_at", "withdrawn_at",
             "withdrawn_reason", "status", "legal_basis", "created_by", "updated_by"],
            [ref, pc, len_, lar, nid, ch, granted, withdrawn, wreason, status,
             lb, ADMIN, ADMIN],
        )

    # ── dpia (5 public-sector) ────────────────────────────────────────────────
    # (ref, title_en, title_ar, trigger, high_risk, status, owner_u, dpo_u,
    #  desc_en, desc_ar)
    DPIA = [
        ("DPIA-GOV-001", "Unified Beneficiary Eligibility Platform",
         "منصة استحقاق المستفيدين الموحّدة", "LARGE_SCALE", 1, "APPROVED",
         RMANAGER, COMPLIANCE_U,
         "Large-scale processing and profiling of beneficiary data to determine "
         "eligibility across services.",
         "معالجة واسعة النطاق وتحليل بيانات المستفيدين لتحديد الاستحقاق عبر الخدمات."),
        ("DPIA-GOV-002", "Inter-Authority Data Sharing via National Integration",
         "تبادل البيانات بين الجهات عبر التكامل الوطني", "NEW_PROCESSING", 1,
         "APPROVED_WITH_CONDITIONS", RMANAGER, COMPLIANCE_U,
         "Sharing of citizen data with partner authorities through the national "
         "integration channel.",
         "تبادل بيانات المواطنين مع الجهات الشريكة عبر قناة التكامل الوطنية."),
        ("DPIA-GOV-003", "AI Chatbot for Citizen Services",
         "المساعد الذكي لخدمات المستفيدين", "NEW_TECHNOLOGY", 1, "DPO_REVIEW",
         RMANAGER, COMPLIANCE_U,
         "Conversational AI processing citizen enquiries and personal data.",
         "ذكاء اصطناعي حواري يعالج استفسارات المستفيدين وبياناتهم الشخصية."),
        ("DPIA-GOV-004", "Geospatial Inspection Analytics",
         "تحليلات التفتيش الجغرافية المكانية", "AUTOMATED_DECISION", 0,
         "APPROVED", RMANAGER, COMPLIANCE_U,
         "Geospatial analytics to prioritise field inspections, with human "
         "oversight of decisions.",
         "تحليلات جغرافية مكانية لترتيب أولويات التفتيش الميداني مع إشراف بشري على "
         "القرارات."),
        ("DPIA-GOV-005", "Open Data Release of Aggregated Service Statistics",
         "إصدار البيانات المفتوحة للإحصاءات المجمّعة للخدمات", "SCOPE_CHANGE", 0,
         "APPROVED", RMANAGER, COMPLIANCE_U,
         "Public release of aggregated, anonymised service statistics with "
         "re-identification risk assessment.",
         "إصدار علني لإحصاءات الخدمات المجمّعة والمجهّلة مع تقييم مخاطر إعادة تحديد "
         "الهوية."),
    ]
    for ref, ten, tar, trig, hr, status, ou, dpou, den, dar in DPIA:
        opinion_en = ("DPO opinion: residual risk acceptable subject to the "
                      "stated conditions, in line with PDPL and SDAIA guidance."
                      if status in ("APPROVED", "APPROVED_WITH_CONDITIONS") else None)
        opinion_ar = ("رأي مسؤول حماية البيانات: المخاطر المتبقية مقبولة وفق الشروط "
                      "المذكورة، بما يتوافق مع النظام وإرشادات سدايا."
                      if opinion_en else None)
        decided = "2026-03-20T12:00:00" if opinion_en else None
        upsert(
            cur, "data_officer.dpia", "data_officer.dpia",
            "reference=?", (ref,),
            ["reference", "title_en", "title_ar", "processing_description_en",
             "processing_description_ar", "trigger_reason", "is_high_risk",
             "status", "data_owner_user_id", "dpo_user_id", "dpo_opinion_en",
             "dpo_opinion_ar", "dpo_opinion_at", "decided_at", "next_review_due",
             "created_by", "updated_by"],
            [ref, ten, tar, den, dar, trig, hr, status, ou, dpou, opinion_en,
             opinion_ar, decided, decided, "2027-03-31", ADMIN, ADMIN],
        )

    # ── dsr_request (8 citizen DSRs) ──────────────────────────────────────────
    # (ref, type, channel, status, assigned_u, verify_method, summary_en, summary_ar)
    DSR = [
        ("DSR-GOV-001", "ACCESS", "WEB", "FULFILLED", COMPLIANCE_U, "NAFATH",
         "Citizen requests access to their own application and case data.",
         "يطلب المستفيد الاطلاع على بيانات طلبه وقضيته."),
        ("DSR-GOV-002", "RECTIFY", "MOBILE", "IN_PROGRESS", COMPLIANCE_U, "ABSHER",
         "Beneficiary requests correction of their record.",
         "يطلب المستفيد تصحيح بياناته في السجل."),
        ("DSR-GOV-003", "ERASE", "WEB", "REJECTED", COMPLIANCE_U, "NAFATH",
         "Erasure request after service closure — rejected due to NCAR legal "
         "retention obligation.",
         "طلب محو بعد إغلاق الخدمة — رُفض بسبب التزام الاحتفاظ النظامي وفق المركز "
         "الوطني للوثائق."),
        ("DSR-GOV-004", "PORTABILITY", "WEB", "FULFILLED", COMPLIANCE_U, "NAFATH",
         "Citizen requests portability of submitted documents.",
         "يطلب المستفيد نقل الوثائق المقدّمة."),
        ("DSR-GOV-005", "OBJECT", "EMAIL", "IN_PROGRESS", COMPLIANCE_U, "NAFATH",
         "Citizen objects to a profiling-based eligibility decision.",
         "يعترض المستفيد على قرار استحقاق مبني على التحليل."),
        ("DSR-GOV-006", "RESTRICT", "SERVICE_CENTER", "ID_VERIFIED", COMPLIANCE_U,
         "IN_BRANCH_KYC",
         "Citizen requests restriction of processing pending a complaint.",
         "يطلب المستفيد تقييد المعالجة لحين البتّ في شكوى."),
        ("DSR-GOV-007", "ACCESS", "CALL", "RECEIVED", COMPLIANCE_U, None,
         "Beneficiary requests a copy of their inspection-related records.",
         "يطلب المستفيد نسخة من سجلاته المتعلقة بالتفتيش."),
        ("DSR-GOV-008", "ERASE", "SDAIA_PORTAL", "ESCALATED_TO_SDAIA", COMPLIANCE_U,
         "NAFATH",
         "Erasure complaint escalated to SDAIA after the citizen disputed the "
         "retention basis.",
         "تصعيد شكوى محو إلى سدايا بعد اعتراض المستفيد على أساس الاحتفاظ."),
    ]
    dsr_ids = {}
    for ref, rt, ch, status, au, vm, sen, sar in DSR:
        verified = "2026-03-12T10:00:00" if status not in ("RECEIVED",) else None
        fulfilled = "2026-03-20T10:00:00" if status == "FULFILLED" else None
        rej_en = rej_ar = rej_basis = None
        if status == "REJECTED":
            rej_en = "Rejected: data is under a statutory retention obligation."
            rej_ar = "مرفوض: البيانات خاضعة لالتزام احتفاظ نظامي."
            rej_basis = "LEGAL_RETENTION"
        rid = upsert(
            cur, "data_officer.dsr_request", "data_officer.dsr_request",
            "reference=?", (ref,),
            ["reference", "request_type", "channel", "request_summary_en",
             "request_summary_ar", "status", "received_at", "sla_deadline",
             "id_verified_at", "id_verification_method", "fulfilled_at",
             "rejection_reason_en", "rejection_reason_ar", "rejection_basis",
             "assigned_user_id", "created_by", "updated_by"],
            [ref, rt, ch, sen, sar, status, "2026-03-10T09:00:00",
             "2026-04-09T09:00:00", verified, vm, fulfilled, rej_en, rej_ar,
             rej_basis, au, ADMIN, ADMIN],
        )
        dsr_ids[ref] = rid

    # ── ethics_review (4 SDAIA AI principles) ─────────────────────────────────
    # (ref, subject_type, subject_ref, title_en, title_ar, status, dims)
    ETH = [
        ("ETH-GOV-001", "AI_MODEL", "DPIA-GOV-003",
         "Citizen-Services AI Chatbot Ethics Review",
         "مراجعة أخلاقيات المساعد الذكي لخدمات المستفيدين", "APPROVED_WITH_CONDITIONS",
         {"fairness": "medium", "transparency": "high", "accountability": "high",
          "privacy": "high"}),
        ("ETH-GOV-002", "DATA_USE_CASE", "DPIA-GOV-001",
         "Eligibility Auto-Decisioning Bias Review",
         "مراجعة تحيّز القرار الآلي للاستحقاق", "APPROVED_WITH_CONDITIONS",
         {"fairness": "high", "transparency": "medium", "accountability": "high",
          "privacy": "medium"}),
        ("ETH-GOV-003", "DATA_USE_CASE", "DPIA-GOV-004",
         "Geospatial Inspection Analytics Proportionality Review",
         "مراجعة تناسب تحليلات التفتيش الجغرافية", "APPROVED",
         {"fairness": "medium", "transparency": "medium",
          "accountability": "high", "privacy": "low"}),
        ("ETH-GOV-004", "OPEN_DATA_RELEASE", "DPIA-GOV-005",
         "Open-Data Aggregated Statistics Re-identification Review",
         "مراجعة إعادة تحديد الهوية للإحصاءات المفتوحة المجمّعة", "APPROVED",
         {"fairness": "low", "transparency": "high", "accountability": "medium",
          "privacy": "high"}),
    ]
    for ref, st, sref, ten, tar, status, dims in ETH:
        opinion_en = ("Reviewed against the SDAIA AI Ethics Principles; approved "
                      "subject to the stated conditions and periodic review by the "
                      "AI Ethics Committee.")
        opinion_ar = ("تمت المراجعة وفق مبادئ أخلاقيات الذكاء الاصطناعي الصادرة عن "
                      "سدايا؛ واعتُمدت وفق الشروط المذكورة ومراجعة دورية من لجنة "
                      "أخلاقيات الذكاء الاصطناعي.")
        upsert(
            cur, "data_officer.ethics_review", "data_officer.ethics_review",
            "reference=?", (ref,),
            ["reference", "subject_type", "subject_ref", "title_en", "title_ar",
             "summary_en", "summary_ar", "risk_dimensions_json", "opinion_en",
             "opinion_ar", "decided_at", "next_review_due", "status",
             "created_by", "updated_by"],
            [ref, st, sref, ten, tar,
             "AI/data use-case reviewed for ethics, fairness, transparency and "
             "accountability per SDAIA principles.",
             "مراجعة حالة استخدام الذكاء الاصطناعي/البيانات من حيث الأخلاقيات "
             "والعدالة والشفافية والمساءلة وفق مبادئ سدايا.",
             json.dumps(dims, ensure_ascii=False), opinion_en, opinion_ar,
             "2026-03-25T12:00:00", "2027-03-31", status, ADMIN, ADMIN],
        )

    # ── breach (3 public-authority PDPL scenarios) ────────────────────────────
    # (ref, title_en, title_ar, detected, status, btype, cause, severity,
    #  rec, subj, special, desc_en, desc_ar, contain_en, contain_ar, sdaia_dl,
    #  sdaia_notified, closed, closure_en, closure_ar)
    BR = [
        ("BR-GOV-001", "Misdirected correspondence exposing beneficiary PII",
         "مراسلة موجَّهة خطأً تكشف بيانات مستفيد",
         "2026-02-08T11:00:00", "NOTIFIED_SDAIA", "CONFIDENTIALITY",
         "MISCONFIGURATION", "MEDIUM", 1, 1, 0,
         "An official letter containing beneficiary personal data was sent to the "
         "wrong recipient due to an address-merge error.",
         "أُرسل خطاب رسمي يحتوي بيانات مستفيد إلى مستلم خاطئ بسبب خطأ في دمج "
         "العناوين.",
         "Recalled the correspondence, notified the affected subject, and "
         "corrected the address-merge process.",
         "تم استرجاع المراسلة وإشعار صاحب البيانات المتأثر وتصحيح عملية دمج "
         "العناوين.",
         "2026-02-11T11:00:00", "2026-02-10T09:00:00", None, None, None),
        ("BR-GOV-002", "Misconfigured open-data export contained personal fields",
         "تصدير بيانات مفتوحة غير مهيّأ احتوى حقولًا شخصية",
         "2026-03-02T14:00:00", "CLOSED", "CONFIDENTIALITY", "MISCONFIGURATION",
         "HIGH", 4200, 4200, 0,
         "An open-data export to data.gov.sa was found to contain non-aggregated "
         "personal fields; caught in pre-publication review and withdrawn before "
         "public release.",
         "تبيّن أن تصديرًا للبيانات المفتوحة إلى البوابة الوطنية احتوى حقولًا شخصية "
         "غير مجمّعة؛ اكتُشف في المراجعة قبل النشر وسُحب قبل الإصدار العلني.",
         "Withdrew the dataset, added a pre-publication anonymisation gate, and "
         "re-published an aggregated version.",
         "تم سحب المجموعة وإضافة بوابة تجهيل قبل النشر وإعادة نشر نسخة مجمّعة.",
         "2026-03-05T14:00:00", "2026-03-03T09:00:00", "2026-03-15T16:00:00",
         "Closed: no public exposure occurred; controls strengthened.",
         "أُغلق: لم يحدث كشف علني؛ وعُزّزت الضوابط."),
        ("BR-GOV-003", "Lost field-inspector device with cached citizen records",
         "فقدان جهاز مفتّش ميداني يحتوي سجلات مخزّنة لمستفيدين",
         "2026-04-01T08:00:00", "ASSESSING", "CONFIDENTIALITY", "LOST_DEVICE",
         "MEDIUM", 350, 350, 0,
         "A field inspector's tablet with cached inspection records was lost; "
         "device is encrypted and remotely wiped.",
         "فُقد جهاز لوحي لمفتّش ميداني يحتوي سجلات تفتيش مخزّنة؛ والجهاز مشفّر وتم "
         "مسحه عن بُعد.",
         "Triggered remote wipe, revoked credentials, and initiated impact "
         "assessment for SDAIA notification.",
         "تم تفعيل المسح عن بُعد وإلغاء الصلاحيات وبدء تقييم الأثر تمهيدًا لإشعار "
         "سدايا.",
         "2026-04-04T08:00:00", None, None, None, None),
    ]
    for (ref, ten, tar, det, status, btype, cause, sev, rec, subj, special,
         den, dar, cen, car, dl, notified, closed, clen, clar) in BR:
        upsert(
            cur, "data_officer.breach", "data_officer.breach",
            "reference=?", (ref,),
            ["reference", "title_en", "title_ar", "detected_at", "status",
             "breach_type", "cause", "severity", "affected_record_count",
             "affected_subject_count", "is_special_category", "description_en",
             "description_ar", "containment_actions_en", "containment_actions_ar",
             "sdaia_deadline", "sdaia_notified_at", "closed_at",
             "closure_summary_en", "closure_summary_ar", "created_by", "updated_by"],
            [ref, ten, tar, det, status, btype, cause, sev, rec, subj, special,
             den, dar, cen, car, dl, notified, closed, clen, clar, ADMIN, ADMIN],
        )

    # ── do_event (12 across new DSR/DPIA/breach/consent entities) ─────────────
    # (entity_type, entity_ref_key, event_type, status_after, actor_u, note_en, note_ar, when)
    def did(ref, m):
        return m.get(ref)
    # Resolve breach ids by reference
    def br_id(ref):
        cur.execute("SELECT id FROM data_officer.breach WHERE organisation_id=? AND reference=?",
                    ORG, ref)
        r = cur.fetchone()
        return r[0] if r else None

    EVT = [
        ("DSR", dsr_ids.get("DSR-GOV-001"), "DSR_RECEIVED", "RECEIVED",
         COMPLIANCE_U, "Access request received via portal.",
         "ورد طلب اطلاع عبر البوابة.", "2026-03-10T09:00:00"),
        ("DSR", dsr_ids.get("DSR-GOV-001"), "DSR_ID_VERIFIED", "ID_VERIFIED",
         COMPLIANCE_U, "Identity verified via Nafath.",
         "تم التحقق من الهوية عبر نفاذ.", "2026-03-12T10:00:00"),
        ("DSR", dsr_ids.get("DSR-GOV-001"), "DSR_FULFILLED", "FULFILLED",
         COMPLIANCE_U, "Access package delivered to the citizen.",
         "تم تسليم حزمة الاطلاع للمستفيد.", "2026-03-20T10:00:00"),
        ("DSR", dsr_ids.get("DSR-GOV-003"), "DSR_REJECTED", "REJECTED",
         COMPLIANCE_U, "Erasure rejected — statutory retention (NCAR).",
         "رُفض المحو — احتفاظ نظامي وفق المركز الوطني للوثائق.",
         "2026-03-18T10:00:00"),
        ("DSR", dsr_ids.get("DSR-GOV-008"), "DSR_ESCALATED", "ESCALATED_TO_SDAIA",
         COMPLIANCE_U, "Erasure complaint escalated to SDAIA.",
         "تصعيد شكوى المحو إلى سدايا.", "2026-03-22T10:00:00"),
        ("DPIA", None, "DPIA_SUBMITTED", "DPO_REVIEW", COMPLIANCE_U,
         "AI chatbot DPIA submitted for DPO review.",
         "تقديم تقييم الأثر للمساعد الذكي لمراجعة مسؤول حماية البيانات.",
         "2026-03-05T11:00:00"),
        ("DPIA", None, "DPIA_DPO_OPINION", "APPROVED_WITH_CONDITIONS", COMPLIANCE_U,
         "DPO issued opinion with conditions on inter-authority sharing.",
         "أصدر مسؤول حماية البيانات رأيًا بشروط على التبادل بين الجهات.",
         "2026-03-20T12:00:00"),
        ("DPIA", None, "DPIA_APPROVED", "APPROVED", COMPLIANCE_U,
         "Beneficiary eligibility platform DPIA approved.",
         "اعتماد تقييم الأثر لمنصة استحقاق المستفيدين.", "2026-03-21T12:00:00"),
        ("BREACH", br_id("BR-GOV-002"), "BREACH_DETECTED", "ASSESSING", COMPLIANCE_U,
         "Open-data export found to contain personal fields.",
         "اكتشاف احتواء تصدير البيانات المفتوحة على حقول شخصية.",
         "2026-03-02T14:00:00"),
        ("BREACH", br_id("BR-GOV-002"), "BREACH_CONTAINED", "CLOSED", COMPLIANCE_U,
         "Dataset withdrawn; anonymisation gate added.",
         "سحب المجموعة وإضافة بوابة تجهيل.", "2026-03-15T16:00:00"),
        ("BREACH", br_id("BR-GOV-001"), "BREACH_SDAIA_NOTIFIED", "NOTIFIED_SDAIA",
         COMPLIANCE_U, "SDAIA notified within the 72-hour window per PDPL.",
         "إشعار سدايا خلال 72 ساعة وفق النظام.", "2026-02-10T09:00:00"),
        ("CONSENT", None, "CONSENT_WITHDRAWN", "WITHDRAWN", COMPLIANCE_U,
         "Citizen withdrew consent for inter-authority sharing.",
         "سحب المستفيد موافقته على التبادل بين الجهات.", "2026-04-02T09:00:00"),
    ]
    for etype, eid, evt, st_after, au, nen, nar, when in EVT:
        ent_id = eid if eid is not None else 0  # entity_id is NOT NULL
        # natural key = (entity_type, event_type, occurred_at)
        cur.execute(
            "SELECT id FROM data_officer.do_event WHERE organisation_id=? "
            "AND entity_type=? AND event_type=? AND occurred_at=?",
            ORG, etype, evt, when)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO data_officer.do_event "
            "(organisation_id, entity_type, entity_id, occurred_at, event_type, "
            " actor_user_id, status_after, note_en, note_ar, created_by, updated_by) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            ORG, etype, ent_id, when, evt, au, st_after, nen, nar, ADMIN, ADMIN)
        bump("data_officer.do_event")

    # ── ndr_submission (1, National Data Catalogue submission 2026) ───────────
    upsert(
        cur, "data_officer.ndr_submission", "data_officer.ndr_submission",
        "reference=?", ("NDR-GOV-2026",),
        ["reference", "cycle_year", "title_en", "title_ar", "status",
         "dataset_count", "submitted_at", "submission_ref",
         "response_summary_en", "response_summary_ar", "created_by", "updated_by"],
        ["NDR-GOV-2026", 2026, "National Data Catalogue Submission 2026",
         "تقديم فهرس البيانات الوطني 2026", "INTERNAL_REVIEW", len(CAT),
         "2026-04-15T10:00:00", "NDMO-CAT-2026-AUTH",
         "Submission of the Authority's government data catalog to NDMO/SDAIA, "
         "consistent with the NDI-2026 cycle.",
         "تقديم فهرس بيانات الهيئة الحكومي لمكتب إدارة البيانات الوطنية وسدايا، "
         "بما يتسق مع دورة مؤشر البيانات الوطني 2026.", ADMIN, ADMIN],
    )


# ═════════════════════════════════════════════════════════════════════════════
def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    print("Seeding Saudi-government data-office layer (org 1) into ndi.* + data_officer.*\n")

    cycle_id = seed_ndi(cur)
    print(f"ndi.* seeded against cycle_id={cycle_id} (NDI-2026)")
    seed_data_officer(cur)
    print("data_officer.* government catalog/quality/retention/privacy seeded")

    # Skipped tables (deliberately, per plan)
    for t in ("ndi.governance_domain", "ndi.framework_item",
              "ndi.report_definition", "ndi.settings"):
        _SKIP.append(t)

    total = sum(_INS.values())
    print("\n── Inserted this run ──")
    for k in sorted(_INS):
        print(f"  {k:<38} +{_INS[k]}")
    print(f"  {'TOTAL':<38} +{total}")

    print("\n── Skipped (persona-neutral / global config, reused as-is) ──")
    for t in _SKIP:
        print(f"  {t}")

    if _BLOCK:
        print("\n── Blockers (manual action needed) ──")
        for b in _BLOCK:
            print(f"  {b}")
    else:
        print("\n── Blockers: none ──")

    # ── Verification counts ──────────────────────────────────────────────────
    print("\n── Verification (org 1 row counts) ──")
    for sch, tabs in (
        ("ndi", ["assessment_cycle", "maturity_assessment", "compliance_assessment",
                 "operational_excellence_kpi", "gap", "corrective_action",
                 "evidence_item", "improvement_plan", "improvement_plan_item",
                 "submission_package", "audit_event"]),
        ("data_officer", ["catalog_entry", "catalog_lineage", "quality_rule",
                          "quality_run", "retention_rule", "disposal_job",
                          "privacy_notice", "consent_record", "dpia",
                          "dsr_request", "ethics_review", "breach", "do_event",
                          "ndr_submission"]),
    ):
        for t in tabs:
            cur.execute(f"SELECT COUNT(*) FROM {sch}.{t} WHERE organisation_id=?", ORG)
            print(f"  {sch}.{t:<32} total={cur.fetchone()[0]}")

    conn.close()
    print(f"\nDone. Inserted {total} rows this run.")


if __name__ == "__main__":
    main()
