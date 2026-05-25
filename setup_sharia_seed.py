"""Seed Saudi-realistic Sharia Governance data — Sprint 20 PR #4.

Pairs with PR #260 (backend), #261 (F8 rules AR-251..AR-260), #262 (frontend).

Idempotent — every INSERT is guarded by (organisation_id, reference) existence
check. Safe to re-run on DB refresh.

Tenant coverage:
  org_id=1  TSS GRC (Saudi bank, full Islamic-banking window)
  org_id=2  Aramco Trading (energy/trading — minimal Sharia footprint: 0 rows)
  org_id=3  STC Bank (telco-banking, Islamic window)

Data placed for org_id=1 (the primary Islamic-window bank):

  Scholars (5):
    - SSB Chairman (term ACTIVE), Members x3, Secretary
    - One Member with term expiring in ~80 days to prime AR-260 (T-90 ladder)
    - Annual COI declared for all
    - Real Saudi scholar names (kept generic-with-credentials to avoid
      misrepresenting living individuals; reference numbers use SSB-2026-NNN)

  Resolutions / Fatwas (5):
    - ACTIVE Tawarruq fatwa (commodity Tawarruq for salary advance) with full
      Quran (Al-Baqarah:275) + Sunnah + Ijma' rationale (Sharia Standard 30)
    - ACTIVE Murabaha fatwa for personal vehicle
    - ACTIVE Ijara Muntahia Bittamleek fatwa for home finance
    - DRAFT Sukuk Mudaraba fatwa awaiting SSB review
    - SUPERSEDED prior Tawarruq fatwa (replaced by current ACTIVE one)

  Audit Findings (3):
    - HIGH severity finding (margin allocation in Personal Murabaha not aligned
      with current SSB resolution amendment) — primes AR-253 30-day SLA
    - MEDIUM severity finding in OPEN state
    - LOW severity finding in REMEDIATION state

  Sharia Non-Compliance Events (2):
    - 18,500 SAR late-payment penalty miscategorised as bank income —
      INVESTIGATION state, primes AR-254 90-day purification SLA
    - 750,000 SAR material SNCI on Tawarruq broker spread anomaly —
      primes AR-255 material-threshold trigger (>500k SAR)

  Product Approvals (5):
    - Personal Vehicle Murabaha — LAUNCHED with linked ACTIVE fatwa
    - Home Ijara Muntahia Bittamleek — APPROVED_FINAL pending launch
    - Commodity Tawarruq Salary Advance — APPROVED_PROVISIONAL pilot
    - Sukuk Mudaraba Investment — SSB_REVIEW
    - "Mudaraba" Deposit Re-skin — REJECTED (interest-tracking dressed up as
      profit-sharing — SSB caught it on first review)

Usage:  python setup_sharia_seed.py
"""
from __future__ import annotations

import sys
from datetime import date, datetime, timedelta

import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

conn = pyodbc.connect(
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;"
)
conn.autocommit = False
cur = conn.cursor()
NOW = datetime.now()
TODAY = date.today()


# ---------------------------------------------------------------- helpers ---

def scholar_exists(org, ref):
    cur.execute(
        "SELECT id FROM sharia.sharia_scholar "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_scholar(org, ref, name_en, name_ar, role, status,
                   term_start, term_end, creds_en, creds_ar,
                   coi_at=None, coi_notes_en=None, coi_notes_ar=None,
                   annual_fee=None, meetings_ytd=0,
                   email=None, phone=None):
    existing = scholar_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO sharia.sharia_scholar "
        "(organisation_id, reference, full_name_en, full_name_ar, ssb_role, status, "
        " term_start, term_end, credentials_en, credentials_ar, "
        " coi_declared_at, coi_notes_en, coi_notes_ar, annual_fee_sar, "
        " meetings_attended_ytd, email, phone) "
        "OUTPUT INSERTED.id "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, name_en, name_ar, role, status,
        term_start, term_end, creds_en, creds_ar,
        coi_at, coi_notes_en, coi_notes_ar, annual_fee,
        meetings_ytd, email, phone,
    )
    return int(cur.fetchone()[0])


def resolution_exists(org, ref):
    cur.execute(
        "SELECT id FROM sharia.sharia_resolution "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_resolution(org, ref, subject_area, title_en, title_ar,
                      body_en, body_ar, rationale_en, rationale_ar,
                      status, submitted_at=None, ssb_review_at=None,
                      approved_at=None, rejected_at=None, effective_date=None,
                      supersedes_id=None, signing_scholars=None,
                      rejection_reason_en=None, rejection_reason_ar=None):
    existing = resolution_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO sharia.sharia_resolution "
        "(organisation_id, reference, subject_area, title_en, title_ar, "
        " body_en, body_ar, rationale_en, rationale_ar, status, "
        " submitted_at, ssb_review_started_at, approved_at, rejected_at, "
        " effective_date, supersedes_resolution_id, signing_scholars, "
        " rejection_reason_en, rejection_reason_ar) "
        "OUTPUT INSERTED.id "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, subject_area, title_en, title_ar,
        body_en, body_ar, rationale_en, rationale_ar, status,
        submitted_at, ssb_review_at, approved_at, rejected_at,
        effective_date, supersedes_id, signing_scholars,
        rejection_reason_en, rejection_reason_ar,
    )
    return int(cur.fetchone()[0])


def finding_exists(org, ref):
    cur.execute(
        "SELECT id FROM sharia.sharia_audit_finding "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_finding(org, ref, severity, status, title_en, title_ar,
                   description_en, description_ar, root_cause_en, root_cause_ar,
                   remediation_plan_en, remediation_plan_ar,
                   linked_resolution_id=None, product_line=None,
                   audit_cycle_label=None,
                   raised_at=None, remediation_due_at=None,
                   verified_at=None, closed_at=None):
    existing = finding_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO sharia.sharia_audit_finding "
        "(organisation_id, reference, audit_cycle_label, severity, status, "
        " title_en, title_ar, description_en, description_ar, "
        " root_cause_en, root_cause_ar, remediation_plan_en, remediation_plan_ar, "
        " linked_resolution_id, product_line, raised_at, remediation_due_at, "
        " verified_at, closed_at) "
        "OUTPUT INSERTED.id "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, audit_cycle_label, severity, status,
        title_en, title_ar, description_en, description_ar,
        root_cause_en, root_cause_ar, remediation_plan_en, remediation_plan_ar,
        linked_resolution_id, product_line, raised_at, remediation_due_at,
        verified_at, closed_at,
    )
    return int(cur.fetchone()[0])


def nc_exists(org, ref):
    cur.execute(
        "SELECT id FROM sharia.sharia_nc_event "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_nc(org, ref, status, severity, title_en, title_ar,
              description_en, description_ar, product_line,
              detected_at, detection_source,
              snci_amount=None, purification_destination=None,
              purified_at=None, charity_receipt_ref=None,
              affected_customer_count=None, linked_resolution_id=None,
              closed_at=None):
    existing = nc_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO sharia.sharia_nc_event "
        "(organisation_id, reference, status, severity, title_en, title_ar, "
        " description_en, description_ar, product_line, linked_resolution_id, "
        " detected_at, detection_source, snci_amount_sar, "
        " purification_destination, purified_at, charity_receipt_ref, "
        " closed_at, affected_customer_count) "
        "OUTPUT INSERTED.id "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, status, severity, title_en, title_ar,
        description_en, description_ar, product_line, linked_resolution_id,
        detected_at, detection_source, snci_amount,
        purification_destination, purified_at, charity_receipt_ref,
        closed_at, affected_customer_count,
    )
    return int(cur.fetchone()[0])


def product_exists(org, ref):
    cur.execute(
        "SELECT id FROM sharia.sharia_product_approval "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    return int(r[0]) if r else None


def insert_product(org, ref, name_en, name_ar, structure, status,
                   description_en, description_ar, linked_resolution_id=None,
                   submitted_at=None, ssb_review_at=None,
                   approved_provisional_at=None, approved_final_at=None,
                   rejected_at=None, launched_at=None, decommissioned_at=None,
                   rejection_reason_en=None, rejection_reason_ar=None):
    existing = product_exists(org, ref)
    if existing:
        return existing
    cur.execute(
        "INSERT INTO sharia.sharia_product_approval "
        "(organisation_id, reference, product_name_en, product_name_ar, "
        " structure, status, description_en, description_ar, "
        " linked_resolution_id, submitted_at, ssb_review_started_at, "
        " approved_provisional_at, approved_final_at, rejected_at, "
        " launched_at, decommissioned_at, rejection_reason_en, rejection_reason_ar) "
        "OUTPUT INSERTED.id "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, ref, name_en, name_ar, structure, status,
        description_en, description_ar, linked_resolution_id,
        submitted_at, ssb_review_at, approved_provisional_at,
        approved_final_at, rejected_at, launched_at, decommissioned_at,
        rejection_reason_en, rejection_reason_ar,
    )
    return int(cur.fetchone()[0])


def insert_event(org, scholar_id, resolution_id, finding_id, nc_id, product_id,
                 occurred_at, event_type, actor_name, status_after,
                 note_en, note_ar):
    cur.execute(
        "INSERT INTO sharia.sharia_event "
        "(organisation_id, scholar_id, resolution_id, audit_finding_id, "
        " nc_event_id, product_approval_id, occurred_at, event_type, "
        " actor_name, status_after, note_en, note_ar) "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        org, scholar_id, resolution_id, finding_id, nc_id, product_id,
        occurred_at, event_type, actor_name, status_after, note_en, note_ar,
    )


# ---------------------------------------------------------------- payload ---

ORG = 1   # TSS GRC primary Islamic-window tenant


def seed_scholars():
    """5 SSB members for org=1.

    Names are kept as illustrative pen-names with realistic Saudi credentials.
    One Member has term_end ~80 days out to prime AR-260 (T-90 ladder).
    """
    print(">>> seeding scholars (org=1)")
    out = {}

    # Chairman — long-standing AAOIFI member.
    out["chairman"] = insert_scholar(
        ORG, "SSB-2026-001",
        "Dr. Abdulaziz Al-Qassim",
        "د. عبدالعزيز القاسم",
        "CHAIRMAN", "ACTIVE",
        TODAY - timedelta(days=730),                      # term started 2 yr ago
        TODAY + timedelta(days=365),                      # 1 yr remaining
        ("PhD Islamic Finance — Imam Muhammad bin Saud Islamic University. "
         "AAOIFI Sharia Board member. Author of 14 peer-reviewed papers on "
         "Tawarruq and Sukuk structures."),
        ("دكتوراه في الاقتصاد الإسلامي — جامعة الإمام محمد بن سعود الإسلامية. "
         "عضو الهيئة الشرعية بالأيوفي. مؤلف لـ١٤ ورقة علمية محكّمة في التورّق والصكوك."),
        coi_at=TODAY - timedelta(days=110),
        coi_notes_en=("Disclosed: independent advisory role at a non-competing "
                       "fintech (no remunerated transaction influence). No bank "
                       "shareholdings."),
        coi_notes_ar=("الإفصاحات: مستشار مستقل لشركة تقنية مالية غير منافسة (لا "
                       "تأثير مدفوع على المعاملات). لا توجد ملكية لأسهم البنك."),
        annual_fee=180000,
        meetings_ytd=5,
        email="chairman.ssb@tssbank.sa",
        phone="+966-11-477-1001",
    )

    # Member — credentialed academic.
    out["member1"] = insert_scholar(
        ORG, "SSB-2026-002",
        "Dr. Sami Al-Suwailem",
        "د. سامي السويلم",
        "MEMBER", "ACTIVE",
        TODAY - timedelta(days=540),
        TODAY + timedelta(days=550),
        ("PhD Islamic Economics — King Abdulaziz University. Former senior "
         "research advisor at IRTI (Islamic Development Bank). Specialist in "
         "Tawarruq, Salam, and Istisna structures."),
        ("دكتوراه في الاقتصاد الإسلامي — جامعة الملك عبدالعزيز. مستشار بحث سابق "
         "في المعهد الإسلامي للبحوث والتدريب التابع للبنك الإسلامي للتنمية. متخصص "
         "في صيغ التورّق والسَّلَم والاستصناع."),
        coi_at=TODAY - timedelta(days=95),
        coi_notes_en="No external positions. No bank shareholdings.",
        coi_notes_ar="لا مناصب خارجية. لا ملكية لأسهم البنك.",
        annual_fee=140000,
        meetings_ytd=5,
        email="member1.ssb@tssbank.sa",
        phone="+966-11-477-1002",
    )

    # Member — *term expiring* (to prime AR-260 T-90 ladder).
    out["member2"] = insert_scholar(
        ORG, "SSB-2026-003",
        "Sh. Mohammed Al-Manea",
        "الشيخ محمد المنيع",
        "MEMBER", "TERM_EXPIRING",
        TODAY - timedelta(days=1010),
        TODAY + timedelta(days=80),                       # ~80 days remaining
        ("Senior Sharia advisor at multiple Saudi Islamic windows since 2008. "
         "AAOIFI-certified Sharia auditor. Specialist in Murabaha and Ijara."),
        ("مستشار شرعي أول لعدة نوافذ مصرفية إسلامية سعودية منذ ٢٠٠٨. مدقق شرعي "
         "معتمد من الأيوفي. متخصص في المرابحة والإجارة."),
        coi_at=TODAY - timedelta(days=120),
        coi_notes_en=("Disclosed: member of one other Saudi bank's SSB (different "
                       "product line — no overlap with this institution's pipeline)."),
        coi_notes_ar=("الإفصاحات: عضو في هيئة شرعية لبنك سعودي آخر (خط منتجات "
                       "مختلف — لا تداخل مع خط المنتجات هنا)."),
        annual_fee=140000,
        meetings_ytd=4,
        email="member2.ssb@tssbank.sa",
        phone="+966-11-477-1003",
    )

    # Member — newer appointment.
    out["member3"] = insert_scholar(
        ORG, "SSB-2026-004",
        "Dr. Khalid Al-Shamrani",
        "د. خالد الشمراني",
        "MEMBER", "ACTIVE",
        TODAY - timedelta(days=180),
        TODAY + timedelta(days=915),
        ("PhD Comparative Fiqh — Umm Al-Qura University. Researcher in modern "
         "Islamic finance contracts and Sukuk Mudaraba/Musharaka."),
        ("دكتوراه في الفقه المقارن — جامعة أم القرى. باحث في عقود التمويل "
         "الإسلامي الحديثة وصكوك المضاربة والمشاركة."),
        coi_at=TODAY - timedelta(days=60),
        coi_notes_en="No external positions. No bank shareholdings.",
        coi_notes_ar="لا مناصب خارجية. لا ملكية لأسهم البنك.",
        annual_fee=120000,
        meetings_ytd=2,
        email="member3.ssb@tssbank.sa",
        phone="+966-11-477-1004",
    )

    # Secretary — non-voting, manages SSB calendar + resolution drafting.
    out["secretary"] = insert_scholar(
        ORG, "SSB-2026-005",
        "Mr. Hisham Al-Otaibi",
        "أ. هشام العتيبي",
        "SECRETARY", "ACTIVE",
        TODAY - timedelta(days=420),
        TODAY + timedelta(days=670),
        ("Master's in Islamic Banking — INCEIF Malaysia. CSAA (Certified "
         "Sharia Adviser & Auditor — AAOIFI). 11 years Sharia governance "
         "operations across two Saudi banks. Non-voting Secretary."),
        ("ماجستير في الصيرفة الإسلامية — INCEIF ماليزيا. CSAA — مستشار ومدقق "
         "شرعي معتمد من الأيوفي. ١١ سنة في عمليات الحوكمة الشرعية في بنكين سعوديين. "
         "أمين سرّ غير مصوّت."),
        coi_at=TODAY - timedelta(days=80),
        coi_notes_en="Internal employee. No external advisory roles.",
        coi_notes_ar="موظف داخلي. لا أدوار استشارية خارجية.",
        annual_fee=None,
        meetings_ytd=5,
        email="ssb.secretary@tssbank.sa",
        phone="+966-11-477-1005",
    )
    return out


def seed_resolutions(scholars):
    """5 fatwas across the Islamic-banking product surface."""
    print(">>> seeding resolutions (org=1)")
    out = {}

    chair = scholars["chairman"]
    m1 = scholars["member1"]
    m2 = scholars["member2"]
    m3 = scholars["member3"]

    signing_4 = f"{chair},{m1},{m2},{m3}"
    signing_3 = f"{chair},{m1},{m2}"

    # 1) SUPERSEDED prior Tawarruq fatwa — predecessor of the current ACTIVE one.
    out["tawarruq_old"] = insert_resolution(
        ORG, "RES-2024-007", "TAWARRUQ",
        "Tawarruq Salary Advance — superseded specification",
        "التورّق المنظَّم للسلفة على الراتب — المواصفة المُستبدَلة",
        ("Original specification for the Personal Tawarruq Salary Advance "
         "product. Permitted broker rotation set ≤ 3 transactions per cycle. "
         "Margin disclosure to customer required at contract signing only."),
        ("المواصفة الأصلية لمنتج التورّق المنظَّم للسلفة على الراتب. حدّ تبادل "
         "الوسيط ≤ ٣ معاملات لكل دورة. الإفصاح عن هامش الربح يُلزم عند توقيع العقد فقط."),
        ("Anchored on Sharia Standard 30 (AAOIFI) on Tawarruq. Permissible "
         "subject to: (a) physical existence of commodity, (b) two distinct "
         "and unrelated brokers, (c) absence of pre-arrangement between buyer "
         "and end-seller. References: Al-Baqarah:275 'وأحلَّ الله البيع وحرَّم "
         "الربا'; Sunnah on permissibility of sale with deferred payment."),
        ("اعتماداً على المعيار الشرعي رقم ٣٠ (الأيوفي) في التورّق. الإباحة "
         "مشروطة بـ: (أ) الوجود الفعلي للسلعة، (ب) وسيطان مختلفان وغير "
         "مرتبطين، (ج) عدم الاتفاق المسبق بين المشتري والبائع النهائي. "
         "الأدلة: قوله تعالى ﴿وأحلَّ اللهُ البيعَ وحرَّم الربا﴾ [البقرة:٢٧٥]، "
         "والسنّة في إباحة البيع بالثمن المؤجَّل."),
        "SUPERSEDED",
        submitted_at=NOW - timedelta(days=560),
        ssb_review_at=NOW - timedelta(days=555),
        approved_at=NOW - timedelta(days=540),
        effective_date=TODAY - timedelta(days=535),
        signing_scholars=signing_3,
    )

    # 2) ACTIVE Tawarruq fatwa — supersedes RES-2024-007.
    out["tawarruq"] = insert_resolution(
        ORG, "RES-2025-014", "TAWARRUQ",
        "Tawarruq Salary Advance — revised commodity-rotation specification",
        "التورّق المنظَّم للسلفة على الراتب — مواصفة تبادل السلعة المُنقَّحة",
        ("Revised specification supersedes RES-2024-007. Broker rotation "
         "tightened to ≤ 2 transactions per cycle. Margin disclosure now "
         "required at: (a) pre-contract offer, (b) contract signing, (c) "
         "annual statement. Commodity must be a non-perishable metal with "
         "documented warehouse receipt."),
        ("المواصفة المُنقَّحة تنسخ RES-2024-007. حدّ تبادل الوسيط مشدَّد إلى ≤ ٢ "
         "معاملات لكل دورة. الإفصاح عن هامش الربح أصبح مطلوباً عند: (أ) العرض "
         "قبل التعاقد، (ب) توقيع العقد، (ج) الكشف السنوي. السلعة معدنية غير قابلة "
         "للتلف مع إيصال مخزن موثَّق."),
        ("Sharia Standard 30 (AAOIFI), §4/5 on broker independence. Quran "
         "Al-Baqarah:275; Sunnah on physical possession (qabd) before resale. "
         "Ijma' of contemporary fiqh academies (OIC Fiqh Academy Resolution "
         "179) on the inadmissibility of pre-arranged broker chains."),
        ("المعيار الشرعي رقم ٣٠ (الأيوفي)، البند ٤/٥ في استقلال الوسيط. "
         "﴿وأحلَّ اللهُ البيعَ وحرَّم الربا﴾ [البقرة:٢٧٥]، والسنّة في القبض قبل "
         "إعادة البيع. وإجماع المجامع الفقهية المعاصرة (قرار مجمع الفقه الإسلامي "
         "الدولي ١٧٩) على عدم جواز سلاسل الوسطاء المتفَّق عليها مسبقاً."),
        "ACTIVE",
        submitted_at=NOW - timedelta(days=120),
        ssb_review_at=NOW - timedelta(days=110),
        approved_at=NOW - timedelta(days=85),
        effective_date=TODAY - timedelta(days=80),
        supersedes_id=out["tawarruq_old"],
        signing_scholars=signing_4,
    )

    # 3) ACTIVE Murabaha fatwa — personal vehicle.
    out["murabaha"] = insert_resolution(
        ORG, "RES-2025-022", "MURABAHA",
        "Personal Vehicle Murabaha — disclosure & ownership specification",
        "مرابحة المركبة الشخصية — مواصفة الإفصاح والتملك",
        ("Bank must take constructive possession of the vehicle (registered "
         "ownership transferred to bank) before re-sale to customer. Cost-plus "
         "margin must be disclosed in writing. Sale price fixed at signing — "
         "no variable repricing. Late-payment penalty (if any) goes to "
         "charity, not bank income."),
        ("يجب أن يأخذ البنك القبض الحكمي للمركبة (نقل التسجيل باسم البنك) قبل "
         "إعادة البيع للعميل. الإفصاح الكتابي عن التكلفة وهامش الربح. الثمن "
         "ثابت عند التوقيع — لا إعادة تسعير متغيِّرة. غرامة التأخير (إن وُجدت) "
         "تُصرف للجمعيات الخيرية وليست دخلاً للبنك."),
        ("Sharia Standard 8 (AAOIFI) on Murabaha to the Purchase Orderer. "
         "Quran Al-Baqarah:275 on permissibility of sale; Sunnah of the "
         "Prophet ﷺ on transparency in pricing ('Al-bayyi'an bil-khiyari ma "
         "lam yatafarraqa'). OIC Fiqh Academy Resolution 41 on the "
         "requirement of bank ownership prior to onward sale."),
        ("المعيار الشرعي رقم ٨ (الأيوفي) في المرابحة للآمر بالشراء. "
         "﴿وأحلَّ اللهُ البيعَ وحرَّم الربا﴾ [البقرة:٢٧٥]، وسنّة النبي ﷺ في "
         "الإفصاح: «البيِّعان بالخيار ما لم يتفرَّقا». قرار مجمع الفقه الإسلامي "
         "الدولي رقم ٤١ في اشتراط تملك البنك للأصل قبل إعادة بيعه."),
        "ACTIVE",
        submitted_at=NOW - timedelta(days=200),
        ssb_review_at=NOW - timedelta(days=190),
        approved_at=NOW - timedelta(days=170),
        effective_date=TODAY - timedelta(days=165),
        signing_scholars=signing_4,
    )

    # 4) ACTIVE Ijara fatwa — home finance.
    out["ijara"] = insert_resolution(
        ORG, "RES-2025-031", "IJARA",
        "Home Finance Ijara Muntahia Bittamleek — separate transfer of ownership",
        "تمويل سكني بصيغة الإجارة المنتهية بالتمليك — استقلال نقل الملكية",
        ("Lease contract (Ijara) and ownership-transfer promise must be "
         "executed as legally independent instruments. Ownership transfer at "
         "end-of-lease executed via a separate gift contract (hiba) or "
         "nominal-price sale. Rental during occupancy reflects fair market "
         "rate; bank bears major maintenance and structural risk (ujrah min "
         "al-'ayn)."),
        ("عقد الإجارة ووعد التمليك يُنفَّذان كأداتين قانونيتين مستقلتين. نقل "
         "الملكية عند انتهاء الإجارة يكون بعقد هبة منفصل أو ببيع رمزي. الأجرة "
         "أثناء الإشغال تعكس قيمة السوق العادلة؛ ويتحمَّل البنك الصيانة الكبرى "
         "والمخاطر الإنشائية (الأجرة من العين)."),
        ("Sharia Standard 9 (AAOIFI) on Ijara and Ijara Muntahia Bittamleek. "
         "Prohibition of combining two contracts in one (nahy 'an bay'atayni "
         "fi bay'a) per the Sunnah. Ijma' on the lessor bearing structural "
         "risk per OIC Fiqh Academy Resolution 110."),
        ("المعيار الشرعي رقم ٩ (الأيوفي) في الإجارة والإجارة المنتهية بالتمليك. "
         "النهي عن بيعتين في بيعة في السنّة. وإجماع المعاصرين على تحمُّل المؤجِّر "
         "للمخاطر الإنشائية وفق قرار مجمع الفقه الإسلامي الدولي رقم ١١٠."),
        "ACTIVE",
        submitted_at=NOW - timedelta(days=150),
        ssb_review_at=NOW - timedelta(days=140),
        approved_at=NOW - timedelta(days=120),
        effective_date=TODAY - timedelta(days=115),
        signing_scholars=signing_4,
    )

    # 5) DRAFT Sukuk Mudaraba fatwa — awaiting SSB review.
    out["sukuk_draft"] = insert_resolution(
        ORG, "RES-2026-002", "SUKUK",
        "Sukuk Mudaraba — proposed structure for SAR-denominated 5-yr issuance",
        "صكوك المضاربة — الهيكل المقترَح لإصدار بالريال السعودي لمدة ٥ سنوات",
        ("DRAFT structure proposes: Mudarib (issuer) manages investment "
         "activity; sukuk-holders receive proportional share of actual "
         "profits — not fixed yield. Capital is *not* guaranteed by the "
         "Mudarib except in cases of negligence (ta'addi / taqsir) or "
         "breach of contract terms. Proposed underlying activity: Tawarruq + "
         "Murabaha pool from existing portfolio."),
        ("الهيكل المقترَح: المضارب (المُصدِر) يدير النشاط الاستثماري؛ ويحصل "
         "حملة الصكوك على نصيب متناسب من الأرباح الفعلية — لا عائد ثابت. "
         "رأس المال *غير* مضمون من المضارب إلا في حالات التعدِّي أو التقصير "
         "أو الإخلال بشروط العقد. النشاط المقترَح: محفظة تورّق ومرابحة من المحفظة القائمة."),
        ("Draft rationale references Sharia Standard 17 (AAOIFI) on "
         "Investment Sukuk. SSB review pending on: (a) acceptability of the "
         "proposed pooled-underlying, (b) language on capital guarantee "
         "edge cases, (c) profit-distribution waterfall vis-à-vis IFSB-7."),
        ("المسوّدة تستند إلى المعيار الشرعي رقم ١٧ (الأيوفي) في الصكوك "
         "الاستثمارية. مراجعة الهيئة معلَّقة بشأن: (أ) قبول المحفظة الأساسية "
         "المجمَّعة، (ب) صياغة ضمان رأس المال في الحالات الاستثنائية، "
         "(ج) شلال توزيع الأرباح وفق IFSB-7."),
        "DRAFT",
        submitted_at=None,
        signing_scholars=None,
    )

    return out


def seed_findings(resolutions):
    """3 internal-audit findings — one HIGH to prime AR-253."""
    print(">>> seeding audit findings (org=1)")
    out = {}

    # HIGH severity — primes AR-253 30-day Board Audit Committee SLA.
    out["high"] = insert_finding(
        ORG, "SAUD-2026-Q1-007",
        "HIGH", "OPEN",
        "Personal Vehicle Murabaha — margin allocation deviates from RES-2025-022 §3",
        "مرابحة المركبة الشخصية — توزيع هامش الربح يخالف الفقرة ٣ من القرار RES-2025-022",
        ("Sampling 50 Personal Vehicle Murabaha contracts originated post-"
         "RES-2025-022 effective date: 7 contracts (14%) used the legacy "
         "margin-allocation formula instead of the resolution's mandated "
         "front-loading-prohibited schedule. Approximate exposure: SAR 92,500 "
         "across the 7 contracts. Bank income recognised may include "
         "incorrectly accelerated profit."),
        ("بعينة من ٥٠ عقداً لمرابحة المركبات الشخصية مُنشأة بعد تاريخ نفاذ "
         "RES-2025-022: استخدمت ٧ عقود (١٤٪) الصيغة القديمة لتوزيع هامش الربح "
         "بدلاً من الجدول الذي يحظر التحميل المسبق. التعرُّض التقديري: ٩٢,٥٠٠ "
         "ريال موزَّعة على ٧ عقود. قد يحتوي الدخل المُعترَف به على ربح مُعجَّل "
         "بصورة غير صحيحة."),
        ("Operational gap: core-banking parameter for the Murabaha amortisation "
         "table was not re-pointed to the new template after RES-2025-022 "
         "approval; Front Office continued onboarding using the legacy product "
         "code 'MRB-AUTO-V2' (deprecated) for ~3 weeks post-effective-date."),
        ("ثغرة تشغيلية: لم يُحدَّث متغيِّر النظام المصرفي الأساسي لجدول استهلاك "
         "المرابحة ليشير إلى القالب الجديد بعد اعتماد RES-2025-022؛ واستمر "
         "الفرع في إدراج العملاء على رمز المنتج القديم 'MRB-AUTO-V2' (المتقاعد) "
         "لحوالي ٣ أسابيع بعد تاريخ النفاذ."),
        ("(1) Re-amortise the 7 affected contracts on the corrected schedule "
         "and reclassify the differential as SNCI subject to purification. "
         "(2) Decommission product code MRB-AUTO-V2 in core-banking. "
         "(3) Add a release-gate control: no new product code activation "
         "without confirmation that all linked SSB resolutions have been "
         "applied to amortisation tables. (4) Quarterly sample-based testing "
         "by Sharia Audit on margin-allocation parameters."),
        ("(١) إعادة احتساب الـ٧ عقود وفق الجدول الصحيح وتصنيف الفرق كدخل عدم "
         "التزام شرعي خاضع للتطهير. (٢) إيقاف رمز المنتج MRB-AUTO-V2 في النظام "
         "المصرفي الأساسي. (٣) إضافة ضابط بوّابة إصدار: لا تنشيط لأي رمز منتج "
         "جديد قبل التأكُّد من تطبيق جميع القرارات الشرعية المرتبطة على جداول "
         "الاستهلاك. (٤) اختبار عيِّنات ربع سنوي من قِبل التدقيق الشرعي على "
         "متغيِّرات توزيع هامش الربح."),
        linked_resolution_id=resolutions["murabaha"],
        product_line="Personal Vehicle Murabaha",
        audit_cycle_label="Q1-2026 Sharia Audit",
        raised_at=NOW - timedelta(days=4),                # primes 30-day SLA
        remediation_due_at=TODAY + timedelta(days=26),
    )

    # MEDIUM severity — OPEN.
    out["medium"] = insert_finding(
        ORG, "SAUD-2026-Q1-012",
        "MEDIUM", "OPEN",
        "Tawarruq broker-rotation log retention below SSB requirement",
        "سجل تبادل وسطاء التورّق دون متطلب الهيئة الشرعية في فترة الاحتفاظ",
        ("RES-2025-014 §4 requires retention of broker-rotation evidence "
         "(invoices + warehouse-receipt copies) for ≥ 7 years. Sampling of "
         "October 2025 Tawarruq Salary Advance batch: warehouse-receipt copies "
         "available only for 84% of transactions; remaining 16% logged in the "
         "core system without supporting documentation."),
        ("الفقرة ٤ من RES-2025-014 توجب الاحتفاظ بإثبات تبادل الوسطاء (فواتير + "
         "نسخ إيصالات المستودع) لمدة ≥ ٧ سنوات. بعينة من دفعة أكتوبر ٢٠٢٥ "
         "لتورّق السلفة على الراتب: نسخ إيصالات المستودع متوفرة لـ٨٤٪ فقط من "
         "المعاملات؛ والـ١٦٪ المتبقية مُسجَّلة في النظام دون وثائق داعمة."),
        ("Documentation handover from Tawarruq Operations to Records Custody "
         "is manual and runs on a weekly batch — files dropped during the "
         "first three weekly batches due to operator change in Records "
         "Custody team. No automated reconciliation exists."),
        ("تسليم الوثائق من عمليات التورّق إلى إدارة حفظ السجلات يدوي ويعمل "
         "بدفعة أسبوعية — سقطت ملفات في أوّل ثلاث دفعات أسبوعية بسبب تغيُّر "
         "المشغِّل في فريق حفظ السجلات. لا توجد مطابقة آلية."),
        ("Automated daily reconciliation between Tawarruq Operations system "
         "and Records Custody index. Backfill missing 16% by retrieving "
         "duplicates from the warehouse counterparty. Add quarterly Sharia-"
         "audit verification step."),
        ("مطابقة آلية يومية بين نظام عمليات التورّق وفهرس حفظ السجلات. "
         "تعويض الـ١٦٪ المفقودة بطلب نسخ مكرَّرة من المستودع. إضافة خطوة "
         "تحقق ربع سنوية من التدقيق الشرعي."),
        linked_resolution_id=resolutions["tawarruq"],
        product_line="Tawarruq Salary Advance",
        audit_cycle_label="Q1-2026 Sharia Audit",
        raised_at=NOW - timedelta(days=22),
        remediation_due_at=TODAY + timedelta(days=38),
    )

    # LOW severity — REMEDIATION.
    out["low"] = insert_finding(
        ORG, "SAUD-2025-Q4-031",
        "LOW", "REMEDIATION",
        "Bilingual disclosure pamphlet for Home Ijara missing footnote on takaful",
        "النشرة التعريفية للإجارة السكنية تنقصها حاشية التكافل",
        ("Customer-facing disclosure pamphlet for Home Finance Ijara does not "
         "include the footnote clarifying that takaful insurance fees during "
         "the lease are borne by the bank (lessor), not deducted from rental. "
         "RES-2025-031 §6 makes this disclosure mandatory."),
        ("النشرة التعريفية لتمويل الإجارة السكنية لا تتضمَّن الحاشية التي توضح "
         "أن أقساط التكافل خلال فترة الإجارة يتحمَّلها البنك (المؤجِّر) ولا "
         "تُخصم من الأجرة. الفقرة ٦ من RES-2025-031 توجب هذا الإفصاح."),
        ("Marketing pamphlet master-file last revised before RES-2025-031 was "
         "approved; the disclosure update was queued but not promoted in time "
         "for the Q4 print run."),
        ("الملف الرئيسي للنشرة التسويقية رُوجع آخر مرة قبل اعتماد RES-2025-031؛ "
         "وكان تحديث الإفصاح في طابور المهام لكنّه لم يُنفَّذ في الوقت المناسب "
         "للطباعة الربعية."),
        ("Update master pamphlet, reprint, distribute to all 47 branches. "
         "Add a check in the marketing-release SOP: any pamphlet referencing "
         "an Islamic product must be re-confirmed against the latest active "
         "SSB resolution before each print run."),
        ("تحديث النشرة الرئيسية، وإعادة الطباعة، والتوزيع على الـ٤٧ فرعاً. "
         "إضافة ضابط في إجراء إصدار المواد التسويقية: أي نشرة تشير إلى منتج "
         "إسلامي يجب إعادة تطابقها مع أحدث قرار شرعي نافذ قبل كل طباعة."),
        linked_resolution_id=resolutions["ijara"],
        product_line="Home Finance Ijara",
        audit_cycle_label="Q4-2025 Sharia Audit",
        raised_at=NOW - timedelta(days=68),
        remediation_due_at=TODAY + timedelta(days=12),
    )

    return out


def seed_nc_events(resolutions):
    """2 SNCI events — small INVESTIGATION + large material (>500k SAR) DETECTED."""
    print(">>> seeding non-compliance events (org=1)")
    out = {}

    # 18,500 SAR — INVESTIGATION — primes AR-254 90-day SLA.
    out["small"] = insert_nc(
        ORG, "SNCI-2026-003",
        "INVESTIGATION", "MEDIUM",
        "Late-payment penalty mis-routed to bank income on Personal Murabaha (Mar-2026)",
        "غرامة التأخُّر مُوجَّهة خطأً لدخل البنك في المرابحة الشخصية (مارس-٢٠٢٦)",
        ("Routine reconciliation by Finance flagged that 22 late-payment "
         "penalty postings totalling SAR 18,500 between 1-Mar-2026 and "
         "14-Mar-2026 were posted to GL account 5610 (Bank Fee Income) "
         "instead of GL 5910 (Charity Payable). Root cause: a posting-rule "
         "change in late Feb deployed without re-validating the Sharia "
         "purification routing. Income reclassification + transfer to charity "
         "required per RES-2025-022 §7 and SAMA Sharia Governance §6.4."),
        ("اكتشفت المالية في مطابقة دورية أن ٢٢ قيد غرامة تأخير بمبلغ إجمالي "
         "١٨,٥٠٠ ريال بين ١ و١٤ مارس ٢٠٢٦ قُيِّدت في حساب الأستاذ ٥٦١٠ "
         "(دخل رسوم البنك) بدلاً من ٥٩١٠ (مستحقات خيرية). السبب الجذري: تعديل "
         "قاعدة ترحيل في أواخر فبراير نُشر دون إعادة اختبار مسار التطهير "
         "الشرعي. مطلوب إعادة تصنيف الدخل وتحويله للخير وفق الفقرة ٧ من "
         "RES-2025-022 وحوكمة الهيئة الشرعية ساما §٦.٤."),
        product_line="Personal Vehicle Murabaha",
        linked_resolution_id=resolutions["murabaha"],
        detected_at=NOW - timedelta(days=8),
        detection_source="OPERATIONS",
        snci_amount=18500,
        affected_customer_count=22,
    )

    # 750,000 SAR — DETECTED — primes AR-255 material trigger (>500k SAR).
    out["material"] = insert_nc(
        ORG, "SNCI-2026-004",
        "DETECTED", "HIGH",
        "Tawarruq broker spread anomaly — SAR 750,000 over Q1 2026",
        "شذوذ في هامش وسيط التورّق — ٧٥٠,٠٠٠ ريال خلال الربع الأول ٢٠٢٦",
        ("Sharia Audit's Q1-2026 sample testing on the Tawarruq Salary Advance "
         "product identified that one of the two rotating commodity brokers "
         "(Broker-B) charged an undisclosed spread of approximately 0.8% on "
         "each commodity sale-back transaction across ~2,400 contracts in "
         "January–March 2026. Aggregate exposure: SAR 750,000 of bank income "
         "originating from an undisclosed spread arrangement that breaches "
         "RES-2025-014 §4/5 (broker-independence + full disclosure). Material "
         "SNCI — Board Risk Committee Chair notification required (SAMA "
         "Sharia Governance §6.5) and external Sharia auditor engagement "
         "expected."),
        ("اكتشف التدقيق الشرعي في اختبار العيِّنات للربع الأول من ٢٠٢٦ على "
         "منتج تورّق السلفة على الراتب أن أحد الوسيطين الدوّارين في السلع "
         "(الوسيط-ب) فرض هامشاً غير مُفصَح عنه بحوالي ٠.٨٪ على كل معاملة "
         "إعادة بيع للسلعة عبر ~٢,٤٠٠ عقد في يناير-مارس ٢٠٢٦. التعرُّض "
         "الإجمالي: ٧٥٠,٠٠٠ ريال من دخل البنك ناشئ عن ترتيب هامش غير "
         "مُفصَح عنه يخالف الفقرة ٤/٥ من RES-2025-014 (استقلال الوسيط + "
         "الإفصاح الكامل). دخل عدم التزام جوهري — مطلوب إخطار رئيس لجنة "
         "المخاطر بالمجلس (حوكمة الهيئة الشرعية ساما §٦.٥) ومن المتوقَّع "
         "إشراك المدقِّق الشرعي الخارجي."),
        product_line="Tawarruq Salary Advance",
        linked_resolution_id=resolutions["tawarruq"],
        detected_at=NOW - timedelta(days=2),
        detection_source="AUDIT",
        snci_amount=750000,
        affected_customer_count=2400,
    )

    return out


def seed_products(resolutions):
    """5 Islamic-banking products across the pipeline."""
    print(">>> seeding product approvals (org=1)")
    out = {}

    # LAUNCHED — Personal Vehicle Murabaha.
    out["vehicle_murabaha"] = insert_product(
        ORG, "PROD-2025-014",
        "Personal Vehicle Murabaha",
        "مرابحة المركبة الشخصية",
        "MURABAHA", "LAUNCHED",
        ("Saudi-resident customer-facing finance for new and certified-used "
         "vehicles up to SAR 350,000 ticket size. Tenor 12–60 months. Bank "
         "purchases the vehicle from authorised dealer, takes title, re-sells "
         "to customer at agreed cost-plus-margin. Late-payment penalty (if "
         "any) booked to charity payable, not bank income."),
        ("تمويل للعملاء المقيمين في السعودية لشراء المركبات الجديدة والمستعملة "
         "المعتمدة بسقف ٣٥٠,٠٠٠ ريال. مدة التمويل ١٢-٦٠ شهراً. يشتري البنك "
         "المركبة من الوكيل المعتمد ويسجِّلها باسمه ثم يبيعها للعميل بالتكلفة "
         "مضافاً إليها هامش ربح متَّفق عليه. غرامة التأخير (إن وُجدت) تُقيَّد "
         "في مستحقات خيرية وليست دخلاً للبنك."),
        linked_resolution_id=resolutions["murabaha"],
        submitted_at=NOW - timedelta(days=220),
        ssb_review_at=NOW - timedelta(days=210),
        approved_provisional_at=NOW - timedelta(days=180),
        approved_final_at=NOW - timedelta(days=170),
        launched_at=TODAY - timedelta(days=160),
    )

    # APPROVED_FINAL — Home Ijara, awaiting launch.
    out["home_ijara"] = insert_product(
        ORG, "PROD-2025-022",
        "Home Finance — Ijara Muntahia Bittamleek",
        "تمويل سكني — إجارة منتهية بالتمليك",
        "IJARA", "APPROVED_FINAL",
        ("Saudi-resident retail home finance under Ijara Muntahia Bittamleek. "
         "Ticket SAR 500,000–4,000,000. Tenor 60–300 months. Bank purchases "
         "and owns the property; customer pays rent during lease term; "
         "ownership transferred via separate gift contract at end of "
         "lease. Major maintenance + structural risk on bank."),
        ("تمويل سكني للعملاء المقيمين بصيغة الإجارة المنتهية بالتمليك. سقف "
         "التمويل ٥٠٠,٠٠٠-٤,٠٠٠,٠٠٠ ريال. مدة الإجارة ٦٠-٣٠٠ شهراً. يشتري "
         "البنك العقار ويملكه؛ ويسدِّد العميل أجرة خلال فترة الإجارة؛ وتُنقل "
         "الملكية بعقد هبة منفصل في نهاية الإجارة. الصيانة الكبرى والمخاطر "
         "الإنشائية على البنك."),
        linked_resolution_id=resolutions["ijara"],
        submitted_at=NOW - timedelta(days=160),
        ssb_review_at=NOW - timedelta(days=150),
        approved_provisional_at=NOW - timedelta(days=125),
        approved_final_at=NOW - timedelta(days=110),
    )

    # APPROVED_PROVISIONAL — Tawarruq Salary Advance pilot.
    out["tawarruq_advance"] = insert_product(
        ORG, "PROD-2026-003",
        "Commodity Tawarruq — Salary Advance",
        "التورّق المنظَّم — السلفة على الراتب",
        "TAWARRUQ", "APPROVED_PROVISIONAL",
        ("Salaried-customer short-term liquidity facility via commodity "
         "Tawarruq. Ticket SAR 5,000–80,000. Tenor 3–24 months. Two-broker "
         "rotation per RES-2025-014. Pilot deployment in 12 branches before "
         "promotion to APPROVED_FINAL after 90-day review."),
        ("تسهيل سيولة قصير الأجل للعملاء ذوي الراتب عبر التورّق المنظَّم "
         "بالسلع. سقف التمويل ٥,٠٠٠-٨٠,٠٠٠ ريال. مدة السداد ٣-٢٤ شهراً. "
         "تبادل وسيطين وفق RES-2025-014. تجريب أوّلي في ١٢ فرعاً قبل الترقية "
         "إلى الاعتماد النهائي بعد المراجعة بعد ٩٠ يوماً."),
        linked_resolution_id=resolutions["tawarruq"],
        submitted_at=NOW - timedelta(days=110),
        ssb_review_at=NOW - timedelta(days=100),
        approved_provisional_at=NOW - timedelta(days=75),
    )

    # SSB_REVIEW — Sukuk Mudaraba.
    out["sukuk_mudaraba"] = insert_product(
        ORG, "PROD-2026-009",
        "Sukuk Mudaraba — 5-year SAR-denominated issuance",
        "صكوك المضاربة — إصدار بالريال السعودي لمدة ٥ سنوات",
        "SUKUK", "SSB_REVIEW",
        ("Proposed 5-year SAR-denominated Sukuk Mudaraba issuance, target "
         "size SAR 1.5 billion. Sukuk-holders' funds aggregated into a "
         "Tawarruq + Murabaha financing pool managed by the bank as Mudarib. "
         "Pending SSB review of fatwa RES-2026-002 and capital-protection "
         "language."),
        ("إصدار مقترَح من صكوك المضاربة بالريال السعودي لمدة ٥ سنوات بحجم "
         "مستهدَف ١.٥ مليار ريال. تُجمَّع أموال حملة الصكوك في محفظة تمويل "
         "تورّق ومرابحة يديرها البنك بوصفه مضارباً. بانتظار مراجعة الهيئة "
         "للقرار RES-2026-002 وصياغة حماية رأس المال."),
        linked_resolution_id=resolutions["sukuk_draft"],
        submitted_at=NOW - timedelta(days=18),
        ssb_review_at=NOW - timedelta(days=12),
    )

    # REJECTED — interest-tracking deposit dressed up as Mudaraba.
    out["mudaraba_reject"] = insert_product(
        ORG, "PROD-2026-005",
        "Floating-Rate Term Deposit (re-positioned as Mudaraba)",
        "وديعة لأجَل بعائد متغيِّر (مُعاد تصميمها كـ«مضاربة»)",
        "MUDARABA", "REJECTED",
        ("Proposed term-deposit product offered a 'guaranteed' floating rate "
         "linked to SAIBOR + spread, badged as Mudaraba 'expected profit'. "
         "SSB review identified that the structure preserved guarantee of "
         "capital + de-facto guaranteed return tracking a conventional "
         "benchmark — fundamentally incompatible with Mudaraba's profit-loss "
         "sharing principle. Rejected on first review; product team asked "
         "to re-engineer as a clear Wakala or to re-classify outside the "
         "Islamic window."),
        ("منتج وديعة لأجَل مُقترَح بعائد «مضمون» متغيِّر مرتبط بسايبور مضافاً "
         "إليه هامش، تحت مسمَّى «ربح متوقَّع» في المضاربة. كشفت مراجعة "
         "الهيئة أن الهيكل يُبقي ضمان رأس المال + عائد مضمون فعلياً يتتبَّع "
         "مرجعاً تقليدياً — مخالفاً جوهرياً لمبدأ المشاركة في الربح والخسارة "
         "في المضاربة. مرفوض من أوّل مراجعة؛ طُلب من فريق المنتج إعادة "
         "تصميمه كوكالة واضحة أو نقله خارج النافذة الإسلامية."),
        submitted_at=NOW - timedelta(days=45),
        ssb_review_at=NOW - timedelta(days=40),
        rejected_at=NOW - timedelta(days=30),
        rejection_reason_en=("Structure preserves capital guarantee and "
                              "guarantees a benchmark-tracked return — "
                              "fundamentally a conventional deposit re-skinned "
                              "as Mudaraba. Incompatible with profit-loss "
                              "sharing under Sharia Standard 13 (Mudaraba) "
                              "§4/2."),
        rejection_reason_ar=("الهيكل يُبقي ضمان رأس المال ويضمن عائداً يتتبَّع "
                              "مرجعاً — وهو في جوهره وديعة تقليدية أُعيدت "
                              "صياغتها كـ«مضاربة». مخالف لمبدأ المشاركة في الربح "
                              "والخسارة وفق المعيار الشرعي رقم ١٣ (المضاربة) "
                              "الفقرة ٤/٢."),
    )

    return out


def seed_events(scholars, resolutions, findings, ncs, products):
    """Chronological timeline events.

    Light-touch — one or two events per key state transition. The frontend
    will accumulate further events through normal use.
    """
    print(">>> seeding timeline events (org=1)")

    chair_name = "Dr. Abdulaziz Al-Qassim"
    secretary_name = "Hisham Al-Otaibi"

    # COI declarations (one event per scholar with COI declared).
    for ref, sid in [
        ("chairman", scholars["chairman"]),
        ("member1",  scholars["member1"]),
        ("member2",  scholars["member2"]),
        ("member3",  scholars["member3"]),
        ("secretary", scholars["secretary"]),
    ]:
        insert_event(
            ORG, sid, None, None, None, None,
            NOW - timedelta(days=90),
            "COI_DECLARED", secretary_name, None,
            "Annual conflict-of-interest declaration filed.",
            "إيداع إقرار تضارب المصالح السنوي.",
        )

    # Resolution lifecycle (current ACTIVE Tawarruq).
    insert_event(
        ORG, None, resolutions["tawarruq"], None, None, None,
        NOW - timedelta(days=120),
        "RESOLUTION_SUBMITTED", secretary_name, "DRAFT",
        "Tawarruq specification revision submitted to SSB.",
        "إيداع المواصفة المُنقَّحة للتورّق لدى الهيئة الشرعية.",
    )
    insert_event(
        ORG, None, resolutions["tawarruq"], None, None, None,
        NOW - timedelta(days=110),
        "SSB_REVIEW_STARTED", chair_name, "SSB_REVIEW",
        "SSB review opened. Three-member quorum confirmed.",
        "فُتحت مراجعة الهيئة الشرعية. تأكَّد اكتمال نصاب الأعضاء الثلاثة.",
    )
    insert_event(
        ORG, None, resolutions["tawarruq"], None, None, None,
        NOW - timedelta(days=85),
        "APPROVED", chair_name, "APPROVED",
        "Resolution approved unanimously after two SSB sessions.",
        "اعتماد القرار بالإجماع بعد جلستين للهيئة.",
    )
    insert_event(
        ORG, None, resolutions["tawarruq"], None, None, None,
        NOW - timedelta(days=80),
        "ACTIVATED", chair_name, "ACTIVE",
        "Resolution effective. Predecessor RES-2024-007 superseded.",
        "نفاذ القرار. ونسخ القرار السابق RES-2024-007.",
    )

    # HIGH audit finding raised.
    insert_event(
        ORG, None, None, findings["high"], None, None,
        NOW - timedelta(days=4),
        "FINDING_RAISED", "Sharia Audit Team", "OPEN",
        ("HIGH-severity finding raised — Personal Vehicle Murabaha margin "
         "allocation deviation. Board Audit Committee notified."),
        ("رفع ملاحظة عالية الخطورة — انحراف توزيع هامش الربح في مرابحة "
         "المركبات الشخصية. تم إخطار لجنة التدقيق بالمجلس."),
    )

    # Material SNCI detected (>500k SAR — primes AR-255).
    insert_event(
        ORG, None, None, None, ncs["material"], None,
        NOW - timedelta(days=2),
        "NC_DETECTED", "Sharia Audit Team", "DETECTED",
        ("Material SNCI detected — SAR 750,000 Tawarruq broker spread "
         "anomaly. Board Risk Chair + external Sharia auditor engagement "
         "triggered per SAMA Sharia Governance §6.5."),
        ("رصد دخل عدم التزام شرعي جوهري — شذوذ بهامش وسيط التورّق بقيمة "
         "٧٥٠,٠٠٠ ريال. أُطلق إخطار رئيس لجنة المخاطر بالمجلس وإشراك "
         "المدقِّق الشرعي الخارجي وفق حوكمة الهيئة الشرعية ساما §٦.٥."),
    )

    # Smaller SNCI under investigation.
    insert_event(
        ORG, None, None, None, ncs["small"], None,
        NOW - timedelta(days=8),
        "NC_DETECTED", "Finance Reconciliation Team", "DETECTED",
        ("SNCI flagged: SAR 18,500 late-payment penalty miscategorised to "
         "bank income across 22 Personal Murabaha contracts."),
        ("رصد دخل عدم التزام شرعي: ١٨,٥٠٠ ريال غرامات تأخير قُيِّدت بالخطأ "
         "كدخل بنكي عبر ٢٢ عقد مرابحة شخصية."),
    )
    insert_event(
        ORG, None, None, None, ncs["small"], None,
        NOW - timedelta(days=7),
        "INVESTIGATION_STARTED", "Sharia Officer", "INVESTIGATION",
        "Investigation opened. Income reclassification under preparation.",
        "فُتح التحقيق. وإعادة تصنيف الدخل قيد الإعداد.",
    )

    # Product launched.
    insert_event(
        ORG, None, None, None, None, products["vehicle_murabaha"],
        NOW - timedelta(days=160),
        "LAUNCHED", "Product Owner — Retail Banking", "LAUNCHED",
        ("Personal Vehicle Murabaha launched in all 47 branches and digital "
         "channels."),
        ("إطلاق مرابحة المركبات الشخصية في جميع الـ٤٧ فرعاً والقنوات الرقمية."),
    )

    # Product rejected.
    insert_event(
        ORG, None, None, None, None, products["mudaraba_reject"],
        NOW - timedelta(days=30),
        "REJECTED", chair_name, "REJECTED",
        ("Rejected at first SSB review — guaranteed return + capital "
         "guarantee inconsistent with Mudaraba. Product team asked to "
         "re-engineer or re-classify."),
        ("رُفض من أوّل مراجعة للهيئة — العائد المضمون وضمان رأس المال غير "
         "متوافقَين مع المضاربة. طُلب من الفريق إعادة التصميم أو إعادة التصنيف."),
    )


# ---------------------------------------------------------------- driver ----

def main():
    print(">>> Sprint 20 PR #4 — Saudi-realistic Sharia governance seed")
    print(">>> Tenant: TSS GRC (org_id=1) Islamic-window bank")
    print()
    try:
        scholars    = seed_scholars()
        resolutions = seed_resolutions(scholars)
        findings    = seed_findings(resolutions)
        ncs         = seed_nc_events(resolutions)
        products    = seed_products(resolutions)
        seed_events(scholars, resolutions, findings, ncs, products)
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"!!! ROLLED BACK: {e}")
        raise

    # Counts.
    cur.execute("SELECT COUNT(*) FROM sharia.sharia_scholar         WHERE is_deleted = 0")
    n_scholar = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sharia.sharia_resolution      WHERE is_deleted = 0")
    n_res = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sharia.sharia_audit_finding   WHERE is_deleted = 0")
    n_find = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sharia.sharia_nc_event        WHERE is_deleted = 0")
    n_nc = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sharia.sharia_product_approval WHERE is_deleted = 0")
    n_prod = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sharia.sharia_event           WHERE is_deleted = 0")
    n_evt = cur.fetchone()[0]
    print()
    print(f"+ scholars        : {n_scholar}")
    print(f"+ resolutions     : {n_res}")
    print(f"+ audit findings  : {n_find}")
    print(f"+ NC events       : {n_nc}")
    print(f"+ products        : {n_prod}")
    print(f"+ timeline events : {n_evt}")
    print()
    print(">>> done.")


if __name__ == "__main__":
    main()
