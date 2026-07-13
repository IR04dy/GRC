# SGI (Saudi Green Initiative) Module — Review & Uplift Plan

> **Scope:** the SGI commitment register inside `module/sust` (`sust.sgi_commitment`,
> screen: ESG page → SGI tab). Review date **2026-07-13** at master `73fb9399`.
> Verdict: **register + progress engine are production-grade; the governance
> work-follows around them are missing** — achieving a national-initiative
> commitment is a self-service write, and nothing watches the calendar.
>
> Companion plans (same format): `SUSTAINABILITY_GREEN_FINANCE_REVIEW_AND_UPLIFT_PLAN.md`,
> `RBAC_ACCESS_GOVERNANCE_MODULE_PLAN.md`. House rules per `CLAUDE.md` apply
> (verified slices, no merge without explicit "merge", ratchet ≤ 62,
> `[REVIEW-TEST]` + CHARINDEX, idempotent migrations applied twice).

---

## §0 Implementation status

| Slice | Content | Status | Commit | Migration |
|---|---|---|---|---|
| SGI-A | Governance gates — SoD on achieve/retire, principal attribution, evidence-gated achieve | **DONE** | `804de092` | none (code-only) |
| SGI-B | Surveillance — scheduled DELAYED sweep + milestone-overdue watch (+ buttons, notifications) | **DONE** | `804de092` | none (code-only) |
| SGI-C | Source lineage — current_value linked to GHG inventory / metric readings + refresh action | **DONE** | _(feat/sgi-uplift)_ | **V296 applied** |
| SGI-D | Optional by decision — verification state, engine routing, target linkage | **DEFERRED** (decisions #1/#3/#5 = direct APPROVE / defer / defer) | — | — |
| SGI-E | Cleanup — dead static constants, per-register export, evidence chip on cards | **NOT STARTED** | — | none |

**Decisions settled (2026-07-13):** #1 direct APPROVE + Rule C (no engine routing);
#2 evidence gate enforced (Rule D); #3 verification state deferred (assurance stays
n/a); #4 sweep daily 03:00 UTC; #5 target linkage deferred. SGI-D therefore drops
out and no V297 is needed. Reserved migration: **V296** (SGI-C only).

**SGI-A/B as-built (2026-07-13):** achieve/retire flipped to `SUST_APPROVE`, body
names dropped, achiever/retirer stamped from the principal; Rule C (achiever ≠
last editor, 409 — PLATFORM_ADMIN bypasses via new `CurrentActor.isPlatformAdmin()`);
Rule D (evidence-gated achieve, 400 — mirrors GHG Rule 2); `SgiSurveillanceService`
+ `SgiSurveillanceScheduler` (daily 03:00 UTC) raising behind-plan (`{id}`) and
overdue-milestone (`{id}:milestone`) auto-CAPAs — no `ck_ca_source` change — plus
owner in-app notification; `POST /sgi-commitments/sweep`. FE: name fields removed,
achieve is a confirm, "Run sweep" button, tier + overdue-milestone card badges.
Suite **2253/0/0**, ratchet **62**, FE tsc clean; live probe **20/20** (admin +
so_officer, through the Vite proxy), dev DB left with zero `[REVIEW-TEST]` residue.

**SGI-C as-built (2026-07-13, V296):** `value_source` (MANUAL|ESG_METRIC|GHG_TOTAL) +
`source_metric_id` + `source_scope` on `sust.sgi_commitment` (V296, applied twice to
dev). `POST /sgi-commitments/{id}/refresh-value` (SUST WRITE): ESG_METRIC pulls the
newest `esg_metric_reading` (value + its as-of); GHG_TOTAL sums VERIFIED/APPROVED
`ghg_emission_record` for the latest year (optionally one scope), as-of = year-end;
stamps `current_value`/`current_value_as_of` + audit old→new + behind-plan CAPA;
MANUAL rejected. The surveillance sweep refreshes sourced values before judging tier
(without re-stamping `updated_by`). FE: source picker in create/edit, "↻ from source"
button + provenance line on sourced cards. Suite **2256/0/0**, ratchet **62**, FE tsc
clean; live probe **7/7** (metric + GHG-total + per-scope + MANUAL-reject + audit
trail), zero residue.

---

## §1 Context

SGI commitments are the organisation's public pledges under the Saudi Green
Initiative / net-zero-2060 programme (renewables share, afforestation, waste
diversion, emission reductions…). They are externally visible statements: they
appear in the sustainability annex/report, the board pack, the ESG composite
score, and the ISAE 3410 assurance-lineage view. That makes their lifecycle a
**disclosure-grade** workflow — the same class as CMA/TCFD disclosures and GHG
records, which all carry SoD approval in this platform. Today SGI does not.

## §2 As-built inventory (verified in code, 2026-07-13)

**Backend** — `module/sust/sgi/` (entity V59, hardened by S-epics):
- Register: bilingual titles/descriptions/notes, 8 sectors, baseline/current/
  target (+years, unit), owner name/e-mail, next milestone (date + bilingual
  text), `evidence_ref` + `evidence_item_id` (V250 FK), external id.
- Lifecycle `ACTIVE → ACHIEVED | RETIRED`: Rule A (achieve requires the current
  value to meet the target, direction-aware), Rule B (retire requires a reason);
  terminal states read-only with who/when attribution; creation mass-assignment
  guarded (cannot be born ACHIEVED); soft delete; every action audit-logged.
- Progress: computed on read — `progressPct` (direction-aware, capped 150),
  `expectedPct` (linear baseline→target timeline), tier `ON_TRACK / AT_RISK /
  DELAYED` with the AT_RISK factor tenant-configurable
  (`organisation_settings.sgi_at_risk_progress_factor`, default 0.70).
- Auto-CAPA (S-D): DELAYED on create/update → idempotent HIGH corrective action
  (source `SGI_COMMITMENT`, deduped by id).
- Aggregate rollup: active/achieved/retired counts, byTier, bySector.

**Wiring:** SGI section of the sustainability annex reads the live register
(S-I); dashboard widget + composite ESG score input (S-P live); rows in the
assurance-lineage view (`has_data`=status ACTIVE, `has_evidence`, verified=n/a);
evidence linkable via `SustEvidenceLinkService` (module_area SGI).

**API** — `/api/sustainability/sgi-commitments`: list (+byStatus/bySector/
byCode), get, `{id}/progress`, aggregate, POST create (`SUST WRITE`), PUT update
(WRITE), POST `{id}/achieve` (WRITE), POST `{id}/retire` (WRITE), DELETE (DELETE).

**UI** — ESG page → SGI tab: sector-grouped cards, create/edit modals,
achieve/retire dialogs (both ask for a **typed** "approved-by name"), live KPI
in the page strip, dashboard progress widget.

## §3 Gap analysis

| # | Finding | Severity | Why it matters |
|---|---|---|---|
| G1 | **Achieve/retire are WRITE-guarded self-service** — same permission as editing; no second person; `achievedBy`/`retiredBy` are free-text names typed into the dialog, not the authenticated principal | **Critical (governance)** | an externally reportable claim ("we met our SGI target") can be asserted by the same person who typed the numbers, under a forged name; inconsistent with CMA/TCFD/GHG/allocation approvals which all require APPROVE + SoD |
| G2 | **No time-based DELAYED sweep** — tier is computed on read; auto-CAPA fires only inside create/update, so an untouched commitment drifts DELAYED as `expectedPct` grows and never raises a CAPA | **High** | silent slippage on public pledges; ESG targets got exactly this in G4 (trajectory sweep), SGI didn't |
| G3 | **`next_milestone_date` is decorative** — no overdue detection, no reminder to the owner, no escalation | **High** | milestones pass silently; owner e-mail is captured but never used |
| G4 | **`current_value` manual with no source lineage** — cannot pull from the GHG inventory or a metric reading; `current_value_as_of` is the only provenance | **Medium** | weakens the ISAE lineage story; reassessments are unverifiable typed numbers |
| G5 | **No verification/attestation state** — assurance lineage shows verified = n/a by design | Medium (decision) | assurance-grade registers for published figures normally carry an evidence-gated verify step (GHG Rule 2 pattern) |
| G6 | **Not routed through the workflow engine** — achieve/retire inline | Medium (decision) | if committee sign-off is wanted for SGI achievements, the engine already does this for 14 modules |
| G7 | **`esg_targets.sgi_aligned` is a bare boolean** — no FK to a commitment; cards can't show supporting targets | Low | reporting nicety |
| G8 | **Cleanup**: dead static `SGI_COMMITMENTS` + `aggregateEsg()` in `tss-grc-ui-v2/src/lib/esg.ts` (superseded by live S-P dashboards); no per-register XLSX export; no attach-evidence chip on SGI cards | Low | dead code confuses future work; export/evidence consistency with sibling tabs |

## §4 Design

### SGI-A — Governance gates (fixes G1; code-only)
1. **Guard change**: `POST {id}/achieve` and `POST {id}/retire` move from
   `hasPermission('SUST','WRITE')` to `hasPermission('SUST','APPROVE')`.
   (Catalogue impact: none — `SUST_APPROVE` exists since G1 of the green plan;
   ratchet unaffected.)
2. **Principal attribution**: drop the request-body names; stamp
   `achieved_by` / `retired_by` from the authenticated principal
   (`SecurityContextHolder`), matching the audit-Critical fix pattern
   (actor never comes from the body).
3. **SoD rule (Rule C)**: the approver must differ from the person who last
   wrote the commitment (`updated_by`, falling back to `created_by`) — 409
   `"Self-approval blocked: the achiever must differ from the last editor (SoD)"`.
   PLATFORM_ADMIN bypasses per platform convention.
4. **Evidence gate (Rule D, mirrors GHG Rule 2)**: achieve requires
   `evidence_item_id` or a non-blank `evidence_ref` — you cannot declare a
   public pledge met without attached proof. (Decision #2 can waive this.)
5. **FE**: achieve/retire dialogs lose the name fields (confirmation +
   reason-textarea remain); buttons surface the 409 messages as-is
   (problemMessage already names the rule).

### SGI-B — Surveillance (fixes G2+G3; code-only)
1. **`SgiSurveillanceService.sweep()`** — for every ACTIVE commitment of every
   org (org-explicit iteration, off-request-safe):
   - tier == DELAYED → `capaService.autoOpen("SGI_COMMITMENT", id, …)` (same
     source/ref as S-D ⇒ idempotent with the on-write path);
   - `next_milestone_date < today` **and** (`current_value_as_of` is null or
     `< next_milestone_date`) → auto-open a MEDIUM CAPA with ref `"{id}:milestone"`
     (same `SGI_COMMITMENT` source_type ⇒ **no `ck_ca_source` change**) + in-app
     notification to the owner (platform notification service; SMTP later).
2. **Schedule**: daily 03:00 UTC (after the SoD scan window), plus a manual
   `POST /api/sustainability/sgi-commitments/sweep` (`SUST WRITE`) returning
   `{delayedCapas, milestoneCapas}` — mirrors the G4 trajectory/permit sweeps.
3. **FE**: "Run sweep" button on the SGI tab header (like Targets/Env tabs);
   DELAYED/overdue badges on cards.

### SGI-C — Source lineage (fixes G4; **V296**)
1. Columns on `sust.sgi_commitment`: `value_source` (`MANUAL` default |
   `ESG_METRIC` | `GHG_TOTAL`), `source_metric_id` (nullable FK-by-convention to
   `sust.esg_metric`), `source_scope` (nullable tinyint 1–3 for GHG_TOTAL).
2. **Refresh action** `POST {id}/refresh-value` (`SUST WRITE`): ESG_METRIC →
   latest reading of the metric; GHG_TOTAL → sum of approved+verified records for
   the latest complete year (per scope if set); stamps `current_value`,
   `current_value_as_of`, audit-logs old→new. Manual edits of `current_value`
   remain allowed but flip nothing (source is advisory provenance).
3. The sweep (SGI-B) refreshes sourced values before tier evaluation.
4. FE: source picker in create/edit; "↻ from source" button on sourced cards;
   provenance line ("from metric ENV-03, as of 2026-06-30").

### SGI-D — Optional, per §9 decisions
- **Verification state** (G5): `verified` bit + verified_by/at, evidence-gated,
  APPROVE-guarded, verifier ≠ achiever; lineage view flips SGI verified from
  n/a to real. (V296 columns if accepted.)
- **Engine routing** (G6): achieve raises a `workflow.requests` approval
  (record-anchored panel already exists for sust) instead of / in addition to
  the direct APPROVE path.
- **Target linkage** (G7): `esg_targets.sgi_commitment_id` nullable FK (V297);
  SGI card lists supporting targets; target create form gains a commitment
  picker (replaces the bare `sgi_aligned` flag, which stays for compatibility).

### SGI-E — Cleanup (G8; code-only)
Delete dead `SGI_COMMITMENTS` / `aggregateEsg()` (+ static export rows) from
`lib/esg.ts`; add `GET /sgi-commitments/export.xlsx` via `TabularExportService`
(`SUST READ`; naming via ExportNaming) + button; attach-evidence chip on cards
reusing the existing evidence-link flow.

## §5 Phased backlog

| Epic | Contents | Acceptance (probe-verified) |
|---|---|---|
| **SGI-A** | guard flip, principal attribution, Rule C SoD, Rule D evidence gate, FE dialogs | achieve as last-editor → 409 Rule C; achieve as second APPROVE user with evidence → 200 with principal-stamped `achieved_by`; achieve without evidence → 400 Rule D; retire keeps Rule B; WRITE-only user → 403 |
| **SGI-B** | surveillance service + schedule + endpoint + FE button/badges + owner notification | sweep on a stale DELAYED commitment → 1 CAPA, re-run → 0 (idempotent); overdue milestone → `{id}:milestone` CAPA + notification row; ON_TRACK untouched |
| **SGI-C** | V296 columns, refresh action, sweep integration, FE source picker | metric-sourced commitment refresh pulls the latest reading (value+date match DB); GHG_TOTAL matches the inventory sum; audit row records old→new |
| **SGI-D** | per accepted decisions | per item |
| **SGI-E** | cleanup + export + evidence chip | tsc/vitest clean; export.xlsx PK-magic + rows == register; dead symbols gone (grep) |

Suggested order: **A → B** (one review cycle, both code-only) → **C** → D/E by
appetite. Estimated: A+B one working session; C one session; D/E half each.

## §6 Migrations

| Number | Content | Status |
|---|---|---|
| V296 | SGI-C columns (`value_source`, `source_metric_id`, `source_scope`) + SGI-D verification columns if decision #3 = yes | reserved |
| V297 | SGI-D `esg_targets.sgi_commitment_id` if decision #5 = yes | reserved |

All idempotent, applied twice to dev via pyodbc (Flyway off in dev), prod picks
them up via baseline-aware Flyway. **No `ck_ca_source` change needed** (SGI-B
reuses the whitelisted `SGI_COMMITMENT` source with a `:milestone` ref suffix).

## §7 API & UI delta summary

| Change | Kind |
|---|---|
| `POST /sgi-commitments/{id}/achieve` → APPROVE guard, no body, Rules C+D | breaking for API callers that sent `{achievedBy}` (none in-product after FE update) |
| `POST /sgi-commitments/{id}/retire` → APPROVE guard, body `{reason}` only | same |
| `POST /sgi-commitments/sweep` | new |
| `POST /sgi-commitments/{id}/refresh-value` | new (SGI-C) |
| `GET /sgi-commitments/export.xlsx` | new (SGI-E) |
| SGI tab: dialogs simplified, sweep button, badges, source picker, evidence chip, export button | additive |

## §8 Verification plan

Per house rules: full suite (`BUILD SUCCESS` + `Tests run` grepped from the
log), ratchet stays ≤ 62 (new endpoints carry real guards), live probe through
the Vite proxy as `admin` + `so_officer` (4-eyes paths), `[REVIEW-TEST]` rows
cleaned via CHARINDEX, migrations double-applied. New unit tests: Rule C/D in
`SgiCommitmentServiceTest`, sweep idempotency, refresh-value math.

## §9 Decisions needed (before SGI-A starts)

1. **SoD mechanism** — direct APPROVE + Rule C (recommended: consistent with
   CMA/TCFD/GHG, zero new moving parts) **or** workflow-engine routing (G6)?
2. **Evidence gate on achieve (Rule D)** — enforce (recommended) or advisory?
3. **Verification state (G5)** — add now (V296) or accept "n/a" in the
   assurance view as documented scope?
4. **Sweep cadence** — daily 03:00 UTC (recommended) or weekly?
5. **Target linkage (G7/V297)** — include or defer?

## §10 Out of scope

Public SGI disclosure portal; SMTP delivery of milestone reminders (in-app
notification only until a relay exists — `SMTP_*` in external.properties);
historical revaluation of already-ACHIEVED commitments; national-level (multi-
tenant rollup) SGI reporting.
