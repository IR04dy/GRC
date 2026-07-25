# Bible Validation Log

**Purpose.** The brief says: *"Treat those documents as evidence. Do not treat them as truth. Validate
them. Challenge them."* This log records the result of doing that — per document, with the specific
check applied.

**Date:** 2026-07-25 · **Validator:** independent review · **Method:** re-derive every checkable
quantitative claim from source (`Appendix/MEASURED_BASELINE.md`), then read the argument for internal
consistency and for conflict with measurement.

## Verdict codes

| Code | Meaning |
|---|---|
| ✅ **VERIFIED** | Quantitative claims independently reproduced; conclusions supported |
| 🟢 **SOUND** | No checkable numbers, but argument is coherent and consistent with measurement |
| 🟡 **UNVERIFIED** | Plausible; asserts things this review could not test — treat as opinion |
| 🟠 **PARTLY CONTRADICTED** | Some claims stand, at least one is refuted by measurement |
| 🔴 **CONTRADICTED** | Central claim is refuted |
| ⚪ **NOT READ** | Outside this pass; no verdict offered |

---

## 1. Headline result — the foundation pack is better than its packaging suggested

**Three independent checks of the pack's quantitative claims all reproduced exactly:**

| Document | Claim | This review's independent measurement | Result |
|---|---|---|---|
| `Decisions/ADR-001` | "**734** direct cross-module imports" | **734** | ✅ **exact** |
| `Decisions/ADR-001` | "**14** bidirectional pairs" | **14** — and the same 14 | ✅ **exact** |
| `Modules/SOURCE_MODULE_INVENTORY.md` | "**58** top-level backend source directories", with per-module Java file counts | 58; `admin` 48, `bcm` 198, `platform` 100, `socops` 101, `sust` 140, `risk` 99, `evidence` 55, `integrations` 68, `datagov` 1 | ✅ **every count exact** |

**Conclusion.** The analytical core of the foundation pack is **genuinely measured, not asserted** — a
materially better standard than typical consultancy output, and better than this project's *own* prior
plans, which published three wrong numbers (F2-19). Where an earlier assessment
(`FOUNDATION_PACK_ASSESSMENT_AND_ACTION_PLAN.md`) treated the pack's rigour as unestablished, **that
judgement is corrected here.** Its defects were in packaging and hygiene — a broken `AGENTS.md` remote,
a `README.md` with unresolved conflict markers describing a different product — not in its analysis.

**And its single most valuable line was true.** `Executive/EXECUTIVE_VERDICT.md` states that "some
important governance decisions can be authored under caller-supplied identities." It offered no location
or proof. **This review located it and confirmed it: `CommitteeResolutionController#recordVote` accepts
the voter's name from the request body** (F2-21, now the highest-severity finding in the register). One
unproven sentence in a vendor document pointed at the platform's most serious defect.

---

## 2. Document-by-document

### Decisions/

| Document | Verdict | Validation notes |
|---|---|---|
| `ADR-001 Enforced Modular Monolith` | ✅ **VERIFIED** | Both numbers exact (above). Decision — keep one deployable, enforce boundaries with tests, extract only on measured need — is correct and matches this review's F2-08 recommendation. **Gap:** its "Proof" section requires *zero forbidden imports* and *package-only boot tests*; **neither test exists.** The ADR is accepted and unenforced. |
| `ADR-002 Platform Kernel and Installable Packages` | ✅ **VERIFIED** | The decision (mandatory kernel + independently entitled packages, server-side entitlement mandatory) is exactly right. **Its factual claim — "current licensing infrastructure exists, but controller enforcement is not platform-wide" — is precisely accurate**, and this review initially got it wrong. `module/platform/licensing/` provides `organisation_licenses` (module code, tier, seat count, validity window, enabled), a Caffeine-cached `LicensingService` with 20 canonical module codes, the `@RequireModule` annotation, a **fail-closed** `LicenseGuardAspect` (403 via `ModuleDisabledException`), an admin controller and UI, and an `enabled_modules` JWT claim. Enforcement reaches **22 of 255 controllers, all in `socops`**; the FE gate helper `moduleEnabled()` has zero callers. **Correction recorded:** F2-04 originally asserted no entitlement mechanism existed. ADR-002 was right and the reviewer was wrong — a reminder that "not found" is not "does not exist" unless you searched for the thing by its own name. |
| `ADR-003 Data Ownership and Evidence Graph` | 🟠 **PARTLY CONTRADICTED** | "Each canonical object has one owning package" is the right target and is **not the current state**: nine modules write into schemas they do not own (`incident`→`cyber` materially), and `reporting` imports peers' repositories and entities directly — the specific thing the ADR's proof forbids ("no peer repository imports"). Target sound; present tense wrong. |
| `ADR-004 Transactional Outbox and Domain Events` | 🟡 **UNVERIFIED** | `DomainEvent` and in-process publication were observed; whether a *transactional outbox* with idempotent, replayable dispatch exists was **not** verified. The ADR permits in-process delivery initially, so it is not contradicted — but the outbox itself is unconfirmed. Flagged for a follow-up check. |
| `ADR-005 Layered Tenant Isolation` | 🟢 **SOUND** | Nine enforcement layers named. Request/query/repository layers confirmed (`TenantContext`, org-scoped finders, org-check-first ordering — 10 tests added this session). Cache keys, object-storage paths, event envelopes, websocket topics and background-job boundaries were **not** verified. The ADR's own proof requirement — two-tenant negative tests across all nine — is the right test and is only partly built. |
| `ADR-006 State Machines and Approvals` | 🔴 **CONTRADICTED IN IMPLEMENTATION** | **The most important entry in this log.** The ADR is excellent: "Approvals use **immutable authenticated user IDs** and effective authority records", and its Proof demands "**forged-actor tests**". F2-21 is precisely the violation this ADR was written to prevent — committee votes are keyed to a caller-supplied *name*, and no forged-actor test exists. **The decision record is right and the code does not comply.** Keep the ADR verbatim; treat F2-21 as a compliance defect against it, which is the strongest possible framing for prioritising the fix. |
| `ADR-007 Arabic-First UX` | 🟢 **SOUND** | Supported by measurement: 5,677-key parity, 85% of pages containing Arabic, genuine RTL craft (`dir="ltr"` islands, RTL table CSS, chart-axis reversal). Undercut in execution by 431 hand-rolled files, 439 hardcoded English strings and 49 zero-Arabic pages (F2-11) — an implementation gap, not a decision defect. |
| `ADR-008 Human-Accountable AI` | 🟡 **UNVERIFIED** | `ai` module measured at 3,758 LoC / 35 endpoints / 48 tests, and 8 AI write endpoints were guarded this session (`AI_WRITE`). Whether the human-accountability *controls* the ADR specifies are implemented was not assessed. |
| `LESSONS_LEARNED.md`, `REJECTED_IDEAS.md`, `README.md` | 🟢 **SOUND** | Short, coherent, no checkable claims. `REJECTED_IDEAS` is a genuinely useful artefact most corpora lack. |

### Modules/ · Architecture/

| Document | Verdict | Validation notes |
|---|---|---|
| `Modules/SOURCE_MODULE_INVENTORY.md` | ✅ **VERIFIED** | **The best artefact in the Bible.** All 58 modules, file counts exact against independent measurement, `datagov` correctly identified as a placeholder, zero-test modules correctly flagged, and eight genuine consolidation questions posed (`audit_eng` vs `audit_lc`; `approvals` vs workflow chains vs committee decisions; `tprm` vs `scm` vs `outsourcing`; `privacy` vs `data_officer` vs `dataclass`; `*_lc` duplication; `incident` ownership; `capa` ownership; read models vs peer queries). **All eight are real and this review reaches the same conclusions independently.** Its weakness is not accuracy — it is that **no control reads it** (F2-01). |
| `Architecture/MODULE_INDEPENDENCE_AND_PACKAGING.md` | ⚪→🟡 | Read in outline only. Superseded on measured facts by `Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md`, which adds per-module tier verdicts derived from the coupling graph. |
| `Heatmaps/MODULE_INDEPENDENCE_MATRIX.md` | 🟠 **PARTLY CONTRADICTED** | The **target** interaction matrix (Q/E/R contracts) and the independence-score weighting are thoughtful and worth keeping. But its per-package rows are **aspirational target states presented in a "Baseline level" column**, and it says "No commercial package has a mandatory peer dependency **in the target**" — a target easily misread as current. Measurement: 13 modules have genuine peer dependencies, 9 have foreign schemas. Also groups the 58 packages into ~20 commercial names without stating the mapping, so it cannot be reconciled against the filesystem. **Superseded** by the SKU model, which tiers all 58 by measured evidence. |
| `Architecture/SOLUTION_ARCHITECTURE.md`, `ARCHITECTURE_PRINCIPLES.md` | 🟢 **SOUND** | Consistent with observed structure. Principles are the right ones; as with ADR-001, they are unenforced by any test. |

### Executive/ · Governance/

| Document | Verdict | Validation notes |
|---|---|---|
| `Executive/EXECUTIVE_VERDICT.md` | ✅ **VERIFIED (in its central claims)** | "Feature-rich but not yet trust-rich" is, after measurement, the correct summary. Verified: entitlements do not protect backend capability (F2-04); module separation is not an installable-product property (F2-06/F2-08); **"governance decisions can be authored under caller-supplied identities" (F2-21 — located and confirmed)**; "do not equate 2,316 green tests with acceptance readiness" (F2-18 — and F2-21 proves the point, since 2,415 green tests do not see it). Its "do not" list is sound advice this review endorses. Unverified: its claims about fake quorum beyond F2-21, and backup/restore readiness. |
| `Executive/GOVERNMENT_ACCEPTANCE_OPINION.md` | ⚪ **NOT READ** | Covered independently by `Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md`. |
| `Governance/ASSESSMENT_METHOD.md` | 🟢 **SOUND** | Explicit scope exclusions (not legal advice, not a penetration test, not a WCAG audit) — correct practice, and the same discipline as Baseline §8. Its weighted score **3.0/5 = 6.0/10** is close to this review's independent **5.3/10**; the sub-scores agree on the shape (packaging 2.4/5 and security 1.8/5 lowest, domain depth 4.2/5 highest). Two independent assessments converging is meaningful. |
| `Governance/EVIDENCE_AND_MAINTENANCE_STANDARD.md` | 🟢 **SOUND** | Read in outline. Its intent — evidence discipline for the corpus — is what F2-19 recommends making mechanical. |

### Registers/ · Roadmap/ · Product/ · UX/ · Security/ · Data/ · Process/ · Integration/ · AI/ · Diagrams/

| Document | Verdict | Validation notes |
|---|---|---|
| `Registers/FINDINGS_REGISTER.md` (599 lines) | ⚪ **NOT RE-ADJUDICATED** | The largest document in the pack. **Superseded on measured facts** by `FINDINGS_REGISTER_V2.md`, which is measurement-anchored and uses the brief's 18-field format. Retained for traceability. A full finding-by-finding adjudication is worthwhile follow-on work and was **not** done here — stated plainly rather than implied. |
| `Registers/TECHNICAL_DEBT_REGISTER.md` (22 lines) · `DECISION_REGISTER.md` (17) | 🟡 **THIN** | Too short to carry the debt of a 186 kLoC platform. Rebuild from measurement: 19 zero-test modules, 9 foreign schemas, 21 role-guarded modules, 216 entity-bound endpoints, 72 pages >500 lines, 44 mock imports, 439 hardcoded strings. |
| `Roadmap/IMPLEMENTATION_ROADMAP.md`, `FUTURE_VISION.md` | 🟢 **SOUND** | Sequencing is compatible with this review's waves. Should now be reconciled with `UNIFIED_IMPLEMENTATION_PLAN.md` so one plan governs — two roadmaps is how the 24-vs-58 divergence happened. |
| `Product/*` (Constitution, DNA & Philosophy, Strategy/Packaging/Sales, Competitive Benchmark) | 🟡 **UNVERIFIED** | Positioning and strategy — not testable by code measurement, and this review takes no view on pricing. **One caution:** any packaging claim must reconcile with F2-04 (no entitlement mechanism exists) before it reaches a customer. |
| `UX/UX_ARCHITECTURE.md`, `DESIGN_SYSTEM_SPECIFICATION.md`, `ARABIC_AND_SAUDI_UX_STANDARD.md`, `PERSONA_JOURNEYS.md` | 🟠 **PARTLY CONTRADICTED** | Corrected in the platform's favour: measurement shows a **real** design system at **99% adoption** across 329 pages and **exact** Arabic dictionary parity, so any recommendation for a *complete UX redesign* is not supported. The genuine crisis is IA — 328 routes vs 184 nav entries, 114 folders. See `UX/UX_REDESIGN_BLUEPRINT.md`. `PERSONA_JOURNEYS.md` (102 lines) is too thin for five buyer personas. |
| `Security/SECURITY_ARCHITECTURE.md`, `ABUSE_CASE_CATALOGUE.md` | 🟠 **PARTLY CONTRADICTED** | `ABUSE_CASE_CATALOGUE.md` is only 34 lines and — on the evidence of F2-21 and F2-22 — **materially incomplete**: it does not cover actor-identity forgery on approval endpoints or mass assignment via entity-bound request bodies, the two concrete security defects this review found. Rebuild it around those two classes first. |
| `Data/DATA_ARCHITECTURE.md` | 🟢 **SOUND**, with one gap | Consistent with 50 schemas / 417 entities. Does not address the **nine foreign-schema modules** (F2-06) or the dev/prod dual-mechanism divergence (F2-09). |
| `Process/BUSINESS_PROCESS_DESIGN.md`, `STATE_MACHINE_CATALOGUE.md` | 🟢 **SOUND** | The state-machine catalogue is useful and aligns with ADR-006's command-not-setter principle. It should now record, per transition, the **authority and actor-provenance rule** — the field whose absence is F2-21. |
| `Integration/INTEGRATION_ARCHITECTURE.md` | 🟡 **UNVERIFIED** | `integrations` measured at 7,568 LoC / 68 files / 6 entities **in the `platform` schema** and guarded by role lists. Connector inventory and residency claims not assessed. |
| `AI/AI_TRANSFORMATION.md` | 🟡 **UNVERIFIED** | Strategy document; no checkable claims tested. |
| `Diagrams/PLATFORM_DIAGRAMS.md` | 🟢 **SOUND** | Structural diagrams consistent with the measured module graph. |

---

## 3. What this validation changes

1. **Trust `SOURCE_MODULE_INVENTORY.md` and `ADR-001`.** They are verified. Build the module registry
   (F2-01) directly from the former rather than re-deriving it.
2. **Keep every ADR — and treat ADR-006 as the authority behind F2-21.** The decision records are the
   strongest part of the corpus; the defect is that nothing enforces them. Each ADR's "Proof" section is
   effectively a list of tests that do not yet exist. **Turning those Proof sections into actual CI
   tests is the single highest-leverage governance action available**, because the decisions are already
   right.
3. **Correct three present-tense claims** that describe targets as current state: ADR-002 (licensing
   infrastructure "exists"), ADR-003 (one owning package per object), `MODULE_INDEPENDENCE_MATRIX.md`
   (no mandatory peer dependencies). Add a `Status: TARGET` / `Status: CURRENT` marker to every table.
4. **Do not act on the "complete UX redesign" recommendation.** Measurement contradicts it; the IA
   redesign is the real work.
5. **Rebuild three thin documents** from measurement: `TECHNICAL_DEBT_REGISTER.md`,
   `ABUSE_CASE_CATALOGUE.md`, `PERSONA_JOURNEYS.md`.
6. **Reconcile the two roadmaps** into one governing plan.

## 4. What this validation did *not* do

- Adjudicate the 599-line `FINDINGS_REGISTER.md` finding by finding.
- Read `GOVERNMENT_ACCEPTANCE_OPINION.md`, or the Product/AI strategy documents beyond their claims.
- Verify ADR-004's transactional outbox, ADR-005's cache/storage/event/scheduler isolation layers, or
  ADR-008's AI accountability controls.
- Test any competitor claim. Competitor statements in this Bible describe publicly observable licensing
  and positioning behaviour only.
