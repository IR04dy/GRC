"""Seed Saudi-realistic Recovery & Resolution Plan cycles for 3 tenants.

Sprint 17 PR #4. Pairs with PRs #245 (backend), #246 (F8 rules),
#247 (frontend).

Each tenant gets cycles + a tenant-shaped catalog of options,
critical functions, and indicators reflecting real SAMA D-SIB
expectations:

  org=1  TSS GRC          — full Saudi-bank stack:
                              FY2025 CLOSED with SAMA ack ref
                              FY2026 IN_PREPARATION (one AMBER
                               indicator exercising AR-225/AR-227)
                              8 options × 12 critical functions ×
                              7 indicators
  org=2  Aramco Trading   — CMA-listed trading firm
                              FY2026 IN_PREPARATION
                              4 options × 6 critical functions ×
                              5 indicators (market/profitability focus)
  org=3  STC Bank         — digital-bank profile
                              FY2025 CLOSED
                              FY2026 IN_PREPARATION
                              6 options × 8 critical functions ×
                              6 indicators (digital-channel emphasis)

Bilingual EN+AR operator-natural throughout.

Idempotent: each (org, reference) and (org, cycle, code) pair guarded
by existence check.

Usage:
  python setup_rrp_seed.py
"""
from __future__ import annotations

import sys
from datetime import date, datetime, timedelta
from decimal import Decimal

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = False
cur = conn.cursor()

NOW = datetime.now()
TODAY = date.today()


def cycle_exists(org, ref):
    cur.execute(
        'SELECT id FROM rrp.rrp_cycle '
        'WHERE organisation_id = ? AND reference = ? AND is_deleted = 0',
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_cycle(org, ref, fy, title_en, title_ar, status,
                  submission_due, submitted_at=None, ack_ref=None,
                  acknowledged_at=None, closed_at=None,
                  board_date=None, board_ref=None,
                  exec_en=None, exec_ar=None, artifact_ref=None):
    existing = cycle_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        'INSERT INTO rrp.rrp_cycle '
        '(organisation_id, reference, fiscal_year, title_en, title_ar, status, '
        ' submission_due_date, submitted_at, regulator_ack_reference, '
        ' acknowledged_at, closed_at, board_approval_date, board_resolution_reference, '
        ' executive_summary_en, executive_summary_ar, filing_artifact_reference, '
        ' created_at, updated_at) '
        'OUTPUT INSERTED.id '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org, ref, fy, title_en, title_ar, status,
        submission_due, submitted_at, ack_ref, acknowledged_at,
        closed_at, board_date, board_ref,
        exec_en, exec_ar, artifact_ref, NOW, NOW,
    )
    return int(cur.fetchone()[0])


def option_exists(org, cycle_id, code):
    cur.execute(
        'SELECT id FROM rrp.rrp_recovery_option '
        'WHERE organisation_id = ? AND cycle_id = ? AND option_code = ? '
        'AND is_deleted = 0',
        org, cycle_id, code,
    )
    return cur.fetchone() is not None


def insert_option(org, cycle_id, code, category, title_en, title_ar,
                   capacity, days, feasibility,
                   impact_en, impact_ar, role):
    if option_exists(org, cycle_id, code):
        return
    cur.execute(
        'INSERT INTO rrp.rrp_recovery_option '
        '(organisation_id, cycle_id, option_code, category, title_en, title_ar, '
        ' capacity_sar_m, time_to_execute_days, feasibility, '
        ' impact_en, impact_ar, accountable_role, created_at, updated_at) '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org, cycle_id, code, category, title_en, title_ar,
        capacity, days, feasibility, impact_en, impact_ar, role, NOW, NOW,
    )


def cf_exists(org, cycle_id, code):
    cur.execute(
        'SELECT id FROM rrp.rrp_critical_function '
        'WHERE organisation_id = ? AND cycle_id = ? AND function_code = ? '
        'AND is_deleted = 0',
        org, cycle_id, code,
    )
    return cur.fetchone() is not None


def insert_cf(org, cycle_id, code, title_en, title_ar, materiality,
               mtpd, customers, volume, owner_role,
               continuity_en, continuity_ar):
    if cf_exists(org, cycle_id, code):
        return
    cur.execute(
        'INSERT INTO rrp.rrp_critical_function '
        '(organisation_id, cycle_id, function_code, title_en, title_ar, '
        ' materiality, max_tolerable_downtime_hours, customer_count_estimate, '
        ' daily_volume_estimate, owner_role, continuity_strategy_en, '
        ' continuity_strategy_ar, created_at, updated_at) '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org, cycle_id, code, title_en, title_ar, materiality,
        mtpd, customers, volume, owner_role,
        continuity_en, continuity_ar, NOW, NOW,
    )


def ind_exists(org, cycle_id, code):
    cur.execute(
        'SELECT id FROM rrp.rrp_indicator '
        'WHERE organisation_id = ? AND cycle_id = ? AND indicator_code = ? '
        'AND is_deleted = 0',
        org, cycle_id, code,
    )
    return cur.fetchone() is not None


def compute_band(direction, green, amber, value):
    if value is None or green is None or amber is None:
        return 'GREEN'
    higher = direction == 'HIGHER_IS_BETTER'
    if higher:
        if value >= green: return 'GREEN'
        if value >= amber: return 'AMBER'
        return 'RED'
    else:
        if value <= green: return 'GREEN'
        if value <= amber: return 'AMBER'
        return 'RED'


def insert_indicator(org, cycle_id, code, category, title_en, title_ar,
                      unit, direction, green, amber, value,
                      data_source, owner_role, measured_at=None):
    if ind_exists(org, cycle_id, code):
        return
    band = compute_band(direction, green, amber, value)
    cur.execute(
        'INSERT INTO rrp.rrp_indicator '
        '(organisation_id, cycle_id, indicator_code, category, title_en, title_ar, '
        ' unit, direction, green_threshold, amber_threshold, current_value, '
        ' current_band, measured_at, data_source, owner_role, '
        ' created_at, updated_at) '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org, cycle_id, code, category, title_en, title_ar,
        unit, direction,
        Decimal(str(green)) if green is not None else None,
        Decimal(str(amber)) if amber is not None else None,
        Decimal(str(value)) if value is not None else None,
        band, measured_at, data_source, owner_role,
        NOW, NOW,
    )


def recount_cycle(org, cycle_id):
    cur.execute(
        'SELECT COUNT(*) FROM rrp.rrp_recovery_option '
        'WHERE organisation_id = ? AND cycle_id = ? AND is_deleted = 0',
        org, cycle_id)
    opts = int(cur.fetchone()[0])
    cur.execute(
        'SELECT COUNT(*) FROM rrp.rrp_critical_function '
        'WHERE organisation_id = ? AND cycle_id = ? AND is_deleted = 0',
        org, cycle_id)
    cfs = int(cur.fetchone()[0])
    cur.execute(
        'SELECT COUNT(*) FROM rrp.rrp_indicator '
        'WHERE organisation_id = ? AND cycle_id = ? AND is_deleted = 0',
        org, cycle_id)
    inds = int(cur.fetchone()[0])
    cur.execute(
        "SELECT COUNT(*) FROM rrp.rrp_indicator "
        "WHERE organisation_id = ? AND cycle_id = ? AND current_band = 'AMBER' "
        "AND is_deleted = 0",
        org, cycle_id)
    amber = int(cur.fetchone()[0])
    cur.execute(
        "SELECT COUNT(*) FROM rrp.rrp_indicator "
        "WHERE organisation_id = ? AND cycle_id = ? AND current_band = 'RED' "
        "AND is_deleted = 0",
        org, cycle_id)
    red = int(cur.fetchone()[0])
    cur.execute(
        'SELECT COALESCE(SUM(capacity_sar_m), 0) FROM rrp.rrp_recovery_option '
        'WHERE organisation_id = ? AND cycle_id = ? AND is_deleted = 0',
        org, cycle_id)
    capacity = int(cur.fetchone()[0])
    cur.execute(
        'UPDATE rrp.rrp_cycle SET '
        ' recovery_option_count = ?, critical_function_count = ?, '
        ' indicator_count = ?, indicators_amber = ?, indicators_red = ?, '
        ' total_recovery_capacity_sar_m = ?, updated_at = ? '
        'WHERE id = ?',
        opts, cfs, inds, amber, red, capacity, NOW, cycle_id,
    )


# ─── TSS GRC (org=1) — full Saudi-bank stack ────────────────────

def seed_tss_grc():
    print("\n>>> Seeding TSS GRC (org=1) — Saudi-bank RRP")
    org = 1
    last_year = TODAY.year - 1
    this_year = TODAY.year

    fy25_id = insert_cycle(
        org, f'RRP-FY{last_year}-001', last_year,
        f'FY{last_year} Recovery & Resolution Plan',
        f'خطّة الاستعادة والحلّ للسنة المالية {last_year}',
        'CLOSED',
        date(last_year, 3, 31),
        submitted_at=datetime(last_year, 3, 22, 14, 30),
        ack_ref=f'SAMA-RRP-{last_year}-00342',
        acknowledged_at=datetime(last_year, 3, 28, 10, 0),
        closed_at=datetime(last_year, 4, 15, 16, 0),
        board_date=date(last_year, 3, 18),
        board_ref=f'BoD-RES-{last_year}-014',
        exec_en='CET1 ratio 14.8%, recovery capacity SAR 12.4bn, '
                'liquidity buffer 1.6x. Plan reviewed in 3 scenarios; '
                'all options pre-rehearsed at Board Risk Committee.',
        exec_ar='كفاية رأس المال 14.8%، طاقة الاستعادة 12.4 مليار ريال، '
                'وسادة السيولة 1.6 ضعف. روجعت الخطّة في 3 سيناريوهات؛ '
                'كل الخيارات بُروفت في لجنة المخاطر بالمجلس.',
        artifact_ref=f's3://compliance/rrp/{last_year}/rrp-fy{last_year}.pdf',
    )

    fy26_id = insert_cycle(
        org, f'RRP-FY{this_year}-001', this_year,
        f'FY{this_year} Recovery & Resolution Plan',
        f'خطّة الاستعادة والحلّ للسنة المالية {this_year}',
        'IN_PREPARATION',
        date(this_year, 3, 31),
    )

    # Options × 2 cycles (same menu, FY2026 reassessed capacity)
    for cycle_id, year_shift in [(fy25_id, 0), (fy26_id, 1)]:
        # FY2026 capacities tweaked
        m = (lambda v: v if year_shift == 0 else int(v * 0.95))
        opts = [
            ('OPT-001', 'CAPITAL', 'Rights issue (1:4 ratio)',
             'إصدار حقوق أولوية (1:4)',
             m(4500), 90, 'HIGH', '+2.1pp on CET1',
             '+2.1 نقطة مئوية على كفاية رأس المال', 'CFO'),
            ('OPT-002', 'CAPITAL', 'AT1 sukuk issuance',
             'إصدار صكوك الفئة الأولى الإضافية',
             m(2000), 60, 'HIGH', '+0.9pp on Total CAR',
             '+0.9 نقطة مئوية على إجمالي كفاية رأس المال', 'CFO'),
            ('OPT-003', 'CAPITAL', 'Dividend suspension',
             'تعليق توزيع الأرباح',
             m(1200), 30, 'HIGH', '+0.5pp on CET1',
             '+0.5 نقطة مئوية على كفاية رأس المال', 'CFO'),
            ('OPT-004', 'LIQUIDITY', 'SAMA repo facility activation',
             'تفعيل تسهيلات الريبو لدى ساما',
             m(3000), 5, 'HIGH', '+30pp on LCR',
             '+30 نقطة مئوية على تغطية السيولة', 'TREASURY'),
            ('OPT-005', 'LIQUIDITY', 'High-yield deposit campaign',
             'حملة ودائع عالية العائد',
             m(1500), 21, 'MEDIUM', '+10pp on LCR + funding stickiness',
             '+10 نقاط مئوية على تغطية السيولة + ثبات تمويل', 'TREASURY'),
            ('OPT-006', 'ASSET_SALE', 'SAR portfolio sale to peer banks',
             'بيع محفظة ريالية للبنوك المحلية',
             m(2500), 45, 'MEDIUM', '+1.4pp on CET1 via RWA reduction',
             '+1.4 نقطة مئوية على كفاية رأس المال عبر تخفيض الأصول المُرجَّحة بالمخاطر',
             'RISK_MANAGER'),
            ('OPT-007', 'BUSINESS_LINE', 'Exit international correspondent banking',
             'الخروج من الخدمات المصرفية المراسلة الدولية',
             m(800), 180, 'LOW', 'SAR 0.3pp RWA, SAR 50m annualised expense saving',
             '0.3 نقطة مئوية أصول مُرجَّحة، وفر 50 مليون ريال سنوياً', 'CEO'),
            ('OPT-008', 'EXPENSE', 'Discretionary expense + headcount freeze',
             'تجميد المصروفات التقديرية والتوظيف',
             m(400), 14, 'HIGH', 'SAR 400m annualised',
             '400 مليون ريال سنوياً', 'CFO'),
        ]
        for o in opts:
            insert_option(org, cycle_id, *o)

        cfs = [
            ('CF-001', 'Retail deposit access (ATM/branch/mobile)',
             'وصول الإيداع للأفراد (الصرافات والفروع والجوّال)',
             'SYSTEMIC', 4, 4_200_000, 8_500_000, 'COO',
             'Dual-region active-active core banking + ATM cash-replenishment with 24h SLA',
             'نواة بنكية ثنائية النشاط بين منطقتين + إعادة تعبئة الصرّافات بمدّة 24 ساعة'),
            ('CF-002', 'SADAD bill payments',
             'سداد المدفوعات',
             'SYSTEMIC', 2, 3_800_000, 12_000_000, 'HEAD_PAYMENTS',
             'Active-active integration with SAMA SADAD switch',
             'تكامل ثنائي النشاط مع مفتاح سداد لدى ساما'),
            ('CF-003', 'IPS instant transfers',
             'التحويلات اللحظية IPS',
             'SYSTEMIC', 1, 4_500_000, 25_000_000, 'HEAD_PAYMENTS',
             'Real-time fail-over to backup BIN, 60-second cutover',
             'تبديل تلقائي لرقم BIN احتياطي خلال 60 ثانية'),
            ('CF-004', 'Government salary processing',
             'معالجة رواتب الحكومة',
             'SYSTEMIC', 12, 1_800_000, 28_000_000, 'HEAD_PAYMENTS',
             'Pre-staged disbursement file at SAMA, manual release fallback',
             'ملف صرف معدّ مسبقاً لدى ساما، إفراج يدوي كاحتياط'),
            ('CF-005', 'Trade-finance L/C continuity',
             'استمرارية خطابات الاعتماد التجارية',
             'HIGH', 48, 8500, 1200, 'HEAD_TRADE_FINANCE',
             'Correspondent network with 2 backup processors',
             'شبكة المراسلين مع معالجَين احتياطيَّين'),
            ('CF-006', 'REDF mortgage payment collection',
             'تحصيل أقساط التمويل العقاري لصندوق التنمية العقاري',
             'HIGH', 24, 285_000, 285_000, 'HEAD_RETAIL_BANKING',
             'Direct-debit mandate with REDF settlement, paper backup',
             'تفويض الخصم المباشر مع تسوية الصندوق، احتياط ورقي'),
            ('CF-007', 'Treasury / FX market-making SAR-USD',
             'صناعة السوق للخزانة وصرف ريال-دولار',
             'HIGH', 8, 0, 0, 'HEAD_TREASURY',
             'Secondary-site FX dealing room with 4h activation',
             'غرفة تداول صرف بديلة بمدّة تفعيل 4 ساعات'),
            ('CF-008', 'Card-present transaction processing',
             'معالجة معاملات البطاقات الحاضرة',
             'SYSTEMIC', 2, 4_500_000, 18_000_000, 'HEAD_CARDS',
             'PCI-compliant secondary acquirer with 30-min cutover',
             'مُكتسِب احتياطي متوافق مع PCI بمدّة تبديل 30 دقيقة'),
            ('CF-009', 'Card-not-present + e-commerce processing',
             'معاملات البطاقات غير الحاضرة والتجارة الإلكترونية',
             'HIGH', 4, 3_200_000, 9_500_000, 'HEAD_CARDS',
             '3DS fail-over to backup ACS + token vault replication',
             'تبديل 3DS لخادم احتياطي + نسخ خزانة الرموز'),
            ('CF-010', 'Mobile banking app',
             'تطبيق المصرف الجوّال',
             'HIGH', 6, 3_900_000, 22_000_000, 'CIO',
             'Mobile-app gateway with multi-region edge + offline mode',
             'بوّابة التطبيق متعدّدة المناطق مع وضع غير متّصل'),
            ('CF-011', 'Wealth-management custody',
             'حفظ إدارة الثروات',
             'MEDIUM', 24, 18_500, 12_000, 'HEAD_WEALTH',
             'Edaa CSD link + backup custody contract',
             'ارتباط مع مركز الإيداع + عقد حفظ احتياطي'),
            ('CF-012', 'Corporate-banking lending operations',
             'عمليات الإقراض المصرفي للشركات',
             'MEDIUM', 24, 2400, 1800, 'HEAD_CORPORATE',
             'Manual underwriting fallback + delegated authorities matrix',
             'احتياط الاكتتاب اليدوي + مصفوفة صلاحيات معتمدة'),
        ]
        for cf in cfs:
            insert_cf(org, cycle_id, *cf)

        # Indicators: FY25 all GREEN. FY26 has one AMBER (LCR drifted) to
        # exercise AR-225 + AR-227 (CAPITAL/LIQUIDITY + worsening).
        # CET1, LCR, NSFR, NPL, Cost of Risk, ROE, Deposit-runoff
        inds_fy25 = [
            ('IND-CET1', 'CAPITAL', 'Common Equity Tier 1 ratio',
             'كفاية رأس المال من الفئة الأولى', '%', 'HIGHER_IS_BETTER',
             13.0, 11.0, 14.8, 'SAMA Pillar 3 datamart', 'CRO'),
            ('IND-LCR', 'LIQUIDITY', 'Liquidity Coverage Ratio',
             'نسبة تغطية السيولة', '%', 'HIGHER_IS_BETTER',
             120.0, 110.0, 145.0, 'Treasury daily LCR report', 'TREASURY'),
            ('IND-NSFR', 'LIQUIDITY', 'Net Stable Funding Ratio',
             'نسبة صافي التمويل المستقر', '%', 'HIGHER_IS_BETTER',
             110.0, 105.0, 118.0, 'Treasury NSFR report', 'TREASURY'),
            ('IND-NPL', 'ASSET_QUALITY', 'Non-Performing Loans ratio',
             'نسبة القروض غير العاملة', '%', 'LOWER_IS_BETTER',
             3.0, 5.0, 2.1, 'Credit risk monthly close', 'CRO'),
            ('IND-COR', 'PROFITABILITY', 'Cost of Risk',
             'تكلفة المخاطر', 'bps', 'LOWER_IS_BETTER',
             80, 150, 62, 'Finance datamart', 'CFO'),
            ('IND-ROE', 'PROFITABILITY', 'Return on Equity',
             'العائد على حقوق المساهمين', '%', 'HIGHER_IS_BETTER',
             10.0, 7.0, 13.5, 'Finance datamart', 'CFO'),
            ('IND-DRO', 'LIQUIDITY', 'Deposit run-off (30-day)',
             'انسحاب الودائع خلال 30 يوماً', '%', 'LOWER_IS_BETTER',
             5.0, 10.0, 2.8, 'Treasury liquidity dashboard', 'TREASURY'),
        ]
        if year_shift == 0:
            inds = inds_fy25
        else:
            # FY26 — LCR drifted to AMBER (115% vs 120 green / 110 amber)
            inds = list(inds_fy25)
            inds[1] = ('IND-LCR', 'LIQUIDITY', 'Liquidity Coverage Ratio',
                        'نسبة تغطية السيولة', '%', 'HIGHER_IS_BETTER',
                        120.0, 110.0, 115.0, 'Treasury daily LCR report', 'TREASURY')
        for i in inds:
            insert_indicator(org, cycle_id, *i,
                             measured_at=TODAY - timedelta(days=5))

        recount_cycle(org, cycle_id)

    print('  ✓ TSS GRC FY{} CLOSED + FY{} IN_PREPARATION seeded'.format(
        last_year, this_year))


# ─── Aramco Trading (org=2) — CMA-listed trader ─────────────────

def seed_aramco():
    print("\n>>> Seeding Aramco Trading (org=2) — RRP")
    org = 2
    this_year = TODAY.year

    fy26_id = insert_cycle(
        org, f'RRP-FY{this_year}-001', this_year,
        f'FY{this_year} Recovery Plan — Commodity Trading',
        f'خطّة الاستعادة لتجارة السلع — السنة المالية {this_year}',
        'IN_PREPARATION',
        date(this_year, 3, 31),
    )

    options = [
        ('OPT-001', 'CAPITAL', 'Shareholder capital injection',
         'ضخّ رأس مال من المساهمين',
         3500, 60, 'HIGH', '+1.5pp on regulatory capital',
         '+1.5 نقطة مئوية على رأس المال النظامي', 'CFO'),
        ('OPT-002', 'LIQUIDITY', 'Trade finance facility from parent',
         'تسهيل تمويل تجاري من الشركة الأم',
         2000, 14, 'HIGH', '+SAR 2bn working-capital headroom',
         '+ 2 مليار ريال مساحة لرأس المال العامل', 'TREASURY'),
        ('OPT-003', 'ASSET_SALE', 'Reduce open commodity exposure',
         'تقليل التعرّض المفتوح للسلع',
         1200, 30, 'MEDIUM', 'Risk-weighted exposure -25%, capital relief',
         'تقليل التعرّض المُرجَّح بالمخاطر بنسبة 25% وراحة لرأس المال',
         'HEAD_TRADING'),
        ('OPT-004', 'EXPENSE', 'Defer non-essential capex',
         'تأجيل النفقات الرأسمالية غير الضرورية',
         300, 7, 'HIGH', 'SAR 300m liquidity preserved',
         'الحفاظ على 300 مليون ريال من السيولة', 'CFO'),
    ]
    for o in options:
        insert_option(org, fy26_id, *o)

    cfs = [
        ('CF-001', 'Commodity trading desk operations',
         'عمليات مكتب تداول السلع',
         'HIGH', 2, 120, 850, 'HEAD_TRADING',
         'Backup trading floor + ETRM dual-region replication',
         'قاعة تداول احتياطية + نسخ ETRM ثنائي المنطقة'),
        ('CF-002', 'Commodity settlement (Eka TRM)',
         'تسوية السلع (نظام Eka)',
         'HIGH', 4, 120, 320, 'HEAD_SETTLEMENT',
         'Eka active-passive with 4h RTO',
         'Eka نشط-سلبي بمدّة استرداد 4 ساعات'),
        ('CF-003', 'Treasury cash management',
         'إدارة النقد بالخزانة',
         'HIGH', 8, 1, 200, 'TREASURY',
         'Secondary banking relationships + manual swift fallback',
         'علاقات مصرفية ثانوية + احتياط سويفت يدوي'),
        ('CF-004', 'Export documentation processing',
         'معالجة وثائق التصدير',
         'MEDIUM', 24, 80, 45, 'HEAD_EXPORT',
         'Paper-based fallback + cross-trained operators',
         'احتياط ورقي + مشغّلون مدرّبون متعدّدو المهام'),
        ('CF-005', 'Counterparty credit risk monitoring',
         'مراقبة مخاطر ائتمان الأطراف المقابلة',
         'MEDIUM', 24, 300, 600, 'CRO',
         'Tier-2 risk engine fallback + manual margin call workflow',
         'محرّك مخاطر الفئة الثانية + سير عمل يدوي لاستدعاء الهامش'),
        ('CF-006', 'Regulatory reporting (CMA, SAMA, ZATCA)',
         'البلاغات الرقابية (هيئة السوق، ساما، زاتكا)',
         'MEDIUM', 48, 0, 0, 'COMPLIANCE_OFFICER',
         'Manual filing workflow documented + redundant signers',
         'سير عمل تقديم يدوي موثّق + مفوّضون احتياطيون'),
    ]
    for cf in cfs:
        insert_cf(org, fy26_id, *cf)

    inds = [
        ('IND-REGCAP', 'CAPITAL', 'Regulatory capital ratio',
         'نسبة رأس المال النظامي', '%', 'HIGHER_IS_BETTER',
         18.0, 14.0, 21.5, 'Finance close', 'CFO'),
        ('IND-LIQ', 'LIQUIDITY', 'Cash + committed lines / 30-day outflows',
         'النقد والائتمانات الملتزمة / تدفّقات 30 يوماً', '%', 'HIGHER_IS_BETTER',
         150.0, 120.0, 178.0, 'Treasury dashboard', 'TREASURY'),
        ('IND-VAR', 'MARKET_CONFIDENCE', 'Value at Risk (95% 1d)',
         'القيمة المعرّضة للخطر (95% يوم واحد)', 'SAR M', 'LOWER_IS_BETTER',
         50, 100, 38, 'Risk engine', 'CRO'),
        ('IND-CPTY', 'OPERATIONAL', 'Top-20 counterparty concentration',
         'تركيز الأطراف المقابلة العشرين الكبرى', '%', 'LOWER_IS_BETTER',
         40.0, 55.0, 36.0, 'Credit risk weekly', 'CRO'),
        ('IND-MARGIN', 'PROFITABILITY', 'Net trading margin',
         'صافي هامش التداول', '%', 'HIGHER_IS_BETTER',
         3.0, 1.5, 4.2, 'Finance datamart', 'CFO'),
    ]
    for i in inds:
        insert_indicator(org, fy26_id, *i, measured_at=TODAY - timedelta(days=7))

    recount_cycle(org, fy26_id)
    print('  ✓ Aramco Trading FY{} seeded'.format(this_year))


# ─── STC Bank (org=3) — digital-bank profile ────────────────────

def seed_stc():
    print("\n>>> Seeding STC Bank (org=3) — digital-bank RRP")
    org = 3
    last_year = TODAY.year - 1
    this_year = TODAY.year

    fy25_id = insert_cycle(
        org, f'RRP-FY{last_year}-001', last_year,
        f'FY{last_year} Digital-Bank Recovery & Resolution Plan',
        f'خطّة الاستعادة والحلّ للمصرف الرقمي — السنة المالية {last_year}',
        'CLOSED',
        date(last_year, 3, 31),
        submitted_at=datetime(last_year, 3, 25, 11, 0),
        ack_ref=f'SAMA-RRP-{last_year}-00118',
        acknowledged_at=datetime(last_year, 4, 2, 9, 0),
        closed_at=datetime(last_year, 4, 20, 12, 0),
        board_date=date(last_year, 3, 20),
        board_ref=f'STC-BANK-BoD-{last_year}-009',
        exec_en='Digital-bank operating model — heavy mobile/USSD channel; '
                'critical-function inventory emphasises voice-biometric + IPS + '
                'mobile-money continuity. CET1 ratio 17.2%, liquidity buffer 1.85x.',
        exec_ar='نموذج تشغيل المصرف الرقمي — اعتماد كبير على الجوّال وUSSD؛ '
                'جرد الوظائف الحيوية يركّز على استمرارية البصمة الصوتية وIPS '
                'والمحفظة الجوّالة. كفاية رأس المال 17.2%، السيولة 1.85 ضعف.',
    )

    fy26_id = insert_cycle(
        org, f'RRP-FY{this_year}-001', this_year,
        f'FY{this_year} Digital-Bank Recovery & Resolution Plan',
        f'خطّة الاستعادة والحلّ للمصرف الرقمي — السنة المالية {this_year}',
        'IN_PREPARATION',
        date(this_year, 3, 31),
    )

    for cycle_id in [fy25_id, fy26_id]:
        options = [
            ('OPT-001', 'CAPITAL', 'Parent (STC Group) equity injection',
             'ضخّ رأس مال من الشركة الأم (مجموعة STC)',
             2500, 45, 'HIGH', '+2.5pp on CET1',
             '+2.5 نقطة مئوية على كفاية رأس المال', 'CFO'),
            ('OPT-002', 'CAPITAL', 'AT1 sukuk via parent guarantee',
             'صكوك الفئة الأولى بضمان الشركة الأم',
             1500, 60, 'MEDIUM', '+0.8pp on Total CAR',
             '+0.8 نقطة مئوية على إجمالي كفاية رأس المال', 'CFO'),
            ('OPT-003', 'LIQUIDITY', 'SAMA Sharia-compliant repo',
             'الريبو الإسلامي لدى ساما',
             1800, 5, 'HIGH', '+25pp on LCR',
             '+25 نقطة مئوية على تغطية السيولة', 'TREASURY'),
            ('OPT-004', 'LIQUIDITY', 'Mobile-money float monetisation',
             'تنقيد رصيد المحفظة الجوّالة',
             900, 10, 'MEDIUM', '+12pp on LCR + funding diversity',
             '+12 نقطة مئوية على تغطية السيولة + تنويع التمويل', 'TREASURY'),
            ('OPT-005', 'BUSINESS_LINE', 'USSD *900# corridor sale to peer',
             'بيع ممرّ USSD ‏*900# لمنافس',
             600, 120, 'LOW', 'Customer-base + tech-debt transfer; SAR 0.5pp RWA',
             'نقل قاعدة العملاء والدين الفنّي؛ 0.5 نقطة أصول مُرجَّحة', 'CEO'),
            ('OPT-006', 'EXPENSE', 'Marketing + customer-acquisition freeze',
             'تجميد التسويق واكتساب العملاء',
             350, 7, 'HIGH', 'SAR 350m annualised expense saving',
             'وفر مصروفات 350 مليون ريال سنوياً', 'CFO'),
        ]
        for o in options:
            insert_option(org, cycle_id, *o)

        cfs = [
            ('CF-001', 'USSD *900# transaction channel',
             'قناة معاملات USSD ‏*900#',
             'SYSTEMIC', 1, 2_800_000, 9_500_000, 'CIO',
             'SS7/SIGTRAN dual-region with 60s fail-over',
             'SS7/SIGTRAN ثنائي المنطقة مع تبديل 60 ثانية'),
            ('CF-002', 'Voice-biometric authentication',
             'مصادقة البصمة الصوتية',
             'SYSTEMIC', 2, 3_200_000, 14_000_000, 'CISO',
             'Anti-spoofing AI fail-open to OTP + tap-and-pay backup',
             'تبديل الذكاء الاصطناعي المضاد للانتحال إلى OTP والدفع باللمس'),
            ('CF-003', 'Mobile-money wallet (Tap-and-Pay)',
             'محفظة الأموال الجوّالة (الدفع باللمس)',
             'SYSTEMIC', 2, 4_000_000, 18_500_000, 'HEAD_MOBILE_MONEY',
             'NFC HSM cluster with active-active + token vault',
             'حشد HSM للـ NFC نشط-نشط مع خزانة الرموز'),
            ('CF-004', 'IPS instant transfers',
             'التحويلات اللحظية IPS',
             'SYSTEMIC', 1, 3_500_000, 12_500_000, 'HEAD_PAYMENTS',
             'IPS dual-BIN fail-over with 60-second cutover',
             'IPS تبديل BIN ثنائي بمدّة 60 ثانية'),
            ('CF-005', 'Mobile-banking app',
             'تطبيق المصرف الجوّال',
             'SYSTEMIC', 2, 4_100_000, 28_000_000, 'CIO',
             'Multi-CDN edge + offline mode + push-notification backup',
             'CDN حافة متعدّدة + وضع غير متّصل + إشعارات احتياطية'),
            ('CF-006', 'Telco-banking integration (SIM card)',
             'تكامل المصرف مع الاتصالات (شريحة الاتصال)',
             'HIGH', 4, 4_200_000, 0, 'HEAD_PARTNERSHIPS',
             'STC OSS/BSS active-active with cross-domain fail-over',
             'STC OSS/BSS نشط-نشط مع تبديل بين النطاقات'),
            ('CF-007', 'HSM key-rotation infrastructure',
             'بنية تدوير مفاتيح HSM',
             'HIGH', 4, 0, 0, 'CISO',
             '3-node HSM cluster + quorum key custodians',
             'حشد HSM من 3 عقد + حراس مفاتيح بالنصاب'),
            ('CF-008', 'Government salary processing (digital-only)',
             'معالجة الرواتب الحكومية (رقمياً فقط)',
             'SYSTEMIC', 6, 850_000, 3_400_000, 'HEAD_PAYMENTS',
             'Pre-staged disbursement at SAMA + push to mobile wallet',
             'صرف مُجهّز مسبقاً لدى ساما + دفع للمحفظة الجوّالة'),
        ]
        for cf in cfs:
            insert_cf(org, cycle_id, *cf)

        inds = [
            ('IND-CET1', 'CAPITAL', 'CET1 ratio',
             'كفاية رأس المال من الفئة الأولى', '%', 'HIGHER_IS_BETTER',
             13.0, 11.0, 17.2, 'SAMA Pillar 3', 'CRO'),
            ('IND-LCR', 'LIQUIDITY', 'LCR',
             'نسبة تغطية السيولة', '%', 'HIGHER_IS_BETTER',
             120.0, 110.0, 162.0, 'Treasury daily', 'TREASURY'),
            ('IND-NPL', 'ASSET_QUALITY', 'NPL ratio',
             'نسبة القروض غير العاملة', '%', 'LOWER_IS_BETTER',
             3.0, 5.0, 1.8, 'Credit risk', 'CRO'),
            ('IND-USSD-UP', 'OPERATIONAL', 'USSD *900# uptime',
             'جاهزية قناة USSD ‏*900#', '%', 'HIGHER_IS_BETTER',
             99.95, 99.5, 99.97, 'Network telemetry', 'CIO'),
            ('IND-MOBILE-UP', 'OPERATIONAL', 'Mobile app availability',
             'جاهزية تطبيق الجوّال', '%', 'HIGHER_IS_BETTER',
             99.9, 99.5, 99.92, 'App telemetry', 'CIO'),
            ('IND-FRAUD', 'OPERATIONAL', 'Fraud loss rate (bps of volume)',
             'نسبة خسائر الاحتيال (نقطة أساس من الحجم)', 'bps', 'LOWER_IS_BETTER',
             5, 10, 3.2, 'Fraud monitoring', 'CISO'),
        ]
        for i in inds:
            insert_indicator(org, cycle_id, *i, measured_at=TODAY - timedelta(days=4))

        recount_cycle(org, cycle_id)

    print('  ✓ STC Bank FY{} CLOSED + FY{} IN_PREPARATION seeded'.format(
        last_year, this_year))


# ─── Main ────────────────────────────────────────────────────────

def main() -> None:
    print(">>> Seeding RRP cycles + catalogs for 3 tenants")
    try:
        seed_tss_grc()
        seed_aramco()
        seed_stc()
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"! seed failed: {e}")
        sys.exit(1)

    cur.execute('SELECT organisation_id, COUNT(*) FROM rrp.rrp_cycle '
                'WHERE is_deleted = 0 GROUP BY organisation_id '
                'ORDER BY organisation_id')
    print('\n--- Cycles per org:')
    for row in cur.fetchall():
        print(f'   org={row[0]}: {row[1]} cycles')

    for label, table in [
            ('Recovery options', 'rrp.rrp_recovery_option'),
            ('Critical functions', 'rrp.rrp_critical_function'),
            ('Indicators', 'rrp.rrp_indicator')]:
        cur.execute(f'SELECT COUNT(*) FROM {table} WHERE is_deleted = 0')
        print(f'--- {label}: {cur.fetchone()[0]}')

    cur.execute("SELECT current_band, COUNT(*) FROM rrp.rrp_indicator "
                "WHERE is_deleted = 0 GROUP BY current_band ORDER BY current_band")
    print('\n--- Indicators by band:')
    for row in cur.fetchall():
        print(f'   {row[0]}: {row[1]}')

    print('\n✓ done')


if __name__ == '__main__':
    main()
