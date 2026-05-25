"""Saudi-realistic Data Classification + NDMO seed — Sprint 32 PR #4.

Seeds 12 data assets across the 4 NDMO levels for org=1, with realistic
Saudi-banking data domains. Plus 6 flows (2 cross-border, primes AR-285)
and 3 exceptions (1 expiring soon, 1 ACTIVE long-term, 1 REQUESTED).
"""
from __future__ import annotations
import sys
from datetime import date, datetime, timedelta
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')
conn = pyodbc.connect(
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;")
conn.autocommit = False
cur = conn.cursor()
NOW = datetime.now()
TODAY = date.today()


def asset(org, ref, *, name_en, name_ar, desc_en, desc_ar, domain,
          classification, status, special="NONE", lawful_basis=None,
          host=None, retention=None, enc_rest=True, enc_transit=True,
          masking=False, classified_at=None, approved_at=None,
          next_review=None, dpia_ref=None, rows=None):
    cur.execute("SELECT id FROM dataclass.data_asset WHERE organisation_id = ? AND reference = ? AND is_deleted = 0", org, ref)
    r = cur.fetchone()
    if r: return int(r[0])
    cur.execute(
        "INSERT INTO dataclass.data_asset "
        "(organisation_id, reference, name_en, name_ar, description_en, description_ar, "
        " domain, classification, pdpl_special_category, pdpl_lawful_basis, status, "
        " host_system_label, retention_years, encryption_at_rest, encryption_in_transit, "
        " masking_required, classified_at, approved_at, next_review_due, "
        " linked_dpia_ref, row_count_approx) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, name_en, name_ar, desc_en, desc_ar, domain, classification,
        special, lawful_basis, status, host, retention,
        1 if enc_rest else 0, 1 if enc_transit else 0, 1 if masking else 0,
        classified_at, approved_at, next_review, dpia_ref, rows,
    )
    return int(cur.fetchone()[0])


def flow(org, asset_id, ref, *, src, dest, country, adequacy, mode, status,
         purpose_en, purpose_ar, art7_ref=None, approved_at=None,
         activated=None):
    cur.execute("SELECT id FROM dataclass.data_flow WHERE organisation_id = ? AND reference = ? AND is_deleted = 0", org, ref)
    r = cur.fetchone()
    if r: return int(r[0])
    cur.execute(
        "INSERT INTO dataclass.data_flow "
        "(organisation_id, data_asset_id, reference, source_system_label, destination_system_label, "
        " destination_country, destination_adequacy, transfer_mode, status, "
        " purpose_en, purpose_ar, pdpl_art7_transfer_ref, approved_at, activated_at) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, asset_id, ref, src, dest, country, adequacy, mode, status,
        purpose_en, purpose_ar, art7_ref, approved_at, activated,
    )
    return int(cur.fetchone()[0])


def exception(org, ref, *, asset_id=None, flow_id=None, etype, status,
              just_en, just_ar, comp_en, comp_ar,
              valid_from=None, valid_until=None, approved_at=None):
    cur.execute("SELECT id FROM dataclass.data_exception WHERE organisation_id = ? AND reference = ? AND is_deleted = 0", org, ref)
    r = cur.fetchone()
    if r: return int(r[0])
    cur.execute(
        "INSERT INTO dataclass.data_exception "
        "(organisation_id, data_asset_id, data_flow_id, reference, exception_type, status, "
        " justification_en, justification_ar, compensating_controls_en, compensating_controls_ar, "
        " valid_from, valid_until, approved_at) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, asset_id, flow_id, ref, etype, status, just_en, just_ar, comp_en, comp_ar,
        valid_from, valid_until, approved_at,
    )
    return int(cur.fetchone()[0])


def seed_org1():
    print(">>> org=1 TSS GRC bank — Data Classification + NDMO seed")
    a = {}

    # ─── 12 Saudi-banking data assets ────────────────────────────────

    a['customer'] = asset(1, "DA-CUST-001",
        name_en="Retail Customer Master (Core Banking)",
        name_ar="السجل الرئيسي للعملاء الأفراد (النواة المصرفية)",
        desc_en="Master customer register: National ID/Iqama, full name, DOB, address, phone, email, employer.",
        desc_ar="السجل الرئيسي للعملاء: الهوية الوطنية/الإقامة، الاسم الكامل، تاريخ الميلاد، العنوان، الهاتف، البريد، صاحب العمل.",
        domain="CUSTOMER_PII",
        classification="CONFIDENTIAL", status="IN_USE",
        special="NONE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGAL_OBLIGATION_AML",
        host="Temenos T24 (TCS-managed)", retention=10,
        classified_at=NOW - timedelta(days=400),
        approved_at=NOW - timedelta(days=395),
        next_review=TODAY + timedelta(days=180),
        dpia_ref="DPIA-2024-001", rows=4_800_000)

    a['transactions'] = asset(1, "DA-TXN-002",
        name_en="Customer Transaction Ledger",
        name_ar="سجل معاملات العملاء",
        desc_en="Daily transaction ledger across current, savings, card, and Murabaha products.",
        desc_ar="سجل المعاملات اليومي عبر الحسابات الجارية والادخار والبطاقات والمرابحة.",
        domain="FINANCIAL_TRANSACTION",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGAL_OBLIGATION_AML",
        host="Temenos T24 + Oracle Exadata DW", retention=10,
        classified_at=NOW - timedelta(days=380),
        approved_at=NOW - timedelta(days=375),
        next_review=TODAY + timedelta(days=200),
        dpia_ref="DPIA-2024-002", rows=2_400_000_000)

    a['biometric'] = asset(1, "DA-BIO-003",
        name_en="Customer Voice Biometric Templates",
        name_ar="نماذج البصمة الصوتية للعملاء",
        desc_en="Voiceprint templates for IVR authentication (Nuance Gatekeeper).",
        desc_ar="نماذج بصمة صوتية لمصادقة الردّ الصوتي التلقائي (Nuance Gatekeeper).",
        domain="CUSTOMER_PII",
        classification="TOP_SECRET", status="IN_USE",
        special="BIOMETRIC",
        lawful_basis="EXPLICIT_CONSENT",
        host="Nuance — IN_KINGDOM-only deployment", retention=5,
        masking=True,
        classified_at=NOW - timedelta(days=180),
        approved_at=NOW - timedelta(days=175),
        next_review=TODAY + timedelta(days=120),
        dpia_ref="DPIA-2025-008", rows=480_000)

    a['credit_file'] = asset(1, "DA-CRD-004",
        name_en="Credit File — Underwriting + Decisioning",
        name_ar="ملف الائتمان — الاكتتاب والقرارات",
        desc_en="Per-application credit file including SIMAH bureau pull, employment letter, income statement, decision rationale.",
        desc_ar="ملف ائتماني لكل طلب يشمل استعلام بيمة، خطاب التوظيف، كشف الدخل، مسوّغات القرار.",
        domain="CREDIT_FILE",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGITIMATE_INTEREST_CREDIT_RISK",
        host="Underwriting Workbench (in-Kingdom)", retention=7,
        classified_at=NOW - timedelta(days=360),
        approved_at=NOW - timedelta(days=355),
        next_review=TODAY + timedelta(days=220),
        dpia_ref="DPIA-2024-004", rows=1_200_000)

    a['aml'] = asset(1, "DA-AML-005",
        name_en="AML Case File (STR/CTR + KYC EDD)",
        name_ar="ملف قضايا غسل الأموال (تقارير العمليات المشبوهة + العناية المُعزَّزة)",
        desc_en="STR/CTR filings + supporting KYC EDD evidence + SAFIU correspondence.",
        desc_ar="تقارير العمليات المشبوهة والكبيرة + أدلة العناية المُعزَّزة + مراسلات سافيو.",
        domain="AML_KYC",
        classification="TOP_SECRET", status="IN_USE",
        special="CRIMINAL",
        lawful_basis="LEGAL_OBLIGATION_AML",
        host="AML Case Management (in-Kingdom)", retention=10,
        masking=True,
        classified_at=NOW - timedelta(days=350),
        approved_at=NOW - timedelta(days=345),
        next_review=TODAY + timedelta(days=180),
        dpia_ref="DPIA-2024-005", rows=4_800)

    a['employee'] = asset(1, "DA-EMP-006",
        name_en="Employee HR Master",
        name_ar="سجل الموارد البشرية للموظفين",
        desc_en="Employee master: Iqama, salary, performance, training history, family + emergency contacts.",
        desc_ar="سجل الموظفين: الإقامة، الراتب، الأداء، تاريخ التدريب، الأسرة وجهات الاتصال للطوارئ.",
        domain="EMPLOYEE",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGAL_OBLIGATION_LABOUR",
        host="Oracle HCM Cloud (KSA region)", retention=7,
        classified_at=NOW - timedelta(days=320),
        approved_at=NOW - timedelta(days=315),
        next_review=TODAY + timedelta(days=140),
        dpia_ref="DPIA-2024-006", rows=3_200)

    a['sharia'] = asset(1, "DA-SHR-007",
        name_en="Sharia Governance Records",
        name_ar="سجلات الحوكمة الشرعية",
        desc_en="SSB minutes, fatwas, SNCI events, audit findings — internal Sharia Audit working papers.",
        desc_ar="محاضر هيئة الرقابة الشرعية، الفتاوى، أحداث دخل عدم الالتزام، نتائج المراجعة — أوراق عمل التدقيق الشرعي الداخلي.",
        domain="SHARIA",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="LEGITIMATE_INTEREST_GOVERNANCE",
        host="GRC Platform (in-Kingdom)", retention=10,
        classified_at=NOW - timedelta(days=85),
        approved_at=NOW - timedelta(days=80),
        next_review=TODAY + timedelta(days=280),
        dpia_ref="DPIA-2026-002", rows=12_400)

    a['fees_book'] = asset(1, "DA-FEE-008",
        name_en="Fees & Charges Schedule (Public)",
        name_ar="جدول الرسوم والمصاريف (عام)",
        desc_en="Published fees and charges schedule — public website disclosure.",
        desc_ar="جدول الرسوم والمصاريف المنشور — إفصاح موقع عام.",
        domain="OPERATIONAL",
        classification="PUBLIC", status="IN_USE",
        lawful_basis="LEGAL_OBLIGATION_CONSUMER_PROTECTION",
        host="Public website CMS", retention=5,
        encryption_at_rest=False, masking=False,
        classified_at=NOW - timedelta(days=420),
        approved_at=NOW - timedelta(days=415),
        next_review=TODAY + timedelta(days=520), rows=180)

    a['policies'] = asset(1, "DA-POL-009",
        name_en="Internal Policy Library",
        name_ar="مكتبة السياسات الداخلية",
        desc_en="Board-approved policies, procedures, SOPs across all functions.",
        desc_ar="السياسات والإجراءات المعتمدة من المجلس عبر جميع الإدارات.",
        domain="OPERATIONAL",
        classification="INTERNAL", status="IN_USE",
        lawful_basis="LEGITIMATE_INTEREST_GOVERNANCE",
        host="SharePoint (in-Kingdom)", retention=10,
        classified_at=NOW - timedelta(days=300),
        approved_at=NOW - timedelta(days=295),
        next_review=TODAY - timedelta(days=10),  # overdue → primes AR-289
        rows=620)

    a['market_data'] = asset(1, "DA-MKT-010",
        name_en="Market Data Feeds (Refinitiv + Bloomberg)",
        name_ar="تغذيات بيانات السوق (ريفينيتيف وبلومبرغ)",
        desc_en="Vendor-licensed market data feeds — yield curves, FX, indices.",
        desc_ar="تغذيات بيانات السوق المرخّصة من المزوّدين — منحنيات العائد، صرف العملات، المؤشرات.",
        domain="MARKET_DATA",
        classification="INTERNAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE",
        host="Treasury workstation", retention=3,
        classified_at=NOW - timedelta(days=200),
        approved_at=NOW - timedelta(days=195),
        next_review=TODAY + timedelta(days=160), rows=18_000_000)

    a['marketing'] = asset(1, "DA-MKT-011",
        name_en="Marketing Campaign Audience (anonymised)",
        name_ar="جمهور حملات التسويق (مجهّل الهوية)",
        desc_en="Anonymised customer segments for marketing — no direct identifiers.",
        desc_ar="شرائح عملاء مجهّلة الهوية للتسويق — دون معرّفات مباشرة.",
        domain="CUSTOMER_PII",
        classification="INTERNAL", status="IN_USE",
        lawful_basis="LEGITIMATE_INTEREST_MARKETING",
        host="Marketing CRM (in-Kingdom)", retention=2,
        classified_at=NOW - timedelta(days=120),
        approved_at=NOW - timedelta(days=115),
        next_review=TODAY + timedelta(days=240), rows=2_800_000)

    a['contracts'] = asset(1, "DA-CON-012",
        name_en="Vendor Contracts Repository",
        name_ar="مستودع عقود المزوّدين",
        desc_en="Signed master agreements with vendors + outsourcing arrangements (links to Sprint 30 module).",
        desc_ar="الاتفاقيات الرئيسية الموقَّعة مع المزوّدين + ترتيبات الإسناد (مرتبطة بوحدة Sprint 30).",
        domain="CONTRACT",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE",
        host="Legal CLM (in-Kingdom)", retention=15,
        classified_at=NOW - timedelta(days=240),
        approved_at=NOW - timedelta(days=235),
        next_review=TODAY + timedelta(days=240), rows=950)

    # ─── Flows ──────────────────────────────────────────────────────

    flow(1, a['transactions'], "DF-TXN-AWS-001",
        src="Temenos T24",
        dest="AWS me-south-1 Data Lake (Bahrain)",
        country="BH", adequacy="GCC", mode="BATCH", status="ACTIVE",
        purpose_en="Nightly anonymised batch for analytics + ML training.",
        purpose_ar="دفعة ليلية مُجهَّلة للتحليلات وتدريب التعلم الآلي.",
        art7_ref="PDPL-ART7-2024-014",
        approved_at=NOW - timedelta(days=120),
        activated=TODAY - timedelta(days=115))

    flow(1, a['biometric'], "DF-BIO-NUANCE-001",
        src="Nuance Gatekeeper",
        dest="Nuance Cloud (US-East-1)",
        country="US", adequacy="NON_ADEQUATE", mode="STREAMING",
        status="PROPOSED",
        purpose_en="Vendor-side biometric scoring (BLOCKED — primes AR-285).",
        purpose_ar="تسجيل بصمة من جهة المزوّد (محظور — يُفعّل AR-285).")

    flow(1, a['credit_file'], "DF-CRD-SIMAH-001",
        src="Underwriting Workbench",
        dest="SIMAH (in-Kingdom credit bureau)",
        country="SA", adequacy="IN_KINGDOM", mode="API",
        status="ACTIVE",
        purpose_en="Real-time SIMAH credit-bureau lookup on application.",
        purpose_ar="استعلام بيمة الائتماني الفوري على الطلب.",
        approved_at=NOW - timedelta(days=300),
        activated=TODAY - timedelta(days=295))

    flow(1, a['marketing'], "DF-MKT-SF-001",
        src="Marketing CRM",
        dest="Salesforce Marketing Cloud (EU/US)",
        country="IE", adequacy="NON_ADEQUATE", mode="API",
        status="PROPOSED",
        purpose_en="Campaign execution via Salesforce (PROPOSED — pending PDPL Art.7 ruling).",
        purpose_ar="تنفيذ الحملات عبر سيلز فورس (مقترَح — بانتظار قرار المادة ٧).")

    flow(1, a['aml'], "DF-AML-SAFIU-001",
        src="AML Case Management",
        dest="SAFIU (Saudi FIU)",
        country="SA", adequacy="IN_KINGDOM", mode="FILE_TRANSFER",
        status="ACTIVE",
        purpose_en="STR/CTR filing per SAMA AML/CFT Manual.",
        purpose_ar="رفع تقارير العمليات المشبوهة والكبيرة وفق دليل ساما لمكافحة غسل الأموال.",
        approved_at=NOW - timedelta(days=400),
        activated=TODAY - timedelta(days=395))

    flow(1, a['contracts'], "DF-CON-INFOSYS-001",
        src="Legal CLM",
        dest="Infosys Document Vault (India — DR)",
        country="IN", adequacy="NON_ADEQUATE", mode="REPLICATION",
        status="SUSPENDED",
        purpose_en="DR replication for vendor contracts — suspended pending Art.7 review.",
        purpose_ar="نسخ احتياطي للاستعادة من الكوارث لعقود المزوّدين — مُعلَّق بانتظار مراجعة المادة ٧.")

    # ─── Exceptions ─────────────────────────────────────────────────

    exception(1, "EXC-2026-001",
        asset_id=a['marketing'], etype="MASKING_WAIVER",
        status="ACTIVE",
        just_en="Anonymised customer segments retain a single hashed identifier for cohort joins. Hash function is salted per campaign and rotated quarterly.",
        just_ar="الشرائح المجهّلة تحتفظ بمعرّف واحد مجزّأ لربط المجموعات. دالة التجزئة بملحٍ خاص لكل حملة، وتُغيَّر فصلياً.",
        comp_en="Quarterly hash rotation + access restricted to 12 named marketing analysts + 90-day cohort TTL.",
        comp_ar="تدوير التجزئة فصلياً + وصول مقيد لـ١٢ محلّلاً مسمّى + عمر مجموعة ٩٠ يوماً.",
        valid_from=TODAY - timedelta(days=80),
        valid_until=TODAY + timedelta(days=18),  # primes AR-288 reminder ladder
        approved_at=NOW - timedelta(days=78))

    exception(1, "EXC-2025-014",
        asset_id=a['market_data'], etype="ENCRYPTION_AT_REST_WAIVER",
        status="ACTIVE",
        just_en="Real-time market-data feeds are ephemeral (in-memory, no persistence beyond 24h). Encryption-at-rest is moot for the workstation cache layer.",
        just_ar="تغذيات بيانات السوق الفورية عابرة (في الذاكرة، لا ثبات يتجاوز ٢٤ ساعة). تشفير عند الراحة غير ذي صلة لطبقة ذاكرة محطة العمل.",
        comp_en="Disk full-volume encryption (BitLocker) + workstation-only access + no removable media + 24h TTL enforced.",
        comp_ar="تشفير القرص الكامل (BitLocker) + وصول من محطة العمل فقط + لا وسائط قابلة للإزالة + عمر ٢٤ ساعة مُفروض.",
        valid_from=TODAY - timedelta(days=380),
        valid_until=TODAY + timedelta(days=350),
        approved_at=NOW - timedelta(days=378))

    exception(1, "EXC-2026-005",
        asset_id=a['contracts'], etype="CROSS_BORDER_TEMP",
        status="REQUESTED",
        just_en="Infosys India DR replication temporary waiver requested — corporate legal counsel needs offline access to vendor contracts for India arbitration matter (May-2026 hearing).",
        just_ar="استثناء مؤقت لاستنساخ Infosys في الهند مطلوب — يحتاج المستشار القانوني للشركة الوصول دون اتصال إلى عقود المزوّدين لشأن تحكيم في الهند (جلسة مايو ٢٠٢٦).",
        comp_en="Scoped to 3 named contracts only + 60-day TTL + DPO + CISO + Legal joint sign-off + read-only access.",
        comp_ar="مقصور على ٣ عقود مسمّاة فقط + عمر ٦٠ يوماً + موافقة مشتركة من الحامي ومسؤول أمن المعلومات والقانون + وصول للقراءة فقط.",
        valid_from=TODAY + timedelta(days=10),
        valid_until=TODAY + timedelta(days=70))


def main():
    print(">>> Sprint 32 PR #4 — Saudi-realistic data classification + NDMO seed")
    try:
        seed_org1()
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"!!! ROLLED BACK: {e}")
        raise
    for tbl, label in [
        ("dataclass.data_asset", "assets"),
        ("dataclass.data_flow", "flows"),
        ("dataclass.data_exception", "exceptions"),
    ]:
        cur.execute(f"SELECT COUNT(*) FROM {tbl} WHERE is_deleted = 0")
        print(f"+ {label:<12s}: {cur.fetchone()[0]}")
    print(">>> done.")


if __name__ == "__main__":
    main()
