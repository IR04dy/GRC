# -*- coding: utf-8 -*-
"""Seed the dev DB with real-world day-to-day Saudi-native GRC data.

This script targets tables that already exist (created by the Python
bootstrap scripts and Flyway-style migrations) but are sparse enough
that pages render with a handful of demo rows and look "empty". It
adds realistic content for a Saudi Bank / TASI listed company tenant
across:

    privacy   — PDPL Articles 4/5/6/20/25 records
    cyber     — assets, control assessments, incidents
    bcm       — BIA records, recovery operations, exercises
    scm/tprm  — supplier onboarding, vendor assessments
    compliance — assessments, regulatory changes
    sust(ESG)  — material topics, climate risks, SGI commitments
    ndi       — gaps, improvement-plan items
    reporting — report_definitions (empty)
    workflow  — workflow_definitions (empty)

It is idempotent — every INSERT is guarded by `WHERE NOT EXISTS (… SELECT 1 …)`.
Run it any number of times; it won't double-seed.

Usage:
    python seed_real_data.py

Targets the default tenant id = 1 (Trusted Security Solutions) and
also seeds id = 2 (Aramco) / id = 3 (STC Bank) where it makes sense.
"""
from __future__ import annotations

import sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

CONN_STR = (
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)

conn = pyodbc.connect(CONN_STR)
conn.autocommit = True
cur = conn.cursor()

ORG_TSS, ORG_ARAMCO, ORG_STC = 1, 2, 3

inserted = 0
skipped = 0
errors = 0


def run(sql: str, *params, label: str = '') -> bool:
    """Execute `IF NOT EXISTS ... INSERT` SQL and report inserted vs skipped.

    `cur.rowcount` after the `IF NOT EXISTS (SELECT 1 ...) INSERT` pattern is
    unreliable on SQL Server — it reflects the SELECT inside EXISTS (always 1
    if a row matched), not whether the INSERT actually ran. That made the
    progress counter wildly over-report on re-runs.

    Fix: append `SELECT @@ROWCOUNT AS inserted_count` to the SQL we pass in,
    fetch that single value, and decide insert-vs-skip from it. `@@ROWCOUNT`
    *is* trustworthy here — it's the row count of the LAST statement in the
    batch, which is the INSERT (when it ran) or the (zero-row) SELECT inside
    EXISTS (when the row already existed).
    """
    global inserted, skipped, errors
    try:
        # Append a sentinel SELECT so we can read @@ROWCOUNT from the INSERT.
        # We strip a trailing semicolon if present so the concatenation works.
        wrapped = sql.rstrip().rstrip(';') + ";\nSELECT @@ROWCOUNT AS inserted_count;"
        cur.execute(wrapped, params)
        row = cur.fetchone()
        n = int(row[0]) if row and row[0] is not None else 0
        if n > 0:
            inserted += n
            if label:
                print(f"  +{n} {label}")
            return True
        else:
            skipped += 1
            return False
    except Exception as e:
        errors += 1
        msg = str(e)[:300]
        print(f"  ! [{label}] {msg}")
        return False


def section(name: str):
    print(f"\n── {name} ──────────────────────────────────────────")


# ──────────────────────────────────────────────────────────────────────
# Privacy / PDPL  (Articles 4 / 5 / 6 / 20 / 25)
# ──────────────────────────────────────────────────────────────────────
section("privacy.consents — PDPL Art.5/6 consent records")

CONSENTS = [
    # (purpose_en, purpose_ar, channel, status)
    ("Marketing communications via SMS",       "الرسائل التسويقية عبر الرسائل القصيرة",        "SMS",   "ACTIVE"),
    ("Email newsletter subscription",          "الاشتراك في النشرة البريدية",                  "EMAIL", "ACTIVE"),
    ("Behavioural analytics on mobile app",    "تحليلات السلوك على تطبيق الجوّال",            "APP",   "ACTIVE"),
    ("Profiling for credit-card upgrade offer","التنميط لعروض ترقية البطاقة الائتمانية",      "WEB",   "ACTIVE"),
    ("Push notifications for transactions",    "الإشعارات الفورية للمعاملات",                  "APP",   "ACTIVE"),
    ("Sharing balance with finance aggregator","مشاركة الرصيد مع مزوّد التجميع المالي",       "API",   "ACTIVE"),
    ("Personalised investment recommendations","التوصيات الاستثمارية المخصّصة",                "APP",   "WITHDRAWN"),
    ("WhatsApp service alerts",                "تنبيهات الخدمة عبر واتساب",                     "WHATSAPP","ACTIVE"),
    ("Biometric login (fingerprint)",          "الدخول البيومتري (البصمة)",                     "APP",   "ACTIVE"),
    ("Targeted ads on partner network",        "الإعلانات المستهدفة على شبكة الشركاء",         "WEB",   "WITHDRAWN"),
    ("Branch CCTV recording",                  "تسجيل كاميرات المراقبة في الفروع",             "BRANCH","ACTIVE"),
    ("Call-centre voice recording",            "تسجيل المكالمات في مركز الاتصال",              "CALL",  "ACTIVE"),
    ("Loyalty programme analytics",            "تحليلات برنامج الولاء",                         "APP",   "ACTIVE"),
    ("Cross-border data transfer to AWS BHR",  "نقل البيانات الحدودي إلى AWS البحرين",         "BACKEND","ACTIVE"),
    ("Open-banking data sharing (SAMA OBF)",  "مشاركة البيانات في المنظومة المفتوحة لساما",   "API",   "ACTIVE"),
]
for purpose_en, purpose_ar, channel, status in CONSENTS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM privacy.consents
                       WHERE purpose_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO privacy.consents (external_id, organisation_id, purpose_en, purpose_ar,
            channel, version_label, effective_date, status, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, 'v1', GETDATE(), ?, 0, GETDATE())
        """,
        purpose_en, ORG_TSS,
        ORG_TSS, purpose_en, purpose_ar, channel, status,
        label=f"consent: {purpose_en[:40]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Cyber assets — Saudi banking technology stack
# ──────────────────────────────────────────────────────────────────────
section("cyber.cyber_assets — production banking systems")

ASSETS = [
    # (name, type, biz_owner, tech_owner, classification, criticality, ip, hostname, os)
    ("Core Banking — T24",            "APPLICATION", "Mohammed Al-Otaibi",   "Khalid Al-Shehri", "RESTRICTED",   "TIER_1", "10.10.20.11",  "core-t24-prod-01",  "RHEL 9.2"),
    ("SADAD Payment Gateway",         "APPLICATION", "Sarah Al-Qahtani",     "Faisal Al-Dosari", "RESTRICTED",   "TIER_1", "10.10.20.22",  "sadad-gw-prod",     "RHEL 9.2"),
    ("Internet Banking Web Front",    "APPLICATION", "Reema Al-Asiri",       "Abdulaziz Al-Sayed","CONFIDENTIAL","TIER_1", "10.10.30.11",  "ib-web-prod-01",    "Ubuntu 22.04"),
    ("Mobile Banking iOS Bundle",     "APPLICATION", "Layla Al-Ghamdi",      "Yousef Al-Mansouri","CONFIDENTIAL","TIER_1", None,           "ios-bundle",        None),
    ("Mobile Banking Android Bundle", "APPLICATION", "Layla Al-Ghamdi",      "Yousef Al-Mansouri","CONFIDENTIAL","TIER_1", None,           "android-bundle",    None),
    ("Oracle Exadata Cluster",        "DATABASE",    "Khalid Al-Shehri",     "Saud Al-Harbi",     "RESTRICTED", "TIER_1", "10.10.20.50",  "exadata-cluster",   "Exadata 22.1"),
    ("Active Directory Domain",       "INFRASTRUCTURE","Mohammed Al-Otaibi", "Bandar Al-Rashidi", "RESTRICTED", "TIER_1", "10.10.10.5",   "ad-dc-prod-01",     "Windows Server 2022"),
    ("Splunk SIEM Cluster",           "INFRASTRUCTURE","Faisal Al-Dosari",   "Bandar Al-Rashidi", "CONFIDENTIAL","TIER_2","10.10.70.10",  "splunk-prod-01",    "RHEL 9.2"),
    ("Palo Alto NGFW HA Pair",        "INFRASTRUCTURE","Mohammed Al-Otaibi", "Saud Al-Harbi",     "RESTRICTED", "TIER_1", "10.10.1.1",   "pa-fw-prod-ha",     "PAN-OS 11.0"),
    ("F5 BIG-IP LTM Pair",            "INFRASTRUCTURE","Khalid Al-Shehri",   "Saud Al-Harbi",     "CONFIDENTIAL","TIER_1","10.10.1.10",   "f5-ltm-prod-ha",    "TMOS 17.1"),
    ("ATM Switch — Cardless Cash",    "APPLICATION", "Sarah Al-Qahtani",     "Faisal Al-Dosari",  "RESTRICTED", "TIER_1", "10.10.40.5",   "atm-switch-prod",   "RHEL 8.6"),
    ("KYC + AML Engine — Actimize",   "APPLICATION", "Reema Al-Asiri",       "Yousef Al-Mansouri","RESTRICTED", "TIER_1", "10.10.50.20",  "actimize-prod-01",  "Windows Server 2022"),
    ("Document Mgmt — OpenText",      "APPLICATION", "Layla Al-Ghamdi",      "Abdulaziz Al-Sayed","CONFIDENTIAL","TIER_2","10.10.60.10",  "opentext-prod",     "Windows Server 2019"),
    ("Card Mgmt System — Way4",       "APPLICATION", "Sarah Al-Qahtani",     "Bandar Al-Rashidi", "RESTRICTED", "TIER_1", "10.10.40.15",  "way4-prod",         "Solaris 11.4"),
    ("Treasury — Murex MX.3",         "APPLICATION", "Mohammed Al-Otaibi",   "Khalid Al-Shehri",  "RESTRICTED", "TIER_1", "10.10.80.10",  "murex-prod-01",     "RHEL 9.2"),
    ("HR — Oracle Fusion HCM",        "APPLICATION", "Layla Al-Ghamdi",      "Abdulaziz Al-Sayed","INTERNAL",   "TIER_3", None,           "oracle-fusion-hcm", None),
    ("ITSM — ServiceNow",             "APPLICATION", "Faisal Al-Dosari",     "Yousef Al-Mansouri","INTERNAL",   "TIER_3", None,           "servicenow-saas",   None),
    ("Email — Microsoft 365 EX5",     "APPLICATION", "Reema Al-Asiri",       "Bandar Al-Rashidi", "CONFIDENTIAL","TIER_2",None,           "m365-tenant",       None),
    ("Backup — Commvault DR Site",    "INFRASTRUCTURE","Khalid Al-Shehri",   "Saud Al-Harbi",     "CONFIDENTIAL","TIER_1","10.20.10.10",  "commvault-dr-01",   "Windows Server 2022"),
    ("AWS Production Account (Bahrain)","INFRASTRUCTURE","Mohammed Al-Otaibi","Bandar Al-Rashidi", "RESTRICTED", "TIER_1", None,           "aws-prod-me-south-1",None),
]
for name, atype, biz, tech, cls, crit, ip, host, os_name in ASSETS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM cyber.cyber_assets
                       WHERE asset_name = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO cyber.cyber_assets (external_id, organisation_id, asset_name, asset_type,
            business_owner, technical_owner, classification, criticality, ip_address, hostname,
            operating_system, linked_vulnerabilities_count, linked_incidents_count,
            linked_controls_count, status, is_deleted, created_at)
        VALUES (LOWER(REPLACE(NEWID(), '-', '')), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0,
            'ACTIVE', 0, GETDATE())
        """,
        name, ORG_TSS,
        ORG_TSS, name, atype, biz, tech, cls, crit, ip, host, os_name,
        label=f"asset: {name}",
    )

# ──────────────────────────────────────────────────────────────────────
# Reporting — pre-built report definitions
# ──────────────────────────────────────────────────────────────────────
section("reporting.report_definitions — Board / SAMA / NCA / PDPL pre-built reports")

REPORTS = [
    ("Risk Heatmap — Quarterly Board Pack",  "خريطة المخاطر — حزمة مجلس الإدارة الربعية", "RISK_HEATMAP",      "PDF"),
    ("KRI Breach Summary",                   "ملخّص اختراق مؤشّرات المخاطر الرئيسية",       "KRI_BREACH",         "XLSX"),
    ("Risk Appetite Compliance",             "الامتثال لإطار شهيّة المخاطر",                  "APPETITE_COMPL",     "PDF"),
    ("PDPL Breach Notification (SDAIA)",     "إخطار خرق البيانات الشخصية (سدايا) — 72 ساعة",  "PDPL_BREACH",        "PDF"),
    ("PDPL RoPA — Article 25 Inventory",     "سجل أنشطة المعالجة — المادة 25",                "PDPL_ROPA",          "XLSX"),
    ("PDPL DSR SLA Tracker — 30 Days",        "تتبّع طلبات أصحاب البيانات — 30 يوماً",         "PDPL_DSR_SLA",       "XLSX"),
    ("SAMA Cyber Maturity Quarterly",        "تقرير النضج السيبراني الربعي — ساما",           "SAMA_CYBER",         "PDF"),
    ("NCA ECC-2 Posture",                    "موقف الامتثال — ECC-2 الهيئة الوطنية للأمن السيبراني","NCA_ECC2_POSTURE","PDF"),
    ("NCA SCC — Cloud Compliance",          "الامتثال للضوابط السحابية — الهيئة الوطنية",     "NCA_CCC",            "PDF"),
    ("Vendor Risk Posture Quarterly",        "موقف مخاطر الموردين الربعي",                    "TPRM_QUARTERLY",     "PDF"),
    ("ISO 22301 Readiness",                  "جاهزية أيزو 22301",                              "ISO22301",           "PDF"),
    ("BCM Exercise Calendar",                "جدول تمارين استمرارية الأعمال",                  "BCM_EXERCISES",      "XLSX"),
    ("CAPA Aging Report",                    "تقرير عمر الإجراءات التصحيحية",                 "CAPA_AGING",         "XLSX"),
    ("Audit Findings Summary",               "ملخّص ملاحظات التدقيق",                          "AUDIT_FINDINGS",     "PDF"),
    ("ESG/Sustainability Annex (CMA Art.91-94)","ملحق الاستدامة — هيئة السوق المالية المواد 91-94","ESG_CMA",         "PDF"),
    ("TCFD Climate Risk Disclosure",         "إفصاح مخاطر المناخ — TCFD",                      "TCFD",               "PDF"),
    ("GHG Inventory — Scopes 1/2/3",         "حصر انبعاثات غازات الدفيئة — النطاقات 1/2/3",   "GHG_INVENTORY",      "XLSX"),
    ("Materiality Matrix",                   "مصفوفة الجوهرية",                                "MATERIALITY",        "PDF"),
    ("Internal Audit Annual Plan",           "خطة التدقيق الداخلي السنوية",                    "IA_PLAN",            "PDF"),
    ("Committee Action-Item Tracker",        "متتبّع بنود الإجراءات للجان",                    "CMTE_ACTIONS",       "XLSX"),
]
for name_en, name_ar, rtype, fmt in REPORTS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM reporting.report_definitions
                       WHERE report_name_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO reporting.report_definitions (external_id, organisation_id,
            report_name_en, report_name_ar, report_type, output_format,
            parameters_json, is_public, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, ?, '{}', 0, 0, GETDATE())
        """,
        name_en, ORG_TSS,
        ORG_TSS, name_en, name_ar, rtype, fmt,
        label=f"report: {name_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Workflow definitions — entity SLA matrix
# ──────────────────────────────────────────────────────────────────────
section("workflow.workflow_definitions — entity SLA matrix")

WFS = [
    ("Risk Treatment Plan Approval",       "اعتماد خطة معالجة المخاطر",                "RISK_TREATMENT",        72),
    ("CAPA Closure Approval",              "اعتماد إغلاق الإجراء التصحيحي",            "CAPA_CLOSURE",          96),
    ("PDPL Data Subject Request",          "طلب صاحب البيانات — نظام حماية البيانات",  "PDPL_DSR",              720),  # 30 days
    ("Cross-Border Transfer Approval",     "اعتماد النقل خارج الحدود",                  "PDPL_CBT",              168),
    ("DPIA Review",                        "مراجعة تقييم أثر الخصوصية",                 "PDPL_DPIA",             240),
    ("Cyber Incident — Major (SAMA 6h)",   "حادث سيبراني — كبير (ساما 6 ساعات)",       "CYBER_INCIDENT_MAJOR",  6),
    ("Cyber Incident — Minor",             "حادث سيبراني — بسيط",                      "CYBER_INCIDENT_MINOR",  48),
    ("Vulnerability — Critical (24h)",     "ثغرة حرجة — 24 ساعة",                       "VULN_CRITICAL",         24),
    ("BCP Plan Approval",                  "اعتماد خطة استمرارية العمل",                "BCP_APPROVAL",          120),
    ("Vendor Onboarding — Critical",       "تأهيل المورّد — حرج",                       "VENDOR_ONBOARD_CRIT",   240),
    ("Vendor Onboarding — Standard",       "تأهيل المورّد — قياسي",                     "VENDOR_ONBOARD",        120),
    ("Vendor Exit Plan Activation",        "تفعيل خطة الخروج للمورّد",                   "VENDOR_EXIT",           168),
    ("Policy Publication Approval",        "اعتماد نشر السياسة",                         "POLICY_PUBLISH",        72),
    ("Regulatory Change Assessment",       "تقييم التغيير التنظيمي",                     "REG_CHANGE",            120),
    ("Audit Finding — High Severity",      "ملاحظة تدقيق — شدّة عالية",                  "AUDIT_FINDING_HIGH",    240),
    ("Audit Finding — Medium",             "ملاحظة تدقيق — متوسطة",                      "AUDIT_FINDING_MED",     720),
    ("Board Pack Review",                  "مراجعة حزمة مجلس الإدارة",                  "BOARD_PACK",            168),
    ("ESG Annex Approval",                 "اعتماد ملحق الاستدامة",                      "ESG_ANNEX",             240),
    ("KRI Breach Escalation",              "تصعيد اختراق مؤشّر مخاطر رئيسي",            "KRI_BREACH",            24),
    ("Risk Acceptance — Senior Mgmt",      "قبول المخاطر — الإدارة العليا",              "RISK_ACCEPT",           120),
]
for name_en, name_ar, ent_type, sla_h in WFS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM workflow.workflow_definitions
                       WHERE entity_type = ? AND organisation_id = ?)
        INSERT INTO workflow.workflow_definitions (external_id, organisation_id,
            name_en, name_ar, entity_type, sla_hours, is_active, is_system, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, ?, 1, 0, GETDATE())
        """,
        ent_type, ORG_TSS,
        ORG_TSS, name_en, name_ar, ent_type, sla_h,
        label=f"workflow: {name_en[:50]} ({sla_h}h)",
    )

# ──────────────────────────────────────────────────────────────────────
# Privacy DSRs — PDPL Article 4 (30-day SLA)
# ──────────────────────────────────────────────────────────────────────
section("privacy.data_subject_requests — PDPL Art.4 DSRs")

DSRS = [
    # (type, requester_name, requester_email, status, days_ago)
    ("ACCESS",      "أحمد عبدالله القحطاني",   "ahmed.q@example.sa",      "RESOLVED",   45),
    ("ACCESS",      "نورة فهد الشمري",           "noura.s@example.sa",      "IN_PROGRESS",  8),
    ("ACCESS",      "فيصل ناصر العتيبي",         "faisal.o@example.sa",     "NEW",          2),
    ("DELETION",    "ريم محمد الزهراني",         "reem.z@example.sa",       "RESOLVED",   60),
    ("DELETION",    "خالد سعد الدوسري",          "khalid.d@example.sa",     "IN_PROGRESS", 12),
    ("DELETION",    "سارة طلال الغامدي",         "sara.g@example.sa",       "ESCALATED",  25),
    ("CORRECTION",  "ياسر صالح المالكي",         "yaser.m@example.sa",      "RESOLVED",   30),
    ("CORRECTION",  "هند بدر المطيري",           "hind.m@example.sa",       "IN_PROGRESS", 5),
    ("PORTABILITY", "عبدالعزيز عبدالرحمن السبيعي","abdulaziz.s@example.sa",  "RESOLVED",   38),
    ("PORTABILITY", "لمى عبدالكريم الحربي",      "lama.h@example.sa",       "NEW",          1),
    ("RESTRICT",    "ماجد طارق الشهري",          "majed.s@example.sa",      "IN_PROGRESS", 14),
    ("OBJECT",      "مريم حسن العنزي",           "maryam.a@example.sa",     "RESOLVED",   22),
    ("ACCESS",      "تركي راشد القحطاني",        "turki.q@example.sa",      "ESCALATED",  28),
    ("DELETION",    "أمل عيسى البلوي",           "amal.b@example.sa",       "REJECTED",   33),
    ("ACCESS",      "بدر عوض العتيبي",            "badr.o@example.sa",       "RESOLVED",   55),
]
for rtype, rname, remail, status, days_ago in DSRS:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM privacy.data_subject_requests
                       WHERE requester_email = ? AND organisation_id = ? AND request_type = ?)
        INSERT INTO privacy.data_subject_requests (external_id, organisation_id,
            request_type, requester_name, requester_email, received_at, due_date, status,
            is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, DATEADD(day, -{days_ago}, GETDATE()),
            DATEADD(day, -{days_ago} + 30, CAST(GETDATE() AS DATE)), ?, 0, DATEADD(day, -{days_ago}, GETDATE()))
        """,
        remail, ORG_TSS, rtype,
        ORG_TSS, rtype, rname, remail, status,
        label=f"DSR: {rtype} — {rname[:30]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Privacy data breaches — PDPL Article 20/21 (72h SDAIA + subject notify)
# ──────────────────────────────────────────────────────────────────────
section("privacy.data_breaches — PDPL Art.20 breaches")

BREACHES = [
    # (title_en, description_en, type, subjects_affected, notify_required, status, days_ago)
    ("Phishing-driven AD account compromise",
     "مهاجم استخدم بريداً تصيّدياً لاختراق حساب موظف خدمات مالية. تأثرت ~120 سجل عملاء.",
     "UNAUTHORISED_ACCESS", 120, True, "NOTIFIED", 90),
    ("Misdirected email batch — KYC documents",
     "أُرسلت 38 رسالة بريد إلكتروني تحتوي على وثائق KYC للعنوان الخاطئ بسبب خطأ في القالب.",
     "ACCIDENTAL_DISCLOSURE", 38, True, "NOTIFIED", 30),
    ("Lost USB drive — branch ops",
     "فقدان USB يحتوي على نسخة احتياطية مشفّرة جزئياً من ملفات Branch Ops. التشفير AES-256.",
     "LOSS_OF_DEVICE", 0, False, "INVESTIGATED", 15),
    ("Vendor exposure — Crowdstrike token leak",
     "تسرّب رمز API لـ Crowdstrike في مستودع GitHub خاص. لا توجد بيانات شخصية مرتبطة مباشرة.",
     "VENDOR_BREACH", 0, False, "CLOSED", 60),
    ("Insider misuse — privileged DBA query log",
     "DBA استعرض سجلات الرواتب دون داعٍ تشغيلي. الإجراء التأديبي قيد التنفيذ.",
     "INSIDER_THREAT", 250, True, "ESCALATED", 7),
    ("Web-app SQL-injection — leaked complaint forms",
     "ثغرة SQL في نموذج الشكاوى أتاحت تنزيل 87 شكوى تشمل بيانات اتصال.",
     "WEB_VULN_EXPLOIT", 87, True, "NOTIFIED", 110),
    ("Misconfigured S3 bucket — marketing PDF cache",
     "حاوية S3 تم تركها عامة لأسبوعين. مراجعة AWS CloudTrail: 4 تحميلات فقط من IP داخلي.",
     "MISCONFIGURATION", 0, False, "CLOSED", 180),
    ("Lost mobile — branch manager",
     "هاتف مدير فرع مفقود. التطبيق مغلق بالبصمة + MDM Wipe تم خلال 2 ساعة.",
     "LOSS_OF_DEVICE", 0, False, "CLOSED", 22),
    ("ATM Skimmer — 3 branches Riyadh",
     "ثلاثة قارئات بطاقات مزورة على أجهزة ATM في الرياض. ~95 بطاقة متأثرة، تم إعادة إصدار البطاقات.",
     "PHYSICAL_TAMPERING", 95, True, "NOTIFIED", 45),
    ("Email spoofing — fake CFO instruction",
     "محاولة تحويل أموال عبر تزوير بريد المدير المالي. الحادث محبط، تأثر بيانات: 0.",
     "PHISHING", 0, False, "INVESTIGATED", 5),
    ("Test data exposed in lower env",
     "بيانات إنتاج اختبارية تحتوي على أسماء وأرقام هواتف ظهرت في بيئة UAT.",
     "DATA_EXPOSURE_LOWER_ENV", 412, True, "RESOLVED", 75),
]
for title, desc, btype, count, notify, status, days_ago in BREACHES:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM privacy.data_breaches
                       WHERE title_en = ? AND organisation_id = ?)
        INSERT INTO privacy.data_breaches (external_id, organisation_id, title_en, description_en,
            breach_type, discovered_at, data_subjects_affected_count, pdpc_notification_required,
            status, is_deleted, created_at, auto_generated)
        VALUES (NEWID(), ?, ?, ?, ?, DATEADD(day, -{days_ago}, GETDATE()), ?, ?, ?, 0,
            DATEADD(day, -{days_ago}, GETDATE()), 0)
        """,
        title, ORG_TSS,
        ORG_TSS, title, desc, btype, count, 1 if notify else 0, status,
        label=f"breach: {title}",
    )

# ──────────────────────────────────────────────────────────────────────
# BIA records — link to existing critical processes
# ──────────────────────────────────────────────────────────────────────
section("bcm.bia_records — BIA assessments for critical processes")

# Lookup process ids dynamically so we link to whatever's in the DB
cur.execute(
    "SELECT TOP 10 id, process_name_en FROM bcm.critical_processes WHERE is_deleted=0 AND organisation_id=? ORDER BY id",
    ORG_TSS,
)
processes = [(r[0], r[1]) for r in cur.fetchall()]
print(f"  found {len(processes)} critical processes")

BIA_PROFILE = [
    # (rto_hours, rpo_hours, mtpd_hours, impact, criticality, financial, operational, regulatory, reputational, risk_level)
    (2, 1, 4, "CRITICAL", "TIER_1", "HIGH",     "HIGH",     "HIGH",     "HIGH",     "CRITICAL"),
    (4, 2, 8, "HIGH",     "TIER_1", "HIGH",     "MEDIUM",   "MEDIUM",   "HIGH",     "HIGH"),
    (8, 4, 24,"MEDIUM",  "TIER_2", "MEDIUM",   "HIGH",     "LOW",      "MEDIUM",   "MEDIUM"),
    (24,8, 72,"MEDIUM",  "TIER_2", "LOW",      "MEDIUM",   "MEDIUM",   "LOW",      "MEDIUM"),
    (48,24,168,"LOW",    "TIER_3", "LOW",      "LOW",      "LOW",      "LOW",      "LOW"),
]
OWNERS = ["Mohammed Al-Otaibi", "Sarah Al-Qahtani", "Khalid Al-Shehri", "Reema Al-Asiri", "Layla Al-Ghamdi"]
BUS = ["Retail Banking", "Operations", "HR", "Finance", "IT Infrastructure"]
for i, (pid, pname) in enumerate(processes[:5]):
    rto, rpo, mtpd, impact, crit, fin, op, reg, rep, risk = BIA_PROFILE[i % len(BIA_PROFILE)]
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM bcm.bia_records
                       WHERE process_id = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO bcm.bia_records (external_id, process_id, assessment_date, rto_hours, rpo_hours,
            mtpd_hours, overall_impact, status, created_at, organisation_id,
            financial_impact, operational_impact, regulatory_impact, reputational_impact,
            criticality, risk_level, owner_name, business_unit, dependencies_count,
            last_review_date, version_number, is_deleted, recovery_tasks_generated, notes)
        VALUES (NEWID(), ?, CAST(GETDATE() AS DATE), ?, ?, ?, ?, 'APPROVED', GETDATE(), ?,
            ?, ?, ?, ?, ?, ?, ?, ?, 3, DATEADD(month, -2, CAST(GETDATE() AS DATE)), 2, 0, 0,
            ?)
        """,
        pid, ORG_TSS,
        pid, rto, rpo, mtpd, impact, ORG_TSS,
        fin, op, reg, rep, crit, risk, OWNERS[i % len(OWNERS)], BUS[i % len(BUS)],
        f"BIA — {pname}: RTO {rto}h / RPO {rpo}h / MTPD {mtpd}h. تمت المراجعة من قبل لجنة BCM.",
        label=f"BIA: {pname} (RTO={rto}h)",
    )

# ──────────────────────────────────────────────────────────────────────
# BCM dependency-cascade topology — deepen the process graph so the
# Sprint-9 cascade simulator (GET /api/v1/bcm/dependency-cascade/{id}) has a
# real multi-tier blast radius to walk. Adds two downstream processes, three
# edges (Core → Retail Payments → Cards; Core → Treasury) and their MTPDs so
# an IT-Infrastructure failure cascades across the 1h / 4h / 8h bands with
# clear MTPD breaches. Parameterised → Arabic + em-dash pass cleanly.
# ──────────────────────────────────────────────────────────────────────
section("bcm cascade topology — downstream processes + edges + MTPDs")

CASCADE_PROCS = [
    # (name_en, name_ar, criticality, business_unit, description_en)
    ("Cards & ATM Switch — mada", "تبديل البطاقات والصرّاف الآلي — مدى", "CRITICAL",
     "Payments & Channels",
     "mada debit-card authorisation and ATM cash-dispense switch; depends on retail-payment rails."),
    ("Treasury & Trade Finance — Murex", "الخزينة وتمويل التجارة — ميوركس", "HIGH",
     "Wholesale Banking",
     "Treasury, FX and trade-finance (LC/guarantees) on Murex MX.3; consumes core-banking balances."),
]
for name_en, name_ar, crit, bu, desc in CASCADE_PROCS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM bcm.critical_processes
                       WHERE organisation_id=? AND process_name_en=?)
        INSERT INTO bcm.critical_processes
            (organisation_id, process_name_en, process_name_ar, criticality, business_unit, description_en)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        ORG_TSS, name_en,
        ORG_TSS, name_en, name_ar, crit, bu, desc,
        label=f"process: {name_en}",
    )

CASCADE_EDGES = [
    # (consumer_like, provider_like, dependency_type, criticality_impact, notes)
    ("Retail Payments%", "Core Banking%", "DATA_FEED", "CRITICAL",
     "SARIE/SADAD/mada posting requires the core-banking ledger."),
    ("Cards & ATM Switch%", "Retail Payments%", "INTERNAL_PROCESS", "CRITICAL",
     "Card authorisation settles over the retail-payment rails."),
    ("Treasury & Trade Finance%", "Core Banking%", "DATA_FEED", "HIGH",
     "Treasury marks-to-market against core-banking positions."),
]
for consumer, provider, dtype, cimpact, notes in CASCADE_EDGES:
    run(
        """
        DECLARE @c BIGINT = (SELECT TOP 1 id FROM bcm.critical_processes WHERE organisation_id=? AND is_deleted=0 AND process_name_en LIKE ?);
        DECLARE @p BIGINT = (SELECT TOP 1 id FROM bcm.critical_processes WHERE organisation_id=? AND is_deleted=0 AND process_name_en LIKE ?);
        IF @c IS NOT NULL AND @p IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM bcm.process_dependencies
            WHERE organisation_id=? AND is_deleted=0 AND process_id=@c AND depends_on_process_id=@p)
        INSERT INTO bcm.process_dependencies
            (organisation_id, process_id, depends_on_process_id, dependency_type, criticality_impact, notes)
        VALUES (?, @c, @p, ?, ?, ?)
        """,
        ORG_TSS, consumer, ORG_TSS, provider, ORG_TSS, ORG_TSS, dtype, cimpact, notes,
        label=f"edge: {consumer} <- {provider}",
    )

CASCADE_BIAS = [
    # (proc_like, mtpd, rto, rpo, criticality, impact, owner, bu, notes)
    ("Retail Payments%", 2, 1, 0, "CRITICAL", "CRITICAL", "Head of Payments", "Payments & Channels",
     "SARIE/SADAD/mada — sub-2h MTPD per SAMA payments availability."),
    ("Cards & ATM Switch%", 4, 2, 1, "CRITICAL", "HIGH", "Head of Cards", "Payments & Channels",
     "mada switch — 4h MTPD; card-present fallback limited."),
    ("Treasury & Trade Finance%", 24, 12, 4, "HIGH", "MEDIUM", "Head of Treasury", "Wholesale Banking",
     "Murex treasury — 24h MTPD; same-day settlement window."),
]
for proc, mtpd, rto, rpo, crit, impact, owner, bu, notes in CASCADE_BIAS:
    run(
        """
        DECLARE @pid BIGINT = (SELECT TOP 1 id FROM bcm.critical_processes WHERE organisation_id=? AND is_deleted=0 AND process_name_en LIKE ?);
        IF @pid IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM bcm.bia_records WHERE organisation_id=? AND is_deleted=0 AND process_id=@pid)
        INSERT INTO bcm.bia_records
            (process_id, mtpd_hours, rto_hours, rpo_hours, criticality, overall_impact, status,
             owner_name, business_unit, organisation_id, notes)
        VALUES (@pid, ?, ?, ?, ?, ?, 'APPROVED', ?, ?, ?, ?)
        """,
        ORG_TSS, proc, ORG_TSS, mtpd, rto, rpo, crit, impact, owner, bu, ORG_TSS, notes,
        label=f"BIA: {proc} (MTPD={mtpd}h)",
    )

# ──────────────────────────────────────────────────────────────────────
# BCM DR-test programme — give the Sprint-9 DR-test trend aggregator
# (GET /api/v1/bcm/dr-test-trend) a believable rolling history. The seed
# DRPs that ship in setup_bcm_db.py cluster in the two most-recent quarters,
# so the rolling-8-quarter chart was almost empty. This adds a Saudi-bank
# DR programme whose `last_tested_at` spreads across the earlier quarters
# with a realistic improving-maturity arc (FAILED / PARTIAL early →
# PASSED later) and all four outcome buckets represented. One DRP per
# critical system; tenant-1 (TSS GRC / Saudi bank) only. Parameterised
# pyodbc → Arabic passes cleanly. row_version is a plain bigint here, so we
# set it explicitly to 0.
# ──────────────────────────────────────────────────────────────────────
section("bcm.dr_plans — Saudi-bank DR-test programme (quarterly trend)")

DR_PLANS = [
    # (code, title_en, title_ar, system, site_type, site_location, site_provider,
    #  owner, rto, rpo, mtpd, tested_at, outcome, notes_en, desc_en)
    ("DRP-SA-CBS", "Core Banking (Temenos T24) — DR plan",
     "خطة التعافي — النظام البنكي الأساسي (تيمينوس T24)", "Temenos T24 Core Banking",
     "ACTIVE_ACTIVE", "Jeddah DR Data Centre", "On-prem", "Head of Core Banking",
     2, 0, 4, "2024-08-20 02:30:00", "FAILED",
     "Q3-2024 failover exercise: ledger replication lag breached RPO; remediation tracked to closure.",
     "Active-active recovery for the T24 core-banking ledger across the Riyadh/Jeddah twin data centres."),
    ("DRP-SA-SADAD", "SADAD Bill-Payment Hub — DR plan",
     "خطة التعافي — منصة سداد للمدفوعات", "SADAD Payment Hub",
     "HOT", "Jeddah DR Data Centre", "On-prem", "Head of Payments",
     4, 1, 8, "2024-11-18 01:00:00", "GAP",
     "Q4-2024 test: hub recovered but biller-reconciliation queue had to be rebuilt manually.",
     "Hot-standby recovery for the SADAD bill-payment hub and biller reconciliation."),
    ("DRP-SA-MADA", "mada Card & ATM Switch — DR plan",
     "خطة التعافي — مفتاح بطاقات مدى والصرّاف الآلي", "mada Card & ATM Switch",
     "ACTIVE_ACTIVE", "Riyadh Primary / Jeddah DR", "On-prem", "Head of Cards",
     2, 0, 4, "2025-02-24 03:15:00", "GAP",
     "Q1-2025 test: switch failover OK; ATM driver re-pointing exceeded the RTO target.",
     "Active-active recovery for the mada debit-card authorisation and ATM cash-dispense switch."),
    ("DRP-SA-MUREX", "Murex Treasury & Trade Finance — DR plan",
     "خطة التعافي — الخزينة وتمويل التجارة (ميوركس)", "Murex MX.3",
     "WARM", "Jeddah DR Data Centre", "On-prem", "Head of Treasury",
     8, 2, 24, "2025-05-19 18:00:00", "PASSED",
     "Q2-2025 exercise passed within RTO/RPO; end-of-day batch validated on the DR instance.",
     "Warm-standby recovery for Murex MX.3 treasury, FX and trade-finance (LC/guarantees)."),
    ("DRP-SA-EXADATA", "Exadata Data Warehouse — DR plan",
     "خطة التعافي — مستودع البيانات (إكسّاداتا)", "Oracle Exadata DW",
     "WARM", "Jeddah DR Data Centre", "On-prem", "Head of Data Platform",
     12, 4, 24, "2025-08-25 22:00:00", "PASSED",
     "Q3-2025 exercise passed; Oracle Data Guard switchover completed within targets.",
     "Warm-standby recovery for the Oracle Exadata regulatory & analytics data warehouse."),
    ("DRP-SA-SWIFT", "SWIFT Gateway (cross-border) — DR plan",
     "خطة التعافي — بوابة سويفت للتحويلات الدولية", "SWIFT Alliance Gateway",
     "HOT", "Riyadh Primary / Jeddah DR", "On-prem", "Head of Correspondent Banking",
     4, 1, 8, "2025-09-15 04:00:00", "FAILED",
     "Q3-2025 test: HSM key ceremony on DR incomplete; message flow not restored within the window.",
     "Hot-standby recovery for the SWIFT Alliance gateway and cross-border message flow."),
    ("DRP-SA-CHANNELS", "Internet & Mobile Banking — DR plan",
     "خطة التعافي — القنوات الرقمية (الإنترنت والجوال)", "Digital Channels Platform",
     "HOT", "Jeddah DR Data Centre", "On-prem", "Head of Digital Channels",
     4, 1, 8, "2025-11-24 05:30:00", "GAP",
     "Q4-2025 test: web/app channels online; push-notification and SADAD inquiry degraded.",
     "Hot-standby recovery for the internet- and mobile-banking digital channels platform."),
    ("DRP-SA-SARIE", "SARIE RTGS Interface — DR plan",
     "خطة التعافي — واجهة نظام سريع للتسوية الفورية", "SARIE RTGS Interface",
     "ACTIVE_ACTIVE", "Riyadh Primary / Jeddah DR", "SAMA network", "Head of Payments",
     2, 0, 4, "2026-01-27 06:00:00", "PASSED",
     "Q1-2026 exercise passed; RTGS interface re-established within the SAMA settlement window.",
     "Active-active recovery for the SARIE real-time gross-settlement interface to SAMA."),
]
for (code, ten, tar, system, site_type, site_loc, site_prov, owner,
     rto, rpo, mtpd, tested, outcome, notes, desc) in DR_PLANS:
    run(
        """
        SET NOCOUNT ON;
        IF NOT EXISTS (SELECT 1 FROM bcm.dr_plans
                       WHERE organisation_id=? AND plan_code=?)
        INSERT INTO bcm.dr_plans
            (organisation_id, plan_code, title_en, title_ar, description_en,
             critical_system_name, dr_site_type, dr_site_location, dr_site_provider, owner,
             rto_hours, rpo_hours, mtpd_hours, status,
             last_tested_at, last_tested_outcome, last_test_notes_en, next_test_due,
             is_deleted, is_active, row_version, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, 'APPROVED',
                CAST(? AS datetime2), ?, ?, DATEADD(MONTH, 12, CAST(? AS date)),
                0, 1, 0, GETDATE(), GETDATE())
        """,
        ORG_TSS, code,
        ORG_TSS, code, ten, tar, desc,
        system, site_type, site_loc, site_prov, owner,
        rto, rpo, mtpd,
        tested, outcome, notes, tested,
        label=f"DRP: {code} ({outcome} @ {tested[:7]})",
    )

# ──────────────────────────────────────────────────────────────────────
# Vendor onboarding rows — link to scm.vendors
# ──────────────────────────────────────────────────────────────────────
section("scm.vendor_onboarding — supplier onboarding pipeline")

cur.execute(
    "SELECT TOP 8 id, name_en FROM scm.vendors WHERE is_deleted=0 AND organisation_id=? ORDER BY id",
    ORG_TSS,
)
vendors = [(r[0], r[1]) for r in cur.fetchall()]
print(f"  found {len(vendors)} vendors")

# Stages per CK_scm_ob_stage: REGISTRATION, CRITICALITY_CLASSIFICATION,
# SECURITY_REVIEW, OPERATIONAL_REVIEW, LEGAL_REVIEW, APPROVAL.
# Status per CK_scm_ob_status: IN_PROGRESS, ON_HOLD, COMPLETED, REJECTED.
STAGES = [
    ("REGISTRATION",              "IN_PROGRESS", 14),
    ("CRITICALITY_CLASSIFICATION","IN_PROGRESS",  7),
    ("SECURITY_REVIEW",           "IN_PROGRESS", 21),
    ("OPERATIONAL_REVIEW",        "IN_PROGRESS", 12),
    ("LEGAL_REVIEW",              "IN_PROGRESS",  5),
    ("APPROVAL",                  "IN_PROGRESS",  3),
    ("APPROVAL",                  "COMPLETED",    0),
    ("SECURITY_REVIEW",           "ON_HOLD",     -3),
]
for i, (vid, vname) in enumerate(vendors):
    stage, status, days_due = STAGES[i % len(STAGES)]
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM scm.vendor_onboarding
                       WHERE vendor_id = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO scm.vendor_onboarding (external_id, organisation_id, vendor_id,
            current_stage, status, due_date, notes, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?,
            DATEADD(day, {days_due}, CAST(GETDATE() AS DATE)),
            ?, 0, GETDATE())
        """,
        vid, ORG_TSS,
        ORG_TSS, vid, stage, status,
        f"تأهيل المورد {vname}. المرحلة الحالية: {stage}.",
        label=f"onboarding: {vname} → {stage}",
    )

# ──────────────────────────────────────────────────────────────────────
# Cyber incidents — SOC operational ledger
# ──────────────────────────────────────────────────────────────────────
section("cyber.cyber_incidents — SOC ledger (resolved + active)")

INCIDENTS = [
    # (title_en, title_ar, type, impact, severity, status, asset, lead, days_ago)
    ("Spear-phishing wave targeting Treasury",
     "موجة تصيد موجّه ضد قسم الخزينة",
     "PHISHING", "HIGH", "CRITICAL", "CLOSED",
     "Email — Microsoft 365 EX5", "Faisal Al-Dosari", 95),
    ("Suspicious PowerShell on retail-banking endpoint",
     "نشاط PowerShell مشبوه على نقطة عميل الأفراد",
     "MALWARE", "MEDIUM", "HIGH", "CLOSED",
     "Active Directory Domain", "Bandar Al-Rashidi", 60),
    ("DDoS — Internet Banking front",
     "هجوم حرمان من الخدمة على واجهة الخدمات المصرفية",
     "DDoS", "HIGH", "CRITICAL", "CLOSED",
     "Internet Banking Web Front", "Saud Al-Harbi", 40),
    ("Unauthorised access attempt to T24 admin console",
     "محاولة وصول غير مخوّل لوحدة إدارة T24",
     "UNAUTHORISED_ACCESS", "CRITICAL", "CRITICAL", "RESOLVED",
     "Core Banking — T24", "Mohammed Al-Otaibi", 22),
    ("Vulnerability exploited — F5 CVE-2024-XYZ",
     "استغلال ثغرة على F5 — CVE-2024-XYZ",
     "VULN_EXPLOIT", "HIGH", "HIGH", "IN_PROGRESS",
     "F5 BIG-IP LTM Pair", "Khalid Al-Shehri", 8),
    ("Suspicious data exfiltration via cloud-storage",
     "اشتباه بتسريب بيانات عبر التخزين السحابي",
     "DATA_EXFIL", "HIGH", "HIGH", "IN_PROGRESS",
     "AWS Production Account (Bahrain)", "Sarah Al-Qahtani", 4),
    ("Brute-force on RDP — exposed to internet",
     "هجوم تخمين كلمات مرور على RDP المعرَّض للإنترنت",
     "BRUTE_FORCE", "MEDIUM", "MEDIUM", "RESOLVED",
     "Backup — Commvault DR Site", "Faisal Al-Dosari", 18),
    ("Ransomware indicator — branch ops endpoint",
     "مؤشّر فدية على نقطة عمليات الفرع",
     "MALWARE", "CRITICAL", "CRITICAL", "CLOSED",
     "Document Mgmt — OpenText", "Mohammed Al-Otaibi", 75),
    ("Insider — privileged token shared on Teams",
     "تسرّب رمز صلاحيات على Teams من موظف",
     "INSIDER_THREAT", "MEDIUM", "HIGH", "IN_REVIEW",
     "Splunk SIEM Cluster", "Bandar Al-Rashidi", 11),
    ("Vendor breach — Crowdstrike token exposure",
     "اختراق المورّد — تسرّب رمز Crowdstrike",
     "VENDOR_BREACH", "MEDIUM", "MEDIUM", "CLOSED",
     "Splunk SIEM Cluster", "Saud Al-Harbi", 105),
    ("DNS poisoning attempt — ATM switch",
     "محاولة تسميم DNS على محوّل أجهزة الصراف الآلي",
     "DNS_ATTACK", "HIGH", "HIGH", "RESOLVED",
     "ATM Switch — Cardless Cash", "Khalid Al-Shehri", 30),
    ("Malicious npm-package detected in CI/CD",
     "اكتشاف حزمة npm خبيثة في خطوط CI/CD",
     "SUPPLY_CHAIN", "MEDIUM", "MEDIUM", "RESOLVED",
     "ITSM — ServiceNow", "Yousef Al-Mansouri", 50),
]
for i, (title_en, title_ar, itype, impact, severity, status, asset, lead, days_ago) in enumerate(INCIDENTS, start=1):
    incident_number = f"INC-{2026}-{1000+i:04d}"
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM cyber.cyber_incidents
                       WHERE title_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO cyber.cyber_incidents (external_id, organisation_id, incident_number,
            title_en, title_ar, incident_type, affected_asset_name, impact, severity,
            workflow_status, detected_at, reported_at,
            resolved_at, incident_lead, notification_sent, bcm_handoff,
            linked_vulnerabilities_count, linked_evidence_count,
            is_deleted, created_at)
        VALUES (LOWER(REPLACE(NEWID(),'-','')), ?, ?, ?, ?, ?, ?, ?, ?, ?,
            DATEADD(day, -{days_ago}, GETDATE()), DATEADD(day, -{days_ago}, GETDATE()),
            {('DATEADD(day, -' + str(max(days_ago - 7, 1)) + ', GETDATE())') if status in ('CLOSED','RESOLVED') else 'NULL'},
            ?, {'1' if severity in ('CRITICAL','HIGH') else '0'},
            {'1' if itype in ('MALWARE','RANSOMWARE','DDoS') else '0'},
            0, 0, 0, DATEADD(day, -{days_ago}, GETDATE()))
        """,
        title_en, ORG_TSS,
        ORG_TSS, incident_number, title_en, title_ar, itype, asset, impact, severity, status,
        lead,
        label=f"incident: {incident_number} {title_en[:40]}",
    )

# ──────────────────────────────────────────────────────────────────────
# BCM exercises — quarterly drills
# ──────────────────────────────────────────────────────────────────────
section("bcm.bcm_exercises — quarterly drills")

EXERCISES = [
    # (title_en, title_ar, exercise_type, status, facilitator, days_ago_or_ahead, result)
    ("Q1 2026 — Data-Centre Failover Drill",       "تمرين Q1 2026 — تجربة التحوّل لمركز البيانات",      "FAILOVER",    "COMPLETED",   "Mohammed Al-Otaibi", -75, "PASS"),
    ("Q2 2026 — Pandemic Tabletop",                 "تمرين Q2 2026 — مكتبي للجائحة",                   "TABLETOP",    "COMPLETED",   "Reema Al-Asiri",     -15, "PASS_WITH_OBSERVATIONS"),
    ("Q3 2026 — Ransomware Live-Fire (SOC)",        "تمرين Q3 2026 — هجوم فدية حي",                   "FUNCTIONAL",  "SCHEDULED",   "Faisal Al-Dosari",    25, None),
    ("Q4 2026 — Full Crisis Activation",            "تمرين Q4 2026 — تفعيل أزمة كاملة",               "FULL_SCALE",  "SCHEDULED",   "Sarah Al-Qahtani",    85, None),
    ("Annual — Treasury Settlement Outage",         "السنوي — انقطاع التسوية في الخزينة",              "FUNCTIONAL",  "COMPLETED",   "Khalid Al-Shehri",   -120, "PASS"),
    ("ATM-Switch Failover Walkthrough",             "محاكاة تحوّل محوّل أجهزة الصراف الآلي",           "WALKTHROUGH", "COMPLETED",   "Bandar Al-Rashidi",   -45, "PASS"),
    ("DR Site Activation (Jeddah → Riyadh)",        "تفعيل موقع التعافي (جدة → الرياض)",               "FAILOVER",    "COMPLETED",   "Saud Al-Harbi",       -90, "PASS"),
    ("Crisis Comms Cascade Test",                   "اختبار شجرة الاتصالات في الأزمة",                  "COMMS",       "COMPLETED",   "Layla Al-Ghamdi",     -30, "PASS"),
    ("SAMA Major-Incident 6-Hour Drill",             "تمرين الحادث الكبير 6 ساعات — ساما",              "REGULATORY",  "COMPLETED",   "Mohammed Al-Otaibi", -150, "PASS"),
    ("Vendor Failure — STC Cloud Service Drill",    "تمرين فشل المورّد — خدمة سحابة STC",              "VENDOR",      "SCHEDULED",   "Sarah Al-Qahtani",    55, None),
]
for title_en, title_ar, etype, status, fac, days_offset, result in EXERCISES:
    exec_date = "NULL" if status == "SCHEDULED" else f"DATEADD(day, {days_offset}, CAST(GETDATE() AS DATE))"
    sched_date = f"DATEADD(day, {days_offset - 7 if status == 'COMPLETED' else days_offset}, CAST(GETDATE() AS DATE))"
    result_val = "NULL" if result is None else f"'{result}'"
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM bcm.bcm_exercises
                       WHERE title_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO bcm.bcm_exercises (external_id, organisation_id, title_en, title_ar,
            exercise_type, scheduled_date, executed_date, status, facilitator,
            objectives, overall_result, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, {sched_date}, {exec_date}, ?, ?,
            'التحقّق من جاهزية الخطة + قياس RTO/RPO الفعلي + ملاحظات لتحسين العملية.',
            {result_val}, 0, GETDATE())
        """,
        title_en, ORG_TSS,
        ORG_TSS, title_en, title_ar, etype, status, fac,
        label=f"exercise: {title_en}",
    )

# ──────────────────────────────────────────────────────────────────────
# Compliance regulatory changes — SAMA / NCA / SDAIA / CMA / ZATCA
# ──────────────────────────────────────────────────────────────────────
section("compliance.regulatory_changes — Saudi authority circulars")

REG_CHANGES = [
    # (authority, title_en, title_ar, citation, applicability, impact, status, days_ago)
    ("SAMA",  "Cloud Computing Outsourcing Framework — Update v2",
              "إطار الحوسبة السحابية والاستعانة بمصادر خارجية — تحديث الإصدار 2",
              "SAMA/CIRC/2026/01", "BANKING", "HIGH",   "IMPLEMENTED",  90),
    ("SAMA",  "Open Banking Phase 3 — Account Information Sharing",
              "المنظومة المفتوحة المرحلة 3 — مشاركة معلومات الحساب",
              "SAMA/CIRC/2026/04", "BANKING", "HIGH",   "IN_REVIEW",    20),
    ("SAMA",  "Cyber Security Framework v1.3 — Incident Reporting 6h SLA",
              "إطار الأمن السيبراني الإصدار 1.3 — تقرير الحوادث ضمن 6 ساعات",
              "SAMA/CSF/1.3",      "BANKING", "CRITICAL","IMPLEMENTED",  180),
    ("NCA",   "Essential Cybersecurity Controls ECC-2",
              "الضوابط الأساسية للأمن السيبراني ECC-2",
              "NCA/ECC-2/2026",    "ALL",     "CRITICAL","IN_PROGRESS",  45),
    ("NCA",   "Cloud Cybersecurity Controls CCC v1.1",
              "ضوابط الأمن السيبراني السحابي CCC v1.1",
              "NCA/CCC/1.1",       "ALL",     "HIGH",   "IN_PROGRESS",   25),
    ("NCA",   "OT Cybersecurity Controls OTCC",
              "ضوابط أمن أنظمة التحكم الصناعي OTCC",
              "NCA/OTCC/2026",     "ALL",     "MEDIUM", "ASSIGNED",      10),
    ("SDAIA", "PDPL — Executive Regulations — Final",
              "نظام حماية البيانات الشخصية — اللائحة التنفيذية — النسخة النهائية",
              "SDAIA/PDPL/EXEC",   "ALL",     "CRITICAL","IMPLEMENTED",  200),
    ("SDAIA", "Cross-Border Transfer Decision — Tier 1 countries",
              "قرار النقل خارج الحدود — دول الفئة الأولى",
              "SDAIA/CBT/2026/01", "ALL",     "HIGH",   "IN_REVIEW",     15),
    ("CMA",   "Sustainability Disclosure Articles 91-94 — Mandatory FY26",
              "إفصاحات الاستدامة المواد 91-94 — إلزامية للعام المالي 2026",
              "CMA/SUST/91-94",    "LISTED",  "HIGH",   "IN_PROGRESS",    60),
    ("CMA",   "Corporate Governance Code — Board Independence Update",
              "نظام حوكمة الشركات — تحديث استقلالية المجلس",
              "CMA/CG/2026",       "LISTED",  "MEDIUM", "IMPLEMENTED",   140),
    ("ZATCA", "Electronic Invoicing Phase 4 — Wave 23",
              "الفوترة الإلكترونية المرحلة 4 — الموجة 23",
              "ZATCA/E-INV/4-23",  "ALL",     "MEDIUM", "IN_PROGRESS",     5),
    ("MOH",   "Patient Privacy & Healthcare Data Sharing — Update",
              "خصوصية المريض ومشاركة بيانات الرعاية الصحية — تحديث",
              "MOH/PP/2026/02",    "HEALTHCARE","MEDIUM","ASSIGNED",      18),
]
for auth, title_en, title_ar, cite, applic, impact, status, days_ago in REG_CHANGES:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM compliance.regulatory_changes
                       WHERE citation = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO compliance.regulatory_changes (external_id, organisation_id, source_authority,
            title_en, title_ar, citation, summary_ar, published_date, effective_date,
            applicability, impact_level, status, controls_updated_count, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, ?, ?, DATEADD(day, -{days_ago}, CAST(GETDATE() AS DATE)),
            DATEADD(day, -{days_ago} + 60, CAST(GETDATE() AS DATE)), ?, ?, ?, 0, 0, GETDATE())
        """,
        cite, ORG_TSS,
        ORG_TSS, auth, title_en, title_ar, cite,
        f"إصدار من {auth} يتطلّب مراجعة ضوابط البنك وإجراء تحديث على السياسات + الأدلة الداعمة.",
        applic, impact, status,
        label=f"regchange: [{auth}] {title_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Regulatory HORIZON feed — in-flight changes for the horizon scanner.
#
# The horizon scanner (GET /api/v1/compliance/regulatory-horizon) only
# surfaces rows whose status is one of the canonical state-machine values
# NEW | APPLICABILITY_ASSESSED | IMPACT_ASSESSED | CONTROLS_UPDATED and
# whose `applicability` is a real enum value. These rows are authored with
# canonical values + explicit effective dates so the horizon view shows a
# full spread across PAST_DUE / IMMINENT / NEAR_TERM / QUARTER / HORIZON.
# ──────────────────────────────────────────────────────────────────────
section("compliance.regulatory_changes — horizon scanner feed (in-flight)")

REG_HORIZON = [
    # (authority, title_en, title_ar, citation, applicability, impact, status, eff_offset_days)
    ("SAMA",  "Counter-Fraud Framework — real-time transaction monitoring mandate",
              "إطار مكافحة الاحتيال — إلزام المراقبة الآنية للمعاملات",
              "SAMA/CFF/2026/HZN",     "APPLICABLE", "CRITICAL", "CONTROLS_UPDATED",       -45),
    ("NCA",   "Essential Cybersecurity Controls ECC-2 — 7-day critical CVE remediation",
              "الضوابط الأساسية للأمن السيبراني ECC-2 — معالجة الثغرات الحرجة خلال 7 أيام",
              "NCA/ECC-2/MTTR/HZN",    "APPLICABLE", "CRITICAL", "IMPACT_ASSESSED",          12),
    ("SDAIA", "PDPL cross-border transfer — standard contractual clauses v1",
              "نقل البيانات خارج الحدود — الشروط التعاقدية النموذجية الإصدار 1",
              "SDAIA/SCC/2026/HZN",    "APPLICABLE", "HIGH",     "APPLICABILITY_ASSESSED",   25),
    ("ZATCA", "E-Invoicing Phase 2 — Wave 24 onboarding",
              "الفوترة الإلكترونية المرحلة 2 — انضمام الموجة 24",
              "ZATCA/E-INV/W24/HZN",   "PARTIAL",    "MEDIUM",   "NEW",                      60),
    ("CMA",   "Sustainability disclosure — limited assurance requirement FY26",
              "إفصاح الاستدامة — اشتراط التأكيد المحدود للعام المالي 2026",
              "CMA/SUST/ASSURE/HZN",   "APPLICABLE", "HIGH",     "IMPACT_ASSESSED",          80),
    ("SAMA",  "Operational Resilience — impact tolerance thresholds for critical services",
              "المرونة التشغيلية — حدود تحمّل الأثر للخدمات الحرجة",
              "SAMA/OPRES/IT/HZN",     "APPLICABLE", "HIGH",     "APPLICABILITY_ASSESSED",  120),
    ("NCA",   "Operational Technology Cybersecurity Controls OTCC-1 — phase 1",
              "ضوابط الأمن السيبراني للأنظمة التشغيلية OTCC-1 — المرحلة 1",
              "NCA/OTCC-1/HZN",        "PARTIAL",    "MEDIUM",   "NEW",                     150),
    ("NCEMA", "Business Continuity Management Standard — NCEMA 7000 refresh",
              "معيار إدارة استمرارية الأعمال — تحديث NCEMA 7000",
              "NCEMA/7000/2027/HZN",   "APPLICABLE", "MEDIUM",   "NEW",                     220),
    ("SDAIA", "AI system registration & risk-tiering — high-risk use cases",
              "تسجيل أنظمة الذكاء الاصطناعي وتصنيف المخاطر — الحالات عالية الخطورة",
              "SDAIA/AI-REG/2027/HZN", "APPLICABLE", "HIGH",     "APPLICABILITY_ASSESSED",  300),
]
for auth, title_en, title_ar, cite, applic, impact, status, eff in REG_HORIZON:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM compliance.regulatory_changes
                       WHERE citation = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO compliance.regulatory_changes (external_id, organisation_id, source_authority,
            title_en, title_ar, citation, summary_ar, published_date, effective_date,
            applicability, impact_level, status, controls_updated_count, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, ?, ?, DATEADD(day, -30, CAST(GETDATE() AS DATE)),
            DATEADD(day, {eff}, CAST(GETDATE() AS DATE)), ?, ?, ?, 0, 0, GETDATE())
        """,
        cite, ORG_TSS,
        ORG_TSS, auth, title_en, title_ar, cite,
        f"تغيير تنظيمي من {auth} قيد المعالجة — يتطلّب تقييم الانطباق وتحديث الضوابط قبل تاريخ النفاذ.",
        applic, impact, status,
        label=f"horizon: [{auth}] {title_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# ESG materiality topics — CMA Art.91-94 themes
# ──────────────────────────────────────────────────────────────────────
section("sust.material_topic — CMA Art.91-94 materiality matrix")

MATERIAL_TOPICS = [
    # (code, category, topic_en, topic_ar, stakeholder, business, tier, status, owner)
    ("MT-001", "ENVIRONMENTAL", "GHG emissions reduction — Scopes 1+2",
                              "خفض انبعاثات غازات الدفيئة — النطاقات 1+2", 4.6, 4.8, "CRITICAL",  "APPROVED",  "Mohammed Al-Otaibi"),
    ("MT-002", "ENVIRONMENTAL", "Energy efficiency in data-centres",
                              "كفاءة الطاقة في مراكز البيانات",                4.2, 4.5, "HIGH",       "APPROVED",  "Khalid Al-Shehri"),
    ("MT-003", "ENVIRONMENTAL", "Water consumption — branches & HQ",
                              "استهلاك المياه — الفروع والمقر",                3.4, 3.0, "MEDIUM",     "APPROVED",  "Reema Al-Asiri"),
    ("MT-004", "ENVIRONMENTAL", "Sustainable financing & green bonds",
                              "التمويل المستدام والصكوك الخضراء",              4.7, 4.6, "CRITICAL",  "APPROVED",  "Sarah Al-Qahtani"),
    ("MT-005", "SOCIAL",      "Saudization & Vision 2030 alignment",
                              "السعودة والتوافق مع رؤية 2030",                 4.8, 4.7, "CRITICAL",  "APPROVED",  "Layla Al-Ghamdi"),
    ("MT-006", "SOCIAL",      "Customer privacy & PDPL compliance",
                              "خصوصية العملاء والامتثال لـ PDPL",              4.9, 4.9, "CRITICAL",  "APPROVED",  "Faisal Al-Dosari"),
    ("MT-007", "SOCIAL",      "Financial literacy & inclusion",
                              "التثقيف المالي والشمول المالي",                  4.3, 4.1, "HIGH",       "SCORED", "Reema Al-Asiri"),
    ("MT-008", "SOCIAL",      "Health, safety & wellbeing of employees",
                              "الصحة والسلامة ورفاهية الموظفين",                3.8, 3.5, "MEDIUM",     "APPROVED",  "Layla Al-Ghamdi"),
    ("MT-009", "GOVERNANCE",  "Board diversity & independence",
                              "تنوّع المجلس واستقلاليته",                       4.5, 4.4, "HIGH",       "APPROVED",  "Mohammed Al-Otaibi"),
    ("MT-010", "GOVERNANCE",  "Anti-bribery & corruption",
                              "مكافحة الرشوة والفساد",                          4.7, 4.6, "CRITICAL",  "APPROVED",  "Khalid Al-Shehri"),
    ("MT-011", "GOVERNANCE",  "Cybersecurity & operational resilience",
                              "الأمن السيبراني والمرونة التشغيلية",             4.8, 4.9, "CRITICAL",  "APPROVED",  "Faisal Al-Dosari"),
    ("MT-012", "GOVERNANCE",  "Responsible AI & algorithmic fairness",
                              "الذكاء الاصطناعي المسؤول والعدالة الخوارزمية",   4.0, 3.9, "HIGH",       "SCORED", "Yousef Al-Mansouri"),
]
for code, cat, t_en, t_ar, stake, biz, tier, status, owner in MATERIAL_TOPICS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM sust.material_topic
                       WHERE topic_code = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO sust.material_topic (organisation_id, external_id, topic_code, reporting_year,
            category, topic_en, topic_ar, description_en, description_ar,
            stakeholder_importance, business_impact, materiality_score, priority_tier, status,
            owner_name, is_deleted, created_at)
        VALUES (?, NEWID(), ?, 2026, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, GETDATE())
        """,
        code, ORG_TSS,
        ORG_TSS, code, cat, t_en, t_ar,
        f"Theme reviewed in 2026 CMA Art.91-94 alignment workshop.",
        f"موضوع روجِع في ورشة محاذاة المواد 91-94 لهيئة السوق المالية 2026.",
        stake, biz, round((stake + biz) / 2, 2), tier, status, owner,
        label=f"material: [{cat}] {t_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# NDI gaps — DGA assessment findings
# ──────────────────────────────────────────────────────────────────────
section("ndi.gap — DGA NDI cycle findings")

cur.execute(
    "SELECT TOP 1 id FROM ndi.assessment_cycle WHERE is_deleted=0 AND organisation_id=? ORDER BY id DESC",
    ORG_TSS,
)
row = cur.fetchone()
ndi_cycle_id = row[0] if row else None
print(f"  using NDI cycle id={ndi_cycle_id}")

if ndi_cycle_id:
    GAPS = [
        # (code, domain, title_en, title_ar, severity, status, owner)
        ("GAP-001", "DATA_GOVERNANCE", "Missing data classification labels on 18% of business datasets",
                                       "وسوم تصنيف البيانات غير مكتملة على 18% من مجموعات البيانات",
                                       "HIGH",     "OPEN",     "Reema Al-Asiri"),
        ("GAP-002", "DATA_GOVERNANCE", "Inconsistent retention schedule across legacy systems",
                                       "جدول احتفاظ بالبيانات غير متّسق عبر الأنظمة القديمة",
                                       "MEDIUM",   "IN_PROGRESS","Khalid Al-Shehri"),
        ("GAP-003", "DATA_QUALITY",    "Customer KYC dataset — 4.2% duplicate rate",
                                       "بيانات KYC — معدّل تكرار 4.2%",
                                       "MEDIUM",   "IN_PROGRESS","Sarah Al-Qahtani"),
        ("GAP-004", "INTEROPERABILITY", "API contract drift between Open-Banking peers",
                                        "انحراف عقود API بين شركاء المنظومة المفتوحة",
                                        "HIGH",    "OPEN",         "Mohammed Al-Otaibi"),
        ("GAP-005", "PRIVACY",         "DSR SLA missed twice in Q1 2026",
                                       "تجاوز SLA طلبات أصحاب البيانات مرّتين في الربع الأول",
                                       "HIGH",     "RESOLVED",   "Faisal Al-Dosari"),
        ("GAP-006", "SECURITY",        "Encryption-at-rest gap in archived backups",
                                       "فجوة في تشفير النسخ الاحتياطية المؤرشفة",
                                       "CRITICAL", "IN_PROGRESS","Saud Al-Harbi"),
        ("GAP-007", "OPERATIONAL_EXCELLENCE", "Manual reconciliation step in nightly ETL",
                                              "خطوة مطابقة يدوية في خط ETL الليلي",
                                              "LOW",       "OPEN",         "Bandar Al-Rashidi"),
        ("GAP-008", "GOVERNANCE",      "Data ownership matrix missing 6 product lines",
                                       "مصفوفة ملكية البيانات تفتقر إلى 6 خطوط منتجات",
                                       "MEDIUM",   "OPEN",         "Layla Al-Ghamdi"),
        ("GAP-009", "PRIVACY",         "Consent re-collection not triggered after policy update",
                                       "إعادة جمع الموافقة لم تُفعَّل بعد تحديث السياسة",
                                       "HIGH",     "IN_PROGRESS","Reema Al-Asiri"),
        ("GAP-010", "DATA_QUALITY",    "Address normalisation failing for Saudi geocodes",
                                       "تطبيع العناوين فاشل لأكواد GPS السعودية",
                                       "LOW",      "OPEN",         "Yousef Al-Mansouri"),
    ]
    for code, domain, t_en, t_ar, severity, status, owner in GAPS:
        run(
            f"""
            IF NOT EXISTS (SELECT 1 FROM ndi.gap
                           WHERE gap_code = ? AND organisation_id = ? AND is_deleted = 0)
            INSERT INTO ndi.gap (organisation_id, external_id, cycle_id, gap_code,
                source_type, domain, title_en, title_ar, severity, likelihood, risk_score,
                owner_name, open_date, target_date, rollup_progress, capa_count, capa_completed_count,
                status, is_deleted, created_at)
            VALUES (?, LOWER(REPLACE(NEWID(),'-','')), ?, ?, 'MANUAL', ?, ?, ?, ?, 'LIKELY', ?, ?,
                CAST(GETDATE() AS DATE), DATEADD(day, 60, CAST(GETDATE() AS DATE)),
                {0 if status == 'OPEN' else (50 if status == 'IN_REMEDIATION' else 100)},
                0, 0, ?, 0, GETDATE())
            """,
            code, ORG_TSS,
            ORG_TSS, ndi_cycle_id, code, domain, t_en, t_ar, severity,
            10 if severity == 'CRITICAL' else (7 if severity == 'HIGH' else (5 if severity == 'MEDIUM' else 2)),
            owner, status,
            label=f"NDI gap: [{domain}] {t_en[:50]}",
        )

# ──────────────────────────────────────────────────────────────────────
# Climate risks & opportunities — TCFD physical + transition
# ──────────────────────────────────────────────────────────────────────
section("sust.climate_risk_opportunity — TCFD physical + transition")

CROS = [
    # (code, kind, category, title_en, title_ar, horizon, likelihood, impact, status, fin_impact_sar)
    ("CRO-001", "RISK",        "ACUTE_PHYSICAL",  "Extreme heat days disrupting Riyadh branch ops",
                                                  "أيام الحرّ الشديد تؤثّر على عمليات فروع الرياض",
                                                  "SHORT",  4, 4, "ASSESSED",   12000000),
    ("CRO-002", "RISK",        "CHRONIC_PHYSICAL","Sea-level rise — Eastern Province coastal branches",
                                                  "ارتفاع منسوب البحر — فروع المنطقة الشرقية الساحلية",
                                                  "LONG",   3, 4, "IDENTIFIED",  35000000),
    ("CRO-003", "RISK",        "TRANSITION",      "Carbon price escalation on Scope-3 financed emissions",
                                                  "ارتفاع تسعير الكربون على انبعاثات النطاق 3 المموَّلة",
                                                  "MEDIUM", 4, 5, "ASSESSED",   85000000),
    ("CRO-004", "RISK",        "REGULATORY",      "CMA Art.91-94 disclosure compliance gap",
                                                  "فجوة الامتثال للإفصاح وفقاً لمواد 91-94 لهيئة السوق",
                                                  "SHORT",  3, 3, "TREATED",     5000000),
    ("CRO-005", "RISK",        "LITIGATION",      "Climate-related lawsuit risk on financed projects",
                                                  "مخاطر دعاوى قضائية مرتبطة بالمناخ على مشاريع مموَّلة",
                                                  "MEDIUM", 2, 4, "IDENTIFIED",  20000000),
    ("CRO-006", "RISK",        "REPUTATIONAL",    "ESG-rating downgrade if scope-3 reduction missed",
                                                  "تراجع التصنيف ESG عند فوات هدف خفض النطاق 3",
                                                  "MEDIUM", 3, 4, "ASSESSED",   15000000),
    ("CRO-007", "OPPORTUNITY", "PRODUCTS",        "Green Sukuk issuance pipeline 2026-2028",
                                                  "خط إصدار الصكوك الخضراء 2026-2028",
                                                  "MEDIUM", 4, 5, "TREATED",    450000000),
    ("CRO-008", "OPPORTUNITY", "MARKETS",         "Sustainable supply-chain finance for KSA SMEs",
                                                  "تمويل السلاسل المستدامة للشركات السعودية الصغيرة",
                                                  "MEDIUM", 4, 4, "ASSESSED",   180000000),
    ("CRO-009", "OPPORTUNITY", "ENERGY_SOURCE",   "Solar-powered branch programme (12 sites by 2027)",
                                                  "برنامج الفروع المُغذّاة بالطاقة الشمسية (12 موقعاً)",
                                                  "SHORT",  4, 3, "TREATED",     8000000),
    ("CRO-010", "OPPORTUNITY", "RESILIENCE",      "Climate-resilient HQ + Data-Centre upgrade",
                                                  "تحديث المقر ومركز البيانات بمقاومة المناخ",
                                                  "LONG",   3, 4, "IDENTIFIED",  60000000),
]
for code, kind, cat, t_en, t_ar, horizon, lik, imp, status, fin in CROS:
    risk_score = lik * imp
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM sust.climate_risk_opportunity
                       WHERE item_code = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO sust.climate_risk_opportunity (organisation_id, external_id, item_code,
            reporting_year, kind, category, title_en, title_ar, description_en, description_ar,
            time_horizon, likelihood, impact, risk_score, financial_impact_sar,
            owner_name, status, identified_at, is_deleted, created_at)
        VALUES (?, NEWID(), ?, 2026, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), 0, GETDATE())
        """,
        code, ORG_TSS,
        ORG_TSS, code, kind, cat, t_en, t_ar,
        f"TCFD-aligned {kind.lower()} reviewed in 2026 climate workshop.",
        f"بند {('فرصة' if kind == 'OPPORTUNITY' else 'مخاطرة')} متوافق مع TCFD روجع في ورشة المناخ 2026.",
        horizon, lik, imp, risk_score, fin, "Sarah Al-Qahtani", status,
        label=f"CRO: [{kind}/{cat}] {t_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Audit findings — IA + external engagements
# ──────────────────────────────────────────────────────────────────────
section("audit_lc.findings — Internal & external audit findings")

# Engagement code default. The audit_lc.findings table requires a non-null
# engagement_code; existing rows may use various codes — we use a stable default.
DEFAULT_ENG_CODE = "IA-2026-001"

FINDINGS = [
    # (ref, title_en, title_ar, severity, status, condition_en, condition_ar)
    ("F-2026-001", "Privileged access not reviewed quarterly",
                   "صلاحيات الوصول المميَّز لم تُراجع ربعياً",
                   "HIGH",    "IN_PROGRESS",
                   "Sample of 20 PAM accounts; 7 lacked Q4 2025 attestation evidence.",
                   "عيّنة 20 حساباً مميَّزاً، 7 منها بلا دليل مصادقة للربع الرابع 2025."),
    ("F-2026-002", "BCP plan version older than 12 months",
                   "إصدار خطة استمرارية الأعمال أقدم من 12 شهراً",
                   "MEDIUM",  "OPEN",
                   "BCP version 3.2 effective 2024-11-04; ISO 22301 §8.4.4 requires annual review.",
                   "الإصدار 3.2 ساري من 2024-11-04، أيزو 22301 §8.4.4 يستوجب مراجعة سنوية."),
    ("F-2026-003", "Vendor exit plans missing for 3 critical suppliers",
                   "خطط الخروج مفقودة لـ 3 مورّدين حرجين",
                   "HIGH",    "OPEN",
                   "STC Cloud, Microsoft Saudi, AWS — no documented exit playbook.",
                   "STC Cloud، Microsoft Saudi، AWS — لا توجد خطة خروج موثَّقة."),
    ("F-2026-004", "DSR SLA exceeded for 2 of 38 cases in Q1",
                   "تجاوز SLA لطلبات أصحاب البيانات في 2 من 38 حالة بالربع الأول",
                   "MEDIUM",  "REMEDIATED",
                   "PDPL Art.4 sets 30-day SLA; 2 cases breached by 4 and 9 days respectively.",
                   "المادة 4 من PDPL تشترط 30 يوماً، حالتان تجاوزتا بـ 4 و 9 أيام على التوالي."),
    ("F-2026-005", "Encryption-at-rest gap on archived backups",
                   "فجوة في تشفير النسخ الاحتياطية المؤرشفة",
                   "CRITICAL","IN_PROGRESS",
                   "Tape backups older than 2024 stored unencrypted in off-site vault.",
                   "النسخ على شرائط أقدم من 2024 مخزّنة دون تشفير في الخزينة الخارجية."),
    ("F-2026-006", "Segregation of duties — payment-file uploader = approver",
                   "فصل المهام — مُحمِّل ملف المدفوعات هو نفسه المعتمِد",
                   "HIGH",    "VERIFIED",
                   "User M.Otaibi can both upload and approve outgoing SADAD batches.",
                   "المستخدم M.Otaibi يستطيع رفع واعتماد دفعات SADAD الصادرة."),
    ("F-2026-007", "Risk register — 14 of 50 risks lack residual score",
                   "سجل المخاطر — 14 من 50 خطراً بلا درجة متبقية",
                   "MEDIUM",  "IN_PROGRESS",
                   "After-control residual likelihood/impact missing on 28% of register.",
                   "بعد الضوابط، الاحتمال/الأثر المتبقي مفقود في 28% من السجل."),
    ("F-2026-008", "KRI thresholds not re-baselined annually",
                   "حدود KRI لم يُعَد ضبطها سنوياً",
                   "LOW",     "OPEN",
                   "Sample of 8 KRIs; thresholds set in 2024 still in force.",
                   "عيّنة 8 مؤشّرات؛ الحدود المضبوطة 2024 ما زالت سارية."),
    ("F-2026-009", "Cross-border transfer log missing safeguard evidence",
                   "سجل النقل خارج الحدود يفتقر إلى أدلة الضمانات",
                   "HIGH",    "OPEN",
                   "PDPL Art.29 — 4 of 12 active transfers lack SCC or adequacy decision PDF.",
                   "PDPL المادة 29 — 4 من 12 عملية نقل بلا عقود قياسية أو قرار كفاية."),
    ("F-2026-010", "Incident lessons-learned not captured for 3 SOC closures",
                   "الدروس المستفادة لم تُسجَّل لـ 3 حوادث مغلقة في SOC",
                   "LOW",     "REMEDIATED",
                   "Q1 2026 sample; 3 of 14 closed cases skipped lessons-learned narrative.",
                   "عيّنة Q1 2026، 3 من 14 حالة مغلقة تخطّت سرد الدروس المستفادة."),
    ("F-2026-011", "Penetration-test report findings not tracked to closure",
                   "ملاحظات اختبار الاختراق غير مُتتبّعة حتى الإغلاق",
                   "MEDIUM",  "OPEN",
                   "2024 external pentest had 12 highs; 5 still without remediation owner.",
                   "اختبار الاختراق الخارجي 2024 سجّل 12 ملاحظة عالية، 5 منها بلا مسؤول معالجة."),
    ("F-2026-012", "Committee meeting quorum not enforced in minutes template",
                   "النصاب القانوني للاجتماع غير مُلزَم في قالب المحاضر",
                   "LOW",     "VERIFIED",
                   "Charter requires 60% quorum; minutes template lacks quorum-met checkbox.",
                   "الميثاق يستوجب 60% نصاب، قالب المحاضر يفتقر إلى مربّع تحقّق النصاب."),
]
for i, (ref, t_en, t_ar, sev, status, cond_en, cond_ar) in enumerate(FINDINGS):
    days_ago = 10 + i * 5  # spread raised_at across last 12 weeks
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM audit_lc.findings
                       WHERE ref_number = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO audit_lc.findings (external_id, organisation_id, engagement_code, ref_number,
            title_en, title_ar, severity, status, condition_en, condition_ar,
            recommendation_en, recommendation_ar, target_date, owner_role, raised_at, is_deleted)
        VALUES (LOWER(REPLACE(NEWID(),'-','')), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
            DATEADD(day, 60, CAST(GETDATE() AS DATE)), 'CISO',
            DATEADD(day, -{days_ago}, GETDATE()), 0)
        """,
        ref, ORG_TSS,
        ORG_TSS, DEFAULT_ENG_CODE, ref, t_en, t_ar, sev, status, cond_en, cond_ar,
        "Establish a quarterly review cadence with documented evidence and owner sign-off.",
        "وضع دورة مراجعة ربعية مع توثيق الأدلة وموافقة المالك.",
        label=f"finding: [{sev}] {ref} {t_en[:40]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Approvals inbox — pending / decided requests
# ──────────────────────────────────────────────────────────────────────
section("approvals.requests — approvals inbox")

APPROVALS = [
    # (entity_type, entity_id, title_en, title_ar, summary_en, requested_by, assigned_to, priority, status, days_offset)
    ("RISK",            "1001", "Treatment plan approval — Cyber Risk #1001",
                                 "اعتماد خطة معالجة — مخاطر سيبرانية #1001",
                                 "Mitigate via EDR deployment + privileged-access overhaul.",
                                 "Khalid Al-Shehri", "Mohammed Al-Otaibi", "HIGH",       "PENDING",   -2),
    ("CAPA",            "F-2026-001", "CAPA closure — Privileged access review",
                                 "إغلاق إجراء تصحيحي — مراجعة صلاحيات الوصول المميَّز",
                                 "All 7 PAM accounts now attest; closure evidence attached.",
                                 "Faisal Al-Dosari", "Bandar Al-Rashidi",  "MEDIUM",    "PENDING",   -1),
    ("POLICY",          "POL-IS-2026-1.3", "Information Security Policy v1.3 publication",
                                 "اعتماد نشر سياسة أمن المعلومات الإصدار 1.3",
                                 "Updates Article 5 (cloud) and Article 9 (BYOD). Reviewed by CISO + Legal.",
                                 "Reema Al-Asiri",   "Mohammed Al-Otaibi", "MEDIUM",    "APPROVED",  -8),
    ("PDPL_CBT",        "CBT-2026-012", "Cross-Border Transfer — Salesforce US",
                                 "نقل خارج الحدود — Salesforce US",
                                 "Tier-3 country; requires SCC + DPIA addendum per SDAIA decision.",
                                 "Layla Al-Ghamdi",  "Faisal Al-Dosari",   "HIGH",      "PENDING",   -3),
    ("PDPL_DPIA",       "DPIA-2026-007", "DPIA approval — Open-Banking phase 3",
                                 "اعتماد تقييم أثر الخصوصية — المنظومة المفتوحة مرحلة 3",
                                 "Account-info sharing with 8 aggregators; mitigations + consent flow approved.",
                                 "Layla Al-Ghamdi",  "Mohammed Al-Otaibi", "CRITICAL",  "APPROVED",  -15),
    ("VENDOR_ONBOARD",  "VND-014", "Vendor onboarding — Crowdstrike (Critical)",
                                 "تأهيل مورّد — Crowdstrike (حرج)",
                                 "Critical-tier vendor; SAMA notification + security review complete.",
                                 "Sarah Al-Qahtani", "Mohammed Al-Otaibi", "HIGH",      "PENDING",   -5),
    ("BCP_PLAN",        "BCP-OPS-002", "BCP plan approval — Branch Ops 2026",
                                 "اعتماد خطة استمرارية — عمليات الفروع 2026",
                                 "Updated to address Q1 lessons; Tier-1 critical processes covered.",
                                 "Khalid Al-Shehri", "Sarah Al-Qahtani",   "MEDIUM",    "PENDING",   -4),
    ("RISK_ACCEPTANCE", "RA-2026-003", "Risk acceptance — Legacy ATM-Switch OS",
                                 "قبول مخاطرة — نظام تشغيل محوّل أجهزة الصراف الآلي القديم",
                                 "Vendor support extended; replacement in 2026-Q4 roadmap.",
                                 "Bandar Al-Rashidi","Mohammed Al-Otaibi", "MEDIUM",    "PENDING",   -1),
    ("BOARD_PACK",      "BP-2026-Q1", "Board Pack — Q1 2026 distribution",
                                 "حزمة مجلس الإدارة — توزيع الربع الأول 2026",
                                 "Risk + Cyber + ESG + Audit summary; ready for chair sign-off.",
                                 "Sarah Al-Qahtani", "Reema Al-Asiri",     "HIGH",      "APPROVED",  -10),
    ("REG_CHANGE",      "SAMA/CIRC/2026/04", "Regulatory change assessment — SAMA OBF Phase 3",
                                 "تقييم تغيير تنظيمي — منظومة ساما المفتوحة مرحلة 3",
                                 "Impact analysis + control update plan; awaiting compliance sign-off.",
                                 "Reema Al-Asiri",   "Khalid Al-Shehri",   "HIGH",      "PENDING",   -2),
    ("ESG_ANNEX",       "ESG-2026", "ESG Sustainability Annex — CMA Art.91-94",
                                 "ملحق الاستدامة — هيئة السوق المالية المواد 91-94",
                                 "TCFD-aligned annex; reviewed by Sustainability + IR.",
                                 "Yousef Al-Mansouri","Mohammed Al-Otaibi","HIGH",      "PENDING",   -6),
    ("AUDIT_FINDING",   "F-2026-005", "Audit finding remediation plan — Backup encryption",
                                 "خطة معالجة ملاحظة تدقيق — تشفير النسخ الاحتياطية",
                                 "CRITICAL finding; Q2 deployment of encrypted backup vault.",
                                 "Saud Al-Harbi",    "Mohammed Al-Otaibi", "CRITICAL",  "PENDING",   -1),
]
for entity_type, entity_id, t_en, t_ar, summary, req, assigned, priority, status, days_offset in APPROVALS:
    decided_at = "GETDATE()" if status in ("APPROVED", "REJECTED") else "NULL"
    decided_by = "?" if status in ("APPROVED", "REJECTED") else "NULL"
    decided_by_param = [assigned] if status in ("APPROVED", "REJECTED") else []
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM approvals.requests
                       WHERE entity_type = ? AND entity_id = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO approvals.requests (external_id, organisation_id, entity_type, entity_id,
            title_en, title_ar, summary_en, summary_ar, requested_by, requested_at,
            assigned_to, original_assigned_to, priority,
            sla_deadline, status, decided_at, decided_by, is_deleted)
        VALUES (LOWER(REPLACE(NEWID(),'-','')), ?, ?, ?, ?, ?, ?, ?, ?,
            DATEADD(day, {days_offset}, GETDATE()), ?, ?, ?,
            DATEADD(day, {days_offset} + 7, GETDATE()), ?, {decided_at}, {decided_by}, 0)
        """,
        entity_type, entity_id, ORG_TSS,
        ORG_TSS, entity_type, entity_id, t_en, t_ar, summary,
        f"ملخّص: {summary}",
        req, req, assigned, priority, status,
        *decided_by_param,
        label=f"approval: [{priority}] {entity_type} {t_en[:40]}",
    )

# ──────────────────────────────────────────────────────────────────────
# audit_lc CAPA tasks — link to findings
# ──────────────────────────────────────────────────────────────────────
section("audit_lc.capa_tasks — CAPA tasks tied to audit findings")

CAPA_TASKS = [
    # (finding_ref, kind, title_en, title_ar, owner_role, target_days, status)
    ("F-2026-001", "CORRECTIVE",  "Roll out quarterly PAM attestation workflow in CyberArk",
                                  "تفعيل سير عمل مصادقة PAM ربعي في CyberArk",
                                  "CISO", 30, "IN_PROGRESS"),
    ("F-2026-001", "PREVENTIVE",  "Auto-revoke PAM accounts inactive 90 days",
                                  "إلغاء حسابات PAM غير المستخدمة لمدة 90 يوماً تلقائياً",
                                  "CISO", 60, "OPEN"),
    ("F-2026-002", "CORRECTIVE",  "Approve BCP v3.3 with Q1 lessons",
                                  "اعتماد خطة استمرارية الإصدار 3.3 مع دروس الربع الأول",
                                  "BCM_LEAD", 14, "IN_PROGRESS"),
    ("F-2026-003", "CORRECTIVE",  "Draft exit playbooks for STC Cloud, MSFT Saudi, AWS",
                                  "صياغة خطط الخروج لـ STC Cloud و Microsoft Saudi و AWS",
                                  "VENDOR_MGR", 45, "OPEN"),
    ("F-2026-004", "PREVENTIVE",  "Auto-trigger SLA reminder at day 21 for DSR cases",
                                  "تذكير SLA تلقائي لطلبات أصحاب البيانات في اليوم 21",
                                  "DPO", 21, "VERIFIED"),
    ("F-2026-005", "CORRECTIVE",  "Procure encrypted backup vault + migrate 2024 tapes",
                                  "شراء خزينة نسخ مشفّرة + ترحيل شرائط 2024",
                                  "CISO", 90, "IN_PROGRESS"),
    ("F-2026-006", "CORRECTIVE",  "Implement SoD on SADAD batch upload + approve",
                                  "تطبيق فصل المهام لرفع واعتماد دفعات SADAD",
                                  "OPS_HEAD", 30, "VERIFIED"),
    ("F-2026-007", "CORRECTIVE",  "Backfill residual likelihood/impact for 14 risks",
                                  "تعبئة الاحتمال/الأثر المتبقي لـ 14 خطراً",
                                  "CRO", 28, "IN_PROGRESS"),
    ("F-2026-008", "PREVENTIVE",  "Annual KRI threshold review on 1 Apr",
                                  "مراجعة سنوية لحدود مؤشّرات المخاطر في 1 أبريل",
                                  "CRO", 75, "OPEN"),
    ("F-2026-009", "CORRECTIVE",  "Attach SCC PDFs for 4 active cross-border transfers",
                                  "إرفاق PDFs للعقود القياسية لـ 4 عمليات نقل نشطة",
                                  "DPO", 30, "IN_PROGRESS"),
    ("F-2026-010", "PREVENTIVE",  "Make lessons-learned narrative required field at SOC closure",
                                  "جعل سرد الدروس المستفادة حقلاً إلزامياً عند إغلاق SOC",
                                  "SOC_HEAD", 14, "VERIFIED"),
    ("F-2026-011", "CORRECTIVE",  "Assign owner + due date to 5 unresolved 2024 pentest highs",
                                  "تعيين مالك وتاريخ استحقاق لـ 5 ملاحظات اختبار اختراق 2024",
                                  "CISO", 21, "IN_PROGRESS"),
]
for fref, kind, t_en, t_ar, role, target_days, status in CAPA_TASKS:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM audit_lc.capa_tasks
                       WHERE finding_id = ? AND title_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO audit_lc.capa_tasks (external_id, organisation_id, finding_id, kind,
            title_en, title_ar, owner_role, target_date, status, is_deleted)
        VALUES (LOWER(REPLACE(NEWID(),'-','')), ?, ?, ?, ?, ?, ?,
            DATEADD(day, {target_days}, CAST(GETDATE() AS DATE)), ?, 0)
        """,
        fref, t_en, ORG_TSS,
        ORG_TSS, fref, kind, t_en, t_ar, role, status,
        label=f"capa: [{kind}/{status}] {fref} {t_en[:40]}",
    )

# ──────────────────────────────────────────────────────────────────────
# BCM recovery operations — live + recent
# ──────────────────────────────────────────────────────────────────────
section("bcm.recovery_operations — recovery activations")

RECOVERY_OPS = [
    # (title_en, title_ar, op_type, rto_target, rto_actual, status, days_ago_triggered, days_ago_resolved)
    ("Treasury settlement — DR failover",
     "تعافي تسوية الخزينة — التحوّل لموقع الاحتياط",
     "DR_FAILOVER",   4, 3, "RESOLVED",   95, 95-1),
    ("Internet Banking — DDoS mitigation drill",
     "الخدمات المصرفية عبر الإنترنت — تمرين تخفيف DDoS",
     "MITIGATION",    2, 2, "RESOLVED",   40, 40),
    ("Card mgmt — Way4 partial outage",
     "إدارة البطاقات — انقطاع جزئي Way4",
     "PARTIAL_RESTORE",6, 8, "RESOLVED",   75, 73),
    ("ATM Switch — cardless cash service restore",
     "محوّل أجهزة الصراف — استعادة خدمة السحب بدون بطاقة",
     "FULL_RESTORE",  3, 4, "RESOLVED",   30, 30-1),
    ("Core T24 — emergency patch deploy",
     "T24 الأساسي — نشر طارئ للتحديثات",
     "EMERGENCY_CHANGE",2, 2, "RESOLVED",  22, 22),
    ("Branch Ops — Eastern Province sandstorm",
     "عمليات الفروع — عاصفة رملية في المنطقة الشرقية",
     "ENVIRONMENTAL", 12, 16, "RESOLVED",  60, 59),
    ("AWS Bahrain — regional outage failover",
     "AWS البحرين — تحوّل بسبب انقطاع إقليمي",
     "DR_FAILOVER",   4, 5, "ACTIVE",      2, None),
    ("Splunk SIEM — index rebuild",
     "Splunk SIEM — إعادة بناء فهرس",
     "RESTORE",       8, None, "ACTIVE",   1, None),
]
for t_en, t_ar, op_type, rto_t, rto_a, status, trig_ago, res_ago in RECOVERY_OPS:
    triggered_at = f"DATEADD(day, -{trig_ago}, GETDATE())"
    resolved_at = "NULL" if res_ago is None else f"DATEADD(day, -{res_ago}, GETDATE())"
    rto_actual_val = "NULL" if rto_a is None else str(rto_a)
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM bcm.recovery_operations
                       WHERE title_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO bcm.recovery_operations (external_id, organisation_id, title_en, title_ar,
            operation_type, triggered_at, resolved_at, rto_target_hours, rto_actual_hours,
            status, assigned_to, description_en, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, {triggered_at}, {resolved_at}, ?, {rto_actual_val},
            ?, ?, ?, 0, GETDATE())
        """,
        t_en, ORG_TSS,
        ORG_TSS, t_en, t_ar, op_type, rto_t, status, "BCM Crisis Team",
        f"BCM recovery operation — {t_en}. RTO target {rto_t}h.",
        label=f"recovery: [{status}] {t_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# BCM communication contacts — crisis comms tree
# ──────────────────────────────────────────────────────────────────────
section("bcm.communication_contacts — crisis comms tree")

CONTACTS = [
    # (name, role, primary_phone, email, escalation_level, status, last_test_result)
    ("Mohammed Al-Otaibi",   "Chief Executive Officer (CEO)",     "+966-50-100-0001", "ceo@example.sa",         1, "ACTIVE", "PASSED"),
    ("Sarah Al-Qahtani",     "Chief Operating Officer (COO)",      "+966-50-100-0002", "coo@example.sa",         1, "ACTIVE", "PASSED"),
    ("Khalid Al-Shehri",     "Chief Risk Officer (CRO)",            "+966-50-100-0003", "cro@example.sa",         1, "ACTIVE", "PASSED"),
    ("Faisal Al-Dosari",     "Chief Information Security Officer", "+966-50-100-0004", "ciso@example.sa",        1, "ACTIVE", "PASSED"),
    ("Reema Al-Asiri",       "General Counsel + DPO",                "+966-50-100-0005", "gc@example.sa",          2, "ACTIVE", "PASSED"),
    ("Layla Al-Ghamdi",      "Head of HR + Crisis Comms",            "+966-50-100-0006", "hr-comms@example.sa",    2, "ACTIVE", "PASSED"),
    ("Yousef Al-Mansouri",   "Head of Customer Experience",         "+966-50-100-0007", "cx@example.sa",          2, "ACTIVE", "NOT_REACHED"),
    ("Bandar Al-Rashidi",    "Head of IT Operations",               "+966-50-100-0008", "it-ops@example.sa",      2, "ACTIVE", "PASSED"),
    ("Saud Al-Harbi",        "Head of DR + BCM",                     "+966-50-100-0009", "bcm@example.sa",         1, "ACTIVE", "PASSED"),
    ("Abdulaziz Al-Sayed",   "Head of Treasury",                     "+966-50-100-0010", "treasury@example.sa",    2, "ACTIVE", "PASSED"),
    ("SAMA Cyber Incident Desk", "Regulator — SAMA",                 "+966-11-463-2000", "cir@sama.gov.sa",        1, "ACTIVE", "PASSED"),
    ("NCA SOC Hotline",         "Regulator — NCA",                  "+966-11-282-3000", "soc@nca.gov.sa",         1, "ACTIVE", "PASSED"),
    ("SDAIA Privacy Office",   "Regulator — SDAIA",                   "+966-11-808-7000", "privacy@sdaia.gov.sa",   2, "ACTIVE", "PASSED"),
    ("CMA Investor Relations",  "Regulator — CMA",                    "+966-11-205-3000", "ir@cma.org.sa",          2, "ACTIVE", "PASSED"),
    ("STC Cloud — Critical Account Mgr","Vendor — STC Cloud",        "+966-11-911-1000", "kam-stc@stc.com.sa",     3, "ACTIVE", "PASSED"),
    ("Crowdstrike — Falcon TAM","Vendor — Crowdstrike",                "+1-888-512-8755",  "tam@crowdstrike.com",    3, "ACTIVE", "PASSED"),
    ("Microsoft Saudi CSM",    "Vendor — Microsoft Saudi",            "+966-11-282-1000", "csm@microsoft.com",      3, "ACTIVE", "NOT_REACHED"),
]
for name, role, phone, email, level, status, last_test in CONTACTS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM bcm.communication_contacts
                       WHERE email = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO bcm.communication_contacts (organisation_id, external_id, name, role_title,
            primary_phone, email, escalation_level, availability_status, last_tested_date,
            last_test_result, status, is_deleted, created_at)
        VALUES (?, NEWID(), ?, ?, ?, ?, ?, 'AVAILABLE', DATEADD(month, -1, CAST(GETDATE() AS DATE)),
            ?, ?, 0, GETDATE())
        """,
        email, ORG_TSS,
        ORG_TSS, name, role, phone, email, level, last_test, status,
        label=f"contact: L{level} {name[:30]} ({role[:30]})",
    )

# ──────────────────────────────────────────────────────────────────────
# KRI breaches — risk_lc.kri_breaches
# ──────────────────────────────────────────────────────────────────────
section("risk_lc.kri_breaches — KRI threshold breach events")

# Pick the first 10 risk ids dynamically so the FK is valid even on
# fresh DBs where the seeded risks may start at a different id.
cur.execute(
    "SELECT TOP 10 id FROM risk.risks WHERE is_deleted=0 AND organisation_id=? ORDER BY id",
    ORG_TSS,
)
risk_ids = [r[0] for r in cur.fetchall()]
print(f"  found {len(risk_ids)} risks to link KRI breaches to")

if risk_ids:
    KRI_BREACHES = [
        # (kri_code, name_en, name_ar, threshold, actual, direction, status, owner, days_ago)
        ("KRI-FRAUD-01",   "Fraud loss rate (bps)",
                           "نسبة خسائر الاحتيال (bps)",            5.0,  7.2,  "HIGH",  "OPEN",        "Faisal Al-Dosari",  3),
        ("KRI-AML-02",    "Open AML alerts > 30 days",
                          "تنبيهات غسل الأموال المفتوحة > 30 يوماً", 25, 42,    "HIGH",  "IN_PROGRESS", "Reema Al-Asiri",    8),
        ("KRI-SLA-03",    "Customer complaint resolution > 5 days",
                          "حل شكاوى العملاء > 5 أيام",              10, 14,    "HIGH",  "IN_PROGRESS", "Layla Al-Ghamdi",   2),
        ("KRI-CYBER-04",  "Critical patches > 30 days overdue",
                          "ترقيات حرجة متأخّرة > 30 يوماً",          0,  3,     "HIGH",  "OPEN",        "Khalid Al-Shehri",  1),
        ("KRI-OPS-05",    "Branch service availability (%)",
                          "توفّر خدمة الفرع (%)",                   99.5, 98.2, "LOW",   "OPEN",        "Sarah Al-Qahtani",  5),
        ("KRI-OPS-06",    "ATM uptime (%)",
                          "توفّر أجهزة الصراف الآلي (%)",           99.8, 99.1, "LOW",   "RESOLVED",    "Bandar Al-Rashidi", 25),
        ("KRI-FRAUD-07",  "Card-not-present chargeback ratio (bps)",
                          "نسبة استرداد المعاملات دون البطاقة (bps)", 4.0,  6.5,  "HIGH",  "IN_PROGRESS", "Sarah Al-Qahtani",  12),
        ("KRI-PDPL-08",   "DSR cases breaching 30-day SLA",
                          "حالات طلبات أصحاب البيانات المتجاوزة SLA 30 يوماً", 0,  2, "HIGH", "RESOLVED", "Reema Al-Asiri",    33),
        ("KRI-IT-09",     "Production incidents per month",
                          "حوادث الإنتاج شهرياً",                    5,   8,     "HIGH",  "OPEN",        "Bandar Al-Rashidi", 4),
        ("KRI-RISK-10",   "Risk acceptances > 90 days unresolved",
                          "قبول مخاطرة > 90 يوماً دون حل",            5,   7,     "HIGH",  "OPEN",        "Khalid Al-Shehri",  18),
        ("KRI-BCM-11",    "DR-test failure rate (%)",
                          "نسبة فشل تمارين التعافي (%)",            10.0, 18.0, "HIGH",  "IN_PROGRESS", "Saud Al-Harbi",     45),
        ("KRI-TPRM-12",   "Critical vendors with expired security review",
                          "موردون حرجون بتقييم أمني منتهي",           0,   2,     "HIGH",  "IN_PROGRESS", "Sarah Al-Qahtani",  9),
    ]
    for i, (code, name_en, name_ar, threshold, actual, direction, status, owner, days_ago) in enumerate(KRI_BREACHES):
        rid = risk_ids[i % len(risk_ids)]
        run(
            f"""
            IF NOT EXISTS (SELECT 1 FROM risk_lc.kri_breaches
                           WHERE kri_code = ? AND organisation_id = ? AND is_deleted = 0)
            INSERT INTO risk_lc.kri_breaches (external_id, organisation_id, risk_id,
                kri_code, kri_name_en, kri_name_ar, threshold_value, actual_value,
                direction, detected_at, status, current_owner, is_deleted)
            VALUES (LOWER(REPLACE(NEWID(),'-','')), ?, ?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -{days_ago}, GETDATE()), ?, ?, 0)
            """,
            code, ORG_TSS,
            ORG_TSS, rid, code, name_en, name_ar, threshold, actual, direction, status, owner,
            label=f"KRI breach: [{status}] {code} thr={threshold} actual={actual}",
        )

# ──────────────────────────────────────────────────────────────────────
# Board packs — quarterly + annual + AGM
# ──────────────────────────────────────────────────────────────────────
section("board_pack.packs — quarterly + annual board packs")

BOARD_PACKS = [
    # (title_en, title_ar, period, p_start, p_end, audience, status, drafted_by)
    ("Q1 2026 Board Pack — Risk + Cyber + ESG",
     "حزمة مجلس الإدارة Q1 2026 — مخاطر + سيبراني + استدامة",
     "2026-Q1", "2026-01-01", "2026-03-31", "BOARD",          "APPROVED",     "Sarah Al-Qahtani"),
    ("Q2 2026 Board Pack — Mid-year posture",
     "حزمة مجلس الإدارة Q2 2026 — موقف منتصف العام",
     "2026-Q2", "2026-04-01", "2026-06-30", "BOARD",          "DRAFT",        "Sarah Al-Qahtani"),
    ("Q3 2026 Board Pack — Annual planning prep",
     "حزمة مجلس الإدارة Q3 2026 — تحضير التخطيط السنوي",
     "2026-Q3", "2026-07-01", "2026-09-30", "BOARD",          "DRAFT",        "Sarah Al-Qahtani"),
    ("Audit Committee Q1 2026 — Findings + CAPA",
     "لجنة التدقيق Q1 2026 — الملاحظات والإجراءات التصحيحية",
     "2026-Q1", "2026-01-01", "2026-03-31", "AUDIT_COMMITTEE","APPROVED",     "Reema Al-Asiri"),
    ("Risk Committee Q1 2026 — Appetite + KRIs",
     "لجنة المخاطر Q1 2026 — شهيّة المخاطر ومؤشّراتها",
     "2026-Q1", "2026-01-01", "2026-03-31", "RISK_COMMITTEE", "APPROVED",     "Khalid Al-Shehri"),
    ("Risk Committee Q2 2026 — Q2 review",
     "لجنة المخاطر Q2 2026 — مراجعة الربع الثاني",
     "2026-Q2", "2026-04-01", "2026-06-30", "RISK_COMMITTEE", "IN_REVIEW",    "Khalid Al-Shehri"),
    ("AGM 2026 — Annual ESG + Sustainability Annex",
     "الجمعية العمومية 2026 — ملحق الاستدامة السنوي",
     "2026-FY", "2026-01-01", "2026-12-31", "BOARD",          "DRAFT",        "Yousef Al-Mansouri"),
    ("Cyber Committee Q1 2026 — SAMA + NCA posture",
     "لجنة الأمن السيبراني Q1 2026 — موقف ساما والهيئة الوطنية",
     "2026-Q1", "2026-01-01", "2026-03-31", "CYBER_COMMITTEE","APPROVED",     "Faisal Al-Dosari"),
]
for t_en, t_ar, period, p_start, p_end, audience, status, drafted_by in BOARD_PACKS:
    drafted_offset = -45 if status == "APPROVED" else -15
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM board_pack.packs
                       WHERE title_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO board_pack.packs (external_id, organisation_id, title_en, title_ar,
            period_label, period_start, period_end, audience, status, drafted_by, drafted_at,
            is_deleted)
        VALUES (LOWER(REPLACE(NEWID(),'-','')), ?, ?, ?, ?, ?, ?, ?, ?, ?,
            DATEADD(day, {drafted_offset}, GETDATE()), 0)
        """,
        t_en, ORG_TSS,
        ORG_TSS, t_en, t_ar, period, p_start, p_end, audience, status, drafted_by,
        label=f"board pack: [{audience}/{status}] {t_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Ethics cases — whistleblower + AML + harassment
# ──────────────────────────────────────────────────────────────────────
section("ethics.cases — whistleblower + AML + harassment + policy breaches")

ETHICS_CASES = [
    # (code, title_en, title_ar, category, severity, intake_channel, status, description, is_anonymous, days_ago)
    ("ETH-2026-001", "Allegation: gift acceptance breach in procurement",
                     "ادعاء: مخالفة قبول هدية في المشتريات",
                     "GIFTS_HOSPITALITY", "MEDIUM", "WHISTLEBLOWER_HOTLINE", "INVESTIGATING",
                     "Anonymous tip via SpeakUp portal alleging a procurement employee accepted SAR 12k watch from a vendor.",
                     True, 22),
    ("ETH-2026-002", "Reported harassment — Branch Riyadh Olaya",
                     "بلاغ تحرّش — فرع الرياض العليا",
                     "HARASSMENT",       "HIGH",   "HR_DIRECT", "TRIAGED",
                     "Female teller reports verbal harassment by branch operations supervisor. HR + Legal engaged.",
                     False, 8),
    ("ETH-2026-003", "Suspected layering — SADAD bulk transfers",
                     "اشتباه طبقات غسيل أموال — تحويلات SADAD مجمّعة",
                     "AML",              "HIGH",   "TRANSACTION_MONITORING", "INVESTIGATING",
                     "Actimize flagged 47 transactions across 9 accounts with classic layering pattern.",
                     False, 5),
    ("ETH-2026-004", "Conflict of interest — director on vendor RFP panel",
                     "تضارب مصالح — مدير في لجنة تقييم عرض مورّد",
                     "CONFLICT_OF_INTEREST", "MEDIUM", "MANAGER_REPORT", "CLOSED",
                     "Director recused after disclosure; no decision impact. CAPA: COI form for all RFP panels.",
                     False, 75),
    ("ETH-2026-005", "Unauthorised disclosure of customer data to relative",
                     "إفصاح غير مصرّح به عن بيانات عميل لقريب",
                     "DATA_PRIVACY",     "CRITICAL","WHISTLEBLOWER_HOTLINE","INVESTIGATING",
                     "Branch employee allegedly shared customer balance with sister-in-law. Termination + SDAIA report pending.",
                     True, 12),
    ("ETH-2026-006", "Falsified KYC document — onboarding",
                     "وثيقة KYC مزوّرة في التأهيل",
                     "FRAUD",            "HIGH",   "INTERNAL_AUDIT", "INVESTIGATING",
                     "IA found 3 onboarding files with photoshopped national ID images. AML + SAMA breach notification underway.",
                     False, 18),
    ("ETH-2026-007", "Time-theft — branch staff buddy-punching",
                     "سرقة وقت — تسجيل حضور بالنيابة بين موظفي الفرع",
                     "POLICY_BREACH",    "LOW",    "ANALYTICS", "CLOSED",
                     "HR analytics detected pattern across 4 employees. Verbal warning + biometric clock-in deployed.",
                     False, 90),
    ("ETH-2026-008", "Retaliation report — manager downgraded whistleblower review",
                     "بلاغ انتقام — مدير خفّض تقييم مبلّغ",
                     "RETALIATION",      "HIGH",   "HR_DIRECT", "TRIAGED",
                     "Employee who reported ETH-2026-001 got 2-grade downgrade in 360 review. Investigation opened.",
                     False, 3),
    ("ETH-2026-009", "Sanctions list — customer name partial-match",
                     "قائمة عقوبات — تطابق جزئي لاسم عميل",
                     "AML",              "CRITICAL","SCREENING", "INVESTIGATING",
                     "OFAC screening flagged 3 customer names within 0.85 similarity. Enhanced due diligence in progress.",
                     False, 1),
    ("ETH-2026-010","Misuse of corporate card",
                     "سوء استخدام بطاقة شركة",
                     "FRAUD",            "MEDIUM", "ACCOUNTING_REVIEW", "CLOSED",
                     "Senior manager used corporate card for SAR 4,500 personal expenses. Recovered + written warning.",
                     False, 60),
]
for code, t_en, t_ar, category, severity, channel, status, desc, anon, days_ago in ETHICS_CASES:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM ethics.cases
                       WHERE case_code = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO ethics.cases (external_id, organisation_id, case_code, title_en, title_ar,
            category, severity, intake_channel, is_anonymous, description,
            status, date_of_incident, regulator_notified, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
            DATEADD(day, -{days_ago}, CAST(GETDATE() AS DATE)),
            {'1' if severity == 'CRITICAL' else '0'}, 0, DATEADD(day, -{days_ago}, GETDATE()))
        """,
        code, ORG_TSS,
        ORG_TSS, code, t_en, t_ar, category, severity, channel, 1 if anon else 0, desc,
        status,
        label=f"ethics: [{severity}/{category}] {code} {t_en[:35]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Crisis AARs — after-action reviews for closed crisis events
# ──────────────────────────────────────────────────────────────────────
section("bcm.crisis_aar — after-action reviews")

# Lookup crisis events to link AARs to
cur.execute(
    "SELECT TOP 10 id, title_en FROM bcm.crisis_events WHERE is_deleted=0 AND organisation_id=? ORDER BY id",
    ORG_TSS,
)
crisis_events = [(r[0], r[1]) for r in cur.fetchall()]
print(f"  found {len(crisis_events)} crisis events")

AAR_PROFILES = [
    # (status, summary, what_worked, what_failed, root_cause)
    ("PUBLISHED",   "Major incident — full AAR published to stakeholders.",
     "SAMA 6h notification met. EDR isolation stopped lateral movement within 22 minutes.",
     "Patch deployment lagged by 4 weeks behind public CVE disclosure. Email-filter false-negative rate higher than SLA.",
     "Patching SLA exception process is informal and rarely tracked. Threat-intel feed not wired to email filter."),
    ("APPROVED",    "Lessons documented, awaiting board publication.",
     "Failover to DR site completed in 3h vs 4h target. Crisis comms tree reached all L1 contacts within 12 minutes.",
     "Customer-facing communication on Twitter was delayed 90 minutes. Mobile app fail-open didn't trigger.",
     "Twitter SLA missing from comms runbook. Mobile-app health-check threshold set too low."),
    ("APPROVED",    "Branch ops AAR — Eastern Province sandstorm.",
     "Manual ATM cash recycling kept 8 branches partially open. Tier-2 contacts mobilised on day 1.",
     "BCP plan didn't account for region-wide sandstorm; only single-site outage scenarios documented.",
     "Plan scenarios biased toward IT outages. Regional-scale weather events not modelled."),
    ("UNDER_REVIEW","Treasury settlement AAR — pending CRO sign-off.",
     "Manual settlement workaround executed for 6 hours; no missed SAMA deadlines.",
     "Lack of pre-position approval to extend SLA window. Treasury team had to escalate twice.",
     "Pre-position approval form missing. Treasury escalation chain ambiguous."),
    ("DRAFT",       "Vendor outage AAR — STC Cloud regional impact.",
     "Vendor exit plan kicked in at hour 2. Failover to AWS Bahrain succeeded.",
     "Customer-impacting latency for 38 minutes during cutover. Internal stakeholders not all notified.",
     "Vendor monitoring lacked anomaly threshold. Crisis comms tree not invoked for Tier-3 vendor."),
]
for i, (eid, ename) in enumerate(crisis_events[:5]):
    status, summary, worked, failed, root = AAR_PROFILES[i % len(AAR_PROFILES)]
    report_code = f"AAR-2026-{eid:03d}"
    approved_at = "DATEADD(day, -5, GETDATE())" if status in ("APPROVED", "PUBLISHED") else "NULL"
    published_at = "DATEADD(day, -2, GETDATE())" if status == "PUBLISHED" else "NULL"
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM bcm.crisis_aar
                       WHERE crisis_event_id = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO bcm.crisis_aar (organisation_id, external_id, crisis_event_id,
            report_code, title_en, title_ar, executive_summary_en, executive_summary_ar,
            what_worked_md_en, what_failed_md_en, root_cause_md_en,
            recommendations_md_en, status, approved_at, published_at, is_deleted, created_at)
        VALUES (?, NEWID(), ?, ?, ?, ?, ?, ?, ?, ?, ?,
            ?, ?, {approved_at}, {published_at}, 0, GETDATE())
        """,
        eid, ORG_TSS,
        ORG_TSS, eid, report_code,
        f"AAR — {ename}",
        f"تقرير ما بعد الحدث — {ename}",
        summary,
        f"تقرير {report_code} — تفاصيل الاستجابة والدروس المستفادة.",
        worked, failed, root,
        "Refresh patching SLA + add weather scenario to BCP + formalise vendor exit invocation runbook. Trigger CAPA for each gap.",
        status,
        label=f"AAR: [{status}] {report_code} {ename[:35]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Audit IA annual plans — 2026 + 2027
# ──────────────────────────────────────────────────────────────────────
section("audit_lc.annual_plans — IA annual plans 2026 + 2027")

PLANS = [
    # (year, status, q1, q2, q3, q4, budget_hours, notes_en)
    (2026, "APPROVED",
     "ENG-CYBER-2026Q1,ENG-PRIV-2026Q1,ENG-TPRM-2026Q1",
     "ENG-AML-2026Q2,ENG-BCM-2026Q2,ENG-CMA-2026Q2",
     "ENG-CRDIT-2026Q3,ENG-TREAS-2026Q3,ENG-FIN-2026Q3",
     "ENG-IT-2026Q4,ENG-OPS-2026Q4,ENG-FOLLOW-2026Q4",
     4800,
     "2026 IA Annual Plan — 12 engagements aligned to SAMA, NCA, SDAIA, CMA priorities. Board-approved."),
    (2027, "DRAFT",
     "ENG-CYBER-2027Q1,ENG-AI-2027Q1,ENG-PRIV-2027Q1",
     "ENG-CMA-2027Q2,ENG-CLIMATE-2027Q2,ENG-CRDIT-2027Q2",
     "ENG-TPRM-2027Q3,ENG-TREAS-2027Q3,ENG-OPS-2027Q3",
     "ENG-IT-2027Q4,ENG-BCM-2027Q4,ENG-FRAUD-2027Q4",
     5200,
     "2027 IA Annual Plan — emphasis on AI governance + climate risk per CMA Art.91-94 maturation."),
]
for year, status, q1, q2, q3, q4, budget, notes in PLANS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM audit_lc.annual_plans
                       WHERE year = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO audit_lc.annual_plans (external_id, organisation_id, year, status,
            last_updated, q1_codes, q2_codes, q3_codes, q4_codes, budget_hours,
            ac_notes_en, ac_notes_ar, is_deleted)
        VALUES (LOWER(REPLACE(NEWID(),'-','')), ?, ?, ?, GETDATE(), ?, ?, ?, ?, ?, ?, ?, 0)
        """,
        year, ORG_TSS,
        ORG_TSS, year, status, q1, q2, q3, q4, budget,
        notes,
        f"خطة التدقيق الداخلي السنوية {year} — معتمدة من لجنة التدقيق.",
        label=f"IA plan: {year} {status} budget={budget}h",
    )

# ──────────────────────────────────────────────────────────────────────
# Audit working papers — IA evidence ledger
# ──────────────────────────────────────────────────────────────────────
section("audit_lc.working_papers — IA evidence ledger")

DEFAULT_ENG_CODE_WP = "IA-2026-001"
PAPERS = [
    # (wp_ref, type, title_en, title_ar, author, status, hours)
    ("WP-2026-001", "WALKTHROUGH",  "PAM walkthrough — CyberArk",
                                     "جولة على نظام PAM — CyberArk",
                                     "Faisal Al-Dosari", "REVIEWED", 12.0),
    ("WP-2026-002", "SAMPLE_TEST",  "Sample of 20 PAM accounts — quarterly attestation",
                                     "عيّنة 20 حساب PAM — مصادقة ربعية",
                                     "Faisal Al-Dosari", "REVIEWED", 8.5),
    ("WP-2026-003", "DESIGN_TEST",  "BCP design effectiveness — ISO 22301 §8.4",
                                     "فعالية تصميم خطة استمرارية — أيزو 22301 §8.4",
                                     "Khalid Al-Shehri", "DRAFT",    16.0),
    ("WP-2026-004", "OPERATING_TEST","Vendor due-diligence sample — 3 critical suppliers",
                                     "عيّنة عناية واجبة على الموردين — 3 مورّدين حرجين",
                                     "Sarah Al-Qahtani", "REVIEWED", 10.5),
    ("WP-2026-005", "WALKTHROUGH",  "DSR end-to-end walkthrough",
                                     "جولة طلبات أصحاب البيانات من البداية للنهاية",
                                     "Reema Al-Asiri",   "REVIEWED",  6.0),
    ("WP-2026-006", "INTERVIEW",    "Backup-vault tour + ops interview",
                                     "زيارة خزينة النسخ الاحتياطية + مقابلة العمليات",
                                     "Saud Al-Harbi",    "REVIEWED",  4.0),
    ("WP-2026-007", "SAMPLE_TEST",  "SADAD payment file uploader+approver — SoD sample",
                                     "عيّنة فصل المهام — رفع واعتماد SADAD",
                                     "Faisal Al-Dosari", "REVIEWED",  7.0),
    ("WP-2026-008", "DESIGN_TEST",  "Risk-register residual-score policy design",
                                     "تصميم سياسة درجة المخاطر المتبقية",
                                     "Khalid Al-Shehri", "DRAFT",     5.0),
    ("WP-2026-009", "WALKTHROUGH",  "KRI threshold-setting workflow",
                                     "جولة على سير عمل تحديد حدود مؤشّرات المخاطر",
                                     "Khalid Al-Shehri", "DRAFT",     3.5),
    ("WP-2026-010", "OPERATING_TEST","Cross-border transfer log sample — Q1 2026",
                                     "عيّنة سجل النقل خارج الحدود — Q1 2026",
                                     "Reema Al-Asiri",   "REVIEWED",  9.0),
    ("WP-2026-011", "FOLLOW_UP",    "Closure follow-up — 2024 pentest findings",
                                     "متابعة إغلاق — ملاحظات اختبار الاختراق 2024",
                                     "Faisal Al-Dosari", "DRAFT",     11.0),
    ("WP-2026-012", "WALKTHROUGH",  "Committee minutes quorum-enforcement check",
                                     "تحقّق من إنفاذ النصاب في محاضر اللجان",
                                     "Reema Al-Asiri",   "REVIEWED",  2.5),
]
for wp_ref, wp_type, t_en, t_ar, author, status, hours in PAPERS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM audit_lc.working_papers
                       WHERE ref_number = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO audit_lc.working_papers (external_id, organisation_id, engagement_code,
            type, ref_number, title_en, title_ar, author, created_at, status, hours, is_deleted)
        VALUES (LOWER(REPLACE(NEWID(),'-','')), ?, ?, ?, ?, ?, ?, ?, GETDATE(), ?, ?, 0)
        """,
        wp_ref, ORG_TSS,
        ORG_TSS, DEFAULT_ENG_CODE_WP, wp_type, wp_ref, t_en, t_ar, author, status, hours,
        label=f"WP: [{wp_type}/{status}] {wp_ref} {t_en[:40]}",
    )

# ══════════════════════════════════════════════════════════════════════
# Multi-tenant — Aramco Trading Company (org_id = 2)
# Energy industry: oil & gas, refinery, OT/SCADA, exporter operations.
# Keeps the script idempotent across tenants (IF NOT EXISTS by org+key).
# ══════════════════════════════════════════════════════════════════════

print("\n\n" + "═" * 70)
print(f"  AR — Aramco Trading Company (org_id={ORG_ARAMCO})")
print("═" * 70)

# ──────────────────────────────────────────────────────────────────────
# Aramco — cyber assets (OT + IT stack)
# ──────────────────────────────────────────────────────────────────────
section("cyber.cyber_assets — Aramco OT + IT production assets")

ARAMCO_ASSETS = [
    # (name, type, biz_owner, tech_owner, classification, criticality, ip, hostname, os)
    ("DCS — Honeywell Experion (Ras Tanura Refinery)",
     "OT", "Saad Al-Otaibi", "Ahmed Al-Ghamdi", "RESTRICTED",   "TIER_1",
     "10.30.10.5",  "exp-rt-dcs-01",  "Windows Server 2019"),
    ("SCADA — GE iFix (Yanbu Refinery)",
     "OT", "Saad Al-Otaibi", "Ahmed Al-Ghamdi", "RESTRICTED",   "TIER_1",
     "10.31.10.5",  "ifix-yanbu-01",  "Windows Server 2019"),
    ("Pipeline SCADA — East-West Crude Line",
     "OT", "Khalid Al-Ahmadi", "Saud Al-Rashid", "RESTRICTED",   "TIER_1",
     "10.32.10.5",  "ew-pipe-scada",   "RHEL 8.6"),
    ("Historian — OSIsoft PI System",
     "OT", "Saad Al-Otaibi", "Ahmed Al-Ghamdi", "CONFIDENTIAL", "TIER_2",
     "10.30.20.10", "pi-historian-01", "Windows Server 2022"),
    ("ERP — SAP S/4HANA (Trading)",
     "APPLICATION", "Nora Al-Sayed",  "Mansour Al-Qarni",   "RESTRICTED",   "TIER_1",
     "10.40.10.10", "sap-s4-prod",      "RHEL 9.2"),
    ("Trading Risk Mgmt — Eka Software",
     "APPLICATION", "Nora Al-Sayed",  "Mansour Al-Qarni",   "RESTRICTED",   "TIER_1",
     "10.40.20.5",  "eka-trm-prod",     "Windows Server 2022"),
    ("Active Directory Forest (CORP)",
     "INFRASTRUCTURE", "Mansour Al-Qarni", "Saud Al-Rashid",  "RESTRICTED",   "TIER_1",
     "10.10.10.5",  "ad-corp-prod",     "Windows Server 2022"),
    ("Splunk Enterprise SIEM",
     "INFRASTRUCTURE", "Ahmed Al-Ghamdi", "Saud Al-Rashid",   "CONFIDENTIAL", "TIER_2",
     "10.70.10.10", "splunk-arc-01",    "RHEL 9.2"),
    ("Palo Alto NGFW (DMZ Refinery)",
     "INFRASTRUCTURE", "Saad Al-Otaibi", "Saud Al-Rashid",    "RESTRICTED",   "TIER_1",
     "10.30.1.1",   "pa-dmz-rt",        "PAN-OS 11.0"),
    ("Refinery Email Filter — Proofpoint",
     "APPLICATION",    "Mansour Al-Qarni", "Saud Al-Rashid", "CONFIDENTIAL", "TIER_2",
     None,           "proofpoint-arc",   None),
]
for name, atype, biz, tech, cls, crit, ip, host, os_name in ARAMCO_ASSETS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM cyber.cyber_assets
                       WHERE asset_name = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO cyber.cyber_assets (external_id, organisation_id, asset_name, asset_type,
            business_owner, technical_owner, classification, criticality, ip_address, hostname,
            operating_system, linked_vulnerabilities_count, linked_incidents_count,
            linked_controls_count, status, is_deleted, created_at)
        VALUES (LOWER(REPLACE(NEWID(), '-', '')), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0,
            'ACTIVE', 0, GETDATE())
        """,
        name, ORG_ARAMCO,
        ORG_ARAMCO, name, atype, biz, tech, cls, crit, ip, host, os_name,
        label=f"AR asset: {name}",
    )

# ──────────────────────────────────────────────────────────────────────
# Aramco — privacy consents (energy partner data sharing)
# ──────────────────────────────────────────────────────────────────────
section("privacy.consents — Aramco energy-partner data sharing")

ARAMCO_CONSENTS = [
    # (purpose_en, purpose_ar, channel, status)
    ("Counterparty KYC data sharing — Mubadala",
     "مشاركة بيانات KYC للأطراف المقابلة — مبادلة",                   "API",   "ACTIVE"),
    ("Bunker-fuel buyer credit assessment",
     "تقييم الائتمان لمشتري وقود السفن",                                "WEB",   "ACTIVE"),
    ("Marine surveyor inspection reports (Lloyd's)",
     "تقارير فحص مفتشي السفن — لويدز",                                  "EMAIL", "ACTIVE"),
    ("PetroChina counterparty due-diligence package",
     "حزمة العناية الواجبة للطرف المقابل — PetroChina",                 "API",   "ACTIVE"),
    ("Saudi Aramco trading data feed to refinery customers",
     "تغذية بيانات التداول لعملاء المصافي",                              "API",   "ACTIVE"),
    ("Vendor onboarding KYC questionnaire",
     "استبيان KYC لتأهيل المورّدين",                                     "WEB",   "ACTIVE"),
    ("Saudi Customs Authority transaction reporting",
     "تقارير المعاملات للهيئة العامة للجمارك",                            "API",   "ACTIVE"),
    ("ESG carbon-footprint partner reporting",
     "تقارير شريك بصمة الكربون ESG",                                     "API",   "ACTIVE"),
]
for purpose_en, purpose_ar, channel, status in ARAMCO_CONSENTS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM privacy.consents
                       WHERE purpose_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO privacy.consents (external_id, organisation_id, purpose_en, purpose_ar,
            channel, version_label, effective_date, status, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, 'v1', GETDATE(), ?, 0, GETDATE())
        """,
        purpose_en, ORG_ARAMCO,
        ORG_ARAMCO, purpose_en, purpose_ar, channel, status,
        label=f"AR consent: {purpose_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# Aramco — enterprise risks (energy + trading)
# ──────────────────────────────────────────────────────────────────────
section("risk.risks — Aramco enterprise risk register")

ARAMCO_RISKS = [
    # (title_en, title_ar, source, status, inh_lik, inh_imp, res_lik, res_imp)
    ("Crude oil price shock impact on margins",
     "صدمة سعر النفط الخام على الهوامش",
     "ERM",       "MITIGATING", 4, 5, 3, 4),
    ("OT cyber attack on refinery DCS/SCADA",
     "هجوم سيبراني على نظام التحكّم في المصافي",
     "CYBER",     "MITIGATING", 3, 5, 2, 4),
    ("Pipeline integrity failure — East-West line",
     "فشل سلامة خط الأنابيب شرق-غرب",
     "BCM",       "MITIGATING", 2, 5, 2, 3),
    ("Sanctions exposure on counterparty trading",
     "تعرّض العقوبات على تداول الأطراف المقابلة",
     "COMPLIANCE","MITIGATING", 3, 5, 2, 4),
    ("Cross-border data transfer violation (SDAIA)",
     "مخالفة نقل البيانات خارج الحدود (سدايا)",
     "PRIVACY",   "MITIGATING", 3, 4, 2, 3),
    ("Refinery safety incident — process safety",
     "حادث سلامة العمليات في المصافي",
     "BCM",       "MITIGATING", 2, 5, 1, 4),
    ("Climate transition risk — Scope-3 financed emissions",
     "مخاطر التحول المناخي — انبعاثات النطاق 3",
     "ERM",       "UNDER_REVIEW", 4, 5, 3, 4),
    ("Insider trading on commodity positions",
     "تداول داخلي على مراكز السلع",
     "COMPLIANCE","MITIGATING", 2, 5, 1, 4),
    ("Vendor concentration on critical OT services",
     "تركّز المورّدين على خدمات OT الحرجة",
     "ERM",       "MITIGATING", 3, 4, 2, 3),
    ("FX volatility on USD-denominated trades",
     "تقلّبات الصرف الأجنبي على التداولات بالدولار",
     "ERM",       "ACCEPTED",   4, 3, 3, 3),
]
for title_en, title_ar, source, status, il, ii, rl, ri in ARAMCO_RISKS:
    # inherent_score + residual_score + risk_level are computed columns —
    # don't INSERT them. The DB derives them from likelihood × impact.
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM risk.risks
                       WHERE title_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO risk.risks (external_id, organisation_id, title_en, title_ar,
            source_module, status, inherent_likelihood, inherent_impact,
            residual_likelihood, residual_impact,
            identified_date, review_date, attestation_cadence_days,
            created_at, updated_at, is_deleted)
        VALUES (NEWID(), ?, ?, ?, ?, ?, ?, ?, ?, ?,
            DATEADD(month, -3, CAST(GETDATE() AS DATE)),
            DATEADD(month, 3, CAST(GETDATE() AS DATE)),
            90, GETDATE(), GETDATE(), 0)
        """,
        title_en, ORG_ARAMCO,
        ORG_ARAMCO, title_en, title_ar, source, status,
        il, ii, rl, ri,
        label=f"AR risk: [{source}] {title_en[:50]} L{il}×I{ii} → L{rl}×I{ri}",
    )

# ══════════════════════════════════════════════════════════════════════
# Multi-tenant — STC Bank (org_id = 3, demo tenant)
# Telco-banking hybrid: mobile-first banking, payment switch, SS7.
# ══════════════════════════════════════════════════════════════════════

print("\n\n" + "═" * 70)
print(f"  STC — STC Bank Demo Tenant (org_id={ORG_STC})")
print("═" * 70)

# ──────────────────────────────────────────────────────────────────────
# STC — cyber assets (telco-banking hybrid)
# ──────────────────────────────────────────────────────────────────────
section("cyber.cyber_assets — STC Bank telco-banking stack")

STC_ASSETS = [
    # (name, type, biz_owner, tech_owner, classification, criticality, ip, hostname, os)
    ("Mobile Banking Backend — Spring Boot K8s",
     "APPLICATION", "Hessa Al-Subaie", "Bandar Al-Otaibi",  "RESTRICTED",   "TIER_1",
     "10.50.10.10", "mob-be-prod-01",   "EKS 1.29 (Linux)"),
    ("Mobile Banking iOS App (Apple Store)",
     "APPLICATION", "Hessa Al-Subaie", "Adel Al-Mutairi",   "CONFIDENTIAL", "TIER_1",
     None,           "stc-ios-bundle",   None),
    ("Mobile Banking Android App (Google Play)",
     "APPLICATION", "Hessa Al-Subaie", "Adel Al-Mutairi",   "CONFIDENTIAL", "TIER_1",
     None,           "stc-android-bundle",None),
    ("Payment Switch — Ericsson Money",
     "APPLICATION", "Talal Al-Anazi",  "Saud Al-Kharji",    "RESTRICTED",   "TIER_1",
     "10.50.30.5",  "ericsson-money",   "Solaris 11.4"),
    ("USSD Banking Gateway — *900#",
     "APPLICATION", "Talal Al-Anazi",  "Saud Al-Kharji",    "RESTRICTED",   "TIER_1",
     "10.50.40.5",  "ussd-gw-prod",     "RHEL 9.2"),
    ("SS7/SIGTRAN Signalling Gateway",
     "INFRASTRUCTURE","Saud Al-Kharji", "Adel Al-Mutairi",  "RESTRICTED",   "TIER_1",
     "10.50.50.5",  "ss7-sigtran",      "RHEL 8.6"),
    ("Cards Personalisation Bureau — HSM Cluster",
     "INFRASTRUCTURE","Talal Al-Anazi", "Saud Al-Kharji",   "RESTRICTED",   "TIER_1",
     "10.50.60.5",  "hsm-cluster-prod", "PROPRIETARY"),
    ("KYC OTP Service",
     "APPLICATION", "Hessa Al-Subaie", "Bandar Al-Otaibi",  "RESTRICTED",   "TIER_1",
     "10.50.70.10", "otp-svc-prod",     "Linux Alpine 3.19"),
    ("Customer Voice Biometrics Engine",
     "APPLICATION", "Talal Al-Anazi",  "Adel Al-Mutairi",   "CONFIDENTIAL", "TIER_2",
     "10.50.80.10", "voicebio-prod",    "Ubuntu 22.04"),
    ("Fraud Detection — Featurespace ARIC",
     "APPLICATION", "Talal Al-Anazi",  "Bandar Al-Otaibi",  "RESTRICTED",   "TIER_1",
     "10.50.90.10", "aric-prod-01",     "RHEL 9.2"),
]
for name, atype, biz, tech, cls, crit, ip, host, os_name in STC_ASSETS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM cyber.cyber_assets
                       WHERE asset_name = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO cyber.cyber_assets (external_id, organisation_id, asset_name, asset_type,
            business_owner, technical_owner, classification, criticality, ip_address, hostname,
            operating_system, linked_vulnerabilities_count, linked_incidents_count,
            linked_controls_count, status, is_deleted, created_at)
        VALUES (LOWER(REPLACE(NEWID(), '-', '')), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0,
            'ACTIVE', 0, GETDATE())
        """,
        name, ORG_STC,
        ORG_STC, name, atype, biz, tech, cls, crit, ip, host, os_name,
        label=f"STC asset: {name}",
    )

# ──────────────────────────────────────────────────────────────────────
# STC — privacy consents (mobile + telco-banking scenarios)
# ──────────────────────────────────────────────────────────────────────
section("privacy.consents — STC Bank mobile + telco data sharing")

STC_CONSENTS = [
    ("Mobile banking biometric login (face/fingerprint)",
     "تسجيل الدخول البيومتري للخدمات المصرفية للجوّال (الوجه/البصمة)", "APP",       "ACTIVE"),
    ("USSD *900# transaction confirmation",
     "تأكيد المعاملات عبر USSD *900#",                                    "USSD",      "ACTIVE"),
    ("Voice biometrics for IVR authentication",
     "بصمة الصوت لمصادقة IVR",                                            "CALL",      "ACTIVE"),
    ("SMS OTP for transactions over SAR 1,000",
     "OTP عبر الرسائل القصيرة للمعاملات فوق 1,000 ريال",                "SMS",       "ACTIVE"),
    ("Location-based ATM finder + offers",
     "خدمة محدد ATM والعروض حسب الموقع",                                   "APP",       "ACTIVE"),
    ("Behavioural biometrics for fraud detection",
     "البصمات السلوكية لكشف الاحتيال",                                     "APP",       "ACTIVE"),
    ("STC Pay wallet linking",
     "ربط محفظة STC Pay",                                                  "API",       "ACTIVE"),
    ("Marketing SMS — product offers",
     "رسائل تسويق — عروض المنتجات",                                        "SMS",       "WITHDRAWN"),
]
for purpose_en, purpose_ar, channel, status in STC_CONSENTS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM privacy.consents
                       WHERE purpose_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO privacy.consents (external_id, organisation_id, purpose_en, purpose_ar,
            channel, version_label, effective_date, status, is_deleted, created_at)
        VALUES (NEWID(), ?, ?, ?, ?, 'v1', GETDATE(), ?, 0, GETDATE())
        """,
        purpose_en, ORG_STC,
        ORG_STC, purpose_en, purpose_ar, channel, status,
        label=f"STC consent: {purpose_en[:50]}",
    )

# ──────────────────────────────────────────────────────────────────────
# STC — enterprise risks (telco-bank specific)
# ──────────────────────────────────────────────────────────────────────
section("risk.risks — STC Bank enterprise risk register")

STC_RISKS = [
    # (title_en, title_ar, source, status, il, ii, rl, ri)
    ("SS7/SIGTRAN attack on OTP delivery",
     "هجوم SS7/SIGTRAN على تسليم OTP",
     "CYBER",     "MITIGATING", 3, 5, 2, 4),
    ("SIM-swap fraud on high-value customers",
     "الاحتيال بتبديل شريحة SIM للعملاء الفئة العليا",
     "CYBER",     "MITIGATING", 4, 4, 2, 3),
    ("Payment switch DDoS during peak (Hajj+Ramadan)",
     "هجوم DDoS على محوّل المدفوعات في الذروة (الحج ورمضان)",
     "CYBER",     "MITIGATING", 3, 5, 2, 3),
    ("Mobile-banking malware (RATs targeting Saudi Android)",
     "البرمجيات الخبيثة على الخدمات المصرفية للجوّال (RATs أندرويد)",
     "CYBER",     "MITIGATING", 4, 4, 3, 3),
    ("Open-Banking aggregator data leak",
     "تسرّب بيانات مُجمّع المنظومة المفتوحة",
     "PRIVACY",   "MITIGATING", 3, 5, 2, 4),
    ("USSD man-in-the-middle on shared SIM tower",
     "هجوم وسيط على USSD عبر برج SIM مشترك",
     "CYBER",     "MITIGATING", 2, 4, 1, 3),
    ("Voice biometrics deepfake attack",
     "هجوم التزييف العميق على بصمة الصوت",
     "CYBER",     "UNDER_REVIEW", 3, 4, 2, 3),
    ("CMA short-selling regulation compliance",
     "الامتثال للوائح البيع على المكشوف — هيئة السوق المالية",
     "COMPLIANCE","MITIGATING", 2, 4, 1, 3),
    ("Card personalisation HSM key compromise",
     "اختراق مفاتيح HSM لتجهيز البطاقات",
     "CYBER",     "MITIGATING", 2, 5, 1, 4),
    ("STC Pay wallet linking customer-due-diligence gap",
     "فجوة العناية الواجبة في ربط محفظة STC Pay",
     "COMPLIANCE","MITIGATING", 3, 4, 2, 3),
]
for title_en, title_ar, source, status, il, ii, rl, ri in STC_RISKS:
    # inherent_score / residual_score / risk_level are computed columns — let DB derive.
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM risk.risks
                       WHERE title_en = ? AND organisation_id = ? AND is_deleted = 0)
        INSERT INTO risk.risks (external_id, organisation_id, title_en, title_ar,
            source_module, status, inherent_likelihood, inherent_impact,
            residual_likelihood, residual_impact,
            identified_date, review_date, attestation_cadence_days,
            created_at, updated_at, is_deleted)
        VALUES (NEWID(), ?, ?, ?, ?, ?, ?, ?, ?, ?,
            DATEADD(month, -3, CAST(GETDATE() AS DATE)),
            DATEADD(month, 3, CAST(GETDATE() AS DATE)),
            90, GETDATE(), GETDATE(), 0)
        """,
        title_en, ORG_STC,
        ORG_STC, title_en, title_ar, source, status,
        il, ii, rl, ri,
        label=f"STC risk: [{source}] {title_en[:50]} L{il}×I{ii} → L{rl}×I{ri}",
    )

# ──────────────────────────────────────────────────────────────────────
print(f"\n══════ Summary ══════")
print(f"  inserted: {inserted}")
print(f"  skipped (already present): {skipped}")
print(f"  errors:   {errors}")

cur.close()
conn.close()
