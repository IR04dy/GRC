"""Saudi-realistic AI/ML Model Lifecycle seed — Sprint 31 PR #4.

Seeds validation events + retrainings + recertifications against the
existing MlModel rows that the Sprint 11 registry seeded. If no models
exist yet, this script creates 6 demo models (credit-scoring,
fraud-detection, AML-monitoring, customer-churn, Arabic OCR, chatbot)
and then builds the lifecycle artefacts around them.

Primed events:
  * One CRITICAL validation FAILED — fairness disparity 14% (primes AR-274)
  * One drift-triggered retraining in PROPOSED (primes AR-275 fast-track)
  * One DEPLOYED retraining 30 days ago (AR-276 record)
  * One ROLLED_BACK retraining (AR-277 post-mortem CAPA)
  * One Recertification DUE overdue by 18 days (primes AR-278)

Usage: python setup_ai_lifecycle_seed.py
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


def ensure_model(org, code, *, title_en, title_ar, mtype, domain, tier,
                  stage="IN_PRODUCTION", version="v1.4.2"):
    cur.execute(
        "SELECT id FROM ai_governance.ml_model "
        "WHERE organisation_id = ? AND model_code = ? AND is_deleted = 0",
        org, code,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.ml_model "
        "(organisation_id, model_code, title_en, title_ar, model_type, "
        " business_domain, risk_tier, lifecycle_stage, version_label) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?)",
        org, code, title_en, title_ar, mtype, domain, tier, stage, version,
    )
    return int(cur.fetchone()[0])


def ins_val(org, model_id, ref, status, *, vtype="INDEPENDENT", validator_label=None,
            scheduled=None, started=None, completed=None,
            disparity=None, findings_en=None, findings_ar=None,
            conditions_en=None, conditions_ar=None):
    cur.execute(
        "SELECT id FROM ai_governance.model_validation "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.model_validation "
        "(organisation_id, model_id, reference, status, validation_type, "
        " validator_org_label, scheduled_at, started_at, completed_at, "
        " fairness_disparity_pct, findings_en, findings_ar, conditions_en, conditions_ar) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, model_id, ref, status, vtype, validator_label, scheduled,
        started, completed, disparity, findings_en, findings_ar,
        conditions_en, conditions_ar,
    )
    return int(cur.fetchone()[0])


def ins_ret(org, model_id, ref, status, *, trigger=None, rationale_en=None, rationale_ar=None,
            from_v=None, to_v=None, approved=None, started=None, deployed=None,
            rolled_back=None, rb_reason_en=None, rb_reason_ar=None):
    cur.execute(
        "SELECT id FROM ai_governance.retraining_event "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.retraining_event "
        "(organisation_id, model_id, reference, status, trigger_reason, "
        " rationale_en, rationale_ar, from_version_label, to_version_label, "
        " approved_at, started_at, deployed_at, rolled_back_at, "
        " rollback_reason_en, rollback_reason_ar) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        org, model_id, ref, status, trigger, rationale_en, rationale_ar,
        from_v, to_v, approved, started, deployed, rolled_back,
        rb_reason_en, rb_reason_ar,
    )
    return int(cur.fetchone()[0])


def ins_rec(org, model_id, ref, status, *, cycle=None, due=None,
            decided=None, decision_en=None, decision_ar=None,
            conditions_en=None, conditions_ar=None, next_due=None):
    cur.execute(
        "SELECT id FROM ai_governance.recertification "
        "WHERE organisation_id = ? AND reference = ? AND is_deleted = 0",
        org, ref,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.recertification "
        "(organisation_id, model_id, reference, cycle_label, status, "
        " due_date, decided_at, next_due_date, "
        " decision_rationale_en, decision_rationale_ar, "
        " conditions_en, conditions_ar) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        org, model_id, ref, cycle, status, due, decided, next_due,
        decision_en, decision_ar, conditions_en, conditions_ar,
    )
    return int(cur.fetchone()[0])


def seed_org1():
    print(">>> org=1 TSS GRC bank — AI/ML lifecycle book")

    # ─── Ensure 6 Saudi-realistic models exist ─────────────────────────
    m = {}
    m['credit'] = ensure_model(1, "MDL-CRD-001",
        title_en="Retail Credit Scoring Model (XGBoost)",
        title_ar="نموذج تسجيل ائتماني للأفراد (XGBoost)",
        mtype="CLASSIFICATION", domain="CREDIT_DECISION", tier="CRITICAL",
        version="v3.2.1")
    m['fraud'] = ensure_model(1, "MDL-FRD-002",
        title_en="Card-Fraud Detection (Deep Learning)",
        title_ar="كشف احتيال البطاقات (تعلّم عميق)",
        mtype="CLASSIFICATION", domain="FRAUD_DETECTION", tier="CRITICAL",
        version="v2.0.4")
    m['aml'] = ensure_model(1, "MDL-AML-003",
        title_en="AML Transaction Monitoring (Graph + ML)",
        title_ar="مراقبة معاملات مكافحة غسل الأموال (رسم بياني + تعلم آلي)",
        mtype="CLASSIFICATION", domain="AML_MONITORING", tier="HIGH",
        version="v1.7.0")
    m['churn'] = ensure_model(1, "MDL-CHR-004",
        title_en="Customer Churn Prediction",
        title_ar="التنبؤ بفقدان العملاء",
        mtype="CLASSIFICATION", domain="CUSTOMER_CHURN", tier="MEDIUM",
        version="v1.2.0")
    m['ocr'] = ensure_model(1, "MDL-OCR-005",
        title_en="Arabic Document OCR (Iqama / Saudi ID)",
        title_ar="استخراج النص العربي للوثائق (إقامة / هوية وطنية)",
        mtype="COMPUTER_VISION", domain="KYC_OCR", tier="HIGH",
        version="v2.1.3")
    m['chat'] = ensure_model(1, "MDL-CHT-006",
        title_en="Customer Support Chatbot (Arabic LLM)",
        title_ar="مساعد دعم العملاء (نموذج لغوي عربي)",
        mtype="LLM", domain="CHATBOT", tier="MEDIUM",
        version="v0.9.5")

    # ─── Validations ──────────────────────────────────────────────────

    # Credit-scoring 2026-Q1 — PASSED_WITH_CONDITIONS at 14% fairness disparity
    ins_val(1, m['credit'], "VAL-2026-Q1-CRD",
        "PASSED_WITH_CONDITIONS",
        validator_label="Deloitte Saudi Arabia — Model Validation Practice",
        scheduled=TODAY - timedelta(days=40),
        started=NOW - timedelta(days=35), completed=NOW - timedelta(days=10),
        disparity=14.2,
        findings_en="Credit-decline rate disparity 14.2% between female and male applicants (target ≤ 5%). Disparity driven by income-proxy features that correlate with employment-sector segmentation. Model accuracy 0.94, precision 0.91, recall 0.88.",
        findings_ar="تفاوت معدل رفض الائتمان ١٤.٢٪ بين المتقدمات والمتقدمين (المستهدف ≤ ٥٪). يدفع التفاوتَ سماتُ وكيلٍ للدخل ترتبط بتقسيم قطاع التوظيف. دقة النموذج ٠.٩٤، الإحكام ٠.٩١، الاستذكار ٠.٨٨.",
        conditions_en="(1) Re-engineer income-proxy features to remove sector correlation. (2) Add fairness regulariser to objective function. (3) Re-validate within 90 days. (4) DPO sign-off required.",
        conditions_ar="(١) إعادة هندسة سمات وكيل الدخل لإزالة الارتباط القطاعي. (٢) إضافة منظِّم العدالة لدالة الهدف. (٣) إعادة التحقق خلال ٩٠ يوماً. (٤) موافقة الحامي مطلوبة.")

    # Fraud-detection — PASSED
    ins_val(1, m['fraud'], "VAL-2026-Q1-FRD",
        "PASSED",
        validator_label="KPMG Saudi Arabia — Risk Modelling",
        scheduled=TODAY - timedelta(days=85),
        started=NOW - timedelta(days=82), completed=NOW - timedelta(days=72),
        disparity=2.1,
        findings_en="Precision 0.97, recall 0.93 on Q4-2025 hold-out. No demographic disparity above 3% threshold. Performance within model risk tolerance.",
        findings_ar="الإحكام ٠.٩٧، الاستذكار ٠.٩٣ على عيّنة الربع الرابع ٢٠٢٥. لا تفاوت ديموغرافي يتجاوز عتبة ٣٪. الأداء داخل تحمّل مخاطر النموذج.")

    # AML — FAILED (primes AR-272)
    ins_val(1, m['aml'], "VAL-2026-Q1-AML",
        "FAILED",
        validator_label="EY Saudi Arabia — AML Analytics",
        scheduled=TODAY - timedelta(days=30),
        started=NOW - timedelta(days=25), completed=NOW - timedelta(days=4),
        disparity=6.8,
        findings_en="STR-filing recall dropped to 0.81 on Q1-2026 hold-out (target ≥ 0.90). False-negative rate on STC Pay-funded transfers materially elevated. Graph-feature pipeline regression suspected after Q4-2025 feature-store upgrade.",
        findings_ar="انخفض استذكار رفع تقارير العمليات المشبوهة إلى ٠.٨١ على عيّنة الربع الأول ٢٠٢٦ (المستهدف ≥ ٠.٩٠). معدّل السلبيّات الكاذبة في التحويلات المموَّلة من STC Pay مرتفع بشكل جوهري. يُشتبه في ارتداد خط أنابيب سمات الرسم البياني بعد ترقية مخزن السمات الربع الرابع ٢٠٢٥.")

    # Arabic OCR — PASSED
    ins_val(1, m['ocr'], "VAL-2025-Q4-OCR",
        "PASSED",
        validator_label="STC Solutions AI Lab — independent validation",
        scheduled=TODAY - timedelta(days=140),
        started=NOW - timedelta(days=130), completed=NOW - timedelta(days=110),
        disparity=1.5,
        findings_en="Character accuracy 98.6%, field accuracy 96.4% on 1,200-document hold-out set spanning National ID, Iqama, and Saudi driving licences. Within tolerance.",
        findings_ar="دقة الحرف ٩٨.٦٪، دقة الحقل ٩٦.٤٪ على عيّنة ١,٢٠٠ وثيقة تشمل الهوية الوطنية والإقامة ورخصة القيادة السعودية. ضمن التحمّل.")

    # ─── Retrainings ──────────────────────────────────────────────────

    # Credit — DRIFT-triggered, PROPOSED (primes AR-275)
    ins_ret(1, m['credit'], "RTR-2026-Q1-CRD-001",
        "PROPOSED",
        trigger="DRIFT",
        rationale_en="Population drift detected — Q1-2026 KSA labour-market shift (Saudisation Phase IV impact). Feature distributions for employment-tenure and sector are no longer aligned with training set. Retraining proposed with last 18 months of data.",
        rationale_ar="رُصد انحراف في توزيع السكان — تحوّل سوق العمل السعودي في الربع الأول ٢٠٢٦ (أثر المرحلة الرابعة للسعودة). توزيعات سمات مدة الخدمة والقطاع لم تعد متوافقة مع مجموعة التدريب. تُقترح إعادة التدريب على بيانات آخر ١٨ شهراً.",
        from_v="v3.2.1", to_v="v3.3.0")

    # Fraud — DEPLOYED 30 days ago (AR-276)
    ins_ret(1, m['fraud'], "RTR-2026-Q1-FRD-001",
        "DEPLOYED",
        trigger="SCHEDULED",
        rationale_en="Quarterly retraining cycle. Added 2.3M new transactions + 4,200 new labelled fraud cases. Precision improved 0.95 → 0.97.",
        rationale_ar="دورة إعادة تدريب فصلية. أُضيف ٢.٣ مليون معاملة جديدة و٤,٢٠٠ حالة احتيال مصنّفة جديدة. تحسّن الإحكام من ٠.٩٥ إلى ٠.٩٧.",
        from_v="v2.0.3", to_v="v2.0.4",
        approved=NOW - timedelta(days=55), started=NOW - timedelta(days=50),
        deployed=TODAY - timedelta(days=30))

    # Churn — ROLLED_BACK (primes AR-277)
    ins_ret(1, m['churn'], "RTR-2026-Q1-CHR-001",
        "ROLLED_BACK",
        trigger="POST_INCIDENT",
        rationale_en="Retraining after Q4 marketing campaign biased the training set toward promotional-response signal. New version under-predicted churn risk by 38% in the first 5 days of production.",
        rationale_ar="إعادة تدريب بعد حملة تسويقية للربع الرابع تسبّبت في تحيّز مجموعة التدريب نحو إشارة الاستجابة الترويجية. النسخة الجديدة قلّلت تقدير مخاطر فقدان العملاء بنسبة ٣٨٪ في أول ٥ أيام من الإنتاج.",
        from_v="v1.1.0", to_v="v1.2.0",
        approved=NOW - timedelta(days=40), started=NOW - timedelta(days=35),
        deployed=TODAY - timedelta(days=20),
        rolled_back=TODAY - timedelta(days=15),
        rb_reason_en="Under-prediction of churn 38% — campaign signal contamination in training set.",
        rb_reason_ar="نقص التنبؤ بفقدان العملاء ٣٨٪ — تلوّث إشارة الحملة في مجموعة التدريب.")

    # ─── Recertifications ─────────────────────────────────────────────

    # Credit — overdue (primes AR-278)
    ins_rec(1, m['credit'], "REC-2026-CRD",
        "DUE",
        cycle="Annual 2026",
        due=TODAY - timedelta(days=18))

    # Fraud — IN_REVIEW
    ins_rec(1, m['fraud'], "REC-2026-FRD",
        "DUE",
        cycle="Annual 2026",
        due=TODAY + timedelta(days=42))

    # AML — RECERTIFIED_WITH_CONDITIONS last year
    ins_rec(1, m['aml'], "REC-2025-AML",
        "RECERTIFIED_WITH_CONDITIONS",
        cycle="Annual 2025",
        due=TODAY - timedelta(days=370),
        decided=NOW - timedelta(days=350),
        decision_en="Recertified conditional on completing graph-feature pipeline upgrade by Q2-2026.",
        decision_ar="إعادة اعتماد مشروطة بإكمال ترقية خط أنابيب سمات الرسم البياني بحلول الربع الثاني ٢٠٢٦.",
        conditions_en="Graph-pipeline upgrade + AR-275 retraining cycle.",
        conditions_ar="ترقية خط أنابيب الرسم البياني + دورة إعادة التدريب AR-275.",
        next_due=TODAY + timedelta(days=15))

    # Chatbot — DUE in 30 days
    ins_rec(1, m['chat'], "REC-2026-CHT",
        "DUE", cycle="Annual 2026",
        due=TODAY + timedelta(days=30))


def main():
    print(">>> Sprint 31 PR #4 — Saudi-realistic AI/ML lifecycle seed")
    try:
        seed_org1()
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"!!! ROLLED BACK: {e}")
        raise
    for tbl, label in [
        ("ai_governance.ml_model", "models"),
        ("ai_governance.model_validation", "validations"),
        ("ai_governance.retraining_event", "retrainings"),
        ("ai_governance.recertification", "recertifications"),
    ]:
        cur.execute(f"SELECT COUNT(*) FROM {tbl} WHERE is_deleted = 0")
        print(f"+ {label:<20s}: {cur.fetchone()[0]}")
    print(">>> done.")


if __name__ == "__main__":
    main()
