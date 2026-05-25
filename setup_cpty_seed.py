"""Seed Saudi-realistic counterparty registry across 3 tenants.

Sprint 18 PR #4. Pairs with PRs #250 (backend), #251 (F8 rules),
#252 (frontend).

Each tenant gets a tenant-shaped portfolio reflecting real Saudi
credit-risk profiles:

  org=1  TSS GRC          — full corporate-bank profile:
                              Sovereign + GRE (Aramco / SABIC / PIF /
                              REDF / SEC / Saudia), Saudi banks
                              (SNB / Al Rajhi / Riyad / ANB), family
                              conglomerates (Olayan / Zamil / Almarai),
                              SME corporates, multilateral (IsDB).
                              One BREACH limit deliberately seeded
                              to demo AR-238 SAMA-reportable event.
  org=2  Aramco Trading   — commodity-trading counterparties:
                              Aramco subsidiaries, commodity trading
                              houses, refiners (Luberef / Petrochem),
                              global oil majors as counterparties.
  org=3  STC Bank         — digital-bank corporate book:
                              government salary employers, fintechs,
                              telco partners, retail-aggregate exposure.

Each counterparty carries: internal rating (R1-R8 PD/LGD/EAD), often
an external rating (S&P / Moody's / Fitch / CARE Saudi), one or two
rating actions (downgrade / watchlist for credit life), and a
counterparty- or group-scope limit with realistic utilisation.

Idempotent: each (org, reference) guarded by existence check.

Usage:
  python setup_cpty_seed.py
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


# ─── Helpers ─────────────────────────────────────────────────────

def cpty_exists(org, ref):
    cur.execute(
        'SELECT id FROM cpty.counterparty '
        'WHERE organisation_id = ? AND reference = ? AND is_deleted = 0',
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_cpty(org, ref, lei, name_en, name_ar, short_name, sector,
                country, industry, parent_group, sovereign, gre, fi,
                rm_id=None, co_id=None,
                exposure_sar_m=0, status='ACTIVE',
                internal_grade=None, external_rating=None, external_agency=None,
                onboarded=None):
    existing = cpty_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        'INSERT INTO cpty.counterparty '
        '(organisation_id, reference, lei, legal_name_en, legal_name_ar, '
        ' short_name, sector, country, industry, parent_group_reference, '
        ' gre_flag, sovereign_flag, fi_flag, status, '
        ' relationship_manager_user_id, credit_officer_user_id, '
        ' onboarded_at, total_exposure_sar_m, '
        ' current_internal_grade, current_external_rating, current_external_agency, '
        ' created_at, updated_at) '
        'OUTPUT INSERTED.id '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org, ref, lei, name_en, name_ar, short_name, sector,
        country, industry, parent_group,
        1 if gre else 0, 1 if sovereign else 0, 1 if fi else 0,
        status,
        rm_id, co_id, onboarded,
        Decimal(str(exposure_sar_m)),
        internal_grade, external_rating, external_agency,
        NOW, NOW,
    )
    return int(cur.fetchone()[0])


def insert_rating(org, cpty_id, rtype, eff_date, grade, scale=None,
                   agency=None, pd=None, lgd=None, ead=None,
                   outlook='STABLE', rationale_en=None, rationale_ar=None):
    cur.execute(
        'INSERT INTO cpty.credit_rating '
        '(organisation_id, counterparty_id, rating_type, effective_date, '
        ' grade, scale, agency, pd, lgd, ead_sar_m, outlook, '
        ' rationale_en, rationale_ar, created_at, updated_at) '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org, cpty_id, rtype, eff_date, grade, scale, agency,
        Decimal(str(pd)) if pd is not None else None,
        Decimal(str(lgd)) if lgd is not None else None,
        Decimal(str(ead)) if ead is not None else None,
        outlook, rationale_en, rationale_ar, NOW, NOW,
    )


def insert_action(org, cpty_id, action_type, action_date,
                   grade_before=None, grade_after=None,
                   trigger=None, rationale_en=None, rationale_ar=None):
    cur.execute(
        'INSERT INTO cpty.rating_action '
        '(organisation_id, counterparty_id, action_type, action_date, '
        ' grade_before, grade_after, trigger_source, '
        ' rationale_en, rationale_ar, created_at, updated_at) '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org, cpty_id, action_type, action_date,
        grade_before, grade_after, trigger,
        rationale_en, rationale_ar, NOW, NOW,
    )


def compute_band(util_pct):
    if util_pct > 100:
        return 'BREACH'
    if util_pct > 90:
        return 'RED'
    if util_pct > 70:
        return 'AMBER'
    return 'GREEN'


def insert_limit(org, scope, cpty_id, group_ref, name_en, name_ar,
                  limit_m, utilised_m, approved=None, next_review=None):
    util_pct = round((utilised_m / limit_m * 100), 4) if limit_m else 0
    band = compute_band(util_pct)
    cur.execute(
        'INSERT INTO cpty.large_exposure_limit '
        '(organisation_id, scope, counterparty_id, group_reference, '
        ' display_name_en, display_name_ar, limit_sar_m, utilised_sar_m, '
        ' utilisation_pct, band, approved_at, next_review_at, '
        ' utilisation_refreshed_at, created_at, updated_at) '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org, scope, cpty_id, group_ref, name_en, name_ar,
        Decimal(str(limit_m)), Decimal(str(utilised_m)),
        Decimal(str(util_pct)), band,
        approved, next_review, TODAY, NOW, NOW,
    )


# ─── TSS GRC (org=1) — full Saudi corporate-bank book ──────────

def seed_tss_grc():
    print("\n>>> Seeding TSS GRC (org=1) — Saudi corporate-bank counterparty book")
    org = 1

    portfolio = [
        # Sovereign + GRE
        ('CP-S001', '5493000XR8K8K8K8K8K8',
         'Kingdom of Saudi Arabia (Sovereign)', 'المملكة العربية السعودية (السيادي)',
         'KSA', 'SOVEREIGN', 'SA', 'Public administration', None,
         True, True, False,
         12500, 'R1', 'A+', "S&P", date(2010, 1, 1)),
        ('CP-S002', '5493000XR8K8K8K8K8K9',
         'Saudi Aramco', 'أرامكو السعودية',
         'Aramco', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Oil and gas',
         'ARAMCO_GROUP', True, False, False,
         8200, 'R2', 'A', "S&P", date(2012, 3, 15)),
        ('CP-S003', '5493000XR8K8K8K8K8KA',
         'SABIC (Saudi Basic Industries Corp)', 'سابك',
         'SABIC', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Petrochemicals',
         'SABIC_GROUP', True, False, False,
         4800, 'R2', 'A', "Moody's", date(2011, 6, 1)),
        ('CP-S004', '5493000XR8K8K8K8K8KB',
         'Public Investment Fund', 'صندوق الاستثمارات العامة',
         'PIF', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Sovereign wealth',
         None, True, False, False,
         6500, 'R1', 'A+', "Fitch", date(2018, 1, 1)),
        ('CP-S005', None,
         'Real Estate Development Fund', 'صندوق التنمية العقاري',
         'REDF', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Mortgage refinance',
         None, True, False, True,
         3200, 'R2', None, None, date(2008, 1, 1)),
        ('CP-S006', '5493000XR8K8K8K8K8KC',
         'Saudi Electricity Company', 'الشركة السعودية للكهرباء',
         'SEC', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Utilities',
         None, True, False, False,
         2100, 'R3', 'A-', "S&P", date(2010, 1, 1)),
        ('CP-S007', '5493000XR8K8K8K8K8KD',
         'Saudi Telecom Company', 'شركة الاتصالات السعودية',
         'STC', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Telecom',
         'STC_GROUP', True, False, False,
         1800, 'R3', 'A-', "Moody's", date(2010, 6, 1)),
        ('CP-S008', '5493000XR8K8K8K8K8KE',
         'Saudia (Saudi Arabian Airlines)', 'الخطوط الجوية العربية السعودية',
         'Saudia', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Aviation',
         None, True, False, False,
         1400, 'R4', None, None, date(2014, 1, 1)),

        # Saudi banks (FI)
        ('CP-F001', '5493000XR8K8K8K8K8KF',
         'Saudi National Bank', 'البنك الأهلي السعودي',
         'SNB', 'FINANCIAL_INSTITUTION', 'SA', 'Banking',
         None, False, False, True,
         3500, 'R2', 'A-', "Fitch", date(2010, 1, 1)),
        ('CP-F002', '5493000XR8K8K8K8K8L1',
         'Al Rajhi Bank', 'مصرف الراجحي',
         'Al Rajhi', 'FINANCIAL_INSTITUTION', 'SA', 'Islamic banking',
         None, False, False, True,
         3200, 'R2', 'A-', "S&P", date(2010, 1, 1)),
        ('CP-F003', '5493000XR8K8K8K8K8L2',
         'Riyad Bank', 'بنك الرياض',
         'Riyad', 'FINANCIAL_INSTITUTION', 'SA', 'Banking',
         None, False, False, True,
         2400, 'R3', 'BBB+', "Moody's", date(2011, 1, 1)),
        ('CP-F004', '5493000XR8K8K8K8K8L3',
         'Arab National Bank', 'البنك العربي الوطني',
         'ANB', 'FINANCIAL_INSTITUTION', 'SA', 'Banking',
         None, False, False, True,
         1900, 'R3', 'BBB+', "Fitch", date(2011, 1, 1)),

        # Family conglomerates / large corporates
        ('CP-C001', '5493000XR8K8K8K8K8L4',
         'Olayan Financing Company', 'شركة العليان للتمويل',
         'Olayan', 'CORPORATE_LARGE', 'SA', 'Diversified holding',
         'OLAYAN_GROUP', False, False, False,
         2800, 'R3', 'BBB', "Moody's", date(2012, 1, 1)),
        ('CP-C002', '5493000XR8K8K8K8K8L5',
         'Almarai Company', 'شركة المراعي',
         'Almarai', 'CORPORATE_LARGE', 'SA', 'Dairy and food',
         None, False, False, False,
         1500, 'R3', 'BBB', "S&P", date(2010, 1, 1)),
        ('CP-C003', '5493000XR8K8K8K8K8L6',
         'Zamil Industrial Investment', 'الزامل للاستثمار الصناعي',
         'Zamil', 'CORPORATE_LARGE', 'SA', 'Industrial holding',
         'ZAMIL_GROUP', False, False, False,
         1200, 'R4', None, None, date(2013, 1, 1)),
        ('CP-C004', '5493000XR8K8K8K8K8L7',
         'Saudi Binladin Group', 'مجموعة بن لادن السعودية',
         'SBG', 'CORPORATE_LARGE', 'SA', 'Construction',
         'BINLADIN_GROUP', False, False, False,
         900, 'R5', None, None, date(2008, 1, 1)),
        ('CP-C005', None,
         'Mobile Telecommunications Company Saudi Arabia', 'موبايلي السعودية',
         'Mobily', 'CORPORATE_LARGE', 'SA', 'Telecom',
         None, False, False, False,
         700, 'R4', 'BB+', "Fitch", date(2014, 1, 1)),
        ('CP-C006', None,
         'Zain Saudi Arabia', 'زين السعودية',
         'Zain SA', 'CORPORATE_LARGE', 'SA', 'Telecom',
         None, False, False, False,
         550, 'R4', 'BB', "S&P", date(2015, 1, 1)),

        # SMEs
        ('CP-SME001', None,
         'Riyadh Steel & Construction Ltd', 'الرياض للحديد والإنشاءات المحدودة',
         'Riyadh Steel', 'CORPORATE_SME', 'SA', 'Steel manufacturing',
         None, False, False, False,
         85, 'R5', None, None, date(2019, 1, 1)),
        ('CP-SME002', None,
         'Jeddah Cold-Chain Logistics Co', 'شركة جدة لخدمات السلسلة الباردة',
         'JCC Logistics', 'CORPORATE_SME', 'SA', 'Cold-chain logistics',
         None, False, False, False,
         62, 'R5', None, None, date(2020, 1, 1)),

        # Multilateral
        ('CP-M001', '5493000XR8K8K8K8K8L8',
         'Islamic Development Bank', 'البنك الإسلامي للتنمية',
         'IsDB', 'MULTILATERAL', 'SA', 'Development banking',
         None, False, False, True,
         950, 'R2', 'AAA', "Moody's", date(2008, 1, 1)),
    ]

    ids = {}
    for p in portfolio:
        (ref, lei, name_en, name_ar, short, sector, country, industry,
         parent, gre, sovereign, fi, exp, grade, ext_rating, ext_agency,
         onboarded) = p
        cid = insert_cpty(org, ref, lei, name_en, name_ar, short, sector,
                           country, industry, parent, sovereign, gre, fi,
                           exposure_sar_m=exp, internal_grade=grade,
                           external_rating=ext_rating, external_agency=ext_agency,
                           onboarded=onboarded)
        ids[ref] = cid

        # Internal rating
        pd_map = {'R1': 0.0010, 'R2': 0.0025, 'R3': 0.0050, 'R4': 0.0120,
                   'R5': 0.0300, 'R6': 0.0750, 'R7': 0.1500, 'R8': 0.3500}
        if grade in pd_map:
            insert_rating(org, cid, 'INTERNAL', TODAY - timedelta(days=180),
                           grade, scale='R1-R8',
                           pd=pd_map[grade], lgd=0.45,
                           ead=exp * 0.9, outlook='STABLE',
                           rationale_en=f'Internal rating assigned per IRB framework, '
                                          f'sector {sector}',
                           rationale_ar=f'تصنيف داخلي وفق إطار IRB، قطاع {sector}')

        # External rating
        if ext_rating:
            insert_rating(org, cid, 'EXTERNAL', TODAY - timedelta(days=120),
                           ext_rating, scale=f'{ext_agency} long-term',
                           agency=ext_agency, outlook='STABLE')

    # Rating actions for credit life
    insert_action(org, ids['CP-C004'], 'WATCHLIST_NEGATIVE',
                   TODAY - timedelta(days=60),
                   trigger='INTERNAL_REVIEW',
                   rationale_en='Construction-sector slowdown + project delays on '
                                  'Riyadh Metro Line 3 subcontract',
                   rationale_ar='تباطؤ قطاع المقاولات وتأخّر مشاريع المسار الثالث في '
                                  'مترو الرياض')
    insert_action(org, ids['CP-C006'], 'DOWNGRADE',
                   TODAY - timedelta(days=30),
                   grade_before='R3', grade_after='R4',
                   trigger='EXTERNAL_AGENCY',
                   rationale_en='S&P downgrade following Q3 results; subscriber-base '
                                  'erosion in postpaid segment',
                   rationale_ar='خفض من S&P بعد نتائج الربع الثالث؛ تآكل قاعدة المشتركين '
                                  'في قطاع الدفع الآجل')

    # Counterparty limits
    limits = [
        # (ref, limit_m, utilised_m)
        ('CP-S001', 18000, 12500),  # 69% — GREEN
        ('CP-S002', 9000, 8200),    # 91% — RED (Aramco running hot)
        ('CP-S003', 5500, 4800),    # 87% — AMBER
        ('CP-S004', 7000, 6500),    # 93% — RED
        ('CP-S005', 4000, 3200),    # 80% — AMBER
        ('CP-S006', 2500, 2100),    # 84% — AMBER
        ('CP-S007', 2200, 1800),    # 82% — AMBER
        ('CP-S008', 1500, 1400),    # 93% — RED
        ('CP-F001', 4000, 3500),    # 87% — AMBER
        ('CP-F002', 4000, 3200),    # 80% — AMBER
        ('CP-F003', 3000, 2400),    # 80% — AMBER
        ('CP-F004', 2500, 1900),    # 76% — AMBER
        ('CP-C001', 3000, 2800),    # 93% — RED
        ('CP-C002', 2000, 1500),    # 75% — AMBER
        ('CP-C003', 1500, 1200),    # 80% — AMBER
        ('CP-C004', 1000, 1050),    # 105% — BREACH (the demo trigger for AR-238!)
        ('CP-C005', 900, 700),      # 78% — AMBER
        ('CP-C006', 700, 550),      # 79% — AMBER
        ('CP-SME001', 100, 85),     # 85% — AMBER
        ('CP-SME002', 80, 62),      # 78% — AMBER
        ('CP-M001', 1200, 950),     # 79% — AMBER
    ]
    for ref, lim, util in limits:
        cid = ids.get(ref)
        if not cid: continue
        # Find display names from the inserted portfolio
        cur.execute(
            'SELECT legal_name_en, legal_name_ar FROM cpty.counterparty '
            'WHERE id = ?', cid)
        ne, na = cur.fetchone()
        insert_limit(org, 'COUNTERPARTY', cid, None, ne, na, lim, util,
                      approved=TODAY - timedelta(days=90),
                      next_review=TODAY + timedelta(days=275))

    # Group-scope limits
    insert_limit(org, 'GROUP', None, 'ARAMCO_GROUP',
                  'Aramco Group (consolidated)', 'مجموعة أرامكو (موحّدة)',
                  12000, 9800,  # 81.6% AMBER
                  approved=TODAY - timedelta(days=60),
                  next_review=TODAY + timedelta(days=305))
    insert_limit(org, 'GROUP', None, 'SABIC_GROUP',
                  'SABIC Group (consolidated)', 'مجموعة سابك (موحّدة)',
                  7000, 5400,  # 77% AMBER
                  approved=TODAY - timedelta(days=60),
                  next_review=TODAY + timedelta(days=305))
    insert_limit(org, 'GROUP', None, 'BINLADIN_GROUP',
                  'Binladin Group (consolidated)', 'مجموعة بن لادن (موحّدة)',
                  1800, 1700,  # 94% RED
                  approved=TODAY - timedelta(days=120),
                  next_review=TODAY + timedelta(days=245))
    insert_limit(org, 'GROUP', None, 'OLAYAN_GROUP',
                  'Olayan Group (consolidated)', 'مجموعة العليان (موحّدة)',
                  4000, 3100,  # 77.5% AMBER
                  approved=TODAY - timedelta(days=60),
                  next_review=TODAY + timedelta(days=305))

    print(f'  ✓ TSS GRC seeded — {len(portfolio)} counterparties + {len(limits)} cpty-limits + 4 group-limits')


# ─── Aramco Trading (org=2) — commodity-trading book ───────────

def seed_aramco():
    print("\n>>> Seeding Aramco Trading (org=2) — commodity counterparty book")
    org = 2

    portfolio = [
        ('CP-O001', None, 'Saudi Aramco (parent)', 'أرامكو السعودية (الأم)',
         'Aramco', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Oil supply',
         'ARAMCO_GROUP', True, False, False,
         2800, 'R2', 'A', "S&P", date(2015, 1, 1)),
        ('CP-O002', None, 'Luberef', 'لوبريف',
         'Luberef', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Lubricant refining',
         'ARAMCO_GROUP', True, False, False,
         800, 'R3', None, None, date(2018, 1, 1)),
        ('CP-O003', None, 'Petro Rabigh', 'بترو رابغ',
         'Petro Rabigh', 'CORPORATE_LARGE', 'SA', 'Petrochemicals',
         None, False, False, False,
         600, 'R4', 'BB+', 'Fitch', date(2017, 1, 1)),
        ('CP-O004', None, 'Trafigura Group', 'مجموعة ترافيغورا',
         'Trafigura', 'CORPORATE_LARGE', 'SG', 'Commodity trading',
         None, False, False, False,
         1200, 'R3', 'BBB-', 'S&P', date(2016, 1, 1)),
        ('CP-O005', None, 'Vitol SA', 'فيتول',
         'Vitol', 'CORPORATE_LARGE', 'CH', 'Commodity trading',
         None, False, False, False,
         1100, 'R3', 'BBB', "Moody's", date(2016, 1, 1)),
        ('CP-O006', None, 'Glencore International', 'غلينكور',
         'Glencore', 'CORPORATE_LARGE', 'CH', 'Commodity trading',
         None, False, False, False,
         950, 'R3', 'BBB', 'Fitch', date(2017, 1, 1)),
    ]

    ids = {}
    for p in portfolio:
        (ref, lei, name_en, name_ar, short, sector, country, industry,
         parent, gre, sovereign, fi, exp, grade, ext_rating, ext_agency, onboarded) = p
        cid = insert_cpty(org, ref, lei, name_en, name_ar, short, sector,
                           country, industry, parent, sovereign, gre, fi,
                           exposure_sar_m=exp, internal_grade=grade,
                           external_rating=ext_rating, external_agency=ext_agency,
                           onboarded=onboarded)
        ids[ref] = cid

        pd_map = {'R2': 0.0025, 'R3': 0.0050, 'R4': 0.0120}
        if grade in pd_map:
            insert_rating(org, cid, 'INTERNAL', TODAY - timedelta(days=120),
                           grade, scale='R1-R8',
                           pd=pd_map[grade], lgd=0.50, ead=exp * 0.85)
        if ext_rating:
            insert_rating(org, cid, 'EXTERNAL', TODAY - timedelta(days=60),
                           ext_rating, agency=ext_agency,
                           scale=f'{ext_agency} long-term')

    # Limits
    limit_specs = [
        ('CP-O001', 3500, 2800),  # 80% AMBER
        ('CP-O002', 1000, 800),   # 80% AMBER
        ('CP-O003', 700, 600),    # 86% AMBER
        ('CP-O004', 1400, 1200),  # 86% AMBER
        ('CP-O005', 1300, 1100),  # 85% AMBER
        ('CP-O006', 1100, 950),   # 86% AMBER
    ]
    for ref, lim, util in limit_specs:
        cid = ids.get(ref)
        if not cid: continue
        cur.execute('SELECT legal_name_en, legal_name_ar FROM cpty.counterparty WHERE id = ?', cid)
        ne, na = cur.fetchone()
        insert_limit(org, 'COUNTERPARTY', cid, None, ne, na, lim, util,
                      approved=TODAY - timedelta(days=60),
                      next_review=TODAY + timedelta(days=305))

    print(f'  ✓ Aramco Trading seeded — {len(portfolio)} counterparties + {len(limit_specs)} limits')


# ─── STC Bank (org=3) — digital-bank corporate book ────────────

def seed_stc():
    print("\n>>> Seeding STC Bank (org=3) — digital-bank corporate book")
    org = 3

    portfolio = [
        ('CP-D001', None, 'STC Group (parent)', 'مجموعة الاتصالات السعودية (الأم)',
         'STC', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Telecom',
         'STC_GROUP', True, False, False,
         1500, 'R2', 'A-', "Moody's", date(2020, 1, 1)),
        ('CP-D002', None, 'Tamara (BNPL fintech)', 'تمارا (شراء الآن وادفع لاحقاً)',
         'Tamara', 'FINANCIAL_INSTITUTION', 'SA', 'Fintech BNPL',
         None, False, False, True,
         180, 'R4', None, None, date(2022, 1, 1)),
        ('CP-D003', None, 'Tabby (BNPL fintech)', 'تابي',
         'Tabby', 'FINANCIAL_INSTITUTION', 'AE', 'Fintech BNPL',
         None, False, False, True,
         165, 'R4', None, None, date(2022, 1, 1)),
        ('CP-D004', None, 'Saudi Post (SPL Logistics)', 'البريد السعودي — سبل',
         'SPL', 'GOVERNMENT_RELATED_ENTITY', 'SA', 'Logistics',
         None, True, False, False,
         320, 'R3', None, None, date(2021, 1, 1)),
        ('CP-D005', None, 'Jahez International', 'جاهز',
         'Jahez', 'CORPORATE_LARGE', 'SA', 'Food delivery',
         None, False, False, False,
         220, 'R4', None, None, date(2021, 1, 1)),
        ('CP-D006', None, 'Saudi Basic Pension (GOSI counterparty)', 'المؤسّسة العامة للتأمينات الاجتماعية',
         'GOSI', 'SOVEREIGN', 'SA', 'Pension administration',
         None, False, True, False,
         900, 'R1', None, None, date(2019, 1, 1)),
    ]

    ids = {}
    for p in portfolio:
        (ref, lei, name_en, name_ar, short, sector, country, industry,
         parent, gre, sovereign, fi, exp, grade, ext_rating, ext_agency, onboarded) = p
        cid = insert_cpty(org, ref, lei, name_en, name_ar, short, sector,
                           country, industry, parent, sovereign, gre, fi,
                           exposure_sar_m=exp, internal_grade=grade,
                           external_rating=ext_rating, external_agency=ext_agency,
                           onboarded=onboarded)
        ids[ref] = cid

        pd_map = {'R1': 0.0010, 'R2': 0.0025, 'R3': 0.0050, 'R4': 0.0120}
        if grade in pd_map:
            insert_rating(org, cid, 'INTERNAL', TODAY - timedelta(days=90),
                           grade, scale='R1-R8',
                           pd=pd_map[grade], lgd=0.45, ead=exp * 0.9)
        if ext_rating:
            insert_rating(org, cid, 'EXTERNAL', TODAY - timedelta(days=60),
                           ext_rating, agency=ext_agency,
                           scale=f'{ext_agency} long-term')

    # Watchlist for fintech BNPL (operational pressure on the sector)
    insert_action(org, ids['CP-D003'], 'WATCHLIST_NEGATIVE',
                   TODAY - timedelta(days=45),
                   trigger='EARLY_WARNING',
                   rationale_en='BNPL-sector funding tightening; KSA regulatory scrutiny '
                                  'increasing post-SAMA BNPL Rules 2024',
                   rationale_ar='تشدّد التمويل في قطاع الشراء الآن والدفع لاحقاً؛ تصاعد '
                                  'الرقابة بعد قواعد ساما 2024')

    # Limits
    limit_specs = [
        ('CP-D001', 1800, 1500),  # 83% AMBER
        ('CP-D002', 250, 180),    # 72% AMBER
        ('CP-D003', 220, 165),    # 75% AMBER
        ('CP-D004', 400, 320),    # 80% AMBER
        ('CP-D005', 300, 220),    # 73% AMBER
        ('CP-D006', 1500, 900),   # 60% GREEN
    ]
    for ref, lim, util in limit_specs:
        cid = ids.get(ref)
        if not cid: continue
        cur.execute('SELECT legal_name_en, legal_name_ar FROM cpty.counterparty WHERE id = ?', cid)
        ne, na = cur.fetchone()
        insert_limit(org, 'COUNTERPARTY', cid, None, ne, na, lim, util,
                      approved=TODAY - timedelta(days=60),
                      next_review=TODAY + timedelta(days=305))

    print(f'  ✓ STC Bank seeded — {len(portfolio)} counterparties + {len(limit_specs)} limits')


# ─── Main ────────────────────────────────────────────────────────

def main() -> None:
    print(">>> Seeding cpty registry across 3 tenants")
    try:
        seed_tss_grc()
        seed_aramco()
        seed_stc()
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"! seed failed: {e}")
        sys.exit(1)

    cur.execute('SELECT organisation_id, COUNT(*) FROM cpty.counterparty '
                'WHERE is_deleted = 0 GROUP BY organisation_id ORDER BY organisation_id')
    print('\n--- Counterparties per org:')
    for row in cur.fetchall():
        print(f'   org={row[0]}: {row[1]} counterparties')

    for label, tbl in [
            ('Credit ratings', 'cpty.credit_rating'),
            ('Rating actions', 'cpty.rating_action'),
            ('Limits',         'cpty.large_exposure_limit')]:
        cur.execute(f'SELECT COUNT(*) FROM {tbl} WHERE is_deleted = 0')
        print(f'--- {label}: {cur.fetchone()[0]}')

    cur.execute("SELECT band, COUNT(*) FROM cpty.large_exposure_limit "
                "WHERE is_deleted = 0 GROUP BY band ORDER BY band")
    print('\n--- Limits by band:')
    for row in cur.fetchall():
        print(f'   {row[0]}: {row[1]}')

    print('\n✓ done')


if __name__ == '__main__':
    main()
