# Measured Baseline — the evidence base for this Bible

**Assessor:** independent review (Claude), acting as Enterprise Architect / CPO / Security Architect /
UX Principal / GRC SME / acceptance committee.
**Measured:** **2026-08-01**, working tree `D:\AYMAN\GRC` @ `master` `6516b007`
(first measured 2026-07-25 @ `99a719da`; re-measured in full by **F2-69**).
**Method:** static measurement of the actual source tree — not documentation, not interviews —
plus both live databases (`TSS_GRC` dev, `TSS_GRC_Deployment` prod).
**Reproduce:** **`py Appendix/scripts/baseline_census.py`** — one command, every figure below.

> **Standing rule for this Bible.** No claim enters any document in this repository unless it is
> (a) reproducible from this baseline, (b) a live probe result with the command shown, or (c) explicitly
> labelled **OPINION** / **ASSUMPTION**. Every finding in `Registers/FINDINGS_REGISTER_V2.md` cites a
> row here. Documents that assert numbers without provenance are flagged in
> `Governance/BIBLE_VALIDATION_LOG.md`.

> ⚠ **This page broke its own rule, and F2-69 is the audit of it.** For six days it was the only
> document in the repository whose numbers nothing re-measured. Three of them had **never** been
> reproducible at the commit this header names; one was measured by a **substring** test that counted
> `@EntityListeners` as an entity; one — the deployed Flyway baseline — was **wrong in the direction
> that matters**, because the baseline decides which migrations can never run. §0.1 records exactly
> which figures went stale, which were never true, and which reproduce to the digit. A measurement
> document with no checker is a claim like any other.

---

## 0. Headline totals

Measured **2026-08-01** @ `6516b007`. The 2026-07-25 column is kept because the *delta* is the point:
a week of remediation moves these numbers, and a page that does not say when it was taken is a page
that will be read as current forever.

| Metric | Measured (08-01) | 07-25 | Where the platform's own docs say otherwise |
|---|---:|---:|---|
| Backend module packages | **59** (57 with code + 1 empty + `change_adapters`, added by S5/CE-1) | 58 | `CLAUDE.md` now lists all 58 ✅ (it said **24** until F2-19). ⚠ The 59th is deliberate and needed an ADR-018 homing decision, not just a directory — `SkuRegistry.requiredSkuForPackage` THROWS for an unhomed `module.*` package |
| Module Java LoC | **192,113** | 186,285 | — |
| Files declaring a JPA entity | **414** | *pub. 417* | ⚠ see §0.1 — 417 was a substring count |
| JPA entity **classes** declared | **427** | — | 13 of them live in one file (`data_officer`) |
| REST endpoint mappings | **2,168** | 2,160 | `CLAUDE.md` says 2,160 — now 8 short |
| Write endpoints (`POST/PUT/PATCH/DELETE`) | **1,219** | *pub. 1,151* | ⚠ see §0.1 — 1,151 reproduces under no rule |
| `@PreAuthorize` annotations (comment-stripped) | **2,156** | *pub. 2,268* | ⚠ see §0.1 — never reproducible |
| Distinct permission modules used by guards | **31** | 27 | matches `CLAUDE.md` "31 modules / 114 permissions" ✅ |
| Module packages with **zero** `hasPermission` usage | **17 of 59** | 21 | F2-05 converted 5 (`fraud`, `integrations`, `knowledge`, `board_pack`, `socops`); the 17th is `change_adapters`, which is **correct** — adapters carry no endpoint, so a guard there would be a second unguarded door (`ChangeModuleBoundaryTest` asserts they hold no `@RestController`) |
| Tests attributed to modules | **2,669** (suite total **2,797**) | 2,339 / 2,415 | `CLAUDE.md` says "~2,700 tests" ✅ |
| Module packages with **zero tests** | **15**, totalling **18,011 LoC (9.4%)** | 19 / 20,503 / 11.0% | — |
| Distinct **JPA** schemas | **50** | 50 | matches `CLAUDE.md` "50+" ✅ |
| **Database** schemas holding tables (dev & prod) | **51** | *not measured* | the 51st is `audit`, which JPA does not know — §7 |
| Flyway migrations on disk | **339** files, `V1`–`V351`, 4.36 MB | 304 / `V1`–`V314` | next free **V352** |
| **Deployed** Flyway baseline / head | **baseline `V292`**, head **`V336`**, **8 pending** | *pub. "baseline V312"* | ⚠ see §0.1 — load-bearing and wrong |
| Frontend pages (`.tsx` under `pages/`) | **336** in **114** folders | 329 / 114 | S5c split the 1,036-line `ChangesPage` into `pages/change/` — +7 files, same folder count |
| Routes registered in `App.tsx` | **328** | 328 | — |
| Sidebar navigation entries | **193** | 184 | — |
| Mock-data import statements | **30** in **26** files | 44 / 29 | W2-1 removed 14 |
| Mock data files still shipped | **15**, **3,550 lines** | 15 / *pub. 3,650* | ⚠ see §0.1 |
| Unguarded write endpoints (enforced ratchet) | **≤ 21** (`ControllerAuthorizationRatchetTest`) | 21 | ⚠ `CLAUDE.md` house rule says "**≤ 62**" — the enforced constant is **21** |

**Corrections to numbers this project has previously published, including by me:**

| Claim | Published as | Measured | Consequence |
|---|---|---|---|
| Backend modules | 24 (`CLAUDE.md`, architecture pack) | **58** packages | Scope statements, licensing and test-bar plans were sized against 40% of the codebase |
| FE mock import sites | 53 (`REMEDIATION_MASTER_PLAN.md`, `UNIFIED_IMPLEMENTATION_PLAN.md` W2-1) | **44** statements / 29 files *(07-25)*, **30 / 26** now | W2-* work items were over-scoped; the *concentration* matters more than the count (see §5) |
| `ck_change_status` existing values | "13 existing statuses" (`UNIFIED_IMPLEMENTATION_PLAN.md` X-2) | **14** (`V300` lines 13–16) | Target of 17 is still correct (14 + 3 new); the arithmetic in the plan was off by one |
| Authorisation ratchet | "≤ **62**" (`CLAUDE.md` house rule 3) | **21** (`BASELINE_UNGUARDED_WRITE_ENDPOINTS`) | A house rule quoting a ceiling 3× looser than the enforced one invites a reviewer to wave through 41 unguarded endpoints the build would in fact reject |

---

## 0.1 What went stale, what was never true, and how the difference was established

F2-69 filed this page as "three numbers wrong or stale". Measured, it is **six**, in three distinct
classes — and separating them needed a method, not an opinion. The census was run twice: against
today's tree, and against **an export of `99a719da`, the commit this page's own header names**
(`baseline_census.py --root <exported tree>`).

**17 of the 20 published figures reproduce at that commit to the digit** — module LoC to the unit
(186,285), endpoints (2,160), pages (329), routes (328), nav (184), mock statements (44), suite tests
(2,415), zero-test packages (19), and every per-module row in §1 (`bcm` 25,130 / 50 / 261 / 139 / 395 /
7 out / 88 edges / 2 in — identical). That is what makes the three misses *findings*: the method
reproduces, so a figure that does not reproduce is a defect in the document, not in the measurement.

| Class | Figure | Published | Reality | Why it matters |
|---|---|---:|---|---|
| **Never reproducible** | `@PreAuthorize` count | 2,268 | **2,148** at `99a719da`; 2,156 today. Tried module-only (2,148), whole-`main` raw (2,159), `main`+`test` raw (2,185), controllers-only (2,153) — **no rule yields 2,268** | The drift table would have read "−112, a week of change". It was never 2,268 to begin with |
| **Never reproducible** | Write endpoints | 1,151 | **1,216** at `99a719da` under *every* reading — module/, whole-`main`, controllers-only, raw or comment-stripped, all exactly 1,216 | Under-states the write surface the authorisation ratchet has to cover by 65 endpoints |
| **Never reproducible** | Mock file lines | 3,650 | **3,550** across the same 15 files (3,535 if you count newlines rather than lines) | Small, but it is the one figure in §5 nobody could have re-derived |
| **Wrong method** | "JPA entities: 417" | 417 | **413** files declare an entity; **426** entity classes are declared. 417 is the count of files containing the *substring* `@Entity` | See below — it is a substring bug and a units bug at once |
| **Load-bearing error** | Deployed Flyway baseline | "prod runs Flyway from baseline **V312**" | **V292**. `V312` was merely the migration head the day the line was written | The baseline decides which migrations can **never** run. Believing 312 hides 20 versions' worth of "this never executed here" |
| **Stale** | Migrations on disk | 304 files, `V1`–`V314` | **332** files, `V1`–`V344` | Stale by 28 files / 30 versions — six days |

### Why 417 was wrong twice over

`coupling.py` counted entities as `if "@Entity" in src: entities += 1`. A substring test over **raw**
source, incremented **per file**. Both halves are wrong:

- **Substring.** `@EntityListeners` and `@EntityGraph` contain `@Entity`, and Javadoc prose containing
  `@Entity` counts too. Four files in the module tree match with no entity in them —
  `iam/repository/UserRepository.java` (`@EntityGraph`), `risk_lc/package-info.java`, and two SCM DTO
  files (prose). This is **RC-20, the regex-over-Javadoc failure, inside the document whose own rule is
  that nothing enters unmeasured.** *(F2-69 also named `common/model/BaseEntity.java` /
  `@EntityListeners` as a fifth phantom. It is a real phantom but it sits **outside** `module/`, which
  is the census scope, so it never contributed to 417 — the row conflated two scopes.)*
- **Units.** One `@Entity` per file is not one entity per file. `data_officer/entity/Entities.java`
  declares **14** entity classes in a single file, so the module census shows `data_officer` with
  **1** entity — a 14× understatement of the module that §6 flags as *"PDPL data-officer workspace,
  37 endpoints, zero tests"*. **The undercount made the platform's largest untested regulatory
  surface look like a thin wrapper.**

Both readings are now published side by side (413 files / 426 classes), because the honest answer to
"how many entities" depends on which question is being asked, and the per-module `Ent` column says
which one it is counting.

### The fix is a checker, not a correction

Correcting numbers by hand is what produced this page twice. `baseline_census.py` holds the published
figures in a `PUBLISHED` dict beside the code that measures them, and
`MeasuredBaselineFreshnessTest` (backend suite) fails the build when a load-bearing figure drifts —
entity files, module packages, JPA schemas, migration count and head, permission modules,
`hasPermission`-less packages, zero-test packages, FE pages and routes. Fast-moving figures (LoC,
test counts, edges) are **reported but not gated**: a ratchet that fires on every commit is a ratchet
someone disables, which is the lesson F2-70 paid for.

---

## 1. Module census — the full 58

Sorted by LoC. `out` = distinct peer modules imported; `edges` = import statements to peers;
`in` = distinct peer modules importing this one. `perm` = permission modules named in
`hasPermission(...)`; **`ROLE`** = guards use hard-coded `hasAnyRole(...)` role lists instead.

| # | Module | LoC | Ent | Endpts | Writes | Tests | out | edges | in | Auth style | FE | Schema |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---|:--:|---|
| 1 | `bcm` | 25,305 | 50 | 261 | 139 | 400 | 6 | 125 | 2 | BCM | ✅ | `bcm` |
| 2 | `sust` | 16,776 | 34 | 226 | 129 | 200 | 5 | 76 | 2 | SUST | — | `sust` |
| 3 | `platform` | 12,904 | 24 | 125 | 61 | 246 | 9 | 34 | 50 | multi | ✅ | `platform` |
| 4 | `risk` | 8,983 | 23 | 101 | 65 | 147 | 5 | 42 | 8 | RISK | ✅ | `risk` |
| 5 | `ndi` | 7,863 | 15 | 127 | 82 | 19 | 2 | 21 | 1 | NDI | ✅ | `ndi` |
| 6 | `integrations` | 7,654 | 6 | 24 | 12 | 127 | 3 | 21 | 0 | CYBER,INTEGRATIONS | — | ⚠ `platform` |
| 7 | `cyber` | 7,285 | 16 | 98 | 61 | 117 | 4 | 36 | 3 | CYBER | ✅ | `cyber` |
| 8 | `socops` | 6,799 | 25 | 111 | 58 | 36 | 3 | 73 | 1 | CYBER | ✅ | `socops` |
| 9 | `admin` | 6,116 | 14 | 62 | 28 | 165 | 3 | 29 | 5 | ADMIN | ✅ | `admin` |
| 10 | `privacy` | 5,947 | 16 | 99 | 62 | 84 | 2 | 24 | 3 | CYBER,PRIVACY | ✅ | `privacy` |
| 11 | `compliance` | 5,723 | 11 | 81 | 41 | 107 | 5 | 35 | 4 | multi | ✅ | `compliance` |
| 12 | `tprm` | 5,527 | 12 | 70 | 43 | 115 | 5 | 24 | 5 | TPRM | ✅ | `tprm` |
| 13 | `workflow` | 5,266 | 14 | 44 | 26 | 114 | 4 | 26 | 1 | multi | ✅ | `workflow` |
| 14 | `ethics` | 4,960 | 14 | 72 | 44 | 92 | 1 | 15 | 1 | COMPLIANCE | ✅ | `ethics` |
| 15 | `reporting` | 4,193 | 2 | 28 | 6 | 26 | 8 | 85 | 0 | multi | ✅ | `reporting` |
| 16 | `iam` | 4,140 | 9 | 37 | 23 | 55 | 1 | 21 | 10 | IAM | — | `iam` |
| 17 | `ai` | 3,821 | 8 | 35 | 20 | 51 | 2 | 19 | 1 | AI | ✅ | `ai`,`ai_governance` |
| 18 | `scm` | 3,818 | 12 | 66 | 45 | 15 | 2 | 7 | 1 | SCM | ✅ | `scm` |
| 19 | `evidence` | 3,683 | 11 | 37 | 19 | 21 | 2 | 12 | 7 | EVIDENCE | ✅ | `evidence` |
| 20 | `audit_lc` | 3,194 | 8 | 50 | 32 | 62 | 1 | 10 | 2 | AUDIT | — | `audit_lc` |
| 21 | `data_officer` | 2,375 | 1 | 37 | 21 | **0** | 1 | 2 | 0 | PRIVACY | ✅ | `data_officer` |
| 22 | `change_mgmt` | 2,290 | 7 | 30 | 15 | 56 | 3 | 13 | 1 | CHANGE | — | `change_mgmt` |
| 23 | `sharia` | 2,236 | 6 | 21 | 12 | **0** | 1 | 3 | 0 | **ROLE** | ✅ | `sharia` |
| 24 | `aml` | 2,167 | 4 | 14 | 7 | 47 | 1 | 7 | 0 | **ROLE** | — | `aml` |
| 25 | `ccm` | 2,033 | 2 | 10 | 5 | 39 | 1 | 5 | 2 | **ROLE** | ✅ | `ccm` |
| 26 | `openbanking` | 1,915 | 5 | 18 | 9 | **0** | 1 | 3 | 0 | **ROLE** | ✅ | `openbanking` |
| 27 | `rrp` | 1,912 | 5 | 17 | 10 | **0** | 1 | 3 | 0 | **ROLE** | ✅ | `rrp` |
| 28 | `governance` | 1,857 | 1 | 7 | 1 | 43 | 8 | 32 | 1 | **ROLE** | ✅ | ⚠ `platform` |
| 29 | `policy` | 1,778 | 5 | 27 | 17 | 39 | 4 | 15 | 5 | COMPLIANCE | ✅ | `policy` |
| 30 | `cpty` | 1,690 | 5 | 14 | 6 | **0** | 1 | 3 | 0 | **ROLE** | ✅ | `cpty` |
| 31 | `outsourcing` | 1,657 | 5 | 17 | 9 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `outsourcing` |
| 32 | `regreporting` | 1,630 | 3 | 17 | 9 | **0** | 1 | 3 | 0 | **ROLE** | ✅ | `regreporting` |
| 33 | `incident` | 1,589 | 2 | 22 | 14 | 31 | 2 | 9 | 5 | CAPA,INCIDENT | — | ⚠ `cyber` |
| 34 | `audit_committee` | 1,583 | 2 | 7 | 4 | 26 | 10 | 30 | 0 | **ROLE** | ✅ | `audit_committee` |
| 35 | `consumer` | 1,422 | 3 | 11 | 6 | 20 | 1 | 3 | 0 | **ROLE** | ✅ | `consumer` |
| 36 | `fraud` | 1,327 | 3 | 10 | 5 | 4 | 1 | 3 | 0 | FRAUD | ✅ | `fraud` |
| 37 | `reginspect` | 1,162 | 3 | 10 | 5 | **0** | 1 | 3 | 0 | **ROLE** | ✅ | `reginspect` |
| 38 | `dataclass` | 990 | 4 | 13 | 7 | **0** | 1 | 2 | 0 | **ROLE** | ✅ | `dataclass` |
| 39 | `opres` | 952 | 3 | 9 | 6 | **0** | 1 | 3 | 0 | **ROLE** | ✅ | `opres` |
| 40 | `attachment` | 949 | 1 | 7 | 4 | 15 | 0 | 0 | 2 | EVIDENCE | — | ⚠ `platform` |
| 41 | `auth` | 902 | 1 | 7 | 6 | 22 | 2 | 4 | 0 | **ROLE** | — | ⚠ `platform` |
| 42 | `signature` | 811 | 2 | 10 | 5 | 11 | 1 | 2 | 1 | SIGNATURE | — | ⚠ `platform`,`signature` |
| 43 | `intake` | 662 | 2 | 4 | 2 | 3 | 0 | 0 | 1 | INTAKE | ✅ | `intake` |
| 44 | `risk_lc` | 611 | 2 | 5 | 3 | 6 | 1 | 3 | 1 | RISK | — | `risk_lc` |
| 45 | `cyber_lc` | 532 | 1 | 5 | 3 | 11 | 1 | 3 | 0 | CYBER | — | `cyber_lc` |
| 46 | `capa` | 512 | 1 | 6 | 3 | 12 | 1 | 2 | 6 | CAPA | ✅ | `capa` |
| 47 | `workflow_submission` | 494 | 1 | 6 | 3 | 35 | 1 | 2 | 0 | WORKFLOW | — | ⚠ `workflow` |
| 48 | `approvals` | 472 | 1 | 5 | 3 | 12 | 1 | 2 | 0 | WORKFLOW | ✅ | `approvals` |
| 49 | `board_pack` | 449 | 1 | 5 | 3 | 11 | 1 | 1 | 0 | ADMIN | ✅ | `board_pack` |
| 50 | `regchange` | 448 | 1 | 5 | 3 | 6 | 1 | 3 | 0 | COMPLIANCE | — | `regchange` |
| 51 | `knowledge` | 442 | 1 | 6 | 4 | 5 | 0 | 0 | 0 | KB | — | ⚠ `kb` |
| 52 | `privacy_lc` | 420 | 1 | 5 | 3 | 10 | 1 | 3 | 2 | PRIVACY | — | `privacy_lc` |
| 53 | `bcm_lc` | 395 | 1 | 5 | 3 | 6 | 1 | 1 | 0 | BCM | — | `bcm_lc` |
| 54 | `controlmap` | 384 | 1 | 3 | 1 | **0** | 1 | 7 | 0 | COMPLIANCE | — | ⚠ `evidence` |
| 55 | `nca_ecc` | 370 | 1 | 6 | 3 | **0** | 1 | 1 | 0 | COMPLIANCE,CYBER | ✅ | `nca_ecc` |
| 56 | `dashboard` | 362 | 0 | 8 | 0 | **0** | 5 | 9 | 0 | multi | ✅ | — |
| 57 | `audit_eng` | 358 | 1 | 5 | 3 | **0** | 1 | 1 | 0 | AUDIT | — | `audit_eng` |
| 58 | `datagov` | 18 | 0 | 0 | 0 | **0** | 0 | 0 | 0 | **ROLE** | — | — |

### 1.1 `datagov` is an empty module
`module/datagov/` contains exactly one file — `package-info.java`, 18 lines, no code. `CLAUDE.md`
names `datagov` in its module list. **A named module with zero implementation is in the platform's
own documentation.** (Finding F2-01.)

---

## 2. Coupling — who reaches into whom

### 2.1 The fan-in ranking (a proxy for "what can never be sold alone")
| Module | Distinct peers depending on it | Reading |
|---|---:|---|
| `platform` | **50** (was 45) | The kernel. Correct and expected — this *is* the shared core. |
| `iam` | 10 (was 9) | Identity is a kernel concern behaving like a module. |
| `risk` | 8 | Risk is the platform's semantic centre — everything scores against it. |
| `evidence` | 7 | Evidence is a kernel concern behaving like a module. |
| `capa` | 6 | Corrective action is cross-cutting by design. |
| `incident`, `policy`, `admin`, `tprm` | 5 each | — |
| `reporting` | **0** (was 8) | ⬇ Nothing imports `reporting` any more — see 2.2. |

### 2.2 The structural coupling defects — one of the four has resolved
| Module | LoC | Reaches into | Why it is a defect |
|---|---:|---|---|
| `reporting` | 4,193 | `platform`×37, `risk`×16, `change_mgmt`×11, `compliance`×11, `policy`×4, `evidence`×3, `ethics`×2, `sust`×1 — **8 modules, 85 edges, only 2 entities** | A god-consumer that imports peers' **entities and services** directly. Any module sold without `reporting` loses its board pack; `reporting` sold alone compiles against 8 modules. Its edge count **rose 74 → 85** as F2-42 replaced the fabricated board pack with a real aggregate over live repositories — the right fix for provenance, and it deepened this coupling. |
| `audit_committee` | 1,583 | `compliance`×6, `sust`×4, `tprm`×4, `platform`×3, `ai`×3, `capa`×2, `ccm`×2, `governance`×2, `incident`×2, `risk`×2 — **10 modules, 30 edges** | Highest fan-out-per-LoC on the platform: 1.6 kLoC that cannot compile without ten peers. |
| `governance` | 1,857 | `platform`×10, `audit_lc`×4, `capa`×4, `ccm`×4, `risk`×4, `iam`×3, `policy`×2, `privacy_lc`×1 — **8 modules, 32 edges** | Same shape, and it stores into the **`platform` schema** (§3). |
| `socops` | 6,799 | `platform`×68, `incident`×3, `cyber`×2 — **3 modules** | ✅ **Resolved.** The inverted `reporting`×3 edge (a producer importing a consumer) is **gone** — `socops` now imports `module.reporting` **zero** times. Kernel-heavy is fine, and this is now only kernel-heavy. |

---

## 3. Nine modules persist into a schema they do not own

`@Table(schema = …)` measured across all `@Entity` classes. **Re-measured 2026-08-01: unchanged** —
same nine modules, same schemas, same entity counts. Six days of remediation touched none of it, which
is worth stating: this is the one section of this page that did not move.

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

## 4. Authorisation coverage — guarded, but not *delegatably* guarded

- **31** distinct permission modules are referenced by `hasPermission(MODULE, ACTION)` guards
  (was 27 — F2-05 added `FRAUD`, `INTEGRATIONS`, `KB`, and `socops` moved onto `CYBER`).
- **17 of 59** module packages contain **no `hasPermission` call at all** (was 21). They are *not*
  unguarded — they use hard-coded `hasAnyRole('ROLE_A','ROLE_B',…)` lists:

  `sharia` · `aml` · `ccm` · `openbanking` · `rrp` · `governance` · `cpty` · `outsourcing` ·
  `regreporting` · `audit_committee` · `consumer` · `reginspect` · `dataclass` · `opres` · `auth` ·
  `datagov` *(which has no code at all, so 15 with code)*

  | Module | Guard as written |
  |---|---|
  | `sharia` | `hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER','INTERNAL_AUDITOR','CRO','SHARIA_OFFICER','SHARIA_AUDITOR')` (read) |
  | `outsourcing` | `hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','CIO')` |
  | `audit_committee` | `hasAnyRole('AUDIT_COMMITTEE_CHAIR','COMPANY_SECRETARY','PLATFORM_ADMIN')` |
  | ~~`socops`~~ | ✅ **converted** — now `hasPermission('CYBER',…)` across 111 endpoints |
  | ~~`knowledge`~~ | ✅ **converted** — now `hasPermission('KB','WRITE')`… but `KB` is **not in `iam.permissions`**, so nobody but PLATFORM_ADMIN can satisfy it (**F2-53**, open) |

> ⚠ **Measurement landmine, found re-measuring this section.** A grep for `hasAnyRole('…')` finds
> **nothing** in `sharia` — its guards are `@PreAuthorize(READ_GUARD)` where `READ_GUARD` is a
> `private static final String` assembled from **two concatenated literals across a line break**. Any
> single-line regex census of guard style silently reports such a module as having no role guard. The
> classification above is therefore made **negatively** — a package is ROLE-guarded when it has zero
> `hasPermission(` calls of any shape — and that predicate was verified twice: the `(MODULE,ACTION)`
> pair regex finds 1,369 guards, a bare `hasPermission(` count finds 1,372, and **the only file where
> those disagree is `ModulePermissionEvaluator` itself**, which implements the call rather than using it.
> None of the 16 packages contains a `hasPermission(` in any form.

**Why this is a commercial defect, not a style preference.** The platform ships a per-tenant RBAC
system (55-role persona matrix, `V272`) whose entire value proposition is that a customer can define
their own roles and grant permissions to them. For these 16 packages that system **has no effect**:
a tenant who creates a role "Sharia Reviewer" and grants it `SHARIA_WRITE` still gets **403**,
because the code compares role *names*. The only way in is to hand the user a built-in platform role.
Sold as a standalone module, such a module is un-administrable by its buyer.

**This does not contradict the authorisation ratchet (`BASELINE_UNGUARDED_WRITE_ENDPOINTS = 21`).**
The ratchet counts endpoints with *no* guard of either kind; `hasAnyRole` satisfies it. The ratchet
measures **coverage**; this section measures **tightness**. Both are needed, and since F2-52 both are
enforced — `RoleGuardConversionRatchetTest` now gates the role-name guard count too.

## 4.1 Entitlement / licensing — the subsystem that was 8.6% wired and is now 77.9%

`module/platform/licensing/` is a complete commercial entitlement subsystem:

| Component | Detail |
|---|---|
| Table | `platform.organisation_licenses` — `organisation_id`, `module_code`, `tier` (FREE│PRO│ENTERPRISE), `seat_count`, `valid_from`, `valid_to`, `enabled`, `notes` |
| Service | `LicensingService` — Caffeine-cached (`@Cacheable("licenses")`), validity-window aware, tier validation, **15** ratified `SELLABLE_MODULES` (ADR-018) + a `RETIRED_DESTINATION` map that keeps legacy codes administrable but unsellable |
| Guard | `@RequireModule("CODE")` (class or method) + `LicenseGuardAspect` — AOP `@Around`, method-level wins over class-level (Spring `@Transactional` semantics), **fail-closed when no tenant is in scope**, throws `ModuleDisabledException` → 403 |
| Token | `JwtTokenProvider` issues an `enabled_modules` claim at login |
| API / UI | `LicensingController`; FE client `lib/licensing.ts`; admin screen at `/admin/licensing` |

**Adoption, measured 2026-08-01 — this is the section that moved most:**

| Measure | 07-25 | 08-01 |
|---|---:|---|
| `@RestController` classes carrying `@RequireModule` | **22 of 255 (8.6%)** — *all* in `socops` | **197 of 253 (77.9%)** across every module (F2-04a) |
| Sellable codes vs module packages | 20 `KNOWN_MODULES` vs 58 | **15 SKUs** vs 58 packages — deliberate: ADR-018 merged/de-licensed the rest |
| FE consumers of the entitlement gate | **0** | `lib/entitlement.tsx` + `lib/licensing.ts`, and `filterNavByEntitlement` gates the **whole nav tree and route table** (F2-04b) |
| Components consuming the `enabled_modules` JWT claim | **0** | 3 modules (`entitlement.tsx`, `licensing.ts`, `socops.ts`) |
| `organisation_licenses` rows — **dev** | 22 | **57** — org 1: 27 enabled; orgs 2 & 3: **15 each** (the ratified SKU set) |
| `organisation_licenses` rows — **prod** | — | **still 22** — org 1: 20; **orgs 2 & 3: `SOCOPS` only** |

**The gap has moved from the runtime to the deployment.** In July the licence table recorded intent the
runtime did not honour. Today the runtime honours it — and **production still holds the July licence
rows**, because prod runs the `V334` jar and the SKU migrations are among the 8 pending (§7). So the
enforcement is real, the entitlements it will enforce against are six days stale, and the first restart
applies both at once. (Finding F2-04 — reframed from "no mechanism exists" to "the mechanism exists and
is unadopted", and now to "adopted in code, not yet in the deployed data".)

---

## 5. Frontend reality

| Metric | Measured (08-01) | Reading |
|---|---:|---|
| Pages | **336** in **114** folders | More screens than ServiceNow GRC exposes in its entire IRM suite |
| Routes in `App.tsx` | **328** | One flat route table |
| Sidebar entries | **193** (was 184) | **~135 routed pages are not in the navigation** — reachable only by URL |
| Largest page folders | `bcm` 30, `socops` 25, `detail` 10, `workflow` 10, `cyber` 9, `risk` 9 | `bcm` alone has more screens than most competitors' whole BCM product |
| Mock imports | **30** statements, **26** files (was 44 / 29) | See concentration below |
| Mock files shipped | **15** files, **3,550 lines** | Still in the production bundle |

### 5.1 Mock-data concentration — all three that mattered are now clean

| File | Mock imports 07-25 | 08-01 | What the user saw |
|---|---:|---:|---|
| `lib/searchIndex.ts` | **7** | **0** ✅ | **Global search** returned fabricated records across compliance, risk, evidence, TPRM, privacy |
| `pages/dashboard/widgets.tsx` | **4** | **0** ✅ | **Executive dashboard** widgets rendered invented figures |
| `lib/boardPack.ts` | **3** | **file deleted** ✅ | **Board pack** — the artefact that goes to a real board — was assembled from mock data (**F2-42**) |

The original ranking was by blast radius, not count: a mock inside one obscure page is cosmetic; a mock
inside global search, the executive dashboard and the board pack meant **the three surfaces an executive
buyer will open first were the three that lied.** (Finding F2-02.) **All three are now clean** — W2-1
took search and the dashboard, F2-42 deleted the board-pack builder outright and replaced it with the
server aggregate that already produced the PDF and the deck.

**What remains is the long tail, and it is flat.** No file imports more than **2** mock modules:
`lib/workflowVersioning.ts`, `pages/audit/AuditWorkspacePage.tsx`,
`pages/authority/AuthorityWorkspacePage.tsx` and `pages/workflow/WorkflowsPage.tsx` at 2 each, then 22
files at 1. The concentration argument that made F2-02 urgent no longer applies; the remaining 30
statements are per-page fallbacks, and the 15 mock files (3,550 lines) are still in the production
bundle. ⚠ **A flat tail is the harder shape to finish** — there is no longer a small set of high-value
targets, so the remaining work is 26 individually cheap edits with no single one worth prioritising.

### 5.2 Bilingual machinery — stronger than the documentation claims

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

### 5.3 Design system — a real asset

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

### 5.4 Page-size distribution — where the FE debt actually is

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

## 6. Test coverage by module — and where the gap sits

**15 packages / 18,011 LoC / 9.4% of module code have zero tests** (was 19 / 20,503 / 11.0%). The
distribution is not random:

| Module | LoC | Tests | Saudi / regulatory relevance |
|---|---:|---:|---|
| `data_officer` | 2,375 | **0** | PDPL data-officer workspace, 37 endpoints — **and it owns 14 entity classes, not the 1 the old census showed** (§0.1) |
| `sharia` | 2,236 | **0** | Sharia governance — Islamic-finance differentiator, no competitor equivalent |
| `openbanking` | 1,915 | **0** | SAMA Open Banking framework |
| `rrp` | 1,912 | **0** | Recovery & resolution planning (SAMA) |
| `cpty` | 1,690 | **0** | Counterparty / credit concentration |
| `outsourcing` | 1,657 | **0** | **SAMA outsourcing regulation — a headline selling point** |
| `regreporting` | 1,630 | **0** | Regulatory reporting |
| `reginspect` | 1,162 | **0** | Regulator inspection management |
| `dataclass` | 990 | **0** | Data classification (NDMO/SDAIA) |
| `opres` | 952 | **0** | Operational resilience |
| `nca_ecc` | 370 | **0** | **NCA ECC** — the single most-cited Saudi control framework |
| `controlmap`, `dashboard`, `audit_eng`, `datagov` | 1,122 | **0** | supporting |

**Four packages left this list since 07-25** — `fraud` (4 tests, F2-05 slice 3), `knowledge` (5),
`board_pack` (11, F2-42/F2-50) and `attachment` (15). Every one of them gained tests as a *side effect*
of an authorisation or provenance fix, not from a test-coverage campaign: **the zero-test list is
shrinking because the findings happen to land there, which is not a strategy.**

Thin (below ~1 test per 60 LoC): `ndi` **19** tests / 7,863 LoC (**127 endpoints, 82 writes**) — still
the worst ratio on the platform — `scm` 15 / 3,818, `evidence` 21 / 3,683, `reporting` 26 / 4,193,
`socops` 36 / 6,799.

**The pattern is the finding.** The modules with zero tests are disproportionately the
**Saudi-specific, regulator-facing, competitively differentiating** ones. The heavily-tested modules
(`bcm` 400, `platform` 246, `sust` 200, `admin` 165, `risk` 147) are the generic ones every competitor
also has. The platform is best-assured exactly where it is least differentiated. (Finding F2-03.)

---

## 7. Database and migration surface

- **332** migration files, `V1`…`V344`, **4.30 MB** of DDL. Next free version: **V345**.
- Sequence gaps at `V82–V90`, `V168`, `V169`, `V174` — expected (withdrawn during development),
  harmless to Flyway, but they mean "highest number" ≠ "count".
- **50 distinct JPA schemas. 51 database schemas hold tables**, in *both* dev and prod (464 tables each).

### 7-A. The schema JPA does not know

The 51st schema is **`audit`**, and JPA has no `@Table(schema="audit")` anywhere. It holds exactly one
table — **`audit.audit_logs`, 14 rows in dev and 14 in prod** — with columns
`event_type, entity_type, before_value_json, after_value_json, change_summary_en, event_timestamp,
module, chain_hash`. It is a **complete audit-ledger design, including a hash chain, that no code
reads or writes**; the live ledger is `platform.audit_log`. The published "Distinct JPA schemas: 50"
was **correct** — the error was measuring only the code side, so an orphaned audit table with a
tamper-evident chain sat unnoticed in a GRC platform's database for as long as the census only looked
at Java. **A JPA census and a database census answer different questions and both are needed.**
*(F2-69 originally filed the 50 as wrong; it is not. Recording the correction here because the
document's credibility rests on it.)*

### 7-B. The deployed Flyway baseline — and what it decides

Measured from `platform.flyway_schema_history` in `TSS_GRC_Deployment`, not from prose:

| Fact | Value |
|---|---|
| Baseline | **`V292`** — *not* `V312`, which this page asserted for six days |
| Applied head | **`V344`** (53 history rows: the baseline + `V293`…`V344`, **0 failed**) — re-measured 2026-08-02 after the deploy that applied `V337`…`V344` in 0.725 s |
| Files at or below the baseline | **280 of 339** — recorded as applied, **never executed here** |
| Files executed by Flyway in prod | **52** |
| **Pending the next jar** | **7** — `V345` (A17-D3 ratification SLA), `V346` (F2-94 recipient keys + `AUTOMATION:READ`), `V347` (F2-95/S6b-ii, the ghost role `CTO` dropped), `V348` (S6c `quorum_requires_chair`, inert), `V349` (S7a/F2-89+F2-100, `PROPOSED` + `decision_published_at`, inert), `V350` (S7b/CE-4, the referral's agenda subject — three nullable columns, no backfill by design) and `V351` (S7c/X-2, `ck_change_status` **14 → 16**: `CAB_DEFERRED` + `MORE_INFO_REQUIRED`, a CHECK widening that cannot move a row. ⚠ `PENDING_RATIFICATION` is deliberately **not** added — A17-D3 models ratification orthogonally to `status`, so it has no producer, and V351's own self-check refuses it) |
| Dev | Flyway **OFF**; no `flyway_schema_history` table at all. Migrations applied by hand via pyodbc |

**Why the baseline number is load-bearing and not trivia.** The baseline is the line below which Flyway
marks everything applied without running it. At `V292`, **280 of the 335 migration files have never
executed against production** — prod's schema for all of them came from the restored dump (§7.3), not
from the DDL in this repository. Believing the baseline was `V312` hides twenty versions' worth of that,
and it is the whole of RC-A: *which migrations can never run?* A migration numbered at or below the
baseline is inert no matter how correct its SQL is — the same failure class as `V46a`/`V47a`, which
Flyway skipped for months because their filenames were not versions (`MigrationFilenameRatchetTest`).

⚠ **The pending set is ordered, and one of the eight is a precondition for the code in the same jar.**
`V344__grandfather_licence_backfill.sql` grants every organisation every ratified SKU; §4.1's
`@RequireModule` aspect is **fail-closed**, and production still holds the July licence rows (org 2 and
org 3: `SOCOPS` only). Flyway runs at startup before the app serves traffic, so a single restart applies
the migration before the annotations can refuse anyone — but a deployment that ships the jar with Flyway
disabled or failed would black out two tenants on everything except security operations.

### 7-C. Production is a restore of a dev dump — so row provenance proves nothing

| Measure | Value |
|---|---|
| Tables in dev / prod | **464 / 464**, **460 shared** |
| Shared tables with **identical row counts** | **429 (93%)** |
| Prod-only tables | **4** — `platform.flyway_schema_history` + `risk.kri_definitions`, `risk.kri_readings`, `risk.kri_breach_events` (F2-65's leftovers) |
| Dev-only tables | **4** — `platform.file_metadata`, `platform.nafath_transaction`, `platform.persistence_row`, `platform.sso_provider_config` |

Production is a **2026-07-11 restore of a dev dump**. The consequence is methodological and it
invalidates a whole class of argument: **classifying a production row by its provenance proves nothing**,
because the row was seeded in dev and copied. Only a *clean tenant*, created through the API and torn
down, is trustworthy provenance evidence — which is why F2-68(d) built one.

⚠ **And the divergence is not noise: the 4 dev-only tables are exactly the tables the 8 pending
migrations create.** `persistence_row` (F2-71), `file_metadata`, `nafath_transaction`,
`sso_provider_config` exist in dev because dev applied `V337`–`V344` by hand. So "dev and prod differ by
4 tables" and "prod is 8 migrations behind" are the *same fact* measured two ways — and when this page
last recorded the clone, it said **zero** dev-only tables, which was true then and stopped being true
the moment the next migration was hand-applied.

**Risk this creates:** dev and prod schemas are produced by *two different mechanisms*. Nothing in CI
proves they converge — `SchemaContractRatchetTest` and `Appendix/scripts/schema_drift.py` narrow it, but
a migration that is correct under Flyway and hand-applied differently in dev is still invisible until
production. (Finding F2-04.)

---

## 7.1 Prior-audit evidence vault — `audit/` (tracked, 98 files)

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

### 7.2 Performance — measured, and good within its scope

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

### 7.3 Accessibility — measured, with one serious open violation

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

### 7.4 Coverage, quality and dependencies (2026-06-18 vintage)

| Metric | Value | Note |
|---|---:|---|
| Backend tests then | 1,858 | **now 2,797** (`mvn -o clean package`, 2026-08-01, 0 failures) |
| **Instruction coverage** | **37.1%** | JaCoCo, 1,324 classes — **not re-measured since; the suite has since grown 51%** |
| **Branch coverage** | **26.0%** | |
| **Line coverage** | **38.5%** | |
| Frontend tests then | **0** (`runner: none`) | **now 188** via vitest (`npx vitest run`) — 49 at the 07-25 baseline |
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

## 8. What this baseline does **not** measure

Stated so no reader over-reads it:

- **Runtime performance beyond one endpoint** — a k6 load test exists (§7.2) covering `GET /api/v1/risk/risks` only. No profiling, no write-path or export load test, no capacity model.
- **Test *quality*** — 2,797 passing tests is a count, not an assertion-strength measure. Spot mutation
  checks were done on the committee engine and `ModulePermissionEvaluator` only. JaCoCo coverage from
  2026-06-18 was **37.1% instruction / 26.0% branch / 38.5% line** (§7.4); it has not been re-measured
  since the suite grew from 1,858 to 2,797 tests. **F2-21 is the proof that coverage is not provenance:**
  the committee engine's 38 tests all pass and none of them notices that the voter's identity is
  attacker-supplied.
- **Functional correctness of the 19 zero-test modules** — they may work perfectly; the point is that
  **nothing proves it**, and nothing would catch a regression.
- **Actual tenant data volumes** — dev carries 3 seeded Saudi tenants; there is no production data
  profile in scope.
- **Accessibility beyond the 2026-06-18 sweep** — axe-core + Lighthouse were run over 10 pages (§7.3): login clean, 68 serious colour-contrast instances open. Focus management, keyboard reachability and screen-reader labelling remain unmeasured.
- **Penetration testing** — none. Automated scanning exists (gitleaks, trivy, bandit, npm audit — §7.4) but scanning is not a pentest; the security findings here are design- and code-level.

---

## 9. Information I would request from the business

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

## 10. Reproduction

**One command reproduces every figure on this page:**

```bash
py TSS-GRC-Platform-Bible/Appendix/scripts/baseline_census.py
```

| Invocation | What it does |
|---|---|
| *(no flags)* | Full census + a drift table against the `PUBLISHED` figures, + both databases |
| `--table` | Emits §1 / §3 / §5.1 as markdown, ready to splice back into this file |
| `--check` | Exit 1 if any **gated** figure drifted — the CI-shaped form |
| `--no-db` | Tree only; use when SQL Server is not reachable |
| `--root <dir>` | **Measure an exported old tree.** This is how §0.1 separated "went stale" from "was never reproducible": `git archive <commit> … \| tar -x -C <dir>`, then census that tree |
| `--json <file>` | Machine-readable dump, including the per-module coupling graph |

The three original scripts remain for their narrative sections — but ⚠ **do not take the entity count
from them.** `coupling.py`/`verify.py` detect entities with `"@Entity" in src`, a substring test over raw
source, which is the bug §0.1 documents. `coupling.py` has been corrected to the strict, comment-stripped
predicate; `verify.py`'s foreign-schema pass now uses it too.

```bash
py Appendix/scripts/coupling.py    # module census + coupling graph -> coupling.json (consumed by verify2)
py Appendix/scripts/verify.py      # datagov, foreign schemas, mock reconciliation, page/route/nav counts
py Appendix/scripts/verify2.py     # per-module guard style, permission census, migrations, Saudi coverage
py Appendix/scripts/schema_drift.py  # dev-vs-prod table/row drift + Flyway history -> SCHEMA_DRIFT.md
```

Counting rules, stated so results are auditable:
- **LoC** = raw line count of `.java` under the module package (comments and blanks included).
- **Entities** = `@Entity` **not followed by an identifier character**, over **comment-stripped** source.
  Reported two ways because they answer different questions: **files** that declare an entity (413) and
  **entity classes** declared (426). The published 417 was neither — see §0.1.
- **Endpoints** = `@Get|Post|Put|Patch|Delete|RequestMapping` occurrences, minus one class-level
  `@RequestMapping("…")` per file that has one. **Writes** = `@Post|Put|Patch|Delete` only.
  Both over comment-stripped source, so an annotation inside a Javadoc usage example does not count
  (RC-20; `RequireModule.java`'s own Javadoc contains one).
- **Tests** = `@Test|@ParameterizedTest|@RepeatedTest` in files under `src/test/.../module/<name>/`,
  else attributed to the module the test file imports most. Suite total (2,788) exceeds the module
  sum (2,669) because kernel/config tests belong to no module.
- **Mock imports** = one hit per `import|export … from '…mock…'` **statement** (not per symbol) —
  this is the correction to the previously-published 53.
- **FE test count** is taken from the **runner**, never from a regex: `describe.each`/`it.each` expand at
  runtime, so a static count of call sites (138) is structurally incapable of equalling `vitest run`
  (188). The census reports the call-site count under its own name and does not pass it off as the total.

**Comment stripping is not tidiness, it is correctness — in both directions.** Under-stripping counts
documentation as code (RC-20, four times in this programme). Over-stripping empties the haystack so that
*absence* assertions pass for the wrong reason — F2-68(c) found the frontend scanner doing exactly that:
strip block comments first, and a `//` line containing a block-OPEN marker swallows the file to the next
CLOSE marker anywhere later (23 files mangled, `App.tsx` 87,393 → 47,438 bytes). Both scanners now use a
single ordered alternation over strings, template/text blocks, block comments and line comments, so no
construct can be entered from inside another.

### 10.1 Why this page now has a checker

`MeasuredBaselineFreshnessTest` (backend suite) re-derives the load-bearing figures from the tree and
fails the build when this document disagrees: module packages, entity files, entity classes, JPA schemas,
migration count and head, permission modules, `hasPermission`-less packages, zero-test packages, FE pages
and routes, and the literal `V292` baseline. Fast-moving figures — LoC, test counts, coupling edges — are
reported but **not** gated, because a ratchet that fires on every commit becomes a ratchet someone
disables. Adding a migration or an entity now costs one line in this file and one in the census script's
`PUBLISHED` dict, and the failure message names both.
