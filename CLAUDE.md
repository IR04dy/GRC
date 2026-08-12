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
— **V1→V375 packaged; next free V376** (363 files; ⚠ this line said "V1→V295 / next free
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
Flyway (baseline 292 on the deployed server; head **V344**, so **V345–V375 are pending** the
next jar — 31 files). `flyway.clean` is disabled — never run `mvn flyway:clean`.

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
| DB | SQL Server 2019+, UTF-8 collation, 50+ schemas, Flyway V1–V375 (OFF in dev) |
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
