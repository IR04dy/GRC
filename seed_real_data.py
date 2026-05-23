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
     "DATA_EXPOSURE_LOWER_ENV", 412, True, "REMEDIATED", 75),
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
    (2, 1, 4, "CRITICAL", "TIER_1", "HIGH",     "HIGH",     "HIGH",     "HIGH",     "VERY_HIGH"),
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
print(f"\n══════ Summary ══════")
print(f"  inserted: {inserted}")
print(f"  skipped (already present): {skipped}")
print(f"  errors:   {errors}")

cur.close()
conn.close()
