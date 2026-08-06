# Stakeholder Analysis
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document identifies all stakeholders of the TSS GRC Platform, maps their interests, influence, and involvement requirements, and establishes a RACI matrix for key platform decisions. It serves as the foundation for communication planning, change management, and governance structures throughout the platform lifecycle.

---

## Stakeholder Categories

Stakeholders are grouped into four categories based on their organizational relationship to the platform:

| Category | Description |
|----------|-------------|
| **Executive Sponsors** | C-level and board members who authorize, fund, and champion the platform |
| **Primary Users** | Domain experts who use the platform daily to perform GRC functions |
| **Supporting Stakeholders** | IT, operations, and administrative staff who enable platform operation |
| **External Stakeholders** | Regulators, auditors, and third parties who interact with platform outputs |

---

## Detailed Stakeholder Map

### 1. Chief Executive Officer (CEO) / Managing Director

| Attribute | Detail |
|-----------|--------|
| **Role** | Ultimate executive sponsor; accountable for organizational risk posture and regulatory compliance to the Board of Directors |
| **Interests** | Strategic risk visibility, regulatory compliance assurance, return on GRC investment, reputational risk reduction, Vision 2030 alignment |
| **Influence Level** | **High** |
| **Involvement Type** | Sponsor / Decision Maker |
| **Communication Needs** | Quarterly executive dashboards, annual risk posture reports, exception-based alerts for critical risks, board-ready summaries |
| **Key Concerns** | Platform delivers measurable compliance improvements; total cost of ownership remains within budget; platform does not introduce operational disruption |
| **Preferred Formats** | Executive summary PDF, visual dashboards (Arabic/English), board presentation exports |

### 2. Board of Directors / Board Risk Committee

| Attribute | Detail |
|-----------|--------|
| **Role** | Governance oversight body; approves risk appetite, reviews enterprise risk posture, ensures regulatory obligations are met |
| **Interests** | Enterprise risk aggregation, regulatory compliance status, audit findings trends, incident severity tracking, fiduciary duty fulfillment |
| **Influence Level** | **High** |
| **Involvement Type** | Oversight / Approval |
| **Communication Needs** | Quarterly board reports with trend analysis, annual risk appetite review materials, immediate notification of critical incidents or regulatory actions |
| **Key Concerns** | Accurate, timely, and auditable risk information; clear accountability chains; alignment with NCA, PDPL, and sector-specific mandates |
| **Preferred Formats** | Board-formatted reports (PDF/PowerPoint exports), heatmaps, trend charts, Arabic/English bilingual summaries |

### 3. Chief Information Security Officer (CISO)

| Attribute | Detail |
|-----------|--------|
| **Role** | Accountable for cybersecurity risk management, NCA compliance, incident response, and security architecture |
| **Interests** | Cybersecurity risk register accuracy, NCA ECC/CSCC compliance tracking, vulnerability management, incident response efficiency, security metrics and KPIs |
| **Influence Level** | **High** |
| **Involvement Type** | Primary User / Domain Owner / Requirements Authority |
| **Communication Needs** | Real-time dashboards for cyber risk posture, weekly vulnerability summaries, monthly NCA compliance scorecards, immediate incident alerts |
| **Key Concerns** | Platform covers all NCA control domains; cyber incidents link to enterprise risks; asset inventory is comprehensive; compliance evidence is audit-ready |
| **Preferred Formats** | Interactive dashboards, drill-down reports, automated compliance scorecards, API-exportable data for SOC integration |

### 4. Chief Risk Officer (CRO) / Head of Risk Management

| Attribute | Detail |
|-----------|--------|
| **Role** | Accountable for the enterprise risk management framework, risk appetite definition, and risk-informed decision-making |
| **Interests** | Enterprise risk register completeness, risk appetite alignment, KRI monitoring, treatment plan effectiveness, cross-functional risk aggregation |
| **Influence Level** | **High** |
| **Involvement Type** | Primary User / Domain Owner / Requirements Authority |
| **Communication Needs** | Daily KRI dashboards, weekly risk treatment status, monthly enterprise risk reports, quarterly risk appetite reviews, ad-hoc risk analysis |
| **Key Concerns** | Risks from all modules (cyber, BCM, privacy, compliance) feed into the enterprise risk register; heatmaps reflect accurate assessments; treatment tracking is enforceable |
| **Preferred Formats** | Risk heatmaps, KRI trend dashboards, treatment Gantt charts, risk register exports (Excel/PDF) |

### 5. Data Protection Officer (DPO)

| Attribute | Detail |
|-----------|--------|
| **Role** | Accountable for PDPL compliance, personal data protection program, data subject rights management, and breach notification |
| **Interests** | RoPA accuracy, PIA/DPIA completion rates, consent management, data subject request tracking, breach notification timeliness, NDMO alignment |
| **Influence Level** | **High** |
| **Involvement Type** | Primary User / Domain Owner / Requirements Authority |
| **Communication Needs** | Monthly privacy compliance dashboards, breach notification timelines, quarterly PDPL compliance reports, data subject request SLA tracking |
| **Key Concerns** | Platform supports full PDPL lifecycle; privacy breaches auto-escalate to incidents; cross-border data transfer tracking; consent records are legally defensible |
| **Preferred Formats** | Compliance scorecards, RoPA exports, breach timeline reports, data flow diagrams, NDMO submission-ready reports |

### 6. Compliance Officer / Head of Compliance

| Attribute | Detail |
|-----------|--------|
| **Role** | Responsible for regulatory compliance management, internal policy enforcement, compliance assessments, and remediation tracking |
| **Interests** | Compliance framework coverage, gap analysis accuracy, finding remediation rates, regulatory change tracking, evidence management |
| **Influence Level** | **High** |
| **Involvement Type** | Primary User / Domain Owner / Requirements Authority |
| **Communication Needs** | Weekly compliance task summaries, monthly compliance scorecards by framework, quarterly gap analysis reports, regulatory deadline alerts |
| **Key Concerns** | Multiple compliance frameworks are manageable simultaneously; controls map across frameworks (NCA ECC, ISO 27001, PDPL, ISO 22301); evidence collection is streamlined; audit trail is tamper-proof |
| **Preferred Formats** | Framework compliance matrices, gap analysis reports, finding aging reports, evidence package exports |

### 7. BCM Manager / Business Continuity Lead

| Attribute | Detail |
|-----------|--------|
| **Role** | Responsible for business continuity planning, business impact analysis, recovery strategy development, and exercise management |
| **Interests** | BIA completeness, BCP currency, recovery time objectives, exercise effectiveness, dependency mapping accuracy, ISO 22301 compliance |
| **Influence Level** | **Medium** |
| **Involvement Type** | Primary User / Domain Owner |
| **Communication Needs** | Monthly BCP status dashboards, exercise schedule and results, BIA review reminders, dependency change alerts, quarterly BCM reports |
| **Key Concerns** | BIA feeds into enterprise risk; BCM incidents escalate properly; dependency maps are current; exercise results drive plan improvements |
| **Preferred Formats** | BIA summary dashboards, BCP status matrices, exercise result reports, dependency diagrams, recovery timeline charts |

### 8. IT Director / Head of IT

| Attribute | Detail |
|-----------|--------|
| **Role** | Responsible for IT infrastructure, platform deployment, integration with existing systems, and technical operations |
| **Interests** | Platform reliability, integration capabilities, deployment flexibility (cloud/on-premises), performance, scalability, data backup and recovery |
| **Influence Level** | **Medium** |
| **Involvement Type** | Technical Authority / Supporting Role |
| **Communication Needs** | Technical architecture documentation, deployment guides, integration specifications, performance reports, incident notifications |
| **Key Concerns** | Platform integrates with existing AD/LDAP; SQL Server deployment is standard; API availability for system integration; backup procedures are robust; platform does not create shadow IT |
| **Preferred Formats** | Technical documentation, architecture diagrams, API specifications, system health dashboards |

### 9. Internal Audit Director / Chief Audit Executive

| Attribute | Detail |
|-----------|--------|
| **Role** | Leads internal audit function; uses platform data for audit planning, fieldwork, and reporting; validates control effectiveness |
| **Interests** | Audit trail integrity, evidence accessibility, finding management, control testing results, cross-module audit visibility, independence of audit data |
| **Influence Level** | **Medium** |
| **Involvement Type** | Primary User / Oversight |
| **Communication Needs** | Audit-specific dashboards, finding status reports, evidence packages, control effectiveness summaries, audit universe coverage reports |
| **Key Concerns** | Read access across all modules without data modification capability; audit trail is immutable; evidence is version-controlled; audit findings link to risk register entries and compliance gaps |
| **Preferred Formats** | Finding aging reports, audit workpaper exports, control effectiveness matrices, trend analysis charts |

### 10. Business Unit Heads / Department Managers

| Attribute | Detail |
|-----------|--------|
| **Role** | Responsible for operational risks within their business unit; participate in risk assessments, BIA, and compliance activities for their domain |
| **Interests** | Understanding risks in their BU, compliance obligations affecting their operations, BIA results for their processes, task completion and deadlines |
| **Influence Level** | **Medium** |
| **Involvement Type** | Contributing User / Data Provider |
| **Communication Needs** | BU-specific risk summaries, assigned task notifications, assessment participation reminders, BU compliance status |
| **Key Concerns** | Platform does not create excessive administrative burden; views are filtered to their BU; workflows are intuitive; Arabic interface is fully functional |
| **Preferred Formats** | Simplified dashboards, task lists, BU-level risk summaries, email notifications in preferred language |

### 11. Data Governance Officer / Chief Data Officer

| Attribute | Detail |
|-----------|--------|
| **Role** | Responsible for data governance framework, data quality, data classification, data stewardship programs, and data catalog management |
| **Interests** | Data inventory completeness, classification coverage, data quality metrics, stewardship assignment tracking, lineage documentation, NDMO compliance |
| **Influence Level** | **Medium** |
| **Involvement Type** | Primary User / Domain Owner |
| **Communication Needs** | Data quality dashboards, classification coverage reports, stewardship assignment status, monthly data governance scorecards |
| **Key Concerns** | Data classification aligns with NDMO requirements; quality rules are enforceable; stewardship assignments cover all critical data assets; lineage tracking supports impact analysis |
| **Preferred Formats** | Data quality scorecards, classification matrices, stewardship maps, lineage diagrams, inventory exports |

### 12. End Users / Standard Users

| Attribute | Detail |
|-----------|--------|
| **Role** | Staff members who perform data entry, respond to assessments, complete assigned tasks, and participate in GRC processes |
| **Interests** | Intuitive interface, clear task assignments, minimal training requirement, language preference support, mobile accessibility |
| **Influence Level** | **Low** |
| **Involvement Type** | Contributing User |
| **Communication Needs** | Task notifications, deadline reminders, simple status views, help documentation in Arabic and English |
| **Key Concerns** | Platform is easy to use; Arabic interface is natural (not translated-feeling); tasks are clearly defined; training materials are available |
| **Preferred Formats** | Email/in-app notifications, simple task lists, contextual help, guided workflows |

### 13. System Administrators

| Attribute | Detail |
|-----------|--------|
| **Role** | Technical staff responsible for platform configuration, user provisioning, system maintenance, and operational monitoring |
| **Interests** | Configuration simplicity, monitoring tools, user management efficiency, backup procedures, log access, troubleshooting capabilities |
| **Influence Level** | **Low** |
| **Involvement Type** | Technical Operations |
| **Communication Needs** | System health alerts, configuration change logs, user provisioning workflows, maintenance schedules, upgrade procedures |
| **Key Concerns** | Platform provides adequate admin tooling; bulk user operations are supported; configuration changes are audited; system diagnostics are accessible |
| **Preferred Formats** | Admin console, system logs, configuration dashboards, operational runbooks |

### 14. External Auditors

| Attribute | Detail |
|-----------|--------|
| **Role** | Third-party auditors who assess organizational compliance with regulatory frameworks (NCA, PDPL, ISO standards, sector regulators) |
| **Interests** | Evidence integrity, compliance assessment results, control effectiveness data, finding history, remediation tracking, audit trail access |
| **Influence Level** | **Medium** |
| **Involvement Type** | External Reviewer (time-bound access) |
| **Communication Needs** | Evidence package exports, compliance assessment reports, control mapping matrices, finding and remediation history |
| **Key Concerns** | Evidence is verifiable and tamper-proof; reports can be exported in standard formats; access is time-bound and scoped; data integrity is demonstrable |
| **Preferred Formats** | PDF/Excel exports, evidence packages with checksums, compliance matrices, signed audit reports |

### 15. National Cybersecurity Authority (NCA)

| Attribute | Detail |
|-----------|--------|
| **Role** | Saudi regulatory authority for cybersecurity; issues ECC, CSCC, and other cybersecurity frameworks; conducts compliance assessments |
| **Interests** | Organizational compliance with NCA frameworks, incident reporting timeliness, cybersecurity maturity levels, national cybersecurity posture |
| **Influence Level** | **High** (regulatory authority) |
| **Involvement Type** | External Regulator (indirect) |
| **Communication Needs** | NCA-formatted compliance reports, incident notification outputs, maturity assessment results |
| **Key Concerns** | Platform accurately represents NCA control frameworks; compliance assessments align with NCA methodology; incident reporting supports NCA timelines and formats |
| **Preferred Formats** | NCA-standard report formats, compliance scorecards aligned to ECC/CSCC domains |

### 16. National Data Management Office (NDMO)

| Attribute | Detail |
|-----------|--------|
| **Role** | Saudi regulatory authority for data governance and data management; issues national data governance standards and guidelines |
| **Interests** | Data governance framework adoption, data classification compliance, open data readiness, data quality standards adherence |
| **Influence Level** | **High** (regulatory authority) |
| **Involvement Type** | External Regulator (indirect) |
| **Communication Needs** | Data governance maturity reports, classification compliance summaries, data quality metrics |
| **Key Concerns** | Platform supports NDMO data classification standards; data governance practices align with national framework; reporting supports NDMO submission requirements |
| **Preferred Formats** | NDMO-aligned compliance reports, data classification matrices, governance maturity scorecards |

### 17. Saudi Data & Artificial Intelligence Authority (SDAIA)

| Attribute | Detail |
|-----------|--------|
| **Role** | Supervising authority for PDPL enforcement; oversees personal data protection compliance across KSA |
| **Interests** | PDPL compliance status, breach notification compliance, data subject rights fulfillment, privacy impact assessment completion |
| **Influence Level** | **High** (regulatory authority) |
| **Involvement Type** | External Regulator (indirect) |
| **Communication Needs** | PDPL compliance reports, breach notification records, privacy program maturity assessments |
| **Key Concerns** | Platform supports full PDPL compliance lifecycle; breach notification timelines are tracked; consent management is robust; cross-border transfer controls are in place |
| **Preferred Formats** | PDPL compliance scorecards, breach notification logs, privacy program reports |

---

## Stakeholder Influence-Interest Matrix

```
                        HIGH INTEREST
                            |
         MANAGE CLOSELY     |    MANAGE CLOSELY
         (Keep Satisfied)   |    (Key Players)
                            |
    - Board of Directors    |    - CEO
    - NCA                   |    - CISO
    - NDMO                  |    - CRO
    - SDAIA                 |    - DPO
                            |    - Compliance Officer
                            |    - Data Governance Officer
                            |
HIGH -------- ------------- + ----------------------- LOW
INFLUENCE                   |                     INFLUENCE
                            |
         MONITOR            |    KEEP INFORMED
                            |
    - External Auditors     |    - End Users
                            |    - Standard Users
    - IT Director           |    - Workflow Approvers
    - Internal Auditor      |
    - BCM Manager           |
    - BU Heads              |
    - System Admins         |
                            |
                        LOW INTEREST
```

---

## Stakeholder Communication Plan Summary

| Stakeholder | Frequency | Channel | Content | Language |
|-------------|-----------|---------|---------|----------|
| CEO | Quarterly + exception | Executive dashboard, board report | Enterprise risk posture, compliance status, KPIs | AR/EN |
| Board | Quarterly | Board report package (PDF export) | Risk appetite review, compliance trends, audit findings | AR/EN |
| CISO | Daily / Weekly | Platform dashboards, email alerts | Cyber risk posture, NCA compliance, incidents, vulns | AR/EN |
| CRO | Daily / Weekly | Platform dashboards, reports | Enterprise risk register, KRIs, treatments, heatmaps | AR/EN |
| DPO | Weekly / Monthly | Platform dashboards, reports | Privacy compliance, RoPA, breach status, DSR tracking | AR/EN |
| Compliance Officer | Weekly / Monthly | Platform dashboards, task lists | Framework compliance, gaps, findings, remediation | AR/EN |
| BCM Manager | Weekly / Monthly | Platform dashboards, reports | BIA status, BCP currency, exercise schedule | AR/EN |
| IT Director | Monthly + ad-hoc | Technical reports, system dashboards | System health, integration status, capacity | EN |
| Internal Auditor | Monthly / Per audit | Audit dashboards, evidence packages | Finding status, control effectiveness, audit trails | AR/EN |
| BU Heads | Monthly + task-driven | Email notifications, BU dashboards | BU risk summary, assigned tasks, compliance status | AR/EN |
| Data Governance Officer | Weekly / Monthly | Platform dashboards | Data quality, classification, stewardship status | AR/EN |
| End Users | Task-driven | In-app notifications, email | Assigned tasks, deadlines, workflow status | AR/EN |
| System Admins | Continuous | Admin console, system alerts | System health, user provisioning, config changes | EN |
| External Auditors | Per engagement | Evidence exports, compliance reports | Assessment results, evidence packages, findings | AR/EN |
| NCA | Per assessment cycle | Compliance report exports | NCA framework compliance scorecards | AR |
| NDMO | Per assessment cycle | Governance report exports | Data governance maturity, classification compliance | AR |
| SDAIA | Per assessment / breach | Compliance reports, breach logs | PDPL compliance, breach notifications | AR |

---

## RACI Matrix for Key Platform Decisions

The following matrix defines Responsible (R), Accountable (A), Consulted (C), and Informed (I) roles for critical platform decisions and activities.

### Platform Governance Decisions

| Decision / Activity | CEO | Board | CISO | CRO | DPO | Compliance Officer | BCM Mgr | IT Director | Internal Auditor | BU Heads | Data Gov Officer | Sys Admin |
|---------------------|-----|-------|------|-----|-----|--------------------|---------|-------------|------------------|----------|------------------|-----------|
| Platform procurement and funding | A | A | C | C | C | C | I | C | I | I | I | I |
| Module licensing decisions | A | I | C | C | C | C | C | C | I | I | C | I |
| Risk appetite definition | I | A | C | R | C | C | C | I | C | C | I | I |
| Enterprise risk register ownership | I | I | C | A/R | C | C | C | I | C | C | I | I |
| Cybersecurity framework selection | I | I | A/R | C | I | C | I | C | C | I | I | I |
| BCM program governance | I | I | C | C | I | C | A/R | I | C | C | I | I |
| Privacy program governance | I | I | C | C | A/R | C | I | I | C | I | I | I |
| Compliance framework mapping | I | I | C | C | C | A/R | C | I | C | I | I | I |
| Data governance framework | I | I | I | C | C | C | I | C | I | I | A/R | I |
| Platform access policy | A | I | C | C | C | C | I | R | C | I | I | C |
| User provisioning and roles | I | I | C | C | C | C | I | A | I | I | I | R |
| System configuration changes | I | I | C | I | I | I | I | A | I | I | I | R |
| Audit trail policy | I | I | C | C | C | C | I | C | A/R | I | I | C |
| Report template design | I | I | C | C | C | C | C | I | C | C | C | I |
| Workflow approval chains | I | I | R | R | R | R | R | I | C | C | R | I |
| Data retention policy | I | I | C | C | R | A | I | C | C | I | C | I |

### Module-Specific Decisions

| Decision / Activity | CISO | CRO | DPO | Compliance Officer | BCM Mgr | Data Gov Officer | Internal Auditor |
|---------------------|------|-----|-----|--------------------|---------|------------------|------------------|
| ERM risk taxonomy definition | C | A/R | I | C | C | I | C |
| Cyber risk assessment methodology | A/R | C | I | C | I | I | C |
| BIA methodology and thresholds | C | C | I | I | A/R | I | C |
| Data classification scheme | C | I | C | C | I | A/R | C |
| Privacy impact assessment criteria | C | C | A/R | C | I | I | C |
| Compliance assessment scheduling | C | C | C | A/R | C | C | C |
| Control library management | R | C | C | A | C | I | C |
| Incident escalation rules | R | C | C | C | C | I | I |
| KRI threshold definition | C | A/R | I | C | C | I | C |
| Reporting frequency and distribution | C | C | C | C | C | C | R |
| Evidence collection standards | C | C | C | R | C | C | A |
| Regulatory submission preparation | C | C | R | R | I | R | C |

### Platform Lifecycle Decisions

| Decision / Activity | CEO | CISO | CRO | IT Director | Internal Auditor | Sys Admin |
|---------------------|-----|------|-----|-------------|------------------|-----------|
| Platform upgrade approval | A | C | C | R | C | R |
| Disaster recovery testing | I | C | I | A | I | R |
| Security patching schedule | I | A | I | R | I | R |
| Backup and retention execution | I | I | I | A | C | R |
| Performance monitoring | I | I | I | A | I | R |
| Integration with external systems | A | C | C | R | I | R |
| Data migration approval | A | C | C | R | C | R |
| SLA definition and monitoring | I | C | C | A/R | I | C |

---

## Stakeholder Engagement Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Executive sponsor disengagement | Platform loses funding priority and organizational mandate | Scheduled quarterly reviews with quantified ROI metrics; exception-based escalation for critical issues |
| Domain owner resistance to process change | Low adoption within specific modules; data quality degradation | Early involvement in requirements; iterative feedback; champion identification per module |
| End user adoption failure | Incomplete data; platform becomes unreliable for decision-making | Arabic-first UX design; role-based training; simplified interfaces for standard users |
| IT team capacity constraints | Delayed deployment; integration bottlenecks | Early technical architecture review; phased deployment; clear IT support requirements |
| Regulatory requirement misinterpretation | Non-compliance despite platform use; regulatory penalties | Direct engagement with NCA/NDMO published guidance; regulatory advisory involvement; periodic alignment reviews |
| Auditor access complexity | Audit delays; reduced confidence in platform data | Dedicated auditor role with cross-module read access; pre-built evidence packages; immutable audit trail |
| Cross-functional coordination failure | Module silos; duplicated effort; inconsistent risk data | Shared kernel architecture; cross-module workflow integration; unified risk view |

---

## Stakeholder Sign-Off Requirements

| Phase | Required Sign-Offs |
|-------|-------------------|
| **Requirements Approval** | CRO, CISO, DPO, Compliance Officer, BCM Manager, Data Governance Officer, IT Director |
| **Architecture Approval** | IT Director, CISO, System Administrators |
| **UAT Sign-Off** | All domain owners (CISO, CRO, DPO, Compliance Officer, BCM Manager, Data Governance Officer) |
| **Go-Live Authorization** | CEO, CRO, CISO, IT Director |
| **Post-Implementation Review** | All stakeholders (formal survey + review meeting) |
