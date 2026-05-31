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

# ---------------------------------------------------------------------------
# FRAMEWORK
# ---------------------------------------------------------------------------
fid = framework(
    "CCC-1:2019",
    "Critical Systems Cybersecurity Controls",
    "ضوابط الأمن السيبراني للأنظمة الحساسة",
    "2019",
    "NCA",
    "Saudi NCA mandatory framework that extends ECC-1:2018 with heightened rigour for "
    "nationally critical systems (financial infrastructure, energy, telecom, government "
    "services). Introduces stricter network segregation, privileged-access controls, "
    "offline backup mandates, out-of-band management, and supply-chain security "
    "requirements that exceed ECC baseline controls. "
    "— practitioner SME mapping; structure aligned to the published framework, "
    "control text paraphrased (not verbatim)."
)

ctrl_count = 0
sub_count  = 0

# ---------------------------------------------------------------------------
# DOMAIN 1 — CYBERSECURITY GOVERNANCE
# ---------------------------------------------------------------------------

# 1-1  Cybersecurity Strategy
c = control(fid, "1-1", "Cybersecurity Strategy for Critical Systems",
            "استراتيجية الأمن السيبراني للأنظمة الحساسة",
            "Establishes a board-approved, risk-driven cybersecurity strategy specifically covering critical systems with defined objectives, ownership, and funding aligned to national security priorities.")
ctrl_count += 1

sub(c, "1-1-1", "Board-Approved Cybersecurity Strategy",
    "استراتيجية أمن سيبراني معتمدة من مجلس الإدارة",
    "Board resolution approving the cybersecurity strategy for critical systems; strategy document with sign-off date; annual review minutes; alignment mapping to national cybersecurity strategy objectives",
    "A formally ratified strategy document for critical-system cybersecurity must exist, approved at board level, reviewed at least annually, and linked explicitly to NCA national objectives.")
sub_count += 1

sub(c, "1-1-2", "Cybersecurity Budget and Resource Allocation for Critical Systems",
    "ميزانية الأمن السيبراني وتخصيص الموارد للأنظمة الحساسة",
    "Approved annual cybersecurity budget line item; headcount plan for critical-system security roles; evidence that budget requests align to risk register priorities; board/executive approval of allocation",
    "Dedicated, separately tracked budget and staffing must be allocated to critical-system cybersecurity, with documented justification tied to the risk register.")
sub_count += 1

sub(c, "1-1-3", "Cybersecurity Objectives and KPIs for Critical Systems",
    "أهداف ومؤشرات أداء الأمن السيبراني للأنظمة الحساسة",
    "KPI dashboard with baseline, target, and actual metrics for critical systems; quarterly review reports presented to senior management; evidence of corrective actions where targets are missed",
    "Measurable KPIs (e.g., mean-time-to-patch, incident dwell time, control coverage) must be defined and tracked with formal management review cadence.")
sub_count += 1

# 1-2  Cybersecurity Management
c = control(fid, "1-2", "Cybersecurity Management Program",
            "برنامج إدارة الأمن السيبراني",
            "Defines the operational program that translates strategy into day-to-day governance: ownership, accountability structures, reporting lines, and integration with enterprise risk management.")
ctrl_count += 1

sub(c, "1-2-1", "Cybersecurity Management Roles and Accountability",
    "أدوار إدارة الأمن السيبراني والمساءلة",
    "Organizational chart showing CISO/cybersecurity function; role descriptions for all critical-system security roles; documented delegation of authority; evidence of security steering committee or equivalent governance body",
    "Designated roles with formal accountability for critical-system cybersecurity must be documented and communicated organisation-wide.")
sub_count += 1

sub(c, "1-2-2", "Cybersecurity Reporting to Executive Management",
    "تقارير الأمن السيبراني لإدارة الوحدة التنظيمية",
    "Management pack templates used for executive reporting; samples of quarterly cybersecurity reports including critical-system KPIs; board/executive sign-off or acknowledgement records",
    "Regular formal reporting (at minimum quarterly) on critical-system cybersecurity posture must reach senior/executive management with documented acknowledgement.")
sub_count += 1

sub(c, "1-2-3", "Integration with Enterprise Risk Management",
    "التكامل مع إدارة مخاطر المؤسسة",
    "Risk register entries for critical systems visible in the enterprise risk register; evidence of risk escalation and treatment decisions; mapping between cyber risks and operational/strategic risks",
    "Cybersecurity risks for critical systems must be formally registered, rated, owned, and escalated within the organisation's enterprise risk management framework.")
sub_count += 1

# 1-3  Cybersecurity Policies for Critical Systems
c = control(fid, "1-3", "Cybersecurity Policies and Standards for Critical Systems",
            "سياسات ومعايير الأمن السيبراني للأنظمة الحساسة",
            "Ensures that documented, approved, and communicated policies govern the protection of critical systems, with mandatory exception and waiver controls.")
ctrl_count += 1

sub(c, "1-3-1", "Policy Framework Covering Critical Systems",
    "إطار السياسات الذي يشمل الأنظمة الحساسة",
    "Policy inventory listing all applicable policies for critical systems with version, owner, and next-review date; approval records; evidence of annual review cycle completion",
    "A complete, versioned set of cybersecurity policies explicitly addressing critical systems must exist, be formally approved, and be reviewed at least annually.")
sub_count += 1

sub(c, "1-3-2", "Policy Exception and Waiver Management",
    "إدارة الاستثناءات والإعفاءات من السياسات",
    "Exception/waiver register for critical-system policy deviations; risk acceptance approvals at appropriate authority level; compensating control documentation; time-bounded waiver records with closure evidence",
    "Any deviation from mandatory cybersecurity policies for critical systems must follow a formal exception process with documented risk acceptance and compensating controls.")
sub_count += 1

sub(c, "1-3-3", "Policy Compliance Monitoring",
    "رصد الامتثال للسياسات",
    "Compliance monitoring schedule and reports; evidence of automated policy checks (e.g., SIEM rules, configuration audits); non-compliance findings log with remediation tracking",
    "Regular monitoring of policy compliance for critical systems must be performed with findings tracked to closure.")
sub_count += 1

# 1-4  Cybersecurity Roles and Responsibilities
c = control(fid, "1-4", "Cybersecurity Roles and Responsibilities",
            "أدوار ومسؤوليات الأمن السيبراني",
            "Defines and formally assigns cybersecurity roles, responsibilities, and authority for all staff and contractors with access to critical systems.")
ctrl_count += 1

sub(c, "1-4-1", "Defined Cybersecurity Roles for Critical Systems",
    "تحديد أدوار الأمن السيبراني للأنظمة الحساسة",
    "Job descriptions/role profiles for all cybersecurity positions covering critical systems; RACI matrix for critical-system security functions; evidence of role-based training completion",
    "All roles with cybersecurity responsibility over critical systems must have formally documented responsibilities and authority levels.")
sub_count += 1

sub(c, "1-4-2", "Separation of Duties for Critical Systems",
    "فصل المهام للأنظمة الحساسة",
    "Separation-of-duties matrix for critical-system administrative functions; evidence of access reviews confirming no conflicting role assignments; compensating monitoring controls where SoD conflicts cannot be eliminated",
    "Incompatible duties (e.g., change approval and implementation, security monitoring and system administration) must be separated for critical systems.")
sub_count += 1

# 1-5  Cybersecurity Risk Management for Critical Systems
c = control(fid, "1-5", "Cybersecurity Risk Management for Critical Systems",
            "إدارة مخاطر الأمن السيبراني للأنظمة الحساسة",
            "Mandates a structured, repeatable risk management lifecycle for critical systems with formal risk identification, assessment, treatment, acceptance, and continuous review.")
ctrl_count += 1

sub(c, "1-5-1", "Critical System Asset Risk Assessment",
    "تقييم مخاطر أصول الأنظمة الحساسة",
    "Risk assessment methodology document; completed risk assessments for all critical systems with likelihood/impact ratings; risk treatment plans with owners and due dates; evidence of risk acceptance sign-offs",
    "Formal risk assessments must be conducted for every critical system at least annually and after any significant change, with results documented and treated.")
sub_count += 1

sub(c, "1-5-2", "Threat Intelligence Integration into Risk Management",
    "دمج المعلومات الاستخباراتية عن التهديدات في إدارة المخاطر",
    "Threat intelligence subscriptions or NCA/CERT feeds evidence; documented process for translating threat intel into risk register updates; board-pack sections referencing current threat landscape for critical systems",
    "Current threat intelligence relevant to critical national systems must feed into the risk assessment and be reflected in treatment prioritisation.")
sub_count += 1

sub(c, "1-5-3", "Risk Acceptance and Residual Risk Sign-off",
    "قبول المخاطر والموافقة على المخاطر المتبقية",
    "Residual risk register with formal acceptance records signed by asset owners and senior management; evidence that accepted risks are reviewed at least annually; escalation records for risks exceeding appetite",
    "Residual cybersecurity risk for critical systems must be explicitly accepted in writing at the appropriate authority level and revisited on a defined schedule.")
sub_count += 1

# 1-6  Cybersecurity in Change and Project Management
c = control(fid, "1-6", "Cybersecurity in Change and Project Management",
            "الأمن السيبراني في إدارة التغييرات والمشاريع",
            "Embeds cybersecurity requirements into all change and project lifecycles affecting critical systems, including mandatory security review gates, change-freeze periods, and post-implementation assessments.")
ctrl_count += 1

sub(c, "1-6-1", "Security Review Gate in Change Management",
    "بوابة مراجعة أمنية في إدارة التغييرات",
    "Change management policy/procedure showing mandatory security review step; CAB minutes evidencing security sign-off for critical-system changes; rejected change records where security review failed; change-freeze calendar and approval records",
    "All changes to critical systems must pass a formal cybersecurity review gate before approval; change-freeze periods during peak operational windows must be documented and enforced.")
sub_count += 1

sub(c, "1-6-2", "Security Requirements in Project Lifecycles",
    "متطلبات الأمن السيبراني في دورات حياة المشاريع",
    "Project charter templates showing mandatory security requirements section; security architecture review records for projects touching critical systems; penetration test reports before go-live; lessons-learned security entries",
    "Projects delivering changes to critical systems must include security requirements from initiation, with architecture reviews and pre-production security testing as mandatory gates.")
sub_count += 1

sub(c, "1-6-3", "Post-Implementation Security Review",
    "مراجعة أمنية بعد التنفيذ",
    "Post-implementation review (PIR) reports for critical-system changes including security findings; evidence that identified gaps are tracked in the risk/remediation register; baseline configuration snapshots taken post-implementation",
    "After deployment to critical systems, a security review must confirm controls are operating as designed and any deviations are formally risk-accepted or remediated.")
sub_count += 1

# 1-7  Periodical Review and Audit
c = control(fid, "1-7", "Periodical Cybersecurity Review and Audit",
            "المراجعة الدورية والتدقيق السيبراني",
            "Requires scheduled internal and independent audits of cybersecurity controls for critical systems, with findings tracked, remediated, and reported to leadership.")
ctrl_count += 1

sub(c, "1-7-1", "Annual Internal Cybersecurity Audit of Critical Systems",
    "التدقيق الداخلي السنوي للأمن السيبراني للأنظمة الحساسة",
    "Internal audit plan covering critical systems; completed audit reports with findings and risk ratings; management responses and remediation plans; evidence of closure verification for high/critical findings",
    "An internal cybersecurity audit of critical systems must be conducted at least annually, with findings formally reported to senior management and tracked to closure.")
sub_count += 1

sub(c, "1-7-2", "Independent Third-Party Cybersecurity Assessment",
    "تقييم مستقل من طرف ثالث للأمن السيبراني",
    "Engagement letter and scope for third-party assessment; assessment report with independent findings; management action plan and executive summary presented to board/senior management; evidence of at least bi-annual independent assessment",
    "An independent external cybersecurity assessment of critical systems must be performed at least every two years, with results presented to the board or equivalent governance body.")
sub_count += 1

sub(c, "1-7-3", "Compliance Review Against NCA Requirements",
    "مراجعة الامتثال لمتطلبات الهيئة الوطنية للأمن السيبراني",
    "NCA CCC compliance self-assessment workbook; evidence of completion and submission/reporting as required; gap analysis and remediation roadmap; version-controlled compliance tracking register",
    "A structured compliance review against NCA CCC requirements must be performed at least annually, with gaps documented, remediated, and tracked.")
sub_count += 1

# 1-8  HR Cybersecurity
c = control(fid, "1-8", "Human Resources Cybersecurity",
            "الأمن السيبراني للموارد البشرية",
            "Addresses pre-employment screening, contractual security obligations, access revocation on departure, and disciplinary frameworks for critical-system personnel.")
ctrl_count += 1

sub(c, "1-8-1", "Pre-Employment Background Screening for Critical-System Staff",
    "الفحص الأمني للموظفين قبل التوظيف للعاملين على الأنظمة الحساسة",
    "HR screening policy referencing critical systems; background check completion records for all staff with access to critical systems; evidence of enhanced screening (criminal, financial, national security) for privileged roles; vendor/contractor screening evidence",
    "All personnel with access to critical systems must undergo enhanced background screening commensurate with the sensitivity of the system before access is granted.")
sub_count += 1

sub(c, "1-8-2", "Security Obligations in Employment Contracts and NDAs",
    "الالتزامات الأمنية في عقود العمل واتفاقيات السرية",
    "Sample employment contract and NDA referencing cybersecurity obligations for critical-system access; evidence all critical-system staff have signed current NDA; disciplinary policy referencing security violations",
    "Employment contracts and NDAs for all critical-system staff must explicitly address cybersecurity responsibilities, data handling obligations, and disciplinary consequences for violations.")
sub_count += 1

sub(c, "1-8-3", "Timely Access Revocation on Termination or Role Change",
    "إلغاء الوصول في الوقت المناسب عند انهاء الخدمة أو تغيير المهام",
    "Offboarding checklist showing same-day or immediate revocation of critical-system access on termination; IAM evidence of account disablement timestamps relative to HR termination dates; periodic leavers reconciliation report",
    "Access to critical systems must be revoked immediately upon termination or role change, with documented evidence of timely execution and periodic reconciliation.")
sub_count += 1

# 1-9  Cybersecurity Awareness and Training
c = control(fid, "1-9", "Cybersecurity Awareness and Training",
            "التوعية والتدريب في الأمن السيبراني",
            "Mandates role-based security awareness and specialist training for all staff and contractors interacting with critical systems, with verified completion tracking.")
ctrl_count += 1

sub(c, "1-9-1", "Mandatory Cybersecurity Awareness for Critical-System Users",
    "التوعية الإلزامية بالأمن السيبراني لمستخدمي الأنظمة الحساسة",
    "Annual awareness training completion reports showing 100% of critical-system users; training content covering threats relevant to critical national infrastructure; phishing simulation results and trend tracking; non-completion escalation evidence",
    "All users of critical systems must complete mandatory annual cybersecurity awareness training with documented completion rates and management follow-up for non-completion.")
sub_count += 1

sub(c, "1-9-2", "Specialist Cybersecurity Training for Critical-System Security Staff",
    "تدريب متخصص في الأمن السيبراني لموظفي أمن الأنظمة الحساسة",
    "Training plans for cybersecurity roles; certification records (e.g., CISSP, CISM, CEH, or NCA-approved); CPD hours tracked per security professional; specialist training budget evidence",
    "Cybersecurity professionals responsible for critical systems must maintain current specialist certifications or complete structured specialist training, with competency evidence retained.")
sub_count += 1

sub(c, "1-9-3", "Social Engineering and Targeted Attack Awareness",
    "التوعية بالهندسة الاجتماعية والهجمات الموجهة",
    "Targeted awareness content for privileged users on spear-phishing, vishing, and supply-chain social engineering; evidence of recurring simulations with privileged-user targeting; results reported to management",
    "Privileged-access users for critical systems must receive enhanced awareness covering advanced social engineering and targeted attack vectors, with simulation evidence.")
sub_count += 1

# ---------------------------------------------------------------------------
# DOMAIN 2 — CYBERSECURITY DEFENCE
# ---------------------------------------------------------------------------

# 2-1  Asset Management for Critical Systems
c = control(fid, "2-1", "Asset Management for Critical Systems",
            "إدارة أصول الأنظمة الحساسة",
            "Requires a comprehensive, maintained inventory of all critical-system assets (hardware, software, data, connections) with classification, ownership, and lifecycle tracking.")
ctrl_count += 1

sub(c, "2-1-1", "Comprehensive Critical-System Asset Inventory",
    "جرد شامل لأصول الأنظمة الحساسة",
    "Asset management system export showing all critical-system hardware and software assets with owner, classification, location, and lifecycle status; evidence of automated discovery tool output reconciled against manual inventory; quarterly reconciliation reports",
    "A complete, authoritative inventory of all critical-system assets must be maintained, automatically discovered where possible, and reconciled at least quarterly.")
sub_count += 1

sub(c, "2-1-2", "Asset Classification and Criticality Rating",
    "تصنيف الأصول وتقييم الأهمية الحساسة",
    "Asset classification policy defining criticality tiers; classification labels/tags applied in asset register; evidence of criticality rating reviews; alignment mapping between asset criticality and protective control selection",
    "All critical-system assets must be classified by criticality and sensitivity, with classifications documented, approved, and used to drive proportionate protective controls.")
sub_count += 1

sub(c, "2-1-3", "Unauthorised Asset Detection and Response",
    "اكتشاف الأصول غير المصرح بها والاستجابة لها",
    "Network Access Control (NAC) or 802.1X configuration evidence; alerts/logs for unauthorised device attempts on critical-system networks; documented response procedure for rogue asset detection; CMDB discrepancy reports and resolution records",
    "Mechanisms must be in place to detect and alert on unauthorised assets connecting to critical-system environments, with documented response and remediation.")
sub_count += 1

# 2-2  Identity and Access Management (IAM)
c = control(fid, "2-2", "Identity and Access Management for Critical Systems",
            "إدارة الهويات وصلاحيات الوصول للأنظمة الحساسة",
            "Enforces strict identity assurance, least-privilege access, privileged-access management, and multi-factor authentication for all users and services accessing critical systems.")
ctrl_count += 1

sub(c, "2-2-1", "Privileged Access Management (PAM) for Critical Systems",
    "إدارة الوصول المميز للأنظمة الحساسة",
    "PAM solution deployment evidence (e.g., CyberArk, BeyondTrust vault screenshots); privileged account inventory showing all admin/service accounts for critical systems; session recording configuration and sample session logs; just-in-time access provisioning records",
    "All privileged access to critical systems must be managed through a PAM solution with vaulted credentials, session recording, and just-in-time provisioning. Shared/generic admin accounts must be prohibited or mitigated.")
sub_count += 1

sub(c, "2-2-2", "Multi-Factor Authentication for Critical-System Access",
    "المصادقة متعددة العوامل للوصول إلى الأنظمة الحساسة",
    "MFA policy requiring MFA for all remote and privileged access to critical systems; IAM system configuration showing MFA enforcement; MFA exception register with risk acceptance; authentication logs confirming MFA events",
    "Multi-factor authentication must be enforced for all remote access and all privileged access to critical systems without exception; any deviation requires documented compensating controls.")
sub_count += 1

sub(c, "2-2-3", "Least-Privilege Access Reviews for Critical Systems",
    "مراجعات الوصول بمبدأ الامتياز الأدنى للأنظمة الحساسة",
    "Semi-annual access review reports for all critical systems showing approvals, revocations, and escalations; evidence of recertification workflow completion; comparison of access granted vs. access required (role alignment analysis)",
    "Formal access reviews for critical systems must be conducted at least semi-annually, with access revoked promptly where no business need is demonstrated.")
sub_count += 1

sub(c, "2-2-4", "Service and Application Account Governance",
    "حوكمة حسابات الخدمات والتطبيقات",
    "Inventory of all service/application accounts with documented owners and business justification; evidence of strong unique passwords or managed secrets for service accounts; periodic review of service account privileges; decommission evidence for stale service accounts",
    "Service and application accounts accessing critical systems must be inventoried, owned, use managed secrets, and be reviewed periodically for continued necessity and least privilege.")
sub_count += 1

# 2-3  System Protection and Hardening
c = control(fid, "2-3", "System Protection and Hardening for Critical Systems",
            "حماية الأنظمة الحساسة وتعزيزها",
            "Mandates the use of approved hardened golden images, configuration baselines, endpoint protection, and change-controlled system configurations for all critical systems.")
ctrl_count += 1

sub(c, "2-3-1", "Hardened Golden Image Baseline Management",
    "إدارة صور النظام المعززة الأساسية",
    "Golden image library with version history and approval records; CIS Benchmark or equivalent hardening guide applied; comparison report between deployed systems and golden-image baseline (configuration drift report); process for updating and re-approving golden images",
    "All critical-system server and endpoint builds must be based on approved hardened golden images with documented CIS/vendor hardening applied; configuration drift must be detected and remediated.")
sub_count += 1

sub(c, "2-3-2", "Endpoint Detection and Response (EDR) for Critical Systems",
    "الكشف عن التهديدات والاستجابة لها على الأجهزة الطرفية للأنظمة الحساسة",
    "EDR solution deployment report showing coverage percentage of critical-system endpoints; EDR policy configuration (real-time protection, isolation capability); sample detections and response actions; exclusion register reviewed for appropriateness",
    "EDR solutions must be deployed on all critical-system endpoints with real-time threat detection, automated isolation capability, and documented coverage gaps managed through compensating controls.")
sub_count += 1

sub(c, "2-3-3", "Application Whitelisting on Critical Systems",
    "القائمة البيضاء للتطبيقات على الأنظمة الحساسة",
    "Application control policy and approved software inventory for critical systems; application whitelist configuration evidence (e.g., AppLocker, WDAC policies); block event logs showing enforcement; process for adding new approved applications",
    "Only explicitly approved applications must be permitted to execute on critical systems; application whitelisting or equivalent controls must be enforced and logged.")
sub_count += 1

sub(c, "2-3-4", "Patch and Vulnerability Remediation for Critical Systems",
    "تصحيح الثغرات ومعالجتها للأنظمة الحساسة",
    "Patch management policy with critical-system SLAs (e.g., critical patches within 72 hours); patch compliance dashboards for critical systems; vulnerability scan reports with remediation tracking; exception/risk-acceptance records for deferred patches",
    "Critical and high-severity patches for critical systems must be applied within defined SLAs, with automated compliance monitoring and formal exception handling for deferred patching.")
sub_count += 1

# 2-4  Network Security and Segregation
c = control(fid, "2-4", "Network Security and Segregation for Critical Systems",
            "أمن الشبكات والعزل للأنظمة الحساسة",
            "Requires strict network segregation isolating critical systems from corporate networks and the internet, with all traffic flows controlled, monitored, and justified.")
ctrl_count += 1

sub(c, "2-4-1", "Network Segregation Architecture for Critical Systems",
    "هيكل عزل الشبكات للأنظمة الحساسة",
    "Network architecture diagrams (as-built, dated, approved) showing critical-system zones, DMZs, and isolation from corporate/internet networks; firewall rule-sets reviewed against the approved architecture; data-flow diagrams justifying every approved inter-zone communication",
    "Critical systems must reside in dedicated network zones with documented perimeter controls; approved as-built architecture diagrams must be maintained and reviewed after every significant change.")
sub_count += 1

sub(c, "2-4-2", "Firewall and Access Control List Management",
    "إدارة جدران الحماية وقوائم التحكم في الوصول",
    "Firewall/ACL rule-set review reports (at least annual); evidence of rule-set against least-privilege principle (no any/any rules on critical-system interfaces); change records for firewall rules with business justification and security sign-off; unused rule identification and removal evidence",
    "Firewall rules and ACLs protecting critical systems must follow least-privilege, be reviewed at least annually, and every rule must have documented business justification and security approval.")
sub_count += 1

sub(c, "2-4-3", "Out-of-Band Management Network",
    "شبكة الإدارة خارج النطاق",
    "Network diagram showing dedicated out-of-band (OOB) management network separated from production traffic; OOB access logs showing management sessions; evidence that production and management traffic do not share the same network path; physical or cryptographic separation evidence",
    "Critical systems must be administered via a dedicated out-of-band management network, physically or cryptographically separated from production traffic, with session logs retained.")
sub_count += 1

sub(c, "2-4-4", "Intrusion Detection and Prevention on Critical Networks",
    "الكشف عن التسلل والوقاية منه على الشبكات الحساسة",
    "IDS/IPS deployment evidence on critical-system network segments; signature/rule update cadence logs; SIEM integration confirming IDS/IPS alert ingestion; tuning documentation and false-positive management records; sample high-severity alert handling records",
    "IDS/IPS must be deployed on all critical-system network segments with current signatures, SIEM integration, and documented tuning to maintain alert fidelity.")
sub_count += 1

# 2-5  Data Protection and Classification
c = control(fid, "2-5", "Data Protection and Classification for Critical Systems",
            "حماية البيانات وتصنيفها للأنظمة الحساسة",
            "Enforces data classification, labelling, handling procedures, and data-loss prevention controls for all data processed by critical systems, with special attention to nationally sensitive data.")
ctrl_count += 1

sub(c, "2-5-1", "Data Classification Policy and Labelling",
    "سياسة تصنيف البيانات والتسمية",
    "Data classification framework document with tier definitions (e.g., Public, Confidential, Restricted, Top Secret for national systems); data inventory/catalog for critical systems showing applied classifications; labelling configuration in DLP tools; evidence of classification reviews",
    "All data processed by critical systems must be classified, labelled, and handled according to a tiered classification policy that aligns with national data classification requirements.")
sub_count += 1

sub(c, "2-5-2", "Data Loss Prevention (DLP) Controls for Critical Systems",
    "ضوابط منع فقدان البيانات للأنظمة الحساسة",
    "DLP solution deployment scope showing coverage of critical-system data channels (email, USB, cloud upload, print); DLP policy configuration for critical-data-type detection; incident reports for DLP violations; quarterly DLP effectiveness reviews",
    "DLP controls must prevent unauthorised exfiltration of critical-system data across all channels, with monitoring, alerting, and formal incident handling for violations.")
sub_count += 1

sub(c, "2-5-3", "Secure Data Disposal and Media Sanitisation",
    "التخلص الآمن من البيانات وتعقيم الوسائط",
    "Media sanitisation policy citing NIST SP 800-88 or equivalent; destruction/sanitisation records for critical-system storage media; third-party certificate of destruction where applicable; evidence of degaussing or secure overwrite for reused media",
    "Storage media from critical systems must be sanitised using approved methods before reuse or disposal, with documented chain-of-custody and destruction certificates.")
sub_count += 1

# 2-6  Cryptography
c = control(fid, "2-6", "Cryptography for Critical Systems",
            "التشفير للأنظمة الحساسة",
            "Mandates strong, approved cryptographic algorithms and key management practices for all data-at-rest and data-in-transit protection on critical systems, aligned to NCA and NIST standards.")
ctrl_count += 1

sub(c, "2-6-1", "Encryption of Data at Rest for Critical Systems",
    "تشفير البيانات المخزنة للأنظمة الحساسة",
    "Encryption policy specifying approved algorithms (AES-256 minimum) for critical-system data at rest; storage encryption configuration evidence (full-disk, database TDE, or file-level); key management documentation; encryption compliance scan results",
    "All critical-system data at rest must be encrypted using approved strong algorithms with documented key management; encryption coverage must be verified by periodic scanning.")
sub_count += 1

sub(c, "2-6-2", "Encryption of Data in Transit for Critical Systems",
    "تشفير البيانات أثناء النقل للأنظمة الحساسة",
    "TLS policy requiring TLS 1.2+ for all critical-system communications; certificate inventory with expiry tracking; scan results confirming no cleartext protocols (Telnet, FTP, HTTP) on critical-system interfaces; inter-system encryption configuration evidence",
    "All data transmitted to or from critical systems must be encrypted using TLS 1.2 or higher; cleartext protocols must be prohibited and enforcement verified by scanning.")
sub_count += 1

sub(c, "2-6-3", "Cryptographic Key Management",
    "إدارة المفاتيح التشفيرية",
    "Key management policy covering generation, distribution, rotation, revocation, and destruction; HSM deployment evidence for critical-system keys; key rotation schedule and completion records; key custodian role assignments and dual-control evidence",
    "Cryptographic keys for critical systems must be managed in a Hardware Security Module (HSM) or equivalent approved key store, with dual-control access, documented rotation schedules, and secure key destruction procedures.")
sub_count += 1

# 2-7  Backup and Recovery with Offline Copies
c = control(fid, "2-7", "Backup and Recovery for Critical Systems",
            "النسخ الاحتياطي والاسترداد للأنظمة الحساسة",
            "Requires comprehensive, tested backup strategies for critical systems including mandatory offline/air-gapped copies, defined recovery objectives, and regular restoration testing.")
ctrl_count += 1

sub(c, "2-7-1", "Backup Policy and Coverage for Critical Systems",
    "سياسة النسخ الاحتياطي وتغطيتها للأنظمة الحساسة",
    "Backup policy defining RTO/RPO for each critical system; backup job completion logs showing consistent success; coverage report confirming all critical-system data is in scope; backup encryption evidence",
    "All critical-system data must be backed up according to documented RTO/RPO requirements with encrypted backup jobs, monitored completion, and 100% coverage verification.")
sub_count += 1

sub(c, "2-7-2", "Offline and Air-Gapped Backup Copies",
    "النسخ الاحتياطية غير المتصلة بالشبكة والمعزولة",
    "Offline/air-gapped backup policy and architecture diagram; evidence of regular transfer to offline media (tape rotation logs, off-site vault receipts, or air-gapped cloud bucket policy); custody chain records; ransomware-resilience test results confirming offline copy integrity",
    "Critical systems must maintain at least one offline or air-gapped backup copy, physically or logically isolated from production networks, to ensure recovery capability in ransomware or destructive-attack scenarios.")
sub_count += 1

sub(c, "2-7-3", "Backup Restoration Testing for Critical Systems",
    "اختبار استعادة النسخ الاحتياطية للأنظمة الحساسة",
    "Annual restoration test plans and results for all critical systems; documented RTO/RPO achievement evidence from tests; business sign-off on test results; discrepancies and improvement actions tracked",
    "Backup restoration must be tested at least annually for every critical system with documented RTO/RPO achievement, business sign-off, and tracked remediation of any gaps identified.")
sub_count += 1

# 2-8  Vulnerability Management
c = control(fid, "2-8", "Vulnerability Management for Critical Systems",
            "إدارة الثغرات الأمنية للأنظمة الحساسة",
            "Establishes a structured vulnerability scanning, assessment, prioritisation, and remediation programme specifically for critical systems with enhanced SLAs and NCA threat intelligence integration.")
ctrl_count += 1

sub(c, "2-8-1", "Regular Vulnerability Scanning of Critical Systems",
    "الفحص الدوري للثغرات الأمنية للأنظمة الحساسة",
    "Authenticated vulnerability scan reports for critical systems (at least monthly); scan coverage report; CVSS scoring and business criticality-adjusted prioritisation; scan tool credentials evidence of authenticated scanning",
    "Authenticated vulnerability scans of critical systems must be conducted at least monthly with results risk-prioritised by CVSS score and asset criticality.")
sub_count += 1

sub(c, "2-8-2", "Vulnerability Remediation SLAs for Critical Systems",
    "مستويات خدمة معالجة الثغرات للأنظمة الحساسة",
    "Vulnerability remediation SLA policy (e.g., Critical: 24h, High: 7 days, Medium: 30 days for critical systems); remediation tracking dashboard for critical systems; SLA breach records and escalation evidence; risk acceptance for deferred critical findings",
    "Strict remediation SLAs must be enforced for critical-system vulnerabilities with automated tracking, escalation on breach, and formal risk acceptance required for any deferral.")
sub_count += 1

sub(c, "2-8-3", "Vulnerability Intelligence and Zero-Day Response",
    "معلومات الثغرات والاستجابة لثغرات اليوم صفر",
    "Subscription evidence for NCA CERT advisories and CVE feeds; documented zero-day response procedure; evidence of emergency patching or compensating controls deployed within defined hours for critical-system zero-days; tabletop exercise records covering zero-day scenario",
    "A documented procedure must exist for emergency response to zero-day vulnerabilities affecting critical systems, with evidence of rapid deployment of patches or compensating controls within defined timeframes.")
sub_count += 1

# 2-9  Penetration Testing
c = control(fid, "2-9", "Penetration Testing for Critical Systems",
            "اختبار الاختراق للأنظمة الحساسة",
            "Mandates regular, scoped penetration testing of critical systems by qualified testers, with findings tracked and risk-accepted at appropriate authority levels.")
ctrl_count += 1

sub(c, "2-9-1", "Annual Penetration Testing of Critical Systems",
    "اختبار الاختراق السنوي للأنظمة الحساسة",
    "Engagement scope document and tester qualifications (OSCP, CEH, or equivalent); penetration test report with methodology, findings, and risk ratings; management remediation plan; re-test confirmation for critical/high findings",
    "Penetration testing of critical systems must be conducted at least annually by qualified testers, with all critical and high findings remediated and verified before the next production change window.")
sub_count += 1

sub(c, "2-9-2", "Red Team and Adversarial Simulation Exercises",
    "تمارين الفريق الأحمر ومحاكاة الهجمات",
    "Red team exercise scope and rules of engagement; exercise report with attack paths, indicators of compromise, and detection capability assessment; purple team debrief records; improvements to detection and response capability documented and tracked",
    "Advanced adversarial simulation (red team) exercises targeting critical-system attack scenarios must be conducted at least bi-annually to test detection and response capability beyond standard penetration testing.")
sub_count += 1

sub(c, "2-9-3", "Penetration Test Finding Remediation Tracking",
    "تتبع معالجة نتائج اختبار الاختراق",
    "Penetration test finding register with status tracking from discovery to verified closure; evidence of security team and asset owner collaboration on remediation; escalation records for overdue critical findings; trend analysis across test cycles",
    "All penetration test findings for critical systems must be tracked in a formal register to verified closure, with escalation for overdue findings and trend analysis across test cycles.")
sub_count += 1

# 2-10  Event Logging and Monitoring / SOC
c = control(fid, "2-10", "Event Logging, Monitoring, and SOC for Critical Systems",
            "تسجيل الأحداث والرصد ومركز العمليات الأمنية للأنظمة الحساسة",
            "Requires comprehensive, tamper-resistant logging of critical-system events with extended retention, 24/7 SOC monitoring, and integration of threat intelligence for proactive threat detection.")
ctrl_count += 1

sub(c, "2-10-1", "Comprehensive Logging of Critical-System Events",
    "تسجيل شامل لأحداث الأنظمة الحساسة",
    "Logging policy defining mandatory log sources for critical systems (authentication, privilege use, configuration changes, network flows, application errors); SIEM ingestion evidence for all mandatory sources; log completeness audit report; NTP synchronisation evidence for all log sources",
    "All critical-system components must generate and forward logs to a central SIEM covering authentication, privilege escalation, configuration changes, and network flows, with NTP-synchronised timestamps.")
sub_count += 1

sub(c, "2-10-2", "Extended Log Retention for Critical Systems",
    "الاحتفاظ الموسع بسجلات الأنظمة الحساسة",
    "Log retention policy specifying minimum 12-month online retention and 36-month archive for critical systems (exceeding ECC baseline); SIEM/log storage capacity plan; archived log retrieval test records; log integrity verification evidence (hash/WORM storage)",
    "Critical-system logs must be retained for a minimum of 12 months online and 36 months in archive, stored in write-once or integrity-protected storage to prevent tampering.")
sub_count += 1

sub(c, "2-10-3", "24/7 SOC Monitoring for Critical Systems",
    "مراقبة مركز العمليات الأمنية على مدار الساعة للأنظمة الحساسة",
    "SOC operational model documentation (in-house/MSSP/hybrid); SLA or OLA for critical-system alert response times; evidence of 24/7 analyst coverage; escalation runbooks for critical-system alert types; monthly SOC performance reports",
    "Critical systems must be monitored 24/7 by a SOC with documented response SLAs, escalation procedures, and monthly performance reporting to management.")
sub_count += 1

sub(c, "2-10-4", "SIEM Use-Case and Detection Rule Management",
    "إدارة حالات الاستخدام وقواعد الكشف في نظام إدارة المعلومات والأحداث الأمنية",
    "SIEM use-case library covering critical-system specific attack scenarios (lateral movement, privilege escalation, data exfiltration); use-case review and update records; false-positive tuning documentation; threat-intelligence-driven rule additions evidenced",
    "SIEM detection rules must be maintained and updated to cover critical-system attack scenarios; rule effectiveness must be reviewed at least quarterly with threat-intelligence-driven updates documented.")
sub_count += 1

# 2-11  Incident and Threat Management
c = control(fid, "2-11", "Incident and Threat Management for Critical Systems",
            "إدارة الحوادث والتهديدات للأنظمة الحساسة",
            "Establishes a comprehensive incident response capability for critical systems with defined playbooks, mandatory NCA notification, and post-incident review requirements.")
ctrl_count += 1

sub(c, "2-11-1", "Incident Response Plan for Critical Systems",
    "خطة الاستجابة للحوادث الأمنية للأنظمة الحساسة",
    "Incident response plan document specific to critical systems; playbooks for top threat scenarios (ransomware, DDoS, insider threat, supply-chain attack); tested escalation tree; evidence of annual tabletop exercise; NCA/relevant authority notification procedure",
    "A documented incident response plan with critical-system-specific playbooks must exist, be tested annually, and include NCA notification procedures within required timeframes.")
sub_count += 1

sub(c, "2-11-2", "Mandatory NCA Incident Reporting",
    "الإبلاغ الإلزامي عن الحوادث إلى الهيئة الوطنية للأمن السيبراني",
    "Incident classification matrix defining reportable incidents for critical systems; evidence of notification submissions to NCA within mandated timeframes for any qualifying incidents; notification templates and contact directory; drill records for notification procedure",
    "Cybersecurity incidents affecting critical systems that meet NCA reporting thresholds must be reported within mandated timeframes with documented notification records and drill evidence.")
sub_count += 1

sub(c, "2-11-3", "Post-Incident Review and Lessons Learned",
    "مراجعة ما بعد الحادث والدروس المستفادة",
    "Post-incident review (PIR) reports for all significant critical-system incidents; lessons-learned action log with owners and due dates; evidence of updates to playbooks, controls, or configurations based on PIR findings; trend analysis across incidents",
    "Every significant critical-system incident must be followed by a formal post-incident review with documented lessons learned, tracked improvement actions, and updates to response playbooks.")
sub_count += 1

# 2-12  Physical Security for Critical Systems
c = control(fid, "2-12", "Physical Security for Critical Systems",
            "الأمن المادي للأنظمة الحساسة",
            "Ensures that critical-system infrastructure is housed in physically secure facilities with multi-layer access controls, surveillance, and environmental monitoring.")
ctrl_count += 1

sub(c, "2-12-1", "Physical Access Control to Critical-System Facilities",
    "التحكم في الوصول المادي إلى مرافق الأنظمة الحساسة",
    "Physical security policy for critical-system data centres and control rooms; multi-factor physical access records (card + PIN or biometric); visitor access logs and escort records; physical access review reports (at least semi-annual); tailgating prevention evidence (mantrap/turnstile)",
    "Critical-system facilities must employ multi-factor physical access controls with logged entry/exit, visitor escorting, periodic access reviews, and anti-tailgating measures.")
sub_count += 1

sub(c, "2-12-2", "CCTV and Environmental Monitoring",
    "الدوائر التلفزيونية المغلقة ورصد البيئة",
    "CCTV coverage map for critical-system facilities showing no blind spots; footage retention policy (minimum 90 days for critical facilities); environmental monitoring alerts (temperature, humidity, water, smoke) with evidence of 24/7 alerting; alarm response records",
    "Critical-system facilities must have CCTV coverage without blind spots, footage retained per policy, and real-time environmental monitoring with 24/7 alert response capability.")
sub_count += 1

sub(c, "2-12-3", "Physical Media and Equipment Security",
    "أمن الوسائط المادية والمعدات",
    "Asset tracking for all physical media and equipment within critical-system facilities; locked cabinet/cage evidence for removable media storage; movement log for equipment leaving the facility; decommissioning checklist including physical media sanitisation",
    "Physical media and equipment within critical-system facilities must be tracked, stored securely, and subject to documented movement and decommissioning procedures.")
sub_count += 1

# ---------------------------------------------------------------------------
# DOMAIN 3 — CYBERSECURITY RESILIENCE
# ---------------------------------------------------------------------------

# 3-1  Resilience and BCM for Critical Systems
c = control(fid, "3-1", "Cybersecurity Resilience and Business Continuity for Critical Systems",
            "الصمود السيبراني واستمرارية الأعمال للأنظمة الحساسة",
            "Requires integration of cybersecurity resilience into business continuity and disaster recovery plans for critical systems, with defined cyber-specific RTOs, regular testing, and crisis management linkage.")
ctrl_count += 1

sub(c, "3-1-1", "Cyber-Resilience and BCP/DRP for Critical Systems",
    "الصمود السيبراني وخطط الاستمرارية والتعافي للأنظمة الحساسة",
    "BCP/DRP documents explicitly addressing cyber disruption scenarios for each critical system; RTO/RPO targets for cyber incidents vs. physical incidents; evidence of BCM/DRP review at least annually and after major incidents; linkage to crisis management framework",
    "BCP/DRP for critical systems must explicitly address cybersecurity disruption scenarios with defined cyber-specific RTOs/RPOs, annual review, and integration with the organisation's crisis management framework.")
sub_count += 1

sub(c, "3-1-2", "Cyber-Specific BCP/DRP Testing",
    "اختبار خطط الاستمرارية والتعافي بسيناريوهات سيبرانية",
    "Annual BCP/DRP test plan and results for critical systems including cyber-scenario exercises (e.g., ransomware, total system compromise); test evidence showing RTO/RPO achievement; gaps identified and tracked to closure; senior management sign-off on test results",
    "BCP/DRP for critical systems must be tested at least annually using realistic cyber-attack scenarios with documented RTO/RPO achievement and management-approved improvement actions.")
sub_count += 1

sub(c, "3-1-3", "Redundancy and Failover for Critical Systems",
    "التكرار وآليات تجاوز الفشل للأنظمة الحساسة",
    "Architecture diagrams showing redundancy and failover design for critical systems; failover test records (at least annual) with documented RTO evidence; high-availability configuration review; single-point-of-failure assessment and remediation tracking",
    "Critical systems must have documented redundancy and failover architectures with annual failover testing, single-point-of-failure elimination, and verified RTO achievement.")
sub_count += 1

sub(c, "3-1-4", "Crisis Management Integration for Cyber Incidents",
    "تكامل إدارة الأزمات لحوادث الأمن السيبراني",
    "Crisis management plan referencing cyber scenarios; communication templates for national authorities, board, and public; evidence of crisis management exercises involving cyber scenarios; documented escalation triggers from cyber incident to crisis status",
    "Cyber incidents on critical systems that meet crisis thresholds must trigger formal crisis management procedures with documented escalation criteria, pre-approved communications, and evidence of joint exercises.")
sub_count += 1

# ---------------------------------------------------------------------------
# DOMAIN 4 — THIRD-PARTY AND CLOUD CYBERSECURITY
# ---------------------------------------------------------------------------

# 4-1  Third-Party Cybersecurity for Critical Systems
c = control(fid, "4-1", "Third-Party Cybersecurity for Critical Systems",
            "الأمن السيبراني للأطراف الثالثة للأنظمة الحساسة",
            "Governs cybersecurity requirements in third-party relationships involving critical systems, including procurement due diligence, contractual obligations, access controls, and ongoing supplier assurance.")
ctrl_count += 1

sub(c, "4-1-1", "Third-Party Cybersecurity Due Diligence",
    "العناية الواجبة في الأمن السيبراني للأطراف الثالثة",
    "Third-party cybersecurity assessment questionnaire/methodology; completed assessments for all suppliers with critical-system access; risk tiering of suppliers; evidence of assessment results influencing contract decisions; reassessment schedule and completion records",
    "All third parties with access to or supporting critical systems must undergo cybersecurity due diligence before engagement, with risk-tiered assessments and documented outcomes influencing contract terms.")
sub_count += 1

sub(c, "4-1-2", "Contractual Cybersecurity Requirements for Critical-System Suppliers",
    "المتطلبات التعاقدية للأمن السيبراني لموردي الأنظمة الحساسة",
    "Contract templates and signed agreements including cybersecurity clauses (right to audit, incident notification, data protection, NCA compliance obligations); evidence of clause review and legal sign-off; evidence of supplier obligations communicated and acknowledged",
    "Contracts with third parties supporting critical systems must include mandatory cybersecurity clauses covering right-to-audit, incident notification timelines, data protection, and NCA compliance obligations.")
sub_count += 1

sub(c, "4-1-3", "Third-Party Access Control to Critical Systems",
    "التحكم في وصول الأطراف الثالثة إلى الأنظمة الحساسة",
    "Third-party access provisioning process requiring business justification and CISO/security approval; PAM-managed or time-limited access tokens for vendor access; session recording logs for third-party privileged sessions; access removal confirmation after engagement end",
    "Third-party access to critical systems must be time-limited, PAM-managed with session recording, require explicit security approval, and be revoked immediately upon engagement completion.")
sub_count += 1

sub(c, "4-1-4", "Ongoing Third-Party Cybersecurity Monitoring",
    "الرصد المستمر للأمن السيبراني للأطراف الثالثة",
    "Annual third-party reassessment schedule and completion evidence; evidence of monitoring third-party security incidents or breaches affecting critical systems; supplier security performance KPIs tracked; escalation records for underperforming suppliers",
    "The cybersecurity posture of third parties supporting critical systems must be reviewed at least annually with documented reassessments, performance tracking, and escalation procedures for underperformers.")
sub_count += 1

# 4-2  Cloud and Hosting Restrictions for Critical Systems
c = control(fid, "4-2", "Cloud and Hosting Security for Critical Systems",
            "أمن الحوسبة السحابية والاستضافة للأنظمة الحساسة",
            "Governs the use of cloud and third-party hosting for critical systems, including data residency restrictions, NCA-approved cloud controls, exit strategies, and enhanced security requirements for any cloud-hosted critical workloads.")
ctrl_count += 1

sub(c, "4-2-1", "Cloud Adoption Restrictions and Approvals for Critical Systems",
    "قيود اعتماد الحوسبة السحابية والموافقات للأنظمة الحساسة",
    "Cloud policy defining prohibited, restricted, and permitted cloud services for critical-system data; evidence of NCA/regulatory approval for any cloud hosting of critical systems; data residency compliance evidence (Saudi Arabia geolocation of processing/storage); cloud risk assessment records",
    "Any use of cloud services for critical-system data or processing must be approved by the designated authority and comply with NCA data residency requirements, with documented risk assessment and NCA/regulatory clearance.")
sub_count += 1

sub(c, "4-2-2", "Cloud Security Controls and Shared Responsibility",
    "ضوابط أمن السحابة والمسؤولية المشتركة",
    "Shared responsibility matrix for each cloud provider used for critical systems; Cloud Security Posture Management (CSPM) tool deployment evidence; cloud configuration compliance reports (CIS Cloud Benchmark); evidence of customer-side controls compensating for provider limitations",
    "A shared responsibility matrix must be documented for every cloud service hosting critical-system workloads, with CSPM tooling deployed to continuously monitor cloud security posture against approved benchmarks.")
sub_count += 1

sub(c, "4-2-3", "Cloud Exit Strategy for Critical Systems",
    "استراتيجية الخروج من الحوسبة السحابية للأنظمة الحساسة",
    "Cloud exit plan per critical-system cloud workload; evidence of data portability testing; contractual data-return and deletion clauses; exit strategy reviewed at least annually; evidence of senior management approval of exit plan",
    "For every critical-system workload hosted in cloud, a documented exit strategy must exist covering data retrieval, transition to alternative hosting, and contractual data deletion, reviewed and approved at least annually.")
sub_count += 1

# ---------------------------------------------------------------------------
# COMMIT
# ---------------------------------------------------------------------------
conn.commit()

print(f"[CCC-1:2019] Seed complete — framework_id={fid}")
print(f"  control() rows  : {ctrl_count}")
print(f"  sub()     rows  : {sub_count}")
print(f"  Total           : {ctrl_count + sub_count}")
