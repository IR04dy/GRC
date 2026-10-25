# TSS GRC — Windows Server Deployment Runbook

> ## ⚠ READ THIS BEFORE FOLLOWING ANY VERSION NUMBER BELOW
>
> This runbook documents the **2026-07-11 go-live**, whose backup carried the schema up to
> **V292**. Every `292` in this file is that deployment's figure, not a constant.
>
> 🔑 **`FLYWAY_BASELINE_VERSION` is not a fixed value — it must equal the newest migration
> already present in the backup you are restoring.** Set it lower and Flyway replays every
> migration above it against objects that already exist, and the startup dies part-way with
> the schema half-modified. The tree is now at **V398**, so a backup taken today needs
> `FLYWAY_BASELINE_VERSION=398`.
>
> **For a NEW server, use the self-contained bundle instead of this file.** It ships the jar,
> the UI, the uploads, a fresh verified backup, service definitions, a verification script and
> its own guide — and the restore-plus-first-boot was rehearsed on the build machine before
> hand-over:
>
> ```
> D:\TSS-GRC-Deploy-2026-09-03\SERVER_DEPLOYMENT_GUIDE.md
> ```
>
> This file remains the reference for the **existing** deployment at `D:\Deployment\tss-grc`,
> which is genuinely baselined at V292 (Flyway history V292→V344, so V345–V398 apply on its
> next restart).

Deploys the platform to a Windows Server cloud machine, reachable by **IP over
HTTP** (TLS can be added at the reverse proxy later). Database arrives as a
**restore of the dev `TSS_GRC` database** (all schema + demo tenants), with
Flyway baselined at the head already present in that backup (**V292** for the
July 2026 backup; **V398** for one taken today) so only future migrations
auto-apply.

**Topology**

```
Browser ──► nginx :80          (serves the React UI + proxies /api and /ws)
              │
              ├──► Spring Boot :8084   (prod profile, HTTP, bound to 127.0.0.1)
              │        └──► SQL Server :1433 (local, TSS_GRC restored from .bak)
              └──  Actuator :8090      (localhost only — never exposed)
```

> ### ⚠ DEP-6 — there are TWO port topologies, and this file documents the one you are
> ### probably *not* standing on
>
> The diagram above is the **bundle convention**: `deploy/external.properties` ships
> `SERVER_PORT=8084` / `SERVER_SSL_ENABLED=false`, and `application-prod.yml` defaults
> `MANAGEMENT_SERVER_PORT` to `8090`. Every port in §4 below follows it.
>
> **The existing deployment does not.** `D:\Deployment\tss-grc\app\external.properties`
> carries `SERVER_PORT=8090` and `MANAGEMENT_SERVER_PORT=8091` (read 2026-09-14; the file
> is dated 2026-08-02), which is also what `CLAUDE.md` states. So on the live box, **:8090 is
> the application, not actuator** — an operator who runs §4's health check there gets a 404
> from the API and concludes the app is down.
>
> | | bundle / fresh install | existing box (`D:\Deployment\tss-grc`) |
> |---|---|---|
> | application | `:8084` | **`:8090`** |
> | actuator / management | `:8090` | **`:8091`** |
> | nginx | `:80` | `:80` |
>
> The packaged **defaults** are neither: `application-prod.yml` reads `${SERVER_PORT:8443}`
> with `${SERVER_SSL_ENABLED:true}`, so an `external.properties` that omits both starts on
> HTTPS `:8443` and fails fast on the empty keystore. Both property files set them; do not
> rely on the default.

---

## 0. What you carry to the server

Everything is staged in `deploy/` on the build machine:

| File | What it is |
|---|---|
| `tss-grc-platform-0.1.0-SNAPSHOT.jar` | Backend (merged master: green-finance G0–G4 + RBAC governance P0–P3) |
| `external.properties` | **ALL runtime configuration** — lives next to the jar, edit + restart to change anything, no rebuild (loaded via `spring.config.import`) |
| `ui-dist/` (or `ui-dist.zip`) | Production React build (`tss-grc-ui-v2/dist`) |
| `TSS_GRC_2026-07-11.bak` | Database backup (SQL Server 2019, `RESTORE VERIFYONLY` passed) |
| `run-backend.cmd` | Thin service launcher — just JAVA_HOME + `java -jar` (no env vars needed) |
| `nginx.conf` | Ready reverse-proxy config |
| `DEPLOYMENT.md` | This file |

Copy the folder to the server (RDP drive mapping, or `robocopy \\SERVER\c$\tss-grc-stage deploy /E`).

---

## 1. Server prerequisites (once)

Run in an **elevated** PowerShell:

```powershell
# Java 17 (Temurin) — the app is pinned to 17
winget install EclipseAdoptium.Temurin.17.JDK
```

**Service wrapper — WinSW** (no installer, no PATH changes): download
`WinSW-x64.exe` (or the `WinSW.NET4.exe` build) once from
https://github.com/winsw/winsw/releases and copy it next to each thing you
want to run as a service. One copy of the exe per service, renamed to the
service's name, with an XML of the same base name beside it (§4).
`winget install NSSM.NSSM` works too if winget is available, but WinSW is
what this runbook uses.

**SQL Server 2019 or newer** (Express is fine to start; the .bak is version
15.0 and cannot restore to anything older):

1. Download *SQL Server 2019 Express* → installer → **Basic**.
2. Open *SQL Server Configuration Manager* → *SQL Server Network Configuration*
   → *Protocols for SQLEXPRESS* → **Enable TCP/IP** → restart the
   `SQL Server (SQLEXPRESS)` service.
3. Mixed authentication: in `sqlcmd` (or SSMS) as the Windows admin:

```powershell
sqlcmd -S localhost\SQLEXPRESS -E -Q "EXEC xp_instance_regwrite N'HKEY_LOCAL_MACHINE', N'Software\Microsoft\MSSQLServer\MSSQLServer', N'LoginMode', REG_DWORD, 2"
Restart-Service 'MSSQL$SQLEXPRESS'
```

**nginx for Windows**: download the zip from https://nginx.org/en/download.html
(stable), extract to `C:\tss-grc\nginx` (so `C:\tss-grc\nginx\nginx.exe` exists).

**Directory layout**:

```powershell
New-Item -ItemType Directory -Force C:\tss-grc\app, C:\tss-grc\ui, C:\tss-grc\logs, C:\tss-grc\data\uploads, C:\tss-grc\backup
```

Place the artifacts:
- jar + `external.properties` + `run-backend.cmd` → `C:\tss-grc\app\`
  (the jar reads `external.properties` from its working directory — they must sit together)
- contents of `ui-dist/` (index.html at the top) → `C:\tss-grc\ui\`
- `.bak` → `C:\tss-grc\backup\`
- `nginx.conf` → `C:\tss-grc\nginx\conf\nginx.conf` (overwrite the default)

---

## 2. Restore the database

```powershell
sqlcmd -S localhost\SQLEXPRESS -E -Q "RESTORE DATABASE TSS_GRC FROM DISK = N'C:\tss-grc\backup\TSS_GRC_2026-07-11.bak' WITH MOVE 'TSS_GRC' TO N'C:\Program Files\Microsoft SQL Server\MSSQL15.SQLEXPRESS\MSSQL\DATA\TSS_GRC.mdf', MOVE 'TSS_GRC_log' TO N'C:\Program Files\Microsoft SQL Server\MSSQL15.SQLEXPRESS\MSSQL\DATA\TSS_GRC_log.ldf', STATS = 10"
```

(The logical file names are exactly `TSS_GRC` and `TSS_GRC_log`. Adjust the
`MSSQL15.SQLEXPRESS` path if your instance folder differs.)

Create a dedicated app login (do **not** run the app as `sa`) — pick a strong
password and put the same value in `run-backend.cmd` later:

```powershell
sqlcmd -S localhost\SQLEXPRESS -E -Q "CREATE LOGIN grc_app WITH PASSWORD = N'<STRONG-DB-PASSWORD>', CHECK_POLICY = ON; USE TSS_GRC; CREATE USER grc_app FOR LOGIN grc_app; ALTER ROLE db_owner ADD MEMBER grc_app;"
```

(`db_owner` because Flyway applies future DDL migrations at startup.)

Sanity check:

```powershell
sqlcmd -S localhost\SQLEXPRESS -U grc_app -P "<STRONG-DB-PASSWORD>" -d TSS_GRC -Q "SELECT COUNT(*) AS users FROM iam.users; SELECT COUNT(*) AS perms FROM iam.permissions"
# expect: users >= 7, perms = the catalogue size of the backup you restored
# (94 in the July 2026 backup; 144 in the dev database as of 2026-09-03, 114 on the
#  deployed server, which is behind by the pending V345-V398 range. This line said a
#  flat "perms = 94" and that stopped being true the moment the catalogue grew.)
```

---

## 3. Configure `external.properties` (the ONLY config file)

All runtime configuration lives in `C:\tss-grc\app\external.properties`, next
to the jar. The app imports it at startup (`spring.config.import` in the
packaged `application.yml`) — **edit the file, restart the service, done; no
environment variables, no rebuild.** The shipped file is pre-filled for this
topology; you only have to replace two values:

> ⚠ **It overrides the `UPPER_CASE` names, not arbitrary Spring properties (F2-149).**
> The names in the template feed `${PLACEHOLDER}` references inside the packaged
> `application*.yml`, and a placeholder resolves from any property source, so those always
> win. A **raw Spring property path** — `grc.something.enabled=false` — is *silently
> outranked* whenever `application-prod.yml` sets the same path: this file is imported *from*
> `application.yml`, and a profile-specific document beats it. There is no error and no
> warning.
>
> This is not hypothetical. On **2026-08-02** an operator wrote
> `grc.security.tenant-write-guard.enabled=false` here to mitigate a P0 and it did nothing at
> all — production kept booting with the guard on, logging `[TenantWriteGuard] interceptor
> WIRED`, and the incident record said the flag was off for four days. **If a setting has no
> `UPPER_CASE` name in the template, it cannot be changed from here**; add the placeholder to
> the yml first (`ExternalConfigOverrideContractTest` enforces that the two stay in step).

1. `JWT_SECRET` — generate one:

```powershell
-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
```

2. `DB_PASSWORD` — the `grc_app` password you created in §2.

Everything else is already set for the nginx topology and can be changed
anytime later: ports/SSL (`SERVER_PORT=8084`, `SERVER_SSL_ENABLED=false`,
loopback binds), Flyway baseline (`FLYWAY_BASELINE_ON_MIGRATE=true`,
`FLYWAY_BASELINE_VERSION=292` — the restored DB already contains V1–V292),
Windows log/upload paths, SMTP relay, and commented blocks for in-app TLS,
health probes, and the optional integrations (Sentry/ServiceNow/Splunk/
CrowdStrike/Tenable).

Two kinds of entries work in the file:
- the `UPPER_CASE` names — they feed the `${PLACEHOLDER}` references inside
  the packaged config (these always win, use them when available);
- raw Spring keys (e.g. `grc.cors.allowed-origins=...`) — full override of
  anything not pinned by the packaged prod profile.

`run-backend.cmd` is now just a thin launcher (`JAVA_HOME` + `java -jar`) —
you normally never edit it.

Lock the config down (it contains secrets):

```powershell
icacls C:\tss-grc\app\external.properties /inheritance:r /grant:r "Administrators:F" "SYSTEM:F"
```

---

## 4. Install the services (WinSW)

WinSW convention: `<name>.exe` (a renamed copy of the WinSW binary) +
`<name>.xml` (the service definition) side by side; then `<name>.exe install`.

**Backend** — copy WinSW to `C:\tss-grc\app\backend-service.exe` and create
`C:\tss-grc\app\backend-service.xml`:

```xml
<service>
  <id>TssGrcBackend</id>
  <name>TSS GRC Backend</name>
  <description>Spring Boot REST API for the TSS GRC platform.</description>
  <executable>java</executable>
  <arguments>-jar C:\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod</arguments>
  <workingdirectory>C:\tss-grc\app</workingdirectory>
  <startmode>Automatic</startmode>
  <logpath>C:\tss-grc\logs</logpath>
  <!-- DEP-4 — this was `<log mode="roll"></log>`. `roll` is ALREADY a size-based rolling
       appender; what it is not is one whose cap you can read. Its thresholds are WinSW's own
       built-in defaults, so the cap this service runs under is whatever the WinSW build you
       copied in decided — which is a fact about the binary, not about this bundle.
       `roll-by-size` is the mode that parses the two children below; under plain `roll` they
       are IGNORED, so writing them there would ship a knob that reads as a setting and is not
       one. sizeThreshold is in KB: 10 MB per file, 8 kept, so this capture is bounded at
       ~80 MB. Stated here, it survives a WinSW upgrade changing its defaults. -->
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>8</keepFiles>
  </log>
  <onfailure action="restart" delay="10 sec"/>
</service>
```

(`workingdirectory` matters: `external.properties` is resolved relative to it.)

⚠ This `logpath` is the same directory the application's own rolling JSON file lands in once
`LOGGING_FILE_NAME` is set (see §5) — WinSW's capture of the service's stdout and the appender's
own file are two different files, and only the second is the one the Flyway check greps.

**nginx** — copy WinSW to `C:\tss-grc\nginx\nginx-service.exe` and create
`C:\tss-grc\nginx\nginx-service.xml`. nginx needs a graceful stop
(`-s stop`), not a process kill — hence the stop executable:

```xml
<service>
  <id>TssGrcNginx</id>
  <name>TSS GRC UI (nginx)</name>
  <description>Serves the TSS GRC web UI and proxies /api and /ws to the backend.</description>
  <executable>C:\tss-grc\nginx\nginx.exe</executable>
  <startarguments>-p C:\tss-grc\nginx</startarguments>
  <stopexecutable>C:\tss-grc\nginx\nginx.exe</stopexecutable>
  <stoparguments>-p C:\tss-grc\nginx -s stop</stoparguments>
  <startmode>Automatic</startmode>
  <stoptimeout>15 sec</stoptimeout>
  <logpath>C:\tss-grc\nginx\logs</logpath>
  <!-- DEP-4 — same change as the backend service above, and for the same reason: `roll` caps
       by size using WinSW's built-in defaults, `roll-by-size` is the mode that reads these two
       children. sizeThreshold is in KB. -->
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>8</keepFiles>
  </log>
  <onfailure action="restart" delay="10 sec"/>
</service>
```

Install and start both:

```powershell
C:\tss-grc\app\backend-service.exe install
C:\tss-grc\app\backend-service.exe start

# always test the config before first start:
C:\tss-grc\nginx\nginx.exe -t -p C:\tss-grc\nginx
C:\tss-grc\nginx\nginx-service.exe install
C:\tss-grc\nginx\nginx-service.exe start
```

Before starting the nginx service, make sure no manually-started nginx is
still running (`C:\tss-grc\nginx\nginx.exe -s stop -p C:\tss-grc\nginx`,
ignore errors) and that nothing else owns port 80 — on Windows Server, IIS
often does (`Stop-Service W3SVC; Set-Service W3SVC -StartupType Manual`).

First-boot verification (backend takes ~60–90 s).

⚠ **Ports below are the bundle convention** (app `:8084`, actuator `:8090`) — see the DEP-6 box
under *Topology*. On the existing `D:\Deployment\tss-grc` box the pair is app `:8090`, actuator
`:8091`, so substitute before running any of these there.

```powershell
# wait for UP   — actuator port: :8090 fresh install / :8091 existing box
curl.exe -s http://127.0.0.1:8090/actuator/health          # {"status":"UP"}

# Flyway must say it BASELINED, not migrated — expect this exact line on
# first boot: "Successfully baselined schema with version: 292"
# (and on every later boot: "Schema [platform] is up to date. No migration necessary.")
#
# ⚠ DEP-4 — this file holds JSON, one object per line, whatever the extension says: the prod
# logback appender uses the Logstash encoder, and `.log` is simply what LOGGING_FILE_NAME is
# set to. The grep below still matches, because the Flyway message sits inside the "message"
# field of each object.
#
# ⚠ The path is honoured as of DEP-4 and was not before it. The appender hard-coded a Linux
# absolute path, so on Windows it resolved DRIVE-RELATIVE (<drive>:\var\log\tss-grc\) and
# succeeded silently — this file did not exist, and the only logs in C:\tss-grc\logs were
# WinSW's capture of the service's stdout. If this Select-String finds nothing, check that
# LOGGING_FILE_NAME is set in external.properties before concluding Flyway did not run.
Select-String -Path C:\tss-grc\logs\application.log -Pattern "baselined|No migration necessary"

# API login smoke test   — application port: :8084 fresh install / :8090 existing box
curl.exe -s -X POST http://127.0.0.1:8084/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"Admin@12345\"}"
# expect a JSON body containing accessToken
```

**Then confirm the box is running the jar you think it is (W0-7).** This step exists because the
alternative has already happened: master carried a fix for a defect failing 33-35x/day in production
while production served a jar from six days earlier, and nothing anyone could query would have said
so. `/actuator/info` now answers with the build (version, timestamp, commit when the release script
passes `-Dgit.commit=$(git rev-parse HEAD)`), the runtime JDK, and the migration state — the head
packaged in *this artifact*, the head applied to *this database*, and the list in between.

```powershell
# From a checkout of the commit you deployed. Exit 0 = match, 1 = mismatch, 2 = unreachable.
# ⚠ --url takes the ACTUATOR port: :8090 on a fresh install, :8091 on the existing box. This
# line read :8091 while every other port in this section read the fresh-install pair — it was
# the one line that had been updated for the live box, and it is why DEP-6 read as three
# mutually inconsistent numbers rather than as two coherent topologies.
py TSS-GRC-Platform-Bible\Appendix\scripts\deployment_identity.py --url http://127.0.0.1:8090 --expect-commit (git rev-parse HEAD)
```

A non-empty `pending` list is the deployment gap stated as fact: those migrations apply on the next
start and have not applied yet. `build.commit = unknown` means the jar was built without the flag and
**cannot** be identified — treat that as a failed check, not a pass.


---

## 5. Firewall — expose only port 80

```powershell
New-NetFirewallRule -DisplayName "TSS GRC HTTP" -Direction Inbound -Protocol TCP -LocalPort 80 -Action Allow
```

Do **not** add rules for 8084 / 8090 / 1433 — the backend and actuator are
bound to 127.0.0.1 and SQL Server should stay unreachable from outside. (On a
cloud VM also open port 80 in the provider's security group / NSG.)

---

## 6. Smoke test from your browser

1. `http://<SERVER_IP>/` → login page loads (UI is served by nginx).
2. Log in `admin / Admin@12345` → dashboard renders, no console errors.
3. Deep-link refresh test: open any module page and press F5 — nginx serves
   `index.html` via the SPA fallback.
4. Live panels use WebSocket: in devtools → Network → WS, `/ws` should show
   **101 Switching Protocols**.

---

## 7. Immediately after go-live (do not skip)

1. **Rotate every seeded password** — this is a copy of the dev database and
   the passwords are known: `admin`, `auditor`, `compliance`, `ranalyst`,
   `rmanager`, `viewer`, `so_officer` (all `Admin@12345` / `Risk@12345` /
   `Viewer@12345` variants). Log in as each (or use the admin console) and
   change them. Prod lockout is 5 attempts / 30 min.
2. **Clean demo tenants if not wanted**: orgs 2 (Aramco Trading) and
   3 (STC Bank demo) carry demo data.
3. **Schedule nightly DB backups** (Express has no SQL Agent — use Task
   Scheduler):

```powershell
schtasks /Create /TN "TSS GRC DB backup" /SC DAILY /ST 02:00 /RU SYSTEM /TR "sqlcmd -S localhost\SQLEXPRESS -E -Q \"BACKUP DATABASE TSS_GRC TO DISK = N'C:\tss-grc\backup\TSS_GRC_nightly.bak' WITH INIT, CHECKSUM\""
```

4. **Constitute the CAB before anyone raises a change** — this one *will* look
   like a bug if you skip it. Change authorisation **fails closed** by design
   (ADR-017 D-2): a `CAB`/`ECAB` gate whose committee is unmapped, or mapped to
   a committee with no active voting members, **refuses every approval** with a
   409 rather than approving without an authority. ⚠ **This is a per-database
   fact and the two differ.** On the *deployment* database the `CAB` gate points
   at committee 13 and `ECAB` at committee 12 and **both hold zero member
   terms**; on the *dev* database (the source of the 2026-09-03 bundle) the same
   two gates map to committee 13 (IT Steering) and 12 (Executive) with **3
   active seated terms each, all linked to real users and all inside their term
   window** — measured 2026-09-03, so change authorisation works there out of
   the box. Do not carry either answer across; check with
   `GET /api/v1/changes/governance-health` (it names which half is missing) and
   follow **`docs/change-manager/CAB_OPERATING_RUNBOOK.md`** §0–§2. Appointing
   members is a tenant decision, so no migration can do it for you.
5. **Plan TLS**: when a domain is available — cert (Let's Encrypt via
   win-acme, or purchased PKCS12), add a `listen 443 ssl` server block to
   nginx, redirect 80→443, and flip
   `server.servlet.session.cookie.secure` traffic to HTTPS. Nothing in the
   app changes; TLS terminates at nginx.

---

## 8. Updating the app later (release loop)

⚠ **Paths and ports below are the bundle convention** (`C:\tss-grc`, app `:8084`,
actuator `:8090`) — see the DEP-6 box in §4. On the **existing** box the root is
`D:\Deployment\tss-grc` and the pair is app `:8090` / actuator `:8091`. Substitute
both, or step 4 checks a port nothing is listening on and reads as "unreachable"
rather than as "wrong build".

⚠ `PROJECT_CHRONICLE.md` §12.3 carries a **second copy** of this procedure, written
for the existing box. This section is the canonical one; that one now points here.

### 1 — Build

On the build machine:

```bash
git pull                                   # merged master
cd tss-grc-backend && mvn clean package    # JAVA_HOME must be JDK 17
cd ../tss-grc-ui-v2 && npm run build
```

### 2 — Back up the database BEFORE the swap

```powershell
sqlcmd -S localhost\SQLEXPRESS -E -Q "BACKUP DATABASE TSS_GRC TO DISK = N'C:\tss-grc\backup\TSS_GRC_pre-upgrade.bak' WITH INIT, CHECKSUM"
```

⚠⚠ **The §7.3 nightly is NOT this backup.** It exists and it is scheduled — and it is
up to **24 hours stale**, so restoring it after a failed update rolls the platform back
to yesterday and loses a day of governance records (risk decisions, approvals, evidence,
audit-log rows). Take a fresh one inside the maintenance window and keep it until step 4
passes. 🔑 Flyway migrations are **forward-only**: restoring the jar does not undo a
migration that has already applied, which is the whole reason this step exists.

Also copy the jar you are replacing, and the `dist` it was built with — step 5 needs both.

### 3 — Swap

On the server:

```powershell
Stop-Service TssGrcBackend
Copy-Item <new>.jar C:\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar -Force
Remove-Item C:\tss-grc\ui\* -Recurse -Force; Copy-Item <new-dist>\* C:\tss-grc\ui\ -Recurse
Start-Service TssGrcBackend     # Flyway applies every migration above the recorded baseline
```

No manual SQL: any migration in the jar above the recorded baseline applies itself at
startup (that is what `FLYWAY_BASELINE_VERSION` records). `external.properties` is never
touched by a redeploy — your configuration survives every upgrade.

⚠ **Scale, on a FRESH install from the 2026-09-03 bundle.** The backup it ships
(`deploy/TSS_GRC_2026-07-11.bak`) is baselined at **V292** and the packaged chain head is
**V400**, so first boot applies **108** migrations (V293…V400) — not "a migration or two".
Expect minutes, not seconds, and do not conclude the service has hung.

### 4 — Verify, BEFORE you tell anyone the release is out

🔑 **The swap is not the release; this step is.** `Start-Service` succeeds whether or not
the new jar is running: WinSW's `<onfailure action="restart" delay="10 sec"/>` restarts a
jar that dies at Flyway, and nginx keeps serving the UI whatever the backend is doing.

```powershell
Invoke-RestMethod http://127.0.0.1:8090/actuator/health   # ACTUATOR port — :8091 on the existing box
```

```powershell
# From a checkout of the commit you deployed. Exit 0 = match, 1 = mismatch, 2 = unreachable.
py TSS-GRC-Platform-Bible\Appendix\scripts\deployment_identity.py --url http://127.0.0.1:8090 --expect-commit (git rev-parse HEAD)
```

This is the same script §4 runs on first boot, and it is the only thing that distinguishes
*"the service started"* from *"the service is running the build you just shipped"*. Read
three things from it:

- **`build.commit`** must equal the commit you deployed. ⚠ `build.commit = unknown` means
  the jar was built without the flag and **cannot** be identified — treat that as a
  **failed** check, not a pass.
- **`pending`** must be **empty**. A non-empty list is the deployment gap stated as fact:
  those migrations have *not* applied, so the schema is not the one this jar expects.
- **`/actuator/health`** must be `UP`. ⚠ It is bound to **loopback**, so run this on the
  server — it is deliberately not reachable through nginx.

⚠ Nothing external reports a failure here: actuator is loopback-only and `SENTRY_DSN` is
empty by default. If you skip this step, a failed update presents as "the site is half up"
and is found by a user.

### 5 — Roll back, if step 4 fails

Executable only because step 2 exists.

```powershell
Stop-Service TssGrcBackend
Copy-Item <previous>.jar C:\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar -Force
Remove-Item C:\tss-grc\ui\* -Recurse -Force; Copy-Item <previous-dist>\* C:\tss-grc\ui\ -Recurse
Start-Service TssGrcBackend
```

⚠⚠ **Restoring the jar is sufficient ONLY if no migration applied.** Read `pending` and the
Flyway lines in the log first. If a migration **has** applied, the old jar now meets a newer
schema and the rollback is *restore the step-2 backup as well*. Where the failure is a data
problem rather than a broken jar, prefer rolling **forward** with a corrective migration —
migrations are forward-only by design. Re-run step 4 after any rollback.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Config edits seem ignored | `external.properties` must sit in the service's working directory (`C:\tss-grc\app`, next to the jar) — and the service must be restarted |
| Service dies at boot, log says *"JWT_SECRET appears to be a placeholder"* | Set a real ≥32-char `JWT_SECRET` in `external.properties` (must not contain `change-this`/`placeholder`) |
| Flyway runs V1/V2 and fails with *"Invalid column name"* | It's checking the wrong (empty) schema — the packaged default `FLYWAY_DEFAULT_SCHEMA=platform` handles this; don't override it to `dbo` |
| Flyway tries to run V3…V292 and fails | `FLYWAY_BASELINE_VERSION=292` missing/changed — the restored DB already has those objects |
| **An UPDATE fails at startup** — Flyway reports a checksum mismatch, or a migration errors mid-chain | ⚠ **The service restart-loops rather than staying down** (`<onfailure action="restart" delay="10 sec"/>`) while nginx keeps serving the old UI and `/api` 502s, so the symptom is *"the site is half up"*, not *"the service is stopped"* — read the Flyway lines in the log, never the service state. **Checksum mismatch:** `validate-on-migrate: true` validates every already-applied migration *before* migrating, so one edited migration file blocks the whole chain. Measured 2026-09-12 on the existing box: **7 of its 53 history rows drifted** (V302, V315, V316, V338, V339, V340, V341) because two commits edited applied migrations. `flyway repair` re-records the checksums and is the remedy — it **writes to the database**, so it is an owner decision, not a step to take mid-incident. ⚠ A **fresh** install is unaffected: it applies those files as new and records matching checksums. **Mid-chain failure:** the schema is half-modified — restore the step-2 backup and fix the migration; do not re-run `Start-Service` and hope. ⚠ Nothing alerts either way (actuator is loopback-only, `SENTRY_DSN` empty by default), so §8 step 4 is the only thing that catches this |
| Boot fails: *"Schema-validation: wrong column type … is_deleted"* | `HIBERNATE_DDL_AUTO` was set to `validate` — the long-lived DB mixes `smallint`/`bit`; leave it unset (`none`) until types are reconciled |
| `/actuator/health` DOWN but the app works | An optional-infrastructure probe (LDAP `localhost:389` / SMTP) — packaged default disables both; re-enable via `LDAP_HEALTH_ENABLED` / `MAIL_HEALTH_ENABLED` only when real servers exist |
| A raw Spring key in `external.properties` doesn't take effect | Profile-specific packaged values beat base-level imports — use the `UPPER_CASE` placeholder name instead (those always win); if no placeholder exists for that key, ask for one to be added |
| Login works via curl but browser gets CORS errors | You bypassed nginx and hit :8084 directly — always go through port 80 (same origin) |
| UI loads, everything 404s under `/api` | nginx `proxy_pass` block missing/edited — restore the shipped `nginx.conf` |
| WebSocket panels dead, `/ws` returns 400 | The `location /ws` block needs the `Upgrade`/`Connection` headers (in the shipped config) |
| Jar refuses to start on the server | Wrong Java — must be JDK 17 (`"%JAVA_HOME%\bin\java" -version`) |
