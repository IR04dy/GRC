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

mvn -o clean package        # full build + all UNIT tests (3,602 @ 2026-08-11; ~1 min hot, 3–5 cold)
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
— **V1→V374 packaged; next free V375** (362 files; ⚠ this line said "V1→V295 / next free
V296" for weeks, 54 behind — reusing a taken version number is a Flyway checksum failure on
the deployed server, so **read the directory, not this line**: `ls .../sqlserver | tail -3`.
`Appendix/scripts/baseline_census.py` prints it and `MeasuredBaselineFreshnessTest` gates it).
⚠ **A grant migration must DECLARE its (role, module, action) triples as literals.** The house idiom
`INSERT … SELECT … JOIN iam.roles JOIN iam.permissions WHERE NOT EXISTS` grants **nothing** when
either side is catalogued later in the chain, and `NOT EXISTS` cannot tell *already granted* from
*nothing matched* — so it succeeds silently. Measured 2026-08-07: a fresh install was **225 grants**
short of dev (`ORG_ADMIN` −30, unable to read seven modules), and re-running the same folder adds
**875**. Never derive the pair from `iam.role_permissions` itself — V284's "READ-parity" clause is a
*snapshot*, not a rule; `RoleGrantDerivationRatchetTest` pins the 6 legacy sites and fails a 7th.
Convention for new migrations: write them **idempotent** with self-checks that `THROW`,
apply to the dev DB **twice via pyodbc** (Flyway off in dev), and let prod pick them up via
Flyway (baseline 292 on the deployed server; head **V344**, so **V345–V374 are pending** the
next jar — 30 files). `flyway.clean` is disabled — never run `mvn flyway:clean`.

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
| DB | SQL Server 2019+, UTF-8 collation, 50+ schemas, Flyway V1–V374 (OFF in dev) |
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
  ⚠⚠ **And the mirror of that landmine: a CHECK constraint the Java side never mentions is still enforced.**
  F2-180 found **19 of the 22** incident categories the platform's own screen offers being refused by
  `CK_incidents_category`, and three promotion adapters writing status/category words their columns had
  never accepted. **Before writing a vocabulary literal, read the column's CHECK** — from
  `sys.check_constraints` or the migration, scoped to *that* table. ⚠ **A cross-check not scoped to the
  target table is worse than none**: the first sweep compared literals against every CHECK in the database
  and cleared the one adapter already known to be broken, because its value is legal on some other table.
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
  ⚠⚠ And the honest half: the catalogued assignee `RISK_OFFICER` **does not exist in `iam.roles`** while
  the hard-coded `RISK_MANAGER` does. It is still the right change — `assigned_to` is a stakeholder-code
  column, **10 of 14 catalogue codes are not role names**, and **nothing resolves the column at all** (it
  renders as a label) — but do not read it as "the assignee now works." That is F2-174.
