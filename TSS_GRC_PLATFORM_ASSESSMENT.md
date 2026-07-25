# TSS GRC Platform — Independent Assessment

**Single-file consolidation of the 2026-07-25 independent review.**

| | |
|---|---|
| **Date** | 2026-07-25 |
| **Tree assessed** | `master` `99a719da` (measurements) · report generated at `3128a629` |
| **Standpoint** | Independent enterprise product review — CPO · Enterprise Architect · CTO · Principal UX · GRC SME · Security Architect · Saudi regulatory consultant · government acceptance committee · enterprise customer · investor technical due diligence |
| **Method** | Static measurement of the source tree, live inspection of running code, and re-derivation of every checkable claim in the platform's existing documentation. Not a penetration test, not a WCAG conformance audit, not a load-test campaign — see Part 3 §8 for the explicit limits |
| **Verdict** | **Conditional acceptance · 5.5 / 10** — "strong engineering, unpackaged product, one unenforceable core control" |
| **Regenerate** | `py TSS-GRC-Platform-Bible/Appendix/scripts/build_single_report.py` |

> **Evidence rule.** No claim in this report is asserted without provenance. Every quantitative statement
> traces to **Part 3 (Measured Baseline)**, which names the script that reproduces it; every finding in
> **Part 2** carries a re-verification command in **Part 6 §4**. Statements that are judgement rather than
> measurement are marked **OPINION**. Competitor statements describe publicly observable licensing and
> positioning behaviour, not verified internals.

---

## The five things that matter

1. **Committee votes are forgeable.** `recordVote` takes the voter's name from the request body, with no
   check that it is the caller or even a member of the committee. Recusal, conflict-of-interest, quorum
   and segregation of duties all evaluate the *claimed* name; another member's cast vote can be
   overwritten; the audit ledger records the forged name; and the SHA-256 e-signed minutes attest to it.
   The correct pattern exists one file away in `approveMinutes`. **~2 days to fix, and it is the platform's
   central control claim.** (F2-21)

2. **The Saudi differentiators are the cleanest code and the least assured.** `sharia`, `openbanking`,
   `outsourcing`, `rrp`, `cpty`, `regreporting`, `reginspect`, `fraud`, `opres`, `dataclass`, `nca_ecc`
   have **zero peer-module dependencies** — and **zero tests**. Nothing blocks selling them except tests
   and a permission catalogue. The moat exists in code and is unproven in CI. (F2-03, Part 4)

3. **The entitlement subsystem is built and 8.6% wired.** A complete licensing layer exists — tier, seats,
   validity, a fail-closed AOP guard, a JWT claim, an admin screen — applied to 22 of 255 controllers, all
   in one module. Dev tenants 2 and 3 are licensed for `SOCOPS` alone and can use all 58 modules. The
   per-module business model is days of rollout, not weeks of build. (F2-04)

4. **Three executive-facing surfaces render fabricated data** — global search, the executive dashboard and
   the board pack. A governance product may not invent records. Days to fix. (F2-02)

5. **The frontend's assets are better than its documentation claims, and its navigation is worse.**
   99% shared-component adoption and exact 5,677-key Arabic/English parity mean a visual redesign would
   destroy value; meanwhile **~144 of 328 routed screens are absent from navigation**. Redesign the
   information architecture, keep the design system. (Part 5)

**What this review got wrong, three times.** "Not found" is not "does not exist": the entitlement
subsystem, the `reporting` export toolkit, and an entire tracked 98-file audit evidence vault (k6 load
tests, axe/Lighthouse accessibility, gitleaks, trivy, bandit, jscpd, JaCoCo coverage) were each asserted
absent before being located. Every correction is recorded in place rather than quietly fixed — Part 3
§7.1, Part 7 §1. A reader should weight the corrections when judging the rest.

---

## Contents

1. **[Independent Acceptance Verdict](#part-1-independent-acceptance-verdict)** — Three verdicts — acceptance committee, enterprise customer, investor DD — 13-dimension scorecard, four conditions, 30-day plan.
2. **[Findings Register](#part-2-findings-register)** — 22 findings in full 18-field format, severity-ranked, each anchored to measured evidence.
3. **[Measured Baseline — the evidence base](#part-3-measured-baseline-the-evidence-base)** — Every number in this report, with the script that reproduces it. Cite this, not prose.
4. **[Module Separability & SKU Model](#part-4-module-separability-sku-model)** — All 58 modules tiered A/B/C/K from the coupling graph; 14-SKU catalogue; what actually blocks per-module sale.
5. **[UX Redesign Blueprint](#part-5-ux-redesign-blueprint)** — Why a visual redesign is the wrong call, the information-architecture redesign that is the right one, Arabic convergence, accessibility.
6. **[Documentation Register & Audit Support](#part-6-documentation-register-audit-support)** — 331 tracked documents across 8 corpora; what governs vs what is historic; a re-verification command for every finding.
7. **[Validation of the Existing Corpus](#part-7-validation-of-the-existing-corpus)** — Per-document verdict on the pre-existing 46-file reference pack, and the three claims this review got wrong.
8. **[ADR-009 — Module Registry as a Build Artefact](#part-8-adr-009-module-registry-as-a-build-artefact)**
9. **[ADR-010 — Permission-Only Authorisation](#part-9-adr-010-permission-only-authorisation)**
10. **[ADR-011 — Schema Ownership Equals Module Ownership](#part-10-adr-011-schema-ownership-equals-module-ownership)**
11. **[ADR-012 — Boundary Enforcement and Consumer-Owned Ports](#part-11-adr-012-boundary-enforcement-and-consumer-owned-ports)**

Appendices, kept as separate files because they are data rather than prose:

- [`DOCUMENT_REGISTER.csv`](TSS-GRC-Platform-Bible/Appendix/DOCUMENT_REGISTER.csv) — all 331 tracked Markdown files with git provenance
- [`Appendix/scripts/`](TSS-GRC-Platform-Bible/Appendix/scripts/) — the six measurement scripts that reproduce every number here
- `audit/` — the tracked 98-file evidence vault from the 2026-06-18 audit (k6, axe, Lighthouse, gitleaks, trivy, bandit, jscpd, JaCoCo)

---

## Part 1: Independent Acceptance Verdict

*Source: [`Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md`](TSS-GRC-Platform-Bible/Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md)*

**Date:** 2026-07-25 · **Tree:** `master` `99a719da` · **Basis:** `Appendix/MEASURED_BASELINE.md`,
`Registers/FINDINGS_REGISTER_V2.md`, `Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md`,
`UX/UX_REDESIGN_BLUEPRINT.md`.

**Standpoint.** Written as if I had never seen this codebase before and had no stake in it. Three hats,
answered separately because they reach different verdicts: **government acceptance committee**,
**enterprise customer**, **investor technical due diligence**.

---

### 1. The one-paragraph verdict

This is a **substantial, genuinely engineered enterprise platform** — 186,285 lines of module code, 417
entities, 2,160 endpoints, 50 schemas, 304 migrations, 2,415 passing tests, a 5,677-key bilingual
dictionary at exact Arabic/English parity, a 144-component design system adopted by 99% of 329 screens,
and RTL-native Arabic export across 22 generators. It is not a prototype and it is not vapour. It is
also **not yet a product line**: it has a complete entitlement subsystem wired to 8.6% of its controllers,
11% of its module code has no tests — concentrated precisely in its Saudi differentiators — and the three screens an
executive opens first render fabricated data. **None of the blockers are architectural dead ends.** The
most valuable finding in this review is that the modules hardest to sell are not the ones anyone
expected: the Saudi regulatory modules are the *cleanest* code on the platform and need only tests and a
permission catalogue, while the coupling problems sit in the large, well-tested modules where they are
least commercially urgent.

One late finding outranks everything else in this document and was verified in code during the review:
**committee votes are recorded under a name supplied by the caller**, so recusal, quorum and segregation
of duties are all bypassable by a single actor, and the e-signed minutes attest to forgeable content
(F2-21). It is roughly two days' work to fix. Until it is fixed, the platform's central control claim
does not hold.

**Verdict: conditional acceptance.** Not "reject" — the engineering is real and the defect is small,
contained and already solved elsewhere in the same codebase. Not "accept" — the four conditions below
are non-negotiable for a governance product.

---

### 2. Government acceptance committee

An acceptance committee samples: it picks a control, asks to see it enforced, asks for the evidence, and
asks who approved it.

| Test | Result |
|---|---|
| Is Arabic the primary interface? | **Partial.** 5,677-key parity and 85% of screens contain Arabic — but **49 screens (15%) contain none**, and 439 strings are hardcoded English that no translation process can reach. Not a passing answer to "is the interface Arabic?" |
| Are approvals segregated and evidenced? | **No — and this is the finding that would fail the review.** The machinery is impressive: server-enforced four-eyes returning 409, quorum and recusal logic, SHA-256 e-signed minutes, an audit ledger. But **committee votes are recorded under a name supplied in the request body, with no check that it is the caller or even a member of the committee** (F2-21). Recusal, COI, quorum and Track-D segregation of duties all evaluate the *claimed* name, so all four are bypassable by one actor; another member's cast vote can be overwritten; and the audit ledger records the forged name. The e-signature is cryptographically sound and attests to content whose votes are forgeable. **The controls are built and they are not enforceable.** |
| Can a sampled control be shown *in force*? | **Not reliably.** `admin.committee_gate_map` was **empty in every tenant** while the authorisation matrix demanded a committee — the flagship gating control was implemented and inert. Fixed for one tenant; the *class* of defect (configuration-gated controls with no health check) is open. **This is the finding I would write up.** |
| Are records trustworthy? | **Not on three surfaces.** Global search, the executive dashboard and the board pack are mock-fed. A board pack containing fabricated risk figures is a governance incident in a governance product. |
| Are Saudi frameworks covered? | **Broadly yes** — NCA ECC, SAMA CSF/outsourcing/open banking/RRP, PDPL, NDMO/SDAIA classification, CMA/TCFD/IFRS S1-S2, Sharia governance. **But `nca_ecc`, `outsourcing`, `openbanking`, `rrp`, `sharia`, `data_officer` and `dataclass` have zero automated tests** — the framework coverage is asserted, not proven. |
| Is the audit trail complete? | **Largely** — an audit ledger exists and is used. Off-request/scheduler paths have historically been fragile (a documented landmine) and warrant sampling. |

**Committee verdict: defer acceptance pending Condition 1; then accept subject to Conditions 2–4.**
The governance machinery is genuinely ambitious and, in design, better than the commercial comparators —
no competitor ships quorum-aware, recusal-enforcing, e-signed committee minutes with Arabic RTL output.
But a committee cannot accept a control it can bypass in one request, so vote provenance is a gate, not
a condition. After it: full Arabic coverage; no fabricated data on any surface; and a demonstrable answer
to "which of your controls are currently in force?"

**A note on how this was found, because it matters to the committee's confidence in the rest.** The vote
defect is invisible to the platform's own test suite: 19 recusal tests and 19 tally tests pass, because
each supplies the member name as an input and verifies the logic *given* that name. They test the
decision, never the provenance of the identity making it. That is not a criticism of those tests — it is
the structural limit of unit tests over mocked inputs, and the reason Condition 3 requires **live probes**
against a running system rather than more of the same.

---

### 3. Enterprise customer (Saudi bank / government entity)

| Question | Answer today |
|---|---|
| Can I buy only the modules I need? | **Not yet — but the mechanism is built.** A full licensing subsystem exists (`organisation_licenses` with tier, seats and validity; a fail-closed AOP guard; an admin screen) and is applied to **22 of 255 controllers, all in one module**. Dev tenants 2 and 3 are licensed for `SOCOPS` alone and can use all 58 modules. This is a rollout of days, not a build. |
| Can I define my own roles and grant granular permissions? | **For 37 of 58 modules.** The other 21 compare hard-coded role *names*, so a custom role granted `SHARIA_WRITE` still gets 403. Least privilege is unachievable there. |
| Can my DBA see clean data ownership? | **Mostly.** 50 schemas, one per module — except nine, of which `incident` writing into the `cyber` schema is the one that will be questioned. |
| Will my users find the features I paid for? | **Probably not all.** ~144 of 328 screens are reachable only by typing a URL. |
| What are the performance limits? | **Partly known.** A k6 load test (2026-06-18) shows **p95 26.9 ms, 0 errors over 18,763 requests** up to a 100-VU spike — on **one read endpoint of 2,160**, at dev data volumes. No write-path test, no capacity model, no per-endpoint p95 target. |
| Is it stable in production? | **A known daily failure is live.** The break-glass sweep defect (33–35 failures/day) is fixed, built and staged — and the service has not been restarted. |
| Can I get my data out? | **Yes** — strong export coverage (PDF/XLSX/DOCX/PPTX, RTL Arabic) across 22 generators. Genuine strength. |

**Customer verdict: buyable for a suite deployment; not yet buyable module-by-module.**

---

### 4. Investor technical due diligence

**What I would pay for.** Real domain depth in a hard, defensible niche — 186 kLoC of GRC domain logic
that encodes Saudi regulation is not reproducible by a competitor in a quarter. Arabic-first with
RTL-native exports is a moat the five global comparators do not contest. The committee/approval engine,
the evidence subsystem with SHA-256 integrity, the workflow engine adopted by 14 modules, and the
authorisation architecture (a single `ModulePermissionEvaluator` choke point) are competent, coherent
platform engineering. 2,415 tests and a **ratcheted** authorisation control driven 62 → 21 in one work
programme shows a team that can execute systematically when pointed at a problem.

**What I would discount for.**

| Risk | Discount driver |
|---|---|
| **Forgeable committee decisions** | The product's central control — segregated, evidenced, recusal-enforcing approval — is bypassable in one request (F2-21). Two days to fix; the discount is for the *review gap* that let a control ship unenforceable, not for the code. |
| **Entitlement built, not rolled out** | The per-module business model rests on a subsystem that exists, works and is wired to 8.6% of controllers. The discount is small and the upside is immediate — the asset is already paid for. |
| **Differentiators unassured** | 20,503 LoC / 11% of module code with zero tests, concentrated in the Saudi modules that justify premium pricing. The moat is unverified. |
| **Documentation understates the product by 40%** | The datasheet says 24 modules; there are 58. Wrong numbers propagated through the project's *own* plans — a control-environment signal, not a typo. |
| **Fabricated data in shipped artefacts** | Cheap to fix, expensive if found by a customer first. |
| **Key-person / tribal knowledge** | 25 documented "landmines" (JDK pinning, ROWVERSION double-flush, transaction wiring, `LIKE` bracket traps) indicate significant undocumented-until-recently operational knowledge. Well-documented *now* — which is the mitigation. |
| **No performance evidence** | Cannot underwrite an enterprise SLA. |

**DD verdict: proceed, with the remediation plan as a condition of close.** The engineering asset is real
and the gaps are unusually *tractable* — mostly additive work (tests, catalogue rows, one entitlement
table, one package move) rather than redesign. That profile is the good kind of technical debt.

---

### 5. The four non-negotiable conditions

Everything else can be scheduled. These four cannot ship as they are:

#### Condition 1 — Committee decisions must be attributable *(2 days)*
`recordVote` must derive the voter from `Authentication`; a body-supplied `memberName` is honoured only
under an explicit secretary/chair authority and is then stamped `recorded_by … on_behalf_of …`; a name
that is not a registered member of that committee is rejected unconditionally; recusal is evaluated
against the resolved identity. Existing rows get `recorded_by_user_id NULL` = *provenance unknown
(pre-fix)* — **visible, not back-filled with a guess**. **In a GRC product, an unattributable approval
is not an approval.** This is the cheapest high-severity fix available and it comes first.

#### Condition 2 — No fabricated data on any surface *(days)*
De-mock `lib/searchIndex.ts`, `pages/dashboard/widgets.tsx`, `lib/boardPack.ts`; add the CI rule
(no `/mock/i` import outside tests) and the backend-down acceptance test. **A governance product may not
invent records.** Non-negotiable regardless of commercial model.

#### Condition 3 — Prove the differentiators *(one quarter, parallelisable)*
Tier-1 test floors on `outsourcing`, `nca_ecc`, `sharia`, `data_officer`, `openbanking`, `dataclass`,
`rrp` — every state transition and authorisation branch, plus one live HTTP probe per write endpoint.
**Nothing regulator-facing may be sold on the promise that it works.**

#### Condition 4 — Say what a customer bought *(days — the subsystem already exists)*
Extend `KNOWN_MODULES` to all 58 codes, apply the existing `@RequireModule` at class level across business controllers, wire the existing `moduleEnabled()` FE helper into navigation, and backfill licence rows before enforcing.
**Required if — and only if — per-module sale is the commercial model.** If it is not, say so explicitly
and F2-04/F2-05/F2-06 drop from blockers to hygiene. **This is the one decision this review cannot make
for the business.**

---

### 6. Scorecard

| Dimension | Score | Basis |
|---|:--:|---|
| Domain depth & regulatory coverage | **8/10** | 58 modules across ERM, compliance, cyber, BCM, privacy, TPRM, ESG, IAM, Saudi financial-sector regulation |
| Platform engineering | **7/10** | Single authorisation choke point, workflow engine in 14 modules, evidence integrity, audit ledger, 304 migrations — the correct actor-identity pattern exists (`approveMinutes`) and simply was not applied everywhere |
| Assurance | **5/10** | 2,415 tests, but 11% of module code untested and concentrated in the differentiators; unit-level only on the governance core |
| Arabic / localisation | **7/10** | Exact dictionary parity and real RTL craft, undercut by 431 hand-rolled files, 439 hardcoded strings, 49 English-only screens |
| Design system | **8/10** | 144 components, 99% adoption, tokenised |
| Information architecture | **3/10** | 184-item sidebar exposing 56% of 328 routes; 114 folders for 58 modules |
| Data architecture | **7/10** | One schema per module — with nine exceptions, one of which is commercially material |
| Modularity / separability | **6/10** | 31 of 58 modules separable with non-architectural work; 13 genuinely coupled |
| **Commercial packaging readiness** | **3/10** | Entitlement subsystem built (tier/seats/validity, fail-closed guard) but wired to 8.6% of controllers and unconsumed by the FE; module inventory understated by 40% in the operative documents |
| Security architecture | **4/10** | Guard *coverage* ratcheted 62→21 — but committee vote provenance is forgeable (F2-21), 21 modules bypass the permission catalogue, 216 endpoints bind entities from the body, and grant tightness is unmeasured |
| Operations | **5/10** | Known-fixed defects live in production; dev/prod schemas built by two mechanisms with no convergence proof |
| Performance / scalability | **4/10** | A real k6 harness with 4 scenarios exists and the one path measured is excellent (p95 26.9 ms, 0/18,763 errors) — but it is 1 of 2,160 endpoints, read-only, at dev volumes, with no capacity model |
| Documentation | **5/10** | Extensive (331 tracked files, 74,878 lines) and now partly generated — but has asserted wrong numbers including in its own plans, 65% is stale and unmarked, a stale worktree duplicates the corpus, and two competing generations of the Saudi pack both claim authority |

**Weighted overall: 5.5 / 10 — "strong engineering, unpackaged product, one unenforceable core control."**

The gap between the domain score (8) and the packaging score (2) *is* the finding. The hard part —
building 186 kLoC of Saudi GRC domain logic — is done. The easy part — declaring what exists, proving
it works, and stating what a customer bought — is not. That is a far better position than the reverse,
and it is unusual enough to be worth saying plainly: **the remaining work is mostly additive, and almost
none of it requires undoing what has been built.**

---

### 7. What I would do in the first 30 days

| Day | Action | Why |
|---|---|---|
| **1–2** | **Fix committee vote provenance (F2-21)** | Two days. Until then recusal, quorum and SoD are advisory, and the e-signed minutes attest to forgeable votes |
| 1 | Restart the service to activate the staged jar | Two known defects, one of them failing 33–35×/day, are fixed and not running |
| 1–3 | De-mock the three executive surfaces + CI rule | Removes the existential trust risk before the next demo |
| 2–4 | Route reconciliation matrix | Highest information value per hour: makes ~144 unknown screens a decision list |
| 3–5 | Promote the export toolkit to the kernel | ~1 day; improves separability for eight modules including the two largest |
| 5–10 | Module registry + CI check; delete `datagov` | The spine everything else attaches to; ends the 24-vs-58 discrepancy permanently |
| 8–12 | Ratcheted boundary test; grant-tightness report | Two cheap tests that stop two problems getting worse |
| 10–20 | Tier-1 tests for `outsourcing` + `nca_ecc` | Proves the two most regulator-salient modules; establishes the pattern for the rest |
| 15–25 | Roll out `@RequireModule` + wire the FE gate + licence backfill *(if per-module sale is confirmed)* | Makes the business model real using a subsystem that already exists |
| 20–30 | i18n lint ratchets + the 49 zero-Arabic screens | Closes the visible gap in the primary differentiator |

Notably, **nine of the ten** are small, independent, and need no decision from anyone. Only the
entitlement work waits on a business answer.

---

## Part 2: Findings Register

*Source: [`Registers/FINDINGS_REGISTER_V2.md`](TSS-GRC-Platform-Bible/Registers/FINDINGS_REGISTER_V2.md)*

**Date:** 2026-07-25 · **Tree:** `master` `99a719da` · **Assessor:** independent review team
(CPO / Enterprise Architect / CTO / Principal UX / GRC SME / Security Architect / Saudi regulatory /
government acceptance committee / enterprise customer / technical due-diligence investor).

**Evidence rule.** Every finding cites `Appendix/MEASURED_BASELINE.md` by section. Statements that are
judgement rather than measurement are marked **OPINION**. Competitor statements describe **market
positioning and licensing models** — publicly observable commercial behaviour — not verified internals.

**Relationship to the previous register.** `Registers/FINDINGS_REGISTER.md` (599 lines, vendor
foundation pack) is *superseded on measured facts* and retained for traceability. Where it and this
document disagree, this one cites a reproducible number; see `Governance/BIBLE_VALIDATION_LOG.md`.

### Severity scale

| | Meaning |
|---|---|
| **P0 Blocker** | Prevents a stated commercial objective, or ships a known-false artefact to a customer/regulator |
| **P1 Critical** | Material risk to a live deal, an audit, or production integrity |
| **P2 High** | Structural debt that compounds; fix before scaling the team or the customer base |
| **P3 Medium** | Real but contained; schedule it |

### Summary

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

### F2-01 · 34 undocumented module packages; one module is empty

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

### F2-02 · Global search, executive dashboard and board pack render mock data

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

### F2-03 · The Saudi-differentiating modules are the untested ones

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

### F2-04 · No per-module entitlement — the per-module SKU strategy is technically unimplementable

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

### F2-05 · 21 modules guard by role name, so tenant RBAC has no effect on them

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

### F2-06 · Nine modules persist into a schema they do not own

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

### F2-07 · 144 routed pages are absent from navigation

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

### F2-08 · `reporting` and `audit_committee` are god-consumers; no boundary is enforced

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

### F2-09 · Dev and prod schemas are produced by two different mechanisms

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

### F2-10 · Production runs code with two known, already-fixed defects

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

### F2-11 · Bilingual delivery is split across two mechanisms; 439 strings are hardcoded English

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

### F2-12 · `ndi`: 127 endpoints, 82 writes, 11 tests, behind a 4,970-line screen

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

### F2-13 · The authorisation ratchet measures coverage, not tightness

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

### F2-14 · Config-gated controls with no config-health check (a defect *class*)

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

### F2-15 · 3,650 lines of fabricated records ship in the production bundle

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

### F2-16 · Two page monoliths carry 7.7% of all frontend code

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

### F2-17 · Zero performance or load evidence for 2,160 endpoints

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

### F2-18 · The governance core is assured at unit level only

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

### F2-19 · Documentation asserts numbers nothing verifies — including this project's own plans

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

### F2-23 · No role holds `ADMIN:WRITE`, so only `PLATFORM_ADMIN` can record a committee vote

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

### F2-20 · `datagov` — a documented module with no implementation

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

### F2-21 · Committee votes are recorded under a caller-supplied name

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

### F2-22 · 216 write endpoints bind a JPA entity directly from the request body

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

### Sequencing — what to do, in what order

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

---

## Part 3: Measured Baseline — the evidence base

*Source: [`Appendix/MEASURED_BASELINE.md`](TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md)*

**Assessor:** independent review (Claude), acting as Enterprise Architect / CPO / Security Architect /
UX Principal / GRC SME / acceptance committee.
**Measured:** 2026-07-25, working tree `D:\AYMAN\GRC` @ `master` `99a719da`.
**Method:** static measurement of the actual source tree — not documentation, not interviews.
**Reproduce:** `scratchpad/coupling.py`, `verify.py`, `verify2.py` (committed under `Appendix/scripts/`).

> **Standing rule for this Bible.** No claim enters any document in this repository unless it is
> (a) reproducible from this baseline, (b) a live probe result with the command shown, or (c) explicitly
> labelled **OPINION** / **ASSUMPTION**. Every finding in `Registers/FINDINGS_REGISTER_V2.md` cites a
> row here. Documents that assert numbers without provenance are flagged in
> `Governance/BIBLE_VALIDATION_LOG.md`.

---

### 0. Headline totals

| Metric | Measured | Where the platform's own docs say otherwise |
|---|---:|---|
| Backend module packages | **58** (57 with code + 1 empty) | `CLAUDE.md` lists **24** — 34 packages undocumented |
| Module Java LoC | **186,285** | — |
| JPA entities | **417** | — |
| REST endpoint mappings | **2,160** | — |
| Write endpoints (`POST/PUT/PATCH/DELETE`) | **1,151** | — |
| `@PreAuthorize` annotations | **2,268** | — |
| Distinct permission modules used by guards | **27** | catalogue documented as 24 modules / 94 permissions |
| Module packages with **zero** `hasPermission` usage | **21 of 58** | — |
| Tests attributed to modules | **2,339** (suite total 2,415) | — |
| Module packages with **zero tests** | **19**, totalling **20,503 LoC (11.0%)** | — |
| Distinct JPA schemas | **50** | matches `CLAUDE.md` "50+" ✅ |
| Flyway migrations on disk | **304** files, `V1`–`V314`, 3.83 MB | matches (next free **V315**) ✅ |
| Frontend pages (`.tsx` under `pages/`) | **329** in **114** folders | — |
| Routes registered in `App.tsx` | **328** | — |
| Sidebar navigation entries | **184** | — |
| Mock-data import statements | **44** in **29** files | prior internal plans said **53** — corrected here |
| Mock data files still shipped | **15**, **3,650 lines** | — |

**Three corrections to numbers this project has previously published, including by me:**

| Claim | Published as | Measured | Consequence |
|---|---|---|---|
| Backend modules | 24 (`CLAUDE.md`, architecture pack) | **58** packages | Scope statements, licensing and test-bar plans were sized against 40% of the codebase |
| FE mock import sites | 53 (`REMEDIATION_MASTER_PLAN.md`, `UNIFIED_IMPLEMENTATION_PLAN.md` W2-1) | **44** statements / 29 files | W2-* work items were over-scoped; the *concentration* matters more than the count (see §5) |
| `ck_change_status` existing values | "13 existing statuses" (`UNIFIED_IMPLEMENTATION_PLAN.md` X-2) | **14** (`V300` lines 13–16) | Target of 17 is still correct (14 + 3 new); the arithmetic in the plan was off by one |

---

### 1. Module census — the full 58

Sorted by LoC. `out` = distinct peer modules imported; `edges` = import statements to peers;
`in` = distinct peer modules importing this one. `perm` = permission modules named in
`hasPermission(...)`; **`ROLE`** = guards use hard-coded `hasAnyRole(...)` role lists instead.

| # | Module | LoC | Ent | Endpts | Writes | Tests | out | edges | in | Auth style | FE | Schema |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---|:--:|---|
| 1 | `bcm` | 25,130 | 50 | 261 | 139 | 395 | 7 | 88 | 2 | BCM | ✅ | `bcm` |
| 2 | `sust` | 16,704 | 34 | 226 | 129 | 200 | 6 | 55 | 2 | SUST | — | `sust` |
| 3 | `platform` | 10,100 | 24 | 122 | 60 | 169 | 9 | 34 | **45** | multi | ✅ | `platform` |
| 4 | `risk` | 8,955 | 23 | 101 | 65 | 147 | 5 | 28 | 8 | RISK | ✅ | `risk` |
| 5 | `ndi` | 7,717 | 15 | 127 | 82 | **11** | 3 | 7 | 1 | NDI | ✅ | `ndi` |
| 6 | `integrations` | 7,568 | 6 | 24 | 12 | 119 | 3 | 21 | 0 | **ROLE** | — | ⚠ `platform` |
| 7 | `cyber` | 7,269 | 16 | 98 | 61 | 117 | 5 | 29 | 3 | CYBER | ✅ | `cyber` |
| 8 | `socops` | 6,787 | 25 | 111 | 58 | **36** | 4 | 73 | 1 | **ROLE** | ✅ | `socops` |
| 9 | `privacy` | 5,804 | 16 | 99 | 62 | 79 | 2 | 12 | 3 | PRIVACY | ✅ | `privacy` |
| 10 | `compliance` | 5,659 | 11 | 81 | 41 | 107 | 5 | 27 | 4 | COMPLIANCE | ✅ | `compliance` |
| 11 | `tprm` | 5,399 | 12 | 70 | 43 | 109 | 5 | 18 | 5 | TPRM | ✅ | `tprm` |
| 12 | `workflow` | 5,127 | 14 | 44 | 26 | 99 | 4 | 26 | 1 | multi | ✅ | `workflow` |
| 13 | `reporting` | 5,107 | 2 | 27 | 6 | 26 | **8** | **74** | 8 | REPORTING | ✅ | — |
| 14 | `ethics` | 4,841 | 14 | 72 | 44 | 83 | 1 | 8 | 1 | COMPLIANCE | ✅ | `ethics` |
| 15 | `admin` | 4,705 | 14 | 59 | 27 | 67 | 3 | 19 | 5 | ADMIN | ✅ | `admin` |
| 16 | `iam` | 3,913 | 10 | 36 | 22 | 31 | 2 | 19 | 9 | IAM | — | `iam` |
| 17 | `scm` | 3,808 | 14 | 66 | 45 | **15** | 1 | 2 | 1 | SCM | ✅ | `scm` |
| 18 | `ai` | 3,758 | 8 | 35 | 20 | 48 | 3 | 14 | 1 | AI | ✅ | `ai`,`ai_governance` |
| 19 | `evidence` | 3,683 | 11 | 37 | 19 | 21 | 2 | 12 | 7 | EVIDENCE | ✅ | `evidence` |
| 20 | `audit_lc` | 3,177 | 8 | 50 | 32 | 58 | 1 | 7 | 2 | AUDIT | — | `audit_lc` |
| 21 | `data_officer` | 2,373 | 1 | 37 | 21 | **0** | 1 | 1 | 0 | PRIVACY | ✅ | `data_officer` |
| 22 | `sharia` | 2,234 | 6 | 21 | 12 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `sharia` |
| 23 | `aml` | 2,165 | 4 | 14 | 7 | 47 | 1 | 6 | 0 | **ROLE** | — | `aml` |
| 24 | `ccm` | 2,031 | 2 | 10 | 5 | 39 | 1 | 4 | 2 | **ROLE** | ✅ | `ccm` |
| 25 | `change_mgmt` | 2,022 | 7 | 30 | 15 | 36 | 3 | 12 | 1 | CHANGE | — | `change_mgmt` |
| 26 | `openbanking` | 1,913 | 5 | 18 | 9 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `openbanking` |
| 27 | `rrp` | 1,910 | 5 | 17 | 10 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `rrp` |
| 28 | `governance` | 1,857 | 1 | 7 | 1 | 30 | **8** | 32 | 1 | **ROLE** | ✅ | ⚠ `platform` |
| 29 | `policy` | 1,774 | 5 | 27 | 17 | 39 | 4 | 14 | 5 | COMPLIANCE | ✅ | `policy` |
| 30 | `cpty` | 1,688 | 5 | 14 | 6 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `cpty` |
| 31 | `outsourcing` | 1,655 | 5 | 17 | 9 | **0** | 1 | 1 | 0 | **ROLE** | ✅ | `outsourcing` |
| 32 | `regreporting` | 1,628 | 3 | 17 | 9 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `regreporting` |
| 33 | `incident` | 1,585 | 2 | 22 | 14 | 31 | 2 | 7 | 5 | INCIDENT,CAPA | — | ⚠ `cyber` |
| 34 | `audit_committee` | 1,581 | 2 | 7 | 4 | 26 | **10** | 29 | 0 | **ROLE** | ✅ | `audit_committee` |
| 35 | `consumer` | 1,420 | 3 | 11 | 6 | 20 | 1 | 2 | 0 | **ROLE** | ✅ | `consumer` |
| 36 | `fraud` | 1,301 | 3 | 10 | 5 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `fraud` |
| 37 | `reginspect` | 1,160 | 3 | 10 | 5 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `reginspect` |
| 38 | `dataclass` | 988 | 4 | 13 | 7 | **0** | 1 | 1 | 0 | **ROLE** | ✅ | `dataclass` |
| 39 | `opres` | 950 | 3 | 9 | 6 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `opres` |
| 40 | `auth` | 894 | 1 | 7 | 6 | 22 | 2 | 4 | 0 | **ROLE** | — | ⚠ `platform` |
| 41 | `signature` | 811 | 2 | 10 | 5 | 11 | 1 | 2 | 1 | SIGNATURE | — | `signature`,⚠`platform` |
| 42 | `intake` | 662 | 2 | 4 | 2 | 3 | 0 | 0 | 1 | INTAKE | ✅ | `intake` |
| 43 | `risk_lc` | 584 | 3 | 5 | 3 | 6 | 1 | 2 | 1 | RISK | — | `risk_lc` |
| 44 | `attachment` | 509 | 1 | 7 | 4 | **0** | 0 | 0 | 2 | EVIDENCE | — | ⚠ `platform` |
| 45 | `cyber_lc` | 506 | 1 | 5 | 3 | 11 | 1 | 2 | 0 | CYBER | — | `cyber_lc` |
| 46 | `workflow_submission` | 494 | 1 | 6 | 3 | 35 | 1 | 2 | 0 | WORKFLOW | — | ⚠ `workflow` |
| 47 | `approvals` | 470 | 1 | 5 | 3 | 12 | 1 | 2 | 0 | WORKFLOW | ✅ | `approvals` |
| 48 | `capa` | 464 | 1 | 6 | 3 | 12 | 1 | 2 | 6 | CAPA | ✅ | `capa` |
| 49 | `regchange` | 444 | 1 | 5 | 3 | 6 | 1 | 2 | 0 | COMPLIANCE | — | `regchange` |
| 50 | `privacy_lc` | 416 | 1 | 5 | 3 | 10 | 1 | 2 | 2 | PRIVACY | — | `privacy_lc` |
| 51 | `bcm_lc` | 391 | 1 | 5 | 3 | 6 | 0 | 0 | 0 | BCM | — | `bcm_lc` |
| 52 | `controlmap` | 384 | 1 | 3 | 1 | **0** | 1 | 7 | 0 | COMPLIANCE | — | ⚠ `evidence` |
| 53 | `nca_ecc` | 368 | 1 | 6 | 3 | **0** | 0 | 0 | 0 | COMPLIANCE,CYBER | ✅ | `nca_ecc` |
| 54 | `dashboard` | 362 | 0 | 8 | 0 | **0** | 5 | 9 | 0 | multi | ✅ | — |
| 55 | `knowledge` | 355 | 1 | 6 | 4 | **0** | 0 | 0 | 0 | **ROLE** | — | ⚠ `kb` |
| 56 | `audit_eng` | 354 | 1 | 5 | 3 | **0** | 0 | 0 | 0 | AUDIT | — | `audit_eng` |
| 57 | `board_pack` | 353 | 1 | 5 | 3 | **0** | 0 | 0 | 0 | **ROLE** | ✅ | `board_pack` |
| 58 | `datagov` | **18** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | — | — | — |

#### 1.1 `datagov` is an empty module
`module/datagov/` contains exactly one file — `package-info.java`, 18 lines, no code. `CLAUDE.md`
names `datagov` in its module list. **A named module with zero implementation is in the platform's
own documentation.** (Finding F2-01.)

---

### 2. Coupling — who reaches into whom

#### 2.1 The fan-in ranking (a proxy for "what can never be sold alone")
| Module | Distinct peers depending on it | Reading |
|---|---:|---|
| `platform` | **45** | The kernel. Correct and expected — this *is* the shared core. |
| `iam` | 9 | Identity is a kernel concern behaving like a module. |
| `risk` | 8 | Risk is the platform's semantic centre — everything scores against it. |
| `reporting` | 8 | Reporting is consumed *and* consumes (see 2.2). |
| `evidence` | 7 | Evidence is a kernel concern behaving like a module. |
| `capa` | 6 | Corrective action is cross-cutting by design. |
| `incident`, `policy`, `admin`, `tprm` | 5 each | — |

#### 2.2 The four structural coupling defects
| Module | LoC | Reaches into | Why it is a defect |
|---|---:|---|---|
| `reporting` | 5,107 | `risk`×22, `platform`×15, `compliance`×13, `change_mgmt`×12, `ethics`×4, `policy`×4, `evidence`×3, `sust`×1 — **8 modules, 74 edges, only 2 entities** | A god-consumer that imports peers' **entities and services** directly. Any module sold without `reporting` loses its board pack; `reporting` sold alone compiles against 8 modules. |
| `audit_committee` | 1,581 | `compliance`×6, `sust`×4, `tprm`×4, `ai`×3, `capa`×2, `ccm`×2, `governance`×2, `incident`×2, `platform`×2, `risk`×2 — **10 modules** | Highest fan-out-per-LoC on the platform: 1.6 kLoC that cannot compile without ten peers. |
| `governance` | 1,857 | `platform`×10, `audit_lc`×4, `capa`×4, `ccm`×4, `risk`×4, `iam`×3, `policy`×2, `privacy_lc`×1 — **8 modules** | Same shape, and it stores into the **`platform` schema** (§3). |
| `socops` | 6,787 | `platform`×65, `incident`×3, `reporting`×3, `cyber`×2 | Kernel-heavy is fine; the `reporting` edge inverts the dependency direction (a producer importing a consumer). |

---

### 3. Nine modules persist into a schema they do not own

`@Table(schema = …)` measured across all `@Entity` classes:

| Module | Writes into schema | Entities | Verdict |
|---|---|---:|---|
| `incident` | **`cyber`** | 2 | ❌ **Blocks separability.** Incident management cannot be deployed, migrated, backed up or sold without the `cyber` schema. |
| `integrations` | **`platform`** | 6 | ❌ Six entities in the kernel schema — the kernel now carries integration state. |
| `controlmap` | **`evidence`** | 1 | ❌ Control mapping owns no data of its own. |
| `workflow_submission` | **`workflow`** | 1 | ⚠ Arguably one bounded context split across two packages — merge or rename. |
| `knowledge` | **`kb`** | 1 | ⚠ Naming only; harmless but undocumented. |
| `governance` | `platform` | 1 | ⚠ Kernel schema used as a dumping ground. |
| `signature` | `signature` + `platform` | 2 | ⚠ Straddles two schemas. |
| `auth` | `platform` | 1 | ✅ Acceptable — auth *is* kernel. |
| `attachment` | `platform` | 1 | ✅ Acceptable — attachments *are* kernel. |

**Rule this violates:** a module that cannot be schema-migrated independently cannot be licensed,
exported, or air-gapped independently. This is the hard floor under every per-module SKU claim.

---

### 4. Authorisation coverage — guarded, but not *delegatably* guarded

- **27** distinct permission modules are referenced by `hasPermission(MODULE, ACTION)` guards.
- **21 of 58** module packages contain **no `hasPermission` call at all**. They are *not* unguarded —
  verified by reading them, they use hard-coded `hasAnyRole('ROLE_A','ROLE_B',…)` lists, e.g.:

  | Module | Guard as written |
  |---|---|
  | `sharia` | `hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','SHARIA_OFFICER','SHARIA_BOARD_CHAIR','CRO')` |
  | `socops` | `hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')` (58 writes) |
  | `outsourcing` | `hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','CIO')` |
  | `audit_committee` | `hasAnyRole('AUDIT_COMMITTEE_CHAIR','COMPANY_SECRETARY','PLATFORM_ADMIN')` |
  | `knowledge` | `hasRole('PLATFORM_ADMIN')` and `isAuthenticated()` |

**Why this is a commercial defect, not a style preference.** The platform ships a per-tenant RBAC
system (55-role persona matrix, `V272`) whose entire value proposition is that a customer can define
their own roles and grant permissions to them. For these 21 packages that system **has no effect**:
a tenant who creates a role "Sharia Reviewer" and grants it `SHARIA_WRITE` still gets **403**,
because the code compares role *names*. The only way in is to hand the user a built-in platform role.
Sold as a standalone module, such a module is un-administrable by its buyer.

**This does not contradict the authorisation ratchet (currently 21).** The ratchet counts endpoints
with *no* guard of either kind; `hasAnyRole` satisfies it. The ratchet measures **coverage**;
this section measures **tightness**. Both are needed and only the first is currently enforced.

### 4.1 Entitlement / licensing — the subsystem that exists and is 8.6% wired

`module/platform/licensing/` is a complete commercial entitlement subsystem:

| Component | Detail |
|---|---|
| Table | `platform.organisation_licenses` — `organisation_id`, `module_code`, `tier` (FREE│PRO│ENTERPRISE), `seat_count`, `valid_from`, `valid_to`, `enabled`, `notes` |
| Service | `LicensingService` — Caffeine-cached (`@Cacheable("licenses")`), validity-window aware, tier validation, **20** canonical `KNOWN_MODULES` |
| Guard | `@RequireModule("CODE")` (class or method) + `LicenseGuardAspect` — AOP `@Around`, method-level wins over class-level (Spring `@Transactional` semantics), **fail-closed when no tenant is in scope**, throws `ModuleDisabledException` → 403 |
| Token | `JwtTokenProvider` issues an `enabled_modules` claim at login |
| API / UI | `LicensingController`; FE client `lib/licensing.ts`; admin screen at `/admin/licensing` |

**Adoption, measured:**

| Measure | Value |
|---|---:|
| Controllers carrying `@RequireModule` | **22 of 255 (8.6%)** — *all* in `socops` |
| `KNOWN_MODULES` codes vs module packages | **20 vs 58** |
| FE callers of the `moduleEnabled()` gate helper | **0** |
| Components consuming the `enabled_modules` JWT claim | **0** |
| `organisation_licenses` rows in dev | **22** — org 1: 20 modules enabled; **org 2: `SOCOPS` only; org 3: `SOCOPS` only** |

**The dev data demonstrates the gap.** Orgs 2 and 3 are licensed for exactly one module each and can use
all 58, because no controller outside `socops` is guarded. The licence table records intent the runtime
does not honour. (Finding F2-04 — reframed from "no mechanism exists" to "the mechanism exists and is
unadopted", which makes it days of work rather than weeks.)

---

### 5. Frontend reality

| Metric | Measured | Reading |
|---|---:|---|
| Pages | **329** in **114** folders | More screens than ServiceNow GRC exposes in its entire IRM suite |
| Routes in `App.tsx` | **328** (926-line file) | One flat route table |
| Sidebar entries | **184** | **~144 routed pages are not in the navigation** — reachable only by URL |
| Largest page folders | `bcm` 30, `socops` 25, `detail` 10, `workflow` 10, `cyber` 9, `risk` 9 | `bcm` alone has more screens than most competitors' whole BCM product |
| Mock imports | **44** statements, **29** files | See concentration below |
| Mock files shipped | **15** files, **3,650 lines** | Still in the production bundle |

#### 5.1 Mock-data concentration — the three that matter
| File | Mock imports | What the user sees |
|---|---:|---|
| `lib/searchIndex.ts` | **7** | **Global search** returns fabricated records across compliance, risk, evidence, TPRM, privacy |
| `pages/dashboard/widgets.tsx` | **4** | **Executive dashboard** widgets render invented figures |
| `lib/boardPack.ts` | **3** | **Board pack** — the artefact that goes to a real board — is assembled from mock data |

Ranked by blast radius, not count: a mock inside one obscure page is cosmetic; a mock inside global
search, the executive dashboard and the board pack means **the three surfaces an executive buyer will
open first are the three that lie.** (Finding F2-02.)

The remaining 26 files are per-page fallbacks: `mockCompliance` (629 lines) imported 9×,
`mockRisks` (418) 4×, `mockWorkflows` (373) 4×, `mockTprm` (226) 6×, `mockEvidence` (116) 5×.

#### 5.2 Bilingual machinery — stronger than the documentation claims

Two things are materially **better** than the vendor foundation pack asserts, and this Bible corrects
the record in the platform's favour:

| Asset | Measured | Verdict |
|---|---|---|
| i18n layer | `lib/i18n.tsx` — React context, `t(key, fallback)`, **per-locale lazy `import()`** so only the active dictionary enters the bundle | ✅ Real, and deliberately performance-tuned (`PERF-FE-002`) |
| Dictionary parity | `translations/en.ts` **5,677 keys** (6,199 lines, 333 KB) · `translations/ar.ts` **5,677 keys** (6,179 lines, 428 KB) | ✅ **Exact key parity** — Arabic is not a stub |
| Arabic presence in pages | **280 of 329 pages (85%)** contain Arabic characters | ✅ Broad |
| RTL handling | `<html dir>` driven; deliberate `dir="ltr"` islands for LTR tokens (hex colours, numerals+units) in `ColorField`, `RangeField`; `[dir="rtl"]` CSS in `dataTable.css`; chart axis reversal noted in `useChartTheme.ts` | ✅ Evidence of genuine RTL craft, not a stylesheet flip |

**But the mechanism is split in two, and the minority path is the disciplined one:**

| Mechanism | Occurrences | Files | Assessment |
|---|---:|---:|---|
| `t('key')` via the catalogue | 6,458 | **163** | The correct path |
| Inline `lang === 'ar' ? … : …` | 4,888 | **431** | Hand-rolled bilingualism, bypasses the catalogue |
| `useTranslation`/`useI18n` hook | 584 | 412 | Hook is taken widely… |
| `*_ar` / `*Ar` data-field reads | 16,768 | 563 | Bilingual *data* (correct — matches the backend rule) |
| **Hardcoded Latin JSX text** | **271** | — | In neither mechanism — untranslatable |
| **Hardcoded `label`/`title`/`placeholder`** | **168** | — | In neither mechanism — untranslatable |
| Pages with **zero** Arabic characters | **49 of 329 (15%)** | — | English-only screens in an Arabic-primary product |

So: 431 files branch on language by hand while only 163 use the catalogue, and **439 user-visible
strings are hardcoded English** that no translator or QA process can reach. (Finding F2-05.)

#### 5.3 Design system — a real asset

| Measure | Value |
|---|---|
| Component files | **144** (`ui/` 56, `viz/` 14, `layout/` 11, `workflows/` 11, `workflow/` 8, `compliance/` 7, `charts/` 6, + domain folders) |
| Pages importing **no** shared component | **2 of 329 (1%)** |
| Adoption of the core primitives | `Badge` 314× · `Shell` 296× · `Card` 293× · `PageHeader` 273× · `Button` 244× · `Kpi` 170× · `InlineEmpty` 133× · `EmptyState` 109× · `DataTable` 73× · `Tabs` 57× · `FormModal` 55× |
| Design tokens | `styles/tokens.css` 15 KB; `styles/components.css` 87 KB; 29 CSS files, 328 KB total |

**99% shared-component adoption is a strong result** — stronger than most enterprise React codebases
of this size, and it means a visual redesign is a *token-and-component* exercise, not a rewrite.
Any assessment recommending a "complete UX redesign" must reconcile with this number; this Bible does
not make that recommendation (see `UX/UX_REDESIGN_BLUEPRINT.md`).

#### 5.4 Page-size distribution — where the FE debt actually is

| Measure | Value |
|---|---|
| Total page LoC | **136,614** across 329 pages |
| Mean / median page | **415 / 312** lines |
| Pages > 500 lines | **72** · > 1,000 lines: **11** · > 1,500 lines: **2** |
| The two monoliths | `pages/esg/EsgPage.tsx` **5,583 lines** · `pages/ndi/NdiPage.tsx` **4,970 lines** |
| Next largest | `EthicsPage` 1,317 · `CompliancePage` 1,283 · `CyberPage` 1,272 · `ScmPage` 1,246 · `CommitteesPage` 1,217 · `PolicyManagementPage` 1,167 |

Two single files carry **10,553 lines** — 7.7% of all page code in 0.6% of the files. `NdiPage.tsx`
(4,970 lines) fronts the `ndi` module that has **127 endpoints, 82 writes and 11 tests**. That
combination — the platform's largest screen over its least-tested large module — is the single
highest-risk square metre of the codebase. (Finding F2-06.)

---

### 6. Test coverage by module — and where the gap sits

**19 packages / 20,503 LoC / 11.0% of module code have zero tests.** The distribution is not random:

| Module | LoC | Tests | Saudi / regulatory relevance |
|---|---:|---:|---|
| `sharia` | 2,234 | **0** | Sharia governance — Islamic-finance differentiator, no competitor equivalent |
| `openbanking` | 1,913 | **0** | SAMA Open Banking framework |
| `rrp` | 1,910 | **0** | Recovery & resolution planning (SAMA) |
| `cpty` | 1,688 | **0** | Counterparty / credit concentration |
| `outsourcing` | 1,655 | **0** | **SAMA outsourcing regulation — a headline selling point** |
| `regreporting` | 1,628 | **0** | Regulatory reporting |
| `fraud` | 1,301 | **0** | Fraud risk |
| `reginspect` | 1,160 | **0** | Regulator inspection management |
| `dataclass` | 988 | **0** | Data classification (NDMO/SDAIA) |
| `opres` | 950 | **0** | Operational resilience |
| `data_officer` | 2,373 | **0** | PDPL data-officer workspace, 37 endpoints |
| `nca_ecc` | 368 | **0** | **NCA ECC** — the single most-cited Saudi control framework |
| `board_pack`, `knowledge`, `audit_eng`, `controlmap`, `attachment`, `dashboard`, `datagov` | 2,335 | **0** | supporting |

Thin (below ~1 test per 60 LoC): `ndi` 11 tests / 7,717 LoC (**127 endpoints, 82 writes**),
`scm` 15 / 3,808, `evidence` 21 / 3,683, `reporting` 26 / 5,107, `socops` 36 / 6,787.

**The pattern is the finding.** The modules with zero tests are disproportionately the
**Saudi-specific, regulator-facing, competitively differentiating** ones. The heavily-tested modules
(`bcm` 395, `sust` 200, `platform` 169, `risk` 147) are the generic ones every competitor also has.
The platform is best-assured exactly where it is least differentiated. (Finding F2-03.)

---

### 7. Database and migration surface

- **50** distinct JPA schemas; **304** migration files, `V1`…`V314`, **3.83 MB** of DDL.
- Sequence gaps at `V82–V90`, `V168`, `V169`, `V174` — expected (withdrawn during development),
  harmless to Flyway, but they mean "highest number" ≠ "count".
- Dev is **Python-bootstrapped with Flyway OFF**; prod runs Flyway from baseline **V312**.
  `V313`/`V314` are applied to dev by hand and await the next prod jar.

**Risk this creates:** dev and prod schemas are produced by *two different mechanisms*. Nothing in CI
proves they converge. A migration that is correct under Flyway but was hand-applied differently in dev
(or vice versa) is invisible until production. (Finding F2-04.)

---

### 7.1 Prior-audit evidence vault — `audit/` (tracked, 98 files)

**Correction to this review.** An earlier draft of `Registers/FINDINGS_REGISTER_V2.md` asserted that no
performance, accessibility, secret-scanning or coverage evidence existed. **That was wrong.** The
repository contains a tracked audit evidence vault from the **2026-06-18** audit at commit `3ca91bf`,
produced with 12 named tools. It is the third time in this review that "not found" turned out not to
mean "does not exist" — the same error recorded against `ADR-002` in `Governance/BIBLE_VALIDATION_LOG.md`.

| Artefact | Location | Content |
|---|---|---|
| Measured metrics | `audit/metrics.json` | LoC, backend counts, coverage, quality, deps, secrets, load test, accessibility |
| Load test | `audit/artifacts/k6/load.js`, `audit/raw/k6-summary.json` | 4 scenarios, full metrics |
| Accessibility | `audit/raw/lighthouse-login.report.html`, `audit/findings/dyn-a11y.json` | Lighthouse + axe-core |
| Security scans | `audit/raw/gitleaks.json`, `bandit.json`, trivy, `npm audit` | secrets, Python SAST, CVEs |
| Quality | `audit/raw/jscpd/` | duplication |
| Findings | `audit/all_findings.json`, `crit_high.json`, `verdicts.json`, `workstreams.json` | the 287 findings |
| E2E | `audit/artifacts/e2e/`, `audit/findings/dyn-*.json` | Playwright functional/UX/a11y |

**Tools used (from `metrics.json`):** trivy 0.71.1 · gitleaks 8.30.1 · npm audit · bandit 1.9.4 · jscpd ·
tsc 5.6 · JaCoCo 0.8.12 · Maven Surefire · Playwright 1.49 + `@axe-core/playwright` · Lighthouse ·
k6 2.0 · 35+ manual review subagents.

#### 7.2 Performance — measured, and good within its scope

| Metric | Value |
|---|---:|
| Endpoint tested | **`GET /api/v1/risk/risks`** (authenticated, admin) — **1 of 2,160** |
| Scenarios | baseline 20 VU/35 s · spike →100 VU · soak 15 VU/45 s · breakpoint arrival-rate →350/s |
| Total requests | **18,763** · 110.1 req/s |
| Latency | p50 **8.6 ms** · p95 **26.9 ms** · max 194.2 ms |
| Errors | **0.0%** · checks passed **100%** (18,762/18,762) |
| Ceiling | **Not reached.** Hikari pool (dev max 10) never exhausted |

The audit's own note is candid and worth quoting in spirit: no failure threshold was reached up to
~350 req/s on this fast read path, so the true ceiling is higher and would need a heavier/write endpoint
or a larger dataset to find.

**So the finding is coverage, not absence:** one read endpoint, at dev data volumes, before the
remediation programmes. No write path, export, dashboard or search was load-tested, and there is still
no capacity model or p95 target per endpoint. (Finding F2-17, reframed.)

#### 7.3 Accessibility — measured, with one serious open violation

| Metric | Value |
|---|---:|
| Lighthouse `/login` — **accessibility** | **100** |
| Lighthouse `/login` — performance · best-practices · SEO | **38** · 77 · 82 |
| axe-core violations on `/login` | **0** |
| Pages scanned by axe | **10** |
| Authenticated-page violation | **`color-contrast` (WCAG 1.4.3, serious)** — **68 instances** |

⚠ The Lighthouse performance score of 38 was taken against the **Vite dev server** (`localhost:5173`),
which is not representative of a production build — treat it as unmeasured rather than bad.
The **68 colour-contrast instances are real and actionable**, and they sit against a design system with
central tokens (§5.3), so the fix is likely a small number of token changes rather than 68 edits.

#### 7.4 Coverage, quality and dependencies (2026-06-18 vintage)

| Metric | Value | Note |
|---|---:|---|
| Backend tests then | 1,858 | **now 2,415** |
| **Instruction coverage** | **37.1%** | JaCoCo, 1,324 classes |
| **Branch coverage** | **26.0%** | |
| **Line coverage** | **38.5%** | |
| Frontend tests then | **0** (`runner: none`) | now 49 via vitest |
| Code duplication | **5.41%** — 1,666 clones, 21,062 lines | jscpd over 389,021 lines |
| **ESLint configured** | **`false`** | ⚠ a precondition for F2-11's lint ratchets — they cannot be added until ESLint exists |
| trivy dependency vulns | 36 | not triaged by severity in `metrics.json` |
| `npm audit` | 5 (1 high, 3 moderate, 1 low) | |
| gitleaks hits | **21 → 0 real** | see below |
| Module packages then | **62** | vs **58** now — consolidation since |

**The 21 gitleaks hits are all false positives**, verified match by match in this review:
placeholder tokens in integration docs (`SPLUNK_HEC_TOKEN="00112233-4455-6677-8899-aabbccddeeff"`,
`TENABLE_ACCESS_KEY="abc123def456..."`), `localStorage` key constants
(`KEY = 'grc.mfa.enrolment.v1'`, `'grc.bcm.invocation.v1'`, …), form-field keys
(`'c5BreachNotification'`), a seed client identifier (`fapi_client="lean-prod-aspsp1-2024"`) and a demo
vendor-portal access code (`'NEOM-2026-A4Z9'`). **No secret leakage was found.**

Process point: `metrics.json` publishes the **untriaged** count of 21, and "21 secret hits" is exactly
the kind of number that alarms a reader who does not open the file. Scan output should carry a triage
verdict beside the raw count.

**Overall, this vault materially strengthens the platform's evidence position** — and it was invisible to
this review until the documentation register was compiled, which is itself the argument for D-5
(no document register) in `Appendix/DOCUMENTATION_REGISTER_AND_AUDIT_SUPPORT.md`.

---

### 8. What this baseline does **not** measure

Stated so no reader over-reads it:

- **Runtime performance beyond one endpoint** — a k6 load test exists (§7.2) covering `GET /api/v1/risk/risks` only. No profiling, no write-path or export load test, no capacity model.
- **Test *quality*** — 2,415 passing tests is a count, not an assertion-strength measure. Spot mutation
  checks were done on the committee engine and `ModulePermissionEvaluator` only. JaCoCo coverage from
  2026-06-18 was **37.1% instruction / 26.0% branch / 38.5% line** (§7.4); it has not been re-measured
  since the suite grew from 1,858 to 2,415 tests. **F2-21 is the proof that coverage is not provenance:**
  the committee engine's 38 tests all pass and none of them notices that the voter's identity is
  attacker-supplied.
- **Functional correctness of the 19 zero-test modules** — they may work perfectly; the point is that
  **nothing proves it**, and nothing would catch a regression.
- **Actual tenant data volumes** — dev carries 3 seeded Saudi tenants; there is no production data
  profile in scope.
- **Accessibility beyond the 2026-06-18 sweep** — axe-core + Lighthouse were run over 10 pages (§7.3): login clean, 68 serious colour-contrast instances open. Focus management, keyboard reachability and screen-reader labelling remain unmeasured.
- **Penetration testing** — none. Automated scanning exists (gitleaks, trivy, bandit, npm audit — §7.4) but scanning is not a pentest; the security findings here are design- and code-level.

---

### 9. Information I would request from the business

The brief invites this. These change conclusions, so they are asked rather than assumed:

| # | Question | Which conclusion it changes |
|---|---|---|
| 1 | Are there **paying production tenants today**, and how many users each? | Whether findings are "pre-launch debt" or "live customer risk" — changes every severity |
| 2 | Is the commercial intent **per-module SKUs** or one suite licence? | Whether §3/§4 are blockers or merely hygiene |
| 3 | Which **3–5 modules** are in the next 2 sales cycles? | Where the test bar and UX spend must land first |
| 4 | Deployment model per customer: **on-prem, private cloud, or shared SaaS**? | Multi-tenancy hardening priority; air-gap/export requirements |
| 5 | Is there a **signed NCA/SAMA/DGA compliance obligation** for the platform itself (not the customer)? | Whether §7 (dev/prod schema divergence) and audit-trail gaps are contractual breaches |
| 6 | Target **price point and competitor** in live deals? | Which competitor comparison in the findings register is the relevant one |
| 7 | Team size and composition available for remediation? | Whether the roadmap is 2 quarters or 6 |
| 8 | Is Arabic the **contractual** UI language of record, or a nice-to-have? | Severity of every bilingual gap |

---

### 10. Reproduction

```bash
py Appendix/scripts/coupling.py    # module census, coupling graph, FE mock scan -> coupling.json
py Appendix/scripts/verify.py      # datagov, foreign schemas, mock reconciliation, page/route/nav counts
py Appendix/scripts/verify2.py     # per-module guard style, permission census, migrations, Saudi coverage
```

Counting rules, stated so results are auditable:
- **LoC** = raw line count of `.java` under the module package (comments and blanks included).
- **Endpoints** = `@Get|Post|Put|Patch|Delete|RequestMapping` occurrences, minus one class-level
  `@RequestMapping("…")` per controller. **Writes** = `@Post|Put|Patch|Delete` only.
- **Tests** = `@Test|@ParameterizedTest|@RepeatedTest` in files under `src/test/.../module/<name>/`,
  else attributed to the module the test file imports most. Suite total (2,415) exceeds the module
  sum (2,339) because kernel/config tests belong to no module.
- **Mock imports** = one hit per `import|export … from '…mock…'` **statement** (not per symbol) —
  this is the correction to the previously-published 53.

---

## Part 4: Module Separability & SKU Model

*Source: [`Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md`](TSS-GRC-Platform-Bible/Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md)*

**Purpose.** Answer one question with measured evidence: *can each module be sold, deployed and
administered separately — and if not, exactly what stands in the way?*

**Date:** 2026-07-25 · **Evidence:** `Appendix/MEASURED_BASELINE.md` · **Supersedes on measured facts:**
`Architecture/MODULE_INDEPENDENCE_AND_PACKAGING.md`, `Heatmaps/MODULE_INDEPENDENCE_MATRIX.md`.

---

### 1. Verdict up front

**The architecture is far more separable than the platform's own documents assume, and the blockers are
not where anyone expected them.**

| | |
|---|---|
| **Sellable standalone today** | **10 modules · 15,975 LoC** — kernel-only dependencies, tests present |
| **Sellable after *non-architectural* work** | **21 modules · 52,713 LoC** — dependencies already clean; missing **tests** and **permission catalogue**, not decoupling |
| **Genuinely coupled** | **13 modules · 78,311 LoC** — includes the four largest revenue modules |
| **Base platform (never a standalone SKU)** | **13 modules · 39,268 LoC** |

**The single most important finding in this document:** the **Saudi-differentiating modules are
architecturally the cleanest on the platform.** `sharia`, `openbanking`, `outsourcing`, `rrp`, `cpty`,
`regreporting`, `reginspect`, `fraud`, `opres`, `dataclass`, `nca_ecc` all have **zero peer-module
dependencies** — they need only the kernel. What blocks selling them is **zero tests** (F2-03) and
**role-name guards instead of catalogued permissions** (F2-05). Both are additive work with no
refactoring risk.

> **Strategic consequence.** The fastest route to a modular product line is *not* to decouple the big
> modules. It is to add tests and permission catalogues to eleven already-decoupled Saudi modules —
> turning the platform's differentiators into its first sellable SKUs, in the order of a quarter's work,
> with no architectural risk at all.

And one cheap architectural win compounds it: **promoting the export toolkit out of `reporting` into the
kernel (≈1 day) improves the separability of eight further modules, including `bcm` and `sust`** — see §5.

---

### 2. Method

Separability is scored on four measured axes, not opinion:

| Axis | Measure | Why it decides sellability |
|---|---|---|
| **Code coupling** | Distinct **non-kernel** peer modules imported | A module that imports a peer cannot compile, deploy or version without it |
| **Data ownership** | Does every `@Entity` write to the module's own schema? | Foreign-schema entities make independent migration, backup, export and residency impossible |
| **Administrability** | Are permissions catalogued (`hasPermission`) or hard-coded roles (`hasAnyRole`)? | A buyer who cannot define their own roles cannot administer what they bought |
| **Assurance** | Tests present, proportionate to write surface | Nothing may be sold standalone on a promise; a standalone SKU carries its own warranty |

**Kernel** (the base platform every SKU includes): `platform`, `iam`, `auth`, `admin`, `evidence`,
`attachment`, `signature`, `workflow`, `approvals`, `capa`, `intake`, `dashboard`, `integrations`.
Depending on the kernel is correct and is not counted against a module.

**Tiers.** **A** — sellable now. **B** — clean dependencies, needs tests and/or permissions.
**C** — genuine coupling or foreign schema; needs architectural work. **K** — kernel.

---

### 3. The separability matrix

#### Tier A — sellable standalone today (10 modules)

| Module | LoC | Endpoints | Tests | Fan-in | Auth style | Note |
|---|---:|---:|---:|---:|---|---|
| `ethics` | 4,841 | 72 | 83 | 1 | `COMPLIANCE` | Whistleblowing / conduct — a **complete standalone product**; OneTrust sells this as a separate SKU |
| `audit_lc` | 3,177 | 50 | 58 | 2 | `AUDIT` | Internal-audit lifecycle — AuditBoard's core product category |
| `aml` | 2,165 | 14 | 47 | 0 | ⚠ ROLE | AML/sanctions — zero fan-in, fully isolated |
| `ccm` | 2,031 | 10 | 39 | 2 | ⚠ ROLE | Continuous control monitoring |
| `consumer` | 1,420 | 11 | 20 | 0 | ⚠ ROLE | Consumer-protection (SAMA) |
| `risk_lc` | 584 | 5 | 6 | 1 | `RISK` | Lifecycle add-on to Risk |
| `cyber_lc` | 506 | 5 | 11 | 0 | `CYBER` | Lifecycle add-on to Cyber |
| `regchange` | 444 | 5 | 6 | 0 | `COMPLIANCE` | Regulatory-change tracking |
| `privacy_lc` | 416 | 5 | 10 | 2 | `PRIVACY` | Lifecycle add-on to Privacy |
| `bcm_lc` | 391 | 5 | 6 | 0 | `BCM` | Lifecycle add-on to BCM |

Caveat: the five `*_lc` modules are **lifecycle add-ons**, commercially part of their parent SKU rather
than products. The genuinely standalone Tier-A products are **`ethics`, `audit_lc`, `aml`, `ccm`,
`consumer`** — and three of the five still need the permission conversion (F2-05) before a customer can
administer them.

#### Tier B — clean dependencies; blocked on tests and/or permissions (21 modules)

**Group B1 — Saudi/financial-sector modules with ZERO peer dependencies and ZERO tests.**
*This group is the commercial opportunity.*

| Module | LoC | Endpoints | Tests | Auth | Blocker | Regulatory anchor |
|---|---:|---:|---:|---|---|---|
| `data_officer` | 2,373 | 37 | **0** | `PRIVACY` | tests | PDPL data-officer workspace |
| `sharia` | 2,234 | 21 | **0** | ⚠ ROLE | tests + perms | Sharia governance — **no competitor equivalent** |
| `openbanking` | 1,913 | 18 | **0** | ⚠ ROLE | tests + perms | SAMA Open Banking |
| `rrp` | 1,910 | 17 | **0** | ⚠ ROLE | tests + perms | SAMA recovery & resolution |
| `cpty` | 1,688 | 14 | **0** | ⚠ ROLE | tests + perms | Counterparty / credit concentration |
| `outsourcing` | 1,655 | 17 | **0** | ⚠ ROLE | tests + perms | **SAMA outsourcing** |
| `regreporting` | 1,628 | 17 | **0** | ⚠ ROLE | tests + perms | Regulatory reporting |
| `fraud` | 1,301 | 10 | **0** | ⚠ ROLE | tests + perms | Fraud risk |
| `reginspect` | 1,160 | 10 | **0** | ⚠ ROLE | tests + perms | Regulator inspections |
| `dataclass` | 988 | 13 | **0** | ⚠ ROLE | tests + perms | NDMO/SDAIA data classification |
| `opres` | 950 | 9 | **0** | ⚠ ROLE | tests + perms | Operational resilience |
| `nca_ecc` | 368 | 6 | **0** | `COMPLIANCE`,`CYBER` | tests | **NCA ECC** |
| `audit_eng` | 354 | 5 | **0** | `AUDIT` | tests | Audit engagements |
| `board_pack` | 353 | 5 | **0** | ⚠ ROLE | tests + perms | Board reporting |

**Total: 18,875 LoC · 199 endpoints · 0 tests.** No decoupling required for any of them.

**Group B2 — larger modules with 1–2 peer dependencies.**

| Module | LoC | Endpoints | Tests | Peer deps | Assessment |
|---|---:|---:|---:|---|---|
| `risk` | 8,955 | 101 | 147 | `policy`, `risk_lc` | Well tested; the `policy` edge is the only real question |
| `ndi` | 7,717 | 127 | **11** | `reporting`*, `risk` | *`reporting` edge is export-toolkit only (§5) → effectively 1 peer. **Assurance is the blocker** (F2-12) |
| `privacy` | 5,804 | 99 | 79 | `tprm` | PDPL suite; thin coverage for 62 writes |
| `scm` | 3,808 | 66 | **15** | `tprm` | SAMA supply-chain; 45 writes, 15 tests |
| `ai` | 3,758 | 35 | 48 | `reporting`* | *Export-toolkit only → **becomes Tier A** after §5 |
| `change_mgmt` | 2,022 | 30 | 36 | `incident` | Extraction already planned (CE-1) |
| `policy` | 1,774 | 27 | 39 | `compliance` | Fan-in 5 — widely depended upon |

#### Tier C — genuine coupling or foreign schema (13 modules)

| Module | LoC | Peer deps | Real blocker | Fix |
|---|---:|---|---|---|
| `bcm` | 25,130 | `reporting`*, `tprm`, `cyber` | *Toolkit-only for `reporting` → 2 real peers | §5 then two ports |
| `sust` | 16,704 | `reporting`*, `risk`, `tprm` | Same → 2 real peers | §5 then two ports |
| `cyber` | 7,269 | `reporting`*, `policy`, `incident` | `incident` shares its schema | §5 + F2-06 decision |
| `socops` | 6,787 | `incident`, `reporting`*, `cyber` | *Cycle is toolkit-only | §5 removes the cycle |
| `compliance` | 5,659 | `policy`, `risk`, `privacy` | Genuine domain coupling | Ports |
| `tprm` | 5,399 | `scm`, `privacy`, `bcm` | Genuine; fan-in 5 | Ports |
| `reporting` | 5,107 | **6 peers, 74 edges** | God-consumer of peer internals | Port inversion (F2-08b) |
| `governance` | 1,857 | 5 peers | Aggregator + writes to `platform` schema | Ports + schema |
| `audit_committee` | 1,581 | **8 peers** | Highest fan-out per LoC | Ports |
| `incident` | 1,585 | — | **Writes into `cyber` schema** | F2-06 decision: separate SKU or merge into `cyber` |
| `workflow_submission` | 494 | — | Writes into `workflow` schema | Merge into `workflow` |
| `controlmap` | 384 | — | Writes into `evidence` schema | Merge into `evidence` or `compliance` |
| `knowledge` | 355 | — | Writes into `kb` schema | Rename schema |

#### Tier K — base platform (13 modules · 39,268 LoC)

`platform` (fan-in **45** — correctly the kernel), `integrations`, `workflow`, `admin`, `iam` (fan-in 9),
`evidence` (fan-in 7), `auth`, `signature`, `intake`, `attachment`, `approvals`, `capa` (fan-in 6),
`dashboard`. **Every SKU includes this.** It is the licensable base, and it is where entitlement,
identity, evidence, audit ledger, workflow and export belong.

⚠ Three kernel modules need attention as kernel: `integrations` (7,568 LoC, **ROLE** guards, 6 entities
in the `platform` schema), `attachment` (0 tests), `dashboard` (0 tests, 0 entities).

---

### 4. What actually blocks per-module sale — ranked by modules unblocked per unit of effort

| Rank | Blocker | Modules affected | Effort | Architectural risk |
|---:|---|---:|---|---|
| **1** | **Entitlement subsystem exists but is wired to 8.6% of controllers** (F2-04) | **All 58** | **S–M (days)** | **Very low — the mechanism is built, proven on `socops`, and fail-closed** |
| **2** | **Zero tests** (F2-03) | 19 (14 in Tier B1) | L, parallelisable | **None** — purely additive |
| **3** | **Role-name guards** (F2-05) | 21 | S per module | Low — behaviour-preserving refactor |
| **4** | **Export toolkit in `reporting`** (F2-08a) | **8** | **S (~1 day)** | **Very low** — package move |
| **5** | Foreign schemas (F2-06) | 4 material | M per module | Medium — schema migration |
| **6** | God-consumer coupling (F2-08b) | 6 | L | Medium |

**Read this table as the plan.** Ranks 1–4 unblock the entire Tier A + Tier B set — **31 modules,
68,688 LoC — with essentially no architectural risk.** Ranks 5–6 are needed only for the specific Tier-C
modules a customer actually wants standalone.

---

### 5. The one-day win: promote the export toolkit

Eight modules (`bcm`, `sust`, `iam`, `socops`, `ndi`, `cyber`, `ai`, `admin`) import `reporting`.
Every one of the 25 inbound edges imports **only** export utilities — `PdfTheme`, `PdfFonts`,
`XlsxStyler`, `ExportNaming`, `RegisterExport`, `TabularExportRequest`, `TabularExportService`.
None imports reporting *logic*.

```
module/reporting/service/{PdfTheme,PdfFonts,XlsxStyler,ExportNaming,RegisterExport,TabularExport*}
        →  module/platform/export/
```

| Effect | Value |
|---|---|
| Inbound edges to `reporting` removed | **25 → 0** |
| Apparent `socops → reporting` cycle | **eliminated** |
| `ai` | Tier B → **Tier A** (kernel-only) |
| `bcm`, `sust`, `cyber`, `socops` | 3 peer deps → **2** |
| `ndi` | 2 → **1** |
| Behaviour / schema change | **none** — pure package move, compiler-verified |

Bilingual RTL export quality (shaping, mirroring, the bundled Amiri font) is a genuine platform asset
used by 22 generators. It belongs in the kernel on merit, not only for graph hygiene.

---

### 6. Proposed SKU catalogue

Derived from the tiers, not invented. Every SKU includes the **Base Platform**.

| SKU | Modules | Status | Comparator |
|---|---|---|---|
| **Base Platform** *(mandatory)* | Tier K: identity, RBAC, entitlement, evidence, audit ledger, workflow, committees, exports, branding, admin | Ships today | ServiceNow Now Platform; Archer Platform |
| **Risk Management** | `risk`, `risk_lc`, `ccm` | Ready (tests strong) | Archer ERM; MetricStream ERM |
| **Compliance & Policy** | `compliance`, `policy`, `controlmap`, `regchange`, `nca_ecc` | Near-ready; `controlmap` schema, `nca_ecc` tests | MetricStream; OneTrust GRC |
| **Internal Audit** | `audit_lc`, `audit_eng`, `audit_committee` | `audit_lc` ready; `audit_committee` coupled | **AuditBoard's core** |
| **Cyber & SOC** | `cyber`, `cyber_lc`, `socops`, `incident` | Coupled set — decide `incident`/`cyber` (F2-06) | ServiceNow SecOps |
| **Resilience (BCM)** | `bcm`, `bcm_lc`, `opres` | `bcm` strong (395 tests); `opres` 0 tests | Fusion; Archer BC&IT |
| **Privacy (PDPL)** | `privacy`, `privacy_lc`, `data_officer`, `dataclass` | `privacy` ready; other three 0 tests | **OneTrust's core** |
| **Third-Party & Supply Chain** | `tprm`, `scm`, `outsourcing`, `cpty` | `tprm` ready; `outsourcing`/`cpty` 0 tests | OneTrust TPRM; Archer |
| **Sustainability & Green Finance** | `sust` | Strong (200 tests); 2 peer deps | No direct Arabic comparator |
| **Ethics & Conduct** | `ethics`, `consumer` | **Ready today** | OneTrust Ethics |
| **Financial-Crime** | `aml`, `fraud` | `aml` ready; `fraud` 0 tests | Specialist vendors |
| **Saudi Financial-Sector Regulatory** ⭐ | `openbanking`, `rrp`, `regreporting`, `reginspect`, `sharia` | **All 0 tests — and all architecturally clean** | **No comparator exists** |
| **AI Governance** | `ai` | Tier A after §5 | Emerging category |
| **Change Enablement** | `change_mgmt` | Extraction in progress (CE-1) | ServiceNow ITSM |

⭐ **The highest-margin, least-contested SKU is the one with zero tests.** That is the register's
central commercial message: the moat exists in code and is unproven in CI.

---

### 7. Target architecture for separability

Four rules, each enforced by a test rather than a document — because an unenforced rule is what produced
the current state:

| # | Rule | Enforcement | ADR |
|---|---|---|---|
| **R1** | A module may import the **kernel** freely and a **peer only through a port interface it owns**. No cycles. | Ratcheted boundary test over all 58 packages | **ADR-012** |
| **R2** | Every `@Entity` writes to its own module's schema; only kernel packages write to `platform`. | Test comparing `@Table(schema=…)` against the module registry | **ADR-011** |
| **R3** | Every write endpoint is guarded by `hasPermission(MODULE, ACTION)`. `hasAnyRole` is forbidden in `module/**`. | Tightness ratchet (extends the coverage ratchet at 21) | **ADR-010** |
| **R4** | A module exists only if it is in the **module registry** with an owner, SKU, maturity tier, schema, permission prefix and test floor. | CI check: registry ↔ filesystem must agree | **ADR-009** |

#### Reference shape for a separable module

```
module/<name>/
  api/          — DTOs + controller: the published contract
  domain/       — entities, state machine (no Spring, no JPA in the state machine)
  service/      — application logic
  repository/   — persistence, own schema only
  spi/          — port INTERFACES this module needs from peers (owned here)
  package-info.java — registry: owner, SKU, tier, schema, permission prefix
integration/<name>/ — ADAPTERS implementing another module's ports (outside both modules)
```

Adapters live **outside** both modules, so neither depends on the other — the dependency inverts onto
an interface the consumer owns. This is the shape the `change_mgmt` extraction (CE-1) is piloting; it
should become the platform-wide standard **only after** the pilot proves it, not before.

---

### 8. What this does *not* claim

- **Separable ≠ independently deployable.** Every tier here is about a *modular monolith*: one process,
  one database, clean internal boundaries. Independent *deployment* additionally requires the schema
  work (F2-06) and a service boundary — out of scope for this document and not currently justified.
- **Tier A does not mean production-ready.** It means *architecturally* separable with tests present.
  `aml`, `ccm` and `consumer` still carry role-name guards, so a buyer could not administer them.
- **The tiering is structural, not functional.** No claim is made about whether any module's *features*
  are complete or competitive; that is `Modules/PRODUCT_DESIGN_SPECIFICATION.md`'s job, and Baseline §8
  states what was not measured.

---

## Part 5: UX Redesign Blueprint

*Source: [`UX/UX_REDESIGN_BLUEPRINT.md`](TSS-GRC-Platform-Bible/UX/UX_REDESIGN_BLUEPRINT.md)*

**Date:** 2026-07-25 · **Evidence:** `Appendix/MEASURED_BASELINE.md` §5–§5.4 ·
**Complements** `UX/UX_ARCHITECTURE.md`, `UX/DESIGN_SYSTEM_SPECIFICATION.md`,
`UX/ARABIC_AND_SAUDI_UX_STANDARD.md`, `UX/PERSONA_JOURNEYS.md` — **corrects them where measurement disagrees.**

---

### 1. The finding that changes the brief

The review brief asks for "a complete UX redesign". **Measurement does not support that, and this
document declines to recommend it.**

| What was measured | Result | Implication |
|---|---|---|
| Pages importing shared components | **327 of 329 (99%)** | A design system exists **and is genuinely adopted** |
| Component library | **144 files** (`ui/` 56, `viz/` 14, `layout/` 11 …) | Primitives exist: `Badge` 314×, `Shell` 296×, `Card` 293×, `PageHeader` 273×, `Button` 244×, `Kpi` 170×, `DataTable` 73× |
| Design tokens | `styles/tokens.css` 15 KB, `components.css` 87 KB | Themeable centrally |
| Arabic dictionary parity | `en.ts` **5,677 keys** / `ar.ts` **5,677 keys** | Arabic is a **first-class locale**, not a stub |
| RTL engineering | `<html dir>`, deliberate `dir="ltr"` islands for hex/numeric tokens, `[dir="rtl"]` table CSS, chart-axis reversal | Evidence of real RTL craft, not a stylesheet flip |

**A redesign would throw away the platform's strongest frontend asset.** 99% component adoption means
visual change is a **token-and-primitive** exercise: restyle `Card`, `Badge`, `Button`, `PageHeader`,
`DataTable`, `Kpi` and 329 screens change together.

**The real UX crisis is structural, not visual:**

| Defect | Evidence | Severity |
|---|---|---|
| **Navigation cannot reach the product** | 328 routes vs **184** sidebar entries → **~144 screens URL-only** | **P1** |
| **Taxonomy has drifted** | **114** page folders for 58 modules; `pages/dashboard` *and* `pages/dashboards`; `pages/detail` holds 10 orphan screens | **P1** |
| **The first three surfaces lie** | Global search, executive dashboard, board pack are mock-fed | **P0** |
| **Bilingualism is split** | `t()` in 163 files vs inline `lang === 'ar'` in **431**; **439** hardcoded English strings; **49 pages (15%) contain no Arabic** | **P1** |
| **Two unreviewable monoliths** | `EsgPage.tsx` **5,583** lines, `NdiPage.tsx` **4,970** — 7.7% of page code | **P2** |

> **Redesign the information architecture. Keep the design system. Fix the truthfulness.**

---

### 2. Information architecture — the redesign

#### 2.1 Diagnosis

A 184-item sidebar is past the point of navigability, and it still exposes only **56%** of routed
screens. Competitors converged on 8–15 top-level surfaces: ServiceNow (workspaces + unified search),
Archer (solution areas), OneTrust (module-scoped IA), AuditBoard (a handful of named products). None
exposes a flat menu of this length — not because they have less capability, but because **menu length is
not how large products are navigated.**

#### 2.2 Target: 12 workspaces

Derived from the SKU catalogue (`Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md` §6), so navigation,
licensing and the datasheet share one taxonomy:

| # | Workspace | Modules behind it |
|---:|---|---|
| 1 | **My Work** | Cross-module tasks, approvals, attestations, my requests |
| 2 | **Risk** | `risk`, `risk_lc`, `ccm` |
| 3 | **Compliance & Policy** | `compliance`, `policy`, `controlmap`, `regchange`, `nca_ecc` |
| 4 | **Internal Audit** | `audit_lc`, `audit_eng`, `audit_committee` |
| 5 | **Cyber & SOC** | `cyber`, `cyber_lc`, `socops`, `incident` |
| 6 | **Resilience** | `bcm`, `bcm_lc`, `opres` |
| 7 | **Privacy** | `privacy`, `privacy_lc`, `data_officer`, `dataclass` |
| 8 | **Third Party & Supply Chain** | `tprm`, `scm`, `outsourcing`, `cpty` |
| 9 | **Sustainability** | `sust` |
| 10 | **Ethics & Conduct** | `ethics`, `consumer` |
| 11 | **Financial-Sector Regulatory** | `openbanking`, `rrp`, `regreporting`, `reginspect`, `sharia`, `aml`, `fraud` |
| 12 | **Governance & Administration** | committees, `governance`, `board_pack`, `admin`, `iam`, `ai`, `change_mgmt` |

**Rules.** Each workspace has one landing page (KPIs → registers → my items → what needs me).
Navigation is **generated** from the module registry filtered by entitlement (F2-01, F2-04), so an
unlicensed module cannot appear and a route with no registry entry cannot exist. Depth comes from
in-workspace tabs and detail routes, never from sidebar length. Sidebar shows only workspaces the user
has permission *and* entitlement for — for a three-module customer that is 3–4 items, not 184.

#### 2.3 Sequence — reconcile before redesigning

**Step 1 (2–3 days, do first).** Generate `UX/ROUTE_RECONCILIATION.md`: every one of the 328 routes ×
{sidebar entry? · module owner? · real API or mock? · last-touched}. Classify all ~144 URL-only screens
as **promote / merge / retire**. This is the highest information value per hour available in the UX
programme: it converts "we don't know what half our app is" into a decision list, and may retire dozens
of screens — shrinking the surface the redesign has to cover.

**Step 2.** Add the CI test: every route has a nav path or an explicit `detailOnly: true`. Prevents
recurrence permanently.

**Step 3.** Build the 12 workspace landing pages. Existing pages move under them unchanged.

**Step 4.** Retire the flat `Sidebar.tsx` route list in favour of registry-driven navigation.

---

### 3. Arabic & Saudi UX standard

#### 3.1 Keep

- Per-locale lazy dictionary loading (`PERF-FE-002`) — correct and performance-aware.
- 5,677-key parity between `en` and `ar`.
- `dir="ltr"` islands for LTR-by-nature tokens (hex colours, numerals with units, IBANs, IPs) — a subtle
  detail most teams miss. Document it as a rule; it currently exists as scattered good instinct.
- RTL-native exports (shaping, mirroring, bundled Amiri font across 22 generators) — **a genuine moat**.

#### 3.2 Fix

| Rule | Current | Target | Enforcement |
|---|---|---|---|
| **One translation mechanism** | `t()` in 163 files; inline `lang === 'ar'` in **431** | `t()` everywhere in `pages/**`; inline branch allowed only inside `components/` where layout genuinely differs | ESLint rule as a **ratchet** |
| **No hardcoded user-visible strings** | **271** JSX text nodes + **168** `label`/`title`/`placeholder` | 0 | ESLint: no bare Latin literal in JSX text or those props |
| **Every page reachable in Arabic** | **49 pages (15%)** contain no Arabic | 0 | Arabic-coverage CI report per module |
| **Bilingual data** | Backend already mandates `*_en` + `*_ar` | Client-side gate before submit so Arabic-mandatory fields fail in the form, not in a 400 | Form-level validation |

#### 3.3 Saudi-specific UX rules (currently informal — make them explicit)

| Rule | Why |
|---|---|
| **Hijri + Gregorian dates together**, Gregorian authoritative for audit timestamps | Saudi business practice; regulator submissions cite both |
| **Arabic-Indic vs Western numerals** — one consistent choice per locale, and **never** in identifiers, amounts or evidence hashes | Mixed numerals in a financial figure is a data-integrity smell to an auditor |
| **Names**: full Arabic name field alongside transliteration; never derive one from the other | PDPL identity accuracy; also why pronoun/gender must never be inferred from a name |
| **Regulator terminology** must match the Arabic used in the source regulation (NCA/SAMA/SDAIA/DGA wording), not a generic translation | An acceptance committee reads its own regulation's terms |
| **Government acceptance artefacts** (exports, minutes, board packs) must be Arabic-primary with correct RTL and e-signature detail | These leave the building |

---

### 4. Truthful UI — a design principle, not a bug fix

**Principle.** *The interface must never present data it did not receive from the system of record.*
Three states, always distinguishable: **has data** · **no data yet** (empty state + the action that
creates data) · **cannot load** (error + retry + what failed).

The design system already has `EmptyState` (109×) and `InlineEmpty` (133×) — the components exist and
the three critical surfaces simply do not use them.

**Enforcement:**

1. CI: no import matching `/mock/i` outside `**/*.test.*`.
2. **Backend-down acceptance test** — with the API stopped, every register shows an error state and
   **zero rows**. This is the test that makes the principle real; it is currently absent.
3. Loading skeletons must never render plausible placeholder *content* — shapes only.
4. Any figure on an executive surface must be traceable to an endpoint; a dashboard tile with no
   endpoint does not ship.

Priority order (blast radius, not count): `lib/searchIndex.ts` (7 mock imports — global search),
`pages/dashboard/widgets.tsx` (4 — executive dashboard), `lib/boardPack.ts` (3 — board pack). These are
the three surfaces an executive or acceptance committee opens first.

---

### 5. Persona journeys — the five that decide deals

`UX/PERSONA_JOURNEYS.md` (102 lines) covers personas thinly. What follows is the **decision-critical
path** for each buyer persona, with the measured gap on that path.

#### 5.1 CEO / Board member — *"is the organisation safe?"*
Login → **Executive workspace** → top risks, control effectiveness, open regulatory obligations,
incidents → drill into one risk → see owner, treatment, evidence → export board pack (Arabic, RTL).
**Gap:** the dashboard and board pack are mock-fed (F2-02) — the single most damaging defect on the most
senior journey. **Target:** every tile traceable to an endpoint; board pack assembled only from
persisted records with a generation timestamp and a data-completeness note.

#### 5.2 Risk Manager — *"is my register current and defensible?"*
My Work → overdue assessments → open risk → update assessment with control effectiveness → residual
recalculates → attach evidence → route for approval → KRI breach alerts.
**Gap:** `risk` is well tested (147 tests) — this journey is the platform's strongest. **Target:**
make it the reference demo, and the template for the other eleven workspaces.

#### 5.3 Internal Auditor — *"can I test a control and evidence my opinion?"*
Audit workspace → engagement → programme → test a control → sample → finding → CAPA → follow-up →
audit-committee reporting.
**Gap:** `audit_lc` has 58 tests, but `audit_eng` (0 tests) and `audit_committee` (0 tests, 8 peer
dependencies) sit on the same journey. **Target:** Tier-1 test floor across all three — this is
AuditBoard's home category and the comparison will be made explicitly.

#### 5.4 Compliance Officer — *"which obligations am I failing, and what do I do today?"*
Compliance workspace → framework (NCA ECC / SAMA CSF / PDPL) → control coverage heat map → gaps →
assessment campaign → attestations → remediation → regulator submission.
**Gap:** `nca_ecc` has **0 tests** and `controlmap` writes into the `evidence` schema. The most-cited
Saudi framework sits on the least-assured code.

#### 5.5 DPO / Data Officer (PDPL) — *"can I answer a DSR in time and prove it?"*
Privacy workspace → RoPA → DPIA → **DSR intake → identity verification → fulfilment within statutory
period** → breach assessment → 72-hour notification.
**Gap:** `data_officer` — **2,373 LoC, 37 endpoints, 1 entity, 0 tests**. A statutory-deadline workflow
with no automated proof it works. **Target:** highest-priority Tier-1 floor after `outsourcing`; a
missed statutory deadline is a regulatory event for the customer, caused by the platform.

#### 5.6 Cross-persona: the acceptance committee
A government acceptance committee does not follow a persona journey — it **samples**. It picks a
control, asks to see it enforced, asks for the evidence, and asks who approved it. The platform answers
this well where the committee engine is involved (quorum, recusal, e-signed minutes, audit ledger) and
answers it badly where a control is *configured* rather than *coded* (F2-14: `committee_gate_map` was
empty in every tenant while the matrix demanded it). **Target:** the Control Health surface (F2-14) —
turn "which of our controls are actually in force?" into a screen. No competitor ships this.

---

### 6. Forms, wizards, tables

Measured: `FormModal` 55×, `DataTable` 73×, `AttachmentsField` 42× — real reuse, and the wizard
inventory (`DsrFulfilmentWizard`, `ControlTestWizard`, `VulnerabilityTriageWizard`, `BcmExerciseWizard`,
`SupplierOnboardingWizard`) shows the pattern is established. These same wizards are the **worst
offenders for hardcoded English** (15, 14, 13, 11, 9 strings respectively) — high-value screens with
the weakest localisation.

| Standard | Rule |
|---|---|
| **Bilingual pairs** | Every `*_en` field renders beside its `*_ar`; Arabic-mandatory fields blocked client-side before submit |
| **Error surfacing** | Keep `problemMessage()` folding RFC-7807 `errors` into field-named toasts — good practice already in place |
| **Destructive actions** | Named confirmation (type the record name), never a bare `confirm()` |
| **Four-eyes** | Where the server enforces a 409 SoD gate, the UI must **say so before submission** — "you cannot approve your own request" beats a 409 after the fact |
| **Tables** | Server-side pagination/sort/filter for every register (untested at volume — F2-17); sticky headers; column choice persisted per user |
| **Wizards** | Resumable, with saved drafts; no wizard longer than 5 steps without a progress save |
| **Page size** | No new page over 800 lines; existing offenders may only shrink (ratchet) |

---

### 7. Accessibility — measured, with one serious open violation

**Corrected.** An earlier draft of this section stated that no accessibility run had been performed.
That was wrong: the tracked `audit/` evidence vault (2026-06-18 @ `3ca91bf`) contains a real
accessibility assessment using **Playwright 1.49 + `@axe-core/playwright`** and **Lighthouse**.

| Measure | Result |
|---|---:|
| Lighthouse `/login` — **accessibility** | **100** |
| axe-core violations on `/login` | **0** |
| Pages scanned by axe | **10** |
| Authenticated-page violation | **`color-contrast` — WCAG 1.4.3, severity *serious*** |
| Instances | **68** |
| Lighthouse `/login` — best-practices · SEO | 77 · 82 |
| Lighthouse `/login` — performance | 38 ⚠ *measured against the Vite **dev** server; not representative of a production build — treat as unmeasured* |

**So the real position is better than "unmeasured" and worse than "conformant".** The login surface is
clean and scores 100. The authenticated application carries **68 instances of a serious colour-contrast
failure** — the single most common WCAG failure in enterprise dashboards, and the one that most affects
users reading dense risk and compliance tables.

**Why this is cheap to fix, and why that matters.** Contrast is a **token** problem, and this platform
has central tokens (`styles/tokens.css`, 15 KB) with 99% component adoption (§1). 68 reported instances
almost certainly trace to a small number of token pairs — muted text on tinted card backgrounds, badge
foregrounds, disabled states. Fixing the tokens fixes every instance at once. This is the highest
accessibility return per hour available anywhere in the frontend.

**Recommendation.** (1) Extract the 68 instances from `audit/findings/dyn-a11y.json`, group by
token pair, and fix at the token level — not per component. (2) Re-run the existing axe harness to
confirm zero. (3) Wire `@axe-core/playwright` into CI over the 12 workspace landing pages plus the top
20 registers, as a **ratchet** (violations may only fall). (4) Extend beyond contrast to focus
management, keyboard reachability and screen-reader labelling — none of which the current 10-page sweep
reports on, so they remain genuinely unmeasured. (5) Then issue a WCAG 2.1 AA conformance statement:
DGA digital-accessibility expectations make it a procurement question for Saudi government buyers, and
the evidence to support one is now within reach.

**Honest RFP answer today:** "axe-core and Lighthouse are in our audit pipeline; the login surface passes
with an accessibility score of 100 and zero violations; one serious colour-contrast issue is open across
authenticated pages and is being remediated at the design-token level. A formal AA conformance statement
is not yet issued."

---

### 8. UX work plan

| Wave | Work | Effort | Value |
|---|---|---|---|
| **Now** | Route reconciliation matrix · route↔nav CI test · de-mock the 3 executive surfaces · backend-down acceptance test | S (~1 wk) | Converts unknowns into decisions; removes the trust risk |
| **1** | i18n lint ratchets · migrate 439 hardcoded strings · Arabic coverage report · fix the 49 zero-Arabic pages | S–M | Makes the primary differentiator measurable |
| **2** | 12 workspace landing pages · registry+entitlement-driven navigation · retire the flat sidebar | M–L | The IA redesign |
| **3** | Split `EsgPage` (5,583) then `NdiPage` (4,970, after its characterisation tests) · page-size ratchet · remaining 26 mock sites | M | Velocity, deep-linking, hygiene |
| **4** | `axe-core` AA pass · Control Health surface · server-side pagination at volume | M | Procurement answers |

**What is deliberately not in this plan:** a visual redesign, a component-library replacement, and a
CSS-framework migration. The evidence says these would consume the budget the IA and truthfulness work
needs, and would discard a 99%-adopted design system that is doing its job.

---

## Part 6: Documentation Register & Audit Support

*Source: [`Appendix/DOCUMENTATION_REGISTER_AND_AUDIT_SUPPORT.md`](TSS-GRC-Platform-Bible/Appendix/DOCUMENTATION_REGISTER_AND_AUDIT_SUPPORT.md)*

**Date:** 2026-07-25 · **Tree:** `c657734f` · **Scope:** every Markdown document in the repository.
**Machine-readable companion:** [`DOCUMENT_REGISTER.csv`](TSS-GRC-Platform-Bible/Appendix/DOCUMENT_REGISTER.csv) — all **331** tracked
files with git provenance. **Regenerate:** `py Appendix/scripts/doc_register.py`.

**Purpose.** Two jobs. (1) Tell a reader — auditor, acceptance committee, new engineer, diligence
reviewer — **which documents govern and which are historic**, because today nothing does.
(2) Give every finding in `Registers/FINDINGS_REGISTER_V2.md` a **re-verification command**, so no
conclusion has to be taken on trust.

---

### 1. Census

| Measure | Value |
|---|---:|
| Markdown files **on disk** | **604** |
| Markdown files **tracked in git** | **331** |
| Untracked / duplicated on disk | **273** |
| Tracked lines · bytes | **74,878** lines · **4.35 MB** |
| Documents not touched since 2026-06-30 | **214 (65%)** — **50,332 lines (67% of the corpus)** |
| Documents touched in July 2026 | 98 |

#### 1.1 Eight corpora, four of them competing

| Corpus | Files | Lines | Newest | Oldest | Authority today |
|---|---:|---:|---|---|---|
| `TSS-GRC-Platform-Bible/` | 56 | 6,379 | 2026-07-25 | 2026-07-25 | ✅ **Governing reference** |
| `<root>` | 38 | 15,262 | 2026-07-25 | 2026-04-30 | ✅ Mixed — plans, operations, **and 3 historic snapshots** |
| `docs/` (V1 era) | 158 | 40,395 | 2026-07-13 | 2026-04-30 | ⚠ **Largest corpus, mostly superseded, unmarked** |
| `docs/Saudi-GRC-Platform-Integration/` | 33 | 4,208 | 2026-05-18 | 2026-05-18 | ⚠ Competing generation **B** |
| `Saudi-GRC-Platform-Integration/` | 20 | 4,267 | 2026-05-14 | 2026-05-11 | ⚠ Competing generation **A** |
| `review/` | 8 | 710 | 2026-07-22 | 2026-07-04 | 🟢 Review artefacts (evidence) |
| `sprints/` | 4 | 1,328 | 2026-05-08 | 2026-04-30 | ⬛ Historic |
| Other (`tss-grc-backend/`, `tss-grc-ui-v2/`, `reports/`, `.tools/`, `TSS-Theme/`, `deploy/`) | 14 | 2,329 | 2026-07-09 | 2026-04-30 | 🟢 Local READMEs |

---

### 2. Corpus-level defects

These are findings about the **documentation control itself**. They matter to an audit because an
auditor's first act is to ask "what is the current documentation?" — and today that question has no
single answer.

#### D-1 · A stale git worktree duplicates the entire corpus *(High)*
`.claude/worktrees/vigilant-swartz-073bd4` is a **live registered worktree** on branch
`fix/break-glass-sweep-tx` @ `ccc06799`, excluded from git via `.git/info/exclude`. It contains a full
copy of the repository — **267 of the 273 untracked Markdown files**, including a second `STATUS.md`
(3,119 lines), a second `PROJECT_CHRONICLE.md` and a second `docs/` tree.

**Why it matters:** any auditor or engineer who greps the working tree gets **doubled hits**, half of
them pointing at a stale copy on a branch already merged to master. Nothing marks which is which.

**Fix:** `git worktree remove .claude/worktrees/vigilant-swartz-073bd4` (the branch is merged; verify
with `git branch --merged master` first). One command; removes 273 phantom files.

#### D-2 · Two competing generations of the Saudi integration pack *(High)*
`Saudi-GRC-Platform-Integration/` (20 files, 2026-05-11…14) and
`docs/Saudi-GRC-Platform-Integration/` (33 files, 2026-05-18) are **not duplicates** — measured
file-by-file, they share **exactly one** relative path (`README.md`), and even that has **drifted**
(94 lines vs 72). Generation A has one document per numbered folder (`product-blueprint.md`);
generation B is more granular and adds documents A lacks (`04_API_Object_Model.md`,
`05_RTL_Localization_Requirements.md`, `07_Control_Library_Import_Template.md`).

**Worse than duplication:** two different documents, same folder names, no marker saying which governs.
A reader who finds one will not know the other exists.

**And the anchor is gone.** Generation A's README states every document is "anchored to the running
code, schema, and seed data at HEAD of branch `feat/pdpl-extensions`" — **that branch no longer
exists** (`git branch -a --list "*pdpl*"` returns nothing). The pack's own stated provenance is
unverifiable.

**Fix:** decide which generation governs, stamp the other `SUPERSEDED BY <path>`, and replace the dead
branch anchor with a commit SHA.

#### D-3 · 65% of the corpus is unmarked and stale *(High — this is `W0-6`, still open)*
**214 tracked documents / 50,332 lines** have not been touched since 2026-06-30 — i.e. before the
remediation programmes that changed the platform's authorisation model, committee engine, export layer,
workflow adoption and permission catalogue. None carries a `HISTORIC` or `SUPERSEDED` stamp.

**Why it matters:** `docs/analysis/10-module-definitions.md` (1,146 lines, 2026-04-30) and
`docs/analysis/15-licensing-model.md` (691 lines) describe module and licensing models predating the
current code. A reader has no signal that they are reading April.

#### D-4 · The largest document in the repository is the most misleading *(High)*
`STATUS.md` — **3,119 lines**, last commit 2026-06-01. `CLAUDE.md` itself warns its snapshot is
**2026-04-22 and predates all remediation programmes**. It is the biggest, most authoritative-looking
document in the tree and it is three months stale, unmarked in the file itself.

**Fix:** a `> ⛔ HISTORIC — snapshot 2026-04-22. Superseded by PROJECT_CHRONICLE.md and
UNIFIED_IMPLEMENTATION_PLAN.md.` banner at line 1. Same for `SESSION_SUMMARY.md` (939 lines,
2026-04-30), `summary.md` (398, 2026-05-02), `PROJECT_BUILD_HISTORY.md`, `sprints/*`.

#### D-5 · No document register, owner, or review cycle *(Medium)*
331 governing-and-historic documents with no index, no per-document owner, no review date, no
supersession mechanism. This is the root cause of D-1 through D-4 **and** of F2-19 (documentation
asserting numbers nothing verifies) — including the three wrong numbers this project published in its
own plans.

**Fix:** this file plus `DOCUMENT_REGISTER.csv` is the register. Make it generated (the script exists),
add `owner` and `review_by` front-matter to governing documents, and fail CI when a governing document
passes its review date.

#### D-6 · The corpus is 74,878 lines with no reading path *(Medium)*
A new engineer or auditor faces 331 files and no stated order. `CLAUDE.md` and the Bible `README.md`
each provide a partial map; neither covers the other's corpus. §3 below is the missing reading path.

---

### 3. What governs — the authoritative reading set

**Read in this order.** Everything not listed here is supporting, historic, or superseded.

#### Tier 1 — start here (4 documents, ~1,750 lines)
| Document | Lines | Why |
|---|---:|---|
| [`CLAUDE.md`](CLAUDE.md) | 200 | Operating instructions, commands, house rules, top landmines. **Corrected 2026-07-25** (24 → 58 modules) |
| [`PROJECT_CHRONICLE.md`](PROJECT_CHRONICLE.md) | 1,127 | The master business + functional reference for every module, screen and API; installation, operations, troubleshooting, all 25 landmines |
| [`UNIFIED_IMPLEMENTATION_PLAN.md`](UNIFIED_IMPLEMENTATION_PLAN.md) | 323 | **The single governing plan** — stages S0–S9, tracker, migration ledger. All other plans feed it |
| [`TSS-GRC-Platform-Bible/README.md`](TSS-GRC-Platform-Bible/README.md) | 122 | Index to the reference corpus + the independent review pass |

#### Tier 2 — the evidence base (6 documents)
| Document | Lines | Role in an audit |
|---|---:|---|
| [`Appendix/MEASURED_BASELINE.md`](#part-3-measured-baseline-the-evidence-base) | 300 | **Every number an auditor needs, with reproduction scripts.** Cite this, not prose |
| [`Registers/FINDINGS_REGISTER_V2.md`](#part-2-findings-register) | 604 | 22 findings, 18 fields each, baseline-anchored |
| [`Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md`](#part-1-independent-acceptance-verdict) | 188 | Three verdicts, 13-dimension scorecard, 4 conditions |
| [`Governance/BIBLE_VALIDATION_LOG.md`](#part-7-validation-of-the-existing-corpus) | 127 | Which documents in this corpus are verified vs asserted |
| [`Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md`](#part-4-module-separability-sku-model) | 257 | Per-module separability tier + SKU catalogue |
| [`UX/UX_REDESIGN_BLUEPRINT.md`](#part-5-ux-redesign-blueprint) | 243 | Measured UX position and the IA redesign |

#### Tier 3 — decisions (12 ADRs)
`Decisions/ADR-001` … `ADR-012`. **Every ADR has a "Proof" section naming tests that mostly do not
exist** — see `BIBLE_VALIDATION_LOG.md` §3. `ADR-006` is contradicted in implementation by **F2-21**.

#### Tier 4 — operations
[`DEPLOYMENT.md`](DEPLOYMENT.md) (323) · [`LOCAL_SETUP.md`](LOCAL_SETUP.md) (243) ·
[`AGENTS.md`](AGENTS.md) (183) · [`reports/audit-report.html`](../../reports/) (the 287-finding platform audit)

#### Tier 5 — programme plans (24 files, 7,972 lines)
Each carries a `§0` implementation-status tracker. **Verify status from the tracker, not the title.**

| Plan | Lines | Programme status *(from git history + plan §0)* |
|---|---:|---|
| `UNIFIED_IMPLEMENTATION_PLAN.md` | 323 | 🔵 **ACTIVE — governing.** S1–S4 done; **DF-3 (P0) next**; S5–S9 open |
| `CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md` | 596 | 🔵 **OPEN** — CE-1…CE-16 = unified stages S5–S9 |
| `REMEDIATION_MASTER_PLAN.md` | 309 | 🟢 Folded into the unified plan |
| `INDEPENDENT_PLATFORM_ASSESSMENT.md` | 264 | 🟢 Assessment input; findings tracked in the unified plan |
| `FOUNDATION_PACK_ASSESSMENT_AND_ACTION_PLAN.md` | 241 | 🟢 Assessment input. ⚠ Its rigour judgement on the vendor pack is **corrected** by `BIBLE_VALIDATION_LOG.md` §1 |
| `CHANGE_AND_POLICY_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` | 367 | ✅ Complete — 15 slices + 4 tracks merged, through V309 |
| `CHANGE_MANAGEMENT_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` | 216 | ✅ Complete (subsumed by the combined plan above) |
| `MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (compliance/policy) | 534 | ✅ Complete — merged `a21da1a5` |
| `ERM_RISK_MANAGEMENT_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` | 476 | ✅ Complete — merged `2c3877b5`, V241–V247 |
| `BCM_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` | 422 | ✅ Complete — PR #487 `ba64ee55`, V235–V239 |
| `SUSTAINABILITY_ESG_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` | 501 | ✅ Complete — 18 epics, merged `868eb6e6`, V248–V257 |
| `SUSTAINABILITY_GREEN_FINANCE_REVIEW_AND_UPLIFT_PLAN.md` | 284 | ✅ Complete — merged `73fb9399`, V285–V295 |
| `ESG_CAPA_GREEN_FINANCE_REMEDIATION_PLAN.md` | 251 | ✅ Complete — 12/12, merged `abf1321b`, V297–V299 |
| `CYBER_SOC_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` | 299 | ✅ Complete — 15/15, merged `0c244f65`, V261 |
| `RBAC_ACCESS_GOVERNANCE_MODULE_PLAN.md` | 321 | ✅ Complete — merged `73fb9399`, V280–V284 |
| `SGI_MODULE_REVIEW_AND_UPLIFT_PLAN.md` | 253 | ✅ Complete — merged `40b955e7`, V296 |
| `FORMS_SCREENS_AND_WORKFLOW_ENTRY_REMEDIATION_PLAN.md` | 332 | ✅ Complete — merged `e2227397`, V277–V278 |
| `EXPORT_FILES_TEMPLATES_QUALITY_REVIEW_AND_REMEDIATION_PLAN.md` | 272 | ✅ Complete — merged `3baceb8e` |
| `EXPORT_TXT_TO_FORMATS_PLAN.md` | 112 | ✅ Complete — `1f0a7225` |
| `WORKFLOW_ENGINE_ENHANCEMENTS_PLAN.md` | 92 | ✅ Complete — merged `8421652e`, V269–V276 |
| `WORKFLOW_ADOPTION_PROGRAM.md` | 108 | ✅ Complete — all 14 modules, `67ba1ea4` |
| `WORKFLOW_LOGIC_AND_LIFECYCLE_REMEDIATION_PLAN.md` | 127 | ✅ Complete — 2026-07-06, 7 batches |
| `PLATFORM_RESIDUALS_REMEDIATION_PLAN.md` | 22 | ⚪ Stub — 22 lines; fold in or delete |
| `BCM_GRC_GAP_ANALYSIS_AND_PLAN.md` | 316 | ⬛ Historic (2026-05-23) — superseded by the BCM plan |

#### Tier 6 — stamp HISTORIC (7 files, 5,220 lines)
`STATUS.md` (3,119) · `SESSION_SUMMARY.md` (939) · `summary.md` (398) ·
`PROJECT_BUILD_HISTORY.md` (320) · `SESSION_SUMMARY_2026-06-18.md` (197) · `sprints/` (4 files, 1,328) ·
plus `docs/` per D-3.

---

### 4. Audit evidence chain

**For every finding: the document that states it, the measurement behind it, and the command that
re-verifies it.** Nothing here needs to be believed.

| ID | Finding | Stated in | Evidence | Re-verify |
|---|---|---|---|---|
| **F2-21** | Committee votes under a caller-supplied name | Register V2 · Verdict §2 | `CommitteeResolutionController.java:59-61`; `CommitteeResolutionService.recordVote`; contrast `CommitteeController.java:113-114` | `grep -n "req.memberName()" tss-grc-backend/src/main/java/com/tss/grc/module/admin/committee/controller/CommitteeResolutionController.java` |
| F2-01 | 58 modules, 24 documented | Baseline §0,§1 | Module census | `py Appendix/scripts/coupling.py` |
| F2-02 | Mock data in search/dashboard/board pack | Baseline §5.1 | 44 imports / 29 files | `py Appendix/scripts/verify.py` §3 |
| F2-03 | Saudi modules untested | Baseline §6 | 19 pkgs / 20,503 LoC | `py Appendix/scripts/verify2.py` §D |
| F2-04 | Entitlement exists, 8.6% wired | Baseline §4.1 | `platform/licensing/**`; 22/255 controllers; 0 FE callers | `grep -rc "@RequireModule" --include=*Controller.java tss-grc-backend/src/main/java \| grep -v ":0"` |
| F2-05 | 21 modules guard by role name | Baseline §4 | Guard-style census | `py Appendix/scripts/verify2.py` §A,§B |
| F2-06 | 9 modules in foreign schemas | Baseline §3 | `@Table(schema=…)` vs owner | `py Appendix/scripts/verify.py` §2 |
| F2-07 | 144 routed pages not in nav | Baseline §5 | 328 routes vs 184 nav | `py Appendix/scripts/verify.py` §7 |
| F2-08 | Export toolkit in `reporting`; god-consumer | Baseline §2.2 · ADR-012 | 734 imports, 14 bidirectional pairs, 25 toolkit-only edges | `py Appendix/scripts/coupling.py` |
| F2-09 | Dev/prod schema, two mechanisms | Baseline §7 | 304 migrations, Flyway off in dev | `CLAUDE.md` §Database; `git log --oneline -- '*/db/migration/*'` |
| F2-10 | Production runs known-fixed defects | Register V2 · plan W0-2 | Staged `…jar.new` not activated | `Get-Service TssGrcBackend`; `Test-Path '…jar.new'` |
| F2-11 | Bilingual split; 439 hardcoded strings | Baseline §5.2 | `t()` 163 files vs `lang==='ar'` 431 | `py Appendix/scripts/ux.py` |
| F2-12 | `ndi` 127 endpoints / 11 tests | Baseline §1,§6 | Census | `py Appendix/scripts/coupling.py` |
| F2-13 | Ratchet = coverage, not tightness | Register V2 | `WORKFLOW_WRITE` in 55/57 roles | `ControllerAuthorizationRatchetTest` javadoc; grant query in `iam` |
| F2-14 | Config-gated controls, no health check | Register V2 | `committee_gate_map` empty; `V314`; `ChangeGovernanceHealthService` | `GET /api/v1/changes/governance-health` |
| F2-15 | 3,650 lines of mock data shipped | Baseline §5 | 15 files | `py Appendix/scripts/verify.py` §4 |
| F2-16 | Two page monoliths (10,553 lines) | Baseline §5.4 | `EsgPage` 5,583 · `NdiPage` 4,970 | `py Appendix/scripts/ux.py` §D |
| F2-17 | Perf evidence covers 1 of 2,160 endpoints | Baseline §7.2 | k6: 18,763 reqs, 0 errors, p95 26.9 ms, one read endpoint | `py -c "import json;print(json.load(open('audit/metrics.json'))['load_test_k6'])"` |
| F2-18 | Governance core unit-tested only | Register V2 | 93 committee tests, all mocked | `git log --oneline 00939e76..ab28f348` |
| F2-19 | Documents assert unverified numbers | Baseline §0 | 3 corrections | This file · `BIBLE_VALIDATION_LOG.md` |
| F2-20 | `datagov` empty module | Baseline §1.1 | 1 file, 18 lines | `find tss-grc-backend/src/main/java/com/tss/grc/module/datagov -type f` |
| F2-22 | 216 entity-bound request bodies | Register V2 | 85 controllers | `py Appendix/scripts/verify2.py` (extend) |
| **D-1…D-6** | Documentation corpus defects | **this file** | 604 on disk / 331 tracked | `py Appendix/scripts/doc_register.py` |

---

### 5. Acceptance-committee sampling guide

A committee samples rather than reads. These five samples reach the platform's real state fastest.

| # | Sample | Expected today | Where it is written up |
|---|---|---|---|
| 1 | Pick a committee resolution. Ask **who cast each vote and prove it.** | ❌ Votes carry a name supplied by the caller; no `recorded_by` exists | **F2-21** |
| 2 | Pick a CAB-gated change in org 2 or 3. Ask to see the **committee decision** | ❌ No committee mapped → falls back to the legacy approval tally | **F2-14**, `V314` |
| 3 | Log in as a tenant licensed for one module. Try another module's endpoint | ❌ 200 — entitlement is wired to `socops` only | **F2-04** |
| 4 | Open global search / the executive dashboard / a board pack on an empty tenant | ❌ Fabricated records appear | **F2-02** |
| 5 | Ask for the test evidence for `outsourcing` (SAMA) or `nca_ecc` | ❌ Zero tests | **F2-03** |

**What passes:** ask for a **bilingual Arabic RTL export** of any register (22 generators, verified), a
**tenant-isolation** demonstration (org-scoped repositories, 10 tests), the **audit ledger** for a
resolution, or the **evidence integrity** chain (SHA-256, four-eyes).

---

### 6. Evidence an auditor will request that does not exist

Stated plainly so it is not discovered as a surprise:

**Corrected 2026-07-25.** A first draft of this table listed several items as absent that are in fact
present in the tracked `audit/` evidence vault (98 files, 2026-06-18 @ `3ca91bf`, 12 named tools). The
corrected position — and the vault is a genuine strength:

| Requested | Status |
|---|---|
| **Load/capacity test** | ✅ **EXISTS** — k6, 4 scenarios, 18,763 requests, 0 errors, p95 **26.9 ms** (`audit/raw/k6-summary.json`). ⚠ **1 of 2,160 endpoints**, read-only, dev volumes → F2-17 |
| **Accessibility audit** | ✅ **EXISTS** — Lighthouse `/login` **a11y 100**; axe-core over **10 pages**, 0 login violations. ⚠ **68 `color-contrast` instances (WCAG 1.4.3, serious)** on authenticated pages — open |
| **Secret scanning** | ✅ **EXISTS** — gitleaks 8.30.1: **21 hits, all verified false positives** (placeholder tokens, `localStorage` key constants). No leakage |
| **Dependency CVE scanning** | ✅ **EXISTS** — trivy 36 vulns; `npm audit` 5 (1 high). ⚠ untriaged by severity |
| **Python SAST** | ✅ **EXISTS** — bandit 1.9.4 |
| **Code-duplication analysis** | ✅ **EXISTS** — jscpd: **5.41%**, 1,666 clones over 389,021 lines |
| **Backend test coverage** | ✅ **EXISTS** — JaCoCo: instruction **37.1%**, branch **26.0%**, line **38.5%** |
| **E2E functional evidence** | ✅ **EXISTS** — Playwright 1.49 (`audit/findings/dyn-*.json`, screenshots) |
| SLA / capacity model | ❌ None — the k6 ceiling was never reached, so no model was derived |
| **Per-module** test-coverage report | ❌ None — `MEASURED_BASELINE.md` §6 is the first |
| Penetration-test report | ❌ None (scanning is not a pentest) |
| WCAG 2.1 AA **conformance statement** | ❌ None — evidence exists, a signed statement does not |
| Backup/restore rehearsal record | ❌ Not found |
| Dev/prod schema equivalence proof | ❌ None — F2-09 |
| Business-continuity plan **for the platform vendor** | ❌ Not found (the product manages customers' BCM; the vendor's own is absent) |
| Signed data-processing / residency statement | ❌ Not found |
| Forged-actor test evidence (**required by `ADR-006`**) | ❌ None — and F2-21 is why it matters |
| **ESLint configuration** | ❌ **`eslint_configured: false`** — blocks F2-11's lint ratchets until it exists |

**Where the vault lives:** `audit/` is tracked (98 files) and contains **no Markdown at all**, which is
why a documentation sweep missed it. That is the concrete argument for D-5 — evidence with no index is
evidence nobody finds.

---

### 7. Corpus remediation — closes `W0-6`

| # | Action | Effort |
|---|---|---|
| 1 | `git worktree remove .claude/worktrees/vigilant-swartz-073bd4` (after `git branch --merged master`) | **2 min** — removes 273 phantom files |
| 2 | `HISTORIC` banner at line 1 of the 7 Tier-6 files | 15 min |
| 3 | `HISTORIC` banner + a pointer to the current equivalent across `docs/` (158 files) | 1 h, scriptable |
| 4 | Decide which Saudi pack generation governs; stamp the other `SUPERSEDED BY`; replace the dead `feat/pdpl-extensions` anchor with a SHA | 30 min + one decision |
| 5 | Delete or fold in `PLATFORM_RESIDUALS_REMEDIATION_PLAN.md` (22-line stub) | 5 min |
| 6 | Add `owner` / `review_by` front-matter to Tier 1–4; CI fails on an overdue governing document | 2 h |
| 7 | Run `doc_register.py` in CI so this register and the CSV cannot drift | 30 min |

**Sequence note:** step 1 first. Every other step is cheaper once the corpus is not doubled — and a
stale worktree is the one defect here that can cause someone to *edit the wrong file*.

---

### 8. Reproduction

```bash
py TSS-GRC-Platform-Bible/Appendix/scripts/doc_register.py   # this register + DOCUMENT_REGISTER.csv
py TSS-GRC-Platform-Bible/Appendix/scripts/coupling.py       # module census + coupling graph
py TSS-GRC-Platform-Bible/Appendix/scripts/verify.py         # schemas, mocks, routes vs navigation
py TSS-GRC-Platform-Bible/Appendix/scripts/verify2.py        # guard styles, permissions, Saudi coverage
py TSS-GRC-Platform-Bible/Appendix/scripts/ux.py             # i18n, design system, page sizes
```

**Counting rules.** *Tracked* = `git ls-files "*.md"` (331). *On disk* = filesystem walk excluding
`node_modules`, `.git`, `dist`, `target`, `build`, `.vite`, `coverage` (604) — the 273-file gap is
almost entirely D-1's worktree. *Lines* = raw line count. *Currency* is derived from the **newest
commit touching the file**, so it measures neglect, not correctness: a stale document may still be
right, and a current one may still be wrong (which is exactly what F2-19 records).

---

## Part 7: Validation of the Existing Corpus

*Source: [`Governance/BIBLE_VALIDATION_LOG.md`](TSS-GRC-Platform-Bible/Governance/BIBLE_VALIDATION_LOG.md)*

**Purpose.** The brief says: *"Treat those documents as evidence. Do not treat them as truth. Validate
them. Challenge them."* This log records the result of doing that — per document, with the specific
check applied.

**Date:** 2026-07-25 · **Validator:** independent review · **Method:** re-derive every checkable
quantitative claim from source (`Appendix/MEASURED_BASELINE.md`), then read the argument for internal
consistency and for conflict with measurement.

### Verdict codes

| Code | Meaning |
|---|---|
| ✅ **VERIFIED** | Quantitative claims independently reproduced; conclusions supported |
| 🟢 **SOUND** | No checkable numbers, but argument is coherent and consistent with measurement |
| 🟡 **UNVERIFIED** | Plausible; asserts things this review could not test — treat as opinion |
| 🟠 **PARTLY CONTRADICTED** | Some claims stand, at least one is refuted by measurement |
| 🔴 **CONTRADICTED** | Central claim is refuted |
| ⚪ **NOT READ** | Outside this pass; no verdict offered |

---

### 1. Headline result — the foundation pack is better than its packaging suggested

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

### 2. Document-by-document

#### Decisions/

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

#### Modules/ · Architecture/

| Document | Verdict | Validation notes |
|---|---|---|
| `Modules/SOURCE_MODULE_INVENTORY.md` | ✅ **VERIFIED** | **The best artefact in the Bible.** All 58 modules, file counts exact against independent measurement, `datagov` correctly identified as a placeholder, zero-test modules correctly flagged, and eight genuine consolidation questions posed (`audit_eng` vs `audit_lc`; `approvals` vs workflow chains vs committee decisions; `tprm` vs `scm` vs `outsourcing`; `privacy` vs `data_officer` vs `dataclass`; `*_lc` duplication; `incident` ownership; `capa` ownership; read models vs peer queries). **All eight are real and this review reaches the same conclusions independently.** Its weakness is not accuracy — it is that **no control reads it** (F2-01). |
| `Architecture/MODULE_INDEPENDENCE_AND_PACKAGING.md` | ⚪→🟡 | Read in outline only. Superseded on measured facts by `Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md`, which adds per-module tier verdicts derived from the coupling graph. |
| `Heatmaps/MODULE_INDEPENDENCE_MATRIX.md` | 🟠 **PARTLY CONTRADICTED** | The **target** interaction matrix (Q/E/R contracts) and the independence-score weighting are thoughtful and worth keeping. But its per-package rows are **aspirational target states presented in a "Baseline level" column**, and it says "No commercial package has a mandatory peer dependency **in the target**" — a target easily misread as current. Measurement: 13 modules have genuine peer dependencies, 9 have foreign schemas. Also groups the 58 packages into ~20 commercial names without stating the mapping, so it cannot be reconciled against the filesystem. **Superseded** by the SKU model, which tiers all 58 by measured evidence. |
| `Architecture/SOLUTION_ARCHITECTURE.md`, `ARCHITECTURE_PRINCIPLES.md` | 🟢 **SOUND** | Consistent with observed structure. Principles are the right ones; as with ADR-001, they are unenforced by any test. |

#### Executive/ · Governance/

| Document | Verdict | Validation notes |
|---|---|---|
| `Executive/EXECUTIVE_VERDICT.md` | ✅ **VERIFIED (in its central claims)** | "Feature-rich but not yet trust-rich" is, after measurement, the correct summary. Verified: entitlements do not protect backend capability (F2-04); module separation is not an installable-product property (F2-06/F2-08); **"governance decisions can be authored under caller-supplied identities" (F2-21 — located and confirmed)**; "do not equate 2,316 green tests with acceptance readiness" (F2-18 — and F2-21 proves the point, since 2,415 green tests do not see it). Its "do not" list is sound advice this review endorses. Unverified: its claims about fake quorum beyond F2-21, and backup/restore readiness. |
| `Executive/GOVERNMENT_ACCEPTANCE_OPINION.md` | ⚪ **NOT READ** | Covered independently by `Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md`. |
| `Governance/ASSESSMENT_METHOD.md` | 🟢 **SOUND** | Explicit scope exclusions (not legal advice, not a penetration test, not a WCAG audit) — correct practice, and the same discipline as Baseline §8. Its weighted score **3.0/5 = 6.0/10** is close to this review's independent **5.3/10**; the sub-scores agree on the shape (packaging 2.4/5 and security 1.8/5 lowest, domain depth 4.2/5 highest). Two independent assessments converging is meaningful. |
| `Governance/EVIDENCE_AND_MAINTENANCE_STANDARD.md` | 🟢 **SOUND** | Read in outline. Its intent — evidence discipline for the corpus — is what F2-19 recommends making mechanical. |

#### Registers/ · Roadmap/ · Product/ · UX/ · Security/ · Data/ · Process/ · Integration/ · AI/ · Diagrams/

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

### 3. What this validation changes

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

### 4. What this validation did *not* do

- Adjudicate the 599-line `FINDINGS_REGISTER.md` finding by finding.
- Read `GOVERNMENT_ACCEPTANCE_OPINION.md`, or the Product/AI strategy documents beyond their claims.
- Verify ADR-004's transactional outbox, ADR-005's cache/storage/event/scheduler isolation layers, or
  ADR-008's AI accountability controls.
- Test any competitor claim. Competitor statements in this Bible describe publicly observable licensing
  and positioning behaviour only.

---

## Part 8: ADR-009 — Module Registry as a Build Artefact

*Source: [`Decisions/ADR-009-MODULE-REGISTRY.md`](TSS-GRC-Platform-Bible/Decisions/ADR-009-MODULE-REGISTRY.md)*

**Status:** Proposed
**Date:** 2026-07-25
**Supersedes:** nothing. **Depends on:** `Modules/SOURCE_MODULE_INVENTORY.md` (verified accurate).

### Context

The platform has **58** backend module packages. `Modules/SOURCE_MODULE_INVENTORY.md` documents all 58
accurately — its per-module file counts reproduce exactly under independent measurement. Yet
`CLAUDE.md`, `PLATFORM_ARCHITECTURE_PACK.md` and every remediation plan still operate on **24**, so 34
packages fall outside the scope of every cross-cutting programme: the permission catalogue, the test
bar, boundary enforcement, i18n coverage and the datasheet.

The inventory is correct and **nothing consumes it**. `module/datagov/` — one 18-line `package-info.java`,
no code — is listed as a capability. An accurate document that no control reads is not governance.

### Decision

Promote the inventory from prose to `module-registry.yaml`, a **build artefact**. Each entry declares:
`code`, `package`, `owner`, `sku`, `maturity` (`PLANNED│ALPHA│BETA│GA`), `separability_tier` (A/B/C/K),
`schema`, `permission_prefix`, `test_floor`, `fe_route_root`, `docs`.

CI fails when: a `module/*` package has no registry entry; a registry entry names a package that does not
exist; a declared `test_floor` is unmet; a declared `schema` disagrees with the module's `@Table` values;
or any datasheet or instruction file states a module count that disagrees with the registry.

The datasheet, licence matrix, module scorecard and frontend navigation are **generated from** the
registry — never hand-maintained.

### Alternatives

- **Keep the Markdown inventory:** rejected. An accurate Markdown inventory already exists and did not
  prevent a 40% understatement of the product. Prose is not a control.
- **Derive the list at runtime from Spring bean scanning:** rejected. It describes what exists, not what
  is intended, so it can never fail a build.
- **Consolidate 58 → ~20 commercial packages first:** attractive, and `SOURCE_MODULE_INVENTORY.md`
  already proposes target owners — but a large refactor that does not itself create the missing control.
  Do it *after* the registry, using the registry to track it.

### Consequences

One mandatory step per new module, and failing builds during adoption — which is the point: an invisible
omission becomes a cheap, immediate, visible failure. Every commercial artefact becomes true by
construction.

### Proof

Registry ↔ filesystem parity test; `datagov` either deleted or marked `PLANNED` and absent from
generated capability lists; the generated datasheet lists 57 implemented modules; no document states a
module count that the registry contradicts.

---

## Part 9: ADR-010 — Permission-Only Authorisation

*Source: [`Decisions/ADR-010-PERMISSION-ONLY-AUTHORISATION.md`](TSS-GRC-Platform-Bible/Decisions/ADR-010-PERMISSION-ONLY-AUTHORISATION.md)*

**Status:** Proposed
**Date:** 2026-07-25
**Relates to:** `ADR-006` (approvals use immutable authenticated user IDs).

### Context

Two authorisation idioms coexist and only one is enforced.

**27** permission modules are referenced through `hasPermission(MODULE, ACTION)`, evaluated by a single
`ModulePermissionEvaluator`. But **21 of 58** module packages contain no `hasPermission` call at all;
they guard with hard-coded role lists — `hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','SHARIA_OFFICER',
'SHARIA_BOARD_CHAIR','CRO')` and similar, including `socops` with 58 write endpoints.

The consequence is commercial, not stylistic: the platform's per-tenant RBAC (55-role persona matrix,
`V272`) has **no effect** on those 21 modules. A customer who creates the role "Sharia Reviewer" and
grants it `SHARIA_WRITE` still receives 403, because the code compares role *names*. Least privilege is
unachievable, and a module sold standalone cannot be administered by its buyer.

The authorisation ratchet (currently 21) accepts `hasAnyRole`, so it measures **coverage** and never
**tightness**. A separate observation: `WORKFLOW_WRITE` is granted to 55 of 57 roles including `VIEWER`,
so an endpoint can be guarded and effectively unrestricted.

### Decision

`hasAnyRole` / `hasRole` are **forbidden in `module/**`**. Every write endpoint is guarded by
`hasPermission(MODULE, ACTION)` against a catalogued permission. Role→permission mapping lives in data
(grants), never in code. `PLATFORM_ADMIN` bypass remains, in the evaluator only.

Conversion is behaviour-preserving: catalogue the permission, then grant it to exactly the roles the old
`hasAnyRole` list named, so day-one behaviour is identical. This is a refactor, not a policy change —
which is what makes it safe to do at scale.

Two ratchets, both monotonic: **coverage** (unguarded write endpoints — at 21, enumerated floor) and
**tightness** (`hasAnyRole` occurrences in `module/**` — to zero). A third report, not yet a ratchet:
roles-per-permission, flagging any business-write permission held by more than half of roles or by any
read-only, external or machine persona.

### Alternatives

- **Leave role guards, document the limitation:** rejected. It is a functional gap in the core value
  proposition, not a documentation gap.
- **Map roles → permissions at runtime inside the evaluator:** rejected. Hides the coupling instead of
  removing it, and leaves the catalogue incomplete, so no customer can see what is grantable.
- **Rebuild least privilege from scratch per persona:** the right eventual goal; the roles-per-permission
  report is the cheap first step that shows where it matters.

### Consequences

~21 migrations and ~150 annotation edits, parallelisable per module, each verified by a two-user live
probe (permitted role 200, `viewer` 403). Completes the RBAC promise and removes a standing enterprise
security-questionnaire failure. Tightening over-broad grants afterwards may affect existing users, so
each grant change needs a per-tenant impact query first.

### Proof

Tightness ratchet at zero; `viewer` receives 403 on every converted write endpoint while the previously
named role receives 200; a tenant-defined custom role granted `<MODULE>_WRITE` can perform the write —
the test that fails today for 21 modules.

---

## Part 10: ADR-011 — Schema Ownership Equals Module Ownership

*Source: [`Decisions/ADR-011-SCHEMA-OWNERSHIP.md`](TSS-GRC-Platform-Bible/Decisions/ADR-011-SCHEMA-OWNERSHIP.md)*

**Status:** Proposed
**Date:** 2026-07-25
**Makes enforceable:** `ADR-003` (data ownership), whose present-tense claim is not yet true.

### Context

50 JPA schemas serve 417 entities, broadly one schema per module. **Nine modules write into a schema they
do not own:**

| Module | Writes into | Entities | Assessment |
|---|---|---:|---|
| `incident` | `cyber` | 2 | Blocks separability — commercially material |
| `integrations` | `platform` | 6 | Integration state inside the kernel schema |
| `controlmap` | `evidence` | 1 | Owns no data of its own |
| `workflow_submission` | `workflow` | 1 | Probably one context split across two packages |
| `knowledge` | `kb` | 1 | Naming only |
| `governance` | `platform` | 1 | Kernel schema as a dumping ground |
| `signature` | `signature` + `platform` | 2 | Straddles two |
| `auth`, `attachment` | `platform` | 1 each | Acceptable — these *are* kernel |

A module that writes into a peer's schema cannot be migrated, backed up, exported, air-gapped, restored
or licensed independently. "Buy Incident Management" today implicitly ships `cyber`'s tables — and a
Saudi bank's DBA reviewing that purchase will ask why.

### Decision

Every module owns exactly one schema, named after its registry `code` (ADR-009). Only kernel packages
write to `platform`. No entity declares a schema owned by another module.

Where a foreign schema reflects a genuine single bounded context split across two packages
(`workflow_submission`/`workflow`, `controlmap`/`evidence`), **merge the packages** rather than move the
tables — the coupling is telling the truth about the boundary. Where the module is genuinely separate
(`incident`), move the tables: create the schema, `ALTER SCHEMA … TRANSFER`, and keep a view at the old
name for one release with a documented rollback recipe.

`incident` requires a product decision first: **is Incident Management a separate SKU, or part of Cyber?**
Decide by commercial intent, not code convenience. Merging into `cyber` is a legitimate answer — incident
response belongs to cyber operations in most GRC products.

### Alternatives

- **Accept and document:** tenable only under a suite-only licence model. If per-module sale is dropped,
  this ADR drops to hygiene.
- **Per-tenant database for every customer:** does not address ownership, and multiplies operational cost.
- **Schema-per-SKU rather than per-module:** simpler licensing story, but loses the module-level ownership
  that makes boundaries testable.

### Consequences

Schema moves are the highest-risk migration class in this codebase; each needs a compatibility view and a
rehearsed rollback. The enforcement test, by contrast, is nearly free and stops new violations
immediately — so it lands first, independent of any move.

### Proof

A test asserting every `@Entity`'s `@Table(schema=…)` equals its module's registry schema; per-module
schema dump/restore rehearsed for one Tier-A module end to end.

---

## Part 11: ADR-012 — Boundary Enforcement and Consumer-Owned Ports

*Source: [`Decisions/ADR-012-BOUNDARY-ENFORCEMENT-AND-PORTS.md`](TSS-GRC-Platform-Bible/Decisions/ADR-012-BOUNDARY-ENFORCEMENT-AND-PORTS.md)*

**Status:** Proposed
**Date:** 2026-07-25
**Makes enforceable:** `ADR-001` (whose Proof section requires "zero forbidden imports" — a test that
does not exist).

### Context

**734** direct cross-module imports and **14** bidirectional pairs (both figures independently confirmed
against `ADR-001`). Nothing in CI forbids any module importing any other, so the modular monolith is a
package *convention*, and an unenforced convention is a preference.

Measurement separates two problems that look identical in the graph:

**(a) A misplaced kernel utility.** Eight modules import `reporting` — `bcm`, `sust`, `iam`, `socops`,
`ndi`, `cyber`, `ai`, `admin`. Inspection of all 25 inbound edges shows they import **only** export
utilities: `PdfTheme`, `PdfFonts`, `XlsxStyler`, `ExportNaming`, `RegisterExport`, `TabularExport*`. None
imports reporting *logic*. This also manufactures the apparent `socops → reporting` cycle.

**(b) A genuine god-consumer.** `reporting` (5,107 LoC, 2 entities) imports 8 peers over 74 edges,
reaching directly into their **repositories and entities** — `RiskRepository`, `Risk`, `KriDefinition`,
`KriMeasurement`, and equivalents in `compliance` and `change_mgmt`. `audit_committee` (1,581 LoC) imports
10 modules; `governance` imports 8.

Excluding the kernel, only **5** genuine peer cycles exist: `bcm↔tprm`, `compliance↔policy`,
`privacy↔tprm`, `reporting↔sust`, `scm↔tprm`.

### Decision

**Three rules.**

1. `module → platform` (kernel) is always permitted.
2. `module → module` only through a **port interface owned by the consumer**, with the adapter placed
   **outside both modules** (`integration/<consumer>/`), so neither module depends on the other — the
   dependency inverts onto an interface the consumer owns.
3. **No cycles** between non-kernel modules.

**Enforced as a ratchet, not a gate.** Record today's per-module edge counts; CI fails only on an
*increase*. This is the mechanism that took authorisation coverage from 62 to 21 in one programme: no
big-bang refactor, and the graph improves with every touch.

**Sequence — cheapest first.** Promote the export toolkit to `module/platform/export` before anything
else. It is a compiler-verified package move of six classes, with no behaviour or schema change, and it
removes all 25 inbound edges to `reporting`, deletes the apparent cycle, and moves `ai` to kernel-only
(separability Tier A). Roughly one day improves eight modules. Only then invert `reporting` with
`ReportDataPort` per source, then `audit_committee`, then `governance`.

### Alternatives

- **Big-bang boundary gate at zero violations:** rejected. 734 edges cannot be cut at once; the test would
  be permanently red and therefore ignored.
- **Java Module System / plugin classloaders:** deferred, per `ADR-001`, until package contracts are
  stable. Enforcement by test achieves the same discipline today at no runtime risk.
- **Extract `reporting` into a separate service over a read model:** the right long-term answer and a
  large one. The port inversion is step one toward it regardless, and the existing `DomainEvent`
  infrastructure makes an event-fed read model a natural follow-on.
- **Allow peer imports of DTOs but not entities/repositories:** a pragmatic middle rule worth considering
  as an interim ratchet target — it captures most of the benefit for much less work.

### Consequences

Ports add interfaces and files, and the toolkit promotion touches imports in eight modules in a single
commit — wide but shallow, with the compiler proving completeness. The ratchet form means the rule is
adoptable on day one without pausing feature work.

### Proof

Ratcheted boundary test green and monotonically decreasing; zero inbound edges to `reporting` after the
toolkit promotion; zero non-kernel cycles; `ChangeModuleBoundaryTest` (the CE-1 pilot) generalised to all
58 packages.

---

## Document control

This file is **generated**. Edit the sources under `TSS-GRC-Platform-Bible/` and re-run
`py TSS-GRC-Platform-Bible/Appendix/scripts/build_single_report.py`. The sources remain
authoritative; this consolidation exists so the assessment can be read, reviewed or sent as one
document.

The pre-existing 46-file reference corpus (product constitution, solution architecture, PDS,
business-process design, data architecture, state machines, integration and security architecture,
Saudi readiness, ADR-001…008, backlog) is **not** inlined here: much of it is superseded on
measured facts, and Part 7 records a per-document verdict on all of it. It stays under
`TSS-GRC-Platform-Bible/`, indexed by its own
[README](TSS-GRC-Platform-Bible/README.md).
