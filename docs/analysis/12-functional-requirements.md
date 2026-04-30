# Functional Requirements

## Purpose
Detailed functional requirements for the TSS GRC Platform, organized by module with unique IDs, priority levels, and acceptance criteria.

**Priority Levels:** Must (M) = non-negotiable, Should (S) = high value, Could (C) = desirable

---

## Platform / Shared Requirements (FR-PLT)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-PLT-001 | Users must authenticate using username/password before accessing any part of the system | M | Login page enforces authentication; unauthenticated requests redirect to login |
| FR-PLT-002 | System must enforce RBAC; users only see and perform actions permitted by their roles | M | Users without permission receive 403; navigation hides unauthorized links |
| FR-PLT-003 | Every create, update, delete, and approve action must be recorded in the audit trail | M | Audit log record created for every state-changing operation |
| FR-PLT-004 | System must support Arabic and English languages with full RTL/LTR layout switching | M | Language toggle switches UI direction and all labels without page reload |
| FR-PLT-005 | Users must be able to attach files (PDF, Word, Excel, images) to any business entity | M | File upload UI on all entity forms; metadata stored; file retrievable |
| FR-PLT-006 | System must send email and in-app notifications for key workflow events | S | Notifications delivered within 60 seconds of triggering event |
| FR-PLT-007 | System must support organization and business unit hierarchy | M | Org and BU setup in admin; all entities scoped to org |
| FR-PLT-008 | System must provide full-text search within each module | S | Search returns relevant results within 2 seconds |
| FR-PLT-009 | System must support configurable lookup/reference data (risk levels, statuses, etc.) | M | Admin can add/edit/deactivate lookup values; values appear in dropdowns |
| FR-PLT-010 | System must allow exporting data to PDF, Excel, and Word formats | M | Export buttons on list and detail pages; generated files are well-formatted |
| FR-PLT-011 | System must enforce configurable password policy (complexity, expiry, history) | M | Admin sets policy; system enforces on password change/reset |
| FR-PLT-012 | User sessions must time out after configurable period of inactivity | M | Session expires; user redirected to login page |
| FR-PLT-013 | System must provide pagination for all list views | M | Lists paginated with configurable page size; total count displayed |
| FR-PLT-014 | System must support configurable date formats and timezone settings | S | User preference for date display; all timestamps stored in UTC |

---

## Enterprise Risk Management (FR-ERM)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-ERM-001 | Users must be able to create, read, update, and delete risk records in the risk register | M | CRUD operations work; changes logged to audit trail |
| FR-ERM-002 | Each risk must support inherent and residual risk scoring (likelihood × impact matrix) | M | 5×5 risk matrix; scores auto-calculated; risk level (Critical/High/Medium/Low) derived |
| FR-ERM-003 | System must provide a visual risk heatmap showing risks by likelihood and impact | M | Heatmap rendered on dashboard; clickable cells navigate to risk list |
| FR-ERM-004 | Users must be able to categorize risks by type (Strategic, Operational, Financial, Compliance, Cyber) | M | Category dropdown on risk form; filter by category on list view |
| FR-ERM-005 | System must support risk treatment recording (Accept, Mitigate, Transfer, Avoid) | M | Treatment strategy selectable; treatment plans attachable |
| FR-ERM-006 | System must link risks to mitigating controls from the shared control library | M | Multi-select control linkage; control effectiveness tracked per risk |
| FR-ERM-007 | System must support risk appetite statements per risk category | S | Admin sets appetite thresholds; risks exceeding appetite flagged |
| FR-ERM-008 | System must allow risks to be submitted for review/approval via workflow | M | Risk submit → workflow instance created → approver notified |
| FR-ERM-009 | System must track Key Risk Indicators (KRIs) with threshold alerts | S | KRI defined with green/amber/red thresholds; breach triggers notification |
| FR-ERM-010 | System must support cross-module risk linkage (risks from Cyber, BCM, Privacy elevated to ERM) | M | Source module and source entity ID tracked on risk; unified view shows all |
| FR-ERM-011 | System must generate a risk register report exportable to PDF and Excel | M | Report includes all risk fields, scores, treatments, owner, review date |
| FR-ERM-012 | Users must be able to record periodic risk review dates and history | M | Review date field; review history with notes tracked |

---

## Cybersecurity Risk & Compliance (FR-CYB)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-CYB-001 | Users must be able to maintain an IT/OT asset inventory with classification | M | Asset CRUD; classification (Critical, High, Medium, Low) required |
| FR-CYB-002 | System must support recording vulnerabilities linked to assets with severity scoring | M | Vulnerability CRUD; CVE ID field; severity (Critical/High/Medium/Low/Informational) |
| FR-CYB-003 | Users must be able to record and manage cybersecurity incidents | M | Incident CRUD; mandatory fields: title, severity, type, detected date |
| FR-CYB-004 | Cyber incidents must be linkable to enterprise risks in the ERM module | M | Link field on incident form; linked risk displayed; bidirectional navigation |
| FR-CYB-005 | System must support NCA ECC control assessments | M | Pre-loaded NCA ECC controls; assessment against each control; status tracking |
| FR-CYB-006 | System must provide a cybersecurity dashboard with key metrics | M | Dashboard shows: open incidents by severity, top vulnerabilities, control compliance % |
| FR-CYB-007 | Users must be able to escalate cyber incidents to management via workflow | M | Escalate action → workflow instance → management notified |
| FR-CYB-008 | System must track threat intelligence entries | S | Threat CRUD; type, source, likelihood, impact fields |
| FR-CYB-009 | System must support cybersecurity assessment reporting | M | Assessment report with control scores, findings, recommendations |
| FR-CYB-010 | Privacy breaches must be linkable to cyber incidents | M | Breach record links to cyber incident; cross-module navigation |
| FR-CYB-011 | System must track vulnerability remediation status and due dates | M | Remediation status field; due date; overdue items highlighted |

---

## Business Continuity Management (FR-BCM)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-BCM-001 | Users must be able to record and manage critical business processes with RTO/RPO | M | Process CRUD; RTO, RPO, MTPD fields required |
| FR-BCM-002 | System must support Business Impact Analysis (BIA) creation and management | M | BIA CRUD; linked to process; financial, operational, regulatory impact fields |
| FR-BCM-003 | Users must be able to create and maintain BCM/BCP plans | M | Plan CRUD; versioning; link to processes; approval workflow |
| FR-BCM-004 | System must track BCM exercises with findings and lessons learned | M | Exercise CRUD; date, type, results, findings fields |
| FR-BCM-005 | System must capture process dependencies (internal, external, IT systems, vendors) | M | Dependency matrix linked to processes |
| FR-BCM-006 | BCM incidents must be linkable to enterprise risks | M | BCM incident → linked_risk_id → ERM risk |
| FR-BCM-007 | System must generate BIA reports and BCP summaries | M | Exportable BIA and BCP reports to PDF |
| FR-BCM-008 | System must provide BCM dashboard with plan status and upcoming exercise schedule | S | Dashboard widget showing plan review dates, exercise schedule, BIA status |

---

## Data Governance / Data Office (FR-DGV)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-DGV-001 | Users must be able to register and classify data assets | M | Data asset CRUD; classification levels; owner assignment |
| FR-DGV-002 | System must support data quality rule definition per data asset | S | Quality rule CRUD; linked to data asset; check frequency |
| FR-DGV-003 | System must allow data lineage recording (source → transformation → target) | S | Lineage relationship CRUD; visual lineage path |
| FR-DGV-004 | System must support data steward assignment per data asset | M | Steward assignment; steward notified of data quality issues |
| FR-DGV-005 | System must integrate data classification with Privacy module's data inventory | M | Shared data asset record; privacy and data governance both reference it |
| FR-DGV-006 | System must generate a data inventory report | M | Report of all data assets with classification, owner, steward |

---

## Privacy & Personal Data Protection (FR-PRV)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-PRV-001 | Users must be able to create Privacy Impact Assessments (PIA) and DPIAs | M | Assessment CRUD; type selection (PIA/DPIA); risk level output |
| FR-PRV-002 | System must support Record of Processing Activities (RoPA) per PDPL requirements | M | RoPA entries CRUD; all mandatory PDPL fields included |
| FR-PRV-003 | Users must be able to register and manage personal data processing activities | M | Processing activity CRUD; legal basis, purpose, data subjects, recipients |
| FR-PRV-004 | System must track consent records for data subject consent | S | Consent CRUD; consent date, withdrawal date, evidence attachment |
| FR-PRV-005 | System must support personal data breach recording and management | M | Breach CRUD; regulatory notification tracking; linked to cyber incident |
| FR-PRV-006 | System must track PDPL data subject rights requests (access, erasure, correction) | S | Rights request CRUD; due date tracking; response status |
| FR-PRV-007 | Privacy breaches must trigger notification workflow within configurable timeframe | M | Breach created → notification task created → overdue alert if not actioned |
| FR-PRV-008 | System must generate RoPA report compliant with PDPL requirements | M | RoPA report exportable to PDF/Word; all required fields included |

---

## Compliance & Audit Management (FR-CMP)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-CMP-001 | System must support assessments against multiple compliance frameworks | M | Framework selection; assessment against all mapped controls |
| FR-CMP-002 | Users must be able to record compliance findings with severity classification | M | Finding CRUD; severity (Critical/High/Medium/Low); linked to control |
| FR-CMP-003 | System must support gap analysis with recommended remediation actions | M | Gap CRUD; root cause; recommended action; linked to finding |
| FR-CMP-004 | Users must be able to create remediation plans with owner and target date | M | Remediation plan CRUD; responsible user; due date; status tracking |
| FR-CMP-005 | System must support evidence submission and management per control | M | Evidence upload; linked to control and assessment; acceptance workflow |
| FR-CMP-006 | System must track compliance score per framework over time | M | Score calculated from compliant/total controls; trend chart on dashboard |
| FR-CMP-007 | Compliance assessment must be submittable for approval via workflow | M | Submit → approver workflow → signed-off assessment |
| FR-CMP-008 | System must support pre-loaded NCA ECC, ISO 27001, ISO 22301, ISO 31000 frameworks | M | Frameworks pre-loaded with all controls at deployment |
| FR-CMP-009 | System must generate compliance status reports per framework | M | Report shows control-by-control compliance status, findings, gaps |
| FR-CMP-010 | Shared controls must show compliance evidence from multiple assessments | M | Control detail page shows all assessments referencing that control |

---

## Workflow & Case Management (FR-WFL)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-WFL-001 | Admin must be able to define workflow templates for any module entity type | M | Workflow definition CRUD; steps configured; assigned to entity type |
| FR-WFL-002 | Workflows must support multi-step approval chains with role-based assignment | M | N steps configurable; each step assigned to role or specific user |
| FR-WFL-003 | Approvers must be able to approve, reject, or return items for revision | M | Three-action buttons on workflow task; mandatory comment for rejection |
| FR-WFL-004 | System must enforce SLA per workflow step with overdue notifications | M | SLA hours configured per step; overdue status set when exceeded; notification sent |
| FR-WFL-005 | System must support delegation of approval tasks | S | Delegate action assigns task to another user; audit trail maintained |
| FR-WFL-006 | Users must have a personal task inbox showing all pending workflow actions | M | My Tasks page; task count badge in navigation |
| FR-WFL-007 | System must send notifications to next approver when workflow step completed | M | Email + in-app notification on step completion |
| FR-WFL-008 | Workflow history must be viewable on every workflow-enabled entity | M | Timeline view showing all workflow steps, decisions, dates, actors |

---

## Reporting & Analytics (FR-RPT)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-RPT-001 | System must provide a unified risk dashboard aggregating data from all modules | M | Dashboard auto-refreshes; data from ERM, Cyber, BCM, Privacy, Compliance visible |
| FR-RPT-002 | Each module must provide a dedicated operational dashboard | M | Module-specific KPIs, charts, and status indicators |
| FR-RPT-003 | System must provide an executive dashboard with high-level KPIs | M | Configurable executive view; risk heatmap; compliance score; incident trends |
| FR-RPT-004 | System must support scheduled report generation and email delivery | S | Cron-configured schedules; report emailed to configured recipients |
| FR-RPT-005 | All reports must be exportable to PDF, Excel, and Word | M | Export buttons on all report pages; files properly formatted |
| FR-RPT-006 | PDF exports must support Arabic text and RTL layout | M | Arabic text renders correctly in exported PDFs |
| FR-RPT-007 | System must support saved filter configurations per user | S | Save filter as named preset; apply saved filter from dropdown |

---

## Licensing & Subscription (FR-LIC)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-LIC-001 | System must enforce module access based on active licenses | M | Unlicensed modules not accessible; license check on every request |
| FR-LIC-002 | Admin must be able to activate/deactivate module licenses per organization | M | License management UI; activation takes effect immediately |
| FR-LIC-003 | System must enforce user count limits per license type | S | Error message when named user limit exceeded |
| FR-LIC-004 | System must display license expiry warnings to administrators | M | Warning banner 30/15/7 days before expiry |

---

## Administration & Configuration (FR-ADM)

| ID | Description | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| FR-ADM-001 | Admins must be able to create, read, update, deactivate user accounts | M | User management CRUD; deactivated users cannot log in |
| FR-ADM-002 | Admins must be able to create and assign roles to users | M | Role CRUD; role assignment UI; immediate effect |
| FR-ADM-003 | Admins must be able to configure system-wide settings | M | Settings UI with save/apply; changes logged to audit |
| FR-ADM-004 | Admins must be able to manage lookup/reference data | M | Lookup category and value CRUD; active/inactive toggle |
| FR-ADM-005 | System must support configurable notification templates | S | Template editor with variable substitution; preview mode |
| FR-ADM-006 | Admins must be able to view and filter audit logs | M | Audit log search by date, user, entity, action; exportable |
| FR-ADM-007 | Admins must be able to configure workflow definitions | M | Workflow definition editor; assign to entity types |
| FR-ADM-008 | System must support organization branding (logo, colors, application title) | S | Upload logo; configure primary color; visible on login and header |
