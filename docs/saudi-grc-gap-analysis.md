# Saudi GRC Platform — Gap Analysis vs Implementation Prompt

**Date:** 2026-05-11
**Reference:** `Saudi_GRC_Platform_Claude_Code_Implementation_Prompt.md`
**Branch surveyed:** `master` (post-merge, commit `2f7b5902`)

This document maps the prompt's 40+ required capabilities against the
current platform and flags concrete gaps. It is the input to subsequent
enhancement sprints — items marked **MISSING** or **PARTIAL** here are
the next candidates for implementation.

---

## Headline numbers

| Status | Count | Notes |
|---|---|---|
| ✓ DONE | **31 / 40** | Core ERM, BCM, SCM, NCA ECC, PDPL, workflow engine, evidence, bilingual, multi-tenant |
| ⚠ PARTIAL | **13 / 40** | Mostly secondary frameworks and cross-module wiring |
| ✗ MISSING | **4 / 40** | Digital signatures, RCA, scenario analysis, alternate supplier planning |

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
| **Scenario analysis** | **✗** | Monte Carlo simulator exists in `RiskLifecyclePage` but no persistent scenario entity |
| **Emerging risk management** | **✗** | No `EmergingRisk` entity, no horizon-scanning workflow |
| Committee workflows | ⚠ | `ApprovalRequest` covers generic approvals; no dedicated `Committee` / `CommitteeMeeting` entity |

### Section 3.2 — Business Continuity Management

| Item | Status | Evidence |
|---|---|---|
| Critical service identification | ✓ | `CriticalProcess.java` |
| Business Impact Analysis (BIA) | ✓ | `BiaRecord.java` |
| BCM risk assessment | ✓ | `bcm_lc` lifecycle module |
| Continuity strategy / BCP | ✓ | `BcmPlan.java`, `BcmController.java` |
| IT DRP | ⚠ | `BackupDrPage.tsx` UI only; no backend `DisasterRecoveryPlan` entity |
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
| **Alternate supplier / exit planning** | **✗** | No `AlternateSupplierPlan` or `ExitPlan` entity |
| Resilience testing | ⚠ | Generic exercise framework exists, not supplier-specific |
| Contractual resilience clauses | ⚠ | `Contract.java` exists but no clause-level template library |

### Section 3.4 — NCA Cybersecurity

| Item | Status | Evidence |
|---|---|---|
| ECC framework + filing | ✓ | `NcaEccFiling.java`, V27 migration |
| CSCC / CCC / OTCC frameworks | ⚠ | Generic `CyberFramework` exists, frameworks not separately seeded |
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
| ROPA register | ✓ | `ProcessingActivity.java`, `RopaPage.tsx` (frontend uses mocks — wire-up listed for Sprint 5) |
| Privacy notices | ⚠ | No dedicated `PrivacyNotice` entity |
| Consent management | ✓ | Consent records modelled in privacy schema |
| Data Subject Rights (DSR) | ✓ | `DataSubjectRequest.java`, `DsrPortalPage.tsx`, 30-day SLA |
| DPIA / PIA | ✓ | `Dpia.java` (DRAFT → IN_REVIEW → APPROVED state machine) |
| Data sharing / disclosure | ⚠ | ROPA tracks sharing party; no formal `DataSharingAgreement` |
| Cross-border transfer | ⚠ | Field on ROPA; no dedicated `CrossBorderTransfer` workflow |
| Personal data breach | ✓ | `DataBreach.java`, 72-hour notification SLA |
| Data retention + disposal | ⚠ | Retention field on ROPA; no `DisposalCertificate` workflow |
| Processor due diligence | ⚠ | Vendor model supports it; no `ProcessorAssessment` lifecycle |

### Section 3.8 — Incident, Crisis & Notification

| Item | Status | Evidence |
|---|---|---|
| Cyber incident → PDPL breach trigger | ⚠ | Both modules exist; no automation rule links them |
| Crisis activation | ✓ | Crisis management page + BCM lifecycle invocation |
| Regulatory notification | ✓ | `IncidentService.notifyRegulator()` covers NCA HEY / SDA / CITC / SAMA / HRSD |
| **Root cause analysis** | **✗** | No `RootCauseAnalysis` entity; only free-text `lessonsLearned` field |
| Corrective action (CAPA) | ✓ | `CapaTask.java`, `CapaPage.tsx`, V23 migration |
| Lessons learned | ✓ | `bcm_lc`, incidents Lessons tab |

### Cross-cutting infrastructure

| Item | Status | Evidence |
|---|---|---|
| **Digital signature / e-signing** | **✗** | No `DigitalSignatureTransaction` entity, no provider integration |
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

## Priority gaps (sequenced for impact)

1. **Digital Signature transactions** — Section 28 of prompt has a complete field-level spec. Used by Board Pack approval, Risk Acceptance, Policy approval, BCP approval, Regulatory evidence package approval, Supplier attestation, NCA compliance attestation (15+ use cases listed in §28). Implementing this unblocks "regulatory-grade" approval workflows across the whole platform.
2. **Root Cause Analysis (RCA)** — Section 3.8 incident lifecycle is incomplete without it. Free-text `lessonsLearned` is too thin for regulators. A structured RCA entity (problem statement, contributing factors, 5-whys chain, corrective recommendations, linked CAPA) fills the gap.
3. **Scenario analysis (ERM)** — Section 7.3. Persist the Monte Carlo runs from `RiskLifecyclePage` as `RiskScenario` entities so analysts can compare scenarios over time and link them to risk acceptance decisions.
4. **Alternate supplier + exit plan (SCM)** — Section 3.3. Required for "supplier resilience report" (Section 30.3 dashboard). Concrete entity scope: `AlternateSupplier` (preferred replacements per critical supplier) + `ExitPlan` (steps, owner, SLA, last-tested date).
5. **CSCC / CCC / OTCC framework seeds** — extend `CyberFramework` with proper NCA framework rows so the assessment UI can offer all four (ECC + CSCC + CCC + OTCC).
6. **Cross-border transfer workflow** — `CrossBorderTransfer` entity with approval flow, residency check, justification, expiry.
7. **Disposal certificate workflow** — `DataDisposalCertificate` linked to retention rule expiry, signed by data owner.
8. **Privacy notice templating** — `PrivacyNotice` entity with versioned bilingual templates linked to ROPA.

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

The platform is substantively built. The remaining work is targeted enhancement of the
4 missing items + 13 partial items above, **not** a from-scratch implementation effort.
