# User Roles and Personas

## Purpose

This document defines the user roles and personas for the TSS GRC Platform. Each role represents a distinct set of responsibilities, access requirements, and interaction patterns with the platform. These definitions drive RBAC configuration, UI/UX design decisions, module access policies, and training program development.

---

## Role Hierarchy Overview

```
Platform Administrator
  |
  +-- Organization Administrator
        |
        +-- Domain Roles (module-specific)
        |     +-- Risk Manager
        |     +-- CISO / Cybersecurity Manager
        |     +-- BCM Manager
        |     +-- Data Protection Officer
        |     +-- Compliance Officer
        |     +-- Data Governance Officer
        |
        +-- Cross-Functional Roles
        |     +-- Internal Auditor
        |     +-- Executive / Board Member
        |
        +-- Operational Roles
              +-- Business Unit Manager
              +-- Workflow Approver
              +-- Standard User
```

---

## Role Definitions

### 1. Platform Administrator

| Attribute | Detail |
|-----------|--------|
| **Description** | Super-administrator responsible for the entire TSS GRC platform instance. Manages system-level configuration, tenant/organization provisioning, platform-wide settings, and system health monitoring. This role is typically held by TSS technical operations staff or the customer's senior IT administrator. |
| **Primary Modules** | Administration & Configuration, Licensing & Subscription |
| **Secondary Modules** | All modules (system-level access for troubleshooting and configuration) |

**Key Tasks:**
- Provision and configure new organizations (tenants)
- Manage platform-wide system settings (email server, file storage, security policies)
- Configure and manage the licensing and subscription model per organization
- Manage platform-level lookup data (shared taxonomies, framework libraries)
- Monitor system health, performance, and audit logs
- Manage platform upgrades and database maintenance coordination
- Configure SSO/LDAP integration settings
- Manage platform branding and white-label configurations
- Review and manage cross-organization system audit trails

**Access Level:** Full system access across all organizations and modules. Can impersonate organization administrators for support purposes. Cannot modify business data directly (risk entries, assessments) -- only configuration and system data.

**Typical Workflows:**
1. **Organization Onboarding:** Create organization -> Configure org settings -> Assign Organization Administrator -> Configure module licensing -> Verify setup
2. **License Management:** Review subscription status -> Apply license changes -> Enable/disable module features -> Notify Organization Administrator
3. **System Maintenance:** Review system health -> Schedule maintenance window -> Execute maintenance -> Verify system status -> Communicate completion
4. **Incident Support:** Receive support request -> Investigate system logs -> Identify root cause -> Apply fix or escalate -> Document resolution

**Reporting Needs:** System health dashboards, license utilization reports, audit log summaries, user activity statistics, storage consumption reports.

---

### 2. Organization Administrator

| Attribute | Detail |
|-----------|--------|
| **Description** | Administrator for a specific organization within the platform. Manages organization-level configuration including user accounts, role assignments, organizational hierarchy, branding, and module-specific settings. Typically the IT Manager or GRC Program Manager within the customer organization. |
| **Primary Modules** | Administration & Configuration |
| **Secondary Modules** | All licensed modules (configuration access only) |

**Key Tasks:**
- Manage user accounts within the organization (create, deactivate, reset passwords)
- Assign roles and module access to users
- Configure organizational hierarchy (departments, business units, locations)
- Manage organization-specific lookup data (risk categories, impact scales, likelihood scales)
- Configure notification templates and distribution rules
- Set up workflow approval chains and escalation paths
- Manage organization branding (logo, color scheme, custom labels)
- Configure module-specific settings (assessment templates, scoring methodologies)
- Review organization-level audit trails
- Manage file storage settings and retention policies

**Access Level:** Full access within their organization across all licensed modules. Cannot access other organizations' data. Cannot modify platform-level system settings. Can delegate configuration tasks to module-specific administrators.

**Typical Workflows:**
1. **User Onboarding:** Create user account -> Assign roles -> Assign to business unit -> Configure module access -> Send welcome notification
2. **Module Configuration:** Access module settings -> Configure taxonomies and templates -> Set up workflow rules -> Test configuration -> Activate for users
3. **Org Hierarchy Setup:** Define departments/BUs -> Assign locations -> Map reporting lines -> Configure data visibility rules -> Verify access isolation
4. **Periodic Review:** Review user access list -> Deactivate departed users -> Audit role assignments -> Update org hierarchy as needed

**Reporting Needs:** User access reports, role assignment matrices, configuration change logs, module usage statistics, organization audit trail summaries.

---

### 3. Risk Manager

| Attribute | Detail |
|-----------|--------|
| **Description** | Domain expert responsible for enterprise risk management. Owns the risk register, conducts risk assessments, defines risk appetite and tolerance, monitors KRIs, and manages risk treatment plans. Reports to the CRO or equivalent executive. |
| **Primary Modules** | Enterprise Risk Management (ERM) |
| **Secondary Modules** | Reporting & Analytics, Workflow & Case Management, Compliance & Audit Management (read access for cross-referencing) |

**Key Tasks:**
- Maintain the enterprise risk register (create, update, close risks)
- Conduct and coordinate risk assessments (inherent, residual, target)
- Define and maintain risk categories, impact scales, and likelihood scales
- Set and monitor risk appetite statements and tolerance thresholds
- Configure and monitor Key Risk Indicators (KRIs)
- Develop and track risk treatment plans (mitigate, accept, transfer, avoid)
- Review and accept risks escalated from other modules (cyber, BCM, privacy)
- Generate risk heatmaps and trend analysis
- Prepare enterprise risk reports for executive and board audiences
- Coordinate risk assessment campaigns across business units

**Access Level:** Full CRUD access to ERM module. Read access to risks surfaced from Cybersecurity, BCM, and Privacy modules. Write access to Workflow for treatment plan tasks. Read access to Compliance for control effectiveness data.

**Typical Workflows:**
1. **Risk Identification:** Receive risk input (assessment, incident, external event) -> Create risk entry -> Classify and categorize -> Assign risk owner -> Perform initial assessment
2. **Risk Assessment:** Select risks for assessment -> Define assessment criteria -> Conduct inherent assessment -> Identify existing controls -> Assess residual risk -> Compare to appetite -> Determine treatment
3. **Treatment Planning:** Create treatment plan -> Define actions and milestones -> Assign action owners -> Set deadlines -> Monitor progress -> Reassess residual risk upon completion
4. **KRI Monitoring:** Review KRI dashboard -> Identify breached thresholds -> Investigate root cause -> Escalate if required -> Update risk assessment -> Document findings
5. **Cross-Module Risk Integration:** Receive cyber risk escalation -> Assess enterprise impact -> Link to enterprise risk -> Update heatmap -> Report to CRO

**Reporting Needs:** Risk heatmaps (inherent/residual/target), KRI dashboards with threshold indicators, risk treatment progress reports, risk trend analysis, top-10 risks report, risk appetite alignment report, board-ready risk summary.

---

### 4. CISO / Cybersecurity Manager

| Attribute | Detail |
|-----------|--------|
| **Description** | Responsible for cybersecurity risk management, NCA compliance, asset security, vulnerability management, threat tracking, and cyber incident response. Owns the cybersecurity module and ensures alignment with NCA ECC, CSCC, and other applicable cyber frameworks. |
| **Primary Modules** | Cybersecurity Risk & Compliance |
| **Secondary Modules** | Enterprise Risk Management (escalation), Compliance & Audit Management (NCA assessments), Reporting & Analytics, Workflow & Case Management |

**Key Tasks:**
- Manage the cybersecurity asset inventory (hardware, software, network, cloud)
- Track and manage vulnerabilities across assets
- Maintain threat register and threat intelligence
- Manage cyber incidents from detection through resolution
- Conduct NCA ECC/CSCC compliance assessments
- Map and manage cybersecurity controls against NCA frameworks
- Monitor cybersecurity KPIs and maturity levels
- Escalate significant cyber risks to the enterprise risk register
- Coordinate penetration testing and security assessment results
- Manage cybersecurity awareness and training tracking

**Access Level:** Full CRUD access to Cybersecurity module. Write access to ERM for risk escalation. Read/write access to Compliance for NCA-specific assessments. Write access to Workflow for incident response tasks.

**Typical Workflows:**
1. **Vulnerability Management:** Receive vulnerability scan results -> Triage vulnerabilities -> Assign remediation owners -> Track remediation -> Verify closure -> Update asset risk posture
2. **Incident Response:** Detect/receive incident report -> Classify severity -> Activate response workflow -> Investigate and contain -> Remediate -> Conduct post-incident review -> Escalate to enterprise risk if significant
3. **NCA Compliance Assessment:** Select NCA framework -> Map controls -> Assess control implementation -> Identify gaps -> Create remediation plans -> Track remediation -> Generate compliance scorecard
4. **Asset Risk Assessment:** Identify critical assets -> Assess threats and vulnerabilities -> Calculate risk scores -> Apply treatments -> Monitor residual risk -> Report cyber risk posture

**Reporting Needs:** Cyber risk posture dashboard, NCA compliance scorecards (ECC/CSCC), vulnerability aging reports, incident trend analysis, asset risk heatmaps, control effectiveness reports, cyber maturity level reports.

---

### 5. BCM Manager

| Attribute | Detail |
|-----------|--------|
| **Description** | Responsible for the business continuity management program. Conducts business impact analysis, develops and maintains business continuity plans, manages recovery strategies, and coordinates exercises and tests. Ensures alignment with ISO 22301. |
| **Primary Modules** | Business Continuity Management (BCM) |
| **Secondary Modules** | Enterprise Risk Management (risk escalation), Reporting & Analytics, Workflow & Case Management |

**Key Tasks:**
- Conduct and maintain Business Impact Analysis (BIA) across business units
- Define Recovery Time Objectives (RTO) and Recovery Point Objectives (RPO)
- Develop and maintain Business Continuity Plans (BCPs)
- Map critical process dependencies (systems, people, suppliers, facilities)
- Plan and execute BCM exercises (tabletop, functional, full-scale)
- Document exercise results and improvement actions
- Manage BCM incidents and invocations
- Escalate BCM-related risks to the enterprise risk register
- Track BCM program maturity against ISO 22301

**Access Level:** Full CRUD access to BCM module. Write access to ERM for risk escalation. Read access to Cybersecurity for IT dependency context. Write access to Workflow for exercise coordination tasks.

**Typical Workflows:**
1. **BIA Execution:** Select business processes -> Distribute BIA questionnaires -> Collect responses -> Analyze impact -> Define RTO/RPO -> Identify dependencies -> Approve BIA results
2. **BCP Development:** Review BIA results -> Identify recovery strategies -> Develop recovery procedures -> Assign plan owners -> Review and approve -> Distribute plans
3. **Exercise Management:** Plan exercise -> Define scope and objectives -> Schedule participants -> Execute exercise -> Document observations -> Analyze results -> Create improvement actions -> Track closure
4. **Dependency Mapping:** Identify critical processes -> Map IT system dependencies -> Map supplier dependencies -> Map facility dependencies -> Map personnel dependencies -> Assess single points of failure

**Reporting Needs:** BIA summary dashboards, BCP status matrix (current/expired/under review), exercise schedule and results, dependency maps, RTO/RPO compliance reports, BCM maturity scorecards, incident invocation history.

---

### 6. Data Protection Officer (DPO)

| Attribute | Detail |
|-----------|--------|
| **Description** | Responsible for personal data protection compliance under Saudi PDPL. Manages privacy impact assessments, Records of Processing Activities, consent management, data subject requests, and breach notification processes. Serves as the organizational point of contact for SDAIA. |
| **Primary Modules** | Privacy & Personal Data Protection |
| **Secondary Modules** | Data Governance (data classification context), Compliance & Audit Management (PDPL assessments), Enterprise Risk Management (privacy risk escalation), Reporting & Analytics, Workflow & Case Management |

**Key Tasks:**
- Maintain the Records of Processing Activities (RoPA)
- Conduct and manage Privacy Impact Assessments (PIA) and Data Protection Impact Assessments (DPIA)
- Manage consent records and consent lifecycle
- Process and track Data Subject Access Requests (DSARs)
- Manage privacy breach detection, assessment, notification, and documentation
- Monitor cross-border data transfer compliance
- Track PDPL compliance status across the organization
- Coordinate with Data Governance on data classification and inventory
- Prepare regulatory submissions and responses for SDAIA
- Manage privacy training and awareness tracking

**Access Level:** Full CRUD access to Privacy module. Read access to Data Governance for data inventory and classification. Write access to ERM for privacy risk escalation. Write access to Compliance for PDPL compliance assessments. Write access to Workflow for DSAR and breach response tasks.

**Typical Workflows:**
1. **RoPA Management:** Identify processing activities -> Document purpose, legal basis, data categories -> Map data flows -> Record retention periods -> Review and update periodically
2. **PIA/DPIA:** Identify processing requiring assessment -> Complete PIA questionnaire -> Assess privacy risks -> Identify mitigations -> Approve or escalate -> Monitor implementation -> Review periodically
3. **Data Subject Request:** Receive DSAR -> Verify identity -> Classify request type -> Locate personal data -> Process request -> Respond within SLA -> Document outcome
4. **Breach Management:** Detect/receive breach notification -> Assess severity and scope -> Determine notification obligations -> Notify SDAIA (if required) -> Notify affected individuals (if required) -> Document entire lifecycle -> Conduct post-breach review

**Reporting Needs:** PDPL compliance dashboard, RoPA completeness report, PIA/DPIA status tracker, DSAR metrics (volume, response time, type), breach notification timeline reports, consent analytics, cross-border transfer register, SDAIA submission reports.

---

### 7. Compliance Officer

| Attribute | Detail |
|-----------|--------|
| **Description** | Responsible for managing regulatory compliance obligations across multiple frameworks. Conducts compliance assessments, manages findings, tracks remediation, and maintains evidence repositories. Coordinates internal and external audit activities. |
| **Primary Modules** | Compliance & Audit Management |
| **Secondary Modules** | All GRC modules (read access for compliance context), Reporting & Analytics, Workflow & Case Management |

**Key Tasks:**
- Define and manage compliance frameworks and control libraries
- Map controls across multiple frameworks (NCA ECC, ISO 27001, ISO 22301, PDPL, ISO 31000)
- Conduct compliance assessments and gap analyses
- Manage compliance findings and track remediation to closure
- Collect, organize, and manage compliance evidence
- Coordinate internal audit engagements
- Support external audit processes and evidence requests
- Track regulatory obligations and deadlines
- Prepare compliance status reports for management and regulators
- Manage Corrective Action Plans (CAPs) and track effectiveness

**Access Level:** Full CRUD access to Compliance module. Read access to all other GRC modules for cross-referencing compliance data. Write access to Workflow for compliance task management. Read access to ERM for risk-informed compliance prioritization.

**Typical Workflows:**
1. **Compliance Assessment:** Select framework -> Review control mappings -> Assess control implementation -> Score compliance -> Identify gaps -> Generate gap analysis report -> Create remediation plans
2. **Finding Management:** Create finding from assessment -> Classify severity -> Assign remediation owner -> Define corrective action -> Track implementation -> Verify effectiveness -> Close finding
3. **Evidence Management:** Identify evidence requirements per control -> Collect evidence -> Review and approve evidence -> Link to controls -> Maintain evidence freshness -> Package for audit
4. **Audit Coordination:** Receive audit notification -> Prepare evidence packages -> Coordinate auditee interviews -> Track audit progress -> Receive findings -> Create remediation plans -> Track closure

**Reporting Needs:** Framework compliance scorecards, gap analysis matrices, finding aging reports, remediation progress dashboards, control effectiveness summaries, evidence coverage reports, audit schedule and status, regulatory deadline tracker.

---

### 8. Data Governance Officer

| Attribute | Detail |
|-----------|--------|
| **Description** | Responsible for the organizational data governance framework. Manages the data inventory, data classification, data quality programs, data stewardship assignments, and data lineage documentation. Ensures alignment with NDMO national data governance standards. |
| **Primary Modules** | Data Governance / Data Office |
| **Secondary Modules** | Privacy & Personal Data Protection (data classification context), Compliance & Audit Management (NDMO compliance), Reporting & Analytics, Workflow & Case Management |

**Key Tasks:**
- Maintain the organizational data inventory and data catalog
- Define and enforce data classification schemes aligned with NDMO standards
- Assign and manage data stewards across business units
- Define and monitor data quality rules and metrics
- Document and maintain data lineage for critical data assets
- Conduct data quality assessments and track remediation
- Coordinate data governance council activities
- Prepare data governance maturity reports
- Support privacy team with data inventory and classification context
- Track compliance with NDMO data governance requirements

**Access Level:** Full CRUD access to Data Governance module. Read access to Privacy for processing activity context. Write access to Workflow for data quality remediation tasks. Read access to Compliance for NDMO compliance assessments.

**Typical Workflows:**
1. **Data Inventory Management:** Identify data assets -> Document metadata -> Classify data -> Assign stewards -> Map storage locations -> Review and update periodically
2. **Data Quality Program:** Define quality rules -> Execute quality assessments -> Identify quality issues -> Create remediation plans -> Assign owners -> Track resolution -> Measure improvement
3. **Classification Campaign:** Define classification scheme -> Train stewards -> Execute classification -> Review results -> Approve classifications -> Monitor compliance -> Report coverage
4. **Lineage Documentation:** Identify critical data -> Map source systems -> Document transformations -> Map target systems -> Validate lineage -> Update on system changes

**Reporting Needs:** Data inventory completeness dashboards, classification coverage reports, data quality scorecards, stewardship assignment matrices, lineage documentation coverage, NDMO compliance reports, data governance maturity assessments.

---

### 9. Internal Auditor

| Attribute | Detail |
|-----------|--------|
| **Description** | Member of the internal audit function responsible for independent assessment of GRC processes, control effectiveness, and compliance status. Requires broad read access across all modules with the ability to create audit findings and collect evidence. Must maintain independence -- cannot modify operational data. |
| **Primary Modules** | Compliance & Audit Management |
| **Secondary Modules** | All GRC modules (read-only access for audit purposes), Reporting & Analytics |

**Key Tasks:**
- Plan and execute internal audit engagements across all GRC domains
- Review risk assessments, compliance assessments, and control effectiveness
- Collect and verify audit evidence from all modules
- Create audit findings with severity classifications
- Track finding remediation and verify corrective actions
- Generate audit reports for management and audit committee
- Assess the effectiveness of GRC processes and controls
- Review audit trail integrity across the platform
- Support external audit engagements with evidence and context
- Perform continuous auditing through platform data analysis

**Access Level:** Read-only access across all GRC modules (ERM, Cybersecurity, BCM, Privacy, Data Governance). Full CRUD access to audit-specific entities within Compliance module (audit engagements, findings, workpapers). Read access to all audit trails and system logs. Cannot modify operational data in any module.

**Typical Workflows:**
1. **Audit Planning:** Define audit universe -> Risk-rank audit areas -> Select engagements -> Allocate resources -> Create audit plan -> Approve and communicate
2. **Audit Execution:** Open engagement -> Review module data -> Collect evidence -> Test controls -> Document observations -> Discuss with auditees -> Draft findings
3. **Finding Reporting:** Classify finding severity -> Draft recommendation -> Discuss with management -> Finalize finding -> Assign remediation owner -> Set due date -> Track to closure
4. **Continuous Monitoring:** Configure monitoring queries -> Review automated alerts -> Investigate anomalies -> Document results -> Escalate as needed

**Reporting Needs:** Audit plan status, finding summary by severity and module, remediation tracking dashboard, evidence inventory, control testing results, audit trail analysis reports, audit universe coverage map.

---

### 10. Business Unit Manager

| Attribute | Detail |
|-----------|--------|
| **Description** | Manager of a specific business unit or department. Participates in GRC processes relevant to their unit -- risk assessments, BIA, compliance activities -- and has visibility limited to their own organizational scope. |
| **Primary Modules** | Enterprise Risk Management (BU-scoped), Business Continuity Management (BU-scoped) |
| **Secondary Modules** | Workflow & Case Management (assigned tasks), Reporting & Analytics (BU dashboards) |

**Key Tasks:**
- Review and validate risks identified within their business unit
- Participate in risk assessments as risk owners for BU-specific risks
- Complete BIA questionnaires for their business processes
- Review and approve BCPs for their unit
- Complete assigned compliance tasks and questionnaires
- Approve risk treatment actions assigned to their team
- Review BU-level risk and compliance dashboards
- Respond to audit inquiries related to their unit

**Access Level:** Read/write access scoped to their own business unit across ERM, BCM, and Compliance modules. Cannot see data from other business units. Read-only access to BU-level dashboards in Reporting. Task-level access in Workflow for assigned items.

**Typical Workflows:**
1. **Risk Assessment Participation:** Receive assessment request -> Review identified risks -> Provide impact/likelihood input -> Validate risk scoring -> Approve treatment plans -> Monitor treatment progress
2. **BIA Response:** Receive BIA questionnaire -> Identify critical processes -> Assess impact timeframes -> Define dependencies -> Submit responses -> Review BIA results
3. **Task Completion:** Receive workflow task -> Review task details -> Complete required action -> Attach evidence if needed -> Submit for approval

**Reporting Needs:** BU risk summary dashboard, assigned task list with deadlines, BU compliance status, BIA summary for their processes, treatment plan progress.

---

### 11. Executive / Board Member

| Attribute | Detail |
|-----------|--------|
| **Description** | C-level executive or board member who requires high-level visibility into the organization's GRC posture. Consumes dashboards and reports but does not perform operational tasks. May approve high-level risk acceptance decisions or policy changes through workflow. |
| **Primary Modules** | Reporting & Analytics |
| **Secondary Modules** | Enterprise Risk Management (read-only, dashboards), Workflow & Case Management (approval tasks) |

**Key Tasks:**
- Review enterprise risk dashboards and heatmaps
- Review compliance status summaries across all frameworks
- Approve risk acceptance decisions above defined thresholds
- Review board-level reports (quarterly risk report, annual compliance summary)
- Monitor KRIs and KPIs at the strategic level
- Review and approve risk appetite statements
- Access executive briefing dashboards

**Access Level:** Read-only access to executive dashboards and reports across all modules. Approval authority in Workflow for high-value decisions. Cannot access detailed operational data or individual records. All views are aggregated and summarized.

**Typical Workflows:**
1. **Dashboard Review:** Log in -> Access executive dashboard -> Review risk heatmap -> Review compliance status -> Review KRI alerts -> Drill into any flagged items
2. **Risk Acceptance Approval:** Receive approval request -> Review risk details and justification -> Approve or reject -> Add comments -> Notification sent to Risk Manager
3. **Board Report Review:** Access quarterly report -> Review trends -> Download PDF export -> Prepare for board meeting

**Reporting Needs:** Executive risk dashboard (single-page summary), enterprise risk heatmap, compliance status by framework, KRI trend charts, top risks report, incident summary, board-ready PDF exports.

---

### 12. Workflow Approver

| Attribute | Detail |
|-----------|--------|
| **Description** | A role overlay that enables users to participate in approval workflows. Any user can be designated as a Workflow Approver for specific workflow chains. This role is typically combined with another primary role (e.g., a BU Manager who is also an approver for risk treatments). |
| **Primary Modules** | Workflow & Case Management |
| **Secondary Modules** | Context-dependent (reads data from the module that generated the workflow item) |

**Key Tasks:**
- Review and approve/reject workflow items assigned to them
- Add comments and conditions to approvals
- Delegate approvals when unavailable
- Review approval history for items in their chain
- Monitor pending approval queue and SLA compliance
- Escalate overdue approvals

**Access Level:** Read access to the specific records that are subject to their approval decision. Write access limited to approval actions (approve, reject, return, delegate). Cannot modify the underlying data being approved. SLA visibility for their approval queue.

**Typical Workflows:**
1. **Standard Approval:** Receive notification -> Open approval task -> Review submitted data -> Approve or reject -> Add comments -> Task advances to next step
2. **Delegation:** Receive notification while unavailable -> Delegate to alternate approver -> Alternate receives and processes -> Original approver notified of outcome
3. **Escalation:** SLA threshold approached -> Receive reminder -> If still not actioned -> Auto-escalate to next level

**Reporting Needs:** Pending approval queue, approval history, SLA compliance metrics, delegation log.

---

### 13. Standard User

| Attribute | Detail |
|-----------|--------|
| **Description** | General staff member who interacts with the GRC platform for basic operational tasks: completing assessments, responding to questionnaires, submitting data, and acknowledging policies. Represents the broadest user base with the most limited access. |
| **Primary Modules** | Workflow & Case Management (task completion) |
| **Secondary Modules** | Module-specific (limited to assigned tasks and data entry) |

**Key Tasks:**
- Complete assigned risk assessment questionnaires
- Respond to BIA questionnaires for their processes
- Submit incident reports
- Acknowledge policies and complete attestations
- Upload evidence documents when requested
- Complete compliance self-assessments
- View their own task history and status

**Access Level:** Minimal access limited to assigned tasks and their own submissions. Cannot browse module data beyond their assigned scope. Read access to their own task history. Write access only through guided workflows and forms.

**Typical Workflows:**
1. **Questionnaire Response:** Receive notification -> Open questionnaire -> Answer questions -> Attach supporting documents if needed -> Submit response
2. **Incident Reporting:** Access incident report form -> Describe incident -> Classify initial severity -> Attach evidence -> Submit -> Receive confirmation
3. **Policy Acknowledgment:** Receive policy notification -> Review policy document -> Confirm acknowledgment -> Record stored in audit trail

**Reporting Needs:** Personal task list, task completion history, upcoming deadlines.

---

## Role-Module Access Matrix

| Role | ERM | Cyber | BCM | Data Gov | Privacy | Compliance | Workflow | Reporting | Licensing | Admin |
|------|-----|-------|-----|----------|---------|------------|----------|-----------|-----------|-------|
| Platform Admin | Cfg | Cfg | Cfg | Cfg | Cfg | Cfg | Cfg | Cfg | Full | Full |
| Org Admin | Cfg | Cfg | Cfg | Cfg | Cfg | Cfg | Cfg | Cfg | Read | Full* |
| Risk Manager | Full | Read | Read | -- | Read | Read | R/W | Read | -- | -- |
| CISO / Cyber Mgr | R/W | Full | Read | -- | -- | R/W | R/W | Read | -- | -- |
| BCM Manager | R/W | Read | Full | -- | -- | Read | R/W | Read | -- | -- |
| DPO | R/W | -- | -- | Read | Full | R/W | R/W | Read | -- | -- |
| Compliance Officer | Read | Read | Read | Read | Read | Full | R/W | Read | -- | -- |
| Data Gov Officer | -- | -- | -- | Full | Read | Read | R/W | Read | -- | -- |
| Internal Auditor | Read | Read | Read | Read | Read | Full* | Read | Read | -- | -- |
| BU Manager | BU | -- | BU | -- | -- | BU | Tasks | BU | -- | -- |
| Executive | Dash | Dash | Dash | Dash | Dash | Dash | Approve | Full | -- | -- |
| Workflow Approver | -- | -- | -- | -- | -- | -- | Approve | -- | -- | -- |
| Standard User | Tasks | Tasks | Tasks | Tasks | Tasks | Tasks | Tasks | -- | -- | -- |

**Legend:**
- **Full**: Complete CRUD access to all entities within the module
- **Full***: With restrictions (Org Admin: within org only; Internal Auditor: audit entities only with read for others)
- **R/W**: Read and write access to specific cross-module integration points
- **Read**: Read-only access to module data
- **Cfg**: Configuration access only (settings, templates, taxonomies -- not business data)
- **BU**: Access scoped to the user's business unit only
- **Dash**: Dashboard/summary views only (no record-level access)
- **Tasks**: Access limited to assigned tasks and own submissions
- **Approve**: Approval actions only on assigned workflow items
- **--**: No access

---

## Language and Localization Requirements by Role

| Role | Primary Language | Interface Preference | Report Language | RTL Requirement |
|------|-----------------|---------------------|-----------------|-----------------|
| Platform Admin | English | LTR | English | Low |
| Org Admin | Arabic or English | User preference | Bilingual | High |
| Risk Manager | Arabic or English | User preference | Bilingual | High |
| CISO / Cyber Mgr | English (common) | User preference | Bilingual | Medium |
| BCM Manager | Arabic or English | User preference | Bilingual | High |
| DPO | Arabic or English | User preference | Bilingual | High |
| Compliance Officer | Arabic or English | User preference | Bilingual | High |
| Data Gov Officer | Arabic or English | User preference | Bilingual | High |
| Internal Auditor | Arabic or English | User preference | Bilingual | High |
| BU Manager | Arabic (common) | User preference | Arabic preferred | High |
| Executive | Arabic (common) | User preference | Arabic preferred | High |
| Workflow Approver | Arabic or English | User preference | N/A | High |
| Standard User | Arabic (common) | User preference | N/A | High |

---

## User Volume Estimates (Typical Enterprise Deployment)

| Role | Estimated Users per Organization | Concurrent Usage Pattern |
|------|----------------------------------|-------------------------|
| Platform Admin | 1-2 | Low (periodic) |
| Org Admin | 1-3 | Low-Medium (daily admin) |
| Risk Manager | 2-5 | High (daily operational) |
| CISO / Cyber Mgr | 1-3 | High (daily operational) |
| BCM Manager | 1-3 | Medium (periodic campaigns) |
| DPO | 1-2 | Medium (daily operational) |
| Compliance Officer | 2-5 | High (daily operational) |
| Data Gov Officer | 1-3 | Medium (daily operational) |
| Internal Auditor | 3-8 | Medium (engagement-based) |
| BU Manager | 10-30 | Low-Medium (task-driven) |
| Executive | 3-10 | Low (dashboard review) |
| Workflow Approver | 15-40 | Low (notification-driven) |
| Standard User | 50-500 | Low (task-driven, periodic) |

---

## Persona-Driven UX Implications

| Persona Group | UX Priority | Key Design Consideration |
|---------------|-------------|--------------------------|
| **Domain Experts** (Risk Mgr, CISO, BCM Mgr, DPO, Compliance, Data Gov) | Efficiency | Dense information displays, keyboard shortcuts, bulk operations, advanced filtering, customizable views |
| **Executives** | Clarity | Clean dashboards, visual summaries, one-click drill-down, mobile-friendly, minimal clicks to insight |
| **BU Managers** | Simplicity | Guided forms, clear task lists, BU-scoped views, no module navigation complexity, Arabic-first |
| **Standard Users** | Minimal friction | Single-purpose forms, clear instructions, progress indicators, mobile-responsive, Arabic-first |
| **Auditors** | Traceability | Cross-module search, evidence linking, export capabilities, immutable views, timestamp visibility |
| **Administrators** | Control | Comprehensive settings panels, bulk operations, import/export, audit logs, configuration validation |
