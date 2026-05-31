"""Sprint 33 — Saudi Data Officer Toolkit seed (realistic banking data).

Idempotent: every INSERT is gated by an existence check on `reference` so
re-running on a fresh DB or after the bootstrap is safe.

Targets the 3 tenants from seed_real_data.py:
  org_id=1  TSS GRC  (Saudi-bank reference tenant — most rows here)
  org_id=2  Aramco Trading
  org_id=3  STC Bank
"""
from __future__ import annotations
import json
import pyodbc
from datetime import datetime, timedelta, date

CONN_STR = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost,1433;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;"
    "Encrypt=yes;TrustServerCertificate=yes;sendStringParametersAsUnicode=yes;"
)

ORG = 1  # TSS GRC (Saudi-bank reference tenant)
NOW = datetime.now()
TODAY = NOW.date()


def insert_if_missing(cur, table: str, ref_col: str, ref: str, columns: list[str], values: list) -> bool:
    """Return True if the row was inserted (didn't already exist)."""
    cur.execute(f"SELECT 1 FROM data_officer.{table} WHERE {ref_col} = ? AND organisation_id = ?", ref, ORG)
    if cur.fetchone():
        return False
    placeholders = ", ".join(["?"] * len(columns))
    cur.execute(
        f"INSERT INTO data_officer.{table} ({', '.join(columns)}) VALUES ({placeholders})",
        *values,
    )
    return True


def seed_dsr(cur):
    """8 customer DSRs covering all 6 request types + various SLA states."""
    rows = [
        # (reference, request_type, channel, name_en, name_ar, email, summary_en, summary_ar, days_ago, status, sla_state)
        ("DSR-2026-001", "ACCESS",      "NAFATH",      "Khalid Al-Otaibi",      "خالد العتيبي",    "k.alotaibi@example.com",
         "Customer requesting copy of all KYC documents + transaction history for 2024-2025.",
         "العميل يطلب نسخة من جميع وثائق اعرف عميلك وسجلّ المعاملات للفترة 2024-2025.",
         12, "IN_PROGRESS", "in_window"),
        ("DSR-2026-002", "ERASE",       "MOBILE",      "Reem Al-Zahrani",       "ريم الزهراني",    "r.alzahrani@example.com",
         "Customer closed account; requesting erasure of marketing profile + ad-personalisation data.",
         "أغلق العميل الحساب؛ يطلب محو ملف التسويق وبيانات الإعلانات الموجّهة.",
         5, "ID_VERIFIED", "in_window"),
        ("DSR-2026-003", "RECTIFY",     "BRANCH",      "Faisal Al-Ghamdi",      "فيصل الغامدي",    "f.alghamdi@example.com",
         "Date of birth wrong on KYC record; requesting correction with attached national-ID copy.",
         "تاريخ الميلاد خاطئ في سجل اعرف عميلك؛ يطلب التصحيح مع نسخة الهوية الوطنية.",
         18, "FULFILLED",  "completed"),
        ("DSR-2026-004", "PORTABILITY", "WEB",         "Noura Al-Qahtani",      "نورا القحطاني",   "n.alqahtani@example.com",
         "Switching to another bank; requesting export of standing-orders + beneficiary list in machine-readable JSON.",
         "تنتقل إلى بنك آخر؛ تطلب تصدير الأوامر المستديمة وقائمة المستفيدين بصيغة JSON قابلة للقراءة الآلية.",
         2, "RECEIVED",   "in_window"),
        ("DSR-2026-005", "OBJECT",      "EMAIL",       "Yousef Al-Harbi",       "يوسف الحربي",     "y.alharbi@example.com",
         "Objecting to profiling for credit-card upsell offers; requesting opt-out from automated decision-making.",
         "يعترض على التنميط بهدف عروض ترقية البطاقات الائتمانية؛ يطلب الانسحاب من القرارات الآلية.",
         32, "ESCALATED_TO_SDAIA", "overdue"),
        ("DSR-2026-006", "RESTRICT",    "CALL",        "Sara Al-Mutairi",       "سارة المطيري",    "s.almutairi@example.com",
         "Dispute over loan-default classification; restrict further processing until resolved.",
         "نزاع حول تصنيف التعثّر في قرض؛ تطلب تقييد المعالجة لحين الفصل.",
         9, "IN_PROGRESS", "in_window"),
        ("DSR-2026-007", "ACCESS",      "SDAIA_PORTAL","Abdullah Al-Shahri",    "عبدالله الشهري",  "a.alshahri@example.com",
         "Subject Access Request received via SDAIA citizen portal — escalated by regulator.",
         "طلب اطّلاع وارد عبر بوابة سدايا للمواطنين — مُحال من الجهة التنظيمية.",
         1, "RECEIVED",   "in_window"),
        ("DSR-2026-008", "ERASE",       "WEB",         "Mona Al-Ahmadi",        "منى الأحمدي",     "m.alahmadi@example.com",
         "Requesting erasure — citing right to be forgotten under PDPL Art.13.",
         "تطلب المحو — مستندةً إلى حقّ النسيان في المادة ١٣ من نظام حماية البيانات الشخصية.",
         22, "REJECTED",   "in_window"),
    ]
    n = 0
    for ref, rtype, ch, name_en, name_ar, email, summary_en, summary_ar, days_ago, status, _ in rows:
        received = NOW - timedelta(days=days_ago)
        sla = received + timedelta(days=30)
        fulfilled = received + timedelta(days=14) if status == "FULFILLED" else None
        id_verified = received + timedelta(days=1) if status not in ("RECEIVED",) else None
        cols = ["organisation_id", "reference", "request_type", "channel",
                "requester_name_en", "requester_name_ar", "requester_email",
                "request_summary_en", "request_summary_ar",
                "status", "received_at", "sla_deadline", "id_verified_at", "fulfilled_at",
                "id_verification_method"]
        vals = [ORG, ref, rtype, ch, name_en, name_ar, email,
                summary_en, summary_ar,
                status, received, sla, id_verified, fulfilled,
                "NAFATH" if ch == "NAFATH" else ("IN_BRANCH_KYC" if ch == "BRANCH" else None)]
        if insert_if_missing(cur, "dsr_request", "reference", ref, cols, vals):
            n += 1
    return n


def seed_consent(cur):
    """12 consent records — marketing, profiling, third-party sharing, plus some withdrawn."""
    purposes = [
        ("MARKETING",            "Marketing communications + product offers",  "إشعارات تسويقية وعروض منتجات"),
        ("PROFILING",            "Credit-decision profiling",                  "تنميط القرار الائتماني"),
        ("THIRD_PARTY_SHARING",  "Sharing with insurance partner",             "المشاركة مع شريك التأمين"),
        ("FRAUD_DETECTION",      "Behavioural analytics for fraud detection",  "تحليلات سلوكية لكشف الاحتيال"),
        ("CUSTOMER_SERVICE",     "Call recording for QA",                      "تسجيل المكالمات لضمان الجودة"),
    ]
    rows = []
    for i in range(12):
        purpose = purposes[i % len(purposes)]
        # 3 are withdrawn, others granted
        withdrawn = i in (2, 5, 9)
        rows.append((
            f"CON-2026-{i+1:04d}",
            f"CUST-{1000+i:06d}",
            purpose[0], purpose[1], purpose[2],
            i % 3 == 0,  # special category for credit profiling
            withdrawn,
        ))
    n = 0
    for ref, subject, code, label_en, label_ar, special, withdrawn in rows:
        granted = NOW - timedelta(days=30 + (hash(ref) % 90))
        withdrawn_at = granted + timedelta(days=15) if withdrawn else None
        valid_until = granted + timedelta(days=365)
        channel = ["WEB", "MOBILE", "BRANCH", "IVR"][hash(ref) % 4]
        cols = ["organisation_id", "reference", "data_subject_id", "purpose_code",
                "purpose_label_en", "purpose_label_ar", "channel",
                "granted_at", "granted_evidence", "valid_until",
                "withdrawn_at", "withdrawn_reason",
                "status", "legal_basis", "is_special_category"]
        vals = [ORG, ref, subject, code, label_en, label_ar, channel,
                granted, f"channel={channel}; IP=10.{(hash(ref)>>4)%256}.{hash(ref)%256}.1; UA=mobile-app/4.2", valid_until,
                withdrawn_at, ("Customer opted out of marketing channel" if withdrawn else None),
                ("WITHDRAWN" if withdrawn else "GRANTED"),
                "CONSENT" if code in ("MARKETING","THIRD_PARTY_SHARING") else "LEGITIMATE_INTEREST",
                1 if special else 0]
        if insert_if_missing(cur, "consent_record", "reference", ref, cols, vals):
            n += 1
    return n


def seed_dpia(cur):
    """5 DPIAs across triggers — covers high-risk processing in a Saudi bank."""
    rows = [
        ("DPIA-2026-001", "AI Credit Scoring Model v2.1 — high-risk automated decisioning",
         "نموذج تقييم الائتمان الذكي إصدار 2.1 — قرار آلي عالي المخاطر",
         "Replaces FICO-based scoring with internal XGBoost model using transaction + behavioural features. "
         "Processes ~2.1M customers monthly; decisions impact credit-card limit + personal-loan approval.",
         "يحلّ محل تقييم FICO بنموذج XGBoost داخلي يعتمد على بيانات المعاملات والسلوك. "
         "يعالج نحو 2.1 مليون عميل شهرياً؛ تؤثّر القرارات في حدود البطاقات الائتمانية وقبول القروض الشخصية.",
         "AUTOMATED_DECISION", True, "APPROVED_WITH_CONDITIONS",
         "DPO opinion: Model risk + fairness review required quarterly. Bias score must remain <5%. Approved with conditions.",
         "رأي مسؤول حماية البيانات: مراجعة مخاطر النموذج والإنصاف ربعياً. درجة التحيّز يجب أن تظل أقل من 5%. مُعتمد بشروط.",
         "Quarterly fairness re-test by Model Validator. Annual SDAIA AI Ethics attestation (AR-280).",
         "إعادة اختبار الإنصاف ربعياً من قِبل مدقّق النموذج. إقرار سنوي بأخلاقيات الذكاء الاصطناعي من سدايا (AR-280).",
         60),
        ("DPIA-2026-002", "Open Banking — TPP data sharing (SAMA OBF v2)",
         "المصرفية المفتوحة — مشاركة البيانات مع مزوّدي الخدمات (OBF v2)",
         "Sharing account information with AISPs + payment initiation by PISPs via SAMA Open Banking Framework v2.",
         "مشاركة معلومات الحساب مع مزوّدي خدمات معلومات الحساب وبدء المدفوعات عبر إطار المصرفية المفتوحة v2 من ساما.",
         "NEW_PROCESSING", True, "APPROVED",
         "Strong customer authentication + scoped consent per TPP. Granular revocation supported.",
         "مصادقة قويّة للعميل وموافقة مُحدَّدة لكل مزوّد. يدعم السحب المفصّل للموافقة.",
         None, None, 45),
        ("DPIA-2026-003", "Mobile-app behavioural biometrics for fraud",
         "البيومتريا السلوكية في تطبيق الجوال لمكافحة الاحتيال",
         "Continuous-authentication via typing rhythm + touch dynamics; data retained 30 days for model training.",
         "مصادقة مستمرّة عبر نمط الطباعة وديناميكيات اللمس؛ يُحتفظ بالبيانات 30 يوماً لتدريب النموذج.",
         "NEW_TECHNOLOGY", True, "DPO_REVIEW",
         None, None, None, None, 10),
        ("DPIA-2026-004", "Branch CCTV + facial-image retention extension",
         "كاميرات الفروع + تمديد الاحتفاظ بصور الوجه",
         "Extending CCTV retention from 30 to 90 days. Faces are special-category data (PDPL Art.4).",
         "تمديد فترة الاحتفاظ بكاميرات الفروع من 30 إلى 90 يوماً. صور الوجه تُعدّ بيانات حسّاسة (المادة 4).",
         "SCOPE_CHANGE", True, "BUSINESS_REVIEW",
         "DPO recommends compensating controls: regional vault encryption + restricted-role access only.",
         "يوصي مسؤول حماية البيانات بضوابط تعويضية: تشفير الخزائن الإقليمية وقصر الوصول على أدوار محدّدة.",
         None, None, 7),
        ("DPIA-2026-005", "Marketing data-clean-room with telco partner",
         "غرفة بيانات تسويقية مع شريك اتصالات",
         "Anonymised-cohort sharing with STC for joint campaign targeting; differential-privacy applied.",
         "مشاركة بيانات مجموعات مجهولة الهوية مع STC لاستهداف حملة مشتركة؛ يُطبَّق تشويش الخصوصية التفاضلية.",
         "LARGE_SCALE", False, "REJECTED",
         "Telco re-identification risk above tolerance. Rejected; revisit with k-anonymity ≥ 100 + 90-day audit.",
         "خطر إعادة التعرّف لدى الشريك أعلى من المسموح. مرفوض؛ يُعاد النظر بإخفاء بحدّ k=100 ومراجعة دورية كلّ 90 يوماً.",
         None, None, 28),
    ]
    n = 0
    for ref, title_en, title_ar, desc_en, desc_ar, trigger, high_risk, status, dpo_en, dpo_ar, cond_en, cond_ar, days_ago in rows:
        cols = ["organisation_id", "reference", "title_en", "title_ar",
                "processing_description_en", "processing_description_ar",
                "trigger_reason", "is_high_risk", "status",
                "submitted_at", "dpo_opinion_en", "dpo_opinion_ar", "dpo_opinion_at",
                "decided_at", "conditions_en", "conditions_ar", "next_review_due"]
        submitted = NOW - timedelta(days=days_ago)
        decided = submitted + timedelta(days=days_ago // 2) if status in ("APPROVED","APPROVED_WITH_CONDITIONS","REJECTED") else None
        next_review = TODAY + timedelta(days=90) if status in ("APPROVED","APPROVED_WITH_CONDITIONS") else None
        vals = [ORG, ref, title_en, title_ar, desc_en, desc_ar,
                trigger, 1 if high_risk else 0, status,
                submitted, dpo_en, dpo_ar, submitted + timedelta(days=2) if dpo_en else None,
                decided, cond_en, cond_ar, next_review]
        if insert_if_missing(cur, "dpia", "reference", ref, cols, vals):
            n += 1
    return n


def seed_breach(cur):
    """3 historical breaches — illustrate full PDPL Art.20 workflow."""
    rows = [
        ("BR-2025-014", "Spear-phishing → mailbox compromise — branch ops officer",
         "هجوم تصيّد موجّه — اختراق صندوق بريد موظّف عمليات فرعية",
         60, "CONFIDENTIALITY", "EXTERNAL_ATTACK", "HIGH",
         847, 412, ["FINANCIAL","CONTACT","KYC_COPY"],
         False, False,
         "Attacker accessed branch ops officer's mailbox for 4 hours via OAuth-token theft. Mailbox contained "
         "412 customer KYC scans attached to ticket replies.",
         "وصل المهاجم إلى صندوق بريد موظّف عمليات الفروع لمدّة 4 ساعات عبر سرقة رمز OAuth. احتوى الصندوق على "
         "412 نسخة من وثائق اعرف عميلك مرفقة بردود التذاكر.",
         "CLOSED"),
        ("BR-2026-002", "AWS S3 bucket misconfiguration — short-lived public ACL",
         "إعداد خاطئ لـ S3 — قائمة وصول عامّة قصيرة الأمد",
         5, "CONFIDENTIALITY", "MISCONFIGURATION", "MEDIUM",
         2400, 2400, ["TRANSACTION","CONTACT"],
         False, True,
         "Bucket s3://tss-grc-marketing-segments unintentionally public for 47 minutes during a Terraform refactor. "
         "Anonymised marketing cohorts. No PDPL Art.4 special-category data exposed.",
         "أصبحت سلّة s3://tss-grc-marketing-segments عامّة بشكل غير مقصود لمدّة 47 دقيقة أثناء إعادة هيكلة Terraform. "
         "البيانات شرائح تسويقية مجهولة الهوية. لم تُكشَف بيانات حسّاسة بمفهوم المادة 4.",
         "NOTIFIED_SDAIA"),
        ("BR-2026-003", "Lost employee laptop with cached client list",
         "فقدان حاسب موظّف به قائمة عملاء مخزّنة محلياً",
         1, "AVAILABILITY", "LOST_DEVICE", "LOW",
         180, 180, ["CONTACT"],
         False, False,
         "Relationship Manager's laptop reported stolen at Riyadh airport. Disk fully encrypted (BitLocker AES-256) "
         "with TPM + PIN; remote-wipe issued within 14 minutes via Intune.",
         "أفاد مدير العلاقات بسرقة حاسبه في مطار الرياض. القرص مشفّر بالكامل (BitLocker AES-256) بـ TPM ورمز PIN؛ "
         "صدر مسح عن بُعد خلال 14 دقيقة عبر Intune.",
         "ASSESSING"),
    ]
    n = 0
    for ref, title_en, title_ar, days_ago, btype, cause, severity, recs, subjects, cats, is_special, is_cross, desc_en, desc_ar, status in rows:
        detected = NOW - timedelta(days=days_ago, hours=2)
        sdaia_deadline = detected + timedelta(hours=72)
        sdaia_notified = detected + timedelta(hours=48) if status in ("NOTIFIED_SDAIA","NOTIFYING_SUBJECTS","CONTAINED","REMEDIATING","CLOSED") else None
        subjects_notified = detected + timedelta(hours=70) if status in ("NOTIFYING_SUBJECTS","CONTAINED","REMEDIATING","CLOSED") else None
        closed = detected + timedelta(days=30) if status == "CLOSED" else None
        cols = ["organisation_id", "reference", "title_en", "title_ar",
                "detected_at", "discovered_at", "sdaia_deadline", "status",
                "breach_type", "cause", "severity",
                "affected_record_count", "affected_subject_count", "affected_categories_json",
                "is_special_category", "is_cross_border",
                "description_en", "description_ar",
                "sdaia_notified_at", "sdaia_reference",
                "subjects_notified_at", "closed_at"]
        vals = [ORG, ref, title_en, title_ar,
                detected, detected + timedelta(hours=1), sdaia_deadline, status,
                btype, cause, severity,
                recs, subjects, json.dumps(cats),
                1 if is_special else 0, 1 if is_cross else 0,
                desc_en, desc_ar,
                sdaia_notified, (f"SDAIA/INC/{detected.year}/{1000+abs(hash(ref))%9000:04d}" if sdaia_notified else None),
                subjects_notified, closed]
        if insert_if_missing(cur, "breach", "reference", ref, cols, vals):
            n += 1
    return n


def seed_privacy_notice(cur):
    """6 notices: web + mobile + branch, both English + Arabic versions."""
    rows = [
        ("NOT-2026-WEB-EN", "v4.1-2026-Q2", "WEB",
         "Privacy Notice — TSS GRC Bank",
         "إشعار الخصوصية — بنك TSS GRC",
         "We collect personal data when you open an account, transact, or contact us. Lawful bases: contract, legal "
         "obligation, legitimate interest. You have rights under PDPL Articles 9-14 (access, rectify, erase, restrict, "
         "portability, object). Contact: dpo@tss-grc.sa.",
         "نجمع بياناتك الشخصية عند فتح الحساب أو إجراء المعاملات أو الاتصال بنا. الأسس النظامية: العقد، الالتزام النظامي، "
         "المصلحة المشروعة. لديك حقوق وفق المواد 9-14 من نظام حماية البيانات (الاطّلاع، التصحيح، المحو، التقييد، النقل، الاعتراض). "
         "للتواصل: dpo@tss-grc.sa.",
         "PUBLISHED", True, 90),
        ("NOT-2026-MOB", "v4.1-2026-Q2", "MOBILE",
         "In-app Privacy Notice",
         "إشعار الخصوصية داخل التطبيق",
         "The mobile app collects device + location data for fraud detection and feature delivery. Push notifications "
         "and biometric login are opt-in. Withdraw consent any time in Settings → Privacy.",
         "يجمع تطبيق الجوال بيانات الجهاز والموقع لكشف الاحتيال وتقديم الميزات. الإشعارات والدخول بالبصمة اختياريّة. "
         "يمكنك سحب الموافقة في أيّ وقت من الإعدادات ← الخصوصية.",
         "PUBLISHED", False, 60),
        ("NOT-2026-BRA", "v4.0-2026-Q1", "BRANCH",
         "Branch-floor Privacy Notice",
         "إشعار الخصوصية في صالة الفرع",
         "CCTV operates 24/7 for safety and fraud prevention. Recordings retained 90 days. Branch staff may record "
         "transactions per SAMA AML requirements. See full notice on our website.",
         "تعمل كاميرات المراقبة على مدار الساعة للسلامة ومنع الاحتيال. تُحفَظ التسجيلات 90 يوماً. قد يسجّل موظّفو الفرع "
         "المعاملات وفق متطلّبات ساما لمكافحة غسل الأموال. اطّلع على الإشعار الكامل في موقعنا الإلكتروني.",
         "PUBLISHED", False, 120),
        ("NOT-2026-IVR", "v2.0-2026-Q2", "IVR",
         "Phone-banking Privacy Notice",
         "إشعار الخصوصية للهاتف المصرفي",
         "Calls are recorded for quality assurance and fraud prevention. By proceeding you consent to the recording. "
         "Recordings retained 12 months per SAMA requirements.",
         "تُسجَّل المكالمات لضمان الجودة ومنع الاحتيال. بمتابعتك أنت توافق على التسجيل. تُحفَظ التسجيلات 12 شهراً وفق "
         "متطلّبات ساما.",
         "PUBLISHED", False, 30),
        ("NOT-2026-ATM", "v1.5-2026-Q2", "ATM",
         "ATM screen — Privacy Notice",
         "إشعار الخصوصية — شاشة الصراف الآلي",
         "ATM cameras record transactions. Card and PIN data are encrypted end-to-end. Activity is monitored for "
         "skimming and fraud.",
         "تسجّل كاميرات الصرّاف المعاملات. بيانات البطاقة والرقم السرّي مشفّرة من طرف إلى طرف. تُراقب الأنشطة لكشف "
         "التصيّد والاحتيال.",
         "PUBLISHED", False, 60),
        ("NOT-2026-WEB-V5", "v5.0-2026-Q3", "WEB",
         "Privacy Notice (DRAFT) — adds AI credit-scoring disclosure",
         "إشعار الخصوصية (مسوّدة) — يضيف الإفصاح عن التقييم الائتماني الذكي",
         "Draft v5 — adds disclosure for new AI credit-scoring model (DPIA-2026-001). Will require re-consent.",
         "المسوّدة v5 — تضيف إفصاحاً عن نموذج التقييم الائتماني الذكي الجديد (DPIA-2026-001). يتطلّب إعادة موافقة.",
         "DRAFT", True, None),
    ]
    n = 0
    for ref, ver, channel, title_en, title_ar, body_en, body_ar, status, requires_reconsent, eff_days_ago in rows:
        effective_from = (TODAY - timedelta(days=eff_days_ago)) if eff_days_ago else None
        published_at = NOW - timedelta(days=eff_days_ago) if status == "PUBLISHED" else None
        cols = ["organisation_id", "reference", "version_label", "channel",
                "title_en", "title_ar", "body_en", "body_ar",
                "status", "effective_from", "published_at", "requires_re_consent"]
        vals = [ORG, ref, ver, channel, title_en, title_ar, body_en, body_ar,
                status, effective_from, published_at, 1 if requires_reconsent else 0]
        if insert_if_missing(cur, "privacy_notice", "reference", ref, cols, vals):
            n += 1
    return n


def seed_retention(cur):
    """8 retention rules per SAMA + AML requirements."""
    rows = [
        ("RET-KYC",  "CUSTOMER_KYC",     120, "SAMA KYC Rules require 10-year retention after relationship ends.",
         "تتطلّب قواعد اعرف عميلك لدى ساما الاحتفاظ لمدّة 10 سنوات بعد انتهاء العلاقة.",
         "CRYPTO_SHRED", 12),
        ("RET-TXN",  "TRANSACTION_LOG",  120, "Banking transaction records — 10 years per SAMA + AML/CFT.",
         "سجلات المعاملات المصرفية — 10 سنوات وفق ساما وقواعد مكافحة غسل الأموال.",
         "OVERWRITE", 12),
        ("RET-AML",  "AML_RECORD",       60,  "AML/STR records — 5 years per SAFIU requirements.",
         "سجلات مكافحة غسل الأموال والتقارير المشبوهة — 5 سنوات وفق متطلّبات SAFIU.",
         "CRYPTO_SHRED", 12),
        ("RET-MKT",  "MARKETING_PROFILE", 24,  "Marketing profiles — 2 years OR until consent withdrawal.",
         "ملفات التسويق — سنتان أو حتى سحب الموافقة.",
         "ANONYMISE", 12),
        ("RET-HR",   "EMPLOYEE_RECORD",   84,  "HR/payroll records — 7 years per Saudi Labor Law + GOSI.",
         "سجلات الموارد البشرية والرواتب — 7 سنوات وفق نظام العمل السعودي والتأمينات الاجتماعية.",
         "PHYSICAL_DESTRUCTION", 24),
        ("RET-CALL", "CALL_RECORDING",    12,  "Call recordings — 12 months for QA + dispute resolution.",
         "تسجيلات المكالمات — 12 شهراً لضمان الجودة وحلّ النزاعات.",
         "OVERWRITE", 6),
        ("RET-CCTV", "CCTV_FOOTAGE",      3,   "Branch CCTV — 90 days (extended from 30; per DPIA-2026-004).",
         "كاميرات الفروع — 90 يوماً (مُمدّدة من 30؛ وفق DPIA-2026-004).",
         "OVERWRITE", 6),
        ("RET-LOG",  "SYSTEM_LOG",        18,  "Application + security logs — 18 months per NCA ECC + cyber audit.",
         "سجلات التطبيقات والأمن — 18 شهراً وفق إطار الضوابط الأساسية للأمن السيبراني.",
         "OVERWRITE", 12),
    ]
    n = 0
    for ref, cat, months, basis_en, basis_ar, method, review_cadence in rows:
        next_review = TODAY + timedelta(days=30 * review_cadence)
        cols = ["organisation_id", "reference", "data_category",
                "description_en", "description_ar",
                "retention_months",
                "legal_basis_en", "legal_basis_ar",
                "disposal_method", "review_cadence_months", "next_review_due",
                "owner_role", "status"]
        vals = [ORG, ref, cat,
                f"Retain {cat} for {months} months", f"الاحتفاظ بـ {cat} لمدّة {months} شهراً",
                months,
                basis_en, basis_ar,
                method, review_cadence, next_review,
                "DPO", "ACTIVE"]
        if insert_if_missing(cur, "retention_rule", "reference", ref, cols, vals):
            n += 1
    return n


def seed_disposal(cur):
    """4 disposal jobs — one completed, two scheduled, one on hold."""
    # First grab retention_rule IDs we just created
    cur.execute("SELECT id, reference FROM data_officer.retention_rule WHERE organisation_id = ?", ORG)
    rules = {ref: rid for rid, ref in cur.fetchall()}
    if not rules:
        return 0
    rows = [
        ("DSP-2026-001", "RET-CALL", -15, "COMPLETED",   24812, "sha256:f4ab...", None),
        ("DSP-2026-002", "RET-MKT",  -5,  "COMPLETED",   1843,  "sha256:9c12...", None),
        ("DSP-2026-003", "RET-CCTV", 15,  "SCHEDULED",   None,  None, None),
        ("DSP-2026-004", "RET-LOG",  30,  "ON_HOLD",     None,  None,
         "Legal hold — pending Public Prosecution case 2026/PP/4421 (fraud investigation)"),
    ]
    n = 0
    for ref, rule_ref, days_offset, status, records, evidence, hold_reason in rows:
        if rule_ref not in rules:
            continue
        scheduled = TODAY + timedelta(days=days_offset)
        cols = ["organisation_id", "retention_rule_id", "reference",
                "scheduled_for", "status",
                "records_disposed", "evidence_hash", "legal_hold_reason",
                "started_at", "completed_at"]
        started = NOW + timedelta(days=days_offset) if status in ("COMPLETED","IN_PROGRESS") else None
        completed = NOW + timedelta(days=days_offset, hours=4) if status == "COMPLETED" else None
        vals = [ORG, rules[rule_ref], ref, scheduled, status,
                records, evidence, hold_reason, started, completed]
        if insert_if_missing(cur, "disposal_job", "reference", ref, cols, vals):
            n += 1
    return n


def seed_ndr(cur):
    """2 NDR submissions — last year accepted, this year in progress."""
    year_now = TODAY.year
    rows = [
        (f"NDR-{year_now-1}-001", year_now-1, "NDR Annual Submission " + str(year_now-1),
         f"تقديم السجل الوطني السنوي {year_now-1}",
         "ACCEPTED", 67, -180, "SDAIA/NDR/2025/001142",
         "Submission accepted with two minor observations on data-lineage coverage; addressed in this cycle.",
         "قُبل التقديم مع ملاحظتين بسيطتين حول تغطية تتبّع البيانات؛ عُولجتا في الدورة الحاليّة."),
        (f"NDR-{year_now}-001", year_now, "NDR Annual Submission " + str(year_now),
         f"تقديم السجل الوطني السنوي {year_now}",
         "INTERNAL_REVIEW", 81, -30, None, None, None),
    ]
    n = 0
    for ref, year, title_en, title_ar, status, count, days_ago, sub_ref, resp_en, resp_ar in rows:
        submitted = NOW + timedelta(days=days_ago) if status in ("SUBMITTED","ACCEPTED","REJECTED","REVISION_REQUESTED") else None
        accepted = submitted + timedelta(days=21) if status == "ACCEPTED" else None
        cols = ["organisation_id", "reference", "cycle_year",
                "title_en", "title_ar", "status",
                "dataset_count", "submitted_at", "submission_ref",
                "accepted_at", "response_summary_en", "response_summary_ar"]
        vals = [ORG, ref, year, title_en, title_ar, status,
                count, submitted, sub_ref, accepted, resp_en, resp_ar]
        if insert_if_missing(cur, "ndr_submission", "reference", ref, cols, vals):
            n += 1
    return n


def seed_catalog(cur):
    """10 catalog entries — the major datasets in a Saudi bank."""
    rows = [
        ("CAT-T24-CUST",  "Customer Master (T24)",        "السجل الرئيسي للعملاء (T24)",
         "Single source of truth for customer KYC, demographics, segments. Feeds risk + AML + marketing.",
         "المصدر الموحّد لاعرف عميلك والديموغرافيا والشرائح. يغذّي المخاطر ومكافحة غسل الأموال والتسويق.",
         "CUSTOMER", "T24-Core", "Exadata-Prod / customer.master", "CONFIDENTIAL", True, True,
         "Head of Retail", 2_140_000, "REAL_TIME"),
        ("CAT-T24-ACCT",  "Account Balances (T24)",       "أرصدة الحسابات (T24)",
         "Real-time account ledger. Feeds payments, statements, regulatory reporting.",
         "دفتر أستاذ الحسابات اللحظي. يغذّي المدفوعات والكشوفات والتقارير التنظيمية.",
         "PAYMENT", "T24-Core", "Exadata-Prod / account.ledger", "CONFIDENTIAL", False, False,
         "Head of Operations", 4_820_000, "REAL_TIME"),
        ("CAT-SADAD",     "SADAD Bill Payments",          "مدفوعات سداد",
         "Outbound bill-payment transactions to SADAD network.",
         "معاملات سداد الفواتير الصادرة إلى شبكة سداد.",
         "PAYMENT", "SADAD-Gateway", "Exadata-Prod / sadad.tx_log", "CONFIDENTIAL", False, False,
         "Payments Manager", 12_400_000, "HOURLY"),
        ("CAT-MUREX",     "Trading Positions (Murex)",    "مراكز التداول (Murex)",
         "Treasury + capital-markets trading book. Feeds market risk + counterparty exposure.",
         "دفتر التداول للخزينة وأسواق رأس المال. يغذّي مخاطر السوق وانكشاف الأطراف المقابلة.",
         "RISK", "Murex-3.1", "Exadata-Prod / murex.positions", "CONFIDENTIAL", False, False,
         "Treasurer", 184_000, "REAL_TIME"),
        ("CAT-AML-ALERTS","AML Alerts (Actimize)",        "تنبيهات مكافحة غسل الأموال",
         "Suspicious-transaction alerts + SAFIU STR pipeline.",
         "تنبيهات المعاملات المشبوهة وقناة التقارير إلى SAFIU.",
         "COMPLIANCE", "Actimize", "Exadata-Prod / aml.alerts", "TOP_SECRET", True, True,
         "MLRO", 38_400, "REAL_TIME"),
        ("CAT-MARKETING", "Marketing Segments (CRM)",     "شرائح التسويق (CRM)",
         "Customer segments for campaign targeting; derived from anonymised transaction patterns.",
         "شرائح العملاء لاستهداف الحملات؛ مُستخلصة من أنماط معاملات مجهولة الهوية.",
         "MARKETING", "Salesforce", "AWS Bahrain / crm.segments", "INTERNAL", False, False,
         "Head of Marketing", 1_820_000, "DAILY"),
        ("CAT-FRAUD-MOD", "Behavioural Biometrics Store",  "مخزن البيومتريا السلوكية",
         "Typing-rhythm + touch-dynamics features for continuous authentication (DPIA-2026-003).",
         "ميزات نمط الطباعة وديناميكيات اللمس للمصادقة المستمرّة (DPIA-2026-003).",
         "FRAUD", "Internal-Biometrics-v2", "On-prem / bio.features", "TOP_SECRET", True, True,
         "Head of Cyber Fraud", 2_100_000, "HOURLY"),
        ("CAT-CREDIT-SC", "Credit Scoring Features",      "ميزات التقييم الائتماني",
         "Feature store for AI credit-scoring model v2.1 (DPIA-2026-001).",
         "مخزن الميزات لنموذج التقييم الائتماني الذكي v2.1 (DPIA-2026-001).",
         "RISK", "Internal-Feature-Store", "Exadata-Prod / credit.features", "CONFIDENTIAL", True, False,
         "CRO", 2_140_000, "DAILY"),
        ("CAT-HR-PAY",    "HR + Payroll (Oracle EBS)",    "الموارد البشرية والرواتب (Oracle EBS)",
         "Employee master + payroll runs. Feeds GOSI + Mudad submissions.",
         "السجل الرئيسي للموظفين وعمليات الرواتب. يغذّي تقديمات التأمينات الاجتماعية ومُدد.",
         "HR", "Oracle-EBS-12", "Exadata-HR / hr.employees", "CONFIDENTIAL", True, False,
         "CHRO", 4_800, "MONTHLY"),
        ("CAT-CALL-REC",  "Call Recordings (Genesys)",    "تسجيلات المكالمات (Genesys)",
         "Phone-banking + branch-callback recordings, retained 12 months.",
         "تسجيلات الهاتف المصرفي والمكالمات المرتجعة من الفرع، تُحفَظ 12 شهراً.",
         "CUSTOMER", "Genesys-Cloud", "AWS Bahrain / genesys.recordings", "CONFIDENTIAL", True, False,
         "Head of Contact Centre", 8_400_000, "REAL_TIME"),
    ]
    n = 0
    for ref, name_en, name_ar, desc_en, desc_ar, domain, source, storage, classif, pii, special, owner_role, rows_n, freq in rows:
        cols = ["organisation_id", "reference",
                "dataset_name_en", "dataset_name_ar",
                "description_en", "description_ar",
                "domain", "source_system", "storage_location", "classification",
                "contains_pii", "contains_special_category",
                "owner_role", "custodian_team",
                "row_count_approx", "refresh_frequency", "last_profiled_at"]
        vals = [ORG, ref, name_en, name_ar, desc_en, desc_ar,
                domain, source, storage, classif,
                1 if pii else 0, 1 if special else 0,
                owner_role, "DBA Team",
                rows_n, freq, NOW - timedelta(days=abs(hash(ref)) % 14)]
        if insert_if_missing(cur, "catalog_entry", "reference", ref, cols, vals):
            n += 1
    return n


def seed_lineage(cur):
    """Data-lineage edges between catalog entries — the bank's data-flow map.

    Idempotent on (organisation_id, source_entry_id, target_entry_id).
    """
    # (source reference, target reference, transform_label)
    edges = [
        ("CAT-T24-CUST",  "CAT-MARKETING",  "PII-minimised customer attributes -> CRM segmentation"),
        ("CAT-T24-CUST",  "CAT-CREDIT-SC",  "KYC + demographics -> credit-scoring feature store"),
        ("CAT-T24-ACCT",  "CAT-CREDIT-SC",  "Balance & transaction aggregates -> credit features"),
        ("CAT-T24-ACCT",  "CAT-AML-ALERTS", "Account postings -> Actimize AML scenarios"),
        ("CAT-SADAD",     "CAT-AML-ALERTS", "SADAD payment events -> AML transaction monitoring"),
        ("CAT-FRAUD-MOD", "CAT-AML-ALERTS", "Behavioural-biometric signals -> fraud/AML correlation"),
        ("CAT-FRAUD-MOD", "CAT-CREDIT-SC",  "Device & biometric risk score -> credit/fraud features"),
        ("CAT-CALL-REC",  "CAT-MARKETING",  "Genesys call-sentiment -> segment refinement"),
    ]
    cur.execute("SELECT id, reference FROM data_officer.catalog_entry WHERE organisation_id = ?", ORG)
    id_by_ref = {ref: cid for cid, ref in cur.fetchall()}
    n = 0
    for src_ref, tgt_ref, label in edges:
        src = id_by_ref.get(src_ref)
        tgt = id_by_ref.get(tgt_ref)
        if not src or not tgt:
            continue
        cur.execute(
            "SELECT 1 FROM data_officer.catalog_lineage "
            "WHERE organisation_id = ? AND source_entry_id = ? AND target_entry_id = ?",
            ORG, src, tgt)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO data_officer.catalog_lineage "
            "(organisation_id, source_entry_id, target_entry_id, transform_label, "
            " is_deleted, created_at, updated_at) "
            "VALUES (?, ?, ?, ?, 0, ?, ?)",
            ORG, src, tgt, label, NOW, NOW)
        n += 1
    return n


def seed_quality(cur):
    """8 quality rules across 5 catalog entries + 12 recent runs."""
    cur.execute("SELECT id, reference FROM data_officer.catalog_entry WHERE organisation_id = ?", ORG)
    cats = {ref: cid for cid, ref in cur.fetchall()}
    rule_specs = [
        ("DQR-001", "CAT-T24-CUST",  "National-ID completeness",      "اكتمال الهوية الوطنية",     "COMPLETENESS", 99.5, "CRITICAL"),
        ("DQR-002", "CAT-T24-CUST",  "Email format validity",         "صحّة صياغة البريد",         "VALIDITY",     95.0, "HIGH"),
        ("DQR-003", "CAT-T24-ACCT",  "Account-customer FK integrity", "تكامل المفتاح الأجنبي للحساب","INTEGRITY",   100.0, "CRITICAL"),
        ("DQR-004", "CAT-SADAD",     "Transaction timestamp present", "وجود طابع زمني للمعاملة",   "COMPLETENESS",  99.9, "CRITICAL"),
        ("DQR-005", "CAT-MUREX",     "Position currency in ISO 4217", "العملة وفق ISO 4217",       "VALIDITY",     100.0, "HIGH"),
        ("DQR-006", "CAT-AML-ALERTS","Duplicate alert-id check",      "فحص تكرار معرّف التنبيه",   "UNIQUENESS",   100.0, "HIGH"),
        ("DQR-007", "CAT-MARKETING", "Segment freshness <72h",        "حداثة الشريحة أقلّ من 72س", "TIMELINESS",    95.0, "MEDIUM"),
        ("DQR-008", "CAT-CREDIT-SC", "Feature drift PSI <0.2",        "انحراف الميزة PSI أقلّ من 0.2","CONSISTENCY", 90.0, "HIGH"),
    ]
    rules_inserted = 0
    rule_ids = {}
    for ref, cat_ref, name_en, name_ar, dim, threshold, sev in rule_specs:
        if cat_ref not in cats:
            continue
        cols = ["organisation_id", "reference", "catalog_entry_id",
                "rule_name_en", "rule_name_ar", "dimension",
                "rule_expression", "threshold_pct", "severity", "is_active"]
        vals = [ORG, ref, cats[cat_ref], name_en, name_ar, dim,
                f"-- {name_en}", threshold, sev, 1]
        cur.execute("SELECT id FROM data_officer.quality_rule WHERE reference = ? AND organisation_id = ?", ref, ORG)
        existing = cur.fetchone()
        if existing:
            rule_ids[ref] = existing[0]
        elif insert_if_missing(cur, "quality_rule", "reference", ref, cols, vals):
            rules_inserted += 1
            cur.execute("SELECT id FROM data_officer.quality_rule WHERE reference = ? AND organisation_id = ?", ref, ORG)
            rule_ids[ref] = cur.fetchone()[0]
    # Now seed runs
    runs_inserted = 0
    for i, (rule_ref, days_ago, pass_pct, band) in enumerate([
        ("DQR-001", 1,  99.7, "GREEN"),
        ("DQR-001", 8,  99.4, "AMBER"),
        ("DQR-002", 1,  96.2, "GREEN"),
        ("DQR-002", 8,  94.1, "AMBER"),
        ("DQR-003", 1, 100.0, "GREEN"),
        ("DQR-004", 1,  99.95,"GREEN"),
        ("DQR-005", 1, 100.0, "GREEN"),
        ("DQR-006", 1,  99.98,"GREEN"),
        ("DQR-007", 1,  82.4, "RED"),
        ("DQR-007", 8,  88.0, "AMBER"),
        ("DQR-008", 1,  78.6, "RED"),
        ("DQR-008", 8,  91.2, "GREEN"),
    ]):
        if rule_ref not in rule_ids:
            continue
        # quality_run has no `reference` column. Dedup on the DETERMINISTIC
        # (rule, records_checked) signature — run_at is derived from NOW and
        # changes every invocation, so keying on it duplicated rows on re-run.
        run_at = NOW - timedelta(days=days_ago, hours=i)
        records_checked = 100000 + (i * 12345) % 50000
        records_failed = int(records_checked * (1 - pass_pct / 100.0))
        cur.execute("SELECT 1 FROM data_officer.quality_run WHERE quality_rule_id = ? AND records_checked = ?",
                    rule_ids[rule_ref], records_checked)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO data_officer.quality_run "
            "(organisation_id, quality_rule_id, run_at, records_checked, records_failed, pass_pct, band) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            ORG, rule_ids[rule_ref], run_at, records_checked, records_failed, pass_pct, band)
        runs_inserted += 1
    return rules_inserted, runs_inserted


def seed_ethics(cur):
    """4 ethics reviews — covers AI model + open-data + data-sharing."""
    rows = [
        ("ETH-2026-001", "AI_MODEL", "MODEL-2026-001",
         "AI Credit Scoring v2.1 — fairness across nationality + gender",
         "تقييم الائتمان الذكي v2.1 — الإنصاف عبر الجنسية والجنس",
         "Pre-deployment ethics review for the new XGBoost credit-scoring model. Bias score 4.2% (within 5% threshold). "
         "Conditions: monthly bias re-test, quarterly Model Validator attestation.",
         "مراجعة أخلاقية قبل النشر لنموذج XGBoost الجديد لتقييم الائتمان. درجة التحيّز 4.2% (ضمن الحدّ 5%). "
         "الشروط: إعادة اختبار شهرية للتحيّز، إقرار ربعي من مدقّق النموذج.",
         '{"fairness":"MEDIUM","privacy":"LOW","explainability":"MEDIUM","accountability":"LOW"}',
         "APPROVED_WITH_CONDITIONS", 30),
        ("ETH-2026-002", "DATA_USE_CASE", "USE-2026-004",
         "Marketing data-clean-room with telco partner — REJECTED",
         "غرفة بيانات تسويقية مع شريك اتصالات — مرفوضة",
         "Joint review with DPO. Re-identification risk above tolerance. Required k-anonymity >=100 not met.",
         "مراجعة مشتركة مع مسؤول حماية البيانات. خطر إعادة التعرّف أعلى من المسموح. لم يُستوفَ شرط k>=100.",
         '{"privacy":"HIGH","fairness":"LOW","explainability":"LOW","accountability":"MEDIUM"}',
         "REJECTED", 28),
        ("ETH-2026-003", "NEW_PROCESSING", "DPIA-2026-003",
         "Behavioural biometrics — continuous authentication",
         "البيومتريا السلوكية — المصادقة المستمرّة",
         "Committee review pending — DPO opinion drafted. Concerns over user awareness; explicit opt-in required.",
         "مراجعة اللجنة قيد الانتظار — مسوّدة رأي مسؤول حماية البيانات. مخاوف حول وعي المستخدم؛ موافقة صريحة مطلوبة.",
         '{"fairness":"LOW","privacy":"HIGH","explainability":"HIGH","accountability":"MEDIUM"}',
         "COMMITTEE_REVIEW", 7),
        ("ETH-2026-004", "OPEN_DATA_RELEASE", "OD-2026-001",
         "Open release — branch-traffic aggregate stats (data.gov.sa)",
         "نشر مفتوح — إحصاءات حركة الفروع المُجمَّعة (data.gov.sa)",
         "Proposed dataset for SDAIA Open Data portal. Aggregated to >=1000 transactions per cell; no PII risk.",
         "مجموعة بيانات مُقترحة لبوّابة البيانات المفتوحة في سدايا. مُجمَّعة بحدّ >=1000 معاملة لكل خليّة؛ لا يوجد خطر بيانات شخصية.",
         '{"fairness":"LOW","privacy":"LOW","explainability":"LOW","accountability":"LOW"}',
         "APPROVED", 14),
    ]
    n = 0
    for ref, subject_type, subject_ref, title_en, title_ar, summary_en, summary_ar, risks_json, status, days_ago in rows:
        requested = NOW - timedelta(days=days_ago)
        committee = requested + timedelta(days=5) if status in ("COMMITTEE_REVIEW","OPINION_DRAFTED","APPROVED","APPROVED_WITH_CONDITIONS","REJECTED") else None
        decided = requested + timedelta(days=14) if status in ("APPROVED","APPROVED_WITH_CONDITIONS","REJECTED") else None
        next_review = TODAY + timedelta(days=90) if status in ("APPROVED","APPROVED_WITH_CONDITIONS") else None
        cols = ["organisation_id", "reference",
                "subject_type", "subject_ref",
                "title_en", "title_ar",
                "summary_en", "summary_ar",
                "requested_at", "status", "risk_dimensions_json",
                "committee_meeting_at", "decided_at", "next_review_due"]
        vals = [ORG, ref, subject_type, subject_ref,
                title_en, title_ar, summary_en, summary_ar,
                requested, status, risks_json,
                committee, decided, next_review]
        if insert_if_missing(cur, "ethics_review", "reference", ref, cols, vals):
            n += 1
    return n


def main():
    print("Sprint 33 — Data Officer seed (realistic Saudi banking data)")
    with pyodbc.connect(CONN_STR, autocommit=False) as conn:
        cur = conn.cursor()
        dsr = seed_dsr(cur)
        consent = seed_consent(cur)
        dpia = seed_dpia(cur)
        breach = seed_breach(cur)
        notice = seed_privacy_notice(cur)
        retention = seed_retention(cur)
        disposal = seed_disposal(cur)
        ndr = seed_ndr(cur)
        catalog = seed_catalog(cur)
        lineage = seed_lineage(cur)
        quality_rules, quality_runs = seed_quality(cur)
        ethics = seed_ethics(cur)
        conn.commit()
        print(f"  [+] DSR requests       : {dsr}")
        print(f"  [+] Consent records    : {consent}")
        print(f"  [+] DPIA assessments   : {dpia}")
        print(f"  [+] Breaches           : {breach}")
        print(f"  [+] Privacy notices    : {notice}")
        print(f"  [+] Retention rules    : {retention}")
        print(f"  [+] Disposal jobs      : {disposal}")
        print(f"  [+] NDR submissions    : {ndr}")
        print(f"  [+] Catalog entries    : {catalog}")
        print(f"  [+] Lineage edges      : {lineage}")
        print(f"  [+] Quality rules      : {quality_rules}")
        print(f"  [+] Quality runs       : {quality_runs}")
        print(f"  [+] Ethics reviews     : {ethics}")
        print("Done.")


if __name__ == "__main__":
    main()
