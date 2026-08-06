# Module Definitions
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document provides detailed definitions for each of the ten modules in the TSS GRC Platform. For every module, it specifies the purpose, core capabilities, primary actors, key entities, important workflows, inputs, outputs, cross-module dependencies, licensing independence, reporting impact, audit requirements, and database/storage implications.

---

## Module 1: Enterprise Risk Management (ERM)

### Purpose

Provide a comprehensive enterprise risk management capability aligned with ISO 31000. Enable organizations to identify, assess, treat, monitor, and report on risks across all business functions, integrating risk inputs from cybersecurity, business continuity, privacy, and compliance domains into a unified enterprise risk view.

### Core Capabilities

- Risk register management (create, categorize, assess, treat, monitor, close)
- Configurable risk taxonomy (categories, subcategories, tags)
- Dual assessment methodology (qualitative and semi-quantitative)
- Inherent, residual, and target risk scoring
- Configurable impact and likelihood scales (3x3 through 7x7)
- Risk appetite and tolerance statement management
- Risk heatmap generation (interactive, filterable)
- Key Risk Indicator (KRI) definition, data collection, threshold monitoring
- Risk treatment plan management (mitigate, accept, transfer, avoid)
- Treatment action tracking with milestones and deadlines
- Risk aggregation across business units and categories
- Risk escalation workflows (from other modules and within ERM)
- Risk owner assignment and accountability tracking
- Scenario analysis and what-if modeling
- Risk assessment campaign management (bulk assessments across BUs)
- Historical risk trending and comparison

### Primary Actors

- Risk Manager (primary owner)
- CRO / Head of Risk (executive oversight)
- Business Unit Managers (risk owners, assessment participants)
- Executive / Board Members (appetite approval, risk acceptance)
- Internal Auditor (read-only review)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Risk** | A risk entry in the enterprise risk register | ID, title (AR/EN), description, category, subcategory, risk owner, business unit, status, inherent score, residual score, target score, risk source, linked controls, creation date, last assessment date |
| **Risk Assessment** | A point-in-time evaluation of a risk | ID, risk ID, assessment date, assessor, impact score, likelihood score, calculated score, assessment type (inherent/residual/target), justification, evidence references |
| **Risk Category** | Taxonomy element for risk classification | ID, name (AR/EN), parent category, description, sort order, active flag |
| **Risk Treatment** | Action plan to modify risk level | ID, risk ID, treatment type (mitigate/accept/transfer/avoid), description, owner, status, start date, target date, actual completion date, cost estimate |
| **Treatment Action** | Individual action within a treatment plan | ID, treatment ID, description, assignee, due date, status, completion evidence, percentage complete |
| **Risk Appetite Statement** | Board-approved risk tolerance level | ID, category, appetite level, tolerance threshold, description, approval date, approver, review date |
| **Key Risk Indicator (KRI)** | Measurable indicator of risk level | ID, name, description, risk linkage, data source, collection frequency, current value, green/amber/red thresholds, trend direction |
| **Impact Scale** | Configurable impact scoring criteria | ID, level, label (AR/EN), description, financial range, reputational impact, operational impact, regulatory impact |
| **Likelihood Scale** | Configurable probability scoring criteria | ID, level, label (AR/EN), description, frequency range, probability percentage |
| **Risk Assessment Campaign** | Coordinated assessment across multiple risks/BUs | ID, name, scope (BU/category), start date, end date, status, participants, completion percentage |

### Important Workflows

1. **Risk Identification and Registration:** New risk identified (manual input, module escalation, or external event) -> Risk created in register -> Categorized using taxonomy -> Risk owner assigned -> Initial assessment triggered -> Workflow notification to Risk Manager
2. **Risk Assessment Cycle:** Risk Manager initiates assessment campaign -> Assessment tasks distributed to risk owners -> Risk owners provide input -> Risk Manager reviews and validates -> Scores calculated -> Heatmap updated -> Treatments triggered for risks exceeding appetite
3. **Treatment Lifecycle:** Treatment plan created -> Actions defined with owners and deadlines -> Progress tracked -> Milestones monitored -> Upon completion, residual risk reassessed -> Risk Manager validates -> Risk status updated
4. **KRI Monitoring:** KRI data collected (manual or integration) -> Thresholds evaluated -> Breaches trigger alerts -> Risk Manager investigates -> Risk assessment updated if needed -> Escalation to CRO if critical
5. **Cross-Module Risk Escalation:** Cyber/BCM/Privacy module identifies significant risk -> Escalation workflow triggered -> Risk created/linked in enterprise register -> Enterprise-level assessment performed -> Treatment integrated into enterprise plan

### Inputs

- Risk submissions from business units
- Risk escalations from Cybersecurity, BCM, and Privacy modules
- Control effectiveness data from Compliance module
- Incident data from Cybersecurity and BCM modules
- External risk intelligence (manual input)
- KRI data feeds (manual or scheduled)

### Outputs

- Enterprise risk register (filterable, exportable)
- Risk heatmaps (inherent, residual, target)
- KRI dashboards with trend analysis
- Treatment progress reports
- Board risk reports (PDF/Excel export)
- Risk data for Reporting & Analytics module
- Risk appetite alignment reports

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| Cybersecurity | Receives data | Cyber risks escalated to enterprise register |
| BCM | Receives data | BCM risks escalated to enterprise register |
| Privacy | Receives data | Privacy risks escalated to enterprise register |
| Compliance | Receives data | Control effectiveness informs risk assessment |
| Workflow | Uses service | Approval workflows for risk acceptance, treatment plans |
| Reporting | Provides data | Risk data feeds into unified dashboards and reports |
| Administration | Uses service | Organization hierarchy, user/role data, lookup tables |

### Licensing Independence

ERM can be licensed independently as a standalone module. When licensed alone, it functions as a self-contained risk register with manual data entry. Cross-module risk escalation features activate only when source modules are also licensed.

### Reporting Impact

ERM is the primary data source for the unified risk view. It contributes: risk heatmaps, top-N risk lists, KRI trend dashboards, treatment progress charts, risk distribution by category/BU, and risk appetite alignment indicators.

### Audit Requirements

- All risk register changes must be captured in the audit trail (who, what, when, before/after values)
- Risk assessments must be immutable once finalized (new assessments create new records)
- Treatment actions must track completion evidence
- KRI threshold changes must be audited
- Risk appetite changes require approval workflow with full audit trail

### Database/Storage Implications

- Risk register is the most frequently queried entity; requires optimized indexing on status, category, BU, owner, and score fields
- Historical assessments accumulate over time; partitioning by assessment date recommended
- KRI time-series data requires efficient storage for trend analysis (consider separate KRI_History table)
- Heatmap generation requires efficient aggregation queries across risk scores
- Estimated storage: 5-10 KB per risk entry, 2-5 KB per assessment record, 1 KB per KRI data point

---

## Module 2: Cybersecurity Risk & Compliance

### Purpose

Provide comprehensive cybersecurity risk management and regulatory compliance capabilities. Enable organizations to manage their cybersecurity asset inventory, track vulnerabilities and threats, respond to cyber incidents, assess compliance against NCA frameworks (ECC, CSCC), and maintain cybersecurity controls with maturity scoring.

### Core Capabilities

- Cybersecurity asset inventory management (hardware, software, network, cloud, data assets)
- Asset classification and criticality scoring
- Vulnerability tracking and management (discovery, assessment, remediation, verification)
- Threat register and threat intelligence tracking
- Cyber incident management (detection, classification, response, resolution, post-incident review)
- NCA ECC (Essential Cybersecurity Controls) compliance assessment
- NCA CSCC (Critical Systems Cybersecurity Controls) compliance assessment
- Cybersecurity control management and mapping to NCA domains/subdomains
- Control implementation status tracking with evidence
- Cybersecurity maturity assessment (capability maturity model)
- Cybersecurity risk assessment (asset-based, threat-based)
- Penetration test result tracking
- Security awareness and training tracking
- Cyber risk escalation to enterprise risk register

### Primary Actors

- CISO / Cybersecurity Manager (primary owner)
- Cybersecurity Analysts (operational)
- IT Administrators (asset data providers)
- Risk Manager (for escalated cyber risks)
- Compliance Officer (for NCA assessments)
- Internal Auditor (read-only review)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Cyber Asset** | An IT/OT asset within the organization | ID, name, type (HW/SW/Network/Cloud/Data), classification, criticality, owner, location, IP/hostname, OS/version, status, business unit, dependencies |
| **Vulnerability** | A known weakness in an asset | ID, title, CVE reference, affected asset(s), severity (CVSS), status, discovery date, remediation deadline, remediation owner, remediation notes |
| **Threat** | A potential threat to organizational assets | ID, title, type (external/internal/environmental), threat actor, target assets, likelihood, potential impact, mitigation status |
| **Cyber Incident** | A cybersecurity event requiring response | ID, title, description, severity, classification, affected assets, detection date, response status, containment actions, resolution date, root cause, lessons learned, escalated to ERM flag |
| **NCA Control** | A control from NCA ECC/CSCC framework | ID, domain, subdomain, control reference, control description (AR/EN), implementation status, maturity level, evidence, assessor, last assessment date |
| **Cyber Assessment** | A compliance or risk assessment | ID, type (NCA ECC/CSCC/custom), scope, assessor, assessment date, overall score, status, findings count |
| **Cyber Risk** | A cybersecurity-specific risk | ID, asset(s), threat, vulnerability, impact, likelihood, risk score, treatment, linked enterprise risk |
| **Penetration Test** | A security testing engagement | ID, scope, tester, test date, findings summary, critical/high/medium/low counts, remediation status |

### Important Workflows

1. **Vulnerability Lifecycle:** Vulnerability discovered (scan import or manual) -> Triaged and scored -> Assigned to remediation owner -> Remediation tracked -> Verification performed -> Closed or exception approved
2. **Incident Response:** Incident detected/reported -> Classified by severity -> Response team activated -> Containment actions executed -> Investigation conducted -> Remediation applied -> Post-incident review completed -> Lessons learned documented -> Escalated to ERM if enterprise-significant
3. **NCA Compliance Assessment:** Select NCA framework (ECC/CSCC) -> Review each control domain -> Assess implementation status -> Score maturity level -> Collect evidence -> Identify gaps -> Create remediation plans -> Generate compliance scorecard
4. **Asset Risk Assessment:** Select assets for assessment -> Identify threats and vulnerabilities per asset -> Score risk -> Apply treatments -> Calculate residual risk -> Update asset risk profile -> Escalate critical risks to ERM
5. **Cyber-to-Enterprise Risk Escalation:** Cyber risk identified as enterprise-significant -> Escalation workflow triggered -> Risk details packaged -> Enterprise risk entry created/linked -> Risk Manager notified -> Enterprise assessment performed

### Inputs

- Asset discovery data (manual entry, import)
- Vulnerability scan results (manual entry, import)
- Incident reports from SOC / monitoring tools
- NCA framework control libraries (pre-loaded)
- Penetration test reports
- Threat intelligence feeds (manual)

### Outputs

- Asset inventory reports
- Vulnerability aging and remediation reports
- Incident trend analysis
- NCA compliance scorecards (ECC/CSCC)
- Cybersecurity risk heatmaps
- Cyber risk escalations to ERM
- Maturity level reports
- Data feeds to Reporting & Analytics

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| ERM | Provides data | Escalates significant cyber risks to enterprise register |
| Compliance | Shares controls | NCA controls shared with compliance framework management |
| Workflow | Uses service | Incident response workflows, vulnerability remediation workflows |
| Reporting | Provides data | Cyber dashboards, NCA scorecards feed unified reporting |
| Administration | Uses service | Asset ownership from org hierarchy, user/role data |
| BCM | Provides data | Cyber incidents may trigger BCM invocation |

### Licensing Independence

Cybersecurity can be licensed independently. Standalone mode provides full asset, vulnerability, threat, incident, and NCA compliance management. Cross-module features (ERM escalation, BCM triggering) activate when those modules are licensed.

### Reporting Impact

Contributes: NCA compliance scorecards, vulnerability metrics, incident statistics, asset risk profiles, cybersecurity maturity trends, and cyber risk distribution data to unified reporting.

### Audit Requirements

- All asset changes audited (classification changes, ownership transfers)
- Incident timeline must be immutable (append-only)
- NCA assessment scores must be version-controlled
- Evidence attached to controls must be timestamped and tamper-evident
- Vulnerability status changes must be tracked with justification

### Database/Storage Implications

- Asset inventory can be large (thousands of assets); requires efficient search and filtering
- Vulnerability data is high-volume with frequent updates; bulk import capability needed
- Incident records include attachments (logs, screenshots); file storage integration required
- NCA control library is reference data; version-controlled and preloaded
- Estimated storage: 3-8 KB per asset, 2-5 KB per vulnerability, 10-50 KB per incident (excluding attachments)

---

## Module 3: Business Continuity Management (BCM)

### Purpose

Provide comprehensive business continuity management aligned with ISO 22301. Enable organizations to conduct business impact analysis, develop and maintain business continuity plans, manage recovery strategies, track dependencies, and plan and execute exercises to validate preparedness.

### Core Capabilities

- Business Impact Analysis (BIA) management and execution
- BIA questionnaire design and distribution
- RTO/RPO/MTPD definition and tracking
- Business Continuity Plan (BCP) creation, versioning, and lifecycle management
- Recovery strategy definition and documentation
- Critical process identification and dependency mapping (IT, people, suppliers, facilities)
- Exercise and test planning, execution, and evaluation
- Exercise type management (tabletop, walkthrough, functional, full-scale)
- BCM incident and invocation management
- Call tree and communication plan management
- Plan distribution and acknowledgment tracking
- BCM maturity assessment against ISO 22301
- Improvement action tracking from exercises and incidents

### Primary Actors

- BCM Manager (primary owner)
- Business Unit Managers (BIA respondents, plan owners)
- IT Director (IT recovery data provider)
- Risk Manager (for BCM risk escalation)
- Executive (plan approval)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Business Process** | A business process subject to BIA | ID, name (AR/EN), description, business unit, criticality tier, process owner, status |
| **BIA Record** | Business impact analysis for a process | ID, process ID, impact assessment (financial, operational, reputational, regulatory at time intervals), RTO, RPO, MTPD, dependencies, peak periods, minimum resources, BIA date, reviewer |
| **Business Continuity Plan** | Documented recovery plan | ID, name, scope (BU/process), version, status (draft/approved/expired), owner, approval date, next review date, distribution list |
| **Recovery Strategy** | Strategy for recovering a process | ID, BIA/process link, strategy type, description, required resources, estimated recovery time, cost, dependencies |
| **Dependency** | A dependency of a critical process | ID, process ID, dependency type (IT system/person/supplier/facility), name, criticality, alternative available, contact details, SLA |
| **Exercise** | A BCM exercise or test | ID, name, type (tabletop/walkthrough/functional/full-scale), scope, date, participants, objectives, status, results summary |
| **Exercise Observation** | Finding from an exercise | ID, exercise ID, observation, severity, recommendation, action owner, action due date, action status |
| **BCM Incident** | An actual business continuity event | ID, title, description, severity, affected processes, invocation status, start time, resolution time, impact summary, lessons learned |
| **Call Tree** | Emergency communication chain | ID, name, scope (BU/plan), contacts (ordered), last tested, test results |

### Important Workflows

1. **BIA Campaign:** BCM Manager initiates BIA -> Questionnaires distributed to BU Managers -> Responses collected -> BCM Manager reviews and validates -> Impact ratings confirmed -> RTO/RPO established -> Dependencies documented -> BIA approved
2. **BCP Development:** Review BIA results -> Identify recovery strategies -> Document recovery procedures -> Define team roles -> Include communication plans -> Review and approve -> Distribute to stakeholders -> Track acknowledgments
3. **Exercise Lifecycle:** Plan exercise -> Define objectives and scope -> Schedule participants -> Execute exercise -> Record observations -> Evaluate against objectives -> Create improvement actions -> Track to closure -> Update BCPs as needed
4. **BCM Incident Management:** Incident declared -> BCP activated -> Response team mobilized -> Recovery actions executed -> Status tracked -> Resolution achieved -> Post-incident review -> Lessons learned -> Improvement actions created -> Risk escalation to ERM if needed
5. **Dependency Impact Analysis:** System/supplier change identified -> Affected processes identified via dependency map -> Impact assessed -> BCP update triggered if needed -> Stakeholders notified

### Inputs

- BIA questionnaire responses from business units
- IT system inventory and dependency data
- Supplier and vendor information
- Facility and location data
- Organization hierarchy from Administration
- Incident reports

### Outputs

- BIA summary reports with RTO/RPO matrices
- BCP documents (versioned, exportable)
- Dependency maps and diagrams
- Exercise schedule and results reports
- BCM maturity scorecards
- BCM risk escalations to ERM
- Data feeds to Reporting & Analytics

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| ERM | Provides data | Escalates BCM-related risks to enterprise register |
| Cybersecurity | Receives data | IT asset data informs dependency mapping; cyber incidents may trigger BCM |
| Workflow | Uses service | BIA distribution, BCP approval, exercise coordination workflows |
| Reporting | Provides data | BCM dashboards, BIA summaries feed unified reporting |
| Administration | Uses service | Organization hierarchy, business unit structure, user data |

### Licensing Independence

BCM can be licensed independently. Standalone mode provides full BIA, BCP, exercise, and incident management. Cross-module features (cyber dependency integration, ERM escalation) activate when those modules are licensed.

### Reporting Impact

Contributes: BIA coverage dashboards, BCP status matrices, exercise results, dependency analysis, RTO/RPO compliance indicators, BCM maturity trends.

### Audit Requirements

- BIA records must be version-controlled with full change history
- BCP versions must be retained (no overwriting of previous versions)
- Exercise results and observations must be immutable once finalized
- Plan distribution and acknowledgment must be tracked and timestamped
- Incident timelines must be append-only

### Database/Storage Implications

- BIA data includes structured questionnaire responses; JSONB or normalized table approach
- BCPs may include document attachments (Word/PDF); file storage integration required
- Dependency maps require graph-like querying (adjacency list model recommended)
- Exercise records accumulate over time; moderate volume
- Estimated storage: 10-20 KB per BIA record, 5-50 KB per BCP (excluding attachments), 5 KB per exercise

---

## Module 4: Data Governance / Data Office

### Purpose

Provide data governance capabilities aligned with NDMO national data governance standards. Enable organizations to maintain a data inventory, classify data assets, manage data quality programs, assign and track data stewardship, and document data lineage for critical data assets.

### Core Capabilities

- Data asset inventory and catalog management
- Data classification scheme management (aligned with NDMO)
- Data classification assignment and tracking
- Data stewardship assignment and accountability
- Data quality rule definition and assessment
- Data quality scoring and dashboards
- Data lineage documentation (source-to-target mapping)
- Data dictionary and glossary management
- Data governance maturity assessment
- Data governance council support (meeting tracking, decision logging)
- Data sharing agreement management
- Data retention policy tracking
- NDMO compliance reporting

### Primary Actors

- Data Governance Officer / CDO (primary owner)
- Data Stewards (per business unit/domain)
- IT Director (technical data management)
- DPO (personal data classification context)
- Compliance Officer (NDMO compliance)
- Business Unit Managers (data owners)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Data Asset** | A logical or physical data asset | ID, name (AR/EN), description, type (database/file/API/report), classification, owner, steward, business unit, system, sensitivity level, retention period, status |
| **Data Classification** | Classification applied to a data asset | ID, asset ID, classification scheme, classification level, classified by, classification date, review date, justification |
| **Data Steward Assignment** | Assignment of stewardship responsibility | ID, steward (user), scope (domain/BU/system), responsibilities, assignment date, status |
| **Data Quality Rule** | Rule defining expected data quality | ID, name, description, rule type (completeness/accuracy/consistency/timeliness), target asset, threshold, measurement method, frequency |
| **Data Quality Assessment** | Result of a quality measurement | ID, rule ID, assessment date, measured value, pass/fail, issues found, remediation action |
| **Data Lineage** | Source-to-target data flow | ID, source system, source field, transformation description, target system, target field, refresh frequency, last validated |
| **Data Dictionary Entry** | Definition of a data element | ID, element name, definition (AR/EN), data type, valid values, owning system, business glossary term |
| **Data Sharing Agreement** | Agreement for data exchange | ID, parties, data scope, purpose, legal basis, expiry date, terms, status |
| **Data Retention Policy** | Retention rules for data categories | ID, data category, retention period, disposal method, legal basis, review date |

### Important Workflows

1. **Data Inventory Population:** Identify data systems -> Catalog data assets -> Assign owners -> Classify sensitivity -> Assign stewards -> Document metadata -> Review and approve
2. **Classification Campaign:** Define/update classification scheme -> Train stewards -> Distribute classification tasks -> Stewards classify assets -> Review and approve -> Generate coverage report
3. **Data Quality Assessment:** Define quality rules -> Schedule assessments -> Execute measurements -> Score results -> Identify failures -> Create remediation actions -> Track to resolution -> Trend analysis
4. **Lineage Documentation:** Identify critical data flows -> Map source systems -> Document transformations -> Map target systems -> Validate with stewards -> Publish lineage -> Update on change
5. **NDMO Compliance Assessment:** Map NDMO requirements -> Assess organizational compliance -> Score maturity -> Identify gaps -> Create improvement plan -> Track execution

### Inputs

- System inventory from IT
- Data catalog imports
- Stewardship nominations from business units
- NDMO framework requirements (preloaded reference)
- Quality measurement results
- Organization hierarchy from Administration

### Outputs

- Data inventory and catalog reports
- Classification coverage dashboards
- Data quality scorecards
- Lineage documentation
- Stewardship assignment reports
- NDMO compliance reports
- Data governance maturity assessments
- Data feeds to Reporting & Analytics
- Data asset context for Privacy module

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| Privacy | Provides data | Data inventory and classification inform privacy processing records |
| Compliance | Shares data | NDMO compliance assessments managed through Compliance framework |
| Workflow | Uses service | Classification approval, quality remediation workflows |
| Reporting | Provides data | Data governance dashboards feed unified reporting |
| Administration | Uses service | Organization hierarchy, user data for stewardship |

### Licensing Independence

Data Governance can be licensed independently. Standalone mode provides full data inventory, classification, quality, and stewardship capabilities. Integration with Privacy module (data inventory sharing) activates when Privacy is also licensed.

### Reporting Impact

Contributes: data inventory statistics, classification coverage metrics, data quality trends, stewardship coverage, NDMO maturity scores, and data governance program KPIs.

### Audit Requirements

- Classification changes must be audited with before/after values and justification
- Data quality assessment results must be immutable once recorded
- Stewardship assignment changes must be tracked
- Data sharing agreement lifecycle must be fully audited
- Retention policy changes require approval workflow

### Database/Storage Implications

- Data inventory can be large (thousands of assets); requires full-text search capability
- Lineage data has graph-like relationships; adjacency model with efficient traversal queries
- Quality assessment history is time-series data; partitioning by date recommended
- Data dictionary may have extensive entries; full-text indexing for search
- Estimated storage: 3-5 KB per data asset, 1-2 KB per quality assessment, 2-5 KB per lineage entry

---

## Module 5: Privacy & Personal Data Protection

### Purpose

Provide comprehensive personal data protection management aligned with Saudi PDPL (Personal Data Protection Law). Enable organizations to maintain Records of Processing Activities, conduct Privacy Impact Assessments, manage consent, handle data subject requests, manage breach notification processes, and track cross-border data transfers.

### Core Capabilities

- Records of Processing Activities (RoPA) management
- Processing activity registration and lifecycle management
- Privacy Impact Assessment (PIA) and Data Protection Impact Assessment (DPIA)
- Configurable PIA/DPIA questionnaire templates
- Consent management (collection, tracking, withdrawal)
- Data Subject Access Request (DSAR) management and tracking
- Privacy breach detection, assessment, and notification management
- Cross-border data transfer register and compliance tracking
- Third-party processor management
- Privacy risk assessment and escalation
- PDPL compliance self-assessment
- Data subject rights fulfillment tracking (access, rectification, erasure, portability)
- Privacy training and awareness tracking
- Regulatory submission preparation (SDAIA)

### Primary Actors

- Data Protection Officer (primary owner)
- Privacy Analysts (operational)
- Business Unit Managers (processing activity owners)
- Data Governance Officer (data inventory context)
- Compliance Officer (PDPL compliance assessments)
- Legal Counsel (cross-border transfers, breach notification)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Processing Activity** | A personal data processing activity (RoPA entry) | ID, name (AR/EN), purpose, legal basis, data categories, data subjects, recipients, retention period, cross-border flag, system, business unit, owner, status, last review date |
| **PIA/DPIA** | Privacy impact assessment | ID, processing activity ID, assessment type (PIA/DPIA), status, assessor, assessment date, risk level, findings count, mitigations, approval status |
| **Consent Record** | Record of individual consent | ID, data subject identifier (pseudonymized), processing activity, consent type, collection date, collection method, scope, withdrawal date, status |
| **Data Subject Request** | A DSAR from an individual | ID, request type (access/rectification/erasure/portability/restriction/objection), requester identifier, submission date, verification status, processing status, due date, response date, outcome |
| **Privacy Breach** | A personal data breach incident | ID, title, description, discovery date, severity, data categories affected, data subjects affected (count), root cause, containment actions, notification required (SDAIA/individuals), notification date, status |
| **Cross-Border Transfer** | A transfer of personal data outside KSA | ID, processing activity, destination country, legal basis, safeguards, transfer volume, frequency, approval status |
| **Third-Party Processor** | An external entity processing personal data | ID, name, country, services, contract date, contract expiry, DPA status, last assessment date, risk rating |
| **Privacy Risk** | A privacy-specific risk | ID, processing activity, risk description, impact, likelihood, score, mitigation, linked enterprise risk |

### Important Workflows

1. **RoPA Management:** Identify processing activities -> Document in RoPA -> Assign owners -> Classify legal basis -> Map data flows -> Schedule periodic review -> Update on change
2. **PIA/DPIA Lifecycle:** Identify processing requiring assessment -> Select template -> Complete assessment questionnaire -> Identify privacy risks -> Define mitigations -> Review and approve -> Monitor implementation -> Periodic reassessment
3. **DSAR Fulfillment:** Receive request -> Verify identity -> Classify request type -> Locate relevant personal data (cross-reference data inventory) -> Process request -> Generate response -> Send to data subject -> Document outcome within SLA
4. **Breach Management:** Breach detected/reported -> Initial assessment (severity, scope) -> Containment actions -> Determine notification obligations (SDAIA timeline, individual notification) -> Execute notifications -> Document full lifecycle -> Post-breach review -> Improvement actions -> Escalate to ERM if enterprise-significant
5. **Consent Lifecycle:** Define consent requirements per processing activity -> Collect consent -> Record in system -> Monitor validity -> Process withdrawals -> Update processing activity status -> Audit trail

### Inputs

- Processing activity declarations from business units
- Data inventory from Data Governance module
- Incident reports (potential privacy breaches)
- Data subject requests (via intake channels)
- PDPL requirements (preloaded reference)
- Third-party processor contracts and assessments

### Outputs

- RoPA reports (full register, per BU, per purpose)
- PIA/DPIA reports with risk findings
- DSAR metrics and compliance reports
- Breach notification timelines and documentation
- Cross-border transfer register
- PDPL compliance scorecards
- Privacy risk escalations to ERM
- Regulatory submission packages for SDAIA
- Data feeds to Reporting & Analytics

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| Data Governance | Receives data | Data inventory and classification inform processing activities |
| ERM | Provides data | Privacy risks escalated to enterprise register |
| Cybersecurity | Receives/Provides data | Cyber incidents may constitute privacy breaches; privacy breaches may originate from cyber incidents |
| Compliance | Shares data | PDPL compliance assessments managed through Compliance framework |
| Workflow | Uses service | DSAR fulfillment, breach notification, PIA approval workflows |
| Reporting | Provides data | Privacy dashboards feed unified reporting |
| Administration | Uses service | Organization hierarchy, user data |

### Licensing Independence

Privacy can be licensed independently. Standalone mode provides full RoPA, PIA/DPIA, consent, DSAR, and breach management. Data inventory integration with Data Governance and risk escalation to ERM activate when those modules are licensed.

### Reporting Impact

Contributes: PDPL compliance dashboards, RoPA completeness metrics, PIA/DPIA status, DSAR volume and SLA compliance, breach statistics, consent analytics, cross-border transfer summaries.

### Audit Requirements

- RoPA changes must be fully audited with version control
- Consent records must be immutable (withdrawal creates new record, does not delete original)
- DSAR processing must be timestamped at every status change for SLA compliance
- Breach notifications must have tamper-proof timeline (regulatory evidence)
- PIA/DPIA assessments must be version-controlled

### Database/Storage Implications

- Consent records can be very high-volume (potentially millions); efficient indexing on data subject identifier and processing activity required
- DSAR tracking requires SLA-aware queries (due date calculations, overdue alerts)
- Breach records include attachments and timeline entries; moderate storage per record
- RoPA is moderate volume but frequently queried; needs efficient filtering
- Estimated storage: 5-10 KB per processing activity, 0.5-1 KB per consent record, 10-30 KB per breach record, 5 KB per DSAR

---

## Module 6: Compliance & Audit Management

### Purpose

Provide comprehensive compliance management and internal audit lifecycle support. Enable organizations to manage multiple regulatory and standards frameworks, conduct compliance assessments, track findings and gaps, manage remediation activities, collect and maintain evidence, and coordinate audit engagements.

### Core Capabilities

- Compliance framework library management (NCA ECC, NCA CSCC, ISO 27001, ISO 22301, ISO 31000, PDPL, custom frameworks)
- Control library management with cross-framework mapping
- Compliance assessment creation and execution
- Gap analysis and scoring
- Finding management (create, classify, assign, track, close)
- Corrective Action Plan (CAP) management
- Evidence repository management (upload, link, version, expiry tracking)
- Evidence freshness monitoring and renewal alerts
- Audit engagement planning and lifecycle management
- Audit universe and risk-based audit planning
- Audit workpaper management
- Regulatory obligation tracking with deadline management
- Compliance calendar and milestone tracking
- Cross-module compliance data aggregation

### Primary Actors

- Compliance Officer (primary owner for compliance)
- Internal Auditor (primary owner for audit engagements)
- CISO (NCA compliance assessments)
- DPO (PDPL compliance assessments)
- BCM Manager (ISO 22301 assessments)
- Business Unit Managers (auditees, finding owners)
- External Auditors (time-bound read access)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Compliance Framework** | A regulatory or standards framework | ID, name (AR/EN), version, source (NCA/ISO/PDPL/custom), description, domain structure, active flag, effective date |
| **Control** | A control within a framework | ID, framework ID, domain, subdomain, reference number, title (AR/EN), description (AR/EN), implementation guidance, cross-mapped controls |
| **Compliance Assessment** | An assessment against a framework | ID, framework ID, scope (org/BU), assessor, assessment date, status, overall score, methodology |
| **Control Assessment** | Assessment of individual control implementation | ID, assessment ID, control ID, implementation status, maturity level, evidence references, gaps identified, assessor notes |
| **Finding** | A compliance gap or audit finding | ID, source (assessment/audit/incident), title, description, severity (critical/high/medium/low), affected control, affected BU, owner, status, due date, root cause |
| **Corrective Action Plan** | Remediation plan for a finding | ID, finding ID, description, actions, owner, start date, target date, actual date, status, evidence of completion |
| **Evidence** | Evidence supporting control implementation | ID, name, type (document/screenshot/log/certificate), linked controls, upload date, uploaded by, expiry date, status (current/expired/pending review), file reference |
| **Audit Engagement** | An internal audit engagement | ID, name, type (full/focused/follow-up), scope, lead auditor, team, planned start, planned end, actual start, actual end, status, findings count |
| **Regulatory Obligation** | A regulatory compliance requirement | ID, regulation, obligation description, responsible party, frequency, next due date, status, evidence |

### Important Workflows

1. **Compliance Assessment:** Select framework -> Define scope -> Review controls -> Assess each control (implementation status, maturity, evidence) -> Score overall compliance -> Identify gaps -> Generate assessment report -> Create findings for gaps
2. **Finding Lifecycle:** Finding created (from assessment, audit, or incident) -> Classified by severity -> Assigned to owner -> Corrective Action Plan developed -> Actions tracked -> Completion evidence collected -> Finding verified -> Closed or escalated
3. **Evidence Management:** Evidence requirement identified per control -> Evidence collected -> Reviewed and approved -> Linked to control -> Freshness monitoring -> Renewal alerts -> Re-collection -> Packaged for audit
4. **Audit Engagement:** Plan engagement -> Define scope and objectives -> Assign audit team -> Execute fieldwork -> Document observations -> Draft findings -> Discuss with auditees -> Finalize findings -> Issue report -> Track remediation -> Follow-up verification
5. **Cross-Framework Control Mapping:** Define control in library -> Map to multiple frameworks (e.g., one control maps to NCA ECC domain 2, ISO 27001 A.8, and PDPL Article 20) -> Assess once -> Compliance status reflected across all mapped frameworks

### Inputs

- Framework definitions (preloaded reference data)
- Control libraries (preloaded, customizable)
- Assessment inputs from domain owners (CISO, DPO, BCM Manager)
- Evidence uploads from various stakeholders
- Audit plans and schedules
- Findings from other modules
- Organization hierarchy from Administration

### Outputs

- Framework compliance scorecards
- Gap analysis reports
- Finding management reports (aging, severity, status)
- Corrective Action Plan tracking
- Evidence coverage reports
- Audit engagement reports
- Regulatory obligation status
- Cross-framework compliance dashboards
- Data feeds to Reporting & Analytics

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| Cybersecurity | Shares controls | NCA controls shared; cyber assessments feed compliance |
| Privacy | Shares data | PDPL assessments managed here; privacy data informs compliance |
| BCM | Shares data | ISO 22301 assessments managed here |
| ERM | Receives data | Risk-based prioritization of compliance activities |
| Workflow | Uses service | Finding assignment, CAP tracking, evidence review workflows |
| Reporting | Provides data | Compliance dashboards feed unified reporting |
| Administration | Uses service | Organization hierarchy, user data |

### Licensing Independence

Compliance & Audit can be licensed independently. Provides standalone framework assessment, finding management, evidence management, and audit engagement capabilities. Cross-module control sharing activates when Cybersecurity, Privacy, or BCM modules are licensed.

### Reporting Impact

Contributes: compliance scorecards by framework, gap analysis heat maps, finding trends, remediation SLA compliance, evidence coverage, audit engagement status, and regulatory obligation tracking.

### Audit Requirements

- Assessment records must be immutable once finalized
- Finding status changes must be fully audited
- Evidence must be version-controlled with checksums for integrity
- Audit engagement workpapers must be access-controlled and audit-trailed
- CAP completion evidence must be timestamped and verified

### Database/Storage Implications

- Control library is reference data with cross-references; efficient many-to-many mapping needed
- Evidence files can be large (documents, certificates, screenshots); file storage with metadata
- Finding and CAP data is moderate volume but heavily queried; optimized indexing needed
- Assessment history accumulates; partitioning by date recommended
- Estimated storage: 2-5 KB per control record, 1-100 MB per evidence file, 5-10 KB per finding, 10-30 KB per assessment

---

## Module 7: Workflow & Case Management

### Purpose

Provide a cross-module workflow engine that supports configurable approval chains, task routing, SLA management, escalation rules, and case lifecycle tracking. This module is the operational backbone that enables process automation across all GRC modules.

### Core Capabilities

- Workflow definition and configuration (visual or template-based)
- Multi-step approval chain management
- Sequential and parallel approval support
- Conditional routing based on data attributes (severity, amount, BU)
- Task assignment and reassignment
- Task delegation and proxy support
- SLA definition per workflow step
- SLA monitoring with automated alerts
- Escalation rules (time-based, threshold-based)
- Case creation and lifecycle management
- Case categorization and prioritization
- Case notes, attachments, and activity log
- Email and in-app notification integration
- Bulk task operations (approve, reject, reassign)
- Workflow template library
- Workflow audit trail (who approved what, when, with what comments)

### Primary Actors

- Organization Administrator (workflow configuration)
- Workflow Approvers (approval tasks)
- All module users (task recipients)
- System Administrators (workflow template management)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Workflow Definition** | Template for a workflow process | ID, name (AR/EN), description, module, trigger type, steps, active flag, version |
| **Workflow Step** | A step within a workflow | ID, workflow ID, step order, step type (approval/review/task/notification), assignee rule, SLA hours, escalation rule, conditions |
| **Workflow Instance** | A running instance of a workflow | ID, workflow definition ID, source module, source entity ID, current step, status (active/completed/cancelled/escalated), created date, completed date |
| **Workflow Task** | An individual task within an instance | ID, instance ID, step ID, assignee, status (pending/approved/rejected/delegated/escalated), assigned date, due date, completed date, comments, decision |
| **Case** | A case for tracking a complex issue | ID, title, description, category, priority, assigned to, status, source module, related entities, SLA, resolution date |
| **Case Activity** | Activity log entry for a case | ID, case ID, activity type, description, actor, timestamp, attachments |
| **Escalation Rule** | Rule defining when/how to escalate | ID, trigger (time/threshold), condition, action (reassign/notify/escalate), target |
| **Notification Template** | Template for workflow notifications | ID, name, channel (email/in-app), subject (AR/EN), body (AR/EN), variables |

### Important Workflows

1. **Generic Approval Flow:** Trigger event -> Workflow instance created -> Task assigned to Step 1 approver -> Approver reviews -> Approves/rejects -> If approved, advances to next step -> Repeats until final step -> Completed status -> Source entity updated
2. **SLA Escalation:** Task assigned -> SLA timer starts -> Warning notification at threshold -> If not completed by deadline -> Escalation rule fires -> Task reassigned or manager notified -> Continue until resolved
3. **Case Management:** Issue identified -> Case created -> Categorized and prioritized -> Assigned to handler -> Investigation and actions documented -> Resolution applied -> Case closed -> Satisfaction survey (optional)
4. **Delegation Flow:** Approver unavailable -> Delegates to alternate -> Alternate receives task -> Processes task -> Decision recorded under delegate with delegation reference -> Original approver notified

### Inputs

- Workflow triggers from all GRC modules
- Task completion and approval decisions from users
- SLA and escalation configuration from Organization Administrator
- Notification templates
- User availability and delegation settings

### Outputs

- Task lists and queues per user
- Workflow status tracking
- SLA compliance reports
- Escalation logs
- Case status and resolution reports
- Workflow audit trails
- Notification delivery logs
- Data feeds to Reporting & Analytics

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| All GRC Modules | Provides service | Workflow engine used by all modules for approvals and task routing |
| Administration | Uses service | User data, role assignments, organization hierarchy for routing |
| Reporting | Provides data | Workflow metrics feed unified reporting |

### Licensing Independence

Workflow is a platform service module. It is included as a base capability with any licensed module. It cannot be licensed independently as it has no standalone business value. Its cost is distributed across licensed modules.

### Reporting Impact

Contributes: workflow throughput metrics, SLA compliance rates, approval turnaround times, task aging, escalation frequency, case resolution statistics.

### Audit Requirements

- Every workflow decision must be recorded (who, what, when, decision, comments)
- Delegation events must be audited
- SLA timer events must be logged
- Escalation events must be tracked
- Workflow definition changes must be version-controlled

### Database/Storage Implications

- Workflow instances are high-volume (every approval across all modules creates instances)
- Task tables require optimized queries for user-specific task lists (index on assignee + status)
- Historical workflow data grows rapidly; archival strategy needed
- Case activity logs are append-only; moderate storage
- Estimated storage: 2-5 KB per workflow instance, 1-2 KB per task, 1-3 KB per case activity entry

---

## Module 8: Reporting & Analytics

### Purpose

Provide a unified reporting and analytics layer that aggregates data from all GRC modules into dashboards, visualizations, and exportable reports. Enable executive summaries, operational drill-downs, scheduled report delivery, and a consolidated risk view across the entire platform.

### Core Capabilities

- Pre-built dashboard templates per module
- Executive summary dashboard (unified GRC view)
- Interactive risk heatmaps with drill-down
- KRI/KPI visualization with threshold indicators
- Customizable chart types (bar, line, pie, radar, stacked, treemap)
- Report builder with drag-and-drop composition
- Scheduled report generation and distribution
- Report export (PDF, Excel, Word, CSV)
- Bilingual report generation (Arabic/English, RTL/LTR)
- Report template management
- Data filtering and slicing (by BU, time period, category, status)
- Trend analysis and comparison (period-over-period)
- Unified risk view (risks aggregated from ERM, Cyber, BCM, Privacy)
- Compliance posture dashboard (all frameworks)
- Ad-hoc query support for power users
- Report scheduling and delivery (email)
- Report access control (who can see which reports)

### Primary Actors

- All roles (consumers of reports and dashboards)
- Risk Manager (risk-focused dashboards)
- CISO (cyber dashboards, NCA scorecards)
- Compliance Officer (compliance dashboards)
- Executive / Board Members (executive summaries)
- Organization Administrator (report template configuration)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Dashboard** | A dashboard configuration | ID, name (AR/EN), type (executive/module/custom), module scope, widgets, layout, access roles, active flag |
| **Dashboard Widget** | A widget within a dashboard | ID, dashboard ID, widget type (chart/table/metric/heatmap), data source, configuration (filters, grouping, visualization), position |
| **Report Template** | A reusable report template | ID, name (AR/EN), description, module scope, sections, data queries, export format, created by |
| **Scheduled Report** | A report scheduled for generation | ID, report template ID, schedule (cron expression), recipients, format, last generated, next generation, status |
| **Generated Report** | An instance of a generated report | ID, template ID, generation date, parameters, file reference, generated by, distribution status |
| **Saved Filter** | User-saved filter configuration | ID, name, module, filter criteria, user ID |

### Important Workflows

1. **Dashboard Access:** User logs in -> Role-based dashboard presented -> Widgets display real-time data -> User applies filters -> Drills down into specific data -> Exports if needed
2. **Report Generation:** Select report template -> Configure parameters (date range, BU, framework) -> Generate report -> Review output -> Export or schedule for distribution
3. **Scheduled Reporting:** Configure schedule -> Select template and parameters -> Define recipients -> System generates on schedule -> Report distributed via email -> Delivery confirmed
4. **Unified Risk View:** Dashboard aggregates risks from ERM, Cyber, BCM, and Privacy -> Presents unified heatmap -> Color-coded by source module -> Drill-down to source module for details

### Inputs

- Data from all GRC modules (read-only aggregation)
- Dashboard and report configuration
- User preferences and saved filters
- Schedule definitions
- Organization hierarchy for scoping

### Outputs

- Interactive dashboards (web-based)
- Generated reports (PDF, Excel, Word, CSV)
- Scheduled report deliveries (email attachments)
- Executive summaries
- Board-ready report packages

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| All GRC Modules | Receives data | Reads data from all modules for aggregation and visualization |
| Administration | Uses service | Organization hierarchy for scoping, user roles for access control |
| Workflow | Receives data | Workflow metrics for operational dashboards |

### Licensing Independence

Reporting is a platform service module. A base reporting capability (module-specific dashboards) is included with each licensed module. Advanced reporting features (cross-module unified view, custom report builder, scheduled reports) may be licensed as a premium add-on.

### Reporting Impact

This IS the reporting module. It aggregates all other modules' data into a unified view.

### Audit Requirements

- Report generation must be logged (who generated what report, when, with what parameters)
- Dashboard access must be tracked for sensitive data views
- Scheduled report delivery must be confirmed and logged
- Report template changes must be audited

### Database/Storage Implications

- Reporting queries span all module tables; read-only access pattern
- Consider materialized views or reporting-specific aggregate tables for performance
- Generated reports (PDF/Excel) require file storage; retention policy needed
- Dashboard widget configurations are lightweight; stored as JSON
- Scheduled reports require a job scheduler integration
- Estimated storage: 1-5 MB per generated report file, 5-10 KB per dashboard configuration

---

## Module 9: Licensing & Subscription

### Purpose

Manage module-level licensing, subscription lifecycle, feature gating, and tenant entitlement for the TSS GRC Platform. Enable flexible commercial models including per-module licensing, user-based licensing, and subscription tiers.

### Core Capabilities

- Module license management (activate, deactivate, renew)
- Subscription lifecycle management (trial, active, suspended, expired)
- Feature flag management (enable/disable specific features within modules)
- User seat management (count tracking, enforcement)
- License key generation and validation
- Subscription tier management (basic, standard, premium)
- Usage metering and tracking
- License expiry notification and renewal reminders
- Entitlement enforcement at runtime (API-level checks)
- License compliance reporting
- Trial period management
- Grace period configuration for expired subscriptions
- License audit trail

### Primary Actors

- Platform Administrator (license management)
- Organization Administrator (view license status)
- System (automated enforcement)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **License** | A module license for an organization | ID, organization ID, module, license type (perpetual/subscription), tier (basic/standard/premium), start date, end date, status, max users, license key |
| **Subscription** | Subscription record for an organization | ID, organization ID, plan, billing cycle, start date, renewal date, status, payment status |
| **Feature Flag** | A toggleable feature within a module | ID, module, feature name, description, enabled (global), enabled (per-org override), tier requirement |
| **User Seat** | User seat allocation tracking | ID, organization ID, allocated seats, consumed seats, seat type (named/concurrent) |
| **License Event** | Audit log for license changes | ID, license ID, event type (activated/renewed/suspended/expired), event date, actor, details |
| **Usage Metric** | Usage data for metering | ID, organization ID, module, metric type, period, value |

### Important Workflows

1. **Module Activation:** Platform Admin creates license -> Assigns to organization -> Module features enabled -> Users gain access -> Usage tracking begins
2. **Subscription Renewal:** Renewal date approaches -> Notification sent -> Payment processed -> License extended -> Confirmation sent -> If not renewed -> Grace period -> Suspension -> Data preserved but access disabled
3. **Feature Gating:** Request to access feature -> System checks feature flag -> Checks organization tier -> Checks license status -> Grants or denies access -> Logs attempt
4. **Seat Management:** New user provisioned -> Seat count checked -> If under limit, access granted -> If at limit, admin notified -> Admin must purchase additional seats or deactivate a user

### Inputs

- License configuration from Platform Administrator
- Subscription and payment status
- Feature flag definitions
- User provisioning events
- Organization setup data

### Outputs

- License status dashboards
- Subscription expiry reports
- Usage metrics reports
- Seat utilization reports
- License compliance reports
- Entitlement data for runtime enforcement

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| All Modules | Provides service | Runtime entitlement checks gate access to module features |
| Administration | Uses/Provides service | Organization data; enforces user seat limits |

### Licensing Independence

This module is a platform infrastructure component. It is always present and active. It cannot be separately licensed.

### Reporting Impact

Contributes: license utilization dashboards, subscription status, seat consumption, feature adoption metrics.

### Audit Requirements

- All license changes must be fully audited
- Entitlement enforcement decisions must be logged
- Subscription lifecycle events must be tracked
- Feature flag changes must be audited with before/after state

### Database/Storage Implications

- License and subscription data is low-volume but mission-critical; requires high availability
- Feature flag lookups are extremely high-frequency (every API request); must be cached in-memory
- Usage metrics are time-series data; efficient aggregation needed
- Estimated storage: minimal (< 1 KB per license record, < 0.5 KB per usage metric)

---

## Module 10: Administration & Configuration

### Purpose

Provide comprehensive platform and organization administration capabilities. Manage system settings, user accounts, role assignments, organizational hierarchies, branding, lookup data, audit logging, and all foundational configuration that supports the other nine modules.

### Core Capabilities

- System settings management (email, file storage, security policies, session management)
- User account lifecycle management (create, activate, deactivate, password reset)
- Role and permission management (RBAC configuration)
- Organization hierarchy management (departments, BUs, locations, reporting lines)
- Multi-organization (tenant) management
- Lookup data management (categories, statuses, severities, scales -- all configurable)
- Organization branding (logo, color scheme, header text, custom labels)
- Notification template management
- Email server configuration
- File storage configuration
- Session and security policy management (password policies, lockout rules, session timeout)
- Audit log management and viewing
- System health monitoring (basic)
- Data import/export utilities
- Language and localization settings
- Custom field definition (extend entities with org-specific fields)
- Backup scheduling configuration
- API key management

### Primary Actors

- Platform Administrator (system-level configuration)
- Organization Administrator (org-level configuration)
- System Administrators (technical operations)

### Key Entities

| Entity | Description | Key Attributes |
|--------|-------------|----------------|
| **Organization** | A tenant/customer organization | ID, name (AR/EN), type, status, subscription, logo, branding config, created date, primary contact |
| **User** | A platform user account | ID, username, email, first name (AR/EN), last name (AR/EN), organization ID, status, roles, language preference, last login, MFA enabled |
| **Role** | A security role with permissions | ID, name (AR/EN), description, permissions (module + action), system flag (built-in vs custom), organization ID |
| **Permission** | An atomic permission | ID, module, action (create/read/update/delete/approve/export), scope (all/BU/own), resource type |
| **Department / Business Unit** | Organizational unit | ID, name (AR/EN), parent ID, organization ID, head (user ID), status, sort order |
| **Location** | Physical location | ID, name (AR/EN), address, city, country, organization ID, status |
| **Lookup Table** | Configurable reference data | ID, category, key, value (AR/EN), sort order, active flag, organization ID |
| **Audit Log** | Immutable record of system activity | ID, timestamp, user ID, action, module, entity type, entity ID, old value, new value, IP address, session ID |
| **Notification Template** | Configurable notification | ID, name, event type, channel (email/in-app), subject (AR/EN), body (AR/EN), variables, active flag |
| **System Setting** | Platform configuration parameter | ID, category, key, value, data type, description, scope (system/organization), editable flag |
| **File Attachment** | Uploaded file metadata | ID, original name, storage path, content type, size, uploaded by, upload date, linked entity type, linked entity ID, checksum |
| **Custom Field Definition** | User-defined field extension | ID, entity type, field name (AR/EN), field type (text/number/date/dropdown/boolean), required, validation rules, organization ID |

### Important Workflows

1. **User Onboarding:** Admin creates user -> Assigns roles -> Assigns to BU -> Configures module access -> Welcome email sent -> User logs in -> Sets password -> Configures language preference
2. **Organization Setup:** Platform Admin creates org -> Configures branding -> Sets up hierarchy -> Configures lookup data -> Creates Org Admin -> Assigns module licenses -> Org Admin takes over
3. **Role Configuration:** Admin defines role -> Assigns permissions per module (CRUD + scope) -> Assigns role to users -> Permissions enforced at runtime
4. **Lookup Data Management:** Admin accesses lookup configuration -> Adds/modifies lookup values -> Changes take effect immediately across modules using that lookup -> Audit trail recorded
5. **Audit Log Review:** Admin/Auditor accesses audit logs -> Filters by user, module, action, date -> Reviews entries -> Exports for investigation or compliance evidence

### Inputs

- Administrator configuration inputs
- User provisioning requests
- Organization setup data
- Branding assets (logos, colors)
- LDAP/AD directory data (for user sync)

### Outputs

- User access reports
- Role assignment matrices
- Organization hierarchy views
- Audit log exports
- System configuration reports
- Lookup data exports

### Dependencies on Other Modules

| Module | Dependency Type | Description |
|--------|----------------|-------------|
| All Modules | Provides service | Organization hierarchy, user/role data, lookup tables, audit logging, file management, notifications used by all modules |
| Licensing | Uses service | License enforcement for module access |

### Licensing Independence

Administration is a platform infrastructure module. It is always present and active. It cannot be separately licensed. Its capabilities are available as a shared foundation for all licensed modules.

### Reporting Impact

Contributes: user activity reports, role assignment summaries, audit log analysis, system health metrics, organization hierarchy views.

### Audit Requirements

- The audit log itself must be immutable (append-only, no modification or deletion)
- User account changes must be audited (creation, role changes, deactivation)
- Permission changes must be audited
- System setting changes must be audited with before/after values
- Login/logout events must be tracked

### Database/Storage Implications

- Audit log is the highest-volume table in the platform; requires partitioning by date, efficient indexing, and archival strategy
- User and role tables are frequently queried; cached in application layer
- Lookup tables are loaded into cache at startup and refreshed on change
- File attachments require external file storage (filesystem or blob storage); metadata in database
- Organization hierarchy uses adjacency list or nested set model
- Estimated storage: 0.5-1 KB per audit log entry (millions of entries per year), 2-5 KB per user, 50+ MB per year for attachments (varies significantly)

---

## Cross-Module Interaction Summary

The following critical cross-module interactions define how the platform operates as an integrated whole rather than isolated silos:

### Risk Escalation Chain

```
Cybersecurity Module ──[cyber risk escalation]──> Enterprise Risk Management
BCM Module ──[BCM risk escalation]──> Enterprise Risk Management
Privacy Module ──[privacy risk escalation]──> Enterprise Risk Management
Compliance Module ──[compliance risk context]──> Enterprise Risk Management
```

All domain-specific modules can escalate significant risks to the enterprise risk register, ensuring a unified view of organizational risk.

### Incident Propagation

```
Cybersecurity Incident ──[breach detected]──> Privacy Breach
Cybersecurity Incident ──[service disruption]──> BCM Incident
Privacy Breach ──[escalation]──> Enterprise Risk
BCM Incident ──[escalation]──> Enterprise Risk
```

Incidents in one domain can trigger processes in another, reflecting real-world event propagation.

### Shared Control Framework

```
Compliance Module ──[control library]──> Cybersecurity (NCA controls)
Compliance Module ──[control library]──> Privacy (PDPL controls)
Compliance Module ──[control library]──> BCM (ISO 22301 controls)
Compliance Module ──[control library]──> ERM (ISO 31000 controls)
```

A single control can be mapped to multiple frameworks, assessed once, and compliance status reflected everywhere.

### Shared Organizational Context

```
Administration ──[org hierarchy]──> All Modules
Administration ──[users/roles]──> All Modules
Administration ──[lookup data]──> All Modules
Administration ──[audit logging]──> All Modules
Administration ──[notifications]──> All Modules
Administration ──[file management]──> All Modules
```

All modules share the organizational foundation provided by Administration.

### Data Inventory Flow

```
Data Governance ──[data inventory]──> Privacy (RoPA context)
Data Governance ──[classification]──> Cybersecurity (asset classification)
Data Governance ──[data assets]──> ERM (data-related risks)
```

Data Governance provides the data context that informs privacy, security, and risk decisions.
