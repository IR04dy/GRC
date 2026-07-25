# Measured Baseline — the evidence base for this Bible

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

## 0. Headline totals

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

## 1. Module census — the full 58

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

### 1.1 `datagov` is an empty module
`module/datagov/` contains exactly one file — `package-info.java`, 18 lines, no code. `CLAUDE.md`
names `datagov` in its module list. **A named module with zero implementation is in the platform's
own documentation.** (Finding F2-01.)

---

## 2. Coupling — who reaches into whom

### 2.1 The fan-in ranking (a proxy for "what can never be sold alone")
| Module | Distinct peers depending on it | Reading |
|---|---:|---|
| `platform` | **45** | The kernel. Correct and expected — this *is* the shared core. |
| `iam` | 9 | Identity is a kernel concern behaving like a module. |
| `risk` | 8 | Risk is the platform's semantic centre — everything scores against it. |
| `reporting` | 8 | Reporting is consumed *and* consumes (see 2.2). |
| `evidence` | 7 | Evidence is a kernel concern behaving like a module. |
| `capa` | 6 | Corrective action is cross-cutting by design. |
| `incident`, `policy`, `admin`, `tprm` | 5 each | — |

### 2.2 The four structural coupling defects
| Module | LoC | Reaches into | Why it is a defect |
|---|---:|---|---|
| `reporting` | 5,107 | `risk`×22, `platform`×15, `compliance`×13, `change_mgmt`×12, `ethics`×4, `policy`×4, `evidence`×3, `sust`×1 — **8 modules, 74 edges, only 2 entities** | A god-consumer that imports peers' **entities and services** directly. Any module sold without `reporting` loses its board pack; `reporting` sold alone compiles against 8 modules. |
| `audit_committee` | 1,581 | `compliance`×6, `sust`×4, `tprm`×4, `ai`×3, `capa`×2, `ccm`×2, `governance`×2, `incident`×2, `platform`×2, `risk`×2 — **10 modules** | Highest fan-out-per-LoC on the platform: 1.6 kLoC that cannot compile without ten peers. |
| `governance` | 1,857 | `platform`×10, `audit_lc`×4, `capa`×4, `ccm`×4, `risk`×4, `iam`×3, `policy`×2, `privacy_lc`×1 — **8 modules** | Same shape, and it stores into the **`platform` schema** (§3). |
| `socops` | 6,787 | `platform`×65, `incident`×3, `reporting`×3, `cyber`×2 | Kernel-heavy is fine; the `reporting` edge inverts the dependency direction (a producer importing a consumer). |

---

## 3. Nine modules persist into a schema they do not own

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

## 4. Authorisation coverage — guarded, but not *delegatably* guarded

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

## 4.1 Entitlement / licensing — the subsystem that exists and is 8.6% wired

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

## 5. Frontend reality

| Metric | Measured | Reading |
|---|---:|---|
| Pages | **329** in **114** folders | More screens than ServiceNow GRC exposes in its entire IRM suite |
| Routes in `App.tsx` | **328** (926-line file) | One flat route table |
| Sidebar entries | **184** | **~144 routed pages are not in the navigation** — reachable only by URL |
| Largest page folders | `bcm` 30, `socops` 25, `detail` 10, `workflow` 10, `cyber` 9, `risk` 9 | `bcm` alone has more screens than most competitors' whole BCM product |
| Mock imports | **44** statements, **29** files | See concentration below |
| Mock files shipped | **15** files, **3,650 lines** | Still in the production bundle |

### 5.1 Mock-data concentration — the three that matter
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

## 7. Database and migration surface

- **50** distinct JPA schemas; **304** migration files, `V1`…`V314`, **3.83 MB** of DDL.
- Sequence gaps at `V82–V90`, `V168`, `V169`, `V174` — expected (withdrawn during development),
  harmless to Flyway, but they mean "highest number" ≠ "count".
- Dev is **Python-bootstrapped with Flyway OFF**; prod runs Flyway from baseline **V312**.
  `V313`/`V314` are applied to dev by hand and await the next prod jar.

**Risk this creates:** dev and prod schemas are produced by *two different mechanisms*. Nothing in CI
proves they converge. A migration that is correct under Flyway but was hand-applied differently in dev
(or vice versa) is invisible until production. (Finding F2-04.)

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

## 8. What this baseline does **not** measure

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
