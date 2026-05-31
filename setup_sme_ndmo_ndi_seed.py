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
# Framework
# ---------------------------------------------------------------------------
fid = framework(
    code="NDMO-DMPDP-1",
    name_en="NDMO Data Management & Personal Data Protection Standards (incl. National Data Index)",
    name_ar="معايير إدارة البيانات وحماية البيانات الشخصية - مكتب إدارة البيانات الوطنية (شاملة مؤشر البيانات الوطني)",
    version="1.0",
    issuer="SDAIA / NDMO",
    desc_en=(
        "SDAIA/NDMO-issued standards governing data management maturity, personal data protection, and national data "
        "catalogue obligations across Saudi public and private sector entities. Encompasses 15 domains from Data "
        "Governance through Data Classification and Personal Data Protection, with embedded National Data Index (NDI) "
        "self-assessment expectations as the kingdom-wide maturity benchmark. — practitioner SME mapping; structure "
        "aligned to the published framework, control text paraphrased (not verbatim)."
    ),
)

control_count = 0
sub_count = 0

# ---------------------------------------------------------------------------
# Domain 1 — Data Governance
# ---------------------------------------------------------------------------
c1 = control(fid, "1", "Data Governance",
             "حوكمة البيانات",
             "Establish the organisational structures, policies, roles, and executive sponsorship required to manage "
             "data as a strategic asset across the entity, including NDI maturity self-assessment governance.")
control_count += 1

sub(c1, "1-1",
    "Data Governance Framework & Charter",
    "إطار حوكمة البيانات والميثاق التأسيسي",
    "Board/executive-approved data governance charter (signed, dated); data governance policy document; "
    "mandate letter for Chief Data Officer or equivalent; governance framework version history showing annual review.",
    "Establish a formal, board-endorsed data governance framework and charter that defines scope, objectives, "
    "guiding principles, and accountability hierarchy for data assets across the entity.")
sub_count += 1

sub(c1, "1-2",
    "Data Governance Roles & RACI",
    "أدوار حوكمة البيانات ومصفوفة المسؤوليات",
    "RACI matrix covering Data Owner, Data Steward, Data Custodian, and CDO roles; organisational chart "
    "showing data office placement; signed role-acceptance letters or HR job descriptions per role.",
    "Define and assign data governance roles including Data Owners, Stewards, and Custodians with documented "
    "responsibilities; publish a RACI matrix covering all data lifecycle decisions.")
sub_count += 1

sub(c1, "1-3",
    "Data Governance Committee & Operating Model",
    "لجنة حوكمة البيانات ونموذج التشغيل",
    "Data Governance Committee ToR (terms of reference); meeting minutes (minimum quarterly); escalation "
    "path document; integration evidence with enterprise risk and IT governance committees.",
    "Establish a Data Governance Committee with defined membership, terms of reference, meeting cadence, "
    "decision-making authority, and formal linkage to enterprise governance bodies.")
sub_count += 1

sub(c1, "1-4",
    "Data Governance Policies & Standards Register",
    "سجل سياسات ومعايير حوكمة البيانات",
    "Policy register listing each data governance policy with owner, version, approval date, and next-review "
    "date; evidence that policies are published on the intranet and communicated to staff.",
    "Maintain an approved, versioned register of data governance policies and standards covering the full "
    "data lifecycle; ensure policies are communicated, accessible, and reviewed at least annually.")
sub_count += 1

sub(c1, "1-5",
    "NDI Self-Assessment Submission & Maturity Tracking",
    "تقديم التقييم الذاتي لمؤشر البيانات الوطني ومتابعة النضج",
    "Completed NDMO NDI self-assessment submission for current year; gap analysis report against previous "
    "year's score; maturity improvement roadmap with milestones; NDI score trend dashboard screenshot.",
    "Complete and submit the National Data Index (NDI) annual self-assessment to SDAIA/NDMO on schedule; "
    "track maturity scores per domain and maintain a documented improvement roadmap.")
sub_count += 1

sub(c1, "1-6",
    "Data Governance Awareness & Training Programme",
    "برنامج التوعية والتدريب على حوكمة البيانات",
    "Annual training plan for data governance roles; completion records / LMS certificates for Data Owners "
    "and Stewards; awareness campaign materials; post-training assessment scores.",
    "Deliver role-appropriate data governance training and awareness programmes; track completion rates for "
    "mandatory data stewardship training and report results to the Governance Committee.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 2 — Data Catalog & Metadata
# ---------------------------------------------------------------------------
c2 = control(fid, "2", "Data Catalog & Metadata Management",
             "فهرس البيانات وإدارة البيانات الوصفية",
             "Maintain a comprehensive, searchable enterprise data catalog populated with authoritative metadata "
             "that enables discoverability, lineage tracing, and NDI catalogue obligations.")
control_count += 1

sub(c2, "2-1",
    "Enterprise Data Catalog Establishment",
    "إنشاء فهرس بيانات مؤسسي شامل",
    "Screenshot/export of active data catalog tool listing all registered data assets with dataset name, "
    "owner, classification, and system of record; data asset inventory count vs. total known systems; "
    "gap closure plan for unregistered systems.",
    "Establish and maintain an enterprise data catalog that inventories all data assets, systems of record, "
    "and data products; assign a Data Steward to each registered asset.")
sub_count += 1

sub(c2, "2-2",
    "Metadata Standards & Business Glossary",
    "معايير البيانات الوصفية والمسرد التجاري",
    "Published business glossary with term definitions, owners, and approval dates; metadata schema "
    "document specifying mandatory attributes per asset type; glossary coverage % across catalog assets.",
    "Define and enforce a metadata standard that includes mandatory business and technical metadata "
    "attributes; maintain a bilingual (Arabic/English) business glossary aligned to entity domain vocabulary.")
sub_count += 1

sub(c2, "2-3",
    "Data Lineage Mapping & Documentation",
    "رسم وتوثيق سلسلة نسب البيانات",
    "Data lineage diagrams for critical data elements (CDEs) showing source-to-consumption path; lineage "
    "coverage % report for Tier-1 systems; evidence that lineage is updated on system changes.",
    "Document end-to-end data lineage for critical data elements, covering source systems, transformation "
    "rules, and consuming applications; keep lineage current through change management integration.")
sub_count += 1

sub(c2, "2-4",
    "NDI Data Asset Submission & Catalogue Registration",
    "تسجيل أصول البيانات في مؤشر البيانات الوطني وفهرسته",
    "NDMO NDI portal submission receipts for all reportable datasets; mapping of internal catalog assets "
    "to NDI catalogue entries; NDI metadata completeness score per submitted dataset.",
    "Register all nationally reportable data assets in the NDMO National Data Index catalogue; ensure NDI "
    "metadata submissions are complete, accurate, and updated within mandated refresh cycles.")
sub_count += 1

sub(c2, "2-5",
    "Metadata Quality & Completeness Monitoring",
    "مراقبة جودة واكتمال البيانات الوصفية",
    "Monthly metadata completeness dashboard showing % of assets with all mandatory fields populated; "
    "automated metadata quality rules definition; trend chart over last 12 months; corrective action log.",
    "Continuously monitor the completeness and accuracy of metadata across the catalog; define metadata "
    "quality thresholds and publish periodic completeness reports to the Data Governance Committee.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 3 — Data Quality
# ---------------------------------------------------------------------------
c3 = control(fid, "3", "Data Quality Management",
             "إدارة جودة البيانات",
             "Define, measure, and continuously improve data quality across critical data elements using "
             "documented DQ rules, scorecards, and remediation workflows.")
control_count += 1

sub(c3, "3-1",
    "Data Quality Framework & Dimensions Definition",
    "إطار جودة البيانات وتعريف الأبعاد",
    "Approved data quality framework document covering completeness, accuracy, consistency, timeliness, "
    "uniqueness, and validity dimensions; DQ dimension definitions aligned to ISO 8000 or DAMA; "
    "DQ policy approval evidence.",
    "Adopt and document a data quality framework specifying the quality dimensions to be measured, "
    "acceptable thresholds per dimension, and the process for raising and resolving data quality issues.")
sub_count += 1

sub(c3, "3-2",
    "Critical Data Elements (CDE) Identification & Profiling",
    "تحديد العناصر البيانية الحرجة وتوصيفها",
    "CDE register listing each element with business owner, source system, quality dimension targets, and "
    "current scores; data profiling reports from profiling tool (e.g. Informatica, Talend, SQL queries) "
    "showing null rates, duplicate rates, format violations.",
    "Identify Critical Data Elements (CDEs) for each business domain; perform systematic data profiling "
    "against all CDEs and document baseline quality scores prior to remediation.")
sub_count += 1

sub(c3, "3-3",
    "Data Quality Rules & Automated Validation",
    "قواعد جودة البيانات والتحقق الآلي",
    "Data quality rules catalogue with rule ID, description, source system, and enforcement point; "
    "evidence of automated rule execution (scheduled job logs, pipeline validation reports); "
    "pass/fail rate trends per rule over 3 months.",
    "Define, implement, and automate data quality validation rules for all CDEs at ingestion, processing, "
    "and reporting stages; log rule execution results and escalate failures to the responsible Data Steward.")
sub_count += 1

sub(c3, "3-4",
    "Data Quality Scorecards & Reporting",
    "بطاقات جودة البيانات والتقارير الدورية",
    "Monthly DQ scorecard by domain showing dimension scores vs. targets; executive summary report "
    "presented to Data Governance Committee (meeting minutes evidence); historical trend chart (12 months); "
    "NDI data quality maturity rating documentation.",
    "Produce regular data quality scorecards per business domain; present DQ trends to the Data Governance "
    "Committee; use scorecard results to drive the NDI data quality maturity assessment.")
sub_count += 1

sub(c3, "3-5",
    "Data Quality Issue Management & Root-Cause Remediation",
    "إدارة مشكلات جودة البيانات ومعالجة الأسباب الجذرية",
    "Issue register / ticketing system export showing open DQ issues with owner, severity, and age; "
    "root-cause analysis reports for recurring issues; evidence of process or system fixes applied; "
    "mean-time-to-resolve (MTTR) trend.",
    "Maintain a data quality issue register; require root-cause analysis for recurring issues; track "
    "remediation to closure and report MTTR to the Governance Committee quarterly.")
sub_count += 1

sub(c3, "3-6",
    "Data Quality Improvement Roadmap",
    "خارطة طريق تحسين جودة البيانات",
    "Approved DQ improvement roadmap aligned to NDI maturity targets; project charters or initiative "
    "briefs for each improvement initiative; progress tracking dashboard with milestone completion %.",
    "Maintain a rolling data quality improvement roadmap aligned to NDI maturity targets; prioritise "
    "initiatives by business impact and track progress against milestones each quarter.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 4 — Data Operations
# ---------------------------------------------------------------------------
c4 = control(fid, "4", "Data Operations",
             "عمليات البيانات",
             "Govern the operational processes for data ingestion, integration, storage, retention, archival, "
             "and disposal to ensure data is managed reliably and in accordance with policy throughout its lifecycle.")
control_count += 1

sub(c4, "4-1",
    "Data Lifecycle Policy & Retention Schedule",
    "سياسة دورة حياة البيانات وجدول الاحتفاظ",
    "Board-approved data lifecycle policy; retention schedule document listing each data asset class with "
    "retention period, legal basis, and disposal method; evidence of periodic review (version history).",
    "Define and enforce a data lifecycle policy covering creation, storage, usage, archival, and secure "
    "disposal; publish a retention schedule with legally-grounded retention periods for each data class.")
sub_count += 1

sub(c4, "4-2",
    "Data Ingestion & Integration Standards",
    "معايير استيعاب البيانات وتكاملها",
    "Integration architecture diagram showing data flows between source systems; data integration standards "
    "document specifying API/file/streaming protocols; evidence of standards enforcement in project "
    "design reviews (review checklists signed by Data Architect).",
    "Establish integration standards for data ingestion (API, batch, streaming) covering format, frequency, "
    "error handling, and reconciliation requirements; enforce standards through architecture review gates.")
sub_count += 1

sub(c4, "4-3",
    "Data Storage, Backup & Recovery Controls",
    "ضوابط تخزين البيانات والنسخ الاحتياطي والاسترداد",
    "Storage architecture document; automated backup job logs with success/failure rates; tested recovery "
    "runbooks with last test date and RTO/RPO results; storage capacity monitoring dashboard.",
    "Implement storage controls that ensure data availability, integrity, and recovery within defined RTO/RPO "
    "targets; test backup and recovery procedures at least annually and document results.")
sub_count += 1

sub(c4, "4-4",
    "Data Disposal & Secure Deletion Procedures",
    "إجراءات إتلاف البيانات والحذف الآمن",
    "Secure deletion policy and procedure document; data disposal request forms with approvals; "
    "certificate of destruction for hard media; audit log from data erasure tool; annual disposal audit report.",
    "Define and follow secure data disposal procedures aligned to the retention schedule; obtain and "
    "retain certificates of destruction for all disposed data, including cloud and third-party data.")
sub_count += 1

sub(c4, "4-5",
    "Data Operations Monitoring & SLA Reporting",
    "مراقبة عمليات البيانات وتقارير اتفاقيات مستوى الخدمة",
    "Operations monitoring dashboard showing pipeline success rates, latency, and error counts; SLA "
    "definition document per data pipeline; monthly SLA compliance report; incident log for pipeline failures.",
    "Monitor data pipeline operations against defined SLAs covering freshness, completeness, and latency; "
    "report SLA compliance monthly to data stewards and escalate breaches to the Governance Committee.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 5 — Document & Content Management
# ---------------------------------------------------------------------------
c5 = control(fid, "5", "Document & Content Management",
             "إدارة الوثائق والمحتوى",
             "Govern the creation, versioning, classification, storage, retrieval, and disposal of unstructured "
             "documents and digital content as data assets of the entity.")
control_count += 1

sub(c5, "5-1",
    "Document Management Policy & Taxonomy",
    "سياسة إدارة الوثائق ونظام التصنيف التدرجي",
    "Approved document management policy; document taxonomy / filing structure document; "
    "DMS tool configuration screenshot showing taxonomy in use; governance committee approval evidence.",
    "Establish a document management policy and taxonomy that governs how documents are created, named, "
    "classified, stored, and retrieved; align taxonomy to the entity's data classification scheme.")
sub_count += 1

sub(c5, "5-2",
    "Document Version Control & Approval Workflows",
    "ضبط إصدارات الوثائق وسير عمل الاعتماد",
    "DMS version history report for a sample of controlled documents; approval workflow configuration "
    "screenshots; evidence that superseded versions are archived not deleted; change log per document.",
    "Enforce version control for all controlled documents; require formal approval workflows before "
    "publication; maintain full version history and ensure superseded versions are accessible for audit.")
sub_count += 1

sub(c5, "5-3",
    "Records Retention & Legal Hold Management",
    "الاحتفاظ بالسجلات وإدارة التجميد القانوني",
    "Records retention schedule aligned to Saudi National Archives Authority requirements; legal hold "
    "register listing active holds with legal basis and scope; evidence of hold notifications to custodians.",
    "Apply records retention rules to all managed documents; implement legal hold capabilities to suspend "
    "disposal when litigation, investigation, or regulatory inquiry is active.")
sub_count += 1

sub(c5, "5-4",
    "Content Accessibility & Search Enablement",
    "إمكانية الوصول للمحتوى وتفعيل البحث",
    "DMS search configuration evidence (full-text indexing enabled); user satisfaction survey results on "
    "content findability; metadata completeness report for DMS assets; accessibility audit report.",
    "Ensure managed documents and content are searchable, accessible to authorised users, and equipped "
    "with sufficient metadata to support discovery; report content accessibility metrics quarterly.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 6 — Data Architecture & Modelling
# ---------------------------------------------------------------------------
c6 = control(fid, "6", "Data Architecture & Data Modelling",
             "هندسة البيانات ونمذجة البيانات",
             "Define, govern, and maintain the enterprise data architecture and conceptual, logical, and physical "
             "data models that guide system design and data integration decisions.")
control_count += 1

sub(c6, "6-1",
    "Enterprise Data Architecture Vision & Principles",
    "رؤية هندسة البيانات المؤسسية ومبادئها",
    "Approved enterprise data architecture strategy document; data architecture principles register "
    "(min. 8 principles with rationale); evidence of architecture board endorsement.",
    "Define and publish enterprise data architecture vision and governing principles; obtain formal "
    "endorsement from the architecture governance body and review principles annually.")
sub_count += 1

sub(c6, "6-2",
    "Conceptual & Logical Data Models",
    "النماذج المفاهيمية والمنطقية للبيانات",
    "Published conceptual data model covering all core business domains; logical data models for "
    "Tier-1 systems in the data modeling tool repository; model review records signed by Data Architect "
    "and Business Owner.",
    "Maintain current conceptual and logical data models for all core business domains; store models "
    "in a governed repository and require review/approval before system changes are implemented.")
sub_count += 1

sub(c6, "6-3",
    "Data Architecture Review in Project Lifecycle",
    "مراجعة هندسة البيانات ضمن دورة حياة المشاريع",
    "Architecture review checklist used in project SDLC gates; sample of architecture review decisions "
    "(approved/rejected) from last 12 months; evidence that non-compliant designs are tracked to resolution.",
    "Embed data architecture review as a mandatory gate in the system development lifecycle; document "
    "review outcomes and track non-compliant designs to approved remediation plans.")
sub_count += 1

sub(c6, "6-4",
    "Data Ecosystem & Integration Architecture Map",
    "خريطة بيئة البيانات وهندسة التكامل",
    "Current-state data ecosystem diagram showing all systems, data flows, and integration patterns; "
    "integration pattern standards document; evidence of annual architecture map update.",
    "Maintain and publish a current-state data ecosystem map covering all systems, data flows, and "
    "integration patterns; update the map whenever significant system changes are approved.")
sub_count += 1

sub(c6, "6-5",
    "Data Technology Standards & Tooling Governance",
    "معايير تقنيات البيانات وحوكمة الأدوات",
    "Approved data technology standards register (databases, ETL, BI, cataloging tools); evidence of "
    "standards enforcement in procurement and project approval; annual review of technology radar.",
    "Maintain a data technology standards register governing approved tools for storage, integration, "
    "analytics, and cataloging; enforce through procurement and architecture review processes.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 7 — Reference & Master Data Management
# ---------------------------------------------------------------------------
c7 = control(fid, "7", "Reference & Master Data Management",
             "إدارة البيانات المرجعية والرئيسية",
             "Establish and govern golden records for critical master data entities and maintain authoritative "
             "reference data aligned to national and sector standards.")
control_count += 1

sub(c7, "7-1",
    "Master Data Domain Identification & Ownership",
    "تحديد نطاقات البيانات الرئيسية وتحديد ملاكها",
    "Master data domain register listing each domain (e.g. Customer, Supplier, Employee, Asset, Location) "
    "with assigned Data Owner and Steward; business justification for each domain's criticality classification.",
    "Identify all master data domains critical to the entity; assign a business Data Owner accountable "
    "for golden-record quality, completeness, and synchronisation across systems.")
sub_count += 1

sub(c7, "7-2",
    "Golden Record Management & MDM Platform",
    "إدارة السجل الذهبي ومنصة البيانات الرئيسية",
    "MDM platform architecture document; golden-record definition per domain (matching rules, survivorship "
    "rules); duplicate detection rate report; golden-record adoption % in consuming systems.",
    "Implement master data management processes and tooling to create and maintain golden records for "
    "each master data domain; document matching and survivorship rules; publish adoption metrics quarterly.")
sub_count += 1

sub(c7, "7-3",
    "Reference Data Governance & Codelist Management",
    "حوكمة البيانات المرجعية وإدارة قوائم الترميز",
    "Reference data registry listing all code lists with owner, source authority (e.g. SAMA, GAZT, "
    "NDMO national classifications), and last-review date; change request log for reference data updates; "
    "evidence of synchronisation with national/sector reference standards.",
    "Govern all reference data (code lists, lookup tables, enumerations) through a central registry; "
    "align to Saudi national standards where applicable and control changes via a formal change process.")
sub_count += 1

sub(c7, "7-4",
    "MDM Data Quality & Reconciliation Reporting",
    "جودة البيانات الرئيسية وتقارير المطابقة",
    "Monthly MDM data quality scorecard per domain showing duplicate rate, completeness, and match "
    "confidence; reconciliation report between MDM hub and downstream systems; data steward sign-off evidence.",
    "Monitor master data quality across all domains; produce monthly reconciliation reports comparing "
    "the MDM hub record count and quality scores against downstream consuming systems.")
sub_count += 1

sub(c7, "7-5",
    "MDM Integration & Synchronisation Standards",
    "معايير تكامل ومزامنة البيانات الرئيسية",
    "MDM integration architecture diagram showing all publishing and subscribing systems; "
    "synchronisation SLA document per domain; latency monitoring dashboard; exception handling procedure.",
    "Define integration standards for distributing golden records to consuming systems; monitor "
    "synchronisation latency and completeness; escalate integration failures to the Data Operations team.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 8 — Business Intelligence & Analytics
# ---------------------------------------------------------------------------
c8 = control(fid, "8", "Business Intelligence & Analytics",
             "ذكاء الأعمال والتحليلات",
             "Govern the delivery, quality, and trustworthiness of business intelligence and analytics products "
             "to ensure decision-makers consume accurate, governed data insights.")
control_count += 1

sub(c8, "8-1",
    "BI & Analytics Governance Framework",
    "إطار حوكمة ذكاء الأعمال والتحليلات",
    "BI governance policy document; BI center-of-excellence or analytics team charter; report ownership "
    "register listing each certified report with owner and approval date.",
    "Establish a BI & analytics governance framework that defines standards for report development, "
    "certification, publication, and retirement; assign ownership for all production reports and dashboards.")
sub_count += 1

sub(c8, "8-2",
    "Data Warehouse & Analytical Data Store Management",
    "إدارة مستودع البيانات والمخازن التحليلية",
    "Data warehouse architecture document; model documentation for fact and dimension tables; "
    "change management log for warehouse schema changes; data freshness SLA report.",
    "Govern the design, change management, and quality of the data warehouse or analytical data store; "
    "require architecture review for structural changes and publish data freshness SLAs.")
sub_count += 1

sub(c8, "8-3",
    "Self-Service Analytics Enablement & Guardrails",
    "تمكين التحليلات الذاتية وضوابطها",
    "Self-service analytics platform access control policy; training completion records for self-service "
    "users; certified dataset register available to self-service users; data literacy programme materials.",
    "Enable self-service analytics on certified, governed datasets while enforcing access controls and "
    "data literacy training requirements; maintain a register of certified datasets available for self-service.")
sub_count += 1

sub(c8, "8-4",
    "Analytics Output Validation & Reconciliation",
    "التحقق من مخرجات التحليلات ومطابقتها",
    "Report reconciliation procedure document; sample reconciliation test results for top-5 management "
    "reports; UAT sign-off records for new reports before production deployment.",
    "Validate analytical outputs against source data through formal reconciliation procedures before "
    "publishing to management; require user acceptance testing and sign-off for new production reports.")
sub_count += 1

sub(c8, "8-5",
    "AI & Advanced Analytics Ethics and Governance",
    "أخلاقيات وحوكمة الذكاء الاصطناعي والتحليلات المتقدمة",
    "AI/ML model inventory register with model owner, purpose, and risk classification; ethical AI "
    "review checklist applied to each model before production; bias assessment report for high-risk models; "
    "alignment documentation with SDAIA AI Ethics Principles.",
    "Apply governance and ethical review processes to AI and advanced analytics models; maintain a model "
    "inventory; assess models for bias and fairness; align to SDAIA AI Ethics Principles.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 9 — Data Security & Protection
# ---------------------------------------------------------------------------
c9 = control(fid, "9", "Data Security & Protection",
             "أمن البيانات وحمايتها",
             "Protect data assets against unauthorised access, disclosure, modification, and loss through "
             "technical and administrative controls commensurate with data classification and sensitivity.")
control_count += 1

sub(c9, "9-1",
    "Data Security Policy & Standards",
    "سياسة أمن البيانات ومعاييرها",
    "Board-approved data security policy; technical data security standards document covering encryption, "
    "masking, access, and DLP; evidence of annual policy review; alignment mapping to NCA ECC controls.",
    "Establish and enforce a data security policy and technical standards covering encryption at rest/in "
    "transit, data masking, access control, DLP, and data loss incident response; review annually.")
sub_count += 1

sub(c9, "9-2",
    "Data Access Control & Privileged User Management",
    "ضبط الوصول للبيانات وإدارة المستخدمين المميزين",
    "Role-based access control (RBAC) matrix for critical data stores; quarterly access recertification "
    "report signed by Data Owners; privileged access review log; segregation of duties conflict report.",
    "Implement role-based access controls for all sensitive data stores; perform quarterly access "
    "recertification with Data Owner sign-off; enforce least-privilege and segregation of duties.")
sub_count += 1

sub(c9, "9-3",
    "Data Encryption & Masking Controls",
    "ضوابط تشفير البيانات وإخفاء هويتها",
    "Encryption standards document specifying algorithms and key lengths per classification level; "
    "encryption coverage report for data at rest and in transit; data masking configuration for "
    "non-production environments; key management procedure document.",
    "Enforce encryption of sensitive and classified data at rest and in transit using approved algorithms; "
    "apply data masking in non-production environments; manage encryption keys through a formal key "
    "management process.")
sub_count += 1

sub(c9, "9-4",
    "Data Loss Prevention (DLP) Controls",
    "ضوابط منع تسرب البيانات",
    "DLP tool deployment scope document; DLP policy rule set configuration export; monthly DLP incident "
    "report showing policy violation counts by severity; evidence of DLP rule tuning based on incidents.",
    "Deploy and tune DLP controls aligned to the data classification scheme; monitor and report DLP "
    "policy violations monthly; escalate high-severity violations to the CISO within 24 hours.")
sub_count += 1

sub(c9, "9-5",
    "Data Security Incident Response & Breach Notification",
    "الاستجابة لحوادث أمن البيانات وإخطار الاختراق",
    "Data security incident response procedure; data breach notification workflow showing NDMO/SDAIA "
    "notification path and timelines; sample incident report (anonymised) showing containment and "
    "notification steps; breach drill exercise report.",
    "Maintain a data security incident response procedure with defined escalation, containment, and "
    "breach notification steps; notify NDMO/SDAIA within the regulatory timeframe for confirmed personal "
    "data breaches; conduct annual breach response drills.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 10 — Data Sharing & Interoperability
# ---------------------------------------------------------------------------
c10 = control(fid, "10", "Data Sharing & Interoperability",
              "مشاركة البيانات وقابلية التشغيل البيني",
              "Enable controlled, governed, and auditable sharing of data across entities and with the national "
              "data ecosystem while ensuring interoperability through standards-based interfaces.")
control_count += 1

sub(c10, "10-1",
    "Data Sharing Policy & Governance Framework",
    "سياسة مشاركة البيانات وإطار حوكمتها",
    "Board-approved data sharing policy; data sharing request and approval procedure; data sharing "
    "register listing active sharing arrangements with counterparty, purpose, legal basis, and expiry date.",
    "Establish a data sharing policy and governance framework governing who can share what data with "
    "whom, under what legal basis, and subject to what controls; maintain a register of all active "
    "data sharing arrangements.")
sub_count += 1

sub(c10, "10-2",
    "Data Sharing Agreements (DSAs) & Legal Basis Documentation",
    "اتفاقيات مشاركة البيانات والأساس القانوني",
    "Signed DSA or MOU for each active sharing arrangement; legal basis assessment document per "
    "arrangement (citing Personal Data Protection Law article where personal data is involved); "
    "DSA renewal calendar.",
    "Execute Data Sharing Agreements for all inter-entity data exchanges; document the legal basis "
    "for each arrangement; review and renew DSAs before expiry and archive terminated agreements.")
sub_count += 1

sub(c10, "10-3",
    "Interoperability Standards & API Governance",
    "معايير قابلية التشغيل البيني وحوكمة واجهات برمجة التطبيقات",
    "Interoperability standards document (e.g. NDMO data exchange standards, Saudi GOV-API guidelines); "
    "API catalogue listing all published APIs with version, owner, and consumer list; API design review "
    "gate evidence.",
    "Adopt and enforce interoperability and data exchange standards aligned to NDMO and national "
    "e-government frameworks; govern APIs through a formal API catalogue and design review process.")
sub_count += 1

sub(c10, "10-4",
    "National Data Exchange Platform (NDP) Integration",
    "التكامل مع منصة تبادل البيانات الوطنية",
    "NDP integration architecture diagram; list of datasets onboarded to NDP with metadata completeness "
    "scores; NDP portal connection test evidence; data quality certification before NDP publication.",
    "Integrate with the NDMO-designated National Data Platform (NDP) or equivalent; ensure all datasets "
    "shared nationally are quality-certified and meet NDP metadata and format requirements.")
sub_count += 1

sub(c10, "10-5",
    "Data Sharing Audit Trail & Usage Monitoring",
    "مسار التدقيق على مشاركة البيانات ومراقبة الاستخدام",
    "Audit log export for data sharing API calls (last 90 days); anomaly detection report for "
    "unusual sharing volumes; annual review of sharing arrangements by Data Governance Committee (minutes).",
    "Maintain an immutable audit trail for all data sharing events; monitor data sharing for anomalous "
    "usage patterns; present annual sharing activity review to the Data Governance Committee.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 11 — Data Value Realisation
# ---------------------------------------------------------------------------
c11 = control(fid, "11", "Data Value Realisation",
              "تحقيق القيمة من البيانات",
              "Identify, measure, and maximise the economic and operational value generated from the entity's "
              "data assets through structured value frameworks and monetisation pathways.")
control_count += 1

sub(c11, "11-1",
    "Data Value Framework & Benefit Measurement",
    "إطار قيمة البيانات وقياس المنافع",
    "Approved data value framework document defining value categories (operational, commercial, social); "
    "benefits realisation register for data initiatives with baseline, target, and actual KPIs; "
    "annual data value report presented to leadership.",
    "Define a data value framework that categorises and measures the benefits derived from data assets; "
    "track benefits realisation for data programmes and report annually to executive leadership.")
sub_count += 1

sub(c11, "11-2",
    "Data Monetisation & Innovation Opportunities Register",
    "سجل فرص تحقيق الدخل من البيانات والابتكار",
    "Data monetisation opportunity register with each initiative's description, owner, stage, and "
    "estimated value; evidence of board/executive review; privacy and security impact assessment "
    "for monetisation initiatives involving personal data.",
    "Identify and govern data monetisation and innovation opportunities; assess each opportunity for "
    "privacy, security, and ethical risks before implementation; present the register to leadership "
    "at least annually.")
sub_count += 1

sub(c11, "11-3",
    "Data Literacy & Data Culture Programme",
    "برنامج محو أمية البيانات وثقافة البيانات",
    "Data literacy strategy document; training curriculum by role; completion rate report (% of staff "
    "completing each tier of training); data culture survey results (annual) with trend; "
    "champions network membership list.",
    "Build data literacy across the entity through a tiered training programme and data culture "
    "initiatives; measure literacy levels annually via survey and track improvement trends.")
sub_count += 1

sub(c11, "11-4",
    "Data Innovation Lab & Pilot Programme Governance",
    "حوكمة مختبر ابتكار البيانات وبرنامج الحالات التجريبية",
    "Data innovation lab (or sandbox) governance document; pilot project register with hypothesis, "
    "dataset used, privacy safeguards applied, and outcome; stage-gate review evidence for pilots "
    "progressing to production.",
    "Govern data innovation pilots through a structured sandbox environment with defined privacy "
    "and security guardrails; document pilot outcomes and apply stage-gate reviews before scaling.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 12 — Open Data
# ---------------------------------------------------------------------------
c12 = control(fid, "12", "Open Data",
              "البيانات المفتوحة",
              "Publish high-value government datasets as open data in machine-readable formats through the "
              "national open data portal, in compliance with NDMO open data standards and licensing requirements.")
control_count += 1

sub(c12, "12-1",
    "Open Data Policy & Publication Strategy",
    "سياسة البيانات المفتوحة واستراتيجية النشر",
    "Board-approved open data policy; open data publication strategy with dataset prioritisation criteria; "
    "evidence of alignment with data.gov.sa or sector-specific portal requirements.",
    "Adopt an open data policy and publication strategy aligned to NDMO open data guidelines; prioritise "
    "high-value datasets for publication and establish a release cadence.")
sub_count += 1

sub(c12, "12-2",
    "Open Data Dataset Register & Quality Certification",
    "سجل مجموعات البيانات المفتوحة وشهادة الجودة",
    "Open data dataset register listing each published dataset with owner, format, licence, update "
    "frequency, and quality score; DQ certification checklist completed before publication; "
    "data portal publication confirmation.",
    "Maintain an internal register of published and candidate open datasets; certify dataset quality "
    "and completeness before publication; assign a dataset owner accountable for ongoing updates.")
sub_count += 1

sub(c12, "12-3",
    "Open Data Licensing & Intellectual Property Management",
    "ترخيص البيانات المفتوحة وإدارة الملكية الفكرية",
    "Licence assignment record per dataset (e.g. Saudi Open Data Licence terms); IP review evidence "
    "confirming no third-party IP conflicts; legal opinion for datasets with potential commercial sensitivity.",
    "Assign appropriate open data licences to all published datasets; conduct IP and sensitivity reviews "
    "to prevent inadvertent publication of restricted or commercially sensitive content.")
sub_count += 1

sub(c12, "12-4",
    "Open Data Portal Maintenance & User Engagement",
    "صيانة بوابة البيانات المفتوحة والتفاعل مع المستخدمين",
    "Portal availability and uptime report (monthly); dataset download and API usage statistics; "
    "user feedback / issue tracker for data quality reports from portal users; evidence of issue resolution.",
    "Maintain open data portal listings with current, accurate metadata; monitor portal usage and "
    "respond to user-reported data quality issues within defined SLAs; publish usage statistics quarterly.")
sub_count += 1

sub(c12, "12-5",
    "Open Data Impact Assessment & Value Reporting",
    "تقييم أثر البيانات المفتوحة وإعداد تقارير القيمة",
    "Annual open data impact report covering dataset downloads, known reuse cases, and estimated "
    "economic/social value; survey results from data ecosystem stakeholders on open data usefulness.",
    "Measure and report the impact of open data publication annually; collect evidence of external "
    "reuse and estimate economic and social value generated; present findings to leadership.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 13 — Freedom of Information
# ---------------------------------------------------------------------------
c13 = control(fid, "13", "Freedom of Information",
              "حرية تداول المعلومات",
              "Establish transparent and timely processes for receiving, processing, and responding to public "
              "information requests in compliance with applicable Saudi legislation.")
control_count += 1

sub(c13, "13-1",
    "FOI Policy & Legal Framework Alignment",
    "سياسة حرية المعلومات والتوافق مع الإطار القانوني",
    "Approved FOI/information access policy document citing applicable legislation; roles and "
    "responsibilities document for FOI handling; evidence of policy publication on entity website.",
    "Adopt a Freedom of Information policy aligned to applicable Saudi legislation and NDMO guidance; "
    "publish the policy publicly and designate an accountable FOI Officer.")
sub_count += 1

sub(c13, "13-2",
    "FOI Request Register & Response Tracking",
    "سجل طلبات حرية المعلومات ومتابعة الردود",
    "FOI request register export showing all requests received in the period with date received, "
    "request type, assigned handler, response date, and outcome (disclosed/partially disclosed/refused); "
    "on-time response rate KPI.",
    "Maintain a complete register of all FOI requests; track each request through receipt, assessment, "
    "and response; report on-time response rates against statutory timelines monthly.")
sub_count += 1

sub(c13, "13-3",
    "FOI Exemption Assessment & Refusal Documentation",
    "تقييم استثناءات حرية المعلومات وتوثيق الرفض",
    "Exemption assessment template with exemption categories and harm test; sample refusal letters with "
    "exemption grounds cited; appeals register; legal review sign-off for complex refusals.",
    "Apply a documented harm test before invoking exemptions to FOI requests; record all exemption "
    "decisions with full reasoning; maintain an appeals register and escalate complex cases for legal review.")
sub_count += 1

sub(c13, "13-4",
    "FOI Annual Reporting & Performance Review",
    "التقارير السنوية وتقييم أداء حرية المعلومات",
    "Annual FOI performance report covering request volumes, response rates, exemption statistics, "
    "and appeal outcomes; evidence of report submission to relevant oversight body; improvement plan "
    "addressing identified gaps.",
    "Publish an annual FOI performance report covering volumes, timeliness, exemption usage, and "
    "appeals; submit the report to the designated oversight authority and derive improvement actions.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 14 — Personal Data Protection
# ---------------------------------------------------------------------------
c14 = control(fid, "14", "Personal Data Protection",
              "حماية البيانات الشخصية",
              "Protect the privacy rights of data subjects in compliance with the Saudi Personal Data Protection "
              "Law (PDPL) and NDMO Personal Data Protection Standards, through lawful processing, data subject "
              "rights management, and breach response.")
control_count += 1

sub(c14, "14-1",
    "Privacy Governance & Data Protection Officer",
    "حوكمة الخصوصية ومسؤول حماية البيانات",
    "DPO appointment letter or equivalent role charter; DPO contact details published on entity website; "
    "privacy governance committee ToR; annual DPO report to board/leadership.",
    "Appoint a Data Protection Officer (or equivalent) with documented authority; establish privacy "
    "governance structures; publish DPO contact details; ensure DPO reports directly to senior leadership.")
sub_count += 1

sub(c14, "14-2",
    "Record of Processing Activities (ROPA)",
    "سجل أنشطة المعالجة",
    "ROPA spreadsheet/system export listing each processing activity with controller details, purpose, "
    "categories of data subjects and data, legal basis, retention period, and third-party transfers; "
    "evidence of quarterly ROPA review and update.",
    "Maintain a complete and current Record of Processing Activities (ROPA) covering all personal data "
    "processing; review and update the ROPA at least quarterly and whenever new processing is introduced.")
sub_count += 1

sub(c14, "14-3",
    "Consent Management & Lawful Basis Documentation",
    "إدارة الموافقة وتوثيق الأساس القانوني",
    "Consent management platform configuration or consent register showing consent records with "
    "timestamp, version of consent text, and opt-out records; lawful basis assessment document per "
    "processing activity citing PDPL article; privacy notice review record.",
    "Document the lawful basis for each personal data processing activity under the PDPL; implement "
    "a consent management mechanism where consent is the lawful basis; maintain records of consent "
    "granted and withdrawn.")
sub_count += 1

sub(c14, "14-4",
    "Data Subject Rights Management (DSAR Process)",
    "إدارة حقوق أصحاب البيانات (آلية طلبات الوصول)",
    "DSAR procedure document covering all PDPL rights (access, correction, deletion, portability, "
    "objection); DSAR request register with receipt date, right exercised, response date, and outcome; "
    "on-time response rate vs. PDPL statutory deadline.",
    "Implement a process for receiving and responding to Data Subject Access Requests (DSARs) for all "
    "rights granted under the PDPL; track DSARs and report response timeliness monthly.")
sub_count += 1

sub(c14, "14-5",
    "Privacy Impact Assessment (DPIA) Process",
    "عملية تقييم أثر الخصوصية",
    "DPIA procedure and threshold assessment criteria; completed DPIAs for high-risk processing "
    "activities (last 12 months); DPO sign-off records on each DPIA; evidence that DPIA findings "
    "are tracked to remediation.",
    "Conduct Data Protection Impact Assessments (DPIAs) for high-risk personal data processing; "
    "document assessment methodology and findings; obtain DPO sign-off; track remediation actions "
    "to closure.")
sub_count += 1

sub(c14, "14-6",
    "Third-Party & Cross-Border Data Transfer Controls",
    "ضوابط نقل البيانات عبر الحدود والأطراف الثالثة",
    "Third-party processor agreements (DPA clauses) for all vendors processing personal data on "
    "behalf of the entity; cross-border transfer risk assessment for transfers outside KSA citing "
    "PDPL adequacy or safeguards; vendor due-diligence checklist.",
    "Require Data Processing Agreements with all third-party processors; assess cross-border transfers "
    "for adequacy under the PDPL; conduct privacy due diligence on vendors before onboarding.")
sub_count += 1

sub(c14, "14-7",
    "Personal Data Breach Management & NDMO Notification",
    "إدارة خروقات البيانات الشخصية والإخطار للمكتب الوطني",
    "Personal data breach response procedure with NDMO notification workflow and timeline (72-hour "
    "target); breach register with severity classification, notification dates, and lessons learned; "
    "annual breach simulation exercise report.",
    "Maintain a personal data breach management procedure; notify NDMO within 72 hours of confirmed "
    "high-risk breaches; maintain a breach register; conduct annual breach simulation exercises and "
    "apply lessons learned.")
sub_count += 1

# ---------------------------------------------------------------------------
# Domain 15 — Data Classification
# ---------------------------------------------------------------------------
c15 = control(fid, "15", "Data Classification",
              "تصنيف البيانات",
              "Classify all data assets according to the Saudi four-level classification scheme and apply "
              "commensurate protection controls aligned to NDMO Data Classification Standard and NCA requirements.")
control_count += 1

sub(c15, "15-1",
    "Data Classification Policy & Four-Level Scheme Adoption",
    "سياسة تصنيف البيانات واعتماد النظام الرباعي",
    "Board-approved data classification policy citing the four classification levels: Top Secret (سري للغاية), "
    "Secret (سري), Restricted (محظور), and Public (عام); mapping of policy to NDMO Data Classification "
    "Standard and NCA ECC requirements; evidence of annual policy review.",
    "Adopt the Saudi national four-level data classification scheme (Top Secret / Secret / Restricted / "
    "Public) as the single enterprise standard; publish the policy with handling rules per level and "
    "align to NDMO and NCA classification requirements.")
sub_count += 1

sub(c15, "15-2",
    "Data Classification Procedures & Labelling Standards",
    "إجراءات تصنيف البيانات ومعايير التوسيم",
    "Data classification procedure document with decision tree and worked examples per data type; "
    "labelling standards document specifying label format for structured data, documents, and emails; "
    "evidence of labelling tool deployment (e.g. Microsoft Purview sensitivity labels or equivalent).",
    "Define step-by-step procedures for classifying data assets and applying classification labels "
    "at the point of creation and ingestion; enforce labelling through technical controls where possible.")
sub_count += 1

sub(c15, "15-3",
    "Data Asset Classification Inventory & Coverage",
    "مخزون تصنيف أصول البيانات ونسبة التغطية",
    "Classification inventory report showing % of data assets classified by level across all systems; "
    "unclassified asset remediation plan with target date; classification coverage trend (monthly).",
    "Classify all data assets in the enterprise data catalog; track classification coverage as a KPI; "
    "target 100% coverage for Tier-1 systems and publish monthly progress to the Governance Committee.")
sub_count += 1

sub(c15, "15-4",
    "Classification-Aligned Security & Handling Controls",
    "ضوابط الأمن والتعامل المنسجمة مع التصنيف",
    "Security control matrix mapping each classification level to required technical controls (encryption, "
    "access restrictions, DLP rules, watermarking); evidence of controls applied per level in Tier-1 "
    "systems; exception register with risk acceptance sign-off.",
    "Define and enforce a security control matrix that maps each classification level to mandatory "
    "technical and administrative handling controls; document exceptions and require risk-owner sign-off.")
sub_count += 1

sub(c15, "15-5",
    "Data Classification Awareness & Staff Training",
    "التوعية بتصنيف البيانات وتدريب الموظفين",
    "Annual classification training completion records (% of staff by role); classification quick-reference "
    "card distributed to staff; phishing/classification scenario test results; awareness campaign evidence.",
    "Deliver annual data classification training to all staff; tailor content by role (general awareness "
    "vs. data handler deep-dive); measure comprehension via assessment and track completion rates.")
sub_count += 1

sub(c15, "15-6",
    "Classification Review, Reclassification & Declassification Process",
    "عملية مراجعة التصنيف وإعادة التصنيف والرفع عنه",
    "Reclassification/declassification request and approval procedure; reclassification request log "
    "with approver, rationale, and effective date; periodic review schedule (at least every 2 years) "
    "for Secret and Restricted assets.",
    "Establish a formal process for reviewing, reclassifying, and declassifying data assets; require "
    "business-owner and Data Governance Committee approval for reclassification; review all Secret and "
    "Restricted assets at least every two years.")
sub_count += 1

# ---------------------------------------------------------------------------
# Commit & summary
# ---------------------------------------------------------------------------
conn.commit()
conn.close()

print(f"NDMO-DMPDP-1 seed complete.")
print(f"  Controls (domains) inserted/updated : {control_count}")
print(f"  Sub-controls inserted/updated       : {sub_count}")
