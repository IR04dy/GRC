# TSS GRC Platform — Independent Assessment & Remediation Plan
### تقييم مستقل لمنصة الحوكمة والمخاطر والامتثال وخطة المعالجة

**Assessment date:** 2026-07-25 · **Subject:** TSS GRC Platform (`D:\AYMAN\GRC`, remote `Ayman-Server/Claude-GRC-2`)
**Code state assessed:** `master` @ `00ba890e`; working branch `feat/change-policy-remediation` @ `22462275`; open worktree `fix/break-glass-sweep-tx` @ `ccc06799`
**Environments inspected:** dev DB `TSS_GRC` (SQL Express) · **live production** stack `:80` / `:8090` / `:8091` (`TSS_GRC_Deployment`)
**Assessor stance:** independent. I did **not** accept the platform's own documentation as evidence. Every material claim was re-tested against source, database and the running system, and I actively searched for disconfirming facts.

---

## §1 Scope, basis and declared limitations

### 1.1 What was assessed
| Party | Artifacts treated as *claims to be tested* |
|---|---|
| **Builder / vendor** | `PLATFORM_ARCHITECTURE_PACK.md`, `PLATFORM_USER_STORIES.md`, `PROJECT_CHRONICLE.md`, 9 × `*_PLAN.md` programme docs, `CLAUDE.md` |
| **Third-party reviewer** | `GRC_REVIEW_FOUNDATION_PACK.zip` (18 files, 2026-07-25, authored for "Codex" from a ChatGPT session) |
| **Original design authority** | `docs/analysis/*` (16), `docs/architecture/*` (18), `docs/Saudi-GRC-Platform-Integration/*` (16 sections) — the V1-era corpus |
| **Prior audit** | `reports/audit-report.html` (2026-06-18, 287 findings) + `audit/` data |
| **Ground truth** | The source tree, the migration set, the dev database, and the running production instance |

### 1.2 Declared conflict of interest — material
**All existing architecture documentation, module grades, user stories and the defect register were authored by the party that built the platform.** Before this engagement, no independent verification of those documents existed. For a platform being positioned at regulated Saudi buyers, that is itself a finding (**F-9**). Accordingly this assessment re-derived its own evidence; where my measurement disagreed with the vendor's, both numbers are shown.

### 1.3 Limitations (what this assessment does *not* cover)
- No penetration test, no threat model review, no dependency/CVE scan, no load or DR test.
- No UX evaluation with real users; no Arabic linguistic review by a native GRC practitioner.
- No review of contracts, licensing entitlements, or customer evidence (none supplied).
- Single-environment data reality: dev + prod on one Windows host, **3 tenants, 7 users** — multi-tenant behaviour at scale is unproven.
- Test-suite results are as reported by the build; I ran targeted tests, not the full suite end-to-end.
- **Method integrity note:** my first heuristic scan reported 353 weakly-guarded write endpoints. On investigation this was inflated by class-level annotation patterns; the vendor's own instrumented test returns **62**, which I accept as authoritative and use throughout. I record the correction rather than the convenient number.

---

## §2 Summary opinion

> ### **QUALIFIED — substantively strong engineering, but not yet independently assurable.**
>
> **Independent overall maturity: 3.4 / 5.**
>
> This is **not** a demo-ware platform. 184k LoC of backend, 458 tables, 2,154 endpoints with 859 write endpoints carrying real permission checks, a genuine 2,322-test suite, CI on every push, an enforced authorization ratchet, working four-eyes/SoD gates that return real 409s, hash-stamped evidence, e-signed committee minutes, and Arabic populated as a first-class column rather than a translation layer. The engineering discipline evident in the migration hygiene (expand-contract, idempotent, applied twice) and the live-probe habit is **above** what I typically find in products of this breadth.
>
> **However, I cannot sign an unqualified opinion**, for six reasons — each evidenced in §4:
> 1. The **governance core is untested**: the committee/meeting/voting engine (the system of record for change and policy approval, quorum, recusal and e-signed minutes) has **zero automated tests**, as does the **permission evaluator** on which all 859 guards depend.
> 2. **Function-level authorization has a material residual**: ~35 of the 62 ratchet-exempt write endpoints are real business writes, including **e-signature create/sign/reject**, RCA **approve**, plan **attest** and BCM compliance-mapping mutation — reachable by any authenticated principal, including a read-only viewer.
> 3. The **flagship committee gate is unconfigured** in the reference environment: `committee_gate_map`, `committee_referral`, `committee_meeting_attendee`, `committee_resolution_vote` are **all empty** — the control exists in code and is dormant in data.
> 4. **20 modules have no tests at all**, including compliance-relevant verticals (SAMA `outsourcing`, `sharia`, `openbanking`, `fraud`, `rrp`, `opres`, `regreporting`, `reginspect`).
> 5. **Mock data is wired into live UI at 52 import sites across 15 modules** — a silent-fallback-to-fabricated-data risk in a system of record.
> 6. **Two known defects were live in production during this assessment**, both with finished fixes that were never deployed — a release-discipline gap, not a coding gap.
>
> **Fit for purpose:** acceptable for **pilot / reference deployment** in the strong domains (policy & compliance, risk & KRI, workflow & approvals, evidence, ESG/GHG) with the §7 conditions. **Not yet** supportable for unqualified enterprise or regulated commercial claims, and specifically **not** for claims about committee governance, e-signature integrity, or the thin regulatory verticals.

---

## §3 Triangulation — where the parties agree and disagree

| Question | Builder | Third-party pack | V1 design corpus | **Independent finding** |
|---|---|---|---|---|
| Platform scale | 59 modules, 458 tables, 2,154 endpoints, 99 perms, 57 roles, 211 rules, 328 routes | repeats the builder | pre-dates | ✅ **All verified accurate.** |
| Core-domain maturity | A / A− across 10 domains | accepts the grades | claims full coverage | ⚠️ **Directionally right, but "A" conflates *code present* with *assured*.** Committee engine graded A has 0 tests; ESG graded A has 25 test files. Grades measure remediation effort, not assurance. |
| Authorization | "ratchet 62, only goes down" — framed as an achievement | not tested | — | ✅ 62 confirmed by the vendor's own test. ❌ **But the docs claim the residual floor is "legitimately-public endpoints"; it is not** — see F-1. |
| Thin verticals | Honestly graded **C**, "don't sell as mature" | echoes the warning | markets them as full modules | ✅ **Builder is right and the V1 corpus is misleading.** Confirmed: those modules also have zero tests. |
| `privacy_lc` | Graded **D**, orphaned, "do not seed" | echoes | lists as a feature | ✅ Confirmed — 3 of 4 tables empty. Builder's honesty holds. |
| Live defects | DF-1..DF-5 disclosed | "fix these first" | silent | ✅ **Both P0 defects confirmed live**; DF-1 failing 33× on assessment day. Disclosure was honest; **remediation delivery was not**. |
| Source availability | n/a | *"no source code available; where is the repository?"* | n/a | ❌ **Third-party premise false** — code is on the same host. Its 40-stage method is calibrated for a system it never saw. |
| Bilingual readiness | mandatory, enforced | mandatory | requirement | ✅ **Substantiated** — Arabic populated 93–100 % in sampled registers. |
| Secret hygiene | config outside the jar | untested | — | ✅ **Clean** — no hardcoded secrets in packaged config; `external.properties` untracked. |
| Roadmap position | post-remediation, next = separation + CAB | proposes "v2: inventory & understand" | — | ❌ **Third-party roadmap re-buys shipped work** (inventory, permission/audit centralisation, licensing — the last already exists in `platform/licensing` with a runtime `LicenseGuardAspect`). |

**Reading:** the builder's documentation is unusually candid — it volunteers its own C/D grades and live defects, which independent testing upheld. Its weakness is not dishonesty but **conflating "built and probed once" with "assured and operating"**. The third-party pack adds a valuable commercial/AI lens but is built on a false premise and would divert effort into re-documentation while a security control fails in production.

---

## §4 Independent test log

| # | Test | Method | Result | Verdict |
|---|---|---|---|---|
| **T-1** | Guard coverage | AST-style scan of all 255 `@RestController`s (1,216 write mappings) + ran the vendor's `ControllerAuthorizationRatchetTest` | 859 writes carry `hasPermission`/`hasAnyRole`; **62** lack a real authority check (4 have no `@PreAuthorize` at all); ratchet passes | ⚠️ **F-1** |
| **T-2** | Test coverage vs claimed maturity | Mapped 239 test files / 2,322 tests against 59 module dirs | **20 modules: zero tests.** No test anywhere matching *committee / meeting / resolution*; **`Permission*Test` = 0**; `Signature*Test` = 1; `Tenant*Test` = 2 | ❌ **F-2, F-3** |
| **T-3** | Are controls *operating* or merely *present*? | Row counts across all 458 tables | `admin.committee_gate_map` **0**, `committee_referral` **0**, `committee_meeting_attendee` **0**, `committee_resolution_vote` **0**; `iam.break_glass_events` **0**; `iam.access_review_items` **0**; `intake.*` **0**; `audit_committee.*` **0**; `risk_lc` 4/7 empty; `workflow.recipient_lists` **0** | ❌ **F-4, F-6** |
| **T-4** | Is the UI backed by real data? | Enumerated mock modules and their importers | **15 mock modules, all imported by live code — 52 sites** (compliance ×13, risk ×6, tprm ×5, evidence ×5, workflow ×4, privacy ×4, changes ×3, bcm/cyber/incidents ×2, admin/ethics/reporting/scm ×1) | ❌ **F-5** |
| **T-5** | Production defect status | Crafted-token probe against `:80`; prod log analysis | Bogus-signature refresh cookie → **HTTP 500** (no-cookie and malformed correctly 401). `break-glass expiry sweep failed` **33× on 2026-07-25**, last 16:26:35. Both fixes exist in code; neither deployed | ❌ **F-7** |
| **T-6** | Bilingual substance | Null/empty `*_ar` census on key registers | `policy.documents` 3/41 missing Arabic; `change_requests` 0/15; `compliance.controls` 0/35 | ✅ Claim upheld |
| **T-7** | Secret & config hygiene | Grep of packaged config for literal credentials; git tracking of prod config | No live secrets in `src/main/resources`; `external.properties` **not** tracked; JWT secret fail-fast + rotation-overlap present | ✅ Claim upheld |
| **T-8** | Governance-document integrity | Compared repo docs, the third-party pack and `AGENTS.md` | `AGENTS.md` is a byte-identical copy of `CLAUDE.md` with a blind `Claude`→`Codex` substitution, asserting a **non-existent remote** (`Codex-GRC-2`); third-party pack's `source/` copies already one revision stale; V1 `docs/` corpus contradicts as-built | ⚠️ **F-8** |

---

## §5 Findings register

Severity: **High** = blocks an unqualified commercial/regulated claim · **Medium** = must be scheduled · **Low** = hygiene.

### F-1 · Function-level authorization residual on business-write endpoints — **High**
**Evidence.** The 62 ratchet-exempt write endpoints are not, as the vendor's ADR-006 narrative states, essentially "the small set of legitimately-public write endpoints". Classifying the actual list:
- *Legitimately public* (~8): auth login/refresh/logout, whistleblower `publicSubmit`/`publicReply`, complaints `submitPublic`, vendor BCP `submitByToken`, public workflow `respond` — all rate-limited; acceptable.
- *Self-scoped, low risk* (~10): notification mark-read, saved views, web-push register, `revokeAllMine`, help `trackView`.
- *Service-compensated* (~4): `ApprovalRequestController#create/transition` — `ApprovalRequestService` throws `AccessDeniedException` unless the caller is the assignee, with principal-based attribution (SEC-IAM-002). A genuine compensating control.
- **Genuinely exposed business writes (~35)**: `DigitalSignatureController#create/sign/reject/expire` and `SignatureController#sign` (**e-signature**), `RootCauseAnalysisController#create/submit/approve/reject/update/generateCapas`, `PlanReviewController#attest/sweep`, `PlanDistributionController#distribute/acknowledge/decline/markStale`, `BcmComplianceMappingController#assess/attachEvidence/attachGap/create/update`, `BcmCommunicationContactController#create/update/delete/recordTestResult`, `BcmSettingsController#update/reset`, `BcmLibraryController#clonePlanTemplate`, `CrisisAarController#generate/transition/update`, `ExerciseRuntimeController#respond`, `AiSuggestionController` ×8, `AiControlMappingController#suggest`.

**Impact.** Any authenticated principal — including the seeded read-only `viewer` — can reach signature creation/signing, RCA approval, plan attestation and BCM compliance-mapping mutation. Attribution is still the authenticated principal (so impersonation is not the risk); the risk is **unauthorized-but-attributable action**, which in a GRC system of record undermines the very control the platform sells (ADR-019 tamper-evidence, US-EV/US-CM story claims). BCM settings reset by any user is additionally a availability/integrity concern.
**Recommendation.** Treat the 62 as three buckets in code, not one number: annotate the public floor explicitly (`@PublicEndpoint` marker), fix the ~35 business writes with real `hasPermission('MODULE','ACTION')` guards, and **lower the ratchet baseline to the documented floor (~18)** so it can never silently re-absorb business endpoints. Prioritise the 5 signature endpoints.

### F-2 · The governance core is untested — **High**
**Evidence.** No test file anywhere in `src/test` matches *committee*, *meeting* or *resolution* (the only hits are the unrelated `audit_committee` dashboard). `module/admin` — 48 production files including the entire committee engine — has **zero** tests. `Permission*Test` count is **0**: `ModulePermissionEvaluator`, the single mechanism behind all 859 guards, has no unit test.
**Impact.** Quorum arithmetic, COI/referral recusal, vote tallying, minutes e-signing and the change/policy approval gate — the controls the platform's governance claims rest on — are protected only by manual probes that self-clean. A refactor could silently break quorum or recusal with a green suite. The permission evaluator is a single point of authorization failure with no regression net.
**Recommendation.** Before any further module separation: unit tests for quorum (met/not-met/edge), recusal forcing, vote tally exclusion of RECUSED, minutes-hash stability, and the referral gate's 409 paths; plus a `ModulePermissionEvaluator` test matrix (grant/deny/PLATFORM_ADMIN bypass/unknown module). This is the single highest-value assurance investment available.

### F-3 · 20 modules with zero automated tests — **High**
**Evidence.** `admin` (48 files), `data_officer` (23), `sharia` (16), `cpty`/`openbanking`/`outsourcing`/`rrp` (14 each), `dataclass` (12), `fraud`/`opres`/`reginspect`/`regreporting` (10 each), `attachment`, `audit_eng`, `board_pack`, `controlmap`, `dashboard`, `knowledge`, `nca_ecc`, `datagov`.
**Impact.** These include the SAMA outsourcing vertical and several regulator-facing packs. Untested + thin (3–6 tables) + unreviewed is the profile most likely to fail a buyer's acceptance test.
**Recommendation.** Do not include any zero-test module in a commercial scope statement. Minimum bar per sellable module: happy-path service test + one authorization-denial test + one tenant-isolation test.

### F-4 · Flagship committee gate is unconfigured — **High**
**Evidence.** `admin.committee_gate_map` = **0 rows** in the reference environment; `committee_referral`, `committee_meeting_attendee`, `committee_resolution_vote` also **0**.
**Impact.** The gate map is what routes a change's CAB/ECAB authority to a committee. With no rows, **no change in this environment is actually committee-gated** — every change falls back to the legacy approval tally. The vendor's strongest governance claim (Track D) is therefore *implemented but not in force*, and there is no retained evidence of it ever having operated (probe rows are CHARINDEX-deleted by design).
**Recommendation.** Ship a seeded default gate map per tenant; add a **configuration-health check** that fails loudly (dashboard + startup warning) when a tenant has authorisation-matrix rows demanding CAB/ECAB but no gate mapping. Retain at least one non-deleted worked example per control as standing evidence.

### F-5 · Mock data wired into live UI — **Medium-High**
**Evidence.** 15 mock modules, **all** imported by live pages/components across 52 sites — heaviest in `compliance` (13), `risk` (6), `tprm` (5), `evidence` (5). The vendor's own debt register flags only `mockChanges.ts`.
**Impact.** Where mocks act as error-fallbacks, a backend failure renders **plausible fabricated register rows instead of an error** — in a GRC system of record that is an integrity and audit-evidence risk (a reviewer could be shown data that does not exist). Where they are primary, the screen is a façade.
**Recommendation.** Classify all 52 sites as *fallback* or *primary*. Fallbacks must be replaced by explicit empty/error states (the third-party pack's UX constitution mandates exactly this). Primary usages must be repointed to canonical APIs or removed. Add a lint/CI rule forbidding `mock*` imports outside test files.

### F-6 · Hollow features — capability present, never operated — **Medium**
**Evidence.** `intake` both tables empty; `audit_committee` both empty; `iam.break_glass_events` 0 (yet its expiry sweep is the DF-1 defect); `iam.access_review_items` 0 (recertification never ran with items); `risk_lc` 4/7 empty; `workflow.recipient_lists` 0; `platform.translations` 0; `platform.saved_views` 0.
**Impact.** These appear in the module catalogue and user stories as delivered capability. An acceptance committee testing them would find no operating history. This is precisely the "a screen is not a workflow" trap.
**Recommendation.** Mark each as *Available — no operating evidence* in the module scorecard, and either seed a demonstrable worked example or exclude from scope claims.

### F-7 · Fixes are written but not delivered — **Medium (process)**
**Evidence.** DF-1 break-glass sweep: fix complete on `fix/break-glass-sweep-tx` (`5dd05fde`, suite 2322/0/0) — **unmerged**, so production failed the sweep **33 times on assessment day**. DF-2 stale-cookie 500: fix merged to `master` (`00ba890e`) and the jar **staged but never activated** — I proved the 500 still returns on `:80`.
**Impact.** A security control (expiry of emergency access) is not running in production while a verified fix sits on a branch. The gap is deployment discipline: there is no gate that says "a High defect with a merged fix must reach production within N hours".
**Recommendation.** Define a defect-to-production SLA for High/Critical, and add a release checklist item that reconciles "fixed in code" against "running in prod" (the deployed jar's build ID should be surfaced on `/actuator/info` and on an admin screen).

### F-8 · Governance-document integrity — **Medium**
**Evidence.** `AGENTS.md` (untracked, 181 lines) is `CLAUDE.md` with a blind `Claude`→`Codex` replacement, asserting remote `Ayman-Server/Codex-GRC-2` — which does not exist (real: `Claude-GRC-2`). The third-party pack embeds a file-transfer artifact ("null-byte versions should be ignored") inside a governing document and copies rather than references its baseline (already one revision stale). The V1 `docs/` corpus (50 files) still markets grade-C verticals as full modules.
**Impact.** Instruction and design documents are being propagated by copy/find-replace, so errors become "facts" for the next reader or agent. Contradictory corpora make it impossible to tell which document governs.
**Recommendation.** One governing set, in-repo, referenced by path + commit SHA — never copied. Correct or delete `AGENTS.md`. Stamp the V1 corpus `HISTORIC — superseded` at the top of each file.

### F-9 · No independent assurance before this engagement — **Medium (governance)**
**Evidence.** Every architecture document, maturity grade, user story and defect entry was authored by the builder. The prior 287-finding audit is referenced but its closure status is not independently reconciled anywhere.
**Impact.** For regulated buyers, self-certified assurance is not assurance. The grades are also structurally optimistic because they measure *remediation activity* rather than *verified control operation* (F-2/F-4 are the concrete consequence).
**Recommendation.** Adopt the third-party pack's acceptance-committee model, but with evidence produced by a party that did not build the feature: convert the 84 user stories into an executable probe suite run in CI, and reconcile the 287 audit findings to closed/open with evidence per finding.

---

## §6 Independent domain ratings

1–5 scale (the third-party pack's, so results are comparable). **Assurance** = is there automated evidence the control works?

| Domain | Vendor grade | **Independent** | Assurance | Delta rationale |
|---|---|---|---|---|
| Workflow engine + approvals | A | **4** | 6 tests | Real engine, 14 adopters; approval-chain/request writes sit in the 62 (service-compensated) |
| Policy & document control | A | **4** | 9 compliance tests | Genuinely strong: forced DRAFT, SoD 409, doc-gate, framework filter verified live |
| Risk & KRI | A | **4** | 14 tests | Solid; `risk_lc` largely unused (F-6) |
| Evidence subsystem | A− | **4** | 9 tests | Hash + four-eyes real; `change_mgmt` still not a consumer |
| ESG / GHG / green finance | A | **4** | 25 tests | Best-tested domain; evidence-gated verification is a genuine differentiator |
| BCM | A | **3.5** | 30 tests | Most tests of any module, but **many BCM write endpoints are in the 62** (F-1) |
| Cyber / SOC ops | A | **3.5** | 20 tests | Broad and tested; TRIAGE gating verified |
| Change enablement | A− | **3.5** | 2 tests | Lifecycle guards real and probed; separation pending; only 2 test classes |
| **Committee governance** | **A** | **2** | **0 tests, 0 data** | ⬇⬇ Biggest divergence: the control the governance story depends on is untested **and** unconfigured (F-2, F-4) |
| IAM / access governance | A | **3** | 2 tenant tests | SoD engine real; break-glass never exercised + its sweep was failing in prod |
| Exports / reporting | A | **4** | 9 tests | RTL/Arabic substantiated |
| Privacy (PDPL) | B | **3** | 7 tests | Reasonable; not deep-reviewed — vendor is fair |
| TPRM / SCM / outsourcing | B | **2.5** | 10 tprm, **0 outsourcing** | SAMA outsourcing vertical untested |
| Audit lifecycle, ethics/AML, NDI, AI gov | B | **2.5** | thin | Fair grade; unverified |
| Regulatory micro-modules (10) | C | **2** | **0 tests** | Vendor's warning confirmed — do not sell as mature |
| `datagov` / `privacy_lc` / legacy UI | C / D / D | **1** | none | Stub / orphaned / superseded |
| **Platform kernel** (tenancy, audit, automation, licensing) | A | **3.5** | 21 tests, 0 permission tests | Licensing with runtime enforcement genuinely exists; **permission evaluator untested** |
| **Overall** | ~A− implied | **3.4 / 5** | — | Strong build, uneven assurance |

---

## §7 Conditions for an unqualified opinion

These six are **blocking** for enterprise/regulated commercial claims. Each is verifiable.

| # | Condition | Evidence required to clear |
|---|---|---|
| **C-1** | Business-write endpoints carry real authority checks | The ~35 endpoints in F-1 guarded by `hasPermission`; ratchet baseline lowered to the documented public floor (~18) and passing; a `viewer`-role probe returns **403** on signature/attest/RCA-approve |
| **C-2** | Governance core has regression cover | Committee quorum/recusal/tally/minutes-hash/gate-409 test suite green; `ModulePermissionEvaluator` test matrix green |
| **C-3** | Committee gate in force | Seeded gate map per tenant + a config-health check that fails when the authorisation matrix demands CAB/ECAB without a mapping; one retained worked example end-to-end |
| **C-4** | No fabricated data reachable from live UI | All 52 mock import sites eliminated or converted to explicit empty/error states; CI rule blocking `mock*` imports in `src/pages` and `src/components` |
| **C-5** | Live defects closed in production | DF-1 merged + deployed (sweep WARN count = 0 over 24 h); DF-2 bogus-signature probe returns **401**; deployed build ID visible on `/actuator/info` |
| **C-6** | Scope statement matches assurance | Module scorecard published with *Assurance* and *Operating-evidence* columns; zero-test and zero-data modules excluded from commercial claims |

---

## §8 Remediation plan

Sequenced by risk-reduction per unit of effort. Sizes: S ≤ 2 d, M ≤ 1 w, L > 1 w.

### Phase 0 — stop the bleeding (this week)
| ID | Action | Size | Clears |
|---|---|---|---|
| R-01 | Merge `fix/break-glass-sweep-tx`; rebuild; **activate the staged jar** in the same restart; verify sweep-WARN = 0 and refresh-probe = 401 | S | C-5 / F-7 |
| R-02 | Guard the **5 e-signature write endpoints** (`DigitalSignatureController` ×4, `SignatureController#sign`) with `hasPermission('SIGNATURE',…)`; probe `viewer` → 403 | S | C-1 (highest-risk slice) |
| R-03 | Correct or delete `AGENTS.md`; stamp the V1 `docs/` corpus `HISTORIC — superseded` | S | F-8 |
| R-04 | Surface the deployed build ID on `/actuator/info` + an admin screen | S | F-7 |

### Phase 1 — assure the governance core (2–3 weeks)
| ID | Action | Size | Clears |
|---|---|---|---|
| R-05 | Committee-engine test suite: quorum met/not-met/boundary, COI + referral recusal forcing, tally excluding RECUSED, minutes-hash stability, referral-gate 409 paths | M | C-2 / F-2 |
| R-06 | `ModulePermissionEvaluator` test matrix (grant, deny, PLATFORM_ADMIN bypass, unknown module/action, tenant mismatch) | S | C-2 |
| R-07 | Seed default `committee_gate_map` per tenant + startup/dashboard config-health check for unmapped gates; retain one worked committee decision as standing evidence | M | C-3 / F-4 |
| R-08 | Guard the remaining ~30 business writes (RCA, PlanReview/Distribution, BCM mapping/contacts/settings/library, CrisisAar, ExerciseRuntime, AI endpoints); introduce an explicit `@PublicEndpoint` marker; **lower the ratchet to ~18** | M | C-1 |

### Phase 2 — make the UI truthful (2–4 weeks)
| ID | Action | Size | Clears |
|---|---|---|---|
| R-09 | Audit all 52 mock import sites; classify fallback vs primary | S | C-4 / F-5 |
| R-10 | Replace fallbacks with empty/error states; repoint or delete primaries; CI rule blocking `mock*` imports outside tests | L | C-4 |
| R-11 | Adopt the third-party pack's mandatory product states (empty / error / approval / rejection / escalation / completion) as FE acceptance criteria | M | quality |

### Phase 3 — evidence-based scope (3–4 weeks)
| ID | Action | Size | Clears |
|---|---|---|---|
| R-12 | Convert the 84 user stories into an **executable probe suite in CI** (persona × permission × expected status × artifact) — the single best assurance investment after R-05 | L | C-6 / F-9 |
| R-13 | Module scorecard with *Assurance* + *Operating-evidence* columns; exclude zero-test/zero-data modules from commercial scope | M | C-6 / F-3 / F-6 |
| R-14 | Minimum test bar for every sellable module (happy path + authorization denial + tenant isolation) — start with SAMA `outsourcing` | L | F-3 |
| R-15 | Reconcile the 287 audit findings to closed/open with evidence per finding | M | F-9 |

### Phase 4 — structure & strategy (deferred, not blocking)
Module boundary tests + Change Enablement separation (the vendor's CE-1…CE-16 plan is sound — but **R-05/R-08 must precede it**: do not refactor an untested governance core). Then the third-party pack's genuinely additive material: commercial packaging, Saudi readiness scorecards, and the AI transformation strategy with its governance gates.

---

## §9 What I would need to lift the qualification

1. Green CI run including the **new** committee + permission suites (R-05/R-06).
2. A `viewer`-role probe report showing **403** on every endpoint in F-1's exposed set.
3. 24 h of production logs with **zero** break-glass sweep failures, and a `:80` refresh probe returning 401.
4. A retained, non-deleted end-to-end committee decision (agenda → quorum → votes → e-signed minutes → change gated) in a live tenant.
5. Zero `mock*` imports outside test files.
6. A module scorecard whose commercial scope contains no zero-test, zero-data module.

On that evidence I would expect the overall rating to move to **4.2–4.5 / 5** and support an unqualified opinion for the strong domains.

---

## §10 Assessor's closing note

The instinct to fix defects before writing more documentation — the third-party pack's own first instruction — is correct, and I would go further: **the platform's problem is not that too little has been built, but that too little of what has been built is independently provable.** Breadth is already exceptional for a product of this age. The gap between 3.4 and 4.5 is not new features; it is tests on the governance core, guards on ~35 endpoints, honest empty states instead of mocks, a configured committee gate, and a deployment discipline that gets finished fixes into production. Five of the six blocking conditions are days-to-weeks of work, not a rearchitecture.

I would also caution against the third-party pack's architectural constitution: its "no cross-module database access / modules must not query each other" rule, applied literally to a deliberately-chosen modular monolith at 3-tenant scale, would trigger a multi-year rewrite that none of the findings above justify. The enforceable version — import-boundary ports with a ratchet test — buys the coupling discipline at a fraction of the cost.

---

### Appendix A — reproduction
| Test | Command / artifact |
|---|---|
| T-1 guards | `scratchpad/assess_guards.py`, `scratchpad/assess_isauth.py`; `mvn -o test -Dtest=ControllerAuthorizationRatchetTest` |
| T-3 / T-6 data | `scratchpad/dev_sql.py "<query>"` (row-count census, Arabic census) |
| T-5 DF-2 proof | `scratchpad/probe_df2.py` — crafts a well-formed JWT with a bogus signature against `:80` |
| T-5 DF-1 proof | `Select-String -Path 'D:\Deployment\tss-grc\app\WinSW.NET4_20260725.out.log' -Pattern 'break-glass expiry sweep failed'` |
| T-2 coverage | test-file census under `tss-grc-backend/src/test/java/com/tss/grc/module/` |
| T-4 mocks | mock-module enumeration + importer grep under `tss-grc-ui-v2/src/` |

### Appendix B — related documents
Vendor: `PLATFORM_ARCHITECTURE_PACK.md` · `PLATFORM_USER_STORIES.md` · `PROJECT_CHRONICLE.md` · `CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md`
Third-party: `GRC_REVIEW_FOUNDATION_PACK.zip` → assessed in `FOUNDATION_PACK_ASSESSMENT_AND_ACTION_PLAN.md`
Prior audit: `reports/audit-report.html` (287 findings, 2026-06-18) — closure **not** independently reconciled (F-9)

*Independent assessment prepared 2026-07-25. Opinion: **QUALIFIED**, 3.4/5, with six clearable blocking conditions. All findings are reproducible via Appendix A.*
