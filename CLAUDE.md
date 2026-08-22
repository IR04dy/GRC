# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Code path & companion docs

Working tree: `D:\AYMAN\GRC` — remote `github.com/Ayman-Server/Claude-GRC-2` (branch `master`).

- **`PROJECT_CHRONICLE.md`** — the master reference: business + functional reference for
  every module/screen/API, installation, configuration, operations, the full
  troubleshooting encyclopedia, all 28 developer landmines, and the complete
  development history. **Read it before deep work; this file stays lean on purpose.**
- `DEPLOYMENT.md` — Windows Server go-live runbook (WinSW services + nginx).
- `*_PLAN.md` at repo root — per-program review/remediation plans; each carries a
  §0 implementation-status tracker (what shipped, commits, migrations).
- `LOCAL_SETUP.md` — dev-machine bring-up. `reports/audit-report.html` — the platform audit.
- `README.md` / `STATUS.md` / `SESSION_SUMMARY.md` / `sprints/` — V1-era docs (historic; STATUS.md snapshot is 2026-04-22 and predates all remediation programs).

## Project overview

**TSS GRC Platform** — bilingual (Arabic primary / English), multi-tenant, enterprise
Governance, Risk & Compliance platform for Saudi Arabia / MENA. Modular-monolith
Spring Boot REST backend + **React SPA (`tss-grc-ui-v2`)**. No SSR.
Modules: risk (ERM/KRI/FAIR), compliance & policy, cyber + SOC ops (NCA ECC),
BCM (BIA/BCP/crisis/NCEMA), privacy (PDPL: RoPA/DPIA/DSR/breach), TPRM/SCM
(SAMA outsourcing), sustainability/ESG + green finance (GHG 15-category Scope 3,
CMA/TCFD/IFRS S1-S2, Green Sukuk/UoP/portfolio/GAR, carbon credits, ISAE 3410
assurance lineage), access governance (IAM: maker-checker requests, SoD engine,
recertification, break-glass), platform services (workflow engine adopted by 14
modules, evidence subsystem, exports, audit ledger, branding, committees).

---

## Commands

**JDK 17 is mandatory and NOT the shell default** (Git Bash picks a JDK 11 →
`mvn` fails "release 17 not supported"):

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17"
```

### Backend (Spring Boot, port 8084 in dev)

Required env vars — dev refuses to boot without real values:
- `JWT_SECRET` — ≥32 chars, must not contain `change-this`/`placeholder`
  (dev value in use: `tss-grc-dev-jwt-secret-0123456789-abcdefghij-KLMNOP`).
- `DB_PASSWORD` — dev SQL Server `sa` password: `tss.123`.

```bash
cd tss-grc-backend
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=<secret> DB_PASSWORD=tss.123 mvn spring-boot:run
# profile MUST be the env var: -Dspring.profiles.active on the mvn CLI never reaches the
# forked app JVM → boots "default" (Flyway ON, user grc_app) and hangs in Hikari retries
# API http://localhost:8084 · Swagger /swagger-ui.html (dev only) · health /actuator/health

mvn -o clean package        # full build + all UNIT tests (3,779 @ 2026-08-12; ~1 min hot, 3–5 cold)
                            # hermetic: needs no database and no secrets — keep it that way
# ⚠ That count is DATED, not maintained: it read 3,354 for weeks while the suite grew past 3,450,
# and unlike the migration head or the ratchet ceilings NO test can gate it (a run cannot know its
# own total). Read it as "roughly this many, and growing" — the authority is the run's own
# `Tests run:` line. Same class as F2-162: an ungateable number stated precisely goes stale.

# Integration tests (*IT.java) — real HTTP + the real dev SQL Server. W1-5/F2-18.
JWT_SECRET=<secret> DB_PASSWORD=tss.123 mvn -o verify -Pit
# ⚠ NOT -DskipTests: failsafe honours it too, so the build goes green having run NEITHER
#   suite. Missing env is a hard failure with instructions, never a skip.
# ⚠ The `it` profile is why `mvn -o clean package` stays database-free. Do not promote
#   failsafe out of it — but do not let the ITs rot either: CommitteeGoldenPathContractTest
#   runs in the normal suite and fails if the wiring or any declared step disappears.
mvn test -Dtest=ClassName#method
java -jar target/tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Stop the running backend before `mvn clean` (jar lock) — kill by port
(`netstat -ano | findstr :8084`; DevTools = 2 java PIDs). When launching in a
background wrapper, **grep the log for `BUILD SUCCESS` / `Tests run:`** — wrapper
exit codes can lie.
⚠ **That landmine has a second, quieter mode: `mvn clean` KILLS a running dev backend and
still reports `BUILD SUCCESS`** (measured 2026-08-09). Under `spring-boot:run` the app runs
from `target/classes`, so `clean` deletes the code out from under it — no jar lock, no build
error, just a dead listener. The failure surfaces later as `ConnectionRefused` from the next
live probe, which reads like the probe's bug. **Check the port before probing, not after.**
⚠ **For `spring-boot:run`, `BUILD SUCCESS` is not a start signal** (measured 2026-08-07).
DevTools' `RestartLauncher` swallows the app's failure, so Maven printed `BUILD SUCCESS` in
5.059 s over an application that never bound a port. **Grep `Started GrcApplication in`**
instead. That run died on `ApplicationTemp`: *"Existing directory 'C:\…\Temp\<hash>' is
not owned by BUILTIN\Administrators"*, reported under a Tomcat `prepareContext` trace that
reads like a port conflict. Cause: the dir was created by a **non-elevated** run and that
shell was elevated — ownership, not permissions.
⚠⚠ **`takeown` is the WRONG fix and this line recommended it for one session.** The assertion
is bidirectional: `takeown /f <dir> /a /r /d y` gives the dir to `BUILTIN\Administrators`, and
the next **non-elevated** launch then fails with the mirror message (*"is not owned by
`<HOST>\User`"*). Elevation is not stable across this repo's tool surface — the Bash tool and
the PowerShell tool do not necessarily agree — so ownership-flipping trades one broken launch
for the other. **Give the JVM its own temp dir instead** (measured working from both):

```
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.io.tmpdir=<a dir this shell created>"
```

### Frontend — `tss-grc-ui-v2` (React + Vite + TS; the primary UI)

```bash
cd tss-grc-ui-v2
npm run dev          # http://localhost:5173 — proxies /api and /ws → :8084
npx tsc --noEmit     # typecheck
npx vitest run       # unit tests
npx vite build       # production bundle → dist/
```

The FE calls same-origin `/api/*`; the Vite proxy forwards to :8084. **If the
backend is down, every button in the UI fails at once** — check :8084 first.
`tss-grc-ui/` is the legacy static frontend (superseded; `python server.py 8086`).

### Database (dev — Flyway is OFF in dev)

SQL Server Express `localhost:1433`, DB `TSS_GRC`, `sa/tss.123`. Dev schema is
**Python-bootstrapped** (`setup_cyber_db.py`, `setup_bcm_db.py`,
`setup_committees_db.py`, `fix_arabic_db.py`), demo data via `seed_real_data.py`
(idempotent, 3 Saudi tenants). Use the `py` launcher (not `python`), pyodbc +
ODBC Driver 17.

Migrations live in `tss-grc-backend/src/main/resources/db/migration/sqlserver/`
— **V1→V391 packaged; next free V392** (379 files; ⚠ this line said "V1→V295 / next free
V296" for weeks, 54 behind — reusing a taken version number is a Flyway checksum failure on
the deployed server, so **read the directory, not this line**: `ls .../sqlserver | tail -3`.
`Appendix/scripts/baseline_census.py` prints it and `MeasuredBaselineFreshnessTest` gates it — ⚠ **but until F2-266 it gated the figure in `MEASURED_BASELINE.md` and the census script's `PUBLISHED` dict and NOT this line**, so adding V385 while updating only those two left the suite green with this sentence reading V384/372. F2-265's finding in the file that records it. `claudeMdMigrationLineMatchesTheTree` closes it; if you reword the line, fix that pattern rather than deleting the assertion, because an unparsed line reports a clean tree).
⚠ **A grant migration must DECLARE its (role, module, action) triples as literals.** The house idiom
`INSERT … SELECT … JOIN iam.roles JOIN iam.permissions WHERE NOT EXISTS` grants **nothing** when
either side is catalogued later in the chain, and `NOT EXISTS` cannot tell *already granted* from
*nothing matched* — so it succeeds silently. Measured 2026-08-07: a fresh install was **225 grants**
short of dev (`ORG_ADMIN` −30, unable to read seven modules), and re-running the same folder adds
**875**. Never derive the pair from `iam.role_permissions` itself — V284's "READ-parity" clause is a
*snapshot*, not a rule; `RoleGrantDerivationRatchetTest` pins the 6 legacy sites and fails a 7th.
Convention for new migrations: write them **idempotent** with self-checks that `THROW`,
apply to the dev DB **twice via pyodbc** (Flyway off in dev), and let prod pick them up via
Flyway (baseline 292 on the deployed server; head **V344**, so **V345–V391 are pending** the
next jar — 47 files). `flyway.clean` is disabled — never run `mvn flyway:clean`.

### Default dev credentials

`admin/Admin@12345` (PLATFORM_ADMIN) · `so_officer/Admin@12345` (CISO +
SUSTAINABILITY_OFFICER — the standard second approver for 4-eyes flows) ·
`compliance/Admin@12345` (GRC_ADMINISTRATOR) · `auditor/Admin@12345` ·
**`ranalyst/Analyst@12345`** (corrected 2026-07-29 — this file said `Admin@12345`;
that password 401s, and a login attempt costs a lockout counter) ·
`rmanager/Risk@12345` · `viewer/Viewer@12345`.
Full persona list: PROJECT_CHRONICLE §4.

Grants matter as much as passwords when probing authorization. Read them from
`iam.role_permissions` rather than inferring from the username — the useful shapes
are `so_officer` (EVIDENCE_READ+WRITE, RISK_READ, **CYBER_READ *and* CYBER_WRITE** — this
line said READ only until 2026-08-06 and the omission cost a probe iteration; **no**
COMPLIANCE/BCM; ROLE_CISO), `ranalyst` (EVIDENCE+RISK+COMPLIANCE reads, **no** BCM/CYBER/PRIVACY/TPRM)
and `auditor` (EVIDENCE_WRITE + COMPLIANCE_WRITE + BCM_READ) — between them they cover
"holds the module permission", "holds the role but not the permission", and neither.

---

## House rules (established working conventions — follow them)

1. **Commit per verified slice**; work on a feature/fix branch.
2. **Never merge to master without the user's explicit "merge"** command.
3. **Authorization ratchet ≤ 21** — the suite enforces the count of unguarded
   write endpoints (`ControllerAuthorizationRatchetTest.BASELINE_UNGUARDED_WRITE_ENDPOINTS`);
   every new write endpoint needs a real `hasPermission('MODULE','ACTION')` guard.
   ⚠ **This line said "≤ 62" for months** — three times the enforced ceiling, so it
   invited waving through 41 unguarded endpoints the build in fact rejects. No ratchet
   in the suite has ever been 62 (F2-69). The other enforced ceilings, for reference:
   `BASELINE_ENTITY_BOUND_WRITES` 214, `BASELINE_PURE_ROLE_SITES` 404 — read the
   constant, never a prose summary of it. ⚠ **`BASELINE_GHOST_ROLE_NAMES` is now 0** (F2-177) —
   another ceiling that became an invariant, and the same rule applies: it may not be raised.
   ⚠ **`BARE_EXPORT_CEILING` 32 is GONE** — F2-61(c)
   swept it to zero and replaced it with an invariant
   (`ExportAuthorizationRatchetTest.everyExportHandlerAlsoDemandsARead`): every
   file-returning handler guarded by `MODULE:EXPORT` must AND a `READ` permission, with
   two named exceptions in `EXPORT_WIDER_THAN_READ`. **A ceiling and an invariant are not
   the same test** — a ceiling tolerates the population it measures, so once a sweep lands,
   leaving the ceiling in place makes a tolerated guard indistinguishable from a reviewed one.
4. **Live-probe every slice** against the running stack (Python urllib + pyodbc),
   including guard 403s and SoD 409s; probe through the Vite proxy (:5173) when
   verifying what the browser does; use `admin` + `so_officer` for 4-eyes paths.
5. Test rows are tagged **`[REVIEW-TEST]`** and cleaned with **`CHARINDEX`**
   deletes (never `LIKE '[...]%'` — brackets are a SQL character class).
6. **Destructive DB operations need the user's explicit OK.**
7. Bilingual rule: user-visible records carry `*_en` **and** `*_ar`; Arabic names
   are backend-mandatory — client-gate them in forms.

## Architecture (condensed — details in PROJECT_CHRONICLE §5–§8)

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5.x, **Java 17 (pinned — Lombok breaks on newer)**, Spring Security 6 |
| Auth | JJWT — Bearer access token + HttpOnly refresh cookie; FE auto-refreshes on 401 |
| DB | SQL Server 2019+, UTF-8 collation, 50+ schemas, Flyway V1–V391 (OFF in dev) |
| ORM | Hibernate 6 / Spring Data JPA, `ddl-auto=none` |
| Exports | Apache POI (XLSX/DOCX/PPTX) + OpenPDF (RTL Arabic, bundled Amiri font) |
| Frontend | React 18 + Vite + TypeScript (`tss-grc-ui-v2`), Recharts, STOMP/SockJS |

Backend modules under `module/` — **59 packages** (58 with code; `datagov` is an empty
placeholder), 205 kLoC, 432 entities, **2,215 endpoints** (measured 2026-08-09; only the
**59** is enforced — the rest drift on almost every commit, so re-run
`baseline_census.py --check` rather than trusting them). Each follows
`controller / dto / entity / repository / service`.

⚠ **Do not edit that count by hand — it is enforced.** `module-registry.yaml`
(`tss-grc-backend/src/main/resources/`) enumerates all 59 exactly once, and
`ModuleRegistryContractTest` fails the build if the registry, the directory listing,
`SkuRegistry`'s ADR-018 homing or *this sentence* disagree. Adding a package means an entry
there, a homing in `SkuRegistry`, and a workspace decision — or the build refuses it.

**The wire between the two codebases is gated** (F2-170, `FrontendEndpointContractTest`):
every endpoint an FE client calls must have a controller — a **hard zero, no allow-list**,
measured 0/1,782. It exists because F2-158's `RoleController#list` answered **500 to every
caller since it was written** and a mock fallback made that invisible; F2-169 removed those
fallbacks, so a wrong path now surfaces. Two decrease-only ceilings ride with it:
**417 endpoints have no FE client, 206 of them guarded writes** (was 421/208 until F2-172 wired
the intake substrate) — 2.2× F2-165's 189 dark
*methods*, because one method serves several endpoints and a clientless controller has no
method to be dark. ⚠ Its backend half is **reflection, never a source regex**: as a regex it
reported 1,763 of 1,809 endpoints missing, then 48 of `/api/v1/cyber` alone, because
`CyberController`'s javadoc says *"god-class was split"* and `\bclass\s+\w+` matched the prose.
A live probe answered 200 for all 48.

- **Largest:** `bcm` 25k · `sust` 16.7k (+`finance`/`ghg`/`capa`/`vendor`) ·
  `platform` 10k · `risk` 9k · `ndi` 7.7k · `integrations` 7.6k · `cyber` 7.3k ·
  `socops` 6.8k · `privacy` · `compliance` · `tprm` · `workflow` · `reporting` ·
  `ethics` · `admin` · `iam` (+`iam/governance`) · `scm` · `ai` · `evidence` · `audit_lc`
- **Saudi / financial-sector:** `sharia`, `openbanking`, `outsourcing`, `rrp`, `cpty`,
  `regreporting`, `reginspect`, `aml`, `fraud`, `consumer`, `opres`, `nca_ecc`,
  `dataclass`, `data_officer`, `regchange`
- **Kernel-ish:** `auth`, `attachment`, `signature`, `approvals`, `capa`, `intake`,
  `dashboard`, `knowledge`, `controlmap`, `workflow_submission`
- **Other:** `change_mgmt`, `change_adapters` (the 59th — S5a's ports/adapters bridging
  Change to `admin.committee` and `incident`; no entities), `policy`, `incident`,
  `governance`, `ccm`, `audit_committee`, `audit_eng`, `board_pack`, and the `*_lc`
  lifecycle packages (`risk_lc`, `cyber_lc`, `bcm_lc`, `privacy_lc`)

⚠ **This file previously listed 24 modules** — a 40% understatement that silently scoped
34 packages out of the RBAC catalogue, the test bar and every remediation plan. Full census
with per-module LoC/tests/coupling: `TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md`
(regenerate with `Appendix/scripts/coupling.py`).

**Key patterns:**
- **Multi-tenancy** — `TenantContext` thread-local from JWT; **every repository
  query filters `organisation_id`** (developer responsibility); prod adds a
  tenant write-guard interceptor. Schedulers/async must be org-explicit.
- **Authorization** — `@PreAuthorize("hasPermission('MODULE','ACTION')")` via
  `ModulePermissionEvaluator`; **140-permission catalogue across 37 modules** (measured 2026-08-07 in dev: V362 added 17 across 6,
  V369 +1, V370 +4, V371 +3. ⚠ The prose said **131** and the migrations account for only 132 of the 133
  that preceded V370, so that figure was already 1 low when written — count the table, not this line)
  (READ/WRITE/APPROVE/DELETE/TRIAGE/EXPORT), measured 2026-08-01 in both dev and
  prod — this line said "94 / 24" for months; **PLATFORM_ADMIN bypasses in code**,
  so `admin` proves nothing about authorization: probe with a real persona.
  ⚠ **`hasAnyRole(...)` is a second, independent gate that gets NO admin bypass**
  and validates nothing — **404 endpoints still authorise by role name** (F2-05/F2-52).
  Measured 2026-08-09: **38 distinct names recited, 0 absent from `iam.roles`** (69 active
  after V373) — F2-177 closed the ghost half, so `BASELINE_GHOST_ROLE_NAMES` is a **hard zero**
  and no longer a ratchet. ⚠ **Do not read that as "role guards are fine now."** 404 endpoints
  still cannot be re-granted without a jar, and the miss rate reaching 0 says only that every
  name *can* match — not that anybody holds it. Several still resolve to roles with **0 active
  users**, which fails identically and is what F2-161 found (`BCM_MANAGER`) and F2-52 slice 5
  found (`AML_OFFICER`/`KYC_OFFICER`/`ONBOARDING_AGENT`). Before trusting one, check the name
  exists **and** that somebody holds it.
  ⚠⚠ **There are TWO role-name surfaces and only one used to be measured.**
  `attachment/AttachmentAccessPolicy` mirrors each parent record's read guard so an attachment
  is as confidential as its parent — where the parent authorises by role, it holds a **Java role
  list no `@PreAuthorize` scan sees**. F2-52 found two entries still listing roles after their
  parents had been converted to `hasPermission`: 13 roles held `TPRM:READ` and were unnamed, so
  four dev personas could open a vendor BCP campaign and 403 on its files. The class's own
  comment already said *"It has to move WITH the parent, not later"* — an instruction with no
  enforcement, ignored by two converting slices. **`RoleNameSurfaceRatchetTest` enforces it now:
  change a guard's roles and change that registry in the same commit.** F2-177 found the **third**
  instance (`SANCTIONS_CHECK`, drifted since the AML conversion) and it cost **nothing** — the 7
  roles holding `AML:READ` were exactly the 7 real names listed. 🔑 **Close a harmless drift
  anyway: what made it harmless was two independent lists agreeing, and nobody was maintaining
  that.** Surface ghosts are now **2, both extractor artefacts** (`TPRM`/`BCM` are the first
  argument of `permOrRoles`), so neither surface holds a real ghost name.
  ⚠ **There is a THIRD authorisation shape now: entity ownership** (F2-159/D-14).
  `@PreAuthorize("hasAnyRole(...) or hasPermission(#externalId,'ML_MODEL','OWNER')")` resolves the
  record's owner via `EntityOwnershipRegistry` + an `EntityOwnershipResolver` per type. Use it where a
  column already names the owner and a role would be **looser** than the column
  (`ml_model.model_owner_user_id` 25/25, `bcm.exercise_run.facilitator_user_id` 4/4). ⚠⚠ **Until
  2026-08-07 this hook was a DECOY**: `PermissionEvaluator`'s 4-arg overload silently **discarded the
  id** and answered the module-level question, so `hasPermission(#id,'RISK','READ')` meant "may I read
  risks?" — **and a test asserted that as correct** (`the_targetId_overload_delegates_to_the_module_
  level_check`). Four register rows recorded this mechanism as "does not exist" without opening the
  file. 🔑 **A test can lock in a defect; it is hardest to spot when it describes the code accurately.**
  Registered types today: **`ML_MODEL`** (F2-159) and **`EXERCISE_RUN`** (F2-161) — the second cost
  one class, which is what the registry is for.
  ⚠ Adding a resolver? **Unit-test the resolver itself**, not just the registry — mutation proved a
  fake resolver hides a missing tenant filter and an ownership check reduced to mere existence.
  ⚠⚠ **Check whether the route actually names the record before binding, and convert PER LINE.** The
  identical guard string sat on `GET /{id}` and on `GET /` + `by-exercise/{exerciseId}` in the same
  controller; a textual replace puts `#id` into a method that has no such parameter, SpEL then throws,
  and the endpoint answers **500, not 403**. Also mind the parameter NAME: it is `#id` on
  `ExerciseRuntimeController` and `#runId` on `ExerciseObserverController`, so a copied constant fails
  silently. Both traps are pinned by `ExerciseRunOwnershipGuardTest`.
  ⚠ And do **not** bind an id just because the route has one: `transition`/`decommission`/audit-record/
  drift-verdict are judgements *about* the record and the owner is their subject (V367's defect).
  `MlModelOwnershipGuardTest` pins that boundary in both directions.
  ⚠ **And a FOURTH shape: a committee seat** (F2-177) —
  `hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')`, a **second relation** on the same 4-arg
  overload, dispatched on `targetType` and answered by `CommitteeSeatAuthority` from
  `admin.committee_member_terms`. Use it where the guard names a **body**, not a job family: the
  three names it retired (`AUDIT_COMMITTEE_CHAIR`/`_MEMBER`, `AUDIT_DIRECTOR`) survived six F2-52
  slices because seating them as roles is looser than the roster, dropping them discards the
  intent, and a module permission admits every reader. `targetId` is the committee **type** (not an
  id — a guard ships in a jar and every tenant's audit committee has its own PK).
  ⚠ **Its own ghost trap is one letter away**: `'AUDIT_COMMITTE'` fails exactly like a ghost role
  name. `CommitteeSeatGuardTest` pins the 17-value `committee_type` vocabulary at a **hard zero**
  and pins `SEAT` as the only relation (there is deliberately no `CHAIR` — add relations in the
  commit that first guards on one; an unconsulted relation is how F2-159's decoy formed).
  ⚠⚠ **The mechanism works and the DATA does not exist yet** (F2-178): of 43 dev seats **13** name
  a user, 1 is declared external, **29 neither** — `AUDIT_COMMITTEE` is **0/5**, and **prod is 0 of
  31**. So the leg answers `false` for everyone today; what changed is that a tenant admin can fix
  it from the roster screen instead of needing a jar. Say this when reporting the guard as working.
  ⚙ Three prerequisites had already shipped for other reasons (F2-26 linkage, F2-28 roster
  denominator, S6c `CommitteeSeatOffice` for the free-text `role`) — **re-measure a recorded
  blocker before believing its estimate.** ⚠⚠ And F2-178's own *recommendation* then broke that
  rule the same day: it asked for a per-committee linkage report **that F2-26 had already shipped**
  (`GET /committees/registry/committees/{id}/linkage`, rendered on the registry screen). 🔑 **A
  recommendation is the least-verified claim on the register — nothing executes it.** What was
  actually missing was the *tenant-wide* view (`GET …/registry/linkage-coverage`, F2-178), because a
  report keyed by committee id cannot show a committee with **no roster at all** — 9 of 18 live dev
  committees and 11 of 17 in prod.
  ⚠⚠ **`renewal_status = 'ACTIVE'` is NOT "this seat may authorise".** The guard also needs
  `ended_at IS NULL` and today inside the term window, and **nothing in the platform ever moves an
  ACTIVE term to EXPIRED** (`CommitteeScheduledSweep` only sends 90/60/30-day reminders; `endTerm`
  is manual). So a roster can read **100% covered and admit nobody** — 0 rows today, and the
  earliest prod term ends **2027-03-01**, so it arrives by calendar arithmetic. Both queries are
  built from one constant, `CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW`;
  `SeatWindowPredicateSharingTest` fails the build if that clause is written **twice** in the file.
  ⚠ Two traps in that constant: it must **lead with a newline** (Java strips *trailing* whitespace
  in text blocks → `WHENt.endedAt` → a malformed `@Query` kills the app **at context startup**, not
  one endpoint), and `SUM` over an empty group is **NULL, not 0** in SQL Server.
  ⚠ **A catalogued permission is inert until a guard consults it**, and a catalogue
  *derived* from guards is not *faithful* to them: V362 classified actions by method
  shape and so promoted whole WRITE tiers to APPROVE in 4 of the 6 modules it touched
  (V367 sharia, V368 openbanking; cpty + aml still filed). Converting a guard is the
  only thing that makes either artefact observable — **measure the per-action
  population diff against `iam.role_permissions` before you convert, not after**.
- **Soft deletes** — `is_deleted` + `@Where`; native SQL must filter it.
  Exception: `iam.user_roles` rows are hard-deleted.
- **Four-eyes/SoD gates are server-enforced 409s** (allocation approve, tag decide,
  request decide, campaign self-certify, break-glass self-review, GHG verify needs
  evidence…). A 409 with an SoD message is the feature working — test with two users.
- **Workflow engine** (`workflow.requests`) + **evidence subsystem** (SHA-256,
  four-eyes) + **auto-CAPA** (`sust.corrective_action`; every new `source_type`
  must be added to the `ck_ca_source` CHECK or inserts 500).
- **Errors** — RFC-7807 ProblemDetail; FE `problemMessage()` folds the
  bean-validation `errors` map into toasts, so failures name the field.

## Configuration profiles

- `dev` — Swagger on, Flyway off, port 8084, permissive CORS (localhost:3000/5173/
  5500/8086/8087 + LAN), lockout effectively off, 1 h tokens.
- `prod` — Swagger off, Flyway on (baseline-aware), hardened; **all runtime config
  in `external.properties` next to the jar** (template:
  `tss-grc-backend/external.properties.template`; reference: PROJECT_CHRONICLE §11).
  Deployed server: `D:\Deployment\tss-grc` — WinSW services, backend :8090 /
  management :8091, nginx :80 serves the SPA + proxies `/api` and `/ws`.

## Top landmines (full list of 28: PROJECT_CHRONICLE §16)

- `JAVA_HOME` → JDK 17, always (fresh shells default to 11).
- ROWVERSION double-flush: never save an entity twice around a native
  `@Modifying` on the same table — native first, mutate + save once.
  ⚠⚠ **A native `@Modifying` is only the loudest trigger. ANY flush between the two saves does it** —
  measured 2026-08-10 (F2-175). `MlModelDriftService.record` did `save → file the issue → mutate →
  save again` and worked for as long as it existed, because the issue was inserted in the *same*
  persistence context and Hibernate batched the INSERT and UPDATE together. Routing that insert
  through a `@Transactional` port put a real boundary between them, the intermediate flush happened,
  and the second save's `UPDATE … WHERE row_version = <what this session last saw>` matched **zero
  rows** → `StaleObjectStateException` → **HTTP 500 on a governance escalation**.
  🔑 **A latent double-save is not a style issue — it is a defect waiting for someone to add a
  transactional call between the two writes**, and that someone will be reading neither. Do the other
  work FIRST, then save once: nothing in the issue payload needed the observation's id, so the second
  save was never necessary.
  ⚠ **No unit test can see this.** Mocked repositories have no `row_version`, so the suite was green
  over it and only the live probe against real SQL Server disagreed — house rule 4 earning its place.
  Pin it with `verify(repo, times(1)).save(...)` plus an `InOrder` check that the other work precedes
  the save.
- Engine-wiring TX rules: `afterCommit` + per-call `REQUIRES_NEW`
  `TransactionTemplate` + native stamp updates (joined-TX failures poison commits).
- `@Valid` fires **before** `@PreAuthorize` — permission probes need a valid body.
- Off-request audit: guard `RequestContextHolder.getRequestAttributes()`;
  `TenantContext` isn't set off-request.
- `[REVIEW-TEST]` in `LIKE` is a character class — `CHARINDEX` for literals.
- Reboot port-steal: keep default MSSQLSERVER **Manual**; Express owns 1433.
- **A full C: drive fails as a test failure, not as a disk error** (2026-08-06). With C: near
  zero, `mvn -o clean package` reported **4 errors in `EntityValidationHandlerTest`** —
  which passes **5/5** when run alone. Mockito/byte-buddy needs a temp dir and C: holds it,
  so the errors land in whatever class the runner reached, never in the class at fault.
  Same signature as the mass "Could not initialize MockMaker" seen with two JVMs competing.
  ⚠ **Do not debug the named class.** Check `shutil.disk_usage('C:/')` first, then re-run the
  class alone: if it passes, the full-run result is noise. Also blocked at that point:
  `CREATE DATABASE` (OS error 112 — SQL Express's data dir is on C:), the session scratchpad
  (`ENOSPC` on write), and `npx vitest run` (V8 crash part-way). The repo on D: is unaffected,
  so Read/Edit/git and single-class `mvn test` keep working — which is what makes the failures
  look like code.
  ⚠⚠ **And the disk FIX broke `CREATE DATABASE` a second way, permanently.** The ~22 GB was freed
  by NTFS-compressing C:, so the SQL data directory carries the `Compressed` attribute, new files
  inherit it, and SQL Server refuses with *error 5118 — the file is compressed but does not reside
  in a read-only database or filegroup*. Existing databases are unaffected, so only NEW scratch
  databases fail — which is every chain/schema proof. **Never let a scratch database pick its own
  path**: give it explicit files on D: (`CREATE DATABASE [x] ON (NAME=..., FILENAME='D:/.../x.mdf',
  SIZE=64MB) LOG ON (...)`). `full_chain_proof.py` and `chain_proof.py` both do now
  (`CHAIN_PROOF_DATA_DIR` overrides). Do **not** `compact /u` the SQL data dir — that edits the
  user's SQL Server installation to work around a script's own default.
- Nested Spring Data repositories aren't scanned; lazy-collection entities 500
  outside the session — return DTOs.
  ⚠ **This exact landmine was live in the tree until 2026-08-07** (F2-158): `RoleController#list`
  returned `List<Role>` and had answered **500 for every caller since it was written** — the whole
  of the Role Management API. `LazyEntitySerialisationInvariantTest` now refuses any handler
  returning an entity that owns a `@OneToMany`/`@ManyToMany`/`LAZY` to-one (255 controllers,
  2,210 handlers, 8 of 407 entities at risk). 🔑 **The reason nobody had noticed is the reusable
  part: the frontend's mock fallback was load-bearing.** `AdminPage.tsx` wired mock roles into all
  three failure paths, so the screen always rendered plausible rows and the 500 was invisible.
  **When a page has a client-side fallback, hit its endpoint directly before believing the page** —
  third occurrence after F2-42 and F2-38. And do **not** fix such a refusal with `EAGER`: that
  trades a 500 for an unbounded join on every read.
- **The database is CASE-INSENSITIVE and Java is not, so a CHECK constraint is no guard for a
  `.equals()`** (F2-179, measured 2026-08-10). Columns are `Latin1_General_100_CI_AS_SC_UTF8`
  (DB `SQL_Latin1_General_CP1_CI_AS`), so `CK_ml_drift_sev` — `severity IN ('CRITICAL','MAJOR',…)` —
  **accepts `'critical'`**, stores it verbatim, and `if ("CRITICAL".equals(req.severity()))` is then
  false. Probed live: a lowercase CRITICAL drift returns **201**, files no issue, and leaves the model
  `IN_PRODUCTION` — no error anywhere. Any Java literal compared against a CHECK-constrained token has
  this shape; `ExerciseObserverService` holds the same five-token `Set.of(...)` against the same
  collation. **Normalise on write** (so the CHECK, the comparison and the FE label map agree) rather
  than reaching for `equalsIgnoreCase`, which leaves the odd casing in the column and misses the
  frontend's label lookup.
  ⚠ **`ExerciseObserverService` is already CLOSED and that sentence was wrong for a day** (F2-179):
  `normaliseRole`/`normaliseType`/`normaliseSeverity` all `trim().toUpperCase(Locale.ROOT)` *before*
  testing the `Set`. It is the pattern to copy. 🔑 **Read the code before believing a doc's claim
  about it — including one you wrote.** The real second instance was somewhere nobody had named.
  ⚠⚠ **Use `common/validation/ClosedVocabulary` for this — do not hand-roll another one.** Measured
  2026-08-11: **347** closed upper-case CHECK vocabularies (389 single-column CHECKs), every text
  column case-insensitive, **79 with an open writer** (a caller's casing reaches the column) and
  **25** of those also read back by a case-sensitive Java comparison. **0** non-canonical values are
  stored today, so it is latent — reachable only through the API, which is where machine callers live.
  The primitive is `require` / `orDefault` / `coerce` / `is`, and a vocabulary **names the CHECK it
  mirrors** so `ClosedVocabularyPinTest` checks all of them against the migration SQL by reflection,
  with no registry to forget. ⚙ It is not new code — `module/cyber`'s `CyberInputValidation#checkEnum`
  got this right at CY-H and was used **6 times, all inside `module/cyber`**, which ADR-012/018 forbid
  a peer importing; that is the whole reason every other module hand-rolled something weaker.
  ⚠ **`require` vs `coerce` is a per-field product decision, not a house style.** Refusing an
  unrecognised token with a 400 is right for a governance escalation and **wrong** for the public
  whistleblower portal, where `safeSeverity` already accepted it as `MEDIUM` and *the report arrived* —
  a lost report is worse than a mis-graded one, and `CyberInputValidation`'s own javadoc draws that
  line ("no request that succeeds today can start failing").
  ⚠⚠ **The worst instance of this family found so far was on the PUBLIC portal** (F2-179):
  `WhistleblowerCaseService.isAnonymousReporterType` asked `"ANONYMOUS".equals(<the raw request
  value>)`, so `reporterType: "anonymous"` was stored `is_anonymous = FALSE` and the next branch
  **persisted the reporter's name, email, phone and employee id.** Its sibling `safeSeverity` returned
  `MEDIUM` for `'critical'`. 🔑 **`safeChannel`/`safeReporterType` returned their argument unchanged —
  the `safeRole(String role) { return role; }` decoy F2-175 deleted, in a different module. The name
  is the tell, not the module: grep `safe*`.** (It found a third, `ComplaintService`, filed as F2-183.)
  ⚠ And the reason the green suite missed the identity bug: the existing test's fixture set **both**
  `reporterType = "ANONYMOUS"` *and* `isAnonymous = true`. 🔑 **A fixture that satisfies a condition
  twice cannot tell you which half is load-bearing** — supply one at a time.
  ⚠⚠ **And the mirror of that landmine: a CHECK constraint the Java side never mentions is still enforced.**
  F2-180 found **19 of the 22** incident categories the platform's own screen offers being refused by
  `CK_incidents_category`, and three promotion adapters writing status/category words their columns had
  never accepted. **Before writing a vocabulary literal, read the column's CHECK** — from
  `sys.check_constraints` or the migration, scoped to *that* table. ⚠ **A cross-check not scoped to the
  target table is worse than none**: the first sweep compared literals against every CHECK in the database
  and cleared the one adapter already known to be broken, because its value is legal on some other table.
  ⚠⚠ **`require` vs `coerce` has a mechanical answer, and F2-182 found it: ask whether a coercion already
  exists upstream.** Where one does — the whistleblower portal's `safeSeverity` returned `MEDIUM` and *the
  report arrived* — out-of-vocabulary values succeed today and `coerce` is mandatory. Where none does, the
  **CHECK is already refusing** everything outside the vocabulary, so **every value that succeeds today is a
  case-variant of a legal token** and `require` cannot break a working request; it only turns a CHECK
  violation into a 400 that names the field, and normalises the casing that slipped through. That retires the
  per-field argument for this whole family. Use `orDefault` where the "absent" token is a real member
  (`chk_calendar_recurrence`'s `NONE`), not a null stand-in.
  ⚠⚠ **A CHECK constraint and its writer must land in the SAME commit, and the migration's scope is
  the WRITERS you close — not the columns you can see** (F2-183, measured 2026-08-11). Adding a CHECK to a
  column whose writer is still open converts today's silently-stored junk into a **constraint violation**;
  on `consumer.complaint` that is a lost complaint on the anonymous SAMA-mandated intake channel, strictly
  worse than the defect being fixed. The whole `consumer` schema had **zero** CHECKs — not a subset like
  F2-153's 32, all of them — so V375 constrains exactly the nine columns whose writers the same commit
  closed, and leaves `subcategory`/`root_cause_code` open **because no vocabulary is declared for them
  anywhere** (a test asserts that as a decision rather than leaving it an absence).
  ⚠ **Measure PROD, not just dev, before shipping any `ADD CONSTRAINT`**: it verifies the rows already
  present, so one unrecognised value fails Flyway and blocks the whole next jar. Both held 12/28/1 rows
  with 0 violations here, so no `WITH NOCHECK` — which V364's self-checks correctly refuse.
  ⚠⚠ **And the constraint cannot replace the normalising writer, because the collation is case-INSENSITIVE
  — probed live: `severity = 'critical'` is ACCEPTED and stored verbatim** under `CK_complaint_severity`.
  A migration that adds the CHECK and stops leaves the F2-179 defect intact *while appearing to guard it*,
  which is worse than not having it. V375 therefore normalises casing first — and because that UPDATE
  touches **0 rows** in both real databases, the verification script **seeds a deliberately lower-case
  `[REVIEW-TEST]` row so the branch is exercised rather than assumed**. 🔑 **A migration branch that is a
  no-op everywhere you can measure is untested code unless you build it something to do.**
  ⚙ Two more from it: `ClosedVocabulary` grew **`coerceOrNull`** for nullable closed columns, where no
  member means "not stated" so `coerce` has no legal fallback and `require` would 400 the record away; and
  a **`ROWVERSION` column refuses an explicit INSERT value**, so a column list built from `sys.columns`
  must exclude by TYPE (`timestamp`), never by name.
  ⚠⚠ **Do NOT decide "is this writer closed?" from a scan of setter call sites** — F2-182's filing did, and
  **6 of the 10 sites it reported as open were already closed**, including both it called control bypasses. A
  guard sits in at least four places a setter-site regex cannot see: a conjunction in the **same `if`**
  (`NdiComplianceAssessmentService.update`), a **shared `validate()`** called by every mutation path
  (`KriDefinitionService`, `CmaDisclosureService`, `TcfdItemService`), an **`if` block** wrapping the setter
  (`BcmRiskService`), or a **DTO annotation** (`SgiCreateRequest`). 🔑 **Reachability is a dataflow question;
  a regex can only tell you where to go and read.** What *is* reliably measurable is whether the Java
  vocabulary agrees with the column's CHECK — `HandRolledVocabularyMirrorTest` does that for the six mirrors
  still hand-rolled, and `ClosedVocabularyPinTest` for every `ClosedVocabulary`.
  ⚠ **And close a refuted site anyway when it gates a control**: `ndi.compliance_assessment.compliance_status`
  and `risk.kri_definition.value_direction` had no reachable defect, but both *read* the column
  case-sensitively, so a row from a data fix or a restore would have walked through an NCA/NDI approval gate
  or inverted a KRI's bands. **Reachability is a fact about today, not a property of a control.** The KRI
  shape is the sharper one: a two-token vocabulary means a mismatch does not degrade to a default, it selects
  the **opposite** branch — probed live, an LCR of 95 against a 130/120 ladder reads GREEN inverted.
  ⚠⚠ **A bean-validation annotation on a class no endpoint binds enforces nothing** (F2-182 → F2-184).
  `CalendarEventDto` carried `@Pattern(NONE|DAILY|…)` on `recurrence`; that type is the **outbound** payload
  and both endpoints bind the nested `CreateRequest`, which had none — so a lower-case `monthly` was stored
  verbatim and `IcsBuilder.rruleFor`'s `default -> null` emitted **no RRULE at all**, exporting a quarterly DR
  test as a single event. 🔑 **A constraint on a response DTO is worse than none, because it answers the
  question a reader was about to ask.** Same family as the `safeRole` decoy, but the tell is **structural**:
  the class is never a `@RequestBody`. ⚠ A source-regex sweep for it reported **75** types and the first one
  checked was a false positive (nested records attributed to their container class); done properly by
  **reflection** the answer is **20**, now **12** and gated — `InertValidationConstraintTest` walks 2,941 types
  (254 controllers, 2,210 handlers) and holds the survivors as a **named map with a reason each, enforced in
  both directions**, so a fixed one must have its entry deleted. Six of its 16 mutations attack the instrument,
  including the phantom-75 bug itself.
  ⚠⚠ **The load-bearing half is `@Valid` on a nested collection, and its absence is invisible at the field.**
  `EntryBulkImportRequest.entries` was `@NotNull` without `@Valid`, and `EntryCreateRequest` is bound **only**
  there — no single-entry endpoint — so on **the only route that creates a sanctions entry** none of
  `@NotBlank primaryNameEn`, `@NotBlank entryExternalId` or the `@Pattern`s on `entityType`/`severity` ran.
  `aml.sanctions_list_entry` has **zero CHECKs**, so those patterns were the only narrowing, and a blank-named
  entry is stored and **never matches a screening query**. It also fixed a second defect for free: the duplicate
  check is `existsBy…EntryExternalId(null)` → `= NULL` → never true, so null-id entries were **created, not
  skipped, on every re-import**. 🔑 **A defect reachable only through input the boundary should have refused is
  fixed at the boundary** — a null check in the service would have left the unnamed row.
  ⚠⚠ **Do NOT just add `@Valid` — a dormant `@Size` number has never been tested against its column.** Cap
  **==** column length → enable (the DB already refuses more, so this only converts a truncation 500 into a 400
  naming the field, and **cannot break a working request**). Column **NVARCHAR(MAX)** → the schema deliberately
  leaves the field unbounded, so **delete the cap**: `AttendanceMark.coiNote`'s `@Size(1000)` was deleted rather
  than enabled because capping a conflict-of-interest declaration is a product change, not a fix. Cap **looser**
  than the column → enabling protects nothing; the annotation is the thing that is wrong. Check stored values
  before enabling a `@Pattern` over a CHECK-less column — F2-184's five all held in-vocabulary data, which is
  what made it latent-defect closure rather than a behaviour change. Third variant of one question: F2-179 asked
  it per field, F2-182 answered *does a coercion exist upstream*, F2-184 *does anything already refuse this*.
  ⚠⚠ **That rule is right and it is not sufficient — `cap == column` can be TRUE while the write still fails**
  (F2-185b, measured 2026-08-11). `ApproveAccessRequest.overrideJustification` is `@Size(max = 1000)` and
  `iam.sod_violations.disposition_note` is `NVARCHAR(1000)`, so the mirror was satisfied — but
  `SodService#recordOverride` prepended `"Override justification: "`, **24 characters**, making the effective
  budget 976. A justification the boundary accepts overflowed by exactly the prefix → SQL Server **8152** → **500
  on an SoD override**, and since the violation is written in the same transaction as the grant it authorises, the
  whole override rolls back. 🔑 **A length check that stops at the boundary misses everything the code adds after
  it** — the prefix is not an annotation, not a vocabulary and not a column, so no scan in this family can see it.
  Fix by dropping the decoration (the row already said `detection_source='OVERRIDE'`), not by lowering the cap to a
  number that leaks a storage detail into the API. `SodOverrideNoteTest` asserts the **arithmetic**: the longest
  value the boundary accepts must fit the column *after* the service has touched it.
  ⚠ **And where the field has no column at all, the rule has nothing to compare.** Both `note` fields F2-185b
  enabled land only in the `NVARCHAR(MAX)` audit message, so the MAX branch argues for deleting them — wrongly:
  the *identical* field on `ShadowItSanctionRequest` is already `@Valid` at 500 on the adjacent `/sanction` route
  of the same controller. 🔑 **Before asking what column a cap guards, ask whether a sibling route already
  enforces the same field.** `RequestSizeCapMirrorTest` holds the nine mirrored caps against their
  `@Column(length)` and requires every other cap on those types to be *explained by name*.
  ⚠⚠ **`jakarta.validation.constraints.Size` has no `RECORD_COMPONENT` target**, so javac propagates it to the
  backing field and the accessor and `RecordComponent#getAnnotation(Size.class)` **always answers null**. A
  reflection sweep that asks only the component reports every cap on every record DTO as absent — and "absent"
  reads identically to "nothing to check here."
  ⚠⚠ **`InertValidationConstraintTest` is per-TYPE and that was blind to a per-SITE defect** (F2-185b): it
  subtracts everything the cascade validates, so a DTO that is `@Valid` on one route drops out of the population
  on **every** route — removing `@Valid` from one of the three routes binding `AttestationReviewRequest` left the
  suite green. 🔑 **A per-type gate over a per-site defect reports the population it can see, and one
  unvalidated site out of three looks exactly like none.** A second assertion,
  `aConstrainedRequestTypeIsValidatedAtEverySiteThatBindsIt`, now walks 254 controllers and **186** bare-bound
  body parameters and demands a hard zero (`PARTIALLY_VALIDATED` empty, enforced both ways). It is also the only
  guard that sees a deleted MAX cap being re-added *without* `@Valid`.
  ⚠ **Re-check a filing's measurements before building on them**: of the ten F2-184 recorded for the next slice,
  **three were wrong** — two caps were 8000 not 1000, one predicted looser-than-column case was an exact match,
  and two columns called "unconfirmed" exist under names a field-name search missed (`approverName` →
  `approved_by_name`). 🔑 **The measurements in a filing are the least-verified thing in it**, and all three were
  wrong in the direction of looking already-solved.
  ⚠ **A constraint on a `@Entity` field is NOT inert** — `spring-boot-starter-validation` + JPA validation mode
  `AUTO` means Hibernate's `BeanValidationEventListener` enforces it on pre-insert/pre-update. It fires as a
  `ConstraintViolationException` → **500** rather than a 400, so it is a weaker defect worth knowing about;
  counting entities as inert would have overstated F2-184 by six sites.
  ⚠⚠ **And the finding indicted the slice that filed it**: `CalendarEventDto` still carried four more inert
  constraints (`source`, `titleEn`, `eventType`, `startsAt`) directly above the comment F2-182 wrote explaining
  why it deleted the fifth. 🔑 **Fixing the instance you noticed is not closing the shape, and a class you have
  already edited reads as one you have already checked.**
  ⚙ An inert constraint on a `*Request` type is also a **dark-code detector**: two of the twelve survivors are
  types nothing reaches, one of them because `WhistleblowerRetaliationService#transitionTo` has no caller — the
  retaliation terminal transition has **no endpoint** (F2-185). Do not close those by deleting the annotation;
  that removes the fingerprint and leaves the defect.
- ⚠⚠ **A reachability gate certifies the granularity it resolves, and this platform had two that between them
  could not see a backend method** (F2-187, measured 2026-08-11). `apiClientMethodReachability.test.ts` resolves
  **TypeScript API-client methods**; `FrontendEndpointContractTest` resolves **endpoints** — and a service method
  with no controller *has no endpoint*, so it sat in neither population. **38 public methods on shipped `@Service`
  classes have no `invoke*` site anywhere in `target/classes`.** `DarkServiceMethodTest` now holds them as a **named
  map with a reason each, enforced in both directions** (a new one fails the build; one that gains a caller must
  lose its entry in the same commit) — and it rejects a reason that is a bare label, which fired on six of my own
  first-draft entries. 🔑 **Before trusting a green reachability gate, say what unit it resolves out loud and ask
  what sits between that unit and the code that runs.**
  ⚠ **Measure this from BYTECODE, never a source regex** — `org.springframework.asm.ClassReader` is already on the
  classpath. The register records four pattern mistakes in one slice from doing it with regexes, and three phantom
  counts over Java in three consecutive findings (1,763 endpoints from a javadoc sentence; 0-of-389; 75 types).
  ⚠⚠ **The raw number is roughly a third false positives and the subtraction is structural, not a name rule.**
  51 → 38: **16** are `@Scheduled`/`@EventListener` (the container calls them), **5** implement a framework
  interface. Reflection/SpEL/scheduler-by-name are invisible in bytecode *by nature*, which is exactly why the
  survivors are a named list and **not** an assertion of zero. Test classes are deliberately **not** counted as
  callers: a method whose only caller is its own unit test is reached by nothing that ships.
  ⚠⚠ **A method-NAME fallback silently clears the methods most likely to be duplicated elsewhere.** A first pass
  cleared any name invoked on any type, hiding `PolicyService#create`/`update`/`delete` — i.e. reporting four dark
  methods where the whole legacy write lifecycle (nine) was dark. Resolve per **owner type**, walking supertypes so
  interface dispatch still counts.
  ⚠ **A dark method is not automatically a missing capability — sort by EFFECT, not by verb name.** F2-185(a) was
  graded **P2** on *"a retaliation complaint cannot be closed through the API"*; probed live 20/20, it can —
  `/retaliation/{id}/validate|uphold|dismiss` are all wired and the FE renders each. `transitionTo` was a redundant
  **fourth** path, so the filed choice ("wire it or delete it") had a third answer. 🔑 **The claim came from reading
  the service and not the controller beside it: one file short is the whole error** (second instance, after F2-179's
  "`ExerciseObserverService` is open" — both mine).
  ⚠ **9 of the 38 are dark ON PURPOSE and the evidence sits by the CALLER, not the method.**
  `RegChangePolicyController` carries the comment that retires the legacy lifecycle, names
  `PolicyManagementService#transition` as the live twin and names CH-9 as the exit. A hard zero would have
  manufactured four false P2s on its first run. 🔑 **The difference between dead code and a decision is a recorded
  reason, and it is rarely recorded where you are looking.**
  ⚠⚠ **That filing had a "sharpest bucket" of five members graded *"a row can never leave this state"*, and
  F2-188 measured all five: EVERY ONE WAS FALSE.** `CcmService#recordRun` is a *third* entry point —
  `CcmSchedulerService#tick` is `@Scheduled(fixedDelay=60s)` and `POST /ccm/tests/{id}/run-now` exists, both
  reaching the private `recordRunInternal` that owns the ladder. `markOverdueForCampaign` was **superseded** by
  `sweepOverdueForOrg`, and `ComplianceDeadlineScheduler`'s javadoc names the method it replaced.
  `IrPlaybookService#recordInvocation` was superseded by `IrPlaybookRunService`, whose comment says it preserves
  the legacy semantics. `ThreatIntelService#markActioned` says *"Legacy shim"* one line above its own signature.
  **Four of the five truths sat in a comment I had not opened.**
  🔑 **A named-map gate proves a reason EXISTS and is substantial; nothing proves it is TRUE — so the reasons are
  the least-verified part of the most authoritative-looking artefact.** And the substantiality check made it
  worse: it rejected six bare labels, so I rewrote them as fluent consequence-naming prose, and **fluency reads
  as verification.** `DarkServiceMethodTest.noReasonAssertsUnreachabilityWithoutAScanThatVerifiesIt` now refuses
  an unreachability claim unless the entry declares the (entity, state) in `CLAIMS_NO_OTHER_WRITER` and a scan
  confirms no other class writes it — **empty today, which is the finding.** ⚠ Its first draft matched bare
  `is NEVER`/`nothing routes` and flagged three reasons that were already correct and already *method*-level:
  a scan not scoped to the **shape of the claim** invents findings, the fifth instance of that here.
  ⚠⚠ **And that gate then verified only the claim shape it was written for** (F2-192, measured 2026-08-11).
  `UNREACHABILITY_CLAIM` is scoped to state-transition verbs — correctly, since that was the shape F2-188 had
  just found false five times — so the map's **larger** bucket, the *capability* claims (*"no endpoint does X"*,
  *"only the list is wired"*, *"cannot be resolved by any route"*), was never scrutinised. Measured against each
  owner class's live **cross-class** surface (ASM, callers other than the class itself): **all 18 owner classes
  have one**, **7 claims were outright false**, 4 overstated, 5 stand. Every false one had a **routed sibling on
  the same class** already doing the thing the reason called impossible — and one (`ChangeModelService#usageFor`)
  had the method's own **parameter** wrong. 🔑 **A gate that verifies claims verifies the claim shape it was
  written for** — F2-187's headline one level up, at the gate built to answer it; assume a third shape exists.
  `everyReasonIsWrittenAgainstItsOwnerClassLiveSurface` now requires each reason to cite a live method of its
  owner class **in method notation** (`#name`), because half the siblings are called `list`/`get` and *"no
  endpoint **lists** unacknowledged drift"* satisfies a substring check for `list` **while asserting its
  opposite**. ⚠ **It fired on its first run against a shape I had not counted:**
  `SanctionsScreeningSchedulerService#setClock` was recorded as a *"DECLARED TEST SEAM … reached by tests only"*
  and **nothing called it** — the sibling class's identically-named setter is the tested one. It had been
  classified a seam **by its name**, three lines under F2-187's own warning that *"a `setClock`-shaped exemption
  would let any dark method be hidden by renaming it"*. 🔑 **Naming a trap does not exempt you from it, and a
  warning you wrote yourself is the one you are least likely to re-read.** ⚙ That was the real gap:
  `#runSlaSweep` flips `sla_breach` and fires `SANCTIONS_HIT_SLA_BREACHED` **across tenants** off a
  `LocalDateTime.now(clock)` cutoff and had **no test at all** — the seam was built for tests nobody wrote.
  `SanctionsSchedulerSweepTest` drives both ticks through it (cutoff arithmetic, one call per *distinct* tenant
  under that tenant's `TenantContext`, no leak, skip-and-log per F2-191, the `equalsIgnoreCase` DAILY filter,
  the NULL-org→1 fallback, the `scheduler-enabled` gate).
  ⚠⚠ **`admin` is the wrong persona for a role-DERIVED value, and it fails toward agreeing with you.** Probing
  `/three-lines/user/{id}/role-band` as `admin` returns `allBands: []` and `lod_band: []` — PLATFORM_ADMIN maps
  to no line of defence — which is indistinguishable from the false claim *"never displayed per user"*. As
  `rmanager` it returns `effectiveBand: "LINE_2"` with `lod_band: ["LINE_2"]` in the JWT. CLAUDE.md's
  "admin proves nothing about authorization" has this second edge.
  ⚠ **And a negative assertion naming something that was never in the population passes for an unrelated
  reason**: 4 of F2-192's 17 mutations survived and **all four were the mutation's fault** — one stripped 1 of 2
  citations in the same reason, one replaced 1 of 6 concatenated literals, one expected a *relaxed* check to
  fail (it cannot, on a corpus that already satisfies the strict one), and one asserted the cross-class
  narrowing against two methods it never touched (one private, one an entry point nothing calls). Pin a
  narrowing by **measurement** — 34 public `@Service` methods are reached only from inside their own class, and
  that count goes to 0 if the narrowing is deleted.
  ⚠⚠ **The third shape was the one that says nothing FIRES it, and asking about it found a live defect on a
  screen** (F2-193, measured 2026-08-12). Six `TOLERATED` reasons say *"NO PRODUCTION TRIGGER … the integration
  reads as working while no domain event reaches it"* — a claim about the **kind** of caller a class has, which
  neither earlier gate measures. F2-192's citation rule made it worse: it forced each of them to name a live
  sibling, and **a named routed sibling reads as evidence the integration is exercised.** The backend half found
  one false claim: the Splunk entry said the module *"has one real trigger"*, citing `POST /security-event/{id}`
  — whose own `@Operation` summary reads **"Manually push a known SOC security event into Splunk"**. 🔑 **A route
  is not a trigger.** `everyTriggerClaimMatchesItsOwnerClassCallerKinds` now classifies every cross-class caller
  as container-driven (`@Scheduled`/`@EventListener`) or not; census **2 of 36**, and those two are exactly the
  entries whose reasons already claim a scheduler.
  ⚠⚠ **The real defect was in the FRONTEND catalogue that says the same thing to an operator.**
  `tss-grc-ui-v2/src/lib/connectors.ts` declared 12 connectors, **8 `ACTIVE`** with a cadence, a `lastRunAt` and
  an `intake30d` count. Measured: **5 of the 8 have no backend of any kind** (SDAIA/NCA/SAMA regulator watch —
  there is no RSS client anywhere; the vendor SOC 2 expiry watcher — `tprm` has no scheduler; the CAPA
  effectiveness re-test, promising to flag `VERIFICATION_FAILED`, **a token that appears in no Java source**), a
  6th is webhook-only rather than the 5-minute poll shown, and the **2 that are real had the wrong cadence in the
  flattering direction** — 900 s shown as "Daily", `cron 0 0 6 * * *` shown as "Hourly" — **and both are off by
  configuration in every profile in the repo** (`…tenable.polling-enabled: false`; `grc.kri.scheduler-enabled`
  defaults false and is set nowhere). ⚠ **CrowdStrike, the one connector with a 60-second real poller, was not in
  the list at all** — which is what proves the catalogue was never compared with the code. The counts summed to
  **292** under *"Auto-intake (30 days)"*, linking to `/intake-inbox` where `intake.intake_records` has **0 rows in
  dev and prod**; Tenable alone claimed **247 in 30 days** against a log table holding **5 rows ever, all manual**.
  🔑 **F2-172 deleted the fabricated intake records and left the fabricated COUNT of them** — a de-mocking that
  stops at the store and not at the aggregates over it. **When you de-mock a store, grep for every KPI that
  summarised it.**
  🔑 **The fabrication lived in the TYPE, not the data**: a `lastRunAt` on a catalogue no server feeds can only be
  invented, and a `status` a human sets is a claim with no source. `status`/`lastRunAt`/`intake30d`/`owner` are
  **deleted from the interface**; each entry declares `trigger` (`SCHEDULED_POLL`/`INBOUND_WEBHOOK`/
  `OPERATOR_INITIATED`/`NOT_IMPLEMENTED`), the `backing` symbol providing it, and the `gate` property an operator
  must set — because *"a poller exists"* and *"the poller runs"* are different facts and the old catalogue asserted
  the second by never distinguishing them.
  ⚠ **It takes TWO gates and neither is sufficient.** `ConnectorBackingContractTest` (backend, hermetic — it reads
  the `.ts` file the way `FrontendEndpointContractTest` reads the API clients) resolves every `backing`: a claimed
  poll must carry `@Scheduled` **on the named method** (naming `#pollFindings`, the manual entry point, is caught),
  a claimed webhook must be a mapped route, an unimplemented connector must name **nothing**, every `gate` must be
  a property the app binds. `connectorTriggerHonesty.test.ts` holds what only the catalogue knows: the retired
  fields, `cadence` iff `SCHEDULED_POLL`, and the **planned voice in both languages** for anything unimplemented —
  the badge is not what a reader reads. A fabricated poller with a perfect shape and a nonexistent class **passes
  the frontend gate and fails the backend one**; that mutation is the pair's justification.
  ⚠⚠ **The new gate failed on its own correction, and that is a general rule for this register's conventions.**
  A correction quotes the sentence it replaces — that is what makes the corrections auditable — so a claim
  detector matching raw text reads the quotation as an assertion. 🔑 **A detector that cannot tell a quotation
  from a claim forces every correction to paraphrase away its own evidence.** `unquoted()` strips double-quoted
  spans first, pinned in both directions, and a deliberate SURVIVES mutation records that the stripping is
  load-bearing on the claim side **only** — no reason quotes a denial.
  ⚠⚠ **Asking that question of the page I had just fixed found a second façade six lines below the first**
  (F2-194, measured 2026-08-12). `ConnectorsPage` renders `LiveControlTests`, which drives the **real** CCM
  engine (`/api/v1/ccm`, persisted `control_test_definition` rows, a real runner, a 60-second scheduler tick) —
  and directly beneath it rendered `components/compliance/CcmDashboard`, a **fabricated** panel computing a
  *"% CCM health"* badge, six KPIs, a ticking `SlaCountdown` over synthesised stale-evidence breaches and a
  coverage matrix citing NCA ECC / ISO 27001 / SOC 2 / PDPL / SAMA CSF controls — all from `lib/ccm.ts`'s nine
  hard-coded rows. 🔑 **Why it outlived F2-193's connector catalogue: its timestamps are generated from
  `Date.now()` at module load, so the fabrication can never LOOK stale.** F2-193's were frozen at 2026-05-05 and
  three months out of date, which is part of what gave it away. **A clock-anchored fabrication has no tell.**
  ⚠⚠ **The de-mocking boundary is drawn by FILENAME, and that is now measured.** F2-15 counts 15 files named
  `mock*` (3,550 lines); its own row corrected the boundary once by adding 7 modules found with a **second**
  rule (modules with **numeric** ids) — and `lib/ccm.ts` keys its rows `cn-tenable.s3-encryption`, so it is in
  **neither** count. 🔑 **A boundary correction made by a second rule inherits a second blind spot.**
  `demoFallbackContract.test.ts` ratchets **`/mock/i` imports**, so a corpus in a file called `ccm.ts` or
  `assets.ts` is invisible to the one enforced instrument here **by construction**. Census by shape (an exported
  `const` array of ≥3 object literals that another product file imports): **52 files, 105 corpora, exactly 1 of
  those files named `mock*`**. ⚠ The population is genuinely mixed — `moduleAnchors`/`moduleLifecycles` are nav
  and lifecycle **configuration** and `AUTHORITIES` is the regulator catalogue W2-1a-i deliberately kept — so
  classification needs reading, per corpus; **F2-194 fixed one member and filed the rest rather than
  manufacturing ~50 unverified reasons** (F2-188's failure mode). Retiring the panel was the **ratified** answer,
  not a new product call: ADR-017 §D-5 already chose *"retire the fabricated page"* for this shape in F2-42.
  ⚠⚠ **Two assertions in my own new gate were satisfied by something that was not the claim, and only the
  mutation harness saw it.** *"The prose explaining the removal is still there"* asserted the bare word
  `CcmDashboard` and passed on the **type** `CcmDashboardSnapshot` imported from the real engine's client;
  *"the page drives the real engine"* used `toContain('getCcmDashboard')` and passed on the **import statement**,
  so a mutation replacing the call with `Promise.resolve(null)` SURVIVED. 🔑 **Third and fourth instances in one
  lineage of a substring check satisfied by something other than the claim** — F2-192's reason citing `list`,
  F2-193's reason quoting the sentence it corrected, then a type name and an import. **Assert the call shape
  (`name\\s*\\(`), and assert a retained explanation by a string that appears nowhere else.**
  ⚠ **And check the site count before believing a surviving mutation**: `getCcmDashboard()` has two call sites
  and the first mutation replaced one, which reads exactly like a hole in the gate. The harness now replaces
  every occurrence and prints how many it touched.
  ⚠⚠ **Asking that question of the record FIELDS rather than the corpora found the strongest instance yet, in
  the KPI strip** (F2-195, measured 2026-08-12). Ranking F2-194's 105 corpora by *"which fields assert live
  operational state?"* (a `status`, a metric, a clock stamp) pointed at `moduleLifecycles`, and reading the 12
  pages that render it found a sharper defect **directly above**: of **1,069 `<Kpi>` tags**, **9 on 5 module
  pages** showed a figure nothing produced. `cyberKpiCompliance` **"NCA ECC Score" `"87%"` in `success` green
  while all 29 NCA ECC controls are `NOT_IMPLEMENTED`** (real: **0%**); `incidentsKpiMttr` `"4.2h"` where
  `/incidents/stats` already returned `avgDays: 4.5` — **the wrong unit**, not just 26× low;
  `complianceKpiEvidence` `"1,283"` against 78; `complianceKpiScore` `"87%"` against 66%, **fifteen lines above
  a real `RadialGauge` fed by `frameworkApi.coverage()`**; `bcmKpiExercises` `"92%"`, which is exercise #1's own
  `score` presented as an aggregate. 🔑 **Every one erred in the flattering direction** — second instance after
  F2-193's cadences. ⚠⚠ **The `|| N` shape is the sharp one, and F2-52 had already fixed it once with the rule
  written down.** `0 || 142` is 142, so `ropa.data.length || 142` fires on **exactly** the tenant whose register
  is empty — a PDPL Article 31 tile reading 142 activities for an organisation that has recorded none — and
  `AdminPage.tsx` carries F2-52's comment three lines from the shape nobody swept: *"`roles.length || 7`
  reported the hardcoded dropdown's length when the server returned nothing … **The real count is the real
  count, including 0**"*. 🔑 **The codebase can hold the correct rule, in prose, beside the place it was not
  applied.** ⚠ **A `trend` prop is a SECOND, independent claim** — it asserts a prior period, and nothing here
  stores a KPI series, so wiring a value while keeping `trend={{dir:'up',value:'+4%'}}` leaves the half of the
  fabrication that implies history exists *and looks finished*. ⚙ **Use `Kpi`'s `value={null}` + `emptyHint`
  for a metric the platform cannot compute** — UX-008 built it (`value == null` → em-dash with the hint as
  `title` **and** `aria-label`), so nothing needs inventing; fourth recorded blocker in this programme that was
  already solved. And `isNoData` is `value == null`, not `!value`, which is why the real **0%** ECC score renders
  as a number — pinned by `Kpi.test.tsx`, because `!value` would re-hide the finding inside the fix meant to
  expose it. ⚠ **`kpiProvenance.test.ts` refuses all three shapes app-wide (survivors 1 of 1,069) and carries a
  SYNTHETIC self-test**, because the corpus assertions pass both when the app is clean and when the regexes have
  rotted; the scan was also run against the pre-fix tree as a control (10 there, 1 here). 🔑 **State out loud
  which of those two a green scan is showing you.** ⚠⚠ **Two self-inflicted errors, both caught only by
  running things:** the nullable-KPI assertion first matched `/\bnull\b/` anywhere and fired on **16 innocent**
  tiles whose `null` is inside `x == null ? '—' : …` (**fifth** instance of a check satisfied by something other
  than its claim), and I then set the follow-on ratchet to **16** — a number printed by the *broken* detector,
  answering a different question; the real population is **71 across 32 files**. 🔑 **A baseline lifted from a
  measurement of something else is worse than no baseline, because it looks measured.**
  ⚠ **That filed item is now CLOSED as F2-197** — and two of its numbers needed correcting first: the red
  `role="status"` badges are **25 tiles** (29 is the *sum of items* they assert) and the pages are **11**, not 12.
  ⚠⚠ **Ask what a COMPONENT asserts, not just what its page fetches** (F2-197, measured 2026-08-12).
  `lib/moduleLifecycles.ts` declared **56 processes across 11 exports** with `activeCount`/`overdueCount` as
  integer literals — **112 literals**, **130 "active" items** and **29 overdue** — rendered on **11 module pages
  that all fetch their real register from the server for the KPI strip immediately above**. 🔑 **F2-195's
  `kpiProvenance` gate could not see it: that gate is keyed on the `<Kpi>` tag, and here the numbers arrive as
  PROPS from a catalogue two files away, so there is no literal in the page's JSX to find.** Third depth of
  F2-192's headline in one lineage — claim level, then predicate level (F2-196), now component level; assume a
  fifth shape exists. Corollary that makes this findable: **a catalogue should declare what a thing IS, never
  what is happening to it right now** — a `status`, a metric or a clock stamp in a static corpus can only have
  been invented (F2-193's rule, applied to a corpus nobody had checked against it).
  ⚠⚠ **The trap in fixing it is that deleting the numbers ASSERTS HEALTH.** The stripe is
  `overdue > 0 ? danger : active > 0 ? accent : success` and `ProcessDetailModal`'s overdue tone falls to
  `success` + a green `CheckCircle2` whenever the figure is not > 0 — so the naive fix turns all 56 tiles green
  and starts claiming *"nothing is late"*. **An invented figure invites a question; an invented all-clear closes
  one.** The shape to copy: one predicate (`lifecycleHealth`) read by all three surfaces — `overdue` / `active` /
  `clear` / `unmeasured` (neutral `--border`, no counter, no badge, an em-dash with a bilingual hint as `title`
  **and** `aria-label`). 🔑 **`clear` needs BOTH counts**: a measured zero beside an unknown is not health, and
  green is an assurance. ⚙ The em-dash treatment is UX-008's `Kpi` convention — **fifth** recorded blocker in
  this programme that was already solved. And test `typeof x === 'number'`, never `!x`.
  ⚠⚠ **The ranking sorted by the fabrication to choose what the platform recommended.** `rankLifecycles`
  compared the counts directly and the compact rail promoted **`ranked[0]` unconditionally** as the filled
  *"Recommended next action"* chip. Now health-first (`unmeasured` can never outrank a measured tile) and
  **nothing is promoted unless the top tile is genuinely `overdue` or `active`**. ⚠ With the counts gone, `?? 0`
  alone would have left the order to `Array.prototype.sort` stability — i.e. catalogue declaration order dressed
  up as urgency; the unit test therefore feeds the array in reverse.
  ⚠ **24 of 56 tiles now carry a server-derived count** (31 expressions on 10 pages), each equal to *the number
  of rows its own register link opens* — a tile whose figure disagrees with the list it navigates to is worse
  than a tile with no figure. The **32 unmeasured** are commented decisions: the list is behind a lazy sub-tab
  (a first render would report 0), or **the terminal set would have to be invented** — `INCIDENT_WORKFLOW_OPTIONS`
  has nine stages and whether `CLOSURE` means finished or the act of finishing is a judgement. 🔑 **Inventing a
  terminal set to make a tile look wired is exactly F2-196's defect.** `overdueCount` is unmeasurable from this
  catalogue **by construction**: `sla` is free text — 41 declared, **30 distinct** (`"Quarterly"`, `"CFO
  escalation"`, `"SDAIA permit"`) — so no per-process due date exists client-side.
  ⚠⚠ **The one tile that COMPUTED its counts had both of them wrong**, which is why "it's computed" is not
  reassurance. `ScmPage`'s inline tier-mapping tile counted rows carrying a **single point of failure** and the
  chip rendered that as a red *"N items need attention"* — **a SPOF is a structural risk, not a missed
  deadline** — and counted `SUPPLY_CHAIN_TIERS`, browser-bundled constants, so it reported the same **5** for
  every tenant. 🔑 **A computed count is not a measured one.**
  ⚙ **A real truncation defect found in passing:** TPRM's "Reviews due" KPI was
  `expiringContracts.filter(c => c.days <= 90).length` and `expiringContracts` ends in **`.slice(0, 8)`** because
  it feeds a preview table — so the number **could never exceed 8**. 🔑 **A capped list is fine to render and
  wrong to count**, and F2-195's gate passes it because the value does come from the server.
  ⚠⚠ **The mutation harness found two gaps and one was in my own gate:** the truthiness ban was keyed on the
  **field** names, but the component aliases `lc.activeCount` to a local `active`, so `!!active` **SURVIVED** —
  🔑 **a rule keyed on a field name cannot see the local alias**; and the "catalogue emptied" negative control
  replaced `= [` with `= [] as ProcessLifecycle[]; const _unused = [`, leaving all 56 entries under a dead name
  so the `name:` census still read 56. **The mutation was wrong, not the gate** — 🔑 **a negative control that
  does not actually break the thing proves nothing, and reads as a hole in the instrument.**
  ⚠ **All 12 gate assertions passed on the first run**, which is also what a rotted regex looks like, so every
  detector was re-run against the pre-fix tree (`git show master:…`) as a control — **6 of 6 fire**. And the
  decrease-only floor is **31**, the number its own detector returns; F2-195 set one to 16 by copying a figure
  printed by a *broken* detector answering a different question.
  ⚙ **One landmine walked into:** `mvn -o clean package` launched with the dev backend live on :8084. It survived
  (the listener stayed bound and `package` rewrote `target/classes` before DevTools restarted into nothing), but
  the port check run *before* the build gave false comfort. 🔑 **The rule is not "check the port before probing"
  — it is "do not run `mvn clean` while you still need the dev backend."** ⚠ And `mvn -q` suppresses the
  `Tests run:` line the house rule tells you to grep for, so a `-q` run cannot support a test-count claim.
  ⚠ **The audit did find one real defect, and it was far narrower than the claim**: `CommitteeScheduledSweep`
  flipped action items to OVERDUE **in place** instead of calling `markOverdue` (whose javadoc said *"Called by
  the scheduled sweep"*). Since the sweep is the only producer in production, **no `ACTION_OVERDUE` audit row
  had ever been written for any tenant** — only the dedup marker `COM_FIRE:…` ("Sweep fired"), which records a
  trigger firing, not a status change and from what — and the two emitters disagreed on a payload key
  (`ownerName` vs `owner`, no `fromStatus`/`toStatus`), latent only because AR-148's `condition_json` is `{}`.
  🔑 **A duplicated transition agrees with the original on the part that is easy to check and differs on the
  parts nothing asserts.** Fixed by routing the sweep through the service with the **union** payload; pinned by
  `CommitteeOverdueTransitionTest`, including a structural leg that the sweep holds no `setStatus(`. ⚙ Side
  effect worth knowing: the transition is now **tenant-scoped** (`findByIdAndOrganisationId`) where the in-place
  flip trusted the query alone.
- ⚠⚠ **A de-mocking gate keyed on a MECHANISM certifies only the pages that use that mechanism, and a page
  that calls nothing sits outside all of them at once** (F2-198, measured 2026-08-12). `/ropa` was **routed,
  sidebar-linked and made no server call of any kind** — 370 lines, zero `api.`/`useApiList`/`fetch` — rendering
  9 invented PDPL Art. 31 processing activities, five `<Kpi>` tiles computed over them (one **`tone="danger"`**
  "needs review"), and `CONSENT_SUMMARIES` claiming **142,310 active / 8,420 withdrawn** consents. Real, probed
  live and identical in dev and prod: `privacy.processing_activities` **11 rows** (5 cross-border) and
  `privacy.consents` **32 ACTIVE / 3 WITHDRAWN / 1 EXPIRED**. **Five instruments missed it:**
  `demoFallbackContract`'s ratchet counts `/mock/i` **imports** (filename); that same file's F2-169 hard zero
  builds its population from `corpusNamesImportedBy`, a **three-entry filename allow-list** — 🔑 **the invariant
  is real, its POPULATION is a filename pattern**, so that zero still covers less than it looks like;
  F2-168's `demoRows()` tagging only reaches corpora that opted in, so F2-167's `RowActions disabled={isDemo}`
  cannot fire; the `isDemo` disclosure rule triggers on `useApiList`; and F2-195's `kpiProvenance` wants a
  literal or `|| N` inside the `<Kpi>` tag. 🔑 **The cheapest question that finds this class: does the thing
  rendering the figure ever ask a server anything?** `pageDataProvenance.test.ts` asks it — over **310 routed
  components, 73 pages call nothing** (47 sidebar-reachable), narrowed by F2-193's rule to **12 corpora on 11
  pages, 7 sidebar, 3 clock-minted** as decrease-only ratchets.
  ⚠ **The new fabrication shape is a CORRECT predicate over an INVENTED corpus** — `aggregateRopa()` is honest
  arithmetic, so it passes F2-195's gate (no literal) *and* F2-196's (which assumes the rows are real). Fourth
  shape in that lineage. And it outlived the others because `ropaConsent.ts` minted every `lastReviewedAt` from
  `Date.now()` at module load: **a clock-anchored fabrication has no tell** (F2-194).
  ⚠⚠ **STRIP LINE COMMENTS AND BLOCK COMMENTS IN ONE LEFT-TO-RIGHT ALTERNATION — using one alternation is
  load-bearing and getting it wrong silently shrinks your population.** Written the obvious way
  (`s/\/\*[\s\S]*?\*\///` first, **then** `//`, as TWO SEQUENTIAL PASSES), the census **deleted 40,247 of `App.tsx`'s 89,522 characters — 45% of the file** — because line 122
  is a `//` prose comment whose text contains a literal `/*`, so the block pattern ran from there to the next
  closing delimiter and took the `lazy()` declarations for the very pages being measured. It reported **24**
  server-less pages instead of **74** and **0** sidebar instances instead of **8**, silently excluding all four
  fabricated pages already known by name. 🔑 **A corrupted source yields a SMALLER population, which reads
  exactly like "those pages are fine"** — the quietest way for a scan to be wrong, and the 7th instance here of
  a detector satisfied by something other than its claim. Use `/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g`: at a `//`
  an inner `/*` can never open a block, and at a `/*` a URL cannot truncate it. Pin **both** directions.
  ⚠ **Correction (F2-211, measured in node): WITHIN one alternation the BRANCH ORDER is a no-op, so "line
  comments first" is not the rule and a mutation that merely swaps the branches is an EQUIVALENT MUTANT.**
  `//` and `/*` can never match at the same position, so leftmost-match consumes the line comment first
  whichever branch is written first — both orderings return the same answer on the exact `App.tsx` shape.
  The defect is **two passes, not branch order**. This matters when you write the negative control: a
  self-test whose "broken" variant is the reordered alternation passes on a correct tree *and* on a rotted
  one. Make the control the two-pass form. 🔑 **A surviving mutant is a hole only if the mutated code is
  actually WRONG — check before treating survival as a gap** (F2-206's rule, second instance). ⚠⚠ **This was a REPEAT: `Appendix/scripts/baseline_census.py` lines 139–142 already carry the correct alternation and a comment saying F2-68(c) found this same bug in the frontend scanner.** I wrote a second scanner over the same tree without reading the first. 🔑 **Before writing a scanner, read the scanner this repo already has.**
  ⚠⚠ **The next question in that lineage is what a page EMITS, not what it asserts — and it found a regulator submission generated from a browser constant** (F2-199, measured 2026-08-12). `/nca-ecc-export` rendered an *"NCA ECC-1 Self-Assessment Submission"* for the National Cybersecurity Authority — cover, six coverage KPIs, a **30-control matrix** with statuses/maturities/evidence references, **CISO and CEO signature lines**, a CSV download and a print-to-PDF path — routed, sidebar-linked as the group header, and making **no server call of any kind**; every figure came from `lib/ncaEcc.ts`. 🔑 **The harm differs in KIND from the six findings before it: a fabricated dashboard misleads whoever is looking at it, and looking again corrects it; a fabricated EXPORT leaves the platform — downloaded, signed, filed — where nothing downstream can tell the figures were invented and a signature line invites a human to vouch for them.** ⚠⚠ **The cover named `brandName` — "TSS GRC Platform", the software product — as the filing entity, beside a hard-coded CR number `1010299292`**, so every tenant's return identified the vendor, identically. 🔑 **A tenant-specific fact appearing as a literal in a shipped bundle cannot be right for more than one tenant and is most likely right for none.** Real, probed live and identical in dev and prod: `nca_ecc.filings` holds **4 filings** with five per-domain scores each, peer review, CISO approval and an NCA submission reference — and `NcaEccFilingsPage` **already rendered all of it**, so this was a retire (ADR-017 §D-5), not a rewire. 🔑 **The fabrication was MORE GRANULAR than the platform's own filing model, which is why it looked more finished**: the ECC-2:2024 return is scored **per domain 1–5**, and the page invented a per-control matrix underneath it. That matrix was unrebuildable anyway — **`responses_json` is NULL on all 4 filings in both databases** — and inventing it would have been F2-196's defect.
  ⚠⚠ **A gate keyed on EMISSION is the eighth instrument, and the seven before it are each keyed on a mechanism**: a filename (`demoFallbackContract`), a three-entry filename allow-list (its F2-169 zero), an opt-in tag (F2-168), a hook (`isDemo`), a JSX tag (F2-195), a prop (F2-197), a routed component that calls nothing (F2-198). `exportProvenance.test.ts` asks instead whether the thing **producing a file** has server data behind it — census **21 files emit a document**, zero routed pages unbacked after the fix, so it ships as a **hard zero with an EMPTY exemption map**.
  ⚠⚠ **Its first draft did exactly what F2-188 warns about, and only the REVERSE leg caught it.** I wrote two exemptions with fluent multi-sentence reasons — one a whole security narrative about browser-local MFA — and **both named Sprint 4 §3.3 CULLED pages** whose routes were removed, so neither was ever in the population. The forward assertion passed because the population was already zero. 🔑 **A reason written for something that is not in the population reads exactly like a reason for something that is**, and the direction that catches it is the one nobody writes first.
  ⚠⚠ **A shared detector hole worth knowing when you touch either provenance gate: the house idiom is a TYPED wrapper and the regex could not see it.** `SERVER_CALL` was `\b\w*[Aa]pi\.\w+\s*\(`, but `api.get<BoardPackAggregate>('/api/v1/reporting/board-pack.json')` puts a generic between the method name and the paren — **118 files** use that form. It surfaced as `BoardPackPage` reading as unbacked when `lib/boardPackAggregate.ts:174` is a plain `api.get<T>(…)`. Fixed in **both** gates. ⚙ State the error direction: this one **over**-reports (a page that does call a server reads as one that never could), so it inflates a population rather than hiding one — the opposite of F2-198's stripper bug, and why F2-198's eleven findings survive re-measurement unchanged. ⚠ A one-hop "is there server data in scope" rule must also exclude the **transport**: `lib/api.ts` contains `fetch(` by definition, so importing an `ApiError` type from it is not evidence of use.
  ⚠⚠ **Second finding, in an existing gate: `routeReachability`'s own comment described a check that does not exist.** Its `INTENTIONALLY_OFF_NAV` header claimed *"if one of these ever becomes reachable … the test below says which"*; the mutation harness disproved it, and measuring showed the claim could not simply be promoted to an assertion — **2 of 16 entries are legitimately linked** (`/login` from `Topbar`'s sign-out, `/portal/trust-center` from `PublicShell`), because "off-nav" there means *not a sidebar destination*. The comment was corrected and the enforceable half added as `RETIRED_MUST_STAY_UNLINKED`: five retired redirects that must have **zero** inbound links, plus a reverse leg that each is still a declared route so a bookmark cannot start 404ing. 🔑 **A comment describing a check that does not exist is worse than no comment, because it answers the question a reader was about to ask** — F2-184's shape, in a test's own documentation.
  ⚠⚠ **And it showed F2-198's retirement was incomplete.** `/ropa` passed that gate not by exemption but because **four in-app links still pointed at it** — two data-flow KPI tiles, a privacy-lifecycle tile, and `RopaWizard`'s post-publish `navigate('/ropa')`. 🔑 **A retirement is not finished when the sidebar is repointed: a redirect keeps every stale link working, which is exactly what stops anyone noticing they are stale** — including the orphan test, for which being linked is the passing condition. **When you retire a page, grep its path, not just the sidebar.**
  ⚠ **Three measurement errors of mine, all caught by running things.** (a) Guessing **column** names gives *"Invalid column name"*, which reads exactly like "the data isn't there" — resolve from `sys.columns`, not just `sys.tables`; F2-198 recorded the table-name version of this one level up and it recurred one level down. (b) **On an SPA dev server, HTTP 200 is not evidence a file exists**: Vite's fallback answers 200 with `index.html`, so a probe asserting a deleted module 404s reported **three false failures**; the tell is `Content-Type` (`text/html` vs `text/javascript`). (c) A structural check of the comment stripper counted declaration keywords and "lost" five — they were the words `export`/`type`/`return` inside **header comments**. Anchored to line starts the honest answer is **0 anchored declarations lost across all 773 files** against **612** lost by the wrong ordering — a stronger negative control than F2-198's single-file one, and the 8th instance in this lineage of a check satisfied by something other than its claim.
  ⚙ **Filed, and one of them is the reason no coverage figure went back on the submission surface: THREE disjoint real ECC control registers with three different answers**, identical in dev and prod — `cyber.cyber_controls` **29 controls, every one `NOT_IMPLEMENTED`, maturity NULL** (the register F2-195 corrected the "NCA ECC Score 87%" tile to **0%** against), `compliance.controls` `NCA_ECC` **14 rows: 6 IMPLEMENTED / 7 PARTIAL / 1 PLANNED**, and `evidence.controls` **34 controls with no status column at all**. That is F2-196's three-disjoint-registers shape for the NCA ECC self-assessment, and picking a winner changes what the platform reports to a regulator — an **owner decision**. Also filed: there is **no server-side ECC export generator**, so the annual return cannot be produced as a document through the export subsystem; and the **8 Sprint 4 §3.3 culled page folders are still on disk** (`process-mining`, `ai-governance`, `production-readiness`, `mfa`, `monitoring`, `backup-dr`, `white-label`, `csv-import` — **2,244 lines**, unrouted since Sprint 4, kept "for one cycle").
  ⚠⚠ **And the next one accepted an ACTION, not just a figure — a governance control that reported success without acting** (F2-201, measured 2026-08-12). `/approvals` was routed, sidebar-linked and made **no server call of any kind**; `lib/approvals.ts` was an approval ENGINE in the browser — **12 hard-coded requests** clock-minted from `Date.now()`, a **16-role `APPROVER_REGISTRY` naming invented individuals as the organisation's CISO, DPO, CFO and CEO**, and a PARALLEL/SEQUENTIAL routing machine — and `decide()` mutated `setRequests` and returned. An approver clicked Approve on a policy publication or a risk acceptance, saw a success toast plus a second toast claiming downstream chain rules had fired, and **nothing was written anywhere**; a reload put it back to PENDING. Meanwhile `approvals.requests` held **22 rows in dev (12 PENDING) and 21 in prod (11 PENDING)** across 17 real `entity_type` values, invisible. 🔑 **This is the INVERSE of F2-198/F2-199 and that is what made it worst: there was a complete server side with NO client, and it had been HARDENED first** — SEC-IAM-002 takes the deciding identity from the authenticated principal and never a body field, the service refuses a decision from anyone but the current assignee, `?myInbox=true` is a real personal queue, and the browser copy used the same type name as the server entity. It was not filling a gap, it was **shadowing a finished control**. So this is the first member of that population **REWIRED rather than retired** — `lib/approvalsApi.ts` + a page that decides through `POST /{id}/transition` and replaces the row **from the server's response**. **0 backend changes, 0 migrations.**
  ⚠ **Deliberately SIMPLER than what it replaced**: the server carries one `assigned_to` plus `delegated_from`, so there are no approver chains, no role registry and no multi-approver progress, and a gate leg refuses them from returning. 🔑 **F2-199's lesson twice in two slices — a fabrication that is MORE granular than the platform's own model is what makes it look more finished.** The load-bearing gate leg is behavioural, not structural: *no decision may be written into local state*, because a rewire that kept a local status write would look identical on screen and be the same defect.
  ⚠⚠ **A ninth instrument, blind for a ninth reason: F2-38c's fabricated-directory guard keys on the literal module name `'lib/userDirectory'`.** `APPROVER_REGISTRY` was a **second** fabricated directory, sixteen invented officers deep, in a file called `approvals.ts` — invisible to it. Same shape as F2-198's finding about `demoFallbackContract`: **the invariant is real, its POPULATION is a filename.**
  ⚠⚠ **My worst error in this slice, caught before it shipped: a dead-key detector that swept 71 keys.** Asking for `t('key')` reported **71** dead translation keys including ~57 `approvalChain*` and **`approvalsSidebar`** — both false, because `t('approvalChainKpiInflight', 'In-flight')` is a **two-argument** call a pattern ending in `'\)` cannot match, and `approvalsSidebar` is a `labelKey`, never a `t()`. Deleting them would have blanked five pages' labels and the sidebar row. With F2-199's rule — the quoted key appearing anywhere outside the translation files — the answer is **11 dead, 12 new**. 🔑 **A usage detector must match every call shape the codebase uses, and for a DELETE the safe direction is to OVER-approximate usage.** ⚠ F2-165's gate also caught a dark method I had just written (`approvalsApi.get`, wrapping an endpoint no screen opens) — now not wrapped at all, with a note to add it in the commit that adds a detail view. **An unused client method should not ship.**
  ⚙ **Filed, and it is the big one behind several earlier findings: `lib/platformPersistence.ts` is a GLOBAL persistence-mode switch whose default is `'demo'` = pure `localStorage`** (stored under `grc.persistence.mode.v1`, with a topbar badge). It declares **11 adapters**, is imported by only **6** lib files, and `mirrorSaveToBackend` / `mirrorTransitionToBackend` / `mirrorDeleteToBackend` all `return` immediately in demo mode — so on the default setting the REST half is inert. Six adapters have no consumer beyond the `LIFECYCLE_ADAPTERS` map, and `APPROVAL_ADAPTER` named `/api/approvals/requests`, the endpoint F2-201 wired, while nothing ever called it. **It is the mechanism behind F2-196's fourth DSR register and needs its own slice.**
  ⚠⚠ **That filing was right about the file and understated it twice, and F2-202 closed it: the switch had NO SETTER, so `'demo'` was an INVARIANT, not a default** (measured 2026-08-13). `setPersistenceMode` had **zero** callers in the product, nothing wrote `grc.persistence.mode.v1`, and unlike its sibling the switch took **no Vite env input** — so all three `mirror*ToBackend` helpers were unconditional no-ops at **11 sites across 5 modules**, in every build, for every user. **And the READ path never consulted the mode at all**: `loadTreatmentPlans()` and its four siblings read localStorage unconditionally, so a write that did reach the server could never be shown. 🔑 **Writing to a store you never read from is not persistence, it is a one-way drain** — the mechanism was incoherent in principle as well as inert in practice. `lib/platformPersistence.ts` is **deleted**; `lifecycleStoreContract.test.ts` refuses its return by name and `LifecycleStoreBackingContractTest` refuses the file's re-creation.
  ⚠⚠ **There are TWO mode switches and the topbar badge reads the OTHER one.** `lib/productionMode.ts` (`'demo' | 'migrating' | 'production'`, key `grc.mode.v1`) **does** read `VITE_MODE`, so a deployment could set `VITE_MODE=production`, turn the badge green and label it *"Production"*, while the switch gating every lifecycle write stayed `'demo'` and unreachable. `platformPersistence.ts`'s own header claimed *"Drives the ModeBadge in the topbar."* It did not, and never had — F2-184's shape on the file's first line. A **third** file, `lib/persistence.ts`, declares a third union with the same type name over `/api/v1/persistence/{key}`; that controller is real and `platform.persistence_row` holds **one row, `store_key='demo-key'`**.
  🔑 **The live defect it hid, and the reusable lesson: `apiClientReachability` correctly reported all eight `platformPersistence` adapters as unreached for months and that was tolerated, because "an adapter nothing calls" reads as unfinished plumbing. The three `mirror*` helpers HAD callers in five modules, so they never appeared in that scan at all.** ⚠⚠ **A reachability scan measures whether a symbol is *called*, never whether calling it *does anything*** — and the second question is where the defect was. In `kb.ts` every call went to the real server **except** `listArticles()`, which resolved through the stuck switch to an empty localStorage key and returned `[]`: `kb.articles` holds **22 rows / 21 PUBLISHED** and `GET /api/kb/articles` returns all 21, so an author could create, publish and open an article by slug and **never see it in the list**. `KbHomePage` explained that away as *"empty until articles are published"* — a cause that was not the cause.
  ⚠ **Deleting a decoy removes a fingerprint, so the trace has to move somewhere enforced.** `lib/lifecycleStores.ts` now declares **8 stores** with state, the real endpoint, `schema.table`, measured dev/prod rows, the localStorage key and a consequence-naming note; two decrease-only figures ride on it (`BASELINE_BROWSER_LOCAL_STORES` **6**, `BASELINE_NOT_FULLY_SERVER_BACKED` **7 of 8**). ⚠ **A THIRD state exists that the first draft missed**: `auditLifecycle.ts` reads four real `/api/audit-lc/*` endpoints and drops every write (`SERVER_READ_LOCAL_WRITE`) — *harder* to notice than an honestly local store, because the list it shows is real. It also seeds `SEED_FINDINGS` when the server returns zero rows, the F2-158 fallback shape.
  ⚠⚠ **Writing that registry, I inferred three of the eight endpoint paths from the schema the table sits in and ALL THREE WERE WRONG, each differently**: `/api/v1/privacy/ropa` (not `/api/privacy/ropa`), `/api/v1/privacy/dpias` (**plural, its own controller class**, not a sub-path), and `/api/privacy/cross-border-transfers` (**no `v1` segment at all**). 🔑 **A schema name does not predict a path, and a plausible endpoint string in a declared registry is worth nothing until something resolves it** — which is what the backend half of the gate does. Fourth guessed-path error in this lineage, and the first caught by a gate rather than a probe. ⚙ Independently: **3 of 5 adapter `localKey` values disagreed with the modules' real keys** (`grc.bcm.invocations.v1` vs `…invocation.v1`, `grc.cyber.vulnerabilities.v1` vs `grc.cyber.vuln.v1`, and a key claimed for a module that keeps none) — proof the machinery was never once exercised.
  ⚠⚠ **Prose about code, stored as DATA, invents callers in a token-matching scan.** `apiClientReachability` flagged the new registry as an API client with no importer (its `/api/…` strings are values) *and* reported `hydrateAuditLifecycle` as **now called**, because a note explaining what that method fetches put the token in a production file. Fixed with a `DECLARATIVE_REGISTRIES` exclusion enforced **both ways** — a file may be excluded only if it contains no real `api.*` call. **When you add a declarative registry, expect every name-matching instrument in the tree to react to its prose.**
  ⚠ **The DSR rewire that came with it, and the rule it re-applies:** `createDsr()` minted the **PDPL Article 4 30-day statutory deadline** as `Date.now() + 30 * 86_400_000` and the tracking reference as `DSR-<year>-<Math.random()*999>` — three digits of entropy. The page's Extend handler recomputed the 60-day extension **and invented the justification text**; `lifecycleChains.ts#chainBreachToDsr` was a third minter with no callers. All deleted, per F2-172: **only the server may issue a tracking code or a statutory deadline.** Server side, `DsrCaseService.create` was honouring a **client-supplied external id** (F2-172(d)'s exact defect) and `TransitionRequest` had **no field for a rejection reason**, so a lawful PDPL refusal could never be recorded; `sla_breached_at` and `rejection_reason_en/ar` had existed since V17 with the DTO never declaring them (F2-198's `crossBorderTransfer` shape). ⚠ **`slaBreachedAt` is not the same question as "is the deadline past"** — dev's two `EXTENDED` cases carry a breach stamp and **prod's two do not**, because F2-196's sweep fix is not deployed, so the page surfaces `pastDeadlineUnrecorded` separately.
  ⚠ **`default-property-inclusion: non_null` means one row's key set cannot answer "is this field on the wire?"** — an absent key is indistinguishable from a field the DTO never declared, which is the very defect being fixed. My probe reported two new fields as missing for that reason. Take the **union of keys across rows** and require a non-null value somewhere.
  ⚠ **Retire, do not rebuild, when a real surface already exists** — `PrivacyPage`'s ROPA tab already renders the
  register, so a second *real* ROPA screen would have made a fourth surface for one statutory object, which is
  F2-196's own defect. ADR-017 §D-5 ratified this in F2-42. `/ropa` redirects (F2-137: a bookmark must land
  somewhere true) and `siblingsUseQuery` — `group.items.some(s => s.to.includes('?'))` — turns query-aware
  sidebar highlighting on automatically, the **6th** blocker in this programme that was already solved.
  ⚠⚠ **But retiring must not silently drop a real signal the fabrication happened to display.** Cross-border
  transfers (PDPL Art. 29, **5 of 11**) were preserved as a tile counted over the *same* `ropa.data` the activity
  count uses. `crossBorderTransfer` was always on the wire — `GET /privacy/ropa` returns the **entity** — and
  `PrivacyRopaDto` merely never declared it, which is *why* the page invented its own count. Conversely
  `lastReviewedAt`/`nextReviewAt` exist on **no column and no wire**, so those two tiles were deleted rather than
  reconstructed: inventing them is F2-196's defect.
  ⚠ **Resolve table names from `sys.tables`, never guess them.** Measuring the backends behind the other ten
  pages, a first pass guessed six table names and **five did not exist** — the same guessed-path error as
  F2-197's probe, and it would have produced ten confident false "no backend" claims.
  ⚠⚠ **Asking the MIRROR question — how many things READ this status, and do they agree? — found a P1 on a
  statutory control** (F2-196, measured 2026-08-12). The platform has **three** server-side DSR registers on
  three tables with **disjoint** status vocabularies and **no CHECK constraint on any of the three status
  columns** (`privacy.data_subject_requests` 20 rows · `privacy_lc.dsr_cases` 12 · `data_officer.dsr_request`
  17, identical in dev and prod; only `IN_PROGRESS` and `REJECTED` common to any two). With no CHECK, **the
  vocabulary lives only in the heads of its readers** — and seven readers had produced four vocabularies.
  🔑 **`PrivacyGapService.dsrSlaSweep()` listed `EXTENDED` as closed.** It is not: the declared machine gives
  it five outgoing edges and `DsrCaseService.transition` **pushes `slaDeadline` out 30 days** on entering it,
  so the deadline already accounts for the PDPL Article 4 extension and a case past it is unambiguously in
  breach. Excluding it meant **the extended deadline could never be enforced** — the requests most likely to
  breach were the only ones exempt. **Not latent**: the sweep ran 2026-06-22 and stamped 7 of 12 cases; the 2
  `EXTENDED` ones were already 4 and 11 days overdue and carry `sla_breached_at = NULL` — today 55 and 62 days
  over, past the Article 4 ceiling, with no event and no audit row, in **both** databases.
  🔑 **Five artefacts, and the two that were wrong were the two that fire the event** — the machine, the owning
  service's own `overdue()` query and a frontend aggregate all said open; the sweep and **`DsrCaseRepository`'s
  own javadoc** said closed. ⚠ The javadoc documented *the value the caller should pass*, which made the one
  correct caller read as the deviation.
  ⚠⚠ **The rule to copy: "open" must be the COMPLEMENT of a declared terminal set, never a second list.** An
  inclusion list over an open vocabulary silently drops every token nobody remembered — that is how the PDPL
  Article 23 tile answered **9** where 12 are open, dropping `ESCALATED` and **`OPEN`, the status
  `DsrPromotionAdapter` writes**, so every DSR filed through the platform's own front door was invisible; and
  how `stats.dsrOpen` answered **10** from a *different* inclusion list. With a complement, an unrecognised
  status is chased rather than excused, which for a statutory clock is the safe direction.
  ⚠ **And derive the terminal set, never type it**: `DsrCaseLifecycle.TERMINAL` is computed from `ALLOWED` as
  *the states with no outgoing edges*, so the machine and the terminal set cannot drift.
  🔑 **A vocabulary mismatch has no direction.** The count erred low (flattering) and the SLA column erred high
  (7 finished rows each rendering a red "N days overdue" against the statutory deadline) from the same missing
  tokens — `status === 'FULFILLED'` names a token this register holds in **0 of 20 rows**, because it lives in
  a *different* table. F2-193 and F2-195 were uniformly flattering; do not assume that again.
  ⚠⚠ **F2-195's `kpiProvenance` gate passed all of this**, because the tile's value genuinely does come from
  the server. 🔑 **A provenance gate cannot see a wrong predicate** — the third shape in that lineage after
  literals and `|| N` fallbacks.
  ⚠⚠ **`ModuleGraphRatchetTest` will refuse the obvious placement, and it is right**: importing a business
  module's lifecycle class into `platform/*` is an ADR-002 kernel inversion (55 → 56; the ratchet is
  decrease-only and **may never be raised**). Put the predicate behind a **repository default method** the
  out-of-module callers already import — better anyway, because the status vocabulary then never crosses a
  module boundary and a caller that cannot name a status cannot invent its own idea of "open".
  ⚠⚠ **A mocked repository will happily return rows for a predicate that is wrong.** `PrivacyGapServiceTest`
  covered the sweep's dedup and audit behaviour thoroughly while stubbing the closed-status argument with
  `any()`, so the wrong selection was invisible under a green test. Capture that argument, or assert the
  accessor that binds the set and `verify(never())` on the raw finder.
  ⚠ **And a gate you write after reading F2-192 will still have F2-192's defect.** My restatement scan detected
  a collection literal and a `||`-chain — **the two shapes I had just fixed** — and a mutation restoring a
  four-way `countByOrganisationIdAndStatus(...)` sum SURVIVED. There are at least **three** ways to restate a
  vocabulary; the third is a per-status arithmetic sum. 🔑 **A gate verifies the claim shape it was written
  for, including one written to answer that very finding.**
  ⚠ Two instrument bugs caught only by running it: a `\[([^\]]*)\]` parse matched the TypeScript **type
  annotation** `readonly string[]` and returned an empty set — reading exactly like *"the frontend declares
  none"* (**sixth** instance here of a pattern satisfied by something other than its claim) — and the page scan
  fired on **my own correction comment** quoting the code it replaced, three methods from the `stripComments`
  helper written for that trap. **Strip comments from `.tsx` too, and anchor a parse on the assignment.**
  ⚙ **No CHECK constraint was added, deliberately.** The vocabularies hold synonyms
  (`FULFILLED`/`RESOLVED`/`COMPLETED` all mean finished; `NEW`/`OPEN`/`PENDING` all mean not started). A CHECK
  admitting all of them **blesses the synonyms**; collapsing them rewrites live rows in two databases — an
  owner decision, not a side effect of a display fix (F2-183's rule).
  ⚠ **A frontend artefact that "agrees" may be a `localStorage` store.** `privacyLifecycle.ts` computes the
  correct complement — over `SEED_DSR` in the browser, so it is evidence the correct rule was **written down**,
  not that any screen showed a correct number. It is also a **fourth** DSR register shadowing a real API
  (`/api/privacy-lc/dsr-cases`), still open. Checking `loadDsr()` is what stopped that being cited as proof.
  ⚠⚠ **A registry is only as honest as the rule that decided its membership, and that rule is usually
  implicit** (F2-204, measured 2026-08-13). `lib/lifecycleStores.ts` — shipped one slice earlier — declared
  **6 of the 16** browser-local corpora the four lifecycle modules own, because it admitted a corpus if it
  happened to have a controller: a rule never stated and therefore never checked, so ten were invisible while
  `BASELINE_BROWSER_LOCAL_STORES = 6` read as *"6 stores remain"*. Same shape as F2-198's finding about
  `demoFallbackContract`, committed in the artefact built to replace that decoy. **The fix is a DERIVED
  population**: the gate now reads the modules' own `grc.*.v1` localStorage keys, so membership is a
  measurement rather than a recollection.
  ⚠⚠ **Seven populated registers have no endpoint of any kind — 69 rows in dev AND prod.**
  `risk_lc.kri_breaches` 12 · `bcm_lc.bia_records` 8 · `bcm_lc.recovery_strategies` 8 · `bcm_lc.drills` 5 ·
  `cyber_lc.assets` 14 · `cyber_lc.cyber_incidents` 10 · `cyber_lc.threat_intel` 12. **Resolve this by
  searching EVERY `@RequestMapping` path, never by looking under `module/*_lc/`** — similar objects DO have
  controllers in other modules over DIFFERENT tables (`/api/v1/bcm/bia-assessments`,
  `/api/v1/socops/ot-assets`, `/api/v1/cyber/threat-intel`), which is F2-196's disjoint-register shape and
  exactly what makes *"there is no endpoint"* easy to get wrong in the reassuring direction. 🔑 **An
  unwireable store and an unwired one are different facts**; the `NO_SERVER_SURFACE` state exists so the next
  slice does not re-measure all of it.
  ⚠⚠ **A decrease-only ratchet cannot express "the population was undercounted" — raising the number is
  indistinguishable from tolerating a regression.** Declaring the real 16 would have meant 6 → 17. So no figure
  was raised: the ratchet was **re-scoped** to *stores that have an endpoint and still do not write to it*
  (7 → 6, honestly decreasing), the unwireable set got its own decrease-only figure, and the total is asserted
  **exactly** so the next omission fails rather than shrinks a count. 🔑 **When a ratchet's number has to go
  up, ask whether its QUESTION was wrong.**
  ⚠⚠ **My own new gate refuted 2 of my 11 claims on its first run, and the live probe refuted a third.**
  `noServerSurfaceStoresReallyHaveNone` verifies a "nothing serves this" claim **structurally** — a controller
  cannot serve a table no `@Entity` maps to — and it fired on `socops.pentest_engagement` (served at
  `/api/v1/socops/pentests`) and `risk_lc.treatment_actions` (a `TreatmentAction` entity exists). Same 2-in-11
  rate F2-188 and F2-192 found in their predecessors' reasons. 🔑 **The argument for such a gate is that
  rate, not the count it produces — expect roughly two in ten of a new instrument's own claims to fall.**
  ⚠⚠ **F2-172(d) has a FOURTH instance and I had already written that it did not.**
  `TreatmentPlanService.create` honoured `req.getId()`, so a client could name an ERM treatment plan's
  reference. I had written *"the server mints it"* into the new API client on the assumption that F2-203 had
  found the last one; only the live probe disagreed. 🔑 **An assumption that a family of defects is closed
  is the cheapest thing to check and the easiest to skip.** Grep `req.getId() != null ?` before claiming any
  create path mints its own reference.
  ⚠⚠ **A page can invent the SUBSTANCE of a governance decision, not just its author — and the server may
  persist it.** `RiskLifecyclePage` passed `'Mohammed Al-Mahmoud (CRO)'` as the approving Chief Risk Officer
  **and** the literal `'Insufficient cost-benefit justification'` as the rejection reason. `TreatmentPlanService`
  writes `rejectedReason` to the column, so **wiring the store naively would have written invented text into
  `risk_lc.treatment_plans.rejected_reason` as a real officer's stated rationale.** Fourth invented officer in
  three slices. A rejection reason comes from the rejecting officer or not at all.
  ⚠⚠ **An undo affordance over a server-enforced state machine is a promise the machine forbids.** The
  page's undoable toast called `transitionPlan(id, priorStatus)` — a BACKWARDS transition. `ALLOWED` has exactly
  one reverse edge (`REJECTED → DRAFT`), so un-submitting, un-approving and un-completing each answer 409
  (probed). While the store was browser-local the undo silently worked and the register drifted from anything
  the machine would ever have permitted. If reversal is a requirement it is a reverse EDGE plus an audit reason,
  decided server-side — not a client-side rewind.
  ⚠ **One file short is the whole error, third instance.** I wrote *"TreatmentPlanDto has no actions field"*
  into a registry note and two comments, from reading the FRONTEND interface in `lib/api.ts`. The **server's**
  DTO declares `List<TreatmentActionDto> actions` and maps all 10 rows on every read — F2-198's
  `crossBorderTransfer` shape, where the value is on the wire and the client type never mentioned it. Only the
  WRITE is missing (no actions sub-resource), so the list is restored read-only and the "Mark done" control
  stays removed.
  ⚠ **A registry whose whole purpose is measured numbers had two of them wrong.** `risk-treatment` said 11
  rows — 11 exist, **10 are live** (the count included a soft-deleted one); `privacy-dpia` said 6/6 and prod
  holds **5**. The two databases are not identical everywhere, and asserting they are stops the figure being a
  measurement. And **a register's size is not what a persona sees**: `listForOrg` is tenant-scoped, so two of my
  probe's expectations (10 plans, 10 actions) were wrong against a correct server — the tenant sees 9 and 8.
  ⚠ **A per-module check over a per-corpus property fails as soon as one module hosts two corpora.** The
  existing leg asserted that a `localKey: ''` store's module writes NO keys at all — true only while every
  module hosted exactly one declared store. Re-scoped to "every key the module writes is claimed by SOME store".
  Same defect F2-203 fixed in its disclosure leg; it fails loudly here, which is why it was cheap.
  ⚠⚠ **A latent crash in a display path is held shut by whatever else is broken around it, so fixing one
  defect is what detonates the next** (F2-203, measured 2026-08-13). The internal-audit workspace had
  **three** `LABEL_MAP[row.status][lang]` TypeErrors, each on real rows, **none reachable**: the findings one
  behind an engagement picker that filtered every row away, the annual-plan one behind a `plans[0]` that
  happened to select the other row, the CAPA one behind a ternary whose `overdue` branch won. **This slice's
  own fix to plan selection opened the second — in the browser, minutes after the gate went green.** 🔑 **When
  you fix a filter, a selector or a fallback, assume something downstream has never run, and run it.**
  ⚠⚠ **`audit_lc` has ZERO CHECK constraints and ALL FOUR of its registers hold statuses their own service's
  vocabulary does not declare** — identical in dev and prod: `findings` 7/20 (`IN_PROGRESS`/`REMEDIATED`/
  `VERIFIED`), `annual_plans` 1/2 (`APPROVED`, where the service declares `AC_APPROVED`), `working_papers`
  8/12 (`REVIEWED`), `capa_tasks` 3/12 (`VERIFIED`). **19 of 46 rows cannot be advanced by ANY route**, because
  every service does `TRANSITIONS.getOrDefault(status, Set.of())` → empty → 409 with an empty allowed-set. The
  sharpest is the annual plan: the one `APPROVED` row is the **2026 plan the organisation is executing** and it
  can never reach EXECUTING or CLOSED. **Do not fold them into the declared vocabulary** — each is a synonym
  and collapsing synonyms rewrites 19 live rows in two databases (F2-183/F2-196's rule). Declared, labelled
  bilingually, disclosed on the row with an announced `role="status"` badge, no button offered, filed for an
  owner. ⚠ It is not only `status`: `working_papers.type` holds `DESIGN_TEST`/`OPERATING_TEST`/`SAMPLE_TEST`/
  `FOLLOW_UP` — **11 of 12 prod rows** — against a five-value union.
  ⚠⚠ **A gate keyed on the maps you know about cannot see the map you did not list, and the omission reads
  exactly like coverage.** F2-203's first label-map leg named four maps and passed while `WP_TYPE_LABEL[wp.type]
  [lang]` sat in the same file; the browser found it by crashing **after** the gate was green. Ban the **shape**
  (`SOMETHING[expr][lang]`), not a list of names, and carry a positive control proving the detector fires on it.
  ⚠⚠ **A one-hop "is there server data in scope" provenance rule certifies the FILE, not the FIGURE.** F2-199's
  `exportProvenance` passed the CMA Article 95 Audit Committee packet because `auditLifecycle.ts` does call
  `api.get` — while section 2 and the whole *Engagements* sheet came from `AUDIT_ENGAGEMENTS`, a `demoRows()`
  corpus for a **different** register. The exported document listed eight invented engagements each reporting
  "WPs: 0 · Findings: 0" beside a section counting 20 real findings: **it contradicted itself on the same
  page.** Tenth instrument in this lineage, blind for a tenth distinct reason. The next question after *does
  this page call a server?* (F2-198), *does this file emit a document?* (F2-199) and *does the call reach
  anything?* (F2-202) is **which of this file's data reaches the thing it emits.**
  ⚠⚠ **A picker built from a catalogue can show an empty list; a picker built from the groups its own lists
  contain cannot.** `engagement_code` is free text with no FK and no CHECK, and there are **five disjoint code
  spaces** (findings `ENG-IA-00x`/`IA-2026-001`, working papers `IA-2026-001`, `compliance.audit_engagements`
  `AUD-2026-00x`, `audit_lc.ia_engagement` `ENG-2026-00x`, `annual_plans.q*_codes` `ENG-CYBER-2026Q1`…). A
  fourth broken join sat beside them: `capa_tasks.finding_id` holds the **ref number** in every seeded row while
  the module's own `createCapa` writes the **external id**, so all 12 CAPAs were invisible. Read tolerantly and
  file — reconciling 32 rows across two databases is an owner decision.
  ⚠ **`FindingService.create` honoured a client-supplied `id` AND `refNumber`** — F2-172(d)'s defect for the
  third time after the intake substrate and F2-202's DSR cases. **Grep for `req.getId() != null ?` before
  assuming a create path mints its own reference.** And the client-side sequence it replaced counted a cache
  that included a seeded row, so the first real finding on an engagement would have been numbered FIN-002.
  ⚠ **An invented officer's name can be display-only on two writes and PERSISTED on a third in the same file.**
  `transitionAnnualPlan`/`transitionWorkingPaper` stamp `actor(auth)` and ignore the body — but nothing read the
  response, so the screen credited an Audit Committee approval to a person who does not exist while the audit
  trail recorded the real user. `createWorkingPaper` saved the body verbatim, so an invented auditor **was** the
  stored author of an IIA 2330 working paper. **Replacing the row from the server's response fixes both halves
  and removes any need for a client-side identity.**
  ⚠ **A selection fix can reintroduce the bug it names.** Replacing `plans[0]` with "most recent year first"
  made a **Q3-2026** Audit Committee packet report the **2027 DRAFT** plan — the exact shadowing the
  replacement's own comment claimed to prevent — and neither version is visible in a passing test. **The
  artefact has to be read.** (`pickCurrentPlan` takes the current year; the Plan tab was also hard-coding `2026`
  in its quarter titles beside a real `plan.year` from the same row.)
  ⚠⚠ **Two more of my own gate bugs, both F2-192's defect in a gate written after quoting F2-192.** (a) A ban on
  `IN_PROGRESS:` written for the FINDING table fired on `CAPA_ALLOWED`'s legitimate edge the moment the sibling
  machines were declared — **scope a scan to the shape of its claim.** (b) The disclosure leg counted
  `role="status"` across both files with a `>= 4` floor (there are five, so deleting one still passed) *and*
  asserted each guard token against `pageCode() + tabsCode()`, so deleting the plan disclosure from the *page*
  passed on `PacketTab`'s occurrence in the *tabs* file. 🔑 **A count with a floor cannot see one member vanish,
  and a token asserted across concatenated files is satisfied by the other file.** Only the mutation harness
  found either.
  ⚠ **A probe that writes rows must be RE-RUNNABLE, which is a different property from excluding its own
  writes** (F2-202's lesson, one run later): a fixed `externalId` made the second run fail on the unique index,
  which reads exactly like the fix having broken.
  ⚠ **The port check is not proof the app is up.** `mvn -o test` recompiles into `target/classes` and DevTools
  restarts the context **while the listener stays bound** — `netstat` showed LISTENING through a window where
  every request was refused with `ConnectionRefused`. **Poll `/actuator/health`, not the port.**
  ⚠⚠ **A LIVE SERVER WIRE CAN TERMINATE IN A BROWSER CONSTANT, and from either end that reads as an
  integration that works** (F2-205, measured 2026-08-13). `VulnerabilityService.transition` calls
  `broadcaster.broadcastEntityUpdate(orgId, "vulnerability", …)` → `/topic/org.{id}.changed.vulnerability`;
  `VulnerabilitiesV2Page` subscribed to **exactly** that topic (checked against `RealtimeBroadcaster`'s format
  string, not assumed) — and its handler was `setAllVulns(loadVulns())`, where `loadVulns()` read
  `localStorage`. So another user's real transition arrived as a websocket frame that refreshed a constant no
  server event could ever change. **The backend sees a subscriber; the frontend sees a handler firing.** 🔑 No
  instrument in this tree can see it, because every component is genuine except the direction the data flows —
  a reachability scan asks whether a symbol is *called* (F2-202), a provenance gate asks whether a page *calls a
  server* (F2-198), and both answer yes. **When you find a realtime subscription, read what its handler does
  with the frame.**
  ⚠⚠ **And the export gate's blind spot is the same question one level sharper: the server call it found was
  the one CARRYING THE FABRICATION OUT.** `/cyber-lifecycle` emitted an *"NCA HASEEN — ECC v3 ANNUAL
  SELF-ASSESSMENT FILING"* four ways (a `.txt` download, print-to-PDF stamped **"CONFIDENTIAL — REGULATOR"**,
  and branded `.xlsx`/`.docx` through the platform's own export subsystem) entirely out of browser constants —
  and F2-199's `exportProvenance` was **green** over it, because the page's **only two** `SERVER_CALL` tokens
  were `reportingApi.packetXlsx(` / `packetDocx(`, which take the fabricated payload as their **argument**.
  🔑 **A rule that asks "does this page talk to a server?" cannot tell an inbound READ from an outbound WRITE,
  and at an export endpoint that difference is the entire question.** The discriminator is mechanical and
  enumerable — a `reportingApi` member taking a caller-supplied `body` is an EMITTER; every other member is a
  `downloadFile` GET the server builds from its own data — and the gate now checks that classification against
  `api.ts` in both directions, so a third body-taking export endpoint fails until it is classified.
  ⚠⚠ **Correcting that revealed two more members, so the hard zero is GONE — and the reason is worth the
  words.** `/crisis-management` emits an **NCEMA** crisis packet and `/bcm-lifecycle` a **SAMA
  operational-resilience** packet, both from browser constants, both certified for the identical reason. The
  BCM one is **not wireable by a frontend slice**: three of its four registers are F2-204's `NO_SERVER_SURFACE`
  stores (21 rows, no controller). They are recorded as measured, **filed defects** in an exactly-asserted
  decrease-only map, never as designs. 🔑 **F2-204's rule points the other way here: there a ratchet's QUESTION
  was wrong, so it was re-scoped; here the question was right and the ANSWER was under-measured, so the number
  moves and says so. A hard zero over a population the instrument cannot see is the most reassuring possible
  artefact.**
  ⚠⚠ **FIVE client vocabularies for one server machine, and what let them survive is that they agreed with
  EACH OTHER.** `VulnStatus` declared `ACCEPTED` (a state the server does not have) and no `FALSE_POSITIVE`;
  the workspace pipeline held a private linear `Record<string, VulnStatus>`; its table had five hard-coded
  `status === '…'` button branches; the lifecycle overview's `stages` listed six codes; the V2 page's **filter
  options** listed six. Consequence: **7 of 36 live rows (dev = prod) carried a status no screen could name, no
  control could reach and no filter could select** — so marking a vulnerability a false positive, a lawful NCA
  ECC disposition with its own bilingual reason columns, was impossible. 🔑 **Any two copies read side by side
  looked consistent; the one they all disagreed with was the only one with a vote.** F2-196's question ("how
  many things read this status, and do they agree?") needs the follow-up **"and which of them is the server?"**
  — and **consistency among copies is not evidence, it is the mechanism of concealment.**
  ⚠ **The browser had NO transition table**, so `transitionVuln` was `{ ...v, status }` and `NEW → CLOSED` was
  permitted: a critical exposure marked closed having never been triaged, assigned, remediated or verified,
  with no `verified_by` and no audit row. The server answers **409** (probed). **31 of 36 rows are past their
  ECC deadline and not closed**, including all 9 open CRITICALs, and `GET ?overdueOnly=true` — which serves
  exactly that list — had no caller. The fabrication showed **one** overdue row: **third consecutive
  fabrication erring in the flattering direction** after F2-193's cadences and F2-195's KPI tiles.
  ⚠⚠ **SIX copies of a server policy constant, and the sixth was in the TRANSLATION FILES.** Five were code.
  The sixth is `cycVulnsSub` in both `en.ts` and `ar.ts`: *"Critical 7d / High 14d / Medium 30d / Low 60d …
  and asset tier (Tier-1 halves the window)"*. `SLA_DAYS` is **7 / 30 / 60 / 90**, so it told an operator HIGH
  was due in 14 days where the server had stamped 30 — **and the server has no asset-tier rule at all.** Found
  in the BROWSER, after every gate assertion was green. 🔑 **A claim about server behaviour can live anywhere a
  user reads text, and a gate scoped to code cannot see the one place written specifically to be read.** Scope
  such a leg to the module's own keys — other modules legitimately state their own SLA rules in prose — and run
  a negative control, because a new leg that passes is indistinguishable from a vacuous one.
  ⚠⚠ **A REGRESSION MY OWN TWO PREVIOUS SLICES INTRODUCED AND NEITHER NOTICED.** F2-203 made `loadFindings()`
  return a cached server array and F2-204 did the same for `loadTreatmentPlans()`; both replaced functions that
  had returned a seeded corpus **synchronously**. `LifecycleOverviewPage` never hydrates, so its internal-audit
  and ERM treatment lanes had been rendering **empty** ever since. 🔑 F2-203's own lesson turned on the slices
  that recorded it: **when you fix a store, a filter or a selector, assume something downstream has never run
  since, and run it.** It stayed invisible because an empty lane reads as a tenant with no rows rather than a
  page that forgot to ask (F2-197's absence-reads-as-health), and **nothing in the type system can see a
  missing call** — so it is asserted now.
  ⚠⚠ **The mutation harness found two holes in my own new gate, both of kinds this file already names.**
  (a) The terminal-set ban was `doesNotContain("List.of(\"CLOSED\")")` over the whole file — it banned **the
  exact literal the slice had just deleted**, so `TERMINAL = Set.of("CLOSED")` with the stream expression parked
  on an unused field SURVIVED. **F2-192's headline inside a gate written after quoting it.** Re-scoped to
  `TERMINAL`'s own initialiser. (b) The hydration leg was `contains("hydrateVulns")`, and deleting all three
  call sites SURVIVED **because the name is still in the import list** — F2-194's substring-satisfied-by-an-
  import, verbatim, in a gate built to catch a missing call. 🔑 **Assert the call shape (`name\s*\(`), never the
  token; and scope a ban to the shape of its claim, never to the spelling you just removed.** 16/16 after.
  ⚠ **And the gate caught a real miss in the fix, one line from where I had made it**: `bulkTriage` was
  converted to ask `nextVulnStatuses`, and the BulkActionsBar's **enablement predicate** was left as
  `r.status === 'NEW'`. 🔑 **A surface consults a status in more than one place, and the one deciding whether a
  control is OFFERED is the easy one to miss, because it is not the one that does the work.**
  ⚠⚠ **A token-matching instrument over a codebase that documents its deletions will read the epitaph as the
  corpse.** `lifecycleStoreContract`'s derived-key leg read raw source, and the note explaining that
  `VULN_KEY = 'grc.cyber.vuln.v1'` is deleted **contains the key** — so it demanded a store for a corpus that
  no longer exists, on a correct tree. Same family as F2-193, F2-196 and F2-202. **Strip comments in every
  scan.** ⚙ Note the error direction: comment-blind **over**-reports, so it fails loudly rather than shrinking
  a population — the opposite of F2-198's stripper bug, and the safe direction to be wrong in.
  ⚠ **F2-172(d) has a FIFTH instance and grepping for it first is now two-for-two.**
  `VulnerabilityService.create` honoured `req.getId()`, letting a client name the reference an NCA ECC reviewer
  cites — in a generator F2-48 had **already** had to fix for collisions. **Grep `req.getId() != null ?` before
  claiming any create path mints its own reference.**
  ⚙ **Two things only the browser found**: a status column with no cell renderer, printing `RISK_ACCEPTED` into
  an Arabic-primary UI (house rule 7); and a "by status" chart using raw tokens as axis labels **and** ordering
  categories by whatever the server returned first (F2-197's declaration-order-as-meaning). ⚠ Also expect one
  Vite **HMR** false alarm per slice that adds a lib export: a new `export const` read by an already-loaded page
  throws `ReferenceError: X is not defined` until a full reload. **Verify with a fresh tab before believing
  it** — this repo has a precedent of an "HMR artefact" being a real context-duplication bug, so check rather
  than assume in either direction.
  ⚠ **`cyber_lc` has zero CHECK constraints in the whole schema** — same as `audit_lc` (F2-203). All 36 stored
  statuses ARE in the machine today, so unlike `audit_lc` it is latent rather than live; **that was checked,
  not assumed**, which is the only reason it can be stated.
  ⚠⚠ **THE WIRE NOMINATES, `sys.columns` ADJUDICATES — AND A DEMO FALLBACK CORPUS IS A SECOND WIRE
  CONTRACT THAT NOTHING WAS CHECKING** (F2-211, measured 2026-08-15). `PrivacyBreachDto` declared
  `severity`/`occurredAt`/`reportedAt`/`affectedRecords`; `GET /privacy/breaches` returns the `DataBreach`
  **entity** and `privacy.data_breaches` has **25 columns, none of them** (17 live rows, dev = prod). So the
  register's Severity badge, Records count and *Occurred* date rendered an em-dash on **every** row — and the
  column headed with the **PDPL Article 20 72-hour clock** opened `if (!r.occurredAt) return '—'`, so **the
  statutory countdown had never once displayed against real data, for any tenant.** The severity filter
  compared `undefined`, so choosing any band **emptied the whole register**. 🔑 **An inert control is a
  nuisance; a control that empties a statutory register produces a false all-clear** — an empty table reads
  as "there are none of those", not as "this is broken".
  ⚠⚠ **"Absent from the wire" CANNOT distinguish "no column" from "column exists, every row null", and the
  two want OPPOSITE fixes.** Under `default-property-inclusion: non_null` they are byte-identical. Measuring
  from the wire named **ten** dead fields across the four privacy DTOs; the entity says **four**.
  `sdaiaSubmissionRef`/`sdaiaSubmittedAt` are real columns awaiting a first SDAIA submission, and **all six**
  suspect `DpiaDto` fields (`processing_activity_id`, `dpo_id`, `assessor_id`, `approved_at`, `rejected_at`,
  `rejection_reason`) exist and are simply null on all 6 rows — **6 of 6 refuted**, worse than the ~2-in-10
  rate F2-188/F2-192/F2-204 each measured, because the instrument could not see the distinction. **Reflection
  over the entity is the adjudicator** (`PrivacyWireContractTest`, which generalises and **replaces**
  `RopaWireContractTest` — two instruments answering one question is this programme's own recurring defect).
  ⚠⚠ **`export const BREACHES: PrivacyBreachDto[] = demoRows([…])` ENFORCES NOTHING.** `demoRows<T>(rows:
  T[]): T[]` is generic, so TS infers `T` from the literal and then checks **assignability**, where extra
  properties are legal — the excess-property check never runs. The corpus set all four dead fields with
  `occurredAt` minted from `Date.now()`, so those columns and the countdown rendered **only in fallback mode**
  and rendered plausibly (F2-194: a clock-anchored fabrication has no tell). **This is the mechanism under
  F2-158's "hit the endpoint directly before believing the page."** Not theoretical: F2-210 renamed
  `retentionPeriod` → `retentionPeriodMonths` one slice earlier and the ROPA corpus kept setting the retired
  key **through a full clean typecheck**. `demoCorpusWireContract.test.ts` derives its population from the
  tree (50 typed corpora, 49 resolved) and holds a hard zero.
  🔑 **Neither gate is sufficient and the composition is the contract: corpus ⊆ DTO ⊆ entity.** The corpus
  gate catches a corpus that DRIFTED from its type — it would **not** have caught this, because the DTO
  declared all four too.
  ⚠ **Scope such a leg to the two holes the type system leaves, not to the field names.** A first draft
  banned `\.severity` on the breach surfaces and fired at once on `advFilters.severity`, the **filter-state
  key**. Writing it properly showed the useful thing: once the DTO stops declaring them, **`tsc` already
  refuses `r.severity`**. Exactly two shapes escape it and both produced this defect — a **widening cast**
  (`item as PrivacyBreachDto & { reportedToAuthority?: boolean }`, how a badge read a key the wire has never
  carried) and a **corpus key**.
  ⚠⚠ **THE HEREDOC LANDMINE FIRED TWICE IN ONE SLICE AND A SELF-TEST IS THE ONLY REASON IT WAS CAUGHT.**
  This file already says to write scan scripts with a file write, never a heredoc. Once it was a loud
  `SyntaxError`; the second time `\\b` in a `cat >>`-written gate collapsed to `\b`, which **inside a
  template literal is a backspace (0x08)** — so the leg reported *"no surface reads a dead field"* over a
  tree full of them. 🔑 **A detector that cannot match reads exactly like a clean tree, and it
  UNDER-reports.** Every detector was then re-run against `git show master:` — **22/22 fire, 0 silent**.
  ⚠ **F2-209's own headline, one component up, missed by the slice that wrote it**: `PrivacyPage` renders the
  corrected `PdplBreachConsole` and the register table **immediately below it** did none of what F2-209 had
  just fixed. Three predicates are now shared by all three surfaces (`breachAwarenessAt`,
  `breachSeverityInputs`, `isSdaiaSubmitted`). ⚙ And a `??` chain can be **right by accident**:
  `b.reportedAt ?? b.occurredAt ?? b.discoveredAt` always resolved to the third because the first two have no
  column — a correct outcome via two dead operands, reading as a preference the schema cannot express.
  ⚠⚠ **A SERVICE THAT PERSISTS THE REQUEST OBJECT HAS NO WRITTEN-FIELD LIST TO CHECK — ITS WRITTEN SET IS
  EVERY PROPERTY THE ENTITY HAS** (F2-213, measured 2026-08-15). Nine services did `repo.save(req)` after
  copying back only id / organisationId / externalId / createdAt, so every other column became whatever a
  freshly-deserialised entity holds. Probed live against a **planted** `[REVIEW-TEST]` row (F2-206's rule):
  editing only the **title** of a cyber incident nulled `incident_number`, `affected_asset_name`,
  `detected_at`, `reported_at` and `resolved_at`, reset both link counts to 0, and flipped
  **`notification_sent` TRUE → false** — the flag recording that an authority had been told. Populated on
  **24/24, 17, 25, 24, 10 and 19 of 24** live rows, dev = prod.
  🔑 **A RESET IS WORSE THAN A NULL, and this family produces resets**: `notificationSent = false`,
  `overdue = false`, `reopenedCount = 0`, `evidenceCount = 0` are **field initialisers**, so a cleared
  SLA-breach flag reads as a fact rather than as missing data. F2-212 was the *mitigated* version of this —
  there the service at least named the four fields it wrote.
  ⚠⚠ **MY PYTHON LOCATOR FOUND 5 SITES; THE JAVA GATE FOUND 9.** The four it missed use
  `findByIdAndOrganisationId(`, which its `findById(` pattern could not match — so they read as **absent**
  rather than **unresolved**, hiding 44% of the population from the instrument I was about to trust
  (F2-206's rule at full strength). **Write the sweep as the gate, over the same tree, in the same language.**
  ⚠⚠ **The discriminator that separates this defect from a legitimate create is `setId`.** A create-or-update
  method contains BOTH branches in one body, so "saves the request" and "has a loaded same-type local" are
  both true of perfectly correct code — 4 sites flagged, **all 4 refuted by reading**
  (`DetectionRuleService#save`, `ThreatIntelService#save`, `AppsecFindingService#ingest`,
  `LicensingService#upsertInternal`). `req.setId(null)` means *this row is new*; `req.setId(existing.getId())`
  means *aim this payload at that row*, which is the replace. ⚠ And the FIRST draft counted the save's **own
  result** (`CyberIncident saved = repo.save(inc)`) as evidence a row had been loaded, reporting **41** sites,
  nearly all creates — the Nth instance here of a detector satisfied by something other than its claim.
  ⚠⚠ **THE CODEBASE ALREADY KNEW, AT ONE SITE, FOR TWO FIELDS.** `CyberEvidenceService` carried `staleSince`
  and `reRequestId` across by hand under a CY-J comment calling them *"sweep-managed, never client-editable"* —
  the correct rule — and left `assessmentId` exposed beside them. The premise the comment gave was also false:
  the frontend does **not** *"round-trip the whole entity"*, it sends `CyberEvidenceRequest`, eleven fields of
  nineteen. 🔑 **Naming two fields to rescue is what a reader does when the editable list is implicit** — the
  fix is to name the editable set, so everything else is safe by construction and a gate can check it. Same
  shape as F2-195's `|| N`, where the correct rule sat in prose three lines from where it was not applied.
  ⚠ **The one that is LATENT is the statutory one, and that is why it survived**: `PrivacyService.updateDsr`
  wrote `assignedTo` and `completedAt` from a request that declares neither, so a PDPL Article 23 request's
  owner and its **completion timestamp — the evidence the statutory response was made in time** — were nulled
  on every edit. Both columns are null on all 20 rows in both databases. 🔑 **A defect with nothing to damage
  yet is not a smaller defect; it is one nobody can notice until the feature is used.**
  ⚠ **Refuted by reading, 3 of 12, and the loudest first.** `UserService`'s `passwordHash` sits inside
  `if (req.password() != null && !req.password().isBlank())` — my guard detector missed it because the guard
  tests `password()` while the setter is `setPasswordHash`. `/api/v1/scm/vendors` was a **guessed pairing**
  (it goes to `ScmService` with a real DTO, not `TprmService`). `CyberControlRequest` declares all thirteen
  editable columns, so that site lost nothing — **converted anyway**, so the hazard is absent rather than
  latent and the family reads as one shape.
  ⚙ **`FullReplaceUpdateContractTest`** (hermetic; reads `api.ts` from disk as `ConnectorBackingContractTest`
  does) holds: a **structural hard zero** — no method may load a `T` and then persist its own `T` parameter;
  a **coverage hard zero** — every field an update writes from its request must be declarable by the frontend
  type feeding it; the reverse direction as an exactly-asserted map; two positive controls carrying the literal
  pre-fix and post-fix shapes; and the entity-bound PUT population (**83**, F2-22's) asserted decrease-only.
  ⚠⚠ **The mutation harness found my own gate was ONE-DIRECTIONAL over a TWO-DIRECTIONAL contract**: the
  coverage leg is a SUBSET test, so deleting a write from an `applyEditable` shrinks the written set, stays a
  valid subset, and leaves the gate green **while a form input silently becomes decorative**. The new reverse
  leg then refuted two of my own claims on its first run — a predicted `regulatory-changes :: status` entry
  that cannot exist (the request type never declared `status` **at all**, which is exactly why the server
  writing it was the defect), and `UserService`, whose `password → passwordHash` transform made the leg report
  a false positive on correct code. 🔑 **A gate that fails on correct code is worse than no gate: it trains the
  next reader to edit the assertion rather than the code** — scoped out with the reason recorded, never
  papered over with an exemption.
  ⚠ **An anti-vacuity floor elsewhere failed and was RIGHT TO ASK.** `ClientSuppliedReferenceRatchetTest`
  counts reference-setter sites with a floor of 40 and found **39**: exactly the nine
  `req.setExternalId(existing.getExternalId())` lines this slice deleted. 🔑 **Those lines existed only to
  carry the reference ACROSS a full replace — compensation for the defect, not reference writes.** Floor
  re-measured to 33 with that reason recorded, because **a population that falls after a fix is not
  automatically rot, but it is always worth asking which.**
  ⚙ **Filed:** TPRM vendor contact name/email/contract-start and a user's job title are now preserved but
  still **not editable** (no form input exists for any of them — offering them is a product change);
  `PrivacyDsrRequest` declares `description`, matching no column (F2-211's shape on a fifth register — **CLOSED
  by F2-214**); and the **83 entity-bound PUT handlers** remain the structural root — a request DTO per endpoint
  would make this whole class unrepresentable, and is bigger than one slice.
- ⚠⚠ **A REQUEST TYPE HAS NO WIRE TO BE NOMINATED FROM, SO THE WHOLE WRITE VOCABULARY SAT OUTSIDE F2-211'S
  INSTRUMENT BY CONSTRUCTION** (F2-214, measured 2026-08-15). *The wire nominates, `sys.columns` adjudicates*
  only ever nominated from the **read** wire — nothing returns a `*Request`. `PrivacyBreachRequest` sits **eight
  lines below** the `PrivacyBreachDto` F2-211 corrected, in the same file, and still declared `severity` and
  `affectedRecords` (two of the exact four F2-211 retired) plus `description` for `descriptionEn`,
  `reportedToAuthority` for nothing and `titleAr` for a column that does not exist. 🔑 **Third instance in this
  lineage of the correct artefact sitting beside the wrong one** (F2-209's reader 200 lines below, F2-213's
  `CyberEvidenceService`). ⚠ **The live consequence was a 400, not a silent loss**: `POST /privacy/breaches`
  binds the **entity** and Spring Boot disables `FAIL_ON_UNKNOWN_PROPERTIES`, so the four unknown keys were
  discarded in silence and the one field the wizard never sent — NOT NULL `breach_type` — produced *"A required
  field was not supplied"*, naming nothing, on a form where nothing looks missing. **The PDPL Article 20 breach
  wizard had never once created a record**; all 17 rows in dev and prod are seeded. F2-210's ROPA defect one
  endpoint over — and **F2-209 and F2-211 both worked on this register without probing its create.**
  ⚠⚠ **A `Partial<T>` CLIENT AGAINST AN UNCONDITIONAL SERVER IS A CONTRADICTION, AND IT DEFEATS F2-213'S
  COVERAGE LEG BY CONSTRUCTION.** That leg asks whether a written field is *declarable*; `Partial<T>` makes
  every field declarable and none mandatory, so it is green exactly where the client is typed to send subsets.
  Three such endpoints wrote everything unconditionally — `DpiaService#update` would erase a PDPL Article 25
  assessment's titles, trigger, residual-risk rating and dates (5/5, 5/5, 5/5, 4/5 live rows, dev = prod).
  🔑 **All five clients are dark, and `apiClientMethodReachability` already records them — *"HOLE: a breach can
  be created, contained and closed, never corrected"*. That entry adjudicates the CLIENT and says nothing about
  the ENDPOINT: it reads as an invitation to build the edit screen that would fire the erasure.** The inverse of
  F2-201, where a clientless server side had been *hardened* first and was safe to wire.
  ⚠⚠ **MY PYTHON LOCATOR FOUND 3; THE JAVA GATE FOUND 11** — F2-213's lesson one slice later, for a new reason:
  my scan reported only sites where its own resolver found unguarded writes, so the **8 already-guarded** ones
  read as *absent* rather than *classified*. **Write the sweep as the gate, and assert the intersection
  exactly.** ⚠ And the guard detector is the load-bearing part, because F2-182's rule lands here:
  `PolicyManagementService#update` reads as twenty unconditional writes and is twenty **null-guarded** ones — a
  correct partial update. A gate built on the naive scan would have failed on correct code, **the one thing a
  gate must never do**. Its positive control carries the two shapes that fooled the first draft: a
  **block-bodied** guard and an **`isX()`** boolean accessor.
  ⚠ **Move a DERIVED field inside the guard with its source**: `recipientCount` recomputed from an absent
  `contactsCsv` is not a null but a **0**, and a zero recipient count reads as *"this audience has nobody in
  it"* rather than as missing data — F2-213's reset-is-worse-than-a-null, one layer out.
  ⚠ **Two more live instances of the same discarded key, both governance records.** `DsrFulfilmentWizard`
  composed its identity-verification evidence into a `description` key `DataSubjectRequest` has no property for
  (**fourteen fields, none free text**), under a comment claiming it *"captures the wizard context without
  needing a dedicated subschema"* — F2-184's shape; and `PrivacyPage` offered a **Description textarea that
  stored nothing**. Where no column exists the answer is disclose, not re-home and not silently drop (F2-209's
  third state). ⚠ The wizard also cited **PDPL Article 23** for the 72-hour breach window in two places;
  Article 23 is the DSR article and `lib/moduleAnchors.ts` records **Article 20** — F2-207's shape.
  ⚙ **Filed:** `privacy.data_breaches.breach_type` is free text with **no CHECK**, 12 values over 17 rows
  including both `UNAUTHORISED_ACCESS` and `UNAUTHORIZED_ACCESS` (F2-196's shape inside one column — collapsing
  them rewrites live rows, an owner call); the breach `status` vocabulary is likewise open; the table still has
  **no `title_ar`/`description_ar`** (house rule 7); a DSR has nowhere to record identity-verification evidence;
  and the Article 20/25/31 citations for the breach lifecycle are used inconsistently across `PrivacyPage` and
  `PdplBreachConsole` — only the plainly-wrong Article 23 was corrected.
  ⚠⚠ **A WIRE-CONTRACT GATE PROVES THE TYPES AGREE AND CANNOT PROVE THE PAYLOAD BUILDER SENDS WHAT THE
  TYPE PERMITS — AND WHERE THE VERB IS A FULL-REPLACE `PUT`, THAT GAP IS SILENT DATA DESTRUCTION**
  (F2-212, measured 2026-08-15). `IncidentService.update` calls `setLessonsLearned(req.lessonsLearned())`
  — and the same for `lessonsLearnedAr`/`resolutionDate`/`timelineJson` — **unconditionally**, so a field the
  client omits arrives null and is **written** null. `incToReq`, which builds every PUT payload, carried none
  of the four: **opening any incident, changing one word and saving destroyed its post-incident record**
  (NIST 800-61r2 §3.4). Proven live against a planted row. 🔑 **Invisible from both ends is what made it
  survive** — the read type did not declare them either, so nothing rendered them before, during or after
  (F2-198's shape as the *cause* of a destructive write, not merely an unused column). Populated on 3 / 6 / 3
  of 44 rows, dev = prod. ⚠ **`WireContractTest` could not have caught it**: it checks the types stay in step,
  and a payload builder may omit a field the type happily permits. Assert the **assignment** at the call site
  (`new RegExp(f + '\\s*:\\s*i\\.')`), not the field's presence on the type.
  ⚠⚠ **The adjudicator is the type the HANDLER RETURNS, which is not always the entity.** F2-211's rule needed
  exactly one refinement to leave privacy: `/api/v1/privacy/*` and `/api/v1/cyber/vulnerabilities` return
  **entities**, `/api/v1/incidents` returns `Page<IncidentResponse>` — a response record. Adjudicating against
  `Incident` would have measured the wrong artefact and every finding on it would be noise. Declare the resolved
  return type per register; never assume the entity. ⚙ Re-measuring F2-210's filing: **3 of its 4 `CyberVulnDto`
  fields are genuinely dead** (`status`/`discoveryDate`/`targetResolutionDate` — `cyber.vulnerabilities` has 22
  columns and none of them) and **`capaId` is REFUTED** — a real column, null on all 20 rows. Same 1-in-4 rate as
  F2-211's DPIA half.
  ⚠⚠ **A REGISTER GATED ON AN INCLUSION LIST OF STATUSES HID EVERY REAL ROW AND INVENTED ONE FOR THE REST.**
  The lessons-learned register filtered `CLOSED || RECOVERED`: `RECOVERED` appears in **zero** rows and
  `RESOLVED` (**10 of 44**) was missing, while all three incidents carrying a real lesson are
  `INVESTIGATING`/`OPEN`/`OPEN` — so **0 of 3 were admitted in both databases**. F2-196's rule again. What it
  showed instead was derived from the **severity band** (F2-209's shape), cited a minted `CAPA-<1000+id>` against
  real references like `CAPA-2026-19E164FC4A7-00`, and kept operator entries in `localStorage` (F2-208's shape)
  beside a server that already stored, returned **and accepted writes for** all four fields. A lesson exists
  because somebody wrote one — **not because the record reached a state the page happens to list**.
  ⚠⚠ **REMOVING A FILTER CAN TURN A LABEL THAT ACCURATELY DESCRIBED IT INTO A FALSE CLAIM — and the slice
  that fixes the predicate is the one that breaks the label.** The badge read `{n} closed` / `{n} مغلقة`; while
  the register filtered CLOSED||RECOVERED that word was true, and after the fix it was wrong for **3 of 3** rows.
  F2-208's label-versus-predicate rule arriving from the inside, and it is **per language** — check the Arabic
  half separately (house rule 7; F2-208 found exactly this drift in the Arabic only). 🔑 **The predicate and the
  words above it are two artefacts, and only one of them is in the diff you are reading.**
  ⚠⚠ **Found in the BROWSER after every gate was green — FIFTH CONSECUTIVE SLICE — six lines below the register
  this slice had just made honest.** *"CAPA Themes (90d)"* rendered five **inline** hard-coded themes with counts
  summing to **18** against a live register of 16 rows in dev and 13 in prod; `capa.tasks` has 26 columns and
  **no theme, root-cause or incident-link column**, so a theme is not reconstructible at all (F2-209's third
  state), and the "(90d)" was a second claim with nothing behind it. 🔑 **Three de-mocking gates were blind at
  once, each for a reason already on the record**: `kpiProvenance` keys on the `<Kpi>` tag (F2-195),
  `pageDataProvenance` asks whether the *page* calls a server and this one does (F2-198), and F2-194's census
  looks for an exported `const` array while this literal is **inline in JSX**. Sixth instance of *the invariant is
  real, its POPULATION is narrower than it looks*. Replaced with the relation the platform does record — CAPAs
  whose `origin_type` is an incident, **exactly 1 in both databases**, with its real server-minted reference.
  ⚠ **A cross-module courtesy panel needs a permission check, and "you cannot see this" must not read as
  "there are none".** The replacement CAPA card calls a *different* module's register: measured identical in
  dev and prod, **11 of the 29 roles holding `INCIDENT:READ` do not hold `CAPA:READ`**, including `IR_LEAD`,
  `FORENSICS_LEAD` and `REGULATOR_VIEWER`. Put the hook in a subcomponent mounted only when entitled (no
  pointless 403) and word the no-access state so it cannot be read as a clean bill — the panel now says *"this
  is not a statement that none exist."* 🔑 **An empty panel says two things at once, and the permission
  boundary is a source of that confusion just as fabrication is.**
  ⚠ **And a gate keyed on an IDENTIFIER breaks on your own refactor**: the leg asserted `incidentCapas` and went
  red when extraction renamed the local to `items` — F2-197's rule on a gate written after quoting it. Assert the
  **shape** of the selection (`originType?.trim().toLowerCase() === 'incident'`), with a self-test that the bare
  literal comparison fails, so it survives renaming and still catches a dropped normalisation.
  ⚠ **An ANCHOR-MISS in a mutation harness is not a surviving mutant, and counting only failures conflates
  them.** Two anchors spanned lines against a **CRLF** tree (4,646 CRLF, 0 bare LF), so `\n` could never match;
  both were reported SURVIVED, and both were **KILLED** once the working copy was normalised. Make it a distinct
  outcome that exits non-zero — 🔑 **a harness that declines to run reads exactly like a gate that passed.**
  ⚠⚠ **DIFF THE CLIENT'S FIELD NAMES AGAINST THE WIRE IN BOTH DIRECTIONS — AND WHEN AN ENDPOINT BINDS
  AN ENTITY, AN UNKNOWN KEY IS A SILENT DELETION, NOT AN ERROR** (F2-210, measured 2026-08-14/15).
  `POST /api/v1/privacy/ropa` takes `@RequestBody ProcessingActivity` and Spring Boot disables
  `FAIL_ON_UNKNOWN_PROPERTIES`, so `PrivacyRopaRequest`'s `processNameEn`/`processNameAr`/
  `dataCategories`/`retentionPeriod` were discarded on every call. Probed with the **exact** payload
  `RopaWizard` sends: **HTTP 400, *"A required field was not supplied"*** — `activity_name_en` is NOT
  NULL and arrived null — while the entity's own names return **201**. 🔑 **Only `status` bound, so the
  failure looked partial, and the diagnostic names no field on a form where nothing is missing.** The
  PDPL Article 31 register's creation path had never once worked; all 11 rows in dev and prod are
  seeded. The wizard also **collected a purpose of processing and never sent it**, and composed the
  retention into prose for a key nothing binds, losing an INT column's value.
  ⚠⚠ **The read half inverts a completeness check into a false accusation.** The wire carries
  `retentionPeriodMonths` (non-null **11 of 11**); the client read `retentionPeriod` (**0 of 11**), so
  `RopaDetailPage`'s *"Completeness check (SDAIA examiner expectations)"* rendered
  `<Check yes={!!item.retentionPeriod}>Retention period set` UNCHECKED for every activity — **telling a
  DPO the statutory register is incomplete where it is complete.**
  🔑 **The DIRECTION is the new part: F2-193, F2-195 and F2-205 all erred FLATTERINGLY and the register
  said "do not assume that again". This one errs PESSIMISTICALLY** — no false assurance, but far less
  likely to be reported, because *"no data yet"* is a plausible reading of an empty column while a
  suspiciously good number invites a question.
  ⚠⚠ **F2-198 and F2-207 both CITED this wizard as evidence the real ROPA surface was complete, and
  neither probed the create.** 🔑 **"There is a wizard" is not "the wizard works"** — a capability claim
  is the least-verified thing in a filing, and this is the first instance of that rule landing on my own
  earlier reasoning rather than someone else's.
  ⚠⚠ **The general instrument was BUILT AND REJECTED, which is the reusable judgement.** Diffing every
  FE `*Dto` against its backend type by name resolved **73 of 163** interfaces, and its 96 "orphans"
  were dominated by `BaseEntity` fields (`id`, `createdAt`, `organisationId`) a per-file scan cannot
  see. F2-206's rule: **"unresolved" reads identically to "missing"**, so it would have manufactured
  findings at scale. **Ground truth is the wire** — union the keys across rows and require a non-null
  value somewhere (F2-202's `non_null` rule).
  ⚠⚠ **And measuring from the wire STILL produced 4 false positives out of 5, because I paired the
  endpoints to the DTOs by hand.** Verified against the endpoint each client actually calls,
  `KriDefinitionDto`'s three renames are refuted (the column rendering `greenMax`/`amberMax` is
  `FrameworkKriRowDto` from another endpoint; `RiskRegisterPage` explicitly maps
  `greenMax: d.greenThreshold`) and `BcmBiaDto.overallImpact` is refuted (I compared `/bcm/bia-assessments`
  while the client calls `/bcm/bia`, where it is non-null 10 of 10). 🔑 **A scan locates; THE PAIRING IS
  THE MEASUREMENT, and a guessed pairing manufactures phantom findings** — 1 of 5 survived, a worse rate
  than the ~2-in-10 F2-188/F2-192/F2-204 each measured, and the cause was entirely mine.
  ⚠ **Align a newly-enabled writer to the register's STORED vocabulary, not the form's.** The wizard
  wrote `PUBLISHED`/`REVIEWED`; the table has **no CHECK**, the tone map and completeness panel read
  `DRAFT`/`UNDER_REVIEW`/`APPROVED`/`ACTIVE`, and the stored values are **ACTIVE 10 / UNDER_REVIEW 1**.
  Enabling a create while keeping the old words would have introduced F2-196's disjoint-vocabulary
  defect on a statutory register — free to avoid here only because no row carries them yet.
  ⚙ **Filed** (first three now CLOSED — do not re-open them): `PrivacyBreachDto`'s four value-less fields
  (the breach Severity column and filter are inert) — **closed by F2-211**; `CyberVulnDto`'s *"four dead
  declarations"* — **closed by F2-212, and it was THREE: `capaId` is a real column, null on all 20 rows**;
  `IncidentDto` never declaring `lessonsLearned`/`resolutionDate`/`timelineJson` which the wire carries
  (F2-198's shape) — **closed by F2-212, where it turned out to be the read half of a destructive write**.
  Still open: the wizard having no Arabic activity-name field (house rule 7); and
  `processing_activities.status` having no CHECK — now cheap to add, since this slice fixed its only
  writer.
  ⚠⚠ **ASK WHAT AN EMITTED DOCUMENT ASSERTS THAT NO COLUMN RECORDS — PER FIELD, NOT PER FILE — AND THE
  ANSWER WAS A FABRICATED PDPL ARTICLE 25 SUBMISSION TO SDAIA** (F2-209, measured 2026-08-13/14).
  `lib/pdplBreach.ts`'s `generateSdaiaNotification` returned a complete personal-data-breach notification: the
  reporting controller's identity, the categories of personal data affected, the likely consequences for data
  subjects, the containment measures taken and the remediation measures proposed. `PdplBreachConsole` rendered
  it and `downloadPacket` wrote `JSON.stringify({ packet, breach })` — the invention and the genuine server row
  **in one file**, named from a client-minted `PDPL-N-<id>-<year>`. Measured against `sys.columns`:
  `privacy.data_breaches` has **25 columns, 17 live rows, 0 CHECKs, identical dev and prod**, and **no column
  for any of it**. `categoriesAffected` came from the **severity band**, so *"Financial / transactional data"*
  was asserted to a regulator because a row count crossed a threshold.
  🔑 **F2-199's harm ladder gains a rung: a fabricated dashboard misleads whoever looks at it; a fabricated
  export leaves the platform; a fabricated STATEMENT OF CONDUCT invites the regulator to ask for an artefact
  that may not exist.** `containmentMeasures` read *"Affected systems isolated from the network within the first
  hour of detection … Forensic image of impacted hosts captured for SDAIA inspection"* — identical for every
  breach in every tenant — and the platform's own runbook says the DPO transcribes it into the SDAIA portal **by
  hand**, so the text was authored to be copied out.
  ⚠⚠ **The controller was the software vendor, with ONE CR number, for every tenant** — `'TSS Saudi Holding'` +
  CR **`1010299292`**, the *same literal F2-199 found on the fabricated NCA ECC return*, plus an invented DPO
  (`'Mona Al-Bishi'`, `dpo@tss.sa`). Second regulator, same rule: **a tenant-specific fact appearing as a
  literal in a shipped bundle cannot be right for more than one tenant.** ⚠ That name is in **eight other
  product files** (F2-207's measured 65-name cast), which is why this slice's identity gate is scoped to the two
  files it owns — **a gate that fails on code the slice did not touch is a gate nobody can keep green.**
  ⚠⚠ **F2-199's `exportProvenance` and F2-203's refinement were both GREEN over it, and the reason is the
  reusable part: this was a HYBRID document.** `exportProvenance` asks *does the emitting page have server data
  in scope?* (yes). F2-203 sharpened it to *which of this file's data reaches what it emits?* (the same data
  does). **Both are FILE-level.** `breach.id`, `descriptionEn`, `dataSubjectsAffectedCount` and `discoveredAt`
  were real; the controller, categories, consequences, containment and proposed measures were literals. 🔑 **The
  real fields are what made the invented ones credible** — F2-203's CMA packet contradicted itself on the page
  and that is part of how it was caught; this one was seamless. The 11th instrument asks per **FIELD**:
  `ARTICLE_25_FIELDS` declares, per Article 25 field, the DTO property it reads or `null`, and
  `Article25FieldBackingContractTest` resolves every claim against the real entity by reflection (the F2-193
  pair — the FE half cannot tell a real DTO property from a plausible string).
  ⚠⚠ **THE LIVE HALF: a statutory gate read the breach LIFECYCLE status, not the column the server owns.** The
  console answered *"has SDAIA been notified?"* with `breach.status === 'NOTIFIED'` while the platform keeps
  `sdaia_notification_status` with a four-state machine (`NOT_REPORTED → IN_PREPARATION|SUBMITTED →
  ACKNOWLEDGED`, terminal, guarded) in `PrivacyService`. Probed live: **4 of 17 live breaches in BOTH databases
  are `status = NOTIFIED` with `sdaia_notification_status = NOT_REPORTED`** (ids 8, 9, 13, 16), all four flagged
  as requiring notification and **113–193 days past** a 72-hour window — and `needsSdaia` excluded exactly
  those four. 🔑 **The one screen built to surface an undischarged Article 25 clock was the screen that hid the
  four breaches furthest past it.** Tenant-wide: **10 rows require notification, have never been reported and
  are past 72h; 0 of 17 carry `sdaia_submitted_at`**, so the discharge has never once been stamped.
  🔑 **And the correct reader was 200 lines below in the SAME FILE, under a comment reading *"authoritative,
  server-backed"*.** F2-196's question with F2-205's follow-up (*which of them is the server?*), except both
  readers were in one component and the wrong one decided what the operator saw.
  ⚠⚠ **`onDispatch` was REMOVED, not repointed, and the prop's own contract stated the rule it broke.** It was
  documented as the *"Real SDAIA dispatch action"* warning *"we never want this regulatory action to 'succeed'
  without actually transitioning state"* — and the one call site passed `notifyDataOffice`, whose whole body is
  `setPdpcNotificationRequired(true)` + `setPdpcNotifiedAt(now)`. It never writes the SDAIA columns, never
  writes `sdaia_submitted_at` (which the server's own javadoc calls the discharge of the Article 31 clock), and
  emits **neither** the F7 audit row **nor** the F8 `BREACH_REPORTED_TO_SDAIA` event that `recordSdaiaSubmission`
  — in the same service — emits. Latent: **0 rows carry `pdpc_notified_at`**, so it had never been clicked.
  F2-184's shape on a prop contract. ⚠ **And it could not be repointed: nothing in this platform can dispatch to
  SDAIA** (no machine-readable API; the entity javadoc says so). 🔑 **When a control's label describes something
  the platform cannot do at all, the fix is deletion, not rewiring.**
  ⚠⚠ **F2-207's diff-the-tokens question has a THIRD answer.** Refused by a CHECK → retire (F2-207); the
  server's machine merely COARSER → rewire and delete the granularity (F2-208); **no column at all → the fields
  cannot be reconstructed**, which is why F2-198 *deleted* `lastReviewedAt` rather than inventing it.
  ⚠ **Deleting the section would have ASSERTED READINESS** (F2-197 on a statutory filing), so it was replaced.
  Three states, and the last two must not look alike: a **value**; *"not yet recorded"* (a column exists and is
  empty — somebody can fill it in here); and **em-dash + "DPO supplies"** with a bilingual hint on `title` *and*
  `aria-label` (no column exists — no data entry here will ever produce it). **6 held / 6 not held.**
  ⚠⚠ **MY FIRST FIX HAD THE SAME DEFECT ONE NOTCH MILDER, and what found it was asking why an export had no
  caller.** I used `isSdaiaReported` for the urgency pool and the countdown KPI, leaving `isSdaiaSubmitted`
  unused. `IN_PREPARATION` **discharges nothing** — so the pool would have dropped a notification begun six
  months ago and never sent, and the 72-hour KPI would have gone **success green** for it. 🔑 **An exported
  predicate with no caller is a question about the predicates that DO have callers.**
  ⚠⚠ **MY OWN GATE COMMITTED F2-203'S DEFECT IN A LEG WRITTEN AFTER QUOTING IT.** The shared-predicate leg
  counted `isSdaiaReported(` with a floor of **`>= 2`**; there are three call sites, so the mutation reverting
  the panel to an inline comparison left two behind and **SURVIVED**. Replaced by a ban on the **shape** — no
  inline comparison against any of the four SDAIA tokens anywhere in the console. 🔑 **Scope an assertion to
  the shape of its claim, never to a population count.**
  ⚠ **Two more instrument errors, both caught by running things.** (a) The stripper self-test used a surviving-
  **character** ratio (`> 40%`) and failed on a correct tree: this file is legitimately **62% comment**, because
  the header explaining a retirement is longer than the code replacing it. F2-199 already recorded the right
  instrument — count declarations **anchored to line starts**, which a comment can never satisfy — and I reached
  for a ratio anyway. (b) Adding `format: 'datetime'` to two inventory entries made **both** parsers (TS and
  Java) return **10 of 12**, dropping exactly the two entries that had changed. 🔑 **A gate keyed to a literal
  entry shape reports a SMALLER population the moment a field gains a property**, and it failed loudly *only*
  because the leg asserts a minimum size — without that anti-vacuity floor it would have silently stopped
  checking the two most recently edited entries.
  ⚠⚠ **A dead-translation-key detector must match every call shape, and `t\(` matches the `t(` inside `set(`.**
  The scan reported **32** dead `pdpl*` keys; **17 were false positives built by concatenation**
  (`t('pdplSev' + severity…)`) — the tell is that the concatenation **stem** appears in the
  referenced-but-undeclared list, which is self-diagnosing once you look. It also called `pdplReportable` and
  `sdaiaDraftEn` undeclared keys; both are **object property names** in `set('pdplReportable', v)`. Honest
  answer: **15 dead removed** (exactly what the retirement orphaned), 19 added, 0 undeclared.
  ⚠⚠ **FOUND IN THE BROWSER AFTER EVERY GATE WAS GREEN — fourth consecutive slice.** The new text leg over the
  module's own keys first caught `pdplConsoleEmpty`: *"All historical incidents are closed **and notifications
  dispatched**"* — the empty state is reached by there being no OPEN breach and knows nothing about SDAIA, and
  all 17 live rows are `NOT_REPORTED`. 🔑 **An empty state may report what it measured and must not vouch for
  what it did not look at.** Then the browser found the clause citation rendering **`25(A)`** because the
  enclosing label is `text-transform: uppercase` — **a CSS rule was silently altering a regulatory reference**,
  which the Implementing Regulations cite in lower case — and the awareness timestamp printing raw
  `2026-08-14T12:21:45` beside localised dates everywhere else, on an Arabic-primary surface (house rule 7), in
  the field that starts the statutory clock. Plus a stale `PrivacyPage` comment still describing *"the
  auto-generated SDAIA notification packet"*.
  ⚠ **Authorization: two gate mechanisms with DISJOINT holder sets on one statutory control.** Reading a breach
  is `hasPermission('PRIVACY','READ')`; recording the SDAIA submission is
  `hasAnyRole('DPO','CISO','PLATFORM_ADMIN')`. Measured: `compliance` holds PRIVACY READ/WRITE/DELETE/EXPORT and
  is **403** on the record (probed); `so_officer` **is** CISO and holds **no PRIVACY grant at all**. **No
  non-admin persona can both read a breach and record its notification**, and `DPO` — the role the guard names
  first — exists in `iam.roles` with **0 active holders**.
  ⚙ **Filed:** `privacy.data_breaches` has **no `title_ar` and no `description_ar`**, so a PDPL statutory record
  cannot be described in the platform's primary language; the breach `status` vocabulary is **open** (7 stored
  values, no CHECK, no declared machine, read by two *exclusion* lists — F2-196's rule unapplied, with
  `REMEDIATED` and `INVESTIGATED` treated as open); `PrivacyBreachDto` declares `severity`/`occurredAt`/
  `reportedAt`/`affectedRecords` and the wire carries a non-null value for **none** of them, so PrivacyPage's
  breach **Severity column renders `—` for every row and its severity filter can never match**; and the
  remaining minter population is **49 prefix sites in 24 files, 41 with browser entropy** — of which the sharp
  bucket is **authority-owned** references (`NCA-ACK-`, `NCA-AD-`, `SAMA-CTI-`, `NCEMA-`, `SAMA-OPRES-`).
  🔑 **A reference only an outside authority can issue is not one the server may mint either — it is received
  and recorded, or it is absent.**
  ⚠⚠ **A BROWSER STORE MERGED WITH A REAL REGISTER IS NOT A SHADOW, IT IS CAMOUFLAGE — AND THE
  RELOAD TEST RECRUITS THE USER INTO CONFIRMING IT** (F2-208, measured 2026-08-13). `useCapas()`
  merged three sources into one list: `GET /api/capa/tasks` (**14 live rows, dev = prod**), a
  **507-line corpus of twenty invented CAPAs** with eleven invented owner mailboxes at the real
  company domain (two carrying `auditSignedOffBy: 'audit.lead@tss.sa'`), and a `localStorage`
  overlay. So the register showed **34 rows where 14 exist** with nothing on the row to tell them
  apart — and the overlay's patches were applied **on top of the server's rows**, so advancing a
  REAL CAPA to "audit signed-off" changed what the screen said about a record the server had never
  been told about, attributed to the literal `'demo.user@tss.sa'`. 🔑 **It outlived F2-201's
  fabricated approvals page for one reason: a localStorage overlay is DURABLE. F2-201 reverted on
  refresh, which is part of what gave it away; here the cheapest test a user knows becomes evidence
  FOR the defect.**
  ⚠⚠ **The tell was the vocabulary, and nothing compared the two.** The client declared **eight**
  states against `CapaTaskService.ALLOWED`'s **five**, and `fromCapaTaskDto`'s STATUS_MAP had no
  source for three of them — so `OWNER_TESTED`, `AUDIT_SIGNED_OFF` and `VERIFICATION_FAILED` were
  producible **only by a local write**. 🔑 **A state only the browser can reach is a state only the
  browser knows about**, and two of those three are the owner test and the independent sign-off an
  ISO 9001 §10.2 / IIA 2500 reviewer asks to see. `CapaMachineParityTest` now compares the FE table,
  the union and the request's `@Pattern` against the service's own map by reflection, both ways.
  ⚠⚠ **F2-207's rule has a SECOND answer, and this is it.** There, wiring was impossible because the
  client's tokens were **refused** by the column's CHECK, so the answer was retire. Here the server's
  machine is merely **COARSER** — nothing is refused — so the answer is rewire and **delete the extra
  granularity**, which is what F2-201 did when a fabricated approver chain met a server carrying one
  assignee. 🔑 **F2-199 twice more: a fabrication more granular than the platform's own model is what
  makes it look more finished.** Ask which of the two cases you are in before choosing.
  ⚠⚠ **Deleting invented figures can ASSERT HEALTH, so four digest figures were removed rather than
  zeroed.** `audCommitteeDigest` reported verification failures, recurrent CAPAs, customer
  disclosures and top root causes — all from fields with **no column on `capa.tasks` (26 columns,
  read from `sys.columns`)** — each rendered `tone={x > 0 ? 'danger' : 'success'}`. Keeping them
  would have shown an audit committee *"verification failures: 0 · recurrent: 0"* **in green**
  (F2-197). Also deleted: `RECIPIENT_FOR_KIND`, mapping each signal to an invented mailbox under the
  field name *"suggested recipient"* while nothing delivers a CAPA notification at all (F2-38d) — a
  recipient nothing sends to is a claim with no source.
  ⚠ **`lib/paTriggers.ts` was deleted BECAUSE the rewire would have made it dangerous**: it derived
  *"a KRI has breached — file a preventive action"* from `pages/risk/mockRisks` and offered a
  one-click **create**. 🔑 **A real write driven by a fabricated signal is worse than a fake write
  driven by one.** And no `create` wrapper shipped: its only caller would have been that modal
  (F2-207's *an unused client method should not ship*), so `POST /api/capa/tasks` stays clientless on
  purpose — the register is filled by the intake substrate and other modules' automation, and the
  comment at `BASELINE_CLIENTLESS_GUARDED_WRITES` now records that wiring it would be the **wrong**
  kind of progress.
  ⚠ **Three real columns the client never read** (`progress_notes_en/ar`, `evidence_refs`, populated
  on 7–10 of 14 rows) plus one the **server DTO** never declared: `created_at` is NOT NULL on all 14
  rows, so a CAPA's age was computed from `startedAt ?? targetDate` — an unstarted CAPA aged from its
  own deadline and could never be old. F2-198's `crossBorderTransfer` shape, both halves in one file.
  ⚠⚠ **F2-172(d) HAS BEEN FIXED ONE INSTANCE PER SLICE FOR SEVEN SLICES AND SIX WERE STILL OPEN.**
  `req.getId() != null ?` returned `bcm_lc/InvocationService` (a BCP invocation raised *during* a
  crisis), `board_pack/BoardPackService`, `capa/CapaTaskService`, `knowledge/KbArticleService`,
  **`nca_ecc/NcaEccFilingService`** (the NCA ECC self-assessment return) and
  `regchange/RegulatoryChangeService`. 🔑 **Six survivors after seven fixes is not bad luck, it is
  what fixing instances of a shape produces — sweep the population and gate the shape.**
  `ClientSuppliedReferenceRatchetTest` holds it at zero over the 25 `create*` reference-setter sites.
  ⚠⚠ **And the grep this file instructs you to run cannot see the whole family.** `req.getId() != null ?`
  is one SPELLING; across all **48** `setExternalId(`/`setRefNumber(` sites the caller's value also
  arrives as `req.externalId()` (a record accessor — no `get`, no null test) and `in.getExternalId()`
  (an entity bound from the body). F2-192's headline turned on CLAUDE.md's own instruction, so the
  gate is written on the **defect's signature** — a null-coalesce on a reference setter inside a
  `create*` method — not on a spelling. ⚙ All five caller-derived sites **outside** create paths were
  refuted by reading: each carries an upstream system's identifier (a SIEM event id that is the dedup
  key one line above, a Splunk alert id, a scanner finding id, a threat-intel advisory id), and they
  are named with a reason each, enforced both ways.
  ⚠ **`NcaEccFilingService`'s constant `-001` suffix is CORRECT and must not be "fixed"** — it would
  be F2-48's collision bug on any other table, and is safe only because `uk_ncaecc_year` is UNIQUE on
  `(organisation_id, fiscal_year)`. Checked against `sys.indexes`, not assumed. ⚙ All four stored
  filing references read `NCA-ECC-<year>`, which the generator does not produce — they come from
  `seed_real_data.py` and V122, so the API path was **latent** there, not exercised.
  ⚠⚠ **A `\b` WRITTEN INTO A BASH HEREDOC BECOMES A LITERAL BACKSPACE (0x08), AND THE REGEX CAN THEN
  NEVER MATCH.** The pre-fix control reported one detector SILENT; the same pattern run standalone
  found the defect at once. The bytes were `r'\x08set[A-Z]…'`. 🔑 **A regex that cannot match reads
  exactly like "the code is clean", and the escape looks correct in every source listing.** It
  **under**-reports, the dangerous direction. **Write scan scripts with a file write, never a
  heredoc** — and this is the whole argument for running every detector against `git show master:`
  before trusting it.
  ⚠⚠ **My load-bearing detector was SILENT on the defect it was written for.** Its first draft banned
  `{ ...c, status: to }`; the real code built `{ ...existingPatch, ...stampField, ...(extra ?? {}),
  status: to, history: [...] }` — three spreads between the brace and the property. 🔑 **A detector
  written for the shape you imagine rather than the shape that existed is a passing gate.** Rewritten
  as *no status may reach a React state setter* (walking each setter's brace-balanced argument) — and
  **that was still not enough**: the harness showed `setItems(… { ...c, ...body })` SURVIVING, because
  a spread carries the status without naming it. **Assert the POSITIVE**: every write to register
  state must be the server's row (`saved` / `fromCapaTaskDto`) or an explicit clear.
  ⚠ **Two of sixteen mutations "survived" and both were the harness's fault**: those legs are asserted
  by the **backend** cross-codebase gate that reads `lib/capa.ts`, and I had classified the mutations
  by the file they edit rather than by the gate that should catch them. 🔑 **A cross-codebase gate
  breaks the file-to-runner correspondence — classify a mutation by its assertion.** 16/16 after.
  ⚠⚠ **FOUND IN THE BROWSER AFTER EVERY GATE WAS GREEN, TWICE.** (a) `capaPageSub` still promised
  *"a documented root cause, verification gates, and effectiveness check"* — two of the three have no
  column. **Third consecutive slice** where the browser finds a text-level claim no code-scoped gate
  can see (F2-205, F2-207, this); the new leg over the `capa*` keys fired on **three** strings I had
  not fixed. (b) Three **Arabic** digest labels named a STATE where the predicate counts a PERIOD
  (`capaCommitteeOpened` rendered *"مفتوحة"* for `createdAt ∈ quarter`). The English carried the past
  tense and the Arabic did not, and Arabic is the primary language (house rule 7). 🔑 **F2-196's
  label-versus-predicate question applies per LANGUAGE, not per key.**
  ⚙ **Filed:** **five CAPA-ish registers, 78 rows, five disjoint status vocabularies**, identical in
  dev and prod — `capa.tasks` 14, `audit_lc.capa_tasks` 12, `bcm.corrective_actions` 10,
  `ndi.corrective_action` 17, `sust.corrective_action` 25 (F2-196's shape at five tables for one
  concept; F2-203 already filed the `audit_lc` half). The whole **`capa` schema has ZERO CHECK
  constraints**, so `status` is narrowed only by the DTO's `@Pattern` and `severity` by nothing.
  `/capa`'s primary button opens the intake form for a control observation, which lands in a
  **different** register — the label now says what it does, but this register has no create path from
  any screen. And PA suggestions over the **real** KRI register, the capability the deleted trigger
  engine faked.
  ⚠⚠ **THREE PDPL STATUTORY REGISTERS WERE KEPT IN `localStorage` AND MUTATED BY A ROUTED PAGE, BESIDE
  THE REAL ONES** (F2-207, measured 2026-08-13). `/privacy-lifecycle` had five tabs; three were
  `SEED_ROPA` (Art. 31 processing activities), `SEED_DPIA` (Art. 25 impact assessments) and `SEED_CBT`
  (Art. 29 cross-border transfers) under `grc.privacy.ropa.v1` / `.dpia.v1` / `.cbt.v1`. The page did not
  merely render them — approving a ROPA, signing off a DPIA and approving a transfer of personal data out
  of the Kingdom each wrote to one operator's browser and nowhere else. All three real registers are
  populated (`privacy.processing_activities` **11**, `privacy.dpia` **6**, `privacy.cross_border_transfers`
  **9**, probed live as `compliance`) and **already had a complete server-backed surface**, so this is a
  RETIRE under ADR-017 §D-5 — a second real client for one statutory object is F2-196's own defect.
  `lib/privacyLifecycle.ts` is **deleted** (511 lines); `privacyRegisterRetirement.test.ts` and
  `LifecycleStoreBackingContractTest` refuse its re-creation from both sides.
  ⚠⚠ **Wiring it was impossible in principle, and that is the reusable test for this shape.** `CbtStatus`
  declared DRAFT/ASSESSING/APPROVED/BLOCKED/PERIODIC_REVIEW against `CK_cbt_status`'s
  REQUESTED/ASSESSED/APPROVED/REJECTED/EXPIRED, and `SafeguardMechanism` fared the same against
  `CK_cbt_mechanism` — **4 of 5 refused in each, and three of those were the same concept differently
  spelled** (`STANDARD_CLAUSES`/`SCC`, `BINDING_CORPORATE_RULES`/`BCR`, `NONE_NEEDED`/`ADEQUACY`), which is
  F2-180's *"being right about what a state means is not evidence about what the column stores it in."* A
  naive rewire converts today's silent local writes into constraint violations (F2-183). **Before wiring a
  browser corpus, diff its vocabulary against the target column's CHECK — if the tokens do not match, the
  answer is probably retire, not rewire.**
  ⚠⚠ **And the handlers passed INVENTED OFFICERS as the author of statutory decisions** — `'Layla
  Al-Ghamdi (DPO)'` as the Data Protection Officer signing an Article 25 assessment and the literal
  `'DPO + CRO'` as the ROPA approver. `dpoSignedBy`/`approvedBy` are persisted columns, so a rewire would
  have recorded a person who does not exist as having signed a PDPL assessment — the trap F2-204 caught one
  commit before shipping.
  ⚠ **The four retired KPI tiles are F2-198's shape and F2-195's gate could not see them**: a CORRECT
  predicate over an INVENTED corpus, so the value came from a function rather than a literal. They were
  **deleted rather than rewired** because each one's real equivalent was *verified to exist* on the surface
  the page now points at (`/privacy?tab=ropa` renders the register count and the 5-of-11 cross-border count;
  `/data-lifecycle?tab=cbt` renders four real status tiles). 🔑 **Retiring a tile is only safe once you have
  checked the signal survives somewhere real** — and three of these also LINKED to a different register than
  they counted, F2-197's rule.
  ⚠⚠ **A `?tab=` LINK CAN LAND ON A PAGE THAT IGNORES `?tab`, AND IT READS AS THE READER'S OWN MISTAKE.**
  Three KPI tiles here linked `to="?tab=dsr"` while the page held its tab in `useState` and never read the
  query string, so each navigated, re-mounted on the default tab, and looked like a control that does
  nothing. F2-197's rule one step earlier: there a tile's *figure* disagreed with the list it opened, here
  the *navigation* is the false claim. ⚠⚠ **My first detector reported a population of 1 and was wrong** —
  it matched only RELATIVE (`to="?tab=x"`) links, the shape I had just found. Resolving every link to its
  **target page** gives **5 across 3 pages of 22 total**: `AuditWorkspacePage` links its own absolute path,
  and `SocOpsEventsPage` links `/cyber?tab=incidents` to a `CyberPage` that also ignored it. 🔑 **A detector
  scoped to the shape that produced the finding returns a SMALLER population, which reads exactly like "the
  rest are fine."** `tabLinkTargets.test.ts` holds it at a hard zero with no exemptions. ⚙ Two sub-rules:
  derive the tab FROM the url (`PrivacyPage`'s idiom) rather than syncing two sources of truth; and delete a
  link whose tab does not exist rather than inventing the tab — `WsTabId` never had `engagements`, and the
  list behind it is F2-203's filed corpus.
  ⚠⚠ **`<Navigate to="/x">` DOES NOT CARRY THE SEARCH STRING, so a `?tab=` link through a redirect loses
  the tab — and the gate caught this on the link THIS SLICE HAD JUST WRITTEN.** `/pdpl-extensions` is a
  redirect to `/data-lifecycle`; the link looked right only because that page's default tab happens to be
  `cbt`. 🔑 F2-202's rule one layer out: **a plausible path string is worth nothing until something resolves
  it** — fifth guessed-path error in this lineage, and the first caught by a gate written in the same commit.
  ⚠⚠ **FOUND IN THE BROWSER, AFTER EVERY GATE ASSERTION WAS GREEN: the page header still advertised the
  three registers it no longer had.** `prcEyebrow` read *"Privacy · PDPL Article 22 / 25 / 29 / 4"* and
  `prcPageSub` described *"ROPA Article 22 register, DPIA Article 25 high-risk assessment, Article 29
  cross-border transfer adequacy"*. 🔑 **F2-205's lesson, second instance: a claim about what a surface DOES
  can live anywhere a user reads text, and a gate scoped to code cannot see the place written specifically to
  be read. Retiring a tab means retiring the sentence that advertised it.** ⚠ It also carried a wrong
  citation — four places said ROPA was PDPL **"Article 22"** while every other citation in the tree says
  **Article 31**, including `lib/moduleAnchors.ts`, the regulator-citation catalogue W2-1a-i deliberately
  keeps (`PDPL / Article 31 / "Records of processing activities."`). Scope such a leg to the module's own
  keys, as F2-205 required.
  ⚠⚠ **TWO OF MY OWN GATE LEGS FIRED ON CORRECT PRE-EXISTING CODE, and measuring what they caught produced
  two populations far larger than the slice — both filed, and both are why those legs are scoped.**
  **(a) 65 distinct invented person-names across 39 product files.** The five officers on the retired tabs
  are a SHARED CAST, deepest in `lib/userDirectory.ts` (18) and `lib/crisis.ts` (16), also in `assets.ts`,
  `bcmLifecycle.ts`, `regulatoryChange.ts`, `monitoring.ts`, `approvalChains.ts`, `auditCommittee.ts`,
  `cyberLifecycle.ts`, `policyLifecycle.ts`, `auditors.ts`, `vendorPortal.ts`. ⚠ **Most are NOT named
  `mock*`**, so the population sits largely outside the de-mocking boundary F2-194 measured — the same blind
  spot one level up. **(b) 14 client-side business-reference minters in 12 files**, entropy from
  `Math.random`: `NCA-ACK-…`, `SAMA-CTI-…` (regulator-facing), `WP-…` (IIA working papers), `CAPA-…`,
  `ACC-…` (risk acceptances), `INV-…`, `IR-…`, `SOC-…`, `KRI-BR-…`. F2-172(d) has been applied one instance
  at a time (F2-202's DSR minter, F2-203's finding refs) with nobody measuring the frontend population.
  🔑 **A gate that fails on code the slice did not touch is a gate nobody can keep green — scope the
  assertion and file the measurement.**
  ⚠ **A THIRD leg I wrote, ran and DELETED, because the deletion is the finding.** It asserted *"every page
  that reads `?tab` narrows it to a declared tab"* — true, verified by reading all 12 — and flagged
  `ChangesPage`, `CompliancePage` and `EsgPage`, **3 for 3 false positives**, because each narrows it
  differently (`TAB_IDS.includes`, `isTabId(…)`, `isEsgTab(…)`, `VALID_TABS.includes`). "Is this value
  narrowed before use?" is a dataflow question a regex cannot answer (F2-182). 🔑 **A gate that fails on
  correct code is worse than no gate: it trains the next reader to edit the assertion rather than the code.**
  ⚠ **A dead-translation-key scan MUST strip comments, and for a DELETE it must over-approximate usage.**
  The page's new header comment explains the retirement by naming the four KPI keys it deleted, so a raw scan
  reported them as still in use — F2-202/F2-205's *"prose about code, stored as data, invents callers"*, the
  9th instance here. ⚙ Direction matters: comment-blind **under**-reports dead keys, so it fails safe for a
  delete. And the key regex must match QUOTED keys: `'prcCbtTrigger Review'` — declared with a literal space
  in **both** dictionaries — read as never declared, which looked like *"a button labelled with a raw key."*
  It was not. Final: **52 dead keys removed, 8 added, 0 undeclared**, and `prcKpiAvgDays` was already dead.
  ⚠⚠ **BOTH REGISTRY RATCHETS WERE ALREADY ONE ABOVE THE TRUTH BEFORE THIS SLICE TOUCHED THEM.** F2-204 set
  `BASELINE_BROWSER_LOCAL_STORES` and `BASELINE_ENDPOINT_BEARING_NOT_WIRED` to 6 correctly; F2-205 then wired
  `cyber-vulnerabilities` and **lowered neither**, so each ceiling silently tolerated a store that no longer
  existed. 🔑 **House rule 3's warning arriving from the inside: a ceiling left in place after a sweep makes
  a tolerated member indistinguishable from a reviewed one. When you change a store's state, lower the
  ceiling in the SAME commit — and re-derive the number rather than subtracting from the one written there.**
  Now **2 / 2**, with `DECLARED_CORPORA` **18 → 15** asserted exactly.
  ⚙ **Filed:** `privacy.processing_activities.transfer_mechanism` holds `DATA_OFFICE_APPROVAL` under **no
  CHECK at all** — a token `CK_cbt_mechanism` on the sibling table would refuse, i.e. F2-196's
  disjoint-vocabulary shape across two tables for one concept. And `privacy.dpia`'s one `UNDER_REVIEW` row
  still cannot be advanced by any route (F2-206's filing, re-confirmed live).
  ⚠⚠ **AN INVARIANT ENFORCED BY A SUPERCLASS COVERS EXACTLY ITS SUBCLASSES, AND A PROBE THAT EXTENDS
  THAT SUPERCLASS CAN ONLY EVER MEASURE THE COVERED HALF** (F2-206, measured 2026-08-13).
  `MassAssignmentRatchetTest` has asserted *"id must not bind — a create could retarget an existing row"*
  since F2-22a, in those words, and was **green while the defect was live on 177 of the 214 endpoints it
  counts**. It is not wrong: the mitigation is `@JsonProperty(access = READ_ONLY)` on `BaseEntity`, and the
  test proves it through **`class Probe extends BaseEntity`**. Measured by reflection: **174 of 432 entities
  extend that mapped superclass, 258 do not, and NONE of the 258 annotated its own `id`.** Its risk profile
  could not rank it either — that scan **breaks its field walk at `BaseEntity`** (correctly, those fields are
  READ_ONLY) and `id` is in none of its TENANT/AUTHORITY/OUTCOME/STATE vocabularies, so **`id` was
  structurally unrankable by the scan built to rank this defect**. 🔑 **Before trusting a green gate, say out
  loud what its probe IS, and ask what is not that.** Fifth instance of F2-198's shape: the invariant is real,
  its POPULATION is narrower than it looks — a filename, a three-entry allow-list, a module name, a
  controller-bearing table, and here an `extends` clause.
  ⚠⚠ **The defect it hid: a create endpoint answered `201 Created` while DESTROYING the record it named.**
  `POST /api/v1/privacy/ropa` with `{"id": n}` — `CrudRepository#save` calls `merge()`, not `persist()`, when
  the id is non-null, so the "create" retargets row `n` and **every column the body omits is written NULL**.
  Probed live as `compliance`: `activity_name_ar`, `purpose_en`, `retention_period_months` and
  `data_categories_json` nulled, PDPL Article 5 lawful basis `LEGAL_OBLIGATION` → `CONSENT`, and
  `special_category_flag` cleared — **the flag that makes the activity Article 25 high-risk.** On `POST /dsrs`
  an `ERASURE` request became `ACCESS` with the data subject's email nulled. ⚠ **Cross-tenant reach was
  probed and REFUTED** (500, row untouched) — which matters because `DpiaService.create`'s own comment warns
  about `merge()` *"into a potentially cross-tenant row"*, i.e. **the comment recording the risk named the
  half that is NOT reachable**, and the half that was live is not what it warns about.
  ⚠ **Fixed with ONE Jackson introspector, not 258 annotations** — `common/json/ServerOwnedFieldsModule`
  (named `ServerOwnedIdentityModule` until F2-215) reports `READ_ONLY` for any of `BaseEntity`'s nine
  server-owned fields on any `@Entity`/`@MappedSuperclass`. **An introspector has no population
  to get wrong**: it is consulted for every property of every type Jackson binds, so a new entity is covered
  the day it is written.
  ⚠⚠ **AND IT COVERED ONE OF THE NINE FOR TWO MONTHS** (F2-215, measured 2026-08-15). `BaseEntity` declares
  **nine** `READ_ONLY` fields and its javadoc explains each — forged attribution, backdating, *"an ordinary
  update could soft-delete or resurrect a record, bypassing whatever guard the DELETE endpoint carries"*,
  optimistic locking. F2-206 widened the population correctly and fixed **`id`**; its own lesson *"one field
  per question"* was about the PROBE and silently became the scope of the FIX. 🔑 **Generalising an invariant
  one field at a time leaves the remainder looking covered, because the mechanism that covers them is visibly
  present** — a reader who finds a platform-wide introspector does not then check which fields it names.
  Fifth depth of F2-198's shape, and here the narrowing is neither a filename, an allow-list, a module name
  nor an `extends` clause but **a field-name list inside the fix itself**.
  ⚠⚠ **And the gate said so, in a comment, ONE TEST METHOD BELOW the leg that had just widened the same
  population**: *"createdBy/updatedBy/isDeleted are `BaseEntity`'s own columns; a type that does not extend it
  has no such property for a caller to set."* **False.** Of the 259 entities outside `BaseEntity`: `createdAt`
  **222**, `isDeleted` **209**, `updatedAt` 146, `createdBy` 111, `updatedBy` 104, `deletedBy` 56,
  `rowVersion` 52, `deletedAt` 50 — **960 declarations, none annotated**, on **170 of the 214 entity-bound
  write endpoints**. 🔑 **The comment names the right worry and reaches the wrong conclusion because it
  assumed rather than measured — and nothing can contradict it, since a skipped entity and a clean entity
  produce the same green.** Proved live on `POST /api/v1/controls`: `createdBy` stored verbatim, `createdAt`
  backdated to 2001 over the entity's own `now()` initialiser, and `isDeleted: true` on a **create** storing a
  row `@Where(is_deleted = 0)` hides from every query **including the service's own duplicate check** while
  the UNIQUE index still sees it — burning that control code, with the **status code as evidence** (409 raw
  index pre-fix, 400 service check post-fix).
  ⚠ **REFUTED, and it is why this is closed at the WIRE**: soft-delete through the UPDATE endpoint does not
  work — `updateControl` copies a nine-field whitelist (F2-22b's INERT verdict). Whether a bound field
  *reaches the row* depends on each service, so a list of the ones that let it through is wrong the next time
  somebody writes `repo.save(body)` — and `createControl` does exactly that, four lines after hand-scrubbing
  `setId(null)` and `setOrganisationId(org())`. **The author scrubbed the two fields with a recorded defect;
  there was no mechanism for the other eight because a superclass was supposed to own them.**
  ⚠⚠ **Before marking a field READ_ONLY, measure what fills it if the wire stops — and there are FOUR
  mechanisms, one of which reflection cannot see.** Annotation, lifecycle callback, field initialiser, and
  **`insertable = false`** (the database default). The scan reported **10 entities with a NOT NULL
  `createdAt` and nothing filling it**; all ten were `insertable = false`. 🔑 **A mechanism the instrument
  cannot see reads exactly like a missing value**, and acting on that 10 would have meant refusing a real fix
  — the F2-40 trap in reverse. Also check the writers: **0 of 378** `setCreatedAt`/`setIsDeleted`/… sites take
  a request value.
  ⚠⚠ **A probe's SHAPE is part of its population, and F2-206 recorded the observation without drawing the
  edge.** Its note says a `Short`-typed `isDeleted` made 34 entities *unprobeable*. That typing never made
  them **safe**: `{"isDeleted": true}` binds on 177 and throws on 35, **`{"isDeleted": 1}` binds on all 209**.
  Build a hostile value from the field's **declared type**, and make the builder **throw** on a type it does
  not know rather than return something harmless — a skipped field scores exactly like a clean one.
  ⚠⚠ **AN INITIALISED FIELD CANNOT BE PROBED BY PRESENCE, ONLY BY IDENTITY.** Asking *"is the field set?"*
  reported **161 false positives**, almost every `createdAt`, because those carry `= LocalDateTime.now()` and
  are non-null whether the hostile value arrived or not. **The test is "does it hold WHAT I SENT".**
  ⚠⚠ **AN ANTI-VACUITY FLOOR MUST SIT ABOVE THE LARGEST *WRONG* ANSWER, NOT ABOVE ZERO.** The floor was
  1,000; re-narrowing the leg back to `BaseEntity` subclasses leaves 174 × 8 ≈ **1,392**, so the mutation that
  recreates the original blind spot **SURVIVED**. 🔑 **A floor set to "obviously not broken" cannot see a
  population narrowed to a real subset — you have to know what the wrong answer would be.**
  ⚠ **Three `git worktree` copies live under `.claude/worktrees/`**, so a bare `find . -name X.java` resolves
  to whichever sorts first (it reported `Control extends BaseEntity` for an entity that does not) and
  `npx vitest run` from the repo root reports **182 phantom failures**. Scope filesystem sweeps to the working
  tree; reflection over `target/classes` is immune, which is why the measurements held.
  ⚙ **`organisationId` is now MEASURED AND DEFENDED, not open** (F2-216): all **202** entity-bound write
  endpoints stamp the tenant on their create path, and `TenantWriteGuardInterceptor` is wired and enabled in
  **dev and prod** with both legs. Probed live — a body naming org 2 from an org-1 caller stores org 1.
  `externalId` likewise stays per-service (F2-172(d)).
- ⚠⚠ **A COVERAGE LEG RAN OVER TWELVE HAND-LISTED SITES BESIDE A LEG, IN THE SAME CLASS, THAT MEASURES 83** (F2-278, measured 2026-08-22). `FullReplaceUpdateContractTest.SITES` is 12 entries and its own comment read *"Adding a row is how a new full-replace update gets covered"* — an instruction with no enforcement (F2-52's `AttachmentAccessPolicy` shape, F2-265's *an instruction not to copy a figure is not an enforcement*), while `theEntityBoundPutPopulationIsMeasured` counts **83 entity-bound PUT handlers**. Derived, the joinable population is **30** (an entity-bound PUT whose path also has an `api.put` with a named payload type); the other **52** have no typed frontend client, so the coverage question cannot be asked of them and they are counted rather than claimed. 🔑 **A reader who finds a hard-zero coverage leg beside a measured 83 reads the zero as covering the 83.**
  🔑🔑 **WHY IT WAS A LIST: THE LEG'S CLAIM WAS NARROWER THAN ITS DETECTOR, AND NOBODY WROTE THAT DOWN — so the list read as laziness rather than as a necessary narrowing.** Its harm sentence is *"the field arrives null on every call and is written null"*, which is **FALSE when the write is null-guarded** (an omitted field then keeps its stored value), so a derived population without a split fails on `PolicyManagementService#update` — **11 undeclarable fields, every one guarded** — i.e. a gate failing on correct code, the one failure a gate must never have (F2-214). The discriminator already existed in the same file, `unguardedWritesFromRequest` (leg 4's), and **the two legs had never met** (F2-190). Split into **RESET** (unguarded → hard zero, empty exemption map, because declaring / not writing / guarding is always available) and **FROZEN** (guarded → named map keyed `endpoint :: field`, both directions, reasons repeating where the finding is genuinely per-site).
  ⚠⚠ **THE FILED ITEM WAS REFUTED BY MEASUREMENT IN BOTH HALVES, AND THAT IS THE RESULT.** F2-277 filed *"this gate lacks F2-233's store-read taint narrowing"*. The narrowing that exists is `loadsARow`, vocabulary `get|mustGet|find*|load*|fetch*` — it cannot see `frameworks.require(...)` or `labelsFor(...)`, the shapes F2-277 hit (F2-217's *a census keyed on a list of names measures the list*, on the filing that quoted it) — and porting it in either the exact or a resolver-aware form changes **NOTHING**: 0 fields lost at any of the 12 sites, because `CyberControlService:116` writes the caller's value verbatim on the **bare-label branch F2-246 requires to stay legal**. 🔑 **The right answer to a filed change to a security gate is sometimes "measure it and do not make it" — and the measurement is what separates that from not doing the work.** Recorded in the register so the next reader does not re-open it.
  ⚠⚠ **TWO OF THE THREE NARROWINGS WERE HIDING REAL WRITES.** (a) **One helper hop where the chain is two**: `updateControl → applyEditable → applyFrameworkReference`, so resolving the population from the controllers lost `frameworkId`/`frameworkName`/`frameworkNameAr` **into the REVERSE leg**, where three real writes read as *three fields the client sends and the server ignores* — i.e. as inert form inputs. 🔑 **A hand-written population can encode a detector's limit as a naming convention — `SITES` names helper methods, and that is WHY — and then the limit is invisible, because the list looks like a choice.** (b) **The service resolved by FILENAME**: two `AuditEngagementService.java` exist (`audit_eng`, `compliance`) and the `audit_eng` twin has no `update`, so `findFirst()` picked it and the site read as **"parsed nothing"** — the quiet direction, indistinguishable from a clean site — and resolving by **import** immediately surfaced a genuine frozen field (`audit-engagements :: findingsCount`, whose *sibling register derives the same field from its linked finding ids*, F2-197's *a computed count is not a measured one* across two registers for one concept). F2-276 found this with a `LinkedHashMap` keyed on an entity's simple name; F2-222 measured **15** duplicate simple names and filed it latent because *"no key collapses two registers today"* — 🔑 **a derived population is where it stops being latent.** Ambiguity surviving both the import and the longest shared package prefix now **throws**.
  ⚠⚠ **THE LIVE HALF IS A REGRESSION F2-272 INTRODUCED ONE SLICE EARLIER, ON THE PDPL ARTICLE 31 REGISTER.** `PrivacyService#updateRopa` writes `setPurposeAr(req.getPurposeAr())` **unconditionally** and `PrivacyRopaRequest` did not declare `purposeAr` — the defect F2-214 fixed for `crossBorderTransfer` **in the same method, whose explanatory comment sits eight lines above**. 🔑🔑 **F2-272's own comment reads *"F2-214's shape, not F2-213's reset"*: true of the code it replaced and false of the code it wrote. Fixing "this field can never be changed" by making the write unconditional converts an IGNORED field into a DESTROYED one, for exactly the callers that cannot send it.** ⚠ Latent, and say so: `purpose_ar` is 0 of 11 live rows in dev, the **column is ABSENT in prod** (V388 is one of the 47 pending), and `updateRopa` has **no caller** — `apiClientMethodReachability` records it as *"HOLE: a RoPA record can be created and never corrected"*. 🔑 **That entry adjudicates the CLIENT and says nothing about the ENDPOINT, so it reads as an invitation to build the edit screen that would fire the erasure** — F2-214's own words, about this very endpoint.
  ⚠ **Two more, both found by the REVERSE direction** (the one nobody writes first, F2-199): `updateRopa` **never wrote `dataCategoriesJson`**, which its client type has always declared, so the Article 31 categories-of-personal-data field was collected by the wizard, stored on create and **discarded on every edit** at HTTP 200 (2 of 11 rows); and `ReportScheduleService#update` writes `nameAr`/`descriptionAr` **guarded** against a DTO declaring neither, so both were **frozen on 8 of 11 live rows in BOTH databases** — settable only by a seed or a direct API call, never correctable from `ReportSchedulesPage`, which *is* the screen that edits that register (F2-275's shape; F2-198's for the read half, since the handler returns the entity so the wire always carried them).
  ⚠⚠ **A FIX THAT IS CORRECT ON DISK AND NEVER COMPILED IS INDISTINGUISHABLE FROM A WRONG FIX, AND IT SURVIVES A RESTART.** The probe read **14/16** with exactly the two server-behaviour legs failing — the stale-listener signature — and reproduced identically after a **full backend restart**. Cause: `shutil.copy2` preserves mtime, so the pre-fix control's revert → compile → `shutil.move` restore left `target/classes` **NEWER** than the restored source and Maven's incremental compiler treated the stale class as up to date. Same mechanism as the recorded *"46 `NoSuchMethodError` while `test-compile` said nothing to compile"*, in the other direction. 🔑 **`javap` on the compiled method is the check that settles it** (`javap -p -c -cp target/classes <FQCN>`, then grep for the new call); the repair is `touch` + `mvn -o compile` + re-grep, and a control script must restore CONTENT with a **fresh** mtime.
  ⚠ **And a mutation killed by `javac` is not a kill.** One weakened `.hasSize(30)` to `.isLessThanOrEqualTo(30)`, which does not compile on a `ListAssert`, and the harness scored it KILLED. It was **withdrawn as invalid** rather than repaired: you can always relax an assertion, so mutating the CHECK proves nothing about the code (F2-206/F2-235). 5 valid mutations, all killed, 0 anchor-miss.
  ⚙ **Filed:** `/api/v1/bcm/bia/{} :: linkedRiskId` (declared, omitted from a seven-field whitelist, so settable on create and frozen after — 0 of 10 rows, latent); `audit-engagements :: findingsCount` and the **11 frozen policy fields** including `summaryAr` (both **owner decisions** — the second route, `/api/policy/documents/{}`, has no typed client at all, so the honest first question is whether it should); and the **52 backend-only entity-bound PUTs**, which is F2-213's structural root restated — a purpose-built request record per endpoint would make resets, refusals and mass assignment alike unrepresentable.
- ⚠⚠ **A DERIVED LABEL INHERITS ITS PARENT'S BILINGUALISM ONLY IF THE DERIVATION COPIES BOTH HALVES, AND FOUR SLICES OF THIS PROGRAMME BUILT THE DERIVATION AND COPIED ONE** (F2-277, measured 2026-08-22). Executing F2-276's filed EN_ONLY list with F2-275's rule — *group by CONCEPT, not by module* — found three of its 35 members are not house rule 7 gaps at all but **F2-245's parent-identity copies**, where a child stores a parent's id beside a copy of that parent's label. F2-272 had noticed one (`CrossBorderTransfer.ropaTitleEn`) and filed it as an incident, with the right reasoning: *adding `ropa_title_ar` would create a THIRD place one ROPA's name is spelled; derive it from the parent and both languages come free.*
  🔑🔑 **THE TWO CENSUSES OVERLAP AND NEITHER GATE CAN SEE THE OTHER.** `BilingualInvariantRatchetTest` buckets a `*En` with no `*Ar` sibling as EN_ONLY; `BilingualWriteSymmetryTest` pairs the same two spellings. **A monolingual identity copy spelled BARE — `framework_name`, `subject_name`, `signer_name`, `asset_name` — is in NEITHER bucket and outside BOTH gates**: not `xEn`, so not EN_ONLY, and no `xAr`, so not a pair. F2-198's shape after a filename, a three-entry allow-list, a module name, an `extends` clause, a field-name list inside a fix, a claim shape in a gate and a naming convention; here the narrowing is **the absence of a suffix** — and it is the **complement** of F2-276's finding, where a bare-stem pair was complete and invisible.
  ⚠ **Which half is wrong is decided by the PARENT, and by MEASUREMENT.** If the parent stores `name_en` and `name_ar`, a single bare column on the child can hold only one of them, so it is monolingual by construction. Resolved by **value agreement** against every bilingual label pair in the database, never by name (F2-252: a scan locates, THE PAIRING IS THE MEASUREMENT, and when two registers share an id space a guessed pairing **dereferences cleanly and lies**). Measured over both databases: **32 monolingual identity copies, 30 of them bare, exactly 4 with FULL agreement against a bilingual parent** — `admin.committee_meetings.committee_name_en`, `cyber.cyber_controls.framework_name`, `ethics.aml_alerts.subject_name`, `platform.signatures.signer_name`. The other 28 split into 9 whose parent (`scm.vendors`) is itself monolingual and 19 that are F2-236/F2-249's **actor attribution** family, where the column now holds a username or an office — a different question. **Classify before closing one.**
  🔑 **All four were CLOSED by an earlier slice of this programme, and every one of those slices derived the English half alone** — F2-252 built `CyberFrameworkReferenceResolver.labelFor`, F2-250 `KycSubjectDirectory`, F2-249 `TenantUserDirectory` (which it named this family's *exemplar*). F2-215's two-of-N: **a reader who finds a resolver deriving the label does not then ask which LANGUAGE it derived.** F2-252's own filing named one member; measured, it is four. ⚠ `TenantUserDirectory.labelFor`'s javadoc is the sharpest instance — it discusses the blank fallback, the difference between a reference and an attribution, and cites `platform.signatures` as the correct member, and never mentions that it reads one of the two names its parent holds. **A careful comment about a column is not a comment about its language.** ⚠ `CommitteeCalendarService` is the sharpest in code: it reaches for `committee.getNameAr()` **three lines below** copying `getNameEn()` into the name column, for the meeting *title*.
  ⚠⚠ **THE LIVE HALF: V390 MADE A DERIVED COPY NARROWER THAN THE COLUMN IT COPIES, AND THE TWO DATABASES DISAGREEING IS THE EVIDENCE.** `cyber.cyber_controls.framework_name` was NVARCHAR(100) holding a copy of `cyber_frameworks.name`, which **V390 (F2-276, one slice earlier) widened to 255** to mirror `name_ar` — so dev (V390 applied) reads `100 <- 255` and prod (V390 pending) reads `100 <- 100`, and the hazard arrives in production with the same jar that carries V390. Probed as `so_officer`: `POST /api/v1/cyber/frameworks` with a 150-character name **200**, `POST /api/v1/cyber/controls` linked to it **500**, the identical call against a short-named framework **200**. 🔑 **A width invariant scoped to the two halves of a bilingual pair WITHIN one entity cannot see a derived copy in ANOTHER TABLE — and widening the source is what makes the copy overflow.** F2-276's capacity leg is exactly that shape. ⚠ The entity was the second tell: `CyberControl.frameworkName` carried `@Column` with **no length**, so the bean assumed JPA's 255 default against a 100-character column — F2-276's declaration-versus-column gap, one table down. **After widening any column, grep for tables holding a copy of it.**
  ⚠⚠ **THE BACKFILL IS DERIVED, NOT AUTHORED, AND THAT IS WHY THIS FAMILY MAY BE BACKFILLED AT ALL.** F2-271's rule — an empty Arabic value is honest, a machine translation is a fabricated record — is about **inventing** text. Here the text already exists in the parent row the column is a copy of, and the UPDATE performs exactly the operation the writer performs: **190 of 194 linked rows** filled with nothing authored. ⚠ **Guarded on AGREEMENT**, because three of the four are point-in-time snapshots (a signature's signer name is documented as surviving later user renames), so copying today's Arabic onto a row whose English half no longer matches would record a name the row never had. Measured **0 disagreements in both databases**, so the guard is a no-op everywhere it can be measured and its branch is exercised by a deliberately diverged `[REVIEW-TEST]` row (F2-183) — which it correctly left NULL. ⚠ And V391 carries **no** check comparing an English copy against its parent: it was written and removed, because on a customer database a committee renamed after its meetings were created legitimately leaves the older snapshot, so the check would THROW and block the whole chain. **A migration may assert its own writes and never tenant data** (F2-260) — which is also why the derived-value check IS safe: the four columns are created by that file, so every non-null value it sees is one it just wrote.
  ⚠⚠ **THE MUTATION HARNESS FOUND A REAL HOLE IN MY OWN GATE AND IT IS F2-194'S SHAPE IN A LEG WRITTEN AFTER QUOTING IT.** The structural leg asserted the Arabic setter was **present** in the method — and every one of these methods writes it in **two** branches, derive-when-the-parent-resolves and clear-on-unlink, so deleting the derivation leaves the clear branch and `contains("setXAr(")` is satisfied by it. Replaced by **EQUAL COUNTS**: the two halves must be written the same number of times, which is what *"the Arabic half rides with the English one at every site"* actually means, and which survives a local being renamed (F2-197). All 4 survivors were **wrong** code, so none was recorded as equivalent (F2-206) — and one was the harness's own fault, classified against the gate whose FILE it edits rather than the gate whose ASSERTION catches it (F2-208). 13 killed / 0 survived / 0 anchor-miss after.
  ⚠ **`FullReplaceUpdateContractTest` failed CORRECTLY about the contract and NOT about its own stated harm**, and the difference matters. Its message is *"the field arrives null on every call and is written null"*; `frameworkNameAr` is derived from the resolved framework, so it never does. It fires because its taint analysis cannot tell a resolver's RESULT from its ARGUMENTS — and **F2-233 made exactly that narrowing in the sibling gate `ServerStampedFieldContractTest`** (*"the id is the caller's; the row it names is the server's"*) **while this gate never got it**. Filed rather than fixed here: changing a shared gate's taint rule inside a slice that is not about it risks weakening it. The honest alternative is to declare the Arabic half on the request type **and consult it** — declaring without consulting is an inert field telling a caller their value matters (F2-184) — so both halves now accept an ECHO and refuse a CONTRADICTION (F2-231/F2-234). ⚠ Extracting that shared refusal **flattened two remedies into one** and an existing test caught it: an id sent with a wrong label means *send the id alone*, a label differing with no id sent means *send frameworkId to change the link*, because the caller is relinking BY NAME — the one operation this pair's hazard forbids. **A shared refusal must carry the remedy, not just the reason.**
  ⚠ **Two of my own errors, and the second came AFTER correcting the first.** A unit test asserted that omitting `frameworkId` on update *unlinks* and clears both halves; F2-252 deliberately made an omitted id mean **leave the link alone** (copying the pair in `applyEditable` is what nulled `framework_id` on 75 of 75 live rows), so there is no unlink path on update at all and the leg was testing something the service is written not to do. 🔑 **I corrected the test and left the IDENTICAL wrong expectation in the live probe, where it failed again** — F2-215's two-of-N inside one slice, on my own correction. And a guessed method name (`materialise` for `CommitteeCalendarService#generateAnnualCalendar`) was caught by the new gate's own (class, method) pair, which is precisely why that pair is self-verifying.
  ⚙ **Filed:** F2-276's **9 `evidence` EN_ONLY members** stand unchanged (EN_ONLY 35 → 34 and the `BASELINE_ARABIC_NOT_GUARANTEED` sum 194 → 193, from `committee_name_en` gaining its sibling); **`FullReplaceUpdateContractTest` lacking F2-233's store-read taint narrowing**, which is worth its own slice where the risk of weakening a security gate can be measured; the **9 partial-agreement vendor copies**, blocked on F2-246/F2-247's two-vendor-register question since `scm.vendors` is itself monolingual; the **19 no-parent copies**, which are F2-236/F2-249's attribution family and not this shape; `risk.scenarios`' 11 divergent rows, reproduced independently here and still F2-245's owner decision; and `cyber.cyber_frameworks` holding **2 of 6 rows with no Arabic name**, so a control's derived Arabic label is empty for two frameworks — no backfill, because inventing Arabic governance text is fabrication.
- ⚠⚠ **A GATE'S POPULATION CAN HAVE MORE THAN ONE NARROWING, AND THE SIBLING GATE ASKING THE SAME QUESTION WAS IMMUNE TO TWO OF THEM BECAUSE IT REFLECTS** (F2-276, measured 2026-08-22). Executing F2-275's own filing and disbelieving it: that filing called `evidence.sub_controls.evidence_guidance_ar` *"an `*_ar` with no English sibling — the mirror case"*. Measured across both databases, Arabic columns with no English half of **any** spelling number **0** — the English half is `evidence_guidance`, a **bare stem**, populated on **654 of 654** rows against the Arabic half's 610. The pair is complete, the data is fine, and the defect was in the instrument.
  🔑🔑 **Both bilingual gates pair `xEn` with `xAr`, so a bare-stem pair is in NEITHER of the ratchet's buckets (ASYMMETRIC, EN_ONLY) and outside BOTH gates** — never tolerated, never exempted, never asked about. F2-198's shape after a filename, a three-entry allow-list, a module name, an `extends` clause, a field-name list inside a fix and a claim shape in a gate; here the narrowing is **a naming convention**. **12** such pairs on entities, **13** columns in the database.
  ⚠⚠ **THE SECOND NARROWING WAS FOUND BY AN ANNOTATION SWEEP PRINTING `!! 2 entity files named Vendor.java`.** `BilingualWriteSymmetryTest` keyed its field map on the entity's **simple name** in a `LinkedHashMap`, so for the 15 simple names this platform declares in two packages the second file visited **overwrote** the first — not a union, a replacement — and every pair on the losing twin left the population: **568 pairs where 585 exist**. 🔑 **`BilingualInvariantRatchetTest` cannot have that defect because it enumerates entities by REFLECTION and gets distinct `Class` objects.** This file records the rule (*measure from BYTECODE, never a source regex*, with four pattern mistakes and three phantom counts behind it) and I wrote a source-reading gate one slice earlier knowing it. F2-222 filed the duplicate-name hazard as latent because *"no key collapses two registers today"*; **a census keyed on the same name is exactly where it stops being latent**, and it fails in the quiet direction — fewer pairs reads as a cleaner tree. ⚠ Resolve writers by the **import**, not the simple name: over-approximating the ARABIC half **hides** an asymmetry, which is the whole defect the gate exists to catch.
  ⚠ **The third narrowing is honest and is a filing.** The database shows 13 bare-stem columns and the gate reports 12, because `privacy.privacy_assessments` is a `V1` table with **no `@Entity`, no repository and no controller** (F2-204's shape, 6 live rows) whose module's `package-info.java` still advertises `PrivacyAssessment` as a module type (F2-184's shape). Filed rather than mapped — `privacy.dpia` is the real assessment register, so adding a second is F2-196's defect.
  ⚠⚠ **THE LIVE HALF: THE TWO HALVES OF A BILINGUAL PAIR HAD DIFFERENT CAPACITIES, AND THE BEAN-LEVEL SYMMETRY IS WHAT CONCEALED IT.** V387/V388/V389 each assert that a **new** Arabic column mirrors its English sibling's width; that had never been applied to the pairs already there. Measured over both databases: **643 pairs, exactly 2 mismatches**, both on `cyber.cyber_frameworks` and both with the **ENGLISH** half narrower — `name` NVARCHAR(100) against `name_ar` NVARCHAR(255), and `description_en` NVARCHAR(500) against `description_ar` MAX. 🔑 **`descriptionEn` and `descriptionAr` carry the IDENTICAL annotation** — `@Column(columnDefinition = "NVARCHAR(MAX)")` — against columns of 500 and MAX: **`columnDefinition` is a CLAIM, not a constraint**, because Hibernate only emits it when generating DDL and `ddl-auto=none` means this platform never does. And `name` carried `@Column(nullable = false)` with **no length at all**, so the bean assumed JPA's 255 default against a 100-character column — a 101-character English framework name passes every validator and is refused as **error 2628**, a 500 rather than a 400 naming the field, and 2628 does not report the limit (F2-185b's family).
  ⚠ **Widen the English, never narrow the Arabic.** Widening cannot refuse a row that exists today; narrowing can, and the Arabic widths are the ones the pair's own migration chose. V390's six self-checks THROW, including one asserting the Arabic halves were **not** touched, because the mirror can be satisfied from either side and narrowing Arabic is the wrong repair. ⚠ **And the new capacity leg would NOT have caught this, which it says out loud**: the asymmetry was between the declaration and the column, and no hermetic gate can see that under `ddl-auto=none`. It closes the **inverse** hazard — declaring different capacities, where the declaration is all a reader has.
  ⚠⚠ **ALL THREE MUTATION SURVIVORS WERE EQUIVALENT ON THIS CORPUS AND EACH ONE WAS A MISSING FLOOR.** With `asymmetric = 0` and every declared capacity matching, reverting the writer narrowing, dropping the bare-stem spelling from the capacity leg and ignoring `columnDefinition` altogether each changed **nothing observable**. A survivor is a hole only if the mutated code is wrong (F2-206) — all three were wrong and undetectable, so they were closed by assertion rather than recorded as equivalent (F2-263's precedent). 🔑 **The pair floor sits at 590, between the largest WRONG answer (~580, the broken keying plus the bare-stem branch) and the truth (597)** — a floor of 500, or of 570, cannot tell the fix from its absence (F2-215). The capacity leg counts its own bare-stem and `columnDefinition` comparisons, and the import narrowing is asserted by **measurement**: at least one field on a duplicate-named entity must give a different writer answer for its two twins.
  ⚠ **Two probe corrections, both mine.** My payload sent `"code"` where the entity's field is **`frameworkCode`**; the endpoint binds the entity and Spring Boot disables `FAIL_ON_UNKNOWN_PROPERTIES`, so the key was **silently discarded** — F2-214's exact shape in my own probe, and the lookup leg then failed for a reason unrelated to its claim while the create it depended on had worked. And my first frontend check grepped **`lib/api.ts` alone**, reporting `subProcessorLegalNameAr` (a PDPL Article 30 legal name) and `purposeAr` as absent from the TypeScript wire; both modules have **their own** client. 🔑 **A detector scoped to one file reports a smaller population, and here the smaller population read as a finding rather than as a clean tree.**
  ⚙ **Filed:** the **9 `evidence` EN_ONLY members** stand, with the shape now measured — every one of the eight tables is bilingual for its **name or title** and monolingual for its **description**, and `evidence.evidence_reviews` has no Arabic column of any kind (19 rows, `notes_en` filled 10); populations are real (`controls` 174/174, `sub_controls` 572/654, `frameworks` 16/16), so it is a migration plus nine writers rather than a batch. Also `privacy.privacy_assessments` (an owner decision, since `privacy.dpia` already is the register); `cyber.cyber_frameworks` holding **2 of 6 rows with no Arabic name** and **0 of 6** with an Arabic description — house rule 7 in data, and no backfill, because inventing Arabic governance text is fabrication; and **two test-support entity enumerators for one question** (this gate's source walk and the ratchet's reflective `entities()`), F2-190's shape, where extracting one would let the source-reading gate inherit reflection's immunity.
- ⚠⚠ **AN ARABIC COLUMN THAT *EXISTS* IS NOT ONE THAT CAN BE *WRITTEN*, AND PRESERVING A FIELD IS NOT PROTECTING IT** (F2-275, measured 2026-08-22). `BilingualInvariantRatchetTest` asks whether the `*_ar` sibling is **declared** and whether it is **annotated** symmetrically — both questions about the entity. Neither can see F2-272's defect: a column fillable on create and silently unwritable afterwards, because the update whitelist named the English field and not the Arabic one, so the edit was accepted with **HTTP 200** and discarded. `BilingualWriteSymmetryTest` asks the complement — if a service writes `setXEn` on an entity, does anything write `setXAr` on that **same entity**? **568 pairs, 0 asymmetric**, hard zero, empty exemption map.
  🔑🔑 **It found a member in a module the slice was not about, and its shape is the reusable part**: F2-213 removed `CyberSubControl.descriptionAr` from an update whitelist because persisting the request object was DELETING it. That stopped the deletion and left the column **permanently uneditable** — **frozen and protected are indistinguishable from the caller's side**, and a NULL GUARD gives both properties at once. ⚠ Its comment said *"offering it means adding the input, which is filed"*, which conflates the FORM with the WIRE: the form is a product question, the server refusing the value was not.
  ⚠⚠ **AND MY CORRECTION OF THAT COMMENT WAS WRONG IN THE MOST INSTRUCTIVE WAY.** I wrote that `CyberSubControlRequest` *"does not exist — its only occurrence in the whole tree was this sentence"*, having grepped the **Java** tree alone. It exists in **TypeScript** (`tss-grc-ui-v2/src/lib/api.ts`), which is exactly what F2-213 meant — and `FullReplaceUpdateContractTest`'s reverse leg proved it by failing the build the moment the write was added (*the server may not write a column its only client cannot put on the wire*). 🔑 **"That type does not exist" is a claim about every tree, and this platform has two.** Grep both before asserting an absence.
  ⚠⚠ **A REGISTER'S ROW COUNT IS ONLY A MEASUREMENT IF NO PROBE RESIDUE IS IN IT.** My baseline read `capa.tasks` at **22** in dev against **15** in prod and I wrote *"the databases differ, so that is a measurement"* into the migration. Both figures were residue: dev carried **eight** leftover `[REVIEW-TEST]` rows from earlier slices, which this slice's own bare-tag sweep removed, leaving exactly the **14** F2-208 recorded. 🔑 **"The two databases differ" is the shape residue takes when you believe the first number.** ⚠ Prod still carries one, soft-deleted and therefore invisible to the API — reported and left, because removing a row from a customer database is not a side effect of a migration (house rule 6).
  ⚠ **Group an EN_ONLY work list by CONCEPT, not by module.** By module the biggest cluster is `evidence` (9); by concept it is *the reason a governance action did not proceed*, which is **one field on each of six registers in six modules** and reads as a single systematic omission rather than six incidental ones. 🔑 **Bilingualism added by walking a FORM rather than a SCHEMA leaves exactly the exception paths monolingual**, because the cancel path is not on the form.
  ⚠ **`mvn -o compile` did not trigger a DevTools restart** and the wait loop spun ten minutes against a listener still serving pre-migration code (F2-252's hazard, where a probe scores the fix as not working). Kill and relaunch — after confirming by `-Djava.io.tmpdir` that the process is this session's and that :8090 belongs to the prod jar (F2-216: identify a process by its ARGUMENTS). And the transition body key is `status`, resolved from each `TransitionRequest` — two guesses both 400'd, and a 400 from a wrong key reads exactly like the fix being rejected, so the corrected probe carries **no fallback** (a fallback hides which key is real).
  ⚙ **Filed:** the **35** remaining EN_ONLY fields — the largest cluster is `evidence` (9) and it is a DIFFERENT sub-shape, since `evidence.evidence_reviews` has **no Arabic column of any kind** where the other eight are bilingual for their name and monolingual for their description, and `evidence.sub_controls` carries an `evidence_guidance_ar` with **no English sibling**, the mirror case. Also `review_comments_en` / `peer_review_comments_en` (*what a reviewer said*, a different concept deliberately not mixed in — the NCA ECC one is the four-eyes peer review on a regulator filing and the only member with real data, 3 of 4 rows); the one `[REVIEW-TEST]` row in production `capa.tasks`; and whether the cyber sub-control form should collect an Arabic description.
- ⚠⚠ **A PATTERN LIST WHOSE COMMENT CLAIMS COMPLETENESS IS THE HARDEST KIND TO DOUBT, AND THIS ONE OMITTED THE TEMPLATE LITERAL** (F2-274, measured 2026-08-22). `routeReachability.test.ts`'s `LINK_KEYS` is introduced as *“Every way this codebase expresses ‘somewhere the user can go’”* and lists `to="…"`, `to={'…'}`, `to: '…'`, `href`, `route` and `navigate('…')` — **not one matches a backtick.** So `DataSharingPage` wrote `to={`/ropa`}` and F2-199's `RETIRED_MUST_STAY_UNLINKED` leg was **green over a live inbound link to a retired page** — the leg built precisely because *a redirect keeps every stale link WORKING, so nothing breaks and nobody notices*. Measured: **6 static** template destinations across **148** template links, exactly **1** at a retired redirect.
  🔑 **When a detector's comment asserts it covers every shape, that sentence is the reason nobody re-derives the population.** F2-184's shape in a detector's own documentation, F2-198's at a new depth. ⚠ Its sibling `tabLinkTargets.test.ts` (F2-207) **does** resolve template literals, so two link scanners in one directory disagreed about what a link is (F2-190) and the newer one was right — **when you find two gates answering one question, diff their patterns.**
  ⚠⚠ **THE CONTROL WAS DEAD THREE WAYS AND EACH IS A RECORDED SHAPE**: `/ropa` is a redirect rather than a page; `<Navigate>` carries **neither the search string nor `state`** (F2-207), so the code it passed was dropped; and the destination has no ROPA-code filter to receive it. **Repointing needs the surface that reads the parameter** — here `/data-lifecycle`, which reads `?tab` and `?ropaCode` and narrows the Article 29 register. A mutation pointing it at `/privacy?tab=ropa`, the page the redirect goes to, is the plausible fix and is killed.
  ⚠⚠ **AND THE COMMENT EXPLAINING THE REPOINTING KEPT THE GATE RED, BECAUSE THE SCAN READ RAW SOURCE.** F2-193's epitaph-as-corpse, and not a one-off: **this codebase's convention is to document a retirement by quoting the link it removed**, so the hazard recurs every time a page is retired. 🔑 Measured effect of stripping **today**: 398 raw destinations against 396, i.e. **2 comment-only, 1 of them a declared route** — so it is a **latent-hazard closure, not a defect discovery**, and saying which is the point.
  ⚠⚠ **THE TWO-PASS MUTATION SURVIVED TWICE, FOR TWO DIFFERENT REASONS, AND BOTH ARE GENERAL.** (a) The first attempt merely **reordered the alternation**, which is an *equivalent mutant* — within one alternation the branch order is a no-op (F2-211) — and the leg I had just written says exactly that. **Naming a trap does not exempt you from it.** (b) The genuine two-pass mutation still survived, because **the leg tested the `STRIP_COMMENTS` CONSTANT while the mutation replaced the CALL SITE**: the production path can stop using the thing under test with every assertion green. 🔑🔑 **F2-205's rule one level over — a leg that exercises a primitive says nothing about whether the code under review CALLS it.** Route the work through a named helper the self-tests exercise, **and** assert the call site structurally.
  ⚠ That structural leg's own first draft counted stripping passes **file-wide** and failed on correct code, because this suite's two-pass *negative control* looks like a production pass — F2-192's scope-a-scan-to-its-claim, on a leg written minutes after quoting it. Scope it to the helper's body.
  ⚙ **Filed:** `ROPA-nnn` is a **code space with no producer** — `privacy.processing_activities` has **no code column at all** (its only reference is a UUID `external_id`), while `ropa_code` (9 rows) and `linked_ropa_codes` on two more tables (8 and 7) carry it with **no foreign key anywhere**; a name join recovers 6 of 9. Either the register gains a code column (and `ROPA-007`'s label matches no activity, so one row cannot be back-filled) or 24 live rows move to the UUID — an **owner decision**. Also: the cross-border create form asks an operator to **type** a ROPA code, `required: true`, no picker (F2-246), and there is no correct value to type; and `RopaWorkflowsController` is a five-route family keyed on that code whose frontend client was deleted with F2-198's retirement.
- ⚠⚠ **A GATE WHOSE POPULATION IS ONE *SPELLING* OF A FACT LEAVES EVERY OTHER SPELLING FREE TO ROT, AND THE GATED COPY BEING CURRENT IS WHAT MAKES THE STALE ONES CREDIBLE** (F2-273, measured 2026-08-22). F2-266 gated `CLAUDE.md`'s migration figure by parsing exactly one sentence shape. Measured immediately before the repair: that line read `V1→V387 … 375 files` and was **current**, while the same file's **pending range** (`head **V344**, so **V345–V384 are pending** — 40 files`) and **architecture table** (`Flyway V1–V384 (OFF in dev)`) both read **V384** — behind by exactly V385/V386/V387, **the three migrations added after that leg landed**, so the drift is measurable in units of *migrations since the gate*.
  🔑 **A reader who checks the gated line, finds it right, and then reads the ungated sentence forty lines up has no signal it is three versions old.** F2-198's shape at a new depth, and F2-215's two-of-N — F2-266's own javadoc says *`CLAUDE.md`'s migration line said it was gated, and it was not*, having looked at one line. **When you gate a figure, grep the file for every other sentence stating the same fact.**
  ⚠⚠ **AND THE SHARPER HALF: THE LEG THAT DID COVER THE PENDING ROW GATED ITS *COUNT* AND NOT ITS *MEMBERSHIP*.** `flywayBaselineArithmeticHolds` asserts the “Pending the next jar” figure equals the files above the applied head, and it was correctly **43** — while the cell enumerating that set named **37**, with `V359`, `V360`, `V365`, `V380`, `V386` and `V387` appearing **nowhere in the document at all**. 🔑🔑 **A count and the enumeration it summarises are two artefacts, and gating the count makes the gap invisible precisely because the number is right** — a reader asking whether the pending set is described reads the total, finds it correct, and stops. Same relationship as F2-264's catalogue-versus-reachability and F2-251's coverage report: **the verified artefact sits on top of the unverified one and vouches for it.**
  ⚠⚠ **MY FIRST MEMBERSHIP LEG WOULD HAVE FAILED ON CORRECT CODE, AND THE CAUSE IS WORTH KNOWING FOR ANY SCAN OVER THESE DOCUMENTS: THE PROSE CONTAINS LITERAL PIPE CHARACTERS.** Vocabulary listings such as `HIGH | MEDIUM | LOW` mean the class's own `valueCell()` helper — which splits the row on `|` and takes the third field — truncates this logical cell at **15,754 of ~34,000** characters, so it reported **14** missing where **4** were. 🔑 It **over**-reports, which fails loudly rather than hiding an omission (the safe direction) — but a gate that fails on correct code is the one failure a gate must never have (F2-214). Read the whole **row** for a cell whose prose can contain the delimiter, and say out loud that the read is permissive.
  ⚠ **`mvn` run from a directory with no POM gave `BUILD FAILURE` with no `Tests run:` line FOUR TIMES in one session** (F2-255's tell). The mechanism is always the same: a compound `cd <relative path> && … && mvn`, where a failed or already-consumed `cd` leaves the shell at the repo root. **Use the absolute path in every `mvn` command** — `cd /d/AYMAN/GRC/tss-grc-backend && …`.
- ⚠⚠ **A DECREASE-ONLY CEILING SAT ON A NUMBER THAT GETS *WORSE* WHEN THE CODEBASE GETS BETTER, AND
  THE CONSTANT'S OWN JAVADOC HAD ALREADY WRITTEN THAT DOWN** (F2-271, measured 2026-08-22).
  `BilingualInvariantRatchetTest` (F2-30) is the only thing checking house rule 7. It pairs each
  `*En` entity field with its `*Ar` sibling and measures **asymmetric annotation**; where the
  sibling does not exist it does `continue;   // EN_ONLY — needs a migration, not an annotation`.
  🔑 **So the gate NAMES the bucket it skips, understands why it differs, and never counts it** —
  the narrowing is the **existence of the sibling**, F2-198's shape after a filename, an allow-list,
  a module name, a controller-bearing table, an `extends` clause, a field-name list, a claim shape
  and an access modifier. Measured: **47 EN_ONLY fields**, against 158 asymmetric pairs and 580
  pairs.
  ⚠ **EN_ONLY is the WORSE half.** An asymmetric pair lets Arabic be supplied and merely fails to
  require it; a missing column means the Arabic **cannot be recorded at all**, on a platform whose
  primary language is Arabic — and it needs a **migration**, so the annotation sweep the ratchet
  measures can never reach it. The census independently reproduced four members that five earlier
  slices had each filed by hand (F2-209 `privacy.data_breaches`, F2-245 `risk.scenarios`, F2-252
  `cyber.cyber_controls.framework_name`, F2-213 `iam.users.job_title_en`) — four confirmations of a
  number nobody had produced.
  🔑🔑 **THE INSTRUMENT FINDING: closing an EN_ONLY member RAISES the asymmetry count.** The fix is
  a **nullable** Arabic sibling (nullable because nothing can invent Arabic governance text for rows
  that predate the column — the ratchet's own reason for being a ratchet), which makes the pair
  *visible* to the pair scan. Measured on V387: **47 → 45 and 158 → 159.** So the decrease-only
  ceiling **failed on the very commit that fixed the worse half of the rule**, and its message read
  *"Add `@NotBlank` to the `*_ar` field — do not raise the baseline"* — which on a populated column
  is exactly what that same class warns makes **every existing record uneditable**. **The instrument
  told me to break editing rather than let its count move.**
  ⚠⚠ **AND IT HAD ALREADY WRITTEN THE REASON.** The constant's javadoc ends: *"an instrument that
  asks about **mapped** things cannot see **unmapped** ones, and its green reading is not evidence of
  absence … **Mapping a column made this number worse and the codebase better.**"* 🔑 **The gate
  documented why its number was the wrong number and stayed keyed on it** — F2-195's shape, the
  codebase holding the correct rule in prose beside the place it was not applied.
  ⚙ **Per F2-204, re-scoped rather than raised: the ceiling is the SUM** — *fields where Arabic is
  not guaranteed*, `asymmetric + EN_ONLY` = **204** — which cannot be gamed in either direction. A
  new English-only column **+1 fails**; a new mandatory-EN/optional-AR pair **+1 fails**; adding a
  nullable Arabic sibling is **−1 and +1, unchanged**, which is right because the defect *moved*
  rather than went away; a symmetric pair or an annotation on a column whose rows already hold
  Arabic **falls**. Both components are still printed and asserted as measurements so a reader can
  see which half moved; only the sum is a ceiling. `BASELINE_ASYMMETRIC_PAIRS` is **deleted** — its
  only remaining mention was a message telling a reader to lower it, at a stale 158 against a
  measured 159, i.e. an inert constant answering the question a reader was about to ask (F2-184).
  ⚙ **The member closed: `privacy.data_breaches` (V387).** The PDPL Article 20 personal-data-breach
  register carried `title_en` NOT NULL and **no `*_ar` column of any kind** — 17 rows in dev, 18 in
  prod (the databases differ, so that is a measurement). ⚠ **The platform has TWO breach registers
  and the one that already supports Arabic is not the statutory one**: `pages/dataOfficer/
  BreachPage.tsx` collects `titleEn` *and* `titleAr` for `data_officer.breach` — F2-196's shape with
  the statutory half the poorer, and reconciling them changes what the platform reports to SDAIA, so
  it is an owner decision. ⚠⚠ **The absence had been documented THREE times and closed none**:
  `lib/api.ts` read *"There is no `severity` and no `titleAr` column on `privacy.data_breaches` — …
  filed as a real gap against house rule 7"*, written while F2-211 and F2-214 were both working on
  this exact register. Corrected in place.
  ⚙ **V387's shape is the reusable part for this family**: both columns **NULLABLE** (a NOT NULL
  would refuse every pre-existing row), **width-mirrored** against their English siblings and
  checked against `sys.columns` (a narrower Arabic column truncates a title the English one accepts,
  and Arabic is not shorter), **no backfill** (an empty Arabic title is an honest *not recorded*; a
  machine-translated one is a fabricated governance record) and **no `@NotBlank`** (it fires on
  UPDATE too). The entity, `updateBreach`, both wire types and `PdplBreachConsole` land in the same
  commit, because a column no write path fills is an inert column (F2-198's shape).
  ⚠ **`BUILD FAILURE` with no `Tests run:` line, and it was my working directory again** — I ran
  `mvn` from `src/test/java/com/tss/grc/common`, which has no POM. F2-255 records this exact tell:
  **the absence of that line is the signal, whatever produced it**, and the surefire report on disk
  then answers an older question.
  ⚠ **An em-dash in a patch script's Python string stays literal where the file holds a real one**,
  so two anchors missed. The recorded rule — use the Write/Edit tools for content carrying escapes —
  covers `—` as much as `\b`.
  ⚙ **Filed:** the **45** remaining EN_ONLY fields as a work list with **no verdicts**, because two
  shapes live in it and want opposite treatment. Governance text house rule 7 is about
  (`CommitteeMemberTerm.independenceBasisEn`, `CommitteeActionItem.completionNotesEn`,
  `NcaEccFiling.peerReviewCommentsEn`, `DataSharingAgreement.terminationReasonEn`) against an
  internal label that may legitimately be English-only (`Permission.descriptionEn`,
  `WorkflowState.descriptionEn`) — deciding which is which is a reading job the number must not
  pre-empt.
  ⚠⚠ **AND THE RE-SCOPED CEILING WAS ONLY PROVED BY THE NEXT REAL FIX, WHICH IS WHY THE RE-SCOPING WAS NOT COSMETIC** (F2-272, measured 2026-08-22). Executing that filed 45-member list, `privacy` held five and each was READ rather than batched: four to close and one to **exclude**. The gate then printed `asymmetric = 159 (unchanged), EN_ONLY 45 → 41, sum 204 → 200` — all four English siblings are nullable, so the new pairs are **symmetric** and never enter the asymmetry bucket, and the sum fell by exactly the number of members closed. 🔑 **Had the ceiling still been on the asymmetry count it would have been unmoved by four genuine closures, which reads identically to having done nothing.**
  🔑🔑 **THE MEMBERS' SHAPE IS THE REUSABLE PART: each of the four tables was already bilingual for every OTHER text column and monolingual for exactly one** — F2-215's two-of-N **inside a single table**, four times in one module. `privacy_notices` already carried `title_ar`, `purpose_ar`, `retention_period_ar` and `body_ar` and had no Arabic for the reason a notice was archived. **Four Arabic columns are precisely what stops a reader asking about the fifth.**
  ⚠⚠ **AND ONE MEMBER OF A BILINGUAL WORK LIST BELONGED TO ANOTHER FAMILY ENTIRELY, WHERE ADDING THE COLUMN WOULD HAVE MADE THINGS WORSE.** `CrossBorderTransfer.ropaTitleEn` is a **label stored beside `ropa_code`** and written from the request — F2-245's parent-identity-copy shape, where a description travelling beside an id can describe the wrong row — and the ROPA it names already carries `activity_name_en` **and** `activity_name_ar`. So the fix is to **derive** the label from the resolved parent, which yields both languages free; adding `ropa_title_ar` would have created a **third** place one ROPA's name is spelled. 🔑 **Read every member of a work list against the OTHER families you know about before closing it the way the list is named.**
  ⚠ **A fixture that gains a new record component must be given a REAL value, not `null`.** `PdplExtensionsServiceTest` constructs `DisposalCreate` positionally; a null Arabic argument would let the assertion below it pass whether or not the writer copies the field (F2-179's rule that a fixture satisfying a condition twice cannot tell you which half is load-bearing).
  ⚠⚠ **AND THE COLUMN WOULD HAVE SHIPPED INERT ON *UPDATE*, WHICH NO GATE CAUGHT.** Both ROPA routes bind the entity, so `purpose_ar` is fillable on create — but `PrivacyService#updateRopa` copies a **seven-field whitelist** onto the loaded row (F2-213's family) and `purposeAr` was not in it, so the Arabic purpose would have been **settable once at creation and permanently UNEDITABLE thereafter** — an edit accepted with **HTTP 200** and silently discarded. ⚠⚠ **I first wrote “wiped on every subsequent edit” and the live probe refuted it: a whitelist is copied onto the row it LOADED, so an uncopied field keeps its stored value rather than being nulled** (F2-214's *field the server discards*, not F2-213's reset). 🔑 **The leg that asks whether a value SURVIVES a round-trip PUT passes on the defective tree; only the leg that asks whether a PUT can CHANGE it discriminates** — 2/7 pre-fix, 7/7 after. When the update path is a whitelist, survival is free and writability is the whole question. 🔑 **The line immediately above it is `existing.setActivityNameAr(...)`** — the whitelist already carried *an* Arabic field, so F2-215's two-of-N landed a **third** time in one slice: across four tables, inside one table, and inside a single method. **A reader who finds an Arabic setter in a whitelist does not ask which other Arabic setters are absent.** ⚠ Add a bilingual column and then grep every writer of its English sibling: create paths, whitelist updates AND version-copy paths (`supersede` here carries every Arabic field in pairs and correctly omits the archive reason).
  ⚠ `RequestSizeCapMirrorTest` (F2-185b) failed the build on the new `@Size(500)` until the cap named its destination in the same commit, and the reason that makes it safe is that **the migration asserts the two columns' widths match** — a width-mirroring migration and a cap-mirroring gate are the same claim checked from two ends, and neither is sufficient alone.
  ⚙ **Filed:** the **41** remaining EN_ONLY fields, same two shapes and still no verdicts; and `ropaTitleEn` as an F2-245 derivation rather than a column.
- ⚠⚠ **A FOUR-CLICK FLOW ENDED BY DISPLAYING A FABRICATED NATIONAL CYBERSECURITY AUTHORITY
  ACKNOWLEDGEMENT** (F2-269, measured 2026-08-22). `cyberLifecycle.transitionFiling` minted
  `NCA-ACK-<year>-<Math.random()>` on the `ACCEPTED` transition and `CyberLifecyclePage` rendered it
  as a green `success` badge, monospaced, beside the filing's status and year — reached by four
  buttons each offered by the status it applies to: `DRAFT → PEER_REVIEW → CISO_APPROVAL → SUBMITTED
  → cycMarkAccepted`. Measured: **no `nca_ack_ref` column anywhere in either database** (the only
  acknowledgement columns are `rrp.rrp_cycle.regulator_ack_reference`,
  `bcm.crisis_regulatory_notifications.acknowledgement_reference` and
  `regreporting.regreport_filing.regulator_ack_reference` — three different registers), no server
  field, and no NCA integration of any kind. **The value could only ever have been invented.**
  🔑🔑 **THIS IS THE TOP RUNG OF F2-199'S HARM LADDER.** A fabricated dashboard misleads whoever is
  looking and looking again corrects it; a fabricated export leaves the platform; **a fabricated
  ACKNOWLEDGEMENT asserts that a regulator has accepted a filing** — and a green badge carrying a
  monospaced reference is exactly what somebody would screenshot as evidence that it did.
  ⚠⚠ **F2-205 EDITED THIS FILE, RETIRED ITS FABRICATED EXPORTS, LEFT COMMENTS SAYING THE FILING
  STORE IS BROWSER-LOCAL — AND LEFT THIS.** 🔑 **A disclosure in the CODE is not a disclosure on the
  SCREEN.** The operator sees no comment; they see a green badge. F2-215's two-of-N, and F2-185b's
  *a class you have already edited reads as one you have already checked.*
  ⚙ **The fix is DELETION and F2-209 is why**: an authority's reference is *received and recorded,
  or absent* — **the server may not mint it either**, so there was nowhere to move it to. Same
  precedent as F2-209's `onDispatch`: when a control's output describes something the platform cannot
  do at all, the fix is deletion rather than rewiring. ⚠ **And deleting must not assert health**
  (F2-197), so the boundary matters: the STATUS FLOW IS KEPT — recording *"we submitted and they
  accepted"* is a legitimate operator note, and the real register is at `/nca-ecc/filings`, which the
  page already links to. What is gone is the platform inventing the regulator's number for it; where
  an advisory genuinely carried no reference, the row now shows an **em-dash with a bilingual hint**
  on `title` *and* `aria-label` (F2-209's three states).
  ⚠ **The latent half, closed anyway.** `createThreat` invented the ISSUING BODY's own advisory
  number — `NCA-AD-<year>-<random>` for NCA HASEEN, `SAMA-CTI-<year>-<random>` for SAMA ISAC, both
  filed by F2-207. Latent only because its **single caller passes `'INTERNAL'`**, which is a fact
  about today rather than a property of the control (F2-182). The reference is a caller-supplied
  parameter now and stays absent when the advisory carried none.
  ⚠⚠ **THIS GATE IS A HARD ZERO WHERE THE THREE BEFORE IT WERE CENSUSES, AND THE REASON IS THE
  TEST.** F2-266/F2-267/F2-268 all shipped counts, because a caller-supplied reference or deadline is
  often legitimate and a ban would fail on correct code (F2-214). **There is no correct reason for a
  browser to generate a Saudi regulator's own reference number**, so this one bans it — with two
  exclusions pinned rather than assumed: **a RECORDED reference is not a MINTED one** (the seed corpus
  carries real received numbers like `sourceRef: 'NCA-AD-2026-0148'`; no entropy on the line, and
  banning those would have deleted real data), and a label formatted from a server id is not a mint.
  The 26 non-authority mints ride a separate decrease-only figure with **no verdicts**, because some
  of those stores have no server side at all (F2-204) and the browser's mint is the only id the row
  will ever have.
  ⚠⚠ **MY FIRST SCAN REPORTED LINE NUMBERS FROM THE COMMENT-STRIPPED TEXT, SO EVERY ONE WAS WRONG.**
  The authority hit came back as "line 630", which in the real file is an unrelated field mapping — I
  opened it, found nothing, and only then noticed the offset. **F2-242 records the remedy and I did
  not apply it: strip comments while PRESERVING NEWLINES** (`m => m.replace(/[^\n]/g, ' ')`).
  Corrected, it reports 484 / 739 / 740, which the pre-fix control confirms against the raw file —
  🔑 **the control is what proves the stripper, not just the ban.**
  ⚠ **It also counted display LABELS as minters.** `INC-${String(i.id).padStart(4,'0')}` formats a
  server-assigned id, which is legitimate; the discriminator is whether the string incorporates a
  server id or **entropy**. Raw 38 → 31 mints → 26 after separating the authority bucket, i.e. **14
  of 38 subtracted** (9 `SHA-256` digests, 5 server-id labels), which is why the exclusion count has
  its own anti-vacuity floor.
  ⚠ **And the path resolution was invented rather than copied.** `new URL('..', import.meta.url)
  .pathname` yields `D:\src` on Windows and every walk failed with `ENOENT`; the house idiom in
  eleven sibling gates is `join(process.cwd(), 'src')`. 🔑 **A harness convention that already exists
  in the tree is not worth re-deriving** — and this is the same lesson as F2-211's *before writing a
  scanner, read the scanner this repo already has.*
  ⚙ **Filed:** the **26** non-authority browser mints, sharpest being the ones whose store has a real
  server side — `ACC-` (risk acceptances) and `KRI-BR-` in `riskLifecycle.ts`, `IR-`/`SOC-`/`PT-` in
  `cyberLifecycle.ts`, `CLS-` in `ContractClausesPage` (F2-267 named that one), and `RES-`/`ACT-` in
  `CommitteesPage`, which mint from a local `seq` counter against a server-backed committee register
  — a race as well as a provenance question.
- ⚠⚠ **F2-202'S RULE HAS TWO HALVES AND ONLY ONE WAS EVER COUNTED** (F2-268, measured
  2026-08-22). *Only the server may issue a tracking code **or a statutory deadline***. F2-266 and
  F2-267 gave the **code** half a primitive (`ReferenceCodeMint`) and a census; the **deadline**
  half had been applied one instance at a time — F2-202 itself (the PDPL Art. 4 30-day DSR
  clock, minted in the browser), F2-172 (the intake SLA), F2-196 (the sweep that excluded
  `EXTENDED`), F2-250 (`aml_alerts.sla_due_at`, filed) — **and never counted**. Measured by
  the gate: **22 create-path sites take a deadline from the request, 11 compute it server-side**
  (`target/caller-supplied-deadlines.txt`).
  ⚠ **A caller-supplied deadline is NOT automatically wrong — three kinds, one defect.**
  **(1) Policy- or statute-derived**, where the platform knows the interval and the server must own
  it (`receivedAt.plusDays(30)` for PDPL Art. 4, `detectedAt().plusHours(72)` for Art. 20,
  `now.plusHours(routing.slaHours())` for intake) — the only defect bucket. **(2) A plan the
  operator sets**, where there is no interval to derive (`ObligationService#create`'s
  `firstDueDate`) — refusing it would fail on correct code (F2-214). **(3) An outside party's
  date** (`ObService#createTpp`'s `certExpiresAt`) — F2-209's rule unchanged: received and
  recorded, or absent, and the server may not compute it either. **Classify before you touch one.**
  🔑 **THE INTERSECTION IS THE WORK LIST**: six properties the platform already computes
  somewhere and takes from a caller elsewhere — `dueDate`, `nextReviewDue`,
  `nextReviewDueDate`, `nextTestDue`, `slaDeadline`, `targetDate`. Asserted **exactly**, not as a
  ceiling, because a decrease-only count cannot express *the population was undercounted* (F2-204).
  ⚠⚠ **The sharpest member is inside ONE class, and the second is in a class I had just
  edited.** `OutsourcingArrangementService` computes `nextReviewDueDate` as `today.plusMonths(12)`
  in `recordReview` and takes it from the request in `create` **and** `update` — F2-215's
  two-of-N, on a SAMA cadence the repository really consumes (`nextReviewDueDate <= :asOf`; 14
  arrangements in both databases, **2 already past due**), with the cadence a **literal** where the
  sibling `SubprocessorService` names one (`REVIEW_CADENCE_MONTHS`) and **no `last_review_date`
  column at all**. And `SupplierResilienceTestService#create :: setNextTestDue` is caller-only while
  `DrPlanService` and `ExitPlanService` both default to `plusMonths(6)` — **the class F2-267
  edited one slice earlier to fix `testCode`.** *A class you have already edited reads as one you
  have already checked* (F2-185b), and the edit was mine.
  ⚙ **Filed rather than fixed, deliberately.** On a create there is no prior review or test for
  a cadence to anchor to, so the first date is arguably kind 2 — but `update` then lets it be
  pushed out indefinitely, which can empty the reviews-due list. **Whether an operator may exceed a
  cadence is a product decision about a customer's SAMA obligations**, and inventing an answer to
  make the register look enforced is F2-196's defect.
  ⚙ **It resolves F2-250's recorded uncertainty**: `ethics.aml_alerts.sla_due_at` was filed as
  *"the caller's by design rather than by oversight"*. Measured — **no AML SLA is declared
  anywhere** in the backend or frontend, the only statement of one is a comment in a **seed DDL**
  (`-- usually +30 days from detection`), and **nothing reads the column**: no sweep, no query, no
  export, no rendered surface, where `socops` has `SECURITY_EVENT_SLA_BREACHED` and a scheduled
  sweep over the same column name. **Inert rather than by design**, and building the SLA is a
  product decision. F2-178's rule: re-measure a recorded blocker before believing its estimate.
  ⚠⚠ **MY OWN DETECTOR WAS TOO NARROW AND THE MUST-MATCH PIN CAUGHT IT BEFORE IT SHIPPED
  AS THE CENSUS.** The first pattern required a **capitalised** suffix, so bare `targetDate`,
  `validUntil` and `nextTestDue` never matched — and `setTargetDate` exists in **7** files,
  `setExpiryDate` in **8**, `setExpiresAt` in **6**, `setValidUntil` in **3**, `setNextTestDue` in
  **3**. The throwaway Python locator used the same regex and reported **16** where the corrected
  gate measures **22**. 🔑 **An under-count reads exactly like a smaller population**, and
  the only thing that stopped it was a pin that fails on a name the pattern must match. The widening
  then exposed real false positives (`dueOffsetBusinessDays`, `evidenceExpiryWarningDays`,
  `avgTargetScore`, `expiredEvidence`, `expiringContracts`, `hasNetZeroTarget`), each now a
  must-NOT-match: **a COUNT of days is not a deadline.**
  ⚠ **The classification ORDER is load-bearing.** Test the server-computation pattern BEFORE
  the request-read one, because the house fallback shape
  (`req.x() != null ? req.x() : LocalDate.now().plusMonths(6)`) does both — and for this census
  the interesting fact is that the platform **can** compute the interval. Breaking the server
  pattern moves those sites into the caller bucket and the count goes **22 → 27**, which is how
  the census leg catches a broken classifier rather than leaving that to the pin alone.
  ⚠ **A decrease-only ceiling cannot see a detector that stopped matching** (F2-69), so the
  mutation that NARROWS the pattern is caught by the pin and the exact intersection, never by the
  count. That is the argument for three legs rather than one. ⚠ And M3 survived its first run
  because I replaced **1 of 3 alternatives** in the server pattern — every server-computed site
  also matches `plusDays`/`plusMonths`, so the mutated code was not wrong (F2-206, and the recorded
  rule to check the site count before believing a surviving mutation).
- ⚠⚠ **ASK THE UNIQUE-INDEX QUESTION FIRST: IT IS THE DIFFERENCE BETWEEN *A DUPLICATE IS
  STORABLE* AND *A CALLER PICKED THE STRING*** (F2-267, measured 2026-08-22). Executing F2-266's
  own filed list of *"sharpest unread"* caller-named references, **nine of ten already carried a
  UNIQUE index** — `subprocessorCode`, `bowtieCode`, `kriCode`, `noticeCode`,
  `arrangementCode`, `campaignCode`, `scenarioCode`, `clauseCode`, `planCode`. So a caller can
  choose a **well-formed** code and cannot create a duplicate, which is F2-259's verdict (a naming
  question) and **not** F2-253's (a defect). 🔑 **F2-253 filed `subprocessorCode` as
  F2-172(d)'s family without mentioning `UQ_subp_code`, and F2-266 carried that reading forward to
  four more members without re-checking** — the same conflation F2-264 found three slices
  carrying, one register over.
  ⚠ **The one real member was `scm.supplier_resilience_tests.test_code`**: caller-only with
  **no generator anywhere in the owning class**, `nvarchar(80)` nullable, **no unique index**, 11
  rows in both databases across two disjoint code spaces (`SRT-2026-00n`, `SRT-GOV-00n`). Its
  collector was a free-text form box placeholdered **`'SRT-2026-009'`** — the next number in
  the server's own sequence — and a **blank** input stored null, so a resilience test could
  exist with no reference at all. 🔑 **The service's own comment listed what is
  server-controlled and `setTestCode(req.testCode())` sat two lines below it**, with F2-246's
  vendor-resolver comment directly above, so the create read as audited (F2-215's two-of-N).
  ⚙ Fixed with one `ReferenceCodeMint.mint("SRT-" + year)` call, which is the test of F2-266's
  claim that the next application would be a call rather than a twelfth generator. It was.
  ⚠⚠ **F2-264'S LANDMINE RECURRED AND ITS OWN REMEDY IS WHAT I SKIPPED.** My probe omitted
  `@NotBlank scenarioDescriptionEn`, so the caller-supplied-code leg and the no-code leg both got an
  identical bean-validation 400 — and **the refusal leg scored a PASS on it**, because `@Valid`
  fires before the service so the refusal never ran. F2-264 wrote *"resolve every mandatory field
  from the DTO before reading a write probe's result"* one slice earlier. **Read the request record's
  components before writing the payload, not after the probe disagrees.**
  ⚠⚠ **A SED-DERIVED PROBE INHERITS THE ORIGINAL'S ASSUMPTIONS ABOUT A DIFFERENT TABLE.**
  Deriving this probe from F2-266's carried over a `description` column this table does not have
  — and my guard against exactly that (`assert "description" not in s`) matched
  `scenario_description_en`, **rejected the patch, and let the old file run again**, planting rows
  twice. 🔑 **A guard whose token also occurs innocently rejects the fix and leaves the
  defect** — the substring-satisfied-by-something-else shape, inside a patch guard. Write a
  fresh probe per table.
  ⚠ **And sweep by the BARE `[REVIEW-TEST]` prefix, never the probe's own suffix.** Earlier runs
  planted `[REVIEW-TEST] V386 probe` while my sweep looked for `[REVIEW-TEST] F2-267`, so three rows
  survived and the table read 14 against 11. House rule 5's tag is the bare prefix for this reason.
  ⚠ **A method-name scan across all services mis-attributes a setter to the wrong owner** —
  F2-187's recorded mistake, hit twice in one throwaway here (`scenarioCode`, `planCode`), plus an FE
  column that mixed CCM's unrelated `testCode` with this register's. **Resolve per owner type.**
  ⚙ **Filed:** `clauseCode` is a **frontend** minter (`ContractClausesPage`'s
  `nextClauseCode(category)`), so it belongs to F2-207's filed population of 14 client-side
  reference minters, not this one. `subprocessorCode`'s generator is a **check-then-insert over 899
  values** with a 50-attempt retry, so a concurrent duplicate becomes a 500 rather than corruption
  because `UQ_subp_code` backstops it — replacing it would change the format of a **public**
  PDPL transparency reference, a product decision with no defect behind it. The two `SRT-*` code
  spaces are left unreconciled: both are legal and distinct, and collapsing two seeded notations on
  a register a SAMA reviewer reads is an owner decision.
- ⚠⚠ **A GATE THAT CORRECTLY REFUSES TO *BAN* A SHAPE LEAVES IT UNCOUNTED, AND SIX SLICES THEN
  CLOSED IT ONE INSTANCE AT A TIME** (F2-266, measured 2026-08-22).
  `ClientSuppliedReferenceRatchetTest` keys on `\bset(?:ExternalId|RefNumber)\s*\(`, and the first
  framing — that this is F2-248's *a gate key is a spelling* — was **refuted by reading**. The
  class's own javadoc records the narrowing as a **decision**, with the right reason: *"Deliberately
  not `setCode` or `setSlug`: those are frequently the caller's to choose … banning them would make
  this gate fail on correct code, which trains the next reader to edit the assertion instead of the
  code."* 🔑🔑 **That is correct and it is not the whole job: a narrowing settles what may be
  BANNED and says nothing about what should be COUNTED.** Nobody counted it, so the family closed
  one instance per slice — F2-250 (`aml_alerts.alert_code`), F2-253 (`subprocessorCode`), F2-258
  (three `AiLifecycleService` creates), F2-259 (`snapshotCode`, read → safe), F2-260
  (`regulatorAckReference`, read → CORRECT), F2-264 (`CreateAssetRequest.reference`). **Six filings
  and six survivors is what a population looks like when nothing counts it** (F2-208).
  ⚠ **Measured by the gate, in Java, over the same tree: 93 create-path sites write a business
  reference from the request, across 59 distinct setter names.** My Python locator said 91 —
  **third consecutive slice in which the throwaway under-counts the shipped gate** (F2-213's rule:
  write the sweep as the gate).
  🔑 **WHY THE BAN'S ZERO READS AS COMPLETE: 118 tables declare `external_id` — server-minted and
  covered by the ban — BESIDE a caller-named `*_code`.** The covering mechanism is visibly present
  on the same row, so a reader has no reason to ask which of the row's **two** references it covers.
  F2-215's two-of-N at its most literal, and `ethics.aml_alerts` is exactly that shape.
  ⚠⚠ **THREE KINDS LIVE IN THAT POPULATION AND ONLY ONE IS A DEFECT, so the census carries NO
  VERDICTS** (F2-188 measured what happens otherwise: five claims, five false). **(1)
  Authority-issued** — `regulatorAckReference`, `samaLicenceRef`, `samaNonObjectionRef`,
  `lawEnforcementReference`, `strReference` — **correct** to be caller-supplied, because F2-209's
  rule is that a reference only an outside authority can issue is *received and recorded, or
  absent*, and **the server may not mint it either**. **(2) Names another record** —
  `linkedAssetRef`, `ropaCode`, `retentionRuleRef`, `contractRef` — F2-242/F2-252's question (does
  it resolve, and to what?), not minting. **(3) The record's own identifier** — `alertCode`,
  `subprocessorCode`, `bowtieCode`, `kriCode` — the only F2-172(d) bucket. **Classify before you
  touch one.**
  ⚠⚠ **TWO SCOPING ERRORS OF MINE, BOTH IN THE OVER-REPORTING DIRECTION.** The first pass put
  `\w*Id$` in the reference shape and swallowed every foreign key (`setVendorId`, `setRiskId`,
  `setApproverId`, `setWorkflowId`), reporting **135** — that is F2-242's family, a different
  question, and **a scan scoped wider than its claim reports a population it never measured.** And
  `ObService#recordApiCall` writes `setStatusCode` from the request, which is an **HTTP status on an
  API-call log, not a reference** — it is now the gate's must-not-match negative control, because
  that is precisely how a `*Code` pattern over-reports.
  ⚙ **The member closed end-to-end, and the check that decided it was structural.**
  `ethics.aml_alerts.alert_code` is the reference an FIU submission cites; before this it was
  caller-named with no minter anywhere and **no unique index**, so a duplicate of a real code was
  storable (F2-250 probed it). **Verified as the platform's own reference rather than an upstream AML
  engine's by the SCHEMA, not by the format**: the table has no `source_system`, no `ingested_at`,
  one caller (`AmlAlertController`, a plain `@PostMapping`, no ingest route), and it declares
  `external_id` beside `alert_code`. 🔑 **The stored `AML-2025-000n` codes are seed rows and prove
  nothing about intent** — getting this backwards would have meant minting a value an outside system
  owns, which is the distinction F2-260 established matters.
  ⚙ **`common/reference/ReferenceCodeMint` was EXTRACTED FROM `IntakeService`, which now delegates**
  — so the second register calls the primitive instead of copying it (F2-190). Entropy, not a
  sequence: a per-tenant sequence needs a read-then-write so two concurrent creates race, and F2-48
  already had to fix a collision in a generator whose suffix was effectively constant.
  ⚠ **The intake output shape is unchanged and asserted.** An intake tracking code is quoted to the
  public on the whistleblowing and breach-report portals, so a code somebody is holding has to keep
  resolving — extracting a shared primitive must not change either caller's output.
  ⚠⚠ **V385 IS A *FILTERED* UNIQUE INDEX AND THAT IS NOT A STYLE CHOICE: SQL SERVER TREATS NULLS AS
  EQUAL FOR UNIQUENESS.** `alert_code` is nullable, so a plain `UNIQUE` would permit at most **one**
  code-less alert — refusing a state the column's own nullability allows. `WHERE alert_code IS NOT
  NULL` is what makes the index a statement about codes rather than about absence. Keyed on
  `(organisation_id, alert_code)`, because a global unique index would let one tenant's reference
  block another's — a multi-tenancy defect dressed as strictness. Measured in **both** databases
  first (10 rows, 10 distinct, 0 duplicate groups), so no `WITH NOCHECK` and nothing to reconcile.
  ⚠⚠ **MY FIRST INDEX PROBE PRINTED *"duplicate REFUSED as intended"* WHEN THE INSERT HAD FAILED ON
  `description` BEING NOT NULL.** `except pyodbc.IntegrityError` cannot tell one refusal from
  another. 🔑 **A refusal thrown for the wrong reason is indistinguishable from the right one in a
  pass/fail line** (F2-261). Build the INSERT column list from `sys.columns` — every NOT NULL column
  with no default, excluding identity and `timestamp` — and **assert the error message names the
  index**. The corrected probe also proves a *fresh* code is accepted and that **two** NULL-coded
  rows are accepted, so the filter is demonstrated rather than described.
  ⚠ **And the gate would not compile because `argumentAt` ALREADY EXISTED in the class** — I
  hand-rolled a helper the file already had, which is F2-190 catching the very slice whose own
  primitive is an extraction. Check the class's existing helpers before adding one, and adapt to
  their contract (the survivor returns `""`, mine returned `null`).
  ⚠⚠ **AND THE SUITE WENT GREEN OVER A THIRD STALE COPY, WHICH IS F2-265's FINDING IN THE FILE
  THAT RECORDS F2-265.** Adding V385 means editing three artefacts — `MEASURED_BASELINE.md`,
  `baseline_census.py`'s `PUBLISHED` dict, and **this file's** migration line.
  `MeasuredBaselineFreshnessTest` failed on the first two and not the third: **no test in the tree
  reads `CLAUDE.md` at all** (every hit is a comment mentioning it), so the sentence *"`baseline_census.py`
  prints it and `MeasuredBaselineFreshnessTest` gates it"* was true of the FIGURE and false of THAT
  LINE — one paragraph after a warning that this very line had gone stale by 54 versions.
  🔑 **Proved, not argued: updating only the other two leaves the suite green with this file
  reading V384 / 372 files.** Closed by `claudeMdMigrationLineMatchesTheTree`, which parses head,
  next-free and count out of the line and compares all three against the tree; the figure is kept
  rather than deleted, because a reader of the house rules needs the next free version to hand, so
  the copy is made **checkable** instead. ⚠ If you reword that line, fix the pattern — do not
  delete the assertion, because an unparsed line reports a clean tree (F2-69), and that is precisely
  how it went 54 behind.
  ⚠⚠ **A `spring-boot:run` DEV BACKEND DID NOT DIE WHEN THE SUITE REWROTE `target/classes` UNDER
  IT — IT WENT RUNAWAY, AND THE SYMPTOM PRESENTED AS *"the test suite is hung"*.** The recorded
  landmine above says `mvn clean` kills a running dev backend and still reports `BUILD SUCCESS`.
  This is its third mode: DevTools entered a **restart loop leaking threads**, measured at **2,267%
  of one core — 22.7 of 24 logical processors — with the thread count GROWING 175 → 214 over two
  minutes**, which starved the suite so a normally ~11-minute run passed 32 minutes with no output.
  🔑 **It answered `/actuator/health` 200 throughout, so a health check cannot distinguish a
  working app from one consuming 90% of the machine.** The signature to look for is not latency but
  a **growing thread count at saturated CPU**; a healthy dev backend on this box is **12% of one
  core / 98 threads** (measured on the replacement immediately after). Kill it and relaunch — with
  its own `-Djava.io.tmpdir`, per the ownership landmine above.
  ⚠⚠ **A STABLE THREAD COUNT IS NOT A CLEAN BILL, AND THE RATE IS THE MEASUREMENT — NOT THE TOTAL** (measured 2026-08-22, F2-276's window). A second dev backend sat at **235% of one core with a thread count that was FLAT and slightly falling** (126 → 125 over 20 s), so it is not the leak signature above — and it is still **20× the healthy baseline on this box**, starving every build the way F2-266's suite went from 11 minutes to 32. 🔑 **The thread-growth tell finds the leak mode and misses this one; what finds both is sampling `Get-Process` twice and dividing.** A cumulative `CPU` figure cannot distinguish them either: 6,952 CPU-seconds reads as alarming on a young process and as ordinary on an old one, so **always sample a delta over a fixed interval** rather than reading the total. `/actuator/health` answered 200 throughout, again.
  ⚠⚠ **AND `TaskStop` ON THE WRAPPER DOES NOT KILL THE JVMs IT STARTED.** Stopping the background shell running `mvn spring-boot:run` reported success and left **both** descendants alive — the Maven launcher and the app JVM, still holding :8084 and DevTools' :35729, still burning the 235%. 🔑 **A successful stop of a wrapper is a claim about the wrapper**, so re-check the PIDs individually afterwards; a listener that lingers for a few seconds after a real death looks the same as one that never died, which is why the PID check and not the port check is the authority (F2-266's own rule, from the other end). Kill the descendants explicitly, and guard the kill on BOTH the session's `-Djava.io.tmpdir` and on the target not owning **8090/8091** — identify a process by its arguments, never by a log timestamp (F2-216).
  ⚠⚠ **AND THE PORT CHECK LIED IN THE OPPOSITE DIRECTION FROM THE RECORDED ONE.** F2-203/F2-252
  record that `netstat` shows LISTENING through a window where every request is refused. Here
  `Get-NetTCPConnection -LocalPort 8084 -State Listen` returned **nothing** while the app was mid
  restart — and I wrote *"it is no longer listening"* out loud before a guard 40 seconds later
  found it serving 8084. 🔑 **A not-listening reading is not proof the app is down when
  DevTools is cycling, just as a listening reading is not proof it is up.** Neither direction of a
  port check is evidence; only a request is.
  ⚙ **Three of my own kill-guards fired and every one was right to.** *"Still listening on
  35729"* — the DevTools **LiveReload** port, not the API, so the guard's claim needed narrowing to
  the shape it meant. *"Serving 8084"* — which is what a dev backend DOES, so that refusal was the
  guard asking the wrong question. The question worth guarding is **"is this the prod jar or somebody
  else's process?"**: match the session id in `-Djava.io.tmpdir` (F2-216's rule — identify a
  process by its ARGUMENTS, never a log timestamp) and refuse on ports **8090/8091**. The prod jar
  was verified up before and after.
  ⚙ **Filed:** the **93**-site census in `target/caller-named-references.txt` as a work list with no
  verdicts — sharpest unread members are kind 3 (`subprocessorCode`, PDPL Art. 30; `kriCode`;
  `bowtieCode`; `arrangementCode`, SAMA outsourcing; `noticeCode`). Also
  `ethics.aml_alerts.sla_due_at` still has **no server computation anywhere**, so the deadline is
  the caller's (F2-202's rule), unchanged by this slice; and **225 of 383** reference-shaped text
  columns in dev carry no unique index — most legitimately, being kinds 1 and 2, so that is a
  starting point for reading and **not** a defect count.
- ⚠⚠ **A COUNT IN PROSE IS A COPY OF A MEASUREMENT, AND AN INSTRUCTION NOT TO COPY IT IS NOT AN
  ENFORCEMENT — THE CELL THAT SAYS *"re-derive it, never edit it by hand"* HAS DRIFTED THROUGH FOUR
  RE-DERIVATIONS** (F2-265, measured 2026-08-22). §0's doc-map cell in
  `UNIFIED_IMPLEMENTATION_PLAN.md` states the rule for findings-register coverage and publishes the
  figures behind it. Re-derived with **the gate's own patterns** (`F2-(\d{2,3})` over the plan,
  `^## F2-(\d{2,3})` over the register — never a hand-written variant, because my own scan has
  already been refuted once here for treating sub-slice ids as distinct where the gate parses them
  to an `Integer` and rolls them up): the cell claimed *"plan references **94** distinct ids, the
  register carries **57** specs, **40 are owed**"* against a measured **264 / 235 / 29**.
  ⚠ The owed figure **overstates** the debt — F2-210's pessimistic direction, which is the less
  likely of the two to be reported, because *"we owe 40 specs"* invites no question while a
  suspiciously low number does.
  🔑🔑 **THE CONSEQUENCE IS THE LIST, NOT THE COUNT.** The cell states a rule — *"Write one before
  scheduling any of them"* — and published the list a reader consults to obey it: eight ids that
  **omit 22 of the 29 and name `F2-57`, which is already paid**, i.e. 76% incomplete. **Not
  hypothetical: the cell records it failing once already**, its own 2026-07-31 entry reading
  *"F2-61, which an ordering pass scheduled straight into the live queue without a spec, breaking
  the rule stated in this very cell"* — from this same cause.
  ⚠⚠ **All four re-derivations added a WARNING rather than making the figure checkable** (2026-07-29,
  07-31, 08-01, now). The 08-01 generation is the sharpest: it asserts its own freshness
  (*"Current, and every figure here is script output"*) — the one property prose cannot hold — and
  its instruction was *"Re-derive with the script in the commit message of this change"*, so the
  subject of *"re-derive it"* was somewhere a reader cannot reach. **F2-52's `AttachmentAccessPolicy`
  shape at the level of a document**: an instruction with no enforcement, ignored by every author who
  read it. `FindingsRegisterCoverageTest` (F2-79) ratchets the owed **count** and asserts its own
  **instrument**, and had no opinion about a document restating either — so nothing in the tree could
  contradict the cell.
  ⚠ **And the gate's own class javadoc opened *"31 specs are owed today"* while
  `BASELINE_SPECS_OWED` three paragraphs below read 29** — that constant's javadoc narrates the walk
  33→31→30→29 and the class-level sentence was left behind by the paragraphs beneath it. House rule
  3's named hazard (*read the constant, never a prose summary of it*) **inside the file that owns the
  constant**, which is F2-258's shape.
  ⚙ **Fixed by PUBLISHING rather than re-deriving a fifth time.**
  `target/findings-register-coverage.txt` (this tree's own convention — `permission-usage.txt`,
  `mass-assignment-risk.txt`, `server-stamped-fields.txt`, `entity-initialiser-checks.txt`) carries
  the three figures, the owed list and a per-id closure hint; the cell states the rule and **points
  at** the gate and the artefact; `theDocMapCellPointsAtTheGateRatherThanRestatingIt` requires
  exactly one such cell, requires both pointers, and refuses any restated coverage count.
  ⚠ **Deleting the figures WITHOUT the pointer would leave a reader nowhere to look — which is how
  they came to be in prose at all — so the pointer is asserted, not assumed.** F2-197's
  *deleting-the-numbers-asserts-health* inverted: here the deletion is only honest with a
  destination.
  ⚠⚠ **THE QUOTATION PROBLEM IS A PROPERTY OF THE WHOLE DETECTOR, NOT OF THE PART WHOSE CLAIM IS
  ABOUT COUNTS — AND I COMMITTED IT THREE TIMES IN ONE SLICE, EACH TIME AFTER QUOTING F2-193.** The
  correction convention here is that a claim's replacement **quotes** the sentence it replaces, so
  `COVERAGE_COUNT` strips double-quoted spans. I did **not** strip them when resolving *which line
  is the cell* — so the moment this finding's own plan bullet quoted the rule sentence, **two lines
  matched and the uniqueness assertion failed on a correct tree**; then the mutation harness's own
  locator found two cells for the identical reason. 🔑 **Every part of a detector that touches prose
  needs the stripping, including the part that only decides where to look.**
  ⚠⚠ **A PUBLISH ASSERTION IS SATISFIED BY THE PREVIOUS RUN'S FILE.** The mutation replacing the
  write with a branch that never fires **SURVIVED**, because the artefact was still on disk from the
  run before and still carried the right numbers — this tree's recurring *an artefact on disk answers
  an older question*, one artefact over from the stale surefire report. **Delete the file first, and
  assert the content on disk equals the string this run wrote** (which also catches a partial write).
  ⚠⚠ **AN OWED-AND-OPEN RATCHET — THE NUMBER THE RULE ACTUALLY WANTS — WAS MEASURED AND REJECTED,
  AND THE REASON IS THIS FINDING ARRIVING FROM THE INSIDE.** A spec for a closed finding is
  retrospective documentation; a spec for an open one gates the work, so *owed and open* is the
  actionable figure. Two independent parsers of the plan's own status markers **disagreed on four
  ids**: mine keyed on the first marker anywhere in the row, the gate's hint on the row's last
  non-empty cell — which for `F2-35` is an **owner** and for `F2-53` a **migration** column, both
  `✅ CLOSED` one cell earlier, while `F2-65` ends *"⬜ (b) still not to be swept"* and `F2-68` leads
  `🔁` over four `DONE` sub-items. 🔑 **My own draft of the corrected sentence claimed "22 of the 29
  are closed or refuted and 6 are open" and was wrong in BOTH operands — and what caught it was the
  two parsers disagreeing, not either of them being checked.** A ratchet on that parser would have
  been an authoritative-looking artefact nobody could keep true (F2-188), so the classification ships
  as a **hint gated on nothing**, the row is named as the adjudicator, and the cell gives **no
  total** — a total would be a fifth figure with the same lifespan as the four retired here.
  ⚠ **The escape-collapse landmine fired again through an inline `py -c`, and this time it left RAW
  CONTROL BYTES in a script**: a Windows path's `\a` and `\b` became 0x07 and 0x08, so the line read
  `D:\Sourcespache-maven-3.9.15in\mvn.cmd` in every listing — F2-235's defect, self-inflicted, in a
  file I had just been told to write with the Write tool. **The `Edit` tool then could not match the
  line at all**, because the string on disk is not the string that is displayed. Replace by line
  number (`sed -i '24s|.*|…|'`) and verify with `grep -cP '[\x00-\x08\x0b\x0c\x0e-\x1f]'`.
  ⚙ **Filed:** the debt is unchanged at **29 owed** — `F2-32`, `F2-34`, `F2-36`, `F2-37`, `F2-74`,
  `F2-85` open (one scheduled into a later round), `F2-65` half-closed with its remaining half a
  recorded decision, `F2-55` refuted, the rest retrospective documentation. **Writing all 29 in one
  batch is F2-188's failure mode** (29 paraphrases of plan rows would be 29 fluent unverified
  artefacts); pay them where a slice touches the finding. Also: `UNIFIED_IMPLEMENTATION_PLAN.md`
  holds **46 `- [x]` and 0 `- [ ]`**, so it is a log rather than a checklist — the live backlog is
  §0's tracker plus the filed items, which is worth knowing before reading it as a work queue.
- ⚠⚠ **"MODULE HAS NO CATALOGUED PERMISSION" HAS TWO OPPOSITE CONSEQUENCES, AND A FILING THAT
  CONFLATED THEM WAS CARRIED FORWARD AS THE NEXT WORK BY THREE SLICES** (F2-264, measured
  2026-08-21). F2-257 filed `DATACLASS`, `OUTSOURCING`, `REGINSPECT`, `REGREPORTING` and `RRP` as
  having no `WRITE`/`APPROVE` row in `iam.permissions`, *"so five of the ten registers are reachable
  only through the PLATFORM_ADMIN code bypass"*; F2-258, F2-259 and F2-260 each named it as the
  remaining work, and F2-260 closed the `RRP` member, which **was** a real lockout. The other four
  are not — probed live, read tier **and** write tier: `compliance` **200** on `/dataclass/assets`,
  `rmanager` **201** on `/reginspect/inspections`, `compliance` **201** on
  `/regreporting/obligations`, `rmanager` **200** on `/outsourcing/arrangements`, with an off-tier
  persona **403** every time and 4–5 of 7 personas at 200 on every read.
  🔑🔑 **The sentence was true about the CATALOGUE and reads as a claim about REACHABILITY, and the
  two coincide only when the guard is a PERMISSION guard.** All 57 of these routes are `hasAnyRole`,
  which never reaches `ModulePermissionEvaluator`, so an uncatalogued module **costs nothing at run
  time** and whoever holds the recited role can already act. That is F2-05's conversion backlog, not
  a lockout — and F2-253 is explicit that this is the distinction that matters: a role nobody holds
  has an administrator remedy, a permission nobody can hold has none. **`RRP` was sharp because it
  was the one with no remedy at all; the other four never needed one.**
  ⚠ **And the list was half the population.** Derived from the tree: **57 module packages carry at
  least one `@PreAuthorize`, and EIGHT cite no `(MODULE, ACTION)` pair** — `auth`, `ccm`,
  `dataclass`, `governance`, `opres`, `outsourcing`, `reginspect`, `regreporting`. Four were named;
  `auth`, `ccm`, `governance` and `opres` are the same shape and were never mentioned. F2-198's
  narrowing in a **filing** rather than in an instrument. (`auth` is mixed and correctly so: 3
  `isAuthenticated()` self-service routes plus one role guard.)
  🔑 **WHY NO INSTRUMENT COULD SEE IT: `PermissionCatalogueUsageTest` extracts the pairs guards
  CITE, so a module citing none is outside its population BY CONSTRUCTION.** Its hard zero was green
  over all eight, correctly, while saying nothing about them — which is how the claim survived four
  slices. `theCatalogueGapHasTwoKindsAndOnlyOneIsALockout` derives the classification **by
  reflection** (that class's own javadoc rule — *"a source regex has twice under-counted a security
  surface in this codebase"*) and asserts the eight **exactly** rather than as a ceiling, because a
  decrease-only count cannot express *the population was undercounted* (F2-204).
  ⚠⚠ **A 400 SAYS NOTHING ABOUT AUTHORIZATION, AND TWO IDENTICAL 400s READ EXACTLY LIKE A
  LOCKOUT.** `@Valid` fires **before** `@PreAuthorize` (the recorded landmine), so my first two
  probe payloads — missing one mandatory field each — returned the same 400 to the admitted persona
  and to the refused one. 🔑 **That is the very signal the filing being refuted asserts, arriving
  from my own harness.** Resolve every mandatory field from the DTO before reading a write probe's
  result, and treat a non-403 refusal as "the probe is wrong" until proved otherwise.
  ⚠ **Refuted while ranking, and each refutation is why this was a correction rather than a
  conversion:** no examiner persona reaches any write in any of the four (the tiers are cleanly
  read/write/approve and `AUDITOR`/`INTERNAL_AUDITOR` appear only on reads — F2-259 warned that both
  directions of that question look identical in a summary); every tier has a live non-admin holder
  in **both** databases, prod differing only in `CIO`/`CRO` being *absent* rather than unheld since
  V373 is pending there; and the fatal kind — a guard citing a pair the catalogue does not define —
  is at **zero**.
  ⚠ **A mutation can be killed by something other than the property it is named for, and that is
  not a pass.** Four controls, four killed, but the inverted-disjointness one died because AssertJ's
  `containsAnyElementsOf` fails on an **empty actual** — the intersection claim itself is untested,
  so that leg is a **pin** whose tripwire is a future regression. F2-262's `\b` lesson: an assertion
  protected by a different part of itself than the part it is named after. The three that matter do
  discriminate (the extractor stopping → 57 instead of 8; the module segment taken from the wrong
  end → sub-packages stop collapsing; the scan resolving nothing → exact-equality over an empty set).
  ⚠⚠ **AND THE BACKTICK LANDMINE FIRED AGAIN, THROUGH AN INLINE `py -c`, EATING EXACTLY ONE
  IDENTIFIER.** Bash expanded `` `RRP` `` as command substitution before Python saw the string, so
  the correction shipped reading *"Only  was a lockout"* — and **the sentence still reads like
  prose**, which is F2-227's rule and the reason it has to be checked rather than assumed. The
  escaped `` \` `` forms in the same string survived, so **one** token of six was lost. **Use the
  Write/Edit tools for any file content carrying backticks, and verify the identifiers survived the
  write, not just that the write happened.**
  ⚙ **Filed:** `auth`, `ccm`, `governance` and `opres` join the F2-05 backlog beside the four already
  named — none urgent, because none is a lockout, which is the whole finding;
  `CreateAssetRequest.reference` is `@NotBlank` and **caller-supplied**, so a `dataclass` data-asset
  reference is named by the client (F2-172(d)'s family in F2-258's *mandatory* shape — no
  null-coalesce, so outside `ClientSuppliedReferenceRatchetTest`'s key, and unmeasured for
  uniqueness); and `RegInspectController`'s `WRITE_GUARD` and `TRANSITION_GUARD` recite **identical**
  role lists, which is duplication rather than a defect (F2-177's shape) and becomes a real question
  the moment one of them is converted.
- ⚠⚠ **AN ANTI-VACUITY CHECK CAN BE THE SAME EXPRESSION THAT MAKES A GATE FAIL ON CORRECT
  CODE, AND THE FILE THAT COINED THAT RULE IS WHERE IT HAPPENED** (F2-263, measured 2026-08-21).
  Executing F2-262's own top filing, re-derived first (F2-221): it named **7** hand-rolled
  `hasPermission('MODULE','ACTION')` pair extractors; the population is **11 regexes in 10 files
  across 4 spellings**, and all four return the **identical 131 pairs** over 1,314 guard
  expressions with **no** guard splitting a call across a concatenation — so every divergence is
  latent and the argument is F2-177's. ⚠⚠ **The sharp edge is not the duplication.**
  `ObLifecycleTest.permissionIn` and its `sharia` twin compiled
  `CONST\s*=\s*\n?\s*"hasPermission\('([A-Z_]+)'\s*,\s*'([A-Z_]+)'\)"` and asserted `m.find()`, so
  the closing `\)"` required the constant's initialiser to **END right after the pair**. Give
  either guard a second leg and the build fails with *"guard constant X must be a hasPermission
  string"* — **a gate refusing correct code** (F2-214), blaming the guard rather than the test.
  F2-262's mechanism 3 **mirrored**: there the pattern broke when a module *converted*, here when a
  guard *gains a leg*, which is the documented direction of travel (F2-159/F2-161 ownership,
  F2-177 seats, admin overrides). Measured rather than hypothesised: **12 of the platform's 94
  permission-bearing guard constants are already that composed shape**, every one
  `"hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')"` — the **singular** `hasRole`
  F2-262 recorded as invisible to a `hasAnyRole\(`-only spelling at 67 sites.
  🔑🔑 **WHY THEY SURVIVED F2-262: they had ALREADY replaced their role extractor with a permission
  one when their modules converted, so they were never members of the role-name population — and
  F2-262 read this very javadoc, quoted its rule as its headline lesson (*"a regex that matches
  nothing satisfies every downstream assertion forever, so the anti-vacuity check belongs in the
  HELPER, where every caller inherits it"*), and did not convert the method the rule is written
  on.** The `find()` they built to be safe is the same `find()` that makes them fail on correct
  code. F2-195's shape at its sharpest: the tree held the correct rule, in prose, on the line that
  broke it. **When you quote a file's own lesson, read the method under it.**
  ⚠⚠ **REFUTED BEFORE ANY CODE WAS WRITTEN, and one of them was a complete, plausible,
  non-existent finding.** **585 of 2,203** `@PreAuthorize` sites name a CONSTANT rather than an
  inline string, which reads exactly like the blind spot a source-text extractor would have — and
  resolving the constants adds **ZERO** pairs to any of the seven source-scanned subjects, because
  the constants sit in the **same file** a whole-file scan already reads. 🔑 **A divergence
  hypothesis that survives reading can still be zero when measured.** Also refuted: two of F2-262's
  other three filings — *"`sharia` and `openbanking` no longer ask the role question"* is true and
  **harmless**, since `RoleGuardConversionRatchetTest` scans every controller reflectively and their
  remaining `hasAnyRole` hits are **comments describing the retired guards** (F2-193's
  epitaph-as-corpse).
  ⚠⚠ **THE TRAILING `\)` IS THE WHOLE THREE-ARGUMENT EXCLUSION, AND THE TWO FORMS ARE REFUSED FOR
  DIFFERENT REASONS.** `hasPermission(#externalId,'ML_MODEL','OWNER')` is refused at the opening
  quote; `hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')`'s first argument **is** quoted, so
  **only the closing paren separates it from a pair** — and matching it publishes
  `AUDIT_COMMITTEE:COMMITTEE` as a catalogued permission, which F2-255 recorded as a false ghost on
  correct code. Use `GuardExpressions.permissionPairsIn` / `permissionPairsInConstant` /
  `permissionPairOfConstant`; do not hand-roll a twelfth (F2-190).
  ⚠⚠ **AN ESCAPED `\(` COUNTS AS A CAPTURE GROUP, AND THAT INVERTED MY CLASSIFIER COMPLETELY.**
  Every one of the four real spellings opens `hasPermission\(`, so counting bare `(` scored them at
  **3**, and the only neighbour the classifier admitted was `ChangeDocumentationFreshnessTest`'s
  one-group pattern at 2 — **the gate reported the four members as clean and the one non-member as
  an offender.** 🔑 **And my own reconnaissance scan had PRINTED `groups=3` beside each spelling; I
  read past it because the number was not the answer I was looking for.** `(?<!\\)\((?!\?)` is the
  fix. Nth instance in this lineage of a detector satisfied by something other than its claim, and
  the first where the evidence was already in my own output.
  ⚠ **TWO PAIR REGEXES FOR ONE QUESTION WAS A DESIGN AND STILL NEEDED ONLY ONE PATTERN.**
  `PermissionCatalogueUsageTest` held a strict `[A-Z_]+` extractor and a loose `[^']*` one for its
  well-formedness leg — extract widest, then judge — which is correct reasoning and did not require
  two regexes: the helper already takes the widest reading, so the judgement became one
  `WELL_FORMED_PAIR` over the joined pair. **Not tidiness: with two, the extraction silently
  dropped a malformed pair from `target/permission-usage.txt` while only the other leg could see
  it.** Judging the joined string is equivalent to judging the halves — a token carrying a colon
  fails the pattern too — which is asserted rather than argued.
  ⚠ **`GuardExpressions.stripComments` IS THE REGEX FORM AND IT IS NOT A DROP-IN REPLACEMENT
  EVERYWHERE.** `MeasuredBaselineFreshnessTest`'s javadoc records that the equivalent alternation
  **throws `StackOverflowError`** on this codebase's larger files (Java's engine recurses per
  iteration of `[\s\S]*?`; the Python census can use the regex, the JVM cannot), so that class keeps
  its own character scanner and **only its extraction moved**. Nine callers use the helper's stripper
  today on sources small enough not to hit it — which is exactly where the four spellings were.
  ⚠⚠ **BOTH FIRST-ROUND MUTATION SURVIVORS WERE REAL UNTESTED CLAUSES, NOT EQUIVALENT MUTANTS.**
  Removing `permissionPairOfConstant`'s size check left it throwing out of an iterator — **the
  anti-vacuity property the whole consolidation rests on** — and the class-stripping clause was
  never exercised, because no spelling in the tree happens to put a comma inside a character class
  (`[\w<>,.\[\]]` does appear elsewhere in the tree). 🔑 **On a clean corpus a clause with no
  counter-example scores exactly like a correct one**; both were closed by assertion rather than
  recorded as equivalent. 12/12 killed after.
  ⚠ **A "collect the violations, assert the list empty" leg is vacuous once the tree is clean**, so
  the malformed-guard leg gained a synthetic self-test: widening `WELL_FORMED_PAIR` to `.*` had left
  every assertion above it green (F2-257's rule).
  ⚙ **The verification for a consolidation is that the published census does not move**: 131 pairs /
  1,784 guard sites / 74 composed / 363 pure role sites / 38 role names, measured identical on
  master and after. **A helper that changed them would be changing findings rather than sharing
  code.** And label a leg whose tripwire is in the FUTURE as a PIN rather than counting its green as
  a discriminating pass — the 3-arg exclusion leg pins a property master already had (F2-225).
  ⚙ **Filed:** **two test-support packages for one purpose** — `com.tss.grc.support`
  (`MigrationVocabularies`, `GuardExpressions`, 20 importers) and `com.tss.grc.testsupport`
  (`StateMachineContract`, 8 importers), F2-190's shape one level up, now visible because four files
  import from both on adjacent lines; a character-scanner variant of the helper's stripper; and
  `PermissionCatalogueUsageTest`'s well-formedness leg **rejecting a token carrying a digit**
  (`ISO27001:READ`) although `ModulePermissionEvaluator` would match `ISO27001_READ` — latent at 0
  such tokens, and whether upper-snake-only is a house convention or an over-narrow check is an
  **owner decision**, not a side effect of an extractor change.
- ⚠⚠ **A FILING THAT NAMES ITS MEMBERS BY MODULE INHERITS THE MODULE LIST OF THE SLICE THAT FILED
  IT, AND A MODULE CAN HOST A DIFFERENT QUESTION'S EXTRACTOR** (F2-262, measured 2026-08-21).
  Executing F2-260's filed *"shared role-name extractor: 5 hand-rolled copies, 3 already bitten"*,
  the re-derivation (F2-221) refuted the membership before any code was read. The three modules it
  named — `sharia`, `openbanking`, `cpty` — hold `hasPermission('MODULE','ACTION')` **pair**
  extractors, a different question, so **they were never members of this population**; and the one
  copy that still carried the defect (`CrisisRoomGuardTest`) **was not in the list at all**.
  Re-derived: **9 members, 3 mechanisms, 6 spellings, 5 slices already bitten** — F2-145(e) twice
  (`sharia`, `openbanking`), then F2-254, F2-259, F2-260, **one per converting slice**. 🔑 **Three
  of five named members wrong, against this programme's usual ~2-in-10 — and every error was in the
  reassuring direction: two modules recorded as already fixed and one as latent, none of which was
  ever in the population.**
  ⚠⚠ **THE RULE THIS SLICE IMPLEMENTS WAS ALREADY WRITTEN DOWN IN THE TREE, IN THE FILE THAT COINED
  IT.** `ObLifecycleTest`'s javadoc, explaining why its own conversion produced a legible failure,
  says *"a regex that matches nothing satisfies every downstream assertion forever, so the
  anti-vacuity check belongs in the **helper**, where every caller inherits it."* Correct, and for
  four more slices there was no helper for it to belong to. F2-195's shape: the codebase can hold
  the correct rule, in prose, beside the place it was not applied.
  ⚠⚠ **ONE DEFECT, THREE MECHANISMS, AND THE THIRD FAILS IN THE OPPOSITE DIRECTION FROM THE OTHER
  TWO.** Mechanisms 1 and 2 (reflective and source-text quoted-token scans) report `MODULE` and
  `ACTION` as **ghost roles** on a converted guard — the false-accusation shape F2-254, F2-259 and
  F2-260 each repaired. Mechanism 3 (`DataclassLifecycleTest`, `OutsourcingLifecycleTest`) asserts
  `m.find()` on a pattern that requires the initialiser to **BE** a `hasAnyRole` string, so a
  conversion **hard-fails the build** with *"guard constant READ must be a hasAnyRole string"*.
  🔑 **Both are a gate failing on correct code (F2-214); only one looks like a bug report.** And it
  was not hypothetical: `DATACLASS` and `OUTSOURCING` are two of the four modules F2-257 filed as
  having no catalogued permissions, **i.e. two of the next conversions in this programme's own
  backlog. The fourth failure was scheduled, not possible.**
  ⚠ **Six spellings, each narrower than the widest on at least one axis, every divergence LATENT
  today** — measured against **1,721** guard expressions in `src/main`: no whitespace tolerance (0
  sites write `hasAnyRole (`); `hasAnyRole\(` only, blind to the singular `hasRole(` (**67** sites,
  0 in that copy's own subject); inner `'([A-Z_]+)'`, blind to a name with a digit (all **38**
  recited names are upper snake case); no `hasAuthority('ROLE_X')` handling (**2** authority sites,
  **0** naming a role); no scoping at all (**1,392** guards contain a `hasPermission(`); and no
  comment stripping. 🔑 **What made them harmless was nine independent regexes happening to agree,
  and nobody was maintaining that** (F2-177). The helper takes the **widest correct** reading of
  every axis, so consolidating can only ever *add* a name — which is why the verification is that
  `RoleGuardConversionRatchetTest`'s published census does not move: **363 pure role sites and 38
  role names, identical before and after. A helper that changed them would be changing findings
  rather than sharing code.**
  ⚠⚠ **THE HARD ZERO CANNOT SEE THE ONE MEMBER THAT STILL HAD THE LIVE DEFECT, AND SAYING SO IS THE
  POINT.** The ban is on *hand-rolled role-call regexes*; run against master it finds **8** of the
  **9**. `CrisisRoomGuardTest` is invisible to it **by construction** — it had no scoping at all, so
  there was no role-call regex to ban — and it was precisely the copy that would have reported `BCM`
  and `READ` as ghost roles the moment anybody converted `CRISIS_ROOM_GUARD`, a conversion its own
  fourth test is written to adjudicate. 🔑 **A ban on hand-rolled extractors cannot see the copy
  that never wrote one.** The complement is `everyFormerCopyCallsTheHelper`, which names all nine;
  neither leg is sufficient and the pair is the contract, because a hard zero over a population the
  instrument cannot see is the most reassuring possible artefact (F2-205).
  ⚠⚠ **MY OWN GATE REFUTED MY OWN CENSUS ON ITS FIRST RUN, THREE WAYS, AND ALL THREE WERE THE
  INSTRUMENT.** (a) It found a **ninth** member, `DataOfficerLifecycleTest`, after my hand-written
  scan had settled on eight — 🔑 **a detector scoped to the shapes you have already seen returns a
  SMALLER population, which reads exactly like a complete sweep** (F2-198). (b) The reverse
  exclusion leg reported my own gate file as *not* declaring a role-call regex, i.e. an exclusion
  for something never in the population — F2-199's rule, and the direction nobody writes first.
  (c) The detector could not see mechanism 3 at all, because it read only the **first** string
  literal after `Pattern.compile(`.
  ⚠⚠ **THAT THIRD ONE IS THE REUSABLE INSTRUMENT BUG: A CONCATENATED REGEX DEFEATS A DETECTOR
  ANCHORED ON THE FIRST LITERAL, AND THE TWO COPIES OF ONE MECHANISM DIFFER IN WHETHER IT BITES.**
  `Outsourcing` interpolates its constant **second** (`compile(constant + "…hasAnyRole…")`), so its
  first literal already carries the token; `Dataclass` interpolates it **third**
  (`compile("String " + constant + "…hasAnyRole…")`), so its first literal is just `"String "`. A
  first-literal detector therefore finds one twin and misses the other — **1 of 2, measured** —
  reporting a smaller population, which reads exactly like a partial sweep. Concatenate **every**
  literal in the statement. ⚠ Bound the window to the enclosing statement, **not** a paren-balanced
  walk: a Java regex literal contains real `(` and `)` characters, so counting parens over raw
  source miscounts. Overshooting over-reports, which fails loudly — the safe direction.
  ⚠⚠ **AND THE DISCRIMINATOR IS `has` **AND** `Role`, NEVER `Role` ALONE — BECAUSE THIS PLATFORM HAS
  TWO ROLE-NAME SURFACES AND THE OTHER ONE IS THE ONE NO `@PreAuthorize` SCAN SEES.** My first draft
  keyed on the token `Role` and fired on `RoleNameSurfaceRatchetTest`, whose
  `ParentGuard\.(?:roles|permOrRoles)\s*\(` contains `Roles` — that is `AttachmentAccessPolicy`'s
  **Java role lists**, the second surface whose drift F2-52 and F2-177 each had to find by hand. It
  asks a different question about a different artefact and keeps its own extractor. 🔑 **A detector
  keyed on a token cannot tell one surface from the other, and the surface it would have swallowed
  is the one the register warns is invisible to every guard scan.**
  ⚙ **The primitive is `com.tss.grc.support.GuardExpressions`** — `rolesRecitedBy` /
  `rawAuthoritiesIn` / `hasRoleLeg` / `isPureRoleGuard` / `guardConstant` /
  `rolesRecitedByConstant` / `stripComments`. `hasRoleLeg` exists so a caller that only needs to
  **classify** a guard never has to declare its own regex, which is what makes the hard zero
  exemption-free: there is always a correct alternative, so it cannot fail on correct code.
  ⚠ **A `hasPermission('MODULE','ACTION')` pair extractor is deliberately NOT in it.** Seven test
  classes hand-roll one and it answers a different question; shipping an unused method would be an
  inert construct answering the question a reader was about to ask (F2-184), and an unused helper
  should not ship (F2-201). Filed as the same argument, one slice over.
  ⚠ **Two source-text properties every caller now inherits, which between zero and one of the four
  copies had:** an absent constant **throws** rather than returning empty, and the extraction is
  **tolerant** of what the initialiser contains. And comment stripping — the hazard is live in this
  tree, since `RrpController`'s javadoc says *"`APPROVE_GUARD` was `hasAnyRole('PLATFORM_ADMIN',
  'CRO','CFO')`"*, one rewording away from a scan reading the retired guard as the live one
  (F2-193's epitaph-as-corpse). One of the four stripped comments, **inline at a single call site**,
  which is the divergence rather than the fix.
  ⚠ **`mvn` is a `.cmd` shim on Windows: `subprocess` with `shell=False` cannot resolve it and dies
  with `WinError 2`.** `shell=True` is worse — that is cmd.exe, where a bash-style `VAR=x mvn`
  prefix is a syntax error and the run prints **nothing at all** (recorded). Resolve the launcher
  path and pass the environment as a parameter.
  ⚠ **And the empty-heredoc landmine fired again, on a `python - <<'X'` with nothing in it**: it
  hung the shell for the full 120-second timeout and swallowed the command chained behind it. It is
  recorded in this file and I did it anyway. **Use the Write/Edit tools.**
- ⚠⚠ **THE ATTRIBUTION QUALITY DECREASED AS THE AUTHORITY INCREASED, AND THE TIER WITH NONE WAS
  THE BOARD SIGN-OFF** (F2-261, measured 2026-08-21). Executing F2-260's filing of §B2b's last two
  rows, re-derived first (F2-221). One ladder, three stages, three different mechanisms:
  `bcm-review` records the actor from the controller, `cfo-approve` records it from
  `CurrentActor` (since F2-223), and `board-approve` stamped `board_approved_at` from the server's
  clock plus a board-minutes reference and took **no actor parameter, because there was no column
  for one**. F2-256's shape — the server owns WHEN and nobody owns WHO — at the **top** of a chain
  rather than at its edge, on the artefact an ISO 22301 or SAMA business-continuity reviewer reads.
  🔑 **Stage 0 already demonstrated the correct form** (`preparedByUserId` beside
  `preparedByName`) and each approval stage degraded from it, so the shape to look for is not "is
  there an attribution" but **"does it get worse as you climb?"**
  ⚠⚠ **A CLAIM IN THAT FILING HAD BEEN FALSE FOR WEEKS AND WAS QUOTED THREE TIMES.** F2-152 filed
  *"a board approval with no signer, and a CFO approval whose signer is whatever the request
  says"*. F2-223 fixed the CFO half **with a comment saying so**; F2-52's javadoc on
  `BOARD_APPROVE_GUARD` repeated the whole sentence anyway, and F2-254's javadoc repeated it again
  eight paragraphs later. 🔑 **A filing that is half true gets quoted whole** — F2-192's rule that a
  claim about a consequence is the least-verified part of a filing, compounded because nobody who
  quoted it opened the service.
  ⚠⚠ **THE FOUR-EYES BAR WAS WRITTEN, UNIT-TESTED, PROBED AND WITHDRAWN, AND THE WITHDRAWAL IS THE
  FINDING.** With `board_approved_by` in place the comparison is finally **writable** — board
  signatory ≠ CFO approver ≠ BCM lead reviewer, three now-comparable parties, exactly the
  prerequisite `BiaQuantitativeController`'s javadoc said was missing. Then measurement killed it:
  `cfo-approve` admits `CFO` + `PLATFORM_ADMIN`, `board-approve` admits `EXECUTIVE_REVIEWER` +
  `PLATFORM_ADMIN`, **both those roles are held by nobody in either database**, and they are
  §B2b's last two rows. **So the only way to complete this chain today is `admin` taking both
  approvals — which is precisely what the bar must refuse.** Probed live with the bar wired: 409,
  correct, on the one path that works. The post-fix row shows it without any bar:
  `stage1=so_officer, stage2=admin, stage3=admin`.
  🔑🔑 **So the missing column, the missing bar and the two zero-holder roles are ONE problem, not
  three.** F2-254 wrote that *"the guard lockout and the missing attribution are one problem and
  F2-52 filed them as two"*; there is a third. Enforcing the bar alone yields a correctly-controlled
  feature **nobody can use** — F2-112's shape **inverted**: not a control that cannot fire but one
  that can only ever fire. Converting the guards alone removes the separation. Doing both decides
  who may give a board-level approval, which is a governance decision about a customer's
  organisation and not a side effect of an attribution fix. **Ship the half that breaks nothing,
  and say why the other half is one decision rather than two tasks.**
  ⚠ **The gate has to assert a withdrawal in BOTH directions**: the attribution is stamped **and**
  neither approval method contains a segregation refusal. A one-directional gate would let the bar
  reappear and the feature quietly stop working — which is the failure mode a reader would blame on
  the guards rather than on the bar.
  ⚠⚠ **A RECORDED BLOCKER CAN BE TWO CLAIMS STACKED IN ONE SENTENCE, AND ONLY ONE OF THEM A
  BLOCKER.** F2-254's read *"the four-eyes bar that would make that safe is not buildable today: …
  there is no identity for either tier to compare. Fixing F2-152 is the prerequisite, and choosing
  who holds a financial versus a board approval is an owner decision plus a migration."* That is
  (a) no identity exists — a fact, and fixable — and (b) who may take which approval — a question.
  🔑 **Stating them as one sentence made the whole thing read as owner-gated, which is how it
  survived F2-52, F2-152, F2-254 and F2-260 without anybody building the buildable half.** F2-178's
  rule with a new edge: re-measure a recorded blocker, and **split it into its claims before
  believing any of them.** Correct it where the claim is written, because a stale blocker is the
  input to somebody's next estimate.
  ⚠ **Mirror a new column on its siblings in type, width AND nullability, and assert it.** The
  comparison the column exists for treats all three parties as strings, so a narrower column could
  truncate one party and make two different people compare equal. ⚠ An id would be the better shape
  — a username can be renamed and the history then lies — and converting **one** of three is
  exactly what produced the divergence being fixed (F2-190), so all three together is a slice and
  one of them is a defect.
  ⚙ **A migration that adds a column to a live governance register: backfill nothing and refuse
  nothing.** Measured first — one row, at `CFO_APPROVED`, `board_approved_at` NULL in both databases
  — so no completed approval's approver has to be invented (F2-196), and deliberately **no check
  refusing a row that DOES have a board approval with no approver**, because on a customer's
  database that is a pre-fix approval to disclose (F2-209's third state) and not a reason to block
  their whole migration chain (F2-183).
  ⚠⚠ **A REFUSAL THROWN FOR THE WRONG REASON IS INDISTINGUISHABLE FROM THE RIGHT ONE IN A
  PASS/FAIL LINE.** My unit fixture authenticated a plain `String` principal;
  `CurrentActor.username()` returns null for anything but a `GrcUserDetails`, so
  `requireUsername` threw its **no-actor** refusal and the four-eyes assertion failed looking
  exactly like the bar not firing. Only the message separated them. **Assert on the message, not
  just the exception type** — and copy the house `GrcUserDetails` fixture idiom rather than
  inventing one (it lives in `com.tss.grc.security`, not `common.security`).
  ⚠ **F2-203's landmine again, and `sleep` is not the fix.** `mvn -o compile` rewrites
  `target/classes` and DevTools restarts the context **while the listener stays bound**, so
  `netstat` reports LISTENING through a window in which every request is refused — and a refused
  connection reads exactly like a dead backend, which sends you looking for a crash that did not
  happen. **Poll `/actuator/health` in a loop; never time a restart.**
  ⚠ **And two more guessed names in one slice** (`bcm.business_process` — it is
  `bcm.critical_processes` — and `bia_id` on the loss-line table, which broke a probe's cleanup and
  left four tagged rows for the next run to trip over). The register's own `process_id` was the
  better source than any table name: **when you need a valid foreign key for a fixture, read one
  out of the register you are about to write to.**
  ⚙ **Filed:** §B2b's last two rows unchanged but better described (one owner decision, three
  symptoms); all three attribution columns holding **usernames rather than ids**;
  `bcm.bia_quantitative` having **zero CHECK constraints** with its status vocabulary only in
  `guardTransition` (F2-221's shape — not cheap here, since this slice touched one writer of one
  column); and the live row's three parties being **invented officers with titles** (F2-207's cast
  of 65), whose repair rewrites a live governance row and is an owner decision.
- ⚠⚠ **THE FINDING THAT COINED "EXISTENCE IS A WEAKER QUESTION THAN HOLDING" WAS ITSELF ONE OF
  THE SITES STILL OPEN, AND ITS EXIT CONDITION NAMED THE REMEDY THAT DOES NOT WORK** (F2-260,
  measured 2026-08-21). Executing F2-259's §B2b list, re-derived first (F2-221):
  `RrpController` carried three pure role guards over 17 routes and the APPROVE tier was
  `hasAnyRole('PLATFORM_ADMIN','CRO','CFO')` — `CRO` and `CFO` held by **no user** in dev or prod,
  `CRO` **absent from prod's `iam.roles`** because V373 is pending there. So
  `POST /api/v1/rrp/cycles/{id}/submit`, the route that lodges the Recovery & Resolution Plan with
  **SAMA**, was reachable by the platform administrator and nobody else.
  🔑 **Why it bit rather than merely being narrow:** `/transition` carries the WRITE tier, which
  `RISK_MANAGER` holds, so a risk manager drives a cycle `DRAFT → IN_PREPARATION → BOARD_REVIEW →
  APPROVED`; `RrpService.transition` then explicitly refuses the `SUBMITTED` edge (*"Use the
  dedicated /submit endpoint to lodge with SAMA"*) and `ALLOWED.get("APPROVED")` is
  `{IN_PREPARATION}` — so for every non-administrator **APPROVED was a dead end, its only forward
  edge the administrator-only submit and its only other move backwards into preparation.**
  Identical in dev and prod: 5 cycles, 46 critical functions, 31 indicators, 32 recovery options,
  and **no cycle has ever reached SUBMITTED**. Probed live: `rmanager` drove a planted cycle to
  APPROVED (200/200/200) and was refused the filing at **403**; so was `compliance`; `admin` lodged
  at 200.
  🔑🔑 **F2-130 FOUND ALL OF THIS ON 2026-08-06.** Its Lesson cell is the sentence the whole
  F2-253 → F2-259 lineage rests on — *"Ask of an authorisation list not do these names exist but
  **can anybody satisfy it today**. Existence is a weaker question than holding, and the weaker
  question is the one four findings had been asking."* It stayed 🔴 OPEN for fifteen days, and its
  stated reason was right as far as it went: *"converting to `hasPermission` is F2-05's ratified
  direction… This is the fourth data point for sequencing F2-05, not a fourth patch."*
  ⚠⚠ **And its exit condition pointed at the remedy that does not work.** The pin's trigger read
  *"When CRO is created or CFO is granted, close the finding and update this"* — **seating**, and
  only that, in a javadoc whose prose had just named conversion as ratified. V373 then chose exactly
  that remedy for exactly these two roles, and **`CRO` was created and the finding stayed open**,
  because seating a role assigns it to nobody (F2-258: 79 sites *satisfiable*, not satisfied).
  🔑 **A pin that names three remedies in its prose and phrases its tripwire in terms of one of them
  points the next reader at that one** — and the one it named is the one this register has since had
  to write two findings to correct. ⚙ One of its measurements had also aged: *"CRO does not exist"*
  was true that day, false in dev from V373 on, and still true in prod.
  ⚠⚠ **THIS ONE HAD NO ADMINISTRATOR REMEDY AT ALL, WHICH IS A DIFFERENT KIND OF LOCKOUT.**
  F2-253 is explicit that an unheld role *"has an administrator remedy — a remedy, not a defence"*.
  `RRP` had **no row in `iam.permissions` of any kind**, so nothing could be granted because nothing
  existed to grant: F2-257's catalogue gap, first of five closed (`DATACLASS`, `OUTSOURCING`,
  `REGINSPECT`, `REGREPORTING` remain). 🔑 **Ask which of the two you have before proposing a fix:
  "assign the role" is available for four of the five slices in this lineage and was unavailable
  here, and the two look identical from the guard.** ⚙ Note the direction of travel is the opposite
  of `ETHICS`'s entry in the same census row: there the catalogue existed and no guard consulted it,
  here the guards existed and there was nothing to cite. Both end up in that column and only the
  order tells you which failure it was.
  ⚠ **V383 catalogues exactly the three tiers the controller declares** — read off its three guard
  constants, never inferred from method shape, which is V362's landmine (it classified by shape and
  promoted whole WRITE tiers to APPROVE in four of six modules; V367/V368 undid it). Check 1b
  refuses a fourth `RRP` action so a later tier is a decision rather than a default.
  ⚠⚠ **A MIGRATION MAY ASSERT ITS OWN WRITES AND MUST NOT ASSERT TENANT DATA.** The natural fifth
  self-check was the one the file exists for — *does any active user now hold a role granted
  `RRP:APPROVE`?*, F2-253's satisfiable-versus-satisfied distinction — and it was written and
  **removed**: a brand-new deployment runs the chain with a bootstrap administrator and no
  `GRC_ADMINISTRATOR` seated, so it would THROW and block V383 and every migration after it, i.e.
  F2-214 at the schema. The roles V383 *grants* are safe to require because migrations before it
  create them (V362 seats `BOARD_SECRETARY` and `CFO`, V373 seats `CRO` — **resolved from the
  migration set, not assumed**); who *holds* them is not. Same reason check 6's catalogue floor is
  **50** and not the ~140 both live databases carry: F2-255 measured that a fresh Flyway install
  from V1 builds only **93** pairs, because 36 core pairs exist in dev and prod solely because the
  Python bootstrap made them. 🔑 **"Did this unlock anybody?" is a probe's question, not a
  migration's** — and the gate asserts the absence structurally so the next author does not re-add
  it.
  ⚠⚠ **`RISK_MANAGER` IS DELIBERATELY WITHHELD FROM `RRP:APPROVE`, AND CONVENTION ARGUED THE OTHER
  WAY.** It holds `RISK:APPROVE`; the shipped guard pointedly did not name it, and WRITE and APPROVE
  overlapped on `CRO` and nothing else. Granting it would let the persona that prepares **and
  board-approves** the cycle also lodge it with the regulator, retiring the one separation this
  controller expresses. V383's check 4 fails the migration if a second role ever holds both, and the
  probe asserts it from the outside — post-fix `rmanager` still answers **403** on submit while
  driving the cycle to APPROVED at 200. `AUDITOR`, `VIEWER` and `REGULATOR_VIEWER` are withheld from
  READ although all three hold `BCM:READ` and `RISK:READ`: a resolution plan is the most sensitive
  document in a bank's contingency set. 🔑 **"Follow the platform's convention" settles a conversion
  TARGET (F2-253) and does not settle a GRANT — for a grant the shipped guard's own audience is the
  evidence, and convention is the thing to argue against.**
  ⚠⚠ **THE THIRD INSTANCE OF ONE DETECTOR BUG, IN A THIRD MODULE, ONE PER CONVERTING SLICE.**
  `RrpLifecycleTest.rolesIn` scanned `'([A-Z_]+)'` across the whole guard initialiser, so
  `hasPermission('RRP','READ')` parsed as two role names — `RRP` and `READ`. F2-254 repaired exactly
  this in `BiaApprovalChainGuardTest`, F2-259 in `AuditCommitteeGuardTest.namesIn`, now here.
  🔑 **A quoted-token scan over a guard expression flags every role-to-permission conversion F2-05
  exists to make**, and because each copy only bites when its own module converts, **the shape
  recurs once per module for want of a shared primitive.** Measured: **5** hand-rolled copies across
  the test tree — `sharia` and `openbanking` were quietly updated when F2-145(e) converted them, and
  `cpty` is still latent. Extracting one test-support helper is filed (F2-190: do not hand-roll a
  fifth).
  ⚠ **F2-225's lesson from the other side: a pinned known-defect test passes on a defective tree by
  design, so the codebase's own record of this defect was invisible to every scan and the finding
  had to be made twice.** Its tripwire then did its job — it failed the build the instant the guards
  were converted, and `FindingsRegisterConsistencyTest` required the register cell to move in the
  same commit. Two instruments in series, both working.
  ⚙ **Two things verified CORRECT in passing, worth recording because this family usually gets them
  wrong.** `SubmitCycleRequest.regulatorAckReference` is `@NotBlank` and **caller-supplied** — which
  is right rather than F2-172(d): a SAMA acknowledgement reference is *received from the authority
  and recorded*, never minted, and F2-209's own rule says so. And `title_ar` is `@NotBlank` on the
  create, so house rule 7 holds.
  ⚙ **The probe's two runs validate each other, which is worth copying when a conversion is
  population-preserving.** All three tiers admit exactly the roles they used to, so the *only*
  behavioural difference is the submit unlock: post-fix code loaded during a "pre" run would have
  answered 200 on leg 11 and **failed** rather than passing for the wrong reason. That is a stronger
  guarantee than polling `/actuator/health`, which a stale listener also satisfies (F2-252/F2-256).
  ⚙ **Filed:** §B2b is down to **2**, both `module.bcm` (`BiaQuantitativeController#boardApprove`
  and `#cfoApprove` — F2-254's two BIA tiers, still blocked on a migration, since
  `bcm.bia_quantitative` has a caller-supplied `cfo_approver_name` and **no `board_approved_by`
  column at all**, so the four-eyes bar that would make one shared `BCM:APPROVE` safe cannot be
  built yet); the **shared role-name extractor** (5 copies, 3 already bitten); F2-257's catalogue
  gap now at **four** modules, each the same two-step this slice was; `COMPLIANCE:APPROVE` existing
  with **0 grants** in both databases (F2-253's attrition one level up, at the permission rather
  than the role, like `CPTY:APPROVE`); and the whole **`rrp` schema having zero CHECK constraints**,
  so `rrp_cycle.status` is narrowed only by `RrpService.ALLOWED` — F2-221's shape, and not cheap
  here, because this slice touched the module's guards and not its writers (F2-183).
- ⚠⚠ **A GHOST ROLE LOOKS WRONG IN THE SOURCE AND AN UNHELD REAL ROLE LOOKS CORRECT, SO A SITE
  THAT HAS ALREADY BEEN AUDITED FOR THIS FAMILY IS THE HARDEST PLACE TO SEE IT** (F2-259, measured
  2026-08-20). Executing F2-258's filed §B2b work list, re-derived first (F2-221):
  `AuditCommitteeDashboardController`'s three snapshot-mutating routes were
  `hasAnyRole('BOARD_SECRETARY','PLATFORM_ADMIN')`, `BOARD_SECRETARY` **exists** in `iam.roles` in
  both databases and is held by **no user**, so they admitted the platform administrator alone.
  Probed live as `compliance` (GRC_ADMINISTRATOR) and `auditor` (INTERNAL_AUDITOR), **both holding
  `AUDIT:WRITE`**: **403 on generate, recordResolution and finalize** while `admin` generated at 201
  — on three routes wired to `/audit-committee/dashboard`, a routed and sidebar-linked page, over the
  artefact SAMA CG §6 and CMA CG Article 54 require the Audit Committee to report from.
  🔑🔑 **F2-52 HAD ALREADY CORRECTED THIS GUARD FOR THIS EXACT FAMILY, AND SAID SO.** It found
  `hasAnyRole('AUDIT_COMMITTEE_CHAIR','COMPANY_SECRETARY','PLATFORM_ADMIN')`, replaced the ghost
  `COMPANY_SECRETARY` with the real `BOARD_SECRETARY`, and wrote *"🔑 `COMPANY_SECRETARY` was a
  near-miss. The platform ships `BOARD_SECRETARY` … Corrected, **which restores one of the two
  intended personas**."* It restored nobody. F2-253's distinction, fourth closure of that shape after
  F2-253, F2-254 and F2-258 — and the first on a site somebody had already opened, edited and
  documented for it. **Asking whether a recited role EXISTS is not asking whether anybody HOLDS it.**
  ⚠⚠ **That is what makes a corrected site worse than an uncorrected one:** the file carries a
  precise description of the defect *immediately above the defect*, presented as history (F2-52's own
  heading reads *"this said 'restricted to the Chair + Secretary + Platform Admin' and admitted the
  platform administrator alone"*), the class javadoc then claimed the snapshot was *"mutated by the
  Board Secretary and the platform administrator"*, and `MEASURED_BASELINE.md` listed the package in
  its *"zero `hasPermission` usage"* column with a guard that reads as fixed. 🔑 **A sentence
  describing a guard's audience is a claim about `iam.user_roles`, never about `iam.roles`.**
  ⚠⚠ **THE SKU CODE IN `@RequireModule` IS NOT THE PERMISSION MODULE, AND TAKING IT FROM THERE MADE
  THIS LOOK UNCONVERTIBLE.** My first measurement asked about `AUDIT_LC` (the SKU) and
  `AUDIT_COMMITTEE`, and both have **no catalogued permission at all** — which reads exactly like
  F2-255's catalogue gap, i.e. like a blocker. The catalogued module is **`AUDIT`**, and every peer
  controller under that same SKU (`AuditLifecycleController`, `FindingController`, `IaController`,
  `AuditEngagementController`) already guards with `hasPermission('AUDIT', …)` — **55 of 55 guarded
  routes, unanimous**. 🔑 **The module's own convention settles a conversion target (F2-253), and you
  find it by reading the peers, not by reading an annotation whose name resembles a permission.**
  ⚠ **Audience diff measured BEFORE converting (V362's landmine), identical in both databases:**
  nothing live dropped, `GRC_ADMINISTRATOR` and `INTERNAL_AUDITOR` admitted, containment holds.
  Internal audit is admitted **deliberately** — `AUDIT:WRITE` already lets it author the findings,
  engagements, annual plans and working papers this snapshot aggregates, and the artefact is internal
  audit's own reporting pack to its committee. ⚠ That is the **opposite** case from V380, which
  *withheld* `INTERNAL_AUDITOR` from the audit-log integrity seal because operating it would make
  internal audit the author of the evidence it examines. **Say which of the two you are in; they look
  identical in a summary.**
  ⚠ **A guard conversion needing a migration is now a recognised step, not a one-off** (V381 → V382 in
  consecutive slices). `BOARD_SECRETARY` held `AUDIT:READ` **and `AUDIT:EXPORT`** and not
  `AUDIT:WRITE` — so the platform already let that role read and export the audit register and
  withheld only the ability to prepare its own committee's snapshot — and converting without the grant
  retires the persona the guard was written for, a policy change disguised as a refactor. Live effect
  **none** (nobody holds it), so what changes is what a tenant administrator gets on assigning it,
  which is the remedy §B2b describes and precisely why that remedy is not a defence.
  ⚠⚠ **THE `AUDIT:EXPORT` GRANT WAS FOUND BY A SELF-CHECK THROWING, NOT BY MY READING.** V382's check
  1 asserted a post-grant count of 2 on the assumption the pre-existing set was `{READ}`; it refused
  the migration on the first dev run. 🔑 **A migration that refuses on a wrong premise is the whole
  argument for writing self-checks rather than comments** — and the fact it surfaced was worth more
  than the check: it changed the finding's sentence.
  ⚠⚠ **TWO TIERS WERE MEASURED AND DELIBERATELY NOT CONVERTED, AND THE REASONS POINT IN OPPOSITE
  DIRECTIONS.** `hasPermission('AUDIT','READ')` is held by **7 live roles including `VIEWER` and
  `AUDITOR`**, so converting the read tier would widen a governance dashboard to any authenticated
  reader — §B3's `BoardPackController` defect, where ghost roles sat above a comment saying reads were
  for *"a board/governance audience, not any authenticated principal"* while `REPORTING_READ` admitted
  `viewer`; four live non-administrator roles already read this dashboard, so there was no lockout to
  fix. Converting the **attest** tier would instead **drop `EXTERNAL_AUDITOR`**, which holds no
  `AUDIT` grant and is the one signatory whose independence is the point (F2-256's `EXTERNAL_AUDIT`
  rule). 🔑 **"Finish the sweep" is not a direction — on one tier it widens past the audience and on
  the next it drops the party the control exists for.** Both verdicts are gate legs.
  ⚙ **A side effect worth naming: the attest tier was WIDER than the tier producing the artefact it
  attests to.** Probed pre-fix, `auditor` signed an attestation at **201** against a snapshot it was
  refused permission to generate at **403**; the only reason nobody hit that inversion is that
  `audit_committee.dashboard_snapshot` holds **0 rows in dev and prod** — and the lockout is a
  sufficient explanation for the emptiness. 🔑 **An empty register is evidence about the guard above
  it, not only about the tenant.**
  ⚠⚠ **A PRE-EXISTING GATE FAILED ON THE CORRECT CONVERSION — BOTH LEGS — AND IT IS F2-254'S FINDING
  VERBATIM IN A SIBLING FILE.** `AuditCommitteeGuardTest.namesIn()` ran a quoted-token regex over the
  **whole** guard expression, so `hasPermission('AUDIT','WRITE')` parsed as two role names and
  `noGuardNamesANonexistentRole` reported them as ghosts. F2-254 repaired exactly this in
  `BiaApprovalChainGuardTest` — **same author slice (F2-52), same purpose, sibling file kept the
  bug** — so a ghost-role detector written as a bare quoted-token scan flags **every** conversion
  F2-05 exists to make. 🔑 **A gate that fails on correct code trains the next reader to edit the
  assertion rather than the code (F2-214), and here the assertion is a ghost-role assertion.** Scope
  such a scan to the argument list of `hasAnyRole(…)`/`hasRole(…)` — the shape its claim is about.
  ⚙ **Make the control for a detector repair MECHANICAL.** F2-254's rule is that a repaired detector
  must still answer the same on the tree the original passed on; arguing that is not checking it. A
  leg now asserts it against the **literal pre-fix and post-fix guard shapes**, plus a composed guard
  (whose role leg must still be seen — the shape F2-159/F2-161 controls are built from) and a
  three-argument ownership guard (which names no role). Its anti-vacuity floor moved 13 → **11** with
  the reason recorded, because the converted tier recites no role at all.
  ⚠⚠ **THE MUTATION HARNESS FOUND MY PEER-CONVENTION LEG SURVIVING WHILE THE CONVENTION ERODED.** It
  was `anyMatch(guard contains "hasPermission('AUDIT',")`, and moving **two of `FindingController`'s
  five** routes off the permission left three still citing it. The mutated code was not wrong about
  the claim I had written — **the claim was weaker than the leg's purpose** (F2-206's rule from the
  other side). Replaced by the negative form that actually holds at 55 of 55: **no peer route may be a
  *pure* role guard**, which kills the mutation, tolerates a legitimate composed or ownership guard
  (F2-254: never ban `hasAnyRole` outright) and tolerates routes being added, with a floor of 50 so a
  renamed or emptied peer class cannot make every assertion above it pass over nothing.
  ⚙ **`role_guard_census.py` finally takes `--database`**, closing a gap F2-253 and F2-258 each filed:
  `DATABASE=TSS_GRC` was hardcoded, so every holder figure it has ever published described **dev**
  while reading as a statement about the platform. The generated report now names the database it read
  and why that matters — two databases differ by **absence** as well as by holders, since a role a
  pending migration has not seated is missing from `iam.roles` on the deployed server and merely
  unheld in dev. `BASELINE_PURE_ROLE_SITES` 383 → **380**, re-derived not subtracted (F2-207); census
  §B2b **6 → 3**; `MEASURED_BASELINE`'s *"packages with zero `hasPermission`"* **11 → 10**.
  ⚙ **Filed:** the **three** remaining §B2b guards as a work list with no verdicts (F2-188) —
  `module.bcm` 2 (F2-254's two BIA tiers, blocked on a migration: `bcm.bia_quantitative` has no
  `board_approved_by` column) and `module.rrp` 1 (the SAMA recovery-and-resolution filing V373 itself
  calls the sharp case, blocked on `RRP` having no catalogued permissions — F2-257's gap);
  **F2-151 unchanged and now the only thing between this surface and a real committee control** —
  `admin.committee_member_terms.member_user_id` is NULL on **all five** audit-committee seats in both
  databases, so F2-177's `hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')` would answer false for
  everyone (F2-178's measurement, re-confirmed), and `AttestRequest.attestedRole` is still
  **self-declared**, so an admitted `INTERNAL_AUDITOR` can record an attestation as `CHAIR` while the
  signer's identity is stamped from the principal and hashed — both halves need the same roster
  linkage, and seating those five terms is a **tenant data task**, not a jar. Also
  `GenerateSnapshotRequest.snapshotCode` is caller-supplied (F2-172(d)'s family on a code an auditor
  cites) but **read and found collision-safe** — `UQ_ac_snap_code` exists and the service appends a
  uniqueness suffix rather than throwing, deliberately, because *"two snapshots in a day"* is a
  legitimate workflow — so it is a naming question, not a defect. And `audit_committee`'s **2** CHECK
  constraints both agree with their DTO `@Pattern` (F2-180's check performed rather than assumed).
- ⚠⚠ **THE PLATFORM'S CHOSEN REMEDY FOR A LOCKED-OUT ROUTE ALREADY SHIPPED, NAMED THE ROUTE,
  AND LEFT IT EXACTLY AS CLOSED AS IT FOUND IT — BECAUSE SEATING A ROLE ASSIGNS IT TO NOBODY**
  (F2-258, measured 2026-08-20). Executing F2-255's filing, re-derived first (F2-221): the filed
  conversion target `AI:APPROVE` **does not exist**, and the population is **six** routes not three.
  `AiLifecycleController`'s three creates were `hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','CIO','DPO')`
  and its three `/transition` routes `hasAnyRole('PLATFORM_ADMIN','CRO','CIO','DPO')`; `CIO`, `CRO` and
  `DPO` are held by **no user** in dev or prod. Probed live as `rmanager` and `compliance`, both holding
  `AI:WRITE`: **403 on all three transitions**, over 16 validations / 11 retraining events / 13
  recertifications identical in both databases — and all six routes are wired to routed pages, so the
  Move control on three AI-lifecycle screens failed for every non-administrator.
  🔑🔑 **V373 was written for these routes — its header names `AiLifecycleController#transition` and
  describes it, before the fix, in the words this finding would use — and chose remedy (3), SEATING
  `CIO`/`CRO`, over remedy (1), conversion. Its closing line is the tell: 79 guard sites are now
  *"satisfiable"*.** F2-253's distinction exactly: `hasAnyRole` tests for a **granted authority**, so a
  role assigned to nobody admits nobody, and dev has held both roles with **0 grants of any module**
  ever since. **Satisfiable is a property of the guard; satisfied is a property of `iam.user_roles`.**
  ⚠⚠ **And the reason it picked the wrong remedy here is that the measurement it made was about four
  OTHER modules.** V373: *"Unavailable: DATACLASS, OUTSOURCING, REGREPORTING and RRP have NO catalogued
  permissions at all … so converting means first cataloguing four new modules."* True of those four
  (still true — F2-257 filed that half). The fifth was swept in one sentence earlier by resemblance
  (*"AiLifecycleController#transition … is the same shape"*) and then justified on a **different and
  circular** ground: *"only AI is catalogued at all, where AiLifecycleController is role-guarded anyway,
  so any grant would be inert."* The grant was inert **because the guard was a role guard**, which is the
  half that could change; `AI:READ`/`WRITE`/`EXPORT` have been catalogued since V313 and `AI:WRITE` is
  held by **8 live roles**. 🔑 **F2-215's two-of-N at the level of a REMEDY: the same defect shape across
  five modules, four genuinely unconvertible, and the sentence saying "the same shape" is what stops a
  reader asking whether the same remedy applies.**
  ⚠ **A guard conversion can need a MIGRATION, and skipping it is a policy change disguised as a
  refactor.** Of the retired names `DPO` already held `AI:WRITE` while `CIO`/`CRO` held **no module
  permission at all**, so converting alone would have **narrowed the shipped audience by two roles**.
  V381 grants them `AI:READ`+`AI:WRITE` as **declared literals** with five THROWing self-checks (the
  grant landmine: `INSERT … SELECT … JOIN … WHERE NOT EXISTS` grants nothing when either side is
  catalogued later and cannot tell *already granted* from *nothing matched*; check 0 refuses outright
  when V373 has not run). Live effect **none** — both roles are held by nobody — so what changes is what
  a tenant administrator gets when they assign one. State the widening rather than letting it be a side
  effect (V362's landmine): `AI:WRITE` also opens **7** `AiSuggestionController` routes these two were
  not named on.
  ⚠⚠ **The load-bearing measurement is on the half you must NOT convert.** `AUDITOR` is named on the READ
  tier, **is** held, and holds `AI:EXPORT` and **not** `AI:READ` — the only role in either database with
  that shape, and deliberate (`MlModelController.EXPORT_GUARD` is `hasPermission('AI','EXPORT') and
  hasAnyRole(…,'AUDITOR',…)`). So `hasPermission('AI','READ')` there would take five live roles to four
  and **drop the examiner**: F2-253's asymmetry, and a gate leg now fails the build if somebody finishes
  the sweep (F2-254's precedent). ⚙ Assert the converted tier by **claim** (a permission guard citing the
  pair), never by banning `hasAnyRole` — F2-254 had two pre-existing gates fail on a *correct* conversion
  for exactly that reason, and a gate that fails on correct code trains the next reader to edit the
  assertion (F2-214). Reflect over `@PreAuthorize` rather than scanning source: the annotation takes a
  compile-time constant, so a renamed or relocated constant cannot fool it (F2-187).
  ⚠⚠ **TWO OF MY OWN GATE LEGS FELL TO THE MUTATION HARNESS AND ONE WAS AN EQUIVALENT MUTANT WORTH
  CONVERTING RATHER THAN RECORDING.** (a) `contains("THROW 51381")` **SURVIVED** the mutation removing
  check 0, because V381 carries **five** of them — F2-194's substring-satisfied-by-something-else, in a
  gate written after quoting it; replaced by a **count** plus that check's own message anchor. (b) The
  mutation making the guard reader skip unguarded methods **SURVIVED**, and the mutated code was **not
  wrong on this corpus** (every route is guarded, so it returns the identical map — F2-206). A
  **synthetic fixture** — a nested controller with a mapped, unguarded method, asserted to be *reported*
  rather than skipped — turns it into a kill. 🔑 **A reader that omits what it cannot explain shrinks its
  own population, and a smaller population reads exactly like a cleaner tree.** 8 killed / 0 survived
  after; the pre-fix control fires naming all six routes exactly.
  ⚠⚠ **THE ESCAPE-COLLAPSE LANDMINE FIRED TWICE IN ONE SLICE THROUGH INLINE `py -c`, AND THE SECOND TIME
  A `sed` FILTER SWALLOWED THE EVIDENCE.** Once a `\\(\\s*` collapsed inside a Java string literal (a loud
  `javac` *"illegal escape character"*); once a `\n` became a **raw newline inside a Python string
  literal**, and because the run was piped through `sed -n '/marker/,$p'` the resulting `SyntaxError`
  printed nothing at all — the harness read as producing no output rather than as failing.
  🔑 **The recorded rule names heredocs and the hazard is the inline script: use the Write/Edit tools for
  any file content carrying escapes, and never filter a harness's stderr through a marker-anchored
  `sed`.**
  ⚠ **And one assertion was wrong about the codebase rather than the reverse:** `contains("'AI', 'WRITE'")`
  failed because V313 writes `('AI','WRITE',` with no space. An assertion that depends on a formatting
  choice fails on a correct tree the day somebody reformats the file — parse whitespace-tolerantly.
  ⚙ **Corrected in passing:** `RoleGuardConversionRatchetTest`'s javadoc said
  *"`BASELINE_PURE_ROLE_SITES` is 404"* while the constant read **389** — the prose-versus-constant drift
  this file warns about, inside the file that owns the constant. Ceiling lowered to **383** in the same
  commit as the sweep, re-derived rather than subtracted (F2-207); census §B2b **9 → 6**.
  ⚙ **Filed:** the **six** remaining §B2b guards as a work list with no verdicts (F2-188) —
  `module.audit_committee` 3 (`BOARD_SECRETARY`, 0 holders; a committee-seat question, so F2-177's
  `hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')` may be the mechanism rather than a module
  permission), `module.bcm` 2 (F2-254's two BIA tiers, blocked on a migration — no `board_approved_by`
  column), `module.rrp` 1 (the SAMA recovery-and-resolution filing V373 itself calls the sharp case,
  blocked on `RRP` having no catalogued permissions — F2-257's filed gap); the **four-eyes question this
  conversion makes askable** (catalogue `AI:APPROVE`, guard the three transitions with it, compare the
  decider against `validator_user_id`/`owner_user_id` — an **owner decision**, and F2-256 already filed
  that `model_validation` has no completer column and `retraining_event` no approver column at all);
  **all three `AiLifecycleService` creates honouring a client-supplied `@NotBlank reference`** with **no
  UNIQUE index** on any of the three columns, F2-172(d)'s family in the `SubprocessorService.create`
  shape (mandatory, so no null-coalesce, so outside `ClientSuppliedReferenceRatchetTest`'s key); and
  **`role_guard_census.py` still hardcoding `DATABASE=TSS_GRC`**, so every holder figure it publishes —
  §B2b included — is a statement about **dev** (F2-253 filed it; it cost a re-measurement here, because
  prod has `CIO`/`CRO` *absent* rather than unheld while V373 is pending).
- ⚠⚠ **A COLUMN NOBODY SETS CONTRIBUTES TO NEITHER HALF OF A PAIR BUILT FROM SETTER CALL
  SITES, SO "NOBODY OWNS WHO" IS INVISIBLE TO THE GATE BUILT TO FIND "THE CALLER OWNS WHO"**
  (F2-256, measured 2026-08-20). Executing F2-255's filing, re-derived first (F2-221):
  `ai_governance.recertification.approver_user_id` is declared on the entity, returned on the wire by
  `RecertificationResponse`, and **written by no code path in the platform** — neither
  `CreateRecertificationRequest` nor `TransitionRecertificationRequest` declares it either, so the
  caller could not supply it and the server did not resolve it, while `transitionRecertification`
  stamps `decidedAt` from `LocalDateTime.now()` in the same `switch` branch. Every ISO/IEC 42001
  model recertification granted, renewed, revoked or allowed to lapse through the API recorded the
  moment and not the person. 🔑🔑 **`SplitAttributionContractTest`'s `splitsIn` walks
  `SETTER.matcher(body)` and sorts each hit into a clock half or an actor half — BOTH halves come
  from setter calls**, so a never-written field produces no hit and can never pair. Same for
  `ParameterBorneActorTest` and `PrincipalStampedFieldContractTest` (which narrow that population
  further) and `ServerStampedFieldContractTest` (which intersects two sets a never-written field is
  in neither of). **All four are keyed on a write EXISTING**, and the narrowing is none of the ones
  already recorded (a filename, a three-entry allow-list, a module name, an `extends` clause, a
  field-name list inside a fix, a claim shape in a gate): it is **the existence of a setter call**.
  ⚠⚠ **NOT LATENT, AND THE ROW IS THE WITNESS.** `REC-2026-CRD` is byte-identical in dev and prod:
  seeded at **03:00:54** with three siblings, then **decided through the API 93 minutes later** —
  `decided_at` **8 ms before** `updated_at`, `updated_by = 1`, `approver_user_id` **NULL**.
  🔑 **The framework's row stamp captured the actor while the domain's own attribution column stayed
  empty** — F2-226's rule from the other side, and the reason a row stamp can never substitute for an
  attribution. F2-255 filed *"2 of 13 rows"* without distinguishing that **one is API-written and one
  is a back-dated seed row**; only reading the timestamps separates them.
  🔑 **The correct artefact has the IDENTICAL helper and this service has it too.**
  `DataclassService#transitionException` declares the same `private static Long currentUserId()`
  delegating to `CurrentActor.userId()` and writes `e.setApproverUserId(currentUserId())` beside its
  own `setApprovedAt(LocalDateTime.now())`. `AiLifecycleService` has that helper **three lines below
  the switch**, feeding `timeline(...)`'s `setActorUserId(currentUserId())` — present, used for the
  event, not used for the row. F2-215's two-of-N, with the two artefacts being sibling **services**.
  ⚠ **Fix shape:** one private `recordDecision()` as the **sole writer of the `(decided_at,
  approver_user_id)` pair** (F2-245's rule — the claim that prevents drift is about WHERE the writes
  live), taking the actor from **`CurrentActor.requireUserId`** (F2-236's primitive, never a second
  mechanism — F2-190); **both** decision branches routed through it, because leaving `EXPIRED` and
  `REVOKED` blank reproduces the defect on a certification allowed to lapse and one withdrawn, the two
  an assurance reader asks about hardest; **refusing** rather than storing null so the hazard is
  absent rather than latent (F2-213); and **overwriting a prior ASSIGNMENT**, because two live `DUE`
  rows carry an approver with no `decided_at` and once the clock is stamped the column is an
  attribution (F2-236's window rule) — a stale assignment left standing would credit the decision to
  whoever was queued for it.
  ⚠⚠ **FOUR OF MY OWN DRAFTS OF THE INSTRUMENT WERE WRONG, THREE OF THEM UNDER-REPORTING.**
  (1) Asking whether the **FIELD NAME** was written anywhere cleared **this very site**, because
  `DataclassService` and `RegReportingService` each write a *different* entity's `approverUserId` —
  F2-187's *"resolve per owner type"*, one noun across, and 🔑 **a field-name fallback silently
  clears exactly the columns most likely to be spelled the same elsewhere.** (2) Resolving the owner
  from the **source file name** collapsed `Entities.java`'s eleven nested `@Entity` classes into one
  key whose field set was their union and whose owner matched no bytecode — eleven phantom rows, each
  looking exactly like a measured one; 🔑 **a source file is not a type.** (3) `getDeclaredFields()`
  was claimed to exclude `BaseEntity`'s nine *"by construction"*; it excludes **inherited** fields,
  not **redeclared** ones, and 259 entities declare their own `createdBy`/`updatedBy`/`deletedBy`, so
  the census went **62 → 209**, almost all row stamps — 🔑 **a correction is not a monotone
  improvement, and "by construction" is a claim to check rather than a reason to stop checking.**
  (4) ⚠⚠ **The mutation harness proved the CLOCK half VACUOUS**: it matched the clock setter by name
  across all sources, and across 432 entities those names collide constantly, so **the mutation
  deleting the requirement altogether SURVIVED**. A surviving mutant is a hole only if the mutated
  code is wrong (F2-206) — here it was, and the wrongness was that the condition **could not fail**.
  Resolved per owner from bytecode (a setter call on this entity, from a class that also calls a
  `java.time` `now()`), class-scoped rather than method-scoped because the house idiom puts
  `LocalDateTime now = LocalDateTime.now()` in the caller and the setter in a helper.
  ⚠ **That tightening excluded exactly one member and the exclusion is ASSERTED, not hidden.**
  `FraudCaseUpdate#actorName` dropped out because `occurredAt` is a **field initialiser**
  (F2-215's third filling mechanism, which no setter scan can see; F2-225/F2-226 measured that
  population at **82 entities / 87 act-clock fields**). Named with an exemplar in both directions,
  because a hard zero over a population the instrument cannot see is the most reassuring possible
  artefact (F2-205).
  ⚠ **The one write mechanism bytecode cannot see is Jackson** — an entity-bound endpoint writes
  every property by reflection with no `INVOKEVIRTUAL`, so those columns are caller-writable and
  belong to `MassAssignmentRatchetTest`'s family, not this one. Entity-bound types are subtracted and
  the subtraction is pinned both ways; without it the gate would publish a caller-writable column as
  unwritable, which is the opposite finding.
  ⚠ **My own substantiality leg refused my own verdicts on its first run** — two `ADJUDICATED`
  entries read *"DARK TWICE — see Policy#approverId."* at 35 characters. F2-188's rule firing on the
  slice that wrote it. ⚠ **`AiLifecycleService` had no test of any kind**, which is not the same as a
  path somebody decided was safe (F2-248).
  ⚠ **The empty-heredoc landmine fired twice in one session**, both times as a pointless
  `python - <<'PY'` "guard" that hung the shell for the full 2-minute timeout and swallowed the
  chained commands behind it. It is recorded and I did it anyway — **use the Write/Edit tools.**
  ⚠ **And `Restarting due to` is NOT the DevTools restart marker in this log** — it appears **0**
  times over a session in which the app started **three** times. 🔑 **Count
  `Started GrcApplication` instead**: a grep for a phrase that never appears reads exactly like a
  process that never restarted, which is how a probe ends up scoring a fix against pre-fix code (or,
  as here, reporting the pre-fix legs as failures against post-fix code).
  ⚙ **Filed:** **`model_validation` has NO completer column and `retraining_event` NO approver column
  at all** — both stamp their clock from the server (`completed_at`, `approved_at`) and their only
  person columns are `validator_user_id`/`owner_user_id`, written from the request at creation, i.e.
  **assignments** (F2-226); `validation_type` admits `EXTERNAL_AUDIT`, so a validator need not be a
  platform principal at all (F2-224's `auditorExternalParty` rule). Adding the columns is a migration
  and a **product decision**, and the absence is asserted in the gate so it is countable rather than
  implied — F2-215's two-of-N, pre-empted. Also: the **27-member work list with no verdicts**, whose
  largest sub-shape is **nine event registers declaring `actorName` beside an `actorUserId` their
  service DOES write**, so the id is stored and the NAME is the half the response carries and every
  timeline entry renders a blank actor; `recertification` having **no route that can record an
  assigned approver**; and the whole **`ai_governance` schema having zero CHECK constraints** on any
  of the three registers' `status` columns (F2-221's shape, now cheaper for `recertification` since
  this slice touched its only writer — F2-183).
- ⚠⚠ **A REGISTER THAT DECLARES A PERSON ID BESIDE A DISPLAY NAME AND WRITES ONLY THE ID
  PUBLISHES A RESPONSE FIELD NOTHING CAN FILL — AND THE LABEL IS USUALLY *NOT* A COPY OF THE ID'S
  IDENTITY, SO THE FIX THAT WORKED LAST SLICE IS THE WRONG ONE** (F2-257, measured 2026-08-20).
  Executing F2-256's filing, re-derived first (F2-221): ten event/timeline registers declare
  `actor_user_id` **and** `actor_name` (from `sys.columns`, dev = prod), every service writes only the
  id inside a private `timeline(...)` helper, and **nothing in the platform writes the name at all**.
  ⚠⚠ **For three of the ten the name was the response's ONLY actor field** — `AiLifecycleDtos`,
  `DataclassDtos` and `OutsourcingDtos` declare `actorName` and no `actorUserId` while the column
  beside it is populated on 13 of 13, 0 of 0 and 10 of 10 rows — F2-198's `crossBorderTransfer` shape
  with both halves in one record.
  🔑 **The pre-fix witness was in the data and needed no probe**: `ai_governance.ai_lifecycle_event`
  id=1 is the only row that register has ever received from the platform (F2-256's own transition) and
  the only one of thirteen with an id and no name — the other nine were seeded in one bulk write and
  all carry a name. **When a register's seeded rows are complete and its API-written rows are not, the
  gap IS the finding.**
  🔑🔑 **Ask whether the label is a COPY before reaching for the derive-from-the-parent fix.** The
  stored names are offices, teams, committees (`'لجنة أخلاقيات الذكاء الاصطناعي — AI Ethics
  Committee'`), an external regulator (the Digital Government Authority) and **machine actors**
  (`'System (cert-monitor)'`, `'System (SIEM detection)'`). That is F2-251's case, not F2-249's:
  deriving the label from the resolved user would **destroy information**, so the fix is to stamp the
  authenticated principal and leave history alone.
  ⚠ **`CurrentActor.username()`, never `requireUsername(...)`, in a side-effect writer.** The id half
  is nullable and comes from the same primitive, so refusing on the name makes the two halves behave
  differently — and a timeline write that throws aborts the business operation it exists to describe
  (F2-191). **Both null together** is the invariant, not *never null*; `ob_event`'s six machine-actor
  rows are why.
  ⚠ **And not F2-249's `TenantUserDirectory`**: measured, **none of these ten modules contains a
  single `module.iam` import**, so a directory adds ten edges to a decrease-only graph ratchet that may
  never be raised. `CurrentActor` is in `common/`, outside that population, and is the precedent F2-248
  set on `ethics.case_actions`. **Check what a fix costs the module graph before choosing the richer
  primitive.**
  ⚠⚠ **F2-256's filing said "nine" and its own javadoc listed TEN.** The tenth, `FraudCaseUpdate`, is
  outside that census because its clock is a **field initialiser** (F2-215's third filling mechanism) —
  an *instrument blind spot*, not a judgement on the merits. 🔑 **A member excluded by a blind spot
  reads exactly like a member that was checked and cleared**, and the prose disagreed with its own list
  in the reassuring direction.
  ⚠⚠ **Its consequence claim was wrong too: no screen renders any of these ten actors.** `EthicsPage`
  is the only surface in the platform that reads an `actorName`, and that is F2-248's register — so the
  blank reached **API consumers**, not a page. 🔑 **A claim about a consequence is the least-verified
  part of a filing** (F2-192), and this one was a fact about today rather than a property of the control
  (F2-182): the field is on the wire, so the next client to declare it renders nothing.
  ⚠⚠ **A "collect the violations, assert the list empty" invariant is VACUOUS once the tree is fixed,
  and the mutation harness is the only thing that says so.** My pair invariant read
  `if (idWritten && !nameWritten) collect;`; forcing the condition false **SURVIVED**, because on a
  fixed tree there is nothing to collect and **an empty list is what both a correct gate and a dead one
  produce**. Rewritten as the **positive equality of two measured sets** (`nameWritten` must contain
  exactly `idWritten`) with a floor under the id side so they cannot match by both being empty —
  F2-208's assert-the-positive rule, and the same family as F2-256's own M8 one slice earlier.
  ⚠ **One survivor was a recorded EQUIVALENT mutant**: pointing the name collector at the id half is
  undetectable on a clean corpus where every register writes both, and its only discriminator is the
  pre-fix control (F2-206). A fifth mutation was **withdrawn as invalid** — deleting a ban is the
  delete-an-assertion family, and the mutation that adds a real refusal already proves the ban
  discriminates.
  ⚠⚠ **MY FIRST PRE-FIX CONTROL DID NOT RUN AT ALL, AND ITS OUTPUT ACCUSED THE GATE.** The
  `git checkout master -- <path>` paths were backend-relative against a repo-root `cwd`, so every
  checkout failed, the gate stayed green and the harness printed *"THE CONTROL DID NOT FIRE — the new
  legs are vacuous."* 🔑 **A control that cannot restore the pre-fix tree reads exactly like a gate that
  is vacuous, and both readings end with the author editing the gate rather than the harness.** Print
  the checkout failures *and* treat a zero-file checkout as an error. Corrected, it fires 4 of 12 legs
  naming four registers exactly.
  ⚠⚠ **MY PROBE MUTATED A REAL SEEDED GOVERNANCE ROW.** Leg 3 transitioned `REC-2026-FRD` DUE →
  IN_REVIEW instead of planting its own victim (F2-206), on a register this programme had measured two
  slices earlier. Restored field-for-field from prod **including `updated_by`/`updated_at`** — 🔑 a
  status restored under a row stamp naming the probe is a repair that still records the intrusion — and
  all five touched tables reconciled against prod afterwards.
  ⚠ **A `CHARINDEX` cleanup keyed on `[REVIEW-TEST]` cannot see a child row the SERVICE authored.**
  `sharia.sharia_event`'s note is composed server-side, so the tag never reaches `note_en` and the
  delete matched 0 rows while the row was still there. **Clean a child by its parent id, not by a tag it
  never carries** — and re-count the tables afterwards rather than trusting the cleanup's own report.
  ⚙ **Filed:** `reginspect.reginspect_inspection_update` holds **6 of 20 rows whose `actor_user_id`
  names a user in another tenant**, identical in dev and prod, all `admin` (org 1) on org-2/org-3 rows
  at one seed timestamp — seed-origin with a correct writer, F2-242's shape in a SAMA/NCA inspection
  register, and repair is an **owner decision**; and **`DATACLASS`, `OUTSOURCING`, `REGINSPECT`,
  `REGREPORTING` and `RRP` have no `WRITE`/`APPROVE` row in `iam.permissions` at all**, so five of the
  ten registers cannot have that permission GRANTED — ⚠⚠ **and F2-264 refuted the consequence this
  sentence went on to draw.** It read *"so five of the ten registers are reachable only through the
  PLATFORM_ADMIN code bypass"*, and F2-258, F2-259 and F2-260 each carried that forward as the next
  work. Probed live, read tier and write tier: only `RRP` was a lockout (closed by F2-260, and it had
  **no** administrator remedy). The other four answer **200/201** for a non-admin holder and **403**
  off-tier, because **all 57 of their routes are `hasAnyRole`, which never reaches
  `ModulePermissionEvaluator`** — so an uncatalogued module costs nothing at run time. 🔑 **The claim
  was true about the CATALOGUE and reads as a claim about REACHABILITY, and the two coincide only when
  the guard is a PERMISSION guard.** `CPTY:APPROVE` exists with **0 holders** (F2-253's attrition).
- ⚠⚠ **A ROLE NOBODY HOLDS HAS AN ADMINISTRATOR REMEDY; A PERMISSION NOBODY CAN HOLD DOES
  NOT — AND THE GATE BUILT FOR EXACTLY THAT DEFECT EMITTED A REPORT AND ASSERTED NOTHING ABOUT
  IT** (F2-255, measured 2026-08-20). Executing F2-254's filed `module.ai` block, the
  re-derivation (F2-221) killed the filed conversion before any code was read: **`AI:APPROVE`
  does not exist** — the catalogue holds only `AI:READ`/`WRITE`/`EXPORT` — so my own filing's
  *"`AI` has its own permission module"* was true and said nothing about the action the guard
  needed. 🔑 **A fact about the module tells you nothing about the action**, which is F2-254's own
  headline one level down. Asking the general question instead — *how many guards cite a
  `(MODULE, ACTION)` pair the catalogue does not define?* — found `AUDIT_LOG:WRITE`, cited by
  `POST /api/v1/audit-log/integrity/seal`, created by **no migration in the packaged set** and
  present in **neither database** (dev 140 pairs, prod 114). `ModulePermissionEvaluator`
  string-matches `MODULE + '_' + ACTION`, so it could only ever answer false and the route reduced
  to the PLATFORM_ADMIN code bypass. F2-253's 24 attrition guards at least have a remedy — assign
  the role — and here there is none: nothing can be granted because nothing exists to grant, so
  closing it takes a jar.
  ⚠⚠ **THE ROUTE'S OWN JAVADOC IS THE WITNESS, AND THE OBSERVE HALF ONE METHOD ABOVE WAS FINE.**
  It reads *"It exists so the control can be EXERCISED rather than only observed: a sealing pass
  whose only trigger is a timer is a control nobody can demonstrate on request, and DF-1 showed
  what an unobserved scheduled sweep costs."* Built to be demonstrable by whoever is asked, and
  demanding a permission nobody the request would be put to could hold. `GET /integrity` is
  `hasPermission('AUDIT_LOG','READ')` — catalogued, 6 roles, **4 with a live holder** in dev and
  prod. **Two adjacent methods, one permission each, and the one that was catalogued is the one
  that already worked** (F2-215's two-of-N at the finest range yet). Probed 10/10 pre and post:
  all four READ holders answer 200 on `/integrity` while only `admin` sealed.
  ⚠ **A 200 with `sealed:0` is indistinguishable from a 200 that wrote nothing** — both granted
  personas got exactly that, because `admin` sorted first and had already sealed the five rows.
  Prove the work separately: 0 unsealed → 3 reads → 1 unsealed → CISO seal → 0 unsealed.
  ⚠⚠ **THE INSTRUMENT: `PermissionCatalogueUsageTest`'s OWN JAVADOC NAMES THIS DEFECT CLASS AND ITS
  TWO PRIOR INSTANCES** — F2-23 (`ADMIN:APPROVE`, 59 committee methods) and F2-27 (`AI:EXPORT`),
  *"both found by hand, months apart"* — **and its two build-time assertions are that its own
  extraction found ≥ 85 pairs and that no guard names a non-`UPPER_SNAKE` token.** Neither compares
  a cited pair against the catalogue; that was deferred to `grant_tightness.py`, a two-step manual
  script. 🔑 **The stated premise — *"the suite has no database"* — is true of WHO HOLDS a
  permission and false of WHETHER IT EXISTS.** The catalogue is defined by the **migrations**, which
  live in `src/main/resources` and are read hermetically by a dozen tests here already; bundling
  both questions under *"tightness"* carried the harder one's constraint to the cheaper one. The
  script's own header even writes the split down — *"a permission no role holds **(or that the
  catalogue does not define)**"* — with the half that needs no database inside the parenthesis.
  **When one half of a check needs an input the build lacks, the other half does not inherit that
  constraint; naming them as one thing is what makes the cheap half invisible.**
  ⚠⚠ **AND THE NEW LEG COULD NOT BE A BARE HARD ZERO, WHICH IS ITSELF THE BIGGER FINDING.** `V1`
  only `CREATE`s `iam.permissions` and `V239`'s header says BCM *"shipped with permission rows"*, so
  a fresh Flyway install from V1 creates **93** catalogue pairs while the guards cite **128**:
  **36** core pairs — `RISK:READ`, `RISK:WRITE`, `BCM:READ/WRITE/DELETE`, `COMPLIANCE:READ/WRITE`,
  `CYBER:READ/WRITE/DELETE`, `PRIVACY:READ/WRITE`, `SCM:READ/WRITE/DELETE`, `ADMIN:READ/WRITE`,
  `REPORTING:READ/WRITE`, `CHANGE:APPROVE/DELETE`, `SUST:APPROVE`, `TPRM:APPROVE` and eight EXPORT
  pairs — are created by **no migration at all** and exist in both live databases only because the
  bootstrap made them. On such an install every guard citing them is closed to all but the platform
  administrator, and **a missing catalogue row cannot be fixed from the role-admin screen the way a
  missing grant can**. Same family as the recorded *"225 grants short of dev"*, one table up.
  Declared as `CREATED_BEFORE_THE_MIGRATION_SET` and enforced **both** ways, so the gap is countable
  instead of invisible; back-filling it is an **owner decision**.
  ⚠ **The authority for "does this permission exist in the platform" is the MIGRATION SET, never
  either database.** Prod shows **27** cited pairs absent from `iam.permissions` across `AML`,
  `AUTOMATION`, `CONSUMER`, `CPTY`, `ETHICS`, `OPENBANKING`, `SHARIA` — that is the V345–V381
  deployment gap, which Flyway closes on deploy, **not 27 defects**. One database can be behind and
  the other ahead via bootstrap; only the migrations say what ships.
  ⚙ **V380 narrows the READ tier rather than promoting it**, which is the V362 landmine as an
  assertion: `ORG_ADMIN`/`GRC_ADMINISTRATOR`/`CISO` granted, `AUDITOR` withheld (an examiner watches
  the demonstration rather than performing the write), `INTERNAL_AUDITOR` withheld (operating the
  seal would make internal audit the author of the evidence it examines), `IAM_LEAD` withheld, and
  `PLATFORM_ADMIN` withheld per V272's own *"needs no grants (evaluator bypass)"*. Four self-checks
  THROW, including **containment** (nobody seals a ledger they cannot read) and **strictness**
  (`|WRITE| < |READ|`, because containment alone is satisfied by granting the whole tier).
  ⚠ **The parser's negative controls are the load-bearing half.** Scope the extraction to the
  **statement**, not the file: `iam.role_permissions` inserts in the same migration carry triples
  like `('ORG_ADMIN','AUDIT_LOG','READ')` whose first two elements read as a pair, and a file-scoped
  parse invents `ORG_ADMIN:AUDIT_LOG`. Both 3-argument guard forms must also stay out —
  `hasPermission(#id,'EXERCISE_RUN','OWNER')` and `hasPermission('AUDIT_COMMITTEE','COMMITTEE',
  'SEAT')`, whose first argument **is** quoted, so only the closing paren distinguishes it; matching
  either would report `ML_MODEL:OWNER` or `AUDIT_COMMITTEE:COMMITTEE` as a ghost on correct code.
  Mutation harness 4/4 killed, and the Java gate agrees with the throwaway Python locator
  **exactly** (128/93/36) — F2-213's write-the-sweep-as-the-gate rule earning its place rather than
  being quoted.
  ⚠ **`iam.permissions.description_en` is `NVARCHAR(300)`** and SQL Server error **2628** names the
  column and the truncated value but **not the limit** — resolve the width from `sys.columns`, the
  guessed-name family at a column's *width* rather than its *name*.
  ⚠⚠ **`BUILD FAILURE` WITH NO `Tests run:` LINE MEANS NOTHING RAN, AND THE CAUSE IS NOT ALWAYS THE
  JDK.** Here the working directory had drifted to the repo root, where there is no POM; F2-252
  recorded the JDK-11 version of this and the tell is the same. 🔑 **The absence of that line is the
  signal, whatever produced it — a stale surefire report on disk then answers an older question.**
  ⚙ **Filed:** the 36-pair fresh-install catalogue gap; **`module.ai`'s three transitions are still
  administrator-only** (`CRO`/`CIO`/`DPO` all 0 holders, `CIO`/`CRO` **ghosts in prod**) with **15
  live rows in each database** on a state that has an outgoing edge — and the guards were never
  carrying a four-eyes bar, because `WRITE` and `APPROVE` **overlap on `CIO` and `DPO`** and no
  transition compares the actor against anything, so unlike F2-254's disjoint BIA tiers collapsing
  them onto `AI:WRITE` (the module's own convention on `AiSuggestionController`) retires nothing;
  and **`ai_governance.recertification.approver_user_id` has no writer in any code path** while
  `decided_at` is stamped from the server's clock — 2 of 13 rows already carry a decision with no
  decider in both databases, and `currentUserId()` sits in the same service feeding the timeline
  event, so F2-223's split attribution here is *the server owns WHEN and nobody owns WHO*.
- ⚠⚠ **THAT FINDING HAD ONE INSTANCE AND READ AS BAD LUCK — THERE ARE THREE, AND EACH IS A
  PRIOR SLICE THAT CORRECTED A GHOST IN ONE OF THESE EXPRESSIONS AND LEFT A ZERO-HOLDER ROLE STANDING
  BESIDE IT** (F2-254, measured 2026-08-20). Executing F2-253's filed `module.bcm` block — re-derived
  against both databases first (F2-221) — `hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN')` guarded eight
  writes, `BCM_MANAGER` is held by **no user** in dev or prod, and probed live as `compliance` (who
  holds `BCM:WRITE`, `BCM:DELETE` *and* `BCM:APPROVE`) **13 of 13 answered 403** while `admin` reached
  the handler on every one: archiving a BCP binder or a quantitative BIA, starting an exercise run,
  instantiating a scenario, publishing an MTPD inheritance assessment, the whole 12-row
  process-vendor-dependency register, and five run-scoped writes.
  🔑🔑 **`BOARD_APPROVE_GUARD`'s javadoc names its own defect: F2-52 chose `EXECUTIVE_REVIEWER` because
  it *"already holds `BCM:APPROVE`, so it is a role the platform already trusts with BCM approvals"* —
  a fact about GRANTS. Whether a guard admits anybody is a fact about `iam.user_roles`.** A role's
  grant list tells you what the platform trusts it with; only its holders tell you whether it is
  anybody. The third instance is F2-161 on `ExerciseRuntimeController#start`, whose comment removed
  `FACILITATOR` for matching no row in `iam.roles` and *"admitting nobody"* while keeping a name with
  exactly that property. **Measured, not reasoned: the one live quantitative BIA in dev AND prod is
  still parked at `CFO_APPROVED` with `board_approved_at` NULL — exactly where F2-52's own javadoc
  found it, so its fix never moved the row it was written for.**
  ⚠⚠ **THE COMPOSED BUCKET HID FIVE OF THE THIRTEEN, so the filed count was half the module's
  real total.** `ROLE_GUARD_CENSUS.md` files a guard as COMPOSED when the expression MENTIONS
  `hasPermission`, not when the permission is **ANDed** — F2-253 filed that and this slice shows it
  hides real members rather than merely mislabelling them. 🔑 **A filed count is only as complete as
  the bucket that produced it**; when you split that kind, re-run §B2b, because **the count will go up**.
  ⚠⚠ **AND AN OWNERSHIP LEG CANNOT RESCUE A CREATE.** F2-161 added
  `or hasPermission(#id,'EXERCISE_RUN','OWNER')` so a facilitator could drive their own run; `#start`
  has no such leg **because there is no run yet to own**. So four of the five composed writes were
  carried by the facilitator and the one that creates the record was not — probed, `ranalyst` (no BCM
  grant of any kind) transitions the run they facilitate and is **403 on `#start`**. **A facilitator
  could drive an exercise run they were not allowed to create**, and the four seeded runs exist because
  an administrator made them. Isolate that leg with a planted run facilitated by a persona holding
  **no** module permission: the seeded facilitator is `rmanager`, who also holds `BCM:WRITE`, so
  driving them proves nothing about ownership.
  ⚠⚠ **BOTH PRE-EXISTING GATES FAILED ON THE CORRECT CONVERSION, AND BOTH WERE KEYED ON A
  SPELLING RATHER THAN ON THEIR OWN CLAIM** — the one failure a gate must never have (F2-214), because
  it trains the next reader to edit the assertion instead of the code. `ExerciseRunOwnershipGuardTest`
  asserted `!guard.contains("hasAnyRole(")` while its claim is *an oversight leg EXISTS*: it **failed on
  the change that made its own claim more true**, since the "oversight" it was checking for admitted the
  administrator alone and `hasPermission('BCM','WRITE')` admits three live roles (F2-197: a gate keyed on
  an identifier breaks on your own refactor). `BiaApprovalChainGuardTest`'s ghost detector ran a
  quoted-token regex over the whole expression, so `hasPermission('BCM','DELETE')` read as two roles
  called `BCM` and `DELETE` — 🔑 **a ghost-role gate that flags every role-to-permission conversion
  F2-05 exists to make** — and its body walked **every method** while its name says *"no STAGE"*, so it
  owned an opinion about routes it had never reviewed. **The control that matters for a detector repair
  is that the repaired detector still passes on the tree the original passed on**; both do.
  ⚠ **Convert the ROLE leg of a composed guard, never the whole expression**: the ownership clause is
  preserved verbatim, and its parameter name differs by controller (`#id` on the runtime console,
  `#runId` on the observer console — the recorded trap, where a copied constant answers **500**, not 403).
  ⚠ **Measure the audience diff before converting** (the V362 landmine), and state what is NOT admitted:
  `BCM:WRITE` is held by `GRC_ADMINISTRATOR`/`PLATFORM_ADMIN`/`RISK_MANAGER` and `BCM:DELETE` by the
  first two, both named roles hold both, so nobody is dropped — and `AUDITOR`/`VIEWER` hold
  `READ`+`EXPORT` and neither, so no reader-only role slips in. **Read guards were deliberately
  untouched: `CISO` is named on them and holds only `BCM:EXPORT`, so converting one would DROP a live
  role** (F2-253's asymmetry, and a gate leg now fails the build if somebody "finishes the sweep").
  ⚠⚠ **The two BIA approval tiers are NOT a mechanical sweep, and the blocker is now measured.**
  `cfo-approve` and `board-approve` are distinct tiers of one BIA; `BCM:APPROVE` is one permission and
  also includes `BCM_MANAGER`, stage **one** of the same chain. The four-eyes bar that would make sharing
  it safe is **unbuildable today**: `sys.columns` says `bcm.bia_quantitative` carries a caller-supplied
  `cfo_approver_name` and **no `board_approved_by` column at all**. 🔑 **The guard lockout and the
  missing attribution are one problem and F2-52 filed them as two** — F2-152's recorded blocker
  (*"nothing enforces that they are different people"*) is removable by building the thing it says is
  missing, which is F2-178's rule about re-measuring a blocker before believing its estimate.
  ⚙ **Five guessed names in one slice** — three tables (`bcp_binder_exports`, `exercise_scenario_library`,
  `business_processes`), one column (`run_id` for `exercise_run_id`) and one method (`listAssessments`).
  🔑 **The column one threw inside a `finally`, which is the worst place for it**: the cleanup died and
  left a planted row live. Resolve from `sys.tables`/`sys.columns`, and make the resolver print
  **near-misses** — that is what turned three of those into one line of output instead of three
  confident "no data" claims. ⚠ And **my own patch-script assertion failed on a correct edit**, because
  an insertion-style edit legitimately keeps its anchor (F2-206's rule, applied to an assertion).
- ⚠⚠ **A ROLE THAT EXISTS AND IS HELD BY NOBODY FAILS EXACTLY LIKE A GHOST, AND EVERY BUCKET
  IN THE INSTRUMENT WAS KEYED ON EXISTENCE** (F2-253, measured 2026-08-20). `hasAnyRole('X')` tests
  for the granted authority `ROLE_X`; a role assigned to no user grants it to nobody, and a role
  guard gets **no `PLATFORM_ADMIN` bypass** (`ModulePermissionEvaluator` is never called). The only
  difference from a ghost name is that an administrator can assign the role without a jar — **which
  is a remedy, not a defence, and says nothing about the platform as shipped.**
  `Appendix/ROLE_GUARD_CENSUS.md` has a bucket for exactly this, *"left admitting PLATFORM_ADMIN
  alone, by attrition"*, and it read **0**: it tests whether the other named roles are **absent from
  `iam.roles`**, never whether anybody **holds** them. The script computes `holders` at the top and
  uses it in one bucket (`thin`) that requires EVERY alive name to be unheld — and almost every guard
  here names `PLATFORM_ADMIN`, which is held, so `thin` and `admin_attrition` **cannot fire for
  them**. Measured: **24 pure guards admit nobody but a platform administrator**, identically in dev
  and prod (`bcm` 10, `privacy` 7, `ai` 3, `audit_committee` 3, `rrp` 1); now **17**, printed as
  §B2b. 🔑 **The question the report exists to answer was being asked of existence.**
  ⚠⚠ **The live one was the PDPL Article 30 sub-processor register, and the guard's own javadoc is
  the finding's witness.** `SubprocessorController.WRITE_GUARD` was
  `hasAnyRole('DPO','PLATFORM_ADMIN')` with `DPO` held by nobody, so probed live as `compliance` (who
  holds `PRIVACY:WRITE`) **all seven writes answered 403** — create, update, transition, **approve**,
  **review**, material-change, event ledger — while `admin` created at 201 and the read guard admitted
  the same caller at 200, over **8 live sub-processors**. F2-52 had audited *this exact pair*, written
  eleven lines correcting a ghost on `READ_GUARD` (*"Correcting a ghost widens access and dropping one
  does not"*) and left `WRITE_GUARD` **one line below** naming a zero-holder role. 🔑 **That rule is
  about existence and has no vocabulary for the case one line down** — F2-215's two-of-N, in a comment
  whose entire subject is this question.
  ⚠ **Measure the audience diff BEFORE converting, never after** (the V362 landmine). Here: converting
  loses nothing (`DPO` holds `PRIVACY:WRITE` too) and gains `GRC_ADMINISTRATOR`; `VIEWER` and
  `AUDITOR` hold `PRIVACY:READ` and **not** WRITE, so no reader-only role is admitted — probed, not
  reasoned. And **the module's own convention settles the target**: `DpiaController`,
  `ConsentRecordController` and `CyberPdplBridgeController` already guard with
  `hasPermission('PRIVACY',…)`, DPIA's Article 25 approve/reject included, so `approve`/`review`
  landing on `PRIVACY:WRITE` follows this module rather than inventing a rule for it.
  ⚠⚠ **Converting ONE leg of a read/write pair can open a write-without-read hole, and the obvious
  patch for it is a trap.** `READ_GUARD` names `CISO`, `COMPLIANCE_OFFICER` and `RISK_MANAGER` — all
  three **HELD**, none holding `PRIVACY:READ` — so converting it drops three live roles (`so_officer`
  reads at 200 today) and granting them the permission instead widens them from this register to the
  **whole privacy read surface**, the DSR queue and the breach register. An **owner decision**, filed.
  🔑🔑 **And the patch I reached for would have been invisible to the ratchet:**
  `RoleGuardConversionRatchetTest` line 929 is `boolean composed = HAS_PERMISSION.matcher(expr).find()`
  — **presence, not conjunction** — while the census prints that bucket as *"role leg ANDed onto a
  permission — already tight"* and the ratchet's javadoc counts it OUT of F2-05's backlog on that
  basis. Measured: **96 of the 99 are ORs.** Most are correct designs (`permission or admin`;
  F2-159/F2-161's `roles or record-OWNER`, which is narrower than a module permission), but **~10 still
  authorise their general population by role name while being labelled tight.** Adding
  `or hasPermission('PRIVACY','READ')` would have made a **widened** guard read as a tightening and
  passed every gate. **Filed** — splitting the kind changes a published artefact's schema.
  ⚠ **A hard zero can be a statement about the PACKAGED platform and false in the DEPLOYED one.**
  `BASELINE_GHOST_ROLE_NAMES = 0` is asserted against `ROLE_CATALOGUE`, whose own javadoc says it was
  *"read 2026-08-06 (58 rows), then 2026-08-07 (69)"* — from **dev**. Prod holds exactly **58**, and
  `CIO` and `CRO` are recited by guards and **absent from `iam.roles` there**. **Run a data-dependent
  census against BOTH databases**: this script hardcodes `DATABASE=TSS_GRC`, so every figure it has
  ever published is a statement about dev. Also measured: **30 of the 38 recited names are held by
  nobody in dev** (28 in prod), and **no** pure guard is closed to everyone — each also names a held
  role — so the harm is narrowness rather than closure, which is the distinction §B2b exists to make.
  ⚠ **A script that "nobody runs" may be one that CANNOT run.** This one prints `→` and `—`, a Windows
  console is cp1252, and every invocation died in `emit` with `UnicodeEncodeError` **before writing a
  line**. `sys.stdout.reconfigure(encoding="utf-8")`, the F2-244 remedy, one file over.
  ⚠ **Asserting a PROSE claim needs FLOWED javadoc, and the quote-stripper must not be line-scoped.**
  A claim wraps, so `contains()` only anchors on whichever fragment the author picked (F2-216); a
  `"[^"\n]*"` stripper cannot cross the newline inside a wrapped quotation, so the quotation survives
  and reads as a claim; and stripping quotes over a **whole Java file** pairs a javadoc citation's
  quote with an unrelated string literal's. **Isolate the comments, flow them, then strip.**
  ⚠ **A leg scoped to "routes whose guard already equals the target" can only pass.** My first draft
  checked for a role name only on routes whose guard was already the permission — an empty set on the
  pre-fix tree. **The pre-fix control is what exposed it** (its first run fired 3 of 5 legs on
  master and this was one of the two that did not; the rescoped gate fires 4 of 5), and the other non-firing leg is a deliberate **pin** whose tripwire is in
  the future — said out loud in the test, because "green on master" is otherwise indistinguishable
  from a regex that stopped matching.
  ⚙ **Filed:** the **17** remaining administrator-only guards, a work list with **no verdicts**
  (F2-188) — and note that two of their unheld roles, `BCM_MANAGER` (F2-161) and `DPO` (F2-209), have
  each been reported unheld before, **one endpoint at a time**, which is what a population looks like
  when nothing counts it. `BiaQuantitativeController#cfoApprove` and `#boardApprove` are **distinct
  approval tiers of one BIA**, so collapsing them onto one permission would retire a four-eyes
  distinction: this is not a mechanical sweep. Also `SubprocessorService.create` honouring a
  client-supplied `subprocessorCode` — F2-172(d)'s family on a reference an auditor cites, outside
  `ClientSuppliedReferenceRatchetTest`'s `setExternalId`/`setRefNumber` key (F2-248's headline that a
  gate key is a spelling).
- ⚠⚠ **A SOFT FOREIGN KEY WHOSE ID SPACE IS VALID IN TWO REGISTERS DEREFERENCES CLEANLY
  AGAINST THE WRONG ONE AND LIES** (F2-252, measured 2026-08-20). Executing F2-251's 28-member
  census (re-derived from the shipped gate's own stdout first — F2-221), the ranking said what the
  filing had not: **every remaining `change_mgmt` member holds 0 rows in dev and has NO TABLE in
  prod**, and the two nobody had read (`iam.access_review_items`, `socops.ir_playbook_run`) are
  empty in both — so the live weight was `cyber.cyber_controls :: framework_id + framework_name`,
  75 rows in dev and prod, filed *latent* by F2-247. Measuring its divergence against a **guessed**
  parent (`compliance.frameworks`) reported **75 of 75 rows divergent**, with example rows naming
  real frameworks: a complete, plausible, **entirely false** finding. Measured against the register
  the rows actually use (`cyber.cyber_frameworks`) the same 75 **agree**.
  🔑🔑 **F2-246 established that a scan locates and THE PAIRING IS THE MEASUREMENT; this is the
  mechanism — when two registers share an id space a wrong pairing cannot fail loudly. It joins,
  and it produces a name.** `cyber.cyber_frameworks` (6 rows, tenant-owned: NCA ECC / ISO 27001 /
  PCI DSS / NCA CCC / NCA CSCC / NCA OTCC) and `compliance.frameworks` (24 rows, **global — no
  `organisation_id`**, W2-1a-i's ratified regulator catalogue) both hold ids 1–6 and name
  **different frameworks**: id 2 is `'ISO 27001'` here and `'NCA Cloud Cybersecurity Controls'`
  there. **V342 added a FOREIGN KEY on `framework_id` for the five tables pointing at
  `compliance.frameworks` and this column got none, because it points at the other one** — so the
  schema records the disjointness and nothing reconciles it (F2-196's shape; picking a winner
  changes what the platform reports about NCA ECC coverage, an **owner decision**).
  ⚠⚠ **DERIVE THE PARENT COLUMN, NEVER PICK THE ONE WHOSE NAME LOOKS RIGHT.** Compare the child's
  label against **every text column of every candidate parent** and report the agreement rate. Done
  that way the answer was unambiguous (75/75 with `cyber_frameworks.name`, **0/75** with anything in
  `compliance.frameworks`) and it refuted **4 of 4** of my guessed pairings:
  `evidence.evidence_items.collector_name` holds an **office** (`'Cyber Risk & Compliance Officer /
  أخصائي…'`) not a person, `bcm.mtpd_inheritance_finding.vendor_name` a **role description**
  (`'Hyperscaler Cloud Region'`) not a vendor name, and `change_mgmt.change_approvals.approver_name`
  an office title — none is a copy of any parent column, i.e. **F2-251's third kind of census
  member**, where deriving the label would destroy information. 🔑 **A no-column-agrees answer is a
  finding, not a failed lookup.**
  ⚠⚠ **AND THE P1 WAS NOT IN THE FAMILY I CAME TO FIX — IT WAS ONE LINE ABOVE IT.**
  `CyberPage`'s `CONTROL_EMPTY` and `controlToReq` carried `frameworkName` and **no `frameworkId`**
  while `applyEditable` wrote both columns unconditionally, so **editing any control's title NULLED
  its framework link at HTTP 200** — on a column populated on **75 of 75** live rows in both
  databases — and left the label standing, so the register still rendered the framework and only a
  join could tell. F2-212's headline verbatim: **a wire-contract gate proves the TYPES agree and
  cannot prove the payload BUILDER sends what the type permits, and on a full-replace verb that gap
  is silent data destruction.** `CyberControlRequest` has always declared `frameworkId`.
  ⚠ **`FullReplaceUpdateContractTest` is green over it for two structural reasons, both worth
  knowing**: its hard-zero leg bans a method that loads a `T` and persists its own `T` **parameter**
  (this one persists the loaded row), and its coverage leg asks whether a written field is
  *declarable* by the frontend type — and `frameworkId` is. **Assert the ASSIGNMENT at the payload
  builder, never the field's presence on the type** (F2-212's own remedy, unapplied here).
  ⚠⚠ **A JAVADOC CERTIFYING A METHOD AGAINST ONE FAMILY READS AS CERTIFYING IT.**
  `applyEditable`'s comment was written by **F2-213** and says this is *"the one member of the family
  that was NOT losing data"* — true of the family F2-213 measured (a service persisting its own
  request object) and false of the defect its own client caused. It was converted *"anyway, because
  persisting the REQUEST OBJECT leaves the hazard latent rather than absent"* — correct reasoning
  that then listed the framework pair among the thirteen editable columns, which is what made the
  destruction unconditional. 🔑 **The next reader does not ask which family a certification covers.**
  ⚠ **Two more on the same pair, both probed live as `so_officer` against planted rows** (F2-206;
  `admin` proves nothing about authorization): a **nonexistent** framework id (999999) and **another
  tenant's** framework were stored at 200 — `CyberFrameworkRepository.findByIdAndOrganisationId` has
  existed since SEC-CYB-001 and this path never called it (F2-242, F2-215's two-of-N) — and a label
  contradicting the id was stored (`framework_id = 1` "NCA ECC" beside `'ISO 27001'`, F2-245).
  ⚙ **The fix shape**: `CyberFrameworkReferenceResolver` (`resolve`/`require`/`labelFor`, the
  F2-249/F2-250 primitive shape, named distinctly per F2-247's duplicate-bean landmine) plus
  `applyFrameworkReference` as the pair's **single writer** — the label DERIVED from the resolved
  framework, a contradiction **refused**, an **echo accepted** so a round-tripping form keeps working
  (F2-231/F2-234), a **bare label still legal** because both columns are nullable and live rows are
  made that way (F2-182), and an **omitted id on UPDATE leaving the stored link alone**, which is the
  destruction fix. Relinking by NAME was rejected: with two registers sharing an id space and
  carrying near-identical names, a name lookup is the least safe operation available.
  ⚠ **The dropdown was five hard-coded names and only THREE were real.** `FRAMEWORK_OPTIONS` offered
  NCA ECC / ISO 27001 / PCI DSS / **NIST CSF** / **SAMA CSF**; the register holds those first three
  plus **NCA CCC / NCA CSCC / NCA OTCC**. So two options named frameworks this register does not have
  (they live in the *other* one) and three registered frameworks with real controls filed against
  them **could not be selected at all** — F2-180's shape in both directions, with the page's own
  comment 400 lines below reading *"the register carries four NCA frameworks (ECC 29, CCC 24, …)"*.
  ⚙ **Measure the picker's guard before offering one** (F2-250): `GET /api/v1/cyber/frameworks` is
  `CYBER:READ` and the form is behind `CYBER:WRITE`, so it cannot 403 on open.
  ⚠⚠ **THE MUTATION HARNESS PRODUCED ONE *EQUIVALENT* MUTANT WORTH KEEPING RATHER THAN COUNTING.**
  Taking the tenant from `c.getOrganisationId()` instead of the caller's `orgId` **SURVIVED**,
  because `saveControl` stamps the org on the line before — so the two expressions hold the same
  value and the mutant is not wrong (F2-206: a surviving mutant is a hole only if the mutated code
  is actually WRONG). It is banned **structurally** anyway, because that is exactly how F2-246's
  404-on-every-linked-create was safe in three of four registers *"by accident of statement order"*:
  moving one line turns this equivalent mutant into that defect, and **no behaviour test would
  notice, because a refusal caused by a null tenant is indistinguishable from a correct refusal.**
  ⚠⚠ **`JAVA_HOME` UNSET MADE A STALE SUREFIRE REPORT READ AS A FRESH FAILURE.** On JDK 11
  `mvn -o test` dies in `spring-boot-maven-plugin:build-info` (*"class file version 61.0 … only
  recognizes up to 55.0"*) **before running anything**, leaves the previous run's `.txt` on disk, and
  prints `BUILD FAILURE` — so the four failures I read were the *earlier* run's and I nearly fixed
  them twice. 🔑 **`BUILD FAILURE` with no `Tests run:` line in the same output means nothing ran;
  the report on disk is then answering an older question.** Landmine #1 of this file, and the tell is
  the absence of a line rather than the presence of one.
  ⚠ **DevTools did not restart, and checking is what saved the probe.** After `mvn -o compile` the
  listener answered `/actuator/health` **UP** with `Restarting due to` appearing **0 times** in its
  log — so it was still serving pre-fix code, and a post-fix probe against it would have scored
  8/8 for the wrong reason. **Grep the log for the restart, not the port and not health** (F2-217).
  ⚙ **Filed:** which framework register is authoritative for a cyber control, and the FOREIGN KEY
  that depends on it (both **owner decisions**); the **23** remaining census members, now with this
  slice's measurements attached — **6 `change_mgmt` members are 0 rows in dev and absent from prod**,
  `iam.access_review_items :: role_id` and `socops.ir_playbook_run :: playbook_id` are **0 rows in
  both** (latent, no data), `ethics.aml_alerts :: rule_id` is **REFUTED as a member** (it is
  `nvarchar(100)`, an upstream engine's rule code with no register anywhere — F2-244's
  `annualPlanId` shape), and `admin.committee_action_items :: linked_entity_id` is **33 rows with 0
  ids and 15 labels**, polymorphic (`linked_entity_type` beside it, so no FK and no single resolver);
  `cyber.cyber_controls.framework_name` has **no `*_ar`** while `cyber_frameworks` carries `name_ar`,
  so the register's framework label is English-only on an Arabic-primary UI (house rule 7).
- ⚠⚠ **CAPTURING ONE PARTY'S ID CAN RETIRE THE ONLY BAR THAT COULD DECIDE ANOTHER —
  AND THE COMMIT THAT DOES IT LOOKS LIKE A STRICT IMPROVEMENT** (F2-251, measured 2026-08-20).
  Executing F2-250's census (re-derived first — 48 pairs, 32 unadjudicated — F2-221) and ranking
  by *id NULL while the label is populated* put `change_mgmt.change_requests`'s two people pairs
  top among the unblocked members. `change_owner_id` has **no writer anywhere in the platform**
  (NULL on 17 of 17 dev and 15 of 15 prod), so the owner is nameable only by label; and of the
  module's five self-review refusals, **three kept a hand-written or merged comparison and keyed
  the name fallback on something other than the party being compared** — the CAB vote (CM-C2) on
  **both ids being null**, `requireRatificationSeparation` (ADR-017 D-3) on **the ACTOR having no
  id**, and `assertNotSelfWaiver` plus `assertNotSelfSatisfaction` on a merged `Party(id, label)`. Since CE-15 stamps `requested_by_id` on every
  create and `GrcUserDetails` always carries an id, the owner's only bar was dead. Probed live as
  `rmanager` (`CHANGE:APPROVE`) against planted rows, 8/8 pre-fix: the change **owner cast a CAB
  vote on their own change at 201** and **ratified their own emergency change at 200** (on a legacy
  row, so no missing-id excuse), while the identical vote with `requested_by_id` NULLed answered
  **409**. 🔑🔑 **The two legs differ in exactly one column and the one that refuses
  is the one where LESS was captured — CE-15's improvement is what switched the owner's bar off.
  After adding a server-stamped id, re-read every predicate that consults the pair.**
  ⚠⚠ **THE MEASUREMENT DECIDES WHICH REMEDY THIS FAMILY WANTS, AND HERE IT WAS THE OPPOSITE
  OF THE LAST THREE SLICES'.** Identical in dev and prod: **0 of 34** requester/owner labels resolve
  to a platform user — they are offices and teams (`'CIO Office'`, `'Head of IAM'`, `'Chief People
  Officer'`, `'AppSec on-call'`) — and `create()`'s own CE-15 comment says the id is *who keyed the
  change in* while the name is *"whatever the tenant typed, since a team ('CloudOps') is a
  legitimate thing to record as the requesting party."* 🔑 **So the label is NOT a copy of the
  id's identity: deriving it (F2-245/F2-246/F2-250's fix) would DESTROY information, and the defect
  is in the READERS.** `ParentIdentityCopyContractTest`'s own framing — *"a parent id AND A COPY OF
  THAT PARENT'S IDENTITY"* — is what disguised it. **Ask whether the label is a copy before
  reaching for the fix that worked last time**, and note the corollary: because the two halves may
  legitimately name different parties, **both** must be compared and neither may switch the other
  off. The PUT storing a label that differs from the stamped id is **BY DESIGN**, asserted in both
  probe modes so a later slice cannot "fix" it.
  ⚠⚠ **THE PRIMITIVE PREDICTED THIS IN ITS OWN JAVADOC AND TWO OF THREE CALLERS BROKE THE
  RULE ANYWAY.** `ChangeSoD` exists as *"the platform's one answer to 'is the acting principal the
  same person as this named party?'"* and says a second copy *"lies the first time one copy learns
  something the others do not."* One of the three gates over the change's people was converted to
  it; **the two that were not are the two that lie** — the prediction and the failure in one file
  (F2-190/F2-177). `compareAny`'s javadoc even states the exact rule: *"build one party **per bar**
  rather than per person — `Party(id, null)` and `Party(null, label)` as separate entries, **never
  `Party(id, label)`** … a refusal removed by a change that looks like a tidy-up."*
  🔑 **A rule with no enforcement is a rule two of five sites follow.** Fix: one predicate,
  `selfReviewBars` / `cabDecisionBars` / `conditionOwnerBars`, an id bar and a name bar per person.
  ⚠⚠ **A REPORT BUILT TO ASK "CAN THIS GATE FIRE?" PUBLISHED FOUR GATES WHILE THE MODULE
  ENFORCED SIX, AND THE TWO ABSENT WERE THE TWO THAT COULD NOT FIRE.**
  `GET /api/v1/changes/sod-coverage` is CE-15's answer to F2-112. Both missing refusals were
  enforced, unit-tested and shipped, so nothing looked unfinished, and the one artefact that would
  have surfaced them had them outside its scope **by construction**. 🔑 **The instrument was
  real; its POPULATION was `ChangeSoD.GATES`** — the same narrowing this codebase has been caught
  by at a filename, a three-entry allow-list, a module name, an `extends` clause, a field-name list
  inside a fix and a claim shape in a gate. `ChangeSodGateCoverageContractTest` derives it from the
  refusals themselves (a method that throws on a `ChangeSoD` verdict), both directions, `GATES`
  now seven. ⚠ And `Coverage`'s javadoc called a label-only party *"UNVERIFIABLE"* — **false**,
  rule 2 compares it by name — which is **exactly the belief the two hand-written copies encoded
  as "legacy fallback only"**. A description that answers the question a reader was about to ask is
  how a wrong belief propagates into code (F2-184, in an instrument's own documentation).
  ⚠⚠ **MY OWN GATE REFUTED MY OWN MAP ON ITS FIRST RUN: 5 REFUSALS, NOT 3 — AND TWO OF MY
  THREE EXEMPTIONS ANSWERED A DIFFERENT QUESTION FROM THE ONE THE SCAN ASKS.** I wrote fluent
  reasons saying two refusals *"sit outside `selfReviewBars`"*: true, and irrelevant, because the
  derivation is keyed on *throwing on a `ChangeSoD` verdict* and both do. 🔑 **An exemption
  that answers a different question reads exactly like a correct exemption** — two in three fell,
  against this programme's usual ~2-in-10 (F2-188/F2-192/F2-204/F2-211/F2-250), and the **reverse**
  leg caught it (F2-199). ⚙ Correcting it paid at once: promoting `assertNotSelfSatisfaction` into
  the population surfaced a **fourth** merged site no reading had found — latent (`change_condition`
  holds 0 rows in dev and **does not exist in prod**), fixed anyway (F2-182/F2-215).
  ⚠⚠ **AND THE MUTATION HARNESS FOUND THIS SLICE'S OWN FINDING INSIDE MY OWN GATE.** The
  per-bar leg iterated `List.of("selfReviewBars", "cabDecisionBars")` — two names long because
  those were the two builders I had written by then — so re-merging the fourth site **SURVIVED the
  whole build**. 🔑 **A real rule over a population that is a list somebody wrote is the
  defect this slice is about, one level up.** Population derived by signature
  (`List<ChangeSoD.Party> \w+\(`); 10 killed / 0 survived / 0 anchor-miss after.
  ⚠ **Two more gate-leg errors, both failing on CORRECT code** (F2-206/F2-214): a name-bar
  detector keyed on the receiver `c.` could not see `cond.getOwnerName()` — **the receiver is not
  part of the claim** (F2-197's local-alias rule) — and the comment-stripper's negative control used
  a **complete** `/* … */` inside a `//` comment, which the two-pass form handles correctly; the
  corruption needs an **unterminated** delimiter plus a closer downstream.
  ⚠ **The pre-fix control tripped on my own quotation**, because this register's convention is
  that a correction QUOTES the sentence it replaces. Strip double-quoted spans first (F2-193), and
  note that **a detector whose claim is about prose cannot share the comment-stripping harness.**
  8/8 discriminate after.
  ⚠ **No escalation, and say so**: `CHANGE:APPROVE` guards the CAB vote, the ratification and the
  waiver alike, so nothing crossed a permission boundary — the object-level machine was the only
  control and two of its three bars were inert (F2-219's shape).
  ⚠ Four probe legs failed for reasons unrelated to their claims (F2-244): the transition body key
  is **`target`** not `to` (**resolve it from the controller** — F2-250 recorded this exact one),
  the CAB decision vocabulary is `APPROVE` not `APPROVED`, `EMERGENCY_APPROVED` needs
  `CHANGE:APPROVE` so `ranalyst` cannot drive it, and the emergency approval must be cast by a
  **third** persona or `selfApprover` fires first and masks the owner leg.
  ⚙ **Filed:** the **28** remaining census members — `change_mgmt` still holds **9** and they are
  a *different question*, **actor attribution** (F2-236), not parent references, with
  `change_approvals :: approver_id` (31 rows, **28 linked**) the one where divergence rather than
  unlinkability is measurable; **`change_owner_id` having no writer** is an owner decision (the
  labels are offices, so requiring a resolvable user refuses the register's own data, and no
  user-listing endpoint is reachable by `CHANGE:WRITE` so a picker would 403 — F2-250's
  measure-the-picker rule); two `sod-coverage` rows count a change `checkable` when only the
  requester has an id, because the two people share one row; and all three people columns are
  English-only free text with no `*_ar` (house rule 7). ⚙ Verified **already correct** while
  ranking, and worth knowing because they are the shape this family usually gets wrong:
  `admin.committee_meetings :: committee_id` (34/34 linked, NOT NULL) and
  `iam.sod_violations :: rule_id` (9/9) both **derive** their label from a resolved parent at every
  writer and bind purpose-built request records, so neither has an entity-bound route to re-open it.
- ⚠⚠ **A FIX AT A DEDICATED ROUTE IS NOT A FIX WHILE AN ADJACENT ROUTE WRITES THE SAME
  COLUMN — AND THE RESULT CAN BE WORSE THAN THE DEFECT** (F2-250, measured 2026-08-19).
  Re-deriving F2-249's 33-member census (F2-221) and ranking by the cheapest universal fact —
  *id NULL while the label is populated*, which needs **no parent pairing** (F2-246: a guessed
  pairing manufactures phantom findings) — pointed at `ethics.aml_alerts :: subject_id +
  subject_name`, **the SUBJECT pair of the entity whose ANALYST pair F2-249 closed eight lines
  away**. `AmlAlertService` had one blanket `apply()`, shared by create and update, that copied
  every column a caller sent. So `analystName` was writable and `analystId` was not: probed live,
  a `PUT` after a real `/assign` left `analyst_id` naming user 4 beside a label naming somebody
  who was never assigned. 🔑🔑 **That is WORSE than pre-F2-249, because before the fix
  there was no id for the label to contradict** — closing the dedicated route created the
  divergence the closure was written to prevent. **After converting a reference, grep the column
  and read every OTHER writer** (F2-246's rule that an update must not re-open what the create
  closed, arriving from the inside).
  ⚠⚠ **THE SAME METHOD ALSO FILED A SUSPICIOUS TRANSACTION REPORT.** `POST {"status":
  "STR_FILED"}` answered **201** with no disposition and no decider, while `/transition` answers
  **400** for the identical edge. 🔑 **`ServerStampedFieldContractTest` cannot see it and
  `AmlAlertService` appears nowhere in its output**: `transition(Long id, String target, …)`
  writes the status from a **scalar parameter**, so `status` never enters that gate's stamped set
  — F2-220's recorded transition-by-parameter blind spot, still filed as an unmeasured
  population, instantiated on a Saudi FIU filing.
  ⚠ **No escalation to report, and say so.** `auditor` holds `COMPLIANCE:WRITE`, the permission
  on the create, the PUT, `/assign` and `/transition` alike — the object-level controls were
  simply the only ones and this route sat beside them (F2-219's shape).
  🔑 **The unlinked half was the strongest form of F2-249's honest-but-unjoinable symptom yet:
  the link was AVAILABLE and was not made.** Measured identical in dev and prod — 16 registered
  KYC subjects, 10 live alerts, 2 linked (both agreeing) and **8 label-only whose labels match a
  registered subject EXACTLY**. There is even an index for the join (`ix_aml_alerts_subject_id`)
  and **no query anywhere that performs one**. The collector was the form: a free-text
  `subjectName` box, no picker, `subjectId` never sent — so every alert the platform's own screen
  produces is exactly that shape.
  ⚠ **`FK_aml_subject` EXISTS and does not help, for the reason worth restating**: a foreign key
  refuses a **nonexistent** id and knows nothing about tenancy, so another tenant's subject is a
  perfectly valid reference (F2-242/F2-243). Probed: stored at 201 pre-fix.
  ⚠⚠ **REMOVING THE ENTITY'S `status = "OPEN"` INITIALISER WAS NOT TIDYING — THE ECHO RULE
  CANNOT WORK WITHOUT IT.** On an entity binding, Jackson deserialises into `new AmlAlert()`, so an
  **omitted** status arrives as the initialiser's value and is indistinguishable from a supplied
  `"OPEN"` (F2-237). The echo rule that stops the PUT performing a transition would therefore have
  refused **every partial update** — a fix failing on correct code, the one failure a fix must
  never have (F2-214). It was also a **second literal** for a state `TRANSITIONS` already declares
  as its root (F2-217), so `INITIAL_STATUS` is now derived from the machine and self-checks that
  the root is unique. F2-240's remedy applies verbatim: **remove the initialiser, own the default
  on the create path**, and register the triple in
  `NeverFiringNullGuardContractTest.everyRemovedInitialiserHasAWriterOnTheCreatePath`.
  ⚙ **The primitive is `module/ethics/service/KycSubjectDirectory`** (`resolve` / `require` /
  `labelFor` / `requireLabel`, the same shape as F2-249's `TenantUserDirectory`), and
  `KycService.require` was **converted to delegate** rather than left as a second copy (F2-190) —
  only its 404 stays local, because those routes address a subject by path variable while a register
  naming one in its body gets a 400. **One predicate, two refusals.**
  ⚠ **A bare label stays legal and the schema decides that** (F2-246): `subject_id` nullable
  beside a nullable `subject_name`, and an alert can concern a party nobody has onboarded — which
  is why `counterparty_name` has no id at all. All 8 live rows are that shape, so `require` would
  break every request that works today (F2-182). What is closed is the **appearance** of a link.
  ⚠ **But editing only the LABEL of an already-linked row must be refused, not silently
  unlinked.** An entity binding cannot tell `subjectId: null` from an absent key, so "unlink" is
  not expressible on that route at all; treating a label edit as one quietly undoes a resolved
  reference, which is the defect rather than the fix.
  ⚙ **A picker WAS built here, and the difference from F2-249 is a measurement, not a
  preference.** `GET /api/v1/ethics/kyc` is `COMPLIANCE:READ` and **all 13 roles holding
  `COMPLIANCE:WRITE` also hold it** in dev and prod, so it cannot 403 on open — where F2-249's
  `/linkable-users` is `ADMIN:WRITE` and a picker would have 403'd, which is why that slice
  deliberately built none. **Measure the picker's guard before deciding whether to offer one**
  (F2-246: an affordance the caller cannot use is its own finding).
  ⚠⚠ **MY OWN GATE LEG WAS WRONG ABOUT THE CODEBASE, NOT THE REVERSE.** A ban on every `"OPEN"`
  literal in the service reported **4**; reading them found **one** real second literal (`assign`'s
  `"OPEN".equals(…)`) and **two legitimate** ones — the analytics bucket list and its
  `getOrDefault` key, which enumerate the whole vocabulary for a KPI strip and ask a **different**
  question ("awaiting disposition", deliberately narrower than the machine's non-terminal
  complement, so F2-196's complement rule does **not** apply). 🔑 **A red leg is a finding only
  if the code is actually wrong** (F2-206, on an assertion) — rescoped to the claim that was
  meant: *nothing compares a status against a literal root*.
  ⚠⚠ **THE PRE-FIX CONTROL REPORTED A DETECTOR SILENT, AND THE CAUSE WAS THE FINDING'S OWN
  MECHANISM.** The leg asked whether master's `AmlAlertService` contained `requireLabel(` at all —
  and it does: `directory.requireLabel(…)`, which **F2-249 added for the analyst**. So the control
  tripped on the very artefact that made this half look covered (F2-215's two-of-N). 🔑 **A
  detector scoped to "is there a resolver in this file" cannot see a second reference in the same
  file that has none** — scope it to the reference (F2-192). 8/8 discriminate after.
  ⚠ **A method-body helper silently returned the WRONG method.** `body()` walks to the first brace
  after a signature, and a **field initialiser has none**, so asserting against the `TRANSITIONS`
  declaration read the next method's body — a leg failing for a reason unrelated to its claim
  (F2-244). Slice to the initialiser instead.
  ⚠ **A probe leg passed for the wrong reason**: `{"to": "STR_FILED"}` got a 400 reading *"target
  is required"* — the wrong body key. **Resolve the key from the controller** (the guessed-route
  family since F2-198); with `target` it refuses for the right reason.
  ⚠⚠ **THE EMPTY-HEREDOC LANDMINE FIRED AGAIN AND THE RULE IS WIDER THAN IT READS: a POINTLESS
  `python - <<'X'` with nothing in it hangs the shell for the full timeout and SWALLOWS EVERY
  COMMAND CHAINED BEHIND IT.** Two minutes lost and a `sed` chain silently unexecuted. The
  recorded rule is about escapes; the hazard is the heredoc itself. **Use the Write/Edit tools.**
  ⚠⚠ **`BUILD SUCCESS` OVER AN APP THAT NEVER BOUND A PORT, AND `/actuator/health` SAID UP.**
  `mvn -o clean package` did **not** kill the old dev listener this time; it deleted and rewrote
  `target/classes` underneath it. The relaunch logged *"Port 8084 was already in use"* under a
  `BUILD SUCCESS`, and the surviving PID answered health **UP** while serving code from a
  directory that had been replaced. 🔑 **Health UP is not proof the listener serves your tree:
  poll health, then ask who owns the port, then restart** — a stale listener is the most
  misleading possible input to a fix-verification probe (F2-217).
  ⚙ **Filed:** the **32** remaining census members, with this slice's own measurements attached —
  the recorded blockers re-confirmed (`socops.sbom_component` 20/20 and `socops.appsec_finding`
  10/10 label-only and **unwireable**; `bcm.dr_plans` 16/16 and 8/16, owner-blocked;
  `cyber.vulnerabilities :: asset_id` 15/20 and `cyber.cyber_incidents :: affected_asset_id` 12/24,
  blocked on which disjoint asset register is the target) plus three newly measured and **not**
  blocked — `admin.committee_action_items :: linked_entity_id` (15 of 33) is **polymorphic**
  (`linked_entity_type` beside the id, so no FK and no single resolver: a different sub-shape),
  `bcm.strategy_roi_analysis :: strategy_id` 3/3 and `risk.emerging_risks :: linked_risk_id` 2/10.
  Also: `change_mgmt.change_comments :: author_id` and `ethics.case_actions :: actor_id` are
  **already closed by F2-248** and still read `NEEDS-READING` in this census, because that slice
  adjudicated a different instrument — bookkeeping, but **a member listed as unread that has been
  read is indistinguishable from one nobody looked at**; `ethics.aml_alerts.alert_code` is
  caller-named with **no minter anywhere and no UNIQUE index** (probed: a duplicate of a real code
  is accepted — F2-172(d)'s family, outside `ClientSuppliedReferenceRatchetTest`'s
  `setExternalId`/`setRefNumber` key, F2-248's gate-key-is-a-spelling headline); `sla_due_at` has
  **no server computation at all** (F2-202); the table has **zero CHECK constraints**, now cheap
  since its open status writer is closed (F2-183); the **8 live unlinked alerts**, mechanically
  repairable since all 8 labels match exactly but rewriting live AML rows in two databases is an
  **owner decision**; and `subject_name` has no `*_ar` (house rule 7).
- ⚠⚠ **A REGISTER THAT DECLARES A PERSON ID BESIDE A LABEL AND WRITES ONLY THE LABEL FAILS
  IN TWO OPPOSITE WAYS, AND THEY LOOK NOTHING LIKE EACH OTHER** (F2-249, measured 2026-08-19).
  Executing F2-248's work list, the eight actor-attribution members whose parent is a **person**
  split by where their label came from. From the **principal**: the record is honest and
  **unjoinable** — `bcm.plan_review_attestations.attested_by_id` **0 of 5**,
  `bcm.bcp_plans.last_attested_by_id` **0 of 8**, `bcm.plan_acknowledgements.distributed_by_id`
  **0 of 8**, one of them naming `'Khalid Al-Mutairi'`, who resolves to nobody (F2-207's cast, in a
  *server* register). From the **caller**: the record is a **claim** — `ethics.aml_alerts.analyst_id`
  **0 of 10** and `ethics.cases.investigator_id` seed-only, with **0 of 3 analyst labels and 0 of 14
  investigator labels resolving to any real user**. Probed live as `auditor` (`COMPLIANCE:WRITE`): an
  AML alert moved `OPEN → INVESTIGATING` naming an analyst who does not exist, and a misconduct case
  entered `INVESTIGATION` with an invented investigator, both **HTTP 200**.
  🔑🔑 **F2-245 established that a description travelling BESIDE an id can describe the wrong row;
  this is the description travelling ALONE — and a reader who fixes the honest-but-unjoinable symptom
  will not recognise the claim symptom, because only one of the two looks like a defect.**
  ⚠⚠ **THE SCREEN WAS THE COLLECTOR, AND THE SLICE BEFORE IT DELETED THE PROMPT NEXT DOOR.**
  `EthicsPage` called `window.prompt(t('amlAssignPrompt'))` — it asked a human to **type** the
  analyst's name. F2-223 removed this page's `amlDecidedByPrompt` and left this one, and
  `decidedByName` twenty lines below in the same service has resolved from `CurrentActor` ever since.
  🔑 **A reader who finds `CurrentActor` in a service does not then ask which of its OTHER
  people-columns still comes from the body** (F2-215). CH-8 ("replace window.prompt/confirm") is a
  completed task and this prompt survived it — **grep `window.prompt` when you touch an attribution.**
  ⚠⚠ **FOUR HAND-WRITTEN COPIES OF resolve-a-user-in-my-tenant WERE LIVE AND TWO DISAGREED.**
  `CoiService.requireOwnUser`, `CommitteeMemberRegistryService.requireUserInTenant`,
  `SignatureService.resolveSignerName` and the committee registry's own error text — and two differed
  on whether a **BLANK** `full_name_en` falls back to the username, so a user with an empty display
  name rendered as an empty cell in one place and as their username in the other. **Use
  `module/iam/service/TenantUserDirectory`** (`resolve` / `require` / `labelFor` / `requireLabel`);
  do not hand-roll a fifth (F2-190). It lives in `module/iam` because `iam` owns `users` and
  `X → iam` is an ordinary business edge — **`common/` would be worse, because it sits OUTSIDE
  `ModuleGraphRatchetTest`'s population entirely, so the dependency would be neither forbidden nor
  measured.**
  ⚠⚠ **A BARE LABEL STAYS LEGAL, AND THE SCHEMA DECIDES THAT.** A nullable id *beside* a nullable
  name states that an unresolvable party is representable (F2-246), and the live labels prove it:
  `'Financial Crime Unit'`, `'Internal Audit'`, `'External Investigator'`. An external investigator
  has no login **by definition**, so requiring a resolvable user would refuse exactly the ethics cases
  where independence matters most — a fix failing on correct usage (F2-214). Contrast F2-244, which
  **refused**: `coi_recusal_event` declares a NOT NULL id and **no name column at all**. What to close
  is the **appearance** of a link: resolve the id in-tenant, **derive** the label from what resolved,
  refuse a label that contradicts it, and accept an **echo** (F2-231/F2-234).
  ⚠ **The gate's load-bearing leg must be the POSITIVE one.** On a path that never wrote the field,
  *"the service does not write X from the request"* is **vacuous** — there is no setter for a negative
  scan to find, so that leg passes forever over the defect (F2-247). Assert the id half **is** written,
  from the resolver or the principal, **and that it is not merely cleared**: dropping
  `setInvestigatorId(userId)` SURVIVED because the file still held `setInvestigatorId(null)` in the
  no-assignee branch, so a presence check was satisfied by the clear.
  ⚠ **An echo comparison is case-INSENSITIVE, so assert which SPELLING is stored.**
  `setAnalystName(label != null ? label : derived)` survived until a leg pinned the derived casing:
  with the contradiction check in place the mutation's only observable effect is the caller's casing
  winning, i.e. a register whose spelling of a person's name depends on how a caller typed it
  (F2-179's collation family, one character deep).
  ⚠ **A hard zero on the obvious rule was MEASURED AND REJECTED**: *no one may hand-spell a
  tenant-narrowed user load* returns **25 sites across 9 files** and most are correct. Measuring
  before writing the assertion is the only reason it did not ship as a gate failing on working code.
  ⚠ **Converting a predicate breaks the gate that asserted its LOCATION, and the response is to
  follow the delegation** — never to relax the assertion, which always passes (F2-206/F2-235).
  F2-244's leg asserted the tenant filter inside `CoiService`'s own body; because the predicate is now
  split across the primitive's `resolve` (tenant) and `require` (refusal), **each half is asserted
  where it actually lives** (a name-keyed lookup pointed at one method reads a CALL for a DECLARATION).
  ⚠ **19 existing tests failed on `directory is null`, and the fix is a REAL primitive over the
  mocked repository, never a mock of it** — a mocked resolver returns empty and every refusal leg goes
  green asserting nothing (F2-237). `ReflectionTestUtils.setField` is the house idiom for `@InjectMocks`.
  ⚙ **No picker was built, deliberately**: `/api/v1/committees/registry/linkable-users` is
  `ADMIN:WRITE` and the AML operator holds `COMPLIANCE:WRITE`, so a picker would **403 on open** —
  offering an affordance the caller cannot use is F2-246's own finding. The id path stays on the wire,
  and the register discloses an unlinked assignment in three states (F2-209).
  ⚠ **A probe leg failed for a reason unrelated to its claim**: the investigator id-path leg re-used
  the case the previous leg had moved to `INVESTIGATION`, and the machine has no
  `INVESTIGATION → INVESTIGATION` edge, so the 400 read exactly like the fix refusing a valid id
  (F2-244). **Plant a fresh row per transition leg.**
  ⚙ **Filed:** the **33** remaining census members, now weighted toward **register** references
  (`cyber.vulnerabilities :: asset_id`, `cyber.cyber_incidents :: affected_asset_id`, `socops.*`,
  `bcm.dr_plans`); **`evidence.evidence_items :: collector` has NO writer of either half** (77 of 78
  rows populated by the seed — honest data behind no code, so it cannot regress and cannot be
  maintained); **no user-listing endpoint is reachable by `COMPLIANCE:WRITE`**, so neither ethics
  register can offer a picker (an owner decision about who may enumerate colleagues); the unresolvable
  live labels (**owner decision** — repair means deciding per row which real person an office label
  meant, in two databases, with no mapping); and neither `analyst_name` nor `investigator_name` has an
  `*_ar`, so both are English-only on an Arabic-primary UI (house rule 7).
- ⚠⚠ **A GENERALISATION IS ITSELF A VOCABULARY: F2-217 REPLACED A LITERAL FIELD-NAME LIST WITH A
  *SHAPE*, AND THE SHAPE'S STEMS ARE A LITERAL LIST** (F2-248, measured 2026-08-19).
  `MassAssignmentRatchetTest` ranks every entity-bound write, and `AGENT_SHAPE` is an alternation of
  **twelve** agent nouns while `AUTHORITY_SHAPE` is **twenty-nine** verbs suffixed `By`/`At`.
  `author` and `actor` are ordinary English agent nouns ending in `-or` and are in neither, so
  `change_mgmt.change_comments :: author_id + author_name` and `ethics.case_actions :: actor_id +
  actor_name` both ranked **LOW** — of 214 entity-bound writes only **five** were in that tier, and
  these were two of them. 🔑 **The ranking filed its two forgeable attributions in its smallest and
  least-attended bucket, and nobody re-reads a rule that looks like a rule.** F2-217's own headline
  one level up; assume a fourth spelling exists, because a **third** turned up in the same slice.
  ⚠⚠ **THE SAME DEFECT PRESENTS AS OPPOSITE SYMPTOMS, AND BOTH ARE THE ABSENCE OF A SERVER-OWNED
  HALF.** The comment thread bound the entity with `if (getAuthorName() == null)
  setAuthorName("Unknown")` in front — a **default, not a guard** (F2-218) — so probed live as
  `ranalyst` (a RISK_ANALYST, the least-privileged of seven `CHANGE:WRITE` holders) a comment on a
  CAB-approved change stored **`author_name = 'so_officer'`, the CISO, at HTTP 201**, and `authorId:
  8` bound onto a column **NULL on 45/45 dev and 46/46 prod rows**. The ethics action log bound the
  entity and touched neither half, and because `EthicsPage` sends **no actor at all**, every action
  recorded through the platform's own screen stored `actor_name = NULL, actor_id = NULL` — the action
  log of a whistleblowing investigation recording what happened and when, and not who did it.
  🔑 **A register that does not own WHO either stores a claim or stores nothing, and neither is an
  attribution.** ⚠ **And PROD HOLDS A ROW WHOSE AUTHOR IS THE LITERAL STRING `'You'`** — the
  frontend's `getUser()?.username ?? 'You'` fallback having fired, under a comment reading *"CH-8:
  attribute the comment to the signed-in user, not a hardcoded 'You'"*, three characters from the
  fallback it claims to have removed (F2-184).
  ⚠⚠ **`action_at` is `insertable = false`, so the DATABASE owned WHEN while the caller owned WHO** —
  F2-223's split attribution arriving through the one filling mechanism a setter-matching scan cannot
  see (F2-226's fourth). That is why no existing instrument reported it, and it is the reason to check
  the *column mapping* and not just the service when asking who owns a timestamp.
  ⚠ 🔑 **The audit row contradicted itself on one line**: `platform.audit_log.user_username` held
  `ranalyst` — the framework already knew who acted — while the message the module composed read
  *"Comment added to change 273 by You"*. F2-223's quoting-the-claim rule, with the sharper twist
  that **the truth was already in the row beside it**. Read the audit trail's own actor column before
  believing a module's message about who did something.
  ⚠⚠ **A SHORT AGENT-NOUN STEM CANNOT SHARE A LONG ONE'S PREFIX RULE.** `AGENT_SHAPE`'s prefix is
  `(?:.*[a-z0-9])?`, which matches a single lower-case letter — so putting `actor` there made it
  match inside `f·actor·Name`, and `contractorName`/`benefactorId` are the same trap. The twelve
  original stems are long and distinctive enough never to have hit it, which is exactly why the rule
  was safe to write and is **not** safe to extend. The two short stems get their own
  camelCase-anchored `SHORT_AGENT_SHAPE`, with **9 must-pair and 8 must-not-pair** cases: a widening
  too wide silently RE-TIERS the platform, one too narrow silently SHRINKS the work list. Caught on
  the first run by the leg's **negative half** — the direction nobody writes first.
  ⚠ **Fix at the wire, not with a guard**: `ChangeCommentRequest(body)` and
  `CaseActionRequest(actionType, title, description, isConfidential)` replace the entity bindings, so
  the actor fields are **absent** rather than validated — a field the bound type does not declare can
  never arrive (F2-233), which `MassAssignmentRatchetTest` already demanded of
  `ChangeConditionRequest` **by name**: *"a scrub is a list of fields somebody remembered, a DTO is
  the set of fields that exist."* Each service resolves from **its own module's** declared primitive
  (`ChangeSoD.actor()` in `change_mgmt`, whose javadoc states the rule — *"the id is the part a gate
  can stand on, and only the server can know it"* — and `CurrentActor` in `ethics`), never a second
  mechanism beside an existing one (F2-190). Both **refuse** rather than record a blank (F2-236).
  ⚠⚠ **A THIRD UNSPELLED NAME, FOUND BY READING THE ONE ROW THE WIDENING SURFACED — AND IT IS AN ACT
  VERB, NOT AN AGENT NOUN.** `admin.committee_meeting_papers.circulated_by` is a user id, bound from
  the request, never written by the service, and **NULL on all 21 live rows in both databases**;
  `circulated` is in none of `AUTHORITY_SHAPE`'s twenty-nine verbs. 🔑 **Two vocabularies found short
  in one slice by two different routes — a widening, then reading its single new row.**
  ⚠ **`authorName` beside it is BY DESIGN, verified rather than reasoned**: there is no `author_id`
  column, `author_role` holds an office (`'Company Secretary'`), and **21 of 21** rows in both
  databases name a *body* (`'COM-BOARD Secretariat'`) resolving to no `iam.users` row. A committee
  paper's author is its **preparer** — a third party — and **the schema's own separation of
  `circulated_by` from `author_name` is what settles it** (F2-236's `counterpartySigner` precedent,
  F2-247's rule that a table declaring an id beside one label and only a label beside another is
  stating which reference is meant to resolve). `circulatedAt` stays a **fallback**, stated rather
  than glossed: back-dating a past circulation is legitimate and F2-233(d) established that a
  fallback is not an ownership claim, so that endpoint was never the split-attribution shape — it was
  a column with no writer.
  ⚠⚠ **MY NEW CENSUS LEG STARTED AS A HARD ZERO AND THE *ASSERTION* WAS THE THING THAT WAS WRONG.**
  Asking which bound **request** types declare an actor field reports **28**, and a large share are
  **round-trip** DTOs — `BoardPackDto`, `CapaTaskDto`, `DsrCaseDto`, `NcaEccFilingDto` — used for the
  response as well as the request. Such a type **must** declare `decidedBy` in order to RETURN it,
  and whether the write path honours the inbound value is `ServerStampedFieldContractTest`'s
  question. 🔑 **The question conflated inbound-only types with round-trip ones, so a zero over it
  would fail on correct code — the one thing a gate must never do** (F2-214). It ships decrease-only
  with **no verdicts** (F2-188), and F2-204's rule lands on my own leg: when a figure cannot honestly
  be zero, ask whether the QUESTION was wrong. ⚙ The leg exists at all because **the entity-bound
  ranking cannot see a request DTO, and binding a narrow record is precisely the recommended fix** —
  so without it, re-adding `authorName` to `ChangeCommentRequest` would go unnoticed by every gate.
  ⚠ **The vocabulary is SHARED, not copied.** The new leg calls `isAuthorityField` directly; writing
  a second agent-noun list in a new gate class would commit the very defect being fixed (F2-177), and
  the whole finding is that F2-217's shape was still a list. One declaration, two readers.
  ⚠⚠ **THE MUTATION HARNESS FOUND A REAL HOLE IN MY OWN GATE, AND IT IS F2-217'S FAILURE MODE
  REINTRODUCED ONE STEM LATER.** Deleting `[Cc]irculated` from the verb list **SURVIVED**: I had
  added the two agent nouns to the anti-vacuity `measuredOnRealEntities` list and forgotten the verb,
  so the mutation drops the committee-paper row out of CRIT — which **lowers** the count, and a `<=`
  ceiling reads that as progress. That leg exists precisely because a ceiling cannot see a detector
  that stopped matching. **When you widen a vocabulary, add every new token to the anti-vacuity list,
  not just the ones that produced the finding.** ⚙ One mutation was **withdrawn as invalid** rather
  than counted: it weakened one of my own assertions, and you can always delete an assertion
  (F2-206, F2-235).
  ⚠ **`ChangeCommentProvenanceTest` is the twin `CabVoteProvenanceTest` never got** — DF-4's
  forged-actor test for the CAB-approval path in the *same service*, while the comment thread beside
  it had **no test of any kind**. 🔑 **A path with no test is not a path somebody decided was safe.**
  ⚙ **Filed:** the **38** remaining census members with the classification carried forward (**20**
  actor-attribution, **18** register-reference — different questions), the two `socops` members still
  **unwireable** and `bcm.dr_plans` still owner-blocked; the **28-type** request-DTO census;
  `ethics.case_actions.action_type` free text with **no CHECK** in a schema that has none — cheap now
  that this slice closed the register's only open writer (F2-183), but blocked on F2-221's finding
  that **11 of 30 ethics cases already hold a status no route can advance**; the **45 dev / 46 prod
  comment rows and 15 action rows whose author is an office label** plus the prod `'You'` row (repair
  rewrites live governance rows in two databases with no office-to-person mapping — **owner
  decision**); and `change_mgmt.change_condition :: owner_id`, which has no writer at all, holds **0
  rows in dev** and **does not exist in prod** (~35 migrations behind), where an owner is an
  *assignment* (F2-226) so free text is defensible and the unwritten id is F2-247's unlinkability
  shape, latent with no data.
- ⚠⚠ **TWO SPRING BEANS WITH THE SAME SIMPLE NAME STOP THE APPLICATION STARTING, AND THE WHOLE
  UNIT SUITE PASSES OVER IT** (F2-247, measured 2026-08-19). Spring's default bean name is the
  decapitalised simple class name, so a second `@Component VendorReferenceResolver` — a legitimate
  one, over `tprm.vendors` where F2-246's serves `scm.vendors`, and ADR-012/018 forbid sharing the
  primitive — made component scanning throw `ConflictingBeanDefinitionException` while parsing the
  configuration class. **4,229 tests green, then no port bound.** Nothing in the suite boots a Spring
  context, and `mvn -o clean package` is deliberately hermetic (no database, no secrets), so the
  build never starts the app either. 🔑 **The only instrument that could see it was launching it,
  and a launch is not part of the build** — under a Maven `BUILD SUCCESS`, per the recorded landmine
  that `BUILD SUCCESS` is not a start signal for `spring-boot:run`. F2-222 filed this shape for
  *entities* and measured it latent (15 of 433 names in two packages); for a bean it is fatal.
  `UniqueBeanNameContractTest` holds it at a hard zero over **744** component-scanned beans, with no
  exemption map — an explicit `@Component("name")` is always available, so there is no correct code
  for it to fail on (F2-214). Prefer the distinguishing NAME over the alias: when two modules each
  resolve their own register, a reader reaching for whichever they found first is the real hazard.
  ⚠⚠ **`io.open(path, "w")` TRUNCATES ON OPEN, so an exception during the write leaves the file at
  ZERO BYTES — and that destroyed the 1.79 MB findings register.** The write raised
  `UnicodeEncodeError` (lone surrogates: `\ud83d\udd11` written as two escapes is two unpaired
  surrogates, not the character), by which point the file was already empty. 🔑 **Every subsequent
  grep then answered "not found", which reads as the anchor having moved rather than as the file
  having been destroyed** — three tool calls went into hunting a phantom rename. It was tracked and
  uncommitted, so `git checkout --` restored it. **Build the content, write a TEMP file, assert the
  round-trip, then `os.replace`.** ⚠ Its sibling in the same session: a patch script whose write
  handle was never closed, so its own verifying `assert` re-read an unflushed file, found nothing and
  passed vacuously. **Use a context manager, and never let a script certify its own write.**
  ⚠⚠ **AN ENTITY-BOUND CREATE HAS NO SETTER FOR A SCAN TO FIND, so "the service does not write X
  from the request" is VACUOUS over it.** My first gate leg asserted exactly that and the pre-fix
  control reported it **SILENT ON MASTER** — Jackson had already bound `vendorId` and `vendorName`
  onto the entity and the service mentioned neither, so there was nothing for `setX(req.…)` to
  match, and the leg would have passed forever over the defect it was written for. 🔑 **On an
  entity-bound endpoint the load-bearing assertion has to be the POSITIVE** — the field must be
  *overwritten from the resolver* — which is the only shape that can see a create doing nothing at
  all. This is why F2-206's introspector exists for the server-owned fields; a business foreign key
  has no such mechanism.
  ⚠ **`vendor_country` looked like a third identity copy and deriving it would have CORRUPTED the
  column**: the assessment stores `'SA'` where `tprm.vendors` stores `'Saudi Arabia'` — two notations
  for one concept (F2-203's shape) — and the form caps the field at `maxLength: 3`. 🔑 **Before
  deriving a second copy from a resolved parent, check the two sides use the same notation**; the
  form's own cap was the tell.
  ⚙ **Filed:** the **40**-member work list (the two `socops` `application_id` members are
  **unwireable** — no application register exists anywhere, F2-204's shape — and `bcm.dr_plans` needs
  an owner decision on both halves, with neither of its id columns having *any* writer);
  `cyber.cyber_controls` latent (both halves caller-supplied, no tenant check on the framework, 0
  rows affected today); `ethics.aml_alerts :: rule_name` written from the source rather than a
  resolved rule; the **8 unlinked live rows** and the missing FOREIGN KEY blocked on them; and **two
  vendor registers for one concept** (`scm.vendors` 31, `tprm.vendors` 28) hence two resolvers —
  **that the shape recurs once per module BECAUSE the primitive cannot be shared is the structural
  finding**, an owner decision.
- ⚠⚠ **A FORM THAT COLLECTS A *LABEL* INSTEAD OF A *LINK* PRODUCES A ROW NOTHING CAN JOIN,
  AND THE WIRE CONTRACT DECLARED IT: THE LABEL WAS MANDATORY AND THE LINK OPTIONAL**
  (F2-246, measured 2026-08-19). Executing F2-245's filed work list — re-derived first
  (F2-221) — gave two results before any code was read: **22 of the 24 parent-register pairs
  carry no FOREIGN KEY at all**, and the live half of this family is not divergence but **a
  stored parent label with no id**. Four `module/scm` registers share one concept, the vendor:
  `exit_plans` 7 of 13, `alternate_suppliers` 10 of 16, `supplier_resilience_tests` 8 of 11,
  `fourth_party_exposures` 7 of 13 — **32 of 53 live rows, byte-identical in dev and prod**,
  and the labels are annotated prose (`'AWS (managed cloud services)'`, `'TCS Saudi Arabia
  (payroll BPO)'`), which is *why* they can never join. Divergence is **0 of the 21 linked
  rows**, unlike F2-245's 11 of 11, so here the divergence is latent and the unlinked half is
  live. 🔑 **All four create DTOs read `Long <x>VendorId` (no annotation) beside `@NotBlank
  @Size(max = 255) String <x>VendorName`**, and all four create forms offered a `required:
  true` free-text vendor box with no picker and sent no id — so **the rows the platform's own
  screens produce are exactly the unlinked ones**, and the 21 linked came from the seed.
  `ExitPlanRepository.findByOrganisationIdAndVendorId…` is a coverage query over that id, so
  *"which vendors have an exit plan?"* — SAMA outsourcing — could never return 7 of 13 real
  plans; both coverage clients are also declared dark, so the query exists and 60% of the data
  could not answer it. Probed live as `ranalyst` against a planted org-2 victim, 17/17: a
  label-only create 201, another tenant's vendor 201, `999999` 201, a lying label stored
  beside a real id 201.
  🔑🔑 **WHICH HALF IS WRONG IS SETTLED BY THE SCHEMA, NOT BY TASTE.** `AlternateSupplier`
  declares `primary_vendor_id` **and** `primary_vendor_name` for the vendor being replaced and
  for the replacement only `alternate_vendor_name` — **there is no `alternate_vendor_id`
  column anywhere**; `FourthPartyExposure` is the same shape. So a substitute or downstream
  party is deliberately free text because it need not be registered, while the **subject**
  vendor has an id column *and* a coverage query and is meant to resolve. **When a table
  declares an id beside a label for one party and only a label for another, the schema is
  stating which reference is supposed to be resolvable.**
  ⚠ **Keep the label where there is no id.** Nothing refuses a label-only create today and 32
  live rows were made that way, so `require` would break working requests (F2-182); the row is
  disclosed as *unlinked* instead (F2-209's three states). The **subject** label is relaxed on
  the wire while the two id-less ones stay mandatory, asserted in both directions so "relax
  them too" fails.
  ⚠ **The one UPDATE that writes a vendor label is how a linked row's copy diverges after the
  fact.** `FourthPartyExposureService#update` declares `primaryVendorName` and no id, so it now
  accepts an **echo** and refuses a **change** (F2-231/F2-234) — a form that round-trips the
  whole row keeps working, and an update cannot re-open what the create closed.
  ⚠⚠ **MY OWN FIX WAS WRONG IN 1 OF THE 4 REGISTERS AND ONLY THE LIVE PROBE CAUGHT IT.**
  `FourthPartyExposureService` resolved the vendor **18 lines above** `e.setOrganisationId(...)`,
  so the tenant argument was null, the filter could never match, and **every LINKED create
  answered 404**; the other three were safe by accident of statement order. 🔑 **The two probe
  legs that expect a REFUSAL passed on the broken code — a refusal caused by a null tenant is
  indistinguishable from a correct one — so only the leg asserting the DERIVED label could see
  it.** Fix the class, not the instance: every site reads the tenant from the request, and a
  gate leg bans taking it from the half-built row.
  ⚠ **Two of my own gate legs failed on CORRECT code**, the one failure a gate must never have
  (F2-214): one asserted the word `organisationId` over comment-stripped source where the code
  says `getOrganisationId`, and one banned a load shape `stampVendorOnboarding` (F2-242's
  deliberately scoped write helper) uses legitimately. F2-206's rule on an assertion — **a red
  leg is a finding only if the code is actually wrong** — and scoping the ban to the *refusal*
  made it true, which then exposed **three hand-spelled copies** of `requireOwnVendor`'s
  predicate in `getVendor`/`updateVendor`/`deleteVendor`, converted rather than exempted.
  ⚠ **Relaxing a type on the RESPONSE is a different claim from relaxing it on the REQUEST.**
  My first type edit hit all four response interfaces; `vendor_name` is `nullable = false`, so
  the server always sends a label and only the create request may omit it. `npx tsc` found it
  at a display site (`TprmConsolidatedOverview` passing the name into a `string` parameter).
  ⚠ **A mocked collaborator would have silenced six existing cross-tenant legs.**
  `ScmServiceTest` now gets a **real** `VendorReferenceResolver` over its mocked repository,
  because a mock returns null and every F2-242 refusal leg would have gone green asserting
  nothing (F2-237).
  ⚠⚠ **FOUND IN THE BROWSER AFTER EVERY GATE WAS GREEN — EIGHTH CONSECUTIVE SLICE — AND IT WAS
  A PROMISE, NOT A FIGURE.** `SearchableSelect` hard-coded its search placeholder as *"Search
  or create new…"* / *"ابحث أو أنشئ جديداً…"* **unconditionally**, while the create row 130 lines
  above is correctly gated on `!!onCreate`: **the affordance was conditional and only the
  promise was not.** Measured **17 of the 19 `select-searchable` fields wire no `onCreate`** —
  including F2-245's risk picker, shipped one slice earlier — so almost every picker in the
  platform invited an operator to create a record it cannot create. F2-184's shape at the level
  of the text a user reads, and **no code-scoped gate can see a string that is wrong only in
  relation to a sibling prop.** Fixed in both languages, because F2-208 found this class of
  drift in the Arabic half alone (house rule 7).
  ⚠ **A probe that plants a fixture should CLONE a real row.** Hand-built placeholders must
  satisfy every CHECK on the table (`scm.vendors` constrains `category`), and losing that fight
  one constraint at a time teaches nothing; a clone satisfies all of them and only the tenant
  and names change. And **a cross-schema text comparison needs an explicit `COLLATE`** — `scm.*`
  is `Latin1_General_100_CI_AS_SC_UTF8` against a `SQL_Latin1_General_CP1_CI_AS` database
  default, so the measurement died on error 468 (the F2-174 landmine) before producing a number.
  ⚙ **Filed:** the **43** remaining work-list members with no verdicts — least urgent by reach
  but the most complete instances of this shape are the ones whose id is populated on *no* row
  (`bcm.dr_plans :: critical_system_id` 16 of 16, `socops.sbom_component :: application_id`
  20 of 20, `socops.appsec_finding` 10 of 10, `bcm.strategy_roi_analysis` 3 of 3,
  `change_mgmt.change_requests :: change_owner_id` 15 of 17), while `cyber.vulnerabilities ::
  asset_id` (15 of 20) and `cyber.cyber_incidents :: affected_asset_id` (12 of 24) are sharper
  by harm and blocked on **which of the several disjoint asset registers is the target**
  (F2-196's shape, an owner decision); the **32 live unlinked rows**, whose repair means
  deciding per row which registered vendor an annotated label meant (owner decision); the four
  tables still having **no FOREIGN KEY** on their vendor column, now cheap but blocked on those
  32 rows; and `scm.vendors` existing for **organisation 1 only**, so the picker is empty for
  any other tenant.
- ⚠⚠ **A SOFT FK THAT ALSO CARRIES THE PARENT'S IDENTITY IS MAKING TWO CLAIMS, AND ONLY THE
  PARENT CAN ANSWER THE SECOND** (F2-245, measured 2026-08-19). `risk.scenarios` stores `risk_id`
  (which row) beside `risk_code` and `risk_title_en` (what it is called), and
  `RiskScenarioService.create` took all three from the request. Measured in dev and prod:
  **11 of 11 live scenarios store a `risk_title_en` that is not the `title_en` of the risk their
  `risk_id` names** — scenario 1, *"Aramco contract delay — Monte Carlo (base case)"*, stores
  *"Aramco contract renewal delay"* against `risk_id = 4`, which is **"Non-Compliance with NCA ECC
  Controls"** — and every `risk_code` is `RSK-2026-NNN` while **`risk.risks` has no code column at
  all**, its only reference being a UUID `external_id` (F2-203's disjoint-code-space shape).
  🔑🔑 **THE REUSABLE RULE: F2-243 established that a *presence* test on an id is not a check on
  the thing it names and F2-244 that an *authority* check on the caller is not a check on the
  referent; this is the next one along — a reference can be stored, dereferenceable and still
  DESCRIBE THE WRONG ROW, because the description travelled beside the id instead of being read
  from it.**
  🔑 **The half on screen is not necessarily the half with a vote.** The stored copy is what the
  register renders; the id is what `GET /by-risk/{riskId}` joins on. Two surfaces, two halves,
  nothing comparing them — and every invented label was a coherent sentence, which is what a
  fabrication needs to survive (F2-205's *consistency among copies is not evidence*, with the
  copy that has no vote being the one an operator reads).
  ⚠⚠ **THE FORM COLLECTED THE LABEL AND NEVER THE LINK.** `RiskScenariosPage` had two free-text
  inputs (`riskCode` placeholdered **`'RSK-2026-042'`**, teaching the operator to invent a
  reference — F2-236's placeholder rule on a register id rather than a person) and `onCreate`
  **never sent `riskId`**. `forRisk` joins on that column, so **a scenario created through the
  platform's own screen could never be found from its risk**; the `by-risk` client is dark, so the
  link was neither written nor read from the client. 🔑 **Ask what a create form SENDS, not what
  it shows.**
  ⚠ **1 of the 8 body-bound `risk_id` writers validated it, and the reassuring sentence is what
  narrowed the claim.** `NdiCrossLinkService`'s javadoc calls itself *"the single place where those
  soft links are validated against their real targets"* — true **of NDI**, while seven peers write
  the same column into the same table. F2-215's two-of-N. It could not be reused (ADR-012/018
  forbid importing a peer's internals — the `CyberInputValidation` situation), and
  `risk/scenario → risk` is **intra-module**, checked against `ModuleGraphRatchetTest`.
  ⚠ **Re-deriving F2-244's filing refuted its own top pick and two more.**
  `AuditEngagementService#create::annualPlanId` is **`String(64)`, not a numeric FK**, nothing
  dereferences it, and there are **two classes with that simple name** (`audit_eng` and
  `compliance`) — one of F2-222's filed 15 duplicate-name hazards, and the filing keyed on the
  simple name. `NdiGapService#updateGap` validates on **both** paths.
  `RiskControlFindingPivotService` dereferences `link.getRiskId()` at three sites with a bare
  `findById` and **all three are correct** — each is followed by
  `.filter(r -> orgId.equals(r.getOrganisationId()))`. A grep locates; reading adjudicates.
  ⚠⚠ **MY OWN GATE FAILED ON THE CORRECT FIX ON ITS FIRST RUN, AND THE GATE WAS WRONG.** It
  asserted *"exactly one call site"* per setter; `applyParentRisk` legitimately writes each in
  **both** branches (no parent ⇒ all three cleared; resolved ⇒ all three derived). F2-206's rule
  applied to an assertion: **a red leg is a finding only if the code is actually wrong.** The claim
  that was wanted is *"the only writer is the resolver"* — about WHERE the writes live, which is
  also the claim that prevents drift. ⚠ And **a fixture that cannot reach a branch proves nothing
  about it**: `Risk` declares `private UUID externalId = UUID.randomUUID()` (F2-241's
  field-initialiser family), so the "risk with no reference" helper silently held a random UUID and
  the null-branch test failed for a reason unrelated to the code.
  ⚠ **A probe leg that asserts more than its claim fails for reasons unrelated to the claim** — one
  leg compared the whole `ProblemDetail` envelope instead of its `detail` and went red on a correct
  server. ⚠ And the Python locator said **52** pairs where the Java gate says **49**: *write the
  sweep as the gate, over the same tree, in the same language* (F2-213), fourth consecutive slice.
  ⚙ **`ParentIdentityCopyContractTest` is deliberately two instruments**: an invariant over this
  register (hard zero, **no exemptions** — a derived copy is always available, so there is no
  correct code for it to fail on) and an **exactly-asserted census** by reflection over all 425
  shipped entities — **49 (id + identity-copy) pairs**, 2 with a verdict (`platform.audit_log`,
  `ndi.audit_event` are **BY DESIGN**: an audit row must keep the code the entity carried *when the
  event happened*), the other 47 a **work list with no verdicts** (F2-188). ⚠ Roughly half that raw
  population is `*_by_id + *_by_name`, i.e. F2-236's **actor-attribution** family — a different
  question whose answer is the authenticated principal, not a derived copy. Classify before reading.
  ⚠⚠ **FOUND IN THE BROWSER AFTER EVERY GATE WAS GREEN, AND IT WAS MY OWN COMMENT CLAIMING A
  BEHAVIOUR THAT DID NOT EXIST.** The risk cell rendered `title={r.riskCode}` under a comment
  reading *"The `title` states that"* about the English-only limitation; it stated the UUID.
  F2-184's shape, self-inflicted, **in the same commit that filed the gap the comment claimed to
  disclose** — 🔑 **writing the filing and the comment in one sitting is how the two come apart.**
  Closed with `parentRiskTooltip(row, lang)` and gated by **call shape**
  (`parentRiskTooltip\s*\(`) plus a ban on the `title={r.riskCode` form. ⚙ The browser also
  settled the filing: **`risk.risks` DOES store `title_ar`** (the new picker renders 51 risks in
  Arabic), so the gap is the snapshot column, not the data.
  ⚙ **Filed:** the 47-site work list (F2-243's filing already noticed `scm.exit_plans`, one of its
  four `scm` members); the **11 live divergent scenarios + 1 dangling `risk_id = 25`**, where repair
  means choosing per row whether the id or the label was the intent (owner decision);
  **`risk_lc.treatment_plans` holds a live CROSS-TENANT row in both databases** (plan 1 is org 2
  naming org 1's risk 1, sharing `external_id` with plan 2, so seed-origin) and `risk_lc`
  dereferences the risk **nowhere**, making it F2-239's least-urgent kind while still proving
  F2-242's *"waiting for the row that dereferences it"* is not hypothetical; **no
  `risk.scenarios.risk_title_ar`**, so the register's risk label is English-only on an
  Arabic-primary UI (house rule 7); **`risk_id` carries no FOREIGN KEY on 19 of the 21 tables that
  declare a soft link to `risk.risks`**; and the structural `@TenantOwned` root, which for this
  shape would also derive any identity copy from what it resolved.
- ⚠⚠ **AN AUTHORITY CHECK ON THE CALLER IS NOT A CHECK ON THE REFERENT, AND ONE METHOD
  VERIFIED ONE OF ITS TWO FOREIGN KEYS WITH A COMMENT SAYING SO** (F2-244, measured 2026-08-19).
  `CoiService#recordRecusal` stored `req.userId()` verbatim **three lines below**
  *"If a declaration id is provided, validate it belongs to this tenant."* — and the split ran the
  same way in the database: `declaration_id` carries `FK_coi_recusal_decl`, `user_id` carries no
  constraint at all, so the code check and the schema constraint both landed on the same reference.
  F2-215's two-of-N at its shortest range yet, shorter than F2-243's.
  🔑🔑 **THE REUSABLE RULE: F2-243 established that a *presence* test on an id is not a check on the
  thing it names; this is one notch further — a method can be careful, documented and CORRECT about
  the *right to name a party* while never asking whether the party exists.** `declarantFor` is the
  sharp one: F2-145(e) put real work into converting three recited role names to `ETHICS:WRITE` so a
  tenant granting it to a fourth role would not silently lose the capability, and then returned
  `req.userId()` unresolved. Both questions look like one because both are about *"is this
  allowed"*, and only one was being asked.
  ⚠⚠ **NOT A MERE DANGLING REFERENCE: `coi_recusal_event.user_id` is NOT NULL with NO FK and NO NAME
  COLUMN OF ANY KIND**, so that id is the only thing identifying who recused — and the entity's own
  javadoc says the subject is a *"director, committee member, or senior executive"* whose recusal the
  Compliance Officer reports under **SAMA Article 30**. Measured identical in dev and prod: **3 of 8
  recusals and 8 of 8 non-null declarations name ids 1001-1007 while `MAX(iam.users.id)` is 8 — 11 of
  28 live rows**, so the register's own per-person query `GET /recusals/user/{userId}` can never reach
  them from any real person. The **sibling table models it correctly** (`coi_declaration.user_id`
  NULLABLE + NOT NULL `full_name_en` + `employee_no`), which is what its 12 NULLs are; the recusal
  table has neither, so the seed had no way to record a director except by inventing an id. ⚠ And the
  create form asks a human to type a **raw numeric id into a text box** — no picker — which is exactly
  what produces invented ids.
  ⚠ **Refusing CAN break a request that succeeds today, and here that is correct.** F2-182's test does
  not clear it: no upstream coercion, no FK, so a caller typing 1007 gets 201 today and 400 after. But
  that 201 stores a recusal attributed to nobody, and the Board Secretariat is a present operator who
  can act on a 400 naming the id — **not** the public-intake case where a lost record is worse than a
  mis-graded one (F2-179/F2-183). ⚠ One message for *"no such user"* and *"another tenant's user"*,
  copied from `CommitteeMemberRegistryService#requireUserInTenant`, which records why: distinguishing
  them lets a caller enumerate foreign user ids from the error text.
  ⚠⚠ **THE TEST'S OWN JAVADOC OFFERED THE HOLE AS A VIRTUE.** `CoiDeclarantBindingTest` read
  *"it touches none of the service's six collaborators"*, presented as clean isolation. It was true,
  and true **because the resolver resolved nothing**. 🔑 **A resolver that touches nothing cannot
  resolve anything** — the isolation the comment praised was the shape of the defect. Its tests were
  all about *authority* and every one is kept; the leg it never had is *"is the named declarant real"*.
  ⚠⚠ **THE DISPLAY HALF WAS THE SAME DEFECT FROM THE CLIENT SIDE, AND THE REGISTER NAMED THE PERSON
  NOWHERE.** `lib/coi.ts`'s `RecusalResponse` has always declared `userNameEn`/`userNameAr`, the server
  record declared neither, so `CoiRegisterPage`'s `selected.userNameEn && …` guard could never fire
  (F2-211: the client nominates, the entity adjudicates) — and the table had **no person column at
  all**. `userResolved` is a separate field on purpose: a resolvable user with a blank name and an id
  that resolves to nobody are different facts, and 11 of 28 rows are the second kind, so blanking both
  would let the register's broken rows read as its tidiest (F2-197; F2-209's three states).
  ⚠ **`ethics -> iam` is an ordinary business edge, checked not assumed**: `UserRepository` is already
  read from ten modules outside `iam`, and `ModuleGraphRatchetTest`'s `KERNEL_ISH` is only
  `{platform, integrations, dashboard}`.
  ⚠⚠ **A PROBE LEG OF MINE PASSED FOR THE WRONG REASON ON BOTH TREES, AND ONLY THE PRE-FIX RUN
  EXPOSED IT.** The declaration leg sent `campaignId: null`, and `submitDeclaration` calls
  `loadCampaign(req.campaignId())` **before** `declarantFor` — so it answered 400 on the campaign, on
  master and on the fix alike, reading as proof in both directions. Repointed at a real OPEN campaign
  it shows **201 pre-fix / 400 post-fix**. 🔑 **The fixed tree alone could never have revealed it** —
  which is the whole argument for running the pre-fix probe rather than reasoning about it.
  ⚠⚠ **A PROBE THAT PLANTS ROWS MUST CLEAN UP IN A `finally`.** The first run crashed printing an
  Arabic name (Windows console is cp1252) **after** planting, so its inline cleanup never ran and two
  `[REVIEW-TEST]` rows were left live in dev. A harness bug is not a reason to leave rows in a real
  database (house rule 6). `sys.stdout.reconfigure(encoding="utf-8")` and legs-in-a-function.
  ⚠ **My own `methodBody` helper matched a CALL, not the declaration** — `recordRecusal` calls
  `requireOwnUser` earlier in the file than the declaration appears, so the tenant-filter leg read a
  different method's body and failed on a correct tree. 🔑 **A name-keyed lookup finds the first thing
  with that name, and a call is not a declaration.** Its first fix then blew the stack:
  `(?:public|private|protected|static|final|\s)+[\w<>,.\[\]?]+\s+` is an alternation of single
  tokens under `+` and backtracks catastrophically over whitespace — three legs died with
  `StackOverflowError`.
  ⚠⚠ **F2-243's PLURAL-LOAD GATE CAUGHT THE SITE THIS SLICE ADDED** (`resolveSubjects`'s
  `findAllById`) and demanded a verdict for it. 🔑 **The verdict-per-site rule earning its place
  immediately: a decrease-only count would have absorbed a new site inside a tolerated total.**
  ⚠ **Re-deriving F2-243's filing corrected it three ways before any code was written** (F2-221):
  `scm/resilience` is **three** services not two, **none of its three tables has any foreign key at
  all**, and it holds **40 live rows not 24** with `vendor_id` NULL on 25 — because the create form
  has no vendor picker and sends only free-text `vendorName`. Re-ranked by *what the FK's target IS*
  (F2-239's two orderings), that register is the least urgent kind — nothing dereferences the id and
  no coverage question joins on it yet — so it was filed again with corrected numbers and the slice
  went to the reference that identifies a **person**. ⚠ 2 of my 24 candidates were refuted by reading,
  both over-reporting: a guard in a **helper** (`applyDerivation`) and a finder whose **organisation
  comes first in the method name** (`findByOrganisationIdAndIdAndIsDeletedFalse`).
  ⚙ **Filed:** the **external-director gap** — this register cannot name a director with no platform
  login, F2-26's roster models that population properly (`LINKED | EXTERNAL | UNLINKED`), and adopting
  it needs a migration, a form field and a decision about whether a recusal's subject should be a
  **committee seat**; the **11 live unresolvable rows** in both databases (repair rewrites governance
  rows and no mapping from 1001-1007 to a real person exists); `scm/resilience` as re-measured; the
  **22-site** store-side residue with no verdicts (readiest by harm:
  `AuditEngagementService#create::annualPlanId`, IIA Standard 2010); **three `ExitPlan` registers for
  one concept**, two sharing a simple name (F2-222's 15); and the structural `@TenantOwned` root.
- ⚠⚠ **A FOUR-EYES GATE THAT CONSUMES A FOREIGN KEY MUST *RESOLVE* IT — A PRESENCE TEST ON
  AN ID IS NOT A CHECK ON THE THING IT NAMES** (F2-243, measured 2026-08-19). CLAUDE.md lists
  *"GHG verify needs evidence"* among the platform's server-enforced four-eyes gates.
  `GhgEmissionRecordService.transition` asked `cur.getEvidenceItemId() == null`, so it was
  discharged by any non-null number the foreign key would accept. Probed live as `so_officer`
  (SUST WRITE) against records the probe planted: a GHG figure citing the register's one
  **REJECTED** evidence item verified with **HTTP 200** and stored `verified = true`, and so did
  one citing an **EXPIRED** item. Measured dev = prod, `evidence.evidence_items` holds EXPIRED 31 /
  APPROVED 27 / PENDING_REVIEW 15 / DRAFT 4 / REJECTED 1 — **51 of 78 are not APPROVED** — so the
  gate built to consume the evidence module's four-eyes review and its nightly staleness sweep was
  discharged by their **rejected output**, on an ISAE 3410 assurance input. 🔑 **No second
  tenant is needed, which is what makes this live rather than latent.**
  ⚠ `FK_ghg_evidence_item` DOES exist, so the comment calling it *"a real evidence-item FK"* is
  true (verified, not assumed — F2-227): a *nonexistent* id is refused by the database and every
  other number is accepted. **The constraint that looks like it protects the reference protects only
  its existence.**
  🔑🔑 **THE REUSABLE RULE: THE TENANT IS VERIFIED WHERE THE REFERENCE IS *STORED*
  (F2-242's discriminator) AND THE STATE WHERE IT IS *CONSUMED*** — because an item can be approved
  on the day it is attached and expired by the time somebody verifies against it. That division is
  why the fix is in two places, and it is the general answer for any gate whose input is an FK.
  ⚠⚠ **The asymmetry sat inside one method, three lines apart.** `update` wrote two
  caller-supplied foreign keys: `emissionFactorId` is resolved by `applyDerivation` with
  `findByIdAndOrganisationId` **and a hard throw**; `evidenceItemId` was stored raw. 🔑 **The
  reference feeding a NUMBER was verified and the one discharging a CONTROL was not** — F2-215's
  two-of-N at its shortest range yet.
  ⚠⚠ **TWO WRITERS OF ONE FK COLUMN, AND ONLY ONE OF THEM IS THE BRIDGE.**
  `SustEvidenceLinkService.link()` (the declared S-C path) resolves the item in-tenant, records a
  generic `EvidenceLink` **so `buildPackage()` can bundle the file and its SHA-256 for an assurer**,
  and writes a LINK_EVIDENCE audit row. `update` did none of the three, so a record could be VERIFIED
  citing an item **the ISAE 3410 evidence package would not contain** — F2-188/F2-189/F2-191's shape
  in the evidence fabric: the duplicate agrees on the column and differs on the parts nothing
  asserts. Closed structurally: the bridge is the single writer, and the entity-bound PUT accepts an
  **echo** and refuses a **change** (F2-231/F2-234 — a round-trip PUT must keep working).
  ⚠ **Put the predicate in the module that owns the vocabulary** (F2-196): `EvidenceItem.APPROVED`
  + `EvidenceItemRepository.findUsableAsEvidence`, an **inclusion of exactly one state**, because
  that column has no CHECK and an exclusion list silently admits every token nobody remembered — for
  a four-eyes gate, the unsafe direction. No new module edge: `sust → evidence` already existed
  through the bridge, and `ModuleGraphRatchetTest` is unmoved.
  ⚠⚠ **MY OWN DIAGNOSTIC CITED A ROUTE THAT DOES NOT EXIST, AND THE GATE COULD NOT TELL.**
  The refusal named `/api/sustainability/evidence/link`; the bridge is mapped at
  `/api/sustainability/evidence-links`. **The live probe found it by receiving 400 from the very path
  the error message had told it to use** — the gate asserted the path as a *literal*, and a
  literal-matching check cannot resolve a route. 🔑 **A diagnostic whose only job is to send
  the caller somewhere is worthless if the somewhere is invented** (the guessed-path error recorded
  since F2-198, now inside a fix's own error text). Make it **one constant** read by the
  `@RequestMapping` and by the citation, and assert the citation against the constant — never a
  typed-out copy.
  ⚠⚠ **F2-242's 7-site residue: ALL SEVEN REFUTED, because the detector looks for ONE
  spelling of the predicate and this tree verifies a reference in at least FOUR ways** — a
  tenant-scoped finder, a helper resolving the *parent*, an org-scoped native query (so the id is not
  caller data at all), and a **recipient identity**, which is narrower than the tenant.
  🔑 **A detector reporting zero defects over its own population is a statement about the
  detector, not about the code**, so the count can never be the assertion (F2-234): demand a verdict
  per site, and a reverse leg requiring every verdict to still name a site the scan finds.
  ⚠ **And the PLURAL form was outside that gate by construction**: its pattern is singular, so a
  caller-supplied id LIST was invisible. 9 receivers call `findAllById` on a tenant-bearing entity;
  the exemplar for doing it right is already in the tree — `PolicyManagementService#setControlLinks`
  filters every returned row on the tenant before using it. The one filed member is
  `UserService#assignRolesByNameOrId`, whose `roleNames` branch resolves through
  `findByOrganisationIdOrOrganisationIdIsNull(orgId)` and whose `roleIds` branch is a bare
  `findAllById(roleIds)` — latent, since all 69 active roles are `organisation_id IS NULL` and both
  write routes are PLATFORM_ADMIN-only. ⚠ `createIn`'s javadoc explains why role resolution is
  tenant-correct and describes the `roleNames` branch **only**: F2-215's two-of-N, in the comment a
  reader consults first.
  ⚠ **My Python locator found 6 of those 9 — third consecutive slice where the throwaway
  undercounts the shipped gate** (F2-213: 5 vs 9; F2-214: 3 vs 11). *Write the sweep as the gate,
  over the same tree, in the same language.* ⚠ And a stripper that blanks comments with spaces
  preserves byte offsets and **destroys line numbers** — every newline inside a block comment
  vanishes, so every site after the first one is attributed to the wrong line, and a wrong line
  number sends the reader to unrelated code, which reads exactly like a false finding.
  ⚠⚠ **A HARNESS THAT DETECTS A KILL WITH `"Failures: 0, Errors: 0" not in out` IS WRONG THE
  MOMENT IT RUNS TWO TEST CLASSES** — the *passing* class prints that line, so a real failure in the
  other reads as SURVIVED, and one mutation was reported as a gate hole when the gate had killed it.
  F2-194's substring-satisfied-by-something-else, in the instrument rather than the gate. Maven prints
  exactly one BUILD verdict; use that.
  ⚠ **An existing test asserted the defect as correct IN ITS NAME** —
  `verify_acceptsWhenEvidenceItemIdPresent_evenIfRefBlank`, i.e. that *presence* of the id suffices.
  The Nth instance (F2-159, F2-175, F2-227, F2-234, F2-236). Repurposed, not deleted.
  ⚙ **Filed:** the **26-site store-side work list** (a create or update writing a caller-supplied
  FK whose target carries `organisation_id`, no tenant predicate in the method) with deliberately no
  verdicts — sharpest unread `CoiService#recordRecusal::userId`,
  `AuditEngagementService#create::annualPlanId`, `DpiaService#update::processingActivityId`;
  **`module/scm/resilience`**, where `ExitPlanService#create` and
  `SupplierResilienceTestService#create` store a caller-supplied `vendorId` unverified **in the very
  module F2-242 swept**, and `scm.exit_plans` / `scm.supplier_resilience_tests` have **no FOREIGN KEY
  on `vendor_id` at all**, so an *absent* id is storable too; **4 GHG rows are VERIFIED/APPROVED with
  no evidence of either kind**, carrying `verification_date = 2027-09-30` — a future date, dev = prod,
  seed-origin, so repair is an owner decision; and the structural root, a `@TenantOwned`-style
  binding, unchanged from F2-242.
- ⚠⚠ **AN UNVERIFIED FOREIGN KEY IS A CROSS-TENANT WRITE WAITING FOR THE ROW THAT
  DEREFERENCES IT, AND THE PLATFORM-WIDE BACKSTOP GUARDS THE TENANT *COLUMN* RATHER THAN THE
  TENANT *ROW*** (F2-242, measured 2026-08-19). `ScmService#startOnboarding` stored a
  caller-supplied `vendorId` without verifying the vendor belonged to the caller's tenant, then
  dereferenced it with `vendorRepo.findById(vendorId).ifPresent(v -> { …set…; save(v); })` — no
  tenant predicate. Probed live as `ranalyst` (organisation 1, holding only `SCM:WRITE`) against a
  `[REVIEW-TEST]` victim planted in organisation 2: the create answered **201** and moved the
  foreign vendor `REJECTED → IN_PROGRESS`; `/approve` then took its `status` from **BLACKLISTED to
  ACTIVE** — a supplier another tenant had struck off, reinstated by a stranger. **0 audit rows
  name the mutated vendor**, the attacker cannot read it back (`GET /vendors` is tenant-scoped),
  and organisation 2 has no user who could notice. In a SAMA-outsourcing register `status` gates
  whether you may transact with a supplier, so the write is not cosmetic.
  ⚠⚠ **`TenantWriteGuardInterceptor` CANNOT SEE IT AND THAT IS NOT A HOLE IN IT.** `decide`
  returns `false` the moment `Objects.equals(oldOrg, newOrg)` — its own comment reads *"Tenant
  unchanged — the normal case."* It catches a row being **moved** between tenants (F2-22b) and an
  insert **landing** in the wrong one (F2-216); writing somebody else's row without touching its
  `organisation_id` is outside its question **by construction**, while `application-prod.yml`
  calls the bean the *"platform-wide … backstop … so a single missed per-service guard cannot
  silently write across tenants"*. 🔑 **A guard's population is its question: before trusting a
  backstop, say out loud which noun it protects.**
  🔑🔑 **THE DISCRIMINATOR IS NOT THE UNSCOPED LOAD — IT IS WHETHER THE REFERENCE WAS VERIFIED
  WHEN IT WAS STORED.** `RiskAcceptanceService#setRiskStatus` carried the **identical**
  `risks.findById(riskId).ifPresent(…save…)` shape and was **correct**, because `request()` had
  already resolved that `riskId` with `.filter(r -> Objects.equals(r.getOrganisationId(), orgId))`
  before writing it to the column. So **fix it where the reference is STORED** and scope the load
  as defence in depth. ⚠ Closed anyway: what made it harmless is a predicate in a *different
  method* and nobody was maintaining that (F2-177); reachability is a fact about today, not a
  property of a control (F2-182).
  ⚠ **The FK does not help, and it is worth being precise about why.** `FK_scm_ob_vendor` and
  `FK_scm_assess_vendor` both exist, so a **nonexistent** id is refused by the database — and a
  foreign key knows nothing about tenancy, so a **cross-tenant** id is a perfectly valid
  reference. *The constraint that looks like it protects the reference is the one that cannot.*
  ⚠⚠ **The second instance: the on-request sibling filters and the sweep six methods away does
  not.** `EvidenceItemService#expireStaleEvidence` (the nightly all-orgs sweep) reopened the
  originating evidence request through an unpredicated `findById`, while `fulfilRequest` has
  always carried `.filter(r -> r.getOrganisationId().equals(orgId))` — F2-215's two-of-N, in one
  class. The correct anchor there is the **item's** tenant, not `TenantContext`, because the sweep
  sets the tenant per row and has no request tenant of its own.
  ⚠⚠ **THE DETECTOR BUG IS THE REUSABLE PART: the reconnaissance pass matched
  `\.ifPresent\(([^;]{0,600}?)\)` and reported 0 SITES ON A TREE HOLDING 7, because a lambda BODY
  is a statement list and a character class excluding `;` can never match the shape being
  hunted.** 🔑 **A detector that cannot match reads exactly like a clean tree, and it
  UNDER-reports.** Extract the argument with a **paren-balanced walk** (skipping string and char
  literals so a `")"` in a message cannot end it) and carry a synthetic self-test over the literal
  pre-fix *and* fixed forms. `UnverifiedForeignKeyWriteContractTest` does, over a **derived**
  population (repository fields per owner type → entity → does it carry `organisation_id`), with
  **one** read-and-reasoned exemption enforced both ways — `LoginAttemptService#onSuccess`, which
  runs during authentication before any tenant exists, where `orgId()` would throw.
  ⚠⚠ **MY OWN LEG 0 WAS WRONG TWICE AND THE SECOND FAILURE EXPOSED THE FIRST.** It asserted
  `status: "planned"` → 400; that answered **201**, and I recorded it as F2-217's stale listener.
  The post-fix run — on a backend restarted 40 seconds earlier — failed the identical leg, because
  `ClosedVocabulary.require()` **normalises case before testing membership** (exactly what F2-241's
  own casing test asserts), so `'planned'` is legal and 201 is correct. 🔑 **A wrong expectation
  and a stale listener produce the identical red line, and the stale-listener reading is the more
  interesting story — which is why it was the one I reached for.** Use a token legal in *no*
  casing, and assert both halves so the leg cannot pass for the wrong reason.
  ⚠ **The backtick landmine fired again**, this time on `py -c "…"` inside a Bash command: Bash
  expands every backtick as command substitution before Python sees the string, prose about code
  is mostly backticks, and the script **printed its own success line alongside the errors**. Write
  the script to a file, and have it re-read what it wrote and assert the identifiers survived — a
  mangled paragraph still reads like prose.
  ⚙ **Filed:** the **7-site load-then-write-later residue** the gate prints and asserts
  decrease-only, deliberately with **no verdicts** (F2-188) — `TprmService:172/211` is readiest,
  being the twin register of the one this slice closed; `scm.vendors.status` /
  `onboarding_status` written as Java literals against CHECK-constrained, case-insensitively
  collated columns (legal today, cheap to close now that the register has no other open writer —
  F2-183); `scm.slas` **still has no server surface of any kind** (14 live rows, dev = prod);
  and the structural root — a caller-supplied foreign key is representable on every create in the
  platform, so a `@TenantOwned`-style binding that resolves a reference through the tenant
  automatically, rather than 200-odd services each remembering, is an **owner decision**.
- ⚠⚠ **AN ENTITY'S FIELD INITIALISER IS A ONE-TOKEN VOCABULARY, AND BECAUSE NOBODY EVER
  *DECLARES* IT AS ONE, NO VOCABULARY INSTRUMENT IN THIS TREE HAD EVER COMPARED ONE AGAINST ITS
  COLUMN'S OWN CHECK** (F2-241, measured 2026-08-19). `module/scm/entity/VendorAssessment` declared
  `private String status = "DRAFT"` and `CK_scm_assess_status` admits
  `PLANNED | IN_PROGRESS | COMPLETED | ESCALATED`; `ScmService#createAssessment` does `new
  VendorAssessment()` then overwrites `status` only `if (req.getStatus() != null)`, so **any caller
  that omitted the state sent `'DRAFT'` and the database refused the INSERT.** `module/scm/entity/Sla`
  was the same shape with `= "ON_TRACK"` against `CK_scm_sla_status`'s
  `NOT_MEASURED | AT_RISK | BREACHED | MET`. Probed live against the real column: `'DRAFT'` is
  REFUSED, `'PLANNED'` accepted.
  ⚠ **The column's DB DEFAULT does not rescue it** — both carry one (`'PLANNED'`, `'NOT_MEASURED'`)
  and the entity maps both insertable, so Hibernate names the column in the INSERT and sends the
  initialiser's value (F2-240 measured that on the *tprm* twin). 🔑 **The DEFAULT is still the
  useful artefact: it is the schema author's own statement of which token the initial state should be,
  so where an entity disagrees with it in the direction the CHECK refuses, the ENTITY is the half that
  is wrong.** Both fixes took the column's own default.
  🔑 **`ON_TRACK` is also the FLATTERING value** — it asserts the supplier is meeting the SLA — so
  had anyone "fixed" it by widening the CHECK rather than correcting the entity, a brand-new SLA would
  have claimed compliance it never demonstrated. F2-238's inversion from the other side.
  ⚠⚠ **CORRECT THE INITIALISER, DO NOT DELETE IT, WHEN THE REGISTER HAS NO CREATE PATH — the
  discriminator is whether a writer exists to own the default.** F2-240's remedy for the adjacent
  never-firing-guard family is *remove the initialiser and own the default on the create path*;
  `scm.slas` has no create path at all (no controller, no route, no FE client, and `SlaRepository` is
  injected into `ScmService` and **never used** — F2-204's `NO_SERVER_SURFACE` shape, 14 live rows dev
  = prod), so deleting it would leave a NOT NULL column with no writer, which is the one hazard F2-238
  flagged for that fix shape.
  ⚠⚠ **A THIRD SITE ON THE SAME CREATE, AND THE LOUDER DEFECT WAS MASKING THE QUIETER ONE.**
  `scm.vendor_assessments.risk_rating` is NOT NULL with DB default `'MEDIUM'` and the entity gave it
  **no initialiser at all**, so an omitted key arrived as an explicit null — and that fires FIRST,
  answering **400 "A required field was not supplied"** with `constraint: null`, **naming no field**
  (F2-210/F2-214's shape, where the diagnostic sends the caller nowhere). 🔑 **Two defects on one
  create; only a probe that omits ONE field at a time can see the second** (F2-206). My first probe leg
  asserted both at once and reported the wrong mechanism.
  ⚠⚠ **FOUR INSTRUMENTS, FOUR POPULATIONS, ALL BLIND FOR THE SAME REASON.**
  `ClosedVocabularyPinTest` narrows on the source string `ClosedVocabulary.of(` and that field type;
  `HandRolledVocabularyMirrorTest` checks six mirrors named in a list; `StateMachineColumnMirrorTest`
  keys on a named machine constant, and a one-token default is not a machine; and F2-240's
  `NeverFiringNullGuardContractTest` keys on **entity-bound** updates while this register's PUT binds a
  purpose-built DTO, so it was outside that gate **by construction**. Ninth appearance of F2-198's
  shape, and the narrowing is none of the previous ones (a filename, a three-entry allow-list, a
  module name, an `extends` clause, a field-name list inside a fix, a claim shape in a gate): it is
  **the absence of a declaration**. 🔑 **A value nobody wrote down as a vocabulary is still a
  vocabulary to the database.**
  ⚙ **`EntityInitialiserAcceptedByColumnTest` CONSTRUCTS EVERY `@Entity` AND ASKS EACH FIELD** — a
  hard zero with an **empty exemption map**, safe because there is no correct code for it to fail on
  (F2-214). ⚠ **Reflection over the constructed object, never a source regex**: the reconnaissance
  needed three hand-written cases — the `@Column` on the line above, the `@Column` column-aligned on
  the **same** line (this codebase does that), and an initialiser that is a **constant reference**
  rather than a literal (`ChangeModelStatus.DRAFT`) — and it silently skipped the four entities whose
  `@Table` carries no `schema=`. Measured and published to `target/entity-initialiser-checks.txt`:
  **432 shipped entities** (the same 432 this file records — the corroboration), **170 comparisons
  against a CHECK vocabulary across 22 schemas**, **13 against a numeric range**, 0 unprobeable, 0
  with no `@Table`, 0 on `insertable = false`, **2 refused**. ⚠ The regex recon resolved **415** and
  169: 🔑 **it found the same two defects, and "found the same defects" is not "measured the same
  population"** — the 18-item delta hid nothing this time, which is luck rather than method (F2-213,
  F2-214).
  ⚠⚠ **INCLUDE THE RANGE CHECKS, AND F2-230 IS THE REASON RATHER THAN THE OBJECTION.** That slice
  established a range is not a vocabulary and asserted the limit deliberately — but a value outside a
  range CHECK produces the **identical** refused INSERT, so excluding ranges would have shipped an
  invariant whose population was half its claim, this programme's most-repeated defect.
  `MigrationVocabularies` grew `ranges()` as a **separate accessor over one shared declaration walk**,
  so the three traps its javadoc records are not copied into a second parser. All 13 numeric
  initialisers are in range today, which is a result and not a dead end (F2-216).
  ⚠⚠ **THE `(table, column)` LOOKUP REFUSES AN AMBIGUOUS BARE TABLE NAME RATHER THAN GUESSING, AND
  THIS SLICE IS THE REASON.** `vendor_assessments` exists in **`scm` and `tprm`**, both carry a
  vocabulary CHECK on `status`, and **the tokens differ** — `ESCALATED` is legal in one and not the
  other — so a bare-name lookup compares one register's initialiser against the other register's
  vocabulary and answers confidently. That is F2-222's filed hazard (15 of 433 entity simple names
  declared in two packages, *"and nobody is maintaining that"*), and **F2-240's filing was written
  from the wrong twin of this exact pair**: it said *"`tprm.vendor_assessments` has 3 CHECK
  constraints and `assessment_type` is unconstrained"*, where that table has **2** CHECKs and **no
  `assessment_type` column at all**. 🔑 **F2-240 hit the ambiguity, fixed it in the GATE, and let it
  leak into the PROSE, where nothing checks it** — the correction is not the finding; reading the twin
  to check the filing is what found the defect.
  ⚠⚠ **PUBLISH THE NAMES, NOT THE COUNT — it is how I learned my scan reached `target/test-classes`.**
  The first draft reported "1 `@Entity` with no `@Table`" and the count was unremarkable; the name was
  `ServerOwnedFieldsInvariantTest$SyntheticUnprobeableEntity`, F2-215's own fixture.
  `ClassPathScanningCandidateComponentProvider` walks the whole classpath, so a hard zero would have
  failed on a fixture a later slice deliberately builds to be refused. 🔑 **A count cannot tell you
  its members are the ones you meant** (F2-235). Excluded by **code source**, never by a name ending
  in `Test` — F2-192 recorded that a name test lets any member escape by being renamed.
  ⚠ **And my case-sensitivity leg asserted the vocabulary SET rather than the gate's own comparison**:
  `admissible.contains("planned")` is false whether the comparison is case-sensitive or not, so it
  proved neither. Extracted as `accepts()` and asserted directly — the mutation that makes the gate
  `equalsIgnoreCase` is killed only because of that change. **Near the claim rather than the claim**,
  for the Nth time.
  ⚠⚠ **CLOSING THE DEFAULT WITHOUT CLOSING THE WRITER IS F2-215's TWO-OF-N AT ITS MOST LITERAL**: a
  reader who sees the initialisers agree with the CHECKs has no reason to ask whether the *caller's*
  value does, and it did not — `AssessmentDtos.Request` carries no validation annotation of any kind
  and `applyAssessment` checked nothing. Three `ClosedVocabulary` declarations, each naming its
  constraint so `ClosedVocabularyPinTest` mirrors it against the migration SQL. ⚠ `require` not
  `coerce` by F2-182's mechanical test — nothing coerces upstream, so the CHECK was the only refusal
  and every value that succeeds today is a case-variant of a legal token. ⚠⚠ **The casing half is
  live**: probed against the real database, `status = 'planned'` is **ACCEPTED and stored verbatim**
  while `ASSESS_STATUS_OPTIONS` and the register's tone map compare case-sensitively (F2-179, on a
  SAMA outsourcing register). ⚙ Check the FE dropdowns against the CHECKs **before** enabling
  `require` — all three matched exactly here, so it cannot break the page. 🔑 **The frontend's own
  comments state the four-token status vocabulary correctly while the entity three files away
  initialised to a fifth token no vocabulary anywhere contains** — F2-205's *which of them is the
  server?*, with the FE right and the entity, which owns the default, wrong.
  ⚠ **An existing test's FIXTURE used the illegal token**: `escalateAssessment_...` set
  `status("DRAFT")`, describing a row that could not exist in either database. 🔑 **A mocked
  repository has no constraint, which is exactly why the illegal initialiser looked normal for as long
  as it did — and why no unit test in a 4,142-test suite could ever have caught this.**
  ⚠ **The probe refuted four of my own expectations**: I predicted 500s and got a 400 and three
  **409 `VALUE_NOT_ALLOWED`** responses naming the column — F2-180's improved 547 diagnostic doing its
  job, better than assumed. State the status code you expect and let the probe correct you.
  ⚙ **Filed:** `ScmService#createAssessment` **never resolves the vendor**, where
  `TprmService#createAssessment` calls `getVendor()` to *"verify org ownership"* — so an SCM assessment
  can name a vendor id the caller's tenant does not own (a dangling reference, not a leak: the row is
  still stamped with the caller's org). Found by an `UnnecessaryStubbing` failure, not by a gate.
  Also: `scm.slas` has **no server surface of any kind** (F2-204's population, whose derived registry
  was scoped to the four `*_lc` modules and so never saw it); and the adjacent bucket of **45 NOT NULL
  columns with no entity-side filling mechanism**, whose majority are F2-238/239/240's own output
  already asserted by `everyRemovedInitialiserHasAWriterOnTheCreatePath` — read the route and probe
  it, do **not** write 45 reasons (F2-188).
- ⚠⚠ **WHEN EVERY REMAINING MEMBER OF A FAMILY IS LATENT-BY-CALLER, REACH HAS NOTHING LEFT TO
  RANK — AND THAT IS THE ARGUMENT FOR SWEEPING THE POPULATION, NOT FOR CLOSING THE FILE** (F2-240,
  measured 2026-08-18). Executing F2-239's filed 19-site work list, the population was re-derived
  (F2-221): **11 routes resolved from their controllers, all binding the ENTITY, none carrying
  `@Valid`**, 7 with a frontend write client, and **every update method having exactly ONE Java
  caller — its own controller**, so no second producer (F2-238's question, asked and answered).
  Then the ordering came up empty: **all six live callers explicitly send every initialised field
  they could omit.** F2-237's headline is that a defence in one client is not a defence; here the
  defence was the shape of one object literal, and for six of the nineteen there was **no client at
  all** (F2-214's invitation-to-build shape). `WORK_LIST` **19 → 0** and now an INVARIANT — safe as
  a hard zero because an entity-bound partial update whose omitted key must mean *leave it alone*
  cannot also declare an initialiser, and where a default is genuinely wanted the create path is
  always available to own it. 🔑 **Removing the initialiser makes the hazard ABSENT rather than
  latent** (F2-213), which is what turns "19 known" into "none representable".
  ⚠ **Sorted by what the initialiser ASSERTS, four are flattering** (dev = prod):
  `VendorAssessment#riskRating` → MEDIUM on **13 of 19** rows that are HIGH (downgrades a
  third-party risk rating); `GhgEmissionRecord#coveragePercent` → **100.00** on 8 of 18 sitting at
  48–89% (an ISAE 3410 assurance input); `NdiFrameworkItem#mandatory` → false on **13 of 20**
  mandatory NDMO requirements; `ControlMapping#coverage` → FULL over PARTIAL. 🔑 **A reset that
  MANUFACTURES an assurance does not lose a fact, it asserts one** — two of the four sharpest are of
  that kind rather than the losing kind.
  ⚠⚠ **THE FIX NEEDED A FIFTH WRITER KIND, AND THE GATE WOULD HAVE FAILED ON THE CORRECT FIX
  WITHOUT IT.** Two of the nineteen defaults belong in a purpose-built request record's
  `toEntity()`, because `POST /api/sustainability/climate-risks` and `.../ghg` bind
  `ClimateRiskCreateRequest` / `GhgCreateRequest` and hand the service an entity they built —
  F2-233's fourth wire-closing mechanism, arriving on the **writer** side. The gate resolved writers
  through files named `*Service.java`, so both would have read as *"no such writer"*.
  `liveWriters()` widens it and a leg asserts the widening is load-bearing, never decorative.
  ⚠⚠ **AND THE GATE'S CREATE-PATH LEG HAD BEEN A WHOLE-FILE SUBSTRING CHECK SINCE F2-238.** The
  mutation harness found it: deleting `NdiFrameworkItemService#create`'s
  `if (req.getMandatory() == null) req.setMandatory(false);` left the build **GREEN**, because the
  leg asked whether the writer FILE contains `setMandatory(` anywhere — and `importJson` and
  `update` both do. **F2-194's substring-satisfied-by-something-else, in the leg that had been
  certifying this exact fix shape for two slices.** Every entry now names the **METHOD**, which is
  self-verifying (a method that does not exist, or does not hold the setter, fails), scoped by a
  brace-balanced extractor with its own synthetic positive control carrying the defeating shape.
  ⚠ **A name-shape classifier could NOT have derived those methods**: `CloudServiceService` puts all
  nine of F2-238's defaults in a helper called `applyCreateDefaults`, matching no
  `create|add|import` pattern. All 55 were read, not guessed.
  ⚠⚠ **MY OWN GATE REFUTED MY OWN FIX ON ITS FIRST RUN.** Closing the separate defect below meant
  adding `if (req.getStatus() != null)` to `updateAssessment` — over a field `VendorAssessment` also
  initialised, i.e. **a brand-new never-firing guard.** Roughly two in ten of a slice's own claims
  fall on first run (F2-188, F2-192, F2-204, F2-211); this one fell to the instrument the same slice
  was extending. ⚠ `tprm.vendor_assessments.status` carries a **database DEFAULT of `'PLANNED'`** and
  that does **not** rescue it: the entity maps the column insertable, so Hibernate names it in the
  INSERT and sends an explicit null. **F2-215's fourth filling mechanism looks like it applies here
  and does not — check whether the entity maps the column insertable before relying on a DB
  default.**
  ⚠⚠ **A SEPARATE DEFECT THE PROBE WALKED INTO, IN THE FILE F2-213 HALF-FIXED.**
  `TprmService#updateAssessment` wrote `assessmentDate`, `assessor`, `status`, `findings` and
  `nextReviewDate` **unconditionally**. Because `assessment_date` and `status` are NOT NULL the route
  answered **400 "A required field was not supplied" to every partial body**, and a body that did
  supply those two nulled the other three — populated on **18 of 19** live rows in dev and **19 of
  19** in prod. **F2-213 fixed exactly this shape in `updateVendor` SIXTY LINES ABOVE and left this
  one** (F2-215: two of N, and the mechanism a reader finds present makes the rest look covered).
  🔑 Found by the **probe**, not by a gate, and recorded that way —
  `FullReplaceUpdateContractTest` passes over it because the method persists the loaded row rather
  than its own parameter.
  ⚠⚠ **AN EXISTING TEST'S FIXTURE DEPENDED ON THE INITIALISER, AND ONLY THE FULL SUITE SAW IT.**
  `CorrectiveActionServiceTest.aggregate_countsByType` went 1 → 0 on CORRECTIVE: its helper built
  `new CorrectiveAction()` and leaned on the entity's `actionType = "CORRECTIVE"` to make one of two
  rows corrective, while explicitly setting PREVENTIVE on the other. 🔑 **A fixture where one value
  comes from the entity and the other from the test cannot tell you which half is load-bearing**
  (F2-179) — and here the silent half was the defect. When this family's fix breaks a test, read the
  fixture before the code.
  ⚠ **Four detector errors while deriving this, in both directions** (F2-228): the body-type regex
  demanded `@Valid @RequestBody` — a literal `@` before an optional group — and reported
  `body=None` on **all 11** routes; the frontend path match was a literal while the house idiom is
  `const BASE = '/api/…'` plus a template, so it reported **0 frontend clients**, the flattering
  direction here; the omit check read only the call ARGUMENT, so the opaque
  `updateFrameworkItem(id, body)` read as sending nothing; and with locals resolved it still missed
  `weight,` because an **ES6 SHORTHAND PROPERTY** does not match `field\s*:`.
  ⚠⚠ **`([^\r\n]*)$` CANNOT MATCH IN A CRLF FILE.** With `(?m)`, `$` sits immediately before `\n`,
  i.e. AFTER the `\r` the character class is forbidden to consume — so a whole-line edit script
  reported **0 of 19** on a tree it was about to edit correctly. **The CRLF trap for the fourth time
  in this lineage** (F2-212, F2-229, F2-234), and in the script where I had just added CRLF handling
  for the OUTPUT. Use `(?=\r?\n|\Z)`. ⚠ Its sibling: `([ \t]*)` before a field declaration captures
  the spaces between a **same-line `@Column(...)`** and the field — this codebase column-aligns them
  — so a `//` comment inserted at that offset lands mid-line and comments out the declaration.
  Anchor whole-line edits at the line START.
  ⚙ **Filed:** `tprm.vendor_assessments` has 3 CHECK constraints and `assessment_type` is
  unconstrained (cheap now that this slice closed the register's open writers — F2-183); F2-239's
  `sequence_order` / `weight` / `mandatory` / `is_placeholder` CHECKs on
  `platform.authority_document_citations` remain open, `weight` still `decimal(5,2)` with no declared
  range; and **the structural root is unchanged at 94 entity-bound `PUT`/`PATCH` routes** — a
  purpose-built request record per endpoint makes resets, refusals and mass assignment alike
  unrepresentable, because a field the bound type does not declare can never arrive. Owner decision.
- ⚠⚠ **THE ORDERING THAT FINDS THE LIVE MEMBERS OF THIS FAMILY IS *WHICH FIELD THE CALLER
  OMITS* — NOT THE BODY'S WIDTH AND NOT WHAT THE INITIALISER ASSERTS** (F2-239, measured
  2026-08-18). Executing F2-238's filed 28-site `PUT` work list, the population was re-derived
  (F2-221): 14 routes across 12 services, **all binding the ENTITY** (resolved from the
  controllers), 10 with a frontend write client. **4 of 28 are live.** 🔑 **F2-238 ranked its
  own filing by what the initialiser asserts, and so named one field that is not reachable and
  the wrong half of the other**: `GhgEmissionRecord#coveragePercent` (→ 100.00) IS sent by its
  only caller, and the live `AuthorityDocument#isPlaceholder` is the **CITATION's** — a
  different entity, edited by a *second form on the same page* with inputs for three fewer
  fields. **Harm-if-reached and reach are two orderings and they disagree; carry both.**
  ⚠⚠ **THE SHARPEST RESET CONTRADICTS THE REQUEST THAT PRODUCED IT.** The register's exact
  6-key edit body took a planted citation from `seq=300 / placeholder=False / AR_OFFICIAL` to
  `seq=0 / placeholder=True / EN_ONLY`. Measured identical in dev and prod over 214 live
  citations: **207 are not placeholders, 212 carry a non-zero `sequence_order`, 207 are past
  EN_ONLY** — and document 9 alone holds **167 citations spanning 100..4360, 159 distinct**, so
  editing one NCA ECC requirement paragraph moved it above section 1-1-1 (`OrderBySequenceOrderAsc`
  server-side, `citationsToTree` client-side) and badged a transcribed sub-control a placeholder.
  The citation form **has** Arabic inputs and sends them (`title_ar` on 214 of 214,
  `requirement_text_ar` on 196), so **a human translating a requirement into Arabic and pressing
  Save recorded it English-only** — house rule 7, and F2-213's reset-is-worse-than-a-null one step
  further: this reset asserts the absence of the exact data the caller just supplied.
  🔑 **The correct artefact was 20 lines above the wrong one in the same file** — `createCitation`
  already defaults all five fields and *inherits* `isPlaceholder` from the parent document, which
  `updateCitation` discarded on every edit.
  ⚠⚠ **AND THE OTHER HALF WAS NOT A RESET AT ALL — BOTH ITS WRITE ROUTES REFUSED.**
  `ChangeTask.titleAr` is `@NotBlank` (F2-30) on an entity bound with `@Valid`, so `POST
  /changes/{id}/tasks` with the add-form's body and `PUT /changes/tasks/{id}` with the panel's
  `{status: next}` both answered **400 "titleAr: Field must not be blank"**: **neither the "Add
  task" button nor the status toggle on the change implementation checklist had ever worked.** The
  same PUT with `titleAr` echoed back answers 200 and collapses `sort_order` 300 → 0 — the reset
  sitting behind the 400. 🔑 **A reachability derivation that stops at the caller's BODY cannot see
  that the route REFUSES it** — F2-238's the-throw-was-holding-the-write-shut from the other side,
  in a mechanism with no hand-written guard in it, because bean validation runs before the service
  is entered and **cannot tell an OMITTED mandatory field from a BLANK one.**
  🔑 **F2-105 DIAGNOSED THIS EXACT MECHANISM, WROTE ELEVEN LINES EXPLAINING IT, AND FIXED IT IN THE
  FRONTEND** — the PIR card resends `{...change}` under a comment saying it *"400'd on every save
  from the day F2-30 landed"*. That closes one caller; it is not a defence (F2-237), and **the
  write-up is what made the two siblings in the same controller look covered** (F2-215): a reader
  who finds the mechanism explained does not then ask which other routes share it.
  ⚙ **The fix, and where the invariant belongs.** Remove the initialiser, own the default on the
  create path (9 initialisers, 3 entities — and **0 `new X()` producers**, checked, so the service
  creates are the whole population); **drop `@Valid` from a partial-update PUT** and put the
  mandatory-field invariant on the ROW (`requireNotBlank` refuses a BLANK title with a 400 naming
  the field while an OMITTED one leaves the stored value alone), keeping it on the CREATE routes
  where a full entity genuinely arrives; and give the add-task form the Arabic input house rule 7
  requires, which is what makes the create's 400 correct rather than the defect.
  ⚠ **`ClosedVocabulary` per COLUMN for the translation status**: `updateCitation` validated
  nothing, so a caller reached a CHECK-constrained column and got the CHECK's 409 rather than a 400
  — and the collation is case-INSENSITIVE, so `'ar_official'` was **accepted and stored verbatim**
  while the badge, tone map and label lookup all compare case-sensitively (F2-179; probed pre-fix).
  `require` not `coerce` (F2-182). It also retires a **seventh hand-rolled vocabulary** that
  `HandRolledVocabularyMirrorTest` never listed.
  ⚠⚠ **THE NEW GATE LEG IS A HARD ZERO WITH AN EMPTY EXEMPTION MAP, AND THE ANNOTATION ORDER IS
  THE TRAP.** `noPartialUpdateRouteBindsAnEntityWithValid`: **172 PUT/PATCH routes take a body, 94
  bind an entity, 9 of 411 entities declare a bean-validated mandatory field, exactly 2 routes were
  in the intersection.** A hard zero is safe because the better fix is always available — bind a
  purpose-built request record (F2-213's structural direction), which puts the route outside the
  population by construction — so there is no correct code for it to fail on (F2-214). ⚠ It passed
  on its first run, which is also what a rotted regex looks like: restoring master's controller makes
  it fail naming both routes and nothing else. **The house idiom is `@Valid @RequestBody T x`, and a
  pattern anchored on `@RequestBody` looking only FORWARDS reported 0 of 2** — indistinguishable from
  a clean tree, the quiet direction again.
  ⚠ **Two derivation errors of mine, in OPPOSITE directions** (F2-228's rule: two such errors produce
  an answer neither earns). Attributing FE call sites by wrapper NAME **over**-reported — `NdiPage`'s
  `updateCapa` is `lib/ndiGaps.ts`'s, over a different register, which is F2-187's recorded mistake.
  Resolving by import statement then **under**-reported, missing `changeApi.updateTask`: a
  **NAMESPACED** member of an api-group object, where the imported name is the group and not the
  method. Both shapes are needed.
  ⚙ **Filed:** the residual **19-site work list**, ordered by reach rather than by initialiser
  severity, with the 4 sites whose route has no FE write client at all named as least urgent (read
  the route and probe it; do not write 19 reasons — F2-188);
  `platform.authority_document_citations` has a CHECK on `translation_status` and **none** on
  `sequence_order`, `weight`, `mandatory` or `is_placeholder` — cheap now that this slice closed the
  only open writer (F2-183), and `weight` is `decimal(5,2)` with no declared range; `createCitation`
  defaults `sequenceOrder` to 0 while the FE create sends nothing, so every UI-added requirement
  paragraph lands at the top of its regulation (a product question, not this family's defect); and
  the structural root, now measured at **94 entity-bound PUT/PATCH routes** — a purpose-built request
  record per endpoint would make resets, refusals and mass assignment alike unrepresentable, which is
  an owner decision.
- ⚠⚠ **A `PATCH` WHOSE OMITTED FIELDS ARE WRITTEN IS A CONTRACT VIOLATION, AND WHERE THE
  NEVER-FIRING GUARD'S BODY IS A `throw` THE ROUTE REFUSES INSTEAD — THE THROW WAS HOLDING THE
  WRITE SHUT** (F2-238, measured 2026-08-18). Executing F2-237's filed 46-site work list, the
  population was ordered by **reachability** rather than field count: all 20 routes bind the ENTITY
  (re-checked), 14 of 20 have a frontend write client, and **10 narrow-literal call sites across 9
  services** send a body narrower than the entity. **Six of the twenty are `@PatchMapping`, and that
  is the slice boundary — derived, not chosen**: a `PUT` has full-replace semantics as a defensible
  reading, so omitting a field there is arguably a request for the default, while a PATCH's whole
  contract is that omitted fields are left alone.
  🔑 **ONE SHAPE, TWO CONSEQUENCES, decided only by whether the guard's body is a WRITE or a THROW.**
  WRITE → silent reset: `CloudServiceService#update` rewrote 9 columns, and the register's **only**
  write is `update(svc.id, { status })` because `CyberCloudServicesPage` has no edit form at all.
  Probed live as `so_officer` against a planted row — `PATCH {"status":"UNDER_REVIEW"}` answered 200
  and turned `IAAS / PRIVATE / ksa=true / CRITICAL / sso / mfa / atRest=true / inTransit=false` into
  `SAAS / PUBLIC / ksa=false / MEDIUM / all-false / inTransit=true`: **8 of 8 fields**. Measured
  identical in dev and prod — SSO, MFA and encryption-at-rest true on **14 of 15** live rows,
  kingdom-data-residency on **10 of 15**, and 3 rows are `GOV_PRIVATE` (the National Government
  Cloud, the SDAIA/NDMO national data platform, the sanctioned analytics platform), so a status
  change reclassified the tenant's most sensitive services as **PUBLIC cloud** while clearing every
  NCA CSCC flag. `BcmComplianceMapping#isPlaceholder` differs on **392 of 412**.
  THROW → the route refuses: three registers carry
  `if (patch.getStatus() != null && !patch.getStatus().equals(existing.getStatus())) throw` over a
  status the entity initialises, so the null test can never be false and the **comparison** fires for
  every row not in the initial state — **5 of 5** crisis events, **4 of 7** BIA assessments, **6 of
  13** issues could not be PATCHed at all.
  ⚠⚠ **AND IN THOSE THREE THE THROW WAS HOLDING THE WRITE SHUT.** A row past the initial state got a
  400 and was therefore *safe* from the resets; a row still in the initial state was reset. **Fixing
  only the throw converts a broken route into a data-destroying one** (F2-203). That is why the fix
  is *remove the initialiser* and not *rewrite the guard* — it closes both halves in one move. It is
  also why the crisis/issue write half **cannot be demonstrated pre-fix at all**, which is the
  finding rather than a gap in the probe.
  🔑 **`encryptionInTransit` is the one flag whose initialiser is not `false`, so its reset
  MANUFACTURES an assurance** instead of losing one — a service recorded as not encrypting in transit
  becoming one that does. F2-213's *a reset is worse than a null* at its sharpest, and latent (0 of
  15 rows hold false today). **When you find this family, sort the fields by what their initialiser
  ASSERTS, not by how many there are.**
  ⚠⚠ **FIXING THE RESET DETONATED THE NEXT DEFECT EXACTLY AS PREDICTED, AND THE RESET HAD BEEN
  MASKING IT.** `update()` ends with `validate(existing)`; `DEPLOYMENT_MODELS` was
  `{PUBLIC, PRIVATE, HYBRID, COMMUNITY}` while `cyber.cloud_services` carries **no CHECK constraint
  of any kind** and stores `GOV_PRIVATE` on 3 live rows in both databases. Pre-fix the reset
  overwrote the column with `"PUBLIC"` *before* `validate()` saw it, so **preserving the stored value
  — the correct behaviour — is what makes `validate()` reachable for it**: omit the token and the fix
  turns a silent reset into a 400 on the National Government Cloud. Declaring it server-side and in
  `CLOUD_DEPLOYMENT_MODELS` is part of the same fix, not a tidy-up. F2-180 inverted — the only
  narrowing was wrong about what the register holds.
  ⚠ **The fix shape and its one hazard: no entity had a `@PrePersist`**, so those initialisers were
  the only default mechanism for three Java producers that bypass the service create. `CcmService`
  (F2-228's third-consecutive-CRITICAL-failure issue) and `KriBreachService` route through
  `issueService.create`, so a create-path default covers them; **`BcmAuditCompletionService` saves
  straight to the repository** and set only 3 of `CrisisEvent`'s 5. Every one of these columns is NOT
  NULL, so **a writer that stops setting one turns a silent reset into a 500 on create** — and with
  `ddl-auto=none` nothing else in the tree would notice.
  `everyRemovedInitialiserHasAWriterOnTheCreatePath` asserts all 26 (entity, field, writer) triples
  in both directions.
  ⚠⚠ **A PHANTOM IN MY OWN GATE, ONE SLICE AFTER WRITING IT.** It reported `CloudService#status` as
  still open after the cyber initialiser was removed, because `initialisedFields` keyed entities by
  **simple name** and merged with `computeIfAbsent` — and there are two live `CloudService` classes,
  the `module/outsourcing` twin declaring `status = "ONBOARDING"`. **F2-233's keying-ambiguity lesson
  in an instrument written one slice before it.** It OVER-reports, so it fails loudly rather than
  hiding a site, but 🔑 **a phantom entry in a hard-asserted list is worse than a missing one,
  because it looks measured.** Re-keyed to the fully-qualified name, pairing resolved by longest
  shared package prefix, both twins pinned by name in both directions. Work list **46 → 28**: 17
  fixed, 1 never real. ⚠ My own anti-vacuity leg caught the re-keying (`inits.get("MaterialTopic")`
  went null) — the leg doing its job.
  ⚠ **Two probe corrections, both from running it:** `compliance` is **403** on
  `/api/v1/cyber/cloud-services` (needs `CYBER:WRITE`, so `so_officer`), and `/api/v1/issues` is
  reachable **only by `admin`** because `PLATFORM:WRITE` is held by one role with zero active users —
  **F2-220's own open filing confirming itself**. Said out loud because *admin proves nothing about
  authorization*: that leg tests data semantics, and the guard is the filing.
  ⚙ **Filed:** the remaining **28-site work list**, all `PUT` routes — sharpest are
  `GhgEmissionRecord#coveragePercent` (resets to **100.00**, an ISAE 3410 assurance input) and
  `AuthorityDocument#isPlaceholder` / `AuthorityDocumentCitation#isPlaceholder` (reset to **true**,
  marking a real regulatory instrument a placeholder). Read the route and probe it; do **not** write
  28 reasons (F2-188). Also: `cyber.cloud_services` has **no CHECK on any of its four vocabulary
  columns**, now cheap since this slice closed its only open writer and declared the missing token
  (F2-183); and the structural root is unchanged from F2-213 — **83 entity-bound `PUT` handlers**,
  where a purpose-built request record per endpoint would make the whole family unrepresentable
  (a field the bound type does not declare can never arrive), which is an owner decision.
- ⚠⚠ **A NULL-GUARD ON A FIELD WHOSE ENTITY DECLARES AN INITIALISER CAN NEVER FIRE, SO THE LINE
  READS AS A PARTIAL UPDATE AND BEHAVES AS A FULL REPLACE** (F2-237, measured 2026-08-18). Executing
  F2-220's filed *"the population of transition-by-parameter registers is unmeasured"*, the population
  was re-derived first (F2-221) in Java over the same tree as the shipped detector (F2-213). **The nine
  registers `MEASURED_MACHINES` names are an anti-vacuity guard and never a census — the detector
  already saw 28**; a permissive scan that also walks NON-PUBLIC methods sees **30**, and the delta it
  can never see is 15 sites / 13 keys with 6 create-side hits. The real figure is now asserted beside
  the nine so the next reader cannot mistake one for the other.
  ⚠⚠ **FOUR GENUINE MACHINES ARE INVISIBLE TO THAT GATE FOR THREE DISTINCT STRUCTURAL REASONS.**
  A **different exception family** — `RecertificationService#decide` (the IAM four-eyes *"reviewers
  cannot certify their own access"*) and `SodService#disposition` both refuse with
  `ResponseStatusException`, which the inline `IllegalState|IllegalArgument|IllegalTransition` test
  cannot match: F2-231's headline on the machine side. A **refusal in a shared helper PLUS a write-verb
  name** — `EvidenceRequestService#updateStatus` fails two clauses at once. And **a PRIVATE
  machine** — `EvidenceItemService#transitionStatus`, behind five public wrappers; this gate's
  population is `publicMethods`, so it is invisible **by construction**. 🔑 **F2-198's shape at
  an ACCESS MODIFIER**, the eleventh narrowing after a filename, a three-entry allow-list, a module
  name, a controller-bearing table, an `extends` clause and a field-name list.
  `PlanDistributionService#transition` and `ChangeManagementService#closeCondition` are the other two
  private ones, which is exactly the 30 minus 28. All four are closed on the create side **today** by
  their own mechanism — a fact about today, not a property of the control (F2-182) — so they
  are asserted rather than assumed.
  ⚠ **9 of the 15 delta sites were my own detector over-matching** (a helper satisfying *"reads F
  on that row"* pulled in `titleEn`, `notesEn`, `planType`), and it pointed at a real defect anyway, of
  an **adjacent shape**: not a state machine but a **validated range**. Measured as its own population
  — 13 range-guarded fields, 11 bypassed pairs, 3 services — with **2 of the 3 refuted by
  reading**: `NdiAssessmentCycle#reportingYear` because `validate()` already carries the rule and both
  write paths call it (F2-182's shared-`validate()` lesson), and `ProcessorAssessment#assessedBy`
  because it is an actor id, not a range. 🔑 **The pattern was already in the tree, in the
  sibling artefact**: `NdiAssessmentCycleService.validate()` carries its range rule so no write path
  can miss it, while `MaterialTopicService.validate()` checked the topic name, the category, the
  reporting year and the status — and **not the two axes the whole register exists to record**.
  ⚠⚠ **THE LIVE DEFECT IS NOT THE RANGE, AND A UNIT TEST'S FAILURE WAS THE FINDING.**
  `MaterialTopic` declares `= BigDecimal.ZERO` on all four score columns because they are NOT NULL, so
  a PUT that omits the key deserialises to **ZERO, not null** — `if (req.getImpactMateriality()
  != null)` was true on **every single request**. Probed live as `compliance` against a topic the probe
  plants itself (F2-206), pre-fix control 5 legs firing and post-fix 11/11: a `PUT` carrying only
  `topicEn` and `category` took a scored topic from `3.5 / 2.0 / 3.5 / HIGH` to **`0 / 0 / 0.0 / LOW`**
  — **an edit that changed only the title destroyed a CMA Article 92(a) / CSRD-ESRS
  double-materiality input**, and `recomputeScore` runs on every save so the score and the tier
  followed it down.
  🔑 **THE FRONTEND WAS CARRYING THE WORKAROUND.** `EsgMaterialityTab`'s edit handler echoed
  both axes back with a comment explaining that *"an omitted value would zero out the score + tier"*.
  **A defence in one client is not a defence** — the next caller does not have it, and this family
  is reachable exactly where machine callers live (F2-179). ⚠ **And the create was worse than
  latent**: the pre-fix probe stored `impact=4.6` beside `stakeholderImportance=0`, so the two
  spellings of one fact **disagreed from birth** — 0 disagreeing rows in dev and prod only because
  every seeded row came from `seed_real_data.py` rather than through the API.
  ⚠⚠ **NO GATE IN THIS TREE COULD SEE IT, AND ONE OF THEM WAS BLIND FOR THIS EXACT REASON.**
  `FullReplaceUpdateContractTest`'s structural leg asks whether a method persists its own request
  parameter (this one persists the loaded row); its coverage leg asks whether a written field is
  *declarable*, and with a `Partial<MaterialTopic>` client every field is (F2-214); and
  `ServerStampedFieldContractTest` **subtracts anything a null-guard protects — the exact
  subtraction this defect defeats**, because the guard is decorative. **Before trusting a
  null-guard, check whether the entity gives the field an initialiser.**
  ⚙ Fixed with one private `applyAxes()` as the only writer of all four columns (range via
  `requireAxis`, the legacy mirror written FROM the canonical axis, then recompute), the range **also**
  in `validate()` so a future write path that forgets the writer still cannot store an out-of-range
  score, and **the edit route writing none of the four** — the axes belong to `create()` and to the
  dedicated Score action, which is what the register already said out loud. `require` not `coerce`
  (F2-182). The UPDATE audit line now **names the derived move** instead of reading *"Material topic
  updated"* whatever changed (F2-188).
  ⚠⚠ **V379 ADDS THE TWO RANGE CHECKS THE CANONICAL PAIR NEVER HAD, AND *WHICH* TWO WERE
  MISSING IS THE POINT.** `sust.material_topic` already carried **five** CHECK constraints, two of them
  bounding `stakeholder_importance` and `business_impact` to 0..5 — the **LEGACY** mirror pair
  — while the canonical `impact_materiality` / `financial_materiality` that every current reader
  prefers had none. **F2-215's two-of-N shape, at the schema**: a reader who finds range CHECKs on this
  table does not then ask which columns they name. Scope tied to the writers the same commit closed
  (F2-183); measured in both databases first (26 live rows each, 0 outside range, 0 disagreeing
  mirrors) so no `WITH NOCHECK`; no casing normalisation, because `decimal(3,1)` cannot carry F2-179's
  collation trap. ⚠ The pre-fix probe shows the migration and the writer are two halves: with V379
  applied and the service reverted, an out-of-range create answered **409 naming no field**; the
  service fix is what makes it a **400 naming `impactMateriality`**.
  ⚠⚠ **THE GENERAL SHAPE IS 46 SITES ACROSS 20 SERVICES, SHIPPED AS A WORK LIST WITH NO
  VERDICTS** (F2-188). `NeverFiringNullGuardContractTest` derives every entity-bound update guarding
  `if (req.getX() != null)` on a field the entity initialises — 50 before this slice — exactly
  asserted in both directions, with a synthetic self-test driving the detector against the literal
  pre-fix shape and three shapes it must NOT flag (F2-195). A DTO cannot have this shape (a record
  component has no default), so the population is exactly the entity-bound writes. Two things are clear
  from the initialisers alone: **a reset is worse than a null** (F2-213 — `false`, `0`,
  `"MEDIUM"` and `new BigDecimal("100.00")` read as facts rather than as missing data), and several are
  **flattering in the direction that matters** — `GhgEmissionRecord#coveragePercent` resets to
  **100.00** (an ISAE 3410 assurance input), `CloudService#encryptionInTransit` to **true**, while
  `CrisisEvent#severity` resets to **1** and `AuthorityDocument#isPlaceholder` to **true**, marking a
  real regulatory instrument a placeholder. **Read the route and probe it before writing a reason.**
  ⚠ **My own claim was refuted by my own gate on its first run**: the first draft asserted
  `EvidenceItemCreateRequest` declares no `status` — *"the strongest closure there is, because a
  field the bound type does not declare can never arrive"* — and **it declares one**. The closure
  is the weaker kind, where the client may send it and `applyRequest` silently drops it (F2-184's
  shape, safe here but not the same claim). Roughly two in ten of a new instrument's own claims fall on
  first run (F2-188, F2-192, F2-204, F2-211), and the reverse-facing assertion is what found it.
  ⚠ **The heredoc landmine fired again**, on a `python - <<'PYEOF'` patch script whose `\\b` and
  `\\w` collapsed — loud this time (an `AssertionError` on a pattern that no longer matched).
  **Use the Write/Edit tools for any file content carrying escapes**, and note that the standing
  instruction to prefer Bash does not extend to that.
  ⚙ **Filed:** the 46-site work list; and `sust.material_topic`'s stored derived values, which are
  **larger than F2-230's** — **20 of 26 live rows hold a `materiality_score` LOWER than
  `MAX(impact, financial)` and 0 higher**, so the register under-states materiality; **14 priority tiers
  would move on the next save, 12 of them to CRITICAL**; and **8 rows' tier contradicts their own stored
  score** (id 14 stores 4.5 with tier HIGH where the declared bucket is CRITICAL, which proves the
  stored tier was never computed by this code). Identical in dev and prod, each self-repairing the first
  time an operator edits it — which is why the audit line now names the move. Re-deriving 26 live
  rows in two real databases is an **owner decision** (F2-183, F2-196, F2-230).
- ⚠⚠ **THE SERVER STAMPS *WHEN* FROM ITS OWN CLOCK AND THE CALLER NAMES *WHO*, AND TWO
  CONTROLLERS DEFAULTED THAT NAME TO A LITERAL** (F2-236, measured 2026-08-18). Executing F2-234's
  filed nine parameter-borne splits, every one was read — the service **and its controller**, since
  the wire-ness of a scalar parameter lives at the call site: **8 confirmed and fixed, 1 BY DESIGN, 0
  unread.** Probed **28/28 pre-fix and 28/28 post-fix** as `compliance`.
  ⚠⚠ **A DEFAULT OF `"admin"` IS NOT A PLACEHOLDER — IT IS A REAL PLATFORM_ADMIN USERNAME.**
  `SupplierResilienceTestController` read `body.getOrDefault("approverName", "admin")`, so an omitted
  approver did not leave an ISO 22301 supplier-resilience approval blank, it attributed it to the most
  privileged account in the platform. `FourthPartyExposureController` defaulted to **`"system"`**,
  which reads as an *automated* validation of a SAMA fourth-party concentration record when a human
  performed it — or when nobody did. **And the frontend passed the literal `'admin'` at FOUR call
  sites**, plus `'counterparty'` as a PDPL data-sharing signature. 🔑 **When you find a
  caller-supplied actor, look at what the caller sends when it says nothing: the default is the value
  that will be in most of the rows.**
  ⚠ The other live shapes: `POST /api/v1/committees/action-items/{id}/complete` stored
  **`completedBy: 9999`, a user id that does not exist**; and
  `POST /api/sustainability/materiality/{id}/approve` **REQUIRED** `approvedBy` and never verified it
  — a 400 when omitted, beside its own clock — the fourth register in this lineage to demand an
  attribution and then trust it (F2-223, F2-224, F2-226). **No escalation anywhere**: every guard is
  the one that register's other writes carry, so the machine was never the issue and the attribution
  was the whole of it.
  ⚠⚠ **CLOSING THE GUARDED ROUTE MAKES THE ORDINARY UPDATE ROUTE A BYPASS, AND ONLY THE OTHER
  GATE CAN SEE IT.** `ServerStampedFieldContractTest` went red on this slice's own fix: once
  `markUnderReview` stamped `reviewerName` from the principal, `NdiMaturityAssessmentService#update`
  writing that field from the request became a contradiction it could report **for the first time** —
  and a real bypass, since under the same `NDI:WRITE` the edit route could overwrite the recorded
  reviewer with anything. 🔑 **Two instruments in series, and the second only became able to see
  the site because the first was closed — so after converting an attribution, RUN THE OTHER GATE.**
  ⚠ The verdict there is per field **and per window** (F2-222's shape): `reviewer_name` is an
  ASSIGNMENT before the review — measured, 8 of 16 live rows in dev and prod hold an office name with
  `reviewed_at` NULL — and the ATTRIBUTION after it. `update` keeps the assignment, refuses a CHANGE
  once `reviewedAt` is stamped, and accepts an ECHO of the stored value so a form that round-trips the
  row keeps working (F2-231). Refusing assignment outright would be a gate driving a fix that fails on
  correct data (F2-214); tolerating the change would have made the whole slice pointless.
  ⚠⚠ **THE CORRECT ARTEFACT WAS THE ADJACENT CONTROLLER IN THE SAME PACKAGE.**
  `SupplierResilienceController` — the file beside `SupplierResilienceTestController` — has always
  passed `usernameOf(auth), userIdOf(auth)` for its own two approvals. N-th instance in this lineage
  of the right pattern sitting next to the wrong one, and the first where both are sibling controllers
  over sibling registers of one module.
  ⚙ **One primitive, eight call sites:** `CurrentActor.requireUsername(act)` / `requireUserId(act)`,
  added to the class whose `organisationId()` already establishes the fail-fast idiom rather than
  hand-rolling a ninth null check. **Refusing is deliberately louder than recording a blank: a
  governance record whose approver is `""` or `"admin"` reads as an attribution, so it is worse than
  one that could not be written.**
  ⚠ **The BY DESIGN survivor is now VERIFIED, not guessed.** F2-234 filed
  `DataSharingAgreementService#activate::counterpartySigner` as *"plausibly BY_DESIGN … and that is a
  GUESS"*. It holds: all **8 live rows in both databases name an external party** — an NCA liaison
  officer, a MoCI director, a university dean, a vendor DPO — and that person can never be a
  principal here. Its sibling `signerName` is OUR signatory and was closed, so the verdict is a
  **field-level boundary inside one method**, never a whole-method exemption.
  ⚠⚠ **AN EIGHTH SITE NO GATE REPORTED, FOUND BY READING THE FRONTEND.** `NdiPage`'s
  compliance-assessment Approve opened a `required: true` prompt for `approverName` and sent it to
  `NdiComplianceAssessmentService#approve`, **which takes no actor parameter and never has** — so the
  server discarded it on arrival. F2-225's shape: a required field the server ignores tells the caller
  their value matters. 🔑 **A prompt asking for an attribution is a locator for this family, and
  it finds sites on both sides: ones the server wrongly trusts AND ones it silently drops.**
  ⚠⚠ **THE FIFTH TEST ASSERTING THE DEFECT AS CORRECT, ONE LINE BELOW ITS OWN STATEMENT OF THE
  RULE.** `CreateNamesNoDecisionTest` read `assertThat(live.getSignerName()).isEqualTo("Real Signer")`
  — that the value the CALLER passed is the value STORED — directly under the comment *"activate()
  stamps the signature date from the clock — that is the whole reason a create may not name it."*
  After F2-159, F2-175, F2-227 and F2-234. Repurposed the way F2-223 and F2-234 repurposed theirs.
  ⚙ **Four of my own errors, every one caught by running something.** (a) **Four of seven route paths
  were guessed and wrong** — `/api/scm/resilience/tests`, `/api/privacy/data-sharing`,
  `/api/scm/fourth-party`, `/api/sustainability/materiality`, `/api/ndi/plans`; resolve from
  `@RequestMapping`, the rule recurring since F2-198. (b) The prod database is
  **`TSS_GRC_Deployment`**, not the name I guessed. (c) The probe hit **two different unique indexes**
  on its second run — `UQ_action_item_code` and `uq_ndi_mat_cycle_item` — until each slot was
  **derived from the index** rather than fixed (F2-217: re-runnability is a different property from
  cleaning up, and a unique-index violation reads exactly like the fix having broken). (d) A planted
  committee row 400'd on **`titleAr must not be blank`** although the column is nullable — the
  recorded landmine that a constraint on an `@Entity` field is enforced by Hibernate on save, and
  house rule 7 in the same breath.
  ⚙ **Filed:** the **office-versus-person meaning** now carried by five columns whose every populated
  value is an office title (`CIO`, `CISO`, `President of the Authority`, `Chair, Data Governance
  Committee`) while the platform now records a username — F2-222 filed the same shape for
  `policy.documents.approver_name`, and recording *which body* authorised something is a second field
  and an owner decision; `ndi.improvement_plan` holding **1 approver with no `approved_at`** and
  `ndi.maturity_assessment` **8 reviewers with no `reviewed_at`**, i.e. attributions with no clock, so
  those rows never went through the routes that stamp both; and
  `scm.fourth_party_exposures.validator_name` holding **5 invented person names** on live rows in a
  *server* register (F2-207's measured cast of 65) — repair rewrites live rows, so owner decision.
- ⚠⚠ **A RAW CONTROL BYTE IN SOURCE MAKES THE FILE INVISIBLE TO EVERY GREP-BASED
  INSTRUMENT IN THIS REPO, AND EVERY MEASUREMENT HERE IS A GREP-BASED INSTRUMENT** (F2-235,
  measured 2026-08-18). Grepping `SplitAttributionContractTest` to re-read F2-234's work list, the
  tool answered **`binary file matches (found "\0" byte around offset 27186)`** and returned **no
  lines**, for a pattern occurring many times in that file. ripgrep classifies a NUL-bearing file as
  **binary**: a content-mode search prints that line and nothing else, and a search whose pattern
  does not match prints **nothing at all** — indistinguishable from a clean file. Census over the
  three trees every instrument scans (**3,557 files**): **3 files held raw control bytes.**
  🔑 **All three were BEHAVIOUR-PRESERVING, which is exactly why they survived a 4,073-test green
  build** — a raw 0x00 inside a Java string literal is legal (JLS: a `StringCharacter` is any
  `InputCharacter` but `"` and `\`), and in a JS regex literal `[\s<NUL>-<0x1F>]` denotes precisely
  the range `[\s\x00-\x1f]` does. Nothing failed and no test could notice.
  ⚠⚠ **The sharpest instance had been unreadable since the commit that created the thing worth
  reading.** `KbArticlePage.tsx` holds `sanitiseUrl`, the strict URL-scheme allowlist for markdown
  links rendered through `dangerouslySetInnerHTML` — the guard that stops
  `[click me](javascript:alert(1))` executing with the reader's session — and its NUL + 0x1F have
  been there since **`957b679e`**, whose message reads *"3 real bug fixes: … KB stored-XSS (#120)"*.
  🔑 **The one frontend file most worth reading in a security sweep was the file no security sweep
  could read.** And `git diff --stat` reports it `Bin 6962 -> 8366 bytes`, so **git will not show a
  textual diff for it either** — the collapse was invisible in code review as well as in every scan.
  ⚠ **The other two are the reusable pair.** One was **F2-234's own escape collapse, one slice
  earlier**, in the gate that measures split attribution — so the instrument was unreadable by the
  tool used to measure everything else. One (`IntakeSystemSubmissionTest`, a raw 0x01) was
  **deliberate**, F2-175's C0-control payload fixture, and therefore not a defect — but written raw,
  the test's whole subject was invisible to a reader, who saw `\"v2\" model` with an unexplained gap.
  Rewritten `\001`: identical at runtime, legible in the diff. 🔑 **A deliberate control byte is the
  argument FOR the hard zero, not against it** — it kept its behaviour and gained a reader.
  ⚠⚠ **The guard was sound, had NO TEST OF ANY KIND, and the comment on its load-bearing line
  denied the mechanism that keeps it shut.** `renderMarkdown` escapes `&`/`<`/`>` across the whole
  document in step 1 and rewrites links in step 3, and that ORDERING closes the vector the allowlist
  cannot see: written with real entities, `&#106;avascript&#58;alert(1)` contains **no colon at all**,
  so `!trimmed.includes(':')` — the relative-path fallback — **accepts it**, and a browser decoding
  the attribute would get `javascript:` back. What makes it inert is that step 1 already turned `&`
  into `&amp;`. **Move step 1 below step 3 and the guard opens with no other change.** The comment
  there read *"the HTML-escape pass above already handled <, >, & **but the raw URL never passed
  through it**"* — false, in the one direction that matters (F2-184's shape, on the sentence a reader
  consults before touching the pipeline).
  ⚠⚠ **THE MUTATION HARNESS SHOWED THE OBVIOUS BLOCKED-VECTOR ASSERTIONS DO NOT DEPEND ON THE
  MECHANISMS THEY APPEAR TO TEST.** Deleting the leading-control strip leaves every
  `<TAB>javascript:` case **still blocked** — the colon rule does that work — and deleting
  `.toLowerCase()` leaves `JavaScript:` blocked for the same reason; both mutations SURVIVED. Their
  only observable effect is on a **legitimate** URL (`<TAB>https://sama.gov.sa`,
  `HTTPS://SAMA.GOV.SA`), so two POSITIVE vectors had to be added before either could be killed.
  🔑 **A test that blocks the right things can still be blind to why** — and the code comment
  naming the tab as *"a real XSS bypass in older WebKit"* points at the strip, which is not the
  defence. 6 killed / 0 survived / 0 anchor-miss; a 7th **withdrawn as invalid** rather than counted,
  because it weakened one of my own assertions and you can always delete an assertion (F2-206).
  ⚠⚠ **MY OWN GATE FILE CONTAINED TWO RAW CONTROL BYTES AND WOULD HAVE FAILED ITS OWN
  INVARIANT.** I wrote the self-test's NUL and 0x1F literally and they landed on disk faithfully.
  Fixed as the octal escapes `\0` and `\037`, which is the claim the gate makes; the new test builds
  its control characters with `String.fromCharCode`. 🔑 **A test that pins this defect must not be
  able to contain it** — and an escape is the only form that survives every tool between an author
  and a file, which is the same rule as *write scan scripts with a file write, never a heredoc*.
  ⚠ `config/ScannableSourceContractTest` is a **hard zero with NO exemption map**, and that is safe
  rather than strict (F2-214): Java and TypeScript both offer an escape for every control character,
  so a raw one is never the only way to express an intent — there is no correct code for it to fail
  on. Four legs: the invariant over a **derived** population (walk, never list); a **per-root** floor
  rather than one total, because the wrong answers are *a root that stops resolving* and *a scan that
  reaches one tree* (2,000 / 400 / 700 against measured 2,286 / 446 / 825 — F2-215: a floor must sit
  above the largest WRONG answer); a **synthetic self-test** that the detector fires on 0x00 and 0x1F
  and not on tab/CR/LF (F2-195); and a leg naming **the three files by path**, since a count can be
  satisfied by the wrong files while a walk that resolves the backend but not the frontend passes.
  ⚙ **Filed:** `DB-11072026.sql` at the repo root is a 13 MB **UTF-16LE** dump (13.4 M NUL bytes) and
  **untracked** — not in the repo, but local `*.sql` sweeps skip it; excluded by root scoping rather
  than by name. And the `//evil.com` protocol-relative case is **REFUTED as a finding**: `/` admits it
  and the comment says *"same-origin relative paths"*, but a KB author who can write a link can
  already write `https://evil.com`, so it grants nothing — noted only because the comment overstates
  what `/` means.
- ⚠⚠ **ONE QUESTION, ASKED OF A PARAMETER, ANSWERED ON ONE SIDE OF THE WIRE** (F2-234,
  measured 2026-08-18). Executing F2-233's filed **4 BCM services**, the population was re-derived
  from the gate's own printed output first (F2-221): 7 sites, all four controllers binding the
  ENTITY (checked, not assumed). All were open, and reading one of them found a SECOND instrument
  finding of exactly F2-233's shape. `BcmComplianceMappingService#assessCompliance` stamps
  `assessedAt` from the server's clock and copied `assessedBy` out of the request — F2-223's
  split attribution on an ISO 22301 / SAMA OPRES / NCEMA register, with the UI half being
  `window.prompt('Reviewer name')` and **`?? ''` on Cancel**, so the recorded assessor could be
  anybody or the empty string. 🔑 **It survived F2-223's sweep because the actor half reaches
  its setter as a BARE SCALAR PARAMETER**: `SplitAttributionContractTest`'s `REQUEST_VALUE` needs a
  named receiver (`req.`/`body.`/…) and `m.setAssessedBy(assessedBy)` names none — the
  wire-ness lives at the CALL SITE, where the controller passes `body.assessedBy()`. **F2-222
  established exactly that rule** (*"whether a scalar parameter is caller data is a fact about its
  call sites"*) **and taught it to the STAMPING side only.** Same structural asymmetry F2-233 found
  for fallbacks one slice earlier, where the accepting side had carried the mirror since F2-219 and
  the stamping side never did. **Assume a third instance exists.**
  ⚠⚠ **MY OWN MEASUREMENT REPEATED F2-187'S RECORDED MISTAKE AND I NEARLY BELIEVED IT.**
  The first resolver matched `.<method>(` in **any** controller without resolving the receiver's
  type, so `approve` picked up every unrelated `approve(` in the tree and reported ~20 sites "from
  the wire" that were almost entirely cross-contamination. F2-187's words are *"a method-NAME
  fallback silently clears the methods most likely to be duplicated elsewhere — resolve per
  owner type."* Resolved to a field DECLARED of the service's own type, the honest answer is **13 of
  92**; the other **79 correctly take the actor from `actor(auth)` / `userIdOf(auth)` /
  `currentPrincipalName()`**, which is why that gate's green was mostly earned. The classifier then
  removes 2 as ASSIGNMENT and `REFERENCE_TAIL` 1 as a document reference, leaving **9 filed with NO
  verdicts** (F2-188).
  ⚠ **`appointedByReso` is a demonstrated false positive and it is the negative control.** The
  wide `By[A-Z]\w*` branch exists so `approvedByLegalName` pairs; it also matches a field whose own
  javadoc reads *"Board-resolution code that appointed this member."* A board-resolution CODE is not
  a person. Pin such a narrowing in **both** directions — 6 must-pair, 4 must-not-pair —
  because a narrowing that is too wide silently SHRINKS the population, which reads exactly like a
  clean tree.
  ⚠⚠ **THE LIVE HALF: A CREATE THAT SKIPPED AN EVIDENCE REQUIREMENT, AND F2-220 HAD FIXED
  THE IDENTICAL TWO LINES IN THE SAME FILE.** `BcmRemediationService#createCapa` had
  `if (status == null) setStatus("OPEN")` plus a MEMBERSHIP test, so `CLOSED` was accepted: probed
  live as `compliance`, `POST /api/v1/bcm/capas {"status":"CLOSED"}` answered **200** while
  `POST /capas/{id}/transition {"to":"CLOSED"}` answers **400 — "Cannot CLOSE without
  evidence"** for the identical edge, and a caller-chosen `closureDate` was stored on an action never
  closed. The row was then unmovable (`updateCapa` refuses a CLOSED CAPA, `transitionCapa` has
  `case "CLOSED" -> false`), so a `BCM:WRITE` holder could manufacture a record only `BCM:APPROVE`
  can touch. 🔑 **F2-220 closed `createGap` eighty lines above, with this primitive, under a
  comment naming this exact defect** — not a sibling file, not a sibling module, the same file.
  F2-215's headline at its sharpest.
  ⚠⚠ **A CREATE WITH NO VOCABULARY TEST AT ALL, OVER A COLUMN WITH NO CHECK, PRODUCES A ROW
  NO ROUTE CAN EVER MOVE.** `BcmService#createPlan` was a bare `save(req)`: `status=APPROVED`
  answered **201** and so did `status=TOTALLY_MADE_UP`, and since `bcm.bcm_plans` has **zero CHECK
  constraints** on a NOT NULL `status` and every transition guard is an equality test against a named
  state, that row is **permanently untransitionable by every route** (F2-220's `platform.issues`
  shape, creatable on demand through the API). `updatePlan` wrote the status unconditionally, so
  `PUT` performed DRAFT → APPROVED at **200** under the same `BCM:WRITE` the guarded route
  carries — no escalation, the machine was simply the only control (F2-219's shape). The page
  already offered all four lifecycle buttons row-conditionally, so the form's status select was a
  second unguarded way to do what they do properly; removing it costs nothing.
  ⚠ **An ECHO is accepted, a CHANGE is refused** (F2-231's shape): a form that round-trips the
  row still works, and an omitted status leaves the stored state alone rather than nulling a NOT NULL
  column (F2-213's reset-is-worse-than-a-null).
  ⚠ **And the fifth field of five: `BcpPlanService#create` left `currentVersion` a DEFAULT one
  line below the four F2-220 had just scrubbed**, so a new continuity plan could claim **v7** with no
  `BcpVersion` row — and `snapshot()` derives the next number from the versions table, so the
  first approval would silently reduce 7 → 1. `addSection` stored caller-supplied
  `lastEditedAt`/`lastEditedBy`, an edit history for a section never edited. ⚠ Its two provenance
  markers (`template:<code>`, `BIA generator`, 21 of 120 rows) are written by paths that build the
  entity directly, never through `addSection` — checked, not assumed, which is what made
  clearing safe.
  ⚠⚠ **AN EXISTING TEST ASSERTED THE DEFECT AS CORRECT — FOURTH INSTANCE.**
  `assess_setsStatusReviewerTimestamp` passed `"BCM Manager"` and asserted
  `getAssessedBy()).isEqualTo("BCM Manager")`: that the value the CALLER passed is the value STORED,
  which is precisely what was wrong. After F2-159, F2-175 and F2-227. Repurposed the way F2-223
  repurposed its own — keep the half that protects the record, assert the attribution now comes
  from the principal.
  ⚠ **The work list is now EMPTY (`BASELINE_CONTRADICTED_SERVICES` 4 → 0) and that is
  exactly what a rotted detector produces** (F2-219: a floor of `>= BASELINE - n` cannot fail at 0).
  What carries the weight is the three reverse legs — every `BY_DESIGN`, `CLOSED_ELSEWHERE` and
  `FILED_OWNER_DECISION` entry must still name something the scan finds, so a detector that stops
  matching fails 15 assertions rather than reporting success. Do not simplify them away.
  ⚠ **Two of my own errors, both caught only by running things.** The mutation harness reported
  one **ANCHOR-MISS** because an anchor spanned two lines with `\n` against a **CRLF** file —
  the CRLF trap for the **fourth** time in this lineage (F2-212, F2-219, F2-229), in a harness whose
  own docstring states the rule; anchor-miss being a distinct non-zero outcome is the only reason it
  was visible. And my new gate leg failed on a **correct** tree because the javadoc explaining the
  removal of `assessedBy` NAMES the field it removed — the detector read the epitaph as the
  corpse (F2-193). **Strip comments in every scan, including one you wrote this hour.**
  ⚠ **Two probe bugs, both mine and both loud:** a guessed field name (`planTitleEn`; it is
  `titleEn`, resolvable from the entity) gave a 400 that reads exactly like the fix having broken, and
  asserting `200` where the controller returns `201` made a leg fail on a correct server.
  ⚙ **Filed:** the **9** parameter-borne splits with no verdicts — read
  `SupplierResilienceTestService#approve` first, whose controller passes
  `body.getOrDefault("approverName", "admin")` so an omitted approver is recorded as the literal
  **`admin`**; `bcm.bcm_plans` still has **no CHECK** on `status` (now cheap, since this slice closed
  its only open writers — F2-183); **two plan registers for one concept** (`bcm_plans` legacy vs
  `bcp_plans` canonical, both live since the T1.1 carve-out, F2-196's shape); and
  `bcm.bcp_sections.last_edited_by` holding an **invented officer name on 72 of 120 rows** in a
  *server* register (F2-207's cast of 65) — an owner decision, since repair rewrites live rows.

- ⚠⚠ **ROUGHLY A THIRD OF WHAT A WORK LIST REPORTS IS THE INSTRUMENT** (F2-233, measured
  2026-08-18). Executing F2-231's filed 11-service work list, **every one was read** and the population
  split almost evenly between the code being wrong and the detector being wrong:
  `BASELINE_CONTRADICTED_SERVICES` **11 → 4**, re-derived from the gate's own printed figure (F2-207),
  with about a third of the reduction coming from **four detector holes** rather than from fixes.
  🔑 **The corrected headline is NOT "seven services were fixed"** — that version is more
  flattering and less true, which is F2-228's shape one slice on.
  ⚠⚠ **(a) ONE COMMA IN A CHARACTER CLASS, WRONG IN BOTH DIRECTIONS AT ONCE.**
  `readOnlyOnTheWire()` matched *type token then field name* with `[\w<>,.\[\]]*` — that class contains
  a **comma**, so `@Column(name = "risk_score", precision = 4, scale = 1) private BigDecimal riskScore;`
  parsed `4,` as the type and **`scale` as the field.** Tree-wide: **4 phantom names** (`scale` ×3,
  `length` ×1) and **4 genuinely closed fields never subtracted** —
  `ProcessorAssessment#riskScore`, `NdiAssessmentCycle#complianceScore`,
  `VendorAssessment#workflowStatus`, `#computedScorePct`. Losing the real field **over**-reports (a field
  the wire already refuses is published as an open contradiction); inventing `scale` **under**-reports,
  because a phantom entry in the closed set would silently subtract any real field of that name.
  🔑 **A phantom exclusion that matches nothing scores exactly like a correct one**, which is why
  it survived two slices. ⚠ **My reading of that regex said `riskScore`, twice** — only running it
  against the entity gave `scale`. The derivation now carries the self-check it never had
  (`everyDerivedReadOnlyNameIsARealField`): **every captured name must be a field the entity declares.**
  ⚠⚠ **(b) THE HOUSE `Map`-PAYLOAD IDIOM IS TWO HOPS AND THE FIRST IS NOT AN ASSIGNMENT.**
  Every `/{id}/action` route writes `Object lvl = body.get("impactLevel"); if (lvl instanceof String s)
  e.setImpactLevel(s);` — so the value at the setter is `s`, bound by an **`instanceof` pattern** to a
  local that is itself an alias of the request. A resolver looking for `s = …` finds nothing and reads
  the caller's own value as a server stamp: **12 setter sites across 6 services**, including
  `Dpia#conclusion`/`#rejectionReason`. **(c) LAMBDA PARAMETERS** —
  `controls.forEach((k, v) -> … setC1(v))`, because `allParamNames` parses the METHOD's parameters and
  never a lambda's: **8 sites in exactly 1 service.** 🔑 That narrowness is the argument FOR fixing
  the detector: **a verdict says the code is right, a narrowing says the instrument was wrong, and eight
  fluent reasons for one detector bug is the artefact F2-188 measured at five claims, five false.**
  ⚠⚠ **(d) A FALLBACK IS NOT AN OWNERSHIP CLAIM — F2-218's HEADLINE READ ON THE STAMPING
  SIDE.** `if (p.getEffectiveFrom() == null) p.setEffectiveFrom(now())` supplies a value only when the
  caller did not, and a field the same method **also writes from the caller** at another site is not owned
  by it however many literals appear elsewhere. **The accepting side has carried the mirror of this since
  F2-219** (`settled.removeAll(defaults(...))`) **and the stamping side never did — that asymmetry is
  the finding**: one question, asked of the request on one side and of the loaded row on the other,
  answered on only one. The fields it hit are exactly the ones a professional is supposed to fill in
  (`Dpia#nextReviewDate`, the PDPL Art. 25 review cadence; `Policy#effectiveFrom`;
  `NdiCorrectiveAction#completionDate`), so every one was a gate failing on correct code (F2-214).
  ⚠⚠ **THE NARROWING'S OWN LIMIT WAS CAUGHT BY MY OWN GATE'S REVERSE LEG.** Following every
  identifier reached `c` in `SgiCommitment c = get(id);`, and `callerSupplied` answers TRUE for `get(id)`
  because `id` is a parameter — so a row **loaded from the database** read as caller data and
  `refreshValue`'s `c.setCurrentValue(sv.value())` stopped counting as a stamp.
  🔑 **The id is the caller's; the row it names is the server's.** The error direction is the
  opposite of the bug the widening fixes (a real stamp read as caller data HIDES a contradiction), which
  is precisely why the leg that caught it is the one nobody writes first (F2-199). **Stop the recursion at
  a store read** — anchored at the start of the RHS, because `body.get("reason")` must NOT read as a
  load and only what precedes `get` distinguishes them.
  ⚠⚠ **THE LIVE HALF: A PDPL ARTICLE 25 ASSESSMENT COULD BE FILED ALREADY APPROVED.**
  `DpiaController` binds the `Dpia` ENTITY and `create` did nothing but `setId(null)` /
  `setOrganisationId` / `save(req)`. Probed **19/19 pre-fix, 16/16 post-fix** as `compliance`:
  `POST /api/v1/privacy/dpias` with `status=APPROVED` answered **201**, skipping DRAFT → IN_REVIEW →
  APPROVED, plus a caller-chosen 2001 `rejectedAt` and an invented rejection reason — and the audit row
  said `DPIA_DRAFTED` whatever was stored. `PRIVACY:WRITE` guards the create **and** `/approve`, so there
  is no escalation: the machine was the only control and the create sat beside it without one.
  🔑 **`approvedAt` was ALREADY refused, because `Dpia` annotates that ONE field
  `@JsonProperty(READ_ONLY)` and not `status`, `rejectedAt`, `rejectionReason` or `conclusion`** —
  F2-215's two-of-N at one of five. The probe confirmed `approved_at = NULL` on the forged row, so **the
  single annotation left the dangerous forgery internally detectable and the REJECTED one seamless** (both
  of those columns are writable), and nothing reads either. `RegulatoryChangeService#create` was the same
  shape on the register recording how a SAMA/NCA/SDAIA change was assessed — filed **CLOSED** with a
  chosen `closedAt`, skipping four transitions that each demand something on the way through (F2-220) —
  and **F2-213 fixed the UPDATE and wrote the reasoning onto it while the create two methods above went
  untouched** (F2-215, fourth instance here). Both refusals are `require` not `coerce` and neither can
  break a working request (F2-182): `dpiaApi.create` is dark and `RegulatoryChangeRequest` declares
  neither field.
  ⚠⚠ **A FOURTH WIRE-CLOSING MECHANISM, AND THE STRONGEST OF THE FOUR: THE TYPE THE ENDPOINT
  BINDS.** This gate subtracts `BaseEntity`'s nine (F2-218) and per-field `READ_ONLY` (F2-219). Neither
  knows about a create whose CONTROLLER binds a purpose-built record and hands the service an entity it
  built itself: `SustainabilityAnnexController` binds `ReportingYearCreateRequest` — whose own javadoc
  says *"a year can never be born APPROVED/PUBLISHED"* — and calls `service.createYear(req.toEntity())`,
  so the service's parameter is a `ReportingYear` and a service-layer scan can only conclude the create
  accepts every column. **A field the bound type does not declare can never arrive**, which makes this
  stronger than an annotation, not weaker. F2-215's headline for the fifth time on this class: **a reader
  who finds two subtractions present does not ask what the third is.** Verified **mechanically** —
  reflection over the record's components plus the controller actually binding it — because an exclusion
  nobody can audit is an assumption wearing a citation.
  ⚠⚠ **AND A THIRD VERDICT BUCKET, BECAUSE TWO COULD NOT SAY WHAT NEEDED SAYING.** Filing
  `PrivacyService`'s `DataBreach#status` / `DataSubjectRequest#status` under `BY_DESIGN` would **assert
  they are correct**, and they are live holes deliberately left open (no CHECK, no declared machine, and
  `DsrRequestLifecycle` declares only a TERMINAL set so `requireInitial` would refuse the NEW/OPEN/PENDING
  creates the register already holds). 🔑 **F2-197 one level up: an invented figure invites a
  question, an invented all-clear closes one** — and F2-224's rule that a verdict vocabulary must not be
  limited to the verdicts an earlier slice happened to reach. `FILED_OWNER_DECISION` entries must contain
  the words OWNER DECISION, asserted.
  ⚠ **Watch what leaves a gate's output SILENTLY when you narrow it.**
  `DataSubjectRequest#dueDate` — the **PDPL Article 4 statutory deadline**, and the sharper half of
  F2-231's filing — is no longer reported at all, because `startDsr()` only supplies it as a fallback,
  which under (d) is correctly not a stamp. **The filing stands on F2-202 (only the server may issue a
  statutory deadline) and does not depend on this instrument**, so it is recorded inside the verdict
  rather than left to vanish with the detector change.
  ⚠ **A keying ambiguity is harmless until two things key on it differently.** The new self-check
  fired on `VendorAssessment#answeredCount` and two siblings: that entity is declared in **both**
  `module/scm/entity` and `module/tprm/entity` (F2-222's filed hazard — 15 such names), and
  `readOnlyOnTheWire` merges duplicates under one filename key while my field-list helper took only
  `findFirst()`. Take the UNION.
  ⚠ **The heredoc landmine fired again**, this time on a `py - <<'PY'` carrying prose with
  apostrophes: bash reported *"unexpected EOF while looking for matching `''`"*. **Write scan and edit
  scripts with the Write tool** — the rule covers any inline heredoc, not just ones with regex escapes.
  ⚙ **Filed:** the **4 BCM services** with per-site evidence in `target/server-stamped-fields.txt` and
  deliberately **no verdicts** (F2-188) — all four controllers bind the ENTITY (checked, not assumed),
  and `BcmRemediationService#createCapa` is the sharpest: `if (status == null) "OPEN"` followed by a
  **membership** check, so a BCM CAPA can be created `CLOSED` and skip `transitionCapa`'s *"Cannot CLOSE
  without evidence"* requirement (F2-217's `audit_lc` shape). Also `ReportingYear#status`, stamped through
  a **scalar parameter** and therefore invisible to this gate either way — F2-220's filed
  transition-by-parameter blind spot, still unmeasured as a population — and the remaining **45**
  `MassAssignmentRatchetTest` NEEDS-VERDICT rows.

- ⚠⚠ **A GATE'S HARD ZERO IS ONLY AS WIDE AS ITS PREDICATE, AND `isGuardedRoute` SAW 280 OF 409
  SERVER-STAMPED FIELD PAIRS** (F2-231, measured 2026-08-17). `ServerStampedFieldContractTest`'s
  `BASELINE_CONTRADICTED_SERVICES = 0` was green over the other **32%**. Two clauses were narrower than
  their intent and **both fired on `PrivacyService`**, hiding all five of its guarded routes at once:
  `if (isWritePath(name)) return false;` — and `isWritePath` matches `record`, so
  **`recordSdaiaSubmission` never reached line two** although it refuses on the terminal ACKNOWLEDGED
  state and stamps `sdaiaSubmittedAt`, the discharge of the PDPL Article 31 clock — and `refuses` named
  two exception families **inline**, while `containBreach`/`closeBreach`/`startDsr`/`fulfilDsr`/
  `rejectDsr` all throw `IllegalArgumentException` and `DpiaService#approve` refuses through a shared
  `require(d, "IN_REVIEW")` helper. That second one is F2-182's rule ("a guard sits in at least four
  places a setter-site regex cannot see", one of them a shared `validate()`) applied to **refusal**
  detection rather than to setter detection. 🔑 **The name test was never load-bearing**: a create
  cannot refuse on a state it has not loaded, so *loads a row AND refuses on that row's own state* is
  the whole discriminator, and the name clause only ever removed transitions. Widened as a provable
  **UNION** — replacing the predicate LOST 96 pairs, so 280 + 129 = 409 (F2-225: verify a widening by
  what it ADDS, in both directions). **Before trusting a gate's zero, ask what its predicate resolves
  and what it therefore cannot see** — F2-198's shape, eleventh instance, and the narrowing here is
  neither a filename nor an allow-list but **a name test inside the predicate itself**.
  ⚠⚠ **THE LIVE ONE: A POLICY WAIVER GRANTED BY THE ROUTE THAT FILES IT.**
  `PolicyManagementService#createException` bound the entity and used **defaults, not guards** —
  `if (body.getStatus() == null) body.setStatus("REQUESTED")` discards nothing (F2-218's headline).
  Probed live as `auditor`, who holds `COMPLIANCE:WRITE`, **the same permission `/transition` carries**,
  so no escalation: the state machine was the only control and the create sat beside it without one.
  `POST /api/policy/exceptions` answered **200** storing `status=APPROVED`, an invented approver AND
  reviewer, backdated `reviewed_at`/`rejected_at`/`requested_at` and an invented `rejection_reason`, on
  a **CRITICAL** exception — a formal acceptance of residual risk with compensating controls that never
  passed review. **One CREATE audit row, no TRANSITION row**, so the trail an auditor reads had no
  record of the approval, and `POLICY_EXCEPTION_APPROVED` — which the class's own comment calls the
  legally significant trigger — never fired. 🔑 **`approvedAt` was already refused, because
  `PolicyException` annotates that ONE field `@JsonProperty(READ_ONLY)` and not its five siblings** —
  F2-215's two-of-three shape at **one of six**, in a service F2-219 had put in the gate's `CLOSED` set
  **for the attestation register**. 🔑 **A service declared closed for one register it owns is not
  closed for another.** Fixed with one rule: a create **files a request**, so `status` goes through
  `ServerOwnedState.requireInitial` (sharing the constant the machine's root now reads), `requestedAt`
  is the server's clock unconditionally, and every column the review/approval/rejection states produce
  is cleared. `require` not `coerce` (F2-182): `ck_policy_exc_status` already refuses non-vocabulary
  values and `PolicyManagementPage` sends `status: 'REQUESTED'` with no input for any stamp.
  ⚠ **REFUTED — an IDENTITY read is not a STATE read.** `NdiImprovementPlanService#reorder` was
  classified a guarded transition of `sequenceOrder` for doing `byId.put(r.getId(), r)` and
  `if (r == null) throw`: an identity read and a **membership** refusal. It then renumbers from a
  counter **without ever reading the previous value**, so F2-220's discriminator — *the value accepted
  depends on the value already there* — is not satisfied, and `sequence_order` is display order (no
  CHECK, 1–4 and 10–40 over 15 rows, dev = prod) which `addItem` openly defaults. Narrowed with
  `IDENTITY_ACCESSORS`, asserted **both** ways: add one real state read and the same shape counts again.
  ⚠⚠ **MY OWN FIX FELL INTO THE GATE'S OTHER BLIND SPOT, AND THE CLOSURE IT PRODUCED WAS FAKE.**
  Extracting `markReviewed()` — the right design, one writer of the `(status, review-clock)` pair — took
  the setter out of `update()`'s body, and `TcfdItemService#update` left the population **looking
  closed**. 🔑 **To a setter-site scan, a refactor that RELOCATES a write is indistinguishable from a
  fix that REMOVED it, and the population shrinks either way — the quiet direction.**
  `writesFromRequest` now follows a one-hop private helper, keyed by the helper's declared parameter
  types and only when the call passes something that reads the request.
  ⚠⚠ **THE MIRROR OF THAT WIDENING WAS BUILT, MEASURED AND REJECTED, WHICH IS THE REUSABLE JUDGEMENT.**
  Following helper **stamps** moved the unexplained population **11 → 15**; two narrowings brought it
  back to 13 (a route may not be its own stamping route; **a helper the write path itself calls is not
  a control it bypasses** — `applyRules` and `maybeAutoCapa` are invariants every route shares); and at
  13 one net-new finding was **still a demonstrated false positive** — `ChangeManagementService#addLink`
  reported as accepting `ChangeLink#changeRequestId`/`entityId`/`entityType`/`linkType`/`notes` *"stamped
  by transition"*, the link's own data fields on the route whose purpose is to supply them. 🔑 **F2-214
  settles it: a gate that fails on correct code is worse than no gate**, and raising a decrease-only
  ceiling to accommodate noise is house rule 3's own warning. So the write half ships and the stamp half
  is an **asserted, measured blind spot** carrying its numbers, a demonstrating corpus and one unread
  lead (`CommitteeResolutionService#create` :: `voteFor`/`voteAgainst`/`voteAbstain`). Its consequence is
  the sharper half: a service that extracts its stamping into a helper drops out of the population
  **entirely** — not one field — because `measure()` skips a source whose stamped map is empty.
  ⚠⚠ **A PER-SERVICE VERDICT CANNOT EXPRESS A PER-FIELD FINDING.** `TcfdItemService` is closed for
  everything except one genuinely-by-design field, and the service-keyed `BY_DESIGN` map forced a false
  choice: drop the whole service out of the hard zero, or refuse a correct design. Re-keyed to
  `Service::Type#field` — **F2-185b's headline one level up, the granularity of the verdict has to match
  the granularity of the finding** — which also stops a service already carrying an exemption from
  hiding a NEW contradiction on a different field of the same type.
  ⚠ **BY DESIGN, mechanically rather than by taste, and the defect was hiding inside it.** `TcfdItem`
  declares `= "UNDER_REVIEW"` as a **field initialiser**, so an omitted key deserialises to that value
  and a null-guarded whitelist cannot tell absence from a real UNDER_REVIEW — which is why both ESG tabs
  echo the status back, in a comment saying exactly that, and why refusing the field would silently
  downgrade every reviewer's classification. But `update` performed the same classification change
  `transition` performs while leaving `last_reviewed_at` stale and writing a generic *"updated"* audit
  row (F2-188/F2-191: a duplicated transition agrees on the row and differs on the parts nothing
  asserts). Both routes now go through one `markReviewed()`: `transition` unconditionally (an explicit
  re-mark IS a review), `update` only when the value differs. 🔑 **An echo is not a review; a change
  is.** Fixed identically in `CmaDisclosureService`, the service TCFD's own javadoc says it mirrors.
  ⚠ **And the create's other field had its correct twin in the sibling file all along**: all three
  guarded routes stamp `lastReviewedAt` from the server's clock, so the create accepting it let a
  brand-new item claim the date an IFRS S2 reader takes as *when this was last looked at* (16 of 85
  rows, dev = prod) — while `CmaDisclosureService#create` has scrubbed exactly that under an
  SEC-ESG-003 comment since it was written. Fourth instance in this lineage of the correct artefact
  sitting beside the wrong one.
  ⚠ **A finding found by READING must not be reported as a gate catch.** `PrivacyService#createBreach`/
  `#createDsr` left `sdaiaSubmittedAt`, `sdaiaSubmissionRef`, `pdpcNotifiedAt` and `completedAt`
  caller-writable — the exact fields F2-209 and F2-213 wrote comments to protect on the **updates two
  lines below** — and the pre-fix control established that `PrivacyService` does not appear in the gate's
  output either way. All four are 0 non-null in dev and prod and none is client-declarable, so clearing
  them cannot break a working request. 🔑 **Attributing it to the widened gate would have been a better
  story and a false one.**
  ⚙ **The mutation harness predicted a gap and then proved it.** 14 killed / 3 survived / 0
  anchor-miss: one **equivalent** (a compile-time-constant `case` label IS the literal), one **invalid**
  (weakening an assertion always passes — F2-206), and one real — reverting the CMA half **survived**
  because only the TCFD half had behaviour tests. 🔑 **Fixing two services and testing one leaves the
  untested one free to drift back, which is how the mirrored pair diverged in the first place.** Closed
  with two CMA tests. ⚠ Two harness bugs of mine, both loud: `shell=True` on Windows is cmd.exe, where
  a bash-style `VAR='x' mvn …` prefix is a syntax error and the run prints **nothing** (the baseline
  check read that as red), and `| grep` has no cmd.exe equivalent — filter in Python.
  ⚠⚠ **THE HEREDOC LANDMINE FIRED THREE TIMES IN ONE SLICE, INCLUDING INTO A JAVA FILE.** `<<'PYEOF'`
  and `<<'JAVA'` — **quoted** delimiters — still collapsed `\\s` → `\s`, `\\b` → `\b` and `\\n` → a
  literal newline inside the payload. Two were loud (a javac error, a Python `SyntaxError`); the
  dangerous mode is the silent one this file already records, where a regex quietly stops matching.
  **The rule extends past shell scripts to any file content with escapes: use the Write/Edit tools.**
  ⚠⚠ **CORRECTION (F2-232, same day): BOTH net-new findings are false positives — 2 of 2, not 1 of 2.**
  The block above named *"one unread lead (`CommitteeResolutionService#create` :: `voteFor`/`voteAgainst`/
  `voteAbstain`)"*. Read, it is **BY DESIGN with provenance, and F2-40 built the mechanism**: the
  secretariat form posts the tally straight from the minutes for a committee that votes in the room, and
  the row records WHICH KIND of tally it is — `ASSERTED` with the recorder from `Authentication`, never
  the body (the DF-3 rule), versus `COMPUTED` for a resolution created empty and decided through
  `recordVote`. Measured: 22 live rows dev / 20 prod, `ASSERTED_LEGACY` **20/20** (F2-40's honest label
  for rows predating the mechanism) and **0** `ASSERTED`-with-recorder, so the path is latent and
  correct rather than bypassed. 🔑 **A population produced by a detector already judged unsafe
  refuted at 2 of 2, against this programme's usual ~2-in-10 rate — that is the argument FOR the
  rejection, not a footnote to it. Do not re-enable the stamp half expecting a defect behind either
  name.**
  ⚙ **Filed:** the **11-service work list** with per-site evidence in
  `target/server-stamped-fields.txt` and deliberately **no verdicts** (F2-188); the helper-stamp blind
  spot; **`privacy.data_subject_requests.due_date`, where the PDPL Article 4 statutory deadline is
  client-declarable and nothing refuses it** (only the server may issue a statutory deadline — F2-202 —
  but `DsrRequestLifecycle` declares only a terminal set, so `requireInitial` has no initial state to
  enforce and the fix needs a product decision about back-dated imports first); `status` on both privacy
  registers (**no CHECK, no declared machine**, so there is nothing for a create to be held to —
  F2-221's shape); `sust.cma_disclosure.clause` holding two disjoint notation schemes plus the literal
  `'None'` on 10 rows; `consumer.complaint.complaint_no` never restarting its sequence per year; and the
  remaining **45** `MassAssignmentRatchetTest` NEEDS-VERDICT rows.

- ⚠⚠ **GRADING IS ONLY ONE WAY TO BE ORDERED, AND A POPULATION KEYED ON A COLUMN'S NAME
  MEASURES THE NAMES ITS AUTHOR LISTED** (F2-230, measured 2026-08-17). Executing F2-229's filed
  six-entry graded-String work list, the population was re-derived first (F2-221) — and re-derived
  from the values the columns actually **store**, because a graded-name locator is the instrument class
  F2-217 proved measures its own vocabulary. **483 columns are sorted by some repository query (853
  ORDER BY terms across 429 repositories), 118 are String-typed, 57 hold a closed vocabulary, 6 of
  those are ORDERED and 51 are CATEGORICAL.** Four other shapes were checked and are **absent**:
  `MAX()`/`MIN()` over a graded column, a lexical `>`/`<`/`BETWEEN` against a graded token,
  `Sort.by(<graded>)` at a call site, and Java `compareTo` on a graded getter (2 hits, both numeric).
  🔑 **A widening that finds nothing is a result, not a dead end** (F2-216) — it says the
  family really does live in `ORDER BY`. Adjudication: **4 CLOSED, 2 REFUTED in opposite directions**,
  the same ~2-in-10 rate F2-188/F2-192/F2-204/F2-211/F2-228 each measured in their predecessor's claims.
  ⚠⚠ **THE SHARPEST WAS A PAGED REGISTER THAT PUT ITS MOST EXPOSED VENDOR LAST.**
  `ThirdPartyCyberAssessmentService.recompute` derives a **five**-rung ladder
  (`LOW < MODERATE < ELEVATED < HIGH < CRITICAL`) that existed **only as an inline ternary chain**, and
  the register's ordering was the derived `findByOrganisationIdOrderByResidualRiskTierDescIdDesc`.
  Descending alphabetically those five read **MODERATE > LOW > HIGH > ELEVATED > CRITICAL**, so
  CRITICAL sorted **last** and LOW sorted **second**. Measured byte-identical in dev and prod: 16 rows
  (LOW 7 / MODERATE 7 / ELEVATED 2), and DESC returned six MODERATE rows before either ELEVATED one —
  on a **paged** endpoint, so on a larger tenant the most exposed third parties are not merely
  mis-ordered, they are on a page nobody opens. ⚠ There is an index on
  `(organisation_id, residual_risk_tier)` from V53: **a lexical sort backed by a covering index reads
  like a considered decision.** 🔑 **The frontend had the ladder right all along** —
  `TpcResidual` declares all five rungs in order with a complete tone map; the half that was wrong is
  the half that decides what an operator sees first (F2-205: when two copies disagree, ask which is
  the server).
  ⚠⚠ **THE GOVERNANCE ONE: AN ALPHABETICAL SORT ANSWERING A CMA ARTICLE 54 QUESTION.**
  `CommitteeMemberSkillRepository#findByDimension` ended `ORDER BY s.skillLevel DESC` over
  `BASIC | INTERMEDIATE | EXPERT`, whose alphabetical descent reads INTERMEDIATE, EXPERT, BASIC — and
  its own javadoc says it exists to answer *"does this committee have a financial expert?"* (CMA
  Article 54, SAMA RGov 5.3.2). Probed live in dev and prod: six INTERMEDIATE members and the **single
  EXPERT last of seven**. That javadoc also described a filter that does not exist (it claimed "at a
  given level or above"; the method takes no level parameter) — F2-184's shape. **The ordering is
  fixed and the capability is NOT invented to make a comment true**; the comment is corrected instead.
  ⚠ Blast radius stated honestly: no FE client calls that route, so today the wrong order reaches
  API callers rather than the skills screen — **a fact about today, not a property of the control**
  (F2-182).
  ⚠⚠ **THE MEMBERS THE NAME-KEYED LOCATOR COULD NOT SEE ARE ORDERED BY TIME AND BY VERSION.**
  `consumer.complaint_aggregate_report.report_period` holds `Q4-2025`, and a lexical sort of
  `Q<n>-<year>` orders by **QUARTER before YEAR** — `'Q4-2025' > 'Q1-2026'` — so the most recent
  SAMA quarterly complaints return is not first. `tprm.questionnaire_templates.version` would put
  `'10.0'` before `'9.0'`. Both latent (1 row each), both **FILED**: `report_period` is `@NotBlank`
  free text with no format contract, so a fix has to settle a format first (F2-183's rule).
  🔑 **When you ask whether a lexical sort is safe, the question is whether the vocabulary is
  ORDERED — not whether it sounds like a severity.**
  ⚠⚠ **A HARD ZERO OVER THE 57 WOULD FAIL ON CORRECT CODE**, the one failure a gate must never
  have (F2-214): `sust.esg_metric.category` is [ENVIRONMENTAL, SOCIAL, GOVERNANCE] and
  `admin.committee_form_templates.form_type` is [AGENDA, ATTENDANCE, MOTION, …], where an alphabetical
  sort is a deterministic grouping that claims nothing. So `OrderedVocabularySortContractTest` is two
  legs: an **invariant** (no declared ladder is sorted lexically unless the sort is *provably* safe) and
  an **exactly-asserted census with no verdicts** (F2-188) over every CHECK-constrained column still
  sorted lexically — the census exists because the invariant's population is *columns somebody
  declared a ladder for*, a narrowing that would let the next ordered column escape by never being
  declared.
  ⚠ **"Provably safe" is MEASURED, not exempted.** `ai_governance.ml_model.risk_tier` is sorted
  lexically and is **correct**, because both queries narrow to `IN ('CRITICAL','HIGH')` whose
  alphabetical order *is* their severity order. F2-229 called that "accidentally correct" in prose; the
  gate now parses the narrowing out of the `@Query` and compares the subset against the ladder. A gate
  that flagged it would fail on working code; one that exempted it by name would tolerate the next one
  that is not.
  ⚠⚠ **INTRODUCING A WRAPPER TYPE AROUND A VALUE AN INSTRUMENT DISCOVERS BY EXACT TYPE SILENTLY
  REMOVES IT FROM THAT INSTRUMENT'S POPULATION.** `ClosedVocabularyPinTest` narrows on the source string
  `ClosedVocabulary.of(` and reflects on `ClosedVocabulary.class.equals(f.getType())`, so all four new
  `OrderedVocabulary` ladders would have been invisible to it **twice over** — wrong source string,
  wrong field type. Fixed in the same commit, with the four constraint names added to `MUST_BE_FOUND`
  so the widening cannot quietly un-widen. **Tenth instance of F2-198's shape** after a filename, a
  three-entry allow-list, a module name, a controller-bearing table, an `extends` clause, a field-name
  list inside a fix, a name list in a census and a claim shape in a gate.
  ⚠⚠ **MY OWN ANTI-VACUITY FLOOR FOUND A REAL HOLE BY BEING WRONG.** I set the CASE-ladder floor
  to 8 and the parse returned 7 — because F2-229 wrote a `CASE` ladder for
  `scm.fourth_party_exposures.downstream_criticality` into the register's `@Query` and **declared no
  ladder for it to be checked against**. 🔑 **A ranking written only in SQL is still a second
  copy of a vocabulary's order; it just has nothing to disagree with.** Promoted to an
  `OrderedVocabulary` and registered.
  ⚠ **One of the gate's own assertions was wrong and the gate was right.** I asserted that widening
  `IN ('CRITICAL','HIGH')` to include MEDIUM breaks the safety proof; it does not — alphabetically
  CRITICAL, HIGH, MEDIUM has ranks 3, 2, 1, which is monotone, so that subset is genuinely safe. **LOW**
  is the token that breaks it. F2-206's rule (a surviving mutant is a hole only if the mutated code is
  actually wrong) applied to an assertion rather than a mutation.
  ⚙ **`common/validation/OrderedVocabulary`** is the primitive: a `ClosedVocabulary` plus an order,
  with `rank` / `outranks` / `atLeast` / `bottom` / `top` and `isAlphabetical()` — that last one exists
  so an accidentally-correct sort is *expressible* rather than argued about in a comment.
  `ConcentrationTier` (F2-229) delegates to it rather than being a second mechanism (F2-190). A `@Query`
  `CASE` ladder is necessarily a **second copy** of the order (JPA takes a compile-time constant), and
  what stops it drifting is the gate parsing the annotation by reflection.
  ⚙ **V378** adds `tprm.third_party_cyber_assessments`'s first three CHECK constraints, scoped to
  the writers the same commit closed (F2-183) — including the create, which **forced** its initial
  state instead of defaulting it (F2-220's shape, **tenth instance**: `if (status == null) status =
  "DRAFT"` is a default, not a guard, so a caller could create an assessment already APPROVED and skip
  `submit()`/`approve()`, the only writers of `assessed_at`, `approved_at` and `approver_name`).
  Measured clean in both databases first, applied twice to dev.
  ⚠⚠ **THE PRE-FIX CONTROL CAUGHT TWO HARNESS BUGS OF MINE AND BOTH FAILED LOUDLY.** Reading the
  "after" side as `git show HEAD:` compares master with **itself** while the fix is still uncommitted,
  so all 13 detectors reported STILL THERE — **F2-229 recorded that exact misuse and I repeated it**.
  Then five detectors reported STILL THERE because **my own comments quote the shapes they retired**
  (F2-193's rule), so the harness strips comments — except the one detector whose *claim is about a
  comment*, which uses `unquoted()` instead. 🔑 **A detector whose claim is about prose cannot
  share a comment-stripping harness.** Final: **13 of 13 fire on master, clean on the fixed tree.**
  ⚠ **Two probe bugs, both mine.** The route was **guessed** — it is
  `/api/tprm/cyber-assessments`, with **no `v1` segment** (recurring since F2-198: resolve from the
  controller, never guess) — and the constraint checks were scoped
  `WHERE id = (SELECT MIN(id) … tagged)` with no tagged row planted, so `WHERE id = NULL` matched
  **zero rows** and every illegal UPDATE "succeeded". 🔑 **An operation that touches nothing
  looks exactly like an operation that was allowed** — F2-183's rule about no-op migration branches,
  one level out. The section now plants its own victim (F2-206) and asserts the **row count**, proving
  the UPDATE reaches the row with a legal value first.
  ⚙ **Filed, and the sharpest is uniformly flattering:** **10 of 16 rows' stored
  `residual_risk_tier` is LOWER than the service's own derivation from the `risk_score` stored beside
  it, and 0 are higher** — identical in dev and prod. `TPC-GOV-001` scores **90.00** of 100 (higher =
  worse) and is recorded **LOW**; `TPC-GOV-003` scores 88 and is also LOW. The register renders both
  figures on the same row, so it **contradicts itself in one line** (F2-203's shape). `recompute` runs
  only on create and `recordPosture`, so these seed-written rows have never been through the
  derivation — and the first operator to record posture on TPC-GOV-001 will watch it jump LOW →
  CRITICAL. **Found in the browser after every gate was green — seventh consecutive slice.**
  Re-deriving rewrites 10 live rows in two real databases, so it is an **owner decision**
  (F2-183/F2-196), not a side effect of an ordering fix. Also filed: the two ordered-but-not-graded
  members; the **51 categorical** vocabularies (no defect unless a reader finds one of them ordered);
  and F2-221's **201** state-ish columns with no CHECK, which no leg of this gate can see.

- ⚠⚠ **A DERIVED VALUE WHOSE OUTPUT RANGE IS NARROWER THAN ITS COLUMN'S VOCABULARY DESTROYS THE
  VALUES IT CANNOT EXPRESS** (F2-229, measured 2026-08-17). Executing F2-228's filed defect, the
  population was re-derived first (F2-221). `FourthPartyExposureService.recomputeConcentration` runs
  across the **whole tenant** on every create and every update, and it **ASSIGNED** the derived tier
  where three artefacts said elevate: the service javadoc (*"auto-elevated to HIGH"*), the
  repository javadoc (*"the service uses this to elevate `concentration_risk`"*) and the auto-CAPA
  hook, written as a **rising edge** — a shape that only makes sense if the value is monotone.
  Measured, identical in dev and prod: all 13 live rows carry **13 DISTINCT** fourth-party names, so
  the multiplicity rule matches nothing and computes LOW for every row, and any write **lowered 9 of
  13** — all three HIGH rows and the sole CRITICAL one (an underlying hyperscaler region). Same
  family as F2-195's `|| N`: the correct rule sat in prose beside the place it was not applied.
  🔑 **Multiplicity can justify LOW, MEDIUM or HIGH and no row count yields CRITICAL, so what the
  derivation overwrote at the top of the ladder could never be restored by it.** Fixed with one
  declared ladder (`ConcentrationTier`) read by the recompute, the CAPA edge and the ordering:
  `multiplicityFloor` is a **floor**, `outranks` decides whether to raise, nothing lowers.
  ⚠ **A RETIRED edge earns nobody a floor, and that matters MORE under elevation than under
  assignment** — an inflated floor is now permanent, so excluding retired dependencies is part of
  making the monotone rule sound rather than decoration. Latent today (no name appears twice in
  either database), so it is asserted by test rather than observed in data.
  ⚠⚠ **SECOND DEFECT, SAME REGISTER: A LEXICAL SORT PRESENTED AS A SEVERITY RANKING.**
  `findByOrganisationIdOrderByConcentrationRiskDescDownstreamCriticalityDesc` is a derived method on
  two NVARCHAR columns, and descending **alphabetically** LOW|MEDIUM|HIGH|CRITICAL reads MEDIUM,
  LOW, HIGH, CRITICAL — so the **CRITICAL/CRITICAL row came back LAST of 13**, the three
  HIGH/CRITICAL rows 10th–12th, on a page the frontend requests at `size=200` and renders in server
  order. 🔑 **A lexical sort is a ranking only where the vocabulary happens to be in alphabetical
  order, and severity ladders never are.** Replaced by a `CASE` rank checked token-for-token against
  `ConcentrationTier.ladder()` by **reflection over the `@Query` annotation** — a `@Query` string is
  necessarily a second copy of the order, and that is what stops it drifting.
  ⚠⚠ **THIRD DEFECT: THE TOP TIER HAD NO DECLARATION ANYWHERE, AND SIX READER QUESTIONS EACH
  DROPPED IT.** The entity's field javadoc said `LOW | MEDIUM | HIGH`, the frontend's
  `FpConcentration` said the same, the table had **no CHECK constraint of any kind** — and the
  column held CRITICAL on one row in both databases. So `FP_CONC_TONE[tier]` was `undefined`, the
  concentration tab filtered `HIGH || MEDIUM` and **excluded the most concentrated row in the
  register**, its chip under-counted (8 where 9 qualify) by summing *two separately spelled*
  inclusion lists rather than the predicate the tab filters with (F2-197), and
  `TprmConsolidatedOverview` plus the dashboard widget asked `=== 'HIGH'`. F2-196's rule on a tier:
  **"elevated" must be a threshold over a declared ladder, never a second inclusion list** —
  **7 equality comparisons across 3 files**, all now asking `isConcentrationAtLeast`. ⚠ The idiom
  was already one component over: `TprmConsolidatedOverview` ranks the *adjacent* `residualRiskTier`
  with its own `tierRank()` and the concentration column beside it never used it.
  ⚠⚠ **MY OWN DETECTOR UNDER-REPORTED AND THE ANTI-VACUITY LEG IS THE ONLY REASON I KNOW.**
  `MigrationVocabularies` keys its vocabularies **`schema.table.column`**; my first draft built the
  entity-side key as `table.column` from `@Table(name)`, ignoring `@Table(schema)`. Every lookup
  missed, every column read as having **no CHECK**, and the hard zero I had written **PASSED over a
  population of 15** — while the throwaway census using the same key printed *"total columns
  affected: 0"* and I read that as a measurement. Caught only by the leg asserting V377's own four
  constraints are findable. 🔑 **A detector that cannot match reads exactly like a clean tree, and
  it UNDER-reports — the quiet direction.** Nothing about the wrong answer's shape looked wrong.
  ⚠⚠ **AND A HARD ZERO OVER THOSE 15 WOULD FAIL ON CORRECT CODE, WHICH IS THE ONE THING A GATE MUST
  NEVER DO** (F2-214). A lexical sort of a closed vocabulary is a false ranking only where the
  vocabulary is **ORDERED**; most of the 15 are **categorical** — `sust.esg_metric.category` is
  [ENVIRONMENTAL, SOCIAL, GOVERNANCE], `admin.committee_form_templates.form_type` is
  [AGENDA, ATTENDANCE, MOTION, …] — where an alphabetical sort is a deterministic grouping that
  claims nothing. So the leg is an **exactly-asserted census with no verdicts** (F2-188), plus a
  6-entry graded-String work list covering the two columns with no CHECK at all.
  ⚠ **Two of the six were read, and they fall in OPPOSITE directions** — which is the whole argument
  for a work list carrying no reasons. `MlModelRepository` is **accidentally correct**:
  `WHERE m.riskTier IN ('CRITICAL','HIGH')` narrows the vocabulary to two tokens whose alphabetical
  order *is* their severity order. `CommitteeMemberSkillRepository#findByDimension` sorts
  `skillLevel DESC` over [BASIC, INTERMEDIATE, EXPERT] to answer *"does this committee have a
  financial expert?"* (CMA Article 54) — ranking INTERMEDIATE above EXPERT.
  ⚠ **The load-bearing narrowing is the field TYPE, and it is pinned by measurement.** A
  graded-name locator returns 10 columns after the fix (12 before) and **4 are numeric**
  (`automation_rules.priority`, `climate_risk_opportunity.risk_score`,
  `communication_contacts.escalation_level`, `ot_asset.purdue_level`), where a lexical sort *is* a
  correct numeric sort — 40% of the raw population. Held exactly, so deleting the String narrowing
  changes an answer rather than an opinion.
  ⚙ **V377** adds the table's **first four CHECK constraints**, scoped to the writers the same commit
  closes (F2-183): `concentration_risk`/`status` are server-owned, and
  `dependency_type`/`downstream_criticality` go through `ClosedVocabulary.require()`. Measured in
  **both** databases first — 13 rows each, 0 violations and 0 non-canonical casings — so no
  `WITH NOCHECK` and **no casing UPDATE**, checked rather than assumed because every writer either
  normalises or writes a Java literal. `require` not `coerce`, per F2-182: the hand-rolled
  `Set.contains` already refused, so nothing that succeeds today starts failing.
  ⚠ **The pre-fix half was deliberately NOT re-run.** F2-228 already observed the flattening live —
  and damaged this exact register doing it — while both defects are reproducible **read-only** (the
  arithmetic, and the ordering). 🔑 **Re-running a destructive experiment to re-confirm a recorded
  result is not verification, it is damage.** The post-fix probe plants its own rows and its
  load-bearing assertion is that the 13 seeded rows are byte-identical after five writes, which is
  exactly the property the fix establishes — so a wrong fix fails the probe.
  ⚠ **Two harness results were mine, not the gate's.** M10 (the FIRST `CASE` ladder flipped to
  `ASC`) **survived** a bare `contains("END DESC")` because the *second, untouched* ladder still
  matched — F2-194's substring-satisfied-by-something-else, so the leg asserts by **count** now. And
  M13's first form mutated the gate's own assertion to a weaker one, which always survives (F2-206);
  replaced by a **data** mutation adding a phantom member. 17/17 after, plus one recorded
  **EQUIVALENT** (the CAPA edge asking `equals` instead of `atLeast` — unreachable difference,
  because the branch only runs when `outranks(floor, stored)` holds and the floor is never CRITICAL).
  ⚠ **The CRLF anchor trap fired for the THIRD time in this lineage** (F2-212, F2-219): the frontend
  is CRLF, the harness's anchors are LF, and two multi-line anchors reported ANCHOR-MISS. Normalise
  the anchor to the target file's own line endings, and make an anchor miss exit non-zero as a
  distinct outcome.
  ⚠⚠ **FOUND IN THE BROWSER AFTER EVERY GATE WAS GREEN — SIXTH CONSECUTIVE SLICE — and it was the
  LABEL.** `f4pKpiHighConc` read *"High concentration"*, which was **exact** while the predicate
  counted exactly the HIGH rows and became imprecise the moment it counted HIGH *and above*.
  🔑 **F2-212's rule from the inside: the slice that fixes a predicate is the slice that must fix the
  label that accurately described the old one** — and check it **per language** (F2-208 found this
  drift in the Arabic only, and Arabic is primary, house rule 7). Both now read as thresholds,
  matching the tab label's own established `≥` convention.
  ⚙ **Filed:** the 6-entry graded-String work list (`skill_level` is the sharpest);
  the 15-column closed-vocabulary census, where a defect requires the vocabulary to be ORDERED and
  that is a question for a reader; and F2-221's **201** state-ish columns with no CHECK, which no leg
  of this gate can see.

- ⚠⚠ **THE FATAL DOUBLE SAVE IS THE ONE WHOSE FIRST SAVE IS AN *INSERT*, AND THE READING
  CONFIRMED THREE SITES THE PROBE REFUTED** (F2-228, measured 2026-08-17). Executing F2-227's filed
  six-site work list, the population was re-derived first (F2-221) and **the detector was wrong in
  BOTH directions, which is exactly what let a green build pin the wrong set.** It **over**-matched:
  the query receiver was unconstrained, so `all.stream()`, `gates.count()` and `x.findFirst()` —
  Java Stream API calls on an in-memory `List` — read as database queries, which is the whole
  reason `WorkflowRequestService#advanceRouting` was on the list (it touches no repository at all).
  ⚠ Keying the receiver on the field NAME is also wrong: `PolicyManagementService` calls its
  repository `docs`, so a `*repo*` rule cannot see it (F2-197) — resolve it from
  `private final …Repository`. It **under**-matched: `findById(` is **not** a flusher
  (`EntityManager#find` raises a LoadEvent, not an AutoFlushEvent, and returns the managed instance
  straight from the persistence context), and the flush may be **TRANSITIVE**, one level deeper than
  the callee. 🔑 **The two errors cancelled** — the loose `find\w*` matched
  `assetRepo.findById(` and so put `recomputeRiskScore` in the population *for a reason that is not
  true*, while the real flush sat in `pickHighestOpenSeverity`. **Fixing either one alone would have
  been worse than fixing neither**: the over-match fix deletes a real site, the under-match fix keeps
  a phantom. F2-224's headline, one slice later, inside the same gate.
  ⚠⚠ **CORRECTION to the landmine above: *"Hibernate never re-reads"* is true of the value
  and does NOT mean two saves of a loaded row fail.** Probed 200 twice each on the ASM asset update
  (`PUT /api/v1/asm/assets/{externalId}` with exposure AND criticality changed so the early return is
  not taken) and the Open Banking TPP revocation (`POST /tpps/{id}/transition` to REVOKED, the branch
  that queries consents and then recounts the same row). A **loaded** row carries a real
  `row_version`, and both UPDATEs match. 🔑 **F2-227's site differed in exactly one way: its
  first save was of a `new` entity.** `@GeneratedValue(IDENTITY)` makes Hibernate execute that INSERT
  immediately to obtain the id, `row_version` is `insertable = false`, so the in-memory version is
  **null** afterwards and the follow-up `UPDATE … WHERE row_version = null` matches zero rows —
  which is precisely what Hibernate's own hint *"or unsaved-value mapping was incorrect"* has been
  saying all along. **For an INSERT the first save IS the flush**, so no intervening query is needed
  and F2-227's flush-between requirement does not apply to the shape that actually fires.
  ⚠⚠ **And the premise the reading never checked: only 172 of 432 entities extend
  `BaseEntity`, so for 260 of them this family is UNREPRESENTABLE.**
  `FourthPartyExposure` is one of them — no `@Version`, no `row_version` column — so its create
  was never at risk however many times it re-saved the row. F2-206's *"an invariant enforced by a
  superclass covers exactly its subclasses"*, inverted: **the defect ENABLED by a superclass reaches
  exactly its subclasses.** Check the entity before believing any instance of this shape.
  ⚠⚠ **The two live sites, both probed at 409 against PLANTED rows, and both are governance
  controls.** `CoiService#submitDeclaration` saved the declaration, inserted the disclosed items to
  get their `declarationId`, then re-saved the declaration to carry the auto-flagged outcome: a
  declaration disclosing **nothing** returns 201 (because `computeAutoOutcome` returns `PENDING`, the
  value already set, so nothing is dirty) and one disclosing **an actual conflict of interest** rolled
  back — 🔑 **a conflict-of-interest register that could record everything EXCEPT a
  conflict**, on a route guarded `isAuthenticated()`, so the population is every employee.
  `CcmService#recordRunInternal` saved the run, opened the Issue, then re-saved the run to carry the
  Issue id: the **third consecutive failure of a CRITICAL control test** rolled the whole transaction
  back, so **neither the auto-opened Issue nor the failing run itself was recorded** — on both
  `POST /ccm/tests/{id}/run-now` and the 60-second `CcmSchedulerService` tick. F2-175's defect, in
  another module, on the escalation ladder. Both fixed F2-175's way: **do the other work FIRST, save
  ONCE** — the COI items are built in memory and the outcome decided before the single save, and
  `openConsecutiveFailureIssue` only ever read the run's `failureReason`, never its id.
  ⚠⚠ **The gate's QUESTION was wrong, so it was re-scoped rather than raised** (F2-204). A
  cross-method re-save of a **loaded** row is not a defect, so a hard zero over that population would
  be a gate failing on correct code — the one failure a gate must never have (F2-214). It is now an
  **exactly-asserted census** of 7 measured-harmless sites, each with a verdict naming its evidence,
  enforced both ways so a new member forces the next reader to ask the only question that matters; the
  **hard zero moved to the INSERT shape**, with both literal pre-fix shapes, both fixed forms,
  F2-227's own site and one must-not-count refutation as positive controls.
  ⚠ **My own verdict vocabulary then rejected a correct, verified verdict.** The first draft
  enumerated `REFUTED BY LIVE PROBE` and `REFUTED BY READING` — the two sources this slice happened
  to reach first — and refused `REFUTED BY THE ENTITY`, the strongest of the seven. **F2-224's own
  lesson recurring inside a gate written after quoting it**: the rule is that a verdict must NAME its
  evidence, not that it must be one of the sources already used.
  ⚠⚠ **A probe of `recomputeConcentration` ALTERED 9 REAL SEEDED ROWS, and that is its own
  finding.** The recompute is authoritative over the whole tenant and disagrees with the seed, so it
  reset 9 of 13 `scm.fourth_party_exposures.concentration_risk` values (MEDIUM/HIGH/CRITICAL) to
  `LOW`. Restored field-for-field from prod, which holds the identical seed and was never in scope
  (F2-206's precedent). 🔑 **Any create or update in that register silently rewrites other
  rows' SAMA concentration tier** — filed. **Before probing a method whose name starts `recompute`,
  ask what population it recomputes.**
  ⚙ **Filed:** the fourth-party concentration flattening; `openbanking.ob_tpp` has **zero** CHECK
  constraints (F2-221's shape); and the structural root carried from F2-227 — `@Generated(event =
  {INSERT, UPDATE})` on `rowVersion` would make the family unrepresentable at the cost of a post-write
  `SELECT` on all 172 `BaseEntity` subclasses, an owner trade-off.

- ⚠⚠ **A LANDMINE THAT CANNOT BE DIAGNOSED FROM A RESPONSE GETS REDISCOVERED INSTEAD OF FIXED**
  (F2-227, measured 2026-08-17). The ROWVERSION double-flush above has been recorded three times —
  F2-175 fixed one instance, F2-226 tripped over a live one by accident — and had **never once been
  identified from an error message**, because `OptimisticLockingFailureException` is a
  `DataAccessException` and so fell into `GlobalExceptionHandler#handleMissingDbObject`, which
  returned a bare *"An unexpected database error occurred."* **with no exception class even in dev
  with `grc.errors.verbose=true` ON** — it built its own `ProblemDetail` instead of delegating to
  `handleGeneral`, where the verbose diagnostics live. Its own comment said *"let the catch-all
  handle it with a 500"*. 🔑 **The handler intercepting this exception class was the one handler
  that discarded the diagnostic verbose mode exists to provide.** F2-180's rule inverted: a
  wrong-but-specific diagnostic at least sends the reader somewhere; a *generic* diagnostic from a
  *specific* handler sends them nowhere and looks deliberate. **A stale row is now a 409 naming the
  entity and its id** (`ObjectOptimisticLockingFailureException` carries both), and the fall-through
  delegates. **Before measuring a defect family, check that its failure is legible.**
  ⚠⚠ **THE LIVE DEFECT WAS THE DEFAULT PATH OF A FACILITATOR CONTROL.**
  `POST /api/v1/bcm/exercise-runs/{id}/custom-inject` saved the new inject and then called
  `dispatchInject`, which re-loads that row and saves it again in one transaction — and
  **`dispatchNow` DEFAULTS TO TRUE**, so the ordinary "throw an inject now" action on a live BCM
  exercise answered **HTTP 500 for every tenant**. Fixed by stamping the dispatch in memory before
  the single save; both entry points now share one `announceDispatched` rather than one calling the
  other (F2-188: a duplicated transition agrees on the row and differs on the audit trail, which is
  the part nothing asserts). `setActualDispatchedAt` has exactly one writer, asserted.
  ⚠⚠ **THE DISCRIMINATOR IS A QUERY, NOT A WRITE — AND GETTING IT WRONG INFLATES THE POPULATION
  18-FOLD IN THE FLATTERING DIRECTION.** A first derivation treated any intervening repository write
  as the flusher and reported **18 live sites**. The tell was `PolicyManagementService#update`: it
  has the shape and F2-222 probed it **13/13 working**. `AuditLogService.log` is `@Transactional`
  (joining the caller's transaction) and saves an **IDENTITY-generated** row, so Hibernate executes
  that one insert immediately to obtain the id **without flushing the caller's other pending
  changes**. 🔑 **`audit.log` and `emit` are not flushers; flush-before-QUERY is.** That is the whole
  difference between 18 live sites and one, and why the platform works while carrying the shape in
  **39** places — 18 with a query between the two saves, 21 without. ⚠ The mapping is what makes it
  fatal: `BaseEntity.rowVersion` is `@Version @Column(insertable = false, updatable = false)` on a
  SQL Server ROWVERSION the **database** generates and Hibernate **never re-reads**, so the moment a
  flush lands the in-memory version is stale. Hibernate's own *"or unsaved-value mapping was
  incorrect"* is the hint that the version was never populated.
  ⚠ **The decidable half is the CROSS-METHOD shape**: a method saves a row, then hands it to a
  sibling that re-loads and saves it. `DoubleSaveContractTest` bans that; population **8** — 1
  fixed, 1 refuted by reading (`ApprovalChainTemplateService#clearOtherDefaults` queries
  `…AndIdNot(`, so it provably saves *other* rows), **6 on a decrease-only work list and
  deliberately NOT given reasons** (F2-188). A hard zero over unread code is a gate failing on code
  the slice never examined (F2-214), and the same-method variant is undecidable by scan.
  ⚠⚠ **THE EXISTING TEST'S STUB WAS THE DEFECT, AND ITS REMOVAL IS THE PROOF.**
  `addCustomInject_with_dispatchNow_dispatches_immediately` stubbed
  `runInjectRepo.findByIdAndOrganisationId` under the comment *"After save, the dispatchInject step
  needs the inject to be findable"* — i.e. the test documented the re-load as a requirement. It
  became an `UnnecessaryStubbingException` the instant the fix landed. 🔑 **A stub that describes
  the defective mechanism reads as setup, not as a finding** — third instance of a test locking in a
  defect (F2-159, F2-175, this). Repurposed to `verify(times(1)).save` + `verify(never())
  .findByIdAndOrganisationId`, because CLAUDE.md is right that **no mock can reproduce the failure**
  — mocked repositories have no `row_version` — so the assertion has to be the structure the fix
  guarantees.
  ⚠⚠ **AND THE HEREDOC LANDMINE HAS A SIBLING THAT FIRED TWICE IN ONE SESSION: `py -c "…"` INSIDE
  A BASH COMMAND LETS BASH EXPAND EVERY BACKTICK AS COMMAND SUBSTITUTION BEFORE PYTHON SEES IT.**
  Prose about code is mostly backticks, so this eats the identifiers and leaves the sentence:
  `KEY: ** is a , so every one fell into`. It ate a whole memory section and, separately, F2-226's
  plan SHA (`(\`PENDING\`)` → `PENDING: command not found`). 🔑 **Both times the script printed its
  own success line, so the failure and the confirmation arrived together** — the same signature as
  the `\b`-in-a-heredoc trap two landmines up. The existing rule ("write scan scripts with a file
  write, never a heredoc") extends to **any** inline `py -c` carrying backticks; and the repair
  script should re-read what it wrote and assert the identifiers survived, because a mangled
  paragraph still reads like prose.
  ⚙ **Filed as the structural root:** annotating `rowVersion` `@Generated(event = {INSERT, UPDATE})`
  would make this whole family **unrepresentable** by having Hibernate re-read the database's value
  after every write — at the cost of a post-write `SELECT` on every write to all 174 `BaseEntity`
  subclasses. That is an owner trade-off, not a side effect of a bug fix (F2-213's rule about the 83
  entity-bound PUTs).

- ⚠⚠ **A WORK LIST AND ITS CLASSIFIER ARE TWO ARTEFACTS, AND THE SLICE THAT BUILT BOTH DID NOT
  MAKE THEM AGREE** (F2-226, measured 2026-08-17). Executing F2-225's filed field-initialiser work
  list, the population was re-derived first (F2-221) — and the filing had measured **one of
  F2-215's four filling mechanisms**. F2-225's scan matched only the field INITIALISER
  (`private LocalDateTime x = …now()`); across all four — initialiser, `@PrePersist`/`@PreUpdate`
  callback, `@CreationTimestamp`, and `insertable = false` where the database default fills the
  column — the denominator is **82 entities / 87 act-clock fields, not 68** (73 / 6 / 8).
  🔑 **The two mechanisms it missed are the ones a caller can NEVER reach**: a `@PrePersist` fires
  at flush time *after* every setter and an `insertable = false` column is never sent at all, so
  both are **stronger** evidence that the server owns WHEN than an initialiser — which F2-225
  itself found can be overridden. Permissively re-paired: **30 sites, 17 candidate / 11 assignment
  / 2 noise**. The widening surfaced no new confirmed attribution, which is a result and not a dead
  end (F2-216).
  ⚠⚠ **2 of the 4 filed sites were mis-bucketed, and F2-225's OWN CLASSIFIER said so.**
  `AuditEngagementService#create::leadAuditor` (and `::auditCommitteeChair`) sit beside
  `auditorFirm` — an IIA engagement is routinely led by an external firm's partner, who is not a
  platform principal at all (F2-224's `auditorExternalParty` lesson) — and a lead auditor is
  ASSIGNED to an engagement rather than being its recorder. `InvocationService#create::
  incidentCommander` and `::bcmCoordinator` are crisis ROLE assignments, refuted **decisively by
  the sibling column**: `Invocation` declares `declaredBy` separately, and that is the attribution
  F2-224 closed. 🔑 **F2-225's headline was *"the classification is as much the finding as the
  count"* and it then hand-filed a work list its own classifier contradicts — this programme's
  most-repeated shape (two artefacts, nobody maintaining the agreement, F2-177) committed inside
  the instrument built to answer it.** `theWorkListAgreesWithTheClassifier` now runs the classifier
  over the work list mechanically. ⚠ Note what it does NOT do: `participantUserId` classifies as an
  ASSIGNMENT and was still fixed, because the classifier reads a NAME and that site was adjudicated
  by reading the CODE. **A classifier triages a population; it never decides a verdict** — recorded
  in the gate rather than papered over by special-casing the word *participant*.
  ⚠⚠ **The live half: an approval register that recorded a raiser the caller invented, and let
  that caller name the only principal permitted to approve it.** `approvals.requests.requested_at`
  is a field initialiser, so the server owns WHEN with no `setRequestedAt` anywhere for the F2-223
  detector to pair, while `requested_by` was `@NotBlank` from the body — the **fifth** instance of
  a server *requiring* an attribution it never verifies. Probed as `viewer` (holds
  `WORKFLOW:WRITE`, **not** `WORKFLOW:APPROVE`): a POLICY-publication approval stored
  `requested_by = "Nobody Who Raised This"` beside a server clock, and omitting the field was a
  **400**. 🔑 **The rule was already written down IN THE CLIENT** — `lib/approvalsApi.ts` says of
  the transition payload *"There is no `decidedBy` here on purpose. The server takes the deciding
  identity from the authenticated principal (SEC-IAM-002) — sending one would be a field the server
  ignores"*, one screenful above the `requestedBy: string` the service trusted. F2-215's headline:
  generalising an invariant one field at a time leaves the remainder looking covered, because the
  mechanism that covers them is visibly present.
  ⚠⚠ **Leg 2 is the sharper one: `assignedTo` IS `transition()`'s authorisation input**
  (`callerUsername.equals(r.getAssignedTo())`, SEC-IAM-002), so whoever creates the request decides
  who may approve it. Probed as `compliance`, who holds WRITE **and** APPROVE: raised a P1
  risk-acceptance approval assigned to `compliance`, then **approved it** — 200 then 200, with
  `decided_by` correctly recording them both times. **The audit trail told the truth about who
  acted while nothing stopped them** (F2-222's leg 2). The gate compares case-**insensitively**
  because the column collates CI (F2-179).
  ⚠⚠ **A FOUR-EYES REFUSAL IS A 409, AND THE CONVENTION HAD TO BE MEASURED RATHER THAN READ.** My
  first draft threw `DomainAccessDeniedException` → **403**, and the live probe caught it. There are
  two conventions in the tree and one is the outlier: `BusinessRuleException`'s javadoc says *"Use
  this when the rule itself is the message — e.g. segregation-of-duties"* and maps to **403**, and it
  is used **once in all of `module/`**; the one real self-approval gate,
  `NdiEvidenceItemService.approve` (SEC-NDI-003, the maker/checker control F2-218 probed at 409),
  throws `IllegalStateException` with a *"Segregation of duties:"* message, which the handler maps to
  CONFLICT — and CLAUDE.md's own house rule says the same. 🔑 **F2-205's question applied to an
  exception type: consistency among copies is not evidence, so ask which one the platform actually
  runs.** 403 is also wrong in substance: the caller IS entitled to raise approval requests, so this
  is a conflict with a rule, not a missing permission.
  ⚠⚠ **The third site is one where the server does not merely record WHO, it MEASURES them.**
  `ExerciseRuntimeService#recordResponse` took `participantUserId` from the body
  (`@NotNull(message = "participantUserId is required")`) while `responseAt` is initialiser-stamped
  *and* re-stamped, and `responseTimeSeconds` is **computed** from the inject's dispatch — so the row
  is the evidence an ISO 22301 / NCEMA reviewer reads to accept that a named participant reacted
  within N seconds. Probed: `viewer` (id 6) filed a response **as `compliance` (id 3)**, and the
  audit row **quoted the caller's claim** (F2-223's exact defect). Every trail now reads off the
  saved row. ⚠ The route is `isAuthenticated()` and that is CORRECT — filing a response is a duty,
  the ADR-010 shape — which made this **worse** rather than better: the population who could forge
  was every authenticated user. The honest client already sent `user.userId` from the decoded JWT;
  a machine caller sent whatever it liked, which is where this class of defect lives (F2-179).
  ⚠⚠ **FILED, and the sharpest one is a register reading 11 PENDING that admits nobody: 9 of those
  11 can be decided by NO ONE.** `transition()` authorises on `callerUsername.equals(assignedTo)`,
  and 9 rows hold a free-text person name (`Khalid Al-Shehri`, `Faisal Al-Dosari`, …) matching no
  username in `iam.users` — so the assignee cannot decide, `myInbox` never returns them, and only a
  PLATFORM_ADMIN override moves them. Identical in dev and prod, and **every one is self-assigned to
  the same invented officer named as its requester** (F2-207's measured cast of 65, now in a *server*
  register). `requested_by` and `assigned_to` both hold **two vocabularies** — usernames and display
  names — under **zero CHECK constraints** (F2-222's shape). Repair rewrites 21 live rows in two
  databases (F2-183/F2-196's rule) and a resolvability check at create needs a user-directory port
  `approvals` does not have: **owner decision.**
  ⚠ **Also filed, found while planting the fixture: `POST /exercise-runs/{id}/custom-inject`
  answers 500 on its DEFAULT path.** `dispatchNow` defaults to **true**, and the combined path is a
  ROWVERSION double-flush — `addCustomInject` saves the INSERT, `audit.log` + `emit` flush, then
  `dispatchInject` re-loads and saves the same row again in one transaction. The dedicated dispatch
  route answers **200 on the identical row**, which is the discriminator. Exactly the landmine
  F2-175 widened: *any* flush between the two saves does it. Plus `StartRunRequest.runCode` as an
  *"optional override"* — F2-172(d)'s family on a code cited in the NCEMA after-action record,
  outside `ClientSuppliedReferenceRatchetTest`'s `setExternalId`/`setRefNumber` key.
  ⚠⚠ **MASTER WAS RED BEFORE THIS SLICE, ON A DOCS GATE, AND F2-219 HAD ALREADY RECORDED THE
  LESSON.** `FindingsRegisterCoverageTest#specsOwedOnlyEverDecreases` was failing 30 > 29: F2-225's
  docs commit referenced F2-225 in the plan and wrote **no register spec**. F2-219's words were *"a
  targeted re-run after a docs commit is not the suite, and the docs gate is precisely the gate a
  docs commit can break"* — and it recurred in the **next** slice, on a **different** docs gate,
  because the targeted re-run named four gate classes and this was not one of them. 🔑 **Naming the
  trap does not exempt you from it, and the fix is a rule about the COMMAND rather than about the
  list: after a docs commit, run the suite.** Both specs written; owed 30 → 28. ⚙ Its reverse leg
  then fired correctly on the new spec until the plan row landed — **both documents must reference a
  finding, because one recorded in only one of them is one an audience cannot see.**
  ⚠ **Three instrument errors of mine, all caught by running things.** (a) The re-derivation's first
  pairing reported **3** sites where F2-225's permissive scan found **19** — I had re-imposed the
  root pairing, which is the narrowing this blind spot is *about*, since a clock with no setter has
  no verb root to take; **under-reporting reads exactly like a clean tree** (F2-223's seven
  corrections, all seven shrinking). (b) The pre-fix control compared `master` with `HEAD` before
  anything was committed, so it compared master with **itself** and reported **all 12 legs SILENT**
  — loud rather than quiet, which is the only reason it cost one iteration. (c) `classify()` asked a
  **second** `Matcher` instance for `.start()` after calling `.find()` on a first, which throws *"No
  match available"*; `lookingAt()` is the anchored-prefix primitive that was wanted.

- ⚠⚠ **A NOMINALISED CLOCK HIDES AN ATTRIBUTION: THE HALVES ARE NAMED AFTER THE NOUN AND THE
  VERB, SO THEY SHARE NO ROOT** (F2-225, measured 2026-08-17). Executing F2-224's filed blind spot,
  the population was re-derived first (F2-221): a permissive (clock, actor) co-occurrence scan
  reports **79** unpaired candidates where the shipped gate reports 4, and the honest split is
  **18 NOISE-WORD** (ordinary English words the agent-noun suffix matches — `lineNumber`,
  `stepOrder`, `isCrossBorder`, `regulatoryAnchor`, `vendorName`), **34 ASSIGNMENT** (names who
  WILL act, or the act's target — `ownerUserId`, `assignedToUserId`, `escalatedToUserId`) and
  **27 CANDIDATE**. 3 confirmed, 2 fixed, 1 filed. 🔑 **The classification is the finding as much
  as the count is: a suffix rule measures spellings, and two thirds of what it returns is not an
  attribution at all.**
  ⚠⚠ **The live half was a regulator return.** `NcaEccFilingService#transition` stamps
  `cisoApprovalAt` from the server's own clock and copied `cisoApprover` out of the request body in
  the **same `switch` branch** — F2-223's shape exactly, on the NCA ECC self-assessment this
  organisation files with the National Cybersecurity Authority — and it was invisible because
  `cisoApprover` reduces to `approv` while `cisoApprovalAt` reduces to `approval`. Probed 13/13
  pre-fix and 13/13 post-fix as `compliance` against **planted** rows (F2-206): *"Nobody Who
  Approved This"* stored as the CISO who approved the filing, beside two server clocks 0.14 s
  apart. Structurally different from F2-224's role-named actor (`standDownAuthoriser`), which is
  still blind and still asserted — 🔑 **two distinct ways for one attribution's two halves to share
  no root have now been found; assume a third.**
  ⚠⚠ **AND THE SAME REGISTER CARRIES A SECOND SPLIT NO SETTER-MATCHING SCAN CAN EVER SEE:
  `drafted_at` is a FIELD INITIALISER.** F2-215's third of four filling mechanisms means the server
  owns WHEN with no `setX(now())` anywhere for a detector to find, while `draftedBy` was
  `@NotBlank` from the body. Found by reading the entity, not by any instrument. **Measured before
  filing** (F2-221): of 67 entities carrying a clock-initialised field that names an ACT rather
  than the row, **19 sites** also take an actor from the request — 4 noise, 6 assignments, 9
  attributions, of which 1 was fixed and 4 refuted by reading. 🔑 One refutation is its own rule:
  `CoiService#recordRecusal`'s clock is `req.recusalDate() != null ? … : now()`, so **a field
  initialiser is only a server stamp while no writer overrides it** — F2-224's caller-overridable
  clock, one layer down. ⚠ `createdAt`/`updatedAt` are **row** stamps, not act clocks: every row
  has one, so they can never be the proof F2-223's rule rests on. Including them inflates the raw
  count from 19 to 46, almost all creates.
  ⚠⚠ **A FORGED ATTRIBUTION THAT IS ALSO AN AUTHORISATION INPUT HAS THREE OUTCOMES, NOT ONE.**
  `ApprovalChainInstanceService#startChain` took `originatorUserId` from the body, and
  `withdraw()` reads that column with **no other input**. Probed as `compliance`(3) naming
  `so_officer`(8): the chain stored 8, **the real raiser was then refused 403 on their own chain,
  the named stranger was GRANTED the withdraw, and naming a nonexistent id (999999) left the chain
  withdrawable by nobody.** 🔑 **`ApprovalChainController` had hardened AND documented the other
  two identity-bearing routes (SEC-IAM-001, on `recordAction` and `withdraw`) and left this one —
  the route that ESTABLISHES the identity the other two check against.** F2-215's headline again:
  generalising an invariant one site at a time leaves the remainder looking covered, because the
  mechanism that covers them is visibly present. ⚠⚠ **All three request records declared
  `@NotNull @Positive Long <x>UserId` — identical wire shapes, opposite fates: two
  required-and-IGNORED, one required-and-HONOURED. Nothing a caller or a reader could see
  distinguished them.** All three deleted (F2-184: a required field the server discards tells the
  caller their value matters; one it TRUSTS for an authorisation decision is worse again).
  ⚠ **No FE client calls `/api/v1/workflow/chains` at all** — the three routed pages render
  `SEED_*` corpora (F2-207's filed population) — so the fix carried zero frontend risk, and the
  body field was an invitation to build the screen that would fire it (F2-214's shape).
  ⚠⚠ **F2-117 IS HALF CLOSED, AND THE HALF THAT MOVED IS THE ONE ITS RECORDED BLOCKER SAID COULD
  NOT.** That row reasoned a four-eyes comparison would be inert because **0 of 4 people-labels
  resolve to a platform user** — correct about **SEGREGATION** (approver ≠ reviewer, which does
  need identity columns) and **applied to ATTRIBUTION** (who the approver IS, which needed nothing
  but `CurrentActor`). 🔑 **The dependency ran the other way: comparing a reviewer against an
  approver is meaningless while the approver is whatever the caller typed, so attribution was the
  PREREQUISITE for the comparison the blocker was protecting.** F2-178's *"re-measure a recorded
  blocker before believing its estimate"* in a new shape — the estimate was right about a fix that
  was not the one being blocked. ⚙ One of its inputs has also moved: `ciso_approver` now holds a
  username, which **does** resolve to a platform user.
  ⚠⚠ **A PINNED KNOWN-DEFECT TEST PASSES ON A DEFECTIVE TREE BY DESIGN, SO THE CODEBASE'S OWN
  RECORD OF THIS DEFECT WAS INVISIBLE TO EVERY SCAN — THE FINDING HAD TO BE MADE TWICE.**
  `NcaEccFilingLifecycleTest` asserted the mechanism (*the service consults no security context*)
  with an explicit exit condition, and it was honest and well built. It still surfaced nothing:
  🔑 **a green pin and a green clean tree are indistinguishable from outside.** Its tripwire then
  worked exactly as designed — it failed the build the moment the service read a principal, and
  `FindingsRegisterConsistencyTest` refused the commit until the register cell was updated. Two
  instruments in series, both doing their job.
  ⚠⚠ **A BINARY CLOSURE GATE CANNOT EXPRESS A HALF-CLOSED FINDING, AND THE SAFE DIRECTION IS FOR
  THE TEST TO UNDER-CLAIM RATHER THAN THE REGISTER TO OVER-CLAIM.** `readsOpen()` is true when an
  OPEN mark precedes a CLOSED one, so a 🟡 *"HALF CLOSED"* cell reads open — correctly — while a
  javadoc saying `F2-nnn … CLOSED` claims the whole finding. **The register is what an auditor is
  handed, so marking a P1 closed to satisfy a regex would have retired a control that is still
  half unbuilt.** ⚙ That gate also cannot tell a quotation from a claim (F2-193's shape) and the
  `unquoted()` convention that solves it elsewhere carries its own warning against whole-Java-file
  use (F2-216) — so name the shape with the **placeholder id**, exactly as the gate's own failure
  message does, rather than paraphrasing away your evidence.
  ⚠ **My own new gate leg failed on correct code.** It banned `originatorUserId` file-wide and the
  RESPONSE records legitimately carry it — telling a client who raised a chain is the point of
  reading one. Re-scoped to the three request records. *Scope a scan to the shape of its claim*,
  landing yet again on a leg written while quoting it; **a gate that fails on correct code trains
  the next reader to edit the assertion rather than the code** (F2-214).
  ⚙ **Verify a widening by what it ADDS, in both directions.** The nominalising stem is emitted as
  an EXTRA key so it can only add pairings, and it was checked against **9 must-pair** (every pair
  the gate already reports, which must keep pairing) and **11 must-not-pair** cases drawn from the
  three measured buckets: it adds **exactly one**. Then the pre-fix control over `git show master:`
  reported **4 narrow / 5 widened**, which is the only evidence that the widening is load-bearing
  rather than decorative — F2-223's rule: *a green scan on the fixed tree cannot tell you the
  detector works.* ⚠ Note what is in NEITHER: the chain finding, because `startedAt` and
  `originatorUserId` share no root and never will. **It was found by reading, and a structural leg
  is what protects it.**
  ⚠ **A `TOP n DISTINCT` is a syntax error in SQL Server** (`DISTINCT TOP n`), and my table-name
  guess was wrong again — `workflow.approval_chain_instance`, singular. The measurement script
  looked the name up in `sys.tables` and printed near-misses instead of reporting "no data", which
  is the only reason that cost nothing. **Resolve from `sys.tables`/`sys.columns`; never guess.**

- ⚠⚠ **TWO DETECTOR ERRORS IN OPPOSITE DIRECTIONS PRODUCE A GREEN THAT NEITHER EARNS, AND THE
  ONE THAT SHRINKS THE POPULATION IS WHAT HIDES THE OTHER** (F2-224, measured 2026-08-16). Executing
  F2-223's filed 10-entry work list, the denominator was re-derived first (F2-221). A deliberately
  **permissive** pairing found **15** split-attribution sites where the shipped gate's green build
  pins the population at **13** — and both extras were **REFUTED**, because their clock is
  `req.x() == null ? now() : req.x()`: a caller-supplied value with a fallback, not a server stamp.
  🔑 **They were absent from the population for an ACCIDENTAL reason — the root reduction could not
  pair `auditDate` with `auditorUserId` — and absent reads identically to correctly excluded.** Had
  the reduction worked, `isServerClock` would have reported both as splits, and the obvious "fix"
  would have forged an attribution onto an **external** auditor's work (`MlModelAudit` declares
  `auditorExternalParty` one line below `auditorUserId`; an ISO/IEC 42001 audit is routinely done by
  a firm). So the widening (`…Date`/`…On` are WHEN fields; pair on a key SET, not one reduced root)
  and the narrowing (a clock the caller may override is not a stamp) **must land in the same
  commit** — F2-219's rule, now inside the gate F2-223 built to enforce F2-219's rule.
  ⚠⚠ **The live half was two statutory attestation-of-record surfaces.** Probed **12/12 pre-fix and
  12/12 post-fix** as `compliance` (user 3) against **planted** rows (F2-206): a SAMA obligation
  attestation with `attestedByUserId: 4` **stored 4**, and **omitting the attester was a 400** —
  `AttestationRecordRequest` carried `@NotNull @Positive`, so the server **required an attribution it
  never verified**, the third instance of that exact shape after F2-223's IIA annual plan. Also the
  obligation review, a regulatory-change ASSESSED, a BCP invocation DECLARED and an ERM risk
  attestation. **Refuse rather than store null** is safe precisely because the field was already
  mandatory: no working caller is broken (F2-182).
  ⚠⚠ **`ObligationDetailPage` sent `attestedByUserId: 1, // current user — wired via JWT claim in
  v2`.** Every obligation attestation and every review recorded from that screen was filed against
  **user id 1 regardless of who was signed in** — a comment promising a fix, load-bearing.
  `VendorBcpCampaignDetailPage` asked the operator to TYPE the reviewer into a prompt placeholdered
  `'e.g. Salman Al-Otaibi (TPRM Lead)'` — **seventh recorded instance of an invented officer in a
  placeholder teaching an operator to type somebody else's name.**
  🔑 **F2-223 guessed exactly one thing, marked it a guess, and was right.** The vendor-portal pair
  is BY DESIGN: `doSubmit`'s second caller is `submitByToken`, whose route is
  `@PreAuthorize("permitAll()")`, so there is no principal to resolve at all. **A correct guess and a
  verified claim are indistinguishable in prose** — which is the entire argument for keeping
  `WORK_LIST` and `BY_DESIGN` as two buckets instead of one map of reasons (F2-188). Adjudication:
  **8 closed · 2 BY DESIGN · 0 open**, `WORK_LIST` 10 → 0.
  ⚠⚠ **The shape NO root-matching detector can see, named rather than papered over.**
  `InvocationService#transition` writes `setStandDownAt(now)` and, three lines below the `declaredBy`
  this slice closed, `standDownAuthoriser` from the request. Same split — **invisible to the shipped
  detector AND to the permissive re-derivation**, because the actor field names the **ROLE PERFORMED**
  (`authoriser`) rather than the verb (`standDown`), so the halves share no root. Found by reading.
  `anActorFieldNamingTheRoleRatherThanTheVerbIsNotPaired()` asserts the blind spot so it cannot be
  mistaken for coverage; the site is **filed, not blessed** — a crisis stand-down is authorised by the
  CMT chair and typically recorded by the coordinator, so which one the column means is an owner call.
  ⚠ **Two more instrument corrections, both in gates from earlier slices of this lineage.**
  (a) `noReasonIsABareLabel` demanded `REFUTED` or `FILED` — **the two verdicts F2-223 happened to
  reach** — and rejected a correct, verified `BY DESIGN` reason. F2-217's headline (*a census keyed on
  a list measures the list*) applied to a **gate's own verdict vocabulary**; the failure mode is the
  benign-looking one, since it hides no defect but pushes the next author to reword a true statement
  until the regex is happy. (b) The gate keys sites on a **simple class name** and
  `RegulatoryChangeService` exists in `module/compliance` *and* `module/regchange` (F2-222 measured 15
  entity names declared twice). Only one has the pair, so nothing collapses **today** — a fact about
  today, not a property of the key, and nobody was maintaining it (F2-177).
  ⚙ `RequestSizeCapMirrorTest`'s floor went **8 → 7** with the reason recorded: the vendor-BCP
  `reviewedByName` cap went with its request field. Second consecutive slice to lower it, and both
  times the answer to *"is this rot?"* was **the field is gone, not the check** (F2-213).

- ⚠⚠ **THE SERVER STAMPS *WHEN* FROM ITS OWN CLOCK AND THE CALLER SAYS *WHO* — ONE ATTRIBUTION
  SPLIT ACROSS TWO OWNERS** (F2-223, measured 2026-08-16). F2-222 filed *"31 authority fields
  written from the request"*, measured by a **name-shape** test — the instrument class F2-217 had
  just proved measures its own vocabulary. Re-derived per F2-221, the question is structural:
  **when one method writes `set<verb>At(<the server's clock>)` and, for the SAME verb, writes
  `set<verb>By…` / `set<verb>erUserId(…)` FROM THE REQUEST, it has split one attribution across two
  owners — and the clock half is the proof, because by stamping the time itself the server has
  already declared that it is the recorder of this act.** No field-name list; the verb root comes
  from the code. **23 split sites + 2 the detector is structurally blind to = 25**: 11 fixed, 2
  filed, 1 refuted, **10 unadjudicated** on a decrease-only work list.
  ⚠⚠ **The complementary question is needed because the split detector cannot see the worst case.**
  *Which routes are guarded by a permission whose ACTION NAMES AN ACT (`MODULE:APPROVE` — 17
  catalogued in dev, 11 in prod) and let the body say who performed it?* That found
  `CptyService#assignRating` and `#upsertLimit`, invisible to the split scan **because there the
  client supplied `approvedAt` too**, minting a SAMA large-exposure approval date in the browser.
  🔑 **A detector keyed on an ASYMMETRY cannot see the case where both halves fell the same way.**
  ⚠⚠ **Probed 14/14 pre-fix and 18/18 post-fix as `compliance` against PLANTED rows (F2-206).** An
  IIA Standard 2010 annual audit plan approved with `approverUserId: 4` **stored 4** while the actor
  was 3 — and **omitting the approver was a 400**: the server *required* an attribution it never
  verified, while stamping the time itself on the next line. A PDPL Art.18 disposal certificate
  stored `verifierName` = `executorName` = *"Nobody Who Approved This"* **beside a `verifiedBy` id
  holding the real principal** the controller had already resolved and passed in. An AML sanctions
  hit recorded the forged reviewer, **and the audit row quoted the caller's claim**
  (`" by user=" + req.reviewerUserId()`) — 🔑 **the trail quoting the claim is what makes a forged
  attribution invisible to whoever audits it.** Every audit line now reads off the SAVED ROW.
  ⚠ **F2-217 closed the CREATE path on the annual-plan register and so funnelled every approval
  through the method that had the same defect.**
  🔑 **THE FIX ALREADY EXISTED ONE FILE AWAY.** `KycService#disposition` resolves the decider from
  `CurrentActor` and refuses to proceed without one — *"An AML disposition with no decider is not a
  disposition."* Same package as `AmlAlertService`, same concept (`decidedByName` on a screening
  hit), never copied. Seventh instance in this lineage of the correct artefact beside the wrong one.
  And F2-222's *"blocked on a principal→user-id resolution this slice did not build"* is **refuted
  by the line above the defect**: `currentUserId()` → `CurrentActor.userId()`.
  ⚠⚠ **The visible half: the UI asked the operator to TYPE it.** `EthicsPage` opened
  `window.prompt(t('amlDecidedByPrompt'))` and sent the answer as the decider of an AML alert —
  including `STR_FILED`, a Suspicious Transaction Report to the Saudi FIU — with `?? ''` on Cancel,
  so the attribution could be the empty string. `StrategyRoiPage`/`QuantitativeBiaPage` used
  `dialog.prompt` placeholdered *"e.g. Faisal Al-Subaie — Group CFO"*, and
  `CrisisCommsDispatchWizard` had a free-text *"Dispatched by (name)"* input. 🔑 **An invented
  officer in a placeholder is how a free-text attribution field teaches an operator to type somebody
  else's name.** 6 surfaces and **19 request-DTO fields** deleted on both sides of the wire (F2-184).
  ⚠⚠ **SEVEN detector corrections, ALL SEVEN SHRINKING THE POPULATION — the direction that reads
  exactly like a clean tree.** (1) `@PreAuthorize(CONSTANT)` invisible to a literal-only regex — the
  house idiom in `CptyController`. (2) A nested DTO (`CptyDtos.UpsertLimitRequest`) unresolvable by a
  `<Name>.java` file lookup, so **no fields read as no authority fields**. (3) `@NotBlank @Size(…)
  private String x;` — the field regex anchored on the modifier, so four body types reported ZERO
  fields. (4) The **`-or`** agent noun: `executorName` reduced to root `name`, so it could never pair
  with `executedAt` — found only because the fix for `#verify` sat one method below an identical
  `#complete` the scan had never reported, i.e. **the class looked measured**. (5)/(6) A **qualifier
  BETWEEN the verb and the tail** (`approvedByLegalName`) defeating both the root extractor and
  `ACTOR_TAIL`; it cost a real site, `CrisisCommsTemplateService`, found by a residual grep rather
  than by the instrument — and that is the CISO sign-off `CrisisCommsDispatchService` refuses to
  dispatch without, whose own comment called the result *"an honest governance trail"*.
  ⚠⚠ **(7) THE CLOCK REACHED THE SETTER THROUGH A LOCAL VARIABLE, AND ONLY THE PRE-FIX CONTROL
  FOUND IT.** Running the shipped detector over `git show master:` reported
  `CrisisCommsTemplateService` **SILENT** — a file this slice had already fixed by hand — because
  there it is `LocalDateTime now = LocalDateTime.now(); … setApprovedByLegalAt(now)`, which an
  argument-only test for `now(` cannot see. Teaching the detector that shape **immediately surfaced
  ten more sites** across ERM, BCM invocation, MTPD inheritance, obligation attestation, regulatory
  change and vendor BCP attestation. 🔑 **A green scan on the fixed tree cannot tell you the
  detector works; only running it against the defect can — and closing a detector gap is how you
  find the population you did not know you had.**
  ⚠ **The ten ship as a WORK LIST and deliberately NOT as reasons.** F2-188 measured what happens
  when a slice writes a reason for something it has not read: five claims, five false, and the
  fluency was what made them look verified. At least one is probably BY DESIGN
  (`VendorBcpAttestationService#doSubmit` takes `submittedByContactName/Email` on a **vendor portal**
  submission, where the submitter may not be an authenticated principal at all) — and that is a
  guess, which is exactly why it is on the list rather than in the adjudicated map.
  ⚠⚠ **`mvn -o test` REPORTED 46 `NoSuchMethodError` FAILURES WHILE `mvn -o test-compile` SAID
  "Nothing to compile — all classes are up to date".** Maven's incremental test compiler keys on
  TEST source timestamps, so changing a **main** DTO leaves the previously-compiled test classes in
  place and the suite runs **stale bytecode**. 🔑 **A run against stale test classes reads as 46
  broken tests rather than as "the tests were never recompiled."** The authoritative list only
  appears after `rm -rf target/test-classes` — which is also how to force it **without** `mvn clean`,
  the command that kills the dev backend.
  ⚠ **Six unit tests asserted the OLD contract** — that the value the CALLER passed was the value
  STORED, which is precisely the defect (F2-159). `transition_boardReviewToApproved_requiresApprover`
  asserted the 400 as correct and was **repurposed**: the refusal now fires when there is no
  authenticated principal to record, keeping the half that protects the record.
  ⚙ **`RequestSizeCapMirrorTest`'s own reverse leg fired** when its `approverName` landing outlived
  the field, and its anti-vacuity floor went 9 → 8 with the reason recorded — **a population that
  falls after a fix is not automatically rot, but it is always worth asking which** (F2-213).
  ⚙ **Filed:** the 10 unadjudicated sites; `CrossBorderTransferService#assess::residencyCheckBy`
  (🔑 **where a method lets the caller supply the clock TOO, the act may not be this call, so its
  performer need not be the caller** — a PDPL Art.29 owner decision); and
  `ControlLibraryService#updateMapping::reviewerUserId`, blocked on a second defect —
  `setLastReviewedAt(now())` fires on **every** update, so editing a rationale already records the
  mapping as reviewed.
- ⚠⚠ **WHETHER A SCALAR PARAMETER IS CALLER DATA OR A SERVER VALUE IS A FACT ABOUT ITS CALL
  SITES, AND THE GATE THAT HAD TO DECIDE IT ONLY HAD THE DECLARATION** (F2-222, measured
  2026-08-16). F2-218's fourth detector correction — *"a scalar method parameter is still caller
  data"* — exists because `markUnderReview(Long id, String reviewerName)` is filled from a body
  field, and a gate that fails on correct code is the one failure a gate must never have. It is
  **right at the declaration and wrong at the call site**:
  `PolicyManagementService#transition(Long id, String to, String actor)` is called as
  `service.transition(id, body.get("to"), actor(auth), isComplianceAdmin(auth))`, so
  `p.setApproverName(actor)` IS a server stamp — and because `ServerStampedFieldContractTest` could
  not see that, `PolicyDocument#approverName` never entered the stamped set and `update()` writing
  it from the request was not a contradiction by that gate's stated question. 🔑 **A narrowing added
  to stop a gate firing on correct code is itself a claim about the codebase, and it inherits a
  population** — here every attribution that reaches its service as a parameter.
  ⚠⚠ **The live defect, P2, probed 13/13 against PLANTED policies (F2-206).** `transition()` carries
  **C-6 segregation of duty** — *"the policy author/owner cannot approve their own policy"* — the S5
  committee-referral check and CH-10 document control, then stamps `approverName` from the acting
  principal. `update()` is guarded by the **same** permission (`COMPLIANCE:WRITE`, **13 roles, 5 with
  an active holder**, dev = prod) and copied the approver out of the body: the author refused with a
  **403** wrote their own name with a **200**, on a document that stayed APPROVED.
  ⚠⚠ **THE FREEZE WAS ALREADY THERE AND WAS WRITTEN FOR THE WRONG STATE.** The top of `update()`
  says *"Once PUBLISHED, only metadata fields are editable"* and already excludes the approver — but
  the stamp happens at **APPROVED**, so the attribution was frozen **one state too late**. Sixth
  instance in this lineage of the correct artefact sitting beside the wrong one.
  ⚠⚠ **AND A CONTROL'S INPUT DESERVES THE SAME PROTECTION AS ITS VERDICT — leg 2 is the sharper
  one.** C-6 compares the actor against `createdBy` (immutable, F2-215), `ownerName` and
  `ownerEmail`. Probed: owner refused **403**, `PUT {ownerName: "somebody else"}` **200**, approve
  **200**. 🔑 **The party a control constrains could edit the control's own input through an adjacent
  route with the same permission.** Leg 1 defeats the RECORD of the control; leg 2 defeats the
  CONTROL — and in leg 2 the attribution is stamped correctly, so the audit trail tells the truth
  about who acted while nothing stopped them.
  ⚠ **The fix must not refuse what works.** The approver stays editable in DRAFT/REVIEW: of the
  **24 of 41** live dev rows (**23 of 37** prod) naming an approver, most name an **office**
  (`'رئيس الهيئة'` ×12, `'Board Risk Committee'`, `'Audit Committee Chair'`), so the column carries
  **two meanings** — F2-196's shape inside one column, **filed** rather than collapsed. Owner fields
  freeze at REVIEW, with the machine's `REVIEW → DRAFT` edge as the reassignment route, so the window
  narrows rather than the capability disappearing. `BEFORE_APPROVAL` is **derived** by driving the
  real machine, and is an **inclusion** list so an unrecognised status protects the attribution
  rather than exposing it (F2-196).
  ⚠⚠ **A TYPE NAME IN SCOPE IS NOT THE TYPE THAT GOVERNS.** A first scan keyed `Type#field` on the
  **simple** name and produced **19 cross-class contradictions**; every one was spurious, because all
  four pairs are duplicate simple names in different packages (`Vulnerability` cyber/cyber_lc,
  `CapaTask` audit_lc/capa, `ScreeningHit` aml/ethics, `RegulatoryChange` compliance/regchange).
  F2-221's constant lesson one level out. ⚙ **Filed, latent not live: 15 of 433 entity simple names
  are declared in two packages** (30 classes, 30 distinct tables) and three shipped gates key on the
  simple name — measured, **no key collapses two registers today**, because only one twin of each
  pair has a machine or a guarded route, and nobody is maintaining that (F2-177).
  ⚠⚠ **A MEASUREMENT TAKEN AFTER THE PROBE COUNTS THE PROBE'S OWN ROWS, AND THE ARTEFACT IT INVENTS
  LOOKS EXACTLY LIKE A FINDING ABOUT THE PLATFORM.** My population query ran after probe 1 had
  planted a policy and reported *"42 live, 25 with an approver, **1 whose approver equals its own
  createdBy**"* — that 1 was the probe's own forgery. The honest figures are **41 / 24 / 21 / 0** in
  dev and **37 / 23 / 20 / 0** in prod. Re-measure after cleanup, and reconcile the count.
  ⚙ **Three more refuted by reading, each an instrument rule.** A **ternary whose CONDITION reads the
  request but whose BRANCHES are both server values** is a stamp, not a caller write
  (`DpiaService#addMitigation` — a sixth shape after F2-219's five). A
  **preparer/reviewer/approver trio written together** is stage ASSIGNMENT, not attribution
  (`RegReportFiling`). **`next*ReviewDate`** is a plan. And a guard-resolution scan keyed on the
  method name `update` matched every controller in the tree — scope the scan to the shape of its
  claim, yet again.
  ⚙ **Filed:** `LargeExposureLimit#approvedAt` + `approvedByUserId` are written straight from the
  body by `CptyService#upsertLimit`, whose route is `CPTY:APPROVE` — a permission **V369 created
  specifically** because *"deciding the rating and the limit is the credit authority's"* under SAMA's
  large-exposure rules. 0 of 74 rows carry the column in either database, so it is latent, and the
  fix needs a principal→user-id resolution this slice did not build. Also filed: the two meanings in
  `policy.documents.approver_name`; and the remaining **31** authority fields written from the
  request with no guarded route stamping them anywhere.
- ⚠⚠ **A STATE MACHINE'S VOCABULARY AND ITS COLUMN'S CHECK ARE TWO ARTEFACTS, AND NOTHING WAS
  COMPARING THEM** (F2-221, measured 2026-08-16). F2-220 filed two status columns as unconstrained.
  That filing's population was **the nine registers F2-220 happened to visit**; the same structural
  discriminator (`ServerStampedFieldContractTest#machineOwned`) finds **28** declared machines, so
  the nine were only the subset whose create *also* accepted the field. 🔑 **A filed item inherits
  the population of the slice that filed it — re-derive the denominator before executing a filing.**
  Ninth appearance of F2-198's shape here, after a filename, a three-entry allow-list, a module
  name, a controller-bearing table, an `extends` clause, a field-name list inside a fix, a name list
  in a census and a claim shape in a gate. Measured across all 28: **12 AGREE · 7 NO CHECK (now 5) ·
  6 constrained with no named constant to mirror · 5 not a vocabulary at all · 0 DISAGREE**. The
  whole-database denominator is **324 state-ish text columns, 201 with no CHECK, 177 populated** —
  do not try to close those in one sweep, most still have an open writer (F2-183).
  ⚠⚠ **The live finding was a register the filing never mentioned: 11 of 30 ethics cases cannot be
  advanced by ANY route, identical in dev and prod.** `EthicsCaseService.transition` opens
  `TRANSITIONS.getOrDefault(c.getStatus(), Set.of())`; `ethics.cases` stores **INVESTIGATING (8)**
  and **TRIAGED (3)** and neither is a KEY, so `allowed` is empty and every target throws. 🔑 **Both
  are near-synonyms of real keys (`INVESTIGATION`, `TRIAGE`), which is exactly why nobody noticed** —
  a reader skimming the register reads INVESTIGATING as the investigation state. A whistleblowing or
  misconduct case that cannot be substantiated, dismissed, escalated to a regulator or closed.
  F2-203's `audit_lc` shape in a module nobody had checked. ⚠ **Filed, not fixed, and that is the
  rule rather than reticence**: F2-22b(v) already made `create` and `update` refuse a body-set
  status, so these rows are seed-origin; a CHECK would fail Flyway on them and collapsing the
  synonyms rewrites live rows in two real databases (F2-183/F2-196/F2-203). Proved live against a
  **planted** case (F2-206), never a seeded governance record: 5/5 declared targets refused, then
  the same case moved to `INVESTIGATION` advanced with a 200 — **the token, not the route.**
  ⚠⚠ **"THE ROW IS STUCK" AND "THE ROW IS FINISHED" ARE THE SAME OBSERVATION unless the machine
  declares its terminal states explicitly.** A `switch (from) { … default -> false }` machine
  declares terminality by OMITTING a case, so a label-based scan reported `ControlAssessment`'s 17
  dev / 16 prod `APPROVED` rows as untransitionable when they are simply approved. Only a machine
  whose terminals are explicit keys (`"CLOSED", Set.of()`) can distinguish them — which is why the
  ethics finding stands and that one was thrown away. Ask which shape you have before believing a
  reachability count.
  ⚠ **A constant that is in scope is not the constant that governs.** The scratch census reported a
  DISAGREE on `ChangeRequest#status` by matching `OUTCOMES.contains(...)` — the vocabulary of
  `change_requests.outcome`, in the same method — and on `NdiAssessmentCycle#status` by matching
  `JUMP_STATUSES = Set.of("ARCHIVED")`, one jump target. `StateMachineColumnMirrorTest` resolves the
  constant by **reflection** and the CHECK from the migration SQL, so neither mistake is available
  to it; nothing in it is a regex over Java.
  ⚠⚠ **HOUSE RULE 5 IS NOT ONLY ABOUT TEST DATA — `cc.definition LIKE '%[status]%'` IS A CHARACTER
  CLASS.** It matched any definition containing s/t/a/u, i.e. every CHECK on the table, and reported
  five registers that AGREE as DISAGREE. **CHARINDEX for literals, in detectors too.**
  ⚠⚠ **And the gate's own claim-verifier had the same defect one layer up.**
  `migrationDeclaresCheckOn` was `CONSTRAINT \w+ CHECK\([^;]{0,4000}?\bcolumn\b` over any file
  whose text mentioned the table, and it reported CHECKs on `ethics.cases.status`,
  `ethics.disclosures.status` and `risk.risk_treatments.status`, none of which has one. A
  4000-character window around the commonest word in the schema matches a constraint on a different
  column of a different table. 🔑 **Scope a scan to the shape of its claim** — third instance in one
  slice. ⚙ Both errors **over**-reported, which fails loudly; the quiet direction is the dangerous one.
  ⚠⚠ **TWO MUTATIONS SURVIVED AND NEITHER WAS A HOLE IN THE GATE.** The un-scoping mutation survived
  because **`ethics.cases` has zero CHECK constraints**, so an un-scoped detector had nothing to
  falsely match and answered correctly by accident — 🔑 **a negative control has to reach the code it
  controls**, F2-220's headline one slice later, in my own harness. Re-fixtured on `bcm.gaps`, which
  carries three vocabulary CHECKs and is not a table V376 touches, so the control keeps
  discriminating on a pre-V376 tree. The second survived because on a green tree every constraint
  resolves, so the unresolvable branch is unreachable from the corpus and only the pre-fix control
  reached it; extracted as `mirrorDrift` and asserted directly. ⚙ Recorded rather than patched:
  first-vs-last declaration is an **equivalent** mutant, because every mirrored constraint is
  declared exactly once — now its own assertion rather than an implicit assumption. And raising a
  decrease-only ceiling is an **invalid** mutation: you can always weaken an assertion (F2-206), and
  a ceiling can only be tested by growing the population.
  ⚠ **A range is not a vocabulary**, and that limit is asserted rather than accidental:
  `CK_crisis_events_severity` is `severity BETWEEN 1 AND 5` and is deliberately invisible to this
  gate. ⚠ V376 adds **no casing normalisation** unlike V375, and that was checked rather than
  assumed — both writers compare case-EXACTLY, so a variant is refused at the boundary rather than
  stored; the pre-flight THROWs if a historical row ever proves otherwise.
  ⚙ **Filed:** the ethics 11 rows (owner decision); `BcmBiaAssessment#approvalStatus` and
  `RiskTreatment#status` both have a named vocabulary constant and no CHECK, so they are the readiest
  candidates after these two — blocked only on reading their writers; and the **199** remaining
  unconstrained state columns.
- ⚠⚠ **A CREATE THAT NAMES THE STATE SKIPS THE MACHINE'S *EVIDENCE REQUIREMENTS*, AND A GATE
  CANNOT SEE IT WHEN THE TARGET STATE ARRIVES AS A PARAMETER** (F2-220, measured 2026-08-16).
  F2-219 filed this shape as unmeasured; it is **nine registers, every field a `status`, 0 of 9
  refuted by reading**. `ServerStampedFieldContractTest` asks whether a create accepts a field a
  guarded route *stamps with a server value*; `transition(Long id, String to)` writes
  `cur.setStatus(to)` from a **scalar parameter**, which is caller data (F2-218's fourth detector
  correction, the one that stopped the gate failing on correct code) — so `status` never entered the
  STAMPED set and the create was not a contradiction by that question. 🔑 **The two questions are
  complements**, which is why the second lives in the same class and not a second instrument.
  ⚠⚠ **The harm is not "the record is in the wrong state", it is that each transition demands
  something ON THE WAY THROUGH and the create supplies none of it.** Probed live, all nine, each as
  a persona holding the module's WRITE: `platform.authority_documents` created **SUPERSEDED** with
  nothing superseding it (the transition resolves a successor and refuses one that is the document
  itself); a **CRITICAL** `bcm.gaps` created **CLOSED** with no corrective action; `bcm.bcp_plans`
  created **PUBLISHED**, so no `BcpVersion` snapshot — *the artefact recording what was approved* —
  and `isLocked()` then refuses to edit it; `bcm.continuity_strategies` created **APPROVED** with
  neither RTO nor RPO, **the exact case its own guard comment names**; `bcm.crisis_events` created
  **CLOSED** with no root cause and no lessons-learned record (**spec §J.13**); `platform.issues`
  created **CLOSED** with no resolution code and no timestamps; `tprm.questionnaire_templates`
  created **ACTIVE with zero questions**, then sent to vendors as an assessment. In every case
  `/transition` answered **400 or 409 for the identical edge**.
  ⚠ **No privilege escalation — same permission on both routes throughout**, so this is F2-219's
  shape and not F2-218's: the object-level machine is the *only* control, and it sat one method away
  with nothing consulting it. Say which of the two you have; they read alike in a summary.
  ⚠⚠ **Four of the nine checked no vocabulary at all, and for two the database does not either.**
  `platform.issues` has **no CHECK constraints whatsoever** and `bcm.crisis_events` none on
  `status`, so an arbitrary string was storable — and `transition()`'s `default -> false` then makes
  the row **permanently untransitionable by any route**. That is F2-203's `audit_lc` shape (19 of 46
  rows stuck), creatable on demand through the API rather than inherited from a seed.
  ⚠⚠ **ONE register needed a different primitive, and only measuring the live rows showed it.**
  `platform.authority_documents` defaults to **ACTIVE** (a library of instruments already in force)
  while its machine's root is **DRAFT** (one being prepared), and both are accepted today — so
  `requireInitial` would have **refused a working create**, the one failure F2-182's test exists to
  prevent. `ServerOwnedState.requireCreatable` takes the creatable set and **self-checks that the
  default is in it**, because taking the two as separate arguments reopens exactly the drift
  F2-217's one-constant rule closed. 🔑 It is deliberately **not** the general form: a per-register
  list of allowed states is the artefact F2-217 found measuring its own list. Every use owes a
  recorded reason.
  ⚠ **The discriminator is structural and carries NO field-name list**: a method is a transition
  route for F when it (1) loads a row, (2) **READS F on that row**, (3) throws conditionally and
  (4) writes F from a scalar parameter. (2)+(4) are what separate a machine from a setter with a
  longer name — *the value it accepts depends on the value already there*. The narrowing that stops
  it firing on correct code is that the target must **not** be a payload parameter: `update(Long id,
  Foo body, String note)` doing `body.setNote(note)` is a partial update, not a transition.
  ⚠⚠ **A NEGATIVE CONTROL THAT DOES NOT REACH THE CODE IT CONTROLS PROVES NOTHING, AND ITS PASSING
  READS EXACTLY LIKE THE NARROWING BEING SOUND.** The mutation deleting that payload narrowing
  **SURVIVED**, and the fault was mine: the control's fixture read the field on the *loaded row*
  (`e.getNote()`), so the read-the-same-field clause already excluded it and the narrowing was never
  exercised. F2-197 recorded the same shape for a mutation that failed to break its target. **Check
  which clause is doing the work before believing a control.** 20/20 after.
  ⚠ **The Java gate and the scratch Python census agreed 9/9 — the first exact agreement in this
  lineage** (F2-213: 5 vs 9; F2-214: 3 vs 11, both because the locator's resolver was narrower than
  the gate's). Keeping the scratch measurement until the shipped gate reproduces it (F2-217) is what
  makes a hard zero mean anything; the new gate was also run against the pre-fix tree and reports
  **exactly the nine**.
  ⚙ **Two things found in passing, both already-named shapes.** `BcpPlan` marks `approvedBy` and
  `approvedAt` `@JsonProperty(READ_ONLY)` and **leaves `publishedAt`** — F2-215's two-of-three,
  sibling forgotten, one slice after `TcfdItem`; the create scrubs all three so the answer does not
  depend on which were annotated. And `TEMPLATE_STATUSES` was **deleted, not left unread**: its only
  reader was the create's membership test (the check that admitted ACTIVE) and
  `transitionTemplate`'s switch is exhaustive, so keeping it would be F2-184's inert construct.
  ⚙ **Filed:** `PLATFORM:WRITE` is held by **one role with zero active users**, so the Issue
  register's write routes are reachable in dev only by `admin`; and `platform.issues` /
  `bcm.crisis_events` still have no CHECK on `status` — now cheap to add, since this slice closed
  their only open writers (F2-183's rule: the constraint and its writer land in the same commit).
- ⚠⚠ **A DEFAULT IS NOT A SETTLEMENT, AND THE GATE F2-218 BUILT TO CATCH THAT COUNTED ONE AS A
  SETTLEMENT** (F2-219, measured 2026-08-16). F2-218's headline is that
  `if (x == null) x = INITIAL` is a default, not a guard; its gate then computed the create-side
  answer as *"did anything overwrite the field before the save?"*, for which a default answers
  **yes** — so a defaulted field dropped out of the ACCEPTED set. 🔑 **F2-218's own headline, inside
  the instrument F2-218 built to enforce it**, and the positive control **asserted the behaviour as
  correct** in those words (*"crucially it does NOT read as a caller write, which is why the
  create-side check must be 'did anything overwrite it before the save'"*) — F2-159's shape, a test
  that locks in a defect and is hardest to spot because it describes the code accurately.
  Correcting it moved the population **8 → 9 services**, and **every field it added was a `status`**.
  ⚠⚠ **THE LIVE DEFECT IT HID WAS A FORGED POLICY ATTESTATION.** Probed as `ranalyst`, who holds
  `COMPLIANCE:READ` and nothing else — the permission guarding **both** routes, held by **32 roles**:
  `POST /api/policy/attestations {userUsername: "compliance", status: "ATTESTED", attestedAt:
  "2001-01-01"}` → **200 with all three stored**, while `POST /attestations/{id}/attest` on a record
  assigned to someone else → **403**. `attest()` carries **SEC-CMP-004** — *"only the assignee (or an
  admin override) may complete an attestation-of-record"* — plus a PENDING gate, the clock, the
  acting principal in `updatedBy` *"for non-repudiation"*, an ATTEST audit row and a POLICY_ATTESTED
  event. **None of it ran, and no ATTEST row was written**, so the forgery is invisible to the trail
  an auditor reads. A policy attestation is the artefact showing a **named individual** acknowledged
  a policy. ⚠ **No permission escalation here**, unlike F2-218: the same permission opens both
  routes (deliberately — attesting is a duty, like the ADR-010 intake exception), so the
  object-level check is the *only* control and the create sat two lines from it without one.
  ⚠⚠ **CHECKING THAT A VALUE IS IN THE VOCABULARY IS NOT CHECKING THAT IT IS ONE A RECORD MAY START
  IN** (F2-217's sentence about `audit_lc`, now a detector rule). `DataSharingAgreementService#create`
  defaulted to DRAFT and then threw on `!STATUSES.contains(status)` — which admits `ACTIVE`, so a
  third-party data-sharing agreement could be created live and signed, skipping `submit()` and
  `activate()` (which stamps `signed_at` from the clock). 🔑 **A detector cannot infer "settled" from
  the presence of a throw**; the DEFAULT test is scoped to the guard's own `if/else` chain, which is
  what correctly excludes `ProcessorAssessmentService#create`'s refusing `else if`.
  ⚠⚠ **FIVE DETECTOR CORRECTIONS, AND TWO OF THEM CANCELLED EACH OTHER OUT.** (1) `readsRequest`
  used `String#matches` with **no `(?s)`**, so every setter whose argument WRAPS read as mentioning
  nothing — and the house idiom `setStatus(ServerOwnedState.requireInitial(\n body.getStatus(), …))`
  wraps by width, so **the four services F2-218 closed were passing the hard zero for the wrong
  reason**. (2) fixing (1) alone reports the refusal primitive as an acceptance, i.e. **the fix
  F2-217/F2-218 introduced, reported as the defect it prevents**. (3) the DEFAULT. (4) a bare-
  identifier **local** aliasing caller data (`String j = note != null ? note : cur.getX(); e.setX(j)`)
  read as a server stamp, so a correct null-guarded update read as a contradiction — F2-197's *a rule
  keyed on a name cannot see the local alias*, in the opposite direction. (5) per-field
  `@JsonProperty(access = READ_ONLY)` was invisible. 🔑 **Two errors in opposite directions produce a
  green that neither one earns.**
  ⚠⚠ **`CLOSED_AT_THE_WIRE` NAMES `BaseEntity`'S NINE AND NO PER-FIELD ANNOTATIONS, AND A READER WHO
  FINDS THAT SUBTRACTION PRESENT DOES NOT ASK WHICH OTHER FIELDS ARE CLOSED THE SAME WAY.** F2-215's
  headline one level up, on the gate rather than on a fix. `DataSharingAgreement#signedAt` was on
  F2-218's work list as open and has carried the annotation all along; **all seven**
  `ProcessorAssessment` fields likewise, so that "fix" was **reverted** as dead code (F2-184: an
  inert construct is worse than none, because it answers the question a reader was about to ask) and
  the service is deliberately **not** listed as CLOSED — listing one that was never open makes it
  indistinguishable from one that was read and fixed. Now DERIVED from the entity source.
  ⚠ Conversely `TcfdItem` marked `approvedBy` and `approvedAt` READ_ONLY and **left
  `approvalStatus`** — two of three, sibling forgotten, F2-215 again; it was INERT only because the
  create DTO omits it and `update()`'s whitelist skips it. **Two accidents are not a mechanism**;
  closed with the annotation (F2-182).
  ⚠⚠ **THE SHAPE THIS GATE STILL CANNOT SEE, NAMED RATHER THAN PAPERED OVER: a transition whose
  TARGET STATE ARRIVES AS A PARAMETER.** `ClimateRiskOpportunityService#transition(Long id, String
  to)` enforces the linear machine and writes `cur.setStatus(to)`; a scalar parameter is caller data,
  so `status` never enters the STAMPED set and a create accepting it is not a contradiction by this
  gate's stated question — the mutation restoring that default **SURVIVED the whole build**.
  🔑 **A surviving mutant the gate legitimately cannot see is an unasserted behaviour, not a loose
  instrument** (F2-218's rule, second application): widening it would mean treating a caller-supplied
  parameter as a server stamp again. The answer is a test. **Filed — the population of
  transition-by-parameter registers is unmeasured.**
  ⚠⚠ **AN ANTI-VACUITY FLOOR HAS TO CHANGE SHAPE WHEN THE FIGURE REACHES ZERO.** The floor was
  `>= BASELINE - 3`; at `BASELINE = 0` that reads `>= -3` and can never fail. 🔑 **A floor is only
  worth having if it sits above the largest WRONG answer (F2-215) — and here the wrong answer IS
  zero**, because a rotted detector reports an empty population and that is indistinguishable from a
  finished sweep. Replaced by a reverse leg over a three-entry `BY_DESIGN` map (each read in the
  slice, enforced both ways) — which a bare count could not be.
  ⚠ **My own new test failed on the UNMUTATED tree and the harness scored it as a kill.**
  `validateAxes()` runs before the status guard in the climate create, so an incomplete fixture threw
  for the wrong reason and the assertion passed on an unrelated message. 🔑 **A test that fails on the
  unmutated tree makes every mutation routed through it read as KILLED** — the full suite found it,
  the harness could not, and the harness was re-run against a green tree afterwards.
  ⚠ **ANCHOR-MISS on the one multi-line anchor, in a harness whose own docstring says the services
  are CRLF** — second instance of F2-212 in two slices. 🔑 Naming a trap does not exempt you from it.
  ⚠ **Four pre-fix probe "failures" were mine**: TCFD answers **201** where I asserted 200; a
  caller-chosen `2001-01-01` stores as `2000-12-31 21:00` because this box is **UTC+3** and the
  server is UTC; and two payloads omitted NOT NULL columns, one of them `title_ar` (house rule 7).
  I also guessed one column (`iam.permissions.module_code`, really `module`) and two tables
  (`policy.policy_documents` → `policy.documents`, `sust.tcfd_items` → `sust.tcfd_item`) — **resolve
  from `sys.columns`/`sys.tables`, never guess**, recurring since F2-198.
  ⚠ **F2-218's register row shipped with no `**Status**` cell and `FindingsRegisterConsistencyTest`
  was red on master for a day.** That slice's final re-run named five gate classes — including the
  two it had just written — and not this one. 🔑 **A targeted re-run after a docs commit is not the
  suite, and the docs gate is precisely the gate a docs commit can break.**
  ⚙ **Filed:** the transition-by-parameter population; `TcfdItem#approvalStatus` is NULL rather than
  `PENDING` on a fresh row although the vocabulary declares PENDING; the **48** remaining
  NEEDS-VERDICT rows; the **83 entity-bound PUT handlers** (F2-22), still the structural root.
- ⚠⚠ **A CREATE THAT NAMES THE STATE IS A TRANSITION NOBODY GUARDED, AND THE COMMENT REMOVING ONE
  FIELD FROM THE WHITELIST SAT ONE LINE ABOVE THE DECISION ITSELF** (F2-218, measured 2026-08-15).
  `NdiEvidenceItemService.approve()` carries the SEC-NDI-003 maker/checker control — `NDI:APPROVE`
  at the controller, an SoD check in the service (*the evidence owner may not approve their own
  item*), the reviewer resolved from the authenticated principal, an audit transition row. Probed
  live as **`ranalyst`** (holds `NDI:WRITE`, **not** `NDI:APPROVE`): `POST /{id}/approve` → **403**,
  while `POST /api/ndi/evidence` with `status=ACTIVE`, `reviewerName="Nobody Who Reviewed This"` and
  `reviewedAt=2001-01-01` → **200 with all three stored**, and `PUT /{id} {"status":"ACTIVE"}` on a
  PENDING row **performed exactly the transition the guard had just refused** — no reviewer, no SoD,
  **no audit row** either time.
  🔑 **The update's own comment was right about ONE of the three fields the same control owns.** It
  read *"reviewerName is the attribution field for the maker/checker SoD control — it is stamped
  server-side from the authenticated principal at approve/reject, never bound from the request
  body"*, directly above `if (body.getReviewedAt() != null) …` and `if (body.getStatus() != null) …`.
  `reviewerName` is the **attribution**, `status` is the **decision**, `reviewedAt` is **when** —
  **removing the name while leaving the decision meant a WRITE-only caller could approve evidence;
  the record simply did not say who.** F2-215's headline one level down, and the **fourth** instance
  in this lineage of the correct artefact sitting beside the wrong one (F2-209's reader 200 lines
  below, F2-213's `CyberEvidenceService`, F2-214's `PrivacyBreachRequest` eight lines below).
  ⚠⚠ **`if (x == null) x = INITIAL` IS A DEFAULT, NOT A GUARD, AND THE TWO ARE INDISTINGUISHABLE TO
  ANY SCAN THAT ONLY ASKS WHETHER THE SETTER IS PRESENT.** `body.setStatus("DRAFT")` discards what
  the caller sent; `if (body.getStatus() == null) body.setStatus("DRAFT")` keeps it. That is how the
  assessment cycle could be created `SUBMITTED` or `CLOSED`. 🔑 **The discriminator is not the
  guard, it is the VALUE** — a literal, a clock reading or the principal means the server owns the
  field; an expression reading the request means the caller does.
  ⚠⚠ **The verdict column of a mass-assignment ratchet is DERIVABLE, and deriving it is what stops
  it being 55 pieces of plausible prose.** F2-217 proved a census keyed on a list of field names
  measures the list. The generalisation is not a longer list: **the authority a field carries is a
  fact about the service that owns it, and the service already states it** — in the methods that
  stamp the field after checking a state and recording who acted. `ServerStampedFieldContractTest`
  intersects, **per service AND per entity type**, the fields a service's guarded routes stamp with
  the fields its create/update paths take from the request. 4 services hard-zero; **8** on a
  decrease-only work list with the evidence printed per site into
  `target/server-stamped-fields.txt` — deliberately **not** a named map of reasons (F2-188's
  failure mode).
  ⚠⚠ **FOUR detector bugs in one slice, and the direction matters more than the count.** Three
  **shrank** the population, which reads exactly like a clean tree: a DEFAULT read as a guard
  (`[^)]*` cannot cross the `)` in `getStatus()`); an inverted null test (`!= null`) read as an
  unconditional server write, clearing 7 wide-open sites; and both halves keyed by **service**, so
  `NdiImprovementPlanService#addItem` — which persists an *item* — was charged with three fields of
  the *plan* (🔑 **a service that owns two entities has two field vocabularies**; F2-191's shape one
  level down). The fourth **grew** it onto correct code: a **scalar method parameter** read as a
  server value, so `markUnderReview(Long id, String reviewerName)` looked like a stamp. 🔑 **A
  scalar method parameter is still caller data**, and a gate that fails on correct code is the one
  failure a gate must never have (F2-214). 11 raw sites → 8, three refuted by the gate's own
  corrections.
  ⚠⚠ **A GATE MEASURING THE SERVICE LAYER CANNOT SEE A FIX MADE AT THE WIRE LAYER.** 7 of the first
  24 findings were `isDeleted`/`updatedAt`, already closed platform-wide by F2-206/F2-215's
  `ServerOwnedFieldsModule`. Subtract them **explicitly**, with the exclusion list checked against
  the introspector's own source in both directions — an exclusion nobody can audit is an assumption
  wearing a citation.
  ⚠ **Refuse, do not coerce** (F2-182's test, third clean application): the create already rejected
  anything outside the register's vocabulary, so every value that succeeds today is a declared
  state and the only requests newly refused are ones performing an unguarded transition — **a
  request that performs an unguarded transition is not a working request.** What made it safe to
  enforce was measurable: the create form sends no status at all, and the entity's own
  `@PrePersist` already defaults to `PENDING`.
  ⚠ **Server-DERIVED state must survive the fix, and it needs asserting in both directions.** Rule 6
  still flips an item to `EXPIRED` at `approve()`; a CAPA still lands `OVERDUE` from its target
  date. *"A create always lands in the initial state"* is an equally passing and wrong reading, and
  the next reader deletes the derivation. Derive the initial state where the machine already
  declares it — the cycle's is `FORWARD_FLOW.get(0)`, never a second literal (F2-196's rule).
  ⚠⚠ **A SURVIVING MUTANT THE GATE LEGITIMATELY CANNOT SEE IS AN UNASSERTED BEHAVIOUR, NOT A LOOSE
  INSTRUMENT.** Two survived the first harness run; widening the gate to catch them would have meant
  treating a caller-supplied parameter as a server stamp again — reinstating detector bug 4. The
  answer was a test (`NdiCreateInitialStateTest`), and 11/11 after. ⚠ Two **anchor misses** in the
  same run, because **the services are CRLF and the files this slice wrote are LF**, so a multi-line
  anchor with either separator misses on half the tree.
  ⚠ **Half a post-fix probe's legs belong to the guarded route still working.** Approve still
  reaches ACTIVE, the reviewer is still the principal, the audit row is still written, SoD still
  answers **409** — without those, a slice that closed the bypass by breaking the approval workflow
  scores identically. 17/17 post-fix, 12/12 pre-fix.
  ⚠ **A parameter the server discards is a claim about where the attribution comes from.**
  `approveEvidence(id, reviewerName?)` passed the literal `'admin'` from its one call site; SEC-NDI-003
  has always ignored it. Deleted rather than left as decoration (F2-184's shape on a client signature).
  ⚙ **Filed:** 8 services on the derived work list with per-site evidence — `TcfdItem` created with
  `approvalStatus`/`approvedAt`/`approvedBy`, `ProcessorAssessment#approvedAt,approverName`,
  `PolicyDocument#approverName,publishedAt`, `DataSharingAgreement#counterpartySigner,signedAt,
  signerName`, `ClimateRiskOpportunity#assessedAt,closedAt,treatedAt`, `SgiCommitment#currentValue*`,
  `NdiComplianceAssessment` (5 fields), `NdiSetting#valueText`, `SustainabilityEvidence#supersedesId`.
  Some will be **BY DESIGN** — an M&A letter-of-intent signing date genuinely is the caller's — which
  is exactly why they ship as a work list and not as verdicts.
- ⚠⚠ **A RISK RANKING BUILT FROM A LITERAL FIELD-NAME LIST FINDS EXACTLY THE SPELLINGS ITS AUTHOR HAD ALREADY
  SEEN, AND A CLOSED FIELD AT THE TOP OF THE LADDER HIDES EVERYTHING BENEATH IT** (F2-217, measured
  2026-08-15). `MassAssignmentRatchetTest` ranks each entity-bound write by matching the body type's fields
  against vocabularies. Its `AUTHORITY` list was written from the four bypasses the class was built after
  (DF-3, DF-4, F2-25, the roster link) — **from those four instances' spellings** — so it holds `approvedBy`,
  `reviewedBy`, `closedBy` while the platform's columns are also `acApprovedBy`, `reviewer`, `reviewedAt`,
  `closedAt`, `approverName` (13 endpoints), `reviewerName` (14), `publishedAt`, `ratifiedAt`,
  `integrityVerifiedAt`, `loiSignedAt`: **24 names on 64 of the 214 endpoints, nearly all ranked below CRIT
  and therefore never adjudicated.** 🔑 **A census keyed on a list of names measures the list, not the
  codebase** — 8th appearance of F2-198's shape, and the sharpest, because this instrument's javadoc says it
  exists *because* "ranking 214 endpoints by hand is how the first four were found."
  ⚠⚠ **The second half is the ladder.** It read `tenant && (authority||outcome) ? CRIT : … : tenant ? TENANT :
  state ? STATE : LOW`, so **`tenant` outranked `state`** — 145 of 214 endpoints bind `organisationId` *and* a
  state field, ranked TENANT, and the STATE tier read **4** where 149 bodies bind one. And `tenant` was
  *required* for CRIT, on the premise F2-216 had measured away one slice earlier. 🔑 **When a ranking's top
  discriminator gets CLOSED, re-read the ladder: a closed field at the top is not neutral, it masks what it
  outranks.** Corrected: authority is now a **shape** (act = verb+`By`/`At` matched as a suffix; actor = agent
  noun), `tenant` drops below `state`; tiers move **CRIT 18→79, HIGH 3→merged, TENANT 184→29, STATE 4→101**.
  ⚠⚠ **The live defect it hid, P2 and probed as `compliance`:** all three `audit_lc` creates persist the
  request entity and validated `status` only for **membership of the vocabulary**, never for being a state a
  record may *start* in. `POST /api/audit-lc/annual-plans` with `status=AC_APPROVED` +
  `acApprovedBy="Nobody Who Approved This"` + a 2001 `acApprovedAt` answered **200 and stored all three**,
  while the guarded `/transition` answered **409** for `DRAFT → AC_APPROVED`. Same shape gave an **IIA Standard
  2330 working paper** created `APPROVED` with a chosen `reviewer`/`reviewedAt`, and a CAPA created `CLOSED`
  with a backdated `closedAt` — a corrective action closed without verification. **DF-3/DF-4's defect exactly.**
  ⚠⚠ **F2-203 fixed `author` on that very method, and its comment cites `reviewer` as already covered** — true
  of `transitionWorkingPaper`, false of the create it is written on. The probe returned `author=compliance`,
  correctly stamped, **beside a `reviewer` the caller had chosen.** 🔑 **Fixing the field you noticed leaves the
  remainder looking covered, because the mechanism that covers them is visibly present** (F2-215's headline one
  level down). **Rule now:** each register declares its **initial state** once, read by *both* the default and
  the guard so the value the server picks and the value it accepts cannot drift; transition-stamped columns are
  cleared on create. ⚠ **Refuse, do not coerce** — F2-182's test (*what is already refusing?*) says the create
  already rejected anything outside the vocabulary, so the only requests newly refused are ones performing an
  unguarded transition, and **a request that performs an unguarded transition is not a working request**.
  ⚠ Server-**derived** state must survive the fix: `applyOverdue` still moves a new CAPA to `OVERDUE` from its
  target date, asserted — *"a create always lands in the initial state"* is an equally passing, wrong reading.
  ⚠⚠ **The gate's held number went UP, and that is the finding.** The pre-F2-217 CRIT set keeps its **hard
  zero**; the **55** rows the widening surfaced ride a separate decrease-only figure and are printed
  `NEEDS-VERDICT` into `target/mass-assignment-risk.txt` with the fields that put each there — a work list, not
  a count. 🔑 **F2-205's rule, not F2-204's: there a ratchet's QUESTION was wrong so it was re-scoped; here the
  question was right and the ANSWER was under-measured, so the figure moves and says so.** Do **not** give the
  55 manufactured reasons (F2-188's failure mode); read the service, record CLOSED/INERT/BY DESIGN, lower the
  figure in the same commit. ⚙ **F2-218 took the first 7** (5 CLOSED, 1 INERT, 1 BY DESIGN — the NDI
  registers), leaving **48**, and built the instrument that makes the rest adjudicable by MEASUREMENT rather
  than by hand — see the F2-218 block above.
  ⚠⚠ **My own detector UNDER-reported and only a second measurement caught it.** `AUTHORITY_SHAPE`'s first
  draft was lower-case-only, so it never matched `acApprovedBy` — **the two endpoints whose live probe
  motivated the whole slice ranked STATE and the build was green.** Found by diffing against the throwaway
  census, which had used `CASE_INSENSITIVE`. 🔑 **A detector that cannot match reads exactly like a clean tree**
  — keep the scratch measurement until the shipped gate reproduces it.
  ⚠⚠ **And the mutation harness found a hole two shape assertions had left between them**: restoring only the
  `tenant &&` half of the old ladder SURVIVED, because it **lowers** the CRIT count and a `<=` ceiling reads
  that as progress. 🔑 **Two shape assertions that each look complete can still leave the predicate between
  them free to move** — assert that binding an authority field is *sufficient* for CRIT, in its own leg.
  ⚠ **A probe that writes rows must survive its own cleanup**: `DELETE` here is a **soft** delete while the
  UNIQUE index on `(organisation_id, external_id)` still sees the row (F2-215's shape), so a fixed `externalId`
  makes run 2 fail on the index — **which reads exactly like the fix having broken**. Mint a fresh suffix.
- ⚠⚠ **A NULL `organisation_id` IS NOT "MISSING" — ON EIGHT TABLES IT MEANS *EVERY TENANT*, AND THE
  PLATFORM-WIDE BACKSTOP EXEMPTS EXACTLY THAT VALUE** (F2-216, measured 2026-08-15).
  `TenantWriteGuardInterceptor#decideInsert` passes a row whose tenant is null, on the stated ground that it is
  *"a NOT NULL violation about to happen, and the database says so far more clearly than a SecurityException
  would."* From `sys.columns`: of **427 org-bearing tables** (421 in prod) **11 declare the column NULLABLE**,
  so for those the database says nothing — and on eight of them NULL is a *value*: six repositories read
  `organisationId IS NULL OR organisationId = :orgId` (`aml.sanctions_list`, `sanctions_list_entry`,
  `fraud.fraud_typology`, `iam.roles`, `iam.sod_rules`, `opres.opres_pillar`, `platform.help_topic`,
  `platform.three_lines_role_mapping`), **169 of 177 rows in dev and 158 of 166 in prod** are stored that way,
  and running that predicate as SQL org 1 reads back **5 sanctions lists, 8 SoD rules and 69 roles it does not
  own**. 🔑 **The value the cross-tenant guard declines to judge is precisely the value that publishes a row
  to every tenant.** ⚠ The tell was visible the whole time: the **UPDATE** leg treats the same value as
  protected (`null → 5` throws, asserted since the leg was written) while the **INSERT** leg waves it through.
  **REFUTED as live** — none of the eight is bound by a write endpoint (202-row scan is the authority) and all
  four writers stamp from `TenantContext`. Throwing would be **F2-183's defect**, a constraint added while its
  writers are open; so the reason was corrected and the property that keeps it safe is now a build failure
  (`TenantGuardNullExemptionContractTest`, population **derived from the repositories**, asserted exactly).
  ⚠⚠ **The branch WAS tested, which is worse than untested, and it was NAMED FOR THE FALSE PREMISE** —
  `allowsAnInsertWithNoTenantOnTheRowAndLetsTheColumnComplain`, javadoc repeating the claim verbatim. The
  assertion was right; everything a reader judges the branch by was wrong. 🔑 **A green test whose NAME asserts
  the behaviour under review is the strongest possible reason not to look further** (F2-159's decoy, F2-175's
  rule). My own first draft claimed the branch had **no** test — `head -25` had truncated the list.
  ⚠⚠ **And two neighbouring classes said the control does not run**: `TenantContext` (*"not yet wired in"*)
  and `TenantWriteGuardConfig` (*"intentionally not connected"*, *"default OFF"*, *"activate first in
  staging"*) — all false since F2-22b, while the dev log prints `interceptor WIRED` on every boot. 🔑 **A
  stale claim about whether a security control RUNS is the input to somebody's next threat model.**
  ⚠ **A phrase-ban detector cannot tell an assertion from a DENIAL** — F2-193's `unquoted()` convention
  extends to refutation: **cite the retired wording in quotes even when you are denying it.** And
  `unquoted()` over a **whole Java file** pairs a javadoc citation's quotes with a string literal's quotes
  elsewhere; isolate the comments first. Assert a prose claim against **flowed** javadoc — a claim wraps, so a
  raw `contains` only anchors on whichever fragment you picked (a mutation gutting the consequence survived
  twice), and an embedded `\n` can never match on this **CRLF** tree (F2-212).
  ⚠ **Two of three first-round "surviving" mutants were the MUTATION's fault** (a mis-targeted anchor; a
  1-of-2-sites replacement) **and one was invalid — weakening an assertion always passes** (F2-206). Three
  more are recorded **EQUIVALENT with measured reasons** rather than counted as holes.
  ⚠⚠ **:8084 MAY NOT BE YOUR BACKEND, AND `mvn` WILL HOT-RESTART IT INTO YOUR WORKING TREE** — the recorded
  F2-74 hazard, hit here: twelve `mvn -o test` runs rewrote `target/classes`, and the listener (whose log was
  not this session's — mine stopped three hours earlier) ended up rejecting **every token it issues**
  (`WWW-Authenticate: Bearer`, curl agrees). It was **not killed**. Check whose process owns the port before
  trusting a live probe, and before assuming a 401 is your finding.
  ⚠⚠ **Correction (F2-217): identify the process by its ARGUMENTS, not by a log timestamp — that one WAS this
  session's.** `Get-CimInstance Win32_Process -Filter "ProcessId = N" | Select CommandLine` prints
  `-Djava.io.tmpdir=…/<session-id>/scratchpad`, which names the session that launched it; the log had merely
  stopped because nothing had hit the app for hours. 🔑 **A log timestamp is not process identity.** The
  practical consequence was the opposite of the one recorded above: the listener was mine to restart, and it
  **needed** restarting — `mvn -o test` rewrote `target/classes` and **DevTools did not reload the context**,
  so a post-fix probe scored 7/14 against code the running process had never loaded. 🔑 **A stale listener
  answers `/actuator/health` 200 and serves the OLD behaviour**, which is the most misleading possible result
  for a fix-verification probe: it looks exactly like the fix not working. Restart, then re-probe.
  ⚙ **Probe-harness landmine:** Python 3.14's `http.client` rejects this server's chunk terminator and raises
  `IncompleteRead` **after buffering the whole payload** — `e.partial` held all 5,305 bytes curl reported.
  Reading the body inside the `with` and letting that escape discarded a healthy 200 and reported the backend
  down for 180 s. 🔑 **A probe that cannot read a reply looks exactly like a server that never sent one.**
  ⚙ **That TENANT-tier item is now CLOSED as F2-217 — and the ranking was itself the defect (see below).** `READ_ONLY` and never `@JsonIgnore` — the whole frontend resolves records by `id`,
  and the property is **not removed** from the deserialiser either, because the FE echoes whole records back
  on update paths and under `FAIL_ON_UNKNOWN_PROPERTIES` that would turn working requests into 400s.
  ⚠⚠ **A test that mirrors the framework must mirror its CONFIGURATION, not just its modules.** The gate's
  first draft reported *"0 entities bind id"* while **most round-trips were throwing**: `new ObjectMapper()`
  defaults `FAIL_ON_UNKNOWN_PROPERTIES` to **true** and Spring Boot turns it off. The only reason that was
  caught is that the gate counted the entities it *could not* check — 🔑 **an exception during a probe is
  indistinguishable from the probe passing unless something counts it.**
  ⚠ **And a probe body that asserts more than its claim fails for reasons unrelated to the claim.** One
  combined hostile body made **34 entities unprobeable** because they map `isDeleted` as `Short` not
  `Boolean` — and the casualties were `TreatmentPlan`, `Vulnerability`, `DsrCase`, `Finding`,
  `CrossBorderTransfer`, i.e. exactly the statutory registers. **One field per question.**
  ⚠⚠ **A PROBE CANNOT CERTIFY ITS OWN SUCCESS, and the mutation harness needed three attempts to show it.**
  v1 *relaxed the assertion* and survived — **you can always delete an assertion, so mutating the CHECK
  proves nothing** (F2-192 recorded this and I did it anyway). v2 flipped a `Probed(ok, …)` success flag and
  **also** survived, because the positive count added to catch exactly that was **derived from the same
  flag**. v3: the probe has no try/catch and no flag at all — an unverifiable entity throws out of the test —
  and is killed. 🔑 **If a probe reports "I worked", something outside it must be able to disagree.**
  ⚠ **9 of 10 killed, and the survivor is an EQUIVALENT MUTANT, recorded rather than "fixed"**: Jackson
  unifies the `id` property from both the field and the accessors, so either spelling is correct. **A
  surviving mutant is a hole only if the mutated code is WRONG** — sometimes it is telling you the code has a
  branch nothing needs, which is how the accessor branch got deleted from the introspector.
  ⚠⚠ **AN INTERRUPTED MUTATION HARNESS LEAVES THE TREE MUTATED, and the mutation left behind may be the one
  that makes the gate vacuous.** The harness timed out mid-run and never restored `HOSTILE_ID`, leaving
  `{ }` — the "probe an empty body" mutation. **The corpus leg then stayed GREEN over a body that asserts
  nothing**; only the negative control noticed, and its failure (`expected 999999L but was null`) reads
  exactly like the fix having broken. Diagnosed by printing what a bare mapper actually binds instead of
  reasoning about it. **Check the harness's own anchors are intact before believing a post-harness failure.**
  ⚠⚠ **MY PROBE DESTROYED A REAL SEEDED ROW, and this is the rule that follows.** The first probe targeted
  `id: 1` — a real `Customer KYC Processing` record — to prove the same-tenant case. It worked, which is the
  finding, and it also overwrote live data without authorisation (house rule 6). Restored field-for-field
  from prod (identical seed, never in scope); 11/11 live rows in both databases. 🔑 **A probe that
  demonstrates a destructive defect must PLANT ITS OWN VICTIM first — "it is only dev data" is not the
  standard, and the row that best proves the point is always a real one.**
  ⚠ **The locator's first number was 3.6× too high, for a reason worth knowing.** It resolved
  `ResponseEntity.ok(service.create(body))` to `ResponseEntity.ok` and reported **72 candidates / 50
  defended**; restricted to calls on a declared `private final <Type> <field>` the answer is **20 / 102**.
  🔑 **A locator that resolves to the OUTER call reports every wrapped handler as unresolvable, and
  "unresolved" reads exactly like "undefended."** Of the 20, **2 were refuted by reading** — one returns a
  `Long` and never saves, one delegates to a defended `create()` one call deeper — the same ~2-in-10 rate
  F2-188/F2-192/F2-204 each measured.
  ⚠ **F2-172(d) has SIXTH and SEVENTH instances and the grep is now three-for-three.**
  `ApprovalRequestService.create` and `AuditEngagementService.create` both honoured `req.getId()` for the
  external reference — the id an approver cites to say which request they decided, and the code IIA working
  papers hang off (F2-203 already measured five disjoint engagement code spaces). **Grep
  `req.getId() != null ?` before claiming any create path mints its own reference.**
  ⚙ **Filed:** one `privacy.dpia` row holds `UNDER_REVIEW`, which `DpiaService`'s machine does not declare,
  so it **can never be approved, rejected or expired by any route** (identical in dev and prod — F2-203's
  `audit_lc` shape on a PDPL register; a synonym, so collapsing it rewrites live rows and is an owner call).
  And **34 entities map `is_deleted` as `Short`** where `BaseEntity` uses `Boolean`.
  ⚠⚠ **There are TWO live vulnerability registers and picking one is an OWNER decision.**
  `cyber.vulnerabilities` **21 rows** at `/api/v1/cyber/vulnerabilities` (rendered by `CyberPage`) and
  `cyber_lc.vulnerabilities` **36 rows** at `/api/cyber-lc/vulnerabilities` (F2-205), plus
  `cyber.vulnerability_sla_finding` 11, `cyber.vulnerability_sla_policy` 4 and `cyber.vuln_exceptions` 3. They
  are **not the same object at the same fidelity** — only `cyber_lc` carries the ECC machine, the
  server-stamped SLA deadline and the `triaged_by`/`verified_by`/`risk_accepted_by` attributions an ECC
  reviewer reads. F2-205 wired the unserved one because `CyberPage` already renders the other and retiring
  either changes what the platform reports to the National Cybersecurity Authority. The two clients are
  declared **adjacent in `api.ts` on purpose** so a reader cannot pick one without seeing the other.
- ⚠⚠ **A declared state machine is only a control if the code that changes state consults it** (F2-189,
  measured 2026-08-11). `WorkflowRequestService.TRANSITIONS` is the workflow engine's lifecycle — `transition()`
  answers **409** outside it, and the FE and `WorkflowEvidenceBridge` read it to know what is reachable. It
  declared `REOPENED → {IN_PROGRESS, SUBMITTED, ESCALATED, CLOSED}` with **no OVERDUE**, while
  `WorkflowRequestSlaScheduler` held its own `OVERDUE_SCAN_STATUSES` **containing REOPENED** and wrote
  `setStatus("OVERDUE")` directly — so a reopened past-due request would be flipped **on a schedule** to a state
  the table calls unreachable, in the engine **14 modules** adopt. Latent (0 such events in dev or prod). The
  sweep was **right** and the table incomplete: a reopened request past its due date *is* overdue, and "fixing"
  the sweep to skip REOPENED would silently stop escalating a class of overdue request. Fixed by one table edge
  + `WorkflowRequestService.mayTransition` read by both (F2-178's one-predicate shape);
  `WorkflowSlaTransitionLegalityTest` compares the scan list against the table, which is the comparison nobody
  was making. **When you add a scheduler that changes state, make it ask the table — do not copy the states.**
  ⚠ **Two measurement traps from that slice.** (a) Grouping `set*Status("X")` by **setter name** across the tree
  put `setStatus("DRAFT")` at the top with **52 writers** — 52 unrelated entity types each with a creation
  default. Sixth instance here of a grouping key that is not the thing being measured; re-scoped to *"does a
  `@Scheduled` class write a state a service owns?"* the answer is a readable **8 of 31**, and most of those
  **are** the owning service. 🔑 **A shape that produced one real defect does not imply a population.**
  (b) 🔑 **An event row whose `from_status` equals its `to_status` is not evidence of a write** — the one
  `OVERDUE → OVERDUE` row in dev is an `ESCALATED` event whose states match *because the status correctly did
  not change*, and reading it is what stopped a false claim.
  ⚠⚠ **The engine had a THIRD status writer and it also drove around the table** (F2-190).
  `WorkflowEvidenceBridge` said *"Drive through the WF-1 machine: pre-submit states hop via SUBMITTED first"*
  and wrote `setStatus` directly; its two hard-coded sets covered **11 of the table's 16** states, and of the
  five in neither, `DRAFT → APPROVED` (a request nobody ever sent, marked approved) and `RETURNED → APPROVED`
  are both forbidden. 🔑 **And the hop was decorative — `setStatus("SUBMITTED")` immediately followed by
  `setStatus(target)` collapses**, so the row never rested there and no event recorded it: *a control described
  in a comment and implemented as two consecutive writes runs as neither.* Fixed with
  `WorkflowRequestService.hopVia`, **capped at depth 2 on purpose** (`DRAFT → SENT → SUBMITTED → APPROVED`
  exists, and walking it would have an evidence approval *send* the request for the requester), the hop
  recorded as its own event, and `PRE_SUBMIT` **deleted** — an unread second opinion is how the next reader
  decides it is the authority. ⚠ Its candidate order is **declared** (SUBMITTED, then RESUBMITTED, then
  alphabetical): a hop picked by whichever state a `Set` yields first is F2-186's `HashMap`-order defect in a
  lifecycle. 🔑 **When a module has a declared state machine, ask not "is it correct?" but "how many things
  write this status, and how many of them ask?"** — F2-175, F2-188, F2-189 and F2-190 are four writers found
  from four different angles, and no earlier fix would have caught a later one.
  ⚠⚠ **Asked on purpose, that question yielded a fifth: `compliance/obligation`** (F2-191). `guardAttest` was a
  `switch` allowing **none** of `DUE`/`OVERDUE`/`IN_PROGRESS` to become `OVERDUE`, while **three** writers set it
  directly and none called the guard — and the artefact that was **right** was the class javadoc's state machine.
  The two harms were both in the trail, not the row: the sweeps passed the literal `"DUE"` as previous status
  although their query selects `DUE` *or* `IN_PROGRESS`, and the campaign path emitted **no row-level event at
  all**. 🔑 **Each duplicate agreed with the original on the part that is easy to check and differed on the audit
  trail** — the row always ends up in the right state; what differs is the event, the from-status, or nothing
  being emitted, and none of that is visible from the row. Fixed with `ATTEST_TRANSITIONS` + `mayAttest` + one
  shared `flipToOverdue` (skip-and-log, never abort a daily batch), and `AttestationOverdueGuardTest` **parses the
  javadoc** and compares it edge-for-edge with the table, so the prose is the specification.
  ⚠ **Cost of asking, stated: 13 candidate groups, 12 false positives.** A same-package proxy is not same-entity —
  `[bcm] setStatus("DRAFT")` lists nine services creating nine different entities. The scan locates; the reading
  is the measurement. ⚠ **Entity creation is not a transition**: a structural assertion banning
  `setAttestationStatus(` outright fails on the campaign's legitimate `"DUE"` at materialisation. Scope such
  assertions to the *state literal*, not the setter.
- **`LocalDate.now()` in a test is UTC+3 here; the services it tests are UTC** — so a date-relative
  fixture is wrong for three hours of every day (F2-178 correction, caught 2026-08-10 at 00:4x local).
  `CommitteeSeatLinkageTest` set `termEndsAt = LocalDate.now().minusDays(1)` while
  `CommitteeMemberRegistryService`/`CommitteeSeatAuthority` read `LocalDate.now(Clock.system(ZoneId.of("UTC")))`;
  between local midnight and 03:00 the two disagree by a day and a lapsed seat reads as current. It had
  merged **green** the day before. **Inject the clock and pin the date** (`Clock.fixed` on a `TODAY`
  constant that is deliberately *not* today — a fixed date equal to the real one proves nothing about the
  injection). ⚠ A mutation harness scoped to a slice's own tests cannot catch this: the failing class was
  not in its list.
- `seed_real_data.py` batches must start `SET NOCOUNT ON;`.
- Don't "seed" an empty table before checking it's wired (`privacy_lc` is orphaned).
- **The intake substrate is the platform front door, and until F2-172 it filed every submission
  into the submitter's own browser.** Now server-backed end to end: `useIntake()` calls
  `POST/GET/PUT /api/v1/intake`, `SEED_INTAKE`'s 5 fabricated records and the `grc.intake`
  localStorage overlay are deleted, and the client-side `generateReferenceCode()`/`slaDeadline()`
  minters are **deleted rather than moved** — only the server may issue a tracking code or a
  statutory deadline. `lib/intake.ts` no longer touches `localStorage` on **any** path; a signed-in
  failure surfaces as an error and the public portal *says* it is blocked (F2-138) rather than
  issuing a code.
  ⚠ **Four things about this module a reader will otherwise get wrong:**
  **(a) `POST /api/v1/intake` is `isAuthenticated()`, not permission-guarded**, and that is an
  ADR-010 §Sanctioned exception, pinned in `ControllerAuthorizationRatchetTest`'s `SELF_SCOPED`
  floor with `IntakeSubmitGuardTest` holding the expression. It was `INTAKE:WRITE`, measured as
  **3 of 69 roles, all administrators** — so an ordinary employee could not report a security
  event (NCA ECC 6.1), a privacy breach (PDPL Art. 27) or a workplace injury (GOSI 24 h). Do not
  "restore consistency" by converting it. READ and TRIAGE stay permissioned: filing is a duty,
  reading other people's whistleblowing reports and DSRs is a privilege.
  **(b) `submitterIsAnonymous` is a REQUEST, not an instruction.** The server honours it only for
  the kinds whose `IntakeKindRouting` entry sets `allowsAnonymous` (SPEAKUP, BREACH_REPORT,
  VRP_DISCLOSURE) — obeying it unconditionally would let any caller make any submission
  unattributable and defeat the `submitter_user_id` binding through the field that exists to
  protect whistleblowers. Where it IS honoured, **nothing identifying is stored** and
  `submitter_user_id` is NULL by design (V374 self-checks THROW if the column is made NOT NULL).
  **(c) The kind catalogue exists TWICE** — `IntakeKindRouting` (Java, in `intake/service/`) and
  `INTAKE_CATALOG` (`lib/intake.ts`) — pinned field-by-field, in both directions, by
  `IntakeCatalogueParityTest`. The server held **16** of the frontend's **26** for months while its
  javadoc claimed they were mirrored; the ten it rejected included the 24-hour GOSI injury
  notification. Add a kind to both. `titleAr` is part of the pin and is **not** decoration — a
  promoted record's mandatory `title_ar` is composed from it.
  **(d) Promotion creates the register record SERVER-side, and the client cannot name it.**
  `PUT /api/v1/intake/{id}/triage` resolves the **target from the kind's catalogue entry**, calls an
  `intake.spi.RegisterPromotionPort`, and stamps `promoted_to_record_id` **from the row it wrote**,
  in one transaction. The request DTO has no field for either — do not re-add one. (Both halves used
  to be broken at once: `lib/intake.ts` drove the lifecycle in `localStorage`, *and*
  `IntakeService.triage()` took `promotedToRecordId` from the request body and created nothing.)
  ⚠⚠ **A promoted record's status/category words are governed by a `CHECK` the Java side never mentions,
  and three of the thirteen adapters were writing values their columns had never accepted** (F2-180 +
  F2-175): `RiskPromotionAdapter` `IDENTIFIED`, `VendorPromotionAdapter` `ONBOARDING`, and
  `IncidentPromotionAdapter` passing the submitter's own category straight through — so `SECURITY_INCIDENT`
  (**0 of its form's 4 categories legal**), `WORKPLACE_INJURY` and `CONSUMER_COMPLAINT_RESPONSE` could
  never promote, and only `VRP_DISCLOSURE` — which has no category field — ever worked. All three words
  were right about what the state *means*. 🔑 **Being right about what a state means is not evidence about
  what the column stores it in.** Now pinned by `PromotionVocabularyTest`: 13 adapters → 13 target tables →
  18 CHECK vocabularies, read from the **migrations** so `mvn package` stays hermetic, with every
  constrained column either bound to a named constant or listed in `unwritten` with a reason. All 13 are
  live-probed (50/50) as of 2026-08-11.
  ⚠ **The reason it was three and not eight is one accessor**: `IntakePromotionContext#severityOrMedium()`
  clamps to the four bands all five severity-ish columns declare. **A closed column needs a closed writer** —
  add a clamp, do not trust the caller, and do not widen the CHECK to admit whatever the caller sent.
  ⚠ **Error 547 is BOTH `FOREIGN KEY` and `CHECK`**, and `GlobalExceptionHandler` reported every one as a
  reference problem — costing three investigations, including one on `risk.risks`, a table with no foreign
  keys at all. It now answers `conflictKind: VALUE_NOT_ALLOWED` and names the column (the 547 CHECK message
  carries the column but not the value, so SEC-CFG-001 holds). If you see the old wording, you are on an
  old jar. 🔑 **A wrong-but-specific diagnostic does not slow the reader down, it sends them somewhere.**
  ⚠ **The 13 adapters live in the module that OWNS the record** (`privacy/intake/`, `risk/intake/`…),
  never in `module/intake`: ADR-018 homes intake in **Base**, and twelve of the fourteen targets are
  sold SKUs. `IntakePromotionContractTest` fails the build if `module/intake` imports a peer. The
  `CHANGE` adapter is the exception and sits in `change_adapters`, because ADR-012 forbids
  `module/change_mgmt` importing a peer — that guard refused the obvious placement and was right to.
  ⚠ Promotion also requires the **target module's own write permission** on top of `INTAKE:TRIAGE`,
  and guessing it from the target name is the trap: the ethics register and the whole policy module
  are guarded by **`COMPLIANCE:WRITE`**, not `ETHICS:WRITE` — and `ETHICS` *does* exist in the
  catalogue, so the obvious guess compiles and 403s.
  ⚠ **Six `CONTROL_TEST` kinds have no register and now 409 by design** (F2-176). A promotion that
  cannot create the record must refuse, not flip the status and write nothing.
  ⚠ **There is exactly ONE writer to `intake.intake_records`, and that is now enforced** (F2-175).
  `MlModelDriftService` used to build the entity itself and call `intakeRepo.save()`, so none of the above
  governed the CRITICAL-drift path: hard-coded assignee, its own `ISS-…` code minter, its own
  `plusDays(14)`, no history events, and hand-concatenated JSON whose `esc()` covered only
  `\`/`"`/`\n`/`\r` — **so a model title containing a TAB produced a record that could never be promoted**,
  failing as *"unreadable payload"* from `intake` while the fault sat in `ai/governance`.
  **A peer module files a submission through `intake.spi.SystemIntakePort`** (the inbound counterpart to
  `RegisterPromotionPort`), which takes a `Map` payload the mapper serialises — so promotability is
  structural, not lucky. `IntakeSingleWriterInvariantTest` pins three hard zeros/ones: `new IntakeRecord(`
  appears **once** in main sources, `IntakeRecordRepository` has **one** user, and **no peer imports
  anything of `module/intake` but its `spi`**. ⚠ Invariants, not ratchets — the population is one, so a
  raisable count would make a tolerated writer look reviewed.
  🔑 Three reusable lessons from it: **(a)** the register's own filing called the bypass *"currently
  benign"* when the supportable claim was *"unexercised"* — 26 seeded drift rows, `triggered_issue_id`
  NULL on all of them, so the path had **never run**; **(b)** two of the filing's three architectural
  claims were false (`ai → intake` is SKU → Base, the *legal* direction, and `ModuleGraphRatchetTest`'s
  `KERNEL_ISH` is only `{platform, integrations, dashboard}` so it could not fire) — **read the guard
  before believing a finding's claim about it**; **(c)** the existing test asserted the defect as
  correct (`startsWith("ISS-")`, `isEqualTo("SUBMITTED")`), the second instance after F2-159 —
  **a test written by reading the implementation can only agree with it.**
  ⚠⚠ And the honest half: the catalogued assignee `RISK_OFFICER` **did not exist in `iam.roles`** while
  the hard-coded `RISK_MANAGER` did. It was still the right change — one catalogue, consulted — but the
  value it consulted was wrong: **10 of 14 catalogue codes matched no role**, carrying **22 of 26 kinds**.
  🔑 **The hard-code F2-175 deleted was right about the name and wrong about the mechanism.**
  ⚠⚠ **`defaultAssignee` is a ROLE NAME from `iam.roles`, not a free-text stakeholder label** (F2-174,
  closed 2026-08-11). All 14 codes had a real counterpart already seated, so the ten that named nothing
  (`CISO_OFFICE`, `ETHICS_CTE`, `CSIRT`, `RISK_OFFICER`, `COMPLIANCE_LEAD`, `POLICY_OFFICER`, `TPRM_LEAD`,
  `BCM_LEAD`, `AUDIT_LEAD`, `CAB`) were renamed, and `IntakeAssigneeRoleResolutionTest` holds a **hard
  zero** against the one pinned catalogue (`RoleGuardConversionRatchetTest.ROLE_CATALOGUE` — its **third
  reader, not a third copy**). Do **not** read it as "the assignee now works": only **4 of 11** names have
  an active holder in dev or prod, nothing resolves the column (it renders as a label in the ack text and
  the audit note), and `assigned_to` **collates differently** from `iam.roles.role_name`
  (`Latin1_General_100_CI_AS_SC_UTF8` vs `SQL_Latin1_General_CP1_CI_AS`) — so a resolver's bare join raises
  *"Cannot resolve the collation conflict"* 468/8180 and must `COLLATE` explicitly.
  ⚠ **Both fixes the filing proposed were non-viable, and only measurement showed it**: failing closed at
  runtime would refuse the 24-hour GOSI injury notification over a label (contradicting the ADR-010
  exception above), `0 of 14` codes matched any committee so the seat machinery was not reusable, and
  `workflow.recipient_list_members` is **empty in both databases**. 🔑 **A filing's candidate fixes are
  the least-verified thing in it after its recommendations.** ⚠ And its urgency was false:
  `intake.intake_records` held **0 rows in dev and prod**, so the field had never once been written.
