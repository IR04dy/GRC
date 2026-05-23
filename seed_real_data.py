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
    """Execute INSERT, return True if a row was inserted."""
    global inserted, skipped, errors
    try:
        cur.execute(sql, params)
        n = cur.rowcount
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
print(f"\n══════ Summary ══════")
print(f"  inserted: {inserted}")
print(f"  skipped (already present): {skipped}")
print(f"  errors:   {errors}")

cur.close()
conn.close()
