import sys, pyodbc
sys.stdout.reconfigure(encoding='utf-8')
CONN = "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;"
conn = pyodbc.connect(CONN); conn.autocommit = False
cur = conn.cursor()

def framework(code, name_en, name_ar, version, issuer, desc_en):
    cur.execute("SELECT id FROM evidence.frameworks WHERE code=?", code)
    r = cur.fetchone()
    if r:
        fid = int(r[0])
        cur.execute("UPDATE evidence.frameworks SET name_en=?, name_ar=?, version=?, issuer=?, description_en=?, is_active=1 WHERE id=?",
                    name_en, name_ar, version, issuer, desc_en, fid)
    else:
        cur.execute("INSERT INTO evidence.frameworks (code,name_en,name_ar,version,issuer,description_en,is_active) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,1)",
                    code, name_en, name_ar, version, issuer, desc_en)
        fid = int(cur.fetchone()[0])
    cur.execute("UPDATE evidence.sub_controls SET is_active=0 WHERE control_id IN (SELECT id FROM evidence.controls WHERE framework_id=?)", fid)
    cur.execute("UPDATE evidence.controls SET is_active=0 WHERE framework_id=?", fid)
    return fid

def control(fid, code, name_en, name_ar, desc_en=None):
    cur.execute("SELECT id FROM evidence.controls WHERE framework_id=? AND control_code=?", fid, code)
    r = cur.fetchone()
    if r:
        cid = int(r[0]); cur.execute("UPDATE evidence.controls SET name_en=?, name_ar=?, description_en=?, is_active=1 WHERE id=?", name_en, name_ar, desc_en, cid)
    else:
        cur.execute("INSERT INTO evidence.controls (framework_id,control_code,name_en,name_ar,description_en,is_active) OUTPUT INSERTED.id VALUES (?,?,?,?,?,1)", fid, code, name_en, name_ar, desc_en)
        cid = int(cur.fetchone()[0])
    return cid

def sub(cid, code, name_en, name_ar, guidance, desc_en=None):
    cur.execute("SELECT id FROM evidence.sub_controls WHERE control_id=? AND sub_control_code=?", cid, code)
    r = cur.fetchone()
    if r:
        sid = int(r[0]); cur.execute("UPDATE evidence.sub_controls SET name_en=?, name_ar=?, description_en=?, evidence_guidance=?, is_active=1 WHERE id=?", name_en, name_ar, desc_en, guidance, sid)
    else:
        cur.execute("INSERT INTO evidence.sub_controls (control_id,sub_control_code,name_en,name_ar,description_en,evidence_guidance,is_active) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,1)", cid, code, name_en, name_ar, desc_en, guidance)
        sid = int(cur.fetchone()[0])
    return sid

# =============================================================================
# FRAMEWORK A: OTCC-1:2022
# Operational Technology Cybersecurity Controls
# =============================================================================

fid_ot = framework(
    "OTCC-1:2022",
    "Operational Technology Cybersecurity Controls",
    "ضوابط الأمن السيبراني للتقنيات التشغيلية",
    "2022",
    "NCA",
    "NCA framework establishing mandatory cybersecurity controls for OT/ICS/SCADA environments across critical national infrastructure sectors in Saudi Arabia, covering governance, defence-in-depth, resilience, and third-party supply chain. Applicable to entities operating industrial control systems, distributed control systems, and safety instrumented systems. — practitioner SME mapping; structure aligned to the published framework, control text paraphrased (not verbatim)."
)

ot_ctrl_count = 0
ot_sub_count  = 0

# ── Domain 1: OT Governance & Policy ─────────────────────────────────────────
c = control(fid_ot, "OTCC-1-1", "OT Cybersecurity Governance & Policy",
            "حوكمة وسياسات الأمن السيبراني للتقنيات التشغيلية",
            "Establish and maintain board-approved OT cybersecurity policy, ownership, roles, and programme oversight.")
ot_ctrl_count += 1

sub(c, "1-1-1",
    "OT Cybersecurity Policy Issuance",
    "إصدار سياسة الأمن السيبراني للتقنيات التشغيلية",
    "Board-approved OT cybersecurity policy document (dated, signed); policy distribution records to OT staff; annual review evidence",
    "A formal, board-approved policy specific to OT/ICS environments must be documented and communicated to all relevant personnel.")
ot_sub_count += 1

sub(c, "1-1-2",
    "OT Cybersecurity Roles & Responsibilities",
    "تحديد الأدوار والمسؤوليات السيبرانية للتقنيات التشغيلية",
    "RACI matrix for OT security roles; job descriptions for OT security lead and OT SOC staff; delegation-of-authority records",
    "Designated OT cybersecurity ownership with clear accountability for ICS/SCADA environments, separate from IT security where applicable.")
ot_sub_count += 1

sub(c, "1-1-3",
    "OT Cybersecurity Risk Programme",
    "برنامج إدارة مخاطر الأمن السيبراني للتقنيات التشغيلية",
    "Annual OT risk assessment report; risk register with OT-specific entries; risk acceptance sign-offs by plant/asset owner",
    "A formal OT-specific risk assessment process must be maintained, feeding identified risks into the enterprise GRC risk register with OT risk context.")
ot_sub_count += 1

sub(c, "1-1-4",
    "OT Cybersecurity Compliance & Audit",
    "الامتثال والتدقيق السيبراني للتقنيات التشغيلية",
    "Internal OT audit schedule; OT audit findings report; evidence of corrective action closure; NCA self-assessment submission",
    "Periodic internal and independent audits of OT cybersecurity controls, with findings formally tracked and remediated.")
ot_sub_count += 1

# ── Domain 2: OT Asset Inventory ─────────────────────────────────────────────
c = control(fid_ot, "OTCC-1-2", "OT Asset Inventory & Classification",
            "جرد وتصنيف أصول التقنيات التشغيلية",
            "Maintain a complete, current inventory of all OT hardware and software assets with criticality classification.")
ot_ctrl_count += 1

sub(c, "1-2-1",
    "OT Hardware Asset Register",
    "سجل أصول العتاد التشغيلي",
    "OT asset inventory database/spreadsheet with make/model/firmware/location/criticality; evidence of quarterly reconciliation; scan reports from passive OT discovery tool (e.g., Claroty, Dragos, Nozomi)",
    "All PLCs, RTUs, DCS controllers, HMIs, engineering workstations, historians, and network devices in the OT environment must be catalogued.")
ot_sub_count += 1

sub(c, "1-2-2",
    "OT Software & Firmware Inventory",
    "جرد البرمجيات والبرامج الثابتة التشغيلية",
    "Software bill of materials (SBOM) for OT systems; SCADA/HMI version register; firmware version tracking spreadsheet; change records for updates",
    "An inventory of all software, operating systems, and firmware versions running on OT assets must be maintained and reviewed quarterly.")
ot_sub_count += 1

sub(c, "1-2-3",
    "OT Asset Criticality Classification",
    "تصنيف مستوى أهمية أصول التقنيات التشغيلية",
    "Asset criticality classification matrix (High/Medium/Low based on safety/operational impact); business impact analysis for top-tier OT assets",
    "Each OT asset must be assigned a criticality classification that drives prioritisation of security controls, patching, and monitoring.")
ot_sub_count += 1

# ── Domain 3: OT Network Segmentation ────────────────────────────────────────
c = control(fid_ot, "OTCC-1-3", "OT Network Segmentation & Architecture",
            "تجزئة شبكات التقنيات التشغيلية وهندستها المعمارية",
            "Implement network zones and conduits based on the Purdue reference model or equivalent, with enforced separation between OT and IT networks.")
ot_ctrl_count += 1

sub(c, "1-3-1",
    "Purdue-Based Zone Architecture",
    "هندسة المناطق وفق نموذج بوردو",
    "Network architecture diagram showing Purdue Levels 0–4; zone-and-conduit diagram; firewall rule-set for each conduit; approved data-flow matrix",
    "OT networks must be segmented into defined zones (field, control, supervisory, enterprise) with enforced conduits and no direct Level 0–2 to corporate IT connectivity.")
ot_sub_count += 1

sub(c, "1-3-2",
    "OT-IT Demilitarised Zone (OT-DMZ)",
    "المنطقة المنزوعة السلاح بين شبكتَي التقنيات التشغيلية وتقنية المعلومات",
    "OT-DMZ network diagram; firewall configuration screenshots; allowed services list (data diode, historian, jump server); penetration test evidence for DMZ boundaries",
    "An OT-DMZ must be deployed between the enterprise IT network and the control-system network, with only explicitly approved data flows permitted.")
ot_sub_count += 1

sub(c, "1-3-3",
    "Wireless & Serial Communication Controls",
    "ضوابط الاتصالات اللاسلكية والتسلسلية",
    "Wireless security policy for OT; Wi-Fi site survey showing segmentation; encryption config for permitted wireless (WPA3 or equivalent); serial-link inventory and justification",
    "Wireless access within OT zones must be risk-justified, encrypted, and monitored; legacy serial links must be inventoried and isolated.")
ot_sub_count += 1

sub(c, "1-3-4",
    "Remote Dial-Up & Legacy Protocol Isolation",
    "عزل الاتصالات القديمة والبروتوكولات الموروثة",
    "Legacy modem/dial-up inventory; decommission plan or compensating-control evidence; Modbus/DNP3/Profibus risk assessment; protocol gateway configuration",
    "Legacy remote access methods (dial-up modems) and insecure industrial protocols must be inventoried, risk-assessed, and either decommissioned or compensated.")
ot_sub_count += 1

# ── Domain 4: Secure Remote Access to OT ─────────────────────────────────────
c = control(fid_ot, "OTCC-1-4", "Secure Remote Access to OT Systems",
            "الوصول الآمن عن بُعد إلى أنظمة التقنيات التشغيلية",
            "Control and monitor all remote access to OT environments via hardened jump hosts, MFA, and vendor-specific approval workflows.")
ot_ctrl_count += 1

sub(c, "1-4-1",
    "Jump Host / Bastion Server for OT",
    "خادم الوصول الآمن (Jump Host) لبيئات التقنيات التشغيلية",
    "Jump-host build baseline; CIS-hardened OS configuration; MFA enrolment records for all jump-host users; session recording configuration; access log samples",
    "All interactive access to OT systems must traverse a hardened, MFA-protected jump host located in the OT-DMZ; direct connections to OT assets are prohibited.")
ot_sub_count += 1

sub(c, "1-4-2",
    "Vendor & Third-Party Remote Access Approval",
    "اعتماد وصول الموردين والأطراف الثالثة عن بُعد",
    "Vendor remote-access request and approval forms; time-limited session tokens; VPN account provisioning/deprovisioning log; signed vendor AUP/NDA",
    "Vendor remote access must follow a formal request-approval-revoke workflow with time-limited credentials; each session must be logged and supervised.")
ot_sub_count += 1

sub(c, "1-4-3",
    "OT Remote-Access Session Monitoring & Recording",
    "مراقبة وتسجيل جلسات الوصول عن بُعد لأنظمة التقنيات التشغيلية",
    "PAM/session-recording tool configuration; sample recorded session exports; alert configuration for anomalous commands; retention policy (minimum 12 months)",
    "All privileged and vendor sessions into OT systems must be recorded in full, with automated alerting for anomalous command sequences.")
ot_sub_count += 1

# ── Domain 5: OT Identity & Access Management ────────────────────────────────
c = control(fid_ot, "OTCC-1-5", "OT Identity & Access Management",
            "إدارة الهويات وصلاحيات الوصول في بيئات التقنيات التشغيلية",
            "Manage OT user accounts with least-privilege, unique credentials, and regular access review, separate from the IT Active Directory where appropriate.")
ot_ctrl_count += 1

sub(c, "1-5-1",
    "OT Account Inventory & Least-Privilege",
    "جرد حسابات التقنيات التشغيلية وتطبيق مبدأ الحد الأدنى من الصلاحيات",
    "OT account list export; role-based access matrix for SCADA/HMI operators vs engineers vs admins; access review sign-off records (semi-annual)",
    "All OT accounts must be inventoried; shared/generic accounts eliminated; access aligned to job function with semi-annual privileged-account review.")
ot_sub_count += 1

sub(c, "1-5-2",
    "OT Default & Hardcoded Credential Elimination",
    "إزالة بيانات الاعتماد الافتراضية والمُضمَّنة في أنظمة التقنيات التشغيلية",
    "Vendor default credential checklist (pre and post change); credential-scan tool output; compensating controls where firmware prevents change; change-management ticket",
    "All default vendor credentials on PLCs, RTUs, HMIs, and network devices must be changed at commissioning; hardcoded credentials must be risk-accepted or mitigated.")
ot_sub_count += 1

sub(c, "1-5-3",
    "OT Privileged Account Management",
    "إدارة الحسابات المميزة في بيئات التقنيات التشغيلية",
    "PAM solution coverage report for OT privileged accounts; password vault usage evidence; break-glass procedure documentation; emergency account audit log",
    "Privileged OT accounts (engineering, admin) must be managed in a PAM vault with approval workflows; emergency break-glass accounts must be tightly controlled.")
ot_sub_count += 1

# ── Domain 6: OT System Hardening & Patch/Change Management ──────────────────
c = control(fid_ot, "OTCC-1-6", "OT System Hardening & Patch/Change Management",
            "تصليب أنظمة التقنيات التشغيلية وإدارة التصحيح والتغيير",
            "Apply OT-appropriate hardening baselines and a risk-based patching process with mandatory testing on an OT test bench before production deployment.")
ot_ctrl_count += 1

sub(c, "1-6-1",
    "OT Hardening Baselines & Configuration Standards",
    "معايير تصليب وضبط إعدادات أنظمة التقنيات التشغيلية",
    "OT hardening checklist (ICS-CERT/CIS ICS benchmarks); baseline configuration files for HMI/EWS; configuration compliance scan output; deviation register",
    "Hardening baselines (aligned to ICS-CERT or CIS benchmarks) must exist for each OT asset class; deviations must be formally risk-accepted.")
ot_sub_count += 1

sub(c, "1-6-2",
    "OT Patch Assessment & Risk-Based Prioritisation",
    "تقييم التصحيحات وترتيب أولوياتها بحسب المخاطر في بيئات التقنيات التشغيلية",
    "Monthly OT vulnerability/patch advisory review records; vendor patch notification subscription evidence; risk-based patch prioritisation matrix; overdue patch register",
    "All vendor-issued patches for OT systems must be triaged monthly; criticality rating must account for OT operational impact and ICS-specific CVSS scoring.")
ot_sub_count += 1

sub(c, "1-6-3",
    "OT Patch Testing on Isolated Test Bench",
    "اختبار التصحيحات على منصة الاختبار المعزولة قبل النشر في بيئة الإنتاج",
    "OT test-bench asset register; patch-test lab network diagram; patch test procedure and sign-off records; test results reports before production push",
    "All patches and software updates destined for production OT systems must first be validated on a representative isolated test bench.")
ot_sub_count += 1

sub(c, "1-6-4",
    "OT Change Management Process",
    "إدارة التغييرات في بيئات التقنيات التشغيلية",
    "OT change management procedure; change-request forms with cybersecurity impact field; CAB approval records for high-risk OT changes; rollback test records",
    "All changes to OT systems (hardware, software, configuration, firmware) must follow a formal change management process including cybersecurity impact assessment.")
ot_sub_count += 1

# ── Domain 7: OT Removable Media & Portable Device Control ───────────────────
c = control(fid_ot, "OTCC-1-7", "OT Removable Media & Portable Device Control",
            "ضبط الوسائط القابلة للإزالة والأجهزة المحمولة في بيئات التقنيات التشغيلية",
            "Prevent malware ingress via removable media and unmanaged portable devices through policy, technical controls, and malware-scan kiosks.")
ot_ctrl_count += 1

sub(c, "1-7-1",
    "USB & Removable Media Policy Enforcement",
    "تطبيق سياسة USB والوسائط القابلة للإزالة في البيئات التشغيلية",
    "USB control policy document; DLP/port-lock configuration screenshots; approved USB device register; non-compliance incident log",
    "Unauthorised USB and removable media use in OT environments must be blocked by technical controls; only pre-approved, encrypted media should be permitted.")
ot_sub_count += 1

sub(c, "1-7-2",
    "Malware Scan Kiosk for OT Media",
    "كشك الفحص من البرمجيات الخبيثة للوسائط التشغيلية",
    "Malware-scan kiosk asset record (e.g., Honeywell Secure Media Exchange or equivalent); AV definition update log; scan results log for each media use event; kiosk location map",
    "A dedicated, air-gapped or isolated malware-scan kiosk must be deployed for scanning all external media before it is used on any OT asset.")
ot_sub_count += 1

sub(c, "1-7-3",
    "Vendor Laptop & Engineering Tool Verification",
    "التحقق من أجهزة حاسوب الموردين وأدوات الهندسة قبل الوصول",
    "Vendor equipment verification checklist; AV scan results performed at site entry; patch-level verification record; vendor equipment inventory log",
    "Portable engineering laptops and diagnostic tools brought by vendors must be inspected and scanned before connecting to any OT network or device.")
ot_sub_count += 1

# ── Domain 8: OT Physical Security ───────────────────────────────────────────
c = control(fid_ot, "OTCC-1-8", "OT Physical Security",
            "الأمن المادي لأصول ومرافق التقنيات التشغيلية",
            "Protect OT assets from unauthorised physical access through access-controlled facilities, cable management, and physical tamper detection.")
ot_ctrl_count += 1

sub(c, "1-8-1",
    "OT Control Room & Cabinet Physical Access Control",
    "ضبط الوصول المادي لغرف التحكم وخزائن التقنيات التشغيلية",
    "Access control system logs for control room entry; badge access list; physical security audit report; CCTV coverage plan for OT areas",
    "Control rooms, DCS cabinets, PLC panels, and communication rooms housing OT assets must have enforced physical access control with logged entry records.")
ot_sub_count += 1

sub(c, "1-8-2",
    "OT Network Port & Unused Interface Lockdown",
    "تعطيل المنافذ وواجهات الشبكة غير المستخدمة في بيئات التقنيات التشغيلية",
    "Network switch port-security configuration; unused port shutdown evidence; physical RJ45 port-lock documentation; network diagram showing active vs disabled ports",
    "All unused network ports and communication interfaces on OT switches, PLCs, and HMIs must be physically disabled or locked to prevent unauthorised connection.")
ot_sub_count += 1

sub(c, "1-8-3",
    "OT Asset Tamper Detection & Environmental Monitoring",
    "الكشف عن العبث بأصول التقنيات التشغيلية ومراقبة البيئة",
    "Tamper-evident seal records for critical OT cabinets; environmental sensor alerts log (temperature, humidity, power); UPS test records; physical inspection checklist",
    "Critical OT assets must be protected with tamper-evident seals; environmental monitoring for temperature, power, and physical intrusion must be active.")
ot_sub_count += 1

# ── Domain 9: OT Monitoring & Anomaly Detection ───────────────────────────────
c = control(fid_ot, "OTCC-1-9", "OT Monitoring & Anomaly Detection",
            "المراقبة والكشف عن الحالات الشاذة في بيئات التقنيات التشغيلية",
            "Deploy passive OT-aware monitoring tools (e.g., Claroty, Dragos, Nozomi) feeding an OT SOC with asset-aware detection rules and SIEM integration.")
ot_ctrl_count += 1

sub(c, "1-9-1",
    "OT Network Traffic Baseline & Anomaly Detection",
    "رسم خط الأساس لحركة مرور الشبكة التشغيلية والكشف عن الانحرافات",
    "OT monitoring tool deployment diagram; traffic baseline report; anomaly detection rule catalogue; sample alert tickets from OT SOC",
    "Passive OT monitoring must establish a known-good baseline of industrial protocol traffic and generate alerts for deviations indicating potential threats or misconfigurations.")
ot_sub_count += 1

sub(c, "1-9-2",
    "OT SIEM Integration & Log Management",
    "تكامل سجلات التقنيات التشغيلية مع نظام إدارة المعلومات والأحداث الأمنية",
    "OT log source inventory and SIEM onboarding records; log retention configuration (minimum 12 months); OT-specific SIEM use-case documentation; alert SLA evidence",
    "OT log sources (PLCs with event logs, historians, network devices, jump hosts) must be onboarded to SIEM with OT-specific detection use-cases.")
ot_sub_count += 1

sub(c, "1-9-3",
    "OT SOC Use Cases & Threat Intelligence",
    "حالات الاستخدام الأمني ومصادر استخبارات التهديدات في مركز العمليات التشغيلي",
    "OT SOC use-case register (with ICS MITRE ATT&CK mapping); OT threat intelligence subscription evidence; monthly OT threat briefing records; use-case tuning log",
    "The OT SOC must operate ICS-specific detection use-cases aligned to MITRE ATT&CK for ICS, with regular threat intelligence feeds relevant to the operational sector.")
ot_sub_count += 1

sub(c, "1-9-4",
    "OT Vulnerability Scanning (Passive / Risk-Based)",
    "الفحص السلبي للثغرات وإدارتها بحسب المخاطر في بيئات التقنيات التشغيلية",
    "Passive vulnerability discovery report; OT CVE watch-list; risk-based remediation plan with owner and target date; vendor advisory subscription records",
    "Vulnerability scanning in OT environments must be passive or vendor-approved to avoid disrupting live processes; results feed the risk-based patching programme.")
ot_sub_count += 1

# ── Domain 10: OT Incident Response ──────────────────────────────────────────
c = control(fid_ot, "OTCC-1-10", "OT Cybersecurity Incident Response",
            "الاستجابة للحوادث السيبرانية في بيئات التقنيات التشغيلية",
            "Maintain an OT-specific incident response plan that coordinates with plant operations, safety teams, and NCA reporting obligations.")
ot_ctrl_count += 1

sub(c, "1-10-1",
    "OT Incident Response Plan & Playbooks",
    "خطة الاستجابة للحوادث وكتيبات التشغيل في بيئات التقنيات التشغيلية",
    "OT-specific IRP document; scenario playbooks (ransomware on HMI, rogue PLC command, historian compromise); annual tabletop exercise report; lessons-learned register",
    "A documented OT incident response plan must exist, covering detection, containment (including controlled shutdown), eradication, recovery, and NCA notification within required timeframes.")
ot_sub_count += 1

sub(c, "1-10-2",
    "OT Incident Classification & Escalation",
    "تصنيف الحوادث وإجراءات التصعيد في بيئات التقنيات التشغيلية",
    "OT incident severity classification matrix; escalation flowchart linking OT SOC to plant manager and CISO; NCA reporting obligation documentation; sample escalation records",
    "OT incidents must be classified by operational and safety impact alongside cybersecurity severity; escalation paths to plant operations and safety teams must be defined.")
ot_sub_count += 1

sub(c, "1-10-3",
    "OT Forensic & Evidence Preservation",
    "الأدلة الجنائية الرقمية وحفظها في بيئات التقنيات التشغيلية",
    "OT forensic toolkit inventory; chain-of-custody form template; ICS forensic procedure document; sample evidence collection records from past drills",
    "OT incident forensics must follow documented procedures that preserve operational safety; forensic images must be taken from engineering workstations and jump hosts without disrupting live control.")
ot_sub_count += 1

# ── Domain 11: Safety Instrumented Systems Protection ────────────────────────
c = control(fid_ot, "OTCC-1-11", "Safety Instrumented Systems (SIS) Cybersecurity",
            "الأمن السيبراني لأنظمة الأجهزة الأمنية (SIS)",
            "Apply dedicated cybersecurity controls to Safety Instrumented Systems to prevent cyber-induced defeat of safety functions, in alignment with IEC 61511.")
ot_ctrl_count += 1

sub(c, "1-11-1",
    "SIS Cyber Risk Assessment (aligned to IEC 61511 / IEC 62443)",
    "تقييم المخاطر السيبرانية لأنظمة الأجهزة الأمنية وفق معايير IEC 61511 و IEC 62443",
    "SIS cyber risk assessment report; cyber PHA/HAZOP findings; SIL verification incorporating cyber threats; IEC 61511 phase review records",
    "A dedicated cyber risk assessment for each SIS must evaluate the cyber threat impact on safety function integrity and feed into the SIL determination and verification process.")
ot_sub_count += 1

sub(c, "1-11-2",
    "SIS Network Isolation & Air-Gapping",
    "عزل أنظمة الأجهزة الأمنية وقطعها عن الشبكات الأخرى",
    "SIS network topology showing isolation from DCS/control network; data diode or one-way gateway configuration records; periodic validation test of isolation; approved exception register",
    "SIS must be isolated from other OT and IT networks; any data exchange must traverse a unidirectional gateway or data diode; exceptions require formal risk acceptance.")
ot_sub_count += 1

sub(c, "1-11-3",
    "SIS Maintenance & Access Security",
    "أمان الوصول وصيانة أنظمة الأجهزة الأمنية",
    "SIS maintenance access procedure; key-switch/bypass log; authorised SIS engineer list; SIS vendor access approval records; post-maintenance validation checklist",
    "Physical and logical access to SIS for maintenance must be tightly controlled; bypass states must be logged, duration-limited, and require management approval.")
ot_sub_count += 1

# ── Domain 12: OT Backup & Recovery ──────────────────────────────────────────
c = control(fid_ot, "OTCC-1-12", "OT Backup & Recovery",
            "النسخ الاحتياطي والاسترداد في بيئات التقنيات التشغيلية",
            "Maintain verified, offline backups of OT configuration, logic, and data to support rapid recovery after cyber incidents without relying on live network restoration.")
ot_ctrl_count += 1

sub(c, "1-12-1",
    "OT Configuration & Logic Backup",
    "النسخ الاحتياطي لإعدادات ومنطق التحكم في التقنيات التشغيلية",
    "Backup schedule and scope documentation; PLC/DCS logic backup files with version history; backup storage location (offline/air-gapped); last backup test date and result",
    "All PLC ladder logic, DCS function blocks, HMI projects, historian databases, and network configurations must be backed up regularly and stored offline.")
ot_sub_count += 1

sub(c, "1-12-2",
    "OT Backup Integrity Testing & Recovery Drill",
    "اختبار سلامة النسخ الاحتياطية وتدريبات الاسترداد التشغيلي",
    "Quarterly backup restoration test records; OT disaster-recovery drill report; RTO/RPO targets for each OT system class; recovery checklist signed by plant engineer",
    "OT backup integrity must be verified quarterly by performing test restores on the OT test bench; full OT DR drills must be conducted at least annually.")
ot_sub_count += 1

sub(c, "1-12-3",
    "Ransomware-Resilient OT Backup Architecture",
    "بنية النسخ الاحتياطي المقاومة للفدية الخبيثة في بيئات التقنيات التشغيلية",
    "Immutable backup solution configuration; offline/cold copy evidence; backup network isolation diagram; ransomware recovery runbook for OT",
    "OT backups must be stored in an immutable, offline, or air-gapped medium; backup infrastructure must be isolated from production OT networks to survive ransomware.")
ot_sub_count += 1

# ── Domain 13: OT Third-Party & Vendor Access ────────────────────────────────
c = control(fid_ot, "OTCC-1-13", "OT Third-Party & Supply Chain Security",
            "أمن سلسلة التوريد والأطراف الثالثة في بيئات التقنيات التشغيلية",
            "Extend OT cybersecurity requirements to OEM vendors, system integrators, and managed service providers with contractual obligations and periodic assessments.")
ot_ctrl_count += 1

sub(c, "1-13-1",
    "OT Vendor Cybersecurity Due Diligence",
    "العناية الواجبة للأمن السيبراني عند اختيار موردي التقنيات التشغيلية",
    "OT vendor cybersecurity questionnaire responses; SDLC security review for purchased OT software; vendor security assessment report; contractual security clauses",
    "All OT system vendors and integrators must complete a cybersecurity questionnaire; critical vendors must undergo on-site or documented assessments before contract award.")
ot_sub_count += 1

sub(c, "1-13-2",
    "OT Remote Maintenance Agreement & Oversight",
    "اتفاقية الصيانة عن بُعد والإشراف على موردي التقنيات التشغيلية",
    "Vendor remote-access agreement (includes session recording consent); NDA with OT data-handling clauses; supervised-access log; post-session closure confirmation",
    "Vendor remote maintenance sessions must be governed by a formal agreement specifying session recording, supervision requirements, and immediate credential revocation on completion.")
ot_sub_count += 1

sub(c, "1-13-3",
    "OT Software & Firmware Supply Chain Integrity",
    "سلامة سلسلة توريد البرمجيات والبرامج الثابتة للتقنيات التشغيلية",
    "Vendor software signing/hash verification records; patch authenticity verification procedure; SBOM for critical OT software; counterfeit hardware detection checklist",
    "All software updates and firmware images for OT systems must be verified for authenticity using vendor-supplied hashes or digital signatures before installation.")
ot_sub_count += 1

# =============================================================================
# FRAMEWORK B: TCC-1:2021
# Telework & Social Media Cybersecurity Controls
# =============================================================================

fid_tw = framework(
    "TCC-1:2021",
    "Telework & Social Media Cybersecurity Controls",
    "ضوابط الأمن السيبراني للعمل عن بُعد ووسائل التواصل",
    "2021",
    "NCA",
    "NCA framework encompassing two complementary control sets: (1) Teleworking Cybersecurity Controls governing secure remote work arrangements for employees and contractors; and (2) Organisations' Social Media Accounts Cybersecurity Controls (OSMACC) governing the security of official organisational social media presence. Both sets are mandatory for NCA-regulated entities. — practitioner SME mapping; structure aligned to the published framework, control text paraphrased (not verbatim)."
)

tw_ctrl_count = 0
tw_sub_count  = 0

# ── B-1: Teleworking Governance & Policy ─────────────────────────────────────
c = control(fid_tw, "TCC-1-1", "Teleworking Governance & Policy",
            "حوكمة وسياسات العمل عن بُعد",
            "Establish a formal, approved teleworking cybersecurity policy covering eligibility, acceptable use, data handling, and disciplinary consequences.")
tw_ctrl_count += 1

sub(c, "1-1-1",
    "Teleworking Cybersecurity Policy",
    "سياسة الأمن السيبراني للعمل عن بُعد",
    "Board/executive-approved teleworking policy document; version history; distribution acknowledgement log from all teleworkers; annual review record",
    "A documented teleworking cybersecurity policy must be approved, published, and acknowledged by all employees and contractors authorised to work remotely.")
tw_sub_count += 1

sub(c, "1-1-2",
    "Acceptable Use Agreement for Teleworkers",
    "اتفاقية الاستخدام المقبول للعاملين عن بُعد",
    "Signed AUP/teleworking agreement per employee; BYOD consent form; updated HR onboarding checklist confirming cybersecurity AUP signature; periodic re-acknowledgement records",
    "Each teleworker must sign an acceptable-use agreement covering permitted devices, network requirements, data handling, and obligation to report security incidents.")
tw_sub_count += 1

sub(c, "1-1-3",
    "Teleworking Risk Assessment",
    "تقييم مخاطر العمل عن بُعد",
    "Teleworking risk assessment report; role-based risk classification (high/medium/low sensitivity roles); risk acceptance sign-off by CISO; re-assessment trigger log",
    "A risk assessment specific to teleworking must identify threats to data confidentiality, device security, and network exposure for each category of remote worker.")
tw_sub_count += 1

# ── B-2: Secure Remote Access (VPN, MFA) ─────────────────────────────────────
c = control(fid_tw, "TCC-1-2", "Secure Remote Access — VPN & Multi-Factor Authentication",
            "الوصول الآمن عن بُعد — الشبكة الافتراضية الخاصة والمصادقة متعددة العوامل",
            "Provide corporate-managed VPN with MFA as the mandatory channel for teleworkers accessing internal resources; prohibit split-tunnelling for sensitive data access.")
tw_ctrl_count += 1

sub(c, "1-2-1",
    "Corporate VPN Deployment & Configuration",
    "نشر الشبكة الافتراضية الخاصة للمؤسسة وضبط إعداداتها",
    "VPN solution architecture diagram; SSL/IPsec configuration baseline; split-tunnel policy (disabled for sensitive roles); VPN capacity and availability report",
    "A corporate-managed VPN must be the approved access channel for all teleworkers; configurations must enforce encryption, certificate-based authentication, and full-tunnel for sensitive roles.")
tw_sub_count += 1

sub(c, "1-2-2",
    "Multi-Factor Authentication (MFA) for Remote Access",
    "المصادقة متعددة العوامل للوصول عن بُعد",
    "MFA enrolment report (% of teleworkers enrolled); MFA platform configuration; phishing-resistant MFA (FIDO2/OTP app) policy; MFA exemption register with risk-acceptance",
    "MFA must be enforced for all remote access to corporate systems; SMS-only OTP is discouraged; phishing-resistant authenticators are preferred for privileged roles.")
tw_sub_count += 1

sub(c, "1-2-3",
    "Remote Access Session Controls & Timeout",
    "ضبط جلسات الوصول عن بُعد والمهلة الزمنية",
    "VPN session timeout configuration; idle-lock policy on remote desktops; concurrent-session limit evidence; failed-authentication lockout configuration",
    "Remote access sessions must enforce idle timeout, concurrent-session limits, and automatic disconnection after defined inactivity periods.")
tw_sub_count += 1

# ── B-3: Endpoint & BYOD Security ────────────────────────────────────────────
c = control(fid_tw, "TCC-1-3", "Endpoint & BYOD Security for Teleworkers",
            "أمن الأجهزة الطرفية والأجهزة الشخصية للعاملين عن بُعد",
            "Enforce minimum security baselines on all devices used for telework — whether corporate-issued or BYOD — through MDM/UEM and compliance-gating before VPN admission.")
tw_ctrl_count += 1

sub(c, "1-3-1",
    "Corporate Device Hardening & MDM Enrolment",
    "تصليب الأجهزة المؤسسية وتسجيلها في نظام إدارة الأجهزة المحمولة",
    "MDM/UEM platform enrolment report; device compliance policy configuration; patch compliance dashboard; full-disk encryption enforcement evidence (BitLocker/FileVault)",
    "All corporate-issued telework endpoints must be enrolled in MDM, have full-disk encryption enabled, and meet patch compliance before VPN access is granted.")
tw_sub_count += 1

sub(c, "1-3-2",
    "BYOD Security Requirements & Containerisation",
    "متطلبات أمان الأجهزة الشخصية والفصل بين البيانات المؤسسية والشخصية",
    "BYOD policy document; MAM/containerisation configuration; BYOD enrolment records; jailbreak/root-detection alert log; BYOD access-tier limitation documentation",
    "BYOD devices must be registered; corporate data must be containerised and remotely wipeable; root/jailbroken devices must be blocked from corporate resource access.")
tw_sub_count += 1

sub(c, "1-3-3",
    "Endpoint Antimalware & EDR for Telework Devices",
    "الحماية من البرمجيات الخبيثة واكتشاف التهديدات على أجهزة العمل عن بُعد",
    "EDR/AV deployment coverage report; last-scan date and quarantine log; EDR alert escalation procedure; AV definition currency check",
    "All telework endpoints must run up-to-date EDR/antimalware with real-time protection; EDR alerts must feed to the corporate SOC.")
tw_sub_count += 1

sub(c, "1-3-4",
    "Application Allow-listing & Web Filtering for Remote Endpoints",
    "قوائم التطبيقات المعتمدة وتصفية الويب لأجهزة العمل عن بُعد",
    "Application allow-list policy; web proxy/DNS filter configuration and block-list categories; DNS-over-VPN configuration; exception approval log",
    "Remote endpoints must enforce application allow-listing or web filtering to prevent access to malicious domains and unauthorised software installation during telework.")
tw_sub_count += 1

# ── B-4: Home/Remote Environment & Data Handling ─────────────────────────────
c = control(fid_tw, "TCC-1-4", "Home/Remote Environment & Data Handling",
            "بيئة العمل عن بُعد وأمن التعامل مع البيانات",
            "Guide teleworkers on securing their home network environment and handling sensitive data appropriately outside the corporate perimeter.")
tw_ctrl_count += 1

sub(c, "1-4-1",
    "Home Router & Wi-Fi Security Requirements",
    "متطلبات أمان أجهزة التوجيه والشبكات اللاسلكية المنزلية",
    "Teleworker home network security checklist (in AUP); self-attestation forms confirming WPA2/3, router firmware update, default credential change; spot-check audit sample",
    "Teleworkers must configure home routers with strong Wi-Fi encryption (WPA2 minimum), change default credentials, and apply firmware updates; self-attestation is required.")
tw_sub_count += 1

sub(c, "1-4-2",
    "Data Handling & Printing Restrictions for Teleworkers",
    "قيود التعامل مع البيانات والطباعة للعاملين عن بُعد",
    "DLP policy configuration covering cloud upload and local print; data classification policy references; teleworker acknowledgement of no-local-print rule for confidential data; DLP alert sample",
    "Teleworkers must not print or locally store classified corporate data without authorisation; DLP controls must prevent unauthorised exfiltration via cloud sync or external storage.")
tw_sub_count += 1

sub(c, "1-4-3",
    "Video Conferencing & Screen-Sharing Security",
    "أمان مؤتمرات الفيديو ومشاركة الشاشة عن بُعد",
    "Approved video-conferencing platform list; meeting password and waiting-room enforcement configuration; screen-share data classification guidance; recording consent policy",
    "Approved corporate video-conferencing tools must be used; meeting links must be password-protected; screen-sharing of confidential information must follow data classification guidance.")
tw_sub_count += 1

# ── B-5: Teleworking Awareness & Training ────────────────────────────────────
c = control(fid_tw, "TCC-1-5", "Teleworking Cybersecurity Awareness & Training",
            "التوعية والتدريب على الأمن السيبراني للعمل عن بُعد",
            "Deliver targeted awareness and training to teleworkers covering phishing, social engineering, secure device use, and incident reporting obligations.")
tw_ctrl_count += 1

sub(c, "1-5-1",
    "Teleworker Security Awareness Programme",
    "برنامج التوعية الأمنية للعاملين عن بُعد",
    "Annual teleworker security awareness training completion records; training content covering phishing, secure Wi-Fi, and device security; knowledge-check pass rate report",
    "All teleworkers must complete security awareness training at onboarding and annually; training must include scenarios specific to remote-work threats.")
tw_sub_count += 1

sub(c, "1-5-2",
    "Phishing Simulation for Remote Workers",
    "محاكاة هجمات التصيد الاحتيالي للعاملين عن بُعد",
    "Phishing simulation campaign schedule and results (click rate, report rate); targeted follow-up training records for repeat clickers; trend analysis report",
    "Regular phishing simulations must target teleworkers with scenarios relevant to remote-work context (fake VPN login pages, IT help-desk impersonation).")
tw_sub_count += 1

sub(c, "1-5-3",
    "Incident Reporting Channel for Remote Workers",
    "قنوات الإبلاغ عن الحوادث السيبرانية للعاملين عن بُعد",
    "Incident reporting SOP communicated to all teleworkers; reporting channel accessibility test (available outside VPN); incident report sample from remote workers; awareness material referencing the channel",
    "Teleworkers must have accessible, well-publicised channels for reporting cybersecurity incidents even when unable to connect to the corporate VPN.")
tw_sub_count += 1

# ── B-6: Social Media Account Governance ─────────────────────────────────────
c = control(fid_tw, "TCC-1-6", "Organisational Social Media Account Governance",
            "حوكمة حسابات وسائل التواصل الاجتماعي المؤسسية",
            "Maintain an authorised inventory of official organisational social media accounts, assign ownership, and establish an approval workflow for content publication.")
tw_ctrl_count += 1

sub(c, "1-6-1",
    "Official Social Media Account Inventory",
    "جرد حسابات وسائل التواصل الاجتماعي الرسمية للمؤسسة",
    "Authorised social media account register (platform, handle, owner, creation date, purpose); quarterly reconciliation evidence; decommissioned account closure records",
    "A complete inventory of all official organisational accounts on each social media platform must be maintained; unauthorised accounts must be identified and closed.")
tw_sub_count += 1

sub(c, "1-6-2",
    "Social Media Policy & Content Approval Workflow",
    "سياسة وسائل التواصل الاجتماعي وإجراءات اعتماد المحتوى",
    "Social media policy document; content approval workflow diagram; approved-publisher list per platform; sample approved post records; post-rejection log",
    "A published policy must govern what can be shared on official channels; a multi-step content approval workflow must be enforced before publication.")
tw_sub_count += 1

sub(c, "1-6-3",
    "Social Media Account Ownership & Succession",
    "ملكية حسابات التواصل الاجتماعي وإجراءات التعاقب",
    "Account-owner role matrix; offboarding checklist confirming credential transfer; emergency access procedure for account owner absence; account access audit log",
    "Each social media account must have a named primary and backup owner; offboarding of owners must trigger immediate credential rotation and ownership transfer.")
tw_sub_count += 1

# ── B-7: Social Media Account Protection ─────────────────────────────────────
c = control(fid_tw, "TCC-1-7", "Social Media Account Protection & Access Control",
            "حماية حسابات التواصل الاجتماعي والتحكم في صلاحيات الوصول إليها",
            "Secure official social media accounts with MFA, strong unique credentials, restricted admin roles, and periodic access reviews.")
tw_ctrl_count += 1

sub(c, "1-7-1",
    "MFA Enforcement on All Official Social Media Accounts",
    "تطبيق المصادقة متعددة العوامل على جميع حسابات التواصل الاجتماعي الرسمية",
    "MFA enrolment screenshots or platform admin panel exports for each official account; MFA method documentation (authenticator app preferred over SMS); periodic MFA validity check records",
    "MFA must be enabled on all official social media accounts; authenticator-app-based MFA is preferred; accounts without MFA must be treated as critical remediation items.")
tw_sub_count += 1

sub(c, "1-7-2",
    "Social Media Admin Role Minimisation & Separation of Duties",
    "تقليص صلاحيات الإدارة وتطبيق مبدأ الفصل بين المهام في حسابات التواصل الاجتماعي",
    "Admin role list per platform (screenshots); role minimisation evidence (number of admins ≤ 3 per account as a benchmark); SoD matrix for content creation vs approval vs publishing",
    "The number of admin-level users on each official account must be minimised; content creation, approval, and publishing roles must be separated where platform features allow.")
tw_sub_count += 1

sub(c, "1-7-3",
    "Social Media Account Password & Credential Management",
    "إدارة كلمات المرور وبيانات الاعتماد لحسابات التواصل الاجتماعي",
    "Password manager / vault usage evidence for social media credentials; password complexity policy extract; credential rotation log (minimum annual); no-shared-password attestation",
    "Social media account passwords must be unique, complex, and stored in a corporate password vault; credentials must be rotated at least annually and immediately upon staff changes.")
tw_sub_count += 1

sub(c, "1-7-4",
    "Third-Party Social Media App & Integration Control",
    "ضبط تطبيقات وتكاملات الطرف الثالث المرتبطة بحسابات التواصل الاجتماعي",
    "Authorised third-party app register for each social platform; revoked app audit log; periodic review of connected apps (quarterly); approval workflow for new integrations",
    "All third-party applications connected to official social media accounts must be inventoried, approved, and periodically reviewed; unauthorised integrations must be revoked immediately.")
tw_sub_count += 1

# ── B-8: Social Media Incident & Impersonation Response ──────────────────────
c = control(fid_tw, "TCC-1-8", "Social Media Incident & Impersonation Response",
            "الاستجابة لحوادث الانتحال والاستيلاء على حسابات التواصل الاجتماعي",
            "Prepare and exercise incident response procedures for social media account compromise, impersonation, and unauthorised content publication.")
tw_ctrl_count += 1

sub(c, "1-8-1",
    "Social Media Account Compromise Playbook",
    "كتيب التشغيل للتعامل مع الاختراق والسيطرة على الحسابات",
    "Social media account compromise playbook; incident response drills (tabletop); contact list for platform security/trust-and-safety teams; credential invalidation runbook",
    "A playbook covering detection, containment (immediate credential reset, session revocation), and recovery steps must be documented and tested for each major platform.")
tw_sub_count += 1

sub(c, "1-8-2",
    "Social Media Impersonation Detection & Takedown",
    "الكشف عن انتحال الشخصية واتخاذ إجراءات الإزالة",
    "Impersonation monitoring tool or scheduled search procedure; takedown request log (platform and NCA/CITC channel); response time SLA for impersonation incidents; past takedown case records",
    "A monitoring process for impersonation accounts must be in place; takedown requests must be filed through official platform channels and, where applicable, escalated to NCA/CITC.")
tw_sub_count += 1

sub(c, "1-8-3",
    "Unauthorised Content Removal & Communication",
    "إزالة المحتوى غير المعتمد والتواصل مع المتابعين",
    "Content removal procedure; unauthorised post log with removal time; crisis communication template for followers; post-incident review records",
    "Procedures must exist for rapid removal of unauthorised or malicious content posted to official accounts, with a communications plan to alert followers when incidents affect trust.")
tw_sub_count += 1

# ── B-9: Social Media Monitoring ─────────────────────────────────────────────
c = control(fid_tw, "TCC-1-9", "Social Media Security Monitoring & Awareness",
            "المراقبة الأمنية لحسابات التواصل الاجتماعي وتعزيز الوعي",
            "Continuously monitor official social media accounts for anomalous login activity, unauthorised access, and brand-impersonation threats.")
tw_ctrl_count += 1

sub(c, "1-9-1",
    "Social Media Account Login & Activity Monitoring",
    "مراقبة تسجيل الدخول والنشاطات على حسابات التواصل الاجتماعي",
    "Login alert configuration on each platform (email/app notifications for unrecognised logins); platform activity log review frequency; SIEM integration for social media alerts where available",
    "Login and administrative activity notifications must be enabled on all official accounts; unusual login locations or times must trigger immediate investigation.")
tw_sub_count += 1

sub(c, "1-9-2",
    "Brand & Keyword Monitoring for Threats",
    "مراقبة العلامة التجارية والمصطلحات المرتبطة بها للكشف عن التهديدات",
    "Brand monitoring tool or saved-search procedure; keyword/hashtag alert configuration; weekly brand monitoring report samples; escalation path for identified threats",
    "A brand and keyword monitoring capability must detect impersonation accounts, data leakage, credential exposure, and reputational threats on major social platforms.")
tw_sub_count += 1

sub(c, "1-9-3",
    "Social Media Security Awareness for Staff & Administrators",
    "التوعية بأمن وسائل التواصل الاجتماعي للموظفين ومديري الحسابات",
    "Social-media security training completion records for account administrators; awareness content covering phishing via DM, credential theft, account takeover signs; annual refresher evidence",
    "All staff who manage official social media accounts must receive dedicated security awareness training covering account takeover, phishing via social channels, and impersonation tactics.")
tw_sub_count += 1

# =============================================================================
# Commit and report
# =============================================================================
conn.commit()
print()
print("=" * 70)
print("NCA OT + Telework SME Catalog Seed — Completed")
print("=" * 70)
print(f"  OTCC-1:2022  controls={ot_ctrl_count}  sub_controls={ot_sub_count}")
print(f"  TCC-1:2021   controls={tw_ctrl_count}  sub_controls={tw_sub_count}")
print(f"  TOTAL        controls={ot_ctrl_count + tw_ctrl_count}  sub_controls={ot_sub_count + tw_sub_count}")
print("=" * 70)
