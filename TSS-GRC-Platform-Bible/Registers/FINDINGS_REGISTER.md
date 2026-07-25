# Findings Register

## Summary

| ID | Severity | Finding | Status |
|---|---|---|---|
| F-001 | Critical | Change approval identity is client-controlled | Open |
| F-002 | Critical | Generic change transitions bypass approval semantics | Open |
| F-003 | Critical | Committee votes and quorum can be manufactured | Open |
| F-004 | Critical | Commercial package licensing is not platform-wide | Open |
| F-005 | High | Module boundaries are not executable | Open |
| F-006 | High | Product information architecture exposes implementation sprawl | Open |
| F-007 | High | Arabic completeness claims exceed enforcement | Open |
| F-008 | High | Export authorization violates least privilege | Open |
| F-009 | High | Governance core lacks direct adversarial test coverage | Open |
| F-010 | High | Canonical ownership is fragmented across overlapping modules | Open |
| F-011 | High | Accessibility conformance is unproven | Open |
| F-012 | High | Saudi regulatory content lacks a governed release lifecycle | Open |
| F-013 | High | Documentation completion markers are not evidence-safe | Open |
| F-014 | High | Approval primitives are duplicated | Open |
| F-015 | Medium-High | Large frontend units obstruct coherent UX and safe change | Open |
| F-016 | Medium | Production-like operational proof is incomplete | Open |

---

## F-001 — Change approval identity is client-controlled

**Category:** Security / Change Governance  
**Severity:** Critical  
**Current State:** `ChangeManagementController.recordApproval` accepts a `ChangeApproval` request body. The service requires `approverName` but does not bind the decision to the authenticated principal. `approverId` is optional and the self-vote rule compares a caller-provided name.

**Problem:** A writer can submit approvals under arbitrary names and potentially create several logically distinct voters.

**Root Cause:** Identity and decision DTO were modelled together; an authenticated actor contract was deferred.

**Impact:** CAB results, segregation of duties and retained approval evidence cannot be trusted.

**Business Impact:** A failed or fraudulent change cannot be defended to management, audit, customer or regulator.

**Technical Impact:** Name-based uniqueness and self-vote logic are bypassable; historical records cannot be reliably reconciled to users.

**UX Impact:** The form invites the user to edit the approver name, creating false confidence that a typed name is an identity.

**Sales Impact:** Immediate technical due-diligence and government-acceptance blocker.

**Recommendation:** Remove approver identity from the writable DTO. Resolve immutable authenticated user ID/name server-side, validate effective CAB membership/role, require `CHANGE:APPROVE`, and enforce unique `(organisation_id, change_request_id, approver_user_id)`.

**Alternative Solutions:** Digitally sign every vote; route decisions through the generic approval engine.

**Trade-offs:** Server binding is lowest complexity and mandatory. Digital signatures add stronger non-repudiation but higher operational/key cost. Generic workflow reuse is valuable only after approval primitives are consolidated.

**Estimated Effort:** 3–5 engineering days plus migration and tests.

**Priority:** P0 / first remediation slice.

**Expected ROI:** Very high; restores decision trust and removes a critical sales blocker.

**References:** `change_mgmt/controller/ChangeManagementController.java:96`; `change_mgmt/service/ChangeManagementService.java:318`; `change_mgmt/entity/ChangeApproval.java`; `pages/changes/ChangesPage.tsx`.

---

## F-002 — Generic change transitions bypass approval semantics

**Category:** Business Logic Security / Authorization  
**Severity:** Critical  
**Current State:** The generic transition endpoint uses `CHANGE:WRITE` while the state model includes `CAB_APPROVED`, `CAB_REJECTED` and `EMERGENCY_APPROVED`. The permission catalogue includes `CHANGE:APPROVE`, but it is not used for these decisions.

**Problem:** A writer can request privileged target states through a generic status transition. Emergency type is caller-selectable and can reach emergency approval without a separately authenticated authority.

**Root Cause:** State mutation was implemented as one generic endpoint and authorization checked at module/action level rather than command/transition level.

**Impact:** The state machine can represent a decision that never occurred.

**Business Impact:** Unsafe changes may be scheduled or implemented without valid approval.

**Technical Impact:** Authorization ratchet passes because an annotation exists, although its semantics are wrong.

**UX Impact:** Users cannot distinguish preparation actions from accountable authorization.

**Sales Impact:** Invalidates “server-enforced CAB/SoD” claims.

**Recommendation:** Replace arbitrary target-state transition with named commands and transition policies. Require `APPROVE` plus effective authority and SoD for decision commands. Require ratification and PIR for emergency changes.

**Alternative Solutions:** Add state-aware checks inside the current endpoint; use the workflow engine.

**Trade-offs:** State-aware checks are faster but retain an unsafe public contract. Named commands are clearer and testable. Workflow adoption adds consistency but should not delay the P0 guard.

**Estimated Effort:** 5–8 engineering days.

**Priority:** P0.

**Expected ROI:** Very high; prevents unauthorized implementation and improves audit clarity.

**References:** `change_mgmt/controller/ChangeManagementController.java:78`; `change_mgmt/service/ChangeManagementService.java`; migration `V301`.

---

## F-003 — Committee votes and quorum can be manufactured

**Category:** Governance / Security  
**Severity:** Critical  
**Current State:** Vote requests accept `memberName` from the caller. Attendance rows can be replaced from caller-provided data. Quorum uses those rows, and meeting completion can accept a supplied `quorumMet` value when structured attendance is absent.

**Problem:** An authorized writer can invent voting identities or attendance and create the appearance of quorum.

**Root Cause:** Meeting administration, membership authority, attendance observation and decision authority are not separated.

**Impact:** Resolutions, minutes, referrals and downstream approvals may be based on invalid governance.

**Business Impact:** Board/committee decisions become challengeable; regulated approvals may be void.

**Technical Impact:** Name-based uniqueness cannot establish eligibility or one-person-one-vote.

**UX Impact:** The secretary is asked to author facts the system should derive, increasing both fraud and innocent-error risk.

**Sales Impact:** Fatal for committee, audit, risk-acceptance and CAB demonstrations to sophisticated buyers.

**Recommendation:** Snapshot effective membership at meeting time; derive eligible denominator; bind attendance and votes to member/user IDs; allow secretary to mark presence/COI only; compute quorum server-side; enforce chair/secretary/member permissions and minutes SoD.

**Alternative Solutions:** Import signed attendance from an external board portal; use digital signatures per vote.

**Trade-offs:** Internal membership control is required regardless. External integration may improve board experience but adds dependency.

**Estimated Effort:** 2–3 weeks including migration, UI and adversarial tests.

**Priority:** P0.

**Expected ROI:** Very high across every governance-dependent package.

**References:** `admin/committee/controller/CommitteeResolutionController.java:57`; `admin/committee/service/CommitteeResolutionService.java:84`; `admin/committee/service/CommitteeService.java:323`.

---

## F-004 — Commercial package licensing is not platform-wide

**Category:** Product Architecture / Commercial Security  
**Severity:** Critical  
**Current State:** Licensing entities, JWT `enabled_modules`, `LicenseGuardAspect` and `@RequireModule` exist. Baseline scan found 22 guarded controllers, all within `socops`.

**Problem:** Hiding navigation or issuing a JWT claim does not stop unlicensed API use for the other commercial domains.

**Root Cause:** Licensing was delivered as a feature-specific slice rather than a platform invariant and package manifest.

**Impact:** Independent module sale, contract enforcement, safe disablement and usage metering are unreliable.

**Business Impact:** Customers may access unpaid modules; contractual packaging cannot be represented accurately.

**Technical Impact:** Routes, permissions, migrations and schedulers are not consistently tied to entitlements.

**UX Impact:** A hidden module can still affect dashboards, tasks or integrations; users receive inconsistent experiences.

**Sales Impact:** Direct blocker to the requested per-module commercial model.

**Recommendation:** Introduce a central endpoint/package registry and universal entitlement interceptor; require every commercial API, route, job, export and event consumer to declare package code; add unlicensed negative tests and package manifests.

**Alternative Solutions:** Annotate every controller manually; deploy separate binaries per SKU.

**Trade-offs:** Central registry is less error-prone. Manual annotations are useful defence-in-depth. Separate binaries create release and support explosion.

**Estimated Effort:** 3–5 weeks for kernel, manifests and first package; incremental migration thereafter.

**Priority:** P0 platform programme.

**Expected ROI:** Foundational; enables revenue packaging, trials, upsell and safer delivery.

**References:** `platform/licensing/*`; `security/JwtTokenProvider.java`; `socops/controller/*`; migration `V97__module_licensing.sql`.

---

## F-005 — Module boundaries are not executable

**Category:** Enterprise Architecture  
**Severity:** High  
**Current State:** Static scan found 734 direct cross-module Java imports across 136 directed pairs and 14 bidirectional pairs. No architecture/boundary test was found.

**Problem:** Package separation depends on developer memory. Modules can directly import peer services, entities and repositories.

**Root Cause:** Functional delivery outpaced bounded-context governance; the `platform` package became an integration centre.

**Impact:** A change in one module can break unrelated modules; extraction and independent release become expensive.

**Business Impact:** Slower roadmap, higher regression cost and weaker per-module economics.

**Technical Impact:** Cycles, shared transactions, unclear ownership and difficult testing.

**UX Impact:** Duplicated or inconsistent object representations appear across pages.

**Sales Impact:** Buyer architecture review will reject “independent module” claims.

**Recommendation:** Add ArchUnit/import ratchet; classify dependencies; publish kernel/owner contracts; replace peer repository access with APIs/events/read models; reduce cycles before moving code.

**Alternative Solutions:** Immediate microservices; accept monolith indefinitely.

**Trade-offs:** Ratcheted modularisation is slower to make visually impressive but far safer and cheaper than a rewrite.

**Estimated Effort:** 2 weeks to establish controls; 3–6 months incremental remediation.

**Priority:** P1 after control-integrity P0s.

**Expected ROI:** High recurring reduction in change and packaging cost.

**References:** baseline import scan; `Architecture/MODULE_INDEPENDENCE_AND_PACKAGING.md`.

---

## F-006 — Product information architecture exposes implementation sprawl

**Category:** UX / Product  
**Severity:** High  
**Current State:** More than 300 routes are centrally registered; dashboards, registers, lifecycle pages, specialist tools and micro-modules compete for attention.

**Problem:** Navigation presents platform breadth instead of persona outcomes and installed-package work.

**Root Cause:** Screens were appended by module and remediation slice without a governing information architecture.

**Impact:** Users struggle to know where to begin; training and implementation effort rise.

**Business Impact:** Lower adoption and poor first-line participation.

**Technical Impact:** Central router/navigation changes have large blast radius.

**UX Impact:** Cognitive overload, duplicate routes, tab-heavy workspaces and inconsistent hierarchy.

**Sales Impact:** Demonstrations feel like feature tours rather than an enterprise operating system.

**Recommendation:** Implement role-specific shells, My Work, Assurance, installed Workspaces, Executive and Administration. Generate routes/navigation from package manifests and consolidate object workspaces.

**Alternative Solutions:** Improve search only; keep menus but add favourites.

**Trade-offs:** Search/favourites are helpful but do not fix conceptual overload. IA redesign requires migration analytics and user testing.

**Estimated Effort:** 8–12 weeks in staged releases.

**Priority:** P1.

**Expected ROI:** Very high for adoption, sales demo quality and support reduction.

**References:** `tss-grc-ui-v2/src/App.tsx`; `UX/UX_ARCHITECTURE.md`.

---

## F-007 — Arabic completeness claims exceed enforcement

**Category:** UX / Data Quality / Saudi Readiness  
**Severity:** High  
**Current State:** Change user stories claim backend-enforced Arabic title. `ChangeRequest.title_ar` is nullable, create accepts the entity without validation, and the UI does not require `titleAr`.

**Problem:** Material records may be incomplete in the primary Saudi language despite documentation claiming otherwise.

**Root Cause:** Bilingual policy is a convention, not a shared validated DTO/content contract.

**Impact:** Arabic reports, decisions and regulator packs can be incomplete.

**Business Impact:** Saudi customer credibility and operational clarity suffer.

**Technical Impact:** Validation may fail late at database level for English while Arabic is silently absent.

**UX Impact:** Users receive inconsistent required-field behaviour and unclear translation responsibility.

**Sales Impact:** Undermines the central Arabic-first differentiator.

**Recommendation:** Define material bilingual field policy; enforce through DTO validation and domain rules; add translation status; create Arabic/English contract and visual tests.

**Alternative Solutions:** Tenant-configurable Arabic requirement; automatic translation.

**Trade-offs:** Configurable exceptions are appropriate for non-material content. Machine translation may draft but cannot be authoritative.

**Estimated Effort:** 2 weeks for shared pattern and change module; incremental rollout.

**Priority:** P0 for decision records, P1 platform-wide.

**Expected ROI:** High Saudi differentiation and reduced report rework.

**References:** `change_mgmt/entity/ChangeRequest.java`; `ChangeManagementController.java`; `pages/changes/ChangesPage.tsx`; `PLATFORM_USER_STORIES.md`.

---

## F-008 — Export authorization violates least privilege

**Category:** Security / Data Loss Prevention  
**Severity:** High  
**Current State:** Change and CAB-pack exports require `CHANGE:READ`; `CHANGE:EXPORT` exists but is unused for these endpoints.

**Problem:** Interactive read access implicitly grants bulk extraction.

**Root Cause:** Export was treated as presentation of reads rather than a higher-volume data action.

**Impact:** Confidential change, evidence and decision data can be extracted by overly broad roles.

**Business Impact:** Increased insider and accidental disclosure risk.

**Technical Impact:** Permission catalogue contains dead or misleading semantics.

**UX Impact:** Users are not warned or required to justify high-sensitivity export.

**Sales Impact:** Weakens DLP and least-privilege answers during security review.

**Recommendation:** Require package `EXPORT`; apply same row/field filters as UI; add export manifests, classification, watermarking where appropriate and audit.

**Alternative Solutions:** Remove export; require administrator for all exports.

**Trade-offs:** Removal harms legitimate assurance. Admin-only creates bottlenecks. Purpose-built permission is proportionate.

**Estimated Effort:** 2–4 days plus permission tests.

**Priority:** P0 security quick win.

**Expected ROI:** High risk reduction for low effort.

**References:** `reporting/controller/ExportController.java:125`; migration `V301`.

---

## F-009 — Governance core lacks direct adversarial test coverage

**Category:** Quality / Security Assurance  
**Severity:** High  
**Current State:** Full suite passes, but baseline module inventory shows zero direct tests under `module/admin`; no tests were found for `CommitteeService` or `CommitteeResolutionService`.

**Problem:** The system's most consequential meeting, quorum and vote logic lacks a dedicated regression and abuse suite.

**Root Cause:** Test volume grew around delivered slices; coverage and criticality were not governed together.

**Impact:** Critical governance defects passed 2,316 green tests.

**Business Impact:** Releases can silently invalidate decision controls.

**Technical Impact:** Refactoring committee code is unsafe.

**UX Impact:** The UI may communicate success for an invalid decision.

**Sales Impact:** Test-count claims are discounted by technical assessors.

**Recommendation:** Build authenticated multi-principal tests for membership, COI, quorum, duplicate/forged votes, minutes SoD, tenant isolation and concurrency; add critical-logic coverage gates.

**Alternative Solutions:** End-to-end tests only; manual UAT only.

**Trade-offs:** Unit/domain tests give speed; API/live tests prove wiring. Both are required.

**Estimated Effort:** 1–2 weeks.

**Priority:** P0.

**Expected ROI:** Very high; prevents recurrence of critical governance failures.

**References:** baseline module test inventory; `REMEDIATION_MASTER_PLAN.md`.

---

## F-010 — Canonical ownership is fragmented across overlapping modules

**Category:** Data / Domain Architecture  
**Severity:** High  
**Current State:** Audit exists in `audit_eng` and `audit_lc`; approvals span `approvals`, workflow chains and committee decisions; TPRM/SCM/outsourcing overlap; privacy/data-officer/dataclass overlap; several `*_lc` modules duplicate lifecycle concerns.

**Problem:** The same business concept can have multiple stores, services or user surfaces.

**Root Cause:** Parallel feature programmes created local solutions before a canonical object council existed.

**Impact:** Inconsistent state, duplicate entry and fragile reporting.

**Business Impact:** Customers cannot confidently identify the authoritative record.

**Technical Impact:** More joins, bridges, migrations and reconciliation.

**UX Impact:** Multiple pages appear to perform the same job with different depth.

**Sales Impact:** Increases implementation complexity and weakens “single source of truth”.

**Recommendation:** Approve an object ownership catalogue; designate authorities; migrate duplicates or convert them into projections/snapshots; prohibit new duplicate stores.

**Alternative Solutions:** Keep all and add synchronisation; merge everything into universal tables.

**Trade-offs:** Migration is disruptive but finite. Permanent synchronization is expensive. Universal tables erase domain rules.

**Estimated Effort:** 4 weeks for decisions; multiple quarters for migration.

**Priority:** P1.

**Expected ROI:** High reduction in data and UX complexity.

**References:** `Modules/SOURCE_MODULE_INVENTORY.md`; current module tree.

---

## F-011 — Accessibility conformance is unproven

**Category:** UX / Government Acceptance  
**Severity:** High  
**Current State:** Accessibility was previously reviewed and many fixes may exist, but no current independent WCAG 2.2 AA conformance report or assistive-technology evidence was available.

**Problem:** Government and inclusive-access claims cannot be accepted without journey-level evidence.

**Root Cause:** Accessibility has been treated as technical findings rather than a continuous product quality attribute.

**Impact:** Keyboard, screen-reader, zoom, chart and Arabic mixed-direction failures may remain.

**Business Impact:** Excludes beneficiaries and may fail government procurement/acceptance.

**Technical Impact:** Late remediation becomes expensive across shared components.

**UX Impact:** Users with visual, motor or cognitive needs may not complete tasks.

**Sales Impact:** Government readiness remains conditional.

**Recommendation:** Adopt WCAG 2.2 AA; automate component checks; commission independent Arabic/English audit; test five critical journeys with NVDA/keyboard/zoom and retain evidence.

**Alternative Solutions:** Target WCAG 2.1 AA; test only public portals.

**Trade-offs:** WCAG 2.2 is stricter but more future-proof. Internal enterprise users also require accessibility.

**Estimated Effort:** 6–10 weeks depending on audit results.

**Priority:** P1, blocking government final acceptance.

**Expected ROI:** High market access and design quality.

**References:** DGA accessibility guide; `UX/ARABIC_AND_SAUDI_UX_STANDARD.md`.

---

## F-012 — Saudi regulatory content lacks a governed release lifecycle

**Category:** Saudi Readiness / Product Operations  
**Severity:** High  
**Current State:** The platform has numerous Saudi framework mappings and seeded content. Several historical documents explicitly note items that require verification.

**Problem:** Content freshness, authoritative language, applicability and mapping review are not represented as a standing product operation.

**Root Cause:** Regulatory content was delivered as product scope and migrations rather than managed content with independent version lifecycle.

**Impact:** Customers may assess against stale or unofficial requirements.

**Business Impact:** False assurance and costly implementation correction.

**Technical Impact:** Regulatory updates may require code/migration release and create tenant inconsistency.

**UX Impact:** Users cannot easily see source, effective date, supersession or mapping confidence.

**Sales Impact:** Saudi differentiation becomes a liability if content cannot be warranted.

**Recommendation:** Establish Saudi Content Office and versioned content packs with official source, Arabic text, applicability, review, activation and impact workflow.

**Alternative Solutions:** Integrate a third-party regulatory-content provider; customer-managed content only.

**Trade-offs:** Internal content provides differentiation but recurring cost. External providers accelerate breadth but may lack Saudi depth. A hybrid is preferred.

**Estimated Effort:** 6–8 weeks for platform/content model; ongoing operation.

**Priority:** P1.

**Expected ROI:** Very high recurring differentiation and customer trust.

**References:** `PROJECT_CHRONICLE.md`; `PLATFORM_ARCHITECTURE_PACK.md`; official sources in `Saudi/`.

---

## F-013 — Documentation completion markers are not evidence-safe

**Category:** Governance / Product Management  
**Severity:** High  
**Current State:** User stories mark capabilities complete that code disproves, including Arabic enforcement and export authorization. Architecture maturity grades overstate Change/CAB control integrity.

**Problem:** Documentation is treated as truth despite contradictory executable evidence.

**Root Cause:** Completion means implementation existence, not the multi-gate enterprise-ready definition.

**Impact:** Roadmaps, sales and acceptance decisions use unreliable status.

**Business Impact:** Misallocation of investment and reputational risk.

**Technical Impact:** Critical gaps are omitted from remediation plans.

**UX Impact:** Claimed journeys may not work as described.

**Sales Impact:** Due-diligence contradictions damage credibility beyond the individual defect.

**Recommendation:** Adopt truth labels and evidence hierarchy; link every complete story to tests/probes; reopen contradicted stories; generate release evidence packs.

**Alternative Solutions:** Archive old documents; maintain only generated documentation.

**Trade-offs:** Archiving loses rationale; generated docs cannot capture product truth alone. Governed mixed evidence is required.

**Estimated Effort:** 1–2 weeks initial reconciliation; continuous.

**Priority:** P0 governance.

**Expected ROI:** High decision accuracy and buyer confidence.

**References:** `PLATFORM_USER_STORIES.md`; `PLATFORM_ARCHITECTURE_PACK.md`; this register F-001/F-007/F-008.

---

## F-014 — Approval primitives are duplicated

**Category:** Architecture / Governance  
**Severity:** High  
**Current State:** Generic approvals, workflow approval chains, domain-local approvals, committee votes and signatures overlap.

**Problem:** Actor, delegation, SoD, quorum, expiry and audit semantics can differ by module.

**Root Cause:** Capabilities were added per programme without one decision-record model.

**Impact:** Repeated security defects and inconsistent user experience.

**Business Impact:** Approval policy changes require many implementations.

**Technical Impact:** More services, tables, schedulers and edge cases.

**UX Impact:** Approval inbox and domain screens may disagree about status/next action.

**Sales Impact:** Weakens enterprise workflow/configurability story.

**Recommendation:** Define one kernel decision/authority contract with specialised policies for sequential approval, quorum vote and signature; migrate incrementally.

**Alternative Solutions:** Keep engines but expose a federated inbox; replace all with an external BPM product.

**Trade-offs:** Unified contract is more important than one physical engine. Immediate wholesale replacement is risky.

**Estimated Effort:** 4–6 weeks for model and adapter; multi-quarter migration.

**Priority:** P1.

**Expected ROI:** High security consistency and lower workflow cost.

**References:** `module/approvals`; `module/workflow/approval_chains`; `admin/committee`; `module/signature`.

---

## F-015 — Large frontend units obstruct coherent UX and safe change

**Category:** Frontend Architecture / UX  
**Severity:** Medium-High  
**Current State:** Major pages combine thousands of lines and many sub-workflows; examples include ESG and NDI, while the main production chunk exceeds the configured size warning.

**Problem:** One file/page owns data, state, forms, tables, tabs and domain rules for many tasks.

**Root Cause:** Slice delivery appended functionality to existing pages for speed.

**Impact:** Regression risk, poor code splitting and inconsistent components.

**Business Impact:** UX iteration slows and defects become expensive.

**Technical Impact:** Hard testing, merge conflicts and unnecessary bundle load.

**UX Impact:** Tab sprawl and weak progressive disclosure.

**Sales Impact:** Visual polish and responsiveness lag product breadth.

**Recommendation:** Split by journey and package route; move domain orchestration into hooks/services; use shared workspace/decision patterns; define route-level loading budgets.

**Alternative Solutions:** Refactor files without changing pages; increase bundle warning threshold.

**Trade-offs:** File-only refactor improves maintenance but not usability. Raising the threshold hides the problem.

**Estimated Effort:** 2–3 weeks per flagship workspace.

**Priority:** P1/P2 by sales journey.

**Expected ROI:** Medium-high through faster UX improvement and reduced regressions.

**References:** `pages/esg/EsgPage.tsx`; `pages/ndi/NdiPage.tsx`; `pages/changes/ChangesPage.tsx`; Vite build warning.

---

## F-016 — Production-like operational proof is incomplete

**Category:** Operations / Acceptance  
**Severity:** Medium  
**Current State:** Automated builds pass and deployment documentation is extensive. During this assessment the local backend/frontend were not running, and no current witnessed production-scale performance, failover or restore evidence was available.

**Problem:** Operability is documented more strongly than independently demonstrated.

**Root Cause:** Evidence is distributed across sessions/environments and not assembled as a release acceptance pack.

**Impact:** Capacity, recovery, optional infrastructure and rollback assumptions remain uncertain.

**Business Impact:** Go-live and SLA risk.

**Technical Impact:** Unknown bottlenecks and recovery incompatibilities.

**UX Impact:** Users may experience outages or inconsistent real-time behaviour.

**Sales Impact:** Enterprise and government acceptance remains conditional.

**Recommendation:** Define workload model; run production-like load, backup/restore, failover, upgrade/rollback and dependency-failure exercises; retain signed evidence per artifact.

**Alternative Solutions:** Contractual best-effort pilot; managed cloud only.

**Trade-offs:** Full proof costs environment time but prevents expensive failures. Controlled pilot can proceed only with explicit risk.

**Estimated Effort:** 3–6 weeks for first evidence pack.

**Priority:** P1 before general availability.

**Expected ROI:** High risk reduction and procurement confidence.

**References:** `DEPLOYMENT.md`; `PROJECT_CHRONICLE.md`; assessment verification log.

