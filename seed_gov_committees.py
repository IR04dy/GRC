# -*- coding: utf-8 -*-
"""Seed the standard Saudi government governance committees into admin.committees.

Companion to seed_gov_hierarchy.py. Targets the default tenant
(organisation_id = 1) — the only tenant with working logins, so the committees
are immediately visible on the /committees page to the `admin` account.

Two roots, mirroring real Saudi public-sector governance:

    Board of Directors (مجلس الإدارة) — governance line
    ├─ Audit Committee
    │    ├─ Internal Control Committee
    │    └─ Integrity & Anti-Fraud Committee
    ├─ Risk Management Committee
    ├─ Nomination & Remuneration Committee
    ├─ Compliance Committee
    ├─ Sustainability Committee
    └─ Cybersecurity Supervisory Committee   (NCA ECC 1-1)
    Executive Committee (اللجنة التنفيذية) — management line
    ├─ IT Steering Committee                 (DGA)
    ├─ Data Governance Committee             (NDMO / SDAIA)
    │    ├─ Personal Data Protection Committee (PDPL)
    │    └─ AI Ethics Committee               (SDAIA)
    ├─ Crisis Management Committee           (NCEMA / ISO 22301)
    └─ BCM Steering Committee

Schema note: admin.committees has a CHECK constraint `CK_com_type` that — on this
restored DB — still allows only the original 8 base committee_type values, even
though the platform's own template catalog and the frontend `ExtendedCommitteeType`
union define 22. This script widens `CK_com_type` to match `ExtendedCommitteeType`
(an additive, reversible change) so authentic types (CYBER_SECURITY, DATA_GOVERNANCE,
PRIVACY_DPO, …) can be stored. The committees list page renders any type as
`type.replace('_',' ')`, so no frontend change is needed for display.

Idempotent: committees are upserted by (organisation_id, code). Existing committees
(Board, Audit, Risk, Compliance, BCM) are enriched in place — reporting lines and
regulator anchors are (re)set, descriptive fields are filled only when NULL
(COALESCE), so their existing rich members/charters are never clobbered. The
dissolved legacy "Technology Steering Committee" is left untouched.

    Usage:  python seed_gov_committees.py
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
REVIEW = "2027-03-31"  # annual charter-review due date (fixed for determinism)

# Mirror frontend ExtendedCommitteeType — the platform's intended universe.
ALLOWED_TYPES = [
    "BOARD", "EXEC_COMMITTEE", "RISK_COMMITTEE", "AUDIT_COMMITTEE",
    "BCM_COMMITTEE", "COMPLIANCE_COMMITTEE", "TECH_COMMITTEE",
    "NOMINATION_REMUNERATION", "CYBER_SECURITY", "DATA_GOVERNANCE",
    "PRIVACY_DPO", "CRISIS_MANAGEMENT", "AML", "IT_STEERING",
    "ALCO", "INVESTMENT", "AI_ETHICS", "ANTI_FRAUD",
    "INTERNAL_CONTROL", "ESG_SUSTAINABILITY", "ARCH_REVIEW", "OTHER",
]


def members(*entries: tuple[str, str, bool]) -> str:
    """Build the members_json shape the UI expects: [{name, role, votingMember}]."""
    return json.dumps(
        [{"name": n, "role": r, "votingMember": v} for n, r, v in entries],
        ensure_ascii=False,
    )


# Each committee. Ordered top-down so reports_to / parent codes always resolve.
# fields: code, type, name_en, name_ar, reports_to, parent, cadence, quorum,
#         chair_name, chair_role, secretary, members_json, desc_en, desc_ar,
#         charter_en, charter_ar, anchors, template, formation
C: list[dict] = [
    # ── Board (governance root) ──────────────────────────────────────────────
    dict(code="COM-BOARD", type="BOARD",
         name_en="Board of Directors", name_ar="مجلس إدارة الهيئة",
         reports_to=None, parent=None, cadence="QUARTERLY", quorum=75,
         chair_name="Chairman of the Board", chair_role="Chairman",
         secretary="Board & Committees Secretariat",
         members=members(("Chairman of the Board", "Chairman", True),
                         ("Vice-Chairman", "Vice-Chair", True),
                         ("Board Member", "Member", True)),
         desc_en="Supreme governing body of the Authority; approves strategy, budget and policies.",
         desc_ar="السلطة الإشرافية العليا للهيئة؛ تعتمد الاستراتيجية والميزانية والسياسات.",
         charter_en="Provides strategic direction and oversight; approves the Authority's strategy, annual budget, governance framework and major decisions.",
         charter_ar="يوجه ويشرف استراتيجياً؛ يعتمد استراتيجية الهيئة والميزانية السنوية وإطار الحوكمة والقرارات الكبرى.",
         anchors="Vision2030", template="CTPL-BOARD", formation="BR-2023-01"),

    # ── Board sub-committees ─────────────────────────────────────────────────
    dict(code="COM-AUDIT", type="AUDIT_COMMITTEE",
         name_en="Audit Committee", name_ar="لجنة المراجعة",
         reports_to="COM-BOARD", parent="COM-BOARD", cadence="QUARTERLY", quorum=67,
         chair_name="Independent Board Member", chair_role="Chair (Independent)",
         secretary="Internal Audit Office",
         members=members(("Independent Board Member", "Chair (Independent)", True),
                         ("Independent Member", "Member", True),
                         ("Head of Internal Audit", "Member", True)),
         desc_en="Oversees internal/external audit, financial reporting and the control environment.",
         desc_ar="تشرف على المراجعة الداخلية والخارجية والتقارير المالية وبيئة الرقابة.",
         charter_en="Oversees the integrity of financial reporting, internal and external audit, and the effectiveness of internal control.",
         charter_ar="تشرف على نزاهة التقارير المالية والمراجعة الداخلية والخارجية وفاعلية الرقابة الداخلية.",
         anchors="GCA,IIA", template="CTPL-AUDIT", formation="BR-2023-02"),
    dict(code="COM-RISK", type="RISK_COMMITTEE",
         name_en="Risk Management Committee", name_ar="لجنة إدارة المخاطر",
         reports_to="COM-BOARD", parent="COM-BOARD", cadence="QUARTERLY", quorum=67,
         chair_name="Board Member", chair_role="Chair",
         secretary="Enterprise Risk Management",
         members=members(("Board Member", "Chair", True),
                         ("Director of Enterprise Risk Management", "Member", True),
                         ("Director of Finance", "Member", True),
                         ("Chief Information Security Officer", "Member", True)),
         desc_en="Oversees the enterprise risk framework, appetite and the corporate risk register.",
         desc_ar="تشرف على إطار إدارة المخاطر المؤسسية وحدود تقبّل المخاطر وسجل المخاطر المؤسسي.",
         charter_en="Reviews the enterprise risk management framework, risk appetite, KRIs and the corporate risk register.",
         charter_ar="تراجع إطار إدارة المخاطر المؤسسية وحدود تقبّل المخاطر ومؤشرات المخاطر الرئيسية وسجل المخاطر.",
         anchors="ISO31000,COSO", template="CTPL-RISK", formation="BR-2023-03"),
    dict(code="COM-NOMREM", type="NOMINATION_REMUNERATION",
         name_en="Nomination & Remuneration Committee", name_ar="لجنة الترشيحات والمكافآت",
         reports_to="COM-BOARD", parent="COM-BOARD", cadence="QUARTERLY", quorum=60,
         chair_name="Independent Board Member", chair_role="Chair (Independent)",
         secretary="Human Resources",
         members=members(("Independent Board Member", "Chair (Independent)", True),
                         ("Board Member", "Member", True),
                         ("Director of Human Resources", "Member", True),
                         ("Director of Governance & Policy", "Member", False)),
         desc_en="Oversees board/executive nominations, succession and the remuneration framework.",
         desc_ar="تشرف على ترشيحات المجلس والتنفيذيين والتعاقب الوظيفي وإطار المكافآت.",
         charter_en="Recommends nominations and succession plans for the board and executives, and the remuneration policy.",
         charter_ar="توصي بالترشيحات وخطط التعاقب لأعضاء المجلس والتنفيذيين وسياسة المكافآت.",
         anchors="HRSD", template="CTPL-NOMREM", formation="BR-2023-04"),
    dict(code="COM-COMPLIANCE", type="COMPLIANCE_COMMITTEE",
         name_en="Compliance Committee", name_ar="لجنة الالتزام",
         reports_to="COM-BOARD", parent="COM-BOARD", cadence="QUARTERLY", quorum=60,
         chair_name="Board Member", chair_role="Chair",
         secretary="Compliance",
         members=members(("Board Member", "Chair", True),
                         ("Director of Compliance", "Member", True),
                         ("Director of Legal Affairs", "Member", True),
                         ("Data Protection Officer (DPO)", "Member", True)),
         desc_en="Oversees regulatory compliance, obligations tracking and attestations.",
         desc_ar="تشرف على الالتزام التنظيمي ومتابعة الالتزامات والإقرارات.",
         charter_en="Monitors compliance with laws and regulations, the obligations register and regulatory correspondence.",
         charter_ar="تراقب الالتزام بالأنظمة واللوائح وسجل الالتزامات والمراسلات التنظيمية.",
         anchors="PDPL,NCA", template="CTPL-COMPLIANCE", formation="BR-2023-05"),
    dict(code="COM-ESG", type="ESG_SUSTAINABILITY",
         name_en="Sustainability Committee", name_ar="لجنة الاستدامة",
         reports_to="COM-BOARD", parent="COM-BOARD", cadence="QUARTERLY", quorum=60,
         chair_name="Board Member", chair_role="Chair",
         secretary="Strategy & Performance",
         members=members(("Board Member", "Chair", True),
                         ("Director of Strategy & Performance", "Member", True),
                         ("Director of Corporate Communication", "Member", True),
                         ("Director of General Services & Facilities", "Member", True)),
         desc_en="Oversees sustainability, ESG disclosure and Vision 2030 alignment.",
         desc_ar="تشرف على الاستدامة والإفصاح البيئي والاجتماعي والحوكمة ومواءمة رؤية 2030.",
         charter_en="Guides the sustainability strategy, ESG targets and disclosure aligned with Vision 2030.",
         charter_ar="توجه استراتيجية الاستدامة وأهداف الحوكمة البيئية والاجتماعية والإفصاح بما يتوافق مع رؤية 2030.",
         anchors="Vision2030,TCFD", template="CTPL-ESG", formation="BR-2024-09"),
    dict(code="COM-CYBER", type="CYBER_SECURITY",
         name_en="Cybersecurity Supervisory Committee", name_ar="اللجنة الإشرافية للأمن السيبراني",
         reports_to="COM-BOARD", parent="COM-BOARD", cadence="QUARTERLY", quorum=60,
         chair_name="Deputy for Digital Transformation & Data", chair_role="Chair",
         secretary="Cyber Risk & Compliance",
         members=members(("Deputy for Digital Transformation & Data", "Chair", True),
                         ("Chief Information Security Officer", "Member", True),
                         ("Director, Security Operations Center", "Member", True),
                         ("Director of Information Technology", "Member", True),
                         ("Head of Internal Audit", "Observer", False)),
         desc_en="Board-level oversight of the cybersecurity programme per NCA ECC.",
         desc_ar="إشراف على مستوى المجلس على برنامج الأمن السيبراني وفق الضوابط الأساسية للهيئة الوطنية للأمن السيبراني.",
         charter_en="Supervises the cybersecurity strategy, ECC compliance, risk and major incidents, per NCA ECC control 1-1.",
         charter_ar="تشرف على استراتيجية الأمن السيبراني والالتزام بالضوابط الأساسية والمخاطر والحوادث الكبرى وفق الضابط 1-1.",
         anchors="NCA,ECC", template="CTPL-CYBER", formation="BR-2024-03"),

    # ── Audit sub-committees ─────────────────────────────────────────────────
    dict(code="COM-INTCTRL", type="INTERNAL_CONTROL",
         name_en="Internal Control Committee", name_ar="لجنة الرقابة الداخلية",
         reports_to="COM-AUDIT", parent="COM-AUDIT", cadence="QUARTERLY", quorum=60,
         chair_name="Head of Internal Audit", chair_role="Chair",
         secretary="Internal Audit Office",
         members=members(("Head of Internal Audit", "Chair", True),
                         ("Director of Finance", "Member", True),
                         ("Director of Compliance", "Member", True),
                         ("Director of Governance & Policy", "Member", True)),
         desc_en="Monitors the internal control framework and remediation of control gaps.",
         desc_ar="تراقب إطار الرقابة الداخلية ومعالجة الثغرات الرقابية.",
         charter_en="Assesses the design and operating effectiveness of internal controls and tracks remediation.",
         charter_ar="تقيّم تصميم وفاعلية الرقابة الداخلية وتتابع خطط المعالجة.",
         anchors="GCA,COSO", template="CTPL-INTCTRL", formation="BR-2024-06"),
    dict(code="COM-ANTIFRAUD", type="ANTI_FRAUD",
         name_en="Integrity & Anti-Fraud Committee", name_ar="لجنة النزاهة ومكافحة الاحتيال",
         reports_to="COM-AUDIT", parent="COM-AUDIT", cadence="QUARTERLY", quorum=60,
         chair_name="Head of Internal Audit", chair_role="Chair",
         secretary="Internal Audit Office",
         members=members(("Head of Internal Audit", "Chair", True),
                         ("Director of Legal Affairs", "Member", True),
                         ("Director of Compliance", "Member", True),
                         ("Director of Human Resources", "Member", True)),
         desc_en="Oversees integrity, anti-fraud controls and whistleblowing aligned with Nazaha.",
         desc_ar="تشرف على النزاهة وضوابط مكافحة الاحتيال والإبلاغ عن المخالفات بما يتوافق مع نزاهة.",
         charter_en="Promotes integrity, oversees fraud-risk controls and the whistleblowing channel.",
         charter_ar="تعزز النزاهة وتشرف على ضوابط مخاطر الاحتيال وقناة الإبلاغ عن المخالفات.",
         anchors="Nazaha", template="CTPL-ANTIFRAUD", formation="BR-2024-07"),

    # ── Executive (management root) ──────────────────────────────────────────
    dict(code="COM-EXEC", type="EXEC_COMMITTEE",
         name_en="Executive Committee", name_ar="اللجنة التنفيذية",
         reports_to=None, parent=None, cadence="MONTHLY", quorum=60,
         chair_name="President of the Authority", chair_role="Chair",
         secretary="Board & Committees Secretariat",
         members=members(("President of the Authority", "Chair", True),
                         ("Deputy for Shared Services", "Member", True),
                         ("Deputy for Digital Transformation & Data", "Member", True),
                         ("Deputy for Operations & Services", "Member", True),
                         ("Director of Strategy & Performance", "Member", True)),
         desc_en="Top management committee; drives execution of strategy and operational decisions.",
         desc_ar="لجنة الإدارة العليا؛ تقود تنفيذ الاستراتيجية والقرارات التشغيلية.",
         charter_en="Coordinates execution of the approved strategy, budgets and cross-deputyship operational decisions.",
         charter_ar="تنسق تنفيذ الاستراتيجية المعتمدة والميزانيات والقرارات التشغيلية بين الوكالات.",
         anchors="Vision2030", template=None, formation="PR-2023-10"),

    # ── Management committees (report to Executive) ──────────────────────────
    dict(code="COM-IT", type="IT_STEERING",
         name_en="IT Steering Committee", name_ar="اللجنة الإشرافية لتقنية المعلومات",
         reports_to="COM-EXEC", parent="COM-EXEC", cadence="QUARTERLY", quorum=60,
         chair_name="Deputy for Digital Transformation & Data", chair_role="Chair",
         secretary="Information Technology",
         members=members(("Deputy for Digital Transformation & Data", "Chair", True),
                         ("Director of Information Technology", "Member", True),
                         ("Director of Digital Transformation", "Member", True),
                         ("Chief Information Security Officer", "Member", True),
                         ("Director of Finance", "Member", True)),
         desc_en="Prioritises the IT/digital portfolio and oversees major technology investments.",
         desc_ar="ترتب أولويات محفظة تقنية المعلومات والتحول الرقمي وتشرف على الاستثمارات التقنية الكبرى.",
         charter_en="Approves the IT/digital roadmap, prioritises projects and oversees technology spend (DGA alignment).",
         charter_ar="تعتمد خارطة طريق التقنية والتحول الرقمي وترتب أولويات المشاريع وتشرف على الإنفاق التقني.",
         anchors="DGA", template="CTPL-IT", formation="PR-2023-11"),
    dict(code="COM-DATAGOV", type="DATA_GOVERNANCE",
         name_en="Data Governance Committee", name_ar="لجنة حوكمة البيانات",
         reports_to="COM-EXEC", parent="COM-EXEC", cadence="QUARTERLY", quorum=60,
         chair_name="Deputy for Digital Transformation & Data", chair_role="Chair",
         secretary="Data Management Office",
         members=members(("Deputy for Digital Transformation & Data", "Chair", True),
                         ("Director, Data Management Office", "Member", True),
                         ("Chief Data Officer", "Member", True),
                         ("Director of Privacy Office", "Member", True),
                         ("Director, Data & AI", "Member", True)),
         desc_en="Oversees data management, classification and national data compliance (NDMO/SDAIA).",
         desc_ar="تشرف على إدارة البيانات وتصنيفها والالتزام بضوابط البيانات الوطنية (مكتب إدارة البيانات/سدايا).",
         charter_en="Governs data ownership, classification, quality and compliance with NDMO standards.",
         charter_ar="تحوكم ملكية البيانات وتصنيفها وجودتها والالتزام بمعايير مكتب إدارة البيانات الوطنية.",
         anchors="NDMO,SDAIA", template="CTPL-DATAGOV", formation="PR-2024-02"),
    dict(code="COM-CRISIS", type="CRISIS_MANAGEMENT",
         name_en="Crisis Management Committee", name_ar="لجنة إدارة الأزمات",
         reports_to="COM-EXEC", parent="COM-EXEC", cadence="AD_HOC", quorum=50,
         chair_name="President of the Authority", chair_role="Chair",
         secretary="Business Continuity",
         members=members(("President of the Authority", "Chair", True),
                         ("Deputy for Operations & Services", "Member", True),
                         ("Director of Business Continuity", "Member", True),
                         ("Chief Information Security Officer", "Member", True),
                         ("Director of Corporate Communication", "Member", True)),
         desc_en="Activates and directs the response to major crises and disruptions.",
         desc_ar="تفعّل وتوجه الاستجابة للأزمات والاضطرابات الكبرى.",
         charter_en="Directs crisis response, command and communication for major disruptions (NCEMA / ISO 22301).",
         charter_ar="توجه الاستجابة للأزمات والقيادة والتواصل في الاضطرابات الكبرى وفق المركز الوطني وآيزو 22301.",
         anchors="NCEMA,ISO22301", template="CTPL-CRISIS", formation="PR-2024-04"),
    dict(code="COM-BCM", type="BCM_COMMITTEE",
         name_en="BCM Steering Committee", name_ar="اللجنة الإشرافية لاستمرارية الأعمال",
         reports_to="COM-EXEC", parent="COM-EXEC", cadence="QUARTERLY", quorum=60,
         chair_name="Deputy for Operations & Services", chair_role="Chair",
         secretary="Business Continuity",
         members=members(("Deputy for Operations & Services", "Chair", True),
                         ("Director of Business Continuity", "Member", True),
                         ("Director of Information Technology", "Member", True),
                         ("Director of General Services & Facilities", "Member", True)),
         desc_en="Oversees the business continuity programme, BIA and recovery exercises.",
         desc_ar="تشرف على برنامج استمرارية الأعمال وتحليل الأثر والتمارين والاختبارات.",
         charter_en="Oversees the BCMS: business impact analysis, continuity plans and exercises (ISO 22301).",
         charter_ar="تشرف على نظام إدارة استمرارية الأعمال: تحليل الأثر وخطط الاستمرارية والتمارين وفق آيزو 22301.",
         anchors="ISO22301,NCEMA", template="CTPL-BCM", formation="PR-2023-12"),

    # ── Data Governance sub-committees ───────────────────────────────────────
    dict(code="COM-PRIVACY", type="PRIVACY_DPO",
         name_en="Personal Data Protection Committee", name_ar="لجنة حماية البيانات الشخصية",
         reports_to="COM-DATAGOV", parent="COM-DATAGOV", cadence="QUARTERLY", quorum=60,
         chair_name="Data Protection Officer (DPO)", chair_role="Chair",
         secretary="Privacy Office",
         members=members(("Data Protection Officer (DPO)", "Chair", True),
                         ("Director of Privacy Office", "Member", True),
                         ("Director of Legal Affairs", "Member", True),
                         ("Director, Data Management Office", "Member", True),
                         ("Director of Compliance", "Member", True)),
         desc_en="Oversees PDPL compliance: ROPA, data-subject rights and breach response.",
         desc_ar="تشرف على الالتزام بنظام حماية البيانات الشخصية: سجل المعالجة وحقوق أصحاب البيانات والاستجابة للانتهاكات.",
         charter_en="Oversees personal-data processing, ROPA, DSR handling and breach notification under PDPL.",
         charter_ar="تشرف على معالجة البيانات الشخصية وسجل المعالجة وطلبات أصحاب البيانات والإبلاغ عن الانتهاكات وفق النظام.",
         anchors="PDPL,SDAIA", template="CTPL-PRIVACY", formation="PR-2024-05"),
    dict(code="COM-AIETHICS", type="AI_ETHICS",
         name_en="AI Ethics Committee", name_ar="لجنة أخلاقيات الذكاء الاصطناعي",
         reports_to="COM-DATAGOV", parent="COM-DATAGOV", cadence="QUARTERLY", quorum=50,
         chair_name="Director, Data & AI", chair_role="Chair",
         secretary="Data & AI",
         members=members(("Director, Data & AI", "Chair", True),
                         ("Director, Data Management Office", "Member", True),
                         ("Director of Legal Affairs", "Member", True),
                         ("Director of Compliance", "Member", True),
                         ("Data Protection Officer (DPO)", "Member", True)),
         desc_en="Reviews AI use-cases for ethics, fairness and SDAIA AI principles.",
         desc_ar="تراجع حالات استخدام الذكاء الاصطناعي من حيث الأخلاقيات والعدالة ومبادئ سدايا.",
         charter_en="Reviews AI/ML use-cases for ethics, bias, transparency and accountability (SDAIA AI principles).",
         charter_ar="تراجع حالات استخدام الذكاء الاصطناعي من حيث الأخلاقيات والتحيز والشفافية والمساءلة وفق مبادئ سدايا.",
         anchors="SDAIA", template="CTPL-AIETHICS", formation="PR-2024-08"),
]


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    # ── 1. Widen CK_com_type to the platform's full ExtendedCommitteeType set ──
    cur.execute(
        "SELECT 1 FROM sys.check_constraints WHERE name='CK_com_type' "
        "AND parent_object_id=OBJECT_ID('admin.committees')"
    )
    if cur.fetchone():
        cur.execute("ALTER TABLE admin.committees DROP CONSTRAINT CK_com_type")
    allowed = ", ".join(f"N'{t}'" for t in ALLOWED_TYPES)
    cur.execute(
        f"ALTER TABLE admin.committees ADD CONSTRAINT CK_com_type "
        f"CHECK (committee_type IN ({allowed}))"
    )
    print(f"CK_com_type widened to {len(ALLOWED_TYPES)} committee types.\n")

    def com_id(code: str | None):
        if not code:
            return None
        cur.execute(
            "SELECT id FROM admin.committees WHERE organisation_id=? AND code=?",
            ORG, code,
        )
        row = cur.fetchone()
        return row[0] if row else None

    inserted = updated = 0
    for c in C:
        rid = com_id(c["reports_to"])
        pid = com_id(c["parent"])
        existing = com_id(c["code"])
        if existing is not None:
            # Enrich in place: (re)set reporting lines + anchors; fill descriptive
            # fields only when NULL so existing rich data is never clobbered.
            cur.execute(
                """UPDATE admin.committees
                      SET reports_to_committee_id = ?,
                          parent_committee_id     = ?,
                          regulatory_domain       = 'PUBLIC',
                          regulator_anchors       = COALESCE(regulator_anchors, ?),
                          charter_template_code   = COALESCE(charter_template_code, ?),
                          formation_resolution_code = COALESCE(formation_resolution_code, ?),
                          next_charter_review_at  = COALESCE(next_charter_review_at, ?),
                          description_en          = COALESCE(description_en, ?),
                          description_ar          = COALESCE(description_ar, ?),
                          charter_en              = COALESCE(charter_en, ?),
                          charter_ar              = COALESCE(charter_ar, ?),
                          chairperson_name        = COALESCE(chairperson_name, ?),
                          chairperson_role        = COALESCE(chairperson_role, ?),
                          secretary_name          = COALESCE(secretary_name, ?),
                          members_json            = COALESCE(members_json, ?),
                          name_ar                 = COALESCE(name_ar, ?),
                          is_active = 1, status = 'ACTIVE',
                          updated_at = SYSUTCDATETIME(), updated_by = 1
                    WHERE id = ?""",
                rid, pid, c["anchors"], c["template"], c["formation"], REVIEW,
                c["desc_en"], c["desc_ar"], c["charter_en"], c["charter_ar"],
                c["chair_name"], c["chair_role"], c["secretary"], c["members"],
                c["name_ar"], existing,
            )
            updated += 1
            print(f"  ~ {c['code']:<15} {c['name_en']}")
        else:
            cur.execute(
                """INSERT INTO admin.committees
                       (organisation_id, code, name_en, name_ar, committee_type,
                        description_en, description_ar, chairperson_name, chairperson_role,
                        secretary_name, members_json, charter_en, charter_ar,
                        meeting_cadence, quorum_pct, status, external_id,
                        reports_to_committee_id, parent_committee_id, regulatory_domain,
                        regulator_anchors, next_charter_review_at, formation_resolution_code,
                        charter_template_code, is_active, is_deleted, created_by, updated_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?,
                           ?, ?, 'PUBLIC', ?, ?, ?, ?, 1, 0, 1, 1)""",
                ORG, c["code"], c["name_en"], c["name_ar"], c["type"],
                c["desc_en"], c["desc_ar"], c["chair_name"], c["chair_role"],
                c["secretary"], c["members"], c["charter_en"], c["charter_ar"],
                c["cadence"], c["quorum"], "EXT-" + c["code"],
                rid, pid, c["anchors"], REVIEW, c["formation"], c["template"],
            )
            inserted += 1
            print(f"  + {c['code']:<15} {c['name_en']}")

    print(f"\nDone. inserted={inserted}, updated={updated}, total={len(C)}")

    # ── Verification: print the committee governance tree ────────────────────
    print("\nGovernance committees (organisation_id = 1):\n")
    cur.execute(
        """WITH tree AS (
               SELECT id, reports_to_committee_id AS rt, code, name_en, name_ar,
                      committee_type, status, 0 AS lvl,
                      CAST(RIGHT('0000'+CAST(id AS varchar(10)),10) AS varchar(900)) AS path
                 FROM admin.committees
                WHERE organisation_id=? AND is_deleted=0 AND reports_to_committee_id IS NULL
               UNION ALL
               SELECT c.id, c.reports_to_committee_id, c.code, c.name_en, c.name_ar,
                      c.committee_type, c.status, t.lvl+1,
                      CAST(t.path+'/'+RIGHT('0000'+CAST(c.id AS varchar(10)),10) AS varchar(900))
                 FROM admin.committees c JOIN tree t ON c.reports_to_committee_id=t.id
                WHERE c.organisation_id=? AND c.is_deleted=0
           )
           SELECT lvl, name_en, name_ar, committee_type, status FROM tree ORDER BY path""",
        ORG, ORG,
    )
    for lvl, name_en, name_ar, ctype, status in cur.fetchall():
        tag = "" if status == "ACTIVE" else f"  ({status})"
        print(f"{'    '*lvl}{name_en}  —  {name_ar}  [{ctype}]{tag}")

    cur.execute(
        "SELECT COUNT(*) FROM admin.committees WHERE organisation_id=? AND is_deleted=0 AND status='ACTIVE'",
        ORG,
    )
    active = cur.fetchone()[0]
    cur.execute(
        "SELECT COUNT(*) FROM admin.committees WHERE organisation_id=? AND is_deleted=0",
        ORG,
    )
    total = cur.fetchone()[0]
    print(f"\nLive committees for org {ORG}: {active} active / {total} total")
    conn.close()


if __name__ == "__main__":
    main()
