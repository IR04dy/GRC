# TSS GRC Platform — Session Summary

**Date:** 2026-04-30  
**Branch:** `claude/unruffled-lamarr-2ebc33`  
**Worktree:** `C:\AYMAN\Github\Claude-GRC-2\.claude\worktrees\unruffled-lamarr-2ebc33\`

---

## 1. The mission

Build a new React + TypeScript frontend (`tss-grc-ui-v2`) for the TSS GRC Platform, replacing the legacy plain-HTML frontend (`tss-grc-ui`). Adopt the **Eye-Comfort Enterprise** design system from `C:\AYMAN\Gafaar-GRC-HTML\TSS GRC Platform Design System\`. Wire the new UI to the existing Spring Boot backend (`tss-grc-backend`). Make it bilingual (English / Arabic with full RTL).

The brief evolved through this session from "build one pilot module" → "build all modules" → "wire to backend" → "fill in every tab with all v1 functions" → "translate every label to Arabic".

---

## 2. Repository layout

```
.claude/worktrees/unruffled-lamarr-2ebc33/
├── CLAUDE.md                  ← project instructions for future Claude Code sessions
├── README.md                  ← public-facing overview (legacy)
├── STATUS.md                  ← v1 module-by-module completion snapshot
├── SESSION_SUMMARY.md         ← v1 handoff (2026-04-07)
├── summary.md                 ← THIS FILE
│
├── tss-grc-backend/           ← Spring Boot 3.5 REST API (Java 17)
│   └── src/main/...           ← 17 modules under com.tss.grc.module/
│
├── tss-grc-ui/                ← LEGACY v1 frontend (plain HTML / Bootstrap / Vanilla JS)
│   ├── login.html, dashboard.html, index.html
│   └── <module>/index.html    ← admin, bcm, compliance, cyber, evidence,
│                                 incidents, privacy, risk, scm, tprm
│
├── tss-grc-ui-v2/             ← ★ ACTIVE — React 18 + Vite + TypeScript
│   ├── src/
│   │   ├── App.tsx            ← routes
│   │   ├── main.tsx           ← entry
│   │   ├── styles/            ← tokens.css · globals.css · shell.css · components.css
│   │   ├── lib/               ← api.ts · auth.ts · i18n.tsx · theme.tsx · toast.tsx
│   │   │                          labels.ts · useApiResource.ts · cn.ts
│   │   ├── translations/      ← en.ts · ar.ts (~600 keys each)
│   │   ├── components/
│   │   │   ├── layout/        ← Shell · Sidebar · Topbar · PageHeader
│   │   │   ├── ui/            ← Button · Badge · Input · Select · Card · Kpi
│   │   │   │                     Progress · Tabs · Modal · FormModal · DataTable
│   │   │   │                     EmptyState · RowActions · ConfirmDialog · Stepper
│   │   │   │                     Timeline · ColorField · RangeField
│   │   │   └── viz/           ← HeatMap · BarChart3D · RiskTable
│   │   └── pages/
│   │       ├── LoginPage.tsx · DashboardPage.tsx · SettingsPage.tsx · PlaceholderPage.tsx
│   │       ├── risk/          ← RiskRegisterPage · NewRiskModal · mockRisks
│   │       ├── cyber/         ← CyberPage · mockCyber                ← 10 tabs, 9 entities
│   │       ├── bcm/           ← BcmPage · mockBcm                    ← 5 tabs
│   │       ├── privacy/       ← PrivacyPage · mockPrivacy            ← 5 tabs
│   │       ├── compliance/    ← CompliancePage · mockCompliance      ← 6 tabs
│   │       ├── tprm/          ← TprmPage · mockTprm                  ← 4 tabs
│   │       ├── incidents/     ← IncidentsPage · mockIncidents        ← 4 tabs
│   │       ├── evidence/      ← EvidencePage · mockEvidence          ← 5 tabs
│   │       ├── admin/         ← AdminPage · mockAdmin                ← 4 tabs
│   │       └── scm/           ← ScmPage · mockScm                    ← 5 tabs
│   ├── package.json (vite + react + lucide-react + react-router-dom + clsx)
│   └── vite.config.ts          ← proxies /api → http://localhost:8084
│
└── TSS-Theme/                 ← FROZEN reference clone of v2 at first sign-off
    └── THEME.md               ← visual spec, palette, components
```

---

## 3. Backend (`tss-grc-backend`) — what's running

- **Spring Boot 3.5**, **Java 17 (pinned)**, **SQL Server 2019 Express**
- Runs from `target/tss-grc-platform-0.1.0-SNAPSHOT.jar` on **port 8084** (dev profile override; base default is 8080)
- DB: `TSS_GRC` database, user `sa` / password `tss.123` (hard-coded in `application-dev.yml`)
- **Flyway is disabled in the dev profile** — schema is bootstrapped by Python scripts (`setup_cyber_db.py`, `setup_bcm_db.py`, `fix_arabic_db.py`) at the repo root
- JPA `ddl-auto=none` in dev (validate in base)
- Soft-delete via `is_deleted` column + Hibernate `@Where(clause = "is_deleted = 0")`
- Stateless JWT — access in `Authorization: Bearer …` (15 min default, 1h in dev), refresh as `HttpOnly` cookie at `/api/auth/refresh`
- 17 backend modules: `admin`, `auth`, `bcm`, `compliance`, `cyber`, `dashboard`, `datagov`, `evidence`, `iam`, `incident`, `platform`, `privacy`, `reporting`, `risk`, `scm`, `tprm`, `workflow`
- Seeded users:
  - `admin` / `Admin@12345` (PLATFORM_ADMIN)
  - `riskmanager` / `Risk@12345`
  - `auditor` / `Audit@12345`

### CLAUDE.md was rewritten to fix v1 inaccuracies:
- Backend port 8084 not 8080 in dev
- Flyway off in dev
- Soft-delete is `is_deleted` not `deleted_at`
- 17 modules not 13
- Java 17 hard-pinned (Lombok breaks on 25)
- Migrations live at `src/main/resources/db/migration/sqlserver/` not `tss-grc-backend/db/migration/sqlserver/`

---

## 4. Frontend (`tss-grc-ui-v2`) — tech stack

| Concern | Choice |
|---|---|
| Build / dev server | Vite 5 — port **5173**, proxies `/api/*` → `http://localhost:8084` |
| Framework | React 18 + TypeScript (strict, `noUnusedLocals: true`) |
| Routing | `react-router-dom` 6 |
| Icons | `lucide-react` — every icon stroke-based 1.5–2px, **no emoji anywhere** |
| Styling | Vanilla CSS with the design tokens copied verbatim from `colors_and_type.css` (no Tailwind / shadcn) |
| State | React hooks + Context — `I18nProvider`, `ThemeProvider`, `ToastProvider`, no Redux |
| Data | Hand-rolled `fetch` wrapper at `src/lib/api.ts` with JWT bearer header; 401 clears the session |
| i18n | `src/lib/i18n.tsx` + flat `en.ts` / `ar.ts` dictionaries — flips `<html dir>` and the body font stack |
| Auth | Access token in `sessionStorage`; refresh token is the backend's `HttpOnly` cookie |
| Forms | `FormModal` with `FieldDef[]` schema, 12-column Bootstrap-style grid |

---

## 5. Design system / tokens

**Eye-Comfort Enterprise** palette (lifted from `C:\AYMAN\Gafaar-GRC-HTML\TSS GRC Platform Design System\colors_and_type.css`):

| Role | Token | Value |
|---|---|---|
| Canvas | `--bg` | `#F2EEE6` (warm off-white) |
| Cards | `--surface` | `#FAF8F4` |
| Primary | `--primary` | `#0E5E5E` (deep teal-petrol) |
| Accent | `--accent` | `#C8932A` (warm sand-amber) |
| Sidebar bg | `--sb-bg` | `#0B1F22` |
| Sidebar highlight | `--sb-accent` | `#E5B547` |
| Ink (primary text) | `--ink-1` | `#15201D` (warm near-black) |
| Risk severity ramp | `--risk-vlow` … `--risk-crit` | sage → terracotta |
| Categorical (8 colors) | `--cat-1` … `--cat-8` | brand teal, sand, slate-teal, plum, sage, terracotta, violet, olive gold |

**Type stack:** *Plus Jakarta Sans* (Latin UI), *IBM Plex Sans Arabic* (Arabic UI), *JetBrains Mono* (codes/IDs/dates). Loaded from Google Fonts.

**Layout tokens:** `--sidebar-w: 280px`, `--header-h: 64px`, `--content-max: 1440px`, radius 4–20px, three shadow steps, motion timings 120–320ms with cubic-bezier `(.2, .8, .2, 1)` soft-spring.

---

## 6. Module inventory (10 modules + Dashboard + Settings + Login)

| Route | Module | Tabs | Real backend? | Workflow actions |
|---|---|:---:|:---:|---|
| `/login` | Auth | — | ✅ `/api/auth/login` | — |
| `/dashboard` | Dashboard | — | ✅ `/api/v1/dashboard/stats` + `/heatmap` | — |
| `/settings` | Theme settings | — | localStorage-only | Reset, presets |
| `/risk` | Enterprise Risk | 4 (Register · Heat Map · KRI · Appetite) | ✅ `/api/v1/risk/risks` + `/categories` | — |
| `/cyber` | Cybersecurity | **10** (Overview · Controls · Sub-Controls · Assessments · Vulnerabilities · Exceptions · Incidents · Assets · Evidence · Action Plans) | ✅ `/api/v1/cyber/*` | **Approve · Escalate · Start Response · Complete** |
| `/bcm` | Business Continuity | 5 (Overview · Plans · Processes · BIA · Exercises) | ✅ `/api/v1/bcm/*` | — |
| `/privacy` | Privacy / PDPL | 5 (Overview · DSR · Breaches · RoPA · Consent) | ✅ `/api/v1/privacy/*` | — |
| `/compliance` | Compliance | 6 (Overview · Controls · Internal · External · Findings · Scorecard) | ✅ `/api/v1/compliance/*` | — |
| `/tprm` | Third-Party Risk | 4 (Overview · Vendors · Assessments · Contracts) | ✅ `/api/v1/tprm/vendors` | — |
| `/incidents` | Incidents | 4 (Overview · Register · Timeline · Lessons) | ✅ `/api/v1/incidents` | — |
| `/evidence` | Evidence | 5 (Overview · Catalog · Requests · Review · Expired) | ✅ `/api/v1/evidence/*` | — |
| `/admin` | Platform Admin | 4 (Users · Roles · Organisations · Audit Log) | ✅ `/api/v1/admin/*` | — |
| `/scm` | Supply Chain | 5 (Overview · Vendors · Onboarding · Assessments · Contracts) | ✅ `/api/v1/scm/*` | — |

Sidebar entries that the design system mocks but v1 doesn't have backend for (`/data`, `/audit`, `/policy`, `/about`) render a placeholder card — kept intentionally so the broader design vision shows.

---

## 7. The Cyber module (deepest build)

### 9 entity types, all with full CRUD + appropriate workflow actions:

| Entity | All v1 fields wired in form | Inline actions on rows |
|---|---|---|
| `CyberControl` | Framework, Domain, Control Number, Title EN/AR, Procedure, Control Type, Owner, Review Frequency, Maturity, Implementation Status, Status | Edit · Delete |
| `CyberSubControl` | Parent Control, Sub-Control Number, Title EN/AR, Description, Owner, Maturity, Implementation Status | Edit · Delete |
| `ControlAssessment` | Control, Assessor, Reviewer, Date, Result (PASS/FAIL/PARTIAL/N/A), Maturity (1–5), Risk Level, Gap Identified, Status, Gap Description, Assessor & Reviewer Comments | **Approve** · Edit · Delete |
| `Vulnerability` | Asset, Title EN/AR, CVE ID, Description, Severity, Vuln Type, Owner, CVSS, SLA days, Due Date, Workflow Status (6 stages), Remediation Notes | **Escalate** · Edit · Delete |
| `VulnException` | Title, Owner, Approver, Expiry Date, Workflow Status (5 stages), Impact Assessment, Reason, Compensating Controls | **Approve** · Edit · Delete |
| `CyberIncident` | Title EN/AR, Type (8 options), Affected Asset, Lead, Severity, Impact (C/I/A/Multiple), Workflow Status (9 stages), BCM Handoff, Lessons Learned | **Start Response** · Edit · Delete |
| `CyberAsset` | Asset Name, Type (8 options), Business & Tech Owner, Classification (Top Secret → Public), Criticality, Status, IP, Hostname, OS | Edit · Delete |
| `CyberEvidence` | Title, Type (6 options), Linked Control, Framework Requirement, Owner, Version, Status, Review Date, Expiry, File Name | Edit · Delete |
| `CyberActionPlan` | Title EN/AR, Priority, Description, Source Type, Owner, Due Date, Workflow Status, Progress %, Completion Evidence | **Complete** · Edit · Delete |

### Workflow API endpoints wired:
- `POST /api/v1/cyber/assessments/{id}/approve`
- `POST /api/v1/cyber/vulnerabilities/{id}/escalate`
- `POST /api/v1/cyber/exceptions/{id}/approve`
- `POST /api/v1/cyber/incidents/{id}/start-response`
- `POST /api/v1/cyber/action-plans/{id}/complete`
- `POST /api/v1/cyber/assessments/{id}/convert-gap` (exposed in API client, not yet UI-wired)

### Visual touches in Cyber Overview:
- **Vulnerabilities by Severity** bar chart (live counts from data)
- **Framework Readiness** progress bars (NCA ECC · ISO 27001 · MITRE ATT&CK · SAMA CSF)
- **Vulnerability Lifecycle Stepper** highlighting the focused open vuln's current stage
- **Response Workflow Stepper** inside Incidents tab for the latest incident

---

## 8. Reusable design touches built across the session

| Component | What it does | Used by |
|---|---|---|
| `Shell` + `Sidebar` + `Topbar` + `PageHeader` | Consistent page chrome with eyebrow / title / actions / KPI strip | Every page |
| `DataTable<T>` | Generic table with column definitions, mono flag, alignment, custom cells | All 11 modules |
| `FormModal<T>` | Schema-driven form (`FieldDef[]`), 12-col Bootstrap-style grid, 880px wide, edit-mode aware | All entity create/edit |
| `RowActions` | Inline pencil / trash buttons + custom workflow actions, hover-fades | Every list table |
| `ConfirmDialog` | Destructive-action confirm modal with triangle warning + danger button | Every delete flow |
| `Stepper` | Horizontal workflow stages with done / active / todo states + accent halo on active | Vuln lifecycle, Incident response, Onboarding |
| `Timeline` | Vertical event list with tone-coloured dots + connecting line | Incidents Timeline, Admin Audit Trail |
| `MaturityBadge` | L1–L5 chip with colour ramp + qualitative label | Cyber Assessments |
| `BarChart3D` | CSS-only 3D-feel bar chart with gradient + specular highlight | Every overview tab |
| `HeatMap` | 5×5 likelihood × impact grid with severity-coloured cells | Risk + Cyber dashboards |
| `Score pill` | Mono number on coloured background using `--risk-*` ramp | Risk score, vendor score, supplier score, compliance score |
| `KPI tile` | Icon (toned background) + value + trend chip + label | Every page header |
| `ColorField` / `RangeField` | Settings page primitives — color pickers + range sliders with reset | Settings page |
| `1-5 chip picker` | Likelihood / Impact selector inside Risk modal | NewRiskModal |

---

## 9. Settings / theme system

`/settings` page lets users override CSS tokens at runtime. Implementation in `src/lib/theme.tsx`:

- 10 tunable tokens exposed: `bg`, `surface`, `primary`, `accent`, `sb-bg`, `sb-text`, `sb-accent`, `sidebar-w`, `header-h`, `r-md`
- 5 named presets: *Eye-Comfort Default*, *Sand*, *Petrol*, *Mono*, *Rose*
- Persisted to `localStorage` under `grc.theme`
- Applied synchronously via `document.documentElement.style.setProperty('--token', value)` — no flash of default theme
- Tints (`--primary-50` … `--primary-900`) are intentionally **not** auto-regenerated when `--primary` changes; presets handle the cohesive palette

---

## 10. Bilingual (EN / AR) translation system

- **600+ translation keys** in flat `Record<string, string>` objects (`en.ts` and `ar.ts`)
- `I18nProvider` flips `document.documentElement.dir` and the body font stack
- `t(key, fallback?)` returns the localised string, falling back to the provided English (or the key) if missing
- **All translated:**
  - Sidebar labels (Main, Core Modules, Settings sections)
  - Topbar (search placeholder, notifications, language toggle, sign out)
  - Page eyebrows / titles / subs
  - KPI labels
  - Tab labels
  - **Form field labels** in every modal (Cyber's 9 modals + 8 module modals)
  - **Modal titles** including `Edit · …` prefix
  - **Table column headers** in all 30+ tables across all modules
  - **Card titles** on overview tabs and dashboard cards
  - Status / severity badge text via `statusLabel(t, …)` and `severityLabel(t, …)` helpers
  - Toast messages
  - Empty-state titles + sub-text
  - Risk modal section labels (*Before controls (Inherent)*, *After controls (Residual)*, *Likelihood*, *Impact*, *Score*, *Risk reduction*)
- **RTL layout** verified working: sidebar moves to the right, search-box internals reverse, toast stack moves to bottom-left, score-pill heatmap axis labels mirror, sidebar active-item indicator border swaps to the right edge

### What stays English (intentionally):
- Backend enum values rendered raw on tables when no `statusLabel`/`severityLabel` mapping exists for them (rare leftovers).
- Some dropdown option labels inside open modals (`Critical` / `High` / `Medium` / `Low` etc.) — translate-pass-3 candidates.

---

## 11. Backend integration — REST API map

`src/lib/api.ts` exposes typed clients for every module, with paginated `Page<T>` response handling and a `useApiList` hook that auto-falls-back to mock data when the backend is unreachable / unauthenticated:

```
authApi:        login · logout · refresh
dashboardApi:   stats · heatmap · riskByCategory · recentRisks · recentIncidents · complianceByFramework
riskApi:        list · get · create · update · remove · categories
bcmApi:         plans (CRUD) · processes · bia · stats
cyberApi:       controls · subControls · assessments · vulns · exceptions · incidents
                · assets · evidence · actionPlans  (all CRUD)
                + approveAssessment · escalateVuln · approveException
                + startResponse · completePlan · convertAssessmentGap
privacyApi:     dsrs (CRUD) · breaches · ropa · stats
complianceApi:  controls (CRUD) · assessments · findings · stats
tprmApi:        vendors (CRUD) · stats
incidentApi:    list · get · create · update · remove · stats
evidenceApi:    items (CRUD + approve/reject) · requests · reviews · expired
scmApi:         vendors (CRUD) · onboarding (+advance/approve) · assessments · contracts · stats
adminApi:       users (CRUD) · roles
```

**A live smoke test of `POST /api/v1/bcm/plans` was performed mid-session — backend round-tripped a new plan with id=7. The full create flow works end-to-end.**

---

## 12. Bugs encountered + fixed

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| 1 | Sidebar didn't move right in RTL — only labels translated | `[dir="rtl"] .grc-shell { grid-template-columns }` override conflicted with `grid-column` placement on `.sb` | Removed both overrides; `direction: rtl` on `<html>` flips grid flow naturally |
| 2 | Browser login failed with **403 Forbidden** | Spring CORS allowlist in `application-dev.yml` had ports 3000/5500/8086/8087 but **not 5173** (Vite default) | Restarted backend JAR with `--grc.cors.allowed-origins=…,localhost:5173,…`. Also updated source YAML for next rebuild. |
| 3 | TS errors blocking compile | `FormModal<T extends Record<string, unknown>>` rejected interface types (no index signature). `Dictionary = typeof en` failed because `as const` made en/ar literal types diverge. | Relaxed `FormModal<T>` constraint; widened `Dictionary` to `Record<string, string>` with cast. |
| 4 | `noUnusedLocals` cascade | TS skipped JSX bodies due to FormModal failure → marked everything inside as unused | Same as #3 |
| 5 | Form modal too narrow, fields cramped | `Modal lg = 760px`, `FormModal` used fixed 2-col grid | Bumped lg to **880px**, FormModal default now `size="lg"`, switched to **12-column** grid via `span: 3 \| 4 \| 6 \| 8 \| 12` |
| 6 | Form labels stayed English in Arabic mode | `FieldDef[]` constants at module scope couldn't call `t()` | Moved field defs **inside** the component, called `t()` per label. Did this for all 10 modules. |
| 7 | Table column headers stayed English | Column arrays had hardcoded English `header:` strings | Added 110+ `colXxx` translation keys, updated every column in every module |

---

## 13. Snapshot folder — `TSS-Theme/`

Frozen reference clone of `tss-grc-ui-v2` taken at first sign-off. Identical source, but `package.json` renamed to `tss-theme` and a `THEME.md` added describing what's signed off. Use it to:
- Diff against later changes when something visually drifts
- Fork from for a future redesign without picking up subsequent module work
- Reference the palette / type / spacing tokens authoritatively

It runs independently with its own `npm install` + `npm run dev` (would conflict on port 5173 if `tss-grc-ui-v2` is also running).

---

## 14. How to run everything

```bash
# 1. SQL Server is already up locally with the TSS_GRC database

# 2. Backend (already running on 8084 via JAR; PID stored in process)
#    To start fresh:
cd tss-grc-backend
java -jar target/tss-grc-platform-0.1.0-SNAPSHOT.jar \
    --spring.profiles.active=dev \
    --grc.cors.allowed-origins=http://localhost:3000,http://localhost:5173,http://localhost:5174,http://localhost:5500,http://localhost:8086,http://localhost:8087

# 3. Frontend (port 5173)
cd tss-grc-ui-v2
npm install      # one-time
npm run dev      # http://localhost:5173

# 4. Login with admin / Admin@12345
```

`npm run typecheck` runs `tsc --noEmit` and currently passes with **zero errors**.

---

## 15. Commits / git status

The worktree is on branch `claude/unruffled-lamarr-2ebc33` with master as the eventual merge target. All changes from this session are uncommitted on the worktree — the user has not asked for a commit yet. Files changed include:

- `CLAUDE.md` (rewritten for accuracy)
- `tss-grc-backend/src/main/resources/application-dev.yml` (CORS allowlist expansion)
- `tss-grc-ui-v2/` (new — entire React project, ~70 source files)
- `TSS-Theme/` (new — snapshot of tss-grc-ui-v2 + THEME.md)

---

## 16. Pending / open items

These were noted during the session but not implemented. Each is small enough to do as a follow-up:

1. **Drop-down option labels in modals** (Critical / High / Medium / Low etc.) — currently English even in Arabic mode. Translation pass needed for ~15 dropdowns.
2. **Confirm dialog titles** like *"Delete this control?"* — still English. Single-line edit per module.
3. **Workflow shortcut buttons** in non-Cyber modules — e.g. SCM Onboarding `advance`/`approve` (API already wired in `scmApi`), Compliance Findings actions.
4. **Auto-tint regeneration** when user picks a custom `--primary` in Settings — currently the `--primary-50…900` ramp stays fixed.
5. **Top-bar global search** — chrome exists but not wired.
6. **Notification dot** in topbar — render uses `/api/v1/notifications/unread-count`.
7. **Activity feed on `/dashboard`** — currently hardcoded; backend has `/api/v1/dashboard/recent-risks` + `/recent-incidents`.
8. **Convert-Gap button** for Cyber assessments — backend supports `POST /assessments/{id}/convert-gap`, API client exposes it, UI button not yet placed.
9. **Risk modal Owner dropdown** for non-admin users — currently fetches `/api/v1/admin/users` which requires admin role; non-admin users will see empty list silently.
10. **Compliance Findings + Privacy Breaches/RoPA** edit/delete — only their primary tab (DSR for Privacy, Controls for Compliance) currently has CRUD.

---

## 17. Files-to-know cheat sheet

| When you need to… | Open this file |
|---|---|
| Change a design token | `tss-grc-ui-v2/src/styles/tokens.css` |
| Change the shell layout | `tss-grc-ui-v2/src/styles/shell.css` |
| Change component styles (Button, Badge, Card, Modal, Table, Stepper, Timeline, Settings widgets) | `tss-grc-ui-v2/src/styles/components.css` |
| Add a translation key | `tss-grc-ui-v2/src/translations/en.ts` + `ar.ts` |
| Add an API endpoint | `tss-grc-ui-v2/src/lib/api.ts` |
| Change a module's tabs / fields / KPIs | `tss-grc-ui-v2/src/pages/<module>/<Module>Page.tsx` |
| Change the field grid layout (cols → span) | `tss-grc-ui-v2/src/components/ui/FormModal.tsx` |
| Add a row workflow action | extend `RowActions` `extras` array on the relevant column |
| Add a new module page | create `src/pages/<name>/<Name>Page.tsx`, wire route in `App.tsx`, add to `Sidebar.tsx` |
| Pin a default theme value | `tss-grc-ui-v2/src/lib/theme.tsx` `TOKEN_DEFS` |
| Verify nothing broke | `cd tss-grc-ui-v2 && npm run typecheck` |
| Restore the frozen design baseline | `cd TSS-Theme && npm install && npm run dev` |

---

## 18. Design philosophy applied throughout

- **Eye-comfort warm palette** — never cold blue-purple gradients. Saudi government office context (fluorescent lighting).
- **Lucide icons only** — the v1 used emoji as icons (🛡️ ⚠️ 🔒); these were replaced consistently.
- **Bilingual by default** — every screen must work in `dir="ltr"` and `dir="rtl"`. Numerals stay Latin in both directions (Saudi enterprise convention).
- **No emoji in product surfaces** — they render inconsistently across OS, look unprofessional in regulator-facing exports.
- **Saudi compliance is the headline** — NCA ECC, SDAIA, PDPL, SAMA, ISO 27001/22301/31000/27701 surfaced wherever frameworks are referenced.
- **Calm hover language** — buttons darken 6–8%, cards translate -2px and step to `--shadow-2`. No bounce, no scale-up.
- **3D charts are a brand signature** — bar charts use a `--shadow-2` card + 4px depth gradient, never flat-fill rainbow scales.
- **Title Case for buttons / page titles / KPI labels / table headers**; Sentence case for descriptions / helper text / toasts.
- **No exclamation marks, no celebratory copy** — regulator-facing tone.

---

## 19. Memory entries (persisted across sessions)

Stored in `~/.claude/projects/C--AYMAN-Github-Claude-GRC-2/memory/`:

- `project_tss_grc_overview.md` — tech stack, repo layout, completed modules
- `project_v2_lessons.md` — critical bugs already fixed in v2 (JWT filter, Swagger CSP, BCrypt shell escaping, Java 17 pin, api.js absolute URL)
- `reference_old_grc_repo.md` — old repo and conversation backup locations

These are loaded automatically into future Claude Code sessions so context survives.

---

**End of summary.**
