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
| Backend suite | **2,438 tests / 0 failures** (2,415 + 23 from DF-3) |
| Authorisation ratchet | **21** (from 62) — floor enumerated: 8 public + 10 self-scoped + 3 participant-scoped |
| Migrations | packaged **V1–V315**; dev has **V313, V314, V315** applied by hand; **prod Flyway baseline V312** — all three land on the next restart |
| Stages complete | **S1, S2, S3, S4** (S0 partially — the restart is outstanding) |
| Module census | **58 packages**, 186,285 LoC, 417 entities, 2,160 endpoints (was documented as 24) |
| Known-fixed-but-not-running | **DF-1** break-glass sweep (was failing 33–35×/day) · **DF-2** stale-cookie 500 |
| Open P0s | **F2-02** fabricated data on 3 executive surfaces · **F2-03** Saudi modules untested · **F2-04** entitlement wired to 8.6% *(DF-3/F2-21 closed at `f3ea92fc`)* |

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

## §2 The one decision that gates a third of this plan

**D-3 · Is the commercial model per-module SKUs, or a single suite licence?**

| If **per-module** | If **suite-only** |
|---|---|
| F2-04 (entitlement rollout), F2-05 (permission conversion), F2-06 (schema ownership) are **P0/P1 and Wave 2** | The same three drop to **P2 hygiene**, and Wave 2 becomes assurance-only |
| ADR-009…012 are adopted as written | ADR-011 (schema ownership) becomes advisory |
| The 11 Saudi Tier-B modules are the first SKUs | Test floors still apply, but packaging work stops |

**Everything else in this plan is unaffected by the answer.** Do not wait on it to start Wave 0 or 1.
Answer before Wave 2 begins. *(Assessment Part 3 §9 lists the other seven business questions.)*

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
| **D-1** | D-1 | `git worktree remove .claude/worktrees/vigilant-swartz-073bd4` (branch already merged) — removes 273 phantom doc files | XS | ⬜ | — |
| F2-20 | F2-20 | **Register** `module/datagov` as maturity `PLANNED` — **do not delete it.** Reading its `package-info.java` showed a deliberate, well-argued v2 deferral (scope + Collibra/Alation overlap + pointers to the live adjacent capabilities), not a stray placeholder | XS | ⬜ *(rides F2-01)* | — |
| **F2-23** | F2-23 | **Catalogue a narrow committee-vote permission.** Measured: **no role holds `ADMIN:WRITE`**, so only `PLATFORM_ADMIN` can record a vote and DF-3's member-self-vote path is unreachable. Add `ADMIN:VOTE` (or a `COMMITTEE` module), keep `ADMIN:APPROVE` as the on-behalf authority, grant per **D-7**. Do **not** widen `ADMIN:WRITE` | S | ⬜ | V316 |
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

### Wave 3 — change enablement (the CE backlog, unchanged in content, re-gated)

| Stage | Src | Action | Gate | Status | Migration |
|---|---|---|---|---|---|
| **S5** | CE-1 | **Module extraction** `module/change_mgmt` → `module/change` with sub-packages + SPI ports (`CommitteePort`, `IncidentPort`; **`EvidencePort`/`WorkflowPort` deferred to S8 where a real caller exists** — a port with no consumer is the F2-14 anti-pattern) + `ChangeModuleBoundaryTest` + FE `pages/change/` skeleton (**keep `mockChanges.ts` — X-7**) | DF-3, S2, S3 | ⬜ | — |
| **S5** | X-2 | **Extend `ck_change_status` 14 → 17** (`CAB_DEFERRED`, `MORE_INFO_REQUIRED`, `PENDING_RATIFICATION`). Runtime-breaking if any CE state code ships first. *(Plan v1 said "13 existing" — it is 14; target 17 unchanged)* | — | ⬜ | **V316** |
| S6 | CE-3, CE-15 | CAB as a governed body — `cab_profile`, `cab_member_role`, charter/ToR, board lifecycle, CAB permissions **catalogued and guarded in the same migration** | S4, S5, **D-1/D-2 answered** | ⬜ | V320, V321 |
| S7 | CE-4…CE-7, CE-14a | Agenda auto-build · decision write-back + conditions register · ECAB ratification SLA · PIR record (**add `CHANGE_PIR` to `ck_ca_source` in the same migration**) · agenda/minutes exports | S6 | ⬜ | V322–V325 |
| S8 | CE-9, CE-2, CE-10, CE-8, CE-11, CE-12, CE-14b | Evidence integration via `EvidencePort` (+ switch `ConditionsSatisfiedGuard` to require **verified** evidence) · change-model catalogue · workflow adoption (15th module) · FSC/PSO · unauthorised-change register · KPI pack · decision-register export | S7 | ⬜ | V326–V328 |

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
| **V315** | **Committee vote provenance** (`recorded_by_user_id`, `recorded_by_name`, `on_behalf`) — DF-3 | 🔵 written, not yet applied |
| **V316** | `ck_change_status` 14 → 17 — X-2 · **and/or** the committee-vote permission — F2-23 | ⬜ |
| V317 | Licence backfill + entitlement rollout — F2-04 | ⬜ *(gated)* |
| V318+ | Schema moves — F2-06b · grant tightening — F2-13b · CAB tables — S6 · CE-4…CE-12 | ⬜ |

**Rules.** Additive only unless explicitly authorised. Idempotent. Applied to dev **twice** via pyodbc
(Flyway is off in dev). Prod picks them up via Flyway on the next jar — **baseline is V312, so V313–V315
all land on the next restart.** `flyway.clean` is disabled; never run it. Every destructive change needs
a reverse recipe in the file header and the user's explicit OK.

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

| # | Decision | Blocks | Owner |
|---|---|---|---|
| **D-3** | **Per-module SKUs or suite-only?** | Wave 2 (F2-04, F2-05 priority, F2-06) | Business |
| D-1 | Which committee constitutes CAB / ECAB per tenant? Orgs 2/3 have **no** committees and were deliberately **not** fabricated | S6 | GRC owner |
| D-2 | ECAB emergency-ratification SLA (default 5 business days) — per tenant? | S7 | GRC owner |
| D-4 | Is **`incident`** a separate SKU or part of Cyber? Decides whether its `cyber`-schema entities move or the packages merge | F2-06b | Product |
| D-5 | Which Saudi integration pack generation governs (root 20 files vs `docs/` 33)? | W0-6 | Product |
| **D-7** | **Which roles may cast a committee vote?** Today the answer is "only `PLATFORM_ADMIN`" | F2-23 | GRC owner |
| **D-8** | **Which roles may cast a CAB vote?** DF-4 narrowed it from **41 roles to 3** (GRC_ADMINISTRATOR, ORG_ADMIN, RISK_MANAGER) by moving the guard to `CHANGE:APPROVE`. That is a large, correct tightening — but **CISO, SERVICE_OWNER and any change-manager role are not in the set**, and a real CAB usually includes them. Review the grant, do not widen the guard. ⚠ **Deployment note:** any tenant user who was voting via `CHANGE:WRITE` and holds none of the three roles now receives **403** | F2-13b | GRC owner |
| D-6 | Does `PLATFORM_ADMIN` bypass **entitlement**? *(Recommendation: no for business modules, yes for platform administration)* | F2-04 | Security + Business |

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

1. ✅ **DF-3 is done** (`f3ea92fc`) — forged vote **403**, non-roster name **400**, on-behalf **200** with
   `on_behalf = 1`; all 5 guards mutation-proven; suite **2,438/0**; V315 applied to dev ×2. It surfaced
   **F2-23**: no role holds `ADMIN:WRITE`, so only `PLATFORM_ADMIN` can record a vote — blocked on **D-7**.
2. **Restart the service** to activate the staged jar — closes DF-1 + DF-2 and lands V313–V315.
   **Only the operator can do this.**
3. **Answer D-3.** It re-prioritises a third of this plan and nothing else in Wave 0/1 waits on it.

```powershell
Stop-Service TssGrcBackend
if (Test-Path 'D:\Deployment\tss-grc\app\tss-grc-platform.jar.new') { Move-Item -Force 'D:\Deployment\tss-grc\app\tss-grc-platform.jar.new' 'D:\Deployment\tss-grc\app\tss-grc-platform.jar' }
Start-Service TssGrcBackend
```
