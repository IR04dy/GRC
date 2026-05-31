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

# ──────────────────────────────────────────────────────────────────────────────
# FRAMEWORK A: DCC-1:2022  Data Cybersecurity Controls
# ──────────────────────────────────────────────────────────────────────────────

dcc_fid = framework(
    "DCC-1:2022",
    "Data Cybersecurity Controls",
    "ضوابط الأمن السيبراني للبيانات",
    "2022",
    "NCA",
    "NCA Data Cybersecurity Controls standard covering the full data lifecycle: "
    "creation, storage, use, sharing, archiving, and destruction. Establishes "
    "governance, classification, protection, de-identification, cross-border "
    "transfer controls, retention scheduling, secure disposal, and DLP monitoring "
    "obligations for organisations operating in the Kingdom of Saudi Arabia. "
    "— practitioner SME mapping; structure aligned to the published framework, "
    "control text paraphrased (not verbatim)."
)

dcc_ctrl_count = 0
dcc_sub_count  = 0

# ── DCC Control 1: Data Governance & Ownership ────────────────────────────────
c = control(dcc_fid, "DCC-1", "Data Governance & Ownership",
            "حوكمة البيانات وتحديد الملكية",
            "Establishing accountable ownership and governance structures for organisational data assets.")
dcc_ctrl_count += 1

sub(c, "1-1-1",
    "Data Owner Assignment",
    "تعيين مالك البيانات",
    "Board-approved data ownership policy; RACI matrix mapping each data domain to a named owner; "
    "signed owner-acceptance letters; annual review evidence.",
    "Each data asset must be assigned a named organisational data owner accountable for classification, "
    "access decisions, and lifecycle controls.")
dcc_sub_count += 1

sub(c, "1-1-2",
    "Data Governance Committee Charter",
    "ميثاق لجنة حوكمة البيانات",
    "Signed committee charter; meeting minutes (at least quarterly); terms-of-reference document; "
    "escalation log for data governance issues.",
    "A cross-functional Data Governance Committee must be established with defined authority over "
    "classification policy, data sharing agreements, and exception management.")
dcc_sub_count += 1

sub(c, "1-1-3",
    "Data Governance Policy & Procedures",
    "سياسة وإجراءات حوكمة البيانات",
    "Published data governance policy version-controlled in the document management system; "
    "evidence of annual review and CISO/board approval; staff-acknowledgement records.",
    "A documented, approved data governance policy aligned to DCC-1 requirements must be maintained "
    "and reviewed at least annually.")
dcc_sub_count += 1

sub(c, "1-1-4",
    "Data Asset Inventory",
    "سجل جرد أصول البيانات",
    "Automated or manually maintained data asset register showing asset name, owner, classification, "
    "location, and retention class; last-updated timestamp; reconciliation with CMDB.",
    "A comprehensive inventory of all data assets — including databases, file shares, cloud storage, "
    "and unstructured repositories — must be maintained and kept current.")
dcc_sub_count += 1

# ── DCC Control 2: Data Classification & Labelling ────────────────────────────
c = control(dcc_fid, "DCC-2", "Data Classification & Labelling",
            "تصنيف البيانات ووضع العلامات",
            "Defining sensitivity tiers and applying machine-readable labels to all data assets.")
dcc_ctrl_count += 1

sub(c, "2-1-1",
    "Classification Scheme Definition",
    "تعريف مخطط التصنيف",
    "Approved classification policy with tier definitions (Public, Internal, Confidential, Restricted); "
    "mapping to PDPL personal-data categories and NCA classification levels; board sign-off record.",
    "The organisation must define a data classification scheme with at least four sensitivity levels "
    "aligned to regulatory requirements including PDPL and NCA guidelines.")
dcc_sub_count += 1

sub(c, "2-1-2",
    "Automated Classification Tooling",
    "أدوات التصنيف الآلي",
    "DLP/classification tool configuration screenshots; regex/pattern library; scan coverage reports "
    "showing % of repositories classified; exception report for unclassified assets.",
    "Automated classification tools must be deployed to discover, classify, and label structured and "
    "unstructured data across on-premises and cloud repositories.")
dcc_sub_count += 1

sub(c, "2-1-3",
    "Labelling Enforcement & Label Samples",
    "تطبيق الملصقات وعيناتها",
    "Sample labelled documents/emails/database column metadata; DLP policy configured to block "
    "unlabelled sensitive data transmission; label-stripping alert logs.",
    "All data assets classified as Confidential or Restricted must carry persistent, "
    "machine-readable labels enforced by technical controls.")
dcc_sub_count += 1

sub(c, "2-1-4",
    "User Classification Awareness Training",
    "تدريب الموظفين على تصنيف البيانات",
    "Training completion records (LMS extract) for all staff handling Confidential/Restricted data; "
    "awareness poster/communications; annual refresher attendance log.",
    "Staff responsible for creating or handling sensitive data must receive role-specific training "
    "on classification criteria and labelling procedures.")
dcc_sub_count += 1

sub(c, "2-1-5",
    "Classification Review & Reclassification Process",
    "مراجعة التصنيف وإعادة التصنيف",
    "Documented reclassification request form; approval workflow evidence; audit trail of "
    "classification changes with justification; quarterly review report from data owner.",
    "A formal process must exist for periodic review and reclassification of data assets "
    "when business context or regulatory requirements change.")
dcc_sub_count += 1

# ── DCC Control 3: Data Protection ────────────────────────────────────────────
c = control(dcc_fid, "DCC-3", "Data Protection",
            "حماية البيانات",
            "Encryption at rest and in transit, tokenisation, and DLP controls to prevent "
            "unauthorised access or exfiltration of sensitive data.")
dcc_ctrl_count += 1

sub(c, "3-1-1",
    "Encryption at Rest for Sensitive Data",
    "تشفير البيانات الساكنة",
    "Encryption configuration evidence (TDE enabled on SQL Server, BitLocker/LUKS on endpoints, "
    "S3/Blob encryption policy); key management system inventory; algorithm compliance (AES-256); "
    "vulnerability scan confirming no unencrypted sensitive data stores.",
    "All Confidential and Restricted data at rest must be encrypted using approved algorithms "
    "(minimum AES-256) with keys managed in a dedicated key management system.")
dcc_sub_count += 1

sub(c, "3-1-2",
    "Encryption in Transit",
    "تشفير البيانات أثناء النقل",
    "TLS configuration review (minimum TLS 1.2; TLS 1.3 preferred); certificate inventory and "
    "expiry monitoring dashboard; network scan confirming no clear-text sensitive data channels; "
    "API gateway TLS enforcement policy.",
    "All transmission of Confidential or Restricted data — internal and external — must use "
    "TLS 1.2 or higher. Clear-text protocols for sensitive data are prohibited.")
dcc_sub_count += 1

sub(c, "3-1-3",
    "Key Management & Lifecycle Controls",
    "إدارة مفاتيح التشفير ودورة حياتها",
    "KMS/HSM inventory; key rotation schedule and evidence of last rotation; key custodian "
    "assignment records; key ceremony documentation; access control list for cryptographic keys; "
    "HSM audit log samples.",
    "Cryptographic keys must be generated, distributed, stored, rotated, and destroyed through "
    "a documented key management process using HSM or equivalent approved key store.")
dcc_sub_count += 1

sub(c, "3-1-4",
    "Tokenisation of Payment & High-Risk Data",
    "ترميز بيانات الدفع والبيانات عالية الخطورة",
    "Tokenisation solution architecture diagram; scope of tokenised fields (PAN, national ID, etc.); "
    "de-tokenisation access control policy; audit logs of de-tokenisation events; PCI-DSS/SAMA "
    "alignment evidence where applicable.",
    "Payment card data, national identity numbers, and other high-risk personal identifiers "
    "must be tokenised in application storage and processing pipelines.")
dcc_sub_count += 1

sub(c, "3-1-5",
    "Data Loss Prevention (DLP) Policy & Rules",
    "سياسة منع تسرب البيانات وقواعدها",
    "DLP tool configuration baseline; content-inspection rule set (covering SSN/national ID, "
    "IBAN, credit card patterns, classification labels); policy exceptions log with approvals; "
    "monthly DLP incident summary report.",
    "A DLP solution must be deployed across email, web proxy, and endpoint channels to detect "
    "and block unauthorised transmission of sensitive data based on classification and content rules.")
dcc_sub_count += 1

sub(c, "3-1-6",
    "DLP Alert Triage & Incident Response",
    "فرز تنبيهات DLP والاستجابة للحوادث",
    "DLP alert queue records; triage SLA evidence (time-to-review per severity); escalation "
    "tickets raised; false-positive tuning log; monthly metric report (alerts raised, confirmed "
    "incidents, blocked events).",
    "DLP alerts must be triaged within defined SLAs, with confirmed incidents escalated through "
    "the information security incident management process.")
dcc_sub_count += 1

sub(c, "3-1-7",
    "Access Control for Sensitive Data Stores",
    "التحكم في الوصول إلى مخازن البيانات الحساسة",
    "RBAC/ABAC matrix for sensitive databases and file repositories; privileged-access review "
    "records (semi-annual); PAM tool audit logs; database activity monitoring (DAM) alerts; "
    "need-to-know justification for Restricted data access grants.",
    "Access to Confidential and Restricted data stores must enforce least-privilege and need-to-know "
    "principles, with privileged access managed through a PAM solution and reviewed semi-annually.")
dcc_sub_count += 1

# ── DCC Control 4: Data De-identification & Anonymisation ─────────────────────
c = control(dcc_fid, "DCC-4", "Data De-identification & Anonymisation",
            "إخفاء هوية البيانات وإزالة التعرف عليها",
            "Techniques and controls to remove or mask personal identifiers from datasets used "
            "for testing, analytics, or secondary purposes.")
dcc_ctrl_count += 1

sub(c, "4-1-1",
    "De-identification Standard & Approved Techniques",
    "معيار إخفاء الهوية والأساليب المعتمدة",
    "Approved de-identification methodology document (referencing ISO 29101 or NIST SP 800-188); "
    "list of approved techniques (pseudonymisation, k-anonymity, differential privacy); "
    "risk-assessment for re-identification for each technique tier.",
    "The organisation must define and approve de-identification techniques appropriate to the "
    "sensitivity of the data and the intended secondary use, with re-identification risk assessed.")
dcc_sub_count += 1

sub(c, "4-1-2",
    "Test & Development Data Masking",
    "إخفاء بيانات بيئات الاختبار والتطوير",
    "Data masking tool configuration; evidence that production Confidential/Restricted data is "
    "masked before loading into non-production environments; sample masked-dataset schema comparison; "
    "developer environment access control policy.",
    "Production personal or sensitive data must not be used in test or development environments "
    "without prior de-identification or synthetic data substitution.")
dcc_sub_count += 1

sub(c, "4-1-3",
    "Analytics & Reporting Data Anonymisation",
    "إخفاء هوية البيانات في التحليلات والتقارير",
    "Privacy impact assessment for analytics use cases involving personal data; anonymisation "
    "process documentation; evidence of k-anonymity threshold applied; published-data review "
    "checklist before external report release.",
    "Datasets prepared for analytics, BI reporting, or external publication must be anonymised "
    "such that individuals cannot be re-identified, with controls validated before release.")
dcc_sub_count += 1

sub(c, "4-1-4",
    "Re-identification Risk Assessment",
    "تقييم مخاطر إعادة التعرف على الهوية",
    "Documented re-identification risk assessment methodology; assessments performed for each "
    "de-identified dataset release or major schema change; residual risk acceptance sign-off "
    "by data owner; remediation log where risk exceeded threshold.",
    "A formal re-identification risk assessment must be conducted before any de-identified dataset "
    "is shared externally or used in a new secondary context.")
dcc_sub_count += 1

# ── DCC Control 5: Data Sharing & Transfer (incl. Cross-Border) ──────────────
c = control(dcc_fid, "DCC-5", "Data Sharing & Transfer",
            "مشاركة البيانات ونقلها",
            "Controls governing internal and external data sharing, cross-border transfers, "
            "and data residency obligations under Saudi law.")
dcc_ctrl_count += 1

sub(c, "5-1-1",
    "Data Sharing Agreement (DSA) Framework",
    "إطار اتفاقيات مشاركة البيانات",
    "Master DSA template approved by legal/DPO; executed DSA register with parties, purpose, "
    "data categories, and expiry dates; annual review evidence; PDPL Art.29 compliance checklist "
    "embedded in DSA approval workflow.",
    "All external data sharing must be governed by a signed Data Sharing Agreement defining "
    "purpose limitation, data categories, security requirements, and termination obligations.")
dcc_sub_count += 1

sub(c, "5-1-2",
    "Cross-Border Data Transfer Controls",
    "ضوابط النقل العابر للحدود للبيانات",
    "Register of cross-border data flows including destination country, legal basis, and "
    "adequacy decision or safeguard type; PDPL-compliant transfer mechanism documentation "
    "(SDAIA adequacy list reference or contractual clauses); transfer impact assessments for "
    "high-risk destinations.",
    "Personal data transferred outside KSA must comply with PDPL cross-border transfer "
    "requirements, with documented legal basis and transfer impact assessment for each flow.")
dcc_sub_count += 1

sub(c, "5-1-3",
    "Data Residency Classification & Mapping",
    "تصنيف البيانات ورسم خرائط متطلبات الإقامة",
    "Data residency requirement register cross-referenced with asset inventory; evidence of "
    "sovereign/critical data hosted only in KSA-domiciled infrastructure; cloud storage "
    "region enforcement policy; periodic residency audit report.",
    "Data categories subject to mandatory KSA residency (financial, health, sovereign) must "
    "be identified, mapped, and technically enforced to remain within approved geographic boundaries.")
dcc_sub_count += 1

sub(c, "5-1-4",
    "Secure File Transfer Mechanisms",
    "آليات النقل الآمن للملفات",
    "SFTP/FTPS/HTTPS configuration standards; MFT platform inventory; encryption-in-transit "
    "coverage evidence; access log samples for file transfer events; prohibition of FTP/email "
    "for Restricted data without compensating controls.",
    "Sensitive data transfers must use encrypted, authenticated channels (SFTP, HTTPS, or "
    "managed file transfer platform); insecure protocols must be disabled for Restricted data flows.")
dcc_sub_count += 1

sub(c, "5-1-5",
    "Third-Party Data Recipient Due Diligence",
    "العناية الواجبة لمستقبلي البيانات من الأطراف الثالثة",
    "Third-party security assessment questionnaire results; contractual data-protection clauses "
    "evidence; onboarding checklist for new data recipients; annual review of existing recipients; "
    "right-to-audit clause in active DSAs.",
    "Before sharing sensitive data with a third party, a security and privacy due-diligence "
    "assessment must be completed and documented, with findings tracked to closure.")
dcc_sub_count += 1

# ── DCC Control 6: Data Retention ─────────────────────────────────────────────
c = control(dcc_fid, "DCC-6", "Data Retention",
            "الاحتفاظ بالبيانات",
            "Defining and enforcing retention periods for data classes in line with regulatory "
            "and business requirements.")
dcc_ctrl_count += 1

sub(c, "6-1-1",
    "Data Retention Schedule",
    "جدول الاحتفاظ بالبيانات",
    "Board-approved retention schedule covering all data classes; mapping to regulatory mandates "
    "(SAMA, PDPL, NCA, SOCPA); last review date and approving authority; exceptions register with "
    "legal-hold justifications.",
    "A formal data retention schedule must define minimum and maximum retention periods "
    "for each data category, aligned to regulatory requirements and reviewed annually.")
dcc_sub_count += 1

sub(c, "6-1-2",
    "Automated Retention Enforcement",
    "تطبيق الاحتفاظ بالبيانات آلياً",
    "Retention policy configuration in EDRMS/archiving system; scheduled purge job logs; "
    "evidence of automated archiving after primary retention period; exception report for "
    "overdue purge items.",
    "Technical controls (archiving systems, database lifecycle policies) must automatically "
    "enforce retention periods without relying solely on manual processes.")
dcc_sub_count += 1

sub(c, "6-1-3",
    "Legal Hold Process",
    "إجراءات الحجز القانوني للبيانات",
    "Legal hold issuance log with matter reference, custodians in scope, and data categories "
    "preserved; legal hold notice templates; suspension of scheduled purge evidence; "
    "hold release authorisation records.",
    "A formal legal hold process must suspend automated retention deletion for data subject "
    "to ongoing litigation, regulatory inquiry, or audit.")
dcc_sub_count += 1

sub(c, "6-1-4",
    "Retention Compliance Audit",
    "مراجعة الامتثال للاحتفاظ بالبيانات",
    "Annual retention compliance audit report; sample testing of purge completion against schedule; "
    "findings and remediation tracker; Internal Audit sign-off.",
    "Annual audits must verify that data is being purged or archived in accordance with the "
    "approved retention schedule, with findings reported to the Data Governance Committee.")
dcc_sub_count += 1

# ── DCC Control 7: Data Destruction & Sanitisation ────────────────────────────
c = control(dcc_fid, "DCC-7", "Data Destruction & Sanitisation",
            "إتلاف البيانات وتعقيمها",
            "Secure erasure, physical destruction, and certified disposal of data and media "
            "at end of retention or end of asset lifecycle.")
dcc_ctrl_count += 1

sub(c, "7-1-1",
    "Secure Erasure Standards & Methods",
    "معايير المسح الآمن وأساليبه",
    "Approved secure-erasure standard (NIST SP 800-88, DoD 5220.22-M equivalent); tool "
    "qualification list; configuration baseline for overwrite passes; verification method "
    "documentation (read-back verification).",
    "Data destruction must follow an approved standard specifying the erasure method, "
    "number of passes, and verification step appropriate to media type and data classification.")
dcc_sub_count += 1

sub(c, "7-1-2",
    "Physical Media Destruction",
    "الإتلاف المادي للوسائط",
    "Certified destruction vendor contract and current accreditation certificate (ISO/IEC 21964 "
    "or equivalent); destruction manifest records with asset serial numbers; witnessed "
    "destruction log or video evidence; certificate-of-destruction files.",
    "Physical storage media containing Confidential or Restricted data must be destroyed "
    "by a certified vendor with documented chain of custody and certificate of destruction.")
dcc_sub_count += 1

sub(c, "7-1-3",
    "Cloud & Virtual Storage Purge",
    "تطهير مساحات التخزين السحابية والافتراضية",
    "Cloud storage deletion policy; evidence of cryptographic erasure (key deletion) or "
    "verified data deletion for decommissioned cloud volumes; cloud provider data-deletion "
    "confirmation records; decommission checklist for virtual machine images.",
    "When decommissioning cloud storage or virtual environments, cryptographic erasure or "
    "verified deletion must be performed and documented, leveraging provider deletion APIs "
    "or BYOK key destruction.")
dcc_sub_count += 1

sub(c, "7-1-4",
    "Disposal Certificates & Audit Trail",
    "شهادات التخلص ومسار التدقيق",
    "Centralised disposal register linking asset tags to destruction method, date, certifying "
    "officer, and certificate reference; quarterly reconciliation against asset inventory; "
    "3-year retention of disposal records.",
    "All data and media disposal events must generate a destruction certificate stored in an "
    "auditable register, retained for at least three years.")
dcc_sub_count += 1

sub(c, "7-1-5",
    "End-of-Contract Data Return & Destruction for Third Parties",
    "إعادة البيانات وإتلافها عند انتهاء العقود مع الأطراف الثالثة",
    "Contractual data-return/destruction clause samples; contractor-provided destruction "
    "certificates on contract termination; verification checklist for data return completeness; "
    "time-bound destruction deadline enforcement log.",
    "On termination of any contract under which a third party processes organisational data, "
    "the third party must return or certify the destruction of all organisational data within "
    "an agreed timeframe.")
dcc_sub_count += 1

# ── DCC Control 8: Data Leakage Monitoring ────────────────────────────────────
c = control(dcc_fid, "DCC-8", "Data Leakage Monitoring",
            "رصد تسرب البيانات",
            "Continuous monitoring, detection, and response capabilities to identify "
            "unauthorised data exfiltration attempts.")
dcc_ctrl_count += 1

sub(c, "8-1-1",
    "SIEM Integration for Data Leakage Events",
    "تكامل SIEM مع أحداث تسرب البيانات",
    "SIEM use-case documentation for data leakage scenarios (bulk downloads, after-hours access, "
    "USB transfer); alert rule configuration screenshots; correlation rule testing evidence; "
    "SIEM coverage of DLP, DAM, and proxy event sources.",
    "SIEM must ingest and correlate events from DLP, database activity monitoring, and web proxy "
    "to detect data leakage patterns across all sensitive data channels.")
dcc_sub_count += 1

sub(c, "8-1-2",
    "User & Entity Behaviour Analytics (UEBA) for Insider Threats",
    "تحليلات سلوك المستخدمين والكيانات للكشف عن التهديدات الداخلية",
    "UEBA tool deployment scope documentation; baseline behaviour model configuration; "
    "high-risk user watchlist process; alert escalation records; quarterly insider-threat "
    "review report.",
    "UEBA controls must establish behavioural baselines and alert on anomalous data access "
    "or transfer patterns indicative of insider threat or compromised account activity.")
dcc_sub_count += 1

sub(c, "8-1-3",
    "Network Data Flow Monitoring",
    "مراقبة تدفق البيانات على الشبكة",
    "Network DLP (NDLP) or NetFlow inspection coverage map; policies blocking exfiltration "
    "via HTTP/S, email, FTP, and cloud storage; monthly exfiltration attempt report; "
    "blocked-channel evidence logs.",
    "Network-based data leakage detection must monitor egress channels including web, email, "
    "and cloud storage to detect and block sensitive data exfiltration in transit.")
dcc_sub_count += 1

sub(c, "8-1-4",
    "Endpoint DLP Monitoring",
    "مراقبة منع تسرب البيانات على الأجهزة الطرفية",
    "Endpoint DLP agent deployment coverage report (% of managed endpoints); USB and "
    "removable-media block/alert policy; screen-capture and clipboard monitoring policy "
    "for Restricted data; agent health dashboard.",
    "Endpoint DLP agents must be deployed on all managed devices handling Confidential or "
    "Restricted data, with controls covering USB transfer, clipboard, and screen-capture channels.")
dcc_sub_count += 1

sub(c, "8-1-5",
    "Data Leakage Incident Response Playbook",
    "دليل الاستجابة لحوادث تسرب البيانات",
    "Documented data-leakage incident response playbook; tabletop exercise record (annual); "
    "incident closure reports with root-cause analysis; notification workflow for PDPL-reportable "
    "breaches (72-hour SDAIA timeline evidence).",
    "A dedicated data-leakage incident response playbook must define containment, investigation, "
    "notification (including PDPL regulatory notification obligations), and remediation steps.")
dcc_sub_count += 1

sub(c, "8-1-6",
    "Dark Web & Leaked Credential Monitoring",
    "مراقبة الويب المظلم وبيانات الاعتماد المسربة",
    "Dark-web monitoring service subscription evidence; alert records for organisational "
    "data detected in breach repositories; response workflow for leaked credentials "
    "(force-reset timeline); quarterly threat intelligence report.",
    "The organisation must subscribe to or operate a dark-web monitoring service to detect "
    "organisational data or credential leakage, with defined response timelines for confirmed findings.")
dcc_sub_count += 1

print(f"[DCC-1:2022] Loaded: {dcc_ctrl_count} controls, {dcc_sub_count} sub-controls")

# ──────────────────────────────────────────────────────────────────────────────
# FRAMEWORK B: CSCC-1:2020  Cloud Cybersecurity Controls
# ──────────────────────────────────────────────────────────────────────────────

cscc_fid = framework(
    "CSCC-1:2020",
    "Cloud Cybersecurity Controls",
    "ضوابط الأمن السيبراني للحوسبة السحابية",
    "2020",
    "NCA",
    "NCA Cloud Cybersecurity Controls standard governing the security responsibilities of both "
    "Cloud Service Providers (CSP) and Cloud Service Tenants (CST) across four domains: Governance, "
    "Defence, Resilience, and Third-Party Management. Applies to all organisations in KSA that "
    "deploy, procure, or operate cloud computing services. "
    "— practitioner SME mapping; structure aligned to the published framework, "
    "control text paraphrased (not verbatim)."
)

cscc_ctrl_count = 0
cscc_sub_count  = 0

# ── CSCC Control 1: Cloud Governance (CSP & CST) ──────────────────────────────
c = control(cscc_fid, "CSCC-1", "Cloud Governance — CSP & CST",
            "حوكمة الحوسبة السحابية — مزود الخدمة والمستأجر",
            "Policy, accountability, and strategic decision-making frameworks for cloud adoption "
            "and operation for both service providers and tenants.")
cscc_ctrl_count += 1

sub(c, "1-1-1",
    "Cloud Usage Policy (CST)",
    "سياسة استخدام الحوسبة السحابية — المستأجر",
    "Board-approved cloud usage policy; scope covering IaaS/PaaS/SaaS; shadow-IT prohibition "
    "clause; approved-cloud-service register; policy distribution and staff acknowledgement records.",
    "CST must maintain an approved cloud usage policy defining permitted cloud deployment models, "
    "approved service categories, and restrictions on unsanctioned cloud usage (shadow IT).")
cscc_sub_count += 1

sub(c, "1-1-2",
    "Approved Cloud Service Provider Register (CST)",
    "سجل مزودي الخدمات السحابية المعتمدين — المستأجر",
    "Approved-CSP register with provider name, service description, classification of hosted data, "
    "NCA licensing status, and last risk-assessment date; onboarding checklist; decommission log.",
    "CST must maintain a register of all approved cloud service providers, updated whenever "
    "a new cloud service is onboarded or an existing one is decommissioned.")
cscc_sub_count += 1

sub(c, "1-1-3",
    "Shared-Responsibility Matrix (CSP & CST)",
    "مصفوفة المسؤولية المشتركة — المزود والمستأجر",
    "Signed shared-responsibility matrix (SRM) for each cloud deployment model in use (IaaS/PaaS/SaaS); "
    "SRM reviewed on contract renewal; evidence of gap analysis where CST controls supplement CSP defaults; "
    "SRM embedded in cloud onboarding checklist.",
    "For each cloud service, a shared-responsibility matrix must be documented and agreed between "
    "CSP and CST, clearly delineating security control ownership across all control domains.")
cscc_sub_count += 1

sub(c, "1-1-4",
    "Cloud Security Strategy & Risk Appetite (CST)",
    "استراتيجية الأمن السيبراني للسحابة وشهية المخاطر — المستأجر",
    "Cloud security strategy document endorsed by CISO; risk appetite statement covering cloud services; "
    "cloud risk register with residual risk sign-off; annual review evidence.",
    "CST must define a cloud security strategy aligned to enterprise risk appetite, with a cloud-specific "
    "risk register reviewed at least annually by the CISO or equivalent.")
cscc_sub_count += 1

sub(c, "1-1-5",
    "Cloud Service Provider NCA Licensing Verification (CST)",
    "التحقق من ترخيص NCA لمزودي الخدمات السحابية — المستأجر",
    "Evidence of NCA cloud licensing check for each CSP (NCA approved-CSP list cross-reference); "
    "annual re-verification records; escalation process for unlicensed CSP discovery.",
    "CST must verify that cloud service providers operating in KSA hold the required NCA cloud "
    "service licence, with annual re-verification and an escalation path for non-compliant findings.")
cscc_sub_count += 1

# ── CSCC Control 2: Data Classification & Residency in Cloud ──────────────────
c = control(cscc_fid, "CSCC-2", "Data Classification & Residency in Cloud — CST",
            "تصنيف البيانات ومتطلبات الإقامة في السحابة — المستأجر",
            "Ensuring data hosted in cloud environments is appropriately classified and that "
            "residency obligations are technically enforced.")
cscc_ctrl_count += 1

sub(c, "2-1-1",
    "Cloud Data Classification Assessment",
    "تقييم تصنيف البيانات في بيئة السحابة",
    "Data classification assessment for each cloud workload; mapping to national classification "
    "requirements; restricted/sovereign data prohibition for public cloud without NCA approval; "
    "assessment updated on each new cloud migration project.",
    "Before migrating data to cloud, CST must classify the data and confirm the target cloud "
    "environment is approved to host that classification level.")
cscc_sub_count += 1

sub(c, "2-1-2",
    "Data Residency Enforcement — KSA Sovereign Data",
    "تطبيق متطلبات الإقامة للبيانات السيادية السعودية",
    "Cloud region configuration evidence (KSA region pinning); geo-restriction policy screenshots; "
    "storage account location audit; periodic cross-region replication review to confirm no "
    "out-of-kingdom copies of sovereign data.",
    "Sovereign and critical data categories must be technically constrained to KSA-based cloud "
    "regions, with geo-restriction policies preventing replication to non-approved regions.")
cscc_sub_count += 1

sub(c, "2-1-3",
    "Cloud Data Inventory & Tagging",
    "جرد البيانات السحابية ووضع علاماتها",
    "Cloud resource tagging policy (classification, owner, environment, data-residency tags); "
    "tag compliance scan report (% tagged); untagged-resource remediation log; integration "
    "with CSPM for continuous tag enforcement.",
    "All cloud storage resources must be tagged with data classification and residency requirements, "
    "with tag compliance monitored continuously by a Cloud Security Posture Management tool.")
cscc_sub_count += 1

# ── CSCC Control 3: Identity & Access Management for Cloud Consoles ───────────
c = control(cscc_fid, "CSCC-3", "Cloud IAM & Privileged Access — CST",
            "إدارة الهوية والوصول المتميز في السحابة — المستأجر",
            "Identity and access management controls for cloud management consoles, APIs, "
            "and workloads, including MFA enforcement and privileged access governance.")
cscc_ctrl_count += 1

sub(c, "3-1-1",
    "MFA Enforcement for Cloud Console Access",
    "تطبيق المصادقة متعددة العوامل على وحدات التحكم السحابية",
    "MFA policy configuration evidence for all cloud management accounts (AWS IAM policy, "
    "Azure Conditional Access, GCP Org Policy); MFA coverage report (% of accounts with MFA); "
    "break-glass account MFA exception log with compensating controls.",
    "MFA must be enforced for all identities accessing cloud management consoles and privileged "
    "cloud APIs, with no exemptions except documented break-glass accounts.")
cscc_sub_count += 1

sub(c, "3-1-2",
    "Least-Privilege IAM Role Design",
    "تصميم أدوار الوصول وفق مبدأ الصلاحيات الدنيا",
    "IAM policy review records showing no wildcard (*) permissions for production workloads; "
    "role definition documentation; unused permission removal log; quarterly access certification "
    "results.",
    "Cloud IAM roles must be designed on least-privilege principles; wildcard permissions are "
    "prohibited in production, and unused permissions must be identified and removed quarterly.")
cscc_sub_count += 1

sub(c, "3-1-3",
    "Privileged Cloud Account Management (PAM Integration)",
    "إدارة الحسابات المتميزة في السحابة — تكامل PAM",
    "PAM tool integration with cloud management accounts evidence; just-in-time access "
    "provisioning configuration; privileged session recording for cloud console access; "
    "quarterly review of standing privileged cloud accounts.",
    "Standing privileged cloud accounts must be minimised; privileged access should be provided "
    "just-in-time through a PAM solution with session recording for all cloud console privileged sessions.")
cscc_sub_count += 1

sub(c, "3-1-4",
    "Service Account & API Key Governance",
    "حوكمة حسابات الخدمة ومفاتيح واجهة برمجة التطبيقات",
    "Service account inventory with purpose, owner, and last-rotation date; API key rotation "
    "schedule and evidence; prohibition of long-lived credentials in code repositories (secret "
    "scanning tool evidence); service account access review (quarterly).",
    "Cloud service accounts and API keys must be inventoried, rotated on schedule, and scanned "
    "to prevent embedding in source code repositories.")
cscc_sub_count += 1

sub(c, "3-1-5",
    "Federated Identity & SSO for Cloud Access (CST)",
    "الهوية الموحدة وتسجيل الدخول الموحد للوصول السحابي — المستأجر",
    "IdP federation configuration evidence (SAML/OIDC with corporate directory); local "
    "cloud-native accounts audit (minimised/eliminated); SSO coverage report; IdP health "
    "monitoring alerts.",
    "CST must federate cloud identity to the corporate identity provider (IdP) via SAML or OIDC, "
    "minimising the use of local cloud-native accounts outside of break-glass scenarios.")
cscc_sub_count += 1

# ── CSCC Control 4: Network Security & Segmentation in Cloud ──────────────────
c = control(cscc_fid, "CSCC-4", "Cloud Network Security & Segmentation — CSP & CST",
            "أمن الشبكات والتجزئة في السحابة — المزود والمستأجر",
            "Virtual network architecture, segmentation, and perimeter controls to protect "
            "cloud workloads from lateral movement and external threats.")
cscc_ctrl_count += 1

sub(c, "4-1-1",
    "Virtual Private Cloud (VPC) Architecture & Segmentation",
    "هندسة الشبكة الافتراضية الخاصة وتجزئتها",
    "VPC/VNET architecture diagram showing subnet segmentation by environment (prod/non-prod) "
    "and workload tier; security group / network ACL baseline; peering and transit-gateway "
    "topology document; quarterly network architecture review.",
    "Cloud workloads must be deployed in dedicated VPCs/VNETs with subnet segmentation "
    "isolating production from non-production and separating application tiers.")
cscc_sub_count += 1

sub(c, "4-1-2",
    "Cloud Firewall & Security Group Rule Management",
    "إدارة جدار الحماية السحابي وقواعد مجموعات الأمان",
    "Security group/firewall rule inventory; evidence of 0.0.0.0/0 inbound rule prohibition "
    "for sensitive ports; rule review cadence records (quarterly); IaC-enforced baseline "
    "configuration (Terraform/CloudFormation policy-as-code scan results).",
    "Cloud firewall and security group rules must follow a deny-by-default baseline; "
    "overly permissive inbound rules (open to all internet) are prohibited for sensitive workloads.")
cscc_sub_count += 1

sub(c, "4-1-3",
    "Cloud Web Application Firewall (WAF) Deployment",
    "نشر جدار حماية تطبيقات الويب السحابي",
    "WAF deployment evidence for public-facing cloud workloads; managed-rule-set version; "
    "custom rule configuration for application-specific threats; WAF alert and block logs; "
    "false-positive tuning records.",
    "Public-facing cloud applications must be protected by a Cloud WAF with managed OWASP rule sets "
    "and application-specific custom rules, with alert tuning reviewed quarterly.")
cscc_sub_count += 1

sub(c, "4-1-4",
    "Private Connectivity for Sensitive Cloud Access (CST)",
    "الاتصال الخاص للوصول الحساس إلى السحابة — المستأجر",
    "Private endpoint / PrivateLink configuration for storage and database services; "
    "ExpressRoute/Direct Connect circuit documentation; evidence that sensitive data services "
    "do not traverse the public internet; network flow logs confirming private routing.",
    "Sensitive cloud data services (databases, object storage, key vaults) must be accessible "
    "only via private endpoints or dedicated connectivity, not over the public internet.")
cscc_sub_count += 1

# ── CSCC Control 5: Cloud Key Management & Encryption (CSP & CST) ─────────────
c = control(cscc_fid, "CSCC-5", "Cloud Key Management & Encryption — CSP & CST",
            "إدارة مفاتيح التشفير والتشفير في السحابة — المزود والمستأجر",
            "Cryptographic key lifecycle management in cloud environments including Bring-Your-Own-Key "
            "and cloud-native HSM-backed key stores.")
cscc_ctrl_count += 1

sub(c, "5-1-1",
    "Cloud-Native KMS Configuration & Key Hierarchy",
    "إعداد KMS السحابي وتسلسل المفاتيح",
    "Cloud KMS key inventory (AWS KMS / Azure Key Vault / GCP Cloud KMS); key hierarchy "
    "documentation (master key, data encryption keys); key rotation schedule and evidence "
    "(annual minimum); access control policy for key management operations.",
    "Cloud workloads must use the cloud provider's managed KMS for key lifecycle management, "
    "with a documented key hierarchy, rotation schedule, and strict access controls.")
cscc_sub_count += 1

sub(c, "5-1-2",
    "Bring-Your-Own-Key (BYOK) for High-Sensitivity Workloads (CST)",
    "إحضار مفاتيحك الخاصة (BYOK) للأحمال الحساسة — المستأجر",
    "BYOK implementation documentation for Confidential/Restricted workloads; HSM-generated "
    "key material evidence; key import audit log; ability-to-revoke-key test evidence; "
    "key custody assignment.",
    "For cloud workloads processing Confidential or Restricted data, CST must implement BYOK "
    "using keys generated and escrowed in a CST-controlled HSM, enabling key revocation independently "
    "of the CSP.")
cscc_sub_count += 1

sub(c, "5-1-3",
    "Encryption of Data at Rest in Cloud Storage",
    "تشفير البيانات الساكنة في التخزين السحابي",
    "Encryption-at-rest configuration for all cloud storage services (S3, Blob, GCS, RDS); "
    "CSP encryption report or configuration compliance scan; evidence AES-256 or equivalent "
    "algorithm in use; unencrypted bucket/container remediation log.",
    "All cloud storage containing Confidential or Restricted data must have server-side "
    "encryption enabled with CMK (customer-managed keys) for highest-sensitivity workloads.")
cscc_sub_count += 1

sub(c, "5-1-4",
    "Secrets Management for Cloud Workloads",
    "إدارة الأسرار لأحمال العمل السحابية",
    "Secrets management solution deployment (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault "
    "references); prohibition of hard-coded secrets in IaC/code (secret-scanning CI gate evidence); "
    "secret rotation policy and rotation audit log.",
    "All cloud application secrets (database passwords, API keys, certificates) must be stored "
    "in a secrets management solution with automated rotation and access audit logging.")
cscc_sub_count += 1

# ── CSCC Control 6: Cloud Logging, Monitoring & SIEM Integration ──────────────
c = control(cscc_fid, "CSCC-6", "Cloud Logging, Monitoring & SIEM Integration — CSP & CST",
            "تسجيل أحداث السحابة ومراقبتها وتكاملها مع SIEM — المزود والمستأجر",
            "Comprehensive logging of cloud management plane and data plane activity, "
            "forwarded to SIEM for correlation and alerting.")
cscc_ctrl_count += 1

sub(c, "6-1-1",
    "Cloud Management Plane Audit Logging (CloudTrail/Activity Log)",
    "تسجيل مستوى إدارة السحابة — CloudTrail / سجل النشاط",
    "CloudTrail / Azure Activity Log / GCP Audit Log enabled in all regions/subscriptions; "
    "log retention configuration (minimum 1 year); log integrity validation enabled; "
    "evidence forwarding to centralised SIEM.",
    "Management plane audit logs must be enabled across all cloud accounts and regions, "
    "retained for at least 12 months, with log integrity protection and SIEM forwarding.")
cscc_sub_count += 1

sub(c, "6-1-2",
    "Cloud Security Posture Management (CSPM) Continuous Monitoring",
    "إدارة وضع الأمن السيبراني السحابي — المراقبة المستمرة",
    "CSPM tool deployment evidence; coverage across all cloud accounts; daily/continuous scan "
    "schedule; critical/high findings SLA (e.g. P1 = 24 hours); monthly posture trend report.",
    "CSPM must continuously assess cloud configurations against security benchmarks (CIS, NCA), "
    "with critical findings remediated within defined SLAs and trends reported monthly.")
cscc_sub_count += 1

sub(c, "6-1-3",
    "SIEM Use Cases for Cloud Threat Detection",
    "حالات استخدام SIEM للكشف عن التهديدات السحابية",
    "Cloud-specific SIEM use-case catalogue (root-account usage, S3 public-access enablement, "
    "security group open-all rule, suspicious API call volume, geographic anomaly); alert test "
    "records; mean-time-to-detect metrics.",
    "SIEM must have cloud-specific detection use cases covering privilege escalation, "
    "data exfiltration, configuration drift, and anomalous API activity, tested at least annually.")
cscc_sub_count += 1

sub(c, "6-1-4",
    "Cloud VPC/Network Flow Log Monitoring",
    "مراقبة سجلات تدفق شبكة VPC السحابية",
    "VPC Flow Logs / NSG Flow Logs enabled for all production VPCs; retention policy; "
    "SIEM ingestion evidence; anomaly detection alert configuration for large outbound transfers "
    "or lateral movement patterns.",
    "Network flow logs must be enabled for all production VPCs, ingested into SIEM, and "
    "correlated with threat intelligence to detect lateral movement and data exfiltration.")
cscc_sub_count += 1

sub(c, "6-1-5",
    "Cloud Workload & Container Security Monitoring (CSP & CST)",
    "مراقبة أمن أحمال العمل والحاويات السحابية",
    "Cloud workload protection platform (CWPP) deployment coverage; container image scanning "
    "in CI/CD pipeline (policy-gate evidence); runtime threat detection alert logs; Kubernetes "
    "admission controller policy configuration.",
    "Cloud workloads and containers must be protected by a CWPP providing runtime threat detection, "
    "with container images scanned for vulnerabilities before deployment.")
cscc_sub_count += 1

# ── CSCC Control 7: Cloud Resilience & Business Continuity ────────────────────
c = control(cscc_fid, "CSCC-7", "Cloud Resilience & Business Continuity — CSP & CST",
            "المرونة السحابية واستمرارية الأعمال — المزود والمستأجر",
            "Availability, backup, disaster recovery, and exit/portability controls for "
            "cloud-hosted workloads.")
cscc_ctrl_count += 1

sub(c, "7-1-1",
    "Cloud Backup Policy & Recovery Testing (CST)",
    "سياسة النسخ الاحتياطي للسحابة واختبار الاسترداد — المستأجر",
    "Cloud backup policy specifying RPO/RTO per workload tier; automated backup configuration "
    "evidence; cross-region backup replication for Confidential/Restricted workloads; "
    "quarterly restore test records with pass/fail outcome.",
    "CST must define and technically enforce cloud backup policies with RPO/RTO commitments, "
    "testing restores at least quarterly and documenting results.")
cscc_sub_count += 1

sub(c, "7-1-2",
    "Disaster Recovery Plan for Cloud Workloads",
    "خطة التعافي من الكوارث لأحمال العمل السحابية",
    "Cloud DR plan document with failover architecture diagram; DR runbook per critical workload; "
    "annual DR test report with RTO/RPO achievement evidence; lessons-learned log.",
    "A cloud disaster recovery plan with documented runbooks must exist for all Tier-1 cloud "
    "workloads, with annual DR exercises testing failover procedures and RTO/RPO targets.")
cscc_sub_count += 1

sub(c, "7-1-3",
    "High Availability Architecture Design (CSP)",
    "تصميم بنية التوافر العالي — مزود الخدمة",
    "CSP SLA documentation; multi-AZ/multi-region deployment architecture for critical services; "
    "load balancer health-check configuration; auto-scaling policy; availability metrics "
    "report (monthly uptime vs. SLA).",
    "CSP must provide and document HA architecture for critical cloud services with multi-AZ "
    "or equivalent redundancy, with monthly SLA attainment reporting.")
cscc_sub_count += 1

sub(c, "7-1-4",
    "Cloud Exit & Data Portability Plan (CST)",
    "خطة الخروج من السحابة وقابلية نقل البيانات — المستأجر",
    "Cloud exit plan document for each material CSP dependency; data export/portability "
    "assessment (standard formats, API availability); exit timeline and resource estimates; "
    "annual review; contractual portability clause evidence in cloud agreement.",
    "CST must maintain a cloud exit plan for each material cloud service, documenting data "
    "export procedures, portability formats, migration timelines, and contractual portability rights.")
cscc_sub_count += 1

sub(c, "7-1-5",
    "Multi-Cloud & Vendor Lock-in Risk Management (CST)",
    "إدارة مخاطر الاعتماد على مورد واحد والسحابة المتعددة — المستأجر",
    "Vendor lock-in risk register; architectural decision records for proprietary service use; "
    "mitigation strategies (abstraction layers, open standards, multi-cloud distribution); "
    "annual review of lock-in risk by cloud governance committee.",
    "CST must assess and manage vendor lock-in risk for critical cloud workloads, with "
    "documented mitigation strategies reviewed annually.")
cscc_sub_count += 1

# ── CSCC Control 8: Third-Party Cloud Assurance & Supply Chain ────────────────
c = control(cscc_fid, "CSCC-8", "Third-Party Cloud Assurance & Supply Chain — CST",
            "ضمان الأطراف الثالثة وسلسلة توريد السحابة — المستأجر",
            "Due diligence, assurance, and contractual controls to manage risk from cloud "
            "service providers and sub-processors.")
cscc_ctrl_count += 1

sub(c, "8-1-1",
    "CSP Security Assurance Report Review (SOC 2 / ISO 27017)",
    "مراجعة تقارير ضمان أمن مزودي الخدمة — SOC 2 / ISO 27017",
    "Current-year SOC 2 Type II report (or ISO 27001/ISO 27017 certificate) for each material CSP; "
    "management review record noting exceptions and CSP remediation commitments; "
    "tracking of bridge letters between annual report cycles.",
    "CST must obtain and review CSP assurance reports (SOC 2 Type II or ISO 27017 certificate) "
    "annually, documenting exceptions and tracking CSP remediation of audit findings.")
cscc_sub_count += 1

sub(c, "8-1-2",
    "Cloud Contract Security Clauses (CST)",
    "بنود الأمن السيبراني في عقود الخدمات السحابية — المستأجر",
    "Cloud service agreement checklist covering: data residency, security incident notification SLA, "
    "audit right, data deletion on termination, sub-processor disclosure, law enforcement "
    "request notification; legal sign-off evidence.",
    "Cloud service agreements must include mandatory security clauses covering incident notification "
    "timelines (≤24 hours for critical), data deletion, audit rights, and sub-processor disclosure.")
cscc_sub_count += 1

sub(c, "8-1-3",
    "CSP Sub-Processor & Fourth-Party Risk Management (CST)",
    "إدارة المخاطر لمعالجي البيانات الفرعيين من الطرف الرابع — المستأجر",
    "CSP sub-processor disclosure list (extracted from cloud DPA/agreement); risk assessment "
    "for material sub-processors; change-notification clause evidence; escalation process "
    "for sub-processor changes affecting KSA data.",
    "CST must maintain visibility of CSP sub-processors processing KSA data and conduct "
    "risk assessment when new sub-processors are disclosed, with a formal objection process.")
cscc_sub_count += 1

sub(c, "8-1-4",
    "Cloud Penetration Testing & Vulnerability Management (CST)",
    "اختبار الاختراق وإدارة الثغرات في السحابة — المستأجر",
    "CSP penetration-testing authorisation obtained (cloud provider pen-test policy compliance); "
    "annual cloud workload penetration test report; critical/high finding remediation within "
    "SLA; cloud vulnerability scan coverage report from CSPM or scanner.",
    "CST must conduct annual penetration tests of cloud workloads, obtaining CSP authorisation, "
    "and remediate critical findings within 30 days and high findings within 90 days.")
cscc_sub_count += 1

sub(c, "8-1-5",
    "Cloud Security Configuration Baseline & IaC Policy-as-Code (CST)",
    "خط الأساس لإعدادات الأمن السحابي وسياسة الكود البنيوي — المستأجر",
    "Cloud security baseline standard document (CIS benchmark alignment); IaC policy-as-code "
    "gate in CI/CD pipeline (OPA/Conftest/Checkov evidence); compliance scan results "
    "(% compliant vs. baseline); drift-detection alerts and remediation log.",
    "A cloud security configuration baseline aligned to CIS benchmarks must be defined and "
    "enforced via Infrastructure-as-Code policy gates in CI/CD, with continuous drift detection.")
cscc_sub_count += 1

print(f"[CSCC-1:2020] Loaded: {cscc_ctrl_count} controls, {cscc_sub_count} sub-controls")

# ──────────────────────────────────────────────────────────────────────────────
conn.commit()
print()
print("=== NCA DCC + CSCC Seed Complete ===")
print(f"  DCC-1:2022  : {dcc_ctrl_count} controls, {dcc_sub_count} sub-controls")
print(f"  CSCC-1:2020 : {cscc_ctrl_count} controls, {cscc_sub_count} sub-controls")
print(f"  TOTAL       : {dcc_ctrl_count + cscc_ctrl_count} controls, {dcc_sub_count + cscc_sub_count} sub-controls")
conn.close()
