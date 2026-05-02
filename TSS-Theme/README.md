# tss-grc-ui-v2

React + TypeScript rebuild of the TSS GRC Platform frontend. Adopts the **Eye-Comfort Enterprise** design system from `C:\AYMAN\Gafaar-GRC-HTML\TSS GRC Platform Design System\` (warm off-white surfaces, deep teal primary, sand-amber accent, Plus Jakarta Sans + IBM Plex Sans Arabic + JetBrains Mono, Lucide icons).

This is a **pilot** — only one module (Enterprise Risk Management) is implemented end-to-end so the design language can be reviewed before being applied to the rest.

## Status

| Surface | State |
|---|---|
| Design tokens (color, type, spacing, radius, shadow, motion) | ✅ Ported verbatim from `colors_and_type.css` |
| App shell (sidebar, topbar, page chrome, RTL flip) | ✅ |
| Login page (split hero + form) | ✅ |
| Dashboard (KPIs, heat map, 3D bars, framework readiness, activity) | ✅ Sample data |
| **Risk Register (pilot module)** | ✅ Register tab, Heat Map tab, KPIs, New Risk modal |
| Risk · KRI / Appetite tabs | 🟡 Placeholders |
| Cyber, BCM, Data Gov, PDPL, Compliance, Audit, TPRM, Policy | 🟡 Placeholders |
| Bilingual EN / AR with RTL toggle | ✅ |
| Connects to Spring Boot backend on `:8084` | ✅ via Vite proxy. Falls back to mock data when no JWT (demo mode). |

## Prerequisites

- Node 20+ and npm

## Setup

```bash
cd tss-grc-ui-v2
npm install
npm run dev
# UI: http://localhost:5173
```

The Vite dev server proxies `/api/*` → `http://localhost:8084` (Spring Boot dev profile). Either start the backend per the project root `CLAUDE.md`, or just click **Continue without backend (demo mode)** on the login page to see the design with sample data.

To override the proxy target, copy `.env.example` to `.env.local` and set `VITE_API_BASE`.

## Tech

| Concern | Choice |
|---|---|
| Build / dev server | Vite 5 |
| UI framework | React 18 + TypeScript |
| Routing | react-router-dom 6 |
| Icons | lucide-react (every icon stroke-based, 1.5–2px) — no emoji anywhere |
| Class composition | clsx |
| Styling | Vanilla CSS with design-token CSS variables (no Tailwind, no UI kit) — keeps the design tokens one-to-one with the design system source |
| State | React hooks + Context (i18n, toasts). No Redux. |
| Data fetch | Hand-rolled `fetch` wrapper in `src/lib/api.ts` with JWT bearer header; 401 clears the session |
| i18n | Two flat dictionaries (`src/translations/{en,ar}.ts`) and a `useI18n()` hook that flips `<html dir>` and the body font stack |
| Auth | Access token in `sessionStorage` (matches the v1 frontend); refresh token is the backend's HttpOnly cookie at `/api/auth/refresh` |

## Folder layout

```
src/
├── App.tsx              ← routing
├── main.tsx             ← entry
├── styles/              ← tokens.css, globals.css, shell.css, components.css
├── lib/                 ← api, auth, i18n, toast, cn
├── translations/        ← en.ts, ar.ts
├── components/
│   ├── layout/          ← Shell, Sidebar, Topbar, PageHeader
│   ├── ui/              ← Button, Badge, Input, Select, Card, Kpi, Progress, Tabs, Modal
│   └── viz/             ← HeatMap, BarChart3D, RiskTable
└── pages/
    ├── LoginPage.tsx
    ├── DashboardPage.tsx
    ├── PlaceholderPage.tsx
    └── risk/            ← pilot module
        ├── RiskRegisterPage.tsx
        ├── NewRiskModal.tsx
        └── mockRisks.ts
```

## What to look at when reviewing the design

1. **Login page** (`/login`) — split hero with the brand gradient, framework pill strip, and form panel.
2. **Risk Register** (`/risk`) — KPI strip, Register tab with score-coloured pills + status badges, Heat Map tab with the 5×5 grid, "New Risk" modal.
3. Toggle language with the **العربية / EN** button in the top-right — every surface mirrors RTL and switches to IBM Plex Sans Arabic.
4. Confirm the eye-comfort palette feels right (warm off-white canvas, deep teal primary, sand-amber accent) — these are the tokens that will propagate to every other module.

## Why Risk as the pilot

- The design system kit (`ui_kits/grc-platform/`) explicitly mocks Enterprise Risk, so the visual intent is unambiguous.
- The backend already exposes `/api/v1/risk/risks` — the pilot proves both design and data wiring on the same module.
- It exercises the most components in one screen (table, heat map, KPIs, badges, score pills, tabs, modal, form), so once Risk is signed off, the other modules are largely re-skin work.

## Next steps (after design sign-off)

1. Apply the same shell + component vocabulary to Cyber, BCM, PDPL, Compliance, TPRM, Evidence (each currently a placeholder route).
2. Add a real Dashboard backed by `/api/v1/dashboard/*`.
3. Wire categories + owners (`/api/v1/risk/categories`, `/api/v1/iam/users`) into the New Risk modal so the Category and Owner fields aren't free-text.
4. Replace the demo password hint on the login page once we've decided the production landing copy.
