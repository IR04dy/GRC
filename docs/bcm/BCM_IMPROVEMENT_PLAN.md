# BCM Module — Improvement Plan

**Status:** Planning — execute over multiple sprints
**Owner:** BCM track lead
**Module:** Business Continuity Management — إدارة استمرارية الأعمال
**Short code:** `BCM`
**Platform path:** `GRC Platform → Business Continuity Management`
**Arabic path:** `منصة GRC → إدارة استمرارية الأعمال`

> **Persona for execution:** Claude Code acting as a senior Saudi GRC
> platform architect, ISO 22301 BCM specialist, Saudi regulatory
> compliance analyst, BCM consultant, enterprise workflow analyst,
> bilingual AR/EN UX designer, full-stack developer, QA automation
> engineer, and product implementation lead.

The current `module/bcm` covers the foundational BCM workflow
(critical services, BIA, BCP, DRP, exercises, crisis). This plan
defines the full operational upgrade — turning the module into the
canonical Saudi-market BCM platform that meets ISO 22301, DGA,
SAMA, and National Risk Council expectations.

**Do not claim full regulatory completeness** unless official control
libraries are uploaded into the repository. Until then, mark
catalog rows with `is_placeholder = 1` and `translation_status` per
the Framework Intersection plan, and let the UI banner stay lit:

- EN — "Placeholder — requires official regulatory control library import"
- AR — "عنصر مؤقت — يتطلب استيراد مكتبة الضوابط الرسمية"

---

## 1. Business objective

Build a full operational BCM module that lets organisations:

1. Establish BCM governance.
2. Identify critical services.
3. Identify critical processes.
4. Perform Business Impact Analysis (BIA).
5. Define RTO, RPO, MTPD, MBCO, and resource requirements.
6. Conduct BCM risk assessment.
7. Identify threats and disruption scenarios.
8. Define business continuity strategies.
9. Create and maintain Business Continuity Plans (BCPs).
10. Create and maintain IT Disaster Recovery Plans (DRPs).
11. Manage crisis and emergency activation.
12. Manage communication trees and call trees.
13. Manage exercises, tests, and simulations.
14. Track lessons learned.
15. Manage corrective actions.
16. Collect and approve BCM evidence.
17. Track regulatory and ISO 22301 readiness.
18. Generate BCM readiness reports.
19. Generate board and executive BCM dashboards.
20. Generate regulator / auditor evidence packages.
21. Integrate with ERM, Cybersecurity / NCA, Supply Chain Resilience,
    PDPL, Incident Management, Evidence Management, and Board
    Reporting.
22. Test all workflows, buttons, forms, validations, reports, and
    exports.

Configurable for future regulatory updates.

---

## 2. Module structure and navigation

Sub-routes to add under the BCM module:

| # | Page (EN) | Page (AR) |
|---|---|---|
| 1 | BCM Dashboard | لوحة استمرارية الأعمال |
| 2 | BCM Governance | حوكمة استمرارية الأعمال |
| 3 | Critical Services Register | سجل الخدمات الحرجة |
| 4 | Critical Processes Register | سجل العمليات الحرجة |
| 5 | Business Impact Analysis / BIA | تحليل الأثر على الأعمال |
| 6 | BCM Risk Assessment | تقييم مخاطر استمرارية الأعمال |
| 7 | Continuity Strategies | استراتيجيات استمرارية الأعمال |
| 8 | Business Continuity Plans / BCP | خطط استمرارية الأعمال |
| 9 | IT Disaster Recovery Plans / DRP | خطط التعافي التقني |
| 10 | Crisis & Emergency Management | إدارة الأزمات والطوارئ |
| 11 | Communication & Call Tree | الاتصالات وسلاسل التواصل |
| 12 | Exercises & Testing | التمارين والاختبارات |
| 13 | Lessons Learned | الدروس المستفادة |
| 14 | BCM Evidence Repository | مستودع أدلة استمرارية الأعمال |
| 15 | BCM Gaps & Corrective Actions | فجوات استمرارية الأعمال والإجراءات التصحيحية |
| 16 | BCM Compliance Mapping | مواءمة متطلبات استمرارية الأعمال |
| 17 | BCM Reports | تقارير استمرارية الأعمال |
| 18 | BCM Settings | إعدادات استمرارية الأعمال |
| 19 | Import Regulatory Framework | استيراد الإطار التنظيمي |
| 20 | BCM Audit Trail | سجل تدقيق استمرارية الأعمال |

Every page must load without errors, expose working buttons, and
render an empty state when no data exists.

---

## 3. Core module capabilities

### A. BCM Dashboard

Display:

- Overall BCM readiness score.
- Critical services count.
- Critical processes count.
- BIA / BCP / DRP completion %.
- Critical services without approved BIA / BCP.
- Critical applications without approved DRP.
- RTO / RPO compliance %.
- MTPD breaches.
- Exercise completion %.
- Failed exercises.
- Open BCM gaps.
- Overdue corrective actions.
- Crisis readiness score.
- Communication tree completion.
- Supplier dependency risk summary.
- Cyber disruption readiness.
- Evidence completion %.
- ISO 22301 readiness score.
- DGA / SAMA / regulatory readiness score (when configured).
- Board approval status.
- Management review status.

Widgets:

1. BCM Overall Score Card
2. Critical Services Card
3. BIA Completion Card
4. BCP Completion Card
5. DRP Completion Card
6. RTO/RPO Compliance Widget
7. Critical Services Heatmap
8. Open BCM Gaps Chart
9. Corrective Action Tracker
10. Exercise Calendar and Success Rate
11. Crisis Activation Readiness Gauge
12. Evidence Completion Widget
13. Regulatory Readiness Widget
14. Top 10 BCM Risks
15. Executive Summary Panel

Buttons:

- Create BIA · Add Critical Service · Add Critical Process
- Create BCP · Create DRP · Start BCM Risk Assessment
- Create Exercise · Activate Crisis Event · Upload Evidence
- Create Corrective Action · Generate BCM Readiness Report
- Generate Audit Evidence Package · Export Dashboard
- Switch Language · Refresh

Every button must perform a real action (or visible placeholder)
and write an audit-log row.

### B. BCM Governance

**Purpose:** manage BCM policy, scope, roles, committees,
responsibilities, governance documents, review cycles, approvals,
and management oversight.

**Fields:** governance record id; policy title; policy version;
scope; objective; BCM manager; executive sponsor; BCM committee;
approval status; review frequency; last review date; next review
date; evidence; related regulatory requirements; related ISO 22301
clauses; status.

**Workflow:**
1. Create governance record.
2. Define BCM scope.
3. Define BCM policy.
4. Assign BCM manager.
5. Assign executive sponsor.
6. Define BCM committee.
7. Define roles and responsibilities.
8. Attach policy evidence.
9. Submit for review.
10. Approve governance baseline.
11. Schedule periodic review.
12. Track exceptions and updates.

**Buttons:** Create Governance Record · Edit Scope · Assign BCM
Manager · Assign Committee · Upload Policy · Submit for Review ·
Approve · Return for Changes · Schedule Review · Export Governance
Summary.

**Statuses:** Draft · Under Review · Changes Required · Approved ·
Active · Review Due · Archived.

### C. Critical Services Register

Identify and classify critical services (digital, national,
customer-facing, operational, internal enabling).

**Fields:** service id; service name (EN/AR); description; service
owner; department; service type; criticality level; beneficiaries;
channels; dependencies; related processes; related applications;
related suppliers; related data assets; related risks; regulatory
importance; public/national/financial/operational/reputation impact;
approved criticality; status.

**Criticality levels:**
1. Sovereign / National Critical — سيادي / وطني حرج
2. Critical — حرج
3. Essential — أساسي
4. Supporting — داعم
5. Low Impact — منخفض الأثر

**Buttons:** Add Service · Edit Service · Classify Criticality ·
Link Process · Link Application · Link Supplier · Link Data Asset ·
Create BIA · Create BCM Risk Assessment · View Dependency Map ·
Approve Criticality · Export Register.

**Business rules:**
- Critical services must have service owner.
- Critical services must have BIA.
- Critical services must have BCP.
- Services with technology dependency must have DRP linkage.
- Services with supplier dependency must have supplier-dependency
  linkage.
- Criticality changes must create audit-log row.

### D. Critical Processes Register

Register business processes supporting critical services.

**Fields:** process id; name (EN/AR); process owner; department;
related service; description; inputs; outputs; dependencies;
manual workaround available (yes/no); minimum resources;
technology / supplier / data dependency; criticality; status.

**Buttons:** Add Process · Edit Process · Link Service · Link
Dependencies · Create BIA · Create Risk Assessment · Export
Register.

### E. Business Impact Analysis (BIA)

Assess disruption impact on services and processes and define
recovery requirements.

**Fields:** BIA id; service id; process id; reporting cycle; BIA
owner; service owner; process owner; department; description;
impact over time; MTPD; RTO; RPO; MBCO; minimum staff; minimum
technology; minimum facility; minimum supplier dependency; manual
workaround; peak periods; impact (regulatory / financial /
operational / reputation / legal-contractual / public-service /
national-sovereign / data / cyber / supplier); overall criticality;
evidence; reviewer; approval status; status.

**Impact time bands:** 0-2h · 2-4h · 4-8h · 8-24h · 1-3d · 3-7d ·
>7d.

**Workflow:**
1. Create BIA.
2. Select service / process.
3. Assign BIA owner.
4. Complete impact assessment.
5. Define MTPD.
6. Define RTO.
7. Define RPO.
8. Define MBCO.
9. Identify dependencies.
10. Identify minimum resources.
11. Identify manual workarounds.
12. Attach evidence.
13. Submit for review.
14. BCM manager reviews.
15. Return for changes or approve.
16. Generate BIA summary.
17. Link to BCP, DRP, BCM risk, continuity strategy.

**Buttons:** Create BIA · Save Draft · Add Impact Rating ·
Calculate Criticality · Add Dependency · Add Minimum Resource ·
Attach Evidence · Submit for Review · Return for Changes · Approve
BIA · Create BCP · Create DRP · Create Risk Assessment · Export
BIA.

**Validation:**
- RTO ≤ MTPD.
- RPO required if technology / data dependency exists.
- Critical services require approved BIA.
- Approved BIA cannot be edited unless reopened.
- Missing evidence creates an evidence gap.
- If RTO is shorter than current recovery capability → create gap.

### F. BCM Risk Assessment

Identify threats and assess risks that could disrupt services,
processes, facilities, people, technology, suppliers, and data.

**Threat categories:** technology failure · cyberattack · data
center outage · cloud outage · telecom outage · power failure ·
facility unavailability · workforce unavailability · supplier
failure · pandemic · natural hazard · fire · flood · regional
disruption · regulatory disruption · data loss · third-party
dependency · critical infrastructure disruption.

**Fields:** BCM risk id; related service / process / asset /
supplier; title (EN/AR); cause; event; consequence; threat
category; existing controls; likelihood; impact; inherent risk
score; control effectiveness; residual risk score; risk owner;
treatment plan; related BIA / BCP / DRP / ERM risk; status.

**Buttons:** Create BCM Risk · Assess Likelihood · Assess Impact ·
Calculate Risk Score · Link ERM Risk · Add Control · Create
Treatment Plan · Create Corrective Action · Attach Evidence ·
Approve Risk Assessment · Export Risk Register.

**Business rules:**
- High / critical BCM risks must link to ERM or be formally
  accepted.
- Risk treatment is required for risks above appetite.
- Supplier-related risks link to supplier register.
- Cyber-related risks link to cyber / NCA module.

### G. Continuity Strategies

Define selected strategies to maintain or recover critical services
within approved RTO / RPO.

**Strategy types:** active-active · active-passive · backup site ·
alternate workplace · remote work · manual workaround · alternate
supplier · stockpiling · cloud failover · data replication · mutual
aid · process transfer · workforce cross-training · emergency
procurement · crisis communication.

**Fields:** strategy id; related service / process / BIA; RTO /
RPO requirement; selected strategy; description; cost estimate;
feasibility; residual risk; required resources; dependencies;
owner; approval status; implementation status; evidence.

**Buttons:** Add Strategy · Compare Strategies · Select Strategy ·
Estimate Cost · Link BIA · Link BCP · Create Implementation Action
· Submit for Approval · Approve Strategy · Export Strategy.

**Business rules:**
- Strategy must meet RTO / RPO or create gap.
- High-cost strategies require executive approval.
- Critical services require approved strategy before BCP approval.

### H. Business Continuity Plans (BCP)

Operational continuity plans for critical services, processes,
departments.

**Fields:** BCP id; name (EN/AR); related service / process;
department; plan owner; activation criteria; plan scope; recovery
objectives; RTO / RPO / MTPD; response team; communication tree;
step-by-step procedures; manual workaround; minimum resources;
supplier escalation; stakeholder communication; return-to-normal;
evidence; test status; approval status; version; status.

**Sections:** plan overview · scope and objectives · roles and
responsibilities · activation criteria · recovery priorities ·
recovery procedures · communication plan · manual workaround ·
resource requirements · supplier escalation · return to normal ·
appendices and evidence.

**Workflow:**
1. Create BCP from approved BIA.
2. Define activation criteria.
3. Define response team.
4. Define recovery steps.
5. Define communication tree.
6. Define manual workaround.
7. Define supplier escalation.
8. Define return-to-normal steps.
9. Attach evidence.
10. Submit for review.
11. Approve BCP.
12. Schedule test.
13. Update after exercise or incident.

**Buttons:** Create BCP · Generate from BIA · Save Draft · Add
Recovery Step · Add Response Team · Add Communication Contact ·
Add Manual Workaround · Link Supplier · Attach Evidence · Submit
for Review · Approve BCP · Schedule Exercise · Export BCP · Publish
Plan · Archive Plan.

**Business rules:**
- BCP cannot be approved without approved BIA.
- Critical-service BCP must include activation criteria + comms tree.
- BCP must be tested at configured frequency.
- Published BCP version is locked.

### I. IT Disaster Recovery Plans (DRP)

Technical recovery plans for applications, infrastructure, cloud
services, databases, integrations, data platforms.

**Fields:** DRP id; name; related service / application /
infrastructure; application owner; IT recovery owner; RTO / RPO;
backup strategy; recovery site; recovery sequence; failover
procedure; validation checklist; rollback procedure; dependencies;
DR test status; evidence; approval status; version; status.

**Sections:** system overview · business service linkage · recovery
objectives · dependencies · backup and replication · recovery
sequence · failover · validation · rollback · contacts and
escalation · test results · evidence.

**Buttons:** Create DRP · Generate from BIA · Link Application ·
Add Recovery Step · Add Dependency · Add Backup Evidence · Add
Validation Checklist · Schedule DR Test · Record DR Test Result ·
Submit for Review · Approve DRP · Export DRP.

**Business rules:**
- DRP RTO / RPO must align with BIA.
- DRP must have test evidence before "tested" status.
- Failed DR test → corrective action.
- Critical applications must have approved DRP.

### J. Crisis & Emergency Management

Manage major disruptions, crisis activation, emergency
coordination, escalation.

**Severity levels:**
1. Operational Event — حدث تشغيلي
2. Significant Incident — حادث مؤثر
3. Major Disruption — تعطل جوهري
4. Crisis — أزمة
5. National / Sector Emergency — طارئ وطني أو قطاعي

**Fields:** crisis event id; title; severity; trigger source;
affected services / processes / sites / suppliers / systems; start
time; detection time; activation time; crisis manager; crisis
team; situation summary; decisions; actions; communications;
regulatory notifications; status; closure time; lessons learned;
evidence.

**Workflow:**
1. Log event.
2. Assess severity.
3. Compare against activation criteria.
4. Activate crisis team if threshold met.
5. Activate BCP / DRP if needed.
6. Notify stakeholders.
7. Track situation reports.
8. Track decisions and actions.
9. Track service recovery.
10. De-escalate.
11. Close event.
12. Conduct lessons learned.
13. Create corrective actions.

**Buttons:** Create Crisis Event · Assess Severity · Activate
Crisis Team · Activate BCP · Activate DRP · Add Situation Report ·
Add Decision · Add Action · Send Notification (Placeholder) ·
Escalate · De-escalate · Close Event · Create Lessons Learned ·
Export Crisis Report.

**Business rules:**
- Level 4 / 5 events require executive notification.
- Personal data affected → trigger PDPL breach placeholder.
- Cyberattack → trigger cyber incident placeholder.
- Supplier involved → trigger supplier incident placeholder.
- Closure requires root cause + lessons learned.

### K. Communication & Call Tree

Manage emergency contacts, escalation paths, stakeholders, and
templates.

**Fields:** contact id; name; role; department; primary phone;
secondary phone; email; escalation level; related plan; availability
status; last tested date; status.

**Templates:** internal alert · executive notification · customer /
service notification · regulator notification placeholder ·
supplier escalation · employee instruction · media holding
statement.

**Buttons:** Add Contact · Edit Contact · Import Contacts · Test
Call Tree · Record Test Result · Add Template · Send Test
Notification (Placeholder) · Export Call Tree.

**Business rules:**
- Critical BCPs require a call tree.
- Call tree must be tested periodically.
- Failed call-tree test → corrective action.

### L. Exercises & Testing

Plan, execute, and document BCM / DR / crisis tests.

**Types:** tabletop · call-tree test · BCP walkthrough · DR
technical test · cyber crisis simulation · supplier disruption
simulation · full interruption test · executive crisis exercise ·
evacuation drill · work-from-alternate-site test.

**Fields:** exercise id; type; scenario; related BCP / DRP /
service; objectives; participants; date; success criteria; results;
observations; gaps identified; corrective actions; evidence;
status.

**Workflow:**
1. Create exercise plan.
2. Define scenario.
3. Define objectives.
4. Define participants.
5. Define success criteria.
6. Conduct exercise.
7. Record observations.
8. Record results.
9. Identify gaps.
10. Create corrective actions.
11. Approve exercise report.
12. Update plans.

**Buttons:** Create Exercise · Add Scenario · Add Participants ·
Start Exercise · Record Observation · Record Result · Mark Passed ·
Mark Failed · Create Gap · Create Corrective Action · Attach
Evidence · Approve Exercise Report · Export Exercise Report.

**Business rules:**
- Failed exercise → corrective action.
- Critical BCP must have exercise within configured frequency.
- Exercise must have objectives + success criteria before start.

### M. Lessons Learned

**Fields:** lesson id; source type; source id; description; root
cause; recommendation; related plan / risk / control; corrective
action; owner; status.

**Buttons:** Create Lesson · Link Incident · Link Exercise · Link
Plan · Create Corrective Action · Approve Lesson · Export Lessons
Learned.

### N. BCM Evidence Repository

Manage BCM evidence linked to governance, BIA, BCP, DRP, exercises,
crisis events, controls, and compliance requirements.

**Fields:** evidence id; title (EN/AR); BCM area; related record
type / id; evidence owner; evidence type; file name; file URL;
version; valid from / to; expiry date; confidentiality level;
reviewer; approval status; verification status; digital signature
status; created / updated at.

**Types:** BCM policy · BCM scope · BIA questionnaire · BIA
approval · BCP document · DRP document · exercise report · DR test
report · call-tree test evidence · crisis report · management
review minutes · training record · supplier continuity evidence ·
corrective-action evidence · audit report · regulatory report ·
approval record · other.

**Buttons:** Upload Evidence · Link Evidence · Review Evidence ·
Approve Evidence · Reject Evidence · Request Replacement · Mark
Expired · Download · Preview · Export Evidence Package.

**Business rules:**
- Evidence must have owner + related record.
- Expired evidence triggers alert.
- Rejected evidence reopens related workflow item.
- Approved evidence updates the readiness score.

### O. BCM Gaps & Corrective Actions

**Gap fields:** id; source; related record; title; description;
severity; root cause; impact; related risk; owner; due date;
status.

**Corrective-action fields:** id; gap id; title; action plan;
owner; priority; due date; progress %; evidence; reviewer; closure
date; status.

**Statuses:** Open · Assigned · In Progress · Evidence Submitted ·
Under Review · Closed · Overdue · Reopened · Accepted Risk.

**Buttons:** Create Gap · Assign Gap · Create Corrective Action ·
Update Action · Attach Evidence · Submit for Review · Approve
Closure · Reopen · Escalate · Accept Risk · Export Gap Register.

**Business rules:**
- High-severity gaps must link to risk or formal acceptance.
- Overdue actions escalate to BCM manager.
- Closure requires evidence or approved exception.
- Accepted risk requires approval, expiry date, and justification.

### P. BCM Compliance Mapping

Map BCM activities to ISO 22301, DGA, SAMA, National Risk Council,
and internal policies.

**Fields:** requirement id; source framework; domain; requirement
code; title; description; related BCM workflow; related control;
related evidence; owner; compliance status; gap; corrective action;
status.

**Sources:** ISO 22301 placeholder · DGA BCM / Risk controls
placeholder · SAMA BCM Framework placeholder · National Risk
Council / emergency resilience placeholder · internal BCM policy ·
custom framework.

**Buttons:** Add Requirement · Import Framework · Map Requirement ·
Attach Evidence · Assess Compliance · Create Gap · Export
Compliance Matrix.

Use placeholder IDs if official IDs are not present.

### Q. BCM Reports

1. BCM Executive Dashboard Report
2. BCM Readiness Report
3. Critical Services Continuity Report
4. BIA Summary Report
5. BCP Status Report
6. DRP Readiness Report
7. Crisis Event Report
8. Exercise and Test Report
9. Evidence Completion Report
10. Gap and Corrective Action Report
11. ISO 22301 Readiness Report
12. DGA / SAMA / Regulatory Readiness Report
13. Management Review Report
14. Board BCM Report
15. Audit Evidence Package

Every report supports: Preview · Generate · Submit for review ·
Approve · Export (Markdown / HTML / JSON / CSV).

### R. BCM Settings

Configurable settings:
- BCM scoring model
- Criticality scale
- Impact scale
- Risk matrix
- RTO / RPO thresholds
- Exercise frequency
- Evidence expiry rules
- SLA rules
- Approval matrix
- Notification rules
- Language labels
- Framework version
- Regulatory import status

**Buttons:** Save Settings · Reset to Default · Import Settings ·
Export Settings · Configure Risk Matrix · Configure Impact Scale ·
Configure Approval Matrix · Configure SLA.

---

## 4. Integration requirements

### A. ERM
- BCM risks can create or link to enterprise risks.
- Critical BCM gaps can create ERM risk records.
- Risk acceptance for unresolved BCM critical gaps uses ERM
  acceptance workflow.
- Placeholder service if ERM module absent.

### B. Supply Chain Resilience
- BIA dependencies link to suppliers.
- Supplier-failure scenarios link to supplier risk assessment.
- Critical suppliers must have BCM evidence.
- Local placeholder records if SCM module absent.

### C. Cybersecurity / NCA
- Cyber disruption scenarios link to cyber risk + incident records.
- DRP links to cybersecurity controls + backup / recovery evidence.
- Cyber incident can trigger BCP / DRP activation.
- Placeholder if cyber module absent.

### D. PDPL
- Disruption / incident affecting personal data triggers PDPL
  breach placeholder.
- Data recovery + backup evidence links to privacy requirements.
- Placeholder if PDPL module absent.

### E. Incident & Crisis
- Major incidents can trigger crisis activation.
- Crisis events can activate BCP / DRP.
- Closure requires lessons learned + corrective actions.

### F. Evidence
- Reuse global evidence repository.
- Local BCM evidence repository if global one absent.

### G. Reporting
- BCM dashboard + reports appear in executive reporting module.
- Local report generator if reporting module absent.

### H. Digital Signature
- BIA / BCP / DRP / risk-acceptance / regulatory-package / board
  approvals use digital signature.
- Placeholder approval record with `digital_signature_status` if
  signature module absent.

### I. Audit
- Every important action creates an audit-log row.
- Actions: create / edit / delete / approve / reject / submit /
  export / import / score change / evidence upload / evidence
  approval / plan activation / crisis activation / report
  generation.

---

## 5. Data model

New / updated entities (full field lists in the source prompt —
copy verbatim when generating Flyway migrations):

- `BCMAssessmentCycle`
- `BCMGovernanceRecord`
- `CriticalService` *(may already exist — extend)*
- `CriticalProcess` *(may already exist — extend)*
- `BIARecord` *(may already exist — extend with cycle id +
  expanded impact dimensions)*
- `BCMRisk`
- `ContinuityStrategy`
- `BCPPlan` *(already exists — extend with version + comms tree +
  approval lifecycle)*
- `DRPPlan` *(already exists — extend with test status +
  validation checklist JSON + version)*
- `CrisisEvent` *(already exists — extend with situation reports +
  decisions JSON + regulatory notifications JSON)*
- `CommunicationContact`
- `BCMExercise` *(already exists — extend with success criteria +
  observations JSON + corrective actions)*
- `BCMLessonLearned`
- `BCMEvidence` *(prefer reusing `evidence.evidence_items` via
  `bcm_area` + `related_record_type` tagging)*
- `BCMGap`
- `BCMCorrectiveAction`
- `BCMComplianceRequirement`
- `BCMReport`
- `BCMAuditLog` *(prefer reusing the F7 `platform.audit_log` —
  emit `sourceModule='bcm'`)*

Every entity uses the platform conventions: `tenant_id`,
`organisation_id`, `external_id (UUID)`, `is_deleted`, `created_at`,
`updated_at`, optional `created_by` / `updated_by`. Bilingual fields
use `*_en` and `*_ar`.

---

## 6. API requirements

All endpoints under `/api/v1/bcm/...` (see the source prompt §6 for
the full inventory — copy verbatim). Each must validate input,
enforce permissions, scope by `tenant_id` + `organisation_id`,
create audit-log rows for critical actions, return bilingual
labels, and return clear error messages.

**Headline endpoint families** (full paths in source prompt):

- Dashboard: `GET /api/v1/bcm/dashboard[/:cycleId]`
- Cycles: list / create / start / approve / archive
- Governance: list / create / submit-review / approve / return
- Critical Services: list / create / classify / approve-criticality / create-bia
- Critical Processes: list / create / create-bia
- BIA: list / create / calculate-criticality / submit-review / approve / create-bcp / create-drp / create-risk
- BCM Risks: list / create / calculate-score / link-erm / create-treatment / approve
- Strategies: list / create / compare / approve / create-action
- BCP: list / create / generate-from-bia / submit-review / approve / publish / archive / export
- DRP: list / create / generate-from-bia / submit-review / approve / schedule-test / record-test-result / export
- Crisis: list / create / assess-severity / activate-team / activate-bcp / activate-drp / add-situation-report / close / export
- Communication: list / create / import / call-tree/test / call-tree/record-result
- Exercises: list / create / start / record-observation / record-result / create-gap / approve-report / export
- Lessons Learned: list / create / create-corrective-action / approve
- Evidence: list / create / approve / reject / request-replacement / mark-expired / package/:cycleId
- Gaps and Corrective Actions: list / create / assign / accept-risk / escalate / submit-review / approve-closure / reopen
- Compliance: list / create / import-framework / assess / attach-evidence / create-gap / export
- Reports: generate / list / submit-review / approve / export
- Settings: get / put / reset / import / export

---

## 7. Workflow rules and automation

Implement the 23 rules in the source prompt §7. All ride the F8
`AutomationRuleEngine` — add seed rows to `platform.automation_rules`
with `source = 'BCM_MODULE'`:

1. Critical / Sovereign service → BIA mandatory.
2. BIA RTO > MTPD → block approval.
3. Technology dependency → RPO mandatory.
4. Technology dependency → DRP linkage required.
5. BIA RTO < current recovery capability → create BCM gap.
6. BCP without approved BIA → block approval.
7. BCP without activation criteria → block approval.
8. Critical service BCP without comms tree → block approval.
9. DR test fails → create corrective action.
10. Exercise fails → create gap + corrective action.
11. Call-tree test fails → create corrective action.
12. High / critical BCM risk → link to ERM or require formal acceptance.
13. Critical-service supplier dependency → require supplier
    continuity evidence or create supplier gap.
14. Cyber disruption scenario → link to cyber / NCA module.
15. Crisis with personal-data impact → link to PDPL breach
    placeholder.
16. Crisis severity 4 / 5 → require executive notification.
17. Corrective action overdue → escalate to BCM manager.
18. Evidence expires → mark related item Evidence Required.
19. Report approved → lock content; new version for changes.
20. Arabic language → all screens flip to RTL and Arabic labels.
21. Imports must validate required columns and show errors clearly.
22. Exports include cycle name, reporting year, generated
    timestamp, version, generated by.
23. All approvals create audit-log + approval records.

---

## 8. UI / UX requirements

- Arabic / English toggle.
- RTL Arabic; LTR English.
- Dashboard widgets, tables with search / filter / sort /
  pagination.
- Forms with validation; wizards for BIA / BCP / DRP / crisis /
  exercises.
- Status badges, heatmaps, progress bars, charts.
- Empty / loading states, success / error notifications.
- Role-based button visibility where possible.
- Translation keys for every label — no English-only inline text.

**Pages to add (25):**

1. BCM Dashboard
2. BCM Governance
3. Critical Services
4. Critical Service Detail
5. Critical Processes
6. BIA Register
7. BIA Form / Wizard
8. BCM Risk Assessment
9. Continuity Strategies
10. BCP Register
11. BCP Builder
12. DRP Register
13. DRP Builder
14. Crisis Room
15. Crisis Event Detail
16. Communication and Call Tree
17. Exercises and Testing
18. Lessons Learned
19. Evidence Repository
20. Gaps and Corrective Actions
21. Compliance Mapping
22. Reports
23. Settings
24. Import Regulatory Framework
25. Audit Trail

---

## 9. Roles and permissions

Add (or reuse) the following roles in `iam.roles`:

1. System Administrator
2. GRC Administrator
3. BCM Manager
4. BCM Coordinator
5. Executive Sponsor
6. Service Owner
7. Process Owner
8. BIA Owner
9. BCP Owner
10. DR Owner
11. IT Recovery Owner
12. Crisis Manager
13. Crisis Team Member
14. Communication Officer
15. Supplier Owner
16. Cybersecurity Manager
17. Risk Manager
18. Evidence Owner
19. Evidence Reviewer
20. Compliance Reviewer
21. Internal Auditor
22. Executive Reviewer
23. Board Secretary
24. External Auditor Read-Only
25. Department Contributor

**Permissions per role:** View · Create · Edit · Delete · Submit ·
Review · Approve · Reject · Reopen · Activate · Close · Export ·
Import · Configure · Archive.

Express RBAC via existing `module/iam` if it supports module-level
permissions; otherwise add a role-permission seed in the BCM
migration set.

---

## 10. Seed data

| Bucket | Seed count |
|---|---|
| Assessment cycle | 1 (`BCM Readiness Cycle 2025` / `دورة جاهزية استمرارية الأعمال 2025`) |
| Critical services | 10 (national digital service · contact center · payment · data exchange · IAM · ERP · cyber monitoring · DMS · supplier portal · exec reporting) |
| Critical processes | ≥ 15 |
| BIA records | ≥ 10 |
| BCM risks | ≥ 15 |
| BCPs | ≥ 8 |
| DRPs | ≥ 6 |
| Continuity strategies | ≥ 10 |
| Crisis events | 3 (cloud outage · ransomware · critical-supplier failure) |
| Exercises | ≥ 6 |
| Evidence records | ≥ 20 |
| Gaps | ≥ 10 |
| Corrective actions | ≥ 10 |
| Compliance requirements | ISO 22301 + DGA + SAMA + NRC placeholders |
| Reports | 5 sample reports |

Bilingual entity names; placeholders flagged as such.

---

## 11. Testing requirements

### A. Unit tests
Lock the math + rule logic:

- BCM overall score calculation
- BIA / BCP / DRP completion calculation
- RTO ≤ MTPD validation
- RPO required when technology dependency exists
- Critical service requires BIA
- BCP approval blocked without BIA
- DRP readiness calculation
- Exercise result logic
- Evidence completion calculation
- Evidence expiry logic
- Corrective-action overdue logic
- Crisis severity escalation logic
- RTL / LTR language helper

### B. API tests
Service-level integration tests covering every endpoint family
listed in §6 — at minimum:

- Create cycle → governance → service → classify → BIA → approve
- BIA approval blocked when RTO > MTPD
- Generate BCP from BIA → block without activation criteria → approve
- Generate DRP → schedule test → record fail → assert
  corrective-action auto-creation (via AR-09-style rule)
- Create BCM risk → link to ERM placeholder
- Create crisis event → activate BCP + DRP → close
- Create exercise → mark failed → assert gap + action created
- Upload evidence → reject → assert related workflow reopen
- Generate report → export Markdown / HTML / JSON / CSV

### C. UI / component tests
Every page loads, every button shows feedback, AR / EN toggle
works. See source prompt §11.C for the full list.

### D. End-to-end test
The 26-step scenario from source prompt §11.D. Smoke-runs the full
BCM workflow from cycle creation through report export and
audit-log verification.

### E. Button testing
Every button: visible to authorised users, disabled / hidden
otherwise, performs expected action, shows success / error,
creates audit-log row where relevant, works in AR + EN, never
breaks RTL.

### F. Regression
Main navigation, existing dashboards, ERM / evidence / report
modules, language toggle, build, lint, type-checks.

---

## 12. Acceptance criteria

The plan is complete only when all 28 criteria from the source
prompt §12 pass. Highlights:

1. BCM module appears in navigation.
2. All BCM pages load without errors.
3. AR + EN labels exist for every screen.
4. RTL works in Arabic.
5. Every workflow (governance → criticals → BIA → risk →
   strategy → BCP → DRP → crisis → comms → exercises → lessons →
   evidence → gaps → compliance → reports) works end-to-end.
6. Business rules implemented + tested.
7. Audit logs created for critical actions.
8. Seed data included.
9. Unit / API / UI / E2E tests added.
10. All buttons functional or visible placeholders.
11. Existing modules not broken.
12. Build / lint / type-check pass.
13. Final implementation summary provided.

---

## 13. Output files to create or update

### Frontend

- BCM module route
- BCM dashboard page (already shipped — extend per §A above)
- BCM governance page
- Critical services page
- Critical processes page
- BIA register page
- BIA wizard
- BCM risk assessment page
- Continuity strategy page
- BCP register page
- BCP builder page
- DRP register page
- DRP builder page
- Crisis room page
- Communication and call-tree page
- Exercises and testing page
- Lessons learned page
- Evidence repository page
- Gaps and corrective actions page
- Compliance mapping page
- Reports page
- Settings page
- Import-framework page
- Audit-trail page
- Shared BCM components
- Translation files
- RTL layout support

### Backend

- BCM API routes
- Controllers / services
- Validation schemas
- Database models / Flyway migrations
- Seed data
- Report generator
- Evidence package generator
- Audit-log integration (F7)
- Automation rule registration (F8)
- ERM / SCM / NCA / PDPL integration placeholders

### Tests

- Unit tests
- API tests
- UI / component tests
- E2E tests
- Manual QA checklist

### Documentation

- `README_BCM_Module.md`
- `BCM_API_Documentation.md`
- `BCM_User_Workflows.md`
- `BCM_Test_Report.md`
- `BCM_Known_Limitations.md`

---

## 14. Development instructions

### Before coding

1. Inspect repository structure.
2. Identify frontend framework — **React 18 + Vite + TypeScript**.
3. Identify backend framework — **Spring Boot 3.5 / Java 17**.
4. Identify database / ORM — **SQL Server + Hibernate / Spring Data JPA**.
5. Identify routing — **React Router v6**.
6. Identify state management — **local + minimal React Query**.
7. Identify i18n — `useI18n()` + `translations/{en,ar}.ts`.
8. Identify existing evidence / risk / report / audit-log modules
   — reuse, don't reinvent (`module/evidence`, `module/risk`,
   `module/reporting`, F7 audit log, F8 automation engine).
9. Identify testing framework — **Mockito (`MockitoExtension`),
   Vitest where helpful, `tsc --noEmit`**.
10. Reuse existing components and style patterns (Shell, PageHeader,
    Tabs, Card, Kpi, Badge, DataTable, FormModal, ConfirmDialog).

### During coding

1. Implement incrementally — one capability per PR.
2. Keep module modular — controllers thin, services hold logic.
3. Use the existing design system; don't add new primitives.
4. Add TypeScript types / interfaces.
5. Add validation (Jakarta validation backend, HTML5 + custom front).
6. Add error handling (RFC 7807 backend, `ApiError` frontend).
7. Add loading + empty states.
8. Add success / error toasts.
9. Audit every important action through F7.
10. Add seed data.
11. Add tests with each implemented area.

### After coding

1. Run `mvn verify` (Mockito strict mode).
2. Run `npx tsc --noEmit`.
3. Run unit / API / UI / E2E suites.
4. Run lint.
5. Run build.
6. Fix all errors.
7. Provide implementation summary.
8. Provide test report.
9. Provide changed files list.
10. Provide known limitations + next steps.

---

## 15. Suggested execution order (sprints)

Given the size, the work is split into 6 sprints:

| Sprint | Theme | Deliverables |
|---|---|---|
| **BCM-1** | Foundations | New Flyway migrations for `BCMAssessmentCycle`, expanded `BIARecord`, `BCMGovernanceRecord`, `BCMRisk`, `ContinuityStrategy`, `BCMComplianceRequirement`. Backend services + REST + Mockito tests. |
| **BCM-2** | Plans + crisis | Extend `BCPPlan` / `DRPPlan` / `CrisisEvent`. Generate-from-BIA path. Crisis activation. Call tree. Comms templates. |
| **BCM-3** | Exercises + lessons + CAPA | Extend `BCMExercise`. Add `BCMLessonLearned`, `BCMGap`, `BCMCorrectiveAction`. Wire all relevant F8 automation rules (DR-test-fail, exercise-fail, call-tree-fail, CAPA-overdue). |
| **BCM-4** | Frontend pages 1-12 | Dashboard upgrade, Governance, Services, Processes, BIA wizard, BCM Risk, Continuity Strategies, BCP Register/Builder, DRP Register/Builder, Crisis Room. |
| **BCM-5** | Frontend pages 13-25 | Comms tree, Exercises, Lessons, Evidence, Gaps, Compliance Mapping, Reports, Settings, Import-framework, Audit Trail. RTL hardening. |
| **BCM-6** | Reports + regulatory + QA | 15-report generator. ISO 22301 / DGA / SAMA / NRC placeholder import templates. Full E2E test suite. Final QA checklist at `docs/bcm/QA_CHECKLIST.md`. Module-licensing entitlement code `BCM` already exists in F9. |

### Pre-requisites already in place

- ✅ F1 — Authority Documents catalog (ISO 22301 / SAMA BCM / DGA /
  NRC placeholders seeded).
- ✅ F2 — Unified Control Library (BCM controls can already map to
  ISO 22301 citations).
- ✅ F5 — Regulatory Alignment tab mounted on `/bcm`.
- ✅ F6 — Compliance Posture Hub shows BCM coverage.
- ✅ F7 — Generic audit log usable for every BCM action.
- ✅ F8 — Automation Rules engine — register BCM rules with
  `source = 'BCM_MODULE'`.
- ✅ F9 — Module licensing — `BCM` module code seeded.
- ✅ #36 — BCM Dashboard page already shipped (extend per §A above).

---

## 16. Final response expected after execution

When each BCM sprint lands, deliver:

1. Implementation summary
2. Files created / modified
3. Features implemented
4. Workflows implemented
5. Buttons implemented and tested
6. API endpoints added
7. Data models added
8. Seed data added
9. Integration points added
10. Tests added
11. Test results
12. Build / lint / type-check results
13. Known limitations
14. Manual QA checklist
15. Next recommended improvements

Do not provide only a conceptual answer. Inspect the repository,
implement the BCM sprint, test it, and report the results.

If official ISO 22301, DGA, SAMA, NRC, or other regulatory
framework content is not present in the repository, implement
configurable placeholder items and clearly label them as
placeholders requiring official framework / control import.

---

**Cross-references:**

- `docs/grc/framework-intersection-plan.md`
- `docs/Saudi-GRC-Platform-Integration/02-Module-Architecture/02_Module_Architecture.md`
- `docs/Saudi-GRC-Platform-Integration/03-Workflow-Catalogue/03_Workflow_Catalogue.md`
- `docs/Saudi-GRC-Platform-Integration/11-Implementation-Backlog/11_Implementation_Backlog.md`
- `tss-grc-backend/src/main/java/com/tss/grc/module/bcm/`
- `tss-grc-ui-v2/src/pages/bcm/BcmPage.tsx`
- `tss-grc-ui-v2/src/pages/dashboards/BcmDashboardPage.tsx`

---

*Last updated: alongside the F7-F10 + #36 closeout. Execute via the
6-sprint plan above; each sprint is independently shippable.*
