# Session Summary — 2026-06-18

Comprehensive log of this working session on the **TSS GRC Platform**: the
autonomous orphan-closing loop, then the live deployment / LAN-access work, the
diagnosis-and-fix of the login failure, and everything committed to GitHub.

- **Repo:** `Ayman-Server/Claude-GRC-2`
- **Worktree:** `C:\AYMAN\Github\Claude-GRC-2\.claude\worktrees\tender-curie-cee787`
- **Final `master`:** `3ca91bf`
- **PRs shipped this session-tail:** [#484](https://github.com/Ayman-Server/Claude-GRC-2/pull/484), [#485](https://github.com/Ayman-Server/Claude-GRC-2/pull/485) (on top of the orphan-loop run #463→#482)

---

## 1. Platform context

**TSS GRC Platform** — bilingual (Arabic-primary / English), multi-tenant,
enterprise Governance-Risk-Compliance application for Saudi Arabia / MENA.
Three tiers, all on one host in dev:

| Tier | Tech | Dev port |
|------|------|---------|
| Frontend | React 18 + Vite 5 + TypeScript (`tss-grc-ui-v2/`) | 5173 |
| Backend | Spring Boot 3.5, Java 17, Spring Security 6, JWT (`tss-grc-backend/`) | 8084 |
| Database | SQL Server 2019 Express, UTF-8 collation, DB `TSS_GRC` | 1433 |

Frontend → backend via a same-origin Vite proxy (`/api` → `localhost:8084`).
Seeded logins: `admin/Admin@12345`, `riskmanager/Risk@12345`, `auditor/Audit@12345`.

---

## 2. Phase 1 — Autonomous orphan-closing loop

A standing `/loop` directive ("do all the next, do not stop, do not ask")
drove a repeating cycle to find and close **orphaned backend write-capabilities**:
an exported API-client write function in `src/lib/**` referenced by no
`src/pages/**` or `src/components/**` file, but with a real persisted write path,
seeded org-1 data, and an admin-satisfiable guard.

**Per-orphan cycle:** read backend contract + guard + DTO + entity + service
state-machine → build a bilingual (AR+EN) edit/transition/action affordance
(FormModal or buttons) → `tsc --noEmit` → live E2E verify via the Preview MCP in
**both** Arabic and English including real DB-confirmed mutations + a negative
test → reset the demo DB to pristine → commit → PR → squash-merge → sync master.
**One verified, merged PR per orphan.**

- **Result:** 18 verified, merged PRs — **#463 → #482** — each closing one
  orphaned write-capability (e.g. dissolve-committee, cancel-campaign,
  archive-GHG-record, the Cyber→PDPL bridge manual trigger, and many ESG / NDI /
  BCM / Committees / Outsourcing edit & transition actions; see the completed
  task list).
- **Deliberately deferred (4):** `archiveTemplate` (dev table absent),
  `deleteClimateItem` (hard delete; app convention is no UI delete),
  `reorderItems` (redundant with `sortOrder`), `startRun` (no clean launch
  surface).
- **Backlog declared exhausted** after #482, validated across multiple sweeps.

### Phase 2 — Idle heartbeat

With the backlog exhausted, the loop dropped to a 30-minute heartbeat: each tick
`git fetch/pull` master, re-run the orphan sweep, confirm "no new orphans,"
reschedule. Master stayed at `fdc5f2b` (background PR #483, a font-size/quick-color
theme customizer, was the latest non-orphan commit). Many idle ticks, no changes.

---

## 3. Phase 3 — Live deployment & LAN access (this session's main work)

### 3.1 "Is GitHub master the latest?"
Verified local worktree HEAD, `origin/master`, working tree, and open PRs were all
in sync at `fdc5f2b` — zero open/unmerged PRs, clean tree.

### 3.2 "Start the application and give me the URL"
- Started the **frontend** via the Preview tool → http://localhost:5173 (HTTP 200).
- Found the **backend down** and **SQL Server (SQLEXPRESS) Stopped**. Starting the
  SQL service requires **admin elevation**, which the non-interactive shell can't
  do — surfaced this to the user.

### 3.3 LAN binding fix (the first real fix)
After the user started SQL, started the backend (prebuilt jar + `JWT_SECRET` /
`DB_PASSWORD`). Then made the dev UI reachable over the LAN:

- **Symptom:** the URL worked on localhost but not via the machine's IP.
- **Cause:** Vite binds to localhost only by default.
- **Fix:** `server.host = true` + `allowedHosts = true` in the Vite config.
- **Gotcha:** Vite loads **`vite.config.js` before `vite.config.ts`**. A committed-
  artifact `vite.config.js` existed and shadowed the `.ts`; editing only the `.ts`
  did nothing (Vite logged "use --host to expose"). Edited the `.js` too →
  Network URL `http://192.168.8.243:5173/` appeared. (`vite.config.js` is
  gitignored; the committed `.ts` governs a fresh clone.)
- **Firewall:** all Windows Firewall profiles were `Enabled: False`, so nothing
  blocked 5173 — the localhost binding was the only blocker.

### 3.4 "The server stopped — why, and start it again"
- Only **Vite** had stopped; backend + SQL were still up.
- **Cause:** the Preview tool runs the dev server as a child of the tooling
  session; when that connection cycled, the managed Vite was torn down. The
  backend survived because it was a detached OS process.
- **Fix:** relaunched Vite as a standalone detached process (`--host`).

### 3.5 "It's working but I can't login" — the CORS root cause
The headline bug. Server-side `curl` login succeeded, but the browser couldn't.

- **Root cause:** the dev CORS allowlist (`grc.cors.allowed-origins`) listed only
  `localhost` / `127.0.0.1` origins. A browser on another machine sends
  `Origin: http://192.168.8.243:5173`, which wasn't allowlisted, so Spring's CORS
  filter returned **403 "Invalid CORS request"** *before login ran*. Plain `curl`
  passed only because it sent no `Origin` header.
- **Diagnosis:** replayed the login with the browser's exact `Origin` header →
  reproduced the 403; with `Origin: http://localhost:5173` → 200. Confirmed via
  preflight header dump.
- **Immediate fix (no rebuild):** restarted the backend with the LAN origin added
  via launch override `--grc.cors.allowed-origins=…,http://192.168.8.243:5173`.
- **Verified:** login via the LAN IP with the browser `Origin` → **HTTP 200**,
  `Access-Control-Allow-Origin` echoed, valid 708-char JWT.

### 3.6 "Restart the application"
Discovered the earlier `nohup &` relaunches did **not** survive tool-session cycles
(only a harness-tracked background task had). Restarted **both** backend and Vite
as **fully independent Windows processes** via `Start-Process` (hidden), so they
no longer depend on the tooling session. Re-verified login over the LAN IP.

### 3.7 "Commit the latest + give me the guide"
- Committed the LAN-access work as **[PR #484](https://github.com/Ayman-Server/Claude-GRC-2/pull/484)** → squash-merged → master `d611b56`:
  - `vite.config.ts` — `host:true` + `allowedHosts:true` (**verified live**).
  - `CorsConfig.java` — `setAllowedOrigins` → `setAllowedOriginPatterns`.
  - `application-dev.yml` — added private-LAN wildcard origins
    (`192.168.*` / `10.*` / `172.*`, any port).
- **Honest caveat (disclosed in the PR):** the backend change could **not** be
  rebuilt/validated in-session — **Maven is not installed on this host** (checked
  PATH, `M2_HOME`/`MAVEN_HOME`, choco/scoop/sdkman/IntelliJ). It is
  backward-compatible (plain origins still match exactly; prod unchanged) and
  takes effect on the next `mvn package`. The running backend stayed on the
  prebuilt jar + launch arg.

### 3.8 "Yes — the guide in an md file"
- Created **[`LOCAL_SETUP.md`](LOCAL_SETUP.md)** (repo root) and shipped it as
  **[PR #485](https://github.com/Ayman-Server/Claude-GRC-2/pull/485)** → squash-merged → master `3ca91bf`.
- Covers: prerequisites, DB (backup/restore **or** bootstrap scripts), backend
  build + env vars, frontend, LAN access (IP, firewall, CORS), seeded logins,
  keeping services running, and a troubleshooting table.

---

## 4. Key technical learnings / gotchas

1. **Vite config precedence:** `vite.config.js` shadows `vite.config.ts`. If a
   compiled `.js` exists, editing the `.ts` alone has no effect.
2. **CORS is server-enforced too:** Spring's CORS filter rejects a present-but-
   disallowed `Origin` with 403 *before* the controller — even for requests the
   browser treats as same-origin. `curl` without `Origin` won't reveal it.
3. **`allowedOriginPatterns` vs `allowedOrigins`:** patterns allow wildcards
   *with* credentials (`allowCredentials=true`), which `allowedOrigins:"*"` forbids.
   Plain entries still match exactly — backward-compatible swap.
4. **Process durability on Windows:** Preview-tool-managed servers and plain
   `nohup &` from the tool shell die on session cycles; `Start-Process` (or a
   Windows service) creates a truly independent process.
5. **Spring property override:** `--grc.cors.allowed-origins=…` at launch overrides
   the baked-in YAML without a rebuild — useful when Maven isn't available.
6. **Mandatory boot env vars:** the backend refuses to start without a valid
   `JWT_SECRET` (≥32 chars, no `change-this`/`placeholder`) and `DB_PASSWORD`.

---

## 5. Final state at session end

| Item | Value |
|------|-------|
| `master` (local + origin) | `3ca91bf` |
| Open / unmerged PRs | none |
| Frontend | Vite on `:5173`, bound to all interfaces (independent process) |
| Backend | Spring Boot on `:8084`, `{"status":"UP"}`, CORS LAN origin via launch arg (independent process) |
| SQL Server | SQLEXPRESS running on `:1433` |
| LAN URL | `http://192.168.8.243:5173` → login `admin` / `Admin@12345` (verified HTTP 200 end-to-end) |

### PRs delivered this session-tail
| PR | Title | Merged → master |
|----|-------|------|
| [#484](https://github.com/Ayman-Server/Claude-GRC-2/pull/484) | feat(dev): enable LAN access to dev servers (Vite host + CORS LAN-IP patterns) | `d611b56` |
| [#485](https://github.com/Ayman-Server/Claude-GRC-2/pull/485) | docs: add LOCAL_SETUP.md (reproduce dev stack + LAN access) | `3ca91bf` |

(Plus the 18 orphan-closing PRs #463→#482 from the autonomous loop earlier in the
session, and background merges #480 / #483.)

---

## 6. Open follow-ups

- **Validate the committed CORS patterns** with a real `mvn package` — only
  possible on a machine that has Maven (e.g. the target machine during
  `LOCAL_SETUP.md` step 3). The running backend here still uses the launch-arg
  workaround.
- **Durable hosting** — the Vite *dev* server isn't a production host; for
  long-term LAN use, build (`npm run build`) and serve behind a real web server,
  and/or run the backend as a Windows service.
- This summary file is **uncommitted** by default — commit it if you want it kept
  in the repo.
