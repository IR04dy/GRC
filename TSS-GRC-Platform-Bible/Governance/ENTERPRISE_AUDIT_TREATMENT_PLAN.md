# Treatment Plan — Enterprise Table / Page / Process Completeness Audit

**Document type:** Governance disposition and remediation treatment plan
**Status:** `DECISION` — dispositions [`ENTERPRISE_TABLE_PAGE_PROCESS_COMPLETENESS_AUDIT_PLAN.md`](ENTERPRISE_TABLE_PAGE_PROCESS_COMPLETENESS_AUDIT_PLAN.md) v2.0 and its companion [`PLATFORM_CAPABILITY_REACHABILITY_AND_ORPHAN_DISCOVERY_PLAN.md`](PLATFORM_CAPABILITY_REACHABILITY_AND_ORPHAN_DISCOVERY_PLAN.md)
**Date:** 2026-08-31
**Validated at:** master HEAD `7baab0d9`; audit-plan baseline `cafcdf2d`
**Method:** first-hand re-derivation of every checkable claim (the programme's F2-221 rule) **plus** an independent, adversarial **Fable 5** second seat. The two assessments were produced separately and **converged** — the standard the `BIBLE_VALIDATION_LOG` sets ("two independent assessments converging is meaningful").

---

## 1. Verdict in one line

The v2.0 audit plan is **genuinely measured, not asserted** — its method and its four "keepers" are sound and match the platform's most productive finding veins. **Keep the method, execute the keepers as `F2-*` findings, and demote the 1,300-line body to a non-gating reference library.** It must **not** become a second "active standard" competing with the companion plan — that duplication is itself the defect the audit exists to find.

**Disposition: (c) EXECUTE the keepers as findings + a single bounded correction-and-commit pass on the document.** (Not *keep as a second standard*, not *merge* two 1,000-line documents, not *retire* validated method content.)

---

## 2. What was validated — converging evidence (first-hand + Fable 5)

| §0.1 baseline claim | Re-derived measurement | Verdict |
|---|---|---|
| 59 module packages | 59 (`module-registry.yaml`, `ModuleRegistryContractTest`) | ✅ **exact** |
| 419 entity files / 432 declarations | `^\s*@Entity\b` → 419 files / 432 occurrences | ✅ **exact** |
| 1,238 write endpoints / ~2,216 total | write-mapping annotations → 1,238 | ✅ **exact** (writes); total consistent-by-definition |
| 353 pages / 330 routes / 190 nav | 353 page `.tsx` / 331 `<Route>` (1 catch-all) / 190 Sidebar `to:` | ✅ **exact / ≈exact / exact** |
| 91 clients / 1,070 methods / 188 uncalled | `apiClientMethodReachability.test.ts:23` — verbatim | ✅ figures match; ⚠ quoted a **2026-08-08** artifact under a "rerun 08-31" heading |
| 407 clientless / 198 clientless guarded writes | `FrontendEndpointContractTest.java:124,159` — `407` / `198` | ✅ **exact** (read the *enforced constants*, not CLAUDE.md's stale 417/206 prose) |
| 471 register rows (399/30/16/10/10/5/1) | `TABLE_DISPOSITION_REGISTER.csv` + `TABLE_DISPOSITION.md` | ✅ **exact**, and honestly self-flagged "stale, dated 08-06" |
| §0.5 five "workflow holes" (DPIA create/update, IA plan transition, risk attestation, KRI breach closure, change-condition access) | `apiClientMethodReachability.test.ts` lines 106/111/136/140/143/292 — all `HOLE` | ✅ **5 of 5 live** |
| 4 zero-test modules incl. `dashboard`; "published baseline still says 7" | **Truth at HEAD is 3** (`audit_eng`, `controlmap`, `datagov`). `baseline_census.py:111` publishes `7`. | 🟠 **STALE + instrument defect** (see below) |
| "pyodbc absent — Gate 0 cannot run the DB census" | `pyodbc 5.3.0` present; ODBC Driver 17 is standing tooling | ⚠ **false on the audit machine** — the DB census is runnable *today* |

**Two demonstrable defects (both verified first-hand):**

1. **The zero-test cell went stale within hours of the baseline.** At `cafcdf2d`, `module/dashboard` had no test; the loop's own **F2-407** (`DashboardServiceTest`, merged `87a30ad9` the same afternoon) fixed it — the true count is **3**. This proves the plan's own thesis (the repo's F2-265 rule: *a count in prose is a copy of a measurement and it rots*), inside a document that then hand-types twelve such counts.
2. **A gated figure has no gate, and three artefacts hold three different numbers.** Re-derived by running the census itself (`--check`): `MEASURED_BASELINE.md`'s value cell says **8**, `baseline_census.py`'s `PUBLISHED` dict says **7**, the tree says **3** (`audit_eng`, `controlmap`, `datagov`). ⚠ **Correction to the Fable 5 review:** its "the census does not credit `opres`/`dataclass`/`reginspect`/`nca_ecc`/`dashboard`" claim is **REFUTED** — the census attributes all of them correctly (its own table shows `dashboard … tests 1`, i.e. F2-407's test). The attribution is sound; only the *published* figures are stale. (That is the register's measured ~2-in-10 rate for any new instrument's own claims — F2-188/F2-192/F2-204/F2-211 — and it is why a consultant's number is re-derived before it is acted on.) **The real defect is structural:** `MeasuredBaselineFreshnessTest` re-derives **11 of the 13 gated figures** from the tree, and compares those same 11 doc↔`PUBLISHED` — but `zero_test_packages` is in **neither** list, while `db_schemas_dev` is the only gated figure that legitimately cannot be derived hermetically. So `zero_test_packages` is **the one derivable gated figure with no gate at all**, which is why it drifted three ways while the build stayed green (F2-215: the mechanism is visibly present for 11 siblings, so the 12th reads as covered). **This is a measurement-integrity finding in its own right** and must be repaired before any keeper's gate is built on it.

**Structural issue — dual-lineage of the audit itself.** The ENTERPRISE plan (1,306 lines) and the companion ORPHAN plan (1,138 lines) cover the same territory — table/page/workflow reachability, classification, clean-tenant proof, canonical ownership, CI invariants. Two governing method documents over one subject is the **K-3 "two structures authoring the same fact"** pattern *at the level of the governance corpus*, and exactly what the `BIBLE_VALIDATION_LOG` warned ("reconcile the two roadmaps into one governing plan; two roadmaps is how the 24-vs-58 divergence happened").

**Over-reach — the v2.0 body re-imports what the companion's ratified §0 deliberately DROPPED:** §20.1's ten hand-maintained CSV registers (incl. two "Master" registers — the companion dropped these and ruled "one register, generated; nothing else is hand-maintained"); §13's 15-dimension weighted scorecard (dropped as "arbitrary weights, double-counted signals"); §17.3's emitted master graph (dropped: "machinery nobody queries"); §25's eighteen "100%" acceptance criteria (the companion replaced five of these as "the team-of-7 version"); §23.1/§23.2's eight roles + four standing councils. And §23 headlines **3–4 weeks** while its own work-package table sums to **4–6 weeks** — three sizings, none agreeing.

**Governance state:** the v2.0 rewrite was committed as `7baab0d9` ("Revalidate…") but is **unpushed** (`master` ahead 1 of origin) and **uncorrected** (its HEAD text still reads "4 zero-test modules incl. `dashboard`"). The keepers K-1…K-4 carry **no `F2-*` ids**, so their scope is unscheduled prose — the precise failure `UNIFIED_IMPLEMENTATION_PLAN.md`'s doc-map row for this file exists to prevent.

---

## 3. Disposition and rationale

**Execute the four keepers as `F2-*` findings; correct-and-commit the document as a non-gating *reference method*.**

- Not **(a) keep as a second canonical standard** — creates two governing method docs (the F2-177 two-copies shape; the dual-lineage measured above). A standard nobody executes is how this file got here.
- Not **(b) merge the two 1,000-line plans** — weeks of editorial work that yields zero findings and zero probes. The repo's proven absorption pattern is *scope → ids* (F2-68), not document fusion.
- Not **(d) retire** — that discards the one genuinely novel idea (the §0.4 expected-surface vocabulary) and §0.5's safeguards, all of which validated true.

---

## 4. Corrections required before the plan can claim authority (≤ 1 day, one commit)

1. **Push the correction, don't leave `7baab0d9` unpushed.** In the same commit:
2. **Replace every hand-typed figure in §0.1 with a pointer to its generated source** (`baseline_census.py --check`, the ratchet constants, `TABLE_DISPOSITION.md`, `target/*.txt`). A method standard should contain approximately zero copied numbers. Note zero-test = **3 at HEAD** and that **pyodbc is present** (Gate 0 is runnable).
3. **Fix the §23 sizing** to a single honest figure (**4–6 engineer-weeks**, per its own work-package table) or re-cut the table.
4. **Mark §§4–21 explicitly "reference method library — non-gating."**
5. **Cut §20.1 to "one generated table register + generated join outputs"** (the companion's ratified §0.7). Delete the two hand-maintained "Master" CSVs and §13's scorecard (or keep §13 only as a prioritisation aid). Keep §25.16–18 (population reconciliation, no-CRUD-for-integrity, name-your-producer); replace §25.1–15 with the single population-reconciliation sentence §23 already contains.
6. **Give K-1…K-4 real `F2-*` ids** (below), so the document carries no scope that lacks an id.
7. The corrected file must pass the governance markdown walker (**F2-408**) and `FindingsRegisterConsistencyTest`.

---

## 5. The residual work as findings — grounded in the worldwide-leader operating patterns

The keepers are the right work: each generalises a finding vein the loop has already proven live, and each maps **one-to-one** onto the operating pattern of a market leader the Bible already benchmarks (§4, §27). File each in the register's 18-field schema, queued behind the schema spine, verdicts left `NEEDS-VERDICT` where unread (never manufactured — F2-188).

| Proposed id | Keeper | Finding vein it counts (measured local evidence) | World-leader pattern to adopt (best practice) | User-friendly target |
|---|---|---|---|---|
| **F2-409** | Wave-0 measurement integrity | The census publishes 7 zero-test packages vs a path-truth of 3; the DB plane is ungenerated | AuditBoard/Archer **evidence lineage** — figures trace to a live source, not a copy | — (build-gate repair) |
| **F2-410** | **K-1 expected-surface join** | Create-side holes: `dpiaApi.create` (a DPIA can be approved/rejected/expired but never *created*), `consentApi.grant`, `complianceApi.createFinding` | **ServiceNow Policy-Exception / guided entry** — purpose, eligibility, duplicate check, ownership, workflow-on-submit; **no** parallel create button where the producer is intake/automation (the F2-208 CAPA rule) | A guided **wizard** on business decision points, not a bare register or CRUD form |
| **F2-411** | **K-2 state-transition reachability** | Stuck states (F2-196; F2-203's 19/46 `audit_lc`; F2-221's 11/30 ethics), duplicate status writers (F2-189/190/191), open-set inclusion lists (F2-343/359/364/367/402); **F2-407 proved the vein live the day of the baseline** | **ServiceNow states / Archer workflows** — named **commands not CRUD** (`Submit · Approve · Return · AcceptRisk · CloseFinding…`) with 409s that name the legal moves (the platform's F2-303/304 idiom) | Object **workspace** showing state, owner, next action; no status dropdown / entity-bound PUT |
| **F2-412** | **K-3 dual-lineage** | Three DSR registers (F2-196), two BCM plan registers (F2-234), two framework registers sharing an id space (F2-252), two screening registers (F2-288), three incident registers + five shadow tables (F2-301) — **and this treatment plan itself** (two audit documents) | **Archer / OneTrust shared trust data model** — one canonical owner per fact, legacy write-blocked, relations actionable not decorative | One authoritative object surface per concept; no duplicate "V2" write path |
| **F2-413** | **K-4 workflow-to-actor proof** | F2-178 (0/31 committee seats resolvable in prod), the F2-253→261 zero-holder-role lineage, companion F-4 (`workflow.requests`: 1 row dev / 0 prod) | **ServiceNow Risk Workspace / My Work** — definition → publisher → instance → **eligible populated actor** → task → decision → evidence, proved in a clean tenant | Tasks surface in a persona **My Work** inbox; `admin` proves nothing |

**Remediation-to-closure spine (MetricStream audit lifecycle).** K-2's output feeds §14.5: `kriApi.closeBreach` ("ACKNOWLEDGED and never closed") and `iaApi.transitionPlan` ("cannot be advanced") are closure-spine holes — every negative outcome must reach an **owned, verified closure with evidence**, the way MetricStream runs finding → action → validation → sign-off.

**Drill-through KPIs (AuditBoard/Archer).** The existing `kpiProvenance` / `pageDataProvenance` / `exportProvenance` gates and the F2-406/407 KPI corrections are this pattern already in motion; §14.7 (read-only derived facts with formula + drill-through) is the target form — extend it, don't rebuild it.

---

## 6. User-friendliness and Arabic-first as **gated** acceptance, not an afterthought

Carry §16.2 / §12.1 into the **finding schema itself**, as columns in the generated page join — so they are evidence, not aspiration. A page disposition may not read `KEEP` unless:

- every K-2 transition surfaced in the UI has **bilingual, non-raw-token labels** (the F2-359 / F2-340 lineage — raw English status tokens on an Arabic-primary UI are a recurring *measured* defect; house rule 7);
- **empty / denied / error states are truthful in both languages** (never a success toast before server confirmation; a demo/empty distinction);
- **RTL and WCAG 2.2 AA** are recorded per page (keyboard, focus, screen-reader labels, non-colour status cues, Arabic font legibility);
- the **default target for a complex object is a task-centred workspace** (state, owner, next action, evidence, timeline — ServiceNow/AuditBoard style), **not** a raw register — consistent with the existing `UX_REDESIGN_BLUEPRINT` workspaces.

TSS's differentiation is **Arabic-first Saudi regulatory evidence, strong SoD and transparent provenance** — not a larger count of tables or menu items.

---

## 7. First three actions

1. **Wave-0 measurement integrity (≤ 1 day, file as F2-409).** Give `zero_test_packages` the gate its 11 sibling figures already have — a tree derivation in `MeasuredBaselineFreshnessTest` mirroring the census's own attribution, plus an entry in the doc↔`PUBLISHED` agreement map — and correct all three artefacts to the derived **3**. (The attribution needs no repair; it is already correct.) Then regenerate `table_xref.py` against **both** databases (pyodbc 5.3.0 is present, so the plan's stated Gate-0 blocker is void here). Converts the plan's only "cannot" into a "did" and unblocks K-1. *Measurement integrity before classification — the plan's own Wave-0 doctrine.*
2. **Correct-and-commit the audit plan (≤ 1 day)** per §4, and file K-1…K-4 as F2-410…F2-413 in the 18-field schema. From that commit the document carries **no scope without an id**.
3. **Run K-1 as the pilot (3–5 days)** on §28's archetype-diverse set (change request, committee vote/attendance, evidence/signature, `privacy` vs `privacy_lc`, automation outbox, a dashboard source). The pilot passes **only if it recommends *no editable UI*** for the immutable/technical objects — proof the method assesses process maturity rather than generating CRUD.

---

## 8. Risks and guardrails (from the register's measured history)

- **Never restate a figure a generator owns** — link the artifact; the zero-test cell rotted in ~4 hours. Any exceptions file is validated **both ways** (unknown → `UNKNOWN`; exception naming a vanished object → error).
- **Run every data census against both databases** — prod trails by dozens of migrations and differs by *absence* (F2-259); a dev-only figure "describes a laptop."
- **`admin` proves nothing** — `PLATFORM_ADMIN` bypasses `hasPermission`; role guards get no bypass; several recited roles have zero holders (F2-253 lineage). Every runtime proof needs an intended persona **and** a denied control persona — and a role-derived value probed as `admin` *fails toward agreeing with you* (F2-192).
- **Measure from bytecode/reflection, never a source regex**; strip comments in every scan; an under-count reads exactly like a clean tree; anti-vacuity floors sit above the largest **wrong** answer (F2-215).
- **Destructive dispositions are owner decisions, never audit side effects** — `RETIRE`, the 10 `ORPHAN_CONFIRMED` tables, F-5's ~20 seed families, and collapsing synonym status vocabularies that rewrite live rows in two databases (F2-183/196/203). House rule 6; `[REVIEW-TEST]` + `CHARINDEX`; plant your own probe victims; sweep **both** DBs for residue.
- **Do not manufacture verdicts** — ship keeper outputs as work lists with `NEEDS-VERDICT`; ~2 in 10 of any new instrument's own claims fall on first contact (F2-188/192/204/211).
- **K-2 finds, it does not sweep-fix** — a state with no incoming edge may be a seed-era synonym on a CHECK-less column; adding the CHECK before closing the writers converts silent junk into constraint violations (F2-183).
- **One governing lineage** — after the correction commit, `UNIFIED_IMPLEMENTATION_PLAN.md`'s doc-map is the sole arbiter of what the audit plan *is*; any future rewrite that re-grows scheduling, sizing or acceptance criteria inside it fails review on that ground alone.

---

*Prepared by first-hand re-derivation (F2-221) and an independent Fable 5 adversarial review; both converged on every checkable claim. This treatment plan is deliberately short and points at generated sources, tests and `F2-*` findings rather than restating them — it is not a third audit apparatus.*
