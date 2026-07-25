# Product Design Specification

## PDS governance

This catalogue defines commercial products, not Java folders. Each product must later own a versioned, testable PDS with field-level and API contracts. Baseline maturity is a risk-directed assessment, not certification.

## Shared product contract

Every package supplies:

- named buyer, users and measurable outcomes;
- included/excluded capabilities;
- canonical objects and state machines;
- permissions and SoD rules;
- integrations and published events;
- Arabic/English content;
- dashboards with lineage;
- migration and retention policy;
- installation, entitlement, upgrade and disable tests;
- UAT and operational acceptance evidence.

## PDS-01 — Enterprise Risk Management

**Users:** CRO, risk manager, risk owner, executive committee.  
**Outcome:** identify uncertainty, measure exposure, remain within appetite and close treatment.

**Scope:** taxonomy, appetite, register, inherent/residual assessment, KRI, treatment, acceptance, scenarios, emerging risk, bow-tie and optional FAIR.

**Canonical objects:** risk, risk taxonomy, assessment, appetite statement, KRI, breach, treatment, acceptance and scenario.

**Primary lifecycle:** `Identified → Assessed → Treatment/Acceptance → Monitored → Closed`.

**UX:** one risk workspace; methodology tools are progressive panels, not separate product islands.

**Acceptance:** one authoritative current rating; authenticated acceptance authority; appetite breach events; control-effectiveness linkage; tenant and export isolation.

**Baseline:** functionally strong; fragmented experience and direct platform/peer dependencies require remediation.

## PDS-02 — Compliance and Common Controls

**Users:** Chief Compliance Officer, compliance specialist, control owner.  
**Outcome:** convert obligations into owned, tested and evidenced controls.

**Scope:** regulatory library, obligations, common controls, crosswalk, assessment, attestation, findings and CAPA.

**Canonical objects:** authority, instrument version, obligation, control, implementation, test, deficiency and issue.

**Design requirement:** framework content is versioned; common controls prevent duplicate testing while showing exact scope/freshness.

**Acceptance:** obligation changes trigger impact workflow; posture denominator is explicit; stale evidence cannot silently sustain effectiveness.

## PDS-03 — Policy Management

**Users:** policy owner, legal/compliance reviewer, employees.  
**Outcome:** create, approve, publish, attest and retire authoritative policies.

**Scope:** templates, drafting, review, approval, versioning, publication, distribution, attestation and review cycle.

**Canonical objects:** policy, version, clause, review, approval, publication and attestation.

**Acceptance:** immutable published versions; authenticated approval; Arabic/English completeness; effective-date handling; acknowledgement evidence.

## PDS-04 — Internal Audit

**Users:** CAE, auditor, Audit Committee, management action owner.  
**Outcome:** plan and execute independent risk-based assurance with defensible findings.

**Scope:** universe, annual plan, resources, engagements, procedures, working papers, findings, responses, CAPA follow-up and committee reporting.

**Independence:** audit owns findings and assurance conclusions; management owns responses/actions; no user can impersonate committee approval.

**Acceptance:** signed working-paper review, finding disagreement preserved, authenticated committee plan approval and complete lineage.

## PDS-05 — Change Enablement

**Users:** change requester, change manager, CAB/ECAB, implementer, reviewer.  
**Outcome:** make safe changes at appropriate speed with authoritative approval and post-implementation learning.

**Scope:** normal/standard/emergency changes, risk, collisions, freeze windows, impact, CAB agenda, conditions, implementation, rollback and PIR.

**Canonical objects:** change, affected service/asset, risk assessment, approval, CAB decision, implementation task and PIR.

**Critical baseline:** not market-ready. Request-body approver names and `WRITE`-guarded approval transitions invalidate decision assurance.

**Acceptance:** authenticated voter ID, membership/role eligibility, one vote per user, standard-model controls, ECAB ratification and independent PIR.

## PDS-06 — Governance, Committees and Board

**Users:** board/committee chair, secretary, member and action owner.  
**Outcome:** run valid meetings and retain authoritative decisions.

**Scope:** charters, members/terms, skills, calendar, agenda, papers, attendance, COI, quorum, votes, resolutions, actions, signatures and board packs.

**Critical baseline:** quorum and votes are not trustworthy while callers can author attendee/voter names and denominator records.

**Acceptance:** membership-derived eligibility, authenticated votes, preserved abstention/dissent, signed minutes, action traceability and records retention.

## PDS-07 — Cyber GRC

**Users:** CISO, cyber GRC, control owners, vulnerability managers.  
**Outcome:** manage NCA/SAMA-aligned cyber obligations and show control effectiveness against exposure.

**Scope:** frameworks, controls, assessments, vulnerabilities/SLA, attack surface, assets/findings, cloud, threat context and corrective action.

**Boundary:** Cyber GRC owns governance and exposure records; SOC operational event volume may be separately purchased.

**Acceptance:** NCA content version, evidence freshness, risk/control connection, scanner contract and cyber-to-PDPL breach handoff.

## PDS-08 — Security Operations Governance

**Users:** SOC manager, analyst, incident commander, cyber assurance.  
**Outcome:** govern detection, response and specialist cyber operations.

**Scope:** events, cases, shifts, playbooks, threat intelligence, scanner runs, SBOM, OT, DLP, forensics, awareness, AppSec, crypto and zero-trust maturity.

**Commercial note:** current entitlement enforcement is more mature here than elsewhere.

**Acceptance:** ingestion authentication/idempotency, chain of custody, high-volume performance, retention tiers and safe optional integration with Cyber GRC.

## PDS-09 — BCM, Crisis and Disaster Recovery

**Users:** BCM manager, process owner, crisis leader, IT recovery manager.  
**Outcome:** understand impact, select recovery strategy and prove recoverability.

**Scope:** programme, processes/dependencies, BIA, MTPD/RTO/RPO, strategy, BCP/DRP, exercises, crisis room, communications, AAR and management review.

**Acceptance:** BIA methodology version; service dependency graph; plan activation authority; offline/mobile crisis usability; exercise-to-improvement evidence.

## PDS-10 — Privacy and Data Protection

**Users:** DPO, privacy team, data/process owner, data subject.  
**Outcome:** demonstrate lawful processing and fulfil PDPL obligations.

**Scope:** RoPA, data flows, notices/consents, DPIA, DSR, breach, retention, transfer, subprocessors, processor assessment and public portal.

**Canonical objects:** processing activity, purpose/legal basis, data category, subject category, recipient, processor, transfer, risk, request and breach.

**Acceptance:** statutory clocks, identity verification, legal hold, deletion across copies, transfer safeguards, processor lineage and restricted sensitive access.

## PDS-11 — Data Governance and NDI

**Users:** Chief Data Officer, data steward, data owner, NDI programme manager.  
**Outcome:** govern data assets, classification, quality and Saudi NDI readiness.

**Scope:** catalog/classification, flows, owners, quality, ethics, NDI cycles, framework, maturity, evidence, gaps, improvement and submission.

**Baseline:** broad implementation with a 15-tab NDI page and weak automated-test concentration; `datagov` is effectively a stub.

**Redesign:** split NDI cycle, evidence, gap and submission workspaces; make official framework provenance explicit.

## PDS-12 — Third-Party Risk and Supply Chain

**Users:** procurement, vendor-risk manager, security/privacy/BCM reviewers, vendor.  
**Outcome:** select, monitor and exit third parties proportionately to criticality.

**Scope:** party inventory, onboarding, inherent risk, dynamic assessment, due diligence, attestations, monitoring, fourth parties, resilience and offboarding.

**Acceptance:** one party master, risk-tier-driven questionnaire, external portal, continuous signals, concentration analysis and evidence-backed acceptance.

## PDS-13 — SAMA Outsourcing and Operational Resilience

**Users:** outsourcing function, risk, compliance, BCM and SAMA liaison.  
**Outcome:** govern material outsourcing/cloud and remain within impact tolerance.

**Scope:** materiality, approval/notification, arrangement register, cloud service, ongoing review, exit plan, resilience assessment, recovery/resolution and filings.

**Acceptance:** regulatory content version; Saudi location/approval fields; critical-service mapping; exit rehearsal; authenticated approval and immutable submission pack.

## PDS-14 — ESG and Sustainable Finance

**Users:** sustainability officer, finance, risk, assurance provider, board.  
**Outcome:** create traceable sustainability and climate disclosures.

**Scope:** metrics, Scope 1–3 GHG, financed emissions, climate risks/scenarios, IFRS S1/S2, CMA/TCFD, green finance/UoP, portfolio/GAR, credits and assurance lineage.

**Acceptance:** calculation version, units and conversion factors; source lineage; restatement; maker-checker; assurance scope; Arabic disclosure output.

## PDS-15 — Access Governance

**Users:** IAM governance, managers, application owners, security.  
**Outcome:** ensure appropriate access through lifecycle, SoD and recertification.

**Scope:** access request, maker-checker approval, SoD rules/violations, certification, joiner-mover-leaver and break-glass.

**Acceptance:** server-enforced SoD, effective entitlement reconciliation, independent break-glass review, off-request tenant safety and connector evidence.

## PDS-16 — Ethics, Speak-up, Complaints and Fraud

**Users:** ethics/compliance, investigator, ombudsman, fraud team, anonymous reporter.  
**Outcome:** receive sensitive reports safely and investigate without retaliation or disclosure.

**Scope:** whistleblowing, anonymous communication, COI, retaliation, complaint intake/tracking and fraud cases.

**Acceptance:** case-level need-to-know, identity separation, safe public tokens, chain of custody, legal hold, conflict reassignment and protected analytics.

## PDS-17 — Regulatory Affairs

**Users:** regulatory liaison, compliance, legal, business owners.  
**Outcome:** understand change, manage inspections and submit accurate filings.

**Scope:** horizon, impact, inspection workspace, obligations, filing calendar, review, signature and submission record.

**Acceptance:** source provenance, applicability decision, regulatory clock, submission version/hash and correspondence history.

## PDS-18 — Saudi Financial Services

**Users:** financial-crime, Sharia, open-banking, counterparty and recovery-planning teams.  
**Outcome:** activate specialist Saudi financial workflows without buying the entire suite.

**Selectable sub-packs:** AML/sanctions, Sharia governance, open banking, counterparty risk, operational resilience and recovery/resolution planning.

**Acceptance:** each sub-pack has independent entitlement and regulatory SME verification; screening source licences and model limitations are visible.

## PDS-19 — AI and Model Governance

**Users:** AI owner, validator, risk/compliance, data scientist and oversight committee.  
**Outcome:** inventory, validate, approve and monitor AI responsibly.

**Scope:** model register, use case, risk tier, validation, fairness/privacy/security review, deployment approval, drift, retraining, recertification and retirement.

**Acceptance:** model/prompt version, dataset lineage, evaluation, human oversight, incident handling and package-specific AI permissions.

## PDS-20 — Platform Kernel

**Users:** tenant admin, platform operator, security admin and integration admin.  
**Outcome:** provide safe shared capabilities to every package.

**Scope:** identity, tenant, RBAC, evidence, workflow, approvals, audit, notifications, reporting, localization, configuration, licensing, integrations, help and operations.

**Acceptance:** boots without commercial packages, universal entitlement enforcement, no domain dependency, tested backup/restore, tenant isolation and versioned contracts.

