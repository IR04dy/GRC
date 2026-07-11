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
| `ui-dist/` (or `ui-dist.zip`) | Production React build (`tss-grc-ui-v2/dist`) |
| `TSS_GRC_2026-07-11.bak` | Database backup (SQL Server 2019, `RESTORE VERIFYONLY` passed) |
| `run-backend.cmd` | Service launcher — **all env vars live here** |
| `nginx.conf` | Ready reverse-proxy config |
| `DEPLOYMENT.md` | This file |

Copy the folder to the server (RDP drive mapping, or `robocopy \\SERVER\c$\tss-grc-stage deploy /E`).

---

## 1. Server prerequisites (once)

Run in an **elevated** PowerShell:

```powershell
# Java 17 (Temurin) — the app is pinned to 17
winget install EclipseAdoptium.Temurin.17.JDK

# NSSM — runs the jar and nginx as Windows services
winget install NSSM.NSSM
```

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
- jar + `run-backend.cmd` → `C:\tss-grc\app\`
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

## 3. Configure the backend launcher

Edit `C:\tss-grc\app\run-backend.cmd` — every deployment setting lives in this
one file. Generate the JWT secret first:

```powershell
-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
```

The launcher (template ships in `deploy/`; values you must set are marked `<>`):

```bat
@echo off
REM ── TSS GRC backend (prod profile, HTTP behind nginx) ─────────────────────
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot"

REM security — REQUIRED
set "JWT_SECRET=<64-char-secret-from-the-generator>"

REM database
set "DB_URL=jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=TSS_GRC;encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=true"
set "DB_USERNAME=grc_app"
set "DB_PASSWORD=<STRONG-DB-PASSWORD>"

REM Flyway — restored DB already carries everything up to V292;
REM baseline there so only FUTURE migrations (V293+) run.
set "FLYWAY_BASELINE_ON_MIGRATE=true"
set "FLYWAY_BASELINE_VERSION=292"

REM HTTP behind nginx (prod profile defaults to 8443+SSL; we terminate at nginx)
set "SERVER_PORT=8084"
set "SERVER_SSL_ENABLED=false"
set "SERVER_ADDRESS=127.0.0.1"
set "MANAGEMENT_SERVER_ADDRESS=127.0.0.1"

REM windows paths (prod defaults are /var/... Linux paths)
set "LOGGING_FILE_NAME=C:/tss-grc/logs/application.log"
set "FILE_STORAGE_PATH=C:/tss-grc/data/uploads"

REM mail — prod profile requires these to resolve; use real SMTP when available
set "SMTP_HOST=localhost"
set "SMTP_PORT=587"
set "SMTP_USERNAME=noreply"
set "SMTP_PASSWORD=unused"

"%JAVA_HOME%\bin\java.exe" -Xms512m -Xmx2g -jar C:\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

Lock the file down (it contains secrets):

```powershell
icacls C:\tss-grc\app\run-backend.cmd /inheritance:r /grant:r "Administrators:F" "SYSTEM:F"
```

---

## 4. Install the services

```powershell
# backend
nssm install TssGrcBackend C:\tss-grc\app\run-backend.cmd
nssm set TssGrcBackend AppDirectory C:\tss-grc\app
nssm set TssGrcBackend AppStdout C:\tss-grc\logs\service.out.log
nssm set TssGrcBackend AppStderr C:\tss-grc\logs\service.err.log
nssm set TssGrcBackend Start SERVICE_AUTO_START
nssm start TssGrcBackend

# nginx
nssm install TssGrcNginx C:\tss-grc\nginx\nginx.exe
nssm set TssGrcNginx AppDirectory C:\tss-grc\nginx
nssm set TssGrcNginx Start SERVICE_AUTO_START
nssm start TssGrcNginx
```

First-boot verification (backend takes ~60–90 s):

```powershell
# wait for UP
curl.exe -s http://127.0.0.1:8090/actuator/health          # {"status":"UP"}

# Flyway must say it BASELINED, not migrated:
Select-String -Path C:\tss-grc\logs\application.log -Pattern "baseline|Successfully"

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
nssm stop TssGrcBackend
Copy-Item <new>.jar C:\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar -Force
Remove-Item C:\tss-grc\ui\* -Recurse -Force; Copy-Item <new-dist>\* C:\tss-grc\ui\ -Recurse
nssm start TssGrcBackend        # Flyway auto-applies any migration > V292
```

No manual SQL: any new `V293+__*.sql` in the jar applies itself at startup
(that's why the restored DB was baselined at 292).

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Service dies at boot, log says *"JWT_SECRET appears to be a placeholder"* | Set a real ≥32-char `JWT_SECRET` in `run-backend.cmd` (must not contain `change-this`/`placeholder`) |
| *"Could not resolve placeholder 'SMTP_HOST'"* | The four `SMTP_*` lines are mandatory in the prod profile — keep them even with dummy values |
| Flyway error *"found non-empty schema without schema history"* | `FLYWAY_BASELINE_ON_MIGRATE=true` missing — add it |
| Flyway tries to run V3…V292 and fails | `FLYWAY_BASELINE_VERSION=292` missing — the restored DB already has those objects |
| Login works via curl but browser gets CORS errors | You bypassed nginx and hit :8084 directly — always go through port 80 (same origin) |
| UI loads, everything 404s under `/api` | nginx `proxy_pass` block missing/edited — restore the shipped `nginx.conf` |
| WebSocket panels dead, `/ws` returns 400 | The `location /ws` block needs the `Upgrade`/`Connection` headers (in the shipped config) |
| Jar refuses to start on the server | Wrong Java — must be JDK 17 (`"%JAVA_HOME%\bin\java" -version`) |
