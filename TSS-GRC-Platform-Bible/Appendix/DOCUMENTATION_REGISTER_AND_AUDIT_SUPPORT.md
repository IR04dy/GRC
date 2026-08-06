# Documentation Register & Audit Support Pack

**Date:** 2026-07-25 · **Tree:** `c657734f` · **Scope:** every Markdown document in the repository.
**Machine-readable companion:** [`DOCUMENT_REGISTER.csv`](DOCUMENT_REGISTER.csv) — all **331** tracked
files with git provenance. **Regenerate:** `py Appendix/scripts/doc_register.py`.

**Purpose.** Two jobs. (1) Tell a reader — auditor, acceptance committee, new engineer, diligence
reviewer — **which documents govern and which are historic**, because today nothing does.
(2) Give every finding in `Registers/FINDINGS_REGISTER_V2.md` a **re-verification command**, so no
conclusion has to be taken on trust.

---

## 1. Census

| Measure | Value |
|---|---:|
| Markdown files **on disk** | **604** |
| Markdown files **tracked in git** | **331** |
| Untracked / duplicated on disk | **273** |
| Tracked lines · bytes | **74,878** lines · **4.35 MB** |
| Documents not touched since 2026-06-30 | **214 (65%)** — **50,332 lines (67% of the corpus)** |
| Documents touched in July 2026 | 98 |

### 1.1 Eight corpora, four of them competing

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

## 2. Corpus-level defects

These are findings about the **documentation control itself**. They matter to an audit because an
auditor's first act is to ask "what is the current documentation?" — and today that question has no
single answer.

### D-1 · A stale git worktree duplicates the entire corpus *(High)*
`.claude/worktrees/vigilant-swartz-073bd4` is a **live registered worktree** on branch
`fix/break-glass-sweep-tx` @ `ccc06799`, excluded from git via `.git/info/exclude`. It contains a full
copy of the repository — **267 of the 273 untracked Markdown files**, including a second `STATUS.md`
(3,119 lines), a second `PROJECT_CHRONICLE.md` and a second `docs/` tree.

**Why it matters:** any auditor or engineer who greps the working tree gets **doubled hits**, half of
them pointing at a stale copy on a branch already merged to master. Nothing marks which is which.

**Fix:** `git worktree remove .claude/worktrees/vigilant-swartz-073bd4` (the branch is merged; verify
with `git branch --merged master` first). One command; removes 273 phantom files.

### D-2 · Two competing generations of the Saudi integration pack *(High)*
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

✔ **Resolved 2026-08-06 (W0-6).** All three citations now name commit `c6f116f` (2026-05-11), the commit that branch pointed at — a SHA outlives its branch, which is the point. `GovernanceDocumentIntegrityTest` fails if the dead branch is ever cited again without it. The *“which generation governs”* half of the fix is **not** resolved and **could not be**: neither pack contains the other, so either answer deletes content — see **F2-135**.

**Fix:** decide which generation governs, stamp the other `SUPERSEDED BY <path>`, and replace the dead
branch anchor with a commit SHA.

### D-3 · 65% of the corpus is unmarked and stale *(High — this is `W0-6`, still open)*
**214 tracked documents / 50,332 lines** have not been touched since 2026-06-30 — i.e. before the
remediation programmes that changed the platform's authorisation model, committee engine, export layer,
workflow adoption and permission catalogue. None carries a `HISTORIC` or `SUPERSEDED` stamp.

**Why it matters:** `docs/analysis/10-module-definitions.md` (1,146 lines, 2026-04-30) and
`docs/analysis/15-licensing-model.md` (691 lines) describe module and licensing models predating the
current code. A reader has no signal that they are reading April.

### D-4 · The largest document in the repository is the most misleading *(High)*
`STATUS.md` — **3,119 lines**, last commit 2026-06-01. `CLAUDE.md` itself warns its snapshot is
**2026-04-22 and predates all remediation programmes**. It is the biggest, most authoritative-looking
document in the tree and it is three months stale, unmarked in the file itself.

**Fix:** a `> ⛔ HISTORIC — snapshot 2026-04-22. Superseded by PROJECT_CHRONICLE.md and
UNIFIED_IMPLEMENTATION_PLAN.md.` banner at line 1. Same for `SESSION_SUMMARY.md` (939 lines,
2026-04-30), `summary.md` (398, 2026-05-02), `PROJECT_BUILD_HISTORY.md`, `sprints/*`.

### D-5 · No document register, owner, or review cycle *(Medium)*
331 governing-and-historic documents with no index, no per-document owner, no review date, no
supersession mechanism. This is the root cause of D-1 through D-4 **and** of F2-19 (documentation
asserting numbers nothing verifies) — including the three wrong numbers this project published in its
own plans.

**Fix:** this file plus `DOCUMENT_REGISTER.csv` is the register. Make it generated (the script exists),
add `owner` and `review_by` front-matter to governing documents, and fail CI when a governing document
passes its review date.

### D-6 · The corpus is 74,878 lines with no reading path *(Medium)*
A new engineer or auditor faces 331 files and no stated order. `CLAUDE.md` and the Bible `README.md`
each provide a partial map; neither covers the other's corpus. §3 below is the missing reading path.

---

## 3. What governs — the authoritative reading set

**Read in this order.** Everything not listed here is supporting, historic, or superseded.

### Tier 1 — start here (4 documents, ~1,750 lines)
| Document | Lines | Why |
|---|---:|---|
| [`CLAUDE.md`](../../CLAUDE.md) | 200 | Operating instructions, commands, house rules, top landmines. **Corrected 2026-07-25** (24 → 58 modules) |
| [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md) | 1,127 | The master business + functional reference for every module, screen and API; installation, operations, troubleshooting, all 25 landmines |
| [`UNIFIED_IMPLEMENTATION_PLAN.md`](../../UNIFIED_IMPLEMENTATION_PLAN.md) | 323 | **The single governing plan** — stages S0–S9, tracker, migration ledger. All other plans feed it |
| [`TSS-GRC-Platform-Bible/README.md`](../README.md) | 122 | Index to the reference corpus + the independent review pass |

### Tier 2 — the evidence base (6 documents)
| Document | Lines | Role in an audit |
|---|---:|---|
| [`Appendix/MEASURED_BASELINE.md`](MEASURED_BASELINE.md) | 300 | **Every number an auditor needs, with reproduction scripts.** Cite this, not prose |
| [`Registers/FINDINGS_REGISTER_V2.md`](../Registers/FINDINGS_REGISTER_V2.md) | 604 | 22 findings, 18 fields each, baseline-anchored |
| [`Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md`](../Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md) | 188 | Three verdicts, 13-dimension scorecard, 4 conditions |
| [`Governance/BIBLE_VALIDATION_LOG.md`](../Governance/BIBLE_VALIDATION_LOG.md) | 127 | Which documents in this corpus are verified vs asserted |
| [`Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md`](../Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md) | 257 | Per-module separability tier + SKU catalogue |
| [`UX/UX_REDESIGN_BLUEPRINT.md`](../UX/UX_REDESIGN_BLUEPRINT.md) | 243 | Measured UX position and the IA redesign |

### Tier 3 — decisions (12 ADRs)
`Decisions/ADR-001` … `ADR-012`. **Every ADR has a "Proof" section naming tests that mostly do not
exist** — see `BIBLE_VALIDATION_LOG.md` §3. `ADR-006` is contradicted in implementation by **F2-21**.

### Tier 4 — operations
[`DEPLOYMENT.md`](../../DEPLOYMENT.md) (323) · [`LOCAL_SETUP.md`](../../LOCAL_SETUP.md) (243) ·
[`AGENTS.md`](../../AGENTS.md) (183) · [`reports/audit-report.html`](../../reports/) (the 287-finding platform audit)

### Tier 5 — programme plans (24 files, 7,972 lines)
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

### Tier 6 — stamp HISTORIC (7 files, 5,220 lines)
`STATUS.md` (3,119) · `SESSION_SUMMARY.md` (939) · `summary.md` (398) ·
`PROJECT_BUILD_HISTORY.md` (320) · `SESSION_SUMMARY_2026-06-18.md` (197) · `sprints/` (4 files, 1,328) ·
plus `docs/` per D-3.

---

## 4. Audit evidence chain

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

## 5. Acceptance-committee sampling guide

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

## 6. Evidence an auditor will request that does not exist

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

## 7. Corpus remediation — closes `W0-6`

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

## 8. Reproduction

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
