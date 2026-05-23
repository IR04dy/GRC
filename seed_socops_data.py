# -*- coding: utf-8 -*-
"""Seed real day-2-day SOC Operations content for the dev DB.

Companion to `seed_real_data.py` — same idempotent `IF NOT EXISTS` pattern,
same `@@ROWCOUNT` trick to make the counter honest. Targets the socops
schema (V150 + V152). Three tenants, role-appropriate content:

    org=1  TSS GRC (Saudi Bank shape) — IR playbooks, SIEM events, scanners,
                                         PT, awareness, certs, SBOM, ZT,
                                         regulator exams, AppSec, DLP, IAM,
                                         insurance, brand mon, forensics
    org=2  Aramco Trading (Energy)   — OT-heavy: Purdue assets, OT-specific
                                        IR playbook steps, ICS pentest
    org=3  STC Bank (Telco-banking)  — USSD/SS7/HSM-heavy: telco-specific
                                        IAM JML, crypto inventory, brand mon

The seasonal_overlay + ir_playbook templates seeded at org=0 by V150 are
NOT relied upon (Flyway is OFF in dev). We insert directly into each org.

Run any number of times — idempotent.
"""
from __future__ import annotations

import sys
import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

CONN_STR = (
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;"
)

ORG_TSS, ORG_ARAMCO, ORG_STC = 1, 2, 3

conn = pyodbc.connect(CONN_STR)
conn.autocommit = True
cur = conn.cursor()

inserted = 0
skipped = 0
errors = 0


def run(sql: str, *params, label: str = "") -> bool:
    """Execute `IF NOT EXISTS ... INSERT` with honest @@ROWCOUNT counter.

    SET NOCOUNT ON suppresses the (n rows affected) mini-result that pyodbc
    otherwise lands on first, leaving fetchone() unable to reach the SELECT.
    """
    global inserted, skipped, errors
    try:
        wrapped = (
            "SET NOCOUNT ON; "
            + sql.rstrip().rstrip(";")
            + ";\nSELECT @@ROWCOUNT AS n;"
        )
        cur.execute(wrapped, params)
        row = cur.fetchone()
        n = int(row[0]) if row and row[0] is not None else 0
        if n > 0:
            inserted += n
            if label:
                print(f"  +{n} {label}")
            return True
        skipped += 1
        return False
    except Exception as e:
        errors += 1
        print(f"  ! [{label}] {str(e)[:200]}")
        return False


def section(name: str):
    print(f"\n── {name} " + "─" * (70 - len(name)))


# ══════════════════════════════════════════════════════════════════════
# 1. IR PLAYBOOKS  (all 3 orgs get the 6 categories; steps for org=1)
# ══════════════════════════════════════════════════════════════════════
section("socops.ir_playbook — IR runbooks (NIST SP 800-61 phases)")

PLAYBOOKS = [
    # (code, en, ar, desc_en, desc_ar, category, sev, mttd, mttr, frameworks)
    ("IR-PHISHING-01", "Phishing Response", "الاستجابة لرسائل التصيد",
     "Standard handling for a confirmed phishing report — user click through containment and lessons learned.",
     "مسار معالجة بلاغ تصيد مؤكد، من النقرة حتى الاحتواء واستخلاص الدروس.",
     "PHISHING", "MEDIUM", 15, 120, "NCA_ECC,SAMA_CSF,ISO_27035,NIST_800_61"),
    ("IR-RANSOMWARE-01", "Ransomware Response", "الاستجابة لبرامج الفدية",
     "Isolate, preserve evidence, assess blast radius, notify NCA per ECC-2, initiate BCP/DR.",
     "العزل، حفظ الأدلة، تقييم نطاق الضرر، إبلاغ NCA وفق ECC-2، تفعيل BCP/DR.",
     "RANSOMWARE", "CRITICAL", 5, 60, "NCA_ECC,SAMA_CSF,ISO_27035,NIST_800_61"),
    ("IR-BEC-01", "Business Email Compromise", "اختراق البريد الإلكتروني التجاري",
     "Suspected wire-fraud BEC — preserve session data, freeze transactions with bank, file SAMA STR.",
     "احتيال تحويل مالي — حفظ الجلسة، تجميد المعاملات مع البنك، إيداع STR لدى ساما.",
     "BEC", "HIGH", 30, 240, "NCA_ECC,SAMA_CSF,SAMA_AML"),
    ("IR-DDOS-01", "DDoS Response", "الاستجابة لهجمات الحرمان من الخدمة",
     "Engage upstream scrubbing, switch to anycast/CDN failover, monitor capacity, customer comms.",
     "تفعيل خدمة التنقية، التحويل إلى CDN، مراقبة السعة، إبلاغ العملاء.",
     "DDOS", "HIGH", 5, 30, "NCA_ECC,SAMA_CSF"),
    ("IR-INSIDER-01", "Insider Threat", "تهديد داخلي",
     "Engage HR + legal first, preserve activity logs, scope account access for revocation.",
     "التنسيق مع HR والإدارة القانونية أولاً، حفظ سجلات النشاط، تحديد صلاحيات الحساب.",
     "INSIDER", "HIGH", 60, 480, "NCA_ECC,SAMA_CSF,ISO_27035"),
    ("IR-BCP-ACTIVATION-01", "BCP/DR Activation", "تفعيل خطة الاستمرارية والتعافي",
     "Major disruption — bridge into BCM plan, log activation, open crisis room, notify SAMA/NCA.",
     "انقطاع كبير — الانتقال إلى خطة BCM، تسجيل التفعيل، فتح غرفة الأزمة، إبلاغ ساما/NCA.",
     "BCP_ACTIVATION", "CRITICAL", 5, 30, "NCA_ECC,SAMA_CSF,ISO_22301"),
    ("IR-DATA-BREACH-01", "Personal Data Breach", "خرق البيانات الشخصية",
     "PDPL Art.31 — assess harm, notify SDAIA within 72h, notify subjects if high harm, document RoPA.",
     "نظام حماية البيانات م.31 — تقييم الضرر، إبلاغ سدايا خلال 72 ساعة، إبلاغ أصحاب البيانات.",
     "DATA_BREACH", "HIGH", 60, 4320, "PDPL,NCA_ECC,SAMA_CSF,ISO_27035"),
    ("IR-CLOUD-BREACH-01", "Cloud Breach", "اختراق السحابة",
     "Cloud-native breach (M365/AWS/Azure) — revoke OAuth, rotate keys, capture CloudTrail, engage CSP IR.",
     "اختراق سحابي — إلغاء OAuth، تدوير المفاتيح، حفظ سجلات CloudTrail، التنسيق مع مزود الخدمة.",
     "CLOUD_BREACH", "HIGH", 30, 180, "NCA_CCC,NCA_ECC,SAMA_CSF"),
    ("IR-OT-INCIDENT-01", "OT/ICS Incident", "حادث الأنظمة الصناعية",
     "OT segment compromise — isolate Purdue zone, engage process safety, never auto-shut without ops approval.",
     "اختراق منطقة OT — عزل منطقة Purdue، التنسيق مع سلامة العمليات، عدم الإيقاف التلقائي بدون موافقة.",
     "OT_INCIDENT", "CRITICAL", 10, 240, "NCA_OTCC,IEC_62443,NIST_800_82"),
]

for org_id in (ORG_TSS, ORG_ARAMCO, ORG_STC):
    for code, te, ta, de, da, cat, sev, mttd, mttr, fw in PLAYBOOKS:
        # Aramco gets OT-specific + cross-cutting; STC gets all except OT
        if org_id == ORG_ARAMCO and cat in ("BEC",):
            continue
        if org_id == ORG_STC and cat in ("OT_INCIDENT",):
            continue
        run(
            """
            IF NOT EXISTS (SELECT 1 FROM socops.ir_playbook
                           WHERE organisation_id = ? AND playbook_code = ? AND is_deleted = 0)
            INSERT INTO socops.ir_playbook
                (organisation_id, playbook_code, title_en, title_ar, description_en, description_ar,
                 category, severity_trigger, target_mttd_minutes, target_mttr_minutes,
                 aligned_frameworks, status, version, invocation_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, 0, SYSUTCDATETIME())
            """,
            org_id, code,
            org_id, code, te, ta, de, da, cat, sev, mttd, mttr, fw,
            label=f"playbook[{org_id}] {code}",
        )

# Steps — only for org=1 (TSS, the demo flagship) to keep volume sane.
# Phishing playbook already seeded by V150 at org=0; we replicate for org=1.
section("socops.ir_playbook_step — checklist steps")

# Get the playbook IDs for org=1
cur.execute(
    "SELECT id, playbook_code FROM socops.ir_playbook "
    "WHERE organisation_id = ? AND is_deleted = 0", ORG_TSS,
)
pb_ids = {code: pid for pid, code in cur.fetchall()}

# Phishing — 9 steps (NIST 800-61 phases)
PHISHING_STEPS = [
    (1, "DETECT", "Receive user report or SIEM alert", "استلام البلاغ أو تنبيه SIEM",
     "Confirm source. If user-reported, capture .eml. If SIEM, pull correlation-rule details.",
     "تأكد من المصدر. احفظ .eml إن كان من المستخدم أو تفاصيل قاعدة SIEM.", "SOC_ANALYST_L1", 5, 1),
    (2, "TRIAGE", "Identify all recipients of the same campaign", "تحديد كل متلقي الحملة نفسها",
     "Query mail gateway for sender+subject+URL matches. Build impact list.",
     "استعلم البوابة عن المرسل والموضوع والرابط. جهّز قائمة المتأثرين.", "SOC_ANALYST_L2", 10, 1),
    (3, "TRIAGE", "Check if any user clicked the link", "تحقق من النقرات على الرابط",
     "Pull proxy/EDR logs 7 days. Flag every clicker.",
     "استخرج سجلات البروكسي/EDR لـ7 أيام. حدد كل من نقر.", "SOC_ANALYST_L2", 15, 1),
    (4, "CONTAIN", "Force credential reset for clickers", "فرض إعادة تعيين بيانات الاعتماد",
     "IAM: invalidate sessions + force password reset + force MFA re-enrol.",
     "IAM: إنهاء الجلسات + تغيير كلمة المرور + إعادة تسجيل MFA.", "IR_LEAD", 20, 1),
    (5, "CONTAIN", "Remove the phish from all mailboxes", "حذف الرسالة من كل الصناديق",
     "Use mail-gateway purge or Exchange compliance search.",
     "استخدم وظيفة الحذف في بوابة البريد أو بحث الامتثال.", "IR_LEAD", 15, 1),
    (6, "ERADICATE", "Block sender + URL + IP at perimeter", "حظر المرسل والرابط وIP",
     "Mail blocklist, DNS sinkhole, firewall deny. Document indicators.",
     "قائمة الحظر، إسناد DNS، رفض الجدار. وثّق المؤشرات.", "SOC_ANALYST_L2", 10, 1),
    (7, "RECOVER", "Verify clean state", "تأكد من نظافة الوضع",
     "Re-scan endpoints. Validate no persistence (scheduled tasks, mail rules, OAuth grants).",
     "أعد فحص الأجهزة. تحقق من عدم وجود استمرارية.", "SOC_ANALYST_L2", 30, 0),
    (8, "LESSONS_LEARNED", "Enrol clickers in targeted training", "إلحاق المتأثرين بتدريب مستهدف",
     "Push remedial micro-training. Track completion. Escalate repeat offenders.",
     "تدريب توعوي مستهدف. تتبع الإتمام. إحالة المتكررين.", "IR_LEAD", 15, 0),
    (9, "LESSONS_LEARNED", "Document AAR + update playbook", "وثّق AAR وحدّث الدليل",
     "Write After-Action Report. Flag playbook gaps as CAPA.",
     "اكتب تقرير ما بعد الحدث. سجل ثغرات الدليل في CAPA.", "IR_LEAD", 30, 0),
]

# Ransomware — 7 steps
RANSOMWARE_STEPS = [
    (1, "DETECT", "EDR/SIEM ransomware behaviour alert", "تنبيه EDR/SIEM لسلوك الفدية",
     "Mass file rename, shadow-copy deletion, or known ransom-note pattern detected.",
     "إعادة تسمية ملفات جماعية، حذف Shadow Copies، أو نمط فدية معروف.", "SOC_ANALYST_L1", 5, 1),
    (2, "TRIAGE", "Identify blast radius", "تحديد نطاق التأثر",
     "Map encrypted hosts via EDR. Identify file shares, AD-joined assets, cloud sync drives.",
     "حدد الأجهزة المتأثرة عبر EDR. ضمّن المشاركات وAD والسحابة.", "IR_LEAD", 30, 1),
    (3, "CONTAIN", "Isolate ALL affected hosts (network quarantine)", "عزل الأجهزة المتأثرة شبكياً",
     "Push EDR isolation. Block at NAC. Disable AD accounts of compromised users.",
     "عزل عبر EDR. حظر في NAC. تعطيل حسابات AD المتأثرة.", "IR_LEAD", 20, 1),
    (4, "CONTAIN", "Preserve volatile evidence BEFORE eradication", "حفظ الأدلة المتطايرة قبل المعالجة",
     "Memory dump, running processes, network connections — for forensics + insurance claim.",
     "إفراغ الذاكرة، العمليات، الاتصالات الشبكية — للأدلة الجنائية ومطالبة التأمين.", "FORENSICS", 60, 1),
    (5, "ERADICATE", "Remove ransomware persistence", "إزالة استمرارية الفدية",
     "Delete scheduled tasks, services, registry run keys, malicious binaries. Rebuild from gold image.",
     "حذف المهام المجدولة، الخدمات، مفاتيح Registry، البرمجيات الخبيثة. إعادة البناء من الصورة الذهبية.",
     "SOC_ANALYST_L2", 240, 1),
    (6, "RECOVER", "Restore from clean backups", "الاستعادة من نسخ نظيفة",
     "Validate backup integrity (offline + immutable). Stage in clean network segment. Verify before reconnect.",
     "تحقق من سلامة النسخ (غير متصلة وثابتة). جهّز في شبكة نظيفة. تحقق قبل الإعادة.",
     "IR_LEAD", 480, 1),
    (7, "LESSONS_LEARNED", "Mandatory NCA notification within 72h", "إبلاغ NCA الإلزامي خلال 72 ساعة",
     "NCA ECC-2 critical-incident notification. Include scope, impact, IOC, remediation plan.",
     "إبلاغ ECC-2 للحوادث الحرجة. شامل النطاق، التأثير، المؤشرات، خطة المعالجة.",
     "CISO", 60, 1),
]

# BEC — 6 steps (financial fraud emphasis)
BEC_STEPS = [
    (1, "DETECT", "Suspicious wire instruction reported", "بلاغ تعليمات تحويل مشبوهة",
     "Finance team flags an unusual wire-instruction email. Confirm sender header authenticity.",
     "فريق المالية يرصد تعليمات تحويل غير اعتيادية. تحقق من ترويسة المرسل.", "SOC_ANALYST_L1", 10, 1),
    (2, "TRIAGE", "Verify sender via out-of-band channel", "التحقق من المرسل عبر قناة بديلة",
     "Phone the legitimate sender on a known number — NEVER the number in the email signature.",
     "اتصل بالمرسل الشرعي على رقم معروف — ليس الرقم في توقيع البريد.", "IR_LEAD", 15, 1),
    (3, "CONTAIN", "Freeze pending wire transfer with bank", "تجميد التحويل المعلق مع البنك",
     "Call bank fraud desk. If transfer left already, request immediate recall via SWIFT.",
     "اتصل بمكافحة الاحتيال في البنك. إن غادر التحويل، اطلب الاسترجاع عبر SWIFT.", "CFO", 60, 1),
    (4, "CONTAIN", "Lock compromised mailbox", "إغلاق الصندوق المخترق",
     "Disable Exchange/M365 account, kill all sessions, revoke OAuth, reset password, re-enrol MFA.",
     "تعطيل Exchange/M365، إنهاء الجلسات، إلغاء OAuth، إعادة تعيين كلمة المرور وMFA.", "IR_LEAD", 30, 1),
    (5, "ERADICATE", "Audit mailbox rules + delegations", "تدقيق قواعد الصندوق والتفويضات",
     "Check inbox rules (hidden forwards), delegations, OAuth grants. Remove all unauthorized.",
     "افحص قواعد الصندوق، التفويضات، تصاريح OAuth. أزل غير المصرّح به.", "SOC_ANALYST_L2", 30, 1),
    (6, "LESSONS_LEARNED", "File SAMA STR if amount ≥ SAR 100,000", "إيداع STR لدى ساما إذا ≥ 100 ألف ريال",
     "AML team files Suspicious Transaction Report via goAML within 5 business days.",
     "فريق مكافحة غسل الأموال يرفع STR عبر goAML خلال 5 أيام عمل.", "AML_OFFICER", 240, 1),
]

# Cloud-breach — 5 steps
CLOUD_STEPS = [
    (1, "DETECT", "Anomalous cloud sign-in or API activity", "نشاط سحابي شاذ",
     "Impossible-travel sign-in, mass-download via API, unexpected resource creation.",
     "تسجيل دخول من جغرافيا مستحيلة، تنزيلات مكثفة، إنشاء موارد غير متوقعة.", "SOC_ANALYST_L1", 15, 1),
    (2, "CONTAIN", "Revoke all OAuth tokens for affected identity", "إلغاء جميع رموز OAuth",
     "AWS: revoke session tokens. Azure: revoke sign-in sessions. M365: kill sessions across all apps.",
     "AWS: إلغاء رموز الجلسة. Azure/M365: إلغاء كل الجلسات.", "IR_LEAD", 15, 1),
    (3, "CONTAIN", "Rotate all credentials touched", "تدوير كل بيانات الاعتماد المتأثرة",
     "Rotate IAM access keys, service principals, KMS keys, application secrets, API gateway keys.",
     "تدوير مفاتيح IAM، الكيانات الخدمية، مفاتيح KMS، أسرار التطبيقات.", "IR_LEAD", 60, 1),
    (4, "ERADICATE", "Capture full CloudTrail/Activity-log window", "حفظ سجلات CloudTrail/Activity كاملة",
     "Export last 90 days of cloud audit logs to immutable storage. Engage CSP IR support.",
     "صدّر سجلات 90 يوم إلى تخزين ثابت. التنسيق مع دعم مزود الخدمة.", "FORENSICS", 120, 1),
    (5, "RECOVER", "Validate cloud posture + close drift", "تحقق من وضع السحابة وإغلاق الانحراف",
     "Run CSPM (Wiz/Prisma/Defender). Close any policy drift introduced during incident.",
     "شغّل CSPM. أغلق أي انحراف عن السياسة حدث خلال الحادث.", "CLOUD_ENGINEER", 240, 0),
]

STEP_BUNDLES = [
    ("IR-PHISHING-01", PHISHING_STEPS),
    ("IR-RANSOMWARE-01", RANSOMWARE_STEPS),
    ("IR-BEC-01", BEC_STEPS),
    ("IR-CLOUD-BREACH-01", CLOUD_STEPS),
]

for code, steps in STEP_BUNDLES:
    pid = pb_ids.get(code)
    if not pid:
        continue
    for ord_, phase, te, ta, ie, ia, role, mins, critical in steps:
        run(
            """
            IF NOT EXISTS (SELECT 1 FROM socops.ir_playbook_step
                           WHERE playbook_id = ? AND step_order = ? AND is_deleted = 0)
            INSERT INTO socops.ir_playbook_step
                (organisation_id, playbook_id, step_order, phase, title_en, title_ar,
                 instructions_en, instructions_ar, responsible_role, estimated_minutes,
                 is_critical, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME())
            """,
            pid, ord_,
            ORG_TSS, pid, ord_, phase, te, ta, ie, ia, role, mins, critical,
            label=f"step {code}#{ord_} {phase}",
        )

# ══════════════════════════════════════════════════════════════════════
# 2. THREAT INTEL
# ══════════════════════════════════════════════════════════════════════
section("socops.threat_intel — Saudi-CERT, NCA, FS-ISAC bulletins")

INTEL = [
    # (external_id, source, title_en, title_ar, summary, ioc_type, ioc_value, sev, conf, tlp, tactics, techniques, sectors)
    ("SC-2026-0412", "SAUDI_CERT",
     "APT34 targeting GCC financial sector — credential harvesting via spear-phishing",
     "APT34 يستهدف القطاع المالي الخليجي عبر التصيد الموجّه",
     "Iranian-aligned APT34 (OilRig) running spear-phishing against KSA banks. Uses LinkedIn lures, malicious .docx with macro that drops a custom RAT named 'PowerExchange'.",
     "DOMAIN", "secure-mail-sa[.]update-portal[.]net", "HIGH", "HIGH", "AMBER",
     "TA0001,TA0011", "T1566.002,T1059.001,T1071.001", "BANKING,GOVT"),
    ("NCA-BULL-2026-15", "NCA",
     "Critical RCE in Citrix NetScaler ADC (CVE-2026-3155) — patch within 48h",
     "ثغرة تنفيذ شفرة عن بُعد حرجة في Citrix NetScaler — التصحيح خلال 48 ساعة",
     "NCA mandates patching CVE-2026-3155 within 48 hours. Active exploitation observed against multiple KSA entities. CVSS 9.8.",
     "CVE", "CVE-2026-3155", "CRITICAL", "HIGH", "AMBER",
     "TA0001", "T1190", "ALL"),
    ("FSIS-2026-Q2-09", "FS_ISAC",
     "Akira ransomware variant impacting MENA banks — exploits VPN appliances",
     "متغير Akira يستهدف البنوك في المنطقة — يستغل أجهزة VPN",
     "Akira affiliate group adapted TTPs for MENA. Initial access via SonicWall + Fortinet VPN N-day. Lateral via Cobalt Strike. Demands paid in XMR.",
     "HASH", "9b8e21c5d4f7a3e2b1c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7",
     "CRITICAL", "HIGH", "AMBER",
     "TA0001,TA0008,TA0040", "T1190,T1021.001,T1486", "BANKING"),
    ("SC-2026-0418", "SAUDI_CERT",
     "Hajj-themed phishing surge — fake Nusuk / Tawakkalna lookalikes",
     "موجة تصيد بمسمى الحج — مواقع مزيفة لنُسك وتوكلنا",
     "Ahead of Hajj 1447, surge in lookalike domain registrations impersonating Nusuk/Tawakkalna. Targets expatriate communities for visa-fee fraud.",
     "DOMAIN", "nusuk-visa-portal[.]ksa[.]sa[.]net", "MEDIUM", "HIGH", "GREEN",
     "TA0001", "T1566.002", "GOVT,BANKING,TRAVEL"),
    ("REC-FUT-2026-9120", "COMMERCIAL",
     "Leaked credentials for Saudi banking domains on dark forum 'Exposed-MENA'",
     "بيانات اعتماد مسربة للبنوك السعودية على منتدى Exposed-MENA",
     "8,400 credential pairs (email + plaintext password) for tssgrc.sa, alrajhibank.com.sa, snb.com employees offered for sale. Dataset dated 2026-04-30.",
     "EMAIL", "(bulk dataset — see attached IOC list)", "HIGH", "MEDIUM", "RED",
     "TA0001", "T1078.004", "BANKING"),
    ("NCA-BULL-2026-16", "NCA",
     "OTCC quarterly threat brief — OT/ICS sector advisory Q2 2026",
     "النشرة الفصلية لـOTCC — قطاع OT/ICS الربع الثاني 2026",
     "TRITON-style activity observed targeting safety instrumented systems at GCC industrial sites. NCA mandates SIL-3 segment review.",
     "CVE", "CVE-2026-1820 (Schneider Triconex)", "CRITICAL", "MEDIUM", "AMBER",
     "TA0001,TA0040", "T0817,T0816", "ENERGY,OIL_GAS,WATER"),
    ("MISP-2026-04211", "MISP",
     "Lockbit 4.0 affiliate IOCs — bank-of-bank targeting",
     "مؤشرات تابع LockBit 4.0 — يستهدف البنوك",
     "Three IPs used for Lockbit C2 in the last 7 days. Block at perimeter. TTPs unchanged from 3.0.",
     "IP", "185.193.88.45", "HIGH", "HIGH", "AMBER",
     "TA0011,TA0040", "T1071,T1486", "BANKING,HEALTHCARE"),
    ("SC-2026-0421", "SAUDI_CERT",
     "DDoS surge expected against payment rails — Hajj season window",
     "توقع موجة DDoS ضد قنوات الدفع — موسم الحج",
     "Historical pattern: DDoS volumetric attacks against mada / SARIE / SADAD payment infrastructure peak during Hajj. Engage scrubbing now.",
     "URL", "https://mada.com.sa", "HIGH", "MEDIUM", "GREEN",
     "TA0040", "T1498", "BANKING,PAYMENT"),
    ("NCA-BULL-2026-17", "NCA",
     "SBOM mandatory disclosure for critical infrastructure — deadline 2026-09-30",
     "الإفصاح الإلزامي عن SBOM للبنية التحتية الحرجة — الموعد 2026-09-30",
     "All CNI entities must publish a CycloneDX-format SBOM for production-facing applications by 2026-09-30.",
     "CVE", "(policy bulletin — no IOC)", "MEDIUM", "HIGH", "GREEN",
     "", "", "CNI,BANKING,ENERGY,HEALTHCARE"),
    ("FSIS-2026-Q2-12", "FS_ISAC",
     "BEC group 'Cobalt Mirage' returns — invoice-redirect via O365 inbox rules",
     "عودة مجموعة Cobalt Mirage — تحويل الفواتير عبر قواعد O365",
     "Cobalt Mirage actively running BEC against MENA banks' supplier-finance teams. Hidden inbox rules forward invoices to attacker-controlled addresses.",
     "DOMAIN", "supplier-finance-portal[.]live", "HIGH", "HIGH", "AMBER",
     "TA0001,TA0006,TA0009", "T1078.004,T1114.003,T1564.008", "BANKING,FINANCE"),
]

for ext, src, te, ta, summ, ioct, iocv, sev, conf, tlp, tactics, techs, sectors in INTEL:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM socops.threat_intel
                       WHERE organisation_id = ? AND source = ? AND external_id = ? AND is_deleted = 0)
        INSERT INTO socops.threat_intel
            (organisation_id, external_id, source, published_at, expires_at,
             title_en, title_ar, summary, ioc_type, ioc_value, severity, confidence, tlp,
             mitre_tactics, mitre_techniques, affected_sectors, actioned, created_at)
        VALUES (?, ?, ?, DATEADD(day, -ABS(CHECKSUM(NEWID())) % 30, SYSUTCDATETIME()),
                DATEADD(day, 60, SYSUTCDATETIME()),
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, SYSUTCDATETIME())
        """,
        ORG_TSS, src, ext,
        ORG_TSS, ext, src, te, ta, summ, ioct, iocv, sev, conf, tlp, tactics, techs, sectors,
        label=f"intel: {ext}",
    )

# ══════════════════════════════════════════════════════════════════════
# 3. SECURITY EVENTS (SIEM ingest)
# ══════════════════════════════════════════════════════════════════════
section("socops.security_event — SIEM telemetry")

EVENTS = [
    # (ext_id, src, severity, category, rule, src_host, src_ip, dst_host, dst_ip, user, status, hours_ago)
    ("SPL-2026-04-29-001", "SPLUNK", "CRITICAL", "MALWARE",
     "Endpoint: Ransomware behaviour signature (shadow-copy delete)",
     "WS-FIN-042", "10.32.14.42", None, None, "ali.alharbi", "TRIAGED", 2),
    ("SPL-2026-04-29-002", "SPLUNK", "HIGH", "AUTH_FAILURE",
     "Brute-force: 47 failed auth in 60s against VPN gateway",
     None, "203.0.113.55", "vpn-gw-01.tssgrc.sa", "10.10.0.1", "service-vpn", "IN_ANALYSIS", 4),
    ("SEN-2026-04-29-011", "SENTINEL", "HIGH", "EXFIL",
     "M365: anomalous mass download via OneDrive (4.2 GB in 5 min)",
     "WS-LEGAL-008", "10.32.20.8", None, None, "saad.alotaibi", "CONTAINED", 8),
    ("QR-2026-04-28-077", "QRADAR", "MEDIUM", "DLP",
     "DLP: PDPL_SENSITIVE pattern via web upload (filebin.net)",
     "WS-HR-014", "10.32.18.14", "filebin.net", "185.245.84.12", "noura.alqahtani", "NEW", 12),
    ("SPL-2026-04-28-089", "SPLUNK", "CRITICAL", "MALWARE",
     "EDR: Cobalt Strike beacon C2 detected outbound",
     "WS-IT-101", "10.32.30.101", "cdn-tracker[.]net", "45.142.215.99", "khalid.almutairi", "RESOLVED", 36),
    ("SPL-2026-04-28-104", "SPLUNK", "HIGH", "AUTH_FAILURE",
     "Impossible travel: sign-in from Riyadh and Tehran in 18 minutes",
     None, "94.182.193.16", "login.microsoftonline.com", None, "ahmad.alharbi@tssgrc.sa", "IN_ANALYSIS", 18),
    ("QR-2026-04-27-201", "QRADAR", "MEDIUM", "RECON",
     "Internal port scan against finance VLAN (1024 ports / 90s)",
     "WS-DEV-077", "10.32.40.77", "10.32.14.0/24", None, "yousef.alharbi", "TRIAGED", 22),
    ("SEN-2026-04-27-222", "SENTINEL", "INFO", "POLICY",
     "Conditional Access: legacy auth attempt (SMTP basic)",
     None, "10.32.50.4", "smtp.office365.com", None, "scanner-service", "RESOLVED", 30),
    ("SPL-2026-04-27-301", "SPLUNK", "HIGH", "INSIDER",
     "User accessed 14 customer records outside normal job profile",
     "WS-CSR-005", "10.32.60.5", None, None, "hala.alanazi", "IN_ANALYSIS", 26),
    ("SPL-2026-04-26-410", "SPLUNK", "CRITICAL", "MALWARE",
     "WAF: SQL injection attempt against /api/payments — 14 distinct payloads",
     None, "104.245.32.198", "api.tssgrc.sa", "10.50.0.10", None, "RESOLVED", 50),
    ("QR-2026-04-26-415", "QRADAR", "MEDIUM", "AUTH_FAILURE",
     "MFA fatigue: 28 push notifications in 7 minutes to same user",
     None, "37.218.245.114", None, None, "fawaz.alharbi", "FALSE_POSITIVE", 56),
    ("SEN-2026-04-26-440", "SENTINEL", "HIGH", "DLP",
     "Email: 12 customer CRM records emailed to personal Gmail",
     "WS-SALES-022", "10.32.70.22", "gmail.com", None, "majid.alharbi", "TRIAGED", 60),
    ("SPL-2026-04-25-501", "SPLUNK", "LOW", "POLICY",
     "PowerShell execution policy bypass attempted",
     "WS-IT-018", "10.32.30.18", None, None, "fahad.alotaibi", "RESOLVED", 76),
    ("QR-2026-04-25-555", "QRADAR", "HIGH", "DDOS",
     "Edge: SYN flood — 480k pps against payment endpoint",
     None, "45.95.146.42", "payments.tssgrc.sa", "10.50.0.30", None, "RESOLVED", 92),
    ("SPL-2026-04-24-601", "SPLUNK", "CRITICAL", "EXFIL",
     "DNS exfiltration: 4kB encoded data via TXT queries to suspicious domain",
     "WS-DEV-088", "10.32.40.88", "tunnel.dns-spy[.]net", "104.236.96.18", "abdullah.alharbi", "RESOLVED", 110),
]

# SLA targets per NCA ECC-2: Critical 1h, High 4h, Medium 24h
SLA_HOURS = {"CRITICAL": 1, "HIGH": 4, "MEDIUM": 24, "LOW": 72, "INFO": 168}

for ext, src, sev, cat, rule, sh, sip, dh, dip, usr, status, hours_ago in EVENTS:
    sla_h = SLA_HOURS.get(sev, 24)
    # sla_due_at = event_time + sla_h hours = NOW + (sla_h - hours_ago) hours
    sla_offset = sla_h - hours_ago  # may be negative (past SLA) — fine for DATEADD
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.security_event
                       WHERE organisation_id = ? AND source_system = ? AND external_id = ? AND is_deleted = 0)
        INSERT INTO socops.security_event
            (organisation_id, external_id, source_system, event_time, ingested_at,
             severity, category, rule_name, source_host, source_ip,
             destination_host, destination_ip, username, status, sla_due_at, created_at)
        VALUES (?, ?, ?,
                DATEADD(hour, {-hours_ago}, SYSUTCDATETIME()),
                DATEADD(hour, {-hours_ago}, SYSUTCDATETIME()),
                ?, ?, ?, ?, ?, ?, ?, ?, ?,
                DATEADD(hour, {sla_offset}, SYSUTCDATETIME()),
                SYSUTCDATETIME())
        """,
        ORG_TSS, src, ext,
        ORG_TSS, ext, src, sev, cat, rule, sh, sip, dh, dip, usr, status,
        label=f"event[{src}] {ext}",
    )

# ══════════════════════════════════════════════════════════════════════
# 4. SCANNER RUNS
# ══════════════════════════════════════════════════════════════════════
section("socops.scanner_run — vulnerability scanner ingest")

SCANS = [
    # (scanner, scan_name, scope, days_ago, status, total, crit, high, med, low, assets)
    ("TENABLE", "Monthly auth scan — RIYADH-DC1",
     "10.10.0.0/16 (data centre — primary)", 3, "COMPLETED", 1842, 12, 87, 412, 1331, 1240),
    ("TENABLE", "Monthly auth scan — DAMMAM-DC2",
     "10.11.0.0/16 (data centre — DR)", 3, "COMPLETED", 1654, 8, 71, 388, 1187, 1110),
    ("DEFENDER", "Weekly endpoint scan — corporate workstations",
     "All Defender-managed endpoints (4,212 hosts)", 5, "COMPLETED", 940, 0, 18, 188, 734, 4212),
    ("QUALYS", "External attack surface — perimeter",
     "Internet-facing IPs (88 hosts)", 2, "COMPLETED", 47, 1, 6, 18, 22, 88),
    ("WIZ", "Cloud posture — AWS Riyadh + Bahrain",
     "AWS me-south-1 + bahrain — 14 accounts", 1, "COMPLETED", 213, 4, 27, 81, 101, 14),
    ("PRISMA_CLOUD", "Container scan — production registry",
     "ECR + Harbor — 144 images", 4, "COMPLETED", 588, 7, 42, 211, 328, 144),
    ("TENABLE", "Ad-hoc scan after V150 deploy",
     "10.50.0.0/24 (DMZ — new services)", 10, "COMPLETED", 22, 0, 2, 8, 12, 18),
    ("DEFENDER", "Weekly endpoint scan — IT admins (privileged subset)",
     "Privileged-user endpoints (40 hosts)", 12, "COMPLETED", 18, 0, 1, 4, 13, 40),
]

for scn, name, scope, days_ago, status, tot, crit, high, med, low, assets in SCANS:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.scanner_run
                       WHERE organisation_id = ? AND scanner = ? AND scan_name = ? AND is_deleted = 0)
        INSERT INTO socops.scanner_run
            (organisation_id, scanner, scan_name, scope,
             started_at, completed_at, status,
             findings_total, findings_critical, findings_high, findings_medium, findings_low,
             assets_scanned, ingested_at, created_at)
        VALUES (?, ?, ?, ?,
                DATEADD(day, -{days_ago}, SYSUTCDATETIME()),
                DATEADD(hour, -{days_ago * 24 - 6}, SYSUTCDATETIME()),
                ?, ?, ?, ?, ?, ?, ?,
                DATEADD(hour, -{days_ago * 24 - 6}, SYSUTCDATETIME()),
                SYSUTCDATETIME())
        """,
        ORG_TSS, scn, name,
        ORG_TSS, scn, name, scope, status, tot, crit, high, med, low, assets,
        label=f"scan[{scn}] {name[:50]}",
    )

# ══════════════════════════════════════════════════════════════════════
# 5. PENTEST ENGAGEMENTS
# ══════════════════════════════════════════════════════════════════════
section("socops.pentest_engagement — penetration tests")

PENTESTS = [
    # (code, te, ta, type, scope, vendor, lead, nca_cert, status, total, crit, high, cost, frameworks)
    ("PT-2026-Q2-001",
     "Annual external network pentest", "اختبار اختراق خارجي سنوي",
     "EXTERNAL", "All internet-facing assets (88 IPs / 12 domains)",
     "BeyondLogic Saudi", "Eng. Mansour Al-Subaie", 1,
     "REMEDIATION", 18, 1, 4, 95000.00, "NCA_ECC,SAMA_CSF,PCI_DSS"),
    ("PT-2026-Q2-002",
     "Mobile banking app pentest (iOS + Android)", "اختبار اختراق تطبيق الجوال",
     "MOBILE", "Saudi-banking-app iOS 4.2 + Android 4.2 — pre-launch",
     "Cylance MENA", "Eng. Hadeel Al-Mohammed", 1,
     "REPORT_DELIVERED", 31, 2, 8, 165000.00, "NCA_ECC,SAMA_CSF,OWASP_MASVS"),
    ("PT-2026-Q1-005",
     "Internal red team — assume-breach scenario", "فريق أحمر داخلي — افتراض اختراق",
     "RED_TEAM", "Full enterprise — assumed initial foothold on finance VLAN",
     "Saudi Cyber Ops Co.", "Capt. Tareq Al-Dosari (rtd)", 1,
     "CLOSED", 22, 3, 7, 240000.00, "NCA_ECC,SAMA_CSF,MITRE_ATT_CK"),
    ("PT-2026-Q1-008",
     "Cloud pentest — AWS Bahrain", "اختبار اختراق سحابي — AWS البحرين",
     "CLOUD", "AWS me-south-1 + bahrain — production accounts",
     "Permiso Security", "Mr. Daniel Bohannon", 0,
     "CLOSED", 9, 0, 3, 88000.00, "NCA_CCC,NCA_ECC,SAMA_CSF"),
]

for code, te, ta, ptype, scope, vendor, lead, nca, status, tot, crit, high, cost, fw in PENTESTS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM socops.pentest_engagement
                       WHERE organisation_id = ? AND engagement_code = ? AND is_deleted = 0)
        INSERT INTO socops.pentest_engagement
            (organisation_id, engagement_code, title_en, title_ar, pentest_type,
             scope, rules_of_engagement, vendor_name, vendor_lead_contact, nca_certified_vendor,
             started_at, completed_at, status,
             findings_total, findings_critical, findings_high,
             cost_sar, aligned_frameworks, created_at)
        VALUES (?, ?, ?, ?, ?, ?,
                'Standard ROE: no DoS, no destructive payload, 09:00-17:00 AST testing window, daily checkpoint.',
                ?, ?, ?,
                DATEADD(day, -60, SYSUTCDATETIME()),
                DATEADD(day, -20, SYSUTCDATETIME()),
                ?, ?, ?, ?, ?, ?, SYSUTCDATETIME())
        """,
        ORG_TSS, code,
        ORG_TSS, code, te, ta, ptype, scope, vendor, lead, nca, status, tot, crit, high, cost, fw,
        label=f"pentest {code}",
    )

# ══════════════════════════════════════════════════════════════════════
# 6. AWARENESS CAMPAIGNS
# ══════════════════════════════════════════════════════════════════════
section("socops.awareness_campaign — phishing sims + training")

CAMPAIGNS = [
    # (code, te, ta, ctype, audience, target, days_ago, status, delivered, opened, clicked, reported, completed, repeat, pass_pct, notes)
    ("AW-2026-Q1-PHISH",
     "Q1 phishing simulation — fake DHL parcel delivery", "محاكاة تصيد Q1 — DHL مزيف",
     "PHISHING_SIM", "ALL_STAFF", 1240, 75, "COMPLETED",
     1232, 856, 142, 318, 0, 11, 88.5,
     "Click rate 11.5% — within 12% target. Report rate 25.8% — above 20% goal."),
    ("AW-2026-Q1-RAMADAN",
     "Ramadan awareness — charitable-cause scams", "توعية رمضان — احتيال الجمعيات الخيرية",
     "AWARENESS_MESSAGE", "ALL_STAFF", 1240, 50, "COMPLETED",
     1240, 1098, 0, 0, 1098, 0, 88.5,
     "Pre-Ramadan push delivered to all staff. 88.5% confirmed read."),
    ("AW-2026-Q2-SIM",
     "Q2 phishing simulation — fake SAMA Open Banking notification",
     "محاكاة تصيد Q2 — إشعار ساما المنظومة المفتوحة مزيف",
     "PHISHING_SIM", "ALL_STAFF", 1240, 30, "COMPLETED",
     1238, 912, 88, 401, 0, 7, 92.9,
     "Click rate 7.1% — improvement over Q1. Repeat-offender count cut from 11 to 7."),
    ("AW-2026-Q2-PRIV",
     "Privileged-user social-engineering training", "تدريب الهندسة الاجتماعية للمستخدمين المميزين",
     "TRAINING", "PRIVILEGED_USERS", 88, 15, "COMPLETED",
     88, 88, 0, 0, 84, 0, 95.5,
     "All 84 privileged users completed. 4 outstanding — escalated to line managers."),
    ("AW-2026-Q2-HAJJ",
     "Hajj season warning — Nusuk/Tawakkalna lookalikes", "تحذير موسم الحج — تطبيقات مزيفة",
     "AWARENESS_MESSAGE", "ALL_STAFF", 1240, 5, "RUNNING",
     1240, 720, 0, 0, 0, 0, None,
     "Push sent 5 days ago. Mid-campaign — 58% read so far."),
    ("AW-2026-Q3-PLAN",
     "Q3 phishing simulation — Hajj-themed visa-fee fraud (planned)",
     "محاكاة تصيد Q3 — احتيال رسوم الحج المخطط",
     "PHISHING_SIM", "ALL_STAFF", 1240, -25, "PLANNED",
     0, 0, 0, 0, 0, 0, None,
     "Launches in 25 days. Template prepared, exec approval received."),
]

for code, te, ta, ctype, audience, target, days_ago, status, delv, op, clk, rep, cmp, repeat, passp, notes in CAMPAIGNS:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.awareness_campaign
                       WHERE organisation_id = ? AND campaign_code = ? AND is_deleted = 0)
        INSERT INTO socops.awareness_campaign
            (organisation_id, campaign_code, title_en, title_ar, campaign_type,
             target_audience, target_user_count,
             started_at, ended_at, status,
             delivered_count, opened_count, clicked_count, reported_count,
             completed_count, repeat_offender_count, pass_rate_percent,
             aligned_controls, notes, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, {-days_ago}, SYSUTCDATETIME()),
                CASE WHEN ? = 'COMPLETED' THEN DATEADD(day, {-(max(days_ago - 14, 0))}, SYSUTCDATETIME()) ELSE NULL END,
                ?, ?, ?, ?, ?, ?, ?, ?,
                'NCA_ECC_1-5,SAMA_CSF_3.3.5,ISO_27001_A.7.2.2',
                ?, SYSUTCDATETIME())
        """,
        ORG_TSS, code,
        ORG_TSS, code, te, ta, ctype, audience, target,
        status, status, delv, op, clk, rep, cmp, repeat, passp, notes,
        label=f"campaign {code}",
    )

# ══════════════════════════════════════════════════════════════════════
# 7. CRYPTO ASSETS
# ══════════════════════════════════════════════════════════════════════
section("socops.crypto_asset — TLS certs, signing certs, HSM keys")

CRYPTO = [
    # (code, type, te, ta, issued_by, subject, algo, key_len, qsafe, days_until_expiry, deployed, owner, rot_days, classification, status)
    ("CERT-TLS-001", "TLS_CERT", "tssgrc.sa public TLS", "شهادة TLS الموقع العام",
     "DigiCert SHA2 Secure Server CA", "CN=tssgrc.sa,O=TSS GRC Co.,C=SA",
     "RSA-2048", 2048, 0, 88, "edge-lb-01,edge-lb-02", "Cloud Platform team", 395,
     "PUBLIC", "ACTIVE"),
    ("CERT-TLS-002", "TLS_CERT", "api.tssgrc.sa public TLS", "شهادة TLS API",
     "DigiCert SHA2 Secure Server CA", "CN=api.tssgrc.sa,O=TSS GRC Co.,C=SA",
     "ECDSA-P256", 256, 0, 12, "api-gw-01,api-gw-02", "API platform team", 395,
     "PUBLIC", "AT_RISK"),
    ("CERT-TLS-003", "TLS_CERT", "internal wildcard *.int.tssgrc.sa", "شهادة داخلية شاملة",
     "TSS Internal Issuing CA G2", "CN=*.int.tssgrc.sa,O=TSS GRC Co.,C=SA",
     "RSA-3072", 3072, 0, 287, "all internal services", "PKI team", 365,
     "RESTRICTED", "ACTIVE"),
    ("CERT-CS-001", "CODE_SIGNING", "Authenticode code signing", "شهادة توقيع البرامج",
     "DigiCert EV Code Signing CA", "CN=Trusted Security Solutions Co.,O=TSS GRC Co.,C=SA",
     "RSA-3072", 3072, 0, 412, "build pipeline (Jenkins)", "AppSec team", 1095,
     "RESTRICTED", "ACTIVE"),
    ("HSM-KEY-001", "HSM_KEY", "Production HSM master key", "مفتاح HSM الرئيسي للإنتاج",
     "Thales Luna 7 (KSA HSM cluster)", "label=prod-master-2025",
     "AES-256", 256, 0, 540, "thales-hsm-prim,thales-hsm-sec", "PKI team", 730,
     "TOP_SECRET", "ACTIVE"),
    ("HSM-KEY-002", "HSM_KEY", "PIN-block encryption key (PCI)", "مفتاح تشفير PIN (PCI)",
     "Thales Luna 7", "label=pci-pin-zmk-2025",
     "TDES-168", 168, 0, 95, "thales-hsm-prim", "Payments team", 365,
     "TOP_SECRET", "AT_RISK"),
    ("SSH-KEY-001", "SSH_KEY", "Jenkins build agent → deploy targets", "مفتاح SSH خادم Jenkins",
     None, "ed25519 jenkins-deploy@build-01",
     "Ed25519", 256, 1, 215, "build-01.int.tssgrc.sa", "DevOps team", 365,
     "RESTRICTED", "ACTIVE"),
    ("KMS-KEY-001", "KMS_KEY", "AWS KMS — S3 encryption", "مفتاح AWS KMS لـS3",
     "AWS KMS (me-south-1)", "alias/prod-s3-encryption",
     "AES-256", 256, 0, 0, "S3 buckets (all prod)", "Cloud Platform team", 365,
     "RESTRICTED", "ACTIVE"),
    ("SVC-KEY-001", "SERVICE_KEY", "Open Banking signing key (SAMA OBF)", "مفتاح توقيع المنظومة المفتوحة",
     "TSS Internal Issuing CA G2", "OU=Open Banking,CN=ob-signing-2026",
     "RSA-4096", 4096, 0, 165, "ob-gateway-01,ob-gateway-02", "Open Banking team", 365,
     "TOP_SECRET", "ACTIVE"),
    ("PGP-KEY-001", "PGP_KEY", "Treasury communications with SAMA", "مفتاح PGP للخزينة مع ساما",
     None, "treasury@tssgrc.sa <0xA1B2C3D4>",
     "RSA-4096", 4096, 0, 358, "treasury-ops-01", "Treasury team", 730,
     "TOP_SECRET", "ACTIVE"),
]

for code, atype, te, ta, issuer, subject, algo, klen, qs, days_to_exp, deployed, owner, rot, classn, status in CRYPTO:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.crypto_asset
                       WHERE organisation_id = ? AND asset_code = ? AND is_deleted = 0)
        INSERT INTO socops.crypto_asset
            (organisation_id, asset_code, asset_type, title_en, title_ar,
             issued_by, subject_dn, algorithm, key_length, quantum_safe,
             valid_from, valid_until, deployed_on, owner, rotation_period_days,
             last_rotated_at, classification, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -180, SYSUTCDATETIME()),
                DATEADD(day, {days_to_exp}, SYSUTCDATETIME()),
                ?, ?, ?,
                DATEADD(day, -180, SYSUTCDATETIME()),
                ?, ?, SYSUTCDATETIME())
        """,
        ORG_TSS, code,
        ORG_TSS, code, atype, te, ta, issuer, subject, algo, klen, qs,
        deployed, owner, rot, classn, status,
        label=f"crypto {code}",
    )

# ══════════════════════════════════════════════════════════════════════
# 8. SBOM COMPONENTS
# ══════════════════════════════════════════════════════════════════════
section("socops.sbom_component — software bill of materials")

SBOM = [
    # (app, comp, ver, eco, license, has_cve, max_sev, upgrade)
    ("Saudi Banking Mobile App (iOS)", "AFNetworking", "4.0.1", "OS_RPM", "MIT", 0, None, 0),
    ("Saudi Banking Mobile App (iOS)", "FirebaseCrashlytics", "10.18.0", "NPM", "Apache-2.0", 0, None, 1),
    ("Saudi Banking Mobile App (iOS)", "Alamofire", "5.6.0", "NPM", "MIT", 1, "MEDIUM", 1),
    ("Saudi Banking Mobile App (Android)", "okhttp3", "4.10.0", "MAVEN", "Apache-2.0", 1, "HIGH", 1),
    ("Saudi Banking Mobile App (Android)", "androidx.core:core-ktx", "1.10.1", "MAVEN", "Apache-2.0", 0, None, 1),
    ("Saudi Banking Mobile App (Android)", "Glide", "4.15.1", "MAVEN", "BSD-3-Clause", 0, None, 0),
    ("Core Banking — Payments Service", "spring-boot-starter-web", "3.1.5", "MAVEN", "Apache-2.0", 1, "MEDIUM", 1),
    ("Core Banking — Payments Service", "jackson-databind", "2.15.2", "MAVEN", "Apache-2.0", 1, "HIGH", 1),
    ("Core Banking — Payments Service", "log4j-core", "2.20.0", "MAVEN", "Apache-2.0", 0, None, 0),
    ("Core Banking — Payments Service", "mssql-jdbc", "12.4.1.jre17", "MAVEN", "MIT", 0, None, 0),
    ("Open Banking Gateway", "express", "4.18.2", "NPM", "MIT", 0, None, 1),
    ("Open Banking Gateway", "axios", "1.6.0", "NPM", "MIT", 1, "MEDIUM", 1),
    ("Open Banking Gateway", "jsonwebtoken", "9.0.0", "NPM", "MIT", 0, None, 0),
    ("Open Banking Gateway", "lodash", "4.17.21", "NPM", "MIT", 0, None, 0),
    ("KYC Service", "tensorflow", "2.15.0", "PYPI", "Apache-2.0", 0, None, 0),
    ("KYC Service", "fastapi", "0.104.1", "PYPI", "MIT", 0, None, 0),
    ("KYC Service", "pillow", "10.0.0", "PYPI", "HPND", 1, "HIGH", 1),
    ("Internal Admin Portal", "react", "18.2.0", "NPM", "MIT", 0, None, 0),
    ("Internal Admin Portal", "next", "13.4.19", "NPM", "MIT", 1, "MEDIUM", 1),
    ("Internal Admin Portal", "tailwindcss", "3.3.5", "NPM", "MIT", 0, None, 0),
]

for app, comp, ver, eco, lic, has_cve, max_sev, upg in SBOM:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.sbom_component
                       WHERE organisation_id = ? AND application_name = ? AND component_name = ? AND component_version = ? AND is_deleted = 0)
        INSERT INTO socops.sbom_component
            (organisation_id, application_name, component_name, component_version,
             package_ecosystem, license, has_known_cves, highest_cve_severity,
             upgrade_available, last_scanned_at, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -2, SYSUTCDATETIME()), SYSUTCDATETIME())
        """,
        ORG_TSS, app, comp, ver,
        ORG_TSS, app, comp, ver, eco, lic, has_cve, max_sev, upg,
        label=f"sbom {app[:25]}::{comp}",
    )

# ══════════════════════════════════════════════════════════════════════
# 9. ZERO TRUST MATURITY
# ══════════════════════════════════════════════════════════════════════
section("socops.zt_maturity — CISA ZTMM pillars (1 assessment cycle)")

ZT_PILLARS = [
    # (pillar, current, target, score, evidence, gap, plan)
    ("IDENTITY", "ADVANCED", "OPTIMAL", 78,
     "Azure AD with conditional access, MFA universal, risk-based sign-in, PIM for privileged.",
     "Service-account MFA not yet 100%. JIT elevation gaps in legacy AD workloads.",
     "Q3 2026: roll JIT elevation to remaining 12 legacy AD service accounts. Q4: passkey pilot."),
    ("DEVICES", "INITIAL", "ADVANCED", 52,
     "Intune-managed corporate fleet. EDR (Defender) on 96%. Compliance policies enforced for M365.",
     "BYOD posture inconsistent. OT engineering workstations outside Intune. Mobile-app EMM partial.",
     "Q2-Q3 2026: extend Intune to OT engineering. Mandate compliant-device for M365 + banking apps."),
    ("NETWORK", "INITIAL", "ADVANCED", 48,
     "VLAN segmentation, NAC at edge, ZTNA pilot for 40 contractors. East-west micro-seg in progress.",
     "Datacentre east-west still flat-VLAN. Cloud security groups inconsistent.",
     "Q3 2026: complete DC micro-seg phase 1 (PCI scope + finance VLAN). Q4: cloud SG hardening."),
    ("APPLICATIONS", "INITIAL", "ADVANCED", 55,
     "Workforce SSO via Azure AD. CASB (Defender for Cloud Apps) deployed. SAST/DAST pipelines for 80% of apps.",
     "API authorisation per-endpoint inconsistent. Legacy banking apps still on SAML 1.1.",
     "Q3 2026: API-gateway authorisation policy uniform. Migrate legacy apps off SAML 1.1."),
    ("DATA", "INITIAL", "ADVANCED", 50,
     "Data classification labels (NDC-aligned) on 60% of M365 estate. Purview DLP on email + SharePoint.",
     "Database-level encryption inconsistent. Tokenisation for PCI scope only — no PII-wide tokenisation.",
     "Q3 2026: DLP coverage for OneDrive + Teams. Q4: PII tokenisation pilot in CRM."),
    ("VISIBILITY", "ADVANCED", "OPTIMAL", 75,
     "Splunk Enterprise Security across IT + cloud. UEBA enabled. 18-month log retention.",
     "OT telemetry not in SIEM. Cloud-native logs partially correlated.",
     "Q3 2026: OT historian → SIEM. Q4: deep CloudTrail + Activity Log correlation."),
    ("AUTOMATION", "INITIAL", "ADVANCED", 45,
     "SOAR (Phantom) automating top-12 playbooks. Auto-isolation for ransomware behaviour signature.",
     "Most playbooks still manual. SOAR not integrated with all upstream tools.",
     "Q3-Q4 2026: 25 additional playbooks to SOAR. Integrate Phantom with Defender + Wiz."),
]

for pillar, cur_stg, tgt_stg, score, evid, gap, plan in ZT_PILLARS:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM socops.zt_maturity
                       WHERE organisation_id = ? AND assessment_code = ? AND pillar = ? AND is_deleted = 0)
        INSERT INTO socops.zt_maturity
            (organisation_id, assessment_code, pillar, current_stage, target_stage,
             score, evidence_summary, gap_summary, action_plan,
             assessed_at, assessed_by, created_at)
        VALUES (?, '2026-H1', ?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -10, SYSUTCDATETIME()),
                'CISO + Cloud Security Lead', SYSUTCDATETIME())
        """,
        ORG_TSS, "2026-H1", pillar,
        ORG_TSS, pillar, cur_stg, tgt_stg, score, evid, gap, plan,
        label=f"zt {pillar} ({cur_stg}, {score}/100)",
    )

# ══════════════════════════════════════════════════════════════════════
# 10. REGULATOR EXAMINATIONS
# ══════════════════════════════════════════════════════════════════════
section("socops.regulator_examination — SAMA / NCA / SDAIA / CMA")

EXAMS = [
    # (code, regulator, exam_type, te, ta, scope, lead, days_notified_ago, fw_start, fw_end, draft, final, resp_due, resp_sub, closed_at, status, total, crit, high, med_low, rating, framework, notes)
    ("SAMA-2026-CYBER-Q2",
     "SAMA", "ON_SITE",
     "SAMA Cyber Security on-site examination — Q2 2026",
     "ساما — فحص أمن سيبراني ميداني الربع الثاني 2026",
     "Cyber security control framework (SAMA CSF) — all 6 control families + payments-specific overlay",
     "Mr. Khalid Al-Saif (Senior Examiner)",
     45, 28, 14, 7, None, None, None, None,
     "FIELDWORK", 0, 0, 0, 0, None, "SAMA_CSF",
     "Fieldwork in progress. 6 examiners on-site. Daily wrap meetings."),
    ("NCA-2026-ECC2-AUDIT",
     "NCA", "REMOTE",
     "NCA ECC-2 annual self-assessment review",
     "NCA — مراجعة التقييم الذاتي السنوي ECC-2",
     "ECC-2 controls 1-3 (Governance) + 2-1 to 2-15 (Asset Management + IAM) deep dive",
     "Eng. Saud Al-Otaibi (NCA Compliance Office)",
     60, 35, 14, None, None, None, None, None,
     "DRAFT_REPORT", 12, 0, 3, 9, None, "NCA_ECC_2",
     "Draft report received. 12 observations (3 high, 9 medium/low). 30-day response window."),
    ("SDAIA-2026-PDPL-DPIA",
     "NDMO", "QUESTIONNAIRE",
     "SDAIA PDPL data-flow questionnaire (annual)",
     "سدايا — استبيان تدفق البيانات السنوي PDPL",
     "RoPA + cross-border transfers + DPIA register + breach-notification SLA history",
     "Ms. Layla Al-Mohammed (SDAIA PDPL Office)",
     30, None, None, None, None, 14, None, None,
     "AWAITING_RESPONSE", 0, 0, 0, 0, None, "PDPL",
     "Questionnaire received. Compiling responses across RoPA + 2024-2026 breach log."),
    ("CMA-2026-INSIDER-CK",
     "CMA", "THEMATIC_REVIEW",
     "CMA insider-information handling thematic review",
     "هيئة السوق المالية — المراجعة الموضوعية لحماية المعلومات الداخلية",
     "Materials non-public information (MNPI) controls — corporate finance + treasury + investor relations",
     "Eng. Norah Al-Subaie (CMA Enforcement)",
     90, 75, 28, 14, 7, 0, 60, 50,
     "REMEDIATION", 4, 0, 1, 3, "SATISFACTORY", "CMA_INSIDER",
     "Final report received. 1 high (info-barrier policy gap), 3 med/low. Remediation plan accepted."),
]

for code, reg, etype, te, ta, scope, lead, notif_ago, fw_s, fw_e, draft, final_, resp_due, resp_sub, closed, status, tot, crit, high, ml, rating, fw, notes in EXAMS:
    fw_start_clause = f"DATEADD(day, -{fw_s}, SYSUTCDATETIME())" if fw_s is not None else "NULL"
    fw_end_clause = f"DATEADD(day, -{fw_e}, SYSUTCDATETIME())" if fw_e is not None else "NULL"
    draft_clause = f"DATEADD(day, -{draft}, SYSUTCDATETIME())" if draft is not None else "NULL"
    final_clause = f"DATEADD(day, -{final_}, SYSUTCDATETIME())" if final_ is not None else "NULL"
    resp_due_clause = f"DATEADD(day, {resp_due}, SYSUTCDATETIME())" if resp_due is not None else "NULL"
    resp_sub_clause = f"DATEADD(day, -{resp_sub}, SYSUTCDATETIME())" if resp_sub is not None else "NULL"
    closed_clause = f"DATEADD(day, -{closed}, SYSUTCDATETIME())" if closed is not None else "NULL"
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.regulator_examination
                       WHERE organisation_id = ? AND examination_code = ? AND is_deleted = 0)
        INSERT INTO socops.regulator_examination
            (organisation_id, examination_code, regulator, examination_type,
             title_en, title_ar, scope_areas, lead_examiner_name,
             notified_at, fieldwork_start_at, fieldwork_end_at,
             draft_report_at, final_report_at, response_due_at, response_submitted_at, closed_at,
             status, findings_total, findings_critical, findings_high, findings_med_low,
             overall_rating, related_framework, notes, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -{notif_ago}, SYSUTCDATETIME()),
                {fw_start_clause}, {fw_end_clause},
                {draft_clause}, {final_clause}, {resp_due_clause}, {resp_sub_clause}, {closed_clause},
                ?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME())
        """,
        ORG_TSS, code,
        ORG_TSS, code, reg, etype, te, ta, scope, lead,
        status, tot, crit, high, ml, rating, fw, notes,
        label=f"exam {code}",
    )

# ══════════════════════════════════════════════════════════════════════
# 11. SEASONAL OVERLAYS
# ══════════════════════════════════════════════════════════════════════
section("socops.seasonal_overlay — Hajj / Ramadan / National Day windows")

OVERLAYS = [
    ("RAMADAN-1447", "Ramadan 1447", "رمضان 1447",
     "2026-02-17", "2026-03-19", "1447/09/01 — 1447/09/30",
     "Phishing spikes targeting expat remittance flows; payment-card fraud peaks; "
     "impersonation of charitable causes; Tawakkalna/Nusuk app fakes proliferate. "
     "Tighten DLP egress rules and DMARC enforcement."),
    ("HAJJ-1447", "Hajj 1447", "حج 1447",
     "2026-05-26", "2026-06-08", "1447/12/01 — 1447/12/13",
     "DDoS surge against payment rails + ministry portals; impersonation of MoH/Nusuk for visa scams; "
     "surge in card-not-present fraud; OT systems at Hajj sites at elevated risk."),
    ("NATIONAL-DAY-1448", "National Day 1448 / 2026", "اليوم الوطني 1448 / 2026",
     "2026-09-23", "2026-09-25", "1448/03/12 — 1448/03/14",
     "Hacktivist DDoS / deface activity against gov-facing sites; brand-impersonation around "
     "national-day promotions."),
]
for org_id in (ORG_TSS, ORG_ARAMCO, ORG_STC):
    for code, te, ta, start, end, hijri, profile in OVERLAYS:
        run(
            """
            IF NOT EXISTS (SELECT 1 FROM socops.seasonal_overlay
                           WHERE organisation_id = ? AND overlay_code = ? AND is_deleted = 0)
            INSERT INTO socops.seasonal_overlay
                (organisation_id, overlay_code, title_en, title_ar,
                 starts_at, ends_at, hijri_label, threat_profile,
                 auto_tighten_kris, auto_tighten_slas, is_active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, SYSUTCDATETIME())
            """,
            org_id, code,
            org_id, code, te, ta, start, end, hijri, profile,
            label=f"overlay[{org_id}] {code}",
        )

# ══════════════════════════════════════════════════════════════════════
# 12. OT ASSETS (Aramco-style + a few branch-bank OT)
# ══════════════════════════════════════════════════════════════════════
section("socops.ot_asset — Purdue model OT inventory")

OT_ARAMCO = [
    # (code, te, ta, purdue, class, vendor, model, fw, site, function, protos, sil, criticality, remote, segment, status)
    ("OT-RTH-PLC-001",
     "Crude-stabiliser PLC", "PLC مثبت النفط الخام", 1,
     "SAFETY_PLC", "Siemens", "S7-1500F", "V2.9.5",
     "RTH-01", "Crude oil stabilisation — safety interlocks for Ras Tanura H plant",
     "Profibus,Profinet", "SIL-3", "CRITICAL", 0, "Purdue-1 (Safety)", "ACTIVE"),
    ("OT-RTH-DCS-001",
     "Distributed Control System — Ras Tanura H", "نظام DCS — رأس تنورة هـ", 2,
     "SCADA_SERVER", "Honeywell", "Experion PKS R520", "R520.1",
     "RTH-01", "Process control — flow, pressure, temperature across stabiliser train",
     "OPC-UA,Modbus", "SIL-2", "CRITICAL", 0, "Purdue-2 (Control)", "ACTIVE"),
    ("OT-RTH-HMI-007",
     "Operator HMI — control room", "واجهة المشغل — غرفة التحكم", 2,
     "HMI", "Honeywell", "Station 2026", "R520.1",
     "RTH-01", "Operator visualization + supervisory commands",
     "OPC-UA", None, "HIGH", 0, "Purdue-2 (Control)", "ACTIVE"),
    ("OT-RTH-HIST-001",
     "Historian server", "خادم Historian", 3,
     "HISTORIAN", "OSIsoft (Aveva PI)", "PI System 2024", "2024.1",
     "RTH-01", "Long-term process data archival — 5 year retention",
     "OPC-UA,PItoPI", None, "HIGH", 1, "Purdue-3 (Plant)", "ACTIVE"),
    ("OT-PIPE-RTU-014",
     "Pipeline RTU — Saudi Aramco East-West", "RTU خط الأنابيب", 1,
     "RTU", "Schneider Electric", "SCADAPack 350E", "2.16.0",
     "RAS-RAB", "Pipeline flow + leak detection — Rabigh segment",
     "DNP3,Modbus", "SIL-2", "HIGH", 1, "Purdue-1 (Field)", "ACTIVE"),
    ("OT-RTH-EWS-002",
     "Engineering workstation", "محطة عمل هندسية", 2,
     "ENGINEERING_WORKSTATION", "Dell", "Precision 7960 Tower", "Win 11 IoT Ent",
     "RTH-01", "Logic download + configuration changes (PLC programmers)",
     "S7Comm,OPC-UA,SSH", None, "HIGH", 0, "Purdue-2 (Control)", "ACTIVE"),
    ("OT-PERIMETER-FW-01",
     "OT perimeter firewall", "جدار ناري حدود OT", 3,
     "OT_FIREWALL", "Fortinet", "FortiGate 600F", "FortiOS 7.4.3",
     "RTH-01", "IT/OT boundary firewall — data-diode pair downstream",
     "TCP,UDP", None, "CRITICAL", 0, "Purdue-3 (Plant DMZ)", "ACTIVE"),
]

for code, te, ta, purdue, klass, vendor, model, fw, site, fn, protos, sil, crit, remote, segment, status in OT_ARAMCO:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.ot_asset
                       WHERE organisation_id = ? AND asset_code = ? AND is_deleted = 0)
        INSERT INTO socops.ot_asset
            (organisation_id, asset_code, title_en, title_ar, purdue_level, asset_class,
             vendor, model, firmware_version, site_code, site_name, process_function,
             industrial_protocols, safety_integrity_level, criticality,
             last_patched_at, patch_window, remote_access_enabled, network_segment, status,
             created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -180, SYSUTCDATETIME()),
                'Annual turnaround (Q4 2026)',
                ?, ?, ?, SYSUTCDATETIME())
        """,
        ORG_ARAMCO, code,
        ORG_ARAMCO, code, te, ta, purdue, klass, vendor, model, fw, site, "Ras Tanura H Plant" if site == "RTH-01" else "Rabigh Pump Station",
        fn, protos, sil, crit, remote, segment, status,
        label=f"OT[Aramco] {code}",
    )

# ══════════════════════════════════════════════════════════════════════
# 13. IAM JML EVENTS  (TSS bank shape)
# ══════════════════════════════════════════════════════════════════════
section("socops.iam_jml_event — joiner/mover/leaver + privileged events")

JML = [
    # (code, etype, days_ago, user, email, dept, title, src, target, role, priv, just, approver, approver_role, outcome, sod, sod_detail)
    ("JML-2026-04-00342", "JOINER", 30,
     "noura.alqahtani", "noura.alqahtani@tssgrc.sa", "Risk Management", "Senior Risk Analyst",
     "AZURE_AD", "M365 + SAP HR + Risk Workbench",
     "RiskAnalyst,Reader-RiskRegister", 0,
     "New hire onboarding — replaces resigned analyst (ticket HR-2026-1142).",
     "saud.aldosari", "Risk Manager", "COMPLETED", 0, None),
    ("JML-2026-04-00351", "MOVER", 22,
     "khalid.almutairi", "khalid.almutairi@tssgrc.sa", "IT Infrastructure → SOC", "L2 Analyst",
     "AZURE_AD", "M365 + Splunk + EDR console",
     "SOC-L2,Splunk-Read,Defender-Investigate", 1,
     "Internal transfer Infra → SOC. Prior IT roles revoked per leaver-of-old workflow.",
     "fawaz.alharbi", "CISO", "COMPLETED", 0, None),
    ("JML-2026-04-00355", "LEAVER", 14,
     "majid.alharbi", "majid.alharbi@tssgrc.sa", "Sales", "Senior Account Manager",
     "AZURE_AD", "M365 + Salesforce + ServiceNow",
     "All roles", 0,
     "Resignation effective 14 days ago. Mailbox forwarded to manager for 30 days.",
     "saad.alotaibi", "Sales Director", "COMPLETED", 0, None),
    ("JML-2026-04-00360", "PRIVILEGED_ELEVATION", 8,
     "fahad.alotaibi", "fahad.alotaibi@tssgrc.sa", "IT Infrastructure", "Senior Infra Engineer",
     "CYBERARK", "Production AD domain controller",
     "Domain Admin (4-hour break-glass)", 1,
     "Emergency: PDC FSMO transfer required after physical hardware failure. Approved by CISO.",
     "fawaz.alharbi", "CISO", "COMPLETED", 0, None),
    ("JML-2026-04-00364", "ROLE_GRANT", 5,
     "abdullah.alharbi", "abdullah.alharbi@tssgrc.sa", "Development", "Senior Backend Dev",
     "SAILPOINT", "Production banking API",
     "Backend-Admin-Read", 0,
     "Investigation support — temporary 7-day grant for incident IR-2026-04-001.",
     "khalid.almutairi", "SOC L2 Analyst", "COMPLETED", 0, None),
    ("JML-2026-04-00368", "JOINER", 3,
     "hala.alanazi", "hala.alanazi@tssgrc.sa", "Customer Service", "CSR Agent",
     "OKTA", "M365 + Genesys + Mobile-banking-app-admin",
     "CSR-L1,Customer-Read", 0,
     "New hire — CSR batch onboarding.",
     "hr.bot", "HR Automation", "COMPLETED", 0, None),
    ("JML-2026-04-00372", "ROLE_GRANT", 2,
     "yousef.alharbi", "yousef.alharbi@tssgrc.sa", "Internal Audit", "Senior Auditor",
     "MANUAL", "Production banking core (read-only)",
     "Audit-Read-Production", 1,
     "Q2 audit cycle — read access for evidence gathering, expires 2026-08-31.",
     "saud.aldosari", "Chief Internal Auditor", "COMPLETED", 0, None),
    ("JML-2026-04-00378", "ACCESS_REVIEW", 1,
     "fawaz.alharbi", "fawaz.alharbi@tssgrc.sa", "CISO Office", "CISO",
     "SAILPOINT", "M365 + SailPoint + CyberArk + all admin consoles",
     "Multiple privileged roles (12 entitlements)", 1,
     "Quarterly access review — CISO self-attestation. 2 roles revoked (no longer needed).",
     "self-attestation", "Quarterly Review", "COMPLETED", 0, None),
    ("JML-2026-04-00380", "ROLE_GRANT", 8,
     "amal.alshehri", "amal.alshehri@tssgrc.sa", "Finance", "Treasury Analyst",
     "SAILPOINT", "Treasury workstation + SARIE access",
     "Treasury-Payments-Initiator + Treasury-Payments-Approver", 1,
     "BLOCKED: SoD violation — same user as initiator AND approver.",
     "(rejected)", "(none)", "DENIED", 1,
     "User would hold both initiator and approver roles on Treasury Payments. SoD rule SoD-FIN-001 violated."),
    ("JML-2026-04-00382", "ROLE_REVOKE", 30,
     "majid.alharbi", "majid.alharbi@tssgrc.sa", "(former) Sales", "(former) Sr Acct Mgr",
     "AZURE_AD", "M365 + Salesforce",
     "All Salesforce roles", 0,
     "Leaver cleanup — automatic 14 days post-departure.",
     "iam.automation", "IAM Automation", "COMPLETED", 0, None),
]

for code, etype, days_ago, user, email, dept, title, src, tgt, role, priv, just, approver, app_role, outcome, sod, sod_detail in JML:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.iam_jml_event
                       WHERE organisation_id = ? AND event_code = ? AND is_deleted = 0)
        INSERT INTO socops.iam_jml_event
            (organisation_id, event_code, event_type, occurred_at,
             subject_username, subject_email, subject_department, subject_job_title,
             source_system, target_system, role_or_entitlement, privileged, justification,
             approver_username, approver_role, outcome, sod_violation_detected, sod_violation_detail,
             requested_at, approved_at, completed_at, created_at)
        VALUES (?, ?, ?,
                DATEADD(day, -{days_ago}, SYSUTCDATETIME()),
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -{days_ago + 1}, SYSUTCDATETIME()),
                DATEADD(day, -{days_ago}, SYSUTCDATETIME()),
                CASE WHEN ? = 'COMPLETED' THEN DATEADD(day, -{days_ago}, SYSUTCDATETIME()) ELSE NULL END,
                SYSUTCDATETIME())
        """,
        ORG_TSS, code,
        ORG_TSS, code, etype, user, email, dept, title, src, tgt, role, priv, just,
        approver, app_role, outcome, sod, sod_detail,
        outcome,
        label=f"jml {code} {etype}",
    )

# ══════════════════════════════════════════════════════════════════════
# 14. APPSEC FINDINGS
# ══════════════════════════════════════════════════════════════════════
section("socops.appsec_finding — SAST/DAST/IAST/SCA")

APPSEC = [
    # (ext_id, scanner, scan_type, app, repo, branch, title, cwe, cve, sev, cvss, file, line, comp, comp_ver, fix_ver, desc, advice, status, days_ago)
    ("SNYK-2026-04-1042", "SNYK", "SCA",
     "Saudi Banking Mobile App (Android)", "github.com/tssgrc/banking-mobile-android", "main",
     "okhttp3 4.10.0 vulnerable to HTTP/2 rapid-reset DoS", "CWE-770", "CVE-2023-44487",
     "HIGH", 7.5, "app/build.gradle", 47, "okhttp3", "4.10.0", "4.12.0",
     "okhttp 4.10.0 inherits CVE-2023-44487 rapid-reset HTTP/2 DoS.",
     "Upgrade okhttp to 4.12.0+. Mobile-build CI already has dependency-update PR open.",
     "TRIAGED", 7),
    ("SNYK-2026-04-1043", "SNYK", "SCA",
     "Saudi Banking Mobile App (iOS)", "github.com/tssgrc/banking-mobile-ios", "main",
     "Alamofire 5.6.0 — TLS cert validation bypass under condition",
     "CWE-295", None, "MEDIUM", 6.5, "Podfile.lock", 88, "Alamofire", "5.6.0", "5.8.0",
     "Alamofire <5.7 has a TLS validation bypass when ATS exceptions configured incorrectly.",
     "Upgrade Alamofire to 5.8.0. Audit Info.plist NSAppTransportSecurity dictionary.",
     "FIXED", 14),
    ("VER-2026-04-2014", "VERACODE", "SAST",
     "Core Banking — Payments Service", "github.com/tssgrc/core-banking-payments", "develop",
     "SQL injection — parameter 'beneficiary_iban' passes through string concat",
     "CWE-89", None, "CRITICAL", 9.8,
     "src/main/java/com/tss/payments/PaymentsRepository.java", 124,
     None, None, None,
     "User-controlled iban parameter concatenated into SQL string in findByIban().",
     "Replace string concat with PreparedStatement parameter binding. PR #1842 has fix.",
     "TRIAGED", 4),
    ("VER-2026-04-2018", "VERACODE", "SAST",
     "Core Banking — Payments Service", "github.com/tssgrc/core-banking-payments", "develop",
     "Hardcoded credential — fallback HMAC key",
     "CWE-798", None, "HIGH", 7.5,
     "src/main/java/com/tss/payments/config/SecurityConfig.java", 67,
     None, None, None,
     "Hardcoded HMAC default-fallback key found in SecurityConfig.",
     "Remove default. Require KMS-fetched key at startup; fail-closed if missing.",
     "OPEN", 4),
    ("SON-2026-04-3201", "SONARQUBE", "SAST",
     "Open Banking Gateway", "github.com/tssgrc/openbanking-gw", "main",
     "Insecure logging — JWT contents written to debug log",
     "CWE-532", None, "MEDIUM", 5.3,
     "src/middleware/audit.ts", 145, None, None, None,
     "JWT payload logged at DEBUG level — leaks subject email + scope claims.",
     "Redact JWT payload in logs. Use structured logger with PII redaction filter.",
     "TRIAGED", 10),
    ("CKM-2026-04-4090", "CHECKMARX", "DAST",
     "Open Banking Gateway", "github.com/tssgrc/openbanking-gw", "main",
     "Missing rate-limit on /api/payments/initiate",
     "CWE-770", None, "HIGH", 7.4,
     "POST /api/payments/initiate", None, None, None, None,
     "Endpoint accepts 1000 requests/sec/IP with no rate-limit.",
     "Apply rate-limit middleware: 60 req/min/user, 600 req/min/IP. Use Redis-backed limiter.",
     "OPEN", 2),
    ("GHAS-2026-04-5012", "GITHUB_AS", "SECRETS",
     "Internal Admin Portal", "github.com/tssgrc/admin-portal", "feature/sso",
     "Possible AWS access key in commit",
     "CWE-798", None, "HIGH", 7.5,
     ".env.example", 12, None, None, None,
     "AKIA... prefix detected in committed .env.example file.",
     "Verify key is genuinely placeholder. If real, rotate immediately and force-push removal.",
     "FALSE_POSITIVE", 18),
    ("SNYK-2026-04-1045", "SNYK", "SCA",
     "KYC Service", "github.com/tssgrc/kyc-service", "main",
     "Pillow 10.0.0 — heap buffer overflow in libimagequant integration",
     "CWE-122", "CVE-2024-28219", "HIGH", 7.8,
     "requirements.txt", 8, "pillow", "10.0.0", "10.3.0",
     "Pillow versions prior to 10.3.0 contain a heap buffer overflow in libimagequant integration.",
     "Upgrade Pillow to 10.3.0+.",
     "OPEN", 1),
    ("SON-2026-04-3205", "SONARQUBE", "SAST",
     "Internal Admin Portal", "github.com/tssgrc/admin-portal", "main",
     "XSS — innerHTML used with user-controlled value",
     "CWE-79", None, "MEDIUM", 6.1,
     "src/components/UserProfile.tsx", 92, None, None, None,
     "user.bio rendered via innerHTML — no sanitization.",
     "Use textContent or React's default escaped rendering. If markdown wanted, use a sanitizer (DOMPurify).",
     "FIXED", 25),
    ("VER-2026-04-2025", "VERACODE", "SAST",
     "Core Banking — Payments Service", "github.com/tssgrc/core-banking-payments", "main",
     "Weak cryptography — SHA-1 used for password hashing",
     "CWE-327", None, "CRITICAL", 9.1,
     "src/main/java/com/tss/payments/legacy/LegacyAuth.java", 34,
     None, None, None,
     "Legacy auth path uses SHA-1 for password hash. Modern auth uses Argon2id correctly.",
     "Migrate legacy auth users to Argon2id on next login. Set rehash trigger. Track migration progress.",
     "ACCEPTED_RISK", 60),
]

for ext, scn, stype, app, repo, branch, title, cwe, cve, sev, cvss, file, line, comp, cv, fv, desc, advice, status, days_ago in APPSEC:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.appsec_finding
                       WHERE organisation_id = ? AND scanner = ? AND external_id = ? AND is_deleted = 0)
        INSERT INTO socops.appsec_finding
            (organisation_id, external_id, scanner, scan_type,
             application_name, repository_url, branch, finding_title,
             cwe, cve, severity, cvss_score, file_path, line_number,
             component_name, component_version, fixed_version,
             description, remediation_advice, status, discovered_at,
             fixed_at, sla_due_at, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -{days_ago}, SYSUTCDATETIME()),
                CASE WHEN ? = 'FIXED' THEN DATEADD(day, -{max(days_ago - 14, 0)}, SYSUTCDATETIME()) ELSE NULL END,
                DATEADD(day, {30 - days_ago}, SYSUTCDATETIME()),
                SYSUTCDATETIME())
        """,
        ORG_TSS, scn, ext,
        ORG_TSS, ext, scn, stype, app, repo, branch, title, cwe, cve, sev, cvss,
        file, line, comp, cv, fv, desc, advice, status,
        status,
        label=f"appsec[{scn}] {ext}",
    )

# ══════════════════════════════════════════════════════════════════════
# 15. DLP EVENTS
# ══════════════════════════════════════════════════════════════════════
section("socops.dlp_event — DLP policy hits")

DLP = [
    # (ext_id, src, policy, cat, classification, user, email, dept, channel, dest, file, bytes, severity, action, days_ago, reviewed, outcome)
    ("PV-2026-04-9001", "PURVIEW", "PDPL — National ID + IBAN combo (email egress)", "PDPL_SENSITIVE", "RESTRICTED",
     "noura.alqahtani", "noura.alqahtani@tssgrc.sa", "Risk Management",
     "EMAIL", "external-counsel@bsf-legal.sa", "customer-cases.xlsx", 142840,
     "HIGH", "BLOCKED", 2, 1, "POLICY_VIOLATION"),
    ("PV-2026-04-9002", "PURVIEW", "PCI — PAN pattern in document", "PCI", "RESTRICTED",
     "majid.alharbi", "majid.alharbi@tssgrc.sa", "Sales",
     "WEB_UPLOAD", "filebin.net", "client-list.xlsx", 88420,
     "CRITICAL", "BLOCKED", 6, 1, "POLICY_VIOLATION"),
    ("PV-2026-04-9003", "PURVIEW", "NDC — RESTRICTED label, USB egress", "NDC_RESTRICTED", "RESTRICTED",
     "fahad.alotaibi", "fahad.alotaibi@tssgrc.sa", "IT Infrastructure",
     "USB", "USB serial 0x4A7B-...0x9C12", "network-diagrams.vsdx", 4218800,
     "HIGH", "BLOCKED", 8, 1, "JUSTIFIED"),
    ("FP-2026-04-7704", "FORCEPOINT", "PII bulk pattern — webform exfil", "PII", "RESTRICTED",
     "hala.alanazi", "hala.alanazi@tssgrc.sa", "Customer Service",
     "WEB_UPLOAD", "pastebin.com", "(form data)", 12420,
     "MEDIUM", "BLOCKED", 12, 0, None),
    ("PV-2026-04-9008", "PURVIEW", "PDPL — bulk export from CRM (mass)", "PDPL_SENSITIVE", "RESTRICTED",
     "abdullah.alharbi", "abdullah.alharbi@tssgrc.sa", "Development",
     "API", "10.32.40.88 → external", "(API mass-export)", 4280000,
     "HIGH", "QUARANTINED", 18, 1, "FALSE_POSITIVE"),
    ("NS-2026-04-5520", "NETSKOPE", "Source-code repo exfil pattern", "IP", "TOP_SECRET",
     "khalid.almutairi", "khalid.almutairi@tssgrc.sa", "(former) Development",
     "CLOUD_STORAGE", "personal-gdrive.com", "core-banking-src.zip", 240000000,
     "CRITICAL", "BLOCKED", 32, 1, "POLICY_VIOLATION"),
    ("PV-2026-04-9012", "PURVIEW", "Customer data — chat egress", "PDPL_SENSITIVE", "RESTRICTED",
     "saad.alotaibi", "saad.alotaibi@tssgrc.sa", "Sales",
     "CHAT", "Microsoft Teams external chat", "(pasted text)", 4180,
     "MEDIUM", "NOTIFIED", 21, 1, "JUSTIFIED"),
    ("PV-2026-04-9015", "PURVIEW", "Print — PCI document, after-hours", "PCI", "RESTRICTED",
     "fawaz.alharbi", "fawaz.alharbi@tssgrc.sa", "CISO Office",
     "PRINT", "print-queue: HR-Floor4-MFD", "audit-evidence-PCI.pdf", 8840000,
     "HIGH", "NOTIFIED", 26, 1, "JUSTIFIED"),
    ("FP-2026-04-7720", "FORCEPOINT", "Mass-mailing — PDPL bulk to external", "PDPL_SENSITIVE", "RESTRICTED",
     "noura.alqahtani", "noura.alqahtani@tssgrc.sa", "Risk Management",
     "EMAIL", "(48 external recipients in 2 minutes)", "customer-survey-results.xlsx", 1840000,
     "HIGH", "BLOCKED", 40, 1, "POLICY_VIOLATION"),
    ("PV-2026-04-9020", "PURVIEW", "PII pattern (national IDs) — print", "PII", "RESTRICTED",
     "hala.alanazi", "hala.alanazi@tssgrc.sa", "Customer Service",
     "PRINT", "branch-MFD-001", "customer-statement-batch.pdf", 4200000,
     "MEDIUM", "ALLOWED_WITH_AUDIT", 48, 0, None),
    ("CL-2026-04-3041", "CLOUD_DLP", "GitHub secret-scanning — JWT in commit", "PDPL_SENSITIVE", "RESTRICTED",
     "yousef.alharbi", "yousef.alharbi@tssgrc.sa", "Development",
     "API", "github.com/tssgrc/admin-portal", "src/auth.test.ts", 2840,
     "HIGH", "NOTIFIED", 55, 1, "FALSE_POSITIVE"),
    ("PV-2026-04-9024", "PURVIEW", "Encrypted ZIP egress — pattern circumvention", "IP", "RESTRICTED",
     "majid.alharbi", "majid.alharbi@tssgrc.sa", "(former) Sales",
     "EMAIL", "external personal address", "data.zip", 280000000,
     "CRITICAL", "QUARANTINED", 70, 1, "POLICY_VIOLATION"),
]

for ext, src, policy, cat, cls, user, email, dept, channel, dest, fname, fsize, sev, action, days_ago, reviewed, outcome in DLP:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.dlp_event
                       WHERE organisation_id = ? AND source_system = ? AND external_id = ? AND is_deleted = 0)
        INSERT INTO socops.dlp_event
            (organisation_id, external_id, source_system, event_time, policy_name,
             policy_category, data_classification, username, user_email, department,
             channel, destination, file_name, file_size_bytes, match_count, severity,
             action_taken, reviewed, review_outcome, created_at)
        VALUES (?, ?, ?,
                DATEADD(day, -{days_ago}, SYSUTCDATETIME()),
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, SYSUTCDATETIME())
        """,
        ORG_TSS, src, ext,
        ORG_TSS, ext, src, policy, cat, cls, user, email, dept, channel, dest, fname, fsize,
        sev, action, reviewed, outcome,
        label=f"dlp[{src}] {ext}",
    )

# ══════════════════════════════════════════════════════════════════════
# 16. CYBER INSURANCE
# ══════════════════════════════════════════════════════════════════════
section("socops.cyber_insurance — policy + claims history")

run(
    """
    IF NOT EXISTS (SELECT 1 FROM socops.cyber_insurance
                   WHERE organisation_id = ? AND policy_number = ? AND is_deleted = 0)
    INSERT INTO socops.cyber_insurance
        (organisation_id, policy_number, carrier_name, broker_name, policy_type,
         coverage_areas, aggregate_limit_sar, per_event_limit_sar, retention_sar,
         effective_from, effective_to, renewal_due_at, status,
         claim_count, last_claim_at, last_claim_paid_sar, aggregate_claims_paid_sar,
         notes, created_at)
    VALUES (?, 'TAW-CY-2026-01218', 'Tawuniya Insurance', 'Marsh McLennan KSA',
            'STANDALONE_CYBER',
            'BUSINESS_INTERRUPTION,RANSOMWARE,EXTORTION,INCIDENT_RESPONSE,LEGAL_DEFENSE,REGULATORY_FINES,DATA_RECOVERY,FORENSICS,CRIME',
            50000000.00, 15000000.00, 250000.00,
            '2026-01-01', '2026-12-31', '2026-11-01',
            'ACTIVE',
            1, DATEADD(month, -8, SYSUTCDATETIME()), 480000.00, 480000.00,
            'Single claim 2025-Q4 — phishing-driven BEC. Paid out SAR 480k for IR + legal. Within retention 5x.',
            SYSUTCDATETIME())
    """,
    ORG_TSS, "TAW-CY-2026-01218",
    ORG_TSS,
    label="insurance: Tawuniya cyber",
)

# ══════════════════════════════════════════════════════════════════════
# 17. BRAND MONITORING ALERTS
# ══════════════════════════════════════════════════════════════════════
section("socops.brand_monitoring — leaked creds, fake domains, dark forum")

BRAND = [
    # (ext, src, atype, days_ago, sev, conf, asset, indicator, context, status)
    ("RF-2026-04-9201", "RECORDED_FUTURE", "LEAKED_CREDENTIAL", 2, "HIGH", "HIGH",
     "abdullah.alharbi@tssgrc.sa",
     "abdullah.alharbi@tssgrc.sa:Spring2024!",
     "Found on 'Exposed-MENA' dark forum dump dated 2026-04-30. Plaintext password observed.",
     "INVESTIGATING"),
    ("RF-2026-04-9202", "RECORDED_FUTURE", "LEAKED_CREDENTIAL", 2, "HIGH", "HIGH",
     "majid.alharbi@tssgrc.sa",
     "majid.alharbi@tssgrc.sa:[REDACTED-hash]",
     "Same dump as above. Hash format suggests Argon2id — protects against direct cracking.",
     "INVESTIGATING"),
    ("DS-2026-04-7711", "DIGITAL_SHADOWS", "FAKE_DOMAIN", 6, "HIGH", "MEDIUM",
     "tssgrc.sa",
     "tssgrc-secure-portal.com",
     "Lookalike domain registered 4 days ago via Namecheap. Resolves to 185.245.91.42. SSL cert via LetsEncrypt.",
     "TAKEDOWN_REQUESTED"),
    ("DS-2026-04-7712", "DIGITAL_SHADOWS", "FAKE_DOMAIN", 14, "MEDIUM", "HIGH",
     "tssgrc.sa",
     "tssbank-online-sa.net",
     "Punycode lookalike. Hosted on hosting provider known for abuse. Cloudflare in front — abuse report filed.",
     "TAKEDOWN_COMPLETE"),
    ("SR-2026-04-8801", "SOCRADAR", "DARK_FORUM_MENTION", 10, "MEDIUM", "HIGH",
     "Trusted Security Solutions",
     "Post: 'Anyone has backdoor on tssgrc.sa? Buyer offering 30 BTC'",
     "Post on RAMP forum, user 'crimson_sky'. Buyer-side post. No indication of actual breach.",
     "INVESTIGATING"),
    ("FP-2026-04-5520", "FLASHPOINT", "EXPOSED_CREDS_REPO", 18, "HIGH", "HIGH",
     "tssgrc.sa",
     "github.com/abc-dev/old-config (contains tssgrc.sa SMTP creds)",
     "Old developer's personal GitHub has 2022-vintage SMTP credentials. Repo is public.",
     "TAKEDOWN_COMPLETE"),
    ("RF-2026-04-9210", "RECORDED_FUTURE", "PHISHING_KIT", 22, "HIGH", "HIGH",
     "tssgrc.sa brand",
     "tssgrc-secure-portal.com (phishing kit deployed)",
     "Phishing kit harvesting username+password+OTP. Branded for TSS GRC. URL ends with /login.php.",
     "TAKEDOWN_REQUESTED"),
    ("KEL-2026-04-1102", "KELA", "LEAKED_CREDENTIAL", 26, "MEDIUM", "MEDIUM",
     "noura.alqahtani@tssgrc.sa",
     "noura.alqahtani@tssgrc.sa:[REDACTED]",
     "Found in 2023-vintage Combolist on Telegram channel 'creds-mena'. Old password.",
     "FALSE_POSITIVE"),
]

for ext, src, atype, days_ago, sev, conf, asset, indicator, context, status in BRAND:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.brand_monitoring
                       WHERE organisation_id = ? AND source_system = ? AND external_id = ? AND is_deleted = 0)
        INSERT INTO socops.brand_monitoring
            (organisation_id, external_id, source_system, alert_type, detected_at,
             severity, confidence, affected_asset, indicator_value, indicator_context,
             status, takedown_requested_at, takedown_completed_at, created_at)
        VALUES (?, ?, ?, ?,
                DATEADD(day, -{days_ago}, SYSUTCDATETIME()),
                ?, ?, ?, ?, ?, ?,
                CASE WHEN ? IN ('TAKEDOWN_REQUESTED','TAKEDOWN_COMPLETE') THEN DATEADD(day, -{max(days_ago - 2, 0)}, SYSUTCDATETIME()) ELSE NULL END,
                CASE WHEN ? = 'TAKEDOWN_COMPLETE' THEN DATEADD(day, -{max(days_ago - 5, 0)}, SYSUTCDATETIME()) ELSE NULL END,
                SYSUTCDATETIME())
        """,
        ORG_TSS, src, ext,
        ORG_TSS, ext, src, atype, sev, conf, asset, indicator, context, status,
        status, status,
        label=f"brand[{src}] {ext}",
    )

# ══════════════════════════════════════════════════════════════════════
# 18. FORENSICS ARTEFACTS
# ══════════════════════════════════════════════════════════════════════
section("socops.forensics_artefact — chain of custody")

FORENSICS = [
    # (code, case, type, te, ta, src_host, days_ago, handler, tool, method, size, md5, sha256, custodian, location, retention_years, legal_hold, notes)
    ("FOR-2026-001-MEM-DUMP", "IR-2026-04-001", "MEMORY_DUMP",
     "Memory dump — WS-FIN-042 (ransomware)", "إفراغ الذاكرة — جهاز WS-FIN-042",
     "WS-FIN-042", 2,
     "Eng. Saud Al-Dosari (Lead Forensics)", "Volatility 3 + winpmem", "LIVE",
     8589934592, "5d41402abc4b2a76b9719d911017c592",
     "9b8e21c5d4f7a3e2b1c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7",
     "Eng. Saud Al-Dosari", "TSS evidence locker R4B-12 (sealed bag #2026-001)",
     7, 0, "Captured under ransomware containment workflow. Hash verified at capture + ingest."),
    ("FOR-2026-001-DISK-IMG", "IR-2026-04-001", "DISK_IMAGE",
     "Disk image — WS-FIN-042 (ransomware)", "صورة قرص — جهاز WS-FIN-042",
     "WS-FIN-042", 2,
     "Eng. Saud Al-Dosari", "FTK Imager + write-blocker", "OFFLINE",
     512000000000, "ab2c41402d4b2a76b9719d911017c5e2",
     "1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2v3w4x5y6z7a8b9c0d1e2f",
     "Eng. Saud Al-Dosari", "TSS evidence locker R4B-12 (USB write-blocker bag #2026-002)",
     7, 1, "Legal hold — likely insurance claim + potential law-enforcement referral."),
    ("FOR-2026-002-PCAP", "IR-2026-04-002", "PACKET_CAPTURE",
     "PCAP — DNS exfil incident (WS-DEV-088)", "حزم شبكة — تسريب DNS",
     "WS-DEV-088", 5,
     "Eng. Hadeel Al-Mohammed", "Wireshark + tcpdump", "REMOTE",
     1418560000, "8d6e9c4f02ab5a76b9719d911017c612",
     "7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2v3w4x5y6z7a8b9c0d1e2f3g4h5i6j7k8l",
     "Eng. Hadeel Al-Mohammed", "TSS evidence locker R4B-12 (encrypted USB #2026-003)",
     5, 0, "Captured during DNS exfil investigation. 2 hours of full-packet capture."),
    ("FOR-2026-003-MOBILE", "IR-2026-04-003", "MOBILE_BACKUP",
     "iOS backup — terminated employee (BEC investigation)", "نسخة iOS — موظف منهي خدمته",
     "(corporate-issued iPhone)", 14,
     "Eng. Saud Al-Dosari + HR observer", "Cellebrite UFED 4PC", "OFFLINE",
     128000000000, "c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6",
     "9a8b7c6d5e4f3g2h1i0j9k8l7m6n5o4p3q2r1s0t9u8v7w6x5y4z3a2b1c0d9e8f",
     "Eng. Hadeel Al-Mohammed", "TSS evidence locker R4B-12 (sealed Faraday bag #2026-004)",
     7, 1, "Legal hold — BEC investigation, employment dispute pending."),
]

for code, case, atype, te, ta, src, days_ago, handler, tool, method, sz, md5, sha256, custodian, loc, retention_years, legal, notes in FORENSICS:
    run(
        f"""
        IF NOT EXISTS (SELECT 1 FROM socops.forensics_artefact
                       WHERE organisation_id = ? AND artefact_code = ? AND is_deleted = 0)
        INSERT INTO socops.forensics_artefact
            (organisation_id, artefact_code, case_code, artefact_type, title_en, title_ar,
             source_host, captured_at, captured_by_handler, capture_tool, capture_method,
             size_bytes, md5_hash, sha256_hash, integrity_verified_at, integrity_status,
             current_custodian, current_location, retention_until, legal_hold, notes,
             created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?,
                DATEADD(day, -{days_ago}, SYSUTCDATETIME()),
                ?, ?, ?, ?, ?, ?,
                DATEADD(day, -{days_ago - 1}, SYSUTCDATETIME()),
                'VERIFIED', ?, ?,
                DATEADD(year, {retention_years}, SYSUTCDATETIME()),
                ?, ?, SYSUTCDATETIME())
        """,
        ORG_TSS, code,
        ORG_TSS, code, case, atype, te, ta, src,
        handler, tool, method, sz, md5, sha256, custodian, loc, legal, notes,
        label=f"forensics {code}",
    )

# ══════════════════════════════════════════════════════════════════════
# 19. M&A DUE DILIGENCE
# ══════════════════════════════════════════════════════════════════════
section("socops.ma_due_diligence — cyber DD workflow")

# Probe schema first — table is V152 Tier-3, may not be bootstrapped on every env.
try:
    cur.execute("SELECT TOP 1 1 FROM socops.ma_due_diligence")
    has_ma = True
except Exception:
    has_ma = False

if has_ma:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM socops.ma_due_diligence
                       WHERE organisation_id = ? AND engagement_code = ? AND is_deleted = 0)
        INSERT INTO socops.ma_due_diligence
            (organisation_id, engagement_code, title_en, title_ar,
             target_entity_name, target_industry, target_country, deal_type,
             phase, deal_status, risk_areas_assessed,
             findings_total, findings_critical, findings_high,
             cyber_lead_name, legal_lead_name, finance_lead_name,
             loi_signed_at, fieldwork_start_at, created_at)
        VALUES (?, 'MA-2026-PIF-001',
                'PIF-style fintech acquisition — cyber DD',
                'الاستحواذ على شركة فنتك — العناية الواجبة السيبرانية',
                'Riyadh-based fintech start-up (target codename Aurora)',
                'Fintech / Lending', 'SA', 'ACQUISITION',
                'DUE_DILIGENCE', 'PROCEEDING',
                'IDENTITY,DATA_PROTECTION,APPLICATIONS,INFRASTRUCTURE,VENDORS,REGULATORY,INCIDENTS_HISTORY',
                14, 0, 4,
                'Eng. Fawaz Al-Harbi (CISO)', 'Mr. Yousef Al-Subaie (Corporate Legal)',
                'Ms. Amal Al-Shehri (CFO Office)',
                DATEADD(day, -42, SYSUTCDATETIME()),
                DATEADD(day, -28, SYSUTCDATETIME()),
                SYSUTCDATETIME())
        """,
        ORG_TSS, "MA-2026-PIF-001",
        ORG_TSS,
        label="M&A DD: PIF-style fintech acquisition",
    )
else:
    print("  · skipping ma_due_diligence — table not present in this env")

# ══════════════════════════════════════════════════════════════════════
# 20. License rows for all 3 tenants
# ══════════════════════════════════════════════════════════════════════
section("platform.organisation_licenses — SOCOPS entitlements")

for org_id, label in [(ORG_TSS, "TSS GRC"), (ORG_ARAMCO, "Aramco Trading"), (ORG_STC, "STC Bank")]:
    run(
        """
        IF NOT EXISTS (SELECT 1 FROM platform.organisation_licenses
                       WHERE organisation_id = ? AND module_code = 'SOCOPS')
        INSERT INTO platform.organisation_licenses
            (organisation_id, module_code, tier, enabled, valid_from, notes, created_at)
        VALUES (?, 'SOCOPS', 'ENTERPRISE', 1, SYSUTCDATETIME(),
                ?, SYSUTCDATETIME())
        """,
        org_id,
        org_id, f"SOC Operations module — auto-entitled for {label}",
        label=f"license[{org_id}] SOCOPS",
    )

# ══════════════════════════════════════════════════════════════════════
# Summary
# ══════════════════════════════════════════════════════════════════════
print(f"\n══════════════════════════════════════════════════════════════════════")
print(f"  socops seed complete")
print(f"  inserted: {inserted}")
print(f"  skipped (already present): {skipped}")
print(f"  errors:   {errors}")
print(f"══════════════════════════════════════════════════════════════════════\n")

cur.close()
conn.close()
sys.exit(0 if errors == 0 else 1)
