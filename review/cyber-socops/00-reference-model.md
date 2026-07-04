# P0 — Expert Reference Model
### What a Saudi-market Cyber-GRC + SOC Operations platform MUST do, end-to-end

> Written **before** inspecting the app. This is the benchmark the coverage matrix (P1) and the
> violent test waves (P2–P7) grade against. Sources: NCA ECC-2:2024 + CSCC + CSCC-critical-systems,
> SAMA CSF v1.0, ISO/IEC 27001:2022 (Annex A) + 27002 + 27035 (incident) + 27005 (risk),
> NIST CSF 2.0 + SP 800-61r3 (IR) + SP 800-40 (patch/vuln), PDPL + its Implementing Regs, and the
> observed behaviour of ServiceNow SecOps/IRM, RSA Archer, MetricStream, OneTrust, AuditBoard.

**Legend for every process:** Actors/RACI · Trigger · Steps · States · Inputs/Outputs · Cross-dept ·
SLA/Countdown/Reminder/Escalation (the "clock") · Attachments · Approvals · Notifications · Audit
receipts · Reports. A process is only **REAL** when the *clock, attachments, approvals, cross-dept
routing, notifications and audit* all exist — not just a CRUD screen.

---

## The non-negotiable "operating-system" primitives
These cut across every capability. If they are missing, individual screens are **islands**:

| # | Primitive | What "real" means | Why it matters (Saudi market) |
|---|---|---|---|
| OS-1 | **Task/assignment engine** | Any obligation can be *assigned to a person or department* with an owner, watchers, and a status lifecycle (Open→In-Progress→Submitted→Done). | ECC governance requires demonstrable ownership; auditors ask "who owns this control?" |
| OS-2 | **The clock** | Due date + **live countdown**, periodic **reminders** (e.g. T-7/T-3/T-1), **breach** state, and **escalation** to the line manager/CISO on breach. | SAMA/NCA measure remediation SLAs; a due date with no reminder/escalation is decorative. |
| OS-3 | **Attachments / evidence upload** | Every artifact-bearing record (evidence, policy, assessment answer, incident, exception, finding) accepts **file upload** (drag-drop, versioned, virus-scan hook), with metadata + retention. | Evidence *is* the deliverable in an audit; a form with no upload cannot collect evidence. |
| OS-4 | **Cross-department request workflow** | I can *request* something (evidence, remediation, approval) **from another department**; it lands in their queue with the clock running; they fulfil → I review → accept/reject → close. | GRC is inherently cross-functional; the CISO office requests evidence from IT/HR/Legal daily. |
| OS-5 | **Approval / maker-checker** | State transitions that matter (policy publish, risk acceptance, exception grant, incident closure) require an **approver** distinct from the maker, with decision + comment captured. | ECC + SAMA mandate segregation of duties and documented approvals. |
| OS-6 | **Notifications** | In-app + email (+ optionally SMS) on assignment, reminder, breach, approval-request, decision, closure. | Without notifications the clock is invisible; work stalls silently. |
| OS-7 | **Immutable audit trail** | Every create/update/state-change/approval/decision writes a who-what-when-before-after record, queryable and exportable. | ECC-1-3, SAMA 3.1, ISO A.8.15 logging; the audit register is itself audited. |
| OS-8 | **Bilingual, Saudi-formal** | Every user-visible label + record carries EN + AR (titleEn/titleAr), correct RTL, formal SOCPA/CMA/NCA terminology. | Regulator submissions are in Arabic; mistranslation is a compliance risk. |
| OS-9 | **Tenant isolation + RBAC** | Every read/write is org-scoped; least-privilege roles (CISO, SOC analyst, auditor, control owner, DPO). No IDOR. | Multi-tenant SaaS for banks; cross-tenant leakage is a Critical breach. |
| OS-10 | **Reporting / KPI / board pack** | Live dashboards + scheduled/exportable reports (Excel + Arabic RTL PDF) + a board/committee pack. | Boards and regulators consume PDFs, not screens. |

---

## Capability A — Governance & Framework Controls

### A1. Framework control library & applicability
- **Actors:** CISO (A), GRC officer (R), control owners (C), auditor (I).
- **Trigger:** onboarding a framework (ECC-2:2024, SAMA CSF, ISO 27001 Annex A, PDPL).
- **Steps:** import framework → domains/subdomains/controls → mark applicability (applicable / N/A + justification) → assign each control an **owner** (dept) → set assessment frequency.
- **States:** Draft → Active → Retired (per framework version).
- **Cross-dept:** each control owner is typically a different department (IT, HR, Legal, Facilities).
- **Clock:** per-control review cadence (annual/quarterly) with reminder before due.
- **Attachments:** control design docs, SoA (Statement of Applicability).
- **Audit/Reports:** SoA export; applicability decisions logged; coverage %.

### A2. Control assessment → gap → CAPA (the core GRC loop)
- **Actors:** assessor (R), control owner (C/R for remediation), CISO (A), auditor (I).
- **Trigger:** scheduled assessment cycle or ad-hoc.
- **Steps:** open assessment → for each control: score maturity/implementation status + **attach evidence** → auto-derive **gaps** for non-compliant controls → for each gap raise a **CAPA** (corrective action) → assign to owner dept with **due date** → track to closure → re-test → close.
- **States (assessment):** Planned → In-Progress → Under-Review → Completed.
- **States (gap):** Open → In-Remediation → Remediated → Verified → Closed (/ Risk-Accepted).
- **States (CAPA):** Open → In-Progress → Done → Verified → Closed; Overdue is a derived breach state.
- **Clock:** CAPA due date + countdown + T-minus reminders + **escalation to CISO on overdue**.
- **Approvals:** risk-acceptance path if a gap won't be remediated (requires CISO/risk-owner sign-off).
- **Notifications:** assignment, reminder, overdue-escalation, verification request, closure.
- **Audit/Reports:** assessment report, gap register, CAPA ageing, maturity heatmap by domain.
- **Market bar:** ServiceNow/Archer auto-generate CAPA from failed controls and route them; a platform that only *lists* gaps without a routed, clocked CAPA is **PARTIAL**.

### A3. Policy lifecycle management
- **Actors:** policy author (R), reviewers (C), approver/CISO or board (A), all-staff (I for attestation).
- **Trigger:** new policy need, scheduled review, or regulatory change.
- **Steps:** draft (with **document upload** + version) → review cycle (route to reviewers, collect comments) → **approval** (maker-checker / committee) → publish → **attestation campaign** (all-staff acknowledge) → scheduled review/renewal → supersede/retire.
- **States:** Draft → In-Review → Approved → Published → Under-Revision → Retired.
- **Clock:** review-due date + reminder; attestation campaign due date + reminder + escalation for non-attesters.
- **Attachments:** the policy document itself (PDF/DOCX), versioned; mapping to controls/obligations.
- **Approvals:** required before Publish; captured with approver + date + comment.
- **Notifications:** review request, approval request, publish announcement, attestation reminders.
- **Audit/Reports:** policy register, version history, attestation completion %, overdue-review list.
- **Market bar:** OneTrust/AuditBoard treat the *document* as first-class (upload, version, e-sign). A policy record with **no file upload** is a **FAÇADE** for policy management.

### A4. Evidence lifecycle (request → collect → review → approve → retain)  ← SEED S1/S2
- **Actors:** requester = GRC/CISO office (R), fulfiller = owning dept (R), reviewer (A), auditor (I).
- **Trigger:** control assessment, audit, regulator RFI, or periodic evidence refresh.
- **Steps:**
  1. **Request evidence** — create a request specifying what, **which department/owner**, and a **due date**; it enters the fulfiller's queue.
  2. **Clock starts** — countdown to due; **periodic reminders** (T-7/T-3/T-1/overdue); **escalation** to fulfiller's manager + requester on breach.
  3. **Fulfil** — fulfiller **uploads the file(s)** (drag-drop, multi-file, versioned) + notes.
  4. **Review** — reviewer accepts or **rejects with reason** (→ re-request/resubmit loop).
  5. **Approve & link** — accepted evidence is linked to the control/gap/obligation it satisfies.
  6. **Retain & expire** — retention period; **expiry** flips evidence to Stale and re-opens a refresh request.
- **States:** Requested → In-Progress → Submitted → Under-Review → Approved (/ Rejected → Resubmit) → Expired.
- **Attachments:** **mandatory file upload** — this is the entire point.
- **Approvals:** reviewer accept/reject with comment.
- **Notifications:** request, reminders, breach-escalation, submission, decision, expiry.
- **Audit/Reports:** evidence register, ageing, completeness by control, expired-evidence list.
- **THIS IS THE USER'S SEED FINDING.** If create-evidence has no upload (S1) and "request" opens no
  clocked, cross-dept, reminder-driven workflow (S2), evidence management is a **FAÇADE**.

---

## Capability B — Asset, Attack Surface & Vulnerability

### B1. Asset inventory & classification
- Actors: IT asset owner (R), CISO (A). Trigger: onboarding/discovery. Steps: register asset →
  classify (criticality, data classification per NDMO/PDPL) → owner → link to controls/services.
  States: Active → Decommissioned. Clock: recertification cadence. Reports: asset register, crown-jewels.

### B2. Attack Surface Management (ASM) & Shadow-IT
- Actors: SOC analyst (R), asset owner (C). Trigger: continuous external discovery / import.
- Steps: discover internet-facing assets → compute risk score → raise **findings** → triage → route
  to owner for remediation → track. Shadow-IT: discovered unsanctioned asset → **review** → sanction/
  decommission decision → **approval**. States (finding): New→Triaged→In-Remediation→Resolved. States
  (shadow-IT): Pending-Review→Approved/Rejected→Decommissioned. Clock: finding remediation SLA.

### B3. Vulnerability management & SLA engine
- **Actors:** scanner (ingest), SOC/vuln analyst (R), asset owner (remediation R), CISO (A, exceptions).
- **Trigger:** scanner ingest (Nessus/Qualys/etc.) or manual registration.
- **Steps:** ingest/register vuln (CVE, CVSS, asset, exposure) → **compute due date via SLA policy**
  (severity × exposure × asset-criticality) → assign to asset owner → remediate → verify → close.
  Alternatively raise a **risk-acceptance exception** (time-boxed, approved).
- **States (vuln):** Registered → Triaged → In-Remediation → Pending-Verification → Closed (terminal:
  Remediated / Accepted-Risk / False-Positive).
- **States (SLA):** OPEN → WARNING (approaching due) → BREACHED; terminal on vuln close.
- **The clock is the whole feature:** SLA due computed from a policy, countdown, WARNING at threshold,
  BREACHED + **escalation**, reminders. A vuln list with a static "due date" and no engine is **PARTIAL**.
- **Exceptions:** REQUESTED → APPROVED/REJECTED, time-boxed, auto-expire re-opens the vuln.
- **Attachments:** scan reports, remediation proof. **Approvals:** exception grant. **Reports:** SLA
  compliance %, ageing by severity, top-exposed assets, exception register.

### B4. Threat intelligence
- Actors: threat analyst (R). Trigger: feed ingest / manual IOC. Steps: ingest IOC/advisory → assess
  relevance → link to assets/vulns → **action** (block/hunt/notify). Reports: IOC register, advisories.

---

## Capability C — Incident Response (E2E)

### C1. Cyber incident lifecycle (ISO 27035 / NIST 800-61r3)
- **Actors:** detector/reporter (R), IR lead (A/R), SOC analysts (R), affected business owner (C),
  CISO (A), legal/DPO (C — breach notification), regulator (I — NCA/SAMA reporting).
- **Trigger:** SOC alert, user report, or automated detection escalation.
- **Steps (canonical 9-step):** Detection → Triage/Analysis → Containment → Eradication → Recovery →
  Post-Incident (Lessons Learned) — with sub-steps for categorization, prioritization, evidence
  collection, communication, and **regulatory notification** (NCA 4h/72h, SAMA, PDPL 72h breach).
- **States:** DETECTION → TRIAGE → CONTAINMENT → ERADICATION → RECOVERY → POST_INCIDENT →
  LESSONS_LEARNED → CLOSED (with rejection of illegal skips).
- **Clock:** SLA per severity (acknowledge, contain, resolve); **regulatory notification timers**
  (NCA/SAMA/PDPL) with hard countdowns + escalation — *this is a Saudi-specific must*.
- **Cross-dept:** tasks to IT, business, legal, comms, HR; **evidence collection** with upload.
- **Attachments:** logs, screenshots, forensic artifacts, notification letters.
- **Approvals:** closure sign-off; regulatory-notification approval (legal/CISO).
- **Notifications:** assignment, SLA breach, escalation, regulator-timer alerts, closure.
- **Audit/Reports:** incident timeline, MTTR/MTTD, incident report (Arabic PDF for regulator),
  lessons-learned → CAPA. **Auto-CAPA** from lessons learned is the market bar.

### C2. IR playbooks
- Actors: IR lead (R). Steps: maintain playbook templates → on incident **clone/invoke** a playbook →
  its steps become assigned, clocked tasks. States: template → invoked-instance. This is what turns a
  static runbook into executed work; invoke-with-no-tasks is a **FAÇADE**.

### C3. SOC monitoring & security-event triage
- Actors: SOC L1/L2 (R), SIEM (ingest). Trigger: SIEM event ingest. Steps: ingest event → triage
  (true/false positive) → correlate → **promote to incident** or resolve → feedback to detection.
  States: New → Triaged → Escalated(→incident) / Resolved(FP). Clock: triage SLA. The SIEM→event→
  incident promotion path is the SOC's spine.

---

## Capability D — SOC Operations (the "SecOps factory")

### D1. Machine ingest pipelines
- SIEM events, scanner results, DLP alerts, AppSec (SAST/DAST) findings, SBOM components, IAM
  joiner/mover/leaver (JML) events, brand-monitoring hits, threat feeds. Each needs a **dedicated
  ingest identity/role** (service account, least-privilege, write-only to its aggregate), idempotent
  ingest, and dedup. Reports: ingest volume, source health, last-ingest timestamp per source.

### D2. Forensics & chain-of-custody
- Actors: forensic analyst (R), evidence custodian (A), legal (C). Steps: open case → acquire evidence
  item → **record chain-of-custody** (each transfer: who→who, when, hash) → analyze → **attach
  artifacts** → report. States: Open → Acquisition → Analysis → Reporting → Closed. The chain-of-
  custody ledger must be immutable + exportable (court/regulator grade).

### D3. Brand protection & takedown
- Actors: SOC analyst (R), legal (C). Steps: detect impersonation/phishing domain → open case →
  initiate **takedown** request (to registrar/host) → track status → close. Clock: takedown SLA +
  reminders. States: Detected → Takedown-Requested → In-Progress → Taken-Down / Failed.

### D4. Governance-adjacent SOC reviews (scheduled)
- **Insurance renewal** (cyber-insurance policy expiry 90/30-day reminders), **pentest cadence**
  (annual/overdue tracking), **privileged-access review** (periodic recertification), **certificate/
  secret expiry**, **M&A cyber due-diligence** (transition workflow). Each is a **clocked, scheduled-
  sweep-driven** obligation with reminders + escalation. This is where a **scheduler** must be
  *proven running* — a dead scheduler makes all of these silent façades.

### D5. Awareness & exercises
- Phishing simulations, tabletop exercises, training campaigns → results → gaps → CAPA. Clock:
  campaign windows, completion tracking, escalation for non-completers.

---

## Capability E — Cross-cutting (grades every screen)

### E1. Dashboards & KPIs
- Live, org-scoped, drill-down: control coverage %, gap/CAPA ageing, vuln SLA compliance, incident
  MTTR/MTTD, evidence completeness, top risks. Must reflect **real data**, not hardcoded demo numbers.

### E2. Reporting & exports
- Excel (POI) + **Arabic RTL PDF** (OpenPDF) for every register; scheduled report delivery; a
  **board/committee pack** aggregating the module's posture.

### E3. i18n / RTL / Saudi-formal terminology
- Every label + record bilingual (titleEn + titleAr both populated); correct RTL; formal
  NCA/SAMA/SOCPA/CMA Arabic (e.g. "الأهمية النسبية", "الثغرات", "الحوادث السيبرانية").

### E4. RBAC & least privilege
- Distinct roles: PLATFORM_ADMIN, CISO, SOC_ANALYST, VULN_ANALYST, CONTROL_OWNER, AUDITOR, DPO,
  plus machine-ingest service roles. A module gated **admin-only** (everyone else 403) fails least-
  privilege and is a **finding**.

### E5. Tenant isolation
- Every query org-scoped; **IDOR probes** (fetch/patch another org's id) must 404/403, never leak.

### E6. Integration bridges (no islands)
- cyber incident → risk register; cyber gap → CAPA (compliance); cyber/vuln evidence → evidence
  module; incident → BCM (if service-impacting); SOC events consumed via the F8 event bus (published
  **and consumed**, not fire-and-forget). Bridges are what stop the modules from being islands — the
  user's core complaint.

---

## Grading rubric (used by P1–P8)
- **REAL** — full journey works: clock + attachments + cross-dept routing + approvals + notifications
  + audit all present and proven with receipts.
- **PARTIAL** — core CRUD + some workflow, but missing ≥1 primitive (e.g. due date but no reminder/
  escalation; state machine but no evidence upload; assessment but gaps don't route to CAPA).
- **FAÇADE** — screen/endpoint exists and returns data, but no working process behind it (no upload
  where evidence is implied; "request" button that only sets a flag; invoke that spawns no tasks;
  dashboard on hardcoded numbers).
- **MISSING** — the reference process has no screen/endpoint at all.

## The 25 processes P1 must grade
A1, A2, A3, **A4(seed)**, B1, B2, B3, B4, C1, C2, C3, D1, D2, D3, D4(insurance), D4(pentest),
D4(privileged-access), D4(M&A-DD), D5, E1, E2, E3, E4, E5, E6.
