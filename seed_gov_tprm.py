# -*- coding: utf-8 -*-
"""Seed Saudi-government third-party / supply-chain / outsourcing demo data.

Domain: "tprm" — schemas tprm, scm, outsourcing. Default tenant only
(organisation_id = 1), which is modelled as a SAUDI GENERAL AUTHORITY
(هيئة عامة) procuring through Etimad (اعتماد) and the Government Tenders &
Procurement Law (GTPL / نظام المنافسات والمشتريات الحكومية).

All 29 tables in these schemas are already populated with a COMMERCIAL-BANK
data set from earlier sprints. This script does NOT touch or re-theme those
rows. It *adds* a parallel GOVERNMENT supplier layer — an Etimad-procured
panel of public-sector suppliers (GovCloud "Deem", NCA-licensed managed SOC,
Elm, Thiqah, facilities management, guarding, cleaning, system integration,
training, fleet, logistics) owned by the Procurement Department — plus the
assessments, contracts, SLAs, dependencies, exit/resilience records,
outsourcing arrangements, due-diligence, questionnaires and BCP attestations
that hang off them.

Regulatory anchors woven in: NCA ECC domain 4 (third-party/cloud cyber) + CCC
(cloud controls); NDMO data-sharing, national data classification & in-Kingdom
residency; PDPL data-processing agreements for processors; NCEMA / ISO 22301
supplier continuity; Nazaha integrity & conflict-of-interest in procurement;
DGA digital-government; GTPL / Etimad; Vision 2030 local content (المحتوى المحلي).

Idempotency: every INSERT is guarded by a natural key (a *_code / reference, or
organisation_id + a stable name). A second run inserts 0. INSERT-ONLY — no
DELETE / TRUNCATE / DROP / ALTER, and no modification of pre-existing rows.

    Usage:  python seed_gov_tprm.py
"""
from __future__ import annotations

import json
import sys

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
# iam.users (org 1): admin=1, rmanager=2, compliance=3, auditor=4, ranalyst=5, viewer=6
U_ADMIN, U_RMGR, U_COMP, U_AUDIT, U_RANALYST, U_VIEWER = 1, 2, 3, 4, 5, 6
PROC_OWNER = "Procurement & Supply Chain Department / إدارة المشتريات وسلسلة الإمداد"

# Running counters: {table -> [inserted, skipped]}
STATS: dict[str, list[int]] = {}
BLOCKERS: list[str] = []


def bump(table: str, inserted: bool) -> None:
    s = STATS.setdefault(table, [0, 0])
    s[0 if inserted else 1] += 1


def j(obj) -> str:
    return json.dumps(obj, ensure_ascii=False)


# ─────────────────────────────────────────────────────────────────────────────
# The Etimad-procured government supplier panel.
# Shared by scm.vendors and tprm.vendors so both registries reference the same
# real-world parties. Codes are stable natural keys used for idempotency.
# scm.vendors CHECK: category IT_SERVICES|CONSULTING|MANUFACTURING|LOGISTICS|
#                    UTILITIES|FINANCIAL|OTHER ; criticality/risk_rating
#                    LOW|MEDIUM|HIGH|CRITICAL ; risk_level LOW|MEDIUM|HIGH (no CRITICAL).
# ─────────────────────────────────────────────────────────────────────────────
GOV_SUPPLIERS = [
    dict(key="GOVCLOUD",
         name_en="Deem National Cloud (GovCloud)",
         name_ar="ديم - السحابة الحكومية الوطنية",
         scm_cat="IT_SERVICES", criticality="CRITICAL", risk_level="HIGH",
         tprm_risk="CRITICAL",
         service_type="Sovereign government cloud hosting (IaaS/PaaS), in-Kingdom",
         vtype_ar="سحابة حكومية سيادية",
         cr="CR-1010101010",
         contact="Government Cloud Onboarding Desk", email="onboarding@deem.gov.sa"),
    dict(key="MSSP",
         name_en="National Managed Security Services (NCA-licensed MSSP)",
         name_ar="الخدمات الأمنية المُدارة الوطنية (مزود مرخّص من الهيئة الوطنية للأمن السيبراني)",
         scm_cat="IT_SERVICES", criticality="CRITICAL", risk_level="HIGH",
         tprm_risk="CRITICAL",
         service_type="Managed SOC / 24x7 monitoring, threat detection & response",
         vtype_ar="مزود خدمات أمنية مُدارة",
         cr="CR-2020202020",
         contact="MSSP Service Delivery Manager", email="soc@mssp.sa"),
    dict(key="ELM",
         name_en="Elm (Digital Government Platforms)",
         name_ar="شركة علم لخدمات الحكومة الرقمية",
         scm_cat="IT_SERVICES", criticality="HIGH", risk_level="HIGH",
         tprm_risk="HIGH",
         service_type="E-government platforms, identity & verification (Nafath/Yakeen integration)",
         vtype_ar="منصات حكومية رقمية وتحقق هوية",
         cr="CR-3030303030",
         contact="Elm Account Manager", email="gov@elm.sa"),
    dict(key="THIQAH",
         name_en="Thiqah Business Services",
         name_ar="ثقة لخدمات الأعمال",
         scm_cat="IT_SERVICES", criticality="HIGH", risk_level="MEDIUM",
         tprm_risk="HIGH",
         service_type="Government digital business services & integration",
         vtype_ar="خدمات أعمال حكومية رقمية",
         cr="CR-4040404040",
         contact="Thiqah Service Desk", email="services@thiqah.sa"),
    dict(key="FM",
         name_en="Almajal Facilities Management",
         name_ar="المجال لإدارة المرافق",
         scm_cat="OTHER", criticality="HIGH", risk_level="HIGH",
         tprm_risk="HIGH",
         service_type="Integrated facilities management for HQ + data hall (HVAC, power, MEP)",
         vtype_ar="إدارة مرافق متكاملة",
         cr="CR-5050505050",
         contact="FM Contract Manager", email="contracts@almajal-fm.sa"),
    dict(key="GUARD",
         name_en="Sahil Security & Guarding Services",
         name_ar="ساحل لخدمات الأمن والحراسات",
         scm_cat="OTHER", criticality="MEDIUM", risk_level="MEDIUM",
         tprm_risk="MEDIUM",
         service_type="Manned guarding, access control & patrol services",
         vtype_ar="خدمات حراسات أمنية",
         cr="CR-6060606060",
         contact="Guarding Operations Lead", email="ops@sahil-security.sa"),
    dict(key="CLEAN",
         name_en="Naqaa Cleaning & Hospitality Services",
         name_ar="نقاء لخدمات النظافة والضيافة",
         scm_cat="OTHER", criticality="LOW", risk_level="LOW",
         tprm_risk="LOW",
         service_type="Cleaning, hospitality & janitorial services",
         vtype_ar="خدمات نظافة وضيافة",
         cr="CR-7070707070",
         contact="Cleaning Site Supervisor", email="site@naqaa.sa"),
    dict(key="SI",
         name_en="Takamol National Systems Integrator",
         name_ar="تكامل لتكامل الأنظمة الوطنية",
         scm_cat="IT_SERVICES", criticality="HIGH", risk_level="HIGH",
         tprm_risk="HIGH",
         service_type="System integration of licensing & beneficiary platforms",
         vtype_ar="تكامل الأنظمة",
         cr="CR-8080808080",
         contact="SI Delivery Director", email="delivery@takamol-si.sa"),
    dict(key="TRAIN",
         name_en="Doroob Learning & Development",
         name_ar="دروب للتدريب والتطوير",
         scm_cat="CONSULTING", criticality="LOW", risk_level="LOW",
         tprm_risk="LOW",
         service_type="Employee learning & development, HRDF-linked training",
         vtype_ar="تدريب وتطوير",
         cr="CR-9090909090",
         contact="Training Programme Lead", email="programs@doroob-ld.sa"),
    dict(key="FLEET",
         name_en="Wusool Fleet & Transport Services",
         name_ar="وصول لخدمات النقل والأسطول",
         scm_cat="LOGISTICS", criticality="MEDIUM", risk_level="MEDIUM",
         tprm_risk="MEDIUM",
         service_type="Fleet leasing, driver services & official transport",
         vtype_ar="نقل وأسطول",
         cr="CR-1112131415",
         contact="Fleet Coordinator", email="fleet@wusool.sa"),
    dict(key="LOGI",
         name_en="Saudi Post | SPL (Logistics & Correspondence)",
         name_ar="البريد السعودي | سُبل (لوجستيات ومراسلات)",
         scm_cat="LOGISTICS", criticality="MEDIUM", risk_level="MEDIUM",
         tprm_risk="MEDIUM",
         service_type="Official correspondence, secure document logistics & national address",
         vtype_ar="لوجستيات ومراسلات حكومية",
         cr="CR-1617181920",
         contact="SPL Government Liaison", email="gov@spl.sa"),
    dict(key="CONTACT",
         name_en="Markaz Beneficiary Contact Centre Services",
         name_ar="مركز لخدمات مركز اتصال المستفيدين",
         scm_cat="OTHER", criticality="HIGH", risk_level="HIGH",
         tprm_risk="HIGH",
         service_type="Beneficiary contact / call centre (omnichannel) outsourcing",
         vtype_ar="مركز اتصال المستفيدين",
         cr="CR-2122232425",
         contact="Contact Centre Operations Manager", email="ops@markaz-cc.sa"),
    dict(key="DIGI",
         name_en="Mahfouthat Records Digitisation & Archiving",
         name_ar="محفوظات لرقمنة وأرشفة الوثائق",
         scm_cat="OTHER", criticality="MEDIUM", risk_level="MEDIUM",
         tprm_risk="MEDIUM",
         service_type="Records digitisation & secure archiving (personal data)",
         vtype_ar="رقمنة وأرشفة وثائق",
         cr="CR-2627282930",
         contact="Digitisation Project Lead", email="projects@mahfouthat.sa"),
]
GOV_BY_KEY = {g["key"]: g for g in GOV_SUPPLIERS}


# ─────────────────────────────────────────────────────────────────────────────
# helpers for idempotent existence checks
# ─────────────────────────────────────────────────────────────────────────────
def exists(cur, sql: str, *params) -> int | None:
    cur.execute(sql, *params)
    r = cur.fetchone()
    return r[0] if r else None


# =============================================================================
#  scm.vendors  — government supplier panel (guard: org + name_en)
# =============================================================================
def seed_scm_vendors(cur) -> dict[str, int]:
    """Returns {key -> scm vendor id} for downstream FKs."""
    ids: dict[str, int] = {}
    for g in GOV_SUPPLIERS:
        vid = exists(cur,
            "SELECT id FROM scm.vendors WHERE organisation_id=? AND name_en=?",
            ORG, g["name_en"])
        if vid is None:
            cur.execute(
                """INSERT INTO scm.vendors
                     (organisation_id, name_en, name_ar, category, criticality,
                      country, service_type, risk_level, status, contact_name,
                      contact_email, registration_number, onboarding_status,
                      description, created_by, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?,?,?,'COMPLETED',?,?,0)""",
                ORG, g["name_en"], g["name_ar"], g["scm_cat"], g["criticality"],
                "Saudi Arabia", g["service_type"], g["risk_level"],
                g["contact"], g["email"], g["cr"],
                f"Etimad-procured government supplier ({g['vtype_ar']}). Owned by {PROC_OWNER}.",
                U_RMGR)
            vid = exists(cur,
                "SELECT id FROM scm.vendors WHERE organisation_id=? AND name_en=?",
                ORG, g["name_en"])
            bump("scm.vendors", True)
        else:
            bump("scm.vendors", False)
        ids[g["key"]] = vid
    return ids


# =============================================================================
#  scm.vendor_assessments  (guard: org + vendor_id + assessment_date + type)
# =============================================================================
def seed_scm_assessments(cur, sv: dict[str, int]) -> None:
    rows = [
        ("GOVCLOUD", "PERIODIC", "2026-02-10", 88.0, "LOW", "COMPLETED",
         "Annual supplier review. NCA ECC-4 & CCC evidence current; ISO 27001/SOC2 valid; PDPL DPA signed; NCEMA continuity attestation on file. Local content ~78%.",
         "مراجعة سنوية للمورد. أدلة الضوابط الأساسية المجال الرابع والضوابط السحابية محدثة؛ شهادات أيزو سارية؛ اتفاقية معالجة بيانات موقعة؛ إقرار الاستمرارية وفق المركز الوطني. المحتوى المحلي ~78%.",
         U_RANALYST),
        ("MSSP", "PERIODIC", "2026-02-15", 84.0, "MEDIUM", "COMPLETED",
         "Managed-SOC review. MTTD/MTTR within SLA; NCA MSSP licence valid; threat-intel sub-processor flow-down confirmed. Minor finding on analyst rotation evidence.",
         "مراجعة مركز العمليات الأمنية المُدار. زمن الاكتشاف والاستجابة ضمن اتفاقية الخدمة؛ ترخيص الهيئة ساري؛ تأكيد بنود التعاقد من الباطن لمزود التهديدات. ملاحظة بسيطة على أدلة تناوب المحللين.",
         U_RANALYST),
        ("SI", "PERIODIC", "2026-03-01", 71.0, "HIGH", "COMPLETED",
         "System-integrator review. Delivery capacity strong; however open critical vulnerabilities in delivered components and patch-SLA gaps. Treatment plan agreed.",
         "مراجعة مكامل الأنظمة. قدرة التنفيذ جيدة؛ إلا أن هناك ثغرات حرجة مفتوحة في المكونات المسلّمة وفجوات في اتفاقية الترقيع. تم الاتفاق على خطة معالجة.",
         U_RANALYST),
        ("FM", "PERIODIC", "2026-03-05", 76.0, "MEDIUM", "COMPLETED",
         "Facilities-management review of HQ + data hall. Preventive maintenance on schedule; continuity gap on chiller redundancy flagged for CAPA.",
         "مراجعة إدارة المرافق للمقر وقاعة البيانات. الصيانة الوقائية وفق الجدول؛ رُصدت فجوة استمرارية في تكرار وحدات التبريد لإجراء تصحيحي.",
         U_RANALYST),
        ("GUARD", "TRIGGERED", "2026-03-12", 58.0, "HIGH", "ESCALATED",
         "Triggered review after patrol-coverage SLA misses and a conflict-of-interest (Nazaha) declaration discrepancy. Escalated to Risk Management Committee.",
         "مراجعة مُحفّزة بعد إخفاقات في تغطية الدوريات وتعارض في إقرار تعارض المصالح (نزاهة). تم التصعيد للجنة إدارة المخاطر.",
         U_RMGR),
        ("CONTACT", "INITIAL", "2026-01-20", 82.0, "MEDIUM", "COMPLETED",
         "Initial onboarding assessment of beneficiary contact centre. PDPL recording-consent controls reviewed; call-recording residency in-Kingdom confirmed.",
         "تقييم تأهيل أولي لمركز اتصال المستفيدين. مراجعة ضوابط موافقة التسجيل وفق النظام؛ تأكيد بقاء تسجيلات المكالمات داخل المملكة.",
         U_RANALYST),
        ("DIGI", "INITIAL", "2026-02-01", 79.0, "MEDIUM", "COMPLETED",
         "Initial assessment of records-digitisation supplier. PDPL processor obligations and NDMO data-return clauses verified; secure-destruction certificate required.",
         "تقييم أولي لمورد رقمنة الوثائق. التحقق من التزامات المعالج وفق النظام وبنود إعادة البيانات وفق مكتب إدارة البيانات؛ يلزم شهادة إتلاف آمن.",
         U_RANALYST),
        ("ELM", "PERIODIC", "2026-02-20", 90.0, "LOW", "COMPLETED",
         "Identity/verification platform review. Nafath/Yakeen integration secure; high availability evidenced; strong NCA posture.",
         "مراجعة منصة الهوية والتحقق. تكامل نفاذ ويقين آمن؛ توفر عالٍ موثّق؛ وضع سيبراني قوي وفق الهيئة.",
         U_RANALYST),
    ]
    for key, atype, adate, score, rating, status, fen, far, assessor in rows:
        vid = sv[key]
        eid = exists(cur,
            "SELECT id FROM scm.vendor_assessments WHERE organisation_id=? AND vendor_id=? "
            "AND assessment_date=? AND assessment_type=?",
            ORG, vid, adate, atype)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.vendor_assessments
                     (organisation_id, vendor_id, assessment_type, assessment_date,
                      next_assessment_date, overall_score, risk_rating, status,
                      assessor_id, findings, recommendations, escalated_to_risk,
                      created_by, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, vid, atype, adate, "2027-02-28", score, rating, status,
                assessor, far + " | " + fen,
                "Maintain NCA ECC-4 evidence; renew DPA and continuity attestations annually.",
                1 if status == "ESCALATED" else 0, U_RMGR)
            bump("scm.vendor_assessments", True)
        else:
            bump("scm.vendor_assessments", False)


# =============================================================================
#  scm.vendor_onboarding  (guard: org + vendor_id)
# =============================================================================
def seed_scm_onboarding(cur, sv: dict[str, int]) -> None:
    rows = [
        ("GOVCLOUD", "SECURITY_REVIEW", "IN_PROGRESS", U_RANALYST, "2026-07-15",
         "Etimad award ETD-2026-001. At NCA CCC security-review gate; cloud cybersecurity controls evidence under verification.",
         ["REGISTRATION", "CRITICALITY_CLASSIFICATION"]),
        ("GUARD", "LEGAL_REVIEW", "ON_HOLD", U_COMP, "2026-07-20",
         "Etimad award ETD-2026-006. On hold at legal review pending DPA and Nazaha conflict-of-interest declaration.",
         ["REGISTRATION", "CRITICALITY_CLASSIFICATION", "SECURITY_REVIEW", "OPERATIONAL_REVIEW"]),
        ("SI", "APPROVAL", "COMPLETED", U_RMGR, "2026-05-30",
         "Etimad award ETD-2026-005. Onboarding completed and approved per GTPL.",
         ["REGISTRATION", "CRITICALITY_CLASSIFICATION", "SECURITY_REVIEW", "OPERATIONAL_REVIEW", "LEGAL_REVIEW"]),
        ("CONTACT", "OPERATIONAL_REVIEW", "IN_PROGRESS", U_RANALYST, "2026-08-01",
         "Etimad award ETD-2026-009. Operational review of staffing & omnichannel readiness.",
         ["REGISTRATION", "CRITICALITY_CLASSIFICATION", "SECURITY_REVIEW"]),
        ("DIGI", "LEGAL_REVIEW", "IN_PROGRESS", U_COMP, "2026-08-10",
         "Etimad award ETD-2026-010. Legal review of PDPL processor clauses & NDMO data-return.",
         ["REGISTRATION", "CRITICALITY_CLASSIFICATION", "SECURITY_REVIEW", "OPERATIONAL_REVIEW"]),
        ("MSSP", "APPROVAL", "COMPLETED", U_RMGR, "2026-04-15",
         "Etimad award ETD-2026-002. Managed-SOC onboarding approved; service live.",
         ["REGISTRATION", "CRITICALITY_CLASSIFICATION", "SECURITY_REVIEW", "OPERATIONAL_REVIEW", "LEGAL_REVIEW"]),
    ]
    for key, stage, status, assignee, due, notes, done in rows:
        vid = sv[key]
        eid = exists(cur,
            "SELECT id FROM scm.vendor_onboarding WHERE organisation_id=? AND vendor_id=?",
            ORG, vid)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.vendor_onboarding
                     (organisation_id, vendor_id, current_stage, status,
                      assigned_to_id, due_date, notes, completed_stages,
                      created_by, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,0)""",
                ORG, vid, stage, status, assignee, due, notes, j(done), U_RMGR)
            bump("scm.vendor_onboarding", True)
        else:
            bump("scm.vendor_onboarding", False)


# =============================================================================
#  scm.contracts  (guard: org + contract_number)
# =============================================================================
def seed_scm_contracts(cur, sv: dict[str, int]) -> dict[str, int]:
    ids: dict[str, int] = {}
    rows = [
        ("GOVCLOUD", "ETD-2026-001", "GovCloud (Deem) sovereign hosting subscription",
         "اشتراك استضافة السحابة الحكومية السيادية (ديم)", "SERVICE", "2026-04-01", "2029-03-31",
         1, 9500000, "ACTIVE", "3-year Etimad framework; in-Kingdom hosting of beneficiary e-services."),
        ("MSSP", "ETD-2026-002", "Managed SOC / security monitoring service",
         "خدمة مركز العمليات الأمنية المُدار", "SERVICE", "2026-04-01", "2028-03-31",
         1, 6200000, "ACTIVE", "NCA-aligned 24x7 managed detection & response."),
        ("FM", "ETD-2026-003", "Integrated facilities management framework (HQ + DC)",
         "اتفاقية إطارية لإدارة المرافق المتكاملة (المقر ومركز البيانات)", "FRAMEWORK",
         "2026-01-01", "2029-12-31", 1, 14800000, "ACTIVE", "3-year FM framework."),
        ("CLEAN", "ETD-2026-004", "Cleaning & hospitality services (annual)",
         "خدمات النظافة والضيافة (سنوي)", "SERVICE", "2026-01-01", "2026-12-31",
         0, 1800000, "ACTIVE", "Annual cleaning contract."),
        ("SI", "ETD-2026-005", "Licensing platform system-integration SOW",
         "نطاق عمل تكامل منصة التراخيص", "SERVICE", "2026-05-01", "2027-04-30",
         0, 4300000, "ACTIVE", "Integration of licensing & beneficiary platforms."),
        ("GUARD", "ETD-2026-006", "Manned guarding & access-control services",
         "خدمات الحراسات الأمنية والتحكم بالدخول", "SERVICE", "2025-07-01", "2026-06-30",
         0, 3100000, "UNDER_REVIEW", "Renewal under review pending SLA remediation."),
        ("TRAIN", "ETD-2026-007", "Learning & development framework",
         "اتفاقية إطارية للتدريب والتطوير", "FRAMEWORK", "2026-02-01", "2028-01-31",
         1, 2200000, "ACTIVE", "HRDF-linked training framework."),
        ("CONTACT", "ETD-2026-009", "Beneficiary contact-centre operations",
         "تشغيل مركز اتصال المستفيدين", "SERVICE", "2026-06-01", "2028-05-31",
         1, 5400000, "ACTIVE", "Omnichannel beneficiary contact centre."),
        ("DIGI", "ETD-2026-010", "Records digitisation & archiving",
         "رقمنة وأرشفة الوثائق", "SERVICE", "2026-06-15", "2027-06-14",
         0, 2600000, "ACTIVE", "Digitisation with PDPL processor obligations."),
        ("LOGI", "ETD-2025-011", "Official correspondence & secure logistics",
         "المراسلات الرسمية واللوجستيات الآمنة", "SERVICE", "2024-01-01", "2025-12-31",
         0, 1200000, "EXPIRED", "Expired SPL logistics contract pending re-tender."),
    ]
    for key, num, ten, tar, ctype, sd, ed, auto, val, status, notes in rows:
        vid = sv[key]
        cid = exists(cur,
            "SELECT id FROM scm.contracts WHERE organisation_id=? AND contract_number=?",
            ORG, num)
        if cid is None:
            cur.execute(
                """INSERT INTO scm.contracts
                     (organisation_id, vendor_id, contract_number, title_en, title_ar,
                      contract_type, start_date, end_date, auto_renewal, contract_value,
                      currency, status, notes, created_by, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,'SAR',?,?,?,0)""",
                ORG, vid, num, ten, tar, ctype, sd, ed, auto, val, status, notes, U_RMGR)
            cid = exists(cur,
                "SELECT id FROM scm.contracts WHERE organisation_id=? AND contract_number=?",
                ORG, num)
            bump("scm.contracts", True)
        else:
            bump("scm.contracts", False)
        ids[key] = cid
    return ids


# =============================================================================
#  scm.slas  (guard: org + vendor_id + metric_name_en)
# =============================================================================
def seed_scm_slas(cur, sv: dict[str, int], sc: dict[str, int]) -> None:
    rows = [
        ("MSSP", "Mean time to detect (MTTD)", "متوسط زمن الاكتشاف", 15, "minutes",
         "MONTHLY", 12, "MET"),
        ("MSSP", "Mean time to respond (MTTR)", "متوسط زمن الاستجابة", 4, "hours",
         "MONTHLY", 3, "MET"),
        ("GOVCLOUD", "Platform availability", "توفر المنصة", 99.9, "percent",
         "MONTHLY", 99.95, "MET"),
        ("FM", "Critical-fault response time", "زمن الاستجابة للأعطال الحرجة", 2, "hours",
         "MONTHLY", 3, "AT_RISK"),
        ("CONTACT", "Helpdesk first-response time", "زمن الرد الأول لخدمة المستفيدين", 30,
         "minutes", "WEEKLY", 28, "MET"),
        ("GUARD", "Patrol coverage compliance", "الالتزام بتغطية الدوريات", 98, "percent",
         "MONTHLY", 91, "BREACHED"),
        ("SI", "Defect-fix turnaround", "زمن إصلاح العيوب", 5, "days",
         "MONTHLY", 6, "AT_RISK"),
        ("ELM", "Identity-verification availability", "توفر التحقق من الهوية", 99.5, "percent",
         "MONTHLY", 99.8, "MET"),
    ]
    for key, men, mar, target, unit, freq, current, status in rows:
        vid = sv[key]
        cid = sc.get(key)
        eid = exists(cur,
            "SELECT id FROM scm.slas WHERE organisation_id=? AND vendor_id=? AND metric_name_en=?",
            ORG, vid, men)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.slas
                     (organisation_id, vendor_id, contract_id, metric_name_en,
                      metric_name_ar, target_value, unit, measurement_frequency,
                      current_value, status, last_measured_at, created_by, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,0)""",
                ORG, vid, cid, men, mar, target, unit, freq, current, status, U_RMGR)
            bump("scm.slas", True)
        else:
            bump("scm.slas", False)


# =============================================================================
#  scm.dependencies  (guard: org + service_name_en + vendor_id)
# =============================================================================
def seed_scm_dependencies(cur, sv: dict[str, int]) -> None:
    rows = [
        ("Licensing e-service (beneficiary portal)", "الخدمة الإلكترونية للتراخيص (بوابة المستفيدين)",
         "Licensing Platform", "GOVCLOUD", "CRITICAL", "HIGH", 1, None, "ACTIVE",
         "Hosted on GovCloud. Alternative: on-prem national DC for DR. NCEMA RTO 4h; beneficiary-service impact High if unavailable."),
        ("Security monitoring (SOC)", "المراقبة الأمنية (مركز العمليات)",
         "SIEM / SOC", "MSSP", "EXCLUSIVE", "HIGH", 0, None, "ACTIVE",
         "Sole managed-SOC provider; exclusive dependency. Concentration risk noted; in-house fallback under build."),
        ("HQ + data-hall facilities", "مرافق المقر وقاعة البيانات",
         "Facilities", "FM", "CRITICAL", "HIGH", 1, None, "ACTIVE",
         "Power/HVAC/MEP for HQ and data hall. Alternate FM identified. Continuity-critical for the data hall."),
        ("Identity / SSO (Nafath integration)", "الهوية والدخول الموحد (تكامل نفاذ)",
         "Identity Service", "ELM", "CRITICAL", "HIGH", 0, None, "ACTIVE",
         "National identity verification via Elm/Nafath. No like-for-like alternative; degraded-mode procedures only."),
        ("Beneficiary contact centre", "مركز اتصال المستفيدين",
         "Contact Centre", "CONTACT", "SHARED", "MEDIUM", 1, None, "ACTIVE",
         "Outsourced omnichannel contact centre; partial in-house overflow capacity exists."),
        ("Records archiving", "أرشفة الوثائق",
         "Records / Archive", "DIGI", "NON_CRITICAL", "MEDIUM", 1, None, "ACTIVE",
         "Records digitisation/archiving; non-real-time, alternative providers available."),
        ("System integration of licensing platform", "تكامل منصة التراخيص",
         "Integration", "SI", "CRITICAL", "HIGH", 0, None, "AT_RISK",
         "Single SI holds platform integration knowledge; at-risk due to open critical vulns. Knowledge-escrow planned."),
        ("Official secure correspondence", "المراسلات الرسمية الآمنة",
         "Logistics", "LOGI", "NON_CRITICAL", "LOW", 1, None, "ACTIVE",
         "Secure document logistics via SPL; commercial couriers available as fallback."),
    ]
    for sen, sar, asset, key, dtype, dlvl, hasalt, altkey, status, impact in rows:
        vid = sv[key]
        eid = exists(cur,
            "SELECT id FROM scm.dependencies WHERE organisation_id=? AND service_name_en=? AND vendor_id=?",
            ORG, sen, vid)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.dependencies
                     (organisation_id, service_name_en, service_name_ar, asset_name,
                      vendor_id, dependency_type, dependency_level, has_alternative,
                      alternative_vendor_id, status, impact_analysis, created_by, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, sen, sar, asset, vid, dtype, dlvl, hasalt,
                sv.get(altkey) if altkey else None, status, impact, U_RMGR)
            bump("scm.dependencies", True)
        else:
            bump("scm.dependencies", False)


# =============================================================================
#  scm.exit_plans  (guard: org + plan_code)
# =============================================================================
def seed_scm_exit_plans(cur, sv: dict[str, int]) -> None:
    rows = [
        ("EXP-GOV-GOVCLOUD", "GOVCLOUD", "GovCloud exit & data repatriation",
         "خطة الخروج وإعادة البيانات للسحابة الحكومية",
         "Sustained SLA breach, loss of NCA/CCC licence, or directive to repatriate national data.",
         "CRITICAL", 120, "PASSED",
         ["Freeze new workloads", "Snapshot & export data to national DC", "Crypto-erase source tenancy",
          "Re-point DNS / integrations", "Validate beneficiary services", "Decommission cloud tenancy"],
         "Data repatriated to national data centre with verified crypto-erase, per NDMO data-return and PDPL deletion clauses.",
         "إعادة البيانات إلى مركز البيانات الوطني مع تحقق من المحو التشفيري وفق بنود إعادة البيانات وحذفها."),
        ("EXP-GOV-MSSP", "MSSP", "Managed-SOC handover to in-house SOC",
         "خطة تسليم مركز العمليات الأمنية إلى الفريق الداخلي",
         "Contract termination, repeated MTTD/MTTR breaches, or licence lapse.",
         "HIGH", 90, "GAP_IDENTIFIED",
         ["Knowledge transfer of detections/playbooks", "Migrate SIEM content", "Recruit/seat in-house analysts",
          "Parallel-run", "Cutover", "Decommission MSSP access"],
         "SIEM content & playbooks transferred; gap: in-house analyst headcount not yet fully seated.",
         "نقل محتوى نظام إدارة الأحداث والكتيبات؛ الفجوة: لم يكتمل توظيف المحللين الداخليين."),
        ("EXP-GOV-FM", "FM", "Facilities-management transition plan",
         "خطة انتقال إدارة المرافق",
         "Contract end, safety/continuity failure, or termination for cause.",
         "HIGH", 60, "NOT_TESTED",
         ["Award alternate FM via Etimad", "Asset & maintenance-record handover", "Staff TUPE/mobilisation",
          "Parallel cover for data hall", "Cutover"],
         "Transition to alternate FM provider with asset and maintenance-record handover.",
         "الانتقال إلى مزود بديل لإدارة المرافق مع تسليم الأصول وسجلات الصيانة."),
        ("EXP-GOV-CONTACT", "CONTACT", "Contact-centre exit & script/data handover",
         "خطة خروج مركز الاتصال وتسليم النصوص والبيانات",
         "Contract end, PDPL non-compliance, or service-quality failure.",
         "MEDIUM", 90, "NOT_TESTED",
         ["Export call-recordings & CRM data in-Kingdom", "Handover scripts/knowledge base",
          "Transfer numbers/IVR", "Stand up alternate provider", "Cutover"],
         "Beneficiary data and recordings returned in-Kingdom per PDPL; knowledge base handed over.",
         "إعادة بيانات وتسجيلات المستفيدين داخل المملكة وفق النظام؛ وتسليم قاعدة المعرفة."),
        ("EXP-GOV-SI", "SI", "System-integrator knowledge & source escrow exit",
         "خطة خروج المكامل مع ضمان المعرفة والشيفرة",
         "Insolvency, persistent critical vulns, or termination.",
         "HIGH", 90, "NOT_TESTED",
         ["Release source/config from escrow", "Knowledge transfer to alternate SI/in-house",
          "Re-document integrations", "Validate build/deploy", "Cutover support"],
         "Source code and configuration released from escrow with documented knowledge transfer.",
         "تحرير الشيفرة والإعدادات من الضمان مع نقل معرفي موثّق."),
        ("EXP-GOV-DIGI", "DIGI", "Records-digitisation exit & secure destruction",
         "خطة خروج رقمنة الوثائق والإتلاف الآمن",
         "Contract end or PDPL processor breach.",
         "MEDIUM", 60, "NOT_TESTED",
         ["Return digitised records & index", "Secure-destruction of supplier copies with certificate",
          "Confirm NDMO data-return", "Close access"],
         "Digitised records returned; supplier copies securely destroyed with certificate per PDPL.",
         "إعادة الوثائق المرقمنة؛ وإتلاف نسخ المورد بشكل آمن مع شهادة وفق النظام."),
    ]
    for code, key, ten, tar, trig, sev, days, outcome, steps, dmen, dmar in rows:
        vid = sv[key]
        vname = GOV_BY_KEY[key]["name_en"]
        status = "APPROVED" if outcome in ("PASSED", "GAP_IDENTIFIED") else "DRAFT"
        tested = outcome != "NOT_TESTED"
        eid = exists(cur,
            "SELECT id FROM scm.exit_plans WHERE organisation_id=? AND plan_code=?",
            ORG, code)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.exit_plans
                     (organisation_id, vendor_id, vendor_name, plan_code, title_en, title_ar,
                      trigger_conditions, severity_threshold, target_disengagement_days,
                      steps_json, data_migration_plan_en, data_migration_plan_ar,
                      knowledge_transfer_plan_en, knowledge_transfer_plan_ar,
                      last_tested_at, last_tested_outcome, next_test_due, status,
                      approved_at, approver_name, external_id, is_deleted, is_active,
                      created_by, row_version)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,1,?,0)""",
                ORG, vid, vname, code, ten, tar, trig, sev, days,
                j(steps), dmen, dmar,
                "Knowledge transfer to in-house / alternate per exit playbook.",
                "نقل المعرفة للفريق الداخلي أو البديل وفق كتيب الخروج.",
                "2026-03-31" if tested else None, outcome, "2027-03-31", status,
                "2026-04-10" if status == "APPROVED" else None,
                PROC_OWNER if status == "APPROVED" else None,
                "EXP-" + code, U_RMGR)
            bump("scm.exit_plans", True)
        else:
            bump("scm.exit_plans", False)


# =============================================================================
#  scm.supplier_resilience_tests  (guard: org + test_code)
# =============================================================================
def seed_scm_resilience(cur, sv: dict[str, int]) -> None:
    rows = [
        ("SRT-GOV-001", "GOVCLOUD", "GovCloud Riyadh region-failure tabletop",
         "تمرين مكتبي لفشل منطقة السحابة الحكومية بالرياض",
         "TABLETOP", "Simulated loss of primary GovCloud region; failover to DR for beneficiary e-services.",
         "محاكاة فقد منطقة السحابة الأساسية والتحول لموقع التعافي لخدمات المستفيدين.",
         "PASS_WITH_OBSERVATIONS", 240, 210),
        ("SRT-GOV-002", "MSSP", "MSSP joint cyber crisis simulation",
         "محاكاة أزمة سيبرانية مشتركة مع مزود الأمن المُدار",
         "JOINT_CRISIS_SIM", "Joint SOC + MSSP simulation of a major intrusion affecting national data.",
         "محاكاة مشتركة لاختراق كبير يؤثر على البيانات الوطنية بين الفريق ومزود الأمن.",
         None, 60, None),  # SCHEDULED
        ("SRT-GOV-003", "FM", "Data-hall critical-utility failure drill",
         "تمرين فشل المرافق الحرجة لقاعة البيانات",
         "FAILOVER_DRILL", "Live drill of chiller/UPS failure in the data hall and FM emergency response.",
         "تمرين حي لفشل التبريد/مزودات الطاقة في قاعة البيانات واستجابة الطوارئ.",
         "PASS", 120, 95),
    ]
    for code, key, ten, tar, ttype, scen_en, scen_ar, outcome, rto_t, rto_a in rows:
        vid = sv[key]
        vname = GOV_BY_KEY[key]["name_en"]
        status = "SCHEDULED" if outcome is None else "APPROVED"
        eid = exists(cur,
            "SELECT id FROM scm.supplier_resilience_tests WHERE organisation_id=? AND test_code=?",
            ORG, code)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.supplier_resilience_tests
                     (organisation_id, external_id, test_code, vendor_id, vendor_name,
                      title_en, title_ar, test_type, scenario_scope,
                      scenario_description_en, scenario_description_ar,
                      success_criteria_en, scheduled_for, executed_at, duration_minutes,
                      outcome, rto_target_minutes, rto_actual_minutes,
                      observations_en, observations_ar, capa_refs, next_test_due,
                      facilitator, status, is_deleted, is_active, created_by, row_version)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,1,?,0)""",
                ORG, "SRT-" + code, code, vid, vname, ten, tar, ttype,
                "DESKTOP" if ttype == "TABLETOP" else "LIVE",
                scen_en, scen_ar, "Meet RTO; validate continuity of beneficiary services per NCEMA / ISO 22301.",
                "2026-09-15T09:00:00" if status == "SCHEDULED" else None,
                "2026-03-20T09:00:00" if status != "SCHEDULED" else None,
                None if status == "SCHEDULED" else (rto_a or 90),
                outcome, rto_t, rto_a,
                "Recovery within RTO; minor observations on runbook clarity." if outcome else None,
                "التعافي ضمن الهدف؛ ملاحظات بسيطة على وضوح كتيب التشغيل." if outcome else None,
                "CAPA-SRT-001" if outcome == "PASS_WITH_OBSERVATIONS" else None,
                "2027-03-31", PROC_OWNER, status, U_RMGR)
            bump("scm.supplier_resilience_tests", True)
        else:
            bump("scm.supplier_resilience_tests", False)


# =============================================================================
#  scm.alternate_suppliers  (guard: org + external_id)
# =============================================================================
def seed_scm_alternates(cur, sv: dict[str, int]) -> None:
    rows = [
        ("ALT-GOV-001", "GOVCLOUD", "On-premise National Data Centre (DR)",
         "BACKUP_PRIMARY", "FRAMEWORK_ACTIVE", "READY", 45, 3500000,
         "Disaster-recovery hosting of beneficiary e-services on the on-prem national DC.",
         "استضافة التعافي من الكوارث لخدمات المستفيدين على مركز البيانات الوطني الداخلي.",
         "Mitigates GovCloud concentration risk; in-Kingdom; Vision-2030 local-content positive."),
        ("ALT-GOV-002", "MSSP", "Alternate NCA-licensed MSSP",
         "BACKUP_SECONDARY", "MOU_SIGNED", "CONDITIONAL", 90, 4200000,
         "Secondary managed-SOC provider for failover / dual-sourcing.",
         "مزود ثانوي لمركز العمليات الأمنية المُدار للتحول أو التعدد.",
         "Conditional on completing onboarding & integration testing."),
        ("ALT-GOV-003", "FM", "Alternate facilities-management provider",
         "TERTIARY", "NONE", "GAP", 120, 5000000,
         "Tertiary FM provider held for continuity of HQ/data-hall services.",
         "مزود ثالث لإدارة المرافق لضمان استمرارية خدمات المقر وقاعة البيانات.",
         "Gap: no contractual standby yet; framework procurement recommended."),
        ("ALT-GOV-004", "CONTACT", "In-house overflow contact-centre capacity",
         "BACKUP_SECONDARY", "NONE", "CONDITIONAL", 30, 900000,
         "Internal overflow capacity to absorb partial contact-centre load.",
         "قدرة فائض داخلية لاستيعاب جزء من حمل مركز الاتصال.",
         "Conditional on staffing & telephony provisioning."),
        ("ALT-GOV-005", "SI", "Alternate national system integrator",
         "BACKUP_SECONDARY", "MOU_SIGNED", "CONDITIONAL", 120, 4800000,
         "Backup SI able to take over licensing-platform integration via escrow.",
         "مكامل بديل قادر على استلام تكامل منصة التراخيص عبر الضمان.",
         "Conditional on source-escrow release and knowledge transfer."),
        ("ALT-GOV-006", "LOGI", "Commercial secure-courier alternative",
         "TERTIARY", "FRAMEWORK_ACTIVE", "READY", 14, 600000,
         "Commercial secure courier as fallback for official correspondence logistics.",
         "ناقل تجاري آمن كبديل للوجستيات المراسلات الرسمية.",
         "Ready; low criticality."),
    ]
    for code, key, altname, role, contractual, outcome, days, cost, sen, sar, notes in rows:
        vid = sv[key]
        pname = GOV_BY_KEY[key]["name_en"]
        status = "APPROVED" if outcome in ("READY", "CONDITIONAL") else "DRAFT"
        eid = exists(cur,
            "SELECT id FROM scm.alternate_suppliers WHERE organisation_id=? AND external_id=?",
            ORG, code)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.alternate_suppliers
                     (organisation_id, primary_vendor_id, primary_vendor_name,
                      alternate_vendor_name, alternate_vendor_country, alternate_vendor_role,
                      service_scope_en, service_scope_ar, estimated_onboarding_days,
                      estimated_cost_sar, last_assessed_at, last_assessed_outcome,
                      contractual_status, notes_en, notes_ar, status, approved_at,
                      approver_name, external_id, is_deleted, is_active, created_by, row_version)
                   VALUES (?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,?,?,?,?,?,?,?,0,1,?,0)""",
                ORG, vid, pname, altname, "SA", role, sen, sar, days, cost,
                outcome, contractual, notes, notes, status,
                "2026-04-10" if status == "APPROVED" else None,
                PROC_OWNER if status == "APPROVED" else None, code, U_RMGR)
            bump("scm.alternate_suppliers", True)
        else:
            bump("scm.alternate_suppliers", False)


# =============================================================================
#  scm.fourth_party_exposures  (guard: org + external_id)
# =============================================================================
def seed_scm_fourth_party(cur, sv: dict[str, int]) -> None:
    rows = [
        ("4PE-GOV-001", "GOVCLOUD", "Underlying hyperscaler region (sub-infrastructure)",
         "INFRASTRUCTURE", "CRITICAL", "CRITICAL", 1, 1,
         "GovCloud rides on an underlying hyperscaler region for compute/storage.",
         "تعتمد السحابة الحكومية على منطقة مزود سحابي عملاق للحوسبة والتخزين.",
         "Compute, storage, network", "Flow-down NCA ECC-4 & in-Kingdom residency clauses in place."),
        ("4PE-GOV-002", "MSSP", "Global threat-intelligence feed provider",
         "DATA_PROCESSING", "HIGH", "MEDIUM", 1, 1,
         "MSSP consumes a global threat-intel feed; limited indicator data shared.",
         "يستهلك مزود الأمن تغذية تهديدات عالمية مع مشاركة محدودة لمؤشرات.",
         "Threat indicators (no personal data)", "PDPL sub-processor & contract-chain clauses enforced."),
        ("4PE-GOV-003", "SI", "Niche integration subcontractor",
         "SUPPORT", "MEDIUM", "MEDIUM", 1, 0,
         "SI uses a niche subcontractor for a specialised integration component.",
         "يستعين المكامل بمقاول متخصص لمكوّن تكامل معين.",
         "Configuration metadata", "Flow-down clauses pending; mitigation: require sub-processor register."),
        ("4PE-GOV-004", "CONTACT", "Cloud telephony / IVR platform",
         "SOFTWARE", "HIGH", "MEDIUM", 1, 1,
         "Contact centre relies on a cloud telephony/IVR platform for omnichannel.",
         "يعتمد مركز الاتصال على منصة اتصالات سحابية لخدمة القنوات المتعددة.",
         "Call metadata, recordings", "In-Kingdom recording residency confirmed; PDPL DPA in place."),
        ("4PE-GOV-005", "ELM", "National identity source (Yakeen/Absher data)",
         "DATA_PROCESSING", "CRITICAL", "HIGH", 0, 1,
         "Identity verification draws on national identity data sources.",
         "يستند التحقق من الهوية إلى مصادر بيانات الهوية الوطنية.",
         "Identity attributes", "Governed by NDMO data-sharing agreement; no sub-contracting permitted."),
        ("4PE-GOV-006", "DIGI", "Scanning hardware maintenance vendor",
         "SUPPORT", "LOW", "LOW", 1, 0,
         "Digitisation supplier uses a third party to maintain scanning hardware.",
         "يستعين مورد الرقمنة بطرف ثالث لصيانة أجهزة المسح.",
         "None (no data access)", "No data access; low concentration risk."),
    ]
    for code, key, fpname, dtype, dcrit, conc, subc, flow, sen, sar, datacats, mit in rows:
        vid = sv[key]
        pname = GOV_BY_KEY[key]["name_en"]
        eid = exists(cur,
            "SELECT id FROM scm.fourth_party_exposures WHERE organisation_id=? AND external_id=?",
            ORG, code)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.fourth_party_exposures
                     (organisation_id, external_id, primary_vendor_id, primary_vendor_name,
                      fourth_party_name, fourth_party_country, dependency_type,
                      downstream_criticality, concentration_risk, service_description_en,
                      service_description_ar, data_categories_shared, contract_chain_clauses,
                      sub_contracting_allowed, flow_down_clauses_present, status,
                      mitigation_notes_en, mitigation_notes_ar, is_deleted, is_active,
                      created_by, row_version)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,1,?,0)""",
                ORG, code, vid, pname, fpname, "SA", dtype, dcrit, conc, sen, sar,
                datacats, "Contract-chain flow-down clauses per NCA ECC-4 / PDPL.",
                subc, flow, "VALIDATED", mit, mit, U_RMGR)
            bump("scm.fourth_party_exposures", True)
        else:
            bump("scm.fourth_party_exposures", False)


# =============================================================================
#  scm.contract_clauses  (guard: org + clause_code) — government model clauses
# =============================================================================
def seed_scm_clauses(cur) -> None:
    rows = [
        ("CLS-GOV-01", "GTPL / Etimad procurement compliance",
         "الالتزام بنظام المنافسات والمشتريات الحكومية ومنصة اعتماد",
         "COMPLIANCE", "FIXED", "HIGH",
         "The supplier shall comply with the Government Tenders & Procurement Law (GTPL), its implementing regulations, and all obligations arising from the Etimad platform award.",
         "يلتزم المورد بنظام المنافسات والمشتريات الحكومية ولائحته التنفيذية وكافة الالتزامات الناشئة عن الترسية عبر منصة اعتماد.",
         "GTPL, Etimad"),
        ("CLS-GOV-02", "NDMO data-sharing & in-Kingdom data residency",
         "مشاركة البيانات وفق مكتب إدارة البيانات والإقامة داخل المملكة",
         "DATA_PROTECTION", "FIXED", "CRITICAL",
         "Government data shall be processed and stored within the Kingdom of Saudi Arabia and shared only under an NDMO-compliant data-sharing agreement and the national data classification.",
         "تُعالَج وتُخزَّن البيانات الحكومية داخل المملكة العربية السعودية ولا تُشارَك إلا بموجب اتفاقية مشاركة بيانات متوافقة مع مكتب إدارة البيانات الوطنية ووفق التصنيف الوطني للبيانات.",
         "NDMO, National Data Management"),
        ("CLS-GOV-03", "Nazaha integrity, anti-bribery & conflict-of-interest disclosure",
         "النزاهة ومكافحة الرشوة والإفصاح عن تعارض المصالح (نزاهة)",
         "COMPLIANCE", "FIXED", "HIGH",
         "The supplier shall uphold integrity standards, refrain from any act of bribery or corruption, and disclose any actual or potential conflict of interest in accordance with Nazaha requirements.",
         "يلتزم المورد بمعايير النزاهة والامتناع عن أي فعل رشوة أو فساد والإفصاح عن أي تعارض مصالح فعلي أو محتمل وفق متطلبات هيئة الرقابة ومكافحة الفساد (نزاهة).",
         "Nazaha"),
        ("CLS-GOV-04", "Local-content commitment (Vision 2030)",
         "الالتزام بالمحتوى المحلي (رؤية 2030)",
         "OTHER", "NEGOTIABLE", "MEDIUM",
         "The supplier shall meet the agreed local-content percentage and report on Saudi employment and local spend in line with Vision 2030 local-content objectives.",
         "يلتزم المورد بنسبة المحتوى المحلي المتفق عليها والإفصاح عن التوطين والإنفاق المحلي بما يتوافق مع أهداف المحتوى المحلي لرؤية 2030.",
         "Vision 2030, Local Content (LCGPA)"),
        ("CLS-GOV-05", "Government audit & right-to-inspect (GCA / Diwan)",
         "حق التدقيق والتفتيش الحكومي (ديوان المراقبة العامة)",
         "AUDIT_RIGHTS", "FIXED", "HIGH",
         "The Authority and competent government bodies (incl. the General Court of Audit) shall have the right to audit and inspect the supplier's records, premises and controls relevant to this contract.",
         "يحق للهيئة والجهات الحكومية المختصة (بما في ذلك ديوان المراقبة العامة) تدقيق وتفتيش سجلات المورد ومواقعه وضوابطه ذات الصلة بهذا العقد.",
         "GCA / Diwan Al-Muraqaba"),
        ("CLS-GOV-06", "PDPL data-processor obligations for government data",
         "التزامات معالج البيانات وفق نظام حماية البيانات الشخصية",
         "DATA_PROTECTION", "FIXED", "CRITICAL",
         "Where the supplier processes personal data on behalf of the Authority, it shall act only on documented instructions, implement appropriate safeguards, notify breaches without undue delay, and return or destroy data on termination, per PDPL.",
         "عند معالجة المورد للبيانات الشخصية نيابة عن الهيئة، يلتزم بالتصرف وفق تعليمات موثقة وتطبيق الضمانات المناسبة والإبلاغ عن الانتهاكات دون تأخير وإعادة البيانات أو إتلافها عند الانتهاء، وفق نظام حماية البيانات الشخصية.",
         "PDPL"),
    ]
    for code, ten, tar, cat, nego, risk, ben, bar, refs in rows:
        eid = exists(cur,
            "SELECT id FROM scm.contract_clauses WHERE organisation_id=? AND clause_code=?",
            ORG, code)
        if eid is None:
            cur.execute(
                """INSERT INTO scm.contract_clauses
                     (organisation_id, clause_code, title_en, title_ar, category,
                      applicable_to, body_en, body_ar, guidance_en, guidance_ar,
                      regulatory_refs, risk_level, negotiability, version, status,
                      approved_at, approver_name, owner, external_id, is_deleted,
                      is_active, created_by, row_version)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0','APPROVED',SYSUTCDATETIME(),?,?,?,0,1,?,0)""",
                ORG, code, ten, tar, cat,
                "Government service & framework contracts",
                ben, bar,
                "Mandatory in all government supplier contracts unless legally varied.",
                "إلزامي في كافة عقود الموردين الحكوميين ما لم يُعدّل نظاماً.",
                refs, risk, nego, PROC_OWNER, PROC_OWNER, "EXT-" + code, U_COMP)
            bump("scm.contract_clauses", True)
        else:
            bump("scm.contract_clauses", False)


# =============================================================================
#  scm.supplier_incidents  (guard: org + incident_code)
# =============================================================================
def seed_scm_incidents(cur, sv: dict[str, int]) -> None:
    rows = [
        ("SCM-INC-GOV-001", "GOVCLOUD", "SERVICE_DISRUPTION", "HIGH", 1, "RESOLVED", 1, 1, 0,
         "2-hour GovCloud zone outage impacting the beneficiary e-service portal; DR failover activated.",
         "انقطاع لمدة ساعتين في منطقة السحابة الحكومية أثّر على بوابة خدمات المستفيدين؛ تم تفعيل التعافي."),
        ("SCM-INC-GOV-002", "MSSP", "QUALITY", "HIGH", 0, "IN_PROGRESS", 0, 1, 1,
         "Delayed security alert (missed MTTD SLA) on a suspicious authentication burst; escalated to management.",
         "تأخر تنبيه أمني (إخفاق زمن الاكتشاف) على دفعة مصادقة مشبوهة؛ تم التصعيد للإدارة."),
        ("SCM-INC-GOV-003", "FM", "SERVICE_DISRUPTION", "CRITICAL", 1, "RESOLVED", 1, 1, 1,
         "Chiller failure in the data hall risking thermal shutdown; temporary cooling deployed, CAPA raised.",
         "فشل وحدة تبريد في قاعة البيانات هدد بإيقاف حراري؛ نُشر تبريد مؤقت ورُفع إجراء تصحيحي."),
        ("SCM-INC-GOV-004", "GUARD", "SLA_BREACH", "MEDIUM", 1, "ESCALATED", 0, 1, 1,
         "Patrol-coverage SLA breach across two shifts; recurring issue escalated alongside integrity concern.",
         "إخفاق في اتفاقية تغطية الدوريات عبر ورديتين؛ مشكلة متكررة صُعّدت مع ملاحظة نزاهة."),
        ("SCM-INC-GOV-005", "CONTACT", "DELAY", "MEDIUM", 0, "CLOSED", 0, 0, 0,
         "Temporary spike in beneficiary call wait-times during a peak campaign; staffing adjusted.",
         "ارتفاع مؤقت في أزمنة انتظار مكالمات المستفيدين خلال حملة ذروة؛ تم تعديل التوظيف."),
    ]
    for code, key, itype, impact, sla, status, backup, capa, esc, den, dar in rows:
        vid = sv[key]
        eid = exists(cur,
            "SELECT id FROM scm.supplier_incidents WHERE organisation_id=? AND incident_code=?",
            ORG, code)
        if eid is None:
            resolved = status in ("RESOLVED", "CLOSED")
            cur.execute(
                """INSERT INTO scm.supplier_incidents
                     (organisation_id, incident_code, vendor_id, incident_type, impact,
                      description, sla_breached, status, backup_plan_activated, capa_created,
                      escalated_to_management, reported_at, resolved_at, assigned_to_id,
                      resolution_notes, created_by, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,?,?,?,0)""",
                ORG, code, vid, itype, impact, dar + " | " + den, sla, status,
                backup, capa, esc,
                "2026-03-21T12:00:00" if resolved else None, U_RMGR,
                "Resolved; CAPA tracked." if resolved else None, U_RMGR)
            bump("scm.supplier_incidents", True)
        else:
            bump("scm.supplier_incidents", False)


# =============================================================================
#  tprm.vendors  — same panel in the TPRM registry (guard: org + vendor_name)
# =============================================================================
def seed_tprm_vendors(cur) -> dict[str, int]:
    ids: dict[str, int] = {}
    # A couple of extra central-government data-sharing counterparts for TPRM only.
    extra = [
        dict(key="NIC", name_en="National Information Center (Yakeen/Absher data source)",
             name_ar="المركز الوطني للمعلومات (مصدر بيانات يقين/أبشر)",
             tprm_risk="HIGH", vtype_ar="مصدر بيانات وطني"),
        dict(key="NAFATH", name_en="Nafath National Single Sign-On",
             name_ar="نفاذ - الدخول الوطني الموحد",
             tprm_risk="HIGH", vtype_ar="هوية رقمية وطنية"),
    ]
    panel = GOV_SUPPLIERS + extra
    for g in panel:
        vid = exists(cur,
            "SELECT id FROM tprm.vendors WHERE organisation_id=? AND vendor_name=?",
            ORG, g["name_en"])
        if vid is None:
            cur.execute(
                """INSERT INTO tprm.vendors
                     (organisation_id, vendor_name, vendor_name_ar, vendor_type, country,
                      risk_rating, status, description, created_by, is_deleted)
                   VALUES (?,?,?,?,?,?,'ACTIVE',?,?,0)""",
                ORG, g["name_en"], g["name_ar"], g["vtype_ar"], "Saudi Arabia",
                g.get("tprm_risk", "MEDIUM"),
                f"Etimad-procured government third party. Owned by {PROC_OWNER}.",
                U_RMGR)
            vid = exists(cur,
                "SELECT id FROM tprm.vendors WHERE organisation_id=? AND vendor_name=?",
                ORG, g["name_en"])
            bump("tprm.vendors", True)
        else:
            bump("tprm.vendors", False)
        ids[g["key"]] = vid
    return ids


# =============================================================================
#  tprm.third_party_cyber_assessments  (guard: org + assessment_code)
# =============================================================================
def seed_tprm_cyber(cur, tv: dict[str, int]) -> None:
    rows = [
        ("TPC-GOV-001", "GOVCLOUD", "CRITICAL", "LOW", "ISO27001; NCA CCC; SOC2 Type II",
         0, 0, 92, 1, 1, 90.0,
         "GovCloud third-party cyber assessment per NCA ECC-4 & CCC. Strong posture; MFA enforced; pen-test within 12 months; no breach in 24m.",
         "تقييم سيبراني للطرف الخارجي للسحابة الحكومية وفق الضوابط الأساسية المجال الرابع والضوابط السحابية. وضع قوي؛ المصادقة متعددة العوامل مفعّلة؛ اختبار اختراق خلال 12 شهراً."),
        ("TPC-GOV-002", "MSSP", "CRITICAL", "MODERATE", "ISO27001; NCA MSSP licence; SOC2",
         0, 1, 85, 1, 1, 78.0,
         "Managed-SOC cyber assessment. Licence valid; minor open vulns; sub-processor threat-intel flow-down confirmed.",
         "تقييم سيبراني لمزود الأمن المُدار. الترخيص ساري؛ ثغرات بسيطة مفتوحة؛ تأكيد بنود التعاقد من الباطن لتغذية التهديدات."),
        ("TPC-GOV-003", "ELM", "HIGH", "LOW", "ISO27001; NCA-aligned",
         0, 0, 90, 1, 1, 88.0,
         "Identity-platform assessment. High availability; secure Nafath/Yakeen integration; strong controls.",
         "تقييم منصة الهوية. توفر عالٍ؛ تكامل آمن مع نفاذ ويقين؛ ضوابط قوية."),
        ("TPC-GOV-004", "SI", "HIGH", "ELEVATED", "ISO9001; partial ISO27001",
         0, 0, 64, 0, 0, 58.0,
         "System-integrator assessment. Open critical vulns in delivered components; MFA not fully enforced; pen-test overdue. Treatment plan required.",
         "تقييم المكامل. ثغرات حرجة مفتوحة في المكونات المسلّمة؛ المصادقة متعددة العوامل غير مفعّلة بالكامل؛ اختبار الاختراق متأخر. تلزم خطة معالجة."),
        ("TPC-GOV-005", "FM", "MEDIUM", "MODERATE", "ISO41001; ISO45001",
         0, 0, 70, 0, 1, 66.0,
         "Facilities (OT) assessment. Building-management systems segmented; physical/OT controls adequate; minor IT-hygiene gaps.",
         "تقييم المرافق (تقنية التشغيل). أنظمة إدارة المباني معزولة؛ الضوابط المادية والتشغيلية كافية؛ فجوات بسيطة في النظافة التقنية."),
        ("TPC-GOV-006", "CONTACT", "HIGH", "MODERATE", "ISO27001; PCI-DSS (telephony)",
         0, 0, 80, 1, 1, 74.0,
         "Contact-centre assessment. In-Kingdom recording residency; PDPL consent controls; adequate access management.",
         "تقييم مركز الاتصال. بقاء التسجيلات داخل المملكة؛ ضوابط موافقة وفق النظام؛ إدارة وصول كافية."),
        ("TPC-GOV-007", "DIGI", "MEDIUM", "MODERATE", "ISO27001 (scoped)",
         0, 0, 72, 1, 0, 68.0,
         "Digitisation supplier assessment. Personal-data handling reviewed; secure-destruction process to be evidenced.",
         "تقييم مورد الرقمنة. مراجعة معالجة البيانات الشخصية؛ يلزم إثبات عملية الإتلاف الآمن."),
        ("TPC-GOV-008", "THIQAH", "HIGH", "LOW", "ISO27001; NCA-aligned",
         0, 0, 88, 1, 1, 84.0,
         "Government digital business-services assessment. Mature integration security; strong governance.",
         "تقييم خدمات الأعمال الرقمية الحكومية. أمن تكامل ناضج؛ حوكمة قوية."),
    ]
    for code, key, inh, res, certs, breach, vulns, posture, mfa, pen, score, fen, far in rows:
        vid = tv[key]
        vname = GOV_BY_KEY.get(key, {}).get("name_en") or key
        status = "APPROVED"
        eid = exists(cur,
            "SELECT id FROM tprm.third_party_cyber_assessments WHERE organisation_id=? AND assessment_code=?",
            ORG, code)
        if eid is None:
            cur.execute(
                """INSERT INTO tprm.third_party_cyber_assessments
                     (organisation_id, external_id, assessment_code, vendor_id, vendor_name,
                      vendor_country, inherent_cyber_risk, scope_description_en, scope_description_ar,
                      certifications, external_posture_score, breach_history_24m,
                      critical_vulns_open, mfa_enforced, pen_test_within_12m,
                      residual_risk_tier, risk_score, findings_en, findings_ar,
                      treatment_plan_en, treatment_plan_ar, next_review_due, assessed_at,
                      assessor_name, approved_at, approver_name, status, is_deleted, is_active,
                      created_by, row_version)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,SYSUTCDATETIME(),?,?,0,1,?,0)""",
                ORG, "EXT-" + code, code, vid, vname, "SA", inh,
                "Third-party cyber due diligence per NCA ECC domain 4 (external-party cybersecurity) and CCC.",
                "العناية الواجبة السيبرانية للطرف الخارجي وفق الضوابط الأساسية المجال الرابع والضوابط السحابية.",
                certs, posture, breach, vulns, mfa, pen, res, score,
                fen, far,
                "Remediate open vulns; enforce MFA; complete pen-test." if res in ("ELEVATED", "HIGH") else "Maintain controls; annual re-assessment.",
                "معالجة الثغرات وتفعيل المصادقة وإكمال اختبار الاختراق." if res in ("ELEVATED", "HIGH") else "الحفاظ على الضوابط وإعادة التقييم سنوياً.",
                "2027-03-31", "TPRM Cyber Assessor", PROC_OWNER, status, U_RANALYST)
            bump("tprm.third_party_cyber_assessments", True)
        else:
            bump("tprm.third_party_cyber_assessments", False)


# =============================================================================
#  tprm.questionnaire_templates  (guard: org + template_code + version)
# =============================================================================
def seed_tprm_template(cur) -> int:
    code, ver = "QT-GOV-TPRM", "1.0"
    tid = exists(cur,
        "SELECT id FROM tprm.questionnaire_templates WHERE organisation_id=? AND template_code=? AND version=?",
        ORG, code, ver)
    if tid is None:
        cur.execute(
            """INSERT INTO tprm.questionnaire_templates
                 (organisation_id, external_id, template_code, version, name_en, name_ar,
                  description_en, description_ar, category, applicable_to,
                  threshold_low_max, threshold_medium_max, threshold_high_max,
                  status, activated_at, created_by, is_deleted)
               VALUES (?,?,?,?,?,?,?,?,'COMPLIANCE',?,30,60,80,'ACTIVE',SYSUTCDATETIME(),?,0)""",
            ORG, "EXT-" + code, code, ver,
            "Government Third-Party & Outsourcing Due Diligence",
            "العناية الواجبة للأطراف الخارجية والإسناد الحكومي",
            "Due-diligence questionnaire for Etimad-procured government suppliers: GTPL/Etimad eligibility, NCA ECC-4 cyber, NDMO data-sharing & residency, PDPL processor obligations, NCEMA continuity, Nazaha integrity, and local content.",
            "استبيان العناية الواجبة للموردين الحكوميين عبر اعتماد: الأهلية النظامية، الأمن السيبراني، مشاركة البيانات والإقامة، التزامات المعالج، الاستمرارية، النزاهة، والمحتوى المحلي.",
            "Government suppliers & outsourcing arrangements", "Procurement / TPRM")
        tid = exists(cur,
            "SELECT id FROM tprm.questionnaire_templates WHERE organisation_id=? AND template_code=? AND version=?",
            ORG, code, ver)
        bump("tprm.questionnaire_templates", True)
    else:
        bump("tprm.questionnaire_templates", False)
    return tid


# =============================================================================
#  tprm.questionnaire_questions  (guard: template_id + question_code)
# =============================================================================
def seed_tprm_questions(cur, tid: int) -> list[dict]:
    # (section_code, section_en, section_ar, q_code, q_en, q_ar, type, weight, invert, options)
    qs = [
        ("ELIG", "Eligibility & Procurement", "الأهلية والشراء",
         "Q01", "Is the supplier registered and the award valid on the Etimad platform under GTPL?",
         "هل المورد مسجّل والترسية سارية على منصة اعتماد وفق نظام المنافسات؟",
         "BOOLEAN", 1.0, 0, None),
        ("ELIG", "Eligibility & Procurement", "الأهلية والشراء",
         "Q02", "What is the committed local-content percentage (Vision 2030)?",
         "ما نسبة المحتوى المحلي الملتزم بها (رؤية 2030)؟",
         "SELECT", 1.0, 0, ["<20%", "20-40%", "40-60%", ">60%"]),
        ("CYBER", "Cybersecurity (NCA ECC-4)", "الأمن السيبراني",
         "Q03", "Rate coverage of NCA ECC domain-4 third-party/cloud sub-controls.",
         "قيّم تغطية الضوابط الفرعية للمجال الرابع من الضوابط الأساسية.",
         "SCALE_1_5", 2.0, 0, None),
        ("CYBER", "Cybersecurity (NCA ECC-4)", "الأمن السيبراني",
         "Q04", "Is multi-factor authentication enforced for all privileged access?",
         "هل المصادقة متعددة العوامل مفعّلة لكل وصول مميّز؟",
         "BOOLEAN", 1.5, 0, None),
        ("CYBER", "Cybersecurity (NCA ECC-4)", "الأمن السيبراني",
         "Q05", "Has an independent penetration test been performed within the last 12 months?",
         "هل أُجري اختبار اختراق مستقل خلال آخر 12 شهراً؟",
         "BOOLEAN", 1.0, 0, None),
        ("DATA", "Data Residency & Sharing (NDMO)", "إقامة البيانات ومشاركتها",
         "Q06", "Is all government data hosted and processed in-Kingdom per NDMO?",
         "هل تُستضاف وتُعالَج كل البيانات الحكومية داخل المملكة وفق مكتب إدارة البيانات؟",
         "BOOLEAN", 2.0, 0, None),
        ("DATA", "Data Residency & Sharing (NDMO)", "إقامة البيانات ومشاركتها",
         "Q07", "Which national data classification levels does the supplier handle?",
         "ما مستويات التصنيف الوطني للبيانات التي يتعامل معها المورد؟",
         "SELECT", 1.0, 0, ["Public", "Internal", "Confidential", "Top Secret"]),
        ("PDPL", "Personal Data Protection (PDPL)", "حماية البيانات الشخصية",
         "Q08", "Is a PDPL-compliant data-processing agreement (DPA) signed?",
         "هل وُقّعت اتفاقية معالجة بيانات متوافقة مع نظام حماية البيانات الشخصية؟",
         "BOOLEAN", 1.5, 0, None),
        ("PDPL", "Personal Data Protection (PDPL)", "حماية البيانات الشخصية",
         "Q09", "Rate the supplier's sub-processor flow-down and oversight controls.",
         "قيّم ضوابط بنود التعاقد من الباطن والرقابة لدى المورد.",
         "SCALE_1_5", 1.0, 0, None),
        ("BCM", "Continuity (NCEMA / ISO 22301)", "الاستمرارية",
         "Q10", "Has a BCP/DR exercise been tested within the last 12 months?",
         "هل اختُبرت خطة الاستمرارية/التعافي خلال آخر 12 شهراً؟",
         "BOOLEAN", 1.5, 0, None),
        ("BCM", "Continuity (NCEMA / ISO 22301)", "الاستمرارية",
         "Q11", "What recovery-time objective (RTO) does the supplier commit to?",
         "ما هدف زمن التعافي الذي يلتزم به المورد؟",
         "SELECT", 1.0, 0, ["<=1h", "<=4h", "<=24h", ">24h"]),
        ("INTEG", "Integrity (Nazaha)", "النزاهة",
         "Q12", "Has the supplier provided a conflict-of-interest declaration per Nazaha?",
         "هل قدّم المورد إقرار تعارض المصالح وفق نزاهة؟",
         "BOOLEAN", 1.0, 0, None),
        ("INTEG", "Integrity (Nazaha)", "النزاهة",
         "Q13", "Rate the maturity of the supplier's anti-bribery & integrity programme.",
         "قيّم نضج برنامج مكافحة الرشوة والنزاهة لدى المورد.",
         "SCALE_1_5", 1.0, 0, None),
        ("FIN", "Financial & Operational", "المالية والتشغيلية",
         "Q14", "Provide a brief note on the supplier's financial stability and references.",
         "اذكر ملاحظة موجزة عن الاستقرار المالي والمراجع للمورد.",
         "TEXT", 0.5, 0, None),
    ]
    out = []
    for i, (sc, sen, sar, qc, qen, qar, qtype, weight, invert, opts) in enumerate(qs, start=1):
        qid = exists(cur,
            "SELECT id FROM tprm.questionnaire_questions WHERE template_id=? AND question_code=?",
            tid, qc)
        if qid is None:
            cur.execute(
                """INSERT INTO tprm.questionnaire_questions
                     (organisation_id, template_id, section_code, section_name_en,
                      section_name_ar, question_code, question_en, question_ar,
                      answer_type, options_json, weight, invert_score, mandatory,
                      sequence_order, guidance_en, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, tid, sc, sen, sar, qc, qen, qar, qtype,
                j(opts) if opts else None, weight, invert,
                0 if qtype == "TEXT" else 1, i * 10,
                "Score 'No'/low as adverse for control questions.")
            qid = exists(cur,
                "SELECT id FROM tprm.questionnaire_questions WHERE template_id=? AND question_code=?",
                tid, qc)
            bump("tprm.questionnaire_questions", True)
        else:
            bump("tprm.questionnaire_questions", False)
        out.append(dict(id=qid, code=qc, type=qtype, weight=weight))
    return out


# =============================================================================
#  tprm.vendor_assessments  (guard: org + vendor_id + assessment_date)
#  Returns the flagship GovCloud assessment id for the response set.
# =============================================================================
def seed_tprm_vendor_assessments(cur, tv: dict[str, int], tid: int,
                                 total_q: int) -> int:
    rows = [
        ("GOVCLOUD", "2026-02-12", 90.0, "LOW", "COMPLETED", "COMPLETED",
         total_q, total_q, 90.0, "Flagship government cyber DD via QT-GOV-TPRM; fully answered."),
        ("MSSP", "2026-02-18", 78.0, "MEDIUM", "COMPLETED", "COMPLETED",
         total_q, total_q, 78.0, "Managed-SOC DD via QT-GOV-TPRM."),
        ("SI", "2026-03-02", 58.0, "HIGH", "IN_PROGRESS", "IN_PROGRESS",
         7, total_q, None, "System-integrator DD in progress; cyber gaps."),
        ("ELM", "2026-02-22", 88.0, "LOW", "COMPLETED", "COMPLETED",
         total_q, total_q, 88.0, "Identity-platform DD via QT-GOV-TPRM."),
        ("CONTACT", "2026-01-25", 74.0, "MEDIUM", "COMPLETED", "COMPLETED",
         total_q, total_q, 74.0, "Contact-centre DD via QT-GOV-TPRM."),
        ("FM", "2026-03-06", 66.0, "MEDIUM", "COMPLETED", "COMPLETED",
         total_q, total_q, 66.0, "Facilities DD via QT-GOV-TPRM."),
    ]
    govcloud_aid = None
    for key, adate, score, rating, status, wf, answered, total, pct, findings in rows:
        vid = tv[key]
        aid = exists(cur,
            "SELECT id FROM tprm.vendor_assessments WHERE organisation_id=? AND vendor_id=? AND assessment_date=?",
            ORG, vid, adate)
        if aid is None:
            cur.execute(
                """INSERT INTO tprm.vendor_assessments
                     (organisation_id, vendor_id, assessment_date, assessor, risk_score,
                      risk_rating, status, findings, next_review_date, workflow_status,
                      template_id, answered_count, total_questions, computed_score_pct,
                      is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, vid, adate, "TPRM Analyst", score, rating, status, findings,
                "2027-02-28", wf, tid, answered, total, pct)
            aid = exists(cur,
                "SELECT id FROM tprm.vendor_assessments WHERE organisation_id=? AND vendor_id=? AND assessment_date=?",
                ORG, vid, adate)
            bump("tprm.vendor_assessments", True)
        else:
            bump("tprm.vendor_assessments", False)
        if key == "GOVCLOUD":
            govcloud_aid = aid
    return govcloud_aid


# =============================================================================
#  tprm.questionnaire_responses  (guard: vendor_assessment_id + question_id)
#  Full answered set for the flagship GovCloud assessment.
# =============================================================================
def seed_tprm_responses(cur, tid: int, aid: int, questions: list[dict]) -> None:
    # answer per question code for GovCloud (a strong, near-perfect profile)
    answers = {
        "Q01": ("Yes", 5.0), "Q02": (">60%", 5.0), "Q03": ("5", 5.0),
        "Q04": ("Yes", 5.0), "Q05": ("Yes", 5.0), "Q06": ("Yes", 5.0),
        "Q07": ("Confidential", 4.0), "Q08": ("Yes", 5.0), "Q09": ("4", 4.0),
        "Q10": ("Yes", 5.0), "Q11": ("<=4h", 4.0), "Q12": ("Yes", 5.0),
        "Q13": ("4", 4.0),
        "Q14": ("Sovereign government cloud; strong references across public sector.", None),
    }
    notes = {
        "Q06": ("EXT-GovCloud-NDMO-2026", "Hosted in Riyadh national region; NDMO agreement on file."),
        "Q08": ("EXT-GovCloud-DPA-2026", "PDPL DPA signed 2026-04."),
        "Q05": ("EXT-GovCloud-PT-2026", "Independent pen-test report Q1 2026."),
    }
    for q in questions:
        code = q["code"]
        av, pts = answers.get(code, (None, None))
        max_pts = 5.0
        weighted = (pts * q["weight"]) if pts is not None else None
        evref, note = notes.get(code, (None, None))
        eid = exists(cur,
            "SELECT id FROM tprm.questionnaire_responses WHERE vendor_assessment_id=? AND question_id=?",
            aid, q["id"])
        if eid is None:
            cur.execute(
                """INSERT INTO tprm.questionnaire_responses
                     (organisation_id, vendor_assessment_id, question_id, template_id,
                      answer_value, scored_points, max_points, weighted_points,
                      answer_notes_en, evidence_ref, not_applicable, answered_by,
                      answered_at, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,0,?,SYSUTCDATETIME(),0)""",
                ORG, aid, q["id"], tid, av, pts, max_pts, weighted,
                note, evref, "TPRM Analyst")
            bump("tprm.questionnaire_responses", True)
        else:
            bump("tprm.questionnaire_responses", False)


# =============================================================================
#  tprm.outsourcing_arrangement  (guard: org + arrangement_code)
# =============================================================================
def seed_tprm_outsourcing(cur, tv: dict[str, int]) -> dict[str, int]:
    ids: dict[str, int] = {}
    # (code, key, title_en, title_ar, service_type, is_material, critical_function,
    #  personal_data, dpa_signed, lifecycle, basis_en, basis_ar)
    rows = [
        ("TPRM-OUT-GOV-01", "MSSP", "Managed SOC / security monitoring outsourcing",
         "إسناد مركز العمليات الأمنية المُدار", "MANAGED_SECURITY", 1, "CYBERSECURITY",
         0, 0, "LIVE",
         "Material: continuous protection of public-service systems; loss would impair cyber defence and continuity of public services.",
         "جوهري: حماية مستمرة لأنظمة الخدمات العامة؛ تعطّله يضعف الدفاع السيبراني واستمرارية الخدمات."),
        ("TPRM-OUT-GOV-02", "GOVCLOUD", "GovCloud hosting of beneficiary services",
         "استضافة خدمات المستفيدين على السحابة الحكومية", "CLOUD_SAAS", 1, "SERVICE_DELIVERY",
         1, 1, "LIVE",
         "Material: hosts beneficiary-facing e-services and personal data; in-Kingdom; outage halts public service delivery.",
         "جوهري: يستضيف خدمات المستفيدين والبيانات الشخصية داخل المملكة؛ انقطاعه يوقف تقديم الخدمة العامة."),
        ("TPRM-OUT-GOV-03", "FM", "Facilities management of HQ + data centre",
         "إدارة مرافق المقر ومركز البيانات", "FACILITIES", 1, "FACILITIES",
         0, 0, "LIVE",
         "Material: physical continuity of HQ and the data hall; failure threatens uptime of hosted services.",
         "جوهري: الاستمرارية المادية للمقر وقاعة البيانات؛ تعطّله يهدد توفر الخدمات المستضافة."),
        ("TPRM-OUT-GOV-04", "CONTACT", "Beneficiary contact-centre outsourcing",
         "إسناد مركز اتصال المستفيدين", "CALL_CENTRE", 1, "SERVICE_DELIVERY",
         1, 1, "LIVE",
         "Material: primary channel for beneficiary support; processes personal data via recordings.",
         "جوهري: القناة الرئيسة لدعم المستفيدين؛ يعالج بيانات شخصية عبر التسجيلات."),
        ("TPRM-OUT-GOV-05", "SI", "System integration of the licensing platform",
         "تكامل منصة التراخيص", "IT_INFRASTRUCTURE", 0, "NA",
         0, 0, "ACTIVE",
         "Non-material: project-based integration; reversible with knowledge escrow.",
         "غير جوهري: تكامل مشاريعي قابل للاسترجاع عبر ضمان المعرفة."),
        ("TPRM-OUT-GOV-06", "DIGI", "Records digitisation & archiving outsourcing",
         "إسناد رقمنة وأرشفة الوثائق", "BACK_OFFICE_PROCESSING", 0, "NA",
         1, 1, "ACTIVE",
         "Non-material but processes personal data; PDPL DPA required.",
         "غير جوهري لكنه يعالج بيانات شخصية؛ تلزم اتفاقية معالجة وفق النظام."),
    ]
    for code, key, ten, tar, stype, material, critfn, pdata, dpa, lifecycle, ben, bar in rows:
        vid = tv[key]
        aid = exists(cur,
            "SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id=? AND arrangement_code=?",
            ORG, code)
        if aid is None:
            cur.execute(
                """INSERT INTO tprm.outsourcing_arrangement
                     (organisation_id, arrangement_code, title_en, title_ar,
                      description_en, description_ar, service_type, primary_vendor_id,
                      is_material, materiality_basis_en, materiality_basis_ar,
                      sama_non_objection_required, sama_non_objection_obtained,
                      contract_start_date, contract_end_date, contract_value_sar_annual,
                      critical_function, country_of_processing, data_residency_kdc,
                      personal_data_processed, pdpl_dpa_signed, lifecycle_stage,
                      owner_user_id, sponsor_user_id, next_review_due_date,
                      created_by_user_id, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,0,0,?,?,?,?,'SA',1,?,?,?,?,?,?,?,0)""",
                ORG, code, ten, tar,
                f"Etimad-procured government outsourcing arrangement. Owned by {PROC_OWNER}.",
                f"ترتيب إسناد حكومي عبر اعتماد. تملكه {PROC_OWNER}.",
                stype, vid, material, ben, bar,
                "2026-04-01", "2029-03-31", 9500000 if key == "GOVCLOUD" else 5000000,
                critfn, pdata, dpa, lifecycle,
                U_RMGR, U_ADMIN, "2027-03-31", U_RMGR)
            aid = exists(cur,
                "SELECT id FROM tprm.outsourcing_arrangement WHERE organisation_id=? AND arrangement_code=?",
                ORG, code)
            bump("tprm.outsourcing_arrangement", True)
        else:
            bump("tprm.outsourcing_arrangement", False)
        ids[key] = aid
    return ids


# =============================================================================
#  tprm.outsourcing_due_diligence  (guard: org + arrangement_id + assessment_date)
# =============================================================================
def seed_tprm_dd(cur, oa: dict[str, int]) -> None:
    # key, fin, op, cyber, bcm, pdpl, rep, recommendation, rationale_en, rationale_ar
    rows = [
        ("GOVCLOUD", 90, 92, 95, 90, 95, 90, "APPROVE",
         "Strong across all axes; NCA ECC-4/CCC compliant, in-Kingdom, PDPL DPA signed, tested DR.",
         "قوي في كل المحاور؛ متوافق مع الضوابط، داخل المملكة، اتفاقية معالجة موقعة، تعافٍ مُختبر."),
        ("MSSP", 82, 88, 85, 80, 84, 85, "APPROVE",
         "NCA-licensed, mature SOC; minor sub-processor oversight actions.",
         "مرخّص من الهيئة، مركز عمليات ناضج؛ إجراءات بسيطة لرقابة التعاقد من الباطن."),
        ("FM", 78, 80, 66, 62, 70, 75, "CONDITIONAL",
         "Approved subject to closing continuity gaps (chiller redundancy) and IT-hygiene.",
         "موافقة مشروطة بإغلاق فجوات الاستمرارية (تكرار التبريد) والنظافة التقنية."),
        ("SI", 75, 82, 58, 70, 72, 70, "CONDITIONAL",
         "Conditional on remediating open critical vulns, enforcing MFA and completing a pen-test.",
         "مشروطة بمعالجة الثغرات الحرجة وتفعيل المصادقة وإكمال اختبار الاختراق."),
        ("CONTACT", 80, 84, 74, 76, 82, 80, "APPROVE",
         "Adequate; in-Kingdom recording residency and PDPL consent controls satisfactory.",
         "مناسب؛ بقاء التسجيلات داخل المملكة وضوابط الموافقة وفق النظام مُرضية."),
        ("DIGI", 76, 78, 68, 66, 64, 74, "CONDITIONAL",
         "Conditional on evidencing PDPL secure-destruction and NDMO data-return processes.",
         "مشروطة بإثبات الإتلاف الآمن وإعادة البيانات وفق النظام ومكتب إدارة البيانات."),
    ]
    for key, fin, op, cyb, bcm, pdpl, rep, rec, ren, rar in rows:
        aid = oa[key]
        comp = round((fin + op + cyb + bcm + pdpl + rep) / 6.0, 2)
        adate = "2026-03-15"
        eid = exists(cur,
            "SELECT id FROM tprm.outsourcing_due_diligence WHERE organisation_id=? AND arrangement_id=? AND assessment_date=?",
            ORG, aid, adate)
        if eid is None:
            cur.execute(
                """INSERT INTO tprm.outsourcing_due_diligence
                     (organisation_id, arrangement_id, assessment_date, assessor_user_id,
                      financial_health_score, operational_capability_score, cybersecurity_score,
                      bcm_score, pdpl_compliance_score, reputation_score, composite_score,
                      recommendation, rationale_en, rationale_ar, created_by_user_id, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, aid, adate, U_RANALYST, fin, op, cyb, bcm, pdpl, rep, comp,
                rec, ren, rar, U_RANALYST)
            bump("tprm.outsourcing_due_diligence", True)
        else:
            bump("tprm.outsourcing_due_diligence", False)


# =============================================================================
#  tprm.outsourcing_exit_plan  (guard: org + arrangement_id) — UQ enforces 1 each
# =============================================================================
def seed_tprm_exit(cur, oa: dict[str, int]) -> None:
    rows = [
        ("GOVCLOUD", "Sustained SLA breach, licence loss, or data-repatriation directive.",
         "إخفاق مستمر في اتفاقية الخدمة أو فقد الترخيص أو توجيه بإعادة البيانات.",
         ["Freeze workloads", "Export to national DC", "Crypto-erase tenancy", "Validate services"],
         "Repatriate data to national DC with crypto-erase per NDMO/PDPL.",
         "إعادة البيانات لمركز البيانات الوطني مع المحو التشفيري وفق مكتب إدارة البيانات والنظام.",
         "ANNUAL", "2026-03-20"),
        ("MSSP", "Termination, repeated SLA breaches, or licence lapse.",
         "إنهاء أو إخفاقات متكررة أو انقضاء الترخيص.",
         ["Transfer playbooks", "Migrate SIEM", "Seat in-house analysts", "Cutover"],
         "Handover SOC operations to in-house team.",
         "تسليم عمليات مركز العمليات للفريق الداخلي.",
         "ANNUAL", "2026-03-22"),
        ("FM", "Contract end, safety failure, or termination for cause.",
         "نهاية العقد أو فشل السلامة أو الإنهاء.",
         ["Award alternate FM", "Asset handover", "Parallel cover", "Cutover"],
         "Transition to alternate FM provider.",
         "الانتقال إلى مزود بديل لإدارة المرافق.",
         "BIANNUAL", None),
        ("CONTACT", "Contract end, PDPL non-compliance, or quality failure.",
         "نهاية العقد أو عدم الالتزام بالنظام أو فشل الجودة.",
         ["Export recordings/CRM in-Kingdom", "Handover scripts", "Transfer IVR", "Cutover"],
         "Return beneficiary data in-Kingdom; handover knowledge base.",
         "إعادة بيانات المستفيدين داخل المملكة وتسليم قاعدة المعرفة.",
         "NEVER_TESTED", None),
        ("SI", "Insolvency, persistent vulns, or termination.",
         "الإعسار أو ثغرات مستمرة أو الإنهاء.",
         ["Release escrow", "Knowledge transfer", "Re-document", "Cutover support"],
         "Release source/config escrow with knowledge transfer.",
         "تحرير ضمان الشيفرة مع نقل المعرفة.",
         "NEVER_TESTED", None),
    ]
    for key, ten, tar, steps, dren, drar, freq, tested in rows:
        aid = oa[key]
        eid = exists(cur,
            "SELECT id FROM tprm.outsourcing_exit_plan WHERE organisation_id=? AND arrangement_id=?",
            ORG, aid)
        if eid is None:
            cur.execute(
                """INSERT INTO tprm.outsourcing_exit_plan
                     (organisation_id, arrangement_id, trigger_conditions_en,
                      trigger_conditions_ar, exit_steps_json, data_repatriation_plan_en,
                      data_repatriation_plan_ar, test_frequency, last_tested_date,
                      created_by_user_id, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, aid, ten, tar, j(steps), dren, drar, freq, tested, U_RMGR)
            bump("tprm.outsourcing_exit_plan", True)
        else:
            bump("tprm.outsourcing_exit_plan", False)


# =============================================================================
#  tprm.outsourcing_monitoring_event  (guard: org + arrangement_id + event_type + event_date)
# =============================================================================
def seed_tprm_monitoring(cur, oa: dict[str, int]) -> None:
    rows = [
        ("GOVCLOUD", "KPI_REVIEW", "2026-04-30", "INFO",
         "Quarterly KPI review: availability 99.95%, no material incidents.",
         "مراجعة مؤشرات ربعية: التوفر 99.95% دون حوادث جوهرية.", None, 1),
        ("MSSP", "SLA_BREACH", "2026-03-18", "MAJOR",
         "MTTD SLA breached on a suspicious authentication burst; remediation tracked.",
         "إخفاق زمن الاكتشاف على دفعة مصادقة مشبوهة؛ متابعة المعالجة.",
         "Escalated; provider corrective-action plan agreed.", 1),
        ("FM", "CONTRACT_AMENDMENT", "2026-05-10", "MINOR",
         "Contract amended to add a new building to the FM scope.",
         "تعديل العقد لإضافة مبنى جديد لنطاق إدارة المرافق.",
         "Amendment processed via Etimad.", 1),
        ("SI", "FINANCIAL_HEALTH_FLAG", "2026-04-05", "MODERATE",
         "Financial-health flag raised on the integrator; monitoring increased.",
         "تنبيه على الوضع المالي للمكامل؛ زيادة المتابعة.",
         "Quarterly financial check-ins added.", 0),
        ("CONTACT", "REGULATORY_QUERY", "2026-04-12", "MODERATE",
         "PDPL query on call-recording consent handling; response prepared.",
         "استفسار نظامي حول معالجة موافقة تسجيل المكالمات؛ تجهيز الرد.",
         "DPA addendum on consent clarified.", 1),
        ("GOVCLOUD", "KPI_REVIEW", "2026-01-31", "INFO",
         "Q1 KPI review: onboarding milestones met.",
         "مراجعة مؤشرات الربع الأول: تحقق معالم التأهيل.", None, 1),
    ]
    for key, etype, edate, sev, den, dar, action, resolved in rows:
        aid = oa[key]
        eid = exists(cur,
            "SELECT id FROM tprm.outsourcing_monitoring_event WHERE organisation_id=? AND arrangement_id=? "
            "AND event_type=? AND event_date=?",
            ORG, aid, etype, edate)
        if eid is None:
            cur.execute(
                """INSERT INTO tprm.outsourcing_monitoring_event
                     (organisation_id, arrangement_id, event_type, event_date, severity,
                      description_en, description_ar, action_taken_en, action_taken_ar,
                      resolved, resolved_at, created_by_user_id, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, aid, etype, edate, sev, den, dar, action, action, resolved,
                "2026-04-20T10:00:00" if resolved else None, U_RMGR)
            bump("tprm.outsourcing_monitoring_event", True)
        else:
            bump("tprm.outsourcing_monitoring_event", False)


# =============================================================================
#  tprm.vendor_bcp_campaign  (guard: org + campaign_code)
# =============================================================================
def seed_tprm_bcp_campaign(cur) -> int:
    code = "VBCP-GOV-2026"
    cid = exists(cur,
        "SELECT id FROM tprm.vendor_bcp_campaign WHERE organisation_id=? AND campaign_code=?",
        ORG, code)
    if cid is None:
        cur.execute(
            """INSERT INTO tprm.vendor_bcp_campaign
                 (organisation_id, campaign_code, title_en, title_ar, description_en,
                  description_ar, cycle_year, cycle_quarter, opened_at, closes_at,
                  status, mandatory_threshold, regulator_reference, created_by_user_id, is_deleted)
               VALUES (?,?,?,?,?,?,2026,'Q2',SYSUTCDATETIME(),?,'OPEN','CRITICAL_ONLY',?,?,0)""",
            ORG, code,
            "Critical Government Supplier Continuity Attestation 2026",
            "إقرار استمرارية الموردين الحكوميين الحرجين 2026",
            "NCEMA/ISO 22301-driven attestation cycle inviting critical government suppliers to attest RTO/MTPD and last BCP/DR test.",
            "دورة إقرار وفق المركز الوطني وأيزو 22301 تدعو الموردين الحكوميين الحرجين لإقرار أهداف التعافي وآخر اختبار للاستمرارية.",
            "2026-09-30T23:59:00",
            "NCEMA; ISO 22301", U_RMGR)
        cid = exists(cur,
            "SELECT id FROM tprm.vendor_bcp_campaign WHERE organisation_id=? AND campaign_code=?",
            ORG, code)
        bump("tprm.vendor_bcp_campaign", True)
    else:
        bump("tprm.vendor_bcp_campaign", False)
    return cid


# =============================================================================
#  tprm.vendor_bcp_attestation  (guard: campaign_id + vendor_id)
# =============================================================================
def seed_tprm_bcp_attestation(cur, cid: int, tv: dict[str, int]) -> None:
    rows = [
        ("GOVCLOUD", "ACCEPTED", 4, 24, 4, 24, 0, 0, 1, "2026-02-01", "2026-02-05",
         "GovCloud attestation accepted; DR tested; within contractual RTO/MTPD.",
         "قُبل إقرار السحابة الحكومية؛ تم اختبار التعافي؛ ضمن الأهداف التعاقدية."),
        ("MSSP", "SUBMITTED", 1, 8, 1, 8, 0, 0, 1, "2026-02-10", None,
         "MSSP attestation submitted; RTO <=1h; last test recent.",
         "قُدّم إقرار مزود الأمن؛ هدف التعافي ساعة؛ آخر اختبار حديث."),
        ("FM", "SUBMITTED", 6, 48, 2, 24, 1, 1, 1, "2026-03-01", None,
         "FM attestation submitted with RTO breach (attested 6h vs contractual 2h).",
         "قُدّم إقرار إدارة المرافق مع تجاوز هدف التعافي (6 ساعات مقابل ساعتين تعاقدياً)."),
        ("CONTACT", "OVERDUE", None, None, 4, 24, 0, 0, None, None, None,
         "Contact-centre attestation overdue; escalated.",
         "إقرار مركز الاتصال متأخر؛ تم التصعيد."),
    ]
    for key, status, rto_a, mtpd_a, rto_c, mtpd_c, rto_b, mtpd_b, tested, bcp_test, dr_test, cen, car in rows:
        vid = tv[key]
        eid = exists(cur,
            "SELECT id FROM tprm.vendor_bcp_attestation WHERE campaign_id=? AND vendor_id=?",
            cid, vid)
        if eid is None:
            submitted = status in ("SUBMITTED", "ACCEPTED")
            cur.execute(
                """INSERT INTO tprm.vendor_bcp_attestation
                     (organisation_id, campaign_id, vendor_id, invited_at, submitted_at,
                      rto_hours_attested, mtpd_hours_attested, contractual_rto_hours,
                      contractual_mtpd_hours, rto_breach, mtpd_breach, has_tested_bcp,
                      last_bcp_test_date, last_dr_test_date, status, reviewer_comment_en,
                      reviewer_comment_ar, reviewed_by_user_id, reviewed_at, is_deleted)
                   VALUES (?,?,?,SYSUTCDATETIME(),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, cid, vid,
                "2026-02-15T10:00:00" if submitted else None,
                rto_a, mtpd_a, rto_c, mtpd_c, rto_b, mtpd_b, tested,
                bcp_test, dr_test, status, cen, car,
                U_RMGR if status == "ACCEPTED" else None,
                "2026-02-20T10:00:00" if status == "ACCEPTED" else None)
            bump("tprm.vendor_bcp_attestation", True)
        else:
            bump("tprm.vendor_bcp_attestation", False)


# =============================================================================
#  outsourcing.outsourcing_arrangement  (guard: org + reference)
# =============================================================================
def seed_out_arrangement(cur) -> dict[str, int]:
    ids: dict[str, int] = {}
    # ref, key, prov_en, prov_ar, title_en, title_ar, service_category, materiality,
    #  status, risk_rating, annual_spend
    rows = [
        ("OUT-GOV-001", "GOVCLOUD", "Deem National Cloud", "ديم السحابة الوطنية",
         "GovCloud hosting of beneficiary e-services", "استضافة خدمات المستفيدين على السحابة الحكومية",
         "CLOUD", "MATERIAL", "LIVE", "HIGH", 9500000),
        ("OUT-GOV-002", "MSSP", "National Managed Security Services", "الخدمات الأمنية المُدارة الوطنية",
         "NCA-licensed managed SOC", "مركز العمليات الأمنية المُدار المرخّص",
         "IT_INFRASTRUCTURE", "MATERIAL", "LIVE", "HIGH", 6200000),
        ("OUT-GOV-003", "FM", "Almajal Facilities Management", "المجال لإدارة المرافق",
         "Facilities management of HQ + data centre", "إدارة مرافق المقر ومركز البيانات",
         "OTHER", "MATERIAL", "LIVE", "MEDIUM", 14800000),
        ("OUT-GOV-004", "CONTACT", "Markaz Contact Centre Services", "مركز لخدمات مركز الاتصال",
         "Beneficiary contact-centre operations", "تشغيل مركز اتصال المستفيدين",
         "CALL_CENTRE", "MATERIAL", "BOARD_APPROVED", "MEDIUM", 5400000),
    ]
    for ref, key, pen, par, ten, tar, cat, mat, status, risk, spend in rows:
        aid = exists(cur,
            "SELECT id FROM outsourcing.outsourcing_arrangement WHERE organisation_id=? AND reference=?",
            ORG, ref)
        if aid is None:
            live = status == "LIVE"
            cur.execute(
                """INSERT INTO outsourcing.outsourcing_arrangement
                     (organisation_id, reference, provider_legal_name_en, provider_legal_name_ar,
                      provider_country, provider_jurisdiction, service_title_en, service_title_ar,
                      service_description_en, service_description_ar, service_category,
                      materiality, is_core_activity, status, risk_rating, risk_assessed_at,
                      board_approved_at, live_at, contract_start, contract_end, annual_spend_sar,
                      next_review_due, review_cadence_months, relationship_owner_user_id,
                      risk_owner_user_id, is_deleted)
                   VALUES (?,?,?,?,'SA','SAUDI',?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),
                           SYSUTCDATETIME(),?,?,?,?,?,12,?,?,0)""",
                ORG, ref, pen, par, ten, tar,
                f"Etimad-procured public-authority arrangement. Owner: {PROC_OWNER}.",
                f"ترتيب لهيئة عامة عبر اعتماد. المالك: {PROC_OWNER}.",
                cat, mat, 1 if mat == "MATERIAL" else 0, status, risk,
                "2026-04-01" if live else None,
                "2026-04-01", "2029-03-31", spend, "2027-03-31",
                U_RMGR, U_RMGR)
            aid = exists(cur,
                "SELECT id FROM outsourcing.outsourcing_arrangement WHERE organisation_id=? AND reference=?",
                ORG, ref)
            bump("outsourcing.outsourcing_arrangement", True)
        else:
            bump("outsourcing.outsourcing_arrangement", False)
        ids[key] = aid
    return ids


# =============================================================================
#  outsourcing.cloud_service  (guard: org + reference)
# =============================================================================
def seed_out_cloud(cur, oa: dict[str, int]) -> dict[str, int]:
    ids: dict[str, int] = {}
    rows = [
        ("OCS-GOV-001", "GOVCLOUD", "Beneficiary-services workload",
         "حمل خدمات المستفيدين", "IAAS", "PRIVATE", "Deem National Cloud", "ديم",
         "T3", "IN_KINGDOM", "Riyadh National Region", "IN_USE", None, None),
        ("OCS-GOV-002", "GOVCLOUD", "National-data analytics platform",
         "منصة تحليلات البيانات الوطنية", "SAAS", "PRIVATE", "Deem National Cloud", "ديم",
         "T4", "IN_KINGDOM", "Riyadh National Region", "IN_USE", None, None),
        ("OCS-GOV-003", "MSSP", "SOC SIEM backend",
         "خلفية نظام إدارة الأحداث الأمنية", "SAAS", "PRIVATE", "National MSSP", "المزود الوطني",
         "T3", "IN_KINGDOM", "Riyadh", "IN_USE", None, None),
        ("OCS-GOV-004", "GOVCLOUD", "Proposed global SaaS analytics add-on",
         "إضافة تحليلات سحابية عالمية مقترحة", "SAAS", "PUBLIC", "Global SaaS Vendor", "مزود عالمي",
         "T3", "GLOBAL", "EU/US regions", "BLOCKED",
         "Blocked: T3 government data may not leave the Kingdom per NDMO/NCA in-Kingdom residency requirements.",
         "محظور: بيانات حكومية من الفئة الثالثة لا يجوز خروجها من المملكة وفق متطلبات الإقامة لمكتب إدارة البيانات والهيئة."),
    ]
    for ref, key, sen, sar, deploy, model, csp_en, csp_ar, dclass, residency, region, status, blk_en, blk_ar in rows:
        aid = oa[key]
        sid = exists(cur,
            "SELECT id FROM outsourcing.cloud_service WHERE organisation_id=? AND reference=?",
            ORG, ref)
        if sid is None:
            cur.execute(
                """INSERT INTO outsourcing.cloud_service
                     (organisation_id, arrangement_id, reference, service_name_en, service_name_ar,
                      deployment_model, service_model, csp_legal_name_en, csp_legal_name_ar,
                      csp_brand, data_classification, data_residency, csp_region_label, status,
                      classified_at, approved_at, in_use_at, block_reason_en, block_reason_ar,
                      approver_role, supports_pdpl_dpia_ref, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),?,?,?,?,?,?,0)""",
                ORG, aid, ref, sen, sar, deploy, model, csp_en, csp_ar,
                csp_en.split()[0], dclass, residency, region, status,
                "2026-03-25T10:00:00" if status == "IN_USE" else None,
                "2026-04-01" if status == "IN_USE" else None,
                blk_en, blk_ar,
                "Deputy for Digital & Data",
                "DPIA-" + ref, )
            sid = exists(cur,
                "SELECT id FROM outsourcing.cloud_service WHERE organisation_id=? AND reference=?",
                ORG, ref)
            bump("outsourcing.cloud_service", True)
        else:
            bump("outsourcing.cloud_service", False)
        ids[ref] = sid
    return ids


# =============================================================================
#  outsourcing.ongoing_review  (guard: org + reference)
# =============================================================================
def seed_out_review(cur, oa: dict[str, int]) -> dict[str, int]:
    ids: dict[str, int] = {}
    rows = [
        ("ORV-GOV-001", "GOVCLOUD", "COMPLETED", "GREEN",
         "Annual GovCloud oversight review: controls effective, SLAs met, DR tested.",
         "مراجعة إشرافية سنوية للسحابة الحكومية: الضوابط فعّالة والاتفاقيات مستوفاة والتعافي مُختبر."),
        ("ORV-GOV-002", "MSSP", "IN_PROGRESS", None,
         "Managed-SOC oversight review in progress; reviewing SLA-breach corrective actions.",
         "مراجعة إشرافية لمزود الأمن قيد التنفيذ؛ مراجعة الإجراءات التصحيحية لإخفاق الاتفاقية."),
        ("ORV-GOV-003", "FM", "WITH_FINDINGS", "AMBER",
         "FM oversight review: continuity and SLA gaps identified; CAPA linked.",
         "مراجعة إشرافية لإدارة المرافق: فجوات استمرارية واتفاقية؛ مرتبطة بإجراء تصحيحي."),
        ("ORV-GOV-004", "CONTACT", "SCHEDULED", None,
         "Contact-centre oversight review scheduled for the period.",
         "مراجعة إشرافية لمركز الاتصال مجدولة للفترة."),
    ]
    for ref, key, status, rag, fen, far in rows:
        aid = oa[key]
        rid = exists(cur,
            "SELECT id FROM outsourcing.ongoing_review WHERE organisation_id=? AND reference=?",
            ORG, ref)
        if rid is None:
            done = status == "COMPLETED"
            cur.execute(
                """INSERT INTO outsourcing.ongoing_review
                     (organisation_id, arrangement_id, reference, review_period_label,
                      status, due_date, started_at, completed_at, outcome_rag,
                      findings_summary_en, findings_summary_ar, reviewer_user_id, is_deleted)
                   VALUES (?,?,?,'2026-Annual',?,?,?,?,?,?,?,?,0)""",
                ORG, aid, ref, status, "2026-06-30",
                "2026-05-01T09:00:00" if status not in ("SCHEDULED",) else None,
                "2026-05-20T17:00:00" if done else None, rag,
                fen, far, U_RMGR)
            rid = exists(cur,
                "SELECT id FROM outsourcing.ongoing_review WHERE organisation_id=? AND reference=?",
                ORG, ref)
            bump("outsourcing.ongoing_review", True)
        else:
            bump("outsourcing.ongoing_review", False)
        ids[ref] = rid
    return ids


# =============================================================================
#  outsourcing.exit_plan  (guard: org + reference)
# =============================================================================
def seed_out_exit(cur, oa: dict[str, int]) -> dict[str, int]:
    ids: dict[str, int] = {}
    rows = [
        ("OEX-GOV-001", "GOVCLOUD", "REHEARSED",
         "GovCloud exit: repatriate data to on-prem national DC; crypto-erase tenancy.",
         "خروج السحابة الحكومية: إعادة البيانات لمركز البيانات الوطني الداخلي ومحو المستأجر تشفيرياً.",
         120, "On-premise National Data Centre (DR)", "مركز البيانات الوطني الداخلي (التعافي)"),
        ("OEX-GOV-002", "MSSP", "APPROVED",
         "Managed-SOC handover to in-house SOC team.",
         "تسليم مركز العمليات الأمنية للفريق الداخلي.",
         90, "In-house SOC", "مركز العمليات الداخلي"),
        ("OEX-GOV-003", "FM", "READY",
         "FM transition to alternate facilities-management provider.",
         "انتقال إدارة المرافق إلى مزود بديل.",
         60, "Alternate FM provider", "مزود بديل لإدارة المرافق"),
        ("OEX-GOV-004", "CONTACT", "DRAFT",
         "Contact-centre exit: data/script handover and number porting.",
         "خروج مركز الاتصال: تسليم البيانات والنصوص ونقل الأرقام.",
         90, "Alternate contact-centre provider", "مزود بديل لمركز الاتصال"),
    ]
    for ref, key, status, sen, sar, days, alt_en, alt_ar in rows:
        aid = oa[key]
        eid = exists(cur,
            "SELECT id FROM outsourcing.exit_plan WHERE organisation_id=? AND reference=?",
            ORG, ref)
        if eid is None:
            cur.execute(
                """INSERT INTO outsourcing.exit_plan
                     (organisation_id, arrangement_id, reference, status, summary_en,
                      summary_ar, target_migration_days, alternative_provider_en,
                      alternative_provider_ar, approved_at, rehearsed_at, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, aid, ref, status, sen, sar, days, alt_en, alt_ar,
                "2026-04-15T10:00:00" if status in ("APPROVED", "REHEARSED", "READY") else None,
                "2026-04-20" if status == "REHEARSED" else None)
            eid = exists(cur,
                "SELECT id FROM outsourcing.exit_plan WHERE organisation_id=? AND reference=?",
                ORG, ref)
            bump("outsourcing.exit_plan", True)
        else:
            bump("outsourcing.exit_plan", False)
        ids[ref] = eid
    return ids


# =============================================================================
#  outsourcing.outsourcing_event  (guard: org + event_type + occurred_at + arrangement/cloud/review/exit)
# =============================================================================
def seed_out_events(cur, oa: dict[str, int], oc: dict[str, int],
                    orv: dict[str, int], oex: dict[str, int]) -> None:
    # (event_type, occurred_at, arrangement_key, cloud_ref, review_ref, exit_ref, status_after, note_en, note_ar)
    rows = [
        ("ARRANGEMENT_REGISTERED", "2026-01-10T09:00:00", "GOVCLOUD", None, None, None, "PROPOSED",
         "GovCloud arrangement registered following Etimad award.",
         "تسجيل ترتيب السحابة الحكومية عقب الترسية عبر اعتماد."),
        ("ARRANGEMENT_TRANSITIONED_RISK_ASSESSED", "2026-02-15T09:00:00", "GOVCLOUD", None, None, None, "RISK_ASSESSED",
         "GovCloud risk-assessed; material, in-Kingdom.",
         "تقييم مخاطر السحابة الحكومية؛ جوهري وداخل المملكة."),
        ("ARRANGEMENT_TRANSITIONED_LIVE", "2026-04-01T09:00:00", "GOVCLOUD", None, None, None, "LIVE",
         "GovCloud went live hosting beneficiary services.",
         "تشغيل السحابة الحكومية لاستضافة خدمات المستفيدين."),
        ("ARRANGEMENT_TRANSITIONED_LIVE", "2026-04-01T09:30:00", "MSSP", None, None, None, "LIVE",
         "Managed SOC went live.", "تشغيل مركز العمليات الأمنية المُدار."),
        ("CLOUD_SERVICE_BLOCKED", "2026-03-28T11:00:00", "GOVCLOUD", "OCS-GOV-004", None, None, "BLOCKED",
         "Global SaaS add-on blocked on in-Kingdom residency (NDMO/NCA).",
         "حظر الإضافة السحابية العالمية لاشتراط الإقامة داخل المملكة."),
        ("REVIEW_WITH_FINDINGS", "2026-05-20T17:00:00", "FM", None, "ORV-GOV-003", None, "WITH_FINDINGS",
         "FM oversight review closed with findings (continuity/SLA gaps).",
         "إغلاق المراجعة الإشرافية لإدارة المرافق بملاحظات (فجوات استمرارية واتفاقية)."),
    ]
    for etype, occ, akey, cref, rref, eref, status_after, nen, nar in rows:
        aid = oa.get(akey) if akey else None
        cid = oc.get(cref) if cref else None
        rid = orv.get(rref) if rref else None
        eid_fk = oex.get(eref) if eref else None
        # idempotency guard: same type + time + arrangement
        dup = exists(cur,
            "SELECT id FROM outsourcing.outsourcing_event WHERE organisation_id=? AND event_type=? "
            "AND occurred_at=? AND ISNULL(arrangement_id,0)=ISNULL(?,0) AND ISNULL(cloud_service_id,0)=ISNULL(?,0)",
            ORG, etype, occ, aid, cid)
        if dup is None:
            cur.execute(
                """INSERT INTO outsourcing.outsourcing_event
                     (organisation_id, arrangement_id, cloud_service_id, review_id,
                      exit_plan_id, occurred_at, event_type, actor_user_id, actor_name,
                      status_after, note_en, note_ar, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0)""",
                ORG, aid, cid, rid, eid_fk, occ, etype, U_RMGR,
                "Procurement Officer / موظف المشتريات", status_after, nen, nar)
            bump("outsourcing.outsourcing_event", True)
        else:
            bump("outsourcing.outsourcing_event", False)


# =============================================================================
#  main
# =============================================================================
def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    print("Seeding Saudi-government TPRM / SCM / Outsourcing data (org 1)\n")

    # scm
    sv = seed_scm_vendors(cur)
    seed_scm_assessments(cur, sv)
    seed_scm_onboarding(cur, sv)
    sc = seed_scm_contracts(cur, sv)
    seed_scm_slas(cur, sv, sc)
    seed_scm_dependencies(cur, sv)
    seed_scm_exit_plans(cur, sv)
    seed_scm_resilience(cur, sv)
    seed_scm_alternates(cur, sv)
    seed_scm_fourth_party(cur, sv)
    seed_scm_clauses(cur)
    seed_scm_incidents(cur, sv)

    # tprm
    tv = seed_tprm_vendors(cur)
    seed_tprm_cyber(cur, tv)
    tid = seed_tprm_template(cur)
    questions = seed_tprm_questions(cur, tid)
    total_q = len(questions)
    govcloud_aid = seed_tprm_vendor_assessments(cur, tv, tid, total_q)
    seed_tprm_responses(cur, tid, govcloud_aid, questions)
    oa_t = seed_tprm_outsourcing(cur, tv)
    seed_tprm_dd(cur, oa_t)
    seed_tprm_exit(cur, oa_t)
    seed_tprm_monitoring(cur, oa_t)
    bcp_cid = seed_tprm_bcp_campaign(cur)
    seed_tprm_bcp_attestation(cur, bcp_cid, tv)

    # outsourcing
    oa = seed_out_arrangement(cur)
    oc = seed_out_cloud(cur, oa)
    orv = seed_out_review(cur, oa)
    oex = seed_out_exit(cur, oa)
    seed_out_events(cur, oa, oc, orv, oex)

    # ── summary ──────────────────────────────────────────────────────────────
    print("Per-table inserted / skipped (already present):\n")
    tot_ins = tot_skip = 0
    for tbl in sorted(STATS):
        ins, skip = STATS[tbl]
        tot_ins += ins
        tot_skip += skip
        print(f"  {tbl:<42} +{ins:<4} ~{skip}")
    print(f"\nTOTAL inserted={tot_ins}, skipped(existing)={tot_skip}")
    if BLOCKERS:
        print("\nBLOCKERS:")
        for b in BLOCKERS:
            print("  ! " + b)
    else:
        print("\nNo blockers.")

    # ── verification counts (org 1) ──────────────────────────────────────────
    print("\nVerification — org 1 row counts in seeded tables:")
    for sch, tbl in [
        ("scm", "vendors"), ("scm", "vendor_assessments"), ("scm", "vendor_onboarding"),
        ("scm", "contracts"), ("scm", "slas"), ("scm", "dependencies"),
        ("scm", "exit_plans"), ("scm", "supplier_resilience_tests"),
        ("scm", "alternate_suppliers"), ("scm", "fourth_party_exposures"),
        ("scm", "contract_clauses"), ("scm", "supplier_incidents"),
        ("tprm", "vendors"), ("tprm", "third_party_cyber_assessments"),
        ("tprm", "vendor_assessments"), ("tprm", "questionnaire_templates"),
        ("tprm", "questionnaire_questions"), ("tprm", "questionnaire_responses"),
        ("tprm", "outsourcing_arrangement"), ("tprm", "outsourcing_due_diligence"),
        ("tprm", "outsourcing_exit_plan"), ("tprm", "outsourcing_monitoring_event"),
        ("tprm", "vendor_bcp_campaign"), ("tprm", "vendor_bcp_attestation"),
        ("outsourcing", "outsourcing_arrangement"), ("outsourcing", "cloud_service"),
        ("outsourcing", "ongoing_review"), ("outsourcing", "exit_plan"),
        ("outsourcing", "outsourcing_event"),
    ]:
        cur.execute(f"SELECT COUNT(*) FROM {sch}.{tbl} WHERE organisation_id=?", ORG)
        print(f"  {sch}.{tbl:<36} {cur.fetchone()[0]}")

    conn.close()


if __name__ == "__main__":
    main()
