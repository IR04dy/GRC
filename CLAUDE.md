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

mvn -o clean package        # full build + all UNIT tests (3,354; ~1 min hot, 3–5 min cold)
                            # hermetic: needs no database and no secrets — keep it that way

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
— **V1→V373 packaged; next free V374** (361 files; ⚠ this line said "V1→V295 / next free
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
Flyway (baseline 292 on the deployed server; head **V344**, so **V345–V373 are pending** the
next jar — 29 files). `flyway.clean` is disabled — never run `mvn flyway:clean`.

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
   `BASELINE_ENTITY_BOUND_WRITES` 214, `BASELINE_PURE_ROLE_SITES` 430 — read the
   constant, never a prose summary of it. ⚠ **`BARE_EXPORT_CEILING` 32 is GONE** — F2-61(c)
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
| DB | SQL Server 2019+, UTF-8 collation, 50+ schemas, Flyway V1–V373 (OFF in dev) |
| ORM | Hibernate 6 / Spring Data JPA, `ddl-auto=none` |
| Exports | Apache POI (XLSX/DOCX/PPTX) + OpenPDF (RTL Arabic, bundled Amiri font) |
| Frontend | React 18 + Vite + TypeScript (`tss-grc-ui-v2`), Recharts, STOMP/SockJS |

Backend modules under `module/` — **59 packages** (58 with code; `datagov` is an empty
placeholder), 204 kLoC, 432 entities, 2,212 endpoints. Each follows
`controller / dto / entity / repository / service`.

⚠ **Do not edit that count by hand — it is enforced.** `module-registry.yaml`
(`tss-grc-backend/src/main/resources/`) enumerates all 59 exactly once, and
`ModuleRegistryContractTest` fails the build if the registry, the directory listing,
`SkuRegistry`'s ADR-018 homing or *this sentence* disagree. Adding a package means an entry
there, a homing in `SkuRegistry`, and a workspace decision — or the build refuses it.

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
  and validates nothing — **430 endpoints still authorise by role name** (F2-05/F2-52).
  Measured 2026-08-07: **43 distinct names recited, 5 absent from `iam.roles`** (67→**69**
  active after V373). ⚠ This line said "~50% miss rate" and that is now 12% — but do **not**
  read the improvement as safety: 3 of the 5 survivors are on 27 sites, and a name that is
  absent makes the guard *tighter* than it reads, which never looks like a bug from the
  outside. Before trusting or writing one, check the names exist.
  ⚠⚠ **There are TWO role-name surfaces and only one used to be measured.**
  `attachment/AttachmentAccessPolicy` mirrors each parent record's read guard so an attachment
  is as confidential as its parent — where the parent authorises by role, it holds a **Java role
  list no `@PreAuthorize` scan sees**. F2-52 found two entries still listing roles after their
  parents had been converted to `hasPermission`: 13 roles held `TPRM:READ` and were unnamed, so
  four dev personas could open a vendor BCP campaign and 403 on its files. The class's own
  comment already said *"It has to move WITH the parent, not later"* — an instruction with no
  enforcement, ignored by two converting slices. **`RoleNameSurfaceRatchetTest` enforces it now:
  change a guard's roles and change that registry in the same commit.**
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
  ⚠ Adding a resolver? **Unit-test the resolver itself**, not just the registry — mutation proved a
  fake resolver hides a missing tenant filter and an ownership check reduced to mere existence.
  ⚠ And do **not** bind an id just because the route has one: `transition`/`decommission`/audit-record/
  drift-verdict are judgements *about* the record and the owner is their subject (V367's defect).
  `MlModelOwnershipGuardTest` pins that boundary in both directions.
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
- `seed_real_data.py` batches must start `SET NOCOUNT ON;`.
- Don't "seed" an empty table before checking it's wired (`privacy_lc` is orphaned).
