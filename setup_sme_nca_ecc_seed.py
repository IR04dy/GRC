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

# ─── FRAMEWORK ────────────────────────────────────────────────────────────────
fid = framework(
    "ECC-2:2024",
    "Essential Cybersecurity Controls",
    "الضوابط الأساسية للأمن السيبراني",
    "2024",
    "NCA",
    "NCA's second edition of the Essential Cybersecurity Controls (ECC-2:2024), mandatory for all government and critical national infrastructure organisations in Saudi Arabia. Covers five domains: Cybersecurity Governance, Cybersecurity Defence, Cybersecurity Resilience, Third-Party & Cloud Computing Cybersecurity, and Industrial Control Systems Cybersecurity. — practitioner SME mapping; structure aligned to the published framework, control text paraphrased (not verbatim)."
)

# ─── DOMAIN 1: CYBERSECURITY GOVERNANCE ──────────────────────────────────────

c_1_1 = control(fid, "1-1", "Cybersecurity Strategy",
    "استراتيجية الأمن السيبراني",
    "Establishing, approving, and maintaining a formal cybersecurity strategy aligned with the organisation's business objectives and national cybersecurity requirements.")

sub(c_1_1, "1-1-1",
    "Cybersecurity Strategy Formulation & Board Approval",
    "صياغة استراتيجية الأمن السيبراني واعتمادها من مجلس الإدارة",
    "Approved cybersecurity strategy document signed by board or equivalent governing body; board meeting minutes recording approval; strategy version-control log showing review dates",
    "A documented cybersecurity strategy covering organisational vision, objectives, risk appetite, and alignment to national frameworks must be formally approved at board level.")

sub(c_1_1, "1-1-2",
    "Cybersecurity Strategy Review & Update",
    "مراجعة استراتيجية الأمن السيبراني وتحديثها",
    "Annual strategy review report; change-log between versions; evidence of trigger-based review (e.g. major incident, regulatory change); sign-off by CISO and executive sponsor",
    "The cybersecurity strategy must be reviewed at least annually or upon significant organisational or threat-landscape changes, with documented outcomes.")

sub(c_1_1, "1-1-3",
    "Cybersecurity Strategy Communication & Alignment",
    "إيصال استراتيجية الأمن السيبراني وضمان التوافق التنظيمي",
    "Internal strategy briefing records; department-level acknowledgement sign-offs; strategy cascade meeting minutes; alignment evidence from business unit plans referencing cybersecurity objectives",
    "The approved strategy must be communicated to all relevant stakeholders and aligned with departmental plans and risk registers.")

c_1_2 = control(fid, "1-2", "Cybersecurity Management",
    "إدارة الأمن السيبراني",
    "Establishing governance structures, appointing cybersecurity leadership, and ensuring management accountability for cybersecurity outcomes.")

sub(c_1_2, "1-2-1",
    "CISO Appointment & Mandate",
    "تعيين مسؤول الأمن السيبراني وتحديد صلاحياته",
    "Formal CISO appointment letter or board resolution; CISO job description with reporting line to C-suite; organisation chart showing position; evidence CISO is independent from IT operations",
    "A qualified Chief Information Security Officer (CISO) or equivalent must be formally appointed with a clear mandate, authority, and reporting line to senior management.")

sub(c_1_2, "1-2-2",
    "Cybersecurity Governance Committee",
    "لجنة حوكمة الأمن السيبراني",
    "Committee charter approved by board; meeting minutes for the past 12 months; terms of reference listing members and quorum requirements; action-item tracking register",
    "A cybersecurity governance committee (or equivalent steering body) must be constituted with defined terms of reference, meeting cadence, and documented decisions.")

sub(c_1_2, "1-2-3",
    "Cybersecurity Metrics & Reporting to Management",
    "مؤشرات الأمن السيبراني وإعداد التقارير للإدارة العليا",
    "Cybersecurity KPI/KRI dashboard reports (at least quarterly); management briefing packs; evidence of escalation path for critical findings; board-level cybersecurity status report",
    "Cybersecurity performance metrics must be collected, analysed, and reported to senior management and the board on a defined periodic basis.")

sub(c_1_2, "1-2-4",
    "Cybersecurity Budget Allocation",
    "تخصيص ميزانية الأمن السيبراني",
    "Approved annual cybersecurity budget line in financial plan; budget vs. actual expenditure tracking report; rationale documentation for budget requests; CFO/board approval evidence",
    "Dedicated cybersecurity budget must be formally allocated, tracked, and reported to demonstrate sustained investment in cybersecurity controls.")

c_1_3 = control(fid, "1-3", "Cybersecurity Policies & Procedures",
    "سياسات وإجراءات الأمن السيبراني",
    "Developing, approving, maintaining, and communicating a comprehensive cybersecurity policy framework aligned to ECC and applicable regulations.")

sub(c_1_3, "1-3-1",
    "Cybersecurity Policy Framework Development & Approval",
    "إعداد إطار سياسات الأمن السيبراني واعتماده",
    "Master cybersecurity policy document with approval signatures; policy register listing all tier-2 and tier-3 documents; approval minutes or workflow records; document control metadata (version, owner, next review date)",
    "A formally approved cybersecurity policy framework must exist, covering all ECC domains, and be endorsed by senior management.")

sub(c_1_3, "1-3-2",
    "Policies Review & Update Cycle",
    "دورة مراجعة السياسات وتحديثها",
    "Policy review schedule showing annual cadence; updated policy versions with change-log; stakeholder consultation records; post-update distribution and acknowledgement evidence",
    "All cybersecurity policies must be reviewed at least annually or after major changes, with documented review outcomes and version history.")

sub(c_1_3, "1-3-3",
    "Policy Exception Management",
    "إدارة الاستثناءات على السياسات",
    "Formal exception request forms with risk justification; approval records from CISO or delegated authority; exception register with expiry dates; compensating controls documentation",
    "A formal exception management process must exist to record, approve, track, and time-bound deviations from cybersecurity policies.")

sub(c_1_3, "1-3-4",
    "Policies Compliance Monitoring",
    "رصد الامتثال للسياسات",
    "Periodic policy compliance assessment reports; automated policy-compliance tool scan results; non-compliance issue log with remediation tracking; internal audit findings relating to policy adherence",
    "Compliance with cybersecurity policies must be actively monitored with findings tracked to remediation.")

c_1_4 = control(fid, "1-4", "Cybersecurity Roles & Responsibilities",
    "أدوار ومسؤوليات الأمن السيبراني",
    "Defining, assigning, and communicating clear cybersecurity roles, responsibilities, and accountability structures across the organisation.")

sub(c_1_4, "1-4-1",
    "Cybersecurity RACI / Roles Definition",
    "تحديد مصفوفة الأدوار والمسؤوليات في الأمن السيبراني",
    "Published RACI matrix or roles-and-responsibilities document covering all ECC domains; sign-off from department heads; integration with HR job descriptions; organisational chart extract",
    "Roles and responsibilities for all cybersecurity functions must be formally documented, assigned, and communicated across the organisation.")

sub(c_1_4, "1-4-2",
    "Segregation of Duties in Cybersecurity Functions",
    "الفصل بين المهام في وظائف الأمن السيبراني",
    "SoD matrix for privileged cybersecurity roles; access-control configuration screenshots confirming SoD enforcement; internal audit report on SoD conflicts; compensating controls for identified conflicts",
    "Conflicting cybersecurity duties must be separated across different individuals to prevent unauthorised actions and reduce fraud risk.")

sub(c_1_4, "1-4-3",
    "Cybersecurity Accountability at Senior Level",
    "المساءلة في الأمن السيبراني على المستوى التنفيذي",
    "Executive performance objectives including cybersecurity KPIs; board-approved accountability framework; escalation records for cybersecurity breaches reaching executive level; signed accountability statements",
    "Senior executives must have documented accountability for cybersecurity outcomes reflected in performance objectives and governance frameworks.")

c_1_5 = control(fid, "1-5", "Cybersecurity Risk Management",
    "إدارة مخاطر الأمن السيبراني",
    "Establishing a systematic process to identify, assess, treat, and monitor cybersecurity risks aligned to enterprise risk management.")

sub(c_1_5, "1-5-1",
    "Cybersecurity Risk Management Framework",
    "إطار إدارة مخاطر الأمن السيبراني",
    "Approved cybersecurity risk management policy/framework document; risk appetite statement endorsed by board; risk methodology document (likelihood/impact scales, scoring criteria); alignment evidence with enterprise risk framework",
    "A formal cybersecurity risk management framework including risk appetite, methodology, and integration with enterprise risk management must be documented and approved.")

sub(c_1_5, "1-5-2",
    "Cybersecurity Risk Register Maintenance",
    "صيانة سجل مخاطر الأمن السيبراني",
    "Current cybersecurity risk register with risk owner, likelihood, impact, inherent/residual risk scores, treatment plans, and review dates; quarterly update records; risk committee review minutes",
    "A cybersecurity risk register must be maintained, updated at least quarterly, with risks assigned to owners and linked to treatment actions.")

sub(c_1_5, "1-5-3",
    "Risk Treatment & Acceptance Process",
    "عملية معالجة المخاطر وقبولها",
    "Risk treatment plan documents with timelines and owners; risk acceptance sign-off records by authorised stakeholders; tracking evidence of treatment milestones; residual risk re-assessment records",
    "Risk treatment options (mitigate, accept, transfer, avoid) must be formally decided, documented, and tracked to completion.")

sub(c_1_5, "1-5-4",
    "Threat Intelligence Integration into Risk Management",
    "دمج معلومات التهديدات في إدارة المخاطر",
    "Threat intelligence subscription or feed records; documented process for incorporating threat intel into risk register updates; sample risk-register entries updated based on threat intelligence reports; analyst review logs",
    "Threat intelligence must be systematically used to update and prioritise cybersecurity risk assessments.")

c_1_6 = control(fid, "1-6", "Cybersecurity in IT Project Management",
    "الأمن السيبراني في إدارة مشاريع تقنية المعلومات",
    "Ensuring cybersecurity requirements are embedded in IT project lifecycles from initiation through deployment and decommissioning.")

sub(c_1_6, "1-6-1",
    "Cybersecurity Requirements in Project Initiation",
    "متطلبات الأمن السيبراني في مرحلة انطلاق المشاريع",
    "Project charter templates showing mandatory cybersecurity section; sample completed project charters with cybersecurity requirements; CISO/security team sign-off records on project initiation documents",
    "Cybersecurity requirements must be formally captured and approved during project initiation, with CISO or security team involvement.")

sub(c_1_6, "1-6-2",
    "Security Review at Key Project Milestones",
    "مراجعة الأمن السيبراني عند المراحل الرئيسية للمشاريع",
    "Security gate-review checklists for design, development, and pre-go-live phases; sign-off records from security team at each gate; open security findings log with resolution tracking; project closure security sign-off",
    "Security gate reviews must be conducted at defined project milestones with documented findings and mandatory sign-off before progression.")

sub(c_1_6, "1-6-3",
    "Security Acceptance Testing",
    "اختبار القبول الأمني قبل تشغيل الأنظمة",
    "Security acceptance test plan and results report; VAPT report (if applicable) performed pre-go-live; remediation evidence for critical/high findings; security sign-off letter authorising system go-live",
    "Security acceptance testing must be completed before new systems go live, with critical findings remediated and formally signed off.")

c_1_7 = control(fid, "1-7", "Compliance with Cybersecurity Standards, Laws & Regulations",
    "الامتثال لمعايير وقوانين وأنظمة الأمن السيبراني",
    "Identifying applicable cybersecurity legal, regulatory, and contractual obligations and ensuring ongoing compliance.")

sub(c_1_7, "1-7-1",
    "Regulatory & Legal Obligations Register",
    "سجل الالتزامات التنظيمية والقانونية للأمن السيبراني",
    "Maintained regulatory compliance register listing all applicable laws (Cybersecurity Law, PDPL, SAMA CSF, NCA ECC, etc.) with applicability rationale; quarterly update log; legal/compliance team review sign-off",
    "All applicable cybersecurity-related laws, regulations, and standards must be identified and maintained in a compliance register with clear ownership.")

sub(c_1_7, "1-7-2",
    "Compliance Assessment Against Regulatory Requirements",
    "تقييم الامتثال للمتطلبات التنظيمية",
    "Annual compliance assessment report against each applicable regulation/standard; gap analysis with remediation roadmap; evidence submitted to regulators (e.g. NCA self-assessment submission); third-party audit report",
    "Periodic assessments must be conducted against all applicable regulations, with gaps formally tracked and remediated.")

sub(c_1_7, "1-7-3",
    "Regulatory Incident Notification Compliance",
    "الامتثال لمتطلبات الإبلاغ عن الحوادث للجهات التنظيمية",
    "Incident notification procedure referencing regulatory timelines (NCA 72-hour rule, SAMA requirements); sample notification records sent to regulators; internal log of incidents assessed for notification obligations",
    "Cybersecurity incidents meeting regulatory reporting thresholds must be notified to the appropriate authorities within mandated timelines.")

c_1_8 = control(fid, "1-8", "Periodical Cybersecurity Review & Audit",
    "المراجعة والتدقيق الدوري للأمن السيبراني",
    "Conducting regular internal and external cybersecurity audits and assessments to verify control effectiveness.")

sub(c_1_8, "1-8-1",
    "Internal Cybersecurity Audit Programme",
    "برنامج التدقيق الداخلي للأمن السيبراني",
    "Annual internal audit plan showing cybersecurity scope; completed internal audit reports for the current cycle; finding register with severity ratings and remediation owners; audit committee presentation records",
    "An internal cybersecurity audit programme must be established with a risk-based annual plan, resulting in formal reports with tracked findings.")

sub(c_1_8, "1-8-2",
    "Independent External Cybersecurity Assessment",
    "التقييم الخارجي المستقل للأمن السيبراني",
    "Contract or engagement letter with accredited external assessor; external assessment report (ECC, ISO 27001, or equivalent); management response and remediation plan; evidence of assessor independence",
    "An independent external cybersecurity assessment must be conducted at least once every two years by a qualified and independent party.")

sub(c_1_8, "1-8-3",
    "Audit Findings Remediation Tracking",
    "تتبع معالجة نتائج التدقيق",
    "Centralised audit findings register with severity, owner, target date, and status; evidence of remediation (configuration screenshots, policy updates, test results); overdue findings escalation records; closure verification by audit team",
    "All audit and assessment findings must be tracked in a centralised register with owners, deadlines, and evidence of remediation verified by the audit function.")

c_1_9 = control(fid, "1-9", "Cybersecurity in Human Resources",
    "الأمن السيبراني في الموارد البشرية",
    "Integrating cybersecurity requirements into HR processes including recruitment, employment, and termination.")

sub(c_1_9, "1-9-1",
    "Cybersecurity Background Screening",
    "الفحص الأمني للموظفين الجدد",
    "Background screening policy referencing cybersecurity roles; completed screening records for employees in security-sensitive roles; third-party screening provider reports; HR and CISO sign-off process evidence",
    "Background screening appropriate to the sensitivity of the role must be performed for all employees and contractors with access to critical systems or information.")

sub(c_1_9, "1-9-2",
    "Cybersecurity Obligations in Employment Agreements",
    "التزامات الأمن السيبراني في عقود التوظيف",
    "Standard employment contract templates including confidentiality and cybersecurity clauses; signed employee agreements; NDA records for privileged users; legal review confirmation of adequacy",
    "All employment and contractor agreements must include cybersecurity responsibilities, confidentiality obligations, and acceptable-use terms.")

sub(c_1_9, "1-9-3",
    "Joiner-Mover-Leaver Cybersecurity Process",
    "إجراءات الأمن السيبراني عند التعيين والنقل والمغادرة",
    "HR onboarding/offboarding checklist with cybersecurity steps; access revocation records for leavers (within 24-hour SLA); system access review after role change; IT confirmation of equipment return and account closure",
    "Cybersecurity controls (access provisioning, revocation, equipment return) must be applied consistently at joining, internal transfer, and departure of all staff.")

sub(c_1_9, "1-9-4",
    "Disciplinary Process for Cybersecurity Policy Violations",
    "الإجراءات التأديبية لمخالفات سياسات الأمن السيبراني",
    "Disciplinary policy referencing cybersecurity violations; sample anonymised records of investigations/disciplinary actions; escalation procedure for serious breaches; HR and legal review of disciplinary framework",
    "A formal disciplinary process must address cybersecurity policy violations with proportionate and consistently applied consequences.")

c_1_10 = control(fid, "1-10", "Cybersecurity Awareness & Training",
    "التوعية والتدريب في مجال الأمن السيبراني",
    "Ensuring all staff receive appropriate cybersecurity awareness and that security practitioners receive role-specific technical training.")

sub(c_1_10, "1-10-1",
    "Mandatory Cybersecurity Awareness Programme",
    "برنامج التوعية الإلزامي بالأمن السيبراني",
    "Annual awareness programme calendar; LMS completion records showing ≥90% staff completion rate; awareness content samples (phishing simulations, e-learning modules, posters); post-training assessment results",
    "All employees and contractors must complete mandatory annual cybersecurity awareness training with completion tracked and reported.")

sub(c_1_10, "1-10-2",
    "Role-Based Cybersecurity Technical Training",
    "التدريب التقني على الأمن السيبراني حسب الأدوار",
    "Training needs analysis per security role; approved annual training plan; training completion certificates; CPD/certification records (CISSP, CISM, CEH, etc.); budget allocation for training",
    "Cybersecurity team members and IT staff with security responsibilities must receive role-appropriate technical training and certifications on a planned basis.")

sub(c_1_10, "1-10-3",
    "Phishing Simulation & Social Engineering Exercises",
    "محاكاة التصيد الاحتيالي وتمارين الهندسة الاجتماعية",
    "Phishing simulation platform records with click-rate metrics by department; campaign schedule (minimum quarterly); improvement trend analysis over time; targeted remedial training records for repeat clickers",
    "Regular phishing simulation exercises must be conducted with metrics tracked and remedial training delivered to high-risk users.")

sub(c_1_10, "1-10-4",
    "Executive & Board Cybersecurity Briefings",
    "إحاطات الأمن السيبراني للقيادة التنفيذية ومجلس الإدارة",
    "Board/executive briefing calendar; presentation materials and attendance records; tabletop or scenario exercise evidence involving executives; board acknowledgement of cyber risk appetite",
    "Board members and senior executives must receive periodic cybersecurity briefings appropriate to their governance role, including emerging threats and organisational risk posture.")

# ─── DOMAIN 2: CYBERSECURITY DEFENCE ─────────────────────────────────────────

c_2_1 = control(fid, "2-1", "Asset Management",
    "إدارة الأصول",
    "Maintaining a comprehensive, accurate, and up-to-date inventory of all information assets and associating them with risk-based classification.")

sub(c_2_1, "2-1-1",
    "Information Asset Inventory",
    "جرد أصول المعلومات",
    "Asset inventory register covering hardware, software, data, and cloud assets with owner, classification, location, and criticality; automated discovery tool scan reports; reconciliation evidence between inventory and actual estate; asset owner acknowledgement records",
    "A complete and current inventory of all information assets (hardware, software, data, services) must be maintained with assigned owners.")

sub(c_2_1, "2-1-2",
    "Asset Classification & Labelling",
    "تصنيف الأصول ووسمها",
    "Asset classification policy defining tiers (e.g. Top Secret, Secret, Confidential, Internal, Public); sample assets labelled in inventory per classification; labelling procedure for physical media; classification review records",
    "All information assets must be classified according to a defined sensitivity scale, with physical and digital labelling applied consistently.")

sub(c_2_1, "2-1-3",
    "Asset Lifecycle Management",
    "إدارة دورة حياة الأصول",
    "Asset lifecycle procedure covering procurement, commissioning, change, and secure disposal; disposal certificates for decommissioned hardware; CMDB or asset database change logs; audit of inventory accuracy vs. physical count",
    "Asset records must reflect the full lifecycle from acquisition to secure disposal, with timely updates at each transition.")

c_2_2 = control(fid, "2-2", "Identity & Access Management",
    "إدارة الهوية والوصول",
    "Controlling access to systems and information based on need-to-know, least privilege, and formal provisioning/deprovisioning processes.")

sub(c_2_2, "2-2-1",
    "Access Control Policy & Provisioning Process",
    "سياسة التحكم في الوصول وعملية تزويده",
    "Approved access control policy; formal access request workflow records (ticket system screenshots); approval chain records showing business owner and security team sign-off; SLA compliance metrics for access provisioning",
    "A formal access provisioning process based on need-to-know and least privilege must exist, with documented approval chains for all access grants.")

sub(c_2_2, "2-2-2",
    "Privileged Access Management",
    "إدارة الوصول المتميز",
    "PAM solution deployment evidence; list of privileged accounts with justification for each; privileged access usage logs reviewed by security; CyberArk/BeyondTrust or equivalent tool configuration reports; quarterly privileged account review records",
    "Privileged accounts must be identified, minimised, individually owned, and subject to enhanced controls including session recording and regular review.")

sub(c_2_2, "2-2-3",
    "Multi-Factor Authentication",
    "المصادقة متعددة العوامل",
    "MFA enforcement policy; technical configuration evidence showing MFA enabled for remote access, privileged accounts, and cloud services; authentication logs confirming MFA challenge events; exception register with compensating controls",
    "MFA must be enforced for all remote access, privileged accounts, and access to critical systems and cloud services.")

sub(c_2_2, "2-2-4",
    "User Access Review",
    "مراجعة وصول المستخدمين",
    "Quarterly user access review records signed off by business owners; access certification reports from IAM system; evidence of access revocation following review; over-privileged account remediation records",
    "User access rights must be reviewed and certified by business owners at least quarterly, with unjustified or excess access promptly revoked.")

sub(c_2_2, "2-2-5",
    "Password & Authentication Standards",
    "معايير كلمات المرور والمصادقة",
    "Password policy document (minimum length, complexity, rotation, lockout thresholds); technical configuration evidence from AD/LDAP/IAM confirming policy enforcement; sample lockout-event logs; password manager deployment evidence",
    "Password and authentication standards meeting current best-practice minimum requirements must be technically enforced across all systems.")

c_2_3 = control(fid, "2-3", "Information System & Information Processing Facilities Protection",
    "حماية أنظمة المعلومات ومرافق معالجتها",
    "Protecting information systems and processing environments through hardening, patching, and secure configuration baselines.")

sub(c_2_3, "2-3-1",
    "Secure Configuration & Hardening Baselines",
    "معايير التكوين الآمن وتصليب الأنظمة",
    "Approved hardening baselines per OS/platform (CIS Benchmarks or equivalent); automated compliance scan results (e.g. CIS-CAT, Tenable) showing baseline adherence %; deviation register with compensating controls; baseline review schedule",
    "Secure configuration baselines must be defined, implemented, and technically verified for all operating systems, databases, and platform components.")

sub(c_2_3, "2-3-2",
    "Patch & Vulnerability Remediation Management",
    "إدارة تصحيح الثغرات الأمنية",
    "Patch management policy with SLAs by severity (Critical ≤72h, High ≤30 days); vulnerability scan reports showing patch compliance rate; change records for patches applied; overdue patch register with risk acceptance",
    "A formal patch management process must ensure timely application of security patches with SLAs based on vulnerability severity.")

sub(c_2_3, "2-3-3",
    "Endpoint Protection (AV/EDR)",
    "حماية نقاط النهاية (مضاد الفيروسات / الكشف والاستجابة)",
    "EDR/AV solution deployment coverage report (target ≥98% managed endpoints); signature/definition update frequency logs; detection and response event summary; EDR policy configuration screenshots; excluded-endpoint risk-acceptance records",
    "Anti-malware and endpoint detection and response (EDR) solutions must be deployed on all endpoints with current signatures and active monitoring.")

sub(c_2_3, "2-3-4",
    "Secure Software Development Lifecycle (SDLC)",
    "دورة حياة تطوير البرمجيات الآمنة",
    "Secure SDLC policy; SAST/DAST tool scan results integrated into CI/CD pipeline; developer secure-coding training records; code review checklist with security checks; sample secure code-review records",
    "A secure SDLC must be followed for all internally developed or customised software, including security testing at development and pre-production stages.")

c_2_4 = control(fid, "2-4", "Email Protection",
    "حماية البريد الإلكتروني",
    "Protecting the organisation's email infrastructure from phishing, malware, spoofing, and unauthorised use.")

sub(c_2_4, "2-4-1",
    "Email Security Gateway & Anti-Spam Controls",
    "بوابة أمان البريد الإلكتروني وضوابط مكافحة البريد المزعج",
    "Email security gateway configuration report; blocked/quarantined message statistics (monthly); anti-spam rule-set review records; email filtering coverage confirmation for all inbound/outbound paths",
    "An email security gateway with anti-spam, anti-malware, and content filtering must be deployed and actively monitored.")

sub(c_2_4, "2-4-2",
    "Email Authentication (SPF, DKIM, DMARC)",
    "مصادقة البريد الإلكتروني (SPF و DKIM و DMARC)",
    "DNS records confirming SPF, DKIM, DMARC configuration; DMARC aggregate (rua) reports; DMARC policy set to at least 'quarantine'; periodic DNS health-check records; spoofing attempt logs",
    "SPF, DKIM, and DMARC must be configured for all organisational email domains with DMARC enforcement at minimum quarantine policy.")

sub(c_2_4, "2-4-3",
    "Advanced Anti-Phishing & Sandboxing",
    "الحماية المتقدمة من التصيد الاحتيالي وعزل الملفات المشبوهة",
    "Sandboxing solution deployment evidence; detection rate metrics for phishing and malicious attachments; URL rewriting/detonation configuration records; threat-intelligence feed integration evidence; incident records of phishing attacks detected",
    "Advanced threat protection with URL sandboxing and attachment detonation must be in place to detect and block sophisticated email-based attacks.")

c_2_5 = control(fid, "2-5", "Network Security Management",
    "إدارة أمن الشبكات",
    "Designing, implementing, and maintaining secure network architecture including segmentation, firewalls, and intrusion detection/prevention.")

sub(c_2_5, "2-5-1",
    "Network Segmentation & Zoning",
    "تقسيم الشبكة إلى مناطق أمنية",
    "Network architecture diagram showing security zones (DMZ, internal, OT, management); firewall rule-set review evidencing zone separation; inter-zone traffic flow approval records; periodic segmentation validation test reports",
    "Networks must be segmented into security zones based on data sensitivity and system criticality, with controlled and auditable inter-zone traffic.")

sub(c_2_5, "2-5-2",
    "Firewall & Next-Generation Firewall (NGFW) Management",
    "إدارة جدران الحماية من الجيل التالي",
    "Firewall policy/rule-set review records (semi-annual minimum); rule cleanup evidence (removal of orphaned/overly permissive rules); change management records for firewall rule changes; NGFW configuration baseline report",
    "Firewall rule-sets must be reviewed and cleaned at least semi-annually, with all changes managed through a formal change control process.")

sub(c_2_5, "2-5-3",
    "Intrusion Detection & Prevention Systems (IDS/IPS)",
    "أنظمة كشف التسلل والوقاية منه",
    "IDS/IPS deployment architecture evidence; tuning records showing false-positive reduction; alert and block event summaries; integration evidence with SIEM; signature update frequency logs",
    "IDS/IPS must be deployed at critical network boundaries with current signatures, active monitoring, and integration into the security operations centre.")

sub(c_2_5, "2-5-4",
    "Remote Access Security (VPN / Zero Trust)",
    "أمن الوصول عن بُعد",
    "Remote access solution configuration documentation; MFA enforcement evidence for VPN/remote sessions; split-tunnelling policy and justification; user remote access logs reviewed for anomalies; zero-trust access policy if applicable",
    "All remote access to organisational networks must use encrypted, authenticated connections with MFA and continuous monitoring.")

sub(c_2_5, "2-5-5",
    "Wireless Network Security",
    "أمن الشبكات اللاسلكية",
    "Wireless security policy; Wi-Fi configuration evidence (WPA3 or WPA2-Enterprise, SSID isolation); rogue AP detection scan records; guest network isolation confirmation; wireless penetration test results",
    "Wireless networks must use strong encryption and authentication, with guest and corporate networks isolated and rogue access point detection in place.")

c_2_6 = control(fid, "2-6", "Mobile Devices Security",
    "أمن الأجهزة المحمولة",
    "Managing and securing mobile devices (smartphones, tablets, laptops) that access organisational systems and data.")

sub(c_2_6, "2-6-1",
    "Mobile Device Management (MDM) / UEM Deployment",
    "نشر نظام إدارة الأجهزة المحمولة",
    "MDM/UEM solution enrolment report showing managed device coverage; device compliance policy configuration screenshots; non-compliant device blocking evidence; remote-wipe capability test records",
    "All organisational mobile devices must be enrolled in an MDM/UEM solution with enforced security policies and remote-wipe capability.")

sub(c_2_6, "2-6-2",
    "BYOD Policy & Controls",
    "سياسة الأجهزة الشخصية وضوابطها",
    "BYOD policy document; BYOD enrolment agreement signed by users; containerisation or MAM configuration evidence separating corporate and personal data; periodic BYOD device compliance audit records",
    "A formal BYOD policy must define acceptable use, security requirements, and data separation controls for personal devices accessing corporate resources.")

sub(c_2_6, "2-6-3",
    "Mobile Threat Defence & Application Control",
    "الحماية من التهديدات على الأجهزة المحمولة والتحكم في التطبيقات",
    "Approved application whitelist/blacklist records; mobile threat defence solution deployment evidence; jailbroken/rooted device detection and blocking logs; OS patching compliance report for mobile estate",
    "Mobile threat defence must detect and block malicious apps, jailbroken/rooted devices, and OS-level vulnerabilities on managed devices.")

c_2_7 = control(fid, "2-7", "Data & Information Protection",
    "حماية البيانات والمعلومات",
    "Classifying, handling, and protecting data throughout its lifecycle, including storage, transmission, and disposal.")

sub(c_2_7, "2-7-1",
    "Data Classification & Handling Procedures",
    "تصنيف البيانات وإجراءات التعامل معها",
    "Data classification policy with defined sensitivity tiers; data handling guidelines per classification; data labelling tooling configuration evidence; sample labelled documents/datasets; staff acknowledgement of handling procedures",
    "A data classification policy with defined sensitivity tiers and corresponding handling procedures must be implemented and followed for all organisational data.")

sub(c_2_7, "2-7-2",
    "Data Loss Prevention (DLP)",
    "منع فقدان البيانات",
    "DLP solution deployment scope (endpoint, network, cloud); DLP policy rule-set review; incident/alert log showing DLP events investigated; false-positive tuning records; DLP coverage gap analysis",
    "DLP controls must monitor and prevent unauthorised transfer of sensitive data across all major channels: endpoint, email, web, and cloud.")

sub(c_2_7, "2-7-3",
    "Data Retention & Secure Disposal",
    "الاحتفاظ بالبيانات والتخلص الآمن منها",
    "Data retention schedule aligned to legal/regulatory requirements; secure deletion and overwrite procedure; destruction certificates for physical media and hardware; periodic data purge records with authorisation sign-off",
    "Data retention periods must be defined and enforced, with secure deletion or physical destruction applied at end-of-life.")

sub(c_2_7, "2-7-4",
    "Personal Data Protection (PDPL Alignment)",
    "حماية البيانات الشخصية (التوافق مع نظام حماية البيانات الشخصية)",
    "PDPL compliance assessment report; data processing activities register (Article 18 PDPL); privacy impact assessments for high-risk processing; evidence of data subject rights process; DPO appointment record",
    "Controls protecting personal data must align with Saudi PDPL requirements including lawful basis for processing, data subject rights, and breach notification obligations.")

c_2_8 = control(fid, "2-8", "Cryptography",
    "التشفير",
    "Applying appropriate cryptographic controls to protect data confidentiality and integrity in transit and at rest.")

sub(c_2_8, "2-8-1",
    "Cryptography Policy & Key Management",
    "سياسة التشفير وإدارة المفاتيح",
    "Approved cryptography policy specifying approved algorithms and key lengths (AES-256, RSA-2048+, TLS 1.2+ minimum); key management procedure covering generation, distribution, rotation, revocation, and destruction; HSM deployment evidence; key custodian appointment records",
    "A cryptography policy must define approved algorithms, key lengths, and key management lifecycle procedures, with keys protected by hardware security modules where applicable.")

sub(c_2_8, "2-8-2",
    "Encryption of Data in Transit",
    "تشفير البيانات أثناء النقل",
    "TLS configuration scan results (e.g. SSL Labs A-grade); evidence of TLS 1.2+ enforced on all externally-facing services; internal service-to-service encryption configuration records; VPN encryption algorithm documentation",
    "All sensitive data transmitted over networks must be encrypted using approved protocols and algorithms with weak cipher suites disabled.")

sub(c_2_8, "2-8-3",
    "Encryption of Data at Rest",
    "تشفير البيانات في حالة السكون",
    "Encryption-at-rest configuration evidence for databases, file stores, and backups; BitLocker/FileVault/OS-level encryption reports for endpoints; cloud storage encryption settings; encryption key rotation records",
    "Sensitive data must be encrypted at rest in databases, file systems, backups, and endpoints using approved encryption standards.")

c_2_9 = control(fid, "2-9", "Backup & Recovery Management",
    "إدارة النسخ الاحتياطي والاستعادة",
    "Ensuring data and systems can be reliably recovered following loss, corruption, or a cybersecurity incident.")

sub(c_2_9, "2-9-1",
    "Backup Policy & Schedule",
    "سياسة النسخ الاحتياطي وجدوله الزمني",
    "Approved backup policy specifying RPO/RTO per system tier; backup schedule configuration evidence; backup job success/failure logs (minimum 30-day history); offsite/cloud backup confirmation; backup encryption evidence",
    "A formal backup policy must define RPO/RTO requirements, backup frequency, retention periods, and offsite/encrypted storage for all critical systems.")

sub(c_2_9, "2-9-2",
    "Backup Integrity Testing & Restore Verification",
    "اختبار سلامة النسخ الاحتياطية والتحقق من استعادتها",
    "Quarterly restore test records with results and sign-off; backup verification tool reports confirming data integrity; documented test scenarios matching actual recovery needs; issues identified during testing and remediation records",
    "Backups must be regularly tested by performing actual restore operations, with results documented and issues remediated.")

sub(c_2_9, "2-9-3",
    "Backup Segregation & Immutability",
    "عزل النسخ الاحتياطية وعدم قابليتها للتغيير",
    "Evidence of backup network/storage segregation from production; immutable/WORM storage configuration for critical backups; ransomware protection capability for backup solution; access control records limiting production admin access to backups",
    "Backups must be stored on segregated, access-controlled, and where possible immutable storage to prevent ransomware or malicious deletion.")

c_2_10 = control(fid, "2-10", "Vulnerability Management",
    "إدارة الثغرات الأمنية",
    "Systematically identifying, evaluating, prioritising, and remediating vulnerabilities across the IT estate.")

sub(c_2_10, "2-10-1",
    "Vulnerability Scanning Programme",
    "برنامج المسح الدوري للثغرات الأمنية",
    "Vulnerability scanning policy with scan frequency (critical assets weekly, others monthly minimum); scan tool configuration and coverage reports; authenticated vs unauthenticated scan evidence; asset coverage percentage metric",
    "Regular authenticated vulnerability scans must cover all in-scope assets with defined frequency based on asset criticality.")

sub(c_2_10, "2-10-2",
    "Vulnerability Prioritisation & Remediation SLAs",
    "تحديد أولويات الثغرات ومستويات الخدمة لمعالجتها",
    "Vulnerability remediation SLA matrix by CVSS severity; current open vulnerability report with age tracking; remediation trend metrics showing reduction over time; risk acceptance records for vulnerabilities not remediated within SLA",
    "Identified vulnerabilities must be prioritised by severity with enforced remediation SLAs and exceptions formally risk-accepted.")

sub(c_2_10, "2-10-3",
    "Vulnerability Intelligence & Zero-Day Response",
    "معلومات الثغرات والاستجابة للثغرات غير المعروفة",
    "Threat intelligence feed subscription records; zero-day response procedure with emergency patching process; records of emergency patch deployments; NCA/CERT-SA vulnerability alert response logs",
    "The organisation must subscribe to vulnerability intelligence feeds and maintain an emergency process for rapid response to zero-day and critical vulnerabilities.")

c_2_11 = control(fid, "2-11", "Penetration Testing",
    "اختبار الاختراق",
    "Conducting regular penetration tests to identify exploitable weaknesses before adversaries do.")

sub(c_2_11, "2-11-1",
    "Annual External Penetration Testing",
    "اختبار الاختراق الخارجي السنوي",
    "Scope and rules of engagement document; penetration test report from accredited provider; management response with remediation plan; re-test report confirming critical/high findings remediated; CISO sign-off on final report",
    "External penetration testing of internet-facing systems must be conducted at least annually by a qualified independent provider.")

sub(c_2_11, "2-11-2",
    "Internal Network Penetration Testing",
    "اختبار اختراق الشبكة الداخلية",
    "Internal penetration test scope document; test report with findings; evidence of remediation for critical/high severity findings; re-test confirmation; comparison with previous year's findings showing improvement",
    "Internal network penetration testing must be conducted at least annually to identify insider threat vectors and lateral movement paths.")

sub(c_2_11, "2-11-3",
    "Red Team & Adversary Simulation Exercises",
    "تمارين الفريق الأحمر ومحاكاة المهاجمين",
    "Red team engagement scope and objectives document; red team exercise report (findings, attack paths, detection gaps); blue team/SOC response evaluation; lessons-learned record; executive briefing on outcomes",
    "Advanced adversary simulation (red team) exercises should be conducted periodically to test detection and response capabilities against realistic attack scenarios.")

c_2_12 = control(fid, "2-12", "Cybersecurity Event Logs & Monitoring Management",
    "إدارة سجلات أحداث الأمن السيبراني والرصد",
    "Collecting, retaining, and analysing security logs to detect and investigate cybersecurity incidents.")

sub(c_2_12, "2-12-1",
    "Security Information & Event Management (SIEM)",
    "إدارة المعلومات الأمنية والأحداث",
    "SIEM deployment architecture document; log source coverage report (% of critical assets onboarded); correlation rule-set and tuning records; alert triage metrics (MTTD, MTTR); SIEM capacity and retention configuration",
    "A SIEM must aggregate logs from all critical systems with correlation rules, alerting, and sufficient retention for investigation purposes.")

sub(c_2_12, "2-12-2",
    "Log Collection Standards & Retention",
    "معايير جمع السجلات واحتفاظها",
    "Log collection policy specifying mandatory log sources and minimum 12-month retention; storage sizing evidence; log integrity controls (e.g. write-once, hash verification); sample log completeness verification records",
    "Logs from all critical systems must be collected centrally, retained for a minimum of 12 months, and protected from tampering.")

sub(c_2_12, "2-12-3",
    "Security Operations Centre (SOC) Monitoring",
    "مركز العمليات الأمنية ورصد التهديدات",
    "SOC operational model document (in-house or MSSP); 24×7 monitoring coverage evidence; SLA for alert response times; SOC analyst shift records; escalation procedure and evidence of it being followed",
    "Continuous (24×7) security monitoring must be in place through an in-house or outsourced SOC with defined alert response SLAs.")

sub(c_2_12, "2-12-4",
    "User & Entity Behaviour Analytics (UEBA)",
    "تحليلات سلوك المستخدمين والكيانات",
    "UEBA tool deployment evidence; baseline behaviour model configuration; anomaly detection alert records; insider threat detection case records; UEBA integration with SIEM/SOAR",
    "UEBA capabilities should be deployed to detect anomalous user and system behaviour indicative of insider threats or compromised accounts.")

c_2_13 = control(fid, "2-13", "Cybersecurity Incident & Threat Management",
    "إدارة حوادث وتهديدات الأمن السيبراني",
    "Establishing processes to detect, respond to, contain, eradicate, and recover from cybersecurity incidents.")

sub(c_2_13, "2-13-1",
    "Incident Response Plan & Playbooks",
    "خطة الاستجابة للحوادث والأدلة الإجرائية",
    "Approved incident response plan covering detection, triage, containment, eradication, recovery, and post-incident review; role-specific playbooks (ransomware, DDoS, data breach, insider threat); annual plan review records; distribution evidence to response team",
    "A documented incident response plan and scenario-specific playbooks must exist, be approved, regularly reviewed, and communicated to all response team members.")

sub(c_2_13, "2-13-2",
    "Incident Classification & Severity Triage",
    "تصنيف الحوادث وتحديد خطورتها",
    "Incident classification matrix (P1–P4 or equivalent) with escalation thresholds; sample incident tickets with classification field populated; average classification time metrics; misclassification review records",
    "All security incidents must be classified by severity using a defined matrix, with escalation procedures triggered by severity level.")

sub(c_2_13, "2-13-3",
    "Incident Response Exercises & Tabletop Simulations",
    "تمارين الاستجابة للحوادث ومحاكاة السيناريوهات",
    "Annual tabletop exercise agenda and participant list; exercise scenario and injects document; after-action report with lessons learned; improvement actions register with completion evidence",
    "Incident response exercises (tabletop and/or live-fire) must be conducted at least annually, with lessons learned actioned and tracked.")

sub(c_2_13, "2-13-4",
    "Post-Incident Review & Continuous Improvement",
    "مراجعة ما بعد الحادثة والتحسين المستمر",
    "Post-incident review reports for all P1/P2 incidents; root cause analysis documentation; corrective action register with owners and due dates; evidence of corrective actions completed; CISO sign-off on closure",
    "All significant incidents must undergo a formal post-incident review with root cause analysis, corrective actions, and tracking to closure.")

sub(c_2_13, "2-13-5",
    "Threat Intelligence Programme",
    "برنامج معلومات التهديدات السيبرانية",
    "Threat intelligence platform or feed subscription records; threat intel analyst role definition; sample threat intelligence reports produced/consumed; integration with SIEM/SOAR for automated indicator ingestion; ISAC membership records (e.g. FS-ISAC)",
    "A threat intelligence programme must collect, analyse, and distribute actionable threat intelligence to inform defensive decisions.")

c_2_14 = control(fid, "2-14", "Physical Security",
    "الأمن المادي",
    "Protecting physical access to data centres, server rooms, and offices that house information processing facilities.")

sub(c_2_14, "2-14-1",
    "Physical Access Controls for Critical Facilities",
    "ضوابط الدخول المادي للمرافق الحرجة",
    "Data centre and server room access control system configuration (badge readers, biometrics); access list showing authorised individuals and justification; access log reports reviewed monthly; unauthorised access alarm records",
    "Physical access to data centres and server rooms must be restricted to authorised personnel via multi-factor physical access controls with access logs retained.")

sub(c_2_14, "2-14-2",
    "CCTV & Physical Security Monitoring",
    "المراقبة بالكاميرات والأمن المادي",
    "CCTV coverage map for critical facilities; recording retention configuration (minimum 90 days); periodic CCTV footage review records; alarm response procedure and test records; security guard post orders",
    "CCTV surveillance must cover all critical facility entry/exit points with sufficient retention and active monitoring.")

sub(c_2_14, "2-14-3",
    "Environmental Controls in Data Centres",
    "ضوابط البيئة المادية في مراكز البيانات",
    "Data centre environmental monitoring system configuration (temperature, humidity, fire suppression, flood sensors); alert threshold configuration; maintenance records for UPS, HVAC, and fire systems; environmental incident log",
    "Environmental controls (cooling, power, fire suppression, flood detection) must be in place, monitored, and regularly tested in all data centre facilities.")

c_2_15 = control(fid, "2-15", "Web Application Security",
    "أمن تطبيقات الويب",
    "Protecting web applications from exploitation through secure development, testing, and runtime protection controls.")

sub(c_2_15, "2-15-1",
    "Web Application Firewall (WAF) Deployment",
    "نشر جدار حماية تطبيقات الويب",
    "WAF deployment evidence for all public-facing web applications; WAF rule-set review and tuning records; blocked attack event statistics; false-positive reduction records; WAF in enforcement (not detection-only) mode confirmation",
    "A WAF must be deployed in enforcement mode in front of all public-facing web applications with actively maintained and tuned rule-sets.")

sub(c_2_15, "2-15-2",
    "OWASP Top 10 Vulnerability Assessment",
    "تقييم الثغرات وفق قائمة OWASP العشرة الأولى",
    "DAST scan results against OWASP Top 10 for all web applications; manual assessment report (where automated tools insufficient); remediation records for critical/high findings; developer OWASP training completion records",
    "Web applications must be assessed against the OWASP Top 10 vulnerabilities with identified issues remediated before deployment and re-assessed annually.")

sub(c_2_15, "2-15-3",
    "API Security Controls",
    "ضوابط أمن واجهات برمجة التطبيقات",
    "API inventory register; API gateway configuration evidence (authentication, rate limiting, input validation); API security scan results; OAuth 2.0 / JWT implementation review; API change management records",
    "All APIs must be inventoried, authenticated, rate-limited, and scanned for vulnerabilities with changes subject to security review.")

# ─── DOMAIN 3: CYBERSECURITY RESILIENCE ──────────────────────────────────────

c_3_1 = control(fid, "3-1", "Cybersecurity Resilience Aspects of Business Continuity Management",
    "جوانب الأمن السيبراني في إدارة استمرارية الأعمال",
    "Embedding cybersecurity resilience into BCM processes to ensure critical services can withstand and recover from cyber disruptions.")

sub(c_3_1, "3-1-1",
    "Cyber-Specific Business Impact Analysis",
    "تحليل الأثر على الأعمال المتعلق بالأمن السيبراني",
    "BIA document with cyber threat scenarios incorporated; cyber-specific impact criteria (data loss, service outage, reputational harm); RTO/RPO defined per critical system from cyber perspective; BIA review records (annual minimum)",
    "A Business Impact Analysis addressing cyber-specific threats and scenarios must be conducted and maintained, with RTO/RPO defined for critical systems against cyber disruption.")

sub(c_3_1, "3-1-2",
    "Cyber Incident Business Continuity Plan",
    "خطة استمرارية الأعمال لحوادث الأمن السيبراني",
    "BCP/DRP with dedicated cyber incident annex or cyber-scenario procedures; manual workaround procedures for critical systems during cyber outage; cross-reference between incident response plan and BCP; BCP owner and version-control records",
    "Business continuity plans must include specific procedures for operating during and recovering from major cybersecurity incidents, including manual fallback processes.")

sub(c_3_1, "3-1-3",
    "Cyber Resilience Testing & Exercises",
    "اختبار المرونة السيبرانية وتمارينها",
    "Cyber resilience exercise schedule; tabletop exercise records with cyber disruption scenarios (ransomware, DDoS, data centre loss); full BCP/DR test records; lessons-learned register; improvement actions with completion evidence",
    "Cyber resilience must be validated through regular exercises including scenarios that simulate significant cybersecurity disruptions, with findings integrated back into plans.")

sub(c_3_1, "3-1-4",
    "Crisis Communication Plan for Cyber Incidents",
    "خطة التواصل في أزمات الأمن السيبراني",
    "Crisis communication plan document with pre-approved messaging templates; stakeholder communication matrix (staff, customers, regulators, media); communication exercise records; spokesperson designation and training evidence",
    "A crisis communication plan must define messaging, stakeholders, channels, and responsibilities for communicating during significant cybersecurity incidents.")

# ─── DOMAIN 4: THIRD-PARTY & CLOUD COMPUTING CYBERSECURITY ───────────────────

c_4_1 = control(fid, "4-1", "Third-Party Cybersecurity",
    "الأمن السيبراني لأطراف ثالثة",
    "Managing cybersecurity risks arising from third-party suppliers, vendors, and service providers with access to organisational systems or data.")

sub(c_4_1, "4-1-1",
    "Third-Party Cybersecurity Risk Assessment",
    "تقييم مخاطر الأمن السيبراني للأطراف الثالثة",
    "Third-party risk assessment methodology document; completed risk assessment records for all critical suppliers; risk-tiering matrix (critical/high/medium/low); assessment review cycle records; assessments triggered by supplier changes",
    "All third parties with access to organisational systems or data must undergo a cybersecurity risk assessment before onboarding, with periodic reassessment based on risk tier.")

sub(c_4_1, "4-1-2",
    "Cybersecurity Requirements in Third-Party Contracts",
    "متطلبات الأمن السيبراني في عقود الأطراف الثالثة",
    "Standard contract clause library for cybersecurity requirements; sample executed contracts showing cybersecurity obligations; right-to-audit clauses evidence; incident notification obligation in contracts; legal review confirmation of clause adequacy",
    "All contracts with third parties accessing sensitive systems or data must include mandatory cybersecurity requirements, right-to-audit, and incident notification obligations.")

sub(c_4_1, "4-1-3",
    "Ongoing Third-Party Cybersecurity Monitoring",
    "الرصد المستمر لالأمن السيبراني للأطراف الثالثة",
    "Third-party monitoring schedule and methodology; annual security questionnaire responses and review records; third-party audit or assessment reports; corrective action records for non-compliant suppliers; supplier termination process for persistent non-compliance",
    "Third-party cybersecurity compliance must be monitored on an ongoing basis through questionnaires, audits, or independent assessments, with non-compliance escalated and tracked.")

sub(c_4_1, "4-1-4",
    "Third-Party Access Controls",
    "ضوابط وصول الأطراف الثالثة",
    "Just-in-time access provisioning records for third-party sessions; third-party access review records (at least quarterly); VPN/jump server configuration for third-party remote access; session recording evidence for privileged third-party access",
    "Third-party access to systems must be time-limited, need-to-know, and subject to enhanced monitoring including session recording for privileged access.")

c_4_2 = control(fid, "4-2", "Cloud Computing & Hosting Cybersecurity",
    "الأمن السيبراني للحوسبة السحابية والاستضافة",
    "Managing cybersecurity risks associated with cloud services including SaaS, PaaS, IaaS, and hosted environments.")

sub(c_4_2, "4-2-1",
    "Cloud Security Risk Assessment & Approval",
    "تقييم مخاطر الأمن السيبراني للخدمات السحابية واعتمادها",
    "Cloud adoption risk assessment methodology; completed risk assessments for all cloud services in use; CISO and data protection officer approval records for cloud deployments; NCA cloud guidelines compliance evidence; data residency confirmation for sensitive data",
    "All cloud services must undergo a security risk assessment and formal approval before adoption, with data residency and sovereignty requirements verified.")

sub(c_4_2, "4-2-2",
    "Cloud Shared Responsibility Model Implementation",
    "تطبيق نموذج المسؤولية المشتركة في السحابة",
    "Shared responsibility matrix for each cloud service (IaaS/PaaS/SaaS); documented customer-side security controls per cloud provider; gap analysis between CSP baseline and organisational requirements; cloud security architecture diagram",
    "The shared responsibility boundaries with each cloud provider must be documented, with all customer-side responsibilities formally assigned and implemented.")

sub(c_4_2, "4-2-3",
    "Cloud Security Configuration & Posture Management (CSPM)",
    "إدارة تكوين الأمن السيبراني في السحابة ووضعه",
    "CSPM tool deployment evidence; cloud security benchmark compliance score (CIS Cloud Benchmarks); misconfiguration alert records and remediation tracking; multi-account/multi-cloud coverage report; cloud resource inventory completeness",
    "A CSPM tool must continuously monitor cloud environments for misconfigurations against security benchmarks with findings tracked to remediation.")

sub(c_4_2, "4-2-4",
    "Cloud Data Protection & Encryption",
    "حماية البيانات في السحابة وتشفيرها",
    "Cloud storage encryption configuration evidence (customer-managed keys preferred); data classification applied to cloud-stored data; DLP policies active on cloud platforms; data exfiltration monitoring records; cloud backup encryption evidence",
    "All sensitive data in cloud environments must be encrypted using customer-managed keys where possible, with DLP and data classification controls active.")

sub(c_4_2, "4-2-5",
    "Cloud Access Security Broker (CASB) / Shadow IT Controls",
    "وسيط أمان الوصول للسحابة والتحكم في تقنية المعلومات غير المعتمدة",
    "CASB solution deployment evidence; unsanctioned cloud app discovery report (shadow IT); cloud app risk assessment and approval/block records; cloud usage policy and enforcement evidence; CASB integration with identity provider",
    "A CASB or equivalent control must detect and manage unsanctioned cloud service use, with approved cloud apps inventoried and unapproved services blocked or restricted.")

# ─── DOMAIN 5: ICS CYBERSECURITY ─────────────────────────────────────────────

c_5_1 = control(fid, "5-1", "Industrial Control Systems (ICS) Protection",
    "حماية أنظمة التحكم الصناعية",
    "Securing industrial control systems, operational technology (OT), and SCADA environments from cyber threats while maintaining operational safety and continuity.")

sub(c_5_1, "5-1-1",
    "ICS Asset Inventory & Classification",
    "جرد أصول أنظمة التحكم الصناعية وتصنيفها",
    "Dedicated ICS/OT asset inventory separate from IT inventory; asset classification by criticality and safety impact; passive asset discovery tool results (e.g. Claroty, Dragos, Nozomi); asset owner assignment records; vendor/model/firmware version tracking",
    "A dedicated inventory of all ICS/OT assets must be maintained, classified by safety criticality, with firmware versions tracked for vulnerability management.")

sub(c_5_1, "5-1-2",
    "IT/OT Network Segmentation",
    "الفصل الشبكي بين بيئات تقنية المعلومات والتقنيات التشغيلية",
    "Network architecture diagram confirming IT/OT segmentation (air gap or industrial DMZ); firewall rule-set between IT and OT zones; unidirectional gateway (data diode) deployment evidence where required; inter-zone traffic monitoring records",
    "IT and OT/ICS networks must be strictly segmented using industrial DMZ or air-gap architecture, with all inter-zone traffic controlled, logged, and minimised.")

sub(c_5_1, "5-1-3",
    "ICS-Specific Vulnerability & Patch Management",
    "إدارة الثغرات والتصحيحات الخاصة بأنظمة التحكم الصناعية",
    "ICS vulnerability scanning results using passive/safe scanning tools; ICS-specific CVE tracking (ICS-CERT advisories); vendor-coordinated patching records with change control; unpatched ICS system compensating controls documentation; maintenance window scheduling records",
    "ICS vulnerabilities must be identified using safe/passive scanning and mitigated through vendor-coordinated patches or documented compensating controls given operational constraints.")

sub(c_5_1, "5-1-4",
    "ICS Incident Response Plan",
    "خطة الاستجابة للحوادث في أنظمة التحكم الصناعية",
    "ICS-specific incident response plan or annex to the main IRP; OT/ICS incident classification criteria; ICS response team roster with OT vendor contacts; integration between ICS IRP and plant emergency response plan; ICS incident response exercise records",
    "A dedicated ICS incident response plan must exist, tested regularly, with defined escalation to safety/operations teams and coordination with equipment vendors.")

sub(c_5_1, "5-1-5",
    "ICS Remote Access Security",
    "أمن الوصول عن بُعد لأنظمة التحكم الصناعية",
    "ICS remote access policy restricting access to pre-approved IP ranges and personnel; MFA enforcement for all ICS remote access; vendor remote access session monitoring and recording; time-limited access token records; emergency remote access procedure",
    "Remote access to ICS/OT environments must be strictly controlled, authenticated with MFA, time-limited, monitored with session recording, and subject to explicit approval for each session.")

# ─── COMMIT ───────────────────────────────────────────────────────────────────
conn.commit()

controls_count = 26   # number of control() rows (subdomains)
subs_count = 106      # number of sub() rows (main controls)
print(f"[ECC-2:2024] Seed complete: 1 framework | {controls_count} controls | {subs_count} sub-controls loaded.")
