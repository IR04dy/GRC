# TSS GRC Platform — Independent Enterprise Product Audit

**Document type:** Consolidated audit report  
**Version:** 1.0  
**Assessment date:** 2026-07-25  
**Repository baseline:** `D:\AYMAN\GRC`  
**Audience:** Board, Product, Architecture, Engineering, UX, Security, Sales, Government Acceptance Committee and investors  
**Opinion:** **Qualified — transformation required before an unqualified enterprise-market opinion**

---

## 1. Executive verdict

TSS GRC is an unusually broad Saudi GRC product asset. It contains credible functional depth across ERM, compliance, policy, cyber, SOC operations, BCM, privacy, third-party risk, SAMA outsourcing, ESG, access governance, audit, committees, ethics, regulatory affairs, data governance and specialist financial-services workflows.

It is **not yet a coherent, independently modular, enterprise-ready platform at the level implied by its feature count and documentation**.

The platform's primary problem is not missing modules. It is insufficient trust and product coherence:

1. some high-consequence approvals and committee votes are not reliably bound to the authenticated decision-maker;
2. server-side commercial entitlement is concentrated in SOC Ops rather than enforced across all sellable modules;
3. module boundaries are not executable and include extensive direct and bidirectional dependencies;
4. the UX exposes hundreds of implementation surfaces instead of organising work around persona outcomes;
5. Arabic coverage is broad, but material bilingual completeness and Arabic task usability are not uniformly enforced;
6. documentation sometimes treats the existence of code, screens or tests as proof of completion, security or market readiness;
7. production-scale performance, accessibility, disaster recovery and customer acceptance evidence remain incomplete.

### Final rating

| Scope | Rating | Opinion |
|---|---:|---|
| Product/domain breadth | 4.2/5 | Strong |
| Enterprise architecture | 2.6/5 | Developing |
| Security/control integrity | 1.8/5 | Weak; critical defects |
| UX/Arabic/accessibility | 2.8/5 | Broad but fragmented |
| Data/integration/auditability | 3.0/5 | Developing |
| Quality/operations | 3.2/5 | Good build health; incomplete acceptance proof |
| Saudi readiness | 3.7/5 | Strategic advantage requiring content governance |
| Commercial packaging | 2.4/5 | Not yet independently enforceable |
| **Weighted platform rating** | **3.0/5** | **Qualified** |

### Market verdict

- **Controlled pilot:** acceptable after Critical findings are closed.
- **General enterprise availability:** not recommended yet.
- **Government final acceptance:** conditionally rejected pending evidence.
- **Independent per-module sale:** commercially designed but not technically enforced platform-wide.
- **Investment recommendation:** invest and transform; do not restart or rewrite as microservices.

---

## 2. Scope, evidence and limitations

### Evidence reviewed

- executable Spring Boot and React source;
- SQL Server migrations and domain schemas;
- 273+ existing Markdown architecture, product, operational and remediation documents;
- `PROJECT_CHRONICLE.md`;
- `PLATFORM_ARCHITECTURE_PACK.md`;
- `PLATFORM_USER_STORIES.md`;
- existing audit/remediation plans;
- official Saudi regulatory and digital-government references;
- public competitor product/architecture material.

### Verification performed

- backend: **2,316 tests passed**, 0 failures, 0 errors;
- frontend: **49 tests passed**;
- TypeScript compilation: passed;
- Vite production build: passed;
- authorization ratchet: passed at the configured threshold of 62;
- source inventory: 58 top-level backend module directories;
- structural inventory: approximately 252 controllers, 423 repositories and 313 services;
- dependency scan: **734 direct cross-module imports**, 136 directed module pairs and 14 bidirectional pairs;
- licensing scan: 22 `@RequireModule`-guarded controllers, all within SOC Ops at baseline;
- frontend inventory: more than 300 routed pages.

### Limitations

This audit is not:

- legal advice or regulatory certification;
- a complete penetration test;
- a formal WCAG conformance audit;
- a production performance benchmark;
- a witnessed disaster-recovery exercise;
- evidence of customer product-market fit.

The local application stack was not running during the audit, so live browser and two-user API probes were not repeated in this engagement.

---

## 3. Brutal product assessment

### What is genuinely strong

- Saudi-domain breadth is substantially beyond a normal early-stage GRC product.
- ERM, BCM, cyber, privacy, sustainability and access-governance implementations show serious domain investment.
- The platform includes workflow, evidence, audit, notification, export, licensing, automation, signatures and committee capabilities.
- Bilingual content and RTL support are deeply present rather than entirely cosmetic.
- The backend and frontend build cleanly with a large passing automated suite.
- Deployment and operational documentation are unusually extensive.
- AI governance includes model inventory, validation, audit, drift and human oversight foundations.

### What is overstated

- “Market-ready” is used more broadly than retained evidence supports.
- Passing authorization annotations do not prove correct approval authority.
- Schema separation does not prove module independence.
- A licensing table and hidden navigation do not prove independently enforceable packages.
- Arabic labels do not prove Arabic-first usability.
- Framework mappings do not prove current regulatory compliance.
- Test count does not prove coverage of critical governance engines.
- Dashboard breadth does not prove decision usefulness.

### The product is currently feature-rich but trust-poor

A sophisticated buyer will be impressed during a broad demo and become concerned during due diligence. The most damaging questions are simple:

- Who actually cast this vote?
- Can the requester approve under another name?
- Where did this quorum denominator come from?
- Can an unlicensed tenant still call the API?
- Which module owns this vendor/control/finding?
- Can the system boot and operate with only one purchased package?
- Is this Arabic record complete and legally reviewed?
- Can this executive percentage be traced to current evidence?
- Has backup restoration been witnessed?
- Has an Arabic screen-reader user completed this journey?

Several current answers rely on design intent rather than retained proof.

---

## 4. Product and module assessment

| Commercial package | Domain maturity | Independence | UX | Security/control | Market opinion |
|---|---:|---:|---:|---:|---|
| Platform Kernel | 4 | 2 | 3 | 3 | Strong foundation; must be cleansed of domain coupling |
| ERM | 4 | 2–3 | 3 | 3 | Sellable after packaging and journey consolidation |
| Compliance | 4 | 2–3 | 3 | 3 | Strong candidate; canonical control ownership required |
| Policy | 4 | 3 | 3 | 3 | Nearer to package readiness |
| Internal Audit | 3 | 1–2 | 3 | 2 | Duplicate implementations and committee trust blockers |
| Change Enablement | 3 | 1 | 2 | 1 | **Not market-ready** |
| Governance/Committees | 4 | 1 | 2 | 1 | **Not acceptable for authoritative decisions** |
| Cyber GRC | 4 | 2–3 | 3 | 3 | Strong Saudi proposition |
| SOC Ops | 4 | 2–3 | 3 | 3 | Best current licensing enforcement; scale proof required |
| BCM/Crisis | 5 | 2–3 | 3 | 3 | Strong flagship candidate |
| Privacy/PDPL | 4 | 2–3 | 3 | 3 | Strong Saudi proposition; sensitive-access proof required |
| Data Governance/NDI | 3 | 1–2 | 2 | 2 | Broad but fragmented and under-tested |
| TPRM/SCM | 4 | 1–2 | 3 | 3 | Party ownership and package boundary unresolved |
| SAMA Resilience | 3 | 1–2 | 3 | 2 | High local value; formal SME validation required |
| ESG/Sustainable Finance | 5 | 2–3 | 2 | 3 | Differentiated but UX/calculation lineage must improve |
| Access Governance | 4 | 2–3 | 3 | 3 | Strong with scheduler/break-glass assurance |
| Ethics/Fraud/Complaints | 3 | 2–3 | 3 | 2 | Sensitive-case security and test evidence required |
| Regulatory Affairs | 3 | 2–3 | 3 | 2 | Useful but content/submission provenance needs strengthening |
| Saudi Financial Services | 3 | 1–2 | 3 | 2 | Promising micro-packs requiring specialist validation |
| AI/Model Governance | 4 | 2–3 | 3 | 3 | Credible foundation; AI gateway/evaluation required |

### Package conclusion

The platform contains feature modules, not yet consistently installable commercial packages. A genuinely sellable module requires:

- server-side entitlement;
- declared kernel and optional dependencies;
- owned data and public APIs;
- independent navigation and permissions;
- install, upgrade, disable and removal behaviour;
- contract, tenant and entitlement tests.

Most modules currently operate around independence level L1–L2. The target for sale is L3 or higher.

---

## 5. UX and beneficiary assessment

### Current problems

- More than 300 routes create excessive conceptual surface.
- The central router and navigation expose implementation structure.
- Large pages combine dashboards, registers, forms, workflow and administration.
- ESG and NDI pages are exceptionally large and difficult to evolve.
- Occasional first-line users see specialist complexity before their assigned work.
- Executive views remain too close to operational tables and framework percentages.
- High-consequence decisions are sometimes presented as ordinary forms/modals.
- Arabic content exists broadly but has inconsistent mandatory-field and terminology governance.
- Accessibility conformance has not been independently demonstrated.

### Required redesign

The primary information architecture should be:

1. **My Work** — tasks, approvals, evidence requests and deadlines.
2. **Assurance** — risks, obligations, controls, evidence, issues and tests.
3. **Workspaces** — only installed and permitted packages.
4. **Executive** — posture, trends and decisions.
5. **Administration** — organisation, access, packages, content and integrations.

### Persona verdicts

- **CEO/Board:** needs five-minute decision briefs with drill-down, not a module tour.
- **Risk Manager:** needs one risk workspace instead of separate register/KRI/FAIR/bow-tie/lifecycle islands.
- **Auditor:** needs independent plan-to-finding evidence with valid committee approval.
- **Compliance Officer:** needs obligation-to-common-control-to-evidence traceability.
- **Control Owner:** needs short contextual tasks, not access to the full GRC structure.
- **DPO:** needs statutory-clock, sensitive-access and deletion/transfer evidence.
- **External user:** needs a separate accessible portal with opaque tracking and minimal disclosure.

### Arabic/Saudi UX verdict

Arabic is a real platform strength, but “Arabic-first” must include:

- governed Saudi GRC terminology;
- material bilingual validation;
- mixed-direction handling;
- Arabic search normalization;
- Arabic screen-reader testing;
- Gregorian/Hijri policy;
- Saudi work calendar;
- bilingual signed decisions and regulator packs;
- human review of legally material Arabic.

---

## 6. Architecture assessment

### Current state

The modular monolith is the correct deployment pattern for the current stage. The problem is not the monolith itself. The problem is insufficiently enforced internal boundaries:

- 734 direct cross-module imports;
- 136 directed package pairs;
- 14 bidirectional pairs;
- overlapping object authorities;
- direct platform and peer dependencies;
- dashboard/reporting coupling;
- no architecture test preventing new violations.

### Target state

- mandatory Platform Kernel;
- independently entitled commercial packages;
- one canonical owner for each business object;
- published command/query/event contracts;
- transactional outbox;
- event-backed read projections;
- package manifests;
- route and permission registration;
- extraction to microservices only when justified by scale, security, residency or team autonomy.

### Architecture verdict

Do not start a microservices rewrite. First make the monolith genuinely modular. A distributed system with the current ownership ambiguity would be slower, less reliable and more expensive.

---

## 7. Security assessment

### Strong foundations

- Spring Security and permission evaluator;
- tenant context;
- JWT/refresh architecture;
- authorization regression ratchet;
- audit ledger;
- multiple server-enforced SoD patterns;
- evidence checksums;
- hardened production profile;
- explicit scheduler/async tenant concerns in engineering guidance.

### Critical weaknesses

- client-controlled approval identity;
- generic change transitions using `WRITE`;
- caller-authored committee voting identities and attendance;
- client-influenced quorum;
- incomplete package entitlement enforcement;
- export permission mismatch;
- insufficient direct adversarial tests for committee governance.

### Security verdict

The platform is not generally insecure, but the most sensitive weakness is business-logic security: the system can authenticate a user correctly and still record a decision under untrustworthy authority semantics.

---

## 8. Saudi-market assessment

### Strategic advantage

TSS can credibly differentiate through:

- NCA-focused cyber assurance;
- SAMA cyber, outsourcing and resilience workflows;
- PDPL operational workflows;
- NDI/data governance;
- Arabic board and regulator output;
- Saudi private/on-prem/cloud deployment options;
- Sharia, open-banking and specialist financial workflows;
- local ESG/sustainable-finance depth.

### Structural risk

Regulatory content must not remain a mixture of migration seeds, hard-coded names and documents. Establish a Saudi Content Office that owns:

- official source and version;
- Arabic authoritative text;
- applicability;
- reviewed obligation/control decomposition;
- change impact;
- customer activation/supersession;
- release notes and update SLA.

### Official baseline

- [NCA Essential Cybersecurity Controls](https://nca.gov.sa/en/regulatory-documents/controls-list/ecc/)
- [NCA implementation guides](https://nca.gov.sa/en/regulatory-documents/guidelines-list/1368/)
- [SAMA Cyber Security Framework](https://rulebook.sama.gov.sa/en/cyber-security-framework-2)
- [SDAIA PDPL Implementing Regulation](https://dgp.sdaia.gov.sa/wps/portal/pdp/knowledgecenter/details/PDPL2/)
- [DGA Regulatory Framework](https://dga.gov.sa/en/regulatory_framework)
- [DGA Digital Transformation Standards](https://dga.gov.sa/en/Standards_Of_Digital_Transformation)

Mappings support customer compliance work; they do not certify customer compliance.

---

## 9. Competitive assessment

| Benchmark | Lesson for TSS |
|---|---|
| ServiceNow IRM | connect operational context, common controls and remediation |
| Archer | retain configurable taxonomies while reducing administrative complexity |
| MetricStream | organise related products on one connected platform and common-control model |
| OneTrust | use purpose-built privacy/third-party journeys and shared trust data |
| AuditBoard | make audit/risk/compliance collaboration approachable |
| Atlassian | simplify intake, queues, templates and change/deployment context |
| Microsoft | use governed base/application package layering |
| Salesforce | make dependencies, versions and commercial packages declarative |

TSS should not imitate the complexity of global platforms. It should win through Saudi depth, Arabic usability, composable packages and defensible evidence.

---

## 10. Detailed findings

### F-001 — Change approval identity is client-controlled

- **Category:** Security / Change Governance
- **Severity:** Critical
- **Current State:** Change approval accepts caller-provided approver name; authenticated identity is not authoritative.
- **Problem:** A writer may vote under arbitrary identities and bypass name-based self-vote logic.
- **Root Cause:** Identity and decision payload were modelled together.
- **Impact:** CAB decisions and SoD cannot be trusted.
- **Business Impact:** Unsafe or fraudulent change approval.
- **Technical Impact:** Optional actor ID and weak uniqueness.
- **UX Impact:** Editable approver name creates false confidence.
- **Sales Impact:** Immediate due-diligence blocker.
- **Recommendation:** Derive actor from authentication; require effective membership and `CHANGE:APPROVE`; unique vote per tenant/change/user.
- **Alternative Solutions:** Digitally sign every vote; route through a consolidated decision engine.
- **Trade-offs:** Server binding is mandatory and fastest; signatures add stronger assurance with higher operational cost.
- **Estimated Effort:** 3–5 engineering days.
- **Priority:** P0.
- **Expected ROI:** Very high.
- **References:** `ChangeManagementController.java:96`, `ChangeManagementService.java:318`, `ChangeApproval.java`, `ChangesPage.tsx`.

### F-002 — Generic change transitions bypass approval semantics

- **Category:** Business Logic Security
- **Severity:** Critical
- **Current State:** `CHANGE:WRITE` protects a generic transition capable of privileged approval/rejection/emergency states.
- **Problem:** State can indicate an approval that never occurred.
- **Root Cause:** Generic status setter instead of named domain commands.
- **Impact:** Unauthorized implementation and false audit history.
- **Business Impact:** Material change risk.
- **Technical Impact:** Authorization ratchet produces a false positive because an annotation exists.
- **UX Impact:** Preparation and accountable authorization are indistinguishable.
- **Sales Impact:** Invalidates server-enforced CAB claims.
- **Recommendation:** Named commands, state-specific permission, SoD, ECAB ratification and PIR.
- **Alternative Solutions:** Add internal state-aware checks to the current endpoint.
- **Trade-offs:** Internal checks are quicker; named commands are safer and clearer.
- **Estimated Effort:** 5–8 days.
- **Priority:** P0.
- **Expected ROI:** Very high.
- **References:** `ChangeManagementController.java:78`, change state service, migration V301.

### F-003 — Committee votes and quorum can be manufactured

- **Category:** Governance / Security
- **Severity:** Critical
- **Current State:** Voter names and attendance can be caller-supplied; quorum can depend on those rows.
- **Problem:** Writers can invent identities or denominators.
- **Root Cause:** Membership authority, attendance and meeting administration are not separated.
- **Impact:** Resolutions and minutes may be invalid.
- **Business Impact:** Board/regulatory decision challenge.
- **Technical Impact:** Name-based uniqueness does not prove eligibility.
- **UX Impact:** Secretary is asked to author facts the system should derive.
- **Sales Impact:** Fatal committee-engine blocker.
- **Recommendation:** Snapshot effective membership; bind votes to member/user IDs; compute quorum; enforce COI, chair/secretary/member authority and minutes SoD.
- **Alternative Solutions:** Integrate a board portal and signed attendance.
- **Trade-offs:** Internal authority control remains mandatory even with an external portal.
- **Estimated Effort:** 2–3 weeks.
- **Priority:** P0.
- **Expected ROI:** Very high.
- **References:** `CommitteeResolutionController.java:57`, `CommitteeResolutionService.java:84`, `CommitteeService.java:323`.

### F-004 — Commercial package licensing is not platform-wide

- **Category:** Product Architecture / Commercial Security
- **Severity:** Critical
- **Current State:** Licensing infrastructure exists; guarded controllers are concentrated in SOC Ops.
- **Problem:** Other unlicensed module APIs may remain callable.
- **Root Cause:** Licensing was implemented as a local feature rather than a platform invariant.
- **Impact:** Independent sale, disablement and contract enforcement are unreliable.
- **Business Impact:** Revenue leakage and contractual ambiguity.
- **Technical Impact:** Entitlements do not uniformly govern routes, jobs, exports and events.
- **UX Impact:** Hidden modules can still influence platform behaviour.
- **Sales Impact:** Direct blocker to separate-module sale.
- **Recommendation:** Central package registry, universal entitlement interceptor and manifest-driven API/UI/job registration.
- **Alternative Solutions:** Manual controller annotations; separate binaries per SKU.
- **Trade-offs:** Registry is safer; separate binaries create release explosion.
- **Estimated Effort:** 3–5 weeks for kernel and first packages.
- **Priority:** P0.
- **Expected ROI:** Foundational.
- **References:** `platform/licensing/*`, `JwtTokenProvider.java`, `socops/controller/*`, V97.

### F-005 — Module boundaries are not executable

- **Category:** Enterprise Architecture
- **Severity:** High
- **Current State:** 734 cross-module imports, 136 directed pairs and 14 bidirectional pairs.
- **Problem:** Independence depends on developer memory.
- **Root Cause:** Functional delivery outran bounded-context governance.
- **Impact:** High regression and extraction cost.
- **Business Impact:** Slower roadmap and poor package economics.
- **Technical Impact:** Cycles, shared transactions and unclear ownership.
- **UX Impact:** Duplicate representations and fragmented journeys.
- **Sales Impact:** “Independent module” claim fails technical review.
- **Recommendation:** Dependency ratchet, kernel/owner contracts, events and read models.
- **Alternative Solutions:** Microservices rewrite; permanent unconstrained monolith.
- **Trade-offs:** Incremental modularisation is slower visually but far safer.
- **Estimated Effort:** 2 weeks controls; 3–6 months remediation.
- **Priority:** P1.
- **Expected ROI:** High and recurring.
- **References:** dependency scan and module inventory.

### F-006 — Information architecture exposes implementation sprawl

- **Category:** UX / Product
- **Severity:** High
- **Current State:** More than 300 centrally registered routes and many tab-heavy workspaces.
- **Problem:** Users see platform breadth before their work.
- **Root Cause:** Screens were appended by module/slice without a governing IA.
- **Impact:** Cognitive overload and training cost.
- **Business Impact:** Low adoption.
- **Technical Impact:** Large navigation/router blast radius.
- **UX Impact:** Duplicate routes and unclear next action.
- **Sales Impact:** Demos feel like feature tours.
- **Recommendation:** Role shells, My Work, Assurance, installed Workspaces, Executive and Administration.
- **Alternative Solutions:** Search and favourites only.
- **Trade-offs:** Search helps but cannot repair the mental model.
- **Estimated Effort:** 8–12 weeks staged.
- **Priority:** P1.
- **Expected ROI:** Very high.
- **References:** `tss-grc-ui-v2/src/App.tsx`.

### F-007 — Arabic completeness claims exceed enforcement

- **Category:** UX / Data / Saudi
- **Severity:** High
- **Current State:** Some documented mandatory Arabic fields are nullable and optional in UI/backend.
- **Problem:** Material Arabic records may be incomplete.
- **Root Cause:** Bilingual policy is a convention, not a shared validated contract.
- **Impact:** Incomplete Arabic decisions and exports.
- **Business Impact:** Saudi credibility and report rework.
- **Technical Impact:** Inconsistent validation layers.
- **UX Impact:** Unclear translation responsibility.
- **Sales Impact:** Weakens Arabic-first differentiation.
- **Recommendation:** Material bilingual policy, DTO/domain validation, translation status and Arabic/English tests.
- **Alternative Solutions:** Tenant-configurable exceptions; machine translation.
- **Trade-offs:** Machine translation may draft, not authorise.
- **Estimated Effort:** 2 weeks shared pattern plus rollout.
- **Priority:** P0 for material decisions; P1 platform-wide.
- **Expected ROI:** High.
- **References:** `ChangeRequest.java`, `ChangesPage.tsx`, `PLATFORM_USER_STORIES.md`.

### F-008 — Export authorization violates least privilege

- **Category:** Security / Data Loss Prevention
- **Severity:** High
- **Current State:** Change/CAB exports use `CHANGE:READ` although `CHANGE:EXPORT` exists.
- **Problem:** Interactive read implies bulk extraction.
- **Root Cause:** Export treated as display rather than higher-risk action.
- **Impact:** Confidential data exfiltration risk.
- **Business Impact:** Insider and accidental disclosure.
- **Technical Impact:** Dead/misleading permission semantics.
- **UX Impact:** No explicit export authority or classification context.
- **Sales Impact:** Weak DLP answer.
- **Recommendation:** Require `EXPORT`; preserve row/field scope; audit and classify exports.
- **Alternative Solutions:** Remove export or make it admin-only.
- **Trade-offs:** Purpose-built permission provides the best balance.
- **Estimated Effort:** 2–4 days.
- **Priority:** P0.
- **Expected ROI:** High for low effort.
- **References:** `ExportController.java:125`, V301.

### F-009 — Governance core lacks adversarial test coverage

- **Category:** Quality / Security Assurance
- **Severity:** High
- **Current State:** Suite passes, but committee service/resolution logic lacks dedicated module tests.
- **Problem:** Critical governance defects coexist with green test counts.
- **Root Cause:** Test volume is not weighted by control criticality.
- **Impact:** Invalid decision logic can regress silently.
- **Business Impact:** Governance release risk.
- **Technical Impact:** Unsafe refactoring.
- **UX Impact:** UI may show success for invalid decisions.
- **Sales Impact:** Test-count claims lose credibility.
- **Recommendation:** Multi-principal membership, COI, quorum, duplicate, forged vote, tenant and concurrency tests.
- **Alternative Solutions:** End-to-end only or manual UAT only.
- **Trade-offs:** Domain and API/live tests are both required.
- **Estimated Effort:** 1–2 weeks.
- **Priority:** P0.
- **Expected ROI:** Very high.
- **References:** module test inventory.

### F-010 — Canonical ownership is fragmented

- **Category:** Data / Domain Architecture
- **Severity:** High
- **Current State:** Audit, approvals, party/vendor, privacy/data and lifecycle concepts overlap across modules.
- **Problem:** Several stores/pages may represent the same business concept.
- **Root Cause:** Parallel programme delivery without canonical object governance.
- **Impact:** Inconsistent state and duplicate entry.
- **Business Impact:** Customers cannot identify the source of truth.
- **Technical Impact:** Bridges, joins and reconciliation.
- **UX Impact:** Duplicate workspaces.
- **Sales Impact:** Weakens connected-platform promise.
- **Recommendation:** Object ownership catalogue, authority decisions and migration to owners/projections.
- **Alternative Solutions:** Permanent synchronisation or universal mega-tables.
- **Trade-offs:** Finite migration is preferable to permanent reconciliation.
- **Estimated Effort:** 4 weeks decisions; multiple quarters migration.
- **Priority:** P1.
- **Expected ROI:** High.
- **References:** source-module inventory.

### F-011 — Accessibility conformance is unproven

- **Category:** UX / Government Acceptance
- **Severity:** High
- **Current State:** No current independent WCAG 2.2 AA report was available.
- **Problem:** Government and inclusive-access claims lack journey proof.
- **Root Cause:** Accessibility treated as findings rather than continuous product quality.
- **Impact:** Unknown keyboard, screen-reader, zoom and chart barriers.
- **Business Impact:** Exclusion and procurement failure.
- **Technical Impact:** Expensive late remediation.
- **UX Impact:** Users may be unable to complete tasks.
- **Sales Impact:** Government readiness remains conditional.
- **Recommendation:** WCAG 2.2 AA target, automated checks, Arabic/English independent audit and assistive-technology testing.
- **Alternative Solutions:** WCAG 2.1 or public portals only.
- **Trade-offs:** 2.2 is stricter and more future-ready.
- **Estimated Effort:** 6–10 weeks depending on findings.
- **Priority:** P1.
- **Expected ROI:** High.
- **References:** DGA accessibility guidance.

### F-012 — Saudi regulatory content lacks a governed lifecycle

- **Category:** Saudi Readiness / Product Operations
- **Severity:** High
- **Current State:** Extensive mappings/seeds exist; some remain marked for verification.
- **Problem:** Freshness, official language, applicability and review are not a standing product service.
- **Root Cause:** Content delivered through code/migrations rather than governed packs.
- **Impact:** Stale or unofficial assessment basis.
- **Business Impact:** False assurance and reimplementation.
- **Technical Impact:** Content updates may require code release.
- **UX Impact:** Source/effective date/mapping confidence are unclear.
- **Sales Impact:** Saudi advantage can become a liability.
- **Recommendation:** Saudi Content Office and versioned content packs.
- **Alternative Solutions:** External provider or customer-managed content.
- **Trade-offs:** Hybrid sourcing is preferred.
- **Estimated Effort:** 6–8 weeks platform plus ongoing SME operation.
- **Priority:** P1.
- **Expected ROI:** Very high.
- **References:** project docs and official Saudi sources.

### F-013 — Documentation completion markers are unsafe

- **Category:** Governance / Product Management
- **Severity:** High
- **Current State:** Some completed user stories and architecture grades contradict executable behaviour.
- **Problem:** Documents are treated as truth without evidence hierarchy.
- **Root Cause:** Completion means existence rather than multi-gate readiness.
- **Impact:** Incorrect roadmaps and claims.
- **Business Impact:** Misallocated investment and reputation.
- **Technical Impact:** Critical defects omitted from plans.
- **UX Impact:** Claimed journeys may not work.
- **Sales Impact:** Due-diligence credibility loss.
- **Recommendation:** Truth labels, evidence links and automatic reopening of contradicted stories.
- **Alternative Solutions:** Archive old docs or generate all docs.
- **Trade-offs:** Governed mixed evidence is required.
- **Estimated Effort:** 1–2 weeks initial; continuous.
- **Priority:** P0 governance.
- **Expected ROI:** High.
- **References:** `PLATFORM_USER_STORIES.md`, `PLATFORM_ARCHITECTURE_PACK.md`.

### F-014 — Approval primitives are duplicated

- **Category:** Architecture / Governance
- **Severity:** High
- **Current State:** Generic approvals, workflow chains, domain approvals, committee voting and signatures overlap.
- **Problem:** Actor, authority, SoD and audit semantics vary.
- **Root Cause:** Per-programme implementation.
- **Impact:** Repeated defects and inconsistent UX.
- **Business Impact:** Approval-policy change is expensive.
- **Technical Impact:** More tables/services/schedulers.
- **UX Impact:** Inbox and domain state can disagree.
- **Sales Impact:** Weak configurable-workflow story.
- **Recommendation:** One decision/authority contract with specialised sequential/quorum/signature policies.
- **Alternative Solutions:** Federated inbox only or external BPM replacement.
- **Trade-offs:** Unify contract before physical engine.
- **Estimated Effort:** 4–6 weeks foundation, incremental migration.
- **Priority:** P1.
- **Expected ROI:** High.
- **References:** `module/approvals`, workflow chains, committee and signature modules.

### F-015 — Large frontend units obstruct safe UX evolution

- **Category:** Frontend / UX
- **Severity:** Medium-High
- **Current State:** Major pages contain thousands of lines and multiple workflows; main bundle exceeds the warning threshold.
- **Problem:** Data, state, forms, tables and workflow live in giant units.
- **Root Cause:** Functionality appended for delivery speed.
- **Impact:** Regression and change risk.
- **Business Impact:** UX iteration slows.
- **Technical Impact:** Testing, splitting and merges become difficult.
- **UX Impact:** Tab sprawl and weak progressive disclosure.
- **Sales Impact:** Polish lags breadth.
- **Recommendation:** Split by journey/package route, shared workspace patterns and route budgets.
- **Alternative Solutions:** File-only refactor or raised bundle threshold.
- **Trade-offs:** Journey redesign provides more value than cosmetic splitting.
- **Estimated Effort:** 2–3 weeks per flagship workspace.
- **Priority:** P1/P2.
- **Expected ROI:** Medium-high.
- **References:** ESG, NDI, Change pages and Vite build warning.

### F-016 — Production-like operational proof is incomplete

- **Category:** Operations / Acceptance
- **Severity:** Medium
- **Current State:** Builds and documentation are strong; current performance, failover, restoration and rollback proof were not available.
- **Problem:** Operability is documented more strongly than independently demonstrated.
- **Root Cause:** Evidence is distributed rather than retained per release.
- **Impact:** Capacity and recovery assumptions remain uncertain.
- **Business Impact:** Go-live and SLA risk.
- **Technical Impact:** Unknown bottlenecks and recovery incompatibilities.
- **UX Impact:** Potential outage and degraded service.
- **Sales Impact:** Enterprise acceptance remains conditional.
- **Recommendation:** Production-like benchmark, backup/restore, failover and upgrade/rollback rehearsal with signed evidence.
- **Alternative Solutions:** Controlled pilot with explicit risk.
- **Trade-offs:** Evidence environments cost time but prevent expensive failures.
- **Estimated Effort:** 3–6 weeks.
- **Priority:** P1 before GA.
- **Expected ROI:** High.
- **References:** `DEPLOYMENT.md`, `PROJECT_CHRONICLE.md`.

---

## 11. Government acceptance decision

| Gate | Decision |
|---|---|
| Functional breadth | Conditional pass |
| Authentication/authorization | Fail pending P0 |
| Decision integrity | Fail |
| Tenant isolation | Conditional |
| Arabic UX | Conditional |
| Accessibility | Not proven |
| Saudi regulatory content | Conditional |
| Performance | Not proven |
| Backup/DR | Not proven |
| Package independence | Fail |
| Documentation | Conditional |

**Recommendation:** permit a restricted pilot after Critical findings close. Do not issue final acceptance or broad compliance/market-ready claims until acceptance evidence is retained.

---

## 12. Strategic recommendation

The transformation sequence is:

1. **Assure:** close decision-integrity, export, tenant and governance-test gaps.
2. **Simplify:** redesign around My Work and persona journeys.
3. **Package:** implement kernel, manifests and server-side entitlements.
4. **Connect:** establish canonical objects, evidence graph, contracts and events.
5. **Localise operationally:** govern Saudi content and Arabic UX continuously.
6. **Prove:** obtain accessibility, security, DR, performance and lighthouse-customer evidence.
7. **Scale:** introduce AI and selective services only on a trustworthy foundation.

The product should be positioned as:

> **The Arabic-first Saudi assurance operating system: one evidence graph, configurable regulatory packs and independently purchasable GRC workspaces.**

---

## 13. Information still required

To convert the qualified opinion into a definitive investment and market-readiness opinion, obtain:

- production topology, volume, latency and availability telemetry;
- witnessed backup, restore and failover results;
- current customer contracts, SLA and data residency commitments;
- real package/pricing and cost-to-serve data;
- sales pipeline, losses and buyer objections;
- customer/user interviews and Arabic usability recordings;
- independent penetration-test report;
- independent WCAG report;
- current regulatory-content source/version approvals;
- data classification, retention and legal-hold policies;
- connector inventory and credential/data-flow register;
- customer UAT and regulator/government acceptance evidence;
- incident, defect and support trends;
- module-level revenue and adoption.

---

## 14. Final conclusion

TSS GRC should not be discarded, rewritten from scratch or reduced to a generic GRC clone. It has real product value and a defensible Saudi strategic direction.

It must, however, stop measuring maturity by feature volume. The next stage is to make every important decision attributable, every package enforceable, every metric explainable, every Arabic journey usable and every market claim evidence-backed.

Until that work is complete, the honest verdict is:

**High-potential platform; strong Saudi domain asset; qualified enterprise readiness; critical governance and modular-commercial remediation required.**

