# TSS GRC — Windows Server Deployment Runbook

Deploys the platform to a Windows Server cloud machine, reachable by **IP over
HTTP** (TLS can be added at the reverse proxy later). Database arrives as a
**restore of the dev `TSS_GRC` database** (all schema + demo tenants), with
Flyway baselined at **V292** so only future migrations auto-apply.

**Topology**

```
Browser ──► nginx :80          (serves the React UI + proxies /api and /ws)
              │
              ├──► Spring Boot :8084   (prod profile, HTTP, bound to 127.0.0.1)
              │        └──► SQL Server :1433 (local, TSS_GRC restored from .bak)
              └──  Actuator :8090      (localhost only — never exposed)
```

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
# expect: users >= 7, perms = 94
```

---

## 3. Configure `external.properties` (the ONLY config file)

All runtime configuration lives in `C:\tss-grc\app\external.properties`, next
to the jar. The app imports it at startup (`spring.config.import` in the
packaged `application.yml`) — **edit the file, restart the service, done; no
environment variables, no rebuild.** The shipped file is pre-filled for this
topology; you only have to replace two values:

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
  <log mode="roll"></log>
  <onfailure action="restart" delay="10 sec"/>
</service>
```

(`workingdirectory` matters: `external.properties` is resolved relative to it.)

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
  <log mode="roll"></log>
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

First-boot verification (backend takes ~60–90 s):

```powershell
# wait for UP
curl.exe -s http://127.0.0.1:8090/actuator/health          # {"status":"UP"}

# Flyway must say it BASELINED, not migrated — expect this exact line on
# first boot: "Successfully baselined schema with version: 292"
# (and on every later boot: "Schema [platform] is up to date. No migration necessary.")
Select-String -Path C:\tss-grc\logs\application.log -Pattern "baselined|No migration necessary"

# API login smoke test
curl.exe -s -X POST http://127.0.0.1:8084/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"Admin@12345\"}"
# expect a JSON body containing accessToken
```

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

4. **Plan TLS**: when a domain is available — cert (Let's Encrypt via
   win-acme, or purchased PKCS12), add a `listen 443 ssl` server block to
   nginx, redirect 80→443, and flip
   `server.servlet.session.cookie.secure` traffic to HTTPS. Nothing in the
   app changes; TLS terminates at nginx.

---

## 8. Updating the app later (release loop)

On the build machine:

```bash
git pull                                   # merged master
cd tss-grc-backend && mvn clean package    # JAVA_HOME must be JDK 17
cd ../tss-grc-ui-v2 && npm run build
```

On the server:

```powershell
Stop-Service TssGrcBackend
Copy-Item <new>.jar C:\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar -Force
Remove-Item C:\tss-grc\ui\* -Recurse -Force; Copy-Item <new-dist>\* C:\tss-grc\ui\ -Recurse
Start-Service TssGrcBackend     # Flyway auto-applies any migration > V292
```

No manual SQL: any new `V293+__*.sql` in the jar applies itself at startup
(that's why the restored DB was baselined at 292). `external.properties` is
never touched by a redeploy — your configuration survives every upgrade.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Config edits seem ignored | `external.properties` must sit in the service's working directory (`C:\tss-grc\app`, next to the jar) — and the service must be restarted |
| Service dies at boot, log says *"JWT_SECRET appears to be a placeholder"* | Set a real ≥32-char `JWT_SECRET` in `external.properties` (must not contain `change-this`/`placeholder`) |
| Flyway runs V1/V2 and fails with *"Invalid column name"* | It's checking the wrong (empty) schema — the packaged default `FLYWAY_DEFAULT_SCHEMA=platform` handles this; don't override it to `dbo` |
| Flyway tries to run V3…V292 and fails | `FLYWAY_BASELINE_VERSION=292` missing/changed — the restored DB already has those objects |
| Boot fails: *"Schema-validation: wrong column type … is_deleted"* | `HIBERNATE_DDL_AUTO` was set to `validate` — the long-lived DB mixes `smallint`/`bit`; leave it unset (`none`) until types are reconciled |
| `/actuator/health` DOWN but the app works | An optional-infrastructure probe (LDAP `localhost:389` / SMTP) — packaged default disables both; re-enable via `LDAP_HEALTH_ENABLED` / `MAIL_HEALTH_ENABLED` only when real servers exist |
| A raw Spring key in `external.properties` doesn't take effect | Profile-specific packaged values beat base-level imports — use the `UPPER_CASE` placeholder name instead (those always win); if no placeholder exists for that key, ask for one to be added |
| Login works via curl but browser gets CORS errors | You bypassed nginx and hit :8084 directly — always go through port 80 (same origin) |
| UI loads, everything 404s under `/api` | nginx `proxy_pass` block missing/edited — restore the shipped `nginx.conf` |
| WebSocket panels dead, `/ws` returns 400 | The `location /ws` block needs the `Upgrade`/`Connection` headers (in the shipped config) |
| Jar refuses to start on the server | Wrong Java — must be JDK 17 (`"%JAVA_HOME%\bin\java" -version`) |
