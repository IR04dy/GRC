# -*- coding: utf-8 -*-
"""Seed SOC operations incident-response playbooks for the default tenant.

Domain: socops. Schema: socops. Deepens the existing IR playbook library
(socops.ir_playbook + socops.ir_playbook_step) with six additional
Saudi-government-themed playbooks and their detailed step sequences.

The tenant (organisation_id = 1) is a Saudi general authority (هيئة عامة)
headed by a President (رئيس الهيئة). The new playbooks fill regulator-driven
gaps in the existing library (which already covers phishing, ransomware, DDoS,
BEC, insider, cloud, OT, personal-data breach and BCP activation):

    IR-NCA-REPORT-01     NCA cyber-incident notification & coordination (ECC 2-13-1 / Haseen)
    IR-SUPPLYCHAIN-01    Software supply-chain / third-party compromise
    IR-PORTAL-DEFACE-01  National e-service portal defacement / web attack (DGA service)
    IR-ACCT-TAKEOVER-01  Beneficiary / employee account takeover (Nafath / IAM)
    IR-MALWARE-OUTBREAK-01  Enterprise malware outbreak & containment
    IR-DATA-EXFIL-01     Confirmed data exfiltration of authority data (PDPL / NDMO)

Idempotency:
  * Playbooks are upserted by (organisation_id, playbook_code). A second run
    re-writes only the descriptive/target fields of the rows THIS script owns
    (it never clobbers the 9 pre-existing playbooks — those codes are not in C).
  * Steps are upserted by (organisation_id, playbook_id, step_order). A second
    run updates in place and inserts 0 new rows.

Cross-links: responsible_role values reference the authority's real org units
(CISO, SOC, Cyber Risk & Compliance, Legal Affairs, Privacy Office, Business
Continuity, Corporate Communication) and governance bodies (Cybersecurity
Supervisory Committee, Crisis Management Committee) by their natural names.

Safety: INSERT / idempotent-UPSERT ONLY. No DELETE / TRUNCATE / DROP / ALTER.
organisation_id = 1 only. Only the socops schema is touched.

    Usage:  python seed_gov_socops.py
"""
from __future__ import annotations

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


def step(order, phase, title_en, title_ar, instr_en, instr_ar, role, mins, critical):
    return dict(order=order, phase=phase, title_en=title_en, title_ar=title_ar,
                instr_en=instr_en, instr_ar=instr_ar, role=role, mins=mins,
                critical=critical)


# Six government-themed IR playbooks. Phases follow NIST 800-61 /
# NCA ECC incident-response lifecycle: PREPARATION, DETECTION, ANALYSIS,
# CONTAINMENT, ERADICATION, RECOVERY, POST_INCIDENT.
C: list[dict] = [
    dict(
        code="IR-NCA-REPORT-01",
        title_en="NCA Cyber-Incident Notification & Coordination",
        title_ar="الإبلاغ عن الحوادث السيبرانية للهيئة الوطنية للأمن السيبراني والتنسيق معها",
        category="REGULATORY_REPORTING",
        severity="HIGH",
        mttd=30, mttr=240,
        frameworks="NCA ECC 2-13-1,NCA Haseen,NCEMA",
        desc_en="Coordinates timely notification of qualifying cyber incidents to the National "
                "Cybersecurity Authority (NCA) via the Haseen platform, and manages the resulting "
                "information exchange, per ECC control 2-13-1.",
        desc_ar="ينظم الإبلاغ في الوقت المناسب عن الحوادث السيبرانية المؤهلة إلى الهيئة الوطنية "
                "للأمن السيبراني عبر منصة حصين، ويدير تبادل المعلومات الناتج وفق الضابط 2-13-1.",
        steps=[
            step(1, "PREPARATION",
                 "Verify Haseen credentials and reporting roster",
                 "التحقق من بيانات الدخول لمنصة حصين وقائمة المبلّغين",
                 "Confirm the authority's Haseen platform account, authorised reporters and the "
                 "NCA point-of-contact are current before any incident.",
                 "تأكيد سريان حساب الهيئة في منصة حصين والمبلّغين المخوّلين وجهة الاتصال في الهيئة الوطنية قبل وقوع أي حادث.",
                 "Cyber Risk & Compliance", 30, 0),
            step(2, "DETECTION",
                 "Classify incident severity against NCA criteria",
                 "تصنيف خطورة الحادث وفق معايير الهيئة الوطنية",
                 "Determine whether the incident meets the NCA's notification threshold "
                 "(impact on confidentiality, integrity, availability or national services).",
                 "تحديد ما إذا كان الحادث يستوفي حد الإبلاغ لدى الهيئة الوطنية (الأثر على السرية أو السلامة أو التوافر أو الخدمات الوطنية).",
                 "SOC Analyst (Tier 2)", 30, 1),
            step(3, "ANALYSIS",
                 "Assemble the notification dossier",
                 "إعداد ملف الإبلاغ",
                 "Collect IOCs, affected assets, timeline, initial impact assessment and "
                 "containment status into the NCA notification template.",
                 "جمع مؤشرات الاختراق والأصول المتأثرة والتسلسل الزمني وتقييم الأثر الأولي وحالة الاحتواء في نموذج الإبلاغ.",
                 "Cyber Risk & Compliance", 60, 1),
            step(4, "CONTAINMENT",
                 "Submit initial notification via Haseen",
                 "تقديم الإبلاغ الأولي عبر منصة حصين",
                 "Submit the initial incident notification to the NCA within the mandated window "
                 "and record the reference number.",
                 "تقديم الإبلاغ الأولي عن الحادث للهيئة الوطنية خلال المدة المقررة وتسجيل الرقم المرجعي.",
                 "CISO", 30, 1),
            step(5, "RECOVERY",
                 "Provide follow-up updates and final report",
                 "تقديم التحديثات اللاحقة والتقرير الختامي",
                 "Send progress updates as required and submit the final post-incident report to "
                 "the NCA, coordinating with the Crisis Management Committee.",
                 "إرسال تحديثات التقدم حسب الحاجة وتقديم التقرير الختامي للهيئة الوطنية بالتنسيق مع لجنة إدارة الأزمات.",
                 "Cyber Risk & Compliance", 60, 0),
            step(6, "POST_INCIDENT",
                 "Brief the Cybersecurity Supervisory Committee",
                 "إحاطة اللجنة الإشرافية للأمن السيبراني",
                 "Present the incident summary, regulator correspondence and lessons learned to "
                 "the Cybersecurity Supervisory Committee.",
                 "عرض ملخص الحادث والمراسلات التنظيمية والدروس المستفادة على اللجنة الإشرافية للأمن السيبراني.",
                 "CISO", 30, 0),
        ],
    ),
    dict(
        code="IR-SUPPLYCHAIN-01",
        title_en="Software Supply-Chain / Third-Party Compromise",
        title_ar="اختراق سلسلة إمداد البرمجيات أو طرف ثالث",
        category="SUPPLY_CHAIN",
        severity="HIGH",
        mttd=240, mttr=1440,
        frameworks="NCA ECC 4-1,NCA OSMC,ISO 27036",
        desc_en="Responds to a compromise originating from a software vendor, managed service "
                "provider or open-source dependency affecting authority systems.",
        desc_ar="يستجيب لاختراق ناشئ من مورّد برمجيات أو مزوّد خدمة مُدارة أو مكوّن مفتوح المصدر يؤثر على أنظمة الهيئة.",
        steps=[
            step(1, "DETECTION",
                 "Confirm the supply-chain indicator",
                 "تأكيد مؤشر اختراق سلسلة الإمداد",
                 "Validate the vendor advisory or threat-intel indicator against the asset and "
                 "SBOM inventory to confirm exposure.",
                 "التحقق من نشرة المورّد أو مؤشر التهديد مقابل سجل الأصول وقائمة مكوّنات البرمجيات لتأكيد التعرّض.",
                 "SOC Analyst (Tier 2)", 120, 1),
            step(2, "ANALYSIS",
                 "Map blast radius across dependent systems",
                 "تحديد نطاق الأثر عبر الأنظمة المعتمِدة",
                 "Identify every authority system, integration and data flow that consumes the "
                 "compromised component or vendor service.",
                 "تحديد كل نظام في الهيئة وتكامل وتدفق بيانات يعتمد على المكوّن أو الخدمة المخترقة.",
                 "Cyber Risk & Compliance", 180, 1),
            step(3, "CONTAINMENT",
                 "Isolate or disable the affected component",
                 "عزل أو تعطيل المكوّن المتأثر",
                 "Block the malicious update, revoke vendor access tokens and isolate the affected "
                 "service pending a clean version.",
                 "حظر التحديث الخبيث وإلغاء رموز وصول المورّد وعزل الخدمة المتأثرة لحين توفّر نسخة سليمة.",
                 "Security Operations Center", 120, 1),
            step(4, "ERADICATION",
                 "Rotate secrets and rebuild from trusted source",
                 "تدوير الأسرار وإعادة البناء من مصدر موثوق",
                 "Rotate all credentials, keys and tokens shared with the vendor and rebuild "
                 "affected systems from a verified clean baseline.",
                 "تدوير جميع بيانات الاعتماد والمفاتيح والرموز المشتركة مع المورّد وإعادة بناء الأنظمة المتأثرة من خط أساس سليم موثّق.",
                 "Information Technology", 360, 1),
            step(5, "RECOVERY",
                 "Restore service and heighten monitoring",
                 "استعادة الخدمة ورفع مستوى المراقبة",
                 "Return the service to production under enhanced monitoring and reassess the "
                 "vendor's third-party risk rating.",
                 "إعادة الخدمة للإنتاج تحت مراقبة معزّزة وإعادة تقييم تصنيف مخاطر الطرف الثالث للمورّد.",
                 "Security Operations Center", 240, 0),
            step(6, "POST_INCIDENT",
                 "Update third-party risk register and contract controls",
                 "تحديث سجل مخاطر الأطراف الثالثة وضوابط العقد",
                 "Record findings in the third-party risk register and require remediation/SLA "
                 "evidence from the vendor before re-onboarding.",
                 "تسجيل النتائج في سجل مخاطر الأطراف الثالثة ومطالبة المورّد بأدلة المعالجة واتفاقية مستوى الخدمة قبل إعادة الارتباط.",
                 "Cyber Risk & Compliance", 120, 0),
        ],
    ),
    dict(
        code="IR-PORTAL-DEFACE-01",
        title_en="National E-Service Portal Defacement / Web Attack",
        title_ar="تشويه بوابة الخدمات الإلكترونية الوطنية أو هجوم على الويب",
        category="WEB_ATTACK",
        severity="HIGH",
        mttd=20, mttr=240,
        frameworks="NCA ECC 2-3,DGA,OWASP",
        desc_en="Responds to defacement, injection or unauthorised content change on a public "
                "e-service portal the authority provides to beneficiaries (DGA digital service).",
        desc_ar="يستجيب لتشويه أو حقن أو تغيير غير مصرّح به للمحتوى في بوابة خدمات إلكترونية عامة تقدّمها الهيئة للمستفيدين (خدمة رقمية لهيئة الحكومة الرقمية).",
        steps=[
            step(1, "DETECTION",
                 "Confirm the unauthorised content change",
                 "تأكيد التغيير غير المصرّح به للمحتوى",
                 "Verify the defacement or anomalous content via monitoring alerts and a manual "
                 "review of the public portal.",
                 "التحقق من التشويه أو المحتوى الشاذ عبر تنبيهات المراقبة والمراجعة اليدوية للبوابة العامة.",
                 "SOC Analyst (Tier 1)", 20, 1),
            step(2, "CONTAINMENT",
                 "Take the portal to a maintenance holding page",
                 "تحويل البوابة إلى صفحة صيانة مؤقتة",
                 "Switch the public portal to a controlled maintenance page to stop further "
                 "exposure of altered content to beneficiaries.",
                 "تحويل البوابة العامة إلى صفحة صيانة محكومة لإيقاف استمرار عرض المحتوى المعدّل للمستفيدين.",
                 "Information Technology", 20, 1),
            step(3, "ANALYSIS",
                 "Identify the entry vector",
                 "تحديد منفذ الاختراق",
                 "Examine web/WAF logs, CMS audit trail and recent deployments to find the "
                 "exploited vulnerability or stolen credential.",
                 "فحص سجلات الويب وجدار حماية التطبيقات وسجل تدقيق نظام إدارة المحتوى والنشرات الأخيرة لتحديد الثغرة المستغلّة أو بيانات الاعتماد المسروقة.",
                 "SOC Analyst (Tier 2)", 90, 1),
            step(4, "ERADICATION",
                 "Patch the vector and restore clean content",
                 "معالجة المنفذ واستعادة المحتوى السليم",
                 "Remediate the vulnerability, rotate affected credentials and restore portal "
                 "content from a verified clean backup.",
                 "معالجة الثغرة وتدوير بيانات الاعتماد المتأثرة واستعادة محتوى البوابة من نسخة احتياطية سليمة موثّقة.",
                 "Information Technology", 120, 1),
            step(5, "RECOVERY",
                 "Re-publish portal and notify communications",
                 "إعادة نشر البوابة وإبلاغ الاتصال المؤسسي",
                 "Bring the portal back online under enhanced WAF rules and coordinate any public "
                 "statement with Corporate Communication.",
                 "إعادة البوابة للعمل تحت قواعد معزّزة لجدار حماية التطبيقات وتنسيق أي بيان عام مع الاتصال المؤسسي.",
                 "Corporate Communication", 60, 0),
            step(6, "POST_INCIDENT",
                 "Remediate root cause and update secure-SDLC",
                 "معالجة السبب الجذري وتحديث دورة التطوير الآمن",
                 "Log the root cause, add a regression test and feed the finding into the secure "
                 "development lifecycle for the portal.",
                 "توثيق السبب الجذري وإضافة اختبار انحدار وإدراج النتيجة في دورة التطوير الآمن للبوابة.",
                 "Cyber Risk & Compliance", 60, 0),
        ],
    ),
    dict(
        code="IR-ACCT-TAKEOVER-01",
        title_en="Beneficiary / Employee Account Takeover",
        title_ar="الاستيلاء على حساب مستفيد أو موظف",
        category="ACCOUNT_TAKEOVER",
        severity="MEDIUM",
        mttd=30, mttr=180,
        frameworks="NCA ECC 2-2,Nafath,PDPL",
        desc_en="Responds to unauthorised access to a beneficiary or employee account, including "
                "compromised Nafath/SSO sessions and identity-credential abuse.",
        desc_ar="يستجيب للوصول غير المصرّح به لحساب مستفيد أو موظف، بما في ذلك اختراق جلسات نفاذ أو الدخول الموحّد وإساءة استخدام بيانات الهوية.",
        steps=[
            step(1, "DETECTION",
                 "Validate the suspicious authentication",
                 "التحقق من المصادقة المشبوهة",
                 "Confirm the anomalous sign-in (impossible travel, MFA fatigue, new device) "
                 "from IAM and SSO logs.",
                 "تأكيد تسجيل الدخول الشاذ (سفر مستحيل أو إنهاك المصادقة الثنائية أو جهاز جديد) من سجلات إدارة الهوية والدخول الموحّد.",
                 "SOC Analyst (Tier 1)", 30, 1),
            step(2, "CONTAINMENT",
                 "Suspend the account and revoke sessions",
                 "تعليق الحساب وإلغاء الجلسات",
                 "Disable the affected account, revoke all active sessions and tokens, and force a "
                 "credential reset.",
                 "تعطيل الحساب المتأثر وإلغاء جميع الجلسات والرموز النشطة وفرض إعادة تعيين بيانات الاعتماد.",
                 "Security Operations Center", 20, 1),
            step(3, "ANALYSIS",
                 "Assess actions taken under the account",
                 "تقييم الإجراءات المتخذة عبر الحساب",
                 "Review the account's activity during the compromise window for data access, "
                 "transactions or privilege changes.",
                 "مراجعة نشاط الحساب خلال فترة الاختراق بحثاً عن وصول للبيانات أو معاملات أو تغييرات في الصلاحيات.",
                 "SOC Analyst (Tier 2)", 60, 1),
            step(4, "ERADICATION",
                 "Reverse malicious changes and harden MFA",
                 "عكس التغييرات الخبيثة وتعزيز المصادقة الثنائية",
                 "Revert unauthorised changes, remove attacker-added recovery methods and enforce "
                 "phishing-resistant MFA for the account.",
                 "التراجع عن التغييرات غير المصرّح بها وإزالة وسائل الاسترداد التي أضافها المهاجم وفرض مصادقة ثنائية مقاومة للتصيّد للحساب.",
                 "Information Technology", 60, 0),
            step(5, "RECOVERY",
                 "Restore access and notify the account holder",
                 "استعادة الوصول وإبلاغ صاحب الحساب",
                 "Reinstate verified access and notify the beneficiary/employee; involve the "
                 "Privacy Office if personal data was exposed.",
                 "إعادة الوصول الموثّق وإبلاغ المستفيد أو الموظف؛ وإشراك مكتب الخصوصية إذا انكشفت بيانات شخصية.",
                 "Privacy Office", 30, 0),
            step(6, "POST_INCIDENT",
                 "Tune detection and review IAM controls",
                 "ضبط الاكتشاف ومراجعة ضوابط إدارة الهوية",
                 "Update detection rules for the abused vector and review conditional-access / IAM "
                 "policies to prevent recurrence.",
                 "تحديث قواعد الاكتشاف للمنفذ المُساء استخدامه ومراجعة سياسات الوصول المشروط وإدارة الهوية لمنع التكرار.",
                 "Cyber Risk & Compliance", 45, 0),
        ],
    ),
    dict(
        code="IR-MALWARE-OUTBREAK-01",
        title_en="Enterprise Malware Outbreak & Containment",
        title_ar="تفشّي البرمجيات الخبيثة على مستوى المؤسسة واحتواؤه",
        category="MALWARE",
        severity="HIGH",
        mttd=60, mttr=720,
        frameworks="NCA ECC 2-3,NIST 800-61,MITRE ATT&CK",
        desc_en="Responds to a spreading malware infection across multiple endpoints or servers "
                "that is not yet ransomware, to contain lateral movement.",
        desc_ar="يستجيب لعدوى برمجيات خبيثة تنتشر عبر عدة أجهزة طرفية أو خوادم ولم تتحوّل بعد إلى فدية، لاحتواء الحركة الجانبية.",
        steps=[
            step(1, "DETECTION",
                 "Confirm and scope the infection",
                 "تأكيد العدوى وتحديد نطاقها",
                 "Correlate EDR alerts to confirm the malware family and enumerate every infected "
                 "host across the estate.",
                 "ربط تنبيهات اكتشاف نقاط النهاية لتأكيد عائلة البرمجية الخبيثة وحصر كل جهاز مصاب في البيئة.",
                 "SOC Analyst (Tier 2)", 60, 1),
            step(2, "CONTAINMENT",
                 "Network-isolate infected hosts",
                 "عزل الأجهزة المصابة عن الشبكة",
                 "Quarantine infected endpoints via EDR, block C2 domains/IPs and segment affected "
                 "network zones to halt spread.",
                 "حجر الأجهزة المصابة عبر أداة اكتشاف نقاط النهاية وحظر نطاقات وعناوين القيادة والتحكم وعزل مناطق الشبكة المتأثرة لوقف الانتشار.",
                 "Security Operations Center", 60, 1),
            step(3, "ANALYSIS",
                 "Identify patient zero and persistence",
                 "تحديد المصدر الأول وآليات البقاء",
                 "Trace the initial infection vector and locate all persistence mechanisms and "
                 "lateral-movement footholds.",
                 "تتبّع منفذ العدوى الأولي وتحديد جميع آليات البقاء ونقاط الارتكاز للحركة الجانبية.",
                 "Forensics Analyst", 240, 1),
            step(4, "ERADICATION",
                 "Remove malware and close the vector",
                 "إزالة البرمجية الخبيثة وإغلاق المنفذ",
                 "Clean or rebuild infected hosts, remove persistence, and patch the exploited "
                 "vulnerability or misconfiguration.",
                 "تنظيف أو إعادة بناء الأجهزة المصابة وإزالة آليات البقاء ومعالجة الثغرة أو الإعداد الخاطئ المستغلّ.",
                 "Information Technology", 360, 1),
            step(5, "RECOVERY",
                 "Reconnect hosts under monitoring",
                 "إعادة توصيل الأجهزة تحت المراقبة",
                 "Return cleaned hosts to the network in waves under heightened EDR monitoring and "
                 "verify no reinfection.",
                 "إعادة الأجهزة المنظّفة للشبكة على دفعات تحت مراقبة معزّزة والتأكد من عدم تكرار العدوى.",
                 "Security Operations Center", 240, 0),
            step(6, "POST_INCIDENT",
                 "Deploy detections and harden the estate",
                 "نشر قواعد الاكتشاف وتحصين البيئة",
                 "Roll out IOCs/YARA rules estate-wide, close the exploited gap fleet-wide and "
                 "report metrics to the SOC lead.",
                 "نشر مؤشرات الاختراق وقواعد الاكتشاف على مستوى البيئة وإغلاق الثغرة المستغلّة على جميع الأجهزة ورفع المؤشرات لقائد مركز العمليات.",
                 "Cyber Risk & Compliance", 120, 0),
        ],
    ),
    dict(
        code="IR-DATA-EXFIL-01",
        title_en="Confirmed Data Exfiltration of Authority Data",
        title_ar="تسريب مؤكد لبيانات الهيئة",
        category="DATA_EXFILTRATION",
        severity="CRITICAL",
        mttd=60, mttr=480,
        frameworks="NCA ECC 2-7,PDPL,NDMO",
        desc_en="Responds to confirmed unauthorised transfer of authority data out of the "
                "environment, coordinating containment, legal and regulatory obligations.",
        desc_ar="يستجيب للنقل غير المصرّح به والمؤكد لبيانات الهيئة خارج البيئة، مع تنسيق الاحتواء والالتزامات القانونية والتنظيمية.",
        steps=[
            step(1, "DETECTION",
                 "Confirm the exfiltration event",
                 "تأكيد حدث التسريب",
                 "Validate DLP/egress alerts to confirm data left the environment and identify the "
                 "channel (upload, email, removable media).",
                 "التحقق من تنبيهات منع تسريب البيانات لتأكيد خروج البيانات من البيئة وتحديد القناة (رفع أو بريد أو وسائط قابلة للإزالة).",
                 "SOC Analyst (Tier 2)", 60, 1),
            step(2, "CONTAINMENT",
                 "Cut the exfiltration channel",
                 "قطع قناة التسريب",
                 "Block the destination, suspend the compromised account/host and stop any "
                 "ongoing transfer immediately.",
                 "حظر الوجهة وتعليق الحساب أو الجهاز المخترق وإيقاف أي عملية نقل جارية فوراً.",
                 "Security Operations Center", 30, 1),
            step(3, "ANALYSIS",
                 "Determine scope, classification and data subjects",
                 "تحديد النطاق والتصنيف وأصحاب البيانات",
                 "Identify exactly what data was taken, its NDMO classification and whether it "
                 "includes personal data subject to PDPL.",
                 "تحديد البيانات المسرّبة بدقة وتصنيفها وفق مكتب إدارة البيانات الوطنية وما إذا كانت تشمل بيانات شخصية خاضعة لنظام حماية البيانات.",
                 "Privacy Office", 180, 1),
            step(4, "CONTAINMENT",
                 "Trigger regulatory and legal obligations",
                 "تفعيل الالتزامات التنظيمية والقانونية",
                 "Engage Legal Affairs and the Privacy Office to assess SDAIA/PDPL breach "
                 "notification duties and initiate the NCA report.",
                 "إشراك الشؤون القانونية ومكتب الخصوصية لتقييم واجبات الإبلاغ عن الانتهاك وفق سدايا والنظام وبدء الإبلاغ للهيئة الوطنية.",
                 "Legal Affairs", 120, 1),
            step(5, "RECOVERY",
                 "Restore controls and notify affected parties",
                 "استعادة الضوابط وإبلاغ المتأثرين",
                 "Harden the exploited control, and where required notify data subjects and "
                 "regulators within the mandated timeframes.",
                 "تحصين الضابط المستغلّ، وعند اللزوم إبلاغ أصحاب البيانات والجهات التنظيمية خلال المدد المقررة.",
                 "Privacy Office", 120, 0),
            step(6, "POST_INCIDENT",
                 "Escalate to Crisis Management Committee and remediate",
                 "التصعيد للجنة إدارة الأزمات والمعالجة",
                 "Brief the Crisis Management Committee, close the data-protection gap and update "
                 "the data classification and DLP policy.",
                 "إحاطة لجنة إدارة الأزمات وإغلاق ثغرة حماية البيانات وتحديث سياسة تصنيف البيانات ومنع التسريب.",
                 "Cyber Risk & Compliance", 90, 0),
        ],
    ),
]


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    pb_inserted = pb_updated = 0
    st_inserted = st_updated = 0

    for pb in C:
        cur.execute(
            "SELECT id FROM socops.ir_playbook WHERE organisation_id=? AND playbook_code=?",
            ORG, pb["code"],
        )
        row = cur.fetchone()
        if row:
            pbid = row[0]
            cur.execute(
                """UPDATE socops.ir_playbook
                      SET title_en=?, title_ar=?, description_en=?, description_ar=?,
                          category=?, severity_trigger=?, target_mttd_minutes=?,
                          target_mttr_minutes=?, aligned_frameworks=?,
                          status='ACTIVE', updated_at=SYSUTCDATETIME(), updated_by=1
                    WHERE id=?""",
                pb["title_en"], pb["title_ar"], pb["desc_en"], pb["desc_ar"],
                pb["category"], pb["severity"], pb["mttd"], pb["mttr"],
                pb["frameworks"], pbid,
            )
            pb_updated += 1
            print(f"  ~ {pb['code']:<24} {pb['title_en']}")
        else:
            cur.execute(
                """INSERT INTO socops.ir_playbook
                       (organisation_id, playbook_code, title_en, title_ar,
                        description_en, description_ar, category, severity_trigger,
                        target_mttd_minutes, target_mttr_minutes, aligned_frameworks,
                        status, version, invocation_count, is_deleted, created_by)
                   OUTPUT INSERTED.id
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, 0, 0, 1)""",
                ORG, pb["code"], pb["title_en"], pb["title_ar"],
                pb["desc_en"], pb["desc_ar"], pb["category"], pb["severity"],
                pb["mttd"], pb["mttr"], pb["frameworks"],
            )
            pbid = cur.fetchone()[0]
            pb_inserted += 1
            print(f"  + {pb['code']:<24} {pb['title_en']}")

        # Steps — upsert by (organisation_id, playbook_id, step_order)
        for s in pb["steps"]:
            cur.execute(
                "SELECT id FROM socops.ir_playbook_step "
                "WHERE organisation_id=? AND playbook_id=? AND step_order=?",
                ORG, pbid, s["order"],
            )
            srow = cur.fetchone()
            if srow:
                cur.execute(
                    """UPDATE socops.ir_playbook_step
                          SET phase=?, title_en=?, title_ar=?, instructions_en=?,
                              instructions_ar=?, responsible_role=?, estimated_minutes=?,
                              is_critical=?, updated_at=SYSUTCDATETIME(), updated_by=1
                        WHERE id=?""",
                    s["phase"], s["title_en"], s["title_ar"], s["instr_en"],
                    s["instr_ar"], s["role"], s["mins"], s["critical"], srow[0],
                )
                st_updated += 1
            else:
                cur.execute(
                    """INSERT INTO socops.ir_playbook_step
                           (organisation_id, playbook_id, step_order, phase,
                            title_en, title_ar, instructions_en, instructions_ar,
                            responsible_role, estimated_minutes, is_critical,
                            is_deleted, created_by)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1)""",
                    ORG, pbid, s["order"], s["phase"], s["title_en"], s["title_ar"],
                    s["instr_en"], s["instr_ar"], s["role"], s["mins"], s["critical"],
                )
                st_inserted += 1

    print(f"\nPlaybooks: inserted={pb_inserted}, updated={pb_updated}")
    print(f"Steps:     inserted={st_inserted}, updated={st_updated}")
    total_inserted = pb_inserted + st_inserted
    print(f"Total rows inserted this run: {total_inserted}")

    # ── Verification ─────────────────────────────────────────────────────────
    print("\nSOC IR playbooks for org 1 (seeded by this script):\n")
    codes = ", ".join(f"N'{pb['code']}'" for pb in C)
    cur.execute(
        f"""SELECT p.playbook_code, p.title_en, p.severity_trigger, p.category,
                   (SELECT COUNT(*) FROM socops.ir_playbook_step s
                     WHERE s.playbook_id=p.id AND s.is_deleted=0) AS steps
              FROM socops.ir_playbook p
             WHERE p.organisation_id=? AND p.is_deleted=0
               AND p.playbook_code IN ({codes})
             ORDER BY p.playbook_code""",
        ORG,
    )
    for code, title, sev, cat, steps in cur.fetchall():
        print(f"  {code:<24} [{sev:<8}] {title}  ({steps} steps)")

    cur.execute(
        "SELECT COUNT(*) FROM socops.ir_playbook WHERE organisation_id=? AND is_deleted=0",
        ORG,
    )
    pb_total = cur.fetchone()[0]
    cur.execute(
        "SELECT COUNT(*) FROM socops.ir_playbook_step WHERE organisation_id=? AND is_deleted=0",
        ORG,
    )
    st_total = cur.fetchone()[0]
    print(f"\nLibrary totals for org {ORG}: {pb_total} playbooks / {st_total} steps")
    conn.close()


if __name__ == "__main__":
    main()
