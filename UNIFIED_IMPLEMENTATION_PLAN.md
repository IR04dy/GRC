# TSS GRC — Unified Implementation Plan

> **This is the single governing plan.** It merges the delivery plan (stages S0–S9, the `W-*`
> workstreams and the CE change-enablement backlog) with the independent assessment's findings
> (`F2-*`, `D-*`) into one backlog, one sequence, one migration ledger.
>
> **Status:** ACTIVE · **Version:** 2.1 · **Date:** 2026-07-25 · **Tree:** `eaa79285`
>
> | Document | Relationship to this plan |
> |---|---|
> | [`TSS_GRC_PLATFORM_ASSESSMENT.md`](TSS_GRC_PLATFORM_ASSESSMENT.md) | **Evidence base — not a plan.** Every `F2-*` / `D-*` row below is specified there in full (18 fields each) with a re-verification command. Cite it; do not duplicate it. |
> | `REMEDIATION_MASTER_PLAN.md`, `INDEPENDENT_PLATFORM_ASSESSMENT.md`, `FOUNDATION_PACK_ASSESSMENT_AND_ACTION_PLAN.md` | **Superseded as plans.** Retained for traceability; their actions are absorbed below. |
> | [`TSS-GRC-Platform-Bible/TSS_GRC_PLATFORM_AUDIT_REPORT.md`](TSS-GRC-Platform-Bible/TSS_GRC_PLATFORM_AUDIT_REPORT.md) · [`…/TSS_GRC_PLATFORM_IMPLEMENTATION_PLAN.md`](TSS-GRC-Platform-Bible/TSS_GRC_PLATFORM_IMPLEMENTATION_PLAN.md) | **Strategic frame — complementary, not competing.** Those two carry the programme structure this plan does not: Phases 0–6 over 12–24 months, councils, team shape, release gates, metrics, and the evidence the business must supply. **This plan is the execution tracker** — concrete items with verified status, migrations and probe results. Read theirs for *why and in what order*; read this for *what is actually done*. <br>⚠ **Provisional arrangement.** Both were produced independently on 2026-07-25 and the reconciliation has not been confirmed, so neither is stamped superseded. The complementarity is evidenced: their Phase 0 P0-A found caller-supplied actor identity in **change approvals** (now **DF-4**) while this review found it in **committee votes** (**DF-3**) — *neither found the other's instance*. |
> | `CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md` | **Live specification** for CE-1…CE-16. This plan sequences it; that document details it. |
> | The 20 completed module plans (BCM, ERM, ESG, Cyber/SOC, Workflow, Exports, RBAC, SGI, Change+Policy…) | ✅ Complete and merged. Historic; no open items. |
>
> **House rules that bind every slice:** commit per verified slice on a feature branch · never merge to
> `master` without an explicit "merge" · destructive DB operations need explicit OK · live-probe every
> slice including guard 403s and SoD 409s · `[REVIEW-TEST]` rows cleaned with `CHARINDEX` · bilingual
> `*_en` + `*_ar` on every user-visible record · the authorisation ratchet may only decrease.

---

## §1 Where we actually are

**Verified at `eaa79285`:**

| | |
|---|---|
| Backend suite | **2,460 tests / 0 failures** (2,415 + 23 DF-3 + 13 DF-4 + 9 D-7) |
| Authorisation ratchet | **21** (from 62) — floor enumerated: 8 public + 10 self-scoped + 3 participant-scoped. Unchanged by D-7/D-8: guards were *retargeted*, none removed |
| Migrations | packaged **V1–V317**; dev has **V313–V317** applied by hand; **prod Flyway baseline V312** — all five land on the next restart. Next free: **V318** |
| Stages complete | **S1, S2, S3, S4** (S0 partially — the restart is outstanding) |
| Module census | **58 packages**, 186,285 LoC, 417 entities, 2,160 endpoints (was documented as 24) |
| Known-fixed-but-not-running | **DF-1** break-glass sweep (was failing 33–35×/day) · **DF-2** stale-cookie 500 · **DF-3/DF-4** provenance · **D-7/D-8** grants (V317 is in dev; :80 still serves the 07-23 jar) |
| Open P0s | **F2-28 / DF-5 quorum denominator is caller-authored** (found by reconciling the v1.0 audit's F-003 — the highest open item) · **F2-02** fabricated data on 3 executive surfaces · **F2-03** Saudi modules untested · **F2-04** entitlement wired to 8.6% *(DF-3/F2-21 closed at `f3ea92fc`; DF-4/F2-24 at `21c83d09`)* |
| New P1s from the D-7 probes | **F2-25** appointing a committee member 500s and is mass-assignable · **F2-26** no roster seat is linked to a platform user, so nobody can self-vote |
| New from the §6b reconciliation | **F2-28** quorum integrity (P0) · **F2-27** 23 of 24 `EXPORT` permissions are dead, bulk export is `READ`-authorised (P1) · **F2-29** named change commands (P2) · **F2-30** enforce the bilingual `*_ar` invariant (S) |

⚠ **The `:80` deployment is still running the 2026-07-23 jar.** `jar.new` is staged and the swap needs an
**elevated** shell — attempted this session and refused (`Cannot open TssGrcBackend service`). Until it runs,
DF-1 keeps failing 33–35×/day in production and none of V313–V317 is applied there:

```powershell
Stop-Service TssGrcBackend; Move-Item -Force 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar.new' 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar'; Start-Service TssGrcBackend
```

A pre-swap safety copy of the live jar is already at `D:\Deployment\tss-grc\backup\jar-before-restart-20260725.jar`.

**What the assessment changed about this plan.** Four things, each of which moved work *earlier* or made
it *cheaper*:

1. **A new P0 outranking everything** — committee votes are recorded under a caller-supplied name
   (F2-21 → **DF-3**).
2. **Entitlement is a rollout, not a build** — the subsystem exists and is wired to 22 of 255
   controllers (F2-04). Days, not weeks.
3. **The `reporting` coupling is a misplaced utility, not business coupling** — one package move
   (~1 day) improves eight modules' separability (F2-08a).
4. **Three "absent" things exist** — a k6 load harness, axe/Lighthouse accessibility evidence, and
   platform-wide JaCoCo coverage, all in the tracked `audit/` vault. Extend, don't build.
5. **And DF-3 itself surfaced F2-23** — the vote endpoint's `ADMIN:WRITE` guard is held by **no role**,
   so a fix for forgery immediately exposed an unreachable feature. Closing one control gap tends to
   reveal the next; the plan expects that rather than treating it as scope creep.
6. **Two independent reviews converged on the same defect class in different code paths.** This review
   found caller-supplied identity in **committee votes** (DF-3); the Bible's
   `TSS_GRC_PLATFORM_IMPLEMENTATION_PLAN.md` Phase 0 independently found it in **change
   approvals** (now **DF-4**). Neither found the other's instance. That is strong evidence the
   *class* needs a systematic sweep — every write path that accepts an actor identity — rather
   than instance-by-instance fixes. Folded into F2-22's audit scope.

---

## §2 The decision that gated a third of this plan — ANSWERED

**D-3 · Is the commercial model per-module SKUs, or a single suite licence?**
→ **Per-module SKUs, one deployment, one database.** [ADR-013](TSS-GRC-Platform-Bible/Decisions/ADR-013-PER-MODULE-SKU-AND-ENTITLEMENT.md), 2026-07-25.

So the left column below is now the live plan: **F2-04, F2-05 and F2-06 are Wave 2 as written**, ADR-009…012
are adopted, and the 11 Saudi Tier-B modules are the first SKUs. Wave 2 is **un-gated**.

| Consequence | Detail |
|---|---|
| Wave 2 is unblocked | F2-04 / F2-05 / F2-06 proceed at P1 |
| One item is **added ahead of F2-04** | **W2-0 grandfather licence backfill.** Orgs 2 and 3 hold exactly one licence row each (`SOCOPS`); `@RequireModule` is fail-closed, so annotating controllers before backfilling would black out two of three tenants |
| The SKU catalogue moves out of code | `LicensingService.KNOWN_MODULES` is a hard-coded `Set.of` of 20 codes that calls itself the source of truth — a recompile per SKU. It moves into the module registry (F2-01) |
| Two other decisions dissolved | **D-4** (`incident` joins the Cyber & SOC *SKU*; the package does not move, so F2-06b is no longer gated) and **D-6** (no role bypasses entitlement — platform administration is inside the mandatory Base SKU, so there is nothing to bypass) |

Pricing is **not** decided and is not this plan's business. *(Assessment Part 3 §9 lists the other business
questions.)*

---

## §3 The single backlog

`Src` shows lineage: `W`/`CE` = original delivery plan · `F2`/`D` = assessment. Rows where both appear
are **one** piece of work, not two.

### Wave 0 — now (days). No decision needed; highest value per hour.

| ID | Src | Action | Size | Status | Migration |
|---|---|---|---|---|---|
| **DF-3** | F2-21 | **Committee vote provenance** — voter from `Authentication`; body `memberName` only under secretary/chair authority, stamped `recorded_by_user_id`/`on_behalf`; reject non-roster names; recusal on the **resolved** identity; refuse cross-user overwrite; **add the forged-actor test `ADR-006` demands** | M | ✅ **DONE** `f3ea92fc` — 23 new tests, suite **2,438/0**, 5/5 guards mutation-proven, live probe **11/11**, V315 applied dev ×2 | **V315** |
| **DF-4** | F2-24 | **CAB approval provenance — DF-3's twin.** `recordApproval` binds the `ChangeApproval` entity from the body and trusts `approverName`, so the CM-C2 SoD gate (name-based, acknowledged in-code as interim) is bypassed by submitting another name — **a requester can approve their own change**. Also `CHANGE:WRITE`-guarded, so raise and approve are not separated, and nothing enforces one vote per approver. Fix per DF-3: DTO not entity, actor from `Authentication`, SoD on resolved user id, guard → `CHANGE:APPROVE`, unique `(change, approver_id)` filtered index → 409, forged-actor tests, mutation-verified | M | ✅ **DONE** — 12 new tests, 6/6 guards mutation-proven, V316 applied dev ×2. **Authority tightened 41 roles → 3** (`CHANGE:WRITE` was held by 41 of 57; `CHANGE:APPROVE` by GRC_ADMINISTRATOR, ORG_ADMIN, RISK_MANAGER). Verified reachable — no repeat of F2-23 | **V316** |
| **W0-2** | F2-10 | **Activate the staged jar** (`…jar.new`) — closes DF-1 + DF-2 | S | ⏳ **awaits an elevated restart — user action** | — |
| W0-3 | F2-10 | Post-deploy verification (24 h): break-glass sweep clean, no stale-cookie 500s | S | blocked on W0-2 | — |
| W0-7 | F2-10 | Build ID + migration head on `/actuator/info`; post-deploy probe compares to `master` | S | ⬜ | — |
| **W2-1a** | F2-02 | **De-mock the three executive surfaces** — `lib/searchIndex.ts` (7), `pages/dashboard/widgets.tsx` (4), `lib/boardPack.ts` (3) → real API or `EmptyState` | S | ⬜ | — |
| W2-1b | F2-02/F2-15 | CI rule: no `/mock/i` import outside `**/*.test.*` + **backend-down acceptance test** (every register → error state, **zero rows**) | S | ⬜ | — |
| **F2-08a** | F2-08 | **Promote the export toolkit to the kernel** — `PdfTheme`, `PdfFonts`, `XlsxStyler`, `ExportNaming`, `RegisterExport`, `TabularExport*` → `module/platform/export`. Removes all 25 inbound edges to `reporting`, kills the apparent `socops→reporting` cycle, moves `ai` to Tier A | S | ⬜ | — |
| **F2-07a** | F2-07 | **Route reconciliation matrix** — all 328 routes × {nav entry? · module owner? · real API or mock? · last touched}; classify the ~144 URL-only screens **promote / merge / retire** | S | ⬜ | — |
| F2-07b | F2-07 | CI test: every route has a nav path or explicit `detailOnly: true` | S | ⬜ | — |
| **W0-1** | D-1 | `git worktree remove .claude/worktrees/vigilant-swartz-073bd4` (branch already merged) — removes 273 phantom doc files. *(Renamed from "D-1": it collided with decision D-1 in §6, which is a different thing entirely)* | XS | ✅ **DONE** — `git worktree list` shows only the main tree; corpus now 353 on disk / 335 tracked | — |
| F2-20 | F2-20 | **Register** `module/datagov` as maturity `PLANNED` — **do not delete it.** Reading its `package-info.java` showed a deliberate, well-argued v2 deferral (scope + Collibra/Alation overlap + pointers to the live adjacent capabilities), not a stray placeholder | XS | ⬜ *(rides F2-01)* | — |
| **F2-23** | F2-23 | **Catalogue a narrow committee-vote permission.** Measured: **no role holds `ADMIN:WRITE`**, so only `PLATFORM_ADMIN` can record a vote and DF-3's member-self-vote path is unreachable | S | ✅ **DONE** `5ec6edfe` — resolved by **ADR-014**, and the shape is the *inverse* of what this row proposed: `ADMIN:APPROVE` became the **vote** capability (15 roles) and `ADMIN:WRITE` the **secretariat/on-behalf** right (3 roles), because the same grant also appointed roster members — a voter could add themselves to a roster and manufacture quorum. `ADMIN:READ` 21, `ADMIN:EXPORT` 7. Suite 2,460/0, 5/5 mutations proven, live-probed | **V317** |
| **F2-25** | new | **Appointing a committee member 500s and is mass-assignable.** `POST /committees/registry/terms` binds the `CommitteeMemberTerm` entity raw, no `@Valid`, never stamps `organisationId` → an *authorised* secretariat call fails; `id`/`organisationId` are client-settable on the table that defines quorum. Fix: DTO + tenant stamp + `@Valid` + tests | XS | ⬜ **blocks the D-7 rollout** | — |
| **F2-26** | new | **No roster seat is linked to a platform user** (`member_user_id` NULL on every row), so nobody can cast their own vote — only the secretariat on-behalf path works. Fix: optional `memberUserId` + user picker + roster-linkage report; link the dev personas (CISO→`so_officer`, CCO→`compliance`, CRO→`rmanager`). **Do not guess links for real seats** | S | ⬜ *(after F2-25)* | — |
| F2-13a | F2-13 | **Grant-tightness report** — roles-per-permission, **in both directions**: flag any business-write permission held by >50% of roles or by a read-only/external/machine persona (`WORKFLOW_WRITE` is in 55 of 57), **and any catalogued permission no role holds at all** (`ADMIN:WRITE` is in 0 — a dead permission is an unreachable feature) | S | ⬜ | — |
| F2-22a | F2-22 | Mass-assignment **audit script** over the 216 entity-bound `@RequestBody` sites + `@JsonIgnore` on `id`/`organisationId` in a mapped superclass as the immediate mitigation | S | ⬜ | — |

### Wave 1 — the spine (weeks). Everything durable attaches to the module registry.

| ID | Src | Action | Size | Status | Migration |
|---|---|---|---|---|---|
| **F2-01** | F2-01 | **Module registry** `module-registry.yaml` — seeded from the *verified* `SOURCE_MODULE_INVENTORY.md`; per module: owner, SKU, maturity, tier, schema, permission prefix, test floor, FE route root, docs. **CI fails** on package↔registry mismatch, unmet floor, schema disagreement, or any document stating a contradicting module count | M | ⬜ | — |
| **F2-03** | W3-3 | **Tier-1 test floors** on the regulator-facing modules, in order: `outsourcing`, `nca_ecc`, `sharia`, `data_officer`, then `openbanking`, `dataclass`, `rrp`. Every state transition + authorisation branch, plus one live HTTP probe per write endpoint | L (parallel) | ⬜ | — |
| **F2-05** | F2-05 | **Permission conversion** — catalogue permissions for the 21 role-guarded modules, convert `hasAnyRole` → `hasPermission`, grant to exactly the roles previously named (**behaviour-identical on day one**); add the **tightness ratchet** (`hasAnyRole` in `module/**` → 0) | L (S per module) | ⬜ | V316+ |
| **F2-06a** | F2-06 | **Schema-ownership enforcement test** — every `@Entity` schema == its registry schema. Cheap, permanent, stops new violations | S | ⬜ | — |
| **F2-08b** | W4-1 | **Ratcheted boundary test** over all 58 packages, seeded from today's graph (734 edges, 14 bidirectional pairs); only allowed to decrease | S | ⬜ | — |
| W0-6 | D-3/D-4/F2-19 | **Stamp the doc corpus** — `HISTORIC` banner on the 7 root snapshots (`STATUS.md` 3,119 lines first) + the 158 `docs/` files; decide which Saudi pack generation governs and stamp the other `SUPERSEDED BY`; replace its dead `feat/pdpl-extensions` anchor with a SHA | S | ⬜ | — |
| F2-19b | D-5 | Run the measurement scripts + `doc_register.py` **in CI** so the baseline and register cannot drift | S | ⬜ | — |
| W1-5 | F2-18 | **Golden-path committee integration test** through the real HTTP stack + DB: constitute → refer → vote with a recusal → quorum → approve minutes → verify the e-signature → assert the change advanced and the ledger recorded it. Retain the decision as `[DEMO-EVIDENCE]` (never `[REVIEW-TEST]`) | M | ⬜ | — |
| W1-6 | — | Tenant-isolation test pack for the remaining module families (committee engine ✅ done) | M | 🔶 partial | — |

### Wave 2 — make the commercial model real *(gated on D-3)*

| ID | Src | Action | Size | Status | Migration |
|---|---|---|---|---|---|
| **F2-04** | F2-04 | **Entitlement rollout** — extend `KNOWN_MODULES` 20 → 58 from the registry; `@RequireModule` at class level on every business controller; wire the existing `moduleEnabled()` FE helper into navigation + route registration; **licence backfill migration before enforcing**; cross-tenant test (a tenant licensed for X gets 403 on Y — orgs 2/3 are already seeded for exactly this) | M | ⬜ *(gated)* | V317 |
| F2-06b | F2-06 | Schema moves, in ownership order: **`incident`→`cyber` decision first** (separate SKU or merge?), then `integrations` out of `platform`, then `controlmap`. Compatibility view for one release + rollback recipe | M each | ⬜ *(gated)* | V318+ |
| **F2-09** | F2-09 | **Schema-convergence CI job** — Flyway `V1`→head on an empty DB, dump, diff against the Python-bootstrapped dev schema; fail on any un-allow-listed difference | M | ⬜ | — |
| F2-13b | F2-13 | Tighten the over-broad grants the Wave-0 report surfaces, starting with `WORKFLOW_WRITE`; per-tenant impact query before each change | M | ⬜ | V319 |
| **F2-14** | F2-14 | **`ControlHealth` SPI** — each control declares its required configuration and reports `IN_FORCE │ NOT_CONFIGURED │ DEGRADED`; Control Assurance dashboard + `/actuator/health`; audit the 8 config-gated candidates (authorisation matrix, freeze windows, SLA policies, quorum policies, workflow gate maps, `ck_ca_source`, evidence-mandatory flags, escalation targets). **Generalises `ChangeGovernanceHealthService` — and is saleable** | M | ⬜ | — |
| F2-22b | F2-22 | DTOs for the highest-risk entity-bound endpoints (approval state, tallies, statuses); ratchet the count down | L | ⬜ | — |

### Wave 3 — Change Enablement as a first-class module *(see **§6c** for the target structure)*

**Elevated 2026-07-25 at the owner's direction:** Change Enablement becomes a properly structured module of
its own — change record + lifecycle + authority + **CAB** + **committee integration** + **workflow** — and is
lifted out of the Risk grouping in the UI. §6c specifies the structure; this table sequences it.

| Stage | Src | Action | Gate | Status | Migration |
|---|---|---|---|---|---|
| **CE-0** | new | **Lift Change out of the Risk nav group** — `Sidebar.tsx:106` places `/changes` inside `// 1. Risk Management (CRO)`. Promote to its own `navChange` group with the CAB, calendar/FSC and decision-register entries beneath it. **This is the whole of "not under risk"** — the backend was never under risk (§6c) | none | ⬜ **do first — XS** | — |
| **S5** | CE-1 | **Module restructure** `module/change_mgmt` → `module/change` with the 11 sub-packages of §6c + SPI ports (`CommitteePort`, `IncidentPort`; **`EvidencePort`/`WorkflowPort` deferred to S8 where a real caller exists** — a port with no consumer is the F2-14 anti-pattern) + `ChangeModuleBoundaryTest` + FE `pages/change/` workspace skeleton (**keep `mockChanges.ts` — X-7**) | DF-3, S2, S3 | ⬜ | — |
| **S5** | X-2 | **Extend `ck_change_status` 14 → 17** (`CAB_DEFERRED`, `MORE_INFO_REQUIRED`, `PENDING_RATIFICATION`). Runtime-breaking if any CE state code ships first | — | ⬜ | **V318** |
| **S6** | CE-3, CE-15 | **CAB as a governed body** — `cab_profile`, `cab_member_role`, charter/ToR, term lifecycle, quorum policy; CAB permissions **catalogued and guarded in the same migration** (the F2-23 lesson) | S5, **F2-28 closed**, **D-1/D-2 answered** | ⬜ | V319, V320 |
| **S7** | CE-4…CE-7, CE-14a | **CAB meeting cycle** — agenda auto-build from the CAB queue · decision write-back + conditions register · ECAB ratification SLA · PIR object (**add `CHANGE_PIR` to `ck_ca_source` in the same migration**) · agenda/minutes exports | S6 | ⬜ | V321–V324 |
| **S8** | CE-9, CE-2, CE-10, CE-8, CE-11, CE-12, CE-14b | Evidence via `EvidencePort` (+ `ConditionsSatisfiedGuard` requires **verified** evidence) · change-model catalogue · **workflow adoption (15th module)** · FSC/PSO · unauthorised-change register · KPI pack · decision-register export | S7 | ⬜ | V325–V327 |
| **CE-13** | CE-13 | FE: `ChangesPage` → **Change workspace + CAB workspace** (agenda builder, voting panel, minutes). Only then delete `mockChanges.ts` | S7 | ⬜ | — |

⚠ **S6 is gated on F2-28, and this is not negotiable.** CE-3 makes the CAB a governed body *over the
committee engine*, and CE-4's decisions are asserted on quorate minutes. The quorum denominator is
currently caller-authored (F2-28/DF-5). Building CAB governance on it would **industrialise** the defect:
every CAB decision would inherit a forgeable validity precondition, and the CAB pack export would present
it as evidence. Close F2-28 first — it is 3–5 days and already sequenced ahead.

### Wave 4 — truthful UI, assurance breadth, strategy

| ID | Src | Action | Size | Status |
|---|---|---|---|---|
| F2-07c | W2-*/CE-13b | **12-workspace IA**, navigation generated from registry + entitlements; retire the flat 184-entry sidebar; complete the `ChangesPage` split and **only now delete `mockChanges.ts`** | L | ⬜ |
| F2-15 | W2-* | Remaining 26 mock import sites; move the 15 mock files (3,650 lines) to `__fixtures__` or delete | M | ⬜ |
| **F2-11** | — | **i18n convergence** — ESLint baseline first (**`eslint_configured: false` today**), then no-bare-Latin-JSX and no-`lang === 'ar'`-in-`pages/**` as **ratchets**; migrate the 439 hardcoded strings; fix the 49 zero-Arabic pages; per-module Arabic-coverage CI report | S→L | ⬜ |
| **F2-A11Y** | Part 5 §7 | Fix the **68 `color-contrast` (WCAG 1.4.3, serious)** instances **at the design-token level**, not per component; re-run the existing axe harness; wire `@axe-core/playwright` into CI as a ratchet; then extend to focus/keyboard/screen-reader (genuinely unmeasured) | M | ⬜ |
| **F2-12** | — | `ndi` containment — characterisation tests over its **82 writes** *first*, then split `NdiPage.tsx` (4,970 lines) along tab boundaries | L | ⬜ |
| F2-16 | — | Split `EsgPage.tsx` (5,583) — **before** `ndi`, since `sust` has 200 tests to catch mistakes; page-size ratchet (no new file > 800 lines, existing may only shrink) | M | ⬜ |
| **F2-17** | — | **Extend** the existing k6 harness: realistic seed volumes (10k risks / 50k controls / 200k evidence / 1M audit rows — this is also F2-02's demo tenant), 20 highest-traffic endpoints **including writes/exports/search**, raise dev Hikari above 10 so a ceiling can be found, publish p95 targets, top-5 in CI | L | ⬜ |
| W3-1 | — | The 84 user stories as a CI probe suite | M | ⬜ |
| W3-2 | — | Module scorecard **generated from the registry** — with *Assurance* and *Operating-evidence* columns | S | ⬜ |
| W3-4 | — | Reconcile the 287 findings in `audit/all_findings.json` against this backlog — close or absorb each | M | ⬜ |
| W3-5 | — | Label hollow features; no zero-test/zero-data module in any commercial scope statement | S | ⬜ |
| W4-3 | — | Chronicle carry-overs: passwords, TLS, SMTP, `is_deleted` in native SQL | M | ⬜ |
| W4-4/5/6 | — | Packaging + Saudi scorecards · AI strategy · structural debt (58→~20 package consolidation, using the registry to track it) | L | ⬜ |

---

## §4 Migration ledger

| Migration | Item | State |
|---|---|---|
| V313 | SIGNATURE/AI/CAPA permission catalogue + grants | ✅ dev ×2 · **prod pending the W0-2 restart** |
| V314 | Committee gate map seed (org 1; orgs 2/3 flagged, not fabricated) | ✅ dev ×2 · **prod pending** |
| **V315** | **Committee vote provenance** (`recorded_by_user_id`, `recorded_by_name`, `on_behalf`) — DF-3 | ✅ dev ×2 · **prod pending** |
| **V316** | **CAB approval provenance** — filtered unique index `(change_request_id, approver_id)` — DF-4 | ✅ dev ×2 · **prod pending** |
| **V317** | **Committee + change-authority grants** — `ADMIN:APPROVE` catalogued, `CHANGE_MANAGER` role created, four ADMIN grant sets + `CHANGE:APPROVE` 3→9 — D-7/D-8, F2-23 | ✅ dev ×2 · **prod pending** |
| **V318** | Roster→user link + **quorum integrity — F2-28 / DF-5**. Takes the *lowest* free number because it lands **first**: Flyway applies in version order, so a lower number created later would be skipped out-of-order on an already-migrated database | ⬜ **next to write** |
| V319 | `ck_change_status` 14 → 17 — X-2 *(reassigned three times: V316 → V317 → V318 → V319)* | ⬜ |
| V320–V329 | **Change Enablement (CE-2…CE-15)** — change models (V320), CAB profile/roles (V321), meeting cycle (V322), decisions + conditions (V323), ECAB ratification (V324), PIR (V325), FSC/PSO (V326), evidence (V327), unauthorised-change register (V328), CAB RBAC (V329). Renumbered from the CE plan's stale V313–V322 | ⬜ |
| V330 | Licence backfill + entitlement rollout — F2-04 | ⬜ |
| V331+ | Schema moves — F2-06b · grant tightening — F2-13b | ⬜ |

**Reservations are advisory, order is not.** A number is only real when the file exists. Because
`ck_change_status` has now been reassigned three times, treat every row above as *intent* and re-run
`ls db/migration/sqlserver | sed 's/^V\([0-9]*\)__.*/\1/' | sort -n | tail -1` immediately before creating
a file. **Never insert a number below an already-applied migration** — Flyway would skip it.

**Rules.** Additive only unless explicitly authorised. Idempotent. Applied to dev **twice** via pyodbc
(Flyway is off in dev). Prod picks them up via Flyway on the next jar — **baseline is V312, so V313–V317
all land on the next restart.** `flyway.clean` is disabled; never run it. Every destructive change needs
a reverse recipe in the file header and the user's explicit OK.

⚠ **Reserved numbers move.** `V316` was reserved for `ck_change_status`, then taken by DF-4; `V317` was
reserved for the entitlement backfill, then taken by the D-7/D-8 grants. Read this table, not an older
plan section, before writing a migration — and re-check it immediately before the file is created.

---

## §5 Definition of done — per slice, not per stage

A slice is done when **all** of these hold. This is the standard every completed programme in §0 met.

1. **Full backend suite green** before and after (`mvn -o clean package`, ~2,250+ tests; grep the log for
   `BUILD SUCCESS` / `Tests run:` — wrapper exit codes lie).
2. **Live-probed against the running stack** — Python `urllib` + `pyodbc`, through the Vite proxy (`:5173`)
   when verifying browser behaviour. Guard **403**s and SoD **409**s are part of the probe, not exceptions
   to it; four-eyes paths need **two users** (`admin` + `so_officer`).
3. **The net is proven real** — temporarily break the new guard/check and confirm a test fails, then
   restore. A test that cannot fail is not evidence.
4. **Migrations applied to dev twice** and idempotent.
5. **Ratchets not worsened** — authorisation coverage ≤ 21, plus (once added) tightness, boundary edges,
   mock imports, page size, i18n, accessibility.
6. **Bilingual** `*_en` + `*_ar` on any new user-visible record, client-gated in the form.
7. **Committed per slice** on a feature branch, with the probe result in the message.

---

## §6 Open decisions

**Six of eight are now decided** (2026-07-25), on the owner's instruction to adopt the leader-aligned
best-practice default rather than keep a third of the plan blocked. Each carries an ADR with the
comparator evidence, the alternatives, the trade-offs accepted and its reversal cost.

### Decided

| # | Decision | Answer | ADR |
|---|---|---|---|
| **D-3** | Per-module SKUs or suite-only? | **Per-module SKUs, one deployment, one database**, enforced by the `@RequireModule` aspect that already exists; a **mandatory Base Platform SKU**; the catalogue moves from `LicensingService.KNOWN_MODULES` (`Set.of`, needs a recompile) into the module registry; **grandfather backfill first** — orgs 2/3 hold one licence row each, so annotating before backfilling is an outage. Pricing explicitly out of scope | [ADR-013](TSS-GRC-Platform-Bible/Decisions/ADR-013-PER-MODULE-SKU-AND-ENTITLEMENT.md) |
| **D-4** | `incident` — separate SKU or part of Cyber? | **Dissolved.** A SKU is a commercial view over packages, so this was never architectural. `incident` joins the **Cyber & SOC** SKU; the package does not move. **F2-06b is no longer gated on this** and is judged purely on ADR-011 | ADR-013 |
| **D-6** | Does `PLATFORM_ADMIN` bypass entitlement? | **No — never, for any role.** The question dissolves: platform administration lives in the mandatory Base SKU, so there is nothing to bypass. One rule beats a role-dependent one, and it is what Salesforce does | ADR-013 |
| **D-7** | Which roles may cast a committee vote? | **Roster confers the vote; the grant opens the door.** `ADMIN:APPROVE` created (**15 roles**); `ADMIN:WRITE` cut to the **secretariat (3)** because it also appoints member terms — the same grant did both, so any voter could add themselves to a roster and manufacture a quorum. `ADMIN:READ` **21**, `ADMIN:EXPORT` **7**. `INTERNAL_AUDITOR` gets READ+EXPORT but **no vote** (IIA 1110 independence) | [ADR-014](TSS-GRC-Platform-Bible/Decisions/ADR-014-COMMITTEE-VOTING-RIGHTS.md) |
| **D-8** | Which roles may cast a CAB vote? | **Guard stays `CHANGE:APPROVE`; the grant was the defect.** Created the missing ITIL **`CHANGE_MANAGER`** role and took `CHANGE:APPROVE` from **3 → 9**: +CHANGE_MANAGER, SERVICE_OWNER, CISO, CYBER_MANAGER, COMPLIANCE_OFFICER, EXECUTIVE_REVIEWER. Safe to widen because DF-4's provenance controls carry the load | [ADR-015](TSS-GRC-Platform-Bible/Decisions/ADR-015-CHANGE-AUTHORITY-AND-CAB-VOTING.md) |
| **Doc governance** | How is the 335-document corpus governed? *(was "provisional")* | **Docs-as-code, one canonical doc per subject, machine-checked.** Status header on every `.md` (`AUTHORITATIVE`/`GENERATED`/`HISTORIC`/`SUPERSEDED-BY`), supersede-never-delete, generated files never hand-edited, every quantitative claim cites its command, register CI-enforced. Grounded in ISO 27001 §7.5 — *the clause this product sells* | [ADR-016](TSS-GRC-Platform-Bible/Decisions/ADR-016-DOCUMENTATION-GOVERNANCE.md) |

**D-5** (which Saudi integration pack generation governs — root 20 files vs `docs/` 33) is **downgraded
from a decision to a task** under W0-6: ADR-016 R2/R3 supply the rule, so it needs a content diff and a
`SUPERSEDED-BY` header, not a judgement call.

### Still open — these need tenant-specific business input and cannot be defaulted

| # | Decision | Blocks | Owner |
|---|---|---|---|
| D-1 | Which committee constitutes CAB / ECAB **per tenant**? Orgs 2/3 have **no** committees and were deliberately **not** fabricated | S6 | GRC owner |
| D-2 | ECAB emergency-ratification SLA (default 5 business days) — per tenant? | S7 | GRC owner |

Both are per-customer charter facts. Inventing them would be fabricating governance data, which is the
defect F2-02 exists to record.

### Follow-ups these decisions created

| # | Item | From |
|---|---|---|
| F2-23b | Split a `COMMITTEE` permission module out of `ADMIN`. `ADMIN:*` gates the 7 committee controllers and **nothing else** — 59 methods — so the module name is a misnomer that couples committee grants to unrelated admin screens | ADR-014 |
| F2-13c | Per-change-type change authority (standard / normal / emergency) in data, sourced from the CH-17 authorisation matrix. Would let `EXECUTIVE_REVIEWER` leave the normal-change voting set | ADR-015 |
| W2-0 | **Grandfather licence backfill** — must land before any `@RequireModule` sweep | ADR-013 |

---

## §6b Reconciliation with the strategic frame — the 16 `F-0NN` findings, judged

**This closes the reconciliation the Bible's `README` marked "provisional and unconfirmed".** Every finding
in [`TSS_GRC_PLATFORM_AUDIT_REPORT.md`](TSS-GRC-Platform-Bible/TSS_GRC_PLATFORM_AUDIT_REPORT.md) §10 was
re-derived from source and from the running system on 2026-07-25, using that document's own truth labels.
Nothing was accepted because it was written down, including the parts that flatter this plan.

| # | Claim | Label | Judgment | Lands as |
|---|---|---|---|---|
| **F-001** | Change approval identity is client-controlled | `VERIFIED` → **CLOSED** | True when written. Closed at `21c83d09`: actor from `Authentication`, DTO with no approver field, SoD on the resolved user id, `CHANGE:APPROVE`, `V316` unique index → 409. 7 of its 9 P0-A steps done; steps 8–9 (named commands, ECAB ratification) are F-002's | **DF-4 / F2-24 ✅** |
| **F-002** | Generic transitions bypass approval semantics | `PARTLY CONTRADICTED` | **The exploit is closed; the design debt is real.** `CAB_APPROVED` now requires a quorate committee resolution (Track D) *or* ≥N recorded `APPROVE` votes with zero `REJECT` (CM-C2 + the CH-17 matrix); `EMERGENCY_APPROVED` requires `changeType = EMERGENCY`. So a `CHANGE:WRITE` holder can *advance* a legitimately-decided change but cannot *manufacture* the decision. The literal claim — a generic transition guarded by `WRITE` reaching privileged states — is still true. **Critical → P2** | **NEW F2-29** (below) |
| **F-003** | Committee votes **and quorum** can be manufactured | `VERIFIED` — **half open, and the open half is P0** | Vote identity closed (`f3ea92fc`, V315, V317). **The quorum half was never closed and this register's first pass missed it.** Attendance is caller-authored free text, the denominator is computed *from that sheet*, and `quorumMet` can be set directly with zero attendance | **DF-5 / F2-28 — new P0** |
| **F-004** | Package licensing is not platform-wide | `VERIFIED` | Exact: 22 `@RequireModule` controllers of 252, all `socops`. Un-gated by ADR-013; needs W2-0 backfill first | **F2-04** (Wave 2) |
| **F-005** | Module boundaries are not executable | `VERIFIED`, numbers corrected | Re-measured: **754** peer imports (audit: 734), **128** directed pairs (136), **13** bidirectional (14). Volume up, tangle down — and still no architecture test | **F2-08b** (Wave 1) |
| **F-006** | IA exposes implementation sprawl | `VERIFIED` | **329** routed pages in a 925-line `App.tsx` | **F2-07 / F2-07c** |
| **F-007** | Arabic completeness claims exceed enforcement | `VERIFIED`, and sharper than stated | `ChangeRequest.titleEn` is `nullable = false`; **`titleAr` has neither `nullable = false` nor `@NotBlank`**. So `CLAUDE.md` house rule 7 — *"Arabic names are backend-mandatory"* — is **asserted and not enforced**. A documented platform invariant that no layer checks | **NEW F2-30** (below) |
| **F-008** | Export authorisation violates least privilege | `VERIFIED`, **far broader than stated** | 24 `MODULE:EXPORT` permissions catalogued and granted to up to **55 of 57 roles**; **exactly one endpoint enforces any of them.** Every register, the board pack and the regulator ZIP are `READ`-authorised. The mirror image of F2-23 | **F2-27 — new P1** |
| **F-009** | Governance core lacks adversarial test coverage | `CONTRADICTED` | **6 committee test classes, 99 test methods** — forged actor, roster eligibility, quorum, recusal/COI, tenant isolation, authorisation separation. It was *already* self-contradicted on the day it was written: the sibling document counted 19 recusal + 19 tally tests. Residual gap is narrow: no `EmergencyChangeRatificationTest`, and the two-principal probes are scripted but not in CI | **W1-5** (not a finding) |
| **F-010** | Canonical ownership is fragmented | `EVIDENCE-BACKED` — **not independently verified** | I did not audit object ownership and will not rate it from inference. Retained unrated | **F2-06 family** |
| **F-011** | Accessibility conformance is unproven | `PARTLY CONTRADICTED` | An axe harness exists and measured **68 serious `color-contrast` violations** — so "no report available" is wrong. But independent WCAG 2.2 AA conformance and assistive-technology testing are genuinely absent | **F2-A11Y** |
| **F-012** | Saudi regulatory content lacks a governed lifecycle | `EVIDENCE-BACKED` | Not code-checkable; the Content Office recommendation stands and is a business decision, not an engineering slice | **Wave 4 / business** |
| **F-013** | Documentation completion markers are unsafe | `VERIFIED` — **including inside the Bible itself** | Five instances: `CLAUDE.md` said 24 modules against 58 measured; this audit reports the ratchet at **62** when it is **21**; it reports **2,316** tests while its same-day sibling reports 2,415; the documentation register's census was stale within days; §8 of *this* plan carried a restart command naming a jar that does not exist. The finding is correct and it indicts the corpus that contains it | **ADR-016 + F2-19** |
| **F-014** | Approval primitives are duplicated | `EVIDENCE-BACKED`, partly confirmed | Confirmed in outline: a direct CAB path, a committee path, workflow chains, generic approvals and signatures coexist with differing actor/SoD semantics. Not measured in depth | **P1, Wave 2+** |
| **F-015** | Large frontend units obstruct UX evolution | `VERIFIED` | `EsgPage.tsx` **5,582** lines, `NdiPage.tsx` **4,969** | **F2-16 / F2-12** |
| **F-016** | Production-like operational proof is incomplete | `VERIFIED by absence` | No witnessed restore, failover or benchmark evidence retained | **F2-17 / W4** |

### The audit's own verification claims, re-run

| Claim | Then | Now | Verdict |
|---|---|---|---|
| Backend suite | 2,316 | **2,460 / 0** | grew; both figures plausible at their commit |
| Authorisation ratchet | "threshold of 62" | **21** | **stale in the audit** — it had already been ratcheted |
| Module directories | 58 | **58** | exact |
| Controllers · repositories · services | ~252 · 423 · 313 | **252 · 423 · 314** | essentially exact |
| `@RequireModule` controllers | 22, all SOC Ops | **22, all SOC Ops** | exact |
| Cross-module imports · pairs · bidirectional | 734 · 136 · 14 | **754 · 128 · 13** | corrected |
| Routed frontend pages | "more than 300" | **329** | exact |
| Live two-principal probes | "remain open" | **run** | **closed** — 15/18 through the Vite proxy |

**Verdict on the strategic frame: sound, and worth having.** Its structural measurements reproduce almost
exactly. Three findings needed correction (F-002, F-009, F-011), one was materially understated (F-008),
one is unverified (F-010) — and **one caught a live P0 that this register's own first pass missed** (F-003's
quorum half). That is the second time the two passes have caught different instances of the *same* defect
class in different code, which retires any argument that one of them is redundant.

### Rating, re-judged

The audit's 3.0/5 was fair at its baseline. On measured change since:

| Dimension | Audit | Now | Why |
|---|---:|---:|---|
| Security / control integrity | 1.8 | **2.6** | 2 of its 7 critical weaknesses closed with mutation proof and live probes, 1 mitigated; but F2-28 (quorum) is a live P0 and F2-27 (export) is platform-wide |
| Commercial packaging | 2.4 | **2.6** | still 22 of 252 controllers, but the decision is made (ADR-013) and the mechanism is proven fail-closed |
| Quality / operations | 3.2 | **3.4** | +144 tests, adversarial governance tests, mutation-proven guards; operational evidence still absent |
| Enterprise architecture | 2.6 | **2.6** | unchanged — pairs and cycles improved marginally, no architecture test yet |
| **Weighted** | **3.0** | **≈3.1** | Movement is real but narrow. **Still `Qualified`** |

**Market verdict, changed in one place:** the audit made a controlled pilot conditional on Criticals
closing. F-001 is closed and F-002 is mitigated — but **F2-28 is a new open Critical**, so *"pilot after
Criticals close"* still holds and now has a specific, costed blocker: 3–5 days, sequenced behind F2-25 (2 h)
and F2-26 (1 d). General availability, government final acceptance and per-module sale verdicts are
**unchanged**.

### New backlog items created by this reconciliation

| ID | Src | Action | Size | Status |
|---|---|---|---|---|
| **F2-28 / DF-5** | F-003 | **Quorum integrity — P0.** Attendance rows reference a roster seat, `votingMember` from the seat, denominator = active voting roster (not the sheet), refuse client `quorumMet`, snapshot the roster at meeting start, audit sheet replacement | M | ⬜ **highest open item** |
| **F2-27** | F-008 | **Enforce `MODULE:EXPORT` on every export endpoint** + per-tenant impact query first + a ratchet test on binary-returning endpoints + wire Export History auditing | S | ⬜ |
| **F2-29** | F-002 | Split `POST /{id}/transition` into named commands with state-specific permissions; `CAB_APPROVED`/`CAB_REJECTED`/`EMERGENCY_APPROVED` require `CHANGE:APPROVE`, not `WRITE`. **Exploit already blocked — this is design debt, P2** | M | ⬜ |
| **F2-30** | F-007 | Enforce the bilingual invariant `CLAUDE.md` already claims: `@NotBlank`/`nullable=false` on `*_ar` for material records, starting with `ChangeRequest.titleAr`; a test that fails when a material entity has a mandatory `*_en` and an optional `*_ar` | S | ⬜ |

**Sequence:** F2-25 (2 h) → F2-26 (1 d) → **F2-28 (3–5 d)** → F2-27 (1 d) → F2-30 (S) → F2-29 (P2, later).
F2-28 depends on both roster items: the roster cannot be the quorum denominator while appointment 500s and
every seat is unlinked.

---

## §6c Change Enablement — a separate, properly structured module

**Owner's direction, 2026-07-25:** *"split change management to be a separate module, not under the risk
module, with more well structure including committee and CAB management and workflow."*

### First, the premise — measured, because it changes the size of the job

**Change management is already separate from Risk in the backend.** Verified:

| Check | Result |
|---|---|
| Backend package | `module/change_mgmt` — its own top-level package, 2,157 LoC, 7 entities, 30 endpoints, 49 tests |
| Schema ownership | **all 7 entities write to `change_mgmt`** — no risk tables |
| `change_mgmt` → `risk` imports | **0** |
| `risk` → `change_mgmt` imports | **0** |
| Peer imports | `platform` 6, `admin` 3, `incident` 3 — **fan-out 3, fan-in 1**, one of the cleanest modules in the platform |
| Frontend route | `/changes` — a **top-level** route, not nested under `/risk` |

**What *is* under Risk is the navigation.** `tss-grc-ui-v2/src/components/layout/Sidebar.tsx:106` places
`{ id: 'changes', to: '/changes' }` inside the group commented `// ─── 1. Risk Management (CRO)`. So every
user opening the sidebar sees Change Management as a Risk sub-item, and that is a real product defect —
ITIL change enablement reports to a service-management or IT-governance function, not to the CRO.

The consequence is favourable: **this is an information-architecture and structuring job, not a backend
extraction.** There is no risk-coupling to unpick, no schema to move, no data migration. CE-0 (lift the nav
group) is hours. The substance of the work is the *internal* structure and the CAB/committee/workflow depth
— which is CE-1…CE-16, already specified and now elevated.

### Target structure

Backend, from
[`CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md`](CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md)
§5. The schema name **stays `change_mgmt`** — renaming a schema is destructive with no functional benefit.

```
module/change/
├── package-info.java   module contract + allowed dependencies (the ADR-009 registry entry)
├── request/            the change record — ChangeRequest, ChangeComment, ChangeLink
├── lifecycle/          ChangeStateMachine (pure, no I/O) + guards:
│                       FreezeWindow, Conflict, PirRequired, CabDecision, ConditionsSatisfied
├── authority/          ChangeAuthorisationMatrix — type × risk → gate + min_approvals  (CH-17)
├── model/              change models / pre-approved standard-change templates      (CE-2)
├── cab/                ⭐ CAB profile, agenda, decisions, conditions, ECAB ratification (CE-3…CE-6)
├── schedule/           FSC · PSO · freeze windows · collision detection             (CE-8)
├── task/               ChangeTask + SLA scheduler                                   (CH-5, shipped)
├── pir/                post-implementation review                                   (CE-7)
├── assurance/          unauthorised-change reconciliation                           (CE-11)
├── analytics/          ITIL/ISO KPI pack + CAB throughput                           (CE-12)
└── spi/                ⭐ OUTBOUND PORTS — interfaces only, no peer imports
                        CommitteePort · IncidentPort · EvidencePort · WorkflowPort

module/change_adapters/ adapters live OUTSIDE the module, so neither side depends on the other
```

**The rule that makes the separation real (ADR-012):** `module/change/**` may import only itself,
`common.*`, `security.*`, `platform.*` and its own `spi` interfaces — **never another `module.<peer>`**.
Enforced by `ChangeModuleBoundaryTest`, ratcheted. Today's 3 peer imports (`admin`, `incident`) become
2 ports plus 2 adapters.

### How committee, CAB and workflow relate — the three the owner named

These are three different things and the structure must keep them distinct:

| Concern | Owner | Relationship |
|---|---|---|
| **Committee engine** | `admin/committee` — **kernel**, in the mandatory Base Platform SKU | Generic: charters, member terms, meetings, attendance, quorum, resolutions, e-signed minutes, gate→committee routing. Used by 14 modules, not just change. **Stays in the kernel** — moving it into `change` would make every other module depend on a commercial package |
| **CAB** | `module/change/cab` — **the Change Enablement SKU** | A CAB *is* a committee with change-specific behaviour: a queue-driven agenda, change decisions, conditions, ECAB ratification. So `cab/` holds the change-specific profile and workflow and reaches the generic engine through **`CommitteePort`** — it does not reimplement quorum or minutes |
| **Workflow engine** | `module/workflow` — **kernel** | Change authorisations become workflow requests via **`WorkflowPort`** (CE-10, the 15th adopting module). The change module owns *what* needs authorising; the engine owns routing, SLA and escalation |

**The design decision, stated so it is not re-litigated:** CAB is a *profile over the committee engine*,
not a parallel implementation. The alternative — a self-contained CAB with its own membership, quorum and
minutes — was rejected because it duplicates the engine (F-014's defect class), doubles the surface where
quorum can be forged (F2-28), and would give the platform two answers to "was this decision quorate?".
Track D already routes change and policy approval through committees with quorum, SoD-recusal and e-signed
minutes; `cab/` builds on that.

### Frontend

`pages/changes/ChangesPage.tsx` splits into a **Change workspace** and a **CAB workspace** (CE-13), and the
sidebar gains its own group (CE-0):

```
Change Enablement            ← its own nav group, NOT under Risk Management
├── Change register          the record: raise, assess, link, tasks
├── CAB                      agenda builder · voting panel · minutes · decision register
├── Change calendar / FSC    forward schedule, freeze windows, collisions
├── Change models            pre-approved standard changes
└── PIR & assurance          post-implementation reviews, unauthorised-change register
```

### Commercial position (ADR-013)

**Change Enablement is its own SKU** — already in the ratified catalogue
(`Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md` §6, comparator: ServiceNow ITSM). It depends on the
Base Platform SKU for the committee engine, the workflow engine, evidence and audit — which is exactly the
kernel-plus-package shape ADR-013 requires, and the reason the committee engine must not move into it.
`@RequireModule("CHANGE")` lands with F2-04.

### Sequence

**CE-0 (hours, do now)** → F2-25 → F2-26 → **F2-28 (unblocks S6)** → S5 restructure → S6 CAB as a governed
body → S7 meeting cycle → S8 workflow/evidence/FSC → CE-13 frontend split.

---

## §7 Deliberately not in scope

Recorded so absence reads as a decision, not an oversight:

- **Microservices extraction.** `ADR-001` rejects it; the ratcheted boundary test achieves the discipline
  without the operational cost.
- **A visual redesign / component-library replacement.** Contradicted by measurement — 99% shared-component
  adoption and exact 5,677-key Arabic parity. The IA is the work; the design system stays.
- **Building a new entitlement layer.** One exists (F2-04); a second would create two sources of truth.
- **Back-filling provenance onto historical votes** (DF-3) or **fabricating committees** for orgs 2/3
  (V314). Both would make an absent control *look* present — the exact failure mode of F2-14.
- **Re-adjudicating the 599-line vendor findings register** finding-by-finding — W3-4 reconciles it
  against this backlog instead.
- **A penetration test, a WCAG conformance statement, and a vendor BCP.** Real gaps, outside engineering
  scope; escalate as commercial commitments.

---

## §8 Immediate next actions

**Done since this section was written:** DF-3 (`f3ea92fc`), DF-4 (`21c83d09`), F2-23 + D-3/D-7/D-8 +
doc governance (`5ec6edfe`), landmines 26–28 (`8d42a37d`), W0-1 worktree removal. Suite **2,460/0**.

### The one thing only the operator can do

**Activate the staged jar.** Closes DF-1 (failing 33–35×/day in production) and DF-2, and lands
**V313–V317**. Attempted 2026-07-25 from this session and refused: *"Cannot open TssGrcBackend service"* —
it needs an **elevated** shell. Filenames below are the verified ones on disk; a pre-swap safety copy of
the live jar is already at `D:\Deployment\tss-grc\backup\jar-before-restart-20260725.jar`.

```powershell
Stop-Service TssGrcBackend
Move-Item -Force 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar.new' 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar'
Start-Service TssGrcBackend
Invoke-RestMethod http://localhost:8091/actuator/health
```

### Next engineering slices, in order

1. **F2-25** (XS, ~2 h) — DTO + tenant stamp + `@Valid` on committee member appointment. It **blocks the
   D-7 rollout**: the grants are live, and the first thing a customer must do returns a 500.
2. **F2-26** (S, ~1 d) — link roster seats to platform users so a member can cast their own vote instead
   of every vote being secretariat-recorded `on_behalf = 1`.
3. **W2-1a** (S) — de-mock the three executive surfaces (`searchIndex.ts`, `widgets.tsx`, `boardPack.ts`).
4. **F2-22a** (S) — mass-assignment audit over the 216 entity-bound `@RequestBody` sites. F2-25 is the
   fourth instance of this class found by hand (after F2-21, F2-24 and the roster); the sweep is overdue.
5. **F2-13a** (S) — grant-tightness report in both directions. It would have caught F2-23 *and* the
   ungrantable `ADMIN_APPROVE` automatically.
6. **F2-08a** (S) — promote the export toolkit to the kernel; removes all 25 inbound edges to `reporting`.
7. **W2-0** (S) — grandfather licence backfill, **before** any `@RequireModule` sweep.
