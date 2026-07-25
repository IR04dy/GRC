# Independent Acceptance Verdict

**Date:** 2026-07-25 · **Tree:** `master` `99a719da` · **Basis:** `Appendix/MEASURED_BASELINE.md`,
`Registers/FINDINGS_REGISTER_V2.md`, `Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md`,
`UX/UX_REDESIGN_BLUEPRINT.md`.

**Standpoint.** Written as if I had never seen this codebase before and had no stake in it. Three hats,
answered separately because they reach different verdicts: **government acceptance committee**,
**enterprise customer**, **investor technical due diligence**.

---

## 1. The one-paragraph verdict

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

## 2. Government acceptance committee

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

## 3. Enterprise customer (Saudi bank / government entity)

| Question | Answer today |
|---|---|
| Can I buy only the modules I need? | **Not yet — but the mechanism is built.** A full licensing subsystem exists (`organisation_licenses` with tier, seats and validity; a fail-closed AOP guard; an admin screen) and is applied to **22 of 255 controllers, all in one module**. Dev tenants 2 and 3 are licensed for `SOCOPS` alone and can use all 58 modules. This is a rollout of days, not a build. |
| Can I define my own roles and grant granular permissions? | **For 37 of 58 modules.** The other 21 compare hard-coded role *names*, so a custom role granted `SHARIA_WRITE` still gets 403. Least privilege is unachievable there. |
| Can my DBA see clean data ownership? | **Mostly.** 50 schemas, one per module — except nine, of which `incident` writing into the `cyber` schema is the one that will be questioned. |
| Will my users find the features I paid for? | **Probably not all.** ~144 of 328 screens are reachable only by typing a URL. |
| What are the performance limits? | **Unknown.** No load test, no capacity model, no p95 targets, for 2,160 endpoints. |
| Is it stable in production? | **A known daily failure is live.** The break-glass sweep defect (33–35 failures/day) is fixed, built and staged — and the service has not been restarted. |
| Can I get my data out? | **Yes** — strong export coverage (PDF/XLSX/DOCX/PPTX, RTL Arabic) across 22 generators. Genuine strength. |

**Customer verdict: buyable for a suite deployment; not yet buyable module-by-module.**

---

## 4. Investor technical due diligence

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

## 5. The four non-negotiable conditions

Everything else can be scheduled. These four cannot ship as they are:

### Condition 1 — Committee decisions must be attributable *(2 days)*
`recordVote` must derive the voter from `Authentication`; a body-supplied `memberName` is honoured only
under an explicit secretary/chair authority and is then stamped `recorded_by … on_behalf_of …`; a name
that is not a registered member of that committee is rejected unconditionally; recusal is evaluated
against the resolved identity. Existing rows get `recorded_by_user_id NULL` = *provenance unknown
(pre-fix)* — **visible, not back-filled with a guess**. **In a GRC product, an unattributable approval
is not an approval.** This is the cheapest high-severity fix available and it comes first.

### Condition 2 — No fabricated data on any surface *(days)*
De-mock `lib/searchIndex.ts`, `pages/dashboard/widgets.tsx`, `lib/boardPack.ts`; add the CI rule
(no `/mock/i` import outside tests) and the backend-down acceptance test. **A governance product may not
invent records.** Non-negotiable regardless of commercial model.

### Condition 3 — Prove the differentiators *(one quarter, parallelisable)*
Tier-1 test floors on `outsourcing`, `nca_ecc`, `sharia`, `data_officer`, `openbanking`, `dataclass`,
`rrp` — every state transition and authorisation branch, plus one live HTTP probe per write endpoint.
**Nothing regulator-facing may be sold on the promise that it works.**

### Condition 4 — Say what a customer bought *(days — the subsystem already exists)*
Extend `KNOWN_MODULES` to all 58 codes, apply the existing `@RequireModule` at class level across business controllers, wire the existing `moduleEnabled()` FE helper into navigation, and backfill licence rows before enforcing.
**Required if — and only if — per-module sale is the commercial model.** If it is not, say so explicitly
and F2-04/F2-05/F2-06 drop from blockers to hygiene. **This is the one decision this review cannot make
for the business.**

---

## 6. Scorecard

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
| Performance / scalability | **2/10** | No evidence of any kind |
| Documentation | **6/10** | Extensive and now partly generated — but has asserted wrong numbers, including in its own plans |

**Weighted overall: 5.3 / 10 — "strong engineering, unpackaged product, one unenforceable core control."**

The gap between the domain score (8) and the packaging score (2) *is* the finding. The hard part —
building 186 kLoC of Saudi GRC domain logic — is done. The easy part — declaring what exists, proving
it works, and stating what a customer bought — is not. That is a far better position than the reverse,
and it is unusual enough to be worth saying plainly: **the remaining work is mostly additive, and almost
none of it requires undoing what has been built.**

---

## 7. What I would do in the first 30 days

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
