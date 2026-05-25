"""Seed Saudi-realistic Outsourcing & Cloud Governance data — Sprint 30 PR #4.

Pairs with PRs #265 (backend), #266 (F8 rules AR-261..AR-270).

Idempotent — every INSERT guarded by (organisation_id, reference) check.

Tenant coverage:
  org_id=1  TSS GRC bank — full outsourcing book (8 arrangements + 7 cloud)
  org_id=2  Aramco Trading — energy/trading flavour (4 arrangements + 3 cloud)
  org_id=3  STC Bank — telco-banking (3 arrangements + 2 cloud)

Primed events:
  * One MATERIAL BOARD_APPROVED arrangement at T-10 days (AR-262 ladder)
  * One core-activity LIVE arrangement (AR-263 evidence)
  * One T3+GLOBAL Salesforce — auto-BLOCKED on residency (AR-265)
  * One review overdue by 12 days (AR-269 ladder)
  * One UK-jurisdiction provider (AR-270 PDPL Art.7)

Usage:  python setup_outsourcing_seed.py
"""
from __future__ import annotations

import sys
from datetime import date, datetime, timedelta

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

conn = pyodbc.connect(
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;"
)
conn.autocommit = False
cur = conn.cursor()
NOW = datetime.now()
TODAY = date.today()


def arr_exists(org, ref):
    cur.execute(
        "SELECT id FROM outsourcing.outsourcing_arrangement "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def ins_arr(org, ref, *, prov_en, prov_ar, country, jur, title_en, title_ar,
            desc_en, desc_ar, cat, materiality, core, status,
            risk_rating=None, risk_at=None, board_at=None, sama_at=None,
            sama_ref=None, live_at=None, exited_at=None,
            cstart=None, cend=None, notice_days=None, spend=None,
            next_review=None, cadence=None):
    existing = arr_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO outsourcing.outsourcing_arrangement "
        "(organisation_id, reference, provider_legal_name_en, provider_legal_name_ar, "
        " provider_country, provider_jurisdiction, service_title_en, service_title_ar, "
        " service_description_en, service_description_ar, service_category, materiality, "
        " is_core_activity, status, risk_rating, risk_assessed_at, board_approved_at, "
        " sama_notified_at, sama_non_objection_ref, live_at, exited_at, "
        " contract_start, contract_end, renewal_notice_days, annual_spend_sar, "
        " next_review_due, review_cadence_months) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, prov_en, prov_ar, country, jur, title_en, title_ar,
        desc_en, desc_ar, cat, materiality, 1 if core else 0, status,
        risk_rating, risk_at, board_at, sama_at, sama_ref, live_at, exited_at,
        cstart, cend, notice_days, spend, next_review, cadence,
    )
    return int(cur.fetchone()[0])


def cloud_exists(org, ref):
    cur.execute(
        "SELECT id FROM outsourcing.cloud_service "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def ins_cloud(org, arr_id, ref, *, name_en, name_ar, deploy, smodel,
              csp_en, csp_ar, brand, classif, residency, region,
              status, classified_at=None, approved_at=None,
              in_use_at=None, block_en=None, block_ar=None,
              approver=None, dpia_ref=None):
    existing = cloud_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO outsourcing.cloud_service "
        "(organisation_id, arrangement_id, reference, service_name_en, service_name_ar, "
        " deployment_model, service_model, csp_legal_name_en, csp_legal_name_ar, csp_brand, "
        " data_classification, data_residency, csp_region_label, status, "
        " classified_at, approved_at, in_use_at, block_reason_en, block_reason_ar, "
        " approver_role, supports_pdpl_dpia_ref) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, arr_id, ref, name_en, name_ar, deploy, smodel,
        csp_en, csp_ar, brand, classif, residency, region, status,
        classified_at, approved_at, in_use_at, block_en, block_ar, approver, dpia_ref,
    )
    return int(cur.fetchone()[0])


def rev_exists(org, ref):
    cur.execute(
        "SELECT id FROM outsourcing.ongoing_review "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def ins_rev(org, arr_id, ref, *, period, status, due, started=None,
            completed=None, rag=None, findings_en=None, findings_ar=None):
    existing = rev_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO outsourcing.ongoing_review "
        "(organisation_id, arrangement_id, reference, review_period_label, "
        " status, due_date, started_at, completed_at, outcome_rag, "
        " findings_summary_en, findings_summary_ar) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        org, arr_id, ref, period, status, due, started, completed,
        rag, findings_en, findings_ar,
    )
    return int(cur.fetchone()[0])


def exit_exists(org, ref):
    cur.execute(
        "SELECT id FROM outsourcing.exit_plan "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def ins_exit(org, arr_id, ref, *, status, summary_en, summary_ar,
             days, alt_en, alt_ar, approved=None, rehearsed=None):
    existing = exit_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO outsourcing.exit_plan "
        "(organisation_id, arrangement_id, reference, status, summary_en, summary_ar, "
        " target_migration_days, alternative_provider_en, alternative_provider_ar, "
        " approved_at, rehearsed_at) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        org, arr_id, ref, status, summary_en, summary_ar, days, alt_en, alt_ar,
        approved, rehearsed,
    )
    return int(cur.fetchone()[0])


# ──────────────────────────────────────────────────────── ORG 1 — TSS GRC ──

def seed_org1():
    print(">>> org=1 TSS GRC bank — full outsourcing book")
    a = {}

    # 1. Core-banking maintenance — TCS (India) — MATERIAL + CORE + LIVE
    a['tcs'] = ins_arr(
        1, "OUT-2025-001",
        prov_en="Tata Consultancy Services Saudi Arabia LLC",
        prov_ar="شركة تاتا للاستشارات الخدمية السعودية المحدودة",
        country="SA", jur="SAUDI",
        title_en="Core-banking platform maintenance & L3 support — T24",
        title_ar="صيانة منصّة النواة المصرفية ودعم المستوى الثالث — تي ٢٤",
        desc_en=("24×7 maintenance + L3 vendor escalation for the bank's "
                  "Temenos T24 core platform under SAMA's core-activity "
                  "non-objection letter SAMA/COR/2024/0211."),
        desc_ar=("صيانة على مدار الساعة ودعم المستوى الثالث لمنصّة تيمينوس "
                  "تي ٢٤ النواة المصرفية بموجب خطاب عدم الممانعة "
                  "SAMA/COR/2024/0211."),
        cat="CORE_BANKING", materiality="MATERIAL", core=True, status="LIVE",
        risk_rating="HIGH",
        risk_at=NOW - timedelta(days=240), board_at=NOW - timedelta(days=220),
        sama_at=NOW - timedelta(days=200), sama_ref="SAMA/COR/2024/0211",
        live_at=TODAY - timedelta(days=180),
        cstart=TODAY - timedelta(days=185),
        cend=TODAY + timedelta(days=545),
        notice_days=180, spend=4_800_000,
        next_review=TODAY + timedelta(days=90), cadence=12,
    )

    # 2. AWS Bahrain region — IaaS for analytics — MATERIAL, GCC jurisdiction
    a['aws'] = ins_arr(
        1, "OUT-2025-007",
        prov_en="Amazon Web Services EMEA SARL",
        prov_ar="أمازون ويب سيرفيسز EMEA",
        country="BH", jur="GCC",
        title_en="AWS me-south-1 (Bahrain) IaaS — analytics + dev/test",
        title_ar="بنية تحتية كخدمة AWS me-south-1 (البحرين) — التحليلات والتطوير",
        desc_en=("Analytics workloads (non-customer-identifying), dev/test "
                  "environments, and CI build agents hosted in AWS Bahrain "
                  "region me-south-1."),
        desc_ar=("أحمال تحليلية (دون معرّفات عملاء)، وبيئات تطوير واختبار، "
                  "ووكلاء بناء التكامل المستمر مستضافون في منطقة AWS "
                  "البحرين me-south-1."),
        cat="CLOUD", materiality="MATERIAL", core=False, status="LIVE",
        risk_rating="MEDIUM",
        risk_at=NOW - timedelta(days=160), board_at=NOW - timedelta(days=140),
        sama_at=NOW - timedelta(days=130), sama_ref="SAMA/CLOUD/2025/0044",
        live_at=TODAY - timedelta(days=120),
        cstart=TODAY - timedelta(days=125), cend=TODAY + timedelta(days=605),
        notice_days=90, spend=2_400_000,
        next_review=TODAY + timedelta(days=240), cadence=12,
    )

    # 3. STC Cloud Riyadh — IaaS for production-T3 — MATERIAL, IN_KINGDOM
    a['stc'] = ins_arr(
        1, "OUT-2025-012",
        prov_en="Saudi Telecom Company — STC Cloud",
        prov_ar="شركة الاتصالات السعودية — سحابة STC",
        country="SA", jur="SAUDI",
        title_en="STC Cloud Riyadh IaaS — customer-facing production (T3)",
        title_ar="سحابة STC الرياض IaaS — إنتاج خاضع للعملاء (الفئة ٣)",
        desc_en=("Production hosting for customer-facing digital channels "
                  "(mobile banking + internet banking). Tier-3 confidential "
                  "data residency satisfied in-Kingdom (Riyadh) under SAMA "
                  "Cloud Framework §5."),
        desc_ar=("استضافة الإنتاج للقنوات الرقمية الموجّهة للعملاء (المصرفية "
                  "الهاتفية والإنترنتية). يلبّي مقرّ بيانات الفئة الثالثة "
                  "السرّية داخل المملكة (الرياض) وفق §٥ من إطار ساما "
                  "للحوسبة السحابية."),
        cat="CLOUD", materiality="MATERIAL", core=False, status="LIVE",
        risk_rating="MEDIUM",
        risk_at=NOW - timedelta(days=140), board_at=NOW - timedelta(days=120),
        sama_at=NOW - timedelta(days=110), sama_ref="SAMA/CLOUD/2025/0061",
        live_at=TODAY - timedelta(days=95),
        cstart=TODAY - timedelta(days=100), cend=TODAY + timedelta(days=630),
        notice_days=90, spend=3_600_000,
        next_review=TODAY + timedelta(days=150), cadence=12,
    )

    # 4. Splunk Cloud — SaaS SIEM — MATERIAL, but T2 — GCC ok
    a['splunk'] = ins_arr(
        1, "OUT-2025-018",
        prov_en="Splunk Services UK Ltd (via STC Solutions reseller)",
        prov_ar="سبلانك خدمات المملكة المتحدة (عبر موزع STC Solutions)",
        country="GB", jur="GLOBAL",  # provider HQ outside KSA → primes AR-270 review
        title_en="Splunk Cloud SIEM — log ingest & detection rules",
        title_ar="سبلانك سحابي SIEM — تجميع السجلات وقواعد الاكتشاف",
        desc_en=("SOC-tier security analytics: ingest, correlation, alerting. "
                  "Resold via STC Solutions; data plane hosted on AWS Bahrain. "
                  "Cross-border processing engagement requires PDPL Art.7 "
                  "review per AR-270."),
        desc_ar=("تحليلات أمنية على مستوى مركز العمليات: تجميع وارتباط "
                  "وتنبيهات. يُوزَّع عبر STC Solutions وتُستضاف طبقة "
                  "البيانات على AWS البحرين. ارتباط المعالجة عبر الحدود يستلزم "
                  "مراجعة المادة ٧ من نظام حماية البيانات وفق AR-270."),
        cat="CLOUD", materiality="MATERIAL", core=False, status="LIVE",
        risk_rating="MEDIUM",
        risk_at=NOW - timedelta(days=110), board_at=NOW - timedelta(days=90),
        sama_at=NOW - timedelta(days=80), sama_ref="SAMA/CLOUD/2025/0073",
        live_at=TODAY - timedelta(days=70),
        cstart=TODAY - timedelta(days=75), cend=TODAY + timedelta(days=290),
        notice_days=60, spend=850_000,
        next_review=TODAY + timedelta(days=295), cadence=12,
    )

    # 5. Oracle Exadata managed — MATERIAL, core, but in BOARD_APPROVED — primes AR-262
    a['oracle'] = ins_arr(
        1, "OUT-2026-002",
        prov_en="Oracle Saudi Arabia Ltd",
        prov_ar="أوراكل المملكة العربية السعودية المحدودة",
        country="SA", jur="SAUDI",
        title_en="Oracle Exadata managed services — DB platform support",
        title_ar="خدمات إدارة أوراكل Exadata — دعم منصّة قواعد البيانات",
        desc_en=("Managed support for the Oracle Exadata cluster underpinning "
                  "the data warehouse and risk-engine ETL. Awaiting SAMA "
                  "60-day notification window per §4 — Board approved "
                  "T-10 days."),
        desc_ar=("دعم مُدار لعنقود أوراكل Exadata الذي يقوم عليه مستودع "
                  "البيانات ومحرّك المخاطر. بانتظار نافذة إخطار ساما ٦٠ "
                  "يوماً وفق §٤ — اعتمد المجلس قبل ١٠ أيام."),
        cat="IT_INFRASTRUCTURE", materiality="MATERIAL", core=False,
        status="BOARD_APPROVED", risk_rating="HIGH",
        risk_at=NOW - timedelta(days=30), board_at=NOW - timedelta(days=10),
        cstart=TODAY + timedelta(days=20), cend=TODAY + timedelta(days=750),
        notice_days=120, spend=1_900_000, cadence=12,
    )

    # 6. Salesforce Marketing Cloud — proposed T3 but on US region — primes residency violation
    a['sf'] = ins_arr(
        1, "OUT-2026-005",
        prov_en="Salesforce.com EMEA Ltd",
        prov_ar="سيلز فورس EMEA المحدودة",
        country="IE", jur="GLOBAL",
        title_en="Salesforce Marketing Cloud — customer engagement (PROPOSED)",
        title_ar="سيلز فورس للتسويق السحابي — تفاعل العملاء (مقترح)",
        desc_en=("Customer engagement marketing automation. Proposed "
                  "deployment region is Salesforce's EU/US — flagged for "
                  "Tier-3 customer-data residency violation under SAMA Cloud "
                  "Framework §5; CISO + DPO triaging classification "
                  "downgrade vs. region change."),
        desc_ar=("أتمتة تسويقية لتفاعل العملاء. المنطقة المقترحة لسيلز فورس "
                  "أوروبا/الولايات المتحدة — مرشّحة لمخالفة مقرّ بيانات الفئة "
                  "الثالثة وفق §٥ من إطار ساما؛ مسؤول أمن المعلومات وحامي "
                  "البيانات يدرسان تخفيض التصنيف أو تغيير المنطقة."),
        cat="MARKETING", materiality="NON_MATERIAL", core=False,
        status="RISK_ASSESSED", risk_rating="MEDIUM",
        risk_at=NOW - timedelta(days=15),
        cstart=TODAY + timedelta(days=60), cend=TODAY + timedelta(days=790),
        notice_days=30, spend=420_000, cadence=24,
    )

    # 7. Genesys Cloud — call centre PaaS — MATERIAL LIVE — review overdue
    a['gen'] = ins_arr(
        1, "OUT-2024-014",
        prov_en="Genesys Telecommunications Laboratories",
        prov_ar="معامل جينيسيس للاتصالات",
        country="SA", jur="SAUDI",
        title_en="Genesys Cloud — contact-centre PaaS",
        title_ar="جينيسيس سحابي — منصّة مركز الاتصال",
        desc_en=("Customer contact centre routing + recording + analytics. "
                  "Operational since 2024; last ongoing review overdue by "
                  "~12 days (primes AR-269)."),
        desc_ar=("توجيه مركز الاتصال بالعملاء والتسجيل والتحليلات. تعمل منذ "
                  "٢٠٢٤؛ المراجعة الدورية الأخيرة متأخرة حوالي ١٢ يوماً "
                  "(تُفعّل AR-269)."),
        cat="CALL_CENTRE", materiality="MATERIAL", core=False, status="LIVE",
        risk_rating="MEDIUM",
        risk_at=NOW - timedelta(days=420), board_at=NOW - timedelta(days=400),
        sama_at=NOW - timedelta(days=390), sama_ref="SAMA/COR/2024/0156",
        live_at=TODAY - timedelta(days=370),
        cstart=TODAY - timedelta(days=375), cend=TODAY + timedelta(days=355),
        notice_days=90, spend=1_350_000,
        next_review=TODAY - timedelta(days=12),  # OVERDUE
        cadence=12,
    )

    # 8. Print + mail-house — NON_MATERIAL
    a['print'] = ins_arr(
        1, "OUT-2024-022",
        prov_en="Saudi Arabian Printing & Mailing Co.",
        prov_ar="الشركة السعودية للطباعة والبريد",
        country="SA", jur="SAUDI",
        title_en="Statement printing + secure mailing",
        title_ar="طباعة كشوف الحساب والبريد الآمن",
        desc_en="Monthly statement printing and secure last-mile delivery to retail customers.",
        desc_ar="طباعة كشوف الحساب الشهرية والتسليم الآمن للعملاء الأفراد.",
        cat="PRINTING", materiality="NON_MATERIAL", core=False, status="LIVE",
        risk_rating="LOW",
        risk_at=NOW - timedelta(days=300), board_at=NOW - timedelta(days=280),
        live_at=TODAY - timedelta(days=270),
        cstart=TODAY - timedelta(days=275), cend=TODAY + timedelta(days=455),
        notice_days=60, spend=180_000,
        next_review=TODAY + timedelta(days=420), cadence=24,
    )

    # ─── Cloud services ────────────────────────────────────────────────

    # AWS Bahrain analytics → IN_USE
    ins_cloud(1, a['aws'], "CSV-AWS-2025-001",
        name_en="AWS me-south-1 — Data Lake (S3 + Glue + Athena)",
        name_ar="AWS me-south-1 — بحيرة البيانات (S3 + Glue + Athena)",
        deploy="IAAS", smodel="PUBLIC",
        csp_en="Amazon Web Services EMEA SARL",
        csp_ar="أمازون ويب سيرفيسز EMEA",
        brand="AWS", classif="T2", residency="GCC", region="me-south-1",
        status="IN_USE",
        classified_at=NOW - timedelta(days=130),
        approved_at=NOW - timedelta(days=125),
        in_use_at=TODAY - timedelta(days=120),
        approver="CISO", dpia_ref="DPIA-2025-014")

    # STC Cloud production
    ins_cloud(1, a['stc'], "CSV-STC-2025-002",
        name_en="STC Cloud Riyadh — Digital Channels Production",
        name_ar="سحابة STC الرياض — إنتاج القنوات الرقمية",
        deploy="IAAS", smodel="PRIVATE",
        csp_en="Saudi Telecom Company — STC Cloud",
        csp_ar="شركة الاتصالات السعودية — سحابة STC",
        brand="STC_CLOUD", classif="T3", residency="IN_KINGDOM",
        region="KSA-Riyadh-Zone1", status="IN_USE",
        classified_at=NOW - timedelta(days=110),
        approved_at=NOW - timedelta(days=100),
        in_use_at=TODAY - timedelta(days=95),
        approver="CRO", dpia_ref="DPIA-2025-018")

    # Splunk Cloud
    ins_cloud(1, a['splunk'], "CSV-SPL-2025-003",
        name_en="Splunk Cloud SIEM Backend",
        name_ar="سبلانك سحابي SIEM الخلفي",
        deploy="SAAS", smodel="PUBLIC",
        csp_en="Splunk Services UK Ltd",
        csp_ar="سبلانك خدمات المملكة المتحدة",
        brand="OTHER", classif="T2", residency="GCC", region="me-south-1",
        status="IN_USE",
        classified_at=NOW - timedelta(days=80),
        approved_at=NOW - timedelta(days=75),
        in_use_at=TODAY - timedelta(days=70),
        approver="CISO", dpia_ref="DPIA-2025-022")

    # Salesforce — auto-BLOCKED on classification + residency mismatch
    ins_cloud(1, a['sf'], "CSV-SF-2026-001",
        name_en="Salesforce Marketing Cloud — proposed",
        name_ar="سيلز فورس للتسويق السحابي — مقترح",
        deploy="SAAS", smodel="PUBLIC",
        csp_en="Salesforce.com EMEA Ltd",
        csp_ar="سيلز فورس EMEA المحدودة",
        brand="SALESFORCE", classif="T3", residency="GLOBAL",
        region="us-east-1", status="BLOCKED",
        classified_at=NOW - timedelta(days=8),
        block_en="Residency GLOBAL incompatible with classification T3 — SAMA Cloud Framework §5 requires IN_KINGDOM for T3.",
        block_ar="مقرّ المعالجة GLOBAL غير متوافق مع التصنيف T3 — إطار ساما للحوسبة السحابية §٥ يستوجب IN_KINGDOM للفئة الثالثة.",
        approver="CISO")

    # TCS T24 hosting (private)
    ins_cloud(1, a['tcs'], "CSV-TCS-2025-001",
        name_en="TCS Managed T24 Cluster — on-prem co-managed",
        name_ar="عنقود T24 المُدار من تاتا — تشاركي داخل المركز",
        deploy="HYBRID", smodel="PRIVATE",
        csp_en="Tata Consultancy Services Saudi Arabia LLC",
        csp_ar="شركة تاتا للاستشارات الخدمية السعودية المحدودة",
        brand="OTHER", classif="T4", residency="IN_KINGDOM",
        region="KSA-DC1-Riyadh", status="IN_USE",
        classified_at=NOW - timedelta(days=200),
        approved_at=NOW - timedelta(days=190),
        in_use_at=TODAY - timedelta(days=180),
        approver="CRO", dpia_ref="DPIA-2024-009")

    # Oracle Exadata — still in ONBOARDING (arrangement is BOARD_APPROVED)
    ins_cloud(1, a['oracle'], "CSV-ORA-2026-001",
        name_en="Oracle Exadata Cloud@Customer — risk engine DB",
        name_ar="أوراكل Exadata Cloud@Customer — قاعدة بيانات محرّك المخاطر",
        deploy="HYBRID", smodel="PRIVATE",
        csp_en="Oracle Saudi Arabia Ltd",
        csp_ar="أوراكل المملكة العربية السعودية المحدودة",
        brand="ORACLE_CLOUD", classif="T3", residency="IN_KINGDOM",
        region="KSA-DC2-Riyadh", status="ONBOARDING",
        approver="CRO")

    # Genesys — IN_USE
    ins_cloud(1, a['gen'], "CSV-GEN-2024-001",
        name_en="Genesys Cloud — call recording + analytics",
        name_ar="جينيسيس سحابي — تسجيل المكالمات والتحليلات",
        deploy="SAAS", smodel="PUBLIC",
        csp_en="Genesys Telecommunications Laboratories",
        csp_ar="معامل جينيسيس للاتصالات",
        brand="OTHER", classif="T3", residency="IN_KINGDOM",
        region="KSA-Riyadh-Zone1", status="IN_USE",
        classified_at=NOW - timedelta(days=380),
        approved_at=NOW - timedelta(days=375),
        in_use_at=TODAY - timedelta(days=370),
        approver="CISO", dpia_ref="DPIA-2024-022")

    # ─── Ongoing reviews ────────────────────────────────────────────────

    # Overdue review on Genesys — primes AR-269
    ins_rev(1, a['gen'], "REV-2026-Q2-001",
        period="Q2-2026", status="SCHEDULED",
        due=TODAY - timedelta(days=12))

    # In progress review on AWS
    ins_rev(1, a['aws'], "REV-2026-Q2-002",
        period="Q2-2026", status="IN_PROGRESS",
        due=TODAY + timedelta(days=18),
        started=NOW - timedelta(days=6))

    # Completed-with-findings on STC Cloud
    ins_rev(1, a['stc'], "REV-2026-Q1-003",
        period="Q1-2026", status="WITH_FINDINGS",
        due=TODAY - timedelta(days=45),
        started=NOW - timedelta(days=55),
        completed=NOW - timedelta(days=40),
        rag="AMBER",
        findings_en="DR drill RTO 6h overrun by 90 minutes (target 6h, actual 7h30m). Backup-network failover took 38 min vs 15 min target. STC committed remediation in 60 days.",
        findings_ar="انحراف في زمن استعادة الكوارث بـ٩٠ دقيقة (المستهدف ٦ ساعات، الفعلي ٧ ساعات ٣٠ دقيقة). تجاوز تبديل الشبكة الاحتياطية ٣٨ دقيقة مقابل ١٥ دقيقة. التزمت STC بالعلاج خلال ٦٠ يوماً.")

    # ─── Exit plans ─────────────────────────────────────────────────────

    ins_exit(1, a['tcs'], "EXIT-TCS-001",
        status="REHEARSED",
        summary_en=("Exit to Infosys Saudi Arabia secondary platform if TCS "
                     "becomes unavailable. Code-base, run-book, and migration "
                     "scripts in escrow with PwC Saudi. 90-day migration "
                     "window; rehearsed in tabletop exercise EX-2025-Q4-007 "
                     "with successful walk-through."),
        summary_ar=("الخروج إلى منصّة Infosys السعودية البديلة في حال تعذّر "
                     "TCS. قاعدة الشيفرة وكتيّبات التشغيل وسكربتات الترحيل "
                     "محفوظة لدى PwC السعودية. نافذة ترحيل ٩٠ يوماً؛ تمّت "
                     "تجربة الانتقال في تمرين مكتبي EX-2025-Q4-007 بنجاح."),
        days=90,
        alt_en="Infosys Saudi Arabia Ltd",
        alt_ar="إنفوسيس المملكة العربية السعودية المحدودة",
        approved=NOW - timedelta(days=150),
        rehearsed=TODAY - timedelta(days=70))

    ins_exit(1, a['aws'], "EXIT-AWS-001",
        status="APPROVED",
        summary_en="Workload migration to STC Cloud Riyadh + Mobily Cloud secondary. Analytics jobs are stateless, dev/test snapshots replicated nightly to STC. 30-day migration.",
        summary_ar="ترحيل الأحمال إلى سحابة STC الرياض وسحابة Mobily الاحتياطية. المهام التحليلية بدون حالة، ولقطات التطوير/الاختبار مكرَّرة ليلياً إلى STC. ترحيل ٣٠ يوماً.",
        days=30,
        alt_en="STC Cloud Riyadh + Mobily Cloud",
        alt_ar="سحابة STC الرياض + سحابة موبايلي",
        approved=NOW - timedelta(days=80))

    ins_exit(1, a['stc'], "EXIT-STC-001",
        status="READY",
        summary_en="Cross-cloud DR to Mobily Cloud + a smaller AWS Bahrain runtime cluster. Verified end-to-end via DR exercise. Migration window 21 days.",
        summary_ar="استعادة كوارث متعدّدة السحابات إلى سحابة Mobily وعنقود AWS البحرين الأصغر. تحقّقت شاملة عبر تمرين الاستعادة. نافذة الترحيل ٢١ يوماً.",
        days=21,
        alt_en="Mobily Cloud (primary) + AWS me-south-1 (secondary)",
        alt_ar="سحابة موبايلي (الأساسية) + AWS me-south-1 (الاحتياطية)",
        approved=NOW - timedelta(days=50),
        rehearsed=TODAY - timedelta(days=25))

    return a


# ──────────────────────────────────────────────────── ORG 2 — Aramco Trading ──

def seed_org2():
    print(">>> org=2 Aramco Trading — energy/trading outsourcing")
    a = {}

    a['eka'] = ins_arr(
        2, "OUT-2025-101",
        prov_en="Eka Software Solutions",
        prov_ar="إيكا للحلول البرمجية",
        country="IN", jur="GLOBAL",
        title_en="Eka TRM — energy trade-risk-management platform support",
        title_ar="إيكا TRM — دعم منصّة إدارة مخاطر تداول الطاقة",
        desc_en="Front-to-back TRM platform L3 support for the energy trading desk.",
        desc_ar="دعم المستوى الثالث لمنصّة إدارة مخاطر التداول من الجبهة إلى الخلف لمكتب التداول.",
        cat="MARKET_DATA", materiality="MATERIAL", core=False, status="LIVE",
        risk_rating="HIGH",
        risk_at=NOW - timedelta(days=200), board_at=NOW - timedelta(days=180),
        sama_at=NOW - timedelta(days=170),
        live_at=TODAY - timedelta(days=150),
        cstart=TODAY - timedelta(days=155), cend=TODAY + timedelta(days=575),
        notice_days=120, spend=2_100_000,
        next_review=TODAY + timedelta(days=210), cadence=12,
    )

    a['azure'] = ins_arr(
        2, "OUT-2025-105",
        prov_en="Microsoft Arabia Ltd — Azure",
        prov_ar="مايكروسوفت العربية المحدودة — أزور",
        country="SA", jur="SAUDI",
        title_en="Azure Riyadh region — trading analytics workloads",
        title_ar="منطقة أزور الرياض — أحمال تحليلات التداول",
        desc_en="Trading analytics + ML model serving in Azure Riyadh region.",
        desc_ar="تحليلات التداول وتقديم نماذج التعلّم الآلي في منطقة أزور الرياض.",
        cat="CLOUD", materiality="MATERIAL", core=False, status="LIVE",
        risk_rating="MEDIUM",
        risk_at=NOW - timedelta(days=130), board_at=NOW - timedelta(days=110),
        live_at=TODAY - timedelta(days=90),
        cstart=TODAY - timedelta(days=95), cend=TODAY + timedelta(days=635),
        notice_days=90, spend=1_400_000,
        next_review=TODAY + timedelta(days=270), cadence=12,
    )

    a['refinitiv'] = ins_arr(
        2, "OUT-2025-108",
        prov_en="Refinitiv (an LSEG business)",
        prov_ar="ريفينيتيف (تابعة لمجموعة بورصة لندن)",
        country="GB", jur="GLOBAL",
        title_en="Refinitiv Eikon — market data feed",
        title_ar="ريفينيتيف إيكون — تغذية بيانات السوق",
        desc_en="Real-time energy market data feed and analytics for trading desk.",
        desc_ar="تغذية بيانات أسواق الطاقة الفورية والتحليلات لمكتب التداول.",
        cat="MARKET_DATA", materiality="NON_MATERIAL", core=False, status="LIVE",
        risk_rating="LOW",
        risk_at=NOW - timedelta(days=240), board_at=NOW - timedelta(days=220),
        live_at=TODAY - timedelta(days=200),
        cstart=TODAY - timedelta(days=205), cend=TODAY + timedelta(days=525),
        notice_days=60, spend=580_000,
        next_review=TODAY + timedelta(days=540), cadence=24,
    )

    a['legal'] = ins_arr(
        2, "OUT-2024-112",
        prov_en="Hadef & Partners",
        prov_ar="هادف وشركاؤه",
        country="AE", jur="GCC",
        title_en="External legal counsel — energy contracts",
        title_ar="استشارات قانونية خارجية — عقود الطاقة",
        desc_en="External legal counsel on Abu Dhabi-arbitrated contracts.",
        desc_ar="استشارات قانونية خارجية للعقود الخاضعة لتحكيم أبوظبي.",
        cat="LEGAL", materiality="NON_MATERIAL", core=False, status="LIVE",
        risk_rating="LOW",
        risk_at=NOW - timedelta(days=380), live_at=TODAY - timedelta(days=350),
        cstart=TODAY - timedelta(days=355), cend=TODAY + timedelta(days=375),
        notice_days=30, spend=380_000,
        next_review=TODAY + timedelta(days=370), cadence=24,
    )

    ins_cloud(2, a['azure'], "CSV-AZ-2025-101",
        name_en="Azure Riyadh — trading workloads",
        name_ar="أزور الرياض — أحمال التداول",
        deploy="IAAS", smodel="PUBLIC",
        csp_en="Microsoft Arabia Ltd",
        csp_ar="مايكروسوفت العربية المحدودة",
        brand="AZURE", classif="T2", residency="IN_KINGDOM",
        region="KSA-Riyadh", status="IN_USE",
        classified_at=NOW - timedelta(days=100),
        approved_at=NOW - timedelta(days=95),
        in_use_at=TODAY - timedelta(days=90),
        approver="CIO")

    ins_cloud(2, a['eka'], "CSV-EKA-2025-101",
        name_en="Eka TRM Cloud — managed PaaS",
        name_ar="إيكا TRM السحابي — منصّة كخدمة مُدارة",
        deploy="PAAS", smodel="PRIVATE",
        csp_en="Eka Software Solutions",
        csp_ar="إيكا للحلول البرمجية",
        brand="OTHER", classif="T3", residency="GCC", region="me-south-1",
        status="APPROVED",  # T3+GCC fails residency — would BLOCK if re-transitioned
        classified_at=NOW - timedelta(days=160),
        approved_at=NOW - timedelta(days=150),
        approver="CRO")

    ins_cloud(2, a['refinitiv'], "CSV-REF-2025-101",
        name_en="Refinitiv Eikon Web — market data SaaS",
        name_ar="ريفينيتيف إيكون ويب — SaaS بيانات السوق",
        deploy="SAAS", smodel="PUBLIC",
        csp_en="Refinitiv (an LSEG business)",
        csp_ar="ريفينيتيف (تابعة لمجموعة بورصة لندن)",
        brand="OTHER", classif="T1", residency="GLOBAL", region="eu-west-2",
        status="IN_USE",
        classified_at=NOW - timedelta(days=220),
        approved_at=NOW - timedelta(days=215),
        in_use_at=TODAY - timedelta(days=200),
        approver="CISO")

    return a


# ──────────────────────────────────────────────────── ORG 3 — STC Bank ──

def seed_org3():
    print(">>> org=3 STC Bank — telco-banking outsourcing")
    a = {}

    a['parent'] = ins_arr(
        3, "OUT-2024-201",
        prov_en="Saudi Telecom Company — Group IT (parent)",
        prov_ar="شركة الاتصالات السعودية — تقنية المعلومات (الشركة الأم)",
        country="SA", jur="SAUDI",
        title_en="Intra-group IT services — telco voice/USSD/SMS backbone",
        title_ar="خدمات تقنية المعلومات داخل المجموعة — العمود الفقري للصوت / USSD / الرسائل القصيرة",
        desc_en=("Intra-group IT services from the parent telco. SAMA "
                  "non-objection on file (intra-group still SAMA-notifiable "
                  "for material activities)."),
        desc_ar=("خدمات تقنية المعلومات داخل المجموعة من الشركة الأم للاتصالات. "
                  "خطاب عدم الممانعة من ساما محفوظ (داخل المجموعة لا يزال "
                  "خاضعاً للإخطار للأنشطة الجوهرية)."),
        cat="IT_INFRASTRUCTURE", materiality="MATERIAL", core=False, status="LIVE",
        risk_rating="MEDIUM",
        risk_at=NOW - timedelta(days=480), board_at=NOW - timedelta(days=460),
        sama_at=NOW - timedelta(days=450), sama_ref="SAMA/COR/2024/0089",
        live_at=TODAY - timedelta(days=430),
        cstart=TODAY - timedelta(days=435), cend=TODAY + timedelta(days=300),
        notice_days=180, spend=8_500_000,
        next_review=TODAY + timedelta(days=120), cadence=12,
    )

    a['hsm'] = ins_arr(
        3, "OUT-2025-204",
        prov_en="Thales DIS (Saudi)",
        prov_ar="تاليس DIS (السعودية)",
        country="SA", jur="SAUDI",
        title_en="Thales HSM cluster — cryptographic key custody",
        title_ar="عنقود Thales HSM — حفظ المفاتيح التشفيرية",
        desc_en="HSM cluster custody + managed key-management for the banking platform.",
        desc_ar="حفظ عنقود HSM وإدارة مفاتيح للمنصّة المصرفية.",
        cat="IT_INFRASTRUCTURE", materiality="MATERIAL", core=True, status="LIVE",
        risk_rating="HIGH",
        risk_at=NOW - timedelta(days=200), board_at=NOW - timedelta(days=180),
        sama_at=NOW - timedelta(days=170), sama_ref="SAMA/COR/2025/0117",
        live_at=TODAY - timedelta(days=145),
        cstart=TODAY - timedelta(days=150), cend=TODAY + timedelta(days=580),
        notice_days=180, spend=1_650_000,
        next_review=TODAY + timedelta(days=180), cadence=12,
    )

    a['biom'] = ins_arr(
        3, "OUT-2025-208",
        prov_en="Nuance Communications (Microsoft)",
        prov_ar="نوانس للاتصالات (مايكروسوفت)",
        country="US", jur="GLOBAL",
        title_en="Voice biometrics — call-centre authentication",
        title_ar="القياسات الحيوية الصوتية — مصادقة مركز الاتصال",
        desc_en="Voice biometric authentication for inbound IVR.",
        desc_ar="مصادقة بصمة صوتية للنداء الصوتي الوارد.",
        cat="OTHER", materiality="NON_MATERIAL", core=False, status="LIVE",
        risk_rating="MEDIUM",
        risk_at=NOW - timedelta(days=180), board_at=NOW - timedelta(days=160),
        live_at=TODAY - timedelta(days=140),
        cstart=TODAY - timedelta(days=145), cend=TODAY + timedelta(days=585),
        notice_days=90, spend=920_000,
        next_review=TODAY + timedelta(days=225), cadence=12,
    )

    ins_cloud(3, a['parent'], "CSV-STC-2024-201",
        name_en="STC Cloud Riyadh — banking platform primary",
        name_ar="سحابة STC الرياض — المنصّة المصرفية الأساسية",
        deploy="IAAS", smodel="PRIVATE",
        csp_en="Saudi Telecom Company — STC Cloud",
        csp_ar="شركة الاتصالات السعودية — سحابة STC",
        brand="STC_CLOUD", classif="T4", residency="IN_KINGDOM",
        region="KSA-Riyadh-Zone1", status="IN_USE",
        classified_at=NOW - timedelta(days=440),
        approved_at=NOW - timedelta(days=435),
        in_use_at=TODAY - timedelta(days=430),
        approver="CRO")

    ins_cloud(3, a['biom'], "CSV-NU-2025-201",
        name_en="Nuance Gatekeeper voice biometrics",
        name_ar="بصمة نوانس الصوتية Gatekeeper",
        deploy="SAAS", smodel="PUBLIC",
        csp_en="Nuance Communications",
        csp_ar="نوانس للاتصالات",
        brand="OTHER", classif="T3", residency="GLOBAL", region="us-east-1",
        status="BLOCKED",  # T3+GLOBAL — same auto-block pattern
        classified_at=NOW - timedelta(days=130),
        block_en="Voiceprint biometrics are PDPL Article 4 special-category data — T3 residency must be IN_KINGDOM, not us-east-1.",
        block_ar="بصمات الصوت بيانات حسّاسة وفق المادة ٤ من نظام حماية البيانات — مقرّ المعالجة لـT3 يجب أن يكون داخل المملكة، لا us-east-1.",
        approver="DPO")

    return a


# ──────────────────────────────────────────────────────────── Driver ──

def main():
    print(">>> Sprint 30 PR #4 — Saudi-realistic outsourcing & cloud seed")
    try:
        a1 = seed_org1()
        a2 = seed_org2()
        a3 = seed_org3()
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"!!! ROLLED BACK: {e}")
        raise

    for tbl, label in [
        ("outsourcing.outsourcing_arrangement", "arrangements"),
        ("outsourcing.cloud_service",           "cloud services"),
        ("outsourcing.ongoing_review",          "reviews"),
        ("outsourcing.exit_plan",               "exit plans"),
    ]:
        cur.execute(f"SELECT COUNT(*) FROM {tbl} WHERE is_deleted = 0")
        print(f"+ {label:<20s}: {cur.fetchone()[0]}")
    print(">>> done.")


if __name__ == "__main__":
    main()
