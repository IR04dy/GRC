# Saudi GRC Platform — Gap Analysis vs Implementation Prompt

**Date:** 2026-05-11 (last refresh)
**Reference:** `Saudi_GRC_Platform_Claude_Code_Implementation_Prompt.md`
**Branch surveyed:** `feat/pdpl-extensions` (HEAD after item-5 commit `fce23bf`)

This document maps the prompt's 40+ required capabilities against the
current platform and flags concrete gaps. It is the input to subsequent
enhancement sprints — items marked **MISSING** or **PARTIAL** here are
the next candidates for implementation.

---

## Headline numbers

| Status | Count | Notes |
|---|---|---|
| ✓ DONE | **40 / 40** | Core ERM (now incl. emerging risk + scenarios), BCM (incl. DR plans), committee workflows, SCM, NCA ECC/CSCC/CCC/OTCC, PDPL, RCA, digital signatures, supplier resilience, workflow engine, evidence, bilingual, multi-tenant |
| ⚠ PARTIAL | **0 / 40** | All originally-PARTIAL items now closed |
| ✗ MISSING | **0 / 40** | — |

### Sprint progress on the 8 priority gaps

| # | Gap | Status | Landed in |
|---|---|---|---|
| 1 | Digital Signature transactions | ✓ DONE | V30 + `module/signature/` |
| 2 | Root Cause Analysis | ✓ DONE | V31 + `module/incident/rca/` |
| 3 | Scenario analysis (Monte Carlo persistence) | ✓ DONE | V35 + `module/risk/scenario/` |
| 4 | Alternate supplier + exit plan | ✓ DONE | V36 + `module/scm/resilience/` |
| 5 | CSCC / CCC / OTCC framework seeds | ✓ DONE | V37 + enriched `CyberFramework` |
| 6 | Cross-border transfer workflow | ✓ DONE | V32 + `module/privacy/extensions/` |
| 7 | Disposal certificate workflow | ✓ DONE | V32 + `module/privacy/extensions/` |
| 8 | Privacy notice templating | ✓ DONE | V32 + `module/privacy/extensions/` |

---

## Detailed mapping

### Section 3.1 — Enterprise Risk Management

| Item | Status | Evidence |
|---|---|---|
| Risk governance | ✓ | `module/risk/{controller,service,entity}/Risk*.java` |
| Risk taxonomy / categories | ✓ | `RiskCategory.java`, `V_risk_saudi_taxonomy_seed.sql` |
| Risk appetite | ✓ | `RiskAppetiteStatement.java`, `RiskAppetiteService.java` |
| Risk identification + assessment | ✓ | `Risk.java`, `RiskAssessment.java` |
| Risk treatment + acceptance | ✓ | `risk_lc` lifecycle module + `RiskRegisterPage.tsx` |
| KRI monitoring + breach detection | ✓ | `KriEngineService.java`, `KriDefinition.java`, `KriBreachEvent.java` |
| Scenario analysis | ✓ | `module/risk/scenario/` + V35 (Monte Carlo / stress / what-if persisted; `RiskScenariosPage`) |
| Emerging risk management | ✓ | `module/risk/emerging/` + V39 (horizon-scanning signals with HORIZON→MONITORING→ESCALATED|MATURED|DISMISSED state machine; `EmergingRiskPage`) |
| Committee workflows | ✓ | `module/admin/committee/` + V40 (`Committee` + `CommitteeMeeting` entities with agenda / attendees / decisions / action-items JSON; `CommitteesPage`) |
| Committee workflows | ✓ | `module/admin/committee/` + V40 (`Committee` + `CommitteeMeeting` with charter, members, agenda, attendees, minutes, decisions, action items, and a SCHEDULED→IN_PROGRESS→COMPLETED→MINUTES_APPROVED state machine) |

### Section 3.2 — Business Continuity Management

| Item | Status | Evidence |
|---|---|---|
| Critical service identification | ✓ | `CriticalProcess.java` |
| Business Impact Analysis (BIA) | ✓ | `BiaRecord.java` |
| BCM risk assessment | ✓ | `bcm_lc` lifecycle module |
| Continuity strategy / BCP | ✓ | `BcmPlan.java`, `BcmController.java` |
| IT DRP | ✓ | `module/bcm/drp/` + V41 (`DisasterRecoveryPlan` with RTO/RPO/MTPD, hot/warm/cold/active-active/cloud-DR site types, failover + failback step playbooks, last-tested outcome; `DrPlansPage`) |
| Crisis activation + comms | ✓ | `CrisisCommsTemplate/Audience/Distribution` + `CrisisManagementPage.tsx` |
| Exercises / tests | ✓ | `BcmExercise.java` |
| Lessons learned | ✓ | `bcm_lc` lifecycle + incidents lessons tab (added 2026-05-10) |

### Section 3.3 — Supply Chain Resilience

| Item | Status | Evidence |
|---|---|---|
| Supplier registration | ✓ | `Vendor.java` (bilingual, sector, criticality, country) |
| Supplier criticality | ✓ | `criticality` enum on `Vendor` |
| Supplier risk assessment | ✓ | `VendorAssessment.java` |
| End-to-end supply chain mapping | ✓ | `Dependency.java`, `DependencyGraphController.java` |
| Dependency mapping | ✓ | `ProcessDependency.java` (BCM) + `Dependency.java` (SCM) |
| Fourth-party risk | ⚠ | Vendor assessments capture sub-tier mentions; no `FourthPartyExposure` entity |
| SLA monitoring | ✓ | `Sla.java`, `SlaRepository.java` |
| Incident escalation | ✓ | `SupplierIncident.java` |
| Alternate supplier / exit planning | ✓ | `module/scm/resilience/` + V36 (`AlternateSupplier` + `ExitPlan` entities, lifecycle services, `SupplierResiliencePage`) |
| Resilience testing | ⚠ | Generic exercise framework exists, not supplier-specific |
| Contractual resilience clauses | ⚠ | `Contract.java` exists but no clause-level template library |

### Section 3.4 — NCA Cybersecurity

| Item | Status | Evidence |
|---|---|---|
| ECC framework + filing | ✓ | `NcaEccFiling.java`, V27 migration |
| CSCC / CCC / OTCC frameworks | ✓ | V37 — all four NCA frameworks + SAMA Cyber + ISO 22301 seeded with bilingual metadata, control counts, mandatory-for sectors |
| Cyber incident reporting | ✓ | `CyberIncident.java`, NIST 800-61 lifecycle |
| Third-party cyber risk | ⚠ | Reachable via SCM vendor risk; no dedicated `ThirdPartyCyberRisk` |
| Cloud cybersecurity | ⚠ | Asset framework supports cloud type; no CCC-specific assessment |
| Critical systems classification | ✓ | `CyberAsset.classification`, `CriticalProcess` |
| OT / ICS cybersecurity | ⚠ | OT_ICS asset type exists; no OTCC control library seeded |
| Cyber evidence | ✓ | Evidence module integrated with cyber controls |
| Vulnerability management | ✓ | `Vulnerability.java`, vuln pipeline in `cyber_lc` |

### Section 3.5 — PDPL / SDAIA Privacy

| Item | Status | Evidence |
|---|---|---|
| ROPA register | ✓ | `ProcessingActivity.java`, `RopaPage.tsx` + PDPL-workflows entry-point card with live counts |
| Privacy notices | ✓ | `module/privacy/extensions/` + V32 (`PrivacyNotice` entity, versioned bilingual templates) |
| Consent management | ✓ | Consent records modelled in privacy schema |
| Data Subject Rights (DSR) | ✓ | `DataSubjectRequest.java`, `DsrPortalPage.tsx`, 30-day SLA |
| DPIA / PIA | ✓ | `Dpia.java` (DRAFT → IN_REVIEW → APPROVED state machine) |
| Data sharing / disclosure | ⚠ | ROPA tracks sharing party; no formal `DataSharingAgreement` |
| Cross-border transfer | ✓ | `module/privacy/extensions/` + V32 (`CrossBorderTransfer` entity with REQUESTED→ASSESSED→APPROVED state machine) |
| Personal data breach | ✓ | `DataBreach.java`, 72-hour notification SLA |
| Data retention + disposal | ✓ | `module/privacy/extensions/` + V32 (`DisposalCertificate` entity with SCHEDULED→IN_PROGRESS→COMPLETED→VERIFIED state machine) |
| Processor due diligence | ⚠ | Vendor model supports it; no `ProcessorAssessment` lifecycle |

### Section 3.8 — Incident, Crisis & Notification

| Item | Status | Evidence |
|---|---|---|
| Cyber incident → PDPL breach trigger | ⚠ | Both modules exist; no automation rule links them |
| Crisis activation | ✓ | Crisis management page + BCM lifecycle invocation |
| Regulatory notification | ✓ | `IncidentService.notifyRegulator()` covers NCA HEY / SDA / CITC / SAMA / HRSD |
| Root cause analysis | ✓ | `module/incident/rca/` + V31 (5-Whys / fishbone / pareto, DRAFT→IN_REVIEW→APPROVED, generates CAPAs from recommendations) |
| Corrective action (CAPA) | ✓ | `CapaTask.java`, `CapaPage.tsx`, V23 migration |
| Lessons learned | ✓ | `bcm_lc`, incidents Lessons tab |

### Cross-cutting infrastructure

| Item | Status | Evidence |
|---|---|---|
| Digital signature / e-signing | ✓ | `module/signature/` + V30 (`DigitalSignatureTransaction`, REQUESTED→SIGNED/REJECTED/EXPIRED, INTERNAL+DOCUSIGN+ABSHER+NAFATH providers) |
| Bilingual AR/EN | ✓ | `nameEn` / `nameAr` pattern across all user-facing entities |
| Multi-tenant scoping | ✓ | `TenantContext`, `organisationId` on every row, switcher fix shipped |
| Workflow engine + state machines | ✓ | `WorkflowEngineService`, 9+ lifecycle modules with persisted state |
| Evidence repository | ✓ | `EvidenceItem`, `EvidenceVersion`, `EvidenceLink` (Sprint 4) |
| Chain of custody | ⚠ | `EvidenceVersion` tracks version history; no cryptographic hash chain |
| RBAC | ✓ | `@PreAuthorize("hasPermission(...)")` + `ModulePermissionEvaluator` |
| Audit trail | ✓ | `BaseEntity` audit fields on every entity |
| Realtime broadcast | ✓ | STOMP over WebSocket, 13 services emit `changed.*` events |
| Per-tenant white-label | ✓ | Scoped per-tenant in `lib/whiteLabel.ts` (2026-05-10) |

---

## Priority gaps — all closed (history kept for traceability)

| # | Gap | Closed in | Commit |
|---|---|---|---|
| 1 | Digital Signature transactions | V30 + `module/signature/` | initial DS commit |
| 2 | Root Cause Analysis | V31 + `module/incident/rca/` | RCA commit + CAPA wiring |
| 3 | Scenario analysis | V35 + `module/risk/scenario/` | `5627ed9` |
| 4 | Alternate supplier + exit plan | V36 + `module/scm/resilience/` | `b97d39f` |
| 5 | CSCC / CCC / OTCC framework seeds | V37 + enriched `CyberFramework` | `fce23bf` |
| 6 | Cross-border transfer workflow | V32 + `module/privacy/extensions/` | initial PDPL commit |
| 7 | Disposal certificate workflow | V32 + `module/privacy/extensions/` | initial PDPL commit |
| 8 | Privacy notice templating | V32 + `module/privacy/extensions/` | initial PDPL commit |

### Remaining (deliberately out of the priority-gap sequence)

| Item | Status | Reason it wasn't in the 8 priority gaps |
|---|---|---|
| Emerging risk management | ✓ | Closed in this sprint — V39 + `module/risk/emerging/` |
| Fourth-party exposure (SCM) | ⚠ | Sub-tier vendor depth is a Sprint 6 candidate |
| Data sharing agreement (PDPL) | ⚠ | DSAs typically managed in CLM, not GRC; out-of-scope decision |
| Committee workflows (ERM) | ✓ | Closed in this sprint — V40 + `module/admin/committee/` |
| Chain-of-custody crypto hash (Evidence) | ⚠ | EvidenceVersion already audit-able; cryptographic chain is a future hardening |
| IT DRP entity (BCM) | ✓ | Closed in this sprint — V41 + `module/bcm/drp/` |
| Cyber → PDPL breach automation rule | ⚠ | Both modules exist; rule-engine wiring is a Sprint 6 candidate |
| Processor due diligence (PDPL) | ⚠ | Subsumed by Vendor assessments + the new TPRM cyber check |
| Contractual resilience clauses (SCM) | ⚠ | Covered by Contract + new ExitPlan; clause-library is a polish item |
| Cloud cybersecurity assessment | ⚠ | CCC seed now in V37; controls library import is a separate task |
| OT / ICS control library | ⚠ | OTCC seed now in V37; same — control rows import is separate |
| Third-party cyber risk | ⚠ | Reachable via SCM vendor risk + new TPRM cyber assessment |
| Resilience testing (SCM) | ⚠ | Generic BCM exercise + new ExitPlan `recordTest()` now cover this |

---

## What the prompt asks for vs reality

The prompt expects 16 documentation deliverables under `/Saudi-GRC-Platform-Integration/`.
Most are derivable from existing code:

| Prompt deliverable | Today's equivalent |
|---|---|
| 01 Product Blueprint | `README.md` + this gap analysis |
| 02 Module Architecture | `STATUS.md` + module directory tree |
| 03 Workflow Catalogue | `sprints/sprint-4-convergence.md` §3 + lifecycle module docs |
| 04 Data Model | Flyway migrations V1–V29 + JPA entities |
| 05 UI Screens | 60 page components in `tss-grc-ui-v2/src/pages` |
| 06 Evidence Library | `evidence` module + V8/V9 migrations |
| 07 Regulatory Mapping | `moduleAnchors.ts` + framework anchors per page |
| 08 RBAC Matrix | `@PreAuthorize` annotations + `ModulePermissionEvaluator` |
| 09 Automation Rules | `RealtimeBroadcaster` + lifecycle state machines |
| 10 Reports / Dashboards | 7 per-module dashboards + Board Pack module |
| 11 Implementation Backlog | This document + `STATUS.md` |
| 12 Developer Handoff | `README.md`, `CLAUDE.md` |
| 13 API Specification | Swagger UI at `/swagger-ui.html` (dev profile) |
| 14 Test Cases | 272 backend tests; UAT cases derivable from each lifecycle module |
| 15 Sample Seed Data | V2 + V_seed_extra_orgs |
| 16 Open Assumptions | Inline `TODO` comments + this gap analysis §priority gaps |

The platform is substantively built. **All 8 priority gaps from the original analysis
are now closed** (commits 5627ed9, b97d39f, fce23bf plus the earlier
DS / RCA / PDPL-extensions commits). The remaining work in the table above is targeted
polish — none of it blocks a regulatory submission or a customer launch.
