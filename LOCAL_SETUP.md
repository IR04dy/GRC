# Local Environment Setup — TSS GRC Platform

How to stand up the full TSS GRC stack on a fresh machine and (optionally) reach
the dev UI from another machine on the same LAN.

The platform has **three tiers**, all expected on one host in dev:

| Tier | Tech | Dev port |
|------|------|---------|
| Frontend | React 18 + Vite + TypeScript (`tss-grc-ui-v2/`) | **5173** |
| Backend  | Spring Boot 3.5 REST API (`tss-grc-backend/`)     | **8084** |
| Database | SQL Server 2019 Express (`TSS_GRC`)               | **1433** |

The frontend calls the backend through a same-origin Vite proxy (`/api` →
`localhost:8084`), so the browser only ever talks to port 5173.

---

## 1. Prerequisites

Install these once on the new machine:

| Tool | Version | Notes |
|------|---------|-------|
| **Java JDK** | **17** (pinned) | Do **not** use Java 25 — Lombok breaks in this project |
| **Maven** | 3.9+ | builds the backend jar |
| **Node.js** | 18+ | runs the Vite dev server |
| **SQL Server** | 2019 Express | reachable on `localhost:1433`, TCP/IP enabled |
| **Python** | 3.x | for DB bootstrap/seed scripts (`pip install pyodbc`) |
| **ODBC Driver 17 for SQL Server** | — | required by `pyodbc` |
| **git** | — | clone the repo |
| **gh** (GitHub CLI) | optional | only if you push branches/PRs |

Verify:

```powershell
java -version      # 17.x
mvn -v             # 3.9+
node -v            # 18+
python --version   # 3.x
```

---

## 2. Clone the repository

```bash
git clone https://github.com/Ayman-Server/Claude-GRC-2.git
cd Claude-GRC-2
```

---

## 3. Database

Set the SQL Server `sa` password to **`tss.123`** (or adjust `DB_PASSWORD` later),
enable the TCP/IP protocol on port **1433**, and start the SQL Server service:

```powershell
Start-Service 'MSSQL$SQLEXPRESS'      # run elevated; service name may differ
```

Then create the data. Pick **one** option.

### Option A — copy this machine's database (most faithful "same environment")

On the **source** machine:

```sql
BACKUP DATABASE TSS_GRC TO DISK = 'C:\temp\TSS_GRC.bak' WITH INIT, COMPRESSION;
```

Copy `TSS_GRC.bak` to the new machine, then restore (adjust file paths to the new
machine's SQL data directory):

```sql
RESTORE DATABASE TSS_GRC FROM DISK = 'C:\temp\TSS_GRC.bak'
WITH MOVE 'TSS_GRC'     TO 'C:\...\MSSQL\DATA\TSS_GRC.mdf',
     MOVE 'TSS_GRC_log' TO 'C:\...\MSSQL\DATA\TSS_GRC_log.ldf';
```

This reproduces the exact data (all tenants, demo rows, users) with no script
juggling.

### Option B — bootstrap from scripts

```bash
sqlcmd -S localhost -U sa -P tss.123 -Q "CREATE DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;"

python setup_cyber_db.py        # cyber schema + seed
python setup_bcm_db.py          # bcm schema + seed
python setup_committees_db.py   # admin.committee_* tables
python fix_arabic_db.py         # Arabic collation/data repair
python seed_real_data.py        # ~450 rows of Saudi-native demo data (idempotent)
```

> Some Sprint-1 schemas (auth / iam / risk / incident …) also come from the SQL
> files under `db/sqlserver/` plus the Sprint-1 steps in `STATUS.md`. If a script
> reports `Invalid object name '...'`, the owning `setup_<module>_db.py` (or that
> SQL) hasn't run yet. **Option A avoids all of this.**

The UTF-8 collation (`Latin1_General_100_CI_AS_SC_UTF8`) is required for correct
Arabic storage — don't skip it.

---

## 4. Backend (Spring Boot, port 8084)

Build the jar:

```powershell
cd tss-grc-backend
mvn clean package -DskipTests
# -> target\tss-grc-platform-0.1.0-SNAPSHOT.jar
```

Both env vars below are **mandatory** — the app refuses to boot without them:

- `JWT_SECRET` — at least 32 chars, must **not** contain `change-this` or `placeholder`.
- `DB_PASSWORD` — the SQL Server `sa` password (`tss.123`).

Run it:

```powershell
$env:JWT_SECRET  = "tss-grc-dev-jwt-secret-0123456789-abcdefghij-KLMNOP"
$env:DB_PASSWORD = "tss.123"
java -jar target\tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Check it's healthy:

- Health:  http://localhost:8084/actuator/health → `{"status":"UP"}`
- Swagger: http://localhost:8084/swagger-ui.html (dev only)

---

## 5. Frontend (Vite, port 5173)

```powershell
cd tss-grc-ui-v2
npm install
npm run dev            # binds to all interfaces (host:true is in vite.config.ts)
```

Open http://localhost:5173/login.

The committed `vite.config.ts` sets `server.host = true`, so the dev server is
already reachable on the machine's LAN IP. (You can also force it explicitly with
`npm run dev -- --host`.)

---

## 6. Access from another machine on the LAN

1. **Find the host's LAN IP** — `ipconfig` (look for the Wi-Fi/Ethernet IPv4
   address, e.g. `192.168.8.243`; ignore VMware/virtual adapters), or read the
   **Network:** URL that Vite prints on startup.

2. **Allow the port through Windows Firewall** (only if the firewall is enabled;
   run elevated):

   ```powershell
   New-NetFirewallRule -DisplayName "TSS GRC Dev UI (5173)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5173 -Profile Any
   ```

   You only need to open **5173**. The backend (8084) does **not** need to be
   exposed — the browser talks to 5173 and Vite proxies `/api` to the backend
   server-side.

3. From the other machine, browse to **`http://<HOST-LAN-IP>:5173`**.

### CORS note (important)

The browser loading the UI from `http://<lan-ip>:5173` sends that as its
`Origin`. The dev profile's CORS allowlist now includes private-LAN **wildcard
patterns** (`192.168.*`, `10.*`, `172.*` on any port) via
`grc.cors.allowed-origins` in `application-dev.yml`, matched as
`allowedOriginPatterns` in `CorsConfig.java`. So LAN login works out of the box
**once the backend is built from current `master`**.

If you ever run an **older backend build** (exact-match origins only), the browser
gets `403 Invalid CORS request` on login. Work around it without rebuilding by
adding the exact origin at launch:

```powershell
java -jar target\tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev `
  --grc.cors.allowed-origins="http://localhost:5173,http://127.0.0.1:5173,http://<HOST-LAN-IP>:5173"
```

---

## 7. Login

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Admin@12345` | Platform admin |
| `riskmanager` | `Risk@12345` | Risk manager |
| `auditor` | `Audit@12345` | Auditor |

---

## 8. Keeping the services running

The backend and Vite are foreground processes. If you launch them from a terminal
or IDE that later closes, they stop. To keep them up independently:

- **PowerShell** — `Start-Process` runs a detached process:

  ```powershell
  # backend
  $env:JWT_SECRET="..."; $env:DB_PASSWORD="tss.123"
  Start-Process java -ArgumentList '-jar','target\tss-grc-platform-0.1.0-SNAPSHOT.jar','--spring.profiles.active=dev' -WorkingDirectory (Resolve-Path .\tss-grc-backend) -WindowStyle Hidden

  # frontend
  Start-Process "C:\Program Files\nodejs\node.exe" -ArgumentList 'node_modules\vite\bin\vite.js','--host' -WorkingDirectory (Resolve-Path .\tss-grc-ui-v2) -WindowStyle Hidden
  ```

- For long-term use, run a real production build (`npm run build` in
  `tss-grc-ui-v2/`) served behind a proper web server, and/or register the backend
  as a Windows service. The Vite **dev** server is not meant to be a durable host.

---

## 9. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Backend won't boot, "JWT_SECRET appears to be a placeholder" | `JWT_SECRET` missing/placeholder | Export a 32+ char secret without `change-this`/`placeholder` |
| Backend won't boot, "Unable to determine Dialect without JDBC metadata" | Can't reach SQL Server | Start SQL Server, confirm `sa`/`tss.123`, port 1433 open |
| UI loads but **can't log in** from a LAN IP → `403 Invalid CORS request` | Backend CORS rejecting the LAN origin | Build from current `master` (wildcard patterns), or pass `--grc.cors.allowed-origins=...,http://<ip>:5173` |
| LAN IP **doesn't respond at all** | Vite bound to localhost only | Ensure `host:true` (committed) or run `npm run dev -- --host` |
| Reachable locally, not from another machine | Windows Firewall | Add the inbound rule for TCP 5173 (section 6) |
| A service "stops on its own" | It was a child of a closed terminal/session | Launch it detached (`Start-Process`) or as a service (section 8) |
| Arabic text shows as mojibake | Wrong DB collation | Recreate `TSS_GRC` with `Latin1_General_100_CI_AS_SC_UTF8` |

---

## Reference

- `CLAUDE.md` — full command reference, env vars, architecture, known landmines.
- `STATUS.md` — module-by-module completion state.
- `README.md` — public overview and regulatory mapping.
