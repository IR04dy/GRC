# -*- coding: utf-8 -*-
"""Seed Saudi-government cybersecurity data into the `cyber` and `cyber_lc` schemas.

Companion to seed_gov_committees.py / seed_gov_hierarchy.py. Targets the default
tenant (organisation_id = 1) — a Saudi GENERAL AUTHORITY (هيئة عامة) headed by a
President — and layers an authentic GOVERNMENT-SYSTEMS cyber posture on top of the
legacy banking demo data that seed_real_data.py left behind.

Why a parallel layer (not an edit): the org-1 cyber registers are currently
banking-themed (T24, SADAD, Mada Pay, *.tss-bank.sa). The hard safety rule is
INSERT-ONLY — never modify existing rows. So this script:

  • FILLS the three empty cyber_lc lifecycle tables (assets / cyber_incidents /
    threat_intel) — and crucially mints asset_codes AST-001..AST-014 so the 30
    existing-but-dangling cyber_lc.vulnerabilities rows finally resolve to assets;
  • DEEPENS the live registers with a government layer: government information
    assets (ERP/GRP, Etimad, national e-service portal, Nafath/Absher, Active
    Directory, Government Service Bus, DGA Najiz/Tarasul …), NCA-ECC control
    assessments, cyber incidents with NCA HSOC / SDAIA notification, vulnerabilities,
    CAPA/action plans, evidence, root-cause analyses, attack-surface assets &
    findings, government cloud services (NCA CSCC / in-Kingdom residency), and
    vulnerability-SLA clock findings;
  • SKIPS the regulatory catalogues (cyber_controls 154, cyber_sub_controls 529,
    cyber_frameworks 6) and a few niche/blocked tables (see SKIP report at the end).

Ownership model: the legacy register tables carry ownership as PLAIN-TEXT columns
(business_owner / technical_owner / owner_name / incident_lead / assessor_name /
facilitator), so we tie work to CYBER / SOC / CYBER_GRC by writing their names
('Cybersecurity', 'Security Operations Center', 'Chief Information Security
Officer', 'Cyber Risk & Compliance') and reference the COM-CYBER 'Cybersecurity
Supervisory Committee' (NCA ECC 1-1) in narrative fields. The only id-FK-to-users
columns (attack_surface_asset.asset_owner_user_id, cloud_services.owner_user_id)
point at admin (id 1) — the only available org-1 user with an owner-grade role.

Idempotent: every INSERT is guarded by a natural key (external_id / code /
unique title) so a second run inserts 0. Bilingual: every user-visible string has
an English and a natural Saudi-Arabic value.

    Usage:  python seed_gov_cyber.py
"""
from __future__ import annotations

import sys
from datetime import datetime, timedelta, timezone

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
ADMIN = 1  # only org-1 user with an owner-grade role (no CISO/SOC user exists)
NOW = datetime(2026, 6, 27, 9, 0, 0)  # deterministic "today" for SLA math


def dt(days_ago=0, hours_ago=0):
    return NOW - timedelta(days=days_ago, hours=hours_ago)


# Per-table counters: {table: [inserted, skipped]}
STATS: dict[str, list[int]] = {}


def bump(table: str, did_insert: bool):
    s = STATS.setdefault(table, [0, 0])
    if did_insert:
        s[0] += 1
    else:
        s[1] += 1


# ─────────────────────────────────────────────────────────────────────────────
#  cyber_lc.assets  (FILL) — government information-asset inventory.
#  asset_code values AST-001..AST-014 deliberately match the dangling
#  cyber_lc.vulnerabilities.asset_id strings so the lifecycle becomes coherent.
#  NOTE: cyber_lc.assets has NO asset_code column — asset_code is stored in
#  external_id (natural key). cyber_lc.vulnerabilities.asset_id references it.
# ─────────────────────────────────────────────────────────────────────────────
LC_ASSETS = [
    # code, name_en, name_ar, type, classification_ar, criticality, biz_owner, tech_owner, env, site
    ("AST-001", "ERP/GRP Financial & Resource Planning System", "نظام تخطيط موارد الجهة (الموارد المالية والبشرية)",
     "APPLICATION", "سري", "CRITICAL", "Finance", "Information Technology", "PRODUCTION", "Primary Data Center - Riyadh"),
    ("AST-002", "Etimad Procurement Integration", "تكامل منصة اعتماد للمشتريات الحكومية",
     "APPLICATION", "مقيّد", "HIGH", "Procurement", "Information Technology", "PRODUCTION", "Primary Data Center - Riyadh"),
    ("AST-003", "National Beneficiary e-Service Portal", "بوابة الخدمات الإلكترونية للمستفيدين",
     "APPLICATION", "مقيّد", "CRITICAL", "Operations & Services", "Digital Transformation", "PRODUCTION", "Government Cloud - Riyadh"),
    ("AST-004", "Nafath / Absher SSO Integration", "تكامل الدخول الموحّد (نفاذ / أبشر)",
     "APPLICATION", "سري", "CRITICAL", "Digital Transformation", "Information Technology", "PRODUCTION", "Government Cloud - Riyadh"),
    ("AST-005", "Tawakkalna Services Gateway", "بوابة خدمات توكلنا",
     "APPLICATION", "مقيّد", "HIGH", "Operations & Services", "Digital Transformation", "PRODUCTION", "Government Cloud - Riyadh"),
    ("AST-006", "Active Directory Domain Services", "خدمات الدليل النشط (Active Directory)",
     "INFRASTRUCTURE", "سري", "CRITICAL", "Information Technology", "Information Technology", "PRODUCTION", "Primary Data Center - Riyadh"),
    ("AST-007", "Government Service Bus (GSB)", "ناقل الخدمات الحكومي (GSB)",
     "INFRASTRUCTURE", "سري", "CRITICAL", "Digital Transformation", "Information Technology", "PRODUCTION", "Primary Data Center - Riyadh"),
    ("AST-008", "Licensing & Inspection System", "نظام التراخيص والتفتيش الميداني",
     "APPLICATION", "مقيّد", "HIGH", "Operations & Services", "Information Technology", "PRODUCTION", "Primary Data Center - Riyadh"),
    ("AST-009", "Ma'an Correspondence & Workflow System", "نظام (معًا) للمراسلات والاتصالات الإدارية",
     "APPLICATION", "مقيّد", "MEDIUM", "General Services", "Information Technology", "PRODUCTION", "Primary Data Center - Riyadh"),
    ("AST-010", "Public Authority Website & Wildcard TLS", "الموقع الإلكتروني الرسمي وشهادة TLS العامة",
     "APPLICATION", "عام", "MEDIUM", "Corporate Communication", "Digital Transformation", "PRODUCTION", "Government Cloud - Riyadh"),
    ("AST-011", "HR & Payroll System (Masar)", "نظام الموارد البشرية والرواتب (مسار)",
     "APPLICATION", "سري", "HIGH", "Human Resources", "Information Technology", "PRODUCTION", "Primary Data Center - Riyadh"),
    ("AST-012", "BI & Data Warehouse Platform", "منصة ذكاء الأعمال ومستودع البيانات",
     "DATABASE", "سري", "HIGH", "Data Management Office", "Data & AI", "PRODUCTION", "Primary Data Center - Riyadh"),
    ("AST-013", "DGA Najiz / Tarasul Gateway", "بوابة هيئة الحكومة الرقمية (ناجز / تراسل)",
     "APPLICATION", "مقيّد", "HIGH", "Digital Transformation", "Information Technology", "PRODUCTION", "Government Cloud - Riyadh"),
    ("AST-014", "Backup & Disaster Recovery Site", "موقع النسخ الاحتياطي والتعافي من الكوارث",
     "INFRASTRUCTURE", "سري", "CRITICAL", "Information Technology", "Information Technology", "DR", "DR Data Center - Jeddah"),
]


def seed_lc_assets(cur):
    print("\n[cyber_lc.assets] government information-asset inventory")
    for code, name_en, name_ar, atype, classif, crit, bowner, towner, env, site in LC_ASSETS:
        cur.execute(
            "SELECT id FROM cyber_lc.assets WHERE organisation_id=? AND external_id=?",
            ORG, code,
        )
        if cur.fetchone():
            bump("cyber_lc.assets", False)
            continue
        # store both EN and AR in asset_name so the UI can show either; primary EN.
        cur.execute(
            """INSERT INTO cyber_lc.assets
                   (external_id, organisation_id, asset_name, asset_type, classification,
                    business_owner, technical_owner, location_site, environment,
                    criticality, operational_status, is_deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0)""",
            code, ORG, f"{name_en} | {name_ar}", atype, classif,
            bowner, towner, site, env, crit,
        )
        bump("cyber_lc.assets", True)
        print(f"  + {code}  {name_en}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber_lc.cyber_incidents  (FILL) — government incident lifecycle with NCA HSOC
#  + SDAIA/PDPL notification timestamps.  natural key = external_id (GOV-INC-...).
# ─────────────────────────────────────────────────────────────────────────────
LC_INCIDENTS = [
    # code, title_en, title_ar, severity, classification, det_days, contained_days, eradicated_days,
    #   recovered_days, status, nca_days(or None), nca_ref, sdaia_days(or None), rc_en, rc_ar
    ("GOV-INC-2026-001", "DDoS against the National Beneficiary e-Service Portal",
     "هجوم حجب خدمة موزّع على بوابة الخدمات الإلكترونية للمستفيدين",
     "HIGH", "DDoS", 40, 39, 38, 37, "CLOSED", 40, "NCA-HSOC-2026-014", None,
     "Absence of upstream DDoS scrubbing and rate-limiting at the portal edge; volumetric flood saturated the public gateway. NCA HSOC notified; reviewed by the Cybersecurity Supervisory Committee (COM-CYBER, ECC 1-1).",
     "غياب خدمة تنقية هجمات حجب الخدمة وتحديد المعدّل عند حافة البوابة؛ أدى الفيضان الحجمي إلى إشباع البوابة العامة. تم إبلاغ مركز العمليات الأمنية الوطني، واستعرضت الحالة اللجنة الإشرافية للأمن السيبراني وفق الضابط 1-1."),
    ("GOV-INC-2026-002", "Phishing campaign harvesting Nafath employee credentials",
     "حملة تصيّد إلكتروني لسرقة بيانات دخول الموظفين عبر نفاذ",
     "HIGH", "PHISHING", 33, 33, 32, 31, "CLOSED", 33, "NCA-HSOC-2026-021", None,
     "Targeted phishing emails impersonated the Nafath login page; lack of phishing-resistant MFA allowed credential capture. Remediated with awareness campaign and conditional access.",
     "رسائل تصيّد موجّهة انتحلت صفحة دخول نفاذ؛ أتاح غياب التحقق متعدد العوامل المقاوم للتصيّد التقاط بيانات الدخول. عولجت بحملة توعية وتطبيق الوصول المشروط."),
    ("GOV-INC-2026-003", "Ransomware indicator contained on an internal file server",
     "مؤشر برمجية فدية تم احتواؤه على خادم ملفات داخلي",
     "CRITICAL", "RANSOMWARE", 28, 28, 27, 25, "CLOSED", 28, "NCA-HSOC-2026-030", None,
     "Malicious payload detected by EDR on a file server; isolated before encryption spread. Root cause: unpatched SMBv1 service. Backups verified per ECC 2-9.",
     "اكتشفت أداة حماية النقاط الطرفية حمولة خبيثة على خادم ملفات وعُزل قبل انتشار التشفير. السبب الجذري: خدمة SMBv1 غير محدّثة. تم التحقق من النسخ الاحتياطية وفق الضابط 2-9."),
    ("GOV-INC-2026-004", "Unauthorized access attempt to the ERP/GRP admin console",
     "محاولة وصول غير مصرّح به إلى وحدة إدارة نظام الموارد",
     "HIGH", "UNAUTHORIZED_ACCESS", 22, 22, 21, 21, "CLOSED", 22, "NCA-HSOC-2026-037", None,
     "Repeated failed logins from an internal host against the ERP/GRP admin console; account locked and source workstation re-imaged. Privileged access moved behind MFA.",
     "محاولات دخول فاشلة متكررة من مضيف داخلي على وحدة إدارة نظام الموارد؛ أُقفل الحساب وأُعيدت تهيئة جهاز المصدر، ونُقل الوصول المتميّز خلف التحقق متعدد العوامل."),
    ("GOV-INC-2026-005", "Suspected PII exfiltration from the beneficiary database",
     "اشتباه في تسريب بيانات شخصية من قاعدة بيانات المستفيدين",
     "CRITICAL", "DATA_BREACH", 18, 18, 17, 16, "CLOSED", 18, "NCA-HSOC-2026-044", 18,
     "Anomalous bulk query against the beneficiary database flagged by DLP; investigation found no confirmed exfiltration but SDAIA was notified under PDPL as a precaution. TDE and query monitoring enforced.",
     "استعلام مجمّع غير اعتيادي على قاعدة بيانات المستفيدين رصدته أدوات منع تسرّب البيانات؛ لم يثبت تسريب فعلي وأُبلغت الهيئة السعودية للبيانات والذكاء الاصطناعي احترازياً وفق نظام حماية البيانات الشخصية. فُعّل التشفير الشفاف ومراقبة الاستعلامات."),
    ("GOV-INC-2026-006", "Exposed Etimad integration token in a code repository",
     "كشف مفتاح تكامل اعتماد في مستودع شيفرة برمجية",
     "MEDIUM", "DATA_BREACH", 14, 14, 13, 13, "CLOSED", None, None, None,
     "An integration secret for the Etimad connector was committed to an internal Git repository; secret rotated immediately and secret-scanning enabled in the pipeline (ECC 2-3).",
     "تم إدراج مفتاح تكامل موصّل اعتماد عن طريق الخطأ في مستودع شيفرة داخلي؛ جرى تدويره فوراً وتفعيل فحص الأسرار في خط الإنتاج وفق الضابط 2-3."),
    ("GOV-INC-2026-007", "Brute-force attempts against Active Directory via VPN",
     "محاولات تخمين كلمات المرور على الدليل النشط عبر الشبكة الافتراضية",
     "MEDIUM", "UNAUTHORIZED_ACCESS", 11, 11, 10, 10, "RECOVERED", 11, "NCA-HSOC-2026-051", None,
     "Password-spray against AD accounts originating from the VPN gateway; lockout thresholds and geo-velocity rules tightened. No accounts compromised.",
     "هجوم رشّ كلمات المرور على حسابات الدليل النشط من بوابة الشبكة الافتراضية؛ شُدِّدت حدود الإقفال وقواعد السرعة الجغرافية ولم تُخترق أي حسابات."),
    ("GOV-INC-2026-008", "Malicious attachment in the Ma'an correspondence system",
     "مرفق خبيث في نظام (معًا) للمراسلات",
     "MEDIUM", "MALWARE", 8, 8, 7, 7, "RECOVERED", None, None, None,
     "A weaponized document reached an inbox via the correspondence system; sandbox detonation confirmed malware and the message was purged. Email protection rules updated (ECC 2-4).",
     "وصلت وثيقة مُسلّحة إلى صندوق بريد عبر نظام المراسلات؛ أكّد التحليل المعزول وجود برمجية خبيثة وحُذفت الرسالة وحُدّثت قواعد حماية البريد وفق الضابط 2-4."),
    ("GOV-INC-2026-009", "Web-shell detected on the licensing portal",
     "اكتشاف غلاف ويب خبيث على بوابة التراخيص",
     "HIGH", "VULN_EXPLOIT", 5, 5, 4, 3, "ERADICATED", 5, "NCA-HSOC-2026-058", None,
     "A web-shell was planted via an unpatched web framework on the licensing portal; host isolated, web-shell removed, and framework patched per ECC 2-15.",
     "زُرع غلاف ويب خبيث عبر إطار عمل غير محدّث على بوابة التراخيص؛ عُزل المضيف وأُزيل الغلاف وحُدّث الإطار وفق الضابط 2-15."),
    ("GOV-INC-2026-010", "Supply-chain advisory affecting a government SaaS vendor",
     "تنبيه سلسلة إمداد يخصّ مورّد خدمة حكومية سحابية",
     "MEDIUM", "VULN_EXPLOIT", 2, None, None, None, "CONTAINED", 2, "NCA-HSOC-2026-063", None,
     "NCA advisory reported a compromise at a SaaS vendor used by the Authority; access tokens rotated and vendor assessment initiated under ECC 4-1 (third-party cybersecurity).",
     "أصدرت الهيئة الوطنية للأمن السيبراني تنبيهاً باختراق مورّد خدمة سحابية تستخدمه الجهة؛ جرى تدوير الرموز وبدء تقييم المورّد وفق الضابط 4-1 للأمن السيبراني للأطراف الخارجية."),
]


def seed_lc_incidents(cur):
    print("\n[cyber_lc.cyber_incidents] government incident lifecycle (NCA HSOC / SDAIA)")
    for (code, ten, tar, sev, classif, dd, cd, ed, rd, status,
         nca_d, nca_ref, sdaia_d, rc_en, rc_ar) in LC_INCIDENTS:
        cur.execute(
            "SELECT id FROM cyber_lc.cyber_incidents WHERE organisation_id=? AND external_id=?",
            ORG, code,
        )
        if cur.fetchone():
            bump("cyber_lc.cyber_incidents", False)
            continue
        cur.execute(
            """INSERT INTO cyber_lc.cyber_incidents
                   (external_id, organisation_id, title_en, title_ar, severity,
                    classification, detected_at, contained_at, eradicated_at, recovered_at,
                    root_cause_en, root_cause_ar, nca_notified_at, nca_reference,
                    sdaia_notified_at, status, is_deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""",
            code, ORG, ten, tar, sev, classif,
            dt(days_ago=dd),
            dt(days_ago=cd) if cd is not None else None,
            dt(days_ago=ed) if ed is not None else None,
            dt(days_ago=rd) if rd is not None else None,
            rc_en, rc_ar,
            dt(days_ago=nca_d) if nca_d is not None else None, nca_ref,
            dt(days_ago=sdaia_d) if sdaia_d is not None else None,
            status,
        )
        bump("cyber_lc.cyber_incidents", True)
        print(f"  + {code}  {ten}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber_lc.threat_intel  (FILL) — NCA / CERT-SA advisory feed mapped to the
#  Authority's stack.  natural key = external_id (TI-...).
# ─────────────────────────────────────────────────────────────────────────────
LC_THREATINTEL = [
    # code, cve, title_en, title_ar, severity, cvss, source, summary_en, summary_ar, products, mitig_ar
    ("TI-2026-001", "CVE-2026-0011", "Critical RCE in on-prem Microsoft Exchange",
     "ثغرة تنفيذ تعليمات عن بُعد حرجة في خوادم Exchange المحلية",
     "CRITICAL", 9.8, "NCA",
     "NCA alert: unauthenticated remote code execution in on-premises Exchange; active exploitation observed against government mail servers.",
     "تنبيه الهيئة الوطنية للأمن السيبراني: تنفيذ تعليمات عن بُعد دون مصادقة في خوادم Exchange المحلية مع رصد استغلال نشط ضد خوادم البريد الحكومية.",
     "Microsoft Exchange Server 2016/2019",
     "تطبيق التحديثات الأمنية خلال 7 أيام للثغرات الحرجة وفق نظام معالجة الثغرات للهيئة الوطنية، وعزل الواجهات الإدارية."),
    ("TI-2026-002", "CVE-2026-0023", "MOVEit / managed file-transfer SQLi",
     "ثغرة حقن SQL في حلول النقل المُدار للملفات (MOVEit)",
     "CRITICAL", 9.1, "CERT-SA",
     "CERT-SA advisory: SQL injection in managed file-transfer software enabling data theft from government file-exchange portals.",
     "نشرة المركز الوطني الإرشادي (CERT-SA): حقن SQL في برمجيات النقل المُدار للملفات يتيح سرقة البيانات من بوابات تبادل الملفات الحكومية.",
     "MOVEit Transfer; managed file-transfer gateways",
     "تحديث فوري وتقييد الوصول العام وتفعيل جدار حماية تطبيقات الويب أمام بوابات النقل."),
    ("TI-2026-003", "CVE-2026-0034", "Fortinet / Palo Alto VPN authentication bypass",
     "تجاوز المصادقة في بوابات VPN (Fortinet / Palo Alto)",
     "CRITICAL", 9.6, "NCA",
     "Authentication bypass in SSL-VPN appliances allowing remote takeover; recommended for immediate patching on perimeter VPNs.",
     "تجاوز للمصادقة في أجهزة SSL-VPN يسمح بالسيطرة عن بُعد؛ يوصى بالتحديث الفوري لبوابات الشبكة الافتراضية المحيطية.",
     "FortiOS SSL-VPN; Palo Alto GlobalProtect",
     "تطبيق التحديث خلال 7 أيام وتفعيل التحقق متعدد العوامل المقاوم للتصيّد على الوصول عن بُعد."),
    ("TI-2026-004", "CVE-2026-0045", "Citrix NetScaler memory disclosure",
     "تسريب ذاكرة في أجهزة Citrix NetScaler",
     "HIGH", 8.2, "CERT-SA",
     "Memory disclosure in NetScaler ADC/Gateway exposing session tokens; observed reconnaissance against public-sector gateways.",
     "تسريب ذاكرة في أجهزة NetScaler يكشف رموز الجلسات؛ رُصد استطلاع ضد بوابات القطاع الحكومي.",
     "Citrix NetScaler ADC / Gateway",
     "تحديث الجهاز وإبطال الجلسات النشطة وتدوير رموز الجلسات."),
    ("TI-2026-005", "CVE-2021-44228", "Apache Log4j RCE (Log4Shell) resurgence",
     "عودة استغلال ثغرة Log4Shell في مكتبة Log4j",
     "CRITICAL", 10.0, "NCA",
     "Renewed scanning for Log4Shell against Java applications powering national portals; verify all dependencies are remediated.",
     "تجدّد عمليات المسح لاستغلال Log4Shell ضد تطبيقات Java المشغّلة للبوابات الوطنية؛ يجب التحقق من معالجة جميع التبعيات.",
     "Apache Log4j 2.x; Java web applications",
     "ترقية Log4j وحظر استدعاءات JNDI الخارجية وفحص التبعيات."),
    ("TI-2026-006", "CVE-2020-1472", "Windows Netlogon privilege escalation (Zerologon)",
     "تصعيد صلاحيات في Netlogon بنظام Windows (Zerologon)",
     "CRITICAL", 10.0, "NCA",
     "Domain-controller takeover via Netlogon; ensure all DCs enforce secure RPC. Relevant to the Authority's Active Directory.",
     "السيطرة على وحدة التحكم بالنطاق عبر Netlogon؛ يجب فرض RPC الآمن على جميع وحدات التحكم، وهي ذات صلة بالدليل النشط للجهة.",
     "Windows Server Active Directory Domain Controllers",
     "تطبيق التحديث وفرض التوقيع الآمن لـ Netlogon ومراجعة سجلات المصادقة."),
    ("TI-2026-007", "CVE-2026-0067", "Spring Framework expression-injection RCE",
     "تنفيذ تعليمات عن بُعد عبر حقن التعابير في إطار Spring",
     "HIGH", 8.8, "CERT-SA",
     "Expression-language injection in Spring-based web apps; national e-service portals using outdated Spring are at risk.",
     "حقن لغة التعابير في تطبيقات الويب المبنية على Spring؛ بوابات الخدمات الإلكترونية التي تستخدم إصدارات قديمة من Spring معرّضة للخطر.",
     "Spring Framework; Spring Boot web applications",
     "ترقية Spring إلى الإصدار المعالَج خلال 30 يوماً للثغرات عالية الخطورة وفق سياسة الثغرات."),
    ("TI-2026-008", "CVE-2026-0078", "Ivanti gateway command injection",
     "حقن أوامر في بوابات Ivanti",
     "CRITICAL", 9.4, "NCA",
     "Unauthenticated command injection in Ivanti gateways with active exploitation; isolate and patch immediately.",
     "حقن أوامر دون مصادقة في بوابات Ivanti مع استغلال نشط؛ يجب العزل والتحديث الفوري.",
     "Ivanti Connect Secure; Ivanti Policy Secure",
     "العزل الفوري والتحديث وإعادة بناء الأجهزة المشتبه باختراقها."),
    ("TI-2026-009", "CVE-2026-0089", "libxml2 heap overflow in XML parsing",
     "تجاوز سعة الكومة في libxml2 عند تحليل XML",
     "MEDIUM", 6.5, "CERT-SA",
     "Heap overflow in libxml2 affecting services that parse untrusted XML, including some integration buses.",
     "تجاوز سعة الكومة في مكتبة libxml2 يؤثر على الخدمات التي تحلّل XML غير الموثوق، بما فيها بعض نواقل التكامل.",
     "libxml2; XML processing services",
     "تحديث المكتبة والتحقق من صحة مدخلات XML."),
    ("TI-2026-010", "CVE-2026-0090", "OpenSSL denial-of-service in certificate parsing",
     "حجب خدمة في OpenSSL عند تحليل الشهادات",
     "MEDIUM", 5.9, "NCA",
     "Malformed certificate can crash OpenSSL-based services; relevant to TLS endpoints on public gateways.",
     "شهادة مشوّهة قد تُعطّل الخدمات المعتمدة على OpenSSL؛ ذات صلة بنقاط TLS على البوابات العامة.",
     "OpenSSL 3.x; TLS-terminating services",
     "تحديث OpenSSL ومراجعة نقاط إنهاء TLS."),
    ("TI-2026-011", "CVE-2026-0101", "DNS resolver cache-poisoning weakness",
     "ضعف تسميم ذاكرة محلّل أسماء النطاقات",
     "HIGH", 7.4, "CERT-SA",
     "Cache-poisoning weakness in DNS resolvers could redirect government domains; deploy DNSSEC validation.",
     "ضعف تسميم الذاكرة في محلّلات DNS قد يعيد توجيه النطاقات الحكومية؛ يوصى بتفعيل التحقق عبر DNSSEC.",
     "DNS resolvers; recursive name servers",
     "تفعيل DNSSEC وتقييد التحويل العام ومراقبة سجلات DNS."),
    ("TI-2026-012", "CVE-2026-0112", "Apache HTTP Server path-traversal RCE",
     "ثغرة اجتياز المسار وتنفيذ التعليمات في خادم Apache",
     "HIGH", 8.1, "NCA",
     "Path-traversal leading to RCE in Apache HTTP Server; public websites and portals fronted by Apache should patch within SLA.",
     "اجتياز المسار يؤدي إلى تنفيذ تعليمات عن بُعد في خادم Apache؛ يجب تحديث المواقع والبوابات العامة المعتمدة على Apache ضمن مدة الالتزام.",
     "Apache HTTP Server 2.4.x",
     "تطبيق التحديث خلال 30 يوماً وتفعيل قواعد جدار حماية تطبيقات الويب."),
]


def seed_lc_threatintel(cur):
    print("\n[cyber_lc.threat_intel] NCA / CERT-SA advisory feed")
    for (code, cve, ten, tar, sev, cvss, src, sum_en, sum_ar, prod, mit_ar) in LC_THREATINTEL:
        cur.execute(
            "SELECT id FROM cyber_lc.threat_intel WHERE organisation_id=? AND external_id=?",
            ORG, code,
        )
        if cur.fetchone():
            bump("cyber_lc.threat_intel", False)
            continue
        cur.execute(
            """INSERT INTO cyber_lc.threat_intel
                   (external_id, organisation_id, cve_id, title_en, title_ar, severity,
                    cvss_score, source, published_at, summary_en, summary_ar,
                    affected_products, mitigations_en, is_deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""",
            code, ORG, cve, ten, tar, sev, cvss, src, dt(days_ago=10),
            sum_en, sum_ar, prod, mit_ar,
        )
        bump("cyber_lc.threat_intel", True)
        print(f"  + {code}  {cve}  {ten}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber_lc.vulnerabilities  (DEEPEN) — government-specific vulns on the new
#  AST-mapped assets, correlated to threat-intel.  natural key = external_id.
# ─────────────────────────────────────────────────────────────────────────────
LC_VULNS = [
    # code, asset_code, cve, title_en, title_ar, sev, cvss, det_source, status, sla_days, ti_code, owner
    ("GOV-VLN-2026-001", "AST-001", "CVE-2026-0011", "Unpatched on-prem Exchange RCE on mail relay",
     "ثغرة تنفيذ عن بُعد غير محدّثة في خادم البريد المحلي",
     "CRITICAL", 9.8, "TENABLE", "IN_REMEDIATION", 7, "TI-2026-001"),
    ("GOV-VLN-2026-002", "AST-002", None, "Exposed Etimad integration endpoint without IP allow-list",
     "نقطة تكامل اعتماد مكشوفة دون قائمة عناوين مسموح بها",
     "HIGH", 7.5, "PEN_TEST", "OWNER_ASSIGNED", 30, None),
    ("GOV-VLN-2026-003", "AST-006", "CVE-2020-1472", "Active Directory privilege escalation (Zerologon-class)",
     "تصعيد صلاحيات في الدليل النشط من فئة Zerologon",
     "CRITICAL", 10.0, "TENABLE", "TRIAGED", 7, "TI-2026-006"),
    ("GOV-VLN-2026-004", "AST-003", "CVE-2026-0067", "Outdated Spring framework on the e-service portal",
     "إطار Spring قديم على بوابة الخدمات الإلكترونية",
     "HIGH", 8.8, "TENABLE", "IN_REMEDIATION", 30, "TI-2026-007"),
    ("GOV-VLN-2026-005", "AST-007", None, "Weak TLS configuration on the Government Service Bus",
     "إعداد TLS ضعيف على ناقل الخدمات الحكومي",
     "MEDIUM", 5.3, "MANUAL", "OWNER_ASSIGNED", 60, None),
    ("GOV-VLN-2026-006", "AST-012", None, "Missing Transparent Data Encryption on the beneficiary data warehouse",
     "غياب التشفير الشفاف للبيانات في مستودع بيانات المستفيدين",
     "HIGH", 7.4, "MANUAL", "NEW", 30, None),
]


def lc_asset_exists(cur, code) -> bool:
    cur.execute(
        "SELECT 1 FROM cyber_lc.assets WHERE organisation_id=? AND external_id=?",
        ORG, code,
    )
    return cur.fetchone() is not None


def seed_lc_vulns(cur):
    print("\n[cyber_lc.vulnerabilities] government-stack vulnerabilities (lifecycle)")
    for (code, asset_code, cve, ten, tar, sev, cvss, src, status, sla_days, ti) in LC_VULNS:
        cur.execute(
            "SELECT id FROM cyber_lc.vulnerabilities WHERE organisation_id=? AND external_id=?",
            ORG, code,
        )
        if cur.fetchone():
            bump("cyber_lc.vulnerabilities", False)
            continue
        sla_deadline = (dt(days_ago=3) + timedelta(days=sla_days)).date()
        cur.execute(
            """INSERT INTO cyber_lc.vulnerabilities
                   (external_id, organisation_id, asset_id, cve_id, title_en, title_ar,
                    severity, cvss_score, detection_source, status, detected_at,
                    owner, sla_deadline, breach_count, sla_extended, threat_intel_id, is_deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 0)""",
            code, ORG, asset_code, cve, ten, tar, sev, cvss, src, status,
            dt(days_ago=3), "Security Operations Center", sla_deadline, ti,
        )
        bump("cyber_lc.vulnerabilities", True)
        print(f"  + {code}  -> {asset_code}  {ten}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.control_assessments  (DEEPEN) — assess un-assessed NCA ECC controls.
#  natural key = (organisation_id, control_id) for our gov rows (ids 1-4 already
#  assessed; we add others).  @PrePersist supplies external_id+created_at.
# ─────────────────────────────────────────────────────────────────────────────
import uuid

# control_id -> (result, maturity, risk, status, gap_en, gap_ar)
CTRL_ASSESS = [
    (7, "COMPLIANT", 4, "LOW", "COMPLETED",
     "Cybersecurity strategy approved by the Cybersecurity Supervisory Committee (COM-CYBER) and aligned to NCA ECC and Vision 2030.",
     "اعتُمدت استراتيجية الأمن السيبراني من اللجنة الإشرافية للأمن السيبراني ووُوئمت مع الضوابط الأساسية ورؤية 2030."),
    (8, "COMPLIANT", 4, "LOW", "COMPLETED",
     "Cybersecurity function established under the CISO with a defined management program.",
     "أُنشئت وظيفة الأمن السيبراني تحت إشراف رئيس الأمن السيبراني مع برنامج إدارة معرّف."),
    (9, "PARTIALLY_COMPLIANT", 3, "MEDIUM", "COMPLETED",
     "Cybersecurity policies are documented but some procedures await annual review and committee re-approval.",
     "سياسات الأمن السيبراني موثّقة إلا أن بعض الإجراءات بانتظار المراجعة السنوية وإعادة اعتماد اللجنة."),
    (11, "PARTIALLY_COMPLIANT", 3, "MEDIUM", "COMPLETED",
     "Cyber risk register maintained; integration with enterprise risk and KRIs is in progress.",
     "سجل مخاطر الأمن السيبراني محدّث؛ ويجري ربطه بإدارة المخاطر المؤسسية ومؤشرات المخاطر."),
    (14, "PARTIALLY_COMPLIANT", 3, "MEDIUM", "IN_REVIEW",
     "Periodic cybersecurity review performed; an independent ECC audit by Internal Audit is scheduled.",
     "تُجرى المراجعة الدورية للأمن السيبراني؛ ومجدولة مراجعة مستقلة للضوابط الأساسية من المراجعة الداخلية."),
    (16, "COMPLIANT", 4, "LOW", "COMPLETED",
     "Annual security-awareness program delivered; phishing simulations run quarterly.",
     "تُنفّذ برامج التوعية الأمنية السنوية وتُجرى محاكاة التصيّد ربع السنوية."),
    (17, "PARTIALLY_COMPLIANT", 3, "MEDIUM", "COMPLETED",
     "IT asset inventory covers core government systems; CMDB reconciliation with the attack-surface register continues.",
     "يغطي جرد الأصول الأنظمة الحكومية الأساسية؛ وتستمر مطابقة قاعدة إدارة التهيئة مع سجل سطح الهجوم."),
    (18, "PARTIALLY_COMPLIANT", 3, "HIGH", "COMPLETED",
     "IAM enforced for most systems; MFA on privileged ERP/GRP and AD admin consoles still being rolled out.",
     "تُطبّق إدارة الهوية والوصول لمعظم الأنظمة؛ ويجري استكمال التحقق متعدد العوامل على وحدات إدارة الموارد والدليل النشط."),
    (20, "PARTIALLY_COMPLIANT", 3, "MEDIUM", "COMPLETED",
     "Email protection in place; advanced anti-phishing tuning continues after the Nafath phishing incident.",
     "حماية البريد مفعّلة؛ ويستمر ضبط مكافحة التصيّد المتقدّمة بعد حادثة تصيّد نفاذ."),
    (21, "NON_COMPLIANT", 2, "HIGH", "COMPLETED",
     "Network segmentation of the Government Service Bus is incomplete; remediation tracked as a CAPA per ECC 2-5.",
     "تجزئة الشبكة لناقل الخدمات الحكومي غير مكتملة؛ وتُتابع المعالجة كخطة تصحيحية وفق الضابط 2-5."),
    (23, "PARTIALLY_COMPLIANT", 3, "HIGH", "COMPLETED",
     "Data protection controls applied; TDE on the beneficiary data warehouse is a tracked gap (NDMO classification).",
     "تُطبّق ضوابط حماية البيانات؛ ويُعدّ التشفير الشفاف لمستودع بيانات المستفيدين ثغرة متابَعة وفق تصنيف مكتب إدارة البيانات."),
    (25, "COMPLIANT", 4, "LOW", "COMPLETED",
     "Backup and recovery validated for critical government systems with periodic restore tests at the DR site.",
     "تم التحقق من النسخ الاحتياطي والتعافي للأنظمة الحكومية الحرجة مع اختبارات استرجاع دورية في موقع التعافي."),
    (26, "PARTIALLY_COMPLIANT", 3, "HIGH", "COMPLETED",
     "Vulnerability management operating to the NCA-ECC SLA policy; some critical patches exceeded the 7-day window.",
     "تُدار الثغرات وفق سياسة مدد الالتزام للهيئة الوطنية؛ وتجاوزت بعض التحديثات الحرجة نافذة الأيام السبعة."),
    (29, "COMPLIANT", 4, "LOW", "COMPLETED",
     "Incident and threat management runbooks include NCA HSOC notification; exercised in recent government incidents.",
     "تتضمّن أدلة إدارة الحوادث والتهديدات إبلاغ مركز العمليات الأمنية الوطني وجُرّبت في حوادث حكومية حديثة."),
    (32, "PARTIALLY_COMPLIANT", 3, "MEDIUM", "IN_REVIEW",
     "Cyber-resilience aspects integrated with BCM; failover testing of national portals to the DR site is being formalized.",
     "دُمجت جوانب الصمود السيبراني مع استمرارية الأعمال؛ ويجري إضفاء الطابع الرسمي على اختبار تحويل البوابات الوطنية لموقع التعافي."),
    (33, "PARTIALLY_COMPLIANT", 3, "HIGH", "COMPLETED",
     "Third-party cybersecurity clauses included in government contracts; vendor assessments being completed under ECC 4-1.",
     "أُدرجت بنود الأمن السيبراني للأطراف الخارجية في العقود الحكومية؛ ويجري استكمال تقييمات المورّدين وفق الضابط 4-1."),
    (34, "PARTIALLY_COMPLIANT", 3, "MEDIUM", "IN_REVIEW",
     "Cloud workloads onboarded to the National Government Cloud; NCA CSCC tiering and residency evidence being finalized.",
     "نُقلت الأحمال السحابية إلى السحابة الحكومية الوطنية؛ ويجري استكمال تصنيف ضوابط الأمن السيبراني للحوسبة السحابية وأدلة الإقامة داخل المملكة."),
]


def seed_control_assessments(cur):
    print("\n[cyber.control_assessments] NCA ECC control assessments (government)")
    for (cid, result, maturity, risk, status, gap_en, gap_ar) in CTRL_ASSESS:
        # only assess controls that actually exist in the catalogue
        cur.execute("SELECT 1 FROM cyber.cyber_controls WHERE id=? AND organisation_id=?", cid, ORG)
        if not cur.fetchone():
            bump("cyber.control_assessments", False)
            continue
        # natural key: one gov assessment per control_id (ids 1-4 pre-exist with our gap text? no).
        # Guard by control_id + our distinctive assessor_name to stay idempotent without
        # clobbering the 4 pre-existing assessments (control_id 1-4, different assessor).
        cur.execute(
            "SELECT id FROM cyber.control_assessments "
            "WHERE organisation_id=? AND control_id=? AND assessor_name=?",
            ORG, cid, "Cyber Risk & Compliance",
        )
        if cur.fetchone():
            bump("cyber.control_assessments", False)
            continue
        gap_flag = 0 if result == "COMPLIANT" else 1
        cur.execute(
            """INSERT INTO cyber.control_assessments
                   (external_id, organisation_id, control_id, assessment_date,
                    assessor_name, reviewer_name, result, maturity_score, gap_identified,
                    gap_description, risk_level, status, is_deleted, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""",
            str(uuid.uuid4()), ORG, cid, dt(days_ago=20).date(),
            "Cyber Risk & Compliance", "Head of Internal Audit",
            result, maturity, gap_flag, gap_en + " | " + gap_ar, risk, status,
            dt(days_ago=20),
        )
        bump("cyber.control_assessments", True)
        print(f"  + control_id={cid}  {result}  ({status})")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.cyber_assets  (DEEPEN) — parallel GOVERNMENT register.
#  natural key = (organisation_id, asset_name).
# ─────────────────────────────────────────────────────────────────────────────
GOV_ASSETS = [
    # name_en | name_ar, type, criticality, classification, biz_owner, tech_owner
    ("[GOV] ERP/GRP Financial & Resource System | نظام تخطيط موارد الجهة",
     "APPLICATION", "TIER_1", "سري", "Finance", "Information Technology"),
    ("[GOV] Etimad Procurement Integration | تكامل اعتماد للمشتريات",
     "APPLICATION", "TIER_2", "مقيّد", "Procurement", "Information Technology"),
    ("[GOV] National Beneficiary e-Service Portal | بوابة الخدمات الإلكترونية للمستفيدين",
     "APPLICATION", "TIER_1", "مقيّد", "Operations & Services", "Digital Transformation"),
    ("[GOV] Nafath / Absher SSO Integration | تكامل الدخول الموحّد (نفاذ/أبشر)",
     "APPLICATION", "TIER_1", "سري", "Digital Transformation", "Information Technology"),
    ("[GOV] Tawakkalna Services Gateway | بوابة خدمات توكلنا",
     "APPLICATION", "TIER_2", "مقيّد", "Operations & Services", "Digital Transformation"),
    ("[GOV] Active Directory Domain Services | خدمات الدليل النشط",
     "INFRASTRUCTURE", "TIER_1", "سري", "Information Technology", "Information Technology"),
    ("[GOV] Government Service Bus (GSB) | ناقل الخدمات الحكومي",
     "INFRASTRUCTURE", "TIER_1", "سري", "Digital Transformation", "Information Technology"),
    ("[GOV] Licensing & Inspection System | نظام التراخيص والتفتيش",
     "APPLICATION", "TIER_2", "مقيّد", "Operations & Services", "Information Technology"),
    ("[GOV] Field Operations / Inspection Mobile System | نظام العمليات الميدانية والتفتيش",
     "APPLICATION", "TIER_3", "مقيّد", "Operations & Services", "Information Technology"),
    ("[GOV] Ma'an Correspondence System | نظام (معًا) للمراسلات",
     "APPLICATION", "TIER_3", "مقيّد", "General Services", "Information Technology"),
    ("[GOV] DGA Najiz / Tarasul Gateway | بوابة ناجز/تراسل (الحكومة الرقمية)",
     "APPLICATION", "TIER_2", "مقيّد", "Digital Transformation", "Information Technology"),
    ("[GOV] HR & Payroll System (Masar) | نظام الموارد البشرية والرواتب (مسار)",
     "APPLICATION", "TIER_2", "سري", "Human Resources", "Information Technology"),
    ("[GOV] BI & Data Warehouse Platform | منصة ذكاء الأعمال ومستودع البيانات",
     "DATABASE", "TIER_2", "سري", "Data Management Office", "Data & AI"),
    ("[GOV] Public Authority Website | الموقع الإلكتروني الرسمي للجهة",
     "APPLICATION", "TIER_3", "عام", "Corporate Communication", "Digital Transformation"),
    ("[GOV] VMware Private-Cloud Platform | منصة الحوسبة السحابية الخاصة (VMware)",
     "INFRASTRUCTURE", "TIER_1", "سري", "Information Technology", "Information Technology"),
    ("[GOV] Disaster Recovery & Backup Site | موقع التعافي من الكوارث والنسخ الاحتياطي",
     "INFRASTRUCTURE", "TIER_1", "سري", "Information Technology", "Information Technology"),
]


def seed_cyber_assets(cur):
    print("\n[cyber.cyber_assets] government information-asset register")
    for (name, atype, crit, classif, bowner, towner) in GOV_ASSETS:
        cur.execute(
            "SELECT id FROM cyber.cyber_assets WHERE organisation_id=? AND asset_name=?",
            ORG, name,
        )
        if cur.fetchone():
            bump("cyber.cyber_assets", False)
            continue
        cur.execute(
            """INSERT INTO cyber.cyber_assets
                   (external_id, organisation_id, asset_name, asset_type, business_owner,
                    technical_owner, classification, criticality, status, is_deleted, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, ?)""",
            str(uuid.uuid4()), ORG, name, atype, bowner, towner, classif, crit,
            dt(days_ago=30),
        )
        bump("cyber.cyber_assets", True)
        print(f"  + {name[:60]}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.cyber_incidents  (DEEPEN) — rich 9-step workflow register, gov incidents.
#  natural key = (organisation_id, incident_number).
# ─────────────────────────────────────────────────────────────────────────────
GOV_CYBER_INCIDENTS = [
    # incident_number, title_en, title_ar, type, severity, workflow_status, lead, notif, bcm, lessons_en, lessons_ar
    ("GOV-CYI-2026-001", "DDoS on the national e-service portal",
     "هجوم حجب خدمة على بوابة الخدمات الإلكترونية الوطنية",
     "DDoS", "HIGH", "LESSONS_LEARNED", "Security Operations Center", 1, 1,
     "Deploy upstream DDoS scrubbing and edge rate-limiting; NCA HSOC notified; reviewed by COM-CYBER per ECC 4 incident response.",
     "نشر تنقية هجمات حجب الخدمة وتحديد المعدّل عند الحافة؛ أُبلغ مركز العمليات الأمنية الوطني واستعرضت اللجنة الإشرافية للأمن السيبراني الحالة وفق إدارة الحوادث."),
    ("GOV-CYI-2026-002", "Phishing for Nafath / employee credentials",
     "تصيّد لبيانات دخول نفاذ والموظفين",
     "PHISHING", "HIGH", "LESSONS_LEARNED", "Security Operations Center", 0, 0,
     "Adopt phishing-resistant MFA; recurring awareness; reviewed by COM-CYBER.",
     "اعتماد التحقق متعدد العوامل المقاوم للتصيّد والتوعية المستمرة؛ واستعراض اللجنة الإشرافية للأمن السيبراني."),
    ("GOV-CYI-2026-003", "Unauthorized access to the ERP/GRP admin console",
     "وصول غير مصرّح به إلى وحدة إدارة نظام الموارد",
     "INSIDER_THREAT", "HIGH", "CLOSED", "Chief Information Security Officer", 1, 0,
     "Enforce MFA and just-in-time privileged access on ERP/GRP; ECC 2-2.",
     "فرض التحقق متعدد العوامل والوصول المتميّز عند الحاجة على نظام الموارد وفق الضابط 2-2."),
    ("GOV-CYI-2026-004", "Ransomware indicator on an internal file server",
     "مؤشر برمجية فدية على خادم ملفات داخلي",
     "RANSOMWARE", "CRITICAL", "RECOVERY", "Security Operations Center", 1, 1,
     "Decommission SMBv1; validate backups; NCA HSOC notified per ECC 2-9 and incident response.",
     "إيقاف بروتوكول SMBv1 والتحقق من النسخ الاحتياطية؛ وأُبلغ مركز العمليات الأمنية الوطني وفق الضابطين."),
    ("GOV-CYI-2026-005", "PII exfiltration attempt from the beneficiary database",
     "محاولة تسريب بيانات شخصية من قاعدة بيانات المستفيدين",
     "DATA_BREACH", "CRITICAL", "ERADICATION", "Chief Information Security Officer", 1, 0,
     "Enable TDE and DLP query monitoring; SDAIA notified under PDPL; reviewed by COM-CYBER and the Privacy Committee.",
     "تفعيل التشفير الشفاف ومراقبة الاستعلامات؛ وأُبلغت الهيئة السعودية للبيانات والذكاء الاصطناعي وفق نظام حماية البيانات الشخصية، واستُعرضت من لجنة الأمن السيبراني ولجنة الخصوصية."),
    ("GOV-CYI-2026-006", "Exposed Etimad integration secret",
     "كشف مفتاح تكامل اعتماد",
     "DATA_BREACH", "MEDIUM", "CONTAINMENT", "Security Operations Center", 0, 0,
     "Rotate secrets and enable pipeline secret-scanning; ECC 2-3.",
     "تدوير الأسرار وتفعيل فحص الأسرار في خط الإنتاج وفق الضابط 2-3."),
    ("GOV-CYI-2026-007", "Web-shell on the licensing portal",
     "غلاف ويب خبيث على بوابة التراخيص",
     "MALWARE", "HIGH", "ERADICATION", "Security Operations Center", 1, 0,
     "Patch web framework and add WAF rules; ECC 2-15 web application security; NCA HSOC notified.",
     "تحديث إطار الويب وإضافة قواعد جدار حماية التطبيقات وفق الضابط 2-15؛ وأُبلغ مركز العمليات الأمنية الوطني."),
    ("GOV-CYI-2026-008", "Supply-chain alert on a government SaaS vendor",
     "تنبيه سلسلة إمداد على مورّد خدمة حكومية سحابية",
     "MALWARE", "MEDIUM", "ANALYSIS", "Security Operations Center", 1, 0,
     "Rotate vendor tokens and run a third-party assessment; ECC 4-1; reviewed by COM-CYBER.",
     "تدوير رموز المورّد وإجراء تقييم طرف خارجي وفق الضابط 4-1؛ واستعراض اللجنة الإشرافية للأمن السيبراني."),
]


def seed_cyber_incidents(cur):
    print("\n[cyber.cyber_incidents] government incidents (9-step workflow)")
    for (num, ten, tar, itype, sev, wf, lead, notif, bcm, les_en, les_ar) in GOV_CYBER_INCIDENTS:
        cur.execute(
            "SELECT id FROM cyber.cyber_incidents WHERE organisation_id=? AND incident_number=?",
            ORG, num,
        )
        if cur.fetchone():
            bump("cyber.cyber_incidents", False)
            continue
        cur.execute(
            """INSERT INTO cyber.cyber_incidents
                   (external_id, organisation_id, incident_number, title_en, title_ar,
                    incident_type, severity, workflow_status, detected_at, reported_at,
                    incident_lead, notification_sent, bcm_handoff, lessons_learned,
                    is_deleted, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""",
            str(uuid.uuid4()), ORG, num, ten, tar, itype, sev, wf,
            dt(days_ago=20), dt(days_ago=20),
            lead, notif, bcm, les_en + " | " + les_ar, dt(days_ago=20),
        )
        bump("cyber.cyber_incidents", True)
        print(f"  + {num}  {ten}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.incidents  (DEEPEN) — lighter register with CHECK enums.
#  category IN (CYBER, ACCESS_CONTROL, INFRASTRUCTURE, DATA_BREACH, OTHER)
#  severity IN (LOW, MEDIUM, HIGH, CRITICAL); status IN (OPEN, INVESTIGATING, RESOLVED, CLOSED)
#  natural key = (organisation_id, title).  row_version is ROWVERSION -> never inserted.
# ─────────────────────────────────────────────────────────────────────────────
SIMPLE_INCIDENTS = [
    # title, title_ar, severity, category, status, assignee, desc, nist_phase
    ("After-hours access to the GRP from an unrecognized host",
     "وصول إلى نظام الموارد خارج أوقات الدوام من جهاز غير معروف",
     "MEDIUM", "ACCESS_CONTROL", "RESOLVED", "Security Operations Center",
     "Unusual after-hours session to the GRP traced to a mis-configured service account; access revoked and monitored.",
     "RECOVERY"),
    ("Malware detected on an inspector laptop",
     "اكتشاف برمجية خبيثة على حاسب محمول لأحد المفتشين",
     "MEDIUM", "CYBER", "RESOLVED", "Security Operations Center",
     "EDR quarantined malware on a field-inspector laptop; device re-imaged and EOL OS upgrade scheduled.",
     "ERADICATION"),
    ("Unencrypted beneficiary data found on a shared drive",
     "اكتشاف بيانات مستفيدين غير مشفّرة على قرص مشترك",
     "HIGH", "DATA_BREACH", "INVESTIGATING", "Cyber Risk & Compliance",
     "Beneficiary records stored unencrypted on a department share; access restricted, NDMO classification and encryption applied.",
     "CONTAINMENT"),
    ("SQL-injection attempt against the public portal",
     "محاولة حقن SQL على البوابة العامة",
     "HIGH", "CYBER", "RESOLVED", "Security Operations Center",
     "WAF blocked SQL-injection probes against the public portal; input validation hardened per ECC 2-15.",
     "ERADICATION"),
    ("Government Service Bus node outage",
     "انقطاع عقدة في ناقل الخدمات الحكومي",
     "MEDIUM", "INFRASTRUCTURE", "CLOSED", "Information Technology",
     "A GSB node failed causing integration delays; failover restored service and capacity was added.",
     "RECOVERY"),
    ("Credential leak from a government code repository",
     "تسرّب بيانات اعتماد من مستودع شيفرة حكومي",
     "HIGH", "CYBER", "RESOLVED", "Security Operations Center",
     "Hard-coded credentials found in an internal repo; rotated and secret-scanning enabled.",
     "ERADICATION"),
    ("Vendor breach notification received",
     "استلام إشعار اختراق من مورّد",
     "MEDIUM", "OTHER", "INVESTIGATING", "Cyber Risk & Compliance",
     "A SaaS vendor reported a breach; impact assessment underway and tokens rotated per ECC 4-1.",
     "ANALYSIS"),
]


def seed_simple_incidents(cur):
    print("\n[cyber.incidents] lightweight government incidents (CHECK-enum)")
    for (title, title_ar, sev, cat, status, assignee, desc, nist) in SIMPLE_INCIDENTS:
        cur.execute(
            "SELECT id FROM cyber.incidents WHERE organisation_id=? AND title=?",
            ORG, title,
        )
        if cur.fetchone():
            bump("cyber.incidents", False)
            continue
        cur.execute(
            """INSERT INTO cyber.incidents
                   (organisation_id, title, title_ar, severity, category, status, assignee,
                    discovery_date, description, nist_phase, created_by, created_at, is_deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""",
            ORG, title, title_ar, sev, cat, status, assignee,
            dt(days_ago=15).date(), desc, nist, ADMIN, dt(days_ago=15),
        )
        bump("cyber.incidents", True)
        print(f"  + {title[:55]}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.vulnerabilities  (DEEPEN) — government-stack vulns, NCA-ECC SLA framed.
#  natural key = (organisation_id, title_en).
# ─────────────────────────────────────────────────────────────────────────────
GOV_VULNS = [
    # title_en, title_ar, sev, cvss, vtype, cve, sla_days, asset_name(for asset_name col), owner
    ("Unpatched on-prem Exchange RCE on the government mail server",
     "ثغرة تنفيذ عن بُعد غير محدّثة في خادم البريد الحكومي المحلي",
     "CRITICAL", 9.8, "Remote Code Execution", "CVE-2026-0011", 7,
     "[GOV] Active Directory Domain Services"),
    ("Exposed Etimad integration endpoint",
     "نقطة تكامل اعتماد مكشوفة",
     "HIGH", 7.5, "Exposed Service", None, 30,
     "[GOV] Etimad Procurement Integration"),
    ("Active Directory privilege escalation (Zerologon-class)",
     "تصعيد صلاحيات في الدليل النشط من فئة Zerologon",
     "CRITICAL", 10.0, "Privilege Escalation", "CVE-2020-1472", 7,
     "[GOV] Active Directory Domain Services"),
    ("Missing Transparent Data Encryption on the beneficiary database",
     "غياب التشفير الشفاف للبيانات في قاعدة بيانات المستفيدين",
     "HIGH", 7.4, "Cryptographic Weakness", None, 30,
     "[GOV] BI & Data Warehouse Platform"),
    ("Weak TLS configuration on the Government Service Bus",
     "إعداد TLS ضعيف على ناقل الخدمات الحكومي",
     "MEDIUM", 5.3, "Weak Cryptography", None, 60,
     "[GOV] Government Service Bus (GSB)"),
    ("Default credentials on a core network switch",
     "بيانات اعتماد افتراضية على محوّل شبكة أساسي",
     "HIGH", 8.1, "Default Credentials", None, 30,
     "[GOV] VMware Private-Cloud Platform"),
    ("Outdated Spring framework on the e-service portal",
     "إطار Spring قديم على بوابة الخدمات الإلكترونية",
     "HIGH", 8.8, "Vulnerable Component", "CVE-2026-0067", 30,
     "[GOV] National Beneficiary e-Service Portal"),
    ("SMBv1 enabled on an internal file server",
     "تفعيل بروتوكول SMBv1 على خادم ملفات داخلي",
     "HIGH", 8.1, "Insecure Protocol", "CVE-2017-0144", 30,
     "[GOV] VMware Private-Cloud Platform"),
    ("Publicly accessible object-storage bucket at the DR site",
     "حاوية تخزين كائنات قابلة للوصول العام في موقع التعافي",
     "CRITICAL", 9.1, "Misconfiguration", None, 7,
     "[GOV] Disaster Recovery & Backup Site"),
    ("End-of-life operating system on inspection laptops",
     "نظام تشغيل منتهي الدعم على حواسب التفتيش المحمولة",
     "MEDIUM", 6.5, "Unsupported Software", None, 60,
     "[GOV] Field Operations / Inspection Mobile System"),
]


def seed_gov_vulns(cur):
    print("\n[cyber.vulnerabilities] government-stack vulnerabilities (ECC SLA)")
    for (ten, tar, sev, cvss, vtype, cve, sla_days, asset_name) in GOV_VULNS:
        cur.execute(
            "SELECT id FROM cyber.vulnerabilities WHERE organisation_id=? AND title_en=?",
            ORG, ten,
        )
        if cur.fetchone():
            bump("cyber.vulnerabilities", False)
            continue
        # resolve the gov asset id (may exist after seed_cyber_assets)
        cur.execute(
            "SELECT id FROM cyber.cyber_assets WHERE organisation_id=? AND asset_name=?",
            ORG, asset_name,
        )
        row = cur.fetchone()
        asset_id = row[0] if row else None
        due = (dt(days_ago=3) + timedelta(days=sla_days)).date()
        cur.execute(
            """INSERT INTO cyber.vulnerabilities
                   (external_id, organisation_id, asset_id, asset_name, title_en, title_ar,
                    severity, cvss_score, vulnerability_type, cve_id, owner_name, sla_days,
                    due_date, workflow_status, overdue, is_deleted, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REGISTERED', 0, 0, ?)""",
            str(uuid.uuid4()), ORG, asset_id, asset_name, ten, tar, sev, cvss, vtype, cve,
            "Security Operations Center", sla_days, due, dt(days_ago=3),
        )
        bump("cyber.vulnerabilities", True)
        print(f"  + {ten[:55]}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.cyber_action_plans  (DEEPEN) — CAPA remediating gov gaps, linked to ECC.
#  natural key = (organisation_id, title_en).
# ─────────────────────────────────────────────────────────────────────────────
GOV_ACTION_PLANS = [
    # title_en, title_ar, source_type, priority, owner, progress, linked_control_id, source_ref
    ("Segment the Government Service Bus per NCA ECC 2-5",
     "تجزئة ناقل الخدمات الحكومي وفق الضابط 2-5",
     "ASSESSMENT", "HIGH", "Information Technology", 40, 21, "ECC 2-5 network segmentation"),
    ("Enable TDE on the beneficiary data warehouse (ECC 2-7 / NDMO)",
     "تفعيل التشفير الشفاف لمستودع بيانات المستفيدين وفق الضابط 2-7",
     "VULNERABILITY", "HIGH", "Data Management Office", 25, 23, "ECC 2-7 data protection"),
    ("Migrate off SMBv1 and end-of-life operating systems",
     "إيقاف بروتوكول SMBv1 والأنظمة منتهية الدعم",
     "VULNERABILITY", "MEDIUM", "Information Technology", 50, 25, "ECC 2-9 backup & recovery"),
    ("Deploy MFA / Nafath on privileged admin consoles",
     "نشر التحقق متعدد العوامل / نفاذ على وحدات الإدارة المتميّزة",
     "ASSESSMENT", "HIGH", "Cybersecurity", 60, 18, "ECC 2-2 identity & access"),
    ("Formalize the NCA HSOC incident-notification runbook",
     "إضفاء الطابع الرسمي على دليل إبلاغ مركز العمليات الأمنية الوطني",
     "INCIDENT", "HIGH", "Security Operations Center", 70, 29, "ECC 2-13 incident management"),
    ("Patch on-prem Exchange to the remediated build",
     "تحديث خوادم Exchange المحلية إلى الإصدار المعالَج",
     "VULNERABILITY", "HIGH", "Information Technology", 30, 26, "ECC 2-10 vulnerability mgmt"),
    ("Harden Etimad integration secrets management",
     "تعزيز إدارة أسرار تكامل اعتماد",
     "INCIDENT", "MEDIUM", "Cybersecurity", 45, 19, "ECC 2-3 system protection"),
    ("Complete cloud onboarding to NCA CSCC for the Government Cloud",
     "استكمال الانتقال السحابي وفق ضوابط الأمن السيبراني للحوسبة السحابية",
     "ASSESSMENT", "MEDIUM", "Digital Transformation", 35, 34, "ECC 4-2 cloud cybersecurity"),
]


def seed_action_plans(cur):
    print("\n[cyber.cyber_action_plans] government CAPA / remediation plans")
    for (ten, tar, stype, prio, owner, prog, ctrl, sref) in GOV_ACTION_PLANS:
        cur.execute(
            "SELECT id FROM cyber.cyber_action_plans WHERE organisation_id=? AND title_en=?",
            ORG, ten,
        )
        if cur.fetchone():
            bump("cyber.cyber_action_plans", False)
            continue
        # only link a control that exists
        cur.execute("SELECT 1 FROM cyber.cyber_controls WHERE id=? AND organisation_id=?", ctrl, ORG)
        linked = ctrl if cur.fetchone() else None
        wf = "COMPLETED" if prog >= 100 else ("IN_PROGRESS" if prog > 0 else "OPEN")
        cur.execute(
            """INSERT INTO cyber.cyber_action_plans
                   (external_id, organisation_id, title_en, title_ar, source_type, priority,
                    owner_name, due_date, workflow_status, progress_pct, linked_control_id,
                    source_ref, is_deleted, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""",
            str(uuid.uuid4()), ORG, ten, tar, stype, prio, owner,
            (dt(days_ago=10) + timedelta(days=60)).date(), wf, prog, linked, sref,
            dt(days_ago=10),
        )
        bump("cyber.cyber_action_plans", True)
        print(f"  + {ten[:55]}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.cyber_evidence  (DEEPEN) — evidence supporting the ECC assessments.
#  natural key = (organisation_id, title_en).
# ─────────────────────────────────────────────────────────────────────────────
GOV_EVIDENCE = [
    # title_en, etype, framework_req, owner, status, control_id
    ("Cybersecurity Policy approved by the Cybersecurity Supervisory Committee (COM-CYBER)",
     "DOCUMENT", "ECC 1-3", "Cyber Risk & Compliance", "CURRENT", 9),
    ("NCA ECC Self-Assessment Workbook 2026",
     "REPORT", "ECC 1-8", "Cyber Risk & Compliance", "CURRENT", 14),
    ("Government Service Bus Network-Segmentation Diagram",
     "DIAGRAM", "ECC 2-5", "Information Technology", "CURRENT", 21),
    ("Government Cloud (NCA CSCC) Onboarding Pack",
     "DOCUMENT", "ECC 4-2", "Digital Transformation", "CURRENT", 34),
    ("NCA HSOC Incident-Notification Runbook",
     "PROCEDURE", "ECC 2-13", "Security Operations Center", "CURRENT", 29),
    ("Active Directory Privileged-Access Review Report",
     "REPORT", "ECC 2-2", "Cybersecurity", "CURRENT", 18),
    ("NDMO Data-Classification Register Extract",
     "DOCUMENT", "ECC 2-7", "Data Management Office", "CURRENT", 23),
    ("Business-Continuity / DR Test Report for Government Systems",
     "REPORT", "ECC 3-1", "Business Continuity", "CURRENT", 32),
]


def seed_evidence(cur):
    print("\n[cyber.cyber_evidence] evidence artefacts for ECC assessments")
    for (ten, etype, fwreq, owner, status, ctrl) in GOV_EVIDENCE:
        cur.execute(
            "SELECT id FROM cyber.cyber_evidence WHERE organisation_id=? AND title_en=?",
            ORG, ten,
        )
        if cur.fetchone():
            bump("cyber.cyber_evidence", False)
            continue
        cur.execute("SELECT 1 FROM cyber.cyber_controls WHERE id=? AND organisation_id=?", ctrl, ORG)
        linked = ctrl if cur.fetchone() else None
        cur.execute(
            """INSERT INTO cyber.cyber_evidence
                   (external_id, organisation_id, title_en, evidence_type, control_id,
                    framework_requirement, owner_name, status, review_date, is_deleted, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""",
            str(uuid.uuid4()), ORG, ten, etype, linked, fwreq, owner, status,
            dt(days_ago=10).date(), dt(days_ago=10),
        )
        bump("cyber.cyber_evidence", True)
        print(f"  + {ten[:55]}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.attack_surface_asset  (DEEPEN) — internet-facing government ASM assets.
#  CHECK enums enforced. natural key = (organisation_id, asset_code).
#  row_version ROWVERSION -> never inserted.
# ─────────────────────────────────────────────────────────────────────────────
ASM_ASSETS = [
    # code, title_en, title_ar, asset_type, exposure, criticality, source, bu_en, bu_ar, protocol, port, hostname
    ("ASM-2026-101", "National Beneficiary e-Service Portal",
     "بوابة الخدمات الإلكترونية للمستفيدين",
     "WEB_APPLICATION", "PUBLIC", "CRITICAL", "DNS_SCAN",
     "Operations & Services", "العمليات والخدمات", "HTTPS", 443, "services.authority.gov.sa"),
    ("ASM-2026-102", "Nafath / Absher Integration Callback",
     "نقطة استدعاء تكامل نفاذ / أبشر",
     "API_ENDPOINT", "PARTNER", "CRITICAL", "MANUAL",
     "Digital Transformation", "التحول الرقمي", "HTTPS", 443, "sso.authority.gov.sa"),
    ("ASM-2026-103", "Etimad Procurement Integration Endpoint",
     "نقطة تكامل اعتماد للمشتريات",
     "API_ENDPOINT", "PARTNER", "HIGH", "MANUAL",
     "Procurement", "المشتريات", "HTTPS", 443, "etimad-int.authority.gov.sa"),
    ("ASM-2026-104", "DGA Najiz / Tarasul Gateway",
     "بوابة ناجز / تراسل (الحكومة الرقمية)",
     "API_ENDPOINT", "PARTNER", "HIGH", "MANUAL",
     "Digital Transformation", "التحول الرقمي", "HTTPS", 443, "dga-gw.authority.gov.sa"),
    ("ASM-2026-105", "Public Authority Website",
     "الموقع الإلكتروني الرسمي للجهة",
     "WEB_APPLICATION", "PUBLIC", "MEDIUM", "DNS_SCAN",
     "Corporate Communication", "الاتصال المؤسسي", "HTTPS", 443, "www.authority.gov.sa"),
    ("ASM-2026-106", "Wildcard TLS Certificate (*.authority.gov.sa)",
     "شهادة TLS العامة (*.authority.gov.sa)",
     "CERTIFICATE", "PUBLIC", "HIGH", "CERT_TRANSPARENCY",
     "Information Technology", "تقنية المعلومات", None, None, None),
    ("ASM-2026-107", "Remote-Access VPN Gateway",
     "بوابة الوصول عن بُعد (VPN)",
     "VPN_GATEWAY", "RESTRICTED", "HIGH", "MANUAL",
     "Information Technology", "تقنية المعلومات", "HTTPS", 443, "vpn.authority.gov.sa"),
    ("ASM-2026-108", "Licensing Admin Portal",
     "بوابة إدارة التراخيص",
     "ADMIN_PORTAL", "RESTRICTED", "HIGH", "MANUAL",
     "Operations & Services", "العمليات والخدمات", "HTTPS", 8443, "lic-admin.authority.gov.sa"),
]


def seed_asm_assets(cur):
    print("\n[cyber.attack_surface_asset] internet-facing government assets")
    for (code, ten, tar, atype, expo, crit, src, bu_en, bu_ar, proto, port, host) in ASM_ASSETS:
        cur.execute(
            "SELECT id FROM cyber.attack_surface_asset WHERE organisation_id=? AND asset_code=?",
            ORG, code,
        )
        if cur.fetchone():
            bump("cyber.attack_surface_asset", False)
            continue
        cur.execute(
            """INSERT INTO cyber.attack_surface_asset
                   (organisation_id, asset_code, title_en, title_ar, asset_type, hostname,
                    port, protocol, asset_owner_user_id, business_unit_en, business_unit_ar,
                    exposure, criticality, lifecycle_stage, discovery_source, discovered_at,
                    is_deleted, created_by, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?, 0, ?, ?)""",
            ORG, code, ten, tar, atype, host, port, proto, ADMIN, bu_en, bu_ar,
            expo, crit, src, dt(days_ago=25), ADMIN, dt(days_ago=25),
        )
        bump("cyber.attack_surface_asset", True)
        print(f"  + {code}  {ten}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.attack_surface_finding  (DEEPEN) — findings on the NEW gov ASM assets.
#  CHECK enums enforced. natural key = (organisation_id, asset_id, finding_type, description_en).
# ─────────────────────────────────────────────────────────────────────────────
ASM_FINDINGS = [
    # asset_code, finding_type, severity, source, status, desc_en, desc_ar, rec_en, rec_ar
    ("ASM-2026-101", "NO_WAF", "HIGH", "MANUAL", "OPEN",
     "The national e-service portal has no web-application firewall in front of it, contributing to the recent DDoS impact.",
     "لا يوجد جدار حماية لتطبيقات الويب أمام بوابة الخدمات الإلكترونية الوطنية، ما أسهم في أثر هجوم حجب الخدمة الأخير.",
     "Deploy a managed WAF with DDoS protection.",
     "نشر جدار حماية مُدار لتطبيقات الويب مع حماية من هجمات حجب الخدمة."),
    ("ASM-2026-101", "NO_RATE_LIMIT", "HIGH", "AUTOMATED_PROBE", "OPEN",
     "No request rate-limiting is enforced at the portal edge, allowing volumetric abuse.",
     "لا يُطبّق تحديد معدّل الطلبات عند حافة البوابة، ما يتيح الإساءة الحجمية.",
     "Enforce edge rate-limiting and upstream scrubbing.",
     "تطبيق تحديد المعدّل عند الحافة وتنقية الحركة من المنبع."),
    ("ASM-2026-105", "OUTDATED_FRAMEWORK", "MEDIUM", "AUTOMATED_PROBE", "INVESTIGATING",
     "The public website runs an outdated web framework with known vulnerabilities.",
     "يعمل الموقع الرسمي على إطار ويب قديم به ثغرات معروفة.",
     "Upgrade the framework to a supported release.",
     "ترقية الإطار إلى إصدار مدعوم."),
    ("ASM-2026-107", "WEAK_CIPHER", "MEDIUM", "TENABLE", "OPEN",
     "The VPN gateway negotiates weak TLS ciphers and deprecated protocols.",
     "تتفاوض بوابة الشبكة الافتراضية على خوارزميات TLS ضعيفة وبروتوكولات مهجورة.",
     "Disable weak ciphers and enforce TLS 1.2+.",
     "تعطيل الخوارزميات الضعيفة وفرض TLS 1.2 فأعلى."),
    ("ASM-2026-106", "EXPIRED_CERT", "MEDIUM", "MANUAL", "OPEN",
     "The wildcard TLS certificate is approaching expiry with no automated renewal.",
     "شهادة TLS العامة تقترب من انتهاء الصلاحية دون تجديد آلي.",
     "Automate certificate renewal and monitoring.",
     "أتمتة تجديد الشهادات ومراقبتها."),
    ("ASM-2026-108", "EXPOSED_PORT", "HIGH", "AUTOMATED_PROBE", "OPEN",
     "The licensing admin portal exposes an administrative port to a broader network than required.",
     "تكشف بوابة إدارة التراخيص منفذاً إدارياً لشبكة أوسع من اللازم.",
     "Restrict the admin port to a management VLAN behind VPN.",
     "تقييد المنفذ الإداري على شبكة إدارة خلف الشبكة الافتراضية."),
    ("ASM-2026-101", "MISSING_HEADER", "LOW", "AUTOMATED_PROBE", "OPEN",
     "The portal is missing key security response headers (HSTS, CSP).",
     "تفتقر البوابة إلى ترويسات الاستجابة الأمنية الأساسية (HSTS وCSP).",
     "Add HSTS, CSP and related security headers.",
     "إضافة ترويسات HSTS وCSP والترويسات الأمنية ذات الصلة."),
    ("ASM-2026-104", "UNPATCHED", "HIGH", "TENABLE", "INVESTIGATING",
     "The DGA gateway shows an unpatched component flagged by the scanner.",
     "تُظهر بوابة الحكومة الرقمية مكوّناً غير محدّث رصده الماسح.",
     "Apply the vendor patch within the ECC SLA window.",
     "تطبيق تحديث المورّد ضمن نافذة الالتزام للضوابط الأساسية."),
]


def seed_asm_findings(cur):
    print("\n[cyber.attack_surface_finding] findings on government ASM assets")
    for (code, ftype, sev, src, status, d_en, d_ar, r_en, r_ar) in ASM_FINDINGS:
        cur.execute(
            "SELECT id FROM cyber.attack_surface_asset WHERE organisation_id=? AND asset_code=?",
            ORG, code,
        )
        row = cur.fetchone()
        if not row:
            bump("cyber.attack_surface_finding", False)
            continue
        asset_id = row[0]
        cur.execute(
            "SELECT id FROM cyber.attack_surface_finding "
            "WHERE organisation_id=? AND asset_id=? AND finding_type=? AND description_en=?",
            ORG, asset_id, ftype, d_en,
        )
        if cur.fetchone():
            bump("cyber.attack_surface_finding", False)
            continue
        cur.execute(
            """INSERT INTO cyber.attack_surface_finding
                   (organisation_id, asset_id, finding_type, severity, source, description_en,
                    description_ar, recommendation_en, recommendation_ar, opened_at, status,
                    is_deleted, created_by, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)""",
            ORG, asset_id, ftype, sev, src, d_en, d_ar, r_en, r_ar,
            dt(days_ago=20), status, ADMIN, dt(days_ago=20),
        )
        bump("cyber.attack_surface_finding", True)
        print(f"  + {code}  {ftype} ({sev})")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.cloud_services  (DEEPEN) — government cloud, NCA CSCC + in-Kingdom residency.
#  natural key = (organisation_id, service_name).  row_version ROWVERSION.
# ─────────────────────────────────────────────────────────────────────────────
GOV_CLOUD = [
    # service_name, provider, model, deployment, classification, residency, kingdom, cscc, crit, notes
    ("National Government Cloud (Deem) — e-Service Hosting", "National Government Cloud (Deem)",
     "IAAS", "GOV_PRIVATE", "مقيّد", "Riyadh, Saudi Arabia", 1, "Tier-3", "CRITICAL",
     "Hosts the national beneficiary e-service portal; in-Kingdom residency per NCA CSCC."),
    ("SDAIA / NDMO National Data Platform Integration", "SDAIA National Data Bank",
     "SAAS", "GOV_PRIVATE", "سري", "Riyadh, Saudi Arabia", 1, "Tier-3", "HIGH",
     "Integration with the national data platform for data sharing under NDMO governance."),
    ("Government Microsoft 365 (GCC) Tenant", "Microsoft (Government Cloud)",
     "SAAS", "PUBLIC", "مقيّد", "Saudi Arabia Region", 1, "Tier-2", "HIGH",
     "Productivity and email for the Authority in a government-tenant configuration."),
    ("Oracle Fusion Government ERP (SaaS)", "Oracle (Saudi Region)",
     "SAAS", "PUBLIC", "سري", "Riyadh, Saudi Arabia", 1, "Tier-3", "CRITICAL",
     "Government ERP SaaS in the in-Kingdom region; backs the ERP/GRP financial system."),
    ("Government DNS & CDN Service", "National CDN / DNS Provider",
     "PAAS", "PUBLIC", "عام", "Saudi Arabia Region", 1, "Tier-2", "MEDIUM",
     "DNS and content delivery for public government websites."),
    ("Sanctioned Analytics & BI Service", "Government Analytics Platform",
     "SAAS", "GOV_PRIVATE", "سري", "Riyadh, Saudi Arabia", 1, "Tier-2", "MEDIUM",
     "Approved analytics service feeding the BI / data-warehouse platform."),
]


def seed_cloud_services(cur):
    print("\n[cyber.cloud_services] government cloud (NCA CSCC / in-Kingdom)")
    for (svc, prov, model, deploy, classif, region, kingdom, cscc, crit, notes) in GOV_CLOUD:
        cur.execute(
            "SELECT id FROM cyber.cloud_services WHERE organisation_id=? AND service_name=?",
            ORG, svc,
        )
        if cur.fetchone():
            bump("cyber.cloud_services", False)
            continue
        cur.execute(
            """INSERT INTO cyber.cloud_services
                   (organisation_id, service_name, provider, service_model, deployment_model,
                    data_classification, residency_region, kingdom_data_resident, cscc_tier,
                    owner_user_id, criticality, sso_enabled, mfa_enforced, encryption_at_rest,
                    encryption_in_transit, status, next_review_date, notes, created_by, created_at,
                    is_deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, 1, 'ACTIVE', ?, ?, ?, ?, 0)""",
            ORG, svc, prov, model, deploy, classif, region, kingdom, cscc, ADMIN, crit,
            (dt(days_ago=10) + timedelta(days=180)).date(), notes, ADMIN, dt(days_ago=30),
        )
        bump("cyber.cloud_services", True)
        print(f"  + {svc[:55]}")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.root_cause_analyses  (DEEPEN) — RCAs for the new gov cyber_incidents.
#  CHECK: methodology, status; REJECTED needs reason (we avoid REJECTED).
#  natural key = (organisation_id, related_entity_type, related_entity_id).
#  Links to cyber.cyber_incidents ids resolved in-script. row_version is bigint=0.
# ─────────────────────────────────────────────────────────────────────────────
GOV_RCA = [
    # incident_number, methodology, status, problem_en, problem_ar, root_en, root_ar, rec_en, rec_ar, five_whys
    ("GOV-CYI-2026-001", "FIVE_WHYS", "APPROVED",
     "The national e-service portal was degraded by a volumetric DDoS attack.",
     "تدهورت بوابة الخدمات الإلكترونية الوطنية بسبب هجوم حجب خدمة حجمي.",
     "No upstream DDoS scrubbing or edge rate-limiting was in place at the portal perimeter.",
     "لا توجد تنقية لهجمات حجب الخدمة من المنبع ولا تحديد للمعدّل عند محيط البوابة.",
     "Deploy managed DDoS scrubbing, edge rate-limiting and a WAF; reviewed by COM-CYBER.",
     "نشر تنقية مُدارة لهجمات حجب الخدمة وتحديد المعدّل وجدار حماية التطبيقات؛ واستعراض اللجنة الإشرافية للأمن السيبراني.",
     '[{"why":"Portal became unreachable","because":"Edge was saturated by traffic"},{"why":"Edge saturated","because":"No rate-limiting"},{"why":"No rate-limiting","because":"WAF/scrubbing not provisioned"},{"why":"Not provisioned","because":"DDoS protection not in the portal design baseline"},{"why":"Not in baseline","because":"Resilience requirement not mapped to ECC for public services"}]'),
    ("GOV-CYI-2026-002", "FISHBONE", "APPROVED",
     "Employees disclosed Nafath credentials to a phishing site.",
     "أفصح موظفون عن بيانات دخول نفاذ لموقع تصيّد.",
     "Lack of phishing-resistant MFA combined with insufficient awareness allowed credential capture.",
     "أتاح غياب التحقق متعدد العوامل المقاوم للتصيّد مع ضعف التوعية التقاط بيانات الدخول.",
     "Roll out phishing-resistant MFA and recurring awareness; reviewed by COM-CYBER.",
     "نشر التحقق متعدد العوامل المقاوم للتصيّد والتوعية المستمرة؛ واستعراض اللجنة الإشرافية للأمن السيبراني.",
     None),
    ("GOV-CYI-2026-003", "FIVE_WHYS", "APPROVED",
     "A ransomware payload reached an internal file server.",
     "وصلت حمولة برمجية فدية إلى خادم ملفات داخلي.",
     "An end-of-life SMBv1 service remained enabled on the file server.",
     "بقيت خدمة SMBv1 منتهية الدعم مفعّلة على خادم الملفات.",
     "Decommission SMBv1, segment file servers and verify backups per ECC 2-9.",
     "إيقاف SMBv1 وتجزئة خوادم الملفات والتحقق من النسخ الاحتياطية وفق الضابط 2-9.",
     '[{"why":"Server infected","because":"Payload executed via SMBv1"},{"why":"SMBv1 reachable","because":"Legacy protocol still enabled"},{"why":"Still enabled","because":"No hardening baseline applied"},{"why":"No baseline","because":"Asset not in hardening scope"},{"why":"Out of scope","because":"Inventory gap in the asset register"}]'),
    ("GOV-CYI-2026-005", "PARETO", "IN_REVIEW",
     "An anomalous bulk query suggested possible PII exfiltration from the beneficiary database.",
     "أشار استعلام مجمّع غير اعتيادي إلى احتمال تسريب بيانات شخصية من قاعدة بيانات المستفيدين.",
     "Absence of TDE and limited query monitoring increased exposure of sensitive records.",
     "زاد غياب التشفير الشفاف ومحدودية مراقبة الاستعلامات من تعرّض السجلات الحساسة.",
     "Enable TDE and DLP query monitoring; notify SDAIA under PDPL; reviewed by COM-CYBER and the Privacy Committee.",
     "تفعيل التشفير الشفاف ومراقبة الاستعلامات؛ وإبلاغ الهيئة السعودية للبيانات والذكاء الاصطناعي وفق النظام؛ واستعراض لجنتي الأمن السيبراني والخصوصية.",
     None),
    ("GOV-CYI-2026-007", "FIVE_WHYS", "IN_REVIEW",
     "A web-shell was placed on the licensing portal via an unpatched framework.",
     "زُرع غلاف ويب خبيث على بوابة التراخيص عبر إطار غير محدّث.",
     "A critical web-framework patch exceeded the ECC vulnerability-management SLA.",
     "تجاوز تحديث حرج لإطار الويب مدة الالتزام لإدارة الثغرات في الضوابط الأساسية.",
     "Patch within SLA, add WAF rules and continuous web-app scanning per ECC 2-15.",
     "التحديث ضمن مدة الالتزام وإضافة قواعد جدار حماية التطبيقات والمسح المستمر وفق الضابط 2-15.",
     '[{"why":"Web-shell installed","because":"Framework had a known RCE"},{"why":"RCE present","because":"Patch not applied"},{"why":"Not applied","because":"SLA window exceeded"},{"why":"SLA exceeded","because":"Change-window constraints"},{"why":"Constraints","because":"No emergency-patch process for public portals"}]'),
]


def seed_rca(cur):
    print("\n[cyber.root_cause_analyses] RCAs for government incidents")
    for (num, method, status, p_en, p_ar, rc_en, rc_ar, rec_en, rec_ar, whys) in GOV_RCA:
        cur.execute(
            "SELECT id FROM cyber.cyber_incidents WHERE organisation_id=? AND incident_number=?",
            ORG, num,
        )
        row = cur.fetchone()
        if not row:
            bump("cyber.root_cause_analyses", False)
            continue
        inc_id = row[0]
        cur.execute(
            "SELECT id FROM cyber.root_cause_analyses "
            "WHERE organisation_id=? AND related_entity_type='incident' AND related_entity_id=?",
            ORG, inc_id,
        )
        if cur.fetchone():
            bump("cyber.root_cause_analyses", False)
            continue
        cur.execute(
            """INSERT INTO cyber.root_cause_analyses
                   (organisation_id, related_entity_type, related_entity_id, related_entity_label,
                    methodology, problem_statement_en, problem_statement_ar, five_whys_json,
                    root_cause_en, root_cause_ar, recommendations_en, recommendations_ar,
                    facilitator, approver_name, conducted_at, status, external_id, is_active,
                    is_deleted, created_at, updated_at, row_version)
               VALUES (?, 'incident', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0,
                       ?, ?, 0)""",
            ORG, inc_id, num, method, p_en, p_ar, whys, rc_en, rc_ar, rec_en, rec_ar,
            "Security Operations Center", "Head of Internal Audit", dt(days_ago=18),
            status, "RCA-" + num, dt(days_ago=18), dt(days_ago=18),
        )
        bump("cyber.root_cause_analyses", True)
        print(f"  + RCA for {num}  ({method}/{status})")


# ─────────────────────────────────────────────────────────────────────────────
#  cyber.vulnerability_sla_finding  (DEEPEN) — SLA clock findings on gov vulns,
#  bound to the existing default policy (id resolved in-script).
#  effective = base * exposure_mul * criticality_mul.  CHECK enums enforced.
#  natural key = (organisation_id, vulnerability_id).
# ─────────────────────────────────────────────────────────────────────────────
SLA_FINDINGS = [
    # gov_vuln_title (to resolve vulnerability_id from cyber.vulnerabilities), cvss_sev, exposure, asset_crit, opened_days_ago, status
    ("Unpatched on-prem Exchange RCE on the government mail server", "CRITICAL", "RESTRICTED", "CRITICAL", 1, "OPEN"),
    ("Active Directory privilege escalation (Zerologon-class)", "CRITICAL", "INTERNAL", "CRITICAL", 2, "WARNING"),
    ("Missing Transparent Data Encryption on the beneficiary database", "HIGH", "RESTRICTED", "HIGH", 3, "OPEN"),
    ("Weak TLS configuration on the Government Service Bus", "MEDIUM", "PARTNER", "HIGH", 4, "OPEN"),
    ("Outdated Spring framework on the e-service portal", "HIGH", "PUBLIC", "CRITICAL", 2, "WARNING"),
    ("Publicly accessible object-storage bucket at the DR site", "CRITICAL", "PUBLIC", "CRITICAL", 5, "BREACHED"),
]

# base SLA hours by cvss severity (from default policy)
BASE_HOURS = {"CRITICAL": 1.0, "HIGH": 4.0, "MEDIUM": 24.0, "LOW": 72.0}
EXPO_MUL = {"PUBLIC": 0.5, "PARTNER": 0.75, "INTERNAL": 1.0, "RESTRICTED": 1.5}
CRIT_MUL = {"CRITICAL": 0.5, "HIGH": 0.75, "MEDIUM": 1.0, "LOW": 1.5}


def seed_sla_findings(cur):
    print("\n[cyber.vulnerability_sla_finding] SLA clock findings (default policy)")
    cur.execute(
        "SELECT id FROM cyber.vulnerability_sla_policy "
        "WHERE organisation_id=? AND is_default=1 AND is_deleted=0",
        ORG,
    )
    prow = cur.fetchone()
    if not prow:
        print("  ! default SLA policy not found — skipping all SLA findings")
        for _ in SLA_FINDINGS:
            bump("cyber.vulnerability_sla_finding", False)
        return
    policy_id = prow[0]

    for (vtitle, cvss_sev, expo, acrit, opened_days, status) in SLA_FINDINGS:
        cur.execute(
            "SELECT id FROM cyber.vulnerabilities WHERE organisation_id=? AND title_en=?",
            ORG, vtitle,
        )
        vrow = cur.fetchone()
        if not vrow:
            bump("cyber.vulnerability_sla_finding", False)
            continue
        vuln_id = vrow[0]
        cur.execute(
            "SELECT id FROM cyber.vulnerability_sla_finding "
            "WHERE organisation_id=? AND vulnerability_id=?",
            ORG, vuln_id,
        )
        if cur.fetchone():
            bump("cyber.vulnerability_sla_finding", False)
            continue
        base = BASE_HOURS[cvss_sev]
        eff = round(base * EXPO_MUL[expo] * CRIT_MUL[acrit], 2)
        opened = dt(days_ago=opened_days)
        due = opened + timedelta(hours=eff)
        cur.execute(
            """INSERT INTO cyber.vulnerability_sla_finding
                   (organisation_id, vulnerability_id, policy_id, cvss_severity, asset_exposure,
                    asset_criticality, base_sla_hours, effective_sla_hours, opened_at, due_at,
                    status, is_deleted, created_by, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)""",
            ORG, vuln_id, policy_id, cvss_sev, expo, acrit, base, eff, opened, due,
            status, ADMIN, opened,
        )
        bump("cyber.vulnerability_sla_finding", True)
        print(f"  + vuln#{vuln_id}  {cvss_sev}/{expo}/{acrit}  eff={eff}h  ({status})")


# ─────────────────────────────────────────────────────────────────────────────
SKIPPED = {
    "cyber.cyber_frameworks": "Complete authoritative framework catalogue (6: NCA ECC/CCC/CSCC/OTCC, ISO 27001, PCI DSS).",
    "cyber.cyber_controls": "Full NCA ECC control catalogue (154) referenced by assessments/sub-controls; regulatory backbone, not demo data.",
    "cyber.cyber_sub_controls": "ECC sub-control catalogue (529) FK-bound to cyber_controls; pure regulatory reference.",
    "cyber.vulnerability_sla_policy": "Default Saudi NCA-ECC policy already present; a filtered unique index forbids a 2nd default. Referenced, not added.",
    "cyber.attack_surface_shadow_it": "Enterprise-generic shadow-IT; existing 4 rows demonstrate it. Rows better spent on asset/incident/control coverage.",
    "cyber.vuln_exceptions": "Niche risk-acceptance register; existing 2 rows show the workflow. Government risk-acceptance covered via cyber_lc.vulnerabilities status.",
}


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    print("=" * 78)
    print("Seeding Saudi-government cyber data (org 1) — schemas: cyber, cyber_lc")
    print("=" * 78)

    # order matters: assets before vulns; ASM assets before findings; incidents
    # before RCAs; gov vulns before SLA findings.
    seed_lc_assets(cur)
    seed_lc_threatintel(cur)
    seed_lc_incidents(cur)
    seed_lc_vulns(cur)

    seed_cyber_assets(cur)
    seed_control_assessments(cur)
    seed_cyber_incidents(cur)
    seed_simple_incidents(cur)
    seed_gov_vulns(cur)
    seed_action_plans(cur)
    seed_evidence(cur)
    seed_asm_assets(cur)
    seed_asm_findings(cur)
    seed_cloud_services(cur)
    seed_rca(cur)
    seed_sla_findings(cur)

    # ── Summary ──────────────────────────────────────────────────────────────
    print("\n" + "=" * 78)
    print("PER-TABLE SUMMARY (inserted / already-present)")
    print("=" * 78)
    total_ins = 0
    for tbl in sorted(STATS):
        ins, skip = STATS[tbl]
        total_ins += ins
        print(f"  {tbl:<38} inserted={ins:<4} present={skip}")
    print(f"\n  TOTAL ROWS INSERTED THIS RUN: {total_ins}")

    print("\nSKIPPED TABLES (reported, not modified):")
    for tbl, why in SKIPPED.items():
        print(f"  - {tbl}: {why}")

    # ── Verification counts ──────────────────────────────────────────────────
    print("\n" + "=" * 78)
    print("VERIFICATION — org-1 row counts after run")
    print("=" * 78)
    verify_tables = [
        "cyber_lc.assets", "cyber_lc.cyber_incidents", "cyber_lc.threat_intel",
        "cyber_lc.vulnerabilities", "cyber.control_assessments", "cyber.cyber_assets",
        "cyber.cyber_incidents", "cyber.incidents", "cyber.vulnerabilities",
        "cyber.cyber_action_plans", "cyber.cyber_evidence", "cyber.root_cause_analyses",
        "cyber.attack_surface_asset", "cyber.attack_surface_finding", "cyber.cloud_services",
        "cyber.vulnerability_sla_finding",
    ]
    for t in verify_tables:
        cur.execute(f"SELECT COUNT(*) FROM {t} WHERE organisation_id=1")
        print(f"  {t:<38} {cur.fetchone()[0]}")

    # coherence check: dangling cyber_lc vulnerabilities now resolved?
    cur.execute(
        "SELECT COUNT(*) FROM cyber_lc.vulnerabilities v "
        "WHERE v.organisation_id=1 AND NOT EXISTS "
        "(SELECT 1 FROM cyber_lc.assets a WHERE a.organisation_id=1 AND a.external_id=v.asset_id)"
    )
    dangling = cur.fetchone()[0]
    print(f"\n  cyber_lc.vulnerabilities still dangling (no matching asset_code): {dangling}")

    conn.close()
    print("\nDone.")


if __name__ == "__main__":
    main()
