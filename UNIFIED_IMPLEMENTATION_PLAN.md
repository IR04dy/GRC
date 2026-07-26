# TSS GRC — Unified Implementation Plan

> **This is the single governing plan.** It merges the delivery plan (stages S0–S9, the `W-*`
> workstreams and the CE change-enablement backlog) with the independent assessment's findings
> (`F2-*`, `D-*`) into one backlog, one sequence, one migration ledger.
>
> **Status:** ACTIVE · **Version:** 2.2 · **Date:** 2026-07-25 · **Tree:** `ff087c7c` · **Reviewed by:** a 5-seat Fable 5 council (§6d)
>
> | Document | Relationship to this plan |
> |---|---|
> | [`TSS_GRC_PLATFORM_ASSESSMENT.md`](TSS_GRC_PLATFORM_ASSESSMENT.md) | **Evidence base — not a plan.** `F2-01`…`F2-24` and `D-1`…`D-8` are specified there in full (18 fields each) with a re-verification command. ⚠ **`F2-25`…`F2-35` and `DF-5` are NOT in the assessment** — found after it was generated. Full 18-field specs exist in [`Registers/FINDINGS_REGISTER_V2.md`](TSS-GRC-Platform-Bible/Registers/FINDINGS_REGISTER_V2.md) for **F2-25, F2-26, F2-27 and F2-28 only**. **`F2-29`…`F2-35` are stated in §6b/§6d as table rows and their 18-field specs are still owed** — write one before scheduling any of them. |
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

**Verified at `ff087c7c`:**

| | |
|---|---|
| Backend suite | **2,469 tests / 0 failures** (2,415 + 23 DF-3 + 13 DF-4 + 9 D-7 + 9 F2-25) |
| Authorisation ratchet | **21** (from 62) — floor enumerated: 8 public + 10 self-scoped + 3 participant-scoped — ⚠ **one of the 3 is a defect, not a floor**: see **W0-8** (plan-distribution acknowledgement is not bound to its recipient). Unchanged by D-7/D-8: guards were *retargeted*, none removed |
| Migrations | repo packages **V1–V320**; dev has **V313–V320** applied by hand. **Prod Flyway history head is V312** (configured `FLYWAY_BASELINE_VERSION=292`). ⚠ **The staged jar packages only V1–V312** — verified by opening it as a zip: 300 migrations, highest V312. **V313–V317 do NOT land on the restart.** Next free: **V321** |
| Stages complete | **S1, S2, S3, S4** (S0 partially — the restart is outstanding) |
| Module census | **58 packages**, 186,285 LoC, 417 entities, 2,160 endpoints, 255 `@RestController` classes (was documented as 24 modules) |
| Known-fixed-but-not-running | **DF-1** break-glass sweep (failing 33–35×/day) · **DF-2** stale-cookie 500 — **these two the staged jar does deliver** (`5dd05fde` predates the 17:37 build). **DF-3, DF-4, D-7/D-8 and S1–S4's guard sweeps it does NOT** — those commits land at 17:51–21:28, *after* the jar was built, and all 20 sit on an **unmerged** branch. They need merge → rebuild → restage → a **second** restart (**W0-2b**) |
| Open P0s | ✅ **F2-28 / DF-5 CLOSED** 2026-07-26 `V319` — the quorum denominator is now the roster, persisted and provenanced *(was: caller-authored)* (credit: the v1.0 audit’s F-003) · **F2-02** fabricated data on 3 executive surfaces · **F2-03** Saudi modules untested · **F2-04** entitlement wired to 8.6% *(DF-3/F2-21 closed at `f3ea92fc`; DF-4/F2-24 at `21c83d09`)* |
| From the D-7 probes | ✅ **F2-25 CLOSED** `55fcbe25` *(P2 — the mass-assignment half was **disproved** in round-2 review; the service defends)* · ✅ **F2-26 CLOSED** 2026-07-26, **V318** — seats now link to platform users and a member can cast their own vote (`on_behalf = 0`, live-proven); it also surfaced **F2-38**, a mock-backed `UserPicker` that would have linked seats to invented identities |
| New from the §6b reconciliation | **F2-28** quorum integrity (P0) · **F2-27** 23 of 24 `EXPORT` permissions are dead, bulk export is `READ`-authorised (P1) · **F2-29** named change commands (P2) · **F2-30** enforce the bilingual `*_ar` invariant (S) |

⚠ **The `:80` deployment is still running the 2026-07-23 jar**, and **what the restart delivers was
misstated in every previous revision of this plan.** Round-3 review opened the staged artifact as a zip;
I confirmed it. `jar.new` (built **17:37**) packages **300 migrations, highest V312** — byte-identical in
migration content to the live jar. So the swap applies **zero** migrations and ships **only** the code
changes committed before 17:37: **DF-1 and DF-2**. Everything else — DF-3 (20:07), DF-4 (20:42),
V313–V317, the e-signature/AI/BCM guard sweeps that took the ratchet 44 → 21 (17:51–18:05) — postdates the
build **and** sits on an unmerged branch (`master...feat/change-policy-remediation` = **0 / 20**).

**This was the plan's most consequential error:** W0-2 is the one action assigned to a human, and W0-3's
verification protocol (break-glass clean, no stale-cookie 500s) would have gone **green** while forgeable
committee votes and self-approvable changes remained live in production. Fixing DF-1's 33–35 failures/day
is still worth the restart — it is simply not the provenance fix.

**W0-7 (build ID + migration head on `/actuator/info`) is now a precondition** for this plan ever again
asserting that anything "lands on the next restart". It would have made this claim impossible to write.

Still worth running now, for DF-1 + DF-2 only:

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

## §1b Owner action register — the constraint this plan was not managing

**Round-3 review named the plan's biggest unexamined assumption: that the binding constraint is
engineering throughput.** The record says otherwise. Every *completed* item is engineer-solo work; every
*stalled* item waits on a human who is not the engineer. The plan meticulously instruments the engineering
side — ratchets, per-slice DoD, mutation proofs, a migration ledger — and had **zero instrumentation for
the other actor**. Owner-blocked items were scattered across §1, §6, §6d and §8 with no aggregate view, no
requested date and no escalation. This table is that view.

| # | Action needed from the owner | Blocks | Requested | Notes |
|---|---|---|---|---|
| **O-1** | **Elevated restart** (`Stop-Service` → `Move-Item` → `Start-Service`) | DF-1 (33–35 failures/day), DF-2, W0-3 | 2026-07-25 | Attempted and refused this session — needs an elevated shell. **Delivers DF-1/DF-2 only** (§1) |
| **O-2** | **`merge` authorisation** for `feat/change-policy-remediation` (20 commits) | DF-3, DF-4, D-7/D-8, V313–V317, the ratchet 44→21 guard sweeps — *everything provenance-related reaching production* | 2026-07-25 | House rule 2 forbids merging without the explicit word. Then rebuild → restage → **O-1 again** (W0-2b) |
| **O-3** | **F2-35** — ratify ONE SKU catalogue; specifically, are committee/board governance and access governance **paid** or **free Base**? | **All of Wave 2** (W2-0, F2-04) | 2026-07-25 | Both advocates argued it in §6d; recommendation on the record. ~$0.5M–2.7M/yr either way on the reviewer's price anchors |
| **O-4** | **D-1** — which committee constitutes each tenant's CAB / ECAB | **S6** (and therefore S7, S8, CE-13) | earlier | Per-tenant charter fact; orgs 2/3 have no committees. Deliberately not fabricated |
| **O-5** | **D-2** — ECAB ratification SLA (default 5 business days) | **S7** | earlier | Per-tenant |
| **O-6** | **D-9** — SKU shelf name for Change Enablement | nothing (cosmetic) | 2026-07-25 | **Recommendation withdrawn** — round 2 showed "Change Enablement" is ITIL 4's own practice name. Defer to pricing time |
| **O-7** | Commission the **independent penetration test**, **witnessed restore/failover**, and **WCAG conformance statement** | 3 of the audit's own §11 acceptance gates | not yet | §7 calls these out of scope, which reads as a silent rejection of three gates unless an owner is named |

**Why this table earns its place:** if owner latency stays at its demonstrated level, Wave 2 cannot start
(O-3), Wave 3 cannot pass S6 (O-4), and **nothing new reaches production at all** (O-2) — while the
engineering loop keeps producing verified commits that accumulate on an unmerged branch. The programme's
end date becomes independent of engineering pace, and §1's "where we actually are" keeps meaning *where the
branch is*, which is not where the product is. Finding 1 (the staged jar) is that gap's first casualty.

**Make owner latency as visible as the authorisation ratchet.** Update `Requested` and add a waiting-days
column at each review.

---

## §2 The decision that gated a third of this plan — ANSWERED

**D-3 · Is the commercial model per-module SKUs, or a single suite licence?**
→ **Per-module SKUs, one deployment, one database.** [ADR-013](TSS-GRC-Platform-Bible/Decisions/ADR-013-PER-MODULE-SKU-AND-ENTITLEMENT.md), 2026-07-25.

So the left column below is now the live plan: **F2-04, F2-05 and F2-06 are Wave 2 as written**, ADR-009…012
are adopted, and the 11 Saudi Tier-B modules are the first SKUs. **D-3 no longer gates Wave 2 — but F2-35 now does** (§6d): the SKU catalogue exists in three contradictory versions, and W2-0/F2-04 cannot be written until one is ratified.

| Consequence | Detail |
|---|---|
| Wave 2 is unblocked *by D-3* | F2-04 / F2-05 / F2-06 proceed at P1 — **once F2-35 ratifies one catalogue** |
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
| **DF-4** | F2-24 | **CAB approval provenance — DF-3's twin.** `recordApproval` binds the `ChangeApproval` entity from the body and trusts `approverName`, so the CM-C2 SoD gate (name-based, acknowledged in-code as interim) is bypassed by submitting another name — **a requester can approve their own change**. Also `CHANGE:WRITE`-guarded, so raise and approve are not separated, and nothing enforces one vote per approver. Fix per DF-3: DTO not entity, actor from `Authentication`, SoD on resolved user id, guard → `CHANGE:APPROVE`, unique `(change, approver_id)` filtered index → 409, forged-actor tests, mutation-verified | M | ✅ **DONE** — 13 new tests (12 in `CabVoteProvenanceTest` + 1 in `ChangeManagementServiceTest`), 6/6 guards mutation-proven, V316 applied dev ×2. **Authority tightened 41 roles → 3** (`CHANGE:WRITE` was held by 41 of 57; `CHANGE:APPROVE` by GRC_ADMINISTRATOR, ORG_ADMIN, RISK_MANAGER). Verified reachable — no repeat of F2-23 | **V316** |
| **W0-2** | F2-10 | **Activate the staged jar** (`…jar.new`) — closes DF-1 + DF-2 | S | ⏳ **awaits an elevated restart — user action** | — |
| W0-3 | F2-10 | Post-deploy verification (24 h): break-glass sweep clean, no stale-cookie 500s | S | blocked on W0-2 | — |
| W0-7 | F2-10 | Build ID + migration head on `/actuator/info`; post-deploy probe compares to `master` | S | ⬜ | — |
| **W2-1a** | F2-02 | **De-mock the three executive surfaces** — `lib/searchIndex.ts` (7), `pages/dashboard/widgets.tsx` (4), `lib/boardPack.ts` (3) → real API or `EmptyState` | S | ⬜ | — |
| W2-1b | F2-02/F2-15 | CI rule: no `/mock/i` import outside `**/*.test.*` + **backend-down acceptance test** (every register → error state, **zero rows**) | S | ⬜ | — |
| **F2-08a** | F2-08 | **Promote the export toolkit to the kernel** — `PdfTheme`, `PdfFonts`, `XlsxStyler`, `ExportNaming`, `RegisterExport`, `TabularExport*` (+`DocxKit`) → `module/platform/export` | S | ✅ **DONE** `313dd6dd` — all 9 files present; `reporting` fan-in **25 → 0**; 22 files now import `platform.export`. *Was listed open in two places until the council caught it* | — |
| **F2-07a** | F2-07 | **Route reconciliation matrix** — all **329** routes × {nav entry? · module owner? · real API or mock? · last touched}; classify the URL-only screens (**329** routes vs **184** nav entries — the unreachable set is larger than the 145 difference, because several entries are query-tab variants of one route) **promote / merge / retire** | S | ⬜ | — |
| F2-07b | F2-07 | CI test: every route has a nav path or explicit `detailOnly: true` | S | ⬜ | — |
| **W0-1** | D-1 | `git worktree remove .claude/worktrees/vigilant-swartz-073bd4` (branch already merged) — removes 273 phantom doc files. *(Renamed from "D-1": it collided with decision D-1 in §6, which is a different thing entirely)* | XS | ✅ **DONE** — `git worktree list` shows only the main tree; corpus now 353 on disk / 335 tracked | — |
| F2-20 | F2-20 | **Register** `module/datagov` as maturity `PLANNED` — **do not delete it.** Reading its `package-info.java` showed a deliberate, well-argued v2 deferral (scope + Collibra/Alation overlap + pointers to the live adjacent capabilities), not a stray placeholder | XS | ⬜ *(rides F2-01)* | — |
| **F2-23** | F2-23 | **Catalogue a narrow committee-vote permission.** Measured: **no role holds `ADMIN:WRITE`**, so only `PLATFORM_ADMIN` can record a vote and DF-3's member-self-vote path is unreachable | S | ✅ **DONE** `5ec6edfe` — resolved by **ADR-014**, and the shape is the *inverse* of what this row proposed: `ADMIN:APPROVE` became the **vote** capability (15 roles) and `ADMIN:WRITE` the **secretariat/on-behalf** right (3 roles), because the same grant also appointed roster members — a voter could add themselves to a roster and manufacture quorum. `ADMIN:READ` 21, `ADMIN:EXPORT` 7. Suite 2,460/0, 5/5 mutations proven, live-probed | **V317** |
| **W0-8** | ratchet javadoc | ⚠ **WITHDRAWN — already fixed before this row was written.** `PlanDistributionService` was bound to `recipientUserId` at **`e9056b09`** (on master, with a 12-case `PlanDistributionOwnershipTest`), **four hours before** the round-1 council endorsed it as an open defect "tracked in no plan at all". The row was authored from `ControllerAuthorizationRatchetTest`'s javadoc, which still says "pending an OWNERSHIP fix … tracked as its own slice" — **the javadoc is stale and the plan inherited its staleness.** Lesson added to the review protocol: *grep the git history before accepting a code comment as evidence of an open item.* **Residual (S):** the javadoc itself, plus `ExerciseRuntimeController#respond` — the third participant-scoped endpoint, which `e9056b09` did not touch | S | 🔶 **residual only** | — |
| **F2-25** | new | **Appointing a committee member 500s on a reasonable body.** `POST /committees/registry/terms` binds the entity raw with **no `@Valid`**, and `appointed_at`/`term_starts_at` are NOT NULL with no default — so an authorised call dies as a `DataIntegrityViolation` surfaced as 500. Fix: `MemberTermRequest` DTO (dates `@NotNull`), `@Valid`, in-tenant `committeeId` check, 400-not-500 tests; sweep `/skills`, which binds an entity too. ⚠ **P1 → P2:** the original entry claimed a cross-tenant mass-assignment vector; the service has defended since `8373d6f8` and a live probe disproved it — see the register's Correction field | XS | ✅ **DONE 2026-07-26** — DTO + `@Valid` + in-tenant committee check; 9 tests, suite **2,469/0**, live 6/6 (500→200; missing dates→**400 naming the fields**; forged `id`/`organisationId` ignored; 403s hold). ⬇ **Narrowed a third time:** the FE already gated both dates bilingually, so the platform's own UI never hit the 500 — only API callers did | — |
| **F2-26** | new | **No roster seat is linked to a platform user** (`member_user_id` NULL on all 31 rows), so nobody can cast their own vote — only the secretariat on-behalf path works. Fix: optional `memberUserId` on the DTO + in-tenant validation, a `POST /terms/{id}/link-user` endpoint *(does not exist yet — guard, audit, re-link and clear semantics all undefined)*, and a linkage report distinguishing **unlinked** from **external** (a director with no login must not read as a gap). ⚠ **The dev-persona links must ship as a SEED SCRIPT, never in V318** — a Flyway migration name-matching personas would fabricate governance identities on real tenants, which §7 forbids. `member_user_id` has existed since **V154**, so V318's "link" half is at most an FK/index and possibly nothing | S | ✅ **DONE 2026-07-26** — three-state seat (`LINKED` / `EXTERNAL`-declared / `UNLINKED`), `POST /terms/{id}/link-user`, `GET /linkable-users`, `GET /committees/{id}/linkage`, FE column + voting-coverage KPI + gap card + real user picker, dev links via **seed script** as required. Suite **2,504/0**, live **32/32**, browser-verified in both languages. **Closing probe:** `so_officer` cast their *own* vote on COM-RISK — `on_behalf=0`, `recorded_by_user_id=8` — where the same call returned 403 before. ➕ **Three things the row did not anticipate:** the third state needed *schema* not a report filter; `appoint()`'s `memberUserId` had **no validation at all** (F2-25's probe could not catch it — it tested the two fields that *were* defended); and one person holding two ACTIVE seats on one committee would have double-counted in F2-28's denominator → now 409 | **V318** |
| **F2-38** | new (found by F2-26) | **`UserPicker` is backed by a hardcoded demo directory.** `lib/userDirectory.ts` is 146 lines of invented users (`usr-001`, `ceo@org.sa`, fake mobiles) whose ids do not exist in `iam.users`; `components/ui/UserPicker.tsx` reads it. It was the obvious component to reuse for the F2-26 seat picker and would have linked **governance seats to fabricated identities** — the very defect F2-26 describes, committed by its own fix. Not used: a real `ADMIN:WRITE`-guarded endpoint was built instead. **`WorkflowActorSelector` was checked and is clean** (binds real `u.id`). Consumers to fix: `ActivityFeedDrawer`, `NotificationOutboxPage`. F2-02's class, narrow scope | S | ⬜ | — |
| F2-13a | F2-13 | **Grant-tightness report** — roles-per-permission, **in both directions**: flag any business-write permission held by >50% of roles or by a read-only/external/machine persona (`WORKFLOW_WRITE` is in 55 of 57), **and any catalogued permission no role holds at all** (`ADMIN:WRITE` is in 0 — a dead permission is an unreachable feature) | S | ⬜ | — |
| F2-22a | F2-22 | Mass-assignment **audit script** over the 216 entity-bound `@RequestBody` sites + `@JsonIgnore` on `id`/`organisationId` in a mapped superclass as the immediate mitigation | S | ⬜ | — |

### Wave 1 — the spine (weeks). Everything durable attaches to the module registry.

| ID | Src | Action | Size | Status | Migration |
|---|---|---|---|---|---|
| **F2-01** | F2-01 | **Module registry** `module-registry.yaml` — seeded from the *verified* `SOURCE_MODULE_INVENTORY.md`; per module: owner, SKU, maturity, tier, schema, permission prefix, test floor, FE route root, docs. **CI fails** on package↔registry mismatch, unmet floor, schema disagreement, or any document stating a contradicting module count | M | ⬜ | — |
| **F2-03** | W3-3 | **Tier-1 test floors** on the regulator-facing modules, in order: `outsourcing`, `nca_ecc`, `sharia`, `data_officer`, then `openbanking`, `dataclass`, `rrp`. Every state transition + authorisation branch, plus one live HTTP probe per write endpoint | L (parallel) | ⬜ | — |
| **F2-05** | F2-05 | **Permission conversion** — catalogue permissions for the 21 role-guarded modules, convert `hasAnyRole` → `hasPermission`, grant to exactly the roles previously named (**behaviour-identical on day one**); add the **tightness ratchet** (`hasAnyRole` in `module/**` → 0) | L (S per module) | ⬜ | see §4 |
| **F2-06a** | F2-06 | **Schema-ownership enforcement test** — every `@Entity` schema == its registry schema. Cheap, permanent, stops new violations | S | ⬜ | — |
| **F2-08b** | W4-1 | **Ratcheted boundary test** over all 58 packages, seeded from the graph **as measured at seeding time** (754 peer imports · 128 directed pairs · 13 bidirectional at `ff087c7c` — the earlier 734/14 predates F2-08a); only allowed to decrease. ⚠ **Cannot be seeded until the kernel-import question is settled** — see F2-34 | S | ⬜ | — |
| W0-6 | D-3/D-4/F2-19 | **Stamp the doc corpus** — `HISTORIC` banner on the 7 root snapshots (`STATUS.md` 3,119 lines first) + the 158 `docs/` files; decide which Saudi pack generation governs and stamp the other `SUPERSEDED BY`; replace its dead `feat/pdpl-extensions` anchor with a SHA | S | ⬜ | — |
| F2-19b | D-5 | Run the measurement scripts + `doc_register.py` **in CI** so the baseline and register cannot drift | S | ⬜ | — |
| W1-5 | F2-18 | **Golden-path committee integration test** through the real HTTP stack + DB: constitute → refer → vote with a recusal → quorum → approve minutes → verify the e-signature → assert the change advanced and the ledger recorded it. Retain the decision as `[DEMO-EVIDENCE]` (never `[REVIEW-TEST]`) | M | ⬜ | — |
| W1-6 | — | Tenant-isolation test pack for the remaining module families (committee engine ✅ done) | M | 🔶 partial | — |

### Wave 2 — make the commercial model real *(D-3 answered by ADR-013 — but now **blocked on F2-35**, the SKU-catalogue ratification: W2-0 and F2-04 cannot be written until one catalogue wins)*

| ID | Src | Action | Size | Status | Migration |
|---|---|---|---|---|---|
| **W2-0** | ADR-013 | **Grandfather licence backfill — must land before ANY `@RequireModule` annotation.** Orgs 2/3 hold one licence row each, and the aspect is fail-closed. **Grant-all-then-tighten**, not license-what-has-data (which false-negatives on read-only, empty and seasonal usage → 403s for paying customers). Also fix new-org provisioning: nothing creates licence rows on organisation creation, so post-sweep every new tenant is born blacked out (F2-32) | S | ⬜ **blocked on F2-35** | **V320** |
| **F2-04** | F2-04 | **Entitlement rollout** — extend `KNOWN_MODULES` 20 → 58 from the registry; `@RequireModule` at class level on every business controller; wire the existing `moduleEnabled()` FE helper into navigation + route registration; **licence backfill migration before enforcing**; cross-tenant test. ⚠ **The cited fixture does not survive W2-0:** "orgs 2/3 are already seeded for exactly this" is true today (one `SOCOPS` row each, verified in dev *and* prod) but grant-all-then-tighten removes it, and per F2-32 nothing provisions licences at org creation, so a fresh org is fail-closed on everything. **The test must create its own org + licence rows** | M | ⬜ | **V320–V321** (see §4) |
| F2-06b | F2-06 | Schema moves, in ownership order: **`integrations` out of `platform` first**, then `controlmap`. *(The `incident`→`cyber` question is **dissolved** — D-4/ADR-013: `incident` joins the Cyber & SOC SKU and the package does not move.)* Compatibility view for one release + rollback recipe | M each | ⬜ | V332+ (see §4) |
| **F2-09** | F2-09 | **Schema-convergence CI job** — Flyway `V1`→head on an empty DB, dump, diff against the Python-bootstrapped dev schema; fail on any un-allow-listed difference | M | ⬜ | — |
| F2-13b | F2-13 | Tighten the over-broad grants the Wave-0 report surfaces, starting with `WORKFLOW_WRITE`; per-tenant impact query before each change | M | ⬜ | V332+ (see §4) |
| **F2-14** | F2-14 | **`ControlHealth` SPI** — each control declares its required configuration and reports `IN_FORCE │ NOT_CONFIGURED │ DEGRADED`; Control Assurance dashboard + `/actuator/health`; audit the 8 config-gated candidates (authorisation matrix, freeze windows, SLA policies, quorum policies, workflow gate maps, `ck_ca_source`, evidence-mandatory flags, escalation targets). **Generalises `ChangeGovernanceHealthService` — and is saleable** | M | ⬜ | — |
| F2-22b | F2-22 | DTOs for the highest-risk entity-bound endpoints (approval state, tallies, statuses); ratchet the count down | L | ⬜ | — |

### Wave 3 — Change Enablement as a first-class module *(see **§6c** for the target structure)*

**Elevated 2026-07-25 at the owner's direction:** Change Enablement becomes a properly structured module of
its own — change record + lifecycle + authority + **CAB** + **committee integration** + **workflow** — and is
lifted out of the Risk grouping in the UI. §6c specifies the structure; this table sequences it.

| Stage | Src | Action | Gate | Status | Migration |
|---|---|---|---|---|---|
| **CE-0** | new | **Lift Change out of the Risk nav group** — `Sidebar.tsx:106` places `/changes` inside `// 1. Risk Management (CRO)`. Promote to its own `navChange` group with the CAB, calendar/FSC and decision-register entries beneath it. **This is the whole of "not under risk"** — the backend was never under risk (§6c). ⚠ **Scope:** the CAB and decision-register screens do not exist until S7/CE-13, so CE-0 ships the group with the entries that resolve **today** (register, calendar/FSC) and adds the rest as they land — building nav-to-nowhere is the F2-02 anti-pattern. Also amend `UX_REDESIGN_BLUEPRINT.md:68` in the same commit (§6d) or Wave 4 re-files Change under "Governance & Administration" | none | ⬜ **do first — XS** | — |
| **S5** | CE-1 | **Module restructure** `module/change_mgmt` → `module/change` with the 11 sub-packages of §6c + SPI ports (`CommitteePort`, `IncidentPort`; **`EvidencePort`/`WorkflowPort` deferred to S8 where a real caller exists** — a port with no consumer is the F2-14 anti-pattern) + `ChangeModuleBoundaryTest` + FE `pages/change/` workspace skeleton (**keep `mockChanges.ts` — X-7**) | DF-3, S2, S3 | ⬜ | — |
| **S5** | X-2 | **Extend `ck_change_status` 14 → 17** (`CAB_DEFERRED`, `MORE_INFO_REQUIRED`, `PENDING_RATIFICATION`). Runtime-breaking if any CE state code ships first | — | ⬜ | **V320** *(see §4 — reassigned again: V318 went to F2-26, V319 to F2-28)* |
| **S6** | CE-3, CE-15 | **CAB as a governed body** — `cab_profile`, `cab_member_role`, charter/ToR, term lifecycle, quorum policy (**per-committee `quorum_pct` semantics + required-roles-present**, so a security-impacting change cannot pass a quorate CAB with no security member); CAB permissions **catalogued and guarded in the same migration** (the F2-23 lesson) | S5, **F2-28 closed**, **W1-5 green**, **D-1 answered** | ⬜ | **V322–V323** (see §4) |
| **S7** | CE-4…CE-7, CE-14a | **CAB meeting cycle** — agenda auto-build from the CAB queue · decision write-back + conditions register · ECAB ratification SLA · PIR object (**add `CHANGE_PIR` to `ck_ca_source` in the same migration**) · agenda/minutes exports · **state which decision mode is authoritative per change type** (meeting-bound minutes vs asynchronous electronic vote — leaving it ambiguous recreates F-014's two-answers defect) | S6, **D-2 answered** | ⬜ | **V324–V327** (see §4) |
| **S8** | CE-9, CE-2, CE-10, CE-8, CE-11, CE-12, CE-14b | Evidence via `EvidencePort` (+ `ConditionsSatisfiedGuard` requires **verified** evidence) · change-model catalogue · **workflow adoption (15th module)** · FSC/PSO · unauthorised-change register · KPI pack · decision-register export | S7 | ⬜ | **V328–V331** (see §4) |
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
| **F2-11** | — | ⬆ **Partly pulled into Wave 1 by the council** — it was silently moved from Wave 1 to Wave 4 with no stated reason. Wave 1 takes the ESLint ratchet + the pages on the five persona journeys; the rest stays here. **i18n convergence** — ESLint baseline first (**`eslint_configured: false` today**), then no-bare-Latin-JSX and no-`lang === 'ar'`-in-`pages/**` as **ratchets**; migrate the 439 hardcoded strings; fix the 49 zero-Arabic pages; per-module Arabic-coverage CI report | S→L | ⬜ |
| **F2-A11Y** | Part 5 §7 | Fix the **68 `color-contrast` (WCAG 1.4.3, serious)** instances **at the design-token level**, not per component; re-run the existing axe harness; wire `@axe-core/playwright` into CI as a ratchet; then extend to focus/keyboard/screen-reader (genuinely unmeasured) | M | ⬜ |
| **F2-12** | — | `ndi` containment — characterisation tests over its **82 writes** *first*, then split `NdiPage.tsx` (4,969 lines) along tab boundaries | L | ⬜ |
| F2-16 | — | Split `EsgPage.tsx` (5,582) — **before** `ndi`, since `sust` has 200 tests to catch mistakes; page-size ratchet (no new file > 800 lines, existing may only shrink) | M | ⬜ |
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
**Numbers are reserved in DELIVERY order, not in ID order.** This was wrong in the previous revision and
the review council caught it: CE-2 held `V320` while being delivered in **S8**, after CE-3…CE-7 (`V321`–
`V325`) would already be applied — so `V320` would have been authored *below* an applied migration and
**Flyway would have skipped it**, exactly the rule stated below. Reserved by wave:

| Migration | Item | State |
|---|---|---|
| **V318** | **Roster→user link — F2-26.** Three-state seat (`no_platform_user` + `_basis_en` + `ck_cmt_no_platform_user`) and the filtered index `IX_member_term_user` on `(organisation_id, member_user_id, renewal_status)`, which `findActiveCommitteeIdsForUser` had no index for and which becomes per-request once seats are linked. **Links nobody** — see the migration's own header for why a name-matching backfill would fabricate governance identities | ✅ **dev ×2** (2026-07-26; second pass 0 changes) · **prod pending the W0-2b merge** |
| **V319** | **Quorum integrity — F2-28 / DF-5.** Persisted quorum facts (`quorum_source` + denominator/present/required/computed_at), attendance attribution (`attendance_recorded_by/at`), seat-bound attendance rows (`member_term_id`, `is_guest`, `ck_cma_seat_or_guest`, unique `(meeting, seat)`), and the **labelling** of 17 pre-existing asserted quorums as `ASSERTED_LEGACY`. Flips nothing: `quorum_met = 1` still holds on the same 17 rows. *(Was V318 last revision; F2-26 shipped first and took it — the fifth reassignment, always for the same reason: reservations are ID-ordered wishes, Flyway is version-ordered fact.)* | ✅ **dev ×2** (2026-07-26; second pass 0 changes) · **prod pending the W0-2b merge** |
| **V320** | **Export permission enforcement — F2-27.** Catalogues `AI:EXPORT` (which did not exist while 41 roles held `AI:READ`) and repairs the two measured grant gaps the impact query found: `CHANGE:EXPORT` for the 4 assurance roles (42 → 46 grants) and `BCM:EXPORT` for CISO/CFO (17 → 19), the two roles the BCP-binder role list allows but the catalogue did not. **Grants only — no schema change, and no role loses a grant here**; the one intentional revocation (VIEWER keeps `CHANGE:READ`, never gains `CHANGE:EXPORT`) is an omission, not a delete | ✅ **dev ×2** (2026-07-26; second pass 0 changes) · **prod pending the W0-2b merge** |
| V321 | `ck_change_status` 14 → 17 — X-2 *(reassigned **six** times: V316 → V317 → V318 → V319 → V320 → V321)* | ⬜ |
| V322 | **W2-0 grandfather licence backfill** — must precede any `@RequireModule` sweep | ⬜ |
| V323 | Licence catalogue → data (`KNOWN_MODULES` out of `Set.of`) + entitlement rollout — F2-04 | ⬜ |
| V324–V325 | **S6** — CAB as a governed body: `cab_profile`, `cab_member_role`, charter/ToR, quorum policy, CAB permissions (CE-3, CE-15) | ⬜ |
| V326–V329 | **S7** — meeting cycle: agenda, decision write-back + conditions, ECAB ratification SLA, PIR (+`CHANGE_PIR` in `ck_ca_source`) (CE-4…CE-7) | ⬜ |
| V330–V333 | **S8** — change models (CE-2 → V330), FSC/PSO (CE-8 → V331), evidence (CE-9 → V332), unauthorised-change register (CE-11 → V333) | ⬜ |
| V334+ | Schema moves — F2-06b · grant tightening — F2-13b · audit-ledger integrity — F2-31 · export row-count/classification — F2-41 (if it needs one) | ⬜ |

⚠ **This table has now been renumbered by delivery six times.** That is the scheme working as designed —
but it is also the strongest argument for the structural finding §6d accepted: a hand-maintained number
ledger across four documents will keep drifting. The mechanical check below is the only reliable authority.

**Reservations are advisory; ordering is not.** A number is only real when the file exists. Treat every row
above as *intent*, and immediately before creating a file run:

```bash
ls tss-grc-backend/src/main/resources/db/migration/sqlserver | sed 's/^V\([0-9]*\)__.*/\1/' | sort -n | tail -1
```

**Never author a number below an already-applied migration** — Flyway applies in version order and would
skip it. Corollary the council had to point out: **if item A is delivered before item B, A takes the lower
number**, no matter which has the earlier ID.

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
| **F-002** | Generic transitions bypass approval semantics | `PARTLY CONTRADICTED` | **The exploit is closed; the design debt is real.** `CAB_APPROVED` now requires a quorate committee resolution (Track D) *or* ≥N recorded `APPROVE` votes with zero `REJECT` (CM-C2 + the CH-17 matrix); `EMERGENCY_APPROVED` requires `changeType = EMERGENCY`. So a `CHANGE:WRITE` holder can *advance* a legitimately-decided change but cannot *manufacture* the decision. The literal claim — a generic transition guarded by `WRITE` reaching privileged states — is still true. **Critical → P1, downgrading to P2 only when F2-28 closes.** The council overturned an unconditional P2: `CAB_APPROVED` has two legitimating legs — recorded votes (sound after DF-4) *or* a quorate committee resolution — and the second leg rests on `quorumMet`, which F2-28 proves is caller-authored. Until V319 lands, one of the two paths that make this "closed" is itself forgeable | **NEW F2-29** (below) |
| **F-003** | Committee votes **and quorum** can be manufactured | `VERIFIED` — **half open, and the open half is P0** | Vote identity closed (`f3ea92fc`, V315, V317). **The quorum half was never closed and this register's first pass missed it.** Attendance is caller-authored free text, the denominator is computed *from that sheet*, and `quorumMet` can be set directly with zero attendance | **DF-5 / F2-28 — new P0** |
| **F-004** | Package licensing is not platform-wide | `VERIFIED` | Exact: 22 `@RequireModule` controllers of 252, all `socops`. Un-gated by ADR-013; needs W2-0 backfill first | **F2-04** (Wave 2) |
| **F-005** | Module boundaries are not executable | `VERIFIED`, numbers corrected | Re-measured: **754** peer imports (audit: 734), **128** directed pairs (136), **13** bidirectional (14). Volume up, tangle down — and still no architecture test | **F2-08b** (Wave 1) |
| **F-006** | IA exposes implementation sprawl | `VERIFIED` | **329** routed pages in a 925-line `App.tsx` | **F2-07 / F2-07c** |
| **F-007** | Arabic completeness claims exceed enforcement | `VERIFIED`, and sharper than stated | `ChangeRequest.titleEn` is `nullable = false`; **`titleAr` has neither `nullable = false` nor `@NotBlank`**. So `CLAUDE.md` house rule 7 — *"Arabic names are backend-mandatory"* — is **asserted and not enforced**. A documented platform invariant that no layer checks | **NEW F2-30** (below) |
| **F-008** | Export authorisation violates least privilege | `VERIFIED`, **far broader than stated** | 24 `MODULE:EXPORT` permissions catalogued and granted to up to **55 of 57 roles**; **exactly one endpoint enforces any of them.** Every register, the board pack and the regulator ZIP are `READ`-authorised. The mirror image of F2-23 | **F2-27 — new P1** |
| **F-009** | Governance core lacks adversarial test coverage | `OVERTAKEN` *(was labelled `CONTRADICTED` — the council was right that this was self-serving: several of those test classes were written the same day the finding was re-judged, so the audit was correct when its evidence was gathered)* | **6 committee test classes, 99 test methods** — forged actor, roster eligibility, quorum, recusal/COI, tenant isolation, authorisation separation. It was *already* self-contradicted on the day it was written: the sibling document counted 19 recusal + 19 tally tests. Residual gap is narrow: no `EmergencyChangeRatificationTest`, and the two-principal probes are scripted but not in CI | **W1-5** (not a finding) |
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
| Security / control integrity | 1.8 | **2.3** | 2 of its 7 critical weaknesses closed with mutation proof and live probes, 1 mitigated — but a live P0 forgery chain on the governance backbone (F2-28), a platform-wide least-privilege inversion (F2-27) and 214 unaudited mass-assignment sites do not support 2.6. Council: "2.2–2.4 is the defensible band"; I had written "movement is real but narrow" and then awarded +0.8 |
| Commercial packaging | 2.4 | **2.6** | still 22 of 252 controllers, but the decision is made (ADR-013) and the mechanism is proven fail-closed |
| Quality / operations | 3.2 | **3.4** | +144 tests, adversarial governance tests, mutation-proven guards; operational evidence still absent |
| Enterprise architecture | 2.6 | **2.6** | unchanged — pairs and cycles improved marginally, no architecture test yet |
| **Weighted** | **3.0** | **≈3.05** | Movement is real but narrow. **Still `Qualified`** |

**Market verdict, changed in one place:** the audit made a controlled pilot conditional on Criticals
closing. F-001 is closed and F-002 is mitigated — but **F2-28 is a new open Critical**, so *"pilot after
Criticals close"* still holds and now has a specific, costed blocker: 3–5 days, sequenced behind F2-25 (2 h)
and F2-26 (1 d). General availability, government final acceptance and per-module sale verdicts are
**unchanged**.

### New backlog items created by this reconciliation

| ID | Src | Action | Size | Status |
|---|---|---|---|---|
| **F2-28 / DF-5** | F-003 | **Quorum integrity — P0.** Attendance rows reference a roster seat, `votingMember` from the seat, denominator = active voting roster (not the sheet), refuse client `quorumMet`, snapshot the roster, audit sheet replacement. **Round-2 review added three things the spec missed:** (a) `recordAttendance` has **no status check**, so a sheet can be replaced on a `MINUTES_APPROVED` meeting and `recomputeQuorum` rewrites `quorum_met` **after** the e-signature — which hashes only the minutes text, not attendance; (b) the legacy free-text `attendeesJson` channel still exists on `completeMeeting` and **32 dev meetings carry it** — a second caller-authored attendance surface the fix as written leaves open; (c) **legacy data must be dispositioned**: measured **17 meetings with `quorum_met = 1` and zero attendance rows, 15 of them already `MINUTES_APPROVED` and e-signed**, plus **20 resolutions whose tallies are seeded scalars with zero vote rows** — any code path calling `recomputeTally` rewrites those to `0/0/0 DEFERRED`. A retroactive "no attendance ⇒ not quorate" rule would strand or flip approved history, so V319 must choose: a `quorum_source ASSERTED_LEGACY / COMPUTED` column, freeze-by-status, or annul | M→L | ✅ **DONE 2026-07-26** `V319`. Suite **2,520/0**, live **29/29**, browser-verified. **Headline probe:** record only 2 of a 4-voting-seat committee → `totalVoting=4`, required 3, **NOT quorate**; the same request produced 2/2 → quorate before. ⚠ **The measurement reframed the finding:** `committee_meeting_attendee` held **0 rows**, so CH-14's deterministic path had *never executed* and all 17 quorate meetings got their flag from `quorumMet` on `complete` — the **fourth** consecutive finding (after F2-23/F2-25/F2-26) where the mechanism was right and its data never existed. All three round-2 additions closed: status guard (409 after approval), `attendeesJson` refused with a message not ignored, replacement audited with what it replaced. Legacy **labelled `ASSERTED_LEGACY`, never flipped** — the 15 e-signed approvals stand, but a meeting still awaiting approval cannot pass on an assertion. Also closed **F2-39** (below). *The old `CommitteeQuorumMinutesTest` asserted the defect — 18 green tests that fixed the input's shape and so could only verify the arithmetic, never the authority; rewritten* |
| **F2-27** | F-008 | **Enforce `MODULE:EXPORT` on every export endpoint** + per-tenant impact query first + a ratchet test + Export History auditing. ⚠ **Round-2 review: the scope is 17 controller files / 24 `CONTENT_DISPOSITION` sites, not one** (`bcm` ×4, `sust` ×5, `cyber`, `ndi`, `socops`, `ai`, `attachment`, `iam`, `reporting`) — the 1-day estimate assumed a single file, so **S → M**. Three rules must be decided first: the AND/OR composition for cross-module exports (board pack spans 6 domains; an AND-chain 403s users who can export today), whether single-attachment downloads count as exports (recommend: **no**), and the POST-body exports. **The 403 leg of the DoD is unprobeable** — zero roles hold READ-without-EXPORT for RISK/COMPLIANCE/REPORTING, so it needs a `[REVIEW-TEST]` role. Grants migration is **unreserved** | M | ✅ **DONE 2026-07-26** `V320`. Suite **2,522/0**, **export ratchet 40 discovered / 0 unenforced**, live **31/31**, browser-verified AR+EN. **Scope was 40 handlers / 18 controllers, not 24/17** — `CONTENT_DISPOSITION` literals under-count by 15 (`ExportController` funnels 19 handlers through 4 helpers) and a source regex cannot see `@PreAuthorize(READ_GUARD)` constants; **0 of 40 were unguarded** (a first-pass scan of mine wrongly said 8 were). **Both of the round-2 design calls were falsified by the impact query:** the AND-chain would 403 **8 of 18** `REPORTING:EXPORT` holders (CISO, CFO, DPO, PRIVACY_MANAGER…) → adopted rule is *keep each guard's boolean shape, lift only the action*, which is access-preserving because 11 of 14 modules grant READ and EXPORT identically; and the 403 leg **was** probeable — `viewer` holds `CHANGE_READ` without `CHANGE_EXPORT`, so 5 real denials needed no `[REVIEW-TEST]` role. Three modules needed a decision: `AI:EXPORT` **did not exist** (the F2-23 trap for a fourth time), `CHANGE:EXPORT` missing from the 4 assurance roles (granted; VIEWER deliberately not — the prover), `ADMIN:EXPORT` **not** enforced on the one ADMIN export because it is a **blank form template** (allowlisted; lifting it would break 14 roles for zero DLP gain). Also found: **2 role names in `hasAnyRole` lists that do not exist in `iam.roles`** (`MODEL_OWNER`, `BOARD_MEMBER`) and the 40th endpoint the reflection scan caught — the BCM evidence ZIP, *"the most sensitive export on the platform"* per its own comment, guarded by `BCM:READ`. Export History auditing (rec #4) split out as **F2-41** |
| **F2-29** | F-002 | Split `POST /{id}/transition` into named commands with state-specific permissions; `CAB_APPROVED`/`CAB_REJECTED`/`EMERGENCY_APPROVED` require `CHANGE:APPROVE`, not `WRITE`. **P1 until F2-28 closes, then P2** — the exploit is blocked by two legs and one of them (a quorate committee resolution) rests on the forgeable `quorumMet`; design debt only once the quorum leg is sound | M | ⬜ |
| **F2-39** | new (found closing F2-28) | **Minutes approval was guarded by the drafting permission, not the approving one.** ADR-014 assigns `ADMIN:WRITE` "manage … minutes" and `ADMIN:APPROVE` **"approve minutes"**; the code guarded `approve-minutes` with `WRITE` — so the **3** roles that draft the minutes were the only ones who could approve them and the **15** the ADR entitled could not, with **no separation between authoring a meeting's record and approving it**. ⚠ A test of mine pinned the divergence (`doesNotContain(ADMIN','APPROVE')` — mechanically true, substantively wrong). Fixed: guard → `ADMIN:APPROVE`; **whoever recorded the attendance cannot approve the minutes resting on it** (409); per-capability assertions plus the reverse check that the vote capability cannot reach `recordAttendance` | XS | ✅ **DONE 2026-07-26** with F2-28 — live: `admin` recorded then got 409; `so_officer` (APPROVE, no WRITE) approved, a call that was 403 before | — |
| **F2-40** | carried from F2-28 | **20 resolutions carry seeded scalar tallies with zero vote rows.** `vote_for/against/abstain` were seeded directly, so any path that calls `recomputeTally` rewrites them to `0/0/0 DEFERRED` — silently replacing recorded governance outcomes with an empty tally. Same class as F2-28 on the *numerator of a resolution* rather than the quorum of a meeting, and the disposition should match: a `tally_source ASSERTED_LEGACY / COMPUTED` marker rather than a backfill or a flip. Belongs with **F2-29** (named change commands), which is where resolution outcomes are consumed | S | ⬜ |
| **F2-41** | carried from F2-27 | **Export History records that an export happened, not what left the building.** `ExportAuditInterceptor` already fires on every file-returning endpoint, but it captures no **row count** and no **data classification**, so the audit trail cannot answer the only two questions a DLP or PDPL enquiry asks: *how much* was extracted, and *how sensitive was it*. F2-27's recommendation #4 asked for both; it is a separate concern from authorisation (40 endpoints, each needing to report its own row count through the interceptor) and was deliberately not bundled. Now that `MODULE:EXPORT` is enforced, an export event also has a meaningful *authority* to record alongside the volume | S | ⬜ |
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
| **Committee engine** | `admin/committee` — **kernel**, in the mandatory Base Platform SKU | Generic: charters, member terms, meetings, attendance, quorum, resolutions, e-signed minutes, gate→committee routing. Consumed by **5** external packages — `bcm`, `change_mgmt`, `policy`, `risk`, `workflow` *(`grep -rl "import com.tss.grc.module.admin.committee" src/main/java`)*. ⚠ **An earlier revision of this row claimed "14 modules" — that was the *workflow*-engine adoption count transplanted into a sentence about the *committee* engine, an ADR-016 R5 violation committed the day ADR-016 was accepted.** The conclusion is unchanged and rests on a stronger fact: the **kernel workflow engine structurally depends on committee** — `WorkflowRequestService` implements `COMMITTEE` as a first-class recipient type with roster fan-out and the SLA scheduler reads `CommitteeMemberTerm` — so the engine cannot move into a commercial package without dragging the kernel with it. Four SKUs consume committee referrals |
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

## §6d Council review — 2026-07-25 (three rounds, ten Fable 5 seats, adversarially briefed)

Three rounds of reviewers with deliberately conflicting mandates read this plan against the repository;
several were briefed to argue *against* positions already committed here. Aggregate verdict:
**ACCEPT-WITH-CONDITIONS as a programme** — and round 3 fairly objected that the previous revision
enumerated *no conditions*, making the verdict unauditable. **The conditions are:**

1. **O-2 merge authorisation**, without which no provenance fix reaches production (§1b).
2. **F2-28 closed** before S6 — non-negotiable, now sized M→L with a 16-item acceptance checklist.
3. **F2-35 ratified** before W2-0/F2-04 are written.
4. **A retained acceptance-evidence artefact** per slice (probe output + environment fingerprint),
   replacing evidence that currently lives only in commit messages.
5. **W0-7 shipped** before this plan again asserts what a deployment contains.
6. **The four acceptance gates with no owner** (pen test, witnessed restore, WCAG conformance, Saudi
   content provenance) either get an owner in §1b or are restated as accepted risks.

Two corrections to the previous summary, both round 3's: the defects were **not** "almost entirely in the
status layer" — items 7 and 9 below (an unconditional downgrade of a forgery-adjacent finding, and a +0.8
self-rating with an open P0 on the same surface) are **judgment** errors, and calling them clerical
flatters the process. And the claims that verified were the *code* claims: **no round verified an artefact
until round 3, which found the plan's deployment claims false** (§1).

### Errors of mine they caught, now fixed above

| # | Error | Fix |
|---|---|---|
| 1 | **§6c claimed the committee engine is "used by 14 modules". It is 5.** That was the *workflow*-adoption count transplanted — an ADR-016 R5 violation (uncited number) committed the day ADR-016 was accepted | §6c corrected, command cited, conclusion re-grounded on the kernel workflow engine's structural dependency |
| 2 | **`V318` was claimed by two rows** — Wave 3 for `ck_change_status`, §4 for F2-28 | Wave 3 → V319; **then both moved again** when F2-26 shipped first and took V318 (F2-28 → V319, Wave 3 → V320). The collision was a symptom; the cause is that reservations are ID-ordered and Flyway is delivery-ordered, so *any* reordering of delivery renumbers the tail |
| 3 | **The §4 reservation scheme violated its own Flyway rule** — CE-2 held V320 while delivered in S8, *after* V321–V325 would be applied, so V320 would have been skipped | §4 re-reserved in **delivery order** |
| 4 | **§8 omitted F2-28**, the plan's own highest-open P0, while listing six lesser items | Inserted as slice 3 |
| 5 | **F2-08a was already complete** (`313dd6dd`, `reporting` fan-in 25 → 0) yet listed `⬜` in Wave 0 *and* as next-action #6 in a §8 rewritten the same day — the sixth instance of F-013, mine | Marked ✅; removed from §8 |
| 6 | **The header promised every `F2-*` row is specified in the assessment.** F2-25…F2-30 are not in it — F2-28's only spec was one table row | Header scoped; register named as the source above F2-24 |
| 7 | **F-002's P2 downgrade was unconditional** when one of its two legitimating legs (quorate resolution) rests on the forgeable `quorumMet` | Now **P1 → P2 on F2-28 closure** |
| 8 | **F-009 was labelled `CONTRADICTED`** by tests written the same day | Relabelled `OVERTAKEN` |
| 9 | **Security re-rated 1.8 → 2.6** while a P0 forgery chain was open | **2.3** |
| 10 | **Wave 2 was "un-gated" in §2 and "(gated)" in §3**; F2-06b still posed the dissolved D-4 question | Both corrected |
| 11 | Four stale migration cells, a stale boundary seed (734/14), the tree stamp 10 commits behind, DF-4 "12 vs 13 tests", and number drift (routes 328/329, pages 5,583/5,582 and 4,970/4,969) | All normalised to the re-measured values |

**A finding of mine the second round disproved.** Round 2's spec-readiness seat live-probed F2-25's security claim — `{"id": 424242, "organisationId": 2}` as `compliance` → **200, saved as `id: 78, organisationId: 1`** — and `CommitteeMemberRegistryService.appoint()` has nulled `id` and stamped the tenant since `8373d6f8`, with a comment saying so. **F2-25's cross-tenant mass-assignment claim was false and is withdrawn; the finding drops P1 → P2.** The 500 is real but caused by two NOT NULL date columns and no `@Valid`. I diagnosed it from the controller signature plus an observed 500 and never opened the service, which was one file away and refuted the inference. *A defect inferred is not a defect verified* — the mirror of this review's own "not found is not does not exist" lesson.

**One round-1 council claim I checked and rejected:** the delivery seat reported that the pre-swap safety jar
does not exist and that the `Move-Item` restart would destroy the only copy of the running jar. **False.**
`D:\Deployment\tss-grc\backup\jar-before-restart-20260725.jar` is present at 104,933,172 bytes — byte-size
identical to the live jar — alongside three further copies from earlier deploys
(`.pre-authfix-20260723_201100`, `.pre-S0-20260725_173748`, `.pre-deploy-*`). Its directory listing missed
them. Recorded because a subagent's confident negative is not evidence.

### New findings the council produced

18-field specs are **not yet written** for these; they are stated here and must be added to
`FINDINGS_REGISTER_V2.md` before any is scheduled.

| ID | Finding | Sev | Source |
|---|---|---|---|
| **F2-31** | **The audit ledger has no tamper-evidence of its own.** Committee minutes carry a SHA-256 signature; `audit.log` — which every control cites as proof — has no hash-chaining, no WORM/retention policy and no protection from administrative deletion. F2-21 already demonstrated that this ledger will faithfully corroborate a forgery. NCA ECC **2-12** requires event-log protection and ≥12-month retention; also ISO 27001 A.8.15, SAMA CSF logging | **P1** — ⚠ *with a caveat round 3 added: hash-chaining an audit ledger is near-theatre while the DBA, platform admin and deployment operator are the same person on the same SQL box. The control is defeated by its own threat model until key custody separates, so pair it with an off-box or append-only sink or state plainly that it is deterrence, not prevention.* | GRC seat |
| **F2-32** | **The licence lifecycle has no data plane.** ADR-013 gates HTTP controller entry only. The aspect fails closed when `TenantContext` is empty off-request, so it **cannot** be extended to schedulers as designed — and today no scheduler, workflow fan-out, event listener or auto-CAPA path checks entitlement: unlicensed modules keep computing, breaching SLAs and raising CAPAs. De-licensing has no semantics either: `CommitteeReferral` rows live in the **`admin`** schema keyed by `sourceModule='CHANGE'`, so a lapsed licence leaves e-signed minutes and ledger entries referencing records nobody can open, with no answer for a PDPL erasure request. **Plus: nothing creates licence rows when an organisation is created**, so after a fail-closed sweep every new tenant is born blacked out | **P1** | Architecture seat |
| **F2-33** | ⬇ **P2** *(round 3: real but over-rated at P1 — nothing downstream consumes it as evidence, unlike F2-02's executive surfaces; P1 only if onboarding sits in a sales-demo path)*. **Onboarding step 3 "Invite teammates" is localStorage-only** (`tss-grc-ui-v2/src/lib/onboarding.ts:169-184`) — invited users are never created. This is F2-02's defect class on the **first-run journey**, and a `/mock/i` CI rule will never catch it because it fabricates local state rather than importing a mock file. Wire it to the user-admin API or cut the step | **P1** | Product seat |
| **F2-34** | **Kernel purity, and the rule contradiction that blocks F2-08b.** 40 kernel→business import lines (`platform`→`risk` 14, `privacy` 3, `bcm` 2, `ndi` 2, `audit_lc` 2, `privacy_lc` 2; `integrations`→`socops` 6, `cyber` 2; `dashboard`→`risk` 4, `compliance` 2, `incident` 1), concentrated in `platform/calendar`, `platform/completion/*`, `dashboard` and the `integrations` connectors. The kernel participates in **6** bidirectional pairs on the architecture seat's count, against the **13** the plan's own graph reports — the two use different methods and reconciling them is part of this item. Either way the kernel is the largest cycle participant, so ADR-002's "kernel boots alone" is unachievable and had **no backlog item**. And `MODULE_SEPARABILITY` §2 says kernel imports don't count while §6c's rule says only `common`/`security`/`platform`/`spi` are legal — under the first reading `CommitteePort` is unnecessary. **The boundary ratchet cannot be seeded until one reading wins** | **P1** | Architecture seat |
| **F2-36** | **CI never runs where the work happens.** `.github/workflows/ci.yml` triggers **only** on push to master and PRs to master — so the 20 pending commits, including DF-3 and DF-4, have **never executed in CI**; `tsc` is deliberately excluded (~45 errors); and the backend job defines no SQL Server service container, so "CI green" and the local 2,460-test green are not known to mean the same thing. Every item saying "CI fails / CI rule" (F2-01, W2-1b, F2-07b, F2-09, F2-19b, F2-11, F2-A11Y) assumes an enforcement point that fires only **after** merge | **P2** | Round 3 |
| **F2-37** | **The production jar ships `BOOT-INF/classes/db/seed/V999__seed_mock_data.sql`** — inert for Flyway (wrong location) but mock seed data inside the shipped artefact of a platform whose P0 list includes "fabricated data ships in the production bundle". Fold into F2-15 | **P3** | Round 3 |
| **F2-35** | **The SKU catalogue exists in three contradictory versions, and one conflict is commercial.** (A) `MODULE_SEPARABILITY_AND_SKU_MODEL.md` §6 — Base + 13, ratified by ADR-013. (B) `Product/PRODUCT_STRATEGY_PACKAGING_AND_SALES.md` — 18 packages that sell **`ACCESS_GOV`** and **`GOVERNANCE` (committees, board packs, signatures)** as *paid*, contradicting ADR-013 and §6c which place them in the mandatory **free** Base. (C) `LicensingService.KNOWN_MODULES` — 20 code-shaped codes. **7 packages appear in no SKU: `ndi` (7,717 LoC, 127 endpoints — the SDAIA/NDMO differentiator), `reporting`, `governance`, `board_pack`, `knowledge`, `workflow_submission`, `datagov`.** Org 1 already holds `EVIDENCE`/`REPORTING` licence rows for capabilities ADR-013 calls mandatory-Base. **W2-0 and F2-04 are unwritable until one catalogue is ratified** — the backfill content differs ~3× by grain | **P1 — blocks Wave 2** | Product + Architecture seats |

### Recommendations accepted, pending your call

- **Rename the SKU "Change Enablement" → "Change Governance & CAB".** Under the ITSM name it competes with
  ServiceNow/Jira on CMDB, service catalogue and release pipelines and loses on brand. What §6c actually
  builds is change *governance* — CAB quorum integrity, SoD-recusal, e-signed minutes, evidence-bound
  conditions, an unauthorised-change register — sold to the CISO/CAE/CCO as SAMA CSF and NCA ECC change
  control. Same code, different buyer, winnable deal. **Your decision (D-9).**
- **CE-0 and F2-07c contradict each other.** `UX_REDESIGN_BLUEPRINT.md:68` places `change_mgmt` inside
  workspace 12 "Governance & Administration" and there is **no Change workspace among the 12** — so
  Wave 4's registry-generated navigation would delete CE-0's group and re-file Change under an admin
  catch-all, silently reversing the owner's direction. **Fix in the same commit as CE-0:** amend the
  blueprint table (Change becomes workspace 13) and give F2-01's registry a `workspace:` field from day
  one, so the decision lives in data rather than in a `.tsx` file Wave 4 retires.
- **W1-5 (golden-path committee integration test) becomes a hard S6 gate** beside F2-28 — a CAB built on
  an engine proven only at unit level repeats F2-18.
- **Grandfather by grant-all-then-tighten, not by data presence.** "License every SKU a tenant has data
  in" false-negatives on read-only, configured-but-empty and seasonal usage → 403s for paying customers.
- **F2-01 becomes a two-stage registry.** v1 enforces only package↔entry parity, schema agreement and
  module-count claims. `sku`/`owner`/`test_floor` become enforceable as they are actually decided —
  otherwise a CI-enforced registry stuffed with placeholder SKUs is the F2-14 anti-pattern.
- **Pull i18n forward.** It was silently moved from Wave 1 (where the UX blueprint put it) to Wave 4 with
  no stated reason, on a product whose headline claim is Arabic-first with 49 zero-Arabic pages. Minimum:
  the ESLint ratchet plus the pages on the five persona journeys. Same for **F2-A11Y's token-level
  contrast fix** — days of work, parked behind two waves.
- **State a contention rule:** if Wave 1 and Wave 3 compete for the same engineer, **F2-03 Tier-1 floors on
  `data_officer` and `outsourcing` outrank S6–S8.** W3-5 ("no zero-test module in any commercial scope
  statement") makes the flagship Saudi SKU unsellable until F2-03 lands, so it is revenue critical-path.

### The structural finding — accepted, not yet acted on

Round 3's strongest argument is that this file's **error rate is a data-structure problem, not a diligence
problem**: the same author catches and creates these errors within hours. v2.2 is simultaneously a backlog
(§3), a status dashboard (§1), an owner register (§1b), a migration ledger (§4), a decision log (§6), an
audit adjudication (§6b), an architecture spec (§6c) and council minutes (§6d) — with the backlog living in
**four** places and **three sequences that disagree** (§6b: F2-25→26→28→27→30→29 · §6c: "CE-0 do now" ·
§8: CE-0 last, F2-30 absent). Six revisions in under three hours is how the Wave-3 cells, the W0-8 phantom
and the 328/329 remnant all shipped stale *on the evening the council fixed their predecessors*. Also
unfalsifiable: the preamble claims to merge "stages S0–**S9**", and **S9 is defined nowhere in the repository**.

**Accepted remedy (not yet done — it is real work, and half-doing it would make things worse):** one
machine-checkable backlog containing every open item exactly once (id, wave, size, status, blocked-on,
migration) that F2-01's CI can lint; §4 as the only migration ledger, with **migration numbers deleted from
§3**; review rounds appending to `FINDINGS_REGISTER_V2.md` only, never new plan sections; and §1's numbers
generated by script per F2-19b rather than typed. The plan already prescribes every one of these mechanisms
for *other* documents.

### Absences the council named that this plan had not consciously excluded

- **Data import / time-to-first-value.** Nothing anywhere imports an existing risk register, control
  library or obligation list. Every GRC displacement deal opens with "load our register"; pilots that
  start from a blank screen stall. Not in scope, not out of scope — simply absent.
- **The locked-module upsell surface.** F2-04 hides unlicensed modules; per-module SKUs on one deployment
  is an *expansion-revenue* architecture, so the merchandising half (a visible locked workspace with
  "licensed under X — request a trial") should exist. One component.
- **Mobile approvals and signatures** — the highest-frequency executive touch, with no item and no
  out-of-scope entry, which by this plan's own standard ("absence reads as a decision") it must have.
- **Day-zero empty states** across ~300 registers, and **nobody owns a curated demo tenant + script**.
- **Retention, disposition and legal hold** (PDPL storage limitation; Companies Law minute retention).
- **Witnessed restore/failover, an independent penetration test, and a WCAG conformance statement** have
  no owner — §7 calls them out of scope, which the GRC seat fairly reads as *silent rejections of three
  of the audit's own §11 acceptance gates*.
- **An acceptance-evidence dossier.** The §5 definition of done is strong engineering evidence but wholly
  **self-attested and unretained** — probe output lives in commit messages, mutation proofs leave no
  artefact. Adding a retained pack (probe output + environment fingerprint + reviewer sign-off) is
  actionable now; the *independence* half is a business decision, not a schedulable task.

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

**0. Resolve F2-28's open design questions first.** ⚠ *Corrected: a reviewer said F2-28's "whole
specification is one table row" and I repeated it — **false**. A full 18-field spec has been in
`Registers/FINDINGS_REGISTER_V2.md` since `c8f672dc`, with the endpoint inventory, the roster-snapshot
recommendation and the `quorum_pct` trade-off.* What is genuinely missing is narrower and must be settled
before V319 is authored: **is the denominator the active roster or those present** (charters differ, and the
register's own trade-off note flags it); how **external directors with no platform login** are represented;
what happens to the **20 existing resolutions** whose tallies were seeded rather than computed; how
apologies and non-voting invitees count; and whether the roster snapshot is a new table or a JSON column.

1. ~~**F2-25**~~ ✅ **DONE `55fcbe25`** — DTO + `@Valid` + in-tenant committee check. The
   foreign-`organisationId` probe this row demanded was run and **disproved the finding's security half**;
   the FE had also gated both dates already, so only API callers ever saw the 500.
2. ~~**F2-26**~~ ✅ **DONE 2026-07-26** — seats link to platform users, with an explicit third state for
   directors who will never have a login. Proven by the probe that had been failing since D-7: `so_officer`
   casting their **own** vote, `on_behalf = 0`. **Two of F2-28's five open questions are now answered by
   construction:** *"how are external directors with no platform login represented"* → the declared-EXTERNAL
   state, which also leaves the coverage denominator; and *"do apologies and non-voting invitees count"* →
   the linkage report already separates voting from non-voting seats, so the denominator question is now only
   about **presence**, not about **eligibility**.
3. ~~**F2-28 / DF-5**~~ ✅ **DONE 2026-07-26 `V319`** — was the highest open P0, and absent from this list until
   the council caught it. Roster-bound attendance, denominator from the roster, client `quorumMet` refused,
   arithmetic persisted, replacement audited. *(V318 went to F2-26 — Flyway applies in version order, so the
   number goes to whatever ships first, per §4.)*
   **All five open questions are now answered**, four by construction and one deferred with a reason:
   - *roster-vs-present denominator* → **the roster.** That is what quorum means; a rule counting only those
     present is a different rule, and per-committee quorum policy is S6/CE-3's work.
   - *external directors with no login* → F2-26's declared-EXTERNAL state, plus first-class **guests** here.
   - *apologies / non-voting invitees* → non-voting seats never enter either side; a guest enters neither.
   - *snapshot as a new table or a JSON column* → **neither**: five persisted columns. Once rows are
     seat-bound the sheet *is* the per-seat snapshot, so a table would have duplicated it.
   - *the 20 resolutions whose tallies are seeded scalars with zero vote rows* → **still open, and it is not a
     quorum question.** It is the *vote-tally* equivalent of the same defect and belongs with F2-29. Any code
     path calling `recomputeTally` still rewrites those to `0/0/0 DEFERRED`. **Carried forward as F2-40.**
   ➕ F2-26 handed this a guarantee it would otherwise have had to build: one person cannot hold two ACTIVE
   seats on one committee, so a roster-derived denominator cannot double-count a member.
4. ~~**F2-27** — enforce `MODULE:EXPORT` on every export endpoint, per-tenant impact query first.~~
   ✅ **DONE 2026-07-26** (`V320`, ~4 h). 40 handlers / 18 controllers — **not** the 24 sites in 17 files the
   round-2 note recorded, because `CONTENT_DISPOSITION` literals under-count by 15 and a source regex cannot
   read `@PreAuthorize(READ_GUARD)` constants. **The impact query falsified both design calls the plan had
   pre-committed to**, and that is the transferable result: the AND-chain composition would have 403'd 8 of 18
   `REPORTING:EXPORT` holders, and the "unprobeable 403 leg" was probeable with a seeded persona. Adopted rule:
   *keep each guard's boolean shape, lift only the action* — access-preserving, because 11 of 14 modules grant
   READ and EXPORT to identical role sets. Three exceptions needed judgement (`AI:EXPORT` absent from the
   catalogue → the F2-23 trap a fourth time; `CHANGE:EXPORT` missing from 4 assurance roles → granted, VIEWER
   deliberately not; the one ADMIN export is a **blank form template** → allowlisted rather than break 14 roles
   for no DLP gain). Export History auditing split out as **F2-41**.
   ➕ Two role names in `hasAnyRole` lists (`MODEL_OWNER`, `BOARD_MEMBER`) **do not exist in `iam.roles`** — the
   silent-failure argument for catalogued permissions over role lists, and F2-05's case in one line.
5. **W2-1a** (S) — de-mock the three executive surfaces (`searchIndex.ts`, `widgets.tsx`, `boardPack.ts`)
   **plus F2-33's onboarding invite**, which is the same defect on the first-run journey.
6. **F2-22a** (S) — mass-assignment audit over the 216 entity-bound `@RequestBody` sites. F2-25 is the
   fourth instance of this class found by hand (after F2-21, F2-24 and the roster); the sweep is overdue.
7. **F2-13a** (S) — grant-tightness report in both directions. It would have caught F2-23 *and* the
   ungrantable `ADMIN_APPROVE` automatically.
8. **F2-35** (S, product + engineer) — ratify **one** SKU catalogue. **W2-0 and F2-04 cannot be written
   until this lands**, and it is the gate on all of Wave 2.
9. **CE-0** (XS) — lift Change out of the Risk nav group, amending the UX blueprint in the same commit.

*(F2-08a was item 6 here until the council found it had already shipped at `313dd6dd`.)*
