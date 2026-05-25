"""Seed Saudi-realistic mandatory regulatory returns for 3 tenants.

Sprint 16 PR #4. Pairs with PRs #239 (backend), #240 (F8 rules),
#241 (frontend).

Each tenant gets a tenant-shaped catalog of recurring obligations
(SAMA / CMA / NCA / SDAIA / ZATCA depending on business profile)
plus a mix of live in-flight filings + past-quarter closed filings
so the dashboard has all states to render:

  Daily returns   — 4 cycles: 2 ACKNOWLEDGED, 1 SUBMITTED today,
                    1 SCHEDULED tomorrow
  Monthly returns — prior month CLOSED, current month varies
                    (IN_PREPARATION / IN_REVIEW / APPROVED), next
                    month SCHEDULED
  Quarterly       — Q1 CLOSED, Q2 in-flight (varies)
  Annual          — FY2025 CLOSED with ack ref, FY2026 SCHEDULED

  Org 1 also has 1 REJECTED + 1 EXEMPTED filing to exercise both
  edge-case paths.

Per-tenant content reflects each firm's actual regulatory profile:

  org=1  TSS GRC          — full SAMA stack (liquidity, capital, AML,
                            consumer protection, risk, fraud) +
                            ZATCA tax + SDAIA PDPL + NCA cyber
  org=2  Aramco Trading   — CMA listed-co stack (directors' dealings,
                            interim FS, annual audited FS, MD&A) +
                            ZATCA + SDAIA + NCA
  org=3  STC Bank         — SAMA-licensed digital-bank stack +
                            NCA cyber + ZATCA + SDAIA

The bilingual EN+AR titles + descriptions are operator-natural,
not literal translations.

Idempotent: each (org, code) and (org, reference) pair is guarded
by an existence check.

Usage:
  python setup_regreporting_seed.py
"""
from __future__ import annotations

import sys
from datetime import date, datetime, timedelta

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

def obligation_exists(org_id: int, code: str) -> int | None:
    cur.execute(
        'SELECT id FROM regreporting.regreport_obligation '
        'WHERE organisation_id = ? AND code = ? AND is_deleted = 0',
        org_id, code,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_obligation(org_id, code, regulator, frequency,
                       title_en, title_ar, due_offset_bd, owner_role,
                       reviewer_role, statutory_basis, filing_channel,
                       description_en, description_ar,
                       reminder_ladder='7,3,1'):
    existing = obligation_exists(org_id, code)
    if existing:
        return existing
    cur.execute(
        'INSERT INTO regreporting.regreport_obligation '
        '(organisation_id, code, regulator, frequency, title_en, title_ar, '
        ' description_en, description_ar, due_offset_business_days, '
        ' statutory_basis, owner_role, reviewer_role, filing_channel, '
        ' reminder_ladder, active, created_at, updated_at) '
        'OUTPUT INSERTED.id '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)',
        org_id, code, regulator, frequency, title_en, title_ar,
        description_en, description_ar, due_offset_bd,
        statutory_basis, owner_role, reviewer_role, filing_channel,
        reminder_ladder, NOW, NOW,
    )
    return int(cur.fetchone()[0])


def filing_exists(org_id, reference) -> int | None:
    cur.execute(
        'SELECT id FROM regreporting.regreport_filing '
        'WHERE organisation_id = ? AND reference = ? AND is_deleted = 0',
        org_id, reference,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_filing(org_id, obligation_id, reference, status,
                  period_start, period_end, due_date,
                  submitted_at=None, ack_ref=None, acknowledged_at=None,
                  closed_at=None, artifact_ref=None,
                  prep_notes_en=None, prep_notes_ar=None,
                  rejection_en=None, rejection_ar=None,
                  exemption_en=None, exemption_ar=None,
                  exemption_ref=None,
                  internal_target=None):
    existing = filing_exists(org_id, reference)
    if existing:
        return existing
    cur.execute(
        'INSERT INTO regreporting.regreport_filing '
        '(organisation_id, obligation_id, reference, status, '
        ' period_start, period_end, due_date, internal_target_date, '
        ' submitted_at, regulator_ack_reference, acknowledged_at, '
        ' closed_at, filing_artifact_reference, '
        ' preparation_notes_en, preparation_notes_ar, '
        ' rejection_reason_en, rejection_reason_ar, '
        ' exemption_reason_en, exemption_reason_ar, exemption_reference, '
        ' created_at, updated_at) '
        'OUTPUT INSERTED.id '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org_id, obligation_id, reference, status,
        period_start, period_end, due_date, internal_target,
        submitted_at, ack_ref, acknowledged_at,
        closed_at, artifact_ref,
        prep_notes_en, prep_notes_ar,
        rejection_en, rejection_ar,
        exemption_en, exemption_ar, exemption_ref,
        NOW, NOW,
    )
    return int(cur.fetchone()[0])


def insert_event(org_id, filing_id, occurred_at, event_type,
                  status_after=None, note_en=None, note_ar=None):
    cur.execute(
        'INSERT INTO regreporting.regreport_event '
        '(organisation_id, filing_id, occurred_at, event_type, '
        ' status_after, note_en, note_ar, created_at, updated_at) '
        'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
        org_id, filing_id, occurred_at, event_type,
        status_after, note_en, note_ar, NOW, NOW,
    )


def first_of_month(d: date) -> date:
    return d.replace(day=1)


def last_of_month(d: date) -> date:
    nxt = (d.replace(day=28) + timedelta(days=4)).replace(day=1)
    return nxt - timedelta(days=1)


# ─── TSS GRC (org=1) — full Saudi bank stack ────────────────────

def seed_tss_grc():
    print("\n>>> Seeding TSS GRC (org=1) — Saudi bank regulatory stack")
    org = 1

    # 1. SAMA-DLR — Daily Liquidity Return
    o1 = insert_obligation(
        org, 'SAMA-DLR', 'SAMA', 'DAILY',
        'SAMA Daily Liquidity Return',
        'تقرير السيولة اليومي لساما',
        1, 'TREASURY', 'CFO',
        'SAMA Circular 41031843 — Daily Liquidity Coverage Ratio',
        'SAMA RBS portal',
        'Daily liquidity coverage and net stable funding metrics, '
        'lodged before 09:30 the following business day.',
        'مقاييس تغطية السيولة وصافي التمويل المستقر اليومية، '
        'تُلَودَع قبل الساعة 9:30 من صباح يوم العمل التالي.',
        reminder_ladder='1',
    )
    # 4 cycles: T-3 ACKNOWLEDGED, T-2 ACKNOWLEDGED, T-1 SUBMITTED today,
    # T+0 SCHEDULED (due tomorrow)
    for i, days_back, status in [
        (3, 3, 'ACKNOWLEDGED'),
        (2, 2, 'ACKNOWLEDGED'),
        (1, 1, 'SUBMITTED'),
        (0, 0, 'SCHEDULED'),
    ]:
        p = TODAY - timedelta(days=days_back)
        due = p + timedelta(days=1)
        ref = f'SAMA-DLR-{p}'
        submitted_at = None
        ack_ref = None
        ack_at = None
        if status in ('SUBMITTED', 'ACKNOWLEDGED'):
            submitted_at = datetime.combine(due, datetime.min.time().replace(hour=8, minute=45))
            ack_ref = f'SAMA-RBS-{p}-{900+i:05d}'
        if status == 'ACKNOWLEDGED':
            ack_at = datetime.combine(due, datetime.min.time().replace(hour=11, minute=15))
        fid = insert_filing(
            org, o1, ref, status, p, p, due,
            submitted_at=submitted_at, ack_ref=ack_ref,
            acknowledged_at=ack_at,
            artifact_ref=(f's3://compliance/sama/dlr/{p}/dlr-{p}.csv'
                          if submitted_at else None),
        )
        insert_event(org, fid, NOW - timedelta(days=days_back+1),
                     'STATUS_CHANGED', 'SCHEDULED',
                     f'DLR cycle for {p} opened.',
                     f'فتح دورة تقرير السيولة لتاريخ {p}.')
        if submitted_at:
            insert_event(org, fid, submitted_at, 'SUBMITTED', 'SUBMITTED',
                         f'Lodged via RBS portal, ack {ack_ref}.',
                         f'إيداع عبر بوّابة RBS، إقرار {ack_ref}.')
        if ack_at:
            insert_event(org, fid, ack_at, 'ACK_RECEIVED', 'ACKNOWLEDGED',
                         'SAMA Treasury Operations confirmed receipt.',
                         'وحدة عمليات الخزانة في ساما أكّدت الاستلام.')

    # 2. SAMA-MCR — Monthly Capital Adequacy
    o2 = insert_obligation(
        org, 'SAMA-MCR', 'SAMA', 'MONTHLY',
        'SAMA Monthly Capital Adequacy Return',
        'تقرير كفاية رأس المال الشهري لساما',
        15, 'CRO', 'CFO',
        'SAMA Capital Adequacy Framework (Basel III local implementation)',
        'SAMA RBS portal',
        'Capital adequacy ratio breakdown by tier, RWA composition, '
        'leverage ratio, and HQLA holdings. Signed off by CFO.',
        'تقسيم كفاية رأس المال حسب الفئة، تركيبة الأصول المُرجَّحة بالمخاطر، '
        'نسبة الرافعة المالية، وأصول السيولة عالية الجودة. يعتمده المدير المالي.',
    )
    # Prior month CLOSED, current month IN_REVIEW, next month SCHEDULED
    prior = first_of_month(TODAY - timedelta(days=30))
    prior_end = last_of_month(prior)
    insert_filing(
        org, o2, f'SAMA-MCR-{prior.year}-{prior.month:02d}',
        'CLOSED', prior, prior_end,
        prior_end + timedelta(days=21),  # +15BD ≈ 21 cal days
        submitted_at=datetime.combine(prior_end + timedelta(days=18),
                                       datetime.min.time().replace(hour=14, minute=12)),
        ack_ref=f'SAMA-RBS-MCR-{prior.year}-{prior.month:02d}-04421',
        acknowledged_at=datetime.combine(prior_end + timedelta(days=22),
                                          datetime.min.time().replace(hour=10, minute=0)),
        closed_at=datetime.combine(prior_end + timedelta(days=28),
                                    datetime.min.time().replace(hour=9, minute=0)),
        artifact_ref=f's3://compliance/sama/mcr/{prior.year}-{prior.month:02d}/mcr.pdf',
        prep_notes_en='Tier-1 CAR 18.2%, comfortably above 10.5% minimum.',
        prep_notes_ar='كفاية رأس المال من الفئة الأولى 18.2%، أعلى من الحدّ الأدنى 10.5% بهامش مريح.',
    )
    cur_m = first_of_month(TODAY)
    cur_end = last_of_month(cur_m)
    insert_filing(
        org, o2, f'SAMA-MCR-{cur_m.year}-{cur_m.month:02d}',
        'IN_REVIEW', cur_m, cur_end, cur_end + timedelta(days=21),
        prep_notes_en='Pending validation of new IRB credit model adjustments.',
        prep_notes_ar='بانتظار التحقّق من تعديلات النموذج الائتماني الداخلي IRB الجديدة.',
    )
    nxt = first_of_month(cur_end + timedelta(days=5))
    nxt_end = last_of_month(nxt)
    insert_filing(
        org, o2, f'SAMA-MCR-{nxt.year}-{nxt.month:02d}',
        'SCHEDULED', nxt, nxt_end, nxt_end + timedelta(days=21),
    )

    # 3. SAMA-AMLM — Monthly AML/CFT Returns
    o3 = insert_obligation(
        org, 'SAMA-AMLM', 'SAMA', 'MONTHLY',
        'SAMA Monthly AML/CFT Returns',
        'تقرير غسل الأموال وتمويل الإرهاب الشهري لساما',
        20, 'COMPLIANCE_OFFICER', 'CRO',
        'SAMA AML/CFT Rules 2018 (Article 35) — periodic reporting',
        'SAMA RBS portal',
        'STR statistics, sanctions-screening hits, high-risk customer reviews, '
        'and PEP onboarding metrics for the reporting month.',
        'إحصاءات بلاغات المعاملات المشبوهة، إصابات فحص العقوبات، '
        'مراجعات العملاء عاليي المخاطر، ومقاييس الأشخاص ذوي الخطورة السياسية الشهرية.',
    )
    # Prior month CLOSED, current month IN_PREPARATION, next SCHEDULED
    insert_filing(
        org, o3, f'SAMA-AMLM-{prior.year}-{prior.month:02d}',
        'CLOSED', prior, prior_end, prior_end + timedelta(days=28),
        submitted_at=datetime.combine(prior_end + timedelta(days=25),
                                       datetime.min.time().replace(hour=15, minute=30)),
        ack_ref=f'SAMA-AMLM-{prior.year}-{prior.month:02d}-01172',
        acknowledged_at=datetime.combine(prior_end + timedelta(days=27),
                                          datetime.min.time()),
        closed_at=datetime.combine(prior_end + timedelta(days=30),
                                    datetime.min.time()),
    )
    insert_filing(
        org, o3, f'SAMA-AMLM-{cur_m.year}-{cur_m.month:02d}',
        'IN_PREPARATION', cur_m, cur_end, cur_end + timedelta(days=28),
    )

    # 4. SAMA-ABF — Annual Anti-Fraud Return (one CLOSED, one SCHEDULED)
    o4 = insert_obligation(
        org, 'SAMA-ABF', 'SAMA', 'ANNUAL',
        'SAMA Annual Anti-Fraud Return',
        'التقرير السنوي لمكافحة الاحتيال لساما',
        45, 'COMPLIANCE_OFFICER', 'CRO',
        'SAMA Anti-Fraud Regulation 2018 — annual posture statement',
        'SAMA RBS portal',
        'Annual fraud-loss analysis, control-effectiveness assessment, '
        'KRI breach history, and forward-looking control roadmap.',
        'التحليل السنوي لخسائر الاحتيال، تقييم فاعلية الضوابط، '
        'تاريخ تجاوزات مؤشّرات المخاطر الرئيسية، وخارطة طريق الضوابط المستقبلية.',
        reminder_ladder='30,14,7,3,1',
    )
    fy = TODAY.year - 1
    fy_start = date(fy, 1, 1)
    fy_end = date(fy, 12, 31)
    insert_filing(
        org, o4, f'SAMA-ABF-{fy}',
        'CLOSED', fy_start, fy_end,
        fy_end + timedelta(days=63),  # +45 BD
        submitted_at=datetime.combine(fy_end + timedelta(days=58),
                                       datetime.min.time().replace(hour=11)),
        ack_ref=f'SAMA-ABF-{fy}-00029',
        acknowledged_at=datetime.combine(fy_end + timedelta(days=72),
                                          datetime.min.time()),
        closed_at=datetime.combine(fy_end + timedelta(days=90),
                                    datetime.min.time()),
        prep_notes_en='Net fraud losses SAR 4.2M; investigation closure rate 87%.',
        prep_notes_ar='صافي خسائر الاحتيال 4.2 مليون ريال؛ نسبة إغلاق التحقيقات 87%.',
    )
    cur_fy = TODAY.year
    insert_filing(
        org, o4, f'SAMA-ABF-{cur_fy}',
        'SCHEDULED', date(cur_fy, 1, 1), date(cur_fy, 12, 31),
        date(cur_fy, 12, 31) + timedelta(days=63),
    )

    # 5. ZATCA-VAT — Monthly VAT (and an EXEMPTED filing for the demo)
    o5 = insert_obligation(
        org, 'ZATCA-VAT', 'ZATCA', 'MONTHLY',
        'ZATCA Monthly VAT Return',
        'إقرار ضريبة القيمة المضافة الشهري لزاتكا',
        11, 'TAX_MANAGER', 'CFO',
        'VAT Implementing Regulation Article 62 — monthly returns',
        'ZATCA Fatoora platform',
        'Monthly input + output VAT reconciliation with payment.',
        'تسوية ضريبة المدخلات والمخرجات الشهرية مع السداد.',
    )
    insert_filing(
        org, o5, f'ZATCA-VAT-{prior.year}-{prior.month:02d}',
        'CLOSED', prior, prior_end, prior_end + timedelta(days=15),
        submitted_at=datetime.combine(prior_end + timedelta(days=14),
                                       datetime.min.time().replace(hour=16, minute=42)),
        ack_ref=f'ZATCA-VAT-{prior.year}-{prior.month:02d}-{org}-72481',
        acknowledged_at=datetime.combine(prior_end + timedelta(days=15),
                                          datetime.min.time()),
        closed_at=datetime.combine(prior_end + timedelta(days=20),
                                    datetime.min.time()),
        prep_notes_en='Net VAT payable SAR 11.8M; settled same day via Fatoora.',
        prep_notes_ar='صافي ضريبة القيمة المضافة المستحقة 11.8 مليون ريال، سُدِّدت في اليوم نفسه عبر فاتورة.',
    )
    insert_filing(
        org, o5, f'ZATCA-VAT-{cur_m.year}-{cur_m.month:02d}',
        'APPROVED', cur_m, cur_end, cur_end + timedelta(days=15),
        prep_notes_en='Ready for CFO sign-off; payable SAR 13.2M.',
        prep_notes_ar='جاهز لتوقيع المدير المالي؛ المستحق 13.2 مليون ريال.',
    )

    # 6. SAMA-CCR — Quarterly Consumer Complaints (REJECTED example!)
    o6 = insert_obligation(
        org, 'SAMA-CCR', 'SAMA', 'QUARTERLY',
        'SAMA Quarterly Consumer Complaints Return',
        'تقرير شكاوى العملاء الربع‑سنوي لساما',
        20, 'COMPLIANCE_OFFICER', 'CRO',
        'SAMA Consumer Protection Principles (Article 28)',
        'SAMA RBS portal',
        'Aggregated complaints by category, resolution-time histogram, '
        'and escalation-to-SAMA cases.',
        'تجميع الشكاوى حسب الفئة، توزيع أزمنة المعالجة، والشكاوى المُحالة لساما.',
    )
    last_q_end = first_of_month(TODAY) - timedelta(days=1)
    last_q_start = date(last_q_end.year,
                         ((last_q_end.month - 1) // 3) * 3 + 1, 1)
    insert_filing(
        org, o6, f'SAMA-CCR-{last_q_end.year}-Q{((last_q_end.month - 1)//3)+1}',
        'REJECTED', last_q_start, last_q_end,
        last_q_end + timedelta(days=28),
        submitted_at=datetime.combine(last_q_end + timedelta(days=20),
                                       datetime.min.time().replace(hour=11)),
        ack_ref=f'SAMA-CCR-RBS-{last_q_end.year}-Q{((last_q_end.month - 1)//3)+1}-0083',
        prep_notes_en='Q-end aggregation, total 1,824 complaints.',
        prep_notes_ar='تجميع نهاية الربع، إجمالي 1,824 شكوى.',
        rejection_en='Category breakdown missing for retail-mortgage complaints. '
                     'Resubmit with the standard SAMA complaint-taxonomy mapping.',
        rejection_ar='تقسيم الفئات مفقود لشكاوى التمويل العقاري للأفراد. '
                     'يُرجى إعادة التقديم بعد المطابقة مع التصنيف المعتمد لدى ساما.',
    )

    # 7. SDAIA-PII — Annual PDPL Compliance Statement (EXEMPTED example)
    o7 = insert_obligation(
        org, 'SDAIA-PII', 'SDAIA', 'ANNUAL',
        'SDAIA Annual PDPL Compliance Statement',
        'البيان السنوي للالتزام بنظام حماية البيانات الشخصية لسدايا',
        60, 'DPO', 'COMPLIANCE_OFFICER',
        'Personal Data Protection Law (Article 35) — annual posture',
        'SDAIA national data office portal',
        'Annual PDPL compliance maturity report including RoPA, '
        'consent metrics, DSR handling KPIs, and cross-border-transfer log.',
        'تقرير النضج السنوي للالتزام بنظام حماية البيانات يشمل سجل أنشطة المعالجة، '
        'مقاييس الموافقات، أداء طلبات أصحاب البيانات، وسجل النقل الدولي.',
        reminder_ladder='30,14,7',
    )
    insert_filing(
        org, o7, f'SDAIA-PII-{fy}',
        'EXEMPTED', fy_start, fy_end, fy_end + timedelta(days=84),
        exemption_en='Granted formal exemption from FY2025 cycle while '
                     'subsidiary entities consolidate to a single legal entity '
                     'effective Jan 2026.',
        exemption_ar='مُنح إعفاء رسمي من دورة العام المالي 2025 ريثما تُدمَج الكيانات '
                     'التابعة في كيان قانوني واحد اعتباراً من يناير 2026.',
        exemption_ref='SDAIA-EX-2026-008',
    )

    print('  ✓ TSS GRC obligations + filings seeded')


# ─── Aramco Trading (org=2) — CMA listed-co stack ───────────────

def seed_aramco_trading():
    print("\n>>> Seeding Aramco Trading (org=2) — CMA listed-co regulatory stack")
    org = 2

    o1 = insert_obligation(
        org, 'CMA-DDIR', 'CMA', 'QUARTERLY',
        'CMA Quarterly Directors\' & Major-Shareholder Dealings',
        'الإفصاح الربع‑سنوي عن صفقات أعضاء المجلس وكبار المساهمين',
        5, 'CFO', 'INTERNAL_AUDITOR',
        'CMA Continuing Obligations — Article 56',
        'Tadawul disclosure portal',
        'Quarterly disclosure of directors\' + 5%+ shareholders\' '
        'dealings in company securities; same-business-day cutoff.',
        'إفصاح ربع‑سنوي عن صفقات أعضاء المجلس وحمَلَة 5%+ من الأسهم؛ '
        'حدّ الإغلاق في يوم العمل نفسه.',
    )
    last_q_end = first_of_month(TODAY) - timedelta(days=1)
    last_q_start = date(last_q_end.year,
                         ((last_q_end.month - 1) // 3) * 3 + 1, 1)
    insert_filing(
        org, o1,
        f'CMA-DDIR-{last_q_end.year}-Q{((last_q_end.month - 1)//3)+1}',
        'CLOSED', last_q_start, last_q_end,
        last_q_end + timedelta(days=7),
        submitted_at=datetime.combine(last_q_end + timedelta(days=4),
                                       datetime.min.time().replace(hour=10, minute=12)),
        ack_ref=f'TADAWUL-DDIR-{last_q_end.year}-Q{((last_q_end.month - 1)//3)+1}-008812',
        acknowledged_at=datetime.combine(last_q_end + timedelta(days=4),
                                          datetime.min.time().replace(hour=14)),
        closed_at=datetime.combine(last_q_end + timedelta(days=10),
                                    datetime.min.time()),
    )

    o2 = insert_obligation(
        org, 'CMA-INTL', 'CMA', 'QUARTERLY',
        'CMA Interim Financial Statements (Quarterly Q-Report)',
        'القوائم المالية المرحلية الربع‑سنوية',
        30, 'CFO', 'INTERNAL_AUDITOR',
        'CMA Listing Rules — Article 41 (interim disclosure)',
        'Tadawul disclosure portal',
        'Reviewed (not audited) interim FS plus MD&A. Must be lodged '
        'within 30 calendar days of quarter end.',
        'القوائم المالية المرحلية المُراجَعة (غير المُدقَّقة) مع مناقشة الإدارة. '
        'يجب إيداعها خلال 30 يوماً تقويميّاً من نهاية الربع.',
    )
    insert_filing(
        org, o2,
        f'CMA-INTL-{last_q_end.year}-Q{((last_q_end.month - 1)//3)+1}',
        'CLOSED', last_q_start, last_q_end,
        last_q_end + timedelta(days=30),
        submitted_at=datetime.combine(last_q_end + timedelta(days=22),
                                       datetime.min.time().replace(hour=16)),
        ack_ref=f'TADAWUL-INTL-{last_q_end.year}-Q{((last_q_end.month - 1)//3)+1}-019021',
        acknowledged_at=datetime.combine(last_q_end + timedelta(days=23),
                                          datetime.min.time()),
        closed_at=datetime.combine(last_q_end + timedelta(days=35),
                                    datetime.min.time()),
    )
    # Current Q in IN_PREPARATION
    cur_q_start = date(TODAY.year, ((TODAY.month - 1) // 3) * 3 + 1, 1)
    cur_q_end_month_start = date(cur_q_start.year,
                                   cur_q_start.month + 2, 1) \
                            if cur_q_start.month + 2 <= 12 \
                            else date(cur_q_start.year, 12, 1)
    cur_q_end = last_of_month(cur_q_end_month_start)
    insert_filing(
        org, o2,
        f'CMA-INTL-{cur_q_start.year}-Q{((cur_q_start.month - 1)//3)+1}',
        'IN_PREPARATION', cur_q_start, cur_q_end,
        cur_q_end + timedelta(days=30),
    )

    # ZATCA-VAT
    o3 = insert_obligation(
        org, 'ZATCA-VAT', 'ZATCA', 'MONTHLY',
        'ZATCA Monthly VAT Return',
        'إقرار ضريبة القيمة المضافة الشهري لزاتكا',
        11, 'TAX_MANAGER', 'CFO',
        'VAT Implementing Regulation Article 62',
        'ZATCA Fatoora platform',
        'Monthly input + output VAT reconciliation with payment.',
        'تسوية ضريبة المدخلات والمخرجات الشهرية مع السداد.',
    )
    prior = first_of_month(TODAY - timedelta(days=30))
    prior_end = last_of_month(prior)
    cur_m = first_of_month(TODAY)
    cur_end = last_of_month(cur_m)
    insert_filing(
        org, o3, f'ZATCA-VAT-{prior.year}-{prior.month:02d}',
        'CLOSED', prior, prior_end, prior_end + timedelta(days=15),
        submitted_at=datetime.combine(prior_end + timedelta(days=13),
                                       datetime.min.time().replace(hour=15)),
        ack_ref=f'ZATCA-VAT-{prior.year}-{prior.month:02d}-{org}-92114',
        acknowledged_at=datetime.combine(prior_end + timedelta(days=14),
                                          datetime.min.time()),
        closed_at=datetime.combine(prior_end + timedelta(days=20),
                                    datetime.min.time()),
        prep_notes_en='Net VAT recoverable on commodity-trading inputs.',
        prep_notes_ar='ضريبة قيمة مضافة قابلة للاسترداد على مدخلات تجارة السلع.',
    )
    insert_filing(
        org, o3, f'ZATCA-VAT-{cur_m.year}-{cur_m.month:02d}',
        'IN_REVIEW', cur_m, cur_end, cur_end + timedelta(days=15),
    )

    print('  ✓ Aramco Trading obligations + filings seeded')


# ─── STC Bank (org=3) — digital banking + telco profile ─────────

def seed_stc_bank():
    print("\n>>> Seeding STC Bank (org=3) — digital-bank + telco regulatory stack")
    org = 3

    # SAMA-DLR daily
    o1 = insert_obligation(
        org, 'SAMA-DLR', 'SAMA', 'DAILY',
        'SAMA Daily Liquidity Return',
        'تقرير السيولة اليومي لساما',
        1, 'TREASURY', 'CFO',
        'SAMA Circular 41031843',
        'SAMA RBS portal',
        'Daily liquidity coverage and net stable funding metrics.',
        'مقاييس تغطية السيولة وصافي التمويل المستقر اليومية.',
        reminder_ladder='1',
    )
    for days_back, status in [(2, 'ACKNOWLEDGED'), (1, 'SUBMITTED'),
                                (0, 'SCHEDULED')]:
        p = TODAY - timedelta(days=days_back)
        due = p + timedelta(days=1)
        ref = f'SAMA-DLR-{p}'
        sub_at = None; ack = None; ack_at = None
        if status in ('SUBMITTED', 'ACKNOWLEDGED'):
            sub_at = datetime.combine(due, datetime.min.time().replace(hour=9, minute=12))
            ack = f'SAMA-RBS-{p}-{1400+days_back:05d}'
        if status == 'ACKNOWLEDGED':
            ack_at = datetime.combine(due, datetime.min.time().replace(hour=12, minute=45))
        insert_filing(org, o1, ref, status, p, p, due,
                       submitted_at=sub_at, ack_ref=ack,
                       acknowledged_at=ack_at)

    # NCA-CMSR monthly cyber
    o2 = insert_obligation(
        org, 'NCA-CMSR', 'NCA', 'MONTHLY',
        'NCA Monthly Cyber Posture Summary',
        'الملخّص الشهري للوضع السيبراني — هيئة الأمن السيبراني',
        10, 'CISO', 'CRO',
        'NCA OTCC Annex H — operator periodic reporting',
        'NCA Haseen portal',
        'Monthly cyber-incident summary, patch-compliance percentage, '
        'detected anomalies on USSD *900# + SS7/SIGTRAN, and HSM-key-rotation log.',
        'الملخّص الشهري للحوادث السيبرانية، نسبة الالتزام بالتحديثات، الشذوذ المرصود '
        'على USSD ‏*900# وSS7/SIGTRAN، وسجل تدوير مفاتيح HSM.',
    )
    prior = first_of_month(TODAY - timedelta(days=30))
    prior_end = last_of_month(prior)
    cur_m = first_of_month(TODAY)
    cur_end = last_of_month(cur_m)
    insert_filing(
        org, o2, f'NCA-CMSR-{prior.year}-{prior.month:02d}',
        'CLOSED', prior, prior_end, prior_end + timedelta(days=14),
        submitted_at=datetime.combine(prior_end + timedelta(days=10),
                                       datetime.min.time().replace(hour=13)),
        ack_ref=f'NCA-HSN-{prior.year}-{prior.month:02d}-22817',
        acknowledged_at=datetime.combine(prior_end + timedelta(days=11),
                                          datetime.min.time()),
        closed_at=datetime.combine(prior_end + timedelta(days=14),
                                    datetime.min.time()),
        prep_notes_en='Zero P1 incidents; 3 P2 (resolved within 24h).',
        prep_notes_ar='لا حوادث من الفئة الأولى؛ 3 حوادث من الفئة الثانية (حُلّت خلال 24 ساعة).',
    )
    insert_filing(
        org, o2, f'NCA-CMSR-{cur_m.year}-{cur_m.month:02d}',
        'IN_PREPARATION', cur_m, cur_end, cur_end + timedelta(days=14),
    )

    # SAMA-AMLM quarterly enhanced (digital channels)
    o3 = insert_obligation(
        org, 'SAMA-AMLM', 'SAMA', 'MONTHLY',
        'SAMA Monthly AML/CFT Returns — digital-channel enhanced',
        'تقرير غسل الأموال وتمويل الإرهاب الشهري — مُعزَّز للقنوات الرقمية',
        20, 'COMPLIANCE_OFFICER', 'CRO',
        'SAMA AML/CFT Rules 2018 — Article 35 + digital-channel addendum',
        'SAMA RBS portal',
        'STR statistics across mobile + USSD channels, voice-biometric '
        'spoofing-attempt log, and customer-onboarding velocity anomalies.',
        'إحصاءات بلاغات المعاملات المشبوهة على القنوات النقّالة وUSSD، '
        'سجل محاولات انتحال البصمة الصوتية، وشذوذ سرعة إدخال العملاء.',
    )
    insert_filing(
        org, o3, f'SAMA-AMLM-{prior.year}-{prior.month:02d}',
        'CLOSED', prior, prior_end, prior_end + timedelta(days=28),
        submitted_at=datetime.combine(prior_end + timedelta(days=24),
                                       datetime.min.time().replace(hour=14)),
        ack_ref=f'SAMA-AMLM-{prior.year}-{prior.month:02d}-{org}-01504',
        acknowledged_at=datetime.combine(prior_end + timedelta(days=25),
                                          datetime.min.time()),
        closed_at=datetime.combine(prior_end + timedelta(days=30),
                                    datetime.min.time()),
    )
    insert_filing(
        org, o3, f'SAMA-AMLM-{cur_m.year}-{cur_m.month:02d}',
        'APPROVED', cur_m, cur_end, cur_end + timedelta(days=28),
        prep_notes_en='Voice-biometric spoofing attempts up 12% MoM; '
                      'CRO sign-off pending.',
        prep_notes_ar='ارتفاع محاولات انتحال البصمة الصوتية بنسبة 12% شهرياً؛ '
                      'بانتظار توقيع مدير المخاطر.',
    )

    print('  ✓ STC Bank obligations + filings seeded')


# ─── Main ────────────────────────────────────────────────────────

def main() -> None:
    print(">>> Seeding regreporting catalog + live filings for 3 tenants")
    try:
        seed_tss_grc()
        seed_aramco_trading()
        seed_stc_bank()
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"! seed failed: {e}")
        sys.exit(1)

    cur.execute('SELECT organisation_id, COUNT(*) FROM regreporting.regreport_obligation '
                'WHERE is_deleted = 0 GROUP BY organisation_id ORDER BY organisation_id')
    print('\n--- Obligations per org:')
    for row in cur.fetchall():
        print(f'   org={row[0]}: {row[1]} obligations')

    cur.execute('SELECT organisation_id, COUNT(*) FROM regreporting.regreport_filing '
                'WHERE is_deleted = 0 GROUP BY organisation_id ORDER BY organisation_id')
    print('\n--- Filings per org:')
    for row in cur.fetchall():
        print(f'   org={row[0]}: {row[1]} filings')

    cur.execute('SELECT status, COUNT(*) FROM regreporting.regreport_filing '
                'WHERE is_deleted = 0 GROUP BY status ORDER BY status')
    print('\n--- Filings by status:')
    for row in cur.fetchall():
        print(f'   {row[0]}: {row[1]}')

    print('\n✓ done')


if __name__ == '__main__':
    main()
