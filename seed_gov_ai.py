# -*- coding: utf-8 -*-
"""Seed a Saudi public-authority AI governance layer into the ai / ai_governance schemas.

Tenant: organisation_id = 1 — a Saudi GENERAL AUTHORITY (هيئة عامة) headed by a
President (رئيس الهيئة). The existing org-1 AI rows are BANK-themed (credit scoring,
card fraud, STC-Pay AML, churn, bank chatbot/OCR), seeded by
setup_ai_lifecycle_seed.py / setup_ai_lifecycle_db.py. This script ADDS a
public-authority AI registry + lifecycle ALONGSIDE those rows and NEVER touches them.

What it seeds (all org 1, idempotent):
  * ai_governance.ml_model            — 6 public-authority models, prefix GA-
  * ai_governance.model_validation    — 6 validations  (VAL-2026-GA-*)
  * ai_governance.ml_model_audit      — 8 audits (FAIRNESS/AIA/PRIVACY/SECURITY/…)
  * ai_governance.ml_model_drift_observation — 10 post-market drift observations
  * ai_governance.retraining_event    — 4 retraining cycles (RTR-2026-GA-*)
  * ai_governance.recertification     — 5 recertifications (REC-2026-GA-*)
  * ai_governance.ai_lifecycle_event  — 12 timeline events
  * ai.ai_suggestions                 — 4 authority-flavoured AI-assist suggestions

Regulatory anchors woven through the bilingual text:
  SDAIA AI Ethics (2024 principles), NDMO/SDAIA data governance, PDPL Art.20
  (human review of automated decisions) + Art.29 (cross-border), DGA (digital
  government service automation), NCA ECC (model/pipeline cybersecurity), EU AI Act
  (maturity yardstick only). Committee tie-in: COM-AIETHICS (admin.committees id=17,
  لجنة أخلاقيات الذكاء الاصطناعي) + Data Governance Committee — expressed narratively,
  as no FK columns exist.

HARD CONSTRAINTS obeyed: INSERT / idempotent-upsert ONLY. No DELETE/TRUNCATE/DROP/
ALTER. organisation_id = 1 only. Only ai / ai_governance schemas touched. The
ml_model.business_domain CHECK is locked to 11 commercial enums with no public-sector
value — authority use-cases are mapped onto the allowed set (RISK_SCORING,
DOCUMENT_CLASSIFICATION, KYC_OCR, CHATBOT, OPERATIONS_AUTOMATION). The schema is NOT
widened.

Idempotency: ml_model by (organisation_id, model_code 'GA-*'); validation/retraining/
recertification by (organisation_id, reference); audit by (model_id, audit_type,
audit_date); drift by (organisation_id, model_id, metric_name, observed_at) probe;
lifecycle_event by (organisation_id, model_id, event_type, occurred_at) probe;
ai_suggestion by (organisation_id, feature_code, related_entity, related_entity_id).
All timestamps are deterministic so a second run inserts 0.

    Usage:  python seed_gov_ai.py
"""
from __future__ import annotations

import json
import sys
from datetime import date, datetime

import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

_CONN_CANDIDATES = [
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;",
    "DRIVER={ODBC Driver 18 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Encrypt=no;TrustServerCertificate=yes;Unicode_Results=Yes;",
]


def connect() -> pyodbc.Connection:
    last = None
    for cs in _CONN_CANDIDATES:
        try:
            return pyodbc.connect(cs)
        except Exception as exc:  # noqa: BLE001 - try next driver
            last = exc
    raise SystemExit(f"Could not connect to SQL Server: {last}")


ORG = 1

# iam.users (org1): admin=1, rmanager=2, compliance=3, auditor=4, ranalyst=5, viewer=6
U_ADMIN, U_RMGR, U_COMPL, U_AUDITOR, U_RANALYST, U_VIEWER = 1, 2, 3, 4, 5, 6

# Committee narrative actors (no FK columns — free text only)
ETHICS_CTE = "لجنة أخلاقيات الذكاء الاصطناعي — AI Ethics Committee (COM-AIETHICS)"
DATAGOV_CTE = "لجنة حوكمة البيانات — Data Governance Committee (COM-DATAGOV)"
PRIVACY_OFFICE = "مكتب حماية البيانات (DPO) — Privacy Office / DPO"
NCA_ASSESSOR = "مُقيّم مواءم مع الضوابط الأساسية للأمن السيبراني (NCA ECC)"
DGA_REVIEW = "هيئة الحكومة الرقمية — مراجعة مستقلة (DGA)"

# Tracking counters per table
counts: dict[str, int] = {}


def jdump(obj) -> str:
    return json.dumps(obj, ensure_ascii=False)


# ──────────────────────────────────────────────────────────────────────────────
# ml_model
# ──────────────────────────────────────────────────────────────────────────────
def ensure_model(cur, code, **kw) -> int:
    cur.execute(
        "SELECT id FROM ai_governance.ml_model "
        "WHERE organisation_id=? AND model_code=?",
        ORG, code,
    )
    r = cur.fetchone()
    if r:
        print(f"  = {code:<14} (exists, id={r[0]})")
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.ml_model "
        "(organisation_id, model_code, title_en, title_ar, description_en, description_ar, "
        " model_type, business_domain, risk_tier, lifecycle_stage, version_label, "
        " version_history_json, model_owner_user_id, business_sponsor_user_id, validator_user_id, "
        " validation_date, production_date, training_data_provenance_en, training_data_provenance_ar, "
        " training_data_size_records, feature_count, framework, hosting, "
        " has_pdpl_dpia, has_fairness_audit, has_explainability_doc, is_high_risk_eu_ai_act, "
        " sdaia_ai_ethics_compliance, sama_ai_principles_compliance, "
        " human_oversight, human_oversight_notes_en, human_oversight_notes_ar, "
        " data_residency, cross_border_transfer_basis, created_by, updated_by) "
        "OUTPUT INSERTED.id VALUES "
        "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ORG, code, kw["title_en"], kw["title_ar"], kw["desc_en"], kw["desc_ar"],
        kw["mtype"], kw["domain"], kw["tier"], kw["stage"], kw["version"],
        kw["vhist"], kw["owner"], kw["sponsor"], kw.get("validator"),
        kw.get("val_date"), kw.get("prod_date"), kw["prov_en"], kw["prov_ar"],
        kw.get("rows"), kw.get("features"), kw.get("framework"), kw.get("hosting"),
        kw.get("dpia", 0), kw.get("fairness", 0), kw.get("explain", 0), kw.get("eu_high", 0),
        kw.get("sdaia", "NOT_ASSESSED"), kw.get("sama", "NOT_ASSESSED"),
        kw.get("oversight"), kw.get("ov_en"), kw.get("ov_ar"),
        kw.get("residency"), kw.get("xborder"), U_ADMIN, U_ADMIN,
    )
    mid = int(cur.fetchone()[0])
    counts["ml_model"] = counts.get("ml_model", 0) + 1
    print(f"  + {code:<14} {kw['title_en']}  (id={mid})")
    return mid


# ──────────────────────────────────────────────────────────────────────────────
# model_validation
# ──────────────────────────────────────────────────────────────────────────────
def ins_val(cur, model_id, ref, status, **kw) -> int:
    cur.execute(
        "SELECT id FROM ai_governance.model_validation "
        "WHERE organisation_id=? AND reference=?",
        ORG, ref,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.model_validation "
        "(organisation_id, model_id, reference, status, validation_type, validator_user_id, "
        " validator_org_label, scheduled_at, started_at, completed_at, metrics_json, "
        " ethics_scores_json, fairness_disparity_pct, findings_en, findings_ar, "
        " conditions_en, conditions_ar, created_by, updated_by) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ORG, model_id, ref, status, kw.get("vtype"), kw.get("validator_user", U_AUDITOR),
        kw.get("validator_label"), kw.get("scheduled"), kw.get("started"), kw.get("completed"),
        kw.get("metrics"), kw.get("ethics"), kw.get("disparity"),
        kw.get("findings_en"), kw.get("findings_ar"),
        kw.get("conditions_en"), kw.get("conditions_ar"), U_AUDITOR, U_AUDITOR,
    )
    vid = int(cur.fetchone()[0])
    counts["model_validation"] = counts.get("model_validation", 0) + 1
    return vid


# ──────────────────────────────────────────────────────────────────────────────
# ml_model_audit
# ──────────────────────────────────────────────────────────────────────────────
def ins_audit(cur, model_id, audit_type, audit_date, status, **kw) -> int:
    cur.execute(
        "SELECT id FROM ai_governance.ml_model_audit "
        "WHERE model_id=? AND audit_type=? AND audit_date=?",
        model_id, audit_type, audit_date,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.ml_model_audit "
        "(organisation_id, model_id, audit_type, audit_date, auditor_user_id, "
        " auditor_external_party, methodology_en, methodology_ar, findings_en, findings_ar, "
        " status, metrics_json, created_by, updated_by) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ORG, model_id, audit_type, audit_date, kw.get("auditor_user", U_AUDITOR),
        kw.get("party"), kw.get("method_en"), kw.get("method_ar"),
        kw.get("findings_en"), kw.get("findings_ar"), status, kw.get("metrics"),
        U_AUDITOR, U_AUDITOR,
    )
    aid = int(cur.fetchone()[0])
    counts["ml_model_audit"] = counts.get("ml_model_audit", 0) + 1
    return aid


# ──────────────────────────────────────────────────────────────────────────────
# ml_model_drift_observation  (no natural unique index — probe synthetic key)
# ──────────────────────────────────────────────────────────────────────────────
def ins_drift(cur, model_id, observed_at, otype, severity, metric_name, metric_value, **kw) -> int:
    cur.execute(
        "SELECT id FROM ai_governance.ml_model_drift_observation "
        "WHERE organisation_id=? AND model_id=? AND metric_name=? AND observed_at=?",
        ORG, model_id, metric_name, observed_at,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.ml_model_drift_observation "
        "(organisation_id, model_id, observed_at, observation_type, severity, metric_name, "
        " metric_value, baseline_value, drift_threshold, notes_en, notes_ar, "
        " is_acknowledged, acknowledged_at, acknowledged_by_user_id, created_by, updated_by) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ORG, model_id, observed_at, otype, severity, metric_name, metric_value,
        kw.get("baseline"), kw.get("threshold"), kw.get("notes_en"), kw.get("notes_ar"),
        kw.get("ack", 0), kw.get("ack_at"), kw.get("ack_by"), U_RANALYST, U_RANALYST,
    )
    did = int(cur.fetchone()[0])
    counts["ml_model_drift_observation"] = counts.get("ml_model_drift_observation", 0) + 1
    return did


# ──────────────────────────────────────────────────────────────────────────────
# retraining_event
# ──────────────────────────────────────────────────────────────────────────────
def ins_ret(cur, model_id, ref, status, **kw) -> int:
    cur.execute(
        "SELECT id FROM ai_governance.retraining_event "
        "WHERE organisation_id=? AND reference=?",
        ORG, ref,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.retraining_event "
        "(organisation_id, model_id, reference, status, trigger_reason, rationale_en, rationale_ar, "
        " from_version_label, to_version_label, approved_at, started_at, deployed_at, "
        " post_deploy_metrics_json, owner_user_id, created_by, updated_by) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ORG, model_id, ref, status, kw.get("trigger"), kw.get("rationale_en"), kw.get("rationale_ar"),
        kw.get("from_v"), kw.get("to_v"), kw.get("approved"), kw.get("started"), kw.get("deployed"),
        kw.get("post_metrics"), kw.get("owner", U_RANALYST), U_RANALYST, U_RANALYST,
    )
    rid = int(cur.fetchone()[0])
    counts["retraining_event"] = counts.get("retraining_event", 0) + 1
    return rid


# ──────────────────────────────────────────────────────────────────────────────
# recertification
# ──────────────────────────────────────────────────────────────────────────────
def ins_rec(cur, model_id, ref, status, **kw) -> int:
    cur.execute(
        "SELECT id FROM ai_governance.recertification "
        "WHERE organisation_id=? AND reference=?",
        ORG, ref,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.recertification "
        "(organisation_id, model_id, reference, cycle_label, status, due_date, started_at, "
        " decided_at, next_due_date, decision_rationale_en, decision_rationale_ar, "
        " conditions_en, conditions_ar, approver_user_id, linked_validation_id, created_by, updated_by) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ORG, model_id, ref, kw.get("cycle"), status, kw.get("due"), kw.get("started"),
        kw.get("decided"), kw.get("next_due"), kw.get("decision_en"), kw.get("decision_ar"),
        kw.get("conditions_en"), kw.get("conditions_ar"),
        kw.get("approver", U_RMGR), kw.get("linked_validation"), U_RMGR, U_RMGR,
    )
    cid = int(cur.fetchone()[0])
    counts["recertification"] = counts.get("recertification", 0) + 1
    return cid


# ──────────────────────────────────────────────────────────────────────────────
# ai_lifecycle_event  (no natural unique index — probe synthetic key)
# ──────────────────────────────────────────────────────────────────────────────
def ins_event(cur, model_id, occurred_at, event_type, **kw) -> int:
    cur.execute(
        "SELECT id FROM ai_governance.ai_lifecycle_event "
        "WHERE organisation_id=? AND model_id=? AND event_type=? AND occurred_at=?",
        ORG, model_id, event_type, occurred_at,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai_governance.ai_lifecycle_event "
        "(organisation_id, model_id, validation_id, retraining_id, recertification_id, "
        " occurred_at, event_type, actor_user_id, actor_name, status_after, note_en, note_ar, "
        " created_by, updated_by) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ORG, model_id, kw.get("validation_id"), kw.get("retraining_id"), kw.get("recertification_id"),
        occurred_at, event_type, kw.get("actor_user", U_ADMIN), kw.get("actor_name"),
        kw.get("status_after"), kw.get("note_en"), kw.get("note_ar"), U_ADMIN, U_ADMIN,
    )
    eid = int(cur.fetchone()[0])
    counts["ai_lifecycle_event"] = counts.get("ai_lifecycle_event", 0) + 1
    return eid


# ──────────────────────────────────────────────────────────────────────────────
# ai.ai_suggestions  (no natural unique index — probe synthetic key)
# ──────────────────────────────────────────────────────────────────────────────
def ins_suggestion(cur, feature_code, related_entity, related_entity_id, status, **kw) -> int:
    cur.execute(
        "SELECT id FROM ai.ai_suggestions "
        "WHERE organisation_id=? AND feature_code=? AND related_entity=? AND related_entity_id=?",
        ORG, feature_code, related_entity, related_entity_id,
    )
    r = cur.fetchone()
    if r:
        return int(r[0])
    cur.execute(
        "INSERT INTO ai.ai_suggestions "
        "(organisation_id, feature_code, related_entity, related_entity_id, input_json, output_json, "
        " status, confidence, rationale, reviewed_by, reviewed_at, reviewer_note, created_by) "
        "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
        ORG, feature_code, related_entity, related_entity_id, kw["input"], kw["output"],
        status, kw.get("confidence"), kw.get("rationale"),
        kw.get("reviewed_by"), kw.get("reviewed_at"), kw.get("reviewer_note"), U_ADMIN,
    )
    sid = int(cur.fetchone()[0])
    counts["ai_suggestions"] = counts.get("ai_suggestions", 0) + 1
    return sid


# Deterministic dates for idempotency
def dt(y, m, d, hh=9, mm=0):
    return datetime(y, m, d, hh, mm, 0)


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    print(">>> Seeding Saudi public-authority AI governance layer (organisation_id=1)\n")

    # ════════════════════════════════════════════════════════════════════════
    # 1. ml_model — 6 public-authority models (prefix GA-)
    # ════════════════════════════════════════════════════════════════════════
    print("ai_governance.ml_model")
    m = {}

    m["insp"] = ensure_model(cur, "GA-INSP-001",
        title_en="Inspection Risk-Prioritisation Engine",
        title_ar="محرك تحديد أولويات التفتيش حسب المخاطر",
        desc_en="Risk-based scoring of regulated entities to prioritise field inspections; supports — but does not replace — the inspector's decision (PDPL Art.20 human review).",
        desc_ar="تقييم قائم على المخاطر للمنشآت الخاضعة للرقابة لترتيب أولويات الزيارات التفتيشية؛ يدعم قرار المفتّش ولا يحلّ محلّه (المادة 20 من نظام حماية البيانات: المراجعة البشرية).",
        mtype="CLASSIFICATION", domain="RISK_SCORING", tier="HIGH", stage="IN_PRODUCTION",
        version="v2.1.0",
        vhist=jdump([{"v": "v1.0.0", "date": "2024-06-01", "note": "Initial pilot"},
                     {"v": "v2.0.0", "date": "2025-03-15", "note": "Production release"},
                     {"v": "v2.1.0", "date": "2026-02-20", "note": "Regional re-balancing after fairness audit"}]),
        owner=U_RANALYST, sponsor=U_RMGR, validator=U_AUDITOR,
        val_date=date(2026, 1, 28), prod_date=date(2025, 3, 15),
        prov_en="Three years of historical inspection outcomes, licensing records and complaint history from the Authority's operational systems; no third-party datasets.",
        prov_ar="ثلاث سنوات من نتائج التفتيش التاريخية وسجلات التراخيص وسجل الشكاوى من الأنظمة التشغيلية للهيئة؛ دون أي مجموعات بيانات من أطراف خارجية.",
        rows=420000, features=58, framework="XGBOOST", hosting="AZURE_KSA",
        dpia=1, fairness=1, explain=1, eu_high=1, sdaia="COMPLIANT", sama="NOT_ASSESSED",
        oversight="HUMAN_IN_THE_LOOP",
        ov_en="Inspectors must review and justify each visit selection; scores are advisory and a reason code is recorded for every inspection decision (PDPL Art.20).",
        ov_ar="يجب على المفتّشين مراجعة وتبرير اختيار كل زيارة؛ الدرجات استرشادية ويُسجَّل رمز سبب لكل قرار تفتيش (المادة 20).",
        residency="KSA")

    m["elig"] = ensure_model(cur, "GA-ELIG-002",
        title_en="Beneficiary Eligibility Risk Scoring",
        title_ar="تقييم مخاطر أهلية المستفيدين",
        desc_en="Flags beneficiary applications for enhanced review based on eligibility-risk; fundamental-rights sensitive — decisions remain with a human officer (human-in-command).",
        desc_ar="ترشيح طلبات المستفيدين للمراجعة المعزّزة بناءً على مخاطر الأهلية؛ حسّاس للحقوق الأساسية — يبقى القرار بيد موظف بشري (الإنسان في القيادة).",
        mtype="CLASSIFICATION", domain="RISK_SCORING", tier="CRITICAL", stage="VALIDATION",
        version="v0.9.0",
        vhist=jdump([{"v": "v0.5.0", "date": "2025-09-01", "note": "Development"},
                     {"v": "v0.9.0", "date": "2026-04-10", "note": "Independent validation in progress"}]),
        owner=U_RANALYST, sponsor=U_RMGR, validator=U_AUDITOR,
        prov_en="Beneficiary registry, eligibility-criteria records and historical adjudication outcomes; nationality and gender held out as protected attributes for fairness testing only.",
        prov_ar="سجل المستفيدين وسجلات معايير الأهلية ونتائج البتّ التاريخية؛ تُستبعَد الجنسية والجنس كسمات محمية لاختبار العدالة فقط.",
        rows=210000, features=46, framework="XGBOOST", hosting="AZURE_KSA",
        dpia=1, fairness=1, explain=1, eu_high=1, sdaia="PARTIAL", sama="NOT_ASSESSED",
        oversight="HUMAN_IN_COMMAND",
        ov_en="No automated denial of benefits. Every adverse flag is routed to a human officer; beneficiaries have an appeal path (PDPL Art.20 right to human review).",
        ov_ar="لا يوجد رفض آلي للمنافع. يُحال كل ترشيح سلبي إلى موظف بشري؛ ولأصحاب الطلبات حق التظلّم (المادة 20: الحق في المراجعة البشرية).",
        residency="KSA")

    m["doc"] = ensure_model(cur, "GA-DOC-003",
        title_en="Incoming Correspondence Auto-Routing & Classification",
        title_ar="التصنيف والتوجيه الآلي للمراسلات الواردة",
        desc_en="Arabic NLP classifies and routes incoming correspondence to the correct department; a human officer can override every routing decision.",
        desc_ar="معالجة لغة عربية تُصنّف وتوجّه المراسلات الواردة إلى الإدارة المختصّة؛ ويمكن لموظف بشري تجاوز كل قرار توجيه.",
        mtype="NLP", domain="DOCUMENT_CLASSIFICATION", tier="MEDIUM", stage="IN_PRODUCTION",
        version="v1.4.0",
        vhist=jdump([{"v": "v1.0.0", "date": "2025-01-20", "note": "Production"},
                     {"v": "v1.4.0", "date": "2026-01-12", "note": "Added new correspondence categories"}]),
        owner=U_RANALYST, sponsor=U_RMGR, validator=U_AUDITOR,
        val_date=date(2025, 12, 10), prod_date=date(2025, 1, 20),
        prov_en="Internal corpus of historical correspondence labelled by the records-management office; data kept on-premises for national data residency.",
        prov_ar="مجموعة داخلية من المراسلات التاريخية مصنّفة من قبل إدارة الوثائق؛ تُحفظ البيانات داخلياً امتثالاً لمتطلبات إقامة البيانات الوطنية.",
        rows=180000, features=0, framework="HUGGINGFACE", hosting="ON_PREM",
        dpia=0, fairness=0, explain=1, eu_high=0, sdaia="COMPLIANT", sama="NOT_ASSESSED",
        oversight="HUMAN_ON_THE_LOOP",
        ov_en="Misrouted items are corrected by the records office and fed back as training signal; humans monitor and intervene on the queue.",
        ov_ar="تُصحَّح العناصر الموجَّهة خطأً من قبل إدارة الوثائق وتُعاد كإشارة تدريب؛ ويراقب البشر قائمة التوجيه ويتدخّلون عند الحاجة.",
        residency="KSA")

    m["ocr"] = ensure_model(cur, "GA-OCR-004",
        title_en="Licensing Attachment OCR (National ID / Iqama / Commercial Registration)",
        title_ar="استخراج النص للمرفقات (هوية وطنية / إقامة / سجل تجاري)",
        desc_en="Computer-vision OCR extracts structured fields from licensing attachments; extracted values are verified by a licensing officer before use.",
        desc_ar="استخراج بصري للنص يستخرج الحقول المنظّمة من مرفقات التراخيص؛ ويتحقّق موظف التراخيص من القيم المستخرجة قبل اعتمادها.",
        mtype="COMPUTER_VISION", domain="KYC_OCR", tier="HIGH", stage="IN_PRODUCTION",
        version="v2.3.0",
        vhist=jdump([{"v": "v1.0.0", "date": "2024-11-05", "note": "Production"},
                     {"v": "v2.0.0", "date": "2025-08-01", "note": "Iqama template coverage"},
                     {"v": "v2.3.0", "date": "2026-03-05", "note": "New National-ID template"}]),
        owner=U_RANALYST, sponsor=U_RMGR, validator=U_AUDITOR,
        val_date=date(2026, 2, 14), prod_date=date(2024, 11, 5),
        prov_en="Synthetic and consented sample documents (National ID, Iqama, Commercial Registration) processed on-premises; personal data minimised and DPIA completed.",
        prov_ar="وثائق عيّنة اصطناعية وبموافقة (هوية وطنية، إقامة، سجل تجاري) تُعالَج داخلياً؛ مع تقليل البيانات الشخصية وإتمام تقييم أثر حماية البيانات.",
        rows=95000, features=0, framework="TENSORFLOW", hosting="ON_PREM",
        dpia=1, fairness=1, explain=0, eu_high=1, sdaia="COMPLIANT", sama="NOT_ASSESSED",
        oversight="HUMAN_IN_THE_LOOP",
        ov_en="A licensing officer confirms every extracted field; low-confidence extractions are flagged for manual entry (PDPL data-minimisation, on-prem pipeline).",
        ov_ar="يؤكّد موظف التراخيص كل حقل مستخرج؛ وتُعلَّم عمليات الاستخراج منخفضة الثقة للإدخال اليدوي (تقليل البيانات، خط معالجة داخلي).",
        residency="KSA")

    m["asst"] = ensure_model(cur, "GA-ASST-005",
        title_en="Beneficiary Virtual Assistant (Arabic RAG)",
        title_ar="المساعد الافتراضي للمستفيدين (استرجاع معزّز عربي)",
        desc_en="Retrieval-augmented Arabic assistant answers beneficiary questions from approved policy content; cannot take benefit actions — it hands off to a human for any transaction.",
        desc_ar="مساعد عربي بالاسترجاع المعزّز يجيب على أسئلة المستفيدين من محتوى السياسات المعتمد؛ لا يُنفّذ أي إجراء على المنافع — بل يحوّل لأي معاملة إلى موظف بشري.",
        mtype="LLM", domain="CHATBOT", tier="MEDIUM", stage="IN_PRODUCTION",
        version="v1.2.0",
        vhist=jdump([{"v": "v0.9.0", "date": "2025-06-15", "note": "Pilot"},
                     {"v": "v1.0.0", "date": "2025-10-01", "note": "Production"},
                     {"v": "v1.2.0", "date": "2026-04-01", "note": "RAG groundedness controls"}]),
        owner=U_RANALYST, sponsor=U_RMGR, validator=U_AUDITOR,
        val_date=date(2026, 3, 20), prod_date=date(2025, 10, 1),
        prov_en="Curated Arabic policy and FAQ corpus owned by the Authority; the LLM is accessed via API with retrieval grounding and citation enforcement.",
        prov_ar="مجموعة عربية منسَّقة من السياسات والأسئلة الشائعة مملوكة للهيئة؛ ويُستخدَم النموذج اللغوي عبر واجهة برمجية مع تأريض الاسترجاع وإلزام الاستشهاد.",
        rows=0, features=0, framework="OPENAI_API", hosting="MIXED",
        dpia=1, fairness=0, explain=0, eu_high=0, sdaia="PARTIAL", sama="NOT_ASSESSED",
        oversight="HUMAN_ON_THE_LOOP",
        ov_en="Answers are grounded in approved content with citations; any benefit action is escalated to a human agent. Cross-border LLM access governed under PDPL Art.29.",
        ov_ar="الإجابات مؤرَّضة في محتوى معتمد مع استشهادات؛ ويُصعَّد أي إجراء على المنافع إلى موظف بشري. ويخضع الوصول عبر الحدود للنموذج اللغوي للمادة 29.",
        residency="GLOBAL", xborder="SDAIA_ADEQUACY/BCR_SCC")

    m["sla"] = ensure_model(cur, "GA-SLA-006",
        title_en="Service SLA & Workload Forecasting",
        title_ar="التنبؤ بأحمال الخدمة ومستويات الخدمة",
        desc_en="Forecasts service demand and SLA breach risk to plan staffing; operational-efficiency model with no impact on individual beneficiaries.",
        desc_ar="يتنبّأ بالطلب على الخدمة ومخاطر الإخلال باتفاقيات مستوى الخدمة لتخطيط الكوادر؛ نموذج كفاءة تشغيلية دون أثر على أفراد المستفيدين.",
        mtype="FORECASTING", domain="OPERATIONS_AUTOMATION", tier="LOW", stage="MONITORING",
        version="v1.1.0",
        vhist=jdump([{"v": "v1.0.0", "date": "2025-07-01", "note": "Production"},
                     {"v": "v1.1.0", "date": "2026-02-01", "note": "Seasonality features"}]),
        owner=U_RANALYST, sponsor=U_RMGR, validator=U_AUDITOR,
        val_date=date(2026, 1, 15), prod_date=date(2025, 7, 1),
        prov_en="Aggregated, non-personal service-ticket volumes and channel-mix history; no personal data used.",
        prov_ar="أحجام تذاكر خدمة مجمّعة وغير شخصية وتاريخ مزيج القنوات؛ دون استخدام أي بيانات شخصية.",
        rows=72000, features=24, framework="LIGHTGBM", hosting="ON_PREM",
        dpia=0, fairness=0, explain=1, eu_high=0, sdaia="COMPLIANT", sama="NOT_ASSESSED",
        oversight="HUMAN_ON_THE_LOOP",
        ov_en="Forecasts inform capacity planning; operations managers approve staffing decisions.",
        ov_ar="تُرشد التنبؤات تخطيط السعة؛ ويعتمد مديرو العمليات قرارات التوظيف.",
        residency="KSA")

    # ════════════════════════════════════════════════════════════════════════
    # 2. model_validation — one per new model
    # ════════════════════════════════════════════════════════════════════════
    print("\nai_governance.model_validation")
    v = {}
    sdaia8 = lambda **k: jdump({
        "fairness": k.get("fairness", 80), "transparency": k.get("transparency", 80),
        "accountability": k.get("accountability", 85), "privacy": k.get("privacy", 85),
        "safety_reliability": k.get("safety", 85), "humanity": k.get("humanity", 90),
        "social_environmental": k.get("social", 80), "human_oversight": k.get("oversight", 88),
    })

    v["insp"] = ins_val(cur, m["insp"], "VAL-2026-GA-INSP", "PASSED_WITH_CONDITIONS",
        vtype="INDEPENDENT", validator_label=DGA_REVIEW,
        scheduled=date(2026, 1, 5), started=dt(2026, 1, 8), completed=dt(2026, 1, 28),
        metrics=jdump({"accuracy": 0.91, "precision": 0.88, "recall": 0.85, "auc": 0.90}),
        ethics=sdaia8(fairness=72),
        disparity=7.0,
        findings_en="Inspection-selection disparity ~7% across regions (target ≤ 5%); driven by historical inspection density rather than risk. Accuracy 0.91, recall 0.85. Explainability (reason codes) adequate.",
        findings_ar="تفاوت في اختيار التفتيش يبلغ نحو 7% بين المناطق (المستهدف ≤ 5%)؛ مدفوع بكثافة التفتيش التاريخية لا بالمخاطر. الدقة 0.91، الاستذكار 0.85. تفسيريّة كافية (رموز الأسباب).",
        conditions_en="(1) Regional re-balancing of training data. (2) AI Ethics Committee sign-off. (3) Enforce PDPL Art.20 reason codes on every selection. (4) Re-validate within 6 months.",
        conditions_ar="(1) إعادة موازنة بيانات التدريب بين المناطق. (2) اعتماد لجنة أخلاقيات الذكاء الاصطناعي. (3) إلزام رموز أسباب المادة 20 لكل اختيار. (4) إعادة التحقق خلال 6 أشهر.")

    v["elig"] = ins_val(cur, m["elig"], "VAL-2026-GA-ELIG", "IN_PROGRESS",
        vtype="INDEPENDENT", validator_label=f"{DGA_REVIEW} + {ETHICS_CTE}",
        scheduled=date(2026, 4, 1), started=dt(2026, 4, 10),
        metrics=jdump({"auc": 0.83, "precision": 0.80, "recall": 0.79}),
        ethics=sdaia8(fairness=68, transparency=75),
        disparity=None,
        findings_en="Fundamental-rights AIA in progress: fairness tested across nationality and gender; preliminary disparity under review. Human-in-command and appeal path confirmed in design.",
        findings_ar="تقييم أثر على الحقوق الأساسية قيد التنفيذ: تُختبَر العدالة عبر الجنسية والجنس؛ والتفاوت الأولي قيد المراجعة. وتأكّد في التصميم مبدأ الإنسان في القيادة ومسار التظلّم.",
        conditions_en="(1) Complete fundamental-rights AIA with AI Ethics Committee. (2) No automated denials. (3) Documented appeal path. (4) Fairness within tolerance before production.",
        conditions_ar="(1) إكمال تقييم أثر الحقوق الأساسية مع لجنة أخلاقيات الذكاء الاصطناعي. (2) لا رفض آلي. (3) مسار تظلّم موثّق. (4) عدالة ضمن الحدود قبل الإنتاج.")

    v["doc"] = ins_val(cur, m["doc"], "VAL-2026-GA-DOC", "PASSED",
        vtype="INTERNAL", validator_label="مكتب إدارة البيانات — Data Management Office",
        scheduled=date(2025, 11, 20), started=dt(2025, 11, 25), completed=dt(2025, 12, 10),
        metrics=jdump({"routing_accuracy": 0.94, "macro_f1": 0.91, "top1_accuracy": 0.94}),
        ethics=sdaia8(),
        findings_en="Arabic NLP routing accuracy 94%, macro-F1 0.91 on held-out correspondence. No personal-data leakage; on-prem inference confirmed.",
        findings_ar="دقة توجيه معالجة اللغة العربية 94%، ومتوسط F1 يبلغ 0.91 على مراسلات محجوزة. لا تسرّب لبيانات شخصية؛ وتأكّد الاستدلال الداخلي.")

    v["ocr"] = ins_val(cur, m["ocr"], "VAL-2026-GA-OCR", "PASSED",
        vtype="INDEPENDENT", validator_label=f"{NCA_ASSESSOR} + {PRIVACY_OFFICE}",
        scheduled=date(2026, 1, 20), started=dt(2026, 1, 25), completed=dt(2026, 2, 14),
        metrics=jdump({"character_accuracy": 0.987, "field_accuracy": 0.962}),
        ethics=sdaia8(privacy=90),
        disparity=0.0,
        findings_en="Character accuracy 98.7%, field accuracy 96.2% on National ID / Iqama / CR set. No demographic disparity. DPIA complete; on-prem pipeline meets NCA ECC controls.",
        findings_ar="دقة الحرف 98.7%، ودقة الحقل 96.2% على عيّنة الهوية الوطنية/الإقامة/السجل التجاري. لا تفاوت ديموغرافي. اكتمل تقييم أثر حماية البيانات؛ ويلبّي خط المعالجة الداخلي ضوابط الأمن السيبراني الأساسية.")

    v["asst"] = ins_val(cur, m["asst"], "VAL-2026-GA-ASST", "PASSED_WITH_CONDITIONS",
        vtype="INDEPENDENT", validator_label=f"{DGA_REVIEW} + {ETHICS_CTE}",
        scheduled=date(2026, 3, 1), started=dt(2026, 3, 5), completed=dt(2026, 3, 20),
        metrics=jdump({"groundedness": 0.89, "hallucination_rate": 0.06, "citation_coverage": 0.93}),
        ethics=sdaia8(transparency=82, safety=78),
        findings_en="GenAI groundedness 0.89, hallucination rate 6% on policy queries (target ≤ 3%). Citation coverage 93%. Cross-border LLM access reviewed under PDPL Art.29.",
        findings_ar="تأريض الذكاء التوليدي 0.89، ومعدل الهلوسة 6% على استفسارات السياسات (المستهدف ≤ 3%). تغطية الاستشهاد 93%. وروجِع الوصول عبر الحدود للنموذج اللغوي وفق المادة 29.",
        conditions_en="(1) Enforce RAG citation on every answer. (2) Human handoff for any benefit action. (3) Reduce hallucination below 3% before next recert. (4) SDAIA groundedness controls.",
        conditions_ar="(1) إلزام استشهاد الاسترجاع المعزّز في كل إجابة. (2) تحويل بشري لأي إجراء على المنافع. (3) خفض الهلوسة دون 3% قبل إعادة الاعتماد التالية. (4) ضوابط تأريض سدايا.")

    v["sla"] = ins_val(cur, m["sla"], "VAL-2026-GA-SLA", "PASSED",
        vtype="INTERNAL", validator_label="إدارة العمليات — Operations",
        scheduled=date(2026, 1, 5), started=dt(2026, 1, 8), completed=dt(2026, 1, 15),
        metrics=jdump({"mape": 0.072, "rmse": 38.4, "directional_accuracy": 0.88}),
        ethics=sdaia8(),
        findings_en="MAPE 7.2% within tolerance (≤ 10%). No personal data; operational-efficiency model only. Approved for continued monitoring.",
        findings_ar="نسبة الخطأ المطلق المئوي 7.2% ضمن الحدود (≤ 10%). لا بيانات شخصية؛ نموذج كفاءة تشغيلية فقط. معتمد لمواصلة المراقبة.")

    # ════════════════════════════════════════════════════════════════════════
    # 3. ml_model_audit — 8 audits
    # ════════════════════════════════════════════════════════════════════════
    print("\nai_governance.ml_model_audit")
    ins_audit(cur, m["insp"], "AIA", date(2026, 1, 25), "PASSED_WITH_CONDITIONS",
        auditor_user=U_COMPL, party=ETHICS_CTE,
        method_en="Algorithmic Impact Assessment per SDAIA AI Ethics and EU AI Act high-risk criteria; reviewed by the AI Ethics Committee.",
        method_ar="تقييم الأثر الخوارزمي وفق أخلاقيات سدايا للذكاء الاصطناعي ومعايير المخاطر العالية في نظام الاتحاد الأوروبي؛ بمراجعة لجنة أخلاقيات الذكاء الاصطناعي.",
        findings_en="High-risk use-case confirmed. Reason-code logging and human override in place; regional fairness conditions raised. Conditional approval granted.",
        findings_ar="تأكّد كونه استخداماً عالي المخاطر. تسجيل رموز الأسباب والتجاوز البشري قائمان؛ ورُفعت شروط عدالة إقليمية. ومُنح اعتماد مشروط.",
        metrics=jdump({"human_override_rate": 0.18, "reason_code_coverage": 1.0}))

    ins_audit(cur, m["insp"], "FAIRNESS", date(2026, 1, 22), "PASSED_WITH_CONDITIONS",
        auditor_user=U_AUDITOR, party=DGA_REVIEW,
        method_en="Group-fairness testing across administrative regions (demographic parity, equal opportunity).",
        method_ar="اختبار العدالة الجماعية عبر المناطق الإدارية (التكافؤ الديموغرافي وتكافؤ الفرص).",
        findings_en="Regional demographic-parity difference 7% (target ≤ 5%). Remediation via regional re-balancing required.",
        findings_ar="فرق التكافؤ الديموغرافي الإقليمي 7% (المستهدف ≤ 5%). يلزم العلاج عبر إعادة الموازنة الإقليمية.",
        metrics=jdump({"demographic_parity_diff": 0.07, "equal_opportunity_diff": 0.06}))

    ins_audit(cur, m["elig"], "AIA", date(2026, 4, 12), "PASSED_WITH_CONDITIONS",
        auditor_user=U_COMPL, party=ETHICS_CTE,
        method_en="Fundamental-rights Algorithmic Impact Assessment (CRITICAL tier) reviewed by the AI Ethics Committee; human-in-command verified.",
        method_ar="تقييم أثر خوارزمي على الحقوق الأساسية (المستوى الحرج) بمراجعة لجنة أخلاقيات الذكاء الاصطناعي؛ مع التحقق من مبدأ الإنسان في القيادة.",
        findings_en="No automated denial of benefits. Appeal path documented. Fairness across nationality/gender pending final results — conditional pass.",
        findings_ar="لا رفض آلي للمنافع. مسار التظلّم موثّق. العدالة عبر الجنسية/الجنس بانتظار النتائج النهائية — اجتياز مشروط.",
        metrics=jdump({"automated_denial": 0, "appeal_path": 1}))

    ins_audit(cur, m["elig"], "FAIRNESS", date(2026, 4, 14), "PASSED_WITH_CONDITIONS",
        auditor_user=U_AUDITOR, party=DGA_REVIEW,
        method_en="Protected-attribute fairness testing (nationality, gender) with equal-opportunity and predictive-parity metrics.",
        method_ar="اختبار عدالة السمات المحمية (الجنسية، الجنس) بمقاييس تكافؤ الفرص والتكافؤ التنبّؤي.",
        findings_en="Disparity within target on gender; nationality cohort under continued monitoring. Conditions: bias mitigation before production.",
        findings_ar="التفاوت ضمن المستهدف على الجنس؛ وشريحة الجنسية تحت المراقبة المستمرة. الشروط: تخفيف التحيّز قبل الإنتاج.",
        metrics=jdump({"equal_opportunity_diff": 0.045, "predictive_parity_diff": 0.06}))

    ins_audit(cur, m["elig"], "EXPLAINABILITY", date(2026, 4, 16), "PASSED",
        auditor_user=U_AUDITOR, party=DGA_REVIEW,
        method_en="SHAP-based local and global explanation review; reason-code completeness for adverse flags.",
        method_ar="مراجعة التفسير المحلي والعالمي القائمة على SHAP؛ واكتمال رموز الأسباب للترشيحات السلبية.",
        findings_en="Every adverse flag carries a human-readable reason code (PDPL Art.20). Global feature attributions stable.",
        findings_ar="يحمل كل ترشيح سلبي رمز سبب مقروءاً (المادة 20). وإسهامات السمات العالمية مستقرّة.",
        metrics=jdump({"reason_code_coverage": 1.0}))

    ins_audit(cur, m["ocr"], "PRIVACY", date(2026, 2, 10), "PASSED",
        auditor_user=U_COMPL, party=PRIVACY_OFFICE,
        method_en="PDPL privacy review of the OCR pipeline: data minimisation, retention, on-prem processing, DPIA verification.",
        method_ar="مراجعة خصوصية وفق نظام حماية البيانات لخط معالجة استخراج النص: تقليل البيانات والاحتفاظ والمعالجة الداخلية والتحقق من تقييم الأثر.",
        findings_en="Data minimisation enforced; documents processed on-premises and purged per retention schedule. DPIA complete. No findings.",
        findings_ar="تطبيق تقليل البيانات؛ تُعالَج الوثائق داخلياً وتُمحى وفق جدول الاحتفاظ. اكتمل تقييم الأثر. لا ملاحظات.",
        metrics=jdump({"data_minimisation": 1, "dpia_complete": 1}))

    ins_audit(cur, m["ocr"], "SECURITY", date(2026, 2, 12), "PASSED",
        auditor_user=U_AUDITOR, party=NCA_ASSESSOR,
        method_en="NCA ECC-aligned review of the on-prem inference pipeline: access control, logging, model-artifact integrity.",
        method_ar="مراجعة مواءمة مع الضوابط الأساسية للأمن السيبراني لخط الاستدلال الداخلي: التحكم بالوصول والتسجيل وسلامة أصول النموذج.",
        findings_en="Pipeline isolated on-prem; access controlled and logged; artifact integrity verified. Compliant with ECC model controls.",
        findings_ar="خط المعالجة معزول داخلياً؛ الوصول مضبوط ومُسجَّل؛ وسلامة الأصول مُتحقَّق منها. متوافق مع ضوابط الأمن السيبراني للنماذج.",
        metrics=jdump({"access_control": 1, "logging": 1, "artifact_integrity": 1}))

    ins_audit(cur, m["asst"], "ROBUSTNESS", date(2026, 3, 18), "PASSED_WITH_CONDITIONS",
        auditor_user=U_AUDITOR, party=NCA_ASSESSOR,
        method_en="GenAI red-team: prompt-injection, jailbreak and groundedness stress-testing of the Arabic RAG assistant.",
        method_ar="اختبار فريق أحمر للذكاء التوليدي: حقن الأوامر وكسر القيود واختبار إجهاد التأريض للمساعد العربي بالاسترجاع المعزّز.",
        findings_en="Resisted most injection attempts; 6% hallucination on edge policy queries. Conditions: tighten RAG grounding and add refusal guardrails.",
        findings_ar="قاوم معظم محاولات الحقن؛ مع 6% هلوسة على استفسارات سياسات حدّية. الشروط: تشديد تأريض الاسترجاع وإضافة حواجز رفض.",
        metrics=jdump({"injection_block_rate": 0.94, "hallucination_rate": 0.06}))

    # ════════════════════════════════════════════════════════════════════════
    # 4. ml_model_drift_observation — 10 observations
    # ════════════════════════════════════════════════════════════════════════
    print("\nai_governance.ml_model_drift_observation")
    ins_drift(cur, m["insp"], dt(2026, 1, 10, 6), "DATA_DRIFT", "MINOR",
        "population_stability_index", 0.12, baseline=0.05, threshold=0.10,
        notes_en="Regional applicant-mix shift detected; PSI above warning threshold but model performance still stable.",
        notes_ar="رُصد تحوّل في مزيج المتقدّمين الإقليمي؛ مؤشر استقرار السكان فوق عتبة التحذير لكن أداء النموذج ما زال مستقراً.",
        ack=1, ack_at=dt(2026, 1, 11, 8), ack_by=U_RANALYST)

    ins_drift(cur, m["insp"], dt(2026, 1, 20, 6), "FAIRNESS_REGRESSION", "MAJOR",
        "regional_demographic_parity_diff", 0.07, baseline=0.04, threshold=0.05,
        notes_en="Regional fairness disparity creeping up to 7%; linked to retraining RTR-2026-GA-INSP-001 for regional re-balancing.",
        notes_ar="تفاوت العدالة الإقليمي يرتفع إلى 7%؛ مرتبط بإعادة التدريب RTR-2026-GA-INSP-001 لإعادة الموازنة الإقليمية.",
        ack=1, ack_at=dt(2026, 1, 21, 8), ack_by=U_RMGR)

    ins_drift(cur, m["doc"], dt(2026, 1, 5, 6), "CONCEPT_DRIFT", "MODERATE",
        "routing_accuracy", 0.89, baseline=0.94, threshold=0.91,
        notes_en="New correspondence categories introduced; routing accuracy dropped to 89%. Retraining scheduled.",
        notes_ar="استُحدثت فئات مراسلات جديدة؛ انخفضت دقة التوجيه إلى 89%. وجُدوِلت إعادة التدريب.",
        ack=1, ack_at=dt(2026, 1, 6, 8), ack_by=U_RANALYST)

    ins_drift(cur, m["ocr"], dt(2026, 2, 25, 6), "PERFORMANCE_DEGRADATION", "MINOR",
        "field_accuracy", 0.948, baseline=0.962, threshold=0.95,
        notes_en="New National-ID template reduced field accuracy slightly; addressed in v2.3.0 retraining.",
        notes_ar="قلّل قالب الهوية الوطنية الجديد دقة الحقل قليلاً؛ عُولج في إعادة تدريب الإصدار v2.3.0.",
        ack=1, ack_at=dt(2026, 2, 26, 8), ack_by=U_RANALYST)

    ins_drift(cur, m["asst"], dt(2026, 3, 12, 6), "PERFORMANCE_DEGRADATION", "MODERATE",
        "groundedness_score", 0.86, baseline=0.90, threshold=0.88,
        notes_en="Groundedness dipped below threshold on new policy topics; triggered RAG-grounding retraining.",
        notes_ar="انخفض التأريض دون العتبة على مواضيع سياسات جديدة؛ مما استدعى إعادة تدريب تأريض الاسترجاع المعزّز.",
        ack=1, ack_at=dt(2026, 3, 13, 8), ack_by=U_RMGR)

    ins_drift(cur, m["asst"], dt(2026, 3, 12, 7), "PERFORMANCE_DEGRADATION", "MODERATE",
        "hallucination_rate", 0.06, baseline=0.03, threshold=0.04,
        notes_en="Hallucination rate doubled on edge queries; mitigation via citation enforcement and refusal guardrails.",
        notes_ar="تضاعف معدل الهلوسة على الاستفسارات الحدّية؛ مع تخفيف عبر إلزام الاستشهاد وحواجز الرفض.",
        ack=1, ack_at=dt(2026, 3, 13, 9), ack_by=U_RMGR)

    ins_drift(cur, m["asst"], dt(2026, 2, 15, 6), "DATA_DRIFT", "INFO",
        "query_topic_mix_shift", 0.08, baseline=0.05, threshold=0.15,
        notes_en="Seasonal shift in beneficiary query topics; informational, no action required.",
        notes_ar="تحوّل موسمي في مواضيع استفسارات المستفيدين؛ معلوماتي ولا يتطلب إجراءً.",
        ack=0)

    ins_drift(cur, m["sla"], dt(2026, 2, 1, 6), "DATA_DRIFT", "INFO",
        "demand_seasonality_index", 0.11, baseline=0.10, threshold=0.20,
        notes_en="Expected seasonal demand pattern; within tolerance.",
        notes_ar="نمط طلب موسمي متوقّع؛ ضمن الحدود.",
        ack=0)

    ins_drift(cur, m["sla"], dt(2026, 4, 1, 6), "DATA_DRIFT", "MINOR",
        "channel_mix_shift", 0.16, baseline=0.10, threshold=0.15,
        notes_en="Digital-channel adoption shifting the channel mix; monitor for next forecast cycle.",
        notes_ar="تبنّي القنوات الرقمية يغيّر مزيج القنوات؛ يُراقَب لدورة التنبؤ التالية.",
        ack=1, ack_at=dt(2026, 4, 2, 8), ack_by=U_RANALYST)

    ins_drift(cur, m["doc"], dt(2026, 2, 20, 6), "DATA_DRIFT", "MINOR",
        "input_length_distribution", 0.13, baseline=0.07, threshold=0.15,
        notes_en="Longer incoming correspondence on new categories; monitored, no degradation yet.",
        notes_ar="مراسلات واردة أطول على الفئات الجديدة؛ مُراقَبة دون تدهور حتى الآن.",
        ack=0)

    # ════════════════════════════════════════════════════════════════════════
    # 5. retraining_event — 4 cycles
    # ════════════════════════════════════════════════════════════════════════
    print("\nai_governance.retraining_event")
    r = {}
    r["insp"] = ins_ret(cur, m["insp"], "RTR-2026-GA-INSP-001", "DEPLOYED",
        trigger="DRIFT",
        rationale_en="Fairness regression on regional demographic parity triggered re-balancing. Retrained with region-stratified sampling; disparity reduced 7% → 4.2%.",
        rationale_ar="استدعت ارتدادة العدالة على التكافؤ الديموغرافي الإقليمي إعادة الموازنة. أُعيد التدريب بأخذ عيّنات مطبّقة على المناطق؛ وانخفض التفاوت من 7% إلى 4.2%.",
        from_v="v2.0.0", to_v="v2.1.0",
        approved=dt(2026, 2, 1), started=dt(2026, 2, 5), deployed=date(2026, 2, 20),
        post_metrics=jdump({"demographic_parity_diff": 0.042, "accuracy": 0.91, "recall": 0.86}),
        owner=U_RANALYST)

    r["elig"] = ins_ret(cur, m["elig"], "RTR-2026-GA-ELIG-001", "PROPOSED",
        trigger="SCHEDULED",
        rationale_en="Annual refresh of the eligibility model pending AI Ethics Committee approval; awaits completion of the fundamental-rights AIA.",
        rationale_ar="تحديث سنوي لنموذج الأهلية بانتظار موافقة لجنة أخلاقيات الذكاء الاصطناعي؛ ومرهون بإكمال تقييم أثر الحقوق الأساسية.",
        from_v="v0.9.0", to_v="v1.0.0", owner=U_RANALYST)

    r["asst"] = ins_ret(cur, m["asst"], "RTR-2026-GA-ASST-001", "IN_PROGRESS",
        trigger="MANUAL",
        rationale_en="Manual retraining to expand Arabic dialect coverage and strengthen RAG grounding on the policy corpus; goal is hallucination < 3%.",
        rationale_ar="إعادة تدريب يدوية لتوسيع تغطية اللهجات العربية وتعزيز تأريض الاسترجاع المعزّز على مجموعة السياسات؛ بهدف هلوسة أقل من 3%.",
        from_v="v1.2.0", to_v="v1.3.0",
        approved=dt(2026, 3, 25), started=dt(2026, 4, 2), owner=U_RANALYST)

    r["ocr"] = ins_ret(cur, m["ocr"], "RTR-2026-GA-OCR-001", "DEPLOYED",
        trigger="SCHEDULED",
        rationale_en="Scheduled retraining to add the new National-ID template; field accuracy restored 94.8% → 96.2%.",
        rationale_ar="إعادة تدريب مجدولة لإضافة قالب الهوية الوطنية الجديد؛ واستُعيدت دقة الحقل من 94.8% إلى 96.2%.",
        from_v="v2.0.0", to_v="v2.3.0",
        approved=dt(2026, 2, 20), started=dt(2026, 2, 25), deployed=date(2026, 3, 5),
        post_metrics=jdump({"field_accuracy": 0.962, "character_accuracy": 0.987}),
        owner=U_RANALYST)

    # ════════════════════════════════════════════════════════════════════════
    # 6. recertification — 5 recerts
    # ════════════════════════════════════════════════════════════════════════
    print("\nai_governance.recertification")
    ins_rec(cur, m["insp"], "REC-2026-GA-INSP", "RECERTIFIED_WITH_CONDITIONS",
        cycle="Annual 2026", due=date(2026, 2, 15), started=dt(2026, 2, 16),
        decided=dt(2026, 2, 28), next_due=date(2027, 2, 28),
        decision_en="Recertified by the Data Governance Committee subject to completing the fairness retraining (now deployed) and AI Ethics Committee oversight.",
        decision_ar="أُعيد الاعتماد من لجنة حوكمة البيانات مشروطاً بإكمال إعادة تدريب العدالة (التي نُشرت) وإشراف لجنة أخلاقيات الذكاء الاصطناعي.",
        conditions_en="Complete regional fairness re-balancing; maintain PDPL Art.20 reason codes.",
        conditions_ar="إكمال إعادة موازنة العدالة الإقليمية؛ والمحافظة على رموز أسباب المادة 20.",
        approver=U_RMGR, linked_validation=v["insp"])

    ins_rec(cur, m["elig"], "REC-2026-GA-ELIG", "IN_REVIEW",
        cycle="Annual 2026", due=date(2026, 5, 31), started=dt(2026, 6, 5),
        decision_en="CRITICAL fundamental-rights re-review by the AI Ethics Committee in progress; recertification overdue pending AIA completion.",
        decision_ar="إعادة مراجعة الحقوق الأساسية (مستوى حرج) من لجنة أخلاقيات الذكاء الاصطناعي قيد التنفيذ؛ وإعادة الاعتماد متأخرة بانتظار إكمال تقييم الأثر.",
        conditions_en="No production deployment until fundamental-rights AIA passes and human-in-command + appeal path are confirmed.",
        conditions_ar="لا نشر إنتاجي حتى يجتاز تقييم أثر الحقوق الأساسية وتأكيد مبدأ الإنسان في القيادة ومسار التظلّم.",
        approver=U_COMPL, linked_validation=v["elig"])

    ins_rec(cur, m["ocr"], "REC-2026-GA-OCR", "RECERTIFIED",
        cycle="Annual 2026", due=date(2026, 3, 1), started=dt(2026, 3, 2),
        decided=dt(2026, 3, 15), next_due=date(2027, 3, 15),
        decision_en="Recertified following successful PDPL privacy and NCA ECC security audits and the National-ID template retraining.",
        decision_ar="أُعيد الاعتماد بعد نجاح مراجعتي خصوصية حماية البيانات وأمن الضوابط السيبرانية الأساسية وإعادة تدريب قالب الهوية الوطنية.",
        approver=U_RMGR, linked_validation=v["ocr"])

    ins_rec(cur, m["asst"], "REC-2026-GA-ASST", "DUE",
        cycle="Annual 2026", due=date(2026, 9, 20),
        decision_en="GenAI annual review due; will assess groundedness, hallucination rate and cross-border governance (PDPL Art.29) after current retraining.",
        decision_ar="مراجعة سنوية للذكاء التوليدي مستحقّة؛ ستقيّم التأريض ومعدل الهلوسة وحوكمة النقل عبر الحدود (المادة 29) بعد إعادة التدريب الحالية.",
        approver=U_COMPL, linked_validation=v["asst"])

    ins_rec(cur, m["sla"], "REC-2026-GA-SLA", "DUE",
        cycle="Annual 2026", due=date(2026, 7, 1),
        decision_en="Upcoming routine recertification for the low-risk forecasting model.",
        decision_ar="إعادة اعتماد روتينية قادمة لنموذج التنبؤ منخفض المخاطر.",
        approver=U_RMGR, linked_validation=v["sla"])

    # ════════════════════════════════════════════════════════════════════════
    # 7. ai_lifecycle_event — 12 timeline events
    # ════════════════════════════════════════════════════════════════════════
    print("\nai_governance.ai_lifecycle_event")
    ins_event(cur, m["insp"], dt(2025, 3, 15, 10), "MODEL_REGISTERED",
        actor_user=U_RANALYST, actor_name="إدارة البيانات والذكاء الاصطناعي — Data & AI",
        status_after="IN_PRODUCTION",
        note_en="Inspection Risk-Prioritisation Engine registered and promoted to production.",
        note_ar="تسجيل محرك تحديد أولويات التفتيش وترقيته إلى الإنتاج.")

    ins_event(cur, m["insp"], dt(2026, 1, 28, 12), "VALIDATION_COMPLETED",
        validation_id=v["insp"], actor_user=U_AUDITOR, actor_name=DGA_REVIEW,
        status_after="IN_PRODUCTION",
        note_en="Independent validation completed: PASSED_WITH_CONDITIONS (regional fairness ~7%).",
        note_ar="اكتمال التحقق المستقل: اجتياز مشروط (عدالة إقليمية نحو 7%).")

    ins_event(cur, m["insp"], dt(2026, 1, 20, 9), "DRIFT_DETECTED",
        actor_user=U_RMGR, actor_name="مراقبة ما بعد النشر — Post-market monitoring",
        status_after="MONITORING",
        note_en="Fairness regression detected on regional demographic parity (MAJOR).",
        note_ar="رصد ارتدادة عدالة على التكافؤ الديموغرافي الإقليمي (كبيرة).")

    ins_event(cur, m["insp"], dt(2026, 2, 20, 11), "RETRAINING_DEPLOYED",
        retraining_id=r["insp"], actor_user=U_RANALYST,
        status_after="IN_PRODUCTION",
        note_en="Retraining v2.1.0 deployed; regional disparity reduced to 4.2%.",
        note_ar="نُشرت إعادة التدريب v2.1.0؛ وانخفض التفاوت الإقليمي إلى 4.2%.")

    ins_event(cur, m["insp"], dt(2026, 2, 28, 14), "RECERTIFIED",
        recertification_id=None, actor_user=U_RMGR, actor_name=DATAGOV_CTE,
        status_after="IN_PRODUCTION",
        note_en="Recertified with conditions by the Data Governance Committee.",
        note_ar="أُعيد الاعتماد بشروط من لجنة حوكمة البيانات.")

    ins_event(cur, m["elig"], dt(2026, 4, 10, 9), "ETHICS_REVIEW",
        actor_user=U_COMPL, actor_name=ETHICS_CTE,
        status_after="VALIDATION",
        note_en="Fundamental-rights review of the eligibility model opened by the AI Ethics Committee (CRITICAL).",
        note_ar="فتح لجنة أخلاقيات الذكاء الاصطناعي مراجعة الحقوق الأساسية لنموذج الأهلية (مستوى حرج).")

    ins_event(cur, m["elig"], dt(2026, 4, 12, 13), "AUDIT_COMPLETED",
        actor_user=U_COMPL, actor_name=ETHICS_CTE,
        status_after="VALIDATION",
        note_en="Algorithmic Impact Assessment (AIA) completed: PASSED_WITH_CONDITIONS — no automated denials, appeal path confirmed.",
        note_ar="اكتمال تقييم الأثر الخوارزمي: اجتياز مشروط — لا رفض آلي، وتأكّد مسار التظلّم.")

    ins_event(cur, m["elig"], dt(2026, 6, 5, 9), "RECERTIFICATION_DUE",
        actor_user=U_COMPL, actor_name=ETHICS_CTE,
        status_after="VALIDATION",
        note_en="Recertification overdue — CRITICAL model held in validation pending AI Ethics Committee sign-off.",
        note_ar="إعادة الاعتماد متأخرة — يُبقى النموذج الحرج في مرحلة التحقق بانتظار اعتماد لجنة أخلاقيات الذكاء الاصطناعي.")

    ins_event(cur, m["ocr"], dt(2024, 11, 5, 10), "MODEL_REGISTERED",
        actor_user=U_RANALYST, status_after="IN_PRODUCTION",
        note_en="Licensing Attachment OCR registered and promoted to production.",
        note_ar="تسجيل استخراج نص مرفقات التراخيص وترقيته إلى الإنتاج.")

    ins_event(cur, m["ocr"], dt(2026, 3, 15, 14), "RECERTIFIED",
        actor_user=U_RMGR, actor_name=DATAGOV_CTE, status_after="IN_PRODUCTION",
        note_en="Recertified after PDPL privacy and NCA ECC security audits passed.",
        note_ar="أُعيد الاعتماد بعد اجتياز مراجعتي خصوصية حماية البيانات وأمن الضوابط السيبرانية الأساسية.")

    ins_event(cur, m["doc"], dt(2025, 1, 20, 10), "PROMOTED_TO_PRODUCTION",
        actor_user=U_RANALYST, status_after="IN_PRODUCTION",
        note_en="Correspondence Auto-Routing promoted to production (on-prem, Arabic NLP).",
        note_ar="ترقية التوجيه الآلي للمراسلات إلى الإنتاج (داخلي، معالجة لغة عربية).")

    ins_event(cur, m["asst"], dt(2026, 3, 20, 12), "ETHICS_REVIEW",
        validation_id=v["asst"], actor_user=U_COMPL, actor_name=ETHICS_CTE,
        status_after="IN_PRODUCTION",
        note_en="GenAI ethics review: groundedness controls and human handoff mandated for benefit actions.",
        note_ar="مراجعة أخلاقيات الذكاء التوليدي: إلزام ضوابط التأريض والتحويل البشري لإجراءات المنافع.")

    # ════════════════════════════════════════════════════════════════════════
    # 8. ai.ai_suggestions — 4 authority-flavoured AI-assist suggestions
    # ════════════════════════════════════════════════════════════════════════
    print("\nai.ai_suggestions")
    ins_suggestion(cur, "BOARD_SUMMARY", "AI_GOVERNANCE_POSTURE", m["insp"], "DRAFT",
        confidence=82,
        input=jdump({"scope": "authority_ai_governance", "period": "2026-H1",
                     "models": 6, "high_risk": 3}),
        output=jdump({
            "summary_en": "The Authority operates 6 AI models; 3 are EU-AI-Act high-risk (inspection prioritisation, beneficiary eligibility, licensing OCR). SDAIA AI-ethics alignment is COMPLIANT for 3 and PARTIAL for 2; one CRITICAL model (eligibility) is held in validation pending an AI Ethics Committee fundamental-rights review.",
            "summary_ar": "تشغّل الهيئة 6 نماذج ذكاء اصطناعي؛ منها 3 عالية المخاطر وفق نظام الاتحاد الأوروبي (أولويات التفتيش، أهلية المستفيدين، استخراج نص التراخيص). مواءمة أخلاقيات سدايا: ممتثلة لثلاثة وجزئية لاثنين؛ ويُبقى نموذج حرج واحد (الأهلية) في التحقق بانتظار مراجعة الحقوق الأساسية من لجنة أخلاقيات الذكاء الاصطناعي."}),
        rationale="Draft board/committee summary of the authority AI-governance posture for the Data Governance and AI Ethics Committees.")

    ins_suggestion(cur, "RISK_DESCRIPTION", "AI_MODEL_RISK", m["elig"], "ACCEPTED",
        confidence=88,
        input=jdump({"model_code": "GA-ELIG-002", "concern": "fairness", "tier": "CRITICAL"}),
        output=jdump({
            "risk_en": "The Beneficiary Eligibility Risk Scoring model may produce disparate adverse-flag rates across nationality and gender cohorts, creating fundamental-rights and PDPL Art.20 exposure if used without human-in-command review and an appeal path.",
            "risk_ar": "قد ينتج نموذج تقييم مخاطر أهلية المستفيدين معدلات ترشيح سلبي متفاوتة عبر شرائح الجنسية والجنس، مما يُنشئ تعرّضاً للحقوق الأساسية والمادة 20 إذا استُخدم دون مراجعة بمبدأ الإنسان في القيادة ومسار تظلّم."}),
        rationale="Generated AI model-risk register entry for the eligibility model fairness concern.",
        reviewed_by=U_RMGR, reviewed_at=dt(2026, 4, 15, 10),
        reviewer_note="Accepted into the AI model-risk register; tied to CRITICAL tier and AI Ethics Committee review.")

    ins_suggestion(cur, "EVIDENCE_QUALITY", "SDAIA_ETHICS_ATTESTATION", m["ocr"], "DRAFT",
        confidence=76,
        input=jdump({"control": "SDAIA_AI_ETHICS", "model_code": "GA-OCR-004",
                     "evidence_type": "attestation"}),
        output=jdump({
            "assessment_en": "The SDAIA AI-ethics attestation evidence for the licensing OCR model is adequate: DPIA, on-prem security audit and privacy review are attached. Recommend adding a signed AI Ethics Committee minute to strengthen the accountability principle.",
            "assessment_ar": "أدلة إقرار أخلاقيات سدايا لنموذج استخراج نص التراخيص كافية: تقييم أثر حماية البيانات ومراجعة الأمن الداخلي ومراجعة الخصوصية مُرفقة. ويوصى بإضافة محضر موقّع من لجنة أخلاقيات الذكاء الاصطناعي لتعزيز مبدأ المساءلة."}),
        rationale="Evidence-quality assessment of the SDAIA AI-ethics attestation pack.")

    ins_suggestion(cur, "INCIDENT_ROOT_CAUSE", "AI_HALLUCINATION_EVENT", m["asst"], "DRAFT",
        confidence=79,
        input=jdump({"model_code": "GA-ASST-005", "event": "hallucination",
                     "metric": "hallucination_rate", "value": 0.06}),
        output=jdump({
            "root_cause_en": "Hallucination on edge policy queries rose to 6% because new policy topics were not yet covered by the RAG corpus, so the assistant generated answers without grounded citations. Mitigation: expand the policy corpus, enforce citation, and add refusal guardrails (now in retraining RTR-2026-GA-ASST-001).",
            "root_cause_ar": "ارتفعت الهلوسة على استفسارات السياسات الحدّية إلى 6% لأن مواضيع سياسات جديدة لم تكن مغطّاة في مجموعة الاسترجاع المعزّز، فولّد المساعد إجابات دون استشهادات مؤرَّضة. التخفيف: توسيع مجموعة السياسات وإلزام الاستشهاد وإضافة حواجز رفض (ضمن إعادة التدريب RTR-2026-GA-ASST-001)."}),
        rationale="Root-cause analysis of the GenAI assistant hallucination drift event.")

    # ════════════════════════════════════════════════════════════════════════
    # Summary + verification
    # ════════════════════════════════════════════════════════════════════════
    print("\n" + "=" * 70)
    print("INSERTED THIS RUN:")
    total = 0
    for tbl in ["ml_model", "model_validation", "ml_model_audit",
                "ml_model_drift_observation", "retraining_event", "recertification",
                "ai_lifecycle_event", "ai_suggestions"]:
        n = counts.get(tbl, 0)
        total += n
        print(f"  {tbl:<32} +{n}")
    print(f"  {'TOTAL':<32} +{total}")

    print("\nVERIFICATION — GA-* / authority rows present for org 1:")
    cur.execute("SELECT COUNT(*) FROM ai_governance.ml_model WHERE organisation_id=1 AND model_code LIKE 'GA-%'")
    print(f"  ml_model (GA-*):                {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ai_governance.model_validation WHERE organisation_id=1 AND reference LIKE 'VAL-2026-GA-%'")
    print(f"  model_validation (VAL-*-GA):    {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ai_governance.ml_model_audit a JOIN ai_governance.ml_model mm ON a.model_id=mm.id WHERE mm.model_code LIKE 'GA-%'")
    print(f"  ml_model_audit (GA models):     {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ai_governance.ml_model_drift_observation d JOIN ai_governance.ml_model mm ON d.model_id=mm.id WHERE mm.model_code LIKE 'GA-%'")
    print(f"  drift_observation (GA models):  {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ai_governance.retraining_event WHERE organisation_id=1 AND reference LIKE 'RTR-2026-GA-%'")
    print(f"  retraining_event (RTR-*-GA):    {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ai_governance.recertification WHERE organisation_id=1 AND reference LIKE 'REC-2026-GA-%'")
    print(f"  recertification (REC-*-GA):     {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ai_governance.ai_lifecycle_event e JOIN ai_governance.ml_model mm ON e.model_id=mm.id WHERE mm.model_code LIKE 'GA-%'")
    print(f"  ai_lifecycle_event (GA models): {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ai.ai_suggestions WHERE organisation_id=1 AND related_entity IN ('AI_GOVERNANCE_POSTURE','AI_MODEL_RISK','SDAIA_ETHICS_ATTESTATION','AI_HALLUCINATION_EVENT')")
    print(f"  ai_suggestions (authority):     {cur.fetchone()[0]}")

    conn.close()
    print("\nDone.")


if __name__ == "__main__":
    main()
