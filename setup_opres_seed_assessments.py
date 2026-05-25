"""Seed Saudi-realistic OpRes assessments + cells for 3 tenants.

Sprint 14 PR #11. Pairs with PRs #229 (backend), #230 (F8 rules),
#231 (frontend).

Creates two assessment cycles per tenant:
  FY2024 — CLOSED. Full 5-pillar profile, all cells scored,
           bilingual rationale, evidence references. Approved
           and closed so it shows in history.
  FY2025 — IN_PROGRESS. Partial fill (3 of 5 cells), reflecting
           live mid-cycle work — gives the dashboard a non-empty
           "currently active" surface to render.

Tenants seeded (matches seed_real_data.py demographics):

  org=1  TSS GRC          — Saudi bank reference tenant; SADAD/IPS
                            heavy, T24 core, mature BCM org.
  org=2  Aramco Trading   — Energy + trading; emphasis on pipeline
                            SCADA + treasury continuity.
  org=3  STC Bank         — Telco-banking; USSD *900# + HSM-based
                            mobile-money critical path.

The Saudi-native Arabic rationale strings reflect each tenant's
business profile — not boilerplate. Operator-natural wording, not
literal translation.

Each INSERT is guarded so re-runs are safe.

Usage:
  python setup_opres_seed_assessments.py
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

# ─── Pillar reference ─────────────────────────────────────────────
# id, code, sort_order from V200__opres_seed.sql
PILLARS = {
    'GOVERNANCE':      1,
    'IBS_MAPPING':     2,
    'TOLERANCES':      3,
    'MAPPING_TESTING': 4,
    'LESSONS':         5,
}

# ─── Tenant profiles ──────────────────────────────────────────────
TENANTS = [
    {
        'org': 1,
        'label': 'TSS GRC',
        # Saudi-bank reference tenant — mature programme, top-quartile.
        'fy2024': {  # last cycle, closed
            'avg_current': 3.4, 'avg_target': 4.0,
            'cells': {
                'GOVERNANCE':      (4, 4,
                    'Board owns Op-Res through the BRCC; CRO is the accountable senior '
                    'executive; programme is in policy framework v3.2 and ERM appetite.',
                    'يملك المجلس الصمود التشغيلي عبر لجنة المخاطر الفرعية؛ مدير المخاطر '
                    'هو التنفيذي الأوّل المساءَل؛ البرنامج موثّق ضمن إطار السياسات الإصدار 3.2 '
                    'وضمن شهية المخاطر المؤسسية.',
                    'POLICY-FRAMEWORK-V3.2 / BRCC-2024-Q3-MIN'),
                'IBS_MAPPING':     (3, 4,
                    'IBS catalogue has 14 services mapped — retail payments (SADAD, mada, '
                    'IPS), corporate cash management, FX trading. Gap: digital wealth onboarding '
                    'not yet classified.',
                    'سجل الخدمات التجارية المهمّة يضم 14 خدمة — المدفوعات الأفراد (سداد، مدى، '
                    'النظام الفوري)، إدارة النقد للشركات، تداول العملات الأجنبية. الفجوة: '
                    'تأهيل العملاء للثروات الرقمية لم يُصنَّف بعد.',
                    'IBS-CATALOG-2024 / SAMA-OPRES-§4.1'),
                'TOLERANCES':      (3, 4,
                    'Quantified impact tolerances board-approved for the 10 most critical IBS. '
                    'Tolerances expressed as MTPD in hours + customer-impact ceiling.',
                    'حدود التحمّل المُكمَّمة معتمدة من المجلس لأهمّ 10 خدمات. الحدود معبّر عنها '
                    'بأقصى مدّة انقطاع مسموح بها بالساعات وسقف العملاء المتأثّرين.',
                    'TOLERANCES-2024 / BOARD-2024-MAR'),
                'MAPPING_TESTING': (3, 4,
                    'Resource map maintained for 12 IBS. 4 severe-but-plausible exercises run in '
                    '2024 — power+telco dual-failure, ransomware, key-vendor outage, IPS interbank '
                    'failure. AAR cycle closed for all.',
                    'خريطة الموارد محدَّثة لـ 12 خدمة. أُجريت 4 تمارين شديدة محتملة في 2024 — '
                    'انقطاع كهرباء واتصالات مزدوج، فدية، انقطاع مورِّد رئيسي، تعطّل النظام '
                    'الفوري. مراجعة الدروس مغلقة للجميع.',
                    'EXERCISE-CYCLE-2024 / AAR-LOG-2024'),
                'LESSONS':         (4, 4,
                    'Lessons-learned register live; 18 actions raised in 2024, 16 closed, 2 in CAPA. '
                    'Quarterly trend reporting to BRCC.',
                    'سجل الدروس فعّال؛ 18 إجراءً مُسجّلاً في 2024، أُغلق 16 منها، 2 في الإجراءات '
                    'التصحيحية. تقارير اتجاه فصلية تُرفع للجنة المخاطر.',
                    'LESSONS-REG-2024-Q4 / CAPA-OPRES-2024-005,007'),
            },
        },
        'fy2025': {  # current, in progress
            'cells': {
                'GOVERNANCE':      (4, 5,
                    'Continuing Q4-2024 baseline. Target raised to Optimising — embedding Op-Res '
                    'KPIs into executive comp plan from Q2-2025.',
                    'متابعة الأساس من الربع الأخير 2024. رُفع المستهدف إلى مستوى التحسين — '
                    'بدء دمج مؤشّرات الصمود التشغيلي في خطة التعويض التنفيذي من الربع الثاني 2025.',
                    'POLICY-FRAMEWORK-V3.3-DRAFT'),
                'IBS_MAPPING':     (4, 5,
                    'Digital wealth onboarding now mapped (closes 2024 gap). Reviewing IPS+SADAD '
                    'dependency on Tier-2 SCM providers.',
                    'تأهيل العملاء للثروات الرقمية مُدرَج الآن في الخريطة (يغلق فجوة 2024). '
                    'مراجعة اعتماد النظام الفوري وسداد على مزوّدي الفئة الثانية.',
                    'IBS-CATALOG-2025-Q1'),
                'TOLERANCES':      (3, 4,
                    'Tolerances under refresh — 3 of 10 reviewed YTD. SAMA Op-Res §4.3 review '
                    'cycle ends Q3-2025.',
                    'حدود التحمّل قيد التحديث — 3 من أصل 10 خضعت للمراجعة منذ بداية السنة. '
                    'دورة المراجعة وفق المادة 4.3 لساما تنتهي في الربع الثالث 2025.',
                    'TOLERANCES-2025-DRAFT'),
                # Remaining 2 cells left blank — in-progress signal
            },
        },
    },
    {
        'org': 2,
        'label': 'Aramco Trading',
        # Energy + trading — pipeline SCADA, treasury, TRM platforms.
        'fy2024': {
            'avg_current': 2.8, 'avg_target': 3.8,
            'cells': {
                'GOVERNANCE':      (3, 4,
                    'CRO owns Op-Res; quarterly review through Trading Operating Committee. '
                    'Standalone Op-Res charter under board review.',
                    'مدير المخاطر يملك الصمود التشغيلي؛ مراجعة فصلية عبر لجنة عمليات التداول. '
                    'ميثاق مستقلّ للصمود التشغيلي قيد مراجعة المجلس.',
                    'TOC-CHARTER-V2 / BOARD-2024-NOV'),
                'IBS_MAPPING':     (3, 4,
                    'IBS catalogue covers 9 services — crude+products trading desks (Eka TRM), '
                    'treasury cash sweep, pipeline SCADA telemetry, customer settlement.',
                    'سجل الخدمات يغطّي 9 خدمات — مكاتب تداول الخام والمنتجات على منصّة Eka TRM، '
                    'حركة النقد للخزينة، قياس عن بُعد لسكادا الأنابيب، تسوية العملاء.',
                    'IBS-CATALOG-TRADING-2024'),
                'TOLERANCES':      (2, 3,
                    'Tolerances draft for trading desks only; pipeline SCADA tolerance not yet '
                    'agreed with Saudi Aramco upstream operations.',
                    'مسوّدة الحدود لمكاتب التداول فقط؛ حدود سكادا الأنابيب لم تُعتمَد بعد مع '
                    'عمليات أعلى السلسلة في أرامكو السعودية.',
                    'TOLERANCES-TRADING-DRAFT-2024'),
                'MAPPING_TESTING': (3, 4,
                    '2 tabletop exercises in 2024 — SCADA telemetry loss, Eka platform 4-hour '
                    'outage. Severe-but-plausible scenario library now tracks 8 scenarios.',
                    'تمرينان طاولة في 2024 — فقد قياس سكادا، انقطاع منصّة Eka لأربع ساعات. '
                    'مكتبة السيناريوهات الشديدة المحتملة تتتبّع الآن 8 سيناريوهات.',
                    'EXERCISE-LOG-2024 / SCENARIO-LIB-TRADING'),
                'LESSONS':         (3, 4,
                    'Lessons-learned register active; biggest theme is pipeline SCADA failover '
                    'latency (RTO 30min vs target 15min).',
                    'سجل الدروس نشط؛ الموضوع الأبرز هو زمن انتقال احتياطي سكادا الأنابيب '
                    '(زمن الاسترداد المستهدف 15 دقيقة مقابل الفعلي 30).',
                    'LESSONS-REG-2024-Q4'),
            },
        },
        'fy2025': {
            'cells': {
                'GOVERNANCE':      (3, 4,
                    'Charter approval pending — board agenda Q1-2025. Three-lines model '
                    'documented but second-line independence still being matured.',
                    'الميثاق قيد الاعتماد — أجندة المجلس للربع الأوّل 2025. نموذج الخطوط '
                    'الثلاثة موثّق لكن استقلال الخط الثاني لا يزال في طور النضج.',
                    'CHARTER-V3-DRAFT'),
                'IBS_MAPPING':     (3, 4,
                    'Adding LNG trading desk to the IBS catalogue following new business line.',
                    'إضافة مكتب تداول الغاز الطبيعي المسال إلى سجل الخدمات المهمّة عقب إطلاق '
                    'خط الأعمال الجديد.',
                    'IBS-CATALOG-TRADING-2025-Q1'),
                # Remaining 3 cells left blank — early-cycle signal
            },
        },
    },
    {
        'org': 3,
        'label': 'STC Bank',
        # Telco-banking — USSD *900#, HSM cluster, voice biometrics.
        'fy2024': {
            'avg_current': 2.6, 'avg_target': 3.6,
            'cells': {
                'GOVERNANCE':      (3, 4,
                    'CISO acts as accountable senior executive; CRO and BCM Manager jointly run '
                    'the workbench. Banking-licence dual-regulation (CITC + SAMA) brings extra '
                    'governance complexity.',
                    'يقوم مدير أمن المعلومات بدور التنفيذي الأوّل المساءَل؛ مدير المخاطر ومدير '
                    'استمرارية الأعمال يتشاركان إدارة الورشة. الترخيص المصرفي بإشراف ثنائي '
                    '(هيئة الاتصالات + ساما) يضيف تعقيداً في الحوكمة.',
                    'GOV-CHARTER-2024'),
                'IBS_MAPPING':     (2, 3,
                    'IBS catalogue v1 covers core mobile-money flows (USSD *900#, HSM-signed '
                    'transactions, voice-biometric authentication). Refresh scheduled.',
                    'الإصدار الأوّل من سجل الخدمات يغطّي تدفّقات المحفظة الجوّالة الأساسية '
                    '(USSD ‎*900#، عمليات موقّعة من وحدة الأجهزة الأمنية، التحقّق الصوتي). '
                    'التحديث مُجدوَل.',
                    'IBS-CATALOG-MOBILE-2024'),
                'TOLERANCES':      (2, 3,
                    'Tolerances drafted for top-3 services. Voice-biometric system has no '
                    'documented tolerance — first telco-banking control of its kind in Saudi.',
                    'مسوّدة حدود التحمّل للخدمات الثلاث الأولى. النظام البيومتري الصوتي لا يملك '
                    'حدّاً موثّقاً — أوّل ضابط من نوعه في المصرفية الاتصالاتية بالمملكة.',
                    'TOLERANCES-DRAFT-2024'),
                'MAPPING_TESTING': (3, 4,
                    '3 exercises in 2024 — SS7/SIGTRAN intercept simulation, HSM-cluster failover, '
                    'USSD platform overload. Lessons fed into 2025 roadmap.',
                    '3 تمارين في 2024 — محاكاة اعتراض SS7/SIGTRAN، احتياطي مجموعة وحدات الأجهزة '
                    'الأمنية، إجهاد منصّة USSD. الدروس أُدرجت في خارطة طريق 2025.',
                    'EXERCISE-LOG-2024'),
                'LESSONS':         (3, 3,
                    'Lessons register live but no formal continuous-improvement KPI yet. Trending '
                    'theme: SIM-swap fraud cascading into Op-Res events.',
                    'سجل الدروس فعّال لكن لا يوجد مؤشّر تحسين مستمرّ رسمي بعد. الموضوع المتكرّر: '
                    'احتيال تبديل شريحة الاتصال يتحوّل إلى أحداث صمود تشغيلي.',
                    'LESSONS-REG-2024-Q4'),
            },
        },
        'fy2025': {
            'cells': {
                'GOVERNANCE':      (3, 4,
                    'Q1-2025 board session approved a dedicated Op-Res sub-committee under the '
                    'BRCC — first meeting Feb 2025.',
                    'اعتمد المجلس في الربع الأوّل 2025 لجنة فرعية مخصّصة للصمود التشغيلي تحت '
                    'لجنة المخاطر — أوّل اجتماع فبراير 2025.',
                    'BOARD-MIN-2025-JAN'),
                # Remaining 4 cells left blank — DRAFT signal
            },
        },
    },
]


def assessment_exists(org_id: int, fy: int) -> int | None:
    cur.execute(
        'SELECT id FROM opres.opres_assessment '
        'WHERE organisation_id = ? AND fiscal_year = ? AND is_deleted = 0',
        org_id, fy,
    )
    row = cur.fetchone()
    return int(row[0]) if row else None


def insert_assessment(org_id: int, fy: int, status: str, title_en: str, title_ar: str,
                      due_date: date, avg_cur: float | None, avg_tgt: float | None,
                      completeness: str, submitted_at: datetime | None,
                      approved_at: datetime | None, approved_by: int | None,
                      closed_at: datetime | None, exec_en: str | None,
                      exec_ar: str | None, now: datetime) -> int:
    cur.execute(
        '''INSERT INTO opres.opres_assessment
        (organisation_id, fiscal_year, title_en, title_ar, status, due_date,
         sponsor_user_id, avg_current_score, avg_target_score, completeness,
         submitted_at, approved_at, approved_by_user_id, closed_at,
         executive_summary_en, executive_summary_ar,
         created_at, updated_at, created_by, updated_by, is_deleted)
        OUTPUT INSERTED.id
        VALUES (?, ?, ?, ?, ?, ?,
                1, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, 1, 1, 0)''',
        org_id, fy, title_en, title_ar, status, due_date,
        avg_cur, avg_tgt, completeness, submitted_at, approved_at,
        approved_by, closed_at, exec_en, exec_ar, now, now,
    )
    return int(cur.fetchone()[0])


def insert_cell(org_id: int, assessment_id: int, pillar_code: str,
                cur_score: int, tgt_score: int, rationale_en: str,
                rationale_ar: str, evidence: str, now: datetime) -> None:
    pillar_id = PILLARS[pillar_code]
    cur.execute(
        '''INSERT INTO opres.opres_assessment_cell
        (organisation_id, assessment_id, pillar_id, pillar_code_snapshot,
         current_score, target_score, rationale_en, rationale_ar,
         evidence_reference, last_edited_by_user_id,
         created_at, updated_at, created_by, updated_by, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1,
                ?, ?, 1, 1, 0)''',
        org_id, assessment_id, pillar_id, pillar_code,
        cur_score, tgt_score, rationale_en, rationale_ar, evidence,
        now, now,
    )


def seed_tenant(t: dict) -> tuple[int, int]:
    org = t['org']
    label = t['label']
    inserted_a = 0
    inserted_c = 0
    now = datetime.now()

    # FY2024 — closed
    fy = 2024
    if assessment_exists(org, fy):
        print(f"  · {label} FY{fy}: already seeded, skipping")
    else:
        data = t['fy2024']
        cells_count = len(data['cells'])
        submitted = datetime(2024, 9, 15)
        approved  = datetime(2024, 10, 5)
        closed    = datetime(2024, 10, 20)
        exec_en = (
            f"FY2024 Op-Res self-assessment closed at avg current "
            f"{data['avg_current']:.1f} / target {data['avg_target']:.1f} maturity. "
            f"All 5 SAMA pillars covered. See pillar-level rationale + evidence "
            f"references for sources."
        )
        exec_ar = (
            f"أُغلق التقييم الذاتي للصمود التشغيلي للسنة المالية 2024 بمتوسّط نضج "
            f"حالي {data['avg_current']:.1f} ومستهدف {data['avg_target']:.1f}. "
            f"جميع ركائز ساما الخمس مغطّاة. للمصادر يُرجى مراجعة مبرّر الركيزة "
            f"ومراجع الأدلّة."
        )
        a_id = insert_assessment(
            org, fy, 'CLOSED',
            f'FY{fy} SAMA Op-Res Annual Self-Assessment',
            f'التقييم الذاتي السنوي للصمود التشغيلي {fy}',
            date(2024, 9, 30),
            data['avg_current'], data['avg_target'],
            f'{cells_count}/5',
            submitted, approved, 1, closed, exec_en, exec_ar, now,
        )
        for code, (cur_s, tgt_s, en, ar, ev) in data['cells'].items():
            insert_cell(org, a_id, code, cur_s, tgt_s, en, ar, ev, now)
            inserted_c += 1
        inserted_a += 1
        print(f"  ✓ {label} FY{fy}: assessment #{a_id} (CLOSED), {cells_count} cells")

    # FY2025 — in progress
    fy = 2025
    if assessment_exists(org, fy):
        print(f"  · {label} FY{fy}: already seeded, skipping")
    else:
        data = t['fy2025']
        cells_count = len(data['cells'])
        # Compute partial avg from cells we have.
        cur_scores = [v[0] for v in data['cells'].values()]
        tgt_scores = [v[1] for v in data['cells'].values()]
        avg_cur = round(sum(cur_scores) / len(cur_scores), 2) if cur_scores else None
        avg_tgt = round(sum(tgt_scores) / len(tgt_scores), 2) if tgt_scores else None
        a_id = insert_assessment(
            org, fy,
            'DRAFT' if cells_count == 1 else 'IN_PROGRESS',
            f'FY{fy} SAMA Op-Res Annual Self-Assessment',
            f'التقييم الذاتي السنوي للصمود التشغيلي {fy}',
            date(2025, 9, 30),
            avg_cur, avg_tgt,
            f'{cells_count}/5',
            None, None, None, None, None, None, now,
        )
        for code, (cur_s, tgt_s, en, ar, ev) in data['cells'].items():
            insert_cell(org, a_id, code, cur_s, tgt_s, en, ar, ev, now)
            inserted_c += 1
        inserted_a += 1
        print(f"  ✓ {label} FY{fy}: assessment #{a_id} (IN_PROGRESS), {cells_count} cells")

    return inserted_a, inserted_c


def main() -> None:
    print(">>> Seeding OpRes assessments + cells for 3 tenants")
    total_a = 0
    total_c = 0
    try:
        for t in TENANTS:
            print(f"-- org={t['org']} ({t['label']}) --")
            a, c = seed_tenant(t)
            total_a += a
            total_c += c
        conn.commit()
        print(f"✓ done — {total_a} assessments + {total_c} cells inserted")
    except Exception as e:
        conn.rollback()
        print(f"! rolled back due to: {e}")
        raise


if __name__ == '__main__':
    main()
