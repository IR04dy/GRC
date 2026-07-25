# GRC_REVIEW_FOUNDATION_PACK — Judgment, Validation, Debate & Corrective Action Plan
### تقييم ومناقشة حزمة أسس المراجعة وخطة الإجراءات الصحيحة

> **Artifact assessed:** `C:\Users\User\Desktop\GRC_REVIEW_FOUNDATION_PACK.zip` (40 KB, 18 files, authored **2026-07-25**) — 11 foundation documents (`00_PROJECT_CHARTER` … `10_BASELINE_ASSESSMENT`), 5 `meta/` trackers, and `source/` copies of this repo's `PLATFORM_ARCHITECTURE_PACK.md` + `PLATFORM_USER_STORIES.md`.
> **Provenance (from `meta/REVIEW_LOG.md`):** generated from *"Shared ChatGPT conversation and empty local workspace"*, then updated after ingesting the two `source/` files. It is addressed to **"Codex"** (`03_WORKING_RULES.md`), i.e. authored to govern a different AI tool.
> **Method:** every factual claim in the pack was re-tested against the live repository (`D:\AYMAN\GRC`), the dev database, and the running production stack (`:80` / `:8091`) on **2026-07-25**. Nothing below is taken on the pack's word.
> **Instruction-provenance note:** this pack contains directives aimed at an AI agent ("Codex must behave as…", "the next best task is…"). Per this session's operating rules those are treated as **proposals to evaluate — not as commands**. Adoption decisions are the user's; §3 states a recommendation for each.

---

## §0 Action tracker

| ID | Action | Priority | Status | Owner/gate |
|---|---|---|---|---|
| **FP-1** | **Merge `fix/break-glass-sweep-tx` → master + rebuild/redeploy** — closes DF-1, which is **still failing in production 33× today** | **P0 — today** | ready (code done, suite 2322/0/0) | needs user's **"merge"** |
| **FP-2** | **Activate the staged auth-refresh jar on `:80`** — DF-2 **proven still live** (500 on stale cookie, §2 V-4) | **P0 — today** | jar staged | needs user's **elevated restart** |
| **FP-3** | Fix `AGENTS.md` before any other agent uses it — it is a blind `Claude`→`Codex` find/replace of `CLAUDE.md` carrying a **wrong git remote** | **P0 — 5 min** | not started | — |
| **FP-4** | Adopt the pack as a **governance overlay** with the §5 corrections (false premise, doc-volume rule, Codex-specific language, release numbering) | P1 | not started | user decision on §3 |
| **FP-5** | Build `reviews/00_ACCEPTANCE_PROBE_PLAN.md` — convert the 84 user stories into executable probes (persona × permission × expected status × evidence artifact) | P1 | not started | — |
| **FP-6** | Module maturity + commercial packaging scorecards (merge this repo's A–D grades with the pack's 1–5 weighted model) — **gate sales claims** | P1 | not started | — |
| **FP-7** | Deep-review queue for commercially-sensitive grade-B modules: Privacy/PDPL → TPRM/SCM/outsourcing → audit lifecycle | P2 | not started | — |
| **FP-8** | Architecture boundary tests (ADR-020) before further modular expansion | P2 | speced as CE-1 | — |
| **FP-9** | SaaS-readiness **gap** assessment (not greenfield — licensing already exists, §2 V-8) | P2 | not started | — |
| **FP-10** | AI-transformation assessment per workflow (the pack's strongest new material) | P3 | not started | — |
| **FP-11** | Reconcile/retire the V1-era `docs/` corpus that now contradicts the as-built system | P3 | not started | — |

---

## §1 Verdict at a glance

Scored with **the pack's own 1–5 scale** (`04_REVIEW_METHODOLOGY`) — judging it by the standard it sets for others.

| Document | Score | One-line verdict |
|---|---|---|
| `00_PROJECT_CHARTER.md` | **4** | Sound mission and stakeholder map; adds a commercial lens this repo genuinely lacks. "Non-negotiable outcomes" are the right shape. |
| `01_PROJECT_CONSTITUTION.md` | **2** | Strong UX/product/security sections, but the **Architecture Constitution contradicts a deliberate, documented ADR** (§3 D-3) and "assume every decision is wrong" is an expensive default for a repo that already has an ADR log, 2,322 tests and a 287-finding audit. |
| `02_MASTER_EXECUTION_PLAN.md` | **3** | A competent 40-stage review programme — but stages 2–7 (inventory, system map, domain model, personas, workflow inventory) are **already delivered**; running them again is re-work. |
| `03_WORKING_RULES.md` | **4** | The Problem→Evidence→Root-cause→3-Alternatives→Trade-offs→Recommendation structure and the "do not assume" evidence rules are excellent and directly compatible with house doctrine. Tool-specific wording needs neutralising. |
| `04_REVIEW_METHODOLOGY.md` | **2** | Good lenses and weighting; **10 mandatory documents per module × 59 modules = 590 files** is unmanageable and conflicts with the pattern that has actually shipped 8 programmes here. |
| `05_OUTPUT_STANDARDS.md` | **3** | Reasonable structure + a strong Definition of Done; inherits the doc-volume problem. |
| `06_PRODUCT_VISION.md` | **4** | Clear market position, buyer list and packaging strategy — the most useful genuinely-new content alongside the AI strategy. |
| `07_ARCHITECTURE_TARGET_STATE.md` | **3** | The shared-services list matches what exists; **"modules must not query each other directly"** silently imports a microservices ideal into a chosen modular monolith. |
| `08_AI_TRANSFORMATION_STRATEGY.md` | **5** | The best document in the pack. "Not a chatbot" framing, per-workflow interrogation questions, and AI-governance requirements (human review, explainability, prompt/output audit, tenant-safe models, eval datasets) are exactly what this platform has not yet addressed. |
| `09_MULTI_RELEASE_ROADMAP.md` | **2** | Logical shape, but **v2 = "inventory and understand" and v3 = "centralize permissions and audit logging" are already done**, and v4's licensing/feature-flags **already exist in code**. As written it would re-buy shipped capability and misrepresent maturity. |
| `10_BASELINE_ASSESSMENT.md` | **4** | Accurate and honourably honest: it carried this repo's own unflattering grades (C verticals, D `privacy_lc`, live defects) into a commercial recommendation instead of sanding them off. Its priority order is mostly right. Marred by a stray file-transfer artifact ("null-byte versions should be ignored") inside a governing document. |
| `meta/*` (5 files) | **4** | Lightweight, correct practice. But `ASSUMPTIONS`/`KNOWN_LIMITATIONS`/`OPEN_QUESTIONS` are built on a **premise that is false in this environment** (§2 V-1). |

**Overall: 3 / 5 — adopt selectively, not wholesale.** The pack is a well-written *operating system for reviewing a codebase you cannot see*. The codebase is fully available. Roughly a third of its programme re-does completed work, one section would trigger an unjustified rearchitecture, and its two highest-priority items are correct — and this assessment closed them out to proof.

---

## §2 Validation results

Each claim re-tested. **Evidence column = what was actually run.**

| # | Pack claim / premise | Verdict | Evidence |
|---|---|---|---|
| **V-1** | *"No source code has been reviewed yet"* · *"The platform source code itself is not yet available in this workspace"* · open question *"Where is the actual source code repository?"* | ❌ **FALSE — and this is the pack's load-bearing flaw** | The repository is at `D:\AYMAN\GRC` on the same machine whose Desktop holds the zip: **1,734 Java files / 184,278 LoC**, 302 migrations, live dev DB, running prod stack. The entire methodology is calibrated for arm's-length review of an unavailable system. |
| **V-2** | Platform scale: 59 modules · 458 tables · 2,154 endpoints · 2,156 guards · 99 permissions · 57 roles · 211 automation rules · 328 SPA routes · 2,316 tests (2026-07-23) | ✅ **TRUE** | These are this repo's own measured figures, carried faithfully. Test count has since risen to **2,322** on `fix/break-glass-sweep-tx`. |
| **V-3** | Open question: *"Has DF-1 (break-glass sweep) been fixed in the parallel session?"* | ✅ **ANSWERED: fixed in code, NOT merged, STILL FAILING IN PROD** | Branch `fix/break-glass-sweep-tx` @ `5dd05fde` (+ `ccc06799`) in worktree `vigilant-swartz-073bd4`: per-org `REQUIRES_NEW TransactionTemplate` + explicit `TenantContext` + org-scoped native update + 6-test regression class; suite **2322/0/0**. **But prod still logs `break-glass expiry sweep failed` — 33 occurrences today, last 2026-07-25T16:26:35** (`WinSW.NET4_20260725.out.log`), because the branch is unmerged and the prod jar unrebuilt. → **FP-1**. |
| **V-4** | Open question: *"Is production running the staged auth-refresh fix, or is DF-2 still active on port 80?"* | ✅ **ANSWERED: DF-2 PROVEN STILL LIVE** | Crafted-probe on `:80 POST /api/auth/refresh` (`scratchpad/probe_df2.py`): no cookie → **401**; malformed cookie → **401**; **well-formed JWT with a bogus signature → 500** (`{"type":".../errors/internal","status":500}`) — precisely the stale/rotated-cookie defect. Live jar is still the pre-fix build (104,933,172 B); the fix (104,933,156 B) sits staged as `...jar.new`. → **FP-2**. |
| **V-5** | `source/` copies are *"the current baseline reference"* | ⚠️ **ONE REVISION STALE** | `PLATFORM_USER_STORIES.md` identical. `PLATFORM_ARCHITECTURE_PACK.md` **differs in content** (not just line endings): the repo version already records DF-1 as fixed-in-code and strikes through BL-C1; the pack's copy predates that. Governance risk: a pack that copies rather than references drifts silently. |
| **V-6** | *"The attached temp-folder files … were readable. The earlier null-byte versions should be ignored."* (inside `10_BASELINE_ASSESSMENT.md`) | ⚠️ **ARTIFACT** | A file-transfer troubleshooting note left inside a governing document — evidence the pack was assembled through lossy copy/paste (consistent with V-5). Delete on adoption. |
| **V-7** | `03_WORKING_RULES.md`: *"Codex must behave as a senior product … review partner"* | ⚠️ **TOOL-SPECIFIC** | The pack governs a different agent. Related: an untracked **`AGENTS.md`** has appeared in the repo root — **181 lines, byte-identical to `CLAUDE.md` apart from a blind `Claude`→`Codex` substitution**. It therefore asserts the remote is `github.com/Ayman-Server/Codex-GRC-2`; the **real remote is `github.com/Ayman-Server/Claude-GRC-2`**, and "Codex (Codex.ai/code)" is not a real product string. An agent following it would target a non-existent repository. → **FP-3**. |
| **V-8** | Roadmap **v4** treats *"licensing, feature flags, and metering"* as future enterprise-readiness work | ❌ **PARTLY ALREADY BUILT** | `module/platform/licensing/` contains `LicensingService`, `OrganisationLicense`, **`LicenseGuardAspect`**, **`@RequireModule`**, `ModuleDisabledException`, `LicensingController` — per-org module licensing with runtime enforcement. Metering/subscription plans are the genuine gaps. Re-scope v4 to a **gap** assessment (**FP-9**). |
| **V-9** | Roadmap **v2** = *"Complete system inventory / map modules, workflows, data, roles, dependencies / identify architectural coupling"* | ❌ **ALREADY DELIVERED** | `PLATFORM_ARCHITECTURE_PACK.md` (§1 measured footprint, §3 solution architecture, §4 module catalogue, §6 data architecture, §7 21 state machines, §8 integration fabric), `PLATFORM_USER_STORIES.md` (84 stories), `PROJECT_CHRONICLE.md`, plus 8 completed remediation programmes. Re-running v2 buys nothing. |
| **V-10** | Roadmap **v3** = *"Centralize permissions and audit logging / standardize workflow primitives"* | ❌ **ALREADY DONE** | Permissions: one DB catalogue + `ModulePermissionEvaluator` + ratchet test (ADR-005/006). Audit: single `AuditLogService` ledger (ADR-008 lineage). Workflow: one engine adopted by 14 modules (ADR-010). What *is* missing from v3 is **module boundary enforcement** — keep only that (**FP-8**). |
| **V-11** | `04_REVIEW_METHODOLOGY`: 10 mandatory output documents **per module** | ❌ **NOT VIABLE AS WRITTEN** | 10 × 59 modules = **590 documents** — larger than the codebase's own doc corpus and unmaintainable at this repo's velocity. The pattern that has actually shipped here is **one `*_PLAN.md` per programme with a §0 tracker** (8 programmes delivered this way). |
| **V-12** | Grade assignments (A/A− core, B unreviewed, C thin verticals, D `privacy_lc`) and the "sell first / sell later" split | ✅ **TRUE & well-judged** | Faithful to this repo's own §13 scorecard. The refusal to sell grade-C micro-modules as mature products is the pack's single most commercially valuable statement. |
| **V-13** | *"Immediate Priority Order: 1. Confirm and fix live high-risk defects (DF-1, DF-2)"* | ✅ **CORRECT — and now executed to proof** | Both confirmed live this session (V-3, V-4); both have ready fixes; both blocked only on a merge word and an elevated restart. |

---

## §3 The debate — adopt / adapt / reject

### The three substantive conflicts

**D-1 — "Assume every current design decision is wrong until evidence proves otherwise."**
*For:* it prevents inherited bias and is the right stance for an unexamined legacy system.
*Against:* this repo is not unexamined. It carries a **22-entry ADR log with rationale**, a 287-finding external audit with remediation programmes, 2,322 passing tests, and a live-probe discipline for every slice. A blanket presumption of wrongness spends the review budget re-litigating settled, documented choices — precisely the "local optimisation" the constitution warns against.
**→ ADAPT:** *"Undocumented decisions are presumed wrong until evidenced. Documented ADRs stand unless new evidence contradicts them — and challenging one requires writing its superseding ADR."* Keeps the rigour, kills the re-work.

**D-2 — "Modules must not query each other directly" / "No shared database access across module boundaries."**
*For:* it is the correct instinct for coupling, and this repo has real cases of it (Change Enablement importing `IncidentService` and `CommitteeReferralService` with no interface — the very problem CE-1 fixes).
*Against:* taken literally it is a **rearchitecture in disguise**. ADR-001 chose a modular monolith *because* cross-module joins (risk ↔ compliance ↔ evidence) **are the product**; one DB, one deploy, at a scale of 3 tenants on a Windows box. Literal compliance means database-per-module and service extraction: a multi-year, high-risk rewrite that no buyer asked for and that would degrade the cross-domain reporting the platform sells.
**→ REJECT as written; ADAPT to the enforceable version:** *"No module may import another module's Java packages except through a declared port (SPI + adapter). Cross-module data access happens via approved shared services, link tables, events, or documented read models. Shared database, deliberately — enforced by an import-boundary test with a ratchet."* That is exactly ADR-020 / CE-1, already speced. It captures 90 % of the coupling benefit at ~1 % of the cost.

**D-3 — 590 module documents vs. the delivery pattern that works here.**
*For:* per-module depth (UX, security, AI, commercial, Saudi readiness, dependencies, quality gates) is genuinely what a buyer's acceptance committee wants to see.
*Against:* the volume is the enemy of the content; 590 files will be stale before they are complete, and this repo has no history of maintaining anything at that scale.
**→ ADAPT:** one **module scorecard row** per module in a single register (all ten lenses as columns, scored 1–5 with the weighted model) + a **full document set only for modules entering a sales or remediation cycle**. Depth follows commercial intent, not the file system.

### Full disposition

| Pack proposal | Verdict | Reasoning |
|---|---|---|
| Charter mission, stakeholders, non-negotiable outcomes | **ADOPT** | Adds the commercial/stakeholder lens the engineering docs lack. |
| "Optimise globally, never locally"; simplicity over feature count | **ADOPT** | Consistent with the repo's own "do-not-delete deliberate archive" and expand-contract habits. |
| Product Constitution ("can it be removed / merged / sold / AI-assisted?") | **ADOPT** | A cheap, high-yield filter for the 59-module sprawl. |
| UX Constitution (one goal per screen; mandatory empty/error/approval/rejection/escalation/completion states) | **ADOPT** | Genuinely stronger than this repo's §10; converts directly into FE acceptance criteria. |
| Security Constitution (zero trust, least privilege, centralised explainable access control) | **ADOPT** | Already substantially true (ADR-005, SoD 409s, tenant guard) — adopt as the standing yardstick. |
| Decision structure (Problem → Evidence → Root cause → 3 alternatives → Trade-offs → Recommendation → Outcome → Metrics) | **ADOPT** | Use for all §3-class decisions from here. |
| Evidence rules ("a screen is not a workflow", "compliance-named fields are not compliance") | **ADOPT — emphatically** | This is the discipline that produced the grade-B/C honesty in the first place. |
| `meta/` trackers (assumptions, decisions, open questions, limitations, review log) | **ADOPT** | Lightweight; keep in-repo so they cannot drift from the code. |
| 1–5 scoring + weighted categories | **ADOPT with a mapping** | Reconcile to the existing A–D scorecard: **A≈4.5–5 · A−≈4 · B≈3 · C≈2 · D≈1**. Publish both; never two competing truths. |
| Acceptance-committee evidence requirements | **ADOPT — best structural idea** | Becomes the probe packs of FP-5; a buyer's committee can be handed evidence rather than assertions. |
| AI transformation strategy + AI governance | **ADOPT wholesale** | The platform's largest genuine blank space. |
| "Sell first / sell later" module split | **ADOPT** | Protects against the pack's own warning: selling thin verticals as deep products. |
| Next task = acceptance probe plan | **ADOPT** | Correct, and it matches house doctrine (server-enforced ACs are already written as probes). |
| SaaS readiness list | **ADAPT** | Run as a **gap** assessment: licensing/flags exist (V-8); metering, subscriptions, residency, white-label, upgrade strategy are the real gaps. |
| 40-stage master execution plan | **ADAPT** | Delete stages 2–7 (delivered); keep 8–40 with the doc-volume correction. |
| "Assume every decision is wrong" | **ADAPT** | See D-1. |
| Cross-module DB / no direct queries | **REJECT as written → ADAPT** | See D-2. |
| 10 documents per module | **REJECT → ADAPT** | See D-3. |
| Release v2 "inventory and understand" | **REJECT** | Delivered (V-9); also misrepresents maturity to stakeholders — the product is not pre-v2. |
| Release v3 "centralise permissions and audit logging" | **REJECT** (keep boundary tests only) | Delivered (V-10). |
| Release v4 "add licensing and feature flags" | **REJECT → re-scope** | Already built (V-8). |
| Codex-specific operating language + `AGENTS.md` as written | **REJECT until corrected** | Wrong remote, non-existent product string (V-7). |
| "Null-byte versions should be ignored" line | **DELETE** | Transfer artifact in a governing doc (V-6). |
| `source/` file copies as the baseline | **REPLACE with references** | Copies drift (V-5); reference the repo paths and a commit SHA instead. |

---

## §4 P0 — the two live defects (the pack's own top priority, now proven)

**FP-1 · DF-1 break-glass expiry sweep — still failing in production right now.**
Fix is complete and verified on `fix/break-glass-sweep-tx` (`5dd05fde`, suite 2322/0/0, live-proven), but unmerged, so prod has failed the sweep **33 times today** (last 16:26:35). Expired emergency-access grants can outlive their window — a security control silently not running. Say **"merge"** and the sequence is: verify the branch's suite → FF-merge → push → rebuild jar → stage → activate with FP-2 in one restart.

**FP-2 · DF-2 stale-cookie 500 — proven live on `:80` this session.**
```powershell
Stop-Service TssGrcBackend
if (Test-Path 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar.new') { Move-Item -Force 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar.new' 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar' }
Start-Service TssGrcBackend
```
Re-probe with `scratchpad/probe_df2.py`: the bogus-signature case must flip **500 → 401**. *Efficiency note: if FP-1 is merged first, one rebuild + one restart closes both defects together.*

**FP-3 · `AGENTS.md`.** Either delete it or correct it: real remote `github.com/Ayman-Server/Claude-GRC-2`, and replace "Codex (Codex.ai/code)" with tool-neutral wording. A blind find/replace of an instruction file is a supply-chain-shaped risk: the next agent inherits a broken repo URL as fact.

---

## §5 The corrected operating model (what to adopt)

Adopt the pack as a **governance overlay** on top of the existing, working document set — not as a replacement, and not as a new workspace.

```
Repo root (single source of truth — code and governance together)
├── PROJECT_CHRONICLE.md              operations, history, landmines        (existing)
├── PLATFORM_ARCHITECTURE_PACK.md     as-built architecture + assessment    (existing)
├── PLATFORM_USER_STORIES.md          84 stories, server-enforced ACs       (existing)
├── CLAUDE.md / AGENTS.md             agent working conventions             (FP-3 fix)
├── governance/                       ← the ADOPTED overlay
│   ├── CHARTER.md                    from 00 (adopted)
│   ├── CONSTITUTION.md               from 01, with D-1/D-2 corrections
│   ├── WORKING_RULES.md              from 03, tool-neutral
│   ├── REVIEW_METHODOLOGY.md         from 04, with the D-3 correction
│   ├── PRODUCT_VISION.md             from 06 (adopted)
│   ├── AI_STRATEGY.md                from 08 (adopted wholesale)
│   ├── ROADMAP.md                    §6 below (renumbered, de-duplicated)
│   └── meta/                         assumptions · decisions · open questions · review log
├── registers/
│   ├── MODULE_SCORECARD.md           one row per module × 10 lenses (1–5, weighted)  ← replaces 590 docs
│   └── ACCEPTANCE_PROBE_PLAN.md      84 stories → executable probes                 ← FP-5
└── *_PLAN.md                         per-programme plans with §0 trackers  (existing pattern, keep)
```

**Three rules that make the overlay safe:**
1. **Reference, never copy.** No `source/` duplicates — cite repo paths + commit SHA (V-5).
2. **One truth per fact.** Where the overlay and the as-built pack disagree, the **as-built pack wins** and the overlay is corrected; grades map A–D ↔ 1–5 once.
3. **Governance lives with the code**, in the same repo and the same commits, so it cannot drift the way the V1-era `docs/` corpus did (FP-11).

---

## §6 The corrected roadmap

The pack's v2/v3/v4 largely describe finished work. Renumbered and de-duplicated against reality — the platform is **post**-inventory, **post**-centralisation:

| Release | Theme | Content (already-done items removed) |
|---|---|---|
| **R-now** | **Prove & stabilise** | FP-1/FP-2 live defects · FP-5 acceptance probe plan · FP-6 module scorecards · finish the remaining chronicle §19 carry-overs (password rotation, TLS, SMTP, `is_deleted` reconciliation) |
| **R-next** | **Separate & enforce** | CE-1…CE-16 (Change Enablement separation + CAB operating model) · ADR-020 boundary tests platform-wide · FP-7 deep-review of grade-B commercial modules |
| **R+1** | **Package & sell safely** | Commercial packaging per module (licensing already enforces module gating) · metering/subscriptions/white-label/residency gaps · implementation playbooks · Saudi readiness scorecards for the first sellable modules |
| **R+2** | **AI-native** | FP-10 per-workflow AI assessment → evidence-completeness review, control/obligation mapping, meeting summarisation & action extraction, workflow risk prediction, executive report drafting — each with the pack's AI-governance gates (human review, explainability, prompt/output audit, tenant-safe models, eval datasets) |
| **R+3** | **Expand** | Integrations beyond the four STUB adapters · marketplace/plugin strategy · regionalisation |

**Do not name the next release "v2".** The platform has 2,322 passing tests, 8 completed remediation programmes and a production deployment; labelling its next increment "v2 — inventory and understand" would misinform every stakeholder who reads the roadmap.

---

## §7 The pack's open questions — answered from the repo

`meta/OPEN_QUESTIONS.md` lists 11 questions. Eight are answerable immediately; that ratio is itself the argument for FP-4's "governance lives with the code".

| Question | Answer |
|---|---|
| Where is the source repository? | `D:\AYMAN\GRC` — remote `github.com/Ayman-Server/Claude-GRC-2`; 1,734 Java files, 733 FE files, 302 migrations. |
| Can the branch `338700c7` be attached? | Already present; `master` is at `00ba890e`, working branch `feat/change-policy-remediation` at `68b9f80d`, plus worktree `fix/break-glass-sweep-tx` at `ccc06799`. |
| Is DF-2 still active on :80? | **Yes — proven** (V-4): bogus-signature refresh → 500. Fix staged, not activated. |
| Has DF-1 been fixed? | **In code yes** (`5dd05fde`, 2322/0/0), **in production no** — 33 failures today (V-3). |
| Is `tss-grc-ui/` still used? | Superseded static UI; graded **D** in the as-built pack; safe to archive (BL-C4). |
| Which deployment model? | Currently **on-prem Windows Server** (WinSW + nginx + SQL Express, 3 tenants). SaaS is aspiration, not current state — hence FP-9. |
| Which modules to sell first? | Per §13.1 grades: policy/compliance, risk/KRI, workflow/approvals, committees, evidence/audit trail, ESG/GHG. |
| Which grade-B/C modules to deep-review first? | Privacy/PDPL → TPRM/SCM/outsourcing → audit lifecycle (FP-7) — chosen by commercial exposure. |
| Saudi frameworks as hard gates? | Already anchored per module: NCA ECC, SAMA CSF, PDPL, NCEMA, CMA/IFRS S1-S2, SAMA outsourcing, NDMO (pack §15.4) — editions still carry a **verify-before-use** caveat. |
| User research / RFPs / acceptance comments? | **Genuinely unknown — remains open.** Owner: the user. |
| Screenshots / running environment for UX review? | **Available:** dev stack (`:5173` + `:8084`) and production (`:80`) both run; browser-driven verification is already in use. UX review needs no new environment. |

---

## §8 Risks of adopting the pack as-is

| Risk | Severity | Mitigation |
|---|---|---|
| **Documentation displaces delivery** — 590 module docs + a 40-stage programme while a security control is failing in production | **High** | FP-1/FP-2 before any documentation work; D-3 correction |
| **Unjustified rearchitecture** — literal reading of the DB-boundary rule triggers service extraction | **High** | D-2 correction; ADR-020 boundary tests instead |
| **Maturity misrepresentation** — a "v2: understand the system" roadmap shown to buyers or an acceptance committee | **Medium** | §6 renumbering |
| **Two competing truths** — overlay grades (1–5) vs as-built grades (A–D) diverging over time | **Medium** | One mapping, one owner, both published together (§5 rule 2) |
| **Instruction drift via find/replace** — `AGENTS.md` already asserts a non-existent remote | **Medium** | FP-3 |
| **Copy-based baselines going stale** — already happened within two days (V-5) | **Low** | Reference + SHA, never copy |
| **Re-litigating settled ADRs** burns the review budget | **Low** | D-1 correction |

---

## §9 Definition of done for this assessment

- [x] Pack extracted, all 18 files read, provenance established
- [x] Every factual claim re-tested against repo / DB / running production
- [x] DF-1 status resolved to evidence (fixed in code, unmerged, **33 prod failures today**)
- [x] DF-2 status resolved to **proof** (bogus-signature probe → 500 on the running jar)
- [x] Undisclosed find/replace instruction file (`AGENTS.md`, wrong remote) caught
- [x] Roadmap claims tested against code (licensing/flags already built)
- [x] Adopt / adapt / reject recorded with reasoning for every proposal
- [x] Corrective action plan with owners and gates (§0)
- [ ] **FP-1** merge + redeploy (needs "merge") · **FP-2** elevated restart · **FP-3** AGENTS.md
- [ ] FP-4 overlay adopted · FP-5 probe plan · FP-6 scorecards

---

**Bottom line.** The pack is a genuinely good review operating system written for a codebase its author could not see, and it should be adopted for what it uniquely adds — commercial packaging, module maturity gating before sales claims, acceptance-committee evidence, the UX product-state discipline, and the AI transformation strategy. It should **not** be adopted where it re-does completed work (inventory, permission/audit centralisation, licensing), where it would trigger an unjustified rearchitecture (cross-module DB rule), or where its volume rules (590 documents) would replace delivery with paperwork. Its own first instruction was the right one: **fix the live defects first** — and as of this assessment both are proven, both have finished fixes, and both are waiting on a single merge word and one elevated restart.

*Prepared 2026-07-25 against `feat/change-policy-remediation` @ `68b9f80d` (master `00ba890e`; worktree `fix/break-glass-sweep-tx` @ `ccc06799`). Probe script: `scratchpad/probe_df2.py`.*
