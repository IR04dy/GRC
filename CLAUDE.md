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
  ⚠⚠ **STRIP LINE COMMENTS AND BLOCK COMMENTS IN ONE LEFT-TO-RIGHT ALTERNATION — the order is load-bearing and
  getting it wrong silently shrinks your population.** Written the obvious way (`s/\/\*[\s\S]*?\*\///` first,
  then `//`), the census **deleted 40,247 of `App.tsx`'s 89,522 characters — 45% of the file** — because line 122
  is a `//` prose comment whose text contains a literal `/*`, so the block pattern ran from there to the next
  closing delimiter and took the `lazy()` declarations for the very pages being measured. It reported **24**
  server-less pages instead of **74** and **0** sidebar instances instead of **8**, silently excluding all four
  fabricated pages already known by name. 🔑 **A corrupted source yields a SMALLER population, which reads
  exactly like "those pages are fine"** — the quietest way for a scan to be wrong, and the 7th instance here of
  a detector satisfied by something other than its claim. Use `/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g`: at a `//`
  an inner `/*` can never open a block, and at a `/*` a URL cannot truncate it. Pin **both** directions. ⚠⚠ **This was a REPEAT: `Appendix/scripts/baseline_census.py` lines 139–142 already carry the correct alternation and a comment saying F2-68(c) found this same bug in the frontend scanner.** I wrote a second scanner over the same tree without reading the first. 🔑 **Before writing a scanner, read the scanner this repo already has.**
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
  ⚠ **Fixed with ONE Jackson introspector, not 258 annotations** — `common/json/ServerOwnedIdentityModule`
  reports `READ_ONLY` for the `id` of any `@Entity`/`@MappedSuperclass`. **An introspector has no population
  to get wrong**: it is consulted for every property of every type Jackson binds, so a new entity is covered
  the day it is written. `READ_ONLY` and never `@JsonIgnore` — the whole frontend resolves records by `id`,
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
