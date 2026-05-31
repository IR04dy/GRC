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
        enc_rest=False, masking=False,
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


def seed_org2():
    """Aramco Trading Company — energy + commodity-trading data estate."""
    print(">>> org=2 Aramco Trading — Data Classification + NDMO seed")
    a = {}

    a['trade'] = asset(2, "DA-ATC-TRD-001",
        name_en="Trade Book & Open Positions (Eka TRM)",
        name_ar="دفتر التداول والمراكز المفتوحة (Eka TRM)",
        desc_en="Physical + paper crude/products trade book: deals, open positions, mark-to-market, VaR inputs.",
        desc_ar="دفتر تداول النفط الخام والمنتجات (مادي وورقي): الصفقات، المراكز المفتوحة، التقييم السوقي، مدخلات القيمة المعرّضة للخطر.",
        domain="FINANCIAL_TRANSACTION",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGITIMATE_INTEREST_TRADING",
        host="Eka TRM (Azure KSA region)", retention=10,
        classified_at=NOW - timedelta(days=300),
        approved_at=NOW - timedelta(days=295),
        next_review=TODAY + timedelta(days=160),
        dpia_ref="DPIA-ATC-2025-001", rows=1_850_000)

    a['cpty'] = asset(2, "DA-ATC-CPT-002",
        name_en="Counterparty & KYC Master (Trading)",
        name_ar="السجل الرئيسي للأطراف المقابلة واعرف عميلك (التداول)",
        desc_en="Trading counterparties: LEI, beneficial ownership, sanctions screening, credit limits, KYC EDD.",
        desc_ar="الأطراف المقابلة للتداول: معرّف الكيان القانوني، الملكية المنتفعة، فحص العقوبات، حدود الائتمان، العناية المُعزَّزة.",
        domain="AML_KYC",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="LEGAL_OBLIGATION_AML,CONTRACT_PERFORMANCE",
        host="Eka TRM + Refinitiv World-Check", retention=10,
        masking=True,
        classified_at=NOW - timedelta(days=280),
        approved_at=NOW - timedelta(days=275),
        next_review=TODAY + timedelta(days=120),
        dpia_ref="DPIA-ATC-2025-002", rows=3_400)

    a['scada'] = asset(2, "DA-ATC-SCD-003",
        name_en="Pipeline SCADA / DCS Telemetry (Honeywell)",
        name_ar="بيانات قياس أنظمة سكادا/التحكّم الموزّع للأنابيب (هانيويل)",
        desc_en="Operational technology telemetry from pipeline SCADA + Honeywell DCS — flow, pressure, valve state, alarms.",
        desc_ar="بيانات التقنية التشغيلية من سكادا الأنابيب والتحكّم الموزّع (هانيويل) — التدفّق، الضغط، حالة الصمّامات، الإنذارات.",
        domain="OPERATIONAL",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="LEGITIMATE_INTEREST_OPERATIONS",
        host="OT Historian (air-gapped, in-Kingdom)", retention=7,
        classified_at=NOW - timedelta(days=240),
        approved_at=NOW - timedelta(days=235),
        next_review=TODAY - timedelta(days=8),  # overdue → primes AR-289
        rows=920_000_000)

    a['contracts'] = asset(2, "DA-ATC-CON-004",
        name_en="Physical & Derivative Contracts",
        name_ar="العقود المادية والمشتقّات",
        desc_en="Signed physical supply, offtake, ISDA, and freight charter contracts.",
        desc_ar="عقود التوريد المادي والشراء، واتفاقيات الأيسدا (ISDA)، وعقود استئجار الشحن الموقَّعة.",
        domain="CONTRACT",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE",
        host="Legal CLM (in-Kingdom)", retention=15,
        classified_at=NOW - timedelta(days=220),
        approved_at=NOW - timedelta(days=215),
        next_review=TODAY + timedelta(days=210), rows=4_200)

    a['emp'] = asset(2, "DA-ATC-EMP-005",
        name_en="Trader & Staff HR Master",
        name_ar="السجل الرئيسي للموارد البشرية (المتداولون والموظفون)",
        desc_en="Employee master incl. trader mandates, PA-dealing declarations, compensation, Iqama.",
        desc_ar="السجل الرئيسي للموظفين بما يشمل تفويضات المتداولين، إقرارات التداول الشخصي، التعويضات، الإقامة.",
        domain="EMPLOYEE",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGAL_OBLIGATION_LABOUR",
        host="SAP SuccessFactors (KSA)", retention=7,
        classified_at=NOW - timedelta(days=210),
        approved_at=NOW - timedelta(days=205),
        next_review=TODAY + timedelta(days=150), rows=640)

    a['mkt'] = asset(2, "DA-ATC-MKT-006",
        name_en="Market Data Feeds (Refinitiv + Platts + ICE)",
        name_ar="تغذيات بيانات السوق (ريفينيتيف وبلاتس وآيس)",
        desc_en="Vendor-licensed price assessments, forward curves, freight indices.",
        desc_ar="تقييمات الأسعار ومنحنيات الآجل ومؤشّرات الشحن المرخّصة من المزوّدين.",
        domain="MARKET_DATA",
        classification="INTERNAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE",
        host="Trading floor workstation", retention=3,
        classified_at=NOW - timedelta(days=190),
        approved_at=NOW - timedelta(days=185),
        next_review=TODAY + timedelta(days=140), rows=26_000_000)

    a['price'] = asset(2, "DA-ATC-PRC-007",
        name_en="Published Price Assessments (Public)",
        name_ar="تقييمات الأسعار المنشورة (عام)",
        desc_en="Public-facing daily price assessments published to the corporate website.",
        desc_ar="تقييمات الأسعار اليومية المنشورة على الموقع المؤسسي للعموم.",
        domain="OPERATIONAL",
        classification="PUBLIC", status="IN_USE",
        lawful_basis="LEGITIMATE_INTEREST_DISCLOSURE",
        host="Public website CMS", retention=5,
        enc_rest=False,
        classified_at=NOW - timedelta(days=300),
        approved_at=NOW - timedelta(days=295),
        next_review=TODAY + timedelta(days=480), rows=240)

    # ─── Flows ──────────────────────────────────────────────────────

    flow(2, a['trade'], "DF-ATC-TRD-AZURE-001",
        src="Eka TRM",
        dest="Azure Synapse (KSA Central — Riyadh)",
        country="SA", adequacy="IN_KINGDOM", mode="BATCH", status="ACTIVE",
        purpose_en="Nightly position + P&L analytics warehouse load (in-Kingdom).",
        purpose_ar="تحميل ليلي لمستودع تحليلات المراكز والأرباح والخسائر (داخل المملكة).",
        approved_at=NOW - timedelta(days=200),
        activated=TODAY - timedelta(days=195))

    flow(2, a['cpty'], "DF-ATC-CPT-EKAIN-001",
        src="Eka TRM",
        dest="Eka Software Support (Bengaluru, India)",
        country="IN", adequacy="NON_ADEQUATE", mode="REPLICATION",
        status="SUSPENDED",
        purpose_en="Vendor L3 support DB replica incl. counterparty KYC (SUSPENDED — primes AR-285 cross-border CONFIDENTIAL).",
        purpose_ar="نسخة قاعدة بيانات لدعم المزوّد من المستوى الثالث تشمل اعرف عميلك للأطراف المقابلة (مُعلَّق — يُفعّل AR-285 لنقل سرّي عبر الحدود).")

    flow(2, a['mkt'], "DF-ATC-MKT-REF-001",
        src="Refinitiv Eikon (London)",
        dest="Trading floor workstation",
        country="GB", adequacy="NON_ADEQUATE", mode="STREAMING",
        status="ACTIVE",
        purpose_en="Inbound licensed market-data stream (no personal data; vendor feed).",
        purpose_ar="تدفّق بيانات سوق مرخّص وارد (لا بيانات شخصية؛ تغذية مزوّد).",
        approved_at=NOW - timedelta(days=300),
        activated=TODAY - timedelta(days=295))

    # ─── Exceptions ─────────────────────────────────────────────────

    exception(2, "EXC-2026-ATC-001",
        asset_id=a['cpty'], etype="CROSS_BORDER_TEMP",
        status="REQUESTED",
        just_en="Temporary cross-border waiver requested for Eka India L3 support to debug a counterparty-limit calculation defect on the live book.",
        just_ar="استثناء مؤقت لنقل عبر الحدود مطلوب لدعم Eka في الهند (المستوى الثالث) لتصحيح خلل في احتساب حدود الأطراف المقابلة على الدفتر الحيّ.",
        comp_en="Read-only masked replica + 30-day TTL + DPO + CISO joint sign-off + access logged + India support under NDA.",
        comp_ar="نسخة مقنّعة للقراءة فقط + عمر ٣٠ يوماً + موافقة مشتركة من الحامي ومسؤول أمن المعلومات + تسجيل الوصول + دعم الهند تحت اتفاقية سرّية.",
        valid_from=TODAY + timedelta(days=5),
        valid_until=TODAY + timedelta(days=35))

    exception(2, "EXC-2026-ATC-002",
        asset_id=a['scada'], etype="ENCRYPTION_AT_REST_WAIVER",
        status="ACTIVE",
        just_en="OT historian writes high-frequency telemetry to a real-time ring buffer where at-rest encryption breaks deterministic latency SLAs for safety-instrumented loops.",
        just_ar="يكتب مؤرّخ التقنية التشغيلية بيانات قياس عالية التردّد إلى ذاكرة حلقية فورية حيث يكسر التشفير عند الراحة اتفاقيات زمن الاستجابة الحتميّة لحلقات السلامة.",
        comp_en="Air-gapped OT network + physical access control + full-disk encryption on the downstream historian archive + 24h ring-buffer TTL.",
        comp_ar="شبكة تقنية تشغيلية معزولة + تحكّم وصول مادي + تشفير القرص الكامل لأرشيف المؤرّخ التالي + عمر ذاكرة حلقية ٢٤ ساعة.",
        valid_from=TODAY - timedelta(days=160),
        valid_until=TODAY + timedelta(days=20),  # primes AR-288 reminder ladder
        approved_at=NOW - timedelta(days=158))


def seed_org3():
    """STC Bank — telco-banking digital-bank data estate."""
    print(">>> org=3 STC Bank — Data Classification + NDMO seed")
    a = {}

    a['cust'] = asset(3, "DA-STCB-CST-001",
        name_en="Wallet & USSD Customer Master",
        name_ar="السجل الرئيسي لعملاء المحفظة وخدمة *900#",
        desc_en="Digital-bank customer register: National ID/Iqama, MSISDN, device binding, wallet KYC tier.",
        desc_ar="سجل عملاء البنك الرقمي: الهوية الوطنية/الإقامة، رقم الجوال، ربط الجهاز، مستوى اعرف عميلك للمحفظة.",
        domain="CUSTOMER_PII",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGAL_OBLIGATION_AML",
        host="Core Wallet Platform (in-Kingdom)", retention=10,
        classified_at=NOW - timedelta(days=260),
        approved_at=NOW - timedelta(days=255),
        next_review=TODAY + timedelta(days=170),
        dpia_ref="DPIA-STCB-2025-001", rows=6_200_000)

    a['voice'] = asset(3, "DA-STCB-VOI-002",
        name_en="Voice Biometric Templates (USSD/IVR auth)",
        name_ar="نماذج البصمة الصوتية (مصادقة *900#/الردّ الصوتي)",
        desc_en="Speaker-verification voiceprint templates for USSD + IVR step-up authentication.",
        desc_ar="نماذج بصمة صوتية للتحقّق من المتحدّث لمصادقة *900# والردّ الصوتي التصاعدية.",
        domain="CUSTOMER_PII",
        classification="TOP_SECRET", status="IN_USE",
        special="BIOMETRIC",
        lawful_basis="EXPLICIT_CONSENT",
        host="Voice Biometric Engine (HSM-backed, in-Kingdom only)", retention=5,
        masking=True,
        classified_at=NOW - timedelta(days=150),
        approved_at=NOW - timedelta(days=145),
        next_review=TODAY + timedelta(days=110),
        dpia_ref="DPIA-STCB-2025-008", rows=2_900_000)

    a['txn'] = asset(3, "DA-STCB-TXN-003",
        name_en="Wallet Transaction Ledger",
        name_ar="سجل معاملات المحفظة",
        desc_en="Wallet + card + USSD transfer ledger across the digital-bank product set.",
        desc_ar="سجل تحويلات المحفظة والبطاقات وخدمة *900# عبر منتجات البنك الرقمي.",
        domain="FINANCIAL_TRANSACTION",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGAL_OBLIGATION_AML",
        host="Core Wallet Platform + Exadata DW", retention=10,
        classified_at=NOW - timedelta(days=250),
        approved_at=NOW - timedelta(days=245),
        next_review=TODAY + timedelta(days=180),
        dpia_ref="DPIA-STCB-2025-003", rows=1_100_000_000)

    a['aml'] = asset(3, "DA-STCB-AML-004",
        name_en="AML Case File — Wallet (STR/CTR)",
        name_ar="ملف قضايا غسل الأموال — المحفظة (تقارير العمليات المشبوهة/الكبيرة)",
        desc_en="Wallet-channel STR/CTR filings + KYC EDD evidence + SAFIU correspondence.",
        desc_ar="تقارير العمليات المشبوهة/الكبيرة لقناة المحفظة + أدلة العناية المُعزَّزة + مراسلات سافيو.",
        domain="AML_KYC",
        classification="TOP_SECRET", status="IN_USE",
        special="CRIMINAL",
        lawful_basis="LEGAL_OBLIGATION_AML",
        host="AML Case Management (in-Kingdom)", retention=10,
        masking=True,
        classified_at=NOW - timedelta(days=240),
        approved_at=NOW - timedelta(days=235),
        next_review=TODAY + timedelta(days=150),
        dpia_ref="DPIA-STCB-2025-005", rows=3_100)

    a['sim'] = asset(3, "DA-STCB-SIM-005",
        name_en="SIM / Device & Signalling Telemetry (SS7/SIGTRAN)",
        name_ar="بيانات قياس الشريحة والجهاز والإشارات (SS7/SIGTRAN)",
        desc_en="Network signalling telemetry used for SIM-swap + device-binding fraud controls.",
        desc_ar="بيانات قياس إشارات الشبكة المُستخدَمة لضوابط احتيال تبديل الشريحة وربط الجهاز.",
        domain="OPERATIONAL",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="LEGITIMATE_INTEREST_FRAUD_PREVENTION",
        host="Fraud Signalling Hub (in-Kingdom)", retention=2,
        classified_at=NOW - timedelta(days=200),
        approved_at=NOW - timedelta(days=195),
        next_review=TODAY - timedelta(days=6),  # overdue → primes AR-289
        rows=540_000_000)

    a['emp'] = asset(3, "DA-STCB-EMP-006",
        name_en="Employee HR Master",
        name_ar="السجل الرئيسي للموارد البشرية",
        desc_en="Employee master: Iqama, salary, performance, training history.",
        desc_ar="السجل الرئيسي للموظفين: الإقامة، الراتب، الأداء، تاريخ التدريب.",
        domain="EMPLOYEE",
        classification="CONFIDENTIAL", status="IN_USE",
        lawful_basis="CONTRACT_PERFORMANCE,LEGAL_OBLIGATION_LABOUR",
        host="Oracle HCM Cloud (KSA region)", retention=7,
        classified_at=NOW - timedelta(days=220),
        approved_at=NOW - timedelta(days=215),
        next_review=TODAY + timedelta(days=160), rows=1_450)

    a['fees'] = asset(3, "DA-STCB-FEE-007",
        name_en="Tariff & Fees Schedule (Public)",
        name_ar="جدول التعرفة والرسوم (عام)",
        desc_en="Published wallet + USSD tariff schedule — public app + website disclosure.",
        desc_ar="جدول تعرفة المحفظة وخدمة *900# المنشور — إفصاح التطبيق والموقع العام.",
        domain="OPERATIONAL",
        classification="PUBLIC", status="IN_USE",
        lawful_basis="LEGAL_OBLIGATION_CONSUMER_PROTECTION",
        host="Public app + website CMS", retention=5,
        enc_rest=False,
        classified_at=NOW - timedelta(days=300),
        approved_at=NOW - timedelta(days=295),
        next_review=TODAY + timedelta(days=500), rows=140)

    # ─── Flows ──────────────────────────────────────────────────────

    flow(3, a['voice'], "DF-STCB-VOI-VND-001",
        src="Voice Biometric Engine",
        dest="Speech-vendor cloud (US-East)",
        country="US", adequacy="NON_ADEQUATE", mode="STREAMING",
        status="PROPOSED",
        purpose_en="Proposed vendor-side anti-spoofing scoring of voiceprints (BLOCKED — primes AR-285 TOP_SECRET cross-border).",
        purpose_ar="تسجيل مضادّ للانتحال من جهة المزوّد لنماذج البصمة الصوتية (محظور — يُفعّل AR-285 لنقل سرّي للغاية عبر الحدود).")

    flow(3, a['txn'], "DF-STCB-TXN-AZURE-001",
        src="Core Wallet Platform",
        dest="Azure Synapse (KSA Central — Riyadh)",
        country="SA", adequacy="IN_KINGDOM", mode="BATCH", status="ACTIVE",
        purpose_en="Nightly anonymised transaction batch for fraud + churn analytics (in-Kingdom).",
        purpose_ar="دفعة ليلية مُجهَّلة للمعاملات لتحليلات الاحتيال والتخارج (داخل المملكة).",
        approved_at=NOW - timedelta(days=180),
        activated=TODAY - timedelta(days=175))

    flow(3, a['aml'], "DF-STCB-AML-SAFIU-001",
        src="AML Case Management",
        dest="SAFIU (Saudi FIU)",
        country="SA", adequacy="IN_KINGDOM", mode="FILE_TRANSFER",
        status="ACTIVE",
        purpose_en="STR/CTR filing per SAMA AML/CFT Manual.",
        purpose_ar="رفع تقارير العمليات المشبوهة والكبيرة وفق دليل ساما لمكافحة غسل الأموال.",
        approved_at=NOW - timedelta(days=260),
        activated=TODAY - timedelta(days=255))

    # ─── Exceptions ─────────────────────────────────────────────────

    exception(3, "EXC-2026-STCB-001",
        asset_id=a['txn'], etype="MASKING_WAIVER",
        status="ACTIVE",
        just_en="Fraud-analytics cohort joins require an unmasked device-binding key to correlate SIM-swap events with wallet transactions in near-real-time.",
        just_ar="ربط مجموعات تحليلات الاحتيال يتطلّب مفتاح ربط جهاز غير مقنّع لربط أحداث تبديل الشريحة بمعاملات المحفظة شبه الفورية.",
        comp_en="Restricted to 8 named fraud analysts + key rotated monthly + 60-day cohort TTL + full access audit.",
        comp_ar="مقصور على ٨ محلّلي احتيال مسمّين + تدوير المفتاح شهرياً + عمر مجموعة ٦٠ يوماً + تدقيق وصول كامل.",
        valid_from=TODAY - timedelta(days=75),
        valid_until=TODAY + timedelta(days=15),  # primes AR-288 reminder ladder
        approved_at=NOW - timedelta(days=73))

    exception(3, "EXC-2026-STCB-002",
        asset_id=a['voice'], flow_id=None, etype="CROSS_BORDER_TEMP",
        status="REQUESTED",
        just_en="Time-boxed PoC waiver requested to benchmark the US speech vendor's anti-spoofing engine on a 5,000-template sample before any production cross-border decision.",
        just_ar="استثناء محدّد المدّة لإثبات مفهوم مطلوب لقياس أداء محرّك المزوّد الأمريكي المضادّ للانتحال على عيّنة ٥٬٠٠٠ نموذج قبل أي قرار نقل إنتاجي عبر الحدود.",
        comp_en="Synthetic + consented samples only, no production templates + 21-day TTL + DPO + CISO + SDAIA notification + delete-on-completion attestation.",
        comp_ar="عيّنات مُصطنَعة وبموافقة فقط، دون نماذج إنتاجية + عمر ٢١ يوماً + الحامي + مسؤول أمن المعلومات + إشعار سدايا + إقرار حذف عند الانتهاء.",
        valid_from=TODAY + timedelta(days=7),
        valid_until=TODAY + timedelta(days=28))


def main():
    print(">>> Sprint 32 PR #4 — Saudi-realistic data classification + NDMO seed")
    try:
        seed_org1()
        seed_org2()
        seed_org3()
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
        cur.execute(
            f"SELECT organisation_id, COUNT(*) FROM {tbl} WHERE is_deleted = 0 "
            "GROUP BY organisation_id ORDER BY organisation_id")
        by_org = " ".join(f"org{r[0]}={r[1]}" for r in cur.fetchall())
        print(f"+ {label:<12s}: {by_org}")
    print(">>> done.")


if __name__ == "__main__":
    main()
