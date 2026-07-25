# TSS GRC Platform — Master Action & Remediation Plan
### خطة الإجراءات والمعالجة الشاملة

> **Purpose.** The single executable backlog for the platform. It consolidates **every open item from every source** — the independent assessment's 9 findings, the live defect register, the vendor's own carry-overs, the third-party foundation pack's adopted items, the Change-Enablement programme, and the deferred audit tail — into one ordered, verifiable plan.
>
> **Basis:** `INDEPENDENT_PLATFORM_ASSESSMENT.md` (opinion **QUALIFIED, 3.4/5**, 2026-07-25) · `FOUNDATION_PACK_ASSESSMENT_AND_ACTION_PLAN.md` · `PLATFORM_ARCHITECTURE_PACK.md` §13–§14 · `CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md` · `PROJECT_CHRONICLE.md` §19.
> **Code baseline:** `master` @ `00ba890e`; working branch @ `0fbb5b19`; worktree `fix/break-glass-sweep-tx` @ `ccc06799`. Suite 2,322/0/0. Migrations V1–V312 packaged; **next free V313**.
> **House rules apply** (`CLAUDE.md`): feature branch per slice · commit per verified slice · **never merge to master without the explicit word "merge"** · migrations idempotent + applied twice to dev · `[REVIEW-TEST]` rows CHARINDEX-cleaned · **destructive DB ops need explicit OK** · live-probe every slice (403/409/tenant) · Arabic mandatory on user-visible records · ratchet may only go down.

---

## §0 Status tracker

Update the row **in the same commit** as the slice. Sizes: **S** ≤ 2 d · **M** ≤ 1 w · **L** > 1 w.

### Wave 0 — Stop the bleeding (target: this week)
| ID | Action | Size | Status | Clears | Migration |
|---|---|---|---|---|---|
| **W0-1** | Merge `fix/break-glass-sweep-tx` → master | S | **READY** (code done, 2322/0/0) | DF-1 / F-7 | none |
| **W0-2** | Rebuild jar + **activate** with the staged auth-refresh fix in ONE restart | S | jar staged | DF-1 + DF-2 / F-7 | none |
| **W0-3** | Post-deploy verification: sweep-WARN = 0 over 24 h; bogus-signature probe = 401 | S | not started | C-5 | none |
| **W0-4** | **V313** — catalogue `SIGNATURE` + `AI` permission modules (+ `CAPA APPROVE`) with role grants | S | not started | prereq for W1-1/W1-3 | **V313** |
| **W0-5** | Guard the **5 e-signature write endpoints** (highest-risk slice of F-1) | S | not started | C-1 (part) | rides V313 |
| **W0-6** | Fix or delete `AGENTS.md` (wrong remote); stamp V1 `docs/` corpus `HISTORIC — superseded` | S | not started | F-8 | none |
| **W0-7** | Surface deployed build ID on `/actuator/info` + admin screen | S | not started | F-7 | none |

### Wave 1 — Assure the governance core (2–3 weeks)
| ID | Action | Size | Status | Clears | Migration |
|---|---|---|---|---|---|
| **W1-1** | Committee-engine test suite (quorum, recusal, tally, minutes hash, referral gate) | M | not started | C-2 / F-2 | none |
| **W1-2** | `ModulePermissionEvaluator` test matrix | S | not started | C-2 / F-2 | none |
| **W1-3** | Guard the remaining **40** business/admin write endpoints; add `@PublicEndpoint` + `@SelfScoped` markers; **lower ratchet 62 → 17** | M | not started | C-1 / F-1 | rides V313 |
| **W1-4** | **V314** — seed default `committee_gate_map` per tenant + config-health check for unmapped gates | M | not started | C-3 / F-4 | **V314** |
| **W1-5** | Retain one non-deleted end-to-end committee decision as standing evidence | S | not started | C-3 | none |
| **W1-6** | Tenant-isolation test pack (cross-org read/write per module family) | M | not started | F-9 | none |

### Wave 2 — Make the UI truthful (2–4 weeks)
| ID | Action | Size | Status | Clears | Migration |
|---|---|---|---|---|---|
| **W2-1** | Classify all **53** mock import sites: fallback / primary / seed-only | S | not started | C-4 / F-5 | none |
| **W2-2** | Replace fallbacks with explicit empty + error states | L | not started | C-4 | none |
| **W2-3** | Repoint or delete primary mock usages (start: `searchIndex.ts` ×8, `dashboard/widgets.tsx` ×3) | L | not started | C-4 | none |
| **W2-4** | CI rule: no `mock*` imports outside `__tests__`/`*.test.*` | S | not started | C-4 | none |
| **W2-5** | Mandatory product states (empty/error/approval/rejection/escalation/completion) as FE acceptance criteria | M | not started | quality | none |

### Wave 3 — Evidence-based scope (3–4 weeks)
| ID | Action | Size | Status | Clears | Migration |
|---|---|---|---|---|---|
| **W3-1** | Convert the 84 user stories into an **executable probe suite in CI** | L | not started | C-6 / F-9 | none |
| **W3-2** | Module scorecard with *Assurance* + *Operating-evidence* columns | M | not started | C-6 / F-3 / F-6 | none |
| **W3-3** | Minimum test bar per sellable module (happy path + 403 + tenant) — start with SAMA `outsourcing` | L | not started | F-3 | none |
| **W3-4** | Reconcile the 287 audit findings to closed/open with evidence | M | not started | F-9 | none |
| **W3-5** | Label hollow features *Available — no operating evidence*; seed a worked example or descope | M | not started | F-6 | none |

### Wave 4 — Structure, then strategy (deferred — **must follow Wave 1**)
| ID | Action | Size | Status | Notes |
|---|---|---|---|---|
| **W4-1** | Architecture boundary tests (import ratchet, LoC ceiling, pure state machines) | M | not started | ADR-020 |
| **W4-2** | **CE-1 … CE-16** Change Enablement separation + CAB uplift | L | speced | ⚠️ **Do not start before W1-1** — never refactor an untested governance core. Migrations **V315–V324** (renumbered from V313–V322) |
| **W4-3** | Chronicle §19 carry-overs: rotate seeded passwords · TLS at nginx · SMTP relay · `is_deleted` type reconciliation · disposition 4 demo SoD violations | M | not started | BL-B1…B5 |
| **W4-4** | Commercial packaging + Saudi readiness scorecards per sellable module | M | not started | adopted from foundation pack |
| **W4-5** | AI transformation assessment per workflow + AI governance gates | L | not started | foundation pack's best material |
| **W4-6** | Structural debt: BaseEntity unification · FE dedup · outsourcing merge · `@Valid` DTO conversion · code-splitting · SW update strategy | L | deferred by decision | audit tail |

---

## §1 How to read this plan

| Field | Meaning |
|---|---|
| **Clears** | The blocking condition (C-1…C-6) or finding (F-1…F-9, DF-1…DF-5) this action closes |
| **Acceptance** | The *testable* outcome. Where it names an HTTP status, that is a live probe, run with two users (`admin` + `so_officer`) and a negative user (`viewer`) |
| **Verify** | The exact command/query producing the evidence |
| **Gate** | What must be true before the action starts |

**Non-negotiables for every action:** org-scoped queries · audit row on mutation · bilingual `*_en`/`*_ar` on user-visible fields · RFC-7807 errors · no new unguarded write endpoint · ratchet only decreases.

---

## §2 Wave 0 — Stop the bleeding

### W0-1 / W0-2 / W0-3 · Close the two live production defects
**Why.** A security control is not running in production: the break-glass expiry sweep failed **33 times on 2026-07-25** (last 16:26:35), so expired emergency access may persist. Separately the stale-cookie 500 is proven live on `:80`. **Both fixes already exist**; only delivery is missing (F-7).

**Scope.**
1. Verify the branch: `mvn -o test` on `fix/break-glass-sweep-tx` (expect 2322/0/0).
2. FF-merge to master, push.
3. Rebuild: `mvn -o clean package` → new jar carries **both** fixes.
4. Back up live jar, stage as `...jar.new`, then **one** elevated restart:
```powershell
Stop-Service TssGrcBackend
if (Test-Path 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar.new') { Move-Item -Force 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar.new' 'D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar' }
Start-Service TssGrcBackend
```
**Acceptance.** (a) `py scratchpad/probe_df2.py` → bogus-signature case returns **401** (currently 500). (b) Zero `break-glass expiry sweep failed` entries over 24 h. (c) `/actuator/health` UP; Flyway at ≥ V312.
**Verify.** `Select-String -Path 'D:\Deployment\tss-grc\app\WinSW.NET4_*.out.log' -Pattern 'break-glass expiry sweep failed'` → count 0 after restart.
**Rollback.** Restore `backup\...jar.pre-authfix-20260723_201100`; DB backup `TSS_GRC_Deployment_predeploy_20260723_194031.bak` exists.
**Gate.** User's explicit **"merge"** + one elevated shell.

### W0-4 · V313 — permission catalogue additions *(prerequisite — do not skip)*
**Why.** The catalogue holds **25 modules**; there is **no `SIGNATURE` and no `AI` module**, and `CAPA` has no `APPROVE` action. A guard naming an uncatalogued module cannot grant anyone — and the evaluator's unknown-module path is itself untested (F-2). So the guards in W0-5/W1-3 **must** be preceded by catalogue rows.

**Scope (idempotent, GO-separated, applied ×2 to dev).**
| Module | Actions | Granted to (mirror the existing persona pattern) |
|---|---|---|
| `SIGNATURE` | READ, WRITE, APPROVE | PLATFORM_ADMIN, COMPLIANCE_OFFICER, CISO, DPO, RISK_MANAGER, BCM_MANAGER (signers); **not** VIEWER |
| `AI` | READ, WRITE | PLATFORM_ADMIN, COMPLIANCE_OFFICER, RISK_MANAGER, CISO, AUDITOR; **not** VIEWER |
| `CAPA` | **+APPROVE** | PLATFORM_ADMIN, COMPLIANCE_OFFICER, CISO |
**Acceptance.** `SELECT COUNT(*) FROM iam.permissions` rises by 6; `viewer` holds none of them; migration re-runs clean twice.
**Verify.** `py scratchpad/dev_sql.py "SELECT module, action FROM iam.permissions WHERE module IN ('SIGNATURE','AI') OR (module='CAPA' AND action='APPROVE')"`

### W0-5 · Guard the e-signature endpoints *(highest-risk slice of F-1)*
**Why.** `DigitalSignatureController#create/sign/reject/expire` and `SignatureController#sign` are `isAuthenticated()` only — **any authenticated principal, including read-only `viewer`, can create and sign signature records.** The platform sells e-signed minutes as tamper-evidence (ADR-019); an unguarded sign endpoint contradicts that claim directly.

**Scope.**
| Endpoint | Guard |
|---|---|
| `DigitalSignatureController#create` | `hasPermission('SIGNATURE','WRITE')` |
| `DigitalSignatureController#sign` | `hasPermission('SIGNATURE','APPROVE')` |
| `DigitalSignatureController#reject` | `hasPermission('SIGNATURE','APPROVE')` |
| `DigitalSignatureController#expire` | `hasPermission('SIGNATURE','WRITE')` |
| `SignatureController#sign` (`/api/v1/signatures/{entityType}/{entityId}`) | `hasPermission('SIGNATURE','APPROVE')` |
Keep principal-based attribution exactly as-is (it is already correct — the caller cannot sign as someone else).
**Acceptance.** `viewer` → **403** on all five; `so_officer` → **200**; existing committee minutes e-signing (Track D) still succeeds; suite green.
**Verify.** New probe `scratchpad/probe_sig_guard.py`; ratchet drops by 5 (62 → 57).

### W0-6 · Governance-document integrity
`AGENTS.md` is `CLAUDE.md` with a blind `Claude`→`Codex` replacement and asserts a **non-existent remote** (`Codex-GRC-2`; real: `Claude-GRC-2`). Either delete it or correct the remote + tool string. Then prepend `> **HISTORIC — superseded by PLATFORM_ARCHITECTURE_PACK.md**` to the 50 V1-era `docs/` files that still market grade-C verticals as mature modules.
**Acceptance.** No file in the repo names a non-existent remote; `grep -rl "Codex-GRC-2" .` → empty.

### W0-7 · Deployed-build visibility
**Why.** W0-1/W0-2 exist because nobody could see that production was running a jar older than the fix. Expose build ID/commit/build-time via `/actuator/info` and a platform-admin screen.
**Acceptance.** `curl :8091/actuator/info` returns the git SHA matching the deployed jar.

---

## §3 Wave 1 — Assure the governance core

### W1-1 · Committee-engine test suite *(the single highest-value assurance investment)*
**Why (F-2).** No test anywhere matches *committee*, *meeting* or *resolution*; `module/admin` is **48 production files with zero tests** — yet this engine is the system of record for change and policy approval, quorum, recusal and e-signed minutes, and the vendor grades it **A**. A refactor could silently break quorum or recusal with a green suite.

**Test classes to create** (`src/test/java/com/tss/grc/module/admin/committee/`):
| Class | Cases |
|---|---|
| `CommitteeQuorumTest` | quorum met · not met · exactly-at-threshold · chair-required-and-absent · attendees from another org excluded |
| `CommitteeRecusalTest` | COI forces RECUSED · referral requester force-recused · referral owner force-recused · delegate cannot vote while principal present |
| `CommitteeResolutionTallyTest` | RECUSED excluded from for/against/abstain · outcome mapping · `min_approvals` boundary (n-1 fails, n passes) |
| `CommitteeMinutesSignatureTest` | MINUTES_APPROVED triggers exactly one APPROVE signature · statement carries the minutes SHA-256 · hash stable across re-read · no signature when approver is null |
| `CommitteeReferralGateTest` | `assertDecisionApproves` 409 when: no resolution · outcome ≠ APPROVED · meeting not MINUTES_APPROVED · voteFor < minApprovals; 200 on the happy path · non-referred entity path untouched |
| `CommitteeTenantIsolationTest` | every finder/mutator rejects or returns empty for a foreign `organisation_id` |
**Acceptance.** ≥ 25 new tests, all green; mutating any one guard in `CommitteeReferralService`/`CommitteeResolutionService` makes a test fail (verify by deliberate temporary mutation).
**Gate.** None — start immediately, in parallel with Wave 0.

### W1-2 · `ModulePermissionEvaluator` test matrix
**Why (F-2).** Zero `Permission*Test` files exist. Every one of the 859 guarded write endpoints depends on this evaluator; it has no regression net.
**Cases.** grant present → true · absent → false · `PLATFORM_ADMIN` bypass · **unknown module** (the W0-4 failure mode) · unknown action · null/blank args · user with role-but-no-grant · tenant mismatch · role inherited via persona matrix.
**Acceptance.** ≥ 9 tests green; unknown-module behaviour explicitly asserted and documented.

### W1-3 · Guard the remaining business/admin write endpoints; re-baseline the ratchet
**Why (F-1).** The 62 ratchet-exempt endpoints are **not** the "small set of legitimately-public write endpoints" the ADR narrative describes. Precise classification of all 62:

| Bucket | Count | Endpoints | Action |
|---|---|---|---|
| **Legitimately public** | **8** | `AuthController#login/logout/refresh` · `ComplaintController#submitPublic` · `PublicWorkflowRequestController#respond` · `VendorBcpAttestationController#submitByToken` · `WhistleblowerCaseController#publicReply/publicSubmit` | Annotate `@PublicEndpoint`; keep rate-limited; **these become the documented floor** |
| **Self-scoped (principal *is* the authority)** | **9** | `HelpController#trackView` · `NotificationController#markRead/markAllRead` · `RefreshTokenAdminController#revokeAllMine` · `SavedViewController#create/update/delete` · `WebPushController#register/unregister` | Annotate `@SelfScoped`; assert server-side that the target row belongs to the caller |
| **Needs a real guard** | **45** | see mapping below | Add `hasPermission(...)` |

**Guard mapping for the 45** (module homes verified in source):
| Controller (module) | Endpoints | Proposed guard |
|---|---|---|
| `DigitalSignatureController`, `SignatureController` (`module/signature`, `module/platform`) | 5 | **done in W0-5** — `SIGNATURE WRITE/APPROVE` |
| `RootCauseAnalysisController` (`module/incident/rca`) | `create`,`update`,`submit`,`generateCapas` → `INCIDENT WRITE`; `approve`,`reject` → **`CAPA APPROVE`** | 6 |
| `BcmComplianceMappingController` (`module/bcm`) | `create`,`update`,`assess`,`attachEvidence`,`attachGap` → `BCM WRITE` | 5 |
| `BcmCommunicationContactController` | `create`,`update`,`delete`,`recordTestResult` → `BCM WRITE` (delete → `BCM DELETE`) | 4 |
| `PlanDistributionController` (`module/bcm/cycle`) | `distribute`,`markStale` → `BCM WRITE`; `acknowledge`,`decline` → `@SelfScoped` + `BCM READ` (recipient action) | 4 |
| `CrisisAarController` (`module/bcm/cycle`) | `generate`,`update` → `BCM WRITE`; `transition` → `BCM APPROVE` | 3 |
| `PlanReviewController` (`module/bcm/cycle`) | `attest` → `BCM APPROVE`; `sweep` → `BCM WRITE` (scheduler-triggered; also restrict to admin) | 2 |
| `BcmSettingsController` | `update`,`reset` → `BCM WRITE` (**`reset` also `PLATFORM_ADMIN`** — destructive) | 2 |
| `BcmLibraryController` | `clonePlanTemplate` → `BCM WRITE` | 1 |
| `ExerciseRuntimeController` (`module/bcm/cycle/runtime`) | `respond` → `BCM WRITE` (participant) | 1 |
| `AiSuggestionController` (`module/ai`) | 7 endpoints → **`AI WRITE`** | 7 |
| `AiControlMappingController` (`module/platform/controls/ai`) | `suggest` → **`AI WRITE`** | 1 |
| `ApprovalChainController` (`module/workflow/approval_chains`) | `recordAction`,`withdraw` → `WORKFLOW APPROVE` | 2 |
| `ApprovalRequestController` (`module/approvals`) | `create` → `WORKFLOW WRITE`; `transition` → `WORKFLOW APPROVE` *(service already enforces assignee-only via `AccessDeniedException` — add the guard as defence in depth)* | 2 |
| `RefreshTokenAdminController#revokeOne` (`module/auth`) | revokes **another user's** session → `IAM WRITE` | 1 |

**Ratchet:** 62 → **17** (8 public + 9 self-scoped). Lower `BASELINE_UNGUARDED_WRITE_ENDPOINTS` to 17 and extend the test so `@PublicEndpoint`/`@SelfScoped` endpoints are *listed explicitly* rather than silently tolerated — a new business endpoint can then never slip into the floor.
**Acceptance.** `viewer` → **403** on all 45; the legitimate role → **200**; public endpoints still reachable unauthenticated; self-scoped endpoints reject a foreign target id; ratchet passes at 17; full suite green.
**Gate.** **W0-4 (V313) must be applied first**, or `SIGNATURE`/`AI`/`CAPA APPROVE` guards deny everyone.

### W1-4 · V314 — configure the committee gate + config-health check
**Why (F-4).** `admin.committee_gate_map` has **0 rows** — and it is what maps a change's CAB/ECAB authority to a committee. With no mapping, **no change is actually committee-gated**; every change silently falls back to the legacy tally. The flagship governance control is implemented but not in force. `committee_referral`, `committee_meeting_attendee` and `committee_resolution_vote` are likewise empty (probe rows are CHARINDEX-deleted by design), so there is also **no retained evidence** of it ever operating.
**Scope.** (a) Idempotent seed: for each tenant with `change_authorisation_matrix` rows demanding CAB/ECAB, create a gate-map row pointing at that tenant's governance committee (create a default committee if none exists). (b) **Config-health check**: a startup log warning + a platform-admin dashboard card that fails loudly when a tenant's matrix demands CAB/ECAB but no gate mapping exists. (c) Expose it via the existing production-readiness panel.
**Acceptance.** Every tenant with a CAB/ECAB matrix row has a gate map row; deliberately deleting a mapping makes the health check report **unhealthy**; a change routed to CAB now hits the committee gate (409 before a quorate decision) instead of the legacy path.
**Verify.** `py scratchpad/dev_sql.py "SELECT organisation_id, gate, committee_id FROM admin.committee_gate_map"` → non-empty; then a two-user referral probe.

### W1-5 · Retain a worked committee decision as standing evidence
Run one complete flow in a demo tenant — refer → agenda → attendance → quorum → votes (incl. a recusal) → minutes → e-signature → change gated → approved — and **do not clean it up**. Tag as `[DEMO-EVIDENCE]` (never `[REVIEW-TEST]`, which the cleanup scripts delete).
**Acceptance.** Non-deleted rows in all four committee tables; the e-signature is retrievable via `GET /api/v1/signatures/committee_meeting/{id}`; screenshot/export attached to the acceptance pack.

### W1-6 · Tenant-isolation test pack
**Why.** Multi-tenancy is a *developer responsibility* per query (ADR-003) with only 2 tenant tests today, and the platform runs 3 tenants. Add a parameterised test per module family asserting cross-org read/write returns empty/403/404 and that the prod write-guard interceptor throws.
**Acceptance.** ≥ 1 isolation test per module family; a deliberately org-unscoped query fails the pack.

---

## §4 Wave 2 — Make the UI truthful

**Why (F-5).** **15 mock modules are imported by live code at 53 sites.** Where a mock is an error-fallback, a backend failure renders *plausible fabricated register rows instead of an error* — in a GRC system of record that is an audit-evidence and integrity risk. The vendor's own debt register flags only `mockChanges.ts`.

**Highest-exposure sites (fix first):**
| Importer | Mocks pulled in |
|---|---|
| `lib/searchIndex.ts` | `mockChanges`, `mockCompliance`, `mockCyber`, `mockEvidence`, `mockIncidents`, `mockPrivacy`, `mockRisks`, `mockTprm` (**8** — global search can surface fabricated records) |
| `pages/dashboard/widgets.tsx` | `mockChanges`, `mockCompliance`, `mockTprm` (**3** — executive dashboard) |
| `lib/boardPack.ts` | `mockCompliance`, `mockPrivacy`, `mockWorkflows` (**3** — board reporting) |
| `pages/audit/*` (Workspace, EngagementDetail) | `mockCompliance`, `mockEvidence` (**4** — auditor-facing) |
| `pages/authority/*` | `mockCompliance`, `mockEvidence` (**3**) |
| `pages/detail/*` (Control, Dpia, Finding, Vendor) | `mockCompliance`, `mockPrivacy`, `mockTprm` (**4**) |
| Register pages | `risk` (RiskRegister, RiskDetail, risk-lifecycle), `compliance`, `cyber`, `privacy`, `tprm`, `scm`, `ethics`, `incidents`, `evidence`, `bcm`, `admin`, `workflow`, `reporting` |
| Lifecycle/analytics libs | `auditLifecycle`, `bcmLifecycle`, `riskLifecycle`, `paTriggers`, `processMining`, `workflowVersioning` |

**W2-1** classify each site: **fallback** (used on API error) · **primary** (rendered as data) · **seed-only** (shape/demo constant, harmless). **W2-2** every fallback becomes an explicit empty/error state — the foundation pack's UX constitution mandates exactly this, and `problemMessage()` already names the failing field. **W2-3** primaries get repointed to canonical APIs or deleted, starting with `searchIndex.ts`, `widgets.tsx` and `boardPack.ts` (the three that reach executives and auditors). **W2-4** a CI grep rule blocks `mock*` imports outside test files so this cannot regrow.
**Acceptance.** `grep -rl "from '.*mock" src/pages src/lib src/components` → empty (or only whitelisted seed constants); with the backend stopped, every register shows an error state and **no data rows**; CI fails on a reintroduced mock import.

---

## §5 Wave 3 — Evidence-based scope

**W3-1 · Executable probe suite (the assurance keystone).** Convert the 84 user stories into CI-run probes: persona × permission × expected HTTP status × evidence artifact. Server-enforced ACs are already written as probes — for SoD rows **a 409 is the pass**. Group by module; run the negative user (`viewer`) against every write. *Acceptance:* suite runs in CI on every push; a removed guard or SoD check fails it.

**W3-2 · Module scorecard.** One row per module × the 10 review lenses (1–5, weighted per the foundation pack), plus two columns the vendor's grades lack: **Assurance** (test count / does a test prove the control?) and **Operating evidence** (non-zero rows / retained worked example). *Acceptance:* published; the A–D ↔ 1–5 mapping stated once (A≈4.5–5 · A−≈4 · B≈3 · C≈2 · D≈1).

**W3-3 · Minimum test bar per sellable module.** Happy path + one authorization-denial + one tenant-isolation test. **20 modules currently have zero tests** — `admin`(48 files), `data_officer`(23), `sharia`(16), `cpty`/`openbanking`/`outsourcing`/`rrp`(14), `dataclass`(12), `fraud`/`opres`/`reginspect`/`regreporting`(10), `attachment`, `audit_eng`, `board_pack`, `controlmap`, `dashboard`, `knowledge`, `nca_ecc`, `datagov`. Start with **SAMA `outsourcing`** (compliance-critical, zero tests). *Acceptance:* no zero-test module appears in any commercial scope statement.

**W3-4 · Audit reconciliation.** Reconcile the 287 findings from `reports/audit-report.html` to closed/open **with evidence per finding**. *Acceptance:* a register with a verification pointer per closed item.

**W3-5 · Hollow features.** Label *Available — no operating evidence* and either seed a worked example or descope: `intake.intake_records`/`intake_events` (0), `audit_committee.*` (0), `iam.break_glass_events` (0), `iam.access_review_items` (0), `risk_lc` 4/7 empty, `workflow.recipient_lists` (0), `platform.translations`/`saved_views` (0), `privacy_lc` 3/4 empty (**graded D — do not seed**, landmine #25).

---

## §6 Sequencing, gates and dependencies

```
W0-1 ─► W0-2 ─► W0-3            (merge → deploy → verify: closes DF-1 + DF-2)
W0-4 (V313 catalogue) ─► W0-5 (signature guards) ─► W1-3 (remaining 40 guards → ratchet 17)
W1-1 (committee tests) ──┬────────────────────────► W4-2 (CE-1..CE-16)   ⚠ hard gate
W1-2 (permission tests) ─┘
W1-4 (V314 gate map) ─► W1-5 (retained evidence)
W2-1 ─► W2-2/W2-3 ─► W2-4
W1-* + W2-* ─► W3-1 (probe suite) ─► W3-2 (scorecard) ─► commercial claims
```

**Hard gates.**
1. **No guard slice before V313** — an uncatalogued module in a guard denies everyone.
2. **No CE-1 refactor before W1-1/W1-2** — do not restructure an untested governance core; the CE plan's own migrations shift to **V315–V324**.
3. **No commercial/regulated claim before W3-2** — scope must match assurance (C-6).
4. **No merge to master without the user's explicit "merge"**; destructive DB operations need explicit OK.

---

## §7 Blocking-condition traceability

| Condition (from the independent assessment) | Closed by | Evidence |
|---|---|---|
| **C-1** business writes carry authority checks | W0-4, W0-5, W1-3 | `viewer` → 403 on 45 endpoints; ratchet 17 |
| **C-2** governance core has regression cover | W1-1, W1-2 | ≥ 25 committee tests + ≥ 9 evaluator tests green |
| **C-3** committee gate in force | W1-4, W1-5 | non-empty gate map; health check; retained decision |
| **C-4** no fabricated data reachable from live UI | W2-1…W2-4 | zero mock imports outside tests; error states with backend down |
| **C-5** live defects closed in production | W0-1…W0-3 | 24 h zero sweep-WARN; refresh probe 401 |
| **C-6** scope matches assurance | W3-2, W3-3 | scorecard with Assurance + Operating-evidence columns |

**On completion of Waves 0–3 the expected independent rating moves from 3.4 to 4.2–4.5 / 5**, supporting an unqualified opinion for the strong domains.

---

## §8 Risk register for the remediation itself

| Risk | Likelihood | Mitigation |
|---|---|---|
| New guards lock out legitimate users (esp. BCM participants, signature signers) | **High** | Grant-mirroring in V313 follows the existing persona pattern; probe each role before/after; per-tenant enable flag for the BCM participant endpoints; dry-run report of who *would* have been blocked |
| `BcmSettingsController#reset` guard breaks a demo flow | Medium | Requires PLATFORM_ADMIN — confirm no seeded automation calls it |
| Removing mock fallbacks exposes previously-hidden API failures | **High — by design** | That is the point; triage the newly-visible failures as real defects before shipping W2-2 |
| Committee gate-map seeding routes changes to a wrong committee | Medium | Seed only where the matrix demands it; require a named committee per tenant; log every routed change for the first week |
| Committee tests encode current (possibly wrong) behaviour | Medium | Derive expected values from ITIL/ISO intent and the plan's ACs, not from running the code; review quorum arithmetic against `CabProfile` policy |
| Ratchet re-baseline to 17 blocks unrelated work | Low | Land it in the same commit as W1-3; the marker annotations make the floor explicit |
| Probe suite becomes flaky in CI | Medium | Self-cleaning `[REVIEW-TEST]` rows with CHARINDEX; no reliance on seeded IDs; isolate per-org fixtures |
| Wave 0 deploy regresses production | Low | Backups staged (jar + DB + UI); rollback commands in §2; single restart window |

---

## §9 Reporting cadence & definition of done

**Cadence.** Update §0 in the commit that lands each slice. After every wave: re-run the affected probes and record the result in the wave's row. Waves 0–1 warrant a short written status to the acceptance committee (they close security-relevant conditions).

**Programme definition of done.**
- [ ] Zero live High defects; production build ID visible and matching master
- [ ] Ratchet at **17**, every business write guarded, `viewer` proven 403 across the set
- [ ] Committee engine + permission evaluator under test; deliberate mutation fails the suite
- [ ] Committee gate configured, health-checked, with a retained worked decision
- [ ] No `mock*` import outside test files; backend-down shows error states, not data
- [ ] 84 stories running as CI probes; 287 audit findings reconciled with evidence
- [ ] Module scorecard published; no zero-test/zero-data module in commercial scope
- [ ] Then — and only then — CE-1…CE-16, boundary tests, packaging and AI strategy

---

*Prepared 2026-07-25 from the independent assessment (QUALIFIED, 3.4/5) and every open item across the vendor, third-party, audit and chronicle sources. All findings reproducible via `INDEPENDENT_PLATFORM_ASSESSMENT.md` Appendix A. Migrations reserved: **V313** (permission catalogue), **V314** (committee gate map), **V315–V324** (CE programme, renumbered).*
