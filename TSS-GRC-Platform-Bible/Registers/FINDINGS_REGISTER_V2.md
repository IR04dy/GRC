# Findings Register V2 — Independent Enterprise Product Review

**Date:** 2026-07-25 · **Tree:** `master` `99a719da` · **Assessor:** independent review team
(CPO / Enterprise Architect / CTO / Principal UX / GRC SME / Security Architect / Saudi regulatory /
government acceptance committee / enterprise customer / technical due-diligence investor).

**Evidence rule.** Every finding cites `Appendix/MEASURED_BASELINE.md` by section. Statements that are
judgement rather than measurement are marked **OPINION**. Competitor statements describe **market
positioning and licensing models** — publicly observable commercial behaviour — not verified internals.

**Relationship to the previous register.** `Registers/FINDINGS_REGISTER.md` (599 lines, vendor
foundation pack) is *superseded on measured facts* and retained for traceability. Where it and this
document disagree, this one cites a reproducible number; see `Governance/BIBLE_VALIDATION_LOG.md`.

## Severity scale

| | Meaning |
|---|---|
| **P0 Blocker** | Prevents a stated commercial objective, or ships a known-false artefact to a customer/regulator |
| **P1 Critical** | Material risk to a live deal, an audit, or production integrity |
| **P2 High** | Structural debt that compounds; fix before scaling the team or the customer base |
| **P3 Medium** | Real but contained; schedule it |

## Summary

| ID | Title | Cat | Sev |
|---|---|---|---|
| **F2-21** | **Committee votes are recorded under a caller-supplied name** — recusal, quorum and SoD all bypassable | **Security** | **P0** |
| F2-01 | An accurate 58-module inventory exists and no control reads it; 24 is still the operative figure | Product | **P0** |
| F2-02 | Global search, executive dashboard and board pack render mock data | Trust | **P0** |
| F2-03 | The Saudi-differentiating modules are the untested ones | Assurance | **P0** |
| F2-04 | A complete entitlement subsystem exists and is wired to 8.6% of controllers — dev tenants 2/3 prove the bypass | Product/Arch | **P0** |
| F2-05 | 21 modules guard by role name, so tenant RBAC has no effect on them | Security/Product | **P1** |
| F2-06 | Nine modules persist into a schema they do not own | Architecture | **P1** |
| F2-07 | 144 routed pages are absent from navigation | UX | **P1** |
| F2-08 | `reporting` and `audit_committee` are god-consumers; no boundary is enforced | Architecture | **P1** |
| F2-09 | Dev and prod schemas are produced by two different mechanisms | Ops/Integrity | **P1** |
| F2-10 | Production runs code with two known, already-fixed defects | Ops | **P1** |
| F2-11 | Bilingual delivery is split across two mechanisms; 439 strings are hardcoded English | UX/i18n | **P1** |
| F2-12 | `ndi`: 127 endpoints, 82 writes, 11 tests, behind a 4,970-line screen | Assurance | **P1** |
| F2-13 | The authorisation ratchet measures coverage, not tightness | Security | **P2** |
| F2-14 | Config-gated controls with no config-health check (a defect *class*) | Governance | **P2** |
| F2-15 | 3,650 lines of fabricated records ship in the production bundle | Trust | **P2** |
| F2-16 | Two page monoliths carry 7.7% of all frontend code | Maintainability | **P2** |
| F2-17 | Performance evidence covers **1 of 2,160 endpoints** (and is good on that one) | Scalability | **P2** |
| F2-18 | The governance core is assured at unit level only | Assurance | **P2** |
| F2-19 | Documentation asserts numbers nothing verifies — including this project's own plans | Governance | **P2** |
| **F2-22** | 216 write endpoints bind a JPA entity from the request body; defence verified in only one | Security | **P2*** |
| **F2-23** | **No role holds `ADMIN:WRITE`, so only `PLATFORM_ADMIN` can record a committee vote** | Security | **P1** |
| F2-20 | `datagov` — a documented module with no implementation | Product | **P3** |

\* F2-22's severity is provisional pending the audit it recommends — the pattern's *extent* is measured, its *exploitability* is not.

---

## F2-01 · 34 undocumented module packages; one module is empty

| Field | Content |
|---|---|
| **ID** | F2-01 |
| **Category** | Product management / inventory |
| **Severity** | **P0 Blocker** |
| **Current State** | `CLAUDE.md` (the engineering instruction file), `PLATFORM_ARCHITECTURE_PACK.md` and this project's own remediation plans enumerate **24** backend modules. The tree contains **58** module packages (57 with code), **186,285 LoC**, **417 entities**, **2,160 endpoints**. Baseline §0, §1. <br><br>**Correction in the platform's favour:** `Modules/SOURCE_MODULE_INVENTORY.md` in this Bible **already documents all 58** with per-module Java file counts, and its counts **match this review's independent measurement exactly** (`admin` 48, `bcm` 198, `platform` 100, `socops` 101, `sust` 140, `risk` 99, `evidence` 55, `integrations` 68, `datagov` 1). It also already flags `datagov` as a placeholder and identifies the zero-test modules. That document is **verified accurate** and is the single best inventory artefact the project has. |
| **Problem** | The inventory *exists* and is correct — and **nothing consumes it**. The 24-module figure still governs the engineering instruction file, the architecture pack, the RBAC catalogue scope, the test-bar plans and every scope statement, so 34 packages remain outside the reach of every cross-cutting programme. An accurate inventory that no control reads is documentation, not governance. |
| **Root Cause** | Modules were added feature-first without a registration step, and the one accurate inventory is a hand-written Markdown table with no mechanical link to the build. Nothing fails when a package appears with no catalogue entry, no permission, no tests and no documentation — and nothing fails when a document disagrees with the filesystem. |
| **Business Impact** | You cannot price, licence, scope, support or contractually warrant an inventory you have not enumerated. Every commercial artefact built on "24 modules" understates the product by 40% — and, worse, the unlisted 34 include the Saudi-specific differentiators that justify premium pricing. |
| **Technical Impact** | Cross-cutting programmes (test bar, RBAC catalogue, boundary tests, i18n coverage, migration ownership) were all sized against 24 modules and silently skip 34. |
| **UX Impact** | Unlisted modules have unlisted screens — directly feeds F2-07 (144 pages absent from navigation). |
| **Sales Impact** | An RFP asking "list your modules and their maturity" cannot be answered truthfully today. A due-diligence reviewer who finds 58 where the datasheet says 24 stops trusting every other number in the datasheet — including the ones that are correct. |
| **Recommendation** | Promote `SOURCE_MODULE_INVENTORY.md` from prose to a **machine-readable Module Registry** — `module-registry.yaml`, seeded directly from that table (the content is already correct, so this is transcription, not analysis): per package — owner, SKU, maturity tier, permission prefix, schema, public API surface, test floor, FE route root, docs link. A CI check fails the build when a `module/*` package exists without a registry entry, when a registry entry's declared floors are unmet, and **when `CLAUDE.md` or any datasheet states a module count that disagrees with it**. Generate the datasheet, the licence matrix and the module scorecard **from** the registry. Also correct the 24-module figure wherever it appears. |
| **Alternatives** | (a) Maintain the list in Markdown — rejected: an accurate Markdown inventory already exists and did not prevent this finding, which is the proof that prose is not a control. (b) Derive the list at runtime from Spring bean scanning — rejected: describes what exists, not what is *intended*, so it can never fail the build. (c) Consolidate 58 → 24 by merging small packages — attractive for coherence, and `SOURCE_MODULE_INVENTORY.md` already proposes the target owners for it; but it is a large refactor that does not by itself create the missing control. |
| **Trade-offs** | The registry adds one mandatory step per new module and will fail builds during adoption. That is the point: it converts an invisible omission into a visible, cheap, immediate failure. |
| **Effort** | **M** — 3–5 days: registry schema, 58 entries backfilled from `coupling.json`, one CI test. |
| **Priority** | **Immediate** — it is the prerequisite for F2-03, F2-04, F2-05 and the module scorecard. |
| **Expected ROI** | Very high. Unblocks the entire per-module commercial model; the datasheet becomes generated and therefore always true. |
| **References** | Baseline §0, §1, §1.1. Competitors: ServiceNow (scoped applications with an explicit application record), Archer (licensed *use cases*), OneTrust (separately licensed modules), AuditBoard (distinct named products) — all four treat the module list as governed product data, not prose. |

---

## F2-02 · Global search, executive dashboard and board pack render mock data

| Field | Content |
|---|---|
| **ID** | F2-02 |
| **Category** | Trust / demo integrity / audit evidence |
| **Severity** | **P0 Blocker** |
| **Current State** | 44 mock-import statements across 29 files. Concentrated in: `lib/searchIndex.ts` **7 imports** (global search), `pages/dashboard/widgets.tsx` **4** (executive dashboard), `lib/boardPack.ts` **3** (board pack). Baseline §5, §5.1. |
| **Problem** | The three surfaces an executive or acceptance committee opens **first** are fed from fabricated records. Global search returns invented compliance, risk, evidence, TPRM and privacy items. Dashboard widgets show invented figures. The board pack — an artefact that leaves the building and reaches a real board — is assembled partly from mock data. |
| **Root Cause** | Mocks were the development scaffold and no rule ever forbade shipping them. There is no lint rule, no CI check, and no "backend-down must show empty state" test. The fallback is silent by design, which is precisely what makes it dangerous. |
| **Business Impact** | A board that receives a pack containing fabricated risk figures is a governance incident in a **governance product**. For a GRC vendor this is category-fatal: the product's only real claim is that its records are trustworthy. |
| **Technical Impact** | Low — the fix is mechanical. The severity is entirely about what the artefact asserts. |
| **UX Impact** | Users cannot distinguish "no data yet" from "data invented". Empty states exist in the design system (`EmptyState` 109×, `InlineEmpty` 133×) and are simply not used on these paths. |
| **Sales Impact** | Highest-consequence finding in the register. A demo that shows rich search results and a full dashboard on an empty tenant will be discovered — and the discovery reframes every other capability claim as possibly fake. |
| **Recommendation** | Three steps, in order: (1) delete the mock import from `searchIndex.ts`, `widgets.tsx`, `boardPack.ts` and render the existing `EmptyState`/error components instead; (2) add the CI rule — no import matching `/mock/i` outside `**/*.test.*`; (3) add a **backend-down acceptance test**: with the API stopped, every register renders an error state and **zero rows**. Ship (1) this week; (2) and (3) make it permanent. |
| **Alternatives** | (a) Keep mocks behind an `import.meta.env.DEV` guard — better than today but still leaves fabricated data in the source and one config flag from production. (b) Replace mocks with a seeded demo tenant — **recommended in addition**: gives sales rich data that is *real*, auditable and clearly labelled, without lying in the product. |
| **Trade-offs** | Sales demos on an empty tenant will look sparse. Answer: a seeded demo tenant, not fiction in the code path. |
| **Effort** | **S** for the three critical files (1–2 days); **M** for the remaining 26 files and the CI rule. |
| **Priority** | **Immediate** — before the next demo. |
| **Expected ROI** | Very high, asymmetric: small effort, removes an existential trust risk. |
| **References** | Baseline §5.1. `UNIFIED_IMPLEMENTATION_PLAN.md` W2-1 (which correctly identified these same three files as the priority — and mis-stated the total as 53). |

---

## F2-03 · The Saudi-differentiating modules are the untested ones

| Field | Content |
|---|---|
| **ID** | F2-03 |
| **Category** | Assurance / regulatory |
| **Severity** | **P0 Blocker** |
| **Current State** | 19 packages / **20,503 LoC (11.0% of module code)** have **zero tests**: `sharia` 2,234 · `data_officer` 2,373 (37 endpoints) · `openbanking` 1,913 · `rrp` 1,910 · `cpty` 1,688 · `outsourcing` 1,655 · `regreporting` 1,628 · `fraud` 1,301 · `reginspect` 1,160 · `dataclass` 988 · `opres` 950 · `nca_ecc` 368 + 7 supporting. Meanwhile `bcm` has 395 tests, `sust` 200, `platform` 169, `risk` 147. Baseline §6. |
| **Problem** | Coverage is inversely correlated with differentiation. The modules that justify a premium in Saudi Arabia — SAMA outsourcing, SAMA Open Banking, recovery & resolution planning, Sharia governance, regulator-inspection management, NCA ECC, PDPL data-officer workspace, NDMO data classification — have **no automated proof they work and nothing that would catch a regression**. The generic modules every competitor also sells are the well-tested ones. |
| **Root Cause** | Test investment followed development attention, which followed the large modules. No per-module test floor exists (a consequence of F2-01: no registry to attach a floor to). |
| **Business Impact** | The premium claim is the least assured part of the product. A regulator-facing defect in `outsourcing` or `nca_ecc` damages exactly the reputation the platform is sold on. |
| **Technical Impact** | 20.5 kLoC where any refactor is unsafe: no test will fail. This blocks the module extraction and boundary work these very modules need. |
| **UX Impact** | Untested modules are where hollow screens hide — `data_officer` has 37 endpoints, 1 entity, 0 tests. |
| **Sales Impact** | A technical due-diligence reviewer computes coverage per module in minutes. "Our Saudi modules are our differentiator" followed by "and they have zero tests" ends the diligence conversation. |
| **Recommendation** | Tiered, enforced test floors in the module registry (F2-01): **Tier 1 (regulator-facing / sold standalone)** — service-level tests for every state transition and authorisation branch, plus one live HTTP probe per write endpoint; **Tier 2** — happy path + authorisation per endpoint; **Tier 3 (internal/support)** — smoke only. Start with `outsourcing`, `nca_ecc`, `sharia`, `data_officer` — highest regulatory salience per LoC. CI fails when a Tier-1 module drops below its floor. |
| **Alternatives** | (a) Uniform coverage % target — rejected: rewards testing trivial getters in big modules and still leaves `nca_ecc` at zero. (b) Manual test scripts — rejected: no regression protection, and unverifiable in diligence. (c) Contract tests generated from the 84 user stories — **recommended as a follow-on**; it makes stories executable (`W3-1`). |
| **Trade-offs** | Real cost: roughly 250–400 tests for the four starting modules. Cheaper than one regulator-visible failure. |
| **Effort** | **L** overall; **M** for the first four modules (2–3 weeks). |
| **Priority** | **Immediate** for the four named; scheduled thereafter. |
| **Expected ROI** | High — converts the differentiator from a claim into evidence, which is what both regulators and diligence reviewers actually buy. |
| **References** | Baseline §6, §7.4 (JaCoCo 37.1% instruction / 26.0% branch / 38.5% line at 2026-06-18 — platform-wide, never per-module). Competitors: none of the five majors ship a Sharia-governance or SAMA-outsourcing module — this is genuinely defensible ground, which is exactly why leaving it unassured is the costliest choice in the register. |

---

## F2-04 · No per-module entitlement — the per-module SKU strategy is technically unimplementable

| Field | Content |
|---|---|
| **ID** | F2-04 |
| **Category** | Product architecture / commercial |
| **Severity** | **P0 Blocker** (against the explicitly stated objective of selling each module separately) |
| **Current State** | **A complete, well-built entitlement subsystem already exists — and is wired to 8.6% of the platform.** `module/platform/licensing/` provides: `platform.organisation_licenses` (`module_code`, `tier` FREE│PRO│ENTERPRISE, `seat_count`, `valid_from`, `valid_to`, `enabled`, `notes`), `LicensingService` (Caffeine-cached, tier validation, 20 canonical `KNOWN_MODULES`), the `@RequireModule` annotation, `LicenseGuardAspect` (AOP, **fail-closed** when no tenant is in scope, throws `ModuleDisabledException` → 403), a `LicensingController`, an `enabled_modules` JWT claim issued at login, an FE client `lib/licensing.ts`, and an admin screen at `/admin/licensing`. Dev data holds **22 licence rows**: org 1 has 20 modules enabled; **orgs 2 and 3 have only `SOCOPS`**. <br><br>**Adoption, measured:** `@RequireModule` appears on **22 of 255 controllers (8.6%)**, *all* in `socops`. `KNOWN_MODULES` has **20 codes against 58 module packages**. The FE gate helper `moduleEnabled()` — whose own javadoc says "the sidebar / route guard can use" — has **zero callers**, and the `enabled_modules` JWT claim is **never consumed** by any component. |
| **Problem** | The mechanism is not missing; it is **unadopted**, and the consequence is visible in the platform's own dev data: **orgs 2 and 3 are licensed for exactly one module and can freely use all 58**, because no other controller is guarded. So today the licence table is a record of intent that the runtime does not honour — and 38 of 58 packages have no `module_code` at all, so they cannot be entitled even in principle. The FE half is written and never wired, so every user sees the whole platform regardless of licence. |
| **Root Cause** | The subsystem was built as part of one framework-intersection initiative (F9) and proven on `socops` — the module being piloted at the time — and adoption was never carried to the other 57. Nothing fails when a controller lacks `@RequireModule`, nothing fails when a package has no licence code, and nothing fails when a gate helper has no callers. Classic pilot-without-rollout, and the same structural pattern as F2-14: a control that exists in code and not in force. |
| **Business Impact** | The per-module commercial model is unenforceable today — but the distance to enforceable is **much shorter than it appears**, which changes the investment case: this is a rollout, not a build. |
| **Technical Impact** | Low risk. The hard parts — schema, caching, aspect, fail-closed semantics, tier/seat/validity model, admin UI — are done and running in production for one module. |
| **UX Impact** | Every user sees all 329 pages regardless of licence, compounding the navigation problem (F2-07) for every customer who bought three modules. Wiring `moduleEnabled()` into navigation fixes both at once. |
| **Sales Impact** | "Buy Risk now, add Privacy later" is currently unenforceable — yet the **tier and seat_count columns already exist**, so the commercial model the sales team needs is already modelled in the database. |
| **Recommendation** | **Adopt what exists; do not rebuild it.** (1) Extend `KNOWN_MODULES` to all 58 codes, sourced from the F2-01 module registry so the two cannot diverge. (2) Apply `@RequireModule` at **class level** to every business-module controller — mechanical, and it is one annotation per controller. (3) **Wire the FE gate**: load `myEnabled()` at app start and consume it in navigation and route registration via the existing `moduleEnabled()` helper. (4) Add the test that would have caught this: *a tenant licensed only for module X receives 403 from module Y's endpoints* — run it against orgs 2 and 3, which are already seeded for exactly this scenario. (5) Decide explicitly whether `PLATFORM_ADMIN` bypasses entitlement — recommendation: **no** for business modules, yes for platform administration. (6) Add a registry ↔ `KNOWN_MODULES` ↔ `@RequireModule` coverage check to CI so adoption cannot silently stall again. |
| **Alternatives** | (a) Build a new entitlement layer in `ModulePermissionEvaluator` — **rejected: an equivalent mechanism already exists**, and a second one would create two sources of truth. (Enforcing in the evaluator *instead of* the aspect is a legitimate design variant, but the aspect is already proven and fail-closed.) (b) Enforce only in the FE — rejected: cosmetic and trivially bypassed. (c) Separate deployments per customer — rejected: multiplies operational cost and F2-06's shared schemas make it unsound. |
| **Trade-offs** | Applying `@RequireModule` broadly will **break access for tenants whose licence rows are incomplete** — orgs 2 and 3 would immediately lose 57 modules. That is the mechanism working, and it must be sequenced with a licence-backfill migration, not discovered in production. |
| **Effort** | **S–M** — days, not weeks: `KNOWN_MODULES` extension, ~230 class-level annotations, one FE wiring change, one cross-tenant test, one backfill migration. Materially cheaper than the "build entitlement" estimate this finding originally carried. |
| **Priority** | **Immediate** if per-module sale is real; **defer explicitly** if the model is suite-only. Decision **D-3** — Baseline §9 question 2. |
| **Expected ROI** | **Highest ROI in the register after F2-21.** It converts an existing, tested, unused asset into the entire modular business model for a few days of mechanical work. |
| **References** | `module/platform/licensing/**`, `JwtTokenProvider` (`enabled_modules` claim), `tss-grc-ui-v2/src/lib/licensing.ts` (`moduleEnabled`, zero callers), `platform.organisation_licenses` (22 rows), migrations `V97` (seed) and `V150` (`SOCOPS`). **Correction:** this finding originally asserted that no entitlement mechanism existed anywhere. That was wrong — `Decisions/ADR-002` stated "current licensing infrastructure exists, but controller enforcement is not platform-wide", and **ADR-002 was accurate**. Competitors: ServiceNow licenses per scoped application, Archer per use case, OneTrust per module, AuditBoard per product — this platform's `tier`/`seat_count`/validity model is directly comparable in design; only its coverage is not. |

---

## F2-05 · 21 modules guard by role name, so tenant RBAC has no effect on them

| Field | Content |
|---|---|
| **ID** | F2-05 |
| **Category** | Security / product administrability |
| **Severity** | **P1 Critical** |
| **Current State** | **21 of 58** packages contain no `hasPermission(MODULE, ACTION)` call. They are guarded — verified by reading them — with hard-coded role lists: `sharia` → `hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','SHARIA_OFFICER','SHARIA_BOARD_CHAIR','CRO')`; `socops` (58 writes) → `hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')`; `outsourcing`, `openbanking`, `rrp`, `cpty`, `regreporting`, `reginspect`, `fraud`, `opres`, `aml`, `ccm`, `audit_committee`, `integrations`, `governance`, `consumer`, `dataclass`, `board_pack`, `knowledge`, `auth`. Baseline §4. |
| **Problem** | The platform's per-tenant RBAC (55-role persona matrix, `V272`) has **no effect** on these modules. A customer who creates the role "Sharia Reviewer" and grants it `SHARIA_WRITE` still receives **403**, because the code compares role *names*. The only way to grant access is to assign one of the vendor's built-in roles — which carries every other privilege that role implies. |
| **Root Cause** | Two authorisation idioms coexist and only one is enforced. The ratchet test accepts `hasAnyRole`, so the weaker idiom has never been challenged. Guard constants (`READ_GUARD`, `WRITE_GUARD`, `APPROVE_GUARD`) made the pattern easy to replicate — 21 modules did. |
| **Business Impact** | A customer cannot implement their own segregation of duties in these modules — in a **GRC product whose selling proposition is segregation of duties**. Least privilege is unachievable: granting Sharia access means granting `COMPLIANCE_OFFICER` or `CRO` wholesale. |
| **Technical Impact** | Role names are now compile-time coupled to 21 modules. Renaming or retiring a role silently changes access. No catalogue entry means no audit of who can do what. |
| **UX Impact** | The role-management UI shows permissions that do nothing for these modules — the admin screen misrepresents the system's real behaviour. |
| **Sales Impact** | Fails a standard enterprise security questionnaire ("can we define custom roles with granular permissions?" — answer today: for 37 of 58 modules). Blocks standalone module sale: a buyer of only `sharia` cannot administer it without platform-wide roles. |
| **Recommendation** | (1) Catalogue permissions for all 21 modules (one migration per batch, following the `V313` pattern already proven this session); (2) convert `hasAnyRole` → `hasPermission` and grant the new permissions to the roles previously named, so behaviour is **identical on day one** — this is a refactor, not a policy change, which is what makes it safe; (3) extend the ratchet into a **tightness ratchet**: count `hasAnyRole` occurrences in `module/**` and drive it to zero; (4) forbid new `hasAnyRole` in module code via the same test. |
| **Alternatives** | (a) Leave role guards and document the limitation — rejected: it is a functional gap in the core value proposition, not a documentation gap. (b) Map roles → permissions at runtime in the evaluator — clever, but hides the coupling instead of removing it and leaves the catalogue incomplete. |
| **Trade-offs** | ~21 migrations and ~150 annotation edits. Mechanically safe if grants mirror the existing role lists exactly; each module needs a two-user live probe (permitted role 200, `viewer` 403). |
| **Effort** | **L** in total; **S per module** — parallelisable, and the `V313`/`W0-5` slice already established the recipe end to end. |
| **Priority** | High — sequence immediately after F2-04, since both land in the evaluator. |
| **Expected ROI** | High — completes the RBAC promise, unblocks standalone module administration, and removes a standing security-questionnaire failure. |
| **References** | Baseline §4. `V272` (55-role matrix), `V313` (catalogue precedent). Competitors: ServiceNow, Archer, MetricStream and OneTrust all expose customer-definable roles bound to granular permissions; hard-coded role checks in product code would fail their own certification suites. |

---

## F2-06 · Nine modules persist into a schema they do not own

| Field | Content |
|---|---|
| **ID** | F2-06 |
| **Category** | Architecture / data ownership |
| **Severity** | **P1 Critical** (P0 if per-module deployment is contractual) |
| **Current State** | `incident` → schema **`cyber`** (2 entities) · `integrations` → **`platform`** (6) · `controlmap` → **`evidence`** · `workflow_submission` → `workflow` · `knowledge` → `kb` · `governance` → `platform` · `signature` → `signature` + `platform` · `auth`, `attachment` → `platform` (acceptable — kernel). Baseline §3. |
| **Problem** | A module that writes into a peer's schema cannot be migrated, backed up, exported, air-gapped, restored or licensed independently. `incident` cannot exist without `cyber`'s schema — so "buy Incident Management" implicitly ships `cyber`'s tables. |
| **Root Cause** | Schema names were chosen per feature era, not per bounded context, and no rule ever tied `@Table(schema=…)` to the owning package. `ddl-auto=none` plus hand-written migrations means nothing objects. |
| **Business Impact** | Undermines the modular sale at the data layer — the layer customers' own auditors and DBAs inspect. A Saudi bank's DBA reviewing a "Privacy module" purchase that creates tables in a `cyber` schema will ask why, and the answer is not defensible. |
| **Technical Impact** | Migrations for one module alter another's schema; ownership of a table cannot be determined from the code; per-module backup/restore is impossible. |
| **UX Impact** | None directly. |
| **Sales Impact** | Fails per-module data-residency, data-segregation and right-to-export questions common in Saudi financial-sector procurement. |
| **Recommendation** | Adop**ADR-011 Schema ownership = module ownership**: each module owns exactly one schema named after it; the kernel (`platform`) is the only shared schema and only kernel packages may write to it. Enforce with a test asserting every `@Entity`'s `schema` equals its module's declared schema from the F2-01 registry. Remediate in ownership order: **`incident`→`cyber` first** (2 entities, highest commercial salience), then `integrations` (6 entities out of the kernel), then `controlmap`. Rename via a migration that creates the new schema, moves the tables (`ALTER SCHEMA … TRANSFER`), and keeps a view at the old name for one release. |
| **Alternatives** | (a) Redefine `incident` and `cyber` as one bounded context and merge the packages — **legitimate and possibly better**: incident response genuinely belongs to cyber operations in most GRC products. Decide by *product* intent (is Incident a separate SKU?), not by code convenience. (b) Accept and document — only tenable under a suite-only licence model. |
| **Trade-offs** | Schema moves are the highest-risk migration class here; each needs the compatibility view and a rollback recipe. The `V312` column-drop this session set the precedent for a guarded, reversible destructive change. |
| **Effort** | **M** per module (migration + entity + probe); **S** for the enforcement test. |
| **Priority** | High — do the enforcement test **now** (cheap, stops new violations) and remediate per module as each is scheduled for standalone sale. |
| **Expected ROI** | Medium-high — the enforcement test is near-free and permanent; the moves pay off exactly when a module is sold alone. |
| **References** | Baseline §3. Competitors: ServiceNow scoped apps own their tables with cross-scope access explicitly declared and auditable — the direct analogue of this recommendation. |

---

## F2-07 · 144 routed pages are absent from navigation

| Field | Content |
|---|---|
| **ID** | F2-07 |
| **Category** | UX / information architecture |
| **Severity** | **P1 Critical** |
| **Current State** | **329** pages in **114** folders. `App.tsx` registers **328** routes in a 926-line flat table. `Sidebar.tsx` (765 lines) exposes **184** route entries. Baseline §5. |
| **Problem** | Roughly **144 routed screens are reachable only by typing a URL.** Either they are real capability the customer cannot find — value paid for and never delivered — or they are dead screens that should not ship. Both are serious, and today **nobody can tell which is which**, because no artefact maps routes to navigation to modules. |
| **Root Cause** | Routes and navigation are two hand-maintained lists with no consistency check. 114 folders for 58 modules means the folder taxonomy diverged from the module taxonomy (`pages/dashboard` *and* `pages/dashboards`; `pages/detail` holding 10 orphan detail screens). |
| **Business Impact** | Unfindable features generate support load and renewal risk; users conclude the platform cannot do things it can. |
| **Technical Impact** | 144 screens with no navigation entry are also, in practice, untested by any click-path and unowned. |
| **UX Impact** | **The platform's most serious UX defect.** A 184-item sidebar is already beyond navigable — competitors surface 8–15 top-level workspaces and reach depth by context, not by menu length. |
| **Sales Impact** | Demos rely on tribal knowledge of URLs. An evaluator exploring alone finds a fraction of the product. |
| **Recommendation** | (1) **Reconcile first, redesign second** — generate the route↔nav↔module matrix and classify all 144 as *promote / merge / retire*; publish it as `UX/ROUTE_RECONCILIATION.md`. (2) Adopt a **workspace IA**: 10–12 top-level workspaces (Risk, Compliance & Policy, Audit, Cyber & SOC, Resilience, Privacy, Third Party, Sustainability, Financial-Sector Regulation, Ethics & Conduct, Governance & Committees, Administration), each with a landing page that is *the* entry point; everything else becomes in-workspace navigation or a detail route. (3) Derive navigation **from the module registry + entitlements** (F2-01, F2-04) so a route with no registry entry cannot exist and an unentitled module cannot appear. (4) Add a CI test: every route has a nav path or an explicit `detailOnly: true`. |
| **Alternatives** | (a) Add all 144 to the sidebar — rejected: makes the primary defect worse. (b) Global search as the primary finding mechanism — rejected *while* search is mock-fed (F2-02); viable as a **secondary** mechanism afterwards, and worth doing (it is how ServiceNow and Atlassian handle scale). |
| **Trade-offs** | The workspace IA is a genuine redesign of navigation — but **not** of screens: 99% shared-component adoption (Baseline §5.3) means the pages themselves are reusable as-is. |
| **Effort** | **S** for the reconciliation matrix and the CI test (2–3 days — highest information value per hour in this register). **M–L** for the workspace IA. |
| **Priority** | Immediate for the matrix; the IA follows the entitlement work so it is built once. |
| **Expected ROI** | High — the matrix alone converts an unknown into a decision list, and may retire dozens of screens (reducing maintenance and the test surface). |
| **References** | Baseline §5, §5.3. Competitors: ServiceNow (workspaces + unified search), Archer (solution-area IA), OneTrust (module-scoped IA) — none exposes a 184-item flat menu. |

---

## F2-08 · `reporting` and `audit_committee` are god-consumers; no boundary is enforced

| Field | Content |
|---|---|
| **ID** | F2-08 |
| **Category** | Architecture / coupling |
| **Severity** | **P1 Critical** |
| **Current State** | `reporting` (5,107 LoC, **2 entities**) imports **8 peer modules over 74 edges**: `risk`×22, `platform`×15, `compliance`×13, `change_mgmt`×12, `ethics`×4, `policy`×4, `evidence`×3, `sust`×1. `audit_committee` (1,581 LoC) imports **10 modules**. `governance` (1,857 LoC) imports 8. Nothing in CI forbids any module importing any other. Baseline §2.2. |
| **Problem** | **Two distinct problems wear the same shape, and they have very different fixes — separating them is the whole value of this finding.** <br><br>**(a) A misplaced kernel utility, not a business dependency.** Eight modules import `reporting`, and inspection of every one of the 25 inbound edges shows they import **only the export toolkit**: `PdfTheme`, `PdfFonts`, `XlsxStyler`, `ExportNaming`, `RegisterExport`, `TabularExportRequest`, `TabularExportService`. `bcm`, `sust`, `iam`, `socops`, `ndi`, `cyber`, `ai` and `admin` do not depend on reporting *logic* at all — they depend on shared PDF/XLSX styling that happens to live in a business module. This also creates the apparent `socops → reporting` cycle. <br><br>**(b) A genuine god-consumer.** `reporting`'s *outbound* edges are real business coupling: it imports `RiskRepository`, `Risk`, `KriDefinition`, `KriMeasurement`, and peers' repositories and entities across 8 modules — reaching into their internals rather than a published contract. |
| **Root Cause** | (a) The export toolkit was written where it was first needed and never promoted to the kernel. (b) The modular monolith was adopted as a *package convention* with no enforcement — `ADR-001`/`ADR-002` assert module independence and nothing tests it. A convention that is never tested is a preference. |
| **Business Impact** | Every "we can sell this module separately" claim is false for the coupled set until the graph is cut. |
| **Technical Impact** | Change amplification: a `risk` entity rename touches `reporting`. Cycles (`socops`→`reporting`→…) prevent clean layering and make build-time modularisation impossible. |
| **UX Impact** | None directly. |
| **Sales Impact** | An architecture reviewer runs exactly the measurement in Baseline §2.2 and asks why the ADRs claim independence the code contradicts. |
| **Recommendation** | **Do (a) first — it is a package move that pays for eight modules.** <br>1. **Promote the export toolkit to the kernel**: move `PdfTheme`, `PdfFonts`, `XlsxStyler`, `ExportNaming`, `RegisterExport`, `TabularExport*` from `module/reporting/service` to `module/platform/export`. Mechanical, no behaviour change, no schema change — and it removes **all 25 inbound edges**, deletes the apparent `socops→reporting` cycle, and moves `ai` to fully kernel-only (Tier A). Roughly one day of work that improves eight modules' separability. <br>2. **Declare the legal dependency directions**: `module → platform` (kernel) always allowed; `module → module` only via a published **port interface** owned by the *consumer*; **no cycles**. <br>3. **Enforce with a ratcheted boundary test** over all 58 packages, seeded from today's graph (record the current edge count per module, allow only decrease) — the same mechanism that took authorisation from 62 to 21 this session. <br>4. **Then invert (b)**: `ReportDataPort` per source module, implemented *by* each source, so `reporting` depends on interfaces it owns. Then `audit_committee` and `governance`. |
| **Alternatives** | For (a): leave the toolkit and let modules import it — rejected, it is precisely what makes the graph unreadable and blocks the boundary test from ever going green. For (b): extract `reporting` into a separate service over a read model — the right long-term answer and a large one; the port inversion is step one toward it either way. An event-driven read model fed by the existing `DomainEvent` infrastructure is a strong fit; sequence it after the ports exist. |
| **Trade-offs** | (a) touches imports in 8 modules in one commit — wide but shallow, and the compiler proves completeness. (b) adds indirection and files. The ratchet form means no big-bang refactor: the number can only fall, so the graph improves with every touch. |
| **Effort** | **S** for the toolkit promotion (~1 day) · **S** for the ratcheted boundary test (1–2 days) · **L** for the `reporting` inversion. |
| **Priority** | High — toolkit promotion and the test immediately; the inversion when `reporting` or one of its sources is next touched. |
| **Expected ROI** | **Very high for (a)** — one day of mechanical work improves the separability tier of eight modules including the two largest (`bcm`, `sust`). High for the ratchet: it stops the graph worsening for the cost of one test. |
| **References** | Baseline §2.1, §2.2. `ADR-001`, `ADR-002`. `UNIFIED_IMPLEMENTATION_PLAN.md` S5 (`ChangeModuleBoundaryTest`) — correct in principle but scoped to **one** module; this finding generalises it to 58. |

---

## F2-09 · Dev and prod schemas are produced by two different mechanisms

| Field | Content |
|---|---|
| **ID** | F2-09 |
| **Category** | Operations / data integrity |
| **Severity** | **P1 Critical** |
| **Current State** | Dev: Flyway **off**, schema bootstrapped by Python scripts (`setup_*_db.py`) plus migrations hand-applied via pyodbc (convention: run each twice to prove idempotence). Prod: Flyway **on**, baseline **V312**. 304 migration files, `V1`–`V314`, with sequence gaps at `V82–V90`, `V168`, `V169`, `V174`. `V313`/`V314` are in dev by hand and not yet in prod. Baseline §7. |
| **Problem** | Two schema-production mechanisms with **nothing proving they converge**. A migration correct under Flyway but hand-applied differently in dev (or vice versa) is invisible until production. Every test in the 2,415-test suite runs against the *dev* schema shape. |
| **Root Cause** | A pragmatic early decision (Flyway off in dev for iteration speed) that was never balanced by a convergence check. |
| **Business Impact** | The class of defect that causes production-only failures after a green build — the most expensive kind to diagnose and the most damaging to customer confidence. |
| **Technical Impact** | Green tests do not evidence prod-schema correctness. Hand-application is also a manual step that can be forgotten — `V313`/`V314` are currently in that gap. |
| **UX Impact** | None until a production failure. |
| **Sales Impact** | "How do you guarantee dev, staging and production schemas match?" has no good answer today. |
| **Recommendation** | Add a **schema-convergence CI job**: from an empty database, run Flyway `V1`→head, then dump the schema (tables, columns, types, nullability, constraints, indexes) and diff it against a dump of the Python-bootstrapped dev schema. Fail on any difference not in an explicit allow-list. This keeps the fast dev path *and* proves equivalence. Additionally: a `/actuator/info` field publishing the applied migration head, so "which schema is production on?" is answerable in one request (this is `W0-7`, already planned). |
| **Alternatives** | (a) Turn Flyway on in dev — cleanest, and it deletes the whole problem class; cost is dev iteration speed and rework of the Python bootstrap. **Recommended if the team will accept it.** (b) Retire the Python bootstrap in favour of a seeded template database — good middle path. |
| **Trade-offs** | The CI job needs a scratch SQL Server instance; ~1 day to build, minutes per run. |
| **Effort** | **M** (2–3 days) |
| **Priority** | High — before the next production release. |
| **Expected ROI** | High — eliminates an entire class of production-only defects. |
| **References** | Baseline §7. `CLAUDE.md` (migration conventions). |

---

## F2-10 · Production runs code with two known, already-fixed defects

| Field | Content |
|---|---|
| **ID** | F2-10 |
| **Category** | Operations |
| **Severity** | **P1 Critical** |
| **Current State** | Two defects are fixed, merged to `master` and built into a jar staged on the deployment host as `…jar.new` (104,934,012 bytes, suite 2,322/0/0 at build time). The service has **not** been restarted, so production still runs the old jar. (1) **DF-1** break-glass sweep transaction defect — was failing **33–35 times per day**. (2) **DF-2** stale/foreign-key refresh cookie returning **500** instead of **401**. |
| **Problem** | The gap between "fixed" and "running" is unmanaged. A verified fix that is not activated has zero customer value while carrying full false confidence — the tracker says done. |
| **Root Cause** | Activation requires an elevated Windows shell (`Stop-Service`/`Move-Item`/`Start-Service`) that the build automation cannot perform, and no gate flags "built but not deployed". |
| **Business Impact** | A daily-recurring failure in a **break-glass emergency-access** control — precisely the control an auditor tests first — persists in production. |
| **Technical Impact** | `V313`/`V314` also await this restart (F2-09), so schema and code drift together. |
| **UX Impact** | Users hitting an expired session receive a 500 error rather than a clean re-authentication. |
| **Sales Impact** | An auditor sampling break-glass logs finds 33–35 daily failures. |
| **Recommendation** | (1) Perform the restart — the one action in this register that only the operator can take. (2) Add a **deployed-build-ID check**: `/actuator/info` publishes git SHA + migration head, and a post-deploy probe compares it to `master`; the tracker cannot show "done" while they differ. This converts an invisible gap into an automatic alarm. |
| **Alternatives** | Automate deployment via the WinSW service account with rights to restart itself — better long-term; needs a security review of the privilege grant. |
| **Trade-offs** | The restart is a brief planned outage on `:80`. |
| **Effort** | **S** — minutes for the restart; **S** for the build-ID probe (`W0-7`). |
| **Priority** | **Immediate.** |
| **Expected ROI** | Very high — the fixes already exist and are already paid for. |
| **References** | `UNIFIED_IMPLEMENTATION_PLAN.md` W0-2, W0-3, W0-7. |

```powershell
Stop-Service TssGrcBackend
if (Test-Path 'D:\Deployment\tss-grc\app\tss-grc-platform.jar.new') { Move-Item -Force 'D:\Deployment\tss-grc\app\tss-grc-platform.jar.new' 'D:\Deployment\tss-grc\app\tss-grc-platform.jar' }
Start-Service TssGrcBackend
```

---

## F2-11 · Bilingual delivery is split across two mechanisms; 439 strings are hardcoded English

| Field | Content |
|---|---|
| **ID** | F2-11 |
| **Category** | UX / internationalisation |
| **Severity** | **P1 Critical** (Arabic is the primary language) |
| **Current State** | A real i18n layer exists (`lib/i18n.tsx`, per-locale lazy loading) with **5,677 keys in `en.ts` and 5,677 in `ar.ts` — exact parity**. But `t('…')` is used **6,458 times in only 163 files**, while **4,888 inline `lang === 'ar' ? … : …` conditionals appear in 431 files**. **271** hardcoded Latin JSX text nodes and **168** hardcoded `label`/`title`/`placeholder` props are in neither mechanism. **49 of 329 pages (15%)** contain no Arabic character at all. Baseline §5.2. |
| **Problem** | Two parallel bilingual mechanisms, and the *undisciplined* one has 2.6× the file reach. 439 user-visible strings cannot be reached by a translator, a reviewer, or any coverage check. |
| **Root Cause** | The catalogue arrived after the inline pattern was established, and no lint rule prevents the inline form or bare literals. |
| **Business Impact** | Arabic-first is the platform's primary differentiator in Saudi Arabia. Partial Arabic in a government demo is a acceptance-committee finding, and 15% of screens are English-only. |
| **Technical Impact** | No third language is addable without touching 431 files. Translation cannot be outsourced, because strings live in TSX ternaries. Arabic coverage is unmeasurable. |
| **UX Impact** | Mixed-language screens — the most visible quality defect a bilingual user meets, and the one that most damages perceived polish. |
| **Sales Impact** | Government and semi-government procurement in Saudi Arabia routinely requires full Arabic UI. "85% of screens contain Arabic" is not a passing answer to "is the interface Arabic?" |
| **Recommendation** | **Precondition: ESLint is not configured at all** (`audit/metrics.json` -> `eslint_configured: false`), so step 0 is to add an ESLint baseline before any lint ratchet can exist. Then: (1) rules for no bare Latin string literal in JSX text or in `label`/`title`/`placeholder`; no `lang === 'ar'` in `pages/**` (allow it only inside `components/` primitives where a genuine layout branch is needed). Introduce both as **ratchets** — current count recorded, only allowed to fall. (2) Migrate the 439 hardcoded strings into the catalogue (mechanical, scriptable). (3) Convert the 431 inline-conditional files to `t()` progressively, prioritising the 49 zero-Arabic pages. (4) Add an **Arabic-coverage CI report** per module — measurable, reportable to a customer, and answers the procurement question with a number. |
| **Alternatives** | (a) Accept inline conditionals as legitimate and drop the catalogue — rejected: loses translator workflow, third-language capability and measurability. (b) Automated machine translation of the 439 — useful as a first pass but requires Arabic GRC-terminology review; regulatory terms are exactly where machine translation fails. |
| **Trade-offs** | The lint ratchets will initially fail on many files; ratcheting (not blocking) is what makes adoption feasible. |
| **Effort** | **S** for the lint ratchets + coverage report; **M** for the 439 strings; **L** for the full 431-file convergence. |
| **Priority** | High — the ratchets and the zero-Arabic pages first. |
| **Expected ROI** | High — converts the primary differentiator from "mostly true" to measured and defensible. |
| **References** | Baseline §5.2. Competitors: none of the five majors offers Arabic-first GRC with RTL-native exports. This is the platform's strongest defensible position — which makes a 15% gap disproportionately expensive. |

---

## F2-12 · `ndi`: 127 endpoints, 82 writes, 11 tests, behind a 4,970-line screen

| Field | Content |
|---|---|
| **ID** | F2-12 |
| **Category** | Assurance / maintainability |
| **Severity** | **P1 Critical** |
| **Current State** | `ndi` — 7,717 LoC, 15 entities, **127 endpoints, 82 write endpoints, 11 tests**. Its frontend is `pages/ndi/NdiPage.tsx` at **4,970 lines**. Baseline §1, §6, §5.4. |
| **Problem** | The platform's largest write surface per test is fronted by its second-largest single file. 82 write endpoints with 11 tests means roughly one test per 7.5 mutating operations. |
| **Root Cause** | Rapid delivery of a broad module without a test floor (F2-01/F2-03) and without a page-size limit. |
| **Business Impact** | Concentrated, uncontained risk: a regression here is likely and will not be caught by CI. |
| **Technical Impact** | A 4,970-line component is beyond safe review; no reviewer holds it in context. Refactoring is unsafe in both directions — no tests below, no structure above. |
| **UX Impact** | Single-file screens of this size typically hide inconsistent states and unreachable branches. |
| **Sales Impact** | The per-module scorecard cannot show `ndi` as production-grade on this evidence. |
| **Recommendation** | Treat `ndi` as a **contained remediation project**, not a cleanup task: (1) characterisation tests over the 82 writes — authorisation + state transition per endpoint, before any refactor; (2) split `NdiPage.tsx` along its existing tab boundaries into route-level pages under `pages/ndi/`; (3) set a **page-size ratchet** (no new file > 800 lines; existing offenders may only shrink); (4) then structural work. Order matters — tests first, because there is currently no safety net for step 2. |
| **Alternatives** | (a) Rewrite the module — rejected: 7.7 kLoC of working domain logic with no specification other than the code. (b) Leave it and document the risk — acceptable **only** if `ndi` is out of commercial scope; that is a product decision, and it should be recorded explicitly rather than left implicit. |
| **Trade-offs** | Characterisation tests codify current behaviour including any current bugs — accepted deliberately: they enable safe change, and a bug that is *pinned* is a bug that can be found and fixed. |
| **Effort** | **L** (3–4 weeks) |
| **Priority** | High if `ndi` is in scope for sale; otherwise record the decision to defer. |
| **Expected ROI** | Medium-high — de-risks the largest single concentration of unproven write surface. |
| **References** | Baseline §1, §5.4, §6. |

---

## F2-13 · The authorisation ratchet measures coverage, not tightness

| Field | Content |
|---|---|
| **ID** | F2-13 |
| **Category** | Security |
| **Severity** | **P2 High** |
| **Current State** | `ControllerAuthorizationRatchetTest` counts write endpoints with no `hasPermission`/`hasAnyRole` guard; driven **62 → 21** this session with the floor enumerated (8 public + 10 self-scoped + 3 participant-scoped). But a guard that *exists* is never checked for *restrictiveness*: `WORKFLOW_WRITE` is granted to **55 of 57 roles including `VIEWER`**, so `ApprovalRequestController#create` is guarded and effectively unrestricted. |
| **Problem** | The ratchet can reach zero while the platform remains open, because it asks "is there a guard?" and never "does the guard exclude anyone?" |
| **Root Cause** | The control was built to close the larger gap (missing guards) and the grant side was never measured. Grants accumulated via broad seed migrations. |
| **Business Impact** | Segregation of duties may be nominal in places — a serious claim to get wrong in a GRC product. |
| **Technical Impact** | The green ratchet creates false assurance and hides the grant problem. |
| **UX Impact** | None. |
| **Sales Impact** | A security reviewer who joins the permission-grant tables to the role table finds this quickly; the platform's own authorisation evidence then reads as over-claimed. |
| **Recommendation** | Add a **grant-tightness report** to CI: for every permission, the number and identity of roles holding it, flagging any business-write permission held by >50% of roles or by any read-only persona (`VIEWER`, `AUDITOR`, external, machine). Review `WORKFLOW_WRITE` first — restrict to roles that legitimately raise approval requests. Then adopt an "excess-grant" ratchet alongside the coverage ratchet. |
| **Alternatives** | Model least privilege per persona from scratch — the right answer eventually; the report is the cheap first step that tells you where it matters. |
| **Trade-offs** | Tightening grants can break existing users' workflows; needs a per-tenant impact query before each change. |
| **Effort** | **S** for the report; **M** for the tightening. |
| **Priority** | Medium-high — the report immediately, so the size of the problem is known. |
| **Expected ROI** | High relative to effort — one query turns an unknown into a list. |
| **References** | Session finding, recorded in `ControllerAuthorizationRatchetTest` javadoc. Baseline §4. |

---

## F2-14 · Config-gated controls with no config-health check (a defect *class*)

| Field | Content |
|---|---|
| **ID** | F2-14 |
| **Category** | Governance / control design |
| **Severity** | **P2 High** |
| **Current State** | `admin.committee_gate_map` was **empty in every tenant** while `change_authorisation_matrix` demanded a committee for CAB (5 rows) and ECAB (6 rows) in all three orgs. The flagship committee-gating control was implemented in code and **not in force in operation** — `refer()` would throw "No committee is configured for gate CAB", and changes silently fell back to the legacy approval tally. Fixed for org 1 by `V314`; orgs 2 and 3 are deliberately flagged rather than fabricated, via a startup WARN and `GET /api/v1/changes/governance-health`. |
| **Problem** | The specific instance is fixed. **The class is not.** Any control whose enforcement depends on configuration rows can be silently inert, and the platform has no systematic way to detect it. An empty configuration table raises no error until someone exercises the path. |
| **Root Cause** | Controls are validated by unit tests that supply their own configuration. Nothing validates the *deployed* configuration. "Implemented" and "in force" are treated as the same state. |
| **Business Impact** | The worst failure mode in GRC: a control that is documented, demonstrated, believed effective — and inert. An auditor who tests it finds the gap, and the finding lands on the platform, not the customer. |
| **Technical Impact** | Unknown how many other controls are config-gated. Candidates from the same family: authorisation-matrix rows, freeze windows, SLA policies, quorum policies, workflow gate maps, `ck_ca_source` whitelist, evidence-mandatory flags, escalation targets. |
| **UX Impact** | Administrators receive no signal that a control they believe is active is not. |
| **Sales Impact** | If discovered during a proof-of-concept, it recasts every "the platform enforces X" claim as needing independent verification. |
| **Recommendation** | Generalise what `ChangeGovernanceHealthService` does for one control into a platform capability: (1) a **`ControlHealth` SPI** — each control declares the configuration it requires and reports `IN_FORCE │ NOT_CONFIGURED │ DEGRADED`; (2) a **Control Assurance dashboard** aggregating it per tenant, and the same data on `/actuator/health` for operations; (3) an audit of every configuration-dependent control against this SPI — start with the eight candidates above; (4) a rule for new controls: no control ships without a health contribution. **Sell this** — "our platform tells you which of its own controls are not yet in force" is a differentiating capability, not just an internal fix. |
| **Alternatives** | (a) Seed defaults everywhere so no table is empty — **rejected, and this is the important judgement**: a fabricated committee with no charter, chair or members can never reach quorum, so minutes can never be approved. The gate then fails at the *last* step instead of the first while *appearing* configured. Failing loudly and early beats failing quietly and late. (b) Documentation checklists — rejected: not a control. |
| **Trade-offs** | Each control needs a health contribution — small per control, and it produces a customer-visible feature. |
| **Effort** | **M** for the SPI + dashboard; **S** per control. |
| **Priority** | Medium-high — the audit of the eight candidates first, since the *extent* is unknown. |
| **Expected ROI** | High — converts a systemic blind spot into a saleable assurance feature. |
| **References** | `V314`, `ChangeGovernanceHealthService`, `INDEPENDENT_PLATFORM_ASSESSMENT.md` F-4. |

---

## F2-15 · 3,650 lines of fabricated records ship in the production bundle

| Field | Content |
|---|---|
| **ID** | F2-15 |
| **Category** | Trust / build hygiene |
| **Severity** | **P2 High** (the executive-surface subset is P0 — F2-02) |
| **Current State** | **15** mock files, **3,650 lines**, in `src/`: `mockCompliance` 629, `mockRisks` 418, `mockWorkflows` 373, `mockEthics` 362, `mockCyber` 327, `mockScm` 297, `mockTprm` 226, `mockPrivacy` 211, `mockChanges` 133, `mockWorkflowDefs` 126, `mockAdmin` 117, `mockEvidence` 116, `mockBcm` 100, `mockReports` 76, `mockIncidents` 39. Baseline §5, §5.1. |
| **Problem** | Fabricated GRC records — named risks, controls, suppliers, incidents — are compiled into the artefact delivered to customers. Any of them can surface through a fallback path, and they are readable by anyone who opens the bundle. |
| **Root Cause** | No CI rule; no separation between development fixtures and shipped source. |
| **Business Impact** | Reputational if discovered in a customer environment; a plausible (if unlikely) data-integrity finding if a mock record is mistaken for a real one in an audit sample. |
| **Technical Impact** | Bundle weight, and 3,650 lines of unmaintained code that must be kept compiling. |
| **UX Impact** | Indistinguishable from real data when a fallback fires. |
| **Sales Impact** | "Why does your production bundle contain a fake supplier register?" is not a question with a good answer. |
| **Recommendation** | Move all 15 files to `src/__fixtures__/` excluded from the production build, or delete them where a real API path now exists. Add the CI rule from F2-02 (no `/mock/i` import outside tests) — one rule closes both findings permanently. Keep `mockChanges.ts` last, as the change-module FE split depends on it (a known sequencing constraint). |
| **Alternatives** | Replace with MSW handlers in dev only — better developer experience, same production outcome; more setup. |
| **Trade-offs** | Some pages will show empty states in development until seeded — which is the honest behaviour and surfaces gaps earlier. |
| **Effort** | **M** (the 26 non-critical import sites) |
| **Priority** | Medium — after F2-02's three files. |
| **Expected ROI** | Medium-high — permanent closure of a whole trust risk class for modest effort. |
| **References** | Baseline §5, §5.1. |

---

## F2-16 · Two page monoliths carry 7.7% of all frontend code

| Field | Content |
|---|---|
| **ID** | F2-16 |
| **Category** | Maintainability |
| **Severity** | **P2 High** |
| **Current State** | `pages/esg/EsgPage.tsx` **5,583 lines** and `pages/ndi/NdiPage.tsx` **4,970 lines** = **10,553 lines**, 7.7% of the 136,614-line page corpus, in 0.6% of the files. 72 pages exceed 500 lines; 11 exceed 1,000; median is 312. Baseline §5.4. |
| **Problem** | Two files beyond the size any reviewer can hold in context, fronting `sust` (16.7 kLoC, 226 endpoints) and `ndi` (7.7 kLoC, 127 endpoints, 11 tests). |
| **Root Cause** | No page-size limit; tabbed mega-screens grew organically instead of splitting into routes. |
| **Business Impact** | Slow, risky change in two of the largest revenue-relevant modules (ESG/green finance is a live Saudi CMA/TCFD selling point). |
| **Technical Impact** | Unreviewable diffs; poor code-splitting; HMR and build cost. |
| **UX Impact** | Deep tab hierarchies inside one route mean no deep-linking to a sub-view and browser Back behaving unexpectedly — both common complaints in large GRC UIs. |
| **Sales Impact** | Indirect (velocity), plus the deep-linking gap is visible in demos. |
| **Recommendation** | Split both along existing tab boundaries into route-level pages (which also fixes deep-linking); introduce a **page-size ratchet** — no new `.tsx` over 800 lines, existing offenders may only shrink. Do `NdiPage` **after** its characterisation tests (F2-12) and `EsgPage` first, since `sust` has 200 tests to catch mistakes. |
| **Alternatives** | Extract shared sub-components without splitting routes — improves review but keeps the deep-linking defect. |
| **Trade-offs** | Routing changes affect bookmarks; add redirects from old paths. |
| **Effort** | **M** per page |
| **Priority** | Medium — `EsgPage` first (safer, better tested). |
| **Expected ROI** | Medium — velocity and deep-linking. |
| **References** | Baseline §5.4, §1. |

---

## F2-17 · Zero performance or load evidence for 2,160 endpoints

| Field | Content |
|---|---|
| **ID** | F2-17 |
| **Category** | Scalability |
| **Severity** | **P2 High** — *reframed after finding the evidence* |
| **Current State** | **A real k6 load test exists and is tracked** (`audit/artifacts/k6/load.js`, `audit/raw/k6-summary.json`, 2026-06-18 @ `3ca91bf`): 4 scenarios (baseline 20 VU · spike to 100 VU · soak 15 VU · breakpoint arrival-rate to 350/s), **18,763 requests, 0 errors, 100% checks passed, p50 8.6 ms / p95 26.9 ms / max 194 ms**, and the Hikari pool never exhausted. It tests **`GET /api/v1/risk/risks` — one endpoint of 2,160**, read-only, at dev data volumes. `PERF-FE-002` (per-locale dictionary lazy-loading) is a second, FE-side performance decision. Baseline §7.1-7.2. |
| **Problem** | **Coverage, not absence.** The one path measured is fast and stable, and the audit's own note says the ceiling was never reached. But no **write** endpoint, export, dashboard or search has been load-tested; there is no realistic data volume, no capacity model, and no per-endpoint p95 target. An RFP asking for concurrent-user figures still cannot be answered — and the fastest read path is the least informative thing to have measured. |
| **Root Cause** | The audit scoped load testing to one representative read to prove the harness, and the harness was never extended. `metrics.json` records the limitation honestly — the gap is follow-through, not candour. |
| **Business Impact** | Cannot size hosting or commit to an SLA. Materially mitigated: a working k6 harness with thresholds and summary export already exists, so this is scenario authoring rather than a new capability. |
| **Technical Impact** | Likely N+1 query problems are unmeasured — a documented landmine already warns that lazy collections fail outside the session, implying entity-graph complexity that typically carries N+1s. |
| **UX Impact** | Unknown; large registers (with real data volumes) are the usual first casualty. |
| **Sales Impact** | Blocks enterprise RFP responses and any SLA commitment. |
| **Recommendation** | **Extend the existing harness — do not start over.** (1) Seed one tenant to realistic volume (10k risks, 50k controls, 200k evidence items, 1M audit-log rows) — this also produces the demo tenant F2-02 needs. (2) Add scenarios to `audit/artifacts/k6/load.js` for the **20 highest-traffic endpoints**, deliberately including **write** paths, exports, dashboards and search. (3) Raise the dev Hikari pool above 10 so a ceiling can actually be found. (4) Publish a capacity model and per-endpoint p95 targets. (5) Run the top 5 in CI as a regression smoke. |
| **Alternatives** | Buy an APM tool and instrument production — complementary, but with few tenants it yields little data and answers no RFP today. |
| **Trade-offs** | Realistic seeding is itself a project; it also produces the demo tenant recommended in F2-02 — do both with one dataset. |
| **Effort** | **M–L** — the harness, thresholds and summary export exist; most of the cost is realistic seeding. |
| **Priority** | Medium — immediate if an enterprise RFP is live. |
| **Expected ROI** | Medium-high — unblocks enterprise deals and de-risks the first large customer. |
| **References** | Baseline §7.1, §7.2, §8; `audit/metrics.json` -> `load_test_k6`. **Correction:** this finding originally read "zero performance or load evidence" — wrong; the evidence was in the tracked `audit/` vault. |

---

## F2-18 · The governance core is assured at unit level only

| Field | Content |
|---|---|
| **ID** | F2-18 |
| **Category** | Assurance |
| **Severity** | **P2 High** |
| **Current State** | The committee engine — quorum, recusal, vote tally, e-signed minutes, referral gating, tenant isolation — had **zero tests** until this session; it now has **93 tests across 5 classes**, plus 14 for `ModulePermissionEvaluator`, each mutation- or live-verified. All are **unit tests with mocked repositories**. There is no HTTP-level or database-level end-to-end test of a complete committee decision. |
| **Problem** | The system of record for change and policy approval is proven at the service layer only. Untested by any automated test: JSON contracts, the authorisation chain in a real filter chain, JPA mapping and constraint behaviour, transaction boundaries, and the interaction with `ROWVERSION` optimistic locking (a documented landmine — double-flush around native `@Modifying` updates). |
| **Root Cause** | Unit tests were the fastest way to close a zero-test gap on a critical component — the right first move. The end-to-end layer was left for later and later has not arrived. |
| **Business Impact** | Committee decisions are the platform's audit-evidence backbone. A mapping or transaction defect invisible to unit tests would corrupt exactly the records auditors rely on. |
| **Technical Impact** | Mocked repositories cannot catch constraint violations, cascade behaviour, or the org-filter-in-native-SQL mistakes this codebase has hit before. |
| **UX Impact** | None. |
| **Sales Impact** | "How do you test your approval engine end to end?" — currently: manually. |
| **Recommendation** | Add one **golden-path integration test** through the real HTTP stack and a real database: constitute a committee → refer a change → record votes with a recusal → reach quorum → approve minutes → verify the e-signature hash → assert the change advanced and the audit ledger recorded it. One test, high value, and it becomes the regression harness for every later CAB epic. Then a second for the rejection/no-quorum path. Retain the worked decision as `[DEMO-EVIDENCE]` (never `[REVIEW-TEST]` — cleanup scripts delete those). |
| **Alternatives** | Testcontainers-based SQL Server for full fidelity — best, heavier CI. Or run against the dev database — faster to build, order-dependent and less reliable. |
| **Trade-offs** | Integration tests are slower and need data setup; two of them is a deliberate, affordable scope. |
| **Effort** | **M** (3–5 days) |
| **Priority** | Medium-high — it is the gate that makes the remaining CAB epics safe. |
| **Expected ROI** | High — one test protects the platform's most audit-critical flow. |
| **References** | Session commits `00939e76`, `083c6119`, `033abaf5`, `ab28f348`. `UNIFIED_IMPLEMENTATION_PLAN.md` S3, W1-5. |

---

## F2-19 · Documentation asserts numbers nothing verifies — including this project's own plans

| Field | Content |
|---|---|
| **ID** | F2-19 |
| **Category** | Governance / documentation integrity |
| **Severity** | **P2 High** |
| **Current State** | Three measurable claims in current documents are wrong: **24 modules** (actual 58) in `CLAUDE.md` and the architecture pack; **53 mock import sites** (actual 44 statements / 29 files) in `REMEDIATION_MASTER_PLAN.md` and `UNIFIED_IMPLEMENTATION_PLAN.md`; **"13 existing statuses"** for `ck_change_status` (actual 14) in the same plan. The vendor foundation pack additionally shipped an `AGENTS.md` naming a **non-existent git remote**, and a `README.md` with **unresolved conflict markers** describing a different product — both fixed at `99a719da`. Baseline §0. |
| **Problem** | A documentation corpus that asserts unverified numbers is a governance defect in a governance product. Two of the three wrong numbers were published **by this review's own predecessor plans** — the failure mode is systemic, not vendor-specific. |
| **Root Cause** | No provenance requirement. Numbers were counted once by hand or inherited, then propagated by citation. |
| **Business Impact** | Plans were sized against wrong numbers (the test bar and RBAC catalogue silently skipped 34 modules). Externally, one discovered wrong number devalues every correct one. |
| **Technical Impact** | Work items were mis-scoped in both directions. |
| **UX Impact** | None. |
| **Sales Impact** | Datasheets and RFP responses built on these numbers understate the product by 40% and are falsifiable in minutes. |
| **Recommendation** | (1) Adopt the **evidence rule** already at the head of this register as a corpus-wide standard: every quantitative claim carries a provenance pointer or is labelled OPINION/ASSUMPTION. (2) **Generate** the numbers — the measurement scripts run in CI and emit `Appendix/MEASURED_BASELINE.md`, so it cannot drift. (3) Retro-fit the three corrections (done here) and stamp superseded documents. (4) Stamp the V1-era `docs/` corpus **HISTORIC** so stale material is not read as current (`W0-6`, outstanding). |
| **Alternatives** | Periodic manual documentation review — rejected: it is the control that just failed. |
| **Trade-offs** | Generated documents are less narratively polished than hand-written ones. Correct beats elegant for a reference corpus. |
| **Effort** | **S** — the scripts exist; wiring them into CI is hours. |
| **Priority** | Medium-high — cheap, and it protects every other document's credibility. |
| **Expected ROI** | High — makes the whole Bible trustworthy by construction. |
| **References** | Baseline §0, §10. `Governance/BIBLE_VALIDATION_LOG.md`. |

---

## F2-23 · No role holds `ADMIN:WRITE`, so only `PLATFORM_ADMIN` can record a committee vote

| Field | Content |
|---|---|
| **ID** | F2-23 |
| **Category** | Security / authorisation catalogue / product usability |
| **Severity** | **P1 Critical** |
| **Current State** | `POST /api/v1/committees/resolutions/{id}/votes` is guarded by `hasPermission('ADMIN','WRITE')`. Measured against `iam.role_permissions`: **zero roles hold `ADMIN:WRITE`**, and `so_officer` (CISO + SECURITY_ANALYST + SUSTAINABILITY_OFFICER) holds **no `ADMIN` permission at all**. `PLATFORM_ADMIN` reaches the endpoint only because it bypasses in code. Found by the DF-3 live probe, which expected an ordinary member's self-vote to succeed and got **403**. |
| **Problem** | Committee voting — the platform's system of record for change and policy approval — is in practice a **platform-administrator-only** operation. The "member casts their own vote" path that DF-3 introduces as the safe default is **unreachable for every real committee member**, so every vote is necessarily recorded by an administrator and (correctly, post-DF-3) stamped `on_behalf = 1`. A committee cannot self-serve its own decisions. |
| **Root Cause** | The endpoint inherited the broad `ADMIN` module permission because committees live under `module/admin`. `ADMIN:WRITE` means "administer the platform's committee configuration", which is a different thing from "cast the vote I am entitled to cast" — and no role was ever granted it, so the permission is catalogued but dead. It is the mirror image of F2-13 (`WORKFLOW_WRITE` held by 55 of 57 roles): the same missing control — nobody measures who holds what. |
| **Business Impact** | Members cannot vote in their own committee. Either committees are administered by IT on members' behalf — undermining the segregation-of-duties story the product sells — or the feature goes unused. It also means the on-behalf trail DF-3 adds will read `on_behalf = 1` on **every** vote: accurate, but it hides the distinction it was built to expose. |
| **Technical Impact** | Small: catalogue a narrower permission and grant it. DF-3 already resolves identity from the roster, so the self-vote path works the moment a member can reach the endpoint. |
| **UX Impact** | The CH-14 voting panel is unusable by the members it was built for. |
| **Sales Impact** | "Can our committee members vote in the system?" — today, only if you give them a platform-administration permission. That answer fails a segregation-of-duties review. |
| **Recommendation** | **Do not widen `ADMIN:WRITE`** — that would hand committee *administration* to every voting member. Instead: (1) catalogue a narrow action for casting one's own vote — `ADMIN:VOTE`, or better a dedicated `COMMITTEE` module with `READ`/`VOTE`/`APPROVE` once F2-01's registry exists; (2) guard `recordVote` with it, keeping `ADMIN:APPROVE` as the separate on-behalf authority DF-3 already requires; (3) grant it to the roles that actually sit on committees — **a governance decision, not an engineering one** (decision D-7); (4) add the F2-13 grant report in its **inverse** form too: flag any catalogued permission that **no** role holds, because a dead permission is an unreachable feature. |
| **Alternatives** | (a) Grant `ADMIN:WRITE` to committee-member roles — rejected, it carries committee administration. (b) Drop the permission check and rely on roster membership alone — rejected: entitlement and permission are separate concerns, and an unguarded write endpoint breaches the authorisation ratchet. (c) Leave it administrator-mediated and document that all votes are recorded on behalf — tenable only if the customer accepts that committees never self-serve, and it should then be an explicit decision rather than a silent default. |
| **Trade-offs** | One migration plus a per-tenant grant decision. Until it lands, DF-3's forgery fix is fully effective — the hole is closed either way; this is about who can legitimately reach the endpoint. |
| **Effort** | **S** — one catalogue migration, one guard change, one grant set, one live probe. |
| **Priority** | High — pairs with F2-05 (permission conversion) and F2-13 (grant tightness); all three are the same missing control seen from different sides. |
| **Expected ROI** | High — makes a built, tested, now-secure feature usable by its intended users. |
| **References** | Live probe `Appendix/scripts/probe_df3_vote_provenance.py` check 1; the `iam.role_permissions` query quoted above. Related: F2-05, F2-13, F2-01. |

---

## F2-20 · `datagov` — a documented module with no implementation

| Field | Content |
|---|---|
| **ID** | F2-20 |
| **Category** | Product hygiene |
| **Severity** | **P3 Medium** |
| **Current State** | `module/datagov/` contains one file: `package-info.java`, 18 lines, no code. `CLAUDE.md` lists `datagov` among the backend modules. Baseline §1.1. |
| **Problem** | A named module with zero implementation appears in the platform's own documentation — the smallest, clearest instance of the F2-01 inventory failure. |
| **Root Cause** | A package created in anticipation of work that never started; nothing detects an empty module. |
| **Business Impact** | Small in isolation; corrosive in aggregate — it is the kind of detail a diligence reviewer cites as evidence that the module list is aspirational. |
| **Technical Impact** | None. |
| **UX Impact** | None (no screens). |
| **Sales Impact** | Reputational only — and only if listed as a capability. |
| **Recommendation** | Either delete the package, or register it in the module registry with maturity **`PLANNED`** and exclude it from all capability lists. Recommendation: **delete** — data governance is substantially covered by `dataclass`, `data_officer`, `privacy` and `datagov`'s absence changes nothing. The F2-01 registry CI check prevents recurrence. |
| **Alternatives** | Keep as a placeholder — acceptable only with an explicit `PLANNED` maturity that the datasheet generator honours. |
| **Trade-offs** | None. |
| **Effort** | **XS** (minutes) |
| **Priority** | Low — bundle with F2-01. |
| **Expected ROI** | Low absolute, high per minute. |
| **References** | Baseline §1.1. |

---

## F2-21 · Committee votes are recorded under a caller-supplied name

| Field | Content |
|---|---|
| **ID** | F2-21 |
| **Category** | Security / non-repudiation / control integrity |
| **Severity** | **P0 Blocker** — the most serious finding in this review |
| **Current State** | `POST /api/v1/committee-resolutions/{id}/votes` → `CommitteeResolutionController#recordVote` passes **`req.memberName()` straight from the request body** into `CommitteeResolutionService.recordVote(...)`. The service validates that the name is non-blank and that the vote value is legal — and **never checks that the name belongs to the authenticated user, or that it belongs to a member of the committee at all**. The guard is `hasPermission('ADMIN','WRITE')`, so any principal holding it may cast a vote as any name. By contrast, the sibling `approveMinutes` endpoint **correctly** derives the actor from `Authentication` (`usernameOf(auth)`, `userIdOf(auth)`) — so the correct pattern exists in the same module, one file away. A sweep of all controllers for caller-supplied actor fields returns **exactly one** hit: this one. |
| **Problem** | Four controls fail simultaneously, all of them controls this platform advertises: <br>**1. Recusal / COI bypass.** Both recusal mechanisms — the COI check against the attendance sheet and the Track-D SoD forced recusal of the referred item's author — compare the **claimed** `memberName`. Submitting a different name evades both entirely. <br>**2. Quorum forgery.** Votes are keyed by name, so submitting *N* distinct names manufactures a quorate tally from a single actor. <br>**3. Vote overwrite.** `voteRepo.findByResolutionIdAndMemberName(...)` upserts by name, so an actor can **overwrite another member's already-cast vote** — silently changing a recorded decision. <br>**4. Forged audit trail.** `audit.log("RESOLUTION_VOTE", …)` records the *claimed* name, so the audit ledger corroborates the forgery rather than exposing it. <br><br>And the compounding failure: **committee minutes are e-signed with a SHA-256 hash.** The signature is cryptographically sound and attests to content whose votes may be forged — non-repudiation of the *document* over a *forgeable* record. Because Track D routes change and policy approval through committees, a single actor can drive a change to APPROVED through a fabricated committee decision. |
| **Root Cause** | The voting API was designed as a *secretary-entry* interface — one person recording a physical meeting's show of hands — and was never re-examined when it became the enforcement point for recusal, quorum and SoD (CH-14, then Track D). The actor-identity question was answered correctly for minutes and never revisited for votes. **No test could catch it:** all 19 recusal tests and 19 tally tests pass, because they supply the name as an input and verify the logic *given* that name. They test the decision, not the provenance of the identity making it — exactly the blind spot F2-18 predicts for mock-input unit tests. |
| **Business Impact** | Committee decisions are the platform's system of record for change and policy approval, and its primary audit-evidence artefact. A customer's auditor who tests vote provenance finds that decisions are unattributable and recusal is advisory. For a **GRC vendor** this is category-fatal: segregation of duties is not a feature here, it is the product. |
| **Technical Impact** | Contained and cheap to fix — one controller, one service signature, one DTO field. Requires a data-quality decision about existing vote rows (see below). |
| **UX Impact** | The secretary-entry workflow is legitimate and must survive: in a real meeting one person does record the votes. The fix must therefore distinguish *voting* from *recording a vote on someone's behalf* — and make the latter visible rather than forbidden. |
| **Sales Impact** | This is the finding that ends a technical due diligence or a government acceptance review if found by the other side first. Conversely, fixing it *and* shipping the on-behalf-of audit trail is a demonstrable strength — most competitors' committee modules do not distinguish the two at all. |
| **Recommendation** | **Mirror the pattern already used by `approveMinutes` and by `PlanDistributionService` (the plan-acknowledgement ownership fix landed this session — the same class of defect, already solved once):** <br>1. `recordVote` takes `Authentication`; the default voter is the **authenticated member**. <br>2. `memberName` in the body is accepted **only** when the caller holds a distinct secretary/chair authority (`hasPermission('ADMIN','APPROVE')` or a new `COMMITTEE_RECORD_ON_BEHALF`), and the stored row then carries `recorded_by_user_id` + `on_behalf = 1`, with the audit line reading `"<secretary> recorded a vote ON BEHALF OF <member>"`. <br>3. Reject a `memberName` that is **not a registered member of that committee** — an unconditional check, regardless of authority. <br>4. Evaluate recusal/COI against the **resolved member identity**, never the raw string. <br>5. Refuse to overwrite a vote cast by a *different* user without the on-behalf authority. <br>6. Add the test that was missing: *a member cannot cast a vote in another member's name*, plus *a recused member cannot evade recusal by submitting a variant of their name*. <br>7. **Data remediation:** existing vote rows have no recorded actor. Do **not** back-fill a guess. Add `recorded_by_user_id NULL` and treat NULL as "provenance unknown (pre-fix)" — visible in the UI and in exports. Fabricating provenance would repeat the mistake F2-14 warns against. |
| **Alternatives** | (a) Require every member to cast their own vote, no on-behalf path — cleanest security, but breaks the real-world meeting workflow and will be worked around by sharing credentials, which is worse. (b) Keep the name field and merely log the authenticated caller alongside it — insufficient: recusal and quorum would still evaluate the claimed name, so three of the four control failures remain. (c) Bind votes to committee membership rows by id instead of name — **recommended as the follow-on**: name-keyed voting is the root of the overwrite and variant-name problems, and an id-keyed vote table removes them structurally. |
| **Trade-offs** | The on-behalf authority is a new permission and a per-tenant grant decision. Existing rows become explicitly unattributed, which is a visible admission — and the correct one; a governance product should be able to say "these records predate provenance enforcement." |
| **Effort** | **S** for the fix and tests (1–2 days) · **S** for the migration adding `recorded_by_user_id`/`on_behalf` · **M** for id-keyed votes if adopted. |
| **Priority** | **Immediate — ahead of every other item in this register.** It is small, it is contained, and it invalidates the platform's central control claim while it stands. |
| **Expected ROI** | Highest in the register. Two days of work converts the platform's most audit-critical flow from forgeable to defensible, and turns the on-behalf trail into a selling point. |
| **References** | `CommitteeResolutionController.java:59-61`, `CommitteeResolutionService.recordVote`, `CommitteeController.java:113-114` (the correct pattern), `PlanDistributionService` (same defect class, fixed this session). **Credit:** `Executive/EXECUTIVE_VERDICT.md` in the vendor foundation pack flagged this in general terms — "some important governance decisions can be authored under caller-supplied identities". That claim was unverified when written; **this review verified it in code and it is true.** It is the single most valuable line in the foundation pack. |

---

## F2-22 · 216 write endpoints bind a JPA entity directly from the request body

| Field | Content |
|---|---|
| **ID** | F2-22 |
| **Category** | Security / mass assignment |
| **Severity** | **P2 High** — *unquantified*, pending the audit this finding recommends |
| **Current State** | **216 `@RequestBody` sites across 85 controllers** bind a JPA `@Entity` directly, rather than a DTO — heaviest in `CyberController` (19), `BcmController` (8), `ChangeManagementController` (8), `ControlLibraryController` (8), `AuditLifecycleController` (6), `PrivacyController` (6), `PolicyController` (5), `DpiaController` (5). Entity types bound include `PolicyDocument` (4 sites), `ControlAssessment` (3), `AppsecFinding` (3), `SustainabilityEvidence` (3), `CapaTask` (2), `BcmPlan` (2), `BiaRecord` (2). |
| **Problem** | Direct entity binding lets a client set **any** persisted field — including `id` (turning a create into a cross-tenant `merge()`), `organisationId` (tenant escape), and computed governance fields such as vote tallies, outcomes, statuses and approval stamps. <br><br>**What is verified:** `CommitteeResolutionService.create` **is** correctly defended — it forces `id=null`, overwrites `organisationId` from `TenantContext`, and sets `createdAt`; its comment references `CommitteeActionItemService.create()` "for the full attack path", showing the team has found and fixed this class before. **What is not verified:** whether the other ~214 sites carry the same defence. This finding is therefore a **scoped audit request, not a claimed vulnerability** — asserting 216 vulnerabilities without checking each would be exactly the unevidenced claiming this Bible exists to stop. |
| **Root Cause** | Entity-as-DTO was the fast path early on and became the house pattern before the tenant model hardened. The defence is applied per service by convention, and convention without enforcement is what F2-08 and F2-19 also describe. |
| **Business Impact** | If even a handful of the 214 unverified sites lack the defence, the exposure is cross-tenant write in a multi-tenant GRC platform — the most severe finding class possible for this product. Unknown magnitude is itself the problem. |
| **Technical Impact** | 216 sites to audit; DTO extraction is mechanical but touches many files. |
| **UX Impact** | None. |
| **Sales Impact** | "Do you bind request bodies directly to persistence entities?" is a standard secure-code-review question, and the honest answer today is "in 216 places, with defences applied per service." |
| **Recommendation** | (1) **Audit first, fix second** — script the check: for each of the 216 sites, does the service null the `id` and force `organisationId` from `TenantContext` before `save()`? Publish the count. This is a day's work and converts an unknown into a ranked list. (2) Add a **tenant write-guard test**: for a sample of entity-bound endpoints, submit a foreign `organisationId` and a foreign `id` and assert the row is created in the caller's tenant (or rejected) — never in the foreign one. A tenant write-guard interceptor already exists in the prod profile; prove it. (3) Introduce DTOs for the highest-risk endpoints — anything carrying approval state, tallies, or statuses. (4) Ratchet the entity-bound count downward, as with authorisation. |
| **Alternatives** | (a) Rely on the prod-profile tenant write-guard interceptor alone — insufficient: it does not stop a client setting a *status* or *tally* field, and it is absent in dev, so tests do not exercise it. (b) Global `@JsonIgnore` on `id`/`organisationId` in a mapped superclass — **cheap, broad and effective**; recommended as the immediate mitigation while the audit runs. |
| **Trade-offs** | DTOs add classes and mapping code. The mapped-superclass `@JsonIgnore` gives most of the protection for almost no effort and should come first. |
| **Effort** | **S** for the audit script + `@JsonIgnore` mitigation · **L** for full DTO extraction. |
| **Priority** | High for the audit and the mitigation; the DTO work follows the audit's findings. |
| **Expected ROI** | High — a day of measurement replaces a large unknown with a small known, and the `@JsonIgnore` mitigation is close to free. |
| **References** | `CommitteeResolutionService.create` (verified defended), `CommitteeActionItemService.create` (documented attack path). Baseline §1. |

---

## Sequencing — what to do, in what order

Dependency-ordered, not severity-ordered. Severity says what matters; this says what is *possible*.

| Wave | Items | Why this order |
|---|---|---|
| **First (this week, ahead of everything)** | **F2-21 vote provenance** · F2-22 audit script + `@JsonIgnore` mitigation | F2-21 invalidates the platform's central control claim and takes ~2 days. Nothing else in this register earns more per hour. F2-22 rides alongside because it is the same question — *who is allowed to set this field?* |
| **Now (days)** | F2-10 restart · F2-02 three files · F2-07 reconciliation matrix · F2-08a export-toolkit promotion · F2-08b boundary ratchet · F2-19 CI-generated baseline · F2-20 delete | Highest value per hour. Each is small, independent, and needs no decision. |
| **Wave 1 (weeks)** | F2-01 module registry → then F2-03 test floors, F2-05 permission conversion, F2-06 enforcement test | The registry is the spine: floors, permissions, schemas and navigation all attach to it. Nothing durable is buildable before it. |
| **Wave 2** | F2-04 entitlement **rollout** (**gated on the per-module SKU decision**) · F2-09 schema convergence · F2-13 grant tightness · F2-14 control-health SPI | Wave 2 is where the modular business model becomes real — and where the platform's assurance story becomes saleable. F2-04 is a rollout of an existing subsystem, so it is days rather than weeks. |
| **Wave 3** | F2-07 workspace IA · F2-11 i18n convergence · F2-12 `ndi` · F2-16 monoliths · F2-15 remaining mocks · F2-17 performance · F2-18 integration tests | Larger, benefits from Waves 1–2 being in place (IA needs entitlement; `ndi` needs test floors). |

**The one decision that must be made before Wave 2:** is the commercial model **per-module SKUs** or a
single suite? F2-04, F2-05 and F2-06 are P0/P1 under the first reading and P2 hygiene under the second.
This is Baseline §9 question 2, and it is a business decision — it is not ours to assume.
